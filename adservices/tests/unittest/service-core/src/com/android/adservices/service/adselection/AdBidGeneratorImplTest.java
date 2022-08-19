/*
 * Copyright (C) 2022 The Android Open Source Project
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *      http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */

package com.android.adservices.service.adselection;

import static com.android.adservices.service.adselection.AdBidGeneratorImpl.BIDDING_TIMED_OUT;
import static com.android.adservices.service.adselection.AdBidGeneratorImpl.MISSING_BIDDING_LOGIC;
import static com.android.adservices.service.adselection.AdBidGeneratorImpl.MISSING_TRUSTED_BIDDING_SIGNALS;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertNull;
import static org.junit.Assert.assertThrows;
import static org.junit.Assert.assertTrue;

import android.adservices.adselection.AdSelectionConfig;
import android.adservices.adselection.AdSelectionConfigFixture;
import android.adservices.adselection.AdWithBid;
import android.adservices.common.AdData;
import android.adservices.common.AdDataFixture;
import android.adservices.common.AdSelectionSignals;
import android.adservices.common.CommonFixture;
import android.adservices.http.MockWebServerRule;
import android.annotation.NonNull;
import android.content.Context;
import android.net.Uri;
import android.util.Pair;

import androidx.room.Room;
import androidx.test.core.app.ApplicationProvider;

import com.android.adservices.MockWebServerRuleFactory;
import com.android.adservices.concurrency.AdServicesExecutors;
import com.android.adservices.customaudience.DBCustomAudienceFixture;
import com.android.adservices.data.adselection.CustomAudienceSignals;
import com.android.adservices.data.customaudience.CustomAudienceDao;
import com.android.adservices.data.customaudience.CustomAudienceDatabase;
import com.android.adservices.data.customaudience.DBCustomAudience;
import com.android.adservices.data.customaudience.DBCustomAudienceOverride;
import com.android.adservices.data.customaudience.DBTrustedBiddingData;
import com.android.adservices.service.Flags;
import com.android.adservices.service.FlagsFactory;
import com.android.adservices.service.common.AdServicesHttpsClient;
import com.android.adservices.service.devapi.CustomAudienceDevOverridesHelper;
import com.android.adservices.service.devapi.DevContext;
import com.android.adservices.service.js.IsolateSettings;

import com.google.common.collect.ImmutableList;
import com.google.common.util.concurrent.FluentFuture;
import com.google.common.util.concurrent.Futures;
import com.google.common.util.concurrent.ListenableFuture;
import com.google.common.util.concurrent.ListeningExecutorService;
import com.google.mockwebserver.Dispatcher;
import com.google.mockwebserver.MockResponse;
import com.google.mockwebserver.MockWebServer;
import com.google.mockwebserver.RecordedRequest;

import org.json.JSONException;
import org.junit.Before;
import org.junit.Ignore;
import org.junit.Rule;
import org.junit.Test;
import org.mockito.Mock;
import org.mockito.Mockito;
import org.mockito.MockitoAnnotations;
import org.mockito.junit.MockitoJUnit;
import org.mockito.junit.MockitoRule;

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.ExecutionException;

public class AdBidGeneratorImplTest {
    public static final List<Double> BIDS =
            new ArrayList<Double>(ImmutableList.of(-10.0, 0.0, 1.0, 5.4));
    public static final List<AdData> ADS =
            AdDataFixture.getValidAdsByBuyer(CommonFixture.VALID_BUYER);
    public static final List<AdWithBid> AD_WITH_NON_POSITIVE_BIDS =
            new ArrayList<AdWithBid>(
                    ImmutableList.of(
                            new AdWithBid(ADS.get(0), BIDS.get(1)),
                            new AdWithBid(ADS.get(1), BIDS.get(0)),
                            new AdWithBid(ADS.get(2), BIDS.get(1)),
                            new AdWithBid(ADS.get(3), BIDS.get(0))));
    public static final List<AdWithBid> AD_WITH_BIDS =
            new ArrayList<AdWithBid>(
                    ImmutableList.of(
                            new AdWithBid(ADS.get(0), BIDS.get(1)),
                            new AdWithBid(ADS.get(1), BIDS.get(0)),
                            new AdWithBid(ADS.get(2), BIDS.get(3)),
                            new AdWithBid(ADS.get(3), BIDS.get(2))));
    private static final AdSelectionSignals EMPTY_AD_SELECTION_SIGNALS = AdSelectionSignals.EMPTY;
    private static final AdSelectionSignals EMPTY_BUYER_SIGNALS = AdSelectionSignals.EMPTY;
    private static final AdSelectionSignals EMPTY_CONTEXTUAL_SIGNALS = AdSelectionSignals.EMPTY;
    private static final AdSelectionSignals EMPTY_USER_SIGNALS = AdSelectionSignals.EMPTY;
    private static final AdSelectionSignals TRUSTED_BIDDING_SIGNALS =
            AdSelectionSignals.fromString(
                    "{\n" + "\t\"max_bid_limit\": 20,\n" + "\t\"ad_type\": \"retail\"\n" + "}");
    private static final DBCustomAudience CUSTOM_AUDIENCE_WITH_EMPTY_ADS =
            DBCustomAudienceFixture.getValidBuilderByBuyer(CommonFixture.VALID_BUYER)
                    .setAds(Collections.emptyList())
                    .build();
    @Rule public final MockitoRule rule = MockitoJUnit.rule();
    private final String mFetchJavaScriptPath = "/fetchJavascript/";
    private final String mTrustedBiddingPath = "/fetchBiddingSignals/";
    private final String mTrustedBiddingParams = "?keys=max_bid_limit%2Cad_type";
    private ListeningExecutorService mLightweightExecutorService;
    private ListeningExecutorService mBackgroundExecutorService;
    private ListeningExecutorService mBlockingExecutorService;
    private final Context mContext = ApplicationProvider.getApplicationContext();
    @Rule public MockWebServerRule mMockWebServerRule = MockWebServerRuleFactory.createForHttps();
    @Mock AdSelectionScriptEngine mAdSelectionScriptEngine;
    private Uri mDecisionLogicUri;
    private Dispatcher mDefaultDispatcher;
    private AdBidGeneratorImpl mAdBidGenerator;
    private AdServicesHttpsClient mAdServicesHttpsClient;
    private String mBuyerDecisionLogicJs;
    private MockWebServer mServer;
    private DBCustomAudience mCustomAudienceWithAds;
    private DBTrustedBiddingData mTrustedBiddingData;
    private List<String> mTrustedBiddingKeys;
    private Uri mTrustedBiddingUri;
    private CustomAudienceSignals mCustomAudienceSignals;
    private CustomAudienceBiddingInfo mCustomAudienceBiddingInfo;
    private DevContext mDevContext;
    private CustomAudienceDao mCustomAudienceDao;
    private Flags mFlags;
    private MockWebServerRule.RequestMatcher<String> mRequestMatcherExactMatch;
    private IsolateSettings mIsolateSettings;

    @Before
    public void setUp() throws Exception {
        MockitoAnnotations.initMocks(this);

        mDevContext = DevContext.createForDevOptionsDisabled();
        mLightweightExecutorService = AdServicesExecutors.getLightWeightExecutor();
        mBackgroundExecutorService = AdServicesExecutors.getBackgroundExecutor();
        mBlockingExecutorService = AdServicesExecutors.getBlockingExecutor();
        mAdServicesHttpsClient =
                new AdServicesHttpsClient(AdServicesExecutors.getBlockingExecutor());
        mCustomAudienceDao =
                Room.inMemoryDatabaseBuilder(
                                ApplicationProvider.getApplicationContext(),
                                CustomAudienceDatabase.class)
                        .build()
                        .customAudienceDao();

        mBuyerDecisionLogicJs =
                "function reportWin(ad_selection_signals, per_buyer_signals, signals_for_buyer,"
                        + " contextual_signals, custom_audience_signals) { \n"
                        + " return {'status': 0, 'results': {'reporting_url': '"
                        + " buyerReportingUrl "
                        + "' } };\n"
                        + "}";

        mDecisionLogicUri = mMockWebServerRule.uriForPath(mFetchJavaScriptPath);

        mTrustedBiddingKeys = ImmutableList.of("max_bid_limit", "ad_type");
        mTrustedBiddingUri = mMockWebServerRule.uriForPath(mTrustedBiddingPath);
        mTrustedBiddingData =
                new DBTrustedBiddingData.Builder()
                        .setKeys(mTrustedBiddingKeys)
                        .setUrl(mTrustedBiddingUri)
                        .build();

        mCustomAudienceWithAds =
                DBCustomAudienceFixture.getValidBuilderByBuyer(CommonFixture.VALID_BUYER)
                        .setBiddingLogicUrl(mDecisionLogicUri)
                        .setTrustedBiddingData(mTrustedBiddingData)
                        .build();

        mDefaultDispatcher =
                new Dispatcher() {
                    @Override
                    public MockResponse dispatch(RecordedRequest request) {
                        switch (request.getPath()) {
                            case mFetchJavaScriptPath:
                                return new MockResponse().setBody(mBuyerDecisionLogicJs);
                            case mTrustedBiddingPath + mTrustedBiddingParams:
                                return new MockResponse()
                                        .setBody(TRUSTED_BIDDING_SIGNALS.toString());
                        }
                        return new MockResponse().setResponseCode(404);
                    }
                };

        mCustomAudienceSignals =
                CustomAudienceSignals.buildFromCustomAudience(mCustomAudienceWithAds);

        mCustomAudienceBiddingInfo =
                CustomAudienceBiddingInfo.create(
                        mDecisionLogicUri, mBuyerDecisionLogicJs, mCustomAudienceSignals);

        mFlags = FlagsFactory.getFlagsForTest();

        mIsolateSettings = IsolateSettings.forMaxHeapSizeEnforcementDisabled();

        mRequestMatcherExactMatch =
                (actualRequest, expectedRequest) -> actualRequest.equals(expectedRequest);
    }

    @Test
    public void testRunAdBiddingPerCASuccess() throws Exception {
        // Given we are using a direct executor and mock the returned result from the
        // AdSelectionScriptEngine.generateBids for preparing the test,
        mServer = mMockWebServerRule.startMockWebServer(mDefaultDispatcher);

        CustomAudienceDevOverridesHelper customAudienceDevOverridesHelper =
                new CustomAudienceDevOverridesHelper(mDevContext, mCustomAudienceDao);
        mAdBidGenerator =
                new AdBidGeneratorImpl(
                        mContext,
                        mLightweightExecutorService,
                        mBackgroundExecutorService,
                        mAdSelectionScriptEngine,
                        mAdServicesHttpsClient,
                        customAudienceDevOverridesHelper,
                        mFlags,
                        mIsolateSettings);

        Mockito.when(
                        mAdSelectionScriptEngine.generateBids(
                                mBuyerDecisionLogicJs,
                                ADS,
                                EMPTY_AD_SELECTION_SIGNALS,
                                EMPTY_BUYER_SIGNALS,
                                TRUSTED_BIDDING_SIGNALS,
                                EMPTY_CONTEXTUAL_SIGNALS,
                                EMPTY_USER_SIGNALS,
                                mCustomAudienceSignals))
                .thenReturn(FluentFuture.from(Futures.immediateFuture(AD_WITH_BIDS)));
        // When the call to runAdBiddingPerCA, and the computation of future is complete,
        FluentFuture<AdBiddingOutcome> result =
                mAdBidGenerator.runAdBiddingPerCA(
                        mCustomAudienceWithAds,
                        EMPTY_AD_SELECTION_SIGNALS,
                        EMPTY_BUYER_SIGNALS,
                        EMPTY_CONTEXTUAL_SIGNALS,
                        AdSelectionConfigFixture.anAdSelectionConfig());
        AdBiddingOutcome expectedAdBiddingOutcome =
                AdBiddingOutcome.builder()
                        .setAdWithBid(AD_WITH_BIDS.get(2))
                        .setCustomAudienceBiddingInfo(mCustomAudienceBiddingInfo)
                        .build();
        // Then we can test the result by assertion,
        assertEquals(expectedAdBiddingOutcome, result.get());
        Mockito.verify(mAdSelectionScriptEngine)
                .generateBids(
                        mBuyerDecisionLogicJs,
                        ADS,
                        EMPTY_AD_SELECTION_SIGNALS,
                        EMPTY_BUYER_SIGNALS,
                        TRUSTED_BIDDING_SIGNALS,
                        EMPTY_CONTEXTUAL_SIGNALS,
                        EMPTY_USER_SIGNALS,
                        mCustomAudienceSignals);
        mMockWebServerRule.verifyMockServerRequests(
                mServer,
                2,
                ImmutableList.of(mFetchJavaScriptPath, mTrustedBiddingPath + mTrustedBiddingParams),
                mRequestMatcherExactMatch);
    }

    @Test
    public void testRunAdBiddingPerCAWithOverrideSuccess() throws Exception {
        // Resetting the server with a missing response body, we do not expect any server calls
        mServer =
                mMockWebServerRule.startMockWebServer(
                        new Dispatcher() {
                            @Override
                            public MockResponse dispatch(RecordedRequest request) {
                                return new MockResponse().setResponseCode(404);
                            }
                        });

        AdSelectionConfig adSelectionConfig = AdSelectionConfigFixture.anAdSelectionConfig();

        // Set dev override
        String myAppPackageName = "com.google.ppapi.test";

        DBCustomAudienceOverride dbCustomAudienceOverride =
                DBCustomAudienceOverride.builder()
                        .setOwner(mCustomAudienceWithAds.getOwner())
                        .setBuyer(mCustomAudienceWithAds.getBuyer())
                        .setName(mCustomAudienceWithAds.getName())
                        .setAppPackageName(myAppPackageName)
                        .setBiddingLogicJS(mBuyerDecisionLogicJs)
                        .setTrustedBiddingData(TRUSTED_BIDDING_SIGNALS.toString())
                        .build();
        mCustomAudienceDao.persistCustomAudienceOverride(dbCustomAudienceOverride);

        // Setting dev context to allow overrides
        mDevContext =
                DevContext.builder()
                        .setDevOptionsEnabled(true)
                        .setCallingAppPackageName(myAppPackageName)
                        .build();

        // Resetting adBidGenerator to use the new dev context
        CustomAudienceDevOverridesHelper customAudienceDevOverridesHelper =
                new CustomAudienceDevOverridesHelper(mDevContext, mCustomAudienceDao);
        mAdBidGenerator =
                new AdBidGeneratorImpl(
                        mContext,
                        mLightweightExecutorService,
                        mBackgroundExecutorService,
                        mAdSelectionScriptEngine,
                        mAdServicesHttpsClient,
                        customAudienceDevOverridesHelper,
                        mFlags,
                        mIsolateSettings);

        // Given we are using a direct executor and mock the returned result from the
        // AdSelectionScriptEngine.generateBids for preparing the test,
        Mockito.when(
                        mAdSelectionScriptEngine.generateBids(
                                mBuyerDecisionLogicJs,
                                ADS,
                                EMPTY_AD_SELECTION_SIGNALS,
                                EMPTY_BUYER_SIGNALS,
                                TRUSTED_BIDDING_SIGNALS,
                                EMPTY_CONTEXTUAL_SIGNALS,
                                EMPTY_USER_SIGNALS,
                                mCustomAudienceSignals))
                .thenReturn(FluentFuture.from(Futures.immediateFuture(AD_WITH_BIDS)));
        // When the call to runAdBiddingPerCA, and the computation of future is complete,
        FluentFuture<AdBiddingOutcome> result =
                mAdBidGenerator.runAdBiddingPerCA(
                        mCustomAudienceWithAds,
                        EMPTY_AD_SELECTION_SIGNALS,
                        EMPTY_BUYER_SIGNALS,
                        EMPTY_CONTEXTUAL_SIGNALS,
                        adSelectionConfig);
        AdBiddingOutcome expectedAdBiddingOutcome =
                AdBiddingOutcome.builder()
                        .setAdWithBid(AD_WITH_BIDS.get(2))
                        .setCustomAudienceBiddingInfo(mCustomAudienceBiddingInfo)
                        .build();

        // Then we can test the result by assertion,
        assertEquals(expectedAdBiddingOutcome, waitForFuture(() -> result));
        Mockito.verify(mAdSelectionScriptEngine)
                .generateBids(
                        mBuyerDecisionLogicJs,
                        ADS,
                        EMPTY_AD_SELECTION_SIGNALS,
                        EMPTY_BUYER_SIGNALS,
                        TRUSTED_BIDDING_SIGNALS,
                        EMPTY_CONTEXTUAL_SIGNALS,
                        EMPTY_USER_SIGNALS,
                        mCustomAudienceSignals);
        mMockWebServerRule.verifyMockServerRequests(
                mServer, 0, Collections.emptyList(), mRequestMatcherExactMatch);
    }

    @Test
    public void testRunAdBiddingPerCANonPositiveBidResult() throws Exception {
        // Given we are using a direct executor and mock the returned result from the
        // AdSelectionScriptEngine.generateBids for preparing the test,
        mServer = mMockWebServerRule.startMockWebServer(mDefaultDispatcher);

        CustomAudienceDevOverridesHelper customAudienceDevOverridesHelper =
                new CustomAudienceDevOverridesHelper(mDevContext, mCustomAudienceDao);
        mAdBidGenerator =
                new AdBidGeneratorImpl(
                        mContext,
                        mLightweightExecutorService,
                        mBackgroundExecutorService,
                        mAdSelectionScriptEngine,
                        mAdServicesHttpsClient,
                        customAudienceDevOverridesHelper,
                        mFlags,
                        mIsolateSettings);

        Mockito.when(
                        mAdSelectionScriptEngine.generateBids(
                                mBuyerDecisionLogicJs,
                                ADS,
                                EMPTY_AD_SELECTION_SIGNALS,
                                EMPTY_BUYER_SIGNALS,
                                TRUSTED_BIDDING_SIGNALS,
                                EMPTY_CONTEXTUAL_SIGNALS,
                                EMPTY_USER_SIGNALS,
                                mCustomAudienceSignals))
                .thenReturn(FluentFuture.from(Futures.immediateFuture(AD_WITH_NON_POSITIVE_BIDS)));
        // When the call to runAdBiddingPerCA, and the computation of future is complete,
        FluentFuture<AdBiddingOutcome> result =
                mAdBidGenerator.runAdBiddingPerCA(
                        mCustomAudienceWithAds,
                        EMPTY_AD_SELECTION_SIGNALS,
                        EMPTY_BUYER_SIGNALS,
                        EMPTY_CONTEXTUAL_SIGNALS,
                        AdSelectionConfigFixture.anAdSelectionConfig());
        // Then we can test the result by assertion,
        assertNull(result.get());
        Mockito.verify(mAdSelectionScriptEngine)
                .generateBids(
                        mBuyerDecisionLogicJs,
                        ADS,
                        EMPTY_AD_SELECTION_SIGNALS,
                        EMPTY_BUYER_SIGNALS,
                        TRUSTED_BIDDING_SIGNALS,
                        EMPTY_CONTEXTUAL_SIGNALS,
                        EMPTY_USER_SIGNALS,
                        mCustomAudienceSignals);
        mMockWebServerRule.verifyMockServerRequests(
                mServer,
                2,
                ImmutableList.of(mFetchJavaScriptPath, mTrustedBiddingPath + mTrustedBiddingParams),
                mRequestMatcherExactMatch);
    }

    @Test
    @Ignore("b/242895704")
    public void testRunAdBiddingPerCABiddingTimesOut() throws Exception {
        // Given we are using a direct executor and mock the returned result from the
        // AdSelectionScriptEngine.generateBids for preparing the test,
        mServer = mMockWebServerRule.startMockWebServer(mDefaultDispatcher);

        CustomAudienceDevOverridesHelper customAudienceDevOverridesHelper =
                new CustomAudienceDevOverridesHelper(mDevContext, mCustomAudienceDao);

        Flags flagsWithSmallerLimits =
                new Flags() {
                    @Override
                    public long getAdSelectionBiddingTimeoutPerCaMs() {
                        return 100;
                    }
                };
        mAdBidGenerator =
                new AdBidGeneratorImpl(
                        mContext,
                        mLightweightExecutorService,
                        mBackgroundExecutorService,
                        mAdSelectionScriptEngine,
                        mAdServicesHttpsClient,
                        customAudienceDevOverridesHelper,
                        flagsWithSmallerLimits,
                        mIsolateSettings);
        Mockito.when(
                        mAdSelectionScriptEngine.generateBids(
                                mBuyerDecisionLogicJs,
                                ADS,
                                EMPTY_AD_SELECTION_SIGNALS,
                                EMPTY_BUYER_SIGNALS,
                                TRUSTED_BIDDING_SIGNALS,
                                EMPTY_CONTEXTUAL_SIGNALS,
                                EMPTY_USER_SIGNALS,
                                mCustomAudienceSignals))
                .thenAnswer((invocation) -> generateBidsWithDelay(flagsWithSmallerLimits));
        // When the call to runAdBiddingPerCA, and the computation of future is complete,
        FluentFuture<AdBiddingOutcome> result =
                mAdBidGenerator.runAdBiddingPerCA(
                        mCustomAudienceWithAds,
                        EMPTY_AD_SELECTION_SIGNALS,
                        EMPTY_BUYER_SIGNALS,
                        EMPTY_CONTEXTUAL_SIGNALS,
                        AdSelectionConfigFixture.anAdSelectionConfig());
        // Then we can test the result by assertion
        ExecutionException thrown = assertThrows(ExecutionException.class, result::get);
        assertTrue(thrown.getMessage().contains(BIDDING_TIMED_OUT));
        Mockito.verify(mAdSelectionScriptEngine)
                .generateBids(
                        mBuyerDecisionLogicJs,
                        ADS,
                        EMPTY_AD_SELECTION_SIGNALS,
                        EMPTY_BUYER_SIGNALS,
                        TRUSTED_BIDDING_SIGNALS,
                        EMPTY_CONTEXTUAL_SIGNALS,
                        EMPTY_USER_SIGNALS,
                        mCustomAudienceSignals);
        mMockWebServerRule.verifyMockServerRequests(
                mServer,
                2,
                ImmutableList.of(mFetchJavaScriptPath, mTrustedBiddingPath + mTrustedBiddingParams),
                mRequestMatcherExactMatch);
    }

    @Test
    public void testRunBiddingThrowsException() throws Exception {
        // Given we are using a direct executor and mock the returned result from the
        // AdSelectionScriptEngine.generateBids for preparing the test,
        mServer = mMockWebServerRule.startMockWebServer(mDefaultDispatcher);

        CustomAudienceDevOverridesHelper customAudienceDevOverridesHelper =
                new CustomAudienceDevOverridesHelper(mDevContext, mCustomAudienceDao);
        mAdBidGenerator =
                new AdBidGeneratorImpl(
                        mContext,
                        mLightweightExecutorService,
                        mBackgroundExecutorService,
                        mAdSelectionScriptEngine,
                        mAdServicesHttpsClient,
                        customAudienceDevOverridesHelper,
                        mFlags,
                        mIsolateSettings);

        JSONException jsonException = new JSONException("");
        Mockito.when(
                        mAdSelectionScriptEngine.generateBids(
                                mBuyerDecisionLogicJs,
                                ADS,
                                EMPTY_AD_SELECTION_SIGNALS,
                                EMPTY_BUYER_SIGNALS,
                                TRUSTED_BIDDING_SIGNALS,
                                EMPTY_CONTEXTUAL_SIGNALS,
                                EMPTY_USER_SIGNALS,
                                mCustomAudienceSignals))
                .thenThrow(jsonException);
        // When the call to runBidding, and the computation of future is complete.
        FluentFuture<Pair<AdWithBid, String>> result =
                mAdBidGenerator.runBidding(
                        mCustomAudienceWithAds,
                        mBuyerDecisionLogicJs,
                        EMPTY_BUYER_SIGNALS,
                        EMPTY_CONTEXTUAL_SIGNALS,
                        mCustomAudienceSignals,
                        EMPTY_USER_SIGNALS,
                        EMPTY_AD_SELECTION_SIGNALS);

        ExecutionException outException = assertThrows(ExecutionException.class, result::get);
        assertEquals(outException.getCause(), jsonException);
    }

    @Test
    public void testTrustedSignalsEmptyKeysSuccess() throws Exception {
        // Given we are using a direct executor and mock the returned result from the
        // AdSelectionScriptEngine.generateBids for preparing the test,

        // In case there are no keys, the server will send empty json
        // Given this transaction is opaque to our logic this is a valid response
        final String emptyRequestParams = "?keys=";
        // Missing server connection for trusted signals
        Dispatcher dispatcher =
                new Dispatcher() {
                    @Override
                    public MockResponse dispatch(RecordedRequest request) {
                        switch (request.getPath()) {
                            case mFetchJavaScriptPath:
                                return new MockResponse().setBody(mBuyerDecisionLogicJs);
                            case mTrustedBiddingPath + emptyRequestParams:
                                return new MockResponse()
                                        .setBody(TRUSTED_BIDDING_SIGNALS.toString());
                        }
                        return new MockResponse().setResponseCode(404);
                    }
                };
        mServer = mMockWebServerRule.startMockWebServer(dispatcher);
        CustomAudienceDevOverridesHelper customAudienceDevOverridesHelper =
                new CustomAudienceDevOverridesHelper(mDevContext, mCustomAudienceDao);
        List<String> emptyTrustedBiddingKeys = Collections.EMPTY_LIST;
        DBTrustedBiddingData trustedBiddingData =
                new DBTrustedBiddingData.Builder()
                        .setKeys(emptyTrustedBiddingKeys)
                        .setUrl(mTrustedBiddingUri)
                        .build();

        DBCustomAudience customAudienceWithAds =
                DBCustomAudienceFixture.getValidBuilderByBuyer(CommonFixture.VALID_BUYER)
                        .setBiddingLogicUrl(mDecisionLogicUri)
                        .setTrustedBiddingData(trustedBiddingData)
                        .build();

        CustomAudienceSignals customAudienceSignals =
                CustomAudienceSignals.buildFromCustomAudience(customAudienceWithAds);

        CustomAudienceBiddingInfo customAudienceBiddingInfo =
                CustomAudienceBiddingInfo.create(
                        mDecisionLogicUri, mBuyerDecisionLogicJs, customAudienceSignals);

        mAdBidGenerator =
                new AdBidGeneratorImpl(
                        mContext,
                        mLightweightExecutorService,
                        mBackgroundExecutorService,
                        mAdSelectionScriptEngine,
                        mAdServicesHttpsClient,
                        customAudienceDevOverridesHelper,
                        mFlags,
                        mIsolateSettings);

        Mockito.when(
                        mAdSelectionScriptEngine.generateBids(
                                mBuyerDecisionLogicJs,
                                ADS,
                                EMPTY_AD_SELECTION_SIGNALS,
                                EMPTY_BUYER_SIGNALS,
                                TRUSTED_BIDDING_SIGNALS,
                                EMPTY_CONTEXTUAL_SIGNALS,
                                EMPTY_USER_SIGNALS,
                                customAudienceSignals))
                .thenReturn(FluentFuture.from(Futures.immediateFuture(AD_WITH_BIDS)));
        // When the call to runAdBiddingPerCA, and the computation of future is complete,
        FluentFuture<AdBiddingOutcome> result =
                mAdBidGenerator.runAdBiddingPerCA(
                        customAudienceWithAds,
                        EMPTY_AD_SELECTION_SIGNALS,
                        EMPTY_BUYER_SIGNALS,
                        EMPTY_CONTEXTUAL_SIGNALS,
                        AdSelectionConfigFixture.anAdSelectionConfig());
        AdBiddingOutcome expectedAdBiddingOutcome =
                AdBiddingOutcome.builder()
                        .setAdWithBid(AD_WITH_BIDS.get(2))
                        .setCustomAudienceBiddingInfo(customAudienceBiddingInfo)
                        .build();
        // Then we can test the result by assertion,
        assertEquals(expectedAdBiddingOutcome, result.get());
        Mockito.verify(mAdSelectionScriptEngine)
                .generateBids(
                        mBuyerDecisionLogicJs,
                        ADS,
                        EMPTY_AD_SELECTION_SIGNALS,
                        EMPTY_BUYER_SIGNALS,
                        TRUSTED_BIDDING_SIGNALS,
                        EMPTY_CONTEXTUAL_SIGNALS,
                        EMPTY_USER_SIGNALS,
                        customAudienceSignals);
        mMockWebServerRule.verifyMockServerRequests(
                mServer,
                2,
                ImmutableList.of(mFetchJavaScriptPath, mTrustedBiddingPath + emptyRequestParams),
                mRequestMatcherExactMatch);
    }

    @Test
    public void testMissingBiddingLogicException() throws Exception {
        // Given we are using a direct executor and mock the returned result from the
        // AdSelectionScriptEngine.generateBids for preparing the test,

        // Send error response for JS logic fetch
        Dispatcher dispatcher =
                new Dispatcher() {
                    @Override
                    public MockResponse dispatch(RecordedRequest request) {
                        switch (request.getPath()) {
                            case mFetchJavaScriptPath:
                                return new MockResponse().setResponseCode(404);
                        }
                        return new MockResponse().setResponseCode(404);
                    }
                };
        mServer = mMockWebServerRule.startMockWebServer(dispatcher);
        IllegalStateException missingJSLogicException =
                new IllegalStateException(MISSING_BIDDING_LOGIC);

        CustomAudienceDevOverridesHelper customAudienceDevOverridesHelper =
                new CustomAudienceDevOverridesHelper(mDevContext, mCustomAudienceDao);
        mAdBidGenerator =
                new AdBidGeneratorImpl(
                        mContext,
                        mLightweightExecutorService,
                        mBackgroundExecutorService,
                        mAdSelectionScriptEngine,
                        mAdServicesHttpsClient,
                        customAudienceDevOverridesHelper,
                        mFlags,
                        mIsolateSettings);

        // When the call to runAdBiddingPerCA, and the computation of future is complete,
        FluentFuture<AdBiddingOutcome> result =
                mAdBidGenerator.runAdBiddingPerCA(
                        mCustomAudienceWithAds,
                        EMPTY_AD_SELECTION_SIGNALS,
                        EMPTY_BUYER_SIGNALS,
                        EMPTY_CONTEXTUAL_SIGNALS,
                        AdSelectionConfigFixture.anAdSelectionConfig());
        ExecutionException outException = assertThrows(ExecutionException.class, result::get);
        assertEquals(outException.getCause().getMessage(), missingJSLogicException.getMessage());
        mMockWebServerRule.verifyMockServerRequests(
                mServer, 1, ImmutableList.of(mFetchJavaScriptPath), mRequestMatcherExactMatch);
    }

    @Test
    public void testMissingTrustedSignalsException() throws Exception {
        // Given we are using a direct executor and mock the returned result from the
        // AdSelectionScriptEngine.generateBids for preparing the test,

        // Missing server connection for trusted signals
        Dispatcher dispatcher =
                new Dispatcher() {
                    @Override
                    public MockResponse dispatch(RecordedRequest request) {
                        switch (request.getPath()) {
                            case mFetchJavaScriptPath:
                                return new MockResponse().setBody(mBuyerDecisionLogicJs);
                        }
                        return new MockResponse().setResponseCode(404);
                    }
                };
        mServer = mMockWebServerRule.startMockWebServer(dispatcher);
        IllegalStateException missingSignalsException =
                new IllegalStateException(MISSING_TRUSTED_BIDDING_SIGNALS);

        CustomAudienceDevOverridesHelper customAudienceDevOverridesHelper =
                new CustomAudienceDevOverridesHelper(mDevContext, mCustomAudienceDao);
        mAdBidGenerator =
                new AdBidGeneratorImpl(
                        mContext,
                        mLightweightExecutorService,
                        mBackgroundExecutorService,
                        mAdSelectionScriptEngine,
                        mAdServicesHttpsClient,
                        customAudienceDevOverridesHelper,
                        mFlags,
                        mIsolateSettings);

        // When the call to runAdBiddingPerCA, and the computation of future is complete,
        FluentFuture<AdBiddingOutcome> result =
                mAdBidGenerator.runAdBiddingPerCA(
                        mCustomAudienceWithAds,
                        EMPTY_AD_SELECTION_SIGNALS,
                        EMPTY_BUYER_SIGNALS,
                        EMPTY_CONTEXTUAL_SIGNALS,
                        AdSelectionConfigFixture.anAdSelectionConfig());
        ExecutionException outException = assertThrows(ExecutionException.class, result::get);
        assertEquals(outException.getCause().getMessage(), missingSignalsException.getMessage());
        mMockWebServerRule.verifyMockServerRequests(
                mServer,
                2,
                ImmutableList.of(mFetchJavaScriptPath, mTrustedBiddingPath + mTrustedBiddingParams),
                mRequestMatcherExactMatch);
    }

    @Test
    public void testRunAdBiddingPerCAWithException() throws Exception {
        mServer = mMockWebServerRule.startMockWebServer(mDefaultDispatcher);

        CustomAudienceDevOverridesHelper customAudienceDevOverridesHelper =
                new CustomAudienceDevOverridesHelper(mDevContext, mCustomAudienceDao);
        mAdBidGenerator =
                new AdBidGeneratorImpl(
                        mContext,
                        mLightweightExecutorService,
                        mBackgroundExecutorService,
                        mAdSelectionScriptEngine,
                        mAdServicesHttpsClient,
                        customAudienceDevOverridesHelper,
                        mFlags,
                        mIsolateSettings);

        // Given we are using a direct executor and mock the returned result from the
        // AdSelectionScriptEngine.generateBids for preparing the test,
        Mockito.when(
                        mAdSelectionScriptEngine.generateBids(
                                mBuyerDecisionLogicJs,
                                ADS,
                                EMPTY_AD_SELECTION_SIGNALS,
                                EMPTY_BUYER_SIGNALS,
                                TRUSTED_BIDDING_SIGNALS,
                                EMPTY_CONTEXTUAL_SIGNALS,
                                EMPTY_USER_SIGNALS,
                                mCustomAudienceSignals))
                .thenThrow(new JSONException(""));
        // When the call to runBidding, and the computation of future is complete.
        FluentFuture<AdBiddingOutcome> result =
                mAdBidGenerator.runAdBiddingPerCA(
                        mCustomAudienceWithAds,
                        EMPTY_AD_SELECTION_SIGNALS,
                        EMPTY_BUYER_SIGNALS,
                        EMPTY_CONTEXTUAL_SIGNALS,
                        AdSelectionConfigFixture.anAdSelectionConfig());
        assertNull(result.get());
        mMockWebServerRule.verifyMockServerRequests(
                mServer,
                2,
                ImmutableList.of(mFetchJavaScriptPath, mTrustedBiddingPath + mTrustedBiddingParams),
                mRequestMatcherExactMatch);
    }

    @Test
    public void testRunAdBiddingPerCAEmptyAds() throws Exception {
        mServer = mMockWebServerRule.startMockWebServer(mDefaultDispatcher);

        CustomAudienceDevOverridesHelper customAudienceDevOverridesHelper =
                new CustomAudienceDevOverridesHelper(mDevContext, mCustomAudienceDao);
        mAdBidGenerator =
                new AdBidGeneratorImpl(
                        mContext,
                        mLightweightExecutorService,
                        mBackgroundExecutorService,
                        mAdSelectionScriptEngine,
                        mAdServicesHttpsClient,
                        customAudienceDevOverridesHelper,
                        mFlags,
                        mIsolateSettings);

        FluentFuture<AdBiddingOutcome> result =
                mAdBidGenerator.runAdBiddingPerCA(
                        CUSTOM_AUDIENCE_WITH_EMPTY_ADS,
                        EMPTY_AD_SELECTION_SIGNALS,
                        EMPTY_BUYER_SIGNALS,
                        EMPTY_CONTEXTUAL_SIGNALS,
                        AdSelectionConfigFixture.anAdSelectionConfig());
        // The result is an early return with a FluentFuture of Null, after checking the Ads list is
        // empty.
        assertNull(result.get());
    }

    private ListenableFuture<List<AdWithBid>> generateBidsWithDelay(@NonNull Flags flags) {
        return mBlockingExecutorService.submit(
                () -> {
                    Thread.sleep(2 * flags.getAdSelectionBiddingTimeoutPerCaMs());
                    return AD_WITH_BIDS;
                });
    }

    private <T> T waitForFuture(
            AdsScoreGeneratorImplTest.ThrowingSupplier<ListenableFuture<T>> function)
            throws Exception {
        CountDownLatch resultLatch = new CountDownLatch(1);
        ListenableFuture<T> futureResult = function.get();
        futureResult.addListener(resultLatch::countDown, mLightweightExecutorService);
        resultLatch.await();
        return futureResult.get();
    }
}

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

import static com.android.dx.mockito.inline.extended.ExtendedMockito.doReturn;
import static com.android.dx.mockito.inline.extended.ExtendedMockito.when;

import android.adservices.adselection.AdSelectionCallback;
import android.adservices.adselection.AdSelectionConfig;
import android.adservices.adselection.AdSelectionConfigFixture;
import android.adservices.adselection.AdSelectionResponse;
import android.adservices.common.CommonFixture;
import android.adservices.common.FledgeErrorResponse;
import android.adservices.customaudience.CustomAudienceFixture;
import android.adservices.customaudience.TrustedBiddingDataFixture;
import android.adservices.http.MockWebServerRule;
import android.content.Context;
import android.net.Uri;
import android.os.RemoteException;

import androidx.room.Room;
import androidx.test.core.app.ApplicationProvider;

import com.android.adservices.MockWebServerRuleFactory;
import com.android.adservices.data.adselection.AdSelectionDatabase;
import com.android.adservices.data.adselection.AdSelectionEntryDao;
import com.android.adservices.data.adselection.DBAdSelectionOverride;
import com.android.adservices.data.common.DBAdData;
import com.android.adservices.data.customaudience.CustomAudienceDao;
import com.android.adservices.data.customaudience.CustomAudienceDatabase;
import com.android.adservices.data.customaudience.DBCustomAudience;
import com.android.adservices.data.customaudience.DBCustomAudienceOverride;
import com.android.adservices.data.customaudience.DBTrustedBiddingData;
import com.android.adservices.service.FlagsFactory;
import com.android.adservices.service.devapi.AdSelectionDevOverridesHelper;
import com.android.adservices.service.devapi.DevContext;
import com.android.adservices.service.devapi.DevContextFilter;
import com.android.adservices.service.stats.AdServicesLogger;
import com.android.adservices.service.stats.AdServicesLoggerImpl;
import com.android.dx.mockito.inline.extended.ExtendedMockito;

import com.google.common.collect.ImmutableList;
import com.google.mockwebserver.Dispatcher;
import com.google.mockwebserver.MockResponse;
import com.google.mockwebserver.RecordedRequest;

import org.junit.After;
import org.junit.Assert;
import org.junit.Before;
import org.junit.Rule;
import org.junit.Test;
import org.mockito.Mock;
import org.mockito.MockitoSession;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collections;
import java.util.List;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;

/**
 * This test the actual flow of Ad Selection internal flow without any mocking. The dependencies in
 * this test are invoked and used in real time.
 */
public class AdSelectionE2ETest {

    private static final String FAILURE_RESPONSE = "Encountered failure during Ad Selection";

    private static final String BUYER_1 = AdSelectionConfigFixture.BUYER_1;
    private static final String BUYER_2 = AdSelectionConfigFixture.BUYER_2;
    private static final String AD_URL_PREFIX = "http://www.domain.com/adverts/123/";

    private static final String SELLER_DECISION_LOGIC_URL = "/ssp/decision/logic/";
    private static final String BUYER_BIDDING_LOGIC_URL_PREFIX = "/buyer/bidding/logic/";

    private static final String USE_BID_AS_SCORE_JS =
            "function scoreAd(ad, bid, auction_config, seller_signals, "
                    + "trusted_scoring_signals, contextual_signal, user_signal, "
                    + "custom_audience_signal) { \n"
                    + "  return {'status': 0, 'score': bid };\n"
                    + "}";
    private static final String READ_BID_FROM_AD_METADATA_JS =
            "function generateBid(ad, auction_signals, per_buyer_signals,"
                    + " trusted_bidding_signals, contextual_signals, user_signals,"
                    + " custom_audience_signals) { \n"
                    + "  return {'status': 0, 'ad': ad, 'bid': ad.metadata.result };\n"
                    + "}";

    @Rule public MockWebServerRule mMockWebServerRule = MockWebServerRuleFactory.createForHttps();
    private MockitoSession mStaticMockSession = null;
    // Mocking DevContextFilter to test behavior with and without override api authorization
    @Mock DevContextFilter mDevContextFilter;
    private Context mContext;
    private ExecutorService mExecutorService;
    private CustomAudienceDao mCustomAudienceDao;
    private AdSelectionEntryDao mAdSelectionEntryDao;
    private AdSelectionHttpClient mAdSelectionHttpClient;
    private AdSelectionConfig mAdSelectionConfig;
    private AdSelectionServiceImpl mAdSelectionService;
    private Dispatcher mDispatcher;
    private AdServicesLogger mAdServicesLogger;

    @Before
    public void setUp() throws Exception {
        // Test applications don't have the required permissions to read config P/H flags, and
        // injecting mocked flags everywhere is annoying and non-trivial for static methods
        mStaticMockSession =
                ExtendedMockito.mockitoSession()
                        .spyStatic(FlagsFactory.class)
                        .initMocks(this)
                        .startMocking();

        // Initialize dependencies for the AdSelectionService
        mContext = ApplicationProvider.getApplicationContext();
        mExecutorService = Executors.newSingleThreadExecutor();
        mCustomAudienceDao =
                Room.inMemoryDatabaseBuilder(mContext, CustomAudienceDatabase.class)
                        .build()
                        .customAudienceDao();

        mAdSelectionEntryDao =
                Room.inMemoryDatabaseBuilder(mContext, AdSelectionDatabase.class)
                        .build()
                        .adSelectionEntryDao();

        mAdSelectionHttpClient = new AdSelectionHttpClient(mExecutorService);

        mAdServicesLogger = AdServicesLoggerImpl.getInstance();
        when(mDevContextFilter.createDevContext())
                .thenReturn(DevContext.createForDevOptionsDisabled());

        // Create an instance of AdSelection Service with real dependencies
        mAdSelectionService =
                new AdSelectionServiceImpl(
                        mAdSelectionEntryDao,
                        mCustomAudienceDao,
                        mAdSelectionHttpClient,
                        mDevContextFilter,
                        mExecutorService,
                        mContext,
                        mAdServicesLogger);

        // Create a dispatcher that helps map a request -> response in mockWebServer
        mDispatcher =
                new Dispatcher() {
                    @Override
                    public MockResponse dispatch(RecordedRequest request) {

                        switch (request.getPath()) {
                            case SELLER_DECISION_LOGIC_URL:
                                return new MockResponse().setBody(USE_BID_AS_SCORE_JS);
                            case BUYER_BIDDING_LOGIC_URL_PREFIX + BUYER_1:
                            case BUYER_BIDDING_LOGIC_URL_PREFIX + BUYER_2:
                                return new MockResponse().setBody(READ_BID_FROM_AD_METADATA_JS);
                        }
                        return new MockResponse().setResponseCode(404);
                    }
                };

        // Create an Ad Selection Config with the buyers and decision logic url
        // the url points to a JS with score generation logic
        mAdSelectionConfig =
                AdSelectionConfigFixture.anAdSelectionConfigBuilder()
                        .setCustomAudienceBuyers(Arrays.asList(BUYER_1, BUYER_2))
                        .setDecisionLogicUrl(
                                mMockWebServerRule.uriForPath(SELLER_DECISION_LOGIC_URL))
                        .build();
    }

    @After
    public void tearDown() {
        mExecutorService.shutdown();
        if (mStaticMockSession != null) {
            mStaticMockSession.finishMocking();
        }
    }

    @Test
    public void testRunAdSelectionSuccess() throws Exception {
        doReturn(FlagsFactory.getFlagsForTest()).when(FlagsFactory::getFlags);

        mMockWebServerRule.startMockWebServer(mDispatcher);
        List<Double> bidsForBuyer1 = ImmutableList.of(1.1, 2.2);
        List<Double> bidsForBuyer2 = ImmutableList.of(4.5, 6.7, 10.0);

        DBCustomAudience dBCustomAudienceForBuyer1 =
                createDBCustomAudience(
                        BUYER_1,
                        mMockWebServerRule.uriForPath(BUYER_BIDDING_LOGIC_URL_PREFIX + BUYER_1),
                        bidsForBuyer1);
        DBCustomAudience dBCustomAudienceForBuyer2 =
                createDBCustomAudience(
                        BUYER_2,
                        mMockWebServerRule.uriForPath(BUYER_BIDDING_LOGIC_URL_PREFIX + BUYER_2),
                        bidsForBuyer2);

        // Populating the Custom Audience DB
        mCustomAudienceDao.insertOrOverwriteCustomAudience(
                dBCustomAudienceForBuyer1, CustomAudienceFixture.VALID_DAILY_UPDATE_URL);
        mCustomAudienceDao.insertOrOverwriteCustomAudience(
                dBCustomAudienceForBuyer2, CustomAudienceFixture.VALID_DAILY_UPDATE_URL);

        AdSelectionTestCallback resultsCallback =
                invokeRunAdSelection(mAdSelectionService, mAdSelectionConfig);

        Assert.assertTrue(resultsCallback.mIsSuccess);
        long resultSelectionId = resultsCallback.mAdSelectionResponse.getAdSelectionId();
        Assert.assertTrue(mAdSelectionEntryDao.doesAdSelectionIdExist(resultSelectionId));
        Assert.assertEquals(
                AD_URL_PREFIX + "buyer2/ad3",
                resultsCallback.mAdSelectionResponse.getRenderUrl().toString());
    }

    @Test
    public void testRunAdSelectionSucceedsWithOverride() throws Exception {
        doReturn(FlagsFactory.getFlagsForTest()).when(FlagsFactory::getFlags);

        List<Double> bidsForBuyer1 = ImmutableList.of(1.1, 2.2);
        List<Double> bidsForBuyer2 = ImmutableList.of(4.5, 6.7, 10.0);

        DBCustomAudience dBCustomAudienceForBuyer1 =
                createDBCustomAudience(
                        BUYER_1,
                        mMockWebServerRule.uriForPath(BUYER_BIDDING_LOGIC_URL_PREFIX + BUYER_1),
                        bidsForBuyer1);
        DBCustomAudience dBCustomAudienceForBuyer2 =
                createDBCustomAudience(
                        BUYER_2,
                        mMockWebServerRule.uriForPath(BUYER_BIDDING_LOGIC_URL_PREFIX + BUYER_2),
                        bidsForBuyer2);

        String myAppPackageName = "com.google.ppapi.test";

        // Set dev override for  ad selection
        DBAdSelectionOverride adSelectionOverride =
                DBAdSelectionOverride.builder()
                        .setAdSelectionConfigId(
                                AdSelectionDevOverridesHelper.calculateAdSelectionConfigId(
                                        mAdSelectionConfig))
                        .setAppPackageName(myAppPackageName)
                        .setDecisionLogicJS(USE_BID_AS_SCORE_JS)
                        .build();
        mAdSelectionEntryDao.persistAdSelectionOverride(adSelectionOverride);

        // Set dev override for custom audience
        DBCustomAudienceOverride dbCustomAudienceOverride =
                DBCustomAudienceOverride.builder()
                        .setOwner(dBCustomAudienceForBuyer2.getOwner())
                        .setBuyer(dBCustomAudienceForBuyer2.getBuyer())
                        .setName(dBCustomAudienceForBuyer2.getName())
                        .setAppPackageName(myAppPackageName)
                        .setBiddingLogicJS(READ_BID_FROM_AD_METADATA_JS)
                        .setTrustedBiddingData("")
                        .build();
        mCustomAudienceDao.persistCustomAudienceOverride(dbCustomAudienceOverride);

        when(mDevContextFilter.createDevContext())
                .thenReturn(
                        DevContext.builder()
                                .setDevOptionsEnabled(true)
                                .setCallingAppPackageName(myAppPackageName)
                                .build());

        // Creating new instance of service with new DevContextFilter
        mAdSelectionService =
                new AdSelectionServiceImpl(
                        mAdSelectionEntryDao,
                        mCustomAudienceDao,
                        mAdSelectionHttpClient,
                        mDevContextFilter,
                        mExecutorService,
                        mContext,
                        mAdServicesLogger);

        // Populating the Custom Audience DB
        mCustomAudienceDao.insertOrOverwriteCustomAudience(
                dBCustomAudienceForBuyer1, CustomAudienceFixture.VALID_DAILY_UPDATE_URL);
        mCustomAudienceDao.insertOrOverwriteCustomAudience(
                dBCustomAudienceForBuyer2, CustomAudienceFixture.VALID_DAILY_UPDATE_URL);

        AdSelectionTestCallback resultsCallback =
                invokeRunAdSelection(mAdSelectionService, mAdSelectionConfig);

        Assert.assertTrue(resultsCallback.mIsSuccess);
        long resultSelectionId = resultsCallback.mAdSelectionResponse.getAdSelectionId();
        Assert.assertTrue(mAdSelectionEntryDao.doesAdSelectionIdExist(resultSelectionId));
        Assert.assertEquals(
                AD_URL_PREFIX + "buyer2/ad3",
                resultsCallback.mAdSelectionResponse.getRenderUrl().toString());
    }

    @Test
    public void testRunAdSelectionNoCAsFailure() throws Exception {
        // Do not populate CustomAudience DAO
        mMockWebServerRule.startMockWebServer(mDispatcher);
        AdSelectionTestCallback resultsCallback =
                invokeRunAdSelection(mAdSelectionService, mAdSelectionConfig);

        Assert.assertFalse(resultsCallback.mIsSuccess);
        Assert.assertEquals(
                FAILURE_RESPONSE, resultsCallback.mFledgeErrorResponse.getErrorMessage());
    }

    @Test
    public void testRunAdSelectionNoBuyersFailure() throws Exception {
        // Do not populate buyers in AdSelectionConfig
        mAdSelectionConfig =
                AdSelectionConfigFixture.anAdSelectionConfigBuilder()
                        .setCustomAudienceBuyers(Collections.emptyList())
                        .build();

        mMockWebServerRule.startMockWebServer(mDispatcher);
        AdSelectionTestCallback resultsCallback =
                invokeRunAdSelection(mAdSelectionService, mAdSelectionConfig);

        Assert.assertFalse(resultsCallback.mIsSuccess);
        Assert.assertEquals(
                FAILURE_RESPONSE, resultsCallback.mFledgeErrorResponse.getErrorMessage());
    }

    @Test
    public void testRunAdSelectionPartialAdsExcludedBidding() throws Exception {
        doReturn(FlagsFactory.getFlagsForTest()).when(FlagsFactory::getFlags);

        mMockWebServerRule.startMockWebServer(mDispatcher);
        // Setting bids which are partially non-positive
        List<Double> bidsForBuyer1 = ImmutableList.of(-1.1, 2.2);
        List<Double> bidsForBuyer2 = ImmutableList.of(-4.5, -6.7, -10.0);

        DBCustomAudience dBCustomAudienceForBuyer1 =
                createDBCustomAudience(
                        BUYER_1,
                        mMockWebServerRule.uriForPath(BUYER_BIDDING_LOGIC_URL_PREFIX + BUYER_1),
                        bidsForBuyer1);
        DBCustomAudience dBCustomAudienceForBuyer2 =
                createDBCustomAudience(
                        BUYER_2,
                        mMockWebServerRule.uriForPath(BUYER_BIDDING_LOGIC_URL_PREFIX + BUYER_2),
                        bidsForBuyer2);

        // Populating the Custom Audience DB
        mCustomAudienceDao.insertOrOverwriteCustomAudience(
                dBCustomAudienceForBuyer1, CustomAudienceFixture.VALID_DAILY_UPDATE_URL);
        mCustomAudienceDao.insertOrOverwriteCustomAudience(
                dBCustomAudienceForBuyer2, CustomAudienceFixture.VALID_DAILY_UPDATE_URL);

        AdSelectionTestCallback resultsCallback =
                invokeRunAdSelection(mAdSelectionService, mAdSelectionConfig);

        Assert.assertTrue(resultsCallback.mIsSuccess);
        long resultSelectionId = resultsCallback.mAdSelectionResponse.getAdSelectionId();
        Assert.assertTrue(mAdSelectionEntryDao.doesAdSelectionIdExist(resultSelectionId));
        Assert.assertEquals(
                AD_URL_PREFIX + "buyer1/ad2",
                resultsCallback.mAdSelectionResponse.getRenderUrl().toString());
    }

    @Test
    public void testRunAdSelectionMissingBiddingLogicFailure() throws Exception {
        doReturn(FlagsFactory.getFlagsForTest()).when(FlagsFactory::getFlags);

        // Setting bids->scores
        List<Double> bidsForBuyer1 = ImmutableList.of(1.1, 2.2);
        List<Double> bidsForBuyer2 = ImmutableList.of(4.5, 6.7, 10.0);

        // In this case the Buyers have no bidding logic response in dispatcher
        Dispatcher dispatcher =
                new Dispatcher() {
                    @Override
                    public MockResponse dispatch(RecordedRequest request) {

                        switch (request.getPath()) {
                            case SELLER_DECISION_LOGIC_URL:
                                return new MockResponse().setBody(USE_BID_AS_SCORE_JS);
                            case BUYER_BIDDING_LOGIC_URL_PREFIX + BUYER_1:
                            case BUYER_BIDDING_LOGIC_URL_PREFIX + BUYER_2:
                                return new MockResponse().setBody("");
                        }
                        return new MockResponse().setResponseCode(404);
                    }
                };

        mMockWebServerRule.startMockWebServer(dispatcher);

        DBCustomAudience dBCustomAudienceForBuyer1 =
                createDBCustomAudience(
                        BUYER_1,
                        mMockWebServerRule.uriForPath(BUYER_BIDDING_LOGIC_URL_PREFIX + BUYER_1),
                        bidsForBuyer1);
        DBCustomAudience dBCustomAudienceForBuyer2 =
                createDBCustomAudience(
                        BUYER_2,
                        mMockWebServerRule.uriForPath(BUYER_BIDDING_LOGIC_URL_PREFIX + BUYER_2),
                        bidsForBuyer2);

        // Populating the Custom Audience DB
        mCustomAudienceDao.insertOrOverwriteCustomAudience(
                dBCustomAudienceForBuyer1, CustomAudienceFixture.VALID_DAILY_UPDATE_URL);
        mCustomAudienceDao.insertOrOverwriteCustomAudience(
                dBCustomAudienceForBuyer2, CustomAudienceFixture.VALID_DAILY_UPDATE_URL);

        AdSelectionTestCallback resultsCallback =
                invokeRunAdSelection(mAdSelectionService, mAdSelectionConfig);

        Assert.assertFalse(resultsCallback.mIsSuccess);
        Assert.assertEquals(
                FAILURE_RESPONSE, resultsCallback.mFledgeErrorResponse.getErrorMessage());
    }

    @Test
    public void testRunAdSelectionMissingScoringLogicFailure() throws Exception {
        doReturn(FlagsFactory.getFlagsForTest()).when(FlagsFactory::getFlags);

        // Setting bids->scores
        List<Double> bidsForBuyer1 = ImmutableList.of(1.1, 2.2);
        List<Double> bidsForBuyer2 = ImmutableList.of(4.5, 6.7, 10.0);

        // In this case the Seller has no scoring logic in dispatcher
        Dispatcher dispatcher =
                new Dispatcher() {
                    @Override
                    public MockResponse dispatch(RecordedRequest request) {

                        switch (request.getPath()) {
                            case SELLER_DECISION_LOGIC_URL:
                                return new MockResponse().setBody("");
                            case BUYER_BIDDING_LOGIC_URL_PREFIX + BUYER_1:
                            case BUYER_BIDDING_LOGIC_URL_PREFIX + BUYER_2:
                                return new MockResponse().setBody(READ_BID_FROM_AD_METADATA_JS);
                        }
                        return new MockResponse().setResponseCode(404);
                    }
                };

        mMockWebServerRule.startMockWebServer(dispatcher);

        DBCustomAudience dBCustomAudienceForBuyer1 =
                createDBCustomAudience(
                        BUYER_1,
                        mMockWebServerRule.uriForPath(BUYER_BIDDING_LOGIC_URL_PREFIX + BUYER_1),
                        bidsForBuyer1);
        DBCustomAudience dBCustomAudienceForBuyer2 =
                createDBCustomAudience(
                        BUYER_2,
                        mMockWebServerRule.uriForPath(BUYER_BIDDING_LOGIC_URL_PREFIX + BUYER_2),
                        bidsForBuyer2);

        // Populating the Custom Audience DB
        mCustomAudienceDao.insertOrOverwriteCustomAudience(
                dBCustomAudienceForBuyer1, CustomAudienceFixture.VALID_DAILY_UPDATE_URL);
        mCustomAudienceDao.insertOrOverwriteCustomAudience(
                dBCustomAudienceForBuyer2, CustomAudienceFixture.VALID_DAILY_UPDATE_URL);

        AdSelectionTestCallback resultsCallback =
                invokeRunAdSelection(mAdSelectionService, mAdSelectionConfig);

        Assert.assertFalse(resultsCallback.mIsSuccess);
        Assert.assertEquals(
                FAILURE_RESPONSE, resultsCallback.mFledgeErrorResponse.getErrorMessage());
    }

    @Test
    public void testRunAdSelectionPartialMissingBiddingLogic() throws Exception {
        doReturn(FlagsFactory.getFlagsForTest()).when(FlagsFactory::getFlags);

        // Setting bids->scores
        List<Double> bidsForBuyer1 = ImmutableList.of(1.1, 2.2);
        List<Double> bidsForBuyer2 = ImmutableList.of(4.5, 6.7, 10.0);

        // In this case the Buyer 2 has no bidding logic response in dispatcher
        Dispatcher dispatcher =
                new Dispatcher() {
                    @Override
                    public MockResponse dispatch(RecordedRequest request) {

                        switch (request.getPath()) {
                            case SELLER_DECISION_LOGIC_URL:
                                return new MockResponse().setBody(USE_BID_AS_SCORE_JS);
                            case BUYER_BIDDING_LOGIC_URL_PREFIX + BUYER_1:
                                return new MockResponse().setBody(READ_BID_FROM_AD_METADATA_JS);
                            case BUYER_BIDDING_LOGIC_URL_PREFIX + BUYER_2:
                                return new MockResponse().setBody("");
                        }
                        return new MockResponse().setResponseCode(404);
                    }
                };

        mMockWebServerRule.startMockWebServer(dispatcher);

        DBCustomAudience dBCustomAudienceForBuyer1 =
                createDBCustomAudience(
                        BUYER_1,
                        mMockWebServerRule.uriForPath(BUYER_BIDDING_LOGIC_URL_PREFIX + BUYER_1),
                        bidsForBuyer1);
        DBCustomAudience dBCustomAudienceForBuyer2 =
                createDBCustomAudience(
                        BUYER_2,
                        mMockWebServerRule.uriForPath(BUYER_BIDDING_LOGIC_URL_PREFIX + BUYER_2),
                        bidsForBuyer2);

        // Populating the Custom Audience DB
        mCustomAudienceDao.insertOrOverwriteCustomAudience(
                dBCustomAudienceForBuyer1, CustomAudienceFixture.VALID_DAILY_UPDATE_URL);
        mCustomAudienceDao.insertOrOverwriteCustomAudience(
                dBCustomAudienceForBuyer2, CustomAudienceFixture.VALID_DAILY_UPDATE_URL);

        AdSelectionTestCallback resultsCallback =
                invokeRunAdSelection(mAdSelectionService, mAdSelectionConfig);

        Assert.assertTrue(resultsCallback.mIsSuccess);
        long resultSelectionId = resultsCallback.mAdSelectionResponse.getAdSelectionId();
        Assert.assertTrue(mAdSelectionEntryDao.doesAdSelectionIdExist(resultSelectionId));
        Assert.assertEquals(
                AD_URL_PREFIX + "buyer1/ad2",
                resultsCallback.mAdSelectionResponse.getRenderUrl().toString());
    }

    @Test
    public void testRunAdSelectionPartialNonPositiveScoring() throws Exception {
        doReturn(FlagsFactory.getFlagsForTest()).when(FlagsFactory::getFlags);

        // Setting bids, in this case the odd bids will be made negative by scoring logic
        List<Double> bidsForBuyer1 = ImmutableList.of(1.0, 2.0);
        List<Double> bidsForBuyer2 = ImmutableList.of(3.0, 5.0, 7.0);

        // This scoring logic assigns negative score to odd bids
        String makeOddBidsNegativeScoreJs =
                "function scoreAd(ad, bid, auction_config, seller_signals, "
                        + "trusted_scoring_signals, contextual_signal, user_signal, "
                        + "custom_audience_signal) { \n"
                        + "  return {'status': 0, 'score': (bid % 2 == 0) ? bid : -bid };\n"
                        + "}";

        Dispatcher dispatcher =
                new Dispatcher() {
                    @Override
                    public MockResponse dispatch(RecordedRequest request) {

                        switch (request.getPath()) {
                            case SELLER_DECISION_LOGIC_URL:
                                return new MockResponse().setBody(makeOddBidsNegativeScoreJs);
                            case BUYER_BIDDING_LOGIC_URL_PREFIX + BUYER_1:
                            case BUYER_BIDDING_LOGIC_URL_PREFIX + BUYER_2:
                                return new MockResponse().setBody(READ_BID_FROM_AD_METADATA_JS);
                        }
                        return new MockResponse().setResponseCode(404);
                    }
                };

        mMockWebServerRule.startMockWebServer(dispatcher);

        DBCustomAudience dBCustomAudienceForBuyer1 =
                createDBCustomAudience(
                        BUYER_1,
                        mMockWebServerRule.uriForPath(BUYER_BIDDING_LOGIC_URL_PREFIX + BUYER_1),
                        bidsForBuyer1);
        DBCustomAudience dBCustomAudienceForBuyer2 =
                createDBCustomAudience(
                        BUYER_2,
                        mMockWebServerRule.uriForPath(BUYER_BIDDING_LOGIC_URL_PREFIX + BUYER_2),
                        bidsForBuyer2);

        // Populating the Custom Audience DB
        mCustomAudienceDao.insertOrOverwriteCustomAudience(
                dBCustomAudienceForBuyer1, CustomAudienceFixture.VALID_DAILY_UPDATE_URL);
        mCustomAudienceDao.insertOrOverwriteCustomAudience(
                dBCustomAudienceForBuyer2, CustomAudienceFixture.VALID_DAILY_UPDATE_URL);

        AdSelectionTestCallback resultsCallback =
                invokeRunAdSelection(mAdSelectionService, mAdSelectionConfig);

        Assert.assertTrue(resultsCallback.mIsSuccess);
        long resultSelectionId = resultsCallback.mAdSelectionResponse.getAdSelectionId();
        Assert.assertTrue(mAdSelectionEntryDao.doesAdSelectionIdExist(resultSelectionId));
        Assert.assertEquals(
                AD_URL_PREFIX + "buyer1/ad2",
                resultsCallback.mAdSelectionResponse.getRenderUrl().toString());
    }

    @Test
    public void testRunAdSelectionNonPositiveScoringFailure() throws Exception {
        doReturn(FlagsFactory.getFlagsForTest()).when(FlagsFactory::getFlags);

        // Setting bids, in this case the odd bids will be made negative by scoring logic
        List<Double> bidsForBuyer1 = ImmutableList.of(1.0, 9.0);
        List<Double> bidsForBuyer2 = ImmutableList.of(3.0, 5.0, 7.0);

        // This scoring logic assigns negative score to odd bids
        String makeOddBidsNegativeScoreJs =
                "function scoreAd(ad, bid, auction_config, seller_signals, "
                        + "trusted_scoring_signals, contextual_signal, user_signal, "
                        + "custom_audience_signal) { \n"
                        + "  return {'status': 0, 'score': (bid % 2 == 0) ? bid : -bid };\n"
                        + "}";

        Dispatcher dispatcher =
                new Dispatcher() {
                    @Override
                    public MockResponse dispatch(RecordedRequest request) {

                        switch (request.getPath()) {
                            case SELLER_DECISION_LOGIC_URL:
                                return new MockResponse().setBody(makeOddBidsNegativeScoreJs);
                            case BUYER_BIDDING_LOGIC_URL_PREFIX + BUYER_1:
                            case BUYER_BIDDING_LOGIC_URL_PREFIX + BUYER_2:
                                return new MockResponse().setBody(READ_BID_FROM_AD_METADATA_JS);
                        }
                        return new MockResponse().setResponseCode(404);
                    }
                };

        mMockWebServerRule.startMockWebServer(dispatcher);

        DBCustomAudience dBCustomAudienceForBuyer1 =
                createDBCustomAudience(
                        BUYER_1,
                        mMockWebServerRule.uriForPath(BUYER_BIDDING_LOGIC_URL_PREFIX + BUYER_1),
                        bidsForBuyer1);
        DBCustomAudience dBCustomAudienceForBuyer2 =
                createDBCustomAudience(
                        BUYER_2,
                        mMockWebServerRule.uriForPath(BUYER_BIDDING_LOGIC_URL_PREFIX + BUYER_2),
                        bidsForBuyer2);

        // Populating the Custom Audience DB
        mCustomAudienceDao.insertOrOverwriteCustomAudience(
                dBCustomAudienceForBuyer1, CustomAudienceFixture.VALID_DAILY_UPDATE_URL);
        mCustomAudienceDao.insertOrOverwriteCustomAudience(
                dBCustomAudienceForBuyer2, CustomAudienceFixture.VALID_DAILY_UPDATE_URL);

        AdSelectionTestCallback resultsCallback =
                invokeRunAdSelection(mAdSelectionService, mAdSelectionConfig);

        Assert.assertFalse(resultsCallback.mIsSuccess);
        Assert.assertEquals(
                FAILURE_RESPONSE, resultsCallback.mFledgeErrorResponse.getErrorMessage());
    }

    /**
     * @param buyer The name of the buyer for this Custom Audience
     * @param biddingUri path from where the bidding logic for this CA can be fetched from
     * @param bids these bids, are added to its metadata. Our JS logic then picks this value and
     *     creates ad with the provided value as bid
     * @return a real Custom Audience object that can be persisted and used in bidding and scoring
     */
    private DBCustomAudience createDBCustomAudience(
            final String buyer, final Uri biddingUri, List<Double> bids) {

        // Generate ads for with bids provided
        List<DBAdData> ads = new ArrayList<>();

        // Create ads with the buyer name and bid number as the ad URL
        // Add the bid value to the metadata
        for (int i = 0; i < bids.size(); i++) {
            ads.add(
                    new DBAdData(
                            Uri.parse(AD_URL_PREFIX + buyer + "/ad" + (i + 1)),
                            "{\"result\":" + bids.get(i) + "}"));
        }

        return new DBCustomAudience.Builder()
                .setOwner(buyer + CustomAudienceFixture.VALID_OWNER)
                .setBuyer(buyer)
                .setName(buyer + CustomAudienceFixture.VALID_NAME)
                .setActivationTime(CustomAudienceFixture.VALID_ACTIVATION_TIME)
                .setExpirationTime(CustomAudienceFixture.VALID_EXPIRATION_TIME)
                .setCreationTime(CommonFixture.FIXED_NOW_TRUNCATED_TO_MILLI)
                .setLastAdsAndBiddingDataUpdatedTime(CommonFixture.FIXED_NOW_TRUNCATED_TO_MILLI)
                .setUserBiddingSignals(CustomAudienceFixture.VALID_USER_BIDDING_SIGNALS)
                .setTrustedBiddingData(
                        new DBTrustedBiddingData.Builder()
                                .setUrl(TrustedBiddingDataFixture.VALID_TRUSTED_BIDDING_URL)
                                .setKeys(TrustedBiddingDataFixture.VALID_TRUSTED_BIDDING_KEYS)
                                .build())
                .setBiddingLogicUrl(biddingUri)
                .setAds(ads)
                .build();
    }

    private AdSelectionTestCallback invokeRunAdSelection(
            AdSelectionServiceImpl adSelectionService, AdSelectionConfig adSelectionConfig)
            throws InterruptedException {

        CountDownLatch countdownLatch = new CountDownLatch(1);
        AdSelectionTestCallback adSelectionTestCallback =
                new AdSelectionTestCallback(countdownLatch);

        adSelectionService.runAdSelection(adSelectionConfig, adSelectionTestCallback);
        adSelectionTestCallback.mCountDownLatch.await();
        return adSelectionTestCallback;
    }

    static class AdSelectionTestCallback extends AdSelectionCallback.Stub {

        final CountDownLatch mCountDownLatch;
        boolean mIsSuccess = false;
        AdSelectionResponse mAdSelectionResponse;
        FledgeErrorResponse mFledgeErrorResponse;

        AdSelectionTestCallback(CountDownLatch countDownLatch) {
            mCountDownLatch = countDownLatch;
            mAdSelectionResponse = null;
            mFledgeErrorResponse = null;
        }

        @Override
        public void onSuccess(AdSelectionResponse adSelectionResponse) throws RemoteException {
            mIsSuccess = true;
            mAdSelectionResponse = adSelectionResponse;
            mCountDownLatch.countDown();
        }

        @Override
        public void onFailure(FledgeErrorResponse fledgeErrorResponse) throws RemoteException {
            mIsSuccess = false;
            mFledgeErrorResponse = fledgeErrorResponse;
            mCountDownLatch.countDown();
        }
    }
}

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

import static android.adservices.common.AdServicesStatusUtils.STATUS_INVALID_ARGUMENT;

import static com.android.adservices.service.adselection.AdSelectionRunner.ERROR_AD_SELECTION_FAILURE;
import static com.android.adservices.service.adselection.AdSelectionRunner.ERROR_NO_BUYERS_AVAILABLE;
import static com.android.adservices.service.adselection.AdSelectionRunner.ERROR_NO_CA_AVAILABLE;
import static com.android.adservices.service.adselection.AdSelectionRunner.ERROR_NO_VALID_BIDS_FOR_SCORING;
import static com.android.adservices.service.adselection.AdSelectionRunner.ERROR_NO_WINNING_AD_FOUND;
import static com.android.adservices.service.adselection.AdsScoreGeneratorImpl.MISSING_TRUSTED_SCORING_SIGNALS;
import static com.android.adservices.service.stats.AdServicesStatsLog.AD_SERVICES_API_CALLED__API_NAME__RUN_AD_SELECTION;
import static com.android.adservices.stats.FledgeApiCallStatsMatcher.aCallStatForFledgeApiWithStatus;
import static com.android.dx.mockito.inline.extended.ExtendedMockito.doReturn;
import static com.android.dx.mockito.inline.extended.ExtendedMockito.when;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertThrows;
import static org.junit.Assert.assertTrue;
import static org.mockito.Mockito.verify;

import android.adservices.adselection.AdSelectionCallback;
import android.adservices.adselection.AdSelectionConfig;
import android.adservices.adselection.AdSelectionConfigFixture;
import android.adservices.adselection.AdSelectionResponse;
import android.adservices.common.AdServicesStatusUtils;
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
import com.android.adservices.service.Flags;
import com.android.adservices.service.FlagsFactory;
import com.android.adservices.service.common.AdServicesHttpsClient;
import com.android.adservices.service.devapi.AdSelectionDevOverridesHelper;
import com.android.adservices.service.devapi.DevContext;
import com.android.adservices.service.devapi.DevContextFilter;
import com.android.adservices.service.stats.AdServicesLogger;
import com.android.adservices.service.stats.AdServicesLoggerImpl;
import com.android.dx.mockito.inline.extended.ExtendedMockito;

import com.google.common.collect.ImmutableList;
import com.google.common.truth.Truth;
import com.google.mockwebserver.Dispatcher;
import com.google.mockwebserver.MockResponse;
import com.google.mockwebserver.RecordedRequest;

import org.junit.After;
import org.junit.Before;
import org.junit.Rule;
import org.junit.Test;
import org.mockito.Mock;
import org.mockito.Mockito;
import org.mockito.MockitoSession;
import org.mockito.Spy;

import java.time.Instant;
import java.util.ArrayList;
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
    private static final String ERROR_SCORE_AD_LOGIC_MISSING = "scoreAd is not defined";

    private static final String BUYER_1 = AdSelectionConfigFixture.BUYER_1;
    private static final String BUYER_2 = AdSelectionConfigFixture.BUYER_2;
    private static final String BUYER_3 = AdSelectionConfigFixture.BUYER_3;
    private static final String BUYER = "buyer";
    private static final String AD_URI_PREFIX = "http://www.domain.com/adverts/123/";

    private static final String BUYER_BIDDING_LOGIC_URI_PATH = "/buyer/bidding/logic/";
    private static final String BUYER_TRUSTED_SIGNAL_URI_PATH = "/kv/buyer/signals/";
    private static final String BUYER_TRUSTED_SIGNAL_PARAMS =
            "?keys=example%2Cvalid%2Clist%2Cof%2Ckeys";

    private static final String SELLER_DECISION_LOGIC_URI_PATH = "/ssp/decision/logic/";
    private static final String SELLER_TRUSTED_SIGNAL_URI_PATH = "/kv/seller/signals/";
    private static final String SELLER_TRUSTED_SIGNAL_PARAMS = "?renderurls=";

    private static final String READ_BID_FROM_AD_METADATA_JS =
            "function generateBid(ad, auction_signals, per_buyer_signals,"
                    + " trusted_bidding_signals, contextual_signals, user_signals,"
                    + " custom_audience_signals) { \n"
                    + "  return {'status': 0, 'ad': ad, 'bid': ad.metadata.result };\n"
                    + "}";

    private static final String USE_BID_AS_SCORE_JS =
            "function scoreAd(ad, bid, auction_config, seller_signals, "
                    + "trusted_scoring_signals, contextual_signal, user_signal, "
                    + "custom_audience_signal) { \n"
                    + "  return {'status': 0, 'score': bid };\n"
                    + "}";

    private static final String TRUSTED_BIDDING_SIGNALS =
            "{\n"
                    + "\t\"example\": \"example\",\n"
                    + "\t\"valid\": \"Also valid\",\n"
                    + "\t\"list\": \"list\",\n"
                    + "\t\"of\": \"of\",\n"
                    + "\t\"keys\": \"trusted bidding signal Values\"\n"
                    + "}";

    private static final String TRUSTED_SCORING_SIGNALS =
            "{\n"
                    + "\t\"render_url_1\": \"signals_for_1\",\n"
                    + "\t\"render_url_2\": \"signals_for_2\"\n"
                    + "}";

    private static final String SELLER_VALID = "developer.android.com";
    private static final Uri DECISION_LOGIC_URI_INCONSISTENT =
            Uri.parse("https://developer%$android.com/test/decisions_logic_urls");
    @Spy private final AdServicesLogger mAdServicesLogger = AdServicesLoggerImpl.getInstance();
    @Rule public MockWebServerRule mMockWebServerRule = MockWebServerRuleFactory.createForHttps();
    // Mocking DevContextFilter to test behavior with and without override api authorization
    @Mock DevContextFilter mDevContextFilter;
    private MockitoSession mStaticMockSession = null;
    private Context mContext;
    private ExecutorService mExecutorService;
    private CustomAudienceDao mCustomAudienceDao;
    private AdSelectionEntryDao mAdSelectionEntryDao;
    private AdServicesHttpsClient mAdServicesHttpsClient;
    private AdSelectionConfig mAdSelectionConfig;
    private AdSelectionServiceImpl mAdSelectionService;
    private Dispatcher mDispatcher;
    private Flags mFlags;

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
        mExecutorService = Executors.newFixedThreadPool(10);
        mCustomAudienceDao =
                Room.inMemoryDatabaseBuilder(mContext, CustomAudienceDatabase.class)
                        .build()
                        .customAudienceDao();

        mAdSelectionEntryDao =
                Room.inMemoryDatabaseBuilder(mContext, AdSelectionDatabase.class)
                        .build()
                        .adSelectionEntryDao();

        mAdServicesHttpsClient = new AdServicesHttpsClient(mExecutorService);

        when(mDevContextFilter.createDevContext())
                .thenReturn(DevContext.createForDevOptionsDisabled());

        mFlags = FlagsFactory.getFlagsForTest();

        // Create an instance of AdSelection Service with real dependencies
        mAdSelectionService =
                new AdSelectionServiceImpl(
                        mAdSelectionEntryDao,
                        mCustomAudienceDao,
                        mAdServicesHttpsClient,
                        mDevContextFilter,
                        mExecutorService,
                        mContext,
                        mAdServicesLogger,
                        mFlags);

        // Create a dispatcher that helps map a request -> response in mockWebServer
        mDispatcher =
                new Dispatcher() {
                    @Override
                    public MockResponse dispatch(RecordedRequest request) {

                        switch (request.getPath()) {
                            case SELLER_DECISION_LOGIC_URI_PATH:
                                return new MockResponse().setBody(USE_BID_AS_SCORE_JS);
                            case BUYER_BIDDING_LOGIC_URI_PATH + BUYER_1:
                            case BUYER_BIDDING_LOGIC_URI_PATH + BUYER_2:
                                return new MockResponse().setBody(READ_BID_FROM_AD_METADATA_JS);
                            case BUYER_TRUSTED_SIGNAL_URI_PATH + BUYER_TRUSTED_SIGNAL_PARAMS:
                                return new MockResponse().setBody(TRUSTED_BIDDING_SIGNALS);
                        }

                        // The seller params vary based on runtime, so we are returning trusted
                        // signals based on correct path prefix
                        if (request.getPath()
                                .startsWith(
                                        SELLER_TRUSTED_SIGNAL_URI_PATH
                                                + SELLER_TRUSTED_SIGNAL_PARAMS)) {
                            return new MockResponse().setBody(TRUSTED_SCORING_SIGNALS);
                        }
                        return new MockResponse().setResponseCode(404);
                    }
                };

        // Create an Ad Selection Config with the buyers and decision logic url
        // the url points to a JS with score generation logic
        mAdSelectionConfig =
                AdSelectionConfigFixture.anAdSelectionConfigBuilder()
                        .setCustomAudienceBuyers(ImmutableList.of(BUYER_1, BUYER_2, BUYER_3))
                        .setSeller(
                                mMockWebServerRule
                                        .uriForPath(SELLER_DECISION_LOGIC_URI_PATH)
                                        .getHost())
                        .setDecisionLogicUri(
                                mMockWebServerRule.uriForPath(SELLER_DECISION_LOGIC_URI_PATH))
                        .setTrustedScoringSignalsUri(
                                mMockWebServerRule.uriForPath(SELLER_TRUSTED_SIGNAL_URI_PATH))
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
                        mMockWebServerRule.uriForPath(BUYER_BIDDING_LOGIC_URI_PATH + BUYER_1),
                        bidsForBuyer1);
        DBCustomAudience dBCustomAudienceForBuyer2 =
                createDBCustomAudience(
                        BUYER_2,
                        mMockWebServerRule.uriForPath(BUYER_BIDDING_LOGIC_URI_PATH + BUYER_2),
                        bidsForBuyer2);

        // Populating the Custom Audience DB
        mCustomAudienceDao.insertOrOverwriteCustomAudience(
                dBCustomAudienceForBuyer1, CustomAudienceFixture.VALID_DAILY_UPDATE_URL);
        mCustomAudienceDao.insertOrOverwriteCustomAudience(
                dBCustomAudienceForBuyer2, CustomAudienceFixture.VALID_DAILY_UPDATE_URL);

        AdSelectionTestCallback resultsCallback =
                invokeRunAdSelection(mAdSelectionService, mAdSelectionConfig);

        assertTrue(resultsCallback.mIsSuccess);
        long resultSelectionId = resultsCallback.mAdSelectionResponse.getAdSelectionId();
        assertTrue(mAdSelectionEntryDao.doesAdSelectionIdExist(resultSelectionId));
        assertEquals(
                AD_URI_PREFIX + "buyer2/ad3",
                resultsCallback.mAdSelectionResponse.getRenderUri().toString());
    }

    @Test
    public void testRunAdSelectionMultipleCAsSuccess() throws Exception {
        doReturn(FlagsFactory.getFlagsForTest()).when(FlagsFactory::getFlags);
        mMockWebServerRule.startMockWebServer(mDispatcher);
        List<Double> bidsForBuyer1 = ImmutableList.of(1.1, 2.2);
        List<Double> bidsForBuyer2 = ImmutableList.of(4.5, 6.7, 10.0);

        DBCustomAudience dBCustomAudienceForBuyer1 =
                createDBCustomAudience(
                        BUYER_1,
                        mMockWebServerRule.uriForPath(BUYER_BIDDING_LOGIC_URI_PATH + BUYER_1),
                        bidsForBuyer1);
        DBCustomAudience dBCustomAudienceForBuyer2 =
                createDBCustomAudience(
                        BUYER_2,
                        mMockWebServerRule.uriForPath(BUYER_BIDDING_LOGIC_URI_PATH + BUYER_2),
                        bidsForBuyer2);

        // Populating the Custom Audience DB
        mCustomAudienceDao.insertOrOverwriteCustomAudience(
                dBCustomAudienceForBuyer1, CustomAudienceFixture.VALID_DAILY_UPDATE_URL);
        mCustomAudienceDao.insertOrOverwriteCustomAudience(
                dBCustomAudienceForBuyer2, CustomAudienceFixture.VALID_DAILY_UPDATE_URL);

        Mockito.lenient()
                .when(mDevContextFilter.createDevContext())
                .thenReturn(DevContext.createForDevOptionsDisabled());

        List<String> participatingBuyers = new ArrayList<>();
        participatingBuyers.add(BUYER_1);
        participatingBuyers.add(BUYER_2);

        for (int i = 3; i <= 50; i++) {
            DBCustomAudience dBCustomAudienceForBuyerX =
                    createDBCustomAudience(
                            BUYER + i,
                            mMockWebServerRule.uriForPath(BUYER_BIDDING_LOGIC_URI_PATH + BUYER_1),
                            bidsForBuyer1);
            mCustomAudienceDao.insertOrOverwriteCustomAudience(
                    dBCustomAudienceForBuyerX, CustomAudienceFixture.VALID_DAILY_UPDATE_URL);

            participatingBuyers.add(BUYER + i);
        }

        AdSelectionConfig adSelectionConfig =
                AdSelectionConfigFixture.anAdSelectionConfigBuilder()
                        .setCustomAudienceBuyers(participatingBuyers)
                        .setSeller(
                                mMockWebServerRule
                                        .uriForPath(SELLER_DECISION_LOGIC_URI_PATH)
                                        .getHost())
                        .setDecisionLogicUri(
                                mMockWebServerRule.uriForPath(SELLER_DECISION_LOGIC_URI_PATH))
                        .setTrustedScoringSignalsUri(
                                mMockWebServerRule.uriForPath(SELLER_TRUSTED_SIGNAL_URI_PATH))
                        .build();

        AdSelectionTestCallback resultsCallback =
                invokeRunAdSelection(mAdSelectionService, adSelectionConfig);

        assertTrue(resultsCallback.mIsSuccess);
        long resultSelectionId = resultsCallback.mAdSelectionResponse.getAdSelectionId();
        assertTrue(mAdSelectionEntryDao.doesAdSelectionIdExist(resultSelectionId));
        assertEquals(
                AD_URI_PREFIX + "buyer2/ad3",
                resultsCallback.mAdSelectionResponse.getRenderUri().toString());
    }

    @Test
    public void testRunAdSelectionSucceedsWithOverride() throws Exception {
        doReturn(FlagsFactory.getFlagsForTest()).when(FlagsFactory::getFlags);

        List<Double> bidsForBuyer1 = ImmutableList.of(1.1, 2.2);
        List<Double> bidsForBuyer2 = ImmutableList.of(4.5, 6.7, 10.0);

        mMockWebServerRule.startMockWebServer(mDispatcher);

        DBCustomAudience dBCustomAudienceForBuyer1 =
                createDBCustomAudience(
                        BUYER_1,
                        mMockWebServerRule.uriForPath(BUYER_BIDDING_LOGIC_URI_PATH + BUYER_1),
                        bidsForBuyer1);
        DBCustomAudience dBCustomAudienceForBuyer2 =
                createDBCustomAudience(
                        BUYER_2,
                        mMockWebServerRule.uriForPath(BUYER_BIDDING_LOGIC_URI_PATH + BUYER_2),
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
                        .setTrustedScoringSignals(TRUSTED_SCORING_SIGNALS)
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
                        .setTrustedBiddingData(TRUSTED_BIDDING_SIGNALS)
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
                        mAdServicesHttpsClient,
                        mDevContextFilter,
                        mExecutorService,
                        mContext,
                        mAdServicesLogger,
                        mFlags);

        // Populating the Custom Audience DB
        mCustomAudienceDao.insertOrOverwriteCustomAudience(
                dBCustomAudienceForBuyer1, CustomAudienceFixture.VALID_DAILY_UPDATE_URL);
        mCustomAudienceDao.insertOrOverwriteCustomAudience(
                dBCustomAudienceForBuyer2, CustomAudienceFixture.VALID_DAILY_UPDATE_URL);

        AdSelectionTestCallback resultsCallback =
                invokeRunAdSelection(mAdSelectionService, mAdSelectionConfig);

        assertTrue(resultsCallback.mIsSuccess);
        long resultSelectionId = resultsCallback.mAdSelectionResponse.getAdSelectionId();
        assertTrue(mAdSelectionEntryDao.doesAdSelectionIdExist(resultSelectionId));
        assertEquals(
                AD_URI_PREFIX + "buyer2/ad3",
                resultsCallback.mAdSelectionResponse.getRenderUri().toString());
    }

    @Test
    public void testRunAdSelectionActiveCAs() throws Exception {
        doReturn(FlagsFactory.getFlagsForTest()).when(FlagsFactory::getFlags);

        mMockWebServerRule.startMockWebServer(mDispatcher);
        List<Double> bidsForBuyer1 = ImmutableList.of(0.9, 0.45);
        List<Double> bidsForBuyer2 = ImmutableList.of(1.1, 2.2);
        List<Double> bidsForBuyer3 = ImmutableList.of(10.0, 100.0);
        DBCustomAudience dbCustomAudienceActive =
                createDBCustomAudience(
                        BUYER_1,
                        mMockWebServerRule.uriForPath(BUYER_BIDDING_LOGIC_URI_PATH + BUYER_1),
                        bidsForBuyer1);
        DBCustomAudience dBCustomAudienceInactive =
                createDBCustomAudience(
                        BUYER_2,
                        mMockWebServerRule.uriForPath(BUYER_BIDDING_LOGIC_URI_PATH + BUYER_2),
                        bidsForBuyer2,
                        CustomAudienceFixture.INVALID_DELAYED_ACTIVATION_TIME,
                        CustomAudienceFixture.VALID_EXPIRATION_TIME,
                        CustomAudienceFixture.VALID_LAST_UPDATE_TIME_24_HRS_BEFORE);
        DBCustomAudience dBCustomAudienceExpired =
                createDBCustomAudience(
                        BUYER_3,
                        mMockWebServerRule.uriForPath(BUYER_BIDDING_LOGIC_URI_PATH + BUYER_3),
                        bidsForBuyer3,
                        CustomAudienceFixture.VALID_ACTIVATION_TIME,
                        CustomAudienceFixture.INVALID_NOW_EXPIRATION_TIME,
                        CustomAudienceFixture.VALID_LAST_UPDATE_TIME_24_HRS_BEFORE);
        // Populating the Custom Audience DB
        mCustomAudienceDao.insertOrOverwriteCustomAudience(
                dbCustomAudienceActive, CustomAudienceFixture.VALID_DAILY_UPDATE_URL);
        mCustomAudienceDao.insertOrOverwriteCustomAudience(
                dBCustomAudienceInactive, CustomAudienceFixture.VALID_DAILY_UPDATE_URL);
        mCustomAudienceDao.insertOrOverwriteCustomAudience(
                dBCustomAudienceExpired, CustomAudienceFixture.VALID_DAILY_UPDATE_URL);
        AdSelectionTestCallback resultsCallback =
                invokeRunAdSelection(mAdSelectionService, mAdSelectionConfig);

        assertTrue(resultsCallback.mIsSuccess);
        long resultSelectionId = resultsCallback.mAdSelectionResponse.getAdSelectionId();
        assertTrue(mAdSelectionEntryDao.doesAdSelectionIdExist(resultSelectionId));
        assertEquals(
                AD_URI_PREFIX + "buyer1/ad1",
                resultsCallback.mAdSelectionResponse.getRenderUri().toString());
    }

    @Test
    public void testRunAdSelectionNoCAsActive() throws Exception {
        doReturn(FlagsFactory.getFlagsForTest()).when(FlagsFactory::getFlags);

        mMockWebServerRule.startMockWebServer(mDispatcher);
        List<Double> bidsForBuyer1 = ImmutableList.of(1.1, 2.2);
        List<Double> bidsForBuyer2 = ImmutableList.of(4.5, 6.7, 10.0);
        List<Double> bidsForBuyer3 = ImmutableList.of(4.3, 6.0, 10.0);

        DBCustomAudience dBCustomAudienceInactive =
                createDBCustomAudience(
                        BUYER_1,
                        mMockWebServerRule.uriForPath(BUYER_BIDDING_LOGIC_URI_PATH + BUYER_1),
                        bidsForBuyer1,
                        CustomAudienceFixture.INVALID_DELAYED_ACTIVATION_TIME,
                        CustomAudienceFixture.VALID_EXPIRATION_TIME,
                        CustomAudienceFixture.VALID_LAST_UPDATE_TIME_24_HRS_BEFORE);
        DBCustomAudience dBCustomAudienceExpired =
                createDBCustomAudience(
                        BUYER_2,
                        mMockWebServerRule.uriForPath(BUYER_BIDDING_LOGIC_URI_PATH + BUYER_2),
                        bidsForBuyer2,
                        CustomAudienceFixture.VALID_ACTIVATION_TIME,
                        CustomAudienceFixture.INVALID_NOW_EXPIRATION_TIME,
                        CustomAudienceFixture.VALID_LAST_UPDATE_TIME_24_HRS_BEFORE);

        DBCustomAudience dBCustomAudienceOutdated =
                createDBCustomAudience(
                        BUYER_3,
                        mMockWebServerRule.uriForPath(BUYER_BIDDING_LOGIC_URI_PATH + BUYER_3),
                        bidsForBuyer3,
                        CustomAudienceFixture.VALID_ACTIVATION_TIME,
                        CustomAudienceFixture.VALID_EXPIRATION_TIME,
                        CustomAudienceFixture.INVALID_LAST_UPDATE_TIME_72_HRS_BEFORE);
        // Populating the Custom Audience DB
        mCustomAudienceDao.insertOrOverwriteCustomAudience(
                dBCustomAudienceInactive, CustomAudienceFixture.VALID_DAILY_UPDATE_URL);
        mCustomAudienceDao.insertOrOverwriteCustomAudience(
                dBCustomAudienceExpired, CustomAudienceFixture.VALID_DAILY_UPDATE_URL);
        mCustomAudienceDao.insertOrOverwriteCustomAudience(
                dBCustomAudienceOutdated, CustomAudienceFixture.VALID_DAILY_UPDATE_URL);

        AdSelectionTestCallback resultsCallback =
                invokeRunAdSelection(mAdSelectionService, mAdSelectionConfig);

        assertFalse(resultsCallback.mIsSuccess);
        assertEquals(
                resultsCallback.mFledgeErrorResponse.getStatusCode(),
                AdServicesStatusUtils.STATUS_INTERNAL_ERROR);
        verifyErrorMessageIsCorrect(
                resultsCallback.mFledgeErrorResponse.getErrorMessage(),
                String.format(
                        AdSelectionRunner.AD_SELECTION_ERROR_PATTERN,
                        AdSelectionRunner.ERROR_AD_SELECTION_FAILURE,
                        AdSelectionRunner.ERROR_NO_CA_AVAILABLE));
    }

    @Test
    public void testRunAdSelectionNoCAsFailure() throws Exception {
        // Do not populate CustomAudience DAO
        mMockWebServerRule.startMockWebServer(mDispatcher);
        AdSelectionTestCallback resultsCallback =
                invokeRunAdSelection(mAdSelectionService, mAdSelectionConfig);

        assertFalse(resultsCallback.mIsSuccess);
        verifyErrorMessageIsCorrect(
                resultsCallback.mFledgeErrorResponse.getErrorMessage(), ERROR_NO_CA_AVAILABLE);
    }

    @Test
    public void testRunAdSelectionNoBuyersFailure() throws Exception {
        // Do not populate buyers in AdSelectionConfig
        mAdSelectionConfig =
                AdSelectionConfigFixture.anAdSelectionConfigBuilder()
                        .setSeller(
                                mMockWebServerRule
                                        .uriForPath(SELLER_DECISION_LOGIC_URI_PATH)
                                        .getHost())
                        .setDecisionLogicUri(
                                mMockWebServerRule.uriForPath(SELLER_DECISION_LOGIC_URI_PATH))
                        .setTrustedScoringSignalsUri(
                                mMockWebServerRule.uriForPath(SELLER_TRUSTED_SIGNAL_URI_PATH))
                        .setCustomAudienceBuyers(Collections.emptyList())
                        .build();

        mMockWebServerRule.startMockWebServer(mDispatcher);
        AdSelectionTestCallback resultsCallback =
                invokeRunAdSelection(mAdSelectionService, mAdSelectionConfig);

        assertFalse(resultsCallback.mIsSuccess);
        verifyErrorMessageIsCorrect(
                resultsCallback.mFledgeErrorResponse.getErrorMessage(), ERROR_NO_BUYERS_AVAILABLE);
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
                        mMockWebServerRule.uriForPath(BUYER_BIDDING_LOGIC_URI_PATH + BUYER_1),
                        bidsForBuyer1);
        DBCustomAudience dBCustomAudienceForBuyer2 =
                createDBCustomAudience(
                        BUYER_2,
                        mMockWebServerRule.uriForPath(BUYER_BIDDING_LOGIC_URI_PATH + BUYER_2),
                        bidsForBuyer2);

        // Populating the Custom Audience DB
        mCustomAudienceDao.insertOrOverwriteCustomAudience(
                dBCustomAudienceForBuyer1, CustomAudienceFixture.VALID_DAILY_UPDATE_URL);
        mCustomAudienceDao.insertOrOverwriteCustomAudience(
                dBCustomAudienceForBuyer2, CustomAudienceFixture.VALID_DAILY_UPDATE_URL);

        AdSelectionTestCallback resultsCallback =
                invokeRunAdSelection(mAdSelectionService, mAdSelectionConfig);

        assertTrue(resultsCallback.mIsSuccess);
        long resultSelectionId = resultsCallback.mAdSelectionResponse.getAdSelectionId();
        assertTrue(mAdSelectionEntryDao.doesAdSelectionIdExist(resultSelectionId));
        assertEquals(
                AD_URI_PREFIX + "buyer1/ad2",
                resultsCallback.mAdSelectionResponse.getRenderUri().toString());
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
                            case SELLER_DECISION_LOGIC_URI_PATH:
                                return new MockResponse().setBody(USE_BID_AS_SCORE_JS);
                            case BUYER_BIDDING_LOGIC_URI_PATH + BUYER_1:
                            case BUYER_BIDDING_LOGIC_URI_PATH + BUYER_2:
                                return new MockResponse().setBody("");
                            case BUYER_TRUSTED_SIGNAL_URI_PATH + BUYER_TRUSTED_SIGNAL_PARAMS:
                                return new MockResponse().setBody(TRUSTED_BIDDING_SIGNALS);
                        }

                        // The seller params vary based on runtime, so we are returning trusted
                        // signals based on correct path prefix
                        if (request.getPath()
                                .startsWith(
                                        SELLER_TRUSTED_SIGNAL_URI_PATH
                                                + SELLER_TRUSTED_SIGNAL_PARAMS)) {
                            return new MockResponse().setBody(TRUSTED_SCORING_SIGNALS);
                        }
                        return new MockResponse().setResponseCode(404);
                    }
                };

        mMockWebServerRule.startMockWebServer(dispatcher);

        DBCustomAudience dBCustomAudienceForBuyer1 =
                createDBCustomAudience(
                        BUYER_1,
                        mMockWebServerRule.uriForPath(BUYER_BIDDING_LOGIC_URI_PATH + BUYER_1),
                        bidsForBuyer1);
        DBCustomAudience dBCustomAudienceForBuyer2 =
                createDBCustomAudience(
                        BUYER_2,
                        mMockWebServerRule.uriForPath(BUYER_BIDDING_LOGIC_URI_PATH + BUYER_2),
                        bidsForBuyer2);

        // Populating the Custom Audience DB
        mCustomAudienceDao.insertOrOverwriteCustomAudience(
                dBCustomAudienceForBuyer1, CustomAudienceFixture.VALID_DAILY_UPDATE_URL);
        mCustomAudienceDao.insertOrOverwriteCustomAudience(
                dBCustomAudienceForBuyer2, CustomAudienceFixture.VALID_DAILY_UPDATE_URL);

        AdSelectionTestCallback resultsCallback =
                invokeRunAdSelection(mAdSelectionService, mAdSelectionConfig);

        assertFalse(resultsCallback.mIsSuccess);
        verifyErrorMessageIsCorrect(
                resultsCallback.mFledgeErrorResponse.getErrorMessage(),
                ERROR_NO_VALID_BIDS_FOR_SCORING);
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
                            case SELLER_DECISION_LOGIC_URI_PATH:
                                return new MockResponse().setBody("");
                            case BUYER_BIDDING_LOGIC_URI_PATH + BUYER_1:
                            case BUYER_BIDDING_LOGIC_URI_PATH + BUYER_2:
                                return new MockResponse().setBody(READ_BID_FROM_AD_METADATA_JS);
                            case BUYER_TRUSTED_SIGNAL_URI_PATH + BUYER_TRUSTED_SIGNAL_PARAMS:
                                return new MockResponse().setBody(TRUSTED_BIDDING_SIGNALS);
                        }

                        // The seller params vary based on runtime, so we are returning trusted
                        // signals based on correct path prefix
                        if (request.getPath()
                                .startsWith(
                                        SELLER_TRUSTED_SIGNAL_URI_PATH
                                                + SELLER_TRUSTED_SIGNAL_PARAMS)) {
                            return new MockResponse().setBody(TRUSTED_SCORING_SIGNALS);
                        }
                        return new MockResponse().setResponseCode(404);
                    }
                };

        mMockWebServerRule.startMockWebServer(dispatcher);

        DBCustomAudience dBCustomAudienceForBuyer1 =
                createDBCustomAudience(
                        BUYER_1,
                        mMockWebServerRule.uriForPath(BUYER_BIDDING_LOGIC_URI_PATH + BUYER_1),
                        bidsForBuyer1);
        DBCustomAudience dBCustomAudienceForBuyer2 =
                createDBCustomAudience(
                        BUYER_2,
                        mMockWebServerRule.uriForPath(BUYER_BIDDING_LOGIC_URI_PATH + BUYER_2),
                        bidsForBuyer2);

        // Populating the Custom Audience DB
        mCustomAudienceDao.insertOrOverwriteCustomAudience(
                dBCustomAudienceForBuyer1, CustomAudienceFixture.VALID_DAILY_UPDATE_URL);
        mCustomAudienceDao.insertOrOverwriteCustomAudience(
                dBCustomAudienceForBuyer2, CustomAudienceFixture.VALID_DAILY_UPDATE_URL);

        AdSelectionTestCallback resultsCallback =
                invokeRunAdSelection(mAdSelectionService, mAdSelectionConfig);

        assertFalse(resultsCallback.mIsSuccess);
        verifyErrorMessageIsCorrect(
                resultsCallback.mFledgeErrorResponse.getErrorMessage(),
                ERROR_SCORE_AD_LOGIC_MISSING);
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
                            case SELLER_DECISION_LOGIC_URI_PATH:
                                return new MockResponse().setBody(USE_BID_AS_SCORE_JS);
                            case BUYER_BIDDING_LOGIC_URI_PATH + BUYER_1:
                                return new MockResponse().setBody(READ_BID_FROM_AD_METADATA_JS);
                            case BUYER_BIDDING_LOGIC_URI_PATH + BUYER_2:
                                return new MockResponse().setBody("");
                            case BUYER_TRUSTED_SIGNAL_URI_PATH + BUYER_TRUSTED_SIGNAL_PARAMS:
                                return new MockResponse().setBody(TRUSTED_BIDDING_SIGNALS);
                        }

                        // The seller params vary based on runtime, so we are returning trusted
                        // signals based on correct path prefix
                        if (request.getPath()
                                .startsWith(
                                        SELLER_TRUSTED_SIGNAL_URI_PATH
                                                + SELLER_TRUSTED_SIGNAL_PARAMS)) {
                            return new MockResponse().setBody(TRUSTED_SCORING_SIGNALS);
                        }
                        return new MockResponse().setResponseCode(404);
                    }
                };

        mMockWebServerRule.startMockWebServer(dispatcher);

        DBCustomAudience dBCustomAudienceForBuyer1 =
                createDBCustomAudience(
                        BUYER_1,
                        mMockWebServerRule.uriForPath(BUYER_BIDDING_LOGIC_URI_PATH + BUYER_1),
                        bidsForBuyer1);
        DBCustomAudience dBCustomAudienceForBuyer2 =
                createDBCustomAudience(
                        BUYER_2,
                        mMockWebServerRule.uriForPath(BUYER_BIDDING_LOGIC_URI_PATH + BUYER_2),
                        bidsForBuyer2);

        // Populating the Custom Audience DB
        mCustomAudienceDao.insertOrOverwriteCustomAudience(
                dBCustomAudienceForBuyer1, CustomAudienceFixture.VALID_DAILY_UPDATE_URL);
        mCustomAudienceDao.insertOrOverwriteCustomAudience(
                dBCustomAudienceForBuyer2, CustomAudienceFixture.VALID_DAILY_UPDATE_URL);

        AdSelectionTestCallback resultsCallback =
                invokeRunAdSelection(mAdSelectionService, mAdSelectionConfig);

        assertTrue(resultsCallback.mIsSuccess);
        long resultSelectionId = resultsCallback.mAdSelectionResponse.getAdSelectionId();
        assertTrue(mAdSelectionEntryDao.doesAdSelectionIdExist(resultSelectionId));
        assertEquals(
                AD_URI_PREFIX + "buyer1/ad2",
                resultsCallback.mAdSelectionResponse.getRenderUri().toString());
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
                            case SELLER_DECISION_LOGIC_URI_PATH:
                                return new MockResponse().setBody(makeOddBidsNegativeScoreJs);
                            case BUYER_BIDDING_LOGIC_URI_PATH + BUYER_1:
                            case BUYER_BIDDING_LOGIC_URI_PATH + BUYER_2:
                                return new MockResponse().setBody(READ_BID_FROM_AD_METADATA_JS);
                            case BUYER_TRUSTED_SIGNAL_URI_PATH + BUYER_TRUSTED_SIGNAL_PARAMS:
                                return new MockResponse().setBody(TRUSTED_BIDDING_SIGNALS);
                        }

                        // The seller params vary based on runtime, so we are returning trusted
                        // signals based on correct path prefix
                        if (request.getPath()
                                .startsWith(
                                        SELLER_TRUSTED_SIGNAL_URI_PATH
                                                + SELLER_TRUSTED_SIGNAL_PARAMS)) {
                            return new MockResponse().setBody(TRUSTED_SCORING_SIGNALS);
                        }
                        return new MockResponse().setResponseCode(404);
                    }
                };

        mMockWebServerRule.startMockWebServer(dispatcher);

        DBCustomAudience dBCustomAudienceForBuyer1 =
                createDBCustomAudience(
                        BUYER_1,
                        mMockWebServerRule.uriForPath(BUYER_BIDDING_LOGIC_URI_PATH + BUYER_1),
                        bidsForBuyer1);
        DBCustomAudience dBCustomAudienceForBuyer2 =
                createDBCustomAudience(
                        BUYER_2,
                        mMockWebServerRule.uriForPath(BUYER_BIDDING_LOGIC_URI_PATH + BUYER_2),
                        bidsForBuyer2);

        // Populating the Custom Audience DB
        mCustomAudienceDao.insertOrOverwriteCustomAudience(
                dBCustomAudienceForBuyer1, CustomAudienceFixture.VALID_DAILY_UPDATE_URL);
        mCustomAudienceDao.insertOrOverwriteCustomAudience(
                dBCustomAudienceForBuyer2, CustomAudienceFixture.VALID_DAILY_UPDATE_URL);

        AdSelectionTestCallback resultsCallback =
                invokeRunAdSelection(mAdSelectionService, mAdSelectionConfig);

        assertTrue(resultsCallback.mIsSuccess);
        long resultSelectionId = resultsCallback.mAdSelectionResponse.getAdSelectionId();
        assertTrue(mAdSelectionEntryDao.doesAdSelectionIdExist(resultSelectionId));
        assertEquals(
                AD_URI_PREFIX + "buyer1/ad2",
                resultsCallback.mAdSelectionResponse.getRenderUri().toString());
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
                            case SELLER_DECISION_LOGIC_URI_PATH:
                                return new MockResponse().setBody(makeOddBidsNegativeScoreJs);
                            case BUYER_BIDDING_LOGIC_URI_PATH + BUYER_1:
                            case BUYER_BIDDING_LOGIC_URI_PATH + BUYER_2:
                                return new MockResponse().setBody(READ_BID_FROM_AD_METADATA_JS);
                            case BUYER_TRUSTED_SIGNAL_URI_PATH + BUYER_TRUSTED_SIGNAL_PARAMS:
                                return new MockResponse().setBody(TRUSTED_BIDDING_SIGNALS);
                        }

                        // The seller params vary based on runtime, so we are returning trusted
                        // signals based on correct path prefix
                        if (request.getPath()
                                .startsWith(
                                        SELLER_TRUSTED_SIGNAL_URI_PATH
                                                + SELLER_TRUSTED_SIGNAL_PARAMS)) {
                            return new MockResponse().setBody(TRUSTED_SCORING_SIGNALS);
                        }
                        return new MockResponse().setResponseCode(404);
                    }
                };

        mMockWebServerRule.startMockWebServer(dispatcher);

        DBCustomAudience dBCustomAudienceForBuyer1 =
                createDBCustomAudience(
                        BUYER_1,
                        mMockWebServerRule.uriForPath(BUYER_BIDDING_LOGIC_URI_PATH + BUYER_1),
                        bidsForBuyer1);
        DBCustomAudience dBCustomAudienceForBuyer2 =
                createDBCustomAudience(
                        BUYER_2,
                        mMockWebServerRule.uriForPath(BUYER_BIDDING_LOGIC_URI_PATH + BUYER_2),
                        bidsForBuyer2);

        // Populating the Custom Audience DB
        mCustomAudienceDao.insertOrOverwriteCustomAudience(
                dBCustomAudienceForBuyer1, CustomAudienceFixture.VALID_DAILY_UPDATE_URL);
        mCustomAudienceDao.insertOrOverwriteCustomAudience(
                dBCustomAudienceForBuyer2, CustomAudienceFixture.VALID_DAILY_UPDATE_URL);

        AdSelectionTestCallback resultsCallback =
                invokeRunAdSelection(mAdSelectionService, mAdSelectionConfig);

        assertFalse(resultsCallback.mIsSuccess);
        verifyErrorMessageIsCorrect(
                resultsCallback.mFledgeErrorResponse.getErrorMessage(), ERROR_NO_WINNING_AD_FOUND);
    }

    @Test
    public void testRunAdSelectionJSTimesOut() throws Exception {
        doReturn(FlagsFactory.getFlagsForTest()).when(FlagsFactory::getFlags);

        String readBidFromAdMetadataWithDelayJs =
                "function generateBid(ad, auction_signals, per_buyer_signals,"
                        + " trusted_bidding_signals, contextual_signals, user_signals,"
                        + " custom_audience_signals) { \n"
                        + "    const wait = (ms) => {\n"
                        + "       var start = new Date().getTime();\n"
                        + "       var end = start;\n"
                        + "       while(end < start + ms) {\n"
                        + "         end = new Date().getTime();\n"
                        + "      }\n"
                        + "    }\n"
                        + "    wait(15000);\n"
                        + "  return {'status': 0, 'ad': ad, 'bid': ad.metadata.result };\n"
                        + "}";

        // In this case the one buyer's logic takes more than the bidding time limit
        Dispatcher dispatcher =
                new Dispatcher() {
                    @Override
                    public MockResponse dispatch(RecordedRequest request) {

                        switch (request.getPath()) {
                            case SELLER_DECISION_LOGIC_URI_PATH:
                                return new MockResponse().setBody(USE_BID_AS_SCORE_JS);
                            case BUYER_BIDDING_LOGIC_URI_PATH + BUYER_1:
                                return new MockResponse().setBody(readBidFromAdMetadataWithDelayJs);
                            case BUYER_BIDDING_LOGIC_URI_PATH + BUYER_2:
                                return new MockResponse().setBody(READ_BID_FROM_AD_METADATA_JS);
                            case BUYER_TRUSTED_SIGNAL_URI_PATH + BUYER_TRUSTED_SIGNAL_PARAMS:
                                return new MockResponse().setBody(TRUSTED_BIDDING_SIGNALS);
                        }

                        // The seller params vary based on runtime, so we are returning trusted
                        // signals based on correct path prefix
                        if (request.getPath()
                                .startsWith(
                                        SELLER_TRUSTED_SIGNAL_URI_PATH
                                                + SELLER_TRUSTED_SIGNAL_PARAMS)) {
                            return new MockResponse().setBody(TRUSTED_SCORING_SIGNALS);
                        }
                        return new MockResponse().setResponseCode(404);
                    }
                };

        mMockWebServerRule.startMockWebServer(dispatcher);
        // buyer1/ad3 is clear winner but will time out
        List<Double> bidsForBuyer1 = ImmutableList.of(1.1, 2.2, 15.0);
        // due to timeout buyer2/ad3 will win
        List<Double> bidsForBuyer2 = ImmutableList.of(4.5, 6.7, 10.0);

        DBCustomAudience dBCustomAudienceForBuyer1 =
                createDBCustomAudience(
                        BUYER_1,
                        mMockWebServerRule.uriForPath(BUYER_BIDDING_LOGIC_URI_PATH + BUYER_1),
                        bidsForBuyer1);
        DBCustomAudience dBCustomAudienceForBuyer2 =
                createDBCustomAudience(
                        BUYER_2,
                        mMockWebServerRule.uriForPath(BUYER_BIDDING_LOGIC_URI_PATH + BUYER_2),
                        bidsForBuyer2);

        // Populating the Custom Audience DB
        mCustomAudienceDao.insertOrOverwriteCustomAudience(
                dBCustomAudienceForBuyer1, CustomAudienceFixture.VALID_DAILY_UPDATE_URL);
        mCustomAudienceDao.insertOrOverwriteCustomAudience(
                dBCustomAudienceForBuyer2, CustomAudienceFixture.VALID_DAILY_UPDATE_URL);

        AdSelectionTestCallback resultsCallback =
                invokeRunAdSelection(mAdSelectionService, mAdSelectionConfig);

        assertTrue(resultsCallback.mIsSuccess);
        assertEquals(
                AD_URI_PREFIX + "buyer2/ad3",
                resultsCallback.mAdSelectionResponse.getRenderUri().toString());
    }

    @Test
    public void testAdSelectionConfigInvalidSellerAndSellerUrls() throws Exception {
        AdSelectionConfig invalidAdSelectionConfig =
                AdSelectionConfigFixture.anAdSelectionConfigBuilder()
                        .setSeller(SELLER_VALID)
                        .setDecisionLogicUri(DECISION_LOGIC_URI_INCONSISTENT)
                        .build();

        Mockito.lenient()
                .when(mDevContextFilter.createDevContext())
                .thenReturn(DevContext.createForDevOptionsDisabled());
        IllegalArgumentException thrown =
                assertThrows(
                        IllegalArgumentException.class,
                        () -> invokeRunAdSelection(mAdSelectionService, invalidAdSelectionConfig));
        String expected =
                String.format(
                        "Invalid object of type %s. The violations are: %s",
                        AdSelectionConfig.class.getName(),
                        ImmutableList.of(
                                String.format(
                                        AdSelectionConfigValidator
                                                .SELLER_AND_DECISION_LOGIC_URL_ARE_INCONSISTENT,
                                        Uri.parse("https://" + SELLER_VALID).getHost(),
                                        DECISION_LOGIC_URI_INCONSISTENT.getHost())));
        Truth.assertThat(thrown).hasMessageThat().isEqualTo(expected);
        verify(mAdServicesLogger)
                .logFledgeApiCallStats(
                        AD_SERVICES_API_CALLED__API_NAME__RUN_AD_SELECTION,
                        STATUS_INVALID_ARGUMENT);
        verify(mAdServicesLogger)
                .logApiCallStats(
                        aCallStatForFledgeApiWithStatus(
                                AD_SERVICES_API_CALLED__API_NAME__RUN_AD_SELECTION,
                                STATUS_INVALID_ARGUMENT));
    }

    @Test
    public void testRunAdSelectionMissingBiddingSignalsFailure() throws Exception {
        doReturn(FlagsFactory.getFlagsForTest()).when(FlagsFactory::getFlags);

        // Create a dispatcher without buyer trusted Signal end point
        Dispatcher missingBiddingSignalsDispatcher =
                new Dispatcher() {
                    @Override
                    public MockResponse dispatch(RecordedRequest request) {

                        switch (request.getPath()) {
                            case SELLER_DECISION_LOGIC_URI_PATH:
                                return new MockResponse().setBody(USE_BID_AS_SCORE_JS);
                            case BUYER_BIDDING_LOGIC_URI_PATH + BUYER_1:
                            case BUYER_BIDDING_LOGIC_URI_PATH + BUYER_2:
                                return new MockResponse().setBody(READ_BID_FROM_AD_METADATA_JS);
                        }

                        // The seller params vary based on runtime, so we are returning trusted
                        // signals based on correct path prefix
                        if (request.getPath()
                                .startsWith(
                                        SELLER_TRUSTED_SIGNAL_URI_PATH
                                                + SELLER_TRUSTED_SIGNAL_PARAMS)) {
                            return new MockResponse().setBody(TRUSTED_SCORING_SIGNALS);
                        }
                        return new MockResponse().setResponseCode(404);
                    }
                };

        mMockWebServerRule.startMockWebServer(missingBiddingSignalsDispatcher);
        List<Double> bidsForBuyer1 = ImmutableList.of(1.1, 2.2);
        List<Double> bidsForBuyer2 = ImmutableList.of(4.5, 6.7, 10.0);

        DBCustomAudience dBCustomAudienceForBuyer1 =
                createDBCustomAudience(
                        BUYER_1,
                        mMockWebServerRule.uriForPath(BUYER_BIDDING_LOGIC_URI_PATH + BUYER_1),
                        bidsForBuyer1);
        DBCustomAudience dBCustomAudienceForBuyer2 =
                createDBCustomAudience(
                        BUYER_2,
                        mMockWebServerRule.uriForPath(BUYER_BIDDING_LOGIC_URI_PATH + BUYER_2),
                        bidsForBuyer2);

        // Populating the Custom Audience DB
        mCustomAudienceDao.insertOrOverwriteCustomAudience(
                dBCustomAudienceForBuyer1, CustomAudienceFixture.VALID_DAILY_UPDATE_URL);
        mCustomAudienceDao.insertOrOverwriteCustomAudience(
                dBCustomAudienceForBuyer2, CustomAudienceFixture.VALID_DAILY_UPDATE_URL);

        AdSelectionTestCallback resultsCallback =
                invokeRunAdSelection(mAdSelectionService, mAdSelectionConfig);

        assertFalse(resultsCallback.mIsSuccess);
        verifyErrorMessageIsCorrect(
                resultsCallback.mFledgeErrorResponse.getErrorMessage(),
                ERROR_NO_VALID_BIDS_FOR_SCORING);
    }

    @Test
    public void testRunAdSelectionMissingScoringSignalsFailure() throws Exception {
        doReturn(FlagsFactory.getFlagsForTest()).when(FlagsFactory::getFlags);

        // Create a dispatcher without buyer trusted Signal end point
        Dispatcher missingScoringSignalsDispatcher =
                new Dispatcher() {
                    @Override
                    public MockResponse dispatch(RecordedRequest request) {

                        switch (request.getPath()) {
                            case SELLER_DECISION_LOGIC_URI_PATH:
                                return new MockResponse().setBody(USE_BID_AS_SCORE_JS);
                            case BUYER_BIDDING_LOGIC_URI_PATH + BUYER_1:
                            case BUYER_BIDDING_LOGIC_URI_PATH + BUYER_2:
                                return new MockResponse().setBody(READ_BID_FROM_AD_METADATA_JS);
                            case BUYER_TRUSTED_SIGNAL_URI_PATH + BUYER_TRUSTED_SIGNAL_PARAMS:
                                return new MockResponse().setBody(TRUSTED_BIDDING_SIGNALS);
                        }
                        return new MockResponse().setResponseCode(404);
                    }
                };

        mMockWebServerRule.startMockWebServer(missingScoringSignalsDispatcher);
        List<Double> bidsForBuyer1 = ImmutableList.of(1.1, 2.2);
        List<Double> bidsForBuyer2 = ImmutableList.of(4.5, 6.7, 10.0);

        DBCustomAudience dBCustomAudienceForBuyer1 =
                createDBCustomAudience(
                        BUYER_1,
                        mMockWebServerRule.uriForPath(BUYER_BIDDING_LOGIC_URI_PATH + BUYER_1),
                        bidsForBuyer1);
        DBCustomAudience dBCustomAudienceForBuyer2 =
                createDBCustomAudience(
                        BUYER_2,
                        mMockWebServerRule.uriForPath(BUYER_BIDDING_LOGIC_URI_PATH + BUYER_2),
                        bidsForBuyer2);

        // Populating the Custom Audience DB
        mCustomAudienceDao.insertOrOverwriteCustomAudience(
                dBCustomAudienceForBuyer1, CustomAudienceFixture.VALID_DAILY_UPDATE_URL);
        mCustomAudienceDao.insertOrOverwriteCustomAudience(
                dBCustomAudienceForBuyer2, CustomAudienceFixture.VALID_DAILY_UPDATE_URL);

        AdSelectionTestCallback resultsCallback =
                invokeRunAdSelection(mAdSelectionService, mAdSelectionConfig);

        assertFalse(resultsCallback.mIsSuccess);
        verifyErrorMessageIsCorrect(
                resultsCallback.mFledgeErrorResponse.getErrorMessage(),
                MISSING_TRUSTED_SCORING_SIGNALS);
    }

    @Test
    public void testRunAdSelectionMissingPartialBiddingSignalsSuccess() throws Exception {
        doReturn(FlagsFactory.getFlagsForTest()).when(FlagsFactory::getFlags);

        // Create a dispatcher with valid end points
        Dispatcher missingBiddingSignalsDispatcher =
                new Dispatcher() {
                    @Override
                    public MockResponse dispatch(RecordedRequest request) {

                        switch (request.getPath()) {
                            case SELLER_DECISION_LOGIC_URI_PATH:
                                return new MockResponse().setBody(USE_BID_AS_SCORE_JS);
                            case BUYER_BIDDING_LOGIC_URI_PATH + BUYER_1:
                            case BUYER_BIDDING_LOGIC_URI_PATH + BUYER_2:
                                return new MockResponse().setBody(READ_BID_FROM_AD_METADATA_JS);
                            case BUYER_TRUSTED_SIGNAL_URI_PATH + BUYER_TRUSTED_SIGNAL_PARAMS:
                                return new MockResponse().setBody(TRUSTED_BIDDING_SIGNALS);
                        }

                        // The seller params vary based on runtime, so we are returning trusted
                        // signals based on correct path prefix
                        if (request.getPath()
                                .startsWith(
                                        SELLER_TRUSTED_SIGNAL_URI_PATH
                                                + SELLER_TRUSTED_SIGNAL_PARAMS)) {
                            return new MockResponse().setBody(TRUSTED_SCORING_SIGNALS);
                        }
                        return new MockResponse().setResponseCode(404);
                    }
                };

        mMockWebServerRule.startMockWebServer(missingBiddingSignalsDispatcher);
        List<Double> bidsForBuyer1 = ImmutableList.of(1.1, 2.2);
        List<Double> bidsForBuyer2 = ImmutableList.of(4.5, 6.7, 10.0);

        DBCustomAudience dBCustomAudienceForBuyer1 =
                createDBCustomAudience(
                        BUYER_1,
                        mMockWebServerRule.uriForPath(BUYER_BIDDING_LOGIC_URI_PATH + BUYER_1),
                        bidsForBuyer1);
        DBCustomAudience dBCustomAudienceForBuyer2 =
                createDBCustomAudience(
                        BUYER_2,
                        // Invalid trusted bidding signal path for buyer 2
                        mMockWebServerRule.uriForPath(""),
                        bidsForBuyer2);

        // Populating the Custom Audience DB
        mCustomAudienceDao.insertOrOverwriteCustomAudience(
                dBCustomAudienceForBuyer1, CustomAudienceFixture.VALID_DAILY_UPDATE_URL);
        mCustomAudienceDao.insertOrOverwriteCustomAudience(
                dBCustomAudienceForBuyer2, CustomAudienceFixture.VALID_DAILY_UPDATE_URL);

        AdSelectionTestCallback resultsCallback =
                invokeRunAdSelection(mAdSelectionService, mAdSelectionConfig);

        assertTrue(resultsCallback.mIsSuccess);
        long resultSelectionId = resultsCallback.mAdSelectionResponse.getAdSelectionId();
        assertTrue(mAdSelectionEntryDao.doesAdSelectionIdExist(resultSelectionId));
        // Given buyer 2 will be excluded from bidding for missing signals, Buyer 1 : Ad 2 will win
        assertEquals(
                AD_URI_PREFIX + "buyer1/ad2",
                resultsCallback.mAdSelectionResponse.getRenderUri().toString());
    }

    /**
     * @param buyer The name of the buyer for this Custom Audience
     * @param biddingUri path from where the bidding logic for this CA can be fetched from
     * @param bids these bids, are added to its metadata. Our JS logic then picks this value and
     *     creates ad with the provided value as bid
     * @param activationTime is the activation time of the Custom Audience
     * @param expirationTime is the expiration time of the Custom Audience
     * @param lastUpdateTime is the last time of the Custom Audience ads and bidding data got
     *     updated
     * @return a real Custom Audience object that can be persisted and used in bidding and scoring
     */
    private DBCustomAudience createDBCustomAudience(
            final String buyer,
            final Uri biddingUri,
            List<Double> bids,
            Instant activationTime,
            Instant expirationTime,
            Instant lastUpdateTime) {

        // Generate ads for with bids provided
        List<DBAdData> ads = new ArrayList<>();

        // Create ads with the buyer name and bid number as the ad URI
        // Add the bid value to the metadata
        for (int i = 0; i < bids.size(); i++) {
            ads.add(
                    new DBAdData(
                            Uri.parse(AD_URI_PREFIX + buyer + "/ad" + (i + 1)),
                            "{\"result\":" + bids.get(i) + "}"));
        }

        return new DBCustomAudience.Builder()
                .setOwner(buyer + CustomAudienceFixture.VALID_OWNER)
                .setBuyer(buyer)
                .setName(buyer + CustomAudienceFixture.VALID_NAME)
                .setActivationTime(activationTime)
                .setExpirationTime(expirationTime)
                .setCreationTime(CommonFixture.FIXED_NOW_TRUNCATED_TO_MILLI)
                .setLastAdsAndBiddingDataUpdatedTime(lastUpdateTime)
                .setUserBiddingSignals(CustomAudienceFixture.VALID_USER_BIDDING_SIGNALS)
                .setTrustedBiddingData(
                        new DBTrustedBiddingData.Builder()
                                .setUrl(
                                        mMockWebServerRule.uriForPath(
                                                BUYER_TRUSTED_SIGNAL_URI_PATH))
                                .setKeys(TrustedBiddingDataFixture.VALID_TRUSTED_BIDDING_KEYS)
                                .build())
                .setBiddingLogicUrl(biddingUri)
                .setAds(ads)
                .build();
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
        return createDBCustomAudience(
                buyer,
                biddingUri,
                bids,
                CustomAudienceFixture.VALID_ACTIVATION_TIME,
                CustomAudienceFixture.VALID_EXPIRATION_TIME,
                CommonFixture.FIXED_NOW_TRUNCATED_TO_MILLI);
    }

    private void verifyErrorMessageIsCorrect(
            final String actualErrorMassage, final String expectedErrorReason) {
        assertTrue(
                String.format(
                        "Actual error [%s] does not begin with [%s]",
                        actualErrorMassage, ERROR_AD_SELECTION_FAILURE),
                actualErrorMassage.startsWith(ERROR_AD_SELECTION_FAILURE));
        assertTrue(
                String.format(
                        "Actual error [%s] does not contain expected message: [%s]",
                        actualErrorMassage, expectedErrorReason),
                actualErrorMassage.contains(expectedErrorReason));
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

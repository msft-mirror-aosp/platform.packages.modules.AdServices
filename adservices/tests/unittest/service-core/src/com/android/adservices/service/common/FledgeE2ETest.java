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

package com.android.adservices.service.common;

import static com.google.common.truth.Truth.assertThat;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertTrue;
import static org.mockito.Mockito.when;

import android.adservices.adselection.AdSelectionCallback;
import android.adservices.adselection.AdSelectionConfig;
import android.adservices.adselection.AdSelectionConfigFixture;
import android.adservices.adselection.AdSelectionOverrideCallback;
import android.adservices.adselection.AdSelectionResponse;
import android.adservices.adselection.ReportImpressionCallback;
import android.adservices.adselection.ReportImpressionInput;
import android.adservices.common.AdData;
import android.adservices.common.CommonFixture;
import android.adservices.common.FledgeErrorResponse;
import android.adservices.customaudience.CustomAudience;
import android.adservices.customaudience.CustomAudienceFixture;
import android.adservices.customaudience.CustomAudienceOverrideCallback;
import android.adservices.customaudience.ICustomAudienceCallback;
import android.adservices.customaudience.TrustedBiddingDataFixture;
import android.adservices.http.MockWebServerRule;
import android.content.Context;
import android.net.Uri;
import android.os.IBinder;
import android.os.RemoteException;

import androidx.room.Room;
import androidx.test.core.app.ApplicationProvider;

import com.android.adservices.MockWebServerRuleFactory;
import com.android.adservices.data.adselection.AdSelectionDatabase;
import com.android.adservices.data.adselection.AdSelectionEntryDao;
import com.android.adservices.data.customaudience.CustomAudienceDao;
import com.android.adservices.data.customaudience.CustomAudienceDatabase;
import com.android.adservices.service.adselection.AdSelectionHttpClient;
import com.android.adservices.service.adselection.AdSelectionServiceImpl;
import com.android.adservices.service.customaudience.CustomAudienceImpl;
import com.android.adservices.service.customaudience.CustomAudienceServiceImpl;
import com.android.adservices.service.devapi.DevContext;
import com.android.adservices.service.devapi.DevContextFilter;
import com.android.adservices.service.stats.AdServicesLogger;
import com.android.adservices.service.stats.AdServicesLoggerImpl;

import com.google.common.collect.ImmutableList;
import com.google.common.util.concurrent.MoreExecutors;
import com.google.mockwebserver.MockResponse;
import com.google.mockwebserver.MockWebServer;
import com.google.mockwebserver.RecordedRequest;

import org.junit.Before;
import org.junit.Rule;
import org.junit.Test;
import org.mockito.Mock;
import org.mockito.junit.MockitoJUnit;
import org.mockito.junit.MockitoRule;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;

public class FledgeE2ETest {
    private static final Context CONTEXT = ApplicationProvider.getApplicationContext();

    @Rule public MockitoRule mMockitoRule = MockitoJUnit.rule();
    @Rule public MockWebServerRule mMockWebServerRule = MockWebServerRuleFactory.createForHttps();

    // This object access some system APIs
    @Mock private DevContextFilter mDevContextFilter;
    private final AdServicesLogger mAdServicesLogger = AdServicesLoggerImpl.getInstance();

    private static final String BUYER_1 = AdSelectionConfigFixture.BUYER_1;
    private static final String BUYER_2 = AdSelectionConfigFixture.BUYER_2;
    private static final String AD_URL_PREFIX = "http://www.domain.com/adverts/123/";

    private static final String SELLER_DECISION_LOGIC_URL = "/ssp/decision/logic/";
    private static final String BUYER_BIDDING_LOGIC_URL_PREFIX = "/buyer/bidding/logic/";

    private static final String SELLER_REPORTING_PATH = "/reporting/seller";
    private static final String BUYER_REPORTING_PATH = "/reporting/buyer";

    private static final String MY_APP_PACKAGE_NAME = "com.google.ppapi.test";

    private static final List<Double> BIDS_FOR_BUYER_1 = ImmutableList.of(1.1, 2.2);
    private static final List<Double> BIDS_FOR_BUYER_2 = ImmutableList.of(4.5, 6.7, 10.0);

    private AdSelectionConfig mAdSelectionConfig;
    private AdSelectionHttpClient mAdSelectionHttpClient;
    private CustomAudienceDao mCustomAudienceDao;
    private AdSelectionEntryDao mAdSelectionEntryDao;
    private ExecutorService mExecutorService;
    private CustomAudienceServiceImpl mCustomAudienceService;
    private AdSelectionServiceImpl mAdSelectionService;

    @Before
    public void setUp() throws Exception {
        mCustomAudienceDao =
                Room.inMemoryDatabaseBuilder(CONTEXT, CustomAudienceDatabase.class)
                        .build()
                        .customAudienceDao();

        mAdSelectionEntryDao =
                Room.inMemoryDatabaseBuilder(CONTEXT, AdSelectionDatabase.class)
                        .build()
                        .adSelectionEntryDao();

        mExecutorService = Executors.newFixedThreadPool(20);

        mAdSelectionHttpClient = new AdSelectionHttpClient(mExecutorService);

        mCustomAudienceService =
                new CustomAudienceServiceImpl(
                        CONTEXT,
                        new CustomAudienceImpl(
                                mCustomAudienceDao, CommonFixture.FIXED_CLOCK_TRUNCATED_TO_MILLI),
                        mDevContextFilter,
                        MoreExecutors.newDirectExecutorService(),
                        mAdServicesLogger);

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
                        CONTEXT,
                        mAdServicesLogger);
    }

    @Test
    public void testFledgeFlowSuccessWithDevOverrides() throws Exception {
        mAdSelectionConfig =
                AdSelectionConfigFixture.anAdSelectionConfigBuilder()
                        .setCustomAudienceBuyers(Arrays.asList(BUYER_1, BUYER_2))
                        .setDecisionLogicUrl(Uri.parse(SELLER_DECISION_LOGIC_URL))
                        .build();

        String decisionLogicJs =
                "function scoreAd(ad, bid, auction_config, seller_signals,"
                        + " trusted_scoring_signals, contextual_signal, user_signal,"
                        + " custom_audience_signal) { \n"
                        + "  return {'status': 0, 'score': bid };\n"
                        + "}\n"
                        + "function reportResult(ad_selection_config, render_url, bid,"
                        + " contextual_signals) { \n"
                        + " return {'status': 0, 'results': {'signals_for_buyer':"
                        + " '{\"signals_for_buyer\":1}', 'reporting_url': '"
                        + SELLER_REPORTING_PATH
                        + "' } };\n"
                        + "}";
        String biddingLogicJs =
                "function generateBid(ad, auction_signals, per_buyer_signals,"
                        + " trusted_bidding_signals, contextual_signals, user_signals,"
                        + " custom_audience_signals) { \n"
                        + "  return {'status': 0, 'ad': ad, 'bid': ad.metadata.result };\n"
                        + "}\n"
                        + "function reportWin(ad_selection_signals, per_buyer_signals,"
                        + " signals_for_buyer, contextual_signals, custom_audience_signals) { \n"
                        + " return {'status': 0, 'results': {'reporting_url': '"
                        + BUYER_REPORTING_PATH
                        + "' } };\n"
                        + "}";

        when(mDevContextFilter.createDevContext())
                .thenReturn(
                        DevContext.builder()
                                .setDevOptionsEnabled(true)
                                .setCallingAppPackageName(MY_APP_PACKAGE_NAME)
                                .build());

        CustomAudience customAudience1 =
                createCustomAudience(
                        BUYER_1,
                        Uri.parse(BUYER_BIDDING_LOGIC_URL_PREFIX + BUYER_1),
                        BIDS_FOR_BUYER_1);

        CustomAudience customAudience2 =
                createCustomAudience(
                        BUYER_2,
                        Uri.parse(BUYER_BIDDING_LOGIC_URL_PREFIX + BUYER_2),
                        BIDS_FOR_BUYER_2);

        // Join first custom audience
        ResultCapturingCallback joinCallback1 = new ResultCapturingCallback();
        mCustomAudienceService.joinCustomAudience(customAudience1, joinCallback1);
        assertTrue(joinCallback1.isSuccess());

        // Join second custom audience
        ResultCapturingCallback joinCallback2 = new ResultCapturingCallback();
        mCustomAudienceService.joinCustomAudience(customAudience2, joinCallback2);
        assertTrue(joinCallback2.isSuccess());

        // Add AdSelection Override
        AdSelectionOverrideTestCallback adSelectionOverrideTestCallback =
                callAddAdSelectionOverride(
                        mAdSelectionService, mAdSelectionConfig, decisionLogicJs);

        assertTrue(adSelectionOverrideTestCallback.mIsSuccess);

        // Add Custom Audience Override
        CustomAudienceOverrideTestCallback customAudienceOverrideTestCallback =
                callAddCustomAudienceOverride(
                        customAudience2.getOwner(),
                        customAudience2.getBuyer(),
                        customAudience2.getName(),
                        biddingLogicJs,
                        "",
                        mCustomAudienceService);

        assertTrue(customAudienceOverrideTestCallback.mIsSuccess);

        // Run Ad Selection
        AdSelectionTestCallback resultsCallback =
                invokeRunAdSelection(mAdSelectionService, mAdSelectionConfig);

        assertTrue(resultsCallback.mIsSuccess);
        long resultSelectionId = resultsCallback.mAdSelectionResponse.getAdSelectionId();
        assertTrue(mAdSelectionEntryDao.doesAdSelectionIdExist(resultSelectionId));
        assertEquals(
                AD_URL_PREFIX + "buyer2/ad3",
                resultsCallback.mAdSelectionResponse.getRenderUrl().toString());

        // Run Report Impression
        ReportImpressionInput input =
                new ReportImpressionInput.Builder()
                        .setAdSelectionConfig(mAdSelectionConfig)
                        .setAdSelectionId(resultsCallback.mAdSelectionResponse.getAdSelectionId())
                        .build();

        ReportImpressionTestCallback reportImpressionTestCallback =
                callReportImpression(mAdSelectionService, input);

        assertTrue(reportImpressionTestCallback.mIsSuccess);
    }

    @Test
    public void testFledgeFlowSuccessWithMockServer() throws Exception {
        Uri sellerReportingUrl = mMockWebServerRule.uriForPath(SELLER_REPORTING_PATH);
        Uri buyerReportingUrl = mMockWebServerRule.uriForPath(BUYER_REPORTING_PATH);

        mAdSelectionConfig =
                AdSelectionConfigFixture.anAdSelectionConfigBuilder()
                        .setCustomAudienceBuyers(Arrays.asList(BUYER_1, BUYER_2))
                        .setDecisionLogicUrl(
                                mMockWebServerRule.uriForPath(SELLER_DECISION_LOGIC_URL))
                        .build();

        String decisionLogicJs =
                "function scoreAd(ad, bid, auction_config, seller_signals,"
                        + " trusted_scoring_signals, contextual_signal, user_signal,"
                        + " custom_audience_signal) { \n"
                        + "  return {'status': 0, 'score': bid };\n"
                        + "}\n"
                        + "function reportResult(ad_selection_config, render_url, bid,"
                        + " contextual_signals) { \n"
                        + " return {'status': 0, 'results': {'signals_for_buyer':"
                        + " '{\"signals_for_buyer\":1}', 'reporting_url': '"
                        + sellerReportingUrl
                        + "' } };\n"
                        + "}";
        String biddingLogicJs =
                "function generateBid(ad, auction_signals, per_buyer_signals,"
                        + " trusted_bidding_signals, contextual_signals, user_signals,"
                        + " custom_audience_signals) { \n"
                        + "  return {'status': 0, 'ad': ad, 'bid': ad.metadata.result };\n"
                        + "}\n"
                        + "function reportWin(ad_selection_signals, per_buyer_signals,"
                        + " signals_for_buyer, contextual_signals, custom_audience_signals) { \n"
                        + " return {'status': 0, 'results': {'reporting_url': '"
                        + buyerReportingUrl
                        + "' } };\n"
                        + "}";

        CustomAudience customAudience1 =
                createCustomAudience(
                        BUYER_1,
                        mMockWebServerRule.uriForPath(BUYER_BIDDING_LOGIC_URL_PREFIX + BUYER_1),
                        BIDS_FOR_BUYER_1);

        CustomAudience customAudience2 =
                createCustomAudience(
                        BUYER_2,
                        mMockWebServerRule.uriForPath(BUYER_BIDDING_LOGIC_URL_PREFIX + BUYER_2),
                        BIDS_FOR_BUYER_2);

        MockWebServer server =
                mMockWebServerRule.startMockWebServer(
                        request -> {
                            switch (request.getPath()) {
                                case SELLER_DECISION_LOGIC_URL:
                                    return new MockResponse().setBody(decisionLogicJs);
                                case BUYER_BIDDING_LOGIC_URL_PREFIX + BUYER_1:
                                case BUYER_BIDDING_LOGIC_URL_PREFIX + BUYER_2:
                                    return new MockResponse().setBody(biddingLogicJs);
                            }
                            return new MockResponse().setResponseCode(404);
                        });

        // Join first custom audience
        ResultCapturingCallback joinCallback1 = new ResultCapturingCallback();
        mCustomAudienceService.joinCustomAudience(customAudience1, joinCallback1);
        assertTrue(joinCallback1.isSuccess());

        // Join second custom audience
        ResultCapturingCallback joinCallback2 = new ResultCapturingCallback();
        mCustomAudienceService.joinCustomAudience(customAudience2, joinCallback2);
        assertTrue(joinCallback2.isSuccess());

        // Run Ad Selection
        AdSelectionTestCallback resultsCallback =
                invokeRunAdSelection(mAdSelectionService, mAdSelectionConfig);

        assertTrue(resultsCallback.mIsSuccess);
        long resultSelectionId = resultsCallback.mAdSelectionResponse.getAdSelectionId();
        assertTrue(mAdSelectionEntryDao.doesAdSelectionIdExist(resultSelectionId));
        assertEquals(
                AD_URL_PREFIX + "buyer2/ad3",
                resultsCallback.mAdSelectionResponse.getRenderUrl().toString());

        // Run Report Impression
        ReportImpressionInput input =
                new ReportImpressionInput.Builder()
                        .setAdSelectionConfig(mAdSelectionConfig)
                        .setAdSelectionId(resultsCallback.mAdSelectionResponse.getAdSelectionId())
                        .build();

        ReportImpressionTestCallback reportImpressionTestCallback =
                callReportImpression(mAdSelectionService, input);

        assertTrue(reportImpressionTestCallback.mIsSuccess);

        List<String> fetchBuyerRequests =
                ImmutableList.of(server.takeRequest().getPath(), server.takeRequest().getPath());

        assertThat(fetchBuyerRequests)
                .containsExactly(
                        BUYER_BIDDING_LOGIC_URL_PREFIX + BUYER_1,
                        BUYER_BIDDING_LOGIC_URL_PREFIX + BUYER_2);

        RecordedRequest fetchSellerRequest1 = server.takeRequest();
        assertEquals(SELLER_DECISION_LOGIC_URL, fetchSellerRequest1.getPath());

        RecordedRequest fetchSellerRequest2 = server.takeRequest();
        assertEquals(SELLER_DECISION_LOGIC_URL, fetchSellerRequest2.getPath());

        List<String> notifications =
                ImmutableList.of(server.takeRequest().getPath(), server.takeRequest().getPath());

        assertThat(notifications).containsExactly(SELLER_REPORTING_PATH, BUYER_REPORTING_PATH);
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

    private AdSelectionOverrideTestCallback callAddAdSelectionOverride(
            AdSelectionServiceImpl adSelectionService,
            AdSelectionConfig adSelectionConfig,
            String decisionLogicJS)
            throws Exception {
        // Counted down in 1) callback
        CountDownLatch resultLatch = new CountDownLatch(1);
        AdSelectionOverrideTestCallback callback = new AdSelectionOverrideTestCallback(resultLatch);

        adSelectionService.overrideAdSelectionConfigRemoteInfo(
                adSelectionConfig, decisionLogicJS, callback);
        resultLatch.await();
        return callback;
    }

    private CustomAudienceOverrideTestCallback callAddCustomAudienceOverride(
            String owner,
            String buyer,
            String name,
            String biddingLogicJs,
            String trustedBiddingData,
            CustomAudienceServiceImpl customAudienceService)
            throws Exception {
        CountDownLatch resultLatch = new CountDownLatch(1);
        CustomAudienceOverrideTestCallback callback =
                new CustomAudienceOverrideTestCallback(resultLatch);

        customAudienceService.overrideCustomAudienceRemoteInfo(
                owner, buyer, name, biddingLogicJs, trustedBiddingData, callback);
        resultLatch.await();
        return callback;
    }

    private ReportImpressionTestCallback callReportImpression(
            AdSelectionServiceImpl adSelectionService, ReportImpressionInput requestParams)
            throws Exception {
        // Counted down in 1) callback
        CountDownLatch resultLatch = new CountDownLatch(1);
        ReportImpressionTestCallback callback = new ReportImpressionTestCallback(resultLatch);

        adSelectionService.reportImpression(requestParams, callback);
        resultLatch.await();
        return callback;
    }

    /**
     * @param buyer The name of the buyer for this Custom Audience
     * @param biddingUri path from where the bidding logic for this CA can be fetched from
     * @param bids these bids, are added to its metadata. Our JS logic then picks this value and
     *     creates ad with the provided value as bid
     * @return a real Custom Audience object that can be persisted and used in bidding and scoring
     */
    private CustomAudience createCustomAudience(
            final String buyer, final Uri biddingUri, List<Double> bids) {

        // Generate ads for with bids provided
        List<AdData> ads = new ArrayList<>();

        // Create ads with the buyer name and bid number as the ad URL
        // Add the bid value to the metadata
        for (int i = 0; i < bids.size(); i++) {
            ads.add(
                    new AdData.Builder()
                            .setRenderUrl(Uri.parse(AD_URL_PREFIX + buyer + "/ad" + (i + 1)))
                            .setMetadata("{\"result\":" + bids.get(i) + "}")
                            .build());
        }

        return new CustomAudience.Builder()
                .setOwner(MY_APP_PACKAGE_NAME)
                .setBuyer(buyer)
                .setName(buyer + CustomAudienceFixture.VALID_NAME)
                .setActivationTime(CustomAudienceFixture.VALID_ACTIVATION_TIME)
                .setExpirationTime(CustomAudienceFixture.VALID_EXPIRATION_TIME)
                .setDailyUpdateUrl(CustomAudienceFixture.VALID_DAILY_UPDATE_URL)
                .setUserBiddingSignals(CustomAudienceFixture.VALID_USER_BIDDING_SIGNALS)
                .setTrustedBiddingData(TrustedBiddingDataFixture.VALID_TRUSTED_BIDDING_DATA)
                .setBiddingLogicUrl(biddingUri)
                .setAds(ads)
                .build();
    }

    private static class ResultCapturingCallback implements ICustomAudienceCallback {
        private boolean mIsSuccess;
        private Exception mException;

        public boolean isSuccess() {
            return mIsSuccess;
        }

        public Exception getException() {
            return mException;
        }

        @Override
        public void onSuccess() throws RemoteException {
            mIsSuccess = true;
        }

        @Override
        public void onFailure(FledgeErrorResponse responseParcel) throws RemoteException {
            mIsSuccess = false;
            mException = responseParcel.asException();
        }

        @Override
        public IBinder asBinder() {
            throw new RuntimeException("Should not be called.");
        }
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

    public static class AdSelectionOverrideTestCallback extends AdSelectionOverrideCallback.Stub {
        boolean mIsSuccess = false;
        FledgeErrorResponse mFledgeErrorResponse;
        private final CountDownLatch mCountDownLatch;

        public AdSelectionOverrideTestCallback(CountDownLatch countDownLatch) {
            mCountDownLatch = countDownLatch;
        }

        @Override
        public void onSuccess() throws RemoteException {
            mIsSuccess = true;
            mCountDownLatch.countDown();
        }

        @Override
        public void onFailure(FledgeErrorResponse fledgeErrorResponse) throws RemoteException {
            mFledgeErrorResponse = fledgeErrorResponse;
            mCountDownLatch.countDown();
        }
    }

    public static class CustomAudienceOverrideTestCallback
            extends CustomAudienceOverrideCallback.Stub {
        boolean mIsSuccess = false;
        FledgeErrorResponse mFledgeErrorResponse;
        private final CountDownLatch mCountDownLatch;

        public CustomAudienceOverrideTestCallback(CountDownLatch countDownLatch) {
            mCountDownLatch = countDownLatch;
        }

        @Override
        public void onSuccess() throws RemoteException {
            mIsSuccess = true;
            mCountDownLatch.countDown();
        }

        @Override
        public void onFailure(FledgeErrorResponse fledgeErrorResponse) throws RemoteException {
            mFledgeErrorResponse = fledgeErrorResponse;
            mCountDownLatch.countDown();
        }
    }

    public static class ReportImpressionTestCallback extends ReportImpressionCallback.Stub {
        boolean mIsSuccess = false;
        FledgeErrorResponse mFledgeErrorResponse;
        private final CountDownLatch mCountDownLatch;

        public ReportImpressionTestCallback(CountDownLatch countDownLatch) {
            mCountDownLatch = countDownLatch;
        }

        @Override
        public void onSuccess() throws RemoteException {
            mIsSuccess = true;
            mCountDownLatch.countDown();
        }

        @Override
        public void onFailure(FledgeErrorResponse fledgeErrorResponse) throws RemoteException {
            mFledgeErrorResponse = fledgeErrorResponse;
            mCountDownLatch.countDown();
        }
    }
}

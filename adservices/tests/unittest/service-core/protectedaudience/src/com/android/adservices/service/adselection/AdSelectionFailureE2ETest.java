/*
 * Copyright (C) 2024 The Android Open Source Project
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

import static android.adservices.common.AdServicesStatusUtils.STATUS_INTERNAL_ERROR;
import static android.adservices.common.CommonFixture.TEST_PACKAGE_NAME;

import static com.android.adservices.common.CommonFlagsValues.EXTENDED_FLEDGE_AD_SELECTION_BIDDING_TIMEOUT_PER_CA_MS;
import static com.android.adservices.common.CommonFlagsValues.EXTENDED_FLEDGE_AD_SELECTION_FROM_OUTCOMES_OVERALL_TIMEOUT_MS;
import static com.android.adservices.common.CommonFlagsValues.EXTENDED_FLEDGE_AD_SELECTION_OVERALL_TIMEOUT_MS;
import static com.android.adservices.common.CommonFlagsValues.EXTENDED_FLEDGE_AD_SELECTION_SCORING_TIMEOUT_MS;
import static com.android.adservices.common.CommonFlagsValues.EXTENDED_FLEDGE_AD_SELECTION_SELECTING_OUTCOME_TIMEOUT_MS;
import static com.android.adservices.common.CommonFlagsValues.EXTENDED_FLEDGE_BACKGROUND_FETCH_NETWORK_CONNECT_TIMEOUT_MS;
import static com.android.adservices.common.CommonFlagsValues.EXTENDED_FLEDGE_BACKGROUND_FETCH_NETWORK_READ_TIMEOUT_MS;
import static com.android.adservices.common.CommonFlagsValues.EXTENDED_FLEDGE_REPORT_IMPRESSION_OVERALL_TIMEOUT_MS;
import static com.android.adservices.data.adselection.AdSelectionDatabase.DATABASE_NAME;
import static com.android.adservices.service.stats.AdSelectionExecutionLoggerTestFixture.DB_AD_SELECTION_FILE_SIZE;

import static com.google.common.truth.Truth.assertWithMessage;

import static org.junit.Assume.assumeFalse;
import static org.mockito.Mockito.doNothing;
import static org.mockito.Mockito.when;

import android.adservices.adid.AdId;
import android.adservices.adselection.AdSelectionCallback;
import android.adservices.adselection.AdSelectionConfig;
import android.adservices.adselection.AdSelectionConfigFixture;
import android.adservices.adselection.AdSelectionInput;
import android.adservices.adselection.AdSelectionResponse;
import android.adservices.adselection.CustomAudienceSignalsFixture;
import android.adservices.adselection.ReportImpressionCallback;
import android.adservices.adselection.ReportImpressionInput;
import android.adservices.common.AdSelectionSignals;
import android.adservices.common.AdTechIdentifier;
import android.adservices.common.CallerMetadata;
import android.adservices.common.CallingAppUidSupplierProcessImpl;
import android.adservices.common.CommonFixture;
import android.adservices.common.FledgeErrorResponse;
import android.adservices.customaudience.CustomAudienceFixture;
import android.adservices.customaudience.TrustedBiddingDataFixture;
import android.adservices.http.MockWebServerRule;
import android.net.Uri;
import android.os.Process;
import android.os.SystemClock;

import androidx.room.Room;

import com.android.adservices.LoggerFactory;
import com.android.adservices.MockWebServerRuleFactory;
import com.android.adservices.common.AdServicesExtendedMockitoTestCase;
import com.android.adservices.common.DbTestUtil;
import com.android.adservices.common.WebViewSupportUtil;
import com.android.adservices.concurrency.AdServicesExecutors;
import com.android.adservices.data.adselection.AdSelectionDatabase;
import com.android.adservices.data.adselection.AdSelectionDebugReportDao;
import com.android.adservices.data.adselection.AdSelectionDebugReportingDatabase;
import com.android.adservices.data.adselection.AdSelectionEntryDao;
import com.android.adservices.data.adselection.AppInstallDao;
import com.android.adservices.data.adselection.ConsentedDebugConfigurationDao;
import com.android.adservices.data.adselection.CustomAudienceSignals;
import com.android.adservices.data.adselection.DBAdSelection;
import com.android.adservices.data.adselection.DBBuyerDecisionLogic;
import com.android.adservices.data.adselection.FrequencyCapDao;
import com.android.adservices.data.adselection.SharedStorageDatabase;
import com.android.adservices.data.common.DBAdData;
import com.android.adservices.data.customaudience.CustomAudienceDao;
import com.android.adservices.data.customaudience.CustomAudienceDatabase;
import com.android.adservices.data.customaudience.DBCustomAudience;
import com.android.adservices.data.customaudience.DBTrustedBiddingData;
import com.android.adservices.data.encryptionkey.EncryptionKeyDao;
import com.android.adservices.data.enrollment.EnrollmentDao;
import com.android.adservices.data.shared.SharedDbHelper;
import com.android.adservices.data.signals.EncodedPayloadDao;
import com.android.adservices.data.signals.ProtectedSignalsDatabase;
import com.android.adservices.service.DebugFlags;
import com.android.adservices.service.Flags;
import com.android.adservices.service.FlagsFactory;
import com.android.adservices.service.adid.AdIdCacheManager;
import com.android.adservices.service.adselection.debug.AuctionServerDebugConfigurationGenerator;
import com.android.adservices.service.adselection.debug.ConsentedDebugConfigurationGeneratorFactory;
import com.android.adservices.service.adselection.encryption.ObliviousHttpEncryptor;
import com.android.adservices.service.common.AdSelectionServiceFilter;
import com.android.adservices.service.common.FledgeAuthorizationFilter;
import com.android.adservices.service.common.RetryStrategyFactory;
import com.android.adservices.service.common.Throttler;
import com.android.adservices.service.common.cache.CacheProviderFactory;
import com.android.adservices.service.common.httpclient.AdServicesHttpsClient;
import com.android.adservices.service.consent.ConsentManager;
import com.android.adservices.service.devapi.DevContext;
import com.android.adservices.service.devapi.DevContextFilter;
import com.android.adservices.service.kanon.KAnonSignJoinFactory;
import com.android.adservices.service.stats.AdServicesLogger;
import com.android.adservices.service.stats.AdServicesStatsLog;
import com.android.adservices.service.stats.NoOpLoggerImpl;
import com.android.adservices.shared.testing.concurrency.FailableOnResultSyncCallback;
import com.android.modules.utils.testing.ExtendedMockitoRule.SpyStatic;

import com.google.common.collect.ImmutableList;
import com.google.common.collect.ImmutableMap;
import com.google.mockwebserver.Dispatcher;
import com.google.mockwebserver.MockResponse;
import com.google.mockwebserver.RecordedRequest;

import org.json.JSONObject;
import org.junit.Before;
import org.junit.Rule;
import org.junit.Test;
import org.mockito.Mock;

import java.io.File;
import java.time.Clock;
import java.time.Instant;
import java.time.ZoneOffset;
import java.time.temporal.ChronoUnit;
import java.util.ArrayList;
import java.util.Collections;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.ScheduledThreadPoolExecutor;

@SpyStatic(FlagsFactory.class)
@SpyStatic(DebugFlags.class)
public final class AdSelectionFailureE2ETest extends AdServicesExtendedMockitoTestCase {

    private static final LoggerFactory.Logger sLogger = LoggerFactory.getFledgeLogger();
    private static final int CALLER_UID = Process.myUid();

    private static final Uri AD_URI_PREFIX = Uri.parse("http://www.domain.com/adverts/123/");

    private static final String BUYER_BIDDING_LOGIC_URI_PATH = "/buyer/bidding/logic/";
    private static final String BUYER_TRUSTED_SIGNAL_URI_PATH = "/kv/buyer/signals/";
    private static final String BUYER_REPORTING_URI_PATH = "/dsp/reporting/";

    private static final String SELLER_DECISION_LOGIC_URI_PATH = "/ssp/decision/logic/";
    private static final String SELLER_TRUSTED_SIGNAL_URI_PATH = "/kv/seller/signals/";
    private static final String SELLER_TRUSTED_SIGNAL_PARAMS = "?renderuris=";
    private static final String SELLER_REPORTING_URI_PATH = "/ssp/reporting/";

    public static final String READ_BID_FROM_AD_METADATA_JS =
            "function generateBid(ad, auction_signals, per_buyer_signals,"
                    + " trusted_bidding_signals, contextual_signals,"
                    + " custom_audience_signals) { \n"
                    + "  return {'status': 0, 'ad': ad, 'bid': ad.metadata.result };\n"
                    + "}\n"
                    + "\n"
                    + "function reportWin(ad_selection_signals, per_buyer_signals,"
                    + " signals_for_buyer, contextual_signals, custom_audience_signals) { \n"
                    + " return {'status': 0, 'results': {'reporting_uri': '%s' } };\n"
                    + "}";

    public static final String USE_BID_AS_SCORE_JS =
            "function scoreAd(ad, bid, auction_config, seller_signals,"
                    + " trusted_scoring_signals, contextual_signal,"
                    + " custom_audience_signal) { \n"
                    + "  return {'status': 0, 'score': bid };\n"
                    + "}\n"
                    + "\n"
                    + "function reportResult(ad_selection_config, render_uri, bid, "
                    + "contextual_signals)"
                    + " { \n"
                    + " return {'status': 0, 'results': {'signals_for_buyer':"
                    + " '{\"signals_for_buyer\":1}', 'reporting_uri': '%s' } };\n"
                    + "}";

    private static final Map<String, String> TRUSTED_BIDDING_SIGNALS_SERVER_DATA =
            new ImmutableMap.Builder<String, String>()
                    .put("example", "example")
                    .put("valid", "Also valid")
                    .put("list", "list")
                    .put("of", "of")
                    .put("keys", "trusted bidding signal Values")
                    .build();

    private static final AdSelectionSignals TRUSTED_SCORING_SIGNALS =
            AdSelectionSignals.fromString(
                    "{\n"
                            + "\t\"render_uri_1\": \"signals_for_1\",\n"
                            + "\t\"render_uri_2\": \"signals_for_2\"\n"
                            + "}");
    private static final AdSelectionSignals BUYER_SIGNALS =
            AdSelectionSignals.fromString(
                    "{\n"
                            + "\t\"signals_for_buyer_1\": \"signal_1\",\n"
                            + "\t\"signals_for_buyer_2\": \"signal_2\"\n"
                            + "}");

    private static final long BINDER_ELAPSED_TIME_MS = 100L;
    private static final String CALLER_PACKAGE_NAME = TEST_PACKAGE_NAME;
    private static final boolean CONSOLE_MESSAGE_IN_LOGS_ENABLED = true;
    private static final long AD_SELECTION_ID = 1;
    private static final double BID = 5.0;
    private static final Clock CLOCK = Clock.fixed(Instant.now(), ZoneOffset.UTC);
    private static final Instant ACTIVATION_TIME = CLOCK.instant().truncatedTo(ChronoUnit.MILLIS);

    @Rule(order = 12)
    public final MockWebServerRule mMockWebServerRule = MockWebServerRuleFactory.createForHttps();

    // Mocking DevContextFilter to test behavior with and without override api authorization
    @Mock private DevContextFilter mMockDevContextFilter;
    @Mock private CallerMetadata mMockCallerMetadata;
    @Mock private File mMockDBAdSelectionFile;
    @Mock private ConsentManager mMockConsentManager;
    @Mock private KAnonSignJoinFactory mMockUnusedKAnonSignJoinFactory;
    @Mock private AdSelectionServiceFilter mMockAdSelectionServiceFilter;
    @Mock private ObliviousHttpEncryptor mMockObliviousHttpEncryptor;

    private Flags mFakeFlags;
    private FledgeAuthorizationFilter mFledgeAuthorizationFilter;
    private AdServicesLogger mAdServicesLogger;
    private ExecutorService mLightweightExecutorService;
    private ExecutorService mBackgroundExecutorService;
    private ScheduledThreadPoolExecutor mScheduledExecutor;
    private CustomAudienceDao mCustomAudienceDao;
    private EncodedPayloadDao mEncodedPayloadDao;
    private AppInstallDao mAppInstallDao;
    private FrequencyCapDao mFrequencyCapDao;
    private EnrollmentDao mEnrollmentDao;
    private EncryptionKeyDao mEncryptionKeyDao;
    private AdSelectionEntryDao mAdSelectionEntryDao;
    private AdServicesHttpsClient mAdServicesHttpsClient;
    private AdSelectionConfig mAdSelectionConfig;
    private Dispatcher mDispatcher;
    private AdFilteringFeatureFactory mAdFilteringFeatureFactory;
    private MultiCloudSupportStrategy mMultiCloudSupportStrategy;
    private AdSelectionDebugReportDao mAdSelectionDebugReportDao;
    private AdIdFetcher mAdIdFetcher;
    private RetryStrategyFactory mRetryStrategyFactory;
    private AdTechIdentifier mBuyer;
    private AuctionServerDebugConfigurationGenerator mAuctionServerDebugConfigurationGenerator;

    @Before
    public void setUp() throws Exception {
        assumeFalse(
                "JavaScriptSandbox is available on the device, skipping test",
                WebViewSupportUtil.isJSSandboxAvailable(mContext));
        mFakeFlags = new AdSelectionFailureE2ETestFlags();
        mocker.mockGetFlags(mFakeFlags);
        mocker.mockGetDebugFlags(mMockDebugFlags);
        mocker.mockGetConsentNotificationDebugMode(false);
        mAdSelectionEntryDao =
                Room.inMemoryDatabaseBuilder(mSpyContext, AdSelectionDatabase.class)
                        .build()
                        .adSelectionEntryDao();
        mAppInstallDao =
                Room.inMemoryDatabaseBuilder(mSpyContext, SharedStorageDatabase.class)
                        .build()
                        .appInstallDao();
        mFrequencyCapDao =
                Room.inMemoryDatabaseBuilder(mSpyContext, SharedStorageDatabase.class)
                        .build()
                        .frequencyCapDao();
        mCustomAudienceDao =
                Room.inMemoryDatabaseBuilder(mSpyContext, CustomAudienceDatabase.class)
                        .addTypeConverter(new DBCustomAudience.Converters(true, true, true))
                        .build()
                        .customAudienceDao();
        mEncodedPayloadDao =
                Room.inMemoryDatabaseBuilder(mSpyContext, ProtectedSignalsDatabase.class)
                        .build()
                        .getEncodedPayloadDao();
        mAdSelectionDebugReportDao =
                Room.inMemoryDatabaseBuilder(mSpyContext, AdSelectionDebugReportingDatabase.class)
                        .build()
                        .getAdSelectionDebugReportDao();

        mAdServicesLogger = new NoOpLoggerImpl();

        SharedDbHelper dbHelper = DbTestUtil.getSharedDbHelperForTest();
        mEncryptionKeyDao = new EncryptionKeyDao(dbHelper, mAdServicesLogger);
        mEnrollmentDao = new EnrollmentDao(mSpyContext, dbHelper, mFakeFlags);
        mFledgeAuthorizationFilter =
                new FledgeAuthorizationFilter(
                        mSpyContext.getPackageManager(), mEnrollmentDao, mAdServicesLogger);
        mAdFilteringFeatureFactory =
                new AdFilteringFeatureFactory(mAppInstallDao, mFrequencyCapDao, mFakeFlags);
        mMultiCloudSupportStrategy =
                MultiCloudTestStrategyFactory.getDisabledTestStrategy(mMockObliviousHttpEncryptor);

        // Initialize dependencies for the AdSelectionService
        mLightweightExecutorService = AdServicesExecutors.getLightWeightExecutor();
        mBackgroundExecutorService = AdServicesExecutors.getBackgroundExecutor();
        mScheduledExecutor = AdServicesExecutors.getScheduler();

        mAdServicesHttpsClient =
                new AdServicesHttpsClient(
                        AdServicesExecutors.getBlockingExecutor(),
                        CacheProviderFactory.createNoOpCache());

        MockAdIdWorker mockAdIdWorker = new MockAdIdWorker(new AdIdCacheManager(mSpyContext));
        mAdIdFetcher =
                new AdIdFetcher(
                        mContext, mockAdIdWorker, mLightweightExecutorService, mScheduledExecutor);
        mRetryStrategyFactory = RetryStrategyFactory.createInstanceForTesting();

        ConsentedDebugConfigurationDao consentedDebugConfigurationDao =
                Room.inMemoryDatabaseBuilder(mSpyContext, AdSelectionDatabase.class)
                        .build()
                        .consentedDebugConfigurationDao();
        ConsentedDebugConfigurationGeneratorFactory consentedDebugConfigurationGeneratorFactory =
                new ConsentedDebugConfigurationGeneratorFactory(
                        false, consentedDebugConfigurationDao);
        mAuctionServerDebugConfigurationGenerator =
                new AuctionServerDebugConfigurationGenerator(
                        Flags.ADID_KILL_SWITCH,
                        Flags.DEFAULT_AUCTION_SERVER_AD_ID_FETCHER_TIMEOUT_MS,
                        Flags.FLEDGE_AUCTION_SERVER_ENABLE_DEBUG_REPORTING,
                        Flags.DEFAULT_FLEDGE_AUCTION_SERVER_ENABLE_PAS_UNLIMITED_EGRESS,
                        Flags.DEFAULT_PROD_DEBUG_IN_AUCTION_SERVER,
                        mAdIdFetcher,
                        consentedDebugConfigurationGeneratorFactory.create(),
                        mLightweightExecutorService);

        when(mMockDevContextFilter.createDevContext())
                .thenReturn(DevContext.createForDevOptionsDisabled());
        when(mMockCallerMetadata.getBinderElapsedTimestamp())
                .thenReturn(SystemClock.elapsedRealtime() - BINDER_ELAPSED_TIME_MS);

        // Create a dispatcher that helps map a request -> response in mockWebServer
        Uri uriPathForScoringWithReportResults =
                mMockWebServerRule.uriForPath(SELLER_REPORTING_URI_PATH);
        Uri uriPathForBiddingWithReportResults =
                mMockWebServerRule.uriForPath(BUYER_REPORTING_URI_PATH);
        mDispatcher =
                new Dispatcher() {
                    @Override
                    public MockResponse dispatch(RecordedRequest request) {
                        if (SELLER_DECISION_LOGIC_URI_PATH.equals(request.getPath())) {
                            return new MockResponse()
                                    .setBody(
                                            String.format(
                                                    USE_BID_AS_SCORE_JS,
                                                    uriPathForScoringWithReportResults));
                        } else if ((BUYER_BIDDING_LOGIC_URI_PATH).equals(request.getPath())) {
                            return new MockResponse()
                                    .setBody(
                                            String.format(
                                                    READ_BID_FROM_AD_METADATA_JS,
                                                    uriPathForBiddingWithReportResults));
                        } else if (SELLER_REPORTING_URI_PATH.equals(request.getPath())) {
                            return new MockResponse().setBody("");
                        } else if (BUYER_REPORTING_URI_PATH.equals(request.getPath())) {
                            return new MockResponse().setBody("");
                        } else if (request.getPath().startsWith(BUYER_TRUSTED_SIGNAL_URI_PATH)) {
                            String[] keys =
                                    Uri.parse(request.getPath())
                                            .getQueryParameter(
                                                    DBTrustedBiddingData.QUERY_PARAM_KEYS)
                                            .split(",");
                            Map<String, String> jsonMap = new HashMap<>();
                            for (String key : keys) {
                                jsonMap.put(key, TRUSTED_BIDDING_SIGNALS_SERVER_DATA.get(key));
                            }
                            return new MockResponse().setBody(new JSONObject(jsonMap).toString());
                        }

                        // The seller params vary based on runtime, so we are returning trusted
                        // signals based on correct path prefix
                        if (request.getPath()
                                .startsWith(
                                        SELLER_TRUSTED_SIGNAL_URI_PATH
                                                + SELLER_TRUSTED_SIGNAL_PARAMS)) {
                            return new MockResponse().setBody(TRUSTED_SCORING_SIGNALS.toString());
                        }
                        sLogger.w("Unexpected call to MockWebServer " + request.getPath());
                        return new MockResponse().setResponseCode(404);
                    }
                };

        AdTechIdentifier seller =
                AdTechIdentifier.fromString(
                        mMockWebServerRule.uriForPath(SELLER_DECISION_LOGIC_URI_PATH).getHost());
        mBuyer =
                AdTechIdentifier.fromString(
                        mMockWebServerRule.uriForPath(BUYER_BIDDING_LOGIC_URI_PATH).getHost());

        // Create an Ad Selection Config with the buyers and decision logic URI
        // the URI points to a JS with score generation logic
        mAdSelectionConfig =
                AdSelectionConfigFixture.anAdSelectionConfigBuilder()
                        .setCustomAudienceBuyers(ImmutableList.of(mBuyer))
                        .setSeller(seller)
                        .setDecisionLogicUri(
                                mMockWebServerRule.uriForPath(SELLER_DECISION_LOGIC_URI_PATH))
                        .setTrustedScoringSignalsUri(
                                mMockWebServerRule.uriForPath(SELLER_TRUSTED_SIGNAL_URI_PATH))
                        .setPerBuyerSignals(ImmutableMap.of(mBuyer, BUYER_SIGNALS))
                        .build();
        when(mSpyContext.getDatabasePath(DATABASE_NAME)).thenReturn(mMockDBAdSelectionFile);
        when(mMockDBAdSelectionFile.length()).thenReturn(DB_AD_SELECTION_FILE_SIZE);
        doNothing()
                .when(mMockAdSelectionServiceFilter)
                .filterRequest(
                        seller,
                        CALLER_PACKAGE_NAME,
                        true,
                        true,
                        true,
                        CALLER_UID,
                        AdServicesStatsLog.AD_SERVICES_API_CALLED__API_NAME__SELECT_ADS,
                        Throttler.ApiKey.FLEDGE_API_SELECT_ADS,
                        DevContext.createForDevOptionsDisabled());
        mockAdIdWorker.setResult(AdId.ZERO_OUT, true);
    }

    @Test
    public void testRunAdSelection_webViewNotInstalled_failsGracefully() throws Exception {

        // Create a new local service impl so that the WebView stub takes effect
        AdSelectionServiceImpl adSelectionServiceImpl =
                new AdSelectionServiceImpl(
                        mAdSelectionEntryDao,
                        mAppInstallDao,
                        mCustomAudienceDao,
                        mEncodedPayloadDao,
                        mFrequencyCapDao,
                        mEncryptionKeyDao,
                        mEnrollmentDao,
                        mAdServicesHttpsClient,
                        mMockDevContextFilter,
                        mLightweightExecutorService,
                        mBackgroundExecutorService,
                        mScheduledExecutor,
                        mSpyContext,
                        mAdServicesLogger,
                        mFakeFlags,
                        mMockDebugFlags,
                        CallingAppUidSupplierProcessImpl.create(),
                        mFledgeAuthorizationFilter,
                        mMockAdSelectionServiceFilter,
                        mAdFilteringFeatureFactory,
                        mMockConsentManager,
                        mMultiCloudSupportStrategy,
                        mAdSelectionDebugReportDao,
                        mAdIdFetcher,
                        mMockUnusedKAnonSignJoinFactory,
                        false,
                        mRetryStrategyFactory,
                        CONSOLE_MESSAGE_IN_LOGS_ENABLED,
                        mAuctionServerDebugConfigurationGenerator);

        mMockWebServerRule.startMockWebServer(mDispatcher);
        List<Double> bidsForBuyer1 = ImmutableList.of(1.1, 2.2);
        DBCustomAudience dBCustomAudienceForBuyer1 =
                createDBCustomAudience(
                        mBuyer,
                        mMockWebServerRule.uriForPath(BUYER_BIDDING_LOGIC_URI_PATH),
                        bidsForBuyer1);
        // Populating the Custom Audience DB
        mCustomAudienceDao.insertOrOverwriteCustomAudience(
                dBCustomAudienceForBuyer1,
                CustomAudienceFixture.getValidDailyUpdateUriByBuyer(mBuyer),
                false);
        sLogger.d("calling ad selection");
        // Ad selection should fail gracefully and not crash
        SyncAdSelectionCallback resultsCallback =
                invokeSelectAds(adSelectionServiceImpl, mAdSelectionConfig);
        resultsCallback.assertCalled();
        FledgeErrorResponse fledgeErrorResponse =
                resultsCallback.assertFailureReceived(FledgeErrorResponse.class);
        assertWithMessage("Error status code")
                .that(fledgeErrorResponse.getStatusCode())
                .isEqualTo(STATUS_INTERNAL_ERROR);
    }

    @Test
    public void testReportImpression_webViewNotInstalled_failsGracefully() throws Exception {
        mMockWebServerRule.startMockWebServer(mDispatcher);
        Uri buyerDecisionLogicUri = mMockWebServerRule.uriForPath(BUYER_BIDDING_LOGIC_URI_PATH);
        Uri uriPathForBiddingWithReportResults =
                mMockWebServerRule.uriForPath(BUYER_REPORTING_URI_PATH);
        DBBuyerDecisionLogic dbBuyerDecisionLogic =
                new DBBuyerDecisionLogic.Builder()
                        .setBiddingLogicUri(buyerDecisionLogicUri)
                        .setBuyerDecisionLogicJs(
                                String.format(
                                        READ_BID_FROM_AD_METADATA_JS,
                                        uriPathForBiddingWithReportResults))
                        .build();
        CustomAudienceSignals customAudienceSignals =
                CustomAudienceSignalsFixture.aCustomAudienceSignalsBuilder()
                        .setBuyer(mBuyer)
                        .build();

        DBAdSelection dbAdSelection =
                new DBAdSelection.Builder()
                        .setAdSelectionId(AD_SELECTION_ID)
                        .setCustomAudienceSignals(customAudienceSignals)
                        .setBuyerContextualSignals(BUYER_SIGNALS.toString())
                        .setBiddingLogicUri(buyerDecisionLogicUri)
                        .setWinningAdRenderUri(AD_URI_PREFIX)
                        .setWinningAdBid(BID)
                        .setCreationTimestamp(ACTIVATION_TIME)
                        .setCallerPackageName(CommonFixture.TEST_PACKAGE_NAME)
                        .build();

        mAdSelectionEntryDao.persistAdSelection(dbAdSelection);
        mAdSelectionEntryDao.persistBuyerDecisionLogic(dbBuyerDecisionLogic);

        // Create new service impl to let the WebView stub take effect
        AdSelectionServiceImpl adSelectionService =
                new AdSelectionServiceImpl(
                        mAdSelectionEntryDao,
                        mAppInstallDao,
                        mCustomAudienceDao,
                        mEncodedPayloadDao,
                        mFrequencyCapDao,
                        mEncryptionKeyDao,
                        mEnrollmentDao,
                        mAdServicesHttpsClient,
                        mMockDevContextFilter,
                        mLightweightExecutorService,
                        mBackgroundExecutorService,
                        mScheduledExecutor,
                        mContext,
                        mAdServicesLogger,
                        mFakeFlags,
                        mMockDebugFlags,
                        CallingAppUidSupplierProcessImpl.create(),
                        mFledgeAuthorizationFilter,
                        mMockAdSelectionServiceFilter,
                        mAdFilteringFeatureFactory,
                        mMockConsentManager,
                        mMultiCloudSupportStrategy,
                        mAdSelectionDebugReportDao,
                        mAdIdFetcher,
                        mMockUnusedKAnonSignJoinFactory,
                        false,
                        mRetryStrategyFactory,
                        CONSOLE_MESSAGE_IN_LOGS_ENABLED,
                        mAuctionServerDebugConfigurationGenerator);

        ReportImpressionInput input =
                new ReportImpressionInput.Builder()
                        .setAdSelectionId(AD_SELECTION_ID)
                        .setAdSelectionConfig(mAdSelectionConfig)
                        .setCallerPackageName(TEST_PACKAGE_NAME)
                        .build();

        // Count down callback + log interaction.
        // Impression reporting should still fail due to unsupported WebView,
        // but gracefully instead of crashing the process
        sLogger.d("calling report impression");
        SyncReportImpressionCallback resultsCallback =
                callReportImpression(adSelectionService, input);
        FledgeErrorResponse fledgeErrorResponse =
                resultsCallback.assertFailureReceived(FledgeErrorResponse.class);
        assertWithMessage("Error status code")
                .that(fledgeErrorResponse.getStatusCode())
                .isEqualTo(STATUS_INTERNAL_ERROR);
    }

    private DBCustomAudience createDBCustomAudience(
            final AdTechIdentifier buyer, final Uri biddingUri, List<Double> bids) {
        // Generate ads for with bids provided
        List<DBAdData> ads = new ArrayList<>();

        // Create ads with the buyer name and bid number as the ad URI
        // Add the bid value to the metadata
        for (int i = 0; i < bids.size(); i++) {
            // TODO(b/266015983) Add real data
            ads.add(
                    new DBAdData(
                            Uri.parse(AD_URI_PREFIX.toString() + buyer + "/ad" + (i + 1)),
                            "{\"result\":" + bids.get(i) + "}",
                            Collections.EMPTY_SET,
                            null,
                            null));
        }

        return new DBCustomAudience.Builder()
                .setOwner(buyer + CustomAudienceFixture.VALID_OWNER)
                .setBuyer(buyer)
                .setName(buyer.toString() + CustomAudienceFixture.VALID_NAME)
                .setActivationTime(CustomAudienceFixture.VALID_ACTIVATION_TIME)
                .setExpirationTime(CustomAudienceFixture.VALID_EXPIRATION_TIME)
                .setCreationTime(CommonFixture.FIXED_NOW_TRUNCATED_TO_MILLI)
                .setLastAdsAndBiddingDataUpdatedTime(CommonFixture.FIXED_NOW_TRUNCATED_TO_MILLI)
                .setUserBiddingSignals(CustomAudienceFixture.VALID_USER_BIDDING_SIGNALS)
                .setTrustedBiddingData(
                        new DBTrustedBiddingData.Builder()
                                .setUri(
                                        mMockWebServerRule.uriForPath(
                                                AdSelectionFailureE2ETest
                                                        .BUYER_TRUSTED_SIGNAL_URI_PATH))
                                .setKeys(TrustedBiddingDataFixture.getValidTrustedBiddingKeys())
                                .build())
                .setBiddingLogicUri(biddingUri)
                .setAds(ads)
                .build();
    }

    private SyncAdSelectionCallback invokeSelectAds(
            AdSelectionServiceImpl adSelectionService, AdSelectionConfig adSelectionConfig) {
        AdSelectionInput input =
                new AdSelectionInput.Builder()
                        .setAdSelectionConfig(adSelectionConfig)
                        .setCallerPackageName(CALLER_PACKAGE_NAME)
                        .build();
        SyncAdSelectionCallback syncAdSelectionCallback = new SyncAdSelectionCallback();
        adSelectionService.selectAds(input, mMockCallerMetadata, syncAdSelectionCallback);
        return syncAdSelectionCallback;
    }

    private SyncReportImpressionCallback callReportImpression(
            AdSelectionServiceImpl adSelectionService, ReportImpressionInput requestParams) {
        SyncReportImpressionCallback syncReportImpressionCallback =
                new SyncReportImpressionCallback();
        adSelectionService.reportImpression(requestParams, syncReportImpressionCallback);
        return syncReportImpressionCallback;
    }

    private static final class SyncAdSelectionCallback
            extends FailableOnResultSyncCallback<AdSelectionResponse, FledgeErrorResponse>
            implements AdSelectionCallback {

        @Override
        public void onSuccess(AdSelectionResponse adSelectionResponse) {
            injectResult(adSelectionResponse);
        }
    }

    private static final class SyncReportImpressionCallback
            extends FailableOnResultSyncCallback<Boolean, FledgeErrorResponse>
            implements ReportImpressionCallback {

        @Override
        public void onSuccess() {
            injectResult(true);
        }
    }

    private static final class AdSelectionFailureE2ETestFlags implements Flags {
        private final long mBiddingLogicVersion;

        AdSelectionFailureE2ETestFlags() {
            this(JsVersionRegister.BUYER_BIDDING_LOGIC_VERSION_VERSION_3);
        }

        AdSelectionFailureE2ETestFlags(long biddingLogicVersion) {
            mBiddingLogicVersion = biddingLogicVersion;
        }

        @Override
        public boolean getEnforceForegroundStatusForFledgeRunAdSelection() {
            return true;
        }

        @Override
        public boolean getEnforceForegroundStatusForFledgeReportImpression() {
            return true;
        }

        @Override
        public boolean getEnforceForegroundStatusForFledgeOverrides() {
            return true;
        }

        @Override
        public boolean getDisableFledgeEnrollmentCheck() {
            return true;
        }

        @Override
        public boolean getFledgeOnDeviceAuctionKillSwitch() {
            return false;
        }

        @Override
        public long getAdSelectionBiddingTimeoutPerCaMs() {
            return EXTENDED_FLEDGE_AD_SELECTION_BIDDING_TIMEOUT_PER_CA_MS;
        }

        @Override
        public long getAdSelectionScoringTimeoutMs() {
            return EXTENDED_FLEDGE_AD_SELECTION_SCORING_TIMEOUT_MS;
        }

        @Override
        public long getAdSelectionSelectingOutcomeTimeoutMs() {
            return EXTENDED_FLEDGE_AD_SELECTION_SELECTING_OUTCOME_TIMEOUT_MS;
        }

        @Override
        public long getAdSelectionOverallTimeoutMs() {
            return EXTENDED_FLEDGE_AD_SELECTION_OVERALL_TIMEOUT_MS;
        }

        @Override
        public long getAdSelectionFromOutcomesOverallTimeoutMs() {
            return EXTENDED_FLEDGE_AD_SELECTION_FROM_OUTCOMES_OVERALL_TIMEOUT_MS;
        }

        @Override
        public long getReportImpressionOverallTimeoutMs() {
            return EXTENDED_FLEDGE_REPORT_IMPRESSION_OVERALL_TIMEOUT_MS;
        }

        @Override
        public int getFledgeBackgroundFetchNetworkConnectTimeoutMs() {
            return EXTENDED_FLEDGE_BACKGROUND_FETCH_NETWORK_CONNECT_TIMEOUT_MS;
        }

        @Override
        public int getFledgeBackgroundFetchNetworkReadTimeoutMs() {
            return EXTENDED_FLEDGE_BACKGROUND_FETCH_NETWORK_READ_TIMEOUT_MS;
        }

        @Override
        public float getSdkRequestPermitsPerSecond() {
            // Unlimited rate for unit tests to avoid flake in tests due to rate
            // limiting
            return -1;
        }

        @Override
        public long getFledgeAdSelectionBiddingLogicJsVersion() {
            return mBiddingLogicVersion;
        }
    }
}

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

import static android.adservices.adselection.CustomAudienceBiddingInfoFixture.DATA_VERSION_1;
import static android.adservices.adselection.CustomAudienceBiddingInfoFixture.DATA_VERSION_2;
import static android.adservices.adselection.SignedContextualAdsFixture.signContextualAds;
import static android.adservices.common.AdServicesStatusUtils.ILLEGAL_STATE_BACKGROUND_CALLER_ERROR_MESSAGE;
import static android.adservices.common.AdServicesStatusUtils.RATE_LIMIT_REACHED_ERROR_MESSAGE;
import static android.adservices.common.AdServicesStatusUtils.STATUS_BACKGROUND_CALLER;
import static android.adservices.common.AdServicesStatusUtils.STATUS_INTERNAL_ERROR;
import static android.adservices.common.AdServicesStatusUtils.STATUS_RATE_LIMIT_REACHED;
import static android.adservices.common.AdServicesStatusUtils.STATUS_SUCCESS;
import static android.adservices.common.AdServicesStatusUtils.STATUS_TIMEOUT;
import static android.adservices.common.AdServicesStatusUtils.STATUS_UNSET;
import static android.adservices.common.AdServicesStatusUtils.STATUS_USER_CONSENT_REVOKED;
import static android.adservices.common.CommonFixture.TEST_PACKAGE_NAME;

import static com.android.adservices.common.CommonFlagsValues.EXTENDED_FLEDGE_AD_SELECTION_BIDDING_TIMEOUT_PER_CA_MS;
import static com.android.adservices.common.CommonFlagsValues.EXTENDED_FLEDGE_AD_SELECTION_FROM_OUTCOMES_OVERALL_TIMEOUT_MS;
import static com.android.adservices.common.CommonFlagsValues.EXTENDED_FLEDGE_AD_SELECTION_OVERALL_TIMEOUT_MS;
import static com.android.adservices.common.CommonFlagsValues.EXTENDED_FLEDGE_AD_SELECTION_SCORING_TIMEOUT_MS;
import static com.android.adservices.common.CommonFlagsValues.EXTENDED_FLEDGE_AD_SELECTION_SELECTING_OUTCOME_TIMEOUT_MS;
import static com.android.adservices.common.CommonFlagsValues.EXTENDED_FLEDGE_REPORT_IMPRESSION_OVERALL_TIMEOUT_MS;
import static com.android.adservices.data.adselection.AdSelectionDatabase.DATABASE_NAME;
import static com.android.adservices.service.adselection.AdSelectionRunner.AD_SELECTION_ERROR_PATTERN;
import static com.android.adservices.service.adselection.AdSelectionRunner.AD_SELECTION_TIMED_OUT;
import static com.android.adservices.service.adselection.AdSelectionRunner.ERROR_AD_SELECTION_FAILURE;
import static com.android.adservices.service.adselection.AdSelectionRunner.ERROR_NO_BUYERS_OR_CONTEXTUAL_ADS_AVAILABLE;
import static com.android.adservices.service.adselection.AdSelectionRunner.ERROR_NO_CA_AND_CONTEXTUAL_ADS_AVAILABLE;
import static com.android.adservices.service.adselection.AdSelectionRunner.ERROR_NO_VALID_BIDS_OR_CONTEXTUAL_ADS_FOR_SCORING;
import static com.android.adservices.service.adselection.AdSelectionRunner.ERROR_NO_WINNING_AD_FOUND;
import static com.android.adservices.service.adselection.AdSelectionRunner.ON_DEVICE_AUCTION_KILL_SWITCH_ENABLED;
import static com.android.adservices.service.adselection.AdSelectionScriptEngine.NUM_BITS_STOCHASTIC_ROUNDING;
import static com.android.adservices.service.adselection.signature.ProtectedAudienceSignatureManager.PUBLIC_TEST_KEY_STRING;
import static com.android.adservices.service.stats.AdFilteringLoggerImplTestFixture.AD_FILTERING_END;
import static com.android.adservices.service.stats.AdFilteringLoggerImplTestFixture.AD_FILTERING_OVERALL_LATENCY_MS;
import static com.android.adservices.service.stats.AdFilteringLoggerImplTestFixture.AD_FILTERING_START;
import static com.android.adservices.service.stats.AdFilteringLoggerImplTestFixture.APP_INSTALL_FILTERING_END;
import static com.android.adservices.service.stats.AdFilteringLoggerImplTestFixture.APP_INSTALL_FILTERING_LATENCY_MS;
import static com.android.adservices.service.stats.AdFilteringLoggerImplTestFixture.APP_INSTALL_FILTERING_START;
import static com.android.adservices.service.stats.AdFilteringLoggerImplTestFixture.FREQUENCY_CAP_FILTERING_LATENCY_MS;
import static com.android.adservices.service.stats.AdFilteringLoggerImplTestFixture.FREQ_CAP_FILTERING_END;
import static com.android.adservices.service.stats.AdFilteringLoggerImplTestFixture.FREQ_CAP_FILTERING_START;
import static com.android.adservices.service.stats.AdSelectionExecutionLoggerTestFixture.BIDDING_STAGE_END_TIMESTAMP;
import static com.android.adservices.service.stats.AdSelectionExecutionLoggerTestFixture.BIDDING_STAGE_START_TIMESTAMP;
import static com.android.adservices.service.stats.AdSelectionExecutionLoggerTestFixture.DB_AD_SELECTION_FILE_SIZE;
import static com.android.adservices.service.stats.AdSelectionExecutionLoggerTestFixture.GET_BUYERS_CUSTOM_AUDIENCE_END_TIMESTAMP;
import static com.android.adservices.service.stats.AdSelectionExecutionLoggerTestFixture.GET_BUYERS_CUSTOM_AUDIENCE_LATENCY_MS;
import static com.android.adservices.service.stats.AdSelectionExecutionLoggerTestFixture.IS_RMKT_ADS_WON;
import static com.android.adservices.service.stats.AdSelectionExecutionLoggerTestFixture.IS_RMKT_ADS_WON_UNSET;
import static com.android.adservices.service.stats.AdSelectionExecutionLoggerTestFixture.PERSIST_AD_SELECTION_END_TIMESTAMP;
import static com.android.adservices.service.stats.AdSelectionExecutionLoggerTestFixture.PERSIST_AD_SELECTION_START_TIMESTAMP;
import static com.android.adservices.service.stats.AdSelectionExecutionLoggerTestFixture.RUN_AD_BIDDING_END_TIMESTAMP;
import static com.android.adservices.service.stats.AdSelectionExecutionLoggerTestFixture.RUN_AD_BIDDING_LATENCY_MS;
import static com.android.adservices.service.stats.AdSelectionExecutionLoggerTestFixture.RUN_AD_BIDDING_START_TIMESTAMP;
import static com.android.adservices.service.stats.AdSelectionExecutionLoggerTestFixture.RUN_AD_SELECTION_INTERNAL_FINAL_LATENCY_MS;
import static com.android.adservices.service.stats.AdSelectionExecutionLoggerTestFixture.RUN_AD_SELECTION_OVERALL_LATENCY_MS;
import static com.android.adservices.service.stats.AdSelectionExecutionLoggerTestFixture.START_ELAPSED_TIMESTAMP;
import static com.android.adservices.service.stats.AdSelectionExecutionLoggerTestFixture.STOP_ELAPSED_TIMESTAMP;
import static com.android.adservices.service.stats.AdSelectionExecutionLoggerTestFixture.TOTAL_BIDDING_STAGE_LATENCY_IN_MS;
import static com.android.adservices.service.stats.AdSelectionExecutionLoggerTestFixture.sCallerMetadata;
import static com.android.adservices.service.stats.AdServicesStatsLog.AD_SERVICES_API_CALLED__API_NAME__SELECT_ADS;
import static com.android.adservices.service.stats.AdsRelevanceStatusUtils.FILTER_PROCESS_TYPE_CONTEXTUAL_ADS;
import static com.android.adservices.service.stats.AdsRelevanceStatusUtils.FILTER_PROCESS_TYPE_CUSTOM_AUDIENCES;
import static com.android.adservices.service.stats.SignatureVerificationLoggerImplTestFixture.SIGNATURE_VERIFICATION_END_KEY_FETCH;
import static com.android.adservices.service.stats.SignatureVerificationLoggerImplTestFixture.SIGNATURE_VERIFICATION_END_SERIALIZATION;
import static com.android.adservices.service.stats.SignatureVerificationLoggerImplTestFixture.SIGNATURE_VERIFICATION_END_VERIFICATION;
import static com.android.adservices.service.stats.SignatureVerificationLoggerImplTestFixture.SIGNATURE_VERIFICATION_KEY_FETCH_LATENCY_MS;
import static com.android.adservices.service.stats.SignatureVerificationLoggerImplTestFixture.SIGNATURE_VERIFICATION_SERIALIZATION_LATENCY_MS;
import static com.android.adservices.service.stats.SignatureVerificationLoggerImplTestFixture.SIGNATURE_VERIFICATION_START_KEY_FETCH;
import static com.android.adservices.service.stats.SignatureVerificationLoggerImplTestFixture.SIGNATURE_VERIFICATION_START_SERIALIZATION;
import static com.android.adservices.service.stats.SignatureVerificationLoggerImplTestFixture.SIGNATURE_VERIFICATION_START_VERIFICATION;
import static com.android.adservices.service.stats.SignatureVerificationLoggerImplTestFixture.SIGNATURE_VERIFICATION_VERIFICATION_LATENCY_MS;
import static com.android.dx.mockito.inline.extended.ExtendedMockito.any;
import static com.android.dx.mockito.inline.extended.ExtendedMockito.anyLong;
import static com.android.dx.mockito.inline.extended.ExtendedMockito.doNothing;
import static com.android.dx.mockito.inline.extended.ExtendedMockito.doReturn;
import static com.android.dx.mockito.inline.extended.ExtendedMockito.doThrow;
import static com.android.dx.mockito.inline.extended.ExtendedMockito.eq;
import static com.android.dx.mockito.inline.extended.ExtendedMockito.never;
import static com.android.dx.mockito.inline.extended.ExtendedMockito.times;
import static com.android.dx.mockito.inline.extended.ExtendedMockito.verify;
import static com.android.dx.mockito.inline.extended.ExtendedMockito.verifyZeroInteractions;
import static com.android.dx.mockito.inline.extended.ExtendedMockito.when;

import static com.google.common.truth.Truth.assertThat;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertNull;
import static org.junit.Assert.assertTrue;
import static org.mockito.ArgumentMatchers.anyList;
import static org.mockito.Mockito.spy;

import android.adservices.adselection.AdBiddingOutcomeFixture;
import android.adservices.adselection.AdSelectionCallback;
import android.adservices.adselection.AdSelectionConfig;
import android.adservices.adselection.AdSelectionConfigFixture;
import android.adservices.adselection.AdSelectionInput;
import android.adservices.adselection.AdSelectionResponse;
import android.adservices.adselection.SignedContextualAds;
import android.adservices.adselection.SignedContextualAdsFixture;
import android.adservices.common.AdDataFixture;
import android.adservices.common.AdFilters;
import android.adservices.common.AdSelectionSignals;
import android.adservices.common.AdServicesStatusUtils;
import android.adservices.common.AdTechIdentifier;
import android.adservices.common.AppInstallFilters;
import android.adservices.common.CommonFixture;
import android.adservices.common.FledgeErrorResponse;
import android.adservices.common.FrequencyCapFilters;
import android.adservices.common.KeyedFrequencyCap;
import android.adservices.common.KeyedFrequencyCapFixture;
import android.adservices.customaudience.CustomAudienceFixture;
import android.adservices.exceptions.AdServicesException;
import android.net.Uri;
import android.os.LimitExceededException;
import android.os.Process;
import android.os.RemoteException;

import androidx.room.Room;
import androidx.test.core.app.ApplicationProvider;

import com.android.adservices.common.AdServicesExtendedMockitoTestCase;
import com.android.adservices.common.DBAdDataFixture;
import com.android.adservices.common.WebViewSupportUtil;
import com.android.adservices.concurrency.AdServicesExecutors;
import com.android.adservices.customaudience.DBCustomAudienceFixture;
import com.android.adservices.data.adselection.AdSelectionDatabase;
import com.android.adservices.data.adselection.AdSelectionEntryDao;
import com.android.adservices.data.adselection.DBAdSelection;
import com.android.adservices.data.adselection.DBAdSelectionEntry;
import com.android.adservices.data.adselection.DBAdSelectionHistogramInfo;
import com.android.adservices.data.adselection.DBReportingComputationInfo;
import com.android.adservices.data.adselection.datahandlers.AdSelectionInitialization;
import com.android.adservices.data.adselection.datahandlers.WinningCustomAudience;
import com.android.adservices.data.common.DBAdData;
import com.android.adservices.data.customaudience.CustomAudienceDao;
import com.android.adservices.data.customaudience.CustomAudienceDatabase;
import com.android.adservices.data.customaudience.DBCustomAudience;
import com.android.adservices.data.encryptionkey.EncryptionKeyDao;
import com.android.adservices.data.enrollment.EnrollmentDao;
import com.android.adservices.service.DebugFlags;
import com.android.adservices.service.Flags;
import com.android.adservices.service.FlagsFactory;
import com.android.adservices.service.adselection.debug.DebugReportSenderStrategy;
import com.android.adservices.service.adselection.debug.DebugReporting;
import com.android.adservices.service.common.AdSelectionServiceFilter;
import com.android.adservices.service.common.AppImportanceFilter;
import com.android.adservices.service.common.FrequencyCapAdDataValidator;
import com.android.adservices.service.common.FrequencyCapAdDataValidatorNoOpImpl;
import com.android.adservices.service.common.Throttler;
import com.android.adservices.service.common.cache.CacheProviderFactory;
import com.android.adservices.service.common.httpclient.AdServicesHttpClientRequest;
import com.android.adservices.service.common.httpclient.AdServicesHttpClientResponse;
import com.android.adservices.service.common.httpclient.AdServicesHttpsClient;
import com.android.adservices.service.consent.ConsentManager;
import com.android.adservices.service.devapi.DevContext;
import com.android.adservices.service.encryptionkey.EncryptionKey;
import com.android.adservices.service.enrollment.EnrollmentData;
import com.android.adservices.service.exception.FilterException;
import com.android.adservices.service.kanon.KAnonMessageEntity;
import com.android.adservices.service.kanon.KAnonSignJoinFactory;
import com.android.adservices.service.kanon.KAnonSignJoinManager;
import com.android.adservices.service.stats.AdFilteringProcessAdSelectionReportedStats;
import com.android.adservices.service.stats.AdSelectionExecutionLogger;
import com.android.adservices.service.stats.AdServicesLogger;
import com.android.adservices.service.stats.AdServicesLoggerImpl;
import com.android.adservices.service.stats.AdServicesStatsLog;
import com.android.adservices.service.stats.RunAdBiddingProcessReportedStats;
import com.android.adservices.service.stats.RunAdSelectionProcessReportedStats;
import com.android.adservices.service.stats.SignatureVerificationStats;
import com.android.adservices.shared.testing.SupportedByConditionRule;
import com.android.dx.mockito.inline.extended.ExtendedMockito;
import com.android.modules.utils.testing.ExtendedMockitoRule.MockStatic;
import com.android.modules.utils.testing.ExtendedMockitoRule.SpyStatic;

import com.google.common.collect.ImmutableList;
import com.google.common.util.concurrent.FluentFuture;
import com.google.common.util.concurrent.Futures;

import org.junit.After;
import org.junit.Assert;
import org.junit.Before;
import org.junit.Rule;
import org.junit.Test;
import org.mockito.ArgumentCaptor;
import org.mockito.Captor;
import org.mockito.Mock;
import org.mockito.Spy;
import org.mockito.internal.stubbing.answers.AnswersWithDelay;
import org.mockito.internal.stubbing.answers.Returns;

import java.io.File;
import java.time.Clock;
import java.time.Instant;
import java.time.temporal.ChronoUnit;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Calendar;
import java.util.Collections;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.concurrent.Callable;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.ExecutionException;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.ScheduledThreadPoolExecutor;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.TimeoutException;
import java.util.stream.Collectors;

/**
 * This test covers strictly the unit of {@link AdSelectionRunner} The dependencies in this test are
 * mocked and provide expected mock responses when invoked with desired input
 */
@SuppressWarnings("FutureReturnValueIgnored")
@SpyStatic(FlagsFactory.class)
@SpyStatic(DebugFlags.class)
@MockStatic(com.android.adservices.shared.util.Clock.class)
public final class OnDeviceAdSelectionRunnerTest extends AdServicesExtendedMockitoTestCase {
    private static final AdTechIdentifier BUYER_1 = AdSelectionConfigFixture.BUYER_1;
    private static final AdTechIdentifier BUYER_2 = AdSelectionConfigFixture.BUYER_2;
    private static final Long AD_SELECTION_ID = 1234L;
    private static final String ERROR_INVALID_JSON = "Invalid Json Exception";
    private static final int CALLER_UID = Process.myUid();
    private static final String MY_APP_PACKAGE_NAME = TEST_PACKAGE_NAME;

    private static final AdTechIdentifier SELLER_VALID =
            AdTechIdentifier.fromString("developer.android.com");
    private static final String BUYER_BIDDING_LOGIC_URI_PATH = "/buyer/bidding/logic/";
    private static final String DECISION_LOGIC_PATH = "/test/decisions_logic_uris";
    private static final Uri DECISION_LOGIC_URI =
            CommonFixture.getUri(SELLER_VALID, DECISION_LOGIC_PATH);
    private static final String TRUSTED_SIGNALS_PATH = "/test/trusted_signals_uri";
    private static final Uri TRUSTED_SIGNALS_URI =
            CommonFixture.getUri(SELLER_VALID, TRUSTED_SIGNALS_PATH);
    private static final int PERSIST_AD_SELECTION_LATENCY_MS =
            (int) (PERSIST_AD_SELECTION_END_TIMESTAMP - PERSIST_AD_SELECTION_START_TIMESTAMP);
    private static final String BUYER_DECISION_LOGIC_JS =
            "function reportWin(ad_selection_signals, per_buyer_signals, signals_for_buyer,"
                    + " contextual_signals, custom_audience_signals) { \n"
                    + " return {'status': 0, 'results': {'reporting_uri': '"
                    + " buyerReportingUri "
                    + "' } };\n"
                    + "}";
    @Mock private AdsScoreGenerator mMockAdsScoreGenerator;
    @Mock private AdSelectionIdGenerator mMockAdSelectionIdGenerator;
    @Spy private Clock mClockSpy = Clock.systemUTC();
    @Mock private com.android.adservices.shared.util.Clock mLoggerClockMock;
    @Mock private File mMockDBAdSelectionFile;
    @Mock private FrequencyCapAdFilterer mMockFrequencyCapAdFilterer;
    @Mock private AppInstallAdFilterer mMockAppInstallAdFilterer;
    @Mock private AdServicesHttpsClient mMockHttpClient;
    @Mock private AdCounterKeyCopier mAdCounterKeyCopierMock;
    @Mock private FrequencyCapAdDataValidator mFrequencyCapAdDataValidatorMock;
    @Mock private AdCounterHistogramUpdater mAdCounterHistogramUpdaterMock;
    @Mock private DebugReporting mDebugReportingMock;
    @Mock private DebugReportSenderStrategy mDebugReportSenderMock;
    @Mock private EnrollmentDao mEnrollmentDaoMock;
    @Mock private EncryptionKeyDao mEncryptionKeyDaoMock;

    @Captor
    ArgumentCaptor<RunAdSelectionProcessReportedStats>
            mRunAdSelectionProcessReportedStatsArgumentCaptor;

    @Captor ArgumentCaptor<SignatureVerificationStats> mSignatureVerificationStatsArgumentCaptor;
    @Captor ArgumentCaptor<AdFilteringProcessAdSelectionReportedStats> mAdFilteringCaptor;

    @Captor
    ArgumentCaptor<RunAdBiddingProcessReportedStats>
            mRunAdBiddingProcessReportedStatsArgumentCaptor;

    @Captor ArgumentCaptor<AdSelectionConfig> mAdSelectionConfigArgumentCaptor;

    @Mock private PerBuyerBiddingRunner mPerBuyerBiddingRunnerMock;

    private Flags mFakeFlags = new OnDeviceAdSelectionRunnerTestFlags();
    private AdServicesHttpsClient mAdServicesHttpsClient;
    private ExecutorService mLightweightExecutorService;
    private ExecutorService mBackgroundExecutorService;
    private ScheduledThreadPoolExecutor mScheduledExecutor;
    private CustomAudienceDao mCustomAudienceDao;
    private AdSelectionEntryDao mAdSelectionEntryDaoSpy;
    private AdServicesLogger mAdServicesLoggerMock =
            ExtendedMockito.mock(AdServicesLoggerImpl.class);
    private List<AdTechIdentifier> mCustomAudienceBuyers;
    private AdSelectionConfig.Builder mAdSelectionConfigBuilder;
    private AdSelectionConfig.Builder mAdSelectionConfigBuilderWithNoBuyers;

    private DBCustomAudience mDBCustomAudienceForBuyer1;
    private DBCustomAudience mDBCustomAudienceForBuyer2;
    private List<DBCustomAudience> mBuyerCustomAudienceList;

    private AdBiddingOutcome mAdBiddingOutcomeForBuyer1;
    private AdBiddingOutcome mAdBiddingOutcomeForBuyer2;
    private List<AdBiddingOutcome> mAdBiddingOutcomeList;

    private AdScoringOutcome mAdScoringOutcomeForBuyer1;
    private AdScoringOutcome mAdScoringOutcomeForBuyer2;
    private List<AdScoringOutcome> mAdScoringOutcomeList;

    private AdSelectionRunner mAdSelectionRunner;
    private AdSelectionExecutionLogger mAdSelectionExecutionLogger;

    // Use no-op implementations and test specific cases with the mocked objects
    private final FrequencyCapAdFilterer mFrequencyCapAdFilterer =
            new FrequencyCapAdFiltererNoOpImpl();
    private final AppInstallAdFilterer mAppInstallAdFilterer = new AppInstallAdFiltererNoOpImpl();
    private final AdCounterKeyCopier mAdCounterKeyCopier = new AdCounterKeyCopierNoOpImpl();
    private final FrequencyCapAdDataValidator mFrequencyCapAdDataValidator =
            new FrequencyCapAdDataValidatorNoOpImpl();
    private final AdCounterHistogramUpdater mAdCounterHistogramUpdater =
            new AdCounterHistogramUpdaterNoOpImpl();

    @Mock AdSelectionServiceFilter mAdSelectionServiceFilterMock;
    @Mock KAnonSignJoinFactory mKAnonSignJoinFactoryMock;
    @Mock KAnonSignJoinManager mKAnonSignJoinManagerMock;
    @Captor ArgumentCaptor<List<KAnonMessageEntity>> kanonMessageEntitiesCaptor;

    // Every test in this class requires that the JS Sandbox be available. The JS Sandbox
    // availability depends on an external component (the system webview) being higher than a
    // certain minimum version.
    @Rule(order = 1)
    public final SupportedByConditionRule webViewSupportsJSSandbox =
            WebViewSupportUtil.createJSSandboxAvailableRule(
                    ApplicationProvider.getApplicationContext());

    @Before
    public void setUp() {
        mocker.mockGetDebugFlags(mMockDebugFlags);
        mocker.mockGetConsentNotificationDebugMode(false);
        // Initializing up here so object is spied
        mAdSelectionEntryDaoSpy =
                spy(
                        Room.inMemoryDatabaseBuilder(mSpyContext, AdSelectionDatabase.class)
                                .build()
                                .adSelectionEntryDao());

        doReturn(mLoggerClockMock).when(com.android.adservices.shared.util.Clock::getInstance);
        mLightweightExecutorService = AdServicesExecutors.getLightWeightExecutor();
        mBackgroundExecutorService = AdServicesExecutors.getBackgroundExecutor();
        mScheduledExecutor = AdServicesExecutors.getScheduler();
        mAdServicesHttpsClient =
                new AdServicesHttpsClient(
                        AdServicesExecutors.getBlockingExecutor(),
                        CacheProviderFactory.createNoOpCache());
        mCustomAudienceDao =
                Room.inMemoryDatabaseBuilder(mSpyContext, CustomAudienceDatabase.class)
                        .addTypeConverter(new DBCustomAudience.Converters(true, true, true))
                        .build()
                        .customAudienceDao();

        mCustomAudienceBuyers = Arrays.asList(BUYER_1, BUYER_2);
        mAdSelectionConfigBuilder =
                AdSelectionConfigFixture.anAdSelectionConfigBuilder()
                        .setSeller(SELLER_VALID)
                        .setCustomAudienceBuyers(mCustomAudienceBuyers)
                        .setDecisionLogicUri(DECISION_LOGIC_URI)
                        .setTrustedScoringSignalsUri(TRUSTED_SIGNALS_URI);
        mAdSelectionConfigBuilderWithNoBuyers =
                AdSelectionConfigFixture.anAdSelectionConfigBuilder()
                        .setSeller(SELLER_VALID)
                        .setCustomAudienceBuyers(Collections.emptyList())
                        .setDecisionLogicUri(DECISION_LOGIC_URI)
                        .setTrustedScoringSignalsUri(TRUSTED_SIGNALS_URI);

        mDBCustomAudienceForBuyer1 = createDBCustomAudience(BUYER_1);
        mDBCustomAudienceForBuyer2 = createDBCustomAudience(BUYER_2);
        mBuyerCustomAudienceList =
                Arrays.asList(mDBCustomAudienceForBuyer1, mDBCustomAudienceForBuyer2);

        mAdBiddingOutcomeForBuyer1 =
                AdBiddingOutcomeFixture.anAdBiddingOutcomeBuilder(BUYER_1, 1.0).build();
        mAdBiddingOutcomeForBuyer2 =
                AdBiddingOutcomeFixture.anAdBiddingOutcomeBuilder(BUYER_2, 2.0).build();
        mAdBiddingOutcomeList =
                Arrays.asList(mAdBiddingOutcomeForBuyer1, mAdBiddingOutcomeForBuyer2);

        mAdScoringOutcomeForBuyer1 =
                AdScoringOutcomeFixture.anAdScoringBuilder(BUYER_1, 2.0).build();
        mAdScoringOutcomeForBuyer2 =
                AdScoringOutcomeFixture.anAdScoringBuilder(BUYER_2, 3.0).build();
        mAdScoringOutcomeList =
                Arrays.asList(mAdScoringOutcomeForBuyer1, mAdScoringOutcomeForBuyer2);

        when(mSpyContext.getDatabasePath(DATABASE_NAME)).thenReturn(mMockDBAdSelectionFile);
        when(mMockDBAdSelectionFile.length()).thenReturn(DB_AD_SELECTION_FILE_SIZE);
        when(mDebugReportingMock.isEnabled()).thenReturn(false);
        when(mDebugReportingMock.getSenderStrategy()).thenReturn(mDebugReportSenderMock);

        doNothing()
                .when(mAdSelectionServiceFilterMock)
                .filterRequest(
                        SELLER_VALID,
                        MY_APP_PACKAGE_NAME,
                        true,
                        true,
                        true,
                        CALLER_UID,
                        AdServicesStatsLog.AD_SERVICES_API_CALLED__API_NAME__SELECT_ADS,
                        Throttler.ApiKey.FLEDGE_API_SELECT_ADS,
                        DevContext.createForDevOptionsDisabled());
    }

    private DBCustomAudience createDBCustomAudience(final AdTechIdentifier buyer) {
        return DBCustomAudienceFixture.getValidBuilderByBuyer(buyer)
                .setOwner(buyer.toString() + CustomAudienceFixture.VALID_OWNER)
                .setName(buyer + CustomAudienceFixture.VALID_NAME)
                .setCreationTime(CommonFixture.FIXED_NOW_TRUNCATED_TO_MILLI)
                .setLastAdsAndBiddingDataUpdatedTime(CommonFixture.FIXED_NOW_TRUNCATED_TO_MILLI)
                .build();
    }

    private DBCustomAudience createDBCustomAudienceNoFilters(final AdTechIdentifier buyer) {
        return DBCustomAudienceFixture.getValidBuilderByBuyerNoFilters(buyer)
                .setOwner(buyer.toString() + CustomAudienceFixture.VALID_OWNER)
                .setName(buyer + CustomAudienceFixture.VALID_NAME)
                .setCreationTime(CommonFixture.FIXED_NOW_TRUNCATED_TO_MILLI)
                .setLastAdsAndBiddingDataUpdatedTime(CommonFixture.FIXED_NOW_TRUNCATED_TO_MILLI)
                .build();
    }

    @Test
    public void testRunAdSelectionSuccess() throws AdServicesException {
        AdSelectionConfig adSelectionConfig = mAdSelectionConfigBuilder.build();
        verifyAndSetupCommonSuccessScenario(adSelectionConfig);
        mAdSelectionRunner =
                new OnDeviceAdSelectionRunner(
                        mCustomAudienceDao,
                        mAdSelectionEntryDaoSpy,
                        mEncryptionKeyDaoMock,
                        mEnrollmentDaoMock,
                        mAdServicesHttpsClient,
                        mLightweightExecutorService,
                        mBackgroundExecutorService,
                        mScheduledExecutor,
                        mMockAdsScoreGenerator,
                        mMockAdSelectionIdGenerator,
                        mClockSpy,
                        mAdServicesLoggerMock,
                        mFakeFlags,
                        mMockDebugFlags,
                        CALLER_UID,
                        mAdSelectionServiceFilterMock,
                        mAdSelectionExecutionLogger,
                        mPerBuyerBiddingRunnerMock,
                        mFrequencyCapAdFilterer,
                        mAdCounterKeyCopier,
                        mAdCounterHistogramUpdater,
                        mFrequencyCapAdDataValidator,
                        mDebugReportingMock,
                        false,
                        mKAnonSignJoinFactoryMock,
                        mAppInstallAdFilterer);
        // Populating the Custom Audience DB
        mCustomAudienceDao.insertOrOverwriteCustomAudience(
                mDBCustomAudienceForBuyer1,
                CustomAudienceFixture.getValidDailyUpdateUriByBuyer(BUYER_1),
                false);
        mCustomAudienceDao.insertOrOverwriteCustomAudience(
                mDBCustomAudienceForBuyer2,
                CustomAudienceFixture.getValidDailyUpdateUriByBuyer(BUYER_2),
                false);
        Instant adSelectionCreationTs = Clock.systemUTC().instant().truncatedTo(ChronoUnit.MILLIS);
        when(mClockSpy.instant()).thenReturn(adSelectionCreationTs);
        DBAdSelectionEntry expectedAdSelectionResult =
                new DBAdSelectionEntry.Builder()
                        .setAdSelectionId(AD_SELECTION_ID)
                        .setBiddingLogicUri(mDBCustomAudienceForBuyer2.getBiddingLogicUri())
                        .setWinningAdBid(
                                mAdScoringOutcomeForBuyer2.getAdWithScore().getAdWithBid().getBid())
                        .setCustomAudienceSignals(
                                mAdScoringOutcomeForBuyer2.getCustomAudienceSignals())
                        .setWinningAdRenderUri(
                                mAdScoringOutcomeForBuyer2
                                        .getAdWithScore()
                                        .getAdWithBid()
                                        .getAdData()
                                        .getRenderUri())
                        .setBuyerDecisionLogicJs(
                                mAdBiddingOutcomeForBuyer1
                                        .getCustomAudienceBiddingInfo()
                                        .getBuyerDecisionLogicJs())
                        // TODO(b/230569187) add contextual signals once supported in the main logic
                        .setBuyerContextualSignals("{}")
                        .setSellerContextualSignals("{}")
                        .setCreationTimestamp(adSelectionCreationTs)
                        .build();

        AdSelectionTestCallback resultsCallback =
                invokeRunAdSelection(mAdSelectionRunner, adSelectionConfig, MY_APP_PACKAGE_NAME);

        verify(mPerBuyerBiddingRunnerMock)
                .runBidding(
                        BUYER_1,
                        ImmutableList.of(mDBCustomAudienceForBuyer1),
                        mFakeFlags.getAdSelectionBiddingTimeoutPerBuyerMs(),
                        adSelectionConfig);
        verify(mPerBuyerBiddingRunnerMock)
                .runBidding(
                        BUYER_2,
                        ImmutableList.of(mDBCustomAudienceForBuyer2),
                        mFakeFlags.getAdSelectionBiddingTimeoutPerBuyerMs(),
                        adSelectionConfig);

        verify(mMockAdsScoreGenerator).runAdScoring(mAdBiddingOutcomeList, adSelectionConfig);

        assertTrue(resultsCallback.mIsSuccess);
        assertEquals(
                expectedAdSelectionResult.getAdSelectionId(),
                resultsCallback.mAdSelectionResponse.getAdSelectionId());
        assertEquals(
                expectedAdSelectionResult.getWinningAdRenderUri(),
                resultsCallback.mAdSelectionResponse.getRenderUri());
        assertTrue(mAdSelectionEntryDaoSpy.doesAdSelectionIdExist(AD_SELECTION_ID));
        assertEquals(
                expectedAdSelectionResult,
                mAdSelectionEntryDaoSpy.getAdSelectionEntityById(AD_SELECTION_ID));
        verifyLogForSuccessfulBiddingProcess(mAdBiddingOutcomeList);
        verifyLogForSuccessfulAdSelectionProcess();
        verifyDebugReportingWasNotCalled();
        verify(mAdServicesLoggerMock)
                .logFledgeApiCallStats(
                        eq(AD_SERVICES_API_CALLED__API_NAME__SELECT_ADS),
                        eq(TEST_PACKAGE_NAME),
                        eq(STATUS_SUCCESS),
                        eq(RUN_AD_SELECTION_OVERALL_LATENCY_MS));
    }

    @Test
    public void testRunAdSelectionSuccessWithUXNotificationEnforcementDisabled()
            throws AdServicesException {
        mocker.mockGetConsentNotificationDebugMode(true);

        doReturn(mFakeFlags).when(FlagsFactory::getFlags);

        AdSelectionConfig adSelectionConfig = mAdSelectionConfigBuilder.build();
        verifyAndSetupCommonSuccessScenario(adSelectionConfig);
        mAdSelectionRunner =
                new OnDeviceAdSelectionRunner(
                        mCustomAudienceDao,
                        mAdSelectionEntryDaoSpy,
                        mEncryptionKeyDaoMock,
                        mEnrollmentDaoMock,
                        mAdServicesHttpsClient,
                        mLightweightExecutorService,
                        mBackgroundExecutorService,
                        mScheduledExecutor,
                        mMockAdsScoreGenerator,
                        mMockAdSelectionIdGenerator,
                        mClockSpy,
                        mAdServicesLoggerMock,
                        mFakeFlags,
                        mMockDebugFlags,
                        CALLER_UID,
                        mAdSelectionServiceFilterMock,
                        mAdSelectionExecutionLogger,
                        mPerBuyerBiddingRunnerMock,
                        mFrequencyCapAdFilterer,
                        mAdCounterKeyCopier,
                        mAdCounterHistogramUpdater,
                        mFrequencyCapAdDataValidator,
                        mDebugReportingMock,
                        false,
                        mKAnonSignJoinFactoryMock,
                        mAppInstallAdFilterer);
        // Populating the Custom Audience DB
        mCustomAudienceDao.insertOrOverwriteCustomAudience(
                mDBCustomAudienceForBuyer1,
                CustomAudienceFixture.getValidDailyUpdateUriByBuyer(BUYER_1),
                false);
        mCustomAudienceDao.insertOrOverwriteCustomAudience(
                mDBCustomAudienceForBuyer2,
                CustomAudienceFixture.getValidDailyUpdateUriByBuyer(BUYER_2),
                false);
        Instant adSelectionCreationTs = Clock.systemUTC().instant().truncatedTo(ChronoUnit.MILLIS);
        when(mClockSpy.instant()).thenReturn(adSelectionCreationTs);
        DBAdSelectionEntry expectedAdSelectionResult =
                new DBAdSelectionEntry.Builder()
                        .setAdSelectionId(AD_SELECTION_ID)
                        .setBiddingLogicUri(mDBCustomAudienceForBuyer2.getBiddingLogicUri())
                        .setWinningAdBid(
                                mAdScoringOutcomeForBuyer2.getAdWithScore().getAdWithBid().getBid())
                        .setCustomAudienceSignals(
                                mAdScoringOutcomeForBuyer2.getCustomAudienceSignals())
                        .setWinningAdRenderUri(
                                mAdScoringOutcomeForBuyer2
                                        .getAdWithScore()
                                        .getAdWithBid()
                                        .getAdData()
                                        .getRenderUri())
                        .setBuyerDecisionLogicJs(
                                mAdBiddingOutcomeForBuyer1
                                        .getCustomAudienceBiddingInfo()
                                        .getBuyerDecisionLogicJs())
                        // TODO(b/230569187) add contextual signals once supported in the main logic
                        .setBuyerContextualSignals("{}")
                        .setSellerContextualSignals("{}")
                        .setCreationTimestamp(adSelectionCreationTs)
                        .build();

        AdSelectionTestCallback resultsCallback =
                invokeRunAdSelection(mAdSelectionRunner, adSelectionConfig, MY_APP_PACKAGE_NAME);

        verify(mPerBuyerBiddingRunnerMock)
                .runBidding(
                        BUYER_1,
                        ImmutableList.of(mDBCustomAudienceForBuyer1),
                        mFakeFlags.getAdSelectionBiddingTimeoutPerBuyerMs(),
                        adSelectionConfig);
        verify(mPerBuyerBiddingRunnerMock)
                .runBidding(
                        BUYER_2,
                        ImmutableList.of(mDBCustomAudienceForBuyer2),
                        mFakeFlags.getAdSelectionBiddingTimeoutPerBuyerMs(),
                        adSelectionConfig);

        verify(mMockAdsScoreGenerator).runAdScoring(mAdBiddingOutcomeList, adSelectionConfig);

        assertTrue(resultsCallback.mIsSuccess);
        assertEquals(
                expectedAdSelectionResult.getAdSelectionId(),
                resultsCallback.mAdSelectionResponse.getAdSelectionId());
        assertEquals(
                expectedAdSelectionResult.getWinningAdRenderUri(),
                resultsCallback.mAdSelectionResponse.getRenderUri());
        assertTrue(mAdSelectionEntryDaoSpy.doesAdSelectionIdExist(AD_SELECTION_ID));
        assertEquals(
                expectedAdSelectionResult,
                mAdSelectionEntryDaoSpy.getAdSelectionEntityById(AD_SELECTION_ID));
        verifyLogForSuccessfulBiddingProcess(mAdBiddingOutcomeList);
        verifyLogForSuccessfulAdSelectionProcess();
        verifyDebugReportingWasNotCalled();
        verify(mAdServicesLoggerMock)
                .logFledgeApiCallStats(
                        eq(AD_SERVICES_API_CALLED__API_NAME__SELECT_ADS),
                        eq(TEST_PACKAGE_NAME),
                        eq(STATUS_SUCCESS),
                        eq(RUN_AD_SELECTION_OVERALL_LATENCY_MS));
        verify(mAdSelectionServiceFilterMock)
                .filterRequest(
                        SELLER_VALID,
                        MY_APP_PACKAGE_NAME,
                        true,
                        true,
                        false,
                        CALLER_UID,
                        AdServicesStatsLog.AD_SERVICES_API_CALLED__API_NAME__SELECT_ADS,
                        Throttler.ApiKey.FLEDGE_API_SELECT_ADS,
                        DevContext.createForDevOptionsDisabled());
    }

    @Test
    public void testRunAdSelectionSuccessWithShouldUseUnifiedTablesFlag()
            throws AdServicesException {
        AdSelectionConfig adSelectionConfig = mAdSelectionConfigBuilder.build();
        verifyAndSetupCommonSuccessScenario(adSelectionConfig);

        // Init runner with unified tables boolean true
        mAdSelectionRunner =
                new OnDeviceAdSelectionRunner(
                        mCustomAudienceDao,
                        mAdSelectionEntryDaoSpy,
                        mEncryptionKeyDaoMock,
                        mEnrollmentDaoMock,
                        mAdServicesHttpsClient,
                        mLightweightExecutorService,
                        mBackgroundExecutorService,
                        mScheduledExecutor,
                        mMockAdsScoreGenerator,
                        mMockAdSelectionIdGenerator,
                        mClockSpy,
                        mAdServicesLoggerMock,
                        mFakeFlags,
                        mMockDebugFlags,
                        CALLER_UID,
                        mAdSelectionServiceFilterMock,
                        mAdSelectionExecutionLogger,
                        mPerBuyerBiddingRunnerMock,
                        mFrequencyCapAdFilterer,
                        mAdCounterKeyCopier,
                        mAdCounterHistogramUpdater,
                        mFrequencyCapAdDataValidator,
                        mDebugReportingMock,
                        true,
                        mKAnonSignJoinFactoryMock,
                        mAppInstallAdFilterer);
        // Populating the Custom Audience DB
        mCustomAudienceDao.insertOrOverwriteCustomAudience(
                mDBCustomAudienceForBuyer1,
                CustomAudienceFixture.getValidDailyUpdateUriByBuyer(BUYER_1),
                false);
        mCustomAudienceDao.insertOrOverwriteCustomAudience(
                mDBCustomAudienceForBuyer2,
                CustomAudienceFixture.getValidDailyUpdateUriByBuyer(BUYER_2),
                false);
        Instant adSelectionCreationTs = Clock.systemUTC().instant().truncatedTo(ChronoUnit.MILLIS);
        when(mClockSpy.instant()).thenReturn(adSelectionCreationTs);

        DBReportingComputationInfo expectedDBComputationInfo =
                DBReportingComputationInfo.builder()
                        .setAdSelectionId(AD_SELECTION_ID)
                        .setBiddingLogicUri(mDBCustomAudienceForBuyer2.getBiddingLogicUri())
                        .setWinningAdBid(
                                mAdScoringOutcomeForBuyer2.getAdWithScore().getAdWithBid().getBid())
                        .setCustomAudienceSignals(
                                mAdScoringOutcomeForBuyer2.getCustomAudienceSignals())
                        .setWinningAdRenderUri(
                                mAdScoringOutcomeForBuyer2
                                        .getAdWithScore()
                                        .getAdWithBid()
                                        .getAdData()
                                        .getRenderUri())
                        .setBuyerDecisionLogicJs(
                                mAdBiddingOutcomeForBuyer1
                                        .getCustomAudienceBiddingInfo()
                                        .getBuyerDecisionLogicJs())
                        // TODO(b/230569187) add contextual signals once supported in the main logic
                        .setBuyerContextualSignals("{}")
                        .setSellerContextualSignals("{}")
                        .build();

        AdSelectionInitialization expectedAdSelectionInitialization =
                AdSelectionInitialization.builder()
                        .setCreationInstant(adSelectionCreationTs)
                        .setCallerPackageName(MY_APP_PACKAGE_NAME)
                        .setSeller(SELLER_VALID)
                        .build();

        WinningCustomAudience expectedWinningCustomAudience =
                WinningCustomAudience.builder()
                        .setName(mAdScoringOutcomeForBuyer2.getCustomAudienceSignals().getName())
                        .setOwner(mAdScoringOutcomeForBuyer2.getCustomAudienceSignals().getOwner())
                        .build();

        AdSelectionTestCallback resultsCallback =
                invokeRunAdSelection(mAdSelectionRunner, adSelectionConfig, MY_APP_PACKAGE_NAME);

        verify(mPerBuyerBiddingRunnerMock)
                .runBidding(
                        BUYER_1,
                        ImmutableList.of(mDBCustomAudienceForBuyer1),
                        mFakeFlags.getAdSelectionBiddingTimeoutPerBuyerMs(),
                        adSelectionConfig);
        verify(mPerBuyerBiddingRunnerMock)
                .runBidding(
                        BUYER_2,
                        ImmutableList.of(mDBCustomAudienceForBuyer2),
                        mFakeFlags.getAdSelectionBiddingTimeoutPerBuyerMs(),
                        adSelectionConfig);

        verify(mMockAdsScoreGenerator).runAdScoring(mAdBiddingOutcomeList, adSelectionConfig);

        assertTrue(resultsCallback.mIsSuccess);
        assertEquals(
                expectedDBComputationInfo.getAdSelectionId(),
                resultsCallback.mAdSelectionResponse.getAdSelectionId());
        assertEquals(
                expectedDBComputationInfo.getWinningAdRenderUri(),
                resultsCallback.mAdSelectionResponse.getRenderUri());
        assertTrue(mAdSelectionEntryDaoSpy.doesReportingComputationInfoExist(AD_SELECTION_ID));
        verify(mAdSelectionEntryDaoSpy).insertDBReportingComputationInfo(expectedDBComputationInfo);
        verify(mAdSelectionEntryDaoSpy)
                .persistAdSelectionInitialization(
                        AD_SELECTION_ID, expectedAdSelectionInitialization);
        verify(mAdSelectionEntryDaoSpy)
                .persistAdSelectionResultForCustomAudience(anyLong(), any(), any(), any());
        assertEquals(
                expectedDBComputationInfo,
                mAdSelectionEntryDaoSpy.getReportingComputationInfoById((AD_SELECTION_ID)));
        assertEquals(
                expectedAdSelectionInitialization,
                mAdSelectionEntryDaoSpy.getAdSelectionInitializationForId(AD_SELECTION_ID));
        assertEquals(
                expectedWinningCustomAudience,
                mAdSelectionEntryDaoSpy.getWinningCustomAudienceDataForId(AD_SELECTION_ID));

        // Verify old dao methods were not called
        verify(mAdSelectionEntryDaoSpy, never()).persistAdSelection(any());
        verify(mAdSelectionEntryDaoSpy, never()).persistBuyerDecisionLogic(any());
        assertFalse(mAdSelectionEntryDaoSpy.doesAdSelectionIdExist(AD_SELECTION_ID));
        assertNull(mAdSelectionEntryDaoSpy.getAdSelectionEntityById(AD_SELECTION_ID));

        verifyLogForSuccessfulBiddingProcess(mAdBiddingOutcomeList);
        verifyLogForSuccessfulAdSelectionProcess();
        verifyDebugReportingWasNotCalled();
        verify(mAdServicesLoggerMock)
                .logFledgeApiCallStats(
                        eq(AD_SERVICES_API_CALLED__API_NAME__SELECT_ADS),
                        eq(TEST_PACKAGE_NAME),
                        eq(STATUS_SUCCESS),
                        eq(RUN_AD_SELECTION_OVERALL_LATENCY_MS));
    }

    @Test
    public void testRunAdSelectionSuccessWithBuyerContextualSignals() throws AdServicesException {
        AdSelectionConfig adSelectionConfig = mAdSelectionConfigBuilder.build();
        when(mClockSpy.instant()).thenReturn(Clock.systemUTC().instant());
        doReturn(mFakeFlags).when(FlagsFactory::getFlags);
        AdCost adCost1 = new AdCost(1.5, NUM_BITS_STOCHASTIC_ROUNDING);
        BuyerContextualSignals buyerContextualSignals1 =
                BuyerContextualSignals.builder()
                        .setAdCost(adCost1)
                        .setDataVersion(DATA_VERSION_1)
                        .build();

        AdCost adCost2 = new AdCost(3.0, NUM_BITS_STOCHASTIC_ROUNDING);
        BuyerContextualSignals buyerContextualSignals2 =
                BuyerContextualSignals.builder()
                        .setAdCost(adCost2)
                        .setDataVersion(DATA_VERSION_2)
                        .build();

        mAdBiddingOutcomeForBuyer1 =
                AdBiddingOutcomeFixture.anAdBiddingOutcomeBuilderWithBuyerContextualSignals(
                                BUYER_1, 1.0, buyerContextualSignals1)
                        .build();
        mAdBiddingOutcomeForBuyer2 =
                AdBiddingOutcomeFixture.anAdBiddingOutcomeBuilderWithBuyerContextualSignals(
                                BUYER_2, 2.0, buyerContextualSignals2)
                        .build();
        mAdBiddingOutcomeList =
                Arrays.asList(mAdBiddingOutcomeForBuyer1, mAdBiddingOutcomeForBuyer2);

        mAdScoringOutcomeForBuyer1 =
                AdScoringOutcomeFixture.anAdScoringBuilderWithBuyerContextualSignals(
                                BUYER_1, 2.0, buyerContextualSignals1)
                        .build();
        mAdScoringOutcomeForBuyer2 =
                AdScoringOutcomeFixture.anAdScoringBuilderWithBuyerContextualSignals(
                                BUYER_2, 3.0, buyerContextualSignals2)
                        .build();
        mAdScoringOutcomeList =
                Arrays.asList(mAdScoringOutcomeForBuyer1, mAdScoringOutcomeForBuyer2);

        verifyAndSetupCommonSuccessScenario(adSelectionConfig);
        mAdSelectionRunner =
                new OnDeviceAdSelectionRunner(
                        mCustomAudienceDao,
                        mAdSelectionEntryDaoSpy,
                        mEncryptionKeyDaoMock,
                        mEnrollmentDaoMock,
                        mAdServicesHttpsClient,
                        mLightweightExecutorService,
                        mBackgroundExecutorService,
                        mScheduledExecutor,
                        mMockAdsScoreGenerator,
                        mMockAdSelectionIdGenerator,
                        mClockSpy,
                        mAdServicesLoggerMock,
                        mFakeFlags,
                        mMockDebugFlags,
                        CALLER_UID,
                        mAdSelectionServiceFilterMock,
                        mAdSelectionExecutionLogger,
                        mPerBuyerBiddingRunnerMock,
                        mFrequencyCapAdFilterer,
                        mAdCounterKeyCopier,
                        mAdCounterHistogramUpdater,
                        mFrequencyCapAdDataValidator,
                        mDebugReportingMock,
                        false,
                        mKAnonSignJoinFactoryMock,
                        mAppInstallAdFilterer);
        // Populating the Custom Audience DB
        mCustomAudienceDao.insertOrOverwriteCustomAudience(
                mDBCustomAudienceForBuyer1,
                CustomAudienceFixture.getValidDailyUpdateUriByBuyer(BUYER_1),
                false);
        mCustomAudienceDao.insertOrOverwriteCustomAudience(
                mDBCustomAudienceForBuyer2,
                CustomAudienceFixture.getValidDailyUpdateUriByBuyer(BUYER_2),
                false);
        Instant adSelectionCreationTs = Clock.systemUTC().instant().truncatedTo(ChronoUnit.MILLIS);
        when(mClockSpy.instant()).thenReturn(adSelectionCreationTs);
        DBAdSelectionEntry expectedAdSelectionResult =
                new DBAdSelectionEntry.Builder()
                        .setAdSelectionId(AD_SELECTION_ID)
                        .setBiddingLogicUri(mDBCustomAudienceForBuyer2.getBiddingLogicUri())
                        .setWinningAdBid(
                                mAdScoringOutcomeForBuyer2.getAdWithScore().getAdWithBid().getBid())
                        .setCustomAudienceSignals(
                                mAdScoringOutcomeForBuyer2.getCustomAudienceSignals())
                        .setWinningAdRenderUri(
                                mAdScoringOutcomeForBuyer2
                                        .getAdWithScore()
                                        .getAdWithBid()
                                        .getAdData()
                                        .getRenderUri())
                        .setBuyerDecisionLogicJs(
                                mAdBiddingOutcomeForBuyer1
                                        .getCustomAudienceBiddingInfo()
                                        .getBuyerDecisionLogicJs())
                        // TODO(b/230569187) add contextual signals once supported in the main logic
                        .setBuyerContextualSignals(buyerContextualSignals2.toString())
                        .setSellerContextualSignals("{}")
                        .setCreationTimestamp(adSelectionCreationTs)
                        .build();

        AdSelectionTestCallback resultsCallback =
                invokeRunAdSelection(mAdSelectionRunner, adSelectionConfig, MY_APP_PACKAGE_NAME);

        verify(mPerBuyerBiddingRunnerMock)
                .runBidding(
                        BUYER_1,
                        ImmutableList.of(mDBCustomAudienceForBuyer1),
                        mFakeFlags.getAdSelectionBiddingTimeoutPerBuyerMs(),
                        adSelectionConfig);
        verify(mPerBuyerBiddingRunnerMock)
                .runBidding(
                        BUYER_2,
                        ImmutableList.of(mDBCustomAudienceForBuyer2),
                        mFakeFlags.getAdSelectionBiddingTimeoutPerBuyerMs(),
                        adSelectionConfig);

        verify(mMockAdsScoreGenerator).runAdScoring(mAdBiddingOutcomeList, adSelectionConfig);

        assertTrue(resultsCallback.mIsSuccess);
        assertEquals(
                expectedAdSelectionResult.getAdSelectionId(),
                resultsCallback.mAdSelectionResponse.getAdSelectionId());
        assertEquals(
                expectedAdSelectionResult.getWinningAdRenderUri(),
                resultsCallback.mAdSelectionResponse.getRenderUri());
        assertTrue(mAdSelectionEntryDaoSpy.doesAdSelectionIdExist(AD_SELECTION_ID));
        assertEquals(
                expectedAdSelectionResult,
                mAdSelectionEntryDaoSpy.getAdSelectionEntityById(AD_SELECTION_ID));
        verifyLogForSuccessfulBiddingProcess(mAdBiddingOutcomeList);
        verifyLogForSuccessfulAdSelectionProcess();
        verify(mAdServicesLoggerMock)
                .logFledgeApiCallStats(
                        eq(AD_SERVICES_API_CALLED__API_NAME__SELECT_ADS),
                        eq(TEST_PACKAGE_NAME),
                        eq(STATUS_SUCCESS),
                        eq(RUN_AD_SELECTION_OVERALL_LATENCY_MS));
    }

    @Test
    public void testRunAdSelectionSuccessWithSellerDataVersionHeader() throws AdServicesException {
        AdSelectionConfig adSelectionConfig = mAdSelectionConfigBuilder.build();
        when(mClockSpy.instant()).thenReturn(Clock.systemUTC().instant());
        doReturn(mFakeFlags).when(FlagsFactory::getFlags);

        mAdBiddingOutcomeForBuyer1 =
                AdBiddingOutcomeFixture.anAdBiddingOutcomeBuilder(BUYER_1, 1.0).build();
        mAdBiddingOutcomeForBuyer2 =
                AdBiddingOutcomeFixture.anAdBiddingOutcomeBuilder(BUYER_2, 2.0).build();
        mAdBiddingOutcomeList =
                Arrays.asList(mAdBiddingOutcomeForBuyer1, mAdBiddingOutcomeForBuyer2);

        SellerContextualSignals sellerContextualSignals =
                SellerContextualSignals.builder().setDataVersion(DATA_VERSION_1).build();

        mAdScoringOutcomeForBuyer1 =
                AdScoringOutcomeFixture.anAdScoringBuilderWithSellerContextualSignals(
                                BUYER_1, 2.0, sellerContextualSignals)
                        .build();
        mAdScoringOutcomeForBuyer2 =
                AdScoringOutcomeFixture.anAdScoringBuilderWithSellerContextualSignals(
                                BUYER_2, 3.0, sellerContextualSignals)
                        .build();
        mAdScoringOutcomeList =
                Arrays.asList(mAdScoringOutcomeForBuyer1, mAdScoringOutcomeForBuyer2);

        verifyAndSetupCommonSuccessScenario(adSelectionConfig);
        mAdSelectionRunner =
                new OnDeviceAdSelectionRunner(
                        mCustomAudienceDao,
                        mAdSelectionEntryDaoSpy,
                        mEncryptionKeyDaoMock,
                        mEnrollmentDaoMock,
                        mAdServicesHttpsClient,
                        mLightweightExecutorService,
                        mBackgroundExecutorService,
                        mScheduledExecutor,
                        mMockAdsScoreGenerator,
                        mMockAdSelectionIdGenerator,
                        mClockSpy,
                        mAdServicesLoggerMock,
                        mFakeFlags,
                        mMockDebugFlags,
                        CALLER_UID,
                        mAdSelectionServiceFilterMock,
                        mAdSelectionExecutionLogger,
                        mPerBuyerBiddingRunnerMock,
                        mFrequencyCapAdFilterer,
                        mAdCounterKeyCopier,
                        mAdCounterHistogramUpdater,
                        mFrequencyCapAdDataValidator,
                        mDebugReportingMock,
                        false,
                        mKAnonSignJoinFactoryMock,
                        mAppInstallAdFilterer);
        // Populating the Custom Audience DB
        mCustomAudienceDao.insertOrOverwriteCustomAudience(
                mDBCustomAudienceForBuyer1,
                CustomAudienceFixture.getValidDailyUpdateUriByBuyer(BUYER_1),
                false);
        mCustomAudienceDao.insertOrOverwriteCustomAudience(
                mDBCustomAudienceForBuyer2,
                CustomAudienceFixture.getValidDailyUpdateUriByBuyer(BUYER_2),
                false);
        Instant adSelectionCreationTs = Clock.systemUTC().instant().truncatedTo(ChronoUnit.MILLIS);
        when(mClockSpy.instant()).thenReturn(adSelectionCreationTs);
        DBAdSelectionEntry expectedAdSelectionResult =
                new DBAdSelectionEntry.Builder()
                        .setAdSelectionId(AD_SELECTION_ID)
                        .setBiddingLogicUri(mDBCustomAudienceForBuyer2.getBiddingLogicUri())
                        .setWinningAdBid(
                                mAdScoringOutcomeForBuyer2.getAdWithScore().getAdWithBid().getBid())
                        .setCustomAudienceSignals(
                                mAdScoringOutcomeForBuyer2.getCustomAudienceSignals())
                        .setWinningAdRenderUri(
                                mAdScoringOutcomeForBuyer2
                                        .getAdWithScore()
                                        .getAdWithBid()
                                        .getAdData()
                                        .getRenderUri())
                        .setBuyerDecisionLogicJs(
                                mAdBiddingOutcomeForBuyer1
                                        .getCustomAudienceBiddingInfo()
                                        .getBuyerDecisionLogicJs())
                        // TODO(b/230569187) add contextual signals once supported in the main logic
                        .setBuyerContextualSignals("{}")
                        .setSellerContextualSignals(sellerContextualSignals.toString())
                        .setCreationTimestamp(adSelectionCreationTs)
                        .build();

        AdSelectionTestCallback resultsCallback =
                invokeRunAdSelection(mAdSelectionRunner, adSelectionConfig, MY_APP_PACKAGE_NAME);

        verify(mPerBuyerBiddingRunnerMock)
                .runBidding(
                        BUYER_1,
                        ImmutableList.of(mDBCustomAudienceForBuyer1),
                        mFakeFlags.getAdSelectionBiddingTimeoutPerBuyerMs(),
                        adSelectionConfig);
        verify(mPerBuyerBiddingRunnerMock)
                .runBidding(
                        BUYER_2,
                        ImmutableList.of(mDBCustomAudienceForBuyer2),
                        mFakeFlags.getAdSelectionBiddingTimeoutPerBuyerMs(),
                        adSelectionConfig);

        verify(mMockAdsScoreGenerator).runAdScoring(mAdBiddingOutcomeList, adSelectionConfig);

        assertTrue(resultsCallback.mIsSuccess);
        assertEquals(
                expectedAdSelectionResult.getAdSelectionId(),
                resultsCallback.mAdSelectionResponse.getAdSelectionId());
        assertEquals(
                expectedAdSelectionResult.getWinningAdRenderUri(),
                resultsCallback.mAdSelectionResponse.getRenderUri());
        assertTrue(mAdSelectionEntryDaoSpy.doesAdSelectionIdExist(AD_SELECTION_ID));
        assertEquals(
                expectedAdSelectionResult,
                mAdSelectionEntryDaoSpy.getAdSelectionEntityById(AD_SELECTION_ID));
        verifyLogForSuccessfulBiddingProcess(mAdBiddingOutcomeList);
        verifyLogForSuccessfulAdSelectionProcess();
        verify(mAdServicesLoggerMock)
                .logFledgeApiCallStats(
                        eq(AD_SERVICES_API_CALLED__API_NAME__SELECT_ADS),
                        eq(TEST_PACKAGE_NAME),
                        eq(STATUS_SUCCESS),
                        eq(RUN_AD_SELECTION_OVERALL_LATENCY_MS));
    }

    @Test
    public void testRunAdSelectionSuccessFilteringDisabled() throws AdServicesException {
        AdSelectionConfig adSelectionConfig = mAdSelectionConfigBuilder.build();
        when(mClockSpy.instant()).thenReturn(Clock.systemUTC().instant());
        Flags flagsWithFilteringDisabled =
                new OnDeviceAdSelectionRunnerTestFlags() {
                    @Override
                    public long getAdSelectionOverallTimeoutMs() {
                        return 300;
                    }

                    @Override
                    public boolean getDisableFledgeEnrollmentCheck() {
                        return true;
                    }
                };
        doReturn(flagsWithFilteringDisabled).when(FlagsFactory::getFlags);

        DBCustomAudience dbCustomAudienceNoFilterBuyer1 = createDBCustomAudienceNoFilters(BUYER_1);
        DBCustomAudience dbCustomAudienceNoFilterBuyer2 = createDBCustomAudienceNoFilters(BUYER_2);

        // Getting BiddingOutcome-forBuyerX corresponding to each CA-forBuyerX
        doReturn(ImmutableList.of(Futures.immediateFuture(mAdBiddingOutcomeForBuyer1)))
                .when(mPerBuyerBiddingRunnerMock)
                .runBidding(
                        BUYER_1,
                        Collections.singletonList(dbCustomAudienceNoFilterBuyer1),
                        flagsWithFilteringDisabled.getAdSelectionBiddingTimeoutPerBuyerMs(),
                        adSelectionConfig);
        doReturn(ImmutableList.of(Futures.immediateFuture(mAdBiddingOutcomeForBuyer2)))
                .when(mPerBuyerBiddingRunnerMock)
                .runBidding(
                        BUYER_2,
                        Collections.singletonList(dbCustomAudienceNoFilterBuyer2),
                        flagsWithFilteringDisabled.getAdSelectionBiddingTimeoutPerBuyerMs(),
                        adSelectionConfig);

        // Getting ScoringOutcome-ForBuyerX corresponding to each BiddingOutcome-forBuyerX
        when(mMockAdsScoreGenerator.runAdScoring(mAdBiddingOutcomeList, adSelectionConfig))
                .thenReturn((FluentFuture.from(Futures.immediateFuture(mAdScoringOutcomeList))));

        setAdSelectionExecutionLoggerMockWithSuccessAdSelection();

        when(mMockAdSelectionIdGenerator.generateId()).thenReturn(AD_SELECTION_ID);
        assertFalse(mAdSelectionEntryDaoSpy.doesAdSelectionIdExist(AD_SELECTION_ID));
        mAdSelectionRunner =
                new OnDeviceAdSelectionRunner(
                        mCustomAudienceDao,
                        mAdSelectionEntryDaoSpy,
                        mEncryptionKeyDaoMock,
                        mEnrollmentDaoMock,
                        mAdServicesHttpsClient,
                        mLightweightExecutorService,
                        mBackgroundExecutorService,
                        mScheduledExecutor,
                        mMockAdsScoreGenerator,
                        mMockAdSelectionIdGenerator,
                        mClockSpy,
                        mAdServicesLoggerMock,
                        flagsWithFilteringDisabled,
                        mMockDebugFlags,
                        CALLER_UID,
                        mAdSelectionServiceFilterMock,
                        mAdSelectionExecutionLogger,
                        mPerBuyerBiddingRunnerMock,
                        new FrequencyCapAdFiltererNoOpImpl(),
                        new AdCounterKeyCopierNoOpImpl(),
                        mAdCounterHistogramUpdater,
                        mFrequencyCapAdDataValidator,
                        mDebugReportingMock,
                        false,
                        mKAnonSignJoinFactoryMock,
                        mAppInstallAdFilterer);
        // Populating the Custom Audience DB
        mCustomAudienceDao.insertOrOverwriteCustomAudience(
                dbCustomAudienceNoFilterBuyer1,
                CustomAudienceFixture.getValidDailyUpdateUriByBuyer(BUYER_1),
                false);
        mCustomAudienceDao.insertOrOverwriteCustomAudience(
                dbCustomAudienceNoFilterBuyer2,
                CustomAudienceFixture.getValidDailyUpdateUriByBuyer(BUYER_2),
                false);
        Instant adSelectionCreationTs = Clock.systemUTC().instant().truncatedTo(ChronoUnit.MILLIS);
        when(mClockSpy.instant()).thenReturn(adSelectionCreationTs);
        DBAdSelectionEntry expectedAdSelectionResult =
                new DBAdSelectionEntry.Builder()
                        .setAdSelectionId(AD_SELECTION_ID)
                        .setBiddingLogicUri(dbCustomAudienceNoFilterBuyer2.getBiddingLogicUri())
                        .setWinningAdBid(
                                mAdScoringOutcomeForBuyer2.getAdWithScore().getAdWithBid().getBid())
                        .setCustomAudienceSignals(
                                mAdScoringOutcomeForBuyer2.getCustomAudienceSignals())
                        .setWinningAdRenderUri(
                                mAdScoringOutcomeForBuyer2
                                        .getAdWithScore()
                                        .getAdWithBid()
                                        .getAdData()
                                        .getRenderUri())
                        .setBuyerDecisionLogicJs(
                                mAdBiddingOutcomeForBuyer1
                                        .getCustomAudienceBiddingInfo()
                                        .getBuyerDecisionLogicJs())
                        // TODO(b/230569187) add contextual signals once supported in the main logic
                        .setBuyerContextualSignals("{}")
                        .setSellerContextualSignals("{}")
                        .setCreationTimestamp(adSelectionCreationTs)
                        .build();

        AdSelectionTestCallback resultsCallback =
                invokeRunAdSelection(mAdSelectionRunner, adSelectionConfig, MY_APP_PACKAGE_NAME);

        verify(mPerBuyerBiddingRunnerMock)
                .runBidding(
                        BUYER_1,
                        Collections.singletonList(dbCustomAudienceNoFilterBuyer1),
                        flagsWithFilteringDisabled.getAdSelectionBiddingTimeoutPerBuyerMs(),
                        adSelectionConfig);
        verify(mPerBuyerBiddingRunnerMock)
                .runBidding(
                        BUYER_2,
                        Collections.singletonList(dbCustomAudienceNoFilterBuyer2),
                        flagsWithFilteringDisabled.getAdSelectionBiddingTimeoutPerBuyerMs(),
                        adSelectionConfig);

        verify(mMockAdsScoreGenerator).runAdScoring(mAdBiddingOutcomeList, adSelectionConfig);

        assertTrue(resultsCallback.mIsSuccess);
        assertEquals(
                expectedAdSelectionResult.getAdSelectionId(),
                resultsCallback.mAdSelectionResponse.getAdSelectionId());
        assertEquals(
                expectedAdSelectionResult.getWinningAdRenderUri(),
                resultsCallback.mAdSelectionResponse.getRenderUri());
        assertTrue(mAdSelectionEntryDaoSpy.doesAdSelectionIdExist(AD_SELECTION_ID));
        assertEquals(
                expectedAdSelectionResult,
                mAdSelectionEntryDaoSpy.getAdSelectionEntityById(AD_SELECTION_ID));
        DBAdSelectionHistogramInfo histogramInfo =
                mAdSelectionEntryDaoSpy.getAdSelectionHistogramInfoInOnDeviceTable(
                        resultsCallback.mAdSelectionResponse.getAdSelectionId(),
                        MY_APP_PACKAGE_NAME);
        assertThat(histogramInfo).isNotNull();
        assertThat(histogramInfo.getAdCounterKeys()).isNull();
        verifyLogForSuccessfulBiddingProcess(mAdBiddingOutcomeList);
        verifyLogForSuccessfulAdSelectionProcess();
        verifyDebugReportingWasNotCalled();
        verify(mAdServicesLoggerMock)
                .logFledgeApiCallStats(
                        eq(AD_SERVICES_API_CALLED__API_NAME__SELECT_ADS),
                        eq(TEST_PACKAGE_NAME),
                        eq(STATUS_SUCCESS),
                        eq(RUN_AD_SELECTION_OVERALL_LATENCY_MS));
    }

    @Test
    public void testRunAdSelectionRetriesAdSelectionIdGenerationAfterCollision()
            throws AdServicesException {
        when(mClockSpy.instant()).thenReturn(Clock.systemUTC().instant());
        doReturn(mFakeFlags).when(FlagsFactory::getFlags);
        AdSelectionConfig adSelectionConfig = mAdSelectionConfigBuilder.build();

        long existingAdSelectionId = 2345L;

        // Populating the Custom Audience DB
        mCustomAudienceDao.insertOrOverwriteCustomAudience(
                mDBCustomAudienceForBuyer1,
                CustomAudienceFixture.getValidDailyUpdateUriByBuyer(BUYER_1),
                false);
        mCustomAudienceDao.insertOrOverwriteCustomAudience(
                mDBCustomAudienceForBuyer2,
                CustomAudienceFixture.getValidDailyUpdateUriByBuyer(BUYER_2),
                false);

        // Getting BiddingOutcome-forBuyerX corresponding to each CA-forBuyerX
        doReturn(ImmutableList.of(Futures.immediateFuture(mAdBiddingOutcomeForBuyer1)))
                .when(mPerBuyerBiddingRunnerMock)
                .runBidding(
                        BUYER_1,
                        ImmutableList.of(mDBCustomAudienceForBuyer1),
                        mFakeFlags.getAdSelectionBiddingTimeoutPerBuyerMs(),
                        adSelectionConfig);
        doReturn(ImmutableList.of(Futures.immediateFuture(mAdBiddingOutcomeForBuyer2)))
                .when(mPerBuyerBiddingRunnerMock)
                .runBidding(
                        BUYER_2,
                        ImmutableList.of(mDBCustomAudienceForBuyer2),
                        mFakeFlags.getAdSelectionBiddingTimeoutPerBuyerMs(),
                        adSelectionConfig);

        // Getting ScoringOutcome-ForBuyerX corresponding to each BiddingOutcome-forBuyerX
        when(mMockAdsScoreGenerator.runAdScoring(mAdBiddingOutcomeList, adSelectionConfig))
                .thenReturn((FluentFuture.from(Futures.immediateFuture(mAdScoringOutcomeList))));

        Instant adSelectionCreationTs = Clock.systemUTC().instant().truncatedTo(ChronoUnit.MILLIS);
        when(mClockSpy.instant()).thenReturn(adSelectionCreationTs);

        DBAdSelectionEntry expectedAdSelectionResult =
                new DBAdSelectionEntry.Builder()
                        .setAdSelectionId(AD_SELECTION_ID)
                        .setBiddingLogicUri(mDBCustomAudienceForBuyer2.getBiddingLogicUri())
                        .setWinningAdBid(
                                mAdScoringOutcomeForBuyer2.getAdWithScore().getAdWithBid().getBid())
                        .setCustomAudienceSignals(
                                mAdScoringOutcomeForBuyer2.getCustomAudienceSignals())
                        .setWinningAdRenderUri(
                                mAdScoringOutcomeForBuyer2
                                        .getAdWithScore()
                                        .getAdWithBid()
                                        .getAdData()
                                        .getRenderUri())
                        .setBuyerDecisionLogicJs(
                                mAdBiddingOutcomeForBuyer1
                                        .getCustomAudienceBiddingInfo()
                                        .getBuyerDecisionLogicJs())
                        // TODO(b/230569187) add contextual signals once supported in the main logic
                        .setBuyerContextualSignals("{}")
                        .setSellerContextualSignals("{}")
                        .setCreationTimestamp(adSelectionCreationTs)
                        .build();

        DBAdSelection existingAdSelection =
                new DBAdSelection.Builder()
                        .setAdSelectionId(existingAdSelectionId)
                        .setWinningAdBid(
                                mAdScoringOutcomeForBuyer2.getAdWithScore().getAdWithBid().getBid())
                        .setCustomAudienceSignals(
                                mAdScoringOutcomeForBuyer2.getCustomAudienceSignals())
                        .setWinningAdRenderUri(
                                mAdScoringOutcomeForBuyer2
                                        .getAdWithScore()
                                        .getAdWithBid()
                                        .getAdData()
                                        .getRenderUri())
                        // TODO(b/230569187) add contextual signals once supported in the main logic
                        .setBuyerContextualSignals("{}")
                        .setSellerContextualSignals("{}")
                        .setCreationTimestamp(adSelectionCreationTs)
                        .setCallerPackageName(MY_APP_PACKAGE_NAME)
                        .setBiddingLogicUri(mAdScoringOutcomeForBuyer2.getBiddingLogicUri())
                        .build();

        // Persist existing ad selection entry with existingAdSelectionId
        mAdSelectionEntryDaoSpy.persistAdSelection(existingAdSelection);

        setAdSelectionExecutionLoggerMockWithSuccessAdSelection();

        // Mock generator to return a collision on the first generation
        when(mMockAdSelectionIdGenerator.generateId())
                .thenReturn(existingAdSelectionId, existingAdSelectionId, AD_SELECTION_ID);

        mAdSelectionRunner =
                new OnDeviceAdSelectionRunner(
                        mCustomAudienceDao,
                        mAdSelectionEntryDaoSpy,
                        mEncryptionKeyDaoMock,
                        mEnrollmentDaoMock,
                        mAdServicesHttpsClient,
                        mLightweightExecutorService,
                        mBackgroundExecutorService,
                        mScheduledExecutor,
                        mMockAdsScoreGenerator,
                        mMockAdSelectionIdGenerator,
                        mClockSpy,
                        mAdServicesLoggerMock,
                        mFakeFlags,
                        mMockDebugFlags,
                        CALLER_UID,
                        mAdSelectionServiceFilterMock,
                        mAdSelectionExecutionLogger,
                        mPerBuyerBiddingRunnerMock,
                        mFrequencyCapAdFilterer,
                        mAdCounterKeyCopier,
                        mAdCounterHistogramUpdater,
                        mFrequencyCapAdDataValidator,
                        mDebugReportingMock,
                        false,
                        mKAnonSignJoinFactoryMock,
                        mAppInstallAdFilterer);

        assertFalse(mAdSelectionEntryDaoSpy.doesAdSelectionIdExist(AD_SELECTION_ID));
        assertTrue(mAdSelectionEntryDaoSpy.doesAdSelectionIdExist(existingAdSelectionId));

        AdSelectionTestCallback resultsCallback =
                invokeRunAdSelection(mAdSelectionRunner, adSelectionConfig, MY_APP_PACKAGE_NAME);

        verify(mPerBuyerBiddingRunnerMock)
                .runBidding(
                        BUYER_1,
                        ImmutableList.of(mDBCustomAudienceForBuyer1),
                        mFakeFlags.getAdSelectionBiddingTimeoutPerBuyerMs(),
                        adSelectionConfig);
        verify(mPerBuyerBiddingRunnerMock)
                .runBidding(
                        BUYER_2,
                        ImmutableList.of(mDBCustomAudienceForBuyer2),
                        mFakeFlags.getAdSelectionBiddingTimeoutPerBuyerMs(),
                        adSelectionConfig);

        verify(mMockAdsScoreGenerator).runAdScoring(mAdBiddingOutcomeList, adSelectionConfig);

        assertTrue(resultsCallback.mIsSuccess);
        assertEquals(
                expectedAdSelectionResult.getAdSelectionId(),
                resultsCallback.mAdSelectionResponse.getAdSelectionId());
        assertEquals(
                expectedAdSelectionResult.getWinningAdRenderUri(),
                resultsCallback.mAdSelectionResponse.getRenderUri());
        assertTrue(mAdSelectionEntryDaoSpy.doesAdSelectionIdExist(AD_SELECTION_ID));
        assertTrue(mAdSelectionEntryDaoSpy.doesAdSelectionIdExist(existingAdSelectionId));
        assertEquals(
                expectedAdSelectionResult,
                mAdSelectionEntryDaoSpy.getAdSelectionEntityById(AD_SELECTION_ID));
        verifyLogForSuccessfulBiddingProcess(mAdBiddingOutcomeList);
        verifyLogForSuccessfulAdSelectionProcess();
        verifyDebugReportingWasNotCalled();
        verify(mAdServicesLoggerMock)
                .logFledgeApiCallStats(
                        eq(AD_SERVICES_API_CALLED__API_NAME__SELECT_ADS),
                        eq(TEST_PACKAGE_NAME),
                        eq(STATUS_SUCCESS),
                        eq(RUN_AD_SELECTION_OVERALL_LATENCY_MS));
    }

    @Test
    public void testRunAdSelectionWithRevokedUserConsentSuccess() throws AdServicesException {
        doReturn(mFakeFlags).when(FlagsFactory::getFlags);
        doThrow(new FilterException(new ConsentManager.RevokedConsentException()))
                .when(mAdSelectionServiceFilterMock)
                .filterRequest(
                        SELLER_VALID,
                        MY_APP_PACKAGE_NAME,
                        true,
                        true,
                        true,
                        CALLER_UID,
                        AdServicesStatsLog.AD_SERVICES_API_CALLED__API_NAME__SELECT_ADS,
                        Throttler.ApiKey.FLEDGE_API_SELECT_ADS,
                        DevContext.createForDevOptionsDisabled());

        // Creating ad selection config for happy case with all the buyers in place
        AdSelectionConfig adSelectionConfig = mAdSelectionConfigBuilder.build();

        // Populating the Custom Audience DB
        mCustomAudienceDao.insertOrOverwriteCustomAudience(
                mDBCustomAudienceForBuyer1,
                CustomAudienceFixture.getValidDailyUpdateUriByBuyer(BUYER_1),
                false);
        mCustomAudienceDao.insertOrOverwriteCustomAudience(
                mDBCustomAudienceForBuyer2,
                CustomAudienceFixture.getValidDailyUpdateUriByBuyer(BUYER_2),
                false);

        when(mMockAdSelectionIdGenerator.generateId()).thenReturn(AD_SELECTION_ID);

        setAdSelectionExecutionLoggerMockWithFailedAdSelectionByValidateRequest();

        mAdSelectionRunner =
                new OnDeviceAdSelectionRunner(
                        mCustomAudienceDao,
                        mAdSelectionEntryDaoSpy,
                        mEncryptionKeyDaoMock,
                        mEnrollmentDaoMock,
                        mAdServicesHttpsClient,
                        mLightweightExecutorService,
                        mBackgroundExecutorService,
                        mScheduledExecutor,
                        mMockAdsScoreGenerator,
                        mMockAdSelectionIdGenerator,
                        mClockSpy,
                        mAdServicesLoggerMock,
                        mFakeFlags,
                        mMockDebugFlags,
                        CALLER_UID,
                        mAdSelectionServiceFilterMock,
                        mAdSelectionExecutionLogger,
                        mPerBuyerBiddingRunnerMock,
                        mFrequencyCapAdFilterer,
                        mAdCounterKeyCopier,
                        mAdCounterHistogramUpdater,
                        mFrequencyCapAdDataValidator,
                        mDebugReportingMock,
                        false,
                        mKAnonSignJoinFactoryMock,
                        mAppInstallAdFilterer);

        assertFalse(mAdSelectionEntryDaoSpy.doesAdSelectionIdExist(AD_SELECTION_ID));

        AdSelectionTestCallback resultsCallback =
                invokeRunAdSelection(mAdSelectionRunner, adSelectionConfig, MY_APP_PACKAGE_NAME);

        verify(mPerBuyerBiddingRunnerMock, never()).runBidding(any(), any(), anyLong(), any());
        verify(mMockAdsScoreGenerator, never()).runAdScoring(any(), any());

        assertTrue(resultsCallback.mIsSuccess);
        assertFalse(
                mAdSelectionEntryDaoSpy.doesAdSelectionIdExist(
                        resultsCallback.mAdSelectionResponse.getAdSelectionId()));
        assertEquals(Uri.EMPTY, resultsCallback.mAdSelectionResponse.getRenderUri());
        assertFalse(mAdSelectionEntryDaoSpy.doesAdSelectionIdExist(AD_SELECTION_ID));

        verifyLogForFailurePriorPersistAdSelection(STATUS_USER_CONSENT_REVOKED);

        // Confirm a duplicate log entry does not exist.
        // AdSelectionServiceFilter ensures the failing assertion is logged internally.
        verify(mAdServicesLoggerMock, never())
                .logFledgeApiCallStats(
                        eq(AD_SERVICES_API_CALLED__API_NAME__SELECT_ADS),
                        eq(MY_APP_PACKAGE_NAME),
                        eq(STATUS_USER_CONSENT_REVOKED),
                        eq(RUN_AD_SELECTION_OVERALL_LATENCY_MS));
    }

    @Test
    public void testRunAdSelectionMissingBuyerSignals() throws AdServicesException {
        when(mClockSpy.instant()).thenReturn(Clock.systemUTC().instant());
        doReturn(mFakeFlags).when(FlagsFactory::getFlags);

        // Creating ad selection config with missing Buyer signals to test the fallback
        AdSelectionConfig adSelectionConfig =
                mAdSelectionConfigBuilder.setPerBuyerSignals(Collections.EMPTY_MAP).build();

        // Populating the Custom Audience DB
        mCustomAudienceDao.insertOrOverwriteCustomAudience(
                mDBCustomAudienceForBuyer1,
                CustomAudienceFixture.getValidDailyUpdateUriByBuyer(BUYER_1),
                false);
        mCustomAudienceDao.insertOrOverwriteCustomAudience(
                mDBCustomAudienceForBuyer2,
                CustomAudienceFixture.getValidDailyUpdateUriByBuyer(BUYER_2),
                false);

        // Getting BiddingOutcome-forBuyerX corresponding to each CA-forBuyerX
        doReturn(ImmutableList.of(Futures.immediateFuture(mAdBiddingOutcomeForBuyer1)))
                .when(mPerBuyerBiddingRunnerMock)
                .runBidding(
                        BUYER_1,
                        ImmutableList.of(mDBCustomAudienceForBuyer1),
                        mFakeFlags.getAdSelectionBiddingTimeoutPerBuyerMs(),
                        adSelectionConfig);
        doReturn(ImmutableList.of(Futures.immediateFuture(mAdBiddingOutcomeForBuyer2)))
                .when(mPerBuyerBiddingRunnerMock)
                .runBidding(
                        BUYER_2,
                        ImmutableList.of(mDBCustomAudienceForBuyer2),
                        mFakeFlags.getAdSelectionBiddingTimeoutPerBuyerMs(),
                        adSelectionConfig);

        // Getting ScoringOutcome-ForBuyerX corresponding to each BiddingOutcome-forBuyerX
        when(mMockAdsScoreGenerator.runAdScoring(mAdBiddingOutcomeList, adSelectionConfig))
                .thenReturn((FluentFuture.from(Futures.immediateFuture(mAdScoringOutcomeList))));

        DBAdSelection expectedDBAdSelectionResult =
                new DBAdSelection.Builder()
                        .setAdSelectionId(AD_SELECTION_ID)
                        .setCreationTimestamp(Calendar.getInstance().toInstant())
                        .setWinningAdBid(
                                mAdScoringOutcomeForBuyer2.getAdWithScore().getAdWithBid().getBid())
                        .setCustomAudienceSignals(
                                mAdScoringOutcomeForBuyer2.getCustomAudienceSignals())
                        .setWinningAdRenderUri(
                                mAdScoringOutcomeForBuyer2
                                        .getAdWithScore()
                                        .getAdWithBid()
                                        .getAdData()
                                        .getRenderUri())
                        .setBiddingLogicUri(mAdScoringOutcomeForBuyer2.getBiddingLogicUri())
                        .setBuyerContextualSignals("{}")
                        .setSellerContextualSignals("{}")
                        .setCallerPackageName(MY_APP_PACKAGE_NAME)
                        .build();

        when(mMockAdSelectionIdGenerator.generateId()).thenReturn(AD_SELECTION_ID);

        setAdSelectionExecutionLoggerMockWithSuccessAdSelection();

        mAdSelectionRunner =
                new OnDeviceAdSelectionRunner(
                        mCustomAudienceDao,
                        mAdSelectionEntryDaoSpy,
                        mEncryptionKeyDaoMock,
                        mEnrollmentDaoMock,
                        mAdServicesHttpsClient,
                        mLightweightExecutorService,
                        mBackgroundExecutorService,
                        mScheduledExecutor,
                        mMockAdsScoreGenerator,
                        mMockAdSelectionIdGenerator,
                        mClockSpy,
                        mAdServicesLoggerMock,
                        mFakeFlags,
                        mMockDebugFlags,
                        CALLER_UID,
                        mAdSelectionServiceFilterMock,
                        mAdSelectionExecutionLogger,
                        mPerBuyerBiddingRunnerMock,
                        mFrequencyCapAdFilterer,
                        mAdCounterKeyCopier,
                        mAdCounterHistogramUpdater,
                        mFrequencyCapAdDataValidator,
                        mDebugReportingMock,
                        false,
                        mKAnonSignJoinFactoryMock,
                        mAppInstallAdFilterer);

        assertFalse(mAdSelectionEntryDaoSpy.doesAdSelectionIdExist(AD_SELECTION_ID));

        AdSelectionTestCallback resultsCallback =
                invokeRunAdSelection(mAdSelectionRunner, adSelectionConfig, MY_APP_PACKAGE_NAME);

        verify(mPerBuyerBiddingRunnerMock)
                .runBidding(
                        BUYER_1,
                        ImmutableList.of(mDBCustomAudienceForBuyer1),
                        mFakeFlags.getAdSelectionBiddingTimeoutPerBuyerMs(),
                        adSelectionConfig);
        verify(mPerBuyerBiddingRunnerMock)
                .runBidding(
                        BUYER_2,
                        ImmutableList.of(mDBCustomAudienceForBuyer2),
                        mFakeFlags.getAdSelectionBiddingTimeoutPerBuyerMs(),
                        adSelectionConfig);
        verify(mMockAdsScoreGenerator).runAdScoring(mAdBiddingOutcomeList, adSelectionConfig);

        assertTrue(resultsCallback.mIsSuccess);
        assertEquals(
                expectedDBAdSelectionResult.getAdSelectionId(),
                resultsCallback.mAdSelectionResponse.getAdSelectionId());
        assertEquals(
                expectedDBAdSelectionResult.getWinningAdRenderUri(),
                resultsCallback.mAdSelectionResponse.getRenderUri());
        assertTrue(mAdSelectionEntryDaoSpy.doesAdSelectionIdExist(AD_SELECTION_ID));

        verifyLogForSuccessfulBiddingProcess(mAdBiddingOutcomeList);
        verifyLogForSuccessfulAdSelectionProcess();
        verifyDebugReportingWasNotCalled();

        verify(mAdServicesLoggerMock)
                .logFledgeApiCallStats(
                        eq(AD_SERVICES_API_CALLED__API_NAME__SELECT_ADS),
                        eq(TEST_PACKAGE_NAME),
                        eq(STATUS_SUCCESS),
                        eq(RUN_AD_SELECTION_OVERALL_LATENCY_MS));
    }

    @Test
    public void testRunAdSelectionNoCAs() {
        when(mClockSpy.instant()).thenReturn(Clock.systemUTC().instant());

        // Creating ad selection config for happy case with all the buyers in place
        AdSelectionConfig adSelectionConfig = mAdSelectionConfigBuilder.build();

        // Do not populate CustomAudience DAO

        setAdSelectionExecutionLoggerMockWithFailedAdSelectionByNoCAs();

        mAdSelectionRunner =
                new OnDeviceAdSelectionRunner(
                        mCustomAudienceDao,
                        mAdSelectionEntryDaoSpy,
                        mEncryptionKeyDaoMock,
                        mEnrollmentDaoMock,
                        mAdServicesHttpsClient,
                        mLightweightExecutorService,
                        mBackgroundExecutorService,
                        mScheduledExecutor,
                        mMockAdsScoreGenerator,
                        mMockAdSelectionIdGenerator,
                        mClockSpy,
                        mAdServicesLoggerMock,
                        mFakeFlags,
                        mMockDebugFlags,
                        CALLER_UID,
                        mAdSelectionServiceFilterMock,
                        mAdSelectionExecutionLogger,
                        mPerBuyerBiddingRunnerMock,
                        mFrequencyCapAdFilterer,
                        mAdCounterKeyCopier,
                        mAdCounterHistogramUpdater,
                        mFrequencyCapAdDataValidator,
                        mDebugReportingMock,
                        false,
                        mKAnonSignJoinFactoryMock,
                        mAppInstallAdFilterer);
        AdSelectionTestCallback resultsCallback =
                invokeRunAdSelection(mAdSelectionRunner, adSelectionConfig, MY_APP_PACKAGE_NAME);

        assertFalse(resultsCallback.mIsSuccess);
        verifyErrorMessageIsCorrect(
                resultsCallback.mFledgeErrorResponse.getErrorMessage(),
                ERROR_NO_CA_AND_CONTEXTUAL_ADS_AVAILABLE);

        verifyLogForFailedBiddingStageDuringFetchBuyersCustomAudience(STATUS_INTERNAL_ERROR);
        verifyLogForFailurePriorPersistAdSelection(STATUS_INTERNAL_ERROR);

        // If there are no corresponding CAs we should not even attempt bidding
        verifyZeroInteractions(mPerBuyerBiddingRunnerMock);
        // If there was no bidding then we should not even attempt to run scoring
        verifyZeroInteractions(mMockAdsScoreGenerator);

        verify(mAdServicesLoggerMock)
                .logFledgeApiCallStats(
                        eq(AD_SERVICES_API_CALLED__API_NAME__SELECT_ADS),
                        eq(MY_APP_PACKAGE_NAME),
                        eq(STATUS_INTERNAL_ERROR),
                        eq(RUN_AD_SELECTION_OVERALL_LATENCY_MS));
    }

    @Test
    public void testRunAdSelectionCallerNotInForeground_fails() {
        // Creating ad selection config for happy case with all the buyers in place
        AdSelectionConfig adSelectionConfig = mAdSelectionConfigBuilder.build();

        doThrow(
                        new FilterException(
                                new AppImportanceFilter.WrongCallingApplicationStateException()))
                .when(mAdSelectionServiceFilterMock)
                .filterRequest(
                        SELLER_VALID,
                        MY_APP_PACKAGE_NAME,
                        true,
                        true,
                        true,
                        CALLER_UID,
                        AdServicesStatsLog.AD_SERVICES_API_CALLED__API_NAME__SELECT_ADS,
                        Throttler.ApiKey.FLEDGE_API_SELECT_ADS,
                        DevContext.createForDevOptionsDisabled());
        setAdSelectionExecutionLoggerMockWithFailedAdSelectionByValidateRequest();
        mAdSelectionRunner =
                new OnDeviceAdSelectionRunner(
                        mCustomAudienceDao,
                        mAdSelectionEntryDaoSpy,
                        mEncryptionKeyDaoMock,
                        mEnrollmentDaoMock,
                        mAdServicesHttpsClient,
                        mLightweightExecutorService,
                        mBackgroundExecutorService,
                        mScheduledExecutor,
                        mMockAdsScoreGenerator,
                        mMockAdSelectionIdGenerator,
                        mClockSpy,
                        mAdServicesLoggerMock,
                        mFakeFlags,
                        mMockDebugFlags,
                        CALLER_UID,
                        mAdSelectionServiceFilterMock,
                        mAdSelectionExecutionLogger,
                        mPerBuyerBiddingRunnerMock,
                        mFrequencyCapAdFilterer,
                        mAdCounterKeyCopier,
                        mAdCounterHistogramUpdater,
                        mFrequencyCapAdDataValidator,
                        mDebugReportingMock,
                        false,
                        mKAnonSignJoinFactoryMock,
                        mAppInstallAdFilterer);
        AdSelectionTestCallback resultsCallback =
                invokeRunAdSelection(mAdSelectionRunner, adSelectionConfig, MY_APP_PACKAGE_NAME);

        assertFalse(resultsCallback.mIsSuccess);
        verifyErrorMessageIsCorrect(
                resultsCallback.mFledgeErrorResponse.getErrorMessage(),
                ILLEGAL_STATE_BACKGROUND_CALLER_ERROR_MESSAGE);

        verifyLogForFailurePriorPersistAdSelection(STATUS_BACKGROUND_CALLER);

        // Confirm a duplicate log entry does not exist.
        // AdSelectionServiceFilter ensures the failing assertion is logged internally.
        verify(mAdServicesLoggerMock, never())
                .logFledgeApiCallStats(
                        eq(AD_SERVICES_API_CALLED__API_NAME__SELECT_ADS),
                        eq(MY_APP_PACKAGE_NAME),
                        eq(STATUS_BACKGROUND_CALLER),
                        eq(RUN_AD_SELECTION_OVERALL_LATENCY_MS));
    }

    @Test
    public void testRunAdSelectionCallerNotInForegroundFlagDisabled_doesNotFailValidation() {
        Flags flags =
                new OnDeviceAdSelectionRunnerTestFlags() {
                    @Override
                    public boolean getEnforceForegroundStatusForFledgeRunAdSelection() {
                        return false;
                    }

                    @Override
                    public boolean getDisableFledgeEnrollmentCheck() {
                        return true;
                    }
                };

        // Creating ad selection config for happy case with all the buyers in place
        AdSelectionConfig adSelectionConfig = mAdSelectionConfigBuilder.build();

        setAdSelectionExecutionLoggerMockWithFailedAdSelectionByNoCAs();

        mAdSelectionRunner =
                new OnDeviceAdSelectionRunner(
                        mCustomAudienceDao,
                        mAdSelectionEntryDaoSpy,
                        mEncryptionKeyDaoMock,
                        mEnrollmentDaoMock,
                        mAdServicesHttpsClient,
                        mLightweightExecutorService,
                        mBackgroundExecutorService,
                        mScheduledExecutor,
                        mMockAdsScoreGenerator,
                        mMockAdSelectionIdGenerator,
                        mClockSpy,
                        mAdServicesLoggerMock,
                        flags,
                        mMockDebugFlags,
                        CALLER_UID,
                        mAdSelectionServiceFilterMock,
                        mAdSelectionExecutionLogger,
                        mPerBuyerBiddingRunnerMock,
                        mFrequencyCapAdFilterer,
                        mAdCounterKeyCopier,
                        mAdCounterHistogramUpdater,
                        mFrequencyCapAdDataValidator,
                        mDebugReportingMock,
                        false,
                        mKAnonSignJoinFactoryMock,
                        mAppInstallAdFilterer);
        AdSelectionTestCallback resultsCallback =
                invokeRunAdSelection(mAdSelectionRunner, adSelectionConfig, MY_APP_PACKAGE_NAME);
        // This ad selection fails because there are no CAs but the foreground status validation
        // is not blocking the rest of the process
        assertFalse(resultsCallback.mIsSuccess);
        verifyErrorMessageIsCorrect(
                resultsCallback.mFledgeErrorResponse.getErrorMessage(),
                ERROR_NO_CA_AND_CONTEXTUAL_ADS_AVAILABLE);
        verifyLogForFailedBiddingStageDuringFetchBuyersCustomAudience(STATUS_INTERNAL_ERROR);
        verifyLogForFailurePriorPersistAdSelection(STATUS_INTERNAL_ERROR);
        verify(mAdServicesLoggerMock)
                .logFledgeApiCallStats(
                        eq(AD_SERVICES_API_CALLED__API_NAME__SELECT_ADS),
                        eq(MY_APP_PACKAGE_NAME),
                        eq(STATUS_INTERNAL_ERROR),
                        eq(RUN_AD_SELECTION_OVERALL_LATENCY_MS));
    }

    @Test
    public void testRunAdSelectionWithValidSubdomainsSuccess() throws AdServicesException {
        AdSelectionConfig adSelectionConfigWithValidSubdomains =
                mAdSelectionConfigBuilder
                        .setDecisionLogicUri(
                                CommonFixture.getUriWithValidSubdomain(
                                        SELLER_VALID.toString(), DECISION_LOGIC_PATH))
                        .setTrustedScoringSignalsUri(
                                CommonFixture.getUriWithValidSubdomain(
                                        SELLER_VALID.toString(), TRUSTED_SIGNALS_PATH))
                        .build();

        verifyAndSetupCommonSuccessScenario(adSelectionConfigWithValidSubdomains);
        AdSelectionRunner runner =
                new OnDeviceAdSelectionRunner(
                        mCustomAudienceDao,
                        mAdSelectionEntryDaoSpy,
                        mEncryptionKeyDaoMock,
                        mEnrollmentDaoMock,
                        mAdServicesHttpsClient,
                        mLightweightExecutorService,
                        mBackgroundExecutorService,
                        mScheduledExecutor,
                        mMockAdsScoreGenerator,
                        mMockAdSelectionIdGenerator,
                        mClockSpy,
                        mAdServicesLoggerMock,
                        mFakeFlags,
                        mMockDebugFlags,
                        CALLER_UID,
                        mAdSelectionServiceFilterMock,
                        mAdSelectionExecutionLogger,
                        mPerBuyerBiddingRunnerMock,
                        mFrequencyCapAdFilterer,
                        mAdCounterKeyCopier,
                        mAdCounterHistogramUpdater,
                        mFrequencyCapAdDataValidator,
                        mDebugReportingMock,
                        false,
                        mKAnonSignJoinFactoryMock,
                        mAppInstallAdFilterer);

        // Populating the Custom Audience DB
        mCustomAudienceDao.insertOrOverwriteCustomAudience(
                mDBCustomAudienceForBuyer1,
                CustomAudienceFixture.getValidDailyUpdateUriByBuyer(BUYER_1),
                false);
        mCustomAudienceDao.insertOrOverwriteCustomAudience(
                mDBCustomAudienceForBuyer2,
                CustomAudienceFixture.getValidDailyUpdateUriByBuyer(BUYER_2),
                false);

        Instant adSelectionCreationTs = Clock.systemUTC().instant().truncatedTo(ChronoUnit.MILLIS);
        when(mClockSpy.instant()).thenReturn(adSelectionCreationTs);
        DBAdSelectionEntry expectedAdSelectionResult =
                new DBAdSelectionEntry.Builder()
                        .setAdSelectionId(AD_SELECTION_ID)
                        .setBiddingLogicUri(mDBCustomAudienceForBuyer2.getBiddingLogicUri())
                        .setWinningAdBid(
                                mAdScoringOutcomeForBuyer2.getAdWithScore().getAdWithBid().getBid())
                        .setCustomAudienceSignals(
                                mAdScoringOutcomeForBuyer2.getCustomAudienceSignals())
                        .setWinningAdRenderUri(
                                mAdScoringOutcomeForBuyer2
                                        .getAdWithScore()
                                        .getAdWithBid()
                                        .getAdData()
                                        .getRenderUri())
                        .setBuyerDecisionLogicJs(
                                mAdBiddingOutcomeForBuyer1
                                        .getCustomAudienceBiddingInfo()
                                        .getBuyerDecisionLogicJs())
                        // TODO(b/230569187) add contextual signals once supported in the main logic
                        .setBuyerContextualSignals("{}")
                        .setSellerContextualSignals("{}")
                        .setCreationTimestamp(adSelectionCreationTs)
                        .build();

        AdSelectionTestCallback resultsCallback =
                invokeRunAdSelection(
                        runner, adSelectionConfigWithValidSubdomains, MY_APP_PACKAGE_NAME);

        verify(mPerBuyerBiddingRunnerMock)
                .runBidding(
                        BUYER_1,
                        ImmutableList.of(mDBCustomAudienceForBuyer1),
                        mFakeFlags.getAdSelectionBiddingTimeoutPerBuyerMs(),
                        adSelectionConfigWithValidSubdomains);
        verify(mPerBuyerBiddingRunnerMock)
                .runBidding(
                        BUYER_2,
                        ImmutableList.of(mDBCustomAudienceForBuyer2),
                        mFakeFlags.getAdSelectionBiddingTimeoutPerBuyerMs(),
                        adSelectionConfigWithValidSubdomains);

        verify(mMockAdsScoreGenerator)
                .runAdScoring(mAdBiddingOutcomeList, adSelectionConfigWithValidSubdomains);

        assertTrue(resultsCallback.mIsSuccess);
        assertEquals(
                expectedAdSelectionResult.getAdSelectionId(),
                resultsCallback.mAdSelectionResponse.getAdSelectionId());
        assertEquals(
                expectedAdSelectionResult.getWinningAdRenderUri(),
                resultsCallback.mAdSelectionResponse.getRenderUri());
        assertTrue(mAdSelectionEntryDaoSpy.doesAdSelectionIdExist(AD_SELECTION_ID));
        assertEquals(
                expectedAdSelectionResult,
                mAdSelectionEntryDaoSpy.getAdSelectionEntityById(AD_SELECTION_ID));
        verifyLogForSuccessfulBiddingProcess(mAdBiddingOutcomeList);
        verifyLogForSuccessfulAdSelectionProcess();
        verifyDebugReportingWasNotCalled();
        verify(mAdServicesLoggerMock)
                .logFledgeApiCallStats(
                        eq(AD_SERVICES_API_CALLED__API_NAME__SELECT_ADS),
                        eq(TEST_PACKAGE_NAME),
                        eq(STATUS_SUCCESS),
                        eq(RUN_AD_SELECTION_OVERALL_LATENCY_MS));
    }

    @Test
    public void testRunAdSelectionPartialBidding() throws AdServicesException {
        when(mClockSpy.instant()).thenReturn(Clock.systemUTC().instant());
        doReturn(mFakeFlags).when(FlagsFactory::getFlags);

        // Creating ad selection config for happy case with all the buyers in place
        AdSelectionConfig adSelectionConfig = mAdSelectionConfigBuilder.build();

        // Populating the Custom Audience DB
        mCustomAudienceDao.insertOrOverwriteCustomAudience(
                mDBCustomAudienceForBuyer1,
                CustomAudienceFixture.getValidDailyUpdateUriByBuyer(BUYER_1),
                false);
        mCustomAudienceDao.insertOrOverwriteCustomAudience(
                mDBCustomAudienceForBuyer2,
                CustomAudienceFixture.getValidDailyUpdateUriByBuyer(BUYER_2),
                false);

        // Getting BiddingOutcome-forBuyerX corresponding to each CA-forBuyerX
        // In this case assuming bidding fails for one of ads and return partial result
        doReturn(ImmutableList.of(Futures.immediateFuture(mAdBiddingOutcomeForBuyer1)))
                .when(mPerBuyerBiddingRunnerMock)
                .runBidding(
                        BUYER_1,
                        ImmutableList.of(mDBCustomAudienceForBuyer1),
                        mFakeFlags.getAdSelectionBiddingTimeoutPerBuyerMs(),
                        adSelectionConfig);
        doReturn(ImmutableList.of(Futures.immediateFuture(null)))
                .when(mPerBuyerBiddingRunnerMock)
                .runBidding(
                        BUYER_2,
                        ImmutableList.of(mDBCustomAudienceForBuyer2),
                        mFakeFlags.getAdSelectionBiddingTimeoutPerBuyerMs(),
                        adSelectionConfig);

        // Getting ScoringOutcome-ForBuyerX corresponding to each BiddingOutcome-forBuyerX
        // In this case we are only expected to get score for the first bidding,
        // as second one is null
        List<AdBiddingOutcome> partialBiddingOutcome = Arrays.asList(mAdBiddingOutcomeForBuyer1);
        when(mMockAdsScoreGenerator.runAdScoring(partialBiddingOutcome, adSelectionConfig))
                .thenReturn(
                        (FluentFuture.from(
                                Futures.immediateFuture(mAdScoringOutcomeList.subList(0, 1)))));

        when(mMockAdSelectionIdGenerator.generateId()).thenReturn(AD_SELECTION_ID);

        setAdSelectionExecutionLoggerMockWithSuccessAdSelection();

        mAdSelectionRunner =
                new OnDeviceAdSelectionRunner(
                        mCustomAudienceDao,
                        mAdSelectionEntryDaoSpy,
                        mEncryptionKeyDaoMock,
                        mEnrollmentDaoMock,
                        mAdServicesHttpsClient,
                        mLightweightExecutorService,
                        mBackgroundExecutorService,
                        mScheduledExecutor,
                        mMockAdsScoreGenerator,
                        mMockAdSelectionIdGenerator,
                        mClockSpy,
                        mAdServicesLoggerMock,
                        mFakeFlags,
                        mMockDebugFlags,
                        CALLER_UID,
                        mAdSelectionServiceFilterMock,
                        mAdSelectionExecutionLogger,
                        mPerBuyerBiddingRunnerMock,
                        mFrequencyCapAdFilterer,
                        mAdCounterKeyCopier,
                        mAdCounterHistogramUpdater,
                        mFrequencyCapAdDataValidator,
                        mDebugReportingMock,
                        false,
                        mKAnonSignJoinFactoryMock,
                        mAppInstallAdFilterer);

        assertFalse(mAdSelectionEntryDaoSpy.doesAdSelectionIdExist(AD_SELECTION_ID));

        AdSelectionTestCallback resultsCallback =
                invokeRunAdSelection(mAdSelectionRunner, adSelectionConfig, MY_APP_PACKAGE_NAME);

        DBAdSelection expectedDBAdSelectionResult =
                new DBAdSelection.Builder()
                        .setAdSelectionId(AD_SELECTION_ID)
                        .setCreationTimestamp(Calendar.getInstance().toInstant())
                        .setWinningAdBid(
                                mAdScoringOutcomeForBuyer1.getAdWithScore().getAdWithBid().getBid())
                        .setCustomAudienceSignals(
                                mAdScoringOutcomeForBuyer1.getCustomAudienceSignals())
                        .setWinningAdRenderUri(
                                mAdScoringOutcomeForBuyer1
                                        .getAdWithScore()
                                        .getAdWithBid()
                                        .getAdData()
                                        .getRenderUri())
                        .setBiddingLogicUri(mAdScoringOutcomeForBuyer1.getBiddingLogicUri())
                        .setBuyerContextualSignals("{}")
                        .setSellerContextualSignals("{}")
                        .setCallerPackageName(MY_APP_PACKAGE_NAME)
                        .build();

        verify(mPerBuyerBiddingRunnerMock)
                .runBidding(
                        BUYER_1,
                        ImmutableList.of(mDBCustomAudienceForBuyer1),
                        mFakeFlags.getAdSelectionBiddingTimeoutPerBuyerMs(),
                        adSelectionConfig);
        verify(mPerBuyerBiddingRunnerMock)
                .runBidding(
                        BUYER_2,
                        ImmutableList.of(mDBCustomAudienceForBuyer2),
                        mFakeFlags.getAdSelectionBiddingTimeoutPerBuyerMs(),
                        adSelectionConfig);
        verify(mMockAdsScoreGenerator).runAdScoring(partialBiddingOutcome, adSelectionConfig);

        assertTrue(resultsCallback.mIsSuccess);
        assertEquals(
                expectedDBAdSelectionResult.getAdSelectionId(),
                resultsCallback.mAdSelectionResponse.getAdSelectionId());
        assertEquals(
                expectedDBAdSelectionResult.getWinningAdRenderUri(),
                resultsCallback.mAdSelectionResponse.getRenderUri());
        assertTrue(mAdSelectionEntryDaoSpy.doesAdSelectionIdExist(AD_SELECTION_ID));
        verifyLogForSuccessfulBiddingProcess(partialBiddingOutcome);
        verifyLogForSuccessfulAdSelectionProcess();
        verifyDebugReportingWasNotCalled();
        verify(mAdServicesLoggerMock)
                .logFledgeApiCallStats(
                        eq(AD_SERVICES_API_CALLED__API_NAME__SELECT_ADS),
                        eq(TEST_PACKAGE_NAME),
                        eq(STATUS_SUCCESS),
                        eq(RUN_AD_SELECTION_OVERALL_LATENCY_MS));
    }

    @Test
    public void testRunAdSelectionBiddingFailure() {
        when(mClockSpy.instant()).thenReturn(Clock.systemUTC().instant());
        doReturn(mFakeFlags).when(FlagsFactory::getFlags);

        // Creating ad selection config for happy case with all the buyers in place
        AdSelectionConfig adSelectionConfig = mAdSelectionConfigBuilder.build();

        // Populating the Custom Audience DB
        mCustomAudienceDao.insertOrOverwriteCustomAudience(
                mDBCustomAudienceForBuyer1,
                CustomAudienceFixture.getValidDailyUpdateUriByBuyer(BUYER_1),
                false);
        mCustomAudienceDao.insertOrOverwriteCustomAudience(
                mDBCustomAudienceForBuyer2,
                CustomAudienceFixture.getValidDailyUpdateUriByBuyer(BUYER_2),
                false);

        // Getting BiddingOutcome-forBuyerX corresponding to each CA-forBuyerX
        // In this case assuming bidding fails and returns null
        doReturn(ImmutableList.of(Futures.immediateFuture(null)))
                .when(mPerBuyerBiddingRunnerMock)
                .runBidding(
                        BUYER_1,
                        ImmutableList.of(mDBCustomAudienceForBuyer1),
                        mFakeFlags.getAdSelectionBiddingTimeoutPerBuyerMs(),
                        adSelectionConfig);
        doReturn(ImmutableList.of(Futures.immediateFuture(null)))
                .when(mPerBuyerBiddingRunnerMock)
                .runBidding(
                        BUYER_2,
                        ImmutableList.of(mDBCustomAudienceForBuyer2),
                        mFakeFlags.getAdSelectionBiddingTimeoutPerBuyerMs(),
                        adSelectionConfig);

        setAdSelectionExecutionLoggerMockWithFailedAdSelectionByNoBiddingOutcomes();

        mAdSelectionRunner =
                new OnDeviceAdSelectionRunner(
                        mCustomAudienceDao,
                        mAdSelectionEntryDaoSpy,
                        mEncryptionKeyDaoMock,
                        mEnrollmentDaoMock,
                        mAdServicesHttpsClient,
                        mLightweightExecutorService,
                        mBackgroundExecutorService,
                        mScheduledExecutor,
                        mMockAdsScoreGenerator,
                        mMockAdSelectionIdGenerator,
                        mClockSpy,
                        mAdServicesLoggerMock,
                        mFakeFlags,
                        mMockDebugFlags,
                        CALLER_UID,
                        mAdSelectionServiceFilterMock,
                        mAdSelectionExecutionLogger,
                        mPerBuyerBiddingRunnerMock,
                        mFrequencyCapAdFilterer,
                        mAdCounterKeyCopier,
                        mAdCounterHistogramUpdater,
                        mFrequencyCapAdDataValidator,
                        mDebugReportingMock,
                        false,
                        mKAnonSignJoinFactoryMock,
                        mAppInstallAdFilterer);

        AdSelectionTestCallback resultsCallback =
                invokeRunAdSelection(mAdSelectionRunner, adSelectionConfig, MY_APP_PACKAGE_NAME);

        verify(mPerBuyerBiddingRunnerMock)
                .runBidding(
                        BUYER_1,
                        ImmutableList.of(mDBCustomAudienceForBuyer1),
                        mFakeFlags.getAdSelectionBiddingTimeoutPerBuyerMs(),
                        adSelectionConfig);
        verify(mPerBuyerBiddingRunnerMock)
                .runBidding(
                        BUYER_2,
                        ImmutableList.of(mDBCustomAudienceForBuyer2),
                        mFakeFlags.getAdSelectionBiddingTimeoutPerBuyerMs(),
                        adSelectionConfig);
        // If the result of bidding is empty, then we should not even attempt to run scoring
        verifyZeroInteractions(mMockAdsScoreGenerator);

        assertFalse(resultsCallback.mIsSuccess);
        verifyErrorMessageIsCorrect(
                resultsCallback.mFledgeErrorResponse.getErrorMessage(),
                ERROR_NO_VALID_BIDS_OR_CONTEXTUAL_ADS_FOR_SCORING);
        verifyLogForSuccessfulBiddingProcess(Arrays.asList(null, null));
        verifyLogForFailurePriorPersistAdSelection(STATUS_INTERNAL_ERROR);
        verify(mAdServicesLoggerMock)
                .logFledgeApiCallStats(
                        eq(AD_SERVICES_API_CALLED__API_NAME__SELECT_ADS),
                        eq(MY_APP_PACKAGE_NAME),
                        eq(STATUS_INTERNAL_ERROR),
                        eq(RUN_AD_SELECTION_OVERALL_LATENCY_MS));
    }

    @Test
    public void testRunAdSelectionScoringFailure() throws AdServicesException {
        when(mClockSpy.instant()).thenReturn(Clock.systemUTC().instant());
        doReturn(mFakeFlags).when(FlagsFactory::getFlags);

        // Creating ad selection config for happy case with all the buyers in place
        AdSelectionConfig adSelectionConfig = mAdSelectionConfigBuilder.build();

        // Populating the Custom Audience DB
        mCustomAudienceDao.insertOrOverwriteCustomAudience(
                mDBCustomAudienceForBuyer1,
                CustomAudienceFixture.getValidDailyUpdateUriByBuyer(BUYER_1),
                false);
        mCustomAudienceDao.insertOrOverwriteCustomAudience(
                mDBCustomAudienceForBuyer2,
                CustomAudienceFixture.getValidDailyUpdateUriByBuyer(BUYER_2),
                false);

        // Getting BiddingOutcome-forBuyerX corresponding to each CA-forBuyerX
        doReturn(ImmutableList.of(Futures.immediateFuture(mAdBiddingOutcomeForBuyer1)))
                .when(mPerBuyerBiddingRunnerMock)
                .runBidding(
                        BUYER_1,
                        ImmutableList.of(mDBCustomAudienceForBuyer1),
                        mFakeFlags.getAdSelectionBiddingTimeoutPerBuyerMs(),
                        adSelectionConfig);
        doReturn(ImmutableList.of(Futures.immediateFuture(mAdBiddingOutcomeForBuyer2)))
                .when(mPerBuyerBiddingRunnerMock)
                .runBidding(
                        BUYER_2,
                        ImmutableList.of(mDBCustomAudienceForBuyer2),
                        mFakeFlags.getAdSelectionBiddingTimeoutPerBuyerMs(),
                        adSelectionConfig);

        // Getting ScoringOutcome-ForBuyerX corresponding to each BiddingOutcome-forBuyerX
        // In this case assuming we get an empty result
        when(mMockAdsScoreGenerator.runAdScoring(mAdBiddingOutcomeList, adSelectionConfig))
                .thenReturn((FluentFuture.from(Futures.immediateFuture(Collections.emptyList()))));

        setAdSelectionExecutionLoggerMockWithFailedAdSelectionDuringScoring();

        mAdSelectionRunner =
                new OnDeviceAdSelectionRunner(
                        mCustomAudienceDao,
                        mAdSelectionEntryDaoSpy,
                        mEncryptionKeyDaoMock,
                        mEnrollmentDaoMock,
                        mAdServicesHttpsClient,
                        mLightweightExecutorService,
                        mBackgroundExecutorService,
                        mScheduledExecutor,
                        mMockAdsScoreGenerator,
                        mMockAdSelectionIdGenerator,
                        mClockSpy,
                        mAdServicesLoggerMock,
                        mFakeFlags,
                        mMockDebugFlags,
                        CALLER_UID,
                        mAdSelectionServiceFilterMock,
                        mAdSelectionExecutionLogger,
                        mPerBuyerBiddingRunnerMock,
                        mFrequencyCapAdFilterer,
                        mAdCounterKeyCopier,
                        mAdCounterHistogramUpdater,
                        mFrequencyCapAdDataValidator,
                        mDebugReportingMock,
                        false,
                        mKAnonSignJoinFactoryMock,
                        mAppInstallAdFilterer);

        AdSelectionTestCallback resultsCallback =
                invokeRunAdSelection(mAdSelectionRunner, adSelectionConfig, MY_APP_PACKAGE_NAME);

        verify(mPerBuyerBiddingRunnerMock)
                .runBidding(
                        BUYER_1,
                        ImmutableList.of(mDBCustomAudienceForBuyer1),
                        mFakeFlags.getAdSelectionBiddingTimeoutPerBuyerMs(),
                        adSelectionConfig);
        verify(mPerBuyerBiddingRunnerMock)
                .runBidding(
                        BUYER_2,
                        ImmutableList.of(mDBCustomAudienceForBuyer2),
                        mFakeFlags.getAdSelectionBiddingTimeoutPerBuyerMs(),
                        adSelectionConfig);
        verify(mMockAdsScoreGenerator).runAdScoring(mAdBiddingOutcomeList, adSelectionConfig);

        assertFalse(resultsCallback.mIsSuccess);
        verifyErrorMessageIsCorrect(
                resultsCallback.mFledgeErrorResponse.getErrorMessage(), ERROR_NO_WINNING_AD_FOUND);
        verifyLogForSuccessfulBiddingProcess(mAdBiddingOutcomeList);
        verifyLogForFailurePriorPersistAdSelection(STATUS_INTERNAL_ERROR);
        verify(mAdServicesLoggerMock)
                .logFledgeApiCallStats(
                        eq(AD_SERVICES_API_CALLED__API_NAME__SELECT_ADS),
                        eq(MY_APP_PACKAGE_NAME),
                        eq(STATUS_INTERNAL_ERROR),
                        eq(RUN_AD_SELECTION_OVERALL_LATENCY_MS));
    }

    @Test
    public void testRunAdSelectionNegativeScoring() throws AdServicesException {
        when(mClockSpy.instant()).thenReturn(Clock.systemUTC().instant());
        doReturn(mFakeFlags).when(FlagsFactory::getFlags);

        // Creating ad selection config for happy case with all the buyers in place
        AdSelectionConfig adSelectionConfig = mAdSelectionConfigBuilder.build();

        // Populating the Custom Audience DB
        mCustomAudienceDao.insertOrOverwriteCustomAudience(
                mDBCustomAudienceForBuyer1,
                CustomAudienceFixture.getValidDailyUpdateUriByBuyer(BUYER_1),
                false);
        mCustomAudienceDao.insertOrOverwriteCustomAudience(
                mDBCustomAudienceForBuyer2,
                CustomAudienceFixture.getValidDailyUpdateUriByBuyer(BUYER_2),
                false);

        // Getting BiddingOutcome-forBuyerX corresponding to each CA-forBuyerX
        doReturn(ImmutableList.of(Futures.immediateFuture(mAdBiddingOutcomeForBuyer1)))
                .when(mPerBuyerBiddingRunnerMock)
                .runBidding(
                        BUYER_1,
                        ImmutableList.of(mDBCustomAudienceForBuyer1),
                        mFakeFlags.getAdSelectionBiddingTimeoutPerBuyerMs(),
                        adSelectionConfig);
        doReturn(ImmutableList.of(Futures.immediateFuture(mAdBiddingOutcomeForBuyer2)))
                .when(mPerBuyerBiddingRunnerMock)
                .runBidding(
                        BUYER_2,
                        ImmutableList.of(mDBCustomAudienceForBuyer2),
                        mFakeFlags.getAdSelectionBiddingTimeoutPerBuyerMs(),
                        adSelectionConfig);

        AdScoringOutcome adScoringNegativeOutcomeForBuyer1 =
                AdScoringOutcomeFixture.anAdScoringBuilder(BUYER_1, -2.0).build();
        AdScoringOutcome adScoringNegativeOutcomeForBuyer2 =
                AdScoringOutcomeFixture.anAdScoringBuilder(BUYER_2, -3.0).build();
        List<AdScoringOutcome> negativeScoreOutcome =
                Arrays.asList(adScoringNegativeOutcomeForBuyer1, adScoringNegativeOutcomeForBuyer2);

        // Getting ScoringOutcome-ForBuyerX corresponding to each BiddingOutcome-forBuyerX
        // In this case assuming we get a result with negative scores
        when(mMockAdsScoreGenerator.runAdScoring(mAdBiddingOutcomeList, adSelectionConfig))
                .thenReturn((FluentFuture.from(Futures.immediateFuture(negativeScoreOutcome))));

        setAdSelectionExecutionLoggerMockWithFailedAdSelectionDuringScoring();

        mAdSelectionRunner =
                new OnDeviceAdSelectionRunner(
                        mCustomAudienceDao,
                        mAdSelectionEntryDaoSpy,
                        mEncryptionKeyDaoMock,
                        mEnrollmentDaoMock,
                        mAdServicesHttpsClient,
                        mLightweightExecutorService,
                        mBackgroundExecutorService,
                        mScheduledExecutor,
                        mMockAdsScoreGenerator,
                        mMockAdSelectionIdGenerator,
                        mClockSpy,
                        mAdServicesLoggerMock,
                        mFakeFlags,
                        mMockDebugFlags,
                        CALLER_UID,
                        mAdSelectionServiceFilterMock,
                        mAdSelectionExecutionLogger,
                        mPerBuyerBiddingRunnerMock,
                        mFrequencyCapAdFilterer,
                        mAdCounterKeyCopier,
                        mAdCounterHistogramUpdater,
                        mFrequencyCapAdDataValidator,
                        mDebugReportingMock,
                        false,
                        mKAnonSignJoinFactoryMock,
                        mAppInstallAdFilterer);

        AdSelectionTestCallback resultsCallback =
                invokeRunAdSelection(mAdSelectionRunner, adSelectionConfig, MY_APP_PACKAGE_NAME);

        verify(mPerBuyerBiddingRunnerMock)
                .runBidding(
                        BUYER_1,
                        ImmutableList.of(mDBCustomAudienceForBuyer1),
                        mFakeFlags.getAdSelectionBiddingTimeoutPerBuyerMs(),
                        adSelectionConfig);
        verify(mPerBuyerBiddingRunnerMock)
                .runBidding(
                        BUYER_2,
                        ImmutableList.of(mDBCustomAudienceForBuyer2),
                        mFakeFlags.getAdSelectionBiddingTimeoutPerBuyerMs(),
                        adSelectionConfig);
        verify(mMockAdsScoreGenerator).runAdScoring(mAdBiddingOutcomeList, adSelectionConfig);

        assertFalse(resultsCallback.mIsSuccess);
        verifyErrorMessageIsCorrect(
                resultsCallback.mFledgeErrorResponse.getErrorMessage(), ERROR_NO_WINNING_AD_FOUND);
        verifyLogForSuccessfulBiddingProcess(mAdBiddingOutcomeList);
        verifyLogForFailurePriorPersistAdSelection(STATUS_INTERNAL_ERROR);

        verify(mAdServicesLoggerMock)
                .logFledgeApiCallStats(
                        eq(AD_SERVICES_API_CALLED__API_NAME__SELECT_ADS),
                        eq(MY_APP_PACKAGE_NAME),
                        eq(STATUS_INTERNAL_ERROR),
                        eq(RUN_AD_SELECTION_OVERALL_LATENCY_MS));
    }

    @Test
    public void testRunAdSelectionPartialNegativeScoring() throws AdServicesException {
        when(mClockSpy.instant()).thenReturn(Clock.systemUTC().instant());
        doReturn(mFakeFlags).when(FlagsFactory::getFlags);

        // Creating ad selection config for happy case with all the buyers in place
        AdSelectionConfig adSelectionConfig = mAdSelectionConfigBuilder.build();

        // Populating the Custom Audience DB
        mCustomAudienceDao.insertOrOverwriteCustomAudience(
                mDBCustomAudienceForBuyer1,
                CustomAudienceFixture.getValidDailyUpdateUriByBuyer(BUYER_1),
                false);
        mCustomAudienceDao.insertOrOverwriteCustomAudience(
                mDBCustomAudienceForBuyer2,
                CustomAudienceFixture.getValidDailyUpdateUriByBuyer(BUYER_2),
                false);

        // Getting BiddingOutcome-forBuyerX corresponding to each CA-forBuyerX
        doReturn(ImmutableList.of(Futures.immediateFuture(mAdBiddingOutcomeForBuyer1)))
                .when(mPerBuyerBiddingRunnerMock)
                .runBidding(
                        BUYER_1,
                        ImmutableList.of(mDBCustomAudienceForBuyer1),
                        mFakeFlags.getAdSelectionBiddingTimeoutPerBuyerMs(),
                        adSelectionConfig);
        doReturn(ImmutableList.of(Futures.immediateFuture(mAdBiddingOutcomeForBuyer2)))
                .when(mPerBuyerBiddingRunnerMock)
                .runBidding(
                        BUYER_2,
                        ImmutableList.of(mDBCustomAudienceForBuyer2),
                        mFakeFlags.getAdSelectionBiddingTimeoutPerBuyerMs(),
                        adSelectionConfig);

        AdScoringOutcome adScoringNegativeOutcomeForBuyer1 =
                AdScoringOutcomeFixture.anAdScoringBuilder(BUYER_1, 2.0).build();
        AdScoringOutcome adScoringNegativeOutcomeForBuyer2 =
                AdScoringOutcomeFixture.anAdScoringBuilder(BUYER_2, -3.0).build();
        List<AdScoringOutcome> negativeScoreOutcome =
                Arrays.asList(adScoringNegativeOutcomeForBuyer1, adScoringNegativeOutcomeForBuyer2);

        // Getting ScoringOutcome-ForBuyerX corresponding to each BiddingOutcome-forBuyerX
        // In this case assuming we get a result with partially negative scores
        when(mMockAdsScoreGenerator.runAdScoring(mAdBiddingOutcomeList, adSelectionConfig))
                .thenReturn((FluentFuture.from(Futures.immediateFuture(negativeScoreOutcome))));

        when(mMockAdSelectionIdGenerator.generateId()).thenReturn(AD_SELECTION_ID);

        setAdSelectionExecutionLoggerMockWithSuccessAdSelection();

        mAdSelectionRunner =
                new OnDeviceAdSelectionRunner(
                        mCustomAudienceDao,
                        mAdSelectionEntryDaoSpy,
                        mEncryptionKeyDaoMock,
                        mEnrollmentDaoMock,
                        mAdServicesHttpsClient,
                        mLightweightExecutorService,
                        mBackgroundExecutorService,
                        mScheduledExecutor,
                        mMockAdsScoreGenerator,
                        mMockAdSelectionIdGenerator,
                        mClockSpy,
                        mAdServicesLoggerMock,
                        mFakeFlags,
                        mMockDebugFlags,
                        CALLER_UID,
                        mAdSelectionServiceFilterMock,
                        mAdSelectionExecutionLogger,
                        mPerBuyerBiddingRunnerMock,
                        mFrequencyCapAdFilterer,
                        mAdCounterKeyCopier,
                        mAdCounterHistogramUpdater,
                        mFrequencyCapAdDataValidator,
                        mDebugReportingMock,
                        false,
                        mKAnonSignJoinFactoryMock,
                        mAppInstallAdFilterer);

        assertFalse(mAdSelectionEntryDaoSpy.doesAdSelectionIdExist(AD_SELECTION_ID));

        AdSelectionTestCallback resultsCallback =
                invokeRunAdSelection(mAdSelectionRunner, adSelectionConfig, MY_APP_PACKAGE_NAME);

        verify(mPerBuyerBiddingRunnerMock)
                .runBidding(
                        BUYER_1,
                        ImmutableList.of(mDBCustomAudienceForBuyer1),
                        mFakeFlags.getAdSelectionBiddingTimeoutPerBuyerMs(),
                        adSelectionConfig);
        verify(mPerBuyerBiddingRunnerMock)
                .runBidding(
                        BUYER_2,
                        ImmutableList.of(mDBCustomAudienceForBuyer2),
                        mFakeFlags.getAdSelectionBiddingTimeoutPerBuyerMs(),
                        adSelectionConfig);
        verify(mMockAdsScoreGenerator).runAdScoring(mAdBiddingOutcomeList, adSelectionConfig);

        DBAdSelection expectedDBAdSelectionResult =
                new DBAdSelection.Builder()
                        .setAdSelectionId(AD_SELECTION_ID)
                        .setCreationTimestamp(Calendar.getInstance().toInstant())
                        .setWinningAdBid(
                                mAdScoringOutcomeForBuyer1.getAdWithScore().getAdWithBid().getBid())
                        .setCustomAudienceSignals(
                                mAdScoringOutcomeForBuyer1.getCustomAudienceSignals())
                        .setWinningAdRenderUri(
                                mAdScoringOutcomeForBuyer1
                                        .getAdWithScore()
                                        .getAdWithBid()
                                        .getAdData()
                                        .getRenderUri())
                        .setBiddingLogicUri(mAdScoringOutcomeForBuyer1.getBiddingLogicUri())
                        .setBuyerContextualSignals("{}")
                        .setSellerContextualSignals("{}")
                        .setCallerPackageName(MY_APP_PACKAGE_NAME)
                        .build();

        assertTrue(resultsCallback.mIsSuccess);
        assertEquals(
                expectedDBAdSelectionResult.getAdSelectionId(),
                resultsCallback.mAdSelectionResponse.getAdSelectionId());
        assertEquals(
                expectedDBAdSelectionResult.getWinningAdRenderUri(),
                resultsCallback.mAdSelectionResponse.getRenderUri());
        assertTrue(mAdSelectionEntryDaoSpy.doesAdSelectionIdExist(AD_SELECTION_ID));
        verifyLogForSuccessfulBiddingProcess(mAdBiddingOutcomeList);
        verifyLogForSuccessfulAdSelectionProcess();
        verifyDebugReportingWasNotCalled();

        verify(mAdServicesLoggerMock)
                .logFledgeApiCallStats(
                        eq(AD_SERVICES_API_CALLED__API_NAME__SELECT_ADS),
                        eq(TEST_PACKAGE_NAME),
                        eq(STATUS_SUCCESS),
                        eq(RUN_AD_SELECTION_OVERALL_LATENCY_MS));
    }

    @Test
    public void testRunAdSelectionScoringException() throws AdServicesException {
        when(mClockSpy.instant()).thenReturn(Clock.systemUTC().instant());
        doReturn(mFakeFlags).when(FlagsFactory::getFlags);

        // Creating ad selection config for happy case with all the buyers in place
        AdSelectionConfig adSelectionConfig =
                mAdSelectionConfigBuilder
                        .setAdSelectionSignals(AdSelectionSignals.fromString("{/}"))
                        .build();

        // Populating the Custom Audience DB
        mCustomAudienceDao.insertOrOverwriteCustomAudience(
                mDBCustomAudienceForBuyer1,
                CustomAudienceFixture.getValidDailyUpdateUriByBuyer(BUYER_1),
                false);
        mCustomAudienceDao.insertOrOverwriteCustomAudience(
                mDBCustomAudienceForBuyer2,
                CustomAudienceFixture.getValidDailyUpdateUriByBuyer(BUYER_2),
                false);

        // Getting BiddingOutcome-forBuyerX corresponding to each CA-forBuyerX
        doReturn(ImmutableList.of(Futures.immediateFuture(mAdBiddingOutcomeForBuyer1)))
                .when(mPerBuyerBiddingRunnerMock)
                .runBidding(
                        BUYER_1,
                        ImmutableList.of(mDBCustomAudienceForBuyer1),
                        mFakeFlags.getAdSelectionBiddingTimeoutPerBuyerMs(),
                        adSelectionConfig);
        doReturn(ImmutableList.of(Futures.immediateFuture(mAdBiddingOutcomeForBuyer2)))
                .when(mPerBuyerBiddingRunnerMock)
                .runBidding(
                        BUYER_2,
                        ImmutableList.of(mDBCustomAudienceForBuyer2),
                        mFakeFlags.getAdSelectionBiddingTimeoutPerBuyerMs(),
                        adSelectionConfig);

        // Getting ScoringOutcome-ForBuyerX corresponding to each BiddingOutcome-forBuyerX
        // In this case we expect a JSON validation exception
        when(mMockAdsScoreGenerator.runAdScoring(mAdBiddingOutcomeList, adSelectionConfig))
                .thenThrow(new AdServicesException(ERROR_INVALID_JSON));

        setAdSelectionExecutionLoggerMockWithFailedAdSelectionDuringScoring();

        mAdSelectionRunner =
                new OnDeviceAdSelectionRunner(
                        mCustomAudienceDao,
                        mAdSelectionEntryDaoSpy,
                        mEncryptionKeyDaoMock,
                        mEnrollmentDaoMock,
                        mAdServicesHttpsClient,
                        mLightweightExecutorService,
                        mBackgroundExecutorService,
                        mScheduledExecutor,
                        mMockAdsScoreGenerator,
                        mMockAdSelectionIdGenerator,
                        mClockSpy,
                        mAdServicesLoggerMock,
                        mFakeFlags,
                        mMockDebugFlags,
                        CALLER_UID,
                        mAdSelectionServiceFilterMock,
                        mAdSelectionExecutionLogger,
                        mPerBuyerBiddingRunnerMock,
                        mFrequencyCapAdFilterer,
                        mAdCounterKeyCopier,
                        mAdCounterHistogramUpdater,
                        mFrequencyCapAdDataValidator,
                        mDebugReportingMock,
                        false,
                        mKAnonSignJoinFactoryMock,
                        mAppInstallAdFilterer);

        AdSelectionTestCallback resultsCallback =
                invokeRunAdSelection(mAdSelectionRunner, adSelectionConfig, MY_APP_PACKAGE_NAME);

        verify(mPerBuyerBiddingRunnerMock)
                .runBidding(
                        BUYER_1,
                        ImmutableList.of(mDBCustomAudienceForBuyer1),
                        mFakeFlags.getAdSelectionBiddingTimeoutPerBuyerMs(),
                        adSelectionConfig);
        verify(mPerBuyerBiddingRunnerMock)
                .runBidding(
                        BUYER_2,
                        ImmutableList.of(mDBCustomAudienceForBuyer2),
                        mFakeFlags.getAdSelectionBiddingTimeoutPerBuyerMs(),
                        adSelectionConfig);

        assertFalse(resultsCallback.mIsSuccess);
        verifyErrorMessageIsCorrect(
                resultsCallback.mFledgeErrorResponse.getErrorMessage(), ERROR_INVALID_JSON);

        verifyLogForSuccessfulBiddingProcess(mAdBiddingOutcomeList);
        verifyLogForFailurePriorPersistAdSelection(STATUS_INTERNAL_ERROR);

        verify(mAdServicesLoggerMock)
                .logFledgeApiCallStats(
                        eq(AD_SERVICES_API_CALLED__API_NAME__SELECT_ADS),
                        eq(MY_APP_PACKAGE_NAME),
                        eq(STATUS_INTERNAL_ERROR),
                        eq(RUN_AD_SELECTION_OVERALL_LATENCY_MS));
    }

    @Test
    public void testRunAdSelection_withOnDeviceAuctionDisabled_throwsIllegalStateException() {
        Flags flagsWithOnDeviceAuctionDisabled =
                new OnDeviceAdSelectionRunnerTestFlags() {
                    @Override
                    public boolean getFledgeOnDeviceAuctionKillSwitch() {
                        return true;
                    }
                };
        mAdSelectionExecutionLogger =
                new AdSelectionExecutionLogger(
                        sCallerMetadata,
                        mLoggerClockMock,
                        mSpyContext,
                        mAdServicesLoggerMock,
                        mFakeFlags);
        AdSelectionRunner runner =
                new OnDeviceAdSelectionRunner(
                        mCustomAudienceDao,
                        mAdSelectionEntryDaoSpy,
                        mEncryptionKeyDaoMock,
                        mEnrollmentDaoMock,
                        mAdServicesHttpsClient,
                        mLightweightExecutorService,
                        mBackgroundExecutorService,
                        mScheduledExecutor,
                        mMockAdsScoreGenerator,
                        mMockAdSelectionIdGenerator,
                        mClockSpy,
                        mAdServicesLoggerMock,
                        flagsWithOnDeviceAuctionDisabled,
                        mMockDebugFlags,
                        CALLER_UID,
                        mAdSelectionServiceFilterMock,
                        mAdSelectionExecutionLogger,
                        mPerBuyerBiddingRunnerMock,
                        mFrequencyCapAdFilterer,
                        mAdCounterKeyCopier,
                        mAdCounterHistogramUpdater,
                        mFrequencyCapAdDataValidator,
                        mDebugReportingMock,
                        false,
                        mKAnonSignJoinFactoryMock,
                        mAppInstallAdFilterer);
        AdSelectionConfig adSelectionConfig = mAdSelectionConfigBuilder.build();

        AdSelectionTestCallback callback =
                invokeRunAdSelection(runner, adSelectionConfig, MY_APP_PACKAGE_NAME);

        verifyErrorMessageIsCorrect(
                callback.mFledgeErrorResponse.getErrorMessage(),
                ON_DEVICE_AUCTION_KILL_SWITCH_ENABLED);
    }

    @Test
    public void testmMockDBAdSeleciton() throws AdServicesException {
        when(mClockSpy.instant()).thenReturn(Clock.systemUTC().instant());
        doReturn(mFakeFlags).when(FlagsFactory::getFlags);

        Flags flagsWithSmallerLimits =
                new OnDeviceAdSelectionRunnerTestFlags() {
                    @Override
                    public long getAdSelectionOverallTimeoutMs() {
                        return 1000;
                    }

                    @Override
                    public boolean getDisableFledgeEnrollmentCheck() {
                        return true;
                    }
                };

        // Creating ad selection config for happy case with all the buyers in place
        AdSelectionConfig adSelectionConfig = mAdSelectionConfigBuilder.build();

        // Populating the Custom Audience DB
        mCustomAudienceDao.insertOrOverwriteCustomAudience(
                mDBCustomAudienceForBuyer1,
                CustomAudienceFixture.getValidDailyUpdateUriByBuyer(BUYER_1),
                false);
        mCustomAudienceDao.insertOrOverwriteCustomAudience(
                mDBCustomAudienceForBuyer2,
                CustomAudienceFixture.getValidDailyUpdateUriByBuyer(BUYER_2),
                false);

        // Getting BiddingOutcome-forBuyerX corresponding to each CA-forBuyerX
        doReturn(ImmutableList.of(Futures.immediateFuture(mAdBiddingOutcomeForBuyer1)))
                .when(mPerBuyerBiddingRunnerMock)
                .runBidding(
                        BUYER_1,
                        ImmutableList.of(mDBCustomAudienceForBuyer1),
                        flagsWithSmallerLimits.getAdSelectionBiddingTimeoutPerBuyerMs(),
                        adSelectionConfig);
        doReturn(ImmutableList.of(Futures.immediateFuture(mAdBiddingOutcomeForBuyer2)))
                .when(mPerBuyerBiddingRunnerMock)
                .runBidding(
                        BUYER_2,
                        ImmutableList.of(mDBCustomAudienceForBuyer2),
                        flagsWithSmallerLimits.getAdSelectionBiddingTimeoutPerBuyerMs(),
                        adSelectionConfig);

        // Getting ScoringOutcome-ForBuyerX corresponding to each BiddingOutcome-forBuyerX
        when(mMockAdsScoreGenerator.runAdScoring(mAdBiddingOutcomeList, adSelectionConfig))
                .thenReturn((FluentFuture.from(Futures.immediateFuture(mAdScoringOutcomeList))));

        Instant adSelectionCreationTs = Clock.systemUTC().instant().truncatedTo(ChronoUnit.MILLIS);
        when(mClockSpy.instant()).thenReturn(adSelectionCreationTs);

        when(mMockAdSelectionIdGenerator.generateId())
                .thenAnswer(
                        new AnswersWithDelay(
                                2 * mFakeFlags.getAdSelectionOverallTimeoutMs(),
                                new Returns(AD_SELECTION_ID)));
        setAdSelectionExecutionLoggerMockWithFailedAdSelectionBeforePersistAdSelection();

        mAdSelectionRunner =
                new OnDeviceAdSelectionRunner(
                        mCustomAudienceDao,
                        mAdSelectionEntryDaoSpy,
                        mEncryptionKeyDaoMock,
                        mEnrollmentDaoMock,
                        mAdServicesHttpsClient,
                        mLightweightExecutorService,
                        mBackgroundExecutorService,
                        mScheduledExecutor,
                        mMockAdsScoreGenerator,
                        mMockAdSelectionIdGenerator,
                        mClockSpy,
                        mAdServicesLoggerMock,
                        flagsWithSmallerLimits,
                        mMockDebugFlags,
                        CALLER_UID,
                        mAdSelectionServiceFilterMock,
                        mAdSelectionExecutionLogger,
                        mPerBuyerBiddingRunnerMock,
                        mFrequencyCapAdFilterer,
                        mAdCounterKeyCopier,
                        mAdCounterHistogramUpdater,
                        mFrequencyCapAdDataValidator,
                        mDebugReportingMock,
                        false,
                        mKAnonSignJoinFactoryMock,
                        mAppInstallAdFilterer);

        assertFalse(mAdSelectionEntryDaoSpy.doesAdSelectionIdExist(AD_SELECTION_ID));

        AdSelectionTestCallback resultsCallback =
                invokeRunAdSelection(mAdSelectionRunner, adSelectionConfig, MY_APP_PACKAGE_NAME);

        assertFalse(resultsCallback.mIsSuccess);
        FledgeErrorResponse response = resultsCallback.mFledgeErrorResponse;
        verifyErrorMessageIsCorrect(response.getErrorMessage(), AD_SELECTION_TIMED_OUT);
        Assert.assertEquals(
                "Error response code mismatch",
                AdServicesStatusUtils.STATUS_TIMEOUT,
                response.getStatusCode());

        verifyLogForSuccessfulBiddingProcess(mAdBiddingOutcomeList);
        verifyLogForFailureByRunAdSelectionOrchestrationTimesOut(STATUS_TIMEOUT);

        verify(mAdServicesLoggerMock)
                .logFledgeApiCallStats(
                        eq(AD_SERVICES_API_CALLED__API_NAME__SELECT_ADS),
                        eq(MY_APP_PACKAGE_NAME),
                        eq(STATUS_TIMEOUT),
                        eq(RUN_AD_SELECTION_OVERALL_LATENCY_MS));
    }

    @Test
    public void testRunAdSelectionPerBuyerTimeout() throws AdServicesException {
        Flags flagsWithSmallPerBuyerTimeout =
                new OnDeviceAdSelectionRunnerTestFlags() {
                    @Override
                    public long getAdSelectionOverallTimeoutMs() {
                        return 5000;
                    }

                    @Override
                    public long getAdSelectionBiddingTimeoutPerBuyerMs() {
                        return 100;
                    }

                    @Override
                    public boolean getDisableFledgeEnrollmentCheck() {
                        return true;
                    }
                };
        when(mClockSpy.instant()).thenReturn(Clock.systemUTC().instant());
        doReturn(flagsWithSmallPerBuyerTimeout).when(FlagsFactory::getFlags);
        AdSelectionConfig adSelectionConfig = mAdSelectionConfigBuilder.build();

        // Populating the Custom Audience DB
        mCustomAudienceDao.insertOrOverwriteCustomAudience(
                mDBCustomAudienceForBuyer1,
                CustomAudienceFixture.getValidDailyUpdateUriByBuyer(BUYER_1),
                false);
        mCustomAudienceDao.insertOrOverwriteCustomAudience(
                mDBCustomAudienceForBuyer2,
                CustomAudienceFixture.getValidDailyUpdateUriByBuyer(BUYER_2),
                false);

        Callable<AdBiddingOutcome> delayedBiddingOutcomeForBuyer1 =
                () -> {
                    TimeUnit.MILLISECONDS.sleep(
                            10
                                    * flagsWithSmallPerBuyerTimeout
                                            .getAdSelectionBiddingTimeoutPerBuyerMs());
                    return mAdBiddingOutcomeForBuyer1;
                };

        doReturn(ImmutableList.of())
                .when(mPerBuyerBiddingRunnerMock)
                .runBidding(
                        BUYER_1,
                        ImmutableList.of(mDBCustomAudienceForBuyer1),
                        flagsWithSmallPerBuyerTimeout.getAdSelectionBiddingTimeoutPerBuyerMs(),
                        adSelectionConfig);
        doReturn(ImmutableList.of(Futures.immediateFuture(mAdBiddingOutcomeForBuyer2)))
                .when(mPerBuyerBiddingRunnerMock)
                .runBidding(
                        BUYER_2,
                        ImmutableList.of(mDBCustomAudienceForBuyer2),
                        flagsWithSmallPerBuyerTimeout.getAdSelectionBiddingTimeoutPerBuyerMs(),
                        adSelectionConfig);

        // bidding Outcome List should only have one bidding outcome
        List<AdBiddingOutcome> adBiddingOutcomeList = ImmutableList.of(mAdBiddingOutcomeForBuyer2);

        // Getting ScoringOutcome-ForBuyerX corresponding to each BiddingOutcome-forBuyerX
        when(mMockAdsScoreGenerator.runAdScoring(adBiddingOutcomeList, adSelectionConfig))
                .thenReturn(
                        (FluentFuture.from(
                                Futures.immediateFuture(
                                        ImmutableList.of(mAdScoringOutcomeForBuyer2)))));

        Instant adSelectionCreationTs = Clock.systemUTC().instant().truncatedTo(ChronoUnit.MILLIS);
        when(mClockSpy.instant()).thenReturn(adSelectionCreationTs);

        DBAdSelectionEntry expectedAdSelectionResult =
                new DBAdSelectionEntry.Builder()
                        .setAdSelectionId(AD_SELECTION_ID)
                        .setBiddingLogicUri(mDBCustomAudienceForBuyer2.getBiddingLogicUri())
                        .setWinningAdBid(
                                mAdScoringOutcomeForBuyer2.getAdWithScore().getAdWithBid().getBid())
                        .setCustomAudienceSignals(
                                mAdScoringOutcomeForBuyer2.getCustomAudienceSignals())
                        .setWinningAdRenderUri(
                                mAdScoringOutcomeForBuyer2
                                        .getAdWithScore()
                                        .getAdWithBid()
                                        .getAdData()
                                        .getRenderUri())
                        .setBuyerDecisionLogicJs(
                                mAdBiddingOutcomeForBuyer1
                                        .getCustomAudienceBiddingInfo()
                                        .getBuyerDecisionLogicJs())
                        // TODO(b/230569187) add contextual signals once supported in the main logic
                        .setBuyerContextualSignals("{}")
                        .setSellerContextualSignals("{}")
                        .setCreationTimestamp(adSelectionCreationTs)
                        .build();

        when(mMockAdSelectionIdGenerator.generateId()).thenReturn(AD_SELECTION_ID);

        setAdSelectionExecutionLoggerMockWithSuccessAdSelection();

        mAdSelectionRunner =
                new OnDeviceAdSelectionRunner(
                        mCustomAudienceDao,
                        mAdSelectionEntryDaoSpy,
                        mEncryptionKeyDaoMock,
                        mEnrollmentDaoMock,
                        mAdServicesHttpsClient,
                        mLightweightExecutorService,
                        mBackgroundExecutorService,
                        mScheduledExecutor,
                        mMockAdsScoreGenerator,
                        mMockAdSelectionIdGenerator,
                        mClockSpy,
                        mAdServicesLoggerMock,
                        flagsWithSmallPerBuyerTimeout,
                        mMockDebugFlags,
                        CALLER_UID,
                        mAdSelectionServiceFilterMock,
                        mAdSelectionExecutionLogger,
                        mPerBuyerBiddingRunnerMock,
                        mFrequencyCapAdFilterer,
                        mAdCounterKeyCopier,
                        mAdCounterHistogramUpdater,
                        mFrequencyCapAdDataValidator,
                        mDebugReportingMock,
                        false,
                        mKAnonSignJoinFactoryMock,
                        mAppInstallAdFilterer);

        assertFalse(mAdSelectionEntryDaoSpy.doesAdSelectionIdExist(AD_SELECTION_ID));

        AdSelectionTestCallback resultsCallback =
                invokeRunAdSelection(mAdSelectionRunner, adSelectionConfig, MY_APP_PACKAGE_NAME);

        verify(mPerBuyerBiddingRunnerMock)
                .runBidding(
                        BUYER_1,
                        ImmutableList.of(mDBCustomAudienceForBuyer1),
                        flagsWithSmallPerBuyerTimeout.getAdSelectionBiddingTimeoutPerBuyerMs(),
                        adSelectionConfig);
        verify(mPerBuyerBiddingRunnerMock)
                .runBidding(
                        BUYER_2,
                        ImmutableList.of(mDBCustomAudienceForBuyer2),
                        flagsWithSmallPerBuyerTimeout.getAdSelectionBiddingTimeoutPerBuyerMs(),
                        adSelectionConfig);
        verify(mMockAdsScoreGenerator).runAdScoring(adBiddingOutcomeList, adSelectionConfig);

        assertTrue(resultsCallback.mIsSuccess);
        assertEquals(
                expectedAdSelectionResult.getAdSelectionId(),
                resultsCallback.mAdSelectionResponse.getAdSelectionId());
        assertEquals(
                expectedAdSelectionResult.getWinningAdRenderUri(),
                resultsCallback.mAdSelectionResponse.getRenderUri());
        assertTrue(mAdSelectionEntryDaoSpy.doesAdSelectionIdExist(AD_SELECTION_ID));
        assertEquals(
                expectedAdSelectionResult,
                mAdSelectionEntryDaoSpy.getAdSelectionEntityById(AD_SELECTION_ID));
        verifyLogForSuccessfulBiddingProcess(adBiddingOutcomeList);
        verifyLogForSuccessfulAdSelectionProcess();
        verifyDebugReportingWasNotCalled();
        verify(mAdServicesLoggerMock)
                .logFledgeApiCallStats(
                        eq(AD_SERVICES_API_CALLED__API_NAME__SELECT_ADS),
                        eq(TEST_PACKAGE_NAME),
                        eq(STATUS_SUCCESS),
                        eq(RUN_AD_SELECTION_OVERALL_LATENCY_MS));
    }

    @Test
    public void testRunAdSelectionThrottledFailure() throws AdServicesException {
        when(mClockSpy.instant()).thenReturn(Clock.systemUTC().instant());
        doReturn(mFakeFlags).when(FlagsFactory::getFlags);
        // Creating ad selection config for happy case with all the buyers in place
        AdSelectionConfig adSelectionConfig = mAdSelectionConfigBuilder.build();

        // Throttle Ad Selection request
        doThrow(new FilterException(new LimitExceededException(RATE_LIMIT_REACHED_ERROR_MESSAGE)))
                .when(mAdSelectionServiceFilterMock)
                .filterRequest(
                        SELLER_VALID,
                        MY_APP_PACKAGE_NAME,
                        true,
                        true,
                        true,
                        CALLER_UID,
                        AdServicesStatsLog.AD_SERVICES_API_CALLED__API_NAME__SELECT_ADS,
                        Throttler.ApiKey.FLEDGE_API_SELECT_ADS,
                        DevContext.createForDevOptionsDisabled());

        setAdSelectionExecutionLoggerMockWithFailedAdSelectionByValidateRequest();

        mAdSelectionRunner =
                new OnDeviceAdSelectionRunner(
                        mCustomAudienceDao,
                        mAdSelectionEntryDaoSpy,
                        mEncryptionKeyDaoMock,
                        mEnrollmentDaoMock,
                        mAdServicesHttpsClient,
                        mLightweightExecutorService,
                        mBackgroundExecutorService,
                        mScheduledExecutor,
                        mMockAdsScoreGenerator,
                        mMockAdSelectionIdGenerator,
                        mClockSpy,
                        mAdServicesLoggerMock,
                        mFakeFlags,
                        mMockDebugFlags,
                        CALLER_UID,
                        mAdSelectionServiceFilterMock,
                        mAdSelectionExecutionLogger,
                        mPerBuyerBiddingRunnerMock,
                        mFrequencyCapAdFilterer,
                        mAdCounterKeyCopier,
                        mAdCounterHistogramUpdater,
                        mFrequencyCapAdDataValidator,
                        mDebugReportingMock,
                        false,
                        mKAnonSignJoinFactoryMock,
                        mAppInstallAdFilterer);

        AdSelectionTestCallback resultsCallback =
                invokeRunAdSelection(mAdSelectionRunner, adSelectionConfig, MY_APP_PACKAGE_NAME);

        assertFalse(resultsCallback.mIsSuccess);
        FledgeErrorResponse response = resultsCallback.mFledgeErrorResponse;
        verifyErrorMessageIsCorrect(response.getErrorMessage(), RATE_LIMIT_REACHED_ERROR_MESSAGE);
        Assert.assertEquals(
                "Error response code mismatch",
                STATUS_RATE_LIMIT_REACHED,
                response.getStatusCode());
        verifyLogForFailurePriorPersistAdSelection(STATUS_RATE_LIMIT_REACHED);

        // Confirm a duplicate log entry does not exist.
        // AdSelectionServiceFilter ensures the failing assertion is logged internally.
        verify(mAdServicesLoggerMock, never())
                .logFledgeApiCallStats(
                        eq(AD_SERVICES_API_CALLED__API_NAME__SELECT_ADS),
                        eq(MY_APP_PACKAGE_NAME),
                        eq(STATUS_RATE_LIMIT_REACHED),
                        eq(RUN_AD_SELECTION_OVERALL_LATENCY_MS));
    }

    @Test
    public void testFilterOneAd() throws AdServicesException {
        final Flags flags =
                new OnDeviceAdSelectionRunnerTestFlags() {
                    @Override
                    public boolean getFledgeAppInstallFilteringEnabled() {
                        return true;
                    }

                    @Override
                    public boolean getFledgeFrequencyCapFilteringEnabled() {
                        return true;
                    }

                    @Override
                    public boolean getFledgeAppInstallFilteringMetricsEnabled() {
                        return true;
                    }

                    @Override
                    public boolean getFledgeFrequencyCapFilteringMetricsEnabled() {
                        return true;
                    }
                };
        AdSelectionConfig adSelectionConfig = mAdSelectionConfigBuilder.build();
        verifyAndSetupAdFilteringSuccessScenario(adSelectionConfig);
        mAdSelectionRunner =
                new OnDeviceAdSelectionRunner(
                        mCustomAudienceDao,
                        mAdSelectionEntryDaoSpy,
                        mEncryptionKeyDaoMock,
                        mEnrollmentDaoMock,
                        mAdServicesHttpsClient,
                        mLightweightExecutorService,
                        mBackgroundExecutorService,
                        mScheduledExecutor,
                        mMockAdsScoreGenerator,
                        mMockAdSelectionIdGenerator,
                        mClockSpy,
                        mAdServicesLoggerMock,
                        flags,
                        mMockDebugFlags,
                        CALLER_UID,
                        mAdSelectionServiceFilterMock,
                        mAdSelectionExecutionLogger,
                        mPerBuyerBiddingRunnerMock,
                        mMockFrequencyCapAdFilterer,
                        mAdCounterKeyCopier,
                        mAdCounterHistogramUpdater,
                        mFrequencyCapAdDataValidator,
                        mDebugReportingMock,
                        false,
                        mKAnonSignJoinFactoryMock,
                        mAppInstallAdFilterer);
        List<DBAdData> adsToNotFilter =
                DBAdDataFixture.getValidDbAdDataListByBuyer(mDBCustomAudienceForBuyer1.getBuyer());
        AppInstallFilters appFilters =
                new AppInstallFilters.Builder()
                        .setPackageNames(Collections.singleton(CommonFixture.TEST_PACKAGE_NAME_1))
                        .build();
        DBAdData adToFilter =
                DBAdDataFixture.getValidDbAdDataBuilder()
                        .setAdFilters(
                                new AdFilters.Builder().setAppInstallFilters(appFilters).build())
                        .build();

        List<DBAdData> ads = new ArrayList<>(adsToNotFilter);
        ads.add(adToFilter);

        DBCustomAudience caWithFilterAd =
                new DBCustomAudience.Builder(mDBCustomAudienceForBuyer1).setAds(ads).build();

        DBCustomAudience caWithoutFilterAd =
                new DBCustomAudience.Builder(mDBCustomAudienceForBuyer1)
                        .setAds(adsToNotFilter)
                        .build();

        // Populating the Custom Audience DB
        mCustomAudienceDao.insertOrOverwriteCustomAudience(
                caWithFilterAd,
                CustomAudienceFixture.getValidDailyUpdateUriByBuyer(BUYER_1),
                false);
        mCustomAudienceDao.insertOrOverwriteCustomAudience(
                mDBCustomAudienceForBuyer2,
                CustomAudienceFixture.getValidDailyUpdateUriByBuyer(BUYER_2),
                false);

        when(mMockFrequencyCapAdFilterer.filterCustomAudiences(
                        Arrays.asList(caWithFilterAd, mDBCustomAudienceForBuyer2)))
                .thenReturn((Arrays.asList(caWithoutFilterAd, mDBCustomAudienceForBuyer2)));

        invokeRunAdSelection(mAdSelectionRunner, adSelectionConfig, MY_APP_PACKAGE_NAME);

        verify(mPerBuyerBiddingRunnerMock)
                .runBidding(
                        BUYER_1,
                        ImmutableList.of(caWithoutFilterAd),
                        flags.getAdSelectionBiddingTimeoutPerBuyerMs(),
                        adSelectionConfig);
        verify(mPerBuyerBiddingRunnerMock)
                .runBidding(
                        BUYER_2,
                        ImmutableList.of(mDBCustomAudienceForBuyer2),
                        flags.getAdSelectionBiddingTimeoutPerBuyerMs(),
                        adSelectionConfig);

        verify(mMockFrequencyCapAdFilterer, times(1))
                .filterCustomAudiences(Arrays.asList(caWithFilterAd, mDBCustomAudienceForBuyer2));
        int expectedNumOfAdsBeforeFiltering =
                caWithFilterAd.getAds().size() + caWithoutFilterAd.getAds().size();
        int expectedNumOfAdsFiltered = 1;
        int expectedNumOfCAsBeforeFiltering = 2;
        int expectedNumOfCAsFiltered = 0;
        verifyLogForAdFilteringForCAs(
                expectedNumOfAdsBeforeFiltering,
                expectedNumOfAdsFiltered,
                expectedNumOfCAsBeforeFiltering,
                expectedNumOfCAsFiltered);
    }

    @Test
    public void testFilterOneAd_appInstall() throws AdServicesException {
        final Flags flags =
                new OnDeviceAdSelectionRunnerTestFlags() {
                    @Override
                    public boolean getFledgeAppInstallFilteringEnabled() {
                        return true;
                    }

                    @Override
                    public boolean getFledgeFrequencyCapFilteringEnabled() {
                        return true;
                    }

                    @Override
                    public boolean getFledgeAppInstallFilteringMetricsEnabled() {
                        return true;
                    }

                    @Override
                    public boolean getFledgeFrequencyCapFilteringMetricsEnabled() {
                        return true;
                    }
                };
        AdSelectionConfig adSelectionConfig = mAdSelectionConfigBuilder.build();
        verifyAndSetupAdFilteringSuccessScenario(adSelectionConfig);
        mAdSelectionRunner =
                new OnDeviceAdSelectionRunner(
                        mCustomAudienceDao,
                        mAdSelectionEntryDaoSpy,
                        mEncryptionKeyDaoMock,
                        mEnrollmentDaoMock,
                        mAdServicesHttpsClient,
                        mLightweightExecutorService,
                        mBackgroundExecutorService,
                        mScheduledExecutor,
                        mMockAdsScoreGenerator,
                        mMockAdSelectionIdGenerator,
                        mClockSpy,
                        mAdServicesLoggerMock,
                        flags,
                        mMockDebugFlags,
                        CALLER_UID,
                        mAdSelectionServiceFilterMock,
                        mAdSelectionExecutionLogger,
                        mPerBuyerBiddingRunnerMock,
                        mFrequencyCapAdFilterer,
                        mAdCounterKeyCopier,
                        mAdCounterHistogramUpdater,
                        mFrequencyCapAdDataValidator,
                        mDebugReportingMock,
                        false,
                        mKAnonSignJoinFactoryMock,
                        mMockAppInstallAdFilterer);
        List<DBAdData> adsToNotFilter =
                DBAdDataFixture.getValidDbAdDataListByBuyer(mDBCustomAudienceForBuyer1.getBuyer());
        FrequencyCapFilters frequencyCapFilters =
                new FrequencyCapFilters.Builder()
                        .setKeyedFrequencyCapsForViewEvents(
                                Collections.singletonList(
                                        new KeyedFrequencyCap.Builder(
                                                        KeyedFrequencyCapFixture.KEY1,
                                                        KeyedFrequencyCapFixture.VALID_COUNT,
                                                        KeyedFrequencyCapFixture.ONE_DAY_DURATION)
                                                .build()))
                        .build();
        DBAdData adToFilter =
                DBAdDataFixture.getValidDbAdDataBuilder()
                        .setAdFilters(
                                new AdFilters.Builder()
                                        .setFrequencyCapFilters(frequencyCapFilters)
                                        .build())
                        .build();

        List<DBAdData> ads = new ArrayList<>(adsToNotFilter);
        ads.add(adToFilter);

        DBCustomAudience caWithFilterAd =
                new DBCustomAudience.Builder(mDBCustomAudienceForBuyer1).setAds(ads).build();

        DBCustomAudience caWithoutFilterAd =
                new DBCustomAudience.Builder(mDBCustomAudienceForBuyer1)
                        .setAds(adsToNotFilter)
                        .build();

        // Populating the Custom Audience DB
        mCustomAudienceDao.insertOrOverwriteCustomAudience(
                caWithFilterAd,
                CustomAudienceFixture.getValidDailyUpdateUriByBuyer(BUYER_1),
                false);
        mCustomAudienceDao.insertOrOverwriteCustomAudience(
                mDBCustomAudienceForBuyer2,
                CustomAudienceFixture.getValidDailyUpdateUriByBuyer(BUYER_2),
                false);

        when(mMockAppInstallAdFilterer.filterCustomAudiences(
                        Arrays.asList(caWithFilterAd, mDBCustomAudienceForBuyer2)))
                .thenReturn((Arrays.asList(caWithoutFilterAd, mDBCustomAudienceForBuyer2)));

        invokeRunAdSelection(mAdSelectionRunner, adSelectionConfig, MY_APP_PACKAGE_NAME);

        verify(mPerBuyerBiddingRunnerMock)
                .runBidding(
                        BUYER_1,
                        ImmutableList.of(caWithoutFilterAd),
                        flags.getAdSelectionBiddingTimeoutPerBuyerMs(),
                        adSelectionConfig);
        verify(mPerBuyerBiddingRunnerMock)
                .runBidding(
                        BUYER_2,
                        ImmutableList.of(mDBCustomAudienceForBuyer2),
                        flags.getAdSelectionBiddingTimeoutPerBuyerMs(),
                        adSelectionConfig);

        verify(mMockAppInstallAdFilterer, times(1))
                .filterCustomAudiences(Arrays.asList(caWithFilterAd, mDBCustomAudienceForBuyer2));
        int expectedNumOfAdsBeforeFiltering =
                caWithFilterAd.getAds().size() + caWithoutFilterAd.getAds().size();
        int expectedNumOfAdsFiltered = 1;
        int expectedNumOfCAsBeforeFiltering = 2;
        int expectedNumOfCAsFiltered = 0;
        verifyLogForAdFilteringForCAs(
                expectedNumOfAdsBeforeFiltering,
                expectedNumOfAdsFiltered,
                expectedNumOfCAsBeforeFiltering,
                expectedNumOfCAsFiltered);
    }

    @Test
    public void testFilterWholeCa() throws AdServicesException {
        final Flags flags =
                new OnDeviceAdSelectionRunnerTestFlags() {
                    @Override
                    public boolean getFledgeAppInstallFilteringEnabled() {
                        return true;
                    }

                    @Override
                    public boolean getFledgeFrequencyCapFilteringEnabled() {
                        return true;
                    }

                    @Override
                    public boolean getFledgeAppInstallFilteringMetricsEnabled() {
                        return true;
                    }

                    @Override
                    public boolean getFledgeFrequencyCapFilteringMetricsEnabled() {
                        return true;
                    }
                };
        AdSelectionConfig adSelectionConfig = mAdSelectionConfigBuilder.build();
        verifyAndSetupAdFilteringSuccessScenario(adSelectionConfig);
        mAdSelectionRunner =
                new OnDeviceAdSelectionRunner(
                        mCustomAudienceDao,
                        mAdSelectionEntryDaoSpy,
                        mEncryptionKeyDaoMock,
                        mEnrollmentDaoMock,
                        mAdServicesHttpsClient,
                        mLightweightExecutorService,
                        mBackgroundExecutorService,
                        mScheduledExecutor,
                        mMockAdsScoreGenerator,
                        mMockAdSelectionIdGenerator,
                        mClockSpy,
                        mAdServicesLoggerMock,
                        flags,
                        mMockDebugFlags,
                        CALLER_UID,
                        mAdSelectionServiceFilterMock,
                        mAdSelectionExecutionLogger,
                        mPerBuyerBiddingRunnerMock,
                        mMockFrequencyCapAdFilterer,
                        mAdCounterKeyCopier,
                        mAdCounterHistogramUpdater,
                        mFrequencyCapAdDataValidator,
                        mDebugReportingMock,
                        false,
                        mKAnonSignJoinFactoryMock,
                        mAppInstallAdFilterer);
        AppInstallFilters appFilters =
                new AppInstallFilters.Builder()
                        .setPackageNames(Collections.singleton(CommonFixture.TEST_PACKAGE_NAME_1))
                        .build();
        DBAdData adToFilter =
                DBAdDataFixture.getValidDbAdDataBuilder()
                        .setAdFilters(
                                new AdFilters.Builder().setAppInstallFilters(appFilters).build())
                        .build();
        List<DBAdData> ads = Collections.singletonList(adToFilter);

        DBCustomAudience caWithFilterAd =
                new DBCustomAudience.Builder(mDBCustomAudienceForBuyer1).setAds(ads).build();

        // Populating the Custom Audience DB
        mCustomAudienceDao.insertOrOverwriteCustomAudience(
                caWithFilterAd,
                CustomAudienceFixture.getValidDailyUpdateUriByBuyer(BUYER_1),
                false);
        mCustomAudienceDao.insertOrOverwriteCustomAudience(
                mDBCustomAudienceForBuyer2,
                CustomAudienceFixture.getValidDailyUpdateUriByBuyer(BUYER_2),
                false);

        when(mMockFrequencyCapAdFilterer.filterCustomAudiences(
                        Arrays.asList(caWithFilterAd, mDBCustomAudienceForBuyer2)))
                .thenReturn((Arrays.asList(mDBCustomAudienceForBuyer2)));

        invokeRunAdSelection(mAdSelectionRunner, adSelectionConfig, MY_APP_PACKAGE_NAME);

        verify(mPerBuyerBiddingRunnerMock, times(1))
                .runBidding(
                        BUYER_2,
                        ImmutableList.of(mDBCustomAudienceForBuyer2),
                        flags.getAdSelectionBiddingTimeoutPerBuyerMs(),
                        adSelectionConfig);
        verify(mPerBuyerBiddingRunnerMock, times(0))
                .runBidding(eq(BUYER_1), any(), anyLong(), any());

        verify(mMockFrequencyCapAdFilterer, times(1))
                .filterCustomAudiences(Arrays.asList(caWithFilterAd, mDBCustomAudienceForBuyer2));
        int expectedNumOfAdsBeforeFiltering =
                caWithFilterAd.getAds().size() + mDBCustomAudienceForBuyer2.getAds().size();
        int expectedNumOfAdsFiltered = caWithFilterAd.getAds().size();
        int expectedNumOfCAsBeforeFiltering = 2;
        int expectedNumOfCAsFiltered = 1;
        verifyLogForAdFilteringForCAs(
                expectedNumOfAdsBeforeFiltering,
                expectedNumOfAdsFiltered,
                expectedNumOfCAsBeforeFiltering,
                expectedNumOfCAsFiltered);
    }

    @Test
    public void testGetDecisionLogic_PreDownloaded()
            throws ExecutionException, InterruptedException, TimeoutException, AdServicesException {
        mAdScoringOutcomeForBuyer1 =
                AdScoringOutcomeFixture.anAdScoringBuilder(BUYER_1, 1.0)
                        .setBiddingLogicJsDownloaded(true)
                        .build();

        AdSelectionConfig adSelectionConfig = mAdSelectionConfigBuilder.build();
        verifyAndSetupCommonSuccessScenario(adSelectionConfig);
        OnDeviceAdSelectionRunner adSelectionRunner =
                new OnDeviceAdSelectionRunner(
                        mCustomAudienceDao,
                        mAdSelectionEntryDaoSpy,
                        mEncryptionKeyDaoMock,
                        mEnrollmentDaoMock,
                        mAdServicesHttpsClient,
                        mLightweightExecutorService,
                        mBackgroundExecutorService,
                        mScheduledExecutor,
                        mMockAdsScoreGenerator,
                        mMockAdSelectionIdGenerator,
                        mClockSpy,
                        mAdServicesLoggerMock,
                        mFakeFlags,
                        mMockDebugFlags,
                        CALLER_UID,
                        mAdSelectionServiceFilterMock,
                        mAdSelectionExecutionLogger,
                        mPerBuyerBiddingRunnerMock,
                        mFrequencyCapAdFilterer,
                        mAdCounterKeyCopier,
                        mAdCounterHistogramUpdater,
                        mFrequencyCapAdDataValidator,
                        mDebugReportingMock,
                        false,
                        mKAnonSignJoinFactoryMock,
                        mAppInstallAdFilterer);

        assertEquals(
                mAdScoringOutcomeForBuyer1.getBiddingLogicJs(),
                adSelectionRunner
                        .getWinnerBiddingLogicJs(mAdScoringOutcomeForBuyer1)
                        .get(500, TimeUnit.MILLISECONDS));
    }

    @Test
    public void testGetDecisionLogic_NotPreDownloaded()
            throws ExecutionException, InterruptedException, TimeoutException, AdServicesException {
        mAdScoringOutcomeForBuyer1 =
                AdScoringOutcomeFixture.anAdScoringBuilder(BUYER_1, 1.0)
                        .setBiddingLogicUri(DECISION_LOGIC_URI)
                        .setBiddingLogicJsDownloaded(false)
                        .build();

        when(mMockHttpClient.fetchPayload(
                        AdServicesHttpClientRequest.builder()
                                .setUri(DECISION_LOGIC_URI)
                                .setUseCache(mFakeFlags.getFledgeHttpJsCachingEnabled())
                                .setDevContext(DevContext.createForDevOptionsDisabled())
                                .build()))
                .thenReturn(
                        Futures.immediateFuture(
                                AdServicesHttpClientResponse.builder()
                                        .setResponseBody(BUYER_DECISION_LOGIC_JS)
                                        .build()));
        AdSelectionConfig adSelectionConfig = mAdSelectionConfigBuilder.build();
        verifyAndSetupCommonSuccessScenario(adSelectionConfig);

        OnDeviceAdSelectionRunner adSelectionRunner =
                new OnDeviceAdSelectionRunner(
                        mCustomAudienceDao,
                        mAdSelectionEntryDaoSpy,
                        mEncryptionKeyDaoMock,
                        mEnrollmentDaoMock,
                        mMockHttpClient,
                        mLightweightExecutorService,
                        mBackgroundExecutorService,
                        mScheduledExecutor,
                        mMockAdsScoreGenerator,
                        mMockAdSelectionIdGenerator,
                        mClockSpy,
                        mAdServicesLoggerMock,
                        mFakeFlags,
                        mMockDebugFlags,
                        CALLER_UID,
                        mAdSelectionServiceFilterMock,
                        mAdSelectionExecutionLogger,
                        mPerBuyerBiddingRunnerMock,
                        mFrequencyCapAdFilterer,
                        mAdCounterKeyCopier,
                        mAdCounterHistogramUpdater,
                        mFrequencyCapAdDataValidator,
                        mDebugReportingMock,
                        false,
                        mKAnonSignJoinFactoryMock,
                        mAppInstallAdFilterer);

        String downloadingDecisionLogic =
                adSelectionRunner
                        .getWinnerBiddingLogicJs(mAdScoringOutcomeForBuyer1)
                        .get(500, TimeUnit.MILLISECONDS);
        assertEquals("", downloadingDecisionLogic);
        verify(mMockHttpClient, times(0))
                .fetchPayload(
                        AdServicesHttpClientRequest.builder()
                                .setUri(DECISION_LOGIC_URI)
                                .setUseCache(true)
                                .setDevContext(DevContext.createForDevOptionsDisabled())
                                .build());
    }

    @Test
    public void testContextualAdsEnabled_success() throws AdServicesException {
        Map<AdTechIdentifier, SignedContextualAds> signedContextualAdsMap = createContextualAds();
        AdSelectionConfig adSelectionConfig =
                mAdSelectionConfigBuilderWithNoBuyers
                        .setPerBuyerSignedContextualAds(signedContextualAdsMap)
                        .build();

        final Flags flags =
                new OnDeviceAdSelectionRunnerTestFlags() {
                    @Override
                    public long getAdSelectionOverallTimeoutMs() {
                        return 300;
                    }

                    @Override
                    public boolean getDisableFledgeEnrollmentCheck() {
                        return true;
                    }

                    @Override
                    public boolean getFledgeAdSelectionContextualAdsEnabled() {
                        return true;
                    }

                    @Override
                    public boolean getFledgeAdSelectionContextualAdsMetricsEnabled() {
                        return true;
                    }
                };

        setAdSelectionExecutionLoggerMockWithContextualAdsAndNoCAs();
        mAdSelectionRunner =
                new OnDeviceAdSelectionRunner(
                        mCustomAudienceDao,
                        mAdSelectionEntryDaoSpy,
                        mEncryptionKeyDaoMock,
                        mEnrollmentDaoMock,
                        mAdServicesHttpsClient,
                        mLightweightExecutorService,
                        mBackgroundExecutorService,
                        mScheduledExecutor,
                        mMockAdsScoreGenerator,
                        mMockAdSelectionIdGenerator,
                        mClockSpy,
                        mAdServicesLoggerMock,
                        flags,
                        mMockDebugFlags,
                        CALLER_UID,
                        mAdSelectionServiceFilterMock,
                        mAdSelectionExecutionLogger,
                        mPerBuyerBiddingRunnerMock,
                        mFrequencyCapAdFilterer,
                        mAdCounterKeyCopier,
                        mAdCounterHistogramUpdater,
                        mFrequencyCapAdDataValidator,
                        mDebugReportingMock,
                        false,
                        mKAnonSignJoinFactoryMock,
                        mAppInstallAdFilterer);

        invokeRunAdSelection(mAdSelectionRunner, adSelectionConfig, MY_APP_PACKAGE_NAME);

        verify(mMockAdsScoreGenerator)
                .runAdScoring(
                        eq(Collections.EMPTY_LIST), mAdSelectionConfigArgumentCaptor.capture());
        assertEquals(
                "The contextual ads should have reached scoring as is",
                signedContextualAdsMap,
                mAdSelectionConfigArgumentCaptor.getValue().getPerBuyerSignedContextualAds());

        int expectedNumOfKeysFetched = 1;
        verifyLogForSuccessfulSignatureVerificationForContextualAds(
                adSelectionConfig.getPerBuyerSignedContextualAds().size(),
                expectedNumOfKeysFetched);
    }

    @Test
    public void testContextualAds_successfulSignatureVerification_loggingDisabled()
            throws AdServicesException {
        Map<AdTechIdentifier, SignedContextualAds> signedContextualAdsMap = createContextualAds();
        AdSelectionConfig adSelectionConfig =
                mAdSelectionConfigBuilderWithNoBuyers
                        .setPerBuyerSignedContextualAds(signedContextualAdsMap)
                        .build();

        final Flags flags =
                new OnDeviceAdSelectionRunnerTestFlags() {
                    @Override
                    public long getAdSelectionOverallTimeoutMs() {
                        return 300;
                    }

                    @Override
                    public boolean getDisableFledgeEnrollmentCheck() {
                        return true;
                    }

                    @Override
                    public boolean getFledgeAdSelectionContextualAdsEnabled() {
                        return true;
                    }

                    @Override
                    public boolean getFledgeAdSelectionContextualAdsMetricsEnabled() {
                        return false;
                    }
                };

        setAdSelectionExecutionLoggerMockWithContextualAdsAndNoCAs();
        mAdSelectionRunner =
                new OnDeviceAdSelectionRunner(
                        mCustomAudienceDao,
                        mAdSelectionEntryDaoSpy,
                        mEncryptionKeyDaoMock,
                        mEnrollmentDaoMock,
                        mAdServicesHttpsClient,
                        mLightweightExecutorService,
                        mBackgroundExecutorService,
                        mScheduledExecutor,
                        mMockAdsScoreGenerator,
                        mMockAdSelectionIdGenerator,
                        mClockSpy,
                        mAdServicesLoggerMock,
                        flags,
                        mMockDebugFlags,
                        CALLER_UID,
                        mAdSelectionServiceFilterMock,
                        mAdSelectionExecutionLogger,
                        mPerBuyerBiddingRunnerMock,
                        mFrequencyCapAdFilterer,
                        mAdCounterKeyCopier,
                        mAdCounterHistogramUpdater,
                        mFrequencyCapAdDataValidator,
                        mDebugReportingMock,
                        false,
                        mKAnonSignJoinFactoryMock,
                        mAppInstallAdFilterer);

        invokeRunAdSelection(mAdSelectionRunner, adSelectionConfig, MY_APP_PACKAGE_NAME);

        verify(mMockAdsScoreGenerator)
                .runAdScoring(
                        eq(Collections.EMPTY_LIST), mAdSelectionConfigArgumentCaptor.capture());
        assertEquals(
                "The contextual ads should have reached scoring as is",
                signedContextualAdsMap,
                mAdSelectionConfigArgumentCaptor.getValue().getPerBuyerSignedContextualAds());
        verify(mAdServicesLoggerMock, times(0))
                .logSignatureVerificationStats(any(SignatureVerificationStats.class));
    }

    @Test
    public void testContextualAds_successfulSignatureVerification_loggingEnabled()
            throws AdServicesException {
        Map<AdTechIdentifier, SignedContextualAds> signedContextualAdsMap = createContextualAds();
        AdSelectionConfig adSelectionConfig =
                mAdSelectionConfigBuilderWithNoBuyers
                        .setPerBuyerSignedContextualAds(signedContextualAdsMap)
                        .build();

        final Flags flags =
                new OnDeviceAdSelectionRunnerTestFlags() {
                    @Override
                    public long getAdSelectionOverallTimeoutMs() {
                        return 300;
                    }

                    @Override
                    public boolean getDisableFledgeEnrollmentCheck() {
                        return true;
                    }

                    @Override
                    public boolean getFledgeAdSelectionContextualAdsEnabled() {
                        return true;
                    }

                    @Override
                    public boolean getFledgeAdSelectionContextualAdsMetricsEnabled() {
                        return true;
                    }
                };

        setAdSelectionExecutionLoggerMockWithContextualAdsAndNoCAs();
        mAdSelectionRunner =
                new OnDeviceAdSelectionRunner(
                        mCustomAudienceDao,
                        mAdSelectionEntryDaoSpy,
                        mEncryptionKeyDaoMock,
                        mEnrollmentDaoMock,
                        mAdServicesHttpsClient,
                        mLightweightExecutorService,
                        mBackgroundExecutorService,
                        mScheduledExecutor,
                        mMockAdsScoreGenerator,
                        mMockAdSelectionIdGenerator,
                        mClockSpy,
                        mAdServicesLoggerMock,
                        flags,
                        mMockDebugFlags,
                        CALLER_UID,
                        mAdSelectionServiceFilterMock,
                        mAdSelectionExecutionLogger,
                        mPerBuyerBiddingRunnerMock,
                        mFrequencyCapAdFilterer,
                        mAdCounterKeyCopier,
                        mAdCounterHistogramUpdater,
                        mFrequencyCapAdDataValidator,
                        mDebugReportingMock,
                        false,
                        mKAnonSignJoinFactoryMock,
                        mAppInstallAdFilterer);

        invokeRunAdSelection(mAdSelectionRunner, adSelectionConfig, MY_APP_PACKAGE_NAME);

        verify(mMockAdsScoreGenerator)
                .runAdScoring(
                        eq(Collections.EMPTY_LIST), mAdSelectionConfigArgumentCaptor.capture());
        assertEquals(
                "The contextual ads should have reached scoring as is",
                signedContextualAdsMap,
                mAdSelectionConfigArgumentCaptor.getValue().getPerBuyerSignedContextualAds());

        int expectedNumOfKeysFetched = 1;
        verifyLogForSuccessfulSignatureVerificationForContextualAds(
                adSelectionConfig.getPerBuyerSignedContextualAds().size(),
                expectedNumOfKeysFetched);
    }

    @Test
    public void testContextualAds_failedSignatureContextualAdsRemovec_loggingEnabled()
            throws AdServicesException {
        Map<AdTechIdentifier, SignedContextualAds> contextualAdsMap = createContextualAds();
        SignedContextualAds contextualAdsWithInvalidSignature =
                new SignedContextualAds.Builder(contextualAdsMap.get(BUYER_2))
                        .setSignature(new byte[] {1, 2, 3})
                        .build();
        contextualAdsMap.put(BUYER_2, contextualAdsWithInvalidSignature);

        AdSelectionConfig adSelectionConfig =
                mAdSelectionConfigBuilderWithNoBuyers
                        .setPerBuyerSignedContextualAds(contextualAdsMap)
                        .build();

        final Flags flags =
                new OnDeviceAdSelectionRunnerTestFlags() {
                    @Override
                    public long getAdSelectionOverallTimeoutMs() {
                        return 300;
                    }

                    @Override
                    public boolean getDisableFledgeEnrollmentCheck() {
                        return true;
                    }

                    @Override
                    public boolean getFledgeAdSelectionContextualAdsEnabled() {
                        return true;
                    }

                    @Override
                    public boolean getFledgeAdSelectionContextualAdsMetricsEnabled() {
                        return false;
                    }

                    @Override
                    public boolean getFledgeAppInstallFilteringMetricsEnabled() {
                        return true;
                    }
                };

        setAdSelectionExecutionLoggerMockWithAdFiltering();
        mAdSelectionRunner =
                new OnDeviceAdSelectionRunner(
                        mCustomAudienceDao,
                        mAdSelectionEntryDaoSpy,
                        mEncryptionKeyDaoMock,
                        mEnrollmentDaoMock,
                        mAdServicesHttpsClient,
                        mLightweightExecutorService,
                        mBackgroundExecutorService,
                        mScheduledExecutor,
                        mMockAdsScoreGenerator,
                        mMockAdSelectionIdGenerator,
                        mClockSpy,
                        mAdServicesLoggerMock,
                        flags,
                        mMockDebugFlags,
                        CALLER_UID,
                        mAdSelectionServiceFilterMock,
                        mAdSelectionExecutionLogger,
                        mPerBuyerBiddingRunnerMock,
                        mFrequencyCapAdFilterer,
                        mAdCounterKeyCopier,
                        mAdCounterHistogramUpdater,
                        mFrequencyCapAdDataValidator,
                        mDebugReportingMock,
                        false,
                        mKAnonSignJoinFactoryMock,
                        mAppInstallAdFilterer);

        invokeRunAdSelection(mAdSelectionRunner, adSelectionConfig, MY_APP_PACKAGE_NAME);

        verify(mMockAdsScoreGenerator)
                .runAdScoring(
                        eq(Collections.EMPTY_LIST), mAdSelectionConfigArgumentCaptor.capture());
        assertEquals(
                "The contextual ad with invalid signature should've removed before scoring",
                Map.of(BUYER_1, contextualAdsMap.get(BUYER_1)),
                mAdSelectionConfigArgumentCaptor.getValue().getPerBuyerSignedContextualAds());

        int numOfContextualAdsBeforeFiltering =
                contextualAdsMap.get(BUYER_1).getAdsWithBid().size();
        int numOfContextualAdsFiltered = 0;
        int numOfContextualAdBundlesFilteredNoAds = 0;
        int numOfContextualAdBundlesFilteredInvalidSignatures = 1;
        verifyLogForAdFilteringForContextualAds(
                numOfContextualAdsBeforeFiltering,
                numOfContextualAdsFiltered,
                numOfContextualAdBundlesFilteredNoAds,
                numOfContextualAdBundlesFilteredInvalidSignatures);
    }

    @Test
    public void testContextualAdsDisabled_contextualAdsRemovedFromAuction() {
        when(mClockSpy.instant()).thenReturn(Clock.systemUTC().instant());
        AdSelectionConfig adSelectionConfig =
                mAdSelectionConfigBuilderWithNoBuyers
                        .setPerBuyerSignedContextualAds(createContextualAds())
                        .build();
        final Flags flags =
                new OnDeviceAdSelectionRunnerTestFlags() {
                    @Override
                    public boolean getDisableFledgeEnrollmentCheck() {
                        return true;
                    }

                    @Override
                    public boolean getFledgeAdSelectionContextualAdsEnabled() {
                        return false;
                    }
                };

        setAdSelectionExecutionLoggerMockWithFailedAdSelectionByNoCAs();
        mAdSelectionRunner =
                new OnDeviceAdSelectionRunner(
                        mCustomAudienceDao,
                        mAdSelectionEntryDaoSpy,
                        mEncryptionKeyDaoMock,
                        mEnrollmentDaoMock,
                        mAdServicesHttpsClient,
                        mLightweightExecutorService,
                        mBackgroundExecutorService,
                        mScheduledExecutor,
                        mMockAdsScoreGenerator,
                        mMockAdSelectionIdGenerator,
                        mClockSpy,
                        mAdServicesLoggerMock,
                        flags,
                        mMockDebugFlags,
                        CALLER_UID,
                        mAdSelectionServiceFilterMock,
                        mAdSelectionExecutionLogger,
                        mPerBuyerBiddingRunnerMock,
                        mFrequencyCapAdFilterer,
                        mAdCounterKeyCopier,
                        mAdCounterHistogramUpdater,
                        mFrequencyCapAdDataValidator,
                        mDebugReportingMock,
                        false,
                        mKAnonSignJoinFactoryMock,
                        mAppInstallAdFilterer);

        AdSelectionTestCallback result =
                invokeRunAdSelection(mAdSelectionRunner, adSelectionConfig, MY_APP_PACKAGE_NAME);

        assertFalse(result.mIsSuccess);
        assertEquals(
                "Contextual Ads should have been flushed and Ad Selection resulted in error",
                result.mFledgeErrorResponse.getErrorMessage(),
                String.format(
                        AD_SELECTION_ERROR_PATTERN,
                        ERROR_AD_SELECTION_FAILURE,
                        ERROR_NO_BUYERS_OR_CONTEXTUAL_ADS_AVAILABLE));
    }

    @Test
    public void testContextualAds_appInstallFilteringEnabled_success() throws AdServicesException {
        Map<AdTechIdentifier, SignedContextualAds> contextualAdsMap = createContextualAds();

        AdSelectionConfig adSelectionConfig =
                mAdSelectionConfigBuilderWithNoBuyers
                        .setPerBuyerSignedContextualAds(contextualAdsMap)
                        .build();

        final Flags flags =
                new OnDeviceAdSelectionRunnerTestFlags() {
                    @Override
                    public boolean getDisableFledgeEnrollmentCheck() {
                        return true;
                    }

                    @Override
                    public boolean getFledgeAppInstallFilteringEnabled() {
                        return true;
                    }

                    @Override
                    public boolean getFledgeAdSelectionContextualAdsEnabled() {
                        return true;
                    }

                    @Override
                    public boolean getFledgeAdSelectionContextualAdsMetricsEnabled() {
                        return false;
                    }

                    @Override
                    public boolean getFledgeAppInstallFilteringMetricsEnabled() {
                        return true;
                    }
                };

        setAdSelectionExecutionLoggerMockWithContextualAdsAndNoCAs();
        mAdSelectionRunner =
                new OnDeviceAdSelectionRunner(
                        mCustomAudienceDao,
                        mAdSelectionEntryDaoSpy,
                        mEncryptionKeyDaoMock,
                        mEnrollmentDaoMock,
                        mAdServicesHttpsClient,
                        mLightweightExecutorService,
                        mBackgroundExecutorService,
                        mScheduledExecutor,
                        mMockAdsScoreGenerator,
                        mMockAdSelectionIdGenerator,
                        mClockSpy,
                        mAdServicesLoggerMock,
                        flags,
                        mMockDebugFlags,
                        CALLER_UID,
                        mAdSelectionServiceFilterMock,
                        mAdSelectionExecutionLogger,
                        mPerBuyerBiddingRunnerMock,
                        mFrequencyCapAdFilterer,
                        mAdCounterKeyCopier,
                        mAdCounterHistogramUpdater,
                        mFrequencyCapAdDataValidator,
                        mDebugReportingMock,
                        false,
                        mKAnonSignJoinFactoryMock,
                        mMockAppInstallAdFilterer);

        when(mMockAppInstallAdFilterer.filterContextualAds(
                        contextualAdsMap.get(CommonFixture.VALID_BUYER_1)))
                .thenReturn(contextualAdsMap.get(CommonFixture.VALID_BUYER_1));
        when(mMockAppInstallAdFilterer.filterContextualAds(
                        contextualAdsMap.get(CommonFixture.VALID_BUYER_2)))
                .thenReturn(
                        signContextualAds(
                                SignedContextualAdsFixture.aContextualAdsWithEmptySignatureBuilder()
                                        .setBuyer(CommonFixture.VALID_BUYER_2)
                                        .setDecisionLogicUri(
                                                contextualAdsMap
                                                        .get(CommonFixture.VALID_BUYER_2)
                                                        .getDecisionLogicUri())
                                        .setAdsWithBid(Collections.emptyList())));
        invokeRunAdSelection(mAdSelectionRunner, adSelectionConfig, MY_APP_PACKAGE_NAME);
        verify(mMockAdsScoreGenerator)
                .runAdScoring(
                        eq(Collections.EMPTY_LIST), mAdSelectionConfigArgumentCaptor.capture());
        assertEquals(
                "The contextual ads should have remained same for Buyer 1",
                contextualAdsMap.get(CommonFixture.VALID_BUYER_1).getAdsWithBid(),
                mAdSelectionConfigArgumentCaptor
                        .getValue()
                        .getPerBuyerSignedContextualAds()
                        .get(CommonFixture.VALID_BUYER_1)
                        .getAdsWithBid());
        assertFalse(
                "The contextual ads should have been filtered for Buyer 2",
                mAdSelectionConfigArgumentCaptor
                        .getValue()
                        .getPerBuyerSignedContextualAds()
                        .containsKey(CommonFixture.VALID_BUYER_2));

        int numOfContextualAdsBeforeFiltering =
                SignedContextualAdsFixture.countAdsIn(contextualAdsMap);
        int numOfContextualAdsFiltered = contextualAdsMap.get(BUYER_2).getAdsWithBid().size();
        int numOfContextualAdBundlesFilteredNoAds = 1;
        int numOfContextualAdBundlesFilteredInvalidSignatures = 0;
        verifyLogForAdFilteringForContextualAds(
                numOfContextualAdsBeforeFiltering,
                numOfContextualAdsFiltered,
                numOfContextualAdBundlesFilteredNoAds,
                numOfContextualAdBundlesFilteredInvalidSignatures);
    }

    @Test
    public void testCopiedAdCounterKeysArePersisted() throws AdServicesException {
        AdSelectionConfig adSelectionConfig = mAdSelectionConfigBuilder.build();
        verifyAndSetupCommonSuccessScenario(adSelectionConfig);
        mAdSelectionRunner =
                new OnDeviceAdSelectionRunner(
                        mCustomAudienceDao,
                        mAdSelectionEntryDaoSpy,
                        mEncryptionKeyDaoMock,
                        mEnrollmentDaoMock,
                        mAdServicesHttpsClient,
                        mLightweightExecutorService,
                        mBackgroundExecutorService,
                        mScheduledExecutor,
                        mMockAdsScoreGenerator,
                        mMockAdSelectionIdGenerator,
                        mClockSpy,
                        mAdServicesLoggerMock,
                        mFakeFlags,
                        mMockDebugFlags,
                        CALLER_UID,
                        mAdSelectionServiceFilterMock,
                        mAdSelectionExecutionLogger,
                        mPerBuyerBiddingRunnerMock,
                        mFrequencyCapAdFilterer,
                        mAdCounterKeyCopierMock,
                        mAdCounterHistogramUpdater,
                        mFrequencyCapAdDataValidatorMock,
                        mDebugReportingMock,
                        false,
                        mKAnonSignJoinFactoryMock,
                        mAppInstallAdFilterer);

        // Populating the Custom Audience DB
        mCustomAudienceDao.insertOrOverwriteCustomAudience(
                mDBCustomAudienceForBuyer1,
                CustomAudienceFixture.getValidDailyUpdateUriByBuyer(BUYER_1),
                false);
        mCustomAudienceDao.insertOrOverwriteCustomAudience(
                mDBCustomAudienceForBuyer2,
                CustomAudienceFixture.getValidDailyUpdateUriByBuyer(BUYER_2),
                false);

        DBAdSelection.Builder dbAdSelectionBuilder =
                new DBAdSelection.Builder()
                        .setWinningAdBid(
                                mAdScoringOutcomeForBuyer1.getAdWithScore().getAdWithBid().getBid())
                        .setCustomAudienceSignals(
                                mAdScoringOutcomeForBuyer1.getCustomAudienceSignals())
                        .setWinningAdRenderUri(
                                mAdScoringOutcomeForBuyer1
                                        .getAdWithScore()
                                        .getAdWithBid()
                                        .getAdData()
                                        .getRenderUri())
                        .setBiddingLogicUri(mAdScoringOutcomeForBuyer1.getBiddingLogicUri())
                        .setBuyerContextualSignals("{}")
                        .setSellerContextualSignals("{}")
                        .setAdCounterIntKeys(AdDataFixture.getAdCounterKeys());

        doReturn(dbAdSelectionBuilder)
                .when(mAdCounterKeyCopierMock)
                .copyAdCounterKeys(any(DBAdSelection.Builder.class), any(AdScoringOutcome.class));

        AdSelectionTestCallback callback =
                invokeRunAdSelection(mAdSelectionRunner, adSelectionConfig, MY_APP_PACKAGE_NAME);

        assertThat(callback.mIsSuccess).isTrue();

        verify(mPerBuyerBiddingRunnerMock)
                .runBidding(
                        BUYER_1,
                        ImmutableList.of(mDBCustomAudienceForBuyer1),
                        mFakeFlags.getAdSelectionBiddingTimeoutPerBuyerMs(),
                        adSelectionConfig);

        verify(mAdCounterKeyCopierMock)
                .copyAdCounterKeys(any(DBAdSelection.Builder.class), any(AdScoringOutcome.class));

        assertTrue(
                mAdSelectionEntryDaoSpy.doesAdSelectionIdExist(
                        callback.mAdSelectionResponse.getAdSelectionId()));

        DBAdSelectionHistogramInfo histogramInfo =
                mAdSelectionEntryDaoSpy.getAdSelectionHistogramInfoInOnDeviceTable(
                        callback.mAdSelectionResponse.getAdSelectionId(), MY_APP_PACKAGE_NAME);
        assertThat(histogramInfo).isNotNull();
        assertThat(histogramInfo.getBuyer()).isEqualTo(BUYER_1);
        assertThat(histogramInfo.getAdCounterKeys()).isNotNull();
        assertThat(histogramInfo.getAdCounterKeys())
                .containsExactlyElementsIn(AdDataFixture.getAdCounterKeys());
    }

    @Test
    public void testAdCounterHistogramIsUpdatedWithWinEvent() throws Exception {
        AdSelectionConfig adSelectionConfig = mAdSelectionConfigBuilder.build();
        verifyAndSetupCommonSuccessScenario(adSelectionConfig);
        mAdSelectionRunner =
                new OnDeviceAdSelectionRunner(
                        mCustomAudienceDao,
                        mAdSelectionEntryDaoSpy,
                        mEncryptionKeyDaoMock,
                        mEnrollmentDaoMock,
                        mAdServicesHttpsClient,
                        mLightweightExecutorService,
                        mBackgroundExecutorService,
                        mScheduledExecutor,
                        mMockAdsScoreGenerator,
                        mMockAdSelectionIdGenerator,
                        mClockSpy,
                        mAdServicesLoggerMock,
                        mFakeFlags,
                        mMockDebugFlags,
                        CALLER_UID,
                        mAdSelectionServiceFilterMock,
                        mAdSelectionExecutionLogger,
                        mPerBuyerBiddingRunnerMock,
                        mFrequencyCapAdFilterer,
                        mAdCounterKeyCopierMock,
                        mAdCounterHistogramUpdaterMock,
                        mFrequencyCapAdDataValidator,
                        mDebugReportingMock,
                        false,
                        mKAnonSignJoinFactoryMock,
                        mAppInstallAdFilterer);

        // Populating the Custom Audience DB
        mCustomAudienceDao.insertOrOverwriteCustomAudience(
                mDBCustomAudienceForBuyer1,
                CustomAudienceFixture.getValidDailyUpdateUriByBuyer(BUYER_1),
                false);
        mCustomAudienceDao.insertOrOverwriteCustomAudience(
                mDBCustomAudienceForBuyer2,
                CustomAudienceFixture.getValidDailyUpdateUriByBuyer(BUYER_2),
                false);

        DBAdSelection.Builder dbAdSelectionBuilder =
                new DBAdSelection.Builder()
                        .setWinningAdBid(
                                mAdScoringOutcomeForBuyer1.getAdWithScore().getAdWithBid().getBid())
                        .setCustomAudienceSignals(
                                mAdScoringOutcomeForBuyer1.getCustomAudienceSignals())
                        .setWinningAdRenderUri(
                                mAdScoringOutcomeForBuyer1
                                        .getAdWithScore()
                                        .getAdWithBid()
                                        .getAdData()
                                        .getRenderUri())
                        .setBiddingLogicUri(mAdScoringOutcomeForBuyer1.getBiddingLogicUri())
                        .setBuyerContextualSignals("{}")
                        .setAdCounterIntKeys(AdDataFixture.getAdCounterKeys());

        // Note that regardless of the input, this copier stubs the actual output of the auction
        doReturn(dbAdSelectionBuilder)
                .when(mAdCounterKeyCopierMock)
                .copyAdCounterKeys(any(DBAdSelection.Builder.class), any(AdScoringOutcome.class));

        AdSelectionTestCallback callback =
                invokeRunAdSelection(mAdSelectionRunner, adSelectionConfig, MY_APP_PACKAGE_NAME);

        assertThat(callback.mIsSuccess).isTrue();

        verify(mPerBuyerBiddingRunnerMock)
                .runBidding(
                        BUYER_1,
                        ImmutableList.of(mDBCustomAudienceForBuyer1),
                        mFakeFlags.getAdSelectionBiddingTimeoutPerBuyerMs(),
                        adSelectionConfig);

        verify(mAdCounterKeyCopierMock)
                .copyAdCounterKeys(any(DBAdSelection.Builder.class), any(AdScoringOutcome.class));

        verify(mAdCounterHistogramUpdaterMock).updateWinHistogram(eq(dbAdSelectionBuilder.build()));

        assertTrue(
                mAdSelectionEntryDaoSpy.doesAdSelectionIdExist(
                        callback.mAdSelectionResponse.getAdSelectionId()));
    }

    @Test
    public void testFailedAdCounterHistogramWinUpdateDoesNotStopAdSelection() throws Exception {
        AdSelectionConfig adSelectionConfig = mAdSelectionConfigBuilder.build();
        verifyAndSetupCommonSuccessScenario(adSelectionConfig);
        mAdSelectionRunner =
                new OnDeviceAdSelectionRunner(
                        mCustomAudienceDao,
                        mAdSelectionEntryDaoSpy,
                        mEncryptionKeyDaoMock,
                        mEnrollmentDaoMock,
                        mAdServicesHttpsClient,
                        mLightweightExecutorService,
                        mBackgroundExecutorService,
                        mScheduledExecutor,
                        mMockAdsScoreGenerator,
                        mMockAdSelectionIdGenerator,
                        mClockSpy,
                        mAdServicesLoggerMock,
                        mFakeFlags,
                        mMockDebugFlags,
                        CALLER_UID,
                        mAdSelectionServiceFilterMock,
                        mAdSelectionExecutionLogger,
                        mPerBuyerBiddingRunnerMock,
                        mFrequencyCapAdFilterer,
                        mAdCounterKeyCopier,
                        mAdCounterHistogramUpdaterMock,
                        mFrequencyCapAdDataValidator,
                        mDebugReportingMock,
                        false,
                        mKAnonSignJoinFactoryMock,
                        mAppInstallAdFilterer);

        // Populating the Custom Audience DB
        mCustomAudienceDao.insertOrOverwriteCustomAudience(
                mDBCustomAudienceForBuyer1,
                CustomAudienceFixture.getValidDailyUpdateUriByBuyer(BUYER_1),
                false);
        mCustomAudienceDao.insertOrOverwriteCustomAudience(
                mDBCustomAudienceForBuyer2,
                CustomAudienceFixture.getValidDailyUpdateUriByBuyer(BUYER_2),
                false);

        doThrow(new RuntimeException("Failing ad counter histogram update for test"))
                .when(mAdCounterHistogramUpdaterMock)
                .updateWinHistogram(any());

        AdSelectionTestCallback callback =
                invokeRunAdSelection(mAdSelectionRunner, adSelectionConfig, MY_APP_PACKAGE_NAME);

        assertThat(callback.mIsSuccess).isTrue();

        verify(mPerBuyerBiddingRunnerMock)
                .runBidding(
                        BUYER_1,
                        ImmutableList.of(mDBCustomAudienceForBuyer1),
                        mFakeFlags.getAdSelectionBiddingTimeoutPerBuyerMs(),
                        adSelectionConfig);

        verify(mAdCounterHistogramUpdaterMock).updateWinHistogram(any());

        assertTrue(
                mAdSelectionEntryDaoSpy.doesAdSelectionIdExist(
                        callback.mAdSelectionResponse.getAdSelectionId()));
    }

    @Test
    public void testRunAdSelection_withKAnonSignJoinEnabled_success() throws AdServicesException {
        AdSelectionConfig adSelectionConfig = mAdSelectionConfigBuilder.build();
        doReturn(mKAnonSignJoinManagerMock)
                .when(mKAnonSignJoinFactoryMock)
                .getKAnonSignJoinManager();
        Flags flagsWithKAnonEnabled = new FlagsWithKAnonEnabled();
        verifyAndSetupCommonSuccessScenario(adSelectionConfig);
        mAdSelectionRunner =
                new OnDeviceAdSelectionRunner(
                        mCustomAudienceDao,
                        mAdSelectionEntryDaoSpy,
                        mEncryptionKeyDaoMock,
                        mEnrollmentDaoMock,
                        mAdServicesHttpsClient,
                        mLightweightExecutorService,
                        mBackgroundExecutorService,
                        mScheduledExecutor,
                        mMockAdsScoreGenerator,
                        mMockAdSelectionIdGenerator,
                        mClockSpy,
                        mAdServicesLoggerMock,
                        flagsWithKAnonEnabled,
                        mMockDebugFlags,
                        CALLER_UID,
                        mAdSelectionServiceFilterMock,
                        mAdSelectionExecutionLogger,
                        mPerBuyerBiddingRunnerMock,
                        mFrequencyCapAdFilterer,
                        mAdCounterKeyCopier,
                        mAdCounterHistogramUpdater,
                        mFrequencyCapAdDataValidator,
                        mDebugReportingMock,
                        false,
                        mKAnonSignJoinFactoryMock,
                        mAppInstallAdFilterer);
        // Populating the Custom Audience DB
        mCustomAudienceDao.insertOrOverwriteCustomAudience(
                mDBCustomAudienceForBuyer1,
                CustomAudienceFixture.getValidDailyUpdateUriByBuyer(BUYER_1),
                false);
        mCustomAudienceDao.insertOrOverwriteCustomAudience(
                mDBCustomAudienceForBuyer2,
                CustomAudienceFixture.getValidDailyUpdateUriByBuyer(BUYER_2),
                false);
        Instant adSelectionCreationTs = Clock.systemUTC().instant().truncatedTo(ChronoUnit.MILLIS);
        when(mClockSpy.instant()).thenReturn(adSelectionCreationTs);
        DBAdSelectionEntry expectedAdSelectionResult =
                new DBAdSelectionEntry.Builder()
                        .setAdSelectionId(AD_SELECTION_ID)
                        .setBiddingLogicUri(mDBCustomAudienceForBuyer2.getBiddingLogicUri())
                        .setWinningAdBid(
                                mAdScoringOutcomeForBuyer2.getAdWithScore().getAdWithBid().getBid())
                        .setCustomAudienceSignals(
                                mAdScoringOutcomeForBuyer2.getCustomAudienceSignals())
                        .setWinningAdRenderUri(
                                mAdScoringOutcomeForBuyer2
                                        .getAdWithScore()
                                        .getAdWithBid()
                                        .getAdData()
                                        .getRenderUri())
                        .setBuyerDecisionLogicJs(
                                mAdBiddingOutcomeForBuyer1
                                        .getCustomAudienceBiddingInfo()
                                        .getBuyerDecisionLogicJs())
                        // TODO(b/230569187) add contextual signals once supported in the main logic
                        .setBuyerContextualSignals("{}")
                        .setSellerContextualSignals("{}")
                        .setCreationTimestamp(adSelectionCreationTs)
                        .build();

        CountDownLatch countDownLatch = new CountDownLatch(1);
        AdSelectionTestCallback adSelectionFullTestCallback =
                new AdSelectionTestCallback(countDownLatch);

        AdSelectionTestCallback resultsCallback =
                invokeRunAdSelectionWithFullCallBack(
                        mAdSelectionRunner,
                        adSelectionConfig,
                        MY_APP_PACKAGE_NAME,
                        adSelectionFullTestCallback);

        verify(mPerBuyerBiddingRunnerMock)
                .runBidding(
                        BUYER_1,
                        ImmutableList.of(mDBCustomAudienceForBuyer1),
                        mFakeFlags.getAdSelectionBiddingTimeoutPerBuyerMs(),
                        adSelectionConfig);
        verify(mPerBuyerBiddingRunnerMock)
                .runBidding(
                        BUYER_2,
                        ImmutableList.of(mDBCustomAudienceForBuyer2),
                        mFakeFlags.getAdSelectionBiddingTimeoutPerBuyerMs(),
                        adSelectionConfig);

        verify(mMockAdsScoreGenerator).runAdScoring(mAdBiddingOutcomeList, adSelectionConfig);

        assertTrue(resultsCallback.mIsSuccess);
        assertEquals(
                expectedAdSelectionResult.getAdSelectionId(),
                resultsCallback.mAdSelectionResponse.getAdSelectionId());
        assertEquals(
                expectedAdSelectionResult.getWinningAdRenderUri(),
                resultsCallback.mAdSelectionResponse.getRenderUri());
        assertTrue(mAdSelectionEntryDaoSpy.doesAdSelectionIdExist(AD_SELECTION_ID));
        assertEquals(
                expectedAdSelectionResult,
                mAdSelectionEntryDaoSpy.getAdSelectionEntityById(AD_SELECTION_ID));
        verifyLogForSuccessfulBiddingProcess(mAdBiddingOutcomeList);
        verifyLogForSuccessfulAdSelectionProcess();
        verifyDebugReportingWasNotCalled();
        verify(mAdServicesLoggerMock)
                .logFledgeApiCallStats(
                        eq(AD_SERVICES_API_CALLED__API_NAME__SELECT_ADS),
                        eq(TEST_PACKAGE_NAME),
                        eq(STATUS_SUCCESS),
                        eq(RUN_AD_SELECTION_OVERALL_LATENCY_MS));
        verify(mKAnonSignJoinManagerMock, times(1))
                .processNewMessages(kanonMessageEntitiesCaptor.capture());
        List<KAnonMessageEntity> kAnonMessageEntities = kanonMessageEntitiesCaptor.getValue();
        assertThat(kAnonMessageEntities.size()).isEqualTo(1);
        assertThat(kAnonMessageEntities.get(0).getAdSelectionId())
                .isEqualTo(resultsCallback.mAdSelectionResponse.getAdSelectionId());
    }

    @Test
    public void testRunAdSelection_kAnonSignJoinCrashes_doesntAffectOtherPaths()
            throws AdServicesException {
        AdSelectionConfig adSelectionConfig = mAdSelectionConfigBuilder.build();
        doReturn(mKAnonSignJoinManagerMock)
                .when(mKAnonSignJoinFactoryMock)
                .getKAnonSignJoinManager();
        doThrow(new RuntimeException())
                .when(mKAnonSignJoinManagerMock)
                .processNewMessages(anyList());
        Flags flagsWithKAnonEnabled = new FlagsWithKAnonEnabled();
        verifyAndSetupCommonSuccessScenario(adSelectionConfig);
        mAdSelectionRunner =
                new OnDeviceAdSelectionRunner(
                        mCustomAudienceDao,
                        mAdSelectionEntryDaoSpy,
                        mEncryptionKeyDaoMock,
                        mEnrollmentDaoMock,
                        mAdServicesHttpsClient,
                        mLightweightExecutorService,
                        mBackgroundExecutorService,
                        mScheduledExecutor,
                        mMockAdsScoreGenerator,
                        mMockAdSelectionIdGenerator,
                        mClockSpy,
                        mAdServicesLoggerMock,
                        flagsWithKAnonEnabled,
                        mMockDebugFlags,
                        CALLER_UID,
                        mAdSelectionServiceFilterMock,
                        mAdSelectionExecutionLogger,
                        mPerBuyerBiddingRunnerMock,
                        mFrequencyCapAdFilterer,
                        mAdCounterKeyCopier,
                        mAdCounterHistogramUpdater,
                        mFrequencyCapAdDataValidator,
                        mDebugReportingMock,
                        false,
                        mKAnonSignJoinFactoryMock,
                        mAppInstallAdFilterer);
        // Populating the Custom Audience DB
        mCustomAudienceDao.insertOrOverwriteCustomAudience(
                mDBCustomAudienceForBuyer1,
                CustomAudienceFixture.getValidDailyUpdateUriByBuyer(BUYER_1),
                false);
        mCustomAudienceDao.insertOrOverwriteCustomAudience(
                mDBCustomAudienceForBuyer2,
                CustomAudienceFixture.getValidDailyUpdateUriByBuyer(BUYER_2),
                false);
        Instant adSelectionCreationTs = Clock.systemUTC().instant().truncatedTo(ChronoUnit.MILLIS);
        when(mClockSpy.instant()).thenReturn(adSelectionCreationTs);
        DBAdSelectionEntry expectedAdSelectionResult =
                new DBAdSelectionEntry.Builder()
                        .setAdSelectionId(AD_SELECTION_ID)
                        .setBiddingLogicUri(mDBCustomAudienceForBuyer2.getBiddingLogicUri())
                        .setWinningAdBid(
                                mAdScoringOutcomeForBuyer2.getAdWithScore().getAdWithBid().getBid())
                        .setCustomAudienceSignals(
                                mAdScoringOutcomeForBuyer2.getCustomAudienceSignals())
                        .setWinningAdRenderUri(
                                mAdScoringOutcomeForBuyer2
                                        .getAdWithScore()
                                        .getAdWithBid()
                                        .getAdData()
                                        .getRenderUri())
                        .setBuyerDecisionLogicJs(
                                mAdBiddingOutcomeForBuyer1
                                        .getCustomAudienceBiddingInfo()
                                        .getBuyerDecisionLogicJs())
                        // TODO(b/230569187) add contextual signals once supported in the main logic
                        .setBuyerContextualSignals("{}")
                        .setSellerContextualSignals("{}")
                        .setCreationTimestamp(adSelectionCreationTs)
                        .build();

        CountDownLatch countDownLatch = new CountDownLatch(1);
        AdSelectionTestCallback adSelectionFullTestCallback =
                new AdSelectionTestCallback(countDownLatch);

        AdSelectionTestCallback resultsCallback =
                invokeRunAdSelectionWithFullCallBack(
                        mAdSelectionRunner,
                        adSelectionConfig,
                        MY_APP_PACKAGE_NAME,
                        adSelectionFullTestCallback);

        verify(mPerBuyerBiddingRunnerMock)
                .runBidding(
                        BUYER_1,
                        ImmutableList.of(mDBCustomAudienceForBuyer1),
                        mFakeFlags.getAdSelectionBiddingTimeoutPerBuyerMs(),
                        adSelectionConfig);
        verify(mPerBuyerBiddingRunnerMock)
                .runBidding(
                        BUYER_2,
                        ImmutableList.of(mDBCustomAudienceForBuyer2),
                        mFakeFlags.getAdSelectionBiddingTimeoutPerBuyerMs(),
                        adSelectionConfig);

        verify(mMockAdsScoreGenerator).runAdScoring(mAdBiddingOutcomeList, adSelectionConfig);

        assertTrue(resultsCallback.mIsSuccess);
        assertEquals(
                expectedAdSelectionResult.getAdSelectionId(),
                resultsCallback.mAdSelectionResponse.getAdSelectionId());
        assertEquals(
                expectedAdSelectionResult.getWinningAdRenderUri(),
                resultsCallback.mAdSelectionResponse.getRenderUri());
        assertTrue(mAdSelectionEntryDaoSpy.doesAdSelectionIdExist(AD_SELECTION_ID));
        assertEquals(
                expectedAdSelectionResult,
                mAdSelectionEntryDaoSpy.getAdSelectionEntityById(AD_SELECTION_ID));
        verifyLogForSuccessfulBiddingProcess(mAdBiddingOutcomeList);
        verifyLogForSuccessfulAdSelectionProcess();
        verifyDebugReportingWasNotCalled();
        verify(mAdServicesLoggerMock)
                .logFledgeApiCallStats(
                        eq(AD_SERVICES_API_CALLED__API_NAME__SELECT_ADS),
                        eq(TEST_PACKAGE_NAME),
                        eq(STATUS_SUCCESS),
                        eq(RUN_AD_SELECTION_OVERALL_LATENCY_MS));
        verify(mKAnonSignJoinManagerMock, times(1))
                .processNewMessages(kanonMessageEntitiesCaptor.capture());
        List<KAnonMessageEntity> kAnonMessageEntities = kanonMessageEntitiesCaptor.getValue();
        assertThat(kAnonMessageEntities.size()).isEqualTo(1);
        assertThat(kAnonMessageEntities.get(0).getAdSelectionId())
                .isEqualTo(resultsCallback.mAdSelectionResponse.getAdSelectionId());
    }

    private void verifyErrorMessageIsCorrect(
            final String actualErrorMassage, final String expectedErrorReason) {
        Assert.assertTrue(
                String.format(
                        "Actual error [%s] does not begin with [%s]",
                        actualErrorMassage, ERROR_AD_SELECTION_FAILURE),
                actualErrorMassage.startsWith(ERROR_AD_SELECTION_FAILURE));
        Assert.assertTrue(
                String.format(
                        "Actual error [%s] does not contain expected message: [%s]",
                        actualErrorMassage, expectedErrorReason),
                actualErrorMassage.contains(expectedErrorReason));
    }

    private AdSelectionTestCallback invokeRunAdSelection(
            AdSelectionRunner adSelectionRunner,
            AdSelectionConfig adSelectionConfig,
            String callerPackageName) {
        return invokeRunAdSelectionWithFullCallBack(
                adSelectionRunner, adSelectionConfig, callerPackageName, null);
    }

    private AdSelectionTestCallback invokeRunAdSelectionWithFullCallBack(
            AdSelectionRunner adSelectionRunner,
            AdSelectionConfig adSelectionConfig,
            String callerPackageName,
            AdSelectionTestCallback fullCallback) {

        // Counted down in 1) callback and 2) logApiCall
        CountDownLatch countDownLatch = new CountDownLatch(1);
        AdSelectionTestCallback adSelectionTestCallback =
                new AdSelectionTestCallback(countDownLatch);

        AdSelectionInput input =
                new AdSelectionInput.Builder()
                        .setAdSelectionConfig(adSelectionConfig)
                        .setCallerPackageName(callerPackageName)
                        .build();

        adSelectionRunner.runAdSelection(
                input,
                adSelectionTestCallback,
                DevContext.createForDevOptionsDisabled(),
                fullCallback);
        try {
            adSelectionTestCallback.mCountDownLatch.await();
            if (fullCallback != null) {
                fullCallback.mCountDownLatch.await();
            }
        } catch (InterruptedException e) {
            e.printStackTrace();
        }
        return adSelectionTestCallback;
    }

    @After
    public void tearDown() {
        if (mAdSelectionEntryDaoSpy != null) {
            mAdSelectionEntryDaoSpy.removeAdSelectionEntriesByIds(Arrays.asList(AD_SELECTION_ID));
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

    private void setAdSelectionExecutionLoggerMockWithSuccessAdSelection() {
        when(mLoggerClockMock.elapsedRealtime())
                .thenReturn(
                        START_ELAPSED_TIMESTAMP, // For AdSelectionExecutionLogger
                        BIDDING_STAGE_START_TIMESTAMP,
                        GET_BUYERS_CUSTOM_AUDIENCE_END_TIMESTAMP,
                        RUN_AD_BIDDING_START_TIMESTAMP,
                        RUN_AD_BIDDING_END_TIMESTAMP,
                        PERSIST_AD_SELECTION_START_TIMESTAMP,
                        PERSIST_AD_SELECTION_END_TIMESTAMP,
                        STOP_ELAPSED_TIMESTAMP);
        mAdSelectionExecutionLogger =
                new AdSelectionExecutionLogger(
                        sCallerMetadata,
                        mLoggerClockMock,
                        mSpyContext,
                        mAdServicesLoggerMock,
                        mFakeFlags);
    }

    private void setAdSelectionExecutionLoggerMockWithFailedAdSelectionByValidateRequest() {
        when(mLoggerClockMock.elapsedRealtime())
                .thenReturn(START_ELAPSED_TIMESTAMP, STOP_ELAPSED_TIMESTAMP);
        mAdSelectionExecutionLogger =
                new AdSelectionExecutionLogger(
                        sCallerMetadata,
                        mLoggerClockMock,
                        mSpyContext,
                        mAdServicesLoggerMock,
                        mFakeFlags);
    }

    private void setAdSelectionExecutionLoggerMockWithFailedAdSelectionByNoCAs() {
        when(mLoggerClockMock.elapsedRealtime())
                .thenReturn(
                        START_ELAPSED_TIMESTAMP, // For AdSelectionExecutionLogger
                        BIDDING_STAGE_START_TIMESTAMP,
                        BIDDING_STAGE_END_TIMESTAMP,
                        STOP_ELAPSED_TIMESTAMP);
        mAdSelectionExecutionLogger =
                new AdSelectionExecutionLogger(
                        sCallerMetadata,
                        mLoggerClockMock,
                        mSpyContext,
                        mAdServicesLoggerMock,
                        mFakeFlags);
    }

    private void setAdSelectionExecutionLoggerMockWithFailedAdSelectionByNoBiddingOutcomes() {
        when(mLoggerClockMock.elapsedRealtime())
                .thenReturn(
                        START_ELAPSED_TIMESTAMP, // For AdSelectionExecutionLogger
                        BIDDING_STAGE_START_TIMESTAMP,
                        GET_BUYERS_CUSTOM_AUDIENCE_END_TIMESTAMP,
                        RUN_AD_BIDDING_START_TIMESTAMP,
                        RUN_AD_BIDDING_END_TIMESTAMP,
                        STOP_ELAPSED_TIMESTAMP);
        mAdSelectionExecutionLogger =
                new AdSelectionExecutionLogger(
                        sCallerMetadata,
                        mLoggerClockMock,
                        mSpyContext,
                        mAdServicesLoggerMock,
                        mFakeFlags);
    }

    private void setAdSelectionExecutionLoggerMockWithContextualAdsAndNoCAs() {
        when(mLoggerClockMock.elapsedRealtime())
                .thenReturn(
                        START_ELAPSED_TIMESTAMP, // For AdSelectionExecutionLogger
                        SIGNATURE_VERIFICATION_START_KEY_FETCH,
                        SIGNATURE_VERIFICATION_END_KEY_FETCH,
                        SIGNATURE_VERIFICATION_START_SERIALIZATION,
                        SIGNATURE_VERIFICATION_END_SERIALIZATION,
                        SIGNATURE_VERIFICATION_START_VERIFICATION,
                        SIGNATURE_VERIFICATION_END_VERIFICATION,
                        SIGNATURE_VERIFICATION_START_KEY_FETCH,
                        SIGNATURE_VERIFICATION_END_KEY_FETCH,
                        SIGNATURE_VERIFICATION_START_SERIALIZATION,
                        SIGNATURE_VERIFICATION_END_SERIALIZATION,
                        SIGNATURE_VERIFICATION_START_VERIFICATION,
                        SIGNATURE_VERIFICATION_END_VERIFICATION,
                        BIDDING_STAGE_START_TIMESTAMP,
                        GET_BUYERS_CUSTOM_AUDIENCE_END_TIMESTAMP,
                        RUN_AD_BIDDING_START_TIMESTAMP,
                        RUN_AD_BIDDING_END_TIMESTAMP,
                        STOP_ELAPSED_TIMESTAMP);
        mAdSelectionExecutionLogger =
                new AdSelectionExecutionLogger(
                        sCallerMetadata,
                        mLoggerClockMock,
                        mSpyContext,
                        mAdServicesLoggerMock,
                        mFakeFlags);
    }

    // TODO(b/221861861): add SCORING TIMESTAMP.
    private void setAdSelectionExecutionLoggerMockWithFailedAdSelectionDuringScoring() {
        when(mLoggerClockMock.elapsedRealtime())
                .thenReturn(
                        START_ELAPSED_TIMESTAMP, // For AdSelectionExecutionLogger
                        BIDDING_STAGE_START_TIMESTAMP,
                        GET_BUYERS_CUSTOM_AUDIENCE_END_TIMESTAMP,
                        RUN_AD_BIDDING_START_TIMESTAMP,
                        RUN_AD_BIDDING_END_TIMESTAMP,
                        STOP_ELAPSED_TIMESTAMP);
        mAdSelectionExecutionLogger =
                new AdSelectionExecutionLogger(
                        sCallerMetadata,
                        mLoggerClockMock,
                        mSpyContext,
                        mAdServicesLoggerMock,
                        mFakeFlags);
    }

    private void setAdSelectionExecutionLoggerMockWithFailedAdSelectionBeforePersistAdSelection() {
        when(mLoggerClockMock.elapsedRealtime())
                .thenReturn(
                        START_ELAPSED_TIMESTAMP, // For AdSelectionExecutionLogger
                        BIDDING_STAGE_START_TIMESTAMP,
                        GET_BUYERS_CUSTOM_AUDIENCE_END_TIMESTAMP,
                        RUN_AD_BIDDING_START_TIMESTAMP,
                        RUN_AD_BIDDING_END_TIMESTAMP,
                        STOP_ELAPSED_TIMESTAMP);
        mAdSelectionExecutionLogger =
                new AdSelectionExecutionLogger(
                        sCallerMetadata,
                        mLoggerClockMock,
                        mSpyContext,
                        mAdServicesLoggerMock,
                        mFakeFlags);
    }

    private void setAdSelectionExecutionLoggerMockWithAdFiltering() {
        when(mLoggerClockMock.elapsedRealtime())
                .thenReturn(
                        START_ELAPSED_TIMESTAMP, // For AdSelectionExecutionLogger
                        BIDDING_STAGE_START_TIMESTAMP,
                        GET_BUYERS_CUSTOM_AUDIENCE_END_TIMESTAMP,
                        AD_FILTERING_START,
                        APP_INSTALL_FILTERING_START,
                        APP_INSTALL_FILTERING_END,
                        FREQ_CAP_FILTERING_START,
                        FREQ_CAP_FILTERING_END,
                        AD_FILTERING_END);
        mAdSelectionExecutionLogger =
                new AdSelectionExecutionLogger(
                        sCallerMetadata,
                        mLoggerClockMock,
                        mSpyContext,
                        mAdServicesLoggerMock,
                        mFakeFlags);
    }

    // Verify bidding process.
    private void verifyLogForSuccessfulBiddingProcess(List<AdBiddingOutcome> adBiddingOutcomeList) {
        verify(mAdServicesLoggerMock)
                .logRunAdBiddingProcessReportedStats(
                        mRunAdBiddingProcessReportedStatsArgumentCaptor.capture());
        RunAdBiddingProcessReportedStats runAdBiddingProcessReportedStats =
                mRunAdBiddingProcessReportedStatsArgumentCaptor.getValue();

        assertThat(runAdBiddingProcessReportedStats.getGetBuyersCustomAudienceLatencyInMills())
                .isEqualTo(GET_BUYERS_CUSTOM_AUDIENCE_LATENCY_MS);
        assertThat(runAdBiddingProcessReportedStats.getGetBuyersCustomAudienceResultCode())
                .isEqualTo(STATUS_SUCCESS);
        assertThat(runAdBiddingProcessReportedStats.getNumBuyersRequested())
                .isEqualTo(mCustomAudienceBuyers.size());
        assertThat(runAdBiddingProcessReportedStats.getNumBuyersFetched())
                .isEqualTo(
                        mBuyerCustomAudienceList.stream()
                                .filter(a -> !Objects.isNull(a))
                                .map(a -> a.getBuyer())
                                .collect(Collectors.toSet())
                                .size());
        assertThat(runAdBiddingProcessReportedStats.getNumOfAdsEnteringBidding())
                .isEqualTo(
                        mBuyerCustomAudienceList.stream()
                                .filter(a -> !Objects.isNull(a))
                                .map(a -> a.getAds().size())
                                .reduce(0, (a, b) -> (a + b)));
        int numOfCAsEnteringBidding = mBuyerCustomAudienceList.size();
        assertThat(runAdBiddingProcessReportedStats.getNumOfCasEnteringBidding())
                .isEqualTo(numOfCAsEnteringBidding);
        int numOfCAsPostBidding =
                adBiddingOutcomeList.stream()
                        .filter(a -> !Objects.isNull(a))
                        .map(
                                a ->
                                        a.getCustomAudienceBiddingInfo()
                                                .getCustomAudienceSignals()
                                                .hashCode())
                        .collect(Collectors.toSet())
                        .size();
        assertThat(runAdBiddingProcessReportedStats.getNumOfCasPostBidding())
                .isEqualTo(numOfCAsPostBidding);
        assertThat(runAdBiddingProcessReportedStats.getRatioOfCasSelectingRmktAds())
                .isEqualTo(((float) numOfCAsPostBidding) / numOfCAsEnteringBidding);
        assertThat(runAdBiddingProcessReportedStats.getRunAdBiddingLatencyInMillis())
                .isEqualTo(RUN_AD_BIDDING_LATENCY_MS);
        assertThat(runAdBiddingProcessReportedStats.getRunAdBiddingResultCode())
                .isEqualTo(STATUS_SUCCESS);
        assertThat(runAdBiddingProcessReportedStats.getTotalAdBiddingStageLatencyInMillis())
                .isEqualTo(TOTAL_BIDDING_STAGE_LATENCY_IN_MS);
    }

    private void verifyLogForFailedBiddingStageDuringFetchBuyersCustomAudience(int resultCode) {
        verify(mAdServicesLoggerMock)
                .logRunAdBiddingProcessReportedStats(
                        mRunAdBiddingProcessReportedStatsArgumentCaptor.capture());
        RunAdBiddingProcessReportedStats runAdBiddingProcessReportedStats =
                mRunAdBiddingProcessReportedStatsArgumentCaptor.getValue();

        assertThat(runAdBiddingProcessReportedStats.getGetBuyersCustomAudienceLatencyInMills())
                .isEqualTo(TOTAL_BIDDING_STAGE_LATENCY_IN_MS);
        assertThat(runAdBiddingProcessReportedStats.getGetBuyersCustomAudienceResultCode())
                .isEqualTo(resultCode);
        assertThat(runAdBiddingProcessReportedStats.getNumBuyersRequested())
                .isEqualTo(mCustomAudienceBuyers.size());
        assertThat(runAdBiddingProcessReportedStats.getNumBuyersFetched()).isEqualTo(STATUS_UNSET);
        assertThat(runAdBiddingProcessReportedStats.getNumOfAdsEnteringBidding())
                .isEqualTo(STATUS_UNSET);
        assertThat(runAdBiddingProcessReportedStats.getNumOfCasEnteringBidding())
                .isEqualTo(STATUS_UNSET);
        assertThat(runAdBiddingProcessReportedStats.getNumOfCasPostBidding())
                .isEqualTo(STATUS_UNSET);
        assertThat(runAdBiddingProcessReportedStats.getRatioOfCasSelectingRmktAds())
                .isEqualTo(-1.0f);
        assertThat(runAdBiddingProcessReportedStats.getRunAdBiddingLatencyInMillis())
                .isEqualTo(STATUS_UNSET);
        assertThat(runAdBiddingProcessReportedStats.getRunAdBiddingResultCode())
                .isEqualTo(STATUS_UNSET);
        assertThat(runAdBiddingProcessReportedStats.getTotalAdBiddingStageLatencyInMillis())
                .isEqualTo(TOTAL_BIDDING_STAGE_LATENCY_IN_MS);
    }

    // Verify Ad selection process.
    private void verifyLogForSuccessfulAdSelectionProcess() {
        verify(mAdServicesLoggerMock)
                .logRunAdSelectionProcessReportedStats(
                        mRunAdSelectionProcessReportedStatsArgumentCaptor.capture());
        RunAdSelectionProcessReportedStats runAdSelectionProcessReportedStats =
                mRunAdSelectionProcessReportedStatsArgumentCaptor.getValue();

        assertThat(runAdSelectionProcessReportedStats.getIsRemarketingAdsWon())
                .isEqualTo(IS_RMKT_ADS_WON);
        assertThat(runAdSelectionProcessReportedStats.getDBAdSelectionSizeInBytes())
                .isEqualTo((int) DB_AD_SELECTION_FILE_SIZE);
        assertThat(runAdSelectionProcessReportedStats.getPersistAdSelectionLatencyInMillis())
                .isEqualTo(PERSIST_AD_SELECTION_LATENCY_MS);
        assertThat(runAdSelectionProcessReportedStats.getPersistAdSelectionResultCode())
                .isEqualTo(STATUS_SUCCESS);
        assertThat(runAdSelectionProcessReportedStats.getRunAdSelectionLatencyInMillis())
                .isEqualTo(RUN_AD_SELECTION_INTERNAL_FINAL_LATENCY_MS);
        assertThat(runAdSelectionProcessReportedStats.getRunAdSelectionResultCode())
                .isEqualTo(STATUS_SUCCESS);
    }

    private void verifyLogForFailurePriorPersistAdSelection(int resultCode) {
        verify(mAdServicesLoggerMock)
                .logRunAdSelectionProcessReportedStats(
                        mRunAdSelectionProcessReportedStatsArgumentCaptor.capture());
        RunAdSelectionProcessReportedStats runAdSelectionProcessReportedStats =
                mRunAdSelectionProcessReportedStatsArgumentCaptor.getValue();

        assertThat(runAdSelectionProcessReportedStats.getIsRemarketingAdsWon())
                .isEqualTo(IS_RMKT_ADS_WON_UNSET);
        assertThat(runAdSelectionProcessReportedStats.getDBAdSelectionSizeInBytes())
                .isEqualTo(STATUS_UNSET);
        assertThat(runAdSelectionProcessReportedStats.getPersistAdSelectionLatencyInMillis())
                .isEqualTo(STATUS_UNSET);
        assertThat(runAdSelectionProcessReportedStats.getPersistAdSelectionResultCode())
                .isEqualTo(STATUS_UNSET);
        assertThat(runAdSelectionProcessReportedStats.getRunAdSelectionLatencyInMillis())
                .isEqualTo(RUN_AD_SELECTION_INTERNAL_FINAL_LATENCY_MS);
        assertThat(runAdSelectionProcessReportedStats.getRunAdSelectionResultCode())
                .isEqualTo(resultCode);
    }

    private void verifyLogForFailureByRunAdSelectionOrchestrationTimesOut(int resultCode) {
        verify(mAdServicesLoggerMock)
                .logRunAdSelectionProcessReportedStats(
                        mRunAdSelectionProcessReportedStatsArgumentCaptor.capture());
        RunAdSelectionProcessReportedStats runAdSelectionProcessReportedStats =
                mRunAdSelectionProcessReportedStatsArgumentCaptor.getValue();
        assertThat(runAdSelectionProcessReportedStats.getRunAdSelectionLatencyInMillis())
                .isEqualTo(RUN_AD_SELECTION_INTERNAL_FINAL_LATENCY_MS);
        assertThat(runAdSelectionProcessReportedStats.getRunAdSelectionResultCode())
                .isEqualTo(resultCode);
    }

    private void verifyLogForSuccessfulSignatureVerificationForContextualAds(
            int numOfContextualAds, int expectedNumOfKeysFetched) {
        SignatureVerificationStats.VerificationStatus verifiedStatus =
                SignatureVerificationStats.VerificationStatus.VERIFIED;
        verify(mAdServicesLoggerMock, times(numOfContextualAds))
                .logSignatureVerificationStats(mSignatureVerificationStatsArgumentCaptor.capture());
        List<SignatureVerificationStats> signatureVerificationStatsList =
                mSignatureVerificationStatsArgumentCaptor.getAllValues();

        for (SignatureVerificationStats stats : signatureVerificationStatsList) {
            assertThat(stats.getSignatureVerificationStatus()).isEqualTo(verifiedStatus);
            assertThat(stats.getKeyFetchLatency())
                    .isEqualTo(SIGNATURE_VERIFICATION_KEY_FETCH_LATENCY_MS);
            assertThat(stats.getSerializationLatency())
                    .isEqualTo(SIGNATURE_VERIFICATION_SERIALIZATION_LATENCY_MS);
            assertThat(stats.getVerificationLatency())
                    .isEqualTo(SIGNATURE_VERIFICATION_VERIFICATION_LATENCY_MS);
            assertThat(stats.getNumOfKeysFetched()).isEqualTo(expectedNumOfKeysFetched);
        }
    }

    private void verifyLogForAdFilteringForCAs(
            int numOfAdsBeforeFiltering,
            int numOfAdsFiltered,
            int numOfCAsBeforeFiltering,
            int numOfCAsFiltered) {
        verify(mAdServicesLoggerMock, times(1))
                .logAdFilteringProcessAdSelectionReportedStats(mAdFilteringCaptor.capture());
        AdFilteringProcessAdSelectionReportedStats stats = mAdFilteringCaptor.getValue();

        assertThat(stats.getLatencyInMillisOfAppInstallFiltering())
                .isEqualTo(APP_INSTALL_FILTERING_LATENCY_MS);
        assertThat(stats.getLatencyInMillisOfFcapFilters())
                .isEqualTo(FREQUENCY_CAP_FILTERING_LATENCY_MS);
        assertThat(stats.getLatencyInMillisOfAllAdFiltering())
                .isEqualTo(AD_FILTERING_OVERALL_LATENCY_MS);
        assertThat(stats.getFilterProcessType()).isEqualTo(FILTER_PROCESS_TYPE_CUSTOM_AUDIENCES);
        assertThat(stats.getTotalNumOfAdsBeforeFiltering()).isEqualTo(numOfAdsBeforeFiltering);
        assertThat(stats.getNumOfAdsFilteredOutOfBidding()).isEqualTo(numOfAdsFiltered);
        assertThat(stats.getNumOfCustomAudiencesFilteredOutOfBidding()).isEqualTo(numOfCAsFiltered);
        assertThat(stats.getTotalNumOfCustomAudiencesBeforeFiltering())
                .isEqualTo(numOfCAsBeforeFiltering);
        assertThat(stats.getNumOfAdCounterKeysInFcapFilters()).isEqualTo(0);
        assertThat(stats.getNumOfPackageInAppInstallFilters()).isEqualTo(0);
        assertThat(stats.getNumOfDbOperations()).isEqualTo(0);
        assertThat(stats.getNumOfContextualAdsFiltered()).isEqualTo(0);
        assertThat(stats.getNumOfContextualAdsFilteredOutOfBiddingInvalidSignatures()).isEqualTo(0);
        assertThat(stats.getNumOfContextualAdsFilteredOutOfBiddingNoAds()).isEqualTo(0);
        assertThat(stats.getTotalNumOfContextualAdsBeforeFiltering()).isEqualTo(0);
    }

    private void verifyLogForAdFilteringForContextualAds(
            int numOfContextualAdsBeforeFiltering,
            int numOfContextualAdsFiltered,
            int numOfContextualAdBundlesFilteredNoAds,
            int numOfContextualAdBundlesFilteredInvalidSignatures) {
        verify(mAdServicesLoggerMock, times(2))
                .logAdFilteringProcessAdSelectionReportedStats(mAdFilteringCaptor.capture());
        AdFilteringProcessAdSelectionReportedStats stats =
                mAdFilteringCaptor.getAllValues().stream()
                        .filter(e -> e.getFilterProcessType() == FILTER_PROCESS_TYPE_CONTEXTUAL_ADS)
                        .findFirst()
                        .orElse(null);

        assertThat(stats).isNotNull();
        assertThat(stats.getFilterProcessType()).isEqualTo(FILTER_PROCESS_TYPE_CONTEXTUAL_ADS);
        assertThat(stats.getNumOfContextualAdsFiltered()).isEqualTo(numOfContextualAdsFiltered);
        assertThat(stats.getNumOfContextualAdsFilteredOutOfBiddingInvalidSignatures())
                .isEqualTo(numOfContextualAdBundlesFilteredInvalidSignatures);
        assertThat(stats.getNumOfContextualAdsFilteredOutOfBiddingNoAds())
                .isEqualTo(numOfContextualAdBundlesFilteredNoAds);
        assertThat(stats.getTotalNumOfContextualAdsBeforeFiltering())
                .isEqualTo(numOfContextualAdsBeforeFiltering);
        assertThat(stats.getTotalNumOfAdsBeforeFiltering()).isEqualTo(0);
        assertThat(stats.getNumOfAdsFilteredOutOfBidding()).isEqualTo(0);
        assertThat(stats.getNumOfCustomAudiencesFilteredOutOfBidding()).isEqualTo(0);
        assertThat(stats.getTotalNumOfCustomAudiencesBeforeFiltering()).isEqualTo(0);
        assertThat(stats.getNumOfAdCounterKeysInFcapFilters()).isEqualTo(0);
        assertThat(stats.getNumOfPackageInAppInstallFilters()).isEqualTo(0);
        assertThat(stats.getNumOfDbOperations()).isEqualTo(0);
    }

    private void verifyAndSetupCommonSuccessScenario(AdSelectionConfig adSelectionConfig)
            throws AdServicesException {
        when(mClockSpy.instant()).thenReturn(Clock.systemUTC().instant());
        doReturn(mFakeFlags).when(FlagsFactory::getFlags);

        // Getting BiddingOutcome-forBuyerX corresponding to each CA-forBuyerX
        doReturn(ImmutableList.of(Futures.immediateFuture(mAdBiddingOutcomeForBuyer1)))
                .when(mPerBuyerBiddingRunnerMock)
                .runBidding(
                        BUYER_1,
                        ImmutableList.of(mDBCustomAudienceForBuyer1),
                        mFakeFlags.getAdSelectionBiddingTimeoutPerBuyerMs(),
                        adSelectionConfig);
        doReturn(ImmutableList.of(Futures.immediateFuture(mAdBiddingOutcomeForBuyer2)))
                .when(mPerBuyerBiddingRunnerMock)
                .runBidding(
                        BUYER_2,
                        ImmutableList.of(mDBCustomAudienceForBuyer2),
                        mFakeFlags.getAdSelectionBiddingTimeoutPerBuyerMs(),
                        adSelectionConfig);

        // Getting ScoringOutcome-ForBuyerX corresponding to each BiddingOutcome-forBuyerX
        when(mMockAdsScoreGenerator.runAdScoring(mAdBiddingOutcomeList, adSelectionConfig))
                .thenReturn((FluentFuture.from(Futures.immediateFuture(mAdScoringOutcomeList))));

        setAdSelectionExecutionLoggerMockWithSuccessAdSelection();

        when(mMockAdSelectionIdGenerator.generateId()).thenReturn(AD_SELECTION_ID);
        assertFalse(mAdSelectionEntryDaoSpy.doesAdSelectionIdExist(AD_SELECTION_ID));
    }

    private void verifyAndSetupAdFilteringSuccessScenario(AdSelectionConfig adSelectionConfig)
            throws AdServicesException {
        when(mClockSpy.instant()).thenReturn(Clock.systemUTC().instant());
        doReturn(mFakeFlags).when(FlagsFactory::getFlags);

        // Getting BiddingOutcome-forBuyerX corresponding to each CA-forBuyerX
        doReturn(ImmutableList.of(Futures.immediateFuture(mAdBiddingOutcomeForBuyer1)))
                .when(mPerBuyerBiddingRunnerMock)
                .runBidding(
                        BUYER_1,
                        ImmutableList.of(mDBCustomAudienceForBuyer1),
                        mFakeFlags.getAdSelectionBiddingTimeoutPerBuyerMs(),
                        adSelectionConfig);
        doReturn(ImmutableList.of(Futures.immediateFuture(mAdBiddingOutcomeForBuyer2)))
                .when(mPerBuyerBiddingRunnerMock)
                .runBidding(
                        BUYER_2,
                        ImmutableList.of(mDBCustomAudienceForBuyer2),
                        mFakeFlags.getAdSelectionBiddingTimeoutPerBuyerMs(),
                        adSelectionConfig);

        // Getting ScoringOutcome-ForBuyerX corresponding to each BiddingOutcome-forBuyerX
        when(mMockAdsScoreGenerator.runAdScoring(mAdBiddingOutcomeList, adSelectionConfig))
                .thenReturn((FluentFuture.from(Futures.immediateFuture(mAdScoringOutcomeList))));

        when(mMockAdSelectionIdGenerator.generateId()).thenReturn(AD_SELECTION_ID);
        assertFalse(mAdSelectionEntryDaoSpy.doesAdSelectionIdExist(AD_SELECTION_ID));
        setAdSelectionExecutionLoggerMockWithAdFiltering();
    }

    private void verifyDebugReportingWasNotCalled() {
        assertThat(mFakeFlags.getFledgeEventLevelDebugReportingEnabled()).isFalse();
        verify(mDebugReportingMock, times(0)).getSenderStrategy();
        verify(mDebugReportSenderMock, times(0)).batchEnqueue(anyList());
        verify(mDebugReportSenderMock, times(0)).flush();
    }

    private static class FlagsWithKAnonEnabled extends OnDeviceAdSelectionRunnerTestFlags {
        @Override
        public boolean getFledgeKAnonSignJoinFeatureOnDeviceAuctionEnabled() {
            return true;
        }
    }

    private static class OnDeviceAdSelectionRunnerTestFlags implements Flags {
        @Override
        public boolean getDisableFledgeEnrollmentCheck() {
            return true;
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
        public boolean getFledgeOnDeviceAuctionKillSwitch() {
            return false;
        }
    }

    private Map<AdTechIdentifier, SignedContextualAds> createContextualAds() {
        Map<AdTechIdentifier, SignedContextualAds> buyerContextualAds = new HashMap<>();

        AdTechIdentifier buyer1 = CommonFixture.VALID_BUYER_1;
        SignedContextualAds contextualAds1 =
                signContextualAds(
                        SignedContextualAdsFixture.aContextualAdsWithEmptySignatureBuilder(
                                        buyer1, ImmutableList.of(100.0, 200.0, 300.0))
                                .setDecisionLogicUri(
                                        CommonFixture.getUri(
                                                BUYER_1, BUYER_BIDDING_LOGIC_URI_PATH)));

        AdTechIdentifier buyer2 = CommonFixture.VALID_BUYER_2;
        SignedContextualAds contextualAds2 =
                signContextualAds(
                        SignedContextualAdsFixture.aContextualAdsWithEmptySignatureBuilder(
                                        buyer2, ImmutableList.of(400.0, 500.0))
                                .setDecisionLogicUri(
                                        CommonFixture.getUri(
                                                BUYER_2, BUYER_BIDDING_LOGIC_URI_PATH)));

        buyerContextualAds.put(buyer1, contextualAds1);
        buyerContextualAds.put(buyer2, contextualAds2);

        for (AdTechIdentifier adTech : buyerContextualAds.keySet()) {
            doReturn(new EnrollmentData.Builder().setEnrollmentId(adTech.toString()).build())
                    .when(mEnrollmentDaoMock)
                    .getEnrollmentDataForFledgeByAdTechIdentifier(adTech);
            doReturn(
                            Collections.singletonList(
                                    new EncryptionKey.Builder()
                                            .setBody(PUBLIC_TEST_KEY_STRING)
                                            .build()))
                    .when(mEncryptionKeyDaoMock)
                    .getEncryptionKeyFromEnrollmentIdAndKeyType(
                            adTech.toString(), EncryptionKey.KeyType.SIGNING);
        }

        return buyerContextualAds;
    }
}

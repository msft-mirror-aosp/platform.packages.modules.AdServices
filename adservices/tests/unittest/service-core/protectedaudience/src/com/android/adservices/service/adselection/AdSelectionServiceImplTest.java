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

import static android.adservices.adselection.AdSelectionFromOutcomesConfigFixture.SAMPLE_SELECTION_LOGIC_URI_1;
import static android.adservices.adselection.AdSelectionFromOutcomesConfigFixture.SAMPLE_SELECTION_SIGNALS;
import static android.adservices.common.AdServicesStatusUtils.STATUS_ADSERVICES_DISABLED;
import static android.adservices.common.CommonFixture.TEST_PACKAGE_NAME;

import static com.android.adservices.common.CommonFlagsValues.EXTENDED_FLEDGE_AD_SELECTION_BIDDING_TIMEOUT_PER_CA_MS;
import static com.android.adservices.common.CommonFlagsValues.EXTENDED_FLEDGE_AD_SELECTION_FROM_OUTCOMES_OVERALL_TIMEOUT_MS;
import static com.android.adservices.common.CommonFlagsValues.EXTENDED_FLEDGE_AD_SELECTION_OVERALL_TIMEOUT_MS;
import static com.android.adservices.common.CommonFlagsValues.EXTENDED_FLEDGE_AD_SELECTION_SCORING_TIMEOUT_MS;
import static com.android.adservices.common.CommonFlagsValues.EXTENDED_FLEDGE_REPORT_IMPRESSION_OVERALL_TIMEOUT_MS;
import static com.android.adservices.common.logging.annotations.ExpectErrorLogUtilWithExceptionCall.Any;
import static com.android.adservices.service.FlagsConstants.KEY_DISABLE_FLEDGE_ENROLLMENT_CHECK;
import static com.android.adservices.service.FlagsConstants.KEY_ENFORCE_FOREGROUND_STATUS_FLEDGE_OVERRIDE;
import static com.android.adservices.service.FlagsConstants.KEY_ENFORCE_FOREGROUND_STATUS_FLEDGE_REPORT_IMPRESSION;
import static com.android.adservices.service.FlagsConstants.KEY_ENFORCE_FOREGROUND_STATUS_FLEDGE_RUN_AD_SELECTION;
import static com.android.adservices.service.FlagsConstants.KEY_FLEDGE_AD_SELECTION_BIDDING_TIMEOUT_PER_CA_MS;
import static com.android.adservices.service.FlagsConstants.KEY_FLEDGE_AD_SELECTION_FROM_OUTCOMES_OVERALL_TIMEOUT_MS;
import static com.android.adservices.service.FlagsConstants.KEY_FLEDGE_AD_SELECTION_OVERALL_TIMEOUT_MS;
import static com.android.adservices.service.FlagsConstants.KEY_FLEDGE_AD_SELECTION_SCORING_TIMEOUT_MS;
import static com.android.adservices.service.FlagsConstants.KEY_FLEDGE_APP_INSTALL_FILTERING_ENABLED;
import static com.android.adservices.service.FlagsConstants.KEY_FLEDGE_MEASUREMENT_REPORT_AND_REGISTER_EVENT_API_ENABLED;
import static com.android.adservices.service.FlagsConstants.KEY_FLEDGE_MEASUREMENT_REPORT_AND_REGISTER_EVENT_API_FALLBACK_ENABLED;
import static com.android.adservices.service.FlagsConstants.KEY_FLEDGE_ON_DEVICE_AUCTION_SHOULD_USE_UNIFIED_TABLES;
import static com.android.adservices.service.FlagsConstants.KEY_FLEDGE_REGISTER_AD_BEACON_ENABLED;
import static com.android.adservices.service.FlagsConstants.KEY_FLEDGE_REPORT_IMPRESSION_OVERALL_TIMEOUT_MS;
import static com.android.adservices.service.FlagsConstants.KEY_SDK_REQUEST_PERMITS_PER_SECOND;
import static com.android.adservices.service.stats.AdServicesStatsLog.AD_SERVICES_API_CALLED__API_NAME__API_NAME_UNKNOWN;
import static com.android.adservices.service.stats.AdServicesStatsLog.AD_SERVICES_API_CALLED__API_NAME__GET_AD_SELECTION_DATA;
import static com.android.adservices.service.stats.AdServicesStatsLog.AD_SERVICES_API_CALLED__API_NAME__OVERRIDE_AD_SELECTION_CONFIG_REMOTE_INFO;
import static com.android.adservices.service.stats.AdServicesStatsLog.AD_SERVICES_API_CALLED__API_NAME__PERSIST_AD_SELECTION_RESULT;
import static com.android.adservices.service.stats.AdServicesStatsLog.AD_SERVICES_API_CALLED__API_NAME__REMOVE_AD_SELECTION_CONFIG_REMOTE_INFO_OVERRIDE;
import static com.android.adservices.service.stats.AdServicesStatsLog.AD_SERVICES_API_CALLED__API_NAME__REPORT_IMPRESSION;
import static com.android.adservices.service.stats.AdServicesStatsLog.AD_SERVICES_API_CALLED__API_NAME__REPORT_INTERACTION;
import static com.android.adservices.service.stats.AdServicesStatsLog.AD_SERVICES_API_CALLED__API_NAME__RESET_ALL_AD_SELECTION_CONFIG_REMOTE_OVERRIDES;
import static com.android.adservices.service.stats.AdServicesStatsLog.AD_SERVICES_API_CALLED__API_NAME__SELECT_ADS;
import static com.android.adservices.service.stats.AdServicesStatsLog.AD_SERVICES_API_CALLED__API_NAME__SELECT_ADS_FROM_OUTCOMES;
import static com.android.adservices.service.stats.AdServicesStatsLog.AD_SERVICES_API_CALLED__API_NAME__SET_APP_INSTALL_ADVERTISERS;
import static com.android.adservices.service.stats.AdServicesStatsLog.AD_SERVICES_API_CALLED__API_NAME__UPDATE_AD_COUNTER_HISTOGRAM;
import static com.android.adservices.service.stats.AdServicesStatsLog.AD_SERVICES_ERROR_REPORTED__ERROR_CODE__API_CALLBACK_ERROR;
import static com.android.adservices.service.stats.AdServicesStatsLog.AD_SERVICES_ERROR_REPORTED__PPAPI_NAME__FLEDGE;
import static com.android.dx.mockito.inline.extended.ExtendedMockito.any;
import static com.android.dx.mockito.inline.extended.ExtendedMockito.anyInt;
import static com.android.dx.mockito.inline.extended.ExtendedMockito.doAnswer;
import static com.android.dx.mockito.inline.extended.ExtendedMockito.doNothing;
import static com.android.dx.mockito.inline.extended.ExtendedMockito.doThrow;
import static com.android.dx.mockito.inline.extended.ExtendedMockito.eq;
import static com.android.dx.mockito.inline.extended.ExtendedMockito.verify;
import static com.android.dx.mockito.inline.extended.ExtendedMockito.when;

import static com.google.common.truth.Truth.assertWithMessage;

import static org.mockito.ArgumentMatchers.anyBoolean;
import static org.mockito.ArgumentMatchers.anyString;

import android.adservices.adselection.AdSelectionCallback;
import android.adservices.adselection.AdSelectionConfig;
import android.adservices.adselection.AdSelectionConfigFixture;
import android.adservices.adselection.AdSelectionFromOutcomesConfig;
import android.adservices.adselection.AdSelectionFromOutcomesInput;
import android.adservices.adselection.AdSelectionInput;
import android.adservices.adselection.AdSelectionOverrideCallback;
import android.adservices.adselection.AdSelectionResponse;
import android.adservices.adselection.CustomAudienceSignalsFixture;
import android.adservices.adselection.GetAdSelectionDataCallback;
import android.adservices.adselection.GetAdSelectionDataInput;
import android.adservices.adselection.GetAdSelectionDataResponse;
import android.adservices.adselection.PerBuyerDecisionLogic;
import android.adservices.adselection.PersistAdSelectionResultCallback;
import android.adservices.adselection.PersistAdSelectionResultInput;
import android.adservices.adselection.PersistAdSelectionResultResponse;
import android.adservices.adselection.RemoveAdCounterHistogramOverrideInput;
import android.adservices.adselection.ReportImpressionCallback;
import android.adservices.adselection.ReportImpressionInput;
import android.adservices.adselection.ReportInteractionCallback;
import android.adservices.adselection.ReportInteractionInput;
import android.adservices.adselection.SetAdCounterHistogramOverrideInput;
import android.adservices.adselection.SetAppInstallAdvertisersCallback;
import android.adservices.adselection.SetAppInstallAdvertisersInput;
import android.adservices.adselection.UpdateAdCounterHistogramCallback;
import android.adservices.adselection.UpdateAdCounterHistogramInput;
import android.adservices.common.AdSelectionSignals;
import android.adservices.common.AdTechIdentifier;
import android.adservices.common.CallerMetadata;
import android.adservices.common.CallingAppUidSupplierProcessImpl;
import android.adservices.common.FledgeErrorResponse;
import android.adservices.http.MockWebServerRule;
import android.net.Uri;
import android.os.Process;
import android.os.RemoteException;

import androidx.room.Room;
import androidx.test.core.app.ApplicationProvider;

import com.android.adservices.MockWebServerRuleFactory;
import com.android.adservices.common.AdServicesExtendedMockitoTestCase;
import com.android.adservices.common.WebViewSupportUtil;
import com.android.adservices.common.logging.annotations.ExpectErrorLogUtilWithExceptionCall;
import com.android.adservices.common.logging.annotations.SetErrorLogUtilDefaultParams;
import com.android.adservices.concurrency.AdServicesExecutors;
import com.android.adservices.data.adselection.AdSelectionDatabase;
import com.android.adservices.data.adselection.AdSelectionDebugReportDao;
import com.android.adservices.data.adselection.AdSelectionEntryDao;
import com.android.adservices.data.adselection.AppInstallDao;
import com.android.adservices.data.adselection.CustomAudienceSignals;
import com.android.adservices.data.adselection.FrequencyCapDao;
import com.android.adservices.data.adselection.SharedStorageDatabase;
import com.android.adservices.data.customaudience.CustomAudienceDao;
import com.android.adservices.data.customaudience.CustomAudienceDatabase;
import com.android.adservices.data.customaudience.DBCustomAudience;
import com.android.adservices.data.encryptionkey.EncryptionKeyDao;
import com.android.adservices.data.enrollment.EnrollmentDao;
import com.android.adservices.data.signals.EncodedPayloadDao;
import com.android.adservices.data.signals.ProtectedSignalsDatabase;
import com.android.adservices.service.DebugFlags;
import com.android.adservices.service.FlagsFactory;
import com.android.adservices.service.adselection.debug.AuctionServerDebugConfigurationGenerator;
import com.android.adservices.service.adselection.encryption.ObliviousHttpEncryptor;
import com.android.adservices.service.adselection.encryption.ServerAuctionCoordinatorUriStrategyFactory;
import com.android.adservices.service.common.AdSelectionServiceFilter;
import com.android.adservices.service.common.AppImportanceFilter;
import com.android.adservices.service.common.FledgeAuthorizationFilter;
import com.android.adservices.service.common.RetryStrategyFactory;
import com.android.adservices.service.common.Throttler;
import com.android.adservices.service.common.cache.CacheProviderFactory;
import com.android.adservices.service.common.httpclient.AdServicesHttpsClient;
import com.android.adservices.service.consent.ConsentManager;
import com.android.adservices.service.devapi.DevContext;
import com.android.adservices.service.devapi.DevContextFilter;
import com.android.adservices.service.js.JSScriptEngine;
import com.android.adservices.service.kanon.KAnonSignJoinFactory;
import com.android.adservices.service.measurement.MeasurementImpl;
import com.android.adservices.service.stats.AdServicesLogger;
import com.android.adservices.service.stats.AdServicesStatsLog;
import com.android.adservices.shared.testing.SupportedByConditionRule;
import com.android.adservices.shared.testing.annotations.SetFlagFalse;
import com.android.adservices.shared.testing.annotations.SetFlagTrue;
import com.android.adservices.shared.testing.annotations.SetFloatFlag;
import com.android.adservices.shared.testing.annotations.SetLongFlag;
import com.android.modules.utils.testing.ExtendedMockitoRule.MockStatic;
import com.android.modules.utils.testing.ExtendedMockitoRule.SpyStatic;

import com.google.common.collect.ImmutableList;
import com.google.common.collect.ImmutableMap;
import com.google.common.collect.ImmutableSet;

import org.junit.Before;
import org.junit.Rule;
import org.junit.Test;
import org.mockito.Mock;
import org.mockito.Spy;
import org.mockito.stubbing.Answer;

import java.util.Map;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.ScheduledThreadPoolExecutor;

@SpyStatic(DebugFlags.class)
@SpyStatic(FlagsFactory.class)
@SpyStatic(JSScriptEngine.class)
@MockStatic(ConsentManager.class)
@MockStatic(MeasurementImpl.class)
@MockStatic(AppImportanceFilter.class)
@SetFlagTrue(KEY_DISABLE_FLEDGE_ENROLLMENT_CHECK)
@SetFlagTrue(KEY_ENFORCE_FOREGROUND_STATUS_FLEDGE_RUN_AD_SELECTION)
@SetFlagTrue(KEY_ENFORCE_FOREGROUND_STATUS_FLEDGE_REPORT_IMPRESSION)
@SetFlagTrue(KEY_ENFORCE_FOREGROUND_STATUS_FLEDGE_OVERRIDE)
// Unlimited rate for unit tests to avoid flake in tests due to rate limiting
@SetFloatFlag(name = KEY_SDK_REQUEST_PERMITS_PER_SECOND, value = -1)
@SetFlagTrue(KEY_FLEDGE_REGISTER_AD_BEACON_ENABLED)
@SetFlagFalse(KEY_FLEDGE_MEASUREMENT_REPORT_AND_REGISTER_EVENT_API_ENABLED)
@SetFlagFalse(KEY_FLEDGE_MEASUREMENT_REPORT_AND_REGISTER_EVENT_API_FALLBACK_ENABLED)
@SetFlagTrue(KEY_FLEDGE_APP_INSTALL_FILTERING_ENABLED)
@SetLongFlag(
        name = KEY_FLEDGE_AD_SELECTION_BIDDING_TIMEOUT_PER_CA_MS,
        value = EXTENDED_FLEDGE_AD_SELECTION_BIDDING_TIMEOUT_PER_CA_MS)
@SetLongFlag(
        name = KEY_FLEDGE_AD_SELECTION_SCORING_TIMEOUT_MS,
        value = EXTENDED_FLEDGE_AD_SELECTION_SCORING_TIMEOUT_MS)
@SetLongFlag(
        name = KEY_FLEDGE_AD_SELECTION_OVERALL_TIMEOUT_MS,
        value = EXTENDED_FLEDGE_AD_SELECTION_OVERALL_TIMEOUT_MS)
@SetLongFlag(
        name = KEY_FLEDGE_AD_SELECTION_FROM_OUTCOMES_OVERALL_TIMEOUT_MS,
        value = EXTENDED_FLEDGE_AD_SELECTION_FROM_OUTCOMES_OVERALL_TIMEOUT_MS)
@SetLongFlag(
        name = KEY_FLEDGE_REPORT_IMPRESSION_OVERALL_TIMEOUT_MS,
        value = EXTENDED_FLEDGE_REPORT_IMPRESSION_OVERALL_TIMEOUT_MS)
@SetFlagFalse(KEY_FLEDGE_ON_DEVICE_AUCTION_SHOULD_USE_UNIFIED_TABLES)
@SetErrorLogUtilDefaultParams(
        throwable = Any.class,
        ppapiName = AD_SERVICES_ERROR_REPORTED__PPAPI_NAME__FLEDGE)
public final class AdSelectionServiceImplTest extends AdServicesExtendedMockitoTestCase {

    private static final int CALLER_UID = Process.myUid();
    private static final long AD_SELECTION_ID = 1;

    private static final boolean CONSOLE_MESSAGE_IN_LOGS_ENABLED = true;

    private static final CallerMetadata TEST_CALLER_METADATA = new CallerMetadata.Builder().build();
    private static final GetAdSelectionDataInput TEST_GET_AD_SELECTION_DATA_INPUT =
            new GetAdSelectionDataInput.Builder()
                    .setCallerPackageName(TEST_PACKAGE_NAME)
                    .setSeller(AdSelectionConfigFixture.SELLER)
                    .build();
    private static final AdSelectionConfig TEST_AD_SELECTION_CONFIG =
            new AdSelectionConfig.Builder()
                    .setSeller(AdSelectionConfigFixture.SELLER)
                    .setDecisionLogicUri(Uri.parse("https://seller.example.com/logic"))
                    .setTrustedScoringSignalsUri(Uri.parse("https://seller.example.com/signals"))
                    .setCustomAudienceBuyers(ImmutableList.of())
                    .setPerBuyerSignals(AdSelectionConfigFixture.PER_BUYER_SIGNALS)
                    .build();
    private static final AdSelectionInput TEST_AD_SELECTION_INPUT =
            new AdSelectionInput.Builder()
                    .setAdSelectionConfig(TEST_AD_SELECTION_CONFIG)
                    .setCallerPackageName(TEST_PACKAGE_NAME)
                    .build();
    private static final AdSelectionFromOutcomesConfig TEST_AD_SELECTION_FROM_OUTCOMES_CONFIG =
            new AdSelectionFromOutcomesConfig.Builder()
                    .setSeller(AdSelectionConfigFixture.SELLER)
                    .setAdSelectionIds(ImmutableList.of())
                    .setSelectionSignals(SAMPLE_SELECTION_SIGNALS)
                    .setSelectionLogicUri(SAMPLE_SELECTION_LOGIC_URI_1)
                    .build();
    private static final AdSelectionFromOutcomesInput TEST_AD_SELECTION_FROM_OUTCOMES_INPUT =
            new AdSelectionFromOutcomesInput.Builder()
                    .setAdSelectionFromOutcomesConfig(TEST_AD_SELECTION_FROM_OUTCOMES_CONFIG)
                    .setCallerPackageName(TEST_PACKAGE_NAME)
                    .build();
    private static final PersistAdSelectionResultInput TEST_PERSIST_AD_SELECTION_RESULT_INPUT =
            new PersistAdSelectionResultInput.Builder()
                    .setAdSelectionId(123L)
                    .setSeller(AdSelectionConfigFixture.SELLER)
                    .setCallerPackageName(TEST_PACKAGE_NAME)
                    .build();
    private static final ReportInteractionInput TEST_REPORT_INTERACTION_INPUT =
            new ReportInteractionInput.Builder()
                    .setAdSelectionId(123L)
                    .setInteractionKey("click")
                    .setInteractionData("data")
                    .setCallerPackageName(TEST_PACKAGE_NAME)
                    .setReportingDestinations(1)
                    .build();
    private static final SetAppInstallAdvertisersInput TEST_SET_APP_INSTALL_ADVERTISERS_INPUT =
            new SetAppInstallAdvertisersInput.Builder()
                    .setAdvertisers(ImmutableSet.of())
                    .setCallerPackageName(TEST_PACKAGE_NAME)
                    .build();
    private static final UpdateAdCounterHistogramInput TEST_UPDATE_AD_COUNTER_HISTOGRAM_INPUT =
            new UpdateAdCounterHistogramInput.Builder(
                            1, 1, AdSelectionConfigFixture.BUYER, TEST_PACKAGE_NAME)
                    .build();
    private static final SetAdCounterHistogramOverrideInput
            TEST_SET_AD_COUNTER_HISTOGRAM_OVERRIDE_INPUT =
                    new SetAdCounterHistogramOverrideInput.Builder()
                            .setAdEventType(1)
                            .setAdCounterKey(1)
                            .setBuyer(AdSelectionConfigFixture.BUYER)
                            .setCustomAudienceOwner(TEST_PACKAGE_NAME)
                            .setCustomAudienceName("name")
                            .build();
    private static final RemoveAdCounterHistogramOverrideInput
            TEST_REMOVE_AD_COUNTER_HISTOGRAM_OVERRIDE_INPUT =
                    new RemoveAdCounterHistogramOverrideInput.Builder()
                            .setAdEventType(1)
                            .setAdCounterKey(1)
                            .setBuyer(AdSelectionConfigFixture.BUYER)
                            .build();

    private final ExecutorService mLightweightExecutorService =
            AdServicesExecutors.getLightWeightExecutor();
    private final ExecutorService mBackgroundExecutorService =
            AdServicesExecutors.getBackgroundExecutor();
    private final ScheduledThreadPoolExecutor mScheduledExecutor =
            AdServicesExecutors.getScheduler();

    @Spy
    private final AdServicesHttpsClient mClientSpy =
            new AdServicesHttpsClient(
                    AdServicesExecutors.getBlockingExecutor(),
                    CacheProviderFactory.createNoOpCache());

    private CustomAudienceDao mCustomAudienceDao;
    private EncodedPayloadDao mEncodedPayloadDao;
    private AdSelectionEntryDao mAdSelectionEntryDao;
    private AppInstallDao mAppInstallDao;
    private FrequencyCapDao mFrequencyCapDao;
    private EncryptionKeyDao mEncryptionKeyDao;
    private EnrollmentDao mEnrollmentDao;
    private AdSelectionConfig.Builder mAdSelectionConfigBuilder;
    private Uri mBiddingLogicUri;
    private CustomAudienceSignals mCustomAudienceSignals;
    private AdTechIdentifier mSeller;
    private AdFilteringFeatureFactory mAdFilteringFeatureFactory;
    private RetryStrategyFactory mRetryStrategyFactory;
    private AdSelectionServiceImpl mAdSelectionService;
    private final String mFetchJavaScriptPathBuyer = "/fetchJavascript/buyer";
    private final String mFetchJavaScriptPathSeller = "/fetchJavascript/seller";
    private final String mFetchTrustedScoringSignalsPath = "/fetchTrustedSignals/";
    @Mock private PersistAdSelectionResultCallback mMockPersistAdSelectionResultCallback;
    @Mock private AdSelectionCallback mMockAdSelectionCallback;
    @Mock private ReportInteractionCallback mMockReportInteractionCallback;
    @Mock private SetAppInstallAdvertisersCallback mMockSetAppInstallAdvertisersCallback;
    @Mock private UpdateAdCounterHistogramCallback mMockUpdateAdCounterHistogramCallback;
    @Mock private AdSelectionOverrideCallback mMockAdSelectionOverrideCallback;

    @Mock
    private GetAdSelectionDataCallback
            mMockGetAdSelectionDataCallback; // Mock for callback failure test

    @Mock private AdServicesLogger mAdServicesLoggerMock;
    @Mock private DevContextFilter mDevContextFilterMock;
    @Mock private AppImportanceFilter mAppImportanceFilterMock;
    @Mock private FledgeAuthorizationFilter mFledgeAuthorizationFilterMock;
    @Mock private ConsentManager mConsentManagerMock;
    @Mock private AdSelectionServiceFilter mAdSelectionServiceFilterMock;
    @Mock private ObliviousHttpEncryptor mObliviousHttpEncryptor;
    @Mock private MeasurementImpl mMeasurementServiceMock;
    @Mock private AdSelectionDebugReportDao mAdSelectionDebugReportDao;
    @Mock private AdIdFetcher mAdIdFetcher;
    @Mock private KAnonSignJoinFactory mUnusedKAnonSignJoinFactory;
    @Mock private ReportImpressionTestCallback mMockReportImpressionTestCallback;

    @Mock
    private AuctionServerDebugConfigurationGenerator mAuctionServerDebugConfigurationGenerator;

    @Mock
    private ServerAuctionCoordinatorUriStrategyFactory
            mServerAuctionCoordinatorUriStrategyFactoryMock;

    @Rule(order = 11)
    public final SupportedByConditionRule webViewSupportsJSSandbox =
            WebViewSupportUtil.createJSSandboxAvailableRule(mContext);

    @Rule(order = 12)
    public final MockWebServerRule mMockWebServerRule = MockWebServerRuleFactory.createForHttps();

    @Before
    public void setUp() {
        mocker.mockGetFlags(mFakeFlags);
        mocker.mockGetDebugFlags(mFakeDebugFlags);
        mockGetConsentNotificationDebugMode(false);
        mCustomAudienceDao =
                Room.inMemoryDatabaseBuilder(mContext, CustomAudienceDatabase.class)
                        .addTypeConverter(new DBCustomAudience.Converters(true, true, true))
                        .build()
                        .customAudienceDao();
        mEncodedPayloadDao =
                Room.inMemoryDatabaseBuilder(mContext, ProtectedSignalsDatabase.class)
                        .build()
                        .getEncodedPayloadDao();

        mAdSelectionEntryDao =
                Room.inMemoryDatabaseBuilder(
                                ApplicationProvider.getApplicationContext(),
                                AdSelectionDatabase.class)
                        .build()
                        .adSelectionEntryDao();
        SharedStorageDatabase sharedDb =
                Room.inMemoryDatabaseBuilder(
                                ApplicationProvider.getApplicationContext(),
                                SharedStorageDatabase.class)
                        .build();

        mAppInstallDao = sharedDb.appInstallDao();
        mFrequencyCapDao = sharedDb.frequencyCapDao();
        mEncryptionKeyDao = EncryptionKeyDao.getInstance();
        mEnrollmentDao = EnrollmentDao.getInstance();
        mAdFilteringFeatureFactory =
                new AdFilteringFeatureFactory(mAppInstallDao, mFrequencyCapDao, mFakeFlags);

        mBiddingLogicUri = (mMockWebServerRule.uriForPath(mFetchJavaScriptPathBuyer));

        mCustomAudienceSignals =
                CustomAudienceSignalsFixture.aCustomAudienceSignalsBuilder()
                        .setBuyer(AdTechIdentifier.fromString(mBiddingLogicUri.getHost()))
                        .build();

        Map<AdTechIdentifier, AdSelectionSignals> perBuyerSignals =
                Map.of(
                        AdTechIdentifier.fromString("test.com"),
                        AdSelectionSignals.fromString("{\"buyer_signals\":1}"),
                        AdTechIdentifier.fromString("test2.com"),
                        AdSelectionSignals.fromString("{\"buyer_signals\":2}"),
                        AdTechIdentifier.fromString("test3.com"),
                        AdSelectionSignals.fromString("{\"buyer_signals\":3}"),
                        AdTechIdentifier.fromString(mBiddingLogicUri.getHost()),
                        AdSelectionSignals.fromString("{\"buyer_signals\":0}"));

        mSeller =
                AdTechIdentifier.fromString(
                        mMockWebServerRule.uriForPath(mFetchJavaScriptPathSeller).getHost());

        mAdSelectionConfigBuilder =
                AdSelectionConfigFixture.anAdSelectionConfigBuilder()
                        .setSeller(mSeller)
                        .setDecisionLogicUri(
                                mMockWebServerRule.uriForPath(mFetchJavaScriptPathSeller))
                        .setTrustedScoringSignalsUri(
                                mMockWebServerRule.uriForPath(mFetchTrustedScoringSignalsPath))
                        .setPerBuyerSignals(perBuyerSignals);

        doNothing()
                .when(mAdSelectionServiceFilterMock)
                .filterRequest(
                        mSeller,
                        TEST_PACKAGE_NAME,
                        true,
                        true,
                        true,
                        CALLER_UID,
                        AdServicesStatsLog.AD_SERVICES_API_CALLED__API_NAME__REPORT_IMPRESSION,
                        Throttler.ApiKey.FLEDGE_API_REPORT_IMPRESSIONS,
                        DevContext.createForDevOptionsDisabled());

        when(ConsentManager.getInstance()).thenReturn(mConsentManagerMock);
        when(AppImportanceFilter.create(any(), any(), anyBoolean()))
                .thenReturn(mAppImportanceFilterMock);
        doNothing()
                .when(mAppImportanceFilterMock)
                .assertCallerIsInForeground(anyInt(), anyInt(), any());
        mockCreateDevContextForDevOptionsDisabled();
        mRetryStrategyFactory = RetryStrategyFactory.createInstanceForTesting();
        mAdSelectionService =
                new AdSelectionServiceImpl(
                        mAdSelectionEntryDao,
                        mAppInstallDao,
                        mCustomAudienceDao,
                        mEncodedPayloadDao,
                        mFrequencyCapDao,
                        mEncryptionKeyDao,
                        mEnrollmentDao,
                        mClientSpy,
                        mDevContextFilterMock,
                        mLightweightExecutorService,
                        mBackgroundExecutorService,
                        mScheduledExecutor,
                        mContext,
                        mAdServicesLoggerMock,
                        mFakeFlags,
                        mFakeDebugFlags,
                        CallingAppUidSupplierProcessImpl.create(),
                        mFledgeAuthorizationFilterMock,
                        mAdSelectionServiceFilterMock,
                        mAdFilteringFeatureFactory,
                        mConsentManagerMock,
                        mObliviousHttpEncryptor,
                        mAdSelectionDebugReportDao,
                        mAdIdFetcher,
                        mUnusedKAnonSignJoinFactory,
                        false,
                        mRetryStrategyFactory,
                        CONSOLE_MESSAGE_IN_LOGS_ENABLED,
                        mAuctionServerDebugConfigurationGenerator,
                        mServerAuctionCoordinatorUriStrategyFactoryMock);
    }

    @Test
    public void testGetAdSelectionData_succeeded_shouldReturnDisabledStatus() throws Exception {
        GetAdSelectionDataInput input = TEST_GET_AD_SELECTION_DATA_INPUT;
        CountDownLatch resultLatch = new CountDownLatch(1);
        GetAdSelectionDataTestCallback callback = new GetAdSelectionDataTestCallback(resultLatch);

        mAdSelectionService.getAdSelectionData(input, TEST_CALLER_METADATA, callback);
        resultLatch.await();

        FledgeErrorResponse response = callback.mFledgeErrorResponse;
        assertWithMessage("Check API disabled response code")
                .that(response.getStatusCode())
                .isEqualTo(STATUS_ADSERVICES_DISABLED);
        verifyStatsdLoggerCalled(
                AD_SERVICES_API_CALLED__API_NAME__GET_AD_SELECTION_DATA,
                TEST_PACKAGE_NAME,
                STATUS_ADSERVICES_DISABLED);
    }

    @Test
    @ExpectErrorLogUtilWithExceptionCall(
            errorCode = AD_SERVICES_ERROR_REPORTED__ERROR_CODE__API_CALLBACK_ERROR)
    public void testGetAdSelectionData_callbackFailed_shouldLogCel() throws Exception {
        doThrow(new RemoteException()).when(mMockGetAdSelectionDataCallback).onFailure(any());
        GetAdSelectionDataInput input = TEST_GET_AD_SELECTION_DATA_INPUT;
        CountDownLatch resultLatch = genCountDownLatchForStatsdIgnoringCallback();

        mAdSelectionService.getAdSelectionData(
                input, TEST_CALLER_METADATA, mMockGetAdSelectionDataCallback);
        resultLatch.await();

        verifyStatsdLoggerCalled(
                AD_SERVICES_API_CALLED__API_NAME__GET_AD_SELECTION_DATA,
                TEST_PACKAGE_NAME,
                STATUS_ADSERVICES_DISABLED);
    }

    @Test
    public void testReportImpression_succeeded_shouldReturnDisabledStatus() throws Exception {
        ReportImpressionInput input =
                new ReportImpressionInput.Builder()
                        .setAdSelectionId(AD_SELECTION_ID)
                        .setAdSelectionConfig(mAdSelectionConfigBuilder.build())
                        .setCallerPackageName(TEST_PACKAGE_NAME)
                        .build();
        CountDownLatch resultLatch = genCountDownLatchForStatsd();
        ReportImpressionTestCallback callback = new ReportImpressionTestCallback(resultLatch);

        mAdSelectionService.reportImpression(input, callback);
        resultLatch.await();

        FledgeErrorResponse response = callback.mFledgeErrorResponse;
        assertWithMessage("Check API deprecation response code")
                .that(response.getStatusCode())
                .isEqualTo(STATUS_ADSERVICES_DISABLED);
        verifyStatsdLoggerCalled(
                AD_SERVICES_API_CALLED__API_NAME__REPORT_IMPRESSION,
                TEST_PACKAGE_NAME,
                STATUS_ADSERVICES_DISABLED);
    }

    @Test
    @ExpectErrorLogUtilWithExceptionCall(
            errorCode = AD_SERVICES_ERROR_REPORTED__ERROR_CODE__API_CALLBACK_ERROR)
    public void testReportImpression_callbackFailed_shouldLogCel() throws Exception {
        ReportImpressionInput input =
                new ReportImpressionInput.Builder()
                        .setAdSelectionId(AD_SELECTION_ID)
                        .setAdSelectionConfig(mAdSelectionConfigBuilder.build())
                        .setCallerPackageName(TEST_PACKAGE_NAME)
                        .build();
        CountDownLatch resultLatch = genCountDownLatchForStatsdIgnoringCallback();
        doThrow(new RemoteException()).when(mMockReportImpressionTestCallback).onFailure(any());

        mAdSelectionService.reportImpression(input, mMockReportImpressionTestCallback);
        resultLatch.await();

        verifyStatsdLoggerCalled(
                AD_SERVICES_API_CALLED__API_NAME__REPORT_IMPRESSION,
                TEST_PACKAGE_NAME,
                STATUS_ADSERVICES_DISABLED);
    }

    @Test
    public void testPersistAdSelectionResult_succeeded_shouldReturnDisabledStatus()
            throws Exception {
        CountDownLatch resultLatch = new CountDownLatch(1);
        PersistAdSelectionResultTestCallback callback =
                new PersistAdSelectionResultTestCallback(resultLatch);

        mAdSelectionService.persistAdSelectionResult(
                TEST_PERSIST_AD_SELECTION_RESULT_INPUT, TEST_CALLER_METADATA, callback);
        resultLatch.await();

        assertWithMessage("Check API disabled response code")
                .that(callback.mFledgeErrorResponse.getStatusCode())
                .isEqualTo(STATUS_ADSERVICES_DISABLED);
        verifyStatsdLoggerCalled(
                AD_SERVICES_API_CALLED__API_NAME__PERSIST_AD_SELECTION_RESULT,
                TEST_PACKAGE_NAME,
                STATUS_ADSERVICES_DISABLED);
    }

    @Test
    @ExpectErrorLogUtilWithExceptionCall(
            errorCode = AD_SERVICES_ERROR_REPORTED__ERROR_CODE__API_CALLBACK_ERROR)
    public void testPersistAdSelectionResult_callbackFailed_shouldLogCel() throws Exception {
        doThrow(new RemoteException()).when(mMockPersistAdSelectionResultCallback).onFailure(any());
        CountDownLatch resultLatch = genCountDownLatchForStatsdIgnoringCallback();

        mAdSelectionService.persistAdSelectionResult(
                TEST_PERSIST_AD_SELECTION_RESULT_INPUT,
                TEST_CALLER_METADATA,
                mMockPersistAdSelectionResultCallback);
        resultLatch.await();

        verifyStatsdLoggerCalled(
                AD_SERVICES_API_CALLED__API_NAME__PERSIST_AD_SELECTION_RESULT,
                TEST_PACKAGE_NAME,
                STATUS_ADSERVICES_DISABLED);
    }

    @Test
    public void testSelectAds_succeeded_shouldReturnDisabledStatus() throws Exception {
        CountDownLatch resultLatch = new CountDownLatch(1);
        AdSelectionTestCallback callback = new AdSelectionTestCallback(resultLatch);

        mAdSelectionService.selectAds(TEST_AD_SELECTION_INPUT, TEST_CALLER_METADATA, callback);
        resultLatch.await();

        assertWithMessage("Check API disabled response code")
                .that(callback.mFledgeErrorResponse.getStatusCode())
                .isEqualTo(STATUS_ADSERVICES_DISABLED);
        verifyStatsdLoggerCalled(
                AD_SERVICES_API_CALLED__API_NAME__SELECT_ADS,
                TEST_PACKAGE_NAME,
                STATUS_ADSERVICES_DISABLED);
    }

    @Test
    @ExpectErrorLogUtilWithExceptionCall(
            errorCode = AD_SERVICES_ERROR_REPORTED__ERROR_CODE__API_CALLBACK_ERROR)
    public void testSelectAds_callbackFailed_shouldLogCel() throws Exception {
        doThrow(new RemoteException()).when(mMockAdSelectionCallback).onFailure(any());
        CountDownLatch resultLatch = genCountDownLatchForStatsdIgnoringCallback();

        mAdSelectionService.selectAds(
                TEST_AD_SELECTION_INPUT, TEST_CALLER_METADATA, mMockAdSelectionCallback);
        resultLatch.await();

        verifyStatsdLoggerCalled(
                AD_SERVICES_API_CALLED__API_NAME__SELECT_ADS,
                TEST_PACKAGE_NAME,
                STATUS_ADSERVICES_DISABLED);
    }

    @Test
    public void testSelectAdsFromOutcomes_succeeded_shouldReturnDisabledStatus() throws Exception {
        CountDownLatch resultLatch = new CountDownLatch(1);
        AdSelectionTestCallback callback = new AdSelectionTestCallback(resultLatch);

        mAdSelectionService.selectAdsFromOutcomes(
                TEST_AD_SELECTION_FROM_OUTCOMES_INPUT, TEST_CALLER_METADATA, callback);
        resultLatch.await();

        assertWithMessage("Check API disabled response code")
                .that(callback.mFledgeErrorResponse.getStatusCode())
                .isEqualTo(STATUS_ADSERVICES_DISABLED);
        verifyStatsdLoggerCalled(
                AD_SERVICES_API_CALLED__API_NAME__SELECT_ADS_FROM_OUTCOMES,
                TEST_PACKAGE_NAME,
                STATUS_ADSERVICES_DISABLED);
    }

    @Test
    @ExpectErrorLogUtilWithExceptionCall(
            errorCode = AD_SERVICES_ERROR_REPORTED__ERROR_CODE__API_CALLBACK_ERROR)
    public void testSelectAdsFromOutcomes_callbackFailed_shouldLogCel() throws Exception {
        doThrow(new RemoteException()).when(mMockAdSelectionCallback).onFailure(any());
        CountDownLatch resultLatch = genCountDownLatchForStatsdIgnoringCallback();

        mAdSelectionService.selectAdsFromOutcomes(
                TEST_AD_SELECTION_FROM_OUTCOMES_INPUT,
                TEST_CALLER_METADATA,
                mMockAdSelectionCallback);
        resultLatch.await();

        verifyStatsdLoggerCalled(
                AD_SERVICES_API_CALLED__API_NAME__SELECT_ADS_FROM_OUTCOMES,
                TEST_PACKAGE_NAME,
                STATUS_ADSERVICES_DISABLED);
    }

    @Test
    public void testReportInteraction_succeeded_shouldReturnDisabledStatus() throws Exception {
        CountDownLatch resultLatch = new CountDownLatch(1);
        ReportInteractionTestCallback callback = new ReportInteractionTestCallback(resultLatch);

        mAdSelectionService.reportInteraction(TEST_REPORT_INTERACTION_INPUT, callback);
        resultLatch.await();

        assertWithMessage("Check API disabled response code")
                .that(callback.mFledgeErrorResponse.getStatusCode())
                .isEqualTo(STATUS_ADSERVICES_DISABLED);
        verifyStatsdLoggerCalled(
                AD_SERVICES_API_CALLED__API_NAME__REPORT_INTERACTION,
                TEST_PACKAGE_NAME,
                STATUS_ADSERVICES_DISABLED);
    }

    @Test
    @ExpectErrorLogUtilWithExceptionCall(
            errorCode = AD_SERVICES_ERROR_REPORTED__ERROR_CODE__API_CALLBACK_ERROR)
    public void testReportInteraction_callbackFailed_shouldLogCel() throws Exception {
        doThrow(new RemoteException()).when(mMockReportInteractionCallback).onFailure(any());
        CountDownLatch resultLatch = genCountDownLatchForStatsdIgnoringCallback();

        mAdSelectionService.reportInteraction(
                TEST_REPORT_INTERACTION_INPUT, mMockReportInteractionCallback);
        resultLatch.await();

        verifyStatsdLoggerCalled(
                AD_SERVICES_API_CALLED__API_NAME__REPORT_INTERACTION,
                TEST_PACKAGE_NAME,
                STATUS_ADSERVICES_DISABLED);
    }

    @Test
    public void testSetAppInstallAdvertisers_succeeded_shouldReturnDisabledStatus()
            throws Exception {
        CountDownLatch resultLatch = new CountDownLatch(1);
        SetAppInstallAdvertisersTestCallback callback =
                new SetAppInstallAdvertisersTestCallback(resultLatch);

        mAdSelectionService.setAppInstallAdvertisers(
                TEST_SET_APP_INSTALL_ADVERTISERS_INPUT, callback);
        resultLatch.await();

        assertWithMessage("Check API disabled response code")
                .that(callback.mFledgeErrorResponse.getStatusCode())
                .isEqualTo(STATUS_ADSERVICES_DISABLED);
        verifyStatsdLoggerCalled(
                AD_SERVICES_API_CALLED__API_NAME__SET_APP_INSTALL_ADVERTISERS,
                TEST_PACKAGE_NAME,
                STATUS_ADSERVICES_DISABLED);
    }

    @Test
    @ExpectErrorLogUtilWithExceptionCall(
            errorCode = AD_SERVICES_ERROR_REPORTED__ERROR_CODE__API_CALLBACK_ERROR)
    public void testSetAppInstallAdvertisers_callbackFailed_shouldLogCel() throws Exception {
        doThrow(new RemoteException()).when(mMockSetAppInstallAdvertisersCallback).onFailure(any());
        CountDownLatch resultLatch = genCountDownLatchForStatsdIgnoringCallback();

        mAdSelectionService.setAppInstallAdvertisers(
                TEST_SET_APP_INSTALL_ADVERTISERS_INPUT, mMockSetAppInstallAdvertisersCallback);
        resultLatch.await();

        verifyStatsdLoggerCalled(
                AD_SERVICES_API_CALLED__API_NAME__SET_APP_INSTALL_ADVERTISERS,
                TEST_PACKAGE_NAME,
                STATUS_ADSERVICES_DISABLED);
    }

    @Test
    public void testUpdateAdCounterHistogram_succeeded_shouldReturnDisabledStatus()
            throws Exception {
        CountDownLatch resultLatch = new CountDownLatch(1);
        UpdateAdCounterHistogramTestCallback callback =
                new UpdateAdCounterHistogramTestCallback(resultLatch);

        mAdSelectionService.updateAdCounterHistogram(
                TEST_UPDATE_AD_COUNTER_HISTOGRAM_INPUT, callback);
        resultLatch.await();

        assertWithMessage("Check API disabled response code")
                .that(callback.mFledgeErrorResponse.getStatusCode())
                .isEqualTo(STATUS_ADSERVICES_DISABLED);
        verifyStatsdLoggerCalled(
                AD_SERVICES_API_CALLED__API_NAME__UPDATE_AD_COUNTER_HISTOGRAM,
                TEST_PACKAGE_NAME,
                STATUS_ADSERVICES_DISABLED);
    }

    @Test
    @ExpectErrorLogUtilWithExceptionCall(
            errorCode = AD_SERVICES_ERROR_REPORTED__ERROR_CODE__API_CALLBACK_ERROR)
    public void testUpdateAdCounterHistogram_callbackFailed_shouldLogCel() throws Exception {
        doThrow(new RemoteException()).when(mMockUpdateAdCounterHistogramCallback).onFailure(any());
        CountDownLatch resultLatch = genCountDownLatchForStatsdIgnoringCallback();

        mAdSelectionService.updateAdCounterHistogram(
                TEST_UPDATE_AD_COUNTER_HISTOGRAM_INPUT, mMockUpdateAdCounterHistogramCallback);
        resultLatch.await();

        verifyStatsdLoggerCalled(
                AD_SERVICES_API_CALLED__API_NAME__UPDATE_AD_COUNTER_HISTOGRAM,
                TEST_PACKAGE_NAME,
                STATUS_ADSERVICES_DISABLED);
    }

    @Test
    public void testOverrideAdSelectionConfigRemoteInfo_succeeded_shouldReturnDisabledStatus()
            throws Exception {
        CountDownLatch resultLatch = new CountDownLatch(1);
        AdSelectionOverrideTestCallback callback = new AdSelectionOverrideTestCallback(resultLatch);

        mAdSelectionService.overrideAdSelectionConfigRemoteInfo(
                TEST_AD_SELECTION_CONFIG,
                "js",
                AdSelectionSignals.EMPTY,
                new PerBuyerDecisionLogic(ImmutableMap.of()),
                callback);
        resultLatch.await();

        assertWithMessage("Check API disabled response code")
                .that(callback.mFledgeErrorResponse.getStatusCode())
                .isEqualTo(STATUS_ADSERVICES_DISABLED);
        verifyStatsdLoggerCalled(
                AD_SERVICES_API_CALLED__API_NAME__OVERRIDE_AD_SELECTION_CONFIG_REMOTE_INFO,
                STATUS_ADSERVICES_DISABLED);
    }

    @Test
    @ExpectErrorLogUtilWithExceptionCall(
            errorCode = AD_SERVICES_ERROR_REPORTED__ERROR_CODE__API_CALLBACK_ERROR)
    public void testOverrideAdSelectionConfigRemoteInfo_callbackFailed_shouldLogCel()
            throws Exception {
        doThrow(new RemoteException()).when(mMockAdSelectionOverrideCallback).onFailure(any());
        CountDownLatch resultLatch = genCountDownLatchForStatsdIgnoringCallback();

        mAdSelectionService.overrideAdSelectionConfigRemoteInfo(
                TEST_AD_SELECTION_CONFIG,
                "js",
                AdSelectionSignals.EMPTY,
                new PerBuyerDecisionLogic(ImmutableMap.of()),
                mMockAdSelectionOverrideCallback);
        resultLatch.await();

        verifyStatsdLoggerCalled(
                AD_SERVICES_API_CALLED__API_NAME__OVERRIDE_AD_SELECTION_CONFIG_REMOTE_INFO,
                STATUS_ADSERVICES_DISABLED);
    }

    @Test
    public void testRemoveAdSelectionConfigRemoteInfoOverride_succeeded_shouldReturnDisabledStatus()
            throws Exception {
        CountDownLatch resultLatch = new CountDownLatch(1);
        AdSelectionOverrideTestCallback callback = new AdSelectionOverrideTestCallback(resultLatch);

        mAdSelectionService.removeAdSelectionConfigRemoteInfoOverride(
                TEST_AD_SELECTION_CONFIG, callback);
        resultLatch.await();

        assertWithMessage("Check API disabled response code")
                .that(callback.mFledgeErrorResponse.getStatusCode())
                .isEqualTo(STATUS_ADSERVICES_DISABLED);
        verifyStatsdLoggerCalled(
                AD_SERVICES_API_CALLED__API_NAME__REMOVE_AD_SELECTION_CONFIG_REMOTE_INFO_OVERRIDE,
                STATUS_ADSERVICES_DISABLED);
    }

    @Test
    @ExpectErrorLogUtilWithExceptionCall(
            errorCode = AD_SERVICES_ERROR_REPORTED__ERROR_CODE__API_CALLBACK_ERROR)
    public void testRemoveAdSelectionConfigRemoteInfoOverride_callbackFailed_shouldLogCel()
            throws Exception {
        doThrow(new RemoteException()).when(mMockAdSelectionOverrideCallback).onFailure(any());
        CountDownLatch resultLatch = genCountDownLatchForStatsdIgnoringCallback();

        mAdSelectionService.removeAdSelectionConfigRemoteInfoOverride(
                TEST_AD_SELECTION_CONFIG, mMockAdSelectionOverrideCallback);
        resultLatch.await();

        verifyStatsdLoggerCalled(
                AD_SERVICES_API_CALLED__API_NAME__REMOVE_AD_SELECTION_CONFIG_REMOTE_INFO_OVERRIDE,
                STATUS_ADSERVICES_DISABLED);
    }

    @Test
    public void testResetAllAdSelectionConfigRemoteOverrides_succeeded_shouldReturnDisabledStatus()
            throws Exception {
        CountDownLatch resultLatch = new CountDownLatch(1);
        AdSelectionOverrideTestCallback callback = new AdSelectionOverrideTestCallback(resultLatch);

        mAdSelectionService.resetAllAdSelectionConfigRemoteOverrides(callback);
        resultLatch.await();

        assertWithMessage("Check API disabled response code")
                .that(callback.mFledgeErrorResponse.getStatusCode())
                .isEqualTo(STATUS_ADSERVICES_DISABLED);
        verifyStatsdLoggerCalled(
                AD_SERVICES_API_CALLED__API_NAME__RESET_ALL_AD_SELECTION_CONFIG_REMOTE_OVERRIDES,
                STATUS_ADSERVICES_DISABLED);
    }

    @Test
    @ExpectErrorLogUtilWithExceptionCall(
            errorCode = AD_SERVICES_ERROR_REPORTED__ERROR_CODE__API_CALLBACK_ERROR)
    public void testResetAllAdSelectionConfigRemoteOverrides_callbackFailed_shouldLogCel()
            throws Exception {
        doThrow(new RemoteException()).when(mMockAdSelectionOverrideCallback).onFailure(any());
        CountDownLatch resultLatch = genCountDownLatchForStatsdIgnoringCallback();

        mAdSelectionService.resetAllAdSelectionConfigRemoteOverrides(
                mMockAdSelectionOverrideCallback);
        resultLatch.await();

        verifyStatsdLoggerCalled(
                AD_SERVICES_API_CALLED__API_NAME__RESET_ALL_AD_SELECTION_CONFIG_REMOTE_OVERRIDES,
                STATUS_ADSERVICES_DISABLED);
    }

    @Test
    public void
            testOverrideAdSelectionFromOutcomesConfigRemoteInfo_succeeded_shouldReturnDisabledStatus()
                    throws Exception {
        CountDownLatch resultLatch = new CountDownLatch(1);
        AdSelectionOverrideTestCallback callback = new AdSelectionOverrideTestCallback(resultLatch);

        mAdSelectionService.overrideAdSelectionFromOutcomesConfigRemoteInfo(
                TEST_AD_SELECTION_FROM_OUTCOMES_CONFIG, "js", AdSelectionSignals.EMPTY, callback);
        resultLatch.await();

        assertWithMessage("Check API disabled response code")
                .that(callback.mFledgeErrorResponse.getStatusCode())
                .isEqualTo(STATUS_ADSERVICES_DISABLED);
        verifyStatsdLoggerCalled(
                AD_SERVICES_API_CALLED__API_NAME__API_NAME_UNKNOWN, STATUS_ADSERVICES_DISABLED);
    }

    @Test
    @ExpectErrorLogUtilWithExceptionCall(
            errorCode = AD_SERVICES_ERROR_REPORTED__ERROR_CODE__API_CALLBACK_ERROR)
    public void testOverrideAdSelectionFromOutcomesConfigRemoteInfo_callbackFailed_shouldLogCel()
            throws Exception {
        doThrow(new RemoteException()).when(mMockAdSelectionOverrideCallback).onFailure(any());
        CountDownLatch resultLatch = genCountDownLatchForStatsdIgnoringCallback();

        mAdSelectionService.overrideAdSelectionFromOutcomesConfigRemoteInfo(
                TEST_AD_SELECTION_FROM_OUTCOMES_CONFIG,
                "js",
                AdSelectionSignals.EMPTY,
                mMockAdSelectionOverrideCallback);
        resultLatch.await();

        verifyStatsdLoggerCalled(
                AD_SERVICES_API_CALLED__API_NAME__API_NAME_UNKNOWN, STATUS_ADSERVICES_DISABLED);
    }

    @Test
    public void
            testRemoveAdSelectionFromOutcomesConfigRemoteInfoOverride_succeeded_shouldReturnDisabledStatus()
                    throws Exception {
        CountDownLatch resultLatch = new CountDownLatch(1);
        AdSelectionOverrideTestCallback callback = new AdSelectionOverrideTestCallback(resultLatch);

        mAdSelectionService.removeAdSelectionFromOutcomesConfigRemoteInfoOverride(
                TEST_AD_SELECTION_FROM_OUTCOMES_CONFIG, callback);
        resultLatch.await();

        assertWithMessage("Check API disabled response code")
                .that(callback.mFledgeErrorResponse.getStatusCode())
                .isEqualTo(STATUS_ADSERVICES_DISABLED);
        verifyStatsdLoggerCalled(
                AD_SERVICES_API_CALLED__API_NAME__API_NAME_UNKNOWN, STATUS_ADSERVICES_DISABLED);
    }

    @Test
    @ExpectErrorLogUtilWithExceptionCall(
            errorCode = AD_SERVICES_ERROR_REPORTED__ERROR_CODE__API_CALLBACK_ERROR)
    public void
            testRemoveAdSelectionFromOutcomesConfigRemoteInfoOverride_callbackFailed_shouldLogCel()
                    throws Exception {
        doThrow(new RemoteException()).when(mMockAdSelectionOverrideCallback).onFailure(any());
        CountDownLatch resultLatch = genCountDownLatchForStatsdIgnoringCallback();

        mAdSelectionService.removeAdSelectionFromOutcomesConfigRemoteInfoOverride(
                TEST_AD_SELECTION_FROM_OUTCOMES_CONFIG, mMockAdSelectionOverrideCallback);
        resultLatch.await();

        verifyStatsdLoggerCalled(
                AD_SERVICES_API_CALLED__API_NAME__API_NAME_UNKNOWN, STATUS_ADSERVICES_DISABLED);
    }

    @Test
    public void
            testResetAllAdSelectionFromOutcomesConfigRemoteOverrides_succeeded_shouldReturnDisabledStatus()
                    throws Exception {
        CountDownLatch resultLatch = new CountDownLatch(1);
        AdSelectionOverrideTestCallback callback = new AdSelectionOverrideTestCallback(resultLatch);

        mAdSelectionService.resetAllAdSelectionFromOutcomesConfigRemoteOverrides(callback);
        resultLatch.await();

        assertWithMessage("Check API disabled response code")
                .that(callback.mFledgeErrorResponse.getStatusCode())
                .isEqualTo(STATUS_ADSERVICES_DISABLED);
        verifyStatsdLoggerCalled(
                AD_SERVICES_API_CALLED__API_NAME__API_NAME_UNKNOWN, STATUS_ADSERVICES_DISABLED);
    }

    @Test
    @ExpectErrorLogUtilWithExceptionCall(
            errorCode = AD_SERVICES_ERROR_REPORTED__ERROR_CODE__API_CALLBACK_ERROR)
    public void
            testResetAllAdSelectionFromOutcomesConfigRemoteOverrides_callbackFailed_shouldLogCel()
                    throws Exception {
        doThrow(new RemoteException()).when(mMockAdSelectionOverrideCallback).onFailure(any());
        CountDownLatch resultLatch = genCountDownLatchForStatsdIgnoringCallback();

        mAdSelectionService.resetAllAdSelectionFromOutcomesConfigRemoteOverrides(
                mMockAdSelectionOverrideCallback);
        resultLatch.await();

        verifyStatsdLoggerCalled(
                AD_SERVICES_API_CALLED__API_NAME__API_NAME_UNKNOWN, STATUS_ADSERVICES_DISABLED);
    }

    @Test
    public void testSetAdCounterHistogramOverride_succeeded_shouldReturnDisabledStatus()
            throws Exception {
        CountDownLatch resultLatch = new CountDownLatch(1);
        AdSelectionOverrideTestCallback callback = new AdSelectionOverrideTestCallback(resultLatch);

        mAdSelectionService.setAdCounterHistogramOverride(
                TEST_SET_AD_COUNTER_HISTOGRAM_OVERRIDE_INPUT, callback);
        resultLatch.await();

        assertWithMessage("Check API disabled response code")
                .that(callback.mFledgeErrorResponse.getStatusCode())
                .isEqualTo(STATUS_ADSERVICES_DISABLED);
        verifyStatsdLoggerCalled(
                AD_SERVICES_API_CALLED__API_NAME__API_NAME_UNKNOWN, STATUS_ADSERVICES_DISABLED);
    }

    @Test
    @ExpectErrorLogUtilWithExceptionCall(
            errorCode = AD_SERVICES_ERROR_REPORTED__ERROR_CODE__API_CALLBACK_ERROR)
    public void testSetAdCounterHistogramOverride_callbackFailed_shouldLogCel() throws Exception {
        doThrow(new RemoteException()).when(mMockAdSelectionOverrideCallback).onFailure(any());
        CountDownLatch resultLatch = genCountDownLatchForStatsdIgnoringCallback();

        mAdSelectionService.setAdCounterHistogramOverride(
                TEST_SET_AD_COUNTER_HISTOGRAM_OVERRIDE_INPUT, mMockAdSelectionOverrideCallback);
        resultLatch.await();

        verifyStatsdLoggerCalled(
                AD_SERVICES_API_CALLED__API_NAME__API_NAME_UNKNOWN, STATUS_ADSERVICES_DISABLED);
    }

    @Test
    public void testRemoveAdCounterHistogramOverride_succeeded_shouldReturnDisabledStatus()
            throws Exception {
        CountDownLatch resultLatch = new CountDownLatch(1);
        AdSelectionOverrideTestCallback callback = new AdSelectionOverrideTestCallback(resultLatch);

        mAdSelectionService.removeAdCounterHistogramOverride(
                TEST_REMOVE_AD_COUNTER_HISTOGRAM_OVERRIDE_INPUT, callback);
        resultLatch.await();

        assertWithMessage("Check API disabled response code")
                .that(callback.mFledgeErrorResponse.getStatusCode())
                .isEqualTo(STATUS_ADSERVICES_DISABLED);
        verifyStatsdLoggerCalled(
                AD_SERVICES_API_CALLED__API_NAME__API_NAME_UNKNOWN, STATUS_ADSERVICES_DISABLED);
    }

    @Test
    @ExpectErrorLogUtilWithExceptionCall(
            errorCode = AD_SERVICES_ERROR_REPORTED__ERROR_CODE__API_CALLBACK_ERROR)
    public void testRemoveAdCounterHistogramOverride_callbackFailed_shouldLogCel()
            throws Exception {
        doThrow(new RemoteException()).when(mMockAdSelectionOverrideCallback).onFailure(any());
        CountDownLatch resultLatch = genCountDownLatchForStatsdIgnoringCallback();

        mAdSelectionService.removeAdCounterHistogramOverride(
                TEST_REMOVE_AD_COUNTER_HISTOGRAM_OVERRIDE_INPUT, mMockAdSelectionOverrideCallback);
        resultLatch.await();

        verifyStatsdLoggerCalled(
                AD_SERVICES_API_CALLED__API_NAME__API_NAME_UNKNOWN, STATUS_ADSERVICES_DISABLED);
    }

    @Test
    public void testResetAllAdCounterHistogramOverrides_succeeded_shouldReturnDisabledStatus()
            throws Exception {
        CountDownLatch resultLatch = new CountDownLatch(1);
        AdSelectionOverrideTestCallback callback = new AdSelectionOverrideTestCallback(resultLatch);

        mAdSelectionService.resetAllAdCounterHistogramOverrides(callback);
        resultLatch.await();

        assertWithMessage("Check API disabled response code")
                .that(callback.mFledgeErrorResponse.getStatusCode())
                .isEqualTo(STATUS_ADSERVICES_DISABLED);
        verifyStatsdLoggerCalled(
                AD_SERVICES_API_CALLED__API_NAME__API_NAME_UNKNOWN, STATUS_ADSERVICES_DISABLED);
    }

    @Test
    @ExpectErrorLogUtilWithExceptionCall(
            errorCode = AD_SERVICES_ERROR_REPORTED__ERROR_CODE__API_CALLBACK_ERROR)
    public void testResetAllAdCounterHistogramOverrides_callbackFailed_shouldLogCel()
            throws Exception {
        doThrow(new RemoteException()).when(mMockAdSelectionOverrideCallback).onFailure(any());
        CountDownLatch resultLatch = genCountDownLatchForStatsdIgnoringCallback();

        mAdSelectionService.resetAllAdCounterHistogramOverrides(mMockAdSelectionOverrideCallback);
        resultLatch.await();

        verifyStatsdLoggerCalled(
                AD_SERVICES_API_CALLED__API_NAME__API_NAME_UNKNOWN, STATUS_ADSERVICES_DISABLED);
    }

    private CountDownLatch genCountDownLatchForStatsdWithCount(int count) {
        CountDownLatch resultLatch = new CountDownLatch(count);

        // Wait for the logging call, which happens after the callback
        Answer<Void> countDownAnswer =
                unused -> {
                    resultLatch.countDown();
                    return null;
                };
        doAnswer(countDownAnswer)
                .when(mAdServicesLoggerMock)
                .logFledgeApiCallStats(anyInt(), anyString(), anyInt(), anyInt());
        return resultLatch;
    }

    private CountDownLatch genCountDownLatchForStatsd() {
        return genCountDownLatchForStatsdWithCount(2);
    }

    private CountDownLatch genCountDownLatchForStatsdIgnoringCallback() {
        return genCountDownLatchForStatsdWithCount(1);
    }

    private void mockCreateDevContextForDevOptionsDisabled() {
        mockCreateDevContext(mDevContextFilterMock, DevContext.createForDevOptionsDisabled());
    }

    private void verifyStatsdLoggerCalled(int apiName, String appPackageName, int resultCode) {
        verify(mAdServicesLoggerMock)
                .logFledgeApiCallStats(
                        eq(apiName), eq(appPackageName), eq(resultCode), /* latencyMs= */ anyInt());
    }

    private void verifyStatsdLoggerCalled(int apiName, int resultCode) {
        verify(mAdServicesLoggerMock)
                .logFledgeApiCallStats(
                        eq(apiName), anyString(), eq(resultCode), /* latencyMs= */ anyInt());
    }

    private void mockCreateDevContext(DevContextFilter mockFilter, DevContext devContext) {
        when(mockFilter.createDevContext()).thenReturn(devContext);
    }

    private static class GetAdSelectionDataTestCallback extends GetAdSelectionDataCallback.Stub {
        protected final CountDownLatch mCountDownLatch;
        boolean mIsSuccess = false;
        FledgeErrorResponse mFledgeErrorResponse;
        GetAdSelectionDataResponse mAdSelectionData;

        GetAdSelectionDataTestCallback(CountDownLatch countDownLatch) {
            mCountDownLatch = countDownLatch;
        }

        @Override
        public void onSuccess(GetAdSelectionDataResponse getAdSelectionDataResponse)
                throws RemoteException {
            mIsSuccess = true;
            mAdSelectionData = getAdSelectionDataResponse;
            mCountDownLatch.countDown();
        }

        @Override
        public void onFailure(FledgeErrorResponse fledgeErrorResponse) throws RemoteException {
            mFledgeErrorResponse = fledgeErrorResponse;
            mCountDownLatch.countDown();
        }
    }

    private static class ReportImpressionTestCallback extends ReportImpressionCallback.Stub {
        protected final CountDownLatch mCountDownLatch;
        boolean mIsSuccess = false;
        FledgeErrorResponse mFledgeErrorResponse;

        ReportImpressionTestCallback(CountDownLatch countDownLatch) {
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

    public static class PersistAdSelectionResultTestCallback
            extends PersistAdSelectionResultCallback.Stub {
        protected final CountDownLatch mCountDownLatch;
        boolean mIsSuccess = false;
        FledgeErrorResponse mFledgeErrorResponse;
        PersistAdSelectionResultResponse mPersistAdSelectionResultResponse;

        PersistAdSelectionResultTestCallback(CountDownLatch countDownLatch) {
            mCountDownLatch = countDownLatch;
        }

        @Override
        public void onSuccess(PersistAdSelectionResultResponse persistAdSelectionResultResponse)
                throws RemoteException {
            mIsSuccess = true;
            mPersistAdSelectionResultResponse = persistAdSelectionResultResponse;
            mCountDownLatch.countDown();
        }

        @Override
        public void onFailure(FledgeErrorResponse fledgeErrorResponse) throws RemoteException {
            mFledgeErrorResponse = fledgeErrorResponse;
            mCountDownLatch.countDown();
        }
    }

    private static class AdSelectionTestCallback extends AdSelectionCallback.Stub {
        protected final CountDownLatch mCountDownLatch;
        boolean mIsSuccess = false;
        FledgeErrorResponse mFledgeErrorResponse;
        AdSelectionResponse mAdSelectionResponse;

        AdSelectionTestCallback(CountDownLatch countDownLatch) {
            mCountDownLatch = countDownLatch;
        }

        @Override
        public void onSuccess(AdSelectionResponse adSelectionResponse) throws RemoteException {
            mIsSuccess = true;
            mAdSelectionResponse = adSelectionResponse;
            mCountDownLatch.countDown();
        }

        @Override
        public void onFailure(FledgeErrorResponse fledgeErrorResponse) throws RemoteException {
            mFledgeErrorResponse = fledgeErrorResponse;

            mCountDownLatch.countDown();
        }
    }

    private static class ReportInteractionTestCallback extends ReportInteractionCallback.Stub {
        protected final CountDownLatch mCountDownLatch;
        boolean mIsSuccess = false;
        FledgeErrorResponse mFledgeErrorResponse;

        ReportInteractionTestCallback(CountDownLatch countDownLatch) {
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

    private static class SetAppInstallAdvertisersTestCallback
            extends SetAppInstallAdvertisersCallback.Stub {
        protected final CountDownLatch mCountDownLatch;
        boolean mIsSuccess = false;
        FledgeErrorResponse mFledgeErrorResponse;

        SetAppInstallAdvertisersTestCallback(CountDownLatch countDownLatch) {
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

    private static class UpdateAdCounterHistogramTestCallback
            extends UpdateAdCounterHistogramCallback.Stub {
        protected final CountDownLatch mCountDownLatch;
        boolean mIsSuccess = false;
        FledgeErrorResponse mFledgeErrorResponse;

        UpdateAdCounterHistogramTestCallback(CountDownLatch countDownLatch) {
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

    private static class AdSelectionOverrideTestCallback extends AdSelectionOverrideCallback.Stub {
        protected final CountDownLatch mCountDownLatch;
        boolean mIsSuccess = false;
        FledgeErrorResponse mFledgeErrorResponse;

        AdSelectionOverrideTestCallback(CountDownLatch countDownLatch) {
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

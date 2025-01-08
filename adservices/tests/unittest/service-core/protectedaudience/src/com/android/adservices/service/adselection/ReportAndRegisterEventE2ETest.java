/*
 * Copyright (C) 2023 The Android Open Source Project
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
import static android.adservices.common.AdServicesStatusUtils.STATUS_SUCCESS;
import static android.adservices.common.CommonFixture.TEST_PACKAGE_NAME;
import static android.view.MotionEvent.ACTION_BUTTON_PRESS;

import static com.android.adservices.service.FlagsConstants.KEY_ASYNC_REGISTRATION_JOB_QUEUE_INTERVAL_MS;
import static com.android.adservices.service.FlagsConstants.KEY_FLEDGE_APP_PACKAGE_NAME_LOGGING_ENABLED;
import static com.android.adservices.service.FlagsConstants.KEY_FLEDGE_MEASUREMENT_REPORT_AND_REGISTER_EVENT_API_ENABLED;
import static com.android.adservices.service.FlagsConstants.KEY_FLEDGE_MEASUREMENT_REPORT_AND_REGISTER_EVENT_API_FALLBACK_ENABLED;
import static com.android.adservices.service.FlagsConstants.KEY_FLEDGE_REGISTER_AD_BEACON_ENABLED;
import static com.android.adservices.service.FlagsConstants.KEY_GA_UX_FEATURE_ENABLED;
import static com.android.adservices.service.FlagsConstants.KEY_MEASUREMENT_API_REGISTER_SOURCE_KILL_SWITCH;
import static com.android.adservices.service.FlagsConstants.KEY_MEASUREMENT_REGISTRATION_JOB_QUEUE_KILL_SWITCH;
import static com.android.adservices.service.adselection.ReportEventDisabledImpl.API_DISABLED_MESSAGE;
import static com.android.adservices.service.stats.AdServicesStatsLog.AD_SERVICES_API_CALLED__API_NAME__REPORT_INTERACTION;
import static com.android.dx.mockito.inline.extended.ExtendedMockito.any;
import static com.android.dx.mockito.inline.extended.ExtendedMockito.anyBoolean;
import static com.android.dx.mockito.inline.extended.ExtendedMockito.anyInt;
import static com.android.dx.mockito.inline.extended.ExtendedMockito.anyLong;
import static com.android.dx.mockito.inline.extended.ExtendedMockito.anyString;
import static com.android.dx.mockito.inline.extended.ExtendedMockito.doAnswer;
import static com.android.dx.mockito.inline.extended.ExtendedMockito.doReturn;
import static com.android.dx.mockito.inline.extended.ExtendedMockito.eq;
import static com.android.dx.mockito.inline.extended.ExtendedMockito.mock;
import static com.android.dx.mockito.inline.extended.ExtendedMockito.never;
import static com.android.dx.mockito.inline.extended.ExtendedMockito.spy;
import static com.android.dx.mockito.inline.extended.ExtendedMockito.verify;
import static com.android.dx.mockito.inline.extended.ExtendedMockito.when;

import static com.google.common.truth.Truth.assertThat;

import static org.junit.Assert.assertTrue;

import android.adservices.adselection.CustomAudienceSignalsFixture;
import android.adservices.adselection.ReportEventRequest;
import android.adservices.adselection.ReportInteractionCallback;
import android.adservices.adselection.ReportInteractionInput;
import android.adservices.common.AdTechIdentifier;
import android.adservices.common.CallingAppUidSupplierProcessImpl;
import android.adservices.common.CommonFixture;
import android.adservices.common.FledgeErrorResponse;
import android.adservices.http.MockWebServerRule;
import android.content.ContentResolver;
import android.content.Context;
import android.net.Uri;
import android.os.RemoteException;
import android.view.InputEvent;
import android.view.MotionEvent;

import androidx.room.Room;
import androidx.test.core.app.ApplicationProvider;

import com.android.adservices.MockWebServerRuleFactory;
import com.android.adservices.common.AdServicesExtendedMockitoTestCase;
import com.android.adservices.common.DbTestUtil;
import com.android.adservices.common.annotations.SetMsmtApiAppAllowList;
import com.android.adservices.concurrency.AdServicesExecutors;
import com.android.adservices.data.adselection.AdSelectionDatabase;
import com.android.adservices.data.adselection.AdSelectionDebugReportDao;
import com.android.adservices.data.adselection.AdSelectionEntryDao;
import com.android.adservices.data.adselection.AppInstallDao;
import com.android.adservices.data.adselection.ConsentedDebugConfigurationDao;
import com.android.adservices.data.adselection.CustomAudienceSignals;
import com.android.adservices.data.adselection.DBAdSelection;
import com.android.adservices.data.adselection.DBRegisteredAdInteraction;
import com.android.adservices.data.adselection.FrequencyCapDao;
import com.android.adservices.data.adselection.SharedStorageDatabase;
import com.android.adservices.data.customaudience.CustomAudienceDao;
import com.android.adservices.data.customaudience.CustomAudienceDatabase;
import com.android.adservices.data.customaudience.DBCustomAudience;
import com.android.adservices.data.encryptionkey.EncryptionKeyDao;
import com.android.adservices.data.enrollment.EnrollmentDao;
import com.android.adservices.data.measurement.DatastoreException;
import com.android.adservices.data.measurement.DatastoreManager;
import com.android.adservices.data.measurement.DatastoreManagerFactory;
import com.android.adservices.data.measurement.SQLDatastoreManager;
import com.android.adservices.data.measurement.deletion.MeasurementDataDeleter;
import com.android.adservices.data.signals.EncodedPayloadDao;
import com.android.adservices.data.signals.ProtectedSignalsDatabase;
import com.android.adservices.service.FakeFlagsFactory;
import com.android.adservices.service.Flags;
import com.android.adservices.service.FlagsFactory;
import com.android.adservices.service.adselection.debug.AuctionServerDebugConfigurationGenerator;
import com.android.adservices.service.adselection.debug.ConsentedDebugConfigurationGeneratorFactory;
import com.android.adservices.service.adselection.encryption.ObliviousHttpEncryptor;
import com.android.adservices.service.common.AdSelectionServiceFilter;
import com.android.adservices.service.common.AllowLists;
import com.android.adservices.service.common.AppImportanceFilter;
import com.android.adservices.service.common.FledgeAuthorizationFilter;
import com.android.adservices.service.common.PermissionHelper;
import com.android.adservices.service.common.RetryStrategyFactory;
import com.android.adservices.service.common.cache.CacheProviderFactory;
import com.android.adservices.service.common.compat.ServiceCompatUtils;
import com.android.adservices.service.common.httpclient.AdServicesHttpsClient;
import com.android.adservices.service.consent.AdServicesApiConsent;
import com.android.adservices.service.consent.AdServicesApiType;
import com.android.adservices.service.consent.ConsentManager;
import com.android.adservices.service.devapi.DevContext;
import com.android.adservices.service.devapi.DevContextFilter;
import com.android.adservices.service.enrollment.EnrollmentData;
import com.android.adservices.service.kanon.KAnonSignJoinFactory;
import com.android.adservices.service.measurement.MeasurementImpl;
import com.android.adservices.service.measurement.inputverification.ClickVerifier;
import com.android.adservices.service.measurement.noising.SourceNoiseHandler;
import com.android.adservices.service.measurement.ondevicepersonalization.IOdpDelegationWrapper;
import com.android.adservices.service.measurement.ondevicepersonalization.NoOdpDelegationWrapper;
import com.android.adservices.service.measurement.registration.AsyncRegistrationQueueJobService;
import com.android.adservices.service.measurement.registration.AsyncRegistrationQueueRunner;
import com.android.adservices.service.measurement.registration.AsyncSourceFetcher;
import com.android.adservices.service.measurement.registration.AsyncTriggerFetcher;
import com.android.adservices.service.measurement.reporting.AggregateDebugReportApi;
import com.android.adservices.service.measurement.reporting.DebugReportApi;
import com.android.adservices.service.stats.AdServicesLogger;
import com.android.adservices.service.stats.AdServicesLoggerImpl;
import com.android.adservices.shared.errorlogging.AdServicesErrorLogger;
import com.android.adservices.shared.testing.annotations.SetFlagFalse;
import com.android.adservices.shared.testing.annotations.SetFlagTrue;
import com.android.adservices.shared.testing.annotations.SetLongFlag;
import com.android.adservices.spe.AdServicesJobServiceLogger;
import com.android.modules.utils.testing.ExtendedMockitoRule.MockStatic;
import com.android.modules.utils.testing.ExtendedMockitoRule.SpyStatic;

import com.google.common.util.concurrent.ListeningExecutorService;
import com.google.mockwebserver.MockResponse;
import com.google.mockwebserver.MockWebServer;

import org.json.JSONException;
import org.json.JSONObject;
import org.junit.After;
import org.junit.Before;
import org.junit.Rule;
import org.junit.Test;
import org.mockito.Mock;
import org.mockito.Spy;
import org.mockito.stubbing.Answer;

import java.time.Instant;
import java.util.List;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.ScheduledThreadPoolExecutor;

@MockStatic(ConsentManager.class)
@MockStatic(PermissionHelper.class)
@SpyStatic(MeasurementImpl.class)
@SpyStatic(AsyncRegistrationQueueJobService.class)
@SpyStatic(AdServicesLoggerImpl.class)
@SpyStatic(DatastoreManagerFactory.class)
@SpyStatic(EnrollmentDao.class)
@SpyStatic(FlagsFactory.class)
@SpyStatic(AdServicesJobServiceLogger.class)
@MockStatic(ServiceCompatUtils.class)
@SetFlagTrue(KEY_FLEDGE_REGISTER_AD_BEACON_ENABLED)
@SetFlagTrue(KEY_FLEDGE_MEASUREMENT_REPORT_AND_REGISTER_EVENT_API_ENABLED)
@SetFlagFalse(KEY_FLEDGE_MEASUREMENT_REPORT_AND_REGISTER_EVENT_API_FALLBACK_ENABLED)
@SetFlagFalse(KEY_MEASUREMENT_API_REGISTER_SOURCE_KILL_SWITCH)
@SetFlagFalse(KEY_MEASUREMENT_REGISTRATION_JOB_QUEUE_KILL_SWITCH)
@SetLongFlag(name = KEY_ASYNC_REGISTRATION_JOB_QUEUE_INTERVAL_MS, value = 0)
@SetMsmtApiAppAllowList
@SetFlagTrue(KEY_GA_UX_FEATURE_ENABLED)
@SetFlagTrue(KEY_FLEDGE_APP_PACKAGE_NAME_LOGGING_ENABLED)
public final class ReportAndRegisterEventE2ETest extends AdServicesExtendedMockitoTestCase {
    private final DevContext mDevContext = DevContext.createForDevOptionsDisabled();

    @Spy
    private final AdServicesHttpsClient mHttpsClientSpy =
            new AdServicesHttpsClient(
                    AdServicesExecutors.getBlockingExecutor(),
                    CacheProviderFactory.createNoOpCache());

    private final AdServicesLogger mAdServicesLoggerMock = mock(AdServicesLoggerImpl.class);
    @Rule public MockWebServerRule mMockWebServerRule = MockWebServerRuleFactory.createForHttps();
    // This object access some system APIs
    @Mock public DevContextFilter mDevContextFilterMock;
    @Mock public AppImportanceFilter mAppImportanceFilterMock;

    @Mock FledgeAuthorizationFilter mFledgeAuthorizationFilterMock;

    @Mock private ConsentManager mConsentManagerMock;
    private CustomAudienceDao mCustomAudienceDao;
    private EncodedPayloadDao mEncodedPayloadDao;
    private AdSelectionEntryDao mAdSelectionEntryDao;
    private AppInstallDao mAppInstallDao;
    private FrequencyCapDao mFrequencyCapDao;
    private EncryptionKeyDao mEncryptionKeyDao;
    private EnrollmentDao mEnrollmentDao;
    private AdFilteringFeatureFactory mAdFilteringFeatureFactory;
    @Mock private AdSelectionServiceFilter mAdSelectionServiceFilterMock;
    @Mock private ObliviousHttpEncryptor mObliviousHttpEncryptor;
    private MultiCloudSupportStrategy mMultiCloudSupportStrategy =
            MultiCloudTestStrategyFactory.getDisabledTestStrategy(mObliviousHttpEncryptor);
    @Mock private AdSelectionDebugReportDao mAdSelectionDebugReportDaoMock;
    @Mock private AdIdFetcher mAdIdFetcher;
    @Mock private KAnonSignJoinFactory mUnusedKAnonSignJoinFactory;

    private static final Instant ACTIVATION_TIME = Instant.now();
    private static final String CALLER_SDK_NAME = "sdk.package.name";
    private static final AdTechIdentifier AD_TECH = AdTechIdentifier.fromString("localhost");
    private static final int BID = 6;
    private static final long AD_SELECTION_ID = 1;
    private static final String SELLER_INTERACTION_REPORTING_PATH = "/seller/interactionReporting/";
    private static final String BUYER_INTERACTION_REPORTING_PATH = "/buyer/interactionReporting/";
    private static final Uri RENDER_URI = Uri.parse("https://test.com/advert/");
    private static final int BUYER_DESTINATION =
            ReportEventRequest.FLAG_REPORTING_DESTINATION_BUYER;
    private static final int SELLER_DESTINATION =
            ReportEventRequest.FLAG_REPORTING_DESTINATION_SELLER;
    private static final String EVENT_KEY = "click";
    private static final InputEvent INPUT_EVENT =
            MotionEvent.obtain(0, 0, ACTION_BUTTON_PRESS, 0, 0, 0);
    private final ListeningExecutorService mLightweightExecutorService =
            AdServicesExecutors.getLightWeightExecutor();
    private final ListeningExecutorService mBackgroundExecutorService =
            AdServicesExecutors.getBackgroundExecutor();

    private final ScheduledThreadPoolExecutor mScheduledExecutor =
            AdServicesExecutors.getScheduler();
    // TODO(b/384949821): move to superclass
    private final Flags mFakeFlags = flags.getFlags();

    private final long mMaxRegisteredAdBeaconsTotalCount =
            mFakeFlags.getFledgeReportImpressionMaxRegisteredAdBeaconsTotalCount();
    private final long mMaxRegisteredAdBeaconsPerDestination =
            mFakeFlags.getFledgeReportImpressionMaxRegisteredAdBeaconsPerAdTechCount();

    private static final boolean CONSOLE_MESSAGE_IN_LOGS_ENABLED = true;

    private DBAdSelection mDBAdSelection;
    private DBRegisteredAdInteraction mDBRegisteredAdInteractionSellerClick;
    private DBRegisteredAdInteraction mDBRegisteredAdInteractionBuyerClick;
    private String mEventData;
    private ReportInteractionInput.Builder mInputBuilder;
    private MockWebServerRule.RequestMatcher<String> mRequestMatcherPrefixMatch;

    private static final String DEFAULT_ENROLLMENT = "enrollment-id";
    private static final EnrollmentData ENROLLMENT_DATA =
            new EnrollmentData.Builder().setEnrollmentId(DEFAULT_ENROLLMENT).build();

    @Mock private AdServicesErrorLogger mErrorLoggerMock;

    @Spy
    private DatastoreManager mDatastoreManagerSpy =
            new SQLDatastoreManager(DbTestUtil.getMeasurementDbHelperForTest(), mErrorLoggerMock);

    @Mock private ContentResolver mContentResolverMock;
    @Mock private ClickVerifier mClickVerifierMock;

    private MeasurementImpl mMeasurementImplSpy;
    @Mock EnrollmentDao mEnrollmentDaoMock;
    @Mock MeasurementDataDeleter mMeasurementDataDeleterMock;
    IOdpDelegationWrapper mIOdpDelegationWrapperMock = mock(NoOdpDelegationWrapper.class);

    AdSelectionServiceImpl mAdSelectionService;
    private AsyncRegistrationQueueRunner mAsyncRegistrationQueueRunnerSpy;
    @Mock private AggregateDebugReportApi mAdrApiMock;
    @Mock private DebugReportApi mDebugReportApiMock;

    @Spy
    private AsyncSourceFetcher mAsyncSourceFetcherSpy =
            new AsyncSourceFetcher(
                    mContext,
                    mEnrollmentDaoMock,
                    mFakeFlags,
                    mDatastoreManagerSpy,
                    mDebugReportApiMock);

    @Spy
    private AsyncTriggerFetcher mAsyncTriggerFetcherSpy =
            new AsyncTriggerFetcher(
                    mContext,
                    mEnrollmentDaoMock,
                    mFakeFlags,
                    mIOdpDelegationWrapperMock,
                    mDatastoreManagerSpy,
                    mDebugReportApiMock);

    @Mock private SourceNoiseHandler mSourceNoiseHandlerMock;
    private RetryStrategyFactory mRetryStrategyFactory;
    private AuctionServerDebugConfigurationGenerator mAuctionServerDebugConfigurationGenerator;

    @Before
    public void setup() throws Exception {
        mRequestMatcherPrefixMatch = (a, b) -> !b.isEmpty() && a.startsWith(b);

        doReturn(mFakeFlags).when(() -> FlagsFactory.getFlags());

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

        mMeasurementImplSpy =
                spy(
                        new MeasurementImpl(
                                mContext,
                                FakeFlagsFactory.getFlagsForTest(),
                                mDatastoreManagerSpy,
                                mClickVerifierMock,
                                mMeasurementDataDeleterMock,
                                mContentResolverMock));
        doReturn(mMeasurementImplSpy).when(() -> MeasurementImpl.getInstance());
        doReturn(true)
                .when(mClickVerifierMock)
                .isInputEventVerifiable(any(), anyLong(), anyString());
        when(mEnrollmentDaoMock.getEnrollmentDataFromMeasurementUrl(any()))
                .thenReturn(ENROLLMENT_DATA);

        when(mDevContextFilterMock.createDevContext()).thenReturn(mDevContext);
        mRetryStrategyFactory = RetryStrategyFactory.createInstanceForTesting();
        ConsentedDebugConfigurationDao consentedDebugConfigurationDao =
                Room.inMemoryDatabaseBuilder(mContext, AdSelectionDatabase.class)
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

        mAdSelectionService = getAdSelectionServiceImpl(mFakeFlags);

        initializeReportingArtifacts();

        doReturn(mDatastoreManagerSpy).when(DatastoreManagerFactory::getDatastoreManager);

        mAsyncRegistrationQueueRunnerSpy =
                spy(
                        new AsyncRegistrationQueueRunner(
                                mContext,
                                mContentResolverMock,
                                mAsyncSourceFetcherSpy,
                                mAsyncTriggerFetcherSpy,
                                mDatastoreManagerSpy,
                                mDebugReportApiMock,
                                mAdrApiMock,
                                mSourceNoiseHandlerMock,
                                mFakeFlags,
                                mAdServicesLoggerMock));
    }

    @After
    public void tearDown() throws DatastoreException {
        // Clean up db to prevent polluting subsequent tests.
        mDatastoreManagerSpy.runInTransaction((dao) -> dao.deleteAllMeasurementData(List.of()));
    }

    @Test
    @SetFlagFalse(KEY_FLEDGE_REGISTER_AD_BEACON_ENABLED)
    public void testService_reportingDisabled() throws Exception {
        enableMeasurementConsentAndARAPermission();
        persistReportingArtifacts();
        // Re-initialize service with the feature disabled altogether.
        mAdSelectionService = getAdSelectionServiceImpl(mFakeFlags);

        // Mock server to report the event in addition to being registered by measurement.
        MockWebServer server =
                mMockWebServerRule.startMockWebServer(
                        List.of(new MockResponse(), new MockResponse()));

        // Call report event with input.
        ReportAndRegisterEventTestCallback callback = callReportEvent(mInputBuilder.build());

        // Process async registration.
        mAsyncRegistrationQueueRunnerSpy.runAsyncRegistrationQueueWorker();

        // Verify registerEvent was never called.
        verify(mMeasurementImplSpy, never())
                .registerEvent(any(), any(), any(), anyBoolean(), any(), any(), any());

        // Confirm failure was reported to caller.
        assertThat(callback.mIsSuccess).isFalse();
        assertThat(callback.mFledgeErrorResponse.getStatusCode()).isEqualTo(STATUS_INTERNAL_ERROR);
        assertThat(callback.mFledgeErrorResponse.getErrorMessage()).isEqualTo(API_DISABLED_MESSAGE);

        // Confirm success was logged.
        verify(mAdServicesLoggerMock)
                .logFledgeApiCallStats(
                        eq(AD_SERVICES_API_CALLED__API_NAME__REPORT_INTERACTION),
                        eq(TEST_PACKAGE_NAME),
                        eq(STATUS_INTERNAL_ERROR),
                        anyInt());

        // Verify the mock server handled requests with exact paths.
        assertThat(server.getRequestCount()).isEqualTo(0);
    }

    @Test
    @SetFlagFalse(KEY_FLEDGE_MEASUREMENT_REPORT_AND_REGISTER_EVENT_API_ENABLED)
    public void testService_onlyReportingEnabled() throws Exception {
        enableMeasurementConsentAndARAPermission();
        persistReportingArtifacts();

        // Re-initialize service with the event registering disabled.
        mAdSelectionService = getAdSelectionServiceImpl(mFakeFlags);

        // Mock server to report the event in addition to being registered by measurement.
        MockWebServer server =
                mMockWebServerRule.startMockWebServer(
                        List.of(new MockResponse(), new MockResponse()));

        // Call report event with input.
        ReportInteractionInput input = mInputBuilder.build();
        ReportAndRegisterEventTestCallback callback = callReportEvent(input);

        // Process async registration.
        mAsyncRegistrationQueueRunnerSpy.runAsyncRegistrationQueueWorker();

        // Verify registerEvent was never called.
        verify(mMeasurementImplSpy, never())
                .registerEvent(any(), any(), any(), anyBoolean(), any(), any(), any());

        // Confirm success was reported to caller.
        assertTrue(callback.mIsSuccess);

        // Confirm success was logged.
        verify(mAdServicesLoggerMock)
                .logFledgeApiCallStats(
                        eq(AD_SERVICES_API_CALLED__API_NAME__REPORT_INTERACTION),
                        eq(TEST_PACKAGE_NAME),
                        eq(STATUS_SUCCESS),
                        anyInt());

        // Verify the mock server handled requests with exact paths.
        mMockWebServerRule.verifyMockServerRequests(
                server,
                2,
                List.of(
                        SELLER_INTERACTION_REPORTING_PATH + EVENT_KEY,
                        BUYER_INTERACTION_REPORTING_PATH + EVENT_KEY),
                mRequestMatcherPrefixMatch);
    }


    @Test
    public void testService_ReportingAndRegisteringEnabled() throws Exception {
        enableMeasurementConsentAndARAPermission();
        persistReportingArtifacts();

        // Mock server to report the event in addition to being registered by measurement.
        MockWebServer server =
                mMockWebServerRule.startMockWebServer(
                        List.of(new MockResponse(), new MockResponse()));

        // Call report event with input.
        ReportInteractionInput input = mInputBuilder.build();
        ReportAndRegisterEventTestCallback callback = callReportEvent(input);

        // Process async registration.
        mAsyncRegistrationQueueRunnerSpy.runAsyncRegistrationQueueWorker();

        // Verify registerEvent was called with exact parameters.
        verifyRegisterEvent(SELLER_INTERACTION_REPORTING_PATH + EVENT_KEY, input);
        verifyRegisterEvent(BUYER_INTERACTION_REPORTING_PATH + EVENT_KEY, input);

        // Confirm success was reported to caller.
        assertTrue(callback.mIsSuccess);

        // Confirm success was logged.
        verify(mAdServicesLoggerMock)
                .logFledgeApiCallStats(
                        eq(AD_SERVICES_API_CALLED__API_NAME__REPORT_INTERACTION),
                        eq(TEST_PACKAGE_NAME),
                        eq(STATUS_SUCCESS),
                        anyInt());

        // Verify the mock server handled requests with exact paths.
        mMockWebServerRule.verifyMockServerRequests(
                server,
                2,
                List.of(
                        SELLER_INTERACTION_REPORTING_PATH + EVENT_KEY,
                        BUYER_INTERACTION_REPORTING_PATH + EVENT_KEY),
                mRequestMatcherPrefixMatch);
    }

    @Test
    @SetFlagTrue(KEY_MEASUREMENT_API_REGISTER_SOURCE_KILL_SWITCH)
    public void testService_ReportingAndRegisteringEnabled_measurementKillSwitchEnabled()
            throws Exception {
        enableMeasurementConsentAndARAPermission();
        persistReportingArtifacts();

        // Re-initialize service with kill switch turned on.
        mAdSelectionService = getAdSelectionServiceImpl(mFakeFlags);

        // Mock server to report the event in addition to being registered by measurement.
        MockWebServer server =
                mMockWebServerRule.startMockWebServer(
                        List.of(new MockResponse(), new MockResponse()));

        // Call report event with input.
        ReportInteractionInput input = mInputBuilder.build();
        ReportAndRegisterEventTestCallback callback = callReportEvent(input);

        // Process async registration.
        mAsyncRegistrationQueueRunnerSpy.runAsyncRegistrationQueueWorker();

        // Verify registerEvent was never called.
        verify(mMeasurementImplSpy, never())
                .registerEvent(any(), any(), any(), anyBoolean(), any(), any(), any());

        // Confirm success was reported to caller.
        assertTrue(callback.mIsSuccess);

        // Confirm success was logged.
        verify(mAdServicesLoggerMock)
                .logFledgeApiCallStats(
                        eq(AD_SERVICES_API_CALLED__API_NAME__REPORT_INTERACTION),
                        eq(TEST_PACKAGE_NAME),
                        eq(STATUS_SUCCESS),
                        anyInt());

        // Verify the mock server handled requests with exact paths.
        mMockWebServerRule.verifyMockServerRequests(
                server,
                2,
                List.of(
                        SELLER_INTERACTION_REPORTING_PATH + EVENT_KEY,
                        BUYER_INTERACTION_REPORTING_PATH + EVENT_KEY),
                mRequestMatcherPrefixMatch);
    }

    @Test
    @SetMsmtApiAppAllowList(AllowLists.ALLOW_NONE)
    public void testService_ReportingAndRegisteringEnabled_appIsNotInMeasurementAllowlisted()
            throws Exception {
        enableMeasurementConsentAndARAPermission();
        persistReportingArtifacts();

        // Re-initialize service with no allow list.
        mAdSelectionService = getAdSelectionServiceImpl(mFakeFlags);

        // Mock server to report the event in addition to being registered by measurement.
        MockWebServer server =
                mMockWebServerRule.startMockWebServer(
                        List.of(new MockResponse(), new MockResponse()));

        // Call report event with input.
        ReportInteractionInput input = mInputBuilder.build();
        ReportAndRegisterEventTestCallback callback = callReportEvent(input);

        // Process async registration.
        mAsyncRegistrationQueueRunnerSpy.runAsyncRegistrationQueueWorker();

        // Verify registerEvent was never called.
        verify(mMeasurementImplSpy, never())
                .registerEvent(any(), any(), any(), anyBoolean(), any(), any(), any());

        // Confirm success was reported to caller.
        assertTrue(callback.mIsSuccess);

        // Confirm success was logged.
        verify(mAdServicesLoggerMock)
                .logFledgeApiCallStats(
                        eq(AD_SERVICES_API_CALLED__API_NAME__REPORT_INTERACTION),
                        eq(TEST_PACKAGE_NAME),
                        eq(STATUS_SUCCESS),
                        anyInt());

        // Verify the mock server handled requests with exact paths.
        mMockWebServerRule.verifyMockServerRequests(
                server,
                2,
                List.of(
                        SELLER_INTERACTION_REPORTING_PATH + EVENT_KEY,
                        BUYER_INTERACTION_REPORTING_PATH + EVENT_KEY),
                mRequestMatcherPrefixMatch);
    }

    @Test
    public void testService_ReportingAndRegisteringEnabled_measurementConsentRevoked()
            throws Exception {
        // Revoke consent for measurement.
        doReturn(AdServicesApiConsent.REVOKED)
                .when(mConsentManagerMock)
                .getConsent(AdServicesApiType.MEASUREMENTS);
        doReturn(true)
                .when(
                        () ->
                                PermissionHelper.hasAttributionPermission(
                                        any(Context.class), anyString()));
        persistReportingArtifacts();

        // Mock server to report the event in addition to being registered by measurement.
        MockWebServer server =
                mMockWebServerRule.startMockWebServer(
                        List.of(new MockResponse(), new MockResponse()));

        // Call report event with input.
        ReportInteractionInput input = mInputBuilder.build();
        ReportAndRegisterEventTestCallback callback = callReportEvent(input);

        // Process async registration.
        mAsyncRegistrationQueueRunnerSpy.runAsyncRegistrationQueueWorker();

        // Verify registerEvent was never called.
        verify(mMeasurementImplSpy, never())
                .registerEvent(any(), any(), any(), anyBoolean(), any(), any(), any());

        // Confirm success was reported to caller.
        assertTrue(callback.mIsSuccess);

        // Confirm success was logged.
        verify(mAdServicesLoggerMock)
                .logFledgeApiCallStats(
                        eq(AD_SERVICES_API_CALLED__API_NAME__REPORT_INTERACTION),
                        eq(TEST_PACKAGE_NAME),
                        eq(STATUS_SUCCESS),
                        anyInt());

        // Verify the mock server handled requests with exact paths.
        mMockWebServerRule.verifyMockServerRequests(
                server,
                2,
                List.of(
                        SELLER_INTERACTION_REPORTING_PATH + EVENT_KEY,
                        BUYER_INTERACTION_REPORTING_PATH + EVENT_KEY),
                mRequestMatcherPrefixMatch);
    }

    @Test
    public void testService_ReportingAndRegisteringEnabled_noPermissionForARA() throws Exception {
        // Disable permission for ARA.
        doReturn(AdServicesApiConsent.GIVEN)
                .when(mConsentManagerMock)
                .getConsent(AdServicesApiType.MEASUREMENTS);
        doReturn(false)
                .when(
                        () ->
                                PermissionHelper.hasAttributionPermission(
                                        any(Context.class), anyString()));
        persistReportingArtifacts();

        // Mock server to report the event in addition to being registered by measurement.
        MockWebServer server =
                mMockWebServerRule.startMockWebServer(
                        List.of(new MockResponse(), new MockResponse()));

        // Call report event with input.
        ReportInteractionInput input = mInputBuilder.build();
        ReportAndRegisterEventTestCallback callback = callReportEvent(input);

        // Process async registration.
        mAsyncRegistrationQueueRunnerSpy.runAsyncRegistrationQueueWorker();

        // Verify registerEvent was never called.
        verify(mMeasurementImplSpy, never())
                .registerEvent(any(), any(), any(), anyBoolean(), any(), any(), any());

        // Confirm success was reported to caller.
        assertTrue(callback.mIsSuccess);

        // Confirm success was logged.
        verify(mAdServicesLoggerMock)
                .logFledgeApiCallStats(
                        eq(AD_SERVICES_API_CALLED__API_NAME__REPORT_INTERACTION),
                        eq(TEST_PACKAGE_NAME),
                        eq(STATUS_SUCCESS),
                        anyInt());

        // Verify the mock server handled requests with exact paths.
        mMockWebServerRule.verifyMockServerRequests(
                server,
                2,
                List.of(
                        SELLER_INTERACTION_REPORTING_PATH + EVENT_KEY,
                        BUYER_INTERACTION_REPORTING_PATH + EVENT_KEY),
                mRequestMatcherPrefixMatch);
    }

    @Test
    @SetFlagTrue(KEY_FLEDGE_MEASUREMENT_REPORT_AND_REGISTER_EVENT_API_FALLBACK_ENABLED)
    public void testService_ReportingAndRegisteringFallbackEnabled() throws Exception {
        enableMeasurementConsentAndARAPermission();
        persistReportingArtifacts();

        mAdSelectionService = getAdSelectionServiceImpl(mFakeFlags);

        // Mock server to report the event in addition to being registered by measurement.
        MockWebServer server =
                mMockWebServerRule.startMockWebServer(
                        List.of(
                                new MockResponse(),
                                new MockResponse(),
                                new MockResponse(),
                                new MockResponse()));

        // Call report event with input.
        ReportInteractionInput input = mInputBuilder.build();
        ReportAndRegisterEventTestCallback callback =
                callReportEvent(input, /* shouldCountLog= */ true);

        // Process async registration.
        mAsyncRegistrationQueueRunnerSpy.runAsyncRegistrationQueueWorker();

        // Verify registerEvent was called with exact parameters.
        verifyRegisterEvent(SELLER_INTERACTION_REPORTING_PATH + EVENT_KEY, input);
        verifyRegisterEvent(BUYER_INTERACTION_REPORTING_PATH + EVENT_KEY, input);

        // Confirm success was reported to caller.
        assertTrue(callback.mIsSuccess);

        // Confirm success was logged.
        verify(mAdServicesLoggerMock)
                .logFledgeApiCallStats(
                        eq(AD_SERVICES_API_CALLED__API_NAME__REPORT_INTERACTION),
                        eq(TEST_PACKAGE_NAME),
                        eq(STATUS_SUCCESS),
                        anyInt());

        // Verify the mock server handled requests with exact paths.
        mMockWebServerRule.verifyMockServerRequests(
                server,
                4,
                List.of(
                        SELLER_INTERACTION_REPORTING_PATH + EVENT_KEY,
                        BUYER_INTERACTION_REPORTING_PATH + EVENT_KEY,
                        SELLER_INTERACTION_REPORTING_PATH + EVENT_KEY,
                        BUYER_INTERACTION_REPORTING_PATH + EVENT_KEY),
                mRequestMatcherPrefixMatch);
    }

    @Test
    @SetFlagTrue(KEY_FLEDGE_MEASUREMENT_REPORT_AND_REGISTER_EVENT_API_FALLBACK_ENABLED)
    @SetFlagTrue(KEY_MEASUREMENT_API_REGISTER_SOURCE_KILL_SWITCH)
    public void testService_ReportingAndRegisteringFallbackEnabled_measurementKillSwitchEnabled()
            throws Exception {
        enableMeasurementConsentAndARAPermission();
        persistReportingArtifacts();

        // Re-initialize service with kill switch turned on.
        mAdSelectionService = getAdSelectionServiceImpl(mFakeFlags);

        // Mock server to report the event in addition to being registered by measurement.
        MockWebServer server =
                mMockWebServerRule.startMockWebServer(
                        List.of(new MockResponse(), new MockResponse()));

        // Call report event with input.
        ReportInteractionInput input = mInputBuilder.build();
        ReportAndRegisterEventTestCallback callback = callReportEvent(input);

        // Process async registration.
        mAsyncRegistrationQueueRunnerSpy.runAsyncRegistrationQueueWorker();

        // Verify registerEvent was never called.
        verify(mMeasurementImplSpy, never())
                .registerEvent(any(), any(), any(), anyBoolean(), any(), any(), any());

        // Confirm success was reported to caller.
        assertTrue(callback.mIsSuccess);

        // Confirm success was logged.
        verify(mAdServicesLoggerMock)
                .logFledgeApiCallStats(
                        eq(AD_SERVICES_API_CALLED__API_NAME__REPORT_INTERACTION),
                        eq(TEST_PACKAGE_NAME),
                        eq(STATUS_SUCCESS),
                        anyInt());

        // Verify the mock server handled requests with exact paths.
        mMockWebServerRule.verifyMockServerRequests(
                server,
                2,
                List.of(
                        SELLER_INTERACTION_REPORTING_PATH + EVENT_KEY,
                        BUYER_INTERACTION_REPORTING_PATH + EVENT_KEY),
                mRequestMatcherPrefixMatch);
    }

    @Test
    @SetFlagTrue(KEY_FLEDGE_MEASUREMENT_REPORT_AND_REGISTER_EVENT_API_FALLBACK_ENABLED)
    @SetMsmtApiAppAllowList(AllowLists.ALLOW_NONE)
    public void
            testService_ReportingAndRegisteringFallbackEnabled_appIsNotInMeasurementAllowlisted()
                    throws Exception {
        enableMeasurementConsentAndARAPermission();
        persistReportingArtifacts();
        // Re-initialize service with the feature disabled altogether.
        mAdSelectionService = getAdSelectionServiceImpl(mFakeFlags);

        // Mock server to report the event in addition to being registered by measurement.
        MockWebServer server =
                mMockWebServerRule.startMockWebServer(
                        List.of(new MockResponse(), new MockResponse()));

        // Call report event with input.
        ReportInteractionInput input = mInputBuilder.build();
        ReportAndRegisterEventTestCallback callback = callReportEvent(input);

        // Process async registration.
        mAsyncRegistrationQueueRunnerSpy.runAsyncRegistrationQueueWorker();

        // Verify registerEvent was never called.
        verify(mMeasurementImplSpy, never())
                .registerEvent(any(), any(), any(), anyBoolean(), any(), any(), any());

        // Confirm success was reported to caller.
        assertTrue(callback.mIsSuccess);

        // Confirm success was logged.
        verify(mAdServicesLoggerMock)
                .logFledgeApiCallStats(
                        eq(AD_SERVICES_API_CALLED__API_NAME__REPORT_INTERACTION),
                        eq(TEST_PACKAGE_NAME),
                        eq(STATUS_SUCCESS),
                        anyInt());

        // Verify the mock server handled requests with exact paths.
        mMockWebServerRule.verifyMockServerRequests(
                server,
                2,
                List.of(
                        SELLER_INTERACTION_REPORTING_PATH + EVENT_KEY,
                        BUYER_INTERACTION_REPORTING_PATH + EVENT_KEY),
                mRequestMatcherPrefixMatch);
    }

    @Test
    @SetFlagTrue(KEY_FLEDGE_MEASUREMENT_REPORT_AND_REGISTER_EVENT_API_FALLBACK_ENABLED)
    public void testService_ReportingAndRegisteringFallbackEnabled_measurementConsentRevoked()
            throws Exception {
        // Revoke consent for measurement.
        doReturn(AdServicesApiConsent.REVOKED)
                .when(mConsentManagerMock)
                .getConsent(AdServicesApiType.MEASUREMENTS);
        doReturn(true)
                .when(
                        () ->
                                PermissionHelper.hasAttributionPermission(
                                        any(Context.class), anyString()));
        persistReportingArtifacts();
        // Re-initialize service with the feature disabled altogether.
        mAdSelectionService = getAdSelectionServiceImpl(mFakeFlags);

        // Mock server to report the event in addition to being registered by measurement.
        MockWebServer server =
                mMockWebServerRule.startMockWebServer(
                        List.of(new MockResponse(), new MockResponse()));

        // Call report event with input.
        ReportInteractionInput input = mInputBuilder.build();
        ReportAndRegisterEventTestCallback callback = callReportEvent(input);

        // Process async registration.
        mAsyncRegistrationQueueRunnerSpy.runAsyncRegistrationQueueWorker();

        // Verify registerEvent was never called.
        verify(mMeasurementImplSpy, never())
                .registerEvent(any(), any(), any(), anyBoolean(), any(), any(), any());

        // Confirm success was reported to caller.
        assertTrue(callback.mIsSuccess);

        // Confirm success was logged.
        verify(mAdServicesLoggerMock)
                .logFledgeApiCallStats(
                        eq(AD_SERVICES_API_CALLED__API_NAME__REPORT_INTERACTION),
                        eq(TEST_PACKAGE_NAME),
                        eq(STATUS_SUCCESS),
                        anyInt());

        // Verify the mock server handled requests with exact paths.
        mMockWebServerRule.verifyMockServerRequests(
                server,
                2,
                List.of(
                        SELLER_INTERACTION_REPORTING_PATH + EVENT_KEY,
                        BUYER_INTERACTION_REPORTING_PATH + EVENT_KEY),
                mRequestMatcherPrefixMatch);
    }

    @Test
    @SetFlagTrue(KEY_FLEDGE_MEASUREMENT_REPORT_AND_REGISTER_EVENT_API_FALLBACK_ENABLED)
    public void testService_ReportingAndRegisteringFallbackEnabled_noPermissionForARA()
            throws Exception {
        // Disable permission for ARA.
        doReturn(AdServicesApiConsent.GIVEN)
                .when(mConsentManagerMock)
                .getConsent(AdServicesApiType.MEASUREMENTS);
        doReturn(false)
                .when(
                        () ->
                                PermissionHelper.hasAttributionPermission(
                                        any(Context.class), anyString()));
        persistReportingArtifacts();

        // Re-initialize service with fallback turned on.
        mAdSelectionService = getAdSelectionServiceImpl(mFakeFlags);

        // Mock server to report the event in addition to being registered by measurement.
        MockWebServer server =
                mMockWebServerRule.startMockWebServer(
                        List.of(new MockResponse(), new MockResponse()));

        // Call report event with input.
        ReportInteractionInput input = mInputBuilder.build();
        ReportAndRegisterEventTestCallback callback =
                callReportEvent(input, /* shouldCountLog= */ true);

        // Process async registration.
        mAsyncRegistrationQueueRunnerSpy.runAsyncRegistrationQueueWorker();

        // Verify registerEvent was never called.
        verify(mMeasurementImplSpy, never())
                .registerEvent(any(), any(), any(), anyBoolean(), any(), any(), any());

        // Confirm success was reported to caller.
        assertTrue(callback.mIsSuccess);

        // Confirm success was logged.
        verify(mAdServicesLoggerMock)
                .logFledgeApiCallStats(
                        eq(AD_SERVICES_API_CALLED__API_NAME__REPORT_INTERACTION),
                        eq(TEST_PACKAGE_NAME),
                        eq(STATUS_SUCCESS),
                        anyInt());

        // Verify the mock server handled requests with exact paths.
        mMockWebServerRule.verifyMockServerRequests(
                server,
                2,
                List.of(
                        SELLER_INTERACTION_REPORTING_PATH + EVENT_KEY,
                        BUYER_INTERACTION_REPORTING_PATH + EVENT_KEY),
                mRequestMatcherPrefixMatch);
    }

    private AdSelectionServiceImpl getAdSelectionServiceImpl(Flags flags) {
        return new AdSelectionServiceImpl(
                mAdSelectionEntryDao,
                mAppInstallDao,
                mCustomAudienceDao,
                mEncodedPayloadDao,
                mFrequencyCapDao,
                mEncryptionKeyDao,
                mEnrollmentDao,
                mHttpsClientSpy,
                mDevContextFilterMock,
                mLightweightExecutorService,
                mBackgroundExecutorService,
                mScheduledExecutor,
                mContext,
                mAdServicesLoggerMock,
                flags,
                mMockDebugFlags,
                CallingAppUidSupplierProcessImpl.create(),
                mFledgeAuthorizationFilterMock,
                mAdSelectionServiceFilterMock,
                mAdFilteringFeatureFactory,
                mConsentManagerMock,
                mMultiCloudSupportStrategy,
                mAdSelectionDebugReportDaoMock,
                mAdIdFetcher,
                mUnusedKAnonSignJoinFactory,
                false,
                mRetryStrategyFactory,
                CONSOLE_MESSAGE_IN_LOGS_ENABLED,
                mAuctionServerDebugConfigurationGenerator);
    }

    private void initializeReportingArtifacts() throws JSONException {
        CustomAudienceSignals customAudienceSignals =
                CustomAudienceSignalsFixture.aCustomAudienceSignalsBuilder()
                        .setBuyer(AD_TECH)
                        .build();

        String biddingLogicPath = "/buyer/bidding";
        mDBAdSelection =
                new DBAdSelection.Builder()
                        .setAdSelectionId(AD_SELECTION_ID)
                        .setCustomAudienceSignals(customAudienceSignals)
                        .setBuyerContextualSignals("{}")
                        .setBiddingLogicUri(mMockWebServerRule.uriForPath(biddingLogicPath))
                        .setWinningAdRenderUri(RENDER_URI)
                        .setWinningAdBid(BID)
                        .setCreationTimestamp(ACTIVATION_TIME)
                        .setCallerPackageName(CommonFixture.TEST_PACKAGE_NAME)
                        .build();

        mDBRegisteredAdInteractionBuyerClick =
                DBRegisteredAdInteraction.builder()
                        .setAdSelectionId(AD_SELECTION_ID)
                        .setInteractionKey(EVENT_KEY)
                        .setDestination(BUYER_DESTINATION)
                        .setInteractionReportingUri(
                                mMockWebServerRule.uriForPath(
                                        BUYER_INTERACTION_REPORTING_PATH + EVENT_KEY))
                        .build();

        mDBRegisteredAdInteractionSellerClick =
                DBRegisteredAdInteraction.builder()
                        .setAdSelectionId(AD_SELECTION_ID)
                        .setInteractionKey(EVENT_KEY)
                        .setDestination(SELLER_DESTINATION)
                        .setInteractionReportingUri(
                                mMockWebServerRule.uriForPath(
                                        SELLER_INTERACTION_REPORTING_PATH + EVENT_KEY))
                        .build();

        mEventData = new JSONObject().put("x", "10").put("y", "12").toString();

        mInputBuilder =
                new ReportInteractionInput.Builder()
                        .setAdSelectionId(AD_SELECTION_ID)
                        .setCallerPackageName(TEST_PACKAGE_NAME)
                        .setInteractionKey(EVENT_KEY)
                        .setInteractionData(mEventData)
                        .setReportingDestinations(BUYER_DESTINATION | SELLER_DESTINATION)
                        .setInputEvent(INPUT_EVENT)
                        .setCallerSdkName(CALLER_SDK_NAME);
    }

    private void enableMeasurementConsentAndARAPermission() {
        doReturn(AdServicesApiConsent.GIVEN)
                .when(mConsentManagerMock)
                .getConsent(AdServicesApiType.MEASUREMENTS);
        doReturn(true)
                .when(
                        () ->
                                PermissionHelper.hasAttributionPermission(
                                        any(Context.class), anyString()));
    }

    private void persistReportingArtifacts() {
        mAdSelectionEntryDao.persistAdSelection(mDBAdSelection);
        mAdSelectionEntryDao.safelyInsertRegisteredAdInteractions(
                AD_SELECTION_ID,
                List.of(mDBRegisteredAdInteractionBuyerClick),
                mMaxRegisteredAdBeaconsTotalCount,
                mMaxRegisteredAdBeaconsPerDestination,
                BUYER_DESTINATION);

        mAdSelectionEntryDao.safelyInsertRegisteredAdInteractions(
                AD_SELECTION_ID,
                List.of(mDBRegisteredAdInteractionSellerClick),
                mMaxRegisteredAdBeaconsTotalCount,
                mMaxRegisteredAdBeaconsPerDestination,
                SELLER_DESTINATION);
    }

    private void verifyRegisterEvent(String path, ReportInteractionInput input) {
        verify(mMeasurementImplSpy)
                .registerEvent(
                        mMockWebServerRule.uriForPath(path),
                        input.getCallerPackageName(),
                        input.getCallerSdkName(),
                        input.getAdId() != null,
                        mEventData,
                        input.getInputEvent(),
                        input.getAdId());
    }

    private ReportAndRegisterEventTestCallback callReportEvent(ReportInteractionInput inputParams)
            throws Exception {
        return callReportEvent(inputParams, true);
    }

    /**
     * @param shouldCountLog if true, adds a latch to the log interaction as well.
     */
    private ReportAndRegisterEventTestCallback callReportEvent(
            ReportInteractionInput inputParams, boolean shouldCountLog) throws Exception {
        // Counted down logging call
        CountDownLatch logLatch = new CountDownLatch(1);
        if (shouldCountLog) {
            Answer<Void> countDownAnswer =
                    unused -> {
                        logLatch.countDown();
                        return null;
                    };
            doAnswer(countDownAnswer)
                    .when(mAdServicesLoggerMock)
                    .logFledgeApiCallStats(anyInt(), anyString(), anyInt(), anyInt());
        }

        // Counted down callback call
        CountDownLatch resultLatch = new CountDownLatch(1);
        ReportAndRegisterEventTestCallback callback =
                new ReportAndRegisterEventTestCallback(resultLatch);
        mAdSelectionService.reportInteraction(inputParams, callback);

        // Wait for the logging call, which happens after the callback is called.
        resultLatch.await();
        if (shouldCountLog) {
            logLatch.await();
        }

        return callback;
    }

    static class ReportAndRegisterEventTestCallback extends ReportInteractionCallback.Stub {
        private final CountDownLatch mCountDownLatch;
        boolean mIsSuccess = false;
        FledgeErrorResponse mFledgeErrorResponse;

        ReportAndRegisterEventTestCallback(CountDownLatch countDownLatch) {
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

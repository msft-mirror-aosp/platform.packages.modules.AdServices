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

package com.android.adservices.service.consent;

import static android.adservices.common.AdServicesCommonManager.MODULE_STATE_DISABLED;
import static android.adservices.common.AdServicesCommonManager.MODULE_STATE_ENABLED;
import static android.adservices.common.AdServicesCommonManager.MODULE_STATE_UNKNOWN;

import static com.android.adservices.service.consent.ConsentConstants.CONSENT_KEY;
import static com.android.adservices.service.consent.ConsentConstants.CONSENT_KEY_FOR_ALL;
import static com.android.adservices.service.consent.ConsentConstants.DEFAULT_CONSENT;
import static com.android.adservices.service.consent.ConsentConstants.GA_UX_NOTIFICATION_DISPLAYED_ONCE;
import static com.android.adservices.service.consent.ConsentConstants.MANUAL_INTERACTION_WITH_CONSENT_RECORDED;
import static com.android.adservices.service.consent.ConsentConstants.NOTIFICATION_DISPLAYED_ONCE;
import static com.android.adservices.service.consent.ConsentConstants.PAS_NOTIFICATION_DISPLAYED_ONCE;
import static com.android.adservices.service.consent.ConsentConstants.PAS_NOTIFICATION_OPENED;
import static com.android.adservices.service.consent.ConsentConstants.SHARED_PREFS_CONSENT;
import static com.android.adservices.service.consent.ConsentConstants.SHARED_PREFS_KEY_APPSEARCH_HAS_MIGRATED;
import static com.android.adservices.service.consent.ConsentConstants.SHARED_PREFS_KEY_HAS_MIGRATED;
import static com.android.adservices.service.consent.ConsentConstants.SHARED_PREFS_KEY_PPAPI_HAS_CLEARED;
import static com.android.adservices.service.consent.ConsentManager.MANUAL_INTERACTIONS_RECORDED;
import static com.android.adservices.service.consent.ConsentManager.UNKNOWN;
import static com.android.adservices.service.consent.ConsentManager.consentSourceOfTruthToString;
import static com.android.adservices.service.consent.ConsentManager.resetSharedPreference;
import static com.android.adservices.service.stats.AdServicesStatsLog.AD_SERVICES_ERROR_REPORTED__ERROR_CODE__APP_SEARCH_DATA_MIGRATION_FAILURE;
import static com.android.adservices.service.stats.AdServicesStatsLog.AD_SERVICES_ERROR_REPORTED__ERROR_CODE__ERROR_WHILE_GET_CONSENT;
import static com.android.adservices.service.stats.AdServicesStatsLog.AD_SERVICES_ERROR_REPORTED__ERROR_CODE__PRIVACY_SANDBOX_SAVE_FAILURE;
import static com.android.adservices.service.stats.AdServicesStatsLog.AD_SERVICES_ERROR_REPORTED__ERROR_CODE__SHARED_PREF_UPDATE_FAILURE;
import static com.android.adservices.service.stats.AdServicesStatsLog.AD_SERVICES_ERROR_REPORTED__PPAPI_NAME__UX;
import static com.android.adservices.shared.testing.common.DumpHelper.dump;
import static com.android.adservices.spe.AdServicesJobInfo.AD_PACKAGE_DENY_PRE_PROCESS_JOB;
import static com.android.adservices.spe.AdServicesJobInfo.COBALT_LOGGING_JOB;
import static com.android.adservices.spe.AdServicesJobInfo.CONSENT_NOTIFICATION_JOB;
import static com.android.adservices.spe.AdServicesJobInfo.ENCRYPTION_KEY_PERIODIC_JOB;
import static com.android.adservices.spe.AdServicesJobInfo.FLEDGE_AD_SELECTION_DEBUG_REPORT_SENDER_JOB;
import static com.android.adservices.spe.AdServicesJobInfo.FLEDGE_BACKGROUND_FETCH_JOB;
import static com.android.adservices.spe.AdServicesJobInfo.FLEDGE_KANON_SIGN_JOIN_BACKGROUND_JOB;
import static com.android.adservices.spe.AdServicesJobInfo.MAINTENANCE_JOB;
import static com.android.adservices.spe.AdServicesJobInfo.MDD_CELLULAR_CHARGING_PERIODIC_TASK_JOB;
import static com.android.adservices.spe.AdServicesJobInfo.MDD_CHARGING_PERIODIC_TASK_JOB;
import static com.android.adservices.spe.AdServicesJobInfo.MDD_MAINTENANCE_PERIODIC_TASK_JOB;
import static com.android.adservices.spe.AdServicesJobInfo.MDD_WIFI_CHARGING_PERIODIC_TASK_JOB;
import static com.android.adservices.spe.AdServicesJobInfo.MEASUREMENT_AGGREGATE_FALLBACK_REPORTING_JOB;
import static com.android.adservices.spe.AdServicesJobInfo.MEASUREMENT_AGGREGATE_MAIN_REPORTING_JOB;
import static com.android.adservices.spe.AdServicesJobInfo.MEASUREMENT_ASYNC_REGISTRATION_FALLBACK_JOB;
import static com.android.adservices.spe.AdServicesJobInfo.MEASUREMENT_ASYNC_REGISTRATION_JOB;
import static com.android.adservices.spe.AdServicesJobInfo.MEASUREMENT_ATTRIBUTION_FALLBACK_JOB;
import static com.android.adservices.spe.AdServicesJobInfo.MEASUREMENT_ATTRIBUTION_JOB;
import static com.android.adservices.spe.AdServicesJobInfo.MEASUREMENT_DEBUG_REPORTING_FALLBACK_JOB;
import static com.android.adservices.spe.AdServicesJobInfo.MEASUREMENT_DELETE_EXPIRED_JOB;
import static com.android.adservices.spe.AdServicesJobInfo.MEASUREMENT_DELETE_UNINSTALLED_JOB;
import static com.android.adservices.spe.AdServicesJobInfo.MEASUREMENT_EVENT_FALLBACK_REPORTING_JOB;
import static com.android.adservices.spe.AdServicesJobInfo.MEASUREMENT_EVENT_MAIN_REPORTING_JOB;
import static com.android.adservices.spe.AdServicesJobInfo.MEASUREMENT_IMMEDIATE_AGGREGATE_REPORTING_JOB;
import static com.android.adservices.spe.AdServicesJobInfo.MEASUREMENT_REPORTING_JOB;
import static com.android.adservices.spe.AdServicesJobInfo.MEASUREMENT_VERBOSE_DEBUG_REPORTING_FALLBACK_JOB;
import static com.android.adservices.spe.AdServicesJobInfo.PERIODIC_SIGNALS_ENCODING_JOB;
import static com.android.adservices.spe.AdServicesJobInfo.TOPICS_EPOCH_JOB;
import static com.android.dx.mockito.inline.extended.ExtendedMockito.any;
import static com.android.dx.mockito.inline.extended.ExtendedMockito.anyBoolean;
import static com.android.dx.mockito.inline.extended.ExtendedMockito.anyInt;
import static com.android.dx.mockito.inline.extended.ExtendedMockito.anyString;
import static com.android.dx.mockito.inline.extended.ExtendedMockito.argThat;
import static com.android.dx.mockito.inline.extended.ExtendedMockito.atLeast;
import static com.android.dx.mockito.inline.extended.ExtendedMockito.atLeastOnce;
import static com.android.dx.mockito.inline.extended.ExtendedMockito.doNothing;
import static com.android.dx.mockito.inline.extended.ExtendedMockito.doReturn;
import static com.android.dx.mockito.inline.extended.ExtendedMockito.doThrow;
import static com.android.dx.mockito.inline.extended.ExtendedMockito.eq;
import static com.android.dx.mockito.inline.extended.ExtendedMockito.mock;
import static com.android.dx.mockito.inline.extended.ExtendedMockito.never;
import static com.android.dx.mockito.inline.extended.ExtendedMockito.spy;
import static com.android.dx.mockito.inline.extended.ExtendedMockito.times;
import static com.android.dx.mockito.inline.extended.ExtendedMockito.verify;
import static com.android.dx.mockito.inline.extended.ExtendedMockito.verifyNoMoreInteractions;
import static com.android.dx.mockito.inline.extended.ExtendedMockito.verifyZeroInteractions;
import static com.android.dx.mockito.inline.extended.ExtendedMockito.when;

import static com.google.common.truth.Truth.assertThat;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertNull;
import static org.junit.Assert.assertThrows;
import static org.junit.Assert.assertTrue;
import static org.mockito.Mockito.clearInvocations;
import static org.mockito.Mockito.doAnswer;

import android.adservices.common.AdServicesModuleUserChoice;
import android.adservices.common.Module;
import android.annotation.Nullable;
import android.app.adservices.AdServicesManager;
import android.app.adservices.IAdServicesManager;
import android.app.adservices.consent.ConsentParcel;
import android.app.job.JobScheduler;
import android.content.Context;
import android.content.SharedPreferences;
import android.content.pm.ApplicationInfo;
import android.content.pm.PackageManager;
import android.os.RemoteException;
import android.os.SystemClock;
import android.util.SparseIntArray;

import androidx.test.core.content.pm.ApplicationInfoBuilder;
import androidx.test.filters.SmallTest;

import com.android.adservices.AdServicesCommon;
import com.android.adservices.cobalt.CobaltJobService;
import com.android.adservices.common.AdServicesExtendedMockitoTestCase;
import com.android.adservices.common.DbTestUtil;
import com.android.adservices.common.logging.annotations.ExpectErrorLogUtilCall;
import com.android.adservices.common.logging.annotations.ExpectErrorLogUtilWithExceptionCall;
import com.android.adservices.common.logging.annotations.ExpectErrorLogUtilWithExceptionCall.Any;
import com.android.adservices.common.logging.annotations.SetErrorLogUtilDefaultParams;
import com.android.adservices.data.adselection.AppInstallDao;
import com.android.adservices.data.adselection.FrequencyCapDao;
import com.android.adservices.data.consent.AppConsentDao;
import com.android.adservices.data.consent.AppConsentDaoFixture;
import com.android.adservices.data.customaudience.CustomAudienceDao;
import com.android.adservices.data.enrollment.EnrollmentDao;
import com.android.adservices.data.signals.EncodedPayloadDao;
import com.android.adservices.data.signals.ProtectedSignalsDao;
import com.android.adservices.data.topics.Topic;
import com.android.adservices.data.topics.TopicsTables;
import com.android.adservices.download.MddJob;
import com.android.adservices.service.Flags;
import com.android.adservices.service.FlagsFactory;
import com.android.adservices.service.MaintenanceJobService;
import com.android.adservices.service.appsearch.AppSearchConsentManager;
import com.android.adservices.service.common.BackgroundJobsManager;
import com.android.adservices.service.common.UserProfileIdManager;
import com.android.adservices.service.common.compat.PackageManagerCompatUtils;
import com.android.adservices.service.common.feature.PrivacySandboxFeatureType;
import com.android.adservices.service.encryptionkey.EncryptionKeyJobService;
import com.android.adservices.service.measurement.DeleteExpiredJobService;
import com.android.adservices.service.measurement.DeleteUninstalledJobService;
import com.android.adservices.service.measurement.MeasurementImpl;
import com.android.adservices.service.measurement.attribution.AttributionFallbackJobService;
import com.android.adservices.service.measurement.attribution.AttributionJobService;
import com.android.adservices.service.measurement.registration.AsyncRegistrationFallbackJob;
import com.android.adservices.service.measurement.registration.AsyncRegistrationQueueJobService;
import com.android.adservices.service.measurement.reporting.AggregateFallbackReportingJobService;
import com.android.adservices.service.measurement.reporting.AggregateReportingJobService;
import com.android.adservices.service.measurement.reporting.DebugReportingFallbackJobService;
import com.android.adservices.service.measurement.reporting.EventFallbackReportingJobService;
import com.android.adservices.service.measurement.reporting.EventReportingJobService;
import com.android.adservices.service.measurement.reporting.ImmediateAggregateReportingJobService;
import com.android.adservices.service.measurement.reporting.ReportingJobService;
import com.android.adservices.service.measurement.reporting.VerboseDebugReportingFallbackJobService;
import com.android.adservices.service.stats.AdServicesLoggerImpl;
import com.android.adservices.service.stats.ConsentMigrationStats;
import com.android.adservices.service.stats.StatsdAdServicesLogger;
import com.android.adservices.service.stats.UiStatsLogger;
import com.android.adservices.service.topics.AppUpdateManager;
import com.android.adservices.service.topics.BlockedTopicsManager;
import com.android.adservices.service.topics.CacheManager;
import com.android.adservices.service.topics.EpochJob;
import com.android.adservices.service.topics.EpochManager;
import com.android.adservices.service.topics.TopicsWorker;
import com.android.adservices.service.ui.data.UxStatesDao;
import com.android.adservices.service.ui.enrollment.collection.PrivacySandboxEnrollmentChannelCollection;
import com.android.adservices.service.ui.util.EnrollmentData;
import com.android.adservices.service.ui.ux.collection.PrivacySandboxUxCollection;
import com.android.adservices.shared.errorlogging.AdServicesErrorLogger;
import com.android.adservices.shared.storage.AtomicFileDatastore;
import com.android.dx.mockito.inline.extended.ExtendedMockito;
import com.android.modules.utils.build.SdkLevel;
import com.android.modules.utils.testing.ExtendedMockitoRule.MockStatic;
import com.android.modules.utils.testing.ExtendedMockitoRule.SpyStatic;

import com.google.common.base.Supplier;
import com.google.common.collect.ImmutableList;

import org.junit.After;
import org.junit.Before;
import org.junit.Test;
import org.mockito.ArgumentMatcher;
import org.mockito.Mock;
import org.mockito.Mockito;
import org.mockito.verification.VerificationMode;

import java.io.File;
import java.io.IOException;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collections;
import java.util.List;
import java.util.Objects;
import java.util.stream.Collectors;

@SpyStatic(AdServicesLoggerImpl.class)
@SpyStatic(AggregateFallbackReportingJobService.class)
@SpyStatic(AggregateReportingJobService.class)
@SpyStatic(ImmediateAggregateReportingJobService.class)
@SpyStatic(ReportingJobService.class)
@SpyStatic(AsyncRegistrationQueueJobService.class)
@SpyStatic(AsyncRegistrationFallbackJob.class)
@SpyStatic(AttributionJobService.class)
@SpyStatic(AttributionFallbackJobService.class)
@SpyStatic(BackgroundJobsManager.class)
@SpyStatic(ConsentManager.class)
@SpyStatic(DeleteExpiredJobService.class)
@SpyStatic(DeleteUninstalledJobService.class)
@SpyStatic(DeviceRegionProvider.class)
@SpyStatic(EpochJob.class)
@SpyStatic(EventFallbackReportingJobService.class)
@SpyStatic(EventReportingJobService.class)
@SpyStatic(DebugReportingFallbackJobService.class)
@SpyStatic(VerboseDebugReportingFallbackJobService.class)
@SpyStatic(FlagsFactory.class)
@SpyStatic(MaintenanceJobService.class)
@SpyStatic(MddJob.class)
@SpyStatic(EncryptionKeyJobService.class)
@SpyStatic(CobaltJobService.class)
@SpyStatic(UiStatsLogger.class)
@SpyStatic(StatsdAdServicesLogger.class)
@MockStatic(PackageManagerCompatUtils.class)
@MockStatic(SdkLevel.class)
@SetErrorLogUtilDefaultParams(
        throwable = Any.class,
        ppapiName = AD_SERVICES_ERROR_REPORTED__PPAPI_NAME__UX)
@SmallTest
public final class ConsentManagerTest extends AdServicesExtendedMockitoTestCase {
    private static final int UX_TYPE_COUNT = PrivacySandboxUxCollection.values().length;
    private static final int ENROLLMENT_CHANNEL_COUNT = 17;

    private AtomicFileDatastore mDatastore;
    private AtomicFileDatastore mConsentDatastore;
    private ConsentManager mConsentManager;
    private AppConsentDao mAppConsentDaoSpy;
    private EnrollmentDao mEnrollmentDaoSpy;
    private AdServicesManager mAdServicesManager;

    @Mock private TopicsWorker mTopicsWorkerMock;
    @Mock private MeasurementImpl mMeasurementImplMock;
    @Mock private AdServicesLoggerImpl mAdServicesLoggerImplMock;
    @Mock private CustomAudienceDao mCustomAudienceDaoMock;
    @Mock private AppInstallDao mAppInstallDaoMock;
    @Mock private ProtectedSignalsDao mProtectedSignalsDaoMock;
    @Mock private FrequencyCapDao mFrequencyCapDaoMock;
    @Mock private EncodedPayloadDao mEncodedPayloadDaoMock;
    @Mock private UiStatsLogger mUiStatsLoggerMock;
    @Mock private AppUpdateManager mAppUpdateManagerMock;
    @Mock private CacheManager mCacheManagerMock;
    @Mock private BlockedTopicsManager mBlockedTopicsManagerMock;
    @Mock private EpochManager mMockEpochManager;
    @Mock private JobScheduler mJobSchedulerMock;
    @Mock private IAdServicesManager mMockIAdServicesManager;
    @Mock private AppSearchConsentManager mAppSearchConsentManagerMock;
    @Mock private UserProfileIdManager mUserProfileIdManagerMock;
    @Mock private UxStatesDao mUxStatesDaoMock;
    @Mock private StatsdAdServicesLogger mStatsdAdServicesLoggerMock;
    @Mock private AdServicesErrorLogger mMockAdServicesErrorLogger;
    @Mock private Supplier<TopicsWorker> mTopicsWorksSupplierMock;
    @Mock private Supplier<AppConsentDao> mAppConsentDaoSupplierMock;
    @Mock private Supplier<EnrollmentDao> mEnrollmentDaoSupplierMock;
    @Mock private Supplier<MeasurementImpl> mMeasurementImplSupplierMock;

    @Before
    public void setup() throws Exception {
        mocker.mockGetFlags(mMockFlags);
        mockEnableAtomicFileDatastoreBatchUpdateApi(/* enable= */ false);

        mDatastore =
                new AtomicFileDatastore(
                        new File(mSpyContext.getDataDir(), AppConsentDao.DATASTORE_NAME),
                        AppConsentDao.DATASTORE_VERSION,
                        /* versionKey= */ "DaKey",
                        mMockAdServicesErrorLogger);
        // For each file, we should ensure there is only one instance of datastore that is able to
        // access it. (Refer to AtomicFileDatastore.class)
        mConsentDatastore =
                ConsentManager.createAndInitializeDataStore(
                        mSpyContext, mMockAdServicesErrorLogger);
        mAppConsentDaoSpy = spy(new AppConsentDao(mDatastore, mSpyContext.getPackageManager()));
        mEnrollmentDaoSpy =
                spy(
                        new EnrollmentDao(
                                mSpyContext, DbTestUtil.getSharedDbHelperForTest(), mMockFlags));
        mAdServicesManager = new AdServicesManager(mMockIAdServicesManager);
        doReturn(mAdServicesManager).when(mSpyContext).getSystemService(AdServicesManager.class);

        // Default to use PPAPI consent to test migration-irrelevant logics.
        mConsentManager = getConsentManagerByConsentSourceOfTruth(Flags.PPAPI_ONLY);

        doReturn(true).when(mMockFlags).getFledgeFrequencyCapFilteringEnabled();
        doReturn(true).when(mMockFlags).getFledgeAppInstallFilteringEnabled();
        doReturn(true).when(mMockFlags).getProtectedSignalsCleanupEnabled();
        doReturn(true).when(mMockFlags).getEnrollmentEnableLimitedLogging();
        doReturn(true).when(mMockFlags).getFledgeScheduleCustomAudienceUpdateEnabled();

        doReturn(mAdServicesLoggerImplMock).when(AdServicesLoggerImpl::getInstance);
        doNothing().when(EpochJob::schedule);
        doReturn(true)
                .when(() -> MaintenanceJobService.scheduleIfNeeded(any(Context.class), eq(false)));
        doNothing().when(MddJob::scheduleAllMddJobs);
        doReturn(true)
                .when(
                        () ->
                                EncryptionKeyJobService.scheduleIfNeeded(
                                        any(Context.class), eq(false)));
        doNothing().when(() -> AggregateReportingJobService.scheduleIfNeeded(any(), anyBoolean()));
        doNothing()
                .when(
                        () ->
                                AggregateFallbackReportingJobService.scheduleIfNeeded(
                                        any(), anyBoolean()));
        doNothing()
                .when(
                        () ->
                                ImmediateAggregateReportingJobService.scheduleIfNeeded(
                                        any(), anyBoolean()));
        doNothing().when(() -> ReportingJobService.scheduleIfNeeded(any(), anyBoolean()));
        doNothing().when(() -> AttributionFallbackJobService.scheduleIfNeeded(any(), anyBoolean()));
        doNothing().when(AsyncRegistrationFallbackJob::schedule);
        doNothing()
                .when(
                        () ->
                                VerboseDebugReportingFallbackJobService.scheduleIfNeeded(
                                        any(), anyBoolean()));
        doNothing()
                .when(() -> DebugReportingFallbackJobService.scheduleIfNeeded(any(), anyBoolean()));
        doNothing().when(() -> AttributionJobService.scheduleIfNeeded(any(), anyBoolean()));
        doNothing().when(EpochJob::schedule);
        doNothing().when(MddJob::scheduleAllMddJobs);
        doNothing().when(() -> EventReportingJobService.scheduleIfNeeded(any(), anyBoolean()));
        doNothing()
                .when(() -> EventFallbackReportingJobService.scheduleIfNeeded(any(), anyBoolean()));
        doNothing().when(() -> DeleteExpiredJobService.scheduleIfNeeded(any(), anyBoolean()));
        doNothing().when(() -> DeleteUninstalledJobService.scheduleIfNeeded(any(), anyBoolean()));
        doReturn(true).when(() -> MaintenanceJobService.scheduleIfNeeded(any(), anyBoolean()));
        doNothing()
                .when(() -> AsyncRegistrationQueueJobService.scheduleIfNeeded(any(), anyBoolean()));
        doReturn(true).when(() -> CobaltJobService.scheduleIfNeeded(any(), anyBoolean()));
        doNothing().when(() -> UiStatsLogger.logOptInSelected());
        doNothing().when(() -> UiStatsLogger.logOptOutSelected());
        doNothing().when(() -> UiStatsLogger.logOptInSelected(any()));
        doNothing().when(() -> UiStatsLogger.logOptOutSelected(any()));
        // The consent_source_of_truth=APPSEARCH_ONLY value is overridden on T+, so ignore level.
        doReturn(false).when(() -> SdkLevel.isAtLeastT());
        doReturn(mTopicsWorkerMock).when(mTopicsWorksSupplierMock).get();
        doReturn(mAppConsentDaoSpy).when(mAppConsentDaoSupplierMock).get();
        doReturn(mEnrollmentDaoSpy).when(mEnrollmentDaoSupplierMock).get();
        doReturn(mMeasurementImplMock).when(mMeasurementImplSupplierMock).get();
    }

    @After
    public void teardown() throws Exception {
        mDatastore.clear();
        mConsentDatastore.clear();
    }

    @Test
    public void testConsentIsGivenAfterEnabling_PpApiOnly() throws Exception {
        boolean isGiven = true;
        int consentSourceOfTruth = Flags.PPAPI_ONLY;
        ConsentManager spyConsentManager =
                getSpiedConsentManagerForMigrationTesting(isGiven, consentSourceOfTruth);

        spyConsentManager.enable(mSpyContext);

        assertThat(spyConsentManager.getConsent().isGiven()).isTrue();

        verifyConsentMigration(
                spyConsentManager,
                /* isGiven */ isGiven,
                /* hasWrittenToPpApi */ true,
                /* hasWrittenToSystemServer */ false,
                /* hasReadFromSystemServer */ false);
        verifyDataCleanup(spyConsentManager);
    }

    @Test
    public void testConsentIsGivenAfterEnabling_SystemServerOnly() throws Exception {
        boolean isGiven = true;
        int consentSourceOfTruth = Flags.SYSTEM_SERVER_ONLY;
        ConsentManager spyConsentManager =
                getSpiedConsentManagerForMigrationTesting(isGiven, consentSourceOfTruth);

        spyConsentManager.enable(mSpyContext);

        assertThat(spyConsentManager.getConsent().isGiven()).isTrue();

        verifyConsentMigration(
                spyConsentManager,
                /* isGiven */ isGiven,
                /* hasWrittenToPpApi */ false,
                /* hasWrittenToSystemServer */ true,
                /* hasReadFromSystemServer */ true);
        verifyDataCleanup(spyConsentManager);
    }

    @Test
    public void testConsentIsGivenAfterEnabling_PPAPIAndSystemServer() throws Exception {
        boolean isGiven = true;
        int consentSourceOfTruth = Flags.PPAPI_AND_SYSTEM_SERVER;
        ConsentManager spyConsentManager =
                getSpiedConsentManagerForMigrationTesting(isGiven, consentSourceOfTruth);

        spyConsentManager.enable(mSpyContext);

        assertThat(spyConsentManager.getConsent().isGiven()).isTrue();

        verifyConsentMigration(
                spyConsentManager,
                /* isGiven */ isGiven,
                /* hasWrittenToPpApi */ true,
                /* hasWrittenToSystemServer */ true,
                /* hasReadFromSystemServer */ true);
        verifyDataCleanup(spyConsentManager);
    }

    @Test
    public void testConsentIsGivenAfterEnabling_AppSearchOnly() throws Exception {
        boolean isGiven = true;
        int consentSourceOfTruth = Flags.APPSEARCH_ONLY;
        doReturn(true).when(mMockFlags).getEnableAppsearchConsentData();
        ConsentManager spyConsentManager =
                getSpiedConsentManagerForMigrationTesting(isGiven, consentSourceOfTruth);

        spyConsentManager.enable(mSpyContext);

        assertThat(spyConsentManager.getConsent().isGiven()).isTrue();

        verifyConsentMigration(
                spyConsentManager,
                /* isGiven */ isGiven,
                /* hasWrittenToPpApi */ false,
                /* hasWrittenToSystemServer */ false,
                /* hasReadFromSystemServer */ false);
        verify(mAppSearchConsentManagerMock, atLeastOnce()).getConsent(CONSENT_KEY_FOR_ALL);
        verifyDataCleanup(spyConsentManager);
    }

    @Test
    public void testConsentManager_LazyEnable() throws Exception {

        boolean isGiven = true;
        int consentSourceOfTruth = Flags.PPAPI_AND_SYSTEM_SERVER;
        ConsentManager spyConsentManager =
                getSpiedConsentManagerForMigrationTesting(isGiven, consentSourceOfTruth);
        doReturn(ConsentParcel.createGivenConsent(ConsentParcel.ALL_API))
                .when(mMockIAdServicesManager)
                .getConsent(ConsentParcel.ALL_API);

        doReturn(true).when(mMockFlags).getConsentManagerLazyEnableMode();
        spyConsentManager.enable(mSpyContext);
        spyConsentManager.enable(mSpyContext);
        assertThat(spyConsentManager.getConsent().isGiven()).isTrue();
        verify(spyConsentManager, times(0)).setConsentToPpApi(isGiven);
        verifyResetApiCalled(spyConsentManager, 0);
    }

    @Test
    public void testConsentManager_LazyDisabled() throws Exception {
        boolean isGiven = true;
        int consentSourceOfTruth = Flags.PPAPI_AND_SYSTEM_SERVER;
        ConsentManager spyConsentManager =
                getSpiedConsentManagerForMigrationTesting(isGiven, consentSourceOfTruth);
        doReturn(ConsentParcel.createGivenConsent(ConsentParcel.ALL_API))
                .when(mMockIAdServicesManager)
                .getConsent(ConsentParcel.ALL_API);
        doReturn(false).when(mMockFlags).getConsentManagerLazyEnableMode();
        spyConsentManager.enable(mSpyContext);

        assertThat(spyConsentManager.getConsent().isGiven()).isTrue();
        verify(spyConsentManager).setConsentToPpApi(isGiven);
        verifyResetApiCalled(spyConsentManager, 1);
    }

    @Test
    public void testConsentManagerPreApi_LazyEnable() throws Exception {
        boolean isGiven = true;
        int consentSourceOfTruth = Flags.SYSTEM_SERVER_ONLY;
        ConsentManager spyConsentManager =
                getSpiedConsentManagerForMigrationTesting(isGiven, consentSourceOfTruth);
        doReturn(ConsentParcel.createGivenConsent(ConsentParcel.MEASUREMENT))
                .when(mMockIAdServicesManager)
                .getConsent(ConsentParcel.MEASUREMENT);

        doReturn(true).when(mMockFlags).getConsentManagerLazyEnableMode();
        doReturn(true).when(mMockFlags).getGaUxFeatureEnabled();
        spyConsentManager.enable(mSpyContext, AdServicesApiType.MEASUREMENTS);
        assertThat(spyConsentManager.getConsent(AdServicesApiType.MEASUREMENTS).isGiven()).isTrue();
        verify(
                () ->
                        ConsentManager.setPerApiConsentToSystemServer(
                                any(),
                                eq(AdServicesApiType.MEASUREMENTS.toConsentApiType()),
                                eq(isGiven)),
                never());
        verify(spyConsentManager, never()).resetByApi(eq(AdServicesApiType.MEASUREMENTS));
    }

    @Test
    public void testConsentManagerPreApi_LazyDisabled() throws Exception {
        boolean isGiven = true;
        int consentSourceOfTruth = Flags.SYSTEM_SERVER_ONLY;
        ConsentManager spyConsentManager =
                getSpiedConsentManagerForMigrationTesting(isGiven, consentSourceOfTruth);
        doReturn(ConsentParcel.createGivenConsent(ConsentParcel.MEASUREMENT))
                .when(mMockIAdServicesManager)
                .getConsent(ConsentParcel.MEASUREMENT);
        doReturn(false).when(mMockFlags).getConsentManagerLazyEnableMode();
        doReturn(true).when(mMockFlags).getGaUxFeatureEnabled();
        spyConsentManager.enable(mSpyContext, AdServicesApiType.MEASUREMENTS);
        assertThat(spyConsentManager.getConsent(AdServicesApiType.MEASUREMENTS).isGiven()).isTrue();

        verify(
                () ->
                        ConsentManager.setPerApiConsentToSystemServer(
                                any(),
                                eq(AdServicesApiType.MEASUREMENTS.toConsentApiType()),
                                eq(isGiven)));
        verify(spyConsentManager).resetByApi(eq(AdServicesApiType.MEASUREMENTS));
    }

    private static void verifyResetApiCalled(
            ConsentManager spyConsentManager, int wantedNumOfInvocations) throws Exception {
        verify(spyConsentManager, times(wantedNumOfInvocations)).resetTopicsAndBlockedTopics();
        verify(spyConsentManager, times(wantedNumOfInvocations)).resetAppsAndBlockedApps();
        verify(spyConsentManager, times(wantedNumOfInvocations)).resetMeasurement();
    }

    @Test
    public void testConsentIsGivenAfterEnabling_notSupportedFlag() throws Exception {
        boolean isGiven = true;
        int invalidConsentSourceOfTruth = 4;
        ConsentManager spyConsentManager =
                getSpiedConsentManagerForMigrationTesting(isGiven, invalidConsentSourceOfTruth);

        assertThrows(RuntimeException.class, () -> spyConsentManager.enable(mSpyContext));
    }

    @Test
    public void testConsentIsRevokedAfterDisabling_PpApiOnly() throws Exception {
        boolean isGiven = false;
        int consentSourceOfTruth = Flags.PPAPI_ONLY;
        ConsentManager spyConsentManager =
                getSpiedConsentManagerForMigrationTesting(isGiven, consentSourceOfTruth);

        spyConsentManager.disable(mSpyContext);

        assertThat(spyConsentManager.getConsent().isGiven()).isFalse();

        verifyConsentMigration(
                spyConsentManager,
                /* isGiven */ isGiven,
                /* hasWrittenToPpApi */ true,
                /* hasWrittenToSystemServer */ false,
                /* hasReadFromSystemServer */ false);
        verifyDataCleanup(spyConsentManager);
    }

    @Test
    public void testConsentIsRevokedAfterDisabling_SystemServerOnly() throws Exception {
        boolean isGiven = false;
        int consentSourceOfTruth = Flags.SYSTEM_SERVER_ONLY;
        ConsentManager spyConsentManager =
                getSpiedConsentManagerForMigrationTesting(isGiven, consentSourceOfTruth);

        spyConsentManager.disable(mSpyContext);

        assertThat(spyConsentManager.getConsent().isGiven()).isFalse();

        verifyConsentMigration(
                spyConsentManager,
                /* isGiven */ isGiven,
                /* hasWrittenToPpApi */ false,
                /* hasWrittenToSystemServer */ true,
                /* hasReadFromSystemServer */ true);
        verifyDataCleanup(spyConsentManager);
    }

    @Test
    public void testConsentIsRevokedAfterDisabling_PpApiAndSystemServer() throws Exception {
        boolean isGiven = false;
        int consentSourceOfTruth = Flags.PPAPI_AND_SYSTEM_SERVER;
        ConsentManager spyConsentManager =
                getSpiedConsentManagerForMigrationTesting(isGiven, consentSourceOfTruth);

        spyConsentManager.disable(mSpyContext);

        assertThat(spyConsentManager.getConsent().isGiven()).isFalse();

        verifyConsentMigration(
                spyConsentManager,
                /* isGiven */ isGiven,
                /* hasWrittenToPpApi */ true,
                /* hasWrittenToSystemServer */ true,
                /* hasReadFromSystemServer */ true);
        verifyDataCleanup(spyConsentManager);
    }

    @Test
    public void testConsentIsRevokedAfterDisabling_AppSearchOnly() throws Exception {
        boolean isGiven = false;
        int consentSourceOfTruth = Flags.APPSEARCH_ONLY;
        doReturn(true).when(mMockFlags).getEnableAppsearchConsentData();
        ConsentManager spyConsentManager =
                getSpiedConsentManagerForMigrationTesting(isGiven, consentSourceOfTruth);

        spyConsentManager.disable(mSpyContext);

        assertThat(spyConsentManager.getConsent().isGiven()).isFalse();

        verifyConsentMigration(
                spyConsentManager,
                /* isGiven */ isGiven,
                /* hasWrittenToPpApi */ false,
                /* hasWrittenToSystemServer */ false,
                /* hasReadFromSystemServer */ false);
        verify(mAppSearchConsentManagerMock, atLeastOnce()).getConsent(CONSENT_KEY_FOR_ALL);
        verifyDataCleanup(spyConsentManager);
    }

    @Test
    public void testConsentIsRevokedAfterDisabling_notSupportedFlag() throws Exception {
        boolean isGiven = true;
        int invalidConsentSourceOfTruth = 4;
        ConsentManager spyConsentManager =
                getSpiedConsentManagerForMigrationTesting(isGiven, invalidConsentSourceOfTruth);

        assertThrows(RuntimeException.class, () -> spyConsentManager.disable(mSpyContext));
    }

    @Test
    public void testJobsAreScheduledAfterEnablingKillSwitchOff() {
        doReturn(false).when(mMockFlags).getTopicsKillSwitch();
        doReturn(false).when(mMockFlags).getFledgeSelectAdsKillSwitch();
        mockMeasurementEnabled(true);
        doReturn(false).when(mMockFlags).getMddBackgroundTaskKillSwitch();
        doReturn(true).when(mMockFlags).getCobaltLoggingEnabled();

        mConsentManager.enable(mSpyContext);

        verify(() -> BackgroundJobsManager.scheduleAllBackgroundJobs(any(Context.class)));
        verify(EpochJob::schedule);
        verify(MddJob::scheduleAllMddJobs, times(3));
        verify(
                () -> EncryptionKeyJobService.scheduleIfNeeded(any(Context.class), eq(false)),
                times(2));
        verify(
                () -> MaintenanceJobService.scheduleIfNeeded(any(Context.class), eq(false)),
                times(2));
        verify(() -> AggregateReportingJobService.scheduleIfNeeded(any(Context.class), eq(false)));
        verify(
                () ->
                        AggregateFallbackReportingJobService.scheduleIfNeeded(
                                any(Context.class), eq(false)));
        verify(
                () ->
                        ImmediateAggregateReportingJobService.scheduleIfNeeded(
                                any(Context.class), eq(false)));
        verify(() -> ReportingJobService.scheduleIfNeeded(any(Context.class), eq(false)));
        verify(() -> AttributionFallbackJobService.scheduleIfNeeded(any(Context.class), eq(false)));
        verify(AsyncRegistrationFallbackJob::schedule);
        verify(
                () ->
                        VerboseDebugReportingFallbackJobService.scheduleIfNeeded(
                                any(Context.class), eq(false)));
        verify(
                () ->
                        DebugReportingFallbackJobService.scheduleIfNeeded(
                                any(Context.class), eq(false)));
        verify(() -> AttributionJobService.scheduleIfNeeded(any(Context.class), eq(false)));
        verify(() -> EventReportingJobService.scheduleIfNeeded(any(Context.class), eq(false)));
        verify(
                () ->
                        EventFallbackReportingJobService.scheduleIfNeeded(
                                any(Context.class), eq(false)));
        verify(() -> DeleteExpiredJobService.scheduleIfNeeded(any(Context.class), eq(false)));
        verify(() -> DeleteUninstalledJobService.scheduleIfNeeded(any(Context.class), eq(false)));
        verify(
                () ->
                        AsyncRegistrationQueueJobService.scheduleIfNeeded(
                                any(Context.class), eq(false)));
        verify(() -> CobaltJobService.scheduleIfNeeded(any(Context.class), eq(false)), times(2));
    }

    @Test
    public void testJobsAreNotScheduledAfterEnablingKillSwitchOn() {
        doReturn(true).when(mMockFlags).getTopicsKillSwitch();
        doReturn(true).when(mMockFlags).getFledgeSelectAdsKillSwitch();
        mockMeasurementEnabled(false);
        doReturn(true).when(mMockFlags).getMddBackgroundTaskKillSwitch();
        doReturn(false).when(mMockFlags).getCobaltLoggingEnabled();

        mConsentManager.enable(mSpyContext);

        verify(() -> BackgroundJobsManager.scheduleAllBackgroundJobs(any(Context.class)));
        verify(EpochJob::schedule, never());
        verify(
                () -> MaintenanceJobService.scheduleIfNeeded(any(Context.class), eq(false)),
                never());
        verify(MddJob::scheduleAllMddJobs, never());
        verify(
                () -> EncryptionKeyJobService.scheduleIfNeeded(any(Context.class), eq(false)),
                never());
        verify(
                () -> AggregateReportingJobService.scheduleIfNeeded(any(Context.class), eq(false)),
                never());
        verify(
                () ->
                        AggregateFallbackReportingJobService.scheduleIfNeeded(
                                any(Context.class), eq(false)),
                never());
        verify(
                () ->
                        ImmediateAggregateReportingJobService.scheduleIfNeeded(
                                any(Context.class), eq(false)),
                never());
        verify(() -> ReportingJobService.scheduleIfNeeded(any(Context.class), eq(false)), never());
        verify(
                () -> AttributionFallbackJobService.scheduleIfNeeded(any(Context.class), eq(false)),
                never());
        verify(AsyncRegistrationFallbackJob::schedule, never());
        verify(
                () ->
                        VerboseDebugReportingFallbackJobService.scheduleIfNeeded(
                                any(Context.class), eq(false)),
                never());
        verify(
                () ->
                        DebugReportingFallbackJobService.scheduleIfNeeded(
                                any(Context.class), eq(false)),
                never());
        verify(
                () -> AttributionJobService.scheduleIfNeeded(any(Context.class), eq(false)),
                never());
        verify(
                () -> EventReportingJobService.scheduleIfNeeded(any(Context.class), eq(false)),
                never());
        verify(
                () ->
                        EventFallbackReportingJobService.scheduleIfNeeded(
                                any(Context.class), eq(false)),
                never());
        verify(
                () -> DeleteExpiredJobService.scheduleIfNeeded(any(Context.class), eq(false)),
                never());
        verify(
                () -> DeleteUninstalledJobService.scheduleIfNeeded(any(Context.class), eq(false)),
                never());
        verify(
                () ->
                        AsyncRegistrationQueueJobService.scheduleIfNeeded(
                                any(Context.class), eq(false)),
                never());
        verify(() -> CobaltJobService.scheduleIfNeeded(any(Context.class), eq(false)), never());
    }

    @Test
    public void testJobsAreUnscheduledAfterDisabling() {
        doReturn(mJobSchedulerMock).when(mSpyContext).getSystemService(JobScheduler.class);
        mConsentManager.disable(mSpyContext);

        verify(() -> UiStatsLogger.logOptOutSelected());

        verify(mJobSchedulerMock).cancel(MAINTENANCE_JOB.getJobId());
        verify(mJobSchedulerMock).cancel(TOPICS_EPOCH_JOB.getJobId());
        verify(mJobSchedulerMock).cancel(MEASUREMENT_EVENT_MAIN_REPORTING_JOB.getJobId());
        verify(mJobSchedulerMock).cancel(MEASUREMENT_DELETE_EXPIRED_JOB.getJobId());
        verify(mJobSchedulerMock).cancel(MEASUREMENT_DELETE_UNINSTALLED_JOB.getJobId());
        verify(mJobSchedulerMock).cancel(MEASUREMENT_ATTRIBUTION_JOB.getJobId());
        verify(mJobSchedulerMock).cancel(MEASUREMENT_EVENT_FALLBACK_REPORTING_JOB.getJobId());
        verify(mJobSchedulerMock).cancel(MEASUREMENT_AGGREGATE_MAIN_REPORTING_JOB.getJobId());
        verify(mJobSchedulerMock).cancel(MEASUREMENT_AGGREGATE_FALLBACK_REPORTING_JOB.getJobId());
        verify(mJobSchedulerMock).cancel(MEASUREMENT_IMMEDIATE_AGGREGATE_REPORTING_JOB.getJobId());
        verify(mJobSchedulerMock).cancel(MEASUREMENT_REPORTING_JOB.getJobId());
        verify(mJobSchedulerMock).cancel(MEASUREMENT_ASYNC_REGISTRATION_JOB.getJobId());
        verify(mJobSchedulerMock).cancel(MEASUREMENT_ATTRIBUTION_FALLBACK_JOB.getJobId());
        verify(mJobSchedulerMock).cancel(MEASUREMENT_ASYNC_REGISTRATION_FALLBACK_JOB.getJobId());
        verify(mJobSchedulerMock)
                .cancel(MEASUREMENT_VERBOSE_DEBUG_REPORTING_FALLBACK_JOB.getJobId());
        verify(mJobSchedulerMock).cancel(MEASUREMENT_DEBUG_REPORTING_FALLBACK_JOB.getJobId());
        verify(mJobSchedulerMock).cancel(FLEDGE_BACKGROUND_FETCH_JOB.getJobId());
        verify(mJobSchedulerMock).cancel(FLEDGE_AD_SELECTION_DEBUG_REPORT_SENDER_JOB.getJobId());
        verify(mJobSchedulerMock).cancel(PERIODIC_SIGNALS_ENCODING_JOB.getJobId());
        verify(mJobSchedulerMock).cancel(CONSENT_NOTIFICATION_JOB.getJobId());
        verify(mJobSchedulerMock).cancel(MDD_MAINTENANCE_PERIODIC_TASK_JOB.getJobId());
        verify(mJobSchedulerMock).cancel(MDD_CHARGING_PERIODIC_TASK_JOB.getJobId());
        verify(mJobSchedulerMock).cancel(MDD_CELLULAR_CHARGING_PERIODIC_TASK_JOB.getJobId());
        verify(mJobSchedulerMock).cancel(MDD_WIFI_CHARGING_PERIODIC_TASK_JOB.getJobId());
        verify(mJobSchedulerMock).cancel(ENCRYPTION_KEY_PERIODIC_JOB.getJobId());
        verify(mJobSchedulerMock).cancel(COBALT_LOGGING_JOB.getJobId());
        verify(mJobSchedulerMock).cancel(FLEDGE_KANON_SIGN_JOIN_BACKGROUND_JOB.getJobId());
        verify(mJobSchedulerMock).cancel(AD_PACKAGE_DENY_PRE_PROCESS_JOB.getJobId());

        verifyNoMoreInteractions(mJobSchedulerMock);
    }

    @Test
    public void testDataIsResetAfterConsentIsRevoked() throws Exception {
        mConsentManager.disable(mSpyContext);

        verify(() -> UiStatsLogger.logOptOutSelected());

        SystemClock.sleep(1000);
        verify(mTopicsWorkerMock).clearAllTopicsData(any());
        // TODO(b/240988406): change to test for correct method call
        verify(mAppConsentDaoSpy).clearAllConsentData();
        verify(mEnrollmentDaoSpy).deleteAll();
        verify(mMeasurementImplMock).deleteAllMeasurementData(any());
        verify(mCustomAudienceDaoMock)
                .deleteAllCustomAudienceData(/* scheduleCustomAudienceEnabled= */ true);
        verify(mAppInstallDaoMock).deleteAllAppInstallData();
        verify(mProtectedSignalsDaoMock).deleteAllSignals();
        verify(mFrequencyCapDaoMock).deleteAllHistogramData();
        verify(mEncodedPayloadDaoMock).deleteAllEncodedPayloads();
        verify(mUserProfileIdManagerMock).deleteId();
    }

    @Test
    public void testDataIsResetAfterConsentIsRevokedSignalsDisabled() throws Exception {
        doReturn(false).when(mMockFlags).getProtectedSignalsCleanupEnabled();
        mConsentManager.disable(mSpyContext);

        verify(() -> UiStatsLogger.logOptOutSelected());

        SystemClock.sleep(1000);
        verify(mTopicsWorkerMock).clearAllTopicsData(any());
        // TODO(b/240988406): change to test for correct method call
        verify(mAppConsentDaoSpy).clearAllConsentData();
        verify(mEnrollmentDaoSpy).deleteAll();
        verify(mMeasurementImplMock).deleteAllMeasurementData(any());
        verify(mCustomAudienceDaoMock)
                .deleteAllCustomAudienceData(/* scheduleCustomAudienceEnabled= */ true);
        verify(mAppInstallDaoMock).deleteAllAppInstallData();
        verifyZeroInteractions(mProtectedSignalsDaoMock);
        verify(mFrequencyCapDaoMock).deleteAllHistogramData();
        verifyZeroInteractions(mEncodedPayloadDaoMock);
        verify(mUserProfileIdManagerMock).deleteId();
    }

    @Test
    public void testDataIsResetAfterConsentIsRevokedFrequencyCapFilteringDisabled()
            throws Exception {
        doReturn(false).when(mMockFlags).getFledgeFrequencyCapFilteringEnabled();
        mConsentManager.disable(mSpyContext);

        verify(() -> UiStatsLogger.logOptOutSelected());

        SystemClock.sleep(1000);
        verify(mTopicsWorkerMock).clearAllTopicsData(any());
        // TODO(b/240988406): change to test for correct method call
        verify(mAppConsentDaoSpy).clearAllConsentData();
        verify(mEnrollmentDaoSpy).deleteAll();
        verify(mMeasurementImplMock).deleteAllMeasurementData(any());
        verify(mCustomAudienceDaoMock)
                .deleteAllCustomAudienceData(/* scheduleCustomAudienceEnabled= */ true);
        verifyZeroInteractions(mFrequencyCapDaoMock);
        verify(mAppInstallDaoMock).deleteAllAppInstallData();
        verify(mProtectedSignalsDaoMock).deleteAllSignals();
        verify(mEncodedPayloadDaoMock).deleteAllEncodedPayloads();
        verify(mUserProfileIdManagerMock).deleteId();
    }

    @Test
    public void testDataIsResetAfterConsentIsRevokedAppInstallFilteringDisabled() throws Exception {
        doReturn(false).when(mMockFlags).getFledgeAppInstallFilteringEnabled();
        mConsentManager.disable(mSpyContext);

        verify(() -> UiStatsLogger.logOptOutSelected());

        SystemClock.sleep(1000);
        verify(mTopicsWorkerMock).clearAllTopicsData(any());
        // TODO(b/240988406): change to test for correct method call
        verify(mAppConsentDaoSpy).clearAllConsentData();
        verify(mEnrollmentDaoSpy).deleteAll();
        verify(mMeasurementImplMock).deleteAllMeasurementData(any());
        verify(mCustomAudienceDaoMock)
                .deleteAllCustomAudienceData(/* scheduleCustomAudienceEnabled= */ true);
        verify(mFrequencyCapDaoMock).deleteAllHistogramData();
        verifyZeroInteractions(mAppInstallDaoMock);
        verify(mProtectedSignalsDaoMock).deleteAllSignals();
        verify(mEncodedPayloadDaoMock).deleteAllEncodedPayloads();
        verify(mUserProfileIdManagerMock).deleteId();
    }

    @Test
    public void testDataIsResetAfterConsentIsGiven() throws Exception {
        mConsentManager.enable(mSpyContext);

        verify(() -> UiStatsLogger.logOptInSelected());

        SystemClock.sleep(1000);
        verify(mTopicsWorkerMock).clearAllTopicsData(any());
        // TODO(b/240988406): change to test for correct method call
        verify(mAppConsentDaoSpy).clearAllConsentData();
        verify(mMeasurementImplMock).deleteAllMeasurementData(any());
        verify(mCustomAudienceDaoMock)
                .deleteAllCustomAudienceData(/* scheduleCustomAudienceEnabled= */ true);
        verify(mAppInstallDaoMock).deleteAllAppInstallData();
        verify(mProtectedSignalsDaoMock).deleteAllSignals();
        verify(mFrequencyCapDaoMock).deleteAllHistogramData();
        verify(mEncodedPayloadDaoMock).deleteAllEncodedPayloads();
        verify(mUserProfileIdManagerMock).deleteId();
        verify(mUserProfileIdManagerMock).getOrCreateId();
    }

    @Test
    public void testDataIsResetAfterConsentIsGivenFrequencyCapFilteringDisabled() throws Exception {
        doReturn(false).when(mMockFlags).getFledgeFrequencyCapFilteringEnabled();
        mConsentManager.enable(mSpyContext);

        verify(() -> UiStatsLogger.logOptInSelected());

        SystemClock.sleep(1000);
        verify(mTopicsWorkerMock).clearAllTopicsData(any());
        // TODO(b/240988406): change to test for correct method call
        verify(mAppConsentDaoSpy).clearAllConsentData();
        verify(mMeasurementImplMock).deleteAllMeasurementData(any());
        verify(mCustomAudienceDaoMock)
                .deleteAllCustomAudienceData(/* scheduleCustomAudienceEnabled= */ true);
        verifyZeroInteractions(mFrequencyCapDaoMock);
        verify(mAppInstallDaoMock).deleteAllAppInstallData();
        verify(mUserProfileIdManagerMock).deleteId();
        verify(mUserProfileIdManagerMock).getOrCreateId();
    }

    @Test
    public void testDataIsResetAfterConsentIsGivenAppInstallFilteringDisabled() throws Exception {
        doReturn(false).when(mMockFlags).getFledgeAppInstallFilteringEnabled();
        mConsentManager.enable(mSpyContext);

        verify(() -> UiStatsLogger.logOptInSelected());

        SystemClock.sleep(1000);
        verify(mTopicsWorkerMock).clearAllTopicsData(any());
        // TODO(b/240988406): change to test for correct method call
        verify(mAppConsentDaoSpy).clearAllConsentData();
        verify(mMeasurementImplMock).deleteAllMeasurementData(any());
        verify(mCustomAudienceDaoMock)
                .deleteAllCustomAudienceData(/* scheduleCustomAudienceEnabled= */ true);
        verify(mFrequencyCapDaoMock).deleteAllHistogramData();
        verifyZeroInteractions(mAppInstallDaoMock);
        verify(mUserProfileIdManagerMock).deleteId();
        verify(mUserProfileIdManagerMock).getOrCreateId();
    }

    @Test
    public void testDataIsResetAfterConsentIsGivenSignalsDisabled() throws Exception {
        doReturn(false).when(mMockFlags).getProtectedSignalsCleanupEnabled();
        mConsentManager.enable(mSpyContext);

        verify(() -> UiStatsLogger.logOptInSelected());

        SystemClock.sleep(1000);
        verify(mTopicsWorkerMock).clearAllTopicsData(any());
        // TODO(b/240988406): change to test for correct method call
        verify(mAppConsentDaoSpy).clearAllConsentData();
        verify(mMeasurementImplMock).deleteAllMeasurementData(any());
        verify(mCustomAudienceDaoMock)
                .deleteAllCustomAudienceData(/* scheduleCustomAudienceEnabled= */ true);
        verify(mAppInstallDaoMock).deleteAllAppInstallData();
        verifyZeroInteractions(mProtectedSignalsDaoMock);
        verify(mFrequencyCapDaoMock).deleteAllHistogramData();
        verifyZeroInteractions(mEncodedPayloadDaoMock);
        verify(mUserProfileIdManagerMock).deleteId();
        verify(mUserProfileIdManagerMock).getOrCreateId();
    }

    @Test
    public void testIsFledgeConsentRevokedForAppWithFullApiConsentGaUxEnabled_ppApiOnly()
            throws Exception {
        when(mMockFlags.getGaUxFeatureEnabled()).thenReturn(true);
        mConsentManager.enable(mSpyContext, AdServicesApiType.FLEDGE);
        assertTrue(mConsentManager.getConsent(AdServicesApiType.FLEDGE).isGiven());

        verify(() -> UiStatsLogger.logOptInSelected(AdServicesApiType.FLEDGE));

        mockGetPackageUid(AppConsentDaoFixture.APP10_PACKAGE_NAME, AppConsentDaoFixture.APP10_UID);
        mockGetPackageUid(AppConsentDaoFixture.APP20_PACKAGE_NAME, AppConsentDaoFixture.APP20_UID);
        mockGetPackageUid(AppConsentDaoFixture.APP30_PACKAGE_NAME, AppConsentDaoFixture.APP30_UID);

        mDatastore.putBoolean(AppConsentDaoFixture.APP10_DATASTORE_KEY, false);
        mDatastore.putBoolean(AppConsentDaoFixture.APP20_DATASTORE_KEY, true);

        assertFalse(
                mConsentManager.isFledgeConsentRevokedForApp(
                        AppConsentDaoFixture.APP10_PACKAGE_NAME));
        assertTrue(
                mConsentManager.isFledgeConsentRevokedForApp(
                        AppConsentDaoFixture.APP20_PACKAGE_NAME));
        assertFalse(
                mConsentManager.isFledgeConsentRevokedForApp(
                        AppConsentDaoFixture.APP30_PACKAGE_NAME));
    }

    @Test
    public void testIsFledgeConsentRevokedForAppWithFullApiConsentGaUxEnabled_systemServerOnly()
            throws Exception, RemoteException {
        when(mMockFlags.getGaUxFeatureEnabled()).thenReturn(true);
        int consentSourceOfTruth = Flags.SYSTEM_SERVER_ONLY;
        mConsentManager = getConsentManagerByConsentSourceOfTruth(consentSourceOfTruth);
        doReturn(ConsentParcel.createGivenConsent(ConsentParcel.FLEDGE))
                .when(mMockIAdServicesManager)
                .getConsent(ConsentParcel.FLEDGE);

        assertTrue(mConsentManager.getConsent(AdServicesApiType.FLEDGE).isGiven());
        mockGetPackageUid(AppConsentDaoFixture.APP10_PACKAGE_NAME, AppConsentDaoFixture.APP10_UID);
        mockGetPackageUid(AppConsentDaoFixture.APP20_PACKAGE_NAME, AppConsentDaoFixture.APP20_UID);
        mockGetPackageUid(AppConsentDaoFixture.APP30_PACKAGE_NAME, AppConsentDaoFixture.APP30_UID);

        doReturn(false)
                .when(mMockIAdServicesManager)
                .isConsentRevokedForApp(
                        AppConsentDaoFixture.APP10_PACKAGE_NAME, AppConsentDaoFixture.APP10_UID);
        doReturn(true)
                .when(mMockIAdServicesManager)
                .isConsentRevokedForApp(
                        AppConsentDaoFixture.APP20_PACKAGE_NAME, AppConsentDaoFixture.APP20_UID);
        doReturn(false)
                .when(mMockIAdServicesManager)
                .isConsentRevokedForApp(
                        AppConsentDaoFixture.APP30_PACKAGE_NAME, AppConsentDaoFixture.APP30_UID);

        assertFalse(
                mConsentManager.isFledgeConsentRevokedForApp(
                        AppConsentDaoFixture.APP10_PACKAGE_NAME));
        assertTrue(
                mConsentManager.isFledgeConsentRevokedForApp(
                        AppConsentDaoFixture.APP20_PACKAGE_NAME));
        assertFalse(
                mConsentManager.isFledgeConsentRevokedForApp(
                        AppConsentDaoFixture.APP30_PACKAGE_NAME));
    }

    @Test
    public void testIsFledgeConsentRevokedForAppWithFullApiConsentGaUxEnabled_appSearchOnly()
            throws Exception {
        runTestIsFledgeConsentRevokedForAppWithFullApiConsentAppSearchOnly(true);
    }

    @Test
    public void testIsFledgeConsentRevokedForAppWithFullApiConsentGaUxDisabled_appSearchOnly()
            throws Exception {
        runTestIsFledgeConsentRevokedForAppWithFullApiConsentAppSearchOnly(false);
    }

    private void runTestIsFledgeConsentRevokedForAppWithFullApiConsentAppSearchOnly(
            boolean isGaUxEnabled) throws Exception {
        when(mMockFlags.getGaUxFeatureEnabled()).thenReturn(isGaUxEnabled);
        when(mMockFlags.getEnableAppsearchConsentData()).thenReturn(true);
        int consentSourceOfTruth = Flags.APPSEARCH_ONLY;
        mConsentManager = getConsentManagerByConsentSourceOfTruth(consentSourceOfTruth);
        when(mAppSearchConsentManagerMock.getConsent(any())).thenReturn(true);

        mConsentManager.enable(mSpyContext, AdServicesApiType.FLEDGE);
        verify(() -> UiStatsLogger.logOptInSelected(AdServicesApiType.FLEDGE));

        String app1 = AppConsentDaoFixture.APP10_PACKAGE_NAME;
        String app2 = AppConsentDaoFixture.APP20_PACKAGE_NAME;
        String app3 = AppConsentDaoFixture.APP30_PACKAGE_NAME;
        mockGetPackageUid(app1, AppConsentDaoFixture.APP10_UID);
        mockGetPackageUid(app2, AppConsentDaoFixture.APP20_UID);
        mockGetPackageUid(app3, AppConsentDaoFixture.APP30_UID);

        when(mAppSearchConsentManagerMock.isFledgeConsentRevokedForApp(app1)).thenReturn(false);
        when(mAppSearchConsentManagerMock.isFledgeConsentRevokedForApp(app2)).thenReturn(true);
        when(mAppSearchConsentManagerMock.isFledgeConsentRevokedForApp(app3)).thenReturn(false);

        assertFalse(mConsentManager.isFledgeConsentRevokedForApp(app1));
        assertTrue(mConsentManager.isFledgeConsentRevokedForApp(app2));
        assertFalse(mConsentManager.isFledgeConsentRevokedForApp(app3));
    }

    @Test
    public void testIsFledgeConsentRevokedForAppWithFullApiConsentGaUxEnabled_ppApiAndSystemServer()
            throws Exception, RemoteException {
        when(mMockFlags.getGaUxFeatureEnabled()).thenReturn(true);
        int consentSourceOfTruth = Flags.PPAPI_AND_SYSTEM_SERVER;
        mConsentManager = getConsentManagerByConsentSourceOfTruth(consentSourceOfTruth);
        doReturn(ConsentParcel.createGivenConsent(ConsentParcel.FLEDGE))
                .when(mMockIAdServicesManager)
                .getConsent(ConsentParcel.FLEDGE);

        assertTrue(mConsentManager.getConsent(AdServicesApiType.FLEDGE).isGiven());
        mockGetPackageUid(AppConsentDaoFixture.APP10_PACKAGE_NAME, AppConsentDaoFixture.APP10_UID);
        mockGetPackageUid(AppConsentDaoFixture.APP20_PACKAGE_NAME, AppConsentDaoFixture.APP20_UID);
        mockGetPackageUid(AppConsentDaoFixture.APP30_PACKAGE_NAME, AppConsentDaoFixture.APP30_UID);

        doReturn(false)
                .when(mMockIAdServicesManager)
                .isConsentRevokedForApp(
                        AppConsentDaoFixture.APP10_PACKAGE_NAME, AppConsentDaoFixture.APP10_UID);
        doReturn(true)
                .when(mMockIAdServicesManager)
                .isConsentRevokedForApp(
                        AppConsentDaoFixture.APP20_PACKAGE_NAME, AppConsentDaoFixture.APP20_UID);
        doReturn(false)
                .when(mMockIAdServicesManager)
                .isConsentRevokedForApp(
                        AppConsentDaoFixture.APP30_PACKAGE_NAME, AppConsentDaoFixture.APP30_UID);

        assertFalse(
                mConsentManager.isFledgeConsentRevokedForApp(
                        AppConsentDaoFixture.APP10_PACKAGE_NAME));
        assertTrue(
                mConsentManager.isFledgeConsentRevokedForApp(
                        AppConsentDaoFixture.APP20_PACKAGE_NAME));
        assertFalse(
                mConsentManager.isFledgeConsentRevokedForApp(
                        AppConsentDaoFixture.APP30_PACKAGE_NAME));
    }

    @Test
    public void testIsFledgeConsentRevokedForAppWithoutPrivacySandboxConsentGaUxEnabled_ppApiOnly()
            throws Exception {
        when(mMockFlags.getGaUxFeatureEnabled()).thenReturn(true);
        mConsentManager.disable(mSpyContext, AdServicesApiType.FLEDGE);
        assertFalse(mConsentManager.getConsent(AdServicesApiType.FLEDGE).isGiven());

        verify(() -> UiStatsLogger.logOptOutSelected(AdServicesApiType.FLEDGE));

        mockGetPackageUid(AppConsentDaoFixture.APP10_PACKAGE_NAME, AppConsentDaoFixture.APP10_UID);
        mockGetPackageUid(AppConsentDaoFixture.APP20_PACKAGE_NAME, AppConsentDaoFixture.APP20_UID);

        assertTrue(
                mConsentManager.isFledgeConsentRevokedForApp(
                        AppConsentDaoFixture.APP10_PACKAGE_NAME));
        assertTrue(
                mConsentManager.isFledgeConsentRevokedForApp(
                        AppConsentDaoFixture.APP20_PACKAGE_NAME));
    }

    @Test
    public void testIsFledgeConsentRevokedForAppWithoutPrivacySandboxConsentGaUxEnabled_sysServer()
            throws Exception {
        when(mMockFlags.getGaUxFeatureEnabled()).thenReturn(true);
        mConsentManager = getConsentManagerByConsentSourceOfTruth(Flags.SYSTEM_SERVER_ONLY);
        doReturn(ConsentParcel.createRevokedConsent(ConsentParcel.FLEDGE))
                .when(mMockIAdServicesManager)
                .getConsent(ConsentParcel.FLEDGE);
        assertFalse(mConsentManager.getConsent(AdServicesApiType.FLEDGE).isGiven());

        assertTrue(
                mConsentManager.isFledgeConsentRevokedForApp(
                        AppConsentDaoFixture.APP10_PACKAGE_NAME));
        assertTrue(
                mConsentManager.isFledgeConsentRevokedForApp(
                        AppConsentDaoFixture.APP20_PACKAGE_NAME));
    }

    @Test
    public void testIsFledgeConsentRevokedForAppWithoutPrivacySandboxConsentGaUxEnabled_bothSrc()
            throws Exception {
        when(mMockFlags.getGaUxFeatureEnabled()).thenReturn(true);
        mConsentManager = getConsentManagerByConsentSourceOfTruth(Flags.PPAPI_AND_SYSTEM_SERVER);
        doReturn(ConsentParcel.createRevokedConsent(ConsentParcel.FLEDGE))
                .when(mMockIAdServicesManager)
                .getConsent(ConsentParcel.FLEDGE);
        assertFalse(mConsentManager.getConsent(AdServicesApiType.FLEDGE).isGiven());

        assertTrue(
                mConsentManager.isFledgeConsentRevokedForApp(
                        AppConsentDaoFixture.APP10_PACKAGE_NAME));
        assertTrue(
                mConsentManager.isFledgeConsentRevokedForApp(
                        AppConsentDaoFixture.APP20_PACKAGE_NAME));
    }

    @Test
    public void testIsFledgeConsentRevokedForNotFoundAppGaUxEnabledThrows_ppApiOnly()
            throws Exception {
        when(mMockFlags.getGaUxFeatureEnabled()).thenReturn(true);
        mConsentManager.enable(mSpyContext, AdServicesApiType.FLEDGE);
        assertTrue(mConsentManager.getConsent(AdServicesApiType.FLEDGE).isGiven());

        verify(() -> UiStatsLogger.logOptInSelected(AdServicesApiType.FLEDGE));

        mockThrowExceptionOnGetPackageUid(AppConsentDaoFixture.APP_NOT_FOUND_PACKAGE_NAME);
        assertThrows(
                IllegalArgumentException.class,
                () ->
                        mConsentManager.isFledgeConsentRevokedForApp(
                                AppConsentDaoFixture.APP_NOT_FOUND_PACKAGE_NAME));
    }

    @Test
    public void testIsFledgeConsentRevokedForNotFoundAppGaUxEnabledThrows_systemServerOnly()
            throws Exception, RemoteException {
        when(mMockFlags.getGaUxFeatureEnabled()).thenReturn(true);
        mConsentManager = getConsentManagerByConsentSourceOfTruth(Flags.SYSTEM_SERVER_ONLY);
        doReturn(ConsentParcel.createGivenConsent(ConsentParcel.FLEDGE))
                .when(mMockIAdServicesManager)
                .getConsent(ConsentParcel.FLEDGE);
        assertTrue(mConsentManager.getConsent(AdServicesApiType.FLEDGE).isGiven());

        mockThrowExceptionOnGetPackageUid(AppConsentDaoFixture.APP_NOT_FOUND_PACKAGE_NAME);
        assertThrows(
                IllegalArgumentException.class,
                () ->
                        mConsentManager.isFledgeConsentRevokedForApp(
                                AppConsentDaoFixture.APP_NOT_FOUND_PACKAGE_NAME));
    }

    @Test
    public void testIsFledgeConsentRevokedForNotFoundAppGaUxEnabledThrows_ppApiAndSystemServer()
            throws Exception, RemoteException {
        when(mMockFlags.getGaUxFeatureEnabled()).thenReturn(true);
        mConsentManager = getConsentManagerByConsentSourceOfTruth(Flags.PPAPI_AND_SYSTEM_SERVER);
        doReturn(ConsentParcel.createGivenConsent(ConsentParcel.FLEDGE))
                .when(mMockIAdServicesManager)
                .getConsent(ConsentParcel.FLEDGE);
        assertTrue(mConsentManager.getConsent(AdServicesApiType.FLEDGE).isGiven());

        mockThrowExceptionOnGetPackageUid(AppConsentDaoFixture.APP_NOT_FOUND_PACKAGE_NAME);
        assertThrows(
                IllegalArgumentException.class,
                () ->
                        mConsentManager.isFledgeConsentRevokedForApp(
                                AppConsentDaoFixture.APP_NOT_FOUND_PACKAGE_NAME));
    }

    // AppSearch test for isFledgeConsentRevokedForAppAfterSettingFledgeUse with GA UX enabled.
    @Test
    public void testIsFledgeConsentRevokedForAppAfterSetFledgeUseWithFullApiConsentGaUxEnabled_as()
            throws Exception {
        runTestIsFledgeConsentRevokedForAppAfterSetFledgeUseWithFullApiConsentAppSearch(true);
    }

    private void runTestIsFledgeConsentRevokedForAppAfterSetFledgeUseWithFullApiConsentAppSearch(
            boolean isGaUxEnabled) throws Exception {
        mConsentManager = getConsentManagerByConsentSourceOfTruth(Flags.APPSEARCH_ONLY);
        when(mMockFlags.getEnableAppsearchConsentData()).thenReturn(true);
        when(mMockFlags.getGaUxFeatureEnabled()).thenReturn(isGaUxEnabled);
        mConsentManager.enable(mSpyContext);
        when(mAppSearchConsentManagerMock.getConsent(any())).thenReturn(true);

        verify(() -> UiStatsLogger.logOptInSelected());

        String app1 = AppConsentDaoFixture.APP10_PACKAGE_NAME;
        String app2 = AppConsentDaoFixture.APP20_PACKAGE_NAME;
        String app3 = AppConsentDaoFixture.APP30_PACKAGE_NAME;
        mockGetPackageUid(app1, AppConsentDaoFixture.APP10_UID);
        mockGetPackageUid(app2, AppConsentDaoFixture.APP20_UID);
        mockGetPackageUid(app3, AppConsentDaoFixture.APP30_UID);

        when(mAppSearchConsentManagerMock.isFledgeConsentRevokedForAppAfterSettingFledgeUse(app1))
                .thenReturn(false);
        when(mAppSearchConsentManagerMock.isFledgeConsentRevokedForAppAfterSettingFledgeUse(app2))
                .thenReturn(true);
        when(mAppSearchConsentManagerMock.isFledgeConsentRevokedForAppAfterSettingFledgeUse(app3))
                .thenReturn(false);

        assertFalse(mConsentManager.isFledgeConsentRevokedForAppAfterSettingFledgeUse(app1));
        assertTrue(mConsentManager.isFledgeConsentRevokedForAppAfterSettingFledgeUse(app2));
        assertFalse(mConsentManager.isFledgeConsentRevokedForAppAfterSettingFledgeUse(app3));
    }

    @Test
    public void
            testIsFledgeConsentRevokedForAppAfterSetFledgeUseWithFullApiConsentGaUxEnabled_ppApi()
                    throws Exception {
        when(mMockFlags.getGaUxFeatureEnabled()).thenReturn(true);
        mConsentManager.enable(mSpyContext, AdServicesApiType.FLEDGE);
        assertTrue(mConsentManager.getConsent(AdServicesApiType.FLEDGE).isGiven());

        verify(() -> UiStatsLogger.logOptInSelected(AdServicesApiType.FLEDGE));

        mockGetPackageUid(AppConsentDaoFixture.APP10_PACKAGE_NAME, AppConsentDaoFixture.APP10_UID);
        mockGetPackageUid(AppConsentDaoFixture.APP20_PACKAGE_NAME, AppConsentDaoFixture.APP20_UID);
        mockGetPackageUid(AppConsentDaoFixture.APP30_PACKAGE_NAME, AppConsentDaoFixture.APP30_UID);

        mDatastore.putBoolean(AppConsentDaoFixture.APP10_DATASTORE_KEY, false);
        mDatastore.putBoolean(AppConsentDaoFixture.APP20_DATASTORE_KEY, true);

        assertFalse(
                mConsentManager.isFledgeConsentRevokedForAppAfterSettingFledgeUse(
                        AppConsentDaoFixture.APP10_PACKAGE_NAME));
        assertTrue(
                mConsentManager.isFledgeConsentRevokedForAppAfterSettingFledgeUse(
                        AppConsentDaoFixture.APP20_PACKAGE_NAME));
        assertFalse(
                mConsentManager.isFledgeConsentRevokedForAppAfterSettingFledgeUse(
                        AppConsentDaoFixture.APP30_PACKAGE_NAME));
    }

    @Test
    public void
            testIsFledgeConsentRevokedForAppAfterSetFledgeUseWithFullApiConsentGaUxEnabled_sysSer()
                    throws Exception, RemoteException {
        when(mMockFlags.getGaUxFeatureEnabled()).thenReturn(true);
        mConsentManager = getConsentManagerByConsentSourceOfTruth(Flags.SYSTEM_SERVER_ONLY);
        doReturn(ConsentParcel.createGivenConsent(ConsentParcel.FLEDGE))
                .when(mMockIAdServicesManager)
                .getConsent(ConsentParcel.FLEDGE);
        assertTrue(mConsentManager.getConsent(AdServicesApiType.FLEDGE).isGiven());

        mockGetPackageUid(AppConsentDaoFixture.APP10_PACKAGE_NAME, AppConsentDaoFixture.APP10_UID);
        mockGetPackageUid(AppConsentDaoFixture.APP20_PACKAGE_NAME, AppConsentDaoFixture.APP20_UID);
        mockGetPackageUid(AppConsentDaoFixture.APP30_PACKAGE_NAME, AppConsentDaoFixture.APP30_UID);

        doReturn(false)
                .when(mMockIAdServicesManager)
                .setConsentForAppIfNew(
                        AppConsentDaoFixture.APP10_PACKAGE_NAME,
                        AppConsentDaoFixture.APP10_UID,
                        false);
        doReturn(true)
                .when(mMockIAdServicesManager)
                .setConsentForAppIfNew(
                        AppConsentDaoFixture.APP20_PACKAGE_NAME,
                        AppConsentDaoFixture.APP20_UID,
                        false);
        doReturn(false)
                .when(mMockIAdServicesManager)
                .setConsentForAppIfNew(
                        AppConsentDaoFixture.APP30_PACKAGE_NAME,
                        AppConsentDaoFixture.APP30_UID,
                        false);

        assertFalse(
                mConsentManager.isFledgeConsentRevokedForAppAfterSettingFledgeUse(
                        AppConsentDaoFixture.APP10_PACKAGE_NAME));
        assertTrue(
                mConsentManager.isFledgeConsentRevokedForAppAfterSettingFledgeUse(
                        AppConsentDaoFixture.APP20_PACKAGE_NAME));
        assertFalse(
                mConsentManager.isFledgeConsentRevokedForAppAfterSettingFledgeUse(
                        AppConsentDaoFixture.APP30_PACKAGE_NAME));
    }

    @Test
    public void
            testIsFledgeConsentRevokedForAppAfterSetFledgeUseWithFullApiConsentGaUxEnabled_both()
                    throws Exception, RemoteException {
        when(mMockFlags.getGaUxFeatureEnabled()).thenReturn(true);
        mConsentManager = getConsentManagerByConsentSourceOfTruth(Flags.PPAPI_AND_SYSTEM_SERVER);
        doReturn(ConsentParcel.createGivenConsent(ConsentParcel.FLEDGE))
                .when(mMockIAdServicesManager)
                .getConsent(ConsentParcel.FLEDGE);
        assertTrue(mConsentManager.getConsent(AdServicesApiType.FLEDGE).isGiven());

        mockGetPackageUid(AppConsentDaoFixture.APP10_PACKAGE_NAME, AppConsentDaoFixture.APP10_UID);
        mockGetPackageUid(AppConsentDaoFixture.APP20_PACKAGE_NAME, AppConsentDaoFixture.APP20_UID);
        mockGetPackageUid(AppConsentDaoFixture.APP30_PACKAGE_NAME, AppConsentDaoFixture.APP30_UID);

        doReturn(false)
                .when(mMockIAdServicesManager)
                .setConsentForAppIfNew(
                        AppConsentDaoFixture.APP10_PACKAGE_NAME,
                        AppConsentDaoFixture.APP10_UID,
                        false);
        doReturn(true)
                .when(mMockIAdServicesManager)
                .setConsentForAppIfNew(
                        AppConsentDaoFixture.APP20_PACKAGE_NAME,
                        AppConsentDaoFixture.APP20_UID,
                        false);
        doReturn(false)
                .when(mMockIAdServicesManager)
                .setConsentForAppIfNew(
                        AppConsentDaoFixture.APP30_PACKAGE_NAME,
                        AppConsentDaoFixture.APP30_UID,
                        false);

        assertFalse(
                mConsentManager.isFledgeConsentRevokedForAppAfterSettingFledgeUse(
                        AppConsentDaoFixture.APP10_PACKAGE_NAME));
        assertTrue(
                mConsentManager.isFledgeConsentRevokedForAppAfterSettingFledgeUse(
                        AppConsentDaoFixture.APP20_PACKAGE_NAME));
        assertFalse(
                mConsentManager.isFledgeConsentRevokedForAppAfterSettingFledgeUse(
                        AppConsentDaoFixture.APP30_PACKAGE_NAME));
    }

    @Test
    public void
            testIsFledgeConsentRevokedForAppSetFledgeUseNoPrivacySandboxConsentGaUxEnabled_ppApi()
                    throws Exception {
        when(mMockFlags.getGaUxFeatureEnabled()).thenReturn(true);

        mConsentManager.disable(mSpyContext, AdServicesApiType.FLEDGE);
        assertFalse(mConsentManager.getConsent(AdServicesApiType.FLEDGE).isGiven());

        mockGetPackageUid(AppConsentDaoFixture.APP10_PACKAGE_NAME, AppConsentDaoFixture.APP10_UID);
        mockGetPackageUid(AppConsentDaoFixture.APP20_PACKAGE_NAME, AppConsentDaoFixture.APP20_UID);

        assertTrue(
                mConsentManager.isFledgeConsentRevokedForAppAfterSettingFledgeUse(
                        AppConsentDaoFixture.APP10_PACKAGE_NAME));
        assertTrue(
                mConsentManager.isFledgeConsentRevokedForAppAfterSettingFledgeUse(
                        AppConsentDaoFixture.APP20_PACKAGE_NAME));
    }

    @Test
    public void
            testIsFledgeConsentRevokedForAppSetFledgeUseNoPrivacySandboxConsentGaUxEnabled_sysSer()
                    throws Exception {
        when(mMockFlags.getGaUxFeatureEnabled()).thenReturn(true);
        mConsentManager = getConsentManagerByConsentSourceOfTruth(Flags.SYSTEM_SERVER_ONLY);
        doReturn(ConsentParcel.createRevokedConsent(ConsentParcel.FLEDGE))
                .when(mMockIAdServicesManager)
                .getConsent(ConsentParcel.FLEDGE);
        assertFalse(mConsentManager.getConsent(AdServicesApiType.FLEDGE).isGiven());

        assertTrue(
                mConsentManager.isFledgeConsentRevokedForAppAfterSettingFledgeUse(
                        AppConsentDaoFixture.APP10_PACKAGE_NAME));
        assertTrue(
                mConsentManager.isFledgeConsentRevokedForAppAfterSettingFledgeUse(
                        AppConsentDaoFixture.APP20_PACKAGE_NAME));
    }

    @Test
    public void
            testIsFledgeConsentRevokedForAppSetFledgeUseNoPrivacySandboxConsentGaUxEnabled_both()
                    throws Exception {
        when(mMockFlags.getGaUxFeatureEnabled()).thenReturn(true);
        mConsentManager = getConsentManagerByConsentSourceOfTruth(Flags.PPAPI_AND_SYSTEM_SERVER);
        doReturn(ConsentParcel.createRevokedConsent(ConsentParcel.FLEDGE))
                .when(mMockIAdServicesManager)
                .getConsent(ConsentParcel.FLEDGE);
        assertFalse(mConsentManager.getConsent(AdServicesApiType.FLEDGE).isGiven());

        assertTrue(
                mConsentManager.isFledgeConsentRevokedForAppAfterSettingFledgeUse(
                        AppConsentDaoFixture.APP10_PACKAGE_NAME));
        assertTrue(
                mConsentManager.isFledgeConsentRevokedForAppAfterSettingFledgeUse(
                        AppConsentDaoFixture.APP20_PACKAGE_NAME));
    }

    @Test
    public void testIsFledgeConsentRevokedForAppAfterSettingFledgeUseThrows_ppApiOnly()
            throws Exception {
        mConsentManager.enable(mSpyContext, AdServicesApiType.FLEDGE);
        assertTrue(mConsentManager.getConsent(AdServicesApiType.FLEDGE).isGiven());

        verify(() -> UiStatsLogger.logOptInSelected(AdServicesApiType.FLEDGE));

        mockThrowExceptionOnGetPackageUid(AppConsentDaoFixture.APP_NOT_FOUND_PACKAGE_NAME);
        assertThrows(
                IllegalArgumentException.class,
                () ->
                        mConsentManager.isFledgeConsentRevokedForAppAfterSettingFledgeUse(
                                AppConsentDaoFixture.APP_NOT_FOUND_PACKAGE_NAME));
    }

    @Test
    public void testIsFledgeConsentRevokedForAppAfterSettingFledgeUseThrows_systemServerOnly()
            throws Exception, RemoteException {
        mConsentManager = getConsentManagerByConsentSourceOfTruth(Flags.SYSTEM_SERVER_ONLY);
        doReturn(ConsentParcel.createGivenConsent(ConsentParcel.FLEDGE))
                .when(mMockIAdServicesManager)
                .getConsent(ConsentParcel.FLEDGE);
        assertTrue(mConsentManager.getConsent(AdServicesApiType.FLEDGE).isGiven());

        mockThrowExceptionOnGetPackageUid(AppConsentDaoFixture.APP_NOT_FOUND_PACKAGE_NAME);
        assertThrows(
                IllegalArgumentException.class,
                () ->
                        mConsentManager.isFledgeConsentRevokedForAppAfterSettingFledgeUse(
                                AppConsentDaoFixture.APP_NOT_FOUND_PACKAGE_NAME));
    }

    @Test
    public void testIsFledgeConsentRevokedForAppAfterSettingFledgeUseThrows_ppApiAndSystemServer()
            throws Exception, RemoteException {
        mConsentManager = getConsentManagerByConsentSourceOfTruth(Flags.PPAPI_AND_SYSTEM_SERVER);
        doReturn(ConsentParcel.createGivenConsent(ConsentParcel.FLEDGE))
                .when(mMockIAdServicesManager)
                .getConsent(ConsentParcel.FLEDGE);
        assertTrue(mConsentManager.getConsent(AdServicesApiType.FLEDGE).isGiven());

        mockThrowExceptionOnGetPackageUid(AppConsentDaoFixture.APP_NOT_FOUND_PACKAGE_NAME);
        assertThrows(
                IllegalArgumentException.class,
                () ->
                        mConsentManager.isFledgeConsentRevokedForAppAfterSettingFledgeUse(
                                AppConsentDaoFixture.APP_NOT_FOUND_PACKAGE_NAME));
    }

    @Test
    public void testGetKnownAppsWithConsent_ppApiOnly() throws Exception {
        mConsentManager.enable(mSpyContext);
        assertTrue(mConsentManager.getConsent().isGiven());

        verify(() -> UiStatsLogger.logOptInSelected());

        mockGetPackageUid(AppConsentDaoFixture.APP10_PACKAGE_NAME, AppConsentDaoFixture.APP10_UID);
        mockGetPackageUid(AppConsentDaoFixture.APP20_PACKAGE_NAME, AppConsentDaoFixture.APP20_UID);
        mockGetPackageUid(AppConsentDaoFixture.APP30_PACKAGE_NAME, AppConsentDaoFixture.APP30_UID);

        mDatastore.putBoolean(AppConsentDaoFixture.APP10_DATASTORE_KEY, false);
        mDatastore.putBoolean(AppConsentDaoFixture.APP20_DATASTORE_KEY, false);
        mDatastore.putBoolean(AppConsentDaoFixture.APP30_DATASTORE_KEY, false);
        List<ApplicationInfo> applicationsInstalled =
                createApplicationInfos(
                        AppConsentDaoFixture.APP10_PACKAGE_NAME,
                        AppConsentDaoFixture.APP20_PACKAGE_NAME,
                        AppConsentDaoFixture.APP30_PACKAGE_NAME);
        mockInstalledApplications(applicationsInstalled);

        ImmutableList<App> knownAppsWithConsent = mConsentManager.getKnownAppsWithConsent();
        ImmutableList<App> appsWithRevokedConsent = mConsentManager.getAppsWithRevokedConsent();

        // all apps have received a consent
        assertThat(knownAppsWithConsent).hasSize(3);
        assertThat(appsWithRevokedConsent).isEmpty();
    }

    @Test
    public void testGetKnownAppsWithConsent_systemServerOnly() throws Exception {
        mConsentManager = getConsentManagerByConsentSourceOfTruth(Flags.SYSTEM_SERVER_ONLY);
        doReturn(ConsentParcel.createGivenConsent(ConsentParcel.ALL_API))
                .when(mMockIAdServicesManager)
                .getConsent(ConsentParcel.ALL_API);
        assertTrue(mConsentManager.getConsent().isGiven());

        List<ApplicationInfo> applicationsInstalled =
                createApplicationInfos(
                        AppConsentDaoFixture.APP10_PACKAGE_NAME,
                        AppConsentDaoFixture.APP20_PACKAGE_NAME,
                        AppConsentDaoFixture.APP30_PACKAGE_NAME);
        List<String> applicationsInstalledNames =
                applicationsInstalled.stream()
                        .map(applicationInfo -> applicationInfo.packageName)
                        .collect(Collectors.toList());
        mockInstalledApplications(applicationsInstalled);

        doReturn(applicationsInstalledNames)
                .when(mMockIAdServicesManager)
                .getKnownAppsWithConsent(
                        argThat(new ListMatcherIgnoreOrder(applicationsInstalledNames)));
        doReturn(List.of())
                .when(mMockIAdServicesManager)
                .getAppsWithRevokedConsent(
                        argThat(new ListMatcherIgnoreOrder(applicationsInstalledNames)));

        ImmutableList<App> knownAppsWithConsent = mConsentManager.getKnownAppsWithConsent();
        ImmutableList<App> appsWithRevokedConsent = mConsentManager.getAppsWithRevokedConsent();

        verify(mMockIAdServicesManager)
                .getKnownAppsWithConsent(
                        argThat(new ListMatcherIgnoreOrder(applicationsInstalledNames)));
        verify(mMockIAdServicesManager)
                .getAppsWithRevokedConsent(
                        argThat(new ListMatcherIgnoreOrder(applicationsInstalledNames)));

        verify(mAppConsentDaoSpy, times(2)).getInstalledPackages();
        verifyNoMoreInteractions(mAppConsentDaoSpy);

        // all apps have received a consent
        assertThat(knownAppsWithConsent).hasSize(3);
        assertThat(appsWithRevokedConsent).isEmpty();
    }

    @Test
    public void testGetKnownAppsWithConsent_ppApiAndSystemServer() throws Exception {
        mConsentManager = getConsentManagerByConsentSourceOfTruth(Flags.PPAPI_AND_SYSTEM_SERVER);
        doReturn(ConsentParcel.createGivenConsent(ConsentParcel.ALL_API))
                .when(mMockIAdServicesManager)
                .getConsent(ConsentParcel.ALL_API);
        assertTrue(mConsentManager.getConsent().isGiven());

        List<ApplicationInfo> applicationsInstalled =
                createApplicationInfos(
                        AppConsentDaoFixture.APP10_PACKAGE_NAME,
                        AppConsentDaoFixture.APP20_PACKAGE_NAME,
                        AppConsentDaoFixture.APP30_PACKAGE_NAME);
        List<String> applicationsInstalledNames =
                applicationsInstalled.stream()
                        .map(applicationInfo -> applicationInfo.packageName)
                        .collect(Collectors.toList());
        mockInstalledApplications(applicationsInstalled);

        doReturn(applicationsInstalledNames)
                .when(mMockIAdServicesManager)
                .getKnownAppsWithConsent(
                        argThat(new ListMatcherIgnoreOrder(applicationsInstalledNames)));
        doReturn(List.of())
                .when(mMockIAdServicesManager)
                .getAppsWithRevokedConsent(
                        argThat(new ListMatcherIgnoreOrder(applicationsInstalledNames)));

        ImmutableList<App> knownAppsWithConsent = mConsentManager.getKnownAppsWithConsent();
        ImmutableList<App> appsWithRevokedConsent = mConsentManager.getAppsWithRevokedConsent();

        verify(mMockIAdServicesManager)
                .getKnownAppsWithConsent(
                        argThat(new ListMatcherIgnoreOrder(applicationsInstalledNames)));
        verify(mMockIAdServicesManager)
                .getAppsWithRevokedConsent(
                        argThat(new ListMatcherIgnoreOrder(applicationsInstalledNames)));

        verify(mAppConsentDaoSpy, times(2)).getInstalledPackages();
        verifyNoMoreInteractions(mAppConsentDaoSpy);

        // all apps have received a consent
        assertThat(knownAppsWithConsent).hasSize(3);
        assertThat(appsWithRevokedConsent).isEmpty();
    }

    @Test
    public void testGetKnownAppsWithConsent_appSearchOnly() {
        mConsentManager = getConsentManagerByConsentSourceOfTruth(Flags.APPSEARCH_ONLY);
        when(mMockFlags.getEnableAppsearchConsentData()).thenReturn(true);

        ImmutableList<App> consentedAppsList =
                ImmutableList.of(App.create(AppConsentDaoFixture.APP10_PACKAGE_NAME));
        ImmutableList<App> revokedAppsList =
                ImmutableList.of(
                        App.create(AppConsentDaoFixture.APP20_PACKAGE_NAME),
                        App.create(AppConsentDaoFixture.APP30_PACKAGE_NAME));

        doReturn(consentedAppsList).when(mAppSearchConsentManagerMock).getKnownAppsWithConsent();
        doReturn(revokedAppsList).when(mAppSearchConsentManagerMock).getAppsWithRevokedConsent();

        ImmutableList<App> knownAppsWithConsent = mConsentManager.getKnownAppsWithConsent();
        ImmutableList<App> appsWithRevokedConsent = mConsentManager.getAppsWithRevokedConsent();

        verify(mAppSearchConsentManagerMock).getKnownAppsWithConsent();
        verify(mAppSearchConsentManagerMock).getAppsWithRevokedConsent();

        // Correct apps have received consent.
        assertThat(knownAppsWithConsent).hasSize(1);
        assertThat(knownAppsWithConsent.get(0).getPackageName())
                .isEqualTo(AppConsentDaoFixture.APP10_PACKAGE_NAME);
        assertThat(appsWithRevokedConsent).hasSize(2);
        assertThat(
                        appsWithRevokedConsent.stream()
                                .map(app -> app.getPackageName())
                                .collect(Collectors.toList()))
                .containsAtLeast(
                        AppConsentDaoFixture.APP20_PACKAGE_NAME,
                        AppConsentDaoFixture.APP30_PACKAGE_NAME);
    }

    @Test
    public void testGetKnownAppsWithConsentAfterConsentForOneOfThemWasRevoked_ppApiOnly()
            throws Exception {
        doNothing()
                .when(mCustomAudienceDaoMock)
                .deleteCustomAudienceDataByOwner(any(), anyBoolean());

        mConsentManager.enable(mSpyContext);
        assertTrue(mConsentManager.getConsent().isGiven());

        verify(() -> UiStatsLogger.logOptInSelected());

        mockGetPackageUid(AppConsentDaoFixture.APP10_PACKAGE_NAME, AppConsentDaoFixture.APP10_UID);
        mockGetPackageUid(AppConsentDaoFixture.APP20_PACKAGE_NAME, AppConsentDaoFixture.APP20_UID);
        mockGetPackageUid(AppConsentDaoFixture.APP30_PACKAGE_NAME, AppConsentDaoFixture.APP30_UID);

        mDatastore.putBoolean(AppConsentDaoFixture.APP10_DATASTORE_KEY, false);
        mDatastore.putBoolean(AppConsentDaoFixture.APP20_DATASTORE_KEY, false);
        mDatastore.putBoolean(AppConsentDaoFixture.APP30_DATASTORE_KEY, false);
        List<ApplicationInfo> applicationsInstalled =
                createApplicationInfos(
                        AppConsentDaoFixture.APP10_PACKAGE_NAME,
                        AppConsentDaoFixture.APP20_PACKAGE_NAME,
                        AppConsentDaoFixture.APP30_PACKAGE_NAME);
        mockInstalledApplications(applicationsInstalled);

        App app = App.create(AppConsentDaoFixture.APP10_PACKAGE_NAME);

        // revoke consent for first app
        mConsentManager.revokeConsentForApp(app);
        ImmutableList<App> knownAppsWithConsent = mConsentManager.getKnownAppsWithConsent();
        ImmutableList<App> appsWithRevokedConsent = mConsentManager.getAppsWithRevokedConsent();

        // all apps have received a consent
        assertThat(knownAppsWithConsent).hasSize(2);
        assertThat(appsWithRevokedConsent).hasSize(1);
        App appWithRevokedConsent = appsWithRevokedConsent.get(0);
        assertThat(appWithRevokedConsent.getPackageName()).isEqualTo(app.getPackageName());

        SystemClock.sleep(1000);
        verify(mCustomAudienceDaoMock)
                .deleteCustomAudienceDataByOwner(
                        app.getPackageName(), /* scheduleCustomAudienceEnabled= */ true);
        verify(mAppInstallDaoMock).deleteByPackageName(app.getPackageName());
        verify(mProtectedSignalsDaoMock)
                .deleteSignalsByPackage(eq(Collections.singletonList(app.getPackageName())));
        verify(mFrequencyCapDaoMock).deleteHistogramDataBySourceApp(app.getPackageName());
    }

    @Test
    public void testGetKnownAppsWithConsentAfterConsentForOneOfThemWasRevokedAndRestored_ppApiOnly()
            throws Exception {
        doNothing()
                .when(mCustomAudienceDaoMock)
                .deleteCustomAudienceDataByOwner(any(), anyBoolean());

        mConsentManager.enable(mSpyContext);
        assertTrue(mConsentManager.getConsent().isGiven());

        verify(() -> UiStatsLogger.logOptInSelected());

        mockGetPackageUid(AppConsentDaoFixture.APP10_PACKAGE_NAME, AppConsentDaoFixture.APP10_UID);
        mockGetPackageUid(AppConsentDaoFixture.APP20_PACKAGE_NAME, AppConsentDaoFixture.APP20_UID);
        mockGetPackageUid(AppConsentDaoFixture.APP30_PACKAGE_NAME, AppConsentDaoFixture.APP30_UID);

        mDatastore.putBoolean(AppConsentDaoFixture.APP10_DATASTORE_KEY, false);
        mDatastore.putBoolean(AppConsentDaoFixture.APP20_DATASTORE_KEY, false);
        mDatastore.putBoolean(AppConsentDaoFixture.APP30_DATASTORE_KEY, false);
        App app = App.create(AppConsentDaoFixture.APP10_PACKAGE_NAME);
        List<ApplicationInfo> applicationsInstalled =
                createApplicationInfos(
                        AppConsentDaoFixture.APP10_PACKAGE_NAME,
                        AppConsentDaoFixture.APP20_PACKAGE_NAME,
                        AppConsentDaoFixture.APP30_PACKAGE_NAME);
        mockInstalledApplications(applicationsInstalled);

        // revoke consent for first app
        mConsentManager.revokeConsentForApp(app);
        ImmutableList<App> knownAppsWithConsent = mConsentManager.getKnownAppsWithConsent();
        ImmutableList<App> appsWithRevokedConsent = mConsentManager.getAppsWithRevokedConsent();

        // all apps have received a consent
        assertThat(knownAppsWithConsent).hasSize(2);
        assertThat(appsWithRevokedConsent).hasSize(1);
        App appWithRevokedConsent = appsWithRevokedConsent.get(0);
        assertThat(appWithRevokedConsent.getPackageName()).isEqualTo(app.getPackageName());

        SystemClock.sleep(1000);
        verify(mCustomAudienceDaoMock)
                .deleteCustomAudienceDataByOwner(
                        app.getPackageName(), /* scheduleCustomAudienceEnabled= */ true);
        verify(mAppInstallDaoMock).deleteByPackageName(app.getPackageName());
        verify(mProtectedSignalsDaoMock)
                .deleteSignalsByPackage(eq(Collections.singletonList(app.getPackageName())));
        verify(mFrequencyCapDaoMock).deleteHistogramDataBySourceApp(app.getPackageName());

        // restore consent for first app
        mConsentManager.restoreConsentForApp(app);
        knownAppsWithConsent = mConsentManager.getKnownAppsWithConsent();
        appsWithRevokedConsent = mConsentManager.getAppsWithRevokedConsent();

        // all apps have received a consent
        assertThat(knownAppsWithConsent).hasSize(3);
        assertThat(appsWithRevokedConsent).isEmpty();
    }

    @Test
    public void testSetConsentForApp_ppApiOnly() throws Exception {
        mConsentManager.enable(mSpyContext, AdServicesApiType.FLEDGE);
        assertTrue(mConsentManager.getConsent(AdServicesApiType.FLEDGE).isGiven());

        verify(() -> UiStatsLogger.logOptInSelected(AdServicesApiType.FLEDGE));

        mockGetPackageUid(AppConsentDaoFixture.APP10_PACKAGE_NAME, AppConsentDaoFixture.APP10_UID);

        mConsentManager.revokeConsentForApp(App.create(AppConsentDaoFixture.APP10_PACKAGE_NAME));
        assertTrue(
                mConsentManager.isFledgeConsentRevokedForApp(
                        AppConsentDaoFixture.APP10_PACKAGE_NAME));

        mConsentManager.restoreConsentForApp(App.create(AppConsentDaoFixture.APP10_PACKAGE_NAME));
        assertFalse(
                mConsentManager.isFledgeConsentRevokedForApp(
                        AppConsentDaoFixture.APP10_PACKAGE_NAME));

        // TODO (b/274035157): The process crashes with a ClassNotFound exception in static mocking
        // occasionally. Need to add a Thread.sleep to prevent this crash.
        Thread.sleep(250);
    }

    @Test
    public void testSetConsentForApp_systemServerOnly() throws Exception {
        mConsentManager = getConsentManagerByConsentSourceOfTruth(Flags.SYSTEM_SERVER_ONLY);
        doReturn(ConsentParcel.createGivenConsent(ConsentParcel.FLEDGE))
                .when(mMockIAdServicesManager)
                .getConsent(ConsentParcel.FLEDGE);
        assertTrue(mConsentManager.getConsent(AdServicesApiType.FLEDGE).isGiven());

        mockGetPackageUid(AppConsentDaoFixture.APP10_PACKAGE_NAME, AppConsentDaoFixture.APP10_UID);

        mConsentManager.revokeConsentForApp(App.create(AppConsentDaoFixture.APP10_PACKAGE_NAME));
        verify(mMockIAdServicesManager)
                .setConsentForApp(
                        AppConsentDaoFixture.APP10_PACKAGE_NAME,
                        AppConsentDaoFixture.APP10_UID,
                        true);

        mConsentManager.restoreConsentForApp(App.create(AppConsentDaoFixture.APP10_PACKAGE_NAME));
        verify(mMockIAdServicesManager)
                .setConsentForApp(
                        AppConsentDaoFixture.APP10_PACKAGE_NAME,
                        AppConsentDaoFixture.APP10_UID,
                        false);

        // TODO (b/274035157): The process crashes with a ClassNotFound exception in static mocking
        // occasionally. Need to add a Thread.sleep to prevent this crash.
        Thread.sleep(250);
    }

    @Test
    public void testSetConsentForApp_ppApiAndSystemServer() throws Exception {
        mConsentManager = getConsentManagerByConsentSourceOfTruth(Flags.PPAPI_AND_SYSTEM_SERVER);
        doReturn(ConsentParcel.createGivenConsent(ConsentParcel.FLEDGE))
                .when(mMockIAdServicesManager)
                .getConsent(ConsentParcel.FLEDGE);
        assertTrue(mConsentManager.getConsent(AdServicesApiType.FLEDGE).isGiven());

        mockGetPackageUid(AppConsentDaoFixture.APP10_PACKAGE_NAME, AppConsentDaoFixture.APP10_UID);

        mConsentManager.revokeConsentForApp(App.create(AppConsentDaoFixture.APP10_PACKAGE_NAME));
        verify(mMockIAdServicesManager)
                .setConsentForApp(
                        AppConsentDaoFixture.APP10_PACKAGE_NAME,
                        AppConsentDaoFixture.APP10_UID,
                        true);
        assertEquals(Boolean.TRUE, mDatastore.getBoolean(AppConsentDaoFixture.APP10_DATASTORE_KEY));

        mConsentManager.restoreConsentForApp(App.create(AppConsentDaoFixture.APP10_PACKAGE_NAME));
        verify(mMockIAdServicesManager)
                .setConsentForApp(
                        AppConsentDaoFixture.APP10_PACKAGE_NAME,
                        AppConsentDaoFixture.APP10_UID,
                        false);
        assertEquals(
                Boolean.FALSE, mDatastore.getBoolean(AppConsentDaoFixture.APP10_DATASTORE_KEY));

        // TODO (b/274035157): The process crashes with a ClassNotFound exception in static mocking
        // occasionally. Need to add a Thread.sleep to prevent this crash.
        Thread.sleep(250);
    }

    @Test
    public void testSetConsentForApp_appSearchOnly() throws Exception {
        mConsentManager = getConsentManagerByConsentSourceOfTruth(Flags.APPSEARCH_ONLY);
        when(mMockFlags.getEnableAppsearchConsentData()).thenReturn(true);
        mockGetPackageUid(AppConsentDaoFixture.APP10_PACKAGE_NAME, AppConsentDaoFixture.APP10_UID);

        App app = App.create(AppConsentDaoFixture.APP10_PACKAGE_NAME);
        mConsentManager.revokeConsentForApp(app);
        verify(mAppSearchConsentManagerMock).revokeConsentForApp(app);

        mConsentManager.restoreConsentForApp(app);
        verify(mAppSearchConsentManagerMock).restoreConsentForApp(app);

        // TODO (b/274035157): The process crashes with a ClassNotFound exception in static mocking
        // occasionally. Need to add a Thread.sleep to prevent this crash.
        Thread.sleep(250);
    }

    @Test
    public void clearConsentForUninstalledApp_ppApiOnly() throws Exception {
        mockGetPackageUid(AppConsentDaoFixture.APP10_PACKAGE_NAME, AppConsentDaoFixture.APP10_UID);

        mConsentManager.restoreConsentForApp(App.create(AppConsentDaoFixture.APP10_PACKAGE_NAME));
        assertEquals(
                Boolean.FALSE, mDatastore.getBoolean(AppConsentDaoFixture.APP10_DATASTORE_KEY));
        mConsentManager.clearConsentForUninstalledApp(
                AppConsentDaoFixture.APP10_PACKAGE_NAME, AppConsentDaoFixture.APP10_UID);
        assertNull(mDatastore.getBoolean(AppConsentDaoFixture.APP10_DATASTORE_KEY));
    }

    @Test
    public void clearConsentForUninstalledApp_systemServerOnly() throws Exception {
        mConsentManager = getConsentManagerByConsentSourceOfTruth(Flags.SYSTEM_SERVER_ONLY);
        mConsentManager.clearConsentForUninstalledApp(
                AppConsentDaoFixture.APP10_PACKAGE_NAME, AppConsentDaoFixture.APP10_UID);
        verify(mMockIAdServicesManager)
                .clearConsentForUninstalledApp(
                        AppConsentDaoFixture.APP10_PACKAGE_NAME, AppConsentDaoFixture.APP10_UID);
    }

    @Test
    public void clearConsentForUninstalledApp_ppApiAndSystemServer() throws Exception {
        mConsentManager = getConsentManagerByConsentSourceOfTruth(Flags.PPAPI_AND_SYSTEM_SERVER);
        mockGetPackageUid(AppConsentDaoFixture.APP10_PACKAGE_NAME, AppConsentDaoFixture.APP10_UID);

        mConsentManager.restoreConsentForApp(App.create(AppConsentDaoFixture.APP10_PACKAGE_NAME));
        assertEquals(
                Boolean.FALSE, mDatastore.getBoolean(AppConsentDaoFixture.APP10_DATASTORE_KEY));
        verify(mMockIAdServicesManager)
                .setConsentForApp(
                        AppConsentDaoFixture.APP10_PACKAGE_NAME,
                        AppConsentDaoFixture.APP10_UID,
                        false);
        mConsentManager.clearConsentForUninstalledApp(
                AppConsentDaoFixture.APP10_PACKAGE_NAME, AppConsentDaoFixture.APP10_UID);
        assertNull(mDatastore.getBoolean(AppConsentDaoFixture.APP10_DATASTORE_KEY));
        verify(mMockIAdServicesManager)
                .clearConsentForUninstalledApp(
                        AppConsentDaoFixture.APP10_PACKAGE_NAME, AppConsentDaoFixture.APP10_UID);
    }

    @Test
    public void clearConsentForUninstalledApp_appSearchOnly() throws Exception {
        mConsentManager = getConsentManagerByConsentSourceOfTruth(Flags.APPSEARCH_ONLY);
        when(mMockFlags.getEnableAppsearchConsentData()).thenReturn(true);
        String packageName = AppConsentDaoFixture.APP10_PACKAGE_NAME;
        mockGetPackageUid(packageName, AppConsentDaoFixture.APP10_UID);

        mConsentManager.clearConsentForUninstalledApp(packageName, AppConsentDaoFixture.APP10_UID);
        verify(mAppSearchConsentManagerMock).clearConsentForUninstalledApp(packageName);
    }

    @Test
    public void clearConsentForUninstalledAppWithoutUid_ppApiOnly() throws Exception {
        mDatastore.putBoolean(AppConsentDaoFixture.APP10_DATASTORE_KEY, true);
        mDatastore.putBoolean(AppConsentDaoFixture.APP20_DATASTORE_KEY, true);
        mDatastore.putBoolean(AppConsentDaoFixture.APP30_DATASTORE_KEY, false);

        mConsentManager.clearConsentForUninstalledApp(AppConsentDaoFixture.APP20_PACKAGE_NAME);

        assertEquals(true, mDatastore.getBoolean(AppConsentDaoFixture.APP10_DATASTORE_KEY));
        assertNull(mDatastore.getBoolean(AppConsentDaoFixture.APP20_DATASTORE_KEY));
        assertEquals(false, mDatastore.getBoolean(AppConsentDaoFixture.APP30_DATASTORE_KEY));

        verify(mAppConsentDaoSpy).clearConsentForUninstalledApp(anyString());
    }

    @Test
    public void clearConsentForUninstalledAppWithoutUid_ppApiOnly_validatesInput() {
        assertThrows(
                NullPointerException.class,
                () -> {
                    mConsentManager.clearConsentForUninstalledApp(null);
                });
        assertThrows(
                IllegalArgumentException.class,
                () -> {
                    mConsentManager.clearConsentForUninstalledApp("");
                });
    }

    @Test
    public void testGetKnownTopicsWithConsent() {
        long taxonomyVersion = 1L;
        long modelVersion = 1L;
        Topic topic1 = Topic.create(1, taxonomyVersion, modelVersion);
        Topic topic2 = Topic.create(2, taxonomyVersion, modelVersion);
        ImmutableList<Topic> expectedKnownTopicsWithConsent = ImmutableList.of(topic1, topic2);
        doReturn(expectedKnownTopicsWithConsent)
                .when(mTopicsWorkerMock)
                .getKnownTopicsWithConsent();

        ImmutableList<Topic> knownTopicsWithConsent = mConsentManager.getKnownTopicsWithConsent();

        assertThat(knownTopicsWithConsent)
                .containsExactlyElementsIn(expectedKnownTopicsWithConsent);
    }

    @Test
    public void testGetTopicsWithRevokedConsent() {
        long taxonomyVersion = 1L;
        long modelVersion = 1L;
        Topic topic1 = Topic.create(1, taxonomyVersion, modelVersion);
        Topic topic2 = Topic.create(2, taxonomyVersion, modelVersion);
        ImmutableList<Topic> expectedTopicsWithRevokedConsent = ImmutableList.of(topic1, topic2);
        doReturn(expectedTopicsWithRevokedConsent)
                .when(mTopicsWorkerMock)
                .getTopicsWithRevokedConsent();

        ImmutableList<Topic> topicsWithRevokedConsent =
                mConsentManager.getTopicsWithRevokedConsent();

        assertThat(topicsWithRevokedConsent)
                .containsExactlyElementsIn(expectedTopicsWithRevokedConsent);
    }

    @Test
    public void testResetAllAppConsentAndAppData_ppApiOnly() throws Exception {
        doNothing()
                .when(mCustomAudienceDaoMock)
                .deleteAllCustomAudienceData(/* scheduleCustomAudienceEnabled= */ true);

        // Prepopulate with consent data for some apps
        mConsentManager.enable(mSpyContext);
        assertTrue(mConsentManager.getConsent().isGiven());

        verify(() -> UiStatsLogger.logOptInSelected());

        mockGetPackageUid(AppConsentDaoFixture.APP10_PACKAGE_NAME, AppConsentDaoFixture.APP10_UID);
        mockGetPackageUid(AppConsentDaoFixture.APP20_PACKAGE_NAME, AppConsentDaoFixture.APP20_UID);
        mockGetPackageUid(AppConsentDaoFixture.APP30_PACKAGE_NAME, AppConsentDaoFixture.APP30_UID);

        mDatastore.putBoolean(AppConsentDaoFixture.APP10_DATASTORE_KEY, false);
        mDatastore.putBoolean(AppConsentDaoFixture.APP20_DATASTORE_KEY, true);
        mDatastore.putBoolean(AppConsentDaoFixture.APP30_DATASTORE_KEY, false);
        List<ApplicationInfo> applicationsInstalled =
                createApplicationInfos(
                        AppConsentDaoFixture.APP10_PACKAGE_NAME,
                        AppConsentDaoFixture.APP20_PACKAGE_NAME,
                        AppConsentDaoFixture.APP30_PACKAGE_NAME);
        mockInstalledApplications(applicationsInstalled);

        // Verify population was successful
        ImmutableList<App> knownAppsWithConsent = mConsentManager.getKnownAppsWithConsent();
        ImmutableList<App> appsWithRevokedConsent = mConsentManager.getAppsWithRevokedConsent();
        assertThat(knownAppsWithConsent).hasSize(2);
        assertThat(appsWithRevokedConsent).hasSize(1);

        mConsentManager.resetAppsAndBlockedApps();

        // All app consent data was deleted
        knownAppsWithConsent = mConsentManager.getKnownAppsWithConsent();
        appsWithRevokedConsent = mConsentManager.getAppsWithRevokedConsent();
        assertThat(knownAppsWithConsent).isEmpty();
        assertThat(appsWithRevokedConsent).isEmpty();

        SystemClock.sleep(1000);
        verify(mCustomAudienceDaoMock, times(2))
                .deleteAllCustomAudienceData(/* scheduleCustomAudienceEnabled= */ true);
        verify(mAppInstallDaoMock, times(2)).deleteAllAppInstallData();
        verify(mProtectedSignalsDaoMock, times(2)).deleteAllSignals();
        verify(mFrequencyCapDaoMock, times(2)).deleteAllHistogramData();
        verify(mEncodedPayloadDaoMock, times(2)).deleteAllEncodedPayloads();
    }

    @Test
    public void testResetAllAppConsentAndAppData_systemServerOnly() throws Exception {
        doNothing()
                .when(mCustomAudienceDaoMock)
                .deleteAllCustomAudienceData(/* scheduleCustomAudienceEnabled= */ true);

        mConsentManager = getConsentManagerByConsentSourceOfTruth(Flags.SYSTEM_SERVER_ONLY);

        mConsentManager.resetAppsAndBlockedApps();

        verify(mMockIAdServicesManager).clearAllAppConsentData();

        SystemClock.sleep(1000);
        verify(mCustomAudienceDaoMock)
                .deleteAllCustomAudienceData(/* scheduleCustomAudienceEnabled= */ true);
        verify(mAppInstallDaoMock).deleteAllAppInstallData();
        verify(mProtectedSignalsDaoMock).deleteAllSignals();
        verify(mFrequencyCapDaoMock).deleteAllHistogramData();
        verify(mEncodedPayloadDaoMock).deleteAllEncodedPayloads();
    }

    @Test
    public void testResetAllAppConsentAndAppData_ppApiAndSystemServer() throws Exception {
        doNothing()
                .when(mCustomAudienceDaoMock)
                .deleteAllCustomAudienceData(/* scheduleCustomAudienceEnabled= */ true);

        // Prepopulate with consent data for some apps
        mConsentManager = getConsentManagerByConsentSourceOfTruth(Flags.PPAPI_AND_SYSTEM_SERVER);
        mConsentManager.enable(mSpyContext);

        verify(() -> UiStatsLogger.logOptInSelected());

        doReturn(ConsentParcel.createGivenConsent(ConsentParcel.ALL_API))
                .when(mMockIAdServicesManager)
                .getConsent(ConsentParcel.ALL_API);
        assertTrue(mConsentManager.getConsent().isGiven());

        mockGetPackageUid(AppConsentDaoFixture.APP10_PACKAGE_NAME, AppConsentDaoFixture.APP10_UID);
        mockGetPackageUid(AppConsentDaoFixture.APP20_PACKAGE_NAME, AppConsentDaoFixture.APP20_UID);
        mockGetPackageUid(AppConsentDaoFixture.APP30_PACKAGE_NAME, AppConsentDaoFixture.APP30_UID);

        mDatastore.putBoolean(AppConsentDaoFixture.APP10_DATASTORE_KEY, false);
        mDatastore.putBoolean(AppConsentDaoFixture.APP20_DATASTORE_KEY, true);
        mDatastore.putBoolean(AppConsentDaoFixture.APP30_DATASTORE_KEY, false);
        List<ApplicationInfo> applicationsInstalled =
                createApplicationInfos(
                        AppConsentDaoFixture.APP10_PACKAGE_NAME,
                        AppConsentDaoFixture.APP20_PACKAGE_NAME,
                        AppConsentDaoFixture.APP30_PACKAGE_NAME);
        mockInstalledApplications(applicationsInstalled);

        // Verify population was successful
        List<App> knownAppsWithConsent =
                mDatastore.keySetFalse().stream().map(App::create).collect(Collectors.toList());
        List<App> appsWithRevokedConsent =
                mDatastore.keySetTrue().stream().map(App::create).collect(Collectors.toList());
        assertThat(knownAppsWithConsent).hasSize(2);
        assertThat(appsWithRevokedConsent).hasSize(1);

        mConsentManager.resetAppsAndBlockedApps();

        // All app consent data was deleted
        knownAppsWithConsent =
                mDatastore.keySetFalse().stream().map(App::create).collect(Collectors.toList());
        appsWithRevokedConsent =
                mDatastore.keySetTrue().stream().map(App::create).collect(Collectors.toList());
        assertThat(knownAppsWithConsent).isEmpty();
        assertThat(appsWithRevokedConsent).isEmpty();

        verify(mMockIAdServicesManager, times(2)).clearAllAppConsentData();

        SystemClock.sleep(1000);
        verify(mCustomAudienceDaoMock, times(2))
                .deleteAllCustomAudienceData(/* scheduleCustomAudienceEnabled= */ true);
        verify(mAppInstallDaoMock, times(2)).deleteAllAppInstallData();
        verify(mProtectedSignalsDaoMock, times(2)).deleteAllSignals();
        verify(mFrequencyCapDaoMock, times(2)).deleteAllHistogramData();
        verify(mEncodedPayloadDaoMock, times(2)).deleteAllEncodedPayloads();
    }

    @Test
    public void testResetAllAppConsentAndAppData_appSearchOnly() throws Exception {
        mConsentManager = getConsentManagerByConsentSourceOfTruth(Flags.APPSEARCH_ONLY);
        when(mMockFlags.getEnableAppsearchConsentData()).thenReturn(true);

        mConsentManager.resetAppsAndBlockedApps();
        verify(mAppSearchConsentManagerMock).clearAllAppConsentData();
    }

    @Test
    public void testResetAllowedAppConsentAndAppData_ppApiOnly() throws Exception {
        doNothing()
                .when(mCustomAudienceDaoMock)
                .deleteAllCustomAudienceData(/* scheduleCustomAudienceEnabled= */ true);

        // Prepopulate with consent data for some apps
        mConsentManager.enable(mSpyContext);
        assertTrue(mConsentManager.getConsent().isGiven());

        verify(() -> UiStatsLogger.logOptInSelected());

        mockGetPackageUid(AppConsentDaoFixture.APP10_PACKAGE_NAME, AppConsentDaoFixture.APP10_UID);
        mockGetPackageUid(AppConsentDaoFixture.APP20_PACKAGE_NAME, AppConsentDaoFixture.APP20_UID);
        mockGetPackageUid(AppConsentDaoFixture.APP30_PACKAGE_NAME, AppConsentDaoFixture.APP30_UID);

        mDatastore.putBoolean(AppConsentDaoFixture.APP10_DATASTORE_KEY, false);
        mDatastore.putBoolean(AppConsentDaoFixture.APP20_DATASTORE_KEY, true);
        mDatastore.putBoolean(AppConsentDaoFixture.APP30_DATASTORE_KEY, false);
        List<ApplicationInfo> applicationsInstalled =
                createApplicationInfos(
                        AppConsentDaoFixture.APP10_PACKAGE_NAME,
                        AppConsentDaoFixture.APP20_PACKAGE_NAME,
                        AppConsentDaoFixture.APP30_PACKAGE_NAME);
        mockInstalledApplications(applicationsInstalled);

        // Verify population was successful
        ImmutableList<App> knownAppsWithConsentBeforeReset =
                mConsentManager.getKnownAppsWithConsent();
        ImmutableList<App> appsWithRevokedConsentBeforeReset =
                mConsentManager.getAppsWithRevokedConsent();
        assertThat(knownAppsWithConsentBeforeReset).hasSize(2);
        assertThat(appsWithRevokedConsentBeforeReset).hasSize(1);
        mConsentManager.resetApps();

        // Known apps with consent were cleared; revoked apps were not deleted
        ImmutableList<App> knownAppsWithConsentAfterReset =
                mConsentManager.getKnownAppsWithConsent();
        ImmutableList<App> appsWithRevokedConsentAfterReset =
                mConsentManager.getAppsWithRevokedConsent();
        assertThat(knownAppsWithConsentAfterReset).isEmpty();
        assertThat(appsWithRevokedConsentAfterReset).hasSize(1);
        assertThat(
                        appsWithRevokedConsentAfterReset.stream()
                                .map(App::getPackageName)
                                .collect(Collectors.toList()))
                .containsExactlyElementsIn(
                        appsWithRevokedConsentBeforeReset.stream()
                                .map(App::getPackageName)
                                .collect(Collectors.toList()));

        SystemClock.sleep(1000);
        verify(mCustomAudienceDaoMock, times(2))
                .deleteAllCustomAudienceData(/* scheduleCustomAudienceEnabled= */ true);
        verify(mAppInstallDaoMock, times(2)).deleteAllAppInstallData();
        verify(mProtectedSignalsDaoMock, times(2)).deleteAllSignals();
        verify(mFrequencyCapDaoMock, times(2)).deleteAllHistogramData();
        verify(mEncodedPayloadDaoMock, times(2)).deleteAllEncodedPayloads();
    }

    @Test
    public void testResetAllowedAppConsentAndAppData_systemServerOnly() throws Exception {
        doNothing()
                .when(mCustomAudienceDaoMock)
                .deleteAllCustomAudienceData(/* scheduleCustomAudienceEnabled= */ true);

        // Prepopulate with consent data for some apps
        mConsentManager = getConsentManagerByConsentSourceOfTruth(Flags.SYSTEM_SERVER_ONLY);
        mConsentManager.resetApps();

        verify(mMockIAdServicesManager).clearKnownAppsWithConsent();

        SystemClock.sleep(1000);
        verify(mCustomAudienceDaoMock)
                .deleteAllCustomAudienceData(/* scheduleCustomAudienceEnabled= */ true);
        verify(mAppInstallDaoMock).deleteAllAppInstallData();
        verify(mProtectedSignalsDaoMock).deleteAllSignals();
        verify(mFrequencyCapDaoMock).deleteAllHistogramData();
        verify(mEncodedPayloadDaoMock).deleteAllEncodedPayloads();
    }

    @Test
    public void testResetAllowedAppConsentAndAppData_ppApiAndSystemServer() throws Exception {
        doNothing()
                .when(mCustomAudienceDaoMock)
                .deleteAllCustomAudienceData(/* scheduleCustomAudienceEnabled= */ true);

        // Prepopulate with consent data for some apps
        mConsentManager = getConsentManagerByConsentSourceOfTruth(Flags.PPAPI_AND_SYSTEM_SERVER);
        doReturn(ConsentParcel.createGivenConsent(ConsentParcel.ALL_API))
                .when(mMockIAdServicesManager)
                .getConsent(ConsentParcel.ALL_API);
        assertTrue(mConsentManager.getConsent().isGiven());
        mockGetPackageUid(AppConsentDaoFixture.APP10_PACKAGE_NAME, AppConsentDaoFixture.APP10_UID);
        mockGetPackageUid(AppConsentDaoFixture.APP20_PACKAGE_NAME, AppConsentDaoFixture.APP20_UID);
        mockGetPackageUid(AppConsentDaoFixture.APP30_PACKAGE_NAME, AppConsentDaoFixture.APP30_UID);

        mDatastore.putBoolean(AppConsentDaoFixture.APP10_DATASTORE_KEY, false);
        mDatastore.putBoolean(AppConsentDaoFixture.APP20_DATASTORE_KEY, true);
        mDatastore.putBoolean(AppConsentDaoFixture.APP30_DATASTORE_KEY, false);
        List<ApplicationInfo> applicationsInstalled =
                createApplicationInfos(
                        AppConsentDaoFixture.APP10_PACKAGE_NAME,
                        AppConsentDaoFixture.APP20_PACKAGE_NAME,
                        AppConsentDaoFixture.APP30_PACKAGE_NAME);
        mockInstalledApplications(applicationsInstalled);

        // Verify population was successful
        List<App> knownAppsWithConsentBeforeReset =
                mDatastore.keySetFalse().stream().map(App::create).collect(Collectors.toList());
        List<App> appsWithRevokedConsentBeforeReset =
                mDatastore.keySetTrue().stream().map(App::create).collect(Collectors.toList());
        assertThat(knownAppsWithConsentBeforeReset).hasSize(2);
        assertThat(appsWithRevokedConsentBeforeReset).hasSize(1);
        mConsentManager.resetApps();

        // Known apps with consent were cleared; revoked apps were not deleted
        List<App> knownAppsWithConsentAfterReset =
                mDatastore.keySetFalse().stream().map(App::create).collect(Collectors.toList());
        List<App> appsWithRevokedConsentAfterReset =
                mDatastore.keySetTrue().stream().map(App::create).collect(Collectors.toList());
        assertThat(knownAppsWithConsentAfterReset).isEmpty();
        assertThat(appsWithRevokedConsentAfterReset).hasSize(1);
        assertThat(
                        appsWithRevokedConsentAfterReset.stream()
                                .map(App::getPackageName)
                                .collect(Collectors.toList()))
                .containsExactlyElementsIn(
                        appsWithRevokedConsentBeforeReset.stream()
                                .map(App::getPackageName)
                                .collect(Collectors.toList()));

        verify(mMockIAdServicesManager).clearKnownAppsWithConsent();

        SystemClock.sleep(1000);
        verify(mCustomAudienceDaoMock)
                .deleteAllCustomAudienceData(/* scheduleCustomAudienceEnabled= */ true);
        verify(mAppInstallDaoMock).deleteAllAppInstallData();
        verify(mProtectedSignalsDaoMock).deleteAllSignals();
        verify(mFrequencyCapDaoMock).deleteAllHistogramData();
        verify(mEncodedPayloadDaoMock).deleteAllEncodedPayloads();
    }

    @Test
    public void testResetAllowedAppConsentAndAppData_appSearchOnly() throws Exception {
        mConsentManager = getConsentManagerByConsentSourceOfTruth(Flags.APPSEARCH_ONLY);
        when(mMockFlags.getEnableAppsearchConsentData()).thenReturn(true);

        mConsentManager.resetApps();
        verify(mAppSearchConsentManagerMock).clearKnownAppsWithConsent();
    }

    @Test
    public void testNotificationDisplayedRecorded_PpApiOnly() throws Exception {
        int consentSourceOfTruth = Flags.PPAPI_ONLY;
        ConsentManager spyConsentManager =
                getSpiedConsentManagerForMigrationTesting(
                        /* isGiven */ false, consentSourceOfTruth);

        assertThat(spyConsentManager.wasNotificationDisplayed()).isFalse();

        verify(mMockIAdServicesManager, never()).wasNotificationDisplayed();

        spyConsentManager.recordNotificationDisplayed(true);

        assertThat(spyConsentManager.wasNotificationDisplayed()).isTrue();

        verify(mMockIAdServicesManager, never()).wasNotificationDisplayed();
        verify(mMockIAdServicesManager, never()).recordNotificationDisplayed(true);
    }

    @Test
    public void testNotificationDisplayedRecorded_SystemServerOnly() throws Exception {
        int consentSourceOfTruth = Flags.SYSTEM_SERVER_ONLY;
        ConsentManager spyConsentManager =
                getSpiedConsentManagerForMigrationTesting(
                        /* isGiven */ false, consentSourceOfTruth);

        assertThat(spyConsentManager.wasNotificationDisplayed()).isFalse();

        verify(mMockIAdServicesManager).wasNotificationDisplayed();

        doReturn(true).when(mMockIAdServicesManager).wasNotificationDisplayed();
        spyConsentManager.recordNotificationDisplayed(true);

        assertThat(spyConsentManager.wasNotificationDisplayed()).isTrue();

        verify(mMockIAdServicesManager, times(2)).wasNotificationDisplayed();
        verify(mMockIAdServicesManager).recordNotificationDisplayed(true);

        // Verify notificationDisplayed is not set in PPAPI
        assertThat(mConsentDatastore.getBoolean(NOTIFICATION_DISPLAYED_ONCE)).isFalse();
    }

    @Test
    public void testNotificationDisplayedRecorded_PpApiAndSystemServer() throws Exception {
        int consentSourceOfTruth = Flags.PPAPI_AND_SYSTEM_SERVER;
        ConsentManager spyConsentManager =
                getSpiedConsentManagerForMigrationTesting(
                        /* isGiven */ false, consentSourceOfTruth);

        Boolean wasNotificationDisplayed = spyConsentManager.wasNotificationDisplayed();

        assertThat(wasNotificationDisplayed).isFalse();

        verify(mMockIAdServicesManager).wasNotificationDisplayed();

        doReturn(true).when(mMockIAdServicesManager).wasNotificationDisplayed();
        spyConsentManager.recordNotificationDisplayed(true);

        assertThat(spyConsentManager.wasNotificationDisplayed()).isTrue();

        verify(mMockIAdServicesManager, times(2)).wasNotificationDisplayed();
        verify(mMockIAdServicesManager).recordNotificationDisplayed(true);

        // Verify notificationDisplayed is also set in PPAPI
        assertThat(mConsentDatastore.getBoolean(NOTIFICATION_DISPLAYED_ONCE)).isTrue();
    }

    @Test
    public void testNotificationDisplayedRecorded_appSearchOnly() throws Exception {
        int consentSourceOfTruth = Flags.APPSEARCH_ONLY;
        when(mMockFlags.getEnableAppsearchConsentData()).thenReturn(true);
        ConsentManager spyConsentManager =
                getSpiedConsentManagerForMigrationTesting(
                        /* isGiven */ false, consentSourceOfTruth);

        doReturn(false).when(mAppSearchConsentManagerMock).wasNotificationDisplayed();
        assertThat(spyConsentManager.wasNotificationDisplayed()).isFalse();
        verify(mAppSearchConsentManagerMock).wasNotificationDisplayed();

        doReturn(true).when(mAppSearchConsentManagerMock).wasNotificationDisplayed();
        spyConsentManager.recordNotificationDisplayed(true);

        assertThat(spyConsentManager.wasNotificationDisplayed()).isTrue();

        verify(mAppSearchConsentManagerMock, times(2)).wasNotificationDisplayed();
        verify(mAppSearchConsentManagerMock).recordNotificationDisplayed(true);
    }

    @Test
    public void testGaUxNotificationDisplayedRecorded_PpApiOnly() throws Exception {
        int consentSourceOfTruth = Flags.PPAPI_ONLY;
        ConsentManager spyConsentManager =
                getSpiedConsentManagerForMigrationTesting(
                        /* isGiven */ false, consentSourceOfTruth);

        assertThat(spyConsentManager.wasGaUxNotificationDisplayed()).isFalse();

        verify(mMockIAdServicesManager, never()).wasGaUxNotificationDisplayed();

        spyConsentManager.recordGaUxNotificationDisplayed(true);

        assertThat(spyConsentManager.wasGaUxNotificationDisplayed()).isTrue();

        verify(mMockIAdServicesManager, never()).wasGaUxNotificationDisplayed();
        verify(mMockIAdServicesManager, never()).recordGaUxNotificationDisplayed(true);
    }

    @Test
    public void testGaUxNotificationDisplayedRecorded_SystemServerOnly() throws Exception {
        int consentSourceOfTruth = Flags.SYSTEM_SERVER_ONLY;
        ConsentManager spyConsentManager =
                getSpiedConsentManagerForMigrationTesting(
                        /* isGiven */ false, consentSourceOfTruth);

        assertThat(spyConsentManager.wasGaUxNotificationDisplayed()).isFalse();

        verify(mMockIAdServicesManager).wasGaUxNotificationDisplayed();

        doReturn(true).when(mMockIAdServicesManager).wasGaUxNotificationDisplayed();
        spyConsentManager.recordGaUxNotificationDisplayed(true);

        assertThat(spyConsentManager.wasGaUxNotificationDisplayed()).isTrue();

        verify(mMockIAdServicesManager, times(2)).wasGaUxNotificationDisplayed();
        verify(mMockIAdServicesManager).recordGaUxNotificationDisplayed(true);

        // Verify notificationDisplayed is not set in PPAPI
        assertThat(mConsentDatastore.getBoolean(GA_UX_NOTIFICATION_DISPLAYED_ONCE)).isFalse();
    }

    @Test
    public void testGaUxNotificationDisplayedRecorded_PpApiAndSystemServer() throws Exception {
        int consentSourceOfTruth = Flags.PPAPI_AND_SYSTEM_SERVER;
        ConsentManager spyConsentManager =
                getSpiedConsentManagerForMigrationTesting(
                        /* isGiven */ false, consentSourceOfTruth);

        Boolean wasGaUxNotificationDisplayed = spyConsentManager.wasGaUxNotificationDisplayed();

        assertThat(wasGaUxNotificationDisplayed).isFalse();

        verify(mMockIAdServicesManager).wasGaUxNotificationDisplayed();

        doReturn(true).when(mMockIAdServicesManager).wasGaUxNotificationDisplayed();
        spyConsentManager.recordGaUxNotificationDisplayed(true);

        assertThat(spyConsentManager.wasGaUxNotificationDisplayed()).isTrue();

        verify(mMockIAdServicesManager, times(2)).wasGaUxNotificationDisplayed();
        verify(mMockIAdServicesManager).recordGaUxNotificationDisplayed(true);

        // Verify notificationDisplayed is also set in PPAPI
        assertThat(mConsentDatastore.getBoolean(GA_UX_NOTIFICATION_DISPLAYED_ONCE)).isTrue();
    }

    @Test
    public void testGaUxNotificationDisplayedRecorded_appSearchOnly() throws Exception {
        int consentSourceOfTruth = Flags.APPSEARCH_ONLY;
        when(mMockFlags.getEnableAppsearchConsentData()).thenReturn(true);
        ConsentManager spyConsentManager =
                getSpiedConsentManagerForMigrationTesting(
                        /* isGiven */ false, consentSourceOfTruth);

        when(mAppSearchConsentManagerMock.wasGaUxNotificationDisplayed()).thenReturn(false);
        assertThat(spyConsentManager.wasGaUxNotificationDisplayed()).isFalse();
        verify(mAppSearchConsentManagerMock).wasGaUxNotificationDisplayed();

        when(mAppSearchConsentManagerMock.wasGaUxNotificationDisplayed()).thenReturn(true);
        spyConsentManager.recordGaUxNotificationDisplayed(true);
        assertThat(spyConsentManager.wasGaUxNotificationDisplayed()).isTrue();

        verify(mAppSearchConsentManagerMock, times(2)).wasGaUxNotificationDisplayed();
        verify(mAppSearchConsentManagerMock).recordGaUxNotificationDisplayed(true);
    }

    @Test
    public void testNotificationDisplayedRecorded_notSupportedFlag() throws Exception {
        int invalidConsentSourceOfTruth = 5;
        ConsentManager spyConsentManager =
                getSpiedConsentManagerForMigrationTesting(
                        /* isGiven */ false, invalidConsentSourceOfTruth);

        assertThrows(
                RuntimeException.class, () -> spyConsentManager.recordNotificationDisplayed(true));
    }

    @Test
    public void testClearPpApiConsent() throws Exception {
        mConsentDatastore.putBoolean(CONSENT_KEY, true);
        mConsentDatastore.putBoolean(NOTIFICATION_DISPLAYED_ONCE, true);
        assertThat(mConsentDatastore.getBoolean(CONSENT_KEY)).isTrue();
        assertThat(mConsentDatastore.getBoolean(NOTIFICATION_DISPLAYED_ONCE)).isTrue();

        ConsentManager.clearPpApiConsent(mSpyContext, mConsentDatastore);
        assertThat(mConsentDatastore.getBoolean(CONSENT_KEY)).isNull();
        assertThat(mConsentDatastore.getBoolean(NOTIFICATION_DISPLAYED_ONCE)).isNull();

        // Verify this should only happen once
        mConsentDatastore.putBoolean(CONSENT_KEY, true);
        mConsentDatastore.putBoolean(NOTIFICATION_DISPLAYED_ONCE, true);
        assertThat(mConsentDatastore.getBoolean(CONSENT_KEY)).isTrue();
        assertThat(mConsentDatastore.getBoolean(NOTIFICATION_DISPLAYED_ONCE)).isTrue();
        // Consent is not cleared again
        ConsentManager.clearPpApiConsent(mSpyContext, mConsentDatastore);
        assertThat(mConsentDatastore.getBoolean(CONSENT_KEY)).isTrue();
        assertThat(mConsentDatastore.getBoolean(NOTIFICATION_DISPLAYED_ONCE)).isTrue();

        // Clear shared preference
        ConsentManager.resetSharedPreference(mSpyContext, SHARED_PREFS_KEY_PPAPI_HAS_CLEARED);
    }

    @Test
    public void testMigratePpApiConsentToSystemService() throws Exception {
        // Disable IPC calls
        doNothing().when(mMockIAdServicesManager).setConsent(any());
        doNothing().when(mMockIAdServicesManager).recordNotificationDisplayed(true);

        mConsentDatastore.putBoolean(CONSENT_KEY, true);
        mConsentDatastore.putBoolean(NOTIFICATION_DISPLAYED_ONCE, true);
        assertThat(mConsentDatastore.getBoolean(CONSENT_KEY)).isTrue();
        assertThat(mConsentDatastore.getBoolean(NOTIFICATION_DISPLAYED_ONCE)).isTrue();

        ConsentManager.migratePpApiConsentToSystemService(
                mSpyContext, mConsentDatastore, mAdServicesManager, mStatsdAdServicesLoggerMock);

        verify(mMockIAdServicesManager).setConsent(any());
        verify(mMockIAdServicesManager).recordNotificationDisplayed(true);

        // Verify this should only happen once
        ConsentManager.migratePpApiConsentToSystemService(
                mSpyContext, mConsentDatastore, mAdServicesManager, mStatsdAdServicesLoggerMock);
        verify(mMockIAdServicesManager).setConsent(any());
        verify(mMockIAdServicesManager).recordNotificationDisplayed(true);

        // Clear shared preference
        ConsentManager.resetSharedPreference(mSpyContext, SHARED_PREFS_KEY_HAS_MIGRATED);
    }

    @Test
    public void testMigratePpApiConsentToSystemServiceWithSuccessfulConsentMigrationLogging()
            throws Exception {
        // Disable IPC calls
        doNothing().when(mMockIAdServicesManager).setConsent(any());
        doNothing().when(mMockIAdServicesManager).recordNotificationDisplayed(true);
        mConsentDatastore.putBoolean(CONSENT_KEY, true);
        mConsentDatastore.putBoolean(NOTIFICATION_DISPLAYED_ONCE, true);
        assertThat(mConsentDatastore.getBoolean(CONSENT_KEY)).isTrue();
        assertThat(mConsentDatastore.getBoolean(NOTIFICATION_DISPLAYED_ONCE)).isTrue();

        SharedPreferences sharedPreferences =
                mSpyContext.getSharedPreferences(SHARED_PREFS_CONSENT, Context.MODE_PRIVATE);
        SharedPreferences.Editor editor = sharedPreferences.edit();

        editor.putBoolean(SHARED_PREFS_KEY_HAS_MIGRATED, false);
        editor.putBoolean(SHARED_PREFS_KEY_APPSEARCH_HAS_MIGRATED, false);
        editor.commit();
        ExtendedMockito.doReturn(false).when(() -> DeviceRegionProvider.isEuDevice(any()));

        ConsentManager.migratePpApiConsentToSystemService(
                mSpyContext, mConsentDatastore, mAdServicesManager, mStatsdAdServicesLoggerMock);

        ConsentMigrationStats consentMigrationStats =
                ConsentMigrationStats.builder()
                        .setTopicsConsent(true)
                        .setFledgeConsent(true)
                        .setMsmtConsent(true)
                        .setMigrationStatus(
                                ConsentMigrationStats.MigrationStatus
                                        .SUCCESS_WITH_SHARED_PREF_UPDATED)
                        .setMigrationType(
                                ConsentMigrationStats.MigrationType.PPAPI_TO_SYSTEM_SERVICE)
                        .setRegion(2)
                        .build();

        verify(mStatsdAdServicesLoggerMock).logConsentMigrationStats(consentMigrationStats);

        // Clear shared preference
        ConsentManager.resetSharedPreference(mSpyContext, SHARED_PREFS_KEY_HAS_MIGRATED);
        ConsentManager.resetSharedPreference(mSpyContext, SHARED_PREFS_KEY_APPSEARCH_HAS_MIGRATED);
    }

    @Test
    @ExpectErrorLogUtilCall(
            errorCode = AD_SERVICES_ERROR_REPORTED__ERROR_CODE__SHARED_PREF_UPDATE_FAILURE)
    public void testMigratePpApiConsentToSystemServiceWithUnSuccessfulConsentMigrationLogging()
            throws Exception {
        // Disable IPC calls
        doNothing().when(mMockIAdServicesManager).setConsent(any());
        doNothing().when(mMockIAdServicesManager).recordNotificationDisplayed(true);
        mConsentDatastore.putBoolean(CONSENT_KEY, true);
        mConsentDatastore.putBoolean(NOTIFICATION_DISPLAYED_ONCE, true);

        SharedPreferences sharedPreferences = mock(SharedPreferences.class);
        SharedPreferences.Editor editor = mock(SharedPreferences.Editor.class);
        doReturn(editor).when(sharedPreferences).edit();
        doReturn(false).when(editor).commit();
        doReturn(sharedPreferences).when(mSpyContext).getSharedPreferences(anyString(), anyInt());

        doNothing().when(mStatsdAdServicesLoggerMock).logConsentMigrationStats(any());
        ExtendedMockito.doReturn(false).when(() -> DeviceRegionProvider.isEuDevice(any()));

        ConsentManager.migratePpApiConsentToSystemService(
                mSpyContext, mConsentDatastore, mAdServicesManager, mStatsdAdServicesLoggerMock);

        ConsentMigrationStats consentMigrationStats =
                ConsentMigrationStats.builder()
                        .setTopicsConsent(true)
                        .setFledgeConsent(true)
                        .setMsmtConsent(true)
                        .setMigrationStatus(
                                ConsentMigrationStats.MigrationStatus
                                        .SUCCESS_WITH_SHARED_PREF_NOT_UPDATED)
                        .setMigrationType(
                                ConsentMigrationStats.MigrationType.PPAPI_TO_SYSTEM_SERVICE)
                        .setRegion(2)
                        .build();

        verify(mStatsdAdServicesLoggerMock).logConsentMigrationStats(consentMigrationStats);

        doReturn(true).when(editor).commit();
        // Clear shared preference
        ConsentManager.resetSharedPreference(mSpyContext, SHARED_PREFS_KEY_HAS_MIGRATED);
    }

    @Test
    public void testMigratePpApiConsentToSystemServiceThrowsException() throws Exception {
        mConsentDatastore.putBoolean(NOTIFICATION_DISPLAYED_ONCE, true);
        doThrow(RemoteException.class)
                .when(mMockIAdServicesManager)
                .recordNotificationDisplayed(true);

        doNothing().when(mStatsdAdServicesLoggerMock).logConsentMigrationStats(any());
        ExtendedMockito.doReturn(false).when(() -> DeviceRegionProvider.isEuDevice(any()));

        ConsentManager.migratePpApiConsentToSystemService(
                mSpyContext, mConsentDatastore, mAdServicesManager, mStatsdAdServicesLoggerMock);

        ConsentMigrationStats consentMigrationStats =
                ConsentMigrationStats.builder()
                        .setTopicsConsent(true)
                        .setFledgeConsent(true)
                        .setMsmtConsent(true)
                        .setMigrationStatus(ConsentMigrationStats.MigrationStatus.FAILURE)
                        .setMigrationType(
                                ConsentMigrationStats.MigrationType.PPAPI_TO_SYSTEM_SERVICE)
                        .setRegion(2)
                        .build();

        verify(mStatsdAdServicesLoggerMock).logConsentMigrationStats(consentMigrationStats);

        // Clear shared preference
        ConsentManager.resetSharedPreference(mSpyContext, SHARED_PREFS_KEY_HAS_MIGRATED);
    }

    @Test
    public void testHandleConsentMigrationIfNeeded_ExtServices() throws Exception {
        doReturn("com." + AdServicesCommon.ADEXTSERVICES_PACKAGE_NAME_SUFFIX)
                .when(mSpyContext)
                .getPackageName();

        ConsentManager.handleConsentMigrationIfNeeded(
                mSpyContext, mConsentDatastore, mAdServicesManager, mStatsdAdServicesLoggerMock, 2);

        verify(mSpyContext, never()).getSharedPreferences(anyString(), anyInt());
        verify(mMockIAdServicesManager, never()).setConsent(any());
    }

    @Test
    public void testHandleConsentMigrationFromAppSearchIfNeeded_ExtServices() throws Exception {
        doReturn("com." + AdServicesCommon.ADEXTSERVICES_PACKAGE_NAME_SUFFIX)
                .when(mSpyContext)
                .getPackageName();
        SharedPreferences mockSharedPrefs = mock(SharedPreferences.class);
        SharedPreferences.Editor mockEditor = mock(SharedPreferences.Editor.class);
        when(mockSharedPrefs.edit()).thenReturn(mockEditor);
        when(mSpyContext.getSharedPreferences(any(String.class), anyInt()))
                .thenReturn(mockSharedPrefs);
        ConsentManager.handleConsentMigrationFromAppSearchIfNeeded(
                mSpyContext,
                mDatastore,
                mAppConsentDaoSpy,
                mAppSearchConsentManagerMock,
                mAdServicesManager,
                mStatsdAdServicesLoggerMock);

        verify(mSpyContext, never()).getSharedPreferences(anyString(), anyInt());
        verify(mAppSearchConsentManagerMock, never())
                .migrateConsentDataIfNeeded(any(), any(), any(), any());
        verify(mMockIAdServicesManager, never()).setConsent(any());
        verify(mMockIAdServicesManager, never()).recordNotificationDisplayed(true);
        verify(mMockIAdServicesManager, never()).recordGaUxNotificationDisplayed(true);
        verify(mMockIAdServicesManager, never()).recordDefaultConsent(anyBoolean());
        verify(mMockIAdServicesManager, never()).recordAdServicesDeletionOccurred(anyInt());
        verify(mMockIAdServicesManager, never()).recordDefaultAdIdState(anyBoolean());
        verify(mMockIAdServicesManager, never()).recordFledgeDefaultConsent(anyBoolean());
        verify(mMockIAdServicesManager, never()).recordMeasurementDefaultConsent(anyBoolean());
        verify(mMockIAdServicesManager, never()).recordTopicsDefaultConsent(anyBoolean());
        verify(mMockIAdServicesManager, never()).recordUserManualInteractionWithConsent(anyInt());
        verify(mMockIAdServicesManager, never()).setModuleEnrollmentState(anyString());
        verify(mockEditor, never()).putBoolean(any(), anyBoolean());
    }

    @Test
    public void testResetSharedPreference() {
        SharedPreferences sharedPreferences =
                mSpyContext.getSharedPreferences(SHARED_PREFS_CONSENT, Context.MODE_PRIVATE);
        SharedPreferences.Editor editor = sharedPreferences.edit();

        editor.putBoolean(SHARED_PREFS_KEY_PPAPI_HAS_CLEARED, true);
        editor.putBoolean(SHARED_PREFS_KEY_HAS_MIGRATED, true);
        editor.commit();

        assertThat(
                        sharedPreferences.getBoolean(
                                SHARED_PREFS_KEY_PPAPI_HAS_CLEARED, /* defValue */ false))
                .isTrue();
        assertThat(
                        sharedPreferences.getBoolean(
                                SHARED_PREFS_KEY_HAS_MIGRATED, /* defValue */ false))
                .isTrue();

        resetSharedPreference(mSpyContext, SHARED_PREFS_KEY_PPAPI_HAS_CLEARED);
        resetSharedPreference(mSpyContext, SHARED_PREFS_KEY_HAS_MIGRATED);

        assertThat(sharedPreferences.getBoolean(SHARED_PREFS_KEY_HAS_MIGRATED, /* defValue */ true))
                .isFalse();
        assertThat(sharedPreferences.getBoolean(SHARED_PREFS_KEY_HAS_MIGRATED, /* defValue */ true))
                .isFalse();
    }

    @Test
    public void testHandleConsentMigrationIfNeeded_PpApiOnly() {
        // Disable actual execution of internal methods
        doNothing()
                .when(
                        () ->
                                ConsentManager.resetSharedPreference(
                                        mSpyContext, SHARED_PREFS_KEY_HAS_MIGRATED));
        doNothing()
                .when(
                        () ->
                                ConsentManager.migratePpApiConsentToSystemService(
                                        mSpyContext,
                                        mConsentDatastore,
                                        mAdServicesManager,
                                        mStatsdAdServicesLoggerMock));
        doNothing().when(() -> ConsentManager.clearPpApiConsent(mSpyContext, mConsentDatastore));

        int consentSourceOfTruth = Flags.PPAPI_ONLY;
        ConsentManager.handleConsentMigrationIfNeeded(
                mSpyContext,
                mConsentDatastore,
                mAdServicesManager,
                mStatsdAdServicesLoggerMock,
                consentSourceOfTruth);

        verify(
                () ->
                        ConsentManager.resetSharedPreference(
                                mSpyContext, SHARED_PREFS_KEY_HAS_MIGRATED));
        verify(
                () ->
                        ConsentManager.migratePpApiConsentToSystemService(
                                mSpyContext,
                                mConsentDatastore,
                                mAdServicesManager,
                                mStatsdAdServicesLoggerMock),
                never());
        verify(() -> ConsentManager.clearPpApiConsent(mSpyContext, mConsentDatastore), never());
    }

    @Test
    public void testHandleConsentMigrationIfNeeded_SystemServerOnly() {
        // Disable actual execution of internal methods
        doNothing()
                .when(
                        () ->
                                ConsentManager.resetSharedPreference(
                                        mSpyContext, SHARED_PREFS_KEY_HAS_MIGRATED));
        doNothing()
                .when(
                        () ->
                                ConsentManager.migratePpApiConsentToSystemService(
                                        mSpyContext,
                                        mConsentDatastore,
                                        mAdServicesManager,
                                        mStatsdAdServicesLoggerMock));
        doNothing().when(() -> ConsentManager.clearPpApiConsent(mSpyContext, mConsentDatastore));

        int consentSourceOfTruth = Flags.SYSTEM_SERVER_ONLY;
        ConsentManager.handleConsentMigrationIfNeeded(
                mSpyContext,
                mConsentDatastore,
                mAdServicesManager,
                mStatsdAdServicesLoggerMock,
                consentSourceOfTruth);

        verify(
                () ->
                        ConsentManager.resetSharedPreference(
                                mSpyContext, SHARED_PREFS_KEY_HAS_MIGRATED),
                never());
        verify(
                () ->
                        ConsentManager.migratePpApiConsentToSystemService(
                                mSpyContext,
                                mConsentDatastore,
                                mAdServicesManager,
                                mStatsdAdServicesLoggerMock));
        verify(() -> ConsentManager.clearPpApiConsent(mSpyContext, mConsentDatastore));
    }

    @Test
    public void testHandleConsentMigrationIfNeeded_PpApiAndSystemServer() {
        // Disable actual execution of internal methods
        doNothing()
                .when(
                        () ->
                                ConsentManager.resetSharedPreference(
                                        mSpyContext, SHARED_PREFS_KEY_HAS_MIGRATED));
        doNothing()
                .when(
                        () ->
                                ConsentManager.migratePpApiConsentToSystemService(
                                        mSpyContext,
                                        mConsentDatastore,
                                        mAdServicesManager,
                                        mStatsdAdServicesLoggerMock));
        doNothing().when(() -> ConsentManager.clearPpApiConsent(mSpyContext, mConsentDatastore));

        int consentSourceOfTruth = Flags.PPAPI_AND_SYSTEM_SERVER;
        ConsentManager.handleConsentMigrationIfNeeded(
                mSpyContext,
                mConsentDatastore,
                mAdServicesManager,
                mStatsdAdServicesLoggerMock,
                consentSourceOfTruth);

        verify(
                () ->
                        ConsentManager.resetSharedPreference(
                                mSpyContext, SHARED_PREFS_KEY_HAS_MIGRATED),
                never());
        verify(
                () ->
                        ConsentManager.migratePpApiConsentToSystemService(
                                mSpyContext,
                                mConsentDatastore,
                                mAdServicesManager,
                                mStatsdAdServicesLoggerMock));
        verify(() -> ConsentManager.clearPpApiConsent(mSpyContext, mConsentDatastore), never());
    }

    @Test
    public void testHandleConsentMigrationIfNeeded_AppSearchOnly() {
        // Disable actual execution of internal methods
        doNothing()
                .when(
                        () ->
                                ConsentManager.resetSharedPreference(
                                        mSpyContext, SHARED_PREFS_KEY_HAS_MIGRATED));
        doNothing()
                .when(
                        () ->
                                ConsentManager.migratePpApiConsentToSystemService(
                                        mSpyContext,
                                        mConsentDatastore,
                                        mAdServicesManager,
                                        mStatsdAdServicesLoggerMock));
        doNothing().when(() -> ConsentManager.clearPpApiConsent(mSpyContext, mConsentDatastore));

        int consentSourceOfTruth = Flags.APPSEARCH_ONLY;
        ConsentManager.handleConsentMigrationIfNeeded(
                mSpyContext,
                mConsentDatastore,
                mAdServicesManager,
                mStatsdAdServicesLoggerMock,
                consentSourceOfTruth);

        verify(
                () ->
                        ConsentManager.resetSharedPreference(
                                mSpyContext, SHARED_PREFS_KEY_HAS_MIGRATED),
                never());
        verify(
                () ->
                        ConsentManager.migratePpApiConsentToSystemService(
                                mSpyContext,
                                mConsentDatastore,
                                mAdServicesManager,
                                mStatsdAdServicesLoggerMock),
                never());
        verify(() -> ConsentManager.clearPpApiConsent(mSpyContext, mConsentDatastore), never());
    }

    @Test
    public void testHandleConsentMigrationFromAppSearchIfNeeded_notMigrated() throws Exception {
        when(mAppSearchConsentManagerMock.migrateConsentDataIfNeeded(any(), any(), any(), any()))
                .thenReturn(false);
        AtomicFileDatastore mockDatastore = mock(AtomicFileDatastore.class);
        AdServicesManager mockAdServicesManager = mock(AdServicesManager.class);
        SharedPreferences mockSharedPrefs = mock(SharedPreferences.class);
        SharedPreferences.Editor mockEditor = mock(SharedPreferences.Editor.class);
        when(mockSharedPrefs.edit()).thenReturn(mockEditor);
        when(mSpyContext.getSharedPreferences(any(String.class), anyInt()))
                .thenReturn(mockSharedPrefs);

        ConsentManager.handleConsentMigrationFromAppSearchIfNeeded(
                mSpyContext,
                mockDatastore,
                mAppConsentDaoSpy,
                mAppSearchConsentManagerMock,
                mockAdServicesManager,
                mStatsdAdServicesLoggerMock);
        verify(mockEditor, never()).putBoolean(any(), anyBoolean());
        verify(mAppSearchConsentManagerMock).migrateConsentDataIfNeeded(any(), any(), any(), any());
        verify(mockAdServicesManager, never()).recordNotificationDisplayed(true);
        verify(mockAdServicesManager, never()).recordGaUxNotificationDisplayed(true);
        verify(mockAdServicesManager, never()).recordDefaultConsent(anyBoolean());
        verify(mockAdServicesManager, never()).recordAdServicesDeletionOccurred(anyInt());
        verify(mockAdServicesManager, never()).recordDefaultAdIdState(anyBoolean());
        verify(mockAdServicesManager, never()).recordFledgeDefaultConsent(anyBoolean());
        verify(mockAdServicesManager, never()).recordMeasurementDefaultConsent(anyBoolean());
        verify(mockAdServicesManager, never()).recordTopicsDefaultConsent(anyBoolean());
        verify(mockAdServicesManager, never()).recordUserManualInteractionWithConsent(anyInt());
        verify(mMockIAdServicesManager, never()).setModuleEnrollmentState(anyString());
    }

    @Test
    public void testHandleConsentMigrationFromAppSearchIfNeeded() throws Exception {
        when(mAppSearchConsentManagerMock.migrateConsentDataIfNeeded(any(), any(), any(), any()))
                .thenReturn(true);
        when(mAppSearchConsentManagerMock.getConsent(any())).thenReturn(true);
        when(mMockFlags.getAdServicesConsentBusinessLogicMigrationEnabled()).thenReturn(true);
        doReturn(mMockFlags).when(FlagsFactory::getFlags);
        mConsentDatastore.putBoolean(CONSENT_KEY, true);
        mConsentDatastore.putBoolean(NOTIFICATION_DISPLAYED_ONCE, true);

        AdServicesManager mockAdServicesManager = mock(AdServicesManager.class);
        SharedPreferences mockSharedPrefs = mock(SharedPreferences.class);
        SharedPreferences.Editor mockEditor = mock(SharedPreferences.Editor.class);
        when(mockSharedPrefs.edit()).thenReturn(mockEditor);
        when(mSpyContext.getSharedPreferences(any(String.class), anyInt()))
                .thenReturn(mockSharedPrefs);
        when(mAppSearchConsentManagerMock.getUserManualInteractionWithConsent())
                .thenReturn(MANUAL_INTERACTIONS_RECORDED);
        when(mockEditor.commit()).thenReturn(true);
        ExtendedMockito.doReturn(false).when(() -> DeviceRegionProvider.isEuDevice(any()));

        ConsentManager.handleConsentMigrationFromAppSearchIfNeeded(
                mSpyContext,
                mConsentDatastore,
                mAppConsentDaoSpy,
                mAppSearchConsentManagerMock,
                mockAdServicesManager,
                mStatsdAdServicesLoggerMock);
        verify(mAppSearchConsentManagerMock).migrateConsentDataIfNeeded(any(), any(), any(), any());

        // Verify interactions data is migrated.
        assertThat(
                        mConsentDatastore.getBoolean(
                                ConsentConstants.MANUAL_INTERACTION_WITH_CONSENT_RECORDED))
                .isTrue();
        verify(mockAdServicesManager).recordUserManualInteractionWithConsent(anyInt());

        // Verify migration is recorded.
        verify(mockEditor)
                .putBoolean(eq(ConsentConstants.SHARED_PREFS_KEY_APPSEARCH_HAS_MIGRATED), eq(true));

        // Verify default consents data is migrated.
        assertThat(mConsentDatastore.getBoolean(ConsentConstants.TOPICS_DEFAULT_CONSENT)).isTrue();
        assertThat(mConsentDatastore.getBoolean(ConsentConstants.FLEDGE_DEFAULT_CONSENT)).isTrue();
        assertThat(mConsentDatastore.getBoolean(ConsentConstants.MEASUREMENT_DEFAULT_CONSENT))
                .isTrue();
        assertThat(mConsentDatastore.getBoolean(ConsentConstants.CONSENT_KEY)).isTrue();
        verify(mockAdServicesManager).recordDefaultConsent(eq(true));
        verify(mockAdServicesManager).recordTopicsDefaultConsent(eq(true));
        verify(mockAdServicesManager).recordFledgeDefaultConsent(eq(true));
        verify(mockAdServicesManager).recordMeasurementDefaultConsent(eq(true));

        // Verify per API consents data is migrated.
        assertThat(mConsentDatastore.getBoolean(AdServicesApiType.TOPICS.toPpApiDatastoreKey()))
                .isTrue();
        assertThat(mConsentDatastore.getBoolean(AdServicesApiType.FLEDGE.toPpApiDatastoreKey()))
                .isTrue();
        assertThat(
                        mConsentDatastore.getBoolean(
                                AdServicesApiType.MEASUREMENTS.toPpApiDatastoreKey()))
                .isTrue();
        verify(mockAdServicesManager, atLeast(4)).setConsent(any());
        ExtendedMockito.doReturn(false).when(() -> DeviceRegionProvider.isEuDevice(any()));

        ConsentMigrationStats consentMigrationStats =
                ConsentMigrationStats.builder()
                        .setTopicsConsent(true)
                        .setFledgeConsent(true)
                        .setMsmtConsent(true)
                        .setMigrationStatus(
                                ConsentMigrationStats.MigrationStatus
                                        .SUCCESS_WITH_SHARED_PREF_UPDATED)
                        .setMigrationType(
                                ConsentMigrationStats.MigrationType.APPSEARCH_TO_SYSTEM_SERVICE)
                        .setRegion(2)
                        .build();

        verify(mStatsdAdServicesLoggerMock).logConsentMigrationStats(consentMigrationStats);
        verify(mockAdServicesManager).setModuleEnrollmentState(anyString());
    }

    @Test
    public void testHandleConsentMigrationFromAppSearchIfNeededSharedPrefsEditorUnsuccessful()
            throws Exception {
        when(mAppSearchConsentManagerMock.migrateConsentDataIfNeeded(any(), any(), any(), any()))
                .thenReturn(true);
        when(mAppSearchConsentManagerMock.getConsent(any())).thenReturn(true);
        mConsentDatastore.putBoolean(CONSENT_KEY, true);
        mConsentDatastore.putBoolean(NOTIFICATION_DISPLAYED_ONCE, true);

        AdServicesManager mockAdServicesManager = mock(AdServicesManager.class);
        SharedPreferences mockSharedPrefs = mock(SharedPreferences.class);
        SharedPreferences.Editor mockEditor = mock(SharedPreferences.Editor.class);
        when(mockSharedPrefs.edit()).thenReturn(mockEditor);
        when(mSpyContext.getSharedPreferences(any(String.class), anyInt()))
                .thenReturn(mockSharedPrefs);
        when(mAppSearchConsentManagerMock.getUserManualInteractionWithConsent())
                .thenReturn(MANUAL_INTERACTIONS_RECORDED);
        when(mockEditor.commit()).thenReturn(false);
        ExtendedMockito.doReturn(false).when(() -> DeviceRegionProvider.isEuDevice(any()));

        ConsentManager.handleConsentMigrationFromAppSearchIfNeeded(
                mSpyContext,
                mConsentDatastore,
                mAppConsentDaoSpy,
                mAppSearchConsentManagerMock,
                mockAdServicesManager,
                mStatsdAdServicesLoggerMock);

        ConsentMigrationStats consentMigrationStats =
                ConsentMigrationStats.builder()
                        .setTopicsConsent(true)
                        .setFledgeConsent(true)
                        .setMsmtConsent(true)
                        .setMigrationStatus(
                                ConsentMigrationStats.MigrationStatus
                                        .SUCCESS_WITH_SHARED_PREF_NOT_UPDATED)
                        .setMigrationType(
                                ConsentMigrationStats.MigrationType.APPSEARCH_TO_SYSTEM_SERVICE)
                        .setRegion(2)
                        .build();

        verify(mStatsdAdServicesLoggerMock).logConsentMigrationStats(consentMigrationStats);
    }

    @Test
    @ExpectErrorLogUtilWithExceptionCall(
            throwable = IOException.class,
            errorCode = AD_SERVICES_ERROR_REPORTED__ERROR_CODE__APP_SEARCH_DATA_MIGRATION_FAILURE)
    public void testHandleConsentMigrationFromAppSearchIfNeededThrowsException() throws Exception {
        when(mAppSearchConsentManagerMock.migrateConsentDataIfNeeded(any(), any(), any(), any()))
                .thenThrow(IOException.class);

        AdServicesManager mockAdServicesManager = mock(AdServicesManager.class);

        doNothing().when(mStatsdAdServicesLoggerMock).logConsentMigrationStats(any());

        doReturn(false).when(() -> DeviceRegionProvider.isEuDevice(any()));

        ConsentManager.handleConsentMigrationFromAppSearchIfNeeded(
                mSpyContext,
                mConsentDatastore,
                mAppConsentDaoSpy,
                mAppSearchConsentManagerMock,
                mockAdServicesManager,
                mStatsdAdServicesLoggerMock);

        ConsentMigrationStats consentMigrationStats =
                ConsentMigrationStats.builder()
                        .setTopicsConsent(true)
                        .setFledgeConsent(true)
                        .setMsmtConsent(true)
                        .setMigrationStatus(ConsentMigrationStats.MigrationStatus.FAILURE)
                        .setMigrationType(
                                ConsentMigrationStats.MigrationType.APPSEARCH_TO_SYSTEM_SERVICE)
                        .setRegion(2)
                        .build();
        Mockito.verify(mStatsdAdServicesLoggerMock).logConsentMigrationStats(consentMigrationStats);
    }

    @Test
    public void testTopicsProxyCalls() {
        Topic topic = Topic.create(1, 1, 1);
        ArrayList<String> tablesToBlock = new ArrayList<>();
        tablesToBlock.add(TopicsTables.BlockedTopicsContract.TABLE);

        TopicsWorker topicsWorker =
                spy(
                        new TopicsWorker(
                                mMockEpochManager,
                                mCacheManagerMock,
                                mBlockedTopicsManagerMock,
                                mAppUpdateManagerMock,
                                mMockFlags));
        doReturn(topicsWorker).when(mTopicsWorksSupplierMock).get();

        ConsentManager consentManager =
                new ConsentManager(
                        mTopicsWorksSupplierMock,
                        mAppConsentDaoSupplierMock,
                        mEnrollmentDaoSupplierMock,
                        mMeasurementImplSupplierMock,
                        mCustomAudienceDaoMock,
                        mAppInstallDaoMock,
                        mProtectedSignalsDaoMock,
                        mFrequencyCapDaoMock,
                        mEncodedPayloadDaoMock,
                        mAdServicesManager,
                        mConsentDatastore,
                        mAppSearchConsentManagerMock,
                        mUserProfileIdManagerMock,
                        mUxStatesDaoMock,
                        mMockFlags,
                        mMockDebugFlags,
                        Flags.PPAPI_ONLY,
                        true);
        doNothing().when(mBlockedTopicsManagerMock).blockTopic(any());
        doNothing().when(mBlockedTopicsManagerMock).unblockTopic(any());
        // The actual usage is to invoke clearAllTopicsData() from TopicsWorker
        doNothing().when(topicsWorker).clearAllTopicsData(any());

        consentManager.revokeConsentForTopic(topic);
        consentManager.restoreConsentForTopic(topic);
        consentManager.resetTopics();

        verify(mBlockedTopicsManagerMock).blockTopic(topic);
        verify(mBlockedTopicsManagerMock).unblockTopic(topic);
        verify(topicsWorker).clearAllTopicsData(tablesToBlock);
    }

    @Test
    public void testLoggingSettingsUsageReportedOptInSelectedRow() {
        doReturn(false).when(() -> DeviceRegionProvider.isEuDevice(any(Context.class)));
        ConsentManager temporalConsentManager =
                getConsentManagerByConsentSourceOfTruth(Flags.PPAPI_ONLY);

        temporalConsentManager.enable(mSpyContext);

        verify(mUiStatsLoggerMock).logOptInSelected();
    }

    @Test
    public void testLoggingSettingsUsageReportedOptInSelectedEu() {
        doReturn(true).when(() -> DeviceRegionProvider.isEuDevice(any(Context.class)));
        ConsentManager temporalConsentManager =
                getConsentManagerByConsentSourceOfTruth(Flags.PPAPI_ONLY);

        temporalConsentManager.enable(mSpyContext);

        verify(mUiStatsLoggerMock).logOptInSelected();
    }

    @Test
    public void testConsentPerApiIsGivenAfterEnabling_PpApiOnly() throws Exception {
        when(mMockFlags.getGaUxFeatureEnabled()).thenReturn(true);
        boolean isGiven = true;
        int consentSourceOfTruth = Flags.PPAPI_ONLY;
        ConsentManager spyConsentManager =
                getSpiedConsentManagerForMigrationTesting(isGiven, consentSourceOfTruth);

        spyConsentManager.enable(mSpyContext, AdServicesApiType.TOPICS);

        assertThat(spyConsentManager.getConsent(AdServicesApiType.TOPICS).isGiven()).isTrue();
        verify(spyConsentManager)
                .setConsentPerApiToPpApi(eq(AdServicesApiType.TOPICS), eq(/* isGiven */ true));

        verify(spyConsentManager).resetTopicsAndBlockedTopics();
    }

    @Test
    public void testConsentPerApiIsGivenAfterEnabling_SystemServerOnly() throws Exception {
        when(mMockFlags.getGaUxFeatureEnabled()).thenReturn(true);
        boolean isGiven = true;
        int consentSourceOfTruth = Flags.SYSTEM_SERVER_ONLY;
        ConsentManager spyConsentManager =
                getSpiedConsentManagerForConsentPerApiTesting(
                        isGiven, consentSourceOfTruth, AdServicesApiType.TOPICS.toConsentApiType());

        spyConsentManager.enable(mSpyContext, AdServicesApiType.TOPICS);

        assertThat(spyConsentManager.getConsent(AdServicesApiType.TOPICS).isGiven()).isTrue();
        verify(
                () ->
                        ConsentManager.setPerApiConsentToSystemServer(
                                any(),
                                eq(AdServicesApiType.TOPICS.toConsentApiType()),
                                eq(isGiven)));
        verify(mMockIAdServicesManager).getConsent(ConsentParcel.TOPICS);
        verify(spyConsentManager).resetTopicsAndBlockedTopics();
    }

    @Test
    @ExpectErrorLogUtilWithExceptionCall(
            errorCode = AD_SERVICES_ERROR_REPORTED__ERROR_CODE__ERROR_WHILE_GET_CONSENT)
    public void testConsentPerApiIsGivenAfterEnabling_PpApiAndSystemServer() throws Exception {
        when(mMockFlags.getGaUxFeatureEnabled()).thenReturn(true);
        boolean isGiven = true;
        int consentSourceOfTruth = Flags.PPAPI_AND_SYSTEM_SERVER;
        ConsentManager spyConsentManager =
                getSpiedConsentManagerForConsentPerApiTesting(
                        isGiven, consentSourceOfTruth, AdServicesApiType.TOPICS.toConsentApiType());

        spyConsentManager.enable(mSpyContext, AdServicesApiType.TOPICS);

        assertThat(spyConsentManager.getConsent(AdServicesApiType.TOPICS).isGiven()).isTrue();
        verify(
                () ->
                        ConsentManager.setPerApiConsentToSystemServer(
                                any(),
                                eq(AdServicesApiType.TOPICS.toConsentApiType()),
                                eq(isGiven)));
        verify(mMockIAdServicesManager, times(2)).getConsent(ConsentParcel.TOPICS);
        verify(spyConsentManager)
                .setConsentPerApiToPpApi(eq(AdServicesApiType.TOPICS), eq(/* isGiven */ true));
        verify(spyConsentManager).resetTopicsAndBlockedTopics();
    }

    @Test
    public void testConsentPerApiIsGivenAfterEnabling_AppSearchOnly() throws Exception {
        when(mMockFlags.getGaUxFeatureEnabled()).thenReturn(true);
        doReturn(true).when(mMockFlags).getEnableAppsearchConsentData();
        boolean isGiven = true;
        int consentSourceOfTruth = Flags.APPSEARCH_ONLY;
        AdServicesApiType apiType = AdServicesApiType.TOPICS;
        ConsentManager spyConsentManager =
                getSpiedConsentManagerForMigrationTesting(isGiven, consentSourceOfTruth);

        spyConsentManager.enable(mSpyContext, AdServicesApiType.TOPICS);
        verify(spyConsentManager)
                .setPerApiConsentToSourceOfTruth(eq(/* isGiven */ true), eq(apiType));
        verify(mAppSearchConsentManagerMock)
                .setConsent(eq(apiType.toPpApiDatastoreKey()), eq(isGiven));
        when(mAppSearchConsentManagerMock.getConsent(AdServicesApiType.CONSENT_TOPICS))
                .thenReturn(true);
        assertThat(spyConsentManager.getConsent(AdServicesApiType.TOPICS).isGiven()).isTrue();
    }

    @Test
    @ExpectErrorLogUtilWithExceptionCall(
            errorCode = AD_SERVICES_ERROR_REPORTED__ERROR_CODE__ERROR_WHILE_GET_CONSENT)
    public void testFledgeConsentIsEnabled_userProfileIdIsClearedThanRecreated() throws Exception {
        when(mMockFlags.getGaUxFeatureEnabled()).thenReturn(true);
        boolean isGiven = true;
        int consentSourceOfTruth = Flags.PPAPI_AND_SYSTEM_SERVER;
        ConsentManager spyConsentManager =
                getSpiedConsentManagerForConsentPerApiTesting(
                        isGiven, consentSourceOfTruth, AdServicesApiType.FLEDGE.toConsentApiType());

        spyConsentManager.enable(mSpyContext, AdServicesApiType.FLEDGE);

        assertThat(spyConsentManager.getConsent(AdServicesApiType.FLEDGE).isGiven()).isTrue();
        verify(mUserProfileIdManagerMock).deleteId();
        verify(mUserProfileIdManagerMock).getOrCreateId();
    }

    @Test
    @ExpectErrorLogUtilWithExceptionCall(
            errorCode = AD_SERVICES_ERROR_REPORTED__ERROR_CODE__ERROR_WHILE_GET_CONSENT,
            times = 3)
    public void testFledgeConsentIsDisabled_userProfileIdIsCleared() throws Exception {
        when(mMockFlags.getGaUxFeatureEnabled()).thenReturn(true);
        boolean isGiven = false;
        int consentSourceOfTruth = Flags.PPAPI_AND_SYSTEM_SERVER;
        ConsentManager spyConsentManager =
                getSpiedConsentManagerForConsentPerApiTesting(
                        isGiven, consentSourceOfTruth, AdServicesApiType.FLEDGE.toConsentApiType());

        spyConsentManager.disable(mSpyContext, AdServicesApiType.FLEDGE);

        assertThat(spyConsentManager.getConsent(AdServicesApiType.FLEDGE).isGiven()).isFalse();
        verify(mUserProfileIdManagerMock).deleteId();
    }

    @Test
    public void testGetDefaultConsent_AppSearchOnly() throws Exception {
        doReturn(true).when(mMockFlags).getEnableAppsearchConsentData();
        int consentSourceOfTruth = Flags.APPSEARCH_ONLY;
        ConsentManager spyConsentManager =
                getSpiedConsentManagerForMigrationTesting(false, consentSourceOfTruth);

        when(mAppSearchConsentManagerMock.getConsent(eq(ConsentConstants.DEFAULT_CONSENT)))
                .thenReturn(false);
        assertThat(spyConsentManager.getDefaultConsent()).isFalse();
        verify(mAppSearchConsentManagerMock).getConsent(eq(ConsentConstants.DEFAULT_CONSENT));
    }

    @Test
    public void testGetTopicsDefaultConsent_AppSearchOnly() throws Exception {
        doReturn(true).when(mMockFlags).getEnableAppsearchConsentData();
        int consentSourceOfTruth = Flags.APPSEARCH_ONLY;
        ConsentManager spyConsentManager =
                getSpiedConsentManagerForMigrationTesting(false, consentSourceOfTruth);

        when(mAppSearchConsentManagerMock.getConsent(eq(ConsentConstants.TOPICS_DEFAULT_CONSENT)))
                .thenReturn(false);
        assertThat(spyConsentManager.getTopicsDefaultConsent()).isFalse();
        verify(mAppSearchConsentManagerMock)
                .getConsent(eq(ConsentConstants.TOPICS_DEFAULT_CONSENT));
    }

    @Test
    public void testGetFledgeDefaultConsent_AppSearchOnly() throws Exception {
        doReturn(true).when(mMockFlags).getEnableAppsearchConsentData();
        int consentSourceOfTruth = Flags.APPSEARCH_ONLY;
        ConsentManager spyConsentManager =
                getSpiedConsentManagerForMigrationTesting(false, consentSourceOfTruth);

        when(mAppSearchConsentManagerMock.getConsent(eq(ConsentConstants.FLEDGE_DEFAULT_CONSENT)))
                .thenReturn(false);
        assertThat(spyConsentManager.getFledgeDefaultConsent()).isFalse();
        verify(mAppSearchConsentManagerMock)
                .getConsent(eq(ConsentConstants.FLEDGE_DEFAULT_CONSENT));
    }

    @Test
    public void testGetMeasurementDefaultConsent_AppSearchOnly() throws Exception {
        doReturn(true).when(mMockFlags).getEnableAppsearchConsentData();
        int consentSourceOfTruth = Flags.APPSEARCH_ONLY;
        ConsentManager spyConsentManager =
                getSpiedConsentManagerForMigrationTesting(false, consentSourceOfTruth);

        when(mAppSearchConsentManagerMock.getConsent(
                        eq(ConsentConstants.MEASUREMENT_DEFAULT_CONSENT)))
                .thenReturn(false);
        assertThat(spyConsentManager.getMeasurementDefaultConsent()).isFalse();
        verify(mAppSearchConsentManagerMock)
                .getConsent(eq(ConsentConstants.MEASUREMENT_DEFAULT_CONSENT));
    }

    @Test
    public void testGetDefaultAdIdState_AppSearchOnly() throws Exception {
        doReturn(true).when(mMockFlags).getEnableAppsearchConsentData();
        int consentSourceOfTruth = Flags.APPSEARCH_ONLY;
        ConsentManager spyConsentManager =
                getSpiedConsentManagerForMigrationTesting(false, consentSourceOfTruth);

        when(mAppSearchConsentManagerMock.getConsent(eq(ConsentConstants.DEFAULT_AD_ID_STATE)))
                .thenReturn(false);
        assertThat(spyConsentManager.getDefaultAdIdState()).isFalse();
        verify(mAppSearchConsentManagerMock).getConsent(eq(ConsentConstants.DEFAULT_AD_ID_STATE));
    }

    @Test
    public void testRecordDefaultConsent_AppSearchOnly() throws Exception {
        doReturn(true).when(mMockFlags).getEnableAppsearchConsentData();
        int consentSourceOfTruth = Flags.APPSEARCH_ONLY;
        ConsentManager spyConsentManager =
                getSpiedConsentManagerForMigrationTesting(false, consentSourceOfTruth);

        spyConsentManager.recordDefaultConsent(true);
        verify(mAppSearchConsentManagerMock)
                .setConsent(eq(ConsentConstants.DEFAULT_CONSENT), eq(true));
    }

    @Test
    public void testRecordTopicsDefaultConsent_AppSearchOnly() throws Exception {
        doReturn(true).when(mMockFlags).getEnableAppsearchConsentData();
        int consentSourceOfTruth = Flags.APPSEARCH_ONLY;
        ConsentManager spyConsentManager =
                getSpiedConsentManagerForMigrationTesting(false, consentSourceOfTruth);

        spyConsentManager.recordTopicsDefaultConsent(true);
        verify(mAppSearchConsentManagerMock)
                .setConsent(eq(ConsentConstants.TOPICS_DEFAULT_CONSENT), eq(true));
    }

    @Test
    public void testRecordFledgeDefaultConsent_AppSearchOnly() throws Exception {
        doReturn(true).when(mMockFlags).getEnableAppsearchConsentData();
        int consentSourceOfTruth = Flags.APPSEARCH_ONLY;
        ConsentManager spyConsentManager =
                getSpiedConsentManagerForMigrationTesting(false, consentSourceOfTruth);

        spyConsentManager.recordFledgeDefaultConsent(true);
        verify(mAppSearchConsentManagerMock)
                .setConsent(eq(ConsentConstants.FLEDGE_DEFAULT_CONSENT), eq(true));
    }

    @Test
    public void testRecordMeasurementDefaultConsent_AppSearchOnly() throws Exception {
        doReturn(true).when(mMockFlags).getEnableAppsearchConsentData();
        int consentSourceOfTruth = Flags.APPSEARCH_ONLY;
        ConsentManager spyConsentManager =
                getSpiedConsentManagerForMigrationTesting(false, consentSourceOfTruth);

        spyConsentManager.recordMeasurementDefaultConsent(true);
        verify(mAppSearchConsentManagerMock)
                .setConsent(eq(ConsentConstants.MEASUREMENT_DEFAULT_CONSENT), eq(true));
    }

    @Test
    public void testRecordDefaultAdIdState_AppSearchOnly() throws Exception {
        doReturn(true).when(mMockFlags).getEnableAppsearchConsentData();
        int consentSourceOfTruth = Flags.APPSEARCH_ONLY;
        ConsentManager spyConsentManager =
                getSpiedConsentManagerForMigrationTesting(false, consentSourceOfTruth);

        spyConsentManager.recordDefaultAdIdState(true);
        verify(mAppSearchConsentManagerMock)
                .setConsent(eq(ConsentConstants.DEFAULT_AD_ID_STATE), eq(true));
    }

    @Test
    public void testAllThreeConsentsPerApiAreGivenAggregatedConsentIsSet_PpApiOnly()
            throws Exception {
        when(mMockFlags.getGaUxFeatureEnabled()).thenReturn(true);
        boolean isGiven = true;
        int consentSourceOfTruth = Flags.PPAPI_ONLY;
        ConsentManager spyConsentManager =
                getSpiedConsentManagerForMigrationTesting(isGiven, consentSourceOfTruth);

        spyConsentManager.enable(mSpyContext, AdServicesApiType.TOPICS);
        spyConsentManager.enable(mSpyContext, AdServicesApiType.FLEDGE);
        spyConsentManager.enable(mSpyContext, AdServicesApiType.MEASUREMENTS);

        assertThat(spyConsentManager.getConsent(AdServicesApiType.TOPICS).isGiven()).isTrue();
        assertThat(spyConsentManager.getConsent(AdServicesApiType.FLEDGE).isGiven()).isTrue();
        assertThat(spyConsentManager.getConsent(AdServicesApiType.MEASUREMENTS).isGiven()).isTrue();
        assertThat(spyConsentManager.getConsent().isGiven()).isTrue();
        verify(spyConsentManager)
                .setConsentPerApiToPpApi(eq(AdServicesApiType.TOPICS), eq(/* isGiven */ true));
        verify(spyConsentManager)
                .setConsentPerApiToPpApi(eq(AdServicesApiType.FLEDGE), eq(/* isGiven */ true));
        verify(spyConsentManager)
                .setConsentPerApiToPpApi(
                        eq(AdServicesApiType.MEASUREMENTS), eq(/* isGiven */ true));
        verify(spyConsentManager, times(3)).setAggregatedConsentToPpApi();

        verifyDataCleanup(spyConsentManager);
    }

    @Test
    public void testAllConsentAreRevokedCleanupIsExecuted() throws Exception {
        when(mMockFlags.getGaUxFeatureEnabled()).thenReturn(true);
        boolean isGiven = true;
        int consentSourceOfTruth = Flags.PPAPI_ONLY;
        ConsentManager spyConsentManager =
                getSpiedConsentManagerForMigrationTesting(isGiven, consentSourceOfTruth);

        // set up the initial state
        spyConsentManager.enable(mSpyContext, AdServicesApiType.TOPICS);
        spyConsentManager.enable(mSpyContext, AdServicesApiType.FLEDGE);
        spyConsentManager.enable(mSpyContext, AdServicesApiType.MEASUREMENTS);

        assertThat(spyConsentManager.getConsent(AdServicesApiType.TOPICS).isGiven()).isTrue();
        assertThat(spyConsentManager.getConsent(AdServicesApiType.FLEDGE).isGiven()).isTrue();
        assertThat(spyConsentManager.getConsent(AdServicesApiType.MEASUREMENTS).isGiven()).isTrue();
        assertThat(spyConsentManager.getConsent().isGiven()).isTrue();
        verify(spyConsentManager)
                .setConsentPerApiToPpApi(eq(AdServicesApiType.TOPICS), eq(/* isGiven */ true));
        verify(spyConsentManager)
                .setConsentPerApiToPpApi(eq(AdServicesApiType.FLEDGE), eq(/* isGiven */ true));
        verify(spyConsentManager)
                .setConsentPerApiToPpApi(
                        eq(AdServicesApiType.MEASUREMENTS), eq(/* isGiven */ true));
        verify(spyConsentManager, times(3)).setAggregatedConsentToPpApi();

        // disable all the consent one by one
        spyConsentManager.disable(mSpyContext, AdServicesApiType.TOPICS);
        spyConsentManager.disable(mSpyContext, AdServicesApiType.FLEDGE);
        spyConsentManager.disable(mSpyContext, AdServicesApiType.MEASUREMENTS);

        assertThat(spyConsentManager.getConsent(AdServicesApiType.TOPICS).isGiven()).isFalse();
        assertThat(spyConsentManager.getConsent(AdServicesApiType.FLEDGE).isGiven()).isFalse();
        assertThat(spyConsentManager.getConsent(AdServicesApiType.MEASUREMENTS).isGiven())
                .isFalse();
        assertThat(spyConsentManager.getConsent().isGiven()).isFalse();

        verify(
                () ->
                        BackgroundJobsManager.unscheduleJobsPerApi(
                                any(JobScheduler.class), eq(AdServicesApiType.TOPICS)));
        verify(
                () ->
                        BackgroundJobsManager.unscheduleJobsPerApi(
                                any(JobScheduler.class), eq(AdServicesApiType.FLEDGE)));
        verify(
                () ->
                        BackgroundJobsManager.unscheduleJobsPerApi(
                                any(JobScheduler.class), eq(AdServicesApiType.MEASUREMENTS)));
        verify(() -> BackgroundJobsManager.unscheduleAllBackgroundJobs(any(JobScheduler.class)));

        verify(spyConsentManager, times(2)).resetTopicsAndBlockedTopics();
        verify(spyConsentManager, times(2)).resetAppsAndBlockedApps();
        verify(spyConsentManager, times(2)).resetMeasurement();
    }

    @Test
    public void testManualInteractionWithConsentRecorded_PpApiOnly() throws Exception {
        int consentSourceOfTruth = Flags.PPAPI_ONLY;
        ConsentManager spyConsentManager =
                getSpiedConsentManagerForMigrationTesting(
                        /* isGiven */ false, consentSourceOfTruth);

        assertThat(spyConsentManager.getUserManualInteractionWithConsent()).isEqualTo(UNKNOWN);

        verify(mMockIAdServicesManager, never()).getUserManualInteractionWithConsent();

        spyConsentManager.recordUserManualInteractionWithConsent(MANUAL_INTERACTIONS_RECORDED);

        assertThat(spyConsentManager.getUserManualInteractionWithConsent())
                .isEqualTo(MANUAL_INTERACTIONS_RECORDED);

        verify(mMockIAdServicesManager, never()).getUserManualInteractionWithConsent();
        verify(mMockIAdServicesManager, never()).recordUserManualInteractionWithConsent(anyInt());
    }

    @Test
    public void testManualInteractionWithConsentRecorded_SystemServerOnly() throws Exception {
        int consentSourceOfTruth = Flags.SYSTEM_SERVER_ONLY;
        ConsentManager spyConsentManager =
                getSpiedConsentManagerForMigrationTesting(
                        /* isGiven */ false, consentSourceOfTruth);

        assertThat(spyConsentManager.getUserManualInteractionWithConsent()).isEqualTo(UNKNOWN);

        verify(mMockIAdServicesManager).getUserManualInteractionWithConsent();

        doReturn(MANUAL_INTERACTIONS_RECORDED)
                .when(mMockIAdServicesManager)
                .getUserManualInteractionWithConsent();
        spyConsentManager.recordUserManualInteractionWithConsent(MANUAL_INTERACTIONS_RECORDED);

        assertThat(spyConsentManager.getUserManualInteractionWithConsent())
                .isEqualTo(MANUAL_INTERACTIONS_RECORDED);

        verify(mMockIAdServicesManager, times(2)).getUserManualInteractionWithConsent();
        verify(mMockIAdServicesManager).recordUserManualInteractionWithConsent(anyInt());

        // Verify the bit is not set in PPAPI
        assertThat(mConsentDatastore.getBoolean(MANUAL_INTERACTION_WITH_CONSENT_RECORDED)).isNull();
    }

    @Test
    public void testManualInteractionWithConsentRecorded_PpApiAndSystemServer() throws Exception {
        int consentSourceOfTruth = Flags.PPAPI_AND_SYSTEM_SERVER;
        ConsentManager spyConsentManager =
                getSpiedConsentManagerForMigrationTesting(
                        /* isGiven */ false, consentSourceOfTruth);

        @ConsentManager.UserManualInteraction
        int userManualInteractionWithConsent =
                spyConsentManager.getUserManualInteractionWithConsent();

        assertThat(userManualInteractionWithConsent).isEqualTo(UNKNOWN);

        verify(mMockIAdServicesManager).getUserManualInteractionWithConsent();

        doReturn(MANUAL_INTERACTIONS_RECORDED)
                .when(mMockIAdServicesManager)
                .getUserManualInteractionWithConsent();
        spyConsentManager.recordUserManualInteractionWithConsent(MANUAL_INTERACTIONS_RECORDED);

        assertThat(spyConsentManager.getUserManualInteractionWithConsent())
                .isEqualTo(MANUAL_INTERACTIONS_RECORDED);

        verify(mMockIAdServicesManager, times(2)).getUserManualInteractionWithConsent();
        verify(mMockIAdServicesManager).recordUserManualInteractionWithConsent(anyInt());

        // Verify the bit is also set in PPAPI
        assertThat(mConsentDatastore.getBoolean(MANUAL_INTERACTION_WITH_CONSENT_RECORDED)).isTrue();
    }

    @Test
    public void testManualInteractionWithConsentRecorded_appSearchOnly() throws Exception {
        int consentSourceOfTruth = Flags.APPSEARCH_ONLY;
        when(mMockFlags.getEnableAppsearchConsentData()).thenReturn(true);
        ConsentManager spyConsentManager =
                getSpiedConsentManagerForMigrationTesting(
                        /* isGiven */ false, consentSourceOfTruth);

        when(mAppSearchConsentManagerMock.getUserManualInteractionWithConsent())
                .thenReturn(UNKNOWN);
        assertThat(spyConsentManager.getUserManualInteractionWithConsent()).isEqualTo(UNKNOWN);
        verify(mAppSearchConsentManagerMock).getUserManualInteractionWithConsent();
        verify(mMockIAdServicesManager, never()).getUserManualInteractionWithConsent();

        spyConsentManager.recordUserManualInteractionWithConsent(MANUAL_INTERACTIONS_RECORDED);
        verify(mAppSearchConsentManagerMock)
                .recordUserManualInteractionWithConsent(MANUAL_INTERACTIONS_RECORDED);
        when(mAppSearchConsentManagerMock.getUserManualInteractionWithConsent())
                .thenReturn(MANUAL_INTERACTIONS_RECORDED);
        assertThat(spyConsentManager.getUserManualInteractionWithConsent())
                .isEqualTo(MANUAL_INTERACTIONS_RECORDED);

        verify(mMockIAdServicesManager, never()).getUserManualInteractionWithConsent();
        verify(mMockIAdServicesManager, never()).recordUserManualInteractionWithConsent(anyInt());
    }

    // Note this method needs to be invoked after other private variables are initialized.
    private ConsentManager getConsentManagerByConsentSourceOfTruth(int consentSourceOfTruth) {
        return new ConsentManager(
                mTopicsWorksSupplierMock,
                mAppConsentDaoSupplierMock,
                mEnrollmentDaoSupplierMock,
                mMeasurementImplSupplierMock,
                mCustomAudienceDaoMock,
                mAppInstallDaoMock,
                mProtectedSignalsDaoMock,
                mFrequencyCapDaoMock,
                mEncodedPayloadDaoMock,
                mAdServicesManager,
                mConsentDatastore,
                mAppSearchConsentManagerMock,
                mUserProfileIdManagerMock,
                mUxStatesDaoMock,
                mMockFlags,
                mMockDebugFlags,
                consentSourceOfTruth,
                true);
    }

    private ConsentManager getSpiedConsentManagerForMigrationTesting(
            boolean isGiven, int consentSourceOfTruth) throws Exception {
        ConsentManager consentManager =
                spy(getConsentManagerByConsentSourceOfTruth(consentSourceOfTruth));

        // Disable IPC calls
        doNothing().when(() -> ConsentManager.setConsentToSystemServer(any(), anyBoolean()));
        ConsentParcel consentParcel =
                isGiven
                        ? ConsentParcel.createGivenConsent(ConsentParcel.ALL_API)
                        : ConsentParcel.createRevokedConsent(ConsentParcel.ALL_API);
        doReturn(consentParcel).when(mMockIAdServicesManager).getConsent(ConsentParcel.ALL_API);
        doReturn(isGiven).when(mMockIAdServicesManager).wasNotificationDisplayed();
        doNothing().when(mMockIAdServicesManager).recordNotificationDisplayed(true);
        doReturn(isGiven).when(mMockIAdServicesManager).wasGaUxNotificationDisplayed();
        doNothing().when(mMockIAdServicesManager).recordGaUxNotificationDisplayed(true);
        doReturn(UNKNOWN).when(mMockIAdServicesManager).getUserManualInteractionWithConsent();
        doNothing().when(mMockIAdServicesManager).recordUserManualInteractionWithConsent(anyInt());
        doReturn(isGiven).when(mAppSearchConsentManagerMock).getConsent(CONSENT_KEY_FOR_ALL);
        return consentManager;
    }

    private ConsentManager getSpiedConsentManagerForConsentPerApiTesting(
            boolean isGiven,
            int consentSourceOfTruth,
            @ConsentParcel.ConsentApiType int consentApiType)
            throws Exception {
        ConsentManager consentManager =
                spy(getConsentManagerByConsentSourceOfTruth(consentSourceOfTruth));

        // Disable IPC calls
        doNothing()
                .when(
                        () ->
                                ConsentManager.setPerApiConsentToSystemServer(
                                        any(), anyInt(), anyBoolean()));
        ConsentParcel consentParcel =
                isGiven
                        ? ConsentParcel.createGivenConsent(consentApiType)
                        : ConsentParcel.createRevokedConsent(consentApiType);
        doReturn(consentParcel).when(mMockIAdServicesManager).getConsent(consentApiType);
        doReturn(isGiven).when(mMockIAdServicesManager).wasNotificationDisplayed();
        doNothing().when(mMockIAdServicesManager).recordNotificationDisplayed(true);
        doReturn(isGiven).when(mMockIAdServicesManager).wasGaUxNotificationDisplayed();
        doNothing().when(mMockIAdServicesManager).recordGaUxNotificationDisplayed(true);
        doReturn(UNKNOWN).when(mMockIAdServicesManager).getUserManualInteractionWithConsent();
        doNothing().when(mMockIAdServicesManager).recordUserManualInteractionWithConsent(anyInt());
        doReturn(isGiven).when(mAppSearchConsentManagerMock).getConsent(any());
        return consentManager;
    }

    private void verifyConsentMigration(
            ConsentManager consentManager,
            boolean isGiven,
            boolean hasWrittenToPpApi,
            boolean hasWrittenToSystemServer,
            boolean hasReadFromSystemServer)
            throws Exception {
        verify(consentManager, verificationMode(hasWrittenToPpApi)).setConsentToPpApi(isGiven);
        verify(
                () -> ConsentManager.setConsentToSystemServer(any(), eq(isGiven)),
                verificationMode(hasWrittenToSystemServer));

        verify(mMockIAdServicesManager, verificationMode(hasReadFromSystemServer))
                .getConsent(ConsentParcel.ALL_API);
    }

    private void verifyDataCleanup(ConsentManager consentManager) throws Exception {
        verify(consentManager).resetTopicsAndBlockedTopics();
        verify(consentManager).resetAppsAndBlockedApps();
        verify(consentManager).resetMeasurement();
    }

    private VerificationMode verificationMode(boolean hasHappened) {
        return hasHappened ? atLeastOnce() : never();
    }

    private void mockGetPackageUid(String packageName, int uid) throws Exception {
        doReturn(uid)
                .when(
                        () ->
                                PackageManagerCompatUtils.getPackageUid(
                                        any(), eq(packageName), anyInt()));
    }

    private void mockInstalledApplications(List<ApplicationInfo> applicationsInstalled) {
        doReturn(applicationsInstalled)
                .when(() -> PackageManagerCompatUtils.getInstalledApplications(any(), anyInt()));
    }

    private void mockThrowExceptionOnGetPackageUid(String packageName) {
        doThrow(PackageManager.NameNotFoundException.class)
                .when(
                        () ->
                                PackageManagerCompatUtils.getPackageUid(
                                        any(), eq(packageName), anyInt()));
    }

    private void mockMeasurementEnabled(boolean value) {
        when(mMockFlags.getMeasurementEnabled()).thenReturn(value);
    }

    private List<ApplicationInfo> createApplicationInfos(String... packageNames) {
        return Arrays.stream(packageNames)
                .map(s -> ApplicationInfoBuilder.newBuilder().setPackageName(s).build())
                .collect(Collectors.toList());
    }

    private class ListMatcherIgnoreOrder implements ArgumentMatcher<List<String>> {
        private final List<String> mStrings;

        private ListMatcherIgnoreOrder(List<String> strings) {
            Objects.requireNonNull(strings);
            mStrings = strings;
        }

        @Override
        public boolean matches(@Nullable List<String> argument) {
            if (argument == null) {
                return false;
            }
            if (argument.size() != mStrings.size()) {
                return false;
            }
            if (!argument.containsAll(mStrings)) {
                return false;
            }
            if (!mStrings.containsAll(argument)) {
                return false;
            }
            return true;
        }
    }

    @Test
    public void testCreateAndInitializeDatastore() throws Exception {
        mConsentDatastore.clear();
        mockEnableAtomicFileDatastoreBatchUpdateApi(/* enable= */ true);

        mConsentDatastore =
                ConsentManager.createAndInitializeDataStore(
                        mSpyContext, mMockAdServicesErrorLogger);

        expect.withMessage("getBoolean(%s)", ConsentConstants.NOTIFICATION_DISPLAYED_ONCE)
                .that(mConsentDatastore.getBoolean(ConsentConstants.NOTIFICATION_DISPLAYED_ONCE))
                .isFalse();
        expect.withMessage("getBoolean(%s)", ConsentConstants.GA_UX_NOTIFICATION_DISPLAYED_ONCE)
                .that(
                        mConsentDatastore.getBoolean(
                                ConsentConstants.GA_UX_NOTIFICATION_DISPLAYED_ONCE))
                .isFalse();
        expect.withMessage("getBoolean(%s)", ConsentConstants.WAS_U18_NOTIFICATION_DISPLAYED)
                .that(mConsentDatastore.getBoolean(ConsentConstants.WAS_U18_NOTIFICATION_DISPLAYED))
                .isFalse();
        expect.withMessage("getBoolean(%s)", ConsentConstants.PAS_NOTIFICATION_DISPLAYED_ONCE)
                .that(
                        mConsentDatastore.getBoolean(
                                ConsentConstants.PAS_NOTIFICATION_DISPLAYED_ONCE))
                .isFalse();
        expect.withMessage("getBoolean(%s)", ConsentConstants.PAS_NOTIFICATION_OPENED)
                .that(mConsentDatastore.getBoolean(ConsentConstants.PAS_NOTIFICATION_OPENED))
                .isFalse();
    }

    @Test
    public void testSetPrivacySandboxFeatureTypeInApp_updateAPIEnabled() throws Exception {
        mockEnableAtomicFileDatastoreBatchUpdateApi(/* enable= */ true);

        runAndVerifyPrivacySandboxFeatureTypeInApp(
                PrivacySandboxFeatureType.PRIVACY_SANDBOX_UNSUPPORTED);
        runAndVerifyPrivacySandboxFeatureTypeInApp(
                PrivacySandboxFeatureType.PRIVACY_SANDBOX_FIRST_CONSENT);
        runAndVerifyPrivacySandboxFeatureTypeInApp(
                PrivacySandboxFeatureType.PRIVACY_SANDBOX_RECONSENT);
        runAndVerifyPrivacySandboxFeatureTypeInApp(
                PrivacySandboxFeatureType.PRIVACY_SANDBOX_FIRST_CONSENT_FF);
        runAndVerifyPrivacySandboxFeatureTypeInApp(
                PrivacySandboxFeatureType.PRIVACY_SANDBOX_RECONSENT_FF);
    }

    private void runAndVerifyPrivacySandboxFeatureTypeInApp(PrivacySandboxFeatureType featureType)
            throws Exception {
        mConsentManager.setPrivacySandboxFeatureTypeInApp(featureType);
        expect.withMessage("PrivacySandboxFeatureType: %s", featureType)
                .that(mConsentManager.getCurrentPrivacySandboxFeature())
                .isEqualTo(featureType);
    }

    @Test
    public void testCurrentPrivacySandboxFeature_ppapiOnly() throws Exception {
        int consentSourceOfTruth = Flags.PPAPI_ONLY;
        ConsentManager spyConsentManager =
                getSpiedConsentManagerForMigrationTesting(
                        /* isGiven */ false, consentSourceOfTruth);

        assertThat(spyConsentManager.getCurrentPrivacySandboxFeature())
                .isEqualTo(PrivacySandboxFeatureType.PRIVACY_SANDBOX_UNSUPPORTED);
        verify(mMockIAdServicesManager, never()).getCurrentPrivacySandboxFeature();

        spyConsentManager.setCurrentPrivacySandboxFeature(
                PrivacySandboxFeatureType.PRIVACY_SANDBOX_FIRST_CONSENT);
        assertThat(spyConsentManager.getCurrentPrivacySandboxFeature())
                .isEqualTo(PrivacySandboxFeatureType.PRIVACY_SANDBOX_FIRST_CONSENT);

        verify(mMockIAdServicesManager, never()).getCurrentPrivacySandboxFeature();
        verify(mMockIAdServicesManager, never()).setCurrentPrivacySandboxFeature(anyString());
    }

    @Test
    @ExpectErrorLogUtilWithExceptionCall(
            errorCode = AD_SERVICES_ERROR_REPORTED__ERROR_CODE__PRIVACY_SANDBOX_SAVE_FAILURE)
    public void testCurrentPrivacySandboxFeature_SystemServerOnly() throws Exception {
        int consentSourceOfTruth = Flags.SYSTEM_SERVER_ONLY;
        ConsentManager spyConsentManager =
                getSpiedConsentManagerForMigrationTesting(
                        /* isGiven */ false, consentSourceOfTruth);
        assertThat(spyConsentManager.getCurrentPrivacySandboxFeature())
                .isEqualTo(PrivacySandboxFeatureType.PRIVACY_SANDBOX_UNSUPPORTED);
        verify(mMockIAdServicesManager).getCurrentPrivacySandboxFeature();
        doReturn(PrivacySandboxFeatureType.PRIVACY_SANDBOX_FIRST_CONSENT.name())
                .when(mMockIAdServicesManager)
                .getCurrentPrivacySandboxFeature();
        spyConsentManager.setCurrentPrivacySandboxFeature(
                PrivacySandboxFeatureType.PRIVACY_SANDBOX_FIRST_CONSENT);
        assertThat(spyConsentManager.getCurrentPrivacySandboxFeature())
                .isEqualTo(PrivacySandboxFeatureType.PRIVACY_SANDBOX_FIRST_CONSENT);

        doReturn(PrivacySandboxFeatureType.PRIVACY_SANDBOX_RECONSENT.name())
                .when(mMockIAdServicesManager)
                .getCurrentPrivacySandboxFeature();
        spyConsentManager.setCurrentPrivacySandboxFeature(
                PrivacySandboxFeatureType.PRIVACY_SANDBOX_RECONSENT);
        assertThat(spyConsentManager.getCurrentPrivacySandboxFeature())
                .isEqualTo(PrivacySandboxFeatureType.PRIVACY_SANDBOX_RECONSENT);

        doReturn(PrivacySandboxFeatureType.PRIVACY_SANDBOX_UNSUPPORTED.name())
                .when(mMockIAdServicesManager)
                .getCurrentPrivacySandboxFeature();
        spyConsentManager.setCurrentPrivacySandboxFeature(
                PrivacySandboxFeatureType.PRIVACY_SANDBOX_UNSUPPORTED);
        assertThat(spyConsentManager.getCurrentPrivacySandboxFeature())
                .isEqualTo(PrivacySandboxFeatureType.PRIVACY_SANDBOX_UNSUPPORTED);

        assertThat(
                        mConsentDatastore.getBoolean(
                                PrivacySandboxFeatureType.PRIVACY_SANDBOX_UNSUPPORTED.name()))
                .isNull();
        assertThat(
                        mConsentDatastore.getBoolean(
                                PrivacySandboxFeatureType.PRIVACY_SANDBOX_FIRST_CONSENT.name()))
                .isNull();
        assertThat(
                        mConsentDatastore.getBoolean(
                                PrivacySandboxFeatureType.PRIVACY_SANDBOX_RECONSENT.name()))
                .isNull();
    }

    @Test
    @ExpectErrorLogUtilWithExceptionCall(
            errorCode = AD_SERVICES_ERROR_REPORTED__ERROR_CODE__PRIVACY_SANDBOX_SAVE_FAILURE)
    public void testCurrentPrivacySandboxFeature_PpApiAndSystemServer() throws Exception {
        int consentSourceOfTruth = Flags.PPAPI_AND_SYSTEM_SERVER;
        ConsentManager spyConsentManager =
                getSpiedConsentManagerForMigrationTesting(
                        /* isGiven */ false, consentSourceOfTruth);

        assertThat(spyConsentManager.getCurrentPrivacySandboxFeature())
                .isEqualTo(PrivacySandboxFeatureType.PRIVACY_SANDBOX_UNSUPPORTED);
        verify(mMockIAdServicesManager).getCurrentPrivacySandboxFeature();
        doReturn(PrivacySandboxFeatureType.PRIVACY_SANDBOX_FIRST_CONSENT.name())
                .when(mMockIAdServicesManager)
                .getCurrentPrivacySandboxFeature();
        spyConsentManager.setCurrentPrivacySandboxFeature(
                PrivacySandboxFeatureType.PRIVACY_SANDBOX_FIRST_CONSENT);
        assertThat(spyConsentManager.getCurrentPrivacySandboxFeature())
                .isEqualTo(PrivacySandboxFeatureType.PRIVACY_SANDBOX_FIRST_CONSENT);

        doReturn(PrivacySandboxFeatureType.PRIVACY_SANDBOX_UNSUPPORTED.name())
                .when(mMockIAdServicesManager)
                .getCurrentPrivacySandboxFeature();
        spyConsentManager.setCurrentPrivacySandboxFeature(
                PrivacySandboxFeatureType.PRIVACY_SANDBOX_UNSUPPORTED);
        assertThat(spyConsentManager.getCurrentPrivacySandboxFeature())
                .isEqualTo(PrivacySandboxFeatureType.PRIVACY_SANDBOX_UNSUPPORTED);

        doReturn(PrivacySandboxFeatureType.PRIVACY_SANDBOX_RECONSENT.name())
                .when(mMockIAdServicesManager)
                .getCurrentPrivacySandboxFeature();
        spyConsentManager.setCurrentPrivacySandboxFeature(
                PrivacySandboxFeatureType.PRIVACY_SANDBOX_RECONSENT);
        assertThat(spyConsentManager.getCurrentPrivacySandboxFeature())
                .isEqualTo(PrivacySandboxFeatureType.PRIVACY_SANDBOX_RECONSENT);

        // Only the last set bit is true.
        assertThat(
                        mConsentDatastore.getBoolean(
                                PrivacySandboxFeatureType.PRIVACY_SANDBOX_UNSUPPORTED.name()))
                .isFalse();
        assertThat(
                        mConsentDatastore.getBoolean(
                                PrivacySandboxFeatureType.PRIVACY_SANDBOX_FIRST_CONSENT.name()))
                .isFalse();
        assertThat(
                        mConsentDatastore.getBoolean(
                                PrivacySandboxFeatureType.PRIVACY_SANDBOX_RECONSENT.name()))
                .isTrue();
    }

    @Test
    public void testCurrentPrivacySandboxFeature_appSearchOnly() throws Exception {
        int consentSourceOfTruth = Flags.APPSEARCH_ONLY;
        when(mMockFlags.getEnableAppsearchConsentData()).thenReturn(true);
        ConsentManager spyConsentManager =
                getSpiedConsentManagerForMigrationTesting(
                        /* isGiven */ false, consentSourceOfTruth);

        when(mAppSearchConsentManagerMock.getCurrentPrivacySandboxFeature())
                .thenReturn(PrivacySandboxFeatureType.PRIVACY_SANDBOX_UNSUPPORTED);
        assertThat(spyConsentManager.getCurrentPrivacySandboxFeature())
                .isEqualTo(PrivacySandboxFeatureType.PRIVACY_SANDBOX_UNSUPPORTED);

        spyConsentManager.setCurrentPrivacySandboxFeature(
                PrivacySandboxFeatureType.PRIVACY_SANDBOX_FIRST_CONSENT);
        verify(mAppSearchConsentManagerMock)
                .setCurrentPrivacySandboxFeature(
                        eq(PrivacySandboxFeatureType.PRIVACY_SANDBOX_FIRST_CONSENT));
        when(mAppSearchConsentManagerMock.getCurrentPrivacySandboxFeature())
                .thenReturn(PrivacySandboxFeatureType.PRIVACY_SANDBOX_FIRST_CONSENT);
        assertThat(spyConsentManager.getCurrentPrivacySandboxFeature())
                .isEqualTo(PrivacySandboxFeatureType.PRIVACY_SANDBOX_FIRST_CONSENT);

        verify(mMockIAdServicesManager, never()).getCurrentPrivacySandboxFeature();
        verify(mMockIAdServicesManager, never()).setCurrentPrivacySandboxFeature(anyString());
    }

    @Test
    public void isAdIdEnabledTest_SystemServerOnly() throws Exception {
        int consentSourceOfTruth = Flags.SYSTEM_SERVER_ONLY;
        ConsentManager spyConsentManager =
                getSpiedConsentManagerForMigrationTesting(
                        /* isGiven */ false, consentSourceOfTruth);

        assertThat(spyConsentManager.isAdIdEnabled()).isFalse();

        verify(mMockIAdServicesManager).isAdIdEnabled();

        doReturn(true).when(mMockIAdServicesManager).isAdIdEnabled();
        spyConsentManager.setAdIdEnabled(true);

        assertThat(spyConsentManager.isAdIdEnabled()).isTrue();

        verify(mMockIAdServicesManager, times(2)).isAdIdEnabled();
        verify(mMockIAdServicesManager).setAdIdEnabled(anyBoolean());
    }

    @Test
    public void isAdIdEnabledTest_PpApiAndSystemServer() throws Exception {
        int consentSourceOfTruth = Flags.PPAPI_AND_SYSTEM_SERVER;
        ConsentManager spyConsentManager =
                getSpiedConsentManagerForMigrationTesting(
                        /* isGiven */ false, consentSourceOfTruth);

        Boolean isAdIdEnabled = spyConsentManager.isAdIdEnabled();

        assertThat(isAdIdEnabled).isFalse();

        verify(mMockIAdServicesManager).isAdIdEnabled();

        doReturn(true).when(mMockIAdServicesManager).isAdIdEnabled();
        spyConsentManager.setAdIdEnabled(true);

        assertThat(spyConsentManager.isAdIdEnabled()).isTrue();

        verify(mMockIAdServicesManager, times(2)).isAdIdEnabled();
        verify(mMockIAdServicesManager).setAdIdEnabled(anyBoolean());
    }

    @Test
    public void isAdIdEnabledTest_appSearchOnly() throws Exception {
        int consentSourceOfTruth = Flags.APPSEARCH_ONLY;
        when(mMockFlags.getEnableAppsearchConsentData()).thenReturn(true);
        ConsentManager spyConsentManager =
                getSpiedConsentManagerForMigrationTesting(
                        /* isGiven */ false, consentSourceOfTruth);

        doReturn(false).when(mAppSearchConsentManagerMock).isAdIdEnabled();
        assertThat(spyConsentManager.isAdIdEnabled()).isFalse();
        verify(mAppSearchConsentManagerMock).isAdIdEnabled();

        doReturn(true).when(mAppSearchConsentManagerMock).isAdIdEnabled();
        spyConsentManager.setAdIdEnabled(true);

        assertThat(spyConsentManager.isAdIdEnabled()).isTrue();

        verify(mAppSearchConsentManagerMock, times(2)).isAdIdEnabled();
        verify(mAppSearchConsentManagerMock).setAdIdEnabled(anyBoolean());
    }

    @Test
    public void isU18AccountTest_SystemServerOnly() throws Exception {
        int consentSourceOfTruth = Flags.SYSTEM_SERVER_ONLY;
        ConsentManager spyConsentManager =
                getSpiedConsentManagerForMigrationTesting(
                        /* isGiven */ false, consentSourceOfTruth);

        assertThat(spyConsentManager.isU18Account()).isFalse();

        verify(mMockIAdServicesManager).isU18Account();

        doReturn(true).when(mMockIAdServicesManager).isU18Account();
        spyConsentManager.setU18Account(true);

        assertThat(spyConsentManager.isU18Account()).isTrue();

        verify(mMockIAdServicesManager, times(2)).isU18Account();
        verify(mMockIAdServicesManager).setU18Account(anyBoolean());
    }

    @Test
    public void isU18AccountTest_PpApiAndSystemServer() throws Exception {
        int consentSourceOfTruth = Flags.PPAPI_AND_SYSTEM_SERVER;
        ConsentManager spyConsentManager =
                getSpiedConsentManagerForMigrationTesting(
                        /* isGiven */ false, consentSourceOfTruth);

        Boolean isU18Account = spyConsentManager.isU18Account();

        assertThat(isU18Account).isFalse();

        verify(mMockIAdServicesManager).isU18Account();

        doReturn(true).when(mMockIAdServicesManager).isU18Account();
        spyConsentManager.setU18Account(true);

        assertThat(spyConsentManager.isU18Account()).isTrue();

        verify(mMockIAdServicesManager, times(2)).isU18Account();
        verify(mMockIAdServicesManager).setU18Account(anyBoolean());
    }

    @Test
    public void isU18AccountTest_appSearchOnly() throws Exception {
        int consentSourceOfTruth = Flags.APPSEARCH_ONLY;
        when(mMockFlags.getEnableAppsearchConsentData()).thenReturn(true);
        ConsentManager spyConsentManager =
                getSpiedConsentManagerForMigrationTesting(
                        /* isGiven */ false, consentSourceOfTruth);

        doReturn(false).when(mAppSearchConsentManagerMock).isU18Account();
        assertThat(spyConsentManager.isU18Account()).isFalse();
        verify(mAppSearchConsentManagerMock).isU18Account();

        doReturn(true).when(mAppSearchConsentManagerMock).isU18Account();
        spyConsentManager.setU18Account(true);

        assertThat(spyConsentManager.isU18Account()).isTrue();

        verify(mAppSearchConsentManagerMock, times(2)).isU18Account();
        verify(mAppSearchConsentManagerMock).setU18Account(anyBoolean());
    }

    @Test
    public void isEntryPointEnabledTest_SystemServerOnly() throws Exception {
        int consentSourceOfTruth = Flags.SYSTEM_SERVER_ONLY;
        ConsentManager spyConsentManager =
                getSpiedConsentManagerForMigrationTesting(
                        /* isGiven */ false, consentSourceOfTruth);

        assertThat(spyConsentManager.isEntryPointEnabled()).isFalse();

        verify(mMockIAdServicesManager).isEntryPointEnabled();

        doReturn(true).when(mMockIAdServicesManager).isEntryPointEnabled();
        spyConsentManager.setEntryPointEnabled(true);

        assertThat(spyConsentManager.isEntryPointEnabled()).isTrue();

        verify(mMockIAdServicesManager, times(2)).isEntryPointEnabled();
        verify(mMockIAdServicesManager).setEntryPointEnabled(anyBoolean());
    }

    @Test
    public void isEntryPointEnabledTest_PpApiAndSystemServer() throws Exception {
        int consentSourceOfTruth = Flags.PPAPI_AND_SYSTEM_SERVER;
        ConsentManager spyConsentManager =
                getSpiedConsentManagerForMigrationTesting(
                        /* isGiven */ false, consentSourceOfTruth);

        Boolean isEntryPointEnabled = spyConsentManager.isEntryPointEnabled();

        assertThat(isEntryPointEnabled).isFalse();

        verify(mMockIAdServicesManager).isEntryPointEnabled();

        doReturn(true).when(mMockIAdServicesManager).isEntryPointEnabled();
        spyConsentManager.setEntryPointEnabled(true);

        assertThat(spyConsentManager.isEntryPointEnabled()).isTrue();

        verify(mMockIAdServicesManager, times(2)).isEntryPointEnabled();
        verify(mMockIAdServicesManager).setEntryPointEnabled(anyBoolean());
    }

    @Test
    public void isEntryPointEnabledTest_appSearchOnly() throws Exception {
        int consentSourceOfTruth = Flags.APPSEARCH_ONLY;
        when(mMockFlags.getEnableAppsearchConsentData()).thenReturn(true);
        ConsentManager spyConsentManager =
                getSpiedConsentManagerForMigrationTesting(
                        /* isGiven */ false, consentSourceOfTruth);

        doReturn(false).when(mAppSearchConsentManagerMock).isEntryPointEnabled();
        assertThat(spyConsentManager.isEntryPointEnabled()).isFalse();
        verify(mAppSearchConsentManagerMock).isEntryPointEnabled();

        doReturn(true).when(mAppSearchConsentManagerMock).isEntryPointEnabled();
        spyConsentManager.setEntryPointEnabled(true);

        assertThat(spyConsentManager.isEntryPointEnabled()).isTrue();

        verify(mAppSearchConsentManagerMock, times(2)).isEntryPointEnabled();
        verify(mAppSearchConsentManagerMock).setEntryPointEnabled(anyBoolean());
    }

    @Test
    public void isAdultAccountTest_SystemServerOnly() throws Exception {
        int consentSourceOfTruth = Flags.SYSTEM_SERVER_ONLY;
        ConsentManager spyConsentManager =
                getSpiedConsentManagerForMigrationTesting(
                        /* isGiven */ false, consentSourceOfTruth);

        assertThat(spyConsentManager.isAdultAccount()).isFalse();

        verify(mMockIAdServicesManager).isAdultAccount();

        doReturn(true).when(mMockIAdServicesManager).isAdultAccount();
        spyConsentManager.setAdultAccount(true);

        assertThat(spyConsentManager.isAdultAccount()).isTrue();

        verify(mMockIAdServicesManager, times(2)).isAdultAccount();
        verify(mMockIAdServicesManager).setAdultAccount(anyBoolean());
    }

    @Test
    public void isAdultAccountTest_PpApiAndSystemServer() throws Exception {
        int consentSourceOfTruth = Flags.PPAPI_AND_SYSTEM_SERVER;
        ConsentManager spyConsentManager =
                getSpiedConsentManagerForMigrationTesting(
                        /* isGiven */ false, consentSourceOfTruth);

        Boolean isAdultAccount = spyConsentManager.isAdultAccount();

        assertThat(isAdultAccount).isFalse();

        verify(mMockIAdServicesManager).isAdultAccount();

        doReturn(true).when(mMockIAdServicesManager).isAdultAccount();
        spyConsentManager.setAdultAccount(true);

        assertThat(spyConsentManager.isAdultAccount()).isTrue();

        verify(mMockIAdServicesManager, times(2)).isAdultAccount();
        verify(mMockIAdServicesManager).setAdultAccount(anyBoolean());
    }

    @Test
    public void isAdultAccountTest_appSearchOnly() throws Exception {
        int consentSourceOfTruth = Flags.APPSEARCH_ONLY;
        when(mMockFlags.getEnableAppsearchConsentData()).thenReturn(true);
        ConsentManager spyConsentManager =
                getSpiedConsentManagerForMigrationTesting(
                        /* isGiven */ false, consentSourceOfTruth);

        doReturn(false).when(mAppSearchConsentManagerMock).isAdultAccount();
        assertThat(spyConsentManager.isAdultAccount()).isFalse();
        verify(mAppSearchConsentManagerMock).isAdultAccount();

        doReturn(true).when(mAppSearchConsentManagerMock).isAdultAccount();
        spyConsentManager.setAdultAccount(true);

        assertThat(spyConsentManager.isAdultAccount()).isTrue();

        verify(mAppSearchConsentManagerMock, times(2)).isAdultAccount();
        verify(mAppSearchConsentManagerMock).setAdultAccount(anyBoolean());
    }

    @Test
    public void testDefaultConsentRecorded_PpApiOnly() throws Exception {
        int consentSourceOfTruth = Flags.PPAPI_ONLY;
        ConsentManager spyConsentManager =
                getSpiedConsentManagerForMigrationTesting(
                        /* isGiven */ false, consentSourceOfTruth);

        assertThat(spyConsentManager.getDefaultConsent()).isNull();

        verify(mMockIAdServicesManager, never()).getDefaultConsent();

        spyConsentManager.recordDefaultConsent(true);

        assertThat(spyConsentManager.getDefaultConsent()).isTrue();

        verify(mMockIAdServicesManager, never()).getDefaultConsent();
        verify(mMockIAdServicesManager, never()).recordDefaultConsent(anyBoolean());
    }

    @Test
    public void testDefaultConsentRecorded_SystemServerOnly() throws Exception {
        int consentSourceOfTruth = Flags.SYSTEM_SERVER_ONLY;
        ConsentManager spyConsentManager =
                getSpiedConsentManagerForMigrationTesting(
                        /* isGiven */ false, consentSourceOfTruth);

        assertThat(spyConsentManager.getDefaultConsent()).isFalse();

        verify(mMockIAdServicesManager).getDefaultConsent();

        doReturn(true).when(mMockIAdServicesManager).getDefaultConsent();
        spyConsentManager.recordDefaultConsent(true);

        assertThat(spyConsentManager.getDefaultConsent()).isTrue();

        verify(mMockIAdServicesManager, times(2)).getDefaultConsent();
        verify(mMockIAdServicesManager).recordDefaultConsent(eq(true));

        // Verify default consent is not set in PPAPI
        assertThat(mConsentDatastore.getBoolean(DEFAULT_CONSENT)).isNull();
    }

    @Test
    public void testDefaultConsentRecorded_PpApiAndSystemServer() throws Exception {
        int consentSourceOfTruth = Flags.PPAPI_AND_SYSTEM_SERVER;
        ConsentManager spyConsentManager =
                getSpiedConsentManagerForMigrationTesting(
                        /* isGiven */ false, consentSourceOfTruth);

        Boolean getDefaultConsent = spyConsentManager.getDefaultConsent();

        assertThat(getDefaultConsent).isFalse();

        verify(mMockIAdServicesManager).getDefaultConsent();

        doReturn(true).when(mMockIAdServicesManager).getDefaultConsent();
        spyConsentManager.recordDefaultConsent(true);

        assertThat(spyConsentManager.getDefaultConsent()).isTrue();

        verify(mMockIAdServicesManager, times(2)).getDefaultConsent();
        verify(mMockIAdServicesManager).recordDefaultConsent(eq(true));

        // Verify default consent is also set in PPAPI
        assertThat(mConsentDatastore.getBoolean(DEFAULT_CONSENT)).isTrue();
    }

    @Test
    public void wasU18NotificationDisplayedTest_SystemServerOnly() throws Exception {
        int consentSourceOfTruth = Flags.SYSTEM_SERVER_ONLY;
        ConsentManager spyConsentManager =
                getSpiedConsentManagerForMigrationTesting(
                        /* isGiven */ false, consentSourceOfTruth);

        assertThat(spyConsentManager.wasU18NotificationDisplayed()).isFalse();

        verify(mMockIAdServicesManager).wasU18NotificationDisplayed();

        doReturn(true).when(mMockIAdServicesManager).wasU18NotificationDisplayed();
        spyConsentManager.setU18NotificationDisplayed(true);

        assertThat(spyConsentManager.wasU18NotificationDisplayed()).isTrue();

        verify(mMockIAdServicesManager, times(2)).wasU18NotificationDisplayed();
        verify(mMockIAdServicesManager).setU18NotificationDisplayed(anyBoolean());
    }

    @Test
    public void wasU18NotificationDisplayedTest_PpApiAndSystemServer() throws Exception {
        int consentSourceOfTruth = Flags.PPAPI_AND_SYSTEM_SERVER;
        ConsentManager spyConsentManager =
                getSpiedConsentManagerForMigrationTesting(
                        /* isGiven */ false, consentSourceOfTruth);

        Boolean wasU18NotificationDisplayed = spyConsentManager.wasU18NotificationDisplayed();

        assertThat(wasU18NotificationDisplayed).isFalse();

        verify(mMockIAdServicesManager).wasU18NotificationDisplayed();

        doReturn(true).when(mMockIAdServicesManager).wasU18NotificationDisplayed();
        spyConsentManager.setU18NotificationDisplayed(true);

        assertThat(spyConsentManager.wasU18NotificationDisplayed()).isTrue();

        verify(mMockIAdServicesManager, times(2)).wasU18NotificationDisplayed();
        verify(mMockIAdServicesManager).setU18NotificationDisplayed(anyBoolean());
    }

    @Test
    public void wasU18NotificationDisplayedTest_appSearchOnly() throws Exception {
        int consentSourceOfTruth = Flags.APPSEARCH_ONLY;
        when(mMockFlags.getEnableAppsearchConsentData()).thenReturn(true);
        ConsentManager spyConsentManager =
                getSpiedConsentManagerForMigrationTesting(
                        /* isGiven */ false, consentSourceOfTruth);

        doReturn(false).when(mAppSearchConsentManagerMock).wasU18NotificationDisplayed();
        assertThat(spyConsentManager.wasU18NotificationDisplayed()).isFalse();
        verify(mAppSearchConsentManagerMock).wasU18NotificationDisplayed();

        doReturn(true).when(mAppSearchConsentManagerMock).wasU18NotificationDisplayed();
        spyConsentManager.setU18NotificationDisplayed(true);

        assertThat(spyConsentManager.wasU18NotificationDisplayed()).isTrue();

        verify(mAppSearchConsentManagerMock, times(2)).wasU18NotificationDisplayed();
        verify(mAppSearchConsentManagerMock).setU18NotificationDisplayed(anyBoolean());
    }

    @Test
    public void testGetUx_PpApiOnly() throws Exception {
        int consentSourceOfTruth = Flags.PPAPI_ONLY;
        ConsentManager spyConsentManager =
                getSpiedConsentManagerForMigrationTesting(
                        /* isGiven */ false, consentSourceOfTruth);

        for (PrivacySandboxUxCollection ux : PrivacySandboxUxCollection.values()) {
            doReturn(ux).when(mUxStatesDaoMock).getUx();
            assertThat(spyConsentManager.getUx()).isEqualTo(ux);

            spyConsentManager.setUx(ux);
        }

        verify(mUxStatesDaoMock, times(UX_TYPE_COUNT)).getUx();
        verify(mUxStatesDaoMock, times(UX_TYPE_COUNT)).setUx(any());
    }

    @Test
    public void getUxTest_SystemServerOnly() throws Exception {
        int consentSourceOfTruth = Flags.SYSTEM_SERVER_ONLY;
        ConsentManager spyConsentManager =
                getSpiedConsentManagerForMigrationTesting(
                        /* isGiven */ false, consentSourceOfTruth);

        for (PrivacySandboxUxCollection ux : PrivacySandboxUxCollection.values()) {
            doReturn(ux.toString()).when(mMockIAdServicesManager).getUx();
            assertThat(spyConsentManager.getUx()).isEqualTo(ux);

            spyConsentManager.setUx(ux);
        }

        verify(mMockIAdServicesManager, times(UX_TYPE_COUNT)).getUx();
        verify(mMockIAdServicesManager, times(UX_TYPE_COUNT)).setUx(any());
    }

    @Test
    public void getUxTest_PpApiAndSystemServer() throws Exception {
        int consentSourceOfTruth = Flags.PPAPI_AND_SYSTEM_SERVER;
        ConsentManager spyConsentManager =
                getSpiedConsentManagerForMigrationTesting(
                        /* isGiven */ false, consentSourceOfTruth);

        for (PrivacySandboxUxCollection ux : PrivacySandboxUxCollection.values()) {
            doReturn(ux.toString()).when(mMockIAdServicesManager).getUx();
            assertThat(spyConsentManager.getUx()).isEqualTo(ux);

            spyConsentManager.setUx(ux);
        }

        verify(mMockIAdServicesManager, times(UX_TYPE_COUNT)).getUx();
        verify(mMockIAdServicesManager, times(UX_TYPE_COUNT)).setUx(any());
    }

    @Test
    public void getUxTest_appSearchOnly() throws Exception {
        int consentSourceOfTruth = Flags.APPSEARCH_ONLY;
        when(mMockFlags.getEnableAppsearchConsentData()).thenReturn(true);
        ConsentManager spyConsentManager =
                getSpiedConsentManagerForMigrationTesting(
                        /* isGiven */ false, consentSourceOfTruth);

        for (PrivacySandboxUxCollection ux : PrivacySandboxUxCollection.values()) {
            doReturn(ux).when(mAppSearchConsentManagerMock).getUx();
            assertThat(spyConsentManager.getUx()).isEqualTo(ux);

            spyConsentManager.setUx(ux);
        }

        verify(mAppSearchConsentManagerMock, times(UX_TYPE_COUNT)).getUx();
        verify(mAppSearchConsentManagerMock, times(UX_TYPE_COUNT)).setUx(any());
    }

    @Test
    public void testGetEnrollmentChannel_ppapiOnly() throws Exception {
        int consentSourceOfTruth = Flags.PPAPI_ONLY;
        ConsentManager spyConsentManager =
                getSpiedConsentManagerForMigrationTesting(
                        /* isGiven */ false, consentSourceOfTruth);

        for (PrivacySandboxUxCollection ux : PrivacySandboxUxCollection.values()) {
            for (PrivacySandboxEnrollmentChannelCollection channel :
                    ux.getEnrollmentChannelCollection()) {
                doReturn(channel).when(mUxStatesDaoMock).getEnrollmentChannel(ux);
                assertThat(spyConsentManager.getEnrollmentChannel(ux)).isEqualTo(channel);

                spyConsentManager.setEnrollmentChannel(ux, channel);
            }
        }

        verify(mUxStatesDaoMock, times(ENROLLMENT_CHANNEL_COUNT)).getEnrollmentChannel(any());
        verify(mUxStatesDaoMock, times(ENROLLMENT_CHANNEL_COUNT))
                .setEnrollmentChannel(any(), any());
    }

    @Test
    public void getEnrollmentChannel_SystemServerOnly() throws Exception {
        int consentSourceOfTruth = Flags.SYSTEM_SERVER_ONLY;
        ConsentManager spyConsentManager =
                getSpiedConsentManagerForMigrationTesting(
                        /* isGiven */ false, consentSourceOfTruth);

        for (PrivacySandboxUxCollection ux : PrivacySandboxUxCollection.values()) {
            for (PrivacySandboxEnrollmentChannelCollection channel :
                    ux.getEnrollmentChannelCollection()) {
                doReturn(channel.toString()).when(mMockIAdServicesManager).getEnrollmentChannel();
                assertThat(spyConsentManager.getEnrollmentChannel(ux)).isEqualTo(channel);

                spyConsentManager.setEnrollmentChannel(ux, channel);
            }
        }

        verify(mMockIAdServicesManager, times(ENROLLMENT_CHANNEL_COUNT)).getEnrollmentChannel();
        verify(mMockIAdServicesManager, times(ENROLLMENT_CHANNEL_COUNT))
                .setEnrollmentChannel(anyString());
    }

    @Test
    public void getEnrollmentChannel_PpApiAndSystemServer() throws Exception {
        int consentSourceOfTruth = Flags.PPAPI_AND_SYSTEM_SERVER;
        ConsentManager spyConsentManager =
                getSpiedConsentManagerForMigrationTesting(
                        /* isGiven */ false, consentSourceOfTruth);

        for (PrivacySandboxUxCollection ux : PrivacySandboxUxCollection.values()) {
            for (PrivacySandboxEnrollmentChannelCollection channel :
                    ux.getEnrollmentChannelCollection()) {
                doReturn(channel.toString()).when(mMockIAdServicesManager).getEnrollmentChannel();
                assertThat(spyConsentManager.getEnrollmentChannel(ux)).isEqualTo(channel);

                spyConsentManager.setEnrollmentChannel(ux, channel);
            }
        }

        verify(mMockIAdServicesManager, times(ENROLLMENT_CHANNEL_COUNT)).getEnrollmentChannel();
        verify(mMockIAdServicesManager, times(ENROLLMENT_CHANNEL_COUNT))
                .setEnrollmentChannel(anyString());
    }

    @Test
    @SuppressWarnings("NewApi")
    public void getEnrollmentChannel_appSearchOnly() throws Exception {
        int consentSourceOfTruth = Flags.APPSEARCH_ONLY;
        when(mMockFlags.getEnableAppsearchConsentData()).thenReturn(true);
        ConsentManager spyConsentManager =
                getSpiedConsentManagerForMigrationTesting(
                        /* isGiven */ false, consentSourceOfTruth);

        for (PrivacySandboxUxCollection ux : PrivacySandboxUxCollection.values()) {
            for (PrivacySandboxEnrollmentChannelCollection channel :
                    ux.getEnrollmentChannelCollection()) {
                doReturn(channel).when(mAppSearchConsentManagerMock).getEnrollmentChannel(ux);
                assertThat(spyConsentManager.getEnrollmentChannel(ux)).isEqualTo(channel);

                spyConsentManager.setEnrollmentChannel(ux, channel);
            }
        }

        verify(mAppSearchConsentManagerMock, times(ENROLLMENT_CHANNEL_COUNT))
                .getEnrollmentChannel(any());
        verify(mAppSearchConsentManagerMock, times(ENROLLMENT_CHANNEL_COUNT))
                .setEnrollmentChannel(any(), any());
    }

    @Test
    public void testPasNotificationDisplayedRecorded_PpApiOnly() throws Exception {
        int consentSourceOfTruth = Flags.PPAPI_ONLY;
        ConsentManager spyConsentManager =
                getSpiedConsentManagerForMigrationTesting(
                        /* isGiven */ false, consentSourceOfTruth);

        assertThat(spyConsentManager.wasPasNotificationDisplayed()).isFalse();

        verify(mMockIAdServicesManager, never()).wasPasNotificationDisplayed();

        spyConsentManager.recordPasNotificationDisplayed(true);

        assertThat(spyConsentManager.wasPasNotificationDisplayed()).isTrue();

        verify(mMockIAdServicesManager, never()).wasPasNotificationDisplayed();
        verify(mMockIAdServicesManager, never()).recordPasNotificationDisplayed(true);
    }

    @Test
    public void testPasNotificationDisplayedRecorded_SystemServerOnly() throws Exception {
        int consentSourceOfTruth = Flags.SYSTEM_SERVER_ONLY;
        ConsentManager spyConsentManager =
                getSpiedConsentManagerForMigrationTesting(
                        /* isGiven */ false, consentSourceOfTruth);

        assertThat(spyConsentManager.wasPasNotificationDisplayed()).isFalse();

        verify(mMockIAdServicesManager).wasPasNotificationDisplayed();

        doReturn(true).when(mMockIAdServicesManager).wasPasNotificationDisplayed();
        spyConsentManager.recordPasNotificationDisplayed(true);

        assertThat(spyConsentManager.wasPasNotificationDisplayed()).isTrue();

        verify(mMockIAdServicesManager, times(2)).wasPasNotificationDisplayed();
        verify(mMockIAdServicesManager).recordPasNotificationDisplayed(true);

        // Verify notificationDisplayed is not set in PPAPI
        assertThat(mConsentDatastore.getBoolean(PAS_NOTIFICATION_DISPLAYED_ONCE)).isFalse();
    }

    @Test
    public void testPasNotificationDisplayedRecorded_PpApiAndSystemServer() throws Exception {
        int consentSourceOfTruth = Flags.PPAPI_AND_SYSTEM_SERVER;
        ConsentManager spyConsentManager =
                getSpiedConsentManagerForMigrationTesting(
                        /* isGiven */ false, consentSourceOfTruth);

        Boolean wasPasNotificationDisplayed = spyConsentManager.wasPasNotificationDisplayed();

        assertThat(wasPasNotificationDisplayed).isFalse();

        verify(mMockIAdServicesManager).wasPasNotificationDisplayed();

        doReturn(true).when(mMockIAdServicesManager).wasPasNotificationDisplayed();
        spyConsentManager.recordPasNotificationDisplayed(true);

        assertThat(spyConsentManager.wasPasNotificationDisplayed()).isTrue();

        verify(mMockIAdServicesManager, times(2)).wasPasNotificationDisplayed();
        verify(mMockIAdServicesManager).recordPasNotificationDisplayed(true);

        // Verify notificationDisplayed is also set in PPAPI
        assertThat(mConsentDatastore.getBoolean(PAS_NOTIFICATION_DISPLAYED_ONCE)).isTrue();
    }

    @Test
    public void testIsPasFledgeConsentGiven_happycase() throws Exception {
        when(mMockFlags.getPasUxEnabled()).thenReturn(true);
        int consentSourceOfTruth = Flags.SYSTEM_SERVER_ONLY;
        ConsentManager spyConsentManager =
                getSpiedConsentManagerForMigrationTesting(/* isGiven */ true, consentSourceOfTruth);
        doReturn(true).when(mMockIAdServicesManager).wasPasNotificationDisplayed();
        doReturn(AdServicesApiConsent.GIVEN)
                .when(spyConsentManager)
                .getConsent(eq(AdServicesApiType.FLEDGE));
        assertThat(spyConsentManager.isPasFledgeConsentGiven()).isTrue();
    }

    @Test
    public void testIsPasFledgeConsentGiven_NotificationNotDisplayed() throws Exception {
        when(mMockFlags.getPasUxEnabled()).thenReturn(true);
        int consentSourceOfTruth = Flags.SYSTEM_SERVER_ONLY;
        ConsentManager spyConsentManager =
                getSpiedConsentManagerForMigrationTesting(/* isGiven */ true, consentSourceOfTruth);
        doReturn(false).when(mMockIAdServicesManager).wasPasNotificationDisplayed();
        doReturn(AdServicesApiConsent.GIVEN)
                .when(spyConsentManager)
                .getConsent(eq(AdServicesApiType.FLEDGE));
        assertThat(spyConsentManager.isPasFledgeConsentGiven()).isFalse();
    }

    @Test
    public void testIsPasFledgeConsentGiven_FledgeRevoken() throws Exception {
        when(mMockFlags.getPasUxEnabled()).thenReturn(true);
        int consentSourceOfTruth = Flags.SYSTEM_SERVER_ONLY;
        ConsentManager spyConsentManager =
                getSpiedConsentManagerForMigrationTesting(/* isGiven */ true, consentSourceOfTruth);
        doReturn(true).when(mMockIAdServicesManager).wasPasNotificationDisplayed();
        doReturn(AdServicesApiConsent.REVOKED)
                .when(spyConsentManager)
                .getConsent(eq(AdServicesApiType.FLEDGE));
        assertThat(spyConsentManager.isPasFledgeConsentGiven()).isFalse();
    }

    @Test
    public void testIsPasFledgeConsentGiven_pasNotEnabled() throws Exception {
        when(mMockFlags.getPasUxEnabled()).thenReturn(false);
        int consentSourceOfTruth = Flags.SYSTEM_SERVER_ONLY;
        ConsentManager spyConsentManager =
                getSpiedConsentManagerForMigrationTesting(/* isGiven */ true, consentSourceOfTruth);
        doReturn(true).when(mMockIAdServicesManager).wasPasNotificationDisplayed();
        doReturn(AdServicesApiConsent.GIVEN)
                .when(spyConsentManager)
                .getConsent(eq(AdServicesApiType.FLEDGE));
        assertThat(spyConsentManager.isPasFledgeConsentGiven()).isFalse();
    }

    @Test
    public void testIsPasMeasurementConsentGiven_happycase() throws Exception {
        when(mMockFlags.getPasUxEnabled()).thenReturn(true);
        int consentSourceOfTruth = Flags.SYSTEM_SERVER_ONLY;
        ConsentManager spyConsentManager =
                getSpiedConsentManagerForMigrationTesting(/* isGiven */ true, consentSourceOfTruth);
        doReturn(true).when(mMockIAdServicesManager).wasPasNotificationDisplayed();
        doReturn(AdServicesApiConsent.GIVEN)
                .when(spyConsentManager)
                .getConsent(eq(AdServicesApiType.MEASUREMENTS));
        assertThat(spyConsentManager.isPasMeasurementConsentGiven()).isTrue();
    }

    @Test
    public void testIsPasMeasurementConsentGiven_NotificationNotDisplayed() throws Exception {
        when(mMockFlags.getPasUxEnabled()).thenReturn(true);
        int consentSourceOfTruth = Flags.SYSTEM_SERVER_ONLY;
        ConsentManager spyConsentManager =
                getSpiedConsentManagerForMigrationTesting(/* isGiven */ true, consentSourceOfTruth);
        doReturn(false).when(mMockIAdServicesManager).wasPasNotificationDisplayed();
        doReturn(AdServicesApiConsent.GIVEN)
                .when(spyConsentManager)
                .getConsent(eq(AdServicesApiType.MEASUREMENTS));
        assertThat(spyConsentManager.isPasMeasurementConsentGiven()).isFalse();
    }

    @Test
    public void testIsPasMeasurementConsentGiven_consentRevoken() throws Exception {
        when(mMockFlags.getPasUxEnabled()).thenReturn(true);
        int consentSourceOfTruth = Flags.SYSTEM_SERVER_ONLY;
        ConsentManager spyConsentManager =
                getSpiedConsentManagerForMigrationTesting(/* isGiven */ true, consentSourceOfTruth);
        doReturn(true).when(mMockIAdServicesManager).wasPasNotificationDisplayed();
        doReturn(AdServicesApiConsent.REVOKED)
                .when(spyConsentManager)
                .getConsent(eq(AdServicesApiType.MEASUREMENTS));
        assertThat(spyConsentManager.isPasMeasurementConsentGiven()).isFalse();
    }

    @Test
    public void testIsPasMeasurementConsentGiven_pasNotEnabled() throws Exception {
        when(mMockFlags.getPasUxEnabled()).thenReturn(false);
        int consentSourceOfTruth = Flags.SYSTEM_SERVER_ONLY;
        ConsentManager spyConsentManager =
                getSpiedConsentManagerForMigrationTesting(/* isGiven */ true, consentSourceOfTruth);
        doReturn(true).when(mMockIAdServicesManager).wasPasNotificationDisplayed();
        doReturn(AdServicesApiConsent.GIVEN)
                .when(spyConsentManager)
                .getConsent(eq(AdServicesApiType.MEASUREMENTS));
        assertThat(spyConsentManager.isPasMeasurementConsentGiven()).isFalse();
    }

    @Test
    public void isMeasurementDataResetTest_SystemServerOnly() throws Exception {
        int consentSourceOfTruth = Flags.SYSTEM_SERVER_ONLY;
        ConsentManager spyConsentManager =
                getSpiedConsentManagerForMigrationTesting(
                        /* isGiven */ false, consentSourceOfTruth);

        assertThat(spyConsentManager.isMeasurementDataReset()).isFalse();

        verify(mMockIAdServicesManager).isMeasurementDataReset();

        doReturn(true).when(mMockIAdServicesManager).isMeasurementDataReset();
        spyConsentManager.setMeasurementDataReset(true);

        assertThat(spyConsentManager.isMeasurementDataReset()).isTrue();

        verify(mMockIAdServicesManager, times(2)).isMeasurementDataReset();
        verify(mMockIAdServicesManager).setMeasurementDataReset(anyBoolean());
    }

    @Test
    public void isMeasurementDataResetTest_PpApiAndSystemServer() throws Exception {
        int consentSourceOfTruth = Flags.PPAPI_AND_SYSTEM_SERVER;
        ConsentManager spyConsentManager =
                getSpiedConsentManagerForMigrationTesting(
                        /* isGiven */ false, consentSourceOfTruth);

        Boolean isMeasurementDataReset = spyConsentManager.isMeasurementDataReset();

        assertThat(isMeasurementDataReset).isFalse();

        verify(mMockIAdServicesManager).isMeasurementDataReset();

        doReturn(true).when(mMockIAdServicesManager).isMeasurementDataReset();
        spyConsentManager.setMeasurementDataReset(true);

        assertThat(spyConsentManager.isMeasurementDataReset()).isTrue();

        verify(mMockIAdServicesManager, times(2)).isMeasurementDataReset();
        verify(mMockIAdServicesManager).setMeasurementDataReset(anyBoolean());
    }

    @Test
    public void isMeasurementDataResetTest_appSearchOnly() throws Exception {
        int consentSourceOfTruth = Flags.APPSEARCH_ONLY;
        when(mMockFlags.getEnableAppsearchConsentData()).thenReturn(true);
        ConsentManager spyConsentManager =
                getSpiedConsentManagerForMigrationTesting(
                        /* isGiven */ false, consentSourceOfTruth);

        doReturn(false).when(mAppSearchConsentManagerMock).isMeasurementDataReset();
        assertThat(spyConsentManager.isMeasurementDataReset()).isFalse();
        verify(mAppSearchConsentManagerMock).isMeasurementDataReset();

        doReturn(true).when(mAppSearchConsentManagerMock).isMeasurementDataReset();
        spyConsentManager.setMeasurementDataReset(true);

        assertThat(spyConsentManager.isMeasurementDataReset()).isTrue();

        verify(mAppSearchConsentManagerMock, times(2)).isMeasurementDataReset();
        verify(mAppSearchConsentManagerMock).setMeasurementDataReset(anyBoolean());
    }

    @Test
    public void isPaDataResetTest_SystemServerOnly() throws Exception {
        int consentSourceOfTruth = Flags.SYSTEM_SERVER_ONLY;
        ConsentManager spyConsentManager =
                getSpiedConsentManagerForMigrationTesting(
                        /* isGiven */ false, consentSourceOfTruth);

        assertThat(spyConsentManager.isPaDataReset()).isFalse();

        verify(mMockIAdServicesManager).isPaDataReset();

        doReturn(true).when(mMockIAdServicesManager).isPaDataReset();
        spyConsentManager.setPaDataReset(true);

        assertThat(spyConsentManager.isPaDataReset()).isTrue();

        verify(mMockIAdServicesManager, times(2)).isPaDataReset();
        verify(mMockIAdServicesManager).setPaDataReset(anyBoolean());
    }

    @Test
    public void isPaDataResetTest_PpApiAndSystemServer() throws Exception {
        int consentSourceOfTruth = Flags.PPAPI_AND_SYSTEM_SERVER;
        ConsentManager spyConsentManager =
                getSpiedConsentManagerForMigrationTesting(
                        /* isGiven */ false, consentSourceOfTruth);

        Boolean isPaDataReset = spyConsentManager.isPaDataReset();

        assertThat(isPaDataReset).isFalse();

        verify(mMockIAdServicesManager).isPaDataReset();

        doReturn(true).when(mMockIAdServicesManager).isPaDataReset();
        spyConsentManager.setPaDataReset(true);

        assertThat(spyConsentManager.isPaDataReset()).isTrue();

        verify(mMockIAdServicesManager, times(2)).isPaDataReset();
        verify(mMockIAdServicesManager).setPaDataReset(anyBoolean());
    }

    @Test
    public void isPaDataResetTest_appSearchOnly() throws Exception {
        int consentSourceOfTruth = Flags.APPSEARCH_ONLY;
        when(mMockFlags.getEnableAppsearchConsentData()).thenReturn(true);
        ConsentManager spyConsentManager =
                getSpiedConsentManagerForMigrationTesting(
                        /* isGiven */ false, consentSourceOfTruth);

        doReturn(false).when(mAppSearchConsentManagerMock).isPaDataReset();
        assertThat(spyConsentManager.isPaDataReset()).isFalse();
        verify(mAppSearchConsentManagerMock).isPaDataReset();

        doReturn(true).when(mAppSearchConsentManagerMock).isPaDataReset();
        spyConsentManager.setPaDataReset(true);

        assertThat(spyConsentManager.isPaDataReset()).isTrue();

        verify(mAppSearchConsentManagerMock, times(2)).isPaDataReset();
        verify(mAppSearchConsentManagerMock).setPaDataReset(anyBoolean());
    }

    @Test
    public void testPasNotificationOpenedRecorded_PpApiOnly() throws Exception {
        int consentSourceOfTruth = Flags.PPAPI_ONLY;
        ConsentManager spyConsentManager =
                getSpiedConsentManagerForMigrationTesting(
                        /* isGiven */ false, consentSourceOfTruth);

        assertThat(spyConsentManager.wasPasNotificationOpened()).isFalse();

        verify(mMockIAdServicesManager, never()).wasPasNotificationOpened();

        spyConsentManager.recordPasNotificationOpened(true);

        assertThat(spyConsentManager.wasPasNotificationOpened()).isTrue();

        verify(mMockIAdServicesManager, never()).wasPasNotificationOpened();
        verify(mMockIAdServicesManager, never()).recordPasNotificationOpened(true);
    }

    @Test
    public void testPasNotificationOpenedRecorded_SystemServerOnly() throws Exception {
        int consentSourceOfTruth = Flags.SYSTEM_SERVER_ONLY;
        ConsentManager spyConsentManager =
                getSpiedConsentManagerForMigrationTesting(
                        /* isGiven */ false, consentSourceOfTruth);

        assertThat(spyConsentManager.wasPasNotificationOpened()).isFalse();

        verify(mMockIAdServicesManager).wasPasNotificationOpened();

        doReturn(true).when(mMockIAdServicesManager).wasPasNotificationOpened();
        spyConsentManager.recordPasNotificationOpened(true);

        assertThat(spyConsentManager.wasPasNotificationOpened()).isTrue();

        verify(mMockIAdServicesManager, times(2)).wasPasNotificationOpened();
        verify(mMockIAdServicesManager).recordPasNotificationOpened(true);

        // Verify notificationOpened is not set in PPAPI
        assertThat(mConsentDatastore.getBoolean(PAS_NOTIFICATION_OPENED)).isFalse();
    }

    @Test
    public void testPasNotificationOpenedRecorded_PpApiAndSystemServer() throws Exception {
        int consentSourceOfTruth = Flags.PPAPI_AND_SYSTEM_SERVER;
        ConsentManager spyConsentManager =
                getSpiedConsentManagerForMigrationTesting(
                        /* isGiven */ false, consentSourceOfTruth);

        Boolean wasPasNotificationOpened = spyConsentManager.wasPasNotificationOpened();

        assertThat(wasPasNotificationOpened).isFalse();

        verify(mMockIAdServicesManager).wasPasNotificationOpened();

        doReturn(true).when(mMockIAdServicesManager).wasPasNotificationOpened();
        spyConsentManager.recordPasNotificationOpened(true);

        assertThat(spyConsentManager.wasPasNotificationOpened()).isTrue();

        verify(mMockIAdServicesManager, times(2)).wasPasNotificationOpened();
        verify(mMockIAdServicesManager).recordPasNotificationOpened(true);

        // Verify notificationOpened is also set in PPAPI
        assertThat(mConsentDatastore.getBoolean(PAS_NOTIFICATION_OPENED)).isTrue();
    }

    @Test
    public void getEnrollmentState_PpApiOnly() throws Exception {
        int consentSourceOfTruth = Flags.PPAPI_ONLY;
        ConsentManager spyConsentManager =
                getSpiedConsentManagerForMigrationTesting(
                        /* isGiven */ false, consentSourceOfTruth);
        setupAndVerifyEnrollmentStates(spyConsentManager, consentSourceOfTruth);
    }

    @Test
    public void getEnrollmentState_SystemServerOnly() throws Exception {
        int consentSourceOfTruth = Flags.SYSTEM_SERVER_ONLY;
        ConsentManager spyConsentManager =
                getSpiedConsentManagerForMigrationTesting(
                        /* isGiven */ false, consentSourceOfTruth);
        setupAndVerifyEnrollmentStates(spyConsentManager, consentSourceOfTruth);
    }

    @Test
    public void getEnrollmentState_PpApiAndSystemServer() throws Exception {
        int consentSourceOfTruth = Flags.PPAPI_AND_SYSTEM_SERVER;
        ConsentManager spyConsentManager =
                getSpiedConsentManagerForMigrationTesting(
                        /* isGiven */ false, consentSourceOfTruth);
        setupAndVerifyEnrollmentStates(spyConsentManager, consentSourceOfTruth);
    }

    @Test
    public void getEnrollmentState_appSearchOnly() throws Exception {
        int consentSourceOfTruth = Flags.APPSEARCH_ONLY;
        when(mMockFlags.getEnableAppsearchConsentData()).thenReturn(true);
        ConsentManager spyConsentManager =
                getSpiedConsentManagerForMigrationTesting(
                        /* isGiven */ false, consentSourceOfTruth);
        setupAndVerifyEnrollmentStates(spyConsentManager, consentSourceOfTruth);
    }

    @Test
    public void testConsentSourceOfTruthToString() {
        expectConsentSourceOfTruthToString(Flags.SYSTEM_SERVER_ONLY, "SYSTEM_SERVER_ONLY");
        expectConsentSourceOfTruthToString(Flags.PPAPI_ONLY, "PPAPI_ONLY");
        expectConsentSourceOfTruthToString(
                Flags.PPAPI_AND_SYSTEM_SERVER, "PPAPI_AND_SYSTEM_SERVER");
        expectConsentSourceOfTruthToString(Flags.APPSEARCH_ONLY, "APPSEARCH_ONLY");
        expectConsentSourceOfTruthToString(666, "UNKNOWN-666");
    }

    @Test
    public void testDump() throws Exception {
        String datastoreDump =
                dump(pw -> mConsentDatastore.dump(pw, /* prefix= */ "    ", /* args= */ null));
        String dump = dump(pw -> mConsentManager.dump(pw, /* args= */ null));

        expect.withMessage("dump()")
                .that(dump)
                .startsWith(
                        "ConsentManager\n"
                                + "  Source of truth: PPAPI_ONLY\n"
                                + "  sDataMigrationDuration: ");
        expect.withMessage("dump()")
                .that(dump)
                .containsMatch("\n  sDataMigrationDuration: .*ms\n  sInstantiationDuration: .*ms");
        expect.withMessage("dump()").that(dump).endsWith("\n  Datastore:\n" + datastoreDump);
    }

    private void expectConsentSourceOfTruthToString(int source, String toString) {
        expect.withMessage("consentSourceOfTruthToString(%s)", source)
                .that(consentSourceOfTruthToString(source))
                .isEqualTo(toString);
    }

    @Test
    public void testMigrateEnrollmentData() throws Exception {
        int consentSourceOfTruth = Flags.PPAPI_AND_SYSTEM_SERVER;
        ConsentManager spyConsentManager =
                getSpiedConsentManagerForMigrationTesting(
                        /* isGiven */ false, consentSourceOfTruth);

        doNothing().when(mMockIAdServicesManager).setConsent(any());
        doNothing().when(mMockIAdServicesManager).recordNotificationDisplayed(true);

        doReturn(null).when(spyConsentManager).getConsent(any());
        doReturn(AdServicesApiConsent.GIVEN)
                .when(spyConsentManager)
                .getConsent(AdServicesApiType.TOPICS);
        doReturn(AdServicesApiConsent.REVOKED)
                .when(spyConsentManager)
                .getConsent(AdServicesApiType.MEASUREMENTS);
        doReturn(true).when(spyConsentManager).wasGaUxNotificationDisplayed();

        final String[] dataStringArg = new String[1];
        doAnswer(
                        i -> {
                            dataStringArg[0] = i.getArgument(0);
                            return null;
                        })
                .when(spyConsentManager)
                .setModuleEnrollmentData(anyString());

        ConsentManager.handleEnrollmentDataMigrationIfNeeded(spyConsentManager);

        EnrollmentData actualData = EnrollmentData.deserialize(dataStringArg[0]);
        assertEquals(
                "TOPICS User Choice",
                AdServicesModuleUserChoice.USER_CHOICE_OPTED_IN,
                actualData.getUserChoice(Module.TOPICS));
        assertEquals(
                "MEASUREMENT User Choice",
                AdServicesModuleUserChoice.USER_CHOICE_OPTED_OUT,
                actualData.getUserChoice(Module.MEASUREMENT));
        assertEquals(
                "PA User Choice",
                AdServicesModuleUserChoice.USER_CHOICE_UNKNOWN,
                actualData.getUserChoice(Module.PROTECTED_AUDIENCE));
        assertEquals(
                "PAS User Choice",
                AdServicesModuleUserChoice.USER_CHOICE_UNKNOWN,
                actualData.getUserChoice(Module.PROTECTED_APP_SIGNALS));
        assertEquals(
                "ODP User Choice",
                AdServicesModuleUserChoice.USER_CHOICE_UNKNOWN,
                actualData.getUserChoice(Module.ON_DEVICE_PERSONALIZATION));
        assertEquals(
                "ADID User Choice",
                AdServicesModuleUserChoice.USER_CHOICE_UNKNOWN,
                actualData.getUserChoice(Module.ADID));
        assertEquals(
                "TOPICS Module State",
                MODULE_STATE_ENABLED,
                actualData.getModuleState(Module.TOPICS));
        assertEquals(
                "MEASUREMENT Module State",
                MODULE_STATE_ENABLED,
                actualData.getModuleState(Module.MEASUREMENT));
        assertEquals(
                "PA Module State",
                MODULE_STATE_ENABLED,
                actualData.getModuleState(Module.PROTECTED_AUDIENCE));
        assertEquals(
                "PAS Module State",
                MODULE_STATE_DISABLED,
                actualData.getModuleState(Module.PROTECTED_APP_SIGNALS));
        assertEquals(
                "ODP Module State",
                MODULE_STATE_DISABLED,
                actualData.getModuleState(Module.ON_DEVICE_PERSONALIZATION));
        assertEquals(
                "ADID Module State", MODULE_STATE_UNKNOWN, actualData.getModuleState(Module.ADID));
        verify(spyConsentManager).setModuleEnrollmentData(anyString());
        clearInvocations(spyConsentManager);

        doAnswer(unused -> dataStringArg[0]).when(spyConsentManager).getModuleEnrollmentState();
        // Verify this should only happen once
        ConsentManager.handleEnrollmentDataMigrationIfNeeded(spyConsentManager);
        verify(spyConsentManager, never()).setModuleEnrollmentData(anyString());
    }

    private void setupAndVerifyEnrollmentStates(
            ConsentManager spyConsentManager, int consentSourceOfTruth)
            throws RemoteException, IOException {
        EnrollmentData data = EnrollmentData.deserialize("");
        int[][] moduleStates =
                new int[][] {
                    {Module.TOPICS, MODULE_STATE_DISABLED},
                    {Module.PROTECTED_AUDIENCE, MODULE_STATE_ENABLED},
                    {Module.MEASUREMENT, MODULE_STATE_ENABLED},
                    {Module.PROTECTED_APP_SIGNALS, MODULE_STATE_ENABLED},
                };
        SparseIntArray moduleStateList = new SparseIntArray(moduleStates.length);
        for (int[] element : moduleStates) {
            data.putModuleState(element[0], element[1]);
            moduleStateList.put(element[0], element[1]);
        }
        spyConsentManager.setModuleStates(moduleStateList);

        AdServicesModuleUserChoice userChoice =
                new AdServicesModuleUserChoice(
                        Module.MEASUREMENT, AdServicesModuleUserChoice.USER_CHOICE_OPTED_OUT);
        spyConsentManager.setUserChoices(List.of(userChoice));
        data.putUserChoice(Module.MEASUREMENT, AdServicesModuleUserChoice.USER_CHOICE_OPTED_OUT);

        AdServicesModuleUserChoice userChoicePa =
                new AdServicesModuleUserChoice(
                        Module.PROTECTED_APP_SIGNALS,
                        AdServicesModuleUserChoice.USER_CHOICE_OPTED_IN);
        spyConsentManager.setUserChoices(List.of(userChoicePa));
        data.putUserChoice(
                Module.PROTECTED_APP_SIGNALS, AdServicesModuleUserChoice.USER_CHOICE_OPTED_IN);

        switch (consentSourceOfTruth) {
            case Flags.SYSTEM_SERVER_ONLY, Flags.PPAPI_AND_SYSTEM_SERVER:
                doReturn(EnrollmentData.serialize(data))
                        .when(mMockIAdServicesManager)
                        .getModuleEnrollmentState();
                verify(mMockIAdServicesManager, times(3)).setModuleEnrollmentState(anyString());
                break;
            case Flags.APPSEARCH_ONLY:
                doReturn(EnrollmentData.serialize(data))
                        .when(mAppSearchConsentManagerMock)
                        .getModuleEnrollmentState();
                verify(mAppSearchConsentManagerMock, times(3))
                        .setModuleEnrollmentState(anyString());
                break;
        }

        assertThat(spyConsentManager.getModuleState(Module.ON_DEVICE_PERSONALIZATION))
                .isEqualTo(MODULE_STATE_UNKNOWN);
        assertThat(spyConsentManager.getUserChoice(Module.ON_DEVICE_PERSONALIZATION))
                .isEqualTo(AdServicesModuleUserChoice.USER_CHOICE_UNKNOWN);
        assertThat(spyConsentManager.getModuleState(Module.TOPICS))
                .isEqualTo(MODULE_STATE_DISABLED);
        assertThat(spyConsentManager.getUserChoice(Module.TOPICS))
                .isEqualTo(AdServicesModuleUserChoice.USER_CHOICE_UNKNOWN);
        assertThat(spyConsentManager.getModuleState(Module.PROTECTED_AUDIENCE))
                .isEqualTo(MODULE_STATE_ENABLED);
        assertThat(spyConsentManager.getUserChoice(Module.PROTECTED_AUDIENCE))
                .isEqualTo(AdServicesModuleUserChoice.USER_CHOICE_UNKNOWN);
        assertThat(spyConsentManager.getModuleState(Module.MEASUREMENT))
                .isEqualTo(MODULE_STATE_ENABLED);
        assertThat(spyConsentManager.getUserChoice(Module.MEASUREMENT))
                .isEqualTo(AdServicesModuleUserChoice.USER_CHOICE_OPTED_OUT);
        assertThat(spyConsentManager.getModuleState(Module.PROTECTED_APP_SIGNALS))
                .isEqualTo(MODULE_STATE_ENABLED);
        assertThat(spyConsentManager.getUserChoice(Module.PROTECTED_APP_SIGNALS))
                .isEqualTo(AdServicesModuleUserChoice.USER_CHOICE_OPTED_IN);
    }

    private void mockEnableAtomicFileDatastoreBatchUpdateApi(boolean enable) {
        when(mMockFlags.getEnableAtomicFileDatastoreBatchUpdateApi()).thenReturn(enable);
    }
}

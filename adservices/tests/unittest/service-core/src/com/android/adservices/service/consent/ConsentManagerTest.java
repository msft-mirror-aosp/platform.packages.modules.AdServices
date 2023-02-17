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

import static com.android.adservices.service.consent.ConsentConstants.CONSENT_KEY;
import static com.android.adservices.service.consent.ConsentConstants.FLEDGE_AND_MSMT_CONSENT_PAGE_DISPLAYED;
import static com.android.adservices.service.consent.ConsentConstants.GA_UX_NOTIFICATION_DISPLAYED_ONCE;
import static com.android.adservices.service.consent.ConsentConstants.NOTIFICATION_DISPLAYED_ONCE;
import static com.android.adservices.service.consent.ConsentConstants.SHARED_PREFS_CONSENT;
import static com.android.adservices.service.consent.ConsentConstants.SHARED_PREFS_KEY_HAS_MIGRATED;
import static com.android.adservices.service.consent.ConsentConstants.SHARED_PREFS_KEY_PPAPI_HAS_CLEARED;
import static com.android.adservices.service.consent.ConsentConstants.TOPICS_CONSENT_PAGE_DISPLAYED;
import static com.android.adservices.service.consent.ConsentManager.resetSharedPreference;

import static com.google.common.truth.Truth.assertThat;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertNull;
import static org.junit.Assert.assertThrows;
import static org.junit.Assert.assertTrue;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyBoolean;
import static org.mockito.ArgumentMatchers.anyInt;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.ArgumentMatchers.argThat;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.atLeastOnce;
import static org.mockito.Mockito.doNothing;
import static org.mockito.Mockito.doReturn;
import static org.mockito.Mockito.doThrow;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.spy;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoMoreInteractions;
import static org.mockito.Mockito.when;

import android.annotation.NonNull;
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

import androidx.test.core.app.ApplicationProvider;
import androidx.test.core.content.pm.ApplicationInfoBuilder;
import androidx.test.filters.SmallTest;

import com.android.adservices.data.DbTestUtil;
import com.android.adservices.data.common.BooleanFileDatastore;
import com.android.adservices.data.consent.AppConsentDao;
import com.android.adservices.data.consent.AppConsentDaoFixture;
import com.android.adservices.data.customaudience.CustomAudienceDao;
import com.android.adservices.data.enrollment.EnrollmentDao;
import com.android.adservices.data.topics.Topic;
import com.android.adservices.data.topics.TopicsTables;
import com.android.adservices.download.MddJobService;
import com.android.adservices.service.AdServicesConfig;
import com.android.adservices.service.Flags;
import com.android.adservices.service.FlagsFactory;
import com.android.adservices.service.MaintenanceJobService;
import com.android.adservices.service.common.BackgroundJobsManager;
import com.android.adservices.service.measurement.AsyncRegistrationQueueJobService;
import com.android.adservices.service.measurement.DeleteExpiredJobService;
import com.android.adservices.service.measurement.DeleteUninstalledJobService;
import com.android.adservices.service.measurement.MeasurementImpl;
import com.android.adservices.service.measurement.attribution.AttributionJobService;
import com.android.adservices.service.measurement.reporting.AggregateFallbackReportingJobService;
import com.android.adservices.service.measurement.reporting.AggregateReportingJobService;
import com.android.adservices.service.measurement.reporting.EventFallbackReportingJobService;
import com.android.adservices.service.measurement.reporting.EventReportingJobService;
import com.android.adservices.service.stats.AdServicesLoggerImpl;
import com.android.adservices.service.stats.UiStatsLogger;
import com.android.adservices.service.topics.AppUpdateManager;
import com.android.adservices.service.topics.BlockedTopicsManager;
import com.android.adservices.service.topics.CacheManager;
import com.android.adservices.service.topics.EpochJobService;
import com.android.adservices.service.topics.EpochManager;
import com.android.adservices.service.topics.TopicsWorker;
import com.android.dx.mockito.inline.extended.ExtendedMockito;

import com.google.common.collect.ImmutableList;

import org.junit.After;
import org.junit.Before;
import org.junit.Test;
import org.mockito.ArgumentMatcher;
import org.mockito.Mock;
import org.mockito.MockitoAnnotations;
import org.mockito.MockitoSession;
import org.mockito.Spy;
import org.mockito.quality.Strictness;
import org.mockito.verification.VerificationMode;

import java.io.IOException;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;
import java.util.Objects;
import java.util.stream.Collectors;

@SmallTest
public class ConsentManagerTest {
    @Spy private final Context mContextSpy = ApplicationProvider.getApplicationContext();

    private BooleanFileDatastore mDatastore;
    private BooleanFileDatastore mConsentDatastore;
    private ConsentManager mConsentManager;
    private AppConsentDao mAppConsentDao;
    private EnrollmentDao mEnrollmentDao;
    private AdServicesManager mAdServicesManager;

    @Mock private PackageManager mPackageManagerMock;
    @Mock private TopicsWorker mTopicsWorker;
    @Mock private MeasurementImpl mMeasurementImpl;
    @Mock private AdServicesLoggerImpl mAdServicesLoggerImpl;
    @Mock private CustomAudienceDao mCustomAudienceDaoMock;
    @Mock private UiStatsLogger mUiStatsLogger;
    @Mock private AppUpdateManager mAppUpdateManager;
    @Mock private CacheManager mCacheManager;
    @Mock private BlockedTopicsManager mBlockedTopicsManager;
    @Mock private EpochManager mMockEpochManager;
    @Mock private Flags mMockFlags;
    @Mock private JobScheduler mJobSchedulerMock;
    @Mock private IAdServicesManager mMockIAdServicesManager;
    private MockitoSession mStaticMockSession = null;

    @Before
    public void setup() throws IOException {
        MockitoAnnotations.initMocks(this);

        mStaticMockSession =
                ExtendedMockito.mockitoSession()
                        .spyStatic(AdServicesLoggerImpl.class)
                        .spyStatic(AggregateFallbackReportingJobService.class)
                        .spyStatic(AggregateReportingJobService.class)
                        .spyStatic(AsyncRegistrationQueueJobService.class)
                        .spyStatic(AttributionJobService.class)
                        .spyStatic(BackgroundJobsManager.class)
                        .spyStatic(ConsentManager.class)
                        .spyStatic(DeleteExpiredJobService.class)
                        .spyStatic(DeleteUninstalledJobService.class)
                        .spyStatic(DeviceRegionProvider.class)
                        .spyStatic(EpochJobService.class)
                        .spyStatic(EventFallbackReportingJobService.class)
                        .spyStatic(EventReportingJobService.class)
                        .spyStatic(FlagsFactory.class)
                        .spyStatic(MaintenanceJobService.class)
                        .spyStatic(MddJobService.class)
                        .spyStatic(UiStatsLogger.class)
                        .strictness(Strictness.WARN)
                        .initMocks(this)
                        .startMocking();

        mDatastore =
                new BooleanFileDatastore(
                        mContextSpy, AppConsentDao.DATASTORE_NAME, AppConsentDao.DATASTORE_VERSION);
        // For each file, we should ensure there is only one instance of datastore that is able to
        // access it. (Refer to BooleanFileDatastore.class)
        mConsentDatastore = ConsentManager.createAndInitializeDataStore(mContextSpy);
        mAppConsentDao = spy(new AppConsentDao(mDatastore, mPackageManagerMock));
        mEnrollmentDao = spy(new EnrollmentDao(mContextSpy, DbTestUtil.getDbHelperForTest()));
        mAdServicesManager = new AdServicesManager(mMockIAdServicesManager);
        doReturn(mAdServicesManager).when(mContextSpy).getSystemService(AdServicesManager.class);

        // Default to use PPAPI consent to test migration-irrelevant logics.
        mConsentManager = getConsentManagerByConsentSourceOfTruth(Flags.PPAPI_ONLY);

        ExtendedMockito.doReturn(mMockFlags).when(FlagsFactory::getFlags);
        ExtendedMockito.doReturn(mAdServicesLoggerImpl).when(AdServicesLoggerImpl::getInstance);
        ExtendedMockito.doReturn(true)
                .when(() -> EpochJobService.scheduleIfNeeded(any(Context.class), eq(false)));
        ExtendedMockito.doReturn(true)
                .when(() -> MaintenanceJobService.scheduleIfNeeded(any(Context.class), eq(false)));
        ExtendedMockito.doReturn(true)
                .when(() -> MddJobService.scheduleIfNeeded(any(Context.class), eq(false)));
        ExtendedMockito.doNothing()
                .when(() -> AggregateReportingJobService.scheduleIfNeeded(any(), anyBoolean()));
        ExtendedMockito.doNothing()
                .when(
                        () ->
                                AggregateFallbackReportingJobService.scheduleIfNeeded(
                                        any(), anyBoolean()));
        ExtendedMockito.doNothing()
                .when(() -> AttributionJobService.scheduleIfNeeded(any(), anyBoolean()));
        ExtendedMockito.doReturn(true)
                .when(() -> EpochJobService.scheduleIfNeeded(any(), anyBoolean()));
        ExtendedMockito.doReturn(true)
                .when(() -> MddJobService.scheduleIfNeeded(any(), anyBoolean()));
        ExtendedMockito.doNothing()
                .when(() -> EventReportingJobService.scheduleIfNeeded(any(), anyBoolean()));
        ExtendedMockito.doNothing()
                .when(() -> EventFallbackReportingJobService.scheduleIfNeeded(any(), anyBoolean()));
        ExtendedMockito.doNothing()
                .when(() -> DeleteExpiredJobService.scheduleIfNeeded(any(), anyBoolean()));
        ExtendedMockito.doNothing()
                .when(() -> DeleteUninstalledJobService.scheduleIfNeeded(any(), anyBoolean()));
        ExtendedMockito.doReturn(true)
                .when(() -> MaintenanceJobService.scheduleIfNeeded(any(), anyBoolean()));
        ExtendedMockito.doNothing()
                .when(() -> AsyncRegistrationQueueJobService.scheduleIfNeeded(any(), anyBoolean()));
        ExtendedMockito.doNothing().when(() -> UiStatsLogger.logOptInSelected(any()));
        ExtendedMockito.doNothing().when(() -> UiStatsLogger.logOptOutSelected(any()));
        ExtendedMockito.doNothing().when(() -> UiStatsLogger.logOptInSelected(any(), any()));
        ExtendedMockito.doNothing().when(() -> UiStatsLogger.logOptOutSelected(any(), any()));
        ExtendedMockito.doNothing().when(() -> UiStatsLogger.logResetMeasurement(any()));
    }

    @After
    public void teardown() throws IOException {
        mDatastore.clear();
        mConsentDatastore.clear();
        if (mStaticMockSession != null) {
            mStaticMockSession.finishMocking();
        }
    }

    @Test
    public void testConsentIsGivenAfterEnabling_PpApiOnly() throws RemoteException, IOException {
        boolean isGiven = true;
        int consentSourceOfTruth = Flags.PPAPI_ONLY;
        ConsentManager spyConsentManager =
                getSpiedConsentManagerForMigrationTesting(isGiven, consentSourceOfTruth);

        spyConsentManager.enable(mContextSpy);

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
    public void testConsentIsGivenAfterEnabling_SystemServerOnly()
            throws RemoteException, IOException {
        boolean isGiven = true;
        int consentSourceOfTruth = Flags.SYSTEM_SERVER_ONLY;
        ConsentManager spyConsentManager =
                getSpiedConsentManagerForMigrationTesting(isGiven, consentSourceOfTruth);

        spyConsentManager.enable(mContextSpy);

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
    public void testConsentIsGivenAfterEnabling_PPAPIAndSystemServer()
            throws RemoteException, IOException {
        boolean isGiven = true;
        int consentSourceOfTruth = Flags.PPAPI_AND_SYSTEM_SERVER;
        ConsentManager spyConsentManager =
                getSpiedConsentManagerForMigrationTesting(isGiven, consentSourceOfTruth);

        spyConsentManager.enable(mContextSpy);

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
    public void testConsentIsGivenAfterEnabling_notSupportedFlag() throws RemoteException {
        boolean isGiven = true;
        int invalidConsentSourceOfTruth = 3;
        ConsentManager spyConsentManager =
                getSpiedConsentManagerForMigrationTesting(isGiven, invalidConsentSourceOfTruth);

        assertThrows(RuntimeException.class, () -> spyConsentManager.enable(mContextSpy));
    }

    @Test
    public void testConsentIsRevokedAfterDisabling_PpApiOnly() throws RemoteException, IOException {
        boolean isGiven = false;
        int consentSourceOfTruth = Flags.PPAPI_ONLY;
        ConsentManager spyConsentManager =
                getSpiedConsentManagerForMigrationTesting(isGiven, consentSourceOfTruth);

        spyConsentManager.disable(mContextSpy);

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
    public void testConsentIsRevokedAfterDisabling_SystemServerOnly()
            throws RemoteException, IOException {
        boolean isGiven = false;
        int consentSourceOfTruth = Flags.SYSTEM_SERVER_ONLY;
        ConsentManager spyConsentManager =
                getSpiedConsentManagerForMigrationTesting(isGiven, consentSourceOfTruth);

        spyConsentManager.disable(mContextSpy);

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
    public void testConsentIsRevokedAfterDisabling_PpApiAndSystemServer()
            throws RemoteException, IOException {
        boolean isGiven = false;
        int consentSourceOfTruth = Flags.PPAPI_AND_SYSTEM_SERVER;
        ConsentManager spyConsentManager =
                getSpiedConsentManagerForMigrationTesting(isGiven, consentSourceOfTruth);

        spyConsentManager.disable(mContextSpy);

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
    public void testConsentIsRevokedAfterDisabling_notSupportedFlag() throws RemoteException {
        boolean isGiven = true;
        int invalidConsentSourceOfTruth = 3;
        ConsentManager spyConsentManager =
                getSpiedConsentManagerForMigrationTesting(isGiven, invalidConsentSourceOfTruth);

        assertThrows(RuntimeException.class, () -> spyConsentManager.disable(mContextSpy));
    }

    @Test
    public void testJobsAreScheduledAfterEnablingKillSwitchOff() {
        doReturn(mPackageManagerMock).when(mContextSpy).getPackageManager();
        doReturn(false).when(mMockFlags).getTopicsKillSwitch();
        doReturn(false).when(mMockFlags).getFledgeSelectAdsKillSwitch();
        doReturn(false).when(mMockFlags).getMeasurementKillSwitch();
        doReturn(false).when(mMockFlags).getMddBackgroundTaskKillSwitch();

        mConsentManager.enable(mContextSpy);

        ExtendedMockito.verify(
                () -> BackgroundJobsManager.scheduleAllBackgroundJobs(any(Context.class)));
        ExtendedMockito.verify(
                () -> EpochJobService.scheduleIfNeeded(any(Context.class), eq(false)));
        ExtendedMockito.verify(
                () -> MddJobService.scheduleIfNeeded(any(Context.class), eq(false)), times(3));
        ExtendedMockito.verify(
                () -> MaintenanceJobService.scheduleIfNeeded(any(Context.class), eq(false)),
                times(2));
        ExtendedMockito.verify(
                () -> AggregateReportingJobService.scheduleIfNeeded(any(Context.class), eq(false)));
        ExtendedMockito.verify(
                () ->
                        AggregateFallbackReportingJobService.scheduleIfNeeded(
                                any(Context.class), eq(false)));
        ExtendedMockito.verify(
                () -> AttributionJobService.scheduleIfNeeded(any(Context.class), eq(false)));
        ExtendedMockito.verify(
                () -> EventReportingJobService.scheduleIfNeeded(any(Context.class), eq(false)));
        ExtendedMockito.verify(
                () ->
                        EventFallbackReportingJobService.scheduleIfNeeded(
                                any(Context.class), eq(false)));
        ExtendedMockito.verify(
                () -> DeleteExpiredJobService.scheduleIfNeeded(any(Context.class), eq(false)));
        ExtendedMockito.verify(
                () -> DeleteUninstalledJobService.scheduleIfNeeded(any(Context.class), eq(false)));
        ExtendedMockito.verify(
                () ->
                        AsyncRegistrationQueueJobService.scheduleIfNeeded(
                                any(Context.class), eq(false)));
    }

    @Test
    public void testJobsAreNotScheduledAfterEnablingKillSwitchOn() {
        doReturn(mPackageManagerMock).when(mContextSpy).getPackageManager();
        doReturn(true).when(mMockFlags).getTopicsKillSwitch();
        doReturn(true).when(mMockFlags).getFledgeSelectAdsKillSwitch();
        doReturn(true).when(mMockFlags).getMeasurementKillSwitch();
        doReturn(true).when(mMockFlags).getMddBackgroundTaskKillSwitch();

        mConsentManager.enable(mContextSpy);

        ExtendedMockito.verify(
                () -> BackgroundJobsManager.scheduleAllBackgroundJobs(any(Context.class)));
        ExtendedMockito.verify(
                () -> EpochJobService.scheduleIfNeeded(any(Context.class), eq(false)),
                ExtendedMockito.never());
        ExtendedMockito.verify(
                () -> MaintenanceJobService.scheduleIfNeeded(any(Context.class), eq(false)),
                ExtendedMockito.never());
        ExtendedMockito.verify(
                () -> MddJobService.scheduleIfNeeded(any(Context.class), eq(false)),
                ExtendedMockito.never());
        ExtendedMockito.verify(
                () -> AggregateReportingJobService.scheduleIfNeeded(any(Context.class), eq(false)),
                ExtendedMockito.never());
        ExtendedMockito.verify(
                () ->
                        AggregateFallbackReportingJobService.scheduleIfNeeded(
                                any(Context.class), eq(false)),
                ExtendedMockito.never());
        ExtendedMockito.verify(
                () -> AttributionJobService.scheduleIfNeeded(any(Context.class), eq(false)),
                ExtendedMockito.never());
        ExtendedMockito.verify(
                () -> EventReportingJobService.scheduleIfNeeded(any(Context.class), eq(false)),
                ExtendedMockito.never());
        ExtendedMockito.verify(
                () ->
                        EventFallbackReportingJobService.scheduleIfNeeded(
                                any(Context.class), eq(false)),
                ExtendedMockito.never());
        ExtendedMockito.verify(
                () -> DeleteExpiredJobService.scheduleIfNeeded(any(Context.class), eq(false)),
                ExtendedMockito.never());
        ExtendedMockito.verify(
                () -> DeleteUninstalledJobService.scheduleIfNeeded(any(Context.class), eq(false)),
                ExtendedMockito.never());
        ExtendedMockito.verify(
                () ->
                        AsyncRegistrationQueueJobService.scheduleIfNeeded(
                                any(Context.class), eq(false)),
                ExtendedMockito.never());
    }

    @Test
    public void testJobsAreUnscheduledAfterDisabling() {
        doReturn(mPackageManagerMock).when(mContextSpy).getPackageManager();
        doReturn(mJobSchedulerMock).when(mContextSpy).getSystemService(JobScheduler.class);
        mConsentManager.disable(mContextSpy);

        ExtendedMockito.verify(() -> UiStatsLogger.logOptOutSelected(mContextSpy));
        ExtendedMockito.verify(() -> UiStatsLogger.logResetMeasurement(mContextSpy));

        verify(mJobSchedulerMock).cancel(AdServicesConfig.MAINTENANCE_JOB_ID);
        verify(mJobSchedulerMock).cancel(AdServicesConfig.TOPICS_EPOCH_JOB_ID);
        verify(mJobSchedulerMock).cancel(AdServicesConfig.MEASUREMENT_EVENT_MAIN_REPORTING_JOB_ID);
        verify(mJobSchedulerMock).cancel(AdServicesConfig.MEASUREMENT_DELETE_EXPIRED_JOB_ID);
        verify(mJobSchedulerMock).cancel(AdServicesConfig.MEASUREMENT_DELETE_UNINSTALLED_JOB_ID);
        verify(mJobSchedulerMock).cancel(AdServicesConfig.MEASUREMENT_ATTRIBUTION_JOB_ID);
        verify(mJobSchedulerMock)
                .cancel(AdServicesConfig.MEASUREMENT_EVENT_FALLBACK_REPORTING_JOB_ID);
        verify(mJobSchedulerMock)
                .cancel(AdServicesConfig.MEASUREMENT_AGGREGATE_MAIN_REPORTING_JOB_ID);
        verify(mJobSchedulerMock)
                .cancel(AdServicesConfig.MEASUREMENT_AGGREGATE_FALLBACK_REPORTING_JOB_ID);
        verify(mJobSchedulerMock).cancel(AdServicesConfig.ASYNC_REGISTRATION_QUEUE_JOB_ID);
        verify(mJobSchedulerMock).cancel(AdServicesConfig.FLEDGE_BACKGROUND_FETCH_JOB_ID);
        verify(mJobSchedulerMock).cancel(AdServicesConfig.CONSENT_NOTIFICATION_JOB_ID);
        verify(mJobSchedulerMock).cancel(AdServicesConfig.MDD_MAINTENANCE_PERIODIC_TASK_JOB_ID);
        verify(mJobSchedulerMock).cancel(AdServicesConfig.MDD_CHARGING_PERIODIC_TASK_JOB_ID);
        verify(mJobSchedulerMock)
                .cancel(AdServicesConfig.MDD_CELLULAR_CHARGING_PERIODIC_TASK_JOB_ID);
        verify(mJobSchedulerMock).cancel(AdServicesConfig.MDD_WIFI_CHARGING_PERIODIC_TASK_JOB_ID);

        verifyNoMoreInteractions(mJobSchedulerMock);
    }

    @Test
    public void testDataIsResetAfterConsentIsRevoked() throws IOException {
        doReturn(mPackageManagerMock).when(mContextSpy).getPackageManager();
        mConsentManager.disable(mContextSpy);

        ExtendedMockito.verify(() -> UiStatsLogger.logOptOutSelected(mContextSpy));
        ExtendedMockito.verify(() -> UiStatsLogger.logResetMeasurement(mContextSpy));

        SystemClock.sleep(1000);
        verify(mTopicsWorker, times(1)).clearAllTopicsData(any());
        // TODO(b/240988406): change to test for correct method call
        verify(mAppConsentDao, times(1)).clearAllConsentData();
        verify(mEnrollmentDao, times(1)).deleteAll();
        verify(mMeasurementImpl, times(1)).deleteAllMeasurementData(any());
        verify(mCustomAudienceDaoMock).deleteAllCustomAudienceData();
    }

    @Test
    public void testDataIsResetAfterConsentIsGiven() throws IOException {
        doReturn(mPackageManagerMock).when(mContextSpy).getPackageManager();
        mConsentManager.enable(mContextSpy);

        ExtendedMockito.verify(() -> UiStatsLogger.logOptInSelected(mContextSpy));
        ExtendedMockito.verify(() -> UiStatsLogger.logResetMeasurement(mContextSpy));

        SystemClock.sleep(1000);
        verify(mTopicsWorker, times(1)).clearAllTopicsData(any());
        // TODO(b/240988406): change to test for correct method call
        verify(mAppConsentDao, times(1)).clearAllConsentData();
        verify(mMeasurementImpl, times(1)).deleteAllMeasurementData(any());
        verify(mCustomAudienceDaoMock).deleteAllCustomAudienceData();
    }

    @Test
    public void testIsFledgeConsentRevokedForAppWithFullApiConsentGaUxDisabled_ppApiOnly()
            throws IOException, PackageManager.NameNotFoundException {
        when(mMockFlags.getGaUxFeatureEnabled()).thenReturn(false);
        mConsentManager.enable(mContextSpy);
        assertTrue(mConsentManager.getConsent().isGiven());

        ExtendedMockito.verify(() -> UiStatsLogger.logOptInSelected(mContextSpy));
        ExtendedMockito.verify(() -> UiStatsLogger.logResetMeasurement(mContextSpy));

        mockGetPackageUid(AppConsentDaoFixture.APP10_PACKAGE_NAME, AppConsentDaoFixture.APP10_UID);
        mockGetPackageUid(AppConsentDaoFixture.APP20_PACKAGE_NAME, AppConsentDaoFixture.APP20_UID);
        mockGetPackageUid(AppConsentDaoFixture.APP30_PACKAGE_NAME, AppConsentDaoFixture.APP30_UID);

        mDatastore.put(AppConsentDaoFixture.APP10_DATASTORE_KEY, false);
        mDatastore.put(AppConsentDaoFixture.APP20_DATASTORE_KEY, true);

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
    public void testIsFledgeConsentRevokedForAppWithFullApiConsentGaUxDisabled_systemServerOnly()
            throws PackageManager.NameNotFoundException, RemoteException {
        when(mMockFlags.getGaUxFeatureEnabled()).thenReturn(false);
        int consentSourceOfTruth = Flags.SYSTEM_SERVER_ONLY;
        mConsentManager = getConsentManagerByConsentSourceOfTruth(consentSourceOfTruth);
        doReturn(ConsentParcel.createGivenConsent(ConsentParcel.ALL_API))
                .when(mMockIAdServicesManager)
                .getConsent(ConsentParcel.ALL_API);

        assertTrue(mConsentManager.getConsent().isGiven());

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
    public void
            testIsFledgeConsentRevokedForAppWithFullApiConsentGaUxDisabled_ppApiAndSystemServer()
                    throws PackageManager.NameNotFoundException, RemoteException {
        when(mMockFlags.getGaUxFeatureEnabled()).thenReturn(false);
        int consentSourceOfTruth = Flags.PPAPI_AND_SYSTEM_SERVER;
        mConsentManager = getConsentManagerByConsentSourceOfTruth(consentSourceOfTruth);
        doReturn(ConsentParcel.createGivenConsent(ConsentParcel.ALL_API))
                .when(mMockIAdServicesManager)
                .getConsent(ConsentParcel.ALL_API);

        assertTrue(mConsentManager.getConsent().isGiven());
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
    public void testIsFledgeConsentRevokedForAppWithFullApiConsentGaUxEnabled_ppApiOnly()
            throws IOException, PackageManager.NameNotFoundException {
        when(mMockFlags.getGaUxFeatureEnabled()).thenReturn(true);
        mConsentManager.enable(mContextSpy, AdServicesApiType.FLEDGE);
        assertTrue(mConsentManager.getConsent(AdServicesApiType.FLEDGE).isGiven());

        ExtendedMockito.verify(
                () -> UiStatsLogger.logOptInSelected(mContextSpy, AdServicesApiType.FLEDGE));

        mockGetPackageUid(AppConsentDaoFixture.APP10_PACKAGE_NAME, AppConsentDaoFixture.APP10_UID);
        mockGetPackageUid(AppConsentDaoFixture.APP20_PACKAGE_NAME, AppConsentDaoFixture.APP20_UID);
        mockGetPackageUid(AppConsentDaoFixture.APP30_PACKAGE_NAME, AppConsentDaoFixture.APP30_UID);

        mDatastore.put(AppConsentDaoFixture.APP10_DATASTORE_KEY, false);
        mDatastore.put(AppConsentDaoFixture.APP20_DATASTORE_KEY, true);

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
            throws PackageManager.NameNotFoundException, RemoteException {
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
    public void testIsFledgeConsentRevokedForAppWithFullApiConsentGaUxEnabled_ppApiAndSystemServer()
            throws PackageManager.NameNotFoundException, RemoteException {
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
    public void testIsFledgeConsentRevokedForAppWithoutPrivacySandboxConsentGaUxDisabled_ppApiOnly()
            throws PackageManager.NameNotFoundException {
        when(mMockFlags.getGaUxFeatureEnabled()).thenReturn(false);
        doReturn(mPackageManagerMock).when(mContextSpy).getPackageManager();
        mConsentManager.disable(mContextSpy);
        assertFalse(mConsentManager.getConsent().isGiven());

        ExtendedMockito.verify(() -> UiStatsLogger.logOptOutSelected(mContextSpy));
        ExtendedMockito.verify(() -> UiStatsLogger.logResetMeasurement(mContextSpy));

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
    public void testIsFledgeConsentRevokedForAppWithoutPrivacySandboxConsentGaUxDisabled_sysServer()
            throws RemoteException {
        when(mMockFlags.getGaUxFeatureEnabled()).thenReturn(false);
        mConsentManager = getConsentManagerByConsentSourceOfTruth(Flags.SYSTEM_SERVER_ONLY);
        doReturn(ConsentParcel.createRevokedConsent(ConsentParcel.ALL_API))
                .when(mMockIAdServicesManager)
                .getConsent(ConsentParcel.ALL_API);
        assertFalse(mConsentManager.getConsent().isGiven());

        assertTrue(
                mConsentManager.isFledgeConsentRevokedForApp(
                        AppConsentDaoFixture.APP10_PACKAGE_NAME));
        assertTrue(
                mConsentManager.isFledgeConsentRevokedForApp(
                        AppConsentDaoFixture.APP20_PACKAGE_NAME));
    }

    @Test
    public void testIsFledgeConsentRevokedForAppWithoutPrivacySandboxConsentGaUxDisabled_bothSrc()
            throws RemoteException {
        when(mMockFlags.getGaUxFeatureEnabled()).thenReturn(false);
        mConsentManager = getConsentManagerByConsentSourceOfTruth(Flags.PPAPI_AND_SYSTEM_SERVER);
        doReturn(ConsentParcel.createRevokedConsent(ConsentParcel.ALL_API))
                .when(mMockIAdServicesManager)
                .getConsent(ConsentParcel.ALL_API);
        assertFalse(mConsentManager.getConsent().isGiven());

        assertTrue(
                mConsentManager.isFledgeConsentRevokedForApp(
                        AppConsentDaoFixture.APP10_PACKAGE_NAME));
        assertTrue(
                mConsentManager.isFledgeConsentRevokedForApp(
                        AppConsentDaoFixture.APP20_PACKAGE_NAME));
    }

    @Test
    public void testIsFledgeConsentRevokedForAppWithoutPrivacySandboxConsentGaUxEnabled_ppApiOnly()
            throws PackageManager.NameNotFoundException {
        when(mMockFlags.getGaUxFeatureEnabled()).thenReturn(true);
        doReturn(mPackageManagerMock).when(mContextSpy).getPackageManager();
        mConsentManager.disable(mContextSpy, AdServicesApiType.FLEDGE);
        assertFalse(mConsentManager.getConsent(AdServicesApiType.FLEDGE).isGiven());

        ExtendedMockito.verify(
                () -> UiStatsLogger.logOptOutSelected(mContextSpy, AdServicesApiType.FLEDGE));

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
            throws RemoteException {
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
            throws RemoteException {
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
    public void testIsFledgeConsentRevokedForNotFoundAppGaUxDisabledThrows_ppApiOnly()
            throws PackageManager.NameNotFoundException {
        when(mMockFlags.getGaUxFeatureEnabled()).thenReturn(false);
        mConsentManager.enable(mContextSpy);
        assertTrue(mConsentManager.getConsent().isGiven());

        ExtendedMockito.verify(() -> UiStatsLogger.logOptInSelected(mContextSpy));
        ExtendedMockito.verify(() -> UiStatsLogger.logResetMeasurement(mContextSpy));

        mockThrowExceptionOnGetPackageUid(AppConsentDaoFixture.APP_NOT_FOUND_PACKAGE_NAME);
        assertThrows(
                IllegalArgumentException.class,
                () ->
                        mConsentManager.isFledgeConsentRevokedForApp(
                                AppConsentDaoFixture.APP_NOT_FOUND_PACKAGE_NAME));
    }

    @Test
    public void testIsFledgeConsentRevokedForNotFoundAppGaUxDisabledThrows_systemServerOnly()
            throws PackageManager.NameNotFoundException, RemoteException {
        when(mMockFlags.getGaUxFeatureEnabled()).thenReturn(false);
        mConsentManager = getConsentManagerByConsentSourceOfTruth(Flags.SYSTEM_SERVER_ONLY);
        doReturn(ConsentParcel.createGivenConsent(ConsentParcel.ALL_API))
                .when(mMockIAdServicesManager)
                .getConsent(ConsentParcel.ALL_API);
        assertTrue(mConsentManager.getConsent().isGiven());

        mockThrowExceptionOnGetPackageUid(AppConsentDaoFixture.APP_NOT_FOUND_PACKAGE_NAME);
        assertThrows(
                IllegalArgumentException.class,
                () ->
                        mConsentManager.isFledgeConsentRevokedForApp(
                                AppConsentDaoFixture.APP_NOT_FOUND_PACKAGE_NAME));
    }

    @Test
    public void testIsFledgeConsentRevokedForNotFoundAppGaUxDisabledThrows_ppApiAndSystemServer()
            throws PackageManager.NameNotFoundException, RemoteException {
        when(mMockFlags.getGaUxFeatureEnabled()).thenReturn(false);
        mConsentManager = getConsentManagerByConsentSourceOfTruth(Flags.PPAPI_AND_SYSTEM_SERVER);
        doReturn(ConsentParcel.createGivenConsent(ConsentParcel.ALL_API))
                .when(mMockIAdServicesManager)
                .getConsent(ConsentParcel.ALL_API);
        assertTrue(mConsentManager.getConsent().isGiven());

        mockThrowExceptionOnGetPackageUid(AppConsentDaoFixture.APP_NOT_FOUND_PACKAGE_NAME);
        assertThrows(
                IllegalArgumentException.class,
                () ->
                        mConsentManager.isFledgeConsentRevokedForApp(
                                AppConsentDaoFixture.APP_NOT_FOUND_PACKAGE_NAME));
    }

    @Test
    public void testIsFledgeConsentRevokedForNotFoundAppGaUxEnabledThrows_ppApiOnly()
            throws PackageManager.NameNotFoundException {
        when(mMockFlags.getGaUxFeatureEnabled()).thenReturn(true);
        mConsentManager.enable(mContextSpy, AdServicesApiType.FLEDGE);
        assertTrue(mConsentManager.getConsent(AdServicesApiType.FLEDGE).isGiven());

        ExtendedMockito.verify(
                () -> UiStatsLogger.logOptInSelected(mContextSpy, AdServicesApiType.FLEDGE));

        mockThrowExceptionOnGetPackageUid(AppConsentDaoFixture.APP_NOT_FOUND_PACKAGE_NAME);
        assertThrows(
                IllegalArgumentException.class,
                () ->
                        mConsentManager.isFledgeConsentRevokedForApp(
                                AppConsentDaoFixture.APP_NOT_FOUND_PACKAGE_NAME));
    }

    @Test
    public void testIsFledgeConsentRevokedForNotFoundAppGaUxEnabledThrows_systemServerOnly()
            throws PackageManager.NameNotFoundException, RemoteException {
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
            throws PackageManager.NameNotFoundException, RemoteException {
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

    @Test
    public void
            testIsFledgeConsentRevokedForAppAfterSetFledgeUseWithFullApiConsentGaUxDisabled_ppApi()
                    throws IOException, PackageManager.NameNotFoundException {
        when(mMockFlags.getGaUxFeatureEnabled()).thenReturn(false);
        mConsentManager.enable(mContextSpy);
        assertTrue(mConsentManager.getConsent().isGiven());

        ExtendedMockito.verify(() -> UiStatsLogger.logOptInSelected(mContextSpy));
        ExtendedMockito.verify(() -> UiStatsLogger.logResetMeasurement(mContextSpy));

        mockGetPackageUid(AppConsentDaoFixture.APP10_PACKAGE_NAME, AppConsentDaoFixture.APP10_UID);
        mockGetPackageUid(AppConsentDaoFixture.APP20_PACKAGE_NAME, AppConsentDaoFixture.APP20_UID);
        mockGetPackageUid(AppConsentDaoFixture.APP30_PACKAGE_NAME, AppConsentDaoFixture.APP30_UID);

        mDatastore.put(AppConsentDaoFixture.APP10_DATASTORE_KEY, false);
        mDatastore.put(AppConsentDaoFixture.APP20_DATASTORE_KEY, true);

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
            testIsFledgeConsentRevokedForAppAfterSetFledgeUseWithFullApiConsentGaUxDisabled_sysSer()
                    throws PackageManager.NameNotFoundException, RemoteException {
        when(mMockFlags.getGaUxFeatureEnabled()).thenReturn(false);
        mConsentManager = getConsentManagerByConsentSourceOfTruth(Flags.SYSTEM_SERVER_ONLY);
        doReturn(ConsentParcel.createGivenConsent(ConsentParcel.ALL_API))
                .when(mMockIAdServicesManager)
                .getConsent(ConsentParcel.ALL_API);
        assertTrue(mConsentManager.getConsent().isGiven());

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
            testIsFledgeConsentRevokedForAppAfterSetFledgeUseWithFullApiConsentGaUxDisabled_both()
                    throws PackageManager.NameNotFoundException, RemoteException {
        when(mMockFlags.getGaUxFeatureEnabled()).thenReturn(false);
        mConsentManager = getConsentManagerByConsentSourceOfTruth(Flags.PPAPI_AND_SYSTEM_SERVER);
        doReturn(ConsentParcel.createGivenConsent(ConsentParcel.ALL_API))
                .when(mMockIAdServicesManager)
                .getConsent(ConsentParcel.ALL_API);
        assertTrue(mConsentManager.getConsent().isGiven());

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
            testIsFledgeConsentRevokedForAppAfterSetFledgeUseWithFullApiConsentGaUxEnabled_ppApi()
                    throws IOException, PackageManager.NameNotFoundException {
        when(mMockFlags.getGaUxFeatureEnabled()).thenReturn(true);
        mConsentManager.enable(mContextSpy, AdServicesApiType.FLEDGE);
        assertTrue(mConsentManager.getConsent(AdServicesApiType.FLEDGE).isGiven());

        ExtendedMockito.verify(
                () -> UiStatsLogger.logOptInSelected(mContextSpy, AdServicesApiType.FLEDGE));

        mockGetPackageUid(AppConsentDaoFixture.APP10_PACKAGE_NAME, AppConsentDaoFixture.APP10_UID);
        mockGetPackageUid(AppConsentDaoFixture.APP20_PACKAGE_NAME, AppConsentDaoFixture.APP20_UID);
        mockGetPackageUid(AppConsentDaoFixture.APP30_PACKAGE_NAME, AppConsentDaoFixture.APP30_UID);

        mDatastore.put(AppConsentDaoFixture.APP10_DATASTORE_KEY, false);
        mDatastore.put(AppConsentDaoFixture.APP20_DATASTORE_KEY, true);

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
                    throws PackageManager.NameNotFoundException, RemoteException {
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
                    throws PackageManager.NameNotFoundException, RemoteException {
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
            testIsFledgeConsentRevokedForAppSetFledgeUseNoPrivacySandboxConsentGaUxDisabled_ppApi()
                    throws PackageManager.NameNotFoundException {
        when(mMockFlags.getGaUxFeatureEnabled()).thenReturn(false);
        doReturn(mPackageManagerMock).when(mContextSpy).getPackageManager();
        mConsentManager.disable(mContextSpy);
        assertFalse(mConsentManager.getConsent().isGiven());

        ExtendedMockito.verify(() -> UiStatsLogger.logOptOutSelected(mContextSpy));
        ExtendedMockito.verify(() -> UiStatsLogger.logResetMeasurement(mContextSpy));

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
            testIsFledgeConsentRevokedForAppSetFledgeUseNoPrivacySandboxConsentGaUxDisabled_sysSer()
                    throws RemoteException {
        when(mMockFlags.getGaUxFeatureEnabled()).thenReturn(false);
        mConsentManager = getConsentManagerByConsentSourceOfTruth(Flags.SYSTEM_SERVER_ONLY);
        doReturn(ConsentParcel.createRevokedConsent(ConsentParcel.ALL_API))
                .when(mMockIAdServicesManager)
                .getConsent(ConsentParcel.ALL_API);
        assertFalse(mConsentManager.getConsent().isGiven());

        assertTrue(
                mConsentManager.isFledgeConsentRevokedForAppAfterSettingFledgeUse(
                        AppConsentDaoFixture.APP10_PACKAGE_NAME));
        assertTrue(
                mConsentManager.isFledgeConsentRevokedForAppAfterSettingFledgeUse(
                        AppConsentDaoFixture.APP20_PACKAGE_NAME));
    }

    @Test
    public void
            testIsFledgeConsentRevokedForAppSetFledgeUseNoPrivacySandboxConsentGaUxDisabled_both()
                    throws RemoteException {
        when(mMockFlags.getGaUxFeatureEnabled()).thenReturn(false);
        mConsentManager = getConsentManagerByConsentSourceOfTruth(Flags.PPAPI_AND_SYSTEM_SERVER);
        doReturn(ConsentParcel.createRevokedConsent(ConsentParcel.ALL_API))
                .when(mMockIAdServicesManager)
                .getConsent(ConsentParcel.ALL_API);
        assertFalse(mConsentManager.getConsent().isGiven());

        assertTrue(
                mConsentManager.isFledgeConsentRevokedForAppAfterSettingFledgeUse(
                        AppConsentDaoFixture.APP10_PACKAGE_NAME));
        assertTrue(
                mConsentManager.isFledgeConsentRevokedForAppAfterSettingFledgeUse(
                        AppConsentDaoFixture.APP20_PACKAGE_NAME));
    }

    @Test
    public void
            testIsFledgeConsentRevokedForAppSetFledgeUseNoPrivacySandboxConsentGaUxEnabled_ppApi()
                    throws PackageManager.NameNotFoundException {
        when(mMockFlags.getGaUxFeatureEnabled()).thenReturn(true);
        doReturn(mPackageManagerMock).when(mContextSpy).getPackageManager();
        mConsentManager.disable(mContextSpy, AdServicesApiType.FLEDGE);
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
                    throws RemoteException {
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
                    throws RemoteException {
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
            throws PackageManager.NameNotFoundException {
        when(mMockFlags.getGaUxFeatureEnabled()).thenReturn(false);
        mConsentManager.enable(mContextSpy);
        assertTrue(mConsentManager.getConsent().isGiven());

        ExtendedMockito.verify(() -> UiStatsLogger.logOptInSelected(mContextSpy));
        ExtendedMockito.verify(() -> UiStatsLogger.logResetMeasurement(mContextSpy));

        mockThrowExceptionOnGetPackageUid(AppConsentDaoFixture.APP_NOT_FOUND_PACKAGE_NAME);
        assertThrows(
                IllegalArgumentException.class,
                () ->
                        mConsentManager.isFledgeConsentRevokedForAppAfterSettingFledgeUse(
                                AppConsentDaoFixture.APP_NOT_FOUND_PACKAGE_NAME));
    }

    @Test
    public void testIsFledgeConsentRevokedForAppAfterSettingFledgeUseThrows_systemServerOnly()
            throws PackageManager.NameNotFoundException, RemoteException {
        when(mMockFlags.getGaUxFeatureEnabled()).thenReturn(false);
        mConsentManager = getConsentManagerByConsentSourceOfTruth(Flags.SYSTEM_SERVER_ONLY);
        doReturn(ConsentParcel.createGivenConsent(ConsentParcel.ALL_API))
                .when(mMockIAdServicesManager)
                .getConsent(ConsentParcel.ALL_API);
        assertTrue(mConsentManager.getConsent().isGiven());

        mockThrowExceptionOnGetPackageUid(AppConsentDaoFixture.APP_NOT_FOUND_PACKAGE_NAME);
        assertThrows(
                IllegalArgumentException.class,
                () ->
                        mConsentManager.isFledgeConsentRevokedForAppAfterSettingFledgeUse(
                                AppConsentDaoFixture.APP_NOT_FOUND_PACKAGE_NAME));
    }

    @Test
    public void testIsFledgeConsentRevokedForAppAfterSettingFledgeUseThrows_ppApiAndSystemServer()
            throws PackageManager.NameNotFoundException, RemoteException {
        when(mMockFlags.getGaUxFeatureEnabled()).thenReturn(false);
        mConsentManager = getConsentManagerByConsentSourceOfTruth(Flags.PPAPI_AND_SYSTEM_SERVER);
        doReturn(ConsentParcel.createGivenConsent(ConsentParcel.ALL_API))
                .when(mMockIAdServicesManager)
                .getConsent(ConsentParcel.ALL_API);
        assertTrue(mConsentManager.getConsent().isGiven());

        mockThrowExceptionOnGetPackageUid(AppConsentDaoFixture.APP_NOT_FOUND_PACKAGE_NAME);
        assertThrows(
                IllegalArgumentException.class,
                () ->
                        mConsentManager.isFledgeConsentRevokedForAppAfterSettingFledgeUse(
                                AppConsentDaoFixture.APP_NOT_FOUND_PACKAGE_NAME));
    }

    @Test
    public void testGetKnownAppsWithConsent_ppApiOnly()
            throws IOException, PackageManager.NameNotFoundException {
        mConsentManager.enable(mContextSpy);
        assertTrue(mConsentManager.getConsent().isGiven());

        ExtendedMockito.verify(() -> UiStatsLogger.logOptInSelected(mContextSpy));
        ExtendedMockito.verify(() -> UiStatsLogger.logResetMeasurement(mContextSpy));

        mockGetPackageUid(AppConsentDaoFixture.APP10_PACKAGE_NAME, AppConsentDaoFixture.APP10_UID);
        mockGetPackageUid(AppConsentDaoFixture.APP20_PACKAGE_NAME, AppConsentDaoFixture.APP20_UID);
        mockGetPackageUid(AppConsentDaoFixture.APP30_PACKAGE_NAME, AppConsentDaoFixture.APP30_UID);

        mDatastore.put(AppConsentDaoFixture.APP10_DATASTORE_KEY, false);
        mDatastore.put(AppConsentDaoFixture.APP20_DATASTORE_KEY, false);
        mDatastore.put(AppConsentDaoFixture.APP30_DATASTORE_KEY, false);
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
    public void testGetKnownAppsWithConsent_systemServerOnly() throws RemoteException {
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

        verify(mAppConsentDao, times(2)).getInstalledPackages();
        verifyNoMoreInteractions(mAppConsentDao);

        // all apps have received a consent
        assertThat(knownAppsWithConsent).hasSize(3);
        assertThat(appsWithRevokedConsent).isEmpty();
    }

    @Test
    public void testGetKnownAppsWithConsent_ppApiAndSystemServer() throws RemoteException {
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

        verify(mAppConsentDao, times(2)).getInstalledPackages();
        verifyNoMoreInteractions(mAppConsentDao);

        // all apps have received a consent
        assertThat(knownAppsWithConsent).hasSize(3);
        assertThat(appsWithRevokedConsent).isEmpty();
    }

    @Test
    public void testGetKnownAppsWithConsentAfterConsentForOneOfThemWasRevoked_ppApiOnly()
            throws IOException, PackageManager.NameNotFoundException {
        doNothing().when(mCustomAudienceDaoMock).deleteCustomAudienceDataByOwner(any());

        mConsentManager.enable(mContextSpy);
        assertTrue(mConsentManager.getConsent().isGiven());

        ExtendedMockito.verify(() -> UiStatsLogger.logOptInSelected(mContextSpy));
        ExtendedMockito.verify(() -> UiStatsLogger.logResetMeasurement(mContextSpy));

        mockGetPackageUid(AppConsentDaoFixture.APP10_PACKAGE_NAME, AppConsentDaoFixture.APP10_UID);
        mockGetPackageUid(AppConsentDaoFixture.APP20_PACKAGE_NAME, AppConsentDaoFixture.APP20_UID);
        mockGetPackageUid(AppConsentDaoFixture.APP30_PACKAGE_NAME, AppConsentDaoFixture.APP30_UID);

        mDatastore.put(AppConsentDaoFixture.APP10_DATASTORE_KEY, false);
        mDatastore.put(AppConsentDaoFixture.APP20_DATASTORE_KEY, false);
        mDatastore.put(AppConsentDaoFixture.APP30_DATASTORE_KEY, false);
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
        verify(mCustomAudienceDaoMock).deleteCustomAudienceDataByOwner(app.getPackageName());
    }

    @Test
    public void testGetKnownAppsWithConsentAfterConsentForOneOfThemWasRevokedAndRestored_ppApiOnly()
            throws IOException, PackageManager.NameNotFoundException {
        doNothing().when(mCustomAudienceDaoMock).deleteCustomAudienceDataByOwner(any());

        mConsentManager.enable(mContextSpy);
        assertTrue(mConsentManager.getConsent().isGiven());

        ExtendedMockito.verify(() -> UiStatsLogger.logOptInSelected(mContextSpy));
        ExtendedMockito.verify(() -> UiStatsLogger.logResetMeasurement(mContextSpy));

        mockGetPackageUid(AppConsentDaoFixture.APP10_PACKAGE_NAME, AppConsentDaoFixture.APP10_UID);
        mockGetPackageUid(AppConsentDaoFixture.APP20_PACKAGE_NAME, AppConsentDaoFixture.APP20_UID);
        mockGetPackageUid(AppConsentDaoFixture.APP30_PACKAGE_NAME, AppConsentDaoFixture.APP30_UID);

        mDatastore.put(AppConsentDaoFixture.APP10_DATASTORE_KEY, false);
        mDatastore.put(AppConsentDaoFixture.APP20_DATASTORE_KEY, false);
        mDatastore.put(AppConsentDaoFixture.APP30_DATASTORE_KEY, false);
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
        verify(mCustomAudienceDaoMock).deleteCustomAudienceDataByOwner(app.getPackageName());

        // restore consent for first app
        mConsentManager.restoreConsentForApp(app);
        knownAppsWithConsent = mConsentManager.getKnownAppsWithConsent();
        appsWithRevokedConsent = mConsentManager.getAppsWithRevokedConsent();

        // all apps have received a consent
        assertThat(knownAppsWithConsent).hasSize(3);
        assertThat(appsWithRevokedConsent).isEmpty();
    }

    @Test
    public void testSetConsentForApp_ppApiOnly()
            throws IOException, PackageManager.NameNotFoundException {
        mConsentManager.enable(mContextSpy);
        assertTrue(mConsentManager.getConsent().isGiven());

        ExtendedMockito.verify(() -> UiStatsLogger.logOptInSelected(mContextSpy));
        ExtendedMockito.verify(() -> UiStatsLogger.logResetMeasurement(mContextSpy));

        mockGetPackageUid(AppConsentDaoFixture.APP10_PACKAGE_NAME, AppConsentDaoFixture.APP10_UID);

        mConsentManager.revokeConsentForApp(App.create(AppConsentDaoFixture.APP10_PACKAGE_NAME));
        assertTrue(
                mConsentManager.isFledgeConsentRevokedForApp(
                        AppConsentDaoFixture.APP10_PACKAGE_NAME));

        mConsentManager.restoreConsentForApp(App.create(AppConsentDaoFixture.APP10_PACKAGE_NAME));
        assertFalse(
                mConsentManager.isFledgeConsentRevokedForApp(
                        AppConsentDaoFixture.APP10_PACKAGE_NAME));
    }

    @Test
    public void testSetConsentForApp_systemServerOnly()
            throws IOException, PackageManager.NameNotFoundException, RemoteException {
        mConsentManager = getConsentManagerByConsentSourceOfTruth(Flags.SYSTEM_SERVER_ONLY);
        doReturn(ConsentParcel.createGivenConsent(ConsentParcel.ALL_API))
                .when(mMockIAdServicesManager)
                .getConsent(ConsentParcel.ALL_API);
        assertTrue(mConsentManager.getConsent().isGiven());

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
    }

    @Test
    public void testSetConsentForApp_ppApiAndSystemServer()
            throws IOException, PackageManager.NameNotFoundException, RemoteException {
        mConsentManager = getConsentManagerByConsentSourceOfTruth(Flags.PPAPI_AND_SYSTEM_SERVER);
        doReturn(ConsentParcel.createGivenConsent(ConsentParcel.ALL_API))
                .when(mMockIAdServicesManager)
                .getConsent(ConsentParcel.ALL_API);
        assertTrue(mConsentManager.getConsent().isGiven());

        mockGetPackageUid(AppConsentDaoFixture.APP10_PACKAGE_NAME, AppConsentDaoFixture.APP10_UID);

        mConsentManager.revokeConsentForApp(App.create(AppConsentDaoFixture.APP10_PACKAGE_NAME));
        verify(mMockIAdServicesManager)
                .setConsentForApp(
                        AppConsentDaoFixture.APP10_PACKAGE_NAME,
                        AppConsentDaoFixture.APP10_UID,
                        true);
        assertEquals(Boolean.TRUE, mDatastore.get(AppConsentDaoFixture.APP10_DATASTORE_KEY));

        mConsentManager.restoreConsentForApp(App.create(AppConsentDaoFixture.APP10_PACKAGE_NAME));
        verify(mMockIAdServicesManager)
                .setConsentForApp(
                        AppConsentDaoFixture.APP10_PACKAGE_NAME,
                        AppConsentDaoFixture.APP10_UID,
                        false);
        assertEquals(Boolean.FALSE, mDatastore.get(AppConsentDaoFixture.APP10_DATASTORE_KEY));
    }

    @Test
    public void clearConsentForUninstalledApp_ppApiOnly()
            throws PackageManager.NameNotFoundException, IOException {
        mockGetPackageUid(AppConsentDaoFixture.APP10_PACKAGE_NAME, AppConsentDaoFixture.APP10_UID);

        mConsentManager.restoreConsentForApp(App.create(AppConsentDaoFixture.APP10_PACKAGE_NAME));
        assertEquals(Boolean.FALSE, mDatastore.get(AppConsentDaoFixture.APP10_DATASTORE_KEY));
        mConsentManager.clearConsentForUninstalledApp(
                AppConsentDaoFixture.APP10_PACKAGE_NAME, AppConsentDaoFixture.APP10_UID);
        assertNull(mDatastore.get(AppConsentDaoFixture.APP10_DATASTORE_KEY));
    }

    @Test
    public void clearConsentForUninstalledApp_systemServerOnly() throws RemoteException {
        mConsentManager = getConsentManagerByConsentSourceOfTruth(Flags.SYSTEM_SERVER_ONLY);
        mConsentManager.clearConsentForUninstalledApp(
                AppConsentDaoFixture.APP10_PACKAGE_NAME, AppConsentDaoFixture.APP10_UID);
        verify(mMockIAdServicesManager)
                .clearConsentForUninstalledApp(
                        AppConsentDaoFixture.APP10_PACKAGE_NAME, AppConsentDaoFixture.APP10_UID);
    }

    @Test
    public void clearConsentForUninstalledApp_ppApiAndSystemServer()
            throws PackageManager.NameNotFoundException, IOException, RemoteException {
        mConsentManager = getConsentManagerByConsentSourceOfTruth(Flags.PPAPI_AND_SYSTEM_SERVER);
        mockGetPackageUid(AppConsentDaoFixture.APP10_PACKAGE_NAME, AppConsentDaoFixture.APP10_UID);

        mConsentManager.restoreConsentForApp(App.create(AppConsentDaoFixture.APP10_PACKAGE_NAME));
        assertEquals(Boolean.FALSE, mDatastore.get(AppConsentDaoFixture.APP10_DATASTORE_KEY));
        verify(mMockIAdServicesManager)
                .setConsentForApp(
                        AppConsentDaoFixture.APP10_PACKAGE_NAME,
                        AppConsentDaoFixture.APP10_UID,
                        false);
        mConsentManager.clearConsentForUninstalledApp(
                AppConsentDaoFixture.APP10_PACKAGE_NAME, AppConsentDaoFixture.APP10_UID);
        assertNull(mDatastore.get(AppConsentDaoFixture.APP10_DATASTORE_KEY));
        verify(mMockIAdServicesManager)
                .clearConsentForUninstalledApp(
                        AppConsentDaoFixture.APP10_PACKAGE_NAME, AppConsentDaoFixture.APP10_UID);
    }

    @Test
    public void clearConsentForUninstalledAppWithoutUid_ppApiOnly() throws IOException {
        mDatastore.put(AppConsentDaoFixture.APP10_DATASTORE_KEY, true);
        mDatastore.put(AppConsentDaoFixture.APP20_DATASTORE_KEY, true);
        mDatastore.put(AppConsentDaoFixture.APP30_DATASTORE_KEY, false);

        mConsentManager.clearConsentForUninstalledApp(AppConsentDaoFixture.APP20_PACKAGE_NAME);

        assertEquals(true, mDatastore.get(AppConsentDaoFixture.APP10_DATASTORE_KEY));
        assertNull(mDatastore.get(AppConsentDaoFixture.APP20_DATASTORE_KEY));
        assertEquals(false, mDatastore.get(AppConsentDaoFixture.APP30_DATASTORE_KEY));

        verify(mAppConsentDao).clearConsentForUninstalledApp(anyString());
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
        doReturn(expectedKnownTopicsWithConsent).when(mTopicsWorker).getKnownTopicsWithConsent();

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
                .when(mTopicsWorker)
                .getTopicsWithRevokedConsent();

        ImmutableList<Topic> topicsWithRevokedConsent =
                mConsentManager.getTopicsWithRevokedConsent();

        assertThat(topicsWithRevokedConsent)
                .containsExactlyElementsIn(expectedTopicsWithRevokedConsent);
    }

    @Test
    public void testResetAllAppConsentAndAppData_ppApiOnly()
            throws IOException, PackageManager.NameNotFoundException {
        doNothing().when(mCustomAudienceDaoMock).deleteAllCustomAudienceData();

        // Prepopulate with consent data for some apps
        mConsentManager.enable(mContextSpy);
        assertTrue(mConsentManager.getConsent().isGiven());

        ExtendedMockito.verify(() -> UiStatsLogger.logOptInSelected(mContextSpy));
        ExtendedMockito.verify(() -> UiStatsLogger.logResetMeasurement(mContextSpy));

        mockGetPackageUid(AppConsentDaoFixture.APP10_PACKAGE_NAME, AppConsentDaoFixture.APP10_UID);
        mockGetPackageUid(AppConsentDaoFixture.APP20_PACKAGE_NAME, AppConsentDaoFixture.APP20_UID);
        mockGetPackageUid(AppConsentDaoFixture.APP30_PACKAGE_NAME, AppConsentDaoFixture.APP30_UID);

        mDatastore.put(AppConsentDaoFixture.APP10_DATASTORE_KEY, false);
        mDatastore.put(AppConsentDaoFixture.APP20_DATASTORE_KEY, true);
        mDatastore.put(AppConsentDaoFixture.APP30_DATASTORE_KEY, false);
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
        verify(mCustomAudienceDaoMock, times(2)).deleteAllCustomAudienceData();
    }

    @Test
    public void testResetAllAppConsentAndAppData_systemServerOnly()
            throws IOException, RemoteException {
        doNothing().when(mCustomAudienceDaoMock).deleteAllCustomAudienceData();

        mConsentManager = getConsentManagerByConsentSourceOfTruth(Flags.SYSTEM_SERVER_ONLY);

        mConsentManager.resetAppsAndBlockedApps();

        verify(mMockIAdServicesManager).clearAllAppConsentData();

        SystemClock.sleep(1000);
        verify(mCustomAudienceDaoMock).deleteAllCustomAudienceData();
    }

    @Test
    public void testResetAllAppConsentAndAppData_ppApiAndSystemServer()
            throws IOException, PackageManager.NameNotFoundException, RemoteException {
        doNothing().when(mCustomAudienceDaoMock).deleteAllCustomAudienceData();

        // Prepopulate with consent data for some apps
        mConsentManager = getConsentManagerByConsentSourceOfTruth(Flags.PPAPI_AND_SYSTEM_SERVER);
        mConsentManager.enable(mContextSpy);

        ExtendedMockito.verify(() -> UiStatsLogger.logOptInSelected(mContextSpy));
        ExtendedMockito.verify(() -> UiStatsLogger.logResetMeasurement(mContextSpy));

        doReturn(ConsentParcel.createGivenConsent(ConsentParcel.ALL_API))
                .when(mMockIAdServicesManager)
                .getConsent(ConsentParcel.ALL_API);
        assertTrue(mConsentManager.getConsent().isGiven());

        mockGetPackageUid(AppConsentDaoFixture.APP10_PACKAGE_NAME, AppConsentDaoFixture.APP10_UID);
        mockGetPackageUid(AppConsentDaoFixture.APP20_PACKAGE_NAME, AppConsentDaoFixture.APP20_UID);
        mockGetPackageUid(AppConsentDaoFixture.APP30_PACKAGE_NAME, AppConsentDaoFixture.APP30_UID);

        mDatastore.put(AppConsentDaoFixture.APP10_DATASTORE_KEY, false);
        mDatastore.put(AppConsentDaoFixture.APP20_DATASTORE_KEY, true);
        mDatastore.put(AppConsentDaoFixture.APP30_DATASTORE_KEY, false);
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
        verify(mCustomAudienceDaoMock, times(2)).deleteAllCustomAudienceData();
    }

    @Test
    public void testResetAllowedAppConsentAndAppData_ppApiOnly()
            throws IOException, PackageManager.NameNotFoundException {
        doNothing().when(mCustomAudienceDaoMock).deleteAllCustomAudienceData();

        // Prepopulate with consent data for some apps
        mConsentManager.enable(mContextSpy);
        assertTrue(mConsentManager.getConsent().isGiven());

        ExtendedMockito.verify(() -> UiStatsLogger.logOptInSelected(mContextSpy));
        ExtendedMockito.verify(() -> UiStatsLogger.logResetMeasurement(mContextSpy));

        mockGetPackageUid(AppConsentDaoFixture.APP10_PACKAGE_NAME, AppConsentDaoFixture.APP10_UID);
        mockGetPackageUid(AppConsentDaoFixture.APP20_PACKAGE_NAME, AppConsentDaoFixture.APP20_UID);
        mockGetPackageUid(AppConsentDaoFixture.APP30_PACKAGE_NAME, AppConsentDaoFixture.APP30_UID);

        mDatastore.put(AppConsentDaoFixture.APP10_DATASTORE_KEY, false);
        mDatastore.put(AppConsentDaoFixture.APP20_DATASTORE_KEY, true);
        mDatastore.put(AppConsentDaoFixture.APP30_DATASTORE_KEY, false);
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
        verify(mCustomAudienceDaoMock, times(2)).deleteAllCustomAudienceData();
    }

    @Test
    public void testResetAllowedAppConsentAndAppData_systemServerOnly()
            throws IOException, RemoteException {
        doNothing().when(mCustomAudienceDaoMock).deleteAllCustomAudienceData();

        // Prepopulate with consent data for some apps
        mConsentManager = getConsentManagerByConsentSourceOfTruth(Flags.SYSTEM_SERVER_ONLY);
        mConsentManager.resetApps();

        verify(mMockIAdServicesManager).clearKnownAppsWithConsent();

        SystemClock.sleep(1000);
        verify(mCustomAudienceDaoMock).deleteAllCustomAudienceData();
    }

    @Test
    public void testResetAllowedAppConsentAndAppData_ppApiAndSystemServer()
            throws IOException, PackageManager.NameNotFoundException, RemoteException {
        doNothing().when(mCustomAudienceDaoMock).deleteAllCustomAudienceData();

        // Prepopulate with consent data for some apps
        mConsentManager = getConsentManagerByConsentSourceOfTruth(Flags.PPAPI_AND_SYSTEM_SERVER);
        doReturn(ConsentParcel.createGivenConsent(ConsentParcel.ALL_API))
                .when(mMockIAdServicesManager)
                .getConsent(ConsentParcel.ALL_API);
        assertTrue(mConsentManager.getConsent().isGiven());
        mockGetPackageUid(AppConsentDaoFixture.APP10_PACKAGE_NAME, AppConsentDaoFixture.APP10_UID);
        mockGetPackageUid(AppConsentDaoFixture.APP20_PACKAGE_NAME, AppConsentDaoFixture.APP20_UID);
        mockGetPackageUid(AppConsentDaoFixture.APP30_PACKAGE_NAME, AppConsentDaoFixture.APP30_UID);

        mDatastore.put(AppConsentDaoFixture.APP10_DATASTORE_KEY, false);
        mDatastore.put(AppConsentDaoFixture.APP20_DATASTORE_KEY, true);
        mDatastore.put(AppConsentDaoFixture.APP30_DATASTORE_KEY, false);
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
        verify(mCustomAudienceDaoMock).deleteAllCustomAudienceData();
    }

    @Test
    public void testNotificationDisplayedRecorded_PpApiOnly() throws RemoteException {
        int consentSourceOfTruth = Flags.PPAPI_ONLY;
        ConsentManager spyConsentManager =
                getSpiedConsentManagerForMigrationTesting(
                        /* isGiven */ false, consentSourceOfTruth);

        assertThat(spyConsentManager.wasNotificationDisplayed()).isFalse();

        verify(mMockIAdServicesManager, never()).wasNotificationDisplayed();

        spyConsentManager.recordNotificationDisplayed();

        assertThat(spyConsentManager.wasNotificationDisplayed()).isTrue();

        verify(mMockIAdServicesManager, never()).wasNotificationDisplayed();
        verify(mMockIAdServicesManager, never()).recordNotificationDisplayed();
    }

    @Test
    public void testNotificationDisplayedRecorded_SystemServerOnly() throws RemoteException {
        int consentSourceOfTruth = Flags.SYSTEM_SERVER_ONLY;
        ConsentManager spyConsentManager =
                getSpiedConsentManagerForMigrationTesting(
                        /* isGiven */ false, consentSourceOfTruth);

        assertThat(spyConsentManager.wasNotificationDisplayed()).isFalse();

        verify(mMockIAdServicesManager).wasNotificationDisplayed();

        doReturn(true).when(mMockIAdServicesManager).wasNotificationDisplayed();
        spyConsentManager.recordNotificationDisplayed();

        assertThat(spyConsentManager.wasNotificationDisplayed()).isTrue();

        verify(mMockIAdServicesManager, times(2)).wasNotificationDisplayed();
        verify(mMockIAdServicesManager).recordNotificationDisplayed();

        // Verify notificationDisplayed is not set in PPAPI
        assertThat(mConsentDatastore.get(NOTIFICATION_DISPLAYED_ONCE)).isFalse();
    }

    @Test
    public void testNotificationDisplayedRecorded_PpApiAndSystemServer() throws RemoteException {
        int consentSourceOfTruth = Flags.PPAPI_AND_SYSTEM_SERVER;
        ConsentManager spyConsentManager =
                getSpiedConsentManagerForMigrationTesting(
                        /* isGiven */ false, consentSourceOfTruth);

        Boolean wasNotificationDisplayed = spyConsentManager.wasNotificationDisplayed();

        assertThat(wasNotificationDisplayed).isFalse();

        verify(mMockIAdServicesManager).wasNotificationDisplayed();

        doReturn(true).when(mMockIAdServicesManager).wasNotificationDisplayed();
        spyConsentManager.recordNotificationDisplayed();

        assertThat(spyConsentManager.wasNotificationDisplayed()).isTrue();

        verify(mMockIAdServicesManager, times(2)).wasNotificationDisplayed();
        verify(mMockIAdServicesManager).recordNotificationDisplayed();

        // Verify notificationDisplayed is also set in PPAPI
        assertThat(mConsentDatastore.get(NOTIFICATION_DISPLAYED_ONCE)).isTrue();
    }

    @Test
    public void testGaUxNotificationDisplayedRecorded_PpApiOnly() throws RemoteException {
        int consentSourceOfTruth = Flags.PPAPI_ONLY;
        ConsentManager spyConsentManager =
                getSpiedConsentManagerForMigrationTesting(
                        /* isGiven */ false, consentSourceOfTruth);

        assertThat(spyConsentManager.wasGaUxNotificationDisplayed()).isFalse();

        verify(mMockIAdServicesManager, never()).wasGaUxNotificationDisplayed();

        spyConsentManager.recordGaUxNotificationDisplayed();

        assertThat(spyConsentManager.wasGaUxNotificationDisplayed()).isTrue();

        verify(mMockIAdServicesManager, never()).wasGaUxNotificationDisplayed();
        verify(mMockIAdServicesManager, never()).recordGaUxNotificationDisplayed();
    }

    @Test
    public void testGaUxNotificationDisplayedRecorded_SystemServerOnly() throws RemoteException {
        int consentSourceOfTruth = Flags.SYSTEM_SERVER_ONLY;
        ConsentManager spyConsentManager =
                getSpiedConsentManagerForMigrationTesting(
                        /* isGiven */ false, consentSourceOfTruth);

        assertThat(spyConsentManager.wasGaUxNotificationDisplayed()).isFalse();

        verify(mMockIAdServicesManager).wasGaUxNotificationDisplayed();

        doReturn(true).when(mMockIAdServicesManager).wasGaUxNotificationDisplayed();
        spyConsentManager.recordGaUxNotificationDisplayed();

        assertThat(spyConsentManager.wasGaUxNotificationDisplayed()).isTrue();

        verify(mMockIAdServicesManager, times(2)).wasGaUxNotificationDisplayed();
        verify(mMockIAdServicesManager).recordGaUxNotificationDisplayed();

        // Verify notificationDisplayed is not set in PPAPI
        assertThat(mConsentDatastore.get(GA_UX_NOTIFICATION_DISPLAYED_ONCE)).isFalse();
    }

    @Test
    public void testGaUxNotificationDisplayedRecorded_PpApiAndSystemServer()
            throws RemoteException {
        int consentSourceOfTruth = Flags.PPAPI_AND_SYSTEM_SERVER;
        ConsentManager spyConsentManager =
                getSpiedConsentManagerForMigrationTesting(
                        /* isGiven */ false, consentSourceOfTruth);

        Boolean wasGaUxNotificationDisplayed = spyConsentManager.wasGaUxNotificationDisplayed();

        assertThat(wasGaUxNotificationDisplayed).isFalse();

        verify(mMockIAdServicesManager).wasGaUxNotificationDisplayed();

        doReturn(true).when(mMockIAdServicesManager).wasGaUxNotificationDisplayed();
        spyConsentManager.recordGaUxNotificationDisplayed();

        assertThat(spyConsentManager.wasGaUxNotificationDisplayed()).isTrue();

        verify(mMockIAdServicesManager, times(2)).wasGaUxNotificationDisplayed();
        verify(mMockIAdServicesManager).recordGaUxNotificationDisplayed();

        // Verify notificationDisplayed is also set in PPAPI
        assertThat(mConsentDatastore.get(GA_UX_NOTIFICATION_DISPLAYED_ONCE)).isTrue();
    }

    @Test
    public void testTopicsconsentPageDisplayedRecorded_PpApiOnly() throws RemoteException {
        int consentSourceOfTruth = Flags.PPAPI_ONLY;
        ConsentManager spyConsentManager =
                getSpiedConsentManagerForMigrationTesting(
                        /* isGiven */ false, consentSourceOfTruth);

        assertThat(spyConsentManager.wasTopicsConsentPageDisplayed()).isFalse();

        verify(mMockIAdServicesManager, never()).wasTopicsConsentPageDisplayed();

        spyConsentManager.recordTopicsConsentPageDisplayed();

        assertThat(spyConsentManager.wasTopicsConsentPageDisplayed()).isTrue();

        verify(mMockIAdServicesManager, never()).wasTopicsConsentPageDisplayed();
        verify(mMockIAdServicesManager, never()).recordTopicsConsentPageDisplayed();
    }

    @Test
    public void testTopicsConsentPageDisplayedRecorded_SystemServerOnly() throws RemoteException {
        int consentSourceOfTruth = Flags.SYSTEM_SERVER_ONLY;
        ConsentManager spyConsentManager =
                getSpiedConsentManagerForMigrationTesting(
                        /* isGiven */ false, consentSourceOfTruth);

        assertThat(spyConsentManager.wasTopicsConsentPageDisplayed()).isFalse();

        verify(mMockIAdServicesManager).wasTopicsConsentPageDisplayed();

        doReturn(true).when(mMockIAdServicesManager).wasTopicsConsentPageDisplayed();
        spyConsentManager.recordTopicsConsentPageDisplayed();

        assertThat(spyConsentManager.wasTopicsConsentPageDisplayed()).isTrue();

        verify(mMockIAdServicesManager, times(2)).wasTopicsConsentPageDisplayed();
        verify(mMockIAdServicesManager).recordTopicsConsentPageDisplayed();

        // Verify topics consent page displayed is not set in PPAPI
        assertThat(mConsentDatastore.get(TOPICS_CONSENT_PAGE_DISPLAYED)).isFalse();
    }

    @Test
    public void testTopicsConsentPageDisplayedRecorded_PpApiAndSystemServer()
            throws RemoteException {
        int consentSourceOfTruth = Flags.PPAPI_AND_SYSTEM_SERVER;
        ConsentManager spyConsentManager =
                getSpiedConsentManagerForMigrationTesting(
                        /* isGiven */ false, consentSourceOfTruth);

        Boolean wasTopicsConsentPageDisplayed = spyConsentManager.wasTopicsConsentPageDisplayed();

        assertThat(wasTopicsConsentPageDisplayed).isFalse();

        verify(mMockIAdServicesManager).wasTopicsConsentPageDisplayed();

        doReturn(true).when(mMockIAdServicesManager).wasTopicsConsentPageDisplayed();
        spyConsentManager.recordTopicsConsentPageDisplayed();

        assertThat(spyConsentManager.wasTopicsConsentPageDisplayed()).isTrue();

        verify(mMockIAdServicesManager, times(2)).wasTopicsConsentPageDisplayed();
        verify(mMockIAdServicesManager).recordTopicsConsentPageDisplayed();

        // Verify topics consent page displayed is also set in PPAPI
        assertThat(mConsentDatastore.get(TOPICS_CONSENT_PAGE_DISPLAYED)).isTrue();
    }

    @Test
    public void testFledgeAndMsmtConsentPageDisplayedRecorded_PpApiOnly() throws RemoteException {
        int consentSourceOfTruth = Flags.PPAPI_ONLY;
        ConsentManager spyConsentManager =
                getSpiedConsentManagerForMigrationTesting(
                        /* isGiven */ false, consentSourceOfTruth);

        assertThat(spyConsentManager.wasFledgeAndMsmtConsentPageDisplayed()).isFalse();

        verify(mMockIAdServicesManager, never()).wasFledgeAndMsmtConsentPageDisplayed();

        spyConsentManager.recordFledgeAndMsmtConsentPageDisplayed();

        assertThat(spyConsentManager.wasFledgeAndMsmtConsentPageDisplayed()).isTrue();

        verify(mMockIAdServicesManager, never()).wasFledgeAndMsmtConsentPageDisplayed();
        verify(mMockIAdServicesManager, never()).recordFledgeAndMsmtConsentPageDisplayed();
    }

    @Test
    public void testFledgeAndMsmtConsentPageDisplayedRecorded_SystemServerOnly()
            throws RemoteException {
        int consentSourceOfTruth = Flags.SYSTEM_SERVER_ONLY;
        ConsentManager spyConsentManager =
                getSpiedConsentManagerForMigrationTesting(
                        /* isGiven */ false, consentSourceOfTruth);

        assertThat(spyConsentManager.wasFledgeAndMsmtConsentPageDisplayed()).isFalse();

        verify(mMockIAdServicesManager).wasFledgeAndMsmtConsentPageDisplayed();

        doReturn(true).when(mMockIAdServicesManager).wasFledgeAndMsmtConsentPageDisplayed();
        spyConsentManager.recordFledgeAndMsmtConsentPageDisplayed();

        assertThat(spyConsentManager.wasFledgeAndMsmtConsentPageDisplayed()).isTrue();

        verify(mMockIAdServicesManager, times(2)).wasFledgeAndMsmtConsentPageDisplayed();
        verify(mMockIAdServicesManager).recordFledgeAndMsmtConsentPageDisplayed();

        // Verify fledge consent page displayed is not set in PPAPI
        assertThat(mConsentDatastore.get(FLEDGE_AND_MSMT_CONSENT_PAGE_DISPLAYED)).isFalse();
    }

    @Test
    public void testFledgeAndMsmtConsentPageDisplayedRecorded_PpApiAndSystemServer()
            throws RemoteException {
        int consentSourceOfTruth = Flags.PPAPI_AND_SYSTEM_SERVER;
        ConsentManager spyConsentManager =
                getSpiedConsentManagerForMigrationTesting(
                        /* isGiven */ false, consentSourceOfTruth);

        Boolean wasFledgeAndMsmtConsentPageDisplayed =
                spyConsentManager.wasFledgeAndMsmtConsentPageDisplayed();

        assertThat(wasFledgeAndMsmtConsentPageDisplayed).isFalse();

        verify(mMockIAdServicesManager).wasFledgeAndMsmtConsentPageDisplayed();

        doReturn(true).when(mMockIAdServicesManager).wasFledgeAndMsmtConsentPageDisplayed();
        spyConsentManager.recordFledgeAndMsmtConsentPageDisplayed();

        assertThat(spyConsentManager.wasFledgeAndMsmtConsentPageDisplayed()).isTrue();

        verify(mMockIAdServicesManager, times(2)).wasFledgeAndMsmtConsentPageDisplayed();
        verify(mMockIAdServicesManager).recordFledgeAndMsmtConsentPageDisplayed();

        // Verify fledge consent page displayed is also set in PPAPI
        assertThat(mConsentDatastore.get(FLEDGE_AND_MSMT_CONSENT_PAGE_DISPLAYED)).isTrue();
    }

    @Test
    public void testNotificationDisplayedRecorded_notSupportedFlag() throws RemoteException {
        int invalidConsentSourceOfTruth = 3;
        ConsentManager spyConsentManager =
                getSpiedConsentManagerForMigrationTesting(
                        /* isGiven */ false, invalidConsentSourceOfTruth);

        assertThrows(RuntimeException.class, spyConsentManager::recordNotificationDisplayed);
    }

    @Test
    public void testClearPpApiConsent() throws IOException {
        mConsentDatastore.put(CONSENT_KEY, true);
        mConsentDatastore.put(NOTIFICATION_DISPLAYED_ONCE, true);
        assertThat(mConsentDatastore.get(CONSENT_KEY)).isTrue();
        assertThat(mConsentDatastore.get(NOTIFICATION_DISPLAYED_ONCE)).isTrue();

        ConsentManager.clearPpApiConsent(mContextSpy, mConsentDatastore);
        assertThat(mConsentDatastore.get(CONSENT_KEY)).isNull();
        assertThat(mConsentDatastore.get(NOTIFICATION_DISPLAYED_ONCE)).isNull();

        // Verify this should only happen once
        mConsentDatastore.put(CONSENT_KEY, true);
        mConsentDatastore.put(NOTIFICATION_DISPLAYED_ONCE, true);
        assertThat(mConsentDatastore.get(CONSENT_KEY)).isTrue();
        assertThat(mConsentDatastore.get(NOTIFICATION_DISPLAYED_ONCE)).isTrue();
        // Consent is not cleared again
        ConsentManager.clearPpApiConsent(mContextSpy, mConsentDatastore);
        assertThat(mConsentDatastore.get(CONSENT_KEY)).isTrue();
        assertThat(mConsentDatastore.get(NOTIFICATION_DISPLAYED_ONCE)).isTrue();

        // Clear shared preference
        ConsentManager.resetSharedPreference(mContextSpy, SHARED_PREFS_KEY_PPAPI_HAS_CLEARED);
    }

    @Test
    public void testMigratePpApiConsentToSystemService() throws RemoteException, IOException {
        // Disable IPC calls
        doNothing().when(mMockIAdServicesManager).setConsent(any());
        doNothing().when(mMockIAdServicesManager).recordNotificationDisplayed();

        mConsentDatastore.put(CONSENT_KEY, true);
        mConsentDatastore.put(NOTIFICATION_DISPLAYED_ONCE, true);
        assertThat(mConsentDatastore.get(CONSENT_KEY)).isTrue();
        assertThat(mConsentDatastore.get(NOTIFICATION_DISPLAYED_ONCE)).isTrue();

        ConsentManager.migratePpApiConsentToSystemService(
                mContextSpy, mConsentDatastore, mAdServicesManager);

        verify(mMockIAdServicesManager).setConsent(any());
        verify(mMockIAdServicesManager).recordNotificationDisplayed();

        // Verify this should only happen once
        ConsentManager.migratePpApiConsentToSystemService(
                mContextSpy, mConsentDatastore, mAdServicesManager);
        verify(mMockIAdServicesManager).setConsent(any());
        verify(mMockIAdServicesManager).recordNotificationDisplayed();

        // Clear shared preference
        ConsentManager.resetSharedPreference(mContextSpy, SHARED_PREFS_KEY_HAS_MIGRATED);
    }

    @Test
    public void testResetSharedPreference() {
        SharedPreferences sharedPreferences =
                mContextSpy.getSharedPreferences(SHARED_PREFS_CONSENT, Context.MODE_PRIVATE);
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

        resetSharedPreference(mContextSpy, SHARED_PREFS_KEY_PPAPI_HAS_CLEARED);
        resetSharedPreference(mContextSpy, SHARED_PREFS_KEY_HAS_MIGRATED);

        assertThat(sharedPreferences.getBoolean(SHARED_PREFS_KEY_HAS_MIGRATED, /* defValue */ true))
                .isFalse();
        assertThat(sharedPreferences.getBoolean(SHARED_PREFS_KEY_HAS_MIGRATED, /* defValue */ true))
                .isFalse();
    }

    @Test
    public void testHandleConsentMigrationIfNeeded_PpApiOnly() {
        // Disable actual execution of internal methods
        ExtendedMockito.doNothing()
                .when(
                        () ->
                                ConsentManager.resetSharedPreference(
                                        mContextSpy, SHARED_PREFS_KEY_HAS_MIGRATED));
        ExtendedMockito.doNothing()
                .when(
                        () ->
                                ConsentManager.migratePpApiConsentToSystemService(
                                        mContextSpy, mConsentDatastore, mAdServicesManager));
        ExtendedMockito.doNothing()
                .when(() -> ConsentManager.clearPpApiConsent(mContextSpy, mConsentDatastore));

        int consentSourceOfTruth = Flags.PPAPI_ONLY;
        ConsentManager.handleConsentMigrationIfNeeded(
                mContextSpy, mConsentDatastore, mAdServicesManager, consentSourceOfTruth);

        ExtendedMockito.verify(
                () ->
                        ConsentManager.resetSharedPreference(
                                mContextSpy, SHARED_PREFS_KEY_HAS_MIGRATED));
        ExtendedMockito.verify(
                () ->
                        ConsentManager.migratePpApiConsentToSystemService(
                                mContextSpy, mConsentDatastore, mAdServicesManager),
                never());
        ExtendedMockito.verify(
                () -> ConsentManager.clearPpApiConsent(mContextSpy, mConsentDatastore), never());
    }

    @Test
    public void testHandleConsentMigrationIfNeeded_SystemServerOnly() {
        // Disable actual execution of internal methods
        ExtendedMockito.doNothing()
                .when(
                        () ->
                                ConsentManager.resetSharedPreference(
                                        mContextSpy, SHARED_PREFS_KEY_HAS_MIGRATED));
        ExtendedMockito.doNothing()
                .when(
                        () ->
                                ConsentManager.migratePpApiConsentToSystemService(
                                        mContextSpy, mConsentDatastore, mAdServicesManager));
        ExtendedMockito.doNothing()
                .when(() -> ConsentManager.clearPpApiConsent(mContextSpy, mConsentDatastore));

        int consentSourceOfTruth = Flags.SYSTEM_SERVER_ONLY;
        ConsentManager.handleConsentMigrationIfNeeded(
                mContextSpy, mConsentDatastore, mAdServicesManager, consentSourceOfTruth);

        ExtendedMockito.verify(
                () ->
                        ConsentManager.resetSharedPreference(
                                mContextSpy, SHARED_PREFS_KEY_HAS_MIGRATED),
                never());
        ExtendedMockito.verify(
                () ->
                        ConsentManager.migratePpApiConsentToSystemService(
                                mContextSpy, mConsentDatastore, mAdServicesManager));
        ExtendedMockito.verify(
                () -> ConsentManager.clearPpApiConsent(mContextSpy, mConsentDatastore));
    }

    @Test
    public void testHandleConsentMigrationIfNeeded_PpApiAndSystemServer() {
        // Disable actual execution of internal methods
        ExtendedMockito.doNothing()
                .when(
                        () ->
                                ConsentManager.resetSharedPreference(
                                        mContextSpy, SHARED_PREFS_KEY_HAS_MIGRATED));
        ExtendedMockito.doNothing()
                .when(
                        () ->
                                ConsentManager.migratePpApiConsentToSystemService(
                                        mContextSpy, mConsentDatastore, mAdServicesManager));
        ExtendedMockito.doNothing()
                .when(() -> ConsentManager.clearPpApiConsent(mContextSpy, mConsentDatastore));

        int consentSourceOfTruth = Flags.PPAPI_AND_SYSTEM_SERVER;
        ConsentManager.handleConsentMigrationIfNeeded(
                mContextSpy, mConsentDatastore, mAdServicesManager, consentSourceOfTruth);

        ExtendedMockito.verify(
                () ->
                        ConsentManager.resetSharedPreference(
                                mContextSpy, SHARED_PREFS_KEY_HAS_MIGRATED),
                never());
        ExtendedMockito.verify(
                () ->
                        ConsentManager.migratePpApiConsentToSystemService(
                                mContextSpy, mConsentDatastore, mAdServicesManager));
        ExtendedMockito.verify(
                () -> ConsentManager.clearPpApiConsent(mContextSpy, mConsentDatastore), never());
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
                                mCacheManager,
                                mBlockedTopicsManager,
                                mAppUpdateManager,
                                mMockFlags));
        // Enable TopicContributors feature
        when(mMockEpochManager.supportsTopicContributorFeature()).thenReturn(true);

        ConsentManager consentManager =
                new ConsentManager(
                        mContextSpy,
                        topicsWorker,
                        mAppConsentDao,
                        mEnrollmentDao,
                        mMeasurementImpl,
                        mCustomAudienceDaoMock,
                        mAdServicesManager,
                        mConsentDatastore,
                        mMockFlags,
                        Flags.PPAPI_ONLY);
        doNothing().when(mBlockedTopicsManager).blockTopic(any());
        doNothing().when(mBlockedTopicsManager).unblockTopic(any());
        // The actual usage is to invoke clearAllTopicsData() from TopicsWorker
        doNothing().when(topicsWorker).clearAllTopicsData(any());

        consentManager.revokeConsentForTopic(topic);
        consentManager.restoreConsentForTopic(topic);
        consentManager.resetTopics();

        verify(mBlockedTopicsManager).blockTopic(topic);
        verify(mBlockedTopicsManager).unblockTopic(topic);
        verify(topicsWorker).clearAllTopicsData(tablesToBlock);
    }

    @Test
    public void testLoggingSettingsUsageReportedOptInSelectedRow() {
        ExtendedMockito.doReturn(false)
                .when(() -> DeviceRegionProvider.isEuDevice(any(Context.class)));
        ConsentManager temporalConsentManager =
                getConsentManagerByConsentSourceOfTruth(Flags.PPAPI_ONLY);

        temporalConsentManager.enable(mContextSpy);

        verify(mUiStatsLogger, times(1)).logOptInSelected(mContextSpy);
    }

    @Test
    public void testLoggingSettingsUsageReportedOptInSelectedEu() {
        ExtendedMockito.doReturn(true)
                .when(() -> DeviceRegionProvider.isEuDevice(any(Context.class)));
        ConsentManager temporalConsentManager =
                getConsentManagerByConsentSourceOfTruth(Flags.PPAPI_ONLY);

        temporalConsentManager.enable(mContextSpy);

        verify(mUiStatsLogger, times(1)).logOptInSelected(mContextSpy);
    }

    @Test
    public void testConsentPerApiIsGivenAfterEnabling_PpApiOnly()
            throws RemoteException, IOException {
        when(mMockFlags.getGaUxFeatureEnabled()).thenReturn(true);
        boolean isGiven = true;
        int consentSourceOfTruth = Flags.PPAPI_ONLY;
        ConsentManager spyConsentManager =
                getSpiedConsentManagerForMigrationTesting(isGiven, consentSourceOfTruth);

        spyConsentManager.enable(mContextSpy, AdServicesApiType.TOPICS);

        assertThat(spyConsentManager.getConsent(AdServicesApiType.TOPICS).isGiven()).isTrue();
        verify(spyConsentManager)
                .setConsentPerApiToPpApi(eq(AdServicesApiType.TOPICS), eq(/* isGiven */ true));

        verify(spyConsentManager).resetTopicsAndBlockedTopics();
    }

    @Test
    public void testConsentPerApiIsGivenAfterEnabling_SystemServerOnly() throws RemoteException {
        when(mMockFlags.getGaUxFeatureEnabled()).thenReturn(true);
        boolean isGiven = true;
        int consentSourceOfTruth = Flags.SYSTEM_SERVER_ONLY;
        ConsentManager spyConsentManager =
                getSpiedConsentManagerForConsentPerApiTesting(
                        isGiven, consentSourceOfTruth, AdServicesApiType.TOPICS.toConsentApiType());

        spyConsentManager.enable(mContextSpy, AdServicesApiType.TOPICS);

        assertThat(spyConsentManager.getConsent(AdServicesApiType.TOPICS).isGiven()).isTrue();
        ExtendedMockito.verify(
                () ->
                        ConsentManager.setPerApiConsentToSystemServer(
                                any(),
                                eq(AdServicesApiType.TOPICS.toConsentApiType()),
                                eq(isGiven)));
        verify(mMockIAdServicesManager).getConsent(ConsentParcel.TOPICS);
        verify(spyConsentManager).resetTopicsAndBlockedTopics();
    }

    @Test
    public void testConsentPerApiIsGivenAfterEnabling_PpApiAndSystemServer()
            throws RemoteException, IOException {
        when(mMockFlags.getGaUxFeatureEnabled()).thenReturn(true);
        boolean isGiven = true;
        int consentSourceOfTruth = Flags.PPAPI_AND_SYSTEM_SERVER;
        ConsentManager spyConsentManager =
                getSpiedConsentManagerForConsentPerApiTesting(
                        isGiven, consentSourceOfTruth, AdServicesApiType.TOPICS.toConsentApiType());

        spyConsentManager.enable(mContextSpy, AdServicesApiType.TOPICS);

        assertThat(spyConsentManager.getConsent(AdServicesApiType.TOPICS).isGiven()).isTrue();
        ExtendedMockito.verify(
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
    public void testAllThreeConsentsPerApiAreGivenAggregatedConsentIsSet_PpApiOnly()
            throws RemoteException, IOException {
        when(mMockFlags.getGaUxFeatureEnabled()).thenReturn(true);
        boolean isGiven = true;
        int consentSourceOfTruth = Flags.PPAPI_ONLY;
        ConsentManager spyConsentManager =
                getSpiedConsentManagerForMigrationTesting(isGiven, consentSourceOfTruth);

        spyConsentManager.enable(mContextSpy, AdServicesApiType.TOPICS);
        spyConsentManager.enable(mContextSpy, AdServicesApiType.FLEDGE);
        spyConsentManager.enable(mContextSpy, AdServicesApiType.MEASUREMENTS);

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
    public void testAllConsentAreRevokedClenaupIsExecuted() throws IOException, RemoteException {
        when(mMockFlags.getGaUxFeatureEnabled()).thenReturn(true);
        boolean isGiven = true;
        int consentSourceOfTruth = Flags.PPAPI_ONLY;
        ConsentManager spyConsentManager =
                getSpiedConsentManagerForMigrationTesting(isGiven, consentSourceOfTruth);

        // set up the initial state
        spyConsentManager.enable(mContextSpy, AdServicesApiType.TOPICS);
        spyConsentManager.enable(mContextSpy, AdServicesApiType.FLEDGE);
        spyConsentManager.enable(mContextSpy, AdServicesApiType.MEASUREMENTS);

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
        spyConsentManager.disable(mContextSpy, AdServicesApiType.TOPICS);
        spyConsentManager.disable(mContextSpy, AdServicesApiType.FLEDGE);
        spyConsentManager.disable(mContextSpy, AdServicesApiType.MEASUREMENTS);

        assertThat(spyConsentManager.getConsent(AdServicesApiType.TOPICS).isGiven()).isFalse();
        assertThat(spyConsentManager.getConsent(AdServicesApiType.FLEDGE).isGiven()).isFalse();
        assertThat(spyConsentManager.getConsent(AdServicesApiType.MEASUREMENTS).isGiven())
                .isFalse();
        assertThat(spyConsentManager.getConsent().isGiven()).isFalse();

        ExtendedMockito.verify(
                () ->
                        BackgroundJobsManager.unscheduleJobsPerApi(
                                any(JobScheduler.class), eq(AdServicesApiType.TOPICS)));
        ExtendedMockito.verify(
                () ->
                        BackgroundJobsManager.unscheduleJobsPerApi(
                                any(JobScheduler.class), eq(AdServicesApiType.FLEDGE)));
        ExtendedMockito.verify(
                () ->
                        BackgroundJobsManager.unscheduleJobsPerApi(
                                any(JobScheduler.class), eq(AdServicesApiType.MEASUREMENTS)));
        ExtendedMockito.verify(
                () -> BackgroundJobsManager.unscheduleAllBackgroundJobs(any(JobScheduler.class)));

        verify(spyConsentManager, times(2)).resetTopicsAndBlockedTopics();
        verify(spyConsentManager, times(2)).resetAppsAndBlockedApps();
        verify(spyConsentManager, times(2)).resetMeasurement();
    }

    // Note this method needs to be invoked after other private variables are initialized.
    private ConsentManager getConsentManagerByConsentSourceOfTruth(int consentSourceOfTruth) {
        return new ConsentManager(
                mContextSpy,
                mTopicsWorker,
                mAppConsentDao,
                mEnrollmentDao,
                mMeasurementImpl,
                mCustomAudienceDaoMock,
                mAdServicesManager,
                mConsentDatastore,
                mMockFlags,
                consentSourceOfTruth);
    }

    private ConsentManager getSpiedConsentManagerForMigrationTesting(
            boolean isGiven, int consentSourceOfTruth) throws RemoteException {
        ConsentManager consentManager =
                spy(getConsentManagerByConsentSourceOfTruth(consentSourceOfTruth));

        // Disable IPC calls
        ExtendedMockito.doNothing()
                .when(() -> ConsentManager.setConsentToSystemServer(any(), anyBoolean()));
        ConsentParcel consentParcel =
                isGiven
                        ? ConsentParcel.createGivenConsent(ConsentParcel.ALL_API)
                        : ConsentParcel.createRevokedConsent(ConsentParcel.ALL_API);
        doReturn(consentParcel).when(mMockIAdServicesManager).getConsent(ConsentParcel.ALL_API);
        doReturn(isGiven).when(mMockIAdServicesManager).wasNotificationDisplayed();
        doNothing().when(mMockIAdServicesManager).recordNotificationDisplayed();
        doReturn(isGiven).when(mMockIAdServicesManager).wasGaUxNotificationDisplayed();
        doNothing().when(mMockIAdServicesManager).recordGaUxNotificationDisplayed();
        doReturn(isGiven).when(mMockIAdServicesManager).wasTopicsConsentPageDisplayed();
        doNothing().when(mMockIAdServicesManager).recordTopicsConsentPageDisplayed();
        doReturn(isGiven).when(mMockIAdServicesManager).wasFledgeAndMsmtConsentPageDisplayed();
        doNothing().when(mMockIAdServicesManager).recordFledgeAndMsmtConsentPageDisplayed();

        return consentManager;
    }

    private ConsentManager getSpiedConsentManagerForConsentPerApiTesting(
            boolean isGiven,
            int consentSourceOfTruth,
            @ConsentParcel.ConsentApiType int consentApiType)
            throws RemoteException {
        ConsentManager consentManager =
                spy(getConsentManagerByConsentSourceOfTruth(consentSourceOfTruth));

        // Disable IPC calls
        ExtendedMockito.doNothing()
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
        doNothing().when(mMockIAdServicesManager).recordNotificationDisplayed();
        doReturn(isGiven).when(mMockIAdServicesManager).wasGaUxNotificationDisplayed();
        doNothing().when(mMockIAdServicesManager).recordGaUxNotificationDisplayed();
        doReturn(isGiven).when(mMockIAdServicesManager).wasTopicsConsentPageDisplayed();
        doNothing().when(mMockIAdServicesManager).recordTopicsConsentPageDisplayed();
        doReturn(isGiven).when(mMockIAdServicesManager).wasFledgeAndMsmtConsentPageDisplayed();
        doNothing().when(mMockIAdServicesManager).recordFledgeAndMsmtConsentPageDisplayed();

        return consentManager;
    }

    private void verifyConsentMigration(
            ConsentManager consentManager,
            boolean isGiven,
            boolean hasWrittenToPpApi,
            boolean hasWrittenToSystemServer,
            boolean hasReadFromSystemServer)
            throws RemoteException, IOException {
        verify(consentManager, verificationMode(hasWrittenToPpApi)).setConsentToPpApi(isGiven);
        ExtendedMockito.verify(
                () -> ConsentManager.setConsentToSystemServer(any(), eq(isGiven)),
                verificationMode(hasWrittenToSystemServer));

        verify(mMockIAdServicesManager, verificationMode(hasReadFromSystemServer))
                .getConsent(ConsentParcel.ALL_API);
    }

    private void verifyDataCleanup(ConsentManager consentManager) throws IOException {
        verify(consentManager).resetTopicsAndBlockedTopics();
        verify(consentManager).resetAppsAndBlockedApps();
        verify(consentManager).resetMeasurement();
    }

    private VerificationMode verificationMode(boolean hasHappened) {
        return hasHappened ? atLeastOnce() : never();
    }

    private void mockGetPackageUid(@NonNull String packageName, int uid)
            throws PackageManager.NameNotFoundException {
        doReturn(uid).when(mPackageManagerMock).getPackageUid(eq(packageName), any());
    }

    private void mockInstalledApplications(List<ApplicationInfo> applicationsInstalled) {
        doReturn(applicationsInstalled)
                .when(mPackageManagerMock)
                .getInstalledApplications(any(PackageManager.ApplicationInfoFlags.class));
    }

    private void mockThrowExceptionOnGetPackageUid(@NonNull String packageName)
            throws PackageManager.NameNotFoundException {
        doThrow(PackageManager.NameNotFoundException.class)
                .when(mPackageManagerMock)
                .getPackageUid(eq(packageName), any());
    }

    private List<ApplicationInfo> createApplicationInfos(String... packageNames) {
        return Arrays.stream(packageNames)
                .map(s -> ApplicationInfoBuilder.newBuilder().setPackageName(s).build())
                .collect(Collectors.toList());
    }

    private class ListMatcherIgnoreOrder implements ArgumentMatcher<List<String>> {
        @NonNull private final List<String> mStrings;

        private ListMatcherIgnoreOrder(@NonNull List<String> strings) {
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
}

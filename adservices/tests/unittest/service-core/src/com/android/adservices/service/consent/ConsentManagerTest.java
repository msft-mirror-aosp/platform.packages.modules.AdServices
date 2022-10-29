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

import static com.android.adservices.service.stats.AdServicesStatsLog.AD_SERVICES_SETTINGS_USAGE_REPORTED;
import static com.android.adservices.service.stats.AdServicesStatsLog.AD_SERVICES_SETTINGS_USAGE_REPORTED__ACTION__OPT_IN_SELECTED;
import static com.android.adservices.service.stats.AdServicesStatsLog.AD_SERVICES_SETTINGS_USAGE_REPORTED__REGION__EU;
import static com.android.adservices.service.stats.AdServicesStatsLog.AD_SERVICES_SETTINGS_USAGE_REPORTED__REGION__ROW;

import static com.google.common.truth.Truth.assertThat;

import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertThrows;
import static org.junit.Assert.assertTrue;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyBoolean;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.doNothing;
import static org.mockito.Mockito.doReturn;
import static org.mockito.Mockito.doThrow;
import static org.mockito.Mockito.spy;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoMoreInteractions;

import android.app.job.JobScheduler;
import android.content.Context;
import android.content.pm.ApplicationInfo;
import android.content.pm.PackageManager;
import android.os.SystemClock;

import androidx.test.core.app.ApplicationProvider;
import androidx.test.core.content.pm.ApplicationInfoBuilder;
import androidx.test.filters.SmallTest;

import com.android.adservices.data.DbHelper;
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
import com.android.adservices.service.stats.UIStats;
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
import org.mockito.Mock;
import org.mockito.MockitoAnnotations;
import org.mockito.MockitoSession;
import org.mockito.Spy;
import org.mockito.quality.Strictness;

import java.io.IOException;
import java.util.Arrays;
import java.util.List;
import java.util.stream.Collectors;

@SmallTest
public class ConsentManagerTest {
    @Spy private final Context mContextSpy = ApplicationProvider.getApplicationContext();

    private BooleanFileDatastore mDatastore;
    private ConsentManager mConsentManager;
    private AppConsentDao mAppConsentDao;
    private EnrollmentDao mEnrollmentDao;
    private DbHelper mDbHelper;

    @Mock private PackageManager mPackageManagerMock;
    @Mock private TopicsWorker mTopicsWorker;
    @Mock private MeasurementImpl mMeasurementImpl;
    @Mock private AdServicesLoggerImpl mAdServicesLoggerImpl;
    @Mock private CustomAudienceDao mCustomAudienceDaoMock;

    @Mock private AppUpdateManager mAppUpdateManager;
    @Mock private CacheManager mCacheManager;
    @Mock private BlockedTopicsManager mBlockedTopicsManager;
    @Mock private EpochManager mMockEpochManager;
    @Mock private Flags mMockFlags;
    @Mock private JobScheduler mJobSchedulerMock;
    private MockitoSession mStaticMockSession = null;

    @Before
    public void setup() throws IOException {
        MockitoAnnotations.initMocks(this);

        mStaticMockSession =
                ExtendedMockito.mockitoSession()
                        .spyStatic(AggregateReportingJobService.class)
                        .spyStatic(AggregateFallbackReportingJobService.class)
                        .spyStatic(AttributionJobService.class)
                        .spyStatic(BackgroundJobsManager.class)
                        .spyStatic(EpochJobService.class)
                        .spyStatic(EventReportingJobService.class)
                        .spyStatic(EventFallbackReportingJobService.class)
                        .spyStatic(DeleteExpiredJobService.class)
                        .spyStatic(DeleteUninstalledJobService.class)
                        .spyStatic(FlagsFactory.class)
                        .spyStatic(MaintenanceJobService.class)
                        .spyStatic(MddJobService.class)
                        .spyStatic(DeviceRegionProvider.class)
                        .spyStatic(AsyncRegistrationQueueJobService.class)
                        .strictness(Strictness.WARN)
                        .initMocks(this)
                        .startMocking();

        mDatastore =
                new BooleanFileDatastore(mContextSpy, AppConsentDaoFixture.TEST_DATASTORE_NAME, 1);
        mAppConsentDao = spy(new AppConsentDao(mDatastore, mPackageManagerMock));
        mDbHelper = DbTestUtil.getDbHelperForTest();
        mEnrollmentDao = spy(new EnrollmentDao(mContextSpy, mDbHelper));

        mConsentManager =
                new ConsentManager(
                        mContextSpy,
                        mTopicsWorker,
                        mAppConsentDao,
                        mEnrollmentDao,
                        mMeasurementImpl,
                        mAdServicesLoggerImpl,
                        mCustomAudienceDaoMock,
                        mMockFlags);

        ExtendedMockito.doReturn(mMockFlags).when(FlagsFactory::getFlags);
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
    }

    @After
    public void teardown() throws IOException {
        mDatastore.clear();
        if (mStaticMockSession != null) {
            mStaticMockSession.finishMocking();
        }
    }

    @Test
    public void testConsentIsGivenAfterEnabling() {
        mConsentManager.enable(mContextSpy);

        assertTrue(mConsentManager.getConsent().isGiven());
    }

    @Test
    public void testConsentIsRevokedAfterDisabling() {
        doReturn(mPackageManagerMock).when(mContextSpy).getPackageManager();
        mConsentManager.disable(mContextSpy);

        assertFalse(mConsentManager.getConsent().isGiven());
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
        ExtendedMockito.verify(() -> MddJobService.scheduleIfNeeded(any(Context.class), eq(false)));
        ExtendedMockito.verify(
                () -> MaintenanceJobService.scheduleIfNeeded(any(Context.class), eq(false)));
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

        SystemClock.sleep(1000);
        verify(mTopicsWorker, times(1)).clearAllTopicsData(any());
        // TODO(b/240988406): change to test for correct method call
        verify(mAppConsentDao, times(1)).clearAllConsentData();
        verify(mEnrollmentDao, times(1)).deleteAll();
        verify(mMeasurementImpl, times(1)).deleteAllMeasurementData(any());
        verify(mCustomAudienceDaoMock).deleteAllCustomAudienceData();
    }

    @Test
    public void testIsFledgeConsentRevokedForAppWithFullApiConsent()
            throws IOException, PackageManager.NameNotFoundException {
        mConsentManager.enable(mContextSpy);
        assertTrue(mConsentManager.getConsent().isGiven());

        doReturn(AppConsentDaoFixture.APP10_UID)
                .when(mPackageManagerMock)
                .getPackageUid(eq(AppConsentDaoFixture.APP10_PACKAGE_NAME), any());
        doReturn(AppConsentDaoFixture.APP20_UID)
                .when(mPackageManagerMock)
                .getPackageUid(eq(AppConsentDaoFixture.APP20_PACKAGE_NAME), any());
        doReturn(AppConsentDaoFixture.APP30_UID)
                .when(mPackageManagerMock)
                .getPackageUid(eq(AppConsentDaoFixture.APP30_PACKAGE_NAME), any());

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
    public void testIsFledgeConsentRevokedForAppWithoutPrivacySandboxConsent()
            throws PackageManager.NameNotFoundException {
        doReturn(mPackageManagerMock).when(mContextSpy).getPackageManager();
        mConsentManager.disable(mContextSpy);
        assertFalse(mConsentManager.getConsent().isGiven());

        doReturn(AppConsentDaoFixture.APP10_UID)
                .when(mPackageManagerMock)
                .getPackageUid(eq(AppConsentDaoFixture.APP10_PACKAGE_NAME), any());
        doReturn(AppConsentDaoFixture.APP20_UID)
                .when(mPackageManagerMock)
                .getPackageUid(eq(AppConsentDaoFixture.APP20_PACKAGE_NAME), any());

        assertTrue(
                mConsentManager.isFledgeConsentRevokedForApp(
                        AppConsentDaoFixture.APP10_PACKAGE_NAME));
        assertTrue(
                mConsentManager.isFledgeConsentRevokedForApp(
                        AppConsentDaoFixture.APP20_PACKAGE_NAME));
    }

    @Test
    public void testIsFledgeConsentRevokedForNotFoundAppThrows()
            throws PackageManager.NameNotFoundException {
        mConsentManager.enable(mContextSpy);
        assertTrue(mConsentManager.getConsent().isGiven());

        doThrow(PackageManager.NameNotFoundException.class)
                .when(mPackageManagerMock)
                .getPackageUid(eq(AppConsentDaoFixture.APP_NOT_FOUND_PACKAGE_NAME), any());

        assertThrows(
                IllegalArgumentException.class,
                () ->
                        mConsentManager.isFledgeConsentRevokedForApp(
                                AppConsentDaoFixture.APP_NOT_FOUND_PACKAGE_NAME));
    }

    @Test
    public void testIsFledgeConsentRevokedForAppAfterSettingFledgeUseWithFullApiConsent()
            throws IOException, PackageManager.NameNotFoundException {
        mConsentManager.enable(mContextSpy);
        assertTrue(mConsentManager.getConsent().isGiven());

        doReturn(AppConsentDaoFixture.APP10_UID)
                .when(mPackageManagerMock)
                .getPackageUid(eq(AppConsentDaoFixture.APP10_PACKAGE_NAME), any());
        doReturn(AppConsentDaoFixture.APP20_UID)
                .when(mPackageManagerMock)
                .getPackageUid(eq(AppConsentDaoFixture.APP20_PACKAGE_NAME), any());
        doReturn(AppConsentDaoFixture.APP30_UID)
                .when(mPackageManagerMock)
                .getPackageUid(eq(AppConsentDaoFixture.APP30_PACKAGE_NAME), any());

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
    public void testIsFledgeConsentRevokedForAppAfterSettingFledgeUseWithoutPrivacySandboxConsent()
            throws PackageManager.NameNotFoundException {
        doReturn(mPackageManagerMock).when(mContextSpy).getPackageManager();
        mConsentManager.disable(mContextSpy);
        assertFalse(mConsentManager.getConsent().isGiven());

        doReturn(AppConsentDaoFixture.APP10_UID)
                .when(mPackageManagerMock)
                .getPackageUid(eq(AppConsentDaoFixture.APP10_PACKAGE_NAME), any());
        doReturn(AppConsentDaoFixture.APP20_UID)
                .when(mPackageManagerMock)
                .getPackageUid(eq(AppConsentDaoFixture.APP20_PACKAGE_NAME), any());

        assertTrue(
                mConsentManager.isFledgeConsentRevokedForAppAfterSettingFledgeUse(
                        AppConsentDaoFixture.APP10_PACKAGE_NAME));
        assertTrue(
                mConsentManager.isFledgeConsentRevokedForAppAfterSettingFledgeUse(
                        AppConsentDaoFixture.APP20_PACKAGE_NAME));
    }

    @Test
    public void testIsFledgeConsentRevokedForAppAfterSettingFledgeUseThrows()
            throws PackageManager.NameNotFoundException {
        mConsentManager.enable(mContextSpy);
        assertTrue(mConsentManager.getConsent().isGiven());

        doThrow(PackageManager.NameNotFoundException.class)
                .when(mPackageManagerMock)
                .getPackageUid(eq(AppConsentDaoFixture.APP_NOT_FOUND_PACKAGE_NAME), any());

        assertThrows(
                IllegalArgumentException.class,
                () ->
                        mConsentManager.isFledgeConsentRevokedForAppAfterSettingFledgeUse(
                                AppConsentDaoFixture.APP_NOT_FOUND_PACKAGE_NAME));
    }

    @Test
    public void testGetKnownAppsWithConsent()
            throws IOException, PackageManager.NameNotFoundException {
        mConsentManager.enable(mContextSpy);
        assertTrue(mConsentManager.getConsent().isGiven());
        doReturn(AppConsentDaoFixture.APP10_UID)
                .when(mPackageManagerMock)
                .getPackageUid(eq(AppConsentDaoFixture.APP10_PACKAGE_NAME), any());
        doReturn(AppConsentDaoFixture.APP20_UID)
                .when(mPackageManagerMock)
                .getPackageUid(eq(AppConsentDaoFixture.APP20_PACKAGE_NAME), any());
        doReturn(AppConsentDaoFixture.APP30_UID)
                .when(mPackageManagerMock)
                .getPackageUid(eq(AppConsentDaoFixture.APP30_PACKAGE_NAME), any());
        mDatastore.put(AppConsentDaoFixture.APP10_DATASTORE_KEY, false);
        mDatastore.put(AppConsentDaoFixture.APP20_DATASTORE_KEY, false);
        mDatastore.put(AppConsentDaoFixture.APP30_DATASTORE_KEY, false);
        List<ApplicationInfo> applicationsInstalled =
                Arrays.asList(
                        ApplicationInfoBuilder.newBuilder()
                                .setPackageName(AppConsentDaoFixture.APP10_PACKAGE_NAME)
                                .build(),
                        ApplicationInfoBuilder.newBuilder()
                                .setPackageName(AppConsentDaoFixture.APP20_PACKAGE_NAME)
                                .build(),
                        ApplicationInfoBuilder.newBuilder()
                                .setPackageName(AppConsentDaoFixture.APP30_PACKAGE_NAME)
                                .build());
        doReturn(applicationsInstalled)
                .when(mPackageManagerMock)
                .getInstalledApplications(any(PackageManager.ApplicationInfoFlags.class));

        ImmutableList<App> knownAppsWithConsent = mConsentManager.getKnownAppsWithConsent();
        ImmutableList<App> appsWithRevokedConsent = mConsentManager.getAppsWithRevokedConsent();

        // all apps have received a consent
        assertThat(knownAppsWithConsent).hasSize(3);
        assertThat(appsWithRevokedConsent).isEmpty();
    }

    @Test
    public void testGetKnownAppsWithConsentAfterConsentForOneOfThemWasRevoked()
            throws IOException, PackageManager.NameNotFoundException, InterruptedException {
        doNothing().when(mCustomAudienceDaoMock).deleteCustomAudienceDataByOwner(any());

        mConsentManager.enable(mContextSpy);
        assertTrue(mConsentManager.getConsent().isGiven());
        doReturn(AppConsentDaoFixture.APP10_UID)
                .when(mPackageManagerMock)
                .getPackageUid(eq(AppConsentDaoFixture.APP10_PACKAGE_NAME), any());
        doReturn(AppConsentDaoFixture.APP20_UID)
                .when(mPackageManagerMock)
                .getPackageUid(eq(AppConsentDaoFixture.APP20_PACKAGE_NAME), any());
        doReturn(AppConsentDaoFixture.APP30_UID)
                .when(mPackageManagerMock)
                .getPackageUid(eq(AppConsentDaoFixture.APP30_PACKAGE_NAME), any());
        mDatastore.put(AppConsentDaoFixture.APP10_DATASTORE_KEY, false);
        mDatastore.put(AppConsentDaoFixture.APP20_DATASTORE_KEY, false);
        mDatastore.put(AppConsentDaoFixture.APP30_DATASTORE_KEY, false);
        List<ApplicationInfo> applicationsInstalled =
                Arrays.asList(
                        ApplicationInfoBuilder.newBuilder()
                                .setPackageName(AppConsentDaoFixture.APP10_PACKAGE_NAME)
                                .build(),
                        ApplicationInfoBuilder.newBuilder()
                                .setPackageName(AppConsentDaoFixture.APP20_PACKAGE_NAME)
                                .build(),
                        ApplicationInfoBuilder.newBuilder()
                                .setPackageName(AppConsentDaoFixture.APP30_PACKAGE_NAME)
                                .build());
        doReturn(applicationsInstalled)
                .when(mPackageManagerMock)
                .getInstalledApplications(any(PackageManager.ApplicationInfoFlags.class));
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
    public void testGetKnownAppsWithConsentAfterConsentForOneOfThemWasRevokedAndRestored()
            throws IOException, PackageManager.NameNotFoundException, InterruptedException {
        doNothing().when(mCustomAudienceDaoMock).deleteCustomAudienceDataByOwner(any());

        mConsentManager.enable(mContextSpy);
        assertTrue(mConsentManager.getConsent().isGiven());
        doReturn(AppConsentDaoFixture.APP10_UID)
                .when(mPackageManagerMock)
                .getPackageUid(eq(AppConsentDaoFixture.APP10_PACKAGE_NAME), any());
        doReturn(AppConsentDaoFixture.APP20_UID)
                .when(mPackageManagerMock)
                .getPackageUid(eq(AppConsentDaoFixture.APP20_PACKAGE_NAME), any());
        doReturn(AppConsentDaoFixture.APP30_UID)
                .when(mPackageManagerMock)
                .getPackageUid(eq(AppConsentDaoFixture.APP30_PACKAGE_NAME), any());
        mDatastore.put(AppConsentDaoFixture.APP10_DATASTORE_KEY, false);
        mDatastore.put(AppConsentDaoFixture.APP20_DATASTORE_KEY, false);
        mDatastore.put(AppConsentDaoFixture.APP30_DATASTORE_KEY, false);
        App app = App.create(AppConsentDaoFixture.APP10_PACKAGE_NAME);
        List<ApplicationInfo> applicationsInstalled =
                Arrays.asList(
                        ApplicationInfoBuilder.newBuilder()
                                .setPackageName(AppConsentDaoFixture.APP10_PACKAGE_NAME)
                                .build(),
                        ApplicationInfoBuilder.newBuilder()
                                .setPackageName(AppConsentDaoFixture.APP20_PACKAGE_NAME)
                                .build(),
                        ApplicationInfoBuilder.newBuilder()
                                .setPackageName(AppConsentDaoFixture.APP30_PACKAGE_NAME)
                                .build());
        doReturn(applicationsInstalled)
                .when(mPackageManagerMock)
                .getInstalledApplications(any(PackageManager.ApplicationInfoFlags.class));

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
    public void testResetAllAppConsentAndAppData()
            throws IOException, PackageManager.NameNotFoundException {
        doNothing().when(mCustomAudienceDaoMock).deleteAllCustomAudienceData();

        // Prepopulate with consent data for some apps
        mConsentManager.enable(mContextSpy);
        assertTrue(mConsentManager.getConsent().isGiven());
        doReturn(AppConsentDaoFixture.APP10_UID)
                .when(mPackageManagerMock)
                .getPackageUid(eq(AppConsentDaoFixture.APP10_PACKAGE_NAME), any());
        doReturn(AppConsentDaoFixture.APP20_UID)
                .when(mPackageManagerMock)
                .getPackageUid(eq(AppConsentDaoFixture.APP20_PACKAGE_NAME), any());
        doReturn(AppConsentDaoFixture.APP30_UID)
                .when(mPackageManagerMock)
                .getPackageUid(eq(AppConsentDaoFixture.APP30_PACKAGE_NAME), any());
        mDatastore.put(AppConsentDaoFixture.APP10_DATASTORE_KEY, false);
        mDatastore.put(AppConsentDaoFixture.APP20_DATASTORE_KEY, true);
        mDatastore.put(AppConsentDaoFixture.APP30_DATASTORE_KEY, false);
        List<ApplicationInfo> applicationsInstalled =
                Arrays.asList(
                        ApplicationInfoBuilder.newBuilder()
                                .setPackageName(AppConsentDaoFixture.APP10_PACKAGE_NAME)
                                .build(),
                        ApplicationInfoBuilder.newBuilder()
                                .setPackageName(AppConsentDaoFixture.APP20_PACKAGE_NAME)
                                .build(),
                        ApplicationInfoBuilder.newBuilder()
                                .setPackageName(AppConsentDaoFixture.APP30_PACKAGE_NAME)
                                .build());
        doReturn(applicationsInstalled)
                .when(mPackageManagerMock)
                .getInstalledApplications(any(PackageManager.ApplicationInfoFlags.class));

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
        verify(mCustomAudienceDaoMock).deleteAllCustomAudienceData();
    }

    @Test
    public void testResetAllowedAppConsentAndAppData()
            throws IOException, PackageManager.NameNotFoundException {
        doNothing().when(mCustomAudienceDaoMock).deleteAllCustomAudienceData();

        // Prepopulate with consent data for some apps
        mConsentManager.enable(mContextSpy);
        assertTrue(mConsentManager.getConsent().isGiven());
        doReturn(AppConsentDaoFixture.APP10_UID)
                .when(mPackageManagerMock)
                .getPackageUid(eq(AppConsentDaoFixture.APP10_PACKAGE_NAME), any());
        doReturn(AppConsentDaoFixture.APP20_UID)
                .when(mPackageManagerMock)
                .getPackageUid(eq(AppConsentDaoFixture.APP20_PACKAGE_NAME), any());
        doReturn(AppConsentDaoFixture.APP30_UID)
                .when(mPackageManagerMock)
                .getPackageUid(eq(AppConsentDaoFixture.APP30_PACKAGE_NAME), any());
        mDatastore.put(AppConsentDaoFixture.APP10_DATASTORE_KEY, false);
        mDatastore.put(AppConsentDaoFixture.APP20_DATASTORE_KEY, true);
        mDatastore.put(AppConsentDaoFixture.APP30_DATASTORE_KEY, false);
        List<ApplicationInfo> applicationsInstalled =
                Arrays.asList(
                        ApplicationInfoBuilder.newBuilder()
                                .setPackageName(AppConsentDaoFixture.APP10_PACKAGE_NAME)
                                .build(),
                        ApplicationInfoBuilder.newBuilder()
                                .setPackageName(AppConsentDaoFixture.APP20_PACKAGE_NAME)
                                .build(),
                        ApplicationInfoBuilder.newBuilder()
                                .setPackageName(AppConsentDaoFixture.APP30_PACKAGE_NAME)
                                .build());
        doReturn(applicationsInstalled)
                .when(mPackageManagerMock)
                .getInstalledApplications(any(PackageManager.ApplicationInfoFlags.class));

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
        verify(mCustomAudienceDaoMock).deleteAllCustomAudienceData();
    }

    @Test
    public void testNotificationDisplayedRecorded() {
        Boolean wasNotificationDisplayed = mConsentManager.wasNotificationDisplayed();

        assertThat(wasNotificationDisplayed).isFalse();

        mConsentManager.recordNotificationDisplayed();
        wasNotificationDisplayed = mConsentManager.wasNotificationDisplayed();

        assertThat(wasNotificationDisplayed).isTrue();
    }

    @Test
    public void testTopicsProxyCalls() {
        Topic topic = Topic.create(1, 1, 1);
        List<String> tablesToBlock = List.of(TopicsTables.BlockedTopicsContract.TABLE);
        ConsentManager consentManager =
                new ConsentManager(
                        mContextSpy,
                        new TopicsWorker(
                                mMockEpochManager,
                                mCacheManager,
                                mBlockedTopicsManager,
                                mAppUpdateManager,
                                mMockFlags),
                        mAppConsentDao,
                        mEnrollmentDao,
                        mMeasurementImpl,
                        mAdServicesLoggerImpl,
                        mCustomAudienceDaoMock,
                        mMockFlags);
        doNothing().when(mBlockedTopicsManager).blockTopic(any());
        doNothing().when(mBlockedTopicsManager).unblockTopic(any());
        doNothing().when(mCacheManager).clearAllTopicsData(any());

        consentManager.revokeConsentForTopic(topic);
        consentManager.restoreConsentForTopic(topic);
        consentManager.resetTopics();

        verify(mBlockedTopicsManager).blockTopic(topic);
        verify(mBlockedTopicsManager).unblockTopic(topic);
        verify(mCacheManager).clearAllTopicsData(tablesToBlock);
    }

    @Test
    public void testLoggingSettingsUsageReportedOptInSelectedRow() {
        ExtendedMockito.doReturn(false)
                .when(() -> DeviceRegionProvider.isEuDevice(any(Context.class)));
        ConsentManager temporalConsentManager =
                new ConsentManager(
                        mContextSpy,
                        mTopicsWorker,
                        mAppConsentDao,
                        mEnrollmentDao,
                        mMeasurementImpl,
                        mAdServicesLoggerImpl,
                        mCustomAudienceDaoMock,
                        mMockFlags);

        temporalConsentManager.enable(mContextSpy);

        UIStats expectedUIStats =
                new UIStats.Builder()
                        .setCode(AD_SERVICES_SETTINGS_USAGE_REPORTED)
                        .setRegion(AD_SERVICES_SETTINGS_USAGE_REPORTED__REGION__ROW)
                        .setAction(AD_SERVICES_SETTINGS_USAGE_REPORTED__ACTION__OPT_IN_SELECTED)
                        .build();

        verify(mAdServicesLoggerImpl, times(1)).logUIStats(any());
        verify(mAdServicesLoggerImpl, times(1)).logUIStats(expectedUIStats);
    }

    @Test
    public void testLoggingSettingsUsageReportedOptInSelectedEu() {
        ExtendedMockito.doReturn(true)
                .when(() -> DeviceRegionProvider.isEuDevice(any(Context.class)));
        ConsentManager temporalConsentManager =
                new ConsentManager(
                        mContextSpy,
                        mTopicsWorker,
                        mAppConsentDao,
                        mEnrollmentDao,
                        mMeasurementImpl,
                        mAdServicesLoggerImpl,
                        mCustomAudienceDaoMock,
                        mMockFlags);

        temporalConsentManager.enable(mContextSpy);

        UIStats expectedUIStats =
                new UIStats.Builder()
                        .setCode(AD_SERVICES_SETTINGS_USAGE_REPORTED)
                        .setRegion(AD_SERVICES_SETTINGS_USAGE_REPORTED__REGION__EU)
                        .setAction(AD_SERVICES_SETTINGS_USAGE_REPORTED__ACTION__OPT_IN_SELECTED)
                        .build();

        verify(mAdServicesLoggerImpl, times(1)).logUIStats(any());
        verify(mAdServicesLoggerImpl, times(1)).logUIStats(expectedUIStats);
    }
}

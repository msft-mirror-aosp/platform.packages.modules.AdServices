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

import static com.android.adservices.service.consent.ConsentManager.EEA_DEVICE;
import static com.android.adservices.service.stats.AdServicesStatsLog.AD_SERVICES_SETTINGS_USAGE_REPORTED;
import static com.android.adservices.service.stats.AdServicesStatsLog.AD_SERVICES_SETTINGS_USAGE_REPORTED__ACTION__OPT_IN_SELECTED;
import static com.android.adservices.service.stats.AdServicesStatsLog.AD_SERVICES_SETTINGS_USAGE_REPORTED__REGION__EU;

import static com.google.common.truth.Truth.assertThat;

import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertThrows;
import static org.junit.Assert.assertTrue;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.doNothing;
import static org.mockito.Mockito.doReturn;
import static org.mockito.Mockito.doThrow;
import static org.mockito.Mockito.spy;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoMoreInteractions;
import static org.mockito.Mockito.when;

import android.app.job.JobScheduler;
import android.content.Context;
import android.content.pm.PackageManager;

import androidx.test.core.app.ApplicationProvider;
import androidx.test.filters.SmallTest;

import com.android.adservices.data.common.BooleanFileDatastore;
import com.android.adservices.data.consent.AppConsentDao;
import com.android.adservices.data.consent.AppConsentDaoFixture;
import com.android.adservices.data.customaudience.CustomAudienceDao;
import com.android.adservices.data.topics.Topic;
import com.android.adservices.data.topics.TopicsTables;
import com.android.adservices.service.AdServicesConfig;
import com.android.adservices.service.Flags;
import com.android.adservices.service.measurement.MeasurementImpl;
import com.android.adservices.service.stats.AdServicesLoggerImpl;
import com.android.adservices.service.stats.UIStats;
import com.android.adservices.service.topics.AppUpdateManager;
import com.android.adservices.service.topics.BlockedTopicsManager;
import com.android.adservices.service.topics.CacheManager;
import com.android.adservices.service.topics.EpochManager;
import com.android.adservices.service.topics.TopicsWorker;

import com.google.common.collect.ImmutableList;

import org.junit.After;
import org.junit.Before;
import org.junit.Test;
import org.mockito.Mock;
import org.mockito.MockitoAnnotations;
import org.mockito.Spy;

import java.io.IOException;
import java.util.List;
import java.util.stream.Collectors;

@SmallTest
public class ConsentManagerTest {
    @Spy private final Context mContextSpy = ApplicationProvider.getApplicationContext();

    private BooleanFileDatastore mDatastore;
    private ConsentManager mConsentManager;
    private AppConsentDao mAppConsentDao;

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

    @Before
    public void setup() throws IOException {
        MockitoAnnotations.initMocks(this);

        mDatastore =
                new BooleanFileDatastore(mContextSpy, AppConsentDaoFixture.TEST_DATASTORE_NAME, 1);
        mAppConsentDao = spy(new AppConsentDao(mDatastore, mPackageManagerMock));

        mConsentManager =
                new ConsentManager(
                        mContextSpy,
                        mTopicsWorker,
                        mAppConsentDao,
                        mMeasurementImpl,
                        mAdServicesLoggerImpl,
                        mCustomAudienceDaoMock);
    }

    @After
    public void teardown() throws IOException {
        mDatastore.clear();
    }

    @Test
    public void testConsentIsGivenAfterEnabling() {
        when(mPackageManagerMock.hasSystemFeature(EEA_DEVICE)).thenReturn(true);
        mConsentManager.enable(mPackageManagerMock);

        assertTrue(mConsentManager.getConsent(mPackageManagerMock).isGiven());
    }

    @Test
    public void testConsentIsRevokedAfterDisabling() {
        doReturn(mPackageManagerMock).when(mContextSpy).getPackageManager();
        when(mPackageManagerMock.hasSystemFeature(EEA_DEVICE)).thenReturn(true);
        mConsentManager.disable(mContextSpy);

        assertFalse(mConsentManager.getConsent(mPackageManagerMock).isGiven());
    }

    @Test
    public void testJobsAreUnscheduledAfterDisabling() {
        doReturn(mPackageManagerMock).when(mContextSpy).getPackageManager();
        doReturn(mJobSchedulerMock).when(mContextSpy).getSystemService(JobScheduler.class);
        when(mPackageManagerMock.hasSystemFeature(EEA_DEVICE)).thenReturn(true);
        mConsentManager.disable(mContextSpy);

        verify(mJobSchedulerMock).cancel(AdServicesConfig.MAINTENANCE_JOB_ID);
        verify(mJobSchedulerMock).cancel(AdServicesConfig.TOPICS_EPOCH_JOB_ID);
        verify(mJobSchedulerMock).cancel(AdServicesConfig.MEASUREMENT_EVENT_MAIN_REPORTING_JOB_ID);
        verify(mJobSchedulerMock).cancel(AdServicesConfig.MEASUREMENT_DELETE_EXPIRED_JOB_ID);
        verify(mJobSchedulerMock).cancel(AdServicesConfig.MEASUREMENT_ATTRIBUTION_JOB_ID);
        verify(mJobSchedulerMock)
                .cancel(AdServicesConfig.MEASUREMENT_EVENT_FALLBACK_REPORTING_JOB_ID);
        verify(mJobSchedulerMock)
                .cancel(AdServicesConfig.MEASUREMENT_AGGREGATE_MAIN_REPORTING_JOB_ID);
        verify(mJobSchedulerMock)
                .cancel(AdServicesConfig.MEASUREMENT_AGGREGATE_FALLBACK_REPORTING_JOB_ID);
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
        when(mPackageManagerMock.hasSystemFeature(EEA_DEVICE)).thenReturn(true);
        mConsentManager.disable(mContextSpy);

        verify(mTopicsWorker, times(1)).clearAllTopicsData(any());
        // TODO(b/240988406): change to test for correct method call
        verify(mAppConsentDao, times(1)).clearAllConsentData();
        verify(mMeasurementImpl, times(1)).deleteAllMeasurementData(any());
        verify(mCustomAudienceDaoMock).deleteAllCustomAudienceData();
    }

    @Test
    public void testConsentIsEnabledForEuConfig() {
        when(mPackageManagerMock.hasSystemFeature(EEA_DEVICE)).thenReturn(true);

        assertFalse(mConsentManager.getInitialConsent(mPackageManagerMock));
    }

    @Test
    public void testConsentIsEnabledForNonEuConfig() {
        when(mPackageManagerMock.hasSystemFeature(EEA_DEVICE)).thenReturn(false);

        assertTrue(mConsentManager.getInitialConsent(mPackageManagerMock));
    }

    @Test
    public void testIsFledgeConsentRevokedForAppWithFullApiConsent()
            throws IOException, PackageManager.NameNotFoundException {
        doReturn(false).when(mPackageManagerMock).hasSystemFeature(eq(EEA_DEVICE));
        mConsentManager.enable(mPackageManagerMock);
        assertTrue(mConsentManager.getConsent(mPackageManagerMock).isGiven());

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
                        mPackageManagerMock, AppConsentDaoFixture.APP10_PACKAGE_NAME));
        assertTrue(
                mConsentManager.isFledgeConsentRevokedForApp(
                        mPackageManagerMock, AppConsentDaoFixture.APP20_PACKAGE_NAME));
        assertFalse(
                mConsentManager.isFledgeConsentRevokedForApp(
                        mPackageManagerMock, AppConsentDaoFixture.APP30_PACKAGE_NAME));
    }

    @Test
    public void testIsFledgeConsentRevokedForAppWithoutPrivacySandboxConsent()
            throws PackageManager.NameNotFoundException {
        doReturn(mPackageManagerMock).when(mContextSpy).getPackageManager();
        doReturn(true).when(mPackageManagerMock).hasSystemFeature(eq(EEA_DEVICE));
        mConsentManager.disable(mContextSpy);
        assertFalse(mConsentManager.getConsent(mPackageManagerMock).isGiven());

        doReturn(AppConsentDaoFixture.APP10_UID)
                .when(mPackageManagerMock)
                .getPackageUid(eq(AppConsentDaoFixture.APP10_PACKAGE_NAME), any());
        doReturn(AppConsentDaoFixture.APP20_UID)
                .when(mPackageManagerMock)
                .getPackageUid(eq(AppConsentDaoFixture.APP20_PACKAGE_NAME), any());

        assertTrue(
                mConsentManager.isFledgeConsentRevokedForApp(
                        mPackageManagerMock, AppConsentDaoFixture.APP10_PACKAGE_NAME));
        assertTrue(
                mConsentManager.isFledgeConsentRevokedForApp(
                        mPackageManagerMock, AppConsentDaoFixture.APP20_PACKAGE_NAME));
    }

    @Test
    public void testIsFledgeConsentRevokedForNotFoundAppThrows()
            throws PackageManager.NameNotFoundException {
        doReturn(false).when(mPackageManagerMock).hasSystemFeature(eq(EEA_DEVICE));
        mConsentManager.enable(mPackageManagerMock);
        assertTrue(mConsentManager.getConsent(mPackageManagerMock).isGiven());

        doThrow(PackageManager.NameNotFoundException.class)
                .when(mPackageManagerMock)
                .getPackageUid(eq(AppConsentDaoFixture.APP_NOT_FOUND_PACKAGE_NAME), any());

        assertThrows(
                IllegalArgumentException.class,
                () ->
                        mConsentManager.isFledgeConsentRevokedForApp(
                                mPackageManagerMock,
                                AppConsentDaoFixture.APP_NOT_FOUND_PACKAGE_NAME));
    }

    @Test
    public void testIsFledgeConsentRevokedForAppAfterSettingFledgeUseWithFullApiConsent()
            throws IOException, PackageManager.NameNotFoundException {
        doReturn(false).when(mPackageManagerMock).hasSystemFeature(eq(EEA_DEVICE));
        mConsentManager.enable(mPackageManagerMock);
        assertTrue(mConsentManager.getConsent(mPackageManagerMock).isGiven());

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
                        mPackageManagerMock, AppConsentDaoFixture.APP10_PACKAGE_NAME));
        assertTrue(
                mConsentManager.isFledgeConsentRevokedForAppAfterSettingFledgeUse(
                        mPackageManagerMock, AppConsentDaoFixture.APP20_PACKAGE_NAME));
        assertFalse(
                mConsentManager.isFledgeConsentRevokedForAppAfterSettingFledgeUse(
                        mPackageManagerMock, AppConsentDaoFixture.APP30_PACKAGE_NAME));
    }

    @Test
    public void testIsFledgeConsentRevokedForAppAfterSettingFledgeUseWithoutPrivacySandboxConsent()
            throws PackageManager.NameNotFoundException {
        doReturn(mPackageManagerMock).when(mContextSpy).getPackageManager();
        doReturn(true).when(mPackageManagerMock).hasSystemFeature(eq(EEA_DEVICE));
        mConsentManager.disable(mContextSpy);
        assertFalse(mConsentManager.getConsent(mPackageManagerMock).isGiven());

        doReturn(AppConsentDaoFixture.APP10_UID)
                .when(mPackageManagerMock)
                .getPackageUid(eq(AppConsentDaoFixture.APP10_PACKAGE_NAME), any());
        doReturn(AppConsentDaoFixture.APP20_UID)
                .when(mPackageManagerMock)
                .getPackageUid(eq(AppConsentDaoFixture.APP20_PACKAGE_NAME), any());

        assertTrue(
                mConsentManager.isFledgeConsentRevokedForAppAfterSettingFledgeUse(
                        mPackageManagerMock, AppConsentDaoFixture.APP10_PACKAGE_NAME));
        assertTrue(
                mConsentManager.isFledgeConsentRevokedForAppAfterSettingFledgeUse(
                        mPackageManagerMock, AppConsentDaoFixture.APP20_PACKAGE_NAME));
    }

    @Test
    public void testIsFledgeConsentRevokedForAppAfterSettingFledgeUseThrows()
            throws PackageManager.NameNotFoundException {
        doReturn(false).when(mPackageManagerMock).hasSystemFeature(eq(EEA_DEVICE));
        mConsentManager.enable(mPackageManagerMock);
        assertTrue(mConsentManager.getConsent(mPackageManagerMock).isGiven());

        doThrow(PackageManager.NameNotFoundException.class)
                .when(mPackageManagerMock)
                .getPackageUid(eq(AppConsentDaoFixture.APP_NOT_FOUND_PACKAGE_NAME), any());

        assertThrows(
                IllegalArgumentException.class,
                () ->
                        mConsentManager.isFledgeConsentRevokedForAppAfterSettingFledgeUse(
                                mPackageManagerMock,
                                AppConsentDaoFixture.APP_NOT_FOUND_PACKAGE_NAME));
    }

    @Test
    public void testGetKnownAppsWithConsent()
            throws IOException, PackageManager.NameNotFoundException {
        doReturn(false).when(mPackageManagerMock).hasSystemFeature(eq(EEA_DEVICE));
        mConsentManager.enable(mPackageManagerMock);
        assertTrue(mConsentManager.getConsent(mPackageManagerMock).isGiven());
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

        ImmutableList<App> knownAppsWithConsent = mConsentManager.getKnownAppsWithConsent();
        ImmutableList<App> appsWithRevokedConsent = mConsentManager.getAppsWithRevokedConsent();

        // all apps have received a consent
        assertThat(knownAppsWithConsent).hasSize(3);
        assertThat(appsWithRevokedConsent).isEmpty();
    }

    @Test
    public void testGetKnownAppsWithConsentAfterConsentForOneOfThemWasRevoked()
            throws IOException, PackageManager.NameNotFoundException, InterruptedException {
        doReturn(false).when(mPackageManagerMock).hasSystemFeature(eq(EEA_DEVICE));
        doNothing().when(mCustomAudienceDaoMock).deleteCustomAudienceDataByOwner(any());

        mConsentManager.enable(mPackageManagerMock);
        assertTrue(mConsentManager.getConsent(mPackageManagerMock).isGiven());
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

        // revoke consent for first app
        mConsentManager.revokeConsentForApp(app);
        ImmutableList<App> knownAppsWithConsent = mConsentManager.getKnownAppsWithConsent();
        ImmutableList<App> appsWithRevokedConsent = mConsentManager.getAppsWithRevokedConsent();

        // all apps have received a consent
        assertThat(knownAppsWithConsent).hasSize(2);
        assertThat(appsWithRevokedConsent).hasSize(1);
        App appWithRevokedConsent = appsWithRevokedConsent.get(0);
        assertThat(appWithRevokedConsent.getPackageName()).isEqualTo(app.getPackageName());

        verify(mCustomAudienceDaoMock).deleteCustomAudienceDataByOwner(app.getPackageName());
    }

    @Test
    public void testGetKnownAppsWithConsentAfterConsentForOneOfThemWasRevokedAndRestored()
            throws IOException, PackageManager.NameNotFoundException, InterruptedException {
        doReturn(false).when(mPackageManagerMock).hasSystemFeature(eq(EEA_DEVICE));
        doNothing().when(mCustomAudienceDaoMock).deleteCustomAudienceDataByOwner(any());

        mConsentManager.enable(mPackageManagerMock);
        assertTrue(mConsentManager.getConsent(mPackageManagerMock).isGiven());
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

        // revoke consent for first app
        mConsentManager.revokeConsentForApp(app);
        ImmutableList<App> knownAppsWithConsent = mConsentManager.getKnownAppsWithConsent();
        ImmutableList<App> appsWithRevokedConsent = mConsentManager.getAppsWithRevokedConsent();

        // all apps have received a consent
        assertThat(knownAppsWithConsent).hasSize(2);
        assertThat(appsWithRevokedConsent).hasSize(1);
        App appWithRevokedConsent = appsWithRevokedConsent.get(0);
        assertThat(appWithRevokedConsent.getPackageName()).isEqualTo(app.getPackageName());

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
        doReturn(false).when(mPackageManagerMock).hasSystemFeature(eq(EEA_DEVICE));
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
        doReturn(false).when(mPackageManagerMock).hasSystemFeature(eq(EEA_DEVICE));
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
        doReturn(false).when(mPackageManagerMock).hasSystemFeature(eq(EEA_DEVICE));
        doNothing().when(mCustomAudienceDaoMock).deleteAllCustomAudienceData();

        // Prepopulate with consent data for some apps
        mConsentManager.enable(mPackageManagerMock);
        assertTrue(mConsentManager.getConsent(mPackageManagerMock).isGiven());
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

        verify(mCustomAudienceDaoMock).deleteAllCustomAudienceData();
    }

    @Test
    public void testResetAllowedAppConsentAndAppData()
            throws IOException, PackageManager.NameNotFoundException {
        doReturn(false).when(mPackageManagerMock).hasSystemFeature(eq(EEA_DEVICE));
        doNothing().when(mCustomAudienceDaoMock).deleteAllCustomAudienceData();

        // Prepopulate with consent data for some apps
        mConsentManager.enable(mPackageManagerMock);
        assertTrue(mConsentManager.getConsent(mPackageManagerMock).isGiven());
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

        verify(mCustomAudienceDaoMock).deleteAllCustomAudienceData();
    }

    @Test
    public void testNotificationDisplayedRecorded() {
        doReturn(false).when(mPackageManagerMock).hasSystemFeature(eq(EEA_DEVICE));
        Boolean wasNotificationDisplayed =
                mConsentManager.wasNotificationDisplayed(mPackageManagerMock);

        assertThat(wasNotificationDisplayed).isFalse();

        mConsentManager.recordNotificationDisplayed(mPackageManagerMock);
        wasNotificationDisplayed = mConsentManager.wasNotificationDisplayed(mPackageManagerMock);

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
                        mMeasurementImpl,
                        mAdServicesLoggerImpl,
                        mCustomAudienceDaoMock);
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
    public void testLoggingSettingsUsageReportedOptInSelected() throws IOException {
        when(mPackageManagerMock.hasSystemFeature(EEA_DEVICE)).thenReturn(true);
        mConsentManager.init(mPackageManagerMock);
        mConsentManager.enable(mPackageManagerMock);

        UIStats expectedUIStats = new UIStats.Builder()
                .setCode(AD_SERVICES_SETTINGS_USAGE_REPORTED)
                .setRegion(AD_SERVICES_SETTINGS_USAGE_REPORTED__REGION__EU)
                .setAction(AD_SERVICES_SETTINGS_USAGE_REPORTED__ACTION__OPT_IN_SELECTED)
                .build();

        verify(mAdServicesLoggerImpl, times(1)).logUIStats(any());
        verify(mAdServicesLoggerImpl, times(1)).logUIStats(expectedUIStats);
    }
}

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
import static org.mockito.Mockito.when;

import android.content.Context;
import android.content.pm.PackageManager;

import androidx.test.core.app.ApplicationProvider;
import androidx.test.filters.SmallTest;

import com.android.adservices.data.common.BooleanFileDatastore;
import com.android.adservices.data.consent.AppConsentDao;
import com.android.adservices.data.consent.AppConsentDaoFixture;
import com.android.adservices.data.topics.Topic;
import com.android.adservices.data.topics.TopicsTables;
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

import java.io.IOException;
import java.util.List;

@SmallTest
public class ConsentManagerTest {
    private final Context mContext = ApplicationProvider.getApplicationContext();

    private BooleanFileDatastore mDatastore;
    private ConsentManager mConsentManager;
    private AppConsentDao mAppConsentDao;

    @Mock private PackageManager mPackageManager;
    @Mock private TopicsWorker mTopicsWorker;
    @Mock private MeasurementImpl mMeasurementImpl;
    @Mock private AdServicesLoggerImpl mAdServicesLoggerImpl;

    @Mock private AppUpdateManager mAppUpdateManager;
    @Mock private CacheManager mCacheManager;
    @Mock private BlockedTopicsManager mBlockedTopicsManager;
    @Mock private EpochManager mMockEpochManager;
    @Mock private Flags mMockFlags;

    @Before
    public void setup() throws IOException {
        MockitoAnnotations.initMocks(this);

        mDatastore =
                new BooleanFileDatastore(mContext, AppConsentDaoFixture.TEST_DATASTORE_NAME, 1);
        mAppConsentDao = spy(new AppConsentDao(mDatastore, mPackageManager));

        mConsentManager = new ConsentManager(
                mContext,
                mTopicsWorker,
                mAppConsentDao,
                mMeasurementImpl,
                mAdServicesLoggerImpl);
    }

    @After
    public void teardown() throws IOException {
        mDatastore.clear();
    }

    @Test
    public void testConsentIsGivenAfterEnabling() {
        when(mPackageManager.hasSystemFeature(EEA_DEVICE)).thenReturn(true);
        mConsentManager.enable(mPackageManager);

        assertTrue(mConsentManager.getConsent(mPackageManager).isGiven());
    }

    @Test
    public void testConsentIsRevokedAfterDisabling() {
        when(mPackageManager.hasSystemFeature(EEA_DEVICE)).thenReturn(true);
        mConsentManager.disable(mPackageManager);

        assertFalse(mConsentManager.getConsent(mPackageManager).isGiven());
    }

    @Test
    public void testDataIsResetAfterConsentIsRevoked() throws IOException {
        when(mPackageManager.hasSystemFeature(EEA_DEVICE)).thenReturn(true);
        mConsentManager.disable(mPackageManager);

        verify(mTopicsWorker, times(1)).clearAllTopicsData(any());
        // TODO(b/240988406): change to test for correct method call
        verify(mAppConsentDao, times(1)).clearAllConsentData();
        verify(mMeasurementImpl, times(1)).deleteAllMeasurementData(any());
    }

    @Test
    public void testConsentIsEnabledForEuConfig() {
        when(mPackageManager.hasSystemFeature(EEA_DEVICE)).thenReturn(true);

        assertFalse(mConsentManager.getInitialConsent(mPackageManager));
    }

    @Test
    public void testConsentIsEnabledForNonEuConfig() {
        when(mPackageManager.hasSystemFeature(EEA_DEVICE)).thenReturn(false);

        assertTrue(mConsentManager.getInitialConsent(mPackageManager));
    }

    @Test
    public void testIsFledgeConsentRevokedForAppWithFullApiConsent()
            throws IOException, PackageManager.NameNotFoundException {
        doReturn(false).when(mPackageManager).hasSystemFeature(eq(EEA_DEVICE));
        mConsentManager.enable(mPackageManager);
        assertTrue(mConsentManager.getConsent(mPackageManager).isGiven());

        doReturn(AppConsentDaoFixture.APP10_UID)
                .when(mPackageManager)
                .getPackageUid(eq(AppConsentDaoFixture.APP10_PACKAGE_NAME), any());
        doReturn(AppConsentDaoFixture.APP20_UID)
                .when(mPackageManager)
                .getPackageUid(eq(AppConsentDaoFixture.APP20_PACKAGE_NAME), any());
        doReturn(AppConsentDaoFixture.APP30_UID)
                .when(mPackageManager)
                .getPackageUid(eq(AppConsentDaoFixture.APP30_PACKAGE_NAME), any());

        mDatastore.put(AppConsentDaoFixture.APP10_DATASTORE_KEY, false);
        mDatastore.put(AppConsentDaoFixture.APP20_DATASTORE_KEY, true);

        assertFalse(
                mConsentManager.isFledgeConsentRevokedForApp(
                        mPackageManager, AppConsentDaoFixture.APP10_PACKAGE_NAME));
        assertTrue(
                mConsentManager.isFledgeConsentRevokedForApp(
                        mPackageManager, AppConsentDaoFixture.APP20_PACKAGE_NAME));
        assertFalse(
                mConsentManager.isFledgeConsentRevokedForApp(
                        mPackageManager, AppConsentDaoFixture.APP30_PACKAGE_NAME));
    }

    @Test
    public void testIsFledgeConsentRevokedForAppWithoutPrivacySandboxConsent()
            throws PackageManager.NameNotFoundException, IOException {
        doReturn(true).when(mPackageManager).hasSystemFeature(eq(EEA_DEVICE));
        mConsentManager.disable(mPackageManager);
        assertFalse(mConsentManager.getConsent(mPackageManager).isGiven());

        doReturn(AppConsentDaoFixture.APP10_UID)
                .when(mPackageManager)
                .getPackageUid(eq(AppConsentDaoFixture.APP10_PACKAGE_NAME), any());
        doReturn(AppConsentDaoFixture.APP20_UID)
                .when(mPackageManager)
                .getPackageUid(eq(AppConsentDaoFixture.APP20_PACKAGE_NAME), any());

        assertTrue(
                mConsentManager.isFledgeConsentRevokedForApp(
                        mPackageManager, AppConsentDaoFixture.APP10_PACKAGE_NAME));
        assertTrue(
                mConsentManager.isFledgeConsentRevokedForApp(
                        mPackageManager, AppConsentDaoFixture.APP20_PACKAGE_NAME));
    }

    @Test
    public void testIsFledgeConsentRevokedForNotFoundAppThrows()
            throws PackageManager.NameNotFoundException {
        doReturn(false).when(mPackageManager).hasSystemFeature(eq(EEA_DEVICE));
        mConsentManager.enable(mPackageManager);
        assertTrue(mConsentManager.getConsent(mPackageManager).isGiven());

        doThrow(PackageManager.NameNotFoundException.class)
                .when(mPackageManager)
                .getPackageUid(eq(AppConsentDaoFixture.APP_NOT_FOUND_PACKAGE_NAME), any());

        assertThrows(
                IllegalArgumentException.class,
                () ->
                        mConsentManager.isFledgeConsentRevokedForApp(
                                mPackageManager, AppConsentDaoFixture.APP_NOT_FOUND_PACKAGE_NAME));
    }

    @Test
    public void testIsFledgeConsentRevokedForAppAfterSettingFledgeUseWithFullApiConsent()
            throws IOException, PackageManager.NameNotFoundException {
        doReturn(false).when(mPackageManager).hasSystemFeature(eq(EEA_DEVICE));
        mConsentManager.enable(mPackageManager);
        assertTrue(mConsentManager.getConsent(mPackageManager).isGiven());

        doReturn(AppConsentDaoFixture.APP10_UID)
                .when(mPackageManager)
                .getPackageUid(eq(AppConsentDaoFixture.APP10_PACKAGE_NAME), any());
        doReturn(AppConsentDaoFixture.APP20_UID)
                .when(mPackageManager)
                .getPackageUid(eq(AppConsentDaoFixture.APP20_PACKAGE_NAME), any());
        doReturn(AppConsentDaoFixture.APP30_UID)
                .when(mPackageManager)
                .getPackageUid(eq(AppConsentDaoFixture.APP30_PACKAGE_NAME), any());

        mDatastore.put(AppConsentDaoFixture.APP10_DATASTORE_KEY, false);
        mDatastore.put(AppConsentDaoFixture.APP20_DATASTORE_KEY, true);

        assertFalse(
                mConsentManager.isFledgeConsentRevokedForAppAfterSettingFledgeUse(
                        mPackageManager, AppConsentDaoFixture.APP10_PACKAGE_NAME));
        assertTrue(
                mConsentManager.isFledgeConsentRevokedForAppAfterSettingFledgeUse(
                        mPackageManager, AppConsentDaoFixture.APP20_PACKAGE_NAME));
        assertFalse(
                mConsentManager.isFledgeConsentRevokedForAppAfterSettingFledgeUse(
                        mPackageManager, AppConsentDaoFixture.APP30_PACKAGE_NAME));
    }

    @Test
    public void testIsFledgeConsentRevokedForAppAfterSettingFledgeUseWithoutPrivacySandboxConsent()
            throws PackageManager.NameNotFoundException, IOException {
        doReturn(true).when(mPackageManager).hasSystemFeature(eq(EEA_DEVICE));
        mConsentManager.disable(mPackageManager);
        assertFalse(mConsentManager.getConsent(mPackageManager).isGiven());

        doReturn(AppConsentDaoFixture.APP10_UID)
                .when(mPackageManager)
                .getPackageUid(eq(AppConsentDaoFixture.APP10_PACKAGE_NAME), any());
        doReturn(AppConsentDaoFixture.APP20_UID)
                .when(mPackageManager)
                .getPackageUid(eq(AppConsentDaoFixture.APP20_PACKAGE_NAME), any());

        assertTrue(
                mConsentManager.isFledgeConsentRevokedForAppAfterSettingFledgeUse(
                        mPackageManager, AppConsentDaoFixture.APP10_PACKAGE_NAME));
        assertTrue(
                mConsentManager.isFledgeConsentRevokedForAppAfterSettingFledgeUse(
                        mPackageManager, AppConsentDaoFixture.APP20_PACKAGE_NAME));
    }

    @Test
    public void testIsFledgeConsentRevokedForAppAfterSettingFledgeUseThrows()
            throws PackageManager.NameNotFoundException {
        doReturn(false).when(mPackageManager).hasSystemFeature(eq(EEA_DEVICE));
        mConsentManager.enable(mPackageManager);
        assertTrue(mConsentManager.getConsent(mPackageManager).isGiven());

        doThrow(PackageManager.NameNotFoundException.class)
                .when(mPackageManager)
                .getPackageUid(eq(AppConsentDaoFixture.APP_NOT_FOUND_PACKAGE_NAME), any());

        assertThrows(
                IllegalArgumentException.class,
                () ->
                        mConsentManager.isFledgeConsentRevokedForAppAfterSettingFledgeUse(
                                mPackageManager, AppConsentDaoFixture.APP_NOT_FOUND_PACKAGE_NAME));
    }

    @Test
    public void testGetKnownAppsWithConsent()
            throws IOException, PackageManager.NameNotFoundException {
        doReturn(false).when(mPackageManager).hasSystemFeature(eq(EEA_DEVICE));
        mConsentManager.enable(mPackageManager);
        assertTrue(mConsentManager.getConsent(mPackageManager).isGiven());
        doReturn(AppConsentDaoFixture.APP10_UID)
                .when(mPackageManager)
                .getPackageUid(eq(AppConsentDaoFixture.APP10_PACKAGE_NAME), any());
        doReturn(AppConsentDaoFixture.APP20_UID)
                .when(mPackageManager)
                .getPackageUid(eq(AppConsentDaoFixture.APP20_PACKAGE_NAME), any());
        doReturn(AppConsentDaoFixture.APP30_UID)
                .when(mPackageManager)
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
            throws IOException, PackageManager.NameNotFoundException {
        doReturn(false).when(mPackageManager).hasSystemFeature(eq(EEA_DEVICE));
        mConsentManager.enable(mPackageManager);
        assertTrue(mConsentManager.getConsent(mPackageManager).isGiven());
        doReturn(AppConsentDaoFixture.APP10_UID)
                .when(mPackageManager)
                .getPackageUid(eq(AppConsentDaoFixture.APP10_PACKAGE_NAME), any());
        doReturn(AppConsentDaoFixture.APP20_UID)
                .when(mPackageManager)
                .getPackageUid(eq(AppConsentDaoFixture.APP20_PACKAGE_NAME), any());
        doReturn(AppConsentDaoFixture.APP30_UID)
                .when(mPackageManager)
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
    }

    @Test
    public void testGetKnownAppsWithConsentAfterConsentForOneOfThemWasRevokedAndRestored()
            throws IOException, PackageManager.NameNotFoundException {
        doReturn(false).when(mPackageManager).hasSystemFeature(eq(EEA_DEVICE));
        mConsentManager.enable(mPackageManager);
        assertTrue(mConsentManager.getConsent(mPackageManager).isGiven());
        doReturn(AppConsentDaoFixture.APP10_UID)
                .when(mPackageManager)
                .getPackageUid(eq(AppConsentDaoFixture.APP10_PACKAGE_NAME), any());
        doReturn(AppConsentDaoFixture.APP20_UID)
                .when(mPackageManager)
                .getPackageUid(eq(AppConsentDaoFixture.APP20_PACKAGE_NAME), any());
        doReturn(AppConsentDaoFixture.APP30_UID)
                .when(mPackageManager)
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
        doReturn(false).when(mPackageManager).hasSystemFeature(eq(EEA_DEVICE));
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
        doReturn(false).when(mPackageManager).hasSystemFeature(eq(EEA_DEVICE));
        doReturn(expectedTopicsWithRevokedConsent)
                .when(mTopicsWorker)
                .getTopicsWithRevokedConsent();

        ImmutableList<Topic> topicsWithRevokedConsent =
                mConsentManager.getTopicsWithRevokedConsent();

        assertThat(topicsWithRevokedConsent)
                .containsExactlyElementsIn(expectedTopicsWithRevokedConsent);
    }

    @Test
    public void testNotificationDisplayedRecorded() {
        doReturn(false).when(mPackageManager).hasSystemFeature(eq(EEA_DEVICE));
        Boolean wasNotificationDisplayed =
                mConsentManager.wasNotificationDisplayed(mPackageManager);

        assertThat(wasNotificationDisplayed).isFalse();

        mConsentManager.recordNotificationDisplayed(mPackageManager);
        wasNotificationDisplayed = mConsentManager.wasNotificationDisplayed(mPackageManager);

        assertThat(wasNotificationDisplayed).isTrue();
    }

    @Test
    public void testProxyCalls() {
        Topic topic = Topic.create(1, 1, 1);
        List<String> tablesToBlock = List.of(TopicsTables.BlockedTopicsContract.TABLE);
        ConsentManager consentManager =
                new ConsentManager(
                        mContext,
                        new TopicsWorker(
                                mMockEpochManager,
                                mCacheManager,
                                mBlockedTopicsManager,
                                mAppUpdateManager,
                                mMockFlags),
                        mAppConsentDao,
                        mMeasurementImpl,
                        mAdServicesLoggerImpl);
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
        when(mPackageManager.hasSystemFeature(EEA_DEVICE)).thenReturn(true);
        mConsentManager.init(mPackageManager);
        mConsentManager.enable(mPackageManager);

        UIStats expectedUIStats = new UIStats.Builder()
                .setCode(AD_SERVICES_SETTINGS_USAGE_REPORTED)
                .setRegion(AD_SERVICES_SETTINGS_USAGE_REPORTED__REGION__EU)
                .setAction(AD_SERVICES_SETTINGS_USAGE_REPORTED__ACTION__OPT_IN_SELECTED)
                .build();

        verify(mAdServicesLoggerImpl, times(1)).logUIStats(any());
        verify(mAdServicesLoggerImpl, times(1)).logUIStats(expectedUIStats);
    }
}

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

package com.android.adservices.service.consent;

import static com.android.dx.mockito.inline.extended.ExtendedMockito.doReturn;
import static com.android.dx.mockito.inline.extended.ExtendedMockito.doThrow;
import static com.android.dx.mockito.inline.extended.ExtendedMockito.verify;

import static com.google.common.truth.Truth.assertThat;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertNull;
import static org.junit.Assert.assertThrows;
import static org.junit.Assert.assertTrue;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyInt;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.spy;
import static org.mockito.Mockito.times;

import android.annotation.NonNull;
import android.content.Context;
import android.content.pm.ApplicationInfo;
import android.content.pm.PackageManager;

import androidx.test.core.app.ApplicationProvider;
import androidx.test.core.content.pm.ApplicationInfoBuilder;

import com.android.adservices.data.common.BooleanFileDatastore;
import com.android.adservices.data.consent.AppConsentDao;
import com.android.adservices.data.consent.AppConsentDaoFixture;
import com.android.adservices.service.common.compat.PackageManagerCompatUtils;
import com.android.adservices.service.common.feature.PrivacySandboxFeatureType;
import com.android.adservices.service.ui.data.UxStatesDao;
import com.android.adservices.service.ui.enrollment.collection.PrivacySandboxEnrollmentChannelCollection;
import com.android.adservices.service.ui.ux.collection.PrivacySandboxUxCollection;
import com.android.dx.mockito.inline.extended.ExtendedMockito;
import com.android.modules.utils.build.SdkLevel;

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

public class AppConsentStorageManagerTest {

    @Spy private final Context mContextSpy = ApplicationProvider.getApplicationContext();
    private BooleanFileDatastore mAppDaoDatastore;
    private BooleanFileDatastore mConsentDatastore;
    private AppConsentDao mAppConsentDaoSpy;
    private AppConsentStorageManager mAppConsentStorageManager;
    @Mock private UxStatesDao mUxStatesDaoMock;
    private MockitoSession mStaticMockSession = null;

    @Before
    public void setup() {
        MockitoAnnotations.initMocks(this);

        mStaticMockSession =
                ExtendedMockito.mockitoSession()
                        .mockStatic(PackageManagerCompatUtils.class)
                        .mockStatic(SdkLevel.class)
                        .strictness(Strictness.WARN)
                        .initMocks(this)
                        .startMocking();
        mConsentDatastore =
                new BooleanFileDatastore(
                        mContextSpy,
                        ConsentConstants.STORAGE_XML_IDENTIFIER,
                        ConsentConstants.STORAGE_VERSION);

        mAppDaoDatastore =
                new BooleanFileDatastore(
                        mContextSpy, AppConsentDao.DATASTORE_NAME, AppConsentDao.DATASTORE_VERSION);

        mAppConsentDaoSpy =
                spy(new AppConsentDao(mAppDaoDatastore, mContextSpy.getPackageManager()));
        mAppConsentStorageManager =
                spy(
                        new AppConsentStorageManager(
                                mConsentDatastore, mAppConsentDaoSpy, mUxStatesDaoMock));
    }

    @After
    public void teardown() throws IOException {
        mAppDaoDatastore.clear();
        mConsentDatastore.clear();
        if (mStaticMockSession != null) {
            mStaticMockSession.finishMocking();
        }
    }

    @Test
    public void testAppNotFoundException() {
        mockThrowExceptionOnGetPackageUid(AppConsentDaoFixture.APP_NOT_FOUND_PACKAGE_NAME);
        assertThrows(
                IllegalArgumentException.class,
                () ->
                        mAppConsentStorageManager.isConsentRevokedForApp(
                                AppConsentDaoFixture.APP_NOT_FOUND_PACKAGE_NAME));
    }

    @Test
    public void testClearAllAppConsentData() throws IOException {
        setConsentForOneApp();

        mAppConsentStorageManager.clearAllAppConsentData();
        assertFalse(
                mAppConsentStorageManager
                        .getKnownAppsWithConsent()
                        .contains(AppConsentDaoFixture.APP10_PACKAGE_NAME));
    }

    @Test
    public void testClearConsentForUninstalledApp() throws IOException {
        mockGetPackageUid(AppConsentDaoFixture.APP10_PACKAGE_NAME, AppConsentDaoFixture.APP10_UID);

        mAppConsentStorageManager.setConsentForApp(AppConsentDaoFixture.APP10_PACKAGE_NAME, false);
        assertEquals(Boolean.FALSE, mAppDaoDatastore.get(AppConsentDaoFixture.APP10_DATASTORE_KEY));
        mAppConsentStorageManager.clearConsentForUninstalledApp(
                AppConsentDaoFixture.APP10_PACKAGE_NAME, AppConsentDaoFixture.APP10_UID);
        assertNull(mAppDaoDatastore.get(AppConsentDaoFixture.APP10_DATASTORE_KEY));
    }

    @Test
    public void testClearConsentForUninstalledAppWithoutUid() throws IOException {
        mAppDaoDatastore.put(AppConsentDaoFixture.APP10_DATASTORE_KEY, true);
        mAppDaoDatastore.put(AppConsentDaoFixture.APP20_DATASTORE_KEY, true);
        mAppDaoDatastore.put(AppConsentDaoFixture.APP30_DATASTORE_KEY, false);

        mAppConsentStorageManager.clearConsentForUninstalledApp(
                AppConsentDaoFixture.APP20_PACKAGE_NAME);

        assertEquals(true, mAppDaoDatastore.get(AppConsentDaoFixture.APP10_DATASTORE_KEY));
        assertNull(mAppDaoDatastore.get(AppConsentDaoFixture.APP20_DATASTORE_KEY));
        assertEquals(false, mAppDaoDatastore.get(AppConsentDaoFixture.APP30_DATASTORE_KEY));

        verify(mAppConsentDaoSpy).clearConsentForUninstalledApp(anyString());
    }

    @Test
    public void testClearConsentForUninstalledAppWithoutUid_validatesInput() {
        assertThrows(
                NullPointerException.class,
                () -> {
                    mAppConsentStorageManager.clearConsentForUninstalledApp(null);
                });
        assertThrows(
                IllegalArgumentException.class,
                () -> {
                    mAppConsentStorageManager.clearConsentForUninstalledApp("");
                });
    }

    @Test
    public void testDatastoreBasicApis() throws IOException {
        PrivacySandboxFeatureType privacySandboxFeatureType =
                PrivacySandboxFeatureType.PRIVACY_SANDBOX_FIRST_CONSENT;
        mAppConsentStorageManager.setCurrentPrivacySandboxFeature(privacySandboxFeatureType);
        assertEquals(
                privacySandboxFeatureType,
                mAppConsentStorageManager.getCurrentPrivacySandboxFeature());

        mAppConsentStorageManager.recordDefaultAdIdState(true);

        assertTrue(mAppConsentStorageManager.getDefaultAdIdState());

        mAppConsentStorageManager.setU18Account(true);
        assertTrue(mAppConsentStorageManager.isU18Account());

        for (AdServicesApiType apiType : AdServicesApiType.values()) {
            if (apiType == AdServicesApiType.UNKNOWN) {
                // Skip UNKNOWN, recordDefaultConsent will throw exception for unknown
                continue;
            }
            mAppConsentStorageManager.recordDefaultConsent(apiType, true);

            assertTrue(mAppConsentStorageManager.getDefaultConsent(apiType).isGiven());

            mAppConsentStorageManager.setConsent(apiType, true);
            assertTrue(mAppConsentStorageManager.getConsent(apiType).isGiven());
        }
        mAppConsentStorageManager.recordGaUxNotificationDisplayed(true);
        assertTrue(mAppConsentStorageManager.wasGaUxNotificationDisplayed());

        mAppConsentStorageManager.recordNotificationDisplayed(true);
        assertTrue(mAppConsentStorageManager.wasNotificationDisplayed());

        mAppConsentStorageManager.setU18NotificationDisplayed(true);
        assertTrue(mAppConsentStorageManager.wasU18NotificationDisplayed());
    }

    @Test
    public void testGetEnrollmentChannel() {
        for (PrivacySandboxUxCollection ux : PrivacySandboxUxCollection.values()) {
            for (PrivacySandboxEnrollmentChannelCollection channel :
                    ux.getEnrollmentChannelCollection()) {
                doReturn(channel).when(mUxStatesDaoMock).getEnrollmentChannel(ux);
                assertThat(mAppConsentStorageManager.getEnrollmentChannel(ux)).isEqualTo(channel);
                mAppConsentStorageManager.setEnrollmentChannel(ux, channel);
            }
        }

        verify(mUxStatesDaoMock, times(20)).getEnrollmentChannel(any());
        verify(mUxStatesDaoMock, times(20)).setEnrollmentChannel(any(), any());
    }

    @Test
    public void testGetKnownAppsWithConsentAfterConsentForOneOfThemWasRevoked() throws IOException {
        setMockfor3Apps(false);

        App app = App.create(AppConsentDaoFixture.APP10_PACKAGE_NAME);

        // revoke consent for first app
        mAppConsentStorageManager.setConsentForApp(AppConsentDaoFixture.APP10_PACKAGE_NAME, true);
        ImmutableList<String> knownAppsWithConsent =
                mAppConsentStorageManager.getKnownAppsWithConsent();
        ImmutableList<String> appsWithRevokedConsent =
                mAppConsentStorageManager.getAppsWithRevokedConsent();

        // all apps have received a consent
        assertThat(knownAppsWithConsent).hasSize(2);
        assertThat(appsWithRevokedConsent).hasSize(1);
        assertThat(appsWithRevokedConsent.get(0)).isEqualTo(app.getPackageName());
    }

    @Test
    public void testGetKnownAppsWithConsentAfterConsentForOneOfThemWasRevokedAndRestored()
            throws IOException {
        setMockfor3Apps(false);
        // revoke consent for first app
        mAppConsentStorageManager.setConsentForApp(AppConsentDaoFixture.APP10_PACKAGE_NAME, true);
        ImmutableList<String> knownAppsWithConsent =
                mAppConsentStorageManager.getKnownAppsWithConsent();
        ImmutableList<String> appsWithRevokedConsent =
                mAppConsentStorageManager.getAppsWithRevokedConsent();

        // all apps have received a consent
        assertThat(knownAppsWithConsent).hasSize(2);
        assertThat(appsWithRevokedConsent).hasSize(1);
        assertThat(appsWithRevokedConsent.get(0))
                .isEqualTo(AppConsentDaoFixture.APP10_PACKAGE_NAME);

        // restore consent for first app
        mAppConsentStorageManager.setConsentForApp(AppConsentDaoFixture.APP10_PACKAGE_NAME, false);
        knownAppsWithConsent = mAppConsentStorageManager.getKnownAppsWithConsent();
        appsWithRevokedConsent = mAppConsentStorageManager.getAppsWithRevokedConsent();

        // all apps have received a consent
        assertThat(knownAppsWithConsent).hasSize(3);
        assertThat(appsWithRevokedConsent).isEmpty();
    }

    @Test
    public void testGetKnownAppsWithConsent_ppApiOnly() throws IOException {
        setMockfor3Apps(false);

        ImmutableList<String> knownAppsWithConsent =
                mAppConsentStorageManager.getKnownAppsWithConsent();
        ImmutableList<String> appsWithRevokedConsent =
                mAppConsentStorageManager.getAppsWithRevokedConsent();

        // all apps have received a consent
        assertThat(knownAppsWithConsent).hasSize(3);
        assertThat(appsWithRevokedConsent).isEmpty();
    }

    @Test
    public void testGetSetUx() {
        for (PrivacySandboxUxCollection ux : PrivacySandboxUxCollection.values()) {
            doReturn(ux).when(mUxStatesDaoMock).getUx();
            assertThat(mAppConsentStorageManager.getUx()).isEqualTo(ux);

            mAppConsentStorageManager.setUx(ux);
        }

        verify(mUxStatesDaoMock, times(5)).getUx();
        verify(mUxStatesDaoMock, times(5)).setUx(any());
    }

    @Test
    public void testIsConsentRevokedForApp() throws IOException {
        // Moved from testIsFledgeConsentRevokedForAppWithFullApiConsentGaUxDisabled_ppApiOnly
        mockGetPackageUid(AppConsentDaoFixture.APP10_PACKAGE_NAME, AppConsentDaoFixture.APP10_UID);
        mockGetPackageUid(AppConsentDaoFixture.APP20_PACKAGE_NAME, AppConsentDaoFixture.APP20_UID);
        mockGetPackageUid(AppConsentDaoFixture.APP30_PACKAGE_NAME, AppConsentDaoFixture.APP30_UID);

        mAppDaoDatastore.put(AppConsentDaoFixture.APP10_DATASTORE_KEY, false);
        mAppDaoDatastore.put(AppConsentDaoFixture.APP20_DATASTORE_KEY, true);

        assertFalse(
                mAppConsentStorageManager.isConsentRevokedForApp(
                        AppConsentDaoFixture.APP10_PACKAGE_NAME));
        assertTrue(
                mAppConsentStorageManager.isConsentRevokedForApp(
                        AppConsentDaoFixture.APP20_PACKAGE_NAME));
        assertFalse(
                mAppConsentStorageManager.isConsentRevokedForApp(
                        AppConsentDaoFixture.APP30_PACKAGE_NAME));
    }

    @Test
    public void testRecordUserManualInteractionWithConsent() throws IOException {
        for (int i = 0; i < 3; i++) {
            int interaction = i - 1;
            mAppConsentStorageManager.recordUserManualInteractionWithConsent(interaction);
            assertEquals(
                    interaction, mAppConsentStorageManager.getUserManualInteractionWithConsent());
        }
    }

    @Test
    public void testResetAllAppConsentAndAppData()
            throws IOException, PackageManager.NameNotFoundException {

        setMockfor3Apps(true);

        // Verify population was successful
        ImmutableList<String> knownAppsWithConsent =
                mAppConsentStorageManager.getKnownAppsWithConsent();
        ImmutableList<String> appsWithRevokedConsent =
                mAppConsentStorageManager.getAppsWithRevokedConsent();
        assertThat(knownAppsWithConsent).hasSize(2);
        assertThat(appsWithRevokedConsent).hasSize(1);

        mAppConsentStorageManager.clearAllAppConsentData();

        // All app consent data was deleted
        knownAppsWithConsent = mAppConsentStorageManager.getKnownAppsWithConsent();
        appsWithRevokedConsent = mAppConsentStorageManager.getAppsWithRevokedConsent();
        assertThat(knownAppsWithConsent).isEmpty();
        assertThat(appsWithRevokedConsent).isEmpty();
    }

    @Test
    public void testResetAllowedAppConsentAndAppData() throws IOException {

        setMockfor3Apps(true);

        // Verify population was successful
        ImmutableList<String> knownAppsWithConsentBeforeReset =
                mAppConsentStorageManager.getKnownAppsWithConsent();
        ImmutableList<String> appsWithRevokedConsentBeforeReset =
                mAppConsentStorageManager.getAppsWithRevokedConsent();
        assertThat(knownAppsWithConsentBeforeReset).hasSize(2);
        assertThat(appsWithRevokedConsentBeforeReset).hasSize(1);
        mAppConsentStorageManager.clearKnownAppsWithConsent();

        // Known apps with consent were cleared; revoked apps were not deleted
        ImmutableList<String> knownAppsWithConsentAfterReset =
                mAppConsentStorageManager.getKnownAppsWithConsent();
        ImmutableList<String> appsWithRevokedConsentAfterReset =
                mAppConsentStorageManager.getAppsWithRevokedConsent();
        assertThat(knownAppsWithConsentAfterReset).isEmpty();
        assertThat(appsWithRevokedConsentAfterReset).hasSize(1);

        assertThat(appsWithRevokedConsentAfterReset)
                .containsExactlyElementsIn(appsWithRevokedConsentBeforeReset);
    }

    @Test
    public void testSetConsentForApp() throws Exception {

        mockGetPackageUid(AppConsentDaoFixture.APP10_PACKAGE_NAME, AppConsentDaoFixture.APP10_UID);

        mAppConsentStorageManager.setConsentForApp(AppConsentDaoFixture.APP10_PACKAGE_NAME, true);
        assertTrue(
                mAppConsentStorageManager.isConsentRevokedForApp(
                        AppConsentDaoFixture.APP10_PACKAGE_NAME));

        mAppConsentStorageManager.setConsentForApp(AppConsentDaoFixture.APP10_PACKAGE_NAME, false);
        assertFalse(
                mAppConsentStorageManager.isConsentRevokedForApp(
                        AppConsentDaoFixture.APP10_PACKAGE_NAME));
    }

    private List<ApplicationInfo> createApplicationInfos(String... packageNames) {
        return Arrays.stream(packageNames)
                .map(s -> ApplicationInfoBuilder.newBuilder().setPackageName(s).build())
                .collect(Collectors.toList());
    }

    private void mock3AppsInstalled() {
        mockGetPackageUid(AppConsentDaoFixture.APP10_PACKAGE_NAME, AppConsentDaoFixture.APP10_UID);
        mockGetPackageUid(AppConsentDaoFixture.APP20_PACKAGE_NAME, AppConsentDaoFixture.APP20_UID);
        mockGetPackageUid(AppConsentDaoFixture.APP30_PACKAGE_NAME, AppConsentDaoFixture.APP30_UID);
        List<ApplicationInfo> applicationsInstalled =
                createApplicationInfos(
                        AppConsentDaoFixture.APP10_PACKAGE_NAME,
                        AppConsentDaoFixture.APP20_PACKAGE_NAME,
                        AppConsentDaoFixture.APP30_PACKAGE_NAME);
        mockInstalledApplications(applicationsInstalled);
    }

    private void mockGetPackageUid(@NonNull String packageName, int uid) {
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

    private void mockThrowExceptionOnGetPackageUid(@NonNull String packageName) {
        doThrow(PackageManager.NameNotFoundException.class)
                .when(
                        () ->
                                PackageManagerCompatUtils.getPackageUid(
                                        any(), eq(packageName), anyInt()));
    }

    private void setConsentForOneApp() throws IOException {
        mockGetPackageUid(AppConsentDaoFixture.APP10_PACKAGE_NAME, AppConsentDaoFixture.APP10_UID);
        mAppConsentStorageManager.setConsentForApp(AppConsentDaoFixture.APP10_PACKAGE_NAME, false);

        List<ApplicationInfo> applicationsInstalled =
                createApplicationInfos(AppConsentDaoFixture.APP10_PACKAGE_NAME);
        mockInstalledApplications(applicationsInstalled);
        assertTrue(
                mAppConsentStorageManager
                        .getKnownAppsWithConsent()
                        .contains(AppConsentDaoFixture.APP10_PACKAGE_NAME));
    }

    private void setMockfor3Apps(boolean value) throws IOException {
        mock3AppsInstalled();
        mAppDaoDatastore.put(AppConsentDaoFixture.APP10_DATASTORE_KEY, false);
        mAppDaoDatastore.put(AppConsentDaoFixture.APP20_DATASTORE_KEY, value);
        mAppDaoDatastore.put(AppConsentDaoFixture.APP30_DATASTORE_KEY, false);
    }
}

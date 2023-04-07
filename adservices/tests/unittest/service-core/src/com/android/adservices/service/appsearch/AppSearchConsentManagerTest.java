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

package com.android.adservices.service.appsearch;

import static com.google.common.truth.Truth.assertThat;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyInt;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import android.content.Context;
import android.content.pm.ApplicationInfo;

import androidx.test.core.app.ApplicationProvider;
import androidx.test.filters.SmallTest;

import com.android.adservices.service.common.compat.PackageManagerCompatUtils;
import com.android.adservices.service.common.feature.PrivacySandboxFeatureType;
import com.android.adservices.service.consent.AdServicesApiType;
import com.android.adservices.service.consent.App;
import com.android.adservices.service.consent.ConsentManager;
import com.android.dx.mockito.inline.extended.ExtendedMockito;

import org.junit.After;
import org.junit.Before;
import org.junit.Test;
import org.mockito.Mock;
import org.mockito.MockitoSession;
import org.mockito.quality.Strictness;

import java.util.List;

@SmallTest
public class AppSearchConsentManagerTest {
    private Context mContext = ApplicationProvider.getApplicationContext();
    private MockitoSession mStaticMockSession;
    @Mock private AppSearchConsentWorker mAppSearchConsentWorker;
    private AppSearchConsentManager mAppSearchConsentManager;
    private static final String API_TYPE = AdServicesApiType.TOPICS.toPpApiDatastoreKey();
    private static final String PACKAGE_NAME1 = "foo.bar.one";
    private static final String PACKAGE_NAME2 = "foo.bar.two";
    private static final String PACKAGE_NAME3 = "foo.bar.three";

    @Before
    public void setup() {
        mStaticMockSession =
                ExtendedMockito.mockitoSession()
                        .mockStatic(AppSearchConsentWorker.class)
                        .mockStatic(PackageManagerCompatUtils.class)
                        .strictness(Strictness.WARN)
                        .initMocks(this)
                        .startMocking();
        ExtendedMockito.doReturn(mAppSearchConsentWorker)
                .when(() -> AppSearchConsentWorker.getInstance(mContext));
        mAppSearchConsentManager = AppSearchConsentManager.getInstance(mContext);
        ApplicationInfo app1 = new ApplicationInfo();
        app1.packageName = PACKAGE_NAME1;
        ApplicationInfo app2 = new ApplicationInfo();
        app2.packageName = PACKAGE_NAME2;
        ApplicationInfo app3 = new ApplicationInfo();
        app3.packageName = PACKAGE_NAME3;
        ExtendedMockito.doReturn(List.of(app1, app2, app3))
                .when(() -> PackageManagerCompatUtils.getInstalledApplications(any(), anyInt()));
    }

    @After
    public void teardown() {
        if (mStaticMockSession != null) {
            mStaticMockSession.finishMocking();
        }
    }

    @Test
    public void testGetConsent() {
        when(mAppSearchConsentWorker.getConsent(API_TYPE)).thenReturn(false);
        assertThat(mAppSearchConsentManager.getConsent(API_TYPE)).isEqualTo(false);

        when(mAppSearchConsentWorker.getConsent(API_TYPE)).thenReturn(true);
        assertThat(mAppSearchConsentManager.getConsent(API_TYPE)).isEqualTo(true);
    }

    @Test
    public void testSetConsent() {
        mAppSearchConsentManager.setConsent(API_TYPE, true);
        verify(mAppSearchConsentWorker).setConsent(API_TYPE, true);

        mAppSearchConsentManager.setConsent(API_TYPE, false);
        verify(mAppSearchConsentWorker).setConsent(API_TYPE, false);
    }

    @Test
    public void testKnownAppsWithConsent() {
        String consentType = AppSearchAppConsentDao.APPS_WITH_CONSENT;
        when(mAppSearchConsentWorker.getAppsWithConsent(eq(consentType)))
                .thenReturn(List.of(PACKAGE_NAME1, PACKAGE_NAME2));
        List<App> result = mAppSearchConsentManager.getKnownAppsWithConsent();
        assertThat(result.size()).isEqualTo(2);

        String package1 = result.get(0).getPackageName();
        String package2 = result.get(1).getPackageName();
        assertThat(package1.equals(PACKAGE_NAME1) || package2.equals(PACKAGE_NAME1)).isTrue();
        assertThat(package1.equals(PACKAGE_NAME2) || package2.equals(PACKAGE_NAME2)).isTrue();
    }

    @Test
    public void testAppsWithRevokedConsent() {
        String consentType = AppSearchAppConsentDao.APPS_WITH_REVOKED_CONSENT;
        when(mAppSearchConsentWorker.getAppsWithConsent(eq(consentType)))
                .thenReturn(List.of(PACKAGE_NAME1, PACKAGE_NAME2));
        List<App> result = mAppSearchConsentManager.getAppsWithRevokedConsent();
        assertThat(result.size()).isEqualTo(2);

        String package1 = result.get(0).getPackageName();
        String package2 = result.get(1).getPackageName();
        assertThat(package1.equals(PACKAGE_NAME1) || package2.equals(PACKAGE_NAME1)).isTrue();
        assertThat(package1.equals(PACKAGE_NAME2) || package2.equals(PACKAGE_NAME2)).isTrue();
    }

    @Test
    public void testRevokeConsentForApp() {
        App app = App.create(PACKAGE_NAME1);
        mAppSearchConsentManager.revokeConsentForApp(app);
        verify(mAppSearchConsentWorker)
                .addAppWithConsent(
                        AppSearchAppConsentDao.APPS_WITH_REVOKED_CONSENT, app.getPackageName());
        verify(mAppSearchConsentWorker)
                .removeAppWithConsent(
                        AppSearchAppConsentDao.APPS_WITH_CONSENT, app.getPackageName());
    }

    @Test
    public void testRestoreConsentForApp() {
        App app = App.create(PACKAGE_NAME1);
        mAppSearchConsentManager.restoreConsentForApp(app);
        verify(mAppSearchConsentWorker)
                .addAppWithConsent(AppSearchAppConsentDao.APPS_WITH_CONSENT, app.getPackageName());
        verify(mAppSearchConsentWorker)
                .removeAppWithConsent(
                        AppSearchAppConsentDao.APPS_WITH_REVOKED_CONSENT, app.getPackageName());
    }

    @Test
    public void testClearAllAppConsentData() {
        mAppSearchConsentManager.clearAllAppConsentData();
        verify(mAppSearchConsentWorker)
                .clearAppsWithConsent(AppSearchAppConsentDao.APPS_WITH_CONSENT);
        verify(mAppSearchConsentWorker)
                .clearAppsWithConsent(AppSearchAppConsentDao.APPS_WITH_REVOKED_CONSENT);
    }

    @Test
    public void testClearKnownAppsWithConsent() throws Exception {
        mAppSearchConsentManager.clearKnownAppsWithConsent();
        verify(mAppSearchConsentWorker)
                .clearAppsWithConsent(AppSearchAppConsentDao.APPS_WITH_CONSENT);
    }

    @Test
    public void testIsFledgeConsentRevokedForApp_consented() {
        when(mAppSearchConsentWorker.getAppsWithConsent(AppSearchAppConsentDao.APPS_WITH_CONSENT))
                .thenReturn(List.of(PACKAGE_NAME1));
        when(mAppSearchConsentWorker.getAppsWithConsent(
                        AppSearchAppConsentDao.APPS_WITH_REVOKED_CONSENT))
                .thenReturn(List.of());
        assertThat(mAppSearchConsentManager.isFledgeConsentRevokedForApp(PACKAGE_NAME1)).isFalse();
    }

    @Test
    public void testIsFledgeConsentRevokedForApp_revoked() {
        when(mAppSearchConsentWorker.getAppsWithConsent(AppSearchAppConsentDao.APPS_WITH_CONSENT))
                .thenReturn(List.of(PACKAGE_NAME1));
        when(mAppSearchConsentWorker.getAppsWithConsent(
                        AppSearchAppConsentDao.APPS_WITH_REVOKED_CONSENT))
                .thenReturn(List.of(PACKAGE_NAME1));
        assertThat(mAppSearchConsentManager.isFledgeConsentRevokedForApp(PACKAGE_NAME1)).isTrue();
    }

    @Test
    public void testIsFledgeConsentRevokedForAppAfterSettingFledgeUse() {
        when(mAppSearchConsentWorker.addAppWithConsent(
                        AppSearchAppConsentDao.APPS_WITH_CONSENT, PACKAGE_NAME1))
                .thenReturn(true);
        assertThat(
                        mAppSearchConsentManager.isFledgeConsentRevokedForAppAfterSettingFledgeUse(
                                PACKAGE_NAME1))
                .isFalse();
    }

    @Test
    public void testIsFledgeConsentRevokedForAppAfterSettingFledgeUse_revoked() {
        when(mAppSearchConsentWorker.addAppWithConsent(
                        AppSearchAppConsentDao.APPS_WITH_CONSENT, PACKAGE_NAME2))
                .thenReturn(false);
        assertThat(
                        mAppSearchConsentManager.isFledgeConsentRevokedForAppAfterSettingFledgeUse(
                                PACKAGE_NAME2))
                .isTrue();
    }

    @Test
    public void testClearConsentForUninstalledApp() {
        mAppSearchConsentManager.clearConsentForUninstalledApp(PACKAGE_NAME1);
        verify(mAppSearchConsentWorker)
                .removeAppWithConsent(
                        AppSearchAppConsentDao.APPS_WITH_REVOKED_CONSENT, PACKAGE_NAME1);
        verify(mAppSearchConsentWorker)
                .removeAppWithConsent(AppSearchAppConsentDao.APPS_WITH_CONSENT, PACKAGE_NAME1);
    }

    @Test
    public void testRecordNotificationDisplayed() {
        mAppSearchConsentManager.recordNotificationDisplayed();
        verify(mAppSearchConsentWorker).recordNotificationDisplayed();
    }

    @Test
    public void testRecordGaUxNotificationDisplayed() {
        mAppSearchConsentManager.recordGaUxNotificationDisplayed();
        verify(mAppSearchConsentWorker).recordGaUxNotificationDisplayed();
    }

    @Test
    public void testWasNotificationDisplayed() {
        when(mAppSearchConsentWorker.wasNotificationDisplayed()).thenReturn(false);
        assertThat(mAppSearchConsentManager.wasNotificationDisplayed()).isFalse();
        verify(mAppSearchConsentWorker).wasNotificationDisplayed();
    }

    @Test
    public void testWasGaUxNotificationDisplayed() {
        when(mAppSearchConsentWorker.wasGaUxNotificationDisplayed()).thenReturn(false);
        assertThat(mAppSearchConsentManager.wasGaUxNotificationDisplayed()).isFalse();
        verify(mAppSearchConsentWorker).wasGaUxNotificationDisplayed();
    }

    @Test
    public void testGetCurrentPrivacySandboxFeature() {
        when(mAppSearchConsentWorker.getPrivacySandboxFeature())
                .thenReturn(PrivacySandboxFeatureType.PRIVACY_SANDBOX_FIRST_CONSENT);
        assertThat(mAppSearchConsentManager.getCurrentPrivacySandboxFeature())
                .isEqualTo(PrivacySandboxFeatureType.PRIVACY_SANDBOX_FIRST_CONSENT);
        verify(mAppSearchConsentWorker).getPrivacySandboxFeature();
    }

    @Test
    public void testSetCurrentPrivacySandboxFeature() {
        mAppSearchConsentManager.setCurrentPrivacySandboxFeature(
                PrivacySandboxFeatureType.PRIVACY_SANDBOX_RECONSENT);
        verify(mAppSearchConsentWorker)
                .setCurrentPrivacySandboxFeature(
                        PrivacySandboxFeatureType.PRIVACY_SANDBOX_RECONSENT);
    }

    @Test
    public void testGetUserManualInteractionsWithConsent() {
        when(mAppSearchConsentWorker.getUserManualInteractionWithConsent())
                .thenReturn(ConsentManager.MANUAL_INTERACTIONS_RECORDED);
        assertThat(mAppSearchConsentManager.getUserManualInteractionWithConsent())
                .isEqualTo(ConsentManager.MANUAL_INTERACTIONS_RECORDED);
        verify(mAppSearchConsentWorker).getUserManualInteractionWithConsent();
    }

    @Test
    public void testRecordUserManualInteractionWithConsent() {
        mAppSearchConsentManager.recordUserManualInteractionWithConsent(
                ConsentManager.NO_MANUAL_INTERACTIONS_RECORDED);
        verify(mAppSearchConsentWorker)
                .recordUserManualInteractionWithConsent(
                        ConsentManager.NO_MANUAL_INTERACTIONS_RECORDED);
    }
}

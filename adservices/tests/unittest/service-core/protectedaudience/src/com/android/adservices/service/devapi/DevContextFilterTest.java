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

package com.android.adservices.service.devapi;

import static com.android.adservices.devapi.DevSessionFixture.IN_DEV;
import static com.android.adservices.devapi.DevSessionFixture.IN_PROD;
import static com.android.adservices.devapi.DevSessionFixture.TRANSITIONING_DEV_TO_PROD;
import static com.android.adservices.devapi.DevSessionFixture.TRANSITIONING_PROD_TO_DEV;

import static com.google.common.truth.Truth.assertWithMessage;
import static com.google.common.util.concurrent.Futures.immediateFuture;

import static org.junit.Assert.assertThrows;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyInt;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.atLeastOnce;
import static org.mockito.Mockito.doReturn;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import android.content.ContentResolver;
import android.content.Context;
import android.content.pm.ApplicationInfo;
import android.content.pm.PackageManager;
import android.os.Build;
import android.os.Process;
import android.provider.Settings;

import com.android.adservices.common.AdServicesExtendedMockitoTestCase;
import com.android.adservices.devapi.DevSessionFixture;
import com.android.modules.utils.testing.ExtendedMockitoRule.MockStatic;

import org.junit.Before;
import org.junit.Test;
import org.mockito.Mock;

import java.util.concurrent.TimeUnit;

@MockStatic(Settings.Global.class)
@MockStatic(Build.class)
public final class DevContextFilterTest extends AdServicesExtendedMockitoTestCase {
    private static final int APP_UID = 100;
    private static final String APP_PACKAGE = "com.test.myapp";
    private static final long DEV_SESSION_SET_TIMEOUT_SEC = 2;

    @Mock private PackageManager mMockPackageManager;
    @Mock private AppPackageNameRetriever mMockAppPackageNameRetriever;
    @Mock private ContentResolver mMockContentResolver;
    @Mock private DevSessionDataStore mMockDevSessionDataStore;

    private DevContextFilter mDevContextFilter;

    @Before
    public void setUp() {
        mDevContextFilter =
                new DevContextFilter(
                        mMockContentResolver,
                        mMockPackageManager,
                        mMockAppPackageNameRetriever,
                        mMockDevSessionDataStore);
    }

    @Test
    public void testCreateDevContextReturnsDevContextWithAppInfo() throws Exception {
        enableDeveloperOptions();
        mockPackageNameForUid(APP_UID, APP_PACKAGE);
        mockInstalledApplications(aDebuggableAppInfo());
        when(mMockDevSessionDataStore.get()).thenReturn(immediateFuture(IN_PROD));

        DevContext devContext = mDevContextFilter.createDevContext(APP_UID);

        assertWithMessage("createDevContext(%s)", APP_UID).that(devContext).isNotNull();
        expect.withMessage("devContext.getDevOptionsEnabled()")
                .that(devContext.getDeviceDevOptionsEnabled())
                .isTrue();
        expect.withMessage("devContext.getCallingAppPackageName()")
                .that(devContext.getCallingAppPackageName())
                .isEqualTo(APP_PACKAGE);
    }

    @Test
    public void testCreateDevContextWithNonDebuggableCallerDuringDevSession() throws Exception {
        enableDeveloperOptions();
        mockPackageNameForUid(APP_UID, APP_PACKAGE);
        mockInstalledApplications(aNonDebuggableAppInfo());
        when(mMockDevSessionDataStore.get()).thenReturn(immediateFuture(IN_DEV));

        assertThrows(SecurityException.class, () -> mDevContextFilter.createDevContext(APP_UID));
    }

    @Test
    public void testCreateDevContextWhileExitingDevSession() throws Exception {
        enableDeveloperOptions();
        mockPackageNameForUid(APP_UID, APP_PACKAGE);
        mockInstalledApplications(aDebuggableAppInfo());
        when(mMockDevSessionDataStore.get()).thenReturn(immediateFuture(TRANSITIONING_DEV_TO_PROD));

        assertThrows(
                IllegalStateException.class, () -> mDevContextFilter.createDevContext(APP_UID));
    }

    @Test
    public void testCreateDevContextWithNonDebuggableCallerDuringUnknownDevSession()
            throws Exception {
        enableDeveloperOptions();
        mockPackageNameForUid(APP_UID, APP_PACKAGE);
        mockInstalledApplications(aDebuggableAppInfo());
        when(mMockDevSessionDataStore.get()).thenReturn(immediateFuture(DevSession.UNKNOWN));

        assertThrows(
                IllegalStateException.class, () -> mDevContextFilter.createDevContext(APP_UID));
    }

    @Test
    public void testCreateDevContextReturnsDevDisabledInstanceIfDeviceIsNotInDevOptionsEnabled()
            throws Exception {
        disableDeveloperOptions();
        mockPackageNameForUid(APP_UID, APP_PACKAGE);
        mockInstalledApplications(aDebuggableAppInfo());

        DevContext devContext = mDevContextFilter.createDevContext(APP_UID);

        assertWithMessage("createDevContext(%s)", APP_UID).that(devContext).isNotNull();

        expect.withMessage("devContext.getDevOptionsEnabled()")
                .that(devContext.getDeviceDevOptionsEnabled())
                .isFalse();
        expect.withMessage("devContext.getCallingAppPackageName()")
                .that(devContext.getCallingAppPackageName())
                .isEqualTo(
                        String.format(
                                DevContextFilter.PACKAGE_NAME_FOR_DISABLED_DEVELOPER_MODE_TEMPLATE,
                                APP_UID));
    }

    @Test
    public void testCreateDevContextReturnsDevEnabledInstanceIfNotInDeveloperModeDebuggableBuild()
            throws Exception {
        // No need to call disableDeveloperOptions since they wouldn't be checked because we are
        // in a debuggable build and Mockito would complain of the not necesasry mock.
        // Not preparing the mock would anyway cause the check method to return false.
        when(Build.isDebuggable()).thenReturn(true);
        when(mMockDevSessionDataStore.get()).thenReturn(immediateFuture(IN_PROD));

        mockPackageNameForUid(APP_UID, APP_PACKAGE);
        mockInstalledApplications(aDebuggableAppInfo());

        DevContext devContext = mDevContextFilter.createDevContext(APP_UID);

        assertWithMessage("createDevContext(%s)", APP_UID).that(devContext).isNotNull();
        expect.withMessage("devContext.getDevOptionsEnabled()")
                .that(devContext.getDeviceDevOptionsEnabled())
                .isTrue();
        expect.withMessage("devContext.getCallingAppPackageName()")
                .that(devContext.getCallingAppPackageName())
                .isEqualTo(APP_PACKAGE);
    }

    @Test
    public void testCreateDevContextReturnsDevDisabledInstanceIfAppIsNotDebuggable()
            throws Exception {
        enableDeveloperOptions();
        mockPackageNameForUid(APP_UID, APP_PACKAGE);
        mockInstalledApplications(aNonDebuggableAppInfo());
        when(mMockDevSessionDataStore.get()).thenReturn(immediateFuture(IN_PROD));

        DevContext devContext = mDevContextFilter.createDevContext(APP_UID);

        assertWithMessage("createDevContext(%s)", APP_UID).that(devContext).isNotNull();
        expect.withMessage("devContext.getDevOptionsEnabled()")
                .that(devContext.getDeviceDevOptionsEnabled())
                .isFalse();
        expect.withMessage("devContext.getCallingAppPackageName()")
                .that(devContext.getCallingAppPackageName())
                .isEqualTo(APP_PACKAGE);
    }

    @Test
    public void testCreateDevContextInDevSession() throws Exception {
        enableDeveloperOptions();
        mockPackageNameForUid(APP_UID, APP_PACKAGE);
        mockInstalledApplications(aDebuggableAppInfo()); // Must be debuggalbe during dev session.
        DevSession devSession = DevSessionFixture.IN_DEV;
        doReturn(immediateFuture(devSession)).when(mMockDevSessionDataStore).get();

        DevContext devContext = mDevContextFilter.createDevContext(APP_UID);

        expect.withMessage("Expect DevSession to be valid")
                .that(devContext.getDevSession())
                .isEqualTo(devSession);
        verify(mMockDevSessionDataStore, atLeastOnce()).get();
    }

    @Test
    public void testCreateDevContextInDevSessionWithDeveloperOptionsDisabled() throws Exception {
        mockPackageNameForUid(APP_UID, APP_PACKAGE);
        mockInstalledApplications(aNonDebuggableAppInfo());

        DevContext devContext = mDevContextFilter.createDevContext(APP_UID);

        expect.that(devContext.getDevSession().getState()).isEqualTo(DevSessionState.IN_PROD);
        verify(mMockDevSessionDataStore, never()).get();
    }

    @Test
    public void testCreateDevContextLookupFailed() throws Exception {
        enableDeveloperOptions();
        when(mMockAppPackageNameRetriever.getAppPackageNameForUid(APP_UID))
                .thenThrow(new IllegalArgumentException("D'OH!"));
        when(mMockDevSessionDataStore.get()).thenReturn(immediateFuture(IN_PROD));
        mockInstalledApplications(aDebuggableAppInfo());

        DevContext devContext = mDevContextFilter.createDevContext(APP_UID);

        assertWithMessage("createDevContext(%s)", APP_UID).that(devContext).isNotNull();
        expect.withMessage("devContext.getDevOptionsEnabled()")
                .that(devContext.getDeviceDevOptionsEnabled())
                .isFalse();
        expect.withMessage("devContext.getCallingAppPackageName()")
                .that(devContext.getCallingAppPackageName())
                .isEqualTo(
                        String.format(
                                DevContextFilter.PACKAGE_NAME_WHEN_LOOKUP_FAILED_TEMPLATE,
                                APP_UID));
    }

    @Test
    public void testGetSettingsForCurrentAppWithDeveloperModeFeatureEnabled() throws Exception {
        int myUid = Process.myUid();
        Context context = appContext.get();
        // Enable development options for the test app
        setDeveloperOptionsEnabled(context.getContentResolver(), true);
        DevSessionDataStoreFactory.get(/* developerModeFeatureEnabled= */ true)
                .set(IN_PROD)
                .get(DEV_SESSION_SET_TIMEOUT_SEC, TimeUnit.SECONDS);

        DevContextFilter nonMockedFilter =
                DevContextFilter.create(context, /* developerModeFeatureEnabled= */ true);
        DevContext devContext = nonMockedFilter.createDevContext(myUid);

        assertWithMessage("createDevContext(%s)", myUid).that(devContext).isNotNull();
        expect.withMessage("devContext.getDevOptionsEnabled()")
                .that(devContext.getDeviceDevOptionsEnabled())
                .isTrue();
        expect.withMessage("devContext.getCallingAppPackageName()")
                .that(devContext.getCallingAppPackageName())
                .isEqualTo(context.getPackageName());
    }

    @Test
    public void testGetSettingsForCurrentApp() {
        int myUid = Process.myUid();
        Context context = appContext.get();
        // Enable development options for the test app
        setDeveloperOptionsEnabled(context.getContentResolver(), true);

        DevContextFilter nonMockedFilter =
                DevContextFilter.create(context, /* developerModeFeatureEnabled= */ false);
        DevContext devContext = nonMockedFilter.createDevContext(myUid);

        assertWithMessage("createDevContext(%s)", myUid).that(devContext).isNotNull();
        expect.withMessage("devContext.getDevOptionsEnabled()")
                .that(devContext.getDeviceDevOptionsEnabled())
                .isTrue();
        expect.withMessage("devContext.getCallingAppPackageName()")
                .that(devContext.getCallingAppPackageName())
                .isEqualTo(context.getPackageName());
    }

    @Test
    public void testNoArgCallFailsIfCalledFromNonBinderThread() {
        DevContextFilter nonMockedFilter =
                DevContextFilter.create(appContext.get(), /* developerModeFeatureEnabled= */ false);

        assertThrows(IllegalStateException.class, nonMockedFilter::createDevContext);
    }

    @Test
    public void testNoArgCallFailsIfCalledFromNonBinderThreadWithDeveloperModeFeatureEnabled() {
        DevContextFilter nonMockedFilter =
                DevContextFilter.create(appContext.get(), /* developerModeFeatureEnabled= */ true);

        assertThrows(IllegalStateException.class, nonMockedFilter::createDevContext);
    }

    private void enableDeveloperOptions() {
        setDeveloperOptionsEnabled(mMockContentResolver, true);
    }

    private void disableDeveloperOptions() {
        setDeveloperOptionsEnabled(mMockContentResolver, false);
    }

    private void setDeveloperOptionsEnabled(ContentResolver contentResolver, boolean enabled) {
        when(Settings.Global.getInt(
                        contentResolver, Settings.Global.DEVELOPMENT_SETTINGS_ENABLED, 0))
                .thenReturn(enabled ? 1 : 0);
    }

    private ApplicationInfo aNonDebuggableAppInfo() {
        return new ApplicationInfo();
    }

    private ApplicationInfo aDebuggableAppInfo() {
        ApplicationInfo result = new ApplicationInfo();
        // Adding some extra flag to verify the check is done correctly
        result.flags = ApplicationInfo.FLAG_DEBUGGABLE | ApplicationInfo.FLAG_INSTALLED;
        return result;
    }

    private void mockInstalledApplications(ApplicationInfo applicationInfo)
            throws PackageManager.NameNotFoundException {
        if (sdkLevel.isAtLeastT()) {
            when(mMockPackageManager.getApplicationInfo(
                            eq(APP_PACKAGE), any(PackageManager.ApplicationInfoFlags.class)))
                    .thenReturn(applicationInfo);
        } else {
            when(mMockPackageManager.getApplicationInfo(eq(APP_PACKAGE), anyInt()))
                    .thenReturn(applicationInfo);
        }
    }

    private void mockPackageNameForUid(int uid, String packageName) {
        when(mMockAppPackageNameRetriever.getAppPackageNameForUid(uid)).thenReturn(packageName);
    }
}

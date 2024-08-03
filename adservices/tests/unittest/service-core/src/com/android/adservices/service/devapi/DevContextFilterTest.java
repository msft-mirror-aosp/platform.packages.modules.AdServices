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

import static com.google.common.truth.Truth.assertWithMessage;

import static org.junit.Assert.assertThrows;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyInt;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.when;

import android.content.ContentResolver;
import android.content.Context;
import android.content.pm.ApplicationInfo;
import android.content.pm.PackageManager;
import android.os.Process;
import android.provider.Settings;

import com.android.adservices.common.AdServicesExtendedMockitoTestCase;
import com.android.adservices.service.common.compat.BuildCompatUtils;
import com.android.modules.utils.testing.ExtendedMockitoRule.MockStatic;

import org.junit.Before;
import org.junit.Test;
import org.mockito.Mock;

@MockStatic(Settings.Global.class)
@MockStatic(BuildCompatUtils.class)
public final class DevContextFilterTest extends AdServicesExtendedMockitoTestCase {
    private static final int APP_UID = 100;
    private static final String APP_PACKAGE = "com.test.myapp";

    @Mock private PackageManager mMockPackageManager;
    @Mock private AppPackageNameRetriever mMockAppPackageNameRetriever;
    @Mock private ContentResolver mMockContentResolver;

    private DevContextFilter mDevContextFilter;

    @Before
    public void setUp() {
        mDevContextFilter =
                new DevContextFilter(
                        mMockContentResolver, mMockPackageManager, mMockAppPackageNameRetriever);
    }

    @Test
    public void testCreateDevContextReturnsDevContextWithAppInfo() throws Exception {
        enableDeveloperOptions();
        mockPackageNameForUid(APP_UID, APP_PACKAGE);
        mockInstalledApplications(aDebuggableAppInfo());

        DevContext devContext = mDevContextFilter.createDevContext(APP_UID);

        assertWithMessage("createDevContext(%s)", APP_UID).that(devContext).isNotNull();
        expect.withMessage("devContext.getDevOptionsEnabled()")
                .that(devContext.getDevOptionsEnabled())
                .isTrue();
        expect.withMessage("devContext.getCallingAppPackageName()")
                .that(devContext.getCallingAppPackageName())
                .isEqualTo(APP_PACKAGE);
    }

    @Test
    public void testCreateDevContextReturnsDevDisabledInstanceIfDeviceIsNotInDeveloperMode()
            throws Exception {
        disableDeveloperOptions();
        mockPackageNameForUid(APP_UID, APP_PACKAGE);
        mockInstalledApplications(aDebuggableAppInfo());

        DevContext devContext = mDevContextFilter.createDevContext(APP_UID);

        assertWithMessage("createDevContext(%s)", APP_UID).that(devContext).isNotNull();

        expect.withMessage("devContext.getDevOptionsEnabled()")
                .that(devContext.getDevOptionsEnabled())
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
        when(BuildCompatUtils.isDebuggable()).thenReturn(true);

        mockPackageNameForUid(APP_UID, APP_PACKAGE);
        mockInstalledApplications(aDebuggableAppInfo());

        DevContext devContext = mDevContextFilter.createDevContext(APP_UID);

        assertWithMessage("createDevContext(%s)", APP_UID).that(devContext).isNotNull();
        expect.withMessage("devContext.getDevOptionsEnabled()")
                .that(devContext.getDevOptionsEnabled())
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

        DevContext devContext = mDevContextFilter.createDevContext(APP_UID);

        assertWithMessage("createDevContext(%s)", APP_UID).that(devContext).isNotNull();
        expect.withMessage("devContext.getDevOptionsEnabled()")
                .that(devContext.getDevOptionsEnabled())
                .isFalse();
        expect.withMessage("devContext.getCallingAppPackageName()")
                .that(devContext.getCallingAppPackageName())
                .isEqualTo(APP_PACKAGE);
    }

    @Test
    public void testCreateDevContextLookupFailed() throws Exception {
        enableDeveloperOptions();
        when(mMockAppPackageNameRetriever.getAppPackageNameForUid(APP_UID))
                .thenThrow(new IllegalArgumentException("D'OH!"));
        mockInstalledApplications(aDebuggableAppInfo());

        DevContext devContext = mDevContextFilter.createDevContext(APP_UID);

        assertWithMessage("createDevContext(%s)", APP_UID).that(devContext).isNotNull();
        expect.withMessage("devContext.getDevOptionsEnabled()")
                .that(devContext.getDevOptionsEnabled())
                .isFalse();
        expect.withMessage("devContext.getCallingAppPackageName()")
                .that(devContext.getCallingAppPackageName())
                .isEqualTo(
                        String.format(
                                DevContextFilter.PACKAGE_NAME_WHEN_LOOKUP_FAILED_TEMPLATE,
                                APP_UID));
    }

    @Test
    public void testGetSettingsForCurrentApp() {
        int myUid = Process.myUid();
        Context context = appContext.get();
        // Enable development options for the test app
        setDeveloperOptionsEnabled(context.getContentResolver(), true);

        DevContextFilter nonMockedFilter = DevContextFilter.create(context);
        DevContext devContext = nonMockedFilter.createDevContext(myUid);

        assertWithMessage("createDevContext(%s)", myUid).that(devContext).isNotNull();
        expect.withMessage("devContext.getDevOptionsEnabled()")
                .that(devContext.getDevOptionsEnabled())
                .isTrue();
        expect.withMessage("devContext.getCallingAppPackageName()")
                .that(devContext.getCallingAppPackageName())
                .isEqualTo(context.getPackageName());
    }

    @Test
    public void testNoArgCallFailsIfCalledFromNonBinderThread() {
        DevContextFilter nonMockedFilter = DevContextFilter.create(appContext.get());

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

    // TODO(b/314969513): move to mocker
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

    // TODO(b/314969513): move to mocker
    private void mockPackageNameForUid(int uid, String packageName) {
        when(mMockAppPackageNameRetriever.getAppPackageNameForUid(uid)).thenReturn(packageName);
    }
}

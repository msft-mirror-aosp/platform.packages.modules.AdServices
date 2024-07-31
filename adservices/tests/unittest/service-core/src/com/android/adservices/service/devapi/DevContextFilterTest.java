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

import static com.google.common.truth.Truth.assertThat;

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

import androidx.test.core.app.ApplicationProvider;

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
        when(mMockAppPackageNameRetriever.getAppPackageNameForUid(APP_UID)).thenReturn(APP_PACKAGE);
        mockInstalledApplications(aDebuggableAppInfo());

        assertThat(mDevContextFilter.createDevContext(APP_UID))
                .isEqualTo(
                        DevContext.builder()
                                .setCallingAppPackageName(APP_PACKAGE)
                                .setDevOptionsEnabled(true)
                                .build());
    }

    @Test
    public void testCreateDevContextReturnsDevDisabledInstanceIfDeviceIsNotInDeveloperMode() {
        disableDeveloperOptions();

        assertThat(mDevContextFilter.createDevContext(APP_UID).getDevOptionsEnabled()).isFalse();
    }

    @Test
    public void testCreateDevContextReturnsDevEnabledInstanceIfNotInDeveloperModeDebuggableBuild()
            throws Exception {
        // No need to call disableDeveloperOptions since they wouldn't be checked because we are
        // in a debuggable build and Mockito would complain of the not necesasry mock.
        // Not preparing the mock would anyway cause the check method to return false.
        when(BuildCompatUtils.isDebuggable()).thenReturn(true);

        when(mMockAppPackageNameRetriever.getAppPackageNameForUid(APP_UID)).thenReturn(APP_PACKAGE);
        mockInstalledApplications(aDebuggableAppInfo());

        assertThat(mDevContextFilter.createDevContext(APP_UID))
                .isEqualTo(
                        DevContext.builder()
                                .setCallingAppPackageName(APP_PACKAGE)
                                .setDevOptionsEnabled(true)
                                .build());
    }

    @Test
    public void testCreateDevContextReturnsDevDisabledInstanceIfAppIsNotDebuggable()
            throws Exception {
        enableDeveloperOptions();
        when(mMockAppPackageNameRetriever.getAppPackageNameForUid(APP_UID)).thenReturn(APP_PACKAGE);
        mockInstalledApplications(aNonDebuggableAppInfo());
        assertThat(mDevContextFilter.createDevContext(APP_UID).getDevOptionsEnabled()).isFalse();
    }

    @Test
    public void testGetSettingsForCurrentApp() {
        Context applicationContext = ApplicationProvider.getApplicationContext();
        // Enable development options for the test app
        when(Settings.Global.getInt(
                        applicationContext.getContentResolver(),
                        Settings.Global.DEVELOPMENT_SETTINGS_ENABLED,
                        0))
                .thenReturn(1);

        DevContextFilter nonMockedFilter = DevContextFilter.create(applicationContext);
        assertThat(nonMockedFilter.createDevContext(Process.myUid()))
                .isEqualTo(
                        DevContext.builder()
                                .setCallingAppPackageName(applicationContext.getPackageName())
                                .setDevOptionsEnabled(true)
                                .build());
    }

    @Test
    public void testNoArgCallFailsIfCalledFromNonBinderThread() {
        DevContextFilter nonMockedFilter =
                DevContextFilter.create(mContext.getApplicationContext());

        assertThrows(IllegalStateException.class, nonMockedFilter::createDevContext);
    }

    private void enableDeveloperOptions() {
        when(Settings.Global.getInt(
                        mMockContentResolver, Settings.Global.DEVELOPMENT_SETTINGS_ENABLED, 0))
                .thenReturn(1);
    }

    private void disableDeveloperOptions() {
        when(Settings.Global.getInt(
                        mMockContentResolver, Settings.Global.DEVELOPMENT_SETTINGS_ENABLED, 0))
                .thenReturn(0);
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
}

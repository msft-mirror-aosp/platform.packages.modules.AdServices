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

package com.android.adservices.service.common.compat;

import static android.content.pm.PackageManager.NameNotFoundException;

import static com.android.adservices.service.stats.AdServicesStatsLog.AD_SERVICES_ERROR_REPORTED__ERROR_CODE__PACKAGE_NAME_NOT_FOUND_EXCEPTION;
import static com.android.adservices.service.stats.AdServicesStatsLog.AD_SERVICES_ERROR_REPORTED__PPAPI_NAME__COMMON;
import static com.android.dx.mockito.inline.extended.ExtendedMockito.any;
import static com.android.dx.mockito.inline.extended.ExtendedMockito.anyInt;
import static com.android.dx.mockito.inline.extended.ExtendedMockito.anyString;
import static com.android.dx.mockito.inline.extended.ExtendedMockito.doReturn;
import static com.android.dx.mockito.inline.extended.ExtendedMockito.eq;
import static com.android.dx.mockito.inline.extended.ExtendedMockito.never;
import static com.android.dx.mockito.inline.extended.ExtendedMockito.times;
import static com.android.dx.mockito.inline.extended.ExtendedMockito.verify;
import static com.android.dx.mockito.inline.extended.ExtendedMockito.when;

import static com.google.common.truth.Truth.assertThat;

import static org.junit.Assert.assertThrows;

import android.content.ComponentName;
import android.content.pm.ApplicationInfo;
import android.content.pm.PackageInfo;
import android.content.pm.PackageManager;

import com.android.adservices.common.AdServicesExtendedMockitoTestCase;
import com.android.adservices.common.logging.AdServicesLoggingUsageRule;
import com.android.adservices.common.logging.annotations.ExpectErrorLogUtilCall;
import com.android.adservices.errorlogging.ErrorLogUtil;
import com.android.adservices.shared.testing.annotations.RequiresSdkLevelAtLeastT;
import com.android.modules.utils.build.SdkLevel;
import com.android.modules.utils.testing.ExtendedMockitoRule.MockStatic;

import com.google.common.collect.ImmutableList;

import org.junit.Rule;
import org.junit.Test;
import org.mockito.Mock;
import org.mockito.Mockito;

@MockStatic(ErrorLogUtil.class)
public final class PackageManagerCompatUtilsTest extends AdServicesExtendedMockitoTestCase {
    private static final String ADSERVICES_PACKAGE_NAME = "com.android.adservices.api";
    private static final String EXTSERVICES_PACKAGE_NAME = "com.android.ext.services";

    @Mock private PackageManager mPackageManagerMock;
    @Mock private PackageInfo mPackageInfo;
    @Mock private ApplicationInfo mApplicationInfo;

    @Rule(order = 11)
    public final AdServicesLoggingUsageRule errorLogUtilUsageRule =
            AdServicesLoggingUsageRule.errorLogUtilUsageRule();

    @Test
    public void testPackageManagerCompatUtilsValidatesArguments() {
        assertThrows(
                NullPointerException.class,
                () -> PackageManagerCompatUtils.getInstalledApplications(null, 0));

        assertThrows(
                NullPointerException.class,
                () -> PackageManagerCompatUtils.getInstalledPackages(null, 0));

        assertThrows(
                NullPointerException.class,
                () -> PackageManagerCompatUtils.getPackageUid(null, "com.example.app", 0));

        assertThrows(
                NullPointerException.class,
                () -> PackageManagerCompatUtils.getPackageUid(mPackageManagerMock, null, 0));

        assertThrows(
                NullPointerException.class,
                () -> PackageManagerCompatUtils.getApplicationInfo(null, "com.example.app", 0));

        assertThrows(
                NullPointerException.class,
                () -> PackageManagerCompatUtils.getApplicationInfo(mPackageManagerMock, null, 0));
    }

    @Test
    @MockStatic(SdkLevel.class)
    public void testGetInstalledApplications_SMinus() {
        mocker.mockIsAtLeastT(false);
        doReturn(ImmutableList.of(mApplicationInfo))
                .when(mPackageManagerMock)
                .getInstalledApplications(anyInt());

        final int flags = PackageManager.MATCH_APEX;
        assertThat(PackageManagerCompatUtils.getInstalledApplications(mPackageManagerMock, flags))
                .isEqualTo(ImmutableList.of(mApplicationInfo));
        verify(mPackageManagerMock).getInstalledApplications(eq(flags));
    }

    @Test
    @RequiresSdkLevelAtLeastT(reason = "Mocks a PackageManager API not available on S-")
    public void testGetInstalledApplications_TPlus() {
        doReturn(ImmutableList.of(mApplicationInfo))
                .when(mPackageManagerMock)
                .getInstalledApplications(any(PackageManager.ApplicationInfoFlags.class));

        assertThat(PackageManagerCompatUtils.getInstalledApplications(mPackageManagerMock, 0))
                .isEqualTo(ImmutableList.of(mApplicationInfo));
        verify(mPackageManagerMock, never()).getInstalledApplications(anyInt());
        verify(mPackageManagerMock)
                .getInstalledApplications(any(PackageManager.ApplicationInfoFlags.class));
    }

    @Test
    @MockStatic(SdkLevel.class)
    public void testGetInstalledPackages_SMinus() {
        mocker.mockIsAtLeastT(false);
        doReturn(ImmutableList.of(mPackageInfo))
                .when(mPackageManagerMock)
                .getInstalledPackages(anyInt());

        final int flags = PackageManager.MATCH_APEX;
        assertThat(PackageManagerCompatUtils.getInstalledPackages(mPackageManagerMock, flags))
                .isEqualTo(ImmutableList.of(mPackageInfo));
        verify(mPackageManagerMock).getInstalledPackages(eq(flags));
    }

    @Test
    @RequiresSdkLevelAtLeastT(reason = "Mocks a PackageManager API not available on S-")
    public void testGetInstalledPackages_TPlus() {
        doReturn(ImmutableList.of(mPackageInfo))
                .when(mPackageManagerMock)
                .getInstalledPackages(any(PackageManager.PackageInfoFlags.class));

        assertThat(PackageManagerCompatUtils.getInstalledPackages(mPackageManagerMock, 0))
                .isEqualTo(ImmutableList.of(mPackageInfo));
        verify(mPackageManagerMock, never()).getInstalledPackages(anyInt());
        verify(mPackageManagerMock)
                .getInstalledPackages(any(PackageManager.PackageInfoFlags.class));
    }

    @Test
    @MockStatic(SdkLevel.class)
    public void testGetUidForPackage_SMinus() throws Exception {
        mocker.mockIsAtLeastT(false);
        final int packageUid = 100;
        doReturn(packageUid).when(mPackageManagerMock).getPackageUid(anyString(), anyInt());

        final int flags = PackageManager.MATCH_APEX;
        final String packageName = "com.example.package";
        assertThat(PackageManagerCompatUtils.getPackageUid(mPackageManagerMock, packageName, flags))
                .isEqualTo(packageUid);
        verify(mPackageManagerMock).getPackageUid(eq(packageName), eq(flags));
    }

    @Test
    @RequiresSdkLevelAtLeastT(reason = "Mocks a PackageManager API not available on S-")
    public void testGetUidForPackage_TPlus() throws Exception {
        final int packageUid = 100;
        doReturn(packageUid)
                .when(mPackageManagerMock)
                .getPackageUid(anyString(), any(PackageManager.PackageInfoFlags.class));

        final String packageName = "com.example.package";
        assertThat(PackageManagerCompatUtils.getPackageUid(mPackageManagerMock, packageName, 0))
                .isEqualTo(packageUid);
        verify(mPackageManagerMock, never()).getPackageUid(anyString(), anyInt());
        verify(mPackageManagerMock)
                .getPackageUid(eq(packageName), any(PackageManager.PackageInfoFlags.class));
    }

    @Test
    @MockStatic(SdkLevel.class)
    public void testGetApplicationInfo_SMinus() throws Exception {
        mocker.mockIsAtLeastT(false);
        doReturn(mApplicationInfo)
                .when(mPackageManagerMock)
                .getApplicationInfo(anyString(), anyInt());

        final int flags = PackageManager.MATCH_APEX;
        final String packageName = "com.example.package";
        assertThat(
                        PackageManagerCompatUtils.getApplicationInfo(
                                mPackageManagerMock, packageName, flags))
                .isEqualTo(mApplicationInfo);
        verify(mPackageManagerMock).getApplicationInfo(eq(packageName), eq(flags));
    }

    @Test
    @RequiresSdkLevelAtLeastT(reason = "Mocks a PackageManager API not available on S-")
    public void testGetApplicationInfo_TPlus() throws Exception {
        doReturn(mApplicationInfo)
                .when(mPackageManagerMock)
                .getApplicationInfo(anyString(), any(PackageManager.ApplicationInfoFlags.class));

        final String packageName = "com.example.package";
        ApplicationInfo info =
                PackageManagerCompatUtils.getApplicationInfo(mPackageManagerMock, packageName, 0);
        assertThat(info).isEqualTo(mApplicationInfo);
        verify(mPackageManagerMock, never()).getApplicationInfo(anyString(), anyInt());
        verify(mPackageManagerMock)
                .getApplicationInfo(
                        eq(packageName), any(PackageManager.ApplicationInfoFlags.class));
    }

    @Test
    public void testIsAdServicesActivityEnabled_adServicesPackage_defaultToEnabled() {
        when(mMockContext.getPackageManager()).thenReturn(mPackageManagerMock);
        when(mMockContext.getPackageName()).thenReturn(ADSERVICES_PACKAGE_NAME);
        boolean isActivityEnabled =
                PackageManagerCompatUtils.isAdServicesActivityEnabled(mMockContext);
        assertThat(isActivityEnabled).isTrue();
    }

    @Test
    public void testIsAdServicesActivityEnabled_nullPackageName_defaultToEnabled() {
        when(mMockContext.getPackageManager()).thenReturn(mPackageManagerMock);
        when(mMockContext.getPackageName()).thenReturn(null);
        boolean isActivityEnabled =
                PackageManagerCompatUtils.isAdServicesActivityEnabled(mMockContext);
        assertThat(isActivityEnabled).isFalse();
    }

    @Test
    public void testIsAdServicesActivityEnabled_extServicesPackage_enabled() throws Exception {
        when(mMockContext.getPackageManager()).thenReturn(mPackageManagerMock);
        when(mMockContext.getPackageName()).thenReturn(EXTSERVICES_PACKAGE_NAME);

        PackageInfo packageInfo = Mockito.spy(PackageInfo.class);
        packageInfo.packageName = EXTSERVICES_PACKAGE_NAME;
        when(mPackageManagerMock.getPackageInfo(eq(packageInfo.packageName), eq(0)))
                .thenReturn(packageInfo);
        when(mPackageManagerMock.getComponentEnabledSetting(any(ComponentName.class)))
                .thenReturn(PackageManager.COMPONENT_ENABLED_STATE_ENABLED);

        boolean isActivityEnabled =
                PackageManagerCompatUtils.isAdServicesActivityEnabled(mMockContext);

        assertThat(isActivityEnabled).isTrue();
        verify(mPackageManagerMock, times(7)).getComponentEnabledSetting(any(ComponentName.class));
    }

    @Test
    public void testIsAdServicesActivityEnabled_extServicesPackage_defaultToDisabled()
            throws Exception {
        when(mMockContext.getPackageManager()).thenReturn(mPackageManagerMock);
        when(mMockContext.getPackageName()).thenReturn(EXTSERVICES_PACKAGE_NAME);

        PackageInfo packageInfo = Mockito.spy(PackageInfo.class);
        packageInfo.packageName = EXTSERVICES_PACKAGE_NAME;
        when(mPackageManagerMock.getPackageInfo(eq(packageInfo.packageName), eq(0)))
                .thenReturn(packageInfo);
        when(mPackageManagerMock.getComponentEnabledSetting(any(ComponentName.class)))
                .thenReturn(PackageManager.COMPONENT_ENABLED_STATE_DEFAULT);

        boolean isActivityEnabled =
                PackageManagerCompatUtils.isAdServicesActivityEnabled(mMockContext);

        assertThat(isActivityEnabled).isFalse();
        verify(mPackageManagerMock).getComponentEnabledSetting(any(ComponentName.class));
    }

    @Test
    @ExpectErrorLogUtilCall(
            throwable = NameNotFoundException.class,
            errorCode = AD_SERVICES_ERROR_REPORTED__ERROR_CODE__PACKAGE_NAME_NOT_FOUND_EXCEPTION,
            ppapiName = AD_SERVICES_ERROR_REPORTED__PPAPI_NAME__COMMON)
    public void testIsAdServicesActivityEnabled_exception_disabled() throws Exception {
        when(mMockContext.getPackageManager()).thenReturn(mPackageManagerMock);
        when(mMockContext.getPackageName()).thenReturn(EXTSERVICES_PACKAGE_NAME);

        PackageInfo packageInfo = Mockito.spy(PackageInfo.class);
        packageInfo.packageName = EXTSERVICES_PACKAGE_NAME;
        when(mPackageManagerMock.getPackageInfo(eq(packageInfo.packageName), eq(0)))
                .thenThrow(new NameNotFoundException());

        boolean isActivityEnabled =
                PackageManagerCompatUtils.isAdServicesActivityEnabled(mMockContext);

        assertThat(isActivityEnabled).isFalse();
    }
}

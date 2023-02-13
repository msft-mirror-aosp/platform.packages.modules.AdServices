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

import static com.android.dx.mockito.inline.extended.ExtendedMockito.any;
import static com.android.dx.mockito.inline.extended.ExtendedMockito.anyInt;
import static com.android.dx.mockito.inline.extended.ExtendedMockito.anyString;
import static com.android.dx.mockito.inline.extended.ExtendedMockito.doReturn;
import static com.android.dx.mockito.inline.extended.ExtendedMockito.eq;
import static com.android.dx.mockito.inline.extended.ExtendedMockito.never;
import static com.android.dx.mockito.inline.extended.ExtendedMockito.verify;

import static com.google.common.truth.Truth.assertThat;

import static org.junit.Assert.assertThrows;

import android.content.pm.ApplicationInfo;
import android.content.pm.PackageManager;

import com.android.dx.mockito.inline.extended.ExtendedMockito;
import com.android.modules.utils.build.SdkLevel;

import org.junit.After;
import org.junit.Before;
import org.junit.Test;
import org.mockito.Mock;
import org.mockito.MockitoSession;

import java.util.ArrayList;

public class PackageManagerCompatUtilsTest {
    private MockitoSession mMockitoSession;

    @Mock private PackageManager mPackageManagerMock;

    @Before
    public void setUp() {
        mMockitoSession =
                ExtendedMockito.mockitoSession()
                        .mockStatic(SdkLevel.class)
                        .initMocks(this)
                        .startMocking();
    }

    @After
    public void tearDown() {
        mMockitoSession.finishMocking();
    }

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
    public void testGetInstalledApplications_SMinus() {
        doReturn(false).when(SdkLevel::isAtLeastT);
        doReturn(new ArrayList<>()).when(mPackageManagerMock).getInstalledApplications(anyInt());

        final int flags = PackageManager.MATCH_APEX;
        PackageManagerCompatUtils.getInstalledApplications(mPackageManagerMock, flags);
        verify(mPackageManagerMock).getInstalledApplications(eq(flags));
        verify(mPackageManagerMock, never())
                .getInstalledApplications(any(PackageManager.ApplicationInfoFlags.class));
    }

    @Test
    public void testGetInstalledApplications_TPlus() {
        doReturn(true).when(SdkLevel::isAtLeastT);
        doReturn(new ArrayList<>())
                .when(mPackageManagerMock)
                .getInstalledApplications(any(PackageManager.ApplicationInfoFlags.class));

        PackageManagerCompatUtils.getInstalledApplications(mPackageManagerMock, 0);
        verify(mPackageManagerMock, never()).getInstalledApplications(anyInt());
        verify(mPackageManagerMock)
                .getInstalledApplications(any(PackageManager.ApplicationInfoFlags.class));
    }

    @Test
    public void testGetInstalledPackages_SMinus() {
        doReturn(false).when(SdkLevel::isAtLeastT);
        doReturn(new ArrayList<>()).when(mPackageManagerMock).getInstalledPackages(anyInt());

        final int flags = PackageManager.MATCH_APEX;
        PackageManagerCompatUtils.getInstalledPackages(mPackageManagerMock, flags);
        verify(mPackageManagerMock).getInstalledPackages(eq(flags));
        verify(mPackageManagerMock, never())
                .getInstalledPackages(any(PackageManager.PackageInfoFlags.class));
    }

    @Test
    public void testGetInstalledPackages_TPlus() {
        doReturn(true).when(SdkLevel::isAtLeastT);
        doReturn(new ArrayList<>())
                .when(mPackageManagerMock)
                .getInstalledPackages(any(PackageManager.PackageInfoFlags.class));

        PackageManagerCompatUtils.getInstalledPackages(mPackageManagerMock, 0);
        verify(mPackageManagerMock, never()).getInstalledPackages(anyInt());
        verify(mPackageManagerMock)
                .getInstalledPackages(any(PackageManager.PackageInfoFlags.class));
    }

    @Test
    public void testGetUidForPackage_SMinus() throws PackageManager.NameNotFoundException {
        doReturn(false).when(SdkLevel::isAtLeastT);
        doReturn(100).when(mPackageManagerMock).getPackageUid(anyString(), anyInt());

        final int flags = PackageManager.MATCH_APEX;
        final String packageName = "com.example.package";
        PackageManagerCompatUtils.getPackageUid(mPackageManagerMock, packageName, flags);
        verify(mPackageManagerMock).getPackageUid(eq(packageName), eq(flags));
        verify(mPackageManagerMock, never())
                .getPackageUid(anyString(), any(PackageManager.PackageInfoFlags.class));
    }

    @Test
    public void testGetUidForPackage_TPlus() throws PackageManager.NameNotFoundException {
        doReturn(true).when(SdkLevel::isAtLeastT);
        doReturn(100)
                .when(mPackageManagerMock)
                .getPackageUid(anyString(), any(PackageManager.PackageInfoFlags.class));

        final String packageName = "com.example.package";
        PackageManagerCompatUtils.getPackageUid(mPackageManagerMock, packageName, 0);
        verify(mPackageManagerMock, never()).getPackageUid(anyString(), anyInt());
        verify(mPackageManagerMock)
                .getPackageUid(eq(packageName), any(PackageManager.PackageInfoFlags.class));
    }

    @Test
    public void testGetApplicationInfo_SMinus() throws PackageManager.NameNotFoundException {
        final ApplicationInfo applicationInfo = new ApplicationInfo();

        doReturn(false).when(SdkLevel::isAtLeastT);
        doReturn(applicationInfo)
                .when(mPackageManagerMock)
                .getApplicationInfo(anyString(), anyInt());

        final int flags = PackageManager.MATCH_APEX;
        final String packageName = "com.example.package";
        ApplicationInfo returned =
                PackageManagerCompatUtils.getApplicationInfo(
                        mPackageManagerMock, packageName, flags);
        assertThat(returned).isSameInstanceAs(applicationInfo);
        verify(mPackageManagerMock).getApplicationInfo(eq(packageName), eq(flags));
        verify(mPackageManagerMock, never())
                .getApplicationInfo(anyString(), any(PackageManager.ApplicationInfoFlags.class));
    }

    @Test
    public void testGetApplicationInfo_TPlus() throws PackageManager.NameNotFoundException {
        final ApplicationInfo applicationInfo = new ApplicationInfo();

        doReturn(true).when(SdkLevel::isAtLeastT);
        doReturn(applicationInfo)
                .when(mPackageManagerMock)
                .getApplicationInfo(anyString(), any(PackageManager.ApplicationInfoFlags.class));

        final String packageName = "com.example.package";
        ApplicationInfo info =
                PackageManagerCompatUtils.getApplicationInfo(mPackageManagerMock, packageName, 0);
        assertThat(info).isSameInstanceAs(applicationInfo);
        verify(mPackageManagerMock, never()).getApplicationInfo(anyString(), anyInt());
        verify(mPackageManagerMock)
                .getApplicationInfo(
                        eq(packageName), any(PackageManager.ApplicationInfoFlags.class));
    }
}

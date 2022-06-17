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
package com.android.adservices.service.topics;

import static com.google.common.truth.Truth.assertThat;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyInt;
import static org.mockito.Mockito.doReturn;
import static org.mockito.Mockito.doThrow;

import android.content.Context;
import android.content.pm.ApplicationInfo;
import android.content.pm.PackageManager;
import android.content.pm.PackageManager.NameNotFoundException;

import com.google.common.collect.ImmutableMap;
import com.google.common.collect.ImmutableSet;
import com.google.common.collect.Iterables;

import org.junit.Before;
import org.junit.Test;
import org.mockito.Mock;
import org.mockito.MockitoAnnotations;

/**
 * Unit tests for {@link com.android.adservices.service.topics.PackageManagerUtil}.
 */
public class PackageManagerUtilTest {
    private static final String TEST_PACKAGE_NAME = "SamplePackageName";
    private static final CharSequence TEST_APP_NAME = "Name for App";
    private static final CharSequence TEST_APP_DESCRIPTION = "Description for App";
    private PackageManagerUtil mPackageManagerUtil;

    @Mock
    private PackageManager mPackageManager;

    @Mock
    private ApplicationInfo mApplicationInfo;

    @Mock
    private Context mContext;

    @Before
    public void setup() {
        MockitoAnnotations.initMocks(this);
        doReturn(mPackageManager).when(mContext).getPackageManager();
        mPackageManagerUtil = new PackageManagerUtil(mContext);
    }

    @Test
    public void testGetAppInformationWithNameNotFoundException()
            throws Exception {
        // Simulate throwing NameNotFoundException when PackageManager calls getApplicationInfo.
        doThrow(new NameNotFoundException()).when(mPackageManager)
                .getApplicationInfo(any(String.class), anyInt());

        // Returned AppInfo instance should have empty strin as appName and empty string as
        // appDescription.
        ImmutableMap<String, AppInfo> appInfoMap = mPackageManagerUtil.getAppInformation(
                ImmutableSet.of(
                        TEST_PACKAGE_NAME));

        assertThat(appInfoMap).hasSize(1);
        AppInfo appInfo = Iterables.getOnlyElement(appInfoMap.values());
        assertThat(appInfo.getAppDescription()).isEmpty();
        assertThat(appInfo.getAppName()).isEmpty();
    }

    @Test
    public void testGetAppInformationWithNullApplicationInfo()
            throws Exception {
        // Return null when PackageManager calls getApplicationInfo.
        doReturn(null).when(mPackageManager).getApplicationInfo(any(String.class), anyInt());

        // Returned AppInfo instance should have empty string as appName and empty string as
        // appDescription.
        ImmutableMap<String, AppInfo> appInfoMap = mPackageManagerUtil.getAppInformation(
                ImmutableSet.of(
                        TEST_PACKAGE_NAME));

        assertThat(appInfoMap).hasSize(1);
        AppInfo appInfo = Iterables.getOnlyElement(appInfoMap.values());
        assertThat(appInfo.getAppDescription()).isEmpty();
        assertThat(appInfo.getAppName()).isEmpty();
    }

    @Test
    public void testGetAppInformation()
            throws Exception {
        // Return mocked ApplicationInfo when PackageManager calls getApplicationInfo.
        // Also return non-empty CharSequence for mocked PackageManager's getApplicationLabel call
        // and mocked ApplicationInfo's loadDescription call.
        doReturn(mApplicationInfo).when(mPackageManager).getApplicationInfo(any(String.class),
                anyInt());
        doReturn(TEST_APP_NAME).when(mPackageManager).getApplicationLabel(mApplicationInfo);
        doReturn(TEST_APP_DESCRIPTION).when(mApplicationInfo).loadDescription(mPackageManager);

        // Returned AppInfo instance should have appName and appDescription as the passed in
        // CharSequence.
        ImmutableMap<String, AppInfo> appInfoMap = mPackageManagerUtil.getAppInformation(
                ImmutableSet.of(
                        TEST_PACKAGE_NAME));

        assertThat(appInfoMap).hasSize(1);
        AppInfo appInfo = Iterables.getOnlyElement(appInfoMap.values());
        assertThat(appInfo.getAppDescription()).isEqualTo(TEST_APP_DESCRIPTION);
        assertThat(appInfo.getAppName()).isEqualTo(TEST_APP_NAME);
    }

    @Test
    public void testGetAppInformationWithNullAppDescription()
            throws Exception {
        // Return null for mocked ApplicationInfo's loadDescription call.
        doReturn(mApplicationInfo).when(mPackageManager).getApplicationInfo(any(String.class),
                anyInt());
        doReturn(TEST_APP_NAME).when(mPackageManager).getApplicationLabel(mApplicationInfo);
        doReturn(null).when(mApplicationInfo).loadDescription(mPackageManager);

        // Returned AppInfo instance should have empty string as appDescription and TEST_APP_NAME
        // as appName.
        ImmutableMap<String, AppInfo> appInfoMap = mPackageManagerUtil.getAppInformation(
                ImmutableSet.of(
                        TEST_PACKAGE_NAME));

        assertThat(appInfoMap).hasSize(1);
        AppInfo appInfo = Iterables.getOnlyElement(appInfoMap.values());
        assertThat(appInfo.getAppDescription()).isEmpty();
        assertThat(appInfo.getAppName()).isEqualTo(TEST_APP_NAME);
    }

    @Test
    public void testGetAppInformationWithNullAppName()
            throws Exception {
        // Return null for mocked PackageManager's getApplicationLabel call.
        doReturn(mApplicationInfo).when(mPackageManager).getApplicationInfo(any(String.class),
                anyInt());
        doReturn(null).when(mPackageManager).getApplicationLabel(mApplicationInfo);
        doReturn(TEST_APP_DESCRIPTION).when(mApplicationInfo).loadDescription(mPackageManager);

        // Returned AppInfo instance should have empty string as appName and TEST_APP_DESCRIPTION
        // as appDescription.
        ImmutableMap<String, AppInfo> appInfoMap = mPackageManagerUtil.getAppInformation(
                ImmutableSet.of(
                        TEST_PACKAGE_NAME));

        assertThat(appInfoMap).hasSize(1);
        AppInfo appInfo = Iterables.getOnlyElement(appInfoMap.values());
        assertThat(appInfo.getAppDescription()).isEqualTo(TEST_APP_DESCRIPTION);
        assertThat(appInfo.getAppName()).isEmpty();
    }

    @Test
    public void testGetAppInformation_multipleAppPackages()
            throws Exception {
        // Arrange mocks for a set of three app package names. Third call is for a package name
        // with null returned as description and app name.
        doReturn(mApplicationInfo).when(mPackageManager).getApplicationInfo(any(String.class),
                anyInt());
        doReturn("app-1", "app-2", null).when(mPackageManager).getApplicationLabel(
                mApplicationInfo);
        doReturn("desc-1", "desc-2", null).when(mApplicationInfo).loadDescription(mPackageManager);

        ImmutableMap<String, AppInfo> appInfoMap = mPackageManagerUtil.getAppInformation(
                ImmutableSet.of(
                        "test-1", "test-2", "test-3"));

        assertThat(appInfoMap).hasSize(3);
        assertThat(appInfoMap.get("test-1")).isEqualTo(new AppInfo("app-1", "desc-1"));
        assertThat(appInfoMap.get("test-2")).isEqualTo(new AppInfo("app-2", "desc-2"));
        // Verify empty strings for null response in app name and description.
        assertThat(appInfoMap.get("test-3")).isEqualTo(new AppInfo("", ""));
    }
}

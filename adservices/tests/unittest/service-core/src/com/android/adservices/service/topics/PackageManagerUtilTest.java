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

import static org.junit.Assert.assertEquals;
import static org.mockito.Matchers.anyInt;
import static org.mockito.Matchers.anyString;
import static org.mockito.Mockito.doReturn;
import static org.mockito.Mockito.doThrow;

import android.content.Context;
import android.content.pm.ApplicationInfo;
import android.content.pm.PackageManager;
import android.content.pm.PackageManager.NameNotFoundException;

import org.junit.Before;
import org.junit.Test;
import org.mockito.Mock;
import org.mockito.MockitoAnnotations;

/**
 * Unit tests for {@link com.android.adservices.service.topics.PackageManagerUtil}
 */
public class PackageManagerUtilTest {
    private static final CharSequence TEST_APP_NAME = "Name for App";
    private static final CharSequence TEST_APP_DESCRIPTION = "Description for App";
    private static final String EMPTY_STR = "";
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
        mPackageManagerUtil = new PackageManagerUtil(mContext, anyString());
    }

    @Test
    public void testGetAppInformationWithNameNotFoundException()
            throws Exception {
        // Simulate throwing NameNotFoundException when PackageManager calls getApplicationInfo
        doThrow(new NameNotFoundException()).when(mPackageManager)
            .getApplicationInfo(anyString(), anyInt());

        // Returned AppInfo instance should have empty string
        // as appName and empty string as appDescription
        AppInfo appInfo = mPackageManagerUtil.getAppInformation();
        assertThat(appInfo.getAppDescription()).isEmpty();
        assertThat(appInfo.getAppName()).isEmpty();
    }

    @Test
    public void testGetAppInformationWithNullApplicationInfo()
        throws Exception {
        // Return null when PackageManager calls getApplicationInfo
        doReturn(null).when(mPackageManager).getApplicationInfo(anyString(), anyInt());

        // Returned AppInfo instance should have empty string as
        // appName and empty string as appDescription
        AppInfo appInfo = mPackageManagerUtil.getAppInformation();
        assertThat(appInfo.getAppDescription()).isEmpty();
        assertThat(appInfo.getAppName()).isEmpty();
    }

    @Test
    public void testGetAppInformation()
        throws Exception {
        // Return mocked ApplicationInfo when PackageManager calls getApplicationInfo
        // Also return non-empty CharSequence for mocked PackageManager's getApplicationLabel call
        // and mocked ApplicationInfo's loadDescription call
        doReturn(mApplicationInfo).when(mPackageManager).getApplicationInfo(anyString(), anyInt());
        doReturn(TEST_APP_NAME).when(mPackageManager).getApplicationLabel(mApplicationInfo);
        doReturn(TEST_APP_DESCRIPTION).when(mApplicationInfo).loadDescription(mPackageManager);

        // Returned AppInfo instance should have appName and appDescription
        // as the passed in CharSequence
        AppInfo appInfo = mPackageManagerUtil.getAppInformation();
        assertEquals(TEST_APP_NAME.toString(), appInfo.getAppName());
        assertEquals(TEST_APP_DESCRIPTION.toString(), appInfo.getAppDescription());
    }

    @Test
    public void testGetAppInformationWithNullAppDescription()
        throws Exception {
        // Return null for mocked ApplicationInfo's loadDescription call
        doReturn(mApplicationInfo).when(mPackageManager).getApplicationInfo(anyString(), anyInt());
        doReturn(TEST_APP_NAME).when(mPackageManager).getApplicationLabel(mApplicationInfo);
        doReturn(null).when(mApplicationInfo).loadDescription(mPackageManager);

        // Returned AppInfo instance should have empty string as
        // appDescription and TEST_APP_NAME as appName
        AppInfo appInfo = mPackageManagerUtil.getAppInformation();
        assertEquals(TEST_APP_NAME.toString(), appInfo.getAppName());
        assertThat(appInfo.getAppDescription()).isEmpty();
    }

    @Test
    public void testGetAppInformationWithNullAppName()
        throws Exception {
        // Return null for mocked PackageManager's getApplicationLabel call
        doReturn(mApplicationInfo).when(mPackageManager).getApplicationInfo(anyString(), anyInt());
        doReturn(null).when(mPackageManager).getApplicationLabel(mApplicationInfo);
        doReturn(TEST_APP_DESCRIPTION).when(mApplicationInfo).loadDescription(mPackageManager);

        // Returned AppInfo instance should have empty string as appName
        // and TEST_APP_DESCRIPTION as appDescription
        AppInfo appInfo = mPackageManagerUtil.getAppInformation();
        assertThat(appInfo.getAppName()).isEmpty();
        assertEquals(TEST_APP_DESCRIPTION.toString(), appInfo.getAppDescription());
    }

}

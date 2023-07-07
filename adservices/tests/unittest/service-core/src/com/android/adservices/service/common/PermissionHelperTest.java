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

package com.android.adservices.service.common;

import static android.adservices.common.AdServicesPermissions.ACCESS_ADSERVICES_AD_ID;
import static android.adservices.common.AdServicesPermissions.ACCESS_ADSERVICES_ATTRIBUTION;
import static android.adservices.common.AdServicesPermissions.ACCESS_ADSERVICES_CUSTOM_AUDIENCE;
import static android.adservices.common.AdServicesPermissions.ACCESS_ADSERVICES_TOPICS;

import static com.google.common.truth.Truth.assertThat;

import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.doReturn;
import static org.mockito.Mockito.when;

import android.adservices.common.AdServicesPermissions;
import android.content.pm.PackageInfo;
import android.content.pm.PackageManager;
import android.os.Process;
import android.test.mock.MockContext;

import androidx.test.filters.SmallTest;

import com.android.modules.utils.build.SdkLevel;

import org.junit.Assume;
import org.junit.Before;
import org.junit.Test;
import org.mockito.Mock;
import org.mockito.MockitoAnnotations;

/** Unit tests for {@link com.android.adservices.service.common.PermissionHelper} */
@SmallTest
public class PermissionHelperTest {
    private static final String SDK_PACKAGE_NAME = "sdk_test_package_name";
    private static final String APP_PACKAGE_NAME = "app_test_package_name";
    private static final int APP_CALLING_UID = Process.myUid();
    private static final int SANDBOX_SDK_CALLING_UID = 25000;

    @Mock private PackageManager mMockPackageManagerGrant;
    @Mock private PackageManager mMockPackageManagerDeny;

    MockContext mMockContextGrant =
            new MockContext() {
                @Override
                public int checkCallingOrSelfPermission(String permission) {
                    return PackageManager.PERMISSION_GRANTED;
                }

                @Override
                public PackageManager getPackageManager() {
                    return mMockPackageManagerGrant;
                }
            };

    MockContext mMockContextDeny =
            new MockContext() {
                @Override
                public int checkCallingOrSelfPermission(String permission) {
                    return PackageManager.PERMISSION_DENIED;
                }

                @Override
                public PackageManager getPackageManager() {
                    return mMockPackageManagerDeny;
                }
            };

    @Before
    public void setup() throws Exception {
        MockitoAnnotations.initMocks(this);
        PackageInfo packageInfoGrant = new PackageInfo();
        packageInfoGrant.requestedPermissions =
                new String[] {ACCESS_ADSERVICES_AD_ID, ACCESS_ADSERVICES_ATTRIBUTION};
        doReturn(packageInfoGrant)
                .when(mMockPackageManagerGrant)
                .getPackageInfo(anyString(), eq(PackageManager.GET_PERMISSIONS));

        PackageInfo packageInfoDeny = new PackageInfo();
        packageInfoDeny.requestedPermissions = new String[0];
        doReturn(packageInfoDeny)
                .when(mMockPackageManagerDeny)
                .getPackageInfo(anyString(), eq(PackageManager.GET_PERMISSIONS));
    }

    @Test
    public void testHasPermission_notUseSandboxCheck() {
        assertThat(PermissionHelper.hasTopicsPermission(mMockContextGrant, APP_CALLING_UID))
                .isTrue();
        assertThat(
                        PermissionHelper.hasAdIdPermission(
                                mMockContextGrant, APP_PACKAGE_NAME, APP_CALLING_UID))
                .isTrue();
        assertThat(PermissionHelper.hasAttributionPermission(mMockContextGrant, APP_PACKAGE_NAME))
                .isTrue();
        assertThat(PermissionHelper.hasCustomAudiencesPermission(mMockContextGrant)).isTrue();
    }

    @Test
    public void testNotHasPermission() {
        assertThat(PermissionHelper.hasTopicsPermission(mMockContextDeny, APP_CALLING_UID))
                .isFalse();
        assertThat(
                        PermissionHelper.hasAdIdPermission(
                                mMockContextDeny, APP_PACKAGE_NAME, APP_CALLING_UID))
                .isFalse();
        assertThat(PermissionHelper.hasAttributionPermission(mMockContextDeny, APP_PACKAGE_NAME))
                .isFalse();
        assertThat(PermissionHelper.hasCustomAudiencesPermission(mMockContextDeny)).isFalse();
    }

    @Test
    public void testSdkHasPermission() {
        // Sandbox is only applicable for T+
        Assume.assumeTrue(SdkLevel.isAtLeastT());
        when(mMockPackageManagerGrant.checkPermission(ACCESS_ADSERVICES_TOPICS, SDK_PACKAGE_NAME))
                .thenReturn(PackageManager.PERMISSION_GRANTED);
        when(mMockPackageManagerGrant.checkPermission(ACCESS_ADSERVICES_AD_ID, SDK_PACKAGE_NAME))
                .thenReturn(PackageManager.PERMISSION_GRANTED);
        when(mMockPackageManagerGrant.checkPermission(
                        AdServicesPermissions.ACCESS_ADSERVICES_ATTRIBUTION, SDK_PACKAGE_NAME))
                .thenReturn(PackageManager.PERMISSION_GRANTED);
        when(mMockPackageManagerGrant.checkPermission(
                        ACCESS_ADSERVICES_CUSTOM_AUDIENCE, SDK_PACKAGE_NAME))
                .thenReturn(PackageManager.PERMISSION_GRANTED);

        assertThat(PermissionHelper.hasTopicsPermission(mMockContextGrant, SANDBOX_SDK_CALLING_UID))
                .isTrue();

        // TODO(b/240718367): Check Sdk permission for adid.
        // assertThat(PermissionHelper.hasAdIdPermission(mMockContextGrant, /*useSandboxCheck
        // =*/ true,

        // TODO(b/236267953): Check Sdk permission for Attribution.
        // assertThat(PermissionHelper.hasAttributionPermission(mMockContextGrant, /*useSandboxCheck
        // =*/ true,
        // SDK_PACKAGE_NAME)).isTrue();

        // TODO(b/236268316): Check Sdk permission for Custom Audiences.
        // assertThat(PermissionHelper.hasCustomAudiencesPermission(mMockContextGrant,
        // /*useSandboxCheck =*/ true,
        // SDK_PACKAGE_NAME)).isTrue();
    }

    @Test
    public void testSdkNotHasPermission() {
        // Sandbox is only applicable for T+
        Assume.assumeTrue(SdkLevel.isAtLeastT());
        when(mMockPackageManagerDeny.checkPermission(ACCESS_ADSERVICES_TOPICS, SDK_PACKAGE_NAME))
                .thenReturn(PackageManager.PERMISSION_DENIED);
        when(mMockPackageManagerDeny.checkPermission(ACCESS_ADSERVICES_AD_ID, SDK_PACKAGE_NAME))
                .thenReturn(PackageManager.PERMISSION_DENIED);
        when(mMockPackageManagerDeny.checkPermission(
                        AdServicesPermissions.ACCESS_ADSERVICES_ATTRIBUTION, SDK_PACKAGE_NAME))
                .thenReturn(PackageManager.PERMISSION_DENIED);
        when(mMockPackageManagerDeny.checkPermission(
                        ACCESS_ADSERVICES_CUSTOM_AUDIENCE, SDK_PACKAGE_NAME))
                .thenReturn(PackageManager.PERMISSION_DENIED);

        assertThat(PermissionHelper.hasTopicsPermission(mMockContextDeny, SANDBOX_SDK_CALLING_UID))
                .isFalse();

        // TODO(b/240718367): Check Sdk permission for Adid.
        // assertThat(PermissionHelper.hasAdIdPermission(mMockContextDeny, /*useSandboxCheck
        // =*/ true,
        // SDK_PACKAGE_NAME)).isFalse();

        // TODO(b/236267953): Check Sdk permission for Attribution.
        // assertThat(PermissionHelper.hasAttributionPermission(mMockContextDeny, /*useSandboxCheck
        // =*/ true,
        // SDK_PACKAGE_NAME)).isFalse();

        // TODO(b/236268316): Check Sdk permission for Custom Audiences.
        // assertThat(PermissionHelper.hasCustomAudiencesPermission(mMockContextDeny,
        // /*useSandboxCheck =*/ true,
        // SDK_PACKAGE_NAME)).isFalse();
    }
}

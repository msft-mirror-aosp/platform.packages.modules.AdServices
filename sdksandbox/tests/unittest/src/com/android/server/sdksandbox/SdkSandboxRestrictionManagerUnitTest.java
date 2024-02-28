/*
 * Copyright (C) 2024 The Android Open Source Project
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

package com.android.server.sdksandbox;

import static com.google.common.truth.Truth.assertThat;

import android.Manifest;
import android.content.Context;
import android.content.pm.ApplicationInfo;
import android.content.pm.SharedLibraryInfo;
import android.os.Build;
import android.os.Process;

import androidx.test.platform.app.InstrumentationRegistry;

import com.android.server.sdksandbox.helpers.PackageManagerHelper;

import org.junit.After;
import org.junit.Before;
import org.junit.Test;
import org.mockito.Mockito;

import java.util.ArrayList;

/** Unit tests for {@link SdkSandboxRestrictionManager}. */
public class SdkSandboxRestrictionManagerUnitTest {
    private static final String TEST_PACKAGE = "com.android.server.sdksandbox.tests";
    private static final int DEFAULT_TARGET_SDK_VERSION = Build.VERSION_CODES.UPSIDE_DOWN_CAKE;
    private SdkSandboxRestrictionManager mSdkSandboxRestrictionManager;
    private PackageManagerHelper mPackageManagerHelper;
    private SdkSandboxRestrictionManager.Injector mInjector;
    private CallingInfo mCallingInfo;

    @Before
    public void setUp() {
        Context context = InstrumentationRegistry.getInstrumentation().getContext();
        InstrumentationRegistry.getInstrumentation()
                .getUiAutomation()
                .adoptShellPermissionIdentity(Manifest.permission.INTERACT_ACROSS_USERS_FULL);

        int clientAppUid = Process.myUid();
        mCallingInfo = new CallingInfo(clientAppUid, TEST_PACKAGE);
        PackageManagerHelper packageManagerHelper = new PackageManagerHelper(context, clientAppUid);

        mPackageManagerHelper = Mockito.spy(packageManagerHelper);
        mInjector = Mockito.spy(new FakeInjector(context, mPackageManagerHelper));
        mSdkSandboxRestrictionManager = new SdkSandboxRestrictionManager(mInjector);
    }

    @After
    public void tearDown() {
        InstrumentationRegistry.getInstrumentation()
                .getUiAutomation()
                .dropShellPermissionIdentity();
    }

    @Test
    public void
            testGetEffectiveTargetSdkVersion_default_noAdSdk_currentSdkLevelLessThanDefaultValue()
                    throws Exception {
        Mockito.when(mInjector.getCurrentSdkLevel()).thenReturn(33);
        Mockito.when(mPackageManagerHelper.getSdkSharedLibraryInfo(TEST_PACKAGE))
                .thenReturn(new ArrayList<>());
        assertThat(mSdkSandboxRestrictionManager.getEffectiveTargetSdkVersion(mCallingInfo))
                .isEqualTo(DEFAULT_TARGET_SDK_VERSION);
    }

    @Test
    public void testGetEffectiveTargetSdkVersion_noAdSdk_currentSdkLevelSameAsDefaultValue()
            throws Exception {
        Mockito.when(mInjector.getCurrentSdkLevel()).thenReturn(34);
        Mockito.when(mPackageManagerHelper.getSdkSharedLibraryInfo(TEST_PACKAGE))
                .thenReturn(new ArrayList<>());
        assertThat(mSdkSandboxRestrictionManager.getEffectiveTargetSdkVersion(mCallingInfo))
                .isEqualTo(DEFAULT_TARGET_SDK_VERSION);
    }

    @Test
    public void testGetEffectiveTargetSdkVersion_noAdSdk_equalToCurrentSdkLevel() throws Exception {
        Mockito.when(mInjector.getCurrentSdkLevel()).thenReturn(35);
        Mockito.when(mPackageManagerHelper.getSdkSharedLibraryInfo(TEST_PACKAGE))
                .thenReturn(new ArrayList<>());
        assertThat(mSdkSandboxRestrictionManager.getEffectiveTargetSdkVersion(mCallingInfo))
                .isEqualTo(35);
    }

    @Test
    public void testGetEffectiveTargetSdkVersion_currentSdkVersionUsed() throws Exception {
        Mockito.when(mInjector.getCurrentSdkLevel()).thenReturn(34);
        ApplicationInfo applicationInfo = new ApplicationInfo();
        applicationInfo.targetSdkVersion = 35;

        Mockito.doReturn(applicationInfo, applicationInfo, applicationInfo)
                .when(mPackageManagerHelper)
                .getApplicationInfoForSharedLibrary(
                        Mockito.any(SharedLibraryInfo.class), Mockito.anyInt());

        assertThat(mSdkSandboxRestrictionManager.getEffectiveTargetSdkVersion(mCallingInfo))
                .isEqualTo(DEFAULT_TARGET_SDK_VERSION);
    }

    @Test
    public void testGetEffectiveTargetSdkVersion_withDifferentTargetSdkVersionForSdk()
            throws Exception {
        Mockito.when(mInjector.getCurrentSdkLevel()).thenReturn(36);

        ApplicationInfo applicationInfo1 = new ApplicationInfo();
        applicationInfo1.targetSdkVersion = 35;

        ApplicationInfo applicationInfo2 = new ApplicationInfo();
        applicationInfo2.targetSdkVersion = 36;

        ApplicationInfo applicationInfo3 = new ApplicationInfo();
        applicationInfo3.targetSdkVersion = 37;

        Mockito.doReturn(applicationInfo1, applicationInfo2, applicationInfo3)
                .when(mPackageManagerHelper)
                .getApplicationInfoForSharedLibrary(
                        Mockito.any(SharedLibraryInfo.class), Mockito.anyInt());

        assertThat(mSdkSandboxRestrictionManager.getEffectiveTargetSdkVersion(mCallingInfo))
                .isEqualTo(35);
    }

    static class FakeInjector extends SdkSandboxRestrictionManager.Injector {
        private PackageManagerHelper mPackageManagerHelper;

        FakeInjector(Context context, PackageManagerHelper packageManagerHelper) {
            super(context);
            mPackageManagerHelper = packageManagerHelper;
        }

        PackageManagerHelper getPackageManagerHelper(int callingUid) {
            return mPackageManagerHelper;
        }
    }
}

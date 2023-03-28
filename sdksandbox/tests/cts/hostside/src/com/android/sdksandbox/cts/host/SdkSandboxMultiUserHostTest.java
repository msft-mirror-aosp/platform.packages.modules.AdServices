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

package com.android.sdksandbox.cts.host;

import static com.google.common.truth.Truth.assertThat;

import static org.junit.Assume.assumeTrue;

import android.platform.test.annotations.LargeTest;

import com.android.tradefed.testtype.DeviceJUnit4ClassRunner;
import com.android.tradefed.testtype.junit4.BaseHostJUnit4Test;

import org.junit.After;
import org.junit.Before;
import org.junit.Test;
import org.junit.runner.RunWith;

/**
 * This test is necessary because using secondary_user option with installPackage will install the
 * package for all users, which does not allow us to test the case when the package is only
 * installed for the secondary user.
 */
@RunWith(DeviceJUnit4ClassRunner.class)
public final class SdkSandboxMultiUserHostTest extends BaseHostJUnit4Test {

    private static final String TEST_APP_PACKAGE_NAME = "com.android.sdksandbox.cts.app";
    private static final String TEST_APP_APK_NAME = "CtsSdkSandboxHostTestApp.apk";

    private int mSecondaryUserId;
    private int mInitialUserId;

    /**
     * Runs the given phase of a test by calling into the device. Throws an exception if the test
     * phase fails.
     *
     * <p>For example, <code>runPhase("testExample");</code>
     */
    private void runPhase(String phase) throws Exception {
        assertThat(
                        runDeviceTests(
                                TEST_APP_PACKAGE_NAME,
                                TEST_APP_PACKAGE_NAME + ".SdkSandboxMultiUserTestApp",
                                phase))
                .isTrue();
    }

    @Before
    public void setUp() throws Exception {
        uninstallPackage(TEST_APP_PACKAGE_NAME);
        mInitialUserId = getDevice().getCurrentUser();
        assumeTrue("Multiple user not supported", getDevice().isMultiUserSupported());
        mSecondaryUserId =
                getDevice().createUser(String.format("user-%d", System.currentTimeMillis()));
        getDevice().switchUser(mSecondaryUserId);
    }

    @After
    public void tearDown() throws Exception {
        uninstallPackage(TEST_APP_PACKAGE_NAME);
        getDevice().switchUser(mInitialUserId);
        getDevice().stopUser(mSecondaryUserId);
        getDevice().removeUser(mSecondaryUserId);
    }

    @Test
    @LargeTest
    public void testAppCanLoadSdkWhenInstalledOnlyForSecondaryUser() throws Exception {
        installPackageAsUser(TEST_APP_APK_NAME, /* grantPermission */ false, mSecondaryUserId);
        runPhase("testAppCanLoadSdk");
    }
}

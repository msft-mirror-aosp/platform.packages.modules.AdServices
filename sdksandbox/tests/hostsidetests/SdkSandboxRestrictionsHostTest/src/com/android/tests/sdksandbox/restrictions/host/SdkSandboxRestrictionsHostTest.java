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

package com.android.tests.sdksandbox.restrictions.host;

import static com.google.common.truth.Truth.assertThat;

import static org.junit.Assume.assumeTrue;

import android.app.sdksandbox.hosttestutils.AconfigFlagUtils;
import android.app.sdksandbox.hosttestutils.SdkSandboxDeviceSupportedHostRule;

import com.android.tradefed.testtype.DeviceJUnit4ClassRunner;
import com.android.tradefed.testtype.junit4.BaseHostJUnit4Test;

import com.google.common.truth.Expect;

import org.junit.Rule;
import org.junit.Test;
import org.junit.runner.RunWith;

@RunWith(DeviceJUnit4ClassRunner.class)
public class SdkSandboxRestrictionsHostTest extends BaseHostJUnit4Test {
    private static final String TEST_APP_RESTRICTIONS_PACKAGE =
            "com.android.tests.sdksandbox.restrictions";
    private static final String TEST_APP_RESTRICTIONS_APK = "SdkSandboxRestrictionsTestApp.apk";
    private final AconfigFlagUtils mAconfigFlagUtils = new AconfigFlagUtils(this);

    @Rule(order = 0)
    public final SdkSandboxDeviceSupportedHostRule deviceSupportRule =
            new SdkSandboxDeviceSupportedHostRule(this);

    @Rule(order = 1)
    public final Expect expect = Expect.create();

    /**
     * Runs the given phase of a test by calling into the device. Throws an exception if the test
     * phase fails.
     *
     * <p>For example, <code>runPhase("testExample");</code>
     */
    private void runPhase(String phase) throws Exception {
        assertThat(
                        runDeviceTests(
                                TEST_APP_RESTRICTIONS_PACKAGE,
                                TEST_APP_RESTRICTIONS_PACKAGE
                                        + "."
                                        + "SdkSandboxRestrictionsTestApp",
                                phase))
                .isTrue();
    }

    @Test
    public void testInvalidateETSV() throws Exception {
        assumeTrue(
                mAconfigFlagUtils.isFlagEnabled(
                        "com.android.adservices.flags."
                                + "sdksandbox_dump_effective_target_sdk_version"));
        assumeTrue(
                mAconfigFlagUtils.isFlagEnabled(
                        "com.android.adservices.flags."
                                + "sdksandbox_use_effective_target_sdk_version_for_restrictions"));

        installPackage(TEST_APP_RESTRICTIONS_APK);

        String currentUserId = getDevice().executeShellCommand("am get-current-user").trim();
        String uid =
                getDevice()
                        .executeShellCommand(
                                "pm list packages -U --user "
                                        + currentUserId
                                        + " "
                                        + TEST_APP_RESTRICTIONS_PACKAGE)
                        .split(":")[2]
                        .trim();

        runPhase("populateETSV");

        String processDump = getDevice().executeShellCommand("dumpsys sdk_sandbox");
        expect.that(processDump).contains(uid + ": 34");

        installPackage(TEST_APP_RESTRICTIONS_APK);
        processDump = getDevice().executeShellCommand("dumpsys sdk_sandbox");
        expect.that(processDump).doesNotContain(uid + ": 34");
    }
}

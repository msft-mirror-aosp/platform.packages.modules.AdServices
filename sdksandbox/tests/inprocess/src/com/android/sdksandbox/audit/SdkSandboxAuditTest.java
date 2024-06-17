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
package com.android.sdksandbox.audit;

import static com.google.common.truth.Truth.assertThat;

import android.app.sdksandbox.testutils.SdkSandboxDeviceSupportedRule;
import android.os.SELinux;
import android.platform.test.annotations.RequiresFlagsEnabled;
import android.platform.test.flag.junit.CheckFlagsRule;
import android.platform.test.flag.junit.DeviceFlagsValueProvider;

import com.android.sdksandbox.flags.Flags;

import org.junit.Rule;
import org.junit.Test;
import org.junit.runner.RunWith;
import org.junit.runners.JUnit4;

/** Tests to check some basic properties of the Sdk Sandbox audit process. */
@RunWith(JUnit4.class)
@RequiresFlagsEnabled(Flags.FLAG_SELINUX_SDK_SANDBOX_AUDIT)
public class SdkSandboxAuditTest {

    @Rule(order = 0)
    public final SdkSandboxDeviceSupportedRule supportedRule = new SdkSandboxDeviceSupportedRule();

    @Rule(order = 1)
    public final CheckFlagsRule mCheckFlagsRule = DeviceFlagsValueProvider.createCheckFlagsRule();

    @Test
    @RequiresFlagsEnabled(Flags.FLAG_SELINUX_INPUT_SELECTOR)
    public void testSdkSandboxAuditDomain() {
        assertThat(SELinux.getContext()).contains("u:r:sdk_sandbox_audit");
    }
}

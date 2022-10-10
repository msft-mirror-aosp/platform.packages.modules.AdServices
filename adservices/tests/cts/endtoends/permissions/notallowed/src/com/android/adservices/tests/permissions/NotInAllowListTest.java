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

package com.android.adservices.tests.permissions;

import static com.google.common.truth.Truth.assertThat;

import static org.junit.Assert.assertThrows;

import android.adservices.clients.topics.AdvertisingTopicsClient;
import android.content.Context;

import androidx.test.core.app.ApplicationProvider;

import com.android.compatibility.common.util.ShellUtils;

import org.junit.After;
import org.junit.Before;
import org.junit.Test;
import org.junit.runner.RunWith;
import org.junit.runners.JUnit4;

import java.util.concurrent.ExecutionException;
import java.util.concurrent.Executor;
import java.util.concurrent.Executors;

@RunWith(JUnit4.class)
// TODO: Add tests for measurement (b/238194122).
public class NotInAllowListTest {
    private static final Executor CALLBACK_EXECUTOR = Executors.newCachedThreadPool();
    private static final Context sContext = ApplicationProvider.getApplicationContext();
    private static final String CALLER_NOT_ALLOWED =
            "java.lang.SecurityException: Caller is not authorized to call this API. "
                    + "Caller is not allowed.";
    private static final String SIGNATURE_ALLOWLIST =
            "6cecc50e34ae31bfb5678986d6d6d3736c571ded2f2459527793e1f054eb0c9b,"
                    + "a40da80a59d170caa950cf15c18c454d47a39b26989d8b640ecd745ba71bf5dc,"
                    + "301aa3cb081134501c45f1422abc66c24224fd5ded5fdc8f17e697176fd866aa,"
                    + "c8a2e9bccf597c2fb6dc66bee293fc13f2fc47ec77bc6b2b0d52c11f51192ab8";

    @Before
    public void setup() {
        overrideDisableTopicsEnrollmentCheck("1");
        overrideConsentManagerDebugMode(true);
        overridingAdservicesLoggingLevel("VERBOSE");
        overrideAdservicesGlobalKillSwitch(true);
        overrideSignatureAllowListToEmpty(true);
    }

    @After
    public void teardown() {
        overrideDisableTopicsEnrollmentCheck("0");
        overrideConsentManagerDebugMode(false);
        overrideAdservicesGlobalKillSwitch(false);
        overrideSignatureAllowListToEmpty(false);
    }

    @Test
    public void testNotInAllowList() {
        AdvertisingTopicsClient advertisingTopicsClient1 =
                new AdvertisingTopicsClient.Builder()
                        .setContext(sContext)
                        .setSdkName("sdk1")
                        .setExecutor(CALLBACK_EXECUTOR)
                        .build();

        ExecutionException exception =
                assertThrows(
                        ExecutionException.class, () -> advertisingTopicsClient1.getTopics().get());
        assertThat(exception.getMessage()).isEqualTo(CALLER_NOT_ALLOWED);
    }

    private void overridingAdservicesLoggingLevel(String loggingLevel) {
        ShellUtils.runShellCommand("setprop log.tag.adservices %s", loggingLevel);
    }

    // Override the Consent Manager behaviour - Consent Given
    private void overrideConsentManagerDebugMode(boolean isGiven) {
        ShellUtils.runShellCommand(
                "setprop debug.adservices.consent_manager_debug_mode " + isGiven);
    }

    // Override global_kill_switch to ignore the effect of actual PH values.
    // If isOverride = true, override global_kill_switch to OFF to allow adservices
    // If isOverride = false, override global_kill_switch to meaningless value so that PhFlags will
    // use the default value.
    private void overrideAdservicesGlobalKillSwitch(boolean isOverride) {
        String overrideString = isOverride ? "false" : "null";
        ShellUtils.runShellCommand("setprop debug.adservices.global_kill_switch " + overrideString);
    }

    // Override the flag to disable Topics enrollment check.
    private void overrideDisableTopicsEnrollmentCheck(String val) {
        // Setting it to 1 here disables the Topics' enrollment check.
        ShellUtils.runShellCommand(
                "setprop debug.adservices.disable_topics_enrollment_check " + val);
    }

    // Override Signature Allow List to deny the signature of this test
    public void overrideSignatureAllowListToEmpty(boolean isEmpty) {
        String overrideString = isEmpty ? "empty" : SIGNATURE_ALLOWLIST;
        ShellUtils.runShellCommand(
                "device_config put adservices ppapi_app_signature_allow_list %s", overrideString);
    }
}

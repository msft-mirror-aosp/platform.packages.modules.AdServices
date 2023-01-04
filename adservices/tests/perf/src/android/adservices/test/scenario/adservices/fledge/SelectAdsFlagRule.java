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

package android.adservices.test.scenario.adservices.fledge;

import android.Manifest;

import androidx.test.platform.app.InstrumentationRegistry;

import com.android.compatibility.common.util.ShellUtils;

import org.junit.rules.TestRule;
import org.junit.runner.Description;
import org.junit.runners.model.Statement;

public class SelectAdsFlagRule implements TestRule {

    @Override
    public Statement apply(Statement base, Description description) {
        setupCommon();
        return new Statement() {
            @Override
            public void evaluate() throws Throwable {
                base.evaluate();
            }
        };
    }

    private void setupCommon() {
        InstrumentationRegistry.getInstrumentation()
                .getUiAutomation()
                .adoptShellPermissionIdentity(Manifest.permission.WRITE_DEVICE_CONFIG);

        ShellUtils.runShellCommand(
                "device_config put adservices fledge_ad_selection_bidding_timeout_per_ca_ms "
                        + "120000");
        ShellUtils.runShellCommand(
                "device_config put adservices fledge_ad_selection_scoring_timeout_ms 120000");
        ShellUtils.runShellCommand(
                "device_config put adservices fledge_ad_selection_overall_timeout_ms 120000");
        ShellUtils.runShellCommand(
                "device_config put adservices fledge_ad_selection_bidding_timeout_per_buyer_ms "
                        + "120000");
        ShellUtils.runShellCommand("su 0 killall -9 com.google.android.adservices.api");

        // TODO(b/260704277) : Remove/modify the temporary adb commands added to
        //  SelectAdsTestServerLatency
        ShellUtils.runShellCommand("setprop debug.adservices.disable_fledge_enrollment_check true");
        ShellUtils.runShellCommand("device_config put adservices global_kill_switch false");
        ShellUtils.runShellCommand(
                "device_config put adservices adservice_system_service_enabled true");
    }
}

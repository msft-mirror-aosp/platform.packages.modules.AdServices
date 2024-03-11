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

package com.android.adservices.common;

import android.os.Build;

import org.junit.AssumptionViolatedException;
import org.junit.rules.TestRule;
import org.junit.runner.Description;
import org.junit.runners.model.Statement;

import java.util.List;

/** Rule used to skip a test when it's not supported by a specific device */
public class DeviceExclusionRule implements TestRule {
    public static final String BARBET = "barbet";

    private final List<String> mDevicesToExclude;

    private DeviceExclusionRule(List<String> devicesToExclude) {
        this.mDevicesToExclude = devicesToExclude;
    }

    /** Get the device exclusion rule for the provided list of devices */
    public static DeviceExclusionRule forDevices(List<String> devicesToExclude) {
        return new DeviceExclusionRule(devicesToExclude);
    }

    @Override
    public Statement apply(Statement base, Description description) {
        return new Statement() {
            @Override
            public void evaluate() throws Throwable {
                for (String device : mDevicesToExclude) {
                    if (device.equalsIgnoreCase(Build.DEVICE)) {
                        throw new AssumptionViolatedException(
                                "Don't run Key Attestation tests on Barbet devices");
                    }
                }
                base.evaluate();
            }
        };
    }
}

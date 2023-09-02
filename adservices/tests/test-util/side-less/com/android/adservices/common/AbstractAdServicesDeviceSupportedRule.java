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
package com.android.adservices.common;

import com.android.adservices.common.Logger.RealLogger;

import org.junit.AssumptionViolatedException;
import org.junit.rules.TestRule;
import org.junit.runner.Description;
import org.junit.runners.model.Statement;

import java.util.Objects;

// NOTE: this class is used by device and host side, so it cannot have any Android dependency
/**
 * Rule used to properly check a test behavior depending on whether the device supports {@code
 * AdService}.
 *
 * <p>Typical usage:
 *
 * <pre class="prettyprint">
 * &#064;Rule
 * public final AdServicesDeviceSupportedRule adServicesDeviceSupportedRule =
 *     new AdServicesDeviceSupportedRule();
 * </pre>
 *
 * <p>In the example above, it assumes that every test should only be executed when the device
 * supports {@code AdServices} - if the device doesn't support it, the test will be skipped (with an
 * {@link AssumptionViolatedException}).
 *
 * <p>This rule can also be used to run tests only on devices that have {@link
 * android.content.pm.PackageManager#FEATURE_RAM_LOW low memory}, by annotating them with {@link
 * RequiresLowRamDevice}.
 *
 * <p>When used with another similar rules, you should organize them using the order of feature
 * dependency. For example, if the test also requires a given SDK level, you should check use that
 * rule first, as the device's SDK level is immutable (while whether or not {@code AdServices}
 * supports a device depends on the device). Example:
 *
 * <pre class="prettyprint">
 * &#064;Rule(order = 0)
 * public final SdkLevelSupportRule sdkLevelRule = SdkLevelSupportRule.forAtLeastS();
 *
 * &#064;Rule(order = 1)
 * public final AdServicesDeviceSupportedRule adServicesDeviceSupportedRule =
 *     new AdServicesDeviceSupportedRule();
 * </pre>
 *
 * <p><b>NOTE: </b>this class should NOT be used as {@code ClassRule}, as it would result in a "no
 * tests run" scenario if it throws a {@link AssumptionViolatedException}.
 */
public abstract class AbstractAdServicesDeviceSupportedRule implements TestRule {

    protected final Logger mLog;

    /** Default constructor. */
    public AbstractAdServicesDeviceSupportedRule(RealLogger logger) {
        mLog = new Logger(Objects.requireNonNull(logger), "AdServicesDeviceSupportedRule");
        mLog.d("Constructor: logger=%s", logger);
    }

    /** Checks whether {@code AdServices} is supported by the device. */
    public abstract boolean isAdServicesSupportedOnDevice() throws Exception;

    /** Checks whether the device has low ram. */
    public abstract boolean isLowRamDevice() throws Exception;

    @Override
    public Statement apply(Statement base, Description description) {
        if (!description.isTest()) {
            throw new IllegalStateException(
                    "This rule can only be applied to individual tests, it cannot be used as"
                            + " @ClassRule or in a test suite");
        }
        return new Statement() {
            @Override
            public void evaluate() throws Throwable {
                String testName = description.getDisplayName();
                boolean isDeviceSupported = isAdServicesSupportedOnDevice();
                boolean isLowRamDevice = isLowRamDevice();
                RequiresLowRamDevice requiresLowRamDevice =
                        description.getAnnotation(RequiresLowRamDevice.class);
                mLog.d(
                        "apply(): testName=%s, isDeviceSupported=%b, isLowRamDevice=%b,"
                                + " requiresLowRamDevice=%s",
                        testName, isDeviceSupported, isLowRamDevice, requiresLowRamDevice);

                if (!isDeviceSupported && requiresLowRamDevice == null) {
                    // Low-ram devices is a sub-set of unsupported, hence we cannot skip it right
                    // away as the test might be annotated with @RequiresLowRamDevice (which is
                    // checked below)
                    throw new AssumptionViolatedException("Device doesn't support Adservices");
                }
                if (!isLowRamDevice && requiresLowRamDevice != null) {
                    throw new AssumptionViolatedException(
                            "Test annotated with @RequiresLowRamDevice and device is not");
                }

                base.evaluate();
            }
        };
    }
}

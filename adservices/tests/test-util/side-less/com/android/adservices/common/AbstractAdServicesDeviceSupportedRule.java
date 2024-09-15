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

import com.android.adservices.common.annotations.RequiresAndroidServiceAvailable;
import com.android.adservices.shared.testing.AndroidDevicePropertiesHelper;
import com.android.adservices.shared.testing.DeviceConditionsViolatedException;
import com.android.adservices.shared.testing.Logger;
import com.android.adservices.shared.testing.Logger.RealLogger;
import com.android.adservices.shared.testing.Nullable;
import com.android.adservices.shared.testing.ScreenSize;
import com.android.adservices.shared.testing.annotations.RequiresGoDevice;
import com.android.adservices.shared.testing.annotations.RequiresLowRamDevice;
import com.android.adservices.shared.testing.annotations.RequiresScreenSizeDevice;

import com.google.common.annotations.VisibleForTesting;

import org.junit.AssumptionViolatedException;
import org.junit.rules.TestRule;
import org.junit.runner.Description;
import org.junit.runners.model.Statement;

import java.lang.annotation.Annotation;
import java.util.ArrayList;
import java.util.List;
import java.util.Locale;
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
 * <p>This rule can also be used to run tests only on devices that have certain screen size, by
 * annotating them with {@link RequiresScreenSizeDevice}.
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
    private final AbstractDeviceSupportHelper mDeviceSupportHelper;

    @VisibleForTesting
    static final String REQUIRES_LOW_RAM_ASSUMPTION_FAILED_ERROR_MESSAGE =
            "Test annotated with @RequiresLowRamDevice and device is not.";

    @VisibleForTesting
    static final String REQUIRES_SCREEN_SIZE_ASSUMPTION_FAILED_ERROR_MESSAGE =
            "Test annotated with @RequiresScreenSizeDevice(size=%s) and device is not.";

    @VisibleForTesting
    static final String REQUIRES_GO_DEVICE_ASSUMPTION_FAILED_ERROR_MESSAGE =
            "Test annotated with @RequiresGoDevice and device is not a Go device.";

    @VisibleForTesting
    static final String REQUIRES_ANDROID_SERVICE_ASSUMPTION_FAILED_ERROR_MSG =
            "Test annotated with @RequiresAndroidServiceAvailable and device doesn't have the"
                    + " android service %s";

    /** Default constructor. */
    public AbstractAdServicesDeviceSupportedRule(
            RealLogger logger, AbstractDeviceSupportHelper deviceSupportHelper) {
        mLog = new Logger(Objects.requireNonNull(logger), getClass());
        mDeviceSupportHelper = Objects.requireNonNull(deviceSupportHelper);
        mLog.d("Constructor: logger=%s", logger);
    }

    /** Checks whether {@code AdServices} is supported by the device. */
    public final boolean isAdServicesSupportedOnDevice() {
        boolean isSupported = mDeviceSupportHelper.isDeviceSupported();
        mLog.v("isAdServicesSupportedOnDevice(): %b", isSupported);
        return isSupported;
    }

    /** Checks whether the device has low ram. */
    public final boolean isLowRamDevice() {
        boolean isLowRamDevice = mDeviceSupportHelper.isLowRamDevice();
        mLog.v("isLowRamDevice(): %b", isLowRamDevice);
        return isLowRamDevice;
    }

    /** Checks whether the device has large screen. */
    public final boolean isLargeScreenDevice() {
        boolean isLargeScreenDevice = mDeviceSupportHelper.isLargeScreenDevice();
        mLog.v("isLargeScreenDevice(): %b", isLargeScreenDevice);
        return isLargeScreenDevice;
    }

    /** Checks whether the device is a go device. */
    public final boolean isGoDevice() {
        boolean isGoDevice = mDeviceSupportHelper.isGoDevice();
        mLog.v("isGoDevice(): %b", isGoDevice);
        return isGoDevice;
    }

    /**
     * Check whether the device has a service.
     *
     * @return {@code true} when it has and only has one service.
     */
    public final boolean isAndroidServiceAvailable(String intentAction) {
        boolean isAndroidServiceAvailable =
                mDeviceSupportHelper.isAndroidServiceAvailable(intentAction);
        mLog.v(
                "isAndroidServiceAvailable() for Intent action %s: %b",
                intentAction, isAndroidServiceAvailable);
        return isAndroidServiceAvailable;
    }

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
                boolean isLargeScreenDevice = isLargeScreenDevice();
                boolean isGoDevice = isGoDevice();
                RequiresLowRamDevice requiresLowRamDevice =
                        description.getAnnotation(RequiresLowRamDevice.class);
                ScreenSize requiresScreenDevice = getRequiresScreenDevice(description);
                RequiresGoDevice requiresGoDevice =
                        description.getAnnotation(RequiresGoDevice.class);
                RequiresAndroidServiceAvailable requiresAndroidServiceAvailable =
                        getRequiresAndroidServiceAvailable(description);

                mLog.d(
                        "apply(): testName=%s, isDeviceSupported=%b, isLowRamDevice=%b,"
                                + " requiresLowRamDevice=%s, requiresAndroidServiceAvailable=%s",
                        testName,
                        isDeviceSupported,
                        isLowRamDevice,
                        requiresLowRamDevice,
                        requiresAndroidServiceAvailable);
                List<String> assumptionViolatedReasons = new ArrayList<>();

                if (!isDeviceSupported
                        && requiresLowRamDevice == null
                        && requiresGoDevice == null
                        && requiresScreenDevice == null) {
                    // Low-ram devices is a sub-set of unsupported, hence we cannot skip it right
                    // away as the test might be annotated with @RequiresLowRamDevice (which is
                    // checked below)
                    assumptionViolatedReasons.add("Device doesn't support Adservices");
                } else {
                    if (!isLowRamDevice && requiresLowRamDevice != null) {
                        assumptionViolatedReasons.add(
                                REQUIRES_LOW_RAM_ASSUMPTION_FAILED_ERROR_MESSAGE);
                    }
                    if (!isGoDevice && requiresGoDevice != null) {
                        assumptionViolatedReasons.add(
                                REQUIRES_GO_DEVICE_ASSUMPTION_FAILED_ERROR_MESSAGE);
                    }
                    if (requiresScreenDevice != null
                            && !AndroidDevicePropertiesHelper.matchScreenSize(
                                    requiresScreenDevice, isLargeScreenDevice)) {
                        assumptionViolatedReasons.add(
                                String.format(
                                        REQUIRES_SCREEN_SIZE_ASSUMPTION_FAILED_ERROR_MESSAGE,
                                        requiresScreenDevice));
                    }
                    if (requiresAndroidServiceAvailable != null) {
                        String intentAction = requiresAndroidServiceAvailable.intentAction();
                        if (!isAndroidServiceAvailable(intentAction)) {
                            assumptionViolatedReasons.add(
                                    String.format(
                                            Locale.ENGLISH,
                                            REQUIRES_ANDROID_SERVICE_ASSUMPTION_FAILED_ERROR_MSG,
                                            intentAction));
                        }
                    }
                }

                // Throw exception in case any of the assumption was violated.
                if (!assumptionViolatedReasons.isEmpty()) {
                    throw new DeviceConditionsViolatedException(assumptionViolatedReasons);
                }

                base.evaluate();
            }
        };
    }

    @Nullable
    private ScreenSize getRequiresScreenDevice(Description description) {
        RequiresScreenSizeDevice requiresLargeScreenDevice =
                description.getAnnotation(RequiresScreenSizeDevice.class);
        if (requiresLargeScreenDevice != null) {
            return requiresLargeScreenDevice.value();
        }
        return null;
    }

    // Check both class and the method for the annotation RequiresAndroidServiceAvailable, while the
    // method's annotation prevails.
    @Nullable
    private RequiresAndroidServiceAvailable getRequiresAndroidServiceAvailable(
            Description description) {
        Annotation[] annotations = description.getTestClass().getAnnotations();

        RequiresAndroidServiceAvailable classAnnotation = null;
        for (Annotation annotation : annotations) {
            if (annotation instanceof RequiresAndroidServiceAvailable) {
                classAnnotation = (RequiresAndroidServiceAvailable) annotation;
                break;
            }
        }

        RequiresAndroidServiceAvailable methodAnnotation =
                description.getAnnotation(RequiresAndroidServiceAvailable.class);

        if (methodAnnotation == null) {
            return classAnnotation;
        }

        return methodAnnotation;
    }
}

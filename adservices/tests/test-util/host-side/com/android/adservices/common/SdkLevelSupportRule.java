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

import com.android.tradefed.device.DeviceNotAvailableException;
import com.android.tradefed.device.ITestDevice;

/** See {@link AbstractSdkLevelSupportedRule}. */
public final class SdkLevelSupportRule extends AbstractSdkLevelSupportedRule {

    private SdkLevelSupportRule(AndroidSdkLevel level) {
        super(ConsoleLogger.getInstance(), level);
    }

    public void setDevice(ITestDevice device) {
        TestDeviceHelper.setTestDevice(device);
    }

    /**
     * Gets a rule that don't skip any test by default.
     *
     * <p>This rule is typically used when:
     * <ul>
     *   <li>Only a few tests require a specific SDK release - such tests will be annotated with a
     *   <code>@RequiresSdkLevel...</code> annotation.
     *   <li>Some test methods (typically {@code &#064;Before}) needs to check for the SDK release inside
     *   them - these tests call call rule methods such as {@code isAtLeastS()}.
     * </ul>
     */
    public static SdkLevelSupportRule forAnyLevel() {
        return new SdkLevelSupportRule(AndroidSdkLevel.ANY);
    }

    /**
     * Gets a rule that ensures tests are only executed on Android S+ and skipped otherwise, by
     * default (if the test have other SDK restrictions, the test can be annotated with extra 
     * {@code &#064;RequiresSdkLevel...} annotations)
     */
    public static SdkLevelSupportRule forAtLeastS() {
        return new SdkLevelSupportRule(AndroidSdkLevel.S);
    }

    /**
     * Gets a rule that ensures tests are only executed on Android T+ and skipped otherwise, by
     * default (if the test have other SDK restrictions, the test can be annotated with extra
     * {@code &#064;RequiresSdkLevel...} annotations)
     */
    public static SdkLevelSupportRule forAtLeastT() {
        return new SdkLevelSupportRule(AndroidSdkLevel.T);
    }

    /**
     * Gets a rule that ensures tests are only executed on Android U+ and skipped otherwise, by
     * default (if the test have other SDK restrictions, the test can be annotated with extra
     * {@code &#064;RequiresSdkLevel...} annotations)
     */
    public static SdkLevelSupportRule forAtLeastU() {
        return new SdkLevelSupportRule(AndroidSdkLevel.U);
    }

    @Override
    public boolean isAtLeastR() throws DeviceNotAvailableException {
        return isDeviceApiLevelAtLeast(AndroidSdkLevel.R);
    }

    @Override
    public boolean isAtLeastS() throws DeviceNotAvailableException {
        return isDeviceApiLevelAtLeast(AndroidSdkLevel.S);
    }

    @Override
    public boolean isAtLeastT() throws DeviceNotAvailableException {
        return isDeviceApiLevelAtLeast(AndroidSdkLevel.T);
    }

    @Override
    public boolean isAtLeastU() throws DeviceNotAvailableException {
        return isDeviceApiLevelAtLeast(AndroidSdkLevel.U);
    }

    private boolean isDeviceApiLevelAtLeast(AndroidSdkLevel level)
            throws DeviceNotAvailableException {
        return TestDeviceHelper.getTestDevice().getApiLevel() >= level.getLevel();
    }
}

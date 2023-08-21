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

import com.android.modules.utils.build.SdkLevel;

/**
 * Device-side version of {@link AbstractSdkLevelSupportedRule}.
 *
 * <p>See {@link AbstractSdkLevelSupportedRule} for usage and examples.
 */
public final class SdkLevelSupportRule extends AbstractSdkLevelSupportedRule {

    private SdkLevelSupportRule(AndroidSdkLevel level) {
        super(AndroidLogger.getInstance(), level);
    }

    /**
     * Gets a rule that ensures test is executed on every Android version, unless the test is
     * explicitly annotated with a {@code RequiresSdkLevel...} annotation.
     */
    public static SdkLevelSupportRule forAnyLevel() {
        return new SdkLevelSupportRule(AndroidSdkLevel.ANY);
    }

    /** Gets a rule that ensures test is executed on Android R+. Skips test otherwise. */
    public static SdkLevelSupportRule forAtLeastR() {
        return new SdkLevelSupportRule(AndroidSdkLevel.R);
    }

    /** Gets a rule that ensures test is executed on Android S+. Skips test otherwise. */
    public static SdkLevelSupportRule forAtLeastS() {
        return new SdkLevelSupportRule(AndroidSdkLevel.S);
    }

    /** Gets a rule that ensures test is executed on Android S+. Skips test otherwise. */
    public static SdkLevelSupportRule forAtLeastS_V2() {
        return new SdkLevelSupportRule(AndroidSdkLevel.S_V2);
    }

    /** Gets a rule that ensures test is executed on Android T+. Skips test otherwise. */
    public static SdkLevelSupportRule forAtLeastT() {
        return new SdkLevelSupportRule(AndroidSdkLevel.T);
    }

    /** Gets a rule that ensures test is executed on Android U+. Skips test otherwise. */
    public static SdkLevelSupportRule forAtLeastU() {
        return new SdkLevelSupportRule(AndroidSdkLevel.U);
    }

    /** Gets a rule that ensures test is executed on Android V+. Skips test otherwise. */
    public static SdkLevelSupportRule forAtLeastV() {
        return new SdkLevelSupportRule(AndroidSdkLevel.V);
    }

    @Override
    public boolean isDeviceAtLeastR() {
        return SdkLevel.isAtLeastR();
    }

    @Override
    public boolean isDeviceAtLeastS() {
        return SdkLevel.isAtLeastS();
    }

    @Override
    public boolean isDeviceAtLeastS_V2() {
        return SdkLevel.isAtLeastSv2();
    }

    @Override
    public boolean isDeviceAtLeastT() {
        return SdkLevel.isAtLeastT();
    }

    @Override
    public boolean isDeviceAtLeastU() {
        return SdkLevel.isAtLeastU();
    }

    @Override
    public boolean isDeviceAtLeastV() {
        return SdkLevel.isAtLeastV();
    }
}

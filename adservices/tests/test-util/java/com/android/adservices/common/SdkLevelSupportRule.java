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

    private static final AndroidLogger sLogger = new AndroidLogger(SdkLevelSupportRule.class);

    private SdkLevelSupportRule(AndroidSdkLevel level) {
        super(sLogger, level);
    }

    /** Rule that ensures test is executed on Android R+. Skips test otherwise. */
    public static SdkLevelSupportRule isAtLeastR() {
        return new SdkLevelSupportRule(AndroidSdkLevel.R);
    }

    /** Rule that ensures test is executed on Android S+. Skips test otherwise. */
    public static SdkLevelSupportRule isAtLeastS() {
        return new SdkLevelSupportRule(AndroidSdkLevel.S);
    }

    /** Rule that ensures test is executed on Android S+. Skips test otherwise. */
    public static SdkLevelSupportRule isAtLeastS_V2() {
        return new SdkLevelSupportRule(AndroidSdkLevel.S_V2);
    }

    /** Rule that ensures test is executed on Android T+. Skips test otherwise. */
    public static SdkLevelSupportRule isAtLeastT() {
        return new SdkLevelSupportRule(AndroidSdkLevel.T);
    }

    /** Rule that ensures test is executed on Android U+. Skips test otherwise. */
    public static SdkLevelSupportRule isAtLeastU() {
        return new SdkLevelSupportRule(AndroidSdkLevel.U);
    }

    /** Rule that ensures test is executed on Android V+. Skips test otherwise. */
    public static SdkLevelSupportRule isAtLeastV() {
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

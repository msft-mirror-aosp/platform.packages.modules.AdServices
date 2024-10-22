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

import com.android.adservices.shared.testing.SdkSandbox;
import com.android.adservices.shared.testing.device.DeviceConfig;
import com.android.adservices.shared.testing.flags.DeviceSideFlagsPreparerClassRule;

import com.google.common.annotations.VisibleForTesting;

/**
 * Adservices-specific, device-side implementation of {@link
 * com.android.adservices.shared.testing.flags.AbstractFlagsPreparerClassRule}.
 *
 * <p>See {@link com.android.adservices.shared.testing.flags.AbstractFlagsPreparerClassRule} for
 * actual documentation.
 */
public final class AdServicesFlagsPreparerClassRule
        extends DeviceSideFlagsPreparerClassRule<AdServicesFlagsPreparerClassRule> {

    /** Default constructor, uses default {@link DeviceConfig} and {@link SdkSandbox}. */
    public AdServicesFlagsPreparerClassRule() {
        super();
    }

    @VisibleForTesting
    AdServicesFlagsPreparerClassRule(SdkSandbox sdkSandbox, DeviceConfig deviceConfig) {
        super(sdkSandbox, deviceConfig);
    }
}

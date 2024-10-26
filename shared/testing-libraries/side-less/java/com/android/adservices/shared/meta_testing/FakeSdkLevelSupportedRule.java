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
package com.android.adservices.shared.meta_testing;

import com.android.adservices.shared.testing.AbstractSdkLevelSupportedRule;
import com.android.adservices.shared.testing.AndroidSdk.Level;
import com.android.adservices.shared.testing.AndroidSdk.Range;
import com.android.adservices.shared.testing.Logger.RealLogger;

import java.util.Objects;

public final class FakeSdkLevelSupportedRule extends AbstractSdkLevelSupportedRule {

    private final FakeDeviceGateway mFakeDeviceGateway;

    public FakeSdkLevelSupportedRule(FakeDeviceGateway deviceGateway) {
        this(new FakeRealLogger(), deviceGateway);
    }

    public FakeSdkLevelSupportedRule(RealLogger logger, FakeDeviceGateway deviceGateway) {
        super(logger);
        mFakeDeviceGateway = Objects.requireNonNull(deviceGateway, "deviceGateway cannot be null");
    }

    public FakeSdkLevelSupportedRule(
            RealLogger logger, FakeDeviceGateway deviceGateway, Range defaultRange) {
        super(logger, defaultRange);
        mFakeDeviceGateway = Objects.requireNonNull(deviceGateway, "deviceGateway cannot be null");
    }

    @Override
    public Level getRawDeviceApiLevel() {
        return mFakeDeviceGateway.getSdkLevel();
    }
}

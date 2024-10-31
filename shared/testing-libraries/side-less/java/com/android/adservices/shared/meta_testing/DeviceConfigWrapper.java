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

import com.android.adservices.shared.testing.Logger;
import com.android.adservices.shared.testing.device.DeviceConfig;
import com.android.adservices.shared.testing.device.DeviceConfig.SyncDisabledModeForTest;

/** Wrapper for real {@link DeviceConfig} implementations. */
public class DeviceConfigWrapper extends Wrapper<DeviceConfig> implements DeviceConfig {

    /** Default constructor, sets logger. */
    public DeviceConfigWrapper() {
        super();
    }

    /** Constructor with a custom logger. */
    public DeviceConfigWrapper(Logger logger) {
        super(logger);
    }

    @Override
    public DeviceConfigWrapper setSyncDisabledMode(SyncDisabledModeForTest mode) {
        mLog.v("setSyncDisabledMode(%s)", mode);
        getWrapped().setSyncDisabledMode(mode);
        return this;
    }

    @Override
    public SyncDisabledModeForTest getSyncDisabledMode() {
        var mode = getWrapped().getSyncDisabledMode();
        mLog.v("getSyncDisabledMode(): returning %s", mode);
        return mode;
    }
}

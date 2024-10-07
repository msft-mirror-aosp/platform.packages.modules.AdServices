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
package com.android.adservices.shared.testing.device;

import java.util.Locale;

/** Side-agnostic abstraction to interact with the {@code DeviceConfig}. */
public interface DeviceConfig {

    /** Sets the synchronization mode. */
    void setSyncDisabledMode(SyncDisabledModeForTest mode);

    /** Gets the synchronization mode. */
    SyncDisabledModeForTest getSyncDisabledMode();

    /* Synchronization mode */
    enum SyncDisabledModeForTest {
        UNSUPPORTED,
        NONE,
        PERSISTENT,
        UNTIL_REBOOT;

        /** Gets the value of the mode used on {@code cmd device_config} parameters. */
        String getShellCommandString() {
            return toString().toLowerCase(Locale.ENGLISH);
        }
    }
}

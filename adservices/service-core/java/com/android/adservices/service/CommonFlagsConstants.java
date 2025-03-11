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
package com.android.adservices.service;

/**
 * Defines constants used by {@code CommonFlags}.
 *
 * <p><b>NOTE: </b>cannot have any dependency on Android or other AdServices code.
 *
 * @hide
 */
public final class CommonFlagsConstants {

    // AdServices Namespace String from DeviceConfig class is not available in S Minus
    public static final String NAMESPACE_ADSERVICES = "adservices";

    private CommonFlagsConstants() {
        throw new UnsupportedOperationException("Contains only static constants");
    }

    // Whether AdServices system service is enabled
    public static final String KEY_ADSERVICES_SYSTEM_SERVICE_ENABLED =
            "adservice_system_service_enabled";
}

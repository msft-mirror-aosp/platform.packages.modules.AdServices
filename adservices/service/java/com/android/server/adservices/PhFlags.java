/*
 * Copyright (C) 2022 The Android Open Source Project
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
package com.android.server.adservices;

import android.annotation.NonNull;
import android.provider.DeviceConfig;

/**
 * Flags Implementation that delegates to DeviceConfig.
 *
 * @hide
 */
public final class PhFlags implements Flags {

    private static final PhFlags sSingleton = new PhFlags();

    /** Returns the singleton instance of the PhFlags. */
    @NonNull
    static PhFlags getInstance() {
        return sSingleton;
    }

    /*
     * Keys for ALL the flags stored in DeviceConfig.
     */
    // Adservices System Service enable status keys.
    static final String KEY_ADSERVICES_SYSTEM_SERVICE_ENABLED = "adservice_system_service_enabled";

    static final String KEY_CLIENT_ERROR_LOGGING__ENABLE_CEL_FOR_SYSTEM_SERVER =
            "ClientErrorLogging__enable_cel_for_system_server";

    /** Key to enable AtomicFileDataStore update API for adservices system service. */
    public static final String KEY_ENABLE_BATCH_UPDATE_API_IN_SYSTEM_SERVER =
            "AtomicFileDatastore__enable_batch_update_api_in_system_server";

    @Override
    public boolean getAdServicesSystemServiceEnabled() {
        return getFlag(KEY_ADSERVICES_SYSTEM_SERVICE_ENABLED, ADSERVICES_SYSTEM_SERVICE_ENABLED);
    }

    @Override
    public boolean getEnableCelForSystemServer() {
        return getFlag(
                KEY_CLIENT_ERROR_LOGGING__ENABLE_CEL_FOR_SYSTEM_SERVER,
                CLIENT_ERROR_LOGGING__ENABLE_CEL_FOR_SYSTEM_SERVER);
    }

    @Override
    public boolean getEnableAtomicFileDatastoreBatchUpdateApiInSystemServer() {
        return getFlag(
                KEY_ENABLE_BATCH_UPDATE_API_IN_SYSTEM_SERVER,
                ENABLE_ATOMIC_FILE_DATASTORE_BATCH_UPDATE_API_IN_SYSTEM_SERVER);
    }

    @SuppressWarnings("AvoidDeviceConfigUsage") // Helper / infra method
    private boolean getFlag(String name, boolean defaultValue) {
        return DeviceConfig.getBoolean(DeviceConfig.NAMESPACE_ADSERVICES, name, defaultValue);
    }
}

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

/**
 * AdServices System Service Feature Flags interface. This Flags interface hold the default values
 * of AdServices System Service Flags.
 *
 * @hide
 */
public interface Flags {
    /**
     * Whether to enable the AdServices System Service. By default, the AdServices System Service is
     * disabled.
     */
    boolean ADSERVICES_SYSTEM_SERVICE_ENABLED = false;

    default boolean getAdServicesSystemServiceEnabled() {
        return ADSERVICES_SYSTEM_SERVICE_ENABLED;
    }

    /** Whether to enable the client error logger. By default, it is disabled. */
    boolean CLIENT_ERROR_LOGGING__ENABLE_CEL_FOR_SYSTEM_SERVER = false;

    default boolean getEnableCelForSystemServer() {
        return CLIENT_ERROR_LOGGING__ENABLE_CEL_FOR_SYSTEM_SERVER;
    }

    /** Feature flag to enable AtomicFileDataStore update API for system service. */
    boolean ENABLE_ATOMIC_FILE_DATASTORE_BATCH_UPDATE_API_IN_SYSTEM_SERVER = false;

    /** Returns whether atomic file datastore batch update Api is enabled in system server. */
    default boolean getEnableAtomicFileDatastoreBatchUpdateApiInSystemServer() {
        return ENABLE_ATOMIC_FILE_DATASTORE_BATCH_UPDATE_API_IN_SYSTEM_SERVER;
    }
}

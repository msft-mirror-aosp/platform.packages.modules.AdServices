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

import static com.android.server.adservices.Flags.ADSERVICES_SYSTEM_SERVICE_ENABLED;
import static com.android.server.adservices.Flags.CLIENT_ERROR_LOGGING__ENABLE_CEL_FOR_SYSTEM_SERVER;
import static com.android.server.adservices.Flags.ENABLE_ATOMIC_FILE_DATASTORE_BATCH_UPDATE_API_IN_SYSTEM_SERVER;
import static com.android.server.adservices.PhFlags.KEY_ADSERVICES_SYSTEM_SERVICE_ENABLED;
import static com.android.server.adservices.PhFlags.KEY_CLIENT_ERROR_LOGGING__ENABLE_CEL_FOR_SYSTEM_SERVER;
import static com.android.server.adservices.PhFlags.KEY_ENABLE_BATCH_UPDATE_API_IN_SYSTEM_SERVER;

import static com.google.common.truth.Truth.assertThat;

import android.provider.DeviceConfig;

import com.android.modules.utils.testing.TestableDeviceConfig;

import org.junit.Rule;
import org.junit.Test;

public final class PhFlagsTest {

    @Rule
    public final TestableDeviceConfig.TestableDeviceConfigRule mDeviceConfigRule =
            new TestableDeviceConfig.TestableDeviceConfigRule();

    private final Flags mPhFlags = PhFlags.getInstance();

    @Test
    public void testAdServicesSystemServiceEnabled() {
        // Without any overriding, the value is the hard coded constant.
        assertThat(mPhFlags.getAdServicesSystemServiceEnabled())
                .isEqualTo(ADSERVICES_SYSTEM_SERVICE_ENABLED);

        // Now overriding with the value from PH.
        boolean phOverridingValue = !ADSERVICES_SYSTEM_SERVICE_ENABLED;

        DeviceConfig.setProperty(
                DeviceConfig.NAMESPACE_ADSERVICES,
                KEY_ADSERVICES_SYSTEM_SERVICE_ENABLED,
                Boolean.toString(phOverridingValue),
                /* makeDefault */ false);

        assertThat(mPhFlags.getAdServicesSystemServiceEnabled()).isEqualTo(phOverridingValue);
    }

    @Test
    public void testAdServicesSystemServiceErrorLoggingEnabled() {
        // Without any overriding, the value is the hard coded constant.
        assertThat(mPhFlags.getEnableCelForSystemServer())
                .isEqualTo(CLIENT_ERROR_LOGGING__ENABLE_CEL_FOR_SYSTEM_SERVER);

        // Now overriding with the value from PH.
        boolean phOverridingValue = !CLIENT_ERROR_LOGGING__ENABLE_CEL_FOR_SYSTEM_SERVER;

        DeviceConfig.setProperty(
                DeviceConfig.NAMESPACE_ADSERVICES,
                KEY_CLIENT_ERROR_LOGGING__ENABLE_CEL_FOR_SYSTEM_SERVER,
                Boolean.toString(phOverridingValue),
                /* makeDefault */ false);

        assertThat(mPhFlags.getEnableCelForSystemServer()).isEqualTo(phOverridingValue);
    }

    @Test
    public void testEnableAtomicFileDatastoreBatchUpdateAPIInSystemServer() {
        // Without any overriding, the value is the hard coded constant.
        assertThat(mPhFlags.getEnableAtomicFileDatastoreBatchUpdateApiInSystemServer())
                .isEqualTo(ENABLE_ATOMIC_FILE_DATASTORE_BATCH_UPDATE_API_IN_SYSTEM_SERVER);

        // Now overriding with the value from PH.
        boolean phOverridingValue = !ENABLE_ATOMIC_FILE_DATASTORE_BATCH_UPDATE_API_IN_SYSTEM_SERVER;

        DeviceConfig.setProperty(
                DeviceConfig.NAMESPACE_ADSERVICES,
                KEY_ENABLE_BATCH_UPDATE_API_IN_SYSTEM_SERVER,
                Boolean.toString(phOverridingValue),
                /* makeDefault */ false);

        assertThat(mPhFlags.getEnableAtomicFileDatastoreBatchUpdateApiInSystemServer())
                .isEqualTo(phOverridingValue);
    }
}

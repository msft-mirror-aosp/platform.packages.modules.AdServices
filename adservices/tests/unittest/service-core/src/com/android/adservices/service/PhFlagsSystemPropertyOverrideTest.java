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

package com.android.adservices.service;

import static com.android.adservices.service.Flags.TOPICS_EPOCH_JOB_FLEX_MS;
import static com.android.adservices.service.FlagsConstants.KEY_TOPICS_EPOCH_JOB_FLEX_MS;

import android.os.SystemProperties;
import android.provider.DeviceConfig;

import com.android.adservices.common.AdServicesExtendedMockitoTestCase;
import com.android.modules.utils.testing.ExtendedMockitoRule.SpyStatic;
import com.android.modules.utils.testing.StaticMockFixture;
import com.android.modules.utils.testing.TestableDeviceConfig;

import org.junit.Test;

import java.util.function.Supplier;

@SpyStatic(SystemProperties.class)
public class PhFlagsSystemPropertyOverrideTest extends AdServicesExtendedMockitoTestCase {

    private final Flags mPhFlags = PhFlags.getInstance();

    // Overriding DeviceConfig stub to avoid Read device config permission errors and to also
    // test the behavior of flags, when both device config and system properties are set.
    @Override
    protected Supplier<? extends StaticMockFixture>[] getStaticMockFixtureSuppliers() {
        @SuppressWarnings("unchecked")
        Supplier<? extends StaticMockFixture>[] suppliers =
                (Supplier<? extends StaticMockFixture>[])
                        new Supplier<?>[] {TestableDeviceConfig::new};
        return (Supplier<? extends StaticMockFixture>[]) suppliers;
    }

    @Test
    public void testGetTopicsEpochJobFlexMs() {
        // Without any overriding, the value is the hard coded constant.
        expect.withMessage("getTopicsEpochJobFlexMs() by default")
                .that(mPhFlags.getTopicsEpochJobFlexMs())
                .isEqualTo(TOPICS_EPOCH_JOB_FLEX_MS);

        // Now overriding with the value in both system properties and device config.
        long systemPropertyValue = TOPICS_EPOCH_JOB_FLEX_MS + 1;
        long deviceConfigValue = TOPICS_EPOCH_JOB_FLEX_MS + 2;
        extendedMockito.mockGetSystemProperty(
                PhFlags.getSystemPropertyName(KEY_TOPICS_EPOCH_JOB_FLEX_MS), systemPropertyValue);
        DeviceConfig.setProperty(
                DeviceConfig.NAMESPACE_ADSERVICES,
                KEY_TOPICS_EPOCH_JOB_FLEX_MS,
                Long.toString(deviceConfigValue),
                /* makeDefault */ false);

        expect.withMessage("getTopicsEpochJobFlexMs() prefers system property value")
                .that(mPhFlags.getTopicsEpochJobFlexMs())
                .isEqualTo(systemPropertyValue);
    }
}

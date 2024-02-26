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

import static com.android.adservices.common.DeviceConfigUtil.setAdservicesFlag;
import static com.android.adservices.service.Flags.TOPICS_EPOCH_JOB_FLEX_MS;
import static com.android.adservices.service.FlagsConstants.KEY_TOPICS_EPOCH_JOB_FLEX_MS;

import android.os.SystemProperties;

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
        testSystemPropertyIsPreferred(
                KEY_TOPICS_EPOCH_JOB_FLEX_MS,
                TOPICS_EPOCH_JOB_FLEX_MS,
                flags -> flags.getTopicsEpochJobFlexMs());
    }

    private void testSystemPropertyIsPreferred(
            String name, long defaultValue, Flaginator<Long> flaginator) {
        // Without any overriding, the value is the hard coded constant.
        expect.withMessage("getter for %s by default", name)
                .that(flaginator.getFlagValue(mPhFlags))
                .isEqualTo(defaultValue);

        // Now overriding with the value in both system properties and device config.
        long systemPropertyValue = defaultValue + 1;
        long deviceConfigValue = defaultValue + 2;
        mockGetSystemProperty(name, systemPropertyValue);

        setAdservicesFlag(name, deviceConfigValue);

        expect.withMessage("getter for %s prefers system property value", name)
                .that(flaginator.getFlagValue(mPhFlags))
                .isEqualTo(systemPropertyValue);
    }

    private void mockGetSystemProperty(String name, long value) {
        extendedMockito.mockGetSystemProperty(PhFlags.getSystemPropertyName(name), value);
    }
}

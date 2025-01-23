/*
 * Copyright (C) 2025 The Android Open Source Project
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

package com.android.adservices.shared.metriclogger.logsampler.deviceselection;

import static com.android.adservices.shared.metriclogger.logsampler.deviceselection.PerDeviceSamplingConfig.DEFAULT_GROUP_NAME;
import static com.android.adservices.shared.metriclogger.logsampler.deviceselection.PerDeviceSamplingConfig.DEFAULT_ROTATION_PERIOD;
import static com.android.adservices.shared.metriclogger.logsampler.deviceselection.PerDeviceSamplingConfig.DEFAULT_SAMPLING_RATE;
import static com.android.adservices.shared.metriclogger.logsampler.deviceselection.PerDeviceSamplingConfig.DEFAULT_STAGGERING_PERIOD;

import static org.junit.Assert.assertThrows;

import com.android.adservices.shared.SharedUnitTestCase;
import com.android.adservices.shared.proto.LogSamplingConfig.PerDeviceSampling;

import org.junit.Test;

import java.time.Duration;

public final class PerDeviceSelectionConfigTest extends SharedUnitTestCase {

    private static final PerDeviceSampling EXAMPLE_PER_DEVICE_SAMPLING_CONFIG =
            PerDeviceSampling.newBuilder()
                    .setSamplingRate(0.5)
                    .setRotationPeriodDays(50)
                    .setStaggeringPeriodDays(2)
                    .setGroupName("example")
                    .build();

    @Test
    public void testCreatePerDeviceSamplingConfig_defaultConfig_alwaysLog() {
        PerDeviceSamplingConfig config =
                PerDeviceSamplingConfig.createPerDeviceSamplingConfig(
                        PerDeviceSampling.getDefaultInstance());

        expect.withMessage("samplingRate")
                .that(config.getSamplingRate())
                .isEqualTo(DEFAULT_SAMPLING_RATE);
        expect.withMessage("rotationPeriod")
                .that(config.getRotationPeriod())
                .isEqualTo(DEFAULT_ROTATION_PERIOD);
        expect.withMessage("staggeringPeriod")
                .that(config.getStaggeringPeriod())
                .isEqualTo(DEFAULT_STAGGERING_PERIOD);
        expect.withMessage("groupName").that(config.getGroupName()).isEqualTo(DEFAULT_GROUP_NAME);
    }

    @Test
    public void testCreatePerDeviceSamplingConfig() {
        PerDeviceSamplingConfig config =
                PerDeviceSamplingConfig.createPerDeviceSamplingConfig(
                        EXAMPLE_PER_DEVICE_SAMPLING_CONFIG);

        expect.withMessage("samplingRate").that(config.getSamplingRate()).isEqualTo(0.5);
        expect.withMessage("rotationPeriod")
                .that(config.getRotationPeriod())
                .isEqualTo(Duration.ofDays(50));
        expect.withMessage("staggeringPeriod")
                .that(config.getStaggeringPeriod())
                .isEqualTo(Duration.ofDays(2));
        expect.withMessage("groupName").that(config.getGroupName()).isEqualTo("example");
    }

    @Test
    public void testCreatePerEventSamplingConfig_invalidSamplingRate_alwaysLog() {
        // Upper bound is invalid
        assertThrows(
                IllegalArgumentException.class,
                () ->
                        PerDeviceSamplingConfig.createPerDeviceSamplingConfig(
                                PerDeviceSampling.newBuilder().setSamplingRate(1.5).build()));

        // Lower bound is invalid
        assertThrows(
                IllegalArgumentException.class,
                () ->
                        PerDeviceSamplingConfig.createPerDeviceSamplingConfig(
                                PerDeviceSampling.newBuilder().setSamplingRate(-1).build()));
    }
}

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

package com.android.adservices.shared.metriclogger.logsampler;

import static org.junit.Assert.assertThrows;

import com.android.adservices.shared.SharedUnitTestCase;
import com.android.adservices.shared.proto.LogSamplingConfig.PerEventSampling;

import org.junit.Test;

public final class PerEventSamplingConfigTest extends SharedUnitTestCase {

    private static final double SAMPLE_RATE_50_PERCENT = 0.5;
    private static final double SAMPLE_RATE_100_PERCENT = 1.0;
    private static final PerEventSampling EXAMPLE_PER_EVENT_SAMPLING_CONFIG =
            PerEventSampling.newBuilder().setSamplingRate(SAMPLE_RATE_50_PERCENT).build();

    @Test
    public void testCreatePerEventSamplingConfig() {
        PerEventSamplingConfig config =
                PerEventSamplingConfig.createPerEventSamplingConfig(
                        EXAMPLE_PER_EVENT_SAMPLING_CONFIG);

        expect.withMessage("sampleRate")
                .that(config.getSamplingRate())
                .isEqualTo(SAMPLE_RATE_50_PERCENT);
    }

    @Test
    public void testCreatePerEventSamplingConfig_defaultConfig_alwaysLog() {
        PerEventSamplingConfig config =
                PerEventSamplingConfig.createPerEventSamplingConfig(
                        PerEventSampling.getDefaultInstance());

        expect.withMessage("sampleRate")
                .that(config.getSamplingRate())
                .isEqualTo(SAMPLE_RATE_100_PERCENT);
    }

    @Test
    public void testCreatePerEventSamplingConfig_invalidSamplingRate_alwaysLog() {
        // Upper bound is invalid
        assertThrows(
                IllegalArgumentException.class,
                () ->
                        PerEventSamplingConfig.createPerEventSamplingConfig(
                                PerEventSampling.newBuilder().setSamplingRate(1.5).build()));

        // Lower bound is invalid
        assertThrows(
                IllegalArgumentException.class,
                () ->
                        PerEventSamplingConfig.createPerEventSamplingConfig(
                                PerEventSampling.newBuilder().setSamplingRate(-1).build()));
    }
}

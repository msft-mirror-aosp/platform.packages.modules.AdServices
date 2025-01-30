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

package com.android.adservices.shared.metriclogger;

import com.android.adservices.shared.SharedUnitTestCase;

import org.junit.Test;

public final class SamplingMetadataTest extends SharedUnitTestCase {

    @Test
    public void testGetPerEventSamplingRate() {
        double deviceSampleRate = 0.8;
        double eventSampleRate = 0.5;
        SamplingMetadata metadata = new SamplingMetadata(deviceSampleRate, eventSampleRate);

        expect.withMessage("deviceSampleRate")
                .that(metadata.getPerDeviceSamplingRate())
                .isEqualTo(deviceSampleRate);
        expect.withMessage("eventSampleRate")
                .that(metadata.getPerEventSamplingRate())
                .isEqualTo(eventSampleRate);
    }
}

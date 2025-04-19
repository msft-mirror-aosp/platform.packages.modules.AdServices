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

package com.android.adservices.shared.metriclogger.logsampler;

import com.android.adservices.shared.SharedUnitTestCase;

import org.junit.Test;

public final class SamplerResultTest extends SharedUnitTestCase {
    @Test
    public void testCreate_logs() {
        double sampleRate = 0.2;
        SamplerResult samplerResult = SamplerResult.create(/* shouldLogEvent= */ true, sampleRate);
        expect.withMessage("getShouldLogEvent()").that(samplerResult.getShouldLogEvent()).isTrue();
        expect.withMessage("getAppliedSamplingRate()")
                .that(samplerResult.getAppliedSamplingRate())
                .isEqualTo(sampleRate);
    }

    @Test
    public void testCreate_doesNotLog() {
        double sampleRate = 0.6;
        SamplerResult samplerResult = SamplerResult.create(/* shouldLogEvent= */ false, sampleRate);
        expect.withMessage("getShouldLogEvent()").that(samplerResult.getShouldLogEvent()).isFalse();
        expect.withMessage("getAppliedSamplingRate()")
                .that(samplerResult.getAppliedSamplingRate())
                .isEqualTo(sampleRate);
    }
}

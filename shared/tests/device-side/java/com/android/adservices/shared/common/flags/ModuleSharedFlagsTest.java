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

package com.android.adservices.shared.common.flags;

import static com.android.adservices.shared.common.flags.ModuleSharedFlags.BACKGROUND_JOB_LOGGING_ENABLED;
import static com.android.adservices.shared.common.flags.ModuleSharedFlags.BACKGROUND_JOB_SAMPLING_LOGGING_RATE;
import static com.android.adservices.shared.common.flags.ModuleSharedFlags.ENCODED_ERROR_CODE_LIST_PER_SAMPLE_INTERVAL;

import static com.google.common.truth.Truth.assertThat;

import com.android.adservices.shared.SharedUnitTestCase;

import org.junit.Test;

/** Unit tests for {@link ModuleSharedFlags}. */
public final class ModuleSharedFlagsTest extends SharedUnitTestCase {

    private final ModuleSharedFlags mFlags = new ModuleSharedFlags() {};

    @Test
    public void testGetBackgroundJobsLoggingEnabled() {
        assertThat(mFlags.getBackgroundJobsLoggingEnabled())
                .isEqualTo(BACKGROUND_JOB_LOGGING_ENABLED);
    }

    @Test
    public void testGetBackgroundJobSamplingLoggingRate() {
        assertThat(mFlags.getBackgroundJobSamplingLoggingRate())
                .isEqualTo(BACKGROUND_JOB_SAMPLING_LOGGING_RATE);
    }

    @Test
    public void testGetErrorCodeSampleInterval() {
        assertThat(mFlags.getEncodedErrorCodeListPerSampleInterval())
                .isEqualTo(ENCODED_ERROR_CODE_LIST_PER_SAMPLE_INTERVAL);
    }

    // TODO(b/325135083): add a test to make sure all constants are annotated with FeatureFlag or
    // ConfigFlag. Might need to be added in a separate file / Android.bp project as the annotation
    // is currently retained on SOURCE only.
}

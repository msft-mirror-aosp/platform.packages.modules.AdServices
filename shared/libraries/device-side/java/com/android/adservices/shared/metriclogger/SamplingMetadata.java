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

/**
 * Sampling metadata that gets uploaded to StatsD
 *
 * <p>This includes sampling rates so that we can upsample in the log processing pipeline.
 */
public final class SamplingMetadata {
    private final double mPerDeviceSampleRate;
    private final double mPerEventSampleRate;

    public SamplingMetadata(double perDeviceSamplingRate, double perEventSampleRate) {
        mPerDeviceSampleRate = perDeviceSamplingRate;
        mPerEventSampleRate = perEventSampleRate;
    }

    /** Returns the per-device sampling rate to use. */
    public double getPerDeviceSamplingRate() {
        return mPerDeviceSampleRate;
    }

    /** Returns the per-event sampling rate to use. */
    public double getPerEventSamplingRate() {
        return mPerEventSampleRate;
    }
}

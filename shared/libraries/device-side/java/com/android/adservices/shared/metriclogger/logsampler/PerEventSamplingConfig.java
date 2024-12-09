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

import com.android.adservices.shared.proto.LogSamplingConfig;

import com.google.auto.value.AutoValue;
import com.google.errorprone.annotations.Immutable;

/**
 * Describes the on-device per-event sampling configuration for a metric.
 *
 * <p>On-device per-event sampling is a log reduction technique by which, instead of uploading all
 * the log events of particular event type as we receive them, we upload only a certain percentage
 * of log events as defined by the sample rate.
 */
@AutoValue
@Immutable
public abstract class PerEventSamplingConfig {

    /** Returns the sampling rate to use. */
    public abstract double getSamplingRate();

    /** Returns the builder for {@link PerEventSamplingConfig}. */
    public abstract Builder toBuilder();

    /**
     * Creates an instance of {@link PerEventSamplingConfig} which contains configuration for
     * per-event sampling.
     */
    public static PerEventSamplingConfig createPerEventSamplingConfig(
            LogSamplingConfig.PerEventSampling config) {
        Builder builder = PerEventSamplingConfig.builder();
        builder.samplingRate(getSamplingRate(config));
        return builder.build();
    }

    private static Builder builder() {
        return new AutoValue_PerEventSamplingConfig.Builder();
    }

    /** Builder for this class */
    @AutoValue.Builder
    public abstract static class Builder {

        /** Sets the value for {@link #getSamplingRate()}. */
        public abstract Builder samplingRate(double samplingRate);

        /** Builds a new {@link PerEventSamplingConfig} instance. */
        public abstract PerEventSamplingConfig build();
    }

    /**
     * Checks if sampling rate is between 0 and 1 and returns the rate.
     *
     * <p>If sampling rate not set, we always log and return 1.
     *
     * @throws IllegalArgumentException if the sampling rate is outside the bounds.
     */
    private static double getSamplingRate(LogSamplingConfig.PerEventSampling config) {
        if (config.hasSamplingRate() && checkSamplingRate(config.getSamplingRate())) {
            return config.getSamplingRate();
        }

        return 1.0;
    }

    private static boolean checkSamplingRate(double samplingRate) {
        if (samplingRate < 0.0 || samplingRate > 1.0) {
            throw new IllegalArgumentException(
                    String.format("Sampling rate=%f should be between 0 and 1", samplingRate));
        }
        return true;
    }
}

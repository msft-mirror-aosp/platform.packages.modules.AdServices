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

import com.android.adservices.shared.proto.DimensionMatcher;
import com.android.adservices.shared.proto.DimensionName;
import com.android.adservices.shared.proto.LogSamplingConfig;

import com.google.auto.value.AutoValue;
import com.google.common.collect.ImmutableList;
import com.google.common.collect.ImmutableMap;

import java.util.function.Function;

/**
 * Describes the on-device per-event sampling configuration for a metric.
 *
 * <p>On-device per-event sampling is a log reduction technique by which, instead of uploading all
 * the log events of particular event type as we receive them, we upload only a certain percentage
 * of log events as defined by the sample rate.
 *
 * @param <L> the type of the log event.
 */
@AutoValue
public abstract class PerEventSamplingConfig<L> {

    /** Returns the sampling rate to use. */
    public abstract double getSamplingRate();

    /** Returns a list of {@link DimensionMatcher} used for matching against events. */
    public abstract ImmutableList<DimensionMatcher> getDimensionMatcherList();

    /**
     * Returns the map of dimension name to value {@link Function}. Each function takes a log event
     * and extracts its corresponding integer value for the associated dimension.
     */
    public abstract ImmutableMap<DimensionName, Function<L, Integer>>
            getDimensionNameToValueFunctionMap();

    /** Return {@link Boolean} indicating whether dimension in logs sampling is supported. */
    public abstract boolean getSupportDimensionInLogSamplingEnabled();

    /** Returns the builder for {@link PerEventSamplingConfig}. */
    public abstract Builder<L> toBuilder();

    /**
     * Creates an instance of {@link PerEventSamplingConfig} which contains configuration for
     * per-event sampling.
     */
    public static <L> PerEventSamplingConfig<L> createPerEventSamplingConfig(
            LogSamplingConfig.PerEventSampling config,
            ImmutableMap<DimensionName, Function<L, Integer>> dimensionNameToValueFunctionMap,
            boolean supportDimensionInLogSamplingEnabled) {
        Builder<L> builder = PerEventSamplingConfig.builder();
        builder.samplingRate(getSamplingRate(config));
        builder.dimensionMatcherList(ImmutableList.copyOf(config.getDimensionMatcherList()));
        builder.dimensionNameToValueFunctionMap(dimensionNameToValueFunctionMap);
        builder.supportDimensionInLogSamplingEnabled(supportDimensionInLogSamplingEnabled);
        return builder.build();
    }

    private static <L> Builder<L> builder() {
        return new AutoValue_PerEventSamplingConfig.Builder<L>();
    }

    /**
     * Builder for this class
     *
     * @param <L> the type of the log event.
     */
    @AutoValue.Builder
    public abstract static class Builder<L> {

        /** Sets the value for {@link #getSamplingRate()}. */
        public abstract Builder<L> samplingRate(double samplingRate);

        /**
         * Sets the map of dimension name to value {@link Function}. Each function takes a log event
         * and extracts its corresponding integer value for the associated dimension.
         */
        public abstract Builder<L> dimensionNameToValueFunctionMap(
                ImmutableMap<DimensionName, Function<L, Integer>> samplingDimensionFunction);

        /** Sets a list of {@link DimensionMatcher} used for matching against events. */
        abstract Builder<L> dimensionMatcherList(ImmutableList<DimensionMatcher> dimensionMatcher);

        /** Sets {@link Boolean} indicating whether dimension in logs sampling is supported. */
        abstract Builder<L> supportDimensionInLogSamplingEnabled(
                boolean supportDimensionInLogSamplingEnabled);

        /** Builds a new {@link PerEventSamplingConfig} instance. */
        public abstract PerEventSamplingConfig<L> build();
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

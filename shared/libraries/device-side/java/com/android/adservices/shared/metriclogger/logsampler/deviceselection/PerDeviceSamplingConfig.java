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

import com.android.adservices.shared.proto.DimensionMatcher;
import com.android.adservices.shared.proto.DimensionName;
import com.android.adservices.shared.proto.LogSamplingConfig;
import com.android.internal.annotations.VisibleForTesting;

import com.google.auto.value.AutoValue;
import com.google.common.collect.ImmutableList;
import com.google.common.collect.ImmutableMap;

import java.time.Duration;
import java.util.function.Function;

/**
 * Describes the on-device per-device sampling configuration for a metric.
 *
 * <p>On-device per-device sampling is a log reduction technique by which, instead of uploading the
 * log events from all the devices, we upload only from a certain percentage of devices.
 *
 * @param <L> the type of the log event.
 */
@AutoValue
public abstract class PerDeviceSamplingConfig<L> {

    // Default rotation period is 35 days or 3024000 seconds.
    @VisibleForTesting static final Duration DEFAULT_ROTATION_PERIOD = Duration.ofDays(35);

    // Default Staggering period is 1 day or 86400 seconds.
    @VisibleForTesting static final Duration DEFAULT_STAGGERING_PERIOD = Duration.ofDays(1);

    // Default sampling rate is 1, we always log.
    @VisibleForTesting static final double DEFAULT_SAMPLING_RATE = 1.0;

    @VisibleForTesting static final String DEFAULT_GROUP_NAME = "";

    /**
     * Returns the sampling rate to use. For a given time period, we determine the percentage of
     * devices to use for logging using the sampling rate. The default sampling rate is 1.
     */
    public abstract double getSamplingRate();

    /**
     * Returns the length of the rotation period, that is, the length of the period after which the
     * devices to log are rotated, aligned with the epoch (1970-01-01 at midnight). The default is
     * 35 days (60*60*24*35 = 3024000 seconds).
     */
    public abstract Duration getRotationPeriod();

    /**
     * Return the length of the staggering period, that is, the length of the period at which
     * rotation is staggered, aligned with the epoch (1970-01-01 at midnight). Rotation period/
     * staggering period number of devices start logging every staggering period. The default is 1
     * day.
     */
    public abstract Duration getStaggeringPeriod();

    /**
     * Returns sampling group to which a particular metric belongs to. Metrics that belong to the
     * same sampling group log from the same set of devices irrespective of their sampling rate.
     */
    public abstract String getGroupName();

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

    /**
     * Creates an instance of {@link PerDeviceSamplingConfig} which contains configuration for
     * per-device sampling.
     */
    public static <L> PerDeviceSamplingConfig<L> createPerDeviceSamplingConfig(
            LogSamplingConfig.PerDeviceSampling config,
            ImmutableMap<DimensionName, Function<L, Integer>> dimensionNameToValueFunctionMap,
            boolean supportDimensionInLogSamplingEnabled) {
        return PerDeviceSamplingConfig.<L>builder()
                .groupName(getGroupNameOrDefault(config))
                .rotationPeriod(getRotationPeriodOrDefault(config))
                .staggeringPeriod(getStaggeringPeriodOrDefault(config))
                .samplingRate(getSamplingRateOrDefault(config))
                .dimensionMatcherList(ImmutableList.copyOf(config.getDimensionMatcherList()))
                .dimensionNameToValueFunctionMap(dimensionNameToValueFunctionMap)
                .supportDimensionInLogSamplingEnabled(supportDimensionInLogSamplingEnabled)
                .build();
    }

    private static <L> Builder<L> builder() {
        return new AutoValue_PerDeviceSamplingConfig.Builder<L>();
    }

    /**
     * Checks if sampling rate is between 0 and 1 and returns the rate.
     *
     * <p>If sampling rate not set, we use default sampling rate.
     *
     * @throws IllegalArgumentException if the sampling rate is outside the bounds.
     */
    private static double getSamplingRateOrDefault(LogSamplingConfig.PerDeviceSampling config) {
        if (config.hasSamplingRate() && checkSamplingRate(config.getSamplingRate())) {
            return config.getSamplingRate();
        }

        return DEFAULT_SAMPLING_RATE;
    }

    private static boolean checkSamplingRate(double samplingRate) {
        if (samplingRate < 0.0 || samplingRate > 1.0) {
            throw new IllegalArgumentException(
                    String.format("Sampling rate=%f should be between 0 and 1", samplingRate));
        }
        return true;
    }

    private static String getGroupNameOrDefault(LogSamplingConfig.PerDeviceSampling config) {
        if (config.hasGroupName() && !config.getGroupName().isEmpty()) {
            return config.getGroupName();
        }
        return DEFAULT_GROUP_NAME;
    }

    private static Duration getRotationPeriodOrDefault(LogSamplingConfig.PerDeviceSampling config) {
        if (config.hasRotationPeriodDays()) {
            return Duration.ofDays(config.getRotationPeriodDays());
        }

        return DEFAULT_ROTATION_PERIOD;
    }

    private static Duration getStaggeringPeriodOrDefault(
            LogSamplingConfig.PerDeviceSampling config) {
        if (config.hasStaggeringPeriodDays()) {
            return Duration.ofDays(config.getStaggeringPeriodDays());
        }

        return DEFAULT_STAGGERING_PERIOD;
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

        /** Sets the value for {@link #getRotationPeriod()}. */
        public abstract Builder<L> rotationPeriod(Duration rotationPeriod);

        /** Sets the value for {@link #getStaggeringPeriod()}. */
        public abstract Builder<L> staggeringPeriod(Duration staggeringPeriod);

        /** Sets the value for {@link #getGroupName}. */
        public abstract Builder<L> groupName(String groupName);

        /** Builds a new {@link PerDeviceSamplingConfig} instance. */
        public abstract PerDeviceSamplingConfig<L> build();

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
    }
}

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

import android.annotation.Nullable;
import android.content.Context;

import com.android.adservices.shared.metriclogger.logsampler.LogSampler;
import com.android.adservices.shared.metriclogger.logsampler.PerEventLogSampler;
import com.android.adservices.shared.metriclogger.logsampler.PerEventSamplingConfig;
import com.android.adservices.shared.metriclogger.logsampler.deviceselection.PerDeviceLogSampler;
import com.android.adservices.shared.metriclogger.logsampler.deviceselection.PerDeviceSamplingConfig;
import com.android.adservices.shared.proto.LogSamplingConfig;
import com.android.adservices.shared.proto.MetricId;

import com.google.auto.value.AutoValue;

import java.util.concurrent.Executor;

/**
 * Describes the configuration for a single metric.
 *
 * <p>This is an immutable class whose instances describe the configuration for a single metric,
 * including general parameters, what devices will log, and log sampling techniques to apply.
 *
 * @param <L> the type of the log event.
 */
@AutoValue
public abstract class MetricLoggerConfig<L> {

    /** Returns configuration parameters set via flag. */
    public abstract @Nullable LogSamplingConfig getLogSamplingConfig();

    /** Returns the metric id. */
    public abstract MetricId getMetricId();

    /** Returns the context. */
    public abstract Context getContext();

    /** Returns the lightweight executor. */
    public abstract Executor getLightweightExecutor();

    /** Returns the background executor. */
    public abstract Executor getBackgroundExecutor();

    /** Returns a function for uploading one log event. */
    public abstract LogUploader<L> getLogUploader();

    /** Returns the per-event sampling config. */
    public abstract @Nullable PerEventSamplingConfig getPerEventSamplingConfig();

    /** Returns the per-device sampling config. */
    public abstract @Nullable PerDeviceSamplingConfig getPerDeviceSamplingConfig();

    /** Returns a generic builder. */
    public static <L> Builder<L> builder(
            MetricId metricId,
            LogSamplingConfig config,
            Executor lightweightExecutor,
            Executor backgroundExecutor,
            Context context,
            LogUploader<L> logUploader) {
        return new AutoValue_MetricLoggerConfig.Builder<L>()
                .metricId(metricId)
                .logSamplingConfig(config)
                .logUploader(logUploader)
                .context(context)
                .lightweightExecutor(lightweightExecutor)
                .backgroundExecutor(backgroundExecutor);
    }

    private static <L> Builder<L> builderWithoutSamplingConfig(
            MetricId metricId,
            Executor lightweightExecutor,
            Executor backgroundExecutor,
            Context context,
            LogUploader<L> logUploader) {
        return new AutoValue_MetricLoggerConfig.Builder<L>()
                .metricId(metricId)
                .logUploader(logUploader)
                .context(context)
                .lightweightExecutor(lightweightExecutor)
                .backgroundExecutor(backgroundExecutor);
    }

    /**
     * Creates Per-event sampling strategy that determines whether to log events based on the
     * sampling rate for a particular metric.
     */
    LogSampler<L> createPerEventSampling() {
        return new PerEventLogSampler<>(getMetricId(), getPerEventSamplingConfig());
    }

    /**
     * Creates Per-device sampling strategy that determines whether to log events based on the
     * sampling rate for a particular metric.
     */
    LogSampler<L> createPerDeviceSampling() {
        return new PerDeviceLogSampler<>(
                getContext(),
                getMetricId(),
                getPerDeviceSamplingConfig(),
                getBackgroundExecutor(),
                getLightweightExecutor());
    }

    /**
     * Builder for this class
     *
     * @param <L> the type of the log event.
     */
    @AutoValue.Builder
    public abstract static class Builder<L> {
        /** Sets the log sampling config. */
        public abstract Builder<L> logSamplingConfig(LogSamplingConfig config);

        /** Sets the id for the metric. */
        public abstract Builder<L> metricId(MetricId metricId);

        /** Sets the context. */
        public abstract Builder<L> context(Context context);

        /** Sets the lightweight executor. */
        public abstract Builder<L> lightweightExecutor(Executor executor);

        /** Sets the background executor. */
        public abstract Builder<L> backgroundExecutor(Executor executor);

        /** Function to upload metric to statsd. */
        public abstract Builder<L> logUploader(LogUploader<L> logUploader);

        /** Sets the config for perEventSampling. */
        public abstract Builder<L> perEventSamplingConfig(
                PerEventSamplingConfig eventSamplingConfig);

        /** Sets the config for perDeviceSampling. */
        public abstract Builder<L> perDeviceSamplingConfig(
                PerDeviceSamplingConfig deviceSamplingConfig);

        abstract MetricLoggerConfig<L> autoBuild();

        /** Builds the instance of {@link MetricLoggerConfig} */
        public MetricLoggerConfig<L> build() {
            MetricLoggerConfig<L> config = autoBuild();
            return mergeLoggerConfig(config);
        }

        /**
         * Merges fields from {@link LogSamplingConfig} proto into their corresponding objects.
         *
         * <p>This method extracts configuration parameters from the provided {@link
         * LogSamplingConfig} proto and populates the fields in the relevant configuration objects
         * inside {@link MetricLoggerConfig}. Since this class is immutable, it does so by creating
         * a new instance.
         */
        private MetricLoggerConfig<L> mergeLoggerConfig(MetricLoggerConfig<L> original) {
            LogSamplingConfig config = original.getLogSamplingConfig();

            MetricLoggerConfig.Builder<L> builder =
                    MetricLoggerConfig.builderWithoutSamplingConfig(
                            original.getMetricId(),
                            original.getLightweightExecutor(),
                            original.getBackgroundExecutor(),
                            original.getContext(),
                            original.getLogUploader());
            // Extract the sampling strategies from proto to create relevant sampling config objects
            // for each strategy.
            if (config.hasPerEventSampling()) {
                builder.perEventSamplingConfig(
                        PerEventSamplingConfig.createPerEventSamplingConfig(
                                config.getPerEventSampling()));
            }
            if (config.hasPerDeviceSampling()) {
                builder.perDeviceSamplingConfig(
                        PerDeviceSamplingConfig.createPerDeviceSamplingConfig(
                                config.getPerDeviceSampling()));
            }

            return builder.autoBuild();
        }
    }
}

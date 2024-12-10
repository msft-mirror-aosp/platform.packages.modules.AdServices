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

import com.android.adservices.shared.metriclogger.logsampler.LogSampler;

import com.google.common.base.Supplier;
import com.google.common.base.Suppliers;

/**
 * Abstract base class for metric loggers which implements the {@link MetricLogger}.
 *
 * <p>Concrete logger implementations should extend this class.
 *
 * @param <L> the type of the log event.
 *     <p>Example Usage:
 *     <pre>{@code
 * // Clients extends the AbstractMetricLogger class to create a logger for the metric
 * ExampleLogger extends AbstractMetricLogger<ExampleStats> { }
 *
 * // Using the logger to log an event
 * // Log an event directly
 * ExampleLogger.get().log(exampleStats);
 *
 * // Log an event using a supplier. Typically used when creating ExampleStats is expensive and we
 * // don't want to create and drop later due to event is not sampled.
 * ExampleLogger.get().log(() -> ExampleStats.builder()...build());
 *
 * }</pre>
 */
public abstract class AbstractMetricLogger<L> implements MetricLogger<L> {
    public static final String TAG = "MetricLogger";

    // Use supplier to lazily instantiate it and avoid log sampler instantiation in the constructor.
    private final Supplier<LogSampler<L>> mPerEventSampling;
    private final MetricLoggerConfig<L> mConfig;

    public AbstractMetricLogger(MetricLoggerConfig<L> config) {
        mConfig = config;
        mPerEventSampling = Suppliers.memoize(config::createPerEventSampling);
    }

    @Override
    public void log(L log) {
        log(() -> log);
    }

    @Override
    public void log(Supplier<L> logSupplier) {
        if (mPerEventSampling.get().shouldLog()) {
            mConfig.getLogUploader().accept(logSupplier.get(), getMetadata());
        }
    }

    private SamplingMetadata getMetadata() {
        // If sampling config is null, perform no per-event sampling (i.e. all events are logged).
        double perEventSampling =
                mConfig.getPerEventSamplingConfig() == null
                        ? 1.0
                        : mConfig.getPerEventSamplingConfig().getSamplingRate();

        return new SamplingMetadata(perEventSampling);
    }
}

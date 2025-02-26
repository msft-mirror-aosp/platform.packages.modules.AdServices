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

import static com.android.adservices.shared.metriclogger.AbstractMetricLogger.TAG;

import android.annotation.Nullable;
import android.util.Log;

import com.android.adservices.shared.proto.MetricId;

/**
 * Implements per-event sampling strategy for a metric.
 *
 * <p>Per-event sampling implementation that checks if we should log event with a probability
 * specified by the sampling rate (a decimal value between 0 and 1).
 *
 * @param <L> the type of the log event.
 */
public final class PerEventLogSampler<L> implements LogSampler<L> {

    private static final String EVENT_SAMPLER = "PerEventSampler";

    @Nullable private final PerEventSamplingConfig mConfig;
    private final MetricId mMetricId;

    public PerEventLogSampler(MetricId metricId, @Nullable PerEventSamplingConfig config) {
        this.mConfig = config;
        this.mMetricId = metricId;
    }

    /**
     * Returns true if we should log the event.
     *
     * <p>Rolls a dice and returns whether the event will be logged or not.
     */
    public boolean shouldLog() {
        if (mConfig != null) {
            boolean logDecision = Math.random() <= mConfig.getSamplingRate();
            Log.v(
                    TAG,
                    String.format(
                            "%s %s: Computed Per event sampling decision whether to log the metric:"
                                    + " %b",
                            mMetricId.name(), EVENT_SAMPLER, logDecision));
            return logDecision;
        }

        Log.v(
                TAG,
                String.format(
                        "%s %s: Per-event sampling config is missing, always log",
                        mMetricId.name(), EVENT_SAMPLER));
        return true;
    }
}

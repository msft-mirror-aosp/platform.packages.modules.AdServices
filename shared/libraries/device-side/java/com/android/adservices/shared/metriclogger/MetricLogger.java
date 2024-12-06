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

import com.google.common.base.Supplier;

/**
 * Interface for defining a metric logger responsible for logging a specific metric.
 *
 * @param <L> type of the log event.
 */
public interface MetricLogger<L> {

    /**
     * Logs the specified event.
     *
     * <p>Applies log sampling techniques before logging the event.
     *
     * <p>Use this method if producing log is inexpensive and we don't care about log `L` thrown
     * away after sampling.
     *
     * <p>If producing log `L` is expensive or if we don't want to produce log `L` and throw them
     * away, use the below log method with Supplier.
     */
    void log(L log);

    /**
     * Logs the event returned by the specified supplier.
     *
     * <p>If we decide to not log the event because of sampling techniques, the supplier won't be
     * invoked. This is useful to avoid incurring the cost of generating the event when not needed.
     */
    void log(Supplier<L> logSupplier);
}

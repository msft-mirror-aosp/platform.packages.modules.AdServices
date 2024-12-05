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

/**
 * Interface to define sampling strategies whether a specific event is sampled or not.
 *
 * <p>Implementations of this interface determine whether a specific event should be sampled (aka.
 * logged), based on strategies like per-event sampling or capping.
 *
 * <p>This helps in reducing the amount of data collected to the server and saving battery life,
 * storage space and network bandwidth.
 *
 * @param <L> The type of the log event.
 */
public interface LogSampler<L> {

    /** Returns true if the event should be logged. */
    boolean shouldLog();
}

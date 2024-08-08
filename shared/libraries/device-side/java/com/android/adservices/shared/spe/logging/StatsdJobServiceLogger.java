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

package com.android.adservices.shared.spe.logging;

/** An interface contains methods used to log by Statsd. */
public interface StatsdJobServiceLogger {
    /**
     * Logs the job execution stats to the logging server.
     *
     * @param stats the stats object {@link ExecutionReportedStats} to log.
     */
    void logExecutionReportedStats(ExecutionReportedStats stats);

    /**
     * Logs the job scheduling stats to the logging server.
     *
     * @param stats the stats object {@link SchedulingReportedStats} to log.
     */
    void logSchedulingReportedStats(SchedulingReportedStats stats);
}
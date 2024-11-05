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

package com.android.cobalt.logging;

/** The API for providing operation logging to Cobalt. */
public interface CobaltOperationLogger {

    /**
     * Logs that a Cobalt logging event exceeds the string buffer max.
     *
     * @param metricId the Cobalt metric id of the event that is being logged
     * @param reportId the Cobalt report id of the event that is being logged
     */
    void logStringBufferMaxExceeded(int metricId, int reportId);

    /**
     * Logs that a Cobalt logging event exceeds the event vector buffer max.
     *
     * @param metricId the Cobalt metric id of the event that is being logged
     * @param reportId the Cobalt report id of the event that is being logged
     */
    void logEventVectorBufferMaxExceeded(int metricId, int reportId);

    /**
     * Logs that a Cobalt logging event exceeds the max value when calculating its private index.
     *
     * @param metricId the Cobalt metric id of the event that is being logged
     * @param reportId the Cobalt report id of the event that is being logged
     */
    void logMaxValueExceeded(int metricId, int reportId);

    /** Logs the Cobalt periodic job failed to upload envelopes. */
    void logUploadFailure();

    /** Logs the Cobalt periodic job uploaded envelopes successfully. */
    void logUploadSuccess();
}

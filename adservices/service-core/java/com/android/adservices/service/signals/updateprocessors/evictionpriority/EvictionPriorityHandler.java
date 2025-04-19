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

package com.android.adservices.service.signals.updateprocessors.evictionpriority;

import com.android.adservices.data.signals.DBProtectedSignal;
import com.android.adservices.service.signals.evict.EvictionPriority;
import com.android.adservices.service.stats.pas.UpdateSignalsProcessReportedLogger;

import org.json.JSONObject;

import java.nio.ByteBuffer;
import java.util.Map;
import java.util.Set;

/** Handler for the {@link EvictionPriority} update field. */
public interface EvictionPriorityHandler {

    /**
     * Gets the eviction priority from a signal update.
     *
     * @param key a ByteBuffer wrapped signal key, used for logging purpose.
     * @param update The update.
     * @param updateSignalsProcessReportedLogger The logger for Signals related telemetry.
     * @return The eviction priority.
     */
    EvictionPriority getEvictionPriorityFromUpdate(
            ByteBuffer key,
            JSONObject update,
            UpdateSignalsProcessReportedLogger updateSignalsProcessReportedLogger);

    /**
     * Gets the eviction priority from a signal update, or from the set of existing signals if
     * missing from the update.
     *
     * @param key a ByteBuffer wrapped signal key, used for logging purpose.
     * @param update The update.
     * @param existingSignalsMap The set of existing signals per key.
     * @param updateSignalsProcessReportedLogger The logger for Signals related telemetry.
     * @return The eviction priority.
     */
    EvictionPriority getEvictionPriorityFromUpdateOrExistingSignals(
            ByteBuffer key,
            JSONObject update,
            Map<ByteBuffer, Set<DBProtectedSignal>> existingSignalsMap,
            UpdateSignalsProcessReportedLogger updateSignalsProcessReportedLogger);
}

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

import com.android.adservices.LoggerFactory;
import com.android.adservices.service.signals.evict.EvictionPriority;
import com.android.adservices.service.stats.pas.UpdateSignalsProcessReportedLogger;

import org.json.JSONObject;

import java.nio.ByteBuffer;

/** Implementation of {@link EvictionPriorityHandler} used if prioritized eviction is enabled. */
public class EvictionPriorityHandlerImpl implements EvictionPriorityHandler {
    private static final String EVICTION_PRIORITY = "eviction_priority";

    private static final LoggerFactory.Logger sLogger = LoggerFactory.getFledgeLogger();

    @Override
    public EvictionPriority getEvictionPriority(
            ByteBuffer key,
            JSONObject update,
            UpdateSignalsProcessReportedLogger updateSignalsProcessReportedLogger) {
        String priorityString = update.optString(EVICTION_PRIORITY);

        if (priorityString.isEmpty()) {
            sLogger.v(
                    "No eviction priority in update, proceeding with default eviction priority: "
                            + EvictionPriority.DEFAULT);
            return EvictionPriority.DEFAULT;
        }

        try {
            EvictionPriority priority = EvictionPriority.valueOf(priorityString);
            updateSignalsProcessReportedLogger.addUpdatedSignalWithEvictionPriorityForCount(key);
            updateSignalsProcessReportedLogger.addUpdatedSignalEvictionPriority(priority);
            return priority;
        } catch (IllegalArgumentException e) {
            sLogger.e(e, "Invalid eviction priority in update: " + priorityString);
            throw e;
        }
    }
}

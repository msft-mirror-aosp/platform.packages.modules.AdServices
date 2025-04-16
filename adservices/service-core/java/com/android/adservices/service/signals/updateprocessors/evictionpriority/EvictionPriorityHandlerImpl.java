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
import com.android.adservices.data.signals.DBProtectedSignal;
import com.android.adservices.service.signals.evict.EvictionPriority;
import com.android.adservices.service.stats.pas.UpdateSignalsProcessReportedLogger;

import com.google.common.collect.Iterables;

import org.json.JSONObject;

import java.nio.ByteBuffer;
import java.util.Map;
import java.util.Set;

/** Implementation of {@link EvictionPriorityHandler} used if prioritized eviction is enabled. */
public class EvictionPriorityHandlerImpl implements EvictionPriorityHandler {
    private static final String EVICTION_PRIORITY = "eviction_priority";

    private static final LoggerFactory.Logger sLogger = LoggerFactory.getFledgeLogger();

    @Override
    public EvictionPriority getEvictionPriorityFromUpdate(
            ByteBuffer key,
            JSONObject update,
            UpdateSignalsProcessReportedLogger updateSignalsProcessReportedLogger) {
        String priorityString = update.optString(EVICTION_PRIORITY);

        if (priorityString.isEmpty()) {
            sLogger.v(
                    "No eviction priority in update, returning default eviction priority: "
                            + EvictionPriority.DEFAULT);
            return EvictionPriority.DEFAULT;
        }

        try {
            EvictionPriority evictionPriority = EvictionPriority.valueOf(priorityString);
            updateSignalsProcessReportedLogger.addUpdatedSignalWithEvictionPriorityForCount(key);
            updateSignalsProcessReportedLogger.addUpdatedSignalEvictionPriority(evictionPriority);
            return evictionPriority;
        } catch (IllegalArgumentException e) {
            sLogger.e(e, "Invalid eviction priority in update: " + priorityString);
            throw e;
        }
    }

    @Override
    public EvictionPriority getEvictionPriorityFromUpdateOrExistingSignals(
            ByteBuffer key,
            JSONObject update,
            Map<ByteBuffer, Set<DBProtectedSignal>> existingSignalsMap,
            UpdateSignalsProcessReportedLogger updateSignalsProcessReportedLogger) {
        EvictionPriority evictionPriority = EvictionPriority.DEFAULT;
        String priorityString = update.optString(EVICTION_PRIORITY);

        if (!priorityString.isEmpty()) {
            try {
                evictionPriority = EvictionPriority.valueOf(priorityString);
                updateSignalsProcessReportedLogger.addUpdatedSignalWithEvictionPriorityForCount(
                        key);
                updateSignalsProcessReportedLogger.addUpdatedSignalEvictionPriority(
                        evictionPriority);
            } catch (IllegalArgumentException e) {
                sLogger.e(e, "Invalid eviction priority in update: " + priorityString);
                throw e;
            }
        } else if (existingSignalsMap.containsKey(key)) {
            sLogger.v(
                    "No eviction priority in update, getting eviction priority from existing"
                            + " signals");
            evictionPriority = Iterables.getLast(existingSignalsMap.get(key)).getEvictionPriority();
        } else {
            sLogger.v(
                    "No eviction priority in update or existing signals, returning default eviction"
                            + " priority: "
                            + EvictionPriority.DEFAULT);
        }

        return evictionPriority;
    }
}

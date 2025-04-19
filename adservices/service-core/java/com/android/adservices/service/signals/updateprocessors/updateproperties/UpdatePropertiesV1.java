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

package com.android.adservices.service.signals.updateprocessors.updateproperties;

import com.android.adservices.LoggerFactory;
import com.android.adservices.data.signals.DBProtectedSignal;
import com.android.adservices.service.signals.evict.EvictionPriority;
import com.android.adservices.service.signals.updateprocessors.UpdateOutput;
import com.android.adservices.service.signals.updateprocessors.UpdateProcessorUtils;
import com.android.adservices.service.signals.updateprocessors.evictionpriority.EvictionPriorityHandler;
import com.android.adservices.service.stats.pas.UpdateSignalsProcessReportedLogger;

import org.json.JSONException;
import org.json.JSONObject;

import java.nio.ByteBuffer;
import java.util.Map;
import java.util.Set;

/**
 * V1 implementation of the UpdateProperties update processor. Uses the following update schema:
 *
 * <pre>
 * {
 *   "update_properties": {
 *     <strong>[Key]</strong>: {
 *       "eviction_priority": <strong>[Eviction Priority]</strong>
 *     }
 *     ... // additional signals
 *   }
 * }
 * </pre>
 */
public class UpdatePropertiesV1 extends UpdateProperties {
    private static final LoggerFactory.Logger sLogger = LoggerFactory.getFledgeLogger();

    private final EvictionPriorityHandler mEvictionPriorityHandler;

    public UpdatePropertiesV1(EvictionPriorityHandler evictionPriorityHandler) {
        mEvictionPriorityHandler = evictionPriorityHandler;
    }

    @Override
    public UpdateOutput processUpdates(
            Object updates,
            Map<ByteBuffer, Set<DBProtectedSignal>> current,
            UpdateSignalsProcessReportedLogger updateSignalsProcessReportedLogger)
            throws JSONException {
        UpdateOutput toReturn = new UpdateOutput();
        JSONObject updatesObject =
                UpdateProcessorUtils.validateAndCastToJSONObject(UPDATE_PROPERTIES, updates);
        // Iterate over the keys.
        for (String stringKey : updatesObject.keySet()) {
            ByteBuffer key = UpdateProcessorUtils.decodeKey(UPDATE_PROPERTIES, stringKey);
            JSONObject update = updatesObject.getJSONObject(stringKey);
            processKey(key, update, current, toReturn, updateSignalsProcessReportedLogger);
        }
        return toReturn;
    }

    private void processKey(
            ByteBuffer key,
            JSONObject update,
            Map<ByteBuffer, Set<DBProtectedSignal>> allCurrentSignalsMap,
            UpdateOutput toReturn,
            UpdateSignalsProcessReportedLogger updateSignalsProcessReportedLogger) {
        UpdateProcessorUtils.touchKey(key, toReturn.getKeysTouched());

        if (!allCurrentSignalsMap.containsKey(key)) {
            sLogger.v("No signals present for key, skipping property updates");
            return;
        }

        Set<DBProtectedSignal> currentSignals = allCurrentSignalsMap.get(key);
        EvictionPriority evictionPriority =
                mEvictionPriorityHandler.getEvictionPriorityFromUpdateOrExistingSignals(
                        key, update, allCurrentSignalsMap, updateSignalsProcessReportedLogger);

        for (DBProtectedSignal currentSignal : currentSignals) {
            DBProtectedSignal.Builder updatedSignalBuilder =
                    currentSignal.toBuilder().setEvictionPriority(evictionPriority);
            toReturn.getToAdd().add(updatedSignalBuilder);
            // TODO: b/408444491 - Eliminate existing signal remove after changing
            //                     ProtectedSignalsDao to use upserts.
            toReturn.getToRemove().add(currentSignal);
        }
    }
}

/*
 * Copyright (C) 2023 The Android Open Source Project
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

package com.android.adservices.service.signals.updateprocessors.append;

import com.android.adservices.LoggerFactory;
import com.android.adservices.LoggerFactory.Logger;
import com.android.adservices.data.signals.DBProtectedSignal;
import com.android.adservices.service.signals.evict.EvictionPriority;
import com.android.adservices.service.signals.updateprocessors.UpdateOutput;
import com.android.adservices.service.signals.updateprocessors.UpdateProcessorUtils;
import com.android.adservices.service.signals.updateprocessors.evictionpriority.EvictionPriorityHandler;
import com.android.adservices.service.stats.pas.UpdateSignalsProcessReportedLogger;
import com.android.internal.annotations.VisibleForTesting;

import org.json.JSONArray;
import org.json.JSONException;
import org.json.JSONObject;

import java.nio.ByteBuffer;
import java.util.Map;
import java.util.Set;

/**
 * V0 implementation of the Append update processor. Uses the following update schema:
 *
 * <pre>
 * {
 *   "append": {
 *     <strong>[Key]</strong>: {
 *       "values": <strong>[Values]</strong>,
 *       "max_signals": <strong>[Max Signals]</strong>
 *       "eviction_priority": <strong>[Eviction Priority]</strong>
 *     }
 *     ... // additional signals
 *   }
 * }
 * </pre>
 */
public class AppendV1 extends Append {
    @VisibleForTesting
    public static final String TOO_MANY_SIGNALS_ERROR =
            "Attempting to append %d than with a max_signals of %d";

    private static final Logger sLogger = LoggerFactory.getFledgeLogger();

    private final EvictionPriorityHandler mEvictionPriorityHandler;

    public AppendV1(EvictionPriorityHandler evictionPriorityHandler) {
        mEvictionPriorityHandler = evictionPriorityHandler;
    }

    @Override
    protected void processKey(
            ByteBuffer key,
            JSONObject update,
            Map<ByteBuffer, Set<DBProtectedSignal>> current,
            UpdateOutput toReturn,
            UpdateSignalsProcessReportedLogger updateSignalsProcessReportedLogger)
            throws JSONException {
        UpdateProcessorUtils.touchKey(key, toReturn.getKeysTouched());
        int maxSignals = update.getInt(MAX_SIGNALS);
        JSONArray values = update.getJSONArray(VALUES);

        // Check that the JSON isn't trying to add more than its maximum allowed signals
        if (values.length() > maxSignals) {
            throw new IllegalArgumentException(
                    String.format(TOO_MANY_SIGNALS_ERROR, values.length(), maxSignals));
        }
        // Delete enough signals to make room for the new ones.
        deleteSignals(key, maxSignals, values, current, toReturn);

        // Read new signal properties from the update.
        EvictionPriority evictionPriority =
                mEvictionPriorityHandler.getEvictionPriorityFromUpdateOrExistingSignals(
                        key, update, current, updateSignalsProcessReportedLogger);

        // Update existing signals for the key with properties from this update.
        updateExistingSignals(key, evictionPriority, current, toReturn);

        // Add the new signals.
        addSignals(
                UpdateProcessorUtils.getByteArrayFromBuffer(key),
                values,
                evictionPriority,
                toReturn);
    }

    /** Update existing signals. */
    private void updateExistingSignals(
            ByteBuffer key,
            EvictionPriority evictionPriority,
            Map<ByteBuffer, Set<DBProtectedSignal>> current,
            UpdateOutput toReturn) {
        if (!current.containsKey(key)) {
            sLogger.v("No existing signals found for key, skipping existing signal updates");
            return;
        }
        Set<DBProtectedSignal> currentSignals = current.get(key);
        for (DBProtectedSignal currentSignal : currentSignals) {
            if (!currentSignal.getEvictionPriority().equals(evictionPriority)) {
                // TODO: b/408444491 - Eliminate ID overwrite and existing signal remove after
                //                     changing ProtectedSignalsDao to use upserts.
                DBProtectedSignal.Builder updatedSignalBuilder =
                        currentSignal.toBuilder().setId(null).setEvictionPriority(evictionPriority);
                toReturn.getToAdd().add(updatedSignalBuilder);
                toReturn.getToRemove().add(currentSignal);
            }
        }
    }

    /** Add new signals. */
    private void addSignals(
            byte[] key, JSONArray values, EvictionPriority evictionPriority, UpdateOutput toReturn)
            throws JSONException {
        for (int i = 0; i < values.length(); i++) {
            DBProtectedSignal.Builder newSignalBuilder =
                    DBProtectedSignal.builder()
                            .setKey(key)
                            .setValue(UpdateProcessorUtils.decodeValue(APPEND, values.getString(i)))
                            .setEvictionPriority(evictionPriority);
            toReturn.getToAdd().add(newSignalBuilder);
        }
    }
}

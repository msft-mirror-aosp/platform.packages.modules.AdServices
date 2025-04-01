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

package com.android.adservices.service.signals.updateprocessors.putifnotpresent;

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
 * V1 implementation of the PutIfNotPresent update processor. Uses the following update schema:
 *
 * <pre>
 * {
 *   "put_if_not_present": {
 *     <strong>[Key]</strong>: {
 *       "value": <strong>[Value]</strong>,
 *       "eviction_priority": <strong>[Eviction Priority]</strong>
 *     }
 *     ... // additional signals
 *   }
 * }
 * </pre>
 */
public class PutIfNotPresentV1 extends PutIfNotPresent {
    private static final String VALUE = "value";

    private final EvictionPriorityHandler mEvictionPriorityHandler;

    public PutIfNotPresentV1(EvictionPriorityHandler evictionPriorityHandler) {
        mEvictionPriorityHandler = evictionPriorityHandler;
    }

    @Override
    protected void processKey(
            ByteBuffer key,
            Object update,
            Map<ByteBuffer, Set<DBProtectedSignal>> current,
            UpdateOutput toReturn,
            UpdateSignalsProcessReportedLogger updateSignalsProcessReportedLogger)
            throws JSONException {
        JSONObject updateObject =
                UpdateProcessorUtils.validateAndCastToJSONObject(PUT_IF_NOT_PRESENT, update);
        String value = updateObject.getString(VALUE);
        EvictionPriority evictionPriority =
                mEvictionPriorityHandler.getEvictionPriority(
                        key, updateObject, updateSignalsProcessReportedLogger);

        UpdateProcessorUtils.touchKey(key, toReturn.getKeysTouched());
        // Add the new signal if nothing exists under the key
        if (!current.containsKey(key)) {
            DBProtectedSignal.Builder newSignalBuilder =
                    DBProtectedSignal.builder()
                            .setKey(UpdateProcessorUtils.getByteArrayFromBuffer(key))
                            .setValue(UpdateProcessorUtils.decodeValue(PUT_IF_NOT_PRESENT, value))
                            .setEvictionPriority(evictionPriority);
            toReturn.getToAdd().add(newSignalBuilder);
        }
    }
}

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

package com.android.adservices.service.signals.updateprocessors.put;

import com.android.adservices.data.signals.DBProtectedSignal;
import com.android.adservices.service.signals.updateprocessors.UpdateOutput;
import com.android.adservices.service.signals.updateprocessors.UpdateProcessor;
import com.android.adservices.service.signals.updateprocessors.UpdateProcessorUtils;

import org.json.JSONException;
import org.json.JSONObject;

import java.nio.ByteBuffer;
import java.util.Iterator;
import java.util.Map;
import java.util.Set;

/**
 * Adds a new signal, overwriting any existing signals with the same key.
 *
 * <p>The value for this is a JSON object where the JSON keys are base 64 strings corresponding to
 * the signal key to put for and the values are base 64 string corresponding to the value to put.
 */
public abstract class Put implements UpdateProcessor {
    public static final String PUT = "put";

    @Override
    public UpdateOutput processUpdates(
            Object updates, Map<ByteBuffer, Set<DBProtectedSignal>> current) throws JSONException {
        UpdateOutput toReturn = new UpdateOutput();
        JSONObject updatesObject = UpdateProcessorUtils.validateAndCastToJSONObject(PUT, updates);
        for (Iterator<String> iter = updatesObject.keys(); iter.hasNext(); ) {
            String stringKey = iter.next();
            ByteBuffer key = UpdateProcessorUtils.decodeKey(PUT, stringKey);
            processKey(key, updatesObject.get(stringKey), current, toReturn);
        }
        return toReturn;
    }

    /** Process the update for one key. */
    protected abstract void processKey(
            ByteBuffer key,
            Object update,
            Map<ByteBuffer, Set<DBProtectedSignal>> current,
            UpdateOutput toReturn)
            throws JSONException;
}

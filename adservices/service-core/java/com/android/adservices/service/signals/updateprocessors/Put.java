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

package com.android.adservices.service.signals.updateprocessors;

import com.android.adservices.data.signals.DBProtectedSignal;

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
public class Put implements UpdateProcessor {

    private static final String PUT = "put";

    @Override
    public String getName() {
        return PUT;
    }

    @Override
    public UpdateOutput processUpdates(
            Object updates, Map<ByteBuffer, Set<DBProtectedSignal>> current) throws JSONException {
        UpdateOutput toReturn = new UpdateOutput();
        JSONObject updatesObject = UpdateProcessorUtils.castToJSONObject(PUT, updates);
        for (Iterator<String> iter = updatesObject.keys(); iter.hasNext(); ) {
            String stringKey = iter.next();
            ByteBuffer key = UpdateProcessorUtils.decodeKey(PUT, stringKey);
            processKey(key, updatesObject.getString(stringKey), current, toReturn);
        }
        return toReturn;
    }

    /** Process the update for one key. */
    private void processKey(
            ByteBuffer key,
            String value,
            Map<ByteBuffer, Set<DBProtectedSignal>> current,
            UpdateOutput toReturn) {
        UpdateProcessorUtils.touchKey(key, toReturn.getKeysTouched());
        // Remove any existing signals for the key
        if (current.containsKey(key)) {
            toReturn.getToRemove().addAll(current.get(key));
        }
        // Add the new signal
        DBProtectedSignal.Builder newSignalBuilder =
                DBProtectedSignal.builder()
                        .setKey(UpdateProcessorUtils.getByteArrayFromBuffer(key))
                        .setValue(UpdateProcessorUtils.decodeValue(PUT, value));
        toReturn.getToAdd().add(newSignalBuilder);
    }
}

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

package com.android.adservices.service.signals.updateprocessors.remove;

import com.android.adservices.data.signals.DBProtectedSignal;
import com.android.adservices.service.signals.updateprocessors.UpdateOutput;
import com.android.adservices.service.signals.updateprocessors.UpdateProcessorUtils;
import com.android.adservices.service.stats.pas.UpdateSignalsProcessReportedLogger;

import org.json.JSONArray;
import org.json.JSONException;

import java.nio.ByteBuffer;
import java.util.Map;
import java.util.Set;

/**
 * V0 implementation of the Remove update processor. Uses the following update schema:
 *
 * <pre>
 * {
 *   "remove": [
 *     <strong>[Key]</strong>,
 *     ... // additional signals
 *   ]
 * }
 * </pre>
 */
public class RemoveV0 extends Remove {

    @Override
    public UpdateOutput processUpdates(
            Object updates,
            Map<ByteBuffer, Set<DBProtectedSignal>> current,
            UpdateSignalsProcessReportedLogger mUpdateSignalsProcessReportedLogger)
            throws JSONException {
        UpdateOutput toReturn = new UpdateOutput();
        JSONArray updatesArray = UpdateProcessorUtils.validateAndCastToJSONArray(REMOVE, updates);
        for (int i = 0; i < updatesArray.length(); i++) {
            ByteBuffer key = UpdateProcessorUtils.decodeKey(REMOVE, updatesArray.getString(i));
            UpdateProcessorUtils.touchKey(key, toReturn.getKeysTouched());
            // Remove all signals for the key
            if (current.containsKey(key)) {
                toReturn.getToRemove().addAll(current.get(key));
            }
        }
        return toReturn;
    }
}

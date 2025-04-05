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

package com.android.adservices.service.signals.updateprocessors.put;

import com.android.adservices.data.signals.DBProtectedSignal;
import com.android.adservices.service.signals.updateprocessors.UpdateOutput;
import com.android.adservices.service.signals.updateprocessors.UpdateProcessorUtils;
import com.android.adservices.service.stats.pas.UpdateSignalsProcessReportedLogger;

import java.nio.ByteBuffer;
import java.util.Map;
import java.util.Set;

/**
 * V0 implementation of the Put update processor. Uses the following update schema:
 *
 * <pre>
 * {
 *   "put": {
 *     <strong>[Key]</strong>: <strong>[Value]</strong>,
 *     ... // additional signals
 *   }
 * }
 * </pre>
 */
public class PutV0 extends Put {

    @Override
    protected void processKey(
            ByteBuffer key,
            Object update,
            Map<ByteBuffer, Set<DBProtectedSignal>> current,
            UpdateOutput toReturn,
            UpdateSignalsProcessReportedLogger updateSignalsProcessReportedLogger) {
        String value = UpdateProcessorUtils.validateAndCastToString(PUT, update);
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

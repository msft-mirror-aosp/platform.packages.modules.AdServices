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

package com.android.adservices.service.signals.updateprocessors.putifnotpresent;

import com.android.adservices.data.signals.DBProtectedSignal;
import com.android.adservices.service.signals.updateprocessors.UpdateOutput;
import com.android.adservices.service.signals.updateprocessors.UpdateProcessorUtils;

import java.nio.ByteBuffer;
import java.util.Map;
import java.util.Set;

/**
 * V0 implementation of the PutIfNotPresent update processor. Uses the following update schema:
 *
 * <pre>
 * {
 *   "put_if_not_present": {
 *     <strong>[Key]</strong>: <strong>[Value]</strong>,
 *     ... // additional signals
 *   }
 * }
 * </pre>
 */
public class PutIfNotPresentV0 extends PutIfNotPresent {

    @Override
    protected void processKey(
            ByteBuffer key,
            Object update,
            Map<ByteBuffer, Set<DBProtectedSignal>> current,
            UpdateOutput toReturn) {
        String value = UpdateProcessorUtils.validateAndCastToString(PUT_IF_NOT_PRESENT, update);
        UpdateProcessorUtils.touchKey(key, toReturn.getKeysTouched());
        // Add the new signal if nothing exists under the key
        if (!current.containsKey(key)) {
            DBProtectedSignal.Builder newSignalBuilder =
                    DBProtectedSignal.builder()
                            .setKey(UpdateProcessorUtils.getByteArrayFromBuffer(key))
                            .setValue(UpdateProcessorUtils.decodeValue(PUT_IF_NOT_PRESENT, value));
            toReturn.getToAdd().add(newSignalBuilder);
        }
    }
}

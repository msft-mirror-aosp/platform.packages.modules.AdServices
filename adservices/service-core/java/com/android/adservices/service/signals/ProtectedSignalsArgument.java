/*
 * Copyright (C) 2024 The Android Open Source Project
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
package com.android.adservices.service.signals;

import com.android.adservices.service.js.JSScriptArgument;

import com.google.common.collect.ImmutableList;

import org.json.JSONException;

import java.util.List;
import java.util.Map;

/** Marshals a buyer's protected signals as {@link JSScriptArgument} for the JS sandbox */
public interface ProtectedSignalsArgument {
    /**
     * Convert a buyer's {@link ProtectedSignal} and maximum payload size allowed to a list of
     * {@link JSScriptArgument} in order to pass to a JS isolate.
     *
     * @param rawSignals A map of the buyer's {@link ProtectedSignal}, where each key is the base64
     *     encoded key for that signal, and the values are all the signals with that key.
     * @param maxSizeInBytes The max payload size for the buyer.
     * @return A {@link JSScriptArgument} list.
     * @throws JSONException If the JSON we created isn't valid JSON.
     */
    ImmutableList<JSScriptArgument> getArgumentsFromRawSignalsAndMaxSize(
            Map<String, List<ProtectedSignal>> rawSignals, int maxSizeInBytes) throws JSONException;
}

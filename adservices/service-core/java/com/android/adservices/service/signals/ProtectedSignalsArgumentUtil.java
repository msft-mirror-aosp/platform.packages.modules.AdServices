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

package com.android.adservices.service.signals;

import static com.android.adservices.service.js.JSScriptArgument.numericArg;

import androidx.annotation.VisibleForTesting;

import com.android.adservices.service.js.JSScriptArgument;

import com.google.common.collect.ImmutableList;

import org.json.JSONException;

import java.util.Base64;
import java.util.List;
import java.util.Map;

/** Creates an {@link JSScriptArgument} for raw protected signals */
public class ProtectedSignalsArgumentUtil {

    public static final String VALUE_KEY_NAME = "val";
    public static final String CREATION_TIME_KEY_NAME = "time";
    public static final String PACKAGE_KEY_NAME = "app";
    public static final String HEX = "%02X";

    public static final String INVALID_BASE64_SIGNAL = "Signals have invalid base64 key or values";

    /**
     * @param rawSignals map of {@link ProtectedSignal}, where map key is the base64 encoded key for
     *     signals
     * @return an {@link JSScriptArgument}
     */
    @VisibleForTesting
    static JSScriptArgument asScriptArgument(
            String name, Map<String, List<ProtectedSignal>> rawSignals) throws JSONException {
        return JSScriptArgument.jsonArrayArg(name, marshalToJson(rawSignals));
    }

    @VisibleForTesting
    static String marshalToJson(Map<String, List<ProtectedSignal>> rawSignals) {
        /**
         * We analyzed various JSON building approaches, turns out using StringBuilder is orders of
         * magnitude faster. Also, given the signals have base64 encoded strings initially fetched
         * as JSON, using string builder is also a safe choice.
         */
        StringBuilder sb = new StringBuilder();
        sb.append("[");
        for (Map.Entry<String, List<ProtectedSignal>> signalsPerKey : rawSignals.entrySet()) {
            serializeEntryToJson(sb, signalsPerKey);
            sb.append(",");
        }
        if (rawSignals.size() > 0) {
            // Remove extra ','
            sb.deleteCharAt(sb.length() - 1);
        }
        sb.append("]");

        return sb.toString();
    }

    private static void serializeEntryToJson(
            StringBuilder jsonBuilder, Map.Entry<String, List<ProtectedSignal>> entry) {
        jsonBuilder.append("{");
        jsonBuilder.append("\"").append(validateAndSerializeBase64(entry.getKey())).append("\":[");

        List<ProtectedSignal> protectedSignals = entry.getValue();
        for (int i = 0; i < protectedSignals.size(); i++) {
            ProtectedSignal protectedSignal = protectedSignals.get(i);
            jsonBuilder.append("{");
            jsonBuilder
                    .append("\"" + VALUE_KEY_NAME + "\":\"")
                    .append(validateAndSerializeBase64(protectedSignal.getBase64EncodedValue()))
                    .append("\",");
            jsonBuilder
                    .append("\"" + CREATION_TIME_KEY_NAME + "\":")
                    .append(protectedSignal.getCreationTime().getEpochSecond())
                    .append(",");
            jsonBuilder
                    .append("\"" + PACKAGE_KEY_NAME + "\":\"")
                    .append(protectedSignal.getPackageName())
                    .append("\"");
            jsonBuilder.append("}");
            if (i < protectedSignals.size() - 1) {
                jsonBuilder.append(",");
            }
        }

        jsonBuilder.append("]");
        jsonBuilder.append("}");
    }

    // TODO(b/294900378) Avoid second serialization
    @VisibleForTesting
    public static String validateAndSerializeBase64(String base64String) {
        try {
            byte[] bytes = Base64.getDecoder().decode(base64String);
            StringBuilder sb = new StringBuilder(bytes.length * 2);
            for (byte b : bytes) {
                sb.append(String.format(HEX, b));
            }
            return sb.toString();
        } catch (IllegalArgumentException e) {
            throw new IllegalStateException(INVALID_BASE64_SIGNAL);
        }
    }

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
    static ImmutableList<JSScriptArgument> getArgumentsFromRawSignalsAndMaxSize(
            Map<String, List<ProtectedSignal>> rawSignals, int maxSizeInBytes)
            throws JSONException {
        return ImmutableList.<JSScriptArgument>builder()
                .add(asScriptArgument(SignalsDriverLogicGenerator.SIGNALS_ARG_NAME, rawSignals))
                .add(
                        numericArg(
                                SignalsDriverLogicGenerator.MAX_SIZE_BYTES_ARG_NAME,
                                maxSizeInBytes))
                .build();
    }
}

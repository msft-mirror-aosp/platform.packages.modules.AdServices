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

import androidx.annotation.VisibleForTesting;

import com.android.adservices.service.js.JSScriptArgument;

import java.util.List;
import java.util.Map;

/** Creates an {@link JSScriptArgument} for raw protected signals */
public class ProtectedSignalsArgumentUtil {

    public static final String SIGNAL_FIELD_NAME = "signals";

    public static final String VALUE_KEY_NAME = "val";
    public static final String CREATION_TIME_KEY_NAME = "time";
    public static final String PACKAGE_KEY_NAME = "app";

    /**
     * @param rawSignals map of {@link ProtectedSignal}, where map key is the base64 encoded key for
     *     signals
     * @return an {@link JSScriptArgument}
     */
    public static JSScriptArgument asScriptArgument(Map<String, List<ProtectedSignal>> rawSignals) {
        return JSScriptArgument.stringArg(SIGNAL_FIELD_NAME, marshalToJson(rawSignals));
    }

    @VisibleForTesting
    static String marshalToJson(Map<String, List<ProtectedSignal>> rawSignals) {

        /**
         * We analyzed various JSON building approaches, turns out using StringBuilder is
         * orders of maginutude faster. Also, given the signals have base64 encoded strings
         * initially fetched as JSON, using string builder is also a safe choice.
         */
        StringBuilder sb = new StringBuilder();
        sb.append("[");
        for (Map.Entry<String, List<ProtectedSignal>> signalsPerKey : rawSignals.entrySet()) {
            String json = serializeEntryToJson(signalsPerKey);
            sb.append(json);
            sb.append(",");
        }
        if (rawSignals.size() > 0) {
            // Remove extra ','
            sb.deleteCharAt(sb.length() - 1);
        }
        sb.append("]");

        return sb.toString();
    }

    @VisibleForTesting
    static String serializeEntryToJson(Map.Entry<String, List<ProtectedSignal>> entry) {
        StringBuilder jsonBuilder = new StringBuilder();
        jsonBuilder.append("{");
        jsonBuilder.append("\"").append(entry.getKey()).append("\":[");

        List<ProtectedSignal> protectedSignals = entry.getValue();
        for (int i = 0; i < protectedSignals.size(); i++) {
            ProtectedSignal protectedSignal = protectedSignals.get(i);
            jsonBuilder.append("{");
            jsonBuilder
                    .append("\"" + VALUE_KEY_NAME + "\":\"")
                    .append(protectedSignal.getValue())
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

        return jsonBuilder.toString();
    }
}

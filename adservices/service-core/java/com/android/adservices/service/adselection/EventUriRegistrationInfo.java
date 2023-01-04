/*
 * Copyright (C) 2022 The Android Open Source Project
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

package com.android.adservices.service.adselection;

import static com.android.adservices.service.adselection.ReportImpressionScriptEngine.EVENT_TYPE_ARG_NAME;
import static com.android.adservices.service.adselection.ReportImpressionScriptEngine.EVENT_URI_ARG_NAME;
import static com.android.adservices.service.common.JsonUtils.getStringFromJson;

import android.annotation.NonNull;
import android.net.Uri;

import com.android.adservices.LogUtil;

import com.google.auto.value.AutoValue;

import org.json.JSONException;
import org.json.JSONObject;

/** POJO to represent an {@code EventType:Uri} pairing */
@AutoValue
public abstract class EventUriRegistrationInfo {
    public static String EXPECTED_STRUCTURE_MISMATCH =
            "EventUriRegistrationInfo does not match expected structure!";

    /** @return the type of event (e.g., click, view, etc.) */
    @NonNull
    public abstract String getEventType();

    /** @return Uri to be used during event reporting */
    @NonNull
    public abstract Uri getEventUri();

    /**
     * Deserializes a {@link EventUriRegistrationInfo} from a {@code JSONObject}
     *
     * @throws IllegalArgumentException if {@code JSONObject} fails to meet these conditions: 1.
     *     {@code JSONObject} has exactly 2 keys with String values named: {@link
     *     ReportImpressionScriptEngine#EVENT_TYPE_ARG_NAME} and {@link
     *     ReportImpressionScriptEngine#EVENT_URI_ARG_NAME} 2. The value of key {@link
     *     ReportImpressionScriptEngine#EVENT_URI_ARG_NAME} properly parses into a Uri
     */
    @NonNull
    public static EventUriRegistrationInfo fromJson(@NonNull JSONObject jsonObject)
            throws IllegalArgumentException {
        try {
            return builder()
                    .setEventType(getStringFromJson(jsonObject, EVENT_TYPE_ARG_NAME))
                    .setEventUri(Uri.parse(getStringFromJson(jsonObject, EVENT_URI_ARG_NAME)))
                    .build();
        } catch (JSONException e) {
            LogUtil.v(String.format("Unexpected object structure: %s", jsonObject));
            throw new IllegalArgumentException(EXPECTED_STRUCTURE_MISMATCH);
        }
    }

    /** @return generic builder */
    @NonNull
    public static EventUriRegistrationInfo.Builder builder() {
        return new AutoValue_EventUriRegistrationInfo.Builder();
    }

    /** Builder class for a {@link EventUriRegistrationInfo} object. */
    @AutoValue.Builder
    public abstract static class Builder {
        /** Sets the eventType for the {@link EventUriRegistrationInfo} object. */
        @NonNull
        public abstract EventUriRegistrationInfo.Builder setEventType(@NonNull String eventType);

        /** Sets the eventUri for the {@link EventUriRegistrationInfo} object. */
        @NonNull
        public abstract EventUriRegistrationInfo.Builder setEventUri(@NonNull Uri eventUri);

        /** Builds a {@link EventUriRegistrationInfo} object. */
        @NonNull
        public abstract EventUriRegistrationInfo build();
    }
}

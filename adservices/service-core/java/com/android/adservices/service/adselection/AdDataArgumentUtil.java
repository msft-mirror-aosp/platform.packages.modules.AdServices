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

import static com.android.adservices.service.js.JSScriptArgument.jsonArg;
import static com.android.adservices.service.js.JSScriptArgument.recordArg;
import static com.android.adservices.service.js.JSScriptArgument.stringArg;

import android.adservices.common.AdData;
import android.net.Uri;

import com.android.adservices.data.common.DBAdData;
import com.android.adservices.service.js.JSScriptArgument;
import com.android.adservices.service.js.JSScriptRecordArgument;
import com.android.internal.annotations.VisibleForTesting;

import org.json.JSONException;
import org.json.JSONObject;

/**
 * A utility class to support the conversion from {@link AdData} to {@link JSScriptArgument} and to
 * parse JS result string into {@link AdData}.
 */
public class AdDataArgumentUtil {
    @VisibleForTesting static final String RENDER_URI_FIELD_NAME = "render_uri";
    @VisibleForTesting static final String METADATA_FIELD_NAME = "metadata";

    // No instance of this class is supposed to be created
    private AdDataArgumentUtil() {}

    /**
     * @return An {@link AdData} instance built reading the content of the provided JSON object.
     * @throws IllegalArgumentException If the provided JSON doesn't contain a `render_uri` and a
     *     `metadata` field with valid content.
     */
    public static AdData parseJsonResponse(JSONObject jsonObject) throws IllegalArgumentException {
        try {
            return new AdData.Builder()
                    .setRenderUri(Uri.parse(jsonObject.getString(RENDER_URI_FIELD_NAME)))
                    .setMetadata(jsonObject.getJSONObject(METADATA_FIELD_NAME).toString())
                    .build();
        } catch (JSONException e) {
            throw new IllegalArgumentException(
                    "Invalid content for '"
                            + RENDER_URI_FIELD_NAME
                            + "' or '"
                            + METADATA_FIELD_NAME
                            + "' fields",
                    e);
        }
    }

    /**
     * @return a {@link JSScriptArgument} with name {@code name} for the JSON object representing
     *     this ad.
     * @throws JSONException If the content of {@link AdData#getMetadata()} of the wrapped {@code
     *     AdData} is not valid JSON.
     */
    public static JSScriptArgument asScriptArgument(String name, AdData adData)
            throws JSONException {
        return recordArg(
                name,
                stringArg(RENDER_URI_FIELD_NAME, adData.getRenderUri().toString()),
                jsonArg(METADATA_FIELD_NAME, adData.getMetadata()));
    }

    /**
     * Returns a record arg represents a {@link DBAdData}.
     *
     * @throws JSONException If the content of {@link AdData#getMetadata()} of the wrapped {@code
     *     AdData} is not valid JSON.
     */
    public static JSScriptRecordArgument asRecordArgument(String name, DBAdData adData)
            throws JSONException {
        return recordArg(
                name,
                stringArg(RENDER_URI_FIELD_NAME, adData.getRenderUri().toString()),
                jsonArg(METADATA_FIELD_NAME, adData.getMetadata()));
    }
}

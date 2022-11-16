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

import static com.android.adservices.service.js.JSScriptArgument.numericArg;
import static com.android.adservices.service.js.JSScriptArgument.recordArg;

import com.android.adservices.service.js.JSScriptArgument;

import org.json.JSONException;
import org.json.JSONObject;

/**
 * A wrapper class for {@code AdSelectionId} and {@code Bid} pair to support the conversion to JS
 * Script parameter and from JS result string.
 */
public class SelectAdsFromOutcomesArgument {
    static final String ID_FIELD_NAME = "id";
    static final String BID_FIELD_NAME = "bid";

    /** Parses Json object for ad selection id and bid to {@code Pair< Long, Double >} */
    public static AdSelectionIdWithBidAndRenderUri parseJsonResponse(JSONObject jsonObject) {
        try {
            return AdSelectionIdWithBidAndRenderUri.builder()
                    .setAdSelectionId(jsonObject.getLong(ID_FIELD_NAME))
                    .setBid(jsonObject.getDouble(BID_FIELD_NAME))
                    .build();
        } catch (JSONException e) {
            throw new IllegalArgumentException("Invalid value for ad selection id, bid pair", e);
        }
    }

    /** Converts {@code Pair< Long, Double >} object to Json object */
    public static JSScriptArgument asScriptArgument(
            String name, AdSelectionIdWithBidAndRenderUri adSelectionIdWithBidAndRenderUri) {
        return recordArg(
                name,
                numericArg(ID_FIELD_NAME, adSelectionIdWithBidAndRenderUri.getAdSelectionId()),
                numericArg(BID_FIELD_NAME, adSelectionIdWithBidAndRenderUri.getBid()));
    }
}

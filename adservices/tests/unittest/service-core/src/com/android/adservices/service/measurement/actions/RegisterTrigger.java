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

package com.android.adservices.service.measurement.actions;

import static com.android.adservices.service.measurement.E2ETest.getAttributionSource;
import static com.android.adservices.service.measurement.E2ETest.getUriToResponseHeadersMap;

import android.adservices.measurement.RegistrationRequest;
import android.content.AttributionSource;
import android.net.Uri;

import com.android.adservices.service.measurement.E2ETest.TestFormatJsonMapping;

import org.json.JSONException;
import org.json.JSONObject;

import java.util.List;
import java.util.Map;

public final class RegisterTrigger implements Action {
    public final RegistrationRequest mRegistrationRequest;
    public final Map<String, List<Map<String, List<String>>>> mUriToResponseHeadersMap;
    public final long mTimestamp;

    public RegisterTrigger(JSONObject obj) throws JSONException {
        JSONObject regParamsJson = obj.getJSONObject(
                TestFormatJsonMapping.REGISTRATION_REQUEST_KEY);

        AttributionSource attributionSource = getAttributionSource(
                regParamsJson.optString(TestFormatJsonMapping.ATTRIBUTION_SOURCE_KEY));

        mRegistrationRequest =
                new RegistrationRequest.Builder()
                        .setRegistrationType(RegistrationRequest.REGISTER_TRIGGER)
                        .setRegistrationUri(
                                Uri.parse(
                                        regParamsJson.getString(
                                                TestFormatJsonMapping.REGISTRATION_URI_KEY)))
                        .setAdIdPermissionGranted(
                                regParamsJson.optBoolean(
                                        TestFormatJsonMapping.IS_ADID_PERMISSION_GRANTED_KEY, true))
                        .setPackageName(attributionSource.getPackageName())
                        .build();

        mUriToResponseHeadersMap = getUriToResponseHeadersMap(obj);
        mTimestamp = obj.getLong(TestFormatJsonMapping.TIMESTAMP_KEY);
    }

    public long getComparable() {
        return mTimestamp;
    }
}

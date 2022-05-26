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
package com.android.adservices.service.measurement.registration;

import android.adservices.measurement.RegistrationRequest;
import android.annotation.NonNull;
import android.net.Uri;

import com.android.adservices.LogUtil;

import org.json.JSONArray;
import org.json.JSONException;
import org.json.JSONObject;

import java.io.IOException;
import java.net.HttpURLConnection;
import java.net.MalformedURLException;
import java.net.URL;
import java.net.URLConnection;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;


/**
 * Download and decode Trigger registration.
 *
 * @hide
 */
public class TriggerFetcher {
    /**
     * Provided a testing hook.
     */
    public @NonNull URLConnection openUrl(@NonNull URL url) throws IOException {
        return url.openConnection();
    }

    private static void parseEventTrigger(
            @NonNull String text,
            TriggerRegistration.Builder result) throws JSONException {
        final JSONArray array = new JSONArray(text);
        if (array.length() != 1) {
            throw new JSONException("Expected list with 1 item");
        }
        final JSONObject inside = array.getJSONObject(0);
        if (inside.has(EventTriggerContract.TRIGGER_DATA)
                && !inside.isNull(EventTriggerContract.TRIGGER_DATA)) {
            result.setTriggerData(inside.getLong(EventTriggerContract.TRIGGER_DATA));
        }
        if (inside.has(EventTriggerContract.PRIORITY)
                && !inside.isNull(EventTriggerContract.PRIORITY)) {
            result.setTriggerPriority(inside.getLong(EventTriggerContract.PRIORITY));
        }
        if (inside.has(EventTriggerContract.DEDUPLICATION_KEY)
                && !inside.isNull(EventTriggerContract.DEDUPLICATION_KEY)) {
            result.setDeduplicationKey(inside.getLong(EventTriggerContract.DEDUPLICATION_KEY));
        }
    }

    private static boolean parseTrigger(
            @NonNull Uri topOrigin,
            @NonNull Uri reportingOrigin,
            @NonNull Map<String, List<String>> headers,
            @NonNull List<TriggerRegistration> addToResults) {
        boolean additionalResult = false;
        TriggerRegistration.Builder result = new TriggerRegistration.Builder();
        result.setTopOrigin(topOrigin);
        result.setReportingOrigin(reportingOrigin);
        List<String> field;
        field = headers.get("Attribution-Reporting-Register-Event-Trigger");
        if (field != null) {
            if (field.size() != 1) {
                LogUtil.d("Expected one event trigger!");
                return false;
            }
            try {
                parseEventTrigger(field.get(0), result);
            } catch (JSONException e) {
                LogUtil.d("Invalid JSON %s", e);
                return false;
            }
            additionalResult = true;
        }
        field = headers.get("Attribution-Reporting-Register-Aggregatable-Trigger-Data");
        if (field != null) {
            if (field.size() != 1) {
                LogUtil.d("Expected one aggregate trigger data!");
                return false;
            }
            // Parses in aggregate trigger data. additionalResult will be false until then.
            result.setAggregateTriggerData(field.get(0));
            additionalResult = true;
        }
        field = headers.get("Attribution-Reporting-Register-Aggregatable-Values");
        if (field != null) {
            if (field.size() != 1) {
                LogUtil.d("Expected one aggregatable values!");
                return false;
            }
            // Parses in aggregate values. additionalResult will be false until then.
            result.setAggregateValues(field.get(0));
            additionalResult = true;
        }
        if (additionalResult) {
            addToResults.add(result.build());
            return true;
        }
        return false;
    }

    private void fetchTrigger(
            @NonNull Uri topOrigin,
            @NonNull Uri target,
            boolean initialFetch,
            @NonNull List<TriggerRegistration> registrationsOut) {
        // Require https.
        if (!target.getScheme().equals("https")) {
            return;
        }
        URL url;
        try {
            url = new URL(target.toString());
        } catch (MalformedURLException e) {
            LogUtil.d("Malformed registration target URL %s", e);
            return;
        }
        HttpURLConnection urlConnection;
        try {
            urlConnection = (HttpURLConnection) openUrl(url);
        } catch (IOException e) {
            LogUtil.d("Failed to open registration target URL %s", e);
            return;
        }
        try {
            urlConnection.setRequestMethod("POST");
            urlConnection.setInstanceFollowRedirects(false);
            Map<String, List<String>> headers = urlConnection.getHeaderFields();

            int responseCode = urlConnection.getResponseCode();
            LogUtil.d("Response code = " + responseCode);

            if (!ResponseBasedFetcher.isRedirect(responseCode)
                    && !ResponseBasedFetcher.isSuccess(responseCode)) {
                return;
            }

            final boolean parsed = parseTrigger(topOrigin, target, headers, registrationsOut);
            if (!parsed && initialFetch) {
                LogUtil.d("Failed to parse initial fetch");
                return;
            }

            ArrayList<Uri> redirects = new ArrayList();
            ResponseBasedFetcher.parseRedirects(initialFetch, headers, redirects);
            for (Uri redirect : redirects) {
                fetchTrigger(topOrigin, redirect, false, registrationsOut);
            }
        } catch (IOException e) {
            LogUtil.d("Failed to get registration response %s", e);
            return;
        } finally {
            if (urlConnection != null) {
                urlConnection.disconnect();
            }
        }
    }

    /**
     * Fetch a trigger type registration.
     */
    public boolean fetchTrigger(@NonNull RegistrationRequest request,
            @NonNull List<TriggerRegistration> out) {
        if (request.getRegistrationType()
                != RegistrationRequest.REGISTER_TRIGGER) {
            throw new IllegalArgumentException("Expected trigger registration");
        }
        fetchTrigger(
                request.getTopOriginUri(),
                request.getRegistrationUri(),
                true, out);
        return !out.isEmpty();
    }

    private interface EventTriggerContract {
        String TRIGGER_DATA = "trigger_data";
        String PRIORITY = "priority";
        String DEDUPLICATION_KEY = "deduplication_key";
    }
}

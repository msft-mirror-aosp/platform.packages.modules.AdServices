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
 * Download and decode Response Based Registration
 *
 * @hide
 */
public class SourceFetcher {
    /**
     * Provided a testing hook.
     */
    public @NonNull URLConnection openUrl(@NonNull URL url) throws IOException {
        return url.openConnection();
    }

    private static void parseEventSource(
            @NonNull String text,
            SourceRegistration.Builder result) throws JSONException {
        JSONObject json = new JSONObject(text);
        result.setSourceEventId(json.getLong("source_event_id"));
        result.setDestination(Uri.parse(json.getString("destination")));
        if (json.has("expiry")) {
            result.setExpiry(json.getLong("expiry"));
        }
        if (json.has("priority")) {
            result.setSourcePriority(json.getLong("priority"));
        }
    }

    private static boolean parseSource(
            @NonNull Uri topOrigin,
            @NonNull Uri reportingOrigin,
            @NonNull Map<String, List<String>> headers,
            @NonNull List<SourceRegistration> addToResults) {
        boolean additionalResult = false;
        SourceRegistration.Builder result = new SourceRegistration.Builder();
        result.setTopOrigin(topOrigin);
        result.setReportingOrigin(reportingOrigin);
        List<String> field;
        field = headers.get("Attribution-Reporting-Register-Source");
        if (field != null) {
            if (field.size() != 1) {
                LogUtil.d("Expected one event source!");
                return false;
            }
            try {
                parseEventSource(field.get(0), result);
            } catch (JSONException e) {
                LogUtil.d("Invalid JSON");
                return false;
            }
            additionalResult = true;
        }
        field = headers.get("Attribution-Reporting-Register-Aggregatable-Source");
        if (field != null) {
            if (field.size() != 1) {
                LogUtil.d("Expected one aggregate source!");
                return false;
            }
            // TODO: Handle aggregates.
            additionalResult = true;
        }
        if (additionalResult) {
            SourceRegistration adding = result.build();
            if (addToResults.size() > 1
                    && !addToResults.get(0).getDestination().equals(adding.getDestination())) {
                LogUtil.d("Illegal change of destination");
                return false;
            }
            addToResults.add(adding);
        }
        return true;
    }

    private boolean fetchSource(
            @NonNull Uri topOrigin,
            @NonNull Uri referrer,
            @NonNull Uri target,
            @NonNull String sourceInfo,
            boolean initialFetch,
            @NonNull List<SourceRegistration> registrationsOut) {
        // Require https.
        if (!target.getScheme().equals("https")) {
            return false;
        }
        URL url;
        try {
            url = new URL(target.toString());
        } catch (MalformedURLException e) {
            LogUtil.d("Malformed registration target URL ", e);
            return false;
        }
        HttpURLConnection urlConnection;
        try {
            urlConnection = (HttpURLConnection) openUrl(url);
        } catch (IOException e) {
            LogUtil.d("Failed to open registation target URL", e);
            return false;
        }
        boolean success = true;
        try {
            urlConnection.setRequestMethod("POST");
            urlConnection.setRequestProperty("Referer", referrer.toString());
            urlConnection.setRequestProperty("Attribution-Reporting-Source-Info", sourceInfo);
            urlConnection.setInstanceFollowRedirects(false);
            Map<String, List<String>> headers = urlConnection.getHeaderFields();

            int responseCode = urlConnection.getResponseCode();
            if (!ResponseBasedFetcher.isRedirect(responseCode)
                    && !ResponseBasedFetcher.isSuccess(responseCode)) {
                success = false;
            }

            if (!parseSource(topOrigin, target, headers, registrationsOut)) {
                success = false;
            }

            ArrayList<Uri> redirects = new ArrayList();
            ResponseBasedFetcher.parseRedirects(initialFetch, headers, redirects);
            for (Uri redirect : redirects) {
                if (!fetchSource(
                        topOrigin, target, redirect, sourceInfo, false, registrationsOut)) {
                    success = false;
                }
            }
            return success;
        } catch (IOException e) {
            LogUtil.d("Failed to get registation response", e);
            return false;
        } finally {
            urlConnection.disconnect();
        }
    }

    /**
     * Fetch an attribution source type registration.
     */
    public boolean fetchSource(@NonNull RegistrationRequest request,
                               @NonNull List<SourceRegistration> out) {
        if (request.getRegistrationType()
                != RegistrationRequest.REGISTER_SOURCE) {
            throw new IllegalArgumentException("Expected source registration");
        }
        return fetchSource(
                request.getTopOriginUri(),
                request.getReferrerUri(),
                request.getRegistrationUri(),
                request.getInputEvent() == null ? "event" : "navigation",
                true, out);
    }
}

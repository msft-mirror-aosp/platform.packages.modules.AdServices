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

import static com.android.adservices.service.measurement.PrivacyParams.MAX_REPORTING_REGISTER_SOURCE_EXPIRATION_IN_SECONDS;
import static com.android.adservices.service.measurement.PrivacyParams.MIN_REPORTING_REGISTER_SOURCE_EXPIRATION_IN_SECONDS;

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
        final JSONObject json = new JSONObject(text);
        final boolean hasRequiredParams = json.has(EventSourceContract.SOURCE_EVENT_ID)
                        && json.has(EventSourceContract.DESTINATION);
        if (!hasRequiredParams) {
            throw new JSONException(
                    String.format("Expected both %s and %s",
                            EventSourceContract.SOURCE_EVENT_ID,
                            EventSourceContract.DESTINATION));
        }

        result.setSourceEventId(json.getLong(EventSourceContract.SOURCE_EVENT_ID));
        result.setDestination(Uri.parse(json.getString(EventSourceContract.DESTINATION)));
        if (json.has(EventSourceContract.EXPIRY) && !json.isNull(EventSourceContract.EXPIRY)) {
            setExpiry(json.getLong(EventSourceContract.EXPIRY), result);
        }
        if (json.has(EventSourceContract.PRIORITY) && !json.isNull(EventSourceContract.PRIORITY)) {
            result.setSourcePriority(json.getLong(EventSourceContract.PRIORITY));
        }
        // This "filter_data" field is used to generate aggregate report.
        if (json.has("filter_data") && !json.isNull("filter_data")) {
            result.setAggregateFilterData(json.getJSONObject("filter_data").toString());
        }
    }

    private static void setExpiry(long expiry, SourceRegistration.Builder result) {
        if (expiry < MIN_REPORTING_REGISTER_SOURCE_EXPIRATION_IN_SECONDS) {
            result.setExpiry(MIN_REPORTING_REGISTER_SOURCE_EXPIRATION_IN_SECONDS);
        } else if (expiry > MAX_REPORTING_REGISTER_SOURCE_EXPIRATION_IN_SECONDS) {
            result.setExpiry(MAX_REPORTING_REGISTER_SOURCE_EXPIRATION_IN_SECONDS);
        } else {
            result.setExpiry(expiry);
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
                LogUtil.d("Invalid JSON %s", e);
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
            // Parse in aggregate source. additionalResult will be false until then.
            result.setAggregateSource(field.get(0));
            additionalResult = true;
        }
        if (additionalResult) {
            addToResults.add(result.build());
            return true;
        }
        return false;
    }

    private void fetchSource(
            @NonNull Uri topOrigin,
            @NonNull Uri target,
            @NonNull String sourceInfo,
            boolean initialFetch,
            @NonNull List<SourceRegistration> registrationsOut) {
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
            LogUtil.e("Failed to open registration target URL %s", e);
            return;
        }
        try {
            urlConnection.setRequestMethod("POST");
            urlConnection.setRequestProperty("Attribution-Reporting-Source-Info", sourceInfo);
            urlConnection.setInstanceFollowRedirects(false);
            Map<String, List<String>> headers = urlConnection.getHeaderFields();

            int responseCode = urlConnection.getResponseCode();
            if (!ResponseBasedFetcher.isRedirect(responseCode)
                    && !ResponseBasedFetcher.isSuccess(responseCode)) {
                return;
            }

            final boolean parsed = parseSource(topOrigin, target, headers, registrationsOut);
            if (!parsed && initialFetch) {
                LogUtil.d("Failed to parse initial fetch");
                return;
            }

            ArrayList<Uri> redirects = new ArrayList();
            ResponseBasedFetcher.parseRedirects(initialFetch, headers, redirects);
            for (Uri redirect : redirects) {
                fetchSource(
                        topOrigin, redirect, sourceInfo, false, registrationsOut);
            }
        } catch (IOException e) {
            LogUtil.e("Failed to get registration response %s", e);
            return;
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
        fetchSource(
                request.getTopOriginUri(),
                request.getRegistrationUri(),
                request.getInputEvent() == null ? "event" : "navigation",
                true, out);
        return !out.isEmpty();
    }

    private interface EventSourceContract {
        String SOURCE_EVENT_ID = "source_event_id";
        String DESTINATION = "destination";
        String EXPIRY = "expiry";
        String PRIORITY = "priority";
    }
}

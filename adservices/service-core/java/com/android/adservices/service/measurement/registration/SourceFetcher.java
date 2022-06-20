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

import static com.android.adservices.service.measurement.PrivacyParams.MAX_INSTALL_ATTRIBUTION_WINDOW;
import static com.android.adservices.service.measurement.PrivacyParams.MAX_POST_INSTALL_EXCLUSIVITY_WINDOW;
import static com.android.adservices.service.measurement.PrivacyParams.MAX_REPORTING_REGISTER_SOURCE_EXPIRATION_IN_SECONDS;
import static com.android.adservices.service.measurement.PrivacyParams.MIN_INSTALL_ATTRIBUTION_WINDOW;
import static com.android.adservices.service.measurement.PrivacyParams.MIN_POST_INSTALL_EXCLUSIVITY_WINDOW;
import static com.android.adservices.service.measurement.PrivacyParams.MIN_REPORTING_REGISTER_SOURCE_EXPIRATION_IN_SECONDS;

import android.adservices.measurement.RegistrationRequest;
import android.annotation.NonNull;
import android.net.Uri;

import com.android.adservices.LogUtil;
import com.android.adservices.service.AdServicesExecutors;

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
import java.util.Optional;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.ExecutionException;
import java.util.concurrent.ExecutorService;

/**
 * Download and decode Response Based Registration
 *
 * @hide
 */
public class SourceFetcher {
    private static final ExecutorService sIoExecutor = AdServicesExecutors.getBlockingExecutor();

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
            long expiry = extractValidValue(json.getLong("expiry"),
                    MIN_REPORTING_REGISTER_SOURCE_EXPIRATION_IN_SECONDS,
                    MAX_REPORTING_REGISTER_SOURCE_EXPIRATION_IN_SECONDS);
            result.setExpiry(expiry);
        }
        if (json.has(EventSourceContract.PRIORITY) && !json.isNull(EventSourceContract.PRIORITY)) {
            result.setSourcePriority(json.getLong(EventSourceContract.PRIORITY));
        }

        if (json.has(EventSourceContract.INSTALL_ATTRIBUTION_WINDOW_KEY)
                && !json.isNull(EventSourceContract.INSTALL_ATTRIBUTION_WINDOW_KEY)) {
            long installAttributionWindow =
                    extractValidValue(
                            json.getLong(EventSourceContract.INSTALL_ATTRIBUTION_WINDOW_KEY),
                            MIN_INSTALL_ATTRIBUTION_WINDOW,
                            MAX_INSTALL_ATTRIBUTION_WINDOW);
            result.setInstallAttributionWindow(installAttributionWindow);
        }
        if (json.has(EventSourceContract.POST_INSTALL_EXCLUSIVITY_WINDOW_KEY)
                && !json.isNull(EventSourceContract.POST_INSTALL_EXCLUSIVITY_WINDOW_KEY)) {
            long installCooldownWindow =
                    extractValidValue(
                            json.getLong(EventSourceContract.POST_INSTALL_EXCLUSIVITY_WINDOW_KEY),
                            MIN_POST_INSTALL_EXCLUSIVITY_WINDOW,
                            MAX_POST_INSTALL_EXCLUSIVITY_WINDOW);
            result.setInstallCooldownWindow(installCooldownWindow);
        }

        // This "filter_data" field is used to generate reports.
        if (json.has(EventSourceContract.FILTER_DATA)
                && !json.isNull(EventSourceContract.FILTER_DATA)) {
            result.setAggregateFilterData(
                    json.getJSONObject(EventSourceContract.FILTER_DATA).toString());
        }
    }

    private static long extractValidValue(long value, long lowerLimit, long upperLimit) {
        if (value < lowerLimit) {
            return lowerLimit;
        } else if (value > upperLimit) {
            return upperLimit;
        }

        return value;
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
            synchronized (addToResults) {
                addToResults.add(result.build());
            }
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
            if (!redirects.isEmpty()) {
                processAsyncRedirects(redirects, topOrigin, sourceInfo, registrationsOut);
            }
        } catch (IOException e) {
            LogUtil.e("Failed to get registration response %s", e);
            return;
        } finally {
            urlConnection.disconnect();
        }
    }

    private void processAsyncRedirects(ArrayList<Uri> redirects, Uri topOrigin, String sourceInfo,
            List<SourceRegistration> registrationsOut) {
        try {
            CompletableFuture.allOf(
                    redirects.stream()
                            .map(redirect ->
                                    CompletableFuture
                                            .runAsync(() ->
                                                            fetchSource(
                                                                    topOrigin,
                                                                    redirect,
                                                                    sourceInfo,
                                                                    /* initialFetch = */ false,
                                                                    registrationsOut),
                                                    sIoExecutor))
                            .toArray(CompletableFuture<?>[]::new)
            ).get();
        } catch (InterruptedException | ExecutionException e) {
            LogUtil.e("Failed to process source redirection", e);
        }
    }

    /**
     * Fetch an attribution source type registration.
     */
    public Optional<List<SourceRegistration>> fetchSource(@NonNull RegistrationRequest request) {
        if (request.getRegistrationType()
                != RegistrationRequest.REGISTER_SOURCE) {
            throw new IllegalArgumentException("Expected source registration");
        }
        List<SourceRegistration> out = new ArrayList<>();
        fetchSource(
                request.getTopOriginUri(),
                request.getRegistrationUri(),
                request.getInputEvent() == null ? "event" : "navigation",
                true, out);
        if (out.isEmpty()) {
            return Optional.empty();
        } else {
            return Optional.of(out);
        }
    }

    private interface EventSourceContract {
        String SOURCE_EVENT_ID = "source_event_id";
        String DESTINATION = "destination";
        String EXPIRY = "expiry";
        String PRIORITY = "priority";
        String INSTALL_ATTRIBUTION_WINDOW_KEY = "install_attribution_window";
        String POST_INSTALL_EXCLUSIVITY_WINDOW_KEY = "post_install_exclusivity_window";
        String FILTER_DATA = "filter_data";
    }
}

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

import static com.android.adservices.service.measurement.PrivacyParams.MAX_AGGREGATE_KEYS_PER_REGISTRATION;

import android.adservices.measurement.RegistrationRequest;
import android.adservices.measurement.WebTriggerParams;
import android.adservices.measurement.WebTriggerRegistrationRequest;
import android.annotation.NonNull;
import android.net.Uri;

import com.android.adservices.LogUtil;
import com.android.adservices.concurrency.AdServicesExecutors;
import com.android.adservices.service.measurement.MeasurementHttpClient;
import com.android.internal.annotations.VisibleForTesting;

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
import java.util.Optional;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.ExecutionException;
import java.util.concurrent.ExecutorService;

/**
 * Download and decode Trigger registration.
 *
 * @hide
 */
public class TriggerFetcher {
    private final ExecutorService mIoExecutor = AdServicesExecutors.getBlockingExecutor();
    private final AdIdPermissionFetcher mAdIdPermissionFetcher;
    private final MeasurementHttpClient mNetworkConnection = new MeasurementHttpClient();

    public TriggerFetcher() {
        this(new AdIdPermissionFetcher());
    }

    @VisibleForTesting
    TriggerFetcher(AdIdPermissionFetcher adIdPermissionFetcher) {
        this.mAdIdPermissionFetcher = adIdPermissionFetcher;
    }

    private boolean parseTrigger(
            @NonNull Uri topOrigin,
            @NonNull Uri reportingOrigin,
            @NonNull Map<String, List<String>> headers,
            @NonNull List<TriggerRegistration> addToResults,
            boolean isWebSource,
            boolean isAllowDebugKey) {
        boolean additionalResult = false;
        TriggerRegistration.Builder result = new TriggerRegistration.Builder();
        result.setTopOrigin(topOrigin);
        result.setReportingOrigin(reportingOrigin);
        List<String> field;
        field = headers.get("Attribution-Reporting-Register-Event-Trigger");
        if (field != null) {
            if (field.size() != 1) {
                LogUtil.d("Expected one event trigger header element!");
                return false;
            }
            // Parses in event triggers data, which is a list of event metadata containing
            // trigger data, priority, de-dup key and event-level filters.
            result.setEventTriggers(field.get(0));
            additionalResult = true;
        }
        field = headers.get("Attribution-Reporting-Register-Aggregatable-Trigger-Data");
        if (field != null) {
            if (field.size() != 1) {
                LogUtil.d("Expected one aggregate trigger data!");
                return false;
            }
            try {
                if (isValidAggregateTriggerData(field.get(0))) {
                    result.setAggregateTriggerData(field.get(0));
                    additionalResult = true;
                } else {
                    return false;
                }
            } catch (JSONException e) {
                LogUtil.d(e, "Invalid JSON");
                return false;
            }
        }
        field = headers.get("Attribution-Reporting-Register-Aggregatable-Values");
        if (field != null) {
            if (field.size() != 1) {
                LogUtil.d("Expected one aggregatable value!");
                return false;
            }
            try {
                if (isValidAggregateValues(field.get(0))) {
                    result.setAggregateValues(field.get(0));
                    additionalResult = true;
                } else {
                    return false;
                }
            } catch (JSONException e) {
                LogUtil.d(e, "Invalid JSON");
                return false;
            }
        }

        field = headers.get("Attribution-Reporting-Filters");
        if (field != null) {
            if (field.size() != 1) {
                LogUtil.d("Expected one reporting filter value!");
                return false;
            }
            result.setFilters(field.get(0));
            additionalResult = true;
        }

        boolean isWebAllow = isWebSource && isAllowDebugKey;
        boolean isAppAllow = !isWebSource && mAdIdPermissionFetcher.isAdIdPermissionEnabled();
        field = headers.get("Attribution-Reporting-Trigger-Debug-Key");
        if (field != null && (isWebAllow || isAppAllow)) {
            if (field.size() != 1) {
                LogUtil.d("Expected one trigger debug value!");
                return false;
            }
            try {
                result.setDebugKey(Long.parseLong(field.get(0)));
            } catch (NumberFormatException e) {
                LogUtil.e(e, "Parsing debug key failed");
            }
        }

        if (additionalResult) {
            addToResults.add(result.build());
            return true;
        }
        return false;
    }

    /** Provided a testing hook. */
    @NonNull
    public URLConnection openUrl(@NonNull URL url) throws IOException {
        return mNetworkConnection.setup(url);
    }

    private void fetchTrigger(
            @NonNull Uri topOrigin,
            @NonNull Uri target,
            boolean shouldProcessRedirects,
            @NonNull List<TriggerRegistration> registrationsOut,
            boolean isWebSource,
            boolean isAllowDebugKey) {
        // Require https.
        if (!target.getScheme().equals("https")) {
            return;
        }
        URL url;
        try {
            url = new URL(target.toString());
        } catch (MalformedURLException e) {
            LogUtil.d(e, "Malformed registration target URL");
            return;
        }
        HttpURLConnection urlConnection;
        try {
            urlConnection = (HttpURLConnection) openUrl(url);
        } catch (IOException e) {
            LogUtil.d(e, "Failed to open registration target URL");
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

            final boolean parsed =
                    parseTrigger(
                            topOrigin,
                            target,
                            headers,
                            registrationsOut,
                            isWebSource,
                            isAllowDebugKey);
            if (!parsed) {
                LogUtil.d("Failed to parse.");
                return;
            }

            if (shouldProcessRedirects) {
                List<Uri> redirects = ResponseBasedFetcher.parseRedirects(headers);
                for (Uri redirect : redirects) {
                    fetchTrigger(
                            topOrigin,
                            redirect,
                            false,
                            registrationsOut,
                            isWebSource,
                            isAllowDebugKey);
                }
            }
        } catch (IOException e) {
            LogUtil.d(e, "Failed to get registration response");
        } finally {
            if (urlConnection != null) {
                urlConnection.disconnect();
            }
        }
    }

    /**
     * Fetch a trigger type registration.
     */
    public Optional<List<TriggerRegistration>> fetchTrigger(@NonNull RegistrationRequest request) {
        if (request.getRegistrationType() != RegistrationRequest.REGISTER_TRIGGER) {
            throw new IllegalArgumentException("Expected trigger registration");
        }
        List<TriggerRegistration> out = new ArrayList<>();
        fetchTrigger(
                request.getTopOriginUri(), request.getRegistrationUri(), true, out, false, false);
        if (out.isEmpty()) {
            return Optional.empty();
        } else {
            return Optional.of(out);
        }
    }

    /** Fetch a trigger type registration without redirects. */
    public Optional<List<TriggerRegistration>> fetchWebTriggers(
            WebTriggerRegistrationRequest request) {
        List<TriggerRegistration> out = new ArrayList<>();
        processWebTriggersFetch(request.getDestination(), request.getTriggerParams(), out);

        if (out.isEmpty()) {
            return Optional.empty();
        } else {
            return Optional.of(out);
        }
    }

    private void processWebTriggersFetch(
            Uri topOrigin,
            List<WebTriggerParams> triggerParamsList,
            List<TriggerRegistration> registrationsOut) {
        try {
            CompletableFuture.allOf(
                            triggerParamsList.stream()
                                    .map(
                                            triggerParams ->
                                                    createFutureToFetchWebTrigger(
                                                            topOrigin,
                                                            registrationsOut,
                                                            triggerParams))
                                    .toArray(CompletableFuture<?>[]::new))
                    .get();
        } catch (InterruptedException | ExecutionException e) {
            LogUtil.e(e, "Failed to process source redirection");
        }
    }

    private CompletableFuture<Void> createFutureToFetchWebTrigger(
            Uri topOrigin,
            List<TriggerRegistration> registrationsOut,
            WebTriggerParams triggerParams) {
        return CompletableFuture.runAsync(
                () ->
                        fetchTrigger(
                                topOrigin,
                                triggerParams.getRegistrationUri(),
                                /* should process redirects*/ false,
                                registrationsOut,
                                true,
                                triggerParams.isDebugKeyAllowed()),
                mIoExecutor);
    }

    private boolean isValidAggregateTriggerData(String aggregateTriggerData) throws JSONException {
        JSONArray jsonArray = new JSONArray(aggregateTriggerData);
        if (jsonArray.length() > MAX_AGGREGATE_KEYS_PER_REGISTRATION) {
            LogUtil.d("Aggregate trigger data has more keys than permitted. %s",
                    jsonArray.length());
            return false;
        }
        return true;
    }

    private boolean isValidAggregateValues(String aggregateValues) throws JSONException {
        JSONObject jsonObj = new JSONObject(aggregateValues);
        if (jsonObj.length() > MAX_AGGREGATE_KEYS_PER_REGISTRATION) {
            LogUtil.d("Aggregate values have more keys than permitted. %s",
                    jsonObj.length());
            return false;
        }
        return true;
    }
}

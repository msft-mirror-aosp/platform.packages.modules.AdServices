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
import android.adservices.measurement.WebTriggerParams;
import android.adservices.measurement.WebTriggerRegistrationRequest;
import android.annotation.NonNull;
import android.net.Uri;

import com.android.adservices.LogUtil;
import com.android.adservices.concurrency.AdServicesExecutors;

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
    private static final ExecutorService sIoExecutor = AdServicesExecutors.getBlockingExecutor();

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
            // Parses in aggregate trigger data. additionalResult will be false until then.
            result.setAggregateTriggerData(field.get(0));
            additionalResult = true;
        }
        field = headers.get("Attribution-Reporting-Register-Aggregatable-Values");
        if (field != null) {
            if (field.size() != 1) {
                LogUtil.d("Expected one aggregatable value!");
                return false;
            }
            // Parses in aggregate values. additionalResult will be false until then.
            result.setAggregateValues(field.get(0));
            additionalResult = true;
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

        if (additionalResult) {
            addToResults.add(result.build());
            return true;
        }
        return false;
    }

    /** Provided a testing hook. */
    @NonNull
    public URLConnection openUrl(@NonNull URL url) throws IOException {
        return url.openConnection();
    }

    private void fetchTrigger(
            @NonNull Uri topOrigin,
            @NonNull Uri target,
            boolean shouldProcessRedirects,
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
            if (!parsed) {
                LogUtil.d("Failed to parse.");
                return;
            }

            if (shouldProcessRedirects) {
                List<Uri> redirects = ResponseBasedFetcher.parseRedirects(headers);
                for (Uri redirect : redirects) {
                    fetchTrigger(topOrigin, redirect, false, registrationsOut);
                }
            }
        } catch (IOException e) {
            LogUtil.d("Failed to get registration response %s", e);
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
        fetchTrigger(request.getTopOriginUri(), request.getRegistrationUri(), true, out);
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
            LogUtil.e("Failed to process source redirection", e);
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
                                registrationsOut),
                sIoExecutor);
    }
}

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
import android.adservices.measurement.WebSourceParams;
import android.adservices.measurement.WebSourceRegistrationRequest;
import android.annotation.NonNull;
import android.annotation.Nullable;
import android.net.Uri;

import com.android.adservices.LogUtil;
import com.android.adservices.concurrency.AdServicesExecutors;
import com.android.internal.annotations.VisibleForTesting;

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
import java.util.Objects;
import java.util.Optional;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.ExecutionException;
import java.util.concurrent.ExecutorService;
import java.util.function.Function;

/**
 * Download and decode Response Based Registration
 *
 * @hide
 */
public class SourceFetcher {
    private final ExecutorService mIoExecutor = AdServicesExecutors.getBlockingExecutor();
    private final AdIdPermissionFetcher mAdIdPermissionFetcher;
    private final String mDefaultAndroidAppScheme = "android-app";
    private final String mDefaultAndroidAppUriPrefix = mDefaultAndroidAppScheme + "://";

    public SourceFetcher() {
        this(new AdIdPermissionFetcher());
    }

    @VisibleForTesting
    SourceFetcher(AdIdPermissionFetcher adIdPermissionFetcher) {
        this.mAdIdPermissionFetcher = adIdPermissionFetcher;
    }

    private boolean parseEventSource(
            @NonNull String text,
            @Nullable Uri osDestinationFromRequest,
            @Nullable Uri webDestinationFromRequest,
            boolean shouldValidateDestination,
            SourceRegistration.Builder result,
            boolean isWebSource,
            boolean isAllowDebugKey)
            throws JSONException {
        final JSONObject json = new JSONObject(text);
        final boolean hasRequiredParams = hasRequiredParams(json, shouldValidateDestination);
        if (!hasRequiredParams) {
            throw new JSONException(
                    String.format(
                            "Expected %s and a destination", EventSourceContract.SOURCE_EVENT_ID));
        }

        result.setSourceEventId(json.getLong(EventSourceContract.SOURCE_EVENT_ID));
        if (!json.isNull(EventSourceContract.EXPIRY)) {
            long expiry =
                    extractValidValue(
                            json.getLong(EventSourceContract.EXPIRY),
                            MIN_REPORTING_REGISTER_SOURCE_EXPIRATION_IN_SECONDS,
                            MAX_REPORTING_REGISTER_SOURCE_EXPIRATION_IN_SECONDS);
            result.setExpiry(expiry);
        }
        if (!json.isNull(EventSourceContract.PRIORITY)) {
            result.setSourcePriority(json.getLong(EventSourceContract.PRIORITY));
        }
        boolean isWebAllow = isWebSource && isAllowDebugKey;
        boolean isAppAllow = !isWebSource && mAdIdPermissionFetcher.isAdIdPermissionEnabled();
        if (!json.isNull(EventSourceContract.DEBUG_KEY) && (isWebAllow || isAppAllow)) {
            result.setDebugKey(json.getLong(EventSourceContract.DEBUG_KEY));
        }
        if (!json.isNull(EventSourceContract.INSTALL_ATTRIBUTION_WINDOW_KEY)) {
            long installAttributionWindow =
                    extractValidValue(
                            json.getLong(EventSourceContract.INSTALL_ATTRIBUTION_WINDOW_KEY),
                            MIN_INSTALL_ATTRIBUTION_WINDOW,
                            MAX_INSTALL_ATTRIBUTION_WINDOW);
            result.setInstallAttributionWindow(installAttributionWindow);
        }
        if (!json.isNull(EventSourceContract.POST_INSTALL_EXCLUSIVITY_WINDOW_KEY)) {
            long installCooldownWindow =
                    extractValidValue(
                            json.getLong(EventSourceContract.POST_INSTALL_EXCLUSIVITY_WINDOW_KEY),
                            MIN_POST_INSTALL_EXCLUSIVITY_WINDOW,
                            MAX_POST_INSTALL_EXCLUSIVITY_WINDOW);
            result.setInstallCooldownWindow(installCooldownWindow);
        }

        // This "filter_data" field is used to generate reports.
        if (!json.isNull(EventSourceContract.FILTER_DATA)) {
            result.setAggregateFilterData(
                    json.getJSONObject(EventSourceContract.FILTER_DATA).toString());
        }

        if (shouldValidateDestination
                && !doUriFieldsMatch(
                        json, EventSourceContract.DESTINATION, osDestinationFromRequest)) {
            LogUtil.d("Expected destination to match with the supplied one!");
            return false;
        }

        if (!json.isNull(EventSourceContract.DESTINATION)) {
            Uri appUri = Uri.parse(json.getString(EventSourceContract.DESTINATION));
            if (appUri.getScheme() == null) {
                LogUtil.d("App destination is missing app scheme, adding.");
                appUri = Uri.parse(mDefaultAndroidAppUriPrefix + appUri);
            }

            if (!mDefaultAndroidAppScheme.equals(appUri.getScheme())) {
                LogUtil.e(
                        "Invalid scheme for app destination: %s; dropping the source.",
                        appUri.getScheme());
                return false;
            }

            result.setAppDestination(appUri);
        }

        if (shouldValidateDestination
                && !doUriFieldsMatch(
                        json, EventSourceContract.WEB_DESTINATION, webDestinationFromRequest)) {
            LogUtil.d("Expected web_destination to match with ths supplied one!");
            return false;
        }

        if (!json.isNull(EventSourceContract.WEB_DESTINATION)) {
            Uri webDestination = Uri.parse(json.getString(EventSourceContract.WEB_DESTINATION));
            result.setWebDestination(webDestination);
        }

        return true;
    }

    private boolean hasRequiredParams(JSONObject json, boolean shouldValidateDestinations) {
        boolean isDestinationAvailable;
        if (shouldValidateDestinations) {
            // This is multiple-destinations case (web or app). At least one of them should be
            // available.
            isDestinationAvailable =
                    !json.isNull(EventSourceContract.DESTINATION)
                            || !json.isNull(EventSourceContract.WEB_DESTINATION);
        } else {
            isDestinationAvailable = !json.isNull(EventSourceContract.DESTINATION);
        }

        return !json.isNull(EventSourceContract.SOURCE_EVENT_ID) && isDestinationAvailable;
    }

    private boolean doUriFieldsMatch(JSONObject json, String fieldName, Uri expectedValue)
            throws JSONException {
        if (json.isNull(fieldName) && expectedValue == null) {
            return true;
        }

        return !json.isNull(fieldName)
                && Objects.equals(expectedValue, Uri.parse(json.getString(fieldName)));
    }

    private long extractValidValue(long value, long lowerLimit, long upperLimit) {
        if (value < lowerLimit) {
            return lowerLimit;
        } else if (value > upperLimit) {
            return upperLimit;
        }

        return value;
    }

    private boolean parseSource(
            @NonNull Uri topOrigin,
            @NonNull Uri reportingOrigin,
            @Nullable Uri osDestination,
            @Nullable Uri webDestination,
            boolean shouldValidateDestination,
            @NonNull Map<String, List<String>> headers,
            @NonNull List<SourceRegistration> addToResults,
            boolean isWebSource,
            boolean isAllowDebugKey) {
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
                boolean isValid =
                        parseEventSource(
                                field.get(0),
                                osDestination,
                                webDestination,
                                shouldValidateDestination,
                                result,
                                isWebSource,
                                isAllowDebugKey);
                if (!isValid) {
                    return false;
                }
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

    /** Provided a testing hook. */
    @NonNull
    public URLConnection openUrl(@NonNull URL url) throws IOException {
        return url.openConnection();
    }

    private void fetchSource(
            @NonNull Uri topOrigin,
            @NonNull Uri registrationUri,
            @Nullable Uri osDestination,
            @Nullable Uri webDestination,
            boolean shouldValidateDestination,
            @NonNull String sourceType,
            boolean shouldProcessRedirects,
            @NonNull List<SourceRegistration> registrationsOut,
            boolean isWebSource,
            boolean isAllowDebugKey) {
        // Require https.
        if (!registrationUri.getScheme().equals("https")) {
            return;
        }
        URL url;
        try {
            url = new URL(registrationUri.toString());
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
            urlConnection.setRequestProperty("Attribution-Reporting-Source-Info", sourceType);
            urlConnection.setInstanceFollowRedirects(false);
            Map<String, List<String>> headers = urlConnection.getHeaderFields();

            int responseCode = urlConnection.getResponseCode();
            if (!ResponseBasedFetcher.isRedirect(responseCode)
                    && !ResponseBasedFetcher.isSuccess(responseCode)) {
                return;
            }

            final boolean parsed =
                    parseSource(
                            topOrigin,
                            registrationUri,
                            osDestination,
                            webDestination,
                            shouldValidateDestination,
                            headers,
                            registrationsOut,
                            isWebSource,
                            isAllowDebugKey);
            if (!parsed) {
                LogUtil.d("Failed to parse");
                return;
            }

            if (shouldProcessRedirects) {
                List<Uri> redirects = ResponseBasedFetcher.parseRedirects(headers);
                if (!redirects.isEmpty()) {
                    processAsyncRedirects(
                            redirects,
                            topOrigin,
                            sourceType,
                            registrationsOut,
                            isWebSource,
                            isAllowDebugKey);
                }
            }
        } catch (IOException e) {
            LogUtil.e("Failed to get registration response %s", e);
        } finally {
            urlConnection.disconnect();
        }
    }

    private void processAsyncRedirects(
            List<Uri> redirects,
            Uri topOrigin,
            String sourceInfo,
            List<SourceRegistration> registrationsOut,
            boolean isWebSource,
            boolean isAllowDebugKey) {
        try {
            Function<Uri, CompletableFuture<Void>> fetchSourceFromRedirectFuture =
                    redirect ->
                            CompletableFuture.runAsync(
                                    () ->
                                            fetchSource(
                                                    topOrigin,
                                                    redirect,
                                                    /* osDestination */ null,
                                                    /* webDestination */ null,
                                                    /* shouldValidateDestination*/ false,
                                                    sourceInfo,
                                                    /*shouldProcessRedirects*/ false,
                                                    registrationsOut,
                                                    isWebSource,
                                                    isAllowDebugKey),
                                    mIoExecutor);
            CompletableFuture.allOf(
                            redirects.stream()
                                    .map(fetchSourceFromRedirectFuture)
                                    .toArray(CompletableFuture<?>[]::new))
                    .get();
        } catch (InterruptedException | ExecutionException e) {
            LogUtil.e(e, "Failed to process source redirection");
        }
    }

    /** Fetch an attribution source type registration. */
    public Optional<List<SourceRegistration>> fetchSource(@NonNull RegistrationRequest request) {
        if (request.getRegistrationType()
                != RegistrationRequest.REGISTER_SOURCE) {
            throw new IllegalArgumentException("Expected source registration");
        }
        List<SourceRegistration> out = new ArrayList<>();
        fetchSource(
                request.getTopOriginUri(),
                request.getRegistrationUri(),
                null,
                null,
                false,
                request.getInputEvent() == null ? "event" : "navigation",
                true,
                out,
                false,
                false);
        if (out.isEmpty()) {
            return Optional.empty();
        } else {
            return Optional.of(out);
        }
    }

    /** Fetch an attribution source type registration. */
    public Optional<List<SourceRegistration>> fetchWebSources(
            @NonNull WebSourceRegistrationRequest request) {
        List<SourceRegistration> out = new ArrayList<>();
        processWebSourcesFetch(
                request.getTopOriginUri(),
                request.getSourceParams(),
                request.getOsDestination(),
                request.getWebDestination(),
                request.getInputEvent() == null ? "event" : "navigation",
                out);
        if (out.isEmpty()) {
            return Optional.empty();
        } else {
            return Optional.of(out);
        }
    }

    private void processWebSourcesFetch(
            Uri topOrigin,
            List<WebSourceParams> sourceParamsList,
            Uri osDestination,
            Uri webDestination,
            String sourceType,
            List<SourceRegistration> registrationsOut) {
        try {
            CompletableFuture.allOf(
                            sourceParamsList.stream()
                                    .map(
                                            sourceParams ->
                                                    createFutureToFetchWebSource(
                                                            topOrigin,
                                                            osDestination,
                                                            webDestination,
                                                            sourceType,
                                                            registrationsOut,
                                                            sourceParams))
                                    .toArray(CompletableFuture<?>[]::new))
                    .get();
        } catch (InterruptedException | ExecutionException e) {
            LogUtil.e(e, "Failed to process source redirection");
        }
    }

    private CompletableFuture<Void> createFutureToFetchWebSource(
            Uri topOrigin,
            Uri osDestination,
            Uri webDestination,
            String sourceType,
            List<SourceRegistration> registrationsOut,
            WebSourceParams sourceParams) {
        return CompletableFuture.runAsync(
                () ->
                        fetchSource(
                                topOrigin,
                                sourceParams.getRegistrationUri(),
                                osDestination,
                                webDestination,
                                /* shouldValidateDestination */ true,
                                sourceType,
                                /* shouldProcessRedirects*/ false,
                                registrationsOut,
                                true,
                                sourceParams.isAllowDebugKey()),
                mIoExecutor);
    }

    private interface EventSourceContract {
        String SOURCE_EVENT_ID = "source_event_id";
        String DEBUG_KEY = "debug_key";
        String DESTINATION = "destination";
        String EXPIRY = "expiry";
        String PRIORITY = "priority";
        String INSTALL_ATTRIBUTION_WINDOW_KEY = "install_attribution_window";
        String POST_INSTALL_EXCLUSIVITY_WINDOW_KEY = "post_install_exclusivity_window";
        String FILTER_DATA = "filter_data";
        String WEB_DESTINATION = "web_destination";
    }
}

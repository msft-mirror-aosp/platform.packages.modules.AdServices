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
import static com.android.adservices.service.measurement.SystemHealthParams.MAX_AGGREGATE_KEYS_PER_REGISTRATION;
import static com.android.adservices.service.measurement.util.BaseUriExtractor.getBaseUri;
import static com.android.adservices.service.measurement.util.MathUtils.extractValidNumberInRange;
import static com.android.adservices.service.stats.AdServicesStatsLog.AD_SERVICES_MEASUREMENT_REGISTRATIONS__TYPE__SOURCE;

import static java.lang.Math.min;

import android.annotation.NonNull;
import android.annotation.Nullable;
import android.content.Context;
import android.net.Uri;

import com.android.adservices.LogUtil;
import com.android.adservices.data.enrollment.EnrollmentDao;
import com.android.adservices.service.Flags;
import com.android.adservices.service.FlagsFactory;
import com.android.adservices.service.measurement.AsyncRegistration;
import com.android.adservices.service.measurement.EventSurfaceType;
import com.android.adservices.service.measurement.MeasurementHttpClient;
import com.android.adservices.service.measurement.Source;
import com.android.adservices.service.measurement.util.AsyncFetchStatus;
import com.android.adservices.service.measurement.util.AsyncRedirect;
import com.android.adservices.service.measurement.util.Enrollment;
import com.android.adservices.service.measurement.util.UnsignedLong;
import com.android.adservices.service.measurement.util.Web;
import com.android.adservices.service.stats.AdServicesLogger;
import com.android.adservices.service.stats.AdServicesLoggerImpl;
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
import java.util.Objects;
import java.util.Optional;
import java.util.concurrent.TimeUnit;

/**
 * Download and decode Response Based Registration
 *
 * @hide
 */
public class AsyncSourceFetcher {

    private static final long ONE_DAY_IN_SECONDS = TimeUnit.DAYS.toSeconds(1);
    private final String mDefaultAndroidAppScheme = "android-app";
    private final String mDefaultAndroidAppUriPrefix = mDefaultAndroidAppScheme + "://";
    private final MeasurementHttpClient mNetworkConnection = new MeasurementHttpClient();
    private final EnrollmentDao mEnrollmentDao;
    private final Flags mFlags;
    private final AdServicesLogger mLogger;

    public AsyncSourceFetcher(Context context) {
        this(
                EnrollmentDao.getInstance(context),
                FlagsFactory.getFlags(),
                AdServicesLoggerImpl.getInstance());
    }

    @VisibleForTesting
    public AsyncSourceFetcher(EnrollmentDao enrollmentDao, Flags flags, AdServicesLogger logger) {
        mEnrollmentDao = enrollmentDao;
        mFlags = flags;
        mLogger = logger;
    }

    private boolean parseCommonSourceParams(
            @NonNull JSONObject json,
            @NonNull Source.SourceType sourceType,
            @Nullable Uri appDestinationFromRequest,
            @Nullable Uri webDestinationFromRequest,
            long sourceEventTime,
            boolean shouldValidateDestinationWebSource,
            boolean shouldOverrideDestinationAppSource,
            Source.Builder result,
            boolean isWebSource)
            throws JSONException {
        final boolean hasRequiredParams =
                hasRequiredParams(json, shouldValidateDestinationWebSource);
        if (!hasRequiredParams) {
            throw new JSONException(
                    String.format(
                            "Expected %s and a destination", SourceHeaderContract.SOURCE_EVENT_ID));
        }
        UnsignedLong eventId = new UnsignedLong(0L);
        if (!json.isNull(SourceHeaderContract.SOURCE_EVENT_ID)) {
            try {
                eventId = new UnsignedLong(json.getString(SourceHeaderContract.SOURCE_EVENT_ID));
            } catch (NumberFormatException e) {
                LogUtil.d(e, "parseCommonSourceParams: parsing source_event_id failed.");
            }
        }
        result.setEventId(eventId);
        long expiry;
        if (!json.isNull(SourceHeaderContract.EXPIRY)) {
            expiry =
                    extractValidNumberInRange(
                            json.getLong(SourceHeaderContract.EXPIRY),
                            MIN_REPORTING_REGISTER_SOURCE_EXPIRATION_IN_SECONDS,
                            MAX_REPORTING_REGISTER_SOURCE_EXPIRATION_IN_SECONDS);
            if (sourceType == Source.SourceType.EVENT) {
                expiry = roundSecondsToWholeDays(expiry);
            }
        } else {
            expiry = MAX_REPORTING_REGISTER_SOURCE_EXPIRATION_IN_SECONDS;
        }
        result.setExpiryTime(sourceEventTime + TimeUnit.SECONDS.toMillis(expiry));
        long eventReportWindow;
        if (!json.isNull(SourceHeaderContract.EVENT_REPORT_WINDOW)) {
            eventReportWindow =
                    Math.min(
                            expiry,
                            extractValidNumberInRange(
                                    json.getLong(SourceHeaderContract.EVENT_REPORT_WINDOW),
                                    MIN_REPORTING_REGISTER_SOURCE_EXPIRATION_IN_SECONDS,
                                    MAX_REPORTING_REGISTER_SOURCE_EXPIRATION_IN_SECONDS));
        } else {
            eventReportWindow = expiry;
        }
        result.setEventReportWindow(sourceEventTime + TimeUnit.SECONDS.toMillis(eventReportWindow));
        long aggregateReportWindow;
        if (!json.isNull(SourceHeaderContract.AGGREGATABLE_REPORT_WINDOW)) {
            aggregateReportWindow =
                    min(
                            expiry,
                            extractValidNumberInRange(
                                    json.getLong(SourceHeaderContract.AGGREGATABLE_REPORT_WINDOW),
                                    MIN_REPORTING_REGISTER_SOURCE_EXPIRATION_IN_SECONDS,
                                    MAX_REPORTING_REGISTER_SOURCE_EXPIRATION_IN_SECONDS));
        } else {
            aggregateReportWindow = expiry;
        }
        result.setAggregatableReportWindow(
                sourceEventTime + TimeUnit.SECONDS.toMillis(aggregateReportWindow));
        if (!json.isNull(SourceHeaderContract.PRIORITY)) {
            result.setPriority(json.getLong(SourceHeaderContract.PRIORITY));
        }
        if (!json.isNull(SourceHeaderContract.DEBUG_REPORTING)) {
            result.setIsDebugReporting(json.optBoolean(SourceHeaderContract.DEBUG_REPORTING));
        }
        if (!json.isNull(SourceHeaderContract.DEBUG_KEY)) {
            try {
                result.setDebugKey(
                        new UnsignedLong(json.getString(SourceHeaderContract.DEBUG_KEY)));
            } catch (NumberFormatException e) {
                LogUtil.e(e, "parseCommonSourceParams: parsing debug key failed");
            }
        }
        if (!json.isNull(SourceHeaderContract.INSTALL_ATTRIBUTION_WINDOW_KEY)) {
            long installAttributionWindow =
                    extractValidNumberInRange(
                            json.getLong(SourceHeaderContract.INSTALL_ATTRIBUTION_WINDOW_KEY),
                            MIN_INSTALL_ATTRIBUTION_WINDOW,
                            MAX_INSTALL_ATTRIBUTION_WINDOW);
            result.setInstallAttributionWindow(TimeUnit.SECONDS.toMillis(installAttributionWindow));
        } else {
            result.setInstallAttributionWindow(
                    TimeUnit.SECONDS.toMillis(MAX_INSTALL_ATTRIBUTION_WINDOW));
        }
        if (!json.isNull(SourceHeaderContract.POST_INSTALL_EXCLUSIVITY_WINDOW_KEY)) {
            long installCooldownWindow =
                    extractValidNumberInRange(
                            json.getLong(SourceHeaderContract.POST_INSTALL_EXCLUSIVITY_WINDOW_KEY),
                            MIN_POST_INSTALL_EXCLUSIVITY_WINDOW,
                            MAX_POST_INSTALL_EXCLUSIVITY_WINDOW);
            result.setInstallCooldownWindow(TimeUnit.SECONDS.toMillis(installCooldownWindow));
        } else {
            result.setInstallCooldownWindow(
                    TimeUnit.SECONDS.toMillis(MIN_POST_INSTALL_EXCLUSIVITY_WINDOW));
        }
        // This "filter_data" field is used to generate reports.
        if (!json.isNull(SourceHeaderContract.FILTER_DATA)) {
            if (!FetcherUtil.areValidAttributionFilters(
                    json.optJSONObject(SourceHeaderContract.FILTER_DATA))) {
                LogUtil.d("Source filter-data is invalid.");
                return false;
            }
            result.setFilterData(
                    json.getJSONObject(SourceHeaderContract.FILTER_DATA).toString());
        }

        Uri appUri = null;
        if (!json.isNull(SourceHeaderContract.DESTINATION)) {
            appUri = Uri.parse(json.getString(SourceHeaderContract.DESTINATION));
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
        }

        if (shouldValidateDestinationWebSource
                && appDestinationFromRequest != null // Only validate when non-null in request
                && !appDestinationFromRequest.equals(appUri)) {
            LogUtil.d("Expected destination to match with the supplied one!");
            return false;
        }

        if (appUri != null) {
            result.setAppDestinations(List.of(getBaseUri(appUri)));
        }

        if (shouldValidateDestinationWebSource
                && webDestinationFromRequest != null // Only validate when non-null in request
                && !doUriFieldsMatch(
                        json, SourceHeaderContract.WEB_DESTINATION, webDestinationFromRequest)) {
            LogUtil.d("Expected web_destination to match with the supplied one!");
            return false;
        }
        if (!json.isNull(SourceHeaderContract.WEB_DESTINATION)) {
            Uri webDestination = Uri.parse(json.getString(SourceHeaderContract.WEB_DESTINATION));
            Optional<Uri> topPrivateDomainAndScheme = Web.topPrivateDomainAndScheme(webDestination);
            if (!topPrivateDomainAndScheme.isPresent()) {
                LogUtil.d("Unable to extract top private domain and scheme from web destination.");
                return false;
            } else {
                result.setWebDestinations(List.of(topPrivateDomainAndScheme.get()));
            }
        }
        if (shouldOverrideDestinationAppSource && !isWebSource
                && appDestinationFromRequest != null) {
            result.setAppDestinations(List.of(appDestinationFromRequest));
        }
        return true;
    }

    /** Parse a {@code Source}, given response headers, adding the {@code Source} to a given list */
    @VisibleForTesting
    public boolean parseSource(
            @NonNull String registrationId,
            @NonNull Uri publisher,
            @NonNull String enrollmentId,
            @Nullable Uri appDestination,
            @Nullable Uri webDestination,
            @Nullable Uri registrant,
            long eventTime,
            @NonNull Source.SourceType sourceType,
            boolean shouldValidateDestinationWebSource,
            boolean shouldOverrideDestinationAppSource,
            @NonNull Map<String, List<String>> headers,
            @NonNull List<Source> sources,
            boolean isWebSource,
            boolean adIdPermission,
            boolean arDebugPermission) {
        Source.Builder result = new Source.Builder();
        result.setRegistrationId(registrationId);
        result.setPublisher(publisher);
        result.setEnrollmentId(enrollmentId);
        result.setRegistrant(registrant);
        result.setSourceType(sourceType);
        result.setAttributionMode(Source.AttributionMode.TRUTHFULLY);
        result.setEventTime(eventTime);
        result.setAdIdPermission(adIdPermission);
        result.setArDebugPermission(arDebugPermission);
        result.setEventTime(eventTime);
        result.setPublisherType(isWebSource ? EventSurfaceType.WEB : EventSurfaceType.APP);
        List<String> field = headers.get("Attribution-Reporting-Register-Source");
        if (field == null || field.size() != 1) {
            LogUtil.d(
                    "AsyncSourceFetcher: "
                            + "Invalid Attribution-Reporting-Register-Source header.");
            return false;
        }
        try {
            JSONObject json = new JSONObject(field.get(0));
            boolean isValid =
                    parseCommonSourceParams(
                            json,
                            sourceType,
                            appDestination,
                            webDestination,
                            eventTime,
                            shouldValidateDestinationWebSource,
                            shouldOverrideDestinationAppSource,
                            result,
                            isWebSource);
            if (!isValid) {
                return false;
            }
            if (!json.isNull(SourceHeaderContract.AGGREGATION_KEYS)) {
                if (!areValidAggregationKeys(
                        json.getJSONObject(SourceHeaderContract.AGGREGATION_KEYS))) {
                    return false;
                }
                result.setAggregateSource(json.getString(SourceHeaderContract.AGGREGATION_KEYS));
            }
            if (mFlags.getMeasurementEnableXNA()
                    && !json.isNull(SourceHeaderContract.SHARED_AGGREGATION_KEYS)) {
                // Parsed as JSONArray for validation
                JSONArray sharedAggregationKeys =
                        json.getJSONArray(SourceHeaderContract.SHARED_AGGREGATION_KEYS);
                result.setSharedAggregationKeys(sharedAggregationKeys.toString());
            }
            sources.add(result.build());
            return true;
        } catch (JSONException | NumberFormatException e) {
            LogUtil.d(e, "AsyncSourceFetcher: Invalid JSON");
            return false;
        }
    }

    private static boolean hasRequiredParams(JSONObject json, boolean shouldValidateDestinations) {
        boolean isDestinationAvailable = !json.isNull(SourceHeaderContract.DESTINATION);
        if (shouldValidateDestinations) {
            isDestinationAvailable |= !json.isNull(SourceHeaderContract.WEB_DESTINATION);
        }
        return isDestinationAvailable;
    }

    private static boolean doUriFieldsMatch(JSONObject json, String fieldName, Uri expectedValue)
            throws JSONException {
        if (json.isNull(fieldName) && expectedValue == null) {
            return true;
        }
        return !json.isNull(fieldName)
                && Objects.equals(expectedValue, Uri.parse(json.getString(fieldName)));
    }

    /** Provided a testing hook. */
    @NonNull
    @VisibleForTesting
    public URLConnection openUrl(@NonNull URL url) throws IOException {
        return mNetworkConnection.setup(url);
    }

    /**
     * Fetch a source type registration.
     *
     * @param asyncRegistration a {@link AsyncRegistration}, a request the record.
     * @param asyncFetchStatus a {@link AsyncFetchStatus}, stores Ad Tech server status.
     */
    public Optional<Source> fetchSource(
            @NonNull AsyncRegistration asyncRegistration,
            @NonNull AsyncFetchStatus asyncFetchStatus,
            AsyncRedirect asyncRedirect) {
        List<Source> out = new ArrayList<>();
        fetchSource(
                asyncRegistration.getId(),
                asyncRegistration.getTopOrigin(),
                asyncRegistration.getRegistrationUri(),
                asyncRegistration.getOsDestination(),
                asyncRegistration.getWebDestination(),
                asyncRegistration.getRegistrant(),
                asyncRegistration.getRequestTime(),
                asyncRegistration.getType() == AsyncRegistration.RegistrationType.WEB_SOURCE,
                asyncRegistration.getRedirectType() != AsyncRegistration.RedirectType.ANY,
                asyncRegistration.getSourceType(),
                out,
                asyncRegistration.shouldProcessRedirects(),
                asyncRegistration.getRedirectType(),
                asyncRedirect,
                asyncRegistration.getType() == AsyncRegistration.RegistrationType.WEB_SOURCE,
                asyncFetchStatus,
                asyncRegistration.hasAdIdPermission(),
                asyncRegistration.getDebugKeyAllowed());
        if (out.isEmpty()) {
            return Optional.empty();
        } else {
            return Optional.of(out.get(0));
        }
    }

    private void fetchSource(
            @NonNull String registrationId,
            @NonNull Uri publisher,
            @NonNull Uri registrationUri,
            @Nullable Uri appDestination,
            @Nullable Uri webDestination,
            @Nullable Uri registrant,
            long eventTime,
            boolean shouldValidateDestinationWebSource,
            boolean shouldOverrideDestinationAppSource,
            @NonNull Source.SourceType sourceType,
            @NonNull List<Source> sourceOut,
            boolean shouldProcessRedirects,
            @AsyncRegistration.RedirectType int redirectType,
            @NonNull AsyncRedirect asyncRedirect,
            boolean isWebSource,
            @Nullable AsyncFetchStatus asyncFetchStatus,
            boolean adIdPermission,
            boolean arDebugPermission) {
        // Require https.
        if (!registrationUri.getScheme().equals("https")) {
            asyncFetchStatus.setStatus(AsyncFetchStatus.ResponseStatus.PARSING_ERROR);
            return;
        }
        URL url;
        try {
            url = new URL(registrationUri.toString());
        } catch (MalformedURLException e) {
            LogUtil.d(e, "Malformed registration target URL");
            return;
        }
        Optional<String> enrollmentId =
                Enrollment.maybeGetEnrollmentId(registrationUri, mEnrollmentDao);
        if (!enrollmentId.isPresent()) {
            asyncFetchStatus.setStatus(AsyncFetchStatus.ResponseStatus.INVALID_ENROLLMENT);
            LogUtil.d(
                    "fetchSource: unable to find enrollment ID. Registration URI: %s",
                    registrationUri);
            return;
        }
        HttpURLConnection urlConnection;
        try {
            urlConnection = (HttpURLConnection) openUrl(url);
        } catch (IOException e) {
            asyncFetchStatus.setStatus(AsyncFetchStatus.ResponseStatus.NETWORK_ERROR);
            LogUtil.e(e, "Failed to open registration target URL");
            return;
        }
        try {
            urlConnection.setRequestMethod("POST");
            urlConnection.setRequestProperty(
                    "Attribution-Reporting-Source-Info", sourceType.toString());
            urlConnection.setInstanceFollowRedirects(false);
            Map<String, List<String>> headers = urlConnection.getHeaderFields();
            FetcherUtil.emitHeaderMetrics(
                    mFlags,
                    mLogger,
                    AD_SERVICES_MEASUREMENT_REGISTRATIONS__TYPE__SOURCE,
                    headers,
                    registrationUri);
            int responseCode = urlConnection.getResponseCode();
            if (!FetcherUtil.isRedirect(responseCode) && !FetcherUtil.isSuccess(responseCode)) {
                asyncFetchStatus.setStatus(AsyncFetchStatus.ResponseStatus.SERVER_UNAVAILABLE);
                return;
            }
            asyncFetchStatus.setStatus(AsyncFetchStatus.ResponseStatus.SUCCESS);
            final boolean parsed =
                    parseSource(
                            registrationId,
                            publisher,
                            enrollmentId.get(),
                            appDestination,
                            webDestination,
                            registrant,
                            eventTime,
                            sourceType,
                            shouldValidateDestinationWebSource,
                            shouldOverrideDestinationAppSource,
                            headers,
                            sourceOut,
                            isWebSource,
                            adIdPermission,
                            arDebugPermission);
            if (!parsed) {
                asyncFetchStatus.setStatus(AsyncFetchStatus.ResponseStatus.PARSING_ERROR);
                LogUtil.d("Failed to parse");
                return;
            }
            if (shouldProcessRedirects) {
                AsyncRedirect redirectsAndType = FetcherUtil.parseRedirects(headers, redirectType);
                asyncRedirect.addToRedirects(redirectsAndType.getRedirects());
                asyncRedirect.setRedirectType(redirectsAndType.getRedirectType());
            } else {
                asyncRedirect.setRedirectType(redirectType);
            }
        } catch (IOException e) {
            asyncFetchStatus.setStatus(AsyncFetchStatus.ResponseStatus.NETWORK_ERROR);
            LogUtil.e(e, "Failed to get registration response");
        } finally {
            urlConnection.disconnect();
        }
    }

    private boolean areValidAggregationKeys(JSONObject aggregationKeys) {
        if (aggregationKeys.length() > MAX_AGGREGATE_KEYS_PER_REGISTRATION) {
            LogUtil.d(
                    "Aggregation-keys have more entries than permitted. %s",
                    aggregationKeys.length());
            return false;
        }
        for (String id : aggregationKeys.keySet()) {
            if (!FetcherUtil.isValidAggregateKeyId(id)) {
                LogUtil.d("SourceFetcher: aggregation key ID is invalid. %s", id);
                return false;
            }
            String keyPiece = aggregationKeys.optString(id);
            if (!FetcherUtil.isValidAggregateKeyPiece(keyPiece)) {
                LogUtil.d("SourceFetcher: aggregation key-piece is invalid. %s", keyPiece);
                return false;
            }
        }
        return true;
    }

    private static long roundSecondsToWholeDays(long seconds) {
        long remainder = seconds % ONE_DAY_IN_SECONDS;
        boolean roundUp = remainder >= ONE_DAY_IN_SECONDS / 2L;
        return seconds - remainder + (roundUp ? ONE_DAY_IN_SECONDS : 0);
    }

    private interface SourceHeaderContract {
        String SOURCE_EVENT_ID = "source_event_id";
        String DEBUG_KEY = "debug_key";
        String DESTINATION = "destination";
        String EXPIRY = "expiry";
        String EVENT_REPORT_WINDOW = "event_report_window";
        String AGGREGATABLE_REPORT_WINDOW = "aggregatable_report_window";
        String PRIORITY = "priority";
        String INSTALL_ATTRIBUTION_WINDOW_KEY = "install_attribution_window";
        String POST_INSTALL_EXCLUSIVITY_WINDOW_KEY = "post_install_exclusivity_window";
        String FILTER_DATA = "filter_data";
        String WEB_DESTINATION = "web_destination";
        String AGGREGATION_KEYS = "aggregation_keys";
        String SHARED_AGGREGATION_KEYS = "shared_aggregation_keys";
        String DEBUG_REPORTING = "debug_reporting";
    }
}

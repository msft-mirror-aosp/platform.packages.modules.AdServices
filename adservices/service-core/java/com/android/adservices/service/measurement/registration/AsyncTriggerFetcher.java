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

import static com.android.adservices.service.measurement.SystemHealthParams.MAX_AGGREGATABLE_TRIGGER_DATA;
import static com.android.adservices.service.measurement.SystemHealthParams.MAX_AGGREGATE_DEDUPLICATION_KEYS_PER_REGISTRATION;
import static com.android.adservices.service.measurement.SystemHealthParams.MAX_AGGREGATE_KEYS_PER_REGISTRATION;
import static com.android.adservices.service.measurement.SystemHealthParams.MAX_ATTRIBUTION_EVENT_TRIGGER_DATA;
import static com.android.adservices.service.stats.AdServicesStatsLog.AD_SERVICES_MEASUREMENT_REGISTRATIONS__TYPE__TRIGGER;

import android.annotation.NonNull;
import android.annotation.Nullable;
import android.content.Context;
import android.net.Uri;

import com.android.adservices.LogUtil;
import com.android.adservices.data.enrollment.EnrollmentDao;
import com.android.adservices.service.Flags;
import com.android.adservices.service.FlagsFactory;
import com.android.adservices.service.common.AllowLists;
import com.android.adservices.service.measurement.AsyncRegistration;
import com.android.adservices.service.measurement.AttributionConfig;
import com.android.adservices.service.measurement.EventSurfaceType;
import com.android.adservices.service.measurement.MeasurementHttpClient;
import com.android.adservices.service.measurement.Trigger;
import com.android.adservices.service.measurement.XNetworkData;
import com.android.adservices.service.measurement.util.AsyncFetchStatus;
import com.android.adservices.service.measurement.util.AsyncRedirect;
import com.android.adservices.service.measurement.util.BaseUriExtractor;
import com.android.adservices.service.measurement.util.Enrollment;
import com.android.adservices.service.measurement.util.Filter;
import com.android.adservices.service.measurement.util.UnsignedLong;
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
import java.util.HashSet;
import java.util.Iterator;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.Set;

/**
 * Download and decode Trigger registration.
 *
 * @hide
 */
public class AsyncTriggerFetcher {

    private final MeasurementHttpClient mNetworkConnection = new MeasurementHttpClient();
    private final EnrollmentDao mEnrollmentDao;
    private final Flags mFlags;
    private final AdServicesLogger mLogger;

    public AsyncTriggerFetcher(Context context) {
        this(
                EnrollmentDao.getInstance(context),
                FlagsFactory.getFlags(),
                AdServicesLoggerImpl.getInstance());
    }

    @VisibleForTesting
    public AsyncTriggerFetcher(EnrollmentDao enrollmentDao, Flags flags, AdServicesLogger logger) {
        mEnrollmentDao = enrollmentDao;
        mFlags = flags;
        mLogger = logger;
    }

    /**
     * Parse a {@code Trigger}, given response headers, adding the {@code Trigger} to a given list.
     */
    @VisibleForTesting
    public boolean parseTrigger(
            @NonNull Uri topOrigin,
            @NonNull Uri registrant,
            @NonNull String enrollmentId,
            long triggerTime,
            @NonNull Map<String, List<String>> headers,
            @NonNull List<Trigger> triggers,
            AsyncRegistration.RegistrationType registrationType,
            boolean adIdPermission,
            boolean arDebugPermission) {
        Trigger.Builder result = new Trigger.Builder();
        result.setEnrollmentId(enrollmentId);
        result.setAttributionDestination(getAttributionDestination(topOrigin, registrationType));
        result.setRegistrant(registrant);
        result.setAdIdPermission(adIdPermission);
        result.setArDebugPermission(arDebugPermission);
        result.setDestinationType(
                registrationType == AsyncRegistration.RegistrationType.WEB_TRIGGER
                        ? EventSurfaceType.WEB
                        : EventSurfaceType.APP);
        result.setTriggerTime(triggerTime);
        List<String> field =
                headers.get(TriggerHeaderContract.HEADER_ATTRIBUTION_REPORTING_REGISTER_TRIGGER);
        if (field == null || field.size() != 1) {
            LogUtil.d(
                    "AsyncTriggerFetcher: "
                            + "Invalid "
                            + TriggerHeaderContract.HEADER_ATTRIBUTION_REPORTING_REGISTER_TRIGGER
                            + " header.");
            return false;
        }
        try {
            String eventTriggerData = new JSONArray().toString();
            JSONObject json = new JSONObject(field.get(0));
            if (!json.isNull(TriggerHeaderContract.EVENT_TRIGGER_DATA)) {
                Optional<String> validEventTriggerData =
                        getValidEventTriggerData(
                                json.getJSONArray(TriggerHeaderContract.EVENT_TRIGGER_DATA));
                if (!validEventTriggerData.isPresent()) {
                    return false;
                }
                eventTriggerData = validEventTriggerData.get();
            }
            result.setEventTriggers(eventTriggerData);
            if (!json.isNull(TriggerHeaderContract.AGGREGATABLE_TRIGGER_DATA)) {
                Optional<String> validAggregateTriggerData =
                        getValidAggregateTriggerData(
                                json.getJSONArray(TriggerHeaderContract.AGGREGATABLE_TRIGGER_DATA));
                if (!validAggregateTriggerData.isPresent()) {
                    return false;
                }
                result.setAggregateTriggerData(validAggregateTriggerData.get());
            }
            if (!json.isNull(TriggerHeaderContract.AGGREGATABLE_VALUES)) {
                if (!isValidAggregateValues(
                        json.getJSONObject(TriggerHeaderContract.AGGREGATABLE_VALUES))) {
                    return false;
                }
                result.setAggregateValues(
                        json.getString(TriggerHeaderContract.AGGREGATABLE_VALUES));
            }
            if (!json.isNull(TriggerHeaderContract.AGGREGATABLE_DEDUPLICATION_KEYS)) {
                Optional<String> validAggregateDeduplicationKeysString =
                        getValidAggregateDuplicationKeysString(
                                json.getJSONArray(
                                        TriggerHeaderContract.AGGREGATABLE_DEDUPLICATION_KEYS));
                if (!validAggregateDeduplicationKeysString.isPresent()) {
                    LogUtil.d("parseTrigger: aggregate deduplication keys are invalid.");
                    return false;
                }
                result.setAggregateDeduplicationKeys(validAggregateDeduplicationKeysString.get());
            }
            if (!json.isNull(TriggerHeaderContract.FILTERS)) {
                JSONArray filters = Filter.maybeWrapFilters(json, TriggerHeaderContract.FILTERS);
                if (!FetcherUtil.areValidAttributionFilters(filters)) {
                    LogUtil.d("parseTrigger: filters are invalid.");
                    return false;
                }
                result.setFilters(filters.toString());
            }
            if (!json.isNull(TriggerHeaderContract.NOT_FILTERS)) {
                JSONArray notFilters =
                        Filter.maybeWrapFilters(json, TriggerHeaderContract.NOT_FILTERS);
                if (!FetcherUtil.areValidAttributionFilters(notFilters)) {
                    LogUtil.d("parseTrigger: not-filters are invalid.");
                    return false;
                }
                result.setNotFilters(notFilters.toString());
            }
            if (!json.isNull(TriggerHeaderContract.DEBUG_REPORTING)) {
                result.setIsDebugReporting(json.optBoolean(TriggerHeaderContract.DEBUG_REPORTING));
            }
            if (!json.isNull(TriggerHeaderContract.DEBUG_KEY)) {
                try {
                    result.setDebugKey(
                            new UnsignedLong(json.getString(TriggerHeaderContract.DEBUG_KEY)));
                } catch (NumberFormatException e) {
                    LogUtil.e(e, "Parsing trigger debug key failed");
                }
            }
            if (mFlags.getMeasurementEnableXNA()
                    && !json.isNull(TriggerHeaderContract.X_NETWORK_KEY_MAPPING)) {
                if (!isValidXNetworkKeyMapping(
                        json.getJSONObject(TriggerHeaderContract.X_NETWORK_KEY_MAPPING))) {
                    LogUtil.d("parseTrigger: adtech bit mapping is invalid.");
                } else {
                    result.setAdtechBitMapping(
                            json.getString(TriggerHeaderContract.X_NETWORK_KEY_MAPPING));
                }
            }
            if (mFlags.getMeasurementEnableXNA()
                    && isXnaAllowedForTriggerRegistrant(registrant, registrationType)
                    && !json.isNull(TriggerHeaderContract.ATTRIBUTION_CONFIG)) {
                String attributionConfigsString =
                        extractValidAttributionConfigs(
                                json.getJSONArray(TriggerHeaderContract.ATTRIBUTION_CONFIG));
                result.setAttributionConfig(attributionConfigsString);
            }

            Set<String> allowedEnrollmentsString =
                    new HashSet<>(
                            AllowLists.splitAllowList(
                                    mFlags.getMeasurementDebugJoinKeyEnrollmentAllowlist()));
            if (allowedEnrollmentsString.contains(enrollmentId)
                    && !json.isNull(TriggerHeaderContract.DEBUG_JOIN_KEY)) {
                result.setDebugJoinKey(json.optString(TriggerHeaderContract.DEBUG_JOIN_KEY));
            }
            triggers.add(result.build());
            return true;
        } catch (JSONException e) {
            LogUtil.e(e, "Trigger Parsing failed");
            return false;
        }
    }

    /** Provided a testing hook. */
    @NonNull
    public URLConnection openUrl(@NonNull URL url) throws IOException {
        return mNetworkConnection.setup(url);
    }

    private boolean isXnaAllowedForTriggerRegistrant(
            Uri registrant, AsyncRegistration.RegistrationType registrationType) {
        // If the trigger is registered from web context, only allow-listed apps should be able to
        // parse attribution config.
        return !AsyncRegistration.RegistrationType.WEB_TRIGGER.equals(registrationType)
                || AllowLists.isPackageAllowListed(
                        mFlags.getWebContextClientAppAllowList(), registrant.getAuthority());
    }

    private void fetchTrigger(
            @NonNull Uri topOrigin,
            @NonNull Uri registrationUri,
            Uri registrant,
            long triggerTime,
            boolean shouldProcessRedirects,
            @AsyncRegistration.RedirectType int redirectType,
            @NonNull AsyncRedirect asyncRedirect,
            @NonNull List<Trigger> triggerOut,
            @Nullable AsyncFetchStatus asyncFetchStatus,
            AsyncRegistration.RegistrationType registrationType,
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
                    "fetchTrigger: unable to find enrollment ID. Registration URI: %s",
                    registrationUri);
            return;
        }
        HttpURLConnection urlConnection;
        try {
            urlConnection = (HttpURLConnection) openUrl(url);
        } catch (IOException e) {
            asyncFetchStatus.setStatus(AsyncFetchStatus.ResponseStatus.NETWORK_ERROR);
            LogUtil.d(e, "Failed to open registration target URL");
            return;
        }
        try {
            urlConnection.setRequestMethod("POST");
            urlConnection.setInstanceFollowRedirects(false);
            Map<String, List<String>> headers = urlConnection.getHeaderFields();
            FetcherUtil.emitHeaderMetrics(
                    mFlags,
                    mLogger,
                    AD_SERVICES_MEASUREMENT_REGISTRATIONS__TYPE__TRIGGER,
                    headers,
                    registrationUri);
            int responseCode = urlConnection.getResponseCode();
            LogUtil.d("Response code = " + responseCode);
            if (!FetcherUtil.isRedirect(responseCode) && !FetcherUtil.isSuccess(responseCode)) {
                asyncFetchStatus.setStatus(AsyncFetchStatus.ResponseStatus.SERVER_UNAVAILABLE);
                return;
            }
            asyncFetchStatus.setStatus(AsyncFetchStatus.ResponseStatus.SUCCESS);
            final boolean parsed =
                    parseTrigger(
                            topOrigin,
                            registrant,
                            enrollmentId.get(),
                            triggerTime,
                            headers,
                            triggerOut,
                            registrationType,
                            adIdPermission,
                            arDebugPermission);
            if (!parsed) {
                asyncFetchStatus.setStatus(AsyncFetchStatus.ResponseStatus.PARSING_ERROR);
                LogUtil.d("Failed to parse.");
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
            LogUtil.d(e, "Failed to get registration response");
        } finally {
            if (urlConnection != null) {
                urlConnection.disconnect();
            }
        }
    }
    /**
     * Fetch a trigger type registration.
     *
     * @param asyncRegistration a {@link AsyncRegistration}, a request the record.
     * @param asyncFetchStatus a {@link AsyncFetchStatus}, stores Ad Tech server status.
     */
    public Optional<Trigger> fetchTrigger(
            @NonNull AsyncRegistration asyncRegistration,
            @NonNull AsyncFetchStatus asyncFetchStatus,
            AsyncRedirect asyncRedirect) {
        List<Trigger> out = new ArrayList<>();
        fetchTrigger(
                asyncRegistration.getTopOrigin(),
                asyncRegistration.getRegistrationUri(),
                asyncRegistration.getRegistrant(),
                asyncRegistration.getRequestTime(),
                asyncRegistration.shouldProcessRedirects(),
                asyncRegistration.getRedirectType(),
                asyncRedirect,
                out,
                asyncFetchStatus,
                asyncRegistration.getType(),
                asyncRegistration.hasAdIdPermission(),
                asyncRegistration.getDebugKeyAllowed());
        if (out.isEmpty()) {
            return Optional.empty();
        } else {
            return Optional.of(out.get(0));
        }
    }

    private static Optional<String> getValidEventTriggerData(JSONArray eventTriggerDataArr) {
        if (eventTriggerDataArr.length() > MAX_ATTRIBUTION_EVENT_TRIGGER_DATA) {
            LogUtil.d(
                    "Event trigger data list has more entries than permitted. %s",
                    eventTriggerDataArr.length());
            return Optional.empty();
        }
        JSONArray validEventTriggerData = new JSONArray();
        for (int i = 0; i < eventTriggerDataArr.length(); i++) {
            JSONObject validEventTriggerDatum = new JSONObject();
            try {
                JSONObject eventTriggerDatum = eventTriggerDataArr.getJSONObject(i);
                // Treat invalid trigger data, priority and deduplication key as if they were not
                // set.
                UnsignedLong triggerData = new UnsignedLong(0L);
                if (!eventTriggerDatum.isNull("trigger_data")) {
                    try {
                        triggerData = new UnsignedLong(eventTriggerDatum.getString("trigger_data"));
                    } catch (NumberFormatException e) {
                        LogUtil.d(e, "getValidEventTriggerData: parsing trigger_data failed.");
                    }
                }
                validEventTriggerDatum.put("trigger_data", triggerData);
                if (!eventTriggerDatum.isNull("priority")) {
                    try {
                        validEventTriggerDatum.put(
                                "priority",
                                String.valueOf(
                                        Long.parseLong(eventTriggerDatum.getString("priority"))));
                    } catch (NumberFormatException e) {
                        LogUtil.d(e, "getValidEventTriggerData: parsing priority failed.");
                    }
                }
                if (!eventTriggerDatum.isNull("deduplication_key")) {
                    try {
                        validEventTriggerDatum.put(
                                "deduplication_key",
                                new UnsignedLong(eventTriggerDatum.getString("deduplication_key")));
                    } catch (NumberFormatException e) {
                        LogUtil.d(e, "getValidEventTriggerData: parsing deduplication_key failed.");
                    }
                }
                if (!eventTriggerDatum.isNull("filters")) {
                    JSONArray filters = Filter.maybeWrapFilters(eventTriggerDatum, "filters");
                    if (!FetcherUtil.areValidAttributionFilters(filters)) {
                        LogUtil.d("getValidEventTriggerData: filters are invalid.");
                        return Optional.empty();
                    }
                    validEventTriggerDatum.put("filters", filters);
                }
                if (!eventTriggerDatum.isNull("not_filters")) {
                    JSONArray notFilters =
                            Filter.maybeWrapFilters(eventTriggerDatum, "not_filters");
                    if (!FetcherUtil.areValidAttributionFilters(notFilters)) {
                        LogUtil.d("getValidEventTriggerData: not-filters are invalid.");
                        return Optional.empty();
                    }
                    validEventTriggerDatum.put("not_filters", notFilters);
                }
                validEventTriggerData.put(validEventTriggerDatum);
            } catch (JSONException e) {
                LogUtil.d(e, "AsyncTriggerFetcher: " + "Parsing event trigger datum JSON failed.");
            }
        }
        return Optional.of(validEventTriggerData.toString());
    }

    private static Optional<String> getValidAggregateTriggerData(JSONArray aggregateTriggerDataArr)
            throws JSONException {
        if (aggregateTriggerDataArr.length() > MAX_AGGREGATABLE_TRIGGER_DATA) {
            LogUtil.d(
                    "Aggregate trigger data list has more entries than permitted. %s",
                    aggregateTriggerDataArr.length());
            return Optional.empty();
        }
        JSONArray validAggregateTriggerData = new JSONArray();
        for (int i = 0; i < aggregateTriggerDataArr.length(); i++) {
            JSONObject aggregateTriggerData = aggregateTriggerDataArr.getJSONObject(i);
            String keyPiece = aggregateTriggerData.optString("key_piece");
            if (!FetcherUtil.isValidAggregateKeyPiece(keyPiece)) {
                LogUtil.d("Aggregate trigger data key-piece is invalid. %s", keyPiece);
                return Optional.empty();
            }
            JSONArray sourceKeys = aggregateTriggerData.optJSONArray("source_keys");
            if (sourceKeys == null || sourceKeys.length() > MAX_AGGREGATE_KEYS_PER_REGISTRATION) {
                LogUtil.d(
                        "Aggregate trigger data source-keys list failed to parse or has more"
                                + " entries than permitted.");
                return Optional.empty();
            }
            for (int j = 0; j < sourceKeys.length(); j++) {
                String key = sourceKeys.optString(j);
                if (!FetcherUtil.isValidAggregateKeyId(key)) {
                    LogUtil.d("Aggregate trigger data source-key is invalid. %s", key);
                    return Optional.empty();
                }
            }
            if (!aggregateTriggerData.isNull("filters")) {
                JSONArray filters = Filter.maybeWrapFilters(aggregateTriggerData, "filters");
                if (!FetcherUtil.areValidAttributionFilters(filters)) {
                    LogUtil.d("Aggregate trigger data filters are invalid.");
                    return Optional.empty();
                }
                aggregateTriggerData.put("filters", filters);
            }
            if (!aggregateTriggerData.isNull("not_filters")) {
                JSONArray notFilters = Filter.maybeWrapFilters(aggregateTriggerData, "not_filters");
                if (!FetcherUtil.areValidAttributionFilters(notFilters)) {
                    LogUtil.d("Aggregate trigger data not-filters are invalid.");
                    return Optional.empty();
                }
                aggregateTriggerData.put("not_filters", notFilters);
            }
            if (!aggregateTriggerData.isNull("x_network_data")) {
                JSONObject xNetworkDataJson = aggregateTriggerData.getJSONObject("x_network_data");
                // This is in order to validate the JSON parsing does not throw exception
                new XNetworkData.Builder(xNetworkDataJson);
            }
            validAggregateTriggerData.put(aggregateTriggerData);
        }
        return Optional.of(validAggregateTriggerData.toString());
    }

    private boolean isValidAggregateValues(JSONObject aggregateValues) {
        if (aggregateValues.length() > MAX_AGGREGATE_KEYS_PER_REGISTRATION) {
            LogUtil.d(
                    "Aggregate values have more keys than permitted. %s", aggregateValues.length());
            return false;
        }
        Iterator<String> ids = aggregateValues.keys();
        while (ids.hasNext()) {
            String id = ids.next();
            if (!FetcherUtil.isValidAggregateKeyId(id)) {
                LogUtil.d("Aggregate values key ID is invalid. %s", id);
                return false;
            }
        }
        return true;
    }

    private Optional<String> getValidAggregateDuplicationKeysString(
            JSONArray aggregateDeduplicationKeys) throws JSONException {
        JSONArray validAggregateDeduplicationKeys = new JSONArray();
        if (aggregateDeduplicationKeys.length()
                > MAX_AGGREGATE_DEDUPLICATION_KEYS_PER_REGISTRATION) {
            LogUtil.d(
                    "Aggregate deduplication keys have more keys than permitted. %s",
                    aggregateDeduplicationKeys.length());
            return Optional.empty();
        }
        for (int i = 0; i < aggregateDeduplicationKeys.length(); i++) {
            JSONObject aggregateDedupKey = new JSONObject();
            JSONObject deduplication_key = aggregateDeduplicationKeys.getJSONObject(i);
            String deduplicationKey = deduplication_key.optString("deduplication_key");
            if (!FetcherUtil.isValidAggregateDeduplicationKey(deduplicationKey)) {
                return Optional.empty();
            }
            aggregateDedupKey.put("deduplication_key", deduplicationKey);
            if (!deduplication_key.isNull("filters")) {
                JSONArray filters = Filter.maybeWrapFilters(deduplication_key, "filters");
                if (!FetcherUtil.areValidAttributionFilters(filters)) {
                    LogUtil.d("Aggregate deduplication key: " + i + " contains invalid filters.");
                    return Optional.empty();
                }
                aggregateDedupKey.put("filters", filters);
            }
            if (!deduplication_key.isNull("not_filters")) {
                JSONArray notFilters = Filter.maybeWrapFilters(deduplication_key, "not_filters");
                if (!FetcherUtil.areValidAttributionFilters(notFilters)) {
                    LogUtil.d(
                            "Aggregate deduplication key: " + i + " contains invalid not filters.");
                    return Optional.empty();
                }
                aggregateDedupKey.put("not_filters", notFilters);
            }
            validAggregateDeduplicationKeys.put(aggregateDedupKey);
        }
        return Optional.of(validAggregateDeduplicationKeys.toString());
    }

    private String extractValidAttributionConfigs(JSONArray attributionConfigsArray)
            throws JSONException {
        JSONArray validAttributionConfigsArray = new JSONArray();
        for (int i = 0; i < attributionConfigsArray.length(); i++) {
            AttributionConfig attributionConfig =
                    new AttributionConfig.Builder(attributionConfigsArray.getJSONObject(i)).build();
            validAttributionConfigsArray.put(attributionConfig.serializeAsJson());
        }
        return validAttributionConfigsArray.toString();
    }

    private boolean isValidXNetworkKeyMapping(JSONObject adTechBitMapping) throws JSONException {
        // TODO: Might need to add logic for keys' and values' lengths.
        Iterator<String> keys = adTechBitMapping.keys();
        while (keys.hasNext()) {
            String key = keys.next();
            String value = adTechBitMapping.optString(key);
            if (value == null || !value.startsWith("0x")) {
                return false;
            }
        }
        return true;
    }

    private static Uri getAttributionDestination(Uri destination,
            AsyncRegistration.RegistrationType registrationType) {
        return registrationType == AsyncRegistration.RegistrationType.APP_TRIGGER
                ? BaseUriExtractor.getBaseUri(destination)
                : destination;
    }

    private interface TriggerHeaderContract {
        String HEADER_ATTRIBUTION_REPORTING_REGISTER_TRIGGER =
                "Attribution-Reporting-Register-Trigger";
        String ATTRIBUTION_CONFIG = "attribution_config";
        String EVENT_TRIGGER_DATA = "event_trigger_data";
        String FILTERS = "filters";
        String NOT_FILTERS = "not_filters";
        String AGGREGATABLE_TRIGGER_DATA = "aggregatable_trigger_data";
        String AGGREGATABLE_VALUES = "aggregatable_values";
        String AGGREGATABLE_DEDUPLICATION_KEYS = "aggregatable_deduplication_keys";
        String DEBUG_KEY = "debug_key";
        String DEBUG_REPORTING = "debug_reporting";
        String X_NETWORK_KEY_MAPPING = "x_network_key_mapping";
        String DEBUG_JOIN_KEY = "debug_join_key";
    }
}

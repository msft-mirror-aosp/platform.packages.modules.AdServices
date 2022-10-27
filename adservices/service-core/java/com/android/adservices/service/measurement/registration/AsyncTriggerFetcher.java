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
import com.android.adservices.service.measurement.AsyncRegistration;
import com.android.adservices.service.measurement.EventSurfaceType;
import com.android.adservices.service.measurement.MeasurementHttpClient;
import com.android.adservices.service.measurement.Trigger;
import com.android.adservices.service.measurement.util.AsyncFetchStatus;
import com.android.adservices.service.measurement.util.AsyncRedirect;
import com.android.adservices.service.measurement.util.Enrollment;
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
import java.util.Iterator;
import java.util.List;
import java.util.Map;
import java.util.Optional;

/**
 * Download and decode Trigger registration.
 *
 * @hide
 */
public class AsyncTriggerFetcher {

    private static final String ANDROID_APP_SCHEME = "android-app";
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

    private boolean parseTrigger(
            @NonNull Uri topOrigin,
            @NonNull Uri registrant,
            @NonNull String enrollmentId,
            long triggerTime,
            @NonNull Map<String, List<String>> headers,
            @NonNull List<Trigger> triggers,
            boolean isWebSource,
            boolean isAllowDebugKey,
            boolean isAdIdPermissionGranted,
            AsyncRegistration.RegistrationType registrationType) {
        Trigger.Builder result = new Trigger.Builder();
        result.setEnrollmentId(enrollmentId);
        result.setAttributionDestination(topOrigin);
        result.setRegistrant(registrant);
        result.setDestinationType(
                registrationType == AsyncRegistration.RegistrationType.WEB_TRIGGER
                        ? EventSurfaceType.WEB
                        : EventSurfaceType.APP);
        result.setTriggerTime(triggerTime);
        List<String> field = headers.get("Attribution-Reporting-Register-Trigger");
        if (field == null || field.size() != 1) {
            LogUtil.d(
                    "AsyncSourceFetcher: "
                            + "Invalid Attribution-Reporting-Register-Source header.");
            return false;
        }
        try {
            JSONObject json = new JSONObject(field.get(0));
            if (!json.isNull(TriggerHeaderContract.EVENT_TRIGGER_DATA)) {
                Optional<String> validEventTriggerData =
                        getValidEventTriggerData(
                                json.getJSONArray(TriggerHeaderContract.EVENT_TRIGGER_DATA));
                if (!validEventTriggerData.isPresent()) {
                    return false;
                }
                result.setEventTriggers(validEventTriggerData.get());
            }
            if (!json.isNull(TriggerHeaderContract.AGGREGATABLE_TRIGGER_DATA)) {
                if (!isValidAggregateTriggerData(
                        json.getJSONArray(TriggerHeaderContract.AGGREGATABLE_TRIGGER_DATA))) {
                    return false;
                }
                result.setAggregateTriggerData(
                        json.getString(TriggerHeaderContract.AGGREGATABLE_TRIGGER_DATA));
            }
            if (!json.isNull(TriggerHeaderContract.AGGREGATABLE_VALUES)) {
                if (!isValidAggregateValues(
                        json.getJSONObject(TriggerHeaderContract.AGGREGATABLE_VALUES))) {
                    return false;
                }
                result.setAggregateValues(
                        json.getString(TriggerHeaderContract.AGGREGATABLE_VALUES));
            }
            if (!json.isNull(TriggerHeaderContract.FILTERS)) {
                result.setFilters(json.getString(TriggerHeaderContract.FILTERS));
            }
            boolean isWebAllow = isWebSource && isAllowDebugKey && isAdIdPermissionGranted;
            boolean isAppAllow = !isWebSource && isAdIdPermissionGranted;
            if (!json.isNull(TriggerHeaderContract.DEBUG_KEY) && (isWebAllow || isAppAllow)) {
                try {
                    result.setDebugKey(
                            new UnsignedLong(json.getString(TriggerHeaderContract.DEBUG_KEY)));
                } catch (NumberFormatException e) {
                    LogUtil.e(e, "Parsing trigger debug key failed");
                }
            }
            triggers.add(result.build());
            return true;
        } catch (JSONException e) {
            LogUtil.e("Trigger Parsing failed", e);
            return false;
        }
    }

    /** Provided a testing hook. */
    @NonNull
    public URLConnection openUrl(@NonNull URL url) throws IOException {
        return mNetworkConnection.setup(url);
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
            boolean isWebSource,
            boolean isAllowDebugKey,
            @Nullable AsyncFetchStatus asyncFetchStatus,
            boolean isAdIdPermissionGranted,
            AsyncRegistration.RegistrationType registrationType) {
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
                            isWebSource,
                            isAllowDebugKey,
                            isAdIdPermissionGranted,
                            registrationType);
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
                asyncRegistration.getType() == AsyncRegistration.RegistrationType.WEB_TRIGGER
                        ? asyncRegistration.getTopOrigin()
                        : getAttributionDestination(asyncRegistration),
                asyncRegistration.getRegistrationUri(),
                asyncRegistration.getRegistrant(),
                asyncRegistration.getRequestTime(),
                asyncRegistration.shouldProcessRedirects(),
                asyncRegistration.getRedirectType(),
                asyncRedirect,
                out,
                asyncRegistration.getType() == AsyncRegistration.RegistrationType.WEB_TRIGGER,
                asyncRegistration.getDebugKeyAllowed(),
                asyncFetchStatus,
                asyncRegistration.getDebugKeyAllowed(),
                asyncRegistration.getType());
        if (out.isEmpty()) {
            return Optional.empty();
        } else {
            return Optional.of(out.get(0));
        }
    }

    private Optional<String> getValidEventTriggerData(JSONArray eventTriggerDataArr) {

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
                if (!eventTriggerDatum.isNull("trigger_data")) {
                    try {
                        validEventTriggerDatum.put(
                                "trigger_data",
                                new UnsignedLong(eventTriggerDatum.getString("trigger_data")));
                    } catch (NumberFormatException e) {
                        LogUtil.d(e, "getValidEventTriggerData: parsing trigger_data failed.");
                    }
                }
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
                    JSONObject filters = eventTriggerDatum.optJSONObject("filters");
                    if (!FetcherUtil.areValidAttributionFilters(filters)) {
                        LogUtil.d("getValidEventTriggerData: filters are invalid.");
                        return Optional.empty();
                    }
                    validEventTriggerDatum.put("filters", filters);
                }
                if (!eventTriggerDatum.isNull("not_filters")) {
                    JSONObject notFilters = eventTriggerDatum.optJSONObject("not_filters");
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

    private boolean isValidAggregateTriggerData(JSONArray aggregateTriggerDataArr)
            throws JSONException {
        if (aggregateTriggerDataArr.length() > MAX_AGGREGATABLE_TRIGGER_DATA) {
            LogUtil.d(
                    "Aggregate trigger data list has more entries than permitted. %s",
                    aggregateTriggerDataArr.length());
            return false;
        }
        for (int i = 0; i < aggregateTriggerDataArr.length(); i++) {
            JSONObject aggregateTriggerData = aggregateTriggerDataArr.getJSONObject(i);
            String keyPiece = aggregateTriggerData.optString("key_piece");
            if (!FetcherUtil.isValidAggregateKeyPiece(keyPiece)) {
                LogUtil.d("Aggregate trigger data key-piece is invalid. %s", keyPiece);
                return false;
            }
            JSONArray sourceKeys = aggregateTriggerData.optJSONArray("source_keys");
            if (sourceKeys == null || sourceKeys.length() > MAX_AGGREGATE_KEYS_PER_REGISTRATION) {
                LogUtil.d(
                        "Aggregate trigger data source-keys list failed to parse or has more"
                                + " entries than permitted.");
                return false;
            }
            for (int j = 0; j < sourceKeys.length(); j++) {
                String key = sourceKeys.optString(j);
                if (!FetcherUtil.isValidAggregateKeyId(key)) {
                    LogUtil.d("Aggregate trigger data source-key is invalid. %s", key);
                    return false;
                }
            }
            if (!aggregateTriggerData.isNull("filters")
                    && !FetcherUtil.areValidAttributionFilters(
                            aggregateTriggerData.optJSONObject("filters"))) {
                LogUtil.d("Aggregate trigger data filters are invalid.");
                return false;
            }
            if (!aggregateTriggerData.isNull("not_filters")
                    && !FetcherUtil.areValidAttributionFilters(
                            aggregateTriggerData.optJSONObject("not_filters"))) {
                LogUtil.d("Aggregate trigger data not-filters are invalid.");
                return false;
            }
        }
        return true;
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

    @VisibleForTesting
    static Uri getAttributionDestination(AsyncRegistration request) {
        return Uri.parse(ANDROID_APP_SCHEME + "://" + request.getRegistrant());
    }

    private interface TriggerHeaderContract {
        String EVENT_TRIGGER_DATA = "event_trigger_data";
        String FILTERS = "filters";
        String AGGREGATABLE_TRIGGER_DATA = "aggregatable_trigger_data";
        String AGGREGATABLE_VALUES = "aggregatable_values";
        String DEBUG_KEY = "debug_key";
    }
}

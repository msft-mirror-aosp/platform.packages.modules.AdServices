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

import static com.android.adservices.service.stats.AdServicesStatsLog.AD_SERVICES_ERROR_REPORTED__ERROR_CODE__ENROLLMENT_INVALID;
import static com.android.adservices.service.stats.AdServicesStatsLog.AD_SERVICES_ERROR_REPORTED__ERROR_CODE__MEASUREMENT_REGISTRATION_ODP_GET_MANAGER_ERROR;
import static com.android.adservices.service.stats.AdServicesStatsLog.AD_SERVICES_ERROR_REPORTED__PPAPI_NAME__MEASUREMENT;

import android.adservices.ondevicepersonalization.OnDevicePersonalizationSystemEventManager;
import android.annotation.NonNull;
import android.content.Context;
import android.net.Uri;

import com.android.adservices.LoggerFactory;
import com.android.adservices.data.enrollment.EnrollmentDao;
import com.android.adservices.data.measurement.DatastoreManager;
import com.android.adservices.data.measurement.DatastoreManagerFactory;
import com.android.adservices.errorlogging.ErrorLogUtil;
import com.android.adservices.service.Flags;
import com.android.adservices.service.FlagsFactory;
import com.android.adservices.service.common.AllowLists;
import com.android.adservices.service.common.WebAddresses;
import com.android.adservices.service.measurement.AttributionConfig;
import com.android.adservices.service.measurement.EventSurfaceType;
import com.android.adservices.service.measurement.MeasurementHttpClient;
import com.android.adservices.service.measurement.Trigger;
import com.android.adservices.service.measurement.TriggerSpecs;
import com.android.adservices.service.measurement.XNetworkData;
import com.android.adservices.service.measurement.ondevicepersonalization.IOdpDelegationWrapper;
import com.android.adservices.service.measurement.ondevicepersonalization.NoOdpDelegationWrapper;
import com.android.adservices.service.measurement.ondevicepersonalization.OdpDelegationWrapperImpl;
import com.android.adservices.service.measurement.reporting.DebugReportApi;
import com.android.adservices.service.measurement.util.BaseUriExtractor;
import com.android.adservices.service.measurement.util.Enrollment;
import com.android.adservices.service.measurement.util.Filter;
import com.android.adservices.service.measurement.util.UnsignedLong;
import com.android.internal.annotations.VisibleForTesting;
import com.android.modules.utils.build.SdkLevel;

import org.json.JSONArray;
import org.json.JSONException;
import org.json.JSONObject;

import java.io.IOException;
import java.net.HttpURLConnection;
import java.net.MalformedURLException;
import java.net.URL;
import java.net.URLConnection;
import java.util.HashSet;
import java.util.Iterator;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Optional;
import java.util.Set;

/**
 * Download and decode Trigger registration.
 *
 * @hide
 */
public class AsyncTriggerFetcher {

    private final MeasurementHttpClient mNetworkConnection;
    private final EnrollmentDao mEnrollmentDao;
    private final Flags mFlags;
    private final Context mContext;
    private final IOdpDelegationWrapper mOdpWrapper;
    private final DatastoreManager mDatastoreManager;
    private final DebugReportApi mDebugReportApi;

    public AsyncTriggerFetcher(Context context) {
        this(
                context,
                EnrollmentDao.getInstance(context),
                FlagsFactory.getFlags(),
                getOdpDelegationManager(context, FlagsFactory.getFlags()),
                DatastoreManagerFactory.getDatastoreManager(context),
                new DebugReportApi(context, FlagsFactory.getFlags()));
    }

    @VisibleForTesting
    public AsyncTriggerFetcher(
            Context context,
            EnrollmentDao enrollmentDao,
            Flags flags,
            IOdpDelegationWrapper odpWrapper,
            DatastoreManager datastoreManager,
            DebugReportApi debugReportApi) {
        mContext = context;
        mEnrollmentDao = enrollmentDao;
        mFlags = flags;
        mNetworkConnection = new MeasurementHttpClient(context);
        mDatastoreManager = datastoreManager;
        mDebugReportApi = debugReportApi;
        mOdpWrapper = odpWrapper;
    }

    /**
     * Parse a {@code Trigger}, given response headers, adding the {@code Trigger} to a given list.
     */
    @VisibleForTesting
    public Optional<Trigger> parseTrigger(
            AsyncRegistration asyncRegistration,
            String enrollmentId,
            Map<String, List<String>> headers,
            AsyncFetchStatus asyncFetchStatus) {
        boolean arDebugPermission = asyncRegistration.getDebugKeyAllowed();
        LoggerFactory.getMeasurementLogger()
                .d("Trigger ArDebug permission enabled %b", arDebugPermission);
        Trigger.Builder builder = new Trigger.Builder();
        builder.setEnrollmentId(enrollmentId);
        builder.setAttributionDestination(
                getAttributionDestination(
                        asyncRegistration.getTopOrigin(), asyncRegistration.getType()));
        builder.setRegistrant(asyncRegistration.getRegistrant());
        builder.setAdIdPermission(asyncRegistration.hasAdIdPermission());
        builder.setArDebugPermission(arDebugPermission);
        builder.setDestinationType(
                asyncRegistration.isWebRequest() ? EventSurfaceType.WEB : EventSurfaceType.APP);
        builder.setTriggerTime(asyncRegistration.getRequestTime());
        Optional<Uri> registrationUriOrigin =
                WebAddresses.originAndScheme(asyncRegistration.getRegistrationUri());
        if (!registrationUriOrigin.isPresent()) {
            LoggerFactory.getMeasurementLogger()
                    .d(
                            "AsyncTriggerFetcher: "
                                    + "Invalid or empty registration uri - "
                                    + asyncRegistration.getRegistrationUri());
            return Optional.empty();
        }
        builder.setRegistrationOrigin(registrationUriOrigin.get());

        builder.setPlatformAdId(asyncRegistration.getPlatformAdId());

        List<String> field =
                headers.get(TriggerHeaderContract.HEADER_ATTRIBUTION_REPORTING_REGISTER_TRIGGER);
        if (field == null || field.size() != 1) {
            LoggerFactory.getMeasurementLogger()
                    .d(
                            "AsyncTriggerFetcher: "
                                    + "Invalid "
                                    + TriggerHeaderContract
                                            .HEADER_ATTRIBUTION_REPORTING_REGISTER_TRIGGER
                                    + " header.");
            asyncFetchStatus.setEntityStatus(AsyncFetchStatus.EntityStatus.HEADER_ERROR);
            return Optional.empty();
        }
        String registrationHeaderStr = field.get(0);

        boolean isHeaderErrorDebugReportEnabled =
                FetcherUtil.isHeaderErrorDebugReportEnabled(
                        headers.get(TriggerHeaderContract.HEADER_ATTRIBUTION_REPORTING_INFO),
                        mFlags);
        try {
            String eventTriggerData = new JSONArray().toString();
            JSONObject json = new JSONObject(registrationHeaderStr);
            if (!json.isNull(TriggerHeaderContract.EVENT_TRIGGER_DATA)) {
                Optional<String> validEventTriggerData =
                        getValidEventTriggerData(
                                json.getJSONArray(TriggerHeaderContract.EVENT_TRIGGER_DATA));
                if (!validEventTriggerData.isPresent()) {
                    asyncFetchStatus.setEntityStatus(
                            AsyncFetchStatus.EntityStatus.VALIDATION_ERROR);
                    return Optional.empty();
                }
                eventTriggerData = validEventTriggerData.get();
            }
            builder.setEventTriggers(eventTriggerData);
            if (!json.isNull(TriggerHeaderContract.AGGREGATABLE_TRIGGER_DATA)) {
                Optional<String> validAggregateTriggerData =
                        getValidAggregateTriggerData(
                                json.getJSONArray(TriggerHeaderContract.AGGREGATABLE_TRIGGER_DATA));
                if (!validAggregateTriggerData.isPresent()) {
                    asyncFetchStatus.setEntityStatus(
                            AsyncFetchStatus.EntityStatus.VALIDATION_ERROR);
                    return Optional.empty();
                }
                builder.setAggregateTriggerData(validAggregateTriggerData.get());
            }
            if (!json.isNull(TriggerHeaderContract.AGGREGATABLE_VALUES)) {
                if (!isValidAggregateValues(
                        json.getJSONObject(TriggerHeaderContract.AGGREGATABLE_VALUES))) {
                    asyncFetchStatus.setEntityStatus(
                            AsyncFetchStatus.EntityStatus.VALIDATION_ERROR);
                    return Optional.empty();
                }
                builder.setAggregateValues(
                        json.getString(TriggerHeaderContract.AGGREGATABLE_VALUES));
            }
            if (!json.isNull(TriggerHeaderContract.AGGREGATABLE_DEDUPLICATION_KEYS)) {
                Optional<String> validAggregateDeduplicationKeysString =
                        getValidAggregateDuplicationKeysString(
                                json.getJSONArray(
                                        TriggerHeaderContract.AGGREGATABLE_DEDUPLICATION_KEYS));
                if (!validAggregateDeduplicationKeysString.isPresent()) {
                    LoggerFactory.getMeasurementLogger()
                            .d("parseTrigger: aggregate deduplication keys are invalid.");
                    asyncFetchStatus.setEntityStatus(
                            AsyncFetchStatus.EntityStatus.VALIDATION_ERROR);
                    return Optional.empty();
                }
                builder.setAggregateDeduplicationKeys(validAggregateDeduplicationKeysString.get());
            }
            boolean shouldCheckFilterSize = !mFlags.getMeasurementEnableUpdateTriggerHeaderLimit();
            if (!json.isNull(TriggerHeaderContract.FILTERS)) {
                JSONArray filters = Filter.maybeWrapFilters(json, TriggerHeaderContract.FILTERS);
                if (!FetcherUtil.areValidAttributionFilters(
                        filters, mFlags, true, shouldCheckFilterSize)) {
                    LoggerFactory.getMeasurementLogger().d("parseTrigger: filters are invalid.");
                    asyncFetchStatus.setEntityStatus(
                            AsyncFetchStatus.EntityStatus.VALIDATION_ERROR);
                    return Optional.empty();
                }
                builder.setFilters(filters.toString());
            }
            if (!json.isNull(TriggerHeaderContract.NOT_FILTERS)) {
                JSONArray notFilters =
                        Filter.maybeWrapFilters(json, TriggerHeaderContract.NOT_FILTERS);
                if (!FetcherUtil.areValidAttributionFilters(
                        notFilters, mFlags, true, shouldCheckFilterSize)) {
                    LoggerFactory.getMeasurementLogger()
                            .d("parseTrigger: not-filters are invalid.");
                    asyncFetchStatus.setEntityStatus(
                            AsyncFetchStatus.EntityStatus.VALIDATION_ERROR);
                    return Optional.empty();
                }
                builder.setNotFilters(notFilters.toString());
            }
            if (!json.isNull(TriggerHeaderContract.DEBUG_REPORTING)) {
                builder.setIsDebugReporting(json.optBoolean(TriggerHeaderContract.DEBUG_REPORTING));
            }
            if (!json.isNull(TriggerHeaderContract.DEBUG_KEY)) {
                Optional<UnsignedLong> maybeDebugKey =
                        FetcherUtil.extractUnsignedLong(json, TriggerHeaderContract.DEBUG_KEY);
                if (maybeDebugKey.isPresent()) {
                    builder.setDebugKey(maybeDebugKey.get());
                }
            }
            if (mFlags.getMeasurementEnableXNA()
                    && !json.isNull(TriggerHeaderContract.X_NETWORK_KEY_MAPPING)) {
                if (!isValidXNetworkKeyMapping(
                        json.getJSONObject(TriggerHeaderContract.X_NETWORK_KEY_MAPPING))) {
                    LoggerFactory.getMeasurementLogger()
                            .d("parseTrigger: adtech bit mapping is invalid.");
                } else {
                    builder.setAdtechBitMapping(
                            json.getString(TriggerHeaderContract.X_NETWORK_KEY_MAPPING));
                }
            }
            if (mFlags.getMeasurementEnableXNA()
                    && isXnaAllowedForTriggerRegistrant(
                            asyncRegistration.getRegistrant(), asyncRegistration.getType())
                    && !json.isNull(TriggerHeaderContract.ATTRIBUTION_CONFIG)) {
                String attributionConfigsString =
                        extractValidAttributionConfigs(
                                json.getJSONArray(TriggerHeaderContract.ATTRIBUTION_CONFIG));
                builder.setAttributionConfig(attributionConfigsString);
            }

            if (mFlags.getMeasurementAggregationCoordinatorOriginEnabled()
                    && !json.isNull(TriggerHeaderContract.AGGREGATION_COORDINATOR_ORIGIN)) {
                String origin =
                        json.getString(TriggerHeaderContract.AGGREGATION_COORDINATOR_ORIGIN);
                String allowlist = mFlags.getMeasurementAggregationCoordinatorOriginList();
                if (origin.isEmpty() || !isAllowlisted(allowlist, origin)) {
                    LoggerFactory.getMeasurementLogger()
                            .d("parseTrigger: aggregation_coordinator_origin is invalid.");
                    asyncFetchStatus.setEntityStatus(
                            AsyncFetchStatus.EntityStatus.VALIDATION_ERROR);
                    return Optional.empty();
                }
                builder.setAggregationCoordinatorOrigin(Uri.parse(origin));
            }

            String enrollmentBlockList =
                    mFlags.getMeasurementPlatformDebugAdIdMatchingEnrollmentBlocklist();
            Set<String> blockedEnrollmentsString =
                    new HashSet<>(AllowLists.splitAllowList(enrollmentBlockList));
            if (!AllowLists.doesAllowListAllowAll(enrollmentBlockList)
                    && !blockedEnrollmentsString.contains(enrollmentId)
                    && !json.isNull(TriggerHeaderContract.DEBUG_AD_ID)) {
                builder.setDebugAdId(json.optString(TriggerHeaderContract.DEBUG_AD_ID));
            }

            Set<String> allowedEnrollmentsString =
                    new HashSet<>(
                            AllowLists.splitAllowList(
                                    mFlags.getMeasurementDebugJoinKeyEnrollmentAllowlist()));
            if (allowedEnrollmentsString.contains(enrollmentId)
                    && !json.isNull(TriggerHeaderContract.DEBUG_JOIN_KEY)) {
                builder.setDebugJoinKey(json.optString(TriggerHeaderContract.DEBUG_JOIN_KEY));
            }

            Trigger.SourceRegistrationTimeConfig sourceRegistrationTimeConfig =
                    getSourceRegistrationTimeConfig(json);

            builder.setAggregatableSourceRegistrationTimeConfig(sourceRegistrationTimeConfig);

            if (mFlags.getMeasurementEnableTriggerContextId()
                    && !json.isNull(TriggerHeaderContract.TRIGGER_CONTEXT_ID)) {
                if (Trigger.SourceRegistrationTimeConfig.INCLUDE.equals(
                        sourceRegistrationTimeConfig)) {
                    LoggerFactory.getMeasurementLogger()
                            .d(
                                    "parseTrigger: %s cannot be set when %s has a value of %s",
                                    TriggerHeaderContract.TRIGGER_CONTEXT_ID,
                                    TriggerHeaderContract.AGGREGATABLE_SOURCE_REGISTRATION_TIME,
                                    Trigger.SourceRegistrationTimeConfig.INCLUDE.name());
                    asyncFetchStatus.setEntityStatus(
                            AsyncFetchStatus.EntityStatus.VALIDATION_ERROR);
                    return Optional.empty();
                }

                Optional<String> contextIdOpt = getValidTriggerContextId(json);
                if (contextIdOpt.isEmpty()) {
                    asyncFetchStatus.setEntityStatus(
                            AsyncFetchStatus.EntityStatus.VALIDATION_ERROR);
                    return Optional.empty();
                }

                builder.setTriggerContextId(contextIdOpt.get());
            }

            if (mFlags.getMeasurementEnableAttributionScope()
                    && !json.isNull(TriggerHeaderContract.ATTRIBUTION_SCOPES)) {
                Optional<List<String>> attributionScopes =
                        FetcherUtil.extractStringArray(
                                json,
                                TriggerHeaderContract.ATTRIBUTION_SCOPES,
                                mFlags.getMeasurementMaxAttributionScopesPerSource(),
                                mFlags.getMeasurementMaxAttributionScopeLength());
                if (attributionScopes.isEmpty() || attributionScopes.get().isEmpty()) {
                    LoggerFactory.getMeasurementLogger()
                            .e("parseTrigger: attribution_scopes is invalid.");
                    asyncFetchStatus.setEntityStatus(
                            AsyncFetchStatus.EntityStatus.VALIDATION_ERROR);
                    return Optional.empty();
                }
                builder.setAttributionScopesString(
                        json.getJSONArray(TriggerHeaderContract.ATTRIBUTION_SCOPES).toString());
            }

            asyncFetchStatus.setEntityStatus(AsyncFetchStatus.EntityStatus.SUCCESS);
            return Optional.of(builder.build());
        } catch (JSONException e) {
            String errMsg = "Trigger JSON parsing failed";
            LoggerFactory.getMeasurementLogger().e(e, errMsg);
            asyncFetchStatus.setEntityStatus(AsyncFetchStatus.EntityStatus.PARSING_ERROR);
            if (isHeaderErrorDebugReportEnabled) {
                mDatastoreManager.runInTransaction(
                        (dao) -> {
                            mDebugReportApi.scheduleHeaderErrorReport(
                                    asyncRegistration.getRegistrationUri(),
                                    asyncRegistration.getRegistrant(),
                                    TriggerHeaderContract
                                            .HEADER_ATTRIBUTION_REPORTING_REGISTER_TRIGGER,
                                    enrollmentId,
                                    errMsg,
                                    registrationHeaderStr,
                                    dao);
                        });
            }
            return Optional.empty();
        } catch (IllegalArgumentException e) {
            LoggerFactory.getMeasurementLogger()
                    .d(e, "AsyncTriggerFetcher: IllegalArgumentException");
            asyncFetchStatus.setEntityStatus(AsyncFetchStatus.EntityStatus.VALIDATION_ERROR);
            return Optional.empty();
        }
    }

    private Optional<String> getValidTriggerContextId(JSONObject json) throws JSONException {
        Object triggerContextIdObj = json.get(TriggerHeaderContract.TRIGGER_CONTEXT_ID);
        if (!(triggerContextIdObj instanceof String)) {
            LoggerFactory.getMeasurementLogger()
                    .d(
                            "%s: %s, is not a String",
                            TriggerHeaderContract.TRIGGER_CONTEXT_ID,
                            json.get(TriggerHeaderContract.TRIGGER_CONTEXT_ID).toString());
            return Optional.empty();
        }

        String contextId = triggerContextIdObj.toString();
        if (contextId.length() > mFlags.getMeasurementMaxLengthOfTriggerContextId()) {
            LoggerFactory.getMeasurementLogger()
                    .d(
                            "Length of %s: \"%s\", exceeds max length of %d",
                            TriggerHeaderContract.TRIGGER_CONTEXT_ID,
                            contextId,
                            mFlags.getMeasurementMaxLengthOfTriggerContextId());
            return Optional.empty();
        }

        return Optional.of(contextId);
    }

    private Trigger.SourceRegistrationTimeConfig getSourceRegistrationTimeConfig(JSONObject json) {
        Trigger.SourceRegistrationTimeConfig sourceRegistrationTimeConfig =
                Trigger.SourceRegistrationTimeConfig.INCLUDE;

        if (mFlags.getMeasurementSourceRegistrationTimeOptionalForAggReportsEnabled()) {
            sourceRegistrationTimeConfig = Trigger.SourceRegistrationTimeConfig.EXCLUDE;
            if (!json.isNull(TriggerHeaderContract.AGGREGATABLE_SOURCE_REGISTRATION_TIME)) {
                String sourceRegistrationTimeConfigString =
                        json.optString(TriggerHeaderContract.AGGREGATABLE_SOURCE_REGISTRATION_TIME)
                                .toUpperCase(Locale.ENGLISH);
                sourceRegistrationTimeConfig =
                        Trigger.SourceRegistrationTimeConfig.valueOf(
                                sourceRegistrationTimeConfigString);
            }
        }

        return sourceRegistrationTimeConfig;
    }

    private boolean isAllowlisted(String allowlist, String origin) {
        if (AllowLists.doesAllowListAllowAll(allowlist)) {
            return true;
        }
        Set<String> elements = new HashSet<>(AllowLists.splitAllowList(allowlist));
        return elements.contains(origin);
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

    /**
     * Fetch a trigger type registration.
     *
     * @param asyncRegistration a {@link AsyncRegistration}, a request the record.
     * @param asyncFetchStatus a {@link AsyncFetchStatus}, stores Ad Tech server status.
     * @param asyncRedirects a {@link AsyncRedirects}, stores redirections.
     */
    public Optional<Trigger> fetchTrigger(
            AsyncRegistration asyncRegistration,
            AsyncFetchStatus asyncFetchStatus,
            AsyncRedirects asyncRedirects) {
        HttpURLConnection urlConnection = null;
        Map<String, List<String>> headers;
        if (!asyncRegistration.getRegistrationUri().getScheme().equalsIgnoreCase("https")) {
            LoggerFactory.getMeasurementLogger().d("Invalid scheme for registrationUri.");
            asyncFetchStatus.setResponseStatus(AsyncFetchStatus.ResponseStatus.INVALID_URL);
            return Optional.empty();
        }
        // TODO(b/276825561): Fix code duplication between fetchSource & fetchTrigger request flow
        Optional<String> enrollmentId = Optional.empty();
        try {
            urlConnection =
                    (HttpURLConnection)
                            openUrl(new URL(asyncRegistration.getRegistrationUri().toString()));
            urlConnection.setRequestMethod("POST");
            urlConnection.setInstanceFollowRedirects(false);
            headers = urlConnection.getHeaderFields();
            enrollmentId = getEnrollmentId(asyncRegistration);

            // get ODP header from headers map and forward ODP header
            long odpHeaderSize = 0;
            Optional<Map<String, List<String>>> odpHeader = getOdpTriggerHeader(headers);
            if (odpHeader.isPresent()) {
                mOdpWrapper.registerOdpTrigger(
                        asyncRegistration, odpHeader.get(), enrollmentId.isPresent());
                odpHeaderSize = FetcherUtil.calculateHeadersCharactersLength(odpHeader.get());
            }

            long headerSize = FetcherUtil.calculateHeadersCharactersLength(headers) - odpHeaderSize;
            if (mFlags.getMeasurementEnableUpdateTriggerHeaderLimit()
                    && headerSize > mFlags.getMaxTriggerRegistrationHeaderSizeBytes()) {
                LoggerFactory.getMeasurementLogger()
                        .d(
                                "Trigger registration header size exceeds limit bytes "
                                        + mFlags.getMaxTriggerRegistrationHeaderSizeBytes());
                asyncFetchStatus.setResponseStatus(
                        AsyncFetchStatus.ResponseStatus.HEADER_SIZE_LIMIT_EXCEEDED);
                return Optional.empty();
            }
            asyncFetchStatus.setResponseSize(headerSize);
            int responseCode = urlConnection.getResponseCode();
            LoggerFactory.getMeasurementLogger().d("Response code = " + responseCode);
            if (!FetcherUtil.isRedirect(responseCode) && !FetcherUtil.isSuccess(responseCode)) {
                asyncFetchStatus.setResponseStatus(
                        AsyncFetchStatus.ResponseStatus.SERVER_UNAVAILABLE);
                return Optional.empty();
            }
            asyncFetchStatus.setResponseStatus(AsyncFetchStatus.ResponseStatus.SUCCESS);
        } catch (MalformedURLException e) {
            LoggerFactory.getMeasurementLogger().d(e, "Malformed registration target URL");
            asyncFetchStatus.setResponseStatus(AsyncFetchStatus.ResponseStatus.INVALID_URL);
            return Optional.empty();
        } catch (IOException e) {
            LoggerFactory.getMeasurementLogger().d(e, "Failed to get registration response");
            asyncFetchStatus.setResponseStatus(AsyncFetchStatus.ResponseStatus.NETWORK_ERROR);
            return Optional.empty();
        } finally {
            if (urlConnection != null) {
                urlConnection.disconnect();
            }
        }

        asyncRedirects.configure(headers, mFlags, asyncRegistration);

        if (!isTriggerHeaderPresent(headers)) {
            asyncFetchStatus.setEntityStatus(AsyncFetchStatus.EntityStatus.HEADER_MISSING);
            asyncFetchStatus.setRedirectOnlyStatus(true);
            return Optional.empty();
        }

        if (enrollmentId.isEmpty()) {
            LoggerFactory.getMeasurementLogger()
                    .d(
                            "fetchTrigger: Valid enrollment id not found. Registration URI: %s",
                            asyncRegistration.getRegistrationUri());
            asyncFetchStatus.setEntityStatus(AsyncFetchStatus.EntityStatus.INVALID_ENROLLMENT);
            ErrorLogUtil.e(
                    AD_SERVICES_ERROR_REPORTED__ERROR_CODE__ENROLLMENT_INVALID,
                    AD_SERVICES_ERROR_REPORTED__PPAPI_NAME__MEASUREMENT);
            return Optional.empty();
        }
        return parseTrigger(asyncRegistration, enrollmentId.get(), headers, asyncFetchStatus);
    }

    /** Return instance of IOdpDelegationWrapper. */
    public IOdpDelegationWrapper getOdpWrapper() {
        return mOdpWrapper;
    }

    private boolean isTriggerHeaderPresent(Map<String, List<String>> headers) {
        return headers.containsKey(
                TriggerHeaderContract.HEADER_ATTRIBUTION_REPORTING_REGISTER_TRIGGER);
    }

    private Optional<Map<String, List<String>>> getOdpTriggerHeader(
            Map<String, List<String>> headers) {
        return headers.containsKey(OdpTriggerHeaderContract.HEADER_ODP_REGISTER_TRIGGER)
                ? Optional.of(Map.of(
                        OdpTriggerHeaderContract.HEADER_ODP_REGISTER_TRIGGER,
                        headers.get(
                                OdpTriggerHeaderContract.HEADER_ODP_REGISTER_TRIGGER)))
                : Optional.empty();
    }

    private Optional<String> getEnrollmentId(AsyncRegistration asyncRegistration) {
        return mFlags.isDisableMeasurementEnrollmentCheck()
                ? WebAddresses.topPrivateDomainAndScheme(asyncRegistration.getRegistrationUri())
                        .map(Uri::toString)
                : Enrollment.getValidEnrollmentId(
                        asyncRegistration.getRegistrationUri(),
                        asyncRegistration.getRegistrant().getAuthority(),
                        mEnrollmentDao,
                        mContext,
                        mFlags);
    }

    private Optional<String> getValidEventTriggerData(JSONArray eventTriggerDataArr) {
        JSONArray validEventTriggerData = new JSONArray();
        for (int i = 0; i < eventTriggerDataArr.length(); i++) {
            JSONObject validEventTriggerDatum = new JSONObject();
            try {
                JSONObject eventTriggerDatum = eventTriggerDataArr.getJSONObject(i);
                UnsignedLong triggerData = new UnsignedLong(0L);
                if (!eventTriggerDatum.isNull("trigger_data")) {
                    Optional<UnsignedLong> maybeTriggerData =
                            FetcherUtil.extractUnsignedLong(eventTriggerDatum, "trigger_data");
                    if (!maybeTriggerData.isPresent()) {
                        return Optional.empty();
                    }
                    triggerData = maybeTriggerData.get();
                }
                validEventTriggerDatum.put("trigger_data", triggerData);
                if (!eventTriggerDatum.isNull("priority")) {
                    Optional<Long> maybePriority =
                            FetcherUtil.extractLongString(eventTriggerDatum, "priority");
                    if (!maybePriority.isPresent()) {
                        return Optional.empty();
                    }
                    validEventTriggerDatum.put("priority", String.valueOf(maybePriority.get()));
                }
                if (!eventTriggerDatum.isNull("value")) {
                    Optional<Long> maybeValue =
                            FetcherUtil.extractLong(eventTriggerDatum, "value");
                    if (!maybeValue.isPresent()) {
                        return Optional.empty();
                    }
                    long value = maybeValue.get();
                    if (value < 1L || value > TriggerSpecs.MAX_BUCKET_THRESHOLD) {
                        return Optional.empty();
                    }
                    validEventTriggerDatum.put("value", value);
                }
                if (!eventTriggerDatum.isNull("deduplication_key")) {
                    Optional<UnsignedLong> maybeDedupKey = FetcherUtil.extractUnsignedLong(
                            eventTriggerDatum, "deduplication_key");
                    if (!maybeDedupKey.isPresent()) {
                        return Optional.empty();
                    }
                    validEventTriggerDatum.put("deduplication_key", maybeDedupKey.get());
                }
                boolean shouldCheckFilterSize =
                        !mFlags.getMeasurementEnableUpdateTriggerHeaderLimit();
                if (!eventTriggerDatum.isNull("filters")) {
                    JSONArray filters = Filter.maybeWrapFilters(eventTriggerDatum, "filters");
                    if (!FetcherUtil.areValidAttributionFilters(
                            filters,
                            mFlags,
                            /* canIncludeLookbackWindow= */ true,
                            shouldCheckFilterSize)) {
                        LoggerFactory.getMeasurementLogger()
                                .d("getValidEventTriggerData: filters are invalid.");
                        return Optional.empty();
                    }
                    validEventTriggerDatum.put("filters", filters);
                }
                if (!eventTriggerDatum.isNull("not_filters")) {
                    JSONArray notFilters =
                            Filter.maybeWrapFilters(eventTriggerDatum, "not_filters");
                    if (!FetcherUtil.areValidAttributionFilters(
                            notFilters,
                            mFlags,
                            /* canIncludeLookbackWindow= */ true,
                            shouldCheckFilterSize)) {
                        LoggerFactory.getMeasurementLogger()
                                .d("getValidEventTriggerData: not-filters are invalid.");
                        return Optional.empty();
                    }
                    validEventTriggerDatum.put("not_filters", notFilters);
                }
                validEventTriggerData.put(validEventTriggerDatum);
            } catch (JSONException e) {
                LoggerFactory.getMeasurementLogger()
                        .d(e, "AsyncTriggerFetcher: JSONException parsing event trigger datum.");
                return Optional.empty();
            } catch (NumberFormatException e) {
                LoggerFactory.getMeasurementLogger()
                        .d(
                                e,
                                "AsyncTriggerFetcher: NumberFormatException parsing event trigger "
                                        + "datum.");
                return Optional.empty();
            }
        }
        return Optional.of(validEventTriggerData.toString());
    }

    private Optional<String> getValidAggregateTriggerData(JSONArray aggregateTriggerDataArr)
            throws JSONException {
        JSONArray validAggregateTriggerData = new JSONArray();
        boolean shouldCheckFilterSize = !mFlags.getMeasurementEnableUpdateTriggerHeaderLimit();
        for (int i = 0; i < aggregateTriggerDataArr.length(); i++) {
            JSONObject aggregateTriggerData = aggregateTriggerDataArr.getJSONObject(i);
            String keyPiece = aggregateTriggerData.optString("key_piece");
            if (!FetcherUtil.isValidAggregateKeyPiece(keyPiece, mFlags)) {
                LoggerFactory.getMeasurementLogger()
                        .d("Aggregate trigger data key-piece is invalid. %s", keyPiece);
                return Optional.empty();
            }
            JSONArray sourceKeys;
            if (aggregateTriggerData.isNull("source_keys")) {
                sourceKeys = new JSONArray();
                aggregateTriggerData.put("source_keys", sourceKeys);
            } else {
                // Registration will be rejected if source-keys is not a list
                sourceKeys = aggregateTriggerData.getJSONArray("source_keys");
            }
            if (shouldCheckFilterSize
                    && sourceKeys.length()
                            > mFlags.getMeasurementMaxAggregateKeysPerTriggerRegistration()) {
                LoggerFactory.getMeasurementLogger()
                        .d(
                                "Aggregate trigger data source-keys list has more entries "
                                        + "than permitted.");
                return Optional.empty();
            }
            for (int j = 0; j < sourceKeys.length(); j++) {
                Object sourceKey = sourceKeys.get(j);
                if (!(sourceKey instanceof String)
                        || !FetcherUtil.isValidAggregateKeyId((String) sourceKey)) {
                    LoggerFactory.getMeasurementLogger()
                            .d("Aggregate trigger data source-key is invalid. %s", sourceKey);
                    return Optional.empty();
                }
            }
            if (!aggregateTriggerData.isNull("filters")) {
                JSONArray filters = Filter.maybeWrapFilters(aggregateTriggerData, "filters");
                if (!FetcherUtil.areValidAttributionFilters(
                        filters,
                        mFlags,
                        /* canIncludeLookbackWindow= */ true,
                        shouldCheckFilterSize)) {
                    LoggerFactory.getMeasurementLogger()
                            .d("Aggregate trigger data filters are invalid.");
                    return Optional.empty();
                }
                aggregateTriggerData.put("filters", filters);
            }
            if (!aggregateTriggerData.isNull("not_filters")) {
                JSONArray notFilters = Filter.maybeWrapFilters(aggregateTriggerData, "not_filters");
                if (!FetcherUtil.areValidAttributionFilters(
                        notFilters,
                        mFlags,
                        /* canIncludeLookbackWindow= */ true,
                        shouldCheckFilterSize)) {
                    LoggerFactory.getMeasurementLogger()
                            .d("Aggregate trigger data not-filters are invalid.");
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

    private boolean isValidAggregateValues(JSONObject aggregateValues) throws JSONException {
        if (!mFlags.getMeasurementEnableUpdateTriggerHeaderLimit()
                && aggregateValues.length()
                        > mFlags.getMeasurementMaxAggregateKeysPerTriggerRegistration()) {
            LoggerFactory.getMeasurementLogger()
                    .d(
                            "Aggregate values have more keys than permitted. %s",
                            aggregateValues.length());
            return false;
        }
        Iterator<String> ids = aggregateValues.keys();
        while (ids.hasNext()) {
            String id = ids.next();
            if (!FetcherUtil.isValidAggregateKeyId(id)) {
                LoggerFactory.getMeasurementLogger()
                        .d("Aggregate values key ID is invalid. %s", id);
                return false;
            }
            Object maybeInt = aggregateValues.get(id);
            if (!(maybeInt instanceof Integer)
                    || ((Integer) maybeInt) < 1
                    || ((Integer) maybeInt)
                            > mFlags.getMeasurementMaxSumOfAggregateValuesPerSource()) {
                LoggerFactory.getMeasurementLogger()
                        .d("Aggregate values '%s' is invalid. %s", id, maybeInt);
                return false;
            }
        }
        return true;
    }

    private Optional<String> getValidAggregateDuplicationKeysString(
            JSONArray aggregateDeduplicationKeys) throws JSONException {
        JSONArray validAggregateDeduplicationKeys = new JSONArray();
        boolean shouldCheckFilterSize = !mFlags.getMeasurementEnableUpdateTriggerHeaderLimit();
        if (shouldCheckFilterSize
                && aggregateDeduplicationKeys.length()
                        > mFlags.getMeasurementMaxAggregateDeduplicationKeysPerRegistration()) {
            LoggerFactory.getMeasurementLogger()
                    .d(
                            "Aggregate deduplication keys have more keys than permitted. %s",
                            aggregateDeduplicationKeys.length());
            return Optional.empty();
        }
        for (int i = 0; i < aggregateDeduplicationKeys.length(); i++) {
            JSONObject aggregateDedupKey = new JSONObject();
            JSONObject deduplicationKeyObj = aggregateDeduplicationKeys.getJSONObject(i);

            if (!deduplicationKeyObj.isNull("deduplication_key")) {
                Optional<UnsignedLong> maybeDedupKey = FetcherUtil.extractUnsignedLong(
                        deduplicationKeyObj, "deduplication_key");
                if (!maybeDedupKey.isPresent()) {
                    return Optional.empty();
                }
                aggregateDedupKey.put("deduplication_key", maybeDedupKey.get().toString());
            }
            if (!deduplicationKeyObj.isNull("filters")) {
                JSONArray filters = Filter.maybeWrapFilters(deduplicationKeyObj, "filters");
                if (!FetcherUtil.areValidAttributionFilters(
                        filters,
                        mFlags,
                        /* canIncludeLookbackWindow= */ true,
                        shouldCheckFilterSize)) {
                    LoggerFactory.getMeasurementLogger()
                            .d("Aggregate deduplication key: " + i + " contains invalid filters.");
                    return Optional.empty();
                }
                aggregateDedupKey.put("filters", filters);
            }
            if (!deduplicationKeyObj.isNull("not_filters")) {
                JSONArray notFilters = Filter.maybeWrapFilters(deduplicationKeyObj, "not_filters");
                if (!FetcherUtil.areValidAttributionFilters(
                        notFilters,
                        mFlags,
                        /* canIncludeLookbackWindow= */ true,
                        shouldCheckFilterSize)) {
                    LoggerFactory.getMeasurementLogger()
                            .d(
                                    "Aggregate deduplication key: "
                                            + i
                                            + " contains invalid not filters.");
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
                    new AttributionConfig.Builder(attributionConfigsArray.getJSONObject(i), mFlags)
                            .build();
            validAttributionConfigsArray.put(attributionConfig.serializeAsJson(mFlags));
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

    private static IOdpDelegationWrapper getOdpDelegationManager(Context context, Flags flags) {
        if (!SdkLevel.isAtLeastT() || !flags.getMeasurementEnableOdpWebTriggerRegistration()) {
            return new NoOdpDelegationWrapper();
        }

        OnDevicePersonalizationSystemEventManager odpSystemEventManager = null;
        try {
            odpSystemEventManager =
                    context.getSystemService(OnDevicePersonalizationSystemEventManager.class);
        } catch (Exception e) {
            LoggerFactory.getMeasurementLogger().d(e, "getOdpDelegationManager: Unknown Exception");
            ErrorLogUtil.e(
                    AD_SERVICES_ERROR_REPORTED__ERROR_CODE__MEASUREMENT_REGISTRATION_ODP_GET_MANAGER_ERROR,
                    AD_SERVICES_ERROR_REPORTED__PPAPI_NAME__MEASUREMENT);
        }
        return (odpSystemEventManager != null)
                ? new OdpDelegationWrapperImpl(odpSystemEventManager)
                : new NoOdpDelegationWrapper();
    }

    private interface TriggerHeaderContract {
        String HEADER_ATTRIBUTION_REPORTING_REGISTER_TRIGGER =
                "Attribution-Reporting-Register-Trigger";
        // Header for verbose debug reports.
        String HEADER_ATTRIBUTION_REPORTING_INFO = "Attribution-Reporting-Info";
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
        String DEBUG_AD_ID = "debug_ad_id";
        String AGGREGATION_COORDINATOR_ORIGIN = "aggregation_coordinator_origin";
        String AGGREGATABLE_SOURCE_REGISTRATION_TIME = "aggregatable_source_registration_time";
        String TRIGGER_CONTEXT_ID = "trigger_context_id";
        String ATTRIBUTION_SCOPES = "attribution_scopes";
    }

    private interface OdpTriggerHeaderContract {
        String HEADER_ODP_REGISTER_TRIGGER = "Odp-Register-Trigger";
    }
}

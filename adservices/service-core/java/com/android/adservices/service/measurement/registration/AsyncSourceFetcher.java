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

import static com.android.adservices.service.measurement.PrivacyParams.MAX_DISTINCT_WEB_DESTINATIONS_IN_SOURCE_REGISTRATION;
import static com.android.adservices.service.measurement.PrivacyParams.MAX_INSTALL_ATTRIBUTION_WINDOW;
import static com.android.adservices.service.measurement.PrivacyParams.MAX_POST_INSTALL_EXCLUSIVITY_WINDOW;
import static com.android.adservices.service.measurement.PrivacyParams.MAX_REPORTING_REGISTER_SOURCE_EXPIRATION_IN_SECONDS;
import static com.android.adservices.service.measurement.PrivacyParams.MIN_INSTALL_ATTRIBUTION_WINDOW;
import static com.android.adservices.service.measurement.PrivacyParams.MIN_POST_INSTALL_EXCLUSIVITY_WINDOW;
import static com.android.adservices.service.measurement.PrivacyParams.MIN_REPORTING_REGISTER_SOURCE_EXPIRATION_IN_SECONDS;
import static com.android.adservices.service.measurement.ReportSpec.encodeTriggerSpecsToJson;
import static com.android.adservices.service.measurement.Source.getOrDefaultEventReportWindows;
import static com.android.adservices.service.measurement.TriggerSpec.getLongArrayFromJSON;
import static com.android.adservices.service.measurement.TriggerSpec.getTriggerDataArrayFromJSON;
import static com.android.adservices.service.measurement.util.BaseUriExtractor.getBaseUri;
import static com.android.adservices.service.measurement.util.MathUtils.extractValidNumberInRange;

import static java.lang.Math.min;

import android.annotation.NonNull;
import android.content.Context;
import android.net.Uri;
import android.util.Pair;

import com.android.adservices.LogUtil;
import com.android.adservices.data.enrollment.EnrollmentDao;
import com.android.adservices.service.Flags;
import com.android.adservices.service.FlagsFactory;
import com.android.adservices.service.common.AllowLists;
import com.android.adservices.service.common.WebAddresses;
import com.android.adservices.service.measurement.EventSurfaceType;
import com.android.adservices.service.measurement.MeasurementHttpClient;
import com.android.adservices.service.measurement.ReportSpecUtil;
import com.android.adservices.service.measurement.Source;
import com.android.adservices.service.measurement.TriggerSpec;
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
import java.util.Collections;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.Set;
import java.util.concurrent.TimeUnit;
import java.util.stream.Collectors;

/**
 * Download and decode Response Based Registration
 *
 * @hide
 */
public class AsyncSourceFetcher {

    private static final long ONE_DAY_IN_SECONDS = TimeUnit.DAYS.toSeconds(1);
    private static final String DEFAULT_ANDROID_APP_SCHEME = "android-app";
    private static final String DEFAULT_ANDROID_APP_URI_PREFIX = DEFAULT_ANDROID_APP_SCHEME + "://";
    private final MeasurementHttpClient mNetworkConnection = new MeasurementHttpClient();
    private final EnrollmentDao mEnrollmentDao;
    private final Flags mFlags;
    private final AdServicesLogger mLogger;
    private final Context mContext;

    public AsyncSourceFetcher(Context context) {
        this(
                context,
                EnrollmentDao.getInstance(context),
                FlagsFactory.getFlags(),
                AdServicesLoggerImpl.getInstance());
    }

    @VisibleForTesting
    public AsyncSourceFetcher(
            Context context, EnrollmentDao enrollmentDao, Flags flags, AdServicesLogger logger) {
        mContext = context;
        mEnrollmentDao = enrollmentDao;
        mFlags = flags;
        mLogger = logger;
    }

    private boolean parseCommonSourceParams(
            JSONObject json,
            AsyncRegistration asyncRegistration,
            Source.Builder builder,
            String enrollmentId)
            throws JSONException {
        if (json.isNull(SourceHeaderContract.DESTINATION)
                && json.isNull(SourceHeaderContract.WEB_DESTINATION)) {
            throw new JSONException("Expected a destination");
        }
        long sourceEventTime = asyncRegistration.getRequestTime();
        UnsignedLong eventId = new UnsignedLong(0L);
        if (!json.isNull(SourceHeaderContract.SOURCE_EVENT_ID)) {
            if (mFlags.getMeasurementEnableAraParsingAlignmentV1()) {
                Optional<UnsignedLong> maybeEventId =
                        FetcherUtil.extractUnsignedLong(json, SourceHeaderContract.SOURCE_EVENT_ID);
                if (!maybeEventId.isPresent()) {
                    return false;
                }
                eventId = maybeEventId.get();
            } else {
                try {
                    eventId = new UnsignedLong(
                            json.getString(SourceHeaderContract.SOURCE_EVENT_ID));
                } catch (NumberFormatException e) {
                    LogUtil.d(e, "parseCommonSourceParams: parsing source_event_id failed.");
                }
            }
        }
        builder.setEventId(eventId);
        long expiry;
        if (!json.isNull(SourceHeaderContract.EXPIRY)) {
            if (mFlags.getMeasurementEnableAraParsingAlignmentV1()) {
                UnsignedLong expiryUnsigned =
                        extractValidNumberInRange(
                                new UnsignedLong(json.getString(SourceHeaderContract.EXPIRY)),
                                new UnsignedLong(
                                        MIN_REPORTING_REGISTER_SOURCE_EXPIRATION_IN_SECONDS),
                                new UnsignedLong(
                                        MAX_REPORTING_REGISTER_SOURCE_EXPIRATION_IN_SECONDS));
                // Relies on expiryUnsigned not using the 64th bit.
                expiry = expiryUnsigned.getValue();
            } else {
                expiry =
                        extractValidNumberInRange(
                                json.getLong(SourceHeaderContract.EXPIRY),
                                MIN_REPORTING_REGISTER_SOURCE_EXPIRATION_IN_SECONDS,
                                MAX_REPORTING_REGISTER_SOURCE_EXPIRATION_IN_SECONDS);
            }
            if (asyncRegistration.getSourceType() == Source.SourceType.EVENT) {
                expiry = roundSecondsToWholeDays(expiry);
            }
        } else {
            expiry = MAX_REPORTING_REGISTER_SOURCE_EXPIRATION_IN_SECONDS;
        }
        builder.setExpiryTime(sourceEventTime + TimeUnit.SECONDS.toMillis(expiry));
        if (!json.isNull(SourceHeaderContract.EVENT_REPORT_WINDOW)) {
            long eventReportWindow;
            if (mFlags.getMeasurementEnableAraParsingAlignmentV1()) {
                UnsignedLong eventReportWindowUnsigned =
                        extractValidNumberInRange(
                                new UnsignedLong(
                                        json.getString(SourceHeaderContract.EVENT_REPORT_WINDOW)),
                                new UnsignedLong(
                                        mFlags.getMeasurementMinimumEventReportWindowInSeconds()),
                                new UnsignedLong(
                                        MAX_REPORTING_REGISTER_SOURCE_EXPIRATION_IN_SECONDS));
                // Relies on eventReportWindowUnsigned not using the 64th bit.
                eventReportWindow = min(expiry, eventReportWindowUnsigned.getValue());
            } else {
                eventReportWindow =
                        Math.min(
                                expiry,
                                extractValidNumberInRange(
                                        json.getLong(SourceHeaderContract.EVENT_REPORT_WINDOW),
                                        mFlags.getMeasurementMinimumEventReportWindowInSeconds(),
                                        MAX_REPORTING_REGISTER_SOURCE_EXPIRATION_IN_SECONDS));
            }
            builder.setEventReportWindow(TimeUnit.SECONDS.toMillis(eventReportWindow));
        }
        long aggregateReportWindow;
        if (!json.isNull(SourceHeaderContract.AGGREGATABLE_REPORT_WINDOW)) {
            if (mFlags.getMeasurementEnableAraParsingAlignmentV1()) {
                // Registration will be rejected if parsing unsigned long throws.
                UnsignedLong aggregateReportWindowUnsigned =
                        extractValidNumberInRange(
                                new UnsignedLong(json.getString(
                                        SourceHeaderContract.AGGREGATABLE_REPORT_WINDOW)),
                                new UnsignedLong(
                                        MIN_REPORTING_REGISTER_SOURCE_EXPIRATION_IN_SECONDS),
                                new UnsignedLong(
                                        MAX_REPORTING_REGISTER_SOURCE_EXPIRATION_IN_SECONDS));
                // Relies on aggregateReportWindowUnsigned not using the 64th bit.
                aggregateReportWindow = min(expiry, aggregateReportWindowUnsigned.getValue());
            } else {
                aggregateReportWindow =
                        min(
                                expiry,
                                extractValidNumberInRange(
                                        json.getLong(
                                                SourceHeaderContract.AGGREGATABLE_REPORT_WINDOW),
                                        MIN_REPORTING_REGISTER_SOURCE_EXPIRATION_IN_SECONDS,
                                        MAX_REPORTING_REGISTER_SOURCE_EXPIRATION_IN_SECONDS));
            }
        } else {
            aggregateReportWindow = expiry;
        }
        builder.setAggregatableReportWindow(
                sourceEventTime + TimeUnit.SECONDS.toMillis(aggregateReportWindow));

        if (!json.isNull(SourceHeaderContract.PRIORITY)) {
            if (mFlags.getMeasurementEnableAraParsingAlignmentV1()) {
                Optional<Long> maybePriority =
                        FetcherUtil.extractLong(json, SourceHeaderContract.PRIORITY);
                if (!maybePriority.isPresent()) {
                    return false;
                }
                builder.setPriority(maybePriority.get());
            } else {
                builder.setPriority(json.getLong(SourceHeaderContract.PRIORITY));
            }
        }

        if (!json.isNull(SourceHeaderContract.DEBUG_REPORTING)) {
            builder.setIsDebugReporting(json.optBoolean(SourceHeaderContract.DEBUG_REPORTING));
        }
        if (!json.isNull(SourceHeaderContract.DEBUG_KEY)) {
            if (mFlags.getMeasurementEnableAraParsingAlignmentV1()) {
                Optional<UnsignedLong> maybeDebugKey =
                        FetcherUtil.extractUnsignedLong(json, SourceHeaderContract.DEBUG_KEY);
                if (maybeDebugKey.isPresent()) {
                    builder.setDebugKey(maybeDebugKey.get());
                }
            } else {
                try {
                    builder.setDebugKey(
                            new UnsignedLong(json.getString(SourceHeaderContract.DEBUG_KEY)));
                } catch (NumberFormatException e) {
                    LogUtil.e(e, "parseCommonSourceParams: parsing debug key failed");
                }
            }
        }
        if (!json.isNull(SourceHeaderContract.INSTALL_ATTRIBUTION_WINDOW_KEY)) {
            long installAttributionWindow =
                    extractValidNumberInRange(
                            json.getLong(SourceHeaderContract.INSTALL_ATTRIBUTION_WINDOW_KEY),
                            MIN_INSTALL_ATTRIBUTION_WINDOW,
                            MAX_INSTALL_ATTRIBUTION_WINDOW);
            builder.setInstallAttributionWindow(
                    TimeUnit.SECONDS.toMillis(installAttributionWindow));
        } else {
            builder.setInstallAttributionWindow(
                    TimeUnit.SECONDS.toMillis(MAX_INSTALL_ATTRIBUTION_WINDOW));
        }
        if (!json.isNull(SourceHeaderContract.POST_INSTALL_EXCLUSIVITY_WINDOW_KEY)) {
            long installCooldownWindow =
                    extractValidNumberInRange(
                            json.getLong(SourceHeaderContract.POST_INSTALL_EXCLUSIVITY_WINDOW_KEY),
                            MIN_POST_INSTALL_EXCLUSIVITY_WINDOW,
                            MAX_POST_INSTALL_EXCLUSIVITY_WINDOW);
            builder.setInstallCooldownWindow(TimeUnit.SECONDS.toMillis(installCooldownWindow));
        } else {
            builder.setInstallCooldownWindow(
                    TimeUnit.SECONDS.toMillis(MIN_POST_INSTALL_EXCLUSIVITY_WINDOW));
        }
        // This "filter_data" field is used to generate reports.
        if (!json.isNull(SourceHeaderContract.FILTER_DATA)) {
            if (mFlags.getMeasurementEnableAraParsingAlignmentV1()) {
                JSONObject maybeFilterData = json.optJSONObject(SourceHeaderContract.FILTER_DATA);
                if (maybeFilterData != null && maybeFilterData.has("source_type")) {
                    LogUtil.d("Source filter-data includes 'source_type' key.");
                    return false;
                }
                if (!FetcherUtil.areValidAttributionFilters(maybeFilterData)) {
                    LogUtil.d("Source filter-data is invalid.");
                    return false;
                }
                builder.setFilterData(maybeFilterData.toString());
            } else {
                if (!FetcherUtil.areValidAttributionFilters(
                        json.optJSONObject(SourceHeaderContract.FILTER_DATA))) {
                    LogUtil.d("Source filter-data is invalid.");
                    return false;
                }
                builder.setFilterData(
                        json.getJSONObject(SourceHeaderContract.FILTER_DATA).toString());
            }
        }

        Uri appUri = null;
        if (!json.isNull(SourceHeaderContract.DESTINATION)) {
            appUri = Uri.parse(json.getString(SourceHeaderContract.DESTINATION));
            if (appUri.getScheme() == null) {
                LogUtil.d("App destination is missing app scheme, adding.");
                appUri = Uri.parse(DEFAULT_ANDROID_APP_URI_PREFIX + appUri);
            }
            if (!DEFAULT_ANDROID_APP_SCHEME.equals(appUri.getScheme())) {
                LogUtil.e(
                        "Invalid scheme for app destination: %s; dropping the source.",
                        appUri.getScheme());
                return false;
            }
        }

        String enrollmentBlockList =
                mFlags.getMeasurementPlatformDebugAdIdMatchingEnrollmentBlocklist();
        Set<String> blockedEnrollmentsString =
                new HashSet<>(AllowLists.splitAllowList(enrollmentBlockList));
        if (!AllowLists.doesAllowListAllowAll(enrollmentBlockList)
                && !blockedEnrollmentsString.contains(enrollmentId)
                && !json.isNull(SourceHeaderContract.DEBUG_AD_ID)) {
            builder.setDebugAdId(json.optString(SourceHeaderContract.DEBUG_AD_ID));
        }

        Set<String> allowedEnrollmentsString =
                new HashSet<>(
                        AllowLists.splitAllowList(
                                mFlags.getMeasurementDebugJoinKeyEnrollmentAllowlist()));
        if (allowedEnrollmentsString.contains(enrollmentId)
                && !json.isNull(SourceHeaderContract.DEBUG_JOIN_KEY)) {
            builder.setDebugJoinKey(json.optString(SourceHeaderContract.DEBUG_JOIN_KEY));
        }

        if (asyncRegistration.isWebRequest()
                // Only validate when non-null in request
                && asyncRegistration.getOsDestination() != null
                && !asyncRegistration.getOsDestination().equals(appUri)) {
            LogUtil.d("Expected destination to match with the supplied one!");
            return false;
        }

        if (appUri != null) {
            builder.setAppDestinations(Collections.singletonList(getBaseUri(appUri)));
        }

        boolean shouldMatchAtLeastOneWebDestination =
                asyncRegistration.isWebRequest() && asyncRegistration.getWebDestination() != null;
        boolean matchedOneWebDestination = false;

        if (!json.isNull(SourceHeaderContract.WEB_DESTINATION)) {
            Set<Uri> destinationSet = new HashSet<>();
            JSONArray jsonDestinations;
            Object obj = json.get(SourceHeaderContract.WEB_DESTINATION);
            if (obj instanceof String) {
                jsonDestinations = new JSONArray();
                jsonDestinations.put(json.getString(SourceHeaderContract.WEB_DESTINATION));
            } else {
                jsonDestinations = json.getJSONArray(SourceHeaderContract.WEB_DESTINATION);
            }
            if (jsonDestinations.length() > MAX_DISTINCT_WEB_DESTINATIONS_IN_SOURCE_REGISTRATION) {
                LogUtil.d("Source registration exceeded the number of allowed destinations.");
                return false;
            }
            for (int i = 0; i < jsonDestinations.length(); i++) {
                Uri destination = Uri.parse(jsonDestinations.getString(i));
                if (shouldMatchAtLeastOneWebDestination
                        && asyncRegistration.getWebDestination().equals(destination)) {
                    matchedOneWebDestination = true;
                }
                Optional<Uri> topPrivateDomainAndScheme =
                        WebAddresses.topPrivateDomainAndScheme(destination);
                if (topPrivateDomainAndScheme.isEmpty()) {
                    LogUtil.d("Unable to extract top private domain and scheme from web "
                            + "destination.");
                    return false;
                } else {
                    destinationSet.add(topPrivateDomainAndScheme.get());
                }
            }
            List<Uri> destinationList = new ArrayList<>(destinationSet);
            builder.setWebDestinations(destinationList);
        }

        if (mFlags.getMeasurementEnableCoarseEventReportDestinations()
                && !json.isNull(SourceHeaderContract.COARSE_EVENT_REPORT_DESTINATIONS)) {
            builder.setCoarseEventReportDestinations(
                    json.getBoolean(SourceHeaderContract.COARSE_EVENT_REPORT_DESTINATIONS));
        }

        if (shouldMatchAtLeastOneWebDestination && !matchedOneWebDestination) {
            LogUtil.d("Expected at least one web_destination to match with the supplied one!");
            return false;
        }

        String eventReportWindows = null;
        if (mFlags.getMeasurementFlexLiteAPIEnabled()
                || mFlags.getMeasurementFlexibleEventReportingApiEnabled()) {
            if (!json.isNull(SourceHeaderContract.MAX_EVENT_LEVEL_REPORTS)) {
                int maxEventLevelReports =
                        json.getInt(SourceHeaderContract.MAX_EVENT_LEVEL_REPORTS);
                if (maxEventLevelReports > mFlags.getMeasurementFlexApiMaxEventReports()) {
                    return false;
                }
                builder.setMaxEventLevelReports(maxEventLevelReports);
            }

            if (!json.isNull(SourceHeaderContract.EVENT_REPORT_WINDOWS)) {
                if (!json.isNull(SourceHeaderContract.EVENT_REPORT_WINDOW)) {
                    LogUtil.d(
                            "Only one of event_report_window and event_report_windows is expected");
                    return false;
                }
                eventReportWindows = json.getString(SourceHeaderContract.EVENT_REPORT_WINDOWS);
                if (!isEventReportWindowsValid(new JSONObject(eventReportWindows), expiry)) {
                    LogUtil.d("Invalid value for event_report_windows");
                    return false;
                }
                eventReportWindows = updateEventReportWindowsToMillis(eventReportWindows);
                builder.setEventReportWindows(eventReportWindows);
            }
        }

        if (mFlags.getMeasurementFlexibleEventReportingApiEnabled()
                && !json.isNull(SourceHeaderContract.TRIGGER_SPECS)) {
            String triggerSpecString = json.getString(SourceHeaderContract.TRIGGER_SPECS);

            if (!isTriggerSpecArrayValid(triggerSpecString, expiry)) {
                LogUtil.d("Invalid Trigger Spec format");
                return false;
            }
            String updatedTriggerSpec =
                    populateTriggerSpecDefaults(
                            triggerSpecString,
                            eventReportWindows,
                            expiry,
                            asyncRegistration.getSourceType());

            builder.setTriggerSpecs(updatedTriggerSpec);
            builder.buildInitialFlexEventReportSpec(mFlags);
        }

        if (mFlags.getMeasurementEnableSharedSourceDebugKey()
                && !json.isNull(SourceHeaderContract.SHARED_DEBUG_KEY)) {
            try {
                builder.setSharedDebugKey(
                        new UnsignedLong(json.getString(SourceHeaderContract.SHARED_DEBUG_KEY)));
            } catch (NumberFormatException e) {
                LogUtil.e(e, "parseCommonSourceParams: parsing shared debug key failed");
            }
        }
        return true;
    }

    private String updateEventReportWindowsToMillis(String eventReportWindow) throws JSONException {
        List<Pair<Long, Long>> eventReportWindows =
                Source.parseEventReportWindows(eventReportWindow);
        if (eventReportWindows == null) {
            return null;
        }
        List<Pair<Long, Long>> converted = new ArrayList<>();
        for (Pair<Long, Long> window : eventReportWindows) {
            converted.add(
                    new Pair<>(
                            TimeUnit.SECONDS.toMillis(window.first),
                            TimeUnit.SECONDS.toMillis(window.second)));
        }
        JSONObject result = new JSONObject();
        result.put("start_time", converted.get(0).first);
        JSONArray endTimes = new JSONArray();
        for (Pair<Long, Long> window : converted) {
            endTimes.put(window.second);
        }
        result.put("end_times", endTimes);
        return result.toString();
    }

    private String populateTriggerSpecDefaults(
            String triggerSpecString,
            String eventReportWindows,
            long expiry,
            Source.SourceType sourceType)
            throws JSONException {
        List<Pair<Long, Long>> parsedEventReportWindows =
                getOrDefaultEventReportWindows(eventReportWindows, sourceType, expiry, mFlags);
        long defaultStart = parsedEventReportWindows.get(0).first;
        List<Long> defaultEnds =
                parsedEventReportWindows.stream().map((x) -> x.second).collect(Collectors.toList());
        JSONArray triggerSpecJson = new JSONArray(triggerSpecString);
        TriggerSpec[] triggerSpecs = new TriggerSpec[triggerSpecJson.length()];
        for (int i = 0; i < triggerSpecJson.length(); i++) {
            triggerSpecs[i] =
                    new TriggerSpec.Builder(
                                    triggerSpecJson.getJSONObject(i), defaultStart, defaultEnds)
                            .build();
        }
        return encodeTriggerSpecsToJson(triggerSpecs);
    }

    private boolean isTriggerSpecArrayValid(String triggerSpecString, long expiry) {
        try {
            JSONArray triggerSpecArray = new JSONArray(triggerSpecString);
            Set<UnsignedLong> triggerDataSet = new HashSet<>();
            for (int i = 0; i < triggerSpecArray.length(); i++) {
                if (!isTriggerSpecValid(
                        triggerSpecArray.getJSONObject(i), expiry, triggerDataSet)) {
                    return false;
                }
            }
            // Check cardinality of trigger_data across the whole trigger spec array
            if (triggerDataSet.size() > mFlags.getMeasurementFlexApiMaxTriggerDataCardinality()) {
                return false;
            }
        } catch (JSONException | IllegalArgumentException ex) {
            LogUtil.d(ex, "Trigger Spec parsing failed");
            return false;
        }
        return true;
    }

    private boolean isTriggerSpecValid(
            JSONObject triggerSpec, long expiry, Set<UnsignedLong> triggerDataSet)
            throws JSONException {
        List<UnsignedLong> triggerDataList =
                getTriggerDataArrayFromJSON(
                        triggerSpec, ReportSpecUtil.FlexEventReportJsonKeys.TRIGGER_DATA);
        if (triggerDataList.isEmpty()
                || triggerDataList.size()
                        > mFlags.getMeasurementFlexApiMaxTriggerDataCardinality()) {
            return false;
        }
        // Check exclusivity of trigger_data across the whole trigger spec array
        for (UnsignedLong triggerData : triggerDataList) {
            if (!triggerDataSet.add(triggerData)) {
                return false;
            }
        }

        if (!triggerSpec.isNull(ReportSpecUtil.FlexEventReportJsonKeys.EVENT_REPORT_WINDOWS)
                && !isEventReportWindowsValid(
                        triggerSpec.getJSONObject(
                                ReportSpecUtil.FlexEventReportJsonKeys.EVENT_REPORT_WINDOWS),
                        expiry)) {
            return false;
        }

        TriggerSpec.SummaryOperatorType summaryWindowOperator =
                TriggerSpec.SummaryOperatorType.COUNT;
        if (!triggerSpec.isNull(ReportSpecUtil.FlexEventReportJsonKeys.SUMMARY_WINDOW_OPERATOR)) {
            try {
                summaryWindowOperator =
                        TriggerSpec.SummaryOperatorType.valueOf(
                                triggerSpec
                                        .getString(
                                                ReportSpecUtil.FlexEventReportJsonKeys
                                                        .SUMMARY_WINDOW_OPERATOR)
                                        .toUpperCase());
            } catch (IllegalArgumentException e) {
                // If a summary window operator is not in the pre-defined list, it will throw to
                // exception.
                LogUtil.d(e, "Summary Operator parsing failed");
                return false;
            }
        }
        List<Long> summaryBuckets = null;
        if (!triggerSpec.isNull(ReportSpecUtil.FlexEventReportJsonKeys.SUMMARY_BUCKETS)) {
            summaryBuckets =
                    getLongArrayFromJSON(
                            triggerSpec, ReportSpecUtil.FlexEventReportJsonKeys.SUMMARY_BUCKETS);
        }
        if ((summaryBuckets == null || summaryBuckets.isEmpty())
                && summaryWindowOperator != TriggerSpec.SummaryOperatorType.COUNT) {
            return false;
        }

        if (summaryBuckets != null && !TriggerSpec.isStrictIncreasing(summaryBuckets)) {
            return false;
        }

        return true;
    }

    private boolean isEventReportWindowsValid(JSONObject jsonReportWindows, long expiry)
            throws JSONException {
        long startTime = 0;
        if (!jsonReportWindows.isNull(ReportSpecUtil.FlexEventReportJsonKeys.START_TIME)) {
            startTime =
                    jsonReportWindows.getLong(ReportSpecUtil.FlexEventReportJsonKeys.START_TIME);
        }
        if (startTime < 0 || startTime > expiry) {
            return false;
        }
        List<Long> windowsEnd =
                getLongArrayFromJSON(
                        jsonReportWindows, ReportSpecUtil.FlexEventReportJsonKeys.END_TIMES);
        if (windowsEnd.isEmpty()
                || windowsEnd.size() > mFlags.getMeasurementFlexApiMaxEventReportWindows()) {
            return false;
        }

        if (startTime > windowsEnd.get(0)
                || windowsEnd.get(0) < mFlags.getMeasurementMinimumEventReportWindowInSeconds()
                || windowsEnd.get(windowsEnd.size() - 1) > expiry) {
            return false;
        }

        if (!TriggerSpec.isStrictIncreasing(windowsEnd)) {
            return false;
        }
        return true;
    }

    /** Parse a {@code Source}, given response headers, adding the {@code Source} to a given list */
    @VisibleForTesting
    public Optional<Source> parseSource(
            AsyncRegistration asyncRegistration,
            String enrollmentId,
            Map<String, List<String>> headers,
            AsyncFetchStatus asyncFetchStatus) {
        boolean arDebugPermission = asyncRegistration.getDebugKeyAllowed();
        LogUtil.d("Source ArDebug permission enabled %b", arDebugPermission);
        Source.Builder builder = new Source.Builder();
        builder.setRegistrationId(asyncRegistration.getRegistrationId());
        builder.setPublisher(getBaseUri(asyncRegistration.getTopOrigin()));
        builder.setEnrollmentId(enrollmentId);
        builder.setRegistrant(asyncRegistration.getRegistrant());
        builder.setSourceType(asyncRegistration.getSourceType());
        builder.setAttributionMode(Source.AttributionMode.TRUTHFULLY);
        builder.setEventTime(asyncRegistration.getRequestTime());
        builder.setAdIdPermission(asyncRegistration.hasAdIdPermission());
        builder.setArDebugPermission(arDebugPermission);
        builder.setPublisherType(
                asyncRegistration.isWebRequest() ? EventSurfaceType.WEB : EventSurfaceType.APP);
        Optional<Uri> registrationUriOrigin =
                WebAddresses.originAndScheme(asyncRegistration.getRegistrationUri());
        if (!registrationUriOrigin.isPresent()) {
            LogUtil.d(
                    "AsyncSourceFetcher: "
                            + "Invalid or empty registration uri - "
                            + asyncRegistration.getRegistrationUri());
            return Optional.empty();
        }
        builder.setRegistrationOrigin(registrationUriOrigin.get());

        builder.setPlatformAdId(asyncRegistration.getPlatformAdId());

        List<String> field =
                headers.get(SourceHeaderContract.HEADER_ATTRIBUTION_REPORTING_REGISTER_SOURCE);
        if (field == null || field.size() != 1) {
            LogUtil.d(
                    "AsyncSourceFetcher: "
                            + "Invalid Attribution-Reporting-Register-Source header.");
            asyncFetchStatus.setEntityStatus(AsyncFetchStatus.EntityStatus.HEADER_ERROR);
            return Optional.empty();
        }
        try {
            JSONObject json = new JSONObject(field.get(0));
            boolean isValid =
                    parseCommonSourceParams(json, asyncRegistration, builder, enrollmentId);
            if (!isValid) {
                asyncFetchStatus.setEntityStatus(AsyncFetchStatus.EntityStatus.VALIDATION_ERROR);
                return Optional.empty();
            }
            if (!json.isNull(SourceHeaderContract.AGGREGATION_KEYS)) {
                if (!areValidAggregationKeys(
                        json.getJSONObject(SourceHeaderContract.AGGREGATION_KEYS))) {
                    asyncFetchStatus.setEntityStatus(
                            AsyncFetchStatus.EntityStatus.VALIDATION_ERROR);
                    return Optional.empty();
                }
                builder.setAggregateSource(json.getString(SourceHeaderContract.AGGREGATION_KEYS));
            }
            if (mFlags.getMeasurementEnableXNA()
                    && !json.isNull(SourceHeaderContract.SHARED_AGGREGATION_KEYS)) {
                // Parsed as JSONArray for validation
                JSONArray sharedAggregationKeys =
                        json.getJSONArray(SourceHeaderContract.SHARED_AGGREGATION_KEYS);
                builder.setSharedAggregationKeys(sharedAggregationKeys.toString());
            }
            if (mFlags.getMeasurementEnableSharedFilterDataKeysXNA()
                    && !json.isNull(SourceHeaderContract.SHARED_FILTER_DATA_KEYS)) {
                // Parsed as JSONArray for validation
                JSONArray sharedFilterDataKeys =
                        json.getJSONArray(SourceHeaderContract.SHARED_FILTER_DATA_KEYS);
                builder.setSharedFilterDataKeys(sharedFilterDataKeys.toString());
            }
            asyncFetchStatus.setEntityStatus(AsyncFetchStatus.EntityStatus.SUCCESS);
            return Optional.of(builder.build());
        } catch (JSONException e) {
            LogUtil.d(e, "AsyncSourceFetcher: invalid JSON");
            asyncFetchStatus.setEntityStatus(AsyncFetchStatus.EntityStatus.PARSING_ERROR);
            return Optional.empty();
        } catch (IllegalArgumentException e) {
            LogUtil.d(e, "AsyncSourceFetcher: illegal argument");
            asyncFetchStatus.setEntityStatus(AsyncFetchStatus.EntityStatus.VALIDATION_ERROR);
            return Optional.empty();
        }
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
            AsyncRegistration asyncRegistration,
            AsyncFetchStatus asyncFetchStatus,
            AsyncRedirect asyncRedirect) {
        HttpURLConnection urlConnection = null;
        Map<String, List<String>> headers;
        // TODO(b/276825561): Fix code duplication between fetchSource & fetchTrigger request flow
        try {
            urlConnection =
                    (HttpURLConnection)
                            openUrl(new URL(asyncRegistration.getRegistrationUri().toString()));
            urlConnection.setRequestMethod("POST");
            urlConnection.setRequestProperty(
                    SourceRequestContract.SOURCE_INFO,
                    asyncRegistration.getSourceType().toString());
            urlConnection.setInstanceFollowRedirects(false);
            headers = urlConnection.getHeaderFields();
            asyncFetchStatus.setResponseSize(FetcherUtil.calculateHeadersCharactersLength(headers));
            int responseCode = urlConnection.getResponseCode();
            LogUtil.d("Response code = " + responseCode);
            if (!FetcherUtil.isRedirect(responseCode) && !FetcherUtil.isSuccess(responseCode)) {
                asyncFetchStatus.setResponseStatus(
                        AsyncFetchStatus.ResponseStatus.SERVER_UNAVAILABLE);
                return Optional.empty();
            }
            asyncFetchStatus.setResponseStatus(AsyncFetchStatus.ResponseStatus.SUCCESS);
        } catch (MalformedURLException e) {
            LogUtil.d(e, "Malformed registration target URL");
            asyncFetchStatus.setResponseStatus(AsyncFetchStatus.ResponseStatus.INVALID_URL);
            return Optional.empty();
        } catch (IOException e) {
            LogUtil.e(e, "Failed to get registration response");
            asyncFetchStatus.setResponseStatus(AsyncFetchStatus.ResponseStatus.NETWORK_ERROR);
            return Optional.empty();
        } finally {
            if (urlConnection != null) {
                urlConnection.disconnect();
            }
        }

        if (asyncRegistration.shouldProcessRedirects()) {
            FetcherUtil.parseRedirects(headers).forEach(asyncRedirect::addToRedirects);
        }

        if (!isSourceHeaderPresent(headers)) {
            asyncFetchStatus.setEntityStatus(AsyncFetchStatus.EntityStatus.HEADER_MISSING);
            return Optional.empty();
        }

        Optional<String> enrollmentId =
                mFlags.isDisableMeasurementEnrollmentCheck()
                        ? Optional.of(Enrollment.FAKE_ENROLLMENT)
                        : Enrollment.getValidEnrollmentId(
                                asyncRegistration.getRegistrationUri(),
                                asyncRegistration.getRegistrant().getAuthority(),
                                mEnrollmentDao,
                                mContext,
                                mFlags);
        if (enrollmentId.isEmpty()) {
            LogUtil.d(
                    "fetchSource: Valid enrollment id not found. Registration URI: %s",
                    asyncRegistration.getRegistrationUri());
            asyncFetchStatus.setEntityStatus(AsyncFetchStatus.EntityStatus.INVALID_ENROLLMENT);
            return Optional.empty();
        }

        Optional<Source> parsedSource =
                parseSource(asyncRegistration, enrollmentId.get(), headers, asyncFetchStatus);
        return parsedSource;
    }

    private boolean isSourceHeaderPresent(Map<String, List<String>> headers) {
        return headers.containsKey(
                SourceHeaderContract.HEADER_ATTRIBUTION_REPORTING_REGISTER_SOURCE);
    }

    private boolean areValidAggregationKeys(JSONObject aggregationKeys) {
        if (aggregationKeys.length()
                > mFlags.getMeasurementMaxAggregateKeysPerSourceRegistration()) {
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
            if (!FetcherUtil.isValidAggregateKeyPiece(keyPiece, mFlags)) {
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
        String HEADER_ATTRIBUTION_REPORTING_REGISTER_SOURCE =
                "Attribution-Reporting-Register-Source";
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
        String DEBUG_JOIN_KEY = "debug_join_key";
        String DEBUG_AD_ID = "debug_ad_id";
        String COARSE_EVENT_REPORT_DESTINATIONS = "coarse_event_report_destinations";
        String TRIGGER_SPECS = "trigger_specs";
        String MAX_EVENT_LEVEL_REPORTS = "max_event_level_reports";
        String EVENT_REPORT_WINDOWS = "event_report_windows";
        String SHARED_DEBUG_KEY = "shared_debug_key";
        String SHARED_FILTER_DATA_KEYS = "shared_filter_data_keys";
    }

    private interface SourceRequestContract {
        String SOURCE_INFO = "Attribution-Reporting-Source-Info";
    }
}

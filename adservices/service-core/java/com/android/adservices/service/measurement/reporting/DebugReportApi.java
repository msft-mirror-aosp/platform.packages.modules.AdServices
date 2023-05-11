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

package com.android.adservices.service.measurement.reporting;

import android.annotation.NonNull;
import android.content.Context;
import android.util.Pair;

import androidx.annotation.Nullable;

import com.android.adservices.LogUtil;
import com.android.adservices.data.measurement.DatastoreException;
import com.android.adservices.data.measurement.IMeasurementDao;
import com.android.adservices.service.Flags;
import com.android.adservices.service.measurement.EventSurfaceType;
import com.android.adservices.service.measurement.Source;
import com.android.adservices.service.measurement.Trigger;
import com.android.adservices.service.measurement.util.BaseUriExtractor;
import com.android.adservices.service.measurement.util.UnsignedLong;

import org.json.JSONException;
import org.json.JSONObject;

import java.util.Objects;
import java.util.UUID;

/** Class used to send debug reports to Ad-Tech {@link DebugReport} */
public class DebugReportApi {

    /** Define different verbose debug report types. */
    public interface Type {
        String SOURCE_DESTINATION_LIMIT = "source-destination-limit";
        String SOURCE_NOISED = "source-noised";
        String SOURCE_STORAGE_LIMIT = "source-storage-limit";
        String SOURCE_SUCCESS = "source-success";
        String SOURCE_UNKNOWN_ERROR = "source-unknown-error";
        String TRIGGER_AGGREGATE_DEDUPLICATED = "trigger-aggregate-deduplicated";
        String TRIGGER_AGGREGATE_INSUFFICIENT_BUDGET = "trigger-aggregate-insufficient-budget";
        String TRIGGER_AGGREGATE_REPORT_WINDOW_PASSED = "trigger-aggregate-report-window-passed";
        String TRIGGER_AGGREGATE_NO_CONTRIBUTIONS = "trigger-aggregate-no-contributions";
        String TRIGGER_ATTRIBUTIONS_PER_SOURCE_DESTINATION_LIMIT =
                "trigger-attributions-per-source-destination-limit";
        String TRIGGER_EVENT_DEDUPLICATED = "trigger-event-deduplicated";
        String TRIGGER_EVENT_EXCESSIVE_REPORTS = "trigger-event-excessive-reports";
        String TRIGGER_EVENT_LOW_PRIORITY = "trigger-event-low-priority";
        String TRIGGER_EVENT_NO_MATCHING_CONFIGURATIONS =
                "trigger-event-no-matching-configurations";
        String TRIGGER_EVENT_NOISE = "trigger-event-noise";
        String TRIGGER_EVENT_REPORT_WINDOW_PASSED = "trigger-event-report-window-passed";
        String TRIGGER_NO_MATCHING_FILTER_DATA = "trigger-no-matching-filter-data";
        String TRIGGER_NO_MATCHING_SOURCE = "trigger-no-matching-source";
        String TRIGGER_REPORTING_ORIGIN_LIMIT = "trigger-reporting-origin-limit";
        String TRIGGER_EVENT_STORAGE_LIMIT = "trigger-event-storage-limit";
        String TRIGGER_UNKNOWN_ERROR = "trigger-unknown-error";
    }

    private interface Body {
        String ATTRIBUTION_DESTINATION = "attribution_destination";
        String LIMIT = "limit";
        String RANDOMIZED_TRIGGER_RATE = "randomized_trigger_rate";
        String SCHEDULED_REPORT_TIME = "scheduled_report_time";
        String SOURCE_DEBUG_KEY = "source_debug_key";
        String SOURCE_EVENT_ID = "source_event_id";
        String SOURCE_SITE = "source_site";
        String SOURCE_TYPE = "source_type";
        String TRIGGER_DEBUG_KEY = "trigger_debug_key";
    }

    private enum PermissionState {
        GRANTED,
        DENIED,
        NONE
    }

    private final Context mContext;
    private final Flags mFlags;

    public DebugReportApi(Context context, Flags flags) {
        mContext = context;
        mFlags = flags;
    }

    /** Schedules the Source Success Debug Report */
    public void scheduleSourceSuccessDebugReport(Source source, IMeasurementDao dao) {
        if (isSourceDebugFlagDisabled(Type.SOURCE_SUCCESS)) {
            return;
        }
        if (isAdTechNotOptIn(source.isDebugReporting(), Type.SOURCE_SUCCESS)) {
            return;
        }
        if (getAdIdPermissionFromSource(source) == PermissionState.DENIED
                || getArDebugPermissionFromSource(source) == PermissionState.DENIED) {
            LogUtil.d("Skipping debug report %s", Type.SOURCE_SUCCESS);
            return;
        }
        scheduleReport(
                Type.SOURCE_SUCCESS,
                generateSourceDebugReportBody(source, null),
                source.getEnrollmentId(),
                dao);
    }

    /** Schedules the Source Destination limit Debug Report */
    public void scheduleSourceDestinationLimitDebugReport(
            Source source, String limit, IMeasurementDao dao) {
        if (isSourceDebugFlagDisabled(Type.SOURCE_DESTINATION_LIMIT)) {
            return;
        }
        if (isAdTechNotOptIn(source.isDebugReporting(), Type.SOURCE_DESTINATION_LIMIT)) {
            return;
        }
        try {
            JSONObject body = new JSONObject();
            body.put(Body.SOURCE_EVENT_ID, source.getEventId().toString());
            body.put(Body.ATTRIBUTION_DESTINATION, serializeSourceDestinations(source));
            body.put(
                    Body.SOURCE_SITE,
                    BaseUriExtractor.getBaseUri(source.getPublisher()).toString());
            body.put(Body.LIMIT, limit);
            if (getAdIdPermissionFromSource(source) == PermissionState.GRANTED
                    || getArDebugPermissionFromSource(source) == PermissionState.GRANTED) {
                body.put(Body.SOURCE_DEBUG_KEY, source.getDebugKey());
            }
            scheduleReport(Type.SOURCE_DESTINATION_LIMIT, body, source.getEnrollmentId(), dao);
        } catch (JSONException e) {
            LogUtil.e(e, "Json error in debug report %s", Type.SOURCE_DESTINATION_LIMIT);
        }
    }

    /** Schedules the Source Noised Debug Report */
    public void scheduleSourceNoisedDebugReport(Source source, IMeasurementDao dao) {
        if (isSourceDebugFlagDisabled(Type.SOURCE_NOISED)) {
            return;
        }
        if (isAdTechNotOptIn(source.isDebugReporting(), Type.SOURCE_NOISED)) {
            return;
        }
        if (getAdIdPermissionFromSource(source) == PermissionState.DENIED
                || getArDebugPermissionFromSource(source) == PermissionState.DENIED) {
            LogUtil.d("Skipping debug report %s", Type.SOURCE_NOISED);
            return;
        }
        scheduleReport(
                Type.SOURCE_NOISED,
                generateSourceDebugReportBody(source, null),
                source.getEnrollmentId(),
                dao);
    }

    /** Schedules Source Storage Limit Debug Report */
    public void scheduleSourceStorageLimitDebugReport(
            Source source, String limit, IMeasurementDao dao) {
        if (isSourceDebugFlagDisabled(Type.SOURCE_STORAGE_LIMIT)) {
            return;
        }
        if (isAdTechNotOptIn(source.isDebugReporting(), Type.SOURCE_STORAGE_LIMIT)) {
            return;
        }
        if (getAdIdPermissionFromSource(source) == PermissionState.DENIED
                || getArDebugPermissionFromSource(source) == PermissionState.DENIED) {
            LogUtil.d("Skipping debug report %s", Type.SOURCE_STORAGE_LIMIT);
            return;
        }
        scheduleReport(
                Type.SOURCE_STORAGE_LIMIT,
                generateSourceDebugReportBody(source, limit),
                source.getEnrollmentId(),
                dao);
    }

    /** Schedules the Source Unknown Error Debug Report */
    public void scheduleSourceUnknownErrorDebugReport(Source source, IMeasurementDao dao) {
        if (isSourceDebugFlagDisabled(Type.SOURCE_UNKNOWN_ERROR)) {
            return;
        }
        if (isAdTechNotOptIn(source.isDebugReporting(), Type.SOURCE_UNKNOWN_ERROR)) {
            return;
        }
        if (getAdIdPermissionFromSource(source) == PermissionState.DENIED
                || getArDebugPermissionFromSource(source) == PermissionState.DENIED) {
            LogUtil.d("Skipping debug report %s", Type.SOURCE_UNKNOWN_ERROR);
            return;
        }
        scheduleReport(
                Type.SOURCE_UNKNOWN_ERROR,
                generateSourceDebugReportBody(source, null),
                source.getEnrollmentId(),
                dao);
    }

    /**
     * Schedules trigger-no-matching-source and trigger-unknown-error debug reports when trigger
     * doesn't have related source.
     */
    public void scheduleTriggerNoMatchingSourceDebugReport(
            Trigger trigger, IMeasurementDao dao, String type) {
        if (isTriggerDebugFlagDisabled(type)) {
            return;
        }
        if (isAdTechNotOptIn(trigger.isDebugReporting(), type)) {
            return;
        }
        Pair<UnsignedLong, UnsignedLong> debugKeyPair =
                new DebugKeyAccessor().getDebugKeysForVerboseTriggerDebugReport(null, trigger);
        scheduleReport(
                type,
                generateTriggerDebugReportBody(null, trigger, null, debugKeyPair, true),
                trigger.getEnrollmentId(),
                dao);
    }

    /** Schedules Trigger Debug Reports with/without limit, pass in Type for different types. */
    public void scheduleTriggerDebugReport(
            Source source,
            Trigger trigger,
            @Nullable String limit,
            IMeasurementDao dao,
            String type) {
        if (isTriggerDebugFlagDisabled(type)) {
            return;
        }
        if (isAdTechNotOptIn(source.isDebugReporting(), type)
                || isAdTechNotOptIn(trigger.isDebugReporting(), type)) {
            return;
        }
        Pair<UnsignedLong, UnsignedLong> debugKeyPair =
                new DebugKeyAccessor().getDebugKeysForVerboseTriggerDebugReport(source, trigger);
        scheduleReport(
                type,
                generateTriggerDebugReportBody(source, trigger, limit, debugKeyPair, false),
                source.getEnrollmentId(),
                dao);
    }

    /**
     * Schedules Trigger Debug Report with all body fields, Used for trigger-low-priority report and
     * trigger-event-excessive-reports.
     */
    public void scheduleTriggerDebugReportWithAllFields(
            Source source, Trigger trigger, IMeasurementDao dao, String type) {
        if (isTriggerDebugFlagDisabled(type)) {
            return;
        }
        if (isAdTechNotOptIn(source.isDebugReporting(), type)
                || isAdTechNotOptIn(trigger.isDebugReporting(), type)) {
            return;
        }
        Pair<UnsignedLong, UnsignedLong> debugKeyPair =
                new DebugKeyAccessor().getDebugKeysForVerboseTriggerDebugReport(source, trigger);
        scheduleReport(
                type,
                generateTriggerDebugReportBodyWithAllFields(source, trigger, debugKeyPair),
                source.getEnrollmentId(),
                dao);
    }

    /**
     * Schedules the Debug Report to be sent
     *
     * @param type The type of the debug report
     * @param body The body of the debug report
     * @param enrollmentId Ad Tech enrollment ID
     * @param dao Measurement DAO
     */
    private void scheduleReport(
            @NonNull String type,
            @NonNull JSONObject body,
            @NonNull String enrollmentId,
            @NonNull IMeasurementDao dao) {
        Objects.requireNonNull(type);
        Objects.requireNonNull(body);
        Objects.requireNonNull(enrollmentId);
        Objects.requireNonNull(dao);
        if (type.isEmpty() || body.length() == 0) {
            LogUtil.d("Empty debug report found %s", type);
            return;
        }
        if (enrollmentId.isEmpty()) {
            LogUtil.d("Empty enrollment found %s", type);
            return;
        }
        DebugReport debugReport =
                new DebugReport.Builder()
                        .setId(UUID.randomUUID().toString())
                        .setType(type)
                        .setBody(body)
                        .setEnrollmentId(enrollmentId)
                        .build();
        try {
            dao.insertDebugReport(debugReport);
        } catch (DatastoreException e) {
            LogUtil.e(e, "Failed to insert debug report %s", type);
        }

        DebugReportingJobService.scheduleIfNeeded(
                mContext, /*forceSchedule=*/ true, /*isDebugReportApi=*/ true);
    }

    /** Get AdIdPermission State from Source */
    private PermissionState getAdIdPermissionFromSource(Source source) {
        if (source.getPublisherType() == EventSurfaceType.APP) {
            if (source.hasAdIdPermission()) {
                return PermissionState.GRANTED;
            } else {
                LogUtil.d("Source doesn't have AdId permission");
                return PermissionState.DENIED;
            }
        }
        return PermissionState.NONE;
    }

    /** Get ArDebugPermission State from Source */
    private PermissionState getArDebugPermissionFromSource(Source source) {
        if (source.getPublisherType() == EventSurfaceType.WEB) {
            if (source.hasArDebugPermission()) {
                return PermissionState.GRANTED;
            } else {
                LogUtil.d("Source doesn't have ArDebug permission");
                return PermissionState.DENIED;
            }
        }
        return PermissionState.NONE;
    }

    /** Get is Ad tech not op-in and log */
    private boolean isAdTechNotOptIn(boolean optIn, String type) {
        if (!optIn) {
            LogUtil.d("Ad-tech not opt-in. Skipping debug report %s", type);
        }
        return !optIn;
    }

    /** Generates source debug report body */
    private JSONObject generateSourceDebugReportBody(
            @NonNull Source source, @Nullable String limit) {
        JSONObject body = new JSONObject();
        try {
            body.put(Body.SOURCE_EVENT_ID, source.getEventId().toString());
            body.put(Body.ATTRIBUTION_DESTINATION, serializeSourceDestinations(source));
            body.put(
                    Body.SOURCE_SITE,
                    BaseUriExtractor.getBaseUri(source.getPublisher()).toString());
            body.put(Body.LIMIT, limit);
            body.put(Body.SOURCE_DEBUG_KEY, source.getDebugKey());
        } catch (JSONException e) {
            LogUtil.e(e, "Json error while generating source debug report body.");
        }
        return body;
    }

    private static Object serializeSourceDestinations(Source source) throws JSONException {
        return source.getPublisherType() == EventSurfaceType.APP
                ? ReportUtil.serializeAttributionDestinations(source.getAppDestinations())
                : ReportUtil.serializeAttributionDestinations(source.getWebDestinations());
    }

    /** Generates trigger debug report body */
    private JSONObject generateTriggerDebugReportBody(
            @Nullable Source source,
            @NonNull Trigger trigger,
            @Nullable String limit,
            @NonNull Pair<UnsignedLong, UnsignedLong> debugKeyPair,
            boolean isTriggerNoMatchingSource) {
        JSONObject body = new JSONObject();
        try {
            body.put(Body.ATTRIBUTION_DESTINATION, trigger.getAttributionDestination());
            body.put(Body.TRIGGER_DEBUG_KEY, debugKeyPair.second);
            if (isTriggerNoMatchingSource) {
                return body;
            }
            body.put(Body.LIMIT, limit);
            body.put(Body.SOURCE_DEBUG_KEY, debugKeyPair.first);
            body.put(Body.SOURCE_EVENT_ID, source.getEventId().toString());
            body.put(
                    Body.SOURCE_SITE,
                    BaseUriExtractor.getBaseUri(source.getPublisher()).toString());
        } catch (JSONException e) {
            LogUtil.e(e, "Json error while generating trigger debug report body.");
        }
        return body;
    }

    /**
     * Generates trigger debug report body with all fields in event-level attribution report. Used
     * for trigger-low-priority, trigger-event-excessive-reports debug reports.
     */
    private JSONObject generateTriggerDebugReportBodyWithAllFields(
            @NonNull Source source,
            @NonNull Trigger trigger,
            @NonNull Pair<UnsignedLong, UnsignedLong> debugKeyPair) {
        JSONObject body = new JSONObject();
        try {
            body.put(
                    Body.ATTRIBUTION_DESTINATION,
                    ReportUtil.serializeAttributionDestinations(
                            source.getAttributionDestinations(trigger.getDestinationType())));
            body.put(
                    Body.SCHEDULED_REPORT_TIME,
                    String.valueOf(
                            source.getReportingTime(
                                    trigger.getTriggerTime(), trigger.getDestinationType())));
            body.put(Body.SOURCE_EVENT_ID, source.getEventId());
            body.put(Body.SOURCE_TYPE, source.getSourceType().getValue());
            body.put(Body.RANDOMIZED_TRIGGER_RATE, source.getRandomAttributionProbability());
            if (debugKeyPair.first != null) {
                body.put(Body.SOURCE_DEBUG_KEY, debugKeyPair.first);
            }
            if (debugKeyPair.second != null) {
                body.put(Body.TRIGGER_DEBUG_KEY, debugKeyPair.second);
            }
        } catch (JSONException e) {
            LogUtil.e(e, "Json error while generating trigger debug report body with all fields.");
        }
        return body;
    }

    /** Checks flags for source debug reports. */
    private boolean isSourceDebugFlagDisabled(String type) {
        if (!mFlags.getMeasurementEnableDebugReport()
                || !mFlags.getMeasurementEnableSourceDebugReport()) {
            LogUtil.d("Source flag is disabled for %s debug report", type);
            return true;
        }
        return false;
    }

    /** Checks flags for trigger debug reports. */
    private boolean isTriggerDebugFlagDisabled(String type) {
        if (!mFlags.getMeasurementEnableDebugReport()
                || !mFlags.getMeasurementEnableTriggerDebugReport()) {
            LogUtil.d("Trigger flag is disabled for %s debug report", type);
            return true;
        }
        return false;
    }
}

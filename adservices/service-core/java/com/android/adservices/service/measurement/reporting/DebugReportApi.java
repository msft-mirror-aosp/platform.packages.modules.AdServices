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

    private interface Type {
        String SOURCE_DESTINATION_LIMIT = "source-destination-limit";
        String SOURCE_NOISED = "source-noised";
        String SOURCE_STORAGE_LIMIT = "source-storage-limit";
        String SOURCE_SUCCESS = "source-success";
        String SOURCE_UNKNOWN_ERROR = "source-unknown-error";
        String TRIGGER_NO_MATCHING_FILTER_DATA = "trigger-no-matching-filter-data";
    }

    private interface Body {
        String ATTRIBUTION_DESTINATION = "attribution_destination";
        String LIMIT = "limit";
        String SOURCE_DEBUG_KEY = "source_debug_key";
        String SOURCE_EVENT_ID = "source_event_id";
        String SOURCE_SITE = "source_site";
        String TRIGGER_DEBUG_KEY = "trigger_debug_key";
    }

    private enum PermissionState {
        GRANTED,
        DENIED,
        NONE
    }

    private final Context mContext;

    public DebugReportApi(Context context) {
        mContext = context;
    }

    /** Schedules the Source Success Debug Report */
    public void scheduleSourceSuccessDebugReport(Source source, IMeasurementDao dao) {
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
        if (isAdTechNotOptIn(source.isDebugReporting(), Type.SOURCE_DESTINATION_LIMIT)) {
            return;
        }
        try {
            boolean isAppSource = source.getPublisherType() == EventSurfaceType.APP;
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

    /** Schedules Trigger No Matching Filter Data Debug Report */
    public void scheduleTriggerNoMatchingFilterDebugReport(
            Source source, Trigger trigger, IMeasurementDao dao) {
        if (isAdTechNotOptIn(source.isDebugReporting(), Type.TRIGGER_NO_MATCHING_FILTER_DATA)
                || isAdTechNotOptIn(
                        trigger.isDebugReporting(), Type.TRIGGER_NO_MATCHING_FILTER_DATA)) {
            return;
        }
        Pair<UnsignedLong, UnsignedLong> debugKeyPair =
                new DebugKeyAccessor().getDebugKeysForVerboseTriggerDebugReport(source, trigger);
        scheduleReport(
                Type.TRIGGER_NO_MATCHING_FILTER_DATA,
                generateTriggerDebugReportBody(source, trigger, null, debugKeyPair, false),
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

    /* Get AdIdPermission State from Source */
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

    /* Get ArDebugPermission State from Source */
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

    /* Get is Ad tech not op-in and log */
    private boolean isAdTechNotOptIn(boolean optIn, String type) {
        if (!optIn) {
            LogUtil.d("Ad-tech not opt-in. Skipping debug report %s", type);
        }
        return !optIn;
    }

    /*Generates source debug report body */
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
            LogUtil.e(e, "Json error in source debug report");
        }
        return body;
    }

    private static Object serializeSourceDestinations(Source source) throws JSONException {
        return source.getPublisherType() == EventSurfaceType.APP
                ? ReportUtil.serializeAttributionDestinations(source.getAppDestinations())
                : ReportUtil.serializeAttributionDestinations(source.getWebDestinations());
    }

    /*Generates trigger debug report body */
    private JSONObject generateTriggerDebugReportBody(
            @NonNull Source source,
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
            LogUtil.e(e, "Json error in source debug report");
        }
        return body;
    }
}

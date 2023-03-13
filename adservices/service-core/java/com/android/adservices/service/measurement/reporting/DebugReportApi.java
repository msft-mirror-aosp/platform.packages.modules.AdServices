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

import androidx.annotation.Nullable;

import com.android.adservices.LogUtil;
import com.android.adservices.data.measurement.DatastoreException;
import com.android.adservices.data.measurement.IMeasurementDao;
import com.android.adservices.service.measurement.EventSurfaceType;
import com.android.adservices.service.measurement.Source;
import com.android.adservices.service.measurement.util.BaseUriExtractor;

import org.json.JSONException;
import org.json.JSONObject;

import java.util.Objects;
import java.util.UUID;

/** Class used to send debug reports to Ad-Tech {@link DebugReport} */
public class DebugReportApi {

    private interface Type {
        String SOURCE_NOISED = "source-noised";
        String SOURCE_DESTINATION_LIMIT = "source-destination-limit";
        String SOURCE_STORAGE_LIMIT = "source-storage-limit";
    }

    private interface Body {
        String SOURCE_EVENT_ID = "source_event_id";
        String ATTRIBUTION_DESTINATION = "attribution_destination";
        String SOURCE_SITE = "source_site";
        String LIMIT = "limit";
        String SOURCE_DEBUG_KEY = "source_debug_key";
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
            body.put(
                    Body.ATTRIBUTION_DESTINATION,
                    isAppSource
                            ? source.getAppDestinations().get(0).toString()
                            : source.getWebDestinations().get(0).toString());
            body.put(
                    Body.SOURCE_SITE,
                    BaseUriExtractor.getBaseUri(source.getPublisher()).toString());
            body.put(Body.LIMIT, limit);
            if (getAdIdPermissionState(source) == PermissionState.GRANTED
                    || getArDebugPermissionState(source) == PermissionState.GRANTED) {
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
        if (getAdIdPermissionState(source) == PermissionState.DENIED
                || getArDebugPermissionState(source) == PermissionState.DENIED) {
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
        if (getAdIdPermissionState(source) == PermissionState.DENIED
                || getArDebugPermissionState(source) == PermissionState.DENIED) {
            LogUtil.d("Skipping debug report %s", Type.SOURCE_STORAGE_LIMIT);
            return;
        }
        scheduleReport(
                Type.SOURCE_STORAGE_LIMIT,
                generateSourceDebugReportBody(source, limit),
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

    /* Get AdIdPermission State */
    private PermissionState getAdIdPermissionState(Source source) {
        if (source.getPublisherType() == EventSurfaceType.APP) {
            if (source.hasAdIdPermission()) {
                return PermissionState.GRANTED;
            } else {
                LogUtil.d("Missing AdId permission");
                return PermissionState.DENIED;
            }
        }
        return PermissionState.NONE;
    }

    /* Get ArDebugPermission State */
    private PermissionState getArDebugPermissionState(Source source) {
        if (source.getPublisherType() == EventSurfaceType.WEB) {
            if (source.hasArDebugPermission()) {
                return PermissionState.GRANTED;
            } else {
                LogUtil.d("Missing ArDebug permission");
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

    /*Generates source debug report body*/
    private JSONObject generateSourceDebugReportBody(
            @NonNull Source source, @Nullable String limit) {
        JSONObject body = new JSONObject();
        try {
            body.put(Body.SOURCE_EVENT_ID, source.getEventId().toString());
            body.put(
                    Body.ATTRIBUTION_DESTINATION,
                    source.getPublisherType() == EventSurfaceType.APP
                            ? source.getAppDestinations().get(0).toString()
                            : source.getWebDestinations().get(0).toString());
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
}

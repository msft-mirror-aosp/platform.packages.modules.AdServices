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

    private static final String TYPE_SOURCE_NOISED = "source-noised";
    private static final String TYPE_SOURCE_DESTINATION_LIMIT = "source-destination-limit";

    private static final String BODY_SOURCE_EVENT_ID = "source_event_id";
    private static final String BODY_ATTRIBUTION_DESTINATION = "attribution_destination";
    private static final String BODY_SOURCE_SITE = "source_site";
    private static final String BODY_LIMIT = "limit";

    private final Context mContext;

    public DebugReportApi(Context context) {
        mContext = context;
    }

    /** Schedules the Source Destination limit Debug Report */
    public void scheduleSourceDestinationLimitDebugReport(
            Source source, String limit, IMeasurementDao dao) {
        try {
            JSONObject body = new JSONObject();
            body.put(BODY_SOURCE_EVENT_ID, source.getEventId().toString());
            body.put(
                    BODY_ATTRIBUTION_DESTINATION,
                    source.getPublisherType() == EventSurfaceType.APP
                            ? source.getAppDestinations().get(0).toString()
                            : source.getWebDestinations().get(0).toString());
            body.put(
                    BODY_SOURCE_SITE,
                    BaseUriExtractor.getBaseUri(source.getPublisher()).toString());
            body.put(BODY_LIMIT, limit);
            scheduleReport(TYPE_SOURCE_DESTINATION_LIMIT, body, source.getEnrollmentId(), dao);
        } catch (JSONException e) {
            LogUtil.e(e, "Json error in destination limit debug report");
        }
    }

    /** Schedules the Source Noised Debug Report */
    public void scheduleSourceNoisedDebugReport(Source source, IMeasurementDao dao) {
        try {
            JSONObject body = new JSONObject();
            body.put(BODY_SOURCE_EVENT_ID, source.getEventId().toString());
            body.put(
                    BODY_ATTRIBUTION_DESTINATION,
                    source.getPublisherType() == EventSurfaceType.APP
                            ? source.getAppDestinations().get(0).toString()
                            : source.getWebDestinations().get(0).toString());
            body.put(
                    BODY_SOURCE_SITE,
                    BaseUriExtractor.getBaseUri(source.getPublisher()).toString());
            scheduleReport(TYPE_SOURCE_NOISED, body, source.getEnrollmentId(), dao);
        } catch (JSONException e) {
            LogUtil.e(e, "Json error in source noised debug report");
        }
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
            LogUtil.d("Empty debug report found");
            return;
        }
        if (enrollmentId.isEmpty()) {
            LogUtil.d("Empty enrollment found");
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
            LogUtil.e(e, "Failed to insert debug report");
        }

        DebugReportingJobService.scheduleIfNeeded(
                mContext, /*forceSchedule=*/ true, /*isDebugReportApi=*/ true);
    }

}

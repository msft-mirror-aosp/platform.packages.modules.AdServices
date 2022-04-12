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

package com.android.adservices.service.measurement;

import android.net.Uri;

import com.android.adservices.data.measurement.DatastoreManager;
import com.android.internal.annotations.VisibleForTesting;

import org.json.JSONException;
import org.json.JSONObject;

import java.io.IOException;
import java.net.HttpURLConnection;
import java.util.Optional;

/**
 * Class for handling event level reporting.
 */
public class EventReportingJobHandler {

    private final DatastoreManager mDatastoreManager;

    EventReportingJobHandler(DatastoreManager datastoreManager) {
        mDatastoreManager = datastoreManager;
    }

    public enum PerformReportResult {
        SUCCESS,
        ALREADY_DELIVERED,
        POST_REQUEST_ERROR,
        DATASTORE_ERROR
    }

    /**
     * Perform reporting by finding the relevant {@link EventReport} and making an HTTP POST
     * request to the specified report to URL with the report data as a JSON in the body.
     *
     * @param eventReportId for the datastore id of the {@link EventReport}
     * @return success
     */
    synchronized PerformReportResult performReport(String eventReportId)
            throws JSONException, IOException {
        Optional<EventReport> eventReportOpt =
                mDatastoreManager.runInTransactionWithResult((dao)
                        -> dao.getEventReport(eventReportId));
        if (!eventReportOpt.isPresent()) {
            return PerformReportResult.DATASTORE_ERROR;
        }
        EventReport eventReport = eventReportOpt.get();
        if (eventReport.getStatus() != EventReport.Status.PENDING) {
            return PerformReportResult.ALREADY_DELIVERED;
        }

        JSONObject eventReportJsonPayload = createReportJsonPayload(eventReport);
        int returnCode = makeHttpPostRequest(eventReport.getReportTo(), eventReportJsonPayload);

        if (returnCode >= HttpURLConnection.HTTP_OK
                && returnCode <= 299) {
            boolean success = mDatastoreManager.runInTransaction((dao) ->
                    dao.markEventReportDelivered(eventReportId));
            return success ? PerformReportResult.SUCCESS : PerformReportResult.DATASTORE_ERROR;
        } else {
            // TODO: Determine behavior for other response codes?
            return PerformReportResult.POST_REQUEST_ERROR;
        }
    }

    /**
     * Creates the JSON payload for the POST request from the EventReport.
     */
    private JSONObject createReportJsonPayload(EventReport eventReport) throws JSONException {
        return new EventReportPayload.Builder()
                .setReportId(eventReport.getId())
                .setSourceEventId(String.valueOf(eventReport.getSourceId()))
                .setAttributionDestination(eventReport.getAttributionDestination().toString())
                .setTriggerData(String.valueOf(eventReport.getTriggerData()))
                .build()
                .toJson();
    }

    /**
     * Makes the POST request to the reporting URL.
     */
    @VisibleForTesting
    public int makeHttpPostRequest(Uri reportToUrl, JSONObject eventReportPayload)
            throws IOException {
        EventReportSender eventReportSender = new EventReportSender();
        return eventReportSender.sendEventReport(reportToUrl, eventReportPayload);
    }
}

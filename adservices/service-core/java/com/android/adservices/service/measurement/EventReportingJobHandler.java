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

import com.android.adservices.LogUtil;
import com.android.adservices.data.measurement.DatastoreManager;
import com.android.internal.annotations.VisibleForTesting;

import org.json.JSONException;
import org.json.JSONObject;

import java.io.IOException;
import java.net.HttpURLConnection;
import java.util.List;
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
     * Finds all reports within the given window that have a status
     * {@link EventReport.Status.PENDING} and attempts to upload them individually.
     * @param windowStartTime Start time of the search window
     * @param windowEndTime End time of the search window
     * @return always return true to signal to JobScheduler that the task is done.
     */
    synchronized boolean performScheduledPendingReportsInWindow(long windowStartTime,
            long windowEndTime) {
        Optional<List<String>> pendingEventReportsInWindowOpt = mDatastoreManager
                .runInTransactionWithResult((dao) ->
                        dao.getPendingEventReportIdsInWindow(windowStartTime, windowEndTime));
        if (!pendingEventReportsInWindowOpt.isPresent()) {
            // Failure during event report retrieval
            return true;
        }

        List<String> pendingEventReportIdsInWindow = pendingEventReportsInWindowOpt.get();
        for (String eventReportId : pendingEventReportIdsInWindow) {
            // TODO: Use result to track rate of success vs retry vs failure
            PerformReportResult result = performReport(eventReportId);
        }
        return true;
    }

    /**
     * Finds all event reports for an app, these event reports have a status
     * {@link EventReport.Status.PENDING} and attempts to upload them individually.
     * @param appName the given app name corresponding to the registrant field in Source table.
     * @return always return true to signal to JobScheduler that the task is done.
     */
    synchronized boolean performAllPendingReportsForGivenApp(Uri appName) {
        LogUtil.d("EventReportingJobHandler: performAllPendingReportsForGivenApp");
        Optional<List<String>> pendingEventReportsForGivenApp = mDatastoreManager
                .runInTransactionWithResult((dao) ->
                        dao.getPendingEventReportIdsForGivenApp(appName));

        if (!pendingEventReportsForGivenApp.isPresent()) {
            // Failure during event report retrieval
            return true;
        }

        List<String> pendingEventReportForGivenApp = pendingEventReportsForGivenApp.get();
        for (String eventReportId : pendingEventReportForGivenApp) {
            PerformReportResult result = performReport(eventReportId);
            if (result != PerformReportResult.SUCCESS) {
                LogUtil.i("Perform report status is %s for app : %s",
                        result, String.valueOf(appName));
            }
        }
        return true;
    }

    /**
     * Perform reporting by finding the relevant {@link EventReport} and making an HTTP POST
     * request to the specified report to URL with the report data as a JSON in the body.
     *
     * @param eventReportId for the datastore id of the {@link EventReport}
     * @return success
     */
    synchronized PerformReportResult performReport(String eventReportId) {
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
        try {
            JSONObject eventReportJsonPayload = createReportJsonPayload(eventReport);
            int returnCode = makeHttpPostRequest(eventReport.getAdTechDomain(),
                    eventReportJsonPayload);

            if (returnCode >= HttpURLConnection.HTTP_OK
                    && returnCode <= 299) {
                boolean success = mDatastoreManager.runInTransaction((dao) ->
                        dao.markEventReportDelivered(eventReportId));

                return success ? PerformReportResult.SUCCESS : PerformReportResult.DATASTORE_ERROR;
            } else {
                // TODO: Determine behavior for other response codes?
                return PerformReportResult.POST_REQUEST_ERROR;
            }
        } catch (Exception e) {
            LogUtil.e(e, e.toString());
            return PerformReportResult.POST_REQUEST_ERROR;
        }
    }

    /**
     * Creates the JSON payload for the POST request from the EventReport.
     */
    @VisibleForTesting
    JSONObject createReportJsonPayload(EventReport eventReport) throws JSONException {
        return new EventReportPayload.Builder()
                .setReportId(eventReport.getId())
                .setSourceEventId(String.valueOf(eventReport.getSourceId()))
                .setAttributionDestination(eventReport.getAttributionDestination().toString())
                .setTriggerData(String.valueOf(eventReport.getTriggerData()))
                .setSourceType(eventReport.getSourceType() == Source.SourceType.NAVIGATION
                        ? "navigation" : "event")
                .setRandomizedTriggerRate(eventReport.getRandomizedTriggerRate())
                .build()
                .toJson();
    }

    /**
     * Makes the POST request to the reporting URL.
     */
    @VisibleForTesting
    public int makeHttpPostRequest(Uri adTechDomain, JSONObject eventReportPayload)
            throws IOException {
        EventReportSender eventReportSender = new EventReportSender();
        return eventReportSender.sendReport(adTechDomain, eventReportPayload);
    }
}

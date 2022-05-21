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
import com.android.adservices.service.measurement.aggregation.CleartextAggregatePayload;
import com.android.internal.annotations.VisibleForTesting;

import org.json.JSONException;
import org.json.JSONObject;

import java.io.IOException;
import java.net.HttpURLConnection;
import java.util.List;
import java.util.Optional;
import java.util.concurrent.TimeUnit;

/**
 * Class for handling aggregate reporting.
 */

public class AggregateReportingJobHandler {

    private final DatastoreManager mDatastoreManager;
    private static final String API_VERSION_PLACEHOLDER = "1";

    AggregateReportingJobHandler(DatastoreManager datastoreManager) {
        mDatastoreManager = datastoreManager;
    }

    public enum PerformReportResult {
        SUCCESS,
        ALREADY_DELIVERED,
        POST_REQUEST_ERROR,
        DATASTORE_ERROR
    }

    /**
     * Finds all aggregate reports within the given window that have a status
     * {@link CleartextAggregatePayload.Status.PENDING} and attempts to upload them individually.
     *
     * @param windowStartTime Start time of the search window
     * @param windowEndTime   End time of the search window
     * @return always return true to signal to JobScheduler that the task is done.
     */
    synchronized boolean performScheduledPendingReportsInWindow(long windowStartTime,
            long windowEndTime) {
        Optional<List<String>> pendingAggregateReportsInWindowOpt = mDatastoreManager
                .runInTransactionWithResult((dao) ->
                        dao.getPendingAggregateReportIdsInWindow(windowStartTime, windowEndTime));
        if (!pendingAggregateReportsInWindowOpt.isPresent()) {
            // Failure during event report retrieval
            return true;
        }

        List<String> pendingAggregateReportIdsInWindow = pendingAggregateReportsInWindowOpt.get();
        for (String aggregateReportId : pendingAggregateReportIdsInWindow) {
            performReport(aggregateReportId);
        }
        return true;
    }

    /**
     * Finds all aggregate reports for an app, these aggregate reports have a status
     * {@link EventReport.Status.PENDING} and attempts to upload them individually.
     * @param appName the given app name corresponding to the registrant field in Source table.
     * @return always return true to signal to JobScheduler that the task is done.
     */

    synchronized boolean performAllPendingReportsForGivenApp(Uri appName) {
        LogUtil.d("AggregateReportingJobHandler: performAllPendingReportsForGivenApp");
        Optional<List<String>> pendingAggregateReportsForGivenApp = mDatastoreManager
                .runInTransactionWithResult((dao) ->
                        dao.getPendingAggregateReportIdsForGivenApp(appName));

        if (!pendingAggregateReportsForGivenApp.isPresent()) {
            // Failure during event report retrieval
            return true;
        }

        List<String> pendingAggregateReportForGivenApp = pendingAggregateReportsForGivenApp.get();
        for (String aggregateReportId : pendingAggregateReportForGivenApp) {
            PerformReportResult result = performReport(aggregateReportId);
            if (result != PerformReportResult.SUCCESS) {
                LogUtil.i("Perform report status is %s for app : %s",
                        result, String.valueOf(appName));
            }
        }
        return true;
    }

    /**
     * Perform aggregate reporting by finding the relevant {@link CleartextAggregatePayload} and
     * making an HTTP POST request to the specified report to URL with the report data as a JSON in
     * the body.
     *
     * @param aggregateReportId for the datastore id of the {@link CleartextAggregatePayload}
     * @return success
     */
    synchronized PerformReportResult performReport(String aggregateReportId) {
        Optional<CleartextAggregatePayload> aggregateReportOpt =
                mDatastoreManager.runInTransactionWithResult((dao)
                        -> dao.getAggregateReport(aggregateReportId));
        if (!aggregateReportOpt.isPresent()) {
            return PerformReportResult.DATASTORE_ERROR;
        }
        CleartextAggregatePayload aggregateReport = aggregateReportOpt.get();
        if (aggregateReport.getStatus() != CleartextAggregatePayload.Status.PENDING) {
            return PerformReportResult.ALREADY_DELIVERED;
        }
        try {
            JSONObject aggregateReportJsonBody = createReportJsonPayload(aggregateReport);
            int returnCode = makeHttpPostRequest(aggregateReport.getReportingOrigin(),
                    aggregateReportJsonBody);

            if (returnCode >= HttpURLConnection.HTTP_OK
                    && returnCode <= 299) {
                boolean success = mDatastoreManager.runInTransaction((dao) ->
                        dao.markAggregateReportDelivered(aggregateReportId));

                return success ? PerformReportResult.SUCCESS :
                        PerformReportResult.DATASTORE_ERROR;
            } else {
                return PerformReportResult.POST_REQUEST_ERROR;
            }
        } catch (Exception e) {
            LogUtil.e(e, e.toString());
            return PerformReportResult.POST_REQUEST_ERROR;
        }
    }

    /**
     * Creates the JSON payload for the POST request from the AggregateReport.
     */
    @VisibleForTesting
    JSONObject createReportJsonPayload(CleartextAggregatePayload aggregateReport)
            throws JSONException {
        return new AggregateReportBody.Builder()
                .setReportId(aggregateReport.getId())
                .setSourceSite(aggregateReport.getSourceSite().toString())
                .setAttributionDestination(aggregateReport.getAttributionDestination().toString())
                .setSourceRegistrationTime(
                        String.valueOf(TimeUnit.MILLISECONDS.toSeconds(
                                aggregateReport.getSourceRegistrationTime())))
                .setScheduledReportTime(
                        String.valueOf(TimeUnit.MILLISECONDS.toSeconds(
                                aggregateReport.getScheduledReportTime())))
                .setPrivacyBudgetKey("") // For now this is blank since the payload is not encrypted
                .setVersion(API_VERSION_PLACEHOLDER) // For now this is just 1 as a placeholder
                .setReportingOrigin(aggregateReport.getReportingOrigin().toString())
                .setDebugCleartextPayload(aggregateReport.getDebugCleartextPayload())
                .build()
                .toJson();
    }

    /**
     * Makes the POST request to the reporting URL.
     */
    @VisibleForTesting
    public int makeHttpPostRequest(Uri adTechDomain, JSONObject aggregateReportBody)
            throws IOException {
        AggregateReportSender aggregateReportSender = new AggregateReportSender();
        return aggregateReportSender.sendReport(adTechDomain, aggregateReportBody);
    }
}

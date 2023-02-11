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

import static com.android.adservices.service.stats.AdServicesStatsLog.AD_SERVICES_MEASUREMENT_REPORTS_UPLOADED__TYPE__EVENT;
import static com.android.adservices.service.stats.AdServicesStatsLog.AD_SERVICES_MESUREMENT_REPORTS_UPLOADED;

import android.adservices.common.AdServicesStatusUtils;
import android.net.Uri;

import com.android.adservices.LogUtil;
import com.android.adservices.data.enrollment.EnrollmentDao;
import com.android.adservices.data.measurement.DatastoreManager;
import com.android.adservices.service.measurement.EventReport;
import com.android.adservices.service.measurement.util.Enrollment;
import com.android.adservices.service.stats.AdServicesLoggerImpl;
import com.android.adservices.service.stats.MeasurementReportsStats;
import com.android.internal.annotations.VisibleForTesting;

import org.json.JSONException;
import org.json.JSONObject;

import java.io.IOException;
import java.net.HttpURLConnection;
import java.util.List;
import java.util.Optional;
import java.util.concurrent.TimeUnit;

/**
 * Class for handling event level reporting.
 */
public class EventReportingJobHandler {

    private final EnrollmentDao mEnrollmentDao;
    private final DatastoreManager mDatastoreManager;
    private boolean mIsDebugInstance;

    EventReportingJobHandler(EnrollmentDao enrollmentDao, DatastoreManager datastoreManager) {
        mEnrollmentDao = enrollmentDao;
        mDatastoreManager = datastoreManager;
    }

    /**
     * Set isDebugInstance
     *
     * @param isDebugInstance indicates a debug event report
     * @return the instance of EventReportingJobHandler
     */
    public EventReportingJobHandler setIsDebugInstance(boolean isDebugInstance) {
        mIsDebugInstance = isDebugInstance;
        return this;
    }

    /**
     * Finds all reports within the given window that have a status {@link
     * EventReport.Status#PENDING} or {@link EventReport.DebugReportStatus#PENDING} based on
     * mIsDebugReport and attempts to upload them individually.
     *
     * @param windowStartTime Start time of the search window
     * @param windowEndTime End time of the search window
     * @return always return true to signal to JobScheduler that the task is done.
     */
    synchronized boolean performScheduledPendingReportsInWindow(
            long windowStartTime, long windowEndTime) {
        Optional<List<String>> pendingEventReportsInWindowOpt =
                mDatastoreManager.runInTransactionWithResult(
                        (dao) -> {
                            if (mIsDebugInstance) {
                                return dao.getPendingDebugEventReportIds();
                            } else {
                                return dao.getPendingEventReportIdsInWindow(
                                        windowStartTime, windowEndTime);
                            }
                        });
        if (!pendingEventReportsInWindowOpt.isPresent()) {
            // Failure during event report retrieval
            return true;
        }

        List<String> pendingEventReportIdsInWindow = pendingEventReportsInWindowOpt.get();
        for (String eventReportId : pendingEventReportIdsInWindow) {
            // TODO: Use result to track rate of success vs retry vs failure
            @AdServicesStatusUtils.StatusCode int result = performReport(eventReportId);
            logReportingStats(result);
        }
        return true;
    }

    /**
     * Perform reporting by finding the relevant {@link EventReport} and making an HTTP POST request
     * to the specified report to URL with the report data as a JSON in the body.
     *
     * @param eventReportId for the datastore id of the {@link EventReport}
     * @return success
     */
    synchronized int performReport(String eventReportId) {
        Optional<EventReport> eventReportOpt =
                mDatastoreManager.runInTransactionWithResult((dao)
                        -> dao.getEventReport(eventReportId));
        if (!eventReportOpt.isPresent()) {
            LogUtil.d("Event report not found");
            return AdServicesStatusUtils.STATUS_IO_ERROR;
        }
        EventReport eventReport = eventReportOpt.get();

        if (mIsDebugInstance
                && eventReport.getDebugReportStatus() != EventReport.DebugReportStatus.PENDING) {
            LogUtil.d("debugging status is not pending");
            return AdServicesStatusUtils.STATUS_INVALID_ARGUMENT;
        }
        if (!mIsDebugInstance && eventReport.getStatus() != EventReport.Status.PENDING) {
            LogUtil.d("event report status is not pending");
            return AdServicesStatusUtils.STATUS_INVALID_ARGUMENT;
        }
        try {
            Optional<Uri> reportingOrigin = Enrollment.maybeGetReportingOrigin(
                    eventReport.getEnrollmentId(), mEnrollmentDao);
            if (!reportingOrigin.isPresent()) {
                // We do not know here what the cause is of the failure to retrieve the reporting
                // origin. INTERNAL_ERROR seems the closest to a "catch-all" error code.
                LogUtil.d("Report origin not present");
                return AdServicesStatusUtils.STATUS_INTERNAL_ERROR;
            }
            JSONObject eventReportJsonPayload = createReportJsonPayload(eventReport);
            int returnCode = makeHttpPostRequest(reportingOrigin.get(), eventReportJsonPayload);

            if (returnCode >= HttpURLConnection.HTTP_OK
                    && returnCode <= 299) {
                boolean success =
                        mDatastoreManager.runInTransaction(
                                (dao) -> {
                                    if (mIsDebugInstance) {
                                        dao.markEventDebugReportDelivered(eventReportId);
                                    } else {
                                        dao.markEventReportStatus(
                                                eventReportId, EventReport.Status.DELIVERED);
                                    }
                                });

                return success
                        ? AdServicesStatusUtils.STATUS_SUCCESS
                        : AdServicesStatusUtils.STATUS_IO_ERROR;
            } else {
                // TODO: Determine behavior for other response codes?
                return AdServicesStatusUtils.STATUS_IO_ERROR;
            }
        } catch (Exception e) {
            LogUtil.e(e, e.toString());
            return AdServicesStatusUtils.STATUS_UNKNOWN_ERROR;
        }
    }

    /**
     * Creates the JSON payload for the POST request from the EventReport.
     */
    @VisibleForTesting
    JSONObject createReportJsonPayload(EventReport eventReport) throws JSONException {
        return new EventReportPayload.Builder()
                .setReportId(eventReport.getId())
                .setSourceEventId(eventReport.getSourceEventId())
                .setAttributionDestination(
                        eventReport.getAttributionDestinations().get(0).toString())
                .setScheduledReportTime(
                        String.valueOf(
                                TimeUnit.MILLISECONDS.toSeconds(
                                        eventReport.getReportTime())))
                .setTriggerData(eventReport.getTriggerData())
                .setSourceType(eventReport.getSourceType().getValue())
                .setRandomizedTriggerRate(eventReport.getRandomizedTriggerRate())
                .setSourceDebugKey(eventReport.getSourceDebugKey())
                .setTriggerDebugKey(eventReport.getTriggerDebugKey())
                .build()
                .toJson();
    }

    /**
     * Makes the POST request to the reporting URL.
     */
    @VisibleForTesting
    public int makeHttpPostRequest(Uri adTechDomain, JSONObject eventReportPayload)
            throws IOException {
        EventReportSender eventReportSender = new EventReportSender(mIsDebugInstance);
        return eventReportSender.sendReport(adTechDomain, eventReportPayload);
    }

    private void logReportingStats(int resultCode) {
        AdServicesLoggerImpl.getInstance()
                .logMeasurementReports(
                        new MeasurementReportsStats.Builder()
                                .setCode(AD_SERVICES_MESUREMENT_REPORTS_UPLOADED)
                                .setType(AD_SERVICES_MEASUREMENT_REPORTS_UPLOADED__TYPE__EVENT)
                                .setResultCode(resultCode)
                                .build());
    }
}

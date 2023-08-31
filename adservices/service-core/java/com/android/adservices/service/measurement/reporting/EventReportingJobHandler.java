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
import com.android.adservices.service.Flags;
import com.android.adservices.service.FlagsFactory;
import com.android.adservices.service.measurement.EventReport;
import com.android.adservices.service.stats.AdServicesLoggerImpl;
import com.android.adservices.service.stats.MeasurementReportsStats;
import com.android.internal.annotations.VisibleForTesting;

import org.json.JSONException;
import org.json.JSONObject;

import java.io.IOException;
import java.net.HttpURLConnection;
import java.util.List;
import java.util.Optional;
import java.util.concurrent.ThreadLocalRandom;
import java.util.concurrent.TimeUnit;

/**
 * Class for handling event level reporting.
 */
public class EventReportingJobHandler {

    private final EnrollmentDao mEnrollmentDao;
    private final DatastoreManager mDatastoreManager;
    private boolean mIsDebugInstance;

    private ReportingStatus.UploadMethod mUploadMethod;
    private final Flags mFlags;

    EventReportingJobHandler(
            EnrollmentDao enrollmentDao, DatastoreManager datastoreManager, Flags flags) {
        this(enrollmentDao, datastoreManager, null, flags);
    }

    EventReportingJobHandler(
            EnrollmentDao enrollmentDao,
            DatastoreManager datastoreManager,
            ReportingStatus.UploadMethod uploadMethod) {
        this(enrollmentDao, datastoreManager, uploadMethod, FlagsFactory.getFlags());
    }

    EventReportingJobHandler(
            EnrollmentDao enrollmentDao,
            DatastoreManager datastoreManager,
            ReportingStatus.UploadMethod uploadMethod,
            Flags flags) {
        mEnrollmentDao = enrollmentDao;
        mDatastoreManager = datastoreManager;
        mUploadMethod = uploadMethod;
        mFlags = flags;
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

            // If the job service's requirements specified at runtime are no longer met, the job
            // service will interrupt this thread.  If the thread has been interrupted, it will exit
            // early.
            if (Thread.currentThread().isInterrupted()) {
                LogUtil.d(
                        "EventReportingJobHandler performScheduledPendingReports "
                                + "thread interrupted, exiting early.");
                return true;
            }

            // TODO: Use result to track rate of success vs retry vs failure
            ReportingStatus reportingStatus = new ReportingStatus();
            @AdServicesStatusUtils.StatusCode
            int result = performReport(eventReportId, reportingStatus);

            if (result == AdServicesStatusUtils.STATUS_SUCCESS) {
                reportingStatus.setUploadStatus(ReportingStatus.UploadStatus.SUCCESS);
            } else {
                reportingStatus.setUploadStatus(ReportingStatus.UploadStatus.FAILURE);
            }

            if (mUploadMethod != null) {
                reportingStatus.setUploadMethod(mUploadMethod);
            }
            // Logged as UNKNOWN_UPLOAD_METHOD for debug reports
            logReportingStats(reportingStatus);
        }
        return true;
    }

    private String getAppPackageName(EventReport eventReport) {
        if (!mFlags.getMeasurementEnableAppPackageNameLogging()) {
            return "";
        }
        if (eventReport.getSourceId() == null) {
            LogUtil.d("SourceId is null on event report.");
            return "";
        }
        Optional<String> sourceRegistrant =
                mDatastoreManager.runInTransactionWithResult(
                        (dao) -> dao.getSourceRegistrant(eventReport.getSourceId()));
        if (!sourceRegistrant.isPresent()) {
            LogUtil.d("Source registrant not found");
            return "";
        }
        return sourceRegistrant.get();
    }

    /**
     * Perform reporting by finding the relevant {@link EventReport} and making an HTTP POST request
     * to the specified report to URL with the report data as a JSON in the body.
     *
     * @param eventReportId for the datastore id of the {@link EventReport}
     * @return success
     */
    synchronized int performReport(String eventReportId, ReportingStatus reportingStatus) {
        Optional<EventReport> eventReportOpt =
                mDatastoreManager.runInTransactionWithResult((dao)
                        -> dao.getEventReport(eventReportId));
        if (!eventReportOpt.isPresent()) {
            LogUtil.d("Event report not found");
            return AdServicesStatusUtils.STATUS_IO_ERROR;
        }
        EventReport eventReport = eventReportOpt.get();
        reportingStatus.setSourceRegistrant(getAppPackageName(eventReport));
        if (mIsDebugInstance
                && eventReport.getDebugReportStatus() != EventReport.DebugReportStatus.PENDING) {
            LogUtil.d("debugging status is not pending");
            reportingStatus.setFailureStatus(ReportingStatus.FailureStatus.REPORT_NOT_PENDING);
            return AdServicesStatusUtils.STATUS_INVALID_ARGUMENT;
        }
        if (!mIsDebugInstance && eventReport.getStatus() != EventReport.Status.PENDING) {
            LogUtil.d("event report status is not pending");
            reportingStatus.setFailureStatus(ReportingStatus.FailureStatus.REPORT_NOT_PENDING);
            return AdServicesStatusUtils.STATUS_INVALID_ARGUMENT;
        }
        try {
            Uri reportingOrigin = eventReport.getRegistrationOrigin();
            JSONObject eventReportJsonPayload = createReportJsonPayload(eventReport);
            int returnCode = makeHttpPostRequest(reportingOrigin, eventReportJsonPayload);

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

                if (success) {
                    long deliveryTime = System.currentTimeMillis();
                    reportingStatus.setReportingDelay(deliveryTime - eventReport.getReportTime());
                    return AdServicesStatusUtils.STATUS_SUCCESS;
                } else {
                    reportingStatus.setFailureStatus(ReportingStatus.FailureStatus.DATASTORE);
                    return AdServicesStatusUtils.STATUS_IO_ERROR;
                }
            } else {
                // TODO: Determine behavior for other response codes?
                reportingStatus.setFailureStatus(ReportingStatus.FailureStatus.NETWORK);
                return AdServicesStatusUtils.STATUS_IO_ERROR;
            }
        } catch (IOException e) {
            LogUtil.d(e, "Network error occurred when attempting to deliver event report.");
            reportingStatus.setFailureStatus(ReportingStatus.FailureStatus.NETWORK);
            // TODO(b/297579501): Log the error with ErrorLogUtil
            return AdServicesStatusUtils.STATUS_IO_ERROR;
        } catch (JSONException e) {
            LogUtil.d(e, "Serialization error occurred at event report delivery.");
            // TODO(b/297579501): Update the atom and the status to indicate serialization error
            reportingStatus.setFailureStatus(ReportingStatus.FailureStatus.UNKNOWN);
            // TODO(b/297579501): Log the error with ErrorLogUtil with the serialization error code

            if (mFlags.getMeasurementEnableReportDeletionOnUnrecoverableException()) {
                // Unrecoverable state - delete the report.
                mDatastoreManager.runInTransaction(
                        dao ->
                                dao.markEventReportStatus(
                                        eventReportId, EventReport.Status.MARKED_TO_DELETE));
            }

            if (mFlags.getMeasurementEnableReportingJobsThrowJsonException()
                    && ThreadLocalRandom.current().nextFloat()
                            < mFlags.getMeasurementThrowUnknownExceptionSamplingRate()) {
                // JSONException is unexpected.
                throw new IllegalStateException(
                        "Serialization error occurred at event report delivery", e);
            }
            return AdServicesStatusUtils.STATUS_UNKNOWN_ERROR;
        } catch (Exception e) {
            LogUtil.e(e, "Unexpected exception occurred when attempting to deliver event report.");
            reportingStatus.setFailureStatus(ReportingStatus.FailureStatus.UNKNOWN);
            if (mFlags.getMeasurementEnableReportingJobsThrowUnaccountedException()
                    && ThreadLocalRandom.current().nextFloat()
                            < mFlags.getMeasurementThrowUnknownExceptionSamplingRate()) {
                throw e;
            }
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
                .setAttributionDestination(eventReport.getAttributionDestinations())
                .setScheduledReportTime(
                        String.valueOf(
                                TimeUnit.MILLISECONDS.toSeconds(eventReport.getReportTime())))
                .setTriggerData(eventReport.getTriggerData())
                .setSourceType(eventReport.getSourceType().getValue())
                .setRandomizedTriggerRate(eventReport.getRandomizedTriggerRate())
                .setSourceDebugKey(eventReport.getSourceDebugKey())
                .setTriggerDebugKey(eventReport.getTriggerDebugKey())
                .setTriggerSummaryBucket(eventReport.getTriggerSummaryBucket())
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

    private void logReportingStats(ReportingStatus reportingStatus) {
        if (!reportingStatus.getReportingDelay().isPresent()) {
            reportingStatus.setReportingDelay(0L);
        }
        AdServicesLoggerImpl.getInstance()
                .logMeasurementReports(
                        new MeasurementReportsStats.Builder()
                                .setCode(AD_SERVICES_MESUREMENT_REPORTS_UPLOADED)
                                .setType(AD_SERVICES_MEASUREMENT_REPORTS_UPLOADED__TYPE__EVENT)
                                .setResultCode(reportingStatus.getUploadStatus().ordinal())
                                .setFailureType(reportingStatus.getFailureStatus().ordinal())
                                .setUploadMethod(reportingStatus.getUploadMethod().ordinal())
                                .setReportingDelay(reportingStatus.getReportingDelay().get())
                                .setSourceRegistrant(reportingStatus.getSourceRegistrant())
                                .build());
    }
}

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

import static com.android.adservices.service.stats.AdServicesStatsLog.AD_SERVICES_MEASUREMENT_REPORTS_UPLOADED__TYPE__AGGREGATE;
import static com.android.adservices.service.stats.AdServicesStatsLog.AD_SERVICES_MESUREMENT_REPORTS_UPLOADED;

import android.adservices.common.AdServicesStatusUtils;
import android.net.Uri;

import com.android.adservices.LogUtil;
import com.android.adservices.data.enrollment.EnrollmentDao;
import com.android.adservices.data.measurement.DatastoreManager;
import com.android.adservices.service.measurement.aggregation.AggregateEncryptionKey;
import com.android.adservices.service.measurement.aggregation.AggregateEncryptionKeyManager;
import com.android.adservices.service.measurement.aggregation.AggregateReport;
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
 * Class for handling aggregate reporting.
 */

public class AggregateReportingJobHandler {

    private final EnrollmentDao mEnrollmentDao;
    private final DatastoreManager mDatastoreManager;
    private final AggregateEncryptionKeyManager mAggregateEncryptionKeyManager;
    private boolean mIsDebugInstance;

    AggregateReportingJobHandler(EnrollmentDao enrollmentDao, DatastoreManager datastoreManager) {
        mEnrollmentDao = enrollmentDao;
        mDatastoreManager = datastoreManager;
        mAggregateEncryptionKeyManager = new AggregateEncryptionKeyManager(datastoreManager);
    }

    @VisibleForTesting
    AggregateReportingJobHandler(
            EnrollmentDao enrollmentDao,
            DatastoreManager datastoreManager,
            AggregateEncryptionKeyManager aggregateEncryptionKeyManager) {
        mEnrollmentDao = enrollmentDao;
        mDatastoreManager = datastoreManager;
        mAggregateEncryptionKeyManager = aggregateEncryptionKeyManager;
    }

    /**
     * Set isDebugInstance
     *
     * @param isDebugInstance indicates a debug aggregate report
     * @return the instance of AggregateReportingJobHandler
     */
    public AggregateReportingJobHandler setIsDebugInstance(boolean isDebugInstance) {
        mIsDebugInstance = isDebugInstance;
        return this;
    }

    /**
     * Finds all aggregate reports within the given window that have a status {@link
     * AggregateReport.Status#PENDING} or {@link AggregateReport.DebugReportStatus#PENDING} based on
     * mIsDebugReport and attempts to upload them individually.
     *
     * @param windowStartTime Start time of the search window
     * @param windowEndTime End time of the search window
     * @return always return true to signal to JobScheduler that the task is done.
     */
    synchronized boolean performScheduledPendingReportsInWindow(
            long windowStartTime, long windowEndTime) {
        Optional<List<String>> pendingAggregateReportsInWindowOpt =
                mDatastoreManager.runInTransactionWithResult(
                        (dao) -> {
                            if (mIsDebugInstance) {
                                return dao.getPendingAggregateDebugReportIds();
                            } else {
                                return dao.getPendingAggregateReportIdsInWindow(
                                        windowStartTime, windowEndTime);
                            }
                        });
        if (!pendingAggregateReportsInWindowOpt.isPresent()) {
            // Failure during event report retrieval
            return true;
        }

        List<String> pendingAggregateReportIdsInWindow = pendingAggregateReportsInWindowOpt.get();
        List<AggregateEncryptionKey> keys =
                mAggregateEncryptionKeyManager.getAggregateEncryptionKeys(
                        pendingAggregateReportIdsInWindow.size());

        if (keys.size() == pendingAggregateReportIdsInWindow.size()) {
            for (int i = 0; i < pendingAggregateReportIdsInWindow.size(); i++) {
                final String aggregateReportId = pendingAggregateReportIdsInWindow.get(i);
                @AdServicesStatusUtils.StatusCode
                int result = performReport(aggregateReportId, keys.get(i));
                logReportingStats(result);
            }
        } else {
            LogUtil.w("The number of keys do not align with the number of reports");
        }
        return true;
    }

    /**
     * Perform aggregate reporting by finding the relevant {@link AggregateReport} and making an
     * HTTP POST request to the specified report to URL with the report data as a JSON in the body.
     *
     * @param aggregateReportId for the datastore id of the {@link AggregateReport}
     * @param key used for encrypting report payload
     * @return success
     */
    @AdServicesStatusUtils.StatusCode
    synchronized int performReport(String aggregateReportId, AggregateEncryptionKey key) {
        Optional<AggregateReport> aggregateReportOpt =
                mDatastoreManager.runInTransactionWithResult((dao)
                        -> dao.getAggregateReport(aggregateReportId));
        if (!aggregateReportOpt.isPresent()) {
            LogUtil.d("Aggregate report not found");
            return AdServicesStatusUtils.STATUS_IO_ERROR;
        }
        AggregateReport aggregateReport = aggregateReportOpt.get();

        if (mIsDebugInstance
                && aggregateReport.getDebugReportStatus()
                        != AggregateReport.DebugReportStatus.PENDING) {
            LogUtil.d("Debugging status is not pending");
            return AdServicesStatusUtils.STATUS_INVALID_ARGUMENT;
        }
        if (!mIsDebugInstance && aggregateReport.getStatus() != AggregateReport.Status.PENDING) {
            return AdServicesStatusUtils.STATUS_INVALID_ARGUMENT;
        }
        try {
            Optional<Uri> reportingOrigin = Enrollment.maybeGetReportingOrigin(
                    aggregateReport.getEnrollmentId(), mEnrollmentDao);
            if (!reportingOrigin.isPresent()) {
                // We do not know here what the cause is of the failure to retrieve the reporting
                // origin. INTERNAL_ERROR seems the closest to a "catch-all" error code.
                LogUtil.d("Report origin not present");
                return AdServicesStatusUtils.STATUS_INTERNAL_ERROR;
            }
            JSONObject aggregateReportJsonBody = createReportJsonPayload(
                    aggregateReport, reportingOrigin.get(), key);
            int returnCode = makeHttpPostRequest(reportingOrigin.get(), aggregateReportJsonBody);

            if (returnCode >= HttpURLConnection.HTTP_OK
                    && returnCode <= 299) {
                boolean success =
                        mDatastoreManager.runInTransaction(
                                (dao) -> {
                                    if (mIsDebugInstance) {
                                        dao.markAggregateDebugReportDelivered(aggregateReportId);
                                    } else {
                                        dao.markAggregateReportStatus(
                                                aggregateReportId,
                                                AggregateReport.Status.DELIVERED);
                                    }
                                });

                return success
                        ? AdServicesStatusUtils.STATUS_SUCCESS
                        : AdServicesStatusUtils.STATUS_IO_ERROR;
            } else {
                return AdServicesStatusUtils.STATUS_IO_ERROR;
            }
        } catch (Exception e) {
            LogUtil.e(e, e.toString());
            return AdServicesStatusUtils.STATUS_UNKNOWN_ERROR;
        }
    }

    /** Creates the JSON payload for the POST request from the AggregateReport. */
    @VisibleForTesting
    JSONObject createReportJsonPayload(AggregateReport aggregateReport, Uri reportingOrigin,
            AggregateEncryptionKey key) throws JSONException {
        return new AggregateReportBody.Builder()
                .setReportId(aggregateReport.getId())
                .setAttributionDestination(aggregateReport.getAttributionDestination().toString())
                .setSourceRegistrationTime(
                        String.valueOf(
                                TimeUnit.MILLISECONDS.toSeconds(
                                        aggregateReport.getSourceRegistrationTime())))
                .setScheduledReportTime(
                        String.valueOf(
                                TimeUnit.MILLISECONDS.toSeconds(
                                        aggregateReport.getScheduledReportTime())))
                .setApiVersion(aggregateReport.getApiVersion())
                .setReportingOrigin(reportingOrigin.toString())
                .setDebugCleartextPayload(aggregateReport.getDebugCleartextPayload())
                .setSourceDebugKey(aggregateReport.getSourceDebugKey())
                .setTriggerDebugKey(aggregateReport.getTriggerDebugKey())
                .build()
                .toJson(key);
    }

    /**
     * Makes the POST request to the reporting URL.
     */
    @VisibleForTesting
    public int makeHttpPostRequest(Uri adTechDomain, JSONObject aggregateReportBody)
            throws IOException {
        AggregateReportSender aggregateReportSender = new AggregateReportSender(mIsDebugInstance);
        return aggregateReportSender.sendReport(adTechDomain, aggregateReportBody);
    }

    private void logReportingStats(int resultCode) {
        AdServicesLoggerImpl.getInstance()
                .logMeasurementReports(
                        new MeasurementReportsStats.Builder()
                                .setCode(AD_SERVICES_MESUREMENT_REPORTS_UPLOADED)
                                .setType(AD_SERVICES_MEASUREMENT_REPORTS_UPLOADED__TYPE__AGGREGATE)
                                .setResultCode(resultCode)
                                .build());
    }
}

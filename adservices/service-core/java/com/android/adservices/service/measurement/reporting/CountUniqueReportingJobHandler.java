/*
 * Copyright (C) 2025 The Android Open Source Project
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

import static com.android.adservices.service.stats.AdServicesStatsLog.AD_SERVICES_ERROR_REPORTED__ERROR_CODE__MEASUREMENT_REPORTING_ENCRYPTION_ERROR;
import static com.android.adservices.service.stats.AdServicesStatsLog.AD_SERVICES_ERROR_REPORTED__ERROR_CODE__MEASUREMENT_REPORTING_NETWORK_ERROR;
import static com.android.adservices.service.stats.AdServicesStatsLog.AD_SERVICES_ERROR_REPORTED__ERROR_CODE__MEASUREMENT_REPORTING_PARSING_ERROR;
import static com.android.adservices.service.stats.AdServicesStatsLog.AD_SERVICES_ERROR_REPORTED__ERROR_CODE__MEASUREMENT_REPORTING_UNKNOWN_ERROR;
import static com.android.adservices.service.stats.AdServicesStatsLog.AD_SERVICES_ERROR_REPORTED__PPAPI_NAME__MEASUREMENT;

import android.content.Context;
import android.net.Uri;

import com.android.adservices.LoggerFactory;
import com.android.adservices.data.measurement.DatastoreManager;
import com.android.adservices.errorlogging.ErrorLogUtil;
import com.android.adservices.service.Flags;
import com.android.adservices.service.exception.CryptoException;
import com.android.adservices.service.measurement.CountUniqueReport;
import com.android.adservices.service.measurement.EventReport;
import com.android.adservices.service.measurement.aggregation.AggregateEncryptionKey;
import com.android.adservices.service.measurement.aggregation.AggregateEncryptionKeyManager;
import com.android.internal.annotations.VisibleForTesting;

import org.json.JSONException;
import org.json.JSONObject;

import java.io.IOException;
import java.net.HttpURLConnection;
import java.util.List;
import java.util.Optional;

public class CountUniqueReportingJobHandler {
    private static final int MAX_HTTP_SUCCESS_CODE = 299;
    private static final String LOGGING_NAME = "CountUniqueReportingJobHandler";
    // TODO(404343446): update API_NAME to accurately reflect feature (possibly with a flag)
    public static final String API_NAME = "shared-storage";
    private boolean mIsDebugInstance;
    private final DatastoreManager mDatastoreManager;
    private final AggregateEncryptionKeyManager mAggregateEncryptionKeyManager;
    private final Flags mFlags;
    private final Context mContext;

    CountUniqueReportingJobHandler(
            DatastoreManager datastoreManager,
            AggregateEncryptionKeyManager aggregateEncryptionKeyManager,
            Flags flags,
            Context context) {
        mDatastoreManager = datastoreManager;
        mAggregateEncryptionKeyManager = aggregateEncryptionKeyManager;
        mFlags = flags;
        mContext = context;
    }

    /**
     * Finds all count unique reports that have a status of {@link
     * CountUniqueReport.ReportDeliveryStatus#PENDING} and attempts to upload them individually.
     *
     * @return always return true to signal to JobScheduler that the task is done.
     */
    synchronized boolean performScheduledPendingReports() {
        if (!mFlags.getMeasurementEnableCountUniqueService()) {
            // Count unique is disabled
            return true;
        }
        // TODO(402197747): Add separate instance for debug reports.
        Optional<List<String>> pendingCountUniqueReportsOpt =
                mDatastoreManager.runInTransactionWithResult(
                        (dao) -> {
                            return dao.getPendingCountUniqueReportIds();
                        });

        if (pendingCountUniqueReportsOpt.isEmpty()) {
            ReportUtil.logReportingFailure(LOGGING_NAME, "Pending Count Unique Reports not found");
            ErrorLogUtil.e(
                    AD_SERVICES_ERROR_REPORTED__ERROR_CODE__MEASUREMENT_REPORTING_UNKNOWN_ERROR,
                    AD_SERVICES_ERROR_REPORTED__PPAPI_NAME__MEASUREMENT);

            return true;
        }
        List<String> pendingCountUniqueReports = pendingCountUniqueReportsOpt.get();

        // TODO(399672589): Add support for different coordinator origins.
        Uri defaultCoordinatorOrigin =
                Uri.parse(mFlags.getMeasurementDefaultAggregationCoordinatorOrigin());
        List<AggregateEncryptionKey> keys =
                mAggregateEncryptionKeyManager.getAggregateEncryptionKeys(
                        defaultCoordinatorOrigin, pendingCountUniqueReports.size());

        if (keys.size() != pendingCountUniqueReports.size()) {
            LoggerFactory.getMeasurementLogger()
                    .w(
                            String.format(
                                    "%s: The number of keys do not align with the number of"
                                            + " reports",
                                    LOGGING_NAME));
            return true;
        }

        for (int i = 0; i < pendingCountUniqueReports.size(); i++) {
            // If the job service's requirements specified at runtime are no longer met, the
            // job service will interrupt this thread.  If the thread has been interrupted,
            // it will exit early.
            if (Thread.currentThread().isInterrupted()) {
                ReportUtil.logReportingFailure(LOGGING_NAME, "Thread interrupted, exiting early");
                return true;
            }

            performReport(pendingCountUniqueReports.get(i), keys.get(i));
        }

        return true;
    }

    /**
     * Perform reporting by finding the relevant {@link EventReport} and making an HTTP POST request
     * to the specified report to URL with the report data as a JSON in the body.
     *
     * @param countUniqueReportId for the datastore id of the {@link CountUniqueReport}
     * @param key used for encrypting report payload
     */
    synchronized void performReport(String countUniqueReportId, AggregateEncryptionKey key) {
        Optional<CountUniqueReport> countUniqueReportOpt =
                mDatastoreManager.runInTransactionWithResult(
                        (dao) -> dao.getCountUniqueReport(countUniqueReportId));

        if (countUniqueReportOpt.isEmpty()) {
            ReportUtil.logReportingFailure(
                    LOGGING_NAME,
                    String.format(
                            "Pending Count Unique Reports not found: ID: %s", countUniqueReportId));
            return;
        }

        CountUniqueReport countUniqueReport = countUniqueReportOpt.get();

        if (countUniqueReport.getStatus() != CountUniqueReport.ReportDeliveryStatus.PENDING) {
            ReportUtil.logReportingFailure(
                    LOGGING_NAME,
                    "Count Unique report status is not pending",
                    countUniqueReportId,
                    null,
                    ReportingStatus.ReportType.COUNT_UNIQUE.toString());
            // TODO(400528120): Add report status logging for report not pending.
            return;
        }

        try {
            JSONObject countUniqueReportJsonBody = createReportJsonPayload(countUniqueReport, key);
            int returnCode =
                    makeHttpPostRequest(
                            countUniqueReport.getReportingOrigin(), countUniqueReportJsonBody);

            // Code outside [200, 299] is a failure according to HTTP protocol.
            if (returnCode < HttpURLConnection.HTTP_OK || returnCode > MAX_HTTP_SUCCESS_CODE) {
                ReportUtil.logReportingFailure(
                        LOGGING_NAME,
                        String.format(
                                "Sending count unique report resulted in non-success HTTP status"
                                        + " code %s",
                                returnCode),
                        countUniqueReportId,
                        null,
                        ReportingStatus.ReportType.COUNT_UNIQUE.toString());
                // TODO(400528120): Add report status logging for unsuccessful HTTP response.
                return;
            }

            boolean success =
                    mDatastoreManager.runInTransaction(
                            (dao) ->
                                    dao.markCountUniqueReportStatus(
                                            countUniqueReportId,
                                            CountUniqueReport.ReportDeliveryStatus.DELIVERED));
            if (!success) {
                ReportUtil.logReportingFailure(
                        LOGGING_NAME,
                        "Updating count unique report status failed",
                        countUniqueReportId,
                        null,
                        ReportingStatus.ReportType.COUNT_UNIQUE.toString());
                // TODO(400528120): Add report status logging for failed report status update.
            }

            LoggerFactory.getMeasurementLogger()
                    .d(
                            "CountUniqueReportingJobHandler (SUCCESS): Count Unique report status"
                                    + " updated! Report ID: %s, Enrollment ID: %s, Type: %s",
                            countUniqueReportId,
                            null,
                            ReportingStatus.ReportType.COUNT_UNIQUE.toString());
            // TODO(400528120): Add report status logging for success.
        } catch (JSONException e) {
            // JSON Serialization error
            ReportUtil.logReportingFailure(
                    LOGGING_NAME,
                    "JSON serialization error occurred at count unique report delivery",
                    countUniqueReportId,
                    null, // TODO(404346883): Add enrollment Id to logs.
                    ReportingStatus.ReportType.COUNT_UNIQUE.toString());
            ErrorLogUtil.e(
                    AD_SERVICES_ERROR_REPORTED__ERROR_CODE__MEASUREMENT_REPORTING_PARSING_ERROR,
                    AD_SERVICES_ERROR_REPORTED__PPAPI_NAME__MEASUREMENT);
            // TODO(400528120): Add report status logging for JSON serialization error.
        } catch (IOException e) {
            // Network Error
            ReportUtil.logReportingFailure(
                    LOGGING_NAME,
                    "Network error occurred when attempting to deliver count unique report",
                    countUniqueReportId,
                    null, // TODO(404346883): Add enrollment Id to logs.
                    ReportingStatus.ReportType.COUNT_UNIQUE.toString());
            ErrorLogUtil.e(
                    AD_SERVICES_ERROR_REPORTED__ERROR_CODE__MEASUREMENT_REPORTING_NETWORK_ERROR,
                    AD_SERVICES_ERROR_REPORTED__PPAPI_NAME__MEASUREMENT);
            // TODO(400528120): Add report status logging for network error.
        } catch (CryptoException e) {
            // Encryption error
            ReportUtil.logReportingFailure(
                    LOGGING_NAME,
                    "Encryption error in count unique reporting",
                    countUniqueReportId,
                    null, // TODO(404346883): Add enrollment Id to logs.
                    ReportingStatus.ReportType.COUNT_UNIQUE.toString());
            ErrorLogUtil.e(
                    e,
                    AD_SERVICES_ERROR_REPORTED__ERROR_CODE__MEASUREMENT_REPORTING_ENCRYPTION_ERROR,
                    AD_SERVICES_ERROR_REPORTED__PPAPI_NAME__MEASUREMENT);
            // TODO(400528120): Add report status logging for encryption error.
        } catch (Exception e) {
            // Any other exception
            ReportUtil.logReportingFailure(
                    LOGGING_NAME,
                    "Unknown error in count unique reporting",
                    countUniqueReportId,
                    null, // TODO(404346883): Add enrollment Id to logs.
                    ReportingStatus.ReportType.COUNT_UNIQUE.toString());
            ErrorLogUtil.e(
                    e,
                    AD_SERVICES_ERROR_REPORTED__ERROR_CODE__MEASUREMENT_REPORTING_UNKNOWN_ERROR,
                    AD_SERVICES_ERROR_REPORTED__PPAPI_NAME__MEASUREMENT);
            // TODO(400528120): Add report status logging for generic error.
        }
    }

    /** Creates the JSON payload for the POST request from the CountUniqueReport. */
    @VisibleForTesting
    JSONObject createReportJsonPayload(
            CountUniqueReport countUniqueReport, AggregateEncryptionKey key) throws JSONException {
        return new CountUniqueReportBody.Builder()
                .setReportId(countUniqueReport.getReportId())
                .setReportingOrigin(countUniqueReport.getReportingOrigin())
                .setScheduledReportTime(countUniqueReport.getScheduledReportTime())
                .setDebugCleartextPayload(countUniqueReport.getPayload())
                .setApiVersion(countUniqueReport.getApiVersion())
                .setApi(API_NAME)
                .setDebugKey(countUniqueReport.getDebugKey())
                .setDebugMode(countUniqueReport.getDebugKey() != null)
                .setContextId(countUniqueReport.getContextId())
                .setAggregationCoordinatorOrigin(
                        Uri.parse(mFlags.getMeasurementDefaultAggregationCoordinatorOrigin()))
                .build()
                .toJson(key, mFlags);
    }

    /** Makes the POST request to the reporting URL. */
    @VisibleForTesting
    public int makeHttpPostRequest(Uri adTechDomain, JSONObject countUniqueReportBody)
            throws IOException {
        CountUniqueReportSender countUniqueReportSender =
                new CountUniqueReportSender(mIsDebugInstance, mContext);
        return countUniqueReportSender.sendReportWithHeaders(
                adTechDomain, countUniqueReportBody, null);
    }
}

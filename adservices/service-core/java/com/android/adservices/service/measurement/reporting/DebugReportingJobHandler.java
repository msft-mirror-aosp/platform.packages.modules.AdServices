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

import static com.android.adservices.service.stats.AdServicesStatsLog.AD_SERVICES_ERROR_REPORTED__ERROR_CODE__ERROR_CODE_UNSPECIFIED;
import static com.android.adservices.service.stats.AdServicesStatsLog.AD_SERVICES_ERROR_REPORTED__PPAPI_NAME__MEASUREMENT;

import android.adservices.common.AdServicesStatusUtils;
import android.net.Uri;

import com.android.adservices.LogUtil;
import com.android.adservices.data.enrollment.EnrollmentDao;
import com.android.adservices.data.measurement.DatastoreManager;
import com.android.adservices.data.measurement.IMeasurementDao;
import com.android.adservices.errorlogging.ErrorLogUtil;
import com.android.adservices.service.Flags;
import com.android.adservices.service.measurement.KeyValueData;
import com.android.internal.annotations.VisibleForTesting;

import org.json.JSONArray;
import org.json.JSONException;

import java.io.IOException;
import java.net.HttpURLConnection;
import java.util.List;
import java.util.Optional;
import java.util.concurrent.ThreadLocalRandom;

/** Class for handling debug reporting. */
public class DebugReportingJobHandler {

    private final EnrollmentDao mEnrollmentDao;
    private final DatastoreManager mDatastoreManager;
    private final Flags mFlags;

    DebugReportingJobHandler(
            EnrollmentDao enrollmentDao, DatastoreManager datastoreManager, Flags flags) {
        mEnrollmentDao = enrollmentDao;
        mDatastoreManager = datastoreManager;
        mFlags = flags;
    }

    /** Finds all debug reports and attempts to upload them individually. */
    void performScheduledPendingReports() {
        Optional<List<String>> pendingDebugReports =
                mDatastoreManager.runInTransactionWithResult(IMeasurementDao::getDebugReportIds);
        if (!pendingDebugReports.isPresent()) {
            LogUtil.d("Pending Debug Reports not found");
            return;
        }

        List<String> pendingDebugReportIdsInWindow = pendingDebugReports.get();
        for (String debugReportId : pendingDebugReportIdsInWindow) {
            // If the job service's requirements specified at runtime are no longer met, the job
            // service will interrupt this thread.  If the thread has been interrupted, it will exit
            // early.
            if (Thread.currentThread().isInterrupted()) {
                LogUtil.d(
                        "DebugReportingJobHandler performScheduledPendingReports "
                                + "thread interrupted, exiting early.");
                return;
            }

            @AdServicesStatusUtils.StatusCode int result = performReport(debugReportId);
            if (result != AdServicesStatusUtils.STATUS_SUCCESS) {
                mDatastoreManager.runInTransaction(
                        (dao) ->
                                dao.incrementReportingRetryCount(
                                        debugReportId,
                                        KeyValueData.DataType.DEBUG_REPORT_RETRY_COUNT));
            }
        }
    }

    /**
     * Perform reporting by finding the relevant {@link DebugReport} and making an HTTP POST request
     * to the specified report to URL with the report data as a JSON in the body.
     *
     * @param debugReportId for the datastore id of the {@link DebugReport}
     * @return success
     */
    int performReport(String debugReportId) {
        Optional<DebugReport> debugReportOpt =
                mDatastoreManager.runInTransactionWithResult(
                        (dao) -> dao.getDebugReport(debugReportId));
        if (!debugReportOpt.isPresent()) {
            LogUtil.d("Reading Scheduled Debug Report failed");
            return AdServicesStatusUtils.STATUS_IO_ERROR;
        }
        DebugReport debugReport = debugReportOpt.get();

        try {
            Uri reportingOrigin = debugReport.getRegistrationOrigin();
            JSONArray debugReportJsonPayload = createReportJsonPayload(debugReport);
            int returnCode = makeHttpPostRequest(reportingOrigin, debugReportJsonPayload);

            if (returnCode >= HttpURLConnection.HTTP_OK && returnCode <= 299) {
                boolean success =
                        mDatastoreManager.runInTransaction(
                                (dao) -> {
                                    dao.deleteDebugReport(debugReport.getId());
                                });
                if (success) {
                    return AdServicesStatusUtils.STATUS_SUCCESS;
                } else {
                    LogUtil.d("Deleting debug report failed");
                    return AdServicesStatusUtils.STATUS_IO_ERROR;
                }
            } else {
                LogUtil.d("Sending debug report failed with http error");
                return AdServicesStatusUtils.STATUS_IO_ERROR;
            }
        } catch (IOException e) {
            LogUtil.d(e, "Network error occurred when attempting to deliver debug report.");
            ErrorLogUtil.e(
                    e,
                    AD_SERVICES_ERROR_REPORTED__ERROR_CODE__ERROR_CODE_UNSPECIFIED,
                    AD_SERVICES_ERROR_REPORTED__PPAPI_NAME__MEASUREMENT);
            // TODO(b/298330312): Change to defined error codes
            return AdServicesStatusUtils.STATUS_IO_ERROR;
        } catch (JSONException e) {
            LogUtil.d(e, "Serialization error occurred at debug report delivery.");
            // TODO(b/298330312): Change to defined error codes
            ErrorLogUtil.e(
                    e,
                    AD_SERVICES_ERROR_REPORTED__ERROR_CODE__ERROR_CODE_UNSPECIFIED,
                    AD_SERVICES_ERROR_REPORTED__PPAPI_NAME__MEASUREMENT);
            if (mFlags.getMeasurementEnableReportDeletionOnUnrecoverableException()) {
                // Unrecoverable state - delete the report.
                mDatastoreManager.runInTransaction(dao -> dao.deleteDebugReport(debugReportId));
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
            LogUtil.e(e, "Unexpected exception occurred when attempting to deliver debug report.");
            // TODO(b/298330312): Change to defined error codes
            ErrorLogUtil.e(
                    e,
                    AD_SERVICES_ERROR_REPORTED__ERROR_CODE__ERROR_CODE_UNSPECIFIED,
                    AD_SERVICES_ERROR_REPORTED__PPAPI_NAME__MEASUREMENT);
            if (mFlags.getMeasurementEnableReportingJobsThrowUnaccountedException()
                    && ThreadLocalRandom.current().nextFloat()
                            < mFlags.getMeasurementThrowUnknownExceptionSamplingRate()) {
                throw e;
            }
            return AdServicesStatusUtils.STATUS_UNKNOWN_ERROR;
        }
    }

    /** Creates the JSON payload for the POST request from the DebugReport. */
    @VisibleForTesting
    JSONArray createReportJsonPayload(DebugReport debugReport) throws JSONException {
        JSONArray debugReportJsonPayload = new JSONArray();
        debugReportJsonPayload.put(debugReport.toPayloadJson());
        return debugReportJsonPayload;
    }

    /** Makes the POST request to the reporting URL. */
    @VisibleForTesting
    public int makeHttpPostRequest(Uri adTechDomain, JSONArray debugReportPayload)
            throws IOException {
        DebugReportSender debugReportSender = new DebugReportSender();
        return debugReportSender.sendReport(adTechDomain, debugReportPayload);
    }
}

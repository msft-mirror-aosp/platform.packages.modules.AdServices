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
import com.android.adservices.data.measurement.IMeasurementDao;
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

/** Class for handling debug reporting. */
public class DebugReportingJobHandler {

    private final EnrollmentDao mEnrollmentDao;
    private final DatastoreManager mDatastoreManager;

    DebugReportingJobHandler(EnrollmentDao enrollmentDao, DatastoreManager datastoreManager) {
        mEnrollmentDao = enrollmentDao;
        mDatastoreManager = datastoreManager;
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
            @AdServicesStatusUtils.StatusCode int result = performReport(debugReportId);
            logReportingStats(result);
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
            Optional<Uri> reportingOrigin =
                    Enrollment.maybeGetReportingOrigin(
                            debugReport.getEnrollmentId(), mEnrollmentDao);
            if (!reportingOrigin.isPresent()) {
                LogUtil.d("Reading Enrollment failed");
                return AdServicesStatusUtils.STATUS_INTERNAL_ERROR;
            }
            JSONObject debugReportJsonPayload = createReportJsonPayload(debugReport);
            int returnCode = makeHttpPostRequest(reportingOrigin.get(), debugReportJsonPayload);

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
        } catch (Exception e) {
            LogUtil.e(e, "Sending debug report error");
            return AdServicesStatusUtils.STATUS_UNKNOWN_ERROR;
        }
    }

    /** Creates the JSON payload for the POST request from the DebugReport. */
    @VisibleForTesting
    JSONObject createReportJsonPayload(DebugReport debugReport) throws JSONException {
        return debugReport.toPayloadJson();
    }

    /** Makes the POST request to the reporting URL. */
    @VisibleForTesting
    public int makeHttpPostRequest(Uri adTechDomain, JSONObject debugReportPayload)
            throws IOException {
        DebugReportSender debugReportSender = new DebugReportSender();
        return debugReportSender.sendReport(adTechDomain, debugReportPayload);
    }

    private static void logReportingStats(int resultCode) {
        AdServicesLoggerImpl.getInstance()
                .logMeasurementReports(
                        new MeasurementReportsStats.Builder()
                                .setCode(AD_SERVICES_MESUREMENT_REPORTS_UPLOADED)
                                .setType(AD_SERVICES_MEASUREMENT_REPORTS_UPLOADED__TYPE__EVENT)
                                .setResultCode(resultCode)
                                .build());
    }
}

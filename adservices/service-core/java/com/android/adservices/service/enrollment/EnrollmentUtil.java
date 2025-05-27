/*
 * Copyright (C) 2023 The Android Open Source Project
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

package com.android.adservices.service.enrollment;

import android.content.Context;
import android.content.SharedPreferences;

import com.android.adservices.data.configdelivery.ArgonConfigurationManager;
import com.android.adservices.data.configdelivery.Configuration;
import com.android.adservices.service.FlagsFactory;
import com.android.adservices.service.proto.RbEnrollment;
import com.android.adservices.service.proto.config_delivery.ConfigurationType;
import com.android.adservices.service.stats.AdServicesEnrollmentTransactionStats;
import com.android.adservices.service.stats.AdServicesLogger;
import com.android.adservices.shared.common.ApplicationContextSingleton;
import com.android.internal.annotations.VisibleForTesting;

/** Util class for all enrollment-related classes */
public class EnrollmentUtil {
    private static EnrollmentUtil sSingleton;
    private final Context mContext;

    @VisibleForTesting public static final String ENROLLMENT_SHARED_PREF = "adservices_enrollment";
    @VisibleForTesting public static final String BUILD_ID = "build_id";
    @VisibleForTesting public static final String FILE_GROUP_STATUS = "file_group_status";

    private EnrollmentUtil(Context context) {
        mContext = context;
    }

    /** Returns an instance of the EnrollmentUtil. */
    public static EnrollmentUtil getInstance() {
        synchronized (EnrollmentUtil.class) {
            if (sSingleton == null) {
                sSingleton = new EnrollmentUtil(ApplicationContextSingleton.get());
            }
            return sSingleton;
        }
    }

    /**
     * Get build ID.
     *
     * <p>If V3 enrollment is enabled, get the latest config version from {@link
     * ArgonConfigurationManager}. Otherwise, get the MDD build ID from shared preferences.
     */
    public int getBuildId() {
        // DIDN'T FIX CustomAudienceManagerTest
        if (FlagsFactory.getFlags().getConfigDeliveryUseArgonConfigManagerToQueryEnrollment()
                && FlagsFactory.getFlags()
                        .getConfigDeliveryEnableEnrollmentConfigV3DataDownload()) {
            return ArgonConfigurationManager.getInstance(
                            ConfigurationType.TYPE_RB_ENROLLMENT,
                            ArgonConfigurationManager.DataConsistencyStrategy.USE_LATEST_VERSION)
                    .getLatestVersion()
                    .intValue();
        }
        SharedPreferences prefs = getPrefs();
        return prefs.getInt(BUILD_ID, /* defaultValue */ -1);
    }

    /** Get file group status from shared preference */
    public int getFileGroupStatus() {
        SharedPreferences prefs = getPrefs();
        return prefs.getInt(FILE_GROUP_STATUS, /* defaultValue */ 0);
    }

    private int convertBuildIdStringToInt(String buildIdString) {
        if (buildIdString == null) {
            return -1;
        }
        try {
            int buildId = Integer.parseInt(buildIdString);
            return buildId;
        } catch (NumberFormatException e) {
            return -1;
        }
    }

    private String getQueryParameterValue(String queryParameter) {
        if (queryParameter == null) {
            return "";
        }
        return queryParameter;
    }

    public static EnrollmentData toEnrollmentData(Configuration configuration) {
        if (configuration == null) {
            return null;
        }
        RbEnrollment enrollment = configuration.getValue(RbEnrollment.getDefaultInstance());
        if (enrollment == null) {
            return null;
        }
        return new EnrollmentData.Builder()
                .setEnrollmentId(configuration.getId())
                .setEnrolledAPIs(enrollment.getEnrolledApisList())
                .setSdkNames(enrollment.getSdkNamesList())
                .setAttributionSourceRegistrationUrl(enrollment.getEnrolledSite())
                .setAttributionTriggerRegistrationUrl(enrollment.getEnrolledSite())
                .setAttributionReportingUrl(enrollment.getEnrolledSite())
                .setRemarketingResponseBasedRegistrationUrl(enrollment.getEnrolledSite())
                .setEncryptionKeyUrl(enrollment.getEnrolledSite())
                .setEnrolledSite(enrollment.getEnrolledSite())
                .build();
    }

    /** Log EnrollmentData atom metrics for enrollment database transactions */
    public void logEnrollmentDataStats(
            AdServicesLogger logger, int type, boolean isSuccessful, Integer buildId) {
        logger.logEnrollmentDataStats(type, isSuccessful, buildId);
    }

    /** Log EnrollmentFileDownload atom metrics for enrollment data downloading */
    public void logEnrollmentFileDownloadStats(
            AdServicesLogger logger, boolean isSuccessful, String buildIdString) {
        logger.logEnrollmentFileDownloadStats(
                isSuccessful, convertBuildIdStringToInt(buildIdString));
    }

    /** Log EnrollmentData atom metrics for enrollment database queries */
    public void logEnrollmentMatchStats(
            AdServicesLogger logger, boolean isSuccessful, Integer buildId) {
        logger.logEnrollmentMatchStats(isSuccessful, buildId);
    }

    /** Log EnrollmentFailed atom metrics for enrollment-related status_caller_not_found errors */
    public void logEnrollmentFailedStats(
            AdServicesLogger logger,
            Integer buildId,
            Integer dataFileGroupStatus,
            int enrollmentRecordCount,
            String queryParameter,
            int errorCause) {
        logger.logEnrollmentFailedStats(
                buildId,
                dataFileGroupStatus,
                enrollmentRecordCount,
                getQueryParameterValue(queryParameter),
                errorCause);
    }

    /** Helper method to initialize transaction stats */
    public AdServicesEnrollmentTransactionStats.Builder initTransactionStatsBuilder(
            AdServicesEnrollmentTransactionStats.TransactionType type,
            int transactionParameterCount,
            int dataSourceRecordCount) {

        return AdServicesEnrollmentTransactionStats.builder()
                .setTransactionType(type)
                .setTransactionParameterCount(transactionParameterCount)
                .setDataSourceRecordCountPre(dataSourceRecordCount);
    }

    /** Log EnrollmentTransactionStats atom metrics for enrollment-related tracking */
    public void logTransactionStats(
            AdServicesLogger logger,
            AdServicesEnrollmentTransactionStats.Builder statsBuilder,
            AdServicesEnrollmentTransactionStats.TransactionStatus status,
            int queryResultCount,
            int transactionResultCount,
            int dataSourceRecordCount,
            int latencyMs) {
        statsBuilder.setDataSourceRecordCountPost(dataSourceRecordCount);
        logger.logEnrollmentTransactionStats(
                statsBuilder
                        .setTransactionStatus(status)
                        .setEnrollmentFileBuildId(getBuildId())
                        .setQueryResultCount(queryResultCount)
                        .setTransactionResultCount(transactionResultCount)
                        .setLatencyMs(latencyMs)
                        .build());
    }

    /**
     * Log EnrollmentTransactionStats atom metrics for enrollment-related tracking where no result
     * is expected
     */
    public void logTransactionStatsNoResult(
            AdServicesLogger logger,
            AdServicesEnrollmentTransactionStats.Builder statsBuilder,
            AdServicesEnrollmentTransactionStats.TransactionStatus status,
            int dataSourceRecordCount,
            int latencyMs) {
        logTransactionStats(
                logger,
                statsBuilder,
                status,
                /* queryResultCount= */ 0,
                /* transactionResultCount= */ 0,
                dataSourceRecordCount,
                latencyMs);
    }

    public void logTransactionStats(
            AdServicesLogger logger,
            AdServicesEnrollmentTransactionStats.Builder statsBuilder,
            int queryResultCount,
            int transactionResultCount,
            int dataSourceRecordCount,
            int latencyMs) {
        if (queryResultCount == 0) {
            logTransactionStatsNoResult(
                    logger,
                    statsBuilder,
                    AdServicesEnrollmentTransactionStats.TransactionStatus.MATCH_NOT_FOUND,
                    dataSourceRecordCount,
                    latencyMs);
            return;
        }

        logTransactionStats(
                logger,
                statsBuilder,
                AdServicesEnrollmentTransactionStats.TransactionStatus.SUCCESS,
                queryResultCount,
                transactionResultCount,
                dataSourceRecordCount,
                latencyMs);
    }

    @SuppressWarnings("AvoidSharedPreferences") // Legacy usage
    private SharedPreferences getPrefs() {
        return mContext.getSharedPreferences(ENROLLMENT_SHARED_PREF, Context.MODE_PRIVATE);
    }
}

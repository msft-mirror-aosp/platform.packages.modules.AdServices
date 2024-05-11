/*
 * Copyright (C) 2024 The Android Open Source Project
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

package com.android.adservices.shared.spe;

import static com.android.adservices.service.stats.AdServicesStatsLog.AD_SERVICES_BACKGROUND_JOBS_EXECUTION_REPORTED__EXECUTION_RESULT_CODE__SKIP_FOR_EXTSERVICES_JOB_ON_TPLUS;
import static com.android.adservices.service.stats.AdServicesStatsLog.AD_SERVICES_BACKGROUND_JOBS_EXECUTION_REPORTED__EXECUTION_RESULT_CODE__SKIP_FOR_JOB_NOT_CONFIGURED;
import static com.android.adservices.service.stats.AdServicesStatsLog.AD_SERVICES_BACKGROUND_JOBS_EXECUTION_REPORTED__EXECUTION_RESULT_CODE__SKIP_FOR_KILL_SWITCH_ON;
import static com.android.adservices.service.stats.AdServicesStatsLog.AD_SERVICES_BACKGROUND_JOBS_EXECUTION_REPORTED__EXECUTION_RESULT_CODE__SKIP_FOR_USER_CONSENT_REVOKED;
import static com.android.adservices.service.stats.AdServicesStatsLog.AD_SERVICES_ERROR_REPORTED__ERROR_CODE__SPE_JOB_SCHEDULER_IS_UNAVAILABLE;
import static com.android.adservices.service.stats.AdServicesStatsLog.BACKGROUND_JOB_SCHEDULING_REPORTED__MODULE_NAME__UNKNOWN_MODULE_NAME;
import static com.android.adservices.service.stats.AdServicesStatsLog.BACKGROUND_JOB_SCHEDULING_REPORTED__RESULT_CODE__SCHEDULING_RESULT_CODE_FAILED;
import static com.android.adservices.service.stats.AdServicesStatsLog.BACKGROUND_JOB_SCHEDULING_REPORTED__RESULT_CODE__SCHEDULING_RESULT_CODE_SKIPPED;
import static com.android.adservices.service.stats.AdServicesStatsLog.BACKGROUND_JOB_SCHEDULING_REPORTED__RESULT_CODE__SCHEDULING_RESULT_CODE_SUCCESSFUL;
import static com.android.adservices.service.stats.AdServicesStatsLog.BACKGROUND_JOB_SCHEDULING_REPORTED__SCHEDULER_TYPE__SCHEDULER_TYPE_JOB_SCHEDULER;
import static com.android.adservices.service.stats.AdServicesStatsLog.BACKGROUND_JOB_SCHEDULING_REPORTED__SCHEDULER_TYPE__SCHEDULER_TYPE_SPE;

import android.annotation.IntDef;
import android.app.job.JobParameters;

import java.lang.annotation.Retention;
import java.lang.annotation.RetentionPolicy;

/** Class to store constants used by background jobs. */
public final class JobServiceConstants {
    /**
     * Unavailable stop reason, used when {@link
     * android.app.job.JobService#onStopJob(JobParameters)} is not invoked in an execution.
     *
     * <p>Use the value of {@link JobParameters#STOP_REASON_UNDEFINED} in case API version is lower
     * than S.
     */
    public static final int UNAVAILABLE_STOP_REASON = 0;

    /** The shared preference file name for background jobs */
    public static final String SHARED_PREFS_BACKGROUND_JOBS = "PPAPI_Background_Jobs";

    /** The suffix to compose the key to store job start timestamp */
    public static final String SHARED_PREFS_START_TIMESTAMP_SUFFIX = "_job_start_timestamp";

    /** The suffix to compose the key to store job stop timestamp */
    public static final String SHARED_PREFS_STOP_TIMESTAMP_SUFFIX = "_job_stop_timestamp";

    /** The suffix to compose the key to store job execution period */
    public static final String SHARED_PREFS_EXEC_PERIOD_SUFFIX = "_job_execution_period";

    /** A utility string key that should NEVER be fetched */
    public static final String UNAVAILABLE_KEY = "unavailableKey";

    /**
     * Value of the execution start timestamp when it's unavailable to achieve. For example, the
     * shared preference key doesn't exist.
     */
    public static final long UNAVAILABLE_JOB_EXECUTION_START_TIMESTAMP = -1L;

    /**
     * Value of the execution stop timestamp when it's unavailable to achieve. For example, the
     * shared preference key doesn't exist.
     */
    public static final long UNAVAILABLE_JOB_EXECUTION_STOP_TIMESTAMP = -1L;

    /**
     * Value of the execution period when it's unavailable to achieve, such as in the first
     * execution.
     */
    public static final long UNAVAILABLE_JOB_EXECUTION_PERIOD = -1L;

    /**
     * Value of the execution latency if it cannot be computed, such as an open-end execution caused
     * by system or device issue.
     */
    public static final long UNAVAILABLE_JOB_LATENCY = -1L;

    /** The number of milliseconds per minute. */
    public static final int MILLISECONDS_PER_MINUTE = 60 * 1000;

    /** Maximum possible percentage for percentage variables. */
    public static final int MAX_PERCENTAGE = 100;

    /** A shorter version of error code for job scheduler is not available. */
    public static final int ERROR_CODE_JOB_SCHEDULER_IS_UNAVAILABLE =
            AD_SERVICES_ERROR_REPORTED__ERROR_CODE__SPE_JOB_SCHEDULER_IS_UNAVAILABLE;

    @IntDef(
            value = {
                JOB_ENABLED_STATUS_ENABLED,
                JOB_ENABLED_STATUS_DISABLED_FOR_KILL_SWITCH_ON,
                JOB_ENABLED_STATUS_DISABLED_FOR_USER_CONSENT_REVOKED,
                JOB_ENABLED_STATUS_DISABLED_FOR_BACK_COMPAT_OTA,
            })
    @Retention(RetentionPolicy.SOURCE)
    public @interface JobEnablementStatus {}

    /** The job is enabled. */
    public static final int JOB_ENABLED_STATUS_ENABLED = -1;

    /** The job is disabled due to kill switch is ON. */
    public static final int JOB_ENABLED_STATUS_DISABLED_FOR_KILL_SWITCH_ON =
            AD_SERVICES_BACKGROUND_JOBS_EXECUTION_REPORTED__EXECUTION_RESULT_CODE__SKIP_FOR_KILL_SWITCH_ON;

    /** The job is disabled due to user consent is revoked. */
    public static final int JOB_ENABLED_STATUS_DISABLED_FOR_USER_CONSENT_REVOKED =
            AD_SERVICES_BACKGROUND_JOBS_EXECUTION_REPORTED__EXECUTION_RESULT_CODE__SKIP_FOR_USER_CONSENT_REVOKED;

    /** The job is disabled due to ExtServices' job running on AdServices */
    public static final int JOB_ENABLED_STATUS_DISABLED_FOR_BACK_COMPAT_OTA =
            AD_SERVICES_BACKGROUND_JOBS_EXECUTION_REPORTED__EXECUTION_RESULT_CODE__SKIP_FOR_EXTSERVICES_JOB_ON_TPLUS;

    /** The job is disabled due to misconfiguration in SPE framework. */
    public static final int SKIP_REASON_JOB_NOT_CONFIGURED =
            AD_SERVICES_BACKGROUND_JOBS_EXECUTION_REPORTED__EXECUTION_RESULT_CODE__SKIP_FOR_JOB_NOT_CONFIGURED;

    /** Shorter version for scheduler type - SPE. */
    public static final int SCHEDULER_TYPE_SPE =
            BACKGROUND_JOB_SCHEDULING_REPORTED__SCHEDULER_TYPE__SCHEDULER_TYPE_SPE;

    /** Shorter version for scheduler type - Job Scheduler. */
    public static final int SCHEDULER_TYPE_JOB_SCHEDULER =
            BACKGROUND_JOB_SCHEDULING_REPORTED__SCHEDULER_TYPE__SCHEDULER_TYPE_JOB_SCHEDULER;

    /** Shorter version for unknown module name. */
    public static final int EXECUTION_LOGGING_UNKNOWN_MODULE_NAME =
            BACKGROUND_JOB_SCHEDULING_REPORTED__MODULE_NAME__UNKNOWN_MODULE_NAME;

    /** Shorter version for unknown module name. */
    public static final int SCHEDULING_LOGGING_UNKNOWN_MODULE_NAME =
            BACKGROUND_JOB_SCHEDULING_REPORTED__MODULE_NAME__UNKNOWN_MODULE_NAME;

    @IntDef(
            value = {
                SCHEDULING_RESULT_CODE_SUCCESSFUL,
                SCHEDULING_RESULT_CODE_FAILED,
                SCHEDULING_RESULT_CODE_SKIPPED,
            })
    @Retention(RetentionPolicy.SOURCE)
    public @interface JobSchedulingResultCode {}

    public static final int SCHEDULING_RESULT_CODE_SUCCESSFUL =
            BACKGROUND_JOB_SCHEDULING_REPORTED__RESULT_CODE__SCHEDULING_RESULT_CODE_SUCCESSFUL;

    public static final int SCHEDULING_RESULT_CODE_FAILED =
            BACKGROUND_JOB_SCHEDULING_REPORTED__RESULT_CODE__SCHEDULING_RESULT_CODE_FAILED;

    public static final int SCHEDULING_RESULT_CODE_SKIPPED =
            BACKGROUND_JOB_SCHEDULING_REPORTED__RESULT_CODE__SCHEDULING_RESULT_CODE_SKIPPED;
}

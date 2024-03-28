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

package com.android.adservices.spe;

import static com.android.adservices.service.stats.AdServicesStatsLog.AD_SERVICES_ERROR_REPORTED__ERROR_CODE__SPE_JOB_NOT_CONFIGURED_CORRECTLY;
import static com.android.adservices.service.stats.AdServicesStatsLog.AD_SERVICES_ERROR_REPORTED__PPAPI_NAME__COMMON;

import android.os.Build;

import androidx.annotation.RequiresApi;

import com.android.adservices.concurrency.AdServicesExecutors;
import com.android.adservices.download.MddJob;
import com.android.adservices.errorlogging.AdServicesErrorLoggerImpl;
import com.android.adservices.service.Flags;
import com.android.adservices.service.FlagsFactory;
import com.android.adservices.shared.common.ApplicationContextSingleton;
import com.android.adservices.shared.common.flags.ModuleSharedFlags;
import com.android.adservices.shared.errorlogging.AdServicesErrorLogger;
import com.android.adservices.shared.proto.ModuleJobPolicy;
import com.android.adservices.shared.proto.ProtoParser;
import com.android.adservices.shared.spe.framework.JobServiceFactory;
import com.android.adservices.shared.spe.framework.JobWorker;
import com.android.adservices.shared.spe.logging.JobServiceLogger;
import com.android.adservices.shared.util.LogUtil;
import com.android.internal.annotations.GuardedBy;
import com.android.internal.annotations.VisibleForTesting;

import java.util.Map;
import java.util.concurrent.Executor;

/** The AdServices' implementation of {@link JobServiceFactory}. */
@RequiresApi(Build.VERSION_CODES.S)
public final class AdServicesJobServiceFactory implements JobServiceFactory {
    private static final String PROTO_PROPERTY_FOR_LOGCAT = "AdServicesModuleJobPolicy";
    private static final Object SINGLETON_LOCK = new Object();

    @GuardedBy("SINGLETON_LOCK")
    private static volatile AdServicesJobServiceFactory sSingleton;

    private final ModuleJobPolicy mModuleJobPolicy;
    private final Flags mFlags;
    private final AdServicesErrorLogger mErrorLogger;
    private final Executor mExecutor;
    private final JobServiceLogger mJobServiceLogger;
    private final Map<Integer, String> mJobIdTojobNameMap;

    @VisibleForTesting
    public AdServicesJobServiceFactory(
            JobServiceLogger jobServiceLogger,
            ModuleJobPolicy moduleJobPolicy,
            AdServicesErrorLogger errorLogger,
            Map<Integer, String> jobIdTojobNameMap,
            Executor executor,
            Flags flags) {
        mJobServiceLogger = jobServiceLogger;
        mModuleJobPolicy = moduleJobPolicy;
        mErrorLogger = errorLogger;
        mJobIdTojobNameMap = jobIdTojobNameMap;
        mExecutor = executor;
        mFlags = flags;
    }

    /** Gets a singleton instance of {@link AdServicesJobServiceFactory}. */
    public static AdServicesJobServiceFactory getInstance() {
        synchronized (SINGLETON_LOCK) {
            if (sSingleton == null) {
                Flags flags = FlagsFactory.getFlags();

                ModuleJobPolicy policy =
                        ProtoParser.parseBase64EncodedStringToProto(
                                ModuleJobPolicy.parser(),
                                PROTO_PROPERTY_FOR_LOGCAT,
                                flags.getAdServicesModuleJobPolicy());
                sSingleton =
                        new AdServicesJobServiceFactory(
                                AdServicesJobServiceLogger.getInstance(
                                        ApplicationContextSingleton.get()),
                                policy,
                                AdServicesErrorLoggerImpl.getInstance(),
                                AdServicesJobInfo.getJobIdToJobNameMap(),
                                AdServicesExecutors.getBackgroundExecutor(),
                                flags);
            }

            return sSingleton;
        }
    }

    @Override
    public JobWorker getJobWorkerInstance(int jobId) {
        AdServicesJobInfo jobInfo = AdServicesJobInfo.getJobIdToJobInfoMap().get(jobId);
        try {
            switch (jobInfo) {
                case MDD_MAINTENANCE_PERIODIC_TASK_JOB:
                case MDD_CHARGING_PERIODIC_TASK_JOB:
                case MDD_CELLULAR_CHARGING_PERIODIC_TASK_JOB:
                case MDD_WIFI_CHARGING_PERIODIC_TASK_JOB:
                    return MddJob.getInstance();
                default:
                    throw new RuntimeException(
                            "The job isn't configured for jobWorker creation. Requested Job ID: "
                                    + jobId);
            }
        } catch (Exception e) {
            LogUtil.e(e, "Creation of Adservices' Job Instance is failed for jobId = %d.", jobId);
            mErrorLogger.logError(
                    AD_SERVICES_ERROR_REPORTED__ERROR_CODE__SPE_JOB_NOT_CONFIGURED_CORRECTLY,
                    AD_SERVICES_ERROR_REPORTED__PPAPI_NAME__COMMON);
        }

        return null;
    }

    @Override
    public Map<Integer, String> getJobIdToNameMap() {
        return mJobIdTojobNameMap;
    }

    @Override
    public JobServiceLogger getJobServiceLogger() {
        return mJobServiceLogger;
    }

    @Override
    public AdServicesErrorLogger getErrorLogger() {
        return mErrorLogger;
    }

    @Override
    public Executor getBackgroundExecutor() {
        return mExecutor;
    }

    @Override
    public ModuleJobPolicy getModuleJobPolicy() {
        return mModuleJobPolicy;
    }

    @Override
    public ModuleSharedFlags getFlags() {
        return mFlags;
    }

    /**
     * Reschedules the corresponding background job using the legacy(non-SPE) scheduling method.
     *
     * <p>Used by {@link AdServicesJobService} for a job scheduled by SPE (when migrating the job to
     * using SPE framework).
     *
     * @param jobId the unique job ID for the background job to reschedule.
     */
    public void rescheduleJobWithLegacyMethod(int jobId) {
        AdServicesJobInfo jobInfo = AdServicesJobInfo.getJobIdToJobInfoMap().get(jobId);

        try {
            switch (jobInfo) {
                case MDD_MAINTENANCE_PERIODIC_TASK_JOB:
                case MDD_CHARGING_PERIODIC_TASK_JOB:
                case MDD_CELLULAR_CHARGING_PERIODIC_TASK_JOB:
                case MDD_WIFI_CHARGING_PERIODIC_TASK_JOB:
                    MddJob.scheduleAllMddJobs();
                    return;
                default:
                    throw new RuntimeException(
                            "The job isn't configured for jobWorker creation. Requested Job ID: "
                                    + jobId);
            }
        } catch (Exception e) {
            LogUtil.e(
                    e,
                    "Rescheduling the job using the legacy JobService is failed for jobId = %d.",
                    jobId);
            mErrorLogger.logError(
                    AD_SERVICES_ERROR_REPORTED__ERROR_CODE__SPE_JOB_NOT_CONFIGURED_CORRECTLY,
                    AD_SERVICES_ERROR_REPORTED__PPAPI_NAME__COMMON);
        }
    }
}

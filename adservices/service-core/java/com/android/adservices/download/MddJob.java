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

package com.android.adservices.download;

import static com.android.adservices.download.MddJob.NetworkState.NETWORK_STATE_ANY;
import static com.android.adservices.download.MddJob.NetworkState.NETWORK_STATE_CONNECTED;
import static com.android.adservices.download.MddJob.NetworkState.NETWORK_STATE_UNMETERED;
import static com.android.adservices.shared.proto.JobPolicy.BatteryType.BATTERY_TYPE_REQUIRE_CHARGING;
import static com.android.adservices.shared.spe.JobServiceConstants.JOB_ENABLED_STATUS_DISABLED_FOR_KILL_SWITCH_ON;
import static com.android.adservices.shared.spe.JobServiceConstants.JOB_ENABLED_STATUS_ENABLED;
import static com.android.adservices.shared.spe.framework.ExecutionResult.SUCCESS;
import static com.android.adservices.spe.AdServicesJobInfo.MDD_CELLULAR_CHARGING_PERIODIC_TASK_JOB;
import static com.android.adservices.spe.AdServicesJobInfo.MDD_CHARGING_PERIODIC_TASK_JOB;
import static com.android.adservices.spe.AdServicesJobInfo.MDD_MAINTENANCE_PERIODIC_TASK_JOB;
import static com.android.adservices.spe.AdServicesJobInfo.MDD_WIFI_CHARGING_PERIODIC_TASK_JOB;

import android.app.job.JobScheduler;
import android.content.Context;
import android.os.Build;
import android.os.PersistableBundle;

import androidx.annotation.RequiresApi;

import com.android.adservices.LogUtil;
import com.android.adservices.concurrency.AdServicesExecutors;
import com.android.adservices.download.EnrollmentDataDownloadManager.DownloadStatus;
import com.android.adservices.service.FlagsFactory;
import com.android.adservices.service.common.AdPackageDenyResolver;
import com.android.adservices.shared.proto.JobPolicy;
import com.android.adservices.shared.proto.JobPolicy.NetworkType;
import com.android.adservices.shared.spe.JobServiceConstants.JobSchedulingResultCode;
import com.android.adservices.shared.spe.framework.ExecutionResult;
import com.android.adservices.shared.spe.framework.ExecutionRuntimeParameters;
import com.android.adservices.shared.spe.framework.JobWorker;
import com.android.adservices.shared.spe.logging.JobSchedulingLogger;
import com.android.adservices.shared.spe.scheduling.JobSpec;
import com.android.adservices.spe.AdServicesJobScheduler;
import com.android.adservices.spe.AdServicesJobServiceFactory;
import com.android.internal.annotations.VisibleForTesting;

import com.google.android.libraries.mobiledatadownload.Flags;
import com.google.android.libraries.mobiledatadownload.tracing.PropagatedFutures;
import com.google.common.util.concurrent.FluentFuture;
import com.google.common.util.concurrent.ListenableFuture;

// TODO(b/331291972): Refactor this class.
@RequiresApi(Build.VERSION_CODES.S)
public final class MddJob implements JobWorker {
    /**
     * Tag for daily mdd maintenance task, that *should* be run once and only once every 24 hours.
     *
     * <p>By default, this task runs on charging.
     */
    @VisibleForTesting
    static final String MAINTENANCE_PERIODIC_TASK = "MDD.MAINTENANCE.PERIODIC.GCM.TASK";

    /**
     * Tag for mdd task that doesn't require any network. This is used to perform some routine
     * operation that do not require network, in case a device doesn't connect to any network for a
     * long time.
     *
     * <p>By default, this task runs on charging once every 6 hours.
     */
    @VisibleForTesting static final String CHARGING_PERIODIC_TASK = "MDD.CHARGING.PERIODIC.TASK";

    /**
     * Tag for mdd task that runs on cellular network. This is used to primarily download file
     * groups that can be downloaded on cellular network.
     *
     * <p>By default, this task runs on charging once every 6 hours. This task can be skipped if
     * nothing is downloaded on cellular.
     */
    @VisibleForTesting
    static final String CELLULAR_CHARGING_PERIODIC_TASK = "MDD.CELLULAR.CHARGING.PERIODIC.TASK";

    /**
     * Tag for mdd task that runs on Wi-Fi network. This is used to primarily download file groups
     * that can be downloaded only on Wi-Fi network.
     *
     * <p>By default, this task runs on charging once every 6 hours. This task can be skipped if
     * nothing is restricted to Wi-Fi.
     */
    @VisibleForTesting
    static final String WIFI_CHARGING_PERIODIC_TASK = "MDD.WIFI.CHARGING.PERIODIC.TASK";

    @VisibleForTesting static final int MILLISECONDS_PER_SECOND = 1_000;

    @VisibleForTesting static final String KEY_MDD_TASK_TAG = "mdd_task_tag";

    @VisibleForTesting
    // Required network state of the device when to run the task.
    enum NetworkState {
        // Metered or unmetered network available.
        NETWORK_STATE_CONNECTED,

        // Unmetered network available.
        NETWORK_STATE_UNMETERED,

        // Network not required.
        NETWORK_STATE_ANY,
    }

    @Override
    public int getJobEnablementStatus() {
        if (FlagsFactory.getFlags().getMddBackgroundTaskKillSwitch()) {
            LogUtil.d("MDD background task is disabled, skipping and cancelling MddJobService");
            return JOB_ENABLED_STATUS_DISABLED_FOR_KILL_SWITCH_ON;
        }
        return JOB_ENABLED_STATUS_ENABLED;
    }

    @Override
    public ListenableFuture<ExecutionResult> getExecutionFuture(
            Context context, ExecutionRuntimeParameters params) {
        ListenableFuture<Void> handleTaskFuture =
                PropagatedFutures.submitAsync(
                        () -> {
                            String mddTag = getMddTag(params);

                            return MobileDataDownloadFactory.getMdd(FlagsFactory.getFlags())
                                    .handleTask(mddTag);
                        },
                        AdServicesExecutors.getBackgroundExecutor());

        return FluentFuture.from(handleTaskFuture)
                .transform(
                        ignoredVoid -> {
                            // TODO(b/331285831): Handle unused return value.
                            // To suppress the lint error of future returning value is unused.
                            ListenableFuture<DownloadStatus> unusedFutureEnrollment =
                                    EnrollmentDataDownloadManager.getInstance()
                                            .readAndInsertEnrollmentDataFromMdd();
                            ListenableFuture<EncryptionDataDownloadManager.DownloadStatus>
                                    unusedFutureEncryption =
                                            EncryptionDataDownloadManager.getInstance()
                                                    .readAndInsertEncryptionDataFromMdd();
                            if (FlagsFactory.getFlags().getEnablePackageDenyJobOnMddDownload()) {
                                ListenableFuture<AdPackageDenyResolver.PackageDenyMddProcessStatus>
                                        unusedFuturePackageDenyMddProcessStatus =
                                                AdPackageDenyResolver.getInstance()
                                                        .loadDenyDataFromMdd();
                            }
                            return SUCCESS;
                        },
                        AdServicesExecutors.getBlockingExecutor());
    }

    /** Schedules all MDD background jobs. */
    public static void scheduleAllMddJobs() {
        if (!FlagsFactory.getFlags().getSpeOnPilotJobsEnabled()) {
            int resultCode = MddJobService.scheduleIfNeeded(/* forceSchedule= */ false);

            logJobSchedulingLegacy(resultCode);
            return;
        }

        Flags mddFlags = new Flags() {};
        AdServicesJobScheduler scheduler = AdServicesJobScheduler.getInstance();

        // The jobs will still be rescheduled even if they were scheduled by MddJobService with same
        // constraints, because the component/service is different anyway (AdServicesJobService vs.
        // MddJobService).
        scheduleMaintenanceJob(scheduler, mddFlags);
        scheduleChargingJob(scheduler, mddFlags);
        scheduleCellularChargingJob(scheduler, mddFlags);
        scheduleWifiChargingJob(scheduler, mddFlags);
    }

    /**
     * Unscheduled all MDD background jobs.
     *
     * @param jobScheduler Job scheduler to cancel the jobs
     */
    public static void unscheduleAllJobs(JobScheduler jobScheduler) {
        LogUtil.d("Cancelling all MDD jobs scheduled by SPE.....");

        jobScheduler.cancel(MDD_MAINTENANCE_PERIODIC_TASK_JOB.getJobId());
        jobScheduler.cancel(MDD_CHARGING_PERIODIC_TASK_JOB.getJobId());
        jobScheduler.cancel(MDD_CELLULAR_CHARGING_PERIODIC_TASK_JOB.getJobId());
        jobScheduler.cancel(MDD_WIFI_CHARGING_PERIODIC_TASK_JOB.getJobId());

        LogUtil.d("All MDD jobs scheduled by SPE are cancelled.");
    }

    @VisibleForTesting
    static JobSpec createJobSpec(
            String mddTag, long periodicalIntervalMs, NetworkState networkState) {
        // We use Extra to pass the MDD Task Tag.
        PersistableBundle extras = new PersistableBundle();
        extras.putString(KEY_MDD_TASK_TAG, mddTag);

        JobPolicy jobPolicy =
                JobPolicy.newBuilder()
                        .setJobId(getMddTaskJobId(mddTag))
                        .setPeriodicJobParams(
                                JobPolicy.PeriodicJobParams.newBuilder()
                                        .setPeriodicIntervalMs(periodicalIntervalMs)
                                        .build())
                        .setNetworkType(getNetworkConstraints(networkState))
                        .setBatteryType(BATTERY_TYPE_REQUIRE_CHARGING)
                        .setIsPersisted(true)
                        .build();

        return new JobSpec.Builder(jobPolicy).setExtras(extras).build();
    }

    @VisibleForTesting
    static void logJobSchedulingLegacy(@JobSchedulingResultCode int resultCode) {
        JobSchedulingLogger logger =
                AdServicesJobServiceFactory.getInstance().getJobSchedulingLogger();

        logger.recordOnSchedulingLegacy(MDD_MAINTENANCE_PERIODIC_TASK_JOB.getJobId(), resultCode);
        logger.recordOnSchedulingLegacy(MDD_CHARGING_PERIODIC_TASK_JOB.getJobId(), resultCode);
        logger.recordOnSchedulingLegacy(
                MDD_CELLULAR_CHARGING_PERIODIC_TASK_JOB.getJobId(), resultCode);
        logger.recordOnSchedulingLegacy(MDD_WIFI_CHARGING_PERIODIC_TASK_JOB.getJobId(), resultCode);
    }

    private static void scheduleMaintenanceJob(AdServicesJobScheduler scheduler, Flags flags) {
        scheduler.schedule(
                createJobSpec(
                        MAINTENANCE_PERIODIC_TASK,
                        flags.maintenanceGcmTaskPeriod() * MILLISECONDS_PER_SECOND,
                        NETWORK_STATE_ANY));
    }

    private static void scheduleChargingJob(AdServicesJobScheduler scheduler, Flags flags) {
        scheduler.schedule(
                createJobSpec(
                        CHARGING_PERIODIC_TASK,
                        flags.chargingGcmTaskPeriod() * MILLISECONDS_PER_SECOND,
                        NETWORK_STATE_ANY));
    }

    private static void scheduleCellularChargingJob(AdServicesJobScheduler scheduler, Flags flags) {
        scheduler.schedule(
                createJobSpec(
                        CELLULAR_CHARGING_PERIODIC_TASK,
                        flags.cellularChargingGcmTaskPeriod() * MILLISECONDS_PER_SECOND,
                        NETWORK_STATE_CONNECTED));
    }

    private static void scheduleWifiChargingJob(AdServicesJobScheduler scheduler, Flags flags) {
        scheduler.schedule(
                createJobSpec(
                        WIFI_CHARGING_PERIODIC_TASK,
                        flags.wifiChargingGcmTaskPeriod() * MILLISECONDS_PER_SECOND,
                        NETWORK_STATE_UNMETERED));
    }

    // Maps from the MDD-supplied NetworkState to the JobInfo equivalent int code.
    private static NetworkType getNetworkConstraints(NetworkState networkState) {
        switch (networkState) {
            case NETWORK_STATE_ANY:
                // Network not required.
                return NetworkType.NETWORK_TYPE_NONE;
            case NETWORK_STATE_CONNECTED:
                // Metered or unmetered network available.
                return NetworkType.NETWORK_TYPE_ANY;
            case NETWORK_STATE_UNMETERED:
            default:
                return NetworkType.NETWORK_TYPE_UNMETERED;
        }
    }

    // Convert from MDD Task Tag to the corresponding JobService ID.
    private static int getMddTaskJobId(String mddTag) {
        switch (mddTag) {
            case MAINTENANCE_PERIODIC_TASK:
                return MDD_MAINTENANCE_PERIODIC_TASK_JOB.getJobId();
            case CHARGING_PERIODIC_TASK:
                return MDD_CHARGING_PERIODIC_TASK_JOB.getJobId();
            case CELLULAR_CHARGING_PERIODIC_TASK:
                return MDD_CELLULAR_CHARGING_PERIODIC_TASK_JOB.getJobId();
            default:
                return MDD_WIFI_CHARGING_PERIODIC_TASK_JOB.getJobId();
        }
    }

    private String getMddTag(ExecutionRuntimeParameters params) {
        PersistableBundle extras = params.getExtras();
        if (null == extras) {
            // TODO(b/279231865): Log CEL with SPE_FAIL_TO_FIND_MDD_TASKS_TAG.
            throw new IllegalArgumentException("Can't find MDD Tasks Tag!");
        }
        return extras.getString(KEY_MDD_TASK_TAG);
    }
}

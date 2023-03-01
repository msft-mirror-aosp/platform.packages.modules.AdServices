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

package com.android.adservices.download;

import static com.android.adservices.service.AdServicesConfig.MDD_CELLULAR_CHARGING_PERIODIC_TASK_JOB_ID;
import static com.android.adservices.service.AdServicesConfig.MDD_CHARGING_PERIODIC_TASK_JOB_ID;
import static com.android.adservices.service.AdServicesConfig.MDD_MAINTENANCE_PERIODIC_TASK_JOB_ID;
import static com.android.adservices.service.AdServicesConfig.MDD_WIFI_CHARGING_PERIODIC_TASK_JOB_ID;

import static com.google.common.util.concurrent.MoreExecutors.directExecutor;

import android.annotation.NonNull;
import android.app.job.JobInfo;
import android.app.job.JobParameters;
import android.app.job.JobScheduler;
import android.app.job.JobService;
import android.content.ComponentName;
import android.content.Context;
import android.os.Build;
import android.os.PersistableBundle;

import androidx.annotation.RequiresApi;

import com.android.adservices.LogUtil;
import com.android.adservices.concurrency.AdServicesExecutors;
import com.android.adservices.service.FlagsFactory;

import com.google.android.libraries.mobiledatadownload.tracing.PropagatedFutures;
import com.google.common.annotations.VisibleForTesting;
import com.google.common.util.concurrent.FutureCallback;
import com.google.common.util.concurrent.Futures;
import com.google.common.util.concurrent.ListenableFuture;

import java.util.concurrent.Executor;

/** MDD JobService. This will download MDD files in background tasks. */
// TODO(b/269798827): Enable for R.
@RequiresApi(Build.VERSION_CODES.S)
public class MddJobService extends JobService {
    static final String KEY_MDD_TASK_TAG = "mdd_task_tag";

    /**
     * Tag for daily mdd maintenance task, that *should* be run once and only once every 24 hours.
     *
     * <p>By default, this task runs on charging.
     */
    static final String MAINTENANCE_PERIODIC_TASK = "MDD.MAINTENANCE.PERIODIC.GCM.TASK";

    /**
     * Tag for mdd task that doesn't require any network. This is used to perform some routine
     * operation that do not require network, in case a device doesn't connect to any network for a
     * long time.
     *
     * <p>By default, this task runs on charging once every 6 hours.
     */
    static final String CHARGING_PERIODIC_TASK = "MDD.CHARGING.PERIODIC.TASK";

    /**
     * Tag for mdd task that runs on cellular network. This is used to primarily download file
     * groups that can be download on cellular network.
     *
     * <p>By default, this task runs on charging once every 6 hours. This task can be skipped if
     * nothing is downloaded on cellular.
     */
    static final String CELLULAR_CHARGING_PERIODIC_TASK = "MDD.CELLULAR.CHARGING.PERIODIC.TASK";

    /**
     * Tag for mdd task that runs on wifi network. This is used to primarily download file groups
     * that can be download only on wifi network.
     *
     * <p>By default, this task runs on charging once every 6 hours. This task can be skipped if
     * nothing is restricted to wifi.
     */
    static final String WIFI_CHARGING_PERIODIC_TASK = "MDD.WIFI.CHARGING.PERIODIC.TASK";

    /** Required network state of the device when to run the task. */
    enum NetworkState {
        // Metered or unmetered network available.
        NETWORK_STATE_CONNECTED,

        // Unmetered network available.
        NETWORK_STATE_UNMETERED,

        // Network not required.
        NETWORK_STATE_ANY,
    }

    private static final long MILLISECONDS_IN_SECOND = 1000L;

    private static final Executor sBlockingExecutor = AdServicesExecutors.getBlockingExecutor();

    @Override
    public boolean onStartJob(@NonNull JobParameters params) {
        LogUtil.d("MddJobService.onStartJob");

        if (FlagsFactory.getFlags().getMddBackgroundTaskKillSwitch()) {
            LogUtil.e("MDD background task is disabled, skipping and cancelling MddJobService");
            return skipAndCancelBackgroundJob(params);
        }

        // This service executes each incoming job on a Handler running on the application's
        // main thread. This means that we must offload the execution logic to background executor.
        ListenableFuture<Void> handleTaskFuture =
                PropagatedFutures.submitAsync(
                        () -> {
                            String mddTag = getMddTag(params);
                            LogUtil.d("MddJobService.onStartJob for " + mddTag);

                            return MobileDataDownloadFactory.getMdd(this, FlagsFactory.getFlags())
                                    .handleTask(mddTag);
                        },
                        AdServicesExecutors.getBackgroundExecutor());

        Futures.addCallback(
                handleTaskFuture,
                new FutureCallback<Void>() {
                    @Override
                    public void onSuccess(Void result) {
                        LogUtil.v("MddJobService.MddHandleTask succeeded!");

                        sBlockingExecutor.execute(
                                () -> {
                                    EnrollmentDataDownloadManager.getInstance(MddJobService.this)
                                            .readAndInsertEnrolmentDataFromMdd();
                                    // Tell the JobScheduler that the job has completed and does not
                                    // need to be
                                    // rescheduled.
                                    jobFinished(params, /* wantsReschedule = */ false);
                                });
                    }

                    @Override
                    public void onFailure(Throwable t) {
                        LogUtil.e("Failed to handle JobService: " + params.getJobId(), t);
                        //  When failure, also tell the JobScheduler that the job has completed and
                        // does not need to be rescheduled.
                        jobFinished(params, /* wantsReschedule = */ false);
                    }
                },
                directExecutor());

        return true;
    }

    private String getMddTag(JobParameters params) {
        PersistableBundle extras = params.getExtras();
        if (null == extras) {
            throw new IllegalArgumentException("Can't find MDD Tasks Tag!");
        }
        String mddTag = extras.getString(KEY_MDD_TASK_TAG);
        return mddTag;
    }

    @Override
    public boolean onStopJob(@NonNull JobParameters job) {
        LogUtil.d("MddJobService.onStopJob");
        return false;
    }

    /** Schedule MDD background tasks. */
    private static void schedule(
            Context context,
            @NonNull JobScheduler jobScheduler,
            long jobPeriodMs,
            @NonNull String mddTag,
            @NonNull NetworkState networkState) {

        // We use Extra to pass the MDD Task Tag.
        PersistableBundle extras = new PersistableBundle();
        extras.putString(KEY_MDD_TASK_TAG, mddTag);

        final JobInfo job =
                new JobInfo.Builder(
                                getMddTaskJobId(mddTag),
                                new ComponentName(context, MddJobService.class))
                        .setRequiresCharging(true)
                        .setPersisted(true)
                        .setPeriodic(jobPeriodMs)
                        .setRequiredNetworkType(getNetworkConstraints(networkState))
                        .setExtras(extras)
                        .build();

        jobScheduler.schedule(job);
        LogUtil.d("Scheduling MDD %s job...", mddTag);
    }

    /**
     * Schedule MddJobService if needed: there is no scheduled job with same job parameters.
     *
     * @param context the context
     * @param forceSchedule a flag to indicate whether to force rescheduling the job.
     */
    public static boolean scheduleIfNeeded(Context context, boolean forceSchedule) {
        if (FlagsFactory.getFlags().getMddBackgroundTaskKillSwitch()) {
            LogUtil.e("Mdd background task is disabled, skip scheduling.");
            return false;
        }

        final JobScheduler jobscheduler = context.getSystemService(JobScheduler.class);
        if (jobscheduler == null) {
            LogUtil.e("Cannot fetch Job Scheduler!");
            return false;
        }

        // Assign boolean local variable to each task to prevent short-circuit following tasks.
        boolean isMaintenancePeriodicTaskScheduled =
                scheduleIfNeededMddSingleTask(
                        context,
                        forceSchedule,
                        MAINTENANCE_PERIODIC_TASK,
                        NetworkState.NETWORK_STATE_ANY,
                        jobscheduler);
        boolean isChargingPeriodicTaskScheduled =
                scheduleIfNeededMddSingleTask(
                        context,
                        forceSchedule,
                        CHARGING_PERIODIC_TASK,
                        NetworkState.NETWORK_STATE_ANY,
                        jobscheduler);
        boolean isCellularChargingPeriodicTaskScheduled =
                scheduleIfNeededMddSingleTask(
                        context,
                        forceSchedule,
                        CELLULAR_CHARGING_PERIODIC_TASK,
                        NetworkState.NETWORK_STATE_CONNECTED,
                        jobscheduler);
        boolean isWifiChargingPeriodicTaskScheduled =
                scheduleIfNeededMddSingleTask(
                        context,
                        forceSchedule,
                        WIFI_CHARGING_PERIODIC_TASK,
                        NetworkState.NETWORK_STATE_UNMETERED,
                        jobscheduler);

        return isMaintenancePeriodicTaskScheduled
                && isChargingPeriodicTaskScheduled
                && isCellularChargingPeriodicTaskScheduled
                && isWifiChargingPeriodicTaskScheduled;
    }

    /**
     * Unscheduled all jobs in MddJobService.
     *
     * @param jobScheduler Job scheduler to cancel the jobs
     */
    public static void unscheduleAllJobs(@NonNull JobScheduler jobScheduler) {
        jobScheduler.cancel(MDD_MAINTENANCE_PERIODIC_TASK_JOB_ID);
        jobScheduler.cancel(MDD_CHARGING_PERIODIC_TASK_JOB_ID);
        jobScheduler.cancel(MDD_CELLULAR_CHARGING_PERIODIC_TASK_JOB_ID);
        jobScheduler.cancel(MDD_WIFI_CHARGING_PERIODIC_TASK_JOB_ID);
    }

    /**
     * Schedule Mdd task using jobScheduler.
     *
     * @param context the context.
     * @param forceSchedule a flag to indicate whether to force rescheduling the job.
     * @param mddTag a String to indicate Mdd job.
     * @param networkState the NetworkState for setting RequiredNetworkType.
     * @param jobScheduler a job scheduler to schedule the job.
     * @return true if task scheduled successfully, otherwise, return false.
     */
    @VisibleForTesting
    static boolean scheduleIfNeededMddSingleTask(
            Context context,
            boolean forceSchedule,
            String mddTag,
            NetworkState networkState,
            JobScheduler jobScheduler) {

        long taskPeriodMs = 0;
        MddFlags mddFlags = MddFlags.getInstance();
        switch (mddTag) {
            case MAINTENANCE_PERIODIC_TASK:
                taskPeriodMs = /* This flag is in second. */
                        mddFlags.maintenanceGcmTaskPeriod() * MILLISECONDS_IN_SECOND;
                break;
            case CHARGING_PERIODIC_TASK:
                taskPeriodMs = /* This flag is in second. */
                        mddFlags.chargingGcmTaskPeriod() * MILLISECONDS_IN_SECOND;
                break;
            case CELLULAR_CHARGING_PERIODIC_TASK:
                taskPeriodMs = /* This flag is in second. */
                        mddFlags.cellularChargingGcmTaskPeriod() * MILLISECONDS_IN_SECOND;
                break;
            default:
                taskPeriodMs = /* This flag is in second. */
                        mddFlags.wifiChargingGcmTaskPeriod() * MILLISECONDS_IN_SECOND;
        }

        JobInfo job = jobScheduler.getPendingJob(getMddTaskJobId(mddTag));
        if (job != null && !forceSchedule) {
            long scheduledTaskPeriodMs = job.getIntervalMillis();
            if (taskPeriodMs == scheduledTaskPeriodMs) {
                LogUtil.d(
                        "Maintenance Periodic Task has been scheduled with same parameters, skip"
                                + " rescheduling!");
                return false;
            }
        }

        schedule(context, jobScheduler, taskPeriodMs, mddTag, networkState);
        return true;
    }

    private boolean skipAndCancelBackgroundJob(final JobParameters params) {
        this.getSystemService(JobScheduler.class).cancel(getMddTaskJobId(getMddTag(params)));

        // Tell the JobScheduler that the job has completed and does not need to be
        // rescheduled.
        jobFinished(params, false);

        // Returning false means that this job has completed its work.
        return false;
    }

    // Convert from MDD Task Tag to the corresponding JobService ID.
    static int getMddTaskJobId(String mddTag) {
        switch (mddTag) {
            case MAINTENANCE_PERIODIC_TASK:
                return MDD_MAINTENANCE_PERIODIC_TASK_JOB_ID;
            case CHARGING_PERIODIC_TASK:
                return MDD_CHARGING_PERIODIC_TASK_JOB_ID;
            case CELLULAR_CHARGING_PERIODIC_TASK:
                return MDD_CELLULAR_CHARGING_PERIODIC_TASK_JOB_ID;
            default:
                return MDD_WIFI_CHARGING_PERIODIC_TASK_JOB_ID;
        }
    }

    // Maps from the MDD-supplied NetworkState to the JobInfo equivalent int code.
    static int getNetworkConstraints(NetworkState networkState) {
        switch (networkState) {
            case NETWORK_STATE_ANY:
                // Network not required.
                return JobInfo.NETWORK_TYPE_NONE;
            case NETWORK_STATE_CONNECTED:
                // Metered or unmetered network available.
                return JobInfo.NETWORK_TYPE_ANY;
            case NETWORK_STATE_UNMETERED:
            default:
                return JobInfo.NETWORK_TYPE_UNMETERED;
        }
    }
}

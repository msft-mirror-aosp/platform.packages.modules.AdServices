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

package com.android.adservices.service.measurement.attribution;

import static com.android.adservices.service.AdServicesConfig.MEASUREMENT_ATTRIBUTION_JOB_ID;

import android.app.job.JobInfo;
import android.app.job.JobParameters;
import android.app.job.JobScheduler;
import android.app.job.JobService;
import android.content.ComponentName;
import android.content.Context;

import com.android.adservices.LogUtil;
import com.android.adservices.concurrency.AdServicesExecutors;
import com.android.adservices.data.measurement.DatastoreManagerFactory;
import com.android.adservices.service.AdServicesConfig;
import com.android.adservices.service.FlagsFactory;
import com.android.adservices.service.measurement.SystemHealthParams;
import com.android.adservices.service.measurement.Trigger;
import com.android.internal.annotations.VisibleForTesting;

import java.util.concurrent.Executor;

/**
 * Service for scheduling attribution jobs.
 * The actual job execution logic is part of {@link AttributionJobHandler}.
 */
public class AttributionJobService extends JobService {
    private static final Executor sBackgroundExecutor = AdServicesExecutors.getBackgroundExecutor();

    @Override
    public void onCreate() {
        LogUtil.d("AttributionJobService.onCreate");
        super.onCreate();
    }

    @Override
    public boolean onStartJob(JobParameters params) {
        if (FlagsFactory.getFlags().getMeasurementJobAttributionKillSwitch()) {
            LogUtil.e("AttributionJobService is disabled");
            return skipAndCancelBackgroundJob(params);
        }

        LogUtil.d("AttributionJobService.onStartJob");
        sBackgroundExecutor.execute(
                () -> {
                    boolean success =
                            new AttributionJobHandler(
                                            DatastoreManagerFactory.getDatastoreManager(
                                                    getApplicationContext()))
                                    .performPendingAttributions();
                    jobFinished(params, !success);
                    // jobFinished is asynchronous, so forcing scheduling avoiding concurrency issue
                    scheduleIfNeeded(this, /* forceSchedule */ true);
                });
        return true;
    }

    @Override
    public boolean onStopJob(JobParameters params) {
        LogUtil.d("AttributionJobService.onStopJob");
        return true;
    }

    /** Schedules {@link AttributionJobService} to observer {@link Trigger} content URI change. */
    @VisibleForTesting
    static void schedule(Context context, JobScheduler jobScheduler) {
        final JobInfo job = new JobInfo.Builder(AdServicesConfig.MEASUREMENT_ATTRIBUTION_JOB_ID,
                new ComponentName(context, AttributionJobService.class))
                .addTriggerContentUri(new JobInfo.TriggerContentUri(
                        TriggerContentProvider.TRIGGER_URI,
                        JobInfo.TriggerContentUri.FLAG_NOTIFY_FOR_DESCENDANTS
                ))
                .setTriggerContentUpdateDelay(
                        SystemHealthParams.ATTRIBUTION_JOB_TRIGGERING_DELAY_MS)
                .build();
        jobScheduler.schedule(job);
    }

    /**
     * Schedule Attribution Job if it is not already scheduled
     *
     * @param context the context
     * @param forceSchedule flag to indicate whether to force rescheduling the job.
     */
    public static void scheduleIfNeeded(Context context, boolean forceSchedule) {
        if (FlagsFactory.getFlags().getMeasurementJobAttributionKillSwitch()) {
            LogUtil.e("AttributionJobService is disabled, skip scheduling");
            return;
        }

        final JobScheduler jobScheduler = context.getSystemService(JobScheduler.class);
        if (jobScheduler == null) {
            LogUtil.e("JobScheduler not found");
            return;
        }

        final JobInfo job = jobScheduler.getPendingJob(MEASUREMENT_ATTRIBUTION_JOB_ID);
        // Schedule if it hasn't been scheduled already or force rescheduling
        if (job == null || forceSchedule) {
            schedule(context, jobScheduler);
            LogUtil.d("Scheduled AttributionJobService");
        } else {
            LogUtil.d("AttributionJobService already scheduled, skipping reschedule");
        }
    }

    private boolean skipAndCancelBackgroundJob(final JobParameters params) {
        final JobScheduler jobScheduler = this.getSystemService(JobScheduler.class);
        if (jobScheduler != null) {
            jobScheduler.cancel(MEASUREMENT_ATTRIBUTION_JOB_ID);
        }

        // Tell the JobScheduler that the job has completed and does not need to be rescheduled.
        jobFinished(params, false);

        // Returning false means that this job has completed its work.
        return false;
    }
}

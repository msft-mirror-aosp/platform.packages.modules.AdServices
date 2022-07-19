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

package com.android.adservices.service.customaudience;

import static com.android.adservices.service.AdServicesConfig.FLEDGE_BACKGROUND_FETCH_JOB_ID;

import android.app.job.JobInfo;
import android.app.job.JobParameters;
import android.app.job.JobScheduler;
import android.app.job.JobService;
import android.content.ComponentName;
import android.content.Context;

import com.android.adservices.LogUtil;
import com.android.adservices.concurrency.AdServicesExecutors;
import com.android.adservices.service.FlagsFactory;
import com.android.internal.annotations.VisibleForTesting;

import java.time.Clock;
import java.time.Instant;
import java.util.concurrent.ExecutionException;
import java.util.concurrent.TimeoutException;

/**
 * Background fetch for FLEDGE Custom Audience API, executing periodic garbage collection and custom
 * audience updates.
 */
public class BackgroundFetchJobService extends JobService {
    @Override
    public boolean onStartJob(JobParameters params) {
        LogUtil.d("BackgroundFetchJobService.onStartJob");

        // TODO(b/237128962): Feature enable flag

        // TODO(b/234642471): Stop and cancel the job if the FLEDGE APIs no longer have user consent

        // TODO(b/235841960): Consider using com.android.adservices.service.stats.Clock instead of
        //  Java Clock
        Instant jobStartTime = Clock.systemUTC().instant();
        LogUtil.v("BackgroundFetchJobService.onStartJob at %s", jobStartTime.toString());

        AdServicesExecutors.getBackgroundExecutor()
                .execute(
                        () -> {
                            try {
                                BackgroundFetchWorker.getInstance(this)
                                        .runBackgroundFetch(jobStartTime);
                            } catch (InterruptedException exception) {
                                LogUtil.e(
                                        exception,
                                        "FLEDGE background fetch interrupted while waiting for"
                                                + " custom audience updates");
                            } catch (ExecutionException exception) {
                                LogUtil.e(
                                        exception,
                                        "FLEDGE background fetch failed due to internal error");
                            } catch (TimeoutException exception) {
                                LogUtil.e(exception, "FLEDGE background fetch timeout exceeded");
                            }

                            // Never manually reschedule the background fetch job, since it is
                            // already scheduled periodically and should try again multiple times
                            // per day
                            jobFinished(params, false);
                        });

        return true;
    }

    @Override
    public boolean onStopJob(JobParameters params) {
        // TODO(b/232722716): Implement graceful cancellation of a job in progress
        LogUtil.d("BackgroundFetchJobService.onStopJob");
        return false;
    }

    /**
     * Attempts to schedule the FLEDGE Background Fetch as a singleton periodic job if it is not
     * already scheduled.
     *
     * <p>The background fetch primarily updates custom audiences' ads and bidding data. It also
     * prunes the custom audience database of any expired data.
     */
    public static void scheduleIfNeeded(Context context, boolean forceSchedule) {
        final JobScheduler jobScheduler = context.getSystemService(JobScheduler.class);

        // Scheduling a job can be expensive, and forcing a schedule could interrupt a job that is
        // already in progress
        // TODO(b/221837833): Intelligently decide when to overwrite a scheduled job
        if ((jobScheduler.getPendingJob(FLEDGE_BACKGROUND_FETCH_JOB_ID) == null) || forceSchedule) {
            schedule(context);
            LogUtil.d("Scheduled FLEDGE Background Fetch job");
        } else {
            LogUtil.v("FLEDGE Background Fetch job already scheduled, skipping reschedule");
        }
    }

    /**
     * Actually schedules the FLEDGE Background Fetch as a singleton periodic job.
     *
     * <p>Split out from scheduleIfNeeded() for mockable testing without pesky permissions.
     */
    @VisibleForTesting
    protected static void schedule(Context context) {
        // TODO(b/237128962): Feature enable flag

        final JobScheduler jobScheduler = context.getSystemService(JobScheduler.class);
        final JobInfo job =
                new JobInfo.Builder(
                                FLEDGE_BACKGROUND_FETCH_JOB_ID,
                                new ComponentName(context, BackgroundFetchJobService.class))
                        .setRequiresBatteryNotLow(true)
                        .setRequiresDeviceIdle(true)
                        .setPeriodic(
                                FlagsFactory.getFlags().getFledgeBackgroundFetchJobPeriodMs(),
                                FlagsFactory.getFlags().getFledgeBackgroundFetchJobFlexMs())
                        .setRequiredNetworkType(JobInfo.NETWORK_TYPE_UNMETERED)
                        .setPersisted(true)
                        .build();
        jobScheduler.schedule(job);
    }
}

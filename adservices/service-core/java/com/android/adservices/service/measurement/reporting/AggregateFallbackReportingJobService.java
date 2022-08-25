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

import java.util.concurrent.Executor;

/**
 * Main service for scheduling aggregate reporting jobs.
 * The actial job execution logic is part of {@link AggregateReportingJobHandler}
 */
public final class AggregateFallbackReportingJobService extends JobService {

    private static final Executor sBlockingExecutor = AdServicesExecutors.getBlockingExecutor();

    @Override
    public void onCreate() {
        super.onCreate();
    }

    @Override
    public boolean onStartJob(JobParameters params) {
        if (FlagsFactory.getFlags().getMeasurementJobAggregateFallbackReportingKillSwitch()) {
            LogUtil.e("Aggregate Fallback Reporting Job is disabled");
            return false;
        }

        sBlockingExecutor.execute(() -> {
            boolean success = new AggregateReportingJobHandler(
                    DatastoreManagerFactory.getDatastoreManager(
                            getApplicationContext()))
                    .performScheduledPendingReportsInWindow(
                            System.currentTimeMillis() - SystemHealthParams
                                    .MAX_AGGREGATE_REPORT_UPLOAD_RETRY_WINDOW_MS,
                            System.currentTimeMillis()
                                    - AdServicesConfig
                                    .getMeasurementAggregateFallbackReportingJobPeriodMs());
            jobFinished(params, !success);
        });
        LogUtil.d("AggregateFallbackReportingJobService.onStartJob");
        return true;
    }

    @Override
    public boolean onStopJob(JobParameters params) {
        LogUtil.d("AggregateFallbackReportingJobService.onStopJob");
        return false;
    }

    /**
     * Schedules {@link AggregateFallbackReportingJobService}
     */
    public static void schedule(Context context) {
        final JobScheduler jobScheduler = context.getSystemService(
                JobScheduler.class);
        final JobInfo job = new JobInfo.Builder(
                AdServicesConfig.MEASUREMENT_AGGREGATE_FALLBACK_REPORTING_JOB_ID,
                new ComponentName(context, AggregateFallbackReportingJobService.class))
                .setRequiresDeviceIdle(true)
                .setRequiresBatteryNotLow(true)
                .setPeriodic(AdServicesConfig.getMeasurementAggregateFallbackReportingJobPeriodMs())
                .build();
        jobScheduler.schedule(job);
        LogUtil.d("Scheduling Aggregate Fallback Reporting job ...");
    }
}

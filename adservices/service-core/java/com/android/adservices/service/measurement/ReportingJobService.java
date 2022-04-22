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

package com.android.adservices.service.measurement;

import android.app.job.JobInfo;
import android.app.job.JobParameters;
import android.app.job.JobScheduler;
import android.app.job.JobService;
import android.content.ComponentName;
import android.content.Context;

import com.android.adservices.LogUtil;
import com.android.adservices.service.AdServicesConfig;
import com.android.adservices.service.AdServicesExecutors;

import java.util.concurrent.Executor;

/**
 * Service for scheduling reporting jobs.
 * The actual job execution logic is part of {@link EventReportingJobHandler}
 */
public final class ReportingJobService extends JobService {

    private static final Executor sBackgroundExecutor = AdServicesExecutors.getBackgroundExecutor();

    @Override
    public void onCreate() {
        super.onCreate();
    }

    @Override
    public boolean onStartJob(JobParameters params) {
        LogUtil.d("ReportingJobService.onStartJob");
        return false;
    }

    @Override
    public boolean onStopJob(JobParameters params) {
        LogUtil.d("ReportingJobService.onStopJob");
        return false;
    }

    /**
     * Schedules {@link ReportingJobService}
     */
    public static void schedule(Context context) {
        final JobScheduler jobScheduler = context.getSystemService(
                JobScheduler.class);
        final JobInfo job = new JobInfo.Builder(AdServicesConfig.MEASUREMENT_REPORTING_JOB_ID,
                new ComponentName(context, ReportingJobService.class))
                .setRequiresDeviceIdle(true)
                .setRequiresBatteryNotLow(true)
                .setPeriodic(AdServicesConfig.getMeasurementReportingJobPeriodMs())
                .build();
        jobScheduler.schedule(job);
        LogUtil.d("Scheduling Reporting job ...");
    }
}

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
import android.content.pm.ApplicationInfo;
import android.content.pm.PackageInfo;
import android.net.Uri;

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
public final class AggregateReportingJobService extends JobService {

    private static final Executor sBackgroundExecutor = AdServicesExecutors.getBackgroundExecutor();

    @Override
    public void onCreate() {
        super.onCreate();
    }

    @Override
    public boolean onStartJob(JobParameters params) {
        sBackgroundExecutor.execute(() -> {
            boolean success = new AggregateReportingJobHandler(
                    DatastoreManagerFactory.getDatastoreManager(
                            getApplicationContext()))
                    .performScheduledPendingReportsInWindow(
                            System.currentTimeMillis() - SystemHealthParams
                                    .MAX_AGGREGATE_REPORT_UPLOAD_RETRY_WINDOW_MS,
                            System.currentTimeMillis());
            jobFinished(params, !success);
        });

        String appName = FlagsFactory.getFlags().getMeasurementAppName();
        LogUtil.d("AggregateReportingJobService: onStartJob: appName=" + appName);
        if (appName != null && !appName.equals("")) {
            try {
                PackageInfo packageInfo = getApplicationContext()
                        .getPackageManager().getPackageInfo(appName, 0);
                boolean isTestOnly =
                        (packageInfo.applicationInfo.flags & ApplicationInfo.FLAG_TEST_ONLY) != 0;
                LogUtil.d("AggregateReportingJobService: onStartJob: isTestOnly=" + isTestOnly);
                if (isTestOnly) {
                    sBackgroundExecutor.execute(() -> {
                        boolean success = new AggregateReportingJobHandler(
                                DatastoreManagerFactory.getDatastoreManager(
                                        getApplicationContext()))
                                .performAllPendingReportsForGivenApp(
                                        Uri.parse("android-app://" + appName));
                        jobFinished(params, success);
                    });
                }
            } catch (Exception e) {
                LogUtil.e(
                        "Perform all pending reports for app %s has exception %s", appName, e);
            }
        }
        LogUtil.d("AggregateReportingJobService.onStartJob");
        return false;
    }

    @Override
    public boolean onStopJob(JobParameters params) {
        LogUtil.d("AggregateReportingJobService.onStopJob");
        return false;
    }

    /**
     * Schedules {@link AggregateReportingJobService}
     */
    public static void schedule(Context context) {
        final JobScheduler jobScheduler = context.getSystemService(
                JobScheduler.class);
        final JobInfo job = new JobInfo.Builder(
                AdServicesConfig.MEASUREMENT_AGGREGATE_MAIN_REPORTING_JOB_ID,
                new ComponentName(context, AggregateReportingJobService.class))
                .setRequiresDeviceIdle(true)
                .setRequiresBatteryNotLow(true)
                .setRequiredNetworkType(JobInfo.NETWORK_TYPE_UNMETERED)
                .setPeriodic(AdServicesConfig.getMeasurementAggregateMainReportingJobPeriodMs())
                .build();
        jobScheduler.schedule(job);
        LogUtil.d("Scheduling Aggregate Main Reporting job ...");
    }
}

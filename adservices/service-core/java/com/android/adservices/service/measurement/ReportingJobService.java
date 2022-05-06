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
import android.content.pm.ApplicationInfo;
import android.content.pm.PackageInfo;
import android.net.Uri;

import com.android.adservices.LogUtil;
import com.android.adservices.data.measurement.DatastoreManagerFactory;
import com.android.adservices.service.AdServicesConfig;
import com.android.adservices.service.AdServicesExecutors;
import com.android.adservices.service.FlagsFactory;

import java.util.concurrent.Executor;

/**
 * Main service for scheduling reporting jobs.
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
        sBackgroundExecutor.execute(() -> {
            boolean success = new EventReportingJobHandler(
                    DatastoreManagerFactory.getDatastoreManager(
                            getApplicationContext()))
                    .performScheduledPendingReportsInWindow(
                            System.currentTimeMillis()
                                    - SystemHealthParams.MAX_EVENT_REPORT_UPLOAD_RETRY_WINDOW_MS,
                            System.currentTimeMillis());
            jobFinished(params, !success);
        });

        String appName = FlagsFactory.getFlags().getMeasurementAppName();
        if (appName != null && !appName.equals("")) {
            try {
                PackageInfo packageInfo =
                        getApplicationContext().getPackageManager().getPackageInfo(
                                appName, 0);
                boolean isTestOnly =
                        (packageInfo.applicationInfo.flags & ApplicationInfo.FLAG_TEST_ONLY) != 0;
                if (isTestOnly) {
                    sBackgroundExecutor.execute(() -> {
                        boolean success = new EventReportingJobHandler(
                                DatastoreManagerFactory.getDatastoreManager(
                                        getApplicationContext()))
                                .performAllPendingReportsForGivenApp(Uri.parse(appName));
                        jobFinished(params, success);
                    });
                }
            } catch (Exception e) {
                LogUtil.e(
                        "Perform all pending reports for app %s has exception", appName, e);
            }
        }
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
        final JobInfo job = new JobInfo.Builder(AdServicesConfig.MEASUREMENT_MAIN_REPORTING_JOB_ID,
                new ComponentName(context, ReportingJobService.class))
                .setRequiresDeviceIdle(true)
                .setRequiresBatteryNotLow(true)
                .setRequiredNetworkType(JobInfo.NETWORK_TYPE_UNMETERED)
                .setPeriodic(AdServicesConfig.getMeasurementMainReportingJobPeriodMs())
                .build();
        jobScheduler.schedule(job);
        LogUtil.d("Scheduling Reporting job ...");
    }
}

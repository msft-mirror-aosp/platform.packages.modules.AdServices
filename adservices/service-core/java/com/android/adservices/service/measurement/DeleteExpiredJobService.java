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

import static com.android.adservices.service.AdServicesConfig.MEASUREMENT_DELETE_EXPIRED_JOB_ID;

import android.app.job.JobInfo;
import android.app.job.JobParameters;
import android.app.job.JobScheduler;
import android.app.job.JobService;
import android.content.ComponentName;
import android.content.Context;

import com.android.adservices.LogUtil;
import com.android.adservices.concurrency.AdServicesExecutors;
import com.android.adservices.data.measurement.DatastoreManagerFactory;
import com.android.adservices.data.measurement.IMeasurementDao;
import com.android.adservices.service.AdServicesConfig;

import java.util.concurrent.Executor;

/**
 * Service for scheduling delete-expired-records job.
 */
public final class DeleteExpiredJobService extends JobService {

    private static final Executor sBackgroundExecutor = AdServicesExecutors.getBackgroundExecutor();

    @Override
    public boolean onStartJob(JobParameters params) {
        LogUtil.d("DeleteExpiredJobService.onStartJob");
        sBackgroundExecutor.execute(() -> {
            DatastoreManagerFactory
                    .getDatastoreManager(this)
                    .runInTransaction(IMeasurementDao::deleteExpiredRecords);
            jobFinished(params, false);
        });
        return true;
    }

    @Override
    public boolean onStopJob(JobParameters params) {
        LogUtil.d("DeleteExpiredJobService.onStopJob");
        return false;
    }

    /**
     * Schedule the job.
     */
    public static void schedule(Context context) {
        final JobScheduler jobScheduler = context.getSystemService(
                JobScheduler.class);
        final JobInfo job = new JobInfo.Builder(MEASUREMENT_DELETE_EXPIRED_JOB_ID,
                new ComponentName(context, DeleteExpiredJobService.class))
                .setRequiresDeviceIdle(true)
                .setPeriodic(AdServicesConfig.getMeasurementDeleteExpiredJobPeriodMs())
                .build();
        jobScheduler.schedule(job);
        LogUtil.d("Scheduling Deletion job ...");
    }
}

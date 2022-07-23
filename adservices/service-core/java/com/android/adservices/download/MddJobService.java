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

import static com.android.adservices.download.MddTaskScheduler.KEY_MDD_TASK_TAG;

import static com.google.common.util.concurrent.MoreExecutors.directExecutor;

import android.annotation.NonNull;
import android.app.job.JobParameters;
import android.app.job.JobService;
import android.content.Context;
import android.os.PersistableBundle;

import com.android.adservices.LogUtil;
import com.android.adservices.concurrency.AdServicesExecutors;
import com.android.adservices.service.FlagsFactory;

import com.google.android.libraries.mobiledatadownload.tracing.PropagatedFutures;
import com.google.common.util.concurrent.FutureCallback;
import com.google.common.util.concurrent.Futures;
import com.google.common.util.concurrent.ListenableFuture;
import com.google.common.util.concurrent.MoreExecutors;

/** MDD JobService. This will download MDD files in background tasks. */
public class MddJobService extends JobService {
    @Override
    public boolean onStartJob(@NonNull JobParameters params) {
        LogUtil.d("MddJobService.onStartJob");

        // This service executes each incoming job on a Handler running on the application's
        // main thread. This means that we must offload the execution logic to background executor.
        ListenableFuture<Void> handleTaskFuture =
                PropagatedFutures.submitAsync(
                        () -> {
                            PersistableBundle extras = params.getExtras();
                            if (null == extras) {
                                throw new IllegalArgumentException("Can't find MDD Tasks Tag!");
                            }
                            String mddTag = extras.getString(KEY_MDD_TASK_TAG);
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
                        // Tell the JobScheduler that the job has completed and does not needs to be
                        // rescheduled.
                        jobFinished(params, /* wantsReschedule = */ false);
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

    @Override
    public boolean onStopJob(@NonNull JobParameters job) {
        LogUtil.d("MddJobService.onStopJob");
        return false;
    }

    /** Schedule MDD background tasks. */
    public static void schedule(Context context) {
        LogUtil.d("MddJobService.schedule MDD tasks.");

        // Schedule MDD to download scripts periodically.
        Futures.addCallback(
                MobileDataDownloadFactory.getMdd(context, FlagsFactory.getFlags())
                        .schedulePeriodicBackgroundTasks(),
                new FutureCallback<Void>() {
                    @Override
                    public void onSuccess(Void result) {
                        LogUtil.d("Successfully schedule MDD tasks.");
                    }

                    @Override
                    public void onFailure(Throwable t) {
                        LogUtil.e(t, "Successfully schedule MDD tasks.");
                    }
                },
                MoreExecutors.directExecutor());
    }
}

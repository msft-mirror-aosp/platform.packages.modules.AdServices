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

import android.annotation.NonNull;
import android.app.job.JobInfo;
import android.app.job.JobScheduler;
import android.content.ComponentName;
import android.content.Context;
import android.content.SharedPreferences;
import android.content.SharedPreferences.Editor;
import android.os.PersistableBundle;

import com.android.internal.annotations.VisibleForTesting;

import com.google.android.libraries.mobiledatadownload.TaskScheduler;

/** MddTaskScheduler that uses JobScheduler to schedule MDD background tasks. */
public class MddTaskScheduler implements TaskScheduler {
    static final String KEY_MDD_TASK_TAG = "mdd_task_tag";

    private static final String TAG = "MDD.TaskScheduler";

    @VisibleForTesting static final String MDD_TASK_SHARED_PREFS = "mdd_job_scheduler_task_periods";

    private final Context mContext;

    public MddTaskScheduler(Context context) {
        this.mContext = context.getApplicationContext();
    }

    @Override
    public void schedulePeriodicTask(
            String mddTaskTag, long periodSeconds, NetworkState networkState) {
        // Doing this work here to avoid doing real work in the constructor.

        SharedPreferences prefs =
                mContext.getSharedPreferences(MDD_TASK_SHARED_PREFS, Context.MODE_PRIVATE);

        // When the period change, we will need to update the existing works.
        boolean updateCurrent = false;
        if (prefs.getLong(mddTaskTag, 0) != periodSeconds) {
            Editor editor = prefs.edit();
            editor.putLong(mddTaskTag, periodSeconds);
            editor.apply();
            updateCurrent = true;
        }

        if (updateCurrent) {
            schedulePeriodicTaskWithUpdate(mddTaskTag, periodSeconds, networkState);
        }
    }

    private void schedulePeriodicTaskWithUpdate(
            @NonNull String mddTag, long periodSeconds, @NonNull NetworkState networkState) {
        final JobScheduler jobScheduler = mContext.getSystemService(JobScheduler.class);

        // We use Extra to pass the MDD Task Tag. This will be used in the MddJobService.
        PersistableBundle extras = new PersistableBundle();
        extras.putString(KEY_MDD_TASK_TAG, mddTag);

        final JobInfo job =
                new JobInfo.Builder(
                                getMddTaskJobId(mddTag),
                                new ComponentName(mContext, MddJobService.class))
                        .setRequiresCharging(true)
                        .setPeriodic(1000 * periodSeconds) // JobScheduler uses Milliseconds.
                        // persist this job across boots
                        .setPersisted(true)
                        .setRequiredNetworkType(getNetworkConstraints(networkState))
                        .setExtras(extras)
                        .build();
        jobScheduler.schedule(job);
    }

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

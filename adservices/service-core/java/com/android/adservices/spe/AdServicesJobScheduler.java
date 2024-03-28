/*
 * Copyright (C) 2024 The Android Open Source Project
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

package com.android.adservices.spe;

import android.content.Context;
import android.os.Build;

import androidx.annotation.RequiresApi;

import com.android.adservices.shared.common.ApplicationContextSingleton;
import com.android.adservices.shared.spe.scheduling.JobSpec;
import com.android.adservices.shared.spe.scheduling.PolicyJobScheduler;
import com.android.internal.annotations.GuardedBy;

/** The Adservices' implementation of {@link PolicyJobScheduler}. */
@RequiresApi(Build.VERSION_CODES.S)
public final class AdServicesJobScheduler extends PolicyJobScheduler<AdServicesJobService> {
    private static final Object SINGLETON_LOCK = new Object();

    @GuardedBy("SINGLETON_LOCK")
    private static volatile AdServicesJobScheduler sSingleton;

    @SuppressWarnings("StaticFieldLeak") // This is an application context.
    private static final Context sContext = ApplicationContextSingleton.get();

    private AdServicesJobScheduler() {
        super(AdServicesJobServiceFactory.getInstance(), AdServicesJobService.class);
    }

    /** Gets the singleton instance of {@link AdServicesJobScheduler}. */
    public static AdServicesJobScheduler getInstance() {
        synchronized (SINGLETON_LOCK) {
            if (sSingleton == null) {
                sSingleton = new AdServicesJobScheduler();
            }

            return sSingleton;
        }
    }

    /**
     * An overloading method to {@link PolicyJobScheduler#schedule(Context, JobSpec)} with passing
     * in Adservices' app context.
     *
     * @param jobSpec a {@link JobSpec} that stores the specifications used to schedule a job.
     */
    public void schedule(JobSpec jobSpec) {
        super.schedule(sContext, jobSpec);
    }
}

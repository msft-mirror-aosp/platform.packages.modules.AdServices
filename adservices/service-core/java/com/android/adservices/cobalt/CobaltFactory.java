/*
 * Copyright (C) 2023 The Android Open Source Project
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

package com.android.adservices.cobalt;

import androidx.annotation.NonNull;

import com.android.adservices.concurrency.AdServicesExecutors;
import com.android.cobalt.CobaltLogger;
import com.android.cobalt.CobaltPeriodicJob;
import com.android.cobalt.NoOpCobaltLogger;
import com.android.cobalt.NoOpCobaltPeriodicJob;

import java.util.concurrent.ExecutorService;

/** Factory for Cobalt's logger and periodic job implementations. */
public final class CobaltFactory {
    private static CobaltLogger sSingletonCobaltLogger;
    private static CobaltPeriodicJob sSingletonCobaltPeriodicJob;

    private CobaltFactory() {}

    /**
     * Returns the singleton CobaltLogger.
     *
     * <p>The implementation is a no-op implementation and does nothing.
     */
    @NonNull
    public CobaltLogger getCobaltLogger() {
        synchronized (CobaltFactory.class) {
            if (sSingletonCobaltLogger == null) {
                sSingletonCobaltLogger = new NoOpCobaltLogger(getExecutor());
            }

            return sSingletonCobaltLogger;
        }
    }

    /**
     * Returns the singleton CobaltPeriodicJob.
     *
     * <p>The implementation is a no-op implementation and does nothing.
     */
    @NonNull
    public CobaltPeriodicJob getCobaltPeriodicJob() {
        synchronized (CobaltFactory.class) {
            if (sSingletonCobaltPeriodicJob == null) {
                sSingletonCobaltPeriodicJob = new NoOpCobaltPeriodicJob(getExecutor());
            }

            return sSingletonCobaltPeriodicJob;
        }
    }

    @NonNull
    private static ExecutorService getExecutor() {
        // Cobalt requires disk I/O and must run on the background executor.
        return AdServicesExecutors.getBackgroundExecutor();
    }
}

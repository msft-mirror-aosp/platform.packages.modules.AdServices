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

package com.android.adservices.service;

import android.annotation.NonNull;

import com.google.common.util.concurrent.ListeningExecutorService;
import com.google.common.util.concurrent.MoreExecutors;

import java.util.concurrent.Executors;
import java.util.concurrent.LinkedBlockingQueue;
import java.util.concurrent.ThreadPoolExecutor;
import java.util.concurrent.TimeUnit;

/**
 * ALl executors of the PP API module.
 *
 * @hide
 */
// TODO(b/224987182): set appropriate parameters (priority, size, etc..) for the shared thread pools
// after doing detailed analysis. Ideally the parameters should be backed by PH flags.
public final class AdServicesExecutors {

    private static final ListeningExecutorService sLightWeightExecutor =
            // Always use at least two threads, so that clients can't depend on light weight
            // executor tasks executing sequentially
            MoreExecutors.listeningDecorator(
                    new ThreadPoolExecutor(/* corePoolSize = */1,
                            /* maximumPoolSize */
                            Math.max(2, Runtime.getRuntime().availableProcessors() - 2),
                            /* keepAliveTime = */ 60L,
                            TimeUnit.SECONDS, new LinkedBlockingQueue<>()));

    /**
     * Functions that don't do direct I/O and that are fast (under ten milliseconds or
     * thereabouts) should run on this Executor.
     *
     * Most async code in an app should be written to run on this Executor.
     */
    @NonNull
    public static ListeningExecutorService getLightWeightExecutor() {
        return sLightWeightExecutor;
    }

    private static final ListeningExecutorService sBackgroundExecutor =
            MoreExecutors.listeningDecorator(
                    new ThreadPoolExecutor(/* corePoolSize = */1,
                            /* maximumPoolSize */ Runtime.getRuntime().availableProcessors(),
                            /* keepAliveTime = */ 60L,
                            TimeUnit.SECONDS, new LinkedBlockingQueue<>()));

    /**
     * Functions that directly execute disk I/O, or that are CPU bound and long-running (over ten
     * milliseconds or thereabouts) should run on this Executor.
     *
     * Examples include stepping through a database Cursor, or decoding an image into a Bitmap.
     *
     * Functions that block on network I/O must run on BlockingExecutor.
     */
    @NonNull
    public static ListeningExecutorService getBackgroundExecutor() {
        return sBackgroundExecutor;
    }

    private static final ListeningExecutorService sBlockingExecutor =
            MoreExecutors.listeningDecorator(
                    Executors.newCachedThreadPool());

    /**
     * Functions that directly execute network I/O, or that block their thread awaiting the
     * progress of at least one other thread, must run on BlockingExecutor.
     *
     * BlockingExecutor will launch as many threads as there are tasks available to run
     * concurrently, stopping and freeing them when the concurrent task count drops again. This
     * unbounded number of threads negatively impacts performance:
     *
     * Extra threads add execution overhead and increase execution latency.
     * Each thread consumes significant memory for thread-local state and stack, and may increase
     * the total amount of space used by objects on the heap.
     * Each additional BlockingExecutor thread reduces the time available to the fixed-size
     * LightweightExecutor and BackgroundExecutor. While BlockingExecutor's threads have a
     * lower priority to decrease this impact, the extra threads can still compete for resources.
     * Always prefer to refactor a class or API to avoid blocking before falling back to using
     * the blocking Executor.
     */
    @NonNull
    public static ListeningExecutorService getBlockingExecutor() {
        return sBlockingExecutor;
    }

    private AdServicesExecutors() {}
}

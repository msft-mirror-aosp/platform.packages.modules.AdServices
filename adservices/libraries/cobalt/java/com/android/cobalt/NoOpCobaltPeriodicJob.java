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

package com.android.cobalt;

import android.annotation.NonNull;
import android.util.Log;

import com.google.common.util.concurrent.Futures;
import com.google.common.util.concurrent.ListenableFuture;

import java.util.Objects;
import java.util.concurrent.ExecutorService;

/** Cobalt periodic job implementation which asynchronously logs that it was called */
public class NoOpCobaltPeriodicJob implements CobaltPeriodicJob {
    private static final String NOOP_TAG = "cobalt.noop";

    private final ExecutorService mExecutorService;

    public NoOpCobaltPeriodicJob(@NonNull ExecutorService executor) {
        Objects.requireNonNull(executor);
        this.mExecutorService = executor;
    }

    /**
     * Writes a log message from an asynchronous task indicating the method was called.
     *
     * @return A ListenableFuture for the logging operation.
     */
    @Override
    public ListenableFuture<Void> generateAggregatedObservations() {
        return Futures.submit(
                () -> {
                    if (Log.isLoggable(NOOP_TAG, Log.INFO)) {
                        Log.i(NOOP_TAG, "Call made to generate aggregated observations");
                    }
                },
                mExecutorService);
    }
}

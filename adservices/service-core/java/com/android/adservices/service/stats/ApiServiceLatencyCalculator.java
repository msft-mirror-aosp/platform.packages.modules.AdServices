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

package com.android.adservices.service.stats;

import android.adservices.common.CallerMetadata;
import android.annotation.NonNull;

import com.android.adservices.LogUtil;

import javax.annotation.concurrent.ThreadSafe;

/** Class for Api Service Latency Calculator. */
@ThreadSafe
public class ApiServiceLatencyCalculator {
    private final long mBinderElapsedTimestamp;
    private final long mStartElapsedTimestamp;
    private volatile long mStopElapsedTimestamp;
    private volatile boolean mRunning;
    private final Clock mClock;

    public ApiServiceLatencyCalculator(
            @NonNull CallerMetadata callerMetadata, @NonNull Clock clock) {
        mBinderElapsedTimestamp = callerMetadata.getBinderElapsedTimestamp();
        mClock = clock;
        mStartElapsedTimestamp = mClock.elapsedRealtime();
        mRunning = true;
        LogUtil.v("ApiServiceLatencyCalculator started.");
    }

    /**
     * Stops a {@link ApiServiceLatencyCalculator} instance from time calculation. If an instance is
     * not running, calling this method will do nothing.
     */
    private void stop() {
        if (!mRunning) {
            return;
        }
        synchronized (this) {
            if (!mRunning) {
                return;
            }
            mStopElapsedTimestamp = mClock.elapsedRealtime();
            mRunning = false;
            LogUtil.v("ApiServiceLatencyCalculator stopped.");
        }
    }

    /**
     * @return the elapsed timestamp since the system boots if the {@link
     *     ApiServiceLatencyCalculator} instance is still running, otherwise the timestamp when it
     *     was stopped.
     */
    private long getServiceElapsedTimestamp() {
        if (mRunning) {
            return mClock.elapsedRealtime();
        }
        return mStopElapsedTimestamp;
    }

    /**
     * @return the api service elapsed time latency since {@link ApiServiceLatencyCalculator} starts
     *     in milliseconds on the service side. This method will not stop the {@link
     *     ApiServiceLatencyCalculator} and should be used for getting intermediate stage latency of
     *     a API process.
     */
    public int getApiServiceElapsedLatencyMs() {
        return (int) (getServiceElapsedTimestamp() - mStartElapsedTimestamp);
    }

    /**
     * @return the api service overall latency since the {@link ApiServiceLatencyCalculator} starts
     *     in milliseconds without binder latency, on the server side. This method will stop the
     *     calculator if still running and the returned latency value will no longer change once the
     *     calculator is stopped. It should be used to get the complete process latency of an API
     *     within the server side.
     */
    public int getApiServiceInternalFinalLatencyMs() {
        stop();
        return getApiServiceElapsedLatencyMs();
    }

    /**
     * @return the approximate api service overall latency since the api is called at the client
     *     interface. This method will stop the {@link ApiServiceLatencyCalculator} if still running
     *     and the returned latency value will no longer change once the calculator is stopped. It
     *     should be used to get the complete process latency of an API.
     */
    public int getApiServiceOverallLatencyMs() {
        return (int)
                ((mStartElapsedTimestamp - mBinderElapsedTimestamp) * 2
                        + getApiServiceInternalFinalLatencyMs());
    }
}

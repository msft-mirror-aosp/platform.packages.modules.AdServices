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

package com.android.adservices.mockito;

import static com.android.dx.mockito.inline.extended.ExtendedMockito.doCallRealMethod;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyBoolean;
import static org.mockito.ArgumentMatchers.anyInt;
import static org.mockito.ArgumentMatchers.anyLong;
import static org.mockito.Mockito.doAnswer;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import android.app.job.JobParameters;
import android.app.job.JobService;
import android.content.Context;
import android.util.Log;

import com.android.adservices.common.NoFailureSyncCallback;
import com.android.adservices.common.synccallback.JobServiceLoggingCallback;
import com.android.adservices.service.Flags;
import com.android.adservices.service.stats.AdServicesLogger;
import com.android.adservices.service.stats.ApiCallStats;
import com.android.adservices.shared.common.ApplicationContextSingleton;
import com.android.adservices.spe.AdservicesJobServiceLogger;

/** Provides Mockito expectation for common calls. */
public final class MockitoExpectations {

    private static final String TAG = MockitoExpectations.class.getSimpleName();

    /**
     * Not a expectation itself, but it sets a mock as the application context on {@link
     * ApplicationContextSingleton}, and returns it.
     */
    public static Context setApplicationContextSingleton() {
        Context context = mock(Context.class);
        when(context.getApplicationContext()).thenReturn(context);

        ApplicationContextSingleton.setForTests(context);

        return context;
    }

    /**
     * Mocks a call to {@link AdServicesLogger#logApiCallStats(ApiCallStats)} and returns a callback
     * object that blocks until that call is made.
     */
    public static NoFailureSyncCallback<ApiCallStats> mockLogApiCallStats(
            AdServicesLogger adServicesLogger) {
        NoFailureSyncCallback<ApiCallStats> callback = new NoFailureSyncCallback<>();
        doAnswer(
                        inv -> {
                            Log.v(TAG, "mockLogApiCallStats(): inv=" + inv);
                            ApiCallStats apiCallStats = inv.getArgument(0);
                            callback.injectResult(apiCallStats);
                            return null;
                        })
                .when(adServicesLogger)
                .logApiCallStats(any());

        return callback;
    }

    /** Verifies methods in {@link AdservicesJobServiceLogger} were never called. */
    public static void verifyLoggingNotHappened(AdservicesJobServiceLogger logger) {
        // Mock logger to call actual public logging methods. Because when the feature flag of
        // logging is on, these methods are actually called, but the internal logging methods will
        // not be invoked.
        callRealPublicMethodsForBackgroundJobLogging(logger);

        verify(logger, never()).persistJobExecutionData(anyInt(), anyLong());
        verify(logger, never()).logExecutionStats(anyInt(), anyLong(), anyInt(), anyInt());
    }

    /** Verifies {@link AdservicesJobServiceLogger#recordJobSkipped(int, int)} is called once. */
    public static void verifyBackgroundJobsSkipLogged(
            AdservicesJobServiceLogger logger, JobServiceLoggingCallback callback)
            throws InterruptedException {
        callback.assertLoggingFinished();

        verify(logger).recordJobSkipped(anyInt(), anyInt());
    }

    /** Verifies the logging flow of a successful {@link JobService}'s execution is called once. */
    public static void verifyJobFinishedLogged(
            AdservicesJobServiceLogger logger,
            JobServiceLoggingCallback onStartJobCallback,
            JobServiceLoggingCallback onJobDoneCallback)
            throws InterruptedException {
        verifyOnStartJobLogged(logger, onStartJobCallback);
        verifyOnJobFinishedLogged(logger, onJobDoneCallback);
    }

    /**
     * Verifies {@link AdservicesJobServiceLogger#recordOnStopJob(JobParameters, int, boolean)} is
     * called once.
     */
    public static void verifyOnStopJobLogged(
            AdservicesJobServiceLogger logger, JobServiceLoggingCallback callback)
            throws InterruptedException {
        callback.assertLoggingFinished();

        verify(logger).recordOnStopJob(any(), anyInt(), anyBoolean());
    }

    /**
     * Mocks a call to {@link Flags#getBackgroundJobsLoggingKillSwitch()}, returning overrideValue.
     */
    public static void mockBackgroundJobsLoggingKillSwitch(Flags flag, boolean overrideValue) {
        when(flag.getBackgroundJobsLoggingKillSwitch()).thenReturn(overrideValue);
    }

    /**
     * Mock {@link AdservicesJobServiceLogger#persistJobExecutionData(int, long)} to wait for it to
     * complete.
     */
    public static JobServiceLoggingCallback syncPersistJobExecutionData(
            AdservicesJobServiceLogger logger) {
        JobServiceLoggingCallback callback = new JobServiceLoggingCallback();
        doAnswer(
                        invocation -> {
                            invocation.callRealMethod();
                            callback.onLoggingMethodCalled();
                            return null;
                        })
                .when(logger)
                .recordOnStartJob(anyInt());

        return callback;
    }

    /**
     * Mock one of below 3 endpoints for a {@link JobService}'s execution to wait for it to
     * complete.
     *
     * <ul>
     *   <li>{@link AdservicesJobServiceLogger#recordOnStopJob(JobParameters, int, boolean)}
     *   <li>{@link AdservicesJobServiceLogger#recordJobSkipped(int, int)}
     *   <li>{@link AdservicesJobServiceLogger#recordJobFinished(int, boolean, boolean)}
     * </ul>
     *
     * @throws IllegalStateException if there is more than one method is called.
     */
    public static JobServiceLoggingCallback syncLogExecutionStats(
            AdservicesJobServiceLogger logger) {
        JobServiceLoggingCallback callback = new JobServiceLoggingCallback();

        doAnswer(
                        invocation -> {
                            callback.onLoggingMethodCalled();
                            return null;
                        })
                .when(logger)
                .recordOnStopJob(any(), anyInt(), anyBoolean());

        doAnswer(
                        invocation -> {
                            callback.onLoggingMethodCalled();
                            return null;
                        })
                .when(logger)
                .recordJobSkipped(anyInt(), anyInt());

        doAnswer(
                        invocation -> {
                            callback.onLoggingMethodCalled();
                            return null;
                        })
                .when(logger)
                .recordJobFinished(anyInt(), anyBoolean(), anyBoolean());

        return callback;
    }

    /**
     * Verify the logging methods in {@link JobService#onStartJob(JobParameters)} has been invoked.
     */
    public static void verifyOnStartJobLogged(
            AdservicesJobServiceLogger logger, JobServiceLoggingCallback callback)
            throws InterruptedException {
        callback.assertLoggingFinished();

        verify(logger).recordOnStartJob(anyInt());
    }

    /**
     * Verify the logging methods in {@link JobService#jobFinished(JobParameters, boolean)} has been
     * invoked.
     */
    public static void verifyOnJobFinishedLogged(
            AdservicesJobServiceLogger logger, JobServiceLoggingCallback callback)
            throws InterruptedException {
        callback.assertLoggingFinished();

        verify(logger).recordJobFinished(anyInt(), anyBoolean(), anyBoolean());
    }

    private MockitoExpectations() {
        throw new UnsupportedOperationException("Provides only static methods");
    }

    private static void callRealPublicMethodsForBackgroundJobLogging(
            AdservicesJobServiceLogger logger) {
        doCallRealMethod().when(logger).recordOnStartJob(anyInt());
        doCallRealMethod().when(logger).recordOnStopJob(any(), anyInt(), anyBoolean());
        doCallRealMethod().when(logger).recordJobSkipped(anyInt(), anyInt());
        doCallRealMethod().when(logger).recordJobFinished(anyInt(), anyBoolean(), anyBoolean());
    }
}

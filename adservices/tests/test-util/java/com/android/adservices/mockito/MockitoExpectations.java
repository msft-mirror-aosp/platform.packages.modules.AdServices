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

import static com.android.adservices.service.stats.AdServicesStatsLog.AD_SERVICES_BACKGROUND_JOBS_EXECUTION_REPORTED__EXECUTION_RESULT_CODE__SKIP_FOR_KILL_SWITCH_ON;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyBoolean;
import static org.mockito.ArgumentMatchers.anyInt;
import static org.mockito.ArgumentMatchers.anyLong;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import android.app.job.JobParameters;
import android.content.Context;

import com.android.adservices.service.Flags;
import com.android.adservices.shared.common.ApplicationContextSingleton;
import com.android.adservices.spe.AdservicesJobServiceLogger;

import org.mockito.verification.VerificationMode;

/** Provides Mockito expectation for common calls. */
public final class MockitoExpectations {

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
     * Verifies {@link AdservicesJobServiceLogger#logExecutionStats(int, long, int, int)} was called
     * with the expected values, using Mockito's {@link VerificationMode} to set the number of times
     * (like {@code times(2)}) or {@code times(0)}).
     */
    public static void verifyBackgroundJobsLogging(
            AdservicesJobServiceLogger logger, VerificationMode mode) {
        verify(logger, mode).persistJobExecutionData(anyInt(), anyLong());
        verify(logger, mode).logExecutionStats(anyInt(), anyLong(), anyInt(), anyInt());
    }

    /** Verifies {@link AdservicesJobServiceLogger#recordJobSkipped(int, int)} is called once. */
    public static void verifyBackgroundJobsSkipLogged(AdservicesJobServiceLogger logger) {
        verify(logger).recordJobSkipped(anyInt(), anyInt());
        verify(logger).persistJobExecutionData(anyInt(), anyLong());
        verify(logger)
                .logExecutionStats(
                        anyInt(),
                        anyLong(),
                        eq(
                                AD_SERVICES_BACKGROUND_JOBS_EXECUTION_REPORTED__EXECUTION_RESULT_CODE__SKIP_FOR_KILL_SWITCH_ON),
                        anyInt());
    }

    /** Verifies {@link AdservicesJobServiceLogger#recordOnStartJob(int)} is called once. */
    public static void verifyJobFinishedLogged(AdservicesJobServiceLogger logger) {
        verify(logger).recordJobFinished(anyInt(), anyBoolean(), anyBoolean());
        verify(logger).recordOnStartJob(anyInt());
        verify(logger).persistJobExecutionData(anyInt(), anyLong());
        verify(logger).logExecutionStats(anyInt(), anyLong(), anyInt(), anyInt());
    }

    /**
     * Verifies {@link AdservicesJobServiceLogger#recordOnStopJob(JobParameters, int, boolean)} is
     * called once.
     */
    public static void verifyOnStopJobLogged(AdservicesJobServiceLogger logger) {
        verify(logger).recordOnStopJob(any(), anyInt(), anyBoolean());
        verify(logger).logExecutionStats(anyInt(), anyLong(), anyInt(), anyInt());
    }

    /**
     * Mocks a call to {@link Flags#getBackgroundJobsLoggingKillSwitch()}, returning overrideValue.
     */
    public static void mockBackgroundJobsLoggingKillSwitch(Flags flag, boolean overrideValue) {
        when(flag.getBackgroundJobsLoggingKillSwitch()).thenReturn(overrideValue);
    }

    private MockitoExpectations() {
        throw new UnsupportedOperationException("Provides only static methods");
    }
}

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

package com.android.adservices.shared.mockito;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyBoolean;
import static org.mockito.ArgumentMatchers.anyInt;
import static org.mockito.Mockito.doAnswer;

import android.app.job.JobParameters;
import android.app.job.JobService;

import com.android.adservices.common.synccallback.JobServiceLoggingCallback;
import com.android.adservices.shared.spe.logging.JobServiceLogger;
import com.android.adservices.shared.testing.JobServiceCallback;

/** A class contains common mocking methods used in unit tests. */
// TODO(b/324919960): Move it to the test-util package.
public final class MockitoExpectations {

    /**
     * Mock {@link JobService}'s execution to wait until {@link
     * JobService#jobFinished(JobParameters, boolean)} is called.
     *
     * @deprecated Use {@code new JobServiceCallback().expectJobFinished(JobService)}
     */
    @Deprecated
    public static JobServiceCallback syncJobServiceOnJobFinished(JobService jobService) {
        return new JobServiceCallback().expectJobFinished(jobService);
    }

    /**
     * Mock {@link JobService}'s execution to wait until {@link
     * JobServiceLogger#recordOnStopJob(JobParameters, int, boolean)} is called.
     */
    public static JobServiceLoggingCallback syncRecordOnStopJob(JobServiceLogger logger) {
        JobServiceLoggingCallback callback = new JobServiceLoggingCallback();

        doAnswer(
                        invocation -> {
                            callback.onLoggingMethodCalled();
                            return null;
                        })
                .when(logger)
                .recordOnStopJob(any(), anyInt(), anyBoolean());

        return callback;
    }
}

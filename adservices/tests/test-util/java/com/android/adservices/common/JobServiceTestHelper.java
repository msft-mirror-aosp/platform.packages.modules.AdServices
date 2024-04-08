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

package com.android.adservices.common;


import android.app.job.JobService;

import com.android.adservices.shared.testing.JobServiceCallback;

// TODO(b/296945680): Remove this class. Use JobServiceCallback directly.
/**
 * @deprecated Helper methods in this class are deprecated. Use {@code new
 *     JobServiceCallback().expectJobFinished(JobService)} or {@code new
 *     JobServiceCallback().expectJobStopped(JobService)} to create a new JobServiceCallback.
 */
@Deprecated
public final class JobServiceTestHelper {

    private static final String TAG = JobServiceTestHelper.class.getSimpleName();

    private JobServiceTestHelper() {
        throw new UnsupportedOperationException();
    }

    /** Creates callback for tests where jobFinished is invoked. */
    public static JobServiceCallback createJobFinishedCallback(JobService jobService) {
        return new JobServiceCallback().expectJobFinished(jobService);
    }

    /** Creates a callback for tests where onStopJob is invoked. */
    public static JobServiceCallback createOnStopJobCallback(JobService jobService) {
        return new JobServiceCallback().expectJobStopped(jobService);
    }
}

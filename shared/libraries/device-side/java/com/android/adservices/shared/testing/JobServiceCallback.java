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

package com.android.adservices.shared.testing;

import static com.android.adservices.shared.util.Preconditions.checkState;
import static com.android.dx.mockito.inline.extended.ExtendedMockito.doAnswer;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyBoolean;
import static org.mockito.Mockito.doAnswer;

import android.app.job.JobParameters;
import android.app.job.JobService;
import android.util.Log;

import com.android.dx.mockito.inline.extended.ExtendedMockito;

import com.google.common.base.Supplier;

/**
 * Helper class to create custom {@link SyncCallback} and assertions for each {@link JobService}
 * lifecycle.
 */
public final class JobServiceCallback {
    private JobFinishedCallback mJobFinishedCallback;
    private JobStoppedCallback mJobStoppedCallback;

    /** Sets the {@link JobFinishedCallback} with the given {@link JobService}. */
    public JobServiceCallback expectJobFinished(JobService jobService) {
        mJobFinishedCallback =
                setCallback(
                        "JobFinishedCallback",
                        mJobFinishedCallback,
                        () -> new JobFinishedCallback(jobService));
        return this;
    }

    /** Verifies the {@link JobService#jobFinished(JobParameters, boolean)} was called. */
    public void assertJobFinished() throws InterruptedException {
        assertCallbackCalled("JobFinishedCallback", mJobFinishedCallback);
    }

    /** Sets the {@link JobStoppedCallback} with the given {@link JobService}. */
    public JobServiceCallback expectJobStopped(JobService jobService) {
        mJobStoppedCallback =
                setCallback(
                        "JobStoppedCallback",
                        mJobStoppedCallback,
                        () -> new JobStoppedCallback(jobService));
        return this;
    }

    /** Verifies the {@link JobService#onStopJob(JobParameters)} was called. */
    public void assertJobStopped() throws InterruptedException {
        assertCallbackCalled("JobStoppedCallback", mJobStoppedCallback);
    }

    private <T extends SyncCallback> T setCallback(
            String callbackInstance, T callback, Supplier<T> callbackSupplier) {
        checkState(
                callback == null,
                "%s should not be initialized. Create a new JobServiceCallback instead.",
                callbackInstance);

        return callbackSupplier.get();
    }

    private void assertCallbackCalled(String callbackInstance, SyncCallback callback)
            throws InterruptedException {
        checkState(callbackInstance != null, "%s not set yet.", callbackInstance);

        callback.assertResultReceived();
    }

    /**
     * Custom {@link BooleanSyncCallback} implementation where used for checking methods in {@link
     * JobService#jobFinished(JobParameters, boolean)} is called or executed. This implementation
     * must only used in {@link JobFinishedCallback}.
     *
     * <p>Use a {@link Boolean} type as a place holder for received on success. This {@link Boolean}
     * is used for checking a method has been called when calling {@link #assertResultReceived()}
     */
    private static final class JobFinishedCallback extends BooleanSyncCallback {

        /**
         * Injects a boolean {@code true} as Result. This is used for checking a stub method is
         * called.
         */
        private void onJobFinished() {
            super.injectResult(true);
        }

        /**
         * Creates a {@link JobFinishedCallback} for tests where {@link
         * JobService#jobFinished(JobParameters, boolean)} is invoked.
         */
        private JobFinishedCallback(JobService jobService) {
            doAnswer(
                            unusedInvocation -> {
                                Log.d(
                                        TAG,
                                        "Calling callback.onJobFinished() on " + unusedInvocation);
                                this.onJobFinished();
                                return null;
                            })
                    .when(jobService)
                    .jobFinished(any(), anyBoolean());
        }
    }

    /**
     * Custom {@link BooleanSyncCallback} implementation where used for checking methods in {@link
     * JobService#onStopJob(JobParameters)} is called or executed. This implementation must only
     * used in {@link JobFinishedCallback}.
     *
     * <p>Use a {@link Boolean} type as a place holder for received on success. This {@link Boolean}
     * is used for checking a method has been called when calling {@link #assertResultReceived()}
     */
    private static class JobStoppedCallback extends BooleanSyncCallback {

        /**
         * Injects a boolean {@code false} as Result. This is used for checking a stub method is
         * called.
         */
        private void onJobStopped() {
            super.injectResult(false);
        }

        /**
         * Creates a {@link JobStoppedCallback} for tests where {@link
         * JobService#onStopJob(JobParameters)} is invoked.
         */
        private JobStoppedCallback(JobService jobService) {
            ExtendedMockito.doAnswer(
                            invocation -> {
                                Log.d(TAG, "Calling callback.onJobStopped() on " + invocation);
                                invocation.callRealMethod();
                                this.onJobStopped();
                                return null;
                            })
                    .when(jobService)
                    .onStopJob(any());
        }
    }
}

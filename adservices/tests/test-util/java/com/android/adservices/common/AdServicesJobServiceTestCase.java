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

import static com.android.dx.mockito.inline.extended.ExtendedMockito.doCallRealMethod;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyBoolean;
import static org.mockito.ArgumentMatchers.anyInt;
import static org.mockito.ArgumentMatchers.anyLong;
import static org.mockito.Mockito.doAnswer;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;

import android.app.job.JobParameters;
import android.app.job.JobService;
import android.content.Context;

import com.android.adservices.common.AdServicesJobServiceTestCase.Mocker;
import com.android.adservices.mockito.AdServicesExtendedMockitoRule;
import com.android.adservices.mockito.AdServicesJobMocker;
import com.android.adservices.mockito.AdServicesMockitoJobMocker;
import com.android.adservices.mockito.StaticClassChecker;
import com.android.adservices.service.DebugFlags;
import com.android.adservices.service.Flags;
import com.android.adservices.shared.spe.logging.JobSchedulingLogger;
import com.android.adservices.shared.testing.JobServiceLoggingCallback;
import com.android.adservices.spe.AdServicesJobServiceFactory;
import com.android.adservices.spe.AdServicesJobServiceLogger;

import com.google.common.annotations.VisibleForTesting;

/** Base class for tests that exercise {@code JobService} implementations. */
public abstract class AdServicesJobServiceTestCase
        extends AdServicesMockerLessExtendedMockitoTestCase<Mocker> {

    @Override
    protected final Mocker newMocker(
            AdServicesExtendedMockitoRule rule, Flags mockFlags, DebugFlags mockDebugFlags) {
        return new Mocker(rule, mockFlags, mockDebugFlags);
    }

    // TODO(b/296945680): methods below were moved "as is" from MockitoExpectations. They should
    // be refactored to use a rule (like AdServicesLoggingUsageRule) or moved to
    // AdServicesJobMocker; regardless of the choice, they should be unit tested

    /** Verifies methods in {@link AdServicesJobServiceLogger} were never called. */
    protected final void verifyLoggingNotHappened(AdServicesJobServiceLogger logger) {
        // Mock logger to call actual public logging methods. Because when the feature flag of
        // logging is on, these methods are actually called, but the internal logging methods will
        // not be invoked.
        callRealPublicMethodsForBackgroundJobLogging(logger);

        verify(logger, never()).persistJobExecutionData(anyInt(), anyLong());
        verify(logger, never()).logExecutionStats(anyInt(), anyLong(), anyInt(), anyInt());
    }

    /** Verifies {@link AdServicesJobServiceLogger#recordJobSkipped(int, int)} is called once. */
    protected final void verifyBackgroundJobsSkipLogged(
            AdServicesJobServiceLogger logger, JobServiceLoggingCallback callback)
            throws InterruptedException {
        callback.assertLoggingFinished();

        verify(logger).recordJobSkipped(anyInt(), anyInt());
    }

    /** Verifies the logging flow of a successful {@link JobService}'s execution is called once. */
    protected final void verifyJobFinishedLogged(
            AdServicesJobServiceLogger logger,
            JobServiceLoggingCallback onStartJobCallback,
            JobServiceLoggingCallback onJobDoneCallback)
            throws InterruptedException {
        verifyOnStartJobLogged(logger, onStartJobCallback);
        verifyOnJobFinishedLogged(logger, onJobDoneCallback);
    }

    /**
     * Verifies {@link AdServicesJobServiceLogger#recordOnStopJob(JobParameters, int, boolean)} is
     * called once.
     */
    protected final void verifyOnStopJobLogged(
            AdServicesJobServiceLogger logger, JobServiceLoggingCallback callback)
            throws InterruptedException {
        callback.assertLoggingFinished();

        verify(logger).recordOnStopJob(any(), anyInt(), anyBoolean());
    }

    /**
     * Mock {@link AdServicesJobServiceLogger#persistJobExecutionData(int, long)} to wait for it to
     * complete.
     */
    protected final JobServiceLoggingCallback syncPersistJobExecutionData(
            AdServicesJobServiceLogger logger) {
        JobServiceLoggingCallback callback = new JobServiceLoggingCallback();
        doAnswer(
                        invocation -> {
                            mLog.v("%s", invocation);
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
     *   <li>{@link AdServicesJobServiceLogger#recordOnStopJob(JobParameters, int, boolean)}
     *   <li>{@link AdServicesJobServiceLogger#recordJobSkipped(int, int)}
     *   <li>{@link AdServicesJobServiceLogger#recordJobFinished(int, boolean, boolean)}
     * </ul>
     *
     * @throws IllegalStateException if there is more than one method is called.
     */
    protected final JobServiceLoggingCallback syncLogExecutionStats(
            AdServicesJobServiceLogger logger) {
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
    protected final void verifyOnStartJobLogged(
            AdServicesJobServiceLogger logger, JobServiceLoggingCallback callback)
            throws InterruptedException {
        callback.assertLoggingFinished();

        verify(logger).recordOnStartJob(anyInt());
    }

    /**
     * Verify the logging methods in {@link JobService#jobFinished(JobParameters, boolean)} has been
     * invoked.
     */
    protected final void verifyOnJobFinishedLogged(
            AdServicesJobServiceLogger logger, JobServiceLoggingCallback callback)
            throws InterruptedException {
        callback.assertLoggingFinished();

        verify(logger).recordJobFinished(anyInt(), anyBoolean(), anyBoolean());
    }

    private static void callRealPublicMethodsForBackgroundJobLogging(
            AdServicesJobServiceLogger logger) {
        doCallRealMethod().when(logger).recordOnStartJob(anyInt());
        doCallRealMethod().when(logger).recordOnStopJob(any(), anyInt(), anyBoolean());
        doCallRealMethod().when(logger).recordJobSkipped(anyInt(), anyInt());
        doCallRealMethod().when(logger).recordJobFinished(anyInt(), anyBoolean(), anyBoolean());
    }

    public static final class Mocker
            extends AdServicesMockerLessExtendedMockitoTestCase.InternalMocker
            implements AdServicesJobMocker {

        private final AdServicesJobMocker mJobMocker;

        @VisibleForTesting
        Mocker(StaticClassChecker checker, Flags mockFlags, DebugFlags mockDebugFlags) {
            super(checker, mockFlags, mockDebugFlags);
            mJobMocker = new AdServicesMockitoJobMocker(checker);
        }

        @Override
        public JobSchedulingLogger mockJobSchedulingLogger(AdServicesJobServiceFactory factory) {
            return mJobMocker.mockJobSchedulingLogger(factory);
        }

        @Override
        public AdServicesJobServiceLogger getSpiedAdServicesJobServiceLogger(
                Context context, Flags flags) {
            return mJobMocker.getSpiedAdServicesJobServiceLogger(context, flags);
        }

        @Override
        public void mockGetAdServicesJobServiceLogger(AdServicesJobServiceLogger logger) {
            mJobMocker.mockGetAdServicesJobServiceLogger(logger);
        }

        @Override
        public AdServicesJobServiceLogger mockNoOpAdServicesJobServiceLogger(
                Context context, Flags flags) {
            return mJobMocker.mockNoOpAdServicesJobServiceLogger(context, flags);
        }
    }
}

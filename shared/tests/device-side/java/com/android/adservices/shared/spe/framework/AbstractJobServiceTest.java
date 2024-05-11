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

package com.android.adservices.shared.spe.framework;

import static com.android.adservices.service.stats.AdServicesStatsLog.AD_SERVICES_ERROR_REPORTED__ERROR_CODE__SPE_JOB_EXECUTION_FAILURE;
import static com.android.adservices.service.stats.AdServicesStatsLog.AD_SERVICES_ERROR_REPORTED__ERROR_CODE__SPE_JOB_ON_STOP_EXECUTION_FAILURE;
import static com.android.adservices.service.stats.AdServicesStatsLog.AD_SERVICES_ERROR_REPORTED__PPAPI_NAME__COMMON;
import static com.android.adservices.shared.spe.JobServiceConstants.JOB_ENABLED_STATUS_DISABLED_FOR_KILL_SWITCH_ON;
import static com.android.adservices.shared.spe.JobServiceConstants.JOB_ENABLED_STATUS_ENABLED;
import static com.android.adservices.shared.spe.JobServiceConstants.SKIP_REASON_JOB_NOT_CONFIGURED;
import static com.android.adservices.shared.spe.framework.ExecutionResult.FAILURE_WITHOUT_RETRY;
import static com.android.adservices.shared.spe.framework.ExecutionResult.FAILURE_WITH_RETRY;
import static com.android.adservices.shared.spe.framework.ExecutionResult.SUCCESS;
import static com.android.adservices.shared.spe.framework.TestJobServiceFactory.JOB_ID_1;
import static com.android.adservices.shared.spe.framework.TestJobServiceFactory.JOB_NAME_1;
import static com.android.dx.mockito.inline.extended.ExtendedMockito.doNothing;
import static com.android.dx.mockito.inline.extended.ExtendedMockito.doReturn;

import static com.google.common.truth.Truth.assertWithMessage;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyBoolean;
import static org.mockito.ArgumentMatchers.anyInt;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.doAnswer;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import android.app.job.JobInfo;
import android.app.job.JobParameters;
import android.app.job.JobScheduler;
import android.content.ComponentName;

import com.android.adservices.shared.SharedMockitoTestCase;
import com.android.adservices.shared.errorlogging.AdServicesErrorLogger;
import com.android.adservices.shared.proto.ModuleJobPolicy;
import com.android.adservices.shared.spe.logging.JobSchedulingLogger;
import com.android.adservices.shared.spe.logging.JobServiceLogger;
import com.android.adservices.shared.spe.scheduling.BackoffPolicy;
import com.android.adservices.shared.testing.JobServiceCallback;
import com.android.adservices.shared.testing.JobServiceLoggingCallback;

import com.google.common.util.concurrent.Futures;
import com.google.common.util.concurrent.ListenableFuture;

import org.junit.After;
import org.junit.Before;
import org.junit.Test;
import org.mockito.Mock;
import org.mockito.Spy;

import java.util.concurrent.CancellationException;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.TimeUnit;

/** Unit test for {@link AbstractJobService}. */
public final class AbstractJobServiceTest extends SharedMockitoTestCase {
    // This is used to schedule a job with a latency before its execution. Use a large value so that
    // the job can NOT execute.
    private static final int EXECUTION_CANNOT_START_LATENCY_MS = 24 * 60 * 60 * 1000;
    private static final int FUTURE_TIMEOUT_MS = 5_000;
    private static final ModuleJobPolicy MODULE_JOB_POLICY = ModuleJobPolicy.getDefaultInstance();

    private final JobScheduler mJobScheduler = sContext.getSystemService(JobScheduler.class);
    private TestJobServiceFactory mFactory;

    @Spy private TestJobService mSpyJobService;
    @Mock private JobServiceLogger mMockLogger;
    @Mock private JobWorker mMockJobWorker;
    @Mock private JobParameters mMockParameters;
    @Mock private AdServicesErrorLogger mMockErrorLogger;
    @Mock private JobSchedulingLogger mMockJobSchedulingLogger;
    @Mock private BackoffPolicy mMockBackoffPolicy;

    @Before
    public void setup() {
        // Mock JobScheduler for the tested JobService.
        assertWithMessage("The JobScheduler").that(mJobScheduler).isNotNull();
        doReturn(mJobScheduler).when(mSpyJobService).getSystemService(JobScheduler.class);

        // Set JobServiceConfig for the TestJobService.
        mFactory =
                new TestJobServiceFactory(
                        mMockJobWorker,
                        mMockLogger,
                        MODULE_JOB_POLICY,
                        mMockErrorLogger,
                        mMockJobSchedulingLogger);
        doReturn(mFactory).when(mSpyJobService).getJobServiceFactory();
        mSpyJobService.onCreate();

        when(mMockParameters.getJobId()).thenReturn(JOB_ID_1);
    }

    @After
    public void teardown() {
        mJobScheduler.cancelAll();

        // TODO(b/326150593): Remove assertion in @After.
        assertWithMessage("Pending job %s at the beginning of the test", JOB_ID_1)
                .that(mJobScheduler.getPendingJob(JOB_ID_1))
                .isNull();
    }

    @Test
    public void testOnStartJob_jobNotConfigured() {
        int nonExistedId = 10000;
        doReturn(nonExistedId).when(mMockParameters).getJobId();
        doNothing().when(mSpyJobService).skipForJobInfoNotConfigured(mMockParameters);

        assertWithMessage("The incorrect Job configuration's execution succeeding")
                .that(mSpyJobService.onStartJob(mMockParameters))
                .isFalse();

        verify(mSpyJobService).skipForJobInfoNotConfigured(mMockParameters);
        verify(mMockLogger).recordOnStartJob(nonExistedId);
    }

    @Test
    public void testOnStartJob_nullJob() {
        doReturn(null).when(mSpyJobService).getJobWorker(any(), anyInt(), anyString());

        assertWithMessage("The incorrect Job configuration's execution succeeding")
                .that(mSpyJobService.onStartJob(mMockParameters))
                .isFalse();

        verify(mMockLogger).recordOnStartJob(JOB_ID_1);
    }

    @Test
    public void testOnStartJob_success() throws Exception {
        doReturn(Futures.immediateFuture(SUCCESS))
                .when(mMockJobWorker)
                .getExecutionFuture(
                        eq(mSpyJobService), any()); // PersistableBundle doesn't implement equals.
        doReturn(JOB_ENABLED_STATUS_ENABLED).when(mMockJobWorker).getJobEnablementStatus();
        JobServiceCallback callback = new JobServiceCallback().expectJobFinished(mSpyJobService);

        assertWithMessage("The execution succeeding")
                .that(mSpyJobService.onStartJob(mMockParameters))
                .isTrue();

        callback.assertJobFinished();
        verify(mMockLogger).recordOnStartJob(JOB_ID_1);
        verify(mMockLogger).recordJobFinished(JOB_ID_1, SUCCESS);
    }

    @Test
    public void testOnStartJob_failure_shouldNotRetry() throws Exception {
        testOnStartJob_failure(/* shouldRetry= */ false);
    }

    @Test
    public void testOnStartJob_failure_shouldRetry() throws Exception {
        testOnStartJob_failure(/* shouldRetry= */ true);
    }

    @Test
    public void testOnStopJob_shouldNotRetry_stopFutureSuccess() throws Exception {
        testOnStopJob(/* shouldRetry= */ false, /* shouldStopFutureThrow= */ false);
    }

    @Test
    public void testOnStopJob_shouldNotRetry_stopFutureThrow() throws Exception {
        testOnStopJob(/* shouldRetry= */ false, /* shouldStopFutureThrow= */ true);
    }

    @Test
    public void testOnStopJob_shouldRetry() throws Exception {
        testOnStopJob(/* shouldRetry= */ true, /* shouldStopFutureThrow= */ false);
    }

    @Test
    public void testSkipAndCancelBackgroundJob() throws Exception {
        JobServiceCallback callback = new JobServiceCallback().expectJobFinished(mSpyJobService);

        JobInfo jobInfo =
                new JobInfo.Builder(JOB_ID_1, new ComponentName(sContext, TestJobService.class))
                        .setMinimumLatency(EXECUTION_CANNOT_START_LATENCY_MS)
                        .build();
        mJobScheduler.schedule(jobInfo);

        assertWithMessage("Scheduled job with id=%s", JOB_ID_1)
                .that(mJobScheduler.getPendingJob(JOB_ID_1))
                .isNotNull();

        int skipReason = JOB_ENABLED_STATUS_DISABLED_FOR_KILL_SWITCH_ON;
        mSpyJobService.skipAndCancelBackgroundJob(mMockParameters, skipReason);

        callback.assertJobFinished();

        assertWithMessage("Scheduled job with id=%s", JOB_ID_1)
                .that(mJobScheduler.getPendingJob(JOB_ID_1))
                .isNull();
        verify(mMockLogger).recordJobSkipped(JOB_ID_1, skipReason);
    }

    @Test
    public void testGetJobWorker() {
        doReturn(JOB_ENABLED_STATUS_ENABLED).when(mMockJobWorker).getJobEnablementStatus();

        assertWithMessage("Getting Job %s ", JOB_NAME_1)
                .that(mSpyJobService.getJobWorker(mMockParameters, JOB_ID_1, JOB_NAME_1))
                .isSameInstanceAs(mMockJobWorker);
    }

    @Test
    public void testGetJobWorker_jobIsNull() {
        int nonExistedId = 10000;
        doNothing().when(mSpyJobService).skipForJobInfoNotConfigured(mMockParameters);

        assertWithMessage("Getting Job %s ", JOB_NAME_1)
                .that(mSpyJobService.getJobWorker(mMockParameters, nonExistedId, JOB_NAME_1))
                .isNull();

        verify(mSpyJobService).skipForJobInfoNotConfigured(mMockParameters);
    }

    @Test
    public void testGetJobWorker_disabled() {
        int skipReason = JOB_ENABLED_STATUS_DISABLED_FOR_KILL_SWITCH_ON;
        doReturn(skipReason).when(mMockJobWorker).getJobEnablementStatus();
        doNothing().when(mSpyJobService).skipAndCancelBackgroundJob(mMockParameters, skipReason);

        assertWithMessage("Getting Job %s ", JOB_NAME_1)
                .that(mSpyJobService.getJobWorker(mMockParameters, JOB_ID_1, JOB_NAME_1))
                .isNull();

        verify(mSpyJobService).skipAndCancelBackgroundJob(mMockParameters, skipReason);
    }

    @Test
    public void testSkipForJobInfoNotConfigured() {
        doNothing()
                .when(mSpyJobService)
                .skipAndCancelBackgroundJob(mMockParameters, SKIP_REASON_JOB_NOT_CONFIGURED);

        mSpyJobService.skipForJobInfoNotConfigured(mMockParameters);

        verify(mSpyJobService)
                .skipAndCancelBackgroundJob(mMockParameters, SKIP_REASON_JOB_NOT_CONFIGURED);
    }

    @Test
    public void testOnJobPostExecution_cancelled() throws Exception {
        doNothing().when(mSpyJobService).jobFinished(any(), anyBoolean());

        // TODO(b/331285831): Decide how to use future in test. Also for below methods calling
        // onJobPostExecution().
        ListenableFuture<Void> unusedFuture =
                mSpyJobService.onJobPostExecution(
                        Futures.immediateFailedFuture(new CancellationException()),
                        JOB_ID_1,
                        JOB_NAME_1,
                        mMockParameters,
                        mMockBackoffPolicy);
        unusedFuture.get(FUTURE_TIMEOUT_MS, TimeUnit.MILLISECONDS);

        // Verify both logging and retry doesn't happen.
        verify(mMockLogger, never()).recordJobFinished(anyInt(), any());
        verify(mSpyJobService).jobFinished(mMockParameters, /* wantsReschedule= */ false);
    }

    @Test
    public void testOnJobPostExecution_onException_WithRetry() throws Exception {
        doNothing().when(mSpyJobService).jobFinished(any(), anyBoolean());
        RuntimeException exception = new RuntimeException("The execution is failed!");
        when(mMockBackoffPolicy.shouldRetryOnExecutionFailure()).thenReturn(true);

        ListenableFuture<Void> unusedFuture =
                mSpyJobService.onJobPostExecution(
                        Futures.immediateFailedFuture(exception),
                        JOB_ID_1,
                        JOB_NAME_1,
                        mMockParameters,
                        mMockBackoffPolicy);
        unusedFuture.get(FUTURE_TIMEOUT_MS, TimeUnit.MILLISECONDS);

        verify(mMockErrorLogger)
                .logErrorWithExceptionInfo(
                        exception,
                        AD_SERVICES_ERROR_REPORTED__ERROR_CODE__SPE_JOB_EXECUTION_FAILURE,
                        AD_SERVICES_ERROR_REPORTED__PPAPI_NAME__COMMON);
        verify(mMockLogger).recordJobFinished(JOB_ID_1, FAILURE_WITH_RETRY);
        verify(mSpyJobService).jobFinished(mMockParameters, /* wantsReschedule= */ true);
    }

    @Test
    public void testOnJobPostExecution_onException_WithoutRetry() throws Exception {
        doNothing().when(mSpyJobService).jobFinished(any(), anyBoolean());
        RuntimeException exception = new RuntimeException("The execution is failed!");
        when(mMockBackoffPolicy.shouldRetryOnExecutionFailure()).thenReturn(false);

        ListenableFuture<Void> unusedFuture =
                mSpyJobService.onJobPostExecution(
                        Futures.immediateFailedFuture(exception),
                        JOB_ID_1,
                        JOB_NAME_1,
                        mMockParameters,
                        mMockBackoffPolicy);
        unusedFuture.get(FUTURE_TIMEOUT_MS, TimeUnit.MILLISECONDS);

        verify(mMockErrorLogger)
                .logErrorWithExceptionInfo(
                        exception,
                        AD_SERVICES_ERROR_REPORTED__ERROR_CODE__SPE_JOB_EXECUTION_FAILURE,
                        AD_SERVICES_ERROR_REPORTED__PPAPI_NAME__COMMON);
        verify(mMockLogger).recordJobFinished(JOB_ID_1, FAILURE_WITHOUT_RETRY);
        verify(mSpyJobService).jobFinished(mMockParameters, /* wantsReschedule= */ false);
    }

    @Test
    public void testOnJobPostExecution_notOnException_success() throws Exception {
        testOnJobPostExecution_notOnException(SUCCESS);
    }

    @Test
    public void testOnJobPostExecution_notOnException_failureWithRetry() throws Exception {
        testOnJobPostExecution_notOnException(FAILURE_WITH_RETRY);
    }

    @Test
    public void testOnJobPostExecution_notOnException_failureWithoutRetry() throws Exception {
        testOnJobPostExecution_notOnException(FAILURE_WITHOUT_RETRY);
    }

    private void testOnStartJob_failure(boolean shouldRetry) throws Exception {
        RuntimeException exception = new RuntimeException("The execution is failed!");
        doReturn(Futures.immediateFailedFuture(exception))
                .when(mMockJobWorker)
                .getExecutionFuture(
                        eq(mSpyJobService), any()); // PersistableBundle doesn't implement equals.
        doReturn(JOB_ENABLED_STATUS_ENABLED).when(mMockJobWorker).getJobEnablementStatus();
        doReturn(new BackoffPolicy.Builder().setShouldRetryOnExecutionFailure(shouldRetry).build())
                .when(mMockJobWorker)
                .getBackoffPolicy();
        JobServiceCallback callback = new JobServiceCallback().expectJobFinished(mSpyJobService);

        assertWithMessage("Job Execution for job with id=%s", JOB_ID_1)
                .that(mSpyJobService.onStartJob(mMockParameters))
                .isTrue();

        callback.assertJobFinished();
        verify(mMockLogger).recordOnStartJob(JOB_ID_1);
        verify(mMockLogger)
                .recordJobFinished(
                        JOB_ID_1, shouldRetry ? FAILURE_WITH_RETRY : FAILURE_WITHOUT_RETRY);
        verify(mMockErrorLogger)
                .logErrorWithExceptionInfo(
                        exception,
                        AD_SERVICES_ERROR_REPORTED__ERROR_CODE__SPE_JOB_EXECUTION_FAILURE,
                        AD_SERVICES_ERROR_REPORTED__PPAPI_NAME__COMMON);
    }

    private void testOnStopJob(boolean shouldRetry, boolean shouldStopFutureThrow)
            throws Exception {
        RuntimeException exception = new RuntimeException("The execution is failed!");

        doReturn(new BackoffPolicy.Builder().setShouldRetryOnExecutionStop(shouldRetry).build())
                .when(mMockJobWorker)
                .getBackoffPolicy();
        doReturn(JOB_ENABLED_STATUS_ENABLED).when(mMockJobWorker).getJobEnablementStatus();

        // Mock Execution stop future.
        CountDownLatch countDownLatch = new CountDownLatch(1);
        ListenableFuture<Void> stopFuture =
                Futures.submit(
                        () -> {
                            if (shouldStopFutureThrow) {
                                throw exception;
                            } else {
                                countDownLatch.countDown();
                            }
                        },
                        mFactory.getBackgroundExecutor());
        doAnswer(
                        invocation -> {
                            countDownLatch.countDown();
                            return null;
                        })
                .when(mMockErrorLogger)
                .logErrorWithExceptionInfo(
                        exception,
                        AD_SERVICES_ERROR_REPORTED__ERROR_CODE__SPE_JOB_ON_STOP_EXECUTION_FAILURE,
                        AD_SERVICES_ERROR_REPORTED__PPAPI_NAME__COMMON);
        doReturn(stopFuture)
                .when(mMockJobWorker)
                .getExecutionStopFuture(
                        eq(mSpyJobService), any()); // PersistableBundle doesn't implement equals.

        // Mock the execution future created from onStartJob() and keep it running for a while.
        ListenableFuture<ExecutionResult> mockRunningFuture =
                Futures.submit(
                        () -> {
                            sleep(
                                    60000,
                                    "Set a comparable long waiting time so that the future"
                                            + " won't finish, but it should still stop in case of"
                                            + " any issue.");
                            return SUCCESS;
                        },
                        mFactory.getBackgroundExecutor());
        mSpyJobService.mRunningFuturesMap.put(JOB_ID_1, mockRunningFuture);
        JobServiceLoggingCallback callback = mocker.syncRecordOnStopJob(mMockLogger);

        assertWithMessage("onStopJob()")
                .that(mSpyJobService.onStopJob(mMockParameters))
                .isEqualTo(shouldRetry);

        callback.assertLoggingFinished();
        assertWithMessage("The stop future's execution finishing")
                .that(countDownLatch.await(3, TimeUnit.SECONDS))
                .isTrue();
        assertWithMessage("The running future's cancellation")
                .that(mockRunningFuture.isCancelled())
                .isTrue();

        // Check CEL when the stop future throws.
        if (shouldStopFutureThrow) {
            verify(mMockErrorLogger)
                    .logErrorWithExceptionInfo(
                            exception,
                            AD_SERVICES_ERROR_REPORTED__ERROR_CODE__SPE_JOB_ON_STOP_EXECUTION_FAILURE,
                            AD_SERVICES_ERROR_REPORTED__PPAPI_NAME__COMMON);
        }
    }

    private void testOnJobPostExecution_notOnException(ExecutionResult result) throws Exception {
        doNothing().when(mSpyJobService).jobFinished(any(), anyBoolean());

        ListenableFuture<Void> unusedFuture =
                mSpyJobService.onJobPostExecution(
                        Futures.immediateFuture(result),
                        JOB_ID_1,
                        JOB_NAME_1,
                        mMockParameters,
                        mMockBackoffPolicy);
        unusedFuture.get(FUTURE_TIMEOUT_MS, TimeUnit.MILLISECONDS);

        verify(mMockLogger).recordJobFinished(JOB_ID_1, result);
        verify(mSpyJobService).jobFinished(mMockParameters, result.equals(FAILURE_WITH_RETRY));
    }
}

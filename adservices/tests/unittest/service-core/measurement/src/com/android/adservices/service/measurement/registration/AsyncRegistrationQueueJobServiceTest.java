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

package com.android.adservices.service.measurement.registration;

import static com.android.adservices.spe.AdServicesJobInfo.DEPRECATED_ASYNC_REGISTRATION_QUEUE_JOB;
import static com.android.adservices.spe.AdServicesJobInfo.MEASUREMENT_ASYNC_REGISTRATION_JOB;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertNotNull;
import static org.junit.Assert.assertNull;
import static org.junit.Assert.assertTrue;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyBoolean;
import static org.mockito.ArgumentMatchers.anyInt;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.atLeast;
import static org.mockito.Mockito.doAnswer;
import static org.mockito.Mockito.doNothing;
import static org.mockito.Mockito.doReturn;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.spy;
import static org.mockito.Mockito.timeout;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import android.app.job.JobInfo;
import android.app.job.JobScheduler;
import android.content.ComponentName;
import android.content.Context;

import androidx.test.core.app.ApplicationProvider;

import com.android.adservices.data.enrollment.EnrollmentDao;
import com.android.adservices.data.measurement.DatastoreManagerFactory;
import com.android.adservices.service.Flags;
import com.android.adservices.service.FlagsFactory;
import com.android.adservices.service.common.compat.ServiceCompatUtils;
import com.android.adservices.service.measurement.MeasurementJobServiceTestCase;
import com.android.adservices.service.measurement.reporting.DebugReportingJobService;
import com.android.adservices.service.stats.AdServicesLoggerImpl;
import com.android.adservices.shared.testing.JobServiceLoggingCallback;
import com.android.adservices.shared.testing.concurrency.JobServiceCallback;
import com.android.adservices.spe.AdServicesJobInfo;
import com.android.adservices.spe.AdServicesJobServiceLogger;
import com.android.compatibility.common.util.TestUtils;
import com.android.dx.mockito.inline.extended.ExtendedMockito;
import com.android.modules.utils.testing.ExtendedMockitoRule.MockStatic;
import com.android.modules.utils.testing.ExtendedMockitoRule.SpyStatic;

import org.junit.Before;
import org.junit.Test;
import org.mockito.ArgumentCaptor;
import org.mockito.Mock;
import org.mockito.internal.stubbing.answers.AnswersWithDelay;
import org.mockito.internal.stubbing.answers.CallsRealMethods;

import java.util.Optional;
import java.util.concurrent.ExecutionException;
import java.util.concurrent.TimeUnit;

@SpyStatic(AsyncRegistrationQueueJobService.class)
@SpyStatic(AdServicesLoggerImpl.class)
@SpyStatic(DatastoreManagerFactory.class)
@SpyStatic(EnrollmentDao.class)
@SpyStatic(FlagsFactory.class)
@SpyStatic(AdServicesJobServiceLogger.class)
@MockStatic(ServiceCompatUtils.class)
@MockStatic(DebugReportingJobService.class)
public final class AsyncRegistrationQueueJobServiceTest
        extends MeasurementJobServiceTestCase<AsyncRegistrationQueueJobService> {
    private static final int MEASUREMENT_ASYNC_REGISTRATION_JOB_ID =
            MEASUREMENT_ASYNC_REGISTRATION_JOB.getJobId();
    private static final long JOB_TRIGGER_MIN_DELAY_MS = TimeUnit.MINUTES.toMillis(2);
    private static final long JOB_TRIGGER_MAX_DELAY_MS = TimeUnit.MINUTES.toMillis(5);

    @Mock private JobInfo mMockJobInfo;

    @Override
    protected AsyncRegistrationQueueJobService getSpiedService() {
        return new AsyncRegistrationQueueJobService();
    }

    @Before
    public void setUp() {
        when(mMockFlags.getMeasurementAsyncRegistrationQueueJobPersisted()).thenReturn(false);
        when(mMockFlags.getMeasurementAsyncRegistrationQueueJobRequiredNetworkType())
                .thenReturn(JobInfo.NETWORK_TYPE_ANY);
        when(mMockFlags.getMeasurementAsyncRegistrationJobTriggerMinDelayMs())
                .thenReturn(JOB_TRIGGER_MIN_DELAY_MS);
        when(mMockFlags.getMeasurementAsyncRegistrationJobTriggerMaxDelayMs())
                .thenReturn(JOB_TRIGGER_MAX_DELAY_MS);
        when(mMockFlags.getMeasurementPrivacyEpsilon())
                .thenReturn(Flags.DEFAULT_MEASUREMENT_PRIVACY_EPSILON);
        when(mMockJobParameters.getJobId())
                .thenReturn(AdServicesJobInfo.MEASUREMENT_ASYNC_REGISTRATION_JOB.getJobId());
        when(mMockJobInfo.getTriggerContentUpdateDelay()).thenReturn(JOB_TRIGGER_MIN_DELAY_MS);
        when(mMockJobInfo.getTriggerContentMaxDelay()).thenReturn(JOB_TRIGGER_MAX_DELAY_MS);
        ExtendedMockito.doNothing()
                .when(() -> DebugReportingJobService.scheduleIfNeeded(any(), anyBoolean()));
    }

    @Test
    public void onStartJob_killSwitchOn_withLogging() throws Exception {
        runWithMocks(
                () -> {
                    JobServiceLoggingCallback callback = syncLogExecutionStats(mSpyLogger);

                    onStartJob_killSwitchOn();

                    verifyBackgroundJobsSkipLogged(mSpyLogger, callback);
                    ExtendedMockito.verify(
                            () -> DebugReportingJobService.scheduleIfNeeded(any(), eq(false)),
                            never());
                });
    }

    @Test
    public void onStartJob_killSwitchOff_withLogging() throws Exception {
        runWithMocks(
                () -> {
                    doReturn(
                                    AsyncRegistrationQueueRunner.ProcessingResult
                                            .SUCCESS_ALL_RECORDS_PROCESSED)
                            .when(mSpyService)
                            .processAsyncRecords();

                    onStartJob_killSwitchOff();

                    verify(mSpyLogger).recordOnStartJob(anyInt());
                    ExtendedMockito.verify(
                            () -> DebugReportingJobService.scheduleIfNeeded(any(), eq(false)));
                });
    }

    @Test
    public void onStartJob_killSwitchOff_unlockingCheck() throws Exception {
        runWithMocks(
                () -> {
                    // Setup
                    disableKillSwitch();
                    doReturn(
                                    AsyncRegistrationQueueRunner.ProcessingResult
                                            .SUCCESS_ALL_RECORDS_PROCESSED)
                            .when(mSpyService)
                            .processAsyncRecords();

                    // Execute
                    mSpyService.onStartJob(mMockJobParameters);
                    mSpyService.getFutureForTesting().get();

                    // Verify before executing again to make sure the lock has been unlocked
                    ExtendedMockito.verify(
                            () ->
                                    AsyncRegistrationQueueJobService.scheduleIfNeeded(
                                            any(), eq(true)));

                    boolean result = mSpyService.onStartJob(mMockJobParameters);
                    mSpyService.getFutureForTesting().get();

                    // Validate the job ran successfully twice
                    assertTrue(result);
                    verify(mSpyService, never()).jobFinished(any(), anyBoolean());
                    ExtendedMockito.verify(
                            () ->
                                    AsyncRegistrationQueueJobService.scheduleIfNeeded(
                                            any(), eq(true)),
                            times(2));
                    verify(mMockJobScheduler, never())
                            .cancel(eq(MEASUREMENT_ASYNC_REGISTRATION_JOB_ID));
                    ExtendedMockito.verify(
                            () -> DebugReportingJobService.scheduleIfNeeded(any(), eq(false)),
                            times(2));
                });
    }

    @Test
    public void onStartJob_shouldDisableJobTrue_withLoggingEnabled() throws Exception {
        runWithMocks(
                () -> {
                    mocker.mockGetFlags(mMockFlags);

                    onStartJob_shouldDisableJobTrue();

                    // Verify logging has not happened even though logging is enabled because this
                    // field is not logged
                    verifyLoggingNotHappened(mSpyLogger);
                    ExtendedMockito.verify(
                            () -> DebugReportingJobService.scheduleIfNeeded(any(), eq(false)),
                            never());
                });
    }

    @Test
    public void testRescheduling_threadInterruptedWhileProcessingRecords_dontRescheduleManually()
            throws Exception {
        runWithMocks(
                () -> {
                    // Setup
                    disableKillSwitch();

                    // Failure while processing records
                    ExtendedMockito.doReturn(
                                    AsyncRegistrationQueueRunner.ProcessingResult
                                            .THREAD_INTERRUPTED)
                            .when(mSpyService)
                            .processAsyncRecords();

                    // Execute
                    boolean result = mSpyService.onStartJob(mMockJobParameters);
                    mSpyService.getFutureForTesting().get();

                    // Validate, reschedule job with jobFinished
                    assertTrue(result);
                    verify(mSpyLogger)
                            .recordJobFinished(
                                    eq(MEASUREMENT_ASYNC_REGISTRATION_JOB_ID),
                                    /* isSuccessful= */ eq(false),
                                    /* shouldRetry= */ eq(true));
                    verify(mSpyService).jobFinished(any(), eq(/* wantsReschedule= */ true));
                    verify(mSpyService, never()).scheduleImmediately(any());
                    verify(mMockJobScheduler, never()).schedule(any());
                    ExtendedMockito.verify(
                            () -> DebugReportingJobService.scheduleIfNeeded(any(), eq(false)));
                });
    }

    @Test
    public void testRescheduling_successNoMoreRecordsToProcess_rescheduleManually()
            throws Exception {
        runWithMocks(
                () -> {
                    // Setup
                    disableKillSwitch();

                    // Successful processing
                    ExtendedMockito.doReturn(
                                    AsyncRegistrationQueueRunner.ProcessingResult
                                            .SUCCESS_ALL_RECORDS_PROCESSED)
                            .when(mSpyService)
                            .processAsyncRecords();

                    // Execute
                    boolean result = mSpyService.onStartJob(mMockJobParameters);
                    mSpyService.getFutureForTesting().get();

                    // Validate, do not reschedule with jobFinished, but reschedule manually
                    assertTrue(result);
                    verify(mSpyLogger)
                            .recordJobFinished(
                                    eq(MEASUREMENT_ASYNC_REGISTRATION_JOB_ID),
                                    /* isSuccessful= */ eq(true),
                                    /* shouldRetry= */ eq(false));
                    verify(mSpyService, never()).jobFinished(any(), anyBoolean());
                    ExtendedMockito.verify(
                            () ->
                                    AsyncRegistrationQueueJobService.scheduleIfNeeded(
                                            any(), eq(true)),
                            times(1));
                    verify(mSpyService, never()).scheduleImmediately(any());
                    ExtendedMockito.verify(
                            () -> DebugReportingJobService.scheduleIfNeeded(any(), eq(false)));
                });
    }

    @Test
    public void testRescheduling_hasMoreRecordsToProcess_rescheduleImmediately() throws Exception {
        runWithMocks(
                () -> {
                    // Setup
                    disableKillSwitch();
                    doNothing().when(mSpyService).scheduleImmediately(any());

                    // Pending records
                    ExtendedMockito.doReturn(
                                    AsyncRegistrationQueueRunner.ProcessingResult
                                            .SUCCESS_WITH_PENDING_RECORDS)
                            .when(mSpyService)
                            .processAsyncRecords();

                    // Execute
                    boolean result = mSpyService.onStartJob(mMockJobParameters);
                    mSpyService.getFutureForTesting().get();

                    // Validate, do not reschedule with jobFinished, but reschedule immediately
                    assertTrue(result);
                    verify(mSpyLogger)
                            .recordJobFinished(
                                    eq(MEASUREMENT_ASYNC_REGISTRATION_JOB_ID),
                                    /* isSuccessful= */ eq(true),
                                    /* shouldRetry= */ eq(true));
                    verify(mSpyService, never()).jobFinished(any(), anyBoolean());
                    verify(mSpyService).scheduleImmediately(any());
                    ExtendedMockito.verify(
                            () -> DebugReportingJobService.scheduleIfNeeded(any(), eq(false)));
                });
    }

    @Test
    public void testScheduleImmediately_killSwitchOff_rescheduleImmediately() {
        // Setup
        disableKillSwitch();
        Context context = mock(Context.class);
        doReturn(mMockJobScheduler).when(context).getSystemService(eq(JobScheduler.class));

        // Execute
        mSpyService.scheduleImmediately(context);

        // Validate jobInfo params to run immediately
        ArgumentCaptor<JobInfo> captorJobInfo = ArgumentCaptor.forClass(JobInfo.class);
        verify(mMockJobScheduler, times(1)).schedule(captorJobInfo.capture());
        JobInfo jobInfo = captorJobInfo.getValue();
        assertNotNull(jobInfo);
        assertNull(jobInfo.getTriggerContentUris());
        assertEquals(-1, jobInfo.getTriggerContentUpdateDelay());
        assertEquals(-1, jobInfo.getTriggerContentMaxDelay());
        assertEquals(JobInfo.NETWORK_TYPE_ANY, jobInfo.getNetworkType());
    }

    @Test
    public void testScheduleImmediately_killSwitchOn_dontReschedule() {
        // Setup
        enableKillSwitch();
        Context context = mock(Context.class);
        doReturn(mMockJobScheduler).when(context).getSystemService(eq(JobScheduler.class));

        // Execute
        mSpyService.scheduleImmediately(context);

        // Validate, job did not schedule
        verify(mMockJobScheduler, never()).schedule(any());
    }

    @Test
    public void scheduleIfNeeded_killSwitchOn_dontSchedule() throws Exception {
        runWithMocks(
                () -> {
                    // Setup
                    enableKillSwitch();

                    final Context mockContext = mock(Context.class);
                    doReturn(mMockJobScheduler)
                            .when(mockContext)
                            .getSystemService(JobScheduler.class);
                    final JobInfo mockJobInfo = mock(JobInfo.class);
                    doReturn(mockJobInfo)
                            .when(mMockJobScheduler)
                            .getPendingJob(eq(MEASUREMENT_ASYNC_REGISTRATION_JOB_ID));

                    // Execute
                    AsyncRegistrationQueueJobService.scheduleIfNeeded(
                            mockContext, /* forceSchedule= */ false);

                    // Validate
                    verify(mMockJobScheduler, never())
                            .getPendingJob(eq(MEASUREMENT_ASYNC_REGISTRATION_JOB_ID));
                    verify(mMockJobScheduler, never()).schedule(any());
                });
    }

    @Test
    public void scheduleIfNeeded_killSwitchOff_previouslyExecuted_dontForceSchedule_dontSchedule()
            throws Exception {
        runWithMocks(
                () -> {
                    // Setup
                    disableKillSwitch();

                    final Context spyContext = spy(ApplicationProvider.getApplicationContext());
                    doReturn(mMockJobScheduler)
                            .when(spyContext)
                            .getSystemService(JobScheduler.class);
                    final JobInfo mockJobInfo =
                            new JobInfo.Builder(
                                            MEASUREMENT_ASYNC_REGISTRATION_JOB_ID,
                                            new ComponentName(
                                                    spyContext,
                                                    AsyncRegistrationQueueJobService.class))
                                    .addTriggerContentUri(
                                            new JobInfo.TriggerContentUri(
                                                    AsyncRegistrationContentProvider
                                                            .getTriggerUri(),
                                                    JobInfo.TriggerContentUri
                                                            .FLAG_NOTIFY_FOR_DESCENDANTS))
                                    .setTriggerContentUpdateDelay(JOB_TRIGGER_MIN_DELAY_MS)
                                    .setTriggerContentMaxDelay(JOB_TRIGGER_MAX_DELAY_MS)
                                    .setRequiredNetworkType(JobInfo.NETWORK_TYPE_ANY)
                                    .setPersisted(false) // Can't call addTriggerContentUri() on a
                                    // persisted job
                                    .build();
                    doReturn(mockJobInfo)
                            .when(mMockJobScheduler)
                            .getPendingJob(eq(MEASUREMENT_ASYNC_REGISTRATION_JOB_ID));

                    // Execute
                    AsyncRegistrationQueueJobService.scheduleIfNeeded(
                            spyContext, /* forceSchedule= */ false);

                    // Validate
                    verify(mMockJobScheduler)
                            .getPendingJob(eq(MEASUREMENT_ASYNC_REGISTRATION_JOB_ID));
                });
    }

    @Test
    public void scheduleIfNeeded_diffJobInfo_doesSchedule() throws Exception {
        runWithMocks(
                () -> {
                    // Setup
                    disableKillSwitch();

                    final Context spyContext = spy(ApplicationProvider.getApplicationContext());
                    doReturn(mMockJobScheduler)
                            .when(spyContext)
                            .getSystemService(JobScheduler.class);
                    final JobInfo mockJobInfo =
                            new JobInfo.Builder(
                                            MEASUREMENT_ASYNC_REGISTRATION_JOB_ID,
                                            new ComponentName(
                                                    spyContext,
                                                    AsyncRegistrationQueueJobService.class))
                                    .addTriggerContentUri(
                                            new JobInfo.TriggerContentUri(
                                                    AsyncRegistrationContentProvider
                                                            .getTriggerUri(),
                                                    JobInfo.TriggerContentUri
                                                            .FLAG_NOTIFY_FOR_DESCENDANTS))
                                    // different
                                    .setTriggerContentUpdateDelay(JOB_TRIGGER_MIN_DELAY_MS - 1)
                                    .setTriggerContentMaxDelay(JOB_TRIGGER_MAX_DELAY_MS)
                                    .setRequiredNetworkType(JobInfo.NETWORK_TYPE_ANY)
                                    .setPersisted(false) // Can't call addTriggerContentUri() on a
                                    // persisted job
                                    .build();
                    doReturn(mockJobInfo)
                            .when(mMockJobScheduler)
                            .getPendingJob(eq(MEASUREMENT_ASYNC_REGISTRATION_JOB_ID));

                    // Execute
                    AsyncRegistrationQueueJobService.scheduleIfNeeded(
                            spyContext, /* forceSchedule= */ false);

                    // Validate
                    ExtendedMockito.verify(
                            () -> AsyncRegistrationQueueJobService.schedule(any(), any()));
                    verify(mMockJobScheduler)
                            .getPendingJob(eq(MEASUREMENT_ASYNC_REGISTRATION_JOB_ID));
                });
    }

    @Test
    public void scheduleIfNeeded_killSwitchOff_previouslyExecuted_forceSchedule_schedule()
            throws Exception {
        runWithMocks(
                () -> {
                    // Setup
                    disableKillSwitch();

                    final Context mockContext = mock(Context.class);
                    doReturn(mMockJobScheduler)
                            .when(mockContext)
                            .getSystemService(JobScheduler.class);
                    final JobInfo mockJobInfo = mock(JobInfo.class);
                    doReturn(mockJobInfo)
                            .when(mMockJobScheduler)
                            .getPendingJob(eq(MEASUREMENT_ASYNC_REGISTRATION_JOB_ID));

                    // Execute
                    AsyncRegistrationQueueJobService.scheduleIfNeeded(
                            mockContext, /* forceSchedule= */ true);

                    // Validate
                    ExtendedMockito.verify(
                            () -> AsyncRegistrationQueueJobService.schedule(any(), any()),
                            atLeast(1));
                    verify(mMockJobScheduler)
                            .getPendingJob(eq(MEASUREMENT_ASYNC_REGISTRATION_JOB_ID));
                });
    }

    @Test
    public void scheduleIfNeeded_killSwitchOff_previouslyNotExecuted_dontForceSchedule_schedule()
            throws Exception {
        runWithMocks(
                () -> {
                    // Setup
                    disableKillSwitch();

                    final Context mockContext = mock(Context.class);
                    doReturn(mMockJobScheduler)
                            .when(mockContext)
                            .getSystemService(JobScheduler.class);
                    // Mock the JobScheduler to have no pending job.
                    doReturn(null)
                            .when(mMockJobScheduler)
                            .getPendingJob(eq(MEASUREMENT_ASYNC_REGISTRATION_JOB_ID));

                    // Execute
                    AsyncRegistrationQueueJobService.scheduleIfNeeded(mockContext, false);

                    // Validate
                    ExtendedMockito.verify(
                            () -> AsyncRegistrationQueueJobService.schedule(any(), any()));
                    verify(mMockJobScheduler)
                            .getPendingJob(eq(MEASUREMENT_ASYNC_REGISTRATION_JOB_ID));
                });
    }

    @Test
    public void scheduleIfNeeded_minAndMaxDelayModified_scheduleAccordingly() throws Exception {
        runWithMocks(
                () -> {
                    // Setup
                    disableKillSwitch();
                    final Context mockContext = mock(Context.class);
                    doReturn(mMockJobScheduler)
                            .when(mockContext)
                            .getSystemService(JobScheduler.class);
                    // Mock the JobScheduler to have no pending job.
                    doReturn(null)
                            .when(mMockJobScheduler)
                            .getPendingJob(eq(MEASUREMENT_ASYNC_REGISTRATION_JOB_ID));

                    // Execute
                    AsyncRegistrationQueueJobService.scheduleIfNeeded(mockContext, false);

                    // Validate
                    ExtendedMockito.verify(
                            () -> AsyncRegistrationQueueJobService.schedule(any(), any()),
                            atLeast(1));
                    ArgumentCaptor<JobInfo> jobInfoArgumentCaptor =
                            ArgumentCaptor.forClass(JobInfo.class);
                    verify(mMockJobScheduler).schedule(jobInfoArgumentCaptor.capture());
                    JobInfo job = jobInfoArgumentCaptor.getValue();
                    assertEquals(JOB_TRIGGER_MIN_DELAY_MS, job.getTriggerContentUpdateDelay());
                    assertEquals(JOB_TRIGGER_MAX_DELAY_MS, job.getTriggerContentMaxDelay());
                });
    }

    @Test
    public void testOnStopJob_stopsExecutingThread() throws Exception {
        runWithMocks(
                () -> {
                    disableKillSwitch();

                    doAnswer(new AnswersWithDelay(50000, new CallsRealMethods()))
                            .when(mSpyService)
                            .processAsyncRecords();
                    mSpyService.onStartJob(mMockJobParameters);
                    Thread.sleep(5000);

                    assertNotNull(mSpyService.getFutureForTesting());

                    boolean onStopJobResult = mSpyService.onStopJob(mMockJobParameters);
                    verify(mSpyService, timeout(5000).times(0)).jobFinished(any(), anyBoolean());
                    assertTrue(onStopJobResult);
                    assertTrue(mSpyService.getFutureForTesting().isCancelled());
                });
    }

    @Test
    public void cancelDeprecatedAsyncRegistrationJob_withPendingJob_cancelsSuccessfully()
            throws Exception {
        runWithMocks(
                () -> {
                    // Setup
                    JobInfo mockLegacyJobInfo =
                            new JobInfo.Builder(
                                            DEPRECATED_ASYNC_REGISTRATION_QUEUE_JOB.getJobId(),
                                            new ComponentName(
                                                    mContext,
                                                    AsyncRegistrationQueueJobService.class))
                                    .setPeriodic(TimeUnit.MINUTES.toMillis(30))
                                    .setRequiredNetworkType(JobInfo.NETWORK_TYPE_ANY)
                                    .setPersisted(true)
                                    .build();
                    disableKillSwitch();
                    when(mMockJobScheduler.getPendingJob(
                                    DEPRECATED_ASYNC_REGISTRATION_QUEUE_JOB.getJobId()))
                            .thenReturn(mockLegacyJobInfo);

                    // Pending records
                    ExtendedMockito.doReturn(
                                    AsyncRegistrationQueueRunner.ProcessingResult
                                            .SUCCESS_WITH_PENDING_RECORDS)
                            .when(mSpyService)
                            .processAsyncRecords();

                    // Execute
                    boolean result = mSpyService.onStartJob(mMockJobParameters);
                    mSpyService.getFutureForTesting().get();

                    // Validate, do not reschedule with jobFinished, but reschedule immediately
                    assertTrue(result);
                    verify(mSpyLogger)
                            .recordJobFinished(
                                    eq(MEASUREMENT_ASYNC_REGISTRATION_JOB_ID),
                                    /* isSuccessful= */ eq(true),
                                    /* shouldRetry= */ eq(true));

                    verify(mMockJobScheduler)
                            .cancel(eq(DEPRECATED_ASYNC_REGISTRATION_QUEUE_JOB.getJobId()));
                });
    }

    @Test
    public void cancelDeprecatedAsyncRegistrationJob_withoutPendingJob_doesntCancel()
            throws Exception {
        runWithMocks(
                () -> {
                    // Setup
                    disableKillSwitch();
                    when(mMockJobScheduler.getPendingJob(
                                    DEPRECATED_ASYNC_REGISTRATION_QUEUE_JOB.getJobId()))
                            .thenReturn(null);

                    // Pending records
                    ExtendedMockito.doReturn(
                                    AsyncRegistrationQueueRunner.ProcessingResult
                                            .SUCCESS_ALL_RECORDS_PROCESSED)
                            .when(mSpyService)
                            .processAsyncRecords();

                    // Execute
                    boolean result = mSpyService.onStartJob(mMockJobParameters);
                    mSpyService.getFutureForTesting().get();

                    // Validate, do not reschedule with jobFinished, but reschedule immediately
                    assertTrue(result);
                    verify(mSpyLogger)
                            .recordJobFinished(
                                    eq(MEASUREMENT_ASYNC_REGISTRATION_JOB_ID),
                                    /* isSuccessful= */ eq(true),
                                    /* shouldRetry= */ eq(false));

                    verify(mMockJobScheduler, never()).cancel(anyInt());
                });
    }

    private void onStartJob_shouldDisableJobTrue() throws Exception {
        // Setup
        ExtendedMockito.doReturn(true)
                .when(
                        () ->
                                ServiceCompatUtils.shouldDisableExtServicesJobOnTPlus(
                                        any(Context.class)));

        JobServiceCallback callback = new JobServiceCallback().expectJobFinished(mSpyService);

        // Execute
        boolean result = mSpyService.onStartJob(mMockJobParameters);

        // Validate
        assertFalse(result);

        callback.assertJobFinished();
        verify(mSpyService).jobFinished(any(), eq(false));
        verify(mMockJobScheduler).cancel(eq(MEASUREMENT_ASYNC_REGISTRATION_JOB_ID));
    }

    private void onStartJob_killSwitchOn() throws ExecutionException, InterruptedException {
        // Setup
        enableKillSwitch();

        // Execute
        boolean result = mSpyService.onStartJob(mMockJobParameters);

        // Validate
        assertFalse(result);
        verify(mSpyService).jobFinished(any(), eq(false));
        verify(mMockJobScheduler).cancel(eq(MEASUREMENT_ASYNC_REGISTRATION_JOB_ID));
    }

    private void onStartJob_killSwitchOff() throws ExecutionException, InterruptedException {
        // Setup
        disableKillSwitch();
        ExtendedMockito.doNothing().when(() -> mSpyService.scheduleIfNeeded(any(), anyBoolean()));

        // Execute
        boolean result = mSpyService.onStartJob(mMockJobParameters);
        mSpyService.getFutureForTesting().get();

        // Validate
        assertTrue(result);
        ExtendedMockito.verify(
                () -> AsyncRegistrationQueueJobService.scheduleIfNeeded(any(), eq(true)));
        verify(mMockJobScheduler, never()).cancel(eq(MEASUREMENT_ASYNC_REGISTRATION_JOB_ID));
    }

    private void runWithMocks(TestUtils.RunnableWithThrow execute) throws Exception {
        // Setup mock everything in job
        doReturn(Optional.empty()).when(mMockDatastoreManager).runInTransactionWithResult(any());
        doNothing().when(mSpyService).jobFinished(any(), anyBoolean());
        doReturn(mMockJobScheduler).when(mSpyService).getSystemService(JobScheduler.class);
        doReturn(mMockJobInfo)
                .when(mMockJobScheduler)
                .getPendingJob(eq(MEASUREMENT_ASYNC_REGISTRATION_JOB.getJobId()));
        doReturn(mContext).when(mSpyService).getApplicationContext();
        doReturn(mContext.getPackageName()).when(mSpyService).getPackageName();
        ExtendedMockito.doReturn(mock(EnrollmentDao.class)).when(EnrollmentDao::getInstance);
        ExtendedMockito.doReturn(mock(AdServicesLoggerImpl.class))
                .when(AdServicesLoggerImpl::getInstance);
        ExtendedMockito.doReturn(mMockDatastoreManager)
                .when(DatastoreManagerFactory::getDatastoreManager);
        ExtendedMockito.doReturn(mMockJobInfo)
                .when(() -> AsyncRegistrationQueueJobService.buildJobInfo(any(), any()));
        mocker.mockGetAdServicesJobServiceLogger(mSpyLogger);
        // Execute
        execute.run();
    }

    @Override
    protected void toggleFeature(boolean value) {
        when(mMockFlags.getAsyncRegistrationJobQueueKillSwitch()).thenReturn(!value);
    }
}

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

import static com.android.adservices.service.stats.AdServicesStatsLog.AD_SERVICES_BACKGROUND_JOBS_EXECUTION_REPORTED__EXECUTION_RESULT_CODE__SKIP_FOR_KILL_SWITCH_ON;
import static com.android.adservices.spe.AdservicesJobInfo.MEASUREMENT_ASYNC_REGISTRATION_JOB;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertNotNull;
import static org.junit.Assert.assertTrue;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyBoolean;
import static org.mockito.ArgumentMatchers.anyInt;
import static org.mockito.ArgumentMatchers.anyLong;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.doAnswer;
import static org.mockito.Mockito.doNothing;
import static org.mockito.Mockito.doReturn;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.spy;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;

import android.app.job.JobInfo;
import android.app.job.JobParameters;
import android.app.job.JobScheduler;
import android.content.ComponentName;
import android.content.Context;

import androidx.test.core.app.ApplicationProvider;

import com.android.adservices.data.enrollment.EnrollmentDao;
import com.android.adservices.data.measurement.DatastoreManager;
import com.android.adservices.data.measurement.DatastoreManagerFactory;
import com.android.adservices.service.Flags;
import com.android.adservices.service.FlagsFactory;
import com.android.adservices.service.common.compat.ServiceCompatUtils;
import com.android.adservices.service.measurement.SystemHealthParams;
import com.android.adservices.service.stats.AdServicesLoggerImpl;
import com.android.adservices.service.stats.Clock;
import com.android.adservices.service.stats.StatsdAdServicesLogger;
import com.android.adservices.spe.AdservicesJobServiceLogger;
import com.android.compatibility.common.util.TestUtils;
import com.android.dx.mockito.inline.extended.ExtendedMockito;

import org.junit.Before;
import org.junit.Test;
import org.mockito.ArgumentCaptor;
import org.mockito.Mockito;
import org.mockito.MockitoSession;
import org.mockito.internal.stubbing.answers.AnswersWithDelay;
import org.mockito.internal.stubbing.answers.CallsRealMethods;
import org.mockito.quality.Strictness;

import java.util.Optional;
import java.util.concurrent.CountDownLatch;

public class AsyncRegistrationQueueJobServiceTest {
    private static final Context CONTEXT = ApplicationProvider.getApplicationContext();
    private static final int MEASUREMENT_ASYNC_REGISTRATION_JOB_ID =
            MEASUREMENT_ASYNC_REGISTRATION_JOB.getJobId();
    private static final long WAIT_IN_MILLIS = 1_000L;
    private JobScheduler mMockJobScheduler;
    private AsyncRegistrationQueueJobService mSpyService;
    private DatastoreManager mMockDatastoreManager;
    private AdservicesJobServiceLogger mSpyLogger;
    private Flags mMockFlags;

    @Before
    public void setUp() {
        mSpyService = spy(new AsyncRegistrationQueueJobService());
        mMockJobScheduler = mock(JobScheduler.class);

        StatsdAdServicesLogger mockStatsdLogger = mock(StatsdAdServicesLogger.class);
        mSpyLogger =
                spy(new AdservicesJobServiceLogger(CONTEXT, Clock.SYSTEM_CLOCK, mockStatsdLogger));
        mMockFlags = mock(Flags.class);
    }

    @Test
    public void onStartJob_killSwitchOn_withoutLogging() throws Exception {
        runWithMocks(
                () -> {
                    // Logging killswitch is on.
                    Mockito.doReturn(true).when(mMockFlags).getBackgroundJobsLoggingKillSwitch();

                    onStartJob_killSwitchOn();

                    // Verify logging methods are not invoked.
                    verify(mSpyLogger, never()).persistJobExecutionData(anyInt(), anyLong());
                    verify(mSpyLogger, never())
                            .logExecutionStats(anyInt(), anyLong(), anyInt(), anyInt());
                });
    }

    @Test
    public void onStartJob_killSwitchOn_withLogging() throws Exception {
        runWithMocks(
                () -> {
                    // Logging killswitch is off.
                    Mockito.doReturn(false).when(mMockFlags).getBackgroundJobsLoggingKillSwitch();

                    onStartJob_killSwitchOn();

                    // Verify logging methods are invoked.
                    verify(mSpyLogger).persistJobExecutionData(anyInt(), anyLong());
                    verify(mSpyLogger)
                            .logExecutionStats(
                                    anyInt(),
                                    anyLong(),
                                    eq(
                                            AD_SERVICES_BACKGROUND_JOBS_EXECUTION_REPORTED__EXECUTION_RESULT_CODE__SKIP_FOR_KILL_SWITCH_ON),
                                    anyInt());
                });
    }

    @Test
    public void onStartJob_killSwitchOff_withoutLogging() throws Exception {
        runWithMocks(
                () -> {
                    // Logging killswitch is on.
                    Mockito.doReturn(true).when(mMockFlags).getBackgroundJobsLoggingKillSwitch();

                    onStartJob_killSwitchOff();

                    // Verify logging methods are not invoked.
                    verify(mSpyLogger, never()).persistJobExecutionData(anyInt(), anyLong());
                    verify(mSpyLogger, never())
                            .logExecutionStats(anyInt(), anyLong(), anyInt(), anyInt());
                });
    }

    @Test
    public void onStartJob_killSwitchOff_withLogging() throws Exception {
        runWithMocks(
                () -> {
                    // Logging killswitch is off.
                    Mockito.doReturn(false).when(mMockFlags).getBackgroundJobsLoggingKillSwitch();

                    onStartJob_killSwitchOff();

                    // Verify logging methods are invoked.
                    verify(mSpyLogger).persistJobExecutionData(anyInt(), anyLong());
                    verify(mSpyLogger).logExecutionStats(anyInt(), anyLong(), anyInt(), anyInt());
                });
    }

    @Test
    public void onStartJob_killSwitchOff_unlockingCheck() throws Exception {
        runWithMocks(
                () -> {
                    // Setup
                    disableKillSwitch();
                    CountDownLatch countDownLatch = createCountDownLatch();
                    ExtendedMockito.doNothing()
                            .when(
                                    () ->
                                            AsyncRegistrationQueueJobService.scheduleIfNeeded(
                                                    any(), anyBoolean()));

                    // Execute
                    mSpyService.onStartJob(mock(JobParameters.class));
                    countDownLatch.await();
                    // TODO (b/298021244): replace sleep() with a better approach
                    Thread.sleep(WAIT_IN_MILLIS);
                    countDownLatch = createCountDownLatch();
                    boolean result = mSpyService.onStartJob(mock(JobParameters.class));
                    countDownLatch.await();
                    // TODO (b/298021244): replace sleep() with a better approach
                    Thread.sleep(WAIT_IN_MILLIS);

                    // Validate
                    assertTrue(result);

                    // Verify the job ran successfully twice
                    ExtendedMockito.verify(mSpyService, times(2)).jobFinished(any(), anyBoolean());
                    verify(mMockJobScheduler, never())
                            .cancel(eq(MEASUREMENT_ASYNC_REGISTRATION_JOB_ID));
                });
    }

    @Test
    public void onStartJob_shouldDisableJobTrue_withoutLogging() throws Exception {
        runWithMocks(
                () -> {
                    // Logging killswitch is on.
                    ExtendedMockito.doReturn(mMockFlags).when(FlagsFactory::getFlags);
                    Mockito.doReturn(true).when(mMockFlags).getBackgroundJobsLoggingKillSwitch();

                    onStartJob_shouldDisableJobTrue();

                    // Verify logging method is not invoked.
                    verify(mSpyLogger, never())
                            .logExecutionStats(anyInt(), anyLong(), anyInt(), anyInt());
                });
    }

    @Test
    public void onStartJob_shouldDisableJobTrue_withLoggingEnabled() throws Exception {
        runWithMocks(
                () -> {
                    // Logging killswitch is off.
                    ExtendedMockito.doReturn(mMockFlags).when(FlagsFactory::getFlags);
                    Mockito.doReturn(false).when(mMockFlags).getBackgroundJobsLoggingKillSwitch();

                    onStartJob_shouldDisableJobTrue();

                    // Verify logging has not happened even though logging is enabled because this
                    // field is not logged
                    verify(mSpyLogger, never())
                            .logExecutionStats(anyInt(), anyLong(), anyInt(), anyInt());
                });
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
                    ExtendedMockito.verify(
                            () -> AsyncRegistrationQueueJobService.schedule(any(), any()), never());
                    verify(mMockJobScheduler, never())
                            .getPendingJob(eq(MEASUREMENT_ASYNC_REGISTRATION_JOB_ID));
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
                    long minDelay =
                            SystemHealthParams.getMeasurementAsyncRegistrationJobQueueMinDelayMs();
                    long maxDelay =
                            SystemHealthParams.getMeasurementAsyncRegistrationJobQueueMaxDelayMs();
                    final JobInfo mockJobInfo =
                            new JobInfo.Builder(
                                            MEASUREMENT_ASYNC_REGISTRATION_JOB_ID,
                                            new ComponentName(
                                                    spyContext,
                                                    AsyncRegistrationQueueJobService.class))
                                    .addTriggerContentUri(
                                            new JobInfo.TriggerContentUri(
                                                    AsyncRegistrationContentProvider.TRIGGER_URI,
                                                    JobInfo.TriggerContentUri
                                                            .FLAG_NOTIFY_FOR_DESCENDANTS))
                                    .setTriggerContentUpdateDelay(minDelay)
                                    .setTriggerContentMaxDelay(maxDelay)
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
                            () -> AsyncRegistrationQueueJobService.schedule(any(), any()), never());
                    verify(mMockJobScheduler, times(1))
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
                    long minDelay =
                            SystemHealthParams.getMeasurementAsyncRegistrationJobQueueMinDelayMs();
                    long maxDelay =
                            SystemHealthParams.getMeasurementAsyncRegistrationJobQueueMaxDelayMs();
                    final JobInfo mockJobInfo =
                            new JobInfo.Builder(
                                            MEASUREMENT_ASYNC_REGISTRATION_JOB_ID,
                                            new ComponentName(
                                                    spyContext,
                                                    AsyncRegistrationQueueJobService.class))
                                    .addTriggerContentUri(
                                            new JobInfo.TriggerContentUri(
                                                    AsyncRegistrationContentProvider.TRIGGER_URI,
                                                    JobInfo.TriggerContentUri
                                                            .FLAG_NOTIFY_FOR_DESCENDANTS))
                                    // different
                                    .setTriggerContentUpdateDelay(minDelay - 1)
                                    .setTriggerContentMaxDelay(maxDelay)
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
                    verify(mMockJobScheduler, times(1))
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
                            times(1));
                    verify(mMockJobScheduler, times(1))
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
                    doReturn(/* noJobInfo= */ null)
                            .when(mMockJobScheduler)
                            .getPendingJob(eq(MEASUREMENT_ASYNC_REGISTRATION_JOB_ID));

                    // Execute
                    AsyncRegistrationQueueJobService.scheduleIfNeeded(mockContext, false);

                    // Validate
                    ExtendedMockito.verify(
                            () -> AsyncRegistrationQueueJobService.schedule(any(), any()),
                            times(1));
                    verify(mMockJobScheduler, times(1))
                            .getPendingJob(eq(MEASUREMENT_ASYNC_REGISTRATION_JOB_ID));
                });
    }

    @Test
    public void scheduleIfNeeded_minAndMaxDelayModified_scheduleAccordingly() throws Exception {
        runWithMocks(
                () -> {
                    // Setup
                    disableKillSwitch();
                    long minDelay = 30000L;
                    doReturn(minDelay)
                            .when(mMockFlags)
                            .getMeasurementAsyncRegistrationJobTriggerMinDelayMs();
                    long maxDelay = 60000L;
                    doReturn(maxDelay)
                            .when(mMockFlags)
                            .getMeasurementAsyncRegistrationJobTriggerMaxDelayMs();

                    final Context mockContext = mock(Context.class);
                    doReturn(mMockJobScheduler)
                            .when(mockContext)
                            .getSystemService(JobScheduler.class);
                    doReturn(/* noJobInfo= */ null)
                            .when(mMockJobScheduler)
                            .getPendingJob(eq(MEASUREMENT_ASYNC_REGISTRATION_JOB_ID));

                    // Execute
                    AsyncRegistrationQueueJobService.scheduleIfNeeded(mockContext, false);

                    // Validate
                    ExtendedMockito.verify(
                            () -> AsyncRegistrationQueueJobService.schedule(any(), any()),
                            times(1));
                    ArgumentCaptor<JobInfo> jobInfoArgumentCaptor =
                            ArgumentCaptor.forClass(JobInfo.class);
                    verify(mMockJobScheduler, times(1)).schedule(jobInfoArgumentCaptor.capture());
                    JobInfo job = jobInfoArgumentCaptor.getValue();
                    assertEquals(minDelay, job.getTriggerContentUpdateDelay());
                    assertEquals(maxDelay, job.getTriggerContentMaxDelay());
                });
    }

    @Test
    public void testOnStopJob_stopsExecutingThread() throws Exception {
        runWithMocks(
                () -> {
                    disableKillSwitch();

                    doAnswer(new AnswersWithDelay(WAIT_IN_MILLIS * 10, new CallsRealMethods()))
                            .when(mSpyService)
                            .processAsyncRecords();
                    mSpyService.onStartJob(Mockito.mock(JobParameters.class));
                    Thread.sleep(WAIT_IN_MILLIS);

                    assertNotNull(mSpyService.getFutureForTesting());

                    boolean onStopJobResult =
                            mSpyService.onStopJob(Mockito.mock(JobParameters.class));
                    verify(mSpyService, times(0)).jobFinished(any(), anyBoolean());
                    assertTrue(onStopJobResult);
                    assertTrue(mSpyService.getFutureForTesting().isCancelled());
                });
    }

    private void onStartJob_shouldDisableJobTrue() throws Exception {
        // Setup
        ExtendedMockito.doReturn(true)
                .when(
                        () ->
                                ServiceCompatUtils.shouldDisableExtServicesJobOnTPlus(
                                        any(Context.class)));

        // Execute
        boolean result = mSpyService.onStartJob(mock(JobParameters.class));

        // Validate
        assertFalse(result);
        // Allow background thread to execute
        Thread.sleep(WAIT_IN_MILLIS);
        verify(mSpyService, times(1)).jobFinished(any(), eq(false));
        verify(mMockJobScheduler, times(1)).cancel(eq(MEASUREMENT_ASYNC_REGISTRATION_JOB_ID));
    }

    private void onStartJob_killSwitchOn() throws Exception {
        // Setup
        enableKillSwitch();

        // Execute
        boolean result = mSpyService.onStartJob(mock(JobParameters.class));

        // Validate
        assertFalse(result);
        // Allow background thread to execute
        Thread.sleep(WAIT_IN_MILLIS);
        verify(mSpyService, times(1)).jobFinished(any(), eq(false));
        verify(mMockJobScheduler, times(1)).cancel(eq(MEASUREMENT_ASYNC_REGISTRATION_JOB_ID));
    }

    private void onStartJob_killSwitchOff() throws Exception {
        // Setup
        disableKillSwitch();
        ExtendedMockito.doNothing()
                .when(() -> AsyncRegistrationQueueJobService.scheduleIfNeeded(any(), anyBoolean()));

        // Execute
        boolean result = mSpyService.onStartJob(mock(JobParameters.class));

        // Validate
        assertTrue(result);
        // Allow background thread to execute
        Thread.sleep(WAIT_IN_MILLIS);
        ExtendedMockito.verify(mSpyService, times(1)).jobFinished(any(), anyBoolean());
        verify(mMockJobScheduler, never()).cancel(eq(MEASUREMENT_ASYNC_REGISTRATION_JOB_ID));
    }

    private void runWithMocks(TestUtils.RunnableWithThrow execute) throws Exception {
        MockitoSession session =
                ExtendedMockito.mockitoSession()
                        .spyStatic(AsyncRegistrationQueueJobService.class)
                        .spyStatic(AdServicesLoggerImpl.class)
                        .spyStatic(DatastoreManagerFactory.class)
                        .spyStatic(EnrollmentDao.class)
                        .spyStatic(FlagsFactory.class)
                        .spyStatic(AdservicesJobServiceLogger.class)
                        .mockStatic(ServiceCompatUtils.class)
                        .strictness(Strictness.LENIENT)
                        .startMocking();
        try {
            // Setup mock everything in job
            mMockDatastoreManager = mock(DatastoreManager.class);
            doReturn(Optional.empty())
                    .when(mMockDatastoreManager)
                    .runInTransactionWithResult(any());
            doNothing().when(mSpyService).jobFinished(any(), anyBoolean());
            doReturn(mMockJobScheduler).when(mSpyService).getSystemService(JobScheduler.class);
            doReturn(Mockito.mock(Context.class)).when(mSpyService).getApplicationContext();
            ExtendedMockito.doReturn(mock(EnrollmentDao.class))
                    .when(() -> EnrollmentDao.getInstance(any()));
            ExtendedMockito.doReturn(mock(AdServicesLoggerImpl.class))
                    .when(AdServicesLoggerImpl::getInstance);
            ExtendedMockito.doReturn(mMockDatastoreManager)
                    .when(() -> DatastoreManagerFactory.getDatastoreManager(any()));

            // Mock AdservicesJobServiceLogger to not actually log the stats to server
            Mockito.doNothing()
                    .when(mSpyLogger)
                    .logExecutionStats(anyInt(), anyLong(), anyInt(), anyInt());
            ExtendedMockito.doReturn(mSpyLogger)
                    .when(() -> AdservicesJobServiceLogger.getInstance(any(Context.class)));

            // Execute
            execute.run();
        } finally {
            session.finishMocking();
        }
    }

    private void enableKillSwitch() {
        toggleKillSwitch(true);
    }

    private void disableKillSwitch() {
        toggleKillSwitch(false);
    }

    private void toggleKillSwitch(boolean value) {
        ExtendedMockito.doReturn(mMockFlags).when(FlagsFactory::getFlags);
        ExtendedMockito.doReturn(value).when(mMockFlags).getAsyncRegistrationJobQueueKillSwitch();
    }

    private CountDownLatch createCountDownLatch() {
        final CountDownLatch countDownLatch = new CountDownLatch(1);
        doAnswer(i -> countDown(countDownLatch)).when(mSpyService).jobFinished(any(), anyBoolean());
        return countDownLatch;
    }

    private Object countDown(CountDownLatch countDownLatch) {
        countDownLatch.countDown();
        return null;
    }
}

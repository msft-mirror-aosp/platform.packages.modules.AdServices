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

package com.android.adservices.service.measurement.reporting;

import static com.android.adservices.spe.AdServicesJobInfo.MEASUREMENT_EVENT_MAIN_REPORTING_JOB;

import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertNotNull;
import static org.junit.Assert.assertTrue;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyBoolean;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.doAnswer;
import static org.mockito.Mockito.doNothing;
import static org.mockito.Mockito.doReturn;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import android.app.job.JobInfo;
import android.app.job.JobParameters;
import android.app.job.JobScheduler;
import android.content.ComponentName;
import android.content.Context;

import com.android.adservices.data.measurement.DatastoreManagerFactory;
import com.android.adservices.service.FlagsFactory;
import com.android.adservices.service.common.compat.ServiceCompatUtils;
import com.android.adservices.service.measurement.MeasurementJobServiceTestCase;
import com.android.adservices.shared.testing.JobServiceLoggingCallback;
import com.android.adservices.shared.testing.concurrency.JobServiceCallback;
import com.android.adservices.spe.AdServicesJobServiceLogger;
import com.android.compatibility.common.util.TestUtils;
import com.android.dx.mockito.inline.extended.ExtendedMockito;
import com.android.modules.utils.testing.ExtendedMockitoRule.MockStatic;
import com.android.modules.utils.testing.ExtendedMockitoRule.SpyStatic;

import org.junit.Before;
import org.junit.Test;
import org.mockito.ArgumentCaptor;
import org.mockito.Mockito;
import org.mockito.internal.stubbing.answers.AnswersWithDelay;
import org.mockito.internal.stubbing.answers.CallsRealMethods;

import java.util.Optional;
import java.util.concurrent.TimeUnit;

/** Unit test for {@link EventReportingJobService} */
@SpyStatic(DatastoreManagerFactory.class)
@SpyStatic(EventReportingJobService.class)
@SpyStatic(FlagsFactory.class)
@SpyStatic(AdServicesJobServiceLogger.class)
@MockStatic(ServiceCompatUtils.class)
public final class EventReportingJobServiceTest
        extends MeasurementJobServiceTestCase<EventReportingJobService> {
    private static final int MEASUREMENT_EVENT_MAIN_REPORTING_JOB_ID =
            MEASUREMENT_EVENT_MAIN_REPORTING_JOB.getJobId();
    private static final long WAIT_IN_MILLIS = 200L;
    private static final long JOB_PERIOD_MS = TimeUnit.HOURS.toMillis(4);

    @Override
    protected EventReportingJobService getSpiedService() {
        return new EventReportingJobService();
    }

    @Before
    public void setUp() {
        when(mMockFlags.getMeasurementEventReportingJobPersisted()).thenReturn(true);
        when(mMockFlags.getMeasurementEventReportingJobRequiredNetworkType())
                .thenReturn(JobInfo.NETWORK_TYPE_UNMETERED);
        when(mMockFlags.getMeasurementEventReportingJobRequiredBatteryNotLow()).thenReturn(true);
        when(mMockFlags.getMeasurementEventMainReportingJobPeriodMs()).thenReturn(JOB_PERIOD_MS);
    }

    @Test
    public void onStartJob_killSwitchOn_withLogging() throws Exception {
        runWithMocks(
                () -> {
                    JobServiceLoggingCallback callback = syncLogExecutionStats(mSpyLogger);

                    onStartJob_killSwitchOn();

                    verifyBackgroundJobsSkipLogged(mSpyLogger, callback);
                });
    }

    @Test
    public void onStartJob_killSwitchOff_withLogging() throws Exception {
        runWithMocks(
                () -> {
                    JobServiceLoggingCallback onStartJobCallback =
                            syncPersistJobExecutionData(mSpyLogger);
                    JobServiceLoggingCallback onJobDoneCallback = syncLogExecutionStats(mSpyLogger);

                    onStartJob_killSwitchOff();

                    verifyJobFinishedLogged(mSpyLogger, onStartJobCallback, onJobDoneCallback);
                });
    }

    @Test
    public void onStartJob_killSwitchOff_unlockingCheck() throws Exception {
        runWithMocks(
                () -> {
                    // Setup
                    disableKillSwitch();
                    ExtendedMockito.doNothing()
                            .when(
                                    () ->
                                            EventReportingJobService.scheduleIfNeeded(
                                                    any(), anyBoolean()));

                    JobServiceCallback callback =
                            new JobServiceCallback().expectJobFinished(mSpyService);

                    // Execute
                    mSpyService.onStartJob(Mockito.mock(JobParameters.class));
                    callback.assertJobFinished();

                    callback = new JobServiceCallback().expectJobFinished(mSpyService);
                    boolean result = mSpyService.onStartJob(Mockito.mock(JobParameters.class));

                    callback.assertJobFinished();

                    // Validate
                    assertTrue(result);

                    // Verify the job ran successfully twice
                    verify(mMockDatastoreManager, times(2)).runInTransactionWithResult(any());
                    verify(mSpyService, times(2)).jobFinished(any(), anyBoolean());
                    verify(mMockJobScheduler, never())
                            .cancel(eq(MEASUREMENT_EVENT_MAIN_REPORTING_JOB_ID));
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
                            .getPendingJob(eq(MEASUREMENT_EVENT_MAIN_REPORTING_JOB_ID));

                    // Execute
                    EventReportingJobService.scheduleIfNeeded(
                            mockContext, /* forceSchedule= */ false);

                    // Validate
                    ExtendedMockito.verify(
                            () -> EventReportingJobService.schedule(any(), any()), never());
                    verify(mMockJobScheduler, never())
                            .getPendingJob(eq(MEASUREMENT_EVENT_MAIN_REPORTING_JOB_ID));
                });
    }

    @Test
    public void scheduleIfNeeded_killSwitchOff_previouslyExecuted_dontForceSchedule_dontSchedule()
            throws Exception {
        runWithMocks(
                () -> {
                    // Setup
                    disableKillSwitch();

                    doReturn(mMockJobScheduler)
                            .when(mSpyContext)
                            .getSystemService(JobScheduler.class);
                    final JobInfo scheduledJobInfo =
                            new JobInfo.Builder(
                                            MEASUREMENT_EVENT_MAIN_REPORTING_JOB_ID,
                                            new ComponentName(
                                                    mSpyContext, EventReportingJobService.class))
                                    .setRequiresBatteryNotLow(true)
                                    .setRequiredNetworkType(JobInfo.NETWORK_TYPE_UNMETERED)
                                    .setPeriodic(JOB_PERIOD_MS)
                                    .setPersisted(true)
                                    .build();
                    doReturn(scheduledJobInfo)
                            .when(mMockJobScheduler)
                            .getPendingJob(eq(MEASUREMENT_EVENT_MAIN_REPORTING_JOB_ID));

                    // Execute
                    EventReportingJobService.scheduleIfNeeded(
                            mSpyContext, /* forceSchedule= */ false);

                    // Validate
                    ExtendedMockito.verify(
                            () -> EventReportingJobService.schedule(any(), any()), never());
                    verify(mMockJobScheduler)
                            .getPendingJob(eq(MEASUREMENT_EVENT_MAIN_REPORTING_JOB_ID));
                });
    }

    @Test
    public void scheduleIfNeeded_killSwitchOffPreviouslyScheduledWithDiffParams_reschedules()
            throws Exception {
        runWithMocks(
                () -> {
                    // Setup
                    disableKillSwitch();

                    doReturn(mMockJobScheduler)
                            .when(mSpyContext)
                            .getSystemService(JobScheduler.class);
                    final JobInfo scheduledJobInfo =
                            new JobInfo.Builder(
                                            MEASUREMENT_EVENT_MAIN_REPORTING_JOB_ID,
                                            new ComponentName(
                                                    mSpyContext, EventReportingJobService.class))
                                    .setRequiresBatteryNotLow(true)
                                    .setRequiredNetworkType(JobInfo.NETWORK_TYPE_UNMETERED)
                                    .setPeriodic(
                                            // To make it different from the newly scheduled job
                                            JOB_PERIOD_MS - 1)
                                    .setPersisted(true)
                                    .build();
                    doReturn(scheduledJobInfo)
                            .when(mMockJobScheduler)
                            .getPendingJob(eq(MEASUREMENT_EVENT_MAIN_REPORTING_JOB_ID));

                    // Execute
                    EventReportingJobService.scheduleIfNeeded(
                            mSpyContext, /* forceSchedule= */ false);

                    // Validate
                    ExtendedMockito.verify(() -> EventReportingJobService.schedule(any(), any()));
                    verify(mMockJobScheduler)
                            .getPendingJob(eq(MEASUREMENT_EVENT_MAIN_REPORTING_JOB_ID));
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
                            .getPendingJob(eq(MEASUREMENT_EVENT_MAIN_REPORTING_JOB_ID));

                    // Execute
                    EventReportingJobService.scheduleIfNeeded(
                            mockContext, /* forceSchedule= */ true);

                    // Validate
                    ExtendedMockito.verify(() -> EventReportingJobService.schedule(any(), any()));
                    verify(mMockJobScheduler)
                            .getPendingJob(eq(MEASUREMENT_EVENT_MAIN_REPORTING_JOB_ID));
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
                            .getPendingJob(eq(MEASUREMENT_EVENT_MAIN_REPORTING_JOB_ID));

                    // Execute
                    EventReportingJobService.scheduleIfNeeded(mockContext, false);

                    // Validate
                    ExtendedMockito.verify(() -> EventReportingJobService.schedule(any(), any()));
                    verify(mMockJobScheduler)
                            .getPendingJob(eq(MEASUREMENT_EVENT_MAIN_REPORTING_JOB_ID));
                });
    }

    @Test
    public void testSchedule_jobInfoIsPersisted() throws Exception {
        runWithMocks(
                () -> {
                    // Setup
                    disableKillSwitch();
                    final JobScheduler jobScheduler = mock(JobScheduler.class);
                    doReturn(jobScheduler).when(mSpyContext).getSystemService(JobScheduler.class);
                    final ArgumentCaptor<JobInfo> captor = ArgumentCaptor.forClass(JobInfo.class);
                    doReturn(null)
                            .when(jobScheduler)
                            .getPendingJob(eq(MEASUREMENT_EVENT_MAIN_REPORTING_JOB_ID));

                    // Execute
                    ExtendedMockito.doCallRealMethod()
                            .when(() -> EventReportingJobService.schedule(any(), any()));
                    EventReportingJobService.scheduleIfNeeded(mSpyContext, true);

                    // Validate
                    verify(jobScheduler).schedule(captor.capture());
                    assertNotNull(captor.getValue());
                    assertTrue(captor.getValue().isPersisted());
                });
    }

    @Test
    public void testOnStopJob_stopsExecutingThread() throws Exception {
        runWithMocks(
                () -> {
                    disableKillSwitch();

                    doAnswer(new AnswersWithDelay(WAIT_IN_MILLIS * 10, new CallsRealMethods()))
                            .when(mSpyService)
                            .processPendingReports();
                    mSpyService.onStartJob(Mockito.mock(JobParameters.class));
                    Thread.sleep(WAIT_IN_MILLIS);

                    assertNotNull(mSpyService.getFutureForTesting());

                    boolean onStopJobResult =
                            mSpyService.onStopJob(Mockito.mock(JobParameters.class));
                    verify(mSpyService, never()).jobFinished(any(), anyBoolean());
                    assertTrue(onStopJobResult);
                    assertTrue(mSpyService.getFutureForTesting().isCancelled());
                });
    }

    private void onStartJob_killSwitchOn() throws Exception {

        // Setup
        enableKillSwitch();

        JobServiceCallback callback = new JobServiceCallback().expectJobFinished(mSpyService);

        // Execute
        boolean result = mSpyService.onStartJob(mock(JobParameters.class));

        // Validate
        assertFalse(result);

        callback.assertJobFinished();
        verify(mMockDatastoreManager, never()).runInTransactionWithResult(any());
        verify(mSpyService).jobFinished(any(), eq(false));
        verify(mMockJobScheduler).cancel(eq(MEASUREMENT_EVENT_MAIN_REPORTING_JOB_ID));
    }

    private void onStartJob_killSwitchOff() throws Exception {
        // Setup
        disableKillSwitch();

        ExtendedMockito.doNothing()
                .when(() -> EventReportingJobService.scheduleIfNeeded(any(), anyBoolean()));

        JobServiceCallback callback = new JobServiceCallback().expectJobFinished(mSpyService);

        // Execute
        boolean result = mSpyService.onStartJob(mock(JobParameters.class));

        // Validate
        assertTrue(result);

        callback.assertJobFinished();
        verify(mMockDatastoreManager).runInTransactionWithResult(any());
        verify(mSpyService).jobFinished(any(), anyBoolean());
        verify(mMockJobScheduler, never()).cancel(eq(MEASUREMENT_EVENT_MAIN_REPORTING_JOB_ID));
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
        boolean result = mSpyService.onStartJob(mock(JobParameters.class));

        // Validate
        assertFalse(result);

        callback.assertJobFinished();
        verify(mMockDatastoreManager, never()).runInTransactionWithResult(any());
        verify(mSpyService).jobFinished(any(), eq(false));
        verify(mMockJobScheduler).cancel(eq(MEASUREMENT_EVENT_MAIN_REPORTING_JOB_ID));
    }

    private void runWithMocks(TestUtils.RunnableWithThrow execute) throws Exception {
        // Setup mock everything in job
        doReturn(Optional.empty()).when(mMockDatastoreManager).runInTransactionWithResult(any());
        doNothing().when(mSpyService).jobFinished(any(), anyBoolean());
        doReturn(mMockJobScheduler).when(mSpyService).getSystemService(JobScheduler.class);
        doReturn(Mockito.mock(Context.class)).when(mSpyService).getApplicationContext();
        ExtendedMockito.doReturn(mMockDatastoreManager)
                .when(DatastoreManagerFactory::getDatastoreManager);
        ExtendedMockito.doNothing().when(() -> EventReportingJobService.schedule(any(), any()));
        mockGetAdServicesJobServiceLogger(mSpyLogger);

        // Execute
        execute.run();
    }

    @Override
    protected void toggleFeature(boolean value) {
        when(mMockFlags.getMeasurementJobEventReportingKillSwitch()).thenReturn(!value);
    }
}
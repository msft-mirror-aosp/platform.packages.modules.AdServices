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

import static com.android.adservices.mockito.ExtendedMockitoExpectations.mockGetAdServicesJobServiceLogger;
import static com.android.adservices.mockito.MockitoExpectations.getSpiedAdServicesJobServiceLogger;
import static com.android.adservices.mockito.MockitoExpectations.mockBackgroundJobsLoggingKillSwitch;
import static com.android.adservices.mockito.MockitoExpectations.syncLogExecutionStats;
import static com.android.adservices.mockito.MockitoExpectations.syncPersistJobExecutionData;
import static com.android.adservices.mockito.MockitoExpectations.verifyBackgroundJobsSkipLogged;
import static com.android.adservices.mockito.MockitoExpectations.verifyJobFinishedLogged;
import static com.android.adservices.mockito.MockitoExpectations.verifyLoggingNotHappened;
import static com.android.adservices.spe.AdServicesJobInfo.MEASUREMENT_AGGREGATE_MAIN_REPORTING_JOB;

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
import static org.mockito.Mockito.spy;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import android.app.job.JobInfo;
import android.app.job.JobParameters;
import android.app.job.JobScheduler;
import android.content.ComponentName;
import android.content.Context;

import androidx.test.core.app.ApplicationProvider;

import com.android.adservices.data.enrollment.EnrollmentDao;
import com.android.adservices.data.measurement.DatastoreManager;
import com.android.adservices.data.measurement.DatastoreManagerFactory;
import com.android.adservices.mockito.AdServicesExtendedMockitoRule;
import com.android.adservices.mockito.ExtendedMockitoExpectations;
import com.android.adservices.service.Flags;
import com.android.adservices.service.FlagsFactory;
import com.android.adservices.service.common.compat.ServiceCompatUtils;
import com.android.adservices.shared.testing.JobServiceCallback;
import com.android.adservices.shared.testing.JobServiceLoggingCallback;
import com.android.adservices.spe.AdServicesJobServiceLogger;
import com.android.compatibility.common.util.TestUtils;
import com.android.dx.mockito.inline.extended.ExtendedMockito;

import org.junit.Before;
import org.junit.Rule;
import org.junit.Test;
import org.mockito.ArgumentCaptor;
import org.mockito.Mockito;
import org.mockito.internal.stubbing.answers.AnswersWithDelay;
import org.mockito.internal.stubbing.answers.CallsRealMethods;
import org.mockito.quality.Strictness;

import java.util.Optional;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.TimeUnit;

/**
 * Unit test for {@link AggregateReportingJobService
 */
public class AggregateReportingJobServiceTest {
    private static final Context CONTEXT = ApplicationProvider.getApplicationContext();
    private static final int MEASUREMENT_AGGREGATE_MAIN_REPORTING_JOB_ID =
            MEASUREMENT_AGGREGATE_MAIN_REPORTING_JOB.getJobId();
    private static final long WAIT_IN_MILLIS = 200L;
    private static final long JOB_PERIOD_MS = TimeUnit.HOURS.toMillis(4);

    private DatastoreManager mMockDatastoreManager;
    private JobScheduler mMockJobScheduler;

    private AggregateReportingJobService mSpyService;
    private Flags mMockFlags;
    private AdServicesJobServiceLogger mSpyLogger;

    @Rule
    public final AdServicesExtendedMockitoRule adServicesExtendedMockitoRule =
            new AdServicesExtendedMockitoRule.Builder(this)
                    .spyStatic(AggregateReportingJobService.class)
                    .spyStatic(DatastoreManagerFactory.class)
                    .spyStatic(EnrollmentDao.class)
                    .spyStatic(FlagsFactory.class)
                    .spyStatic(AdServicesJobServiceLogger.class)
                    .mockStatic(ServiceCompatUtils.class)
                    .setStrictness(Strictness.LENIENT)
                    .build();

    @Before
    public void setUp() {
        mSpyService = spy(new AggregateReportingJobService());
        mMockDatastoreManager = mock(DatastoreManager.class);
        mMockJobScheduler = mock(JobScheduler.class);

        mMockFlags = mock(Flags.class);
        mSpyLogger = getSpiedAdServicesJobServiceLogger(CONTEXT, mMockFlags);
        when(mMockFlags.getMeasurementAggregateReportingJobPersisted()).thenReturn(true);
        when(mMockFlags.getMeasurementAggregateReportingJobRequiredNetworkType())
                .thenReturn(JobInfo.NETWORK_TYPE_UNMETERED);
        when(mMockFlags.getMeasurementAggregateReportingJobRequiredBatteryNotLow())
                .thenReturn(true);
        when(mMockFlags.getMeasurementAggregateMainReportingJobPeriodMs())
                .thenReturn(JOB_PERIOD_MS);
    }

    @Test
    public void onStartJob_killSwitchOn_withoutLogging() throws Exception {
        runWithMocks(
                () -> {
                    mockBackgroundJobsLoggingKillSwitch(mMockFlags, /* overrideValue= */ true);

                    onStartJob_killSwitchOn();

                    verifyLoggingNotHappened(mSpyLogger);
                });
    }

    @Test
    public void onStartJob_killSwitchOn_withLogging() throws Exception {
        runWithMocks(
                () -> {
                    mockBackgroundJobsLoggingKillSwitch(mMockFlags, /* overrideValue= */ false);
                    JobServiceLoggingCallback callback = syncLogExecutionStats(mSpyLogger);

                    onStartJob_killSwitchOn();

                    verifyBackgroundJobsSkipLogged(mSpyLogger, callback);
                });
    }

    @Test
    public void onStartJob_killSwitchOff_withoutLogging() throws Exception {
        runWithMocks(
                () -> {
                    mockBackgroundJobsLoggingKillSwitch(mMockFlags, /* overrideValue= */ true);

                    onStartJob_killSwitchOff();

                    verifyLoggingNotHappened(mSpyLogger);
                });
    }

    @Test
    public void onStartJob_killSwitchOff_withLogging() throws Exception {
        runWithMocks(
                () -> {
                    mockBackgroundJobsLoggingKillSwitch(mMockFlags, /* overrideValue= */ false);
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
                            .cancel(eq(MEASUREMENT_AGGREGATE_MAIN_REPORTING_JOB_ID));
                });
    }

    @Test
    public void onStartJob_shouldDisableJobTrue_withoutLogging() throws Exception {
        runWithMocks(
                () -> {
                    ExtendedMockitoExpectations.mockGetFlags(mMockFlags);
                    ExtendedMockito.doReturn(mMockFlags).when(FlagsFactory::getFlags);
                    mockBackgroundJobsLoggingKillSwitch(mMockFlags, /* overrideValue= */ true);

                    onStartJob_shouldDisableJobTrue();

                    verifyLoggingNotHappened(mSpyLogger);
                });
    }

    @Test
    public void onStartJob_shouldDisableJobTrue_withLoggingEnabled() throws Exception {
        runWithMocks(
                () -> {
                    ExtendedMockito.doReturn(mMockFlags).when(FlagsFactory::getFlags);
                    mockBackgroundJobsLoggingKillSwitch(mMockFlags, /* overrideValue= */ false);

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
                            .getPendingJob(eq(MEASUREMENT_AGGREGATE_MAIN_REPORTING_JOB_ID));

                    // Execute
                    AggregateReportingJobService.scheduleIfNeeded(
                            mockContext, /* forceSchedule= */ false);

                    // Validate
                    ExtendedMockito.verify(
                            () -> AggregateReportingJobService.schedule(any(), any()), never());
                    verify(mMockJobScheduler, never())
                            .getPendingJob(eq(MEASUREMENT_AGGREGATE_MAIN_REPORTING_JOB_ID));
                });
    }

    @Test
    public void scheduleIfNeeded_killSwitchOff_previouslyExecuted_dontForceSchedule_dontSchedule()
            throws Exception {
        runWithMocks(
                () -> {
                    // Setup
                    disableKillSwitch();

                    final Context spyContext = spy(CONTEXT);
                    doReturn(mMockJobScheduler)
                            .when(spyContext)
                            .getSystemService(JobScheduler.class);
                    final JobInfo scheduledJobInfo =
                            new JobInfo.Builder(
                                            MEASUREMENT_AGGREGATE_MAIN_REPORTING_JOB_ID,
                                            new ComponentName(
                                                    spyContext, AggregateReportingJobService.class))
                                    .setRequiresBatteryNotLow(true)
                                    .setRequiredNetworkType(JobInfo.NETWORK_TYPE_UNMETERED)
                                    .setPeriodic(JOB_PERIOD_MS)
                                    .setPersisted(true)
                                    .build();
                    doReturn(scheduledJobInfo)
                            .when(mMockJobScheduler)
                            .getPendingJob(eq(MEASUREMENT_AGGREGATE_MAIN_REPORTING_JOB_ID));

                    // Execute
                    AggregateReportingJobService.scheduleIfNeeded(
                            spyContext, /* forceSchedule= */ false);

                    // Validate
                    ExtendedMockito.verify(
                            () -> AggregateReportingJobService.schedule(any(), any()), never());
                    verify(mMockJobScheduler, times(1))
                            .getPendingJob(eq(MEASUREMENT_AGGREGATE_MAIN_REPORTING_JOB_ID));
                });
    }

    @Test
    public void scheduleIfNeeded_killSwitchOffPreviouslyScheduledWithDiffParams_reschedules()
            throws Exception {
        runWithMocks(
                () -> {
                    // Setup
                    disableKillSwitch();

                    final Context spyContext = spy(CONTEXT);
                    doReturn(mMockJobScheduler)
                            .when(spyContext)
                            .getSystemService(JobScheduler.class);
                    final JobInfo scheduledJobInfo =
                            new JobInfo.Builder(
                                            MEASUREMENT_AGGREGATE_MAIN_REPORTING_JOB_ID,
                                            new ComponentName(
                                                    spyContext, AggregateReportingJobService.class))
                                    .setRequiresBatteryNotLow(true)
                                    .setRequiredNetworkType(JobInfo.NETWORK_TYPE_UNMETERED)
                                    .setPeriodic(
                                            // To make it different from the newly scheduled job
                                            JOB_PERIOD_MS - 1)
                                    .setPersisted(true)
                                    .build();
                    doReturn(scheduledJobInfo)
                            .when(mMockJobScheduler)
                            .getPendingJob(eq(MEASUREMENT_AGGREGATE_MAIN_REPORTING_JOB_ID));

                    // Execute
                    AggregateReportingJobService.scheduleIfNeeded(
                            spyContext, /* forceSchedule= */ false);

                    // Validate
                    ExtendedMockito.verify(
                            () -> AggregateReportingJobService.schedule(any(), any()));
                    verify(mMockJobScheduler, times(1))
                            .getPendingJob(eq(MEASUREMENT_AGGREGATE_MAIN_REPORTING_JOB_ID));
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
                            .getPendingJob(eq(MEASUREMENT_AGGREGATE_MAIN_REPORTING_JOB_ID));

                    // Execute
                    AggregateReportingJobService.scheduleIfNeeded(
                            mockContext, /* forceSchedule= */ true);

                    // Validate
                    ExtendedMockito.verify(
                            () -> AggregateReportingJobService.schedule(any(), any()), times(1));
                    verify(mMockJobScheduler, times(1))
                            .getPendingJob(eq(MEASUREMENT_AGGREGATE_MAIN_REPORTING_JOB_ID));
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
                            .getPendingJob(eq(MEASUREMENT_AGGREGATE_MAIN_REPORTING_JOB_ID));

                    // Execute
                    AggregateReportingJobService.scheduleIfNeeded(mockContext, false);

                    // Validate
                    ExtendedMockito.verify(
                            () -> AggregateReportingJobService.schedule(any(), any()), times(1));
                    verify(mMockJobScheduler, times(1))
                            .getPendingJob(eq(MEASUREMENT_AGGREGATE_MAIN_REPORTING_JOB_ID));
                });
    }

    @Test
    public void testSchedule_jobInfoIsPersisted() throws Exception {
        runWithMocks(
                () -> {
                    // Setup
                    disableKillSwitch();
                    Context spyContext = spy(CONTEXT);
                    final JobScheduler jobScheduler = mock(JobScheduler.class);
                    doReturn(jobScheduler).when(spyContext).getSystemService(JobScheduler.class);
                    final ArgumentCaptor<JobInfo> captor = ArgumentCaptor.forClass(JobInfo.class);
                    doReturn(null)
                            .when(jobScheduler)
                            .getPendingJob(eq(MEASUREMENT_AGGREGATE_MAIN_REPORTING_JOB_ID));

                    // Execute
                    ExtendedMockito.doCallRealMethod()
                            .when(() -> AggregateReportingJobService.schedule(any(), any()));
                    AggregateReportingJobService.scheduleIfNeeded(spyContext, true);

                    // Validate
                    verify(jobScheduler, times(1)).schedule(captor.capture());
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
                    verify(mSpyService, times(0)).jobFinished(any(), anyBoolean());
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
        verify(mSpyService, times(1)).jobFinished(any(), eq(false));
        verify(mMockJobScheduler, times(1)).cancel(eq(MEASUREMENT_AGGREGATE_MAIN_REPORTING_JOB_ID));
    }

    private void onStartJob_killSwitchOff() throws Exception {
        // Setup
        disableKillSwitch();

        JobServiceCallback callback = new JobServiceCallback().expectJobFinished(mSpyService);

        // Execute
        boolean result = mSpyService.onStartJob(mock(JobParameters.class));

        // Validate
        assertTrue(result);

        callback.assertJobFinished();
        verify(mMockDatastoreManager, times(1)).runInTransactionWithResult(any());
        verify(mSpyService, times(1)).jobFinished(any(), anyBoolean());
        verify(mMockJobScheduler, never()).cancel(eq(MEASUREMENT_AGGREGATE_MAIN_REPORTING_JOB_ID));
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
        verify(mSpyService, times(1)).jobFinished(any(), eq(false));
        verify(mMockJobScheduler, times(1)).cancel(eq(MEASUREMENT_AGGREGATE_MAIN_REPORTING_JOB_ID));
    }

    private void runWithMocks(TestUtils.RunnableWithThrow execute) throws Exception {
        Context context = Mockito.mock(Context.class);
        doReturn(CONTEXT.getPackageName()).when(context).getPackageName();
        doReturn(CONTEXT.getPackageManager()).when(context).getPackageManager();
        // Setup mock everything in job
        mMockDatastoreManager = mock(DatastoreManager.class);
        doReturn(Optional.empty()).when(mMockDatastoreManager).runInTransactionWithResult(any());
        doNothing().when(mSpyService).jobFinished(any(), anyBoolean());
        doReturn(mMockJobScheduler).when(mSpyService).getSystemService(JobScheduler.class);
        doReturn(context).when(mSpyService).getApplicationContext();
        ExtendedMockito.doReturn(mock(EnrollmentDao.class)).when(() -> EnrollmentDao.getInstance());
        ExtendedMockito.doReturn(mMockDatastoreManager)
                .when(() -> DatastoreManagerFactory.getDatastoreManager(any()));
        ExtendedMockito.doNothing().when(() -> AggregateReportingJobService.schedule(any(), any()));
        mockGetAdServicesJobServiceLogger(mSpyLogger);

        // Execute
        execute.run();
    }

    private void enableKillSwitch() {
        toggleKillSwitch(true);
    }

    private void disableKillSwitch() {
        toggleKillSwitch(false);
    }

    private void toggleKillSwitch(boolean value) {
        ExtendedMockito.doReturn(mMockFlags).when(FlagsFactory::getFlags);
        ExtendedMockito.doReturn(value)
                .when(mMockFlags)
                .getMeasurementJobAggregateReportingKillSwitch();
    }

    private Object countDown(CountDownLatch countDownLatch) {
        countDownLatch.countDown();
        return null;
    }
}

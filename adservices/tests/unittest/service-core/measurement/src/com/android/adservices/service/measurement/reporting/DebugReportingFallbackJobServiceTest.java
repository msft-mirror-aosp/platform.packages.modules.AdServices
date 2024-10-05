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

package com.android.adservices.service.measurement.reporting;

import static com.android.adservices.spe.AdServicesJobInfo.MEASUREMENT_DEBUG_REPORTING_FALLBACK_JOB;

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
import com.android.adservices.data.measurement.DatastoreManagerFactory;
import com.android.adservices.service.FlagsFactory;
import com.android.adservices.service.common.compat.ServiceCompatUtils;
import com.android.adservices.service.measurement.MeasurementJobServiceTestCase;
import com.android.adservices.service.stats.AdServicesLoggerImpl;
import com.android.adservices.shared.testing.JobServiceLoggingCallback;
import com.android.adservices.shared.testing.concurrency.JobServiceCallback;
import com.android.adservices.spe.AdServicesJobServiceLogger;
import com.android.compatibility.common.util.TestUtils;
import com.android.dx.mockito.inline.extended.ExtendedMockito;
import com.android.modules.utils.testing.ExtendedMockitoRule.MockStatic;
import com.android.modules.utils.testing.ExtendedMockitoRule.SpyStatic;

import org.junit.Before;
import org.junit.Test;
import org.mockito.Mockito;
import org.mockito.internal.stubbing.answers.AnswersWithDelay;
import org.mockito.internal.stubbing.answers.CallsRealMethods;

import java.util.Optional;
import java.util.concurrent.TimeUnit;

@SpyStatic(DebugReportingFallbackJobService.class)
@SpyStatic(AdServicesLoggerImpl.class)
@SpyStatic(DatastoreManagerFactory.class)
@SpyStatic(EnrollmentDao.class)
@SpyStatic(FlagsFactory.class)
@SpyStatic(AdServicesJobServiceLogger.class)
@MockStatic(ServiceCompatUtils.class)
public final class DebugReportingFallbackJobServiceTest
        extends MeasurementJobServiceTestCase<DebugReportingFallbackJobService> {
    private static final int MEASUREMENT_DEBUG_REPORTING_FALLBACK_JOB_ID =
            MEASUREMENT_DEBUG_REPORTING_FALLBACK_JOB.getJobId();
    private static final long WAIT_IN_MILLIS = 200L;
    private static final long JOB_PERIOD_MS = TimeUnit.HOURS.toMillis(4);

    @Override
    protected DebugReportingFallbackJobService getSpiedService() {
        return new DebugReportingFallbackJobService();
    }

    @Before
    public void setUp() {
        when(mMockFlags.getMeasurementDebugReportingFallbackJobPersisted()).thenReturn(true);
        when(mMockFlags.getMeasurementDebugReportingFallbackJobRequiredNetworkType())
                .thenReturn(JobInfo.NETWORK_TYPE_ANY);
        when(mMockFlags.getMeasurementDebugReportingFallbackJobPeriodMs())
                .thenReturn(JOB_PERIOD_MS);
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
                    ExtendedMockito.doNothing()
                            .when(
                                    () ->
                                            DebugReportingFallbackJobService.scheduleIfNeeded(
                                                    any(), anyBoolean()));
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
                    verify(mSpyService, times(2)).jobFinished(any(), anyBoolean());
                    verify(mMockJobScheduler, never())
                            .cancel(eq(MEASUREMENT_DEBUG_REPORTING_FALLBACK_JOB_ID));
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
                            .getPendingJob(eq(MEASUREMENT_DEBUG_REPORTING_FALLBACK_JOB_ID));

                    // Execute
                    DebugReportingFallbackJobService.scheduleIfNeeded(
                            mockContext, /* forceSchedule= */ false);

                    // Validate
                    ExtendedMockito.verify(
                            () -> DebugReportingFallbackJobService.schedule(any(), any()), never());
                    verify(mMockJobScheduler, never())
                            .getPendingJob(eq(MEASUREMENT_DEBUG_REPORTING_FALLBACK_JOB_ID));
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
                                            MEASUREMENT_DEBUG_REPORTING_FALLBACK_JOB_ID,
                                            new ComponentName(
                                                    spyContext,
                                                    DebugReportingFallbackJobService.class))
                                    .setRequiredNetworkType(JobInfo.NETWORK_TYPE_ANY)
                                    .setPeriodic(JOB_PERIOD_MS)
                                    .setPersisted(true)
                                    .build();
                    doReturn(mockJobInfo)
                            .when(mMockJobScheduler)
                            .getPendingJob(eq(MEASUREMENT_DEBUG_REPORTING_FALLBACK_JOB_ID));

                    // Execute
                    DebugReportingFallbackJobService.scheduleIfNeeded(
                            spyContext, /* forceSchedule= */ false);

                    // Validate
                    ExtendedMockito.verify(
                            () -> DebugReportingFallbackJobService.schedule(any(), any()), never());
                    verify(mMockJobScheduler)
                            .getPendingJob(eq(MEASUREMENT_DEBUG_REPORTING_FALLBACK_JOB_ID));
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
                                            MEASUREMENT_DEBUG_REPORTING_FALLBACK_JOB_ID,
                                            new ComponentName(
                                                    spyContext,
                                                    DebugReportingFallbackJobService.class))
                                    .setRequiredNetworkType(JobInfo.NETWORK_TYPE_ANY)
                                    // Difference
                                    .setPeriodic(JOB_PERIOD_MS - 1)
                                    .setPersisted(true)
                                    .build();
                    doReturn(mockJobInfo)
                            .when(mMockJobScheduler)
                            .getPendingJob(eq(MEASUREMENT_DEBUG_REPORTING_FALLBACK_JOB_ID));

                    // Execute
                    DebugReportingFallbackJobService.scheduleIfNeeded(
                            spyContext, /* forceSchedule= */ false);

                    // Validate
                    ExtendedMockito.verify(
                            () -> DebugReportingFallbackJobService.schedule(any(), any()));
                    verify(mMockJobScheduler)
                            .getPendingJob(eq(MEASUREMENT_DEBUG_REPORTING_FALLBACK_JOB_ID));
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
                            .getPendingJob(eq(MEASUREMENT_DEBUG_REPORTING_FALLBACK_JOB_ID));

                    // Execute
                    DebugReportingFallbackJobService.scheduleIfNeeded(
                            mockContext, /* forceSchedule= */ true);

                    // Validate
                    ExtendedMockito.verify(
                            () -> DebugReportingFallbackJobService.schedule(any(), any()),
                            times(1));
                    verify(mMockJobScheduler)
                            .getPendingJob(eq(MEASUREMENT_DEBUG_REPORTING_FALLBACK_JOB_ID));
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
                            .getPendingJob(eq(MEASUREMENT_DEBUG_REPORTING_FALLBACK_JOB_ID));

                    // Execute
                    DebugReportingFallbackJobService.scheduleIfNeeded(mockContext, false);

                    // Validate
                    ExtendedMockito.verify(
                            () -> DebugReportingFallbackJobService.schedule(any(), any()),
                            times(1));
                    verify(mMockJobScheduler)
                            .getPendingJob(eq(MEASUREMENT_DEBUG_REPORTING_FALLBACK_JOB_ID));
                });
    }

    @Test
    public void testOnStopJob_stopsExecutingThread() throws Exception {
        runWithMocks(
                () -> {
                    disableKillSwitch();

                    doAnswer(new AnswersWithDelay(WAIT_IN_MILLIS * 10, new CallsRealMethods()))
                            .when(mSpyService)
                            .sendReports();
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
        verify(mSpyService).jobFinished(any(), eq(false));
        verify(mMockJobScheduler).cancel(eq(MEASUREMENT_DEBUG_REPORTING_FALLBACK_JOB_ID));
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
        verify(mSpyService).jobFinished(any(), eq(false));
        verify(mMockJobScheduler).cancel(eq(MEASUREMENT_DEBUG_REPORTING_FALLBACK_JOB_ID));
    }

    private void onStartJob_killSwitchOff() throws Exception {
        // Setup
        disableKillSwitch();
        ExtendedMockito.doNothing()
                .when(() -> DebugReportingFallbackJobService.scheduleIfNeeded(any(), anyBoolean()));

        JobServiceCallback callback = new JobServiceCallback().expectJobFinished(mSpyService);

        // Execute
        boolean result = mSpyService.onStartJob(mock(JobParameters.class));

        // Validate
        assertTrue(result);

        callback.assertJobFinished();

        verify(mSpyService).jobFinished(any(), anyBoolean());
        verify(mMockJobScheduler, never()).cancel(eq(MEASUREMENT_DEBUG_REPORTING_FALLBACK_JOB_ID));
    }

    private void runWithMocks(TestUtils.RunnableWithThrow execute) throws Exception {
        doReturn(mContext.getPackageName()).when(mMockContext).getPackageName();
        doReturn(mContext.getPackageManager()).when(mMockContext).getPackageManager();
        // Setup mock everything in job
        doReturn(Optional.empty()).when(mMockDatastoreManager).runInTransactionWithResult(any());
        doNothing().when(mSpyService).jobFinished(any(), anyBoolean());
        doReturn(mMockJobScheduler).when(mSpyService).getSystemService(JobScheduler.class);
        doReturn(mMockContext).when(mSpyService).getApplicationContext();
        ExtendedMockito.doReturn(mock(EnrollmentDao.class)).when(EnrollmentDao::getInstance);
        ExtendedMockito.doReturn(mock(AdServicesLoggerImpl.class))
                .when(AdServicesLoggerImpl::getInstance);
        ExtendedMockito.doNothing()
                .when(() -> DebugReportingFallbackJobService.schedule(any(), any()));
        ExtendedMockito.doReturn(mMockDatastoreManager)
                .when(DatastoreManagerFactory::getDatastoreManager);
        ExtendedMockito.doReturn(false)
                .when(
                        () ->
                                ServiceCompatUtils.shouldDisableExtServicesJobOnTPlus(
                                        any(Context.class)));
        mocker.mockGetAdServicesJobServiceLogger(mSpyLogger);

        // Execute
        execute.run();
    }

    @Override
    protected void toggleFeature(boolean value) {
        when(mMockFlags.getMeasurementDebugReportingFallbackJobKillSwitch()).thenReturn(!value);
    }
}

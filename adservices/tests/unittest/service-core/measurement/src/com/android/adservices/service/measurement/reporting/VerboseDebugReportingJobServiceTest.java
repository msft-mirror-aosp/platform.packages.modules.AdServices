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

import static com.android.adservices.service.measurement.reporting.VerboseDebugReportingJobService.VERBOSE_DEBUG_REPORT_JOB_ID;
import static com.android.adservices.spe.AdServicesJobInfo.MEASUREMENT_VERBOSE_DEBUG_REPORT_JOB;

import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertNotNull;
import static org.junit.Assert.assertTrue;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyBoolean;
import static org.mockito.ArgumentMatchers.anyInt;
import static org.mockito.ArgumentMatchers.eq;
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
import android.app.job.JobParameters;
import android.app.job.JobScheduler;
import android.content.ComponentName;
import android.content.Context;

import androidx.test.core.app.ApplicationProvider;

import com.android.adservices.data.enrollment.EnrollmentDao;
import com.android.adservices.data.measurement.DatastoreManagerFactory;
import com.android.adservices.service.AdServicesConfig;
import com.android.adservices.service.FlagsFactory;
import com.android.adservices.service.common.compat.ServiceCompatUtils;
import com.android.adservices.service.measurement.MeasurementJobServiceTestCase;
import com.android.adservices.shared.testing.concurrency.JobServiceCallback;
import com.android.adservices.spe.AdServicesJobServiceLogger;
import com.android.compatibility.common.util.TestUtils;
import com.android.dx.mockito.inline.extended.ExtendedMockito;
import com.android.modules.utils.testing.ExtendedMockitoRule.MockStatic;
import com.android.modules.utils.testing.ExtendedMockitoRule.SpyStatic;

import org.junit.Test;
import org.mockito.Mockito;
import org.mockito.internal.stubbing.answers.AnswersWithDelay;
import org.mockito.internal.stubbing.answers.CallsRealMethods;

import java.util.Optional;

/** Unit test for {@link VerboseDebugReportingJobService} */
@SpyStatic(AdServicesConfig.class)
@SpyStatic(DatastoreManagerFactory.class)
@SpyStatic(EnrollmentDao.class)
@SpyStatic(VerboseDebugReportingJobService.class)
@SpyStatic(FlagsFactory.class)
@SpyStatic(AdServicesJobServiceLogger.class)
@MockStatic(ServiceCompatUtils.class)
public final class VerboseDebugReportingJobServiceTest
        extends MeasurementJobServiceTestCase<VerboseDebugReportingJobService> {
    private static final int MEASUREMENT_VERBOSE_DEBUG_REPORT_JOB_ID =
            MEASUREMENT_VERBOSE_DEBUG_REPORT_JOB.getJobId();

    private static final long WAIT_IN_MILLIS = 1_000L;

    @Override
    protected VerboseDebugReportingJobService getSpiedService() {
        return new VerboseDebugReportingJobService();
    }

    @Test
    public void onStartJob_killSwitchOn() throws Exception {
        runWithMocks(
                () -> {
                    // Setup
                    enableKillSwitch();

                    JobServiceCallback callback =
                            new JobServiceCallback().expectJobFinished(mSpyService);

                    // Execute
                    boolean result = mSpyService.onStartJob(mock(JobParameters.class));

                    // Validate
                    assertFalse(result);

                    callback.assertJobFinished();
                    verify(mMockDatastoreManager, never()).runInTransactionWithResult(any());
                    verify(mSpyService, times(1)).jobFinished(any(), eq(false));
                    verify(mMockJobScheduler, times(1))
                            .cancel(eq(MEASUREMENT_VERBOSE_DEBUG_REPORT_JOB_ID));
                });
    }

    @Test
    public void onStartJob_killSwitchOff() throws Exception {
        runWithMocks(
                () -> {
                    // Setup
                    disableKillSwitch();
                    ExtendedMockito.doNothing()
                            .when(
                                    () ->
                                            VerboseDebugReportingJobService.scheduleIfNeeded(
                                                    any(), anyBoolean()));

                    ExtendedMockito.doNothing()
                            .when(mSpyLogger)
                            .recordJobFinished(anyInt(), anyBoolean(), anyBoolean());

                    JobServiceCallback callback =
                            new JobServiceCallback().expectJobFinished(mSpyService);

                    // Execute
                    boolean result = mSpyService.onStartJob(mock(JobParameters.class));

                    // Validate
                    assertTrue(result);

                    callback.assertJobFinished();
                    verify(mMockDatastoreManager, times(1)).runInTransactionWithResult(any());
                    verify(mSpyService, times(1)).jobFinished(any(), anyBoolean());
                    verify(mMockJobScheduler, never())
                            .cancel(eq(MEASUREMENT_VERBOSE_DEBUG_REPORT_JOB_ID));
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
                                            VerboseDebugReportingJobService.scheduleIfNeeded(
                                                    any(), anyBoolean()));

                    ExtendedMockito.doNothing()
                            .when(mSpyLogger)
                            .recordJobFinished(anyInt(), anyBoolean(), anyBoolean());

                    JobServiceCallback callback =
                            new JobServiceCallback().expectJobFinished(mSpyService);

                    // Execute
                    boolean result = mSpyService.onStartJob(Mockito.mock(JobParameters.class));
                    callback.assertJobFinished();

                    assertTrue(result);

                    JobServiceCallback secondCallback =
                            new JobServiceCallback().expectJobFinished(mSpyService);
                    verify(mSpyService, timeout(WAIT_IN_MILLIS).times(1))
                            .jobFinished(any(), anyBoolean());

                    result = mSpyService.onStartJob(Mockito.mock(JobParameters.class));

                    secondCallback.assertJobFinished();

                    // Validate
                    assertTrue(result);

                    // Verify the job ran successfully twice
                    verify(mMockDatastoreManager, times(2)).runInTransactionWithResult(any());
                    verify(mSpyService, times(2)).jobFinished(any(), anyBoolean());
                    verify(mMockJobScheduler, never())
                            .cancel(eq(MEASUREMENT_VERBOSE_DEBUG_REPORT_JOB_ID));
                });
    }

    @Test
    public void onStartJob_shouldDisableJobTrue() throws Exception {
        runWithMocks(
                () -> {
                    // Setup
                    ExtendedMockito.doReturn(true)
                            .when(
                                    () ->
                                            ServiceCompatUtils.shouldDisableExtServicesJobOnTPlus(
                                                    any(Context.class)));

                    JobServiceCallback callback =
                            new JobServiceCallback().expectJobFinished(mSpyService);

                    // Execute
                    boolean result = mSpyService.onStartJob(mock(JobParameters.class));

                    // Validate
                    assertFalse(result);

                    callback.assertJobFinished();
                    verify(mMockDatastoreManager, never()).runInTransactionWithResult(any());
                    verify(mSpyService, times(1)).jobFinished(any(), eq(false));
                    verify(mMockJobScheduler, times(1))
                            .cancel(eq(MEASUREMENT_VERBOSE_DEBUG_REPORT_JOB_ID));
                    ExtendedMockito.verifyZeroInteractions(
                            ExtendedMockito.staticMockMarker(FlagsFactory.class));
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
                            .getPendingJob(eq(MEASUREMENT_VERBOSE_DEBUG_REPORT_JOB_ID));

                    // Execute
                    VerboseDebugReportingJobService.scheduleIfNeeded(
                            mockContext, /* forceSchedule= */ false);

                    // Validate
                    ExtendedMockito.verify(
                            () -> VerboseDebugReportingJobService.schedule(any(), any()), never());
                    verify(mMockJobScheduler, never())
                            .getPendingJob(eq(MEASUREMENT_VERBOSE_DEBUG_REPORT_JOB_ID));
                });
    }

    @Test
    public void scheduleIfNeeded_killSwitchOff_sameJobInfoDontForceSchedule_dontSchedule()
            throws Exception {
        runWithMocks(
                () -> {
                    // Setup
                    disableKillSwitch();
                    final Context mockContext = spy(ApplicationProvider.getApplicationContext());
                    doReturn(mMockJobScheduler)
                            .when(mockContext)
                            .getSystemService(JobScheduler.class);
                    final JobInfo mockJobInfo =
                            new JobInfo.Builder(
                                            VERBOSE_DEBUG_REPORT_JOB_ID,
                                            new ComponentName(
                                                    mockContext,
                                                    VerboseDebugReportingJobService.class))
                                    .setRequiredNetworkType(JobInfo.NETWORK_TYPE_ANY)
                                    .build();
                    doReturn(mockJobInfo)
                            .when(mMockJobScheduler)
                            .getPendingJob(eq(MEASUREMENT_VERBOSE_DEBUG_REPORT_JOB_ID));

                    // Execute
                    VerboseDebugReportingJobService.scheduleIfNeeded(
                            mockContext, /* forceSchedule= */ false);

                    // Validate
                    ExtendedMockito.verify(
                            () -> VerboseDebugReportingJobService.schedule(any(), any()), never());
                    verify(mMockJobScheduler, times(1))
                            .getPendingJob(eq(MEASUREMENT_VERBOSE_DEBUG_REPORT_JOB_ID));
                });
    }

    @Test
    public void scheduleIfNeeded_killSwitchOff_diffJobInfoDontForceSchedule_doesSchedule()
            throws Exception {
        runWithMocks(
                () -> {
                    // Setup
                    disableKillSwitch();
                    final Context mockContext = spy(ApplicationProvider.getApplicationContext());
                    doReturn(mMockJobScheduler)
                            .when(mockContext)
                            .getSystemService(JobScheduler.class);
                    final JobInfo mockJobInfo =
                            new JobInfo.Builder(
                                            VERBOSE_DEBUG_REPORT_JOB_ID,
                                            new ComponentName(
                                                    mockContext,
                                                    VerboseDebugReportingJobService.class))
                                    // Difference
                                    .setRequiredNetworkType(JobInfo.NETWORK_TYPE_CELLULAR)
                                    .build();
                    doReturn(mockJobInfo)
                            .when(mMockJobScheduler)
                            .getPendingJob(eq(MEASUREMENT_VERBOSE_DEBUG_REPORT_JOB_ID));

                    // Execute
                    VerboseDebugReportingJobService.scheduleIfNeeded(
                            mockContext, /* forceSchedule= */ false);

                    // Validate
                    ExtendedMockito.verify(
                            () -> VerboseDebugReportingJobService.schedule(any(), any()));
                    verify(mMockJobScheduler, times(1))
                            .getPendingJob(eq(MEASUREMENT_VERBOSE_DEBUG_REPORT_JOB_ID));
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
                            .getPendingJob(eq(MEASUREMENT_VERBOSE_DEBUG_REPORT_JOB_ID));

                    // Execute
                    VerboseDebugReportingJobService.scheduleIfNeeded(
                            mockContext, /* forceSchedule= */ true);

                    // Validate
                    ExtendedMockito.verify(
                            () -> VerboseDebugReportingJobService.schedule(any(), any()), times(1));
                    verify(mMockJobScheduler, times(1))
                            .getPendingJob(eq(MEASUREMENT_VERBOSE_DEBUG_REPORT_JOB_ID));
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
                            .getPendingJob(eq(MEASUREMENT_VERBOSE_DEBUG_REPORT_JOB_ID));

                    // Execute
                    VerboseDebugReportingJobService.scheduleIfNeeded(
                            mockContext, /* forceSchedule= */ false);

                    // Validate
                    ExtendedMockito.verify(
                            () -> VerboseDebugReportingJobService.schedule(any(), any()), times(1));
                    verify(mMockJobScheduler, times(1))
                            .getPendingJob(eq(MEASUREMENT_VERBOSE_DEBUG_REPORT_JOB_ID));
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
                    verify(mSpyService, timeout(WAIT_IN_MILLIS).times(1)).onStartJob(any());

                    assertNotNull(mSpyService.getFutureForTesting());

                    boolean onStopJobResult =
                            mSpyService.onStopJob(Mockito.mock(JobParameters.class));
                    verify(mSpyService, times(0)).jobFinished(any(), anyBoolean());
                    assertTrue(onStopJobResult);
                    assertTrue(mSpyService.getFutureForTesting().isCancelled());
                });
    }

    private void runWithMocks(TestUtils.RunnableWithThrow execute) throws Exception {
        // Setup mock everything in job
        doReturn(Optional.empty()).when(mMockDatastoreManager).runInTransactionWithResult(any());
        doNothing().when(mSpyService).jobFinished(any(), anyBoolean());
        doReturn(mMockJobScheduler).when(mSpyService).getSystemService(JobScheduler.class);
        doReturn(Mockito.mock(Context.class)).when(mSpyService).getApplicationContext();
        ExtendedMockito.doReturn(mock(EnrollmentDao.class)).when(EnrollmentDao::getInstance);
        ExtendedMockito.doReturn(mMockDatastoreManager)
                .when(DatastoreManagerFactory::getDatastoreManager);
        ExtendedMockito.doNothing()
                .when(() -> VerboseDebugReportingJobService.schedule(any(), any()));
        mocker.mockGetAdServicesJobServiceLogger(mSpyLogger);

        // Execute
        execute.run();
    }

    @Override
    protected void toggleFeature(boolean value) {
        when(mMockFlags.getMeasurementJobVerboseDebugReportingKillSwitch()).thenReturn(!value);
        when(mMockFlags.getMeasurementVerboseDebugReportingJobRequiredNetworkType())
                .thenReturn(JobInfo.NETWORK_TYPE_ANY);
    }
}

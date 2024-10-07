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

package com.android.adservices.service.measurement;

import static com.android.adservices.spe.AdServicesJobInfo.MEASUREMENT_DELETE_UNINSTALLED_JOB;

import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertNotNull;
import static org.junit.Assert.assertTrue;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyBoolean;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.doNothing;
import static org.mockito.Mockito.doReturn;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.spy;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import android.app.job.JobInfo;
import android.app.job.JobParameters;
import android.app.job.JobScheduler;
import android.content.ComponentName;
import android.content.Context;

import androidx.test.core.app.ApplicationProvider;

import com.android.adservices.service.FlagsFactory;
import com.android.adservices.service.common.compat.ServiceCompatUtils;
import com.android.adservices.service.stats.StatsdAdServicesLogger;
import com.android.adservices.shared.testing.JobServiceLoggingCallback;
import com.android.adservices.spe.AdServicesJobServiceLogger;
import com.android.compatibility.common.util.TestUtils;
import com.android.dx.mockito.inline.extended.ExtendedMockito;
import com.android.modules.utils.testing.ExtendedMockitoRule.MockStatic;
import com.android.modules.utils.testing.ExtendedMockitoRule.SpyStatic;

import org.junit.Test;
import org.mockito.ArgumentCaptor;
import org.mockito.Mock;
import org.mockito.Mockito;

import java.util.concurrent.TimeUnit;

@SpyStatic(MeasurementImpl.class)
@SpyStatic(DeleteUninstalledJobService.class)
@SpyStatic(FlagsFactory.class)
@SpyStatic(AdServicesJobServiceLogger.class)
@MockStatic(ServiceCompatUtils.class)
public final class DeleteUninstalledJobServiceTest
        extends MeasurementJobServiceTestCase<DeleteUninstalledJobService> {
    private static final int MEASUREMENT_DELETE_UNINSTALLED_JOB_ID =
            MEASUREMENT_DELETE_UNINSTALLED_JOB.getJobId();
    private static final long WAIT_IN_MILLIS = 1_000L;
    private static final long JOB_PERIOD_MS = TimeUnit.HOURS.toMillis(4);

    @Mock private MeasurementImpl mMockMeasurementImpl;

    @Override
    protected DeleteUninstalledJobService getSpiedService() {
        return new DeleteUninstalledJobService();
    }

    @Test
    public void onStartJob_killSwitchOn_withLogging() throws Exception {
        runWithMocks(
                () -> {
                    JobServiceLoggingCallback onJobDoneCallback = syncLogExecutionStats(mSpyLogger);

                    onStartJob_killSwitchOn();

                    verifyBackgroundJobsSkipLogged(mSpyLogger, onJobDoneCallback);
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
                            .getPendingJob(eq(MEASUREMENT_DELETE_UNINSTALLED_JOB_ID));

                    // Execute
                    DeleteUninstalledJobService.scheduleIfNeeded(
                            mockContext, /* forceSchedule= */ false);

                    // Validate
                    ExtendedMockito.verify(
                            () -> DeleteUninstalledJobService.schedule(any(), any()), never());
                    verify(mMockJobScheduler, never())
                            .getPendingJob(eq(MEASUREMENT_DELETE_UNINSTALLED_JOB_ID));
                });
    }

    @Test
    public void scheduleIfNeeded_sameJobInfo_dontSchedule() throws Exception {
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
                                            MEASUREMENT_DELETE_UNINSTALLED_JOB_ID,
                                            new ComponentName(
                                                    mockContext, DeleteUninstalledJobService.class))
                                    .setPeriodic(JOB_PERIOD_MS)
                                    .setPersisted(true)
                                    .build();
                    doReturn(mockJobInfo)
                            .when(mMockJobScheduler)
                            .getPendingJob(eq(MEASUREMENT_DELETE_UNINSTALLED_JOB_ID));

                    // Execute
                    DeleteUninstalledJobService.scheduleIfNeeded(
                            mockContext, /* forceSchedule= */ false);

                    // Validate
                    ExtendedMockito.verify(
                            () -> DeleteUninstalledJobService.schedule(any(), any()), never());
                    verify(mMockJobScheduler)
                            .getPendingJob(eq(MEASUREMENT_DELETE_UNINSTALLED_JOB_ID));
                });
    }

    @Test
    public void scheduleIfNeeded_diffJobInfo_doesSchedule() throws Exception {
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
                                            MEASUREMENT_DELETE_UNINSTALLED_JOB_ID,
                                            new ComponentName(
                                                    mockContext, DeleteUninstalledJobService.class))
                                    // Difference
                                    .setPeriodic(JOB_PERIOD_MS - 1)
                                    .setPersisted(true)
                                    .build();
                    doReturn(mockJobInfo)
                            .when(mMockJobScheduler)
                            .getPendingJob(eq(MEASUREMENT_DELETE_UNINSTALLED_JOB_ID));

                    // Execute
                    DeleteUninstalledJobService.scheduleIfNeeded(
                            mockContext, /* forceSchedule= */ false);

                    // Validate
                    ExtendedMockito.verify(
                            () -> DeleteUninstalledJobService.schedule(any(), any()));
                    verify(mMockJobScheduler)
                            .getPendingJob(eq(MEASUREMENT_DELETE_UNINSTALLED_JOB_ID));
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
                            .getPendingJob(eq(MEASUREMENT_DELETE_UNINSTALLED_JOB_ID));

                    // Execute
                    DeleteUninstalledJobService.scheduleIfNeeded(
                            mockContext, /* forceSchedule= */ true);

                    // Validate
                    ExtendedMockito.verify(
                            () -> DeleteUninstalledJobService.schedule(any(), any()));
                    verify(mMockJobScheduler)
                            .getPendingJob(eq(MEASUREMENT_DELETE_UNINSTALLED_JOB_ID));
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
                            .getPendingJob(eq(MEASUREMENT_DELETE_UNINSTALLED_JOB_ID));

                    // Execute
                    DeleteUninstalledJobService.scheduleIfNeeded(mockContext, false);

                    // Validate
                    ExtendedMockito.verify(
                            () -> DeleteUninstalledJobService.schedule(any(), any()));
                    verify(mMockJobScheduler)
                            .getPendingJob(eq(MEASUREMENT_DELETE_UNINSTALLED_JOB_ID));
                });
    }

    @Test
    public void testSchedule_jobInfoIsPersisted() throws Exception {
        // Setup
        runWithMocks(
                () -> {
                    disableKillSwitch();
                    final JobScheduler jobScheduler = mock(JobScheduler.class);
                    doReturn(jobScheduler).when(mSpyContext).getSystemService(JobScheduler.class);
                    final ArgumentCaptor<JobInfo> captor = ArgumentCaptor.forClass(JobInfo.class);
                    doReturn(null)
                            .when(jobScheduler)
                            .getPendingJob(eq(MEASUREMENT_DELETE_UNINSTALLED_JOB_ID));

                    // Execute
                    ExtendedMockito.doCallRealMethod()
                            .when(() -> DeleteUninstalledJobService.schedule(any(), any()));
                    DeleteUninstalledJobService.scheduleIfNeeded(mSpyContext, true);

                    // Validate
                    verify(jobScheduler).schedule(captor.capture());
                    assertNotNull(captor.getValue());
                    assertTrue(captor.getValue().isPersisted());
                });
    }

    private void onStartJob_killSwitchOn() throws Exception {
        // Setup
        enableKillSwitch();

        // Execute
        boolean result = mSpyService.onStartJob(Mockito.mock(JobParameters.class));

        // Validate
        assertFalse(result);

        // Allow background thread to execute
        Thread.sleep(WAIT_IN_MILLIS);
        verify(mMockMeasurementImpl, never()).deleteAllUninstalledMeasurementData();
        verify(mSpyService).jobFinished(any(), eq(false));
        verify(mMockJobScheduler).cancel(eq(MEASUREMENT_DELETE_UNINSTALLED_JOB_ID));
    }

    private void onStartJob_killSwitchOff() throws Exception {
        // Setup
        disableKillSwitch();

        // Execute
        boolean result = mSpyService.onStartJob(Mockito.mock(JobParameters.class));

        // Validate
        assertTrue(result);

        // Allow background thread to execute
        Thread.sleep(WAIT_IN_MILLIS);
        verify(mMockMeasurementImpl).deleteAllUninstalledMeasurementData();
        verify(mSpyService).jobFinished(any(), anyBoolean());
        verify(mMockJobScheduler, never()).cancel(eq(MEASUREMENT_DELETE_UNINSTALLED_JOB_ID));
    }

    private void onStartJob_shouldDisableJobTrue() throws Exception {
        // Setup
        ExtendedMockito.doReturn(true)
                .when(
                        () ->
                                ServiceCompatUtils.shouldDisableExtServicesJobOnTPlus(
                                        any(Context.class)));

        // Execute
        boolean result = mSpyService.onStartJob(Mockito.mock(JobParameters.class));

        // Validate
        assertFalse(result);

        // Allow background thread to execute
        Thread.sleep(WAIT_IN_MILLIS);
        verify(mMockMeasurementImpl, never()).deleteAllUninstalledMeasurementData();
        verify(mSpyService).jobFinished(any(), eq(false));
        verify(mMockJobScheduler).cancel(eq(MEASUREMENT_DELETE_UNINSTALLED_JOB_ID));
    }

    private void runWithMocks(TestUtils.RunnableWithThrow execute) throws Exception {
        // Setup mock everything in job
        ExtendedMockito.doReturn(mMockMeasurementImpl).when(MeasurementImpl::getInstance);
        doNothing().when(mSpyService).jobFinished(any(), anyBoolean());
        doReturn(mMockJobScheduler).when(mSpyService).getSystemService(JobScheduler.class);
        ExtendedMockito.doNothing().when(() -> DeleteUninstalledJobService.schedule(any(), any()));

        StatsdAdServicesLogger mockStatsdLogger = mock(StatsdAdServicesLogger.class);
        mSpyLogger = mocker.mockNoOpAdServicesJobServiceLogger(mContext, mMockFlags);

        // Execute
        execute.run();
    }

    @Override
    protected void toggleFeature(boolean value) {
        when(mMockFlags.getMeasurementJobDeleteUninstalledKillSwitch()).thenReturn(!value);
        when(mMockFlags.getMeasurementDeleteUninstalledJobPersisted()).thenReturn(true);
        when(mMockFlags.getMeasurementDeleteUninstalledJobPeriodMs()).thenReturn(JOB_PERIOD_MS);
    }
}

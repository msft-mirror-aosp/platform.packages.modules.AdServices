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

import static com.android.adservices.service.AdServicesConfig.MEASUREMENT_AGGREGATE_FALLBACK_REPORTING_JOB_ID;

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
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;

import android.app.job.JobInfo;
import android.app.job.JobParameters;
import android.app.job.JobScheduler;
import android.content.Context;

import com.android.adservices.data.enrollment.EnrollmentDao;
import com.android.adservices.data.measurement.DatastoreManager;
import com.android.adservices.data.measurement.DatastoreManagerFactory;
import com.android.adservices.service.AdServicesConfig;
import com.android.adservices.service.Flags;
import com.android.adservices.service.FlagsFactory;
import com.android.adservices.service.common.compat.ServiceCompatUtils;
import com.android.compatibility.common.util.TestUtils;
import com.android.dx.mockito.inline.extended.ExtendedMockito;

import org.junit.Before;
import org.junit.Test;
import org.mockito.ArgumentCaptor;
import org.mockito.Mockito;
import org.mockito.MockitoSession;
import org.mockito.quality.Strictness;

import java.util.Optional;
import java.util.concurrent.TimeUnit;

/**
 * Unit test for {@link AggregateFallbackReportingJobService
 */
public class AggregateFallbackReportingJobServiceTest {
    private static final long WAIT_IN_MILLIS = 1_000L;

    private DatastoreManager mMockDatastoreManager;
    private JobScheduler mMockJobScheduler;

    private AggregateFallbackReportingJobService mSpyService;

    @Before
    public void setUp() {
        mSpyService = spy(new AggregateFallbackReportingJobService());
        mMockDatastoreManager = mock(DatastoreManager.class);
        mMockJobScheduler = mock(JobScheduler.class);
    }

    @Test
    public void onStartJob_killSwitchOn() throws Exception {
        runWithMocks(
                () -> {
                    // Setup
                    enableKillSwitch();

                    // Execute
                    boolean result = mSpyService.onStartJob(mock(JobParameters.class));

                    // Validate
                    assertFalse(result);
                    // Allow background thread to execute
                    Thread.sleep(WAIT_IN_MILLIS);
                    verify(mMockDatastoreManager, never()).runInTransactionWithResult(any());
                    verify(mSpyService, times(1)).jobFinished(any(), eq(false));
                    verify(mMockJobScheduler, times(1))
                            .cancel(eq(MEASUREMENT_AGGREGATE_FALLBACK_REPORTING_JOB_ID));
                });
    }

    @Test
    public void onStartJob_killSwitchOff() throws Exception {
        runWithMocks(
                () -> {
                    // Setup
                    disableKillSwitch();

                    // Execute
                    boolean result = mSpyService.onStartJob(mock(JobParameters.class));

                    // Validate
                    assertTrue(result);
                    // Allow background thread to execute
                    Thread.sleep(WAIT_IN_MILLIS);
                    verify(mMockDatastoreManager, times(1)).runInTransactionWithResult(any());
                    ExtendedMockito.verify(mSpyService, times(1)).jobFinished(any(), anyBoolean());
                    verify(mMockJobScheduler, never())
                            .cancel(eq(MEASUREMENT_AGGREGATE_FALLBACK_REPORTING_JOB_ID));
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

                    // Execute
                    boolean result = mSpyService.onStartJob(mock(JobParameters.class));

                    // Validate
                    assertFalse(result);
                    // Allow background thread to execute
                    Thread.sleep(WAIT_IN_MILLIS);
                    verify(mMockDatastoreManager, never()).runInTransactionWithResult(any());
                    verify(mSpyService, times(1)).jobFinished(any(), eq(false));
                    verify(mMockJobScheduler, times(1))
                            .cancel(eq(MEASUREMENT_AGGREGATE_FALLBACK_REPORTING_JOB_ID));
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
                            .getPendingJob(eq(MEASUREMENT_AGGREGATE_FALLBACK_REPORTING_JOB_ID));

                    // Execute
                    AggregateFallbackReportingJobService.scheduleIfNeeded(
                            mockContext, /* forceSchedule = */ false);

                    // Validate
                    ExtendedMockito.verify(
                            () -> AggregateFallbackReportingJobService.schedule(any(), any()),
                            never());
                    verify(mMockJobScheduler, never())
                            .getPendingJob(eq(MEASUREMENT_AGGREGATE_FALLBACK_REPORTING_JOB_ID));
                });
    }

    @Test
    public void scheduleIfNeeded_killSwitchOff_previouslyExecuted_dontForceSchedule_dontSchedule()
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
                            .getPendingJob(eq(MEASUREMENT_AGGREGATE_FALLBACK_REPORTING_JOB_ID));

                    // Execute
                    AggregateFallbackReportingJobService.scheduleIfNeeded(
                            mockContext, /* forceSchedule = */ false);

                    // Validate
                    ExtendedMockito.verify(
                            () -> AggregateFallbackReportingJobService.schedule(any(), any()),
                            never());
                    verify(mMockJobScheduler, times(1))
                            .getPendingJob(eq(MEASUREMENT_AGGREGATE_FALLBACK_REPORTING_JOB_ID));
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
                            .getPendingJob(eq(MEASUREMENT_AGGREGATE_FALLBACK_REPORTING_JOB_ID));

                    // Execute
                    AggregateFallbackReportingJobService.scheduleIfNeeded(
                            mockContext, /* forceSchedule = */ true);

                    // Validate
                    ExtendedMockito.verify(
                            () -> AggregateFallbackReportingJobService.schedule(any(), any()),
                            times(1));
                    verify(mMockJobScheduler, times(1))
                            .getPendingJob(eq(MEASUREMENT_AGGREGATE_FALLBACK_REPORTING_JOB_ID));
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
                    doReturn(/* noJobInfo = */ null)
                            .when(mMockJobScheduler)
                            .getPendingJob(eq(MEASUREMENT_AGGREGATE_FALLBACK_REPORTING_JOB_ID));

                    // Execute
                    AggregateFallbackReportingJobService.scheduleIfNeeded(mockContext, false);

                    // Validate
                    ExtendedMockito.verify(
                            () -> AggregateFallbackReportingJobService.schedule(any(), any()),
                            times(1));
                    verify(mMockJobScheduler, times(1))
                            .getPendingJob(eq(MEASUREMENT_AGGREGATE_FALLBACK_REPORTING_JOB_ID));
                });
    }

    @Test
    public void testSchedule_jobInfoIsPersisted() throws Exception {
        runWithMocks(
                () -> {
                    final JobScheduler jobScheduler = mock(JobScheduler.class);
                    final ArgumentCaptor<JobInfo> captor = ArgumentCaptor.forClass(JobInfo.class);

                    // Execute
                    ExtendedMockito.doCallRealMethod()
                            .when(
                                    () ->
                                            AggregateFallbackReportingJobService.schedule(
                                                    any(), any()));
                    AggregateFallbackReportingJobService.schedule(
                            mock(Context.class), jobScheduler);

                    // Validate
                    verify(jobScheduler, times(1)).schedule(captor.capture());
                    assertNotNull(captor.getValue());
                    assertTrue(captor.getValue().isPersisted());
                });
    }

    private void runWithMocks(TestUtils.RunnableWithThrow execute) throws Exception {
        MockitoSession session =
                ExtendedMockito.mockitoSession()
                        .spyStatic(AdServicesConfig.class)
                        .spyStatic(AggregateFallbackReportingJobService.class)
                        .spyStatic(DatastoreManagerFactory.class)
                        .spyStatic(EnrollmentDao.class)
                        .spyStatic(FlagsFactory.class)
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
            ExtendedMockito.doReturn(TimeUnit.HOURS.toMillis(4))
                    .when(AdServicesConfig::getMeasurementAggregateMainReportingJobPeriodMs);
            ExtendedMockito.doReturn(TimeUnit.HOURS.toMillis(24))
                    .when(AdServicesConfig::getMeasurementAggregateFallbackReportingJobPeriodMs);
            ExtendedMockito.doReturn("http://example.test")
                    .when(AdServicesConfig::getMeasurementAggregateEncryptionKeyCoordinatorUrl);
            ExtendedMockito.doReturn(mock(EnrollmentDao.class))
                    .when(() -> EnrollmentDao.getInstance(any()));
            ExtendedMockito.doReturn(mMockDatastoreManager)
                    .when(() -> DatastoreManagerFactory.getDatastoreManager(any()));
            ExtendedMockito.doNothing()
                    .when(() -> AggregateFallbackReportingJobService.schedule(any(), any()));

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
        Flags mockFlags = Mockito.mock(Flags.class);
        ExtendedMockito.doReturn(mockFlags).when(FlagsFactory::getFlags);
        ExtendedMockito.doReturn(value)
                .when(mockFlags)
                .getMeasurementJobAggregateFallbackReportingKillSwitch();
    }
}

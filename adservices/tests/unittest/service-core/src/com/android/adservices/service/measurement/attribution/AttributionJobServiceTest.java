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

package com.android.adservices.service.measurement.attribution;

import static com.android.adservices.service.AdServicesConfig.MEASUREMENT_ATTRIBUTION_JOB_ID;

import static org.junit.Assert.assertFalse;
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
import android.provider.DeviceConfig;

import com.android.adservices.data.measurement.DatastoreManager;
import com.android.adservices.data.measurement.DatastoreManagerFactory;
import com.android.compatibility.common.util.TestUtils;
import com.android.dx.mockito.inline.extended.ExtendedMockito;
import com.android.modules.utils.testing.TestableDeviceConfig;

import org.junit.Before;
import org.junit.Rule;
import org.junit.Test;
import org.mockito.Mockito;
import org.mockito.MockitoSession;
import org.mockito.quality.Strictness;

import java.util.Optional;

/**
 * Unit test for {@link AttributionJobService
 */
public class AttributionJobServiceTest {
    // This rule is used for configuring P/H flags
    @Rule
    public final TestableDeviceConfig.TestableDeviceConfigRule mDeviceConfigRule =
            new TestableDeviceConfig.TestableDeviceConfigRule();

    private static final long WAIT_IN_MILLIS = 50L;

    private DatastoreManager mMockDatastoreManager;
    private JobScheduler mMockJobScheduler;

    private AttributionJobService mSpyService;

    @Before
    public void setUp() {
        mSpyService = spy(new AttributionJobService());
        mMockDatastoreManager = mock(DatastoreManager.class);
        mMockJobScheduler = mock(JobScheduler.class);
    }

    @Test
    public void onStartJob_killSwitchOn() throws Exception {
        enableKillSwitch();

        runWithMocks(
                () -> {
                    // Execute
                    boolean result = mSpyService.onStartJob(Mockito.mock(JobParameters.class));

                    // Validate
                    assertFalse(result);
                    // Allow background thread to execute
                    Thread.sleep(WAIT_IN_MILLIS);
                    verify(mMockDatastoreManager, never()).runInTransactionWithResult(any());
                    verify(mSpyService, times(1)).jobFinished(any(), eq(false));
                    ExtendedMockito.verify(
                            () -> AttributionJobService.scheduleIfNeeded(any(), anyBoolean()),
                            never());
                    verify(mMockJobScheduler, times(1)).cancel(eq(MEASUREMENT_ATTRIBUTION_JOB_ID));
                });
    }

    @Test
    public void onStartJob_killSwitchOff() throws Exception {
        disableKillSwitch();

        runWithMocks(
                () -> {
                    // Setup
                    ExtendedMockito.doNothing()
                            .when(
                                    () ->
                                            AttributionJobService.scheduleIfNeeded(
                                                    any(), anyBoolean()));

                    // Execute
                    boolean result = mSpyService.onStartJob(Mockito.mock(JobParameters.class));

                    // Validate
                    assertTrue(result);
                    // Allow background thread to execute
                    Thread.sleep(WAIT_IN_MILLIS);
                    verify(mMockDatastoreManager, times(1)).runInTransactionWithResult(any());
                    verify(mSpyService, times(1)).jobFinished(any(), anyBoolean());
                    ExtendedMockito.verify(
                            () -> AttributionJobService.scheduleIfNeeded(any(), anyBoolean()),
                            times(1));
                    verify(mMockJobScheduler, never()).cancel(eq(MEASUREMENT_ATTRIBUTION_JOB_ID));
                });
    }

    @Test
    public void scheduleIfNeeded_killSwitchOn_dontSchedule() throws Exception {
        enableKillSwitch();

        runWithMocks(
                () -> {
                    // Setup
                    final Context mockContext = mock(Context.class);
                    doReturn(mMockJobScheduler)
                            .when(mockContext)
                            .getSystemService(JobScheduler.class);
                    final JobInfo mockJobInfo = mock(JobInfo.class);
                    doReturn(mockJobInfo)
                            .when(mMockJobScheduler)
                            .getPendingJob(eq(MEASUREMENT_ATTRIBUTION_JOB_ID));

                    // Execute
                    AttributionJobService.scheduleIfNeeded(
                            mockContext, /* forceSchedule = */ false);

                    // Validate
                    // Allow background thread to execute
                    Thread.sleep(WAIT_IN_MILLIS);
                    ExtendedMockito.verify(
                            () -> AttributionJobService.schedule(any(), any()), never());
                    verify(mMockJobScheduler, never())
                            .getPendingJob(eq(MEASUREMENT_ATTRIBUTION_JOB_ID));
                });
    }

    @Test
    public void scheduleIfNeeded_killSwitchOff_previouslyExecuted_dontForceSchedule_dontSchedule()
            throws Exception {
        disableKillSwitch();

        runWithMocks(
                () -> {
                    // Setup
                    final Context mockContext = mock(Context.class);
                    doReturn(mMockJobScheduler)
                            .when(mockContext)
                            .getSystemService(JobScheduler.class);
                    final JobInfo mockJobInfo = mock(JobInfo.class);
                    doReturn(mockJobInfo)
                            .when(mMockJobScheduler)
                            .getPendingJob(eq(MEASUREMENT_ATTRIBUTION_JOB_ID));

                    // Execute
                    AttributionJobService.scheduleIfNeeded(
                            mockContext, /* forceSchedule = */ false);

                    // Validate
                    // Allow background thread to execute
                    Thread.sleep(WAIT_IN_MILLIS);
                    ExtendedMockito.verify(
                            () -> AttributionJobService.schedule(any(), any()), never());
                    verify(mMockJobScheduler, times(1))
                            .getPendingJob(eq(MEASUREMENT_ATTRIBUTION_JOB_ID));
                });
    }

    @Test
    public void scheduleIfNeeded_killSwitchOff_previouslyExecuted_forceSchedule_schedule()
            throws Exception {
        disableKillSwitch();

        runWithMocks(
                () -> {
                    // Setup
                    final Context mockContext = mock(Context.class);
                    doReturn(mMockJobScheduler)
                            .when(mockContext)
                            .getSystemService(JobScheduler.class);
                    final JobInfo mockJobInfo = mock(JobInfo.class);
                    doReturn(mockJobInfo)
                            .when(mMockJobScheduler)
                            .getPendingJob(eq(MEASUREMENT_ATTRIBUTION_JOB_ID));

                    // Execute
                    AttributionJobService.scheduleIfNeeded(mockContext, /* forceSchedule = */ true);

                    // Validate
                    // Allow background thread to execute
                    Thread.sleep(WAIT_IN_MILLIS);
                    ExtendedMockito.verify(
                            () -> AttributionJobService.schedule(any(), any()), times(1));
                    verify(mMockJobScheduler, times(1))
                            .getPendingJob(eq(MEASUREMENT_ATTRIBUTION_JOB_ID));
                });
    }

    @Test
    public void scheduleIfNeeded_killSwitchOff_previouslyNotExecuted_dontForceSchedule_schedule()
            throws Exception {
        disableKillSwitch();

        runWithMocks(
                () -> {
                    // Setup
                    final Context mockContext = mock(Context.class);
                    doReturn(mMockJobScheduler)
                            .when(mockContext)
                            .getSystemService(JobScheduler.class);
                    doReturn(/* noJobInfo = */ null)
                            .when(mMockJobScheduler)
                            .getPendingJob(eq(MEASUREMENT_ATTRIBUTION_JOB_ID));

                    // Execute
                    AttributionJobService.scheduleIfNeeded(mockContext, false);

                    // Validate
                    // Allow background thread to execute
                    Thread.sleep(WAIT_IN_MILLIS);
                    ExtendedMockito.verify(
                            () -> AttributionJobService.schedule(any(), any()), times(1));
                    verify(mMockJobScheduler, times(1))
                            .getPendingJob(eq(MEASUREMENT_ATTRIBUTION_JOB_ID));
                });
    }

    private void runWithMocks(TestUtils.RunnableWithThrow execute) throws Exception {
        MockitoSession session =
                ExtendedMockito.mockitoSession()
                        .spyStatic(DatastoreManagerFactory.class)
                        .spyStatic(AttributionJobService.class)
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
            ExtendedMockito.doReturn(mMockDatastoreManager)
                    .when(() -> DatastoreManagerFactory.getDatastoreManager(any()));
            ExtendedMockito.doNothing().when(() -> AttributionJobService.schedule(any(), any()));

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
        DeviceConfig.setProperty(
                DeviceConfig.NAMESPACE_ADSERVICES,
                "measurement_job_attribution_kill_switch",
                Boolean.toString(value),
                /* makeDefault */ false);
    }
}

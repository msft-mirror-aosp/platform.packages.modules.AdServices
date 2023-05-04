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

import static com.android.adservices.service.AdServicesConfig.MEASUREMENT_ATTRIBUTION_FALLBACK_JOB_ID;

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

import android.app.job.JobInfo;
import android.app.job.JobParameters;
import android.app.job.JobScheduler;
import android.content.Context;

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
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.TimeUnit;

/**
 * Unit test for {@link AttributionFallbackJobService
 */
public class AttributionFallbackJobServiceTest {

    private DatastoreManager mMockDatastoreManager;
    private JobScheduler mMockJobScheduler;

    private AttributionFallbackJobService mSpyService;
    private Flags mFlags;

    @Before
    public void setUp() {
        mSpyService = spy(new AttributionFallbackJobService());
        mMockDatastoreManager = mock(DatastoreManager.class);
        mMockJobScheduler = mock(JobScheduler.class);
        mFlags = mock(Flags.class);
    }

    @Test
    public void onStartJob_killSwitchOn() throws Exception {
        runWithMocks(
                () -> {
                    // Setup
                    enableKillSwitch();
                    CountDownLatch countDownLatch = new CountDownLatch(1);
                    doAnswer(invocation -> {
                        countDownLatch.countDown();
                        return null;
                    }).when(mSpyService).jobFinished(any(), anyBoolean());

                    // Execute
                    boolean result = mSpyService.onStartJob(Mockito.mock(JobParameters.class));

                    // Validate
                    assertFalse(result);

                    // Allow background thread to execute
                    countDownLatch.await();
                    verify(mMockDatastoreManager, never()).runInTransactionWithResult(any());
                    verify(mSpyService, times(1)).jobFinished(any(), eq(false));
                    verify(mMockJobScheduler, times(1))
                            .cancel(eq(MEASUREMENT_ATTRIBUTION_FALLBACK_JOB_ID));
                });
    }

    @Test
    public void onStartJob_killSwitchOff() throws Exception {
        runWithMocks(
                () -> {
                    // Setup
                    disableKillSwitch();
                    CountDownLatch countDownLatch = new CountDownLatch(1);
                    doAnswer(invocation -> {
                        countDownLatch.countDown();
                        return null;
                    }).when(mSpyService).jobFinished(any(), anyBoolean());

                    ExtendedMockito.doNothing()
                            .when(
                                    () ->
                                            AttributionFallbackJobService.scheduleIfNeeded(
                                                    any(), anyBoolean()));

                    // Execute
                    boolean result = mSpyService.onStartJob(Mockito.mock(JobParameters.class));

                    // Validate
                    assertTrue(result);
                    // Allow background thread to execute
                    countDownLatch.await();
                    verify(mMockDatastoreManager, times(1)).runInTransactionWithResult(any());
                    verify(mSpyService, times(1)).jobFinished(any(), anyBoolean());
                    verify(mMockJobScheduler, never())
                            .cancel(eq(MEASUREMENT_ATTRIBUTION_FALLBACK_JOB_ID));
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
                    CountDownLatch countDownLatch = new CountDownLatch(1);
                    doAnswer(invocation -> {
                        countDownLatch.countDown();
                        return null;
                    }).when(mSpyService).jobFinished(any(), anyBoolean());

                    // Execute
                    boolean result = mSpyService.onStartJob(Mockito.mock(JobParameters.class));

                    // Validate
                    assertFalse(result);
                    // Allow background thread to execute
                    countDownLatch.await();
                    verify(mMockDatastoreManager, never()).runInTransactionWithResult(any());
                    verify(mSpyService, times(1)).jobFinished(any(), eq(false));
                    verify(mMockJobScheduler, times(1))
                            .cancel(eq(MEASUREMENT_ATTRIBUTION_FALLBACK_JOB_ID));
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
                            .getPendingJob(eq(MEASUREMENT_ATTRIBUTION_FALLBACK_JOB_ID));

                    // Execute
                    AttributionFallbackJobService.scheduleIfNeeded(
                            mockContext, /* forceSchedule = */ false);

                    // Validate
                    ExtendedMockito.verify(
                            () -> AttributionFallbackJobService.schedule(any(), any()), never());
                    verify(mMockJobScheduler, never())
                            .getPendingJob(eq(MEASUREMENT_ATTRIBUTION_FALLBACK_JOB_ID));
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
                            .getPendingJob(eq(MEASUREMENT_ATTRIBUTION_FALLBACK_JOB_ID));

                    // Execute
                    AttributionFallbackJobService.scheduleIfNeeded(
                            mockContext, /* forceSchedule = */ false);

                    // Validate
                    ExtendedMockito.verify(
                            () -> AttributionFallbackJobService.schedule(any(), any()), never());
                    verify(mMockJobScheduler, times(1))
                            .getPendingJob(eq(MEASUREMENT_ATTRIBUTION_FALLBACK_JOB_ID));
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
                            .getPendingJob(eq(MEASUREMENT_ATTRIBUTION_FALLBACK_JOB_ID));

                    // Execute
                    AttributionFallbackJobService.scheduleIfNeeded(
                            mockContext, /* forceSchedule = */ true);

                    // Validate
                    ExtendedMockito.verify(
                            () -> AttributionFallbackJobService.schedule(any(), any()), times(1));
                    verify(mMockJobScheduler, times(1))
                            .getPendingJob(eq(MEASUREMENT_ATTRIBUTION_FALLBACK_JOB_ID));
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
                            .getPendingJob(eq(MEASUREMENT_ATTRIBUTION_FALLBACK_JOB_ID));

                    // Execute
                    AttributionFallbackJobService.scheduleIfNeeded(mockContext, false);

                    // Validate
                    ExtendedMockito.verify(
                            () -> AttributionFallbackJobService.schedule(any(), any()), times(1));
                    verify(mMockJobScheduler, times(1))
                            .getPendingJob(eq(MEASUREMENT_ATTRIBUTION_FALLBACK_JOB_ID));
                });
    }

    @Test
    public void testSchedule_jobInfoIsPersisted() throws Exception {
        runWithMocks(
                () -> {
                    // Setup
                    final JobScheduler jobScheduler = mock(JobScheduler.class);
                    final ArgumentCaptor<JobInfo> captor = ArgumentCaptor.forClass(JobInfo.class);

                    // Execute
                    ExtendedMockito.doCallRealMethod()
                            .when(() -> AttributionFallbackJobService.schedule(any(), any()));
                    AttributionFallbackJobService.schedule(mock(Context.class), jobScheduler);

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
                        .spyStatic(DatastoreManagerFactory.class)
                        .spyStatic(AttributionFallbackJobService.class)
                        .spyStatic(FlagsFactory.class)
                        .mockStatic(ServiceCompatUtils.class)
                        .strictness(Strictness.LENIENT)
                        .startMocking();
        try {
            // Setup mock everything in job
            ExtendedMockito.doReturn(mFlags).when(FlagsFactory::getFlags);
            mMockDatastoreManager = mock(DatastoreManager.class);
            doReturn(Optional.empty())
                    .when(mMockDatastoreManager)
                    .runInTransactionWithResult(any());
            doNothing().when(mSpyService).jobFinished(any(), anyBoolean());
            doReturn(mMockJobScheduler).when(mSpyService).getSystemService(JobScheduler.class);
            doReturn(Mockito.mock(Context.class)).when(mSpyService).getApplicationContext();
            ExtendedMockito.doReturn(mMockDatastoreManager)
                    .when(() -> DatastoreManagerFactory.getDatastoreManager(any()));
            ExtendedMockito.doNothing()
                    .when(() -> AttributionFallbackJobService.schedule(any(), any()));
            ExtendedMockito.doReturn(false)
                    .when(
                            () ->
                                    ServiceCompatUtils.shouldDisableExtServicesJobOnTPlus(
                                            any(Context.class)));
            ExtendedMockito.doReturn(TimeUnit.HOURS.toMillis(1))
                    .when(mFlags)
                    .getMeasurementAttributionFallbackJobPeriodMs();

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
        ExtendedMockito.doReturn(value)
                .when(mFlags)
                .getMeasurementAttributionFallbackJobKillSwitch();
    }
}

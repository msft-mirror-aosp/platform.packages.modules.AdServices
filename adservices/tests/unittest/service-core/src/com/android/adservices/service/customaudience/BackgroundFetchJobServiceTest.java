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

package com.android.adservices.service.customaudience;

import static com.android.adservices.service.AdServicesConfig.FLEDGE_BACKGROUND_FETCH_JOB_ID;
import static com.android.dx.mockito.inline.extended.ExtendedMockito.any;
import static com.android.dx.mockito.inline.extended.ExtendedMockito.doAnswer;
import static com.android.dx.mockito.inline.extended.ExtendedMockito.doCallRealMethod;
import static com.android.dx.mockito.inline.extended.ExtendedMockito.doNothing;
import static com.android.dx.mockito.inline.extended.ExtendedMockito.doReturn;
import static com.android.dx.mockito.inline.extended.ExtendedMockito.doThrow;
import static com.android.dx.mockito.inline.extended.ExtendedMockito.eq;
import static com.android.dx.mockito.inline.extended.ExtendedMockito.never;
import static com.android.dx.mockito.inline.extended.ExtendedMockito.staticMockMarker;
import static com.android.dx.mockito.inline.extended.ExtendedMockito.verify;
import static com.android.dx.mockito.inline.extended.ExtendedMockito.verifyNoMoreInteractions;

import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertNotNull;
import static org.junit.Assert.assertNull;

import android.app.job.JobInfo;
import android.app.job.JobParameters;
import android.app.job.JobScheduler;
import android.content.ComponentName;
import android.content.Context;

import androidx.test.core.app.ApplicationProvider;

import com.android.dx.mockito.inline.extended.ExtendedMockito;

import org.junit.After;
import org.junit.Assume;
import org.junit.Before;
import org.junit.Test;
import org.mockito.Mock;
import org.mockito.MockitoSession;
import org.mockito.Spy;

import java.util.concurrent.CountDownLatch;
import java.util.concurrent.ExecutionException;
import java.util.concurrent.TimeoutException;

public class BackgroundFetchJobServiceTest {
    private static final Context CONTEXT = ApplicationProvider.getApplicationContext();
    private static final JobScheduler JOB_SCHEDULER = CONTEXT.getSystemService(JobScheduler.class);

    @Spy
    private final BackgroundFetchJobService mBgFJobServiceSpy = new BackgroundFetchJobService();

    @Mock private BackgroundFetchWorker mBgFWorkerMock;
    @Mock private JobParameters mJobParametersMock;

    // Set a minimum delay of 1 hour so scheduled jobs don't run immediately
    private static final long MINIMUM_SCHEDULING_DELAY_MS = 60L * 60L * 1000L;

    private MockitoSession mStaticMockSession = null;

    @Before
    public void setup() {
        // The actual scheduling of the job needs to be mocked out because the test application does
        // not have the required permissions to schedule the job with the constraints requested by
        // the BackgroundFetchJobService, and adding them is non-trivial.
        mStaticMockSession =
                ExtendedMockito.mockitoSession()
                        .spyStatic(BackgroundFetchJobService.class)
                        .spyStatic(BackgroundFetchWorker.class)
                        .initMocks(this)
                        .startMocking();

        Assume.assumeNotNull(JOB_SCHEDULER);
        assertNull(
                "Job already scheduled before setup!",
                JOB_SCHEDULER.getPendingJob(FLEDGE_BACKGROUND_FETCH_JOB_ID));
    }

    @After
    public void teardown() {
        JOB_SCHEDULER.cancelAll();
        mStaticMockSession.finishMocking();
    }

    @Test
    public void testOnStartJobUpdateSuccess()
            throws InterruptedException, ExecutionException, TimeoutException {
        CountDownLatch jobFinishedCountDown = new CountDownLatch(1);

        doReturn(mBgFWorkerMock).when(() -> BackgroundFetchWorker.getInstance(any()));
        doNothing().when(mBgFWorkerMock).runBackgroundFetch(any());
        doAnswer(
                        unusedInvocation -> {
                            jobFinishedCountDown.countDown();
                            return null;
                        })
                .when(mBgFJobServiceSpy)
                .jobFinished(mJobParametersMock, false);

        assertFalse(mBgFJobServiceSpy.onStartJob(mJobParametersMock));
        jobFinishedCountDown.await();

        verify(() -> BackgroundFetchWorker.getInstance(mBgFJobServiceSpy));
        verify(mBgFWorkerMock).runBackgroundFetch(any());
        verify(mBgFJobServiceSpy).jobFinished(mJobParametersMock, false);
        verifyNoMoreInteractions(staticMockMarker(BackgroundFetchWorker.class));
    }

    @Test
    public void testOnStartJobUpdateTimeoutHandled()
            throws InterruptedException, ExecutionException, TimeoutException {
        CountDownLatch jobFinishedCountDown = new CountDownLatch(1);

        doReturn(mBgFWorkerMock).when(() -> BackgroundFetchWorker.getInstance(any()));
        doThrow(TimeoutException.class).when(mBgFWorkerMock).runBackgroundFetch(any());
        doAnswer(
                        unusedInvocation -> {
                            jobFinishedCountDown.countDown();
                            return null;
                        })
                .when(mBgFJobServiceSpy)
                .jobFinished(mJobParametersMock, false);

        assertFalse(mBgFJobServiceSpy.onStartJob(mJobParametersMock));
        jobFinishedCountDown.await();

        verify(() -> BackgroundFetchWorker.getInstance(mBgFJobServiceSpy));
        verify(mBgFWorkerMock).runBackgroundFetch(any());
        verify(mBgFJobServiceSpy).jobFinished(mJobParametersMock, false);
        verifyNoMoreInteractions(staticMockMarker(BackgroundFetchWorker.class));
    }

    @Test
    public void testOnStartJobUpdateInterruptedHandled()
            throws InterruptedException, ExecutionException, TimeoutException {
        CountDownLatch jobFinishedCountDown = new CountDownLatch(1);

        doReturn(mBgFWorkerMock).when(() -> BackgroundFetchWorker.getInstance(any()));
        doThrow(InterruptedException.class).when(mBgFWorkerMock).runBackgroundFetch(any());
        doAnswer(
                        unusedInvocation -> {
                            jobFinishedCountDown.countDown();
                            return null;
                        })
                .when(mBgFJobServiceSpy)
                .jobFinished(mJobParametersMock, false);

        assertFalse(mBgFJobServiceSpy.onStartJob(mJobParametersMock));
        jobFinishedCountDown.await();

        verify(() -> BackgroundFetchWorker.getInstance(mBgFJobServiceSpy));
        verify(mBgFWorkerMock).runBackgroundFetch(any());
        verify(mBgFJobServiceSpy).jobFinished(mJobParametersMock, false);
        verifyNoMoreInteractions(staticMockMarker(BackgroundFetchWorker.class));
    }

    @Test
    public void testOnStartJobUpdateExecutionExceptionHandled()
            throws InterruptedException, ExecutionException, TimeoutException {
        CountDownLatch jobFinishedCountDown = new CountDownLatch(1);

        doReturn(mBgFWorkerMock).when(() -> BackgroundFetchWorker.getInstance(any()));
        doThrow(ExecutionException.class).when(mBgFWorkerMock).runBackgroundFetch(any());
        doAnswer(
                        unusedInvocation -> {
                            jobFinishedCountDown.countDown();
                            return null;
                        })
                .when(mBgFJobServiceSpy)
                .jobFinished(mJobParametersMock, false);

        assertFalse(mBgFJobServiceSpy.onStartJob(mJobParametersMock));
        jobFinishedCountDown.await();

        verify(() -> BackgroundFetchWorker.getInstance(mBgFJobServiceSpy));
        verify(mBgFWorkerMock).runBackgroundFetch(any());
        verify(mBgFJobServiceSpy).jobFinished(mJobParametersMock, false);
        verifyNoMoreInteractions(staticMockMarker(BackgroundFetchWorker.class));
    }

    @Test
    public void testScheduleIfNeededSuccess() {
        doCallRealMethod().when(() -> BackgroundFetchJobService.scheduleIfNeeded(any(), eq(false)));
        doNothing().when(() -> BackgroundFetchJobService.schedule(any()));

        BackgroundFetchJobService.scheduleIfNeeded(CONTEXT, false);

        verify(() -> BackgroundFetchJobService.schedule(any()));
        verifyNoMoreInteractions(staticMockMarker(BackgroundFetchWorker.class));
    }

    @Test
    public void testScheduleIfNeededSkipped() {
        JobInfo existingJobInfo =
                new JobInfo.Builder(
                                FLEDGE_BACKGROUND_FETCH_JOB_ID,
                                new ComponentName(CONTEXT, BackgroundFetchJobService.class))
                        .setMinimumLatency(MINIMUM_SCHEDULING_DELAY_MS)
                        .build();
        JOB_SCHEDULER.schedule(existingJobInfo);
        assertNotNull(JOB_SCHEDULER.getPendingJob(FLEDGE_BACKGROUND_FETCH_JOB_ID));

        doCallRealMethod().when(() -> BackgroundFetchJobService.scheduleIfNeeded(any(), eq(false)));

        BackgroundFetchJobService.scheduleIfNeeded(CONTEXT, false);

        verify(() -> BackgroundFetchJobService.schedule(any()), never());
        verifyNoMoreInteractions(staticMockMarker(BackgroundFetchWorker.class));
    }

    @Test
    public void testScheduleIfNeededForceSuccess() {
        JobInfo existingJobInfo =
                new JobInfo.Builder(
                                FLEDGE_BACKGROUND_FETCH_JOB_ID,
                                new ComponentName(CONTEXT, BackgroundFetchJobService.class))
                        .setMinimumLatency(MINIMUM_SCHEDULING_DELAY_MS)
                        .build();
        JOB_SCHEDULER.schedule(existingJobInfo);
        assertNotNull(JOB_SCHEDULER.getPendingJob(FLEDGE_BACKGROUND_FETCH_JOB_ID));

        doCallRealMethod().when(() -> BackgroundFetchJobService.scheduleIfNeeded(any(), eq(true)));
        doNothing().when(() -> BackgroundFetchJobService.schedule(any()));

        BackgroundFetchJobService.scheduleIfNeeded(CONTEXT, true);

        verify(() -> BackgroundFetchJobService.schedule(any()));
        verifyNoMoreInteractions(staticMockMarker(BackgroundFetchWorker.class));
    }
}

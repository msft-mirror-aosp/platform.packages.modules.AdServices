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

package com.android.adservices.download;

import static com.android.adservices.download.MddTaskScheduler.KEY_MDD_TASK_TAG;
import static com.android.adservices.service.AdServicesConfig.MDD_WIFI_CHARGING_PERIODIC_TASK_JOB_ID;
import static com.android.dx.mockito.inline.extended.ExtendedMockito.doNothing;
import static com.android.dx.mockito.inline.extended.ExtendedMockito.staticMockMarker;

import static com.google.android.libraries.mobiledatadownload.TaskScheduler.WIFI_CHARGING_PERIODIC_TASK;
import static com.google.common.truth.Truth.assertThat;
import static com.google.common.util.concurrent.Futures.immediateFuture;

import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertNotNull;
import static org.junit.Assert.assertNull;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.doReturn;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoMoreInteractions;
import static org.mockito.Mockito.verifyZeroInteractions;
import static org.mockito.Mockito.when;

import android.app.job.JobInfo;
import android.app.job.JobParameters;
import android.app.job.JobScheduler;
import android.content.ComponentName;
import android.content.Context;
import android.os.PersistableBundle;

import androidx.test.core.app.ApplicationProvider;

import com.android.adservices.service.Flags;
import com.android.adservices.service.FlagsFactory;
import com.android.dx.mockito.inline.extended.ExtendedMockito;

import com.google.android.libraries.mobiledatadownload.MobileDataDownload;

import org.junit.After;
import org.junit.Before;
import org.junit.Test;
import org.mockito.Mock;
import org.mockito.MockitoAnnotations;
import org.mockito.MockitoSession;
import org.mockito.Spy;

import java.util.concurrent.CountDownLatch;
import java.util.concurrent.TimeUnit;

/** Unit tests for {@link com.android.adservices.download.MddJobService} */
public class MddJobServiceTest {
    private static final int BACKGROUND_TASK_TIMEOUT_MS = 5_000;

    private static final Context CONTEXT = ApplicationProvider.getApplicationContext();
    private static final JobScheduler JOB_SCHEDULER = CONTEXT.getSystemService(JobScheduler.class);

    @Spy private MddJobService mSpyMddJobService;
    private MockitoSession mStaticMockSession;

    @Mock JobParameters mMockJobParameters;

    @Mock MobileDataDownload mMockMdd;
    @Mock MobileDataDownloadFactory mMockMddFactory;
    @Mock Flags mMockFlags;

    @Before
    public void setup() {
        MockitoAnnotations.initMocks(this);

        // Start a mockitoSession to mock static method.
        mStaticMockSession =
                ExtendedMockito.mockitoSession()
                        .spyStatic(MddJobService.class)
                        .spyStatic(MobileDataDownloadFactory.class)
                        .spyStatic(FlagsFactory.class)
                        .startMocking();

        // Mock JobScheduler invocation in EpochJobService
        assertThat(JOB_SCHEDULER).isNotNull();
        assertNull(
                "Job already scheduled before setup!",
                JOB_SCHEDULER.getPendingJob(MDD_WIFI_CHARGING_PERIODIC_TASK_JOB_ID));

        ExtendedMockito.doReturn(JOB_SCHEDULER)
                .when(mSpyMddJobService)
                .getSystemService(JobScheduler.class);

        // MDD Task Tag.
        PersistableBundle bundle = new PersistableBundle();
        bundle.putString(KEY_MDD_TASK_TAG, WIFI_CHARGING_PERIODIC_TASK);
        when(mMockJobParameters.getExtras()).thenReturn(bundle);
    }

    @After
    public void teardown() {
        JOB_SCHEDULER.cancelAll();
        mStaticMockSession.finishMocking();
    }

    @Test
    public void testOnStartJob_killswitchIsOff() throws InterruptedException {
        // Mock static method FlagsFactory.getFlags() to return Mock Flags.
        ExtendedMockito.doReturn(mMockFlags).when(FlagsFactory::getFlags);
        // Killswitch is off.
        doReturn(false).when(mMockFlags).getMddBackgroundTaskKillSwitch();

        // Add a countDownLatch to ensure background thread gets executed
        CountDownLatch countDownLatch = new CountDownLatch(1);

        ExtendedMockito.doReturn(mMockMdd)
                .when(() -> MobileDataDownloadFactory.getMdd(any(Context.class), any(Flags.class)));

        mSpyMddJobService.onStartJob(mMockJobParameters);

        // The countDownLatch doesn't get decreased and waits until timeout.
        assertThat(countDownLatch.await(BACKGROUND_TASK_TIMEOUT_MS, TimeUnit.MILLISECONDS))
                .isFalse();

        // Check that Mdd.handleTask is executed.
        ExtendedMockito.verify(
                () -> MobileDataDownloadFactory.getMdd(any(Context.class), any(Flags.class)));
        verify(mMockMdd).handleTask(WIFI_CHARGING_PERIODIC_TASK);
    }

    @Test
    public void testOnStartJob_killswitchIsOn() throws InterruptedException {
        // Mock static method FlagsFactory.getFlags() to return Mock Flags.
        ExtendedMockito.doReturn(mMockFlags).when(FlagsFactory::getFlags);
        // Killswitch is on.
        doReturn(true).when(mMockFlags).getMddBackgroundTaskKillSwitch();

        doNothing().when(mSpyMddJobService).jobFinished(mMockJobParameters, false);

        // Schedule the job to assert after starting that the scheduled job has been cancelled
        JobInfo existingJobInfo =
                new JobInfo.Builder(
                                MDD_WIFI_CHARGING_PERIODIC_TASK_JOB_ID,
                                new ComponentName(CONTEXT, MddJobService.class))
                        .setRequiresCharging(true)
                        .setPeriodic(/* periodMs */ 10000, /* flexMs */ 1000)
                        .build();
        JOB_SCHEDULER.schedule(existingJobInfo);
        assertNotNull(JOB_SCHEDULER.getPendingJob(MDD_WIFI_CHARGING_PERIODIC_TASK_JOB_ID));

        // Now verify that when the Job starts, it will unschedule itself.
        assertFalse(mSpyMddJobService.onStartJob(mMockJobParameters));

        assertNull(JOB_SCHEDULER.getPendingJob(MDD_WIFI_CHARGING_PERIODIC_TASK_JOB_ID));

        verify(mSpyMddJobService).jobFinished(mMockJobParameters, false);
        verifyNoMoreInteractions(staticMockMarker(MobileDataDownloadFactory.class));
    }

    @Test
    public void testSchedule_killswitchOff() throws InterruptedException {
        // Mock static method FlagsFactory.getFlags() to return Mock Flags.
        ExtendedMockito.doReturn(mMockFlags).when(FlagsFactory::getFlags);
        // Killswitch is off.
        doReturn(false).when(mMockFlags).getMddBackgroundTaskKillSwitch();

        // Add a countDownLatch to ensure background thread gets executed
        CountDownLatch countDownLatch = new CountDownLatch(1);

        ExtendedMockito.doReturn(mMockMdd)
                .when(() -> MobileDataDownloadFactory.getMdd(any(Context.class), any(Flags.class)));

        when(mMockMdd.schedulePeriodicBackgroundTasks()).thenReturn(immediateFuture(null));

        mSpyMddJobService.schedule(CONTEXT);

        // The countDownLatch doesn't get decreased and waits until timeout.
        assertThat(countDownLatch.await(BACKGROUND_TASK_TIMEOUT_MS, TimeUnit.MILLISECONDS))
                .isFalse();

        // Check that Mdd.handleTask is executed.
        ExtendedMockito.verify(
                () -> MobileDataDownloadFactory.getMdd(any(Context.class), any(Flags.class)));
        verify(mMockMdd).schedulePeriodicBackgroundTasks();
    }

    @Test
    public void testSchedule_killswitchOn() {
        // Mock static method FlagsFactory.getFlags() to return Mock Flags.
        ExtendedMockito.doReturn(mMockFlags).when(FlagsFactory::getFlags);
        // Killswitch is off.
        doReturn(true).when(mMockFlags).getMddBackgroundTaskKillSwitch();

        mSpyMddJobService.schedule(CONTEXT);

        verifyZeroInteractions(staticMockMarker(MobileDataDownloadFactory.class));
    }

    @Test
    public void testOnStopJob() {
        // Verify nothing throws
        mSpyMddJobService.onStopJob(mMockJobParameters);
    }

    // TODO: Implement after the decision between WorkManager and JobScheduler is made.
    @Test
    public void testSchedule() {}
}

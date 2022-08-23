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

package com.android.adservices.service;

import static com.android.adservices.service.AdServicesConfig.MAINTENANCE_JOB_ID;
import static com.android.dx.mockito.inline.extended.ExtendedMockito.doNothing;
import static com.android.dx.mockito.inline.extended.ExtendedMockito.staticMockMarker;

import static com.google.common.truth.Truth.assertThat;

import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertNotNull;
import static org.junit.Assert.assertNull;
import static org.mockito.ArgumentMatchers.anyLong;
import static org.mockito.Mockito.any;
import static org.mockito.Mockito.doReturn;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoMoreInteractions;

import android.app.job.JobInfo;
import android.app.job.JobParameters;
import android.app.job.JobScheduler;
import android.content.ComponentName;
import android.content.Context;

import androidx.test.core.app.ApplicationProvider;

import com.android.adservices.service.topics.AppUpdateManager;
import com.android.adservices.service.topics.BlockedTopicsManager;
import com.android.adservices.service.topics.CacheManager;
import com.android.adservices.service.topics.EpochJobService;
import com.android.adservices.service.topics.EpochManager;
import com.android.adservices.service.topics.TopicsWorker;
import com.android.dx.mockito.inline.extended.ExtendedMockito;

import org.junit.After;
import org.junit.Before;
import org.junit.Test;
import org.mockito.Mock;
import org.mockito.MockitoAnnotations;
import org.mockito.MockitoSession;
import org.mockito.Spy;

/** Unit tests for {@link com.android.adservices.service.MaintenanceJobService} */
@SuppressWarnings("ConstantConditions")
public class MaintenanceJobServiceTest {
    private static final int BACKGROUND_THREAD_TIMEOUT_MS = 5_000;
    private static final Context CONTEXT = ApplicationProvider.getApplicationContext();
    private static final JobScheduler JOB_SCHEDULER = CONTEXT.getSystemService(JobScheduler.class);
    private static final Flags TEST_FLAGS = FlagsFactory.getFlagsForTest();

    @Spy private MaintenanceJobService mSpyMaintenanceJobService;
    private MockitoSession mStaticMockSession;

    // Mock EpochManager and CacheManager as the methods called are tested in corresponding
    // unit test. In this test, only verify whether specific method is initiated.
    @Mock EpochManager mMockEpochManager;
    @Mock CacheManager mMockCacheManager;
    @Mock BlockedTopicsManager mBlockedTopicsManager;
    @Mock AppUpdateManager mMockAppUpdateManager;
    @Mock JobParameters mMockJobParameters;
    @Mock Flags mMockFlags;

    @Before
    public void setup() {
        MockitoAnnotations.initMocks(this);

        // Start a mockitoSession to mock static method
        mStaticMockSession =
                ExtendedMockito.mockitoSession()
                        .spyStatic(MaintenanceJobService.class)
                        .spyStatic(TopicsWorker.class)
                        .spyStatic(FlagsFactory.class)
                        .startMocking();

        // Mock JobScheduler invocation in EpochJobService
        assertThat(JOB_SCHEDULER).isNotNull();
        ExtendedMockito.doReturn(JOB_SCHEDULER)
                .when(mSpyMaintenanceJobService)
                .getSystemService(JobScheduler.class);
    }

    @After
    public void teardown() {
        JOB_SCHEDULER.cancelAll();
        mStaticMockSession.finishMocking();
    }

    @Test
    public void testOnStartJob_killSwitchOff() throws InterruptedException {
        final TopicsWorker topicsWorker =
                new TopicsWorker(
                        mMockEpochManager,
                        mMockCacheManager,
                        mBlockedTopicsManager,
                        mMockAppUpdateManager,
                        TEST_FLAGS);

        // Killswitch is off.
        doReturn(false).when(mMockFlags).getTopicsKillSwitch();

        // Mock static method FlagsFactory.getFlags() to return Mock Flags.
        ExtendedMockito.doReturn(mMockFlags).when(FlagsFactory::getFlags);

        // Mock static method AppUpdateWorker.getInstance, let it return the local
        // appUpdateWorker in order to get a test instance.
        ExtendedMockito.doReturn(topicsWorker)
                .when(() -> TopicsWorker.getInstance(any(Context.class)));

        mSpyMaintenanceJobService.onStartJob(mMockJobParameters);

        // Grant some time to allow background thread to execute
        Thread.sleep(BACKGROUND_THREAD_TIMEOUT_MS);

        ExtendedMockito.verify(() -> TopicsWorker.getInstance(any(Context.class)));
        verify(mMockAppUpdateManager).reconcileUninstalledApps(any(Context.class));
        verify(mMockAppUpdateManager)
                .reconcileInstalledApps(any(Context.class), /* currentEpochId */ anyLong());
    }

    @Test
    public void testOnStartJob_killSwitchOn() throws InterruptedException {
        // Killswitch on.
        doReturn(true).when(mMockFlags).getTopicsKillSwitch();

        // Mock static method FlagsFactory.getFlags() to return Mock Flags.
        ExtendedMockito.doReturn(mMockFlags).when(FlagsFactory::getFlags);

        doNothing().when(mSpyMaintenanceJobService).jobFinished(mMockJobParameters, false);

        // Schedule the job to assert after starting that the scheduled job has been cancelled
        JobInfo existingJobInfo =
                new JobInfo.Builder(
                                MAINTENANCE_JOB_ID,
                                new ComponentName(CONTEXT, EpochJobService.class))
                        .setRequiresCharging(true)
                        .setPeriodic(
                                /* maintenanceJobPeriodMs */ 10000, /* maintenanceJobFlexMs */ 1000)
                        .build();
        JOB_SCHEDULER.schedule(existingJobInfo);
        assertNotNull(JOB_SCHEDULER.getPendingJob(MAINTENANCE_JOB_ID));

        // Now verify that when the Job starts, it will unschedule itself.
        assertFalse(mSpyMaintenanceJobService.onStartJob(mMockJobParameters));

        assertNull(JOB_SCHEDULER.getPendingJob(MAINTENANCE_JOB_ID));

        verify(mSpyMaintenanceJobService).jobFinished(mMockJobParameters, false);
        verifyNoMoreInteractions(staticMockMarker(TopicsWorker.class));
    }

    @Test
    public void testOnStartJob_globalKillswitchOverridesAll() throws InterruptedException {
        // Global Killswitch is on.
        doReturn(true).when(mMockFlags).getGlobalKillSwitch();

        // Killswitch off.
        doReturn(false).when(mMockFlags).getTopicsKillSwitch();

        // Mock static method FlagsFactory.getFlags() to return Mock Flags.
        ExtendedMockito.doReturn(mMockFlags).when(FlagsFactory::getFlags);

        mSpyMaintenanceJobService.onStartJob(mMockJobParameters);

        // Grant some time to allow background thread to execute
        Thread.sleep(BACKGROUND_THREAD_TIMEOUT_MS);

        // When the kill switch is on, the MaintenanceJobService exits early and do nothing.
    }

    @Test
    public void testOnStopJob() {
        // Verify nothing throws
        mSpyMaintenanceJobService.onStopJob(mMockJobParameters);
    }

    @Test
    public void testScheduleIfNeeded_Success() {
        // Mock static method FlagsFactory.getFlags() to return test Flags.
        ExtendedMockito.doReturn(TEST_FLAGS).when(FlagsFactory::getFlags);

        // The first invocation of scheduleIfNeeded() schedules the job.
        assertThat(MaintenanceJobService.scheduleIfNeeded(CONTEXT, /* forceSchedule */ false))
                .isTrue();
    }

    @Test
    public void testScheduleIfNeeded_ScheduledWithSameParameters() {
        // Mock static method FlagsFactory.getFlags() to return test Flags.
        ExtendedMockito.doReturn(TEST_FLAGS).when(FlagsFactory::getFlags);

        // The first invocation of scheduleIfNeeded() schedules the job.
        assertThat(MaintenanceJobService.scheduleIfNeeded(CONTEXT, /* forceSchedule */ false))
                .isTrue();
        assertThat(JOB_SCHEDULER.getPendingJob(MAINTENANCE_JOB_ID)).isNotNull();

        // The second invocation of scheduleIfNeeded() with same parameters skips the scheduling.
        assertThat(MaintenanceJobService.scheduleIfNeeded(CONTEXT, /* forceSchedule */ false))
                .isFalse();
    }

    @Test
    public void testScheduleIfNeeded_ScheduledWithDifferentParameters() {
        // Mock Flags in order to change values within this test
        doReturn(TEST_FLAGS.getMaintenanceJobPeriodMs())
                .when(mMockFlags)
                .getMaintenanceJobPeriodMs();
        doReturn(TEST_FLAGS.getMaintenanceJobFlexMs()).when(mMockFlags).getMaintenanceJobFlexMs();
        ExtendedMockito.doReturn(mMockFlags).when(FlagsFactory::getFlags);

        // The first invocation of scheduleIfNeeded() schedules the job.
        assertThat(MaintenanceJobService.scheduleIfNeeded(CONTEXT, /* forceSchedule */ false))
                .isTrue();
        assertThat(JOB_SCHEDULER.getPendingJob(MAINTENANCE_JOB_ID)).isNotNull();

        // Change the value of a parameter so that the second invocation of scheduleIfNeeded()
        // schedules the job.
        doReturn(TEST_FLAGS.getMaintenanceJobFlexMs() + 1)
                .when(mMockFlags)
                .getMaintenanceJobFlexMs();
        assertThat(MaintenanceJobService.scheduleIfNeeded(CONTEXT, /* forceSchedule */ false))
                .isTrue();
    }

    @Test
    public void testScheduleIfNeeded_forceRun() {
        // Mock static method FlagsFactory.getFlags() to return test Flags.
        ExtendedMockito.doReturn(TEST_FLAGS).when(FlagsFactory::getFlags);

        // The first invocation of scheduleIfNeeded() schedules the job.
        assertThat(MaintenanceJobService.scheduleIfNeeded(CONTEXT, /* forceSchedule */ false))
                .isTrue();
        assertThat(JOB_SCHEDULER.getPendingJob(MAINTENANCE_JOB_ID)).isNotNull();

        // The second invocation of scheduleIfNeeded() with same parameters skips the scheduling.
        assertThat(MaintenanceJobService.scheduleIfNeeded(CONTEXT, /* forceSchedule */ false))
                .isFalse();

        // The third invocation of scheduleIfNeeded() is forced and re-schedules the job.
        assertThat(MaintenanceJobService.scheduleIfNeeded(CONTEXT, /* forceSchedule */ true))
                .isTrue();
    }

    @Test
    public void testScheduleIfNeeded_scheduledWithKillSwichOn() {
        // Killswitch is on.
        doReturn(true).when(mMockFlags).getTopicsKillSwitch();

        // Mock static method FlagsFactory.getFlags() to return Mock Flags.
        ExtendedMockito.doReturn(mMockFlags).when(FlagsFactory::getFlags);

        // The first invocation of scheduleIfNeeded() schedules the job.
        assertThat(EpochJobService.scheduleIfNeeded(CONTEXT, /* forceSchedule */ false)).isFalse();
        assertThat(JOB_SCHEDULER.getPendingJob(MAINTENANCE_JOB_ID)).isNull();
    }
}

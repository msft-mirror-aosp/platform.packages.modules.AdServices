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

package com.android.adservices.service.topics;

import static com.android.adservices.service.AdServicesConfig.TOPICS_EPOCH_JOB_ID;

import static com.google.common.truth.Truth.assertThat;

import static org.mockito.Mockito.any;
import static org.mockito.Mockito.doReturn;
import static org.mockito.Mockito.spy;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;

import android.app.job.JobParameters;
import android.app.job.JobScheduler;
import android.content.Context;

import androidx.test.core.app.ApplicationProvider;

import com.android.adservices.service.Flags;
import com.android.adservices.service.FlagsFactory;
import com.android.dx.mockito.inline.extended.ExtendedMockito;

import org.junit.After;
import org.junit.Before;
import org.junit.Test;
import org.mockito.Mock;
import org.mockito.MockitoAnnotations;
import org.mockito.MockitoSession;

import java.util.concurrent.CountDownLatch;
import java.util.concurrent.TimeUnit;

/** Unit tests for {@link com.android.adservices.service.topics.EpochJobService} */
@SuppressWarnings("ConstantConditions")
public class EpochJobServiceTest {
    private static final int BINDER_CONNECTION_TIMEOUT_MS = 5_000;
    private static final Context CONTEXT = ApplicationProvider.getApplicationContext();
    private static final JobScheduler JOB_SCHEDULER = CONTEXT.getSystemService(JobScheduler.class);
    private static final Flags TEST_FLAGS = FlagsFactory.getFlagsForTest();

    private EpochJobService mEpochJobService;
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

        mEpochJobService = spy(new EpochJobService());

        // Start a mockitoSession to mock static method
        mStaticMockSession =
                ExtendedMockito.mockitoSession()
                        .spyStatic(EpochJobService.class)
                        .spyStatic(TopicsWorker.class)
                        .spyStatic(FlagsFactory.class)
                        .startMocking();

        // Mock JobScheduler invocation in EpochJobService
        assertThat(JOB_SCHEDULER).isNotNull();
        ExtendedMockito.doReturn(JOB_SCHEDULER)
                .when(mEpochJobService)
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
                        mMockFlags);
        // Add a countDownLatch to ensure background thread gets executed
        CountDownLatch countDownLatch = new CountDownLatch(1);

        // Killswitch is off.
        doReturn(false).when(mMockFlags).getTopicsKillSwitch();

        // Mock static method FlagsFactory.getFlags() to return Mock Flags.
        ExtendedMockito.doReturn(mMockFlags).when(FlagsFactory::getFlags);

        // Mock static method TopicsWorker.getInstance, let it return the local topicsWorker
        // in order to get a test instance.
        ExtendedMockito.doReturn(topicsWorker)
                .when(() -> TopicsWorker.getInstance(any(Context.class)));

        mEpochJobService.onStartJob(mMockJobParameters);

        // The countDownLatch doesn't get decreased and waits until timeout.
        assertThat(countDownLatch.await(BINDER_CONNECTION_TIMEOUT_MS, TimeUnit.MILLISECONDS))
                .isFalse();

        // Check that processEpoch() and loadCache() are executed to justify
        // TopicsWorker.computeEpoch() is executed.
        ExtendedMockito.verify(() -> TopicsWorker.getInstance(any(Context.class)));
        verify(mMockEpochManager).processEpoch();
        verify(mMockCacheManager).loadCache();
    }

    @Test
    public void testOnStartJob_killSwitchOn() throws InterruptedException {
        // Add a countDownLatch to ensure background thread gets executed
        CountDownLatch countDownLatch = new CountDownLatch(1);

        // Killswitch is on.
        doReturn(true).when(mMockFlags).getTopicsKillSwitch();

        // Mock static method FlagsFactory.getFlags() to return Mock Flags.
        ExtendedMockito.doReturn(mMockFlags).when(FlagsFactory::getFlags);

        mEpochJobService.onStartJob(mMockJobParameters);

        // The countDownLatch doesn't get decreased and waits until timeout.
        assertThat(countDownLatch.await(BINDER_CONNECTION_TIMEOUT_MS, TimeUnit.MILLISECONDS))
                .isFalse();

        // When the kill switch is on, the EpochJobService exits early and do nothing.
    }

    @Test
    public void testOnStartJob_globalKillSwitchOverridesAll() throws InterruptedException {
        // Add a countDownLatch to ensure background thread gets executed
        CountDownLatch countDownLatch = new CountDownLatch(1);

        // Global Killswitch is on.
        doReturn(true).when(mMockFlags).getGlobalKillSwitch();

        // Topics API Killswitch off but is overridden by global killswitch.
        doReturn(false).when(mMockFlags).getTopicsKillSwitch();

        // Mock static method FlagsFactory.getFlags() to return Mock Flags.
        ExtendedMockito.doReturn(mMockFlags).when(FlagsFactory::getFlags);

        mEpochJobService.onStartJob(mMockJobParameters);

        // The countDownLatch doesn't get decreased and waits until timeout.
        assertThat(countDownLatch.await(BINDER_CONNECTION_TIMEOUT_MS, TimeUnit.MILLISECONDS))
                .isFalse();

        // When the kill switch is on, the EpochJobService exits early and do nothing.
    }

    @Test
    public void testOnStopJob() {
        // Verify nothing throws
        mEpochJobService.onStopJob(mMockJobParameters);
    }

    @Test
    public void testScheduleIfNeeded_Success() {
        // Mock static method FlagsFactory.getFlags() to return test Flags.
        ExtendedMockito.doReturn(TEST_FLAGS).when(FlagsFactory::getFlags);

        // The first invocation of scheduleIfNeeded() schedules the job.
        assertThat(EpochJobService.scheduleIfNeeded(CONTEXT, /* forceSchedule */ false)).isTrue();
        ExtendedMockito.verify(FlagsFactory::getFlags, times(2));
    }

    @Test
    public void testScheduleIfNeeded_ScheduledWithSameParameters() {
        // Mock static method FlagsFactory.getFlags() to return test Flags.
        ExtendedMockito.doReturn(TEST_FLAGS).when(FlagsFactory::getFlags);

        // The first invocation of scheduleIfNeeded() schedules the job.
        assertThat(EpochJobService.scheduleIfNeeded(CONTEXT, /* forceSchedule */ false)).isTrue();
        assertThat(JOB_SCHEDULER.getPendingJob(TOPICS_EPOCH_JOB_ID)).isNotNull();

        // The second invocation of scheduleIfNeeded() with same parameters skips the scheduling.
        assertThat(EpochJobService.scheduleIfNeeded(CONTEXT, /* forceSchedule */ false)).isFalse();
        ExtendedMockito.verify(FlagsFactory::getFlags, times(4));
    }

    @Test
    public void testScheduleIfNeeded_ScheduledWithDifferentParameters() {
        // Mock Flags in order to change values within this test
        doReturn(TEST_FLAGS.getTopicsEpochJobPeriodMs())
                .when(mMockFlags)
                .getTopicsEpochJobPeriodMs();
        doReturn(TEST_FLAGS.getTopicsEpochJobFlexMs()).when(mMockFlags).getTopicsEpochJobFlexMs();
        ExtendedMockito.doReturn(mMockFlags).when(FlagsFactory::getFlags);

        // The first invocation of scheduleIfNeeded() schedules the job.
        assertThat(EpochJobService.scheduleIfNeeded(CONTEXT, /* forceSchedule */ false)).isTrue();
        assertThat(JOB_SCHEDULER.getPendingJob(TOPICS_EPOCH_JOB_ID)).isNotNull();

        // Change the value of a parameter so that the second invocation of scheduleIfNeeded()
        // schedules the job.
        doReturn(TEST_FLAGS.getTopicsEpochJobFlexMs() + 1)
                .when(mMockFlags)
                .getTopicsEpochJobFlexMs();
        assertThat(EpochJobService.scheduleIfNeeded(CONTEXT, /* forceSchedule */ false)).isTrue();

        ExtendedMockito.verify(FlagsFactory::getFlags, times(4));
    }

    @Test
    public void testScheduleIfNeeded_forceRun() {
        // Mock static method FlagsFactory.getFlags() to return test Flags.
        ExtendedMockito.doReturn(TEST_FLAGS).when(FlagsFactory::getFlags);

        // The first invocation of scheduleIfNeeded() schedules the job.
        assertThat(EpochJobService.scheduleIfNeeded(CONTEXT, /* forceSchedule */ false)).isTrue();
        assertThat(JOB_SCHEDULER.getPendingJob(TOPICS_EPOCH_JOB_ID)).isNotNull();

        // The second invocation of scheduleIfNeeded() with same parameters skips the scheduling.
        assertThat(EpochJobService.scheduleIfNeeded(CONTEXT, /* forceSchedule */ false)).isFalse();

        // The third invocation of scheduleIfNeeded() is forced and re-schedules the job.
        assertThat(EpochJobService.scheduleIfNeeded(CONTEXT, /* forceSchedule */ true)).isTrue();

        ExtendedMockito.verify(FlagsFactory::getFlags, times(6));
    }
}

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

import static com.android.adservices.mockito.ExtendedMockitoExpectations.mockAdServicesJobServiceLogger;
import static com.android.adservices.mockito.MockitoExpectations.mockBackgroundJobsLoggingKillSwitch;
import static com.android.adservices.mockito.MockitoExpectations.syncLogExecutionStats;
import static com.android.adservices.mockito.MockitoExpectations.syncPersistJobExecutionData;
import static com.android.adservices.mockito.MockitoExpectations.verifyBackgroundJobsSkipLogged;
import static com.android.adservices.mockito.MockitoExpectations.verifyJobFinishedLogged;
import static com.android.adservices.mockito.MockitoExpectations.verifyLoggingNotHappened;
import static com.android.adservices.mockito.MockitoExpectations.verifyOnStopJobLogged;
import static com.android.adservices.service.stats.AdServicesStatsLog.AD_SERVICES_ERROR_REPORTED__ERROR_CODE__TOPICS_API_DISABLED;
import static com.android.adservices.service.stats.AdServicesStatsLog.AD_SERVICES_ERROR_REPORTED__PPAPI_NAME__TOPICS;
import static com.android.adservices.shared.spe.JobServiceConstants.SCHEDULING_RESULT_CODE_SKIPPED;
import static com.android.adservices.shared.spe.JobServiceConstants.SCHEDULING_RESULT_CODE_SUCCESSFUL;
import static com.android.adservices.spe.AdServicesJobInfo.TOPICS_EPOCH_JOB;
import static com.android.dx.mockito.inline.extended.ExtendedMockito.doNothing;
import static com.android.dx.mockito.inline.extended.ExtendedMockito.doReturn;
import static com.android.dx.mockito.inline.extended.ExtendedMockito.staticMockMarker;
import static com.android.dx.mockito.inline.extended.ExtendedMockito.verify;

import static com.google.common.truth.Truth.assertThat;
import static com.google.common.truth.Truth.assertWithMessage;

import static org.mockito.Mockito.any;
import static org.mockito.Mockito.verifyNoMoreInteractions;
import static org.mockito.Mockito.when;

import android.app.job.JobInfo;
import android.app.job.JobParameters;
import android.app.job.JobScheduler;
import android.content.ComponentName;

import com.android.adservices.common.AdServicesExtendedMockitoTestCase;
import com.android.adservices.common.logging.AdServicesLoggingUsageRule;
import com.android.adservices.common.logging.annotations.ExpectErrorLogUtilCall;
import com.android.adservices.common.logging.annotations.SetErrorLogUtilDefaultParams;
import com.android.adservices.errorlogging.ErrorLogUtil;
import com.android.adservices.service.FakeFlagsFactory;
import com.android.adservices.service.Flags;
import com.android.adservices.service.FlagsFactory;
import com.android.adservices.service.common.compat.ServiceCompatUtils;
import com.android.adservices.shared.testing.HandlerIdleSyncCallback;
import com.android.adservices.shared.testing.JobServiceLoggingCallback;
import com.android.adservices.shared.testing.annotations.RequiresSdkLevelAtLeastS;
import com.android.adservices.shared.testing.concurrency.JobServiceCallback;
import com.android.adservices.spe.AdServicesJobScheduler;
import com.android.adservices.spe.AdServicesJobServiceLogger;
import com.android.modules.utils.testing.ExtendedMockitoRule.MockStatic;
import com.android.modules.utils.testing.ExtendedMockitoRule.SpyStatic;

import org.junit.After;
import org.junit.Before;
import org.junit.Rule;
import org.junit.Test;
import org.mockito.ArgumentCaptor;
import org.mockito.Mock;
import org.mockito.Spy;

/** Unit tests for {@link EpochJobService} */
@SuppressWarnings("ConstantConditions")
@SpyStatic(AdServicesJobScheduler.class)
@SpyStatic(AdServicesJobServiceLogger.class)
@SpyStatic(EpochJobService.class)
@SpyStatic(ErrorLogUtil.class)
@SpyStatic(FlagsFactory.class)
@MockStatic(ServiceCompatUtils.class)
@SpyStatic(TopicsWorker.class)
@RequiresSdkLevelAtLeastS
@SetErrorLogUtilDefaultParams(ppapiName = AD_SERVICES_ERROR_REPORTED__PPAPI_NAME__TOPICS)
public class EpochJobServiceTest extends AdServicesExtendedMockitoTestCase {
    private static final int TOPICS_EPOCH_JOB_ID = TOPICS_EPOCH_JOB.getJobId();
    // Set a minimum delay of 1 hour so scheduled jobs don't run immediately
    private static final long MINIMUM_SCHEDULING_DELAY_MS = 60L * 60L * 1000L;
    private static final Flags TEST_FLAGS = FakeFlagsFactory.getFlagsForTest();

    private final JobScheduler mJobScheduler = mContext.getSystemService(JobScheduler.class);

    @Spy private EpochJobService mSpyEpochJobService;
    @Mock private TopicsWorker mMockTopicsWorker;
    @Mock private JobParameters mMockJobParameters;
    @Mock private Flags mMockFlags;
    @Mock private JobScheduler mMockJobScheduler;
    @Mock private AdServicesJobScheduler mMockAdServicesJobScheduler;

    @Rule(order = 11)
    public final AdServicesLoggingUsageRule errorLogUtilUsageRule =
            AdServicesLoggingUsageRule.errorLogUtilUsageRule();

    private AdServicesJobServiceLogger mSpyLogger;

    @Before
    public void setup() {
        mocker.mockGetFlags(mMockFlags);

        // Mock JobScheduler invocation in EpochJobService
        assertThat(mJobScheduler).isNotNull();
        assertWithMessage("Pending EpochJobService")
                .that(mJobScheduler.getPendingJob(TOPICS_EPOCH_JOB_ID))
                .isNull();

        doReturn(mJobScheduler).when(mSpyEpochJobService).getSystemService(JobScheduler.class);

        mSpyLogger = mockAdServicesJobServiceLogger(mContext, mMockFlags);

        // By default, do not use SPE.
        when(mMockFlags.getSpeOnEpochJobEnabled()).thenReturn(false);
    }

    @After
    public void teardown() throws Exception {
        HandlerIdleSyncCallback callback = new HandlerIdleSyncCallback();

        mJobScheduler.cancelAll();

        callback.assertIdle();
    }

    @Test
    public void testOnStartJob_killSwitchOff_withoutLogging() throws InterruptedException {
        mockBackgroundJobsLoggingKillSwitch(mMockFlags, /* overrideValue= */ true);

        testOnStartJob_killSwitchOff();

        verifyLoggingNotHappened(mSpyLogger);
    }

    @Test
    public void testOnStartJob_killSwitchOff_withLogging() throws InterruptedException {
        mockBackgroundJobsLoggingKillSwitch(mMockFlags, /* overrideValue= */ false);
        JobServiceLoggingCallback onStartJobCallback = syncPersistJobExecutionData(mSpyLogger);
        JobServiceLoggingCallback onJobDoneCallback = syncLogExecutionStats(mSpyLogger);

        testOnStartJob_killSwitchOff();

        verifyJobFinishedLogged(mSpyLogger, onStartJobCallback, onJobDoneCallback);
    }

    @Test
    @ExpectErrorLogUtilCall(errorCode = AD_SERVICES_ERROR_REPORTED__ERROR_CODE__TOPICS_API_DISABLED)
    public void testOnStartJob_killSwitchOn_withoutLogging() {
        mockBackgroundJobsLoggingKillSwitch(mMockFlags, /* overrideValue= */ true);

        testOnStartJob_killSwitchOn();

        verifyLoggingNotHappened(mSpyLogger);
    }

    @Test
    @ExpectErrorLogUtilCall(errorCode = AD_SERVICES_ERROR_REPORTED__ERROR_CODE__TOPICS_API_DISABLED)
    public void testOnStartJob_killSwitchOn_withLogging() throws InterruptedException {
        mockBackgroundJobsLoggingKillSwitch(mMockFlags, /* overrideValue= */ false);
        JobServiceLoggingCallback callback = syncLogExecutionStats(mSpyLogger);

        testOnStartJob_killSwitchOn();

        verifyBackgroundJobsSkipLogged(mSpyLogger, callback);
    }

    @Test
    public void testOnStartJob_shouldDisableJobTrue_withoutLogging() {
        mockBackgroundJobsLoggingKillSwitch(mMockFlags, /* overrideValue= */ true);

        testOnStartJob_shouldDisableJobTrue();

        verifyLoggingNotHappened(mSpyLogger);
    }

    @Test
    public void testOnStartJob_shouldDisableJobTrue_withLoggingEnabled() {
        mockBackgroundJobsLoggingKillSwitch(mMockFlags, /* overrideValue= */ false);

        testOnStartJob_shouldDisableJobTrue();

        // Verify logging has not happened even though logging is enabled because this field is not
        // logged
        verifyLoggingNotHappened(mSpyLogger);
    }

    @Test
    public void testOnStartJob_speEnabled() throws Exception {
        when(mMockFlags.getSpeOnEpochJobEnabled()).thenReturn(true);
        doReturn(false).when(mMockFlags).getGlobalKillSwitch();
        mocker.mockSpeJobScheduler(mMockAdServicesJobScheduler);
        // Mock not to run actual execution logic.
        doReturn(mMockTopicsWorker).when(TopicsWorker::getInstance);
        // Verify logging for current execution.
        mockBackgroundJobsLoggingKillSwitch(mMockFlags, /* overrideValue= */ false);
        JobServiceLoggingCallback onStartJobCallback = syncPersistJobExecutionData(mSpyLogger);
        JobServiceLoggingCallback onJobDoneCallback = syncLogExecutionStats(mSpyLogger);

        mSpyEpochJobService.onStartJob(mMockJobParameters);

        verify(mMockAdServicesJobScheduler).schedule(any());
        verifyJobFinishedLogged(mSpyLogger, onStartJobCallback, onJobDoneCallback);
    }

    @Test
    public void testOnStopJob_withoutLogging() {
        mockBackgroundJobsLoggingKillSwitch(mMockFlags, /* overrideValue= */ true);

        testOnStopJob();

        verifyLoggingNotHappened(mSpyLogger);
    }

    @Test
    public void testOnStopJob_withLogging() throws InterruptedException {
        mockBackgroundJobsLoggingKillSwitch(mMockFlags, /* overrideValue= */ false);
        JobServiceLoggingCallback callback = syncLogExecutionStats(mSpyLogger);

        testOnStopJob();

        verifyOnStopJobLogged(mSpyLogger, callback);
    }

    @Test
    public void testScheduleIfNeeded_Success() {
        doReturn(false).when(mMockFlags).getGlobalKillSwitch();

        // The first invocation of scheduleIfNeeded() schedules the job.
        assertThat(EpochJobService.scheduleIfNeeded(/* forceSchedule= */ false))
                .isEqualTo(SCHEDULING_RESULT_CODE_SUCCESSFUL);
    }

    @Test
    public void testScheduleIfNeeded_ScheduledWithSameParameters() {
        doReturn(false).when(mMockFlags).getGlobalKillSwitch();
        doReturn(TEST_FLAGS.getTopicsEpochJobPeriodMs())
                .when(mMockFlags)
                .getTopicsEpochJobPeriodMs();
        doReturn(TEST_FLAGS.getTopicsEpochJobFlexMs()).when(mMockFlags).getTopicsEpochJobFlexMs();

        // The first invocation of scheduleIfNeeded() schedules the job.
        assertThat(EpochJobService.scheduleIfNeeded(/* forceSchedule */ false))
                .isEqualTo(SCHEDULING_RESULT_CODE_SUCCESSFUL);
        assertThat(mJobScheduler.getPendingJob(TOPICS_EPOCH_JOB_ID)).isNotNull();

        // The second invocation of scheduleIfNeeded() with same parameters skips the scheduling.
        assertThat(EpochJobService.scheduleIfNeeded(/* forceSchedule */ false))
                .isEqualTo(SCHEDULING_RESULT_CODE_SKIPPED);
    }

    @Test
    public void testScheduleIfNeeded_ScheduledWithDifferentParameters() {
        // Mock Flags in order to change values within this test
        doReturn(TEST_FLAGS.getTopicsEpochJobPeriodMs())
                .when(mMockFlags)
                .getTopicsEpochJobPeriodMs();
        doReturn(TEST_FLAGS.getTopicsEpochJobFlexMs()).when(mMockFlags).getTopicsEpochJobFlexMs();

        // The first invocation of scheduleIfNeeded() schedules the job.
        assertThat(EpochJobService.scheduleIfNeeded(/* forceSchedule */ false))
                .isEqualTo(SCHEDULING_RESULT_CODE_SUCCESSFUL);
        assertThat(mJobScheduler.getPendingJob(TOPICS_EPOCH_JOB_ID)).isNotNull();

        // Change the value of a parameter so that the second invocation of scheduleIfNeeded()
        // schedules the job.
        doReturn(TEST_FLAGS.getTopicsEpochJobFlexMs() + 1)
                .when(mMockFlags)
                .getTopicsEpochJobFlexMs();
        assertThat(EpochJobService.scheduleIfNeeded(/* forceSchedule */ false))
                .isEqualTo(SCHEDULING_RESULT_CODE_SUCCESSFUL);
    }

    @Test
    public void testScheduleIfNeeded_forceRun() {
        doReturn(false).when(mMockFlags).getGlobalKillSwitch();
        doReturn(TEST_FLAGS.getTopicsEpochJobPeriodMs())
                .when(mMockFlags)
                .getTopicsEpochJobPeriodMs();
        doReturn(TEST_FLAGS.getTopicsEpochJobFlexMs()).when(mMockFlags).getTopicsEpochJobFlexMs();

        // The first invocation of scheduleIfNeeded() schedules the job.
        assertThat(EpochJobService.scheduleIfNeeded(/* forceSchedule */ false))
                .isEqualTo(SCHEDULING_RESULT_CODE_SUCCESSFUL);
        assertThat(mJobScheduler.getPendingJob(TOPICS_EPOCH_JOB_ID)).isNotNull();

        // The second invocation of scheduleIfNeeded() with same parameters skips the scheduling.
        assertThat(EpochJobService.scheduleIfNeeded(/* forceSchedule */ false))
                .isEqualTo(SCHEDULING_RESULT_CODE_SKIPPED);

        // The third invocation of scheduleIfNeeded() is forced and re-schedules the job.
        assertThat(EpochJobService.scheduleIfNeeded(/* forceSchedule */ true))
                .isEqualTo(SCHEDULING_RESULT_CODE_SUCCESSFUL);
    }

    @Test
    @ExpectErrorLogUtilCall(errorCode = AD_SERVICES_ERROR_REPORTED__ERROR_CODE__TOPICS_API_DISABLED)
    public void testScheduleIfNeeded_scheduledWithKillSwitchOn() {
        // Kill switch is on.
        doReturn(true).when(mMockFlags).getTopicsKillSwitch();

        // The first invocation of scheduleIfNeeded() schedules the job.
        assertThat(EpochJobService.scheduleIfNeeded(/* forceSchedule */ false))
                .isEqualTo(SCHEDULING_RESULT_CODE_SKIPPED);
        assertThat(mJobScheduler.getPendingJob(TOPICS_EPOCH_JOB_ID)).isNull();
    }

    @Test
    public void testSchedule_jobInfoIsPersisted() {
        ArgumentCaptor<JobInfo> argumentCaptor = ArgumentCaptor.forClass(JobInfo.class);
        long epoch_period = 10_000L;
        long flex_period = 1_000L;

        EpochJobService.schedule(mContext, mMockJobScheduler, epoch_period, flex_period);

        verify(mMockJobScheduler).schedule(argumentCaptor.capture());
        assertThat(argumentCaptor.getValue()).isNotNull();
        assertThat(argumentCaptor.getValue().isPersisted()).isTrue();
    }

    private void testOnStartJob_killSwitchOff() throws InterruptedException {
        // Kill switch is off.
        doReturn(false).when(mMockFlags).getTopicsKillSwitch();

        doNothing().when(mSpyEpochJobService).jobFinished(mMockJobParameters, false);

        // Mock static method TopicsWorker.getInstance, let it return the local topicsWorker
        // in order to get a test instance.
        doReturn(mMockTopicsWorker).when(TopicsWorker::getInstance);

        scheduleJobWithMinimumLatency();

        JobServiceCallback callback =
                new JobServiceCallback().expectJobFinished(mSpyEpochJobService);

        // Now verify that when the Job starts, it will schedule itself.
        assertThat(mSpyEpochJobService.onStartJob(mMockJobParameters)).isTrue();

        callback.assertJobFinished();
    }

    private void testOnStartJob_killSwitchOn() {
        // Kill switch is on.
        doReturn(true).when(mMockFlags).getTopicsKillSwitch();

        doNothing().when(mSpyEpochJobService).jobFinished(mMockJobParameters, false);

        scheduleJobWithMinimumLatency();

        verifyJobStartAndCancelled();
    }

    private void testOnStartJob_shouldDisableJobTrue() {
        doReturn(true).when(() -> ServiceCompatUtils.shouldDisableExtServicesJobOnTPlus(any()));

        doNothing().when(mSpyEpochJobService).jobFinished(mMockJobParameters, false);

        scheduleJobWithMinimumLatency();

        verifyJobStartAndCancelled();
    }

    // Verify that when the Job starts, it will unschedule itself.
    private void verifyJobStartAndCancelled() {
        expect.withMessage("Calling onStartJob()")
                .that(mSpyEpochJobService.onStartJob(mMockJobParameters))
                .isFalse();
        expect.withMessage("Pending EpochJobService")
                .that(mJobScheduler.getPendingJob(TOPICS_EPOCH_JOB_ID))
                .isNull();

        verify(mSpyEpochJobService).jobFinished(mMockJobParameters, false);
        verifyNoMoreInteractions(staticMockMarker(TopicsWorker.class));
    }

    // Schedule the JobService with an unreachable start-up latency to prevent any execution.
    // Note this test case is to test the behavior of onStartJob(), so how the job is scheduled
    // doesn't matter.
    private void scheduleJobWithMinimumLatency() {
        JobInfo existingJobInfo =
                new JobInfo.Builder(
                                TOPICS_EPOCH_JOB_ID,
                                new ComponentName(mContext, EpochJobService.class))
                        .setMinimumLatency(MINIMUM_SCHEDULING_DELAY_MS)
                        .build();
        mJobScheduler.schedule(existingJobInfo);
        assertThat(mJobScheduler.getPendingJob(TOPICS_EPOCH_JOB_ID)).isNotNull();
    }

    private void testOnStopJob() {
        // Verify nothing throws
        mSpyEpochJobService.onStopJob(mMockJobParameters);
    }
}

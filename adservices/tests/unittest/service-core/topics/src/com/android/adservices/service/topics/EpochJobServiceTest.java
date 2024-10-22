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

import static com.android.adservices.service.stats.AdServicesStatsLog.AD_SERVICES_ERROR_REPORTED__ERROR_CODE__TOPICS_API_DISABLED;
import static com.android.adservices.service.stats.AdServicesStatsLog.AD_SERVICES_ERROR_REPORTED__PPAPI_NAME__TOPICS;
import static com.android.adservices.service.stats.AdsRelevanceStatusUtils.TOPICS_EPOCH_JOB_BATTERY_CONSTRAINT_REQUIRES_BATTERY_NOT_LOW;
import static com.android.adservices.service.stats.AdsRelevanceStatusUtils.TOPICS_EPOCH_JOB_BATTERY_CONSTRAINT_REQUIRES_CHARGING;
import static com.android.adservices.service.stats.AdsRelevanceStatusUtils.TOPICS_EPOCH_JOB_BATTERY_CONSTRAINT_UNKNOWN_SETTING;
import static com.android.adservices.service.stats.AdsRelevanceStatusUtils.TOPICS_RESCHEDULE_EPOCH_JOB_STATUS_RESCHEDULE_SUCCESS;
import static com.android.adservices.service.stats.AdsRelevanceStatusUtils.TOPICS_RESCHEDULE_EPOCH_JOB_STATUS_SKIP_RESCHEDULE_EMPTY_JOB_SCHEDULER;
import static com.android.adservices.service.stats.AdsRelevanceStatusUtils.TOPICS_RESCHEDULE_EPOCH_JOB_STATUS_SKIP_RESCHEDULE_EMPTY_PENDING_JOB;
import static com.android.adservices.service.stats.AdsRelevanceStatusUtils.TOPICS_RESCHEDULE_EPOCH_JOB_STATUS_UNSET;
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
import static org.mockito.Mockito.doAnswer;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verifyNoMoreInteractions;
import static org.mockito.Mockito.when;

import android.app.job.JobInfo;
import android.app.job.JobParameters;
import android.app.job.JobScheduler;
import android.content.ComponentName;

import com.android.adservices.common.AdServicesJobServiceTestCase;
import com.android.adservices.common.logging.annotations.ExpectErrorLogUtilCall;
import com.android.adservices.common.logging.annotations.SetErrorLogUtilDefaultParams;
import com.android.adservices.service.FakeFlagsFactory;
import com.android.adservices.service.Flags;
import com.android.adservices.service.FlagsFactory;
import com.android.adservices.service.common.compat.ServiceCompatUtils;
import com.android.adservices.service.stats.AdServicesLoggerImpl;
import com.android.adservices.service.stats.TopicsScheduleEpochJobSettingReportedStats;
import com.android.adservices.service.stats.TopicsScheduleEpochJobSettingReportedStatsLogger;
import com.android.adservices.shared.testing.AnswerSyncCallback;
import com.android.adservices.shared.testing.HandlerIdleSyncCallback;
import com.android.adservices.shared.testing.JobServiceLoggingCallback;
import com.android.adservices.shared.testing.concurrency.JobServiceCallback;
import com.android.adservices.spe.AdServicesJobScheduler;
import com.android.adservices.spe.AdServicesJobServiceLogger;
import com.android.modules.utils.testing.ExtendedMockitoRule.MockStatic;
import com.android.modules.utils.testing.ExtendedMockitoRule.SpyStatic;

import org.junit.After;
import org.junit.Before;
import org.junit.Test;
import org.mockito.ArgumentCaptor;
import org.mockito.Mock;
import org.mockito.Spy;

import java.util.List;

/** Unit tests for {@link EpochJobService} */
@SuppressWarnings("ConstantConditions")
@SpyStatic(AdServicesJobScheduler.class)
@SpyStatic(AdServicesJobServiceLogger.class)
@SpyStatic(EpochJobService.class)
@SpyStatic(FlagsFactory.class)
@MockStatic(ServiceCompatUtils.class)
@SpyStatic(TopicsWorker.class)
@SpyStatic(TopicsScheduleEpochJobSettingReportedStatsLogger.class)
@SetErrorLogUtilDefaultParams(ppapiName = AD_SERVICES_ERROR_REPORTED__PPAPI_NAME__TOPICS)
public final class EpochJobServiceTest extends AdServicesJobServiceTestCase {
    private static final int TOPICS_EPOCH_JOB_ID = TOPICS_EPOCH_JOB.getJobId();
    // Set a minimum delay of 1 hour so scheduled jobs don't run immediately
    private static final long MINIMUM_SCHEDULING_DELAY_MS = 60L * 60L * 1000L;
    private static final Flags TEST_FLAGS = FakeFlagsFactory.getFlagsForTest();

    private final JobScheduler mJobScheduler = mContext.getSystemService(JobScheduler.class);

    @Spy private EpochJobService mSpyEpochJobService;
    @Spy private AdServicesLoggerImpl mSpyAdServicesLogger;
    @Mock private TopicsWorker mMockTopicsWorker;
    @Mock private JobParameters mMockJobParameters;
    @Mock private JobScheduler mMockJobScheduler;
    @Mock private AdServicesJobScheduler mMockAdServicesJobScheduler;

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

        mSpyLogger = mocker.mockNoOpAdServicesJobServiceLogger(mContext, mMockFlags);

        // By default, do not use SPE.
        when(mMockFlags.getSpeOnEpochJobEnabled()).thenReturn(false);

        // By default, do not set requiresBatteryNotLow to true in EpochJobService.
        when(mMockFlags.getTopicsEpochJobBatteryNotLowInsteadOfCharging()).thenReturn(false);

        // By default, enables clean DB when Topics kill switch on
        // or epoch job's configuration is changed.
        when(mMockFlags.getTopicsCleanDBWhenEpochJobSettingsChanged()).thenReturn(true);
    }

    @After
    public void teardown() throws Exception {
        HandlerIdleSyncCallback callback = new HandlerIdleSyncCallback();

        mJobScheduler.cancelAll();

        callback.assertIdle();
    }

    @Test
    public void testOnStartJob_speEnabled() throws Exception {
        when(mMockFlags.getSpeOnEpochJobEnabled()).thenReturn(true);
        doReturn(false).when(mMockFlags).getGlobalKillSwitch();
        mocker.mockSpeJobScheduler(mMockAdServicesJobScheduler);
        // Mock not to run actual execution logic.
        doReturn(mMockTopicsWorker).when(TopicsWorker::getInstance);

        JobServiceLoggingCallback onStartJobCallback = syncPersistJobExecutionData(mSpyLogger);
        JobServiceLoggingCallback onJobDoneCallback = syncLogExecutionStats(mSpyLogger);

        mSpyEpochJobService.onStartJob(mMockJobParameters);

        verify(mMockAdServicesJobScheduler).schedule(any());
        verifyJobFinishedLogged(mSpyLogger, onStartJobCallback, onJobDoneCallback);
    }

    @Test
    public void testOnStartJob_rescheduleEpochJobEnabled() throws Exception {
        when(mMockFlags.getTopicsKillSwitch()).thenReturn(false);
        when(mMockFlags.getTopicsEpochJobPeriodMs())
                .thenReturn(TEST_FLAGS.getTopicsEpochJobPeriodMs());
        when(mMockFlags.getTopicsEpochJobFlexMs()).thenReturn(TEST_FLAGS.getTopicsEpochJobFlexMs());

        // The first invocation of scheduleIfNeeded() schedules the job
        // with requires charging setting.
        assertThat(EpochJobService.scheduleIfNeeded(/* forceSchedule */ false))
                .isEqualTo(SCHEDULING_RESULT_CODE_SUCCESSFUL);
        JobInfo pendingJobInfo1 = mJobScheduler.getPendingJob(TOPICS_EPOCH_JOB_ID);
        assertThat(pendingJobInfo1).isNotNull();
        assertThat(pendingJobInfo1.isRequireCharging()).isTrue();

        // Then disable requires charging setting and verify the job is rescheduled in onStartJob.
        when(mMockFlags.getTopicsEpochJobBatteryNotLowInsteadOfCharging()).thenReturn(true);
        when(mMockFlags.getTopicsJobSchedulerRescheduleEnabled()).thenReturn(true);

        testOnStartJob_killSwitchOff();
        assertThat(EpochJobService.scheduleIfNeeded(/* forceSchedule */ false))
                .isEqualTo(SCHEDULING_RESULT_CODE_SKIPPED);
        JobInfo pendingJobInfo2 = mJobScheduler.getPendingJob(TOPICS_EPOCH_JOB_ID);
        expect.that(pendingJobInfo2).isNotNull();
        expect.that(pendingJobInfo2.isRequireCharging()).isFalse();
        expect.that(pendingJobInfo2.isRequireBatteryNotLow()).isTrue();
    }

    @Test
    public void testRescheduleEpochJobEnabled_withLogging() throws Exception {
        // Initializes the argumentCaptor to collect topics epoch job setting metrics.
        ArgumentCaptor<TopicsScheduleEpochJobSettingReportedStats> argumentCaptor =
                ArgumentCaptor.forClass(TopicsScheduleEpochJobSettingReportedStats.class);
        // Mock not to run actual execution logic.
        doReturn(mMockTopicsWorker).when(TopicsWorker::getInstance);
        when(mMockFlags.getTopicsEpochJobBatteryConstraintLoggingEnabled()).thenReturn(true);

        when(mMockFlags.getTopicsKillSwitch()).thenReturn(false);
        when(mMockFlags.getTopicsEpochJobPeriodMs())
                .thenReturn(TEST_FLAGS.getTopicsEpochJobPeriodMs());
        when(mMockFlags.getTopicsEpochJobFlexMs()).thenReturn(TEST_FLAGS.getTopicsEpochJobFlexMs());

        AnswerSyncCallback<Void> callback = AnswerSyncCallback.forMultipleVoidAnswers(2);
        doAnswer(callback).when(mSpyAdServicesLogger)
                .logTopicsScheduleEpochJobSettingReportedStats(any());

        // The first invocation of scheduleIfNeededCalledFromRescheduleEpochJob() schedules the job
        // with requires charging setting.
        assertThat(
                EpochJobService.scheduleIfNeededCalledFromRescheduleEpochJob(
                        /* forceSchedule */ false,
                        new TopicsScheduleEpochJobSettingReportedStatsLogger(
                                mSpyAdServicesLogger, mMockFlags)))
                .isEqualTo(SCHEDULING_RESULT_CODE_SUCCESSFUL);
        JobInfo pendingJobInfo1 = mJobScheduler.getPendingJob(TOPICS_EPOCH_JOB_ID);
        assertThat(pendingJobInfo1).isNotNull();
        assertThat(pendingJobInfo1.isRequireCharging()).isTrue();

        // Then disable requires charging setting and verify the job is rescheduled in onStartJob.
        when(mMockFlags.getTopicsEpochJobBatteryNotLowInsteadOfCharging()).thenReturn(true);
        when(mMockFlags.getTopicsJobSchedulerRescheduleEnabled()).thenReturn(true);

        // The second invocation of scheduleIfNeededCalledFromRescheduleEpochJob() schedules the job
        // with requires battery not low setting.
        assertThat(
                EpochJobService.scheduleIfNeededCalledFromRescheduleEpochJob(
                        /* forceSchedule */ false,
                        new TopicsScheduleEpochJobSettingReportedStatsLogger(
                                mSpyAdServicesLogger, mMockFlags)))
                .isEqualTo(SCHEDULING_RESULT_CODE_SUCCESSFUL);
        JobInfo pendingJobInfo2 = mJobScheduler.getPendingJob(TOPICS_EPOCH_JOB_ID);
        assertThat(pendingJobInfo2).isNotNull();
        assertThat(pendingJobInfo2.isRequireCharging()).isFalse();
        assertThat(pendingJobInfo2.isRequireBatteryNotLow()).isTrue();
        callback.assertCalled();

        verify(mMockTopicsWorker).clearAllTopicsData(any());

        // Verifies the topics epoch job setting metric is logged 2 times.
        verify(mSpyAdServicesLogger, times(2))
                .logTopicsScheduleEpochJobSettingReportedStats(argumentCaptor.capture());
        List<TopicsScheduleEpochJobSettingReportedStats> stats = argumentCaptor.getAllValues();
        assertThat(stats).hasSize(2);

        List<TopicsScheduleEpochJobSettingReportedStats> actualStats =
                argumentCaptor.getAllValues();

        TopicsScheduleEpochJobSettingReportedStats expectedStat1 =
                TopicsScheduleEpochJobSettingReportedStats.builder()
                        .setCurrentEpochJobSetting(
                                TOPICS_EPOCH_JOB_BATTERY_CONSTRAINT_UNKNOWN_SETTING)
                        .setRescheduleEpochJobStatus(TOPICS_RESCHEDULE_EPOCH_JOB_STATUS_UNSET)
                        .setPreviousEpochJobSetting(
                                TOPICS_EPOCH_JOB_BATTERY_CONSTRAINT_UNKNOWN_SETTING)
                        .setScheduleIfNeededEpochJobStatus(
                                TOPICS_EPOCH_JOB_BATTERY_CONSTRAINT_REQUIRES_CHARGING)
                        .build();
        TopicsScheduleEpochJobSettingReportedStats expectedStat2 =
                TopicsScheduleEpochJobSettingReportedStats.builder()
                        .setCurrentEpochJobSetting(
                                TOPICS_EPOCH_JOB_BATTERY_CONSTRAINT_UNKNOWN_SETTING)
                        .setRescheduleEpochJobStatus(TOPICS_RESCHEDULE_EPOCH_JOB_STATUS_UNSET)
                        .setPreviousEpochJobSetting(
                                TOPICS_EPOCH_JOB_BATTERY_CONSTRAINT_UNKNOWN_SETTING)
                        .setScheduleIfNeededEpochJobStatus(
                                TOPICS_EPOCH_JOB_BATTERY_CONSTRAINT_REQUIRES_BATTERY_NOT_LOW)
                        .build();

        assertWithMessage("Calls to log scheduled battery constraint")
                .that(actualStats)
                .containsExactly(expectedStat1, expectedStat2);
    }

    @Test
    public void testOnStopJob_withLogging() throws Exception {
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
    public void testScheduleIfNeeded_RequiresBatteryNotLow_Success() {
        when(mMockFlags.getGlobalKillSwitch()).thenReturn(false);
        when(mMockFlags.getTopicsJobSchedulerRescheduleEnabled()).thenReturn(true);
        when(mMockFlags.getTopicsEpochJobBatteryNotLowInsteadOfCharging()).thenReturn(true);

        // The first invocation of scheduleIfNeeded() schedules the job.
        assertThat(EpochJobService.scheduleIfNeeded(/* forceSchedule= */ false))
                .isEqualTo(SCHEDULING_RESULT_CODE_SUCCESSFUL);
    }

    @Test
    public void testScheduleIfNeededCalledFromRescheduleEpochJob_RequiresBatteryNotLow_Success()
            throws InterruptedException {
        // Initializes the argumentCaptor to collect topics epoch job setting metrics.
        ArgumentCaptor<TopicsScheduleEpochJobSettingReportedStats> argumentCaptor =
                ArgumentCaptor.forClass(TopicsScheduleEpochJobSettingReportedStats.class);
        when(mMockFlags.getTopicsEpochJobBatteryConstraintLoggingEnabled()).thenReturn(true);

        when(mMockFlags.getGlobalKillSwitch()).thenReturn(false);
        when(mMockFlags.getTopicsJobSchedulerRescheduleEnabled()).thenReturn(true);
        when(mMockFlags.getTopicsEpochJobBatteryNotLowInsteadOfCharging()).thenReturn(true);

        AnswerSyncCallback<Void> callback = AnswerSyncCallback.forSingleVoidAnswer();
        doAnswer(callback).when(mSpyAdServicesLogger)
                .logTopicsScheduleEpochJobSettingReportedStats(any());

        // The first invocation of scheduleIfNeededCalledFromRescheduleEpochJob() schedules the job.
        assertThat(
                EpochJobService.scheduleIfNeededCalledFromRescheduleEpochJob(
                        /* forceSchedule= */ false,
                        new TopicsScheduleEpochJobSettingReportedStatsLogger(
                                mSpyAdServicesLogger, mMockFlags)))
                .isEqualTo(SCHEDULING_RESULT_CODE_SUCCESSFUL);
        callback.assertCalled();

        // Verifies the topics epoch job setting metric is logged.
        verify(mSpyAdServicesLogger)
                .logTopicsScheduleEpochJobSettingReportedStats(argumentCaptor.capture());
        TopicsScheduleEpochJobSettingReportedStats stats = argumentCaptor.getValue();
        expect.that(stats.getRescheduleEpochJobStatus())
                .isEqualTo(TOPICS_RESCHEDULE_EPOCH_JOB_STATUS_UNSET);
        expect.that(stats.getPreviousEpochJobSetting())
                .isEqualTo(TOPICS_EPOCH_JOB_BATTERY_CONSTRAINT_UNKNOWN_SETTING);
        expect.that(stats.getCurrentEpochJobSetting())
                .isEqualTo(TOPICS_EPOCH_JOB_BATTERY_CONSTRAINT_UNKNOWN_SETTING);
        expect.that(stats.getScheduleIfNeededEpochJobStatus())
                .isEqualTo(TOPICS_EPOCH_JOB_BATTERY_CONSTRAINT_REQUIRES_BATTERY_NOT_LOW);
    }

    @Test
    public void testScheduleIfNeeded_ScheduledWithSameParameters() {
        doReturn(false).when(mMockFlags).getGlobalKillSwitch();
        doReturn(TEST_FLAGS.getTopicsEpochJobPeriodMs())
                .when(mMockFlags)
                .getTopicsEpochJobPeriodMs();
        doReturn(TEST_FLAGS.getTopicsEpochJobFlexMs()).when(mMockFlags).getTopicsEpochJobFlexMs();
        doReturn(false).when(mMockFlags).getTopicsEpochJobBatteryNotLowInsteadOfCharging();

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

        // Mock not to run actual execution logic.
        doReturn(mMockTopicsWorker).when(TopicsWorker::getInstance);

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

        verify(mMockTopicsWorker).clearAllTopicsData(any());
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
    public void testScheduleIfNeeded_RequiresBatteryNotLow_ForceRun() {
        when(mMockFlags.getGlobalKillSwitch()).thenReturn(false);
        when(mMockFlags.getTopicsEpochJobPeriodMs())
                .thenReturn(TEST_FLAGS.getTopicsEpochJobPeriodMs());
        when(mMockFlags.getTopicsEpochJobFlexMs()).thenReturn(TEST_FLAGS.getTopicsEpochJobFlexMs());
        when(mMockFlags.getTopicsJobSchedulerRescheduleEnabled()).thenReturn(true);
        when(mMockFlags.getTopicsEpochJobBatteryNotLowInsteadOfCharging()).thenReturn(true);

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
        boolean topics_epoch_job_battery_not_low_instead_of_charging = false;

        EpochJobService.schedule(
                mMockJobScheduler,
                new JobInfo.Builder(
                        TOPICS_EPOCH_JOB_ID,
                        new ComponentName(mContext, EpochJobService.class))
                        .setPersisted(true)
                        .setPeriodic(epoch_period, flex_period)
                        .setRequiresBatteryNotLow(
                                topics_epoch_job_battery_not_low_instead_of_charging)
                        .build());

        verify(mMockJobScheduler).schedule(argumentCaptor.capture());
        assertThat(argumentCaptor.getValue()).isNotNull();
        assertThat(argumentCaptor.getValue().isPersisted()).isTrue();
    }

    @Test
    public void testSchedule_disableTopicsJobSchedulerRequiresCharging() {
        when(mMockFlags.getTopicsJobSchedulerRescheduleEnabled()).thenReturn(true);
        ArgumentCaptor<JobInfo> argumentCaptor = ArgumentCaptor.forClass(JobInfo.class);

        long epoch_period = 10_000L;
        long flex_period = 1_000L;
        boolean topics_epoch_job_battery_not_low_instead_of_charging = true;

        EpochJobService.schedule(
                mMockJobScheduler,
                new JobInfo.Builder(
                        TOPICS_EPOCH_JOB_ID,
                        new ComponentName(mContext, EpochJobService.class))
                        .setPersisted(true)
                        .setPeriodic(epoch_period, flex_period)
                        .setRequiresBatteryNotLow(
                                topics_epoch_job_battery_not_low_instead_of_charging)
                        .build());

        // Verify the JobScheduler has scheduled a new background job with new JobInfo.
        verify(mMockJobScheduler).schedule(argumentCaptor.capture());
        assertThat(argumentCaptor.getValue()).isNotNull();
        expect.that(argumentCaptor.getValue().isRequireCharging()).isFalse();
        expect.that(argumentCaptor.getValue().isRequireBatteryNotLow()).isTrue();
    }

    @Test
    public void testOnStartJob_killSwitchOff() throws Exception {
        JobServiceLoggingCallback onStartJobCallback = syncPersistJobExecutionData(mSpyLogger);
        JobServiceLoggingCallback onJobDoneCallback = syncLogExecutionStats(mSpyLogger);

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

        verifyJobFinishedLogged(mSpyLogger, onStartJobCallback, onJobDoneCallback);
    }

    @Test
    @ExpectErrorLogUtilCall(errorCode = AD_SERVICES_ERROR_REPORTED__ERROR_CODE__TOPICS_API_DISABLED)
    public void testOnStartJob_killSwitchOn() throws Exception {
        JobServiceLoggingCallback callback = syncLogExecutionStats(mSpyLogger);

        // Mock not to run actual execution logic.
        doReturn(mMockTopicsWorker).when(TopicsWorker::getInstance);

        // Kill switch is on.
        doReturn(true).when(mMockFlags).getTopicsKillSwitch();

        doNothing().when(mSpyEpochJobService).jobFinished(mMockJobParameters, false);

        scheduleJobWithMinimumLatency();

        verifyJobStartAndCancelled();

        verifyBackgroundJobsSkipLogged(mSpyLogger, callback);
    }

    @Test
    public void testOnStartJob_shouldDisableJobTrue() {
        doReturn(true).when(() -> ServiceCompatUtils.shouldDisableExtServicesJobOnTPlus(any()));

        // Mock not to run actual execution logic.
        doReturn(mMockTopicsWorker).when(TopicsWorker::getInstance);

        doNothing().when(mSpyEpochJobService).jobFinished(mMockJobParameters, false);

        scheduleJobWithMinimumLatency();

        verifyJobStartAndCancelled();

        // Verify logging has not happened even though logging is enabled because this field is not
        // logged
        verifyLoggingNotHappened(mSpyLogger);
    }

    @Test
    public void testRescheduleEpochJob_skipReschedule_emptyJobScheduler()
            throws InterruptedException {
        // Initializes the argumentCaptor to collect topics epoch job setting metrics.
        ArgumentCaptor<TopicsScheduleEpochJobSettingReportedStats> argumentCaptor =
                ArgumentCaptor.forClass(TopicsScheduleEpochJobSettingReportedStats.class);
        when(mMockFlags.getTopicsEpochJobBatteryConstraintLoggingEnabled()).thenReturn(true);

        when(mMockFlags.getGlobalKillSwitch()).thenReturn(false);
        when(mMockFlags.getTopicsJobSchedulerRescheduleEnabled()).thenReturn(true);
        when(mMockFlags.getTopicsEpochJobBatteryNotLowInsteadOfCharging()).thenReturn(true);

        AnswerSyncCallback<Void> callback = AnswerSyncCallback.forSingleVoidAnswer();
        doAnswer(callback).when(mSpyAdServicesLogger)
                .logTopicsScheduleEpochJobSettingReportedStats(any());

        appContext.set(mMockContext);
        when(mMockContext.getSystemService(JobScheduler.class)).thenReturn(null);

        doReturn(new TopicsScheduleEpochJobSettingReportedStatsLogger(
                mSpyAdServicesLogger, mMockFlags))
                .when(TopicsScheduleEpochJobSettingReportedStatsLogger::getInstance);

        EpochJobService.rescheduleEpochJob();
        callback.assertCalled();

        // Verifies the topics epoch job setting metric is logged.
        verify(mSpyAdServicesLogger)
                .logTopicsScheduleEpochJobSettingReportedStats(argumentCaptor.capture());
        TopicsScheduleEpochJobSettingReportedStats stats = argumentCaptor.getValue();
        expect.that(stats.getRescheduleEpochJobStatus())
                .isEqualTo(TOPICS_RESCHEDULE_EPOCH_JOB_STATUS_SKIP_RESCHEDULE_EMPTY_JOB_SCHEDULER);
        expect.that(stats.getPreviousEpochJobSetting())
                .isEqualTo(TOPICS_EPOCH_JOB_BATTERY_CONSTRAINT_UNKNOWN_SETTING);
        expect.that(stats.getCurrentEpochJobSetting())
                .isEqualTo(TOPICS_EPOCH_JOB_BATTERY_CONSTRAINT_UNKNOWN_SETTING);
        expect.that(stats.getScheduleIfNeededEpochJobStatus())
                .isEqualTo(TOPICS_EPOCH_JOB_BATTERY_CONSTRAINT_UNKNOWN_SETTING);
    }

    @Test
    public void testRescheduleEpochJob_skipReschedule_emptyPendingJob()
            throws InterruptedException {
        // Initializes the argumentCaptor to collect topics epoch job setting metrics.
        ArgumentCaptor<TopicsScheduleEpochJobSettingReportedStats> argumentCaptor =
                ArgumentCaptor.forClass(TopicsScheduleEpochJobSettingReportedStats.class);
        when(mMockFlags.getTopicsEpochJobBatteryConstraintLoggingEnabled()).thenReturn(true);

        when(mMockFlags.getGlobalKillSwitch()).thenReturn(false);
        when(mMockFlags.getTopicsJobSchedulerRescheduleEnabled()).thenReturn(true);
        when(mMockFlags.getTopicsEpochJobBatteryNotLowInsteadOfCharging()).thenReturn(true);

        AnswerSyncCallback<Void> callback = AnswerSyncCallback.forSingleVoidAnswer();
        doAnswer(callback).when(mSpyAdServicesLogger)
                .logTopicsScheduleEpochJobSettingReportedStats(any());

        appContext.set(mMockContext);
        when(mMockContext.getSystemService(JobScheduler.class)).thenReturn(mMockJobScheduler);
        when(mMockJobScheduler.getPendingJob(TOPICS_EPOCH_JOB_ID)).thenReturn(null);
        doReturn(new TopicsScheduleEpochJobSettingReportedStatsLogger(
                mSpyAdServicesLogger, mMockFlags))
                .when(TopicsScheduleEpochJobSettingReportedStatsLogger::getInstance);

        EpochJobService.rescheduleEpochJob();
        callback.assertCalled();

        // Verifies the topics epoch job setting metric is logged.
        verify(mSpyAdServicesLogger)
                .logTopicsScheduleEpochJobSettingReportedStats(argumentCaptor.capture());
        TopicsScheduleEpochJobSettingReportedStats stats = argumentCaptor.getValue();
        expect.that(stats.getRescheduleEpochJobStatus())
                .isEqualTo(TOPICS_RESCHEDULE_EPOCH_JOB_STATUS_SKIP_RESCHEDULE_EMPTY_PENDING_JOB);
        expect.that(stats.getPreviousEpochJobSetting())
                .isEqualTo(TOPICS_EPOCH_JOB_BATTERY_CONSTRAINT_UNKNOWN_SETTING);
        expect.that(stats.getCurrentEpochJobSetting())
                .isEqualTo(TOPICS_EPOCH_JOB_BATTERY_CONSTRAINT_UNKNOWN_SETTING);
        expect.that(stats.getScheduleIfNeededEpochJobStatus())
                .isEqualTo(TOPICS_EPOCH_JOB_BATTERY_CONSTRAINT_UNKNOWN_SETTING);
    }

    @Test
    public void testRescheduleEpochJob_rescheduleBatteryNotLow_success()
            throws InterruptedException {
        // Initializes the argumentCaptor to collect topics epoch job setting metrics.
        ArgumentCaptor<TopicsScheduleEpochJobSettingReportedStats> argumentCaptor =
                ArgumentCaptor.forClass(TopicsScheduleEpochJobSettingReportedStats.class);
        when(mMockFlags.getTopicsEpochJobBatteryConstraintLoggingEnabled()).thenReturn(true);

        when(mMockFlags.getGlobalKillSwitch()).thenReturn(false);
        when(mMockFlags.getTopicsJobSchedulerRescheduleEnabled()).thenReturn(true);
        when(mMockFlags.getTopicsEpochJobBatteryNotLowInsteadOfCharging()).thenReturn(true);

        AnswerSyncCallback<Void> callback = AnswerSyncCallback.forSingleVoidAnswer();
        doAnswer(callback).when(mSpyAdServicesLogger)
                .logTopicsScheduleEpochJobSettingReportedStats(any());

        appContext.set(mMockContext);
        JobInfo existingJobInfo =
                new JobInfo.Builder(
                        TOPICS_EPOCH_JOB_ID,
                        new ComponentName(mMockContext, EpochJobService.class))
                        .setMinimumLatency(MINIMUM_SCHEDULING_DELAY_MS)
                        .setRequiresCharging(true)
                        .build();
        when(mMockContext.getSystemService(JobScheduler.class)).thenReturn(mMockJobScheduler);
        when(mMockJobScheduler.getPendingJob(TOPICS_EPOCH_JOB_ID)).thenReturn(existingJobInfo);
        doReturn(new TopicsScheduleEpochJobSettingReportedStatsLogger(
                mSpyAdServicesLogger, mMockFlags))
                .when(TopicsScheduleEpochJobSettingReportedStatsLogger::getInstance);

        EpochJobService.rescheduleEpochJob();
        callback.assertCalled();

        // Verifies the topics epoch job setting metric is logged.
        verify(mSpyAdServicesLogger)
                .logTopicsScheduleEpochJobSettingReportedStats(argumentCaptor.capture());
        TopicsScheduleEpochJobSettingReportedStats stats = argumentCaptor.getValue();
        expect.that(stats.getRescheduleEpochJobStatus())
                .isEqualTo(TOPICS_RESCHEDULE_EPOCH_JOB_STATUS_RESCHEDULE_SUCCESS);
        expect.that(stats.getPreviousEpochJobSetting())
                .isEqualTo(TOPICS_EPOCH_JOB_BATTERY_CONSTRAINT_REQUIRES_CHARGING);
        expect.that(stats.getCurrentEpochJobSetting())
                .isEqualTo(TOPICS_EPOCH_JOB_BATTERY_CONSTRAINT_REQUIRES_BATTERY_NOT_LOW);
        expect.that(stats.getScheduleIfNeededEpochJobStatus())
                .isEqualTo(TOPICS_EPOCH_JOB_BATTERY_CONSTRAINT_REQUIRES_BATTERY_NOT_LOW);
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

/*
 * Copyright (C) 2023 The Android Open Source Project
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

package com.android.adservices.cobalt;

import static com.android.adservices.cobalt.CobaltConstants.DEFAULT_API_KEY;
import static com.android.adservices.cobalt.CobaltConstants.DEFAULT_RELEASE_STAGE;
import static com.android.adservices.mockito.ExtendedMockitoExpectations.mockAdServicesJobServiceLogger;
import static com.android.adservices.mockito.MockitoExpectations.mockBackgroundJobsLoggingKillSwitch;
import static com.android.adservices.mockito.MockitoExpectations.syncLogExecutionStats;
import static com.android.adservices.mockito.MockitoExpectations.syncPersistJobExecutionData;
import static com.android.adservices.mockito.MockitoExpectations.verifyBackgroundJobsSkipLogged;
import static com.android.adservices.mockito.MockitoExpectations.verifyJobFinishedLogged;
import static com.android.adservices.mockito.MockitoExpectations.verifyLoggingNotHappened;
import static com.android.adservices.mockito.MockitoExpectations.verifyOnStopJobLogged;
import static com.android.adservices.spe.AdServicesJobInfo.COBALT_LOGGING_JOB;
import static com.android.dx.mockito.inline.extended.ExtendedMockito.doReturn;
import static com.android.dx.mockito.inline.extended.ExtendedMockito.doNothing;
import static com.android.dx.mockito.inline.extended.ExtendedMockito.staticMockMarker;
import static com.android.dx.mockito.inline.extended.ExtendedMockito.verify;

import static com.google.common.truth.Truth.assertThat;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.verifyNoMoreInteractions;
import static org.mockito.Mockito.when;

import android.app.job.JobInfo;
import android.app.job.JobParameters;
import android.app.job.JobScheduler;
import android.content.ComponentName;
import android.content.Context;

import com.android.adservices.common.AdServicesExtendedMockitoTestCase;
import com.android.adservices.common.synccallback.JobServiceLoggingCallback;
import com.android.adservices.service.Flags;
import com.android.adservices.service.FlagsFactory;
import com.android.adservices.service.common.compat.ServiceCompatUtils;
import com.android.adservices.shared.testing.BooleanSyncCallback;
import com.android.adservices.shared.testing.JobServiceCallback;
import com.android.adservices.shared.testing.annotations.RequiresSdkLevelAtLeastS;
import com.android.adservices.spe.AdServicesJobServiceLogger;
import com.android.cobalt.CobaltPeriodicJob;
import com.android.modules.utils.testing.ExtendedMockitoRule.MockStatic;
import com.android.modules.utils.testing.ExtendedMockitoRule.SpyStatic;

import org.junit.After;
import org.junit.Before;
import org.junit.Test;
import org.mockito.Mock;
import org.mockito.Spy;

import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;

@RequiresSdkLevelAtLeastS(reason = "Cobalt is not launching for Android R")
@SpyStatic(CobaltJobService.class)
@SpyStatic(FlagsFactory.class)
@SpyStatic(CobaltFactory.class)
@SpyStatic(AdServicesJobServiceLogger.class)
@MockStatic(ServiceCompatUtils.class)
public final class CobaltJobServiceTest extends AdServicesExtendedMockitoTestCase {

    private static final long JOB_INTERVAL_MS = 21_600_000L;
    private static final long JOB_FLEX_MS = 2_000_000L;
    private static final int COBALT_LOGGING_JOB_ID = COBALT_LOGGING_JOB.getJobId();
    private static final JobScheduler JOB_SCHEDULER = sContext.getSystemService(JobScheduler.class);
    @Spy private CobaltJobService mSpyCobaltJobService;

    @Mock Flags mMockFlags;
    @Mock CobaltPeriodicJob mMockCobaltPeriodicJob;
    @Mock JobParameters mMockJobParameters;

    private final ExecutorService mExecutorService = Executors.newSingleThreadExecutor();
    private AdServicesJobServiceLogger mLogger;

    @Before
    public void setup() {
        doReturn(JOB_SCHEDULER).when(mSpyCobaltJobService).getSystemService(JobScheduler.class);
        mockCobaltLoggingFlags();

        mLogger = mockAdServicesJobServiceLogger(sContext, mMockFlags);
    }

    @After
    public void teardown() {
        JOB_SCHEDULER.cancelAll();
    }

    @Test
    public void testOnStartJob_featureDisabled_withoutLogging() throws Exception {
        mockBackgroundJobsLoggingKillSwitch(mMockFlags, /* overrideValue= */ true);

        onStartJob_featureDisabled();

        verifyLoggingNotHappened(mLogger);
    }

    @Test
    public void testOnStartJob_featureEnabled_withoutLogging() throws Exception {
        mockBackgroundJobsLoggingKillSwitch(mMockFlags, /* overrideValue= */ true);

        onStartJob_featureEnabled();

        verifyLoggingNotHappened(mLogger);
    }

    @Test
    public void testOnStartJob_featureDisabled_withLogging() throws Exception {
        mockBackgroundJobsLoggingKillSwitch(mMockFlags, /* overrideValue= */ false);
        JobServiceLoggingCallback callback = syncLogExecutionStats(mLogger);

        onStartJob_featureDisabled();

        verifyBackgroundJobsSkipLogged(mLogger, callback);
    }

    @Test
    public void testOnStartJob_featureEnabled_withLogging() throws Exception {
        mockBackgroundJobsLoggingKillSwitch(mMockFlags, /* overrideValue= */ false);
        JobServiceLoggingCallback onStartJobCallback = syncPersistJobExecutionData(mLogger);
        JobServiceLoggingCallback onJobDoneCallback = syncLogExecutionStats(mLogger);

        onStartJob_featureEnabled();

        verifyJobFinishedLogged(mLogger, onStartJobCallback, onJobDoneCallback);
    }

    @Test
    public void testOnStopJob_withoutLogging() throws Exception {
        mockBackgroundJobsLoggingKillSwitch(mMockFlags, /* overrideValue= */ true);

        onStopJob();

        verifyLoggingNotHappened(mLogger);
    }

    @Test
    public void testOnStopJob_withLogging() throws Exception {
        mockBackgroundJobsLoggingKillSwitch(mMockFlags, /* overrideValue= */ false);
        JobServiceLoggingCallback callback = syncLogExecutionStats(mLogger);

        onStopJob();

        verifyOnStopJobLogged(mLogger, callback);
    }

    @Test
    public void testSchedule_featureEnabled() throws Exception {
        // Feature is Enabled.
        mockCobaltLoggingEnabled(/* overrideValue= */ true);

        BooleanSyncCallback callBack = scheduleJobInBackground(/* forceSchedule */ false);

        assertJobScheduled(callBack, /* shouldSchedule */ true);
    }

    @Test
    public void testSchedule_featureDisabled() throws Exception {
        // Feature is disabled.
        mockCobaltLoggingEnabled(false);

        BooleanSyncCallback callBack = scheduleJobInBackground(/* forceSchedule */ false);

        assertJobScheduled(callBack, /* shouldSchedule */ false);
        verifyNoMoreInteractions(staticMockMarker(CobaltFactory.class));
    }

    @Test
    public void testScheduleIfNeeded_success() throws Exception {
        // Feature is enabled.
        mockCobaltLoggingEnabled(/* overrideValue= */ true);
        mockBackgroundJobsLoggingKillSwitch(mMockFlags, /* overrideValue= */ true);

        BooleanSyncCallback callBack = scheduleJobInBackground(/* forceSchedule */ false);

        assertJobScheduled(callBack, /* shouldSchedule */ true);
    }

    @Test
    public void testScheduleIfNeeded_scheduleWithSameParameters() throws Exception {
        // Feature is enabled.
        mockCobaltLoggingEnabled(/* overrideValue= */ true);
        mockBackgroundJobsLoggingKillSwitch(mMockFlags, /* overrideValue= */ true);

        doReturn(/* COBALT_LOGGING_JOB_PERIOD_MS */ JOB_INTERVAL_MS)
                .when(mMockFlags)
                .getCobaltLoggingJobPeriodMs();

        scheduleJobDirectly();

        // The second invocation of scheduleIfNeeded() with the same parameters should skip
        // scheduling.
        BooleanSyncCallback callBack = scheduleJobInBackground(/* forceSchedule */ false);

        assertJobScheduled(callBack, /* shouldSchedule */ false);
    }

    @Test
    public void testScheduleIfNeeded_scheduleWithDifferentParameters() throws Exception {
        // Feature is enabled.
        mockCobaltLoggingEnabled(/* overrideValue= */ true);
        mockBackgroundJobsLoggingKillSwitch(mMockFlags, /* overrideValue= */ true);

        scheduleJobDirectly();

        // The second invocation of scheduleIfNeeded() with different parameters should schedule a
        // new job.
        doReturn(/* COBALT_LOGGING_JOB_PERIOD_MS */ JOB_INTERVAL_MS + 1)
                .when(mMockFlags)
                .getCobaltLoggingJobPeriodMs();

        BooleanSyncCallback callBack = scheduleJobInBackground(/* forceSchedule */ false);

        assertJobScheduled(callBack, /* shouldSchedule */ true);
    }

    @Test
    public void testScheduleIfNeeded_forceRun() throws Exception {
        // Feature is enabled.
        mockCobaltLoggingEnabled(/* overrideValue= */ true);
        mockBackgroundJobsLoggingKillSwitch(mMockFlags, /* overrideValue= */ true);

        doReturn(/* COBALT_LOGGING_JOB_PERIOD_MS */ JOB_INTERVAL_MS)
                .when(mMockFlags)
                .getCobaltLoggingJobPeriodMs();

        scheduleJobDirectly();

        // The second invocation of scheduleIfNeeded() schedules the job with same
        // parameter and force to schedule the job.
        BooleanSyncCallback callBack = scheduleJobInBackground(/* forceSchedule */ true);

        assertJobScheduled(callBack, /* shouldSchedule */ true);
    }

    @Test
    public void testOnStartJob_shouldDisableJobTrue_withoutLogging() {
        doReturn(mMockFlags).when(FlagsFactory::getFlags);
        mockBackgroundJobsLoggingKillSwitch(mMockFlags, /* overrideValue= */ true);

        onStartJob_shouldDisableJobTrue();

        verifyLoggingNotHappened(mLogger);
    }

    @Test
    public void testOnStartJob_shouldDisableJobTrue_withLoggingEnabled() {
        doReturn(mMockFlags).when(FlagsFactory::getFlags);
        mockBackgroundJobsLoggingKillSwitch(mMockFlags, /* overrideValue= */ false);

        onStartJob_shouldDisableJobTrue();

        // Verify no logging has happened even though logging is enabled because this field is not
        // logged
        verifyLoggingNotHappened(mLogger);
    }

    private void onStartJob_featureEnabled() throws InterruptedException {
        // Feature is enabled.
        mockCobaltLoggingEnabled(/* overrideValue= */ true);

        doReturn(mMockCobaltPeriodicJob)
                .when(() -> CobaltFactory.getCobaltPeriodicJob(any(), any()));

        JobServiceCallback callback =
                new JobServiceCallback().expectJobFinished(mSpyCobaltJobService);

        mSpyCobaltJobService.onStartJob(mMockJobParameters);

        callback.assertJobFinished();

        // Check that generateAggregatedObservations() is executed.
        verify(() -> CobaltFactory.getCobaltPeriodicJob(any(Context.class), any(Flags.class)));
        verify(mMockCobaltPeriodicJob).generateAggregatedObservations();
    }

    private void onStartJob_featureDisabled() throws InterruptedException {
        // Feature is disabled.
        mockCobaltLoggingEnabled(/* overrideValue= */ false);

        doNothing().when(mSpyCobaltJobService).jobFinished(mMockJobParameters, false);

        // Schedule the job to assert after starting that the scheduled job has been cancelled
        JobInfo existingJobInfo =
                new JobInfo.Builder(
                                COBALT_LOGGING_JOB_ID,
                                new ComponentName(sContext, CobaltJobService.class))
                        .setRequiresCharging(true)
                        .setPersisted(true)
                        .setPeriodic(/* periodMs */ JOB_INTERVAL_MS, JOB_FLEX_MS)
                        .build();
        JOB_SCHEDULER.schedule(existingJobInfo);
        assertThat(JOB_SCHEDULER.getPendingJob(COBALT_LOGGING_JOB_ID)).isNotNull();

        JobServiceCallback callback =
                new JobServiceCallback().expectJobFinished(mSpyCobaltJobService);

        // Now verify that when the Job starts, it will be unscheduled.
        assertThat(mSpyCobaltJobService.onStartJob(mMockJobParameters)).isFalse();
        assertThat(JOB_SCHEDULER.getPendingJob(COBALT_LOGGING_JOB_ID)).isNull();

        callback.assertJobFinished();

        verify(mSpyCobaltJobService).jobFinished(mMockJobParameters, false);
        verifyNoMoreInteractions(staticMockMarker(CobaltFactory.class));
    }

    private void onStopJob() throws InterruptedException {
        // Feature is enabled.
        mockCobaltLoggingEnabled(/* overrideValue= */ true);

        JobServiceCallback callback =
                new JobServiceCallback().expectJobStopped(mSpyCobaltJobService);
        // Verify nothing throws.
        mSpyCobaltJobService.onStopJob(mMockJobParameters);

        callback.assertJobStopped();
    }

    private void onStartJob_shouldDisableJobTrue() {
        doReturn(true)
                .when(
                        () ->
                                ServiceCompatUtils.shouldDisableExtServicesJobOnTPlus(
                                        any(Context.class)));

        doNothing().when(mSpyCobaltJobService).jobFinished(mMockJobParameters, false);

        // Schedule the job to assert after starting that the scheduled job has been cancelled
        JobInfo existingJobInfo =
                new JobInfo.Builder(
                                COBALT_LOGGING_JOB_ID,
                                new ComponentName(sContext, CobaltJobService.class))
                        .setRequiresCharging(true)
                        .setPersisted(true)
                        .setPeriodic(/* periodMs */ JOB_INTERVAL_MS, JOB_FLEX_MS)
                        .build();
        JOB_SCHEDULER.schedule(existingJobInfo);
        assertThat(JOB_SCHEDULER.getPendingJob(COBALT_LOGGING_JOB_ID)).isNotNull();

        // Now verify that when the Job starts, it will be unscheduled.
        assertThat(mSpyCobaltJobService.onStartJob(mMockJobParameters)).isFalse();

        assertThat(JOB_SCHEDULER.getPendingJob(COBALT_LOGGING_JOB_ID)).isNull();

        verify(mSpyCobaltJobService).jobFinished(mMockJobParameters, false);
        verifyNoMoreInteractions(staticMockMarker(CobaltFactory.class));
    }

    private void mockCobaltLoggingEnabled(boolean overrideValue) {
        doReturn(overrideValue).when(mMockFlags).getCobaltLoggingEnabled();
    }

    private void mockCobaltLoggingFlags() {
        extendedMockito.mockGetFlags(mMockFlags);

        when(mMockFlags.getAdservicesReleaseStageForCobalt()).thenReturn(DEFAULT_RELEASE_STAGE);
        when(mMockFlags.getCobaltAdservicesApiKeyHex()).thenReturn(DEFAULT_API_KEY);
    }

    private BooleanSyncCallback scheduleJobInBackground(boolean forceSchedule) {
        doNothing().when(() -> CobaltJobService.schedule(any(), any(), any()));
        BooleanSyncCallback callback = new BooleanSyncCallback();

        mExecutorService.execute(
                () ->
                        callback.injectResult(
                                CobaltJobService.scheduleIfNeeded(sContext, forceSchedule)));

        return callback;
    }

    private void assertJobScheduled(BooleanSyncCallback callback, boolean shouldSchedule)
            throws InterruptedException {
        assertThat(callback.assertResultReceived()).isEqualTo(shouldSchedule);
    }

    private void scheduleJobDirectly() {
        JobInfo jobInfo =
                new JobInfo.Builder(
                                COBALT_LOGGING_JOB_ID,
                                new ComponentName(sContext, CobaltJobService.class))
                        .setRequiresCharging(true)
                        .setPeriodic(JOB_INTERVAL_MS, JOB_FLEX_MS)
                        .build();
        JOB_SCHEDULER.schedule(jobInfo);
    }
}

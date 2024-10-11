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
import static com.android.adservices.spe.AdServicesJobInfo.COBALT_LOGGING_JOB;
import static com.android.dx.mockito.inline.extended.ExtendedMockito.doNothing;
import static com.android.dx.mockito.inline.extended.ExtendedMockito.doReturn;
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

import com.android.adservices.common.AdServicesJobServiceTestCase;
import com.android.adservices.service.Flags;
import com.android.adservices.service.FlagsFactory;
import com.android.adservices.service.common.compat.ServiceCompatUtils;
import com.android.adservices.shared.testing.BooleanSyncCallback;
import com.android.adservices.shared.testing.JobServiceLoggingCallback;
import com.android.adservices.shared.testing.concurrency.JobServiceCallback;
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

@SpyStatic(CobaltJobService.class)
@SpyStatic(FlagsFactory.class)
@SpyStatic(CobaltFactory.class)
@SpyStatic(AdServicesJobServiceLogger.class)
@MockStatic(ServiceCompatUtils.class)
public final class CobaltJobServiceTest extends AdServicesJobServiceTestCase {

    private static final long JOB_INTERVAL_MS = 21_600_000L;
    private static final long JOB_FLEX_MS = 2_000_000L;
    private static final int COBALT_LOGGING_JOB_ID = COBALT_LOGGING_JOB.getJobId();
    private static final JobScheduler JOB_SCHEDULER = sContext.getSystemService(JobScheduler.class);

    @Spy private CobaltJobService mSpyCobaltJobService;

    @Mock CobaltPeriodicJob mMockCobaltPeriodicJob;
    @Mock JobParameters mMockJobParameters;

    private final ExecutorService mExecutorService = Executors.newSingleThreadExecutor();
    private AdServicesJobServiceLogger mLogger;

    @Before
    public void setup() {
        mocker.mockGetFlags(mMockFlags);
        doReturn(JOB_SCHEDULER).when(mSpyCobaltJobService).getSystemService(JobScheduler.class);
        mockCobaltLoggingFlags();

        mLogger = mocker.mockNoOpAdServicesJobServiceLogger(mContext, mMockFlags);
    }

    @After
    public void teardown() {
        JOB_SCHEDULER.cancelAll();
    }

    @Test
    public void testSchedule_featureEnabled() throws Exception {
        // Feature is Enabled.
        mockCobaltLoggingEnabled(true);

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
        mockCobaltLoggingEnabled(true);

        BooleanSyncCallback callBack = scheduleJobInBackground(/* forceSchedule */ false);

        assertJobScheduled(callBack, /* shouldSchedule */ true);
    }

    @Test
    public void testScheduleIfNeeded_scheduleWithSameParameters() throws Exception {
        // Feature is enabled.
        mockCobaltLoggingEnabled(true);

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
        mockCobaltLoggingEnabled(true);

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
        mockCobaltLoggingEnabled(true);

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
    public void onStartJob_featureEnabled() throws Exception {
        JobServiceLoggingCallback onStartJobCallback = syncPersistJobExecutionData(mLogger);
        JobServiceLoggingCallback onJobDoneCallback = syncLogExecutionStats(mLogger);

        // Feature is enabled.
        mockCobaltLoggingEnabled(true);

        doReturn(mMockCobaltPeriodicJob)
                .when(() -> CobaltFactory.getCobaltPeriodicJob(any(), any()));

        JobServiceCallback callback =
                new JobServiceCallback().expectJobFinished(mSpyCobaltJobService);

        mSpyCobaltJobService.onStartJob(mMockJobParameters);

        callback.assertJobFinished();

        // Check that generateAggregatedObservations() is executed.
        verify(() -> CobaltFactory.getCobaltPeriodicJob(any(Context.class), any(Flags.class)));
        verify(mMockCobaltPeriodicJob).generateAggregatedObservations();

        verifyJobFinishedLogged(mLogger, onStartJobCallback, onJobDoneCallback);
    }

    @Test
    public void onStartJob_featureDisabled() throws Exception {
        JobServiceLoggingCallback loggingCallback = syncLogExecutionStats(mLogger);

        // Feature is disabled.
        mockCobaltLoggingEnabled(false);

        doNothing().when(mSpyCobaltJobService).jobFinished(mMockJobParameters, false);

        // Schedule the job to assert after starting that the scheduled job has been cancelled
        JobInfo existingJobInfo =
                new JobInfo.Builder(
                                COBALT_LOGGING_JOB_ID,
                                new ComponentName(mContext, CobaltJobService.class))
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

        verifyBackgroundJobsSkipLogged(mLogger, loggingCallback);
    }

    @Test
    public void onStopJob() throws Exception {
        JobServiceLoggingCallback loggingCallback = syncLogExecutionStats(mLogger);

        // Feature is enabled.
        mockCobaltLoggingEnabled(true);

        JobServiceCallback callback =
                new JobServiceCallback().expectJobStopped(mSpyCobaltJobService);
        // Verify nothing throws.
        mSpyCobaltJobService.onStopJob(mMockJobParameters);

        callback.assertJobStopped();

        verifyOnStopJobLogged(mLogger, loggingCallback);
    }

    @Test
    public void onStartJob_shouldDisableJobTrue() {
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
                                new ComponentName(mContext, CobaltJobService.class))
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

        // Verify no logging has happened even though logging is enabled because this field is not
        // logged
        verifyLoggingNotHappened(mLogger);
    }

    private void mockCobaltLoggingEnabled(boolean value) {
        mocker.mockGetCobaltLoggingEnabled(value);
    }

    private void mockCobaltLoggingFlags() {
        mocker.mockGetAdservicesReleaseStageForCobalt(DEFAULT_RELEASE_STAGE);
        when(mMockFlags.getCobaltAdservicesApiKeyHex()).thenReturn(DEFAULT_API_KEY);
    }

    private BooleanSyncCallback scheduleJobInBackground(boolean forceSchedule) {
        doNothing().when(() -> CobaltJobService.schedule(any(), any(), any()));
        BooleanSyncCallback callback = new BooleanSyncCallback();

        mExecutorService.execute(
                () ->
                        callback.injectResult(
                                CobaltJobService.scheduleIfNeeded(mContext, forceSchedule)));

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
                                new ComponentName(mContext, CobaltJobService.class))
                        .setRequiresCharging(true)
                        .setPeriodic(JOB_INTERVAL_MS, JOB_FLEX_MS)
                        .build();
        JOB_SCHEDULER.schedule(jobInfo);
    }
}

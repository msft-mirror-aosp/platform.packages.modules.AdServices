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
import static com.android.adservices.mockito.ExtendedMockitoExpectations.mockGetFlags;
import static com.android.adservices.service.stats.AdServicesStatsLog.AD_SERVICES_BACKGROUND_JOBS_EXECUTION_REPORTED__EXECUTION_RESULT_CODE__SKIP_FOR_KILL_SWITCH_ON;
import static com.android.adservices.spe.AdservicesJobInfo.COBALT_LOGGING_JOB;
import static com.android.dx.mockito.inline.extended.ExtendedMockito.doAnswer;
import static com.android.dx.mockito.inline.extended.ExtendedMockito.doReturn;
import static com.android.dx.mockito.inline.extended.ExtendedMockito.staticMockMarker;
import static com.android.dx.mockito.inline.extended.ExtendedMockito.verify;

import static com.google.common.truth.Truth.assertThat;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyBoolean;
import static org.mockito.ArgumentMatchers.anyInt;
import static org.mockito.ArgumentMatchers.anyLong;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.doNothing;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.spy;
import static org.mockito.Mockito.verifyNoMoreInteractions;
import static org.mockito.Mockito.when;

import android.app.job.JobInfo;
import android.app.job.JobParameters;
import android.app.job.JobScheduler;
import android.content.ComponentName;
import android.content.Context;

import androidx.test.core.app.ApplicationProvider;

import com.android.adservices.common.SyncCallback;
import com.android.adservices.mockito.AdServicesExtendedMockitoRule;
import com.android.adservices.service.Flags;
import com.android.adservices.service.FlagsFactory;
import com.android.adservices.service.common.compat.ServiceCompatUtils;
import com.android.adservices.service.stats.Clock;
import com.android.adservices.service.stats.StatsdAdServicesLogger;
import com.android.adservices.spe.AdservicesJobServiceLogger;
import com.android.cobalt.CobaltPeriodicJob;

import org.junit.After;
import org.junit.Before;
import org.junit.Rule;
import org.junit.Test;
import org.mockito.Mock;
import org.mockito.Spy;


public final class CobaltJobServiceTest {
    private static final int JOB_SCHEDULED_WAIT_TIME_MS = 5_000;
    private static final long JOB_INTERVAL_MS = 21_600_000L;
    private static final long JOB_FLEX_MS = 2_000_000L;
    private static final int COBALT_LOGGING_JOB_ID = COBALT_LOGGING_JOB.getJobId();
    private static final Context sContext = ApplicationProvider.getApplicationContext();
    private static final JobScheduler sJobScheduler = sContext.getSystemService(JobScheduler.class);

    @Spy private CobaltJobService mSpyCobaltJobService;

    @Mock Flags mMockFlags;
    @Mock StatsdAdServicesLogger mMockStatsdLogger;
    @Mock CobaltPeriodicJob mMockCobaltPeriodicJob;
    @Mock JobParameters mMockJobParameters;

    private AdservicesJobServiceLogger mLogger;

    @Rule
    public final AdServicesExtendedMockitoRule mAdServicesExtendedMockitoRule =
            new AdServicesExtendedMockitoRule.Builder(this)
                    .spyStatic(CobaltJobService.class)
                    .spyStatic(FlagsFactory.class)
                    .spyStatic(CobaltFactory.class)
                    .spyStatic(AdservicesJobServiceLogger.class)
                    .mockStatic(ServiceCompatUtils.class)
                    .build();

    @Before
    public void setup() {
        doReturn(sJobScheduler).when(mSpyCobaltJobService).getSystemService(JobScheduler.class);
        mockCobaltLoggingFlags();

        // Mock AdservicesJobServiceLogger to not actually log the stats to server
        mLogger =
                spy(
                        new AdservicesJobServiceLogger(
                                sContext, Clock.SYSTEM_CLOCK, mMockStatsdLogger));
        doNothing().when(mLogger).logExecutionStats(anyInt(), anyLong(), anyInt(), anyInt());
        doReturn(mLogger).when(() -> AdservicesJobServiceLogger.getInstance(any()));
    }

    @After
    public void teardown() {
        sJobScheduler.cancelAll();
    }

    @Test
    public void testOnStartJob_featureDisabled_withoutLogging() throws InterruptedException {
        // Logging killswitch is on.
        mockBackgroundJobsLoggingKillSwitch(/* overrideValue= */ true);

        onStartJob_featureDisabled();
        // Verify logging methods are not invoked.
        verifyBackgroundJobsLoggingNeverCalled();
    }

    @Test
    public void testOnStartJob_featureEnabled_withoutLogging() throws InterruptedException {
        // Logging killswitch is on.
        mockBackgroundJobsLoggingKillSwitch(/* overrideValue= */ true);

        onStartJob_featureEnabled();

        // Verify logging methods are not invoked.
        verifyBackgroundJobsLoggingNeverCalled();
    }

    @Test
    public void testOnStartJob_featureDisabled_withLogging() throws InterruptedException {
        // Logging killswitch is off.
        mockBackgroundJobsLoggingKillSwitch(/* overrideValue= */ false);

        onStartJob_featureDisabled();

        // Verify logging methods are invoked.
        verifyJobSkipLoggedOnce();
    }

    @Test
    public void testOnStartJob_featureEnabled_withLogging() throws InterruptedException {
        // Logging killswitch is off.
        mockBackgroundJobsLoggingKillSwitch(/* overrideValue= */ false);

        onStartJob_featureEnabled();

        // Verify logging methods are invoked.
        verifyJobFinishedLoggedOnce();
    }

    @Test
    public void testOnStopJob_withoutLogging() throws InterruptedException {
        // Logging killswitch is on.
        mockBackgroundJobsLoggingKillSwitch(/* overrideValue= */ true);

        onStopJob();

        // Verify logging methods are not invoked.
        verifyBackgroundJobsLoggingNeverCalled();
    }

    @Test
    public void testOnStopJob_withLogging() throws InterruptedException {
        // Logging killswitch is off.
        mockBackgroundJobsLoggingKillSwitch(/* overrideValue= */ false);

        onStopJob();

        // Verify logging methods invoked.
        verifyOnStopJobLoggedOnce();
    }

    @Test
    public void testSchedule_featureEnabled() throws InterruptedException {
        // Feature is Enabled.
        mockCobaltLoggingEnabled(/* overrideValue= */ true);

        assertThat(CobaltJobService.scheduleIfNeeded(sContext, /* forceSchedule */ false)).isTrue();

        waitForJobFinished(JOB_SCHEDULED_WAIT_TIME_MS);
    }

    @Test
    public void testSchedule_featureDisabled() {
        // Feature is disabled.
        mockCobaltLoggingEnabled(false);

        assertThat(CobaltJobService.scheduleIfNeeded(sContext, /* forceSchedule */ false))
                .isFalse();
        verifyNoMoreInteractions(staticMockMarker(CobaltFactory.class));
    }

    @Test
    public void testScheduleIfNeeded_success() throws InterruptedException {
        // Feature is enabled.
        mockCobaltLoggingEnabled(/* overrideValue= */ true);
        mockBackgroundJobsLoggingKillSwitch(/* overrideValue= */ true);

        assertThat(CobaltJobService.scheduleIfNeeded(sContext, false)).isTrue();
        assertThat(sJobScheduler.getPendingJob(COBALT_LOGGING_JOB_ID)).isNotNull();

        waitForJobFinished(JOB_SCHEDULED_WAIT_TIME_MS);
    }

    @Test
    public void testScheduleIfNeeded_scheduleWithSameParameters() throws InterruptedException {
        // Feature is enabled.
        mockCobaltLoggingEnabled(/* overrideValue= */ true);
        mockBackgroundJobsLoggingKillSwitch(/* overrideValue= */ true);

        doReturn(/* COBALT_LOGGING_JOB_PERIOD_MS */ JOB_INTERVAL_MS)
                .when(mMockFlags)
                .getCobaltLoggingJobPeriodMs();

        // The first invocation of scheduleIfNeeded() schedules the job.
        assertThat(CobaltJobService.scheduleIfNeeded(sContext, /* forceSchedule */ false)).isTrue();
        assertThat(sJobScheduler.getPendingJob(COBALT_LOGGING_JOB_ID)).isNotNull();

        // The second invocation of scheduleIfNeeded() with same parameters skips the scheduling.
        assertThat(CobaltJobService.scheduleIfNeeded(sContext, /* forceSchedule */ false))
                .isFalse();

        waitForJobFinished(JOB_SCHEDULED_WAIT_TIME_MS);
    }

    @Test
    public void testScheduleIfNeeded_scheduleWithDifferentParameters() throws InterruptedException {
        // Feature is enabled.
        mockCobaltLoggingEnabled(/* overrideValue= */ true);
        mockBackgroundJobsLoggingKillSwitch(/* overrideValue= */ true);

        doReturn(/* COBALT_LOGGING_JOB_PERIOD_MS */ JOB_INTERVAL_MS)
                .when(mMockFlags)
                .getCobaltLoggingJobPeriodMs();

        // The first invocation of scheduleIfNeeded() schedules the job.
        assertThat(CobaltJobService.scheduleIfNeeded(sContext, /* forceSchedule */ false)).isTrue();
        assertThat(sJobScheduler.getPendingJob(COBALT_LOGGING_JOB_ID)).isNotNull();

        // The second invocation of scheduleIfNeeded() with different parameters skips the
        // scheduling.
        doReturn(/* COBALT_LOGGING_JOB_PERIOD_MS */ JOB_INTERVAL_MS + 1)
                .when(mMockFlags)
                .getCobaltLoggingJobPeriodMs();
        assertThat(CobaltJobService.scheduleIfNeeded(sContext, /* forceSchedule */ false)).isTrue();

        waitForJobFinished(JOB_SCHEDULED_WAIT_TIME_MS);
    }

    @Test
    public void testScheduleIfNeeded_forceRun() throws InterruptedException {
        // Feature is enabled.
        mockCobaltLoggingEnabled(/* overrideValue= */ true);
        mockBackgroundJobsLoggingKillSwitch(/* overrideValue= */ true);

        doReturn(/* COBALT_LOGGING_JOB_PERIOD_MS */ JOB_INTERVAL_MS)
                .when(mMockFlags)
                .getCobaltLoggingJobPeriodMs();

        // The first invocation of scheduleIfNeeded() schedules the job.
        assertThat(CobaltJobService.scheduleIfNeeded(sContext, /* forceSchedule */ false)).isTrue();
        assertThat(sJobScheduler.getPendingJob(COBALT_LOGGING_JOB_ID)).isNotNull();

        // The second invocation of scheduleIfNeeded() schedules the job with same parameter and
        // force to schedule the job.
        assertThat(CobaltJobService.scheduleIfNeeded(sContext, /* forceSchedule */ true)).isTrue();
        assertThat(sJobScheduler.getPendingJob(COBALT_LOGGING_JOB_ID)).isNotNull();

        waitForJobFinished(JOB_SCHEDULED_WAIT_TIME_MS);
    }

    @Test
    public void testOnStartJob_shouldDisableJobTrue_withoutLogging() {
        // Logging killswitch is on.
        doReturn(mMockFlags).when(FlagsFactory::getFlags);
        mockBackgroundJobsLoggingKillSwitch(/* overrideValue= */ true);

        onStartJob_shouldDisableJobTrue();

        // Verify logging method is not invoked.
        verifyBackgroundJobsLoggingNeverCalled();
    }

    @Test
    public void testOnStartJob_shouldDisableJobTrue_withLoggingEnabled() {
        // Logging killswitch is off.
        doReturn(mMockFlags).when(FlagsFactory::getFlags);
        mockBackgroundJobsLoggingKillSwitch(/* overrideValue= */ false);

        onStartJob_shouldDisableJobTrue();

        // Verify no logging has happened even though logging is enabled because this field is not
        // logged
        verifyBackgroundJobsLoggingNeverCalled();
    }

    // TODO(b/296945680): remove Thread.sleep().
    /**
     * Waits for current running job to finish before mocked {@code Flags} finished mocking.
     *
     * <p>Tests needs to call this at the end of the test if scheduled background job to prevent
     * READ_DEVICE_CONFIG permission error when scheduled job start running after mocked {@code
     * flags} finished mocking.
     */
    public void waitForJobFinished(int timeout) throws InterruptedException {
        Thread.sleep(timeout);
    }

    private void onStartJob_featureEnabled() throws InterruptedException {
        // Feature is enabled.
        mockCobaltLoggingEnabled(/* overrideValue= */ true);

        doReturn(mMockCobaltPeriodicJob)
                .when(() -> CobaltFactory.getCobaltPeriodicJob(any(), any()));

        JobFinishedGuard jobFinishedGuard = createJobFinishedGuard();

        mSpyCobaltJobService.onStartJob(mMockJobParameters);

        jobFinishedGuard.assertFinished();

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
        sJobScheduler.schedule(existingJobInfo);
        assertThat(sJobScheduler.getPendingJob(COBALT_LOGGING_JOB_ID)).isNotNull();

        JobFinishedGuard jobFinishedGuard = createJobFinishedGuard();

        // Now verify that when the Job starts, it will be unscheduled.
        assertThat(mSpyCobaltJobService.onStartJob(mMockJobParameters)).isFalse();
        assertThat(sJobScheduler.getPendingJob(COBALT_LOGGING_JOB_ID)).isNull();

        jobFinishedGuard.assertFinished();

        verify(mSpyCobaltJobService).jobFinished(mMockJobParameters, false);
        verifyNoMoreInteractions(staticMockMarker(CobaltFactory.class));
    }

    private void onStopJob() throws InterruptedException {
        // Feature is enabled.
        mockCobaltLoggingEnabled(/* overrideValue= */ true);

        JobFinishedGuard jobStoppedGuard = createOnStopJobGuard();
        // Verify nothing throws.
        mSpyCobaltJobService.onStopJob(mMockJobParameters);

        jobStoppedGuard.assertFinished();
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
        sJobScheduler.schedule(existingJobInfo);
        assertThat(sJobScheduler.getPendingJob(COBALT_LOGGING_JOB_ID)).isNotNull();

        // Now verify that when the Job starts, it will be unscheduled.
        assertThat(mSpyCobaltJobService.onStartJob(mMockJobParameters)).isFalse();

        assertThat(sJobScheduler.getPendingJob(COBALT_LOGGING_JOB_ID)).isNull();

        verify(mSpyCobaltJobService).jobFinished(mMockJobParameters, false);
        verifyNoMoreInteractions(staticMockMarker(CobaltFactory.class));
    }

    private void mockBackgroundJobsLoggingKillSwitch(boolean overrideValue) {
        doReturn(overrideValue).when(mMockFlags).getBackgroundJobsLoggingKillSwitch();
    }

    private void mockCobaltLoggingEnabled(boolean overrideValue) {
        doReturn(overrideValue).when(mMockFlags).getCobaltLoggingEnabled();
    }

    private void verifyBackgroundJobsLoggingNeverCalled() {
        verify(mLogger, never()).persistJobExecutionData(anyInt(), anyLong());
        verify(mLogger, never()).logExecutionStats(anyInt(), anyLong(), anyInt(), anyInt());
    }

    private void verifyJobSkipLoggedOnce() {
        verify(mLogger).recordJobSkipped(anyInt(), anyInt());
        verify(mLogger).persistJobExecutionData(anyInt(), anyLong());
        verify(mLogger)
                .logExecutionStats(
                        anyInt(),
                        anyLong(),
                        eq(
                                AD_SERVICES_BACKGROUND_JOBS_EXECUTION_REPORTED__EXECUTION_RESULT_CODE__SKIP_FOR_KILL_SWITCH_ON),
                        anyInt());
    }

    private void verifyJobFinishedLoggedOnce() {
        verify(mLogger).recordJobFinished(anyInt(), anyBoolean(), anyBoolean());
        verify(mLogger).recordOnStartJob(anyInt());
        verify(mLogger).persistJobExecutionData(anyInt(), anyLong());
        verify(mLogger).logExecutionStats(anyInt(), anyLong(), anyInt(), anyInt());
    }

    private void verifyOnStopJobLoggedOnce() {
        verify(mLogger).recordOnStopJob(any(), anyInt(), anyBoolean());
        verify(mLogger).logExecutionStats(anyInt(), anyLong(), anyInt(), anyInt());
    }

    private JobFinishedGuard createJobFinishedGuard() {
        JobFinishedGuard jobFinishedGuard = new JobFinishedGuard();

        doAnswer(
                        unusedInvocation -> {
                            jobFinishedGuard.jobFinished();
                            return null;
                        })
                .when(mSpyCobaltJobService)
                .jobFinished(any(), anyBoolean());

        return jobFinishedGuard;
    }

    private JobFinishedGuard createOnStopJobGuard() {
        JobFinishedGuard jobFinishedGuard = new JobFinishedGuard();

        doAnswer(
                        invocation -> {
                            invocation.callRealMethod();
                            jobFinishedGuard.jobFinished();
                            return null;
                        })
                .when(mSpyCobaltJobService)
                .onStopJob(any());

        return jobFinishedGuard;
    }

    private void mockCobaltLoggingFlags() {
        // Mock static method FlagsFactory.getFlags() to return Mock Flags.
        mockGetFlags(mMockFlags);

        when(mMockFlags.getAdservicesReleaseStageForCobalt()).thenReturn(DEFAULT_RELEASE_STAGE);
        when(mMockFlags.getCobaltAdservicesApiKeyHex()).thenReturn(DEFAULT_API_KEY);
    }

    /**
     * Custom {@link SyncCallback} implementation where used for checking a background job is
     * finished.
     *
     * <p>Use a {@link Boolean} type as a place holder for received on success. This {@link Boolean}
     * is used for checking a method has been called when calling {@link #assertResultReceived()}
     */
    private static final class JobFinishedGuard extends SyncCallback<Boolean, Void> {
        public void jobFinished() {
            super.injectResult(true);
        }

        public void assertFinished() throws InterruptedException {
            assertResultReceived();
        }
    }
}

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

import static com.android.adservices.service.FlagsConstants.KEY_FLEDGE_BACKGROUND_FETCH_ENABLED;
import static com.android.adservices.service.FlagsConstants.KEY_FLEDGE_BACKGROUND_FETCH_JOB_FLEX_MS;
import static com.android.adservices.service.FlagsConstants.KEY_FLEDGE_BACKGROUND_FETCH_JOB_PERIOD_MS;
import static com.android.adservices.service.FlagsConstants.KEY_FLEDGE_CUSTOM_AUDIENCE_SERVICE_KILL_SWITCH;
import static com.android.adservices.service.FlagsConstants.KEY_GA_UX_FEATURE_ENABLED;
import static com.android.adservices.service.FlagsConstants.KEY_SPE_ON_BACKGROUND_FETCH_JOB_ENABLED;
import static com.android.adservices.shared.spe.JobServiceConstants.SCHEDULING_RESULT_CODE_SKIPPED;
import static com.android.adservices.shared.spe.JobServiceConstants.SCHEDULING_RESULT_CODE_SUCCESSFUL;
import static com.android.adservices.spe.AdServicesJobInfo.FLEDGE_BACKGROUND_FETCH_JOB;
import static com.android.dx.mockito.inline.extended.ExtendedMockito.any;
import static com.android.dx.mockito.inline.extended.ExtendedMockito.doAnswer;
import static com.android.dx.mockito.inline.extended.ExtendedMockito.doNothing;
import static com.android.dx.mockito.inline.extended.ExtendedMockito.doReturn;
import static com.android.dx.mockito.inline.extended.ExtendedMockito.never;
import static com.android.dx.mockito.inline.extended.ExtendedMockito.staticMockMarker;
import static com.android.dx.mockito.inline.extended.ExtendedMockito.verify;
import static com.android.dx.mockito.inline.extended.ExtendedMockito.verifyNoMoreInteractions;

import static com.google.common.truth.Truth.assertThat;
import static com.google.common.util.concurrent.Futures.immediateFailedFuture;
import static com.google.common.util.concurrent.Futures.immediateFuture;

import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertNotNull;
import static org.junit.Assert.assertNull;
import static org.junit.Assert.assertTrue;

import android.app.job.JobInfo;
import android.app.job.JobParameters;
import android.app.job.JobScheduler;
import android.content.ComponentName;
import android.content.Context;
import android.content.pm.PackageManager;

import com.android.adservices.common.AdServicesJobServiceTestCase;
import com.android.adservices.service.FlagsFactory;
import com.android.adservices.service.common.compat.ServiceCompatUtils;
import com.android.adservices.service.consent.AdServicesApiConsent;
import com.android.adservices.service.consent.AdServicesApiType;
import com.android.adservices.service.consent.ConsentManager;
import com.android.adservices.shared.testing.JobServiceLoggingCallback;
import com.android.adservices.spe.AdServicesJobServiceLogger;
import com.android.modules.utils.testing.ExtendedMockitoRule.MockStatic;
import com.android.modules.utils.testing.ExtendedMockitoRule.SpyStatic;

import com.google.common.util.concurrent.FluentFuture;

import org.junit.After;
import org.junit.Assume;
import org.junit.Before;
import org.junit.Test;
import org.mockito.Mock;
import org.mockito.Spy;

import java.util.concurrent.CountDownLatch;
import java.util.concurrent.ExecutionException;
import java.util.concurrent.TimeoutException;

// The actual scheduling of the job needs to be mocked out because the test application does
// not have the required permissions to schedule the job with the constraints requested by
// the BackgroundFetchJobService, and adding them is non-trivial.
@SpyStatic(FlagsFactory.class)
@MockStatic(ConsentManager.class)
@MockStatic(BackgroundFetchJob.class)
@SpyStatic(BackgroundFetchJobService.class)
@SpyStatic(BackgroundFetchWorker.class)
@SpyStatic(AdServicesJobServiceLogger.class)
@MockStatic(ServiceCompatUtils.class)
public final class BackgroundFetchJobServiceTest extends AdServicesJobServiceTestCase {
    private static final int FLEDGE_BACKGROUND_FETCH_JOB_ID =
            FLEDGE_BACKGROUND_FETCH_JOB.getJobId();
    private static final JobScheduler JOB_SCHEDULER = sContext.getSystemService(JobScheduler.class);
    // Set a minimum delay of 1 hour so scheduled jobs don't run immediately
    private static final long MINIMUM_SCHEDULING_DELAY_MS = 60L * 60L * 1000L;

    @Spy
    private final BackgroundFetchJobService mBgFJobServiceSpy = new BackgroundFetchJobService();

    @Mock private BackgroundFetchWorker mBgFWorkerMock;
    @Mock private JobParameters mJobParametersMock;
    @Mock private ConsentManager mConsentManagerMock;
    @Mock private PackageManager mPackageManagerMock;

    @Before
    public void setup() {
        Assume.assumeNotNull(JOB_SCHEDULER);
        assertNull(
                "Job already scheduled before setup!",
                JOB_SCHEDULER.getPendingJob(FLEDGE_BACKGROUND_FETCH_JOB_ID));
        mocker.mockGetFlags(mFakeFlags);
    }

    @After
    public void teardown() {
        JOB_SCHEDULER.cancelAll();
    }

    @Test
    public void testOnStartJobFlagDisabled_withLogging() throws Exception {
        setFlagsWithDisabledBgF();

        AdServicesJobServiceLogger logger =
                mocker.mockNoOpAdServicesJobServiceLogger(mContext, mFakeFlags);
        JobServiceLoggingCallback onStartJobCallback = syncPersistJobExecutionData(logger);
        JobServiceLoggingCallback onJobDoneCallback = syncLogExecutionStats(logger);

        testOnStartJobFlagDisabled();

        verifyOnStartJobLogged(logger, onStartJobCallback);
        onJobDoneCallback.assertLoggingFinished();
    }

    @Test
    public void testOnStartJobConsentRevokedGaUxDisabled() throws Exception {
        setFlagsWithEnabledBgFGaUxDisabled();
        doReturn(mConsentManagerMock).when(ConsentManager::getInstance);
        doReturn(AdServicesApiConsent.REVOKED)
                .when(mConsentManagerMock)
                .getConsent(AdServicesApiType.FLEDGE);
        doReturn(JOB_SCHEDULER).when(mBgFJobServiceSpy).getSystemService(JobScheduler.class);
        doNothing().when(mBgFJobServiceSpy).jobFinished(mJobParametersMock, false);

        // Schedule the job to assert after starting that the scheduled job has been cancelled
        JobInfo existingJobInfo =
                new JobInfo.Builder(
                                FLEDGE_BACKGROUND_FETCH_JOB_ID,
                                new ComponentName(mContext, BackgroundFetchJobService.class))
                        .setMinimumLatency(MINIMUM_SCHEDULING_DELAY_MS)
                        .build();
        JOB_SCHEDULER.schedule(existingJobInfo);
        assertNotNull(JOB_SCHEDULER.getPendingJob(FLEDGE_BACKGROUND_FETCH_JOB_ID));

        assertFalse(mBgFJobServiceSpy.onStartJob(mJobParametersMock));

        assertNull(JOB_SCHEDULER.getPendingJob(FLEDGE_BACKGROUND_FETCH_JOB_ID));
        verify(mBgFWorkerMock, never()).runBackgroundFetch();
        verify(mBgFJobServiceSpy).jobFinished(mJobParametersMock, false);
        verifyNoMoreInteractions(staticMockMarker(BackgroundFetchWorker.class));
    }

    @Test
    public void testOnStartJobConsentRevokedGaUxEnabled_withLogging() throws Exception {
        setFlagsWithEnabledBgFGaUxEnabled();

        AdServicesJobServiceLogger logger =
                mocker.mockNoOpAdServicesJobServiceLogger(mContext, mFakeFlags);
        JobServiceLoggingCallback callback = syncLogExecutionStats(logger);

        testOnStartJobConsentRevokedGaUxEnabled();

        // Verify logging has happened
        verifyBackgroundJobsSkipLogged(logger, callback);
    }

    @Test
    public void testOnStartJobCustomAudienceKillSwitchOn() throws Exception {
        setFlagsWithKillSwitchOn();
        doReturn(JOB_SCHEDULER).when(mBgFJobServiceSpy).getSystemService(JobScheduler.class);
        doNothing().when(mBgFJobServiceSpy).jobFinished(mJobParametersMock, false);

        // Schedule the job to assert after starting that the scheduled job has been cancelled
        JobInfo existingJobInfo =
                new JobInfo.Builder(
                                FLEDGE_BACKGROUND_FETCH_JOB_ID,
                                new ComponentName(mContext, BackgroundFetchJobService.class))
                        .setMinimumLatency(MINIMUM_SCHEDULING_DELAY_MS)
                        .build();
        JOB_SCHEDULER.schedule(existingJobInfo);
        assertNotNull(JOB_SCHEDULER.getPendingJob(FLEDGE_BACKGROUND_FETCH_JOB_ID));

        assertFalse(mBgFJobServiceSpy.onStartJob(mJobParametersMock));

        assertNull(JOB_SCHEDULER.getPendingJob(FLEDGE_BACKGROUND_FETCH_JOB_ID));
        verify(mBgFWorkerMock, never()).runBackgroundFetch();
        verify(mBgFJobServiceSpy).jobFinished(mJobParametersMock, false);
        verifyNoMoreInteractions(staticMockMarker(BackgroundFetchWorker.class));
    }

    @Test
    public void testOnStartJobUpdateSuccess_withLogging()
            throws Exception {
        setFlagsWithEnabledBgFGaUxDisabled();

        AdServicesJobServiceLogger logger =
                mocker.mockNoOpAdServicesJobServiceLogger(mContext, mFakeFlags);
        JobServiceLoggingCallback onStartJobCallback = syncPersistJobExecutionData(logger);
        JobServiceLoggingCallback onJobDoneCallback = syncLogExecutionStats(logger);

        testOnStartJobUpdateSuccess();

        verifyJobFinishedLogged(logger, onStartJobCallback, onJobDoneCallback);
    }

    @Test
    public void testOnStartJobUpdateTimeoutHandled_withLogging() throws Exception {
        setFlagsWithEnabledBgFGaUxDisabled();

        AdServicesJobServiceLogger logger =
                mocker.mockNoOpAdServicesJobServiceLogger(mContext, mFakeFlags);
        JobServiceLoggingCallback onStartJobCallback = syncPersistJobExecutionData(logger);
        JobServiceLoggingCallback onJobDoneCallback = syncLogExecutionStats(logger);

        testOnStartJobUpdateTimeoutHandled();

        verifyJobFinishedLogged(logger, onStartJobCallback, onJobDoneCallback);
    }

    @Test
    public void testOnStartJobUpdateInterruptedHandled() throws Exception {
        CountDownLatch jobFinishedCountDown = new CountDownLatch(1);

        setFlagsWithEnabledBgFGaUxDisabled();
        doReturn(mConsentManagerMock).when(ConsentManager::getInstance);
        doReturn(AdServicesApiConsent.GIVEN)
                .when(mConsentManagerMock)
                .getConsent(AdServicesApiType.FLEDGE);
        doReturn(mBgFWorkerMock).when(BackgroundFetchWorker::getInstance);
        doReturn(
                        FluentFuture.from(
                                immediateFailedFuture(new InterruptedException("testing timeout"))))
                .when(mBgFWorkerMock)
                .runBackgroundFetch();
        doAnswer(
                        unusedInvocation -> {
                            jobFinishedCountDown.countDown();
                            return null;
                        })
                .when(mBgFJobServiceSpy)
                .jobFinished(mJobParametersMock, false);

        assertTrue(mBgFJobServiceSpy.onStartJob(mJobParametersMock));
        jobFinishedCountDown.await();

        verify(BackgroundFetchWorker::getInstance);
        verify(mBgFWorkerMock).runBackgroundFetch();
        verify(mBgFJobServiceSpy).jobFinished(mJobParametersMock, false);
        verifyNoMoreInteractions(staticMockMarker(BackgroundFetchWorker.class));
    }

    @Test
    public void testOnStartJobUpdateExecutionExceptionHandled() throws Exception {
        CountDownLatch jobFinishedCountDown = new CountDownLatch(1);

        setFlagsWithEnabledBgFGaUxDisabled();
        doReturn(mConsentManagerMock).when(ConsentManager::getInstance);
        doReturn(AdServicesApiConsent.GIVEN)
                .when(mConsentManagerMock)
                .getConsent(AdServicesApiType.FLEDGE);
        doReturn(mBgFWorkerMock).when(BackgroundFetchWorker::getInstance);
        doReturn(
                        FluentFuture.from(
                                immediateFailedFuture(
                                        new ExecutionException("testing timeout", null))))
                .when(mBgFWorkerMock)
                .runBackgroundFetch();
        doAnswer(
                        unusedInvocation -> {
                            jobFinishedCountDown.countDown();
                            return null;
                        })
                .when(mBgFJobServiceSpy)
                .jobFinished(mJobParametersMock, false);

        assertTrue(mBgFJobServiceSpy.onStartJob(mJobParametersMock));
        jobFinishedCountDown.await();

        verify(BackgroundFetchWorker::getInstance);
        verify(mBgFWorkerMock).runBackgroundFetch();
        verify(mBgFJobServiceSpy).jobFinished(mJobParametersMock, false);
        verifyNoMoreInteractions(staticMockMarker(BackgroundFetchWorker.class));
    }

    @Test
    public void testOnStopJob() throws InterruptedException {
        AdServicesJobServiceLogger logger =
                mocker.mockNoOpAdServicesJobServiceLogger(mContext, mMockFlags);
        JobServiceLoggingCallback callback = syncLogExecutionStats(logger);

        testOnStopJobCallsStopWork();

        verifyOnStopJobLogged(logger, callback);
    }

    @Test
    public void testScheduleIfNeededFlagDisabled() {
        setFlagsWithDisabledBgF();
        assertThat(BackgroundFetchJobService.scheduleIfNeeded(mFakeFlags, false))
                .isEqualTo(SCHEDULING_RESULT_CODE_SKIPPED);

        verify(() -> BackgroundFetchJobService.schedule(any(), any()), never());
        verifyNoMoreInteractions(staticMockMarker(BackgroundFetchWorker.class));
    }

    @Test
    public void testScheduleIfNeededSuccess() {
        setFlagsWithEnabledBgFGaUxDisabled();
        assertThat(BackgroundFetchJobService.scheduleIfNeeded(mFakeFlags, false))
                .isEqualTo(SCHEDULING_RESULT_CODE_SUCCESSFUL);

        verify(() -> BackgroundFetchJobService.schedule(any(), any()));
        verifyNoMoreInteractions(staticMockMarker(BackgroundFetchWorker.class));
    }

    @Test
    public void testScheduleIfNeededSkippedAlreadyScheduled() {
        setFlagsWithEnabledBgFGaUxDisabled();
        JobInfo existingJobInfo =
                new JobInfo.Builder(
                                FLEDGE_BACKGROUND_FETCH_JOB_ID,
                                new ComponentName(mContext, BackgroundFetchJobService.class))
                        .setMinimumLatency(MINIMUM_SCHEDULING_DELAY_MS)
                        .build();
        JOB_SCHEDULER.schedule(existingJobInfo);
        assertNotNull(JOB_SCHEDULER.getPendingJob(FLEDGE_BACKGROUND_FETCH_JOB_ID));

        assertThat(BackgroundFetchJobService.scheduleIfNeeded(mFakeFlags, false))
                .isEqualTo(SCHEDULING_RESULT_CODE_SKIPPED);

        verify(() -> BackgroundFetchJobService.schedule(any(), any()), never());
        verifyNoMoreInteractions(staticMockMarker(BackgroundFetchWorker.class));
    }

    @Test
    public void testScheduleIfNeededForceSuccess() {
        setFlagsWithEnabledBgFGaUxDisabled();
        JobInfo existingJobInfo =
                new JobInfo.Builder(
                                FLEDGE_BACKGROUND_FETCH_JOB_ID,
                                new ComponentName(mContext, BackgroundFetchJobService.class))
                        .setMinimumLatency(MINIMUM_SCHEDULING_DELAY_MS)
                        .build();
        JOB_SCHEDULER.schedule(existingJobInfo);
        assertNotNull(JOB_SCHEDULER.getPendingJob(FLEDGE_BACKGROUND_FETCH_JOB_ID));

        doNothing().when(() -> BackgroundFetchJobService.schedule(any(), any()));

        assertThat(BackgroundFetchJobService.scheduleIfNeeded(mFakeFlags, true))
                .isEqualTo(SCHEDULING_RESULT_CODE_SUCCESSFUL);

        verify(() -> BackgroundFetchJobService.schedule(any(), any()));
        verifyNoMoreInteractions(staticMockMarker(BackgroundFetchWorker.class));
    }

    @Test
    public void testScheduleFlagDisabled() {
        setFlagsWithDisabledBgF();
        BackgroundFetchJobService.schedule(mContext, mFakeFlags);

        verifyNoMoreInteractions(staticMockMarker(BackgroundFetchWorker.class));
    }

    @Test
    public void testOnStartJobCustomAudienceKillSwitchOff() throws Exception {
        setFlagsWithKillSwitchOff();
        doReturn(mConsentManagerMock).when(ConsentManager::getInstance);
        doReturn(AdServicesApiConsent.GIVEN)
                .when(mConsentManagerMock)
                .getConsent(AdServicesApiType.FLEDGE);
        doReturn(mBgFWorkerMock).when(BackgroundFetchWorker::getInstance);
        doReturn(FluentFuture.from(immediateFuture(null)))
                .when(mBgFWorkerMock)
                .runBackgroundFetch();
        CountDownLatch jobFinishedCountDown = new CountDownLatch(1);

        doAnswer(
                        unusedInvocation -> {
                            jobFinishedCountDown.countDown();
                            return null;
                        })
                .when(mBgFJobServiceSpy)
                .jobFinished(mJobParametersMock, false);

        assertTrue(mBgFJobServiceSpy.onStartJob(mJobParametersMock));
        jobFinishedCountDown.await();

        verify(BackgroundFetchWorker::getInstance);
        FluentFuture<Void> unusedFuture = verify(mBgFWorkerMock).runBackgroundFetch();
        verify(mBgFJobServiceSpy).jobFinished(mJobParametersMock, false);
        verifyNoMoreInteractions(staticMockMarker(BackgroundFetchWorker.class));
    }

    @Test
    public void testOnStartJobShouldDisableJobTrue() {
        AdServicesJobServiceLogger logger =
                mocker.mockNoOpAdServicesJobServiceLogger(mContext, mMockFlags);

        doReturn(true)
                .when(
                        () ->
                                ServiceCompatUtils.shouldDisableExtServicesJobOnTPlus(
                                        any(Context.class)));
        doReturn(JOB_SCHEDULER).when(mBgFJobServiceSpy).getSystemService(JobScheduler.class);
        doNothing().when(mBgFJobServiceSpy).jobFinished(mJobParametersMock, false);

        // Schedule the job to assert after starting that the scheduled job has been cancelled
        JobInfo existingJobInfo =
                new JobInfo.Builder(
                                FLEDGE_BACKGROUND_FETCH_JOB_ID,
                                new ComponentName(mContext, BackgroundFetchJobService.class))
                        .setMinimumLatency(MINIMUM_SCHEDULING_DELAY_MS)
                        .build();
        JOB_SCHEDULER.schedule(existingJobInfo);
        assertNotNull(JOB_SCHEDULER.getPendingJob(FLEDGE_BACKGROUND_FETCH_JOB_ID));

        assertFalse(mBgFJobServiceSpy.onStartJob(mJobParametersMock));

        assertNull(JOB_SCHEDULER.getPendingJob(FLEDGE_BACKGROUND_FETCH_JOB_ID));
        verify(mBgFWorkerMock, never()).runBackgroundFetch();
        verify(mBgFJobServiceSpy).jobFinished(mJobParametersMock, false);
        verifyNoMoreInteractions(staticMockMarker(BackgroundFetchWorker.class));

        // Verify logging has not happened even though logging is enabled because this field is not
        // logged
        verifyLoggingNotHappened(logger);
    }

    private void testOnStartJobFlagDisabled() throws Exception {
        doReturn(JOB_SCHEDULER).when(mBgFJobServiceSpy).getSystemService(JobScheduler.class);
        doNothing().when(mBgFJobServiceSpy).jobFinished(mJobParametersMock, false);

        // Schedule the job to assert after starting that the scheduled job has been cancelled
        JobInfo existingJobInfo =
                new JobInfo.Builder(
                                FLEDGE_BACKGROUND_FETCH_JOB_ID,
                                new ComponentName(mContext, BackgroundFetchJobService.class))
                        .setMinimumLatency(MINIMUM_SCHEDULING_DELAY_MS)
                        .build();
        JOB_SCHEDULER.schedule(existingJobInfo);
        assertNotNull(JOB_SCHEDULER.getPendingJob(FLEDGE_BACKGROUND_FETCH_JOB_ID));

        assertFalse(mBgFJobServiceSpy.onStartJob(mJobParametersMock));

        assertNull(JOB_SCHEDULER.getPendingJob(FLEDGE_BACKGROUND_FETCH_JOB_ID));
        verify(mBgFWorkerMock, never()).runBackgroundFetch();
        verify(mBgFJobServiceSpy).jobFinished(mJobParametersMock, false);
        verifyNoMoreInteractions(staticMockMarker(BackgroundFetchWorker.class));
    }

    private void testOnStartJobUpdateSuccess() throws Exception {
        CountDownLatch jobFinishedCountDown = new CountDownLatch(1);

        doReturn(mConsentManagerMock).when(ConsentManager::getInstance);
        doReturn(AdServicesApiConsent.GIVEN)
                .when(mConsentManagerMock)
                .getConsent(AdServicesApiType.FLEDGE);
        doReturn(mBgFWorkerMock).when(BackgroundFetchWorker::getInstance);
        doReturn(FluentFuture.from(immediateFuture(null)))
                .when(mBgFWorkerMock)
                .runBackgroundFetch();
        doAnswer(
                        unusedInvocation -> {
                            jobFinishedCountDown.countDown();
                            return null;
                        })
                .when(mBgFJobServiceSpy)
                .jobFinished(mJobParametersMock, false);

        assertTrue(mBgFJobServiceSpy.onStartJob(mJobParametersMock));
        jobFinishedCountDown.await();

        verify(BackgroundFetchWorker::getInstance);
        verify(mBgFWorkerMock).runBackgroundFetch();
        verify(mBgFJobServiceSpy).jobFinished(mJobParametersMock, false);
        verifyNoMoreInteractions(staticMockMarker(BackgroundFetchWorker.class));
    }

    private void testOnStartJobUpdateTimeoutHandled() throws Exception {
        CountDownLatch jobFinishedCountDown = new CountDownLatch(1);

        doReturn(mConsentManagerMock).when(ConsentManager::getInstance);
        doReturn(AdServicesApiConsent.GIVEN)
                .when(mConsentManagerMock)
                .getConsent(AdServicesApiType.FLEDGE);
        doReturn(AdServicesApiConsent.GIVEN)
                .when(mConsentManagerMock)
                .getConsent(AdServicesApiType.FLEDGE);
        doReturn(mBgFWorkerMock).when(BackgroundFetchWorker::getInstance);
        doReturn(FluentFuture.from(immediateFailedFuture(new TimeoutException("testing timeout"))))
                .when(mBgFWorkerMock)
                .runBackgroundFetch();
        doAnswer(
                        unusedInvocation -> {
                            jobFinishedCountDown.countDown();
                            return null;
                        })
                .when(mBgFJobServiceSpy)
                .jobFinished(mJobParametersMock, false);

        assertTrue(mBgFJobServiceSpy.onStartJob(mJobParametersMock));
        jobFinishedCountDown.await();

        verify(BackgroundFetchWorker::getInstance);
        verify(mBgFWorkerMock).runBackgroundFetch();
        verify(mBgFJobServiceSpy).jobFinished(mJobParametersMock, false);
        verifyNoMoreInteractions(staticMockMarker(BackgroundFetchWorker.class));
    }

    private void testOnStartJobConsentRevokedGaUxEnabled() throws Exception {
        doReturn(mConsentManagerMock).when(ConsentManager::getInstance);
        doReturn(AdServicesApiConsent.REVOKED)
                .when(mConsentManagerMock)
                .getConsent(AdServicesApiType.FLEDGE);
        doReturn(JOB_SCHEDULER).when(mBgFJobServiceSpy).getSystemService(JobScheduler.class);
        doNothing().when(mBgFJobServiceSpy).jobFinished(mJobParametersMock, false);

        // Schedule the job to assert after starting that the scheduled job has been cancelled
        JobInfo existingJobInfo =
                new JobInfo.Builder(
                                FLEDGE_BACKGROUND_FETCH_JOB_ID,
                                new ComponentName(mContext, BackgroundFetchJobService.class))
                        .setMinimumLatency(MINIMUM_SCHEDULING_DELAY_MS)
                        .build();
        JOB_SCHEDULER.schedule(existingJobInfo);
        assertNotNull(JOB_SCHEDULER.getPendingJob(FLEDGE_BACKGROUND_FETCH_JOB_ID));

        assertFalse(mBgFJobServiceSpy.onStartJob(mJobParametersMock));

        assertNull(JOB_SCHEDULER.getPendingJob(FLEDGE_BACKGROUND_FETCH_JOB_ID));
        verify(mBgFWorkerMock, never()).runBackgroundFetch();
        verify(mBgFJobServiceSpy).jobFinished(mJobParametersMock, false);
        verifyNoMoreInteractions(staticMockMarker(BackgroundFetchWorker.class));
    }

    private void testOnStopJobCallsStopWork() {
        doReturn(mBgFWorkerMock).when(BackgroundFetchWorker::getInstance);
        doNothing().when(mBgFWorkerMock).stopWork();

        assertTrue(mBgFJobServiceSpy.onStopJob(mJobParametersMock));

        verify(mBgFWorkerMock).stopWork();
    }

    private void setFlagsWithEnabledBgFGaUxDisabled() {
        flags.setFlag(KEY_FLEDGE_BACKGROUND_FETCH_ENABLED, true);
        flags.setFlag(KEY_GA_UX_FEATURE_ENABLED, false);
        flags.setGlobalKillSwitch(false);
        // By default, do not use SPE.
        flags.setFlag(KEY_SPE_ON_BACKGROUND_FETCH_JOB_ENABLED, false);
    }

    private void setFlagsWithEnabledBgFGaUxEnabled() {
        flags.setFlag(KEY_FLEDGE_BACKGROUND_FETCH_ENABLED, true);
        flags.setFlag(KEY_GA_UX_FEATURE_ENABLED, true);
        flags.setGlobalKillSwitch(false);
        // By default, do not use SPE.
        flags.setFlag(KEY_SPE_ON_BACKGROUND_FETCH_JOB_ENABLED, false);
    }

    private void setFlagsWithDisabledBgF() {
        flags.setFlag(KEY_FLEDGE_BACKGROUND_FETCH_ENABLED, false);
        String reason = "This configured value should not be called";
        flags.onGetFlagThrows(KEY_FLEDGE_BACKGROUND_FETCH_JOB_PERIOD_MS, reason);
        flags.onGetFlagThrows(KEY_FLEDGE_BACKGROUND_FETCH_JOB_FLEX_MS, reason);
        flags.setGlobalKillSwitch(false);
        // By default, do not use SPE.
        flags.setFlag(KEY_SPE_ON_BACKGROUND_FETCH_JOB_ENABLED, false);
    }

    private void setFlagsWithKillSwitchOn() {
        flags.setFlag(KEY_FLEDGE_CUSTOM_AUDIENCE_SERVICE_KILL_SWITCH, true);
        // For testing the corner case where the BgF is enabled but overall Custom Audience Service
        // kill switch is on
        flags.setFlag(KEY_FLEDGE_BACKGROUND_FETCH_ENABLED, true);
        flags.setGlobalKillSwitch(false);
        // By default, do not use SPE.
        flags.setFlag(KEY_SPE_ON_BACKGROUND_FETCH_JOB_ENABLED, false);
    }

    private void setFlagsWithKillSwitchOff(boolean speOnBackgroundFetchJobEnabled) {
        flags.setFlag(KEY_FLEDGE_CUSTOM_AUDIENCE_SERVICE_KILL_SWITCH, false);
        flags.setFlag(KEY_FLEDGE_BACKGROUND_FETCH_ENABLED, true);
        flags.setGlobalKillSwitch(false);
        flags.setFlag(KEY_SPE_ON_BACKGROUND_FETCH_JOB_ENABLED, speOnBackgroundFetchJobEnabled);
    }

    private void setFlagsWithKillSwitchOff() {
        // By default, do not use SPE.
        setFlagsWithKillSwitchOff(/* speOnBackgroundFetchJobEnabled= */ false);
    }

    private void setFlagsWithKillSwitchOffSpeEnabled() {
        setFlagsWithKillSwitchOff(/* speOnBackgroundFetchJobEnabled= */ true);
    }
}

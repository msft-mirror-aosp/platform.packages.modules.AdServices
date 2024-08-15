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

package com.android.adservices.service.adselection;

import static com.android.adservices.spe.AdServicesJobInfo.FLEDGE_AD_SELECTION_DEBUG_REPORT_SENDER_JOB;
import static com.android.dx.mockito.inline.extended.ExtendedMockito.doAnswer;
import static com.android.dx.mockito.inline.extended.ExtendedMockito.doCallRealMethod;
import static com.android.dx.mockito.inline.extended.ExtendedMockito.doNothing;
import static com.android.dx.mockito.inline.extended.ExtendedMockito.doReturn;
import static com.android.dx.mockito.inline.extended.ExtendedMockito.staticMockMarker;

import static com.google.common.util.concurrent.Futures.immediateFailedFuture;
import static com.google.common.util.concurrent.Futures.immediateFuture;

import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertNotNull;
import static org.junit.Assert.assertNull;
import static org.junit.Assert.assertTrue;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoMoreInteractions;

import android.app.job.JobInfo;
import android.app.job.JobParameters;
import android.app.job.JobScheduler;
import android.content.ComponentName;
import android.content.Context;

import com.android.adservices.common.AdServicesJobServiceTestCase;
import com.android.adservices.service.Flags;
import com.android.adservices.service.FlagsFactory;
import com.android.adservices.service.common.compat.ServiceCompatUtils;
import com.android.adservices.service.consent.AdServicesApiConsent;
import com.android.adservices.service.consent.AdServicesApiType;
import com.android.adservices.service.consent.ConsentManager;
import com.android.adservices.shared.testing.JobServiceLoggingCallback;
import com.android.adservices.shared.testing.annotations.RequiresSdkLevelAtLeastS;
import com.android.adservices.spe.AdServicesJobServiceLogger;
import com.android.dx.mockito.inline.extended.ExtendedMockito;
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

@RequiresSdkLevelAtLeastS()
@SpyStatic(FlagsFactory.class)
@MockStatic(ConsentManager.class)
@SpyStatic(DebugReportSenderJobService.class)
@SpyStatic(DebugReportSenderWorker.class)
@SpyStatic(AdServicesJobServiceLogger.class)
@MockStatic(ServiceCompatUtils.class)
public final class DebugReportSenderJobServiceTest extends AdServicesJobServiceTestCase {

    private static final int FLEDGE_DEBUG_REPORT_SENDER_JOB_ID =
            FLEDGE_AD_SELECTION_DEBUG_REPORT_SENDER_JOB.getJobId();
    private static final JobScheduler JOB_SCHEDULER = sContext.getSystemService(JobScheduler.class);
    // Set a minimum delay of 1 hour so scheduled jobs don't run immediately
    private static final long MINIMUM_SCHEDULING_DELAY_MS = 60L * 60L * 1000L;

    @Spy
    private final DebugReportSenderJobService mDebugReportSenderJobService =
            new DebugReportSenderJobService();

    private final Flags mFlagsWithAdSelectionDisabled =
            new DebugReportSenderJobServiceTestFlags.FlagsWithAdSelectionDisabled();
    private final Flags mFlagsWithDebugReportingDisabled =
            new DebugReportSenderJobServiceTestFlags.FlagsWithDebugReportingDisabled();
    private final Flags mFlagsWithGaUxDisabled =
            new DebugReportSenderJobServiceTestFlags.FlagsWithGaUxDisabled();
    @Mock private ConsentManager mConsentManagerMock;
    @Mock private DebugReportSenderWorker mDebugReportSenderWorker;
    @Mock private JobParameters mJobParametersMock;

    @Before
    public void setup() {
        Assume.assumeNotNull(JOB_SCHEDULER);
        assertNull(
                "Job already scheduled before setup!",
                JOB_SCHEDULER.getPendingJob(FLEDGE_DEBUG_REPORT_SENDER_JOB_ID));
    }

    @After
    public void teardown() {
        JOB_SCHEDULER.cancelAll();
    }

    @Test
    public void testOnStartJobFlagDisabledWithoutLogging() {
        Flags mFlagsWithDisabledBgFWithoutLogging =
                new DebugReportSenderJobServiceTestFlags.FlagsWithAdSelectionDisabled() {
                    @Override
                    public boolean getBackgroundJobsLoggingKillSwitch() {
                        return true;
                    }
                };
        mocker.mockGetFlags(mFlagsWithDisabledBgFWithoutLogging);
        AdServicesJobServiceLogger logger =
                mockAdServicesJobServiceLogger(sContext, mFlagsWithDisabledBgFWithoutLogging);

        testOnStartJobFlagDisabled();

        verifyLoggingNotHappened(logger);
    }

    @Test
    public void testOnStartJobFlagDisabledWithLogging() throws Exception {
        Flags mFlagsWithDisabledBgFWithLogging =
                new DebugReportSenderJobServiceTestFlags.FlagsWithAdSelectionDisabled() {
                    @Override
                    public boolean getBackgroundJobsLoggingKillSwitch() {
                        return false;
                    }
                };
        mocker.mockGetFlags(mFlagsWithDisabledBgFWithLogging);
        AdServicesJobServiceLogger logger =
                mockAdServicesJobServiceLogger(sContext, mFlagsWithDisabledBgFWithLogging);
        JobServiceLoggingCallback callback = syncLogExecutionStats(logger);

        testOnStartJobFlagDisabled();

        verifyBackgroundJobsSkipLogged(logger, callback);
    }

    @Test
    public void testOnStartJobAdSelectionKillSwitchFlagEnabled() {
        mocker.mockGetFlags(mFlagsWithAdSelectionDisabled);
        doReturn(JOB_SCHEDULER)
                .when(mDebugReportSenderJobService)
                .getSystemService(JobScheduler.class);
        doNothing().when(mDebugReportSenderJobService).jobFinished(mJobParametersMock, false);

        // Schedule the job to assert after starting that the scheduled job has been cancelled
        JobInfo existingJobInfo =
                new JobInfo.Builder(
                                FLEDGE_DEBUG_REPORT_SENDER_JOB_ID,
                                new ComponentName(sContext, DebugReportSenderJobService.class))
                        .setMinimumLatency(MINIMUM_SCHEDULING_DELAY_MS)
                        .build();
        JOB_SCHEDULER.schedule(existingJobInfo);
        assertNotNull(JOB_SCHEDULER.getPendingJob(FLEDGE_DEBUG_REPORT_SENDER_JOB_ID));

        assertFalse(mDebugReportSenderJobService.onStartJob(mJobParametersMock));

        assertNull(JOB_SCHEDULER.getPendingJob(FLEDGE_DEBUG_REPORT_SENDER_JOB_ID));
        verify(mDebugReportSenderWorker, never()).runDebugReportSender();
        verify(mDebugReportSenderJobService).jobFinished(mJobParametersMock, false);
        verifyNoMoreInteractions(staticMockMarker(DebugReportSenderWorker.class));
    }

    @Test
    public void testOnStartJobEnableDebugReportingFlagDisabled() {
        doReturn(mFlagsWithDebugReportingDisabled).when(FlagsFactory::getFlags);
        doReturn(JOB_SCHEDULER)
                .when(mDebugReportSenderJobService)
                .getSystemService(JobScheduler.class);
        doNothing().when(mDebugReportSenderJobService).jobFinished(mJobParametersMock, false);

        // Schedule the job to assert after starting that the scheduled job has been cancelled
        JobInfo existingJobInfo =
                new JobInfo.Builder(
                                FLEDGE_DEBUG_REPORT_SENDER_JOB_ID,
                                new ComponentName(sContext, DebugReportSenderJobService.class))
                        .setMinimumLatency(MINIMUM_SCHEDULING_DELAY_MS)
                        .build();
        JOB_SCHEDULER.schedule(existingJobInfo);
        assertNotNull(JOB_SCHEDULER.getPendingJob(FLEDGE_DEBUG_REPORT_SENDER_JOB_ID));

        assertFalse(mDebugReportSenderJobService.onStartJob(mJobParametersMock));

        assertNull(JOB_SCHEDULER.getPendingJob(FLEDGE_DEBUG_REPORT_SENDER_JOB_ID));
        verify(mDebugReportSenderWorker, never()).runDebugReportSender();
        verify(mDebugReportSenderJobService).jobFinished(mJobParametersMock, false);
        verifyNoMoreInteractions(staticMockMarker(DebugReportSenderWorker.class));
    }

    @Test
    public void testOnStartJobGaUxFlagDisabled() {
        doReturn(mFlagsWithGaUxDisabled).when(FlagsFactory::getFlags);
        doReturn(mConsentManagerMock).when(() -> ConsentManager.getInstance());
        doReturn(AdServicesApiConsent.REVOKED)
                .when(mConsentManagerMock)
                .getConsent(AdServicesApiType.FLEDGE);
        doReturn(JOB_SCHEDULER)
                .when(mDebugReportSenderJobService)
                .getSystemService(JobScheduler.class);
        doNothing().when(mDebugReportSenderJobService).jobFinished(mJobParametersMock, false);

        // Schedule the job to assert after starting that the scheduled job has been cancelled
        JobInfo existingJobInfo =
                new JobInfo.Builder(
                                FLEDGE_DEBUG_REPORT_SENDER_JOB_ID,
                                new ComponentName(sContext, DebugReportSenderJobService.class))
                        .setMinimumLatency(MINIMUM_SCHEDULING_DELAY_MS)
                        .build();
        JOB_SCHEDULER.schedule(existingJobInfo);
        assertNotNull(JOB_SCHEDULER.getPendingJob(FLEDGE_DEBUG_REPORT_SENDER_JOB_ID));

        assertFalse(mDebugReportSenderJobService.onStartJob(mJobParametersMock));

        assertNull(JOB_SCHEDULER.getPendingJob(FLEDGE_DEBUG_REPORT_SENDER_JOB_ID));
        verify(mDebugReportSenderWorker, never()).runDebugReportSender();
        verify(mDebugReportSenderJobService).jobFinished(mJobParametersMock, false);
        verifyNoMoreInteractions(staticMockMarker(DebugReportSenderWorker.class));
    }

    @Test
    public void testOnStartJobConsentRevokedGaUxDisabled() {
        doReturn(mFlagsWithGaUxDisabled).when(FlagsFactory::getFlags);
        doReturn(mConsentManagerMock).when(() -> ConsentManager.getInstance());
        doReturn(AdServicesApiConsent.REVOKED)
                .when(mConsentManagerMock)
                .getConsent(AdServicesApiType.FLEDGE);
        doReturn(JOB_SCHEDULER)
                .when(mDebugReportSenderJobService)
                .getSystemService(JobScheduler.class);
        doNothing().when(mDebugReportSenderJobService).jobFinished(mJobParametersMock, false);

        // Schedule the job to assert after starting that the scheduled job has been cancelled
        JobInfo existingJobInfo =
                new JobInfo.Builder(
                                FLEDGE_DEBUG_REPORT_SENDER_JOB_ID,
                                new ComponentName(sContext, DebugReportSenderJobService.class))
                        .setMinimumLatency(MINIMUM_SCHEDULING_DELAY_MS)
                        .build();
        JOB_SCHEDULER.schedule(existingJobInfo);
        assertNotNull(JOB_SCHEDULER.getPendingJob(FLEDGE_DEBUG_REPORT_SENDER_JOB_ID));

        assertFalse(mDebugReportSenderJobService.onStartJob(mJobParametersMock));

        assertNull(JOB_SCHEDULER.getPendingJob(FLEDGE_DEBUG_REPORT_SENDER_JOB_ID));
        verify(mDebugReportSenderWorker, never()).runDebugReportSender();
        verify(mDebugReportSenderJobService).jobFinished(mJobParametersMock, false);
        verifyNoMoreInteractions(staticMockMarker(DebugReportSenderWorker.class));
    }

    @Test
    public void testScheduleIfNeededFlagDisabled() {
        doCallRealMethod()
                .when(() -> DebugReportSenderJobService.scheduleIfNeeded(any(), eq(false)));
        doReturn(mFlagsWithDebugReportingDisabled).when(FlagsFactory::getFlags);
        DebugReportSenderJobService.scheduleIfNeeded(sContext, false);

        ExtendedMockito.verify(() -> DebugReportSenderJobService.schedule(any(), any()), never());
        verifyNoMoreInteractions(staticMockMarker(DebugReportSenderWorker.class));
    }

    @Test
    public void testScheduleIfNeededSuccess() {
        doCallRealMethod()
                .when(() -> DebugReportSenderJobService.scheduleIfNeeded(any(), eq(false)));
        doReturn(mFlagsWithGaUxDisabled).when(FlagsFactory::getFlags);
        doNothing().when(() -> DebugReportSenderJobService.schedule(any(), any()));

        DebugReportSenderJobService.scheduleIfNeeded(sContext, false);

        ExtendedMockito.verify(() -> DebugReportSenderJobService.schedule(any(), any()));
        verifyNoMoreInteractions(staticMockMarker(DebugReportSenderWorker.class));
    }

    @Test
    public void testScheduleIfNeededSkippedAlreadyScheduled() {
        doReturn(mFlagsWithGaUxDisabled).when(FlagsFactory::getFlags);
        JobInfo existingJobInfo =
                new JobInfo.Builder(
                                FLEDGE_DEBUG_REPORT_SENDER_JOB_ID,
                                new ComponentName(sContext, DebugReportSenderJobService.class))
                        .setMinimumLatency(MINIMUM_SCHEDULING_DELAY_MS)
                        .build();
        JOB_SCHEDULER.schedule(existingJobInfo);
        assertNotNull(JOB_SCHEDULER.getPendingJob(FLEDGE_DEBUG_REPORT_SENDER_JOB_ID));

        doCallRealMethod()
                .when(() -> DebugReportSenderJobService.scheduleIfNeeded(any(), eq(false)));

        DebugReportSenderJobService.scheduleIfNeeded(sContext, false);

        ExtendedMockito.verify(() -> DebugReportSenderJobService.schedule(any(), any()), never());
        verifyNoMoreInteractions(staticMockMarker(DebugReportSenderWorker.class));
    }

    @Test
    public void testScheduleIfNeededForceSuccess() {
        doReturn(mFlagsWithGaUxDisabled).when(FlagsFactory::getFlags);
        JobInfo existingJobInfo =
                new JobInfo.Builder(
                                FLEDGE_DEBUG_REPORT_SENDER_JOB_ID,
                                new ComponentName(sContext, DebugReportSenderJobService.class))
                        .setMinimumLatency(MINIMUM_SCHEDULING_DELAY_MS)
                        .build();
        JOB_SCHEDULER.schedule(existingJobInfo);
        assertNotNull(JOB_SCHEDULER.getPendingJob(FLEDGE_DEBUG_REPORT_SENDER_JOB_ID));

        doCallRealMethod()
                .when(() -> DebugReportSenderJobService.scheduleIfNeeded(any(), eq(true)));
        doNothing().when(() -> DebugReportSenderJobService.schedule(any(), any()));

        DebugReportSenderJobService.scheduleIfNeeded(sContext, true);

        ExtendedMockito.verify(() -> DebugReportSenderJobService.schedule(any(), any()));
        verifyNoMoreInteractions(staticMockMarker(DebugReportSenderWorker.class));
    }

    @Test
    public void testScheduleFlagDisabled() {
        doReturn(mFlagsWithDebugReportingDisabled).when(FlagsFactory::getFlags);
        DebugReportSenderJobService.schedule(sContext, mFlagsWithDebugReportingDisabled);

        verifyNoMoreInteractions(staticMockMarker(DebugReportSenderWorker.class));
    }

    @Test
    public void testOnStartJob_shouldDisableJobTrueWithoutLogging() {
        Flags mockFlag = mock(Flags.class);
        mocker.mockGetFlags(mockFlag);
        mockBackgroundJobsLoggingKillSwitch(mockFlag, /* overrideValue= */ true);
        AdServicesJobServiceLogger logger = mockAdServicesJobServiceLogger(sContext, mockFlag);

        testOnStartJobShouldDisableJobTrue();

        verifyLoggingNotHappened(logger);
    }

    @Test
    public void testOnStartJobUpdateTimeoutHandledWithoutLogging() throws Exception {
        Flags flagsWithGaUxDisabledLoggingDisabled =
                new DebugReportSenderJobServiceTestFlags.FlagsWithGaUxDisabledLoggingDisabled();

        doReturn(flagsWithGaUxDisabledLoggingDisabled).when(FlagsFactory::getFlags);
        AdServicesJobServiceLogger logger =
                mockAdServicesJobServiceLogger(sContext, flagsWithGaUxDisabledLoggingDisabled);

        testOnStartJobUpdateTimeoutHandled();

        verifyLoggingNotHappened(logger);
    }

    @Test
    public void testOnStartJobUpdateTimeoutHandledWithLogging() throws Exception {
        Flags flagsWithGaUxDisabledLoggingEnabled =
                new DebugReportSenderJobServiceTestFlags.FlagsWithGaUxDisabledLoggingEnabled();
        doReturn(flagsWithGaUxDisabledLoggingEnabled).when(FlagsFactory::getFlags);
        AdServicesJobServiceLogger logger =
                mockAdServicesJobServiceLogger(sContext, flagsWithGaUxDisabledLoggingEnabled);
        JobServiceLoggingCallback onStartJobCallback = syncPersistJobExecutionData(logger);
        JobServiceLoggingCallback onJobDoneCallback = syncLogExecutionStats(logger);

        testOnStartJobUpdateTimeoutHandled();

        verifyOnStartJobLogged(logger, onStartJobCallback);
        verifyOnJobFinishedLogged(logger, onJobDoneCallback);
    }

    @Test
    public void testOnStartJobUpdateInterruptedHandled() throws Exception {
        CountDownLatch jobFinishedCountDown = new CountDownLatch(1);

        doReturn(mFlagsWithGaUxDisabled).when(FlagsFactory::getFlags);
        doReturn(mConsentManagerMock).when(() -> ConsentManager.getInstance());
        doReturn(AdServicesApiConsent.GIVEN)
                .when(mConsentManagerMock)
                .getConsent(AdServicesApiType.FLEDGE);
        doReturn(mDebugReportSenderWorker).when(DebugReportSenderWorker::getInstance);
        doReturn(
                        FluentFuture.from(
                                immediateFailedFuture(new InterruptedException("testing timeout"))))
                .when(mDebugReportSenderWorker)
                .runDebugReportSender();
        doAnswer(
                        unusedInvocation -> {
                            jobFinishedCountDown.countDown();
                            return null;
                        })
                .when(mDebugReportSenderJobService)
                .jobFinished(mJobParametersMock, false);

        assertTrue(mDebugReportSenderJobService.onStartJob(mJobParametersMock));
        jobFinishedCountDown.await();

        ExtendedMockito.verify(DebugReportSenderWorker::getInstance);
        verify(mDebugReportSenderWorker).runDebugReportSender();
        verify(mDebugReportSenderJobService).jobFinished(mJobParametersMock, false);
        verifyNoMoreInteractions(staticMockMarker(DebugReportSenderWorker.class));
    }

    @Test
    public void testOnStartJobUpdateExecutionExceptionHandled() throws Exception {
        CountDownLatch jobFinishedCountDown = new CountDownLatch(1);

        doReturn(mFlagsWithGaUxDisabled).when(FlagsFactory::getFlags);
        doReturn(mConsentManagerMock).when(() -> ConsentManager.getInstance());
        doReturn(AdServicesApiConsent.GIVEN)
                .when(mConsentManagerMock)
                .getConsent(AdServicesApiType.FLEDGE);
        doReturn(mDebugReportSenderWorker).when(DebugReportSenderWorker::getInstance);
        doReturn(
                        FluentFuture.from(
                                immediateFailedFuture(
                                        new ExecutionException("testing timeout", null))))
                .when(mDebugReportSenderWorker)
                .runDebugReportSender();
        doAnswer(
                        unusedInvocation -> {
                            jobFinishedCountDown.countDown();
                            return null;
                        })
                .when(mDebugReportSenderJobService)
                .jobFinished(mJobParametersMock, false);

        assertTrue(mDebugReportSenderJobService.onStartJob(mJobParametersMock));
        jobFinishedCountDown.await();

        ExtendedMockito.verify(DebugReportSenderWorker::getInstance);
        verify(mDebugReportSenderWorker).runDebugReportSender();
        verify(mDebugReportSenderJobService).jobFinished(mJobParametersMock, false);
        verifyNoMoreInteractions(staticMockMarker(DebugReportSenderWorker.class));
    }

    @Test
    public void testOnStartJob_shouldDisableJobTrueWithLoggingEnabled() {
        Flags mockFlag = mock(Flags.class);
        mocker.mockGetFlags(mockFlag);
        mockBackgroundJobsLoggingKillSwitch(mockFlag, /* overrideValue= */ true);
        AdServicesJobServiceLogger logger = mockAdServicesJobServiceLogger(sContext, mockFlag);

        testOnStartJobShouldDisableJobTrue();

        // Verify logging has not happened even though logging is enabled because this field is not
        // logged
        verifyLoggingNotHappened(logger);
    }

    @Test
    public void testOnStopJobCallsStopWorkWithoutLogging() {
        Flags flagsWithGaUxDisabledLoggingDisabled =
                new DebugReportSenderJobServiceTestFlags.FlagsWithGaUxDisabledLoggingDisabled();
        doReturn(flagsWithGaUxDisabledLoggingDisabled).when(FlagsFactory::getFlags);
        AdServicesJobServiceLogger logger =
                mockAdServicesJobServiceLogger(sContext, flagsWithGaUxDisabledLoggingDisabled);

        testOnStopJobCallsStopWork();

        verifyLoggingNotHappened(logger);
    }

    @Test
    public void testOnStopJobWithLogging() throws Exception {
        Flags mockFlag =
                new DebugReportSenderJobServiceTestFlags.FlagsWithGaUxDisabledLoggingEnabled();
        mocker.mockGetFlags(mockFlag);
        AdServicesJobServiceLogger logger = mockAdServicesJobServiceLogger(sContext, mockFlag);
        JobServiceLoggingCallback callback = syncLogExecutionStats(logger);

        testOnStopJobCallsStopWork();

        verifyOnStopJobLogged(logger, callback);
    }

    @Test
    public void testOnStartJobConsentRevokedGaUxEnabledWithoutLogging() {
        Flags flags =
                new DebugReportSenderJobServiceTestFlags.FlagsWithGaUxEnabledLoggingDisabled();
        mocker.mockGetFlags(flags);
        AdServicesJobServiceLogger logger = mockAdServicesJobServiceLogger(sContext, flags);

        testOnStartJobConsentRevokedGaUxEnabled();

        verifyLoggingNotHappened(logger);
    }

    @Test
    public void testOnStartJobConsentRevokedGaUxEnabledWithLogging() throws Exception {
        Flags flags = new DebugReportSenderJobServiceTestFlags.FlagsWithGaUxEnabledLoggingEnabled();
        mocker.mockGetFlags(flags);
        AdServicesJobServiceLogger logger = mockAdServicesJobServiceLogger(sContext, flags);
        JobServiceLoggingCallback onStartJobCallback = syncPersistJobExecutionData(logger);
        JobServiceLoggingCallback onJobDoneCallback = syncLogExecutionStats(logger);

        testOnStartJobConsentRevokedGaUxEnabled();

        // Verify logging has happened
        verifyOnStartJobLogged(logger, onStartJobCallback);
        verifyBackgroundJobsSkipLogged(logger, onJobDoneCallback);
    }

    @Test
    public void testOnStartJobUpdateSuccessWithoutLogging() throws Exception {
        Flags flagsWithGaUxDisabledLoggingDisabled =
                new DebugReportSenderJobServiceTestFlags.FlagsWithGaUxDisabledLoggingDisabled();
        doReturn(flagsWithGaUxDisabledLoggingDisabled).when(FlagsFactory::getFlags);
        AdServicesJobServiceLogger logger =
                mockAdServicesJobServiceLogger(sContext, flagsWithGaUxDisabledLoggingDisabled);

        testOnStartJobUpdateSuccess();

        verifyLoggingNotHappened(logger);
    }

    @Test
    public void testOnStartJobUpdateSuccessWithLogging() throws Exception {
        Flags flagsWithGaUxDisabledLoggingEnabled =
                new DebugReportSenderJobServiceTestFlags.FlagsWithGaUxDisabledLoggingEnabled();
        doReturn(flagsWithGaUxDisabledLoggingEnabled).when(FlagsFactory::getFlags);
        AdServicesJobServiceLogger logger =
                mockAdServicesJobServiceLogger(sContext, flagsWithGaUxDisabledLoggingEnabled);
        JobServiceLoggingCallback onStartJobCallback = syncPersistJobExecutionData(logger);
        JobServiceLoggingCallback onJobDoneCallback = syncLogExecutionStats(logger);

        testOnStartJobUpdateSuccess();

        verifyJobFinishedLogged(logger, onStartJobCallback, onJobDoneCallback);
    }

    private void testOnStartJobUpdateSuccess() throws Exception {
        CountDownLatch jobFinishedCountDown = new CountDownLatch(1);

        doReturn(mConsentManagerMock).when(() -> ConsentManager.getInstance());
        doReturn(AdServicesApiConsent.GIVEN)
                .when(mConsentManagerMock)
                .getConsent(AdServicesApiType.FLEDGE);
        doReturn(mDebugReportSenderWorker).when(DebugReportSenderWorker::getInstance);
        doReturn(FluentFuture.from(immediateFuture(null)))
                .when(mDebugReportSenderWorker)
                .runDebugReportSender();
        doAnswer(
                        unusedInvocation -> {
                            jobFinishedCountDown.countDown();
                            return null;
                        })
                .when(mDebugReportSenderJobService)
                .jobFinished(mJobParametersMock, false);

        assertTrue(mDebugReportSenderJobService.onStartJob(mJobParametersMock));
        jobFinishedCountDown.await();

        ExtendedMockito.verify(DebugReportSenderWorker::getInstance);
        verify(mDebugReportSenderWorker).runDebugReportSender();
        verify(mDebugReportSenderJobService).jobFinished(mJobParametersMock, false);
        verifyNoMoreInteractions(staticMockMarker(DebugReportSenderWorker.class));
    }

    private void testOnStartJobConsentRevokedGaUxEnabled() {
        doReturn(mConsentManagerMock).when(() -> ConsentManager.getInstance());
        doReturn(AdServicesApiConsent.REVOKED)
                .when(mConsentManagerMock)
                .getConsent(AdServicesApiType.FLEDGE);
        doReturn(JOB_SCHEDULER)
                .when(mDebugReportSenderJobService)
                .getSystemService(JobScheduler.class);
        doNothing().when(mDebugReportSenderJobService).jobFinished(mJobParametersMock, false);

        // Schedule the job to assert after starting that the scheduled job has been cancelled
        JobInfo existingJobInfo =
                new JobInfo.Builder(
                                FLEDGE_DEBUG_REPORT_SENDER_JOB_ID,
                                new ComponentName(sContext, DebugReportSenderJobService.class))
                        .setMinimumLatency(MINIMUM_SCHEDULING_DELAY_MS)
                        .build();
        JOB_SCHEDULER.schedule(existingJobInfo);
        assertNotNull(JOB_SCHEDULER.getPendingJob(FLEDGE_DEBUG_REPORT_SENDER_JOB_ID));

        assertFalse(mDebugReportSenderJobService.onStartJob(mJobParametersMock));

        assertNull(JOB_SCHEDULER.getPendingJob(FLEDGE_DEBUG_REPORT_SENDER_JOB_ID));
        verify(mDebugReportSenderWorker, never()).runDebugReportSender();
        verify(mDebugReportSenderJobService).jobFinished(mJobParametersMock, false);
        verifyNoMoreInteractions(staticMockMarker(DebugReportSenderWorker.class));
    }

    private void testOnStartJobUpdateTimeoutHandled() throws Exception {
        CountDownLatch jobFinishedCountDown = new CountDownLatch(1);

        doReturn(mConsentManagerMock).when(() -> ConsentManager.getInstance());
        doReturn(AdServicesApiConsent.GIVEN)
                .when(mConsentManagerMock)
                .getConsent(AdServicesApiType.FLEDGE);
        doReturn(mDebugReportSenderWorker).when(DebugReportSenderWorker::getInstance);
        doReturn(FluentFuture.from(immediateFailedFuture(new TimeoutException("testing timeout"))))
                .when(mDebugReportSenderWorker)
                .runDebugReportSender();
        doAnswer(
                        unusedInvocation -> {
                            jobFinishedCountDown.countDown();
                            return null;
                        })
                .when(mDebugReportSenderJobService)
                .jobFinished(mJobParametersMock, false);

        assertTrue(mDebugReportSenderJobService.onStartJob(mJobParametersMock));
        jobFinishedCountDown.await();

        ExtendedMockito.verify(DebugReportSenderWorker::getInstance);
        verify(mDebugReportSenderWorker).runDebugReportSender();
        verify(mDebugReportSenderJobService).jobFinished(mJobParametersMock, false);
        verifyNoMoreInteractions(staticMockMarker(DebugReportSenderWorker.class));
    }

    private void testOnStopJobCallsStopWork() {
        doReturn(mDebugReportSenderWorker).when(DebugReportSenderWorker::getInstance);
        doNothing().when(mDebugReportSenderWorker).stopWork();

        assertTrue(mDebugReportSenderJobService.onStopJob(mJobParametersMock));

        verify(mDebugReportSenderWorker).stopWork();
    }

    private void testOnStartJobShouldDisableJobTrue() {
        doReturn(true)
                .when(
                        () ->
                                ServiceCompatUtils.shouldDisableExtServicesJobOnTPlus(
                                        any(Context.class)));
        doReturn(JOB_SCHEDULER)
                .when(mDebugReportSenderJobService)
                .getSystemService(JobScheduler.class);
        doNothing().when(mDebugReportSenderJobService).jobFinished(mJobParametersMock, false);

        // Schedule the job to assert after starting that the scheduled job has been cancelled
        JobInfo existingJobInfo =
                new JobInfo.Builder(
                                FLEDGE_DEBUG_REPORT_SENDER_JOB_ID,
                                new ComponentName(sContext, DebugReportSenderJobService.class))
                        .setMinimumLatency(MINIMUM_SCHEDULING_DELAY_MS)
                        .build();
        JOB_SCHEDULER.schedule(existingJobInfo);
        assertNotNull(JOB_SCHEDULER.getPendingJob(FLEDGE_DEBUG_REPORT_SENDER_JOB_ID));

        assertFalse(mDebugReportSenderJobService.onStartJob(mJobParametersMock));

        assertNull(JOB_SCHEDULER.getPendingJob(FLEDGE_DEBUG_REPORT_SENDER_JOB_ID));
        verify(mDebugReportSenderWorker, never()).runDebugReportSender();
        verify(mDebugReportSenderJobService).jobFinished(mJobParametersMock, false);
        verifyNoMoreInteractions(staticMockMarker(DebugReportSenderWorker.class));
    }

    private void testOnStartJobFlagDisabled() {
        doReturn(JOB_SCHEDULER)
                .when(mDebugReportSenderJobService)
                .getSystemService(JobScheduler.class);
        doNothing().when(mDebugReportSenderJobService).jobFinished(mJobParametersMock, false);

        // Schedule the job to assert after starting that the scheduled job has been cancelled
        JobInfo existingJobInfo =
                new JobInfo.Builder(
                                FLEDGE_DEBUG_REPORT_SENDER_JOB_ID,
                                new ComponentName(sContext, DebugReportSenderJobService.class))
                        .setMinimumLatency(MINIMUM_SCHEDULING_DELAY_MS)
                        .build();
        JOB_SCHEDULER.schedule(existingJobInfo);
        assertNotNull(JOB_SCHEDULER.getPendingJob(FLEDGE_DEBUG_REPORT_SENDER_JOB_ID));

        assertFalse(mDebugReportSenderJobService.onStartJob(mJobParametersMock));

        assertNull(JOB_SCHEDULER.getPendingJob(FLEDGE_DEBUG_REPORT_SENDER_JOB_ID));
        verify(mDebugReportSenderWorker, never()).runDebugReportSender();
        verify(mDebugReportSenderJobService).jobFinished(mJobParametersMock, false);
        verifyNoMoreInteractions(staticMockMarker(DebugReportSenderWorker.class));
    }

    private static class DebugReportSenderJobServiceTestFlags {
        private static class FlagsWithAdSelectionDisabled implements Flags {
            @Override
            public boolean getFledgeSelectAdsKillSwitch() {
                return true;
            }
        }

        private static class FlagsWithDebugReportingDisabled implements Flags {
            @Override
            public boolean getFledgeSelectAdsKillSwitch() {
                return false;
            }

            @Override
            public boolean getFledgeEventLevelDebugReportingEnabled() {
                return false;
            }
        }

        private static class FlagsWithGaUxDisabled implements Flags {
            @Override
            public boolean getFledgeSelectAdsKillSwitch() {
                return false;
            }

            @Override
            public boolean getFledgeEventLevelDebugReportingEnabled() {
                return true;
            }

            @Override
            public boolean getGaUxFeatureEnabled() {
                return false;
            }
        }

        private static class FlagsWithGaUxDisabledLoggingDisabled extends FlagsWithGaUxDisabled {
            @Override
            public boolean getBackgroundJobsLoggingKillSwitch() {
                return true;
            }
        }

        private static class FlagsWithGaUxDisabledLoggingEnabled extends FlagsWithGaUxDisabled {
            @Override
            public boolean getBackgroundJobsLoggingKillSwitch() {
                return false;
            }
        }

        private static class FlagsWithGaUxEnabled implements Flags {
            @Override
            public boolean getFledgeSelectAdsKillSwitch() {
                return false;
            }

            @Override
            public boolean getFledgeEventLevelDebugReportingEnabled() {
                return true;
            }

            @Override
            public boolean getGaUxFeatureEnabled() {
                return true;
            }
        }

        private static class FlagsWithGaUxEnabledLoggingDisabled extends FlagsWithGaUxEnabled {
            @Override
            public boolean getBackgroundJobsLoggingKillSwitch() {
                return true;
            }
        }

        private static class FlagsWithGaUxEnabledLoggingEnabled extends FlagsWithGaUxEnabled {
            @Override
            public boolean getBackgroundJobsLoggingKillSwitch() {
                return false;
            }
        }
    }
}

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

package com.android.adservices.service.signals;

import static com.android.adservices.spe.AdServicesJobInfo.PERIODIC_SIGNALS_ENCODING_JOB;
import static com.android.dx.mockito.inline.extended.ExtendedMockito.doCallRealMethod;
import static com.android.dx.mockito.inline.extended.ExtendedMockito.doNothing;
import static com.android.dx.mockito.inline.extended.ExtendedMockito.doReturn;
import static com.android.dx.mockito.inline.extended.ExtendedMockito.staticMockMarker;
import static com.android.dx.mockito.inline.extended.ExtendedMockito.verify;

import static com.google.common.truth.Truth.assertWithMessage;
import static com.google.common.util.concurrent.Futures.immediateFailedFuture;
import static com.google.common.util.concurrent.Futures.immediateFuture;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verifyNoMoreInteractions;
import static org.mockito.Mockito.when;

import android.app.job.JobInfo;
import android.app.job.JobParameters;
import android.app.job.JobScheduler;
import android.content.ComponentName;
import android.content.Context;
import android.util.Log;

import com.android.adservices.common.AdServicesJobServiceTestCase;
import com.android.adservices.service.FlagsFactory;
import com.android.adservices.service.common.compat.ServiceCompatUtils;
import com.android.adservices.service.consent.AdServicesApiConsent;
import com.android.adservices.service.consent.AdServicesApiType;
import com.android.adservices.service.consent.ConsentManager;
import com.android.adservices.service.stats.AdsRelevanceStatusUtils;
import com.android.adservices.shared.testing.HandlerIdleSyncCallback;
import com.android.adservices.shared.testing.JobServiceLoggingCallback;
import com.android.adservices.shared.testing.concurrency.JobServiceCallback;
import com.android.adservices.spe.AdServicesJobServiceLogger;
import com.android.modules.utils.testing.ExtendedMockitoRule.MockStatic;
import com.android.modules.utils.testing.ExtendedMockitoRule.SpyStatic;

import com.google.common.util.concurrent.FluentFuture;

import org.junit.After;
import org.junit.Before;
import org.junit.Test;
import org.mockito.Mock;
import org.mockito.Spy;

import java.util.concurrent.ExecutionException;
import java.util.concurrent.TimeoutException;

@SpyStatic(FlagsFactory.class)
@MockStatic(ConsentManager.class)
@SpyStatic(PeriodicEncodingJobService.class)
@SpyStatic(PeriodicEncodingJobWorker.class)
@SpyStatic(AdServicesJobServiceLogger.class)
@MockStatic(ServiceCompatUtils.class)
public final class PeriodicEncodingJobServiceTest extends AdServicesJobServiceTestCase {

    private static final int PROTECTED_SIGNALS_PERIODIC_ENCODING_JOB_ID =
            PERIODIC_SIGNALS_ENCODING_JOB.getJobId();

    // Set a minimum delay of 1 hour so scheduled jobs don't run immediately
    private static final long MINIMUM_SCHEDULING_DELAY_MS = 60L * 60L * 1000L;
    private static final long PERIOD = 42 * 60 * 1000;
    private static final JobScheduler JOB_SCHEDULER = sContext.getSystemService(JobScheduler.class);
    private static final int PAS_ENCODING_SOURCE_TYPE =
            AdsRelevanceStatusUtils.PAS_ENCODING_SOURCE_TYPE_ENCODING_JOB_SERVICE;

    @Spy
    private final PeriodicEncodingJobService mSpyEncodingJobService =
            new PeriodicEncodingJobService();

    @Mock private PeriodicEncodingJobWorker mMockPeriodicEncodingJobWorker;
    @Mock private JobParameters mMockJobParameters;
    @Mock private ConsentManager mMockConsentManager;

    @Before
    public void setup() {
        assertWithMessage("job_scheduler").that(JOB_SCHEDULER).isNotNull();
        assertNoPendingJob();
        mocker.mockGetFlags(mMockFlags);
        mockFledgeConsentIsGiven();
        doReturn(JOB_SCHEDULER).when(mSpyEncodingJobService).getSystemService(JobScheduler.class);
    }

    @After
    public void tearDown() throws Exception {
        HandlerIdleSyncCallback callback = new HandlerIdleSyncCallback();

        JOB_SCHEDULER.cancelAll();

        callback.assertIdle();
    }

    @Test
    public void testOnStartJobFlagDisabled_withLogging() throws Exception {
        mockDisableParentKillSwitches();
        mockGetProtectedSignalsPeriodicEncodingEnabled(false);

        AdServicesJobServiceLogger logger =
                mocker.mockNoOpAdServicesJobServiceLogger(mMockContext, mMockFlags);
        JobServiceLoggingCallback callback = syncLogExecutionStats(logger);

        testOnStartJobFlagDisabled();

        verifyBackgroundJobsSkipLogged(logger, callback);
    }

    @Test
    public void testOnStartJobConsentRevokedGaUxDisabled() {
        mockGetGaUxFeatureEnabled(false);
        mockFledgeConsentIsRevoked();
        doNothing().when(mSpyEncodingJobService).jobFinished(mMockJobParameters, false);

        // Schedule the job to assert after starting that the scheduled job has been cancelled
        JobInfo existingJobInfo =
                new JobInfo.Builder(
                                PROTECTED_SIGNALS_PERIODIC_ENCODING_JOB_ID,
                                new ComponentName(sContext, PeriodicEncodingJobService.class))
                        .setMinimumLatency(MINIMUM_SCHEDULING_DELAY_MS)
                        .build();
        scheduleJob(existingJobInfo);

        assertOnStartIgnored();
        assertNoPendingJob();
        verifyEncodeProtectedSignalsNeverCalled();
        verifyJobFinished();
        verifyNoMoreInteractions(staticMockMarker(PeriodicEncodingJobWorker.class));
    }

    @Test
    public void testOnStartJobConsentRevokedGaUxEnabled_withLogging() throws Exception {
        mockDisableRelevantKillSwitches();

        AdServicesJobServiceLogger logger =
                mocker.mockNoOpAdServicesJobServiceLogger(mContext, mMockFlags);
        JobServiceLoggingCallback callback = syncLogExecutionStats(logger);

        testOnStartJobConsentRevokedGaUxEnabled();

        // Verify logging has happened
        verifyBackgroundJobsSkipLogged(logger, callback);
    }

    @Test
    public void testOnStartJobProtectedSignalsKillSwitchOn() {
        mockGetProtectedSignalsPeriodicEncodingEnabled(false);
        doNothing().when(mSpyEncodingJobService).jobFinished(mMockJobParameters, false);

        // Schedule the job to assert after starting that the scheduled job has been cancelled
        JobInfo existingJobInfo =
                new JobInfo.Builder(
                                PROTECTED_SIGNALS_PERIODIC_ENCODING_JOB_ID,
                                new ComponentName(sContext, PeriodicEncodingJobService.class))
                        .setMinimumLatency(MINIMUM_SCHEDULING_DELAY_MS)
                        .build();
        scheduleJob(existingJobInfo);

        assertOnStartIgnored();
        assertNoPendingJob();
        verifyEncodeProtectedSignalsNeverCalled();
        verifyJobFinished();
        verifyNoMoreInteractions(staticMockMarker(PeriodicEncodingJobWorker.class));
    }

    @Test
    public void testOnStartJobProtectedSignalsKillSwitchOff() throws Exception {
        mockDisableRelevantKillSwitches();
        doReturn(mMockPeriodicEncodingJobWorker)
                .when(() -> PeriodicEncodingJobWorker.getInstance());
        doReturn(FluentFuture.from(immediateFuture(null)))
                .when(mMockPeriodicEncodingJobWorker)
                .encodeProtectedSignals(PAS_ENCODING_SOURCE_TYPE);
        JobServiceCallback callback =
                new JobServiceCallback().expectJobFinished(mSpyEncodingJobService);

        assertOnStartSucceeded();
        callback.assertJobFinished();

        verify(() -> PeriodicEncodingJobWorker.getInstance());
        verifyEncodeProtectedSignalsCalled();
        verifyJobFinished();
        verifyNoMoreInteractions(staticMockMarker(PeriodicEncodingJobWorker.class));
    }

    @Test
    public void testOnStartJobUpdateSuccess_withLogging() throws Exception {
        mockDisableRelevantKillSwitches();

        AdServicesJobServiceLogger logger =
                mocker.mockNoOpAdServicesJobServiceLogger(mContext, mMockFlags);
        JobServiceLoggingCallback onStartJobCallback = syncPersistJobExecutionData(logger);
        JobServiceLoggingCallback onJobDoneCallback = syncLogExecutionStats(logger);

        testOnStartJobUpdateSuccess();

        verifyJobFinishedLogged(logger, onStartJobCallback, onJobDoneCallback);
    }

    @Test
    public void testOnStartJobUpdateTimeoutHandled_withLogging() throws Exception {
        mockDisableRelevantKillSwitches();

        AdServicesJobServiceLogger logger =
                mocker.mockNoOpAdServicesJobServiceLogger(mContext, mMockFlags);
        JobServiceLoggingCallback onStartJobCallback = syncPersistJobExecutionData(logger);
        JobServiceLoggingCallback onJobDoneCallback = syncLogExecutionStats(logger);

        testOnStartJobUpdateTimeoutHandled();

        verifyJobFinishedLogged(logger, onStartJobCallback, onJobDoneCallback);
    }

    @Test
    public void testOnStartJobUpdateInterruptedHandled() throws Exception {
        JobServiceCallback callback =
                new JobServiceCallback().expectJobFinished(mSpyEncodingJobService);
        mockDisableRelevantKillSwitches();
        doReturn(mMockPeriodicEncodingJobWorker)
                .when(() -> PeriodicEncodingJobWorker.getInstance());
        doReturn(
                        FluentFuture.from(
                                immediateFailedFuture(new InterruptedException("testing timeout"))))
                .when(mMockPeriodicEncodingJobWorker)
                .encodeProtectedSignals(PAS_ENCODING_SOURCE_TYPE);

        assertOnStartSucceeded();
        callback.assertJobFinished();

        verify(() -> PeriodicEncodingJobWorker.getInstance());
        verifyEncodeProtectedSignalsCalled();
        verifyJobFinished();
        verifyNoMoreInteractions(staticMockMarker(PeriodicEncodingJobWorker.class));
    }

    @Test
    public void testOnStartJobUpdateExecutionExceptionHandled() throws Exception {
        JobServiceCallback callback =
                new JobServiceCallback().expectJobFinished(mSpyEncodingJobService);
        mockDisableRelevantKillSwitches();
        doReturn(mMockPeriodicEncodingJobWorker)
                .when(() -> PeriodicEncodingJobWorker.getInstance());

        doReturn(
                        FluentFuture.from(
                                immediateFailedFuture(
                                        new ExecutionException("testing timeout", null))))
                .when(mMockPeriodicEncodingJobWorker)
                .encodeProtectedSignals(PAS_ENCODING_SOURCE_TYPE);

        assertOnStartSucceeded();
        callback.assertJobFinished();

        verify(() -> PeriodicEncodingJobWorker.getInstance());
        verifyEncodeProtectedSignalsCalled();
        verifyJobFinished();
        verifyNoMoreInteractions(staticMockMarker(PeriodicEncodingJobWorker.class));
    }

    @Test
    public void testOnStopJob_withLogging() throws Exception {
        AdServicesJobServiceLogger logger =
                mocker.mockNoOpAdServicesJobServiceLogger(mContext, mMockFlags);
        JobServiceLoggingCallback callback = syncLogExecutionStats(logger);

        doReturn(mMockPeriodicEncodingJobWorker).when(PeriodicEncodingJobWorker::getInstance);
        doNothing().when(mMockPeriodicEncodingJobWorker).stopWork();
        assertOnStopSucceeded();
        verify(mMockPeriodicEncodingJobWorker).stopWork();
        verifyOnStopJobLogged(logger, callback);
    }

    @Test
    public void testScheduleIfNeededFlagDisabled() {
        mockDisableParentKillSwitches();
        mockGetProtectedSignalsPeriodicEncodingEnabled(false);
        doCallRealMethod()
                .when(() -> PeriodicEncodingJobService.scheduleIfNeeded(any(), any(), eq(false)));

        PeriodicEncodingJobService.scheduleIfNeeded(sContext, mMockFlags, false);

        verify(() -> PeriodicEncodingJobService.schedule(any(), any()), never());
        verifyNoMoreInteractions(staticMockMarker(PeriodicEncodingJobWorker.class));
    }

    @Test
    public void testScheduleIfNeededSuccess() {
        mockDisableRelevantKillSwitches();
        doCallRealMethod()
                .when(() -> PeriodicEncodingJobService.scheduleIfNeeded(any(), any(), eq(false)));
        doNothing().when(() -> PeriodicEncodingJobService.schedule(any(), any()));

        PeriodicEncodingJobService.scheduleIfNeeded(sContext, mMockFlags, false);

        verify(() -> PeriodicEncodingJobService.schedule(any(), any()));
        verifyNoMoreInteractions(staticMockMarker(PeriodicEncodingJobWorker.class));
    }

    @Test
    public void testScheduleIfNeeded_AlreadyScheduled_isNotRescheduled() {
        mockFlagsEnabledPeriodicEncoding();
        JobInfo existingJobInfo =
                new JobInfo.Builder(
                                PROTECTED_SIGNALS_PERIODIC_ENCODING_JOB_ID,
                                new ComponentName(sContext, PeriodicEncodingJobService.class))
                        .setPeriodic(PERIOD)
                        // ensure that the job does not run during the test
                        .setRequiresDeviceIdle(true)
                        .build();
        JobInfo job = scheduleJob(existingJobInfo);
        assertWithMessage("job.getIntervalMillis()")
                .that(job.getIntervalMillis())
                .isEqualTo(PERIOD);

        doCallRealMethod()
                .when(() -> PeriodicEncodingJobService.scheduleIfNeeded(any(), any(), eq(false)));

        PeriodicEncodingJobService.scheduleIfNeeded(sContext, mMockFlags, false);

        verify(() -> PeriodicEncodingJobService.schedule(any(), any()), never());
        verifyNoMoreInteractions(staticMockMarker(PeriodicEncodingJobWorker.class));
    }

    @Test
    public void testScheduleIfNeeded_AlreadyScheduledTimeoutChanged_isRescheduled() {
        mockDisableRelevantKillSwitches();
        mockGetProtectedSignalPeriodicEncodingJobPeriodMs(PERIOD);

        JobInfo existingJobInfo =
                new JobInfo.Builder(
                                PROTECTED_SIGNALS_PERIODIC_ENCODING_JOB_ID,
                                new ComponentName(sContext, PeriodicEncodingJobService.class))
                        .setPeriodic(30 * 60 * 1000)
                        // ensure that the job does not run during the test
                        .setRequiresDeviceIdle(true)
                        .build();
        scheduleJob(existingJobInfo);

        doCallRealMethod()
                .when(() -> PeriodicEncodingJobService.scheduleIfNeeded(any(), any(), eq(false)));

        PeriodicEncodingJobService.scheduleIfNeeded(sContext, mMockFlags, false);

        verify(() -> PeriodicEncodingJobService.schedule(any(), any()));
        verifyNoMoreInteractions(staticMockMarker(PeriodicEncodingJobWorker.class));
    }

    @Test
    public void testScheduleIfNeededUnderMin() {
        mockDisableRelevantKillSwitches();
        mockGetProtectedSignalPeriodicEncodingJobPeriodMs(60_000);
        JobInfo existingJobInfo =
                new JobInfo.Builder(
                                PROTECTED_SIGNALS_PERIODIC_ENCODING_JOB_ID,
                                new ComponentName(sContext, PeriodicEncodingJobService.class))
                        .setPeriodic(30 * 60 * 1000)
                        // ensure that the job does not run during the test
                        .setRequiresDeviceIdle(true)
                        .build();
        scheduleJob(existingJobInfo);

        doCallRealMethod()
                .when(() -> PeriodicEncodingJobService.scheduleIfNeeded(any(), any(), eq(false)));

        PeriodicEncodingJobService.scheduleIfNeeded(sContext, mMockFlags, false);

        verify(() -> PeriodicEncodingJobService.schedule(any(), any()), never());
        verifyNoMoreInteractions(staticMockMarker(PeriodicEncodingJobWorker.class));
    }

    @Test
    public void testScheduleIfNeededForceSuccess() {
        mockDisableRelevantKillSwitches();
        JobInfo existingJobInfo =
                new JobInfo.Builder(
                                PROTECTED_SIGNALS_PERIODIC_ENCODING_JOB_ID,
                                new ComponentName(sContext, PeriodicEncodingJobService.class))
                        .setMinimumLatency(MINIMUM_SCHEDULING_DELAY_MS)
                        .build();
        scheduleJob(existingJobInfo);

        doCallRealMethod()
                .when(() -> PeriodicEncodingJobService.scheduleIfNeeded(any(), any(), eq(true)));
        doNothing().when(() -> PeriodicEncodingJobService.schedule(any(), any()));

        PeriodicEncodingJobService.scheduleIfNeeded(sContext, mMockFlags, true);

        verify(() -> PeriodicEncodingJobService.schedule(any(), any()));
        verifyNoMoreInteractions(staticMockMarker(PeriodicEncodingJobWorker.class));
    }

    @Test
    public void testScheduleFlagDisabled() {
        mockDisableParentKillSwitches();
        mockGetProtectedSignalsPeriodicEncodingEnabled(false);

        PeriodicEncodingJobService.schedule(sContext, mMockFlags);

        verifyNoMoreInteractions(staticMockMarker(PeriodicEncodingJobWorker.class));
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
        doNothing().when(mSpyEncodingJobService).jobFinished(mMockJobParameters, false);

        // Schedule the job to assert after starting that the scheduled job has been cancelled
        JobInfo existingJobInfo =
                new JobInfo.Builder(
                                PROTECTED_SIGNALS_PERIODIC_ENCODING_JOB_ID,
                                new ComponentName(sContext, PeriodicEncodingJobService.class))
                        .setMinimumLatency(MINIMUM_SCHEDULING_DELAY_MS)
                        .build();
        scheduleJob(existingJobInfo);

        assertOnStartIgnored();
        assertNoPendingJob();
        verifyEncodeProtectedSignalsNeverCalled();
        verifyJobFinished();
        verifyNoMoreInteractions(staticMockMarker(PeriodicEncodingJobWorker.class));

        // Verify logging has not happened even though logging is enabled because this field is not
        // logged
        verifyLoggingNotHappened(logger);
    }

    private void testOnStartJobUpdateTimeoutHandled() throws Exception {
        JobServiceCallback callback =
                new JobServiceCallback().expectJobFinished(mSpyEncodingJobService);

        doReturn(mMockPeriodicEncodingJobWorker)
                .when(() -> PeriodicEncodingJobWorker.getInstance());
        doReturn(FluentFuture.from(immediateFailedFuture(new TimeoutException("testing timeout"))))
                .when(mMockPeriodicEncodingJobWorker)
                .encodeProtectedSignals(PAS_ENCODING_SOURCE_TYPE);

        assertOnStartSucceeded();
        callback.assertJobFinished();

        verify(() -> PeriodicEncodingJobWorker.getInstance());
        verifyEncodeProtectedSignalsCalled();
        verifyJobFinished();
        verifyNoMoreInteractions(staticMockMarker(PeriodicEncodingJobWorker.class));
    }

    private void testOnStartJobUpdateSuccess() throws Exception {
        JobServiceCallback callback =
                new JobServiceCallback().expectJobFinished(mSpyEncodingJobService);
        doReturn(mMockPeriodicEncodingJobWorker)
                .when(() -> PeriodicEncodingJobWorker.getInstance());
        doReturn(FluentFuture.from(immediateFuture(null)))
                .when(mMockPeriodicEncodingJobWorker)
                .encodeProtectedSignals(PAS_ENCODING_SOURCE_TYPE);

        assertOnStartSucceeded();
        callback.assertJobFinished();

        verify(() -> PeriodicEncodingJobWorker.getInstance());
        verifyEncodeProtectedSignalsCalled();
        verifyJobFinished();
        verifyNoMoreInteractions(staticMockMarker(PeriodicEncodingJobWorker.class));
    }

    private void testOnStartJobFlagDisabled() {
        doNothing().when(mSpyEncodingJobService).jobFinished(mMockJobParameters, false);

        // Schedule the job to assert after starting that the scheduled job has been cancelled
        JobInfo existingJobInfo =
                new JobInfo.Builder(
                                PROTECTED_SIGNALS_PERIODIC_ENCODING_JOB_ID,
                                new ComponentName(sContext, PeriodicEncodingJobService.class))
                        .setMinimumLatency(MINIMUM_SCHEDULING_DELAY_MS)
                        .build();
        scheduleJob(existingJobInfo);

        assertOnStartIgnored();
        assertNoPendingJob();
        verifyEncodeProtectedSignalsNeverCalled();
        verifyJobFinished();
        verifyNoMoreInteractions(staticMockMarker(PeriodicEncodingJobWorker.class));
    }

    private void testOnStartJobConsentRevokedGaUxEnabled() {
        mockFledgeConsentIsRevoked();
        doNothing().when(mSpyEncodingJobService).jobFinished(mMockJobParameters, false);

        // Schedule the job to assert after starting that the scheduled job has been cancelled
        JobInfo existingJobInfo =
                new JobInfo.Builder(
                                PROTECTED_SIGNALS_PERIODIC_ENCODING_JOB_ID,
                                new ComponentName(sContext, PeriodicEncodingJobService.class))
                        .setMinimumLatency(MINIMUM_SCHEDULING_DELAY_MS)
                        .build();
        scheduleJob(existingJobInfo);

        assertOnStartIgnored();
        assertNoPendingJob();
        verifyEncodeProtectedSignalsNeverCalled();
        verifyJobFinished();
        verifyNoMoreInteractions(staticMockMarker(PeriodicEncodingJobWorker.class));
    }

    private void mockGetProtectedSignalPeriodicEncodingJobPeriodMs(long value) {
        when(mMockFlags.getProtectedSignalPeriodicEncodingJobPeriodMs()).thenReturn(value);
    }

    private void mockGetGaUxFeatureEnabled(boolean value) {
        when(mMockFlags.getGaUxFeatureEnabled()).thenReturn(value);
    }

    private void mockDisableParentKillSwitches() {
        mockGetGaUxFeatureEnabled(true);
        when(mMockFlags.getProtectedSignalsEnabled()).thenReturn(true);
        when(mMockFlags.getGlobalKillSwitch()).thenReturn(false);
    }

    private void mockDisableRelevantKillSwitches() {
        mockDisableParentKillSwitches();
        mockGetProtectedSignalsPeriodicEncodingEnabled(true);
    }

    private void mockGetProtectedSignalsPeriodicEncodingEnabled(boolean value) {
        when(mMockFlags.getProtectedSignalsPeriodicEncodingEnabled()).thenReturn(value);
    }

    private void mockFlagsEnabledPeriodicEncoding() {
        mockDisableRelevantKillSwitches();
        mockGetProtectedSignalPeriodicEncodingJobPeriodMs(PERIOD);
    }

    private void mockFledgeConsentIsGiven() {
        mockFledgeConsent(AdServicesApiConsent.GIVEN);
    }

    private void mockFledgeConsentIsRevoked() {
        mockFledgeConsent(AdServicesApiConsent.REVOKED);
    }

    private void mockFledgeConsent(AdServicesApiConsent consent) {
        // TODO(b/339046136): should log just 'consent', but it doesn't implement toString()
        Log.v(mTag, "mockFledgeConsent(): " + consent.isGiven());
        doReturn(mMockConsentManager).when(ConsentManager::getInstance);
        when(mMockConsentManager.getConsent(AdServicesApiType.FLEDGE)).thenReturn(consent);
    }

    private JobInfo scheduleJob(JobInfo existingJobInfo) {
        int newJobId = JOB_SCHEDULER.schedule(existingJobInfo);
        assertWithMessage("jobId returened by %s.schedule(%s)", JOB_SCHEDULER, existingJobInfo)
                .that(newJobId)
                .isNotEqualTo(PROTECTED_SIGNALS_PERIODIC_ENCODING_JOB_ID);

        JobInfo pendingJob =
                JOB_SCHEDULER.getPendingJob(PROTECTED_SIGNALS_PERIODIC_ENCODING_JOB_ID);
        assertWithMessage(
                        "getPendingJob(PROTECTED_SIGNALS_PERIODIC_ENCODING_JOB_ID=%s) after"
                                + " schedule(%s)",
                        newJobId, existingJobInfo)
                .that(pendingJob)
                .isNotNull();
        return pendingJob;
    }

    private void assertOnStartIgnored() {
        assertWithMessage("onStartJob(mMockJobParameters)")
                .that(mSpyEncodingJobService.onStartJob(mMockJobParameters))
                .isFalse();
    }

    private void assertOnStartSucceeded() {
        assertWithMessage("onStartJob(mMockJobParameters)")
                .that(mSpyEncodingJobService.onStartJob(mMockJobParameters))
                .isTrue();
    }

    private void assertOnStopSucceeded() {
        assertWithMessage("onStopJob(mMockJobParameters)")
                .that(mSpyEncodingJobService.onStopJob(mMockJobParameters))
                .isTrue();
    }

    private void assertNoPendingJob() {
        assertWithMessage(
                        "getPendingJob(PROTECTED_SIGNALS_PERIODIC_ENCODING_JOB_ID=%s)",
                        PROTECTED_SIGNALS_PERIODIC_ENCODING_JOB_ID)
                .that(JOB_SCHEDULER.getPendingJob(PROTECTED_SIGNALS_PERIODIC_ENCODING_JOB_ID))
                .isNull();
    }

    private void verifyEncodeProtectedSignalsNeverCalled() {
        verify(mMockPeriodicEncodingJobWorker, never())
                .encodeProtectedSignals(PAS_ENCODING_SOURCE_TYPE);
    }

    private void verifyEncodeProtectedSignalsCalled() {
        verify(mMockPeriodicEncodingJobWorker).encodeProtectedSignals(PAS_ENCODING_SOURCE_TYPE);
    }

    private void verifyJobFinished() {
        verify(mSpyEncodingJobService).jobFinished(mMockJobParameters, false);
    }
}

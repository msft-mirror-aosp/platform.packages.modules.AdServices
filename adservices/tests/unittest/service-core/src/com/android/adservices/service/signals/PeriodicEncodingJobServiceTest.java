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

import static com.android.adservices.mockito.ExtendedMockitoExpectations.mockAdServicesJobServiceLogger;
import static com.android.adservices.mockito.MockitoExpectations.syncLogExecutionStats;
import static com.android.adservices.mockito.MockitoExpectations.syncPersistJobExecutionData;
import static com.android.adservices.mockito.MockitoExpectations.verifyBackgroundJobsSkipLogged;
import static com.android.adservices.mockito.MockitoExpectations.verifyJobFinishedLogged;
import static com.android.adservices.mockito.MockitoExpectations.verifyLoggingNotHappened;
import static com.android.adservices.mockito.MockitoExpectations.verifyOnStopJobLogged;
import static com.android.adservices.spe.AdServicesJobInfo.PERIODIC_SIGNALS_ENCODING_JOB;
import static com.android.dx.mockito.inline.extended.ExtendedMockito.doAnswer;
import static com.android.dx.mockito.inline.extended.ExtendedMockito.doCallRealMethod;
import static com.android.dx.mockito.inline.extended.ExtendedMockito.doNothing;
import static com.android.dx.mockito.inline.extended.ExtendedMockito.doReturn;
import static com.android.dx.mockito.inline.extended.ExtendedMockito.staticMockMarker;

import static com.google.common.truth.Truth.assertWithMessage;
import static com.google.common.util.concurrent.Futures.immediateFailedFuture;
import static com.google.common.util.concurrent.Futures.immediateFuture;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertNotNull;
import static org.junit.Assert.assertNull;
import static org.junit.Assert.assertTrue;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoMoreInteractions;
import static org.mockito.Mockito.when;

import android.app.job.JobInfo;
import android.app.job.JobParameters;
import android.app.job.JobScheduler;
import android.content.ComponentName;
import android.content.Context;
import android.util.Log;

import com.android.adservices.common.AdServicesExtendedMockitoTestCase;
import com.android.adservices.common.synccallback.JobServiceLoggingCallback;
import com.android.adservices.service.Flags;
import com.android.adservices.service.FlagsFactory;
import com.android.adservices.service.common.compat.ServiceCompatUtils;
import com.android.adservices.service.consent.AdServicesApiConsent;
import com.android.adservices.service.consent.AdServicesApiType;
import com.android.adservices.service.consent.ConsentManager;
import com.android.adservices.shared.testing.annotations.RequiresSdkLevelAtLeastS;
import com.android.adservices.spe.AdServicesJobServiceLogger;
import com.android.dx.mockito.inline.extended.ExtendedMockito;
import com.android.modules.utils.testing.ExtendedMockitoRule.MockStatic;
import com.android.modules.utils.testing.ExtendedMockitoRule.SpyStatic;

import com.google.common.util.concurrent.FluentFuture;

import org.junit.After;
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
@SpyStatic(PeriodicEncodingJobService.class)
@SpyStatic(PeriodicEncodingJobWorker.class)
@SpyStatic(AdServicesJobServiceLogger.class)
@MockStatic(ServiceCompatUtils.class)
public final class PeriodicEncodingJobServiceTest extends AdServicesExtendedMockitoTestCase {

    private static final int PROTECTED_SIGNALS_PERIODIC_ENCODING_JOB_ID =
            PERIODIC_SIGNALS_ENCODING_JOB.getJobId();

    // Set a minimum delay of 1 hour so scheduled jobs don't run immediately
    private static final long MINIMUM_SCHEDULING_DELAY_MS = 60L * 60L * 1000L;
    private static final long PERIOD = 42 * 60 * 1000;
    private static final JobScheduler JOB_SCHEDULER = sContext.getSystemService(JobScheduler.class);

    @Spy
    private final PeriodicEncodingJobService mSpyEncodingJobService =
            new PeriodicEncodingJobService();

    @Mock private PeriodicEncodingJobWorker mMockPeriodicEncodingJobWorker;
    @Mock private JobParameters mMockJobParameters;
    @Mock private ConsentManager mMockConsentManager;

    @Before
    public void setup() {
        assertWithMessage("job_scheduler").that(JOB_SCHEDULER).isNotNull();
        assertNull(
                "Job already scheduled before setup!",
                JOB_SCHEDULER.getPendingJob(PROTECTED_SIGNALS_PERIODIC_ENCODING_JOB_ID));
        mockFledgeConsentIsGiven();
        doReturn(JOB_SCHEDULER).when(mSpyEncodingJobService).getSystemService(JobScheduler.class);
    }

    @After
    public void tearDown() {
        JOB_SCHEDULER.cancelAll();
    }

    @Test
    public void testOnStartJobFlagDisabled_withoutLogging() {
        Flags flagsWithPeriodicEncodingDisabledWithoutLogging =
                new Flags() {
                    @Override
                    public boolean getBackgroundJobsLoggingKillSwitch() {
                        return true;
                    }

                    @Override
                    public boolean getProtectedSignalsPeriodicEncodingEnabled() {
                        return false;
                    }

                    @Override
                    public long getProtectedSignalPeriodicEncodingJobPeriodMs() {
                        throw new IllegalStateException(
                                "This configured value should not be called");
                    }

                    @Override
                    public long getProtectedSignalsPeriodicEncodingJobFlexMs() {
                        throw new IllegalStateException(
                                "This configured value should not be called");
                    }

                    @Override
                    public boolean getGlobalKillSwitch() {
                        return false;
                    }
                };
        doReturn(flagsWithPeriodicEncodingDisabledWithoutLogging).when(FlagsFactory::getFlags);
        AdServicesJobServiceLogger logger =
                mockAdServicesJobServiceLogger(
                        sContext, flagsWithPeriodicEncodingDisabledWithoutLogging);

        testOnStartJobFlagDisabled();

        verifyLoggingNotHappened(logger);
    }

    @Test
    public void testOnStartJobFlagDisabled_withLogging() throws InterruptedException {
        Flags flagsWithPeriodicEncodingDisabledWithLogging =
                new Flags() {
                    @Override
                    public boolean getBackgroundJobsLoggingKillSwitch() {
                        return false;
                    }

                    @Override
                    public boolean getProtectedSignalsPeriodicEncodingEnabled() {
                        return false;
                    }

                    @Override
                    public long getProtectedSignalPeriodicEncodingJobPeriodMs() {
                        throw new IllegalStateException(
                                "This configured value should not be called");
                    }

                    @Override
                    public long getProtectedSignalsPeriodicEncodingJobFlexMs() {
                        throw new IllegalStateException(
                                "This configured value should not be called");
                    }

                    @Override
                    public boolean getGlobalKillSwitch() {
                        return false;
                    }
                };
        mocker.mockGetFlags(flagsWithPeriodicEncodingDisabledWithLogging);
        AdServicesJobServiceLogger logger =
                mockAdServicesJobServiceLogger(
                        sContext, flagsWithPeriodicEncodingDisabledWithLogging);
        JobServiceLoggingCallback callback = syncLogExecutionStats(logger);

        testOnStartJobFlagDisabled();

        verifyBackgroundJobsSkipLogged(logger, callback);
    }

    @Test
    public void testOnStartJobConsentRevokedGaUxDisabled() {
        Flags flagsWithGaUxDisabled =
                new Flags() {
                    @Override
                    public boolean getGaUxFeatureEnabled() {
                        return false;
                    }
                };

        mocker.mockGetFlags(flagsWithGaUxDisabled);
        mockFledgeConsentIsRevoked();
        doNothing().when(mSpyEncodingJobService).jobFinished(mMockJobParameters, false);

        // Schedule the job to assert after starting that the scheduled job has been cancelled
        JobInfo existingJobInfo =
                new JobInfo.Builder(
                                PROTECTED_SIGNALS_PERIODIC_ENCODING_JOB_ID,
                                new ComponentName(sContext, PeriodicEncodingJobService.class))
                        .setMinimumLatency(MINIMUM_SCHEDULING_DELAY_MS)
                        .build();
        JOB_SCHEDULER.schedule(existingJobInfo);
        assertNotNull(JOB_SCHEDULER.getPendingJob(PROTECTED_SIGNALS_PERIODIC_ENCODING_JOB_ID));

        assertFalse(mSpyEncodingJobService.onStartJob(mMockJobParameters));

        assertNull(JOB_SCHEDULER.getPendingJob(PROTECTED_SIGNALS_PERIODIC_ENCODING_JOB_ID));
        verify(mMockPeriodicEncodingJobWorker, never()).encodeProtectedSignals();
        verify(mSpyEncodingJobService).jobFinished(mMockJobParameters, false);
        verifyNoMoreInteractions(staticMockMarker(PeriodicEncodingJobWorker.class));
    }

    @Test
    public void testOnStartJobConsentRevokedGaUxEnabled_withoutLogging() {
        Flags flagsWithGaUxEnabledLoggingDisabled =
                new Flags() {
                    @Override
                    public boolean getGaUxFeatureEnabled() {
                        return true;
                    }

                    @Override
                    public boolean getBackgroundJobsLoggingKillSwitch() {
                        return true;
                    }
                };
        mocker.mockGetFlags(flagsWithGaUxEnabledLoggingDisabled);
        AdServicesJobServiceLogger logger =
                mockAdServicesJobServiceLogger(sContext, flagsWithGaUxEnabledLoggingDisabled);

        testOnStartJobConsentRevokedGaUxEnabled();

        verifyLoggingNotHappened(logger);
    }

    @Test
    public void testOnStartJobConsentRevokedGaUxEnabled_withLogging() throws InterruptedException {
        Flags flagsWithGaUxEnabledLoggingEnabled =
                new Flags() {
                    @Override
                    public boolean getGaUxFeatureEnabled() {
                        return true;
                    }

                    @Override
                    public boolean getProtectedSignalsPeriodicEncodingEnabled() {
                        return true;
                    }

                    @Override
                    public boolean getBackgroundJobsLoggingKillSwitch() {
                        return false;
                    }

                    @Override
                    public boolean getProtectedSignalsEnabled() {
                        return true;
                    }

                    @Override
                    public boolean getGlobalKillSwitch() {
                        return false;
                    }
                };
        mocker.mockGetFlags(flagsWithGaUxEnabledLoggingEnabled);
        AdServicesJobServiceLogger logger =
                mockAdServicesJobServiceLogger(sContext, flagsWithGaUxEnabledLoggingEnabled);
        JobServiceLoggingCallback callback = syncLogExecutionStats(logger);

        testOnStartJobConsentRevokedGaUxEnabled();

        // Verify logging has happened
        verifyBackgroundJobsSkipLogged(logger, callback);
    }

    @Test
    public void testOnStartJobProtectedSignalsKillSwitchOn() {
        Flags flagsWithKillSwitchOn =
                new Flags() {
                    @Override
                    public boolean getProtectedSignalsEnabled() {
                        return false;
                    }

                    @Override
                    public boolean getGlobalKillSwitch() {
                        return false;
                    }
                };
        mocker.mockGetFlags(flagsWithKillSwitchOn);
        doNothing().when(mSpyEncodingJobService).jobFinished(mMockJobParameters, false);

        // Schedule the job to assert after starting that the scheduled job has been cancelled
        JobInfo existingJobInfo =
                new JobInfo.Builder(
                                PROTECTED_SIGNALS_PERIODIC_ENCODING_JOB_ID,
                                new ComponentName(sContext, PeriodicEncodingJobService.class))
                        .setMinimumLatency(MINIMUM_SCHEDULING_DELAY_MS)
                        .build();
        JOB_SCHEDULER.schedule(existingJobInfo);
        assertNotNull(JOB_SCHEDULER.getPendingJob(PROTECTED_SIGNALS_PERIODIC_ENCODING_JOB_ID));

        assertFalse(mSpyEncodingJobService.onStartJob(mMockJobParameters));

        assertNull(JOB_SCHEDULER.getPendingJob(PROTECTED_SIGNALS_PERIODIC_ENCODING_JOB_ID));
        verify(mMockPeriodicEncodingJobWorker, never()).encodeProtectedSignals();
        verify(mSpyEncodingJobService).jobFinished(mMockJobParameters, false);
        verifyNoMoreInteractions(staticMockMarker(PeriodicEncodingJobWorker.class));
    }

    @Test
    public void testOnStartJobProtectedSignalsKillSwitchOff() throws InterruptedException {
        Flags flagsWithKillSwitchOff =
                new Flags() {
                    @Override
                    public boolean getGaUxFeatureEnabled() {
                        return true;
                    }

                    @Override
                    public boolean getProtectedSignalsEnabled() {
                        return true;
                    }

                    @Override
                    public boolean getProtectedSignalsPeriodicEncodingEnabled() {
                        return true;
                    }

                    @Override
                    public boolean getGlobalKillSwitch() {
                        return false;
                    }
                };
        mocker.mockGetFlags(flagsWithKillSwitchOff);
        doReturn(mMockPeriodicEncodingJobWorker)
                .when(() -> PeriodicEncodingJobWorker.getInstance());
        doReturn(FluentFuture.from(immediateFuture(null)))
                .when(mMockPeriodicEncodingJobWorker)
                .encodeProtectedSignals();
        CountDownLatch jobFinishedCountDown = new CountDownLatch(1);

        doAnswer(
                        unusedInvocation -> {
                            jobFinishedCountDown.countDown();
                            return null;
                        })
                .when(mSpyEncodingJobService)
                .jobFinished(mMockJobParameters, false);

        assertTrue(mSpyEncodingJobService.onStartJob(mMockJobParameters));
        jobFinishedCountDown.await();

        ExtendedMockito.verify(() -> PeriodicEncodingJobWorker.getInstance());
        verify(mMockPeriodicEncodingJobWorker).encodeProtectedSignals();
        verify(mSpyEncodingJobService).jobFinished(mMockJobParameters, false);
        verifyNoMoreInteractions(staticMockMarker(PeriodicEncodingJobWorker.class));
    }

    @Test
    public void testOnStartJobUpdateSuccess_withLogging() throws InterruptedException {
        Flags flagsWithLogging =
                new Flags() {
                    @Override
                    public boolean getGaUxFeatureEnabled() {
                        return true;
                    }

                    @Override
                    public boolean getBackgroundJobsLoggingKillSwitch() {
                        return false;
                    }

                    @Override
                    public boolean getProtectedSignalsEnabled() {
                        return true;
                    }

                    @Override
                    public boolean getProtectedSignalsPeriodicEncodingEnabled() {
                        return true;
                    }

                    @Override
                    public boolean getGlobalKillSwitch() {
                        return false;
                    }
                };
        mocker.mockGetFlags(flagsWithLogging);
        AdServicesJobServiceLogger logger =
                mockAdServicesJobServiceLogger(sContext, flagsWithLogging);
        JobServiceLoggingCallback onStartJobCallback = syncPersistJobExecutionData(logger);
        JobServiceLoggingCallback onJobDoneCallback = syncLogExecutionStats(logger);

        testOnStartJobUpdateSuccess();

        verifyJobFinishedLogged(logger, onStartJobCallback, onJobDoneCallback);
    }

    @Test
    public void testOnStartJobUpdateTimeoutHandled_withoutLogging() throws InterruptedException {
        Flags flagsWithoutLogging =
                new Flags() {
                    @Override
                    public boolean getGaUxFeatureEnabled() {
                        return true;
                    }

                    @Override
                    public boolean getBackgroundJobsLoggingKillSwitch() {
                        return true;
                    }

                    @Override
                    public boolean getProtectedSignalsEnabled() {
                        return true;
                    }

                    @Override
                    public boolean getProtectedSignalsPeriodicEncodingEnabled() {
                        return true;
                    }

                    @Override
                    public boolean getGlobalKillSwitch() {
                        return false;
                    }
                };
        mocker.mockGetFlags(flagsWithoutLogging);
        AdServicesJobServiceLogger logger =
                mockAdServicesJobServiceLogger(sContext, flagsWithoutLogging);

        testOnStartJobUpdateTimeoutHandled();

        verifyLoggingNotHappened(logger);
    }

    @Test
    public void testOnStartJobUpdateTimeoutHandled_withLogging() throws InterruptedException {
        Flags flagsWithLogging =
                new Flags() {
                    @Override
                    public boolean getGaUxFeatureEnabled() {
                        return true;
                    }

                    @Override
                    public boolean getBackgroundJobsLoggingKillSwitch() {
                        return false;
                    }

                    @Override
                    public boolean getProtectedSignalsEnabled() {
                        return true;
                    }

                    @Override
                    public boolean getProtectedSignalsPeriodicEncodingEnabled() {
                        return true;
                    }

                    @Override
                    public boolean getGlobalKillSwitch() {
                        return false;
                    }
                };
        mocker.mockGetFlags(flagsWithLogging);
        AdServicesJobServiceLogger logger =
                mockAdServicesJobServiceLogger(sContext, flagsWithLogging);
        JobServiceLoggingCallback onStartJobCallback = syncPersistJobExecutionData(logger);
        JobServiceLoggingCallback onJobDoneCallback = syncLogExecutionStats(logger);

        testOnStartJobUpdateTimeoutHandled();

        verifyJobFinishedLogged(logger, onStartJobCallback, onJobDoneCallback);
    }

    @Test
    public void testOnStartJobUpdateInterruptedHandled() throws InterruptedException {
        CountDownLatch jobFinishedCountDown = new CountDownLatch(1);

        Flags flagsEnabledPeriodicEncoding =
                new Flags() {
                    @Override
                    public boolean getGaUxFeatureEnabled() {
                        return true;
                    }

                    @Override
                    public boolean getProtectedSignalsPeriodicEncodingEnabled() {
                        return true;
                    }

                    @Override
                    public boolean getProtectedSignalsEnabled() {
                        return true;
                    }

                    @Override
                    public boolean getGlobalKillSwitch() {
                        return false;
                    }
                };
        mocker.mockGetFlags(flagsEnabledPeriodicEncoding);
        doReturn(mMockPeriodicEncodingJobWorker)
                .when(() -> PeriodicEncodingJobWorker.getInstance());
        doReturn(
                        FluentFuture.from(
                                immediateFailedFuture(new InterruptedException("testing timeout"))))
                .when(mMockPeriodicEncodingJobWorker)
                .encodeProtectedSignals();
        doAnswer(
                        unusedInvocation -> {
                            jobFinishedCountDown.countDown();
                            return null;
                        })
                .when(mSpyEncodingJobService)
                .jobFinished(mMockJobParameters, false);

        assertTrue(mSpyEncodingJobService.onStartJob(mMockJobParameters));
        jobFinishedCountDown.await();

        ExtendedMockito.verify(() -> PeriodicEncodingJobWorker.getInstance());
        verify(mMockPeriodicEncodingJobWorker).encodeProtectedSignals();
        verify(mSpyEncodingJobService).jobFinished(mMockJobParameters, false);
        verifyNoMoreInteractions(staticMockMarker(PeriodicEncodingJobWorker.class));
    }

    @Test
    public void testOnStartJobUpdateExecutionExceptionHandled() throws InterruptedException {
        CountDownLatch jobFinishedCountDown = new CountDownLatch(1);

        Flags flagsEnabledPeriodicEncoding =
                new Flags() {
                    @Override
                    public boolean getGaUxFeatureEnabled() {
                        return true;
                    }

                    @Override
                    public boolean getProtectedSignalsPeriodicEncodingEnabled() {
                        return true;
                    }

                    @Override
                    public boolean getProtectedSignalsEnabled() {
                        return true;
                    }

                    @Override
                    public boolean getGlobalKillSwitch() {
                        return false;
                    }
                };
        mocker.mockGetFlags(flagsEnabledPeriodicEncoding);
        doReturn(mMockPeriodicEncodingJobWorker)
                .when(() -> PeriodicEncodingJobWorker.getInstance());

        doReturn(
                        FluentFuture.from(
                                immediateFailedFuture(
                                        new ExecutionException("testing timeout", null))))
                .when(mMockPeriodicEncodingJobWorker)
                .encodeProtectedSignals();
        doAnswer(
                        unusedInvocation -> {
                            jobFinishedCountDown.countDown();
                            return null;
                        })
                .when(mSpyEncodingJobService)
                .jobFinished(mMockJobParameters, false);

        assertTrue(mSpyEncodingJobService.onStartJob(mMockJobParameters));
        jobFinishedCountDown.await();

        ExtendedMockito.verify(() -> PeriodicEncodingJobWorker.getInstance());
        verify(mMockPeriodicEncodingJobWorker).encodeProtectedSignals();
        verify(mSpyEncodingJobService).jobFinished(mMockJobParameters, false);
        verifyNoMoreInteractions(staticMockMarker(PeriodicEncodingJobWorker.class));
    }

    @Test
    public void testOnStopJobCallsStopWork_withoutLogging() {
        Flags flagsWithoutLogging =
                new Flags() {
                    @Override
                    public boolean getBackgroundJobsLoggingKillSwitch() {
                        return true;
                    }
                };
        mocker.mockGetFlags(flagsWithoutLogging);
        AdServicesJobServiceLogger logger =
                mockAdServicesJobServiceLogger(sContext, flagsWithoutLogging);

        doReturn(mMockPeriodicEncodingJobWorker)
                .when(() -> PeriodicEncodingJobWorker.getInstance());
        doNothing().when(mMockPeriodicEncodingJobWorker).stopWork();
        assertTrue(mSpyEncodingJobService.onStopJob(mMockJobParameters));
        verify(mMockPeriodicEncodingJobWorker).stopWork();

        verifyLoggingNotHappened(logger);
    }

    @Test
    public void testOnStopJob_withLogging() throws InterruptedException {
        Flags flagsWithLogging =
                new Flags() {
                    @Override
                    public boolean getBackgroundJobsLoggingKillSwitch() {
                        return false;
                    }
                };
        mocker.mockGetFlags(flagsWithLogging);
        AdServicesJobServiceLogger logger =
                mockAdServicesJobServiceLogger(sContext, flagsWithLogging);
        JobServiceLoggingCallback callback = syncLogExecutionStats(logger);

        doReturn(mMockPeriodicEncodingJobWorker)
                .when(() -> PeriodicEncodingJobWorker.getInstance());
        doNothing().when(mMockPeriodicEncodingJobWorker).stopWork();
        assertTrue(mSpyEncodingJobService.onStopJob(mMockJobParameters));
        verify(mMockPeriodicEncodingJobWorker).stopWork();

        verifyOnStopJobLogged(logger, callback);
    }

    @Test
    public void testScheduleIfNeededFlagDisabled() {
        Flags flagsDisabledPeriodicEncoding =
                new Flags() {
                    @Override
                    public boolean getGaUxFeatureEnabled() {
                        return true;
                    }

                    @Override
                    public boolean getProtectedSignalsPeriodicEncodingEnabled() {
                        return false;
                    }

                    @Override
                    public boolean getProtectedSignalsEnabled() {
                        return true;
                    }

                    @Override
                    public boolean getGlobalKillSwitch() {
                        return false;
                    }
                };
        doCallRealMethod()
                .when(() -> PeriodicEncodingJobService.scheduleIfNeeded(any(), any(), eq(false)));

        PeriodicEncodingJobService.scheduleIfNeeded(sContext, flagsDisabledPeriodicEncoding, false);

        ExtendedMockito.verify(() -> PeriodicEncodingJobService.schedule(any(), any()), never());
        verifyNoMoreInteractions(staticMockMarker(PeriodicEncodingJobWorker.class));
    }

    @Test
    public void testScheduleIfNeededSuccess() {
        Flags flagsEnabledPeriodicEncoding =
                new Flags() {
                    @Override
                    public boolean getGaUxFeatureEnabled() {
                        return true;
                    }

                    @Override
                    public boolean getProtectedSignalsPeriodicEncodingEnabled() {
                        return true;
                    }

                    @Override
                    public boolean getProtectedSignalsEnabled() {
                        return true;
                    }

                    @Override
                    public boolean getGlobalKillSwitch() {
                        return false;
                    }
                };
        doCallRealMethod()
                .when(() -> PeriodicEncodingJobService.scheduleIfNeeded(any(), any(), eq(false)));
        doNothing().when(() -> PeriodicEncodingJobService.schedule(any(), any()));

        PeriodicEncodingJobService.scheduleIfNeeded(sContext, flagsEnabledPeriodicEncoding, false);

        ExtendedMockito.verify(() -> PeriodicEncodingJobService.schedule(any(), any()));
        verifyNoMoreInteractions(staticMockMarker(PeriodicEncodingJobWorker.class));
    }

    @Test
    public void testScheduleIfNeeded_AlreadyScheduled_isNotRescheduled() {
        Flags flagsEnabledPeriodicEncoding =
                new Flags() {
                    @Override
                    public boolean getGaUxFeatureEnabled() {
                        return true;
                    }

                    @Override
                    public boolean getProtectedSignalsPeriodicEncodingEnabled() {
                        return true;
                    }

                    @Override
                    public boolean getProtectedSignalsEnabled() {
                        return true;
                    }

                    @Override
                    public boolean getGlobalKillSwitch() {
                        return false;
                    }

                    @Override
                    public long getProtectedSignalPeriodicEncodingJobPeriodMs() {
                        return PERIOD;
                    }
                };
        JobInfo existingJobInfo =
                new JobInfo.Builder(
                                PROTECTED_SIGNALS_PERIODIC_ENCODING_JOB_ID,
                                new ComponentName(sContext, PeriodicEncodingJobService.class))
                        .setPeriodic(PERIOD)
                        // ensure that the job does not run during the test
                        .setRequiresDeviceIdle(true)
                        .build();
        JOB_SCHEDULER.schedule(existingJobInfo);
        JobInfo job = JOB_SCHEDULER.getPendingJob(PROTECTED_SIGNALS_PERIODIC_ENCODING_JOB_ID);
        assertNotNull(job);
        assertEquals(PERIOD, job.getIntervalMillis());

        doCallRealMethod()
                .when(() -> PeriodicEncodingJobService.scheduleIfNeeded(any(), any(), eq(false)));

        PeriodicEncodingJobService.scheduleIfNeeded(sContext, flagsEnabledPeriodicEncoding, false);

        ExtendedMockito.verify(() -> PeriodicEncodingJobService.schedule(any(), any()), never());
        verifyNoMoreInteractions(staticMockMarker(PeriodicEncodingJobWorker.class));
    }

    @Test
    public void testScheduleIfNeeded_AlreadyScheduledTimeoutChanged_isRescheduled() {
        Flags flagsEnabledPeriodicEncoding =
                new Flags() {
                    @Override
                    public boolean getGaUxFeatureEnabled() {
                        return true;
                    }

                    @Override
                    public boolean getProtectedSignalsPeriodicEncodingEnabled() {
                        return true;
                    }

                    @Override
                    public boolean getProtectedSignalsEnabled() {
                        return true;
                    }

                    @Override
                    public boolean getGlobalKillSwitch() {
                        return false;
                    }

                    @Override
                    public long getProtectedSignalPeriodicEncodingJobPeriodMs() {
                        return PERIOD;
                    }
                };
        JobInfo existingJobInfo =
                new JobInfo.Builder(
                                PROTECTED_SIGNALS_PERIODIC_ENCODING_JOB_ID,
                                new ComponentName(sContext, PeriodicEncodingJobService.class))
                        .setPeriodic(30 * 60 * 1000)
                        // ensure that the job does not run during the test
                        .setRequiresDeviceIdle(true)
                        .build();
        JOB_SCHEDULER.schedule(existingJobInfo);
        assertNotNull(JOB_SCHEDULER.getPendingJob(PROTECTED_SIGNALS_PERIODIC_ENCODING_JOB_ID));

        doCallRealMethod()
                .when(() -> PeriodicEncodingJobService.scheduleIfNeeded(any(), any(), eq(false)));

        PeriodicEncodingJobService.scheduleIfNeeded(sContext, flagsEnabledPeriodicEncoding, false);

        ExtendedMockito.verify(() -> PeriodicEncodingJobService.schedule(any(), any()));
        verifyNoMoreInteractions(staticMockMarker(PeriodicEncodingJobWorker.class));
    }

    @Test
    public void testScheduleIfNeededUnderMin() {
        Flags flagsEnabledPeriodicEncoding =
                new Flags() {
                    @Override
                    public boolean getGaUxFeatureEnabled() {
                        return true;
                    }

                    @Override
                    public boolean getProtectedSignalsPeriodicEncodingEnabled() {
                        return true;
                    }

                    @Override
                    public boolean getProtectedSignalsEnabled() {
                        return true;
                    }

                    @Override
                    public boolean getGlobalKillSwitch() {
                        return false;
                    }

                    @Override
                    public long getProtectedSignalPeriodicEncodingJobPeriodMs() {
                        return 60 * 1000;
                    }
                };
        JobInfo existingJobInfo =
                new JobInfo.Builder(
                                PROTECTED_SIGNALS_PERIODIC_ENCODING_JOB_ID,
                                new ComponentName(sContext, PeriodicEncodingJobService.class))
                        .setPeriodic(30 * 60 * 1000)
                        // ensure that the job does not run during the test
                        .setRequiresDeviceIdle(true)
                        .build();
        JOB_SCHEDULER.schedule(existingJobInfo);
        assertNotNull(JOB_SCHEDULER.getPendingJob(PROTECTED_SIGNALS_PERIODIC_ENCODING_JOB_ID));

        doCallRealMethod()
                .when(() -> PeriodicEncodingJobService.scheduleIfNeeded(any(), any(), eq(false)));

        PeriodicEncodingJobService.scheduleIfNeeded(sContext, flagsEnabledPeriodicEncoding, false);

        ExtendedMockito.verify(() -> PeriodicEncodingJobService.schedule(any(), any()), never());
        verifyNoMoreInteractions(staticMockMarker(PeriodicEncodingJobWorker.class));
    }

    @Test
    public void testScheduleIfNeededForceSuccess() {
        Flags flagsEnabledPeriodicEncoding =
                new Flags() {
                    @Override
                    public boolean getGaUxFeatureEnabled() {
                        return true;
                    }

                    @Override
                    public boolean getProtectedSignalsPeriodicEncodingEnabled() {
                        return true;
                    }

                    @Override
                    public boolean getProtectedSignalsEnabled() {
                        return true;
                    }

                    @Override
                    public boolean getGlobalKillSwitch() {
                        return false;
                    }
                };
        JobInfo existingJobInfo =
                new JobInfo.Builder(
                                PROTECTED_SIGNALS_PERIODIC_ENCODING_JOB_ID,
                                new ComponentName(sContext, PeriodicEncodingJobService.class))
                        .setMinimumLatency(MINIMUM_SCHEDULING_DELAY_MS)
                        .build();
        JOB_SCHEDULER.schedule(existingJobInfo);
        assertNotNull(JOB_SCHEDULER.getPendingJob(PROTECTED_SIGNALS_PERIODIC_ENCODING_JOB_ID));

        doCallRealMethod()
                .when(() -> PeriodicEncodingJobService.scheduleIfNeeded(any(), any(), eq(true)));
        doNothing().when(() -> PeriodicEncodingJobService.schedule(any(), any()));

        PeriodicEncodingJobService.scheduleIfNeeded(sContext, flagsEnabledPeriodicEncoding, true);

        ExtendedMockito.verify(() -> PeriodicEncodingJobService.schedule(any(), any()));
        verifyNoMoreInteractions(staticMockMarker(PeriodicEncodingJobWorker.class));
    }

    @Test
    public void testScheduleFlagDisabled() {
        Flags flagsDisabledPeriodicEncoding =
                new Flags() {
                    @Override
                    public boolean getGaUxFeatureEnabled() {
                        return true;
                    }

                    @Override
                    public boolean getProtectedSignalsPeriodicEncodingEnabled() {
                        return false;
                    }
                };
        PeriodicEncodingJobService.schedule(sContext, flagsDisabledPeriodicEncoding);

        verifyNoMoreInteractions(staticMockMarker(PeriodicEncodingJobWorker.class));
    }

    @Test
    public void testOnStartJob_shouldDisableJobTrue_withoutLogging() {
        Flags flagsWithoutLogging =
                new Flags() {
                    @Override
                    public boolean getBackgroundJobsLoggingKillSwitch() {
                        return true;
                    }
                };
        mocker.mockGetFlags(flagsWithoutLogging);
        AdServicesJobServiceLogger logger =
                mockAdServicesJobServiceLogger(sContext, flagsWithoutLogging);

        testOnStartJobShouldDisableJobTrue();

        verifyLoggingNotHappened(logger);
    }

    @Test
    public void testOnStartJob_shouldDisableJobTrue_withLoggingEnabled() {
        Flags flagsWithLogging =
                new Flags() {
                    @Override
                    public boolean getBackgroundJobsLoggingKillSwitch() {
                        return false;
                    }
                };
        mocker.mockGetFlags(flagsWithLogging);
        AdServicesJobServiceLogger logger =
                mockAdServicesJobServiceLogger(sContext, flagsWithLogging);

        testOnStartJobShouldDisableJobTrue();

        // Verify logging has not happened even though logging is enabled because this field is not
        // logged
        verifyLoggingNotHappened(logger);
    }

    private void testOnStartJobUpdateTimeoutHandled() throws InterruptedException {
        CountDownLatch jobFinishedCountDown = new CountDownLatch(1);

        doReturn(mMockPeriodicEncodingJobWorker)
                .when(() -> PeriodicEncodingJobWorker.getInstance());
        doReturn(FluentFuture.from(immediateFailedFuture(new TimeoutException("testing timeout"))))
                .when(mMockPeriodicEncodingJobWorker)
                .encodeProtectedSignals();
        doAnswer(
                        unusedInvocation -> {
                            jobFinishedCountDown.countDown();
                            return null;
                        })
                .when(mSpyEncodingJobService)
                .jobFinished(mMockJobParameters, false);

        assertTrue(mSpyEncodingJobService.onStartJob(mMockJobParameters));
        jobFinishedCountDown.await();

        ExtendedMockito.verify(() -> PeriodicEncodingJobWorker.getInstance());
        verify(mMockPeriodicEncodingJobWorker).encodeProtectedSignals();
        verify(mSpyEncodingJobService).jobFinished(mMockJobParameters, false);
        verifyNoMoreInteractions(staticMockMarker(PeriodicEncodingJobWorker.class));
    }

    private void testOnStartJobShouldDisableJobTrue() {
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
        JOB_SCHEDULER.schedule(existingJobInfo);
        assertNotNull(JOB_SCHEDULER.getPendingJob(PROTECTED_SIGNALS_PERIODIC_ENCODING_JOB_ID));

        assertFalse(mSpyEncodingJobService.onStartJob(mMockJobParameters));

        assertNull(JOB_SCHEDULER.getPendingJob(PROTECTED_SIGNALS_PERIODIC_ENCODING_JOB_ID));
        verify(mMockPeriodicEncodingJobWorker, never()).encodeProtectedSignals();
        verify(mSpyEncodingJobService).jobFinished(mMockJobParameters, false);
        verifyNoMoreInteractions(staticMockMarker(PeriodicEncodingJobWorker.class));
    }

    private void testOnStartJobUpdateSuccess() throws InterruptedException {
        CountDownLatch jobFinishedCountDown = new CountDownLatch(1);

        doReturn(mMockPeriodicEncodingJobWorker)
                .when(() -> PeriodicEncodingJobWorker.getInstance());
        doReturn(FluentFuture.from(immediateFuture(null)))
                .when(mMockPeriodicEncodingJobWorker)
                .encodeProtectedSignals();
        doAnswer(
                        unusedInvocation -> {
                            jobFinishedCountDown.countDown();
                            return null;
                        })
                .when(mSpyEncodingJobService)
                .jobFinished(mMockJobParameters, false);

        assertTrue(mSpyEncodingJobService.onStartJob(mMockJobParameters));
        jobFinishedCountDown.await();

        ExtendedMockito.verify(() -> PeriodicEncodingJobWorker.getInstance());
        verify(mMockPeriodicEncodingJobWorker).encodeProtectedSignals();
        verify(mSpyEncodingJobService).jobFinished(mMockJobParameters, false);
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
        JOB_SCHEDULER.schedule(existingJobInfo);
        assertNotNull(JOB_SCHEDULER.getPendingJob(PROTECTED_SIGNALS_PERIODIC_ENCODING_JOB_ID));
        assertFalse(mSpyEncodingJobService.onStartJob(mMockJobParameters));

        assertNull(JOB_SCHEDULER.getPendingJob(PROTECTED_SIGNALS_PERIODIC_ENCODING_JOB_ID));
        verify(mMockPeriodicEncodingJobWorker, never()).encodeProtectedSignals();
        verify(mSpyEncodingJobService).jobFinished(mMockJobParameters, false);
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
        JOB_SCHEDULER.schedule(existingJobInfo);
        assertNotNull(JOB_SCHEDULER.getPendingJob(PROTECTED_SIGNALS_PERIODIC_ENCODING_JOB_ID));

        assertFalse(mSpyEncodingJobService.onStartJob(mMockJobParameters));

        assertNull(JOB_SCHEDULER.getPendingJob(PROTECTED_SIGNALS_PERIODIC_ENCODING_JOB_ID));
        verify(mMockPeriodicEncodingJobWorker, never()).encodeProtectedSignals();
        verify(mSpyEncodingJobService).jobFinished(mMockJobParameters, false);
        verifyNoMoreInteractions(staticMockMarker(PeriodicEncodingJobWorker.class));
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
}

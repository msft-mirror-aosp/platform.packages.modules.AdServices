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

import static com.android.adservices.service.stats.AdServicesStatsLog.AD_SERVICES_BACKGROUND_JOBS_EXECUTION_REPORTED__EXECUTION_RESULT_CODE__FAILED_WITHOUT_RETRY;
import static com.android.adservices.service.stats.AdServicesStatsLog.AD_SERVICES_BACKGROUND_JOBS_EXECUTION_REPORTED__EXECUTION_RESULT_CODE__SKIP_FOR_USER_CONSENT_REVOKED;
import static com.android.adservices.service.stats.AdServicesStatsLog.AD_SERVICES_BACKGROUND_JOBS_EXECUTION_REPORTED__EXECUTION_RESULT_CODE__SUCCESSFUL;
import static com.android.adservices.spe.AdservicesJobInfo.PERIODIC_SIGNALS_ENCODING_JOB;
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
import static org.mockito.ArgumentMatchers.anyInt;
import static org.mockito.ArgumentMatchers.anyLong;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.spy;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoMoreInteractions;

import android.app.job.JobInfo;
import android.app.job.JobParameters;
import android.app.job.JobScheduler;
import android.content.ComponentName;
import android.content.Context;

import androidx.test.core.app.ApplicationProvider;

import com.android.adservices.service.Flags;
import com.android.adservices.service.FlagsFactory;
import com.android.adservices.service.common.compat.ServiceCompatUtils;
import com.android.adservices.service.consent.AdServicesApiConsent;
import com.android.adservices.service.consent.AdServicesApiType;
import com.android.adservices.service.consent.ConsentManager;
import com.android.adservices.service.stats.Clock;
import com.android.adservices.service.stats.StatsdAdServicesLogger;
import com.android.adservices.spe.AdservicesJobServiceLogger;
import com.android.dx.mockito.inline.extended.ExtendedMockito;

import com.google.common.util.concurrent.FluentFuture;

import org.junit.After;
import org.junit.Assume;
import org.junit.Before;
import org.junit.Test;
import org.mockito.Mock;
import org.mockito.Mockito;
import org.mockito.MockitoSession;
import org.mockito.Spy;
import org.mockito.quality.Strictness;

import java.util.concurrent.CountDownLatch;
import java.util.concurrent.ExecutionException;
import java.util.concurrent.TimeoutException;

public class PeriodicEncodingJobServiceTest {

    private static final int PROTECTED_SIGNALS_PERIODIC_ENCODING_JOB_ID =
            PERIODIC_SIGNALS_ENCODING_JOB.getJobId();

    private static final Context CONTEXT = ApplicationProvider.getApplicationContext();
    // Set a minimum delay of 1 hour so scheduled jobs don't run immediately
    private static final long MINIMUM_SCHEDULING_DELAY_MS = 60L * 60L * 1000L;
    private static final JobScheduler JOB_SCHEDULER = CONTEXT.getSystemService(JobScheduler.class);

    @Spy
    private final PeriodicEncodingJobService mEncodingJobServiceSpy =
            new PeriodicEncodingJobService();

    private AdservicesJobServiceLogger mSpyLogger;

    @Mock PeriodicEncodingJobWorker mPeriodicEncodingJobWorker;
    @Mock StatsdAdServicesLogger mMockStatsdLogger;
    @Mock private JobParameters mJobParametersMock;
    @Mock private ConsentManager mConsentManagerMock;

    private MockitoSession mStaticMockSession = null;

    @Before
    public void setup() {

        mStaticMockSession =
                ExtendedMockito.mockitoSession()
                        .spyStatic(FlagsFactory.class)
                        .mockStatic(ConsentManager.class)
                        .spyStatic(PeriodicEncodingJobService.class)
                        .spyStatic(PeriodicEncodingJobWorker.class)
                        .spyStatic(AdservicesJobServiceLogger.class)
                        .mockStatic(ServiceCompatUtils.class)
                        .strictness(Strictness.LENIENT)
                        .initMocks(this)
                        .startMocking();

        Assume.assumeNotNull(JOB_SCHEDULER);
        assertNull(
                "Job already scheduled before setup!",
                JOB_SCHEDULER.getPendingJob(PROTECTED_SIGNALS_PERIODIC_ENCODING_JOB_ID));

        // Mock AdservicesJobServiceLogger to not actually log the stats to server
        mSpyLogger =
                spy(new AdservicesJobServiceLogger(CONTEXT, Clock.SYSTEM_CLOCK, mMockStatsdLogger));
        Mockito.doNothing()
                .when(mSpyLogger)
                .logExecutionStats(anyInt(), anyLong(), anyInt(), anyInt());
        ExtendedMockito.doReturn(mSpyLogger)
                .when(() -> AdservicesJobServiceLogger.getInstance(any(Context.class)));
    }

    @After
    public void tearDown() {
        JOB_SCHEDULER.cancelAll();
        mStaticMockSession.finishMocking();
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

        testOnStartJobFlagDisabled();

        // Verify logging methods are not invoked.
        verify(mSpyLogger, never()).persistJobExecutionData(anyInt(), anyLong());
        verify(mSpyLogger, never()).logExecutionStats(anyInt(), anyLong(), anyInt(), anyInt());
    }

    @Test
    public void testOnStartJobFlagDisabled_withLogging() {
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
        doReturn(flagsWithPeriodicEncodingDisabledWithLogging).when(FlagsFactory::getFlags);

        testOnStartJobFlagDisabled();

        // Verify logging methods are invoked.
        verify(mSpyLogger).persistJobExecutionData(anyInt(), anyLong());
        verify(mSpyLogger).logExecutionStats(anyInt(), anyLong(), anyInt(), anyInt());
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

        doReturn(flagsWithGaUxDisabled).when(FlagsFactory::getFlags);
        doReturn(mConsentManagerMock).when(() -> ConsentManager.getInstance(any()));
        doReturn(AdServicesApiConsent.REVOKED)
                .when(mConsentManagerMock)
                .getConsent(AdServicesApiType.FLEDGE);
        doReturn(JOB_SCHEDULER).when(mEncodingJobServiceSpy).getSystemService(JobScheduler.class);
        doNothing().when(mEncodingJobServiceSpy).jobFinished(mJobParametersMock, false);

        // Schedule the job to assert after starting that the scheduled job has been cancelled
        JobInfo existingJobInfo =
                new JobInfo.Builder(
                        PROTECTED_SIGNALS_PERIODIC_ENCODING_JOB_ID,
                        new ComponentName(CONTEXT, PeriodicEncodingJobService.class))
                        .setMinimumLatency(MINIMUM_SCHEDULING_DELAY_MS)
                        .build();
        JOB_SCHEDULER.schedule(existingJobInfo);
        assertNotNull(JOB_SCHEDULER.getPendingJob(PROTECTED_SIGNALS_PERIODIC_ENCODING_JOB_ID));

        assertFalse(mEncodingJobServiceSpy.onStartJob(mJobParametersMock));

        assertNull(JOB_SCHEDULER.getPendingJob(PROTECTED_SIGNALS_PERIODIC_ENCODING_JOB_ID));
        verify(mPeriodicEncodingJobWorker, never()).encodeProtectedSignals();
        verify(mEncodingJobServiceSpy).jobFinished(mJobParametersMock, false);
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
        doReturn(flagsWithGaUxEnabledLoggingDisabled).when(FlagsFactory::getFlags);

        testOnStartJobConsentRevokedGaUxEnabled();

        // Verify logging methods are not invoked.
        verify(mSpyLogger, never()).persistJobExecutionData(anyInt(), anyLong());
        verify(mSpyLogger, never()).logExecutionStats(anyInt(), anyLong(), anyInt(), anyInt());
    }

    @Test
    public void testOnStartJobConsentRevokedGaUxEnabled_withLogging() {
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
                    public boolean getProtectedSignalsServiceKillSwitch() {
                        return false;
                    }

                    @Override
                    public boolean getGlobalKillSwitch() {
                        return false;
                    }
                };
        doReturn(flagsWithGaUxEnabledLoggingEnabled).when(FlagsFactory::getFlags);

        testOnStartJobConsentRevokedGaUxEnabled();

        // Verify logging has happened
        verify(mSpyLogger)
                .logExecutionStats(
                        anyInt(),
                        anyLong(),
                        eq(
                                AD_SERVICES_BACKGROUND_JOBS_EXECUTION_REPORTED__EXECUTION_RESULT_CODE__SKIP_FOR_USER_CONSENT_REVOKED),
                        anyInt());
    }

    @Test
    public void testOnStartJobProtectedSignalsKillSwitchOn() {
        Flags flagsWithKillSwitchOn =
                new Flags() {
                    @Override
                    public boolean getProtectedSignalsServiceKillSwitch() {
                        return true;
                    }

                    @Override
                    public boolean getGlobalKillSwitch() {
                        return false;
                    }
                };
        doReturn(flagsWithKillSwitchOn).when(FlagsFactory::getFlags);
        doReturn(JOB_SCHEDULER).when(mEncodingJobServiceSpy).getSystemService(JobScheduler.class);
        doNothing().when(mEncodingJobServiceSpy).jobFinished(mJobParametersMock, false);

        // Schedule the job to assert after starting that the scheduled job has been cancelled
        JobInfo existingJobInfo =
                new JobInfo.Builder(
                        PROTECTED_SIGNALS_PERIODIC_ENCODING_JOB_ID,
                        new ComponentName(CONTEXT, PeriodicEncodingJobService.class))
                        .setMinimumLatency(MINIMUM_SCHEDULING_DELAY_MS)
                        .build();
        JOB_SCHEDULER.schedule(existingJobInfo);
        assertNotNull(JOB_SCHEDULER.getPendingJob(PROTECTED_SIGNALS_PERIODIC_ENCODING_JOB_ID));

        assertFalse(mEncodingJobServiceSpy.onStartJob(mJobParametersMock));

        assertNull(JOB_SCHEDULER.getPendingJob(PROTECTED_SIGNALS_PERIODIC_ENCODING_JOB_ID));
        verify(mPeriodicEncodingJobWorker, never()).encodeProtectedSignals();
        verify(mEncodingJobServiceSpy).jobFinished(mJobParametersMock, false);
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
                    public boolean getProtectedSignalsServiceKillSwitch() {
                        return false;
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
        doReturn(flagsWithKillSwitchOff).when(FlagsFactory::getFlags);
        doReturn(mConsentManagerMock).when(() -> ConsentManager.getInstance(any()));
        doReturn(AdServicesApiConsent.GIVEN)
                .when(mConsentManagerMock)
                .getConsent(AdServicesApiType.FLEDGE);
        doReturn(mPeriodicEncodingJobWorker)
                .when(() -> PeriodicEncodingJobWorker.getInstance(any()));
        doReturn(FluentFuture.from(immediateFuture(null)))
                .when(mPeriodicEncodingJobWorker)
                .encodeProtectedSignals();
        CountDownLatch jobFinishedCountDown = new CountDownLatch(1);

        doAnswer(
                unusedInvocation -> {
                    jobFinishedCountDown.countDown();
                    return null;
                })
                .when(mEncodingJobServiceSpy)
                .jobFinished(mJobParametersMock, false);

        assertTrue(mEncodingJobServiceSpy.onStartJob(mJobParametersMock));
        jobFinishedCountDown.await();

        ExtendedMockito.verify(() -> PeriodicEncodingJobWorker.getInstance(mEncodingJobServiceSpy));
        verify(mPeriodicEncodingJobWorker).encodeProtectedSignals();
        verify(mEncodingJobServiceSpy).jobFinished(mJobParametersMock, false);
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
                    public boolean getProtectedSignalsServiceKillSwitch() {
                        return false;
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
        doReturn(flagsWithLogging).when(FlagsFactory::getFlags);

        testOnStartJobUpdateSuccess();

        // Verify logging methods are invoked.
        verify(mSpyLogger).persistJobExecutionData(anyInt(), anyLong());
        verify(mSpyLogger)
                .logExecutionStats(
                        anyInt(),
                        anyLong(),
                        eq(
                                AD_SERVICES_BACKGROUND_JOBS_EXECUTION_REPORTED__EXECUTION_RESULT_CODE__SUCCESSFUL),
                        anyInt());
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
                    public boolean getProtectedSignalsServiceKillSwitch() {
                        return false;
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
        doReturn(flagsWithoutLogging).when(FlagsFactory::getFlags);

        testOnStartJobUpdateTimeoutHandled();

        // Verify logging method is not invoked.
        verify(mSpyLogger, never()).logExecutionStats(anyInt(), anyLong(), anyInt(), anyInt());
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
                    public boolean getProtectedSignalsServiceKillSwitch() {
                        return false;
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
        doReturn(flagsWithLogging).when(FlagsFactory::getFlags);

        testOnStartJobUpdateTimeoutHandled();

        // Verify logging methods are invoked.
        verify(mSpyLogger).persistJobExecutionData(anyInt(), anyLong());
        verify(mSpyLogger)
                .logExecutionStats(
                        anyInt(),
                        anyLong(),
                        eq(
                                AD_SERVICES_BACKGROUND_JOBS_EXECUTION_REPORTED__EXECUTION_RESULT_CODE__FAILED_WITHOUT_RETRY),
                        anyInt());
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
                    public boolean getProtectedSignalsServiceKillSwitch() {
                        return false;
                    }

                    @Override
                    public boolean getGlobalKillSwitch() {
                        return false;
                    }
                };
        doReturn(flagsEnabledPeriodicEncoding).when(FlagsFactory::getFlags);
        doReturn(mConsentManagerMock).when(() -> ConsentManager.getInstance(any()));
        doReturn(AdServicesApiConsent.GIVEN).when(mConsentManagerMock).getConsent(any());
        doReturn(mPeriodicEncodingJobWorker)
                .when(() -> PeriodicEncodingJobWorker.getInstance(any()));
        doReturn(
                FluentFuture.from(
                        immediateFailedFuture(new InterruptedException("testing timeout"))))
                .when(mPeriodicEncodingJobWorker)
                .encodeProtectedSignals();
        doAnswer(
                unusedInvocation -> {
                    jobFinishedCountDown.countDown();
                    return null;
                })
                .when(mEncodingJobServiceSpy)
                .jobFinished(mJobParametersMock, false);

        assertTrue(mEncodingJobServiceSpy.onStartJob(mJobParametersMock));
        jobFinishedCountDown.await();

        ExtendedMockito.verify(() -> PeriodicEncodingJobWorker.getInstance(mEncodingJobServiceSpy));
        verify(mPeriodicEncodingJobWorker).encodeProtectedSignals();
        verify(mEncodingJobServiceSpy).jobFinished(mJobParametersMock, false);
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
                    public boolean getProtectedSignalsServiceKillSwitch() {
                        return false;
                    }

                    @Override
                    public boolean getGlobalKillSwitch() {
                        return false;
                    }
                };
        doReturn(flagsEnabledPeriodicEncoding).when(FlagsFactory::getFlags);
        doReturn(mConsentManagerMock).when(() -> ConsentManager.getInstance(any()));
        doReturn(AdServicesApiConsent.GIVEN).when(mConsentManagerMock).getConsent(any());
        doReturn(mPeriodicEncodingJobWorker)
                .when(() -> PeriodicEncodingJobWorker.getInstance(any()));

        doReturn(
                FluentFuture.from(
                        immediateFailedFuture(
                                new ExecutionException("testing timeout", null))))
                .when(mPeriodicEncodingJobWorker)
                .encodeProtectedSignals();
        doAnswer(
                unusedInvocation -> {
                    jobFinishedCountDown.countDown();
                    return null;
                })
                .when(mEncodingJobServiceSpy)
                .jobFinished(mJobParametersMock, false);
        doReturn(JOB_SCHEDULER).when(mEncodingJobServiceSpy).getSystemService(JobScheduler.class);

        assertTrue(mEncodingJobServiceSpy.onStartJob(mJobParametersMock));
        jobFinishedCountDown.await();

        ExtendedMockito.verify(() -> PeriodicEncodingJobWorker.getInstance(mEncodingJobServiceSpy));
        verify(mPeriodicEncodingJobWorker).encodeProtectedSignals();
        verify(mEncodingJobServiceSpy).jobFinished(mJobParametersMock, false);
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
        doReturn(flagsWithoutLogging).when(FlagsFactory::getFlags);

        assertTrue(mEncodingJobServiceSpy.onStopJob(mJobParametersMock));

        // Verify logging methods are not invoked.
        verify(mSpyLogger, never()).persistJobExecutionData(anyInt(), anyLong());
        verify(mSpyLogger, never()).logExecutionStats(anyInt(), anyLong(), anyInt(), anyInt());
    }

    @Test
    public void testOnStopJob_withLogging() {
        Flags flagsWithLogging =
                new Flags() {
                    @Override
                    public boolean getBackgroundJobsLoggingKillSwitch() {
                        return false;
                    }
                };
        doReturn(flagsWithLogging).when(FlagsFactory::getFlags);

        assertTrue(mEncodingJobServiceSpy.onStopJob(mJobParametersMock));

        // Verify logging methods are invoked.
        verify(mSpyLogger).logExecutionStats(anyInt(), anyLong(), anyInt(), anyInt());
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
                    public boolean getProtectedSignalsServiceKillSwitch() {
                        return false;
                    }

                    @Override
                    public boolean getGlobalKillSwitch() {
                        return false;
                    }
                };
        doCallRealMethod()
                .when(() -> PeriodicEncodingJobService.scheduleIfNeeded(any(), any(), eq(false)));

        PeriodicEncodingJobService.scheduleIfNeeded(CONTEXT, flagsDisabledPeriodicEncoding, false);

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
                    public boolean getProtectedSignalsServiceKillSwitch() {
                        return false;
                    }

                    @Override
                    public boolean getGlobalKillSwitch() {
                        return false;
                    }
                };
        doCallRealMethod()
                .when(() -> PeriodicEncodingJobService.scheduleIfNeeded(any(), any(), eq(false)));
        doNothing().when(() -> PeriodicEncodingJobService.schedule(any(), any()));

        PeriodicEncodingJobService.scheduleIfNeeded(CONTEXT, flagsEnabledPeriodicEncoding, false);

        ExtendedMockito.verify(() -> PeriodicEncodingJobService.schedule(any(), any()));
        verifyNoMoreInteractions(staticMockMarker(PeriodicEncodingJobWorker.class));
    }

    @Test
    public void testScheduleIfNeededSkippedAlreadyScheduled() {
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
                    public boolean getProtectedSignalsServiceKillSwitch() {
                        return false;
                    }

                    @Override
                    public boolean getGlobalKillSwitch() {
                        return false;
                    }
                };
        JobInfo existingJobInfo =
                new JobInfo.Builder(
                        PROTECTED_SIGNALS_PERIODIC_ENCODING_JOB_ID,
                        new ComponentName(CONTEXT, PeriodicEncodingJobService.class))
                        .setMinimumLatency(MINIMUM_SCHEDULING_DELAY_MS)
                        .build();
        JOB_SCHEDULER.schedule(existingJobInfo);
        assertNotNull(JOB_SCHEDULER.getPendingJob(PROTECTED_SIGNALS_PERIODIC_ENCODING_JOB_ID));

        doCallRealMethod()
                .when(() -> PeriodicEncodingJobService.scheduleIfNeeded(any(), any(), eq(false)));

        PeriodicEncodingJobService.scheduleIfNeeded(CONTEXT, flagsEnabledPeriodicEncoding, false);

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
                    public boolean getProtectedSignalsServiceKillSwitch() {
                        return false;
                    }

                    @Override
                    public boolean getGlobalKillSwitch() {
                        return false;
                    }
                };
        JobInfo existingJobInfo =
                new JobInfo.Builder(
                        PROTECTED_SIGNALS_PERIODIC_ENCODING_JOB_ID,
                        new ComponentName(CONTEXT, PeriodicEncodingJobService.class))
                        .setMinimumLatency(MINIMUM_SCHEDULING_DELAY_MS)
                        .build();
        JOB_SCHEDULER.schedule(existingJobInfo);
        assertNotNull(JOB_SCHEDULER.getPendingJob(PROTECTED_SIGNALS_PERIODIC_ENCODING_JOB_ID));

        doCallRealMethod()
                .when(() -> PeriodicEncodingJobService.scheduleIfNeeded(any(), any(), eq(true)));
        doNothing().when(() -> PeriodicEncodingJobService.schedule(any(), any()));

        PeriodicEncodingJobService.scheduleIfNeeded(CONTEXT, flagsEnabledPeriodicEncoding, true);

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
        PeriodicEncodingJobService.schedule(CONTEXT, flagsDisabledPeriodicEncoding);

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
        doReturn(flagsWithoutLogging).when(FlagsFactory::getFlags);

        testOnStartJobShouldDisableJobTrue();

        // Verify logging method is not invoked.
        verify(mSpyLogger, never()).logExecutionStats(anyInt(), anyLong(), anyInt(), anyInt());
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
        doReturn(flagsWithLogging).when(FlagsFactory::getFlags);

        testOnStartJobShouldDisableJobTrue();

        // Verify logging has not happened even though logging is enabled because this field is not
        // logged
        verify(mSpyLogger, never()).logExecutionStats(anyInt(), anyLong(), anyInt(), anyInt());
    }

    private void testOnStartJobUpdateTimeoutHandled() throws InterruptedException {
        CountDownLatch jobFinishedCountDown = new CountDownLatch(1);

        doReturn(mConsentManagerMock).when(() -> ConsentManager.getInstance(any()));
        doReturn(AdServicesApiConsent.GIVEN).when(mConsentManagerMock).getConsent(any());
        doReturn(mPeriodicEncodingJobWorker)
                .when(() -> PeriodicEncodingJobWorker.getInstance(any()));
        doReturn(FluentFuture.from(immediateFailedFuture(new TimeoutException("testing timeout"))))
                .when(mPeriodicEncodingJobWorker)
                .encodeProtectedSignals();
        doAnswer(
                unusedInvocation -> {
                    jobFinishedCountDown.countDown();
                    return null;
                })
                .when(mEncodingJobServiceSpy)
                .jobFinished(mJobParametersMock, false);

        assertTrue(mEncodingJobServiceSpy.onStartJob(mJobParametersMock));
        jobFinishedCountDown.await();

        ExtendedMockito.verify(() -> PeriodicEncodingJobWorker.getInstance(mEncodingJobServiceSpy));
        verify(mPeriodicEncodingJobWorker).encodeProtectedSignals();
        verify(mEncodingJobServiceSpy).jobFinished(mJobParametersMock, false);
        verifyNoMoreInteractions(staticMockMarker(PeriodicEncodingJobWorker.class));
    }

    private void testOnStartJobShouldDisableJobTrue() {
        doReturn(true)
                .when(
                        () ->
                                ServiceCompatUtils.shouldDisableExtServicesJobOnTPlus(
                                        any(Context.class)));
        doReturn(JOB_SCHEDULER).when(mEncodingJobServiceSpy).getSystemService(JobScheduler.class);
        doNothing().when(mEncodingJobServiceSpy).jobFinished(mJobParametersMock, false);

        // Schedule the job to assert after starting that the scheduled job has been cancelled
        JobInfo existingJobInfo =
                new JobInfo.Builder(
                        PROTECTED_SIGNALS_PERIODIC_ENCODING_JOB_ID,
                        new ComponentName(CONTEXT, PeriodicEncodingJobService.class))
                        .setMinimumLatency(MINIMUM_SCHEDULING_DELAY_MS)
                        .build();
        JOB_SCHEDULER.schedule(existingJobInfo);
        assertNotNull(JOB_SCHEDULER.getPendingJob(PROTECTED_SIGNALS_PERIODIC_ENCODING_JOB_ID));

        assertFalse(mEncodingJobServiceSpy.onStartJob(mJobParametersMock));

        assertNull(JOB_SCHEDULER.getPendingJob(PROTECTED_SIGNALS_PERIODIC_ENCODING_JOB_ID));
        verify(mPeriodicEncodingJobWorker, never()).encodeProtectedSignals();
        verify(mEncodingJobServiceSpy).jobFinished(mJobParametersMock, false);
        verifyNoMoreInteractions(staticMockMarker(PeriodicEncodingJobWorker.class));
    }

    private void testOnStartJobUpdateSuccess() throws InterruptedException {
        CountDownLatch jobFinishedCountDown = new CountDownLatch(1);

        doReturn(mConsentManagerMock).when(() -> ConsentManager.getInstance(any()));
        doReturn(AdServicesApiConsent.GIVEN)
                .when(mConsentManagerMock)
                .getConsent(AdServicesApiType.FLEDGE);
        doReturn(mPeriodicEncodingJobWorker)
                .when(() -> PeriodicEncodingJobWorker.getInstance(any()));
        doReturn(FluentFuture.from(immediateFuture(null)))
                .when(mPeriodicEncodingJobWorker)
                .encodeProtectedSignals();
        doAnswer(
                unusedInvocation -> {
                    jobFinishedCountDown.countDown();
                    return null;
                })
                .when(mEncodingJobServiceSpy)
                .jobFinished(mJobParametersMock, false);

        assertTrue(mEncodingJobServiceSpy.onStartJob(mJobParametersMock));
        jobFinishedCountDown.await();

        ExtendedMockito.verify(() -> PeriodicEncodingJobWorker.getInstance(mEncodingJobServiceSpy));
        verify(mPeriodicEncodingJobWorker).encodeProtectedSignals();
        verify(mEncodingJobServiceSpy).jobFinished(mJobParametersMock, false);
        verifyNoMoreInteractions(staticMockMarker(PeriodicEncodingJobWorker.class));
    }

    private void testOnStartJobFlagDisabled() {
        doReturn(JOB_SCHEDULER).when(mEncodingJobServiceSpy).getSystemService(JobScheduler.class);
        doNothing().when(mEncodingJobServiceSpy).jobFinished(mJobParametersMock, false);

        // Schedule the job to assert after starting that the scheduled job has been cancelled
        JobInfo existingJobInfo =
                new JobInfo.Builder(
                        PROTECTED_SIGNALS_PERIODIC_ENCODING_JOB_ID,
                        new ComponentName(CONTEXT, PeriodicEncodingJobService.class))
                        .setMinimumLatency(MINIMUM_SCHEDULING_DELAY_MS)
                        .build();
        JOB_SCHEDULER.schedule(existingJobInfo);
        assertNotNull(JOB_SCHEDULER.getPendingJob(PROTECTED_SIGNALS_PERIODIC_ENCODING_JOB_ID));

        assertFalse(mEncodingJobServiceSpy.onStartJob(mJobParametersMock));

        assertNull(JOB_SCHEDULER.getPendingJob(PROTECTED_SIGNALS_PERIODIC_ENCODING_JOB_ID));
        verify(mPeriodicEncodingJobWorker, never()).encodeProtectedSignals();
        verify(mEncodingJobServiceSpy).jobFinished(mJobParametersMock, false);
        verifyNoMoreInteractions(staticMockMarker(PeriodicEncodingJobWorker.class));
    }

    private void testOnStartJobConsentRevokedGaUxEnabled() {
        doReturn(mConsentManagerMock).when(() -> ConsentManager.getInstance(any()));
        doReturn(AdServicesApiConsent.REVOKED)
                .when(mConsentManagerMock)
                .getConsent(AdServicesApiType.FLEDGE);
        doReturn(JOB_SCHEDULER).when(mEncodingJobServiceSpy).getSystemService(JobScheduler.class);
        doNothing().when(mEncodingJobServiceSpy).jobFinished(mJobParametersMock, false);

        // Schedule the job to assert after starting that the scheduled job has been cancelled
        JobInfo existingJobInfo =
                new JobInfo.Builder(
                        PROTECTED_SIGNALS_PERIODIC_ENCODING_JOB_ID,
                        new ComponentName(CONTEXT, PeriodicEncodingJobService.class))
                        .setMinimumLatency(MINIMUM_SCHEDULING_DELAY_MS)
                        .build();
        JOB_SCHEDULER.schedule(existingJobInfo);
        assertNotNull(JOB_SCHEDULER.getPendingJob(PROTECTED_SIGNALS_PERIODIC_ENCODING_JOB_ID));

        assertFalse(mEncodingJobServiceSpy.onStartJob(mJobParametersMock));

        assertNull(JOB_SCHEDULER.getPendingJob(PROTECTED_SIGNALS_PERIODIC_ENCODING_JOB_ID));
        verify(mPeriodicEncodingJobWorker, never()).encodeProtectedSignals();
        verify(mEncodingJobServiceSpy).jobFinished(mJobParametersMock, false);
        verifyNoMoreInteractions(staticMockMarker(PeriodicEncodingJobWorker.class));
    }
}

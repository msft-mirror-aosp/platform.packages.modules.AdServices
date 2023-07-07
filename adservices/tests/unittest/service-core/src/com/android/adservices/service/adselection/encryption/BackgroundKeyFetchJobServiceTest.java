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

package com.android.adservices.service.adselection.encryption;

import static com.android.adservices.spe.AdservicesJobInfo.FLEDGE_AD_SELECTION_ENCRYPTION_KEY_FETCH_JOB;
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
import static org.mockito.ArgumentMatchers.anyBoolean;
import static org.mockito.ArgumentMatchers.anyInt;
import static org.mockito.ArgumentMatchers.anyLong;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.mock;
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

public class BackgroundKeyFetchJobServiceTest {

    private static final int FLEDGE_AD_SELECTION_ENCRYPTION_KEY_FETCH_JOB_ID =
            FLEDGE_AD_SELECTION_ENCRYPTION_KEY_FETCH_JOB.getJobId();
    private static final Context CONTEXT = ApplicationProvider.getApplicationContext();
    private static final JobScheduler JOB_SCHEDULER = CONTEXT.getSystemService(JobScheduler.class);
    // Set a minimum delay of 1 hour so scheduled jobs don't run immediately
    private static final long MINIMUM_SCHEDULING_DELAY_MS = 60L * 60L * 1000L;

    @Spy
    private final BackgroundKeyFetchJobService mBgFJobServiceSpy =
            new BackgroundKeyFetchJobService();

    private final Flags mFlagsWithEnabledBgFGaUxDisabled =
            new BackgroundKeyFetchJobServiceTest.FlagsWithEnabledBgFGaUxDisabled();
    private final Flags mFlagsWithDisabledBgF =
            new BackgroundKeyFetchJobServiceTest.FlagsWithDisabledBgF();
    private final Flags mFlagsWithAdSelectionDataKillSwitchOn =
            new BackgroundKeyFetchJobServiceTest.FlagsWithKillSwitchOn();
    private final Flags mFlagsWithAdSelectionDataKillSwitchOff =
            new BackgroundKeyFetchJobServiceTest.FlagsWithKillSwitchOff();
    @Mock private BackgroundKeyFetchWorker mBgFWorkerMock;
    @Mock private JobParameters mJobParametersMock;
    @Mock private ConsentManager mConsentManagerMock;
    @Mock StatsdAdServicesLogger mMockStatsdLogger;
    private AdservicesJobServiceLogger mSpyLogger;
    private MockitoSession mStaticMockSession = null;

    @Before
    public void setup() {
        // The actual scheduling of the job needs to be mocked out because the test application does
        // not have the required permissions to schedule the job with the constraints requested by
        // the BackgroundKeyFetchJobService, and adding them is non-trivial.
        mStaticMockSession =
                ExtendedMockito.mockitoSession()
                        .spyStatic(FlagsFactory.class)
                        .mockStatic(ConsentManager.class)
                        .spyStatic(BackgroundKeyFetchJobService.class)
                        .spyStatic(BackgroundKeyFetchWorker.class)
                        .spyStatic(AdservicesJobServiceLogger.class)
                        .mockStatic(ServiceCompatUtils.class)
                        .strictness(Strictness.LENIENT)
                        .initMocks(this)
                        .startMocking();

        Assume.assumeNotNull(JOB_SCHEDULER);
        assertNull(
                "Job already scheduled before setup!",
                JOB_SCHEDULER.getPendingJob(FLEDGE_AD_SELECTION_ENCRYPTION_KEY_FETCH_JOB_ID));

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
    public void teardown() {
        JOB_SCHEDULER.cancelAll();
        mStaticMockSession.finishMocking();
    }

    @Test
    public void testOnStartJobConsentRevokedGaUxDisabled()
            throws ExecutionException, InterruptedException, TimeoutException {
        doReturn(mFlagsWithEnabledBgFGaUxDisabled).when(FlagsFactory::getFlags);
        doReturn(mConsentManagerMock).when(() -> ConsentManager.getInstance(any()));
        doReturn(AdServicesApiConsent.REVOKED).when(mConsentManagerMock).getConsent();
        doReturn(JOB_SCHEDULER).when(mBgFJobServiceSpy).getSystemService(JobScheduler.class);
        doNothing().when(mBgFJobServiceSpy).jobFinished(mJobParametersMock, false);

        // Schedule the job to assert after starting that the scheduled job has been cancelled
        JobInfo existingJobInfo =
                new JobInfo.Builder(
                                FLEDGE_AD_SELECTION_ENCRYPTION_KEY_FETCH_JOB_ID,
                                new ComponentName(CONTEXT, BackgroundKeyFetchJobService.class))
                        .setMinimumLatency(MINIMUM_SCHEDULING_DELAY_MS)
                        .build();
        JOB_SCHEDULER.schedule(existingJobInfo);
        assertNotNull(JOB_SCHEDULER.getPendingJob(FLEDGE_AD_SELECTION_ENCRYPTION_KEY_FETCH_JOB_ID));

        assertFalse(mBgFJobServiceSpy.onStartJob(mJobParametersMock));

        assertNull(JOB_SCHEDULER.getPendingJob(FLEDGE_AD_SELECTION_ENCRYPTION_KEY_FETCH_JOB_ID));
        verify(mBgFWorkerMock, never()).runBackgroundKeyFetch();
        verify(mBgFJobServiceSpy).jobFinished(mJobParametersMock, false);
        verifyNoMoreInteractions(staticMockMarker(BackgroundKeyFetchWorker.class));
    }

    @Test
    public void testOnStartJobAdSelectionDataKillSwitchOn()
            throws ExecutionException, InterruptedException, TimeoutException {
        doReturn(mFlagsWithAdSelectionDataKillSwitchOn).when(FlagsFactory::getFlags);
        doReturn(JOB_SCHEDULER).when(mBgFJobServiceSpy).getSystemService(JobScheduler.class);
        doNothing().when(mBgFJobServiceSpy).jobFinished(mJobParametersMock, false);

        // Schedule the job to assert after starting that the scheduled job has been cancelled
        JobInfo existingJobInfo =
                new JobInfo.Builder(
                                FLEDGE_AD_SELECTION_ENCRYPTION_KEY_FETCH_JOB_ID,
                                new ComponentName(CONTEXT, BackgroundKeyFetchJobService.class))
                        .setMinimumLatency(MINIMUM_SCHEDULING_DELAY_MS)
                        .build();
        JOB_SCHEDULER.schedule(existingJobInfo);
        assertNotNull(JOB_SCHEDULER.getPendingJob(FLEDGE_AD_SELECTION_ENCRYPTION_KEY_FETCH_JOB_ID));

        assertFalse(mBgFJobServiceSpy.onStartJob(mJobParametersMock));

        assertNull(JOB_SCHEDULER.getPendingJob(FLEDGE_AD_SELECTION_ENCRYPTION_KEY_FETCH_JOB_ID));
        verify(mBgFWorkerMock, never()).runBackgroundKeyFetch();
        verify(mBgFJobServiceSpy).jobFinished(mJobParametersMock, false);
        verifyNoMoreInteractions(staticMockMarker(BackgroundKeyFetchWorker.class));
    }

    @Test
    public void testOnStartJobAdSelectionDataKillSwitchOff()
            throws ExecutionException, InterruptedException, TimeoutException {
        doReturn(mFlagsWithAdSelectionDataKillSwitchOff).when(FlagsFactory::getFlags);
        doReturn(mConsentManagerMock).when(() -> ConsentManager.getInstance(any()));
        doReturn(AdServicesApiConsent.GIVEN).when(mConsentManagerMock).getConsent();
        doReturn(mBgFWorkerMock).when(() -> BackgroundKeyFetchWorker.getInstance(any()));
        doReturn(FluentFuture.from(immediateFuture(null)))
                .when(mBgFWorkerMock)
                .runBackgroundKeyFetch();
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

        ExtendedMockito.verify(() -> BackgroundKeyFetchWorker.getInstance(mBgFJobServiceSpy));
        verify(mBgFWorkerMock).runBackgroundKeyFetch();
        verify(mBgFJobServiceSpy).jobFinished(mJobParametersMock, false);
        verifyNoMoreInteractions(staticMockMarker(BackgroundKeyFetchWorker.class));
    }

    @Test
    public void testOnStartJobUpdateSuccessdd()
            throws InterruptedException, ExecutionException, TimeoutException {
        Flags flagsWithEnabledBgFGaUxDisabledWithoutLogging =
                new BackgroundKeyFetchJobServiceTest.FlagsWithEnabledBgFGaUxEnabled();
        doReturn(flagsWithEnabledBgFGaUxDisabledWithoutLogging).when(FlagsFactory::getFlags);

        CountDownLatch jobFinishedCountDown = new CountDownLatch(1);

        doReturn(mConsentManagerMock).when(() -> ConsentManager.getInstance(any()));
        doReturn(AdServicesApiConsent.GIVEN).when(mConsentManagerMock).getConsent(any());
        doReturn(mBgFWorkerMock).when(() -> BackgroundKeyFetchWorker.getInstance(any()));
        doReturn(FluentFuture.from(immediateFuture(null)))
                .when(mBgFWorkerMock)
                .runBackgroundKeyFetch();
        doAnswer(
                unusedInvocation -> {
                    jobFinishedCountDown.countDown();
                    return null;
                })
                .when(mBgFJobServiceSpy)
                .jobFinished(mJobParametersMock, false);

        assertTrue(mBgFJobServiceSpy.onStartJob(mJobParametersMock));
        jobFinishedCountDown.await();

        ExtendedMockito.verify(() -> BackgroundKeyFetchWorker.getInstance(mBgFJobServiceSpy));
        verify(mBgFWorkerMock).runBackgroundKeyFetch();
        verify(mBgFJobServiceSpy).jobFinished(mJobParametersMock, false);
        verifyNoMoreInteractions(staticMockMarker(BackgroundKeyFetchWorker.class));
        // Verify logging methods are invoked.
        verify(mSpyLogger).recordOnStartJob(anyInt());
        verify(mSpyLogger).recordJobFinished(anyInt(), anyBoolean(), anyBoolean());
    }

    @Test
    public void testOnStartJobUpdateTimeoutHandled() throws InterruptedException {
        Flags flagsWithEnabledBgFGaUxDisabled =
                new BackgroundKeyFetchJobServiceTest.FlagsWithEnabledBgFGaUxDisabled();
        doReturn(flagsWithEnabledBgFGaUxDisabled).when(FlagsFactory::getFlags);

        CountDownLatch jobFinishedCountDown = new CountDownLatch(1);

        doReturn(mConsentManagerMock).when(() -> ConsentManager.getInstance(any()));
        doReturn(AdServicesApiConsent.GIVEN).when(mConsentManagerMock).getConsent();
        doReturn(mBgFWorkerMock).when(() -> BackgroundKeyFetchWorker.getInstance(any()));
        doReturn(FluentFuture.from(immediateFailedFuture(new TimeoutException("testing timeout"))))
                .when(mBgFWorkerMock)
                .runBackgroundKeyFetch();
        doAnswer(
                unusedInvocation -> {
                    jobFinishedCountDown.countDown();
                    return null;
                })
                .when(mBgFJobServiceSpy)
                .jobFinished(mJobParametersMock, false);

        assertTrue(mBgFJobServiceSpy.onStartJob(mJobParametersMock));
        jobFinishedCountDown.await();

        ExtendedMockito.verify(() -> BackgroundKeyFetchWorker.getInstance(mBgFJobServiceSpy));
        verify(mBgFWorkerMock).runBackgroundKeyFetch();
        verify(mBgFJobServiceSpy).jobFinished(mJobParametersMock, false);
        verifyNoMoreInteractions(staticMockMarker(BackgroundKeyFetchWorker.class));
    }

    @Test
    public void testOnStartJobUpdateExecutionExceptionHandled() throws InterruptedException {
        CountDownLatch jobFinishedCountDown = new CountDownLatch(1);

        doReturn(mFlagsWithEnabledBgFGaUxDisabled).when(FlagsFactory::getFlags);
        doReturn(mConsentManagerMock).when(() -> ConsentManager.getInstance(any()));
        doReturn(AdServicesApiConsent.GIVEN).when(mConsentManagerMock).getConsent();
        doReturn(mBgFWorkerMock).when(() -> BackgroundKeyFetchWorker.getInstance(any()));
        doReturn(
                        FluentFuture.from(
                                immediateFailedFuture(
                                        new ExecutionException("testing timeout", null))))
                .when(mBgFWorkerMock)
                .runBackgroundKeyFetch();
        doAnswer(
                unusedInvocation -> {
                    jobFinishedCountDown.countDown();
                    return null;
                })
                .when(mBgFJobServiceSpy)
                .jobFinished(mJobParametersMock, false);

        assertTrue(mBgFJobServiceSpy.onStartJob(mJobParametersMock));
        jobFinishedCountDown.await();

        ExtendedMockito.verify(() -> BackgroundKeyFetchWorker.getInstance(mBgFJobServiceSpy));
        verify(mBgFWorkerMock).runBackgroundKeyFetch();
        verify(mBgFJobServiceSpy).jobFinished(mJobParametersMock, false);
        verifyNoMoreInteractions(staticMockMarker(BackgroundKeyFetchWorker.class));
    }

    @Test
    public void testOnStopJobCallsStopWork() {
        Flags mockFlag = mock(Flags.class);
        doReturn(mockFlag).when(FlagsFactory::getFlags);

        doReturn(mBgFWorkerMock).when(() -> BackgroundKeyFetchWorker.getInstance(any()));
        doNothing().when(mBgFWorkerMock).stopWork();

        assertTrue(mBgFJobServiceSpy.onStopJob(mJobParametersMock));

        verify(mBgFWorkerMock).stopWork();
    }

    @Test
    public void testScheduleIfNeededFlagDisabled() {
        doCallRealMethod()
                .when(() -> BackgroundKeyFetchJobService.scheduleIfNeeded(any(), any(), eq(false)));

        BackgroundKeyFetchJobService.scheduleIfNeeded(CONTEXT, mFlagsWithDisabledBgF, false);

        ExtendedMockito.verify(() -> BackgroundKeyFetchJobService.schedule(any(), any()), never());
        verifyNoMoreInteractions(staticMockMarker(BackgroundKeyFetchWorker.class));
    }

    @Test
    public void testScheduleIfNeededSuccess() {
        doCallRealMethod()
                .when(() -> BackgroundKeyFetchJobService.scheduleIfNeeded(any(), any(), eq(false)));
        doNothing().when(() -> BackgroundKeyFetchJobService.schedule(any(), any()));

        BackgroundKeyFetchJobService.scheduleIfNeeded(
                CONTEXT, mFlagsWithEnabledBgFGaUxDisabled, false);

        ExtendedMockito.verify(() -> BackgroundKeyFetchJobService.schedule(any(), any()));
        verifyNoMoreInteractions(staticMockMarker(BackgroundKeyFetchWorker.class));
    }

    @Test
    public void testScheduleIfNeededSkippedAlreadyScheduled() {
        JobInfo existingJobInfo =
                new JobInfo.Builder(
                                FLEDGE_AD_SELECTION_ENCRYPTION_KEY_FETCH_JOB_ID,
                                new ComponentName(CONTEXT, BackgroundKeyFetchJobService.class))
                        .setMinimumLatency(MINIMUM_SCHEDULING_DELAY_MS)
                        .build();
        JOB_SCHEDULER.schedule(existingJobInfo);
        assertNotNull(JOB_SCHEDULER.getPendingJob(FLEDGE_AD_SELECTION_ENCRYPTION_KEY_FETCH_JOB_ID));

        doCallRealMethod()
                .when(() -> BackgroundKeyFetchJobService.scheduleIfNeeded(any(), any(), eq(false)));

        BackgroundKeyFetchJobService.scheduleIfNeeded(
                CONTEXT, mFlagsWithEnabledBgFGaUxDisabled, false);

        ExtendedMockito.verify(() -> BackgroundKeyFetchJobService.schedule(any(), any()), never());
        verifyNoMoreInteractions(staticMockMarker(BackgroundKeyFetchWorker.class));
    }

    @Test
    public void testScheduleIfNeededForceSuccess() {
        JobInfo existingJobInfo =
                new JobInfo.Builder(
                                FLEDGE_AD_SELECTION_ENCRYPTION_KEY_FETCH_JOB_ID,
                                new ComponentName(CONTEXT, BackgroundKeyFetchJobService.class))
                        .setMinimumLatency(MINIMUM_SCHEDULING_DELAY_MS)
                        .build();
        JOB_SCHEDULER.schedule(existingJobInfo);
        assertNotNull(JOB_SCHEDULER.getPendingJob(FLEDGE_AD_SELECTION_ENCRYPTION_KEY_FETCH_JOB_ID));

        doCallRealMethod()
                .when(() -> BackgroundKeyFetchJobService.scheduleIfNeeded(any(), any(), eq(true)));
        doNothing().when(() -> BackgroundKeyFetchJobService.schedule(any(), any()));

        BackgroundKeyFetchJobService.scheduleIfNeeded(
                CONTEXT, mFlagsWithEnabledBgFGaUxDisabled, true);

        ExtendedMockito.verify(() -> BackgroundKeyFetchJobService.schedule(any(), any()));
        verifyNoMoreInteractions(staticMockMarker(BackgroundKeyFetchWorker.class));
    }

    @Test
    public void testScheduleFlagDisabled() {
        BackgroundKeyFetchJobService.schedule(CONTEXT, mFlagsWithDisabledBgF);

        verifyNoMoreInteractions(staticMockMarker(BackgroundKeyFetchWorker.class));
    }

    @Test
    public void testOnStartJob_shouldDisableJobTrue()
            throws ExecutionException, InterruptedException, TimeoutException {
        Flags mockFlag = mock(Flags.class);
        // Logging killswitch is on.
        doReturn(mockFlag).when(FlagsFactory::getFlags);

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
                                FLEDGE_AD_SELECTION_ENCRYPTION_KEY_FETCH_JOB_ID,
                                new ComponentName(CONTEXT, BackgroundKeyFetchJobService.class))
                        .setMinimumLatency(MINIMUM_SCHEDULING_DELAY_MS)
                        .build();
        JOB_SCHEDULER.schedule(existingJobInfo);
        assertNotNull(JOB_SCHEDULER.getPendingJob(FLEDGE_AD_SELECTION_ENCRYPTION_KEY_FETCH_JOB_ID));

        assertFalse(mBgFJobServiceSpy.onStartJob(mJobParametersMock));

        assertNull(JOB_SCHEDULER.getPendingJob(FLEDGE_AD_SELECTION_ENCRYPTION_KEY_FETCH_JOB_ID));
        verify(mBgFWorkerMock, never()).runBackgroundKeyFetch();
        verify(mBgFJobServiceSpy).jobFinished(mJobParametersMock, false);
        verifyNoMoreInteractions(staticMockMarker(BackgroundKeyFetchWorker.class));
    }

    private static class FlagsWithEnabledBgFGaUxDisabled implements Flags {
        @Override
        public boolean getFledgeAuctionServerBackgroundKeyFetchJobEnabled() {
            return true;
        }

        @Override
        public boolean getGaUxFeatureEnabled() {
            return false;
        }

        @Override
        public boolean getGlobalKillSwitch() {
            return false;
        }

        @Override
        public boolean getFledgeAuctionServerKillSwitch() {
            return false;
        }
    }

    private static class FlagsWithEnabledBgFGaUxEnabled implements Flags {
        @Override
        public boolean getFledgeAuctionServerBackgroundKeyFetchJobEnabled() {
            return true;
        }

        @Override
        public boolean getGaUxFeatureEnabled() {
            return true;
        }

        @Override
        public boolean getGlobalKillSwitch() {
            return false;
        }

        @Override
        public boolean getFledgeAuctionServerKillSwitch() {
            return false;
        }
    }

    private static class FlagsWithDisabledBgF implements Flags {
        @Override
        public boolean getFledgeAuctionServerBackgroundKeyFetchJobEnabled() {
            return false;
        }

        @Override
        public long getFledgeAuctionServerBackgroundKeyFetchJobPeriodMs() {
            throw new IllegalStateException("This configured value should not be called");
        }

        @Override
        public long getFledgeAuctionServerBackgroundKeyFetchJobFlexMs() {
            throw new IllegalStateException("This configured value should not be called");
        }

        @Override
        public boolean getGlobalKillSwitch() {
            return false;
        }
    }

    private static class FlagsWithKillSwitchOn implements Flags {

        @Override
        public boolean getFledgeAuctionServerKillSwitch() {
            return true;
        }

        // For testing the corner case where the BgF is enabled but overall Ad Selection Data
        // kill switch is on
        @Override
        public boolean getFledgeAuctionServerBackgroundKeyFetchJobEnabled() {
            return true;
        }

        @Override
        public boolean getGlobalKillSwitch() {
            return false;
        }
    }

    private static class FlagsWithKillSwitchOff implements Flags {

        @Override
        public boolean getFledgeAuctionServerKillSwitch() {
            return false;
        }

        @Override
        public boolean getFledgeAuctionServerBackgroundKeyFetchJobEnabled() {
            return true;
        }

        @Override
        public boolean getGlobalKillSwitch() {
            return false;
        }
    }
}

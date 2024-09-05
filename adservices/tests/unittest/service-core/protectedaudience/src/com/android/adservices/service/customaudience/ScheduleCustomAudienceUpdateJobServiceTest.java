/*
 * Copyright (C) 2024 The Android Open Source Project
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

import static com.android.adservices.spe.AdServicesJobInfo.SCHEDULE_CUSTOM_AUDIENCE_UPDATE_BACKGROUND_JOB;
import static com.android.dx.mockito.inline.extended.ExtendedMockito.doAnswer;
import static com.android.dx.mockito.inline.extended.ExtendedMockito.doCallRealMethod;
import static com.android.dx.mockito.inline.extended.ExtendedMockito.doNothing;
import static com.android.dx.mockito.inline.extended.ExtendedMockito.doReturn;
import static com.android.dx.mockito.inline.extended.ExtendedMockito.staticMockMarker;

import static com.google.common.truth.Truth.assertThat;
import static com.google.common.util.concurrent.Futures.immediateFailedFuture;
import static com.google.common.util.concurrent.Futures.immediateFuture;

import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertNotNull;
import static org.junit.Assert.assertNull;
import static org.junit.Assert.assertTrue;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
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
@SpyStatic(ScheduleCustomAudienceUpdateJobService.class)
@SpyStatic(ScheduleCustomAudienceUpdateWorker.class)
@SpyStatic(AdServicesJobServiceLogger.class)
@MockStatic(ServiceCompatUtils.class)
public final class ScheduleCustomAudienceUpdateJobServiceTest extends AdServicesJobServiceTestCase {

    private static final int SCHEDULE_CUSTOM_AUDIENCE_UPDATE_BACKGROUND_JOB_ID =
            SCHEDULE_CUSTOM_AUDIENCE_UPDATE_BACKGROUND_JOB.getJobId();

    // Set a minimum delay of 1 hour so scheduled jobs don't run immediately
    private static final long MINIMUM_SCHEDULING_DELAY_MS = 60L * 60L * 1000L;
    private static final JobScheduler JOB_SCHEDULER = sContext.getSystemService(JobScheduler.class);

    @Spy
    private final ScheduleCustomAudienceUpdateJobService mUpdateServiceSpy =
            new ScheduleCustomAudienceUpdateJobService();

    @Mock private ScheduleCustomAudienceUpdateWorker mUpdateWorker;
    @Mock private JobParameters mJobParametersMock;
    @Mock private ConsentManager mConsentManagerMock;

    @Before
    public void setup() {
        Assume.assumeNotNull(JOB_SCHEDULER);
        // Reduces flake, in scenario the first test run encounters job already scheduled
        JOB_SCHEDULER.cancelAll();
        assertNull(
                "Job already scheduled before setup!",
                JOB_SCHEDULER.getPendingJob(SCHEDULE_CUSTOM_AUDIENCE_UPDATE_BACKGROUND_JOB_ID));
    }

    @After
    public void tearDown() {
        JOB_SCHEDULER.cancelAll();
    }

    @Test
    public void testOnStartJobFlagDisabled_withLogging() throws InterruptedException {
        Flags flagsWithScheduleUpdateDisabled =
                new Flags() {
                    @Override
                    public boolean getFledgeScheduleCustomAudienceUpdateEnabled() {
                        return false;
                    }

                    @Override
                    public long getFledgeScheduleCustomAudienceUpdateJobPeriodMs() {
                        throw new IllegalStateException(
                                "This configured value should not be called");
                    }

                    @Override
                    public long getFledgeScheduleCustomAudienceUpdateJobFlexMs() {
                        throw new IllegalStateException(
                                "This configured value should not be called");
                    }

                    @Override
                    public boolean getGlobalKillSwitch() {
                        return false;
                    }
                };
        mocker.mockGetFlags(flagsWithScheduleUpdateDisabled);
        AdServicesJobServiceLogger logger =
                mockAdServicesJobServiceLogger(mContext, flagsWithScheduleUpdateDisabled);
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

        doReturn(flagsWithGaUxDisabled).when(FlagsFactory::getFlags);
        doReturn(mConsentManagerMock).when(() -> ConsentManager.getInstance());
        doReturn(AdServicesApiConsent.REVOKED)
                .when(mConsentManagerMock)
                .getConsent(AdServicesApiType.FLEDGE);
        doReturn(JOB_SCHEDULER).when(mUpdateServiceSpy).getSystemService(JobScheduler.class);
        doNothing().when(mUpdateServiceSpy).jobFinished(mJobParametersMock, false);

        // Schedule the job to assert after starting that the scheduled job has been cancelled
        JobInfo existingJobInfo =
                new JobInfo.Builder(
                                SCHEDULE_CUSTOM_AUDIENCE_UPDATE_BACKGROUND_JOB_ID,
                                new ComponentName(
                                        sContext, ScheduleCustomAudienceUpdateJobService.class))
                        .setMinimumLatency(MINIMUM_SCHEDULING_DELAY_MS)
                        .build();
        JOB_SCHEDULER.schedule(existingJobInfo);
        assertNotNull(
                JOB_SCHEDULER.getPendingJob(SCHEDULE_CUSTOM_AUDIENCE_UPDATE_BACKGROUND_JOB_ID));

        assertFalse(mUpdateServiceSpy.onStartJob(mJobParametersMock));

        assertNull(JOB_SCHEDULER.getPendingJob(SCHEDULE_CUSTOM_AUDIENCE_UPDATE_BACKGROUND_JOB_ID));
        verify(mUpdateWorker, never()).updateCustomAudience();
        verify(mUpdateServiceSpy).jobFinished(mJobParametersMock, false);
        verifyNoMoreInteractions(staticMockMarker(ScheduleCustomAudienceUpdateWorker.class));
    }

    @Test
    public void testOnStartJobConsentRevokedGaUxEnabled_withLogging() throws InterruptedException {
        Flags flagsWithGaUxEnabled =
                new Flags() {
                    @Override
                    public boolean getGaUxFeatureEnabled() {
                        return true;
                    }

                    @Override
                    public boolean getFledgeScheduleCustomAudienceUpdateEnabled() {
                        return true;
                    }

                    @Override
                    public boolean getGlobalKillSwitch() {
                        return false;
                    }
                };
        mocker.mockGetFlags(flagsWithGaUxEnabled);
        AdServicesJobServiceLogger logger =
                mockAdServicesJobServiceLogger(mContext, flagsWithGaUxEnabled);
        JobServiceLoggingCallback callback = syncLogExecutionStats(logger);

        testOnStartJobConsentRevokedGaUxEnabled();

        // Verify logging has happened
        verifyBackgroundJobsSkipLogged(logger, callback);
    }

    @Test
    public void testOnStartJobScheduleCustomAudienceUpdateKillSwitchOn() {
        Flags flagsWithKillSwitchOn =
                new Flags() {
                    @Override
                    public boolean getFledgeScheduleCustomAudienceUpdateEnabled() {
                        return false;
                    }

                    @Override
                    public boolean getGlobalKillSwitch() {
                        return false;
                    }
                };
        doReturn(flagsWithKillSwitchOn).when(FlagsFactory::getFlags);
        doReturn(JOB_SCHEDULER).when(mUpdateServiceSpy).getSystemService(JobScheduler.class);
        doNothing().when(mUpdateServiceSpy).jobFinished(mJobParametersMock, false);

        // Schedule the job to assert after starting that the scheduled job has been cancelled
        JobInfo existingJobInfo =
                new JobInfo.Builder(
                                SCHEDULE_CUSTOM_AUDIENCE_UPDATE_BACKGROUND_JOB_ID,
                                new ComponentName(
                                        sContext, ScheduleCustomAudienceUpdateJobService.class))
                        .setMinimumLatency(MINIMUM_SCHEDULING_DELAY_MS)
                        .build();
        JOB_SCHEDULER.schedule(existingJobInfo);
        assertNotNull(
                JOB_SCHEDULER.getPendingJob(SCHEDULE_CUSTOM_AUDIENCE_UPDATE_BACKGROUND_JOB_ID));

        assertFalse(mUpdateServiceSpy.onStartJob(mJobParametersMock));

        assertNull(JOB_SCHEDULER.getPendingJob(SCHEDULE_CUSTOM_AUDIENCE_UPDATE_BACKGROUND_JOB_ID));
        verify(mUpdateWorker, never()).updateCustomAudience();
        verify(mUpdateServiceSpy).jobFinished(mJobParametersMock, false);
        verifyNoMoreInteractions(staticMockMarker(ScheduleCustomAudienceUpdateWorker.class));
    }

    @Test
    public void testOnStartJobScheduleCustomAudienceUpdateKillSwitchOff()
            throws InterruptedException {
        Flags flagsWithKillSwitchOff =
                new Flags() {
                    @Override
                    public boolean getGaUxFeatureEnabled() {
                        return true;
                    }

                    @Override
                    public boolean getFledgeScheduleCustomAudienceUpdateEnabled() {
                        return true;
                    }

                    @Override
                    public boolean getGlobalKillSwitch() {
                        return false;
                    }
                };
        doReturn(flagsWithKillSwitchOff).when(FlagsFactory::getFlags);
        doReturn(mConsentManagerMock).when(() -> ConsentManager.getInstance());
        doReturn(AdServicesApiConsent.GIVEN)
                .when(mConsentManagerMock)
                .getConsent(AdServicesApiType.FLEDGE);
        doReturn(mUpdateWorker).when(() -> ScheduleCustomAudienceUpdateWorker.getInstance(any()));
        doReturn(FluentFuture.from(immediateFuture(null)))
                .when(mUpdateWorker)
                .updateCustomAudience();
        CountDownLatch jobFinishedCountDown = new CountDownLatch(1);

        doAnswer(
                        unusedInvocation -> {
                            jobFinishedCountDown.countDown();
                            return null;
                        })
                .when(mUpdateServiceSpy)
                .jobFinished(mJobParametersMock, false);

        assertTrue(mUpdateServiceSpy.onStartJob(mJobParametersMock));
        jobFinishedCountDown.await();

        ExtendedMockito.verify(
                () -> ScheduleCustomAudienceUpdateWorker.getInstance(mUpdateServiceSpy));
        verify(mUpdateWorker).updateCustomAudience();
        verify(mUpdateServiceSpy).jobFinished(mJobParametersMock, false);
        verifyNoMoreInteractions(staticMockMarker(ScheduleCustomAudienceUpdateWorker.class));
    }

    @Test
    public void testOnStartJobUpdateSuccess_withLogging() throws InterruptedException {
        Flags flags =
                new Flags() {
                    @Override
                    public boolean getGaUxFeatureEnabled() {
                        return true;
                    }

                    @Override
                    public boolean getFledgeScheduleCustomAudienceUpdateEnabled() {
                        return true;
                    }

                    @Override
                    public boolean getGlobalKillSwitch() {
                        return false;
                    }
                };
        mocker.mockGetFlags(flags);
        AdServicesJobServiceLogger logger = mockAdServicesJobServiceLogger(mContext, flags);
        JobServiceLoggingCallback onStartJobCallback = syncPersistJobExecutionData(logger);
        JobServiceLoggingCallback onJobDoneCallback = syncLogExecutionStats(logger);

        testOnStartJobUpdateSuccess();

        verifyJobFinishedLogged(logger, onStartJobCallback, onJobDoneCallback);
    }

    @Test
    public void testOnStartJobUpdateTimeoutHandled_withLogging() throws InterruptedException {
        Flags flags =
                new Flags() {
                    @Override
                    public boolean getGaUxFeatureEnabled() {
                        return true;
                    }

                    @Override
                    public boolean getFledgeScheduleCustomAudienceUpdateEnabled() {
                        return true;
                    }

                    @Override
                    public boolean getGlobalKillSwitch() {
                        return false;
                    }
                };
        mocker.mockGetFlags(flags);
        AdServicesJobServiceLogger logger = mockAdServicesJobServiceLogger(mContext, flags);
        JobServiceLoggingCallback onStartJobCallback = syncPersistJobExecutionData(logger);
        JobServiceLoggingCallback onJobDoneCallback = syncLogExecutionStats(logger);

        testOnStartJobUpdateTimeoutHandled();

        verifyJobFinishedLogged(logger, onStartJobCallback, onJobDoneCallback);
    }

    @Test
    public void testOnStartJobUpdateInterruptedHandled() throws InterruptedException {
        CountDownLatch jobFinishedCountDown = new CountDownLatch(1);

        Flags flagsEnabledScheduleUpdate =
                new Flags() {
                    @Override
                    public boolean getGaUxFeatureEnabled() {
                        return true;
                    }

                    @Override
                    public boolean getFledgeScheduleCustomAudienceUpdateEnabled() {
                        return true;
                    }

                    @Override
                    public boolean getGlobalKillSwitch() {
                        return false;
                    }
                };
        doReturn(flagsEnabledScheduleUpdate).when(FlagsFactory::getFlags);
        doReturn(mConsentManagerMock).when(() -> ConsentManager.getInstance());
        doReturn(AdServicesApiConsent.GIVEN).when(mConsentManagerMock).getConsent(any());
        doReturn(mUpdateWorker).when(() -> ScheduleCustomAudienceUpdateWorker.getInstance(any()));
        doReturn(
                        FluentFuture.from(
                                immediateFailedFuture(new InterruptedException("testing timeout"))))
                .when(mUpdateWorker)
                .updateCustomAudience();
        doAnswer(
                        unusedInvocation -> {
                            jobFinishedCountDown.countDown();
                            return null;
                        })
                .when(mUpdateServiceSpy)
                .jobFinished(mJobParametersMock, false);

        assertTrue(mUpdateServiceSpy.onStartJob(mJobParametersMock));
        jobFinishedCountDown.await();

        ExtendedMockito.verify(
                () -> ScheduleCustomAudienceUpdateWorker.getInstance(mUpdateServiceSpy));
        verify(mUpdateWorker).updateCustomAudience();
        verify(mUpdateServiceSpy).jobFinished(mJobParametersMock, false);
        verifyNoMoreInteractions(staticMockMarker(ScheduleCustomAudienceUpdateWorker.class));
    }

    @Test
    public void testOnStartJobUpdateExecutionExceptionHandled() throws InterruptedException {
        CountDownLatch jobFinishedCountDown = new CountDownLatch(1);

        Flags flagsEnabledScheduleUpdate =
                new Flags() {
                    @Override
                    public boolean getGaUxFeatureEnabled() {
                        return true;
                    }

                    @Override
                    public boolean getFledgeScheduleCustomAudienceUpdateEnabled() {
                        return true;
                    }

                    @Override
                    public boolean getGlobalKillSwitch() {
                        return false;
                    }
                };
        doReturn(flagsEnabledScheduleUpdate).when(FlagsFactory::getFlags);
        doReturn(mConsentManagerMock).when(() -> ConsentManager.getInstance());
        doReturn(AdServicesApiConsent.GIVEN).when(mConsentManagerMock).getConsent(any());
        doReturn(mUpdateWorker).when(() -> ScheduleCustomAudienceUpdateWorker.getInstance(any()));

        doReturn(
                        FluentFuture.from(
                                immediateFailedFuture(
                                        new ExecutionException("testing timeout", null))))
                .when(mUpdateWorker)
                .updateCustomAudience();
        doAnswer(
                        unusedInvocation -> {
                            jobFinishedCountDown.countDown();
                            return null;
                        })
                .when(mUpdateServiceSpy)
                .jobFinished(mJobParametersMock, false);
        doReturn(JOB_SCHEDULER).when(mUpdateServiceSpy).getSystemService(JobScheduler.class);

        assertTrue(mUpdateServiceSpy.onStartJob(mJobParametersMock));
        jobFinishedCountDown.await();

        ExtendedMockito.verify(
                () -> ScheduleCustomAudienceUpdateWorker.getInstance(mUpdateServiceSpy));
        verify(mUpdateWorker).updateCustomAudience();
        verify(mUpdateServiceSpy).jobFinished(mJobParametersMock, false);
        verifyNoMoreInteractions(staticMockMarker(ScheduleCustomAudienceUpdateWorker.class));
    }

    @Test
    public void testOnStopJob() throws InterruptedException {
        AdServicesJobServiceLogger logger = mockAdServicesJobServiceLogger(mContext);
        JobServiceLoggingCallback callback = syncLogExecutionStats(logger);

        doReturn(mUpdateWorker).when(() -> ScheduleCustomAudienceUpdateWorker.getInstance(any()));
        doNothing().when(mUpdateWorker).stopWork();
        assertTrue(mUpdateServiceSpy.onStopJob(mJobParametersMock));
        verify(mUpdateWorker).stopWork();

        verifyOnStopJobLogged(logger, callback);
    }

    @Test
    public void testScheduleIfNeededSuccess() {
        Flags flagsEnabledScheduleUpdate =
                new Flags() {
                    @Override
                    public boolean getGaUxFeatureEnabled() {
                        return true;
                    }

                    @Override
                    public boolean getFledgeScheduleCustomAudienceUpdateEnabled() {
                        return true;
                    }

                    @Override
                    public boolean getGlobalKillSwitch() {
                        return false;
                    }
                };
        doCallRealMethod()
                .when(
                        () ->
                                ScheduleCustomAudienceUpdateJobService.scheduleIfNeeded(
                                        any(), any(), eq(false)));
        doNothing().when(() -> ScheduleCustomAudienceUpdateJobService.schedule(any(), any()));

        ScheduleCustomAudienceUpdateJobService.scheduleIfNeeded(
                sContext, flagsEnabledScheduleUpdate, false);

        ExtendedMockito.verify(() -> ScheduleCustomAudienceUpdateJobService.schedule(any(), any()));
        verifyNoMoreInteractions(staticMockMarker(ScheduleCustomAudienceUpdateWorker.class));
    }

    @Test
    public void testScheduleIfNeededSkippedAlreadyScheduled() {
        Flags flagsEnabledScheduleUpdate =
                new Flags() {
                    @Override
                    public boolean getGaUxFeatureEnabled() {
                        return true;
                    }

                    @Override
                    public boolean getFledgeScheduleCustomAudienceUpdateEnabled() {
                        return true;
                    }

                    @Override
                    public boolean getGlobalKillSwitch() {
                        return false;
                    }
                };
        JobInfo existingJobInfo =
                new JobInfo.Builder(
                                SCHEDULE_CUSTOM_AUDIENCE_UPDATE_BACKGROUND_JOB_ID,
                                new ComponentName(
                                        sContext, ScheduleCustomAudienceUpdateJobService.class))
                        .setMinimumLatency(MINIMUM_SCHEDULING_DELAY_MS)
                        .build();
        JOB_SCHEDULER.schedule(existingJobInfo);
        assertNotNull(
                JOB_SCHEDULER.getPendingJob(SCHEDULE_CUSTOM_AUDIENCE_UPDATE_BACKGROUND_JOB_ID));

        doCallRealMethod()
                .when(
                        () ->
                                ScheduleCustomAudienceUpdateJobService.scheduleIfNeeded(
                                        any(), any(), eq(false)));

        ScheduleCustomAudienceUpdateJobService.scheduleIfNeeded(
                sContext, flagsEnabledScheduleUpdate, false);

        ExtendedMockito.verify(
                () -> ScheduleCustomAudienceUpdateJobService.schedule(any(), any()), never());
        verifyNoMoreInteractions(staticMockMarker(ScheduleCustomAudienceUpdateWorker.class));
    }

    @Test
    public void testScheduleIfNeededForceSuccess() {
        Flags flagsEnabledScheduleUpdate =
                new Flags() {
                    @Override
                    public boolean getGaUxFeatureEnabled() {
                        return true;
                    }

                    @Override
                    public boolean getFledgeScheduleCustomAudienceUpdateEnabled() {
                        return true;
                    }

                    @Override
                    public boolean getGlobalKillSwitch() {
                        return false;
                    }
                };
        JobInfo existingJobInfo =
                new JobInfo.Builder(
                                SCHEDULE_CUSTOM_AUDIENCE_UPDATE_BACKGROUND_JOB_ID,
                                new ComponentName(
                                        sContext, ScheduleCustomAudienceUpdateJobService.class))
                        .setMinimumLatency(MINIMUM_SCHEDULING_DELAY_MS)
                        .build();
        JOB_SCHEDULER.schedule(existingJobInfo);
        assertNotNull(
                JOB_SCHEDULER.getPendingJob(SCHEDULE_CUSTOM_AUDIENCE_UPDATE_BACKGROUND_JOB_ID));

        doCallRealMethod()
                .when(
                        () ->
                                ScheduleCustomAudienceUpdateJobService.scheduleIfNeeded(
                                        any(), any(), eq(true)));
        doNothing().when(() -> ScheduleCustomAudienceUpdateJobService.schedule(any(), any()));

        ScheduleCustomAudienceUpdateJobService.scheduleIfNeeded(
                sContext, flagsEnabledScheduleUpdate, true);

        ExtendedMockito.verify(() -> ScheduleCustomAudienceUpdateJobService.schedule(any(), any()));
        verifyNoMoreInteractions(staticMockMarker(ScheduleCustomAudienceUpdateWorker.class));
    }

    @Test
    public void testScheduleFlagDisabled() {
        Flags flagsDisabledScheduleUpdate =
                new Flags() {
                    @Override
                    public boolean getGaUxFeatureEnabled() {
                        return true;
                    }

                    @Override
                    public boolean getFledgeScheduleCustomAudienceUpdateEnabled() {
                        return false;
                    }
                };
        ScheduleCustomAudienceUpdateJobService.schedule(sContext, flagsDisabledScheduleUpdate);

        verifyNoMoreInteractions(staticMockMarker(ScheduleCustomAudienceUpdateWorker.class));
    }

    @Test
    public void testOnStartJobShouldDisableJobTrue() {
        AdServicesJobServiceLogger logger = mockAdServicesJobServiceLogger(mContext);

        doReturn(true)
                .when(
                        () ->
                                ServiceCompatUtils.shouldDisableExtServicesJobOnTPlus(
                                        any(Context.class)));
        doReturn(JOB_SCHEDULER).when(mUpdateServiceSpy).getSystemService(JobScheduler.class);
        doNothing().when(mUpdateServiceSpy).jobFinished(mJobParametersMock, false);

        // Schedule the job to assert after starting that the scheduled job has been cancelled
        JobInfo existingJobInfo =
                new JobInfo.Builder(
                                SCHEDULE_CUSTOM_AUDIENCE_UPDATE_BACKGROUND_JOB_ID,
                                new ComponentName(
                                        mContext, ScheduleCustomAudienceUpdateJobService.class))
                        .setMinimumLatency(MINIMUM_SCHEDULING_DELAY_MS)
                        .build();
        JOB_SCHEDULER.schedule(existingJobInfo);
        assertThat(JOB_SCHEDULER.getPendingJob(SCHEDULE_CUSTOM_AUDIENCE_UPDATE_BACKGROUND_JOB_ID))
                .isNotNull();

        assertThat(mUpdateServiceSpy.onStartJob(mJobParametersMock)).isFalse();

        assertThat(JOB_SCHEDULER.getPendingJob(SCHEDULE_CUSTOM_AUDIENCE_UPDATE_BACKGROUND_JOB_ID))
                .isNull();
        verify(mUpdateWorker, never()).updateCustomAudience();
        verify(mUpdateServiceSpy).jobFinished(mJobParametersMock, false);
        verifyNoMoreInteractions(staticMockMarker(ScheduleCustomAudienceUpdateWorker.class));

        // Verify logging has not happened even though logging is enabled because this field is not
        // logged
        verifyLoggingNotHappened(logger);
    }

    private void testOnStartJobUpdateTimeoutHandled() throws InterruptedException {
        CountDownLatch jobFinishedCountDown = new CountDownLatch(1);

        doReturn(mConsentManagerMock).when(() -> ConsentManager.getInstance());
        doReturn(AdServicesApiConsent.GIVEN).when(mConsentManagerMock).getConsent(any());
        doReturn(mUpdateWorker).when(() -> ScheduleCustomAudienceUpdateWorker.getInstance(any()));
        doReturn(FluentFuture.from(immediateFailedFuture(new TimeoutException("testing timeout"))))
                .when(mUpdateWorker)
                .updateCustomAudience();
        doAnswer(
                        unusedInvocation -> {
                            jobFinishedCountDown.countDown();
                            return null;
                        })
                .when(mUpdateServiceSpy)
                .jobFinished(mJobParametersMock, false);

        assertTrue(mUpdateServiceSpy.onStartJob(mJobParametersMock));
        jobFinishedCountDown.await();

        ExtendedMockito.verify(
                () -> ScheduleCustomAudienceUpdateWorker.getInstance(mUpdateServiceSpy));
        verify(mUpdateWorker).updateCustomAudience();
        verify(mUpdateServiceSpy).jobFinished(mJobParametersMock, false);
        verifyNoMoreInteractions(staticMockMarker(ScheduleCustomAudienceUpdateWorker.class));
    }

    private void testOnStartJobUpdateSuccess() throws InterruptedException {
        CountDownLatch jobFinishedCountDown = new CountDownLatch(1);

        doReturn(mConsentManagerMock).when(() -> ConsentManager.getInstance());
        doReturn(AdServicesApiConsent.GIVEN)
                .when(mConsentManagerMock)
                .getConsent(AdServicesApiType.FLEDGE);
        doReturn(mUpdateWorker).when(() -> ScheduleCustomAudienceUpdateWorker.getInstance(any()));
        doReturn(FluentFuture.from(immediateFuture(null)))
                .when(mUpdateWorker)
                .updateCustomAudience();
        doAnswer(
                        unusedInvocation -> {
                            jobFinishedCountDown.countDown();
                            return null;
                        })
                .when(mUpdateServiceSpy)
                .jobFinished(mJobParametersMock, false);

        assertTrue(mUpdateServiceSpy.onStartJob(mJobParametersMock));
        jobFinishedCountDown.await();

        ExtendedMockito.verify(
                () -> ScheduleCustomAudienceUpdateWorker.getInstance(mUpdateServiceSpy));
        verify(mUpdateWorker).updateCustomAudience();
        verify(mUpdateServiceSpy).jobFinished(mJobParametersMock, false);
        verifyNoMoreInteractions(staticMockMarker(ScheduleCustomAudienceUpdateWorker.class));
    }

    private void testOnStartJobFlagDisabled() {
        doReturn(JOB_SCHEDULER).when(mUpdateServiceSpy).getSystemService(JobScheduler.class);
        doNothing().when(mUpdateServiceSpy).jobFinished(mJobParametersMock, false);

        // Schedule the job to assert after starting that the scheduled job has been cancelled
        JobInfo existingJobInfo =
                new JobInfo.Builder(
                                SCHEDULE_CUSTOM_AUDIENCE_UPDATE_BACKGROUND_JOB_ID,
                                new ComponentName(
                                        sContext, ScheduleCustomAudienceUpdateJobService.class))
                        .setMinimumLatency(MINIMUM_SCHEDULING_DELAY_MS)
                        .build();
        JOB_SCHEDULER.schedule(existingJobInfo);
        assertNotNull(
                JOB_SCHEDULER.getPendingJob(SCHEDULE_CUSTOM_AUDIENCE_UPDATE_BACKGROUND_JOB_ID));

        assertFalse(mUpdateServiceSpy.onStartJob(mJobParametersMock));

        assertNull(JOB_SCHEDULER.getPendingJob(SCHEDULE_CUSTOM_AUDIENCE_UPDATE_BACKGROUND_JOB_ID));
        verify(mUpdateWorker, never()).updateCustomAudience();
        verify(mUpdateServiceSpy).jobFinished(mJobParametersMock, false);
        verifyNoMoreInteractions(staticMockMarker(ScheduleCustomAudienceUpdateWorker.class));
    }

    private void testOnStartJobConsentRevokedGaUxEnabled() {
        doReturn(mConsentManagerMock).when(() -> ConsentManager.getInstance());
        doReturn(AdServicesApiConsent.REVOKED)
                .when(mConsentManagerMock)
                .getConsent(AdServicesApiType.FLEDGE);
        doReturn(JOB_SCHEDULER).when(mUpdateServiceSpy).getSystemService(JobScheduler.class);
        doNothing().when(mUpdateServiceSpy).jobFinished(mJobParametersMock, false);

        // Schedule the job to assert after starting that the scheduled job has been cancelled
        JobInfo existingJobInfo =
                new JobInfo.Builder(
                                SCHEDULE_CUSTOM_AUDIENCE_UPDATE_BACKGROUND_JOB_ID,
                                new ComponentName(
                                        sContext, ScheduleCustomAudienceUpdateJobService.class))
                        .setMinimumLatency(MINIMUM_SCHEDULING_DELAY_MS)
                        .build();
        JOB_SCHEDULER.schedule(existingJobInfo);
        assertNotNull(
                JOB_SCHEDULER.getPendingJob(SCHEDULE_CUSTOM_AUDIENCE_UPDATE_BACKGROUND_JOB_ID));

        assertFalse(mUpdateServiceSpy.onStartJob(mJobParametersMock));

        assertNull(JOB_SCHEDULER.getPendingJob(SCHEDULE_CUSTOM_AUDIENCE_UPDATE_BACKGROUND_JOB_ID));
        verify(mUpdateWorker, never()).updateCustomAudience();
        verify(mUpdateServiceSpy).jobFinished(mJobParametersMock, false);
        verifyNoMoreInteractions(staticMockMarker(ScheduleCustomAudienceUpdateWorker.class));
    }
}

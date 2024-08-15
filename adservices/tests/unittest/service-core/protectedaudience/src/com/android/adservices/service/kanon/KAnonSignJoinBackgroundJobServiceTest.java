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

package com.android.adservices.service.kanon;

import static com.android.adservices.mockito.MockitoExpectations.syncLogExecutionStats;
import static com.android.adservices.mockito.MockitoExpectations.verifyBackgroundJobsSkipLogged;
import static com.android.adservices.mockito.MockitoExpectations.verifyOnJobFinishedLogged;
import static com.android.adservices.spe.AdServicesJobInfo.FLEDGE_KANON_SIGN_JOIN_BACKGROUND_JOB;
import static com.android.dx.mockito.inline.extended.ExtendedMockito.doAnswer;
import static com.android.dx.mockito.inline.extended.ExtendedMockito.doCallRealMethod;
import static com.android.dx.mockito.inline.extended.ExtendedMockito.doNothing;
import static com.android.dx.mockito.inline.extended.ExtendedMockito.doReturn;

import static com.google.common.truth.Truth.assertThat;
import static com.google.common.util.concurrent.Futures.immediateFuture;

import static junit.framework.Assert.assertNull;

import static org.junit.Assert.assertNotNull;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;

import android.app.job.JobInfo;
import android.app.job.JobParameters;
import android.app.job.JobScheduler;
import android.content.ComponentName;

import com.android.adservices.common.AdServicesJobServiceTestCase;
import com.android.adservices.service.Flags;
import com.android.adservices.service.FlagsFactory;
import com.android.adservices.service.common.compat.ServiceCompatUtils;
import com.android.adservices.service.consent.AdServicesApiConsent;
import com.android.adservices.service.consent.ConsentManager;
import com.android.adservices.shared.testing.JobServiceLoggingCallback;
import com.android.adservices.spe.AdServicesJobServiceLogger;
import com.android.dx.mockito.inline.extended.ExtendedMockito;
import com.android.modules.utils.testing.ExtendedMockitoRule.SpyStatic;

import com.google.common.util.concurrent.FluentFuture;

import org.junit.After;
import org.junit.Before;
import org.junit.Test;
import org.mockito.Mock;
import org.mockito.Spy;

import java.util.concurrent.CountDownLatch;

@SpyStatic(FlagsFactory.class)
@SpyStatic(KAnonSignJoinBackgroundJobWorker.class)
@SpyStatic(KAnonSignJoinBackgroundJobService.class)
@SpyStatic(AdServicesJobServiceLogger.class)
@SpyStatic(ConsentManager.class)
@SpyStatic(ServiceCompatUtils.class)
public final class KAnonSignJoinBackgroundJobServiceTest extends AdServicesJobServiceTestCase {
    private static final JobScheduler JOB_SCHEDULER = sContext.getSystemService(JobScheduler.class);
    private static final int FLEDGE_KANON_SIGN_JOIN_BACKGROUND_JOB_ID =
            FLEDGE_KANON_SIGN_JOIN_BACKGROUND_JOB.getJobId();
    // Set a minimum delay of 1 hour so scheduled jobs don't run immediately
    private static final long MINIMUM_SCHEDULING_DELAY_MS = 60L * 60L * 1000L;

    @Spy
    private KAnonSignJoinBackgroundJobService mKAnonSignJoinBackgroundJobService =
            new KAnonSignJoinBackgroundJobService();

    @Mock private KAnonSignJoinBackgroundJobWorker mKAnonSignJoinBackgroundJobWorkerMock;
    @Mock private JobParameters mJobParametersMock;
    @Mock private ConsentManager mConsentManagerMock;

    @Before
    public void setup() {
        doReturn(JOB_SCHEDULER)
                .when(mKAnonSignJoinBackgroundJobService)
                .getSystemService(JobScheduler.class);
    }

    @After
    public void tearDown() {
        JOB_SCHEDULER.cancelAll();
    }

    @Test
    public void onStartJob_jobStartsAndJobFinishedLogged() throws Exception {
        KAnonSignJoinBackgroundJobServiceTestFlags testFlags =
                new KAnonSignJoinBackgroundJobServiceTestFlags(10000, false, true);
        mocker.mockGetFlags(testFlags);
        doReturn(mConsentManagerMock).when(() -> ConsentManager.getInstance());
        doReturn(AdServicesApiConsent.GIVEN).when(mConsentManagerMock).getConsent(any());
        doReturn(mKAnonSignJoinBackgroundJobWorkerMock)
                .when(() -> KAnonSignJoinBackgroundJobWorker.getInstance(any()));
        doReturn(false).when(() -> ServiceCompatUtils.shouldDisableExtServicesJobOnTPlus(any()));
        doReturn(FluentFuture.from(immediateFuture(null)))
                .when(mKAnonSignJoinBackgroundJobWorkerMock)
                .runSignJoinBackgroundProcess();
        doNothing().when(mKAnonSignJoinBackgroundJobService).jobFinished(mJobParametersMock, false);
        CountDownLatch jobFinishedCountDown = new CountDownLatch(1);
        doAnswer(
                        unusedInvocation -> {
                            jobFinishedCountDown.countDown();
                            return null;
                        })
                .when(mKAnonSignJoinBackgroundJobService)
                .jobFinished(mJobParametersMock, false);
        AdServicesJobServiceLogger logger = mockAdServicesJobServiceLogger(mContext, testFlags);
        JobServiceLoggingCallback onJobDoneCallback = syncLogExecutionStats(logger);

        mKAnonSignJoinBackgroundJobService.onStartJob(mJobParametersMock);
        jobFinishedCountDown.await();

        verify(mKAnonSignJoinBackgroundJobService).jobFinished(mJobParametersMock, false);
        verify(mKAnonSignJoinBackgroundJobWorkerMock).runSignJoinBackgroundProcess();
        verifyOnJobFinishedLogged(logger, onJobDoneCallback);
    }

    @Test
    public void onStartJob_withDisabledExtServicesJobOnTPlus_skipsAndCancelsBackgroundJob()
            throws Exception {
        doReturn(true).when(() -> ServiceCompatUtils.shouldDisableExtServicesJobOnTPlus(any()));
        doNothing().when(mKAnonSignJoinBackgroundJobService).jobFinished(mJobParametersMock, false);
        AdServicesJobServiceLogger logger =
                mockAdServicesJobServiceLogger(mContext, mock(Flags.class));
        JobServiceLoggingCallback callback = syncLogExecutionStats(logger);
        JobInfo existingJobInfo =
                new JobInfo.Builder(
                                FLEDGE_KANON_SIGN_JOIN_BACKGROUND_JOB_ID,
                                new ComponentName(
                                        mContext, KAnonSignJoinBackgroundJobService.class))
                        .setMinimumLatency(MINIMUM_SCHEDULING_DELAY_MS)
                        .build();
        JOB_SCHEDULER.schedule(existingJobInfo);

        assertNotNull(JOB_SCHEDULER.getPendingJob(FLEDGE_KANON_SIGN_JOIN_BACKGROUND_JOB_ID));

        mKAnonSignJoinBackgroundJobService.onStartJob(mJobParametersMock);

        assertNull(JOB_SCHEDULER.getPendingJob(FLEDGE_KANON_SIGN_JOIN_BACKGROUND_JOB_ID));
        verify(mKAnonSignJoinBackgroundJobWorkerMock, never()).runSignJoinBackgroundProcess();
        verifyBackgroundJobsSkipLogged(logger, callback);
    }

    @Test
    public void onStartJob_withBackgroundProcessFlagDisabled_skipsAndCancelsBackgroundJob()
            throws Exception {
        KAnonSignJoinBackgroundJobServiceTestFlags testWithBackgroundDisabled =
                new KAnonSignJoinBackgroundJobServiceTestFlags(10000, false, false);
        doReturn(testWithBackgroundDisabled).when(() -> FlagsFactory.getFlags());
        doReturn(false).when(() -> ServiceCompatUtils.shouldDisableExtServicesJobOnTPlus(any()));
        doNothing().when(mKAnonSignJoinBackgroundJobService).jobFinished(mJobParametersMock, false);
        AdServicesJobServiceLogger logger =
                mockAdServicesJobServiceLogger(mContext, testWithBackgroundDisabled);
        JobServiceLoggingCallback callback = syncLogExecutionStats(logger);
        JobInfo existingJobInfo =
                new JobInfo.Builder(
                                FLEDGE_KANON_SIGN_JOIN_BACKGROUND_JOB_ID,
                                new ComponentName(
                                        mContext, KAnonSignJoinBackgroundJobService.class))
                        .setMinimumLatency(MINIMUM_SCHEDULING_DELAY_MS)
                        .build();
        JOB_SCHEDULER.schedule(existingJobInfo);

        assertNotNull(JOB_SCHEDULER.getPendingJob(FLEDGE_KANON_SIGN_JOIN_BACKGROUND_JOB_ID));

        mKAnonSignJoinBackgroundJobService.onStartJob(mJobParametersMock);

        assertNull(JOB_SCHEDULER.getPendingJob(FLEDGE_KANON_SIGN_JOIN_BACKGROUND_JOB_ID));
        verify(mKAnonSignJoinBackgroundJobWorkerMock, never()).runSignJoinBackgroundProcess();
        verifyBackgroundJobsSkipLogged(logger, callback);
    }

    @Test
    public void scheduleIfNeeded_schedulesTheJob() {
        KAnonSignJoinBackgroundJobServiceTestFlags testFlags =
                new KAnonSignJoinBackgroundJobServiceTestFlags(10000, false, true);
        doReturn(testFlags).when(() -> FlagsFactory.getFlags());
        doReturn(mKAnonSignJoinBackgroundJobWorkerMock)
                .when(() -> KAnonSignJoinBackgroundJobWorker.getInstance(mContext));

        KAnonSignJoinBackgroundJobService.scheduleIfNeeded(mContext, false);

        assertThat(JOB_SCHEDULER.getPendingJob(FLEDGE_KANON_SIGN_JOIN_BACKGROUND_JOB_ID))
                .isNotNull();
    }

    @Test
    public void testScheduleIfNeeded_skipsIfAlreadyScheduled() {
        JobInfo existingJobInfo =
                new JobInfo.Builder(
                                FLEDGE_KANON_SIGN_JOIN_BACKGROUND_JOB_ID,
                                new ComponentName(
                                        mContext, KAnonSignJoinBackgroundJobService.class))
                        .setMinimumLatency(MINIMUM_SCHEDULING_DELAY_MS)
                        .build();
        JOB_SCHEDULER.schedule(existingJobInfo);
        assertNotNull(JOB_SCHEDULER.getPendingJob(FLEDGE_KANON_SIGN_JOIN_BACKGROUND_JOB_ID));
        boolean forceSchedule = false;
        doCallRealMethod()
                .when(
                        () ->
                                KAnonSignJoinBackgroundJobService.scheduleIfNeeded(
                                        any(), eq(forceSchedule)));

        KAnonSignJoinBackgroundJobService.scheduleIfNeeded(mContext, forceSchedule);

        ExtendedMockito.verify(
                () -> KAnonSignJoinBackgroundJobService.schedule(any(), any()), never());
    }

    @Test
    public void testScheduleIfNeeded_withForceScheduleTrue_schedules() {
        JobInfo existingJobInfo =
                new JobInfo.Builder(
                                FLEDGE_KANON_SIGN_JOIN_BACKGROUND_JOB_ID,
                                new ComponentName(
                                        mContext, KAnonSignJoinBackgroundJobService.class))
                        .setMinimumLatency(MINIMUM_SCHEDULING_DELAY_MS)
                        .build();
        JOB_SCHEDULER.schedule(existingJobInfo);
        assertNotNull(JOB_SCHEDULER.getPendingJob(FLEDGE_KANON_SIGN_JOIN_BACKGROUND_JOB_ID));
        boolean forceSchedule = true;
        doCallRealMethod()
                .when(
                        () ->
                                KAnonSignJoinBackgroundJobService.scheduleIfNeeded(
                                        any(), eq(forceSchedule)));
        doNothing().when(() -> KAnonSignJoinBackgroundJobService.schedule(any(), any()));

        KAnonSignJoinBackgroundJobService.scheduleIfNeeded(mContext, forceSchedule);

        ExtendedMockito.verify(() -> KAnonSignJoinBackgroundJobService.schedule(any(), any()));
    }

    public static final class KAnonSignJoinBackgroundJobServiceTestFlags implements Flags {
        private final int mBackgroundJobPeriod;
        private final boolean mSignJoinFeatureEnabled;
        private final boolean mKanonFledgeBackgroundJobEnabled;

        KAnonSignJoinBackgroundJobServiceTestFlags(
                int backgroundJobPeriod,
                boolean signJoinFeatureEnabled,
                boolean kanonBackgroundJobEnabled) {
            mBackgroundJobPeriod = backgroundJobPeriod;
            mSignJoinFeatureEnabled = signJoinFeatureEnabled;
            mKanonFledgeBackgroundJobEnabled = kanonBackgroundJobEnabled;
        }

        @Override
        public long getFledgeKAnonBackgroundProcessTimePeriodInMs() {
            return mBackgroundJobPeriod;
        }

        @Override
        public boolean getFledgeKAnonSignJoinFeatureEnabled() {
            return mSignJoinFeatureEnabled;
        }

        @Override
        public boolean getFledgeKAnonBackgroundProcessEnabled() {
            return mKanonFledgeBackgroundJobEnabled;
        }
    }
}

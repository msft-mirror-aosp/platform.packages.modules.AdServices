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

package com.android.adservices.service.common;

import static com.android.adservices.spe.AdServicesJobInfo.AD_PACKAGE_DENY_PRE_PROCESS_JOB;
import static com.android.dx.mockito.inline.extended.ExtendedMockito.doAnswer;
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
import com.android.adservices.service.FlagsFactory;
import com.android.adservices.service.common.compat.ServiceCompatUtils;
import com.android.adservices.shared.testing.BooleanSyncCallback;
import com.android.adservices.shared.testing.FutureSyncCallback;
import com.android.adservices.shared.testing.JobServiceLoggingCallback;
import com.android.adservices.shared.testing.annotations.RequiresSdkLevelAtLeastS;
import com.android.adservices.shared.testing.concurrency.JobServiceCallback;
import com.android.adservices.spe.AdServicesJobServiceLogger;
import com.android.modules.utils.testing.ExtendedMockitoRule.MockStatic;
import com.android.modules.utils.testing.ExtendedMockitoRule.SpyStatic;

import com.google.common.util.concurrent.Futures;

import org.junit.After;
import org.junit.Before;
import org.junit.Test;
import org.mockito.Mock;
import org.mockito.Spy;

import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;

@RequiresSdkLevelAtLeastS()
@SpyStatic(AdPackageDenyPreProcessJobService.class)
@SpyStatic(FlagsFactory.class)
@SpyStatic(AdServicesJobServiceLogger.class)
@SpyStatic(AdPackageDenyResolver.class)
@MockStatic(ServiceCompatUtils.class)
public final class AdPackageDenyPreProcessJobServiceTest extends AdServicesJobServiceTestCase {

    private static final long JOB_INTERVAL_MS = 21_600_000L;
    private static final int PACKAGE_DENY_PRE_PROCESS_JOB_ID =
            AD_PACKAGE_DENY_PRE_PROCESS_JOB.getJobId();
    private static final JobScheduler JOB_SCHEDULER = sContext.getSystemService(JobScheduler.class);

    @Spy private AdPackageDenyPreProcessJobService mAdPackageDenyPreProcessJobService;
    @Mock private JobParameters mMockJobParameters;
    @Mock private AdPackageDenyResolver mMockAdPackageDenyResolver;
    private final ExecutorService mExecutorService = Executors.newSingleThreadExecutor();
    private AdServicesJobServiceLogger mLogger;

    @Before
    public void setup() {
        mocker.mockGetFlags(mMockFlags);
        doReturn(JOB_SCHEDULER)
                .when(mAdPackageDenyPreProcessJobService)
                .getSystemService(JobScheduler.class);
        mLogger = mocker.mockNoOpAdServicesJobServiceLogger(mContext, mMockFlags);
    }

    @After
    public void teardown() {
        JOB_SCHEDULER.cancelAll();
    }

    @Test
    public void testSchedule_featureEnabled() throws Exception {
        // Feature is Enabled.
        when(mMockFlags.getEnablePackageDenyBgJob()).thenReturn(true);
        BooleanSyncCallback callBack = scheduleJobInBackground();

        assertJobScheduled(callBack, /* shouldSchedule */ true);
    }

    @Test
    public void testSchedule_featureDisabled() throws Exception {
        // Feature is disabled.
        when(mMockFlags.getEnablePackageDenyBgJob()).thenReturn(false);

        BooleanSyncCallback callBack = scheduleJobInBackground();

        assertJobScheduled(callBack, /* shouldSchedule */ false);
        verifyNoMoreInteractions(staticMockMarker(AdPackageDenyResolver.class));
    }

    @Test
    public void testScheduleIfNeeded_success() throws Exception {
        // Feature is enabled.
        when(mMockFlags.getEnablePackageDenyBgJob()).thenReturn(true);

        BooleanSyncCallback callBack = scheduleJobInBackground();

        assertJobScheduled(callBack, /* shouldSchedule */ true);
    }

    @Test
    public void onStartJob_featureEnabled() throws Exception {
        FutureSyncCallback<Void> packageDenyCallBack = mockPackageDenyReadFromMdd();
        JobServiceLoggingCallback onStartJobCallback = syncPersistJobExecutionData(mLogger);
        JobServiceLoggingCallback onJobDoneCallback = syncLogExecutionStats(mLogger);

        // Feature is enabled.
        JobServiceCallback callback =
                new JobServiceCallback().expectJobFinished(mAdPackageDenyPreProcessJobService);

        mAdPackageDenyPreProcessJobService.onStartJob(mMockJobParameters);

        callback.assertJobFinished();

        verifyJobFinishedLogged(mLogger, onStartJobCallback, onJobDoneCallback);
        packageDenyCallBack.assertResultReceived();
    }

    @Test
    public void onStartJob_featureDisabled() throws Exception {
        // Feature is disabled.
        JobServiceLoggingCallback loggingCallback = syncLogExecutionStats(mLogger);

        when(mMockFlags.getEnablePackageDenyBgJob()).thenReturn(false);
        doNothing().when(mAdPackageDenyPreProcessJobService).jobFinished(mMockJobParameters, false);

        JobServiceCallback callback =
                new JobServiceCallback().expectJobFinished(mAdPackageDenyPreProcessJobService);

        // Now verify that when the Job starts, it will be unscheduled.
        assertThat(mAdPackageDenyPreProcessJobService.onStartJob(mMockJobParameters)).isFalse();
        assertThat(JOB_SCHEDULER.getPendingJob(PACKAGE_DENY_PRE_PROCESS_JOB_ID)).isNull();

        callback.assertJobFinished();

        verify(mAdPackageDenyPreProcessJobService).jobFinished(mMockJobParameters, false);
        verifyNoMoreInteractions(staticMockMarker(AdPackageDenyResolver.class));
        verifyBackgroundJobsSkipLogged(mLogger, loggingCallback);
    }

    @Test
    public void onStopJob() throws Exception {
        JobServiceLoggingCallback loggingCallback = syncLogExecutionStats(mLogger);

        // Feature is enabled.
        when(mMockFlags.getEnablePackageDenyBgJob()).thenReturn(true);

        JobServiceCallback callback =
                new JobServiceCallback().expectJobStopped(mAdPackageDenyPreProcessJobService);
        // Verify nothing throws.
        mAdPackageDenyPreProcessJobService.onStopJob(mMockJobParameters);

        callback.assertJobStopped();

        verifyOnStopJobLogged(mLogger, loggingCallback);
    }

    @Test
    public void onStartJob_shouldDisableJobTrue() throws Exception {
        JobServiceLoggingCallback loggingCallback = syncLogExecutionStats(mLogger);
        doReturn(true)
                .when(
                        () ->
                                ServiceCompatUtils.shouldDisableExtServicesJobOnTPlus(
                                        any(Context.class)));

        doNothing().when(mAdPackageDenyPreProcessJobService).jobFinished(mMockJobParameters, false);

        assertThat(mAdPackageDenyPreProcessJobService.onStartJob(mMockJobParameters)).isFalse();

        assertThat(JOB_SCHEDULER.getPendingJob(PACKAGE_DENY_PRE_PROCESS_JOB_ID)).isNull();

        verify(mAdPackageDenyPreProcessJobService).jobFinished(mMockJobParameters, false);
        verifyNoMoreInteractions(staticMockMarker(AdPackageDenyResolver.class));

        verifyBackgroundJobsSkipLogged(mLogger, loggingCallback);
    }

    private BooleanSyncCallback scheduleJobInBackground() {
        doNothing().when(() -> AdPackageDenyPreProcessJobService.schedule(any(), any()));
        BooleanSyncCallback callback = new BooleanSyncCallback();

        mExecutorService.execute(
                () -> callback.injectResult(AdPackageDenyPreProcessJobService.scheduleIfNeeded()));

        return callback;
    }

    private void assertJobScheduled(BooleanSyncCallback callback, boolean shouldSchedule)
            throws InterruptedException {
        assertThat(callback.assertResultReceived()).isEqualTo(shouldSchedule);
    }

    private JobInfo mockJobInfo() {
        return new JobInfo.Builder(
                        PACKAGE_DENY_PRE_PROCESS_JOB_ID,
                        new ComponentName(mContext, AdPackageDenyPreProcessJobService.class))
                .setPeriodic(JOB_INTERVAL_MS)
                .setPersisted(true)
                .build();
    }

    private FutureSyncCallback<Void> mockPackageDenyReadFromMdd() {
        when(mMockFlags.getEnablePackageDenyBgJob()).thenReturn(true);
        doReturn(mMockAdPackageDenyResolver).when(AdPackageDenyResolver::getInstance);
        FutureSyncCallback<Void> futureCallback = new FutureSyncCallback<>();
        doAnswer(
                        invocation -> {
                            futureCallback.onSuccess(null);
                            return Futures.immediateFuture(
                                    AdPackageDenyResolver.PackageDenyMddProcessStatus.SUCCESS);
                        })
                .when(mMockAdPackageDenyResolver)
                .loadDenyDataFromMdd();

        return futureCallback;
    }
}

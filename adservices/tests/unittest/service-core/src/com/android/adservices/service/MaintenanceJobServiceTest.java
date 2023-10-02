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

package com.android.adservices.service;

import static com.android.adservices.service.stats.AdServicesStatsLog.AD_SERVICES_BACKGROUND_JOBS_EXECUTION_REPORTED__EXECUTION_RESULT_CODE__SKIP_FOR_KILL_SWITCH_ON;
import static com.android.adservices.service.stats.AdServicesStatsLog.AD_SERVICES_ERROR_REPORTED__ERROR_CODE__TOPICS_API_DISABLED;
import static com.android.adservices.service.stats.AdServicesStatsLog.AD_SERVICES_ERROR_REPORTED__PPAPI_NAME__TOPICS;
import static com.android.adservices.spe.AdservicesJobInfo.MAINTENANCE_JOB;
import static com.android.dx.mockito.inline.extended.ExtendedMockito.any;
import static com.android.dx.mockito.inline.extended.ExtendedMockito.anyLong;
import static com.android.dx.mockito.inline.extended.ExtendedMockito.doAnswer;
import static com.android.dx.mockito.inline.extended.ExtendedMockito.doNothing;
import static com.android.dx.mockito.inline.extended.ExtendedMockito.doReturn;
import static com.android.dx.mockito.inline.extended.ExtendedMockito.doThrow;
import static com.android.dx.mockito.inline.extended.ExtendedMockito.never;
import static com.android.dx.mockito.inline.extended.ExtendedMockito.staticMockMarker;
import static com.android.dx.mockito.inline.extended.ExtendedMockito.verify;
import static com.android.dx.mockito.inline.extended.ExtendedMockito.verifyNoMoreInteractions;

import static com.google.common.truth.Truth.assertThat;
import static com.google.common.truth.Truth.assertWithMessage;

import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertNotNull;
import static org.junit.Assert.assertNull;
import static org.mockito.ArgumentMatchers.anyInt;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.atLeastOnce;
import static org.mockito.Mockito.spy;
import static org.mockito.Mockito.times;

import android.app.job.JobInfo;
import android.app.job.JobParameters;
import android.app.job.JobScheduler;
import android.content.ComponentName;
import android.content.Context;
import android.content.pm.PackageManager;

import androidx.test.core.app.ApplicationProvider;

import com.android.adservices.errorlogging.ErrorLogUtil;
import com.android.adservices.service.common.FledgeMaintenanceTasksWorker;
import com.android.adservices.service.common.compat.ServiceCompatUtils;
import com.android.adservices.service.signals.SignalsMaintenanceTasksWorker;
import com.android.adservices.service.stats.Clock;
import com.android.adservices.service.stats.StatsdAdServicesLogger;
import com.android.adservices.service.topics.AppUpdateManager;
import com.android.adservices.service.topics.BlockedTopicsManager;
import com.android.adservices.service.topics.CacheManager;
import com.android.adservices.service.topics.EpochJobService;
import com.android.adservices.service.topics.EpochManager;
import com.android.adservices.service.topics.TopicsWorker;
import com.android.adservices.spe.AdservicesJobServiceLogger;
import com.android.dx.mockito.inline.extended.ExtendedMockito;

import org.junit.After;
import org.junit.Before;
import org.junit.Test;
import org.mockito.ArgumentCaptor;
import org.mockito.Mock;
import org.mockito.MockitoSession;
import org.mockito.Spy;
import org.mockito.quality.Strictness;

import java.util.concurrent.CountDownLatch;
import java.util.concurrent.TimeUnit;

/** Unit tests for {@link com.android.adservices.service.MaintenanceJobService} */
@SuppressWarnings("ConstantConditions")
public class MaintenanceJobServiceTest {
    private static final int BACKGROUND_THREAD_TIMEOUT_MS = 5_000;
    private static final int MAINTENANCE_JOB_ID = MAINTENANCE_JOB.getJobId();
    private static final long MAINTENANCE_JOB_PERIOD_MS = 10_000L;
    private static final long MAINTENANCE_JOB_FLEX_MS = 1_000L;
    private static final long CURRENT_EPOCH_ID = 1L;
    private static final Context CONTEXT = ApplicationProvider.getApplicationContext();
    private static final JobScheduler JOB_SCHEDULER = CONTEXT.getSystemService(JobScheduler.class);
    private static final Flags TEST_FLAGS = FlagsFactory.getFlagsForTest();

    @Spy private MaintenanceJobService mSpyMaintenanceJobService;
    private MockitoSession mStaticMockSession;

    // Mock EpochManager and CacheManager as the methods called are tested in corresponding
    // unit test. In this test, only verify whether specific method is initiated.
    @Mock EpochManager mMockEpochManager;
    @Mock CacheManager mMockCacheManager;
    @Mock BlockedTopicsManager mBlockedTopicsManager;
    @Mock AppUpdateManager mMockAppUpdateManager;
    @Mock JobParameters mMockJobParameters;
    @Mock Flags mMockFlags;
    @Mock JobScheduler mMockJobScheduler;
    @Mock private PackageManager mPackageManagerMock;
    @Mock StatsdAdServicesLogger mMockStatsdLogger;
    private AdservicesJobServiceLogger mSpyLogger;
    @Mock private FledgeMaintenanceTasksWorker mFledgeMaintenanceTasksWorkerMock;
    @Mock private SignalsMaintenanceTasksWorker mSignalsMaintenanceTasksWorkerMock;

    @Before
    public void setup() {
        // Start a mockitoSession to mock static method
        mStaticMockSession =
                ExtendedMockito.mockitoSession()
                        .spyStatic(MaintenanceJobService.class)
                        .spyStatic(TopicsWorker.class)
                        .spyStatic(FlagsFactory.class)
                        .spyStatic(AdservicesJobServiceLogger.class)
                        .spyStatic(ErrorLogUtil.class)
                        .mockStatic(ServiceCompatUtils.class)
                        .initMocks(this)
                        .strictness(Strictness.LENIENT)
                        .startMocking();

        // Mock JobScheduler invocation in EpochJobService
        assertThat(JOB_SCHEDULER).isNotNull();
        doReturn(JOB_SCHEDULER)
                .when(mSpyMaintenanceJobService)
                .getSystemService(JobScheduler.class);

        // Mock AdservicesJobServiceLogger to not actually log the stats to server
        mSpyLogger =
                spy(new AdservicesJobServiceLogger(CONTEXT, Clock.SYSTEM_CLOCK, mMockStatsdLogger));
        doNothing().when(mSpyLogger).logExecutionStats(anyInt(), anyLong(), anyInt(), anyInt());
        doReturn(mSpyLogger).when(() -> AdservicesJobServiceLogger.getInstance(any(Context.class)));
    }

    @After
    public void teardown() {
        JOB_SCHEDULER.cancelAll();
        if (mStaticMockSession != null) {
            mStaticMockSession.finishMocking();
        }
    }

    @Test
    public void testOnStartJob_killSwitchOff_withoutLogging() throws InterruptedException {
        // Logging killswitch is on.
        doReturn(true).when(mMockFlags).getBackgroundJobsLoggingKillSwitch();

        testOnStartJob_killSwitchOff();

        // Verify logging methods are not invoked.
        verify(mSpyLogger, never()).persistJobExecutionData(anyInt(), anyLong());
        verify(mSpyLogger, never()).logExecutionStats(anyInt(), anyLong(), anyInt(), anyInt());
    }

    @Test
    public void testOnStartJob_killSwitchOff_withLogging() throws InterruptedException {
        // Logging killswitch is off.
        doReturn(false).when(mMockFlags).getBackgroundJobsLoggingKillSwitch();

        testOnStartJob_killSwitchOff();

        // Verify logging methods are invoked.
        verify(mSpyLogger, atLeastOnce()).persistJobExecutionData(anyInt(), anyLong());
    }

    @Test
    public void testOnStartJob_TopicsKillSwitchOn() throws InterruptedException {
        // Killswitch is off.
        doReturn(true).when(mMockFlags).getTopicsKillSwitch();
        doReturn(false).when(mMockFlags).getFledgeSelectAdsKillSwitch();
        doReturn(true).when(mMockFlags).getProtectedSignalsCleanupEnabled();
        doReturn(CURRENT_EPOCH_ID).when(mMockEpochManager).getCurrentEpochId();

        // Mock static method FlagsFactory.getFlags() to return Mock Flags.
        doReturn(mMockFlags).when(FlagsFactory::getFlags);
        doReturn(TEST_FLAGS.getAdSelectionExpirationWindowS())
                .when(mMockFlags)
                .getAdSelectionExpirationWindowS();

        // Inject FledgeMaintenanceTasksWorker since the test can't get it the standard way
        doReturn(mPackageManagerMock).when(mSpyMaintenanceJobService).getPackageManager();
        mSpyMaintenanceJobService.injectFledgeMaintenanceTasksWorker(
                mFledgeMaintenanceTasksWorkerMock);
        mSpyMaintenanceJobService.injectSignalsMaintenanceTasksWorker(
                mSignalsMaintenanceTasksWorkerMock);
        CountDownLatch jobCompletionLatch = new CountDownLatch(1);
        doAnswer(
                        unusedInvocation -> {
                            jobCompletionLatch.countDown();
                            return null;
                        })
                .when(mSpyMaintenanceJobService)
                .jobFinished(mMockJobParameters, false);

        // Schedule the job to assert after starting that the scheduled job has been started
        JobInfo existingJobInfo =
                new JobInfo.Builder(
                        MAINTENANCE_JOB_ID,
                        new ComponentName(CONTEXT, EpochJobService.class))
                        .setRequiresCharging(true)
                        .setPeriodic(MAINTENANCE_JOB_PERIOD_MS, MAINTENANCE_JOB_FLEX_MS)
                        .setPersisted(true)
                        .build();
        JOB_SCHEDULER.schedule(existingJobInfo);
        assertThat(JOB_SCHEDULER.getPendingJob(MAINTENANCE_JOB_ID)).isNotNull();

        // Now verify that when the Job starts, it will schedule itself.
        assertThat(mSpyMaintenanceJobService.onStartJob(mMockJobParameters)).isTrue();

        assertThat(JOB_SCHEDULER.getPendingJob(MAINTENANCE_JOB_ID)).isNotNull();

        assertWithMessage("Job completed within timeout")
                .that(jobCompletionLatch.await(1, TimeUnit.SECONDS))
                .isTrue();

        // Verify that topics job is not done
        verify(() -> TopicsWorker.getInstance(any(Context.class)), never());
        verify(mMockAppUpdateManager, never())
                .reconcileUninstalledApps(any(Context.class), eq(CURRENT_EPOCH_ID));
        verify(mMockAppUpdateManager, never())
                .reconcileInstalledApps(any(Context.class), /* currentEpochId */ anyLong());

        // Verifying that FLEDGE job was done
        verify(mFledgeMaintenanceTasksWorkerMock).clearExpiredAdSelectionData();
        verify(mFledgeMaintenanceTasksWorkerMock)
                .clearInvalidFrequencyCapHistogramData(any(PackageManager.class));

        verify(mSignalsMaintenanceTasksWorkerMock).clearInvalidSignals();
    }

    @Test
    public void testOnStartJob_SelectAdsKillSwitchOn() throws InterruptedException {
        final TopicsWorker topicsWorker =
                new TopicsWorker(
                        mMockEpochManager,
                        mMockCacheManager,
                        mBlockedTopicsManager,
                        mMockAppUpdateManager,
                        TEST_FLAGS);
        // Killswitch is off.
        doReturn(false).when(mMockFlags).getTopicsKillSwitch();
        doReturn(true).when(mMockFlags).getFledgeSelectAdsKillSwitch();
        doReturn(CURRENT_EPOCH_ID).when(mMockEpochManager).getCurrentEpochId();

        // Mock static method FlagsFactory.getFlags() to return Mock Flags.
        doReturn(mMockFlags).when(FlagsFactory::getFlags);

        // Mock static method AppUpdateWorker.getInstance, let it return the local
        // appUpdateWorker in order to get a test instance.
        doReturn(topicsWorker).when(() -> TopicsWorker.getInstance(any(Context.class)));

        CountDownLatch jobCompletionLatch = new CountDownLatch(1);
        doAnswer(
                        unusedInvocation -> {
                            jobCompletionLatch.countDown();
                            return null;
                        })
                .when(mSpyMaintenanceJobService)
                .jobFinished(mMockJobParameters, false);

        // Schedule the job to assert after starting that the scheduled job has been started
        JobInfo existingJobInfo =
                new JobInfo.Builder(
                        MAINTENANCE_JOB_ID,
                        new ComponentName(CONTEXT, EpochJobService.class))
                        .setRequiresCharging(true)
                        .setPeriodic(MAINTENANCE_JOB_PERIOD_MS, MAINTENANCE_JOB_FLEX_MS)
                        .setPersisted(true)
                        .build();
        JOB_SCHEDULER.schedule(existingJobInfo);
        assertThat(JOB_SCHEDULER.getPendingJob(MAINTENANCE_JOB_ID)).isNotNull();

        // Now verify that when the Job starts, it will schedule itself.
        assertThat(mSpyMaintenanceJobService.onStartJob(mMockJobParameters)).isTrue();

        assertThat(JOB_SCHEDULER.getPendingJob(MAINTENANCE_JOB_ID)).isNotNull();

        assertWithMessage("Job completed within timeout")
                .that(jobCompletionLatch.await(1, TimeUnit.SECONDS))
                .isTrue();

        verify(() -> TopicsWorker.getInstance(any(Context.class)));
        verify(mMockAppUpdateManager)
                .reconcileUninstalledApps(any(Context.class), eq(CURRENT_EPOCH_ID));
        verify(mMockAppUpdateManager)
                .reconcileInstalledApps(any(Context.class), /* currentEpochId */ anyLong());
        verify(mFledgeMaintenanceTasksWorkerMock, never()).clearExpiredAdSelectionData();
        verify(mFledgeMaintenanceTasksWorkerMock, never())
                .clearInvalidFrequencyCapHistogramData(any(PackageManager.class));
    }

    @Test
    public void testOnStartJob_SignalsDisabled() throws InterruptedException {
        final TopicsWorker topicsWorker =
                new TopicsWorker(
                        mMockEpochManager,
                        mMockCacheManager,
                        mBlockedTopicsManager,
                        mMockAppUpdateManager,
                        TEST_FLAGS);
        // Killswitch is off.
        doReturn(false).when(mMockFlags).getTopicsKillSwitch();
        doReturn(false).when(mMockFlags).getFledgeSelectAdsKillSwitch();
        doReturn(false).when(mMockFlags).getProtectedSignalsCleanupEnabled();
        doReturn(CURRENT_EPOCH_ID).when(mMockEpochManager).getCurrentEpochId();

        // Mock static method FlagsFactory.getFlags() to return Mock Flags.
        doReturn(mMockFlags).when(FlagsFactory::getFlags);

        // Mock static method AppUpdateWorker.getInstance, let it return the local
        // appUpdateWorker in order to get a test instance.
        doReturn(topicsWorker).when(() -> TopicsWorker.getInstance(any(Context.class)));
        doReturn(mPackageManagerMock).when(mSpyMaintenanceJobService).getPackageManager();
        mSpyMaintenanceJobService.injectFledgeMaintenanceTasksWorker(
                mFledgeMaintenanceTasksWorkerMock);
        mSpyMaintenanceJobService.injectSignalsMaintenanceTasksWorker(
                mSignalsMaintenanceTasksWorkerMock);

        CountDownLatch jobCompletionLatch = new CountDownLatch(1);
        doAnswer(
                        unusedInvocation -> {
                            jobCompletionLatch.countDown();
                            return null;
                        })
                .when(mSpyMaintenanceJobService)
                .jobFinished(mMockJobParameters, false);

        // Schedule the job to assert after starting that the scheduled job has been started
        JobInfo existingJobInfo =
                new JobInfo.Builder(
                                MAINTENANCE_JOB_ID,
                                new ComponentName(CONTEXT, EpochJobService.class))
                        .setRequiresCharging(true)
                        .setPeriodic(MAINTENANCE_JOB_PERIOD_MS, MAINTENANCE_JOB_FLEX_MS)
                        .setPersisted(true)
                        .build();
        JOB_SCHEDULER.schedule(existingJobInfo);
        assertThat(JOB_SCHEDULER.getPendingJob(MAINTENANCE_JOB_ID)).isNotNull();

        // Now verify that when the Job starts, it will schedule itself.
        assertThat(mSpyMaintenanceJobService.onStartJob(mMockJobParameters)).isTrue();

        assertThat(JOB_SCHEDULER.getPendingJob(MAINTENANCE_JOB_ID)).isNotNull();

        assertWithMessage("Job completed within timeout")
                .that(jobCompletionLatch.await(1, TimeUnit.SECONDS))
                .isTrue();

        verify(() -> TopicsWorker.getInstance(any(Context.class)));
        verify(mMockAppUpdateManager)
                .reconcileUninstalledApps(any(Context.class), eq(CURRENT_EPOCH_ID));
        verify(mMockAppUpdateManager)
                .reconcileInstalledApps(any(Context.class), /* currentEpochId */ anyLong());
        verify(mFledgeMaintenanceTasksWorkerMock).clearExpiredAdSelectionData();
        verify(mFledgeMaintenanceTasksWorkerMock)
                .clearInvalidFrequencyCapHistogramData(any(PackageManager.class));
        verify(mSignalsMaintenanceTasksWorkerMock, never()).clearInvalidSignals();
    }

    @Test
    public void testOnStartJob_killSwitchOn_withoutLogging() throws Exception {
        // Logging killswitch is on.
        doReturn(true).when(mMockFlags).getBackgroundJobsLoggingKillSwitch();

        testOnStartJob_killSwitchOn();

        // Verify logging methods are not invoked.
        verify(mSpyLogger, never()).persistJobExecutionData(anyInt(), anyLong());
        verify(mSpyLogger, never()).logExecutionStats(anyInt(), anyLong(), anyInt(), anyInt());
    }

    @Test
    public void testOnStartJob_killSwitchOn_withLogging() throws Exception {
        // Logging killswitch is off.
        doReturn(false).when(mMockFlags).getBackgroundJobsLoggingKillSwitch();

        testOnStartJob_killSwitchOn();

        // Verify logging methods are invoked.
        verify(mSpyLogger).persistJobExecutionData(anyInt(), anyLong());
        verify(mSpyLogger)
                .logExecutionStats(
                        anyInt(),
                        anyLong(),
                        eq(
                                AD_SERVICES_BACKGROUND_JOBS_EXECUTION_REPORTED__EXECUTION_RESULT_CODE__SKIP_FOR_KILL_SWITCH_ON),
                        anyInt());
    }

    @Test
    public void testOnStartJob_killSwitchOnDoesFledgeJobWhenTopicsJobThrowsException()
            throws Exception {
        final TopicsWorker topicsWorker =
                new TopicsWorker(
                        mMockEpochManager,
                        mMockCacheManager,
                        mBlockedTopicsManager,
                        mMockAppUpdateManager,
                        TEST_FLAGS);
        // Killswitch is off.
        doReturn(false).when(mMockFlags).getTopicsKillSwitch();
        doReturn(false).when(mMockFlags).getFledgeSelectAdsKillSwitch();
        doReturn(true).when(mMockFlags).getProtectedSignalsCleanupEnabled();
        doReturn(CURRENT_EPOCH_ID).when(mMockEpochManager).getCurrentEpochId();
        doReturn(TEST_FLAGS.getAdSelectionExpirationWindowS())
                .when(mMockFlags)
                .getAdSelectionExpirationWindowS();

        // Mock static method FlagsFactory.getFlags() to return Mock Flags.
        doReturn(mMockFlags).when(FlagsFactory::getFlags);

        // Mock static method AppUpdateWorker.getInstance, let it return the local
        // appUpdateWorker in order to get a test instance.
        doReturn(topicsWorker).when(() -> TopicsWorker.getInstance(any(Context.class)));

        // Inject FledgeMaintenanceTasksWorker since the test can't get it the standard way
        doReturn(mPackageManagerMock).when(mSpyMaintenanceJobService).getPackageManager();
        mSpyMaintenanceJobService.injectFledgeMaintenanceTasksWorker(
                mFledgeMaintenanceTasksWorkerMock);
        mSpyMaintenanceJobService.injectSignalsMaintenanceTasksWorker(
                mSignalsMaintenanceTasksWorkerMock);

        // Simulating a failure in Topics job
        doThrow(new IllegalStateException())
                .when(mMockAppUpdateManager)
                .reconcileUninstalledApps(any(Context.class), eq(CURRENT_EPOCH_ID));

        CountDownLatch jobCompletionLatch = new CountDownLatch(1);
        doAnswer(
                        unusedInvocation -> {
                            jobCompletionLatch.countDown();
                            return null;
                        })
                .when(mSpyMaintenanceJobService)
                .jobFinished(mMockJobParameters, false);

        // Schedule the job to assert after starting that the scheduled job has been started
        JobInfo existingJobInfo =
                new JobInfo.Builder(
                        MAINTENANCE_JOB_ID,
                        new ComponentName(CONTEXT, EpochJobService.class))
                        .setRequiresCharging(true)
                        .setPeriodic(MAINTENANCE_JOB_PERIOD_MS, MAINTENANCE_JOB_FLEX_MS)
                        .setPersisted(true)
                        .build();
        JOB_SCHEDULER.schedule(existingJobInfo);
        assertThat(JOB_SCHEDULER.getPendingJob(MAINTENANCE_JOB_ID)).isNotNull();

        // Now verify that when the Job starts, it will schedule itself.
        assertThat(mSpyMaintenanceJobService.onStartJob(mMockJobParameters)).isTrue();

        assertThat(JOB_SCHEDULER.getPendingJob(MAINTENANCE_JOB_ID)).isNotNull();

        assertWithMessage("Job completed within timeout")
                .that(jobCompletionLatch.await(1, TimeUnit.SECONDS))
                .isTrue();

        // Verify that this is not called because we threw an exception
        verify(mMockAppUpdateManager, never())
                .reconcileInstalledApps(any(Context.class), /* currentEpochId */ anyLong());

        // Verifying that FLEDGE job was done
        verify(mFledgeMaintenanceTasksWorkerMock).clearExpiredAdSelectionData();
        verify(mFledgeMaintenanceTasksWorkerMock)
                .clearInvalidFrequencyCapHistogramData(any(PackageManager.class));

        verify(mSignalsMaintenanceTasksWorkerMock).clearInvalidSignals();
    }

    @Test
    public void testOnStartJob_killSwitchOnDoesTopicsJobWhenFledgeThrowsException()
            throws Exception {
        final TopicsWorker topicsWorker =
                new TopicsWorker(
                        mMockEpochManager,
                        mMockCacheManager,
                        mBlockedTopicsManager,
                        mMockAppUpdateManager,
                        TEST_FLAGS);
        // Killswitch is off.
        doReturn(false).when(mMockFlags).getTopicsKillSwitch();
        doReturn(false).when(mMockFlags).getFledgeSelectAdsKillSwitch();
        doReturn(true).when(mMockFlags).getProtectedSignalsCleanupEnabled();
        doReturn(CURRENT_EPOCH_ID).when(mMockEpochManager).getCurrentEpochId();

        // Mock static method FlagsFactory.getFlags() to return Mock Flags.
        doReturn(mMockFlags).when(FlagsFactory::getFlags);

        // Mock static method AppUpdateWorker.getInstance, let it return the local
        // appUpdateWorker in order to get a test instance.
        doReturn(topicsWorker).when(() -> TopicsWorker.getInstance(any(Context.class)));

        // Inject FledgeMaintenanceTasksWorker since the test can't get it the standard way
        mSpyMaintenanceJobService.injectFledgeMaintenanceTasksWorker(
                mFledgeMaintenanceTasksWorkerMock);
        mSpyMaintenanceJobService.injectSignalsMaintenanceTasksWorker(
                mSignalsMaintenanceTasksWorkerMock);

        // Simulating a failure in Fledge job
        doThrow(new IllegalStateException())
                .when(mFledgeMaintenanceTasksWorkerMock)
                .clearExpiredAdSelectionData();

        CountDownLatch jobCompletionLatch = new CountDownLatch(1);
        doAnswer(
                        unusedInvocation -> {
                            jobCompletionLatch.countDown();
                            return null;
                        })
                .when(mSpyMaintenanceJobService)
                .jobFinished(mMockJobParameters, false);

        // Schedule the job to assert after starting that the scheduled job has been started
        JobInfo existingJobInfo =
                new JobInfo.Builder(
                        MAINTENANCE_JOB_ID,
                        new ComponentName(CONTEXT, EpochJobService.class))
                        .setRequiresCharging(true)
                        .setPeriodic(MAINTENANCE_JOB_PERIOD_MS, MAINTENANCE_JOB_FLEX_MS)
                        .setPersisted(true)
                        .build();
        JOB_SCHEDULER.schedule(existingJobInfo);
        assertThat(JOB_SCHEDULER.getPendingJob(MAINTENANCE_JOB_ID)).isNotNull();

        // Now verify that when the Job starts, it will schedule itself.
        assertThat(mSpyMaintenanceJobService.onStartJob(mMockJobParameters)).isTrue();

        assertThat(JOB_SCHEDULER.getPendingJob(MAINTENANCE_JOB_ID)).isNotNull();

        assertWithMessage("Job completed within timeout")
                .that(jobCompletionLatch.await(1, TimeUnit.SECONDS))
                .isTrue();

        verify(() -> TopicsWorker.getInstance(any(Context.class)));
        verify(mMockAppUpdateManager)
                .reconcileUninstalledApps(any(Context.class), eq(CURRENT_EPOCH_ID));
        verify(mMockAppUpdateManager)
                .reconcileInstalledApps(any(Context.class), /* currentEpochId */ anyLong());

        verify(mFledgeMaintenanceTasksWorkerMock).clearExpiredAdSelectionData();

        verify(mSignalsMaintenanceTasksWorkerMock).clearInvalidSignals();
    }

    @Test
    public void testOnStartJob_globalKillswitchOverridesAll() throws InterruptedException {
        Flags flagsGlobalKillSwitchOn =
                new Flags() {
                    @Override
                    public boolean getGlobalKillSwitch() {
                        return true;
                    }
                };

        // Setting mock flags to use flags with global switch overridden
        doReturn(flagsGlobalKillSwitchOn.getTopicsKillSwitch())
                .when(mMockFlags)
                .getTopicsKillSwitch();
        doReturn(flagsGlobalKillSwitchOn.getTopicsKillSwitch())
                .when(mMockFlags)
                .getFledgeSelectAdsKillSwitch();
        doReturn(CURRENT_EPOCH_ID).when(mMockEpochManager).getCurrentEpochId();
        doReturn(true).when(mMockFlags).getProtectedSignalsCleanupEnabled();
        doReturn(true).when(mMockFlags).getGlobalKillSwitch();

        // Mock static method FlagsFactory.getFlags() to return Mock Flags.
        doReturn(mMockFlags).when(FlagsFactory::getFlags);

        mSpyMaintenanceJobService.injectSignalsMaintenanceTasksWorker(
                mSignalsMaintenanceTasksWorkerMock);

        CountDownLatch jobCompletionLatch = new CountDownLatch(1);
        doAnswer(
                        unusedInvocation -> {
                            jobCompletionLatch.countDown();
                            return null;
                        })
                .when(mSpyMaintenanceJobService)
                .jobFinished(mMockJobParameters, false);

        // Schedule the job to assert after starting that the scheduled job has been cancelled
        JobInfo existingJobInfo =
                new JobInfo.Builder(
                        MAINTENANCE_JOB_ID,
                        new ComponentName(CONTEXT, EpochJobService.class))
                        .setRequiresCharging(true)
                        .setPeriodic(MAINTENANCE_JOB_PERIOD_MS, MAINTENANCE_JOB_FLEX_MS)
                        .setPersisted(true)
                        .build();
        JOB_SCHEDULER.schedule(existingJobInfo);
        assertThat(JOB_SCHEDULER.getPendingJob(MAINTENANCE_JOB_ID)).isNotNull();

        // Now verify that when the Job starts, it will unschedule itself.
        assertThat(mSpyMaintenanceJobService.onStartJob(mMockJobParameters)).isFalse();

        assertThat(JOB_SCHEDULER.getPendingJob(MAINTENANCE_JOB_ID)).isNull();

        assertWithMessage("Job completed within timeout")
                .that(jobCompletionLatch.await(1, TimeUnit.SECONDS))
                .isTrue();

        verify(mSpyMaintenanceJobService).jobFinished(mMockJobParameters, false);
        verifyNoMoreInteractions(staticMockMarker(TopicsWorker.class));

        verify(() -> TopicsWorker.getInstance(any(Context.class)), never());
        verify(mMockAppUpdateManager, never())
                .reconcileUninstalledApps(any(Context.class), eq(CURRENT_EPOCH_ID));
        verify(mMockAppUpdateManager, never())
                .reconcileInstalledApps(any(Context.class), /* currentEpochId */ anyLong());

        // Ensure Fledge job not was done
        verify(mFledgeMaintenanceTasksWorkerMock, never()).clearExpiredAdSelectionData();
        verify(mFledgeMaintenanceTasksWorkerMock, never())
                .clearInvalidFrequencyCapHistogramData(any(PackageManager.class));

        verify(mSignalsMaintenanceTasksWorkerMock, never()).clearInvalidSignals();
    }

    @Test
    public void testOnStopJob_withoutLogging() {
        // Mock static method FlagsFactory.getFlags() to return Mock Flags.
        doReturn(mMockFlags).when(FlagsFactory::getFlags);
        // Logging killswitch is on.
        doReturn(true).when(mMockFlags).getBackgroundJobsLoggingKillSwitch();

        testOnStopJob();

        // Verify logging methods are not invoked.
        verify(mSpyLogger, never()).persistJobExecutionData(anyInt(), anyLong());
        verify(mSpyLogger, never()).logExecutionStats(anyInt(), anyLong(), anyInt(), anyInt());
    }

    @Test
    public void testOnStopJob_withLogging() {
        // Mock static method FlagsFactory.getFlags() to return Mock Flags.
        doReturn(mMockFlags).when(FlagsFactory::getFlags);
        // Logging killswitch is off.
        doReturn(false).when(mMockFlags).getBackgroundJobsLoggingKillSwitch();

        testOnStopJob();

        // Verify logging methods are invoked.
        verify(mSpyLogger).logExecutionStats(anyInt(), anyLong(), anyInt(), anyInt());
    }

    @Test
    public void testScheduleIfNeeded_Success() {
        doReturn(false).when(mMockFlags).getGlobalKillSwitch();

        // Mock static method FlagsFactory.getFlags() to return Mock Flags.
        doReturn(mMockFlags).when(FlagsFactory::getFlags);

        // The first invocation of scheduleIfNeeded() schedules the job.
        assertThat(MaintenanceJobService.scheduleIfNeeded(CONTEXT, /* forceSchedule */ false))
                .isTrue();
    }

    @Test
    public void testScheduleIfNeeded_ScheduledWithSameParameters() {
        // Mock Flags in order to change values within this test
        doReturn(TEST_FLAGS.getMaintenanceJobPeriodMs())
                .when(mMockFlags)
                .getMaintenanceJobPeriodMs();
        doReturn(TEST_FLAGS.getMaintenanceJobFlexMs()).when(mMockFlags).getMaintenanceJobFlexMs();
        doReturn(false).when(mMockFlags).getGlobalKillSwitch();

        // Mock static method FlagsFactory.getFlags() to return Mock Flags.
        doReturn(mMockFlags).when(FlagsFactory::getFlags);

        // The first invocation of scheduleIfNeeded() schedules the job.
        assertThat(MaintenanceJobService.scheduleIfNeeded(CONTEXT, /* forceSchedule */ false))
                .isTrue();
        assertThat(JOB_SCHEDULER.getPendingJob(MAINTENANCE_JOB_ID)).isNotNull();

        // The second invocation of scheduleIfNeeded() with same parameters skips the scheduling.
        assertThat(MaintenanceJobService.scheduleIfNeeded(CONTEXT, /* forceSchedule */ false))
                .isFalse();
    }

    @Test
    public void testScheduleIfNeeded_ScheduledWithDifferentParameters() {
        // Mock Flags in order to change values within this test
        doReturn(TEST_FLAGS.getMaintenanceJobPeriodMs())
                .when(mMockFlags)
                .getMaintenanceJobPeriodMs();
        doReturn(TEST_FLAGS.getMaintenanceJobFlexMs()).when(mMockFlags).getMaintenanceJobFlexMs();
        doReturn(mMockFlags).when(FlagsFactory::getFlags);

        // The first invocation of scheduleIfNeeded() schedules the job.
        assertThat(MaintenanceJobService.scheduleIfNeeded(CONTEXT, /* forceSchedule */ false))
                .isTrue();
        assertThat(JOB_SCHEDULER.getPendingJob(MAINTENANCE_JOB_ID)).isNotNull();

        // Change the value of a parameter so that the second invocation of scheduleIfNeeded()
        // schedules the job.
        doReturn(TEST_FLAGS.getMaintenanceJobFlexMs() + 1)
                .when(mMockFlags)
                .getMaintenanceJobFlexMs();
        assertThat(MaintenanceJobService.scheduleIfNeeded(CONTEXT, /* forceSchedule */ false))
                .isTrue();
    }

    @Test
    public void testScheduleIfNeeded_forceRun() {
        // Mock Flags in order to change values within this test
        doReturn(TEST_FLAGS.getMaintenanceJobPeriodMs())
                .when(mMockFlags)
                .getMaintenanceJobPeriodMs();
        doReturn(TEST_FLAGS.getMaintenanceJobFlexMs()).when(mMockFlags).getMaintenanceJobFlexMs();
        doReturn(false).when(mMockFlags).getGlobalKillSwitch();

        // Mock static method FlagsFactory.getFlags() to return Mock Flags.
        doReturn(mMockFlags).when(FlagsFactory::getFlags);

        // The first invocation of scheduleIfNeeded() schedules the job.
        assertThat(MaintenanceJobService.scheduleIfNeeded(CONTEXT, /* forceSchedule */ false))
                .isTrue();
        assertThat(JOB_SCHEDULER.getPendingJob(MAINTENANCE_JOB_ID)).isNotNull();

        // The second invocation of scheduleIfNeeded() with same parameters skips the scheduling.
        assertThat(MaintenanceJobService.scheduleIfNeeded(CONTEXT, /* forceSchedule */ false))
                .isFalse();

        // The third invocation of scheduleIfNeeded() is forced and re-schedules the job.
        assertThat(MaintenanceJobService.scheduleIfNeeded(CONTEXT, /* forceSchedule */ true))
                .isTrue();
    }

    @Test
    public void testScheduleIfNeeded_scheduledWithKillSwitchOn() {
        doNothing().when(() -> ErrorLogUtil.e(anyInt(), anyInt()));
        // Killswitch is on.
        doReturn(true).when(mMockFlags).getTopicsKillSwitch();

        // Mock static method FlagsFactory.getFlags() to return Mock Flags.
        doReturn(mMockFlags).when(FlagsFactory::getFlags);

        // The first invocation of scheduleIfNeeded() schedules the job.
        assertThat(EpochJobService.scheduleIfNeeded(CONTEXT, /* forceSchedule */ false)).isFalse();
        assertThat(JOB_SCHEDULER.getPendingJob(MAINTENANCE_JOB_ID)).isNull();
        verify(
                () -> {
                    ErrorLogUtil.e(
                            eq(AD_SERVICES_ERROR_REPORTED__ERROR_CODE__TOPICS_API_DISABLED),
                            eq(AD_SERVICES_ERROR_REPORTED__PPAPI_NAME__TOPICS));
                });
    }

    @Test
    public void testSchedule_jobInfoIsPersisted() {
        final ArgumentCaptor<JobInfo> argumentCaptor = ArgumentCaptor.forClass(JobInfo.class);

        MaintenanceJobService.schedule(
                CONTEXT, mMockJobScheduler, MAINTENANCE_JOB_PERIOD_MS, MAINTENANCE_JOB_FLEX_MS);

        verify(mMockJobScheduler, times(1)).schedule(argumentCaptor.capture());
        assertThat(argumentCaptor.getValue()).isNotNull();
        assertThat(argumentCaptor.getValue().isPersisted()).isTrue();
    }

    @Test
    public void testOnStartJob_shouldDisableJobTrue_withoutLogging() throws Exception {
        // Logging killswitch is on.
        doReturn(mMockFlags).when(FlagsFactory::getFlags);
        doReturn(true).when(mMockFlags).getBackgroundJobsLoggingKillSwitch();

        testOnStartJob_shouldDisableJobTrue();

        // Verify logging method is not invoked.
        verify(mSpyLogger, never()).logExecutionStats(anyInt(), anyLong(), anyInt(), anyInt());
    }

    @Test
    public void testOnStartJob_shouldDisableJobTrue_withLoggingEnabled() throws Exception {
        // Logging killswitch is off.
        doReturn(mMockFlags).when(FlagsFactory::getFlags);
        doReturn(false).when(mMockFlags).getBackgroundJobsLoggingKillSwitch();

        testOnStartJob_shouldDisableJobTrue();

        // Verify logging has not happened even though logging is enabled because this field is not
        // logged
        verify(mSpyLogger, never()).logExecutionStats(anyInt(), anyLong(), anyInt(), anyInt());
    }

    private void testOnStartJob_killSwitchOn() throws Exception {
        // Killswitch on.
        doReturn(true).when(mMockFlags).getTopicsKillSwitch();
        doReturn(true).when(mMockFlags).getFledgeSelectAdsKillSwitch();
        doReturn(false).when(mMockFlags).getProtectedSignalsCleanupEnabled();

        // Mock static method FlagsFactory.getFlags() to return Mock Flags.
        doReturn(mMockFlags).when(FlagsFactory::getFlags);

        CountDownLatch jobCompletionLatch = new CountDownLatch(1);
        doAnswer(
                        unusedInvocation -> {
                            jobCompletionLatch.countDown();
                            return null;
                        })
                .when(mSpyMaintenanceJobService)
                .jobFinished(mMockJobParameters, false);

        // Inject FledgeMaintenanceTasksWorker since the test can't get it the standard way
        doReturn(mPackageManagerMock).when(mSpyMaintenanceJobService).getPackageManager();
        mSpyMaintenanceJobService.injectFledgeMaintenanceTasksWorker(
                mFledgeMaintenanceTasksWorkerMock);
        mSpyMaintenanceJobService.injectSignalsMaintenanceTasksWorker(
                mSignalsMaintenanceTasksWorkerMock);

        // Schedule the job to assert after starting that the scheduled job has been cancelled
        JobInfo existingJobInfo =
                new JobInfo.Builder(
                                MAINTENANCE_JOB_ID,
                                new ComponentName(CONTEXT, EpochJobService.class))
                        .setRequiresCharging(true)
                        .setPeriodic(MAINTENANCE_JOB_PERIOD_MS, MAINTENANCE_JOB_FLEX_MS)
                        .setPersisted(true)
                        .build();
        JOB_SCHEDULER.schedule(existingJobInfo);
        assertNotNull(JOB_SCHEDULER.getPendingJob(MAINTENANCE_JOB_ID));

        // Now verify that when the Job starts, it will unschedule itself.
        assertFalse(mSpyMaintenanceJobService.onStartJob(mMockJobParameters));

        assertNull(JOB_SCHEDULER.getPendingJob(MAINTENANCE_JOB_ID));

        assertWithMessage("Job completed within timeout")
                .that(jobCompletionLatch.await(1, TimeUnit.SECONDS))
                .isTrue();

        verify(mSpyMaintenanceJobService).jobFinished(mMockJobParameters, false);
        verifyNoMoreInteractions(staticMockMarker(TopicsWorker.class));
        verify(mFledgeMaintenanceTasksWorkerMock, never()).clearExpiredAdSelectionData();
        verify(mFledgeMaintenanceTasksWorkerMock, never())
                .clearInvalidFrequencyCapHistogramData(any(PackageManager.class));
        verify(mSignalsMaintenanceTasksWorkerMock, never()).clearInvalidSignals();
    }

    private void testOnStartJob_killSwitchOff() throws InterruptedException {
        final TopicsWorker topicsWorker =
                new TopicsWorker(
                        mMockEpochManager,
                        mMockCacheManager,
                        mBlockedTopicsManager,
                        mMockAppUpdateManager,
                        TEST_FLAGS);
        // Killswitch is off.
        doReturn(false).when(mMockFlags).getTopicsKillSwitch();
        doReturn(false).when(mMockFlags).getFledgeSelectAdsKillSwitch();
        doReturn(true).when(mMockFlags).getProtectedSignalsCleanupEnabled();
        doReturn(CURRENT_EPOCH_ID).when(mMockEpochManager).getCurrentEpochId();
        doReturn(TEST_FLAGS.getAdSelectionExpirationWindowS())
                .when(mMockFlags)
                .getAdSelectionExpirationWindowS();

        // Mock static method FlagsFactory.getFlags() to return Mock Flags.
        doReturn(mMockFlags).when(FlagsFactory::getFlags);

        // Mock static method AppUpdateWorker.getInstance, let it return the local
        // appUpdateWorker in order to get a test instance.
        doReturn(topicsWorker).when(() -> TopicsWorker.getInstance(any(Context.class)));

        CountDownLatch jobCompletionLatch = new CountDownLatch(1);
        doAnswer(
                        unusedInvocation -> {
                            jobCompletionLatch.countDown();
                            return null;
                        })
                .when(mSpyMaintenanceJobService)
                .jobFinished(mMockJobParameters, false);

        doReturn(mPackageManagerMock).when(mSpyMaintenanceJobService).getPackageManager();
        mSpyMaintenanceJobService.injectFledgeMaintenanceTasksWorker(
                mFledgeMaintenanceTasksWorkerMock);
        mSpyMaintenanceJobService.injectSignalsMaintenanceTasksWorker(
                mSignalsMaintenanceTasksWorkerMock);

        // Schedule the job to assert after starting that the scheduled job has been started
        JobInfo existingJobInfo =
                new JobInfo.Builder(
                        MAINTENANCE_JOB_ID,
                        new ComponentName(CONTEXT, EpochJobService.class))
                        .setRequiresCharging(true)
                        .setPeriodic(MAINTENANCE_JOB_PERIOD_MS, MAINTENANCE_JOB_FLEX_MS)
                        .setPersisted(true)
                        .build();
        JOB_SCHEDULER.schedule(existingJobInfo);
        assertThat(JOB_SCHEDULER.getPendingJob(MAINTENANCE_JOB_ID)).isNotNull();

        // Now verify that when the Job starts, it will schedule itself.
        assertThat(mSpyMaintenanceJobService.onStartJob(mMockJobParameters)).isTrue();

        assertThat(JOB_SCHEDULER.getPendingJob(MAINTENANCE_JOB_ID)).isNotNull();

        assertWithMessage("Job completed within timeout")
                .that(jobCompletionLatch.await(1, TimeUnit.SECONDS))
                .isTrue();

        verify(() -> TopicsWorker.getInstance(any(Context.class)));
        verify(mMockAppUpdateManager)
                .reconcileUninstalledApps(any(Context.class), eq(CURRENT_EPOCH_ID));
        verify(mMockAppUpdateManager)
                .reconcileInstalledApps(any(Context.class), /* currentEpochId */ anyLong());

        // Ensure Fledge job was done
        verify(mFledgeMaintenanceTasksWorkerMock).clearExpiredAdSelectionData();
        verify(mFledgeMaintenanceTasksWorkerMock)
                .clearInvalidFrequencyCapHistogramData(any(PackageManager.class));

        verify(mSignalsMaintenanceTasksWorkerMock).clearInvalidSignals();
    }

    private void testOnStopJob() {
        // Verify nothing throws
        mSpyMaintenanceJobService.onStopJob(mMockJobParameters);
    }

    private void testOnStartJob_shouldDisableJobTrue() throws Exception {
        doReturn(true).when(mMockFlags).getProtectedSignalsCleanupEnabled();
        doReturn(true)
                .when(
                        () ->
                                ServiceCompatUtils.shouldDisableExtServicesJobOnTPlus(
                                        any(Context.class)));

        CountDownLatch jobCompletionLatch = new CountDownLatch(1);
        doAnswer(
                        unusedInvocation -> {
                            jobCompletionLatch.countDown();
                            return null;
                        })
                .when(mSpyMaintenanceJobService)
                .jobFinished(mMockJobParameters, false);

        // Inject FledgeMaintenanceTasksWorker since the test can't get it the standard way
        doReturn(mPackageManagerMock).when(mSpyMaintenanceJobService).getPackageManager();
        mSpyMaintenanceJobService.injectFledgeMaintenanceTasksWorker(
                mFledgeMaintenanceTasksWorkerMock);
        mSpyMaintenanceJobService.injectSignalsMaintenanceTasksWorker(
                mSignalsMaintenanceTasksWorkerMock);

        // Schedule the job to assert after starting that the scheduled job has been cancelled
        JobInfo existingJobInfo =
                new JobInfo.Builder(
                                MAINTENANCE_JOB_ID,
                                new ComponentName(CONTEXT, EpochJobService.class))
                        .setRequiresCharging(true)
                        .setPeriodic(MAINTENANCE_JOB_PERIOD_MS, MAINTENANCE_JOB_FLEX_MS)
                        .setPersisted(true)
                        .build();
        JOB_SCHEDULER.schedule(existingJobInfo);
        assertNotNull(JOB_SCHEDULER.getPendingJob(MAINTENANCE_JOB_ID));

        // Now verify that when the Job starts, it will unschedule itself.
        assertFalse(mSpyMaintenanceJobService.onStartJob(mMockJobParameters));

        assertNull(JOB_SCHEDULER.getPendingJob(MAINTENANCE_JOB_ID));

        assertWithMessage("Job completed within timeout")
                .that(jobCompletionLatch.await(1, TimeUnit.SECONDS))
                .isTrue();

        verify(mSpyMaintenanceJobService).jobFinished(mMockJobParameters, false);
        verifyNoMoreInteractions(staticMockMarker(TopicsWorker.class));
        verify(mFledgeMaintenanceTasksWorkerMock, never()).clearExpiredAdSelectionData();
        verify(mFledgeMaintenanceTasksWorkerMock, never())
                .clearInvalidFrequencyCapHistogramData(any(PackageManager.class));

        verify(mSignalsMaintenanceTasksWorkerMock, never()).clearInvalidSignals();
    }
}

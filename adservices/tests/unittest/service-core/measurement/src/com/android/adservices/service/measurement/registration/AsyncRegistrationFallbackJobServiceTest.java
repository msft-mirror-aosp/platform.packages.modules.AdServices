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

package com.android.adservices.service.measurement.registration;

import static com.android.adservices.shared.spe.JobServiceConstants.SCHEDULING_RESULT_CODE_SUCCESSFUL;
import static com.android.adservices.spe.AdServicesJobInfo.MEASUREMENT_ASYNC_REGISTRATION_FALLBACK_JOB;
import static com.android.dx.mockito.inline.extended.ExtendedMockito.doReturn;

import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertNotNull;
import static org.junit.Assert.assertTrue;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyBoolean;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.doAnswer;
import static org.mockito.Mockito.doNothing;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.timeout;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import android.app.job.JobInfo;
import android.app.job.JobParameters;
import android.app.job.JobScheduler;
import android.content.ComponentName;
import android.content.Context;

import com.android.adservices.data.enrollment.EnrollmentDao;
import com.android.adservices.data.measurement.DatastoreManager;
import com.android.adservices.data.measurement.DatastoreManagerFactory;
import com.android.adservices.service.FlagsFactory;
import com.android.adservices.service.common.compat.ServiceCompatUtils;
import com.android.adservices.service.measurement.MeasurementJobServiceTestCase;
import com.android.adservices.service.stats.AdServicesLoggerImpl;
import com.android.adservices.shared.common.ApplicationContextSingleton;
import com.android.adservices.shared.testing.JobServiceLoggingCallback;
import com.android.adservices.shared.testing.concurrency.JobServiceCallback;
import com.android.adservices.spe.AdServicesJobScheduler;
import com.android.adservices.spe.AdServicesJobServiceLogger;
import com.android.compatibility.common.util.TestUtils;
import com.android.dx.mockito.inline.extended.ExtendedMockito;
import com.android.modules.utils.testing.ExtendedMockitoRule.MockStatic;
import com.android.modules.utils.testing.ExtendedMockitoRule.SpyStatic;

import org.junit.Before;
import org.junit.Test;
import org.mockito.Mockito;
import org.mockito.internal.stubbing.answers.AnswersWithDelay;
import org.mockito.internal.stubbing.answers.CallsRealMethods;

import java.util.Optional;
import java.util.concurrent.TimeUnit;

@SpyStatic(AsyncRegistrationFallbackJobService.class)
@SpyStatic(AdServicesLoggerImpl.class)
@SpyStatic(DatastoreManagerFactory.class)
@SpyStatic(EnrollmentDao.class)
@SpyStatic(FlagsFactory.class)
@SpyStatic(AdServicesJobServiceLogger.class)
@SpyStatic(ApplicationContextSingleton.class)
@MockStatic(ServiceCompatUtils.class)
public class AsyncRegistrationFallbackJobServiceTest
        extends MeasurementJobServiceTestCase<AsyncRegistrationFallbackJobService> {
    private static final int MEASUREMENT_ASYNC_REGISTRATION_FALLBACK_JOB_ID =
            MEASUREMENT_ASYNC_REGISTRATION_FALLBACK_JOB.getJobId();
    private static final long WAIT_IN_MILLIS = 1_000L;
    private static final long JOB_PERIOD_MS = TimeUnit.HOURS.toMillis(1);

    @Override
    protected AsyncRegistrationFallbackJobService getSpiedService() {
        return new AsyncRegistrationFallbackJobService();
    }

    @Before
    public void setUp() {
        when(mMockFlags.getMeasurementAsyncRegistrationFallbackJobRequiredBatteryNotLow())
                .thenReturn(true);
        when(mMockFlags.getAsyncRegistrationJobQueueIntervalMs()).thenReturn(JOB_PERIOD_MS);
        when(mMockFlags.getMeasurementAsyncRegistrationFallbackJobRequiredNetworkType())
                .thenReturn(JobInfo.NETWORK_TYPE_ANY);
        when(mMockFlags.getMeasurementAsyncRegistrationFallbackJobPersisted()).thenReturn(true);

        // By default, disable SPE
        when(mMockFlags.getSpeOnAsyncRegistrationFallbackJobEnabled()).thenReturn(false);
    }

    @Test
    public void onStartJob_killSwitchOn_withLogging() throws Exception {
        runWithMocks(
                () -> {
                    JobServiceLoggingCallback callback = syncLogExecutionStats(mSpyLogger);

                    onStartJob_killSwitchOn();

                    verifyBackgroundJobsSkipLogged(mSpyLogger, callback);
                });
    }

    @Test
    public void onStartJob_killSwitchOff_withLogging() throws Exception {
        runWithMocks(
                () -> {
                    JobServiceLoggingCallback onStartJobCallback =
                            syncPersistJobExecutionData(mSpyLogger);
                    JobServiceLoggingCallback onJobDoneCallback = syncLogExecutionStats(mSpyLogger);

                    onStartJob_killSwitchOff();

                    verifyJobFinishedLogged(mSpyLogger, onStartJobCallback, onJobDoneCallback);
                });
    }

    @Test
    public void onStartJob_killSwitchOff_unlockingCheck() throws Exception {
        runWithMocks(
                () -> {
                    // Setup
                    disableKillSwitch();

                    doReturn(SCHEDULING_RESULT_CODE_SUCCESSFUL)
                            .when(
                                    () ->
                                            AsyncRegistrationFallbackJobService.scheduleIfNeeded(
                                                    anyBoolean()));

                    JobServiceCallback callback =
                            new JobServiceCallback().expectJobFinished(mSpyService);

                    // Execute
                    mSpyService.onStartJob(Mockito.mock(JobParameters.class));
                    callback.assertJobFinished();

                    callback = new JobServiceCallback().expectJobFinished(mSpyService);
                    boolean result = mSpyService.onStartJob(Mockito.mock(JobParameters.class));

                    callback.assertJobFinished();

                    // Validate
                    assertTrue(result);

                    // Verify the job ran successfully twice
                    ExtendedMockito.verify(mSpyService, times(2)).jobFinished(any(), anyBoolean());
                    verify(mMockJobScheduler, never())
                            .cancel(eq(MEASUREMENT_ASYNC_REGISTRATION_FALLBACK_JOB_ID));
                });
    }

    @Test
    public void onStartJob_shouldDisableJobTrue_withLoggingEnabled() throws Exception {
        runWithMocks(
                () -> {
                    mocker.mockGetFlags(mMockFlags);

                    onStartJob_shouldDisableJobTrue();

                    // Verify logging has not happened even though logging is enabled because this
                    // field is not logged
                    verifyLoggingNotHappened(mSpyLogger);
                });
    }

    @Test
    @SpyStatic(AdServicesJobScheduler.class)
    public void onStartJob_speEnabled() {
        mocker.mockGetFlags(mMockFlags);
        when(mMockFlags.getSpeOnAsyncRegistrationFallbackJobEnabled()).thenReturn(true);

        AdServicesJobScheduler mockedAdServicesJobScheduler = mock(AdServicesJobScheduler.class);
        JobParameters mockedJobParameters = mock(JobParameters.class);
        mocker.mockSpeJobScheduler(mockedAdServicesJobScheduler);

        mSpyService.onStartJob(mockedJobParameters);

        verify(mockedAdServicesJobScheduler).schedule(any());
    }

    @Test
    public void scheduleIfNeeded_killSwitchOn_dontSchedule() throws Exception {
        runWithMocks(
                () -> {
                    // Setup
                    enableKillSwitch();

                    appContext.set(mSpyContext);
                    doReturn(mMockJobScheduler)
                            .when(mSpyContext)
                            .getSystemService(JobScheduler.class);
                    final JobInfo mockJobInfo = mock(JobInfo.class);
                    doReturn(mockJobInfo)
                            .when(mMockJobScheduler)
                            .getPendingJob(eq(MEASUREMENT_ASYNC_REGISTRATION_FALLBACK_JOB_ID));

                    // Execute
                    AsyncRegistrationFallbackJobService.scheduleIfNeeded(
                            /* forceSchedule= */ false);

                    // Validate
                    ExtendedMockito.verify(
                            () -> AsyncRegistrationFallbackJobService.schedule(any(), any()),
                            never());
                    verify(mMockJobScheduler, never())
                            .getPendingJob(eq(MEASUREMENT_ASYNC_REGISTRATION_FALLBACK_JOB_ID));
                });
    }

    @Test
    public void scheduleIfNeeded_sameJobInfoDontForceSchedule_dontSchedule() throws Exception {
        runWithMocks(
                () -> {
                    // Setup
                    disableKillSwitch();

                    appContext.set(mSpyContext);
                    doReturn(mMockJobScheduler)
                            .when(mSpyContext)
                            .getSystemService(JobScheduler.class);
                    final JobInfo mockJobInfo =
                            new JobInfo.Builder(
                                            MEASUREMENT_ASYNC_REGISTRATION_FALLBACK_JOB_ID,
                                            new ComponentName(
                                                    mSpyContext,
                                                    AsyncRegistrationFallbackJobService.class))
                                    .setRequiresBatteryNotLow(true)
                                    .setPeriodic(JOB_PERIOD_MS)
                                    .setRequiredNetworkType(JobInfo.NETWORK_TYPE_ANY)
                                    .setPersisted(true)
                                    .build();
                    doReturn(mockJobInfo)
                            .when(mMockJobScheduler)
                            .getPendingJob(eq(MEASUREMENT_ASYNC_REGISTRATION_FALLBACK_JOB_ID));

                    // Execute
                    AsyncRegistrationFallbackJobService.scheduleIfNeeded(
                            /* forceSchedule= */ false);

                    // Validate
                    ExtendedMockito.verify(
                            () -> AsyncRegistrationFallbackJobService.schedule(any(), any()),
                            never());
                    verify(mMockJobScheduler, times(1))
                            .getPendingJob(eq(MEASUREMENT_ASYNC_REGISTRATION_FALLBACK_JOB_ID));
                });
    }

    @Test
    public void scheduleIfNeeded_diffJobInfoDontForceSchedule_doesSchedule() throws Exception {
        runWithMocks(
                () -> {
                    // Setup
                    disableKillSwitch();

                    appContext.set(mSpyContext);
                    doReturn(mMockJobScheduler)
                            .when(mSpyContext)
                            .getSystemService(JobScheduler.class);
                    final JobInfo mockJobInfo =
                            new JobInfo.Builder(
                                            MEASUREMENT_ASYNC_REGISTRATION_FALLBACK_JOB_ID,
                                            new ComponentName(
                                                    mSpyContext,
                                                    AsyncRegistrationFallbackJobService.class))
                                    // Difference
                                    .setRequiresBatteryNotLow(false)
                                    .setPeriodic(JOB_PERIOD_MS)
                                    .setRequiredNetworkType(JobInfo.NETWORK_TYPE_ANY)
                                    .setPersisted(true)
                                    .build();
                    doReturn(mockJobInfo)
                            .when(mMockJobScheduler)
                            .getPendingJob(eq(MEASUREMENT_ASYNC_REGISTRATION_FALLBACK_JOB_ID));

                    // Execute
                    AsyncRegistrationFallbackJobService.scheduleIfNeeded(
                            /* forceSchedule= */ false);

                    // Validate
                    ExtendedMockito.verify(
                            () -> AsyncRegistrationFallbackJobService.schedule(any(), any()));
                    verify(mMockJobScheduler, times(1))
                            .getPendingJob(eq(MEASUREMENT_ASYNC_REGISTRATION_FALLBACK_JOB_ID));
                });
    }

    @Test
    public void scheduleIfNeeded_killSwitchOff_previouslyExecuted_forceSchedule_schedule()
            throws Exception {
        runWithMocks(
                () -> {
                    // Setup
                    disableKillSwitch();

                    appContext.set(mSpyContext);
                    doReturn(mMockJobScheduler)
                            .when(mSpyContext)
                            .getSystemService(JobScheduler.class);
                    final JobInfo mockJobInfo = mock(JobInfo.class);
                    doReturn(mockJobInfo)
                            .when(mMockJobScheduler)
                            .getPendingJob(eq(MEASUREMENT_ASYNC_REGISTRATION_FALLBACK_JOB_ID));

                    // Execute
                    AsyncRegistrationFallbackJobService.scheduleIfNeeded(/* forceSchedule= */ true);

                    // Validate
                    ExtendedMockito.verify(
                            () -> AsyncRegistrationFallbackJobService.schedule(any(), any()),
                            times(1));
                    verify(mMockJobScheduler, times(1))
                            .getPendingJob(eq(MEASUREMENT_ASYNC_REGISTRATION_FALLBACK_JOB_ID));
                });
    }

    @Test
    public void scheduleIfNeeded_killSwitchOff_previouslyNotExecuted_dontForceSchedule_schedule()
            throws Exception {
        runWithMocks(
                () -> {
                    // Setup
                    disableKillSwitch();

                    appContext.set(mSpyContext);
                    doReturn(mMockJobScheduler)
                            .when(mSpyContext)
                            .getSystemService(JobScheduler.class);
                    // Mock the JobScheduler to have no pending job.
                    doReturn(null)
                            .when(mMockJobScheduler)
                            .getPendingJob(eq(MEASUREMENT_ASYNC_REGISTRATION_FALLBACK_JOB_ID));

                    // Execute
                    AsyncRegistrationFallbackJobService.scheduleIfNeeded(false);

                    // Validate
                    ExtendedMockito.verify(
                            () -> AsyncRegistrationFallbackJobService.schedule(any(), any()),
                            times(1));
                    verify(mMockJobScheduler, times(1))
                            .getPendingJob(eq(MEASUREMENT_ASYNC_REGISTRATION_FALLBACK_JOB_ID));
                });
    }

    @Test
    public void testOnStopJob_stopsExecutingThread() throws Exception {
        runWithMocks(
                () -> {
                    disableKillSwitch();

                    doAnswer(new AnswersWithDelay(WAIT_IN_MILLIS * 10, new CallsRealMethods()))
                            .when(mSpyService)
                            .processAsyncRecords();
                    mSpyService.onStartJob(Mockito.mock(JobParameters.class));
                    Thread.sleep(WAIT_IN_MILLIS);

                    assertNotNull(mSpyService.getFutureForTesting());

                    boolean onStopJobResult =
                            mSpyService.onStopJob(Mockito.mock(JobParameters.class));
                    verify(mSpyService, times(0)).jobFinished(any(), anyBoolean());
                    assertTrue(onStopJobResult);
                    assertTrue(mSpyService.getFutureForTesting().isCancelled());
                });
    }

    private void onStartJob_shouldDisableJobTrue() throws Exception {
        // Setup
        doReturn(true)
                .when(
                        () ->
                                ServiceCompatUtils.shouldDisableExtServicesJobOnTPlus(
                                        any(Context.class)));

        JobServiceCallback callback = new JobServiceCallback().expectJobFinished(mSpyService);

        // Execute
        boolean result = mSpyService.onStartJob(mock(JobParameters.class));

        // Validate
        assertFalse(result);

        callback.assertJobFinished();
        verify(mSpyService, times(1)).jobFinished(any(), eq(false));
        verify(mMockJobScheduler, times(1))
                .cancel(eq(MEASUREMENT_ASYNC_REGISTRATION_FALLBACK_JOB_ID));
    }

    private void onStartJob_killSwitchOn() throws Exception {
        // Setup
        enableKillSwitch();

        JobServiceCallback callback = new JobServiceCallback().expectJobFinished(mSpyService);

        // Execute
        boolean result = mSpyService.onStartJob(mock(JobParameters.class));

        // Validate
        assertFalse(result);

        callback.assertJobFinished();
        verify(mSpyService, times(1)).jobFinished(any(), eq(false));
        verify(mMockJobScheduler, times(1))
                .cancel(eq(MEASUREMENT_ASYNC_REGISTRATION_FALLBACK_JOB_ID));
    }

    private void onStartJob_killSwitchOff() throws Exception {
        // Setup
        disableKillSwitch();
        doReturn(SCHEDULING_RESULT_CODE_SUCCESSFUL)
                .when(() -> AsyncRegistrationFallbackJobService.scheduleIfNeeded(anyBoolean()));

        JobServiceCallback callback = new JobServiceCallback().expectJobFinished(mSpyService);

        // Execute
        boolean result = mSpyService.onStartJob(mock(JobParameters.class));

        // Validate
        assertTrue(result);

        callback.assertJobFinished();

        // Blocks until background thread finishes
        mSpyService.getFutureForTesting().get();
        verify(mSpyService, timeout(WAIT_IN_MILLIS).times(1)).jobFinished(any(), anyBoolean());
        verify(mMockJobScheduler, never())
                .cancel(eq(MEASUREMENT_ASYNC_REGISTRATION_FALLBACK_JOB_ID));
    }

    private void runWithMocks(TestUtils.RunnableWithThrow execute) throws Exception {
        Context context = Mockito.mock(Context.class);
        doReturn(sContext.getPackageName()).when(context).getPackageName();
        doReturn(sContext.getPackageManager()).when(context).getPackageManager();
        // Setup mock everything in job
        mMockDatastoreManager = mock(DatastoreManager.class);
        doReturn(Optional.empty()).when(mMockDatastoreManager).runInTransactionWithResult(any());
        doNothing().when(mSpyService).jobFinished(any(), anyBoolean());
        doReturn(mMockJobScheduler).when(mSpyService).getSystemService(JobScheduler.class);
        doReturn(context).when(mSpyService).getApplicationContext();
        doReturn(mock(EnrollmentDao.class)).when(EnrollmentDao::getInstance);
        doReturn(mock(AdServicesLoggerImpl.class)).when(AdServicesLoggerImpl::getInstance);
        ExtendedMockito.doNothing()
                .when(() -> AsyncRegistrationFallbackJobService.schedule(any(), any()));
        doReturn(mMockDatastoreManager).when(DatastoreManagerFactory::getDatastoreManager);
        doReturn(false)
                .when(
                        () ->
                                ServiceCompatUtils.shouldDisableExtServicesJobOnTPlus(
                                        any(Context.class)));
        mocker.mockGetAdServicesJobServiceLogger(mSpyLogger);

        // Execute
        execute.run();
    }

    @Override
    protected void toggleFeature(boolean value) {
        when(mMockFlags.getAsyncRegistrationFallbackJobKillSwitch()).thenReturn(!value);
    }
}

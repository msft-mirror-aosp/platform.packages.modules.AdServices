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

package com.android.adservices.service.measurement.reporting;

import static com.android.adservices.mockito.ExtendedMockitoExpectations.mockGetAdServicesJobServiceLogger;
import static com.android.adservices.mockito.MockitoExpectations.getSpiedAdServicesJobServiceLogger;
import static com.android.adservices.mockito.MockitoExpectations.mockBackgroundJobsLoggingKillSwitch;
import static com.android.adservices.mockito.MockitoExpectations.syncLogExecutionStats;
import static com.android.adservices.mockito.MockitoExpectations.syncPersistJobExecutionData;
import static com.android.adservices.mockito.MockitoExpectations.verifyBackgroundJobsSkipLogged;
import static com.android.adservices.mockito.MockitoExpectations.verifyJobFinishedLogged;
import static com.android.adservices.mockito.MockitoExpectations.verifyLoggingNotHappened;
import static com.android.adservices.service.Flags.MEASUREMENT_REPORTING_JOB_PERSISTED;
import static com.android.adservices.service.Flags.MEASUREMENT_REPORTING_JOB_REQUIRED_BATTERY_NOT_LOW;
import static com.android.adservices.service.Flags.MEASUREMENT_REPORTING_JOB_REQUIRED_NETWORK_TYPE;
import static com.android.adservices.service.Flags.MEASUREMENT_REPORTING_JOB_SERVICE_BATCH_WINDOW_MILLIS;
import static com.android.adservices.service.Flags.MEASUREMENT_REPORTING_JOB_SERVICE_MIN_EXECUTION_WINDOW_MILLIS;
import static com.android.adservices.spe.AdServicesJobInfo.MEASUREMENT_REPORTING_JOB;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertNotNull;
import static org.junit.Assert.assertTrue;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyBoolean;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.doAnswer;
import static org.mockito.Mockito.doNothing;
import static org.mockito.Mockito.doReturn;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.spy;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import android.app.job.JobInfo;
import android.app.job.JobParameters;
import android.app.job.JobScheduler;
import android.content.ComponentName;
import android.content.Context;

import androidx.test.core.app.ApplicationProvider;

import com.android.adservices.data.enrollment.EnrollmentDao;
import com.android.adservices.data.measurement.DatastoreException;
import com.android.adservices.data.measurement.DatastoreManager;
import com.android.adservices.data.measurement.DatastoreManagerFactory;
import com.android.adservices.data.measurement.IMeasurementDao;
import com.android.adservices.data.measurement.ITransaction;
import com.android.adservices.mockito.AdServicesExtendedMockitoRule;
import com.android.adservices.service.Flags;
import com.android.adservices.service.FlagsFactory;
import com.android.adservices.service.common.compat.ServiceCompatUtils;
import com.android.adservices.service.measurement.KeyValueData;
import com.android.adservices.service.measurement.attribution.AttributionJobService;
import com.android.adservices.shared.errorlogging.AdServicesErrorLogger;
import com.android.adservices.shared.testing.JobServiceLoggingCallback;
import com.android.adservices.shared.testing.concurrency.JobServiceCallback;
import com.android.adservices.spe.AdServicesJobServiceLogger;
import com.android.dx.mockito.inline.extended.ExtendedMockito;

import org.junit.Before;
import org.junit.Rule;
import org.junit.Test;
import org.mockito.ArgumentCaptor;
import org.mockito.Mock;
import org.mockito.Mockito;
import org.mockito.internal.stubbing.answers.AnswersWithDelay;
import org.mockito.internal.stubbing.answers.CallsRealMethods;
import org.mockito.quality.Strictness;

import java.util.Optional;

/** Unit test for {@link ReportingJobService} */
public class ReportingJobServiceTest {
    private static final Context CONTEXT = ApplicationProvider.getApplicationContext();
    private static final int MEASUREMENT_REPORTING_JOB_ID = MEASUREMENT_REPORTING_JOB.getJobId();
    private static final long WAIT_IN_MILLIS = 200L;
    @Mock private AdServicesErrorLogger mErrorLogger;
    @Mock private IMeasurementDao mMeasurementDao;
    @Mock private ITransaction mTransaction;
    private DatastoreManager mMockDatastoreManager;
    private JobScheduler mMockJobScheduler;

    private ReportingJobService mSpyService;
    private Flags mMockFlags;
    private AdServicesJobServiceLogger mSpyLogger;

    @Rule
    public final AdServicesExtendedMockitoRule adServicesExtendedMockitoRule =
            new AdServicesExtendedMockitoRule.Builder(this)
                    .spyStatic(ReportingJobService.class)
                    .spyStatic(DatastoreManagerFactory.class)
                    .spyStatic(EnrollmentDao.class)
                    .spyStatic(FlagsFactory.class)
                    .spyStatic(AdServicesJobServiceLogger.class)
                    .mockStatic(ServiceCompatUtils.class)
                    .setStrictness(Strictness.LENIENT)
                    .build();

    private Context mContext;

    private class FakeDatastoreManager extends DatastoreManager {
        private FakeDatastoreManager() {
            super(mErrorLogger);
        }

        @Override
        public ITransaction createNewTransaction() {
            return mTransaction;
        }

        @Override
        public IMeasurementDao getMeasurementDao() {
            return mMeasurementDao;
        }

        @Override
        protected int getDataStoreVersion() {
            return 0;
        }
    }

    @Before
    public void setUp() {
        mSpyService = spy(ReportingJobService.class);
        mMockDatastoreManager = new FakeDatastoreManager();
        mMockJobScheduler = spy(JobScheduler.class);
        mMockFlags = mock(Flags.class);
        mSpyLogger = getSpiedAdServicesJobServiceLogger(CONTEXT, mMockFlags);
        mContext = mock(Context.class);
        doReturn(CONTEXT.getPackageName()).when(mContext).getPackageName();
        doReturn(CONTEXT.getPackageManager()).when(mContext).getPackageManager();
        doReturn(mMockJobScheduler).when(mContext).getSystemService(JobScheduler.class);
        doReturn(mMockJobScheduler).when(mSpyService).getSystemService(JobScheduler.class);
        doReturn(mContext).when(mSpyService).getApplicationContext();
        doNothing().when(mSpyService).jobFinished(any(), anyBoolean());
        ExtendedMockito.doReturn(mMockDatastoreManager)
                .when(() -> DatastoreManagerFactory.getDatastoreManager(any()));
        ExtendedMockito.doReturn(mMockFlags).when(FlagsFactory::getFlags);
        doReturn(MEASUREMENT_REPORTING_JOB_SERVICE_BATCH_WINDOW_MILLIS)
                .when(mMockFlags)
                .getMeasurementReportingJobServiceBatchWindowMillis();
        doReturn(MEASUREMENT_REPORTING_JOB_SERVICE_MIN_EXECUTION_WINDOW_MILLIS)
                .when(mMockFlags)
                .getMeasurementReportingJobServiceMinExecutionWindowMillis();
        doReturn(MEASUREMENT_REPORTING_JOB_REQUIRED_NETWORK_TYPE)
                .when(mMockFlags)
                .getMeasurementReportingJobRequiredNetworkType();
        doReturn(MEASUREMENT_REPORTING_JOB_REQUIRED_BATTERY_NOT_LOW)
                .when(mMockFlags)
                .getMeasurementReportingJobRequiredBatteryNotLow();
        doReturn(MEASUREMENT_REPORTING_JOB_PERSISTED)
                .when(mMockFlags)
                .getMeasurementReportingJobPersisted();
        mockGetAdServicesJobServiceLogger(mSpyLogger);
    }

    @Test
    public void scheduleIfNeeded_nextReportInFuture_noPendingJobs_schedule() throws Exception {
        // Setup
        enableFeature();
        // next report is scheduled 100s from now.
        int timeToNextReport = 100000;
        long nextReportTime = System.currentTimeMillis() + timeToNextReport;
        when(mMeasurementDao.getLatestReportTimeInBatchWindow(
                        MEASUREMENT_REPORTING_JOB_SERVICE_BATCH_WINDOW_MILLIS))
                .thenReturn(nextReportTime);
        mockGettingLastExecutionTime(Long.MIN_VALUE);
        mockGettingNextExecutionTime(null);
        // Execute
        ReportingJobService.scheduleIfNeeded(mContext, false);

        // Validate
        ArgumentCaptor<JobInfo> jobInfoArgumentCaptor = ArgumentCaptor.forClass(JobInfo.class);
        verify(mMockJobScheduler, times(1)).schedule(jobInfoArgumentCaptor.capture());

        JobInfo scheduledJob = jobInfoArgumentCaptor.getValue();
        long minLatencyMs = scheduledJob.getMinLatencyMillis();
        assertTrue(minLatencyMs > 0);
        assertTrue(minLatencyMs <= timeToNextReport);
        assertEquals(
                MEASUREMENT_REPORTING_JOB_REQUIRED_BATTERY_NOT_LOW,
                scheduledJob.isRequireBatteryNotLow());
        assertEquals(MEASUREMENT_REPORTING_JOB_PERSISTED, scheduledJob.isPersisted());
    }

    @Test
    public void scheduleIfNeeded_nextReportInFuture_onePendingJob_reschedule() throws Exception {
        // Setup
        enableFeature();
        long now = System.currentTimeMillis();
        // next report is scheduled 100s from now.
        int timeToNextReport = 100000;
        long nextReportTime = now + timeToNextReport;
        when(mMeasurementDao.getLatestReportTimeInBatchWindow(
                        MEASUREMENT_REPORTING_JOB_SERVICE_BATCH_WINDOW_MILLIS))
                .thenReturn(nextReportTime);
        mockGettingLastExecutionTime(Long.MIN_VALUE);
        JobInfo pendingJob =
                new JobInfo.Builder(
                                MEASUREMENT_REPORTING_JOB_ID,
                                new ComponentName(mContext, AttributionJobService.class))
                        .setMinimumLatency(
                                MEASUREMENT_REPORTING_JOB_SERVICE_BATCH_WINDOW_MILLIS + 1)
                        .setPersisted(true)
                        .build();
        when(mMockJobScheduler.getPendingJob(MEASUREMENT_REPORTING_JOB_ID)).thenReturn(pendingJob);
        // next job is scheduled just outside the batching window.
        mockGettingNextExecutionTime(
                now + MEASUREMENT_REPORTING_JOB_SERVICE_BATCH_WINDOW_MILLIS + 1);

        // Execute
        ReportingJobService.scheduleIfNeeded(mContext, false);

        // Validate
        ArgumentCaptor<JobInfo> jobInfoArgumentCaptor = ArgumentCaptor.forClass(JobInfo.class);
        verify(mMockJobScheduler, times(1)).schedule(jobInfoArgumentCaptor.capture());

        JobInfo scheduledJob = jobInfoArgumentCaptor.getValue();
        long minLatencyMs = scheduledJob.getMinLatencyMillis();

        assertTrue(minLatencyMs > 0);
        assertTrue(minLatencyMs <= timeToNextReport);
    }

    @Test
    public void scheduleIfNeeded_nextReportInFuture_onePendingJobWithSameTime_dontReschedule()
            throws Exception {
        // Setup
        enableFeature();
        long now = System.currentTimeMillis();
        // next report is scheduled 100s from now.
        int timeToNextReport = 100000;
        long nextReportTime = now + timeToNextReport;
        when(mMeasurementDao.getLatestReportTimeInBatchWindow(
                        MEASUREMENT_REPORTING_JOB_SERVICE_BATCH_WINDOW_MILLIS))
                .thenReturn(nextReportTime);

        mockGettingLastExecutionTime(Long.MIN_VALUE);
        KeyValueData nextExecutionTimeKV =
                new KeyValueData.Builder()
                        .setKey("job_last_execution_time")
                        .setDataType(KeyValueData.DataType.JOB_LAST_EXECUTION_TIME)
                        .setValue(Long.toString(nextReportTime))
                        .build();
        when(mMeasurementDao.getKeyValueData(
                        eq("job_next_execution_time"),
                        eq(KeyValueData.DataType.JOB_NEXT_EXECUTION_TIME)))
                .thenReturn(nextExecutionTimeKV);
        JobInfo pendingJob =
                new JobInfo.Builder(
                                MEASUREMENT_REPORTING_JOB_ID,
                                new ComponentName(mContext, AttributionJobService.class))
                        // in reality, this invocation doesn't affect the test, but we set it
                        // anyway to create a consistent setup.
                        .setMinimumLatency(timeToNextReport)
                        .setPersisted(true)
                        .build();
        when(mMockJobScheduler.getPendingJob(MEASUREMENT_REPORTING_JOB_ID)).thenReturn(pendingJob);

        // Execute
        ReportingJobService.scheduleIfNeeded(mContext, false);

        // Validate
        verify(mMockJobScheduler, never()).schedule(any());
    }

    @Test
    public void scheduleIfNeeded_nextReportInMinExecutionWindow_schedule() throws Exception {
        // Setup
        enableFeature();
        long now = System.currentTimeMillis();
        // next report is scheduled 100s from now.
        long nextReportTime = now + 100000L;
        long timeToMinInvocationWindow = 100001L;
        // last execution happened 100.001 seconds within min invocation window
        long lastExecutionTime =
                now
                        - (MEASUREMENT_REPORTING_JOB_SERVICE_MIN_EXECUTION_WINDOW_MILLIS
                                - timeToMinInvocationWindow);
        when(mMeasurementDao.getLatestReportTimeInBatchWindow(
                        MEASUREMENT_REPORTING_JOB_SERVICE_BATCH_WINDOW_MILLIS))
                .thenReturn(nextReportTime);
        mockGettingLastExecutionTime(lastExecutionTime);
        mockGettingNextExecutionTime(null);

        // Execute
        ReportingJobService.scheduleIfNeeded(mContext, false);

        // Validate
        ArgumentCaptor<JobInfo> jobInfoArgumentCaptor = ArgumentCaptor.forClass(JobInfo.class);
        verify(mMockJobScheduler, times(1)).schedule(jobInfoArgumentCaptor.capture());

        JobInfo scheduledJob = jobInfoArgumentCaptor.getValue();
        long minLatencyMs = scheduledJob.getMinLatencyMillis();

        assertTrue(minLatencyMs > 0);
        assertTrue(minLatencyMs <= timeToMinInvocationWindow);
    }

    @Test
    public void scheduleIfNeeded_nextReportInPast_noPendingJob_schedule() throws Exception {
        // Setup
        enableFeature();
        long now = System.currentTimeMillis();
        // next report is scheduled 10m in the past.
        long timeToNextReport = 600000L;
        long nextReportTime = now - timeToNextReport;

        when(mMeasurementDao.getLatestReportTimeInBatchWindow(
                        MEASUREMENT_REPORTING_JOB_SERVICE_BATCH_WINDOW_MILLIS))
                .thenReturn(nextReportTime);
        mockGettingLastExecutionTime(Long.MIN_VALUE);
        mockGettingNextExecutionTime(null);
        // Execute
        ReportingJobService.scheduleIfNeeded(mContext, false);

        // Validate. Because the report was scheduled in the past, we schedule the job for near
        // immediate delivery.
        ArgumentCaptor<JobInfo> jobInfoArgumentCaptor = ArgumentCaptor.forClass(JobInfo.class);
        verify(mMockJobScheduler, times(1)).schedule(jobInfoArgumentCaptor.capture());
        JobInfo scheduledJob = jobInfoArgumentCaptor.getValue();
        assertEquals(0, scheduledJob.getMinLatencyMillis());
    }

    @Test
    public void
            scheduleIfNeeded_nextReportInMinExecutionWindow_noPendingJob_forceSchedule_schedule()
                    throws Exception {
        // Setup
        enableFeature();
        long now = System.currentTimeMillis();
        // next report is scheduled 100s from now.
        long timeToNextReport = 100000L;
        long nextReportTime = now + timeToNextReport;
        long timeToMinInvocationWindowEnd = 300000L;
        // last execution happened 300 seconds within min execution window, making the next
        // report fall within the window.
        long lastExecutionTime =
                now
                        - (MEASUREMENT_REPORTING_JOB_SERVICE_MIN_EXECUTION_WINDOW_MILLIS
                                - timeToMinInvocationWindowEnd);

        when(mMeasurementDao.getLatestReportTimeInBatchWindow(
                        MEASUREMENT_REPORTING_JOB_SERVICE_BATCH_WINDOW_MILLIS))
                .thenReturn(nextReportTime);
        mockGettingLastExecutionTime(lastExecutionTime);
        mockGettingNextExecutionTime(null);

        // Execute
        ReportingJobService.scheduleIfNeeded(mContext, true);

        // Validate
        ArgumentCaptor<JobInfo> jobInfoArgumentCaptor = ArgumentCaptor.forClass(JobInfo.class);
        verify(mMockJobScheduler, times(1)).schedule(jobInfoArgumentCaptor.capture());

        JobInfo scheduledJob = jobInfoArgumentCaptor.getValue();
        long minLatencyMs = scheduledJob.getMinLatencyMillis();

        // Execution time of job should be at nextReportTime, even though that report time is
        // in the min execution window. This is because we forced the job.
        assertTrue(minLatencyMs > 0);
        assertTrue(minLatencyMs <= timeToNextReport);
    }

    @Test
    public void scheduleIfNeeded_modifyFlags_jobInfoIsModified() throws Exception {
        // Setup
        enableFeature();
        boolean requireBatteryNotLow = !MEASUREMENT_REPORTING_JOB_REQUIRED_BATTERY_NOT_LOW;
        boolean persisted = !MEASUREMENT_REPORTING_JOB_PERSISTED;
        int requiredNetworkType = JobInfo.NETWORK_TYPE_NONE;

        doReturn(requiredNetworkType)
                .when(mMockFlags)
                .getMeasurementReportingJobRequiredNetworkType();
        doReturn(persisted).when(mMockFlags).getMeasurementReportingJobPersisted();
        doReturn(requireBatteryNotLow)
                .when(mMockFlags)
                .getMeasurementReportingJobRequiredBatteryNotLow();

        // next report is scheduled 100s from now.
        int timeToNextReport = 100000;
        long nextReportTime = System.currentTimeMillis() + timeToNextReport;
        when(mMeasurementDao.getLatestReportTimeInBatchWindow(
                        MEASUREMENT_REPORTING_JOB_SERVICE_BATCH_WINDOW_MILLIS))
                .thenReturn(nextReportTime);
        mockGettingLastExecutionTime(Long.MIN_VALUE);
        mockGettingNextExecutionTime(null);
        // Execute
        ReportingJobService.scheduleIfNeeded(mContext, false);

        // Validate
        ArgumentCaptor<JobInfo> jobInfoArgumentCaptor = ArgumentCaptor.forClass(JobInfo.class);
        verify(mMockJobScheduler, times(1)).schedule(jobInfoArgumentCaptor.capture());

        JobInfo scheduledJob = jobInfoArgumentCaptor.getValue();
        long minLatencyMs = scheduledJob.getMinLatencyMillis();

        assertTrue(minLatencyMs > 0);
        assertTrue(minLatencyMs <= timeToNextReport);
        assertEquals(requireBatteryNotLow, scheduledJob.isRequireBatteryNotLow());
        assertEquals(persisted, scheduledJob.isPersisted());
        // Below is not accessible.
        // assertNull(scheduledJob.getRequiredNetwork());
    }

    @Test
    public void scheduleIfNeeded_noPendingReports_dontSchedule() throws Exception {
        // Setup
        disableFeature();
        when(mMeasurementDao.getLatestReportTimeInBatchWindow(
                        MEASUREMENT_REPORTING_JOB_SERVICE_BATCH_WINDOW_MILLIS))
                .thenReturn(null);
        mockGettingLastExecutionTime(Long.MIN_VALUE);
        // Execute
        ReportingJobService.scheduleIfNeeded(mContext, false);

        // Validate. No reports are left in the queue.
        verify(mMockJobScheduler, never()).schedule(any());
    }

    @Test
    public void scheduleIfNeeded_noPendingReports_forceSchedule_dontSchedule() throws Exception {
        // Setup
        disableFeature();
        when(mMeasurementDao.getLatestReportTimeInBatchWindow(
                        MEASUREMENT_REPORTING_JOB_SERVICE_BATCH_WINDOW_MILLIS))
                .thenReturn(null);
        mockGettingLastExecutionTime(Long.MIN_VALUE);
        // Execute
        ReportingJobService.scheduleIfNeeded(mContext, true);

        // Validate. No reports are left in the queue.
        verify(mMockJobScheduler, never()).schedule(any());
    }

    @Test
    public void scheduleIfNeeded_featureDisabled_dontSchedule() throws Exception {
        // Setup
        disableFeature();

        // Execute
        ReportingJobService.scheduleIfNeeded(mContext, false);

        // Validate
        ExtendedMockito.verify(() -> ReportingJobService.schedule(any(), any()), never());
        verify(mMockJobScheduler, never()).getPendingJob(eq(MEASUREMENT_REPORTING_JOB_ID));
    }

    @Test
    public void testOnStopJob_stopsExecutingThread() throws Exception {
        enableFeature();

        doAnswer(new AnswersWithDelay(WAIT_IN_MILLIS * 10, new CallsRealMethods()))
                .when(mSpyService)
                .processPendingAggregateReports();
        doAnswer(new AnswersWithDelay(WAIT_IN_MILLIS * 10, new CallsRealMethods()))
                .when(mSpyService)
                .processPendingEventReports();
        mockGettingLastExecutionTime(Long.MIN_VALUE);
        mockGettingNextExecutionTime(null);
        mSpyService.onStartJob(Mockito.mock(JobParameters.class));
        Thread.sleep(WAIT_IN_MILLIS);

        assertNotNull(mSpyService.getFutureForTesting());

        boolean onStopJobResult = mSpyService.onStopJob(Mockito.mock(JobParameters.class));
        verify(mSpyService, never()).jobFinished(any(), anyBoolean());
        assertTrue(onStopJobResult);
        assertTrue(mSpyService.getFutureForTesting().isCancelled());
    }

    @Test
    public void onStartJob_featureDisabled_withoutLogging() throws Exception {
        mockBackgroundJobsLoggingKillSwitch(mMockFlags, /* overrideValue= */ true);

        onStartJob_featureDisabled();

        verifyLoggingNotHappened(mSpyLogger);
    }

    @Test
    public void onStartJob_featureDisabled_withLogging() throws Exception {
        mockBackgroundJobsLoggingKillSwitch(mMockFlags, /* overrideValue= */ false);
        JobServiceLoggingCallback callback = syncLogExecutionStats(mSpyLogger);

        onStartJob_featureDisabled();

        verifyBackgroundJobsSkipLogged(mSpyLogger, callback);
    }

    @Test
    public void onStartJob_featureEnabled_withoutLogging() throws Exception {
        mockBackgroundJobsLoggingKillSwitch(mMockFlags, /* overrideValue= */ true);

        onStartJob_featureEnabled();

        verifyLoggingNotHappened(mSpyLogger);
    }

    @Test
    public void onStartJob_featureEnabled_withLogging() throws Exception {
        mockBackgroundJobsLoggingKillSwitch(mMockFlags, /* overrideValue= */ false);
        JobServiceLoggingCallback onStartJobCallback = syncPersistJobExecutionData(mSpyLogger);
        JobServiceLoggingCallback onJobDoneCallback = syncLogExecutionStats(mSpyLogger);

        onStartJob_featureEnabled();

        verifyJobFinishedLogged(mSpyLogger, onStartJobCallback, onJobDoneCallback);
    }

    @Test
    public void onStartJob_shouldDisableJobTrue_withoutLogging() throws Exception {
        mockBackgroundJobsLoggingKillSwitch(mMockFlags, /* overrideValue= */ true);

        onStartJob_shouldDisableJobTrue();

        verifyLoggingNotHappened(mSpyLogger);
    }

    @Test
    public void onStartJob_shouldDisableJobTrue_withLoggingEnabled() throws Exception {
        mockBackgroundJobsLoggingKillSwitch(mMockFlags, /* overrideValue= */ false);

        onStartJob_shouldDisableJobTrue();

        // Verify logging has not happened even though logging is enabled because this
        // field is not logged
        verifyLoggingNotHappened(mSpyLogger);
    }

    private void onStartJob_featureDisabled() throws Exception {
        // Setup
        disableFeature();
        mMockDatastoreManager = mock(DatastoreManager.class);
        mockGettingNextExecutionTime(null);

        JobServiceCallback callback = new JobServiceCallback().expectJobFinished(mSpyService);

        // Execute
        boolean result = mSpyService.onStartJob(mock(JobParameters.class));

        // Validate
        assertFalse(result);

        callback.assertJobFinished();
        verify(mMockDatastoreManager, never()).runInTransactionWithResult(any());
        verify(mSpyService, times(1)).jobFinished(any(), eq(false));
    }

    private void onStartJob_featureEnabled() throws Exception {
        // Setup
        enableFeature();
        mMockDatastoreManager = mock(DatastoreManager.class);
        doReturn(Optional.empty()).when(mMockDatastoreManager).runInTransactionWithResult(any());
        doReturn(true).when(mMockDatastoreManager).runInTransaction(any());
        ExtendedMockito.doReturn(mMockDatastoreManager)
                .when(() -> DatastoreManagerFactory.getDatastoreManager(any()));
        // Return a null job scheduler to short circuit scheduling
        doReturn(null).when(mContext).getSystemService(JobScheduler.class);

        JobServiceCallback callback = new JobServiceCallback().expectJobFinished(mSpyService);

        // Execute
        boolean result = mSpyService.onStartJob(mock(JobParameters.class));

        // Validate
        assertTrue(result);

        // Call get() on the future object to ensure this thread blocks on the results from the
        // background thread that's scheduled at job start.
        mSpyService.getFutureForTesting().get();

        callback.assertJobFinished();

        verify(mSpyService, times(1)).jobFinished(any(), anyBoolean());
        ExtendedMockito.verify(() -> ReportingJobService.scheduleIfNeeded(any(), eq(false)));
        verify(mMockJobScheduler, never()).cancel(eq(MEASUREMENT_REPORTING_JOB_ID));
    }

    private void onStartJob_shouldDisableJobTrue() throws Exception {
        // Setup
        mMockDatastoreManager = mock(DatastoreManager.class);
        doReturn(Optional.empty()).when(mMockDatastoreManager).runInTransactionWithResult(any());
        doReturn(true).when(mMockDatastoreManager).runInTransaction(any());
        ExtendedMockito.doReturn(mMockDatastoreManager)
                .when(() -> DatastoreManagerFactory.getDatastoreManager(any()));
        ExtendedMockito.doReturn(true)
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
    }

    private void mockGettingLastExecutionTime(long time) throws DatastoreException {
        KeyValueData lastExecutionTimeKV =
                new KeyValueData.Builder()
                        .setKey("job_last_execution_time")
                        .setDataType(KeyValueData.DataType.JOB_LAST_EXECUTION_TIME)
                        .setValue(Long.toString(time))
                        .build();
        when(mMeasurementDao.getKeyValueData(
                        eq("job_last_execution_time"),
                        eq(KeyValueData.DataType.JOB_LAST_EXECUTION_TIME)))
                .thenReturn(lastExecutionTimeKV);
    }

    private void mockGettingNextExecutionTime(Long time) throws DatastoreException {
        KeyValueData nextExecutionTimeKV =
                new KeyValueData.Builder()
                        .setKey("job_next_execution_time")
                        .setDataType(KeyValueData.DataType.JOB_NEXT_EXECUTION_TIME)
                        .setValue(time == null ? null : Long.toString(time))
                        .build();
        when(mMeasurementDao.getKeyValueData(
                        eq("job_next_execution_time"),
                        eq(KeyValueData.DataType.JOB_NEXT_EXECUTION_TIME)))
                .thenReturn(nextExecutionTimeKV);
    }

    private void enableFeature() {
        toggleFeatureFlag(true);
    }

    private void disableFeature() {
        toggleFeatureFlag(false);
    }

    private void toggleFeatureFlag(boolean value) {
        ExtendedMockito.doReturn(value).when(mMockFlags).getMeasurementReportingJobServiceEnabled();
        ExtendedMockito.doReturn(mMockFlags).when(FlagsFactory::getFlags);
    }
}

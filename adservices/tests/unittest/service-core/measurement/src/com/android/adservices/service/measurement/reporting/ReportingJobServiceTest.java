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

import static com.android.adservices.service.Flags.MEASUREMENT_REPORTING_JOB_PERSISTED;
import static com.android.adservices.service.Flags.MEASUREMENT_REPORTING_JOB_REQUIRED_BATTERY_NOT_LOW;
import static com.android.adservices.service.Flags.MEASUREMENT_REPORTING_JOB_REQUIRED_NETWORK_TYPE;
import static com.android.adservices.service.Flags.MEASUREMENT_REPORTING_JOB_SERVICE_BATCH_WINDOW_MILLIS;
import static com.android.adservices.service.Flags.MEASUREMENT_REPORTING_JOB_SERVICE_MIN_EXECUTION_WINDOW_MILLIS;
import static com.android.adservices.spe.AdServicesJobInfo.MEASUREMENT_REPORTING_JOB;
import static com.android.dx.mockito.inline.extended.ExtendedMockito.doReturn;

import static com.google.common.truth.Truth.assertThat;

import static org.junit.Assert.assertEquals;
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
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import android.app.job.JobInfo;
import android.app.job.JobParameters;
import android.app.job.JobScheduler;
import android.content.ComponentName;
import android.content.Context;

import com.android.adservices.data.enrollment.EnrollmentDao;
import com.android.adservices.data.measurement.DatastoreException;
import com.android.adservices.data.measurement.DatastoreManager;
import com.android.adservices.data.measurement.DatastoreManagerFactory;
import com.android.adservices.data.measurement.IMeasurementDao;
import com.android.adservices.data.measurement.ITransaction;
import com.android.adservices.service.FlagsFactory;
import com.android.adservices.service.common.compat.ServiceCompatUtils;
import com.android.adservices.service.measurement.KeyValueData;
import com.android.adservices.service.measurement.MeasurementJobServiceTestCase;
import com.android.adservices.service.measurement.attribution.AttributionJobService;
import com.android.adservices.shared.errorlogging.AdServicesErrorLogger;
import com.android.adservices.shared.testing.JobServiceLoggingCallback;
import com.android.adservices.shared.testing.concurrency.JobServiceCallback;
import com.android.adservices.spe.AdServicesJobServiceLogger;
import com.android.dx.mockito.inline.extended.ExtendedMockito;
import com.android.modules.utils.testing.ExtendedMockitoRule.MockStatic;
import com.android.modules.utils.testing.ExtendedMockitoRule.SpyStatic;

import org.junit.Before;
import org.junit.Test;
import org.mockito.ArgumentCaptor;
import org.mockito.Mock;
import org.mockito.Mockito;
import org.mockito.internal.stubbing.answers.AnswersWithDelay;
import org.mockito.internal.stubbing.answers.CallsRealMethods;

import java.util.Optional;

/** Unit test for {@link ReportingJobService} */
@SpyStatic(ReportingJobService.class)
@SpyStatic(DatastoreManagerFactory.class)
@SpyStatic(EnrollmentDao.class)
@SpyStatic(FlagsFactory.class)
@SpyStatic(AdServicesJobServiceLogger.class)
@MockStatic(ServiceCompatUtils.class)
public final class ReportingJobServiceTest
        extends MeasurementJobServiceTestCase<ReportingJobService> {
    private static final int MEASUREMENT_REPORTING_JOB_ID = MEASUREMENT_REPORTING_JOB.getJobId();
    private static final long WAIT_IN_MILLIS = 200L;

    @Mock private AdServicesErrorLogger mErrorLogger;
    @Mock private IMeasurementDao mMeasurementDao;
    @Mock private ITransaction mTransaction;

    private final class FakeDatastoreManager extends DatastoreManager {
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

    @Override
    protected ReportingJobService getSpiedService() {
        return new ReportingJobService();
    }

    @Before
    public void setUp() {
        doReturn(mContext.getPackageName()).when(mMockContext).getPackageName();
        doReturn(mContext.getPackageManager()).when(mMockContext).getPackageManager();
        doReturn(mMockJobScheduler).when(mMockContext).getSystemService(JobScheduler.class);
        doReturn(mMockJobScheduler).when(mSpyService).getSystemService(JobScheduler.class);
        doReturn(mMockContext).when(mSpyService).getApplicationContext();
        doNothing().when(mSpyService).jobFinished(any(), anyBoolean());
        doReturn(new FakeDatastoreManager()).when(DatastoreManagerFactory::getDatastoreManager);
        doReturn(mMockFlags).when(FlagsFactory::getFlags);
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
        mocker.mockGetAdServicesJobServiceLogger(mSpyLogger);
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
        ReportingJobService.scheduleIfNeeded(mMockContext, false);

        // Validate
        ArgumentCaptor<JobInfo> jobInfoArgumentCaptor = ArgumentCaptor.forClass(JobInfo.class);
        verify(mMockJobScheduler).schedule(jobInfoArgumentCaptor.capture());

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
                                new ComponentName(mMockContext, AttributionJobService.class))
                        .setMinimumLatency(
                                MEASUREMENT_REPORTING_JOB_SERVICE_BATCH_WINDOW_MILLIS + 1)
                        .setPersisted(true)
                        .build();
        when(mMockJobScheduler.getPendingJob(MEASUREMENT_REPORTING_JOB_ID)).thenReturn(pendingJob);
        // next job is scheduled just outside the batching window.
        mockGettingNextExecutionTime(
                now + MEASUREMENT_REPORTING_JOB_SERVICE_BATCH_WINDOW_MILLIS + 1);

        // Execute
        ReportingJobService.scheduleIfNeeded(mMockContext, false);

        // Validate
        ArgumentCaptor<JobInfo> jobInfoArgumentCaptor = ArgumentCaptor.forClass(JobInfo.class);
        verify(mMockJobScheduler).schedule(jobInfoArgumentCaptor.capture());

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
                                new ComponentName(mMockContext, AttributionJobService.class))
                        // in reality, this invocation doesn't affect the test, but we set it
                        // anyway to create a consistent setup.
                        .setMinimumLatency(timeToNextReport)
                        .setPersisted(true)
                        .build();
        when(mMockJobScheduler.getPendingJob(MEASUREMENT_REPORTING_JOB_ID)).thenReturn(pendingJob);

        // Execute
        ReportingJobService.scheduleIfNeeded(mMockContext, false);

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
        ReportingJobService.scheduleIfNeeded(mMockContext, false);

        // Validate
        ArgumentCaptor<JobInfo> jobInfoArgumentCaptor = ArgumentCaptor.forClass(JobInfo.class);
        verify(mMockJobScheduler).schedule(jobInfoArgumentCaptor.capture());

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
        ReportingJobService.scheduleIfNeeded(mMockContext, false);

        // Validate. Because the report was scheduled in the past, we schedule the job for near
        // immediate delivery.
        ArgumentCaptor<JobInfo> jobInfoArgumentCaptor = ArgumentCaptor.forClass(JobInfo.class);
        verify(mMockJobScheduler).schedule(jobInfoArgumentCaptor.capture());
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
        ReportingJobService.scheduleIfNeeded(mMockContext, true);

        // Validate
        ArgumentCaptor<JobInfo> jobInfoArgumentCaptor = ArgumentCaptor.forClass(JobInfo.class);
        verify(mMockJobScheduler).schedule(jobInfoArgumentCaptor.capture());

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
        ReportingJobService.scheduleIfNeeded(mMockContext, false);

        // Validate
        ArgumentCaptor<JobInfo> jobInfoArgumentCaptor = ArgumentCaptor.forClass(JobInfo.class);
        verify(mMockJobScheduler).schedule(jobInfoArgumentCaptor.capture());

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
        ReportingJobService.scheduleIfNeeded(mMockContext, false);

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
        ReportingJobService.scheduleIfNeeded(mMockContext, true);

        // Validate. No reports are left in the queue.
        verify(mMockJobScheduler, never()).schedule(any());
    }

    @Test
    public void scheduleIfNeeded_featureDisabled_dontSchedule() throws Exception {
        // Setup
        disableFeature();

        // Execute
        ReportingJobService.scheduleIfNeeded(mMockContext, false);

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
    public void onStartJob_featureDisabled_withLogging() throws Exception {
        JobServiceLoggingCallback callback = syncLogExecutionStats(mSpyLogger);

        onStartJob_featureDisabled();

        verifyBackgroundJobsSkipLogged(mSpyLogger, callback);
    }

    @Test
    public void onStartJob_featureEnabled_withLogging() throws Exception {
        JobServiceLoggingCallback onStartJobCallback = syncPersistJobExecutionData(mSpyLogger);
        JobServiceLoggingCallback onJobDoneCallback = syncLogExecutionStats(mSpyLogger);

        onStartJob_featureEnabled();

        verifyJobFinishedLogged(mSpyLogger, onStartJobCallback, onJobDoneCallback);
    }

    @Test
    public void onStartJob_shouldDisableJobTrue() throws Exception {
        // Setup
        when(mMockDatastoreManager.runInTransactionWithResult(any())).thenReturn(Optional.empty());
        when(mMockDatastoreManager.runInTransaction(any())).thenReturn(true);
        doReturn(mMockDatastoreManager).when(DatastoreManagerFactory::getDatastoreManager);
        doReturn(true)
                .when(
                        () ->
                                ServiceCompatUtils.shouldDisableExtServicesJobOnTPlus(
                                        any(Context.class)));

        JobServiceCallback callback = new JobServiceCallback().expectJobFinished(mSpyService);

        // Execute
        boolean result = mSpyService.onStartJob(mock(JobParameters.class));

        // Validate
        assertThat(result).isFalse();

        callback.assertJobFinished();
        verify(mSpyService).jobFinished(any(), eq(false));

        // Verify logging has not happened even though logging is enabled because this
        // field is not logged
        verifyLoggingNotHappened(mSpyLogger);
    }

    private void onStartJob_featureDisabled() throws Exception {
        // Setup
        disableFeature();
        mockGettingNextExecutionTime(null);

        JobServiceCallback callback = new JobServiceCallback().expectJobFinished(mSpyService);

        // Execute
        boolean result = mSpyService.onStartJob(mock(JobParameters.class));

        // Validate
        assertFalse(result);

        callback.assertJobFinished();
        verify(mMockDatastoreManager, never()).runInTransactionWithResult(any());
        verify(mSpyService).jobFinished(any(), eq(false));
    }

    private void onStartJob_featureEnabled() throws Exception {
        // Setup
        enableFeature();
        doReturn(Optional.empty()).when(mMockDatastoreManager).runInTransactionWithResult(any());
        doReturn(true).when(mMockDatastoreManager).runInTransaction(any());
        doReturn(mMockDatastoreManager).when(DatastoreManagerFactory::getDatastoreManager);
        // Return a null job scheduler to short circuit scheduling
        doReturn(null).when(mMockContext).getSystemService(JobScheduler.class);

        JobServiceCallback callback = new JobServiceCallback().expectJobFinished(mSpyService);

        // Execute
        boolean result = mSpyService.onStartJob(mock(JobParameters.class));

        // Validate
        assertTrue(result);

        // Call get() on the future object to ensure this thread blocks on the results from the
        // background thread that's scheduled at job start.
        mSpyService.getFutureForTesting().get();

        callback.assertJobFinished();

        verify(mSpyService).jobFinished(any(), anyBoolean());
        ExtendedMockito.verify(() -> ReportingJobService.scheduleIfNeeded(any(), eq(false)));
        verify(mMockJobScheduler, never()).cancel(eq(MEASUREMENT_REPORTING_JOB_ID));
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

    @Override
    protected void toggleFeature(boolean value) {
        when(mMockFlags.getMeasurementReportingJobServiceEnabled()).thenReturn(value);
    }
}

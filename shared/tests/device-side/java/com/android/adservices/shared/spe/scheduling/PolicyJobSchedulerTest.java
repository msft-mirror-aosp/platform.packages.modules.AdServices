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

package com.android.adservices.shared.spe.scheduling;

import static com.android.adservices.service.stats.AdServicesStatsLog.AD_SERVICES_ERROR_REPORTED__ERROR_CODE__SPE_INVALID_JOB_POLICY_SYNC;
import static com.android.adservices.service.stats.AdServicesStatsLog.AD_SERVICES_ERROR_REPORTED__ERROR_CODE__SPE_JOB_SCHEDULING_FAILURE;
import static com.android.adservices.service.stats.AdServicesStatsLog.AD_SERVICES_ERROR_REPORTED__PPAPI_NAME__COMMON;
import static com.android.adservices.shared.proto.JobPolicy.BatteryType.BATTERY_TYPE_REQUIRE_CHARGING;
import static com.android.adservices.shared.proto.JobPolicy.BatteryType.BATTERY_TYPE_REQUIRE_NONE;
import static com.android.adservices.shared.spe.JobServiceConstants.JOB_ENABLED_STATUS_DISABLED_FOR_KILL_SWITCH_ON;
import static com.android.adservices.shared.spe.JobServiceConstants.JOB_ENABLED_STATUS_ENABLED;
import static com.android.adservices.shared.spe.JobServiceConstants.SCHEDULING_RESULT_CODE_FAILED;
import static com.android.adservices.shared.spe.JobServiceConstants.SCHEDULING_RESULT_CODE_SKIPPED;
import static com.android.adservices.shared.spe.JobServiceConstants.SCHEDULING_RESULT_CODE_SUCCESSFUL;
import static com.android.adservices.shared.spe.JobServiceConstants.UNAVAILABLE_KEY;
import static com.android.adservices.shared.spe.framework.TestJobServiceFactory.JOB_ID_1;
import static com.android.adservices.shared.spe.framework.TestJobServiceFactory.JOB_NAME_1;

import static com.google.common.truth.Truth.assertThat;
import static com.google.common.truth.Truth.assertWithMessage;

import static org.junit.Assert.assertThrows;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyInt;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.doAnswer;
import static org.mockito.Mockito.doReturn;
import static org.mockito.Mockito.spy;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import android.app.job.JobInfo;
import android.app.job.JobScheduler;
import android.content.ComponentName;
import android.os.PersistableBundle;

import com.android.adservices.common.AdServicesMockitoTestCase;
import com.android.adservices.shared.errorlogging.AdServicesErrorLogger;
import com.android.adservices.shared.proto.JobPolicy;
import com.android.adservices.shared.proto.ModuleJobPolicy;
import com.android.adservices.shared.spe.framework.JobWorker;
import com.android.adservices.shared.spe.framework.TestJobService;
import com.android.adservices.shared.spe.framework.TestJobServiceFactory;
import com.android.adservices.shared.spe.logging.JobSchedulingLogger;
import com.android.adservices.shared.spe.logging.JobServiceLogger;
import com.android.adservices.shared.testing.NoFailureSyncCallback;

import com.google.common.util.concurrent.Futures;
import com.google.common.util.concurrent.ListenableFuture;

import org.junit.After;
import org.junit.Before;
import org.junit.Test;
import org.mockito.Mock;

import java.util.Map;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.TimeUnit;

/** Unit Test for {@link PolicyJobScheduler}. */
public final class PolicyJobSchedulerTest extends AdServicesMockitoTestCase {
    // Since this unit test is to test scheduling behavior, it doesn't need to actually execute the
    // job. Set this unreachable latency to prevent the job to execute.
    private static final long MINIMUM_LATENCY_MS = 60 * 60 * 1000;
    private static final boolean REQUIRE_CHARGING = true;
    private static final JobPolicy sJobPolicy =
            JobPolicy.newBuilder()
                    .setJobId(JOB_ID_1)
                    .setOneOffJobParams(
                            JobPolicy.OneOffJobParams.newBuilder()
                                    .setMinimumLatencyMs(MINIMUM_LATENCY_MS)
                                    .build())
                    .setBatteryType(
                            REQUIRE_CHARGING
                                    ? BATTERY_TYPE_REQUIRE_CHARGING
                                    : BATTERY_TYPE_REQUIRE_NONE)
                    .build();
    private static final JobSpec sJobSpec = new JobSpec.Builder(sJobPolicy).build();

    private final JobScheduler mJobScheduler = sContext.getSystemService(JobScheduler.class);
    private TestJobServiceFactory mFactory;
    private PolicyJobScheduler<TestJobService> mPolicyJobScheduler;
    @Mock private JobServiceLogger mMockLogger;
    @Mock private JobWorker mMockJobWorker;
    @Mock private ModuleJobPolicy mMockModuleJobPolicy;
    @Mock private AdServicesErrorLogger mMockErrorLogger;
    @Mock private JobSchedulingLogger mMockJobSchedulingLogger;

    @Before
    public void setup() {
        assertWithMessage("The JobScheduler").that(mJobScheduler).isNotNull();

        mFactory =
                new TestJobServiceFactory(
                        mMockJobWorker,
                        mMockLogger,
                        mMockModuleJobPolicy,
                        mMockErrorLogger,
                        mMockJobSchedulingLogger);
        mPolicyJobScheduler = new PolicyJobScheduler<>(mFactory, TestJobService.class);
    }

    @After
    public void teardown() {
        mJobScheduler.cancelAll();

        // TODO(b/326150593): Remove assertion in @After.
        assertWithMessage("Any pending job in JobScheduler")
                .that(mJobScheduler.getAllPendingJobs())
                .isEmpty();
    }

    @Test
    public void testScheduleJob_jobNotConfigured() {
        int notConfiguredJobId = 10000;
        JobSpec jobSpec =
                new JobSpec.Builder(JobPolicy.newBuilder().setJobId(notConfiguredJobId).build())
                        .build();

        assertThrows(
                IllegalStateException.class,
                () -> mPolicyJobScheduler.scheduleJob(sContext, jobSpec));
    }

    @Test
    public void testScheduleJob_skipForNotEnabled() {
        when(mMockJobWorker.getJobSchedulingEnablementStatus())
                .thenReturn(JOB_ENABLED_STATUS_DISABLED_FOR_KILL_SWITCH_ON);

        assertThat(mPolicyJobScheduler.scheduleJob(sContext, sJobSpec))
                .isEqualTo(SCHEDULING_RESULT_CODE_SKIPPED);
    }

    @Test
    public void testScheduleJob_noPendingJob() {
        scheduleOneTimeJobWithDefaultConstraints();
    }

    @Test
    public void testScheduleJob_skipSchedulingOnSameInfo() {
        scheduleOneTimeJobWithDefaultConstraints();

        expect.withMessage("The scheduling for job with same JobInfo")
                .that(mPolicyJobScheduler.scheduleJob(sContext, sJobSpec))
                .isEqualTo(SCHEDULING_RESULT_CODE_SKIPPED);
    }

    @Test
    public void testScheduleJob_forceSchedule() {
        scheduleOneTimeJobWithDefaultConstraints();

        JobSpec jobSpec = new JobSpec.Builder(sJobPolicy).setShouldForceSchedule(true).build();
        expect.withMessage("The forced scheduling for job with same JobInfo")
                .that(mPolicyJobScheduler.scheduleJob(sContext, jobSpec))
                .isEqualTo(SCHEDULING_RESULT_CODE_SUCCESSFUL);
    }

    @Test
    public void testScheduleJob_rescheduleWithDifferentInfo() {
        scheduleOneTimeJobWithDefaultConstraints();

        JobPolicy updatedJobPolicy =
                sJobPolicy.toBuilder().setBatteryType(BATTERY_TYPE_REQUIRE_NONE).build();
        JobSpec updatedJobSpec = new JobSpec.Builder(updatedJobPolicy).build();

        // Call the scheduling method with an updated info.
        expect.withMessage("The scheduling for job with different jobInfo")
                .that(mPolicyJobScheduler.scheduleJob(sContext, updatedJobSpec))
                .isEqualTo(SCHEDULING_RESULT_CODE_SUCCESSFUL);

        JobInfo scheduledJobInfo = mJobScheduler.getPendingJob(JOB_ID_1);
        JobInfo expectedJobInfo =
                getBaseJobInfoBuilder()
                        .setMinimumLatency(MINIMUM_LATENCY_MS)
                        .setRequiresCharging(!REQUIRE_CHARGING)
                        .build();
        // Unparcel it in order to call equals().
        scheduledJobInfo.getExtras().getString(UNAVAILABLE_KEY);
        expect.withMessage("The rescheduled jobInfo")
                .that(scheduledJobInfo)
                .isEqualTo(expectedJobInfo);
    }

    @Test
    public void testSchedule() throws Exception {
        PolicyJobScheduler<TestJobService> spyScheduler = spy(mPolicyJobScheduler);

        CountDownLatch countDownLatch = new CountDownLatch(1);
        doAnswer(
                        invocation -> {
                            countDownLatch.countDown();
                            return true;
                        })
                .when(spyScheduler)
                .scheduleJob(sContext, sJobSpec);

        spyScheduler.schedule(sContext, sJobSpec);

        assertWithMessage("The scheduling finishing")
                .that(countDownLatch.await(3, TimeUnit.SECONDS))
                .isTrue();
    }

    @Test
    public void getJobInfoToSchedule() {
        long minimumLatencyFromServer = sJobPolicy.getOneOffJobParams().getMinimumLatencyMs() + 1;
        JobPolicy serverJobPolicy =
                sJobPolicy.toBuilder()
                        .setOneOffJobParams(
                                JobPolicy.OneOffJobParams.newBuilder()
                                        .setMinimumLatencyMs(minimumLatencyFromServer)
                                        .build())
                        .build();
        doReturn(Map.of(JOB_ID_1, serverJobPolicy)).when(mMockModuleJobPolicy).getJobPolicyMap();

        PersistableBundle extras = new PersistableBundle();
        extras.putBoolean("testKey", true);

        JobSpec jobSpec = new JobSpec.Builder(sJobPolicy).setExtras(extras).build();

        JobInfo expectedJobInfo =
                getBaseJobInfoBuilder()
                        .setMinimumLatency(minimumLatencyFromServer)
                        .setRequiresCharging(REQUIRE_CHARGING)
                        .setExtras(extras)
                        .build();
        assertWithMessage("getJobInfoToSchedule()")
                .that(mPolicyJobScheduler.getJobInfoToSchedule(sContext, jobSpec, JOB_NAME_1))
                .isEqualTo(expectedJobInfo);
    }

    @Test
    public void getJobInfoToSchedule_throwsWhenSyncPolicy() {
        // Policy requires to have job_id field to merge.
        JobPolicy serverJobPolicy = sJobPolicy.toBuilder().clearJobId().build();
        doReturn(Map.of(JOB_ID_1, serverJobPolicy)).when(mMockModuleJobPolicy).getJobPolicyMap();

        JobSpec jobSpec = new JobSpec.Builder(sJobPolicy).build();

        assertWithMessage("getJobInfoToSchedule()")
                .that(mPolicyJobScheduler.getJobInfoToSchedule(sContext, jobSpec, JOB_NAME_1))
                .isEqualTo(getBaseJobInfoBuilder().build());
        verify(mMockErrorLogger)
                .logErrorWithExceptionInfo(
                        any(),
                        eq(AD_SERVICES_ERROR_REPORTED__ERROR_CODE__SPE_INVALID_JOB_POLICY_SYNC),
                        eq(AD_SERVICES_ERROR_REPORTED__PPAPI_NAME__COMMON));
    }

    @Test
    public void testGetPolicyFromFlagServer() {
        doReturn(Map.of(JOB_ID_1, sJobPolicy)).when(mMockModuleJobPolicy).getJobPolicyMap();

        expect.withMessage("Module policy").that(mMockModuleJobPolicy).isNotNull();
        expect.withMessage("The returned jobInfo for null job policy synced from server")
                .that(mPolicyJobScheduler.getPolicyFromFlagServer(JOB_ID_1, JOB_NAME_1))
                .isEqualTo(sJobPolicy);
    }

    @Test
    public void testGetPolicyFromFlagServer_nullModulePolicy() {
        mFactory =
                new TestJobServiceFactory(
                        mMockJobWorker,
                        mMockLogger,
                        /* moduleJobPolicy= */ null,
                        mMockErrorLogger,
                        mMockJobSchedulingLogger);
        assertWithMessage("The returned jobInfo for null module policy synced from server")
                .that(mPolicyJobScheduler.getPolicyFromFlagServer(JOB_ID_1, JOB_NAME_1))
                .isNull();
    }

    @Test
    public void testGetPolicyFromFlagServer_jobPolicyNotExisted() {
        doReturn(Map.of()).when(mMockModuleJobPolicy).getJobPolicyMap();

        expect.withMessage("Module policy").that(mMockModuleJobPolicy).isNotNull();
        expect.withMessage("The returned jobInfo for null job policy synced from server")
                .that(mPolicyJobScheduler.getPolicyFromFlagServer(JOB_ID_1, JOB_NAME_1))
                .isNull();
    }

    @Test
    public void testCreateBaseJobInfoBuilder() {
        JobInfo.Builder expectedBuilder =
                new JobInfo.Builder(JOB_ID_1, new ComponentName(sContext, TestJobService.class));
        assertWithMessage("Base jobInfo creation")
                .that(expectedBuilder.build())
                .isEqualTo(getBaseJobInfoBuilder().build());
    }

    @Test
    public void testAddCallbackToSchedulingFuture_onSuccess() throws Exception {
        ListenableFuture<Integer> future =
                Futures.immediateFuture(SCHEDULING_RESULT_CODE_SUCCESSFUL);
        NoFailureSyncCallback<Void> callback = syncRecordOnScheduling();

        mPolicyJobScheduler.addCallbackToSchedulingFuture(future, JOB_ID_1);

        callback.assertReceived();
        verify(mMockJobSchedulingLogger)
                .recordOnScheduling(JOB_ID_1, SCHEDULING_RESULT_CODE_SUCCESSFUL);
    }

    @Test
    public void testAddCallbackToSchedulingFuture_onFailure() throws Exception {
        RuntimeException exception = new RuntimeException("Failed!");
        ListenableFuture<Integer> future = Futures.immediateFailedFuture(exception);
        NoFailureSyncCallback<Void> callback = syncRecordOnScheduling();

        mPolicyJobScheduler.addCallbackToSchedulingFuture(future, JOB_ID_1);

        callback.assertReceived();
        verify(mMockErrorLogger)
                .logErrorWithExceptionInfo(
                        exception,
                        AD_SERVICES_ERROR_REPORTED__ERROR_CODE__SPE_JOB_SCHEDULING_FAILURE,
                        AD_SERVICES_ERROR_REPORTED__PPAPI_NAME__COMMON);
        verify(mMockJobSchedulingLogger)
                .recordOnScheduling(JOB_ID_1, SCHEDULING_RESULT_CODE_FAILED);
    }

    private JobInfo.Builder getBaseJobInfoBuilder() {
        return mPolicyJobScheduler.createBaseJobInfoBuilder(sContext, JOB_ID_1);
    }

    private void scheduleOneTimeJobWithDefaultConstraints() {
        when(mMockJobWorker.getJobSchedulingEnablementStatus())
                .thenReturn(JOB_ENABLED_STATUS_ENABLED);

        expect.withMessage("Pending job with id=%s in JobScheduler", JOB_ID_1)
                .that(mJobScheduler.getPendingJob(JOB_ID_1))
                .isNull();
        expect.withMessage("Scheduling for  job with id=%s", JOB_ID_1)
                .that(mPolicyJobScheduler.scheduleJob(sContext, sJobSpec))
                .isEqualTo(SCHEDULING_RESULT_CODE_SUCCESSFUL);

        JobInfo scheduledJobInfo = mJobScheduler.getPendingJob(JOB_ID_1);
        JobInfo expectedJobInfo =
                getBaseJobInfoBuilder()
                        .setMinimumLatency(MINIMUM_LATENCY_MS)
                        .setRequiresCharging(REQUIRE_CHARGING)
                        .build();

        // Unparcel it in order to call equals().
        scheduledJobInfo.getExtras().getString(UNAVAILABLE_KEY);
        expect.withMessage("Successful scheduled jobInfo for job with id=%s ", JOB_ID_1)
                .that(scheduledJobInfo)
                .isEqualTo(expectedJobInfo);
    }

    private NoFailureSyncCallback<Void> syncRecordOnScheduling() {
        NoFailureSyncCallback<Void> callback = new NoFailureSyncCallback<>();

        doAnswer(
                        invocation -> {
                            callback.injectResult(null);
                            return null;
                        })
                .when(mMockJobSchedulingLogger)
                .recordOnScheduling(anyInt(), anyInt());

        return callback;
    }
}

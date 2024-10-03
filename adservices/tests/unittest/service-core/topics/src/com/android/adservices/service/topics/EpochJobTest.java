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

package com.android.adservices.service.topics;

import static com.android.adservices.service.Flags.TOPICS_EPOCH_JOB_FLEX_MS;
import static com.android.adservices.service.Flags.TOPICS_EPOCH_JOB_PERIOD_MS;
import static com.android.adservices.shared.proto.JobPolicy.BatteryType.BATTERY_TYPE_REQUIRE_CHARGING;
import static com.android.adservices.shared.proto.JobPolicy.BatteryType.BATTERY_TYPE_REQUIRE_NOT_LOW;
import static com.android.adservices.shared.spe.JobServiceConstants.JOB_ENABLED_STATUS_DISABLED_FOR_KILL_SWITCH_ON;
import static com.android.adservices.shared.spe.JobServiceConstants.JOB_ENABLED_STATUS_ENABLED;
import static com.android.adservices.shared.spe.JobServiceConstants.SCHEDULING_RESULT_CODE_SUCCESSFUL;
import static com.android.adservices.shared.spe.framework.ExecutionResult.SUCCESS;
import static com.android.adservices.spe.AdServicesJobInfo.TOPICS_EPOCH_JOB;
import static com.android.dx.mockito.inline.extended.ExtendedMockito.doReturn;
import static com.android.dx.mockito.inline.extended.ExtendedMockito.verify;

import static com.google.common.truth.Truth.assertThat;
import static com.google.common.truth.Truth.assertWithMessage;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyBoolean;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.when;

import com.android.adservices.common.AdServicesJobTestCase;
import com.android.adservices.service.FlagsFactory;
import com.android.adservices.shared.proto.JobPolicy;
import com.android.adservices.shared.spe.framework.ExecutionResult;
import com.android.adservices.shared.spe.framework.ExecutionRuntimeParameters;
import com.android.adservices.shared.spe.logging.JobSchedulingLogger;
import com.android.adservices.shared.spe.scheduling.JobSpec;
import com.android.adservices.spe.AdServicesJobScheduler;
import com.android.adservices.spe.AdServicesJobServiceFactory;
import com.android.modules.utils.testing.ExtendedMockitoRule.SpyStatic;

import com.google.common.util.concurrent.ListenableFuture;

import org.junit.Before;
import org.junit.Test;
import org.mockito.Mock;

/** Unit tests for {@link EpochJob}. */
@SpyStatic(AdServicesJobScheduler.class)
@SpyStatic(AdServicesJobServiceFactory.class)
@SpyStatic(EpochJobService.class)
@SpyStatic(FlagsFactory.class)
@SpyStatic(TopicsWorker.class)
public final class EpochJobTest extends AdServicesJobTestCase {
    private final EpochJob mEpochJob = new EpochJob();

    @Mock private TopicsWorker mMockTopicsWorker;
    @Mock private ExecutionRuntimeParameters mMockParams;
    @Mock private AdServicesJobScheduler mMockAdServicesJobScheduler;
    @Mock private AdServicesJobServiceFactory mMockAdServicesJobServiceFactory;

    @Before
    public void setup() {
        mocker.mockGetFlags(mMockFlags);
        mocker.mockSpeJobScheduler(mMockAdServicesJobScheduler);
        mocker.mockAdServicesJobServiceFactory(mMockAdServicesJobServiceFactory);

        doReturn(mMockTopicsWorker).when(TopicsWorker::getInstance);
    }

    @Test
    public void testGetExecutionFuture() throws Exception {
        ListenableFuture<ExecutionResult> executionFuture =
                mEpochJob.getExecutionFuture(mContext, mMockParams);

        assertThat(executionFuture.get()).isEqualTo(SUCCESS);

        verify(mMockTopicsWorker).computeEpoch();
    }

    @Test
    public void testGetJobEnablementStatus_disabled() {
        when(mMockFlags.getTopicsKillSwitch()).thenReturn(true);

        assertWithMessage("getJobEnablementStatus() for Topics API kill switch ON")
                .that(mEpochJob.getJobEnablementStatus())
                .isEqualTo(JOB_ENABLED_STATUS_DISABLED_FOR_KILL_SWITCH_ON);
    }

    @Test
    public void testGetJobEnablementStatus_enabled() {
        when(mMockFlags.getTopicsKillSwitch()).thenReturn(false);

        assertWithMessage("getJobEnablementStatus()")
                .that(mEpochJob.getJobEnablementStatus())
                .isEqualTo(JOB_ENABLED_STATUS_ENABLED);
    }

    @Test
    public void testSchedule_spe() {
        when(mMockFlags.getSpeOnEpochJobEnabled()).thenReturn(true);

        EpochJob.schedule();

        verify(mMockAdServicesJobScheduler).schedule(any(JobSpec.class));
    }

    @Test
    public void testSchedule_legacy() {
        int resultCode = SCHEDULING_RESULT_CODE_SUCCESSFUL;
        when(mMockFlags.getSpeOnEpochJobEnabled()).thenReturn(false);
        JobSchedulingLogger logger =
                mocker.mockJobSchedulingLogger(mMockAdServicesJobServiceFactory);
        doReturn(resultCode).when(() -> EpochJobService.scheduleIfNeeded(anyBoolean()));

        EpochJob.schedule();

        verify(mMockAdServicesJobScheduler, never()).schedule(any(JobSpec.class));
        verify(() -> EpochJobService.scheduleIfNeeded(anyBoolean()));
        verify(logger).recordOnSchedulingLegacy(TOPICS_EPOCH_JOB.getJobId(), resultCode);
    }

    @Test
    public void testCreateDefaultJobSpec_schedulerRequiresChargingEnabled() {
        when(mMockFlags.getTopicsEpochJobBatteryNotLowInsteadOfCharging()).thenReturn(false);
        JobSpec jobSpec = EpochJob.createDefaultJobSpec();

        JobPolicy expectedJobPolicy =
                JobPolicy.newBuilder()
                        .setJobId(TOPICS_EPOCH_JOB.getJobId())
                        .setBatteryType(BATTERY_TYPE_REQUIRE_CHARGING)
                        .setIsPersisted(true)
                        .setPeriodicJobParams(
                                JobPolicy.PeriodicJobParams.newBuilder()
                                        .setPeriodicIntervalMs(TOPICS_EPOCH_JOB_PERIOD_MS)
                                        .setFlexInternalMs(TOPICS_EPOCH_JOB_FLEX_MS)
                                        .build())
                        .build();

        assertWithMessage("createJobSpec() for EpochJob")
                .that(jobSpec.getJobPolicy())
                .isEqualTo(expectedJobPolicy);
    }

    @Test
    public void testCreateDefaultJobSpec_schedulerRequiresChargingDisabled() {
        when(mMockFlags.getTopicsEpochJobBatteryNotLowInsteadOfCharging()).thenReturn(true);
        JobSpec jobSpec = EpochJob.createDefaultJobSpec();

        JobPolicy expectedJobPolicy =
                JobPolicy.newBuilder()
                        .setJobId(TOPICS_EPOCH_JOB.getJobId())
                        .setBatteryType(BATTERY_TYPE_REQUIRE_NOT_LOW)
                        .setIsPersisted(true)
                        .setPeriodicJobParams(
                                JobPolicy.PeriodicJobParams.newBuilder()
                                        .setPeriodicIntervalMs(TOPICS_EPOCH_JOB_PERIOD_MS)
                                        .setFlexInternalMs(TOPICS_EPOCH_JOB_FLEX_MS)
                                        .build())
                        .build();

        assertWithMessage("createJobSpec() for EpochJob")
                .that(jobSpec.getJobPolicy())
                .isEqualTo(expectedJobPolicy);
    }
}

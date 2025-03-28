/*
 * Copyright (C) 2025 The Android Open Source Project
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

import static com.android.adservices.service.Flags.DEFAULT_MEASUREMENT_COUNT_UNIQUE_REPORTING_JOB_PERIOD_MS;
import static com.android.adservices.shared.spe.JobServiceConstants.JOB_ENABLED_STATUS_DISABLED_FOR_KILL_SWITCH_ON;
import static com.android.adservices.shared.spe.JobServiceConstants.JOB_ENABLED_STATUS_ENABLED;
import static com.android.adservices.shared.spe.framework.ExecutionResult.SUCCESS;
import static com.android.adservices.spe.AdServicesJobInfo.MEASUREMENT_COUNT_UNIQUE_REPORTING_JOB;
import static com.android.dx.mockito.inline.extended.ExtendedMockito.doReturn;

import static com.google.common.truth.Truth.assertWithMessage;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import com.android.adservices.common.AdServicesJobTestCase;
import com.android.adservices.data.measurement.DatastoreManager;
import com.android.adservices.service.FlagsFactory;
import com.android.adservices.shared.proto.JobPolicy;
import com.android.adservices.shared.spe.framework.ExecutionResult;
import com.android.adservices.shared.spe.framework.ExecutionRuntimeParameters;
import com.android.adservices.shared.spe.scheduling.JobSpec;
import com.android.adservices.spe.AdServicesJobScheduler;
import com.android.adservices.spe.AdServicesJobServiceFactory;
import com.android.modules.utils.testing.ExtendedMockitoRule.SpyStatic;

import com.google.common.util.concurrent.ListenableFuture;

import org.junit.Before;
import org.junit.Test;
import org.mockito.Mock;

import java.util.concurrent.TimeUnit;

/** Unit tests for {@link CountUniqueReportingJob} */
@SpyStatic(FlagsFactory.class)
@SpyStatic(AdServicesJobScheduler.class)
@SpyStatic(AdServicesJobServiceFactory.class)
@SpyStatic(CountUniqueReportingJobHandler.class)
public class CountUniqueReportingJobTest extends AdServicesJobTestCase {
    private final CountUniqueReportingJob mCountUniqueReportingJob = new CountUniqueReportingJob();
    @Mock private ExecutionRuntimeParameters mMockParams;
    @Mock private AdServicesJobScheduler mMockAdServicesJobScheduler;
    @Mock private AdServicesJobServiceFactory mMockAdServicesJobServiceFactory;
    @Mock protected DatastoreManager mMockDatastoreManager;
    @Mock private CountUniqueReportingJobHandler mMockCountUniqueReportingJobHandler;

    @Before
    public void setup() {
        when(mMockFlags.getMeasurementEnableCountUniqueReportingJob()).thenReturn(true);
        when(mMockFlags.getMeasurementEnableCountUniqueService()).thenReturn(true);
        when(mMockFlags.getMeasurementCountUniqueReportingJobPeriodMs())
                .thenReturn(TimeUnit.HOURS.toMillis(2));

        mocker.mockGetFlags(mMockFlags);
        mocker.mockSpeJobScheduler(mMockAdServicesJobScheduler);
        mocker.mockAdServicesJobServiceFactory(mMockAdServicesJobServiceFactory);
    }

    @Test
    public void testGetExecutionFuture() throws Exception {
        doReturn(mMockCountUniqueReportingJobHandler)
                .when(CountUniqueReportingJobHandler::getInstance);

        ListenableFuture<ExecutionResult> executionFuture =
                mCountUniqueReportingJob.getExecutionFuture(mContext, mMockParams);

        assertWithMessage("getExecutionFuture()").that(executionFuture.get()).isEqualTo(SUCCESS);

        verify(mMockCountUniqueReportingJobHandler).performScheduledPendingReports();
    }

    @Test
    public void testGetJobEnablementStatus_jobDisabled() {
        when(mMockFlags.getMeasurementEnableCountUniqueReportingJob()).thenReturn(false);

        assertWithMessage("getJobEnablementStatus() for Count Unique Reporting Job enabled")
                .that(mCountUniqueReportingJob.getJobEnablementStatus())
                .isEqualTo(JOB_ENABLED_STATUS_DISABLED_FOR_KILL_SWITCH_ON);
    }

    @Test
    public void testGetJobEnablementStatus_countUniqueDisabled() {
        when(mMockFlags.getMeasurementEnableCountUniqueService()).thenReturn(false);

        assertWithMessage("getJobEnablementStatus() for Count Unique Reporting Job enabled")
                .that(mCountUniqueReportingJob.getJobEnablementStatus())
                .isEqualTo(JOB_ENABLED_STATUS_DISABLED_FOR_KILL_SWITCH_ON);
    }

    @Test
    public void testGetJobEnablementStatus_enabled() {
        assertWithMessage("getJobEnablementStatus()")
                .that(mCountUniqueReportingJob.getJobEnablementStatus())
                .isEqualTo(JOB_ENABLED_STATUS_ENABLED);
    }

    @Test
    public void testSchedule_spe() {
        CountUniqueReportingJob.schedule();

        verify(mMockAdServicesJobScheduler).schedule(any(JobSpec.class));
    }

    @Test
    public void testCreateJobSpec() {
        JobSpec jobSpec = CountUniqueReportingJob.createJobSpec();

        JobPolicy.PeriodicJobParams expectedPeriodicJobParams =
                JobPolicy.PeriodicJobParams.newBuilder()
                        .setPeriodicIntervalMs(
                                DEFAULT_MEASUREMENT_COUNT_UNIQUE_REPORTING_JOB_PERIOD_MS)
                        .build();

        JobPolicy expectedJobPolicy =
                JobPolicy.newBuilder()
                        .setJobId(MEASUREMENT_COUNT_UNIQUE_REPORTING_JOB.getJobId())
                        .setBatteryType(JobPolicy.BatteryType.BATTERY_TYPE_REQUIRE_NONE)
                        .setNetworkType(JobPolicy.NetworkType.NETWORK_TYPE_UNMETERED)
                        .setPeriodicJobParams(expectedPeriodicJobParams)
                        .setIsPersisted(true)
                        .build();

        assertWithMessage("createJobSpec() for CountUniqueReportingJob")
                .that(jobSpec.getJobPolicy())
                .isEqualTo(expectedJobPolicy);
    }
}

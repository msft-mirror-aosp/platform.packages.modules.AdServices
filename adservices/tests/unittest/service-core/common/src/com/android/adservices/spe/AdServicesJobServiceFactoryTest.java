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

package com.android.adservices.spe;

import static com.android.adservices.service.stats.AdServicesStatsLog.AD_SERVICES_ERROR_REPORTED__ERROR_CODE__SPE_JOB_NOT_CONFIGURED_CORRECTLY;
import static com.android.adservices.service.stats.AdServicesStatsLog.AD_SERVICES_ERROR_REPORTED__PPAPI_NAME__COMMON;
import static com.android.adservices.shared.spe.JobServiceConstants.SCHEDULING_RESULT_CODE_SUCCESSFUL;
import static com.android.adservices.spe.AdServicesJobInfo.MDD_CELLULAR_CHARGING_PERIODIC_TASK_JOB;
import static com.android.adservices.spe.AdServicesJobInfo.MDD_CHARGING_PERIODIC_TASK_JOB;
import static com.android.adservices.spe.AdServicesJobInfo.MDD_MAINTENANCE_PERIODIC_TASK_JOB;
import static com.android.adservices.spe.AdServicesJobInfo.MDD_WIFI_CHARGING_PERIODIC_TASK_JOB;
import static com.android.dx.mockito.inline.extended.ExtendedMockito.doReturn;
import static com.android.dx.mockito.inline.extended.ExtendedMockito.verify;

import static com.google.common.truth.Truth.assertThat;

import static org.mockito.Mockito.times;

import com.android.adservices.common.AdServicesExtendedMockitoTestCase;
import com.android.adservices.download.MddJob;
import com.android.adservices.download.MddJobService;
import com.android.adservices.service.Flags;
import com.android.adservices.shared.errorlogging.AdServicesErrorLogger;
import com.android.adservices.shared.proto.ModuleJobPolicy;
import com.android.adservices.shared.spe.logging.JobSchedulingLogger;
import com.android.adservices.shared.spe.logging.JobServiceLogger;
import com.android.modules.utils.testing.ExtendedMockitoRule.SpyStatic;

import org.junit.Before;
import org.junit.Test;
import org.mockito.Mock;

import java.util.Map;
import java.util.concurrent.Executor;
import java.util.concurrent.Executors;

/** Unit tests for {@link AdServicesJobServiceFactory} */
@SpyStatic(AdServicesJobInfo.class)
@SpyStatic(MddJob.class)
@SpyStatic(MddJobService.class)
public final class AdServicesJobServiceFactoryTest extends AdServicesExtendedMockitoTestCase {
    private static final Executor sExecutor = Executors.newCachedThreadPool();
    private static final Map<Integer, String> sJobIdToNameMap = Map.of();

    private AdServicesJobServiceFactory mFactory;

    @Mock private JobServiceLogger mMockJobServiceLogger;
    @Mock private JobSchedulingLogger mMockJobSchedulingLogger;

    @Mock private ModuleJobPolicy mMockModuleJobPolicy;

    @Mock private AdServicesErrorLogger mMockErrorLogger;

    @Mock private Flags mMockFlags;

    @Before
    public void setup() {
        mFactory =
                new AdServicesJobServiceFactory(
                        mMockJobServiceLogger,
                        mMockJobSchedulingLogger,
                        mMockModuleJobPolicy,
                        mMockErrorLogger,
                        sJobIdToNameMap,
                        sExecutor,
                        mMockFlags);
    }

    @Test
    public void testGetJobInstance_notConfiguredJob() {
        doReturn(Map.of()).when(AdServicesJobInfo::getJobIdToJobInfoMap);
        int notConfiguredJobId = 1000;

        assertThat(mFactory.getJobWorkerInstance(notConfiguredJobId)).isNull();

        verify(mMockErrorLogger)
                .logError(
                        AD_SERVICES_ERROR_REPORTED__ERROR_CODE__SPE_JOB_NOT_CONFIGURED_CORRECTLY,
                        AD_SERVICES_ERROR_REPORTED__PPAPI_NAME__COMMON);
    }

    @Test
    public void testGetJobInstance() {
        expect.withMessage("getJobWorkerInstance()")
                .that(mFactory.getJobWorkerInstance(MDD_MAINTENANCE_PERIODIC_TASK_JOB.getJobId()))
                .isInstanceOf(MddJob.class);
        expect.withMessage("getJobWorkerInstance()")
                .that(mFactory.getJobWorkerInstance(MDD_CHARGING_PERIODIC_TASK_JOB.getJobId()))
                .isInstanceOf(MddJob.class);
        expect.withMessage("getJobWorkerInstance()")
                .that(
                        mFactory.getJobWorkerInstance(
                                MDD_CELLULAR_CHARGING_PERIODIC_TASK_JOB.getJobId()))
                .isInstanceOf(MddJob.class);
        expect.withMessage("getJobWorkerInstance()")
                .that(mFactory.getJobWorkerInstance(MDD_WIFI_CHARGING_PERIODIC_TASK_JOB.getJobId()))
                .isInstanceOf(MddJob.class);
    }

    @Test
    public void testRescheduleJobWithLegacyMethod_notConfiguredJob() {
        doReturn(Map.of()).when(AdServicesJobInfo::getJobIdToJobInfoMap);
        int notConfiguredJobId = 1000;

        mFactory.rescheduleJobWithLegacyMethod(notConfiguredJobId);

        verify(mMockErrorLogger)
                .logError(
                        AD_SERVICES_ERROR_REPORTED__ERROR_CODE__SPE_JOB_NOT_CONFIGURED_CORRECTLY,
                        AD_SERVICES_ERROR_REPORTED__PPAPI_NAME__COMMON);
    }

    @Test
    public void testRescheduleJobWithLegacyMethod_mddJobs() {
        boolean forceSchedule = true;
        doReturn(SCHEDULING_RESULT_CODE_SUCCESSFUL)
                .when(() -> MddJobService.scheduleIfNeeded(forceSchedule));

        mFactory.rescheduleJobWithLegacyMethod(MDD_MAINTENANCE_PERIODIC_TASK_JOB.getJobId());
        verify(() -> MddJobService.scheduleIfNeeded(forceSchedule));
        mFactory.rescheduleJobWithLegacyMethod(MDD_CHARGING_PERIODIC_TASK_JOB.getJobId());
        verify(() -> MddJobService.scheduleIfNeeded(forceSchedule), times(2));
        mFactory.rescheduleJobWithLegacyMethod(MDD_CELLULAR_CHARGING_PERIODIC_TASK_JOB.getJobId());
        verify(() -> MddJobService.scheduleIfNeeded(forceSchedule), times(3));
        mFactory.rescheduleJobWithLegacyMethod(MDD_WIFI_CHARGING_PERIODIC_TASK_JOB.getJobId());
        verify(() -> MddJobService.scheduleIfNeeded(forceSchedule), times(4));
    }

    @Test
    public void testGetJobIdToNameMap() {
        assertThat(mFactory.getJobIdToNameMap()).isSameInstanceAs(sJobIdToNameMap);
    }

    @Test
    public void testGetJobServiceLogger() {
        assertThat(mFactory.getJobServiceLogger()).isSameInstanceAs(mMockJobServiceLogger);
    }

    @Test
    public void testGetJobSchedulingLogger() {
        assertThat(mFactory.getJobSchedulingLogger()).isSameInstanceAs(mMockJobSchedulingLogger);
    }

    @Test
    public void testGetErrorLogger() {
        assertThat(mFactory.getErrorLogger()).isSameInstanceAs(mMockErrorLogger);
    }

    @Test
    public void testGetExecutor() {
        assertThat(mFactory.getBackgroundExecutor()).isSameInstanceAs(sExecutor);
    }

    @Test
    public void testGetModuleJobPolicy() {
        assertThat(mFactory.getModuleJobPolicy()).isSameInstanceAs(mMockModuleJobPolicy);
    }

    @Test
    public void testGetFlags() {
        assertThat(mFactory.getFlags()).isSameInstanceAs(mMockFlags);
    }
}

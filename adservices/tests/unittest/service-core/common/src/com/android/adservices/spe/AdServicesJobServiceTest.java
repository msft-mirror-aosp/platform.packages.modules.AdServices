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

import static com.android.adservices.shared.spe.JobServiceConstants.JOB_ENABLED_STATUS_DISABLED_FOR_BACK_COMPAT_OTA;
import static com.android.adservices.shared.spe.JobServiceConstants.SKIP_REASON_JOB_NOT_CONFIGURED;
import static com.android.dx.mockito.inline.extended.ExtendedMockito.doReturn;

import static com.google.common.truth.Truth.assertThat;
import static com.google.common.truth.Truth.assertWithMessage;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyInt;
import static org.mockito.Mockito.doAnswer;
import static org.mockito.Mockito.doNothing;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import android.app.job.JobInfo;
import android.app.job.JobParameters;
import android.app.job.JobScheduler;
import android.content.ComponentName;

import com.android.adservices.common.AdServicesExtendedMockitoTestCase;
import com.android.adservices.service.Flags;
import com.android.adservices.service.FlagsFactory;
import com.android.adservices.service.common.compat.ServiceCompatUtils;
import com.android.adservices.shared.spe.logging.JobServiceLogger;
import com.android.modules.utils.testing.ExtendedMockitoRule.SpyStatic;

import org.junit.After;
import org.junit.Before;
import org.junit.Test;
import org.mockito.Mock;
import org.mockito.Spy;

/** Unit tests for {@link AdServicesJobService}. */
@SpyStatic(ServiceCompatUtils.class)
@SpyStatic(FlagsFactory.class)
public final class AdServicesJobServiceTest extends AdServicesExtendedMockitoTestCase {
    private final JobScheduler mJobScheduler = sContext.getSystemService(JobScheduler.class);

    @Spy AdServicesJobService mSpyAdServicesJobService;
    @Mock JobServiceLogger mMockLogger;
    @Mock JobParameters mMockParameters;
    @Mock AdServicesJobServiceFactory mMockJobServiceFactory;
    @Mock Flags mMockFlags;

    @Before
    public void setup() {
        assertWithMessage("The JobScheduler").that(mJobScheduler).isNotNull();

        extendedMockito.mockGetFlags(mMockFlags);
        // By default, enable SPE.
        when(mMockFlags.getSpeOnPilotJobsEnabled()).thenReturn(true);

        // By default, do not skip for back compat.
        doReturn(false)
                .when(
                        () ->
                                ServiceCompatUtils.shouldDisableExtServicesJobOnTPlus(
                                        mSpyAdServicesJobService));

        doReturn(mMockLogger).when(mMockJobServiceFactory).getJobServiceLogger();

        doReturn(mMockJobServiceFactory).when(mSpyAdServicesJobService).getJobServiceFactory();
        mSpyAdServicesJobService.onCreate();
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
    public void testOnStartJob_notSkip() {
        doNothing().when(mMockLogger).recordOnStartJob(anyInt());

        // The Parent class's onStartJob() returns at the beginning due to null idToNameMapping.
        doNothing().when(mSpyAdServicesJobService).skipAndCancelBackgroundJob(any(), anyInt());

        // The execution will be skipped due to not configured but not back compat.
        assertThat(mSpyAdServicesJobService.onStartJob(mMockParameters)).isFalse();
        verify(mMockLogger).recordOnStartJob(anyInt());
        verify(mSpyAdServicesJobService)
                .skipAndCancelBackgroundJob(mMockParameters, SKIP_REASON_JOB_NOT_CONFIGURED);
    }

    @Test
    public void testOnStartJob_skipForCompat_skip() {
        doNothing().when(mMockLogger).recordOnStartJob(anyInt());

        // Skip for back compat.
        doReturn(true)
                .when(
                        () ->
                                ServiceCompatUtils.shouldDisableExtServicesJobOnTPlus(
                                        mSpyAdServicesJobService));

        // The Parent class's onStartJob() returns at the beginning due to null idToNameMapping.
        doNothing().when(mSpyAdServicesJobService).skipAndCancelBackgroundJob(any(), anyInt());

        // The execution will be skipped due to back compat.
        assertThat(mSpyAdServicesJobService.onStartJob(mMockParameters)).isFalse();
        verify(mMockLogger).recordOnStartJob(anyInt());
        verify(mSpyAdServicesJobService)
                .skipAndCancelBackgroundJob(
                        mMockParameters, JOB_ENABLED_STATUS_DISABLED_FOR_BACK_COMPAT_OTA);
    }

    @Test
    public void testOnStartJob_rescheduleWithLegacyMethod() {
        int jobId = 1;
        // Unreachable latency to prevent the job to execute.
        long minimumLatencyMs1 = 60 * 60 * 1000;
        long minimumLatencyMs2 = minimumLatencyMs1 + 1;

        // Create a job pending to but won't execute.
        when(mMockParameters.getJobId()).thenReturn(jobId);
        JobInfo jobInfo1 =
                new JobInfo.Builder(jobId, new ComponentName(sContext, AdServicesJobService.class))
                        .setMinimumLatency(minimumLatencyMs1)
                        .build();
        mJobScheduler.schedule(jobInfo1);

        // Mock the rescheduling method to reschedule the same job with a different minimum latency.
        doAnswer(
                        invocation -> {
                            JobInfo jobInfo2 =
                                    new JobInfo.Builder(
                                                    jobId,
                                                    new ComponentName(
                                                            sContext, AdServicesJobService.class))
                                            .setMinimumLatency(minimumLatencyMs2)
                                            .build();
                            mJobScheduler.schedule(jobInfo2);
                            return null;
                        })
                .when(mMockJobServiceFactory)
                .rescheduleJobWithLegacyMethod(jobId);

        // Disable SPE and the job should be rescheduled by the legacy scheduling method.
        when(mMockFlags.getSpeOnPilotJobsEnabled()).thenReturn(false);
        assertWithMessage("mSpyAdServicesJobService.onStartJob()")
                .that(mSpyAdServicesJobService.onStartJob(mMockParameters))
                .isFalse();

        // Verify the job is rescheduled.
        JobInfo actualJobInfo = mJobScheduler.getPendingJob(jobId);
        assertWithMessage("Actual minimum latency")
                .that(actualJobInfo.getMinLatencyMillis())
                .isEqualTo(minimumLatencyMs2);
        verify(mMockLogger, never()).recordOnStartJob(anyInt());
    }
}

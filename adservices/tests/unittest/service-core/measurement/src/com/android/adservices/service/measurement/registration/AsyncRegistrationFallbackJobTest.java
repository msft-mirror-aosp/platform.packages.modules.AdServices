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

package com.android.adservices.service.measurement.registration;

import static com.android.adservices.service.Flags.ASYNC_REGISTRATION_JOB_QUEUE_INTERVAL_MS;
import static com.android.adservices.shared.proto.JobPolicy.BatteryType.BATTERY_TYPE_REQUIRE_NOT_LOW;
import static com.android.adservices.shared.proto.JobPolicy.NetworkType.NETWORK_TYPE_ANY;
import static com.android.adservices.shared.spe.JobServiceConstants.JOB_ENABLED_STATUS_DISABLED_FOR_KILL_SWITCH_ON;
import static com.android.adservices.shared.spe.JobServiceConstants.JOB_ENABLED_STATUS_ENABLED;
import static com.android.adservices.shared.spe.JobServiceConstants.SCHEDULING_RESULT_CODE_SUCCESSFUL;
import static com.android.adservices.shared.spe.framework.ExecutionResult.SUCCESS;
import static com.android.adservices.spe.AdServicesJobInfo.MEASUREMENT_ASYNC_REGISTRATION_FALLBACK_JOB;
import static com.android.dx.mockito.inline.extended.ExtendedMockito.doReturn;

import static com.google.common.truth.Truth.assertWithMessage;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyBoolean;
import static org.mockito.Mockito.doNothing;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import com.android.adservices.common.AdServicesJobTestCase;
import com.android.adservices.service.FlagsFactory;
import com.android.adservices.shared.proto.JobPolicy;
import com.android.adservices.shared.spe.framework.ExecutionResult;
import com.android.adservices.shared.spe.framework.ExecutionRuntimeParameters;
import com.android.adservices.shared.spe.logging.JobSchedulingLogger;
import com.android.adservices.shared.spe.scheduling.BackoffPolicy;
import com.android.adservices.shared.spe.scheduling.JobSpec;
import com.android.adservices.spe.AdServicesJobScheduler;
import com.android.adservices.spe.AdServicesJobServiceFactory;
import com.android.dx.mockito.inline.extended.ExtendedMockito;
import com.android.modules.utils.testing.ExtendedMockitoRule.MockStatic;

import com.google.common.util.concurrent.ListenableFuture;

import org.junit.Before;
import org.junit.Test;
import org.mockito.Mock;
import org.mockito.Spy;

/** Unit tests for {@link AsyncRegistrationFallbackJobTest}. */
@MockStatic(AdServicesJobScheduler.class)
@MockStatic(AdServicesJobServiceFactory.class)
@MockStatic(AsyncRegistrationFallbackJobService.class)
@MockStatic(FlagsFactory.class)
public final class AsyncRegistrationFallbackJobTest extends AdServicesJobTestCase {
    @Spy AsyncRegistrationFallbackJob mSpyAsyncRegistrationFallbackJob;
    @Mock private ExecutionRuntimeParameters mMockParams;
    @Mock private AdServicesJobScheduler mMockAdServicesJobScheduler;
    @Mock private AdServicesJobServiceFactory mMockAdServicesJobServiceFactory;

    @Before
    public void setup() {
        mocker.mockGetFlags(mMockFlags);
        mocker.mockSpeJobScheduler(mMockAdServicesJobScheduler);
        mocker.mockAdServicesJobServiceFactory(mMockAdServicesJobServiceFactory);

        // Mock processAsyncRecords() to do nothing unless asked.
        doNothing().when(mSpyAsyncRegistrationFallbackJob).processAsyncRecords(any());
    }

    @Test
    @SuppressWarnings("unused")
    public void testGetExecutionFuture() throws Exception {
        ListenableFuture<ExecutionResult> executionFuture =
                mSpyAsyncRegistrationFallbackJob.getExecutionFuture(sContext, mMockParams);

        assertWithMessage("testGetExecutionFuture().get()")
                .that(executionFuture.get())
                .isEqualTo(SUCCESS);
        verify(mSpyAsyncRegistrationFallbackJob).processAsyncRecords(sContext);
    }

    @Test
    public void testGetJobEnablementStatus_asyncRegistrationFallbackKillSwitchOn() {
        when(mMockFlags.getAsyncRegistrationFallbackJobKillSwitch()).thenReturn(true);

        assertWithMessage("getJobEnablementStatus() for AsyncRegistrationFallback kill switch ON")
                .that(mSpyAsyncRegistrationFallbackJob.getJobEnablementStatus())
                .isEqualTo(JOB_ENABLED_STATUS_DISABLED_FOR_KILL_SWITCH_ON);
    }

    @Test
    public void testGetJobEnablementStatus_enabled() {
        when(mMockFlags.getAsyncRegistrationFallbackJobKillSwitch()).thenReturn(false);

        assertWithMessage("getJobEnablementStatus() for AsyncRegistrationFallback kill switch OFF")
                .that(mSpyAsyncRegistrationFallbackJob.getJobEnablementStatus())
                .isEqualTo(JOB_ENABLED_STATUS_ENABLED);
    }

    @Test
    public void testSchedule_spe() {
        when(mMockFlags.getSpeOnAsyncRegistrationFallbackJobEnabled()).thenReturn(true);

        AsyncRegistrationFallbackJob.schedule();

        verify(mMockAdServicesJobScheduler).schedule(any());
    }

    @Test
    public void testSchedule_legacy() {
        int resultCode = SCHEDULING_RESULT_CODE_SUCCESSFUL;
        when(mMockFlags.getSpeOnAsyncRegistrationFallbackJobEnabled()).thenReturn(false);
        JobSchedulingLogger mockedLogger =
                mocker.mockJobSchedulingLogger(mMockAdServicesJobServiceFactory);
        doReturn(resultCode)
                .when(() -> AsyncRegistrationFallbackJobService.scheduleIfNeeded(anyBoolean()));

        AsyncRegistrationFallbackJob.schedule();

        verify(mMockAdServicesJobScheduler, never()).schedule(any());
        ExtendedMockito.verify(() -> AsyncRegistrationFallbackJobService.scheduleIfNeeded(false));
        verify(mockedLogger)
                .recordOnSchedulingLegacy(
                        MEASUREMENT_ASYNC_REGISTRATION_FALLBACK_JOB.getJobId(), resultCode);
    }

    @Test
    public void testCreateDefaultJobSpec() {
        JobPolicy expectedJobPolicy =
                JobPolicy.newBuilder()
                        .setJobId(MEASUREMENT_ASYNC_REGISTRATION_FALLBACK_JOB.getJobId())
                        .setBatteryType(BATTERY_TYPE_REQUIRE_NOT_LOW)
                        .setPeriodicJobParams(
                                JobPolicy.PeriodicJobParams.newBuilder()
                                        .setPeriodicIntervalMs(
                                                ASYNC_REGISTRATION_JOB_QUEUE_INTERVAL_MS)
                                        .build())
                        .setNetworkType(NETWORK_TYPE_ANY)
                        .setIsPersisted(true)
                        .build();

        BackoffPolicy backoffPolicy =
                new BackoffPolicy.Builder().setShouldRetryOnExecutionStop(true).build();

        assertWithMessage("createDefaultJobSpec() for AsyncRegistrationFallbackJob")
                .that(AsyncRegistrationFallbackJob.createDefaultJobSpec())
                .isEqualTo(
                        new JobSpec.Builder(expectedJobPolicy)
                                .setBackoffPolicy(backoffPolicy)
                                .build());
    }
}

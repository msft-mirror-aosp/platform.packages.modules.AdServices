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

package com.android.adservices.service.customaudience;

import static com.android.adservices.service.Flags.FLEDGE_BACKGROUND_FETCH_JOB_FLEX_MS;
import static com.android.adservices.service.Flags.FLEDGE_BACKGROUND_FETCH_JOB_PERIOD_MS;
import static com.android.adservices.service.consent.AdServicesApiConsent.GIVEN;
import static com.android.adservices.service.consent.AdServicesApiConsent.REVOKED;
import static com.android.adservices.service.consent.AdServicesApiType.FLEDGE;
import static com.android.adservices.shared.proto.JobPolicy.BatteryType.BATTERY_TYPE_REQUIRE_NOT_LOW;
import static com.android.adservices.shared.spe.JobServiceConstants.JOB_ENABLED_STATUS_DISABLED_FOR_KILL_SWITCH_ON;
import static com.android.adservices.shared.spe.JobServiceConstants.JOB_ENABLED_STATUS_DISABLED_FOR_USER_CONSENT_REVOKED;
import static com.android.adservices.shared.spe.JobServiceConstants.JOB_ENABLED_STATUS_ENABLED;
import static com.android.adservices.shared.spe.JobServiceConstants.SCHEDULING_RESULT_CODE_SUCCESSFUL;
import static com.android.adservices.shared.spe.framework.ExecutionResult.SUCCESS;
import static com.android.adservices.spe.AdServicesJobInfo.FLEDGE_BACKGROUND_FETCH_JOB;
import static com.android.dx.mockito.inline.extended.ExtendedMockito.doReturn;
import static com.android.dx.mockito.inline.extended.ExtendedMockito.verify;

import static com.google.common.truth.Truth.assertWithMessage;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyBoolean;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.when;

import com.android.adservices.common.AdServicesJobTestCase;
import com.android.adservices.service.FlagsFactory;
import com.android.adservices.service.consent.ConsentManager;
import com.android.adservices.shared.proto.JobPolicy;
import com.android.adservices.shared.spe.framework.ExecutionResult;
import com.android.adservices.shared.spe.framework.ExecutionRuntimeParameters;
import com.android.adservices.shared.spe.logging.JobSchedulingLogger;
import com.android.adservices.shared.spe.scheduling.BackoffPolicy;
import com.android.adservices.shared.spe.scheduling.JobSpec;
import com.android.adservices.spe.AdServicesJobScheduler;
import com.android.adservices.spe.AdServicesJobServiceFactory;
import com.android.modules.utils.testing.ExtendedMockitoRule.MockStatic;

import com.google.common.util.concurrent.FluentFuture;
import com.google.common.util.concurrent.Futures;
import com.google.common.util.concurrent.ListenableFuture;

import org.junit.Before;
import org.junit.Test;
import org.mockito.Mock;

/** Unit tests for {@link BackgroundFetchJob}. */
@MockStatic(AdServicesJobScheduler.class)
@MockStatic(AdServicesJobServiceFactory.class)
@MockStatic(BackgroundFetchJobService.class)
@MockStatic(BackgroundFetchWorker.class)
@MockStatic(ConsentManager.class)
@MockStatic(FlagsFactory.class)
public final class BackgroundFetchJobTest extends AdServicesJobTestCase {
    private final BackgroundFetchJob mBackgroundFetchJob = new BackgroundFetchJob();

    @Mock private BackgroundFetchWorker mMockBackgroundFetchWorker;
    @Mock private ConsentManager mMockConsentManager;
    @Mock private ExecutionRuntimeParameters mMockParams;
    @Mock private AdServicesJobScheduler mMockAdServicesJobScheduler;
    @Mock private AdServicesJobServiceFactory mMockAdServicesJobServiceFactory;

    @Before
    public void setup() {
        mocker.mockGetFlags(mMockFlags);
        mocker.mockSpeJobScheduler(mMockAdServicesJobScheduler);
        mocker.mockAdServicesJobServiceFactory(mMockAdServicesJobServiceFactory);

        doReturn(mMockConsentManager).when(ConsentManager::getInstance);
        when(mMockConsentManager.getConsent(FLEDGE)).thenReturn(GIVEN);

        // Mock BackgroundFetchWorker.
        doReturn(mMockBackgroundFetchWorker).when(BackgroundFetchWorker::getInstance);
        when(mMockBackgroundFetchWorker.runBackgroundFetch())
                .thenReturn(FluentFuture.from(Futures.immediateVoidFuture()));
    }

    @Test
    public void testGetExecutionFuture() throws Exception {
        ListenableFuture<ExecutionResult> executionFuture =
                mBackgroundFetchJob.getExecutionFuture(mContext, mMockParams);

        assertWithMessage("testGetExecutionFuture().get()")
                .that(executionFuture.get())
                .isEqualTo(SUCCESS);
        @SuppressWarnings("unused")
        FluentFuture<Void> unusedFuture = verify(mMockBackgroundFetchWorker).runBackgroundFetch();
    }

    @Test
    public void testGetJobEnablementStatus_backgroundFetchDisabled() {
        when(mMockFlags.getFledgeBackgroundFetchEnabled()).thenReturn(false);

        assertWithMessage("getJobEnablementStatus() for BackgroundFetch feature flag OFF")
                .that(mBackgroundFetchJob.getJobEnablementStatus())
                .isEqualTo(JOB_ENABLED_STATUS_DISABLED_FOR_KILL_SWITCH_ON);
    }

    @Test
    public void testGetJobEnablementStatus_customAudienceServiceKillSwitchOn() {
        when(mMockFlags.getFledgeBackgroundFetchEnabled()).thenReturn(true);
        when(mMockFlags.getFledgeCustomAudienceServiceKillSwitch()).thenReturn(true);

        assertWithMessage("getJobEnablementStatus() for Custom Audience kill switch ON")
                .that(mBackgroundFetchJob.getJobEnablementStatus())
                .isEqualTo(JOB_ENABLED_STATUS_DISABLED_FOR_KILL_SWITCH_ON);
    }

    @Test
    public void testGetJobEnablementStatus_fledgeConsentRevoked() {
        when(mMockFlags.getFledgeBackgroundFetchEnabled()).thenReturn(true);
        when(mMockFlags.getFledgeCustomAudienceServiceKillSwitch()).thenReturn(false);
        when(mMockConsentManager.getConsent(FLEDGE)).thenReturn(REVOKED);

        assertWithMessage("getJobEnablementStatus() for revoked Fledge consent")
                .that(mBackgroundFetchJob.getJobEnablementStatus())
                .isEqualTo(JOB_ENABLED_STATUS_DISABLED_FOR_USER_CONSENT_REVOKED);
    }

    @Test
    public void testGetJobEnablementStatus_enabled() {
        when(mMockFlags.getFledgeBackgroundFetchEnabled()).thenReturn(true);
        when(mMockFlags.getFledgeCustomAudienceServiceKillSwitch()).thenReturn(false);

        assertWithMessage("getJobEnablementStatus()")
                .that(mBackgroundFetchJob.getJobEnablementStatus())
                .isEqualTo(JOB_ENABLED_STATUS_ENABLED);
    }

    @Test
    public void testSchedule_spe() {
        when(mMockFlags.getSpeOnBackgroundFetchJobEnabled()).thenReturn(true);

        BackgroundFetchJob.schedule(mMockFlags);

        verify(mMockAdServicesJobScheduler).schedule(any(JobSpec.class));
    }

    @Test
    public void testSchedule_legacy() {
        int resultCode = SCHEDULING_RESULT_CODE_SUCCESSFUL;
        when(mMockFlags.getSpeOnBackgroundFetchJobEnabled()).thenReturn(false);
        JobSchedulingLogger mockedLogger =
                mocker.mockJobSchedulingLogger(mMockAdServicesJobServiceFactory);
        doReturn(resultCode)
                .when(() -> BackgroundFetchJobService.scheduleIfNeeded(any(), anyBoolean()));

        BackgroundFetchJob.schedule(mMockFlags);

        verify(mMockAdServicesJobScheduler, never()).schedule(any());
        verify(() -> BackgroundFetchJobService.scheduleIfNeeded(any(), anyBoolean()));
        verify(mockedLogger)
                .recordOnSchedulingLegacy(FLEDGE_BACKGROUND_FETCH_JOB.getJobId(), resultCode);
    }

    @Test
    public void testCreateDefaultJobSpec() {
        JobSpec jobSpec = BackgroundFetchJob.createDefaultJobSpec();

        JobPolicy expectedJobPolicy =
                JobPolicy.newBuilder()
                        .setJobId(FLEDGE_BACKGROUND_FETCH_JOB.getJobId())
                        .setBatteryType(BATTERY_TYPE_REQUIRE_NOT_LOW)
                        .setRequireDeviceIdle(true)
                        .setPeriodicJobParams(
                                JobPolicy.PeriodicJobParams.newBuilder()
                                        .setPeriodicIntervalMs(
                                                FLEDGE_BACKGROUND_FETCH_JOB_PERIOD_MS)
                                        .setFlexInternalMs(FLEDGE_BACKGROUND_FETCH_JOB_FLEX_MS)
                                        .build())
                        .setNetworkType(JobPolicy.NetworkType.NETWORK_TYPE_UNMETERED)
                        .setIsPersisted(true)
                        .build();

        BackoffPolicy backoffPolicy =
                new BackoffPolicy.Builder().setShouldRetryOnExecutionStop(true).build();

        assertWithMessage("createDefaultJobSpec() for BackgroundFetchJob")
                .that(jobSpec)
                .isEqualTo(
                        new JobSpec.Builder(expectedJobPolicy)
                                .setBackoffPolicy(backoffPolicy)
                                .build());
    }
}

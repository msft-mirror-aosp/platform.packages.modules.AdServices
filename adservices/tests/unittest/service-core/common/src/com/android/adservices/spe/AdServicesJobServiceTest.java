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

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyInt;
import static org.mockito.Mockito.doNothing;
import static org.mockito.Mockito.verify;

import android.app.job.JobParameters;

import com.android.adservices.common.AdServicesExtendedMockitoTestCase;
import com.android.adservices.service.common.compat.ServiceCompatUtils;
import com.android.adservices.shared.spe.framework.JobServiceFactory;
import com.android.adservices.shared.spe.logging.JobServiceLogger;
import com.android.modules.utils.testing.ExtendedMockitoRule.SpyStatic;

import org.junit.Before;
import org.junit.Test;
import org.mockito.Mock;
import org.mockito.Spy;

/** Unit tests for {@link AdServicesJobService}. */
@SpyStatic(ServiceCompatUtils.class)
public final class AdServicesJobServiceTest extends AdServicesExtendedMockitoTestCase {
    @Spy AdServicesJobService mSpyAdServicesJobService;
    @Mock JobServiceLogger mMockLogger;
    @Mock JobParameters mMockParameters;
    @Mock JobServiceFactory mMockJobServiceFactory;

    @Before
    public void setup() {
        doReturn(mMockLogger).when(mMockJobServiceFactory).getJobServiceLogger();

        doReturn(mMockJobServiceFactory).when(mSpyAdServicesJobService).getJobServiceFactory();
        mSpyAdServicesJobService.onCreate();
    }

    @Test
    public void testOnStartJob_skipForCompat_notSkip() {
        doNothing().when(mMockLogger).recordOnStartJob(anyInt());

        // Do NOT skip for back compat.
        doReturn(false)
                .when(
                        () ->
                                ServiceCompatUtils.shouldDisableExtServicesJobOnTPlus(
                                        mSpyAdServicesJobService));

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
}

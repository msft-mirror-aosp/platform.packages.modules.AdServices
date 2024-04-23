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

package com.android.adservices.shared.spe.framework;

import static com.android.adservices.shared.spe.JobServiceConstants.JOB_ENABLED_STATUS_DISABLED_FOR_KILL_SWITCH_ON;
import static com.android.adservices.shared.spe.JobServiceConstants.JOB_ENABLED_STATUS_ENABLED;

import static com.google.common.truth.Truth.assertThat;

import static org.mockito.Mockito.doReturn;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.spy;

import android.content.Context;

import com.android.adservices.common.AdServicesMockitoTestCase;
import com.android.adservices.shared.spe.JobServiceConstants.JobEnablementStatus;
import com.android.adservices.shared.spe.scheduling.BackoffPolicy;

import com.google.common.util.concurrent.Futures;
import com.google.common.util.concurrent.ListenableFuture;

import org.junit.Test;

/** Unit tests for default methods in {@link JobWorker}. */
public final class JobWorkerTest extends AdServicesMockitoTestCase {
    private static final JobWorker sJobWorker =
            new JobWorker() {
                @Override
                public ListenableFuture<ExecutionResult> getExecutionFuture(
                        Context context, ExecutionRuntimeParameters executionRuntimeParameters) {
                    return null;
                }

                @Override
                @JobEnablementStatus
                public int getJobEnablementStatus() {
                    return JOB_ENABLED_STATUS_ENABLED;
                }
            };

    @Test
    public void testGetBackoffPolicy() {
        BackoffPolicy backoffPolicy = new BackoffPolicy.Builder().build();

        assertThat(sJobWorker.getBackoffPolicy()).isEqualTo(backoffPolicy);
    }

    @Test
    public void testGetExecutionStopFuture() {
        ExecutionRuntimeParameters mockParams = mock(ExecutionRuntimeParameters.class);
        assertThat(sJobWorker.getExecutionStopFuture(sContext, mockParams))
                .isEqualTo(Futures.immediateVoidFuture());
    }

    @Test
    public void testsGetJobSchedulingEnablementStatus() {
        JobWorker spyJobWorker = spy(sJobWorker);

        doReturn(JOB_ENABLED_STATUS_ENABLED).when(spyJobWorker).getJobEnablementStatus();
        expect.that(spyJobWorker.getJobSchedulingEnablementStatus())
                .isEqualTo(JOB_ENABLED_STATUS_ENABLED);

        doReturn(JOB_ENABLED_STATUS_DISABLED_FOR_KILL_SWITCH_ON)
                .when(spyJobWorker)
                .getJobEnablementStatus();
        expect.that(spyJobWorker.getJobSchedulingEnablementStatus())
                .isEqualTo(JOB_ENABLED_STATUS_DISABLED_FOR_KILL_SWITCH_ON);
    }
}

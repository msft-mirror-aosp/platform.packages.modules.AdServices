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

package com.android.adservices.shared.spe.logging;

import static com.android.adservices.shared.spe.JobServiceConstants.SCHEDULER_TYPE_JOB_SCHEDULER;
import static com.android.adservices.shared.spe.JobServiceConstants.SCHEDULER_TYPE_SPE;
import static com.android.adservices.shared.spe.JobServiceConstants.SCHEDULING_LOGGING_UNKNOWN_MODULE_NAME;
import static com.android.adservices.shared.spe.JobServiceConstants.SCHEDULING_RESULT_CODE_SUCCESSFUL;

import static com.google.common.truth.Truth.assertThat;

import static org.junit.Assert.assertThrows;
import static org.mockito.ArgumentMatchers.anyInt;
import static org.mockito.Mockito.doAnswer;
import static org.mockito.Mockito.doReturn;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.spy;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import com.android.adservices.common.AdServicesMockitoTestCase;
import com.android.adservices.shared.common.flags.ModuleSharedFlags;
import com.android.adservices.shared.testing.NoFailureSyncCallback;

import org.junit.Before;
import org.junit.Test;
import org.mockito.ArgumentCaptor;
import org.mockito.Mock;

import java.util.concurrent.Executors;

/** Unit test for {@link JobSchedulingLogger}. */
public final class JobSchedulingLoggerTest extends AdServicesMockitoTestCase {
    private static final int JOB_ID = 1;
    private static final int RESULT_CODE = SCHEDULING_RESULT_CODE_SUCCESSFUL;

    @Mock private StatsdJobServiceLogger mMockStatsdLogger;
    @Mock private ModuleSharedFlags mMockFlags;

    private JobSchedulingLogger mSpyJobSchedulingLogger;

    @Before
    public void setup() {
        mSpyJobSchedulingLogger =
                spy(
                        new JobSchedulingLogger(
                                mMockStatsdLogger, Executors.newCachedThreadPool(), mMockFlags));
    }

    @Test
    public void testConstructor_nullCheck() {
        assertThrows(
                NullPointerException.class,
                () ->
                        new JobSchedulingLogger(
                                /* statsdLogger= */ null,
                                Executors.newCachedThreadPool(),
                                mMockFlags));

        assertThrows(
                NullPointerException.class,
                () ->
                        new JobSchedulingLogger(
                                mMockStatsdLogger, /* loggingExecutor= */ null, mMockFlags));

        assertThrows(
                NullPointerException.class,
                () ->
                        new JobSchedulingLogger(
                                mMockStatsdLogger,
                                Executors.newCachedThreadPool(),
                                /* flags= */ null));
    }

    @Test
    public void testRecordOnScheduling_disabled() {
        when(mMockFlags.getJobSchedulingLoggingEnabled()).thenReturn(false);

        syncLogSchedulingStatsHelper();
        mSpyJobSchedulingLogger.recordOnScheduling(JOB_ID, RESULT_CODE);

        verify(mSpyJobSchedulingLogger, never())
                .logSchedulingStatsHelper(anyInt(), anyInt(), anyInt());
    }

    @Test
    public void testRecordOnScheduling_enabled() throws Exception {
        when(mMockFlags.getJobSchedulingLoggingEnabled()).thenReturn(true);

        NoFailureSyncCallback<Void> callback = syncLogSchedulingStatsHelper();
        mSpyJobSchedulingLogger.recordOnScheduling(JOB_ID, RESULT_CODE);

        callback.assertReceived();
        verify(mSpyJobSchedulingLogger)
                .logSchedulingStatsHelper(JOB_ID, RESULT_CODE, SCHEDULER_TYPE_SPE);
    }

    @Test
    public void testRecordOnSchedulingLagacy_disabled() {
        when(mMockFlags.getJobSchedulingLoggingEnabled()).thenReturn(false);

        syncLogSchedulingStatsHelper();
        mSpyJobSchedulingLogger.recordOnSchedulingLegacy(JOB_ID, RESULT_CODE);

        verify(mSpyJobSchedulingLogger, never())
                .logSchedulingStatsHelper(anyInt(), anyInt(), anyInt());
    }

    @Test
    public void testRecordOnSchedulingLagacy_enabled() throws Exception {
        when(mMockFlags.getJobSchedulingLoggingEnabled()).thenReturn(true);

        NoFailureSyncCallback<Void> callback = syncLogSchedulingStatsHelper();
        mSpyJobSchedulingLogger.recordOnSchedulingLegacy(JOB_ID, RESULT_CODE);

        callback.assertReceived();
        verify(mSpyJobSchedulingLogger)
                .logSchedulingStatsHelper(JOB_ID, RESULT_CODE, SCHEDULER_TYPE_JOB_SCHEDULER);
    }

    @Test
    public void testLogJobStatsHelper() {
        // Mock to avoid sampling logging
        doReturn(true).when(mSpyJobSchedulingLogger).shouldLog();

        ArgumentCaptor<SchedulingReportedStats> captor =
                ArgumentCaptor.forClass(SchedulingReportedStats.class);

        mSpyJobSchedulingLogger.logSchedulingStatsHelper(JOB_ID, RESULT_CODE, SCHEDULER_TYPE_SPE);

        verify(mMockStatsdLogger).logSchedulingReportedStats(captor.capture());
        assertThat(captor.getValue())
                .isEqualTo(
                        SchedulingReportedStats.builder()
                                .setJobId(JOB_ID)
                                .setResultCode(RESULT_CODE)
                                .setSchedulerType(SCHEDULER_TYPE_SPE)
                                .setModuleName(SCHEDULING_LOGGING_UNKNOWN_MODULE_NAME)
                                .build());
    }

    @Test
    public void testShouldLog() {
        when(mMockFlags.getJobSchedulingLoggingSamplingRate()).thenReturn(0);
        expect.that(mSpyJobSchedulingLogger.shouldLog()).isFalse();

        when(mMockFlags.getJobSchedulingLoggingSamplingRate()).thenReturn(100);
        expect.that(mSpyJobSchedulingLogger.shouldLog()).isTrue();
    }

    private NoFailureSyncCallback<Void> syncLogSchedulingStatsHelper() {
        NoFailureSyncCallback<Void> callback = new NoFailureSyncCallback<>();

        doAnswer(
                        invocation -> {
                            callback.injectResult(null);
                            return null;
                        })
                .when(mSpyJobSchedulingLogger)
                .logSchedulingStatsHelper(anyInt(), anyInt(), anyInt());

        return callback;
    }
}

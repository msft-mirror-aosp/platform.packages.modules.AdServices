/*
 * Copyright (C) 2022 The Android Open Source Project
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

package com.android.adservices.service.stats;

import static com.google.common.truth.Truth.assertThat;

import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import android.adservices.common.CallerMetadata;

import org.junit.Before;
import org.junit.Test;
import org.mockito.Mock;
import org.mockito.MockitoAnnotations;

public class ApiServiceLatencyCalculatorTest {
    private static final long BINDER_ELAPSED_TIMESTAMP = 100L;
    private static final CallerMetadata sCallerMetadata =
            new CallerMetadata.Builder()
                    .setBinderElapsedTimestamp(BINDER_ELAPSED_TIMESTAMP)
                    .build();
    private static final long START_ELAPSED_TIMESTAMP = 105L;
    private static final long CURRENT_ELAPSED_TIMESTAMP = 107L;
    private static final long STOP_ELAPSED_TIMESTAMP = 110L;
    private static final int BINDER_LATENCY_MS =
            (int) (2 * (START_ELAPSED_TIMESTAMP - BINDER_ELAPSED_TIMESTAMP));
    private static final int INTERNAL_LATENCY_MS =
            (int) (STOP_ELAPSED_TIMESTAMP - START_ELAPSED_TIMESTAMP);
    private static final int OVERALL_LATENCY_MS = BINDER_LATENCY_MS + INTERNAL_LATENCY_MS;

    @Mock private Clock mMockClock;

    @Before
    public void setUp() {
        MockitoAnnotations.initMocks(this);
    }

    @Test
    public void testApiServiceLatencyCalculator_currentElapsedTimeLatency() {
        when(mMockClock.elapsedRealtime())
                .thenReturn(START_ELAPSED_TIMESTAMP, CURRENT_ELAPSED_TIMESTAMP);
        ApiServiceLatencyCalculator apiServiceLatencyCalculator =
                new ApiServiceLatencyCalculator(sCallerMetadata, mMockClock);
        verify(mMockClock).elapsedRealtime();
        assertThat(apiServiceLatencyCalculator.getApiServiceElapsedLatencyMs())
                .isEqualTo((int) (CURRENT_ELAPSED_TIMESTAMP - START_ELAPSED_TIMESTAMP));
    }

    @Test
    public void testApiServiceLatencyCalculatorStop_calculateOverallLatencyOnce()
            throws InterruptedException {
        when(mMockClock.elapsedRealtime())
                .thenReturn(START_ELAPSED_TIMESTAMP, STOP_ELAPSED_TIMESTAMP);
        ApiServiceLatencyCalculator apiServiceLatencyCalculator =
                new ApiServiceLatencyCalculator(sCallerMetadata, mMockClock);

        int overallLatencyMs1 = apiServiceLatencyCalculator.getApiServiceOverallLatencyMs();
        assertThat(overallLatencyMs1).isEqualTo(OVERALL_LATENCY_MS);
        // Make thread to fall sleep for 5 milliseconds to verify that the overall latencies no
        // longer change once the api service latency calculator was stopped by first time calling
        // {@link ApiServiceLatencyCalculator#.getApiServiceOverallLatencyMs()} or
        // {@link ApiServiceLatencyCalculator#.getApiServiceInternalFinalLatencyMs()}
        Thread.sleep(5L);
        int overallLatencyMs2 = apiServiceLatencyCalculator.getApiServiceOverallLatencyMs();
        assertThat(overallLatencyMs1).isEqualTo(overallLatencyMs2);
    }

    @Test
    public void testApiServiceLatencyCalculatorStop_calculateInternalLatencyOnce()
            throws InterruptedException {
        when(mMockClock.elapsedRealtime())
                .thenReturn(START_ELAPSED_TIMESTAMP, STOP_ELAPSED_TIMESTAMP);
        ApiServiceLatencyCalculator apiServiceLatencyCalculator =
                new ApiServiceLatencyCalculator(sCallerMetadata, mMockClock);

        int internalLatencyMs1 = apiServiceLatencyCalculator.getApiServiceInternalFinalLatencyMs();
        assertThat(internalLatencyMs1).isEqualTo(INTERNAL_LATENCY_MS);
        // Make thread to fall sleep for 5 milliseconds to verify that the overall latencies no
        // longer change once the api service latency calculator was stopped by first time calling
        // {@link ApiServiceLatencyCalculator#.getApiServiceOverallLatencyMs()} or
        // {@link ApiServiceLatencyCalculator#.getApiServiceInternalFinalLatencyMs()}
        Thread.sleep(5L);
        int internalLatencyMs2 = apiServiceLatencyCalculator.getApiServiceInternalFinalLatencyMs();
        assertThat(internalLatencyMs1).isEqualTo(internalLatencyMs2);
    }
}

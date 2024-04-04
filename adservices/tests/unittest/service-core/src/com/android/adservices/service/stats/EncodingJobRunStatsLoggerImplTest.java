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

package com.android.adservices.service.stats;

import static com.google.common.truth.Truth.assertThat;

import static org.mockito.Mockito.verify;

import com.android.adservices.service.stats.pas.EncodingJobRunStats;
import com.android.adservices.service.stats.pas.EncodingJobRunStatsLogger;
import com.android.adservices.service.stats.pas.EncodingJobRunStatsLoggerImpl;

import org.junit.Before;
import org.junit.Test;
import org.mockito.ArgumentCaptor;
import org.mockito.Mock;
import org.mockito.MockitoAnnotations;

public class EncodingJobRunStatsLoggerImplTest {
    @Mock private AdServicesLogger mAdServicesLoggerMock;

    private EncodingJobRunStatsLogger mEncodingJobRunStatsLogger;

    @Before
    public void setUp() {
        MockitoAnnotations.initMocks(this);
        mEncodingJobRunStatsLogger =
                new EncodingJobRunStatsLoggerImpl(
                        mAdServicesLoggerMock, EncodingJobRunStats.builder());
    }

    @Test
    public void testEncodingJobRunStatsLogger_successLogging() {
        ArgumentCaptor<EncodingJobRunStats> argumentCaptor =
                ArgumentCaptor.forClass(EncodingJobRunStats.class);

        mEncodingJobRunStatsLogger.addOneSignalEncodingFailures();
        mEncodingJobRunStatsLogger.addOneSignalEncodingSkips();
        mEncodingJobRunStatsLogger.addOneSignalEncodingFailures();
        mEncodingJobRunStatsLogger.addOneSignalEncodingSkips();
        mEncodingJobRunStatsLogger.addOneSignalEncodingFailures();

        mEncodingJobRunStatsLogger.setSizeOfFilteredBuyerEncodingList(10);

        mEncodingJobRunStatsLogger.logEncodingJobRunStats();

        verify(mAdServicesLoggerMock).logEncodingJobRunStats(argumentCaptor.capture());

        EncodingJobRunStats stats = argumentCaptor.getValue();
        // The count of signal encoding successes is equal to size of filtered buyer encoding list
        // subtract count of signal encoding skips and count of signal encoding failures.
        assertThat(stats.getSignalEncodingSuccesses()).isEqualTo(5);
        // addOneSignalEncodingSkips() was called 2 times.
        assertThat(stats.getSignalEncodingSkips()).isEqualTo(2);
        // addOneSignalEncodingFailures() was called 3 times.
        assertThat(stats.getSignalEncodingFailures()).isEqualTo(3);
    }

    @Test
    public void testEncodingJobRunStatsLogger_emptyLogging() {
        ArgumentCaptor<EncodingJobRunStats> argumentCaptor =
                ArgumentCaptor.forClass(EncodingJobRunStats.class);

        mEncodingJobRunStatsLogger.logEncodingJobRunStats();

        verify(mAdServicesLoggerMock).logEncodingJobRunStats(argumentCaptor.capture());

        EncodingJobRunStats stats = argumentCaptor.getValue();
        assertThat(stats.getSignalEncodingSuccesses()).isEqualTo(0);
        assertThat(stats.getSignalEncodingSkips()).isEqualTo(0);
        assertThat(stats.getSignalEncodingFailures()).isEqualTo(0);
    }
}

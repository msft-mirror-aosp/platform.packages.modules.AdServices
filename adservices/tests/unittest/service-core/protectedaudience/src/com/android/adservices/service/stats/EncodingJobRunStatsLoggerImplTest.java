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

import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;

import com.android.adservices.common.AdServicesExtendedMockitoTestCase;
import com.android.adservices.service.stats.pas.EncodingJobRunStats;
import com.android.adservices.service.stats.pas.EncodingJobRunStatsLogger;
import com.android.adservices.service.stats.pas.EncodingJobRunStatsLoggerImpl;

import org.junit.Before;
import org.junit.Test;
import org.mockito.ArgumentCaptor;
import org.mockito.Mock;

import java.util.List;

public final class EncodingJobRunStatsLoggerImplTest extends AdServicesExtendedMockitoTestCase {
    @Mock private AdServicesLogger mAdServicesLoggerMock;

    private EncodingJobRunStatsLogger mEncodingJobRunStatsLogger;

    @Before
    public void setUp() {
        mEncodingJobRunStatsLogger =
                new EncodingJobRunStatsLoggerImpl(
                        mAdServicesLoggerMock,
                        EncodingJobRunStats.builder(),
                        /* FledgeEnableForcedEncodingAfterSignalsUpdate = */ true);
    }

    @Test
    public void testEncodingJobRunStatsLogger_successLogging() {
        ArgumentCaptor<EncodingJobRunStats> argumentCaptor =
                ArgumentCaptor.forClass(EncodingJobRunStats.class);

        mEncodingJobRunStatsLogger.resetStatsWithEncodingSourceType(
                AdsRelevanceStatusUtils.PAS_ENCODING_SOURCE_TYPE_ENCODING_JOB_SERVICE);
        mEncodingJobRunStatsLogger.addOneSignalEncodingFailures();
        mEncodingJobRunStatsLogger.addOneSignalEncodingSkips();
        mEncodingJobRunStatsLogger.addOneSignalEncodingFailures();
        mEncodingJobRunStatsLogger.addOneSignalEncodingSkips();
        mEncodingJobRunStatsLogger.addOneSignalEncodingFailures();

        mEncodingJobRunStatsLogger.setSizeOfFilteredBuyerEncodingList(10);

        mEncodingJobRunStatsLogger.logEncodingJobRunStats();

        verify(mAdServicesLoggerMock).logEncodingJobRunStats(argumentCaptor.capture());

        EncodingJobRunStats stats = argumentCaptor.getValue();
        expect.withMessage("EncodingJobRunStats")
                .that(stats)
                .isNotNull();
        // The count of signal encoding successes is equal to size of filtered buyer encoding list
        // subtract count of signal encoding skips and count of signal encoding failures.
        expect.withMessage("EncodingJobRunStats.getSignalEncodingSuccesses()")
                .that(stats.getSignalEncodingSuccesses())
                .isEqualTo(5);
        // addOneSignalEncodingSkips() was called 2 times.
        expect.withMessage("EncodingJobRunStats.getSignalEncodingSkips()")
                .that(stats.getSignalEncodingSkips())
                .isEqualTo(2);
        // addOneSignalEncodingFailures() was called 3 times.
        expect.withMessage("EncodingJobRunStats.getSignalEncodingFailures()")
                .that(stats.getSignalEncodingFailures())
                .isEqualTo(3);
        // Pas encoding source type was logged as PAS_ENCODING_SOURCE_TYPE_ENCODING_JOB_SERVICE.
        expect.withMessage("EncodingJobRunStats.getEncodingSourceType()")
                .that(stats.getEncodingSourceType())
                .isEqualTo(AdsRelevanceStatusUtils.PAS_ENCODING_SOURCE_TYPE_ENCODING_JOB_SERVICE);
    }

    @Test
    public void testEncodingJobRunStatsLogger_successLogging_twoTimes() {
        ArgumentCaptor<EncodingJobRunStats> argumentCaptor =
                ArgumentCaptor.forClass(EncodingJobRunStats.class);

        // Start to EncodingJobRunStats first time.
        mEncodingJobRunStatsLogger.resetStatsWithEncodingSourceType(
                AdsRelevanceStatusUtils.PAS_ENCODING_SOURCE_TYPE_ENCODING_JOB_SERVICE);
        mEncodingJobRunStatsLogger.addOneSignalEncodingFailures();
        mEncodingJobRunStatsLogger.addOneSignalEncodingFailures();
        mEncodingJobRunStatsLogger.addOneSignalEncodingSkips();
        mEncodingJobRunStatsLogger.addOneSignalEncodingFailures();

        mEncodingJobRunStatsLogger.setSizeOfFilteredBuyerEncodingList(5);

        mEncodingJobRunStatsLogger.logEncodingJobRunStats();

        // Start to EncodingJobRunStats second time.
        mEncodingJobRunStatsLogger.resetStatsWithEncodingSourceType(
                AdsRelevanceStatusUtils.PAS_ENCODING_SOURCE_TYPE_SERVICE_IMPL);
        mEncodingJobRunStatsLogger.addOneSignalEncodingFailures();
        mEncodingJobRunStatsLogger.addOneSignalEncodingFailures();
        mEncodingJobRunStatsLogger.addOneSignalEncodingSkips();
        mEncodingJobRunStatsLogger.addOneSignalEncodingSkips();

        mEncodingJobRunStatsLogger.setSizeOfFilteredBuyerEncodingList(8);

        mEncodingJobRunStatsLogger.logEncodingJobRunStats();

        // Verify the EncodingJobRunStats was logged 2 times.
        verify(mAdServicesLoggerMock, times(2)).logEncodingJobRunStats(argumentCaptor.capture());

        List<EncodingJobRunStats> stats = argumentCaptor.getAllValues();
        expect.withMessage("List of EncodingJobRunStats captured")
                .that(stats)
                .hasSize(2);

        // Verify the first EncodingJobRunStats was logged correctly.
        EncodingJobRunStats stats1 = stats.get(0);
        expect.withMessage("EncodingJobRunStats1")
                .that(stats1)
                .isNotNull();
        // The count of signal encoding successes is equal to size of filtered buyer encoding list
        // subtract count of signal encoding skips and count of signal encoding failures.
        expect.withMessage("EncodingJobRunStats1.getSignalEncodingSuccesses()")
                .that(stats1.getSignalEncodingSuccesses())
                .isEqualTo(1);
        // addOneSignalEncodingSkips() was called 1 times.
        expect.withMessage("EncodingJobRunStats1.getSignalEncodingSkips()")
                .that(stats1.getSignalEncodingSkips())
                .isEqualTo(1);
        // addOneSignalEncodingFailures() was called 3 times.
        expect.withMessage("EncodingJobRunStats1.getSignalEncodingFailures()")
                .that(stats1.getSignalEncodingFailures())
                .isEqualTo(3);
        // Pas encoding source type was logged as PAS_ENCODING_SOURCE_TYPE_ENCODING_JOB_SERVICE.
        expect.withMessage("EncodingJobRunStats1.getEncodingSourceType()")
                .that(stats1.getEncodingSourceType())
                .isEqualTo(AdsRelevanceStatusUtils.PAS_ENCODING_SOURCE_TYPE_ENCODING_JOB_SERVICE);

        // Verify the second EncodingJobRunStats was logged correctly.
        EncodingJobRunStats stats2 = stats.get(1);
        expect.withMessage("EncodingJobRunStats2")
                .that(stats2)
                .isNotNull();
        // The count of signal encoding successes is equal to size of filtered buyer encoding list
        // subtract count of signal encoding skips and count of signal encoding failures.
        expect.withMessage("EncodingJobRunStats2.getSignalEncodingSuccesses()")
                .that(stats2.getSignalEncodingSuccesses())
                .isEqualTo(4);
        // addOneSignalEncodingSkips() was called 2 times.
        expect.withMessage("EncodingJobRunStats2.getSignalEncodingSkips()")
                .that(stats2.getSignalEncodingSkips())
                .isEqualTo(2);
        // addOneSignalEncodingFailures() was called 2 times.
        expect.withMessage("EncodingJobRunStats2.getSignalEncodingFailures()")
                .that(stats2.getSignalEncodingFailures())
                .isEqualTo(2);
        // Pas encoding source type was logged as PAS_ENCODING_SOURCE_TYPE_SERVICE_IMPL.
        expect.withMessage("EncodingJobRunStats2.getEncodingSourceType()")
                .that(stats2.getEncodingSourceType())
                .isEqualTo(AdsRelevanceStatusUtils.PAS_ENCODING_SOURCE_TYPE_SERVICE_IMPL);
    }

    @Test
    public void testEncodingJobRunStatsLogger_emptyLogging() {
        ArgumentCaptor<EncodingJobRunStats> argumentCaptor =
                ArgumentCaptor.forClass(EncodingJobRunStats.class);

        mEncodingJobRunStatsLogger.logEncodingJobRunStats();

        verify(mAdServicesLoggerMock).logEncodingJobRunStats(argumentCaptor.capture());

        EncodingJobRunStats stats = argumentCaptor.getValue();
        expect.withMessage("EncodingJobRunStats1")
                .that(stats)
                .isNotNull();
        expect.withMessage("EncodingJobRunStats1.getSignalEncodingSuccesses()")
                .that(stats.getSignalEncodingSuccesses())
                .isEqualTo(0);
        expect.withMessage("EncodingJobRunStats1.getSignalEncodingSkips()")
                .that(stats.getSignalEncodingSkips())
                .isEqualTo(0);
        expect.withMessage("EncodingJobRunStats1.getSignalEncodingFailures()")
                .that(stats.getSignalEncodingFailures())
                .isEqualTo(0);
        expect.withMessage("EncodingJobRunStats1.getEncodingSourceType()")
                .that(stats.getEncodingSourceType())
                .isEqualTo(AdsRelevanceStatusUtils.PAS_ENCODING_SOURCE_TYPE_UNSET);
    }
}

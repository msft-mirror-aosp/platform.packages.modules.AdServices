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

import com.android.adservices.common.AdServicesUnitTestCase;

import org.junit.Test;

public class AdFilteringProcessJoinCAReportedStatsTest extends AdServicesUnitTestCase {
    private static final int STATUS_CODE = 0;
    private static final int COUNT_OF_ADS_WITH_KEYS_MUCH_SMALLER_THAN_LIMITATION = 1;
    private static final int COUNT_OF_ADS_WITH_KEYS_SMALLER_THAN_LIMITATION = 2;
    private static final int COUNT_OF_ADS_WITH_KEYS_EQUAL_TO_LIMITATION = 3;
    private static final int COUNT_OF_ADS_WITH_KEYS_LARGER_THAN_LIMITATION = 4;
    private static final int COUNT_OF_ADS_WITH_EMPTY_KEYS = 5;
    private static final int COUNT_OF_ADS_WITH_FILTERS_MUCH_SMALLER_THAN_LIMITATION = 6;
    private static final int COUNT_OF_ADS_WITH_FILTERS_SMALLER_THAN_LIMITATION = 7;
    private static final int COUNT_OF_ADS_WITH_FILTERS_EQUAL_TO_LIMITATION = 8;
    private static final int COUNT_OF_ADS_WITH_FILTERS_LARGER_THAN_LIMITATION = 9;
    private static final int COUNT_OF_ADS_WITH_EMPTY_FILTERS = 10;
    private static final int TOTAL_NUMBER_OF_USED_KEYS = 11;
    private static final int TOTAL_NUMBER_OF_USED_FILTERS = 12;

    @Test
    public void testBuildAdFilteringProcessJoinCAReportedStats() {
        AdFilteringProcessJoinCAReportedStats stats =
                AdFilteringProcessJoinCAReportedStats.builder()
                        .setStatusCode(STATUS_CODE)
                        .setCountOfAdsWithKeysMuchSmallerThanLimitation(
                                COUNT_OF_ADS_WITH_KEYS_MUCH_SMALLER_THAN_LIMITATION)
                        .setCountOfAdsWithKeysSmallerThanLimitation(
                                COUNT_OF_ADS_WITH_KEYS_SMALLER_THAN_LIMITATION)
                        .setCountOfAdsWithKeysEqualToLimitation(
                                COUNT_OF_ADS_WITH_KEYS_EQUAL_TO_LIMITATION)
                        .setCountOfAdsWithKeysLargerThanLimitation(
                                COUNT_OF_ADS_WITH_KEYS_LARGER_THAN_LIMITATION)
                        .setCountOfAdsWithEmptyKeys(COUNT_OF_ADS_WITH_EMPTY_KEYS)
                        .setCountOfAdsWithFiltersMuchSmallerThanLimitation(
                                COUNT_OF_ADS_WITH_FILTERS_MUCH_SMALLER_THAN_LIMITATION)
                        .setCountOfAdsWithFiltersSmallerThanLimitation(
                                COUNT_OF_ADS_WITH_FILTERS_SMALLER_THAN_LIMITATION)
                        .setCountOfAdsWithFiltersEqualToLimitation(
                                COUNT_OF_ADS_WITH_FILTERS_EQUAL_TO_LIMITATION)
                        .setCountOfAdsWithFiltersLargerThanLimitation(
                                COUNT_OF_ADS_WITH_FILTERS_LARGER_THAN_LIMITATION)
                        .setCountOfAdsWithEmptyFilters(COUNT_OF_ADS_WITH_EMPTY_FILTERS)
                        .setTotalNumberOfUsedKeys(TOTAL_NUMBER_OF_USED_KEYS)
                        .setTotalNumberOfUsedFilters(TOTAL_NUMBER_OF_USED_FILTERS)
                        .build();

        expect.that(stats.getStatusCode()).isEqualTo(STATUS_CODE);
        expect.that(stats.getCountOfAdsWithKeysMuchSmallerThanLimitation())
                .isEqualTo(COUNT_OF_ADS_WITH_KEYS_MUCH_SMALLER_THAN_LIMITATION);
        expect.that(stats.getCountOfAdsWithKeysSmallerThanLimitation())
                .isEqualTo(COUNT_OF_ADS_WITH_KEYS_SMALLER_THAN_LIMITATION);
        expect.that(stats.getCountOfAdsWithKeysEqualToLimitation())
                .isEqualTo(COUNT_OF_ADS_WITH_KEYS_EQUAL_TO_LIMITATION);
        expect.that(stats.getCountOfAdsWithKeysLargerThanLimitation())
                .isEqualTo(COUNT_OF_ADS_WITH_KEYS_LARGER_THAN_LIMITATION);
        expect.that(stats.getCountOfAdsWithEmptyKeys()).isEqualTo(COUNT_OF_ADS_WITH_EMPTY_KEYS);
        expect.that(stats.getCountOfAdsWithFiltersMuchSmallerThanLimitation())
                .isEqualTo(COUNT_OF_ADS_WITH_FILTERS_MUCH_SMALLER_THAN_LIMITATION);
        expect.that(stats.getCountOfAdsWithFiltersSmallerThanLimitation())
                .isEqualTo(COUNT_OF_ADS_WITH_FILTERS_SMALLER_THAN_LIMITATION);
        expect.that(stats.getCountOfAdsWithFiltersEqualToLimitation())
                .isEqualTo(COUNT_OF_ADS_WITH_FILTERS_EQUAL_TO_LIMITATION);
        expect.that(stats.getCountOfAdsWithFiltersLargerThanLimitation())
                .isEqualTo(COUNT_OF_ADS_WITH_FILTERS_LARGER_THAN_LIMITATION);
        expect.that(stats.getCountOfAdsWithEmptyFilters())
                .isEqualTo(COUNT_OF_ADS_WITH_EMPTY_FILTERS);
        expect.that(stats.getTotalNumberOfUsedKeys()).isEqualTo(TOTAL_NUMBER_OF_USED_KEYS);
        expect.that(stats.getTotalNumberOfUsedFilters()).isEqualTo(TOTAL_NUMBER_OF_USED_FILTERS);
    }
}

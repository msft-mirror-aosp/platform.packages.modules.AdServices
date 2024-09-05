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

import static com.android.adservices.service.stats.AdsRelevanceStatusUtils.FILTER_PROCESS_TYPE_CUSTOM_AUDIENCES;

import com.android.adservices.common.AdServicesUnitTestCase;

import org.junit.Test;

public class AdFilteringProcessAdSelectionReportedStatsTest extends AdServicesUnitTestCase {
    private static final int LATENCY_IN_MILLIS_OF_ALL_AD_FILTERING = 150;
    private static final int LATENCY_IN_MILLIS_OF_APP_INSTALL_FILTERING = 50;
    private static final int LATENCY_IN_MILLIS_OF_FCAP_FILTERS = 100;
    private static final int STATUS_CODE = 1;
    private static final int NUM_OF_ADS_FILTERED_OUT_OF_BIDDING = 5;
    private static final int NUM_OF_CUSTOM_AUDIENCES_FILTERED_OUT_OF_BIDDING = 2;
    private static final int TOTAL_NUM_OF_ADS_BEFORE_FILTERING = 3;
    private static final int TOTAL_NUM_OF_CUSTOM_AUDIENCES_BEFORE_FILTERING = 5;
    private static final int NUM_OF_PACKAGE_IN_APP_INSTALL_FILTERS = 10;
    private static final int NUM_OF_DB_OPERATIONS = 7;
    private static final int FILTER_PROCESS_TYPE = FILTER_PROCESS_TYPE_CUSTOM_AUDIENCES;
    private static final int NUM_OF_CONTEXTUAL_ADS_FILTERED = 15;
    private static final int NUM_OF_AD_COUNTER_KEYS_IN_FCAP_FILTERS = 20;
    private static final int NUM_OF_CONTEXTUAL_ADS_FILTERED_OUT_OF_BIDDING_INVALID_SIGNATURES = 16;
    private static final int NUM_OF_CONTEXTUAL_ADS_FILTERED_OUT_OF_BIDDING_NO_ADS = 25;
    private static final int TOTAL_NUM_OF_CONTEXTUAL_ADS_BEFORE_FILTERING = 30;

    @Test
    public void testBuildAdFilteringProcessAdSelectionReportedStats() {
        AdFilteringProcessAdSelectionReportedStats stats =
                AdFilteringProcessAdSelectionReportedStats.builder()
                        .setLatencyInMillisOfAllAdFiltering(LATENCY_IN_MILLIS_OF_ALL_AD_FILTERING)
                        .setLatencyInMillisOfAppInstallFiltering(
                                LATENCY_IN_MILLIS_OF_APP_INSTALL_FILTERING)
                        .setLatencyInMillisOfFcapFilters(LATENCY_IN_MILLIS_OF_FCAP_FILTERS)
                        .setStatusCode(STATUS_CODE)
                        .setNumOfAdsFilteredOutOfBidding(NUM_OF_ADS_FILTERED_OUT_OF_BIDDING)
                        .setNumOfCustomAudiencesFilteredOutOfBidding(
                                NUM_OF_CUSTOM_AUDIENCES_FILTERED_OUT_OF_BIDDING)
                        .setTotalNumOfAdsBeforeFiltering(TOTAL_NUM_OF_ADS_BEFORE_FILTERING)
                        .setTotalNumOfCustomAudiencesBeforeFiltering(
                                TOTAL_NUM_OF_CUSTOM_AUDIENCES_BEFORE_FILTERING)
                        .setNumOfPackageInAppInstallFilters(NUM_OF_PACKAGE_IN_APP_INSTALL_FILTERS)
                        .setNumOfDbOperations(NUM_OF_DB_OPERATIONS)
                        .setFilterProcessType(FILTER_PROCESS_TYPE)
                        .setNumOfContextualAdsFiltered(NUM_OF_CONTEXTUAL_ADS_FILTERED)
                        .setNumOfAdCounterKeysInFcapFilters(NUM_OF_AD_COUNTER_KEYS_IN_FCAP_FILTERS)
                        .setNumOfContextualAdsFilteredOutOfBiddingInvalidSignatures(
                                NUM_OF_CONTEXTUAL_ADS_FILTERED_OUT_OF_BIDDING_INVALID_SIGNATURES)
                        .setNumOfContextualAdsFilteredOutOfBiddingNoAds(
                                NUM_OF_CONTEXTUAL_ADS_FILTERED_OUT_OF_BIDDING_NO_ADS)
                        .setTotalNumOfContextualAdsBeforeFiltering(
                                TOTAL_NUM_OF_CONTEXTUAL_ADS_BEFORE_FILTERING)
                        .build();

        expect.that(stats.getLatencyInMillisOfAllAdFiltering())
                .isEqualTo(LATENCY_IN_MILLIS_OF_ALL_AD_FILTERING);
        expect.that(stats.getLatencyInMillisOfAppInstallFiltering())
                .isEqualTo(LATENCY_IN_MILLIS_OF_APP_INSTALL_FILTERING);
        expect.that(stats.getLatencyInMillisOfFcapFilters())
                .isEqualTo(LATENCY_IN_MILLIS_OF_FCAP_FILTERS);
        expect.that(stats.getStatusCode()).isEqualTo(STATUS_CODE);
        expect.that(stats.getNumOfAdsFilteredOutOfBidding())
                .isEqualTo(NUM_OF_ADS_FILTERED_OUT_OF_BIDDING);
        expect.that(stats.getNumOfCustomAudiencesFilteredOutOfBidding())
                .isEqualTo(NUM_OF_CUSTOM_AUDIENCES_FILTERED_OUT_OF_BIDDING);
        expect.that(stats.getTotalNumOfAdsBeforeFiltering())
                .isEqualTo(TOTAL_NUM_OF_ADS_BEFORE_FILTERING);
        expect.that(stats.getTotalNumOfCustomAudiencesBeforeFiltering())
                .isEqualTo(TOTAL_NUM_OF_CUSTOM_AUDIENCES_BEFORE_FILTERING);
        expect.that(stats.getNumOfPackageInAppInstallFilters())
                .isEqualTo(NUM_OF_PACKAGE_IN_APP_INSTALL_FILTERS);
        expect.that(stats.getNumOfDbOperations()).isEqualTo(NUM_OF_DB_OPERATIONS);
        expect.that(stats.getFilterProcessType()).isEqualTo(FILTER_PROCESS_TYPE);
        expect.that(stats.getNumOfContextualAdsFiltered())
                .isEqualTo(NUM_OF_CONTEXTUAL_ADS_FILTERED);
        expect.that(stats.getNumOfAdCounterKeysInFcapFilters())
                .isEqualTo(NUM_OF_AD_COUNTER_KEYS_IN_FCAP_FILTERS);
        expect.that(stats.getNumOfContextualAdsFilteredOutOfBiddingInvalidSignatures())
                .isEqualTo(NUM_OF_CONTEXTUAL_ADS_FILTERED_OUT_OF_BIDDING_INVALID_SIGNATURES);
        expect.that(stats.getNumOfContextualAdsFilteredOutOfBiddingNoAds())
                .isEqualTo(NUM_OF_CONTEXTUAL_ADS_FILTERED_OUT_OF_BIDDING_NO_ADS);
        expect.that(stats.getTotalNumOfContextualAdsBeforeFiltering())
                .isEqualTo(TOTAL_NUM_OF_CONTEXTUAL_ADS_BEFORE_FILTERING);
    }

    @Test
    public void testBuildAdFilteringProcessAdSelectionReportedStatsWithEmptyValues() {
        AdFilteringProcessAdSelectionReportedStats stats =
                AdFilteringProcessAdSelectionReportedStats.builder().build();

        expect.that(stats.getLatencyInMillisOfAllAdFiltering()).isEqualTo(0);
        expect.that(stats.getLatencyInMillisOfAppInstallFiltering()).isEqualTo(0);
        expect.that(stats.getLatencyInMillisOfFcapFilters()).isEqualTo(0);
        expect.that(stats.getStatusCode()).isEqualTo(0);
        expect.that(stats.getNumOfAdsFilteredOutOfBidding()).isEqualTo(0);
        expect.that(stats.getNumOfCustomAudiencesFilteredOutOfBidding()).isEqualTo(0);
        expect.that(stats.getTotalNumOfAdsBeforeFiltering()).isEqualTo(0);
        expect.that(stats.getTotalNumOfCustomAudiencesBeforeFiltering()).isEqualTo(0);
        expect.that(stats.getNumOfPackageInAppInstallFilters()).isEqualTo(0);
        expect.that(stats.getNumOfDbOperations()).isEqualTo(0);
        expect.that(stats.getFilterProcessType()).isEqualTo(0);
        expect.that(stats.getNumOfContextualAdsFiltered()).isEqualTo(0);
        expect.that(stats.getNumOfAdCounterKeysInFcapFilters()).isEqualTo(0);
        expect.that(stats.getNumOfContextualAdsFilteredOutOfBiddingInvalidSignatures())
                .isEqualTo(0);
        expect.that(stats.getNumOfContextualAdsFilteredOutOfBiddingNoAds()).isEqualTo(0);
        expect.that(stats.getTotalNumOfContextualAdsBeforeFiltering()).isEqualTo(0);
    }
}

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

import static com.android.adservices.service.stats.AdServicesLoggerUtil.FIELD_UNSET;
import static com.android.adservices.service.stats.AdsRelevanceStatusUtils.SERVER_AUCTION_COORDINATOR_SOURCE_API;
import static com.android.adservices.service.stats.AdsRelevanceStatusUtils.SERVER_AUCTION_COORDINATOR_SOURCE_UNSET;

import android.adservices.common.AdServicesStatusUtils;

import com.android.adservices.common.AdServicesUnitTestCase;

import org.junit.Test;

public class GetAdSelectionDataApiCalledStatsTest extends AdServicesUnitTestCase {
    private static final int PAYLOAD_SIZE_KB = 64;
    private static final int NUM_BUYERS = 2;
    private static final int SELLER_MAX_SIZE_KB = 32;
    private static final int LATENCY_MS = 3;
    private static final int COMPRESSED_BUYER_CREATOR_VERSION = 1;
    private static final int NUM_RE_ESTIMATIONS = 4;

    @AdsRelevanceStatusUtils.ServerAuctionCoordinatorSource
    private static final int SERVER_AUCTION_COORDINATOR_SOURCE =
            SERVER_AUCTION_COORDINATOR_SOURCE_API;

    @AdServicesStatusUtils.StatusCode
    private static final int STATUS_CODE = AdServicesStatusUtils.STATUS_SUCCESS;

    @Test
    public void testBuildGetAdSelectionDataApiCalledStats() {
        GetAdSelectionDataApiCalledStats stats =
                GetAdSelectionDataApiCalledStats.builder()
                        .setPayloadSizeKb(PAYLOAD_SIZE_KB)
                        .setNumBuyers(NUM_BUYERS)
                        .setStatusCode(STATUS_CODE)
                        .build();

        expect.that(stats.getPayloadSizeKb()).isEqualTo(PAYLOAD_SIZE_KB);
        expect.that(stats.getNumBuyers()).isEqualTo(NUM_BUYERS);
        expect.that(stats.getStatusCode()).isEqualTo(STATUS_CODE);
        expect.that(stats.getServerAuctionCoordinatorSource())
                .isEqualTo(SERVER_AUCTION_COORDINATOR_SOURCE_UNSET);
        expect.that(stats.getSellerMaxSizeKb()).isEqualTo(FIELD_UNSET);
        expect.that(stats.getPayloadOptimizationResult())
                .isEqualTo(
                        GetAdSelectionDataApiCalledStats.PayloadOptimizationResult
                                .PAYLOAD_OPTIMIZATION_RESULT_UNKNOWN);
        expect.that(stats.getInputGenerationLatencyMs()).isEqualTo(FIELD_UNSET);
        expect.that(stats.getCompressedBuyerInputCreatorVersion()).isEqualTo(FIELD_UNSET);
        expect.that(stats.getNumReEstimations()).isEqualTo(FIELD_UNSET);
    }

    @Test
    public void testBuildGetAdSelectionDataApiCalledStatsWithSellerConfigurationMetrics() {
        GetAdSelectionDataApiCalledStats stats =
                GetAdSelectionDataApiCalledStats.builder()
                        .setPayloadSizeKb(PAYLOAD_SIZE_KB)
                        .setNumBuyers(NUM_BUYERS)
                        .setStatusCode(STATUS_CODE)
                        .setSellerMaxSizeKb(SELLER_MAX_SIZE_KB)
                        .setPayloadOptimizationResult(
                                GetAdSelectionDataApiCalledStats.PayloadOptimizationResult
                                        .PAYLOAD_WITHIN_REQUESTED_MAX)
                        .setInputGenerationLatencyMs(LATENCY_MS)
                        .setCompressedBuyerInputCreatorVersion(COMPRESSED_BUYER_CREATOR_VERSION)
                        .setNumReEstimations(NUM_RE_ESTIMATIONS)
                        .build();

        expect.that(stats.getPayloadSizeKb()).isEqualTo(PAYLOAD_SIZE_KB);
        expect.that(stats.getNumBuyers()).isEqualTo(NUM_BUYERS);
        expect.that(stats.getStatusCode()).isEqualTo(STATUS_CODE);
        expect.that(stats.getServerAuctionCoordinatorSource())
                .isEqualTo(SERVER_AUCTION_COORDINATOR_SOURCE_UNSET);
        expect.that(stats.getSellerMaxSizeKb()).isEqualTo(SELLER_MAX_SIZE_KB);
        expect.that(stats.getPayloadOptimizationResult())
                .isEqualTo(
                        GetAdSelectionDataApiCalledStats.PayloadOptimizationResult
                                .PAYLOAD_WITHIN_REQUESTED_MAX);
        expect.that(stats.getInputGenerationLatencyMs()).isEqualTo(LATENCY_MS);
        expect.that(stats.getCompressedBuyerInputCreatorVersion())
                .isEqualTo(COMPRESSED_BUYER_CREATOR_VERSION);
        expect.that(stats.getNumReEstimations()).isEqualTo(NUM_RE_ESTIMATIONS);
    }

    @Test
    public void testBuildGetAdSelectionDataApiCalledStats_WithCoordinatorSource() {
        GetAdSelectionDataApiCalledStats stats =
                GetAdSelectionDataApiCalledStats.builder()
                        .setPayloadSizeKb(PAYLOAD_SIZE_KB)
                        .setNumBuyers(NUM_BUYERS)
                        .setStatusCode(STATUS_CODE)
                        .setServerAuctionCoordinatorSource(SERVER_AUCTION_COORDINATOR_SOURCE)
                        .build();

        expect.that(stats.getPayloadSizeKb()).isEqualTo(PAYLOAD_SIZE_KB);
        expect.that(stats.getNumBuyers()).isEqualTo(NUM_BUYERS);
        expect.that(stats.getStatusCode()).isEqualTo(STATUS_CODE);
        expect.that(stats.getServerAuctionCoordinatorSource())
                .isEqualTo(SERVER_AUCTION_COORDINATOR_SOURCE);
        expect.that(stats.getSellerMaxSizeKb()).isEqualTo(FIELD_UNSET);
        expect.that(stats.getPayloadOptimizationResult())
                .isEqualTo(
                        GetAdSelectionDataApiCalledStats.PayloadOptimizationResult
                                .PAYLOAD_OPTIMIZATION_RESULT_UNKNOWN);
        expect.that(stats.getInputGenerationLatencyMs()).isEqualTo(FIELD_UNSET);
        expect.that(stats.getCompressedBuyerInputCreatorVersion()).isEqualTo(FIELD_UNSET);
        expect.that(stats.getNumReEstimations()).isEqualTo(FIELD_UNSET);
    }
}

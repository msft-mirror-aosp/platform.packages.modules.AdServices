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

import static com.android.adservices.service.stats.AdsRelevanceStatusUtils.SERVER_AUCTION_COORDINATOR_SOURCE_DEFAULT;
import static com.android.adservices.service.stats.AdsRelevanceStatusUtils.SERVER_AUCTION_ENCRYPTION_KEY_SOURCE_NETWORK;
import static com.android.adservices.service.stats.AdsRelevanceStatusUtils.SERVER_AUCTION_KEY_FETCH_SOURCE_BACKGROUND_FETCH;

import com.android.adservices.common.AdServicesUnitTestCase;

import org.junit.Test;

public class ServerAuctionKeyFetchCalledStatsTest extends AdServicesUnitTestCase {
    @AdsRelevanceStatusUtils.ServerAuctionKeyFetchSource
    private static final int SOURCE = SERVER_AUCTION_KEY_FETCH_SOURCE_BACKGROUND_FETCH;

    @AdsRelevanceStatusUtils.ServerAuctionEncryptionKeySource
    private static final int ENCRYPTION_KEY_SOURCE = SERVER_AUCTION_ENCRYPTION_KEY_SOURCE_NETWORK;

    @AdsRelevanceStatusUtils.ServerAuctionCoordinatorSource
    private static final int COORDINATOR_SOURCE = SERVER_AUCTION_COORDINATOR_SOURCE_DEFAULT;

    private static final int NETWORK_STATUS_CODE = 200;

    private static final int NETWORK_LATENCY_MILLIS = 123;

    @Test
    public void testBuildServerAuctionKeyFetchCalledStats() {
        ServerAuctionKeyFetchCalledStats stats =
                ServerAuctionKeyFetchCalledStats.builder()
                        .setSource(SOURCE)
                        .setEncryptionKeySource(ENCRYPTION_KEY_SOURCE)
                        .setCoordinatorSource(COORDINATOR_SOURCE)
                        .setNetworkStatusCode(NETWORK_STATUS_CODE)
                        .setNetworkLatencyMillis(NETWORK_LATENCY_MILLIS)
                        .build();

        expect.that(stats.getSource()).isEqualTo(SOURCE);
        expect.that(stats.getEncryptionKeySource()).isEqualTo(ENCRYPTION_KEY_SOURCE);
        expect.that(stats.getCoordinatorSource()).isEqualTo(COORDINATOR_SOURCE);
        expect.that(stats.getNetworkStatusCode()).isEqualTo(NETWORK_STATUS_CODE);
        expect.that(stats.getNetworkLatencyMillis()).isEqualTo(NETWORK_LATENCY_MILLIS);
    }
}

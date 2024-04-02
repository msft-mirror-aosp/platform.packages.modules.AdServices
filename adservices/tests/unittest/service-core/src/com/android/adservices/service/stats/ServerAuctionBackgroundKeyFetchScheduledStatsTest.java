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

import static com.android.adservices.service.stats.AdsRelevanceStatusUtils.BACKGROUND_KEY_FETCH_STATUS_REFRESH_KEYS_INITIATED;

import com.android.adservices.common.AdServicesUnitTestCase;

import org.junit.Test;

public class ServerAuctionBackgroundKeyFetchScheduledStatsTest extends AdServicesUnitTestCase {
    @AdsRelevanceStatusUtils.BackgroundKeyFetchStatus
    private static final int BACKGROUND_KEY_FETCH_STATUS =
            BACKGROUND_KEY_FETCH_STATUS_REFRESH_KEYS_INITIATED;

    private static final int COUNT_AUCTION_URLS = 2;
    private static final int COUNT_JOIN_URLS = 3;

    @Test
    public void testBuildServerAuctionBackgroundKeyFetchScheduledStats() {
        ServerAuctionBackgroundKeyFetchScheduledStats stats =
                ServerAuctionBackgroundKeyFetchScheduledStats.builder()
                        .setStatus(BACKGROUND_KEY_FETCH_STATUS)
                        .setCountAuctionUrls(COUNT_AUCTION_URLS)
                        .setCountJoinUrls(COUNT_JOIN_URLS)
                        .build();

        expect.that(stats.getStatus()).isEqualTo(BACKGROUND_KEY_FETCH_STATUS);
        expect.that(stats.getCountAuctionUrls()).isEqualTo(COUNT_AUCTION_URLS);
        expect.that(stats.getCountJoinUrls()).isEqualTo(COUNT_JOIN_URLS);
    }
}

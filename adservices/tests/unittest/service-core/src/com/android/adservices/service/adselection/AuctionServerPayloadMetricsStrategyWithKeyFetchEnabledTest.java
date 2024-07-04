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

package com.android.adservices.service.adselection;

import static com.android.adservices.service.stats.AdsRelevanceStatusUtils.SERVER_AUCTION_COORDINATOR_SOURCE_API;

import static org.mockito.Mockito.verify;

import com.android.adservices.service.stats.AdServicesLogger;
import com.android.adservices.service.stats.GetAdSelectionDataApiCalledStats;

import org.junit.Before;
import org.junit.Test;
import org.mockito.Mock;
import org.mockito.Mockito;
import org.mockito.MockitoAnnotations;
import org.mockito.Spy;

public class AuctionServerPayloadMetricsStrategyWithKeyFetchEnabledTest {
    @Spy private GetAdSelectionDataApiCalledStats.Builder mBuilder;
    @Mock private AdServicesLogger mAdServicesLoggerMock;
    private AuctionServerPayloadMetricsStrategy mAuctionServerPayloadMetricsStrategy;

    @Before
    public void setup() {
        MockitoAnnotations.initMocks(this);
        mBuilder = Mockito.spy(GetAdSelectionDataApiCalledStats.builder());
        mAuctionServerPayloadMetricsStrategy =
                new AuctionServerPayloadMetricsStrategyWithKeyFetchEnabled(
                        mAdServicesLoggerMock, new SellerConfigurationMetricsStrategyDisabled());
    }

    @Test
    public void testSetServerAuctionCoordinatorSourceSetsCoordinatorSource() {
        mAuctionServerPayloadMetricsStrategy.setServerAuctionCoordinatorSource(
                mBuilder, SERVER_AUCTION_COORDINATOR_SOURCE_API);
        verify(mBuilder).setServerAuctionCoordinatorSource(SERVER_AUCTION_COORDINATOR_SOURCE_API);
    }
}

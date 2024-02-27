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

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.verify;

import android.adservices.common.AdServicesStatusUtils;

import com.android.adservices.service.stats.AdServicesLogger;
import com.android.adservices.service.stats.GetAdSelectionDataApiCalledStats;

import org.junit.Before;
import org.junit.Test;
import org.mockito.Mock;
import org.mockito.Mockito;
import org.mockito.MockitoAnnotations;
import org.mockito.Spy;

public class AuctionServerPayloadMetricsStrategyEnabledTest {
    @Spy private GetAdSelectionDataApiCalledStats.Builder mBuilder;
    @Mock private AdServicesLogger mAdServicesLoggerMock;
    private AuctionServerPayloadMetricsStrategy mAuctionServerPayloadMetricsStrategy;

    @Before
    public void setup() {
        MockitoAnnotations.initMocks(this);
        mBuilder = Mockito.spy(GetAdSelectionDataApiCalledStats.builder());
        mAuctionServerPayloadMetricsStrategy =
                new AuctionServerPayloadMetricsStrategyEnabled(mAdServicesLoggerMock);
    }

    @Test
    public void testSetNumBuyersSetsNumBuyers() {
        mAuctionServerPayloadMetricsStrategy.setNumBuyers(mBuilder, 2);
        verify(mBuilder).setNumBuyers(2);
    }

    @Test
    public void testLogGetAdSelectionDataApiCalledStatsDoesLog() {
        int payloadSize = 2000;
        mAuctionServerPayloadMetricsStrategy.logGetAdSelectionDataApiCalledStats(
                mBuilder.setNumBuyers(2), payloadSize, AdServicesStatusUtils.STATUS_SUCCESS);
        verify(mBuilder).setStatusCode(AdServicesStatusUtils.STATUS_SUCCESS);
        verify(mBuilder).setPayloadSizeKb(payloadSize);

        verify(mAdServicesLoggerMock).logGetAdSelectionDataApiCalledStats(any());
    }
}

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

import android.adservices.common.AdsRelevanceStatusUtils;

import com.android.adservices.service.stats.AdServicesLogger;
import com.android.adservices.service.stats.GetAdSelectionDataApiCalledStats;

public class AuctionServerPayloadMetricsStrategyWithKeyFetchEnabled
        extends AuctionServerPayloadMetricsStrategyEnabled {
    /** Constructs a {@link AuctionServerPayloadMetricsStrategyWithKeyFetchEnabled} instance. */
    public AuctionServerPayloadMetricsStrategyWithKeyFetchEnabled(
            AdServicesLogger adServicesLogger) {
        super(adServicesLogger);
    }

    @Override
    public void setServerAuctionCoordinatorSource(
            GetAdSelectionDataApiCalledStats.Builder builder,
            @AdsRelevanceStatusUtils.ServerAuctionCoordinatorSource int coordinatorSource) {
        builder.setServerAuctionCoordinatorSource(coordinatorSource);
    }
}

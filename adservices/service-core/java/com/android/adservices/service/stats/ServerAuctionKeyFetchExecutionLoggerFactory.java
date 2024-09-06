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

import android.annotation.NonNull;

import com.android.adservices.service.Flags;
import com.android.adservices.service.common.BinderFlagReader;
import com.android.adservices.shared.util.Clock;

import java.util.Objects;

public class ServerAuctionKeyFetchExecutionLoggerFactory {
    private final Clock mClock;

    private final AdServicesLogger mAdServicesLogger;

    private final boolean mFledgeAuctionServerKeyFetchMetricsEnabled;

    public ServerAuctionKeyFetchExecutionLoggerFactory(
            @NonNull Clock clock,
            @NonNull AdServicesLogger adServicesLogger,
            @NonNull Flags flags) {
        Objects.requireNonNull(clock);
        Objects.requireNonNull(adServicesLogger);
        Objects.requireNonNull(flags);
        mFledgeAuctionServerKeyFetchMetricsEnabled =
                BinderFlagReader.readFlag(flags::getFledgeAuctionServerKeyFetchMetricsEnabled);
        mClock = clock;
        mAdServicesLogger = adServicesLogger;
    }

    /**
     * Gets the {@link FetchProcessLogger} implementation to use, dependent on whether the Fledge
     * Auction Server Key Fetch Metrics are enabled.
     *
     * @return an {@link FetchProcessLogger} instance if the Fledge Auction Server Key Fetch metrics
     *     are enabled, or {@link FetchProcessLoggerNoLoggingImpl} instance otherwise
     */
    public FetchProcessLogger getAdsRelevanceExecutionLogger() {
        if (mFledgeAuctionServerKeyFetchMetricsEnabled) {
            return new ServerAuctionKeyFetchExecutionLoggerImpl(mClock, mAdServicesLogger);
        }
        return new FetchProcessLoggerNoLoggingImpl();
    }
}

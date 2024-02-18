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

import static com.android.adservices.service.stats.AdServicesStatsLog.AD_SERVICES_API_CALLED__API_NAME__GET_AD_SELECTION_DATA;
import static com.android.adservices.service.stats.AdServicesStatsLog.AD_SERVICES_API_CALLED__API_NAME__PERSIST_AD_SELECTION_RESULT;

import android.adservices.common.CallerMetadata;
import android.annotation.NonNull;

import com.android.adservices.service.Flags;
import com.android.adservices.service.common.BinderFlagReader;
import com.android.adservices.shared.util.Clock;
import com.android.internal.annotations.VisibleForTesting;

import java.util.Objects;

public class FledgeAuctionServerExecutionLoggerFactory {

    @VisibleForTesting
    static final String GET_AD_SELECTION_DATA_API_NAME = "GET_AD_SELECTION_DATA";

    @VisibleForTesting
    static final String PERSIST_AD_SELECTION_RESULT_API_NAME = "PERSIST_AD_SELECTION_RESULT";

    @VisibleForTesting
    static final String UNKNOWN_API_NAME = "UNKNOWN_API_NAME";

    private final String mCallerAppPackageName;

    private final CallerMetadata mCallerMetadata;

    private final Clock mClock;

    private final AdServicesLogger mAdServicesLogger;

    private final int mApiNameCode;

    private String mApiName;

    private final boolean mFledgeAuctionServerApiUsageMetricsEnabled;

    public FledgeAuctionServerExecutionLoggerFactory(
            @NonNull String callerAppPackageName,
            @NonNull CallerMetadata callerMetadata,
            @NonNull Clock clock,
            @NonNull AdServicesLogger adServicesLogger,
            @NonNull Flags flags,
            int apiNameCode) {
        Objects.requireNonNull(callerAppPackageName);
        Objects.requireNonNull(callerMetadata);
        Objects.requireNonNull(clock);
        Objects.requireNonNull(adServicesLogger);
        Objects.requireNonNull(flags);
        mCallerAppPackageName = callerAppPackageName;
        mFledgeAuctionServerApiUsageMetricsEnabled =
                BinderFlagReader.readFlag(flags::getFledgeAuctionServerApiUsageMetricsEnabled);
        mCallerMetadata = callerMetadata;
        mClock = clock;
        mAdServicesLogger = adServicesLogger;
        mApiNameCode = apiNameCode;
        if (apiNameCode == AD_SERVICES_API_CALLED__API_NAME__PERSIST_AD_SELECTION_RESULT) {
            mApiName = PERSIST_AD_SELECTION_RESULT_API_NAME;
        } else if (apiNameCode == AD_SERVICES_API_CALLED__API_NAME__GET_AD_SELECTION_DATA) {
            mApiName = GET_AD_SELECTION_DATA_API_NAME;
        } else {
            mApiName = UNKNOWN_API_NAME;
        }
    }

    /**
     * Gets the {@link FledgeAuctionServerExecutionLogger} implementation to use,
     * dependent on whether the fledge auction server metrics is enabled.
     *
     * @return an {@link FledgeAuctionServerExecutionLoggerImpl} instance if the fledge auction
     *      server metrics is enabled, or {@link FledgeAuctionServerExecutionLoggerNoLoggingImpl}
     *      instance otherwise
     */
    public FledgeAuctionServerExecutionLogger getFledgeAuctionServerExecutionLogger() {
        if (mFledgeAuctionServerApiUsageMetricsEnabled) {
            return new FledgeAuctionServerExecutionLoggerImpl(
                    mCallerAppPackageName,
                    mCallerMetadata,
                    mClock,
                    mAdServicesLogger,
                    mApiName,
                    mApiNameCode);
        } else {
            return new FledgeAuctionServerExecutionLoggerNoLoggingImpl(mApiName);
        }
    }
}

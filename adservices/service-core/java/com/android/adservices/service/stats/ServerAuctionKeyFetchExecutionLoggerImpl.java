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
import static com.android.adservices.service.stats.AdsRelevanceStatusUtils.SERVER_AUCTION_ENCRYPTION_KEY_SOURCE_DATABASE;
import static com.android.adservices.service.stats.AdsRelevanceStatusUtils.SERVER_AUCTION_ENCRYPTION_KEY_SOURCE_NETWORK;
import static com.android.adservices.service.stats.AdsRelevanceStatusUtils.SERVER_AUCTION_ENCRYPTION_KEY_SOURCE_UNSET;

import android.annotation.NonNull;

import com.android.adservices.LoggerFactory;
import com.android.adservices.shared.util.Clock;
import com.android.internal.annotations.VisibleForTesting;

import java.util.Objects;

/**
 * Class for logging the server auction key fetch metrics. It provides the functions to collect and
 * log the corresponding key fetch process and log the data into the statsd logs. This class collect
 * data for the telemetry atoms:
 *
 * <ul>
 *   <li>ServerAuctionKeyFetchCalledStats for API calls
 *   <li>ServerAuctionKeyFetchCalledStats for background fetch
 * </ul>
 *
 * <p>Each complete process should start the stopwatch immediately on construction this Logger
 * object, and call its corresponding end method to record its states and log the generated atom
 * proto into the statsd logger.
 */
public class ServerAuctionKeyFetchExecutionLoggerImpl implements FetchProcessLogger {
    private static final LoggerFactory.Logger sLogger = LoggerFactory.getFledgeLogger();

    @VisibleForTesting
    static final String ILLEGAL_ENCRYPTION_KEY_SOURCE =
            "The logger's encryption key source doesn't match the process: ";

    @VisibleForTesting
    static final String MISSING_SERVER_AUCTION_KEY_FETCH_PROCESS =
            "The logger should set the start of server auction key fetch process: ";

    @VisibleForTesting
    static final String REPEATED_SERVER_AUCTION_KEY_FETCH_PROCESS =
            "The logger has already set the end of server auction key fetch process: ";

    private long mRunKeyFetchStartTimestamp;
    private long mRunKeyFetchEndTimestamp;
    private boolean mIsLatencyAvailable;
    @AdsRelevanceStatusUtils.ServerAuctionKeyFetchSource private int mSource;
    @AdsRelevanceStatusUtils.ServerAuctionEncryptionKeySource private int mEncryptionKeySource;
    @AdsRelevanceStatusUtils.ServerAuctionCoordinatorSource private int mCoordinatorSource;
    private final Clock mClock;
    private final AdServicesLogger mAdServicesLogger;

    public ServerAuctionKeyFetchExecutionLoggerImpl(
            @NonNull Clock clock, @NonNull AdServicesLogger adServicesLogger) {
        Objects.requireNonNull(clock);
        Objects.requireNonNull(adServicesLogger);
        mClock = clock;
        mAdServicesLogger = adServicesLogger;
        mIsLatencyAvailable = true;
        sLogger.v("ServerAuctionKeyFetchExecutionLogger starts.");
    }

    /** Start the background fetch process. */
    @Override
    public void startNetworkCallTimestamp() {
        sLogger.v("Start logging the network key fetch process.");
        this.mRunKeyFetchStartTimestamp = mClock.elapsedRealtime();
    }

    /** Close and log the key fetch process into AdServicesLogger once the network call finished. */
    @Override
    public void logServerAuctionKeyFetchCalledStatsFromDatabase() {
        sLogger.v("Close BackgroundFetchExecutionLogger for database source.");
        if (mEncryptionKeySource != SERVER_AUCTION_ENCRYPTION_KEY_SOURCE_DATABASE) {
            sLogger.e(ILLEGAL_ENCRYPTION_KEY_SOURCE);
            mEncryptionKeySource = SERVER_AUCTION_ENCRYPTION_KEY_SOURCE_UNSET;
        }
        sLogger.v("KeyFetch process has been logged into AdServicesLogger.");
        mAdServicesLogger.logServerAuctionKeyFetchCalledStats(
                ServerAuctionKeyFetchCalledStats.builder()
                        .setSource(mSource)
                        .setEncryptionKeySource(mEncryptionKeySource)
                        .setCoordinatorSource(mCoordinatorSource)
                        .setNetworkStatusCode(FIELD_UNSET)
                        .setNetworkLatencyMillis(FIELD_UNSET)
                        .build());
    }

    /** Close and log the key fetch process into AdServicesLogger once the network call finished. */
    @Override
    public void logServerAuctionKeyFetchCalledStatsFromNetwork(int networkStatusCode) {
        sLogger.v("Close BackgroundFetchExecutionLogger for network source.");
        if (mEncryptionKeySource != SERVER_AUCTION_ENCRYPTION_KEY_SOURCE_NETWORK) {
            sLogger.e(ILLEGAL_ENCRYPTION_KEY_SOURCE);
            mEncryptionKeySource = SERVER_AUCTION_ENCRYPTION_KEY_SOURCE_UNSET;
        }
        if (mRunKeyFetchStartTimestamp == 0L) {
            sLogger.e(MISSING_SERVER_AUCTION_KEY_FETCH_PROCESS);
            mIsLatencyAvailable = false;
        }
        if (mRunKeyFetchEndTimestamp > 0L) {
            sLogger.e(REPEATED_SERVER_AUCTION_KEY_FETCH_PROCESS);
            mIsLatencyAvailable = false;
        }
        mRunKeyFetchEndTimestamp = mClock.elapsedRealtime();
        int runKeyFetchLatencyInMs =
                mIsLatencyAvailable
                        ? (int) (mRunKeyFetchEndTimestamp - mRunKeyFetchStartTimestamp)
                        : FIELD_UNSET;

        sLogger.v("KeyFetch process has been logged into AdServicesLogger.");
        mAdServicesLogger.logServerAuctionKeyFetchCalledStats(
                ServerAuctionKeyFetchCalledStats.builder()
                        .setSource(mSource)
                        .setEncryptionKeySource(mEncryptionKeySource)
                        .setCoordinatorSource(mCoordinatorSource)
                        .setNetworkStatusCode(networkStatusCode)
                        .setNetworkLatencyMillis(runKeyFetchLatencyInMs)
                        .build());
    }

    /** Sets the source of key fetch (e.g., during auction, background fetch) */
    @Override
    public void setSource(@AdsRelevanceStatusUtils.ServerAuctionKeyFetchSource int source) {
        mSource = source;
    }

    /** Sets whether the key was fetched over the network or the database */
    @Override
    public void setEncryptionKeySource(
            @AdsRelevanceStatusUtils.ServerAuctionEncryptionKeySource int encryptionKeySource) {
        mEncryptionKeySource = encryptionKeySource;
    }

    /** Sets whether we used the default coordinator or adtech-provided coordinator via API call */
    @Override
    public void setCoordinatorSource(
            @AdsRelevanceStatusUtils.ServerAuctionCoordinatorSource int coordinatorSource) {
        mCoordinatorSource = coordinatorSource;
    }

    @VisibleForTesting
    void setKeyFetchStartTimestamp(long timestamp) {
        mRunKeyFetchStartTimestamp = timestamp;
    }

    @VisibleForTesting
    void setKeyFetchEndTimestamp(long timestamp) {
        mRunKeyFetchEndTimestamp = timestamp;
    }
}

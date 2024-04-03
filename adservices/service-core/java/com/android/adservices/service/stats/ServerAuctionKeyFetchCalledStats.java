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

import static com.android.adservices.service.stats.AdsRelevanceStatusUtils.SERVER_AUCTION_COORDINATOR_SOURCE_UNSET;
import static com.android.adservices.service.stats.AdsRelevanceStatusUtils.SERVER_AUCTION_ENCRYPTION_KEY_SOURCE_UNSET;
import static com.android.adservices.service.stats.AdsRelevanceStatusUtils.SERVER_AUCTION_KEY_FETCH_SOURCE_UNSET;

import com.google.auto.value.AutoValue;

/** Class for Server Auction Key Fetch called stats */
@AutoValue
public abstract class ServerAuctionKeyFetchCalledStats {

    /** The source of key fetch (e.g., during auction, background fetch) */
    @AdsRelevanceStatusUtils.ServerAuctionKeyFetchSource
    public abstract int getSource();

    /** Specifies whether the key was fetched over the network or the database */
    @AdsRelevanceStatusUtils.ServerAuctionEncryptionKeySource
    public abstract int getEncryptionKeySource();

    /** Whether we used the default coordinator or adtech-provided coordinator via API call */
    @AdsRelevanceStatusUtils.ServerAuctionCoordinatorSource
    public abstract int getCoordinatorSource();

    /** The status code of the network call */
    public abstract int getNetworkStatusCode();

    /** The latency of network key fetch (in milliseconds) */
    public abstract int getNetworkLatencyMillis();

    /** Returns a generic builder. */
    public static Builder builder() {
        return new AutoValue_ServerAuctionKeyFetchCalledStats.Builder()
                .setSource(SERVER_AUCTION_KEY_FETCH_SOURCE_UNSET)
                .setEncryptionKeySource(SERVER_AUCTION_ENCRYPTION_KEY_SOURCE_UNSET)
                .setCoordinatorSource(SERVER_AUCTION_COORDINATOR_SOURCE_UNSET);
    }

    /** Builder class for ServerAuctionKeyFetchCalledStats. */
    @AutoValue.Builder
    public abstract static class Builder {
        /** Sets the source of key fetch (e.g., during auction, background fetch) */
        public abstract Builder setSource(
                @AdsRelevanceStatusUtils.ServerAuctionKeyFetchSource int source);

        /** Sets whether the key was fetched over the network or the database */
        public abstract Builder setEncryptionKeySource(
                @AdsRelevanceStatusUtils.ServerAuctionEncryptionKeySource int encryptionKeySource);

        /**
         * Sets whether we used the default coordinator or adtech-provided coordinator via API call
         */
        public abstract Builder setCoordinatorSource(
                @AdsRelevanceStatusUtils.ServerAuctionCoordinatorSource int coordinatorSource);

        /** Sets the status code of the network call */
        public abstract Builder setNetworkStatusCode(int networkStatusCode);

        /** Sets the latency of network key fetch (in milliseconds) */
        public abstract Builder setNetworkLatencyMillis(int networkLatencyMillis);

        /** Builds the {@link ServerAuctionKeyFetchCalledStats} object. */
        public abstract ServerAuctionKeyFetchCalledStats build();
    }
}

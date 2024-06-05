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

import com.android.adservices.service.stats.pas.EncodingFetchStats;

public interface FetchProcessLogger {
    /** Invokes the logger to log {@link EncodingFetchStats}. */
    default void logEncodingJsFetchStats(
            @AdsRelevanceStatusUtils.EncodingFetchStatus int jsFetchStatus) {
        // do nothing
    }

    /** Sets the AdTech's eTLD+1 ID. */
    default void setAdTechId(String adTechId) {
        // do nothing
    }

    /** Sets the timestamp to start download the js. */
    default void setJsDownloadStartTimestamp(long jsDownloadStartTimestamp) {
        // do nothing
    }

    /** Invokes the logger to log {@link ServerAuctionKeyFetchCalledStats} for database. */
    default void logServerAuctionKeyFetchCalledStatsFromDatabase() {
        // do nothing
    }

    /** Invokes the logger to log {@link ServerAuctionKeyFetchCalledStats} for network call. */
    default void logServerAuctionKeyFetchCalledStatsFromNetwork(int networkCode) {
        // do nothing
    }

    /** Sets the timestamp to start network call. */
    default void startNetworkCallTimestamp() {
        // do nothing
    }

    /** Sets the key fetch source */
    default void setSource(@AdsRelevanceStatusUtils.ServerAuctionKeyFetchSource int source) {
        // do nothing
    }

    /** Sets the encryption key source */
    default void setEncryptionKeySource(
            @AdsRelevanceStatusUtils.ServerAuctionEncryptionKeySource int encryptionKeySource) {
        // do nothing
    }

    /** Sets the coordinator source */
    default void setCoordinatorSource(
            @AdsRelevanceStatusUtils.ServerAuctionCoordinatorSource int coordinatorSource) {
        // do nothing
    }
}

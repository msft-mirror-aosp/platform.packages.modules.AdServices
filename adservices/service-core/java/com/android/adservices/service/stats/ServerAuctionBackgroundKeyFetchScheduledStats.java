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

import static com.android.adservices.service.stats.AdsRelevanceStatusUtils.BACKGROUND_KEY_FETCH_STATUS_UNSET;

import com.google.auto.value.AutoValue;

/** Class for Server Auction Background Key Fetch scheduled stats */
@AutoValue
public abstract class ServerAuctionBackgroundKeyFetchScheduledStats {
    /** The status of the Server Auction Background Key Fetch scheduled in AdServices */
    @AdsRelevanceStatusUtils.BackgroundKeyFetchStatus
    public abstract int getStatus();

    /** The count of auction urls */
    public abstract int getCountAuctionUrls();

    /** The count of join urls */
    public abstract int getCountJoinUrls();

    /** Returns a generic builder. */
    public static Builder builder() {
        return new AutoValue_ServerAuctionBackgroundKeyFetchScheduledStats.Builder()
                .setStatus(BACKGROUND_KEY_FETCH_STATUS_UNSET);
    }

    /** Builder class for ServerAuctionBackgroundKeyFetchScheduledStats. */
    @AutoValue.Builder
    public abstract static class Builder {
        /** Sets the status of the Server Auction Background Key Fetch scheduled in AdServices */
        public abstract Builder setStatus(
                @AdsRelevanceStatusUtils.BackgroundKeyFetchStatus int status);

        /** Sets the count of auction urls */
        public abstract Builder setCountAuctionUrls(int countAuctionUrls);

        /** Sets the join of auction urls */
        public abstract Builder setCountJoinUrls(int countJoinUrls);

        /** Builds the {@link ServerAuctionBackgroundKeyFetchScheduledStats} object. */
        public abstract ServerAuctionBackgroundKeyFetchScheduledStats build();
    }
}

/*
 * Copyright (C) 2023 The Android Open Source Project
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

import com.android.adservices.data.customaudience.DBCustomAudience;

import com.google.common.base.Strings;

/** Class to filter CustomAudience for server-side auctions. */
public class AuctionServerCustomAudienceFilterer {
    /** Returns true if the given customAudience is valid for server side auction. */
    public static boolean isValidCustomAudienceForServerSideAuction(
            DBCustomAudience dbCustomAudience) {
        if (dbCustomAudience.getTrustedBiddingData() == null) {
            // Trusted bidding keys in trusted bidding data is required for server auction.
            return false;
        }

        if (dbCustomAudience.getTrustedBiddingData().getKeys().stream()
                .allMatch(key -> Strings.isNullOrEmpty(key))) {
            // At least one trusted bidding key should be non-empty string.
            return false;
        }

        if (dbCustomAudience.getAds().stream()
                .allMatch(ad -> Strings.isNullOrEmpty(ad.getAdRenderId()))) {
            // At least one ad render id should be non-null and non-empty.
            return false;
        }
        return true;
    }
}

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

public class AdFilteringLoggerNoOp implements AdFilteringLogger {
    @Override
    public void setStatusCode(int statusCode) {}

    @Override
    public void setAdFilteringStartTimestamp() {}

    @Override
    public void setAdFilteringEndTimestamp() {}

    @Override
    public void setAppInstallStartTimestamp() {}

    @Override
    public void setAppInstallEndTimestamp() {}

    @Override
    public void setFrequencyCapStartTimestamp() {}

    @Override
    public void setFrequencyCapEndTimestamp() {}

    @Override
    public void setTotalNumOfAdsBeforeFiltering(int totalNumOfAdsBeforeFiltering) {}

    @Override
    public void setNumOfAdsFilteredOutOfBidding(int numOfAdsFilteredOutOfBidding) {}

    @Override
    public void setTotalNumOfCustomAudiencesBeforeFiltering(
            int totalNumOfCustomAudiencesBeforeFiltering) {}

    @Override
    public void setNumOfCustomAudiencesFilteredOutOfBidding(
            int numOfCustomAudiencesFilteredOutOfBidding) {}

    @Override
    public void setTotalNumOfContextualAdsBeforeFiltering(
            int totalNumOfContextualAdsBeforeFiltering) {}

    @Override
    public void setNumOfContextualAdsFiltered(int numOfContextualAdsFiltered) {}

    @Override
    public void setNumOfContextualAdsFilteredOutOfBiddingInvalidSignatures(
            int numOfContextualAdsFilteredOutOfBiddingInvalidSignatures) {}

    @Override
    public void setNumOfContextualAdsFilteredOutOfBiddingNoAds(
            int numOfContextualAdsFilteredOutOfBiddingNoAds) {}

    @Override
    public void setNumOfAdCounterKeysInFcapFilters(int numOfAdCounterKeysInFcapFilters) {}

    @Override
    public void setNumOfPackagesInAppInstallFilters(int numOfPackagesInAppInstallFilters) {}

    @Override
    public void setNumOfDbOperations(int numOfDbOperations) {}

    @Override
    public void close() {}
}

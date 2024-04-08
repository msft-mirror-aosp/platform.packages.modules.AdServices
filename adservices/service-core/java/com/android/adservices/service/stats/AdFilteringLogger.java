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

/**
 * Logger interface for ad filtering operation in {@link
 * com.android.adservices.service.adselection.AdSelectionRunner} and {@link
 * com.android.adservices.service.adselection.OnDeviceAdSelectionRunner}.
 */
public interface AdFilteringLogger {
    /** Sets the status code */
    void setStatusCode(int statusCode);

    /** Sets the start timestamp for filtering */
    void setAdFilteringStartTimestamp();

    /** Sets the end timestamp for filtering */
    void setAdFilteringEndTimestamp();

    /** Sets the start timestamp for app install filtering */
    void setAppInstallStartTimestamp();

    /** Sets the end timestamp for app install */
    void setAppInstallEndTimestamp();

    /** Sets the start timestamp for fcap */
    void setFrequencyCapStartTimestamp();

    /** Sets the end timestamp for fcap */
    void setFrequencyCapEndTimestamp();

    /** Sets the number of ads before filtering */
    void setTotalNumOfAdsBeforeFiltering(int totalNumOfAdsBeforeFiltering);

    /** Sets the number of ads filtered out of bidding */
    void setNumOfAdsFilteredOutOfBidding(int numOfAdsFilteredOutOfBidding);

    /** Sets the total number of custom audience before filtering */
    void setTotalNumOfCustomAudiencesBeforeFiltering(int totalNumOfCustomAudiencesBeforeFiltering);

    /** Sets the number of custom audiences filtered out of bidding */
    void setNumOfCustomAudiencesFilteredOutOfBidding(int numOfCustomAudiencesFilteredOutOfBidding);

    /** Sets the number of contextual ads before filtering */
    void setTotalNumOfContextualAdsBeforeFiltering(int totalNumOfContextualAdsBeforeFiltering);

    /** Sets the number of contextual ads filtered */
    void setNumOfContextualAdsFiltered(int numOfContextualAdsFiltered);

    /** Sets the number of contextual ads filtered out because they have invalid signatures */
    void setNumOfContextualAdsFilteredOutOfBiddingInvalidSignatures(
            int numOfContextualAdsFilteredOutOfBiddingInvalidSignatures);

    /**
     * Sets the number of contextual ads filtered of bidding because no ads remained after filtering
     */
    void setNumOfContextualAdsFilteredOutOfBiddingNoAds(
            int numOfContextualAdsFilteredOutOfBiddingNoAds);

    /** Sets the number of ad counter keys in fcap filters */
    void setNumOfAdCounterKeysInFcapFilters(int numOfAdCounterKeysInFcapFilters);

    /** Sets the number of packages in app install filters */
    void setNumOfPackagesInAppInstallFilters(int numOfPackagesInAppInstallFilters);

    /** Sets the number of database operations */
    void setNumOfDbOperations(int numOfDbOperations);

    /** Closes the logging operation */
    void close();
}

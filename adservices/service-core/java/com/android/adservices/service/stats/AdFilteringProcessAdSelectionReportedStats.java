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

import com.google.auto.value.AutoValue;

/** Class for logging Ad filtering process during ad selection. */
@AutoValue
public abstract class AdFilteringProcessAdSelectionReportedStats {
    /** Returns the status response code in AdServices. */
    public abstract int getStatusCode();
    /** Returns latency when running the whole Ad filtering process. */
    public abstract int getLatencyInMillisOfAllAdFiltering();

    /** Returns latency when calling app install filters in ad filters. */
    public abstract int getLatencyInMillisOfAppInstallFiltering();

    /** Returns latency when calling FCap filters in ad filters. */
    public abstract int getLatencyInMillisOfFcapFilters();

    /** Returns the total number of Ads before Ad filtering process. */
    public abstract int getTotalNumOfAdsBeforeFiltering();

    /** Returns the process type when calling Ad filtering. */
    @AdsRelevanceStatusUtils.FilterProcessType
    public abstract int getFilterProcessType();

    /**
     * Returns the number of Ads which are filtered out of bidding. The field will be set as
     * FIELD_UNSET if filter_process_type is FILTER_CONTEXTUAL_ADS
     */
    public abstract int getNumOfAdsFilteredOutOfBidding();

    /**
     * Returns the number of custom audiences which are filtered out of bidding. The field will be
     * set as FIELD_UNSET if filter_process_type is FILTER_CONTEXTUAL_ADS
     */
    public abstract int getNumOfCustomAudiencesFilteredOutOfBidding();

    /**
     * Returns the total number of custom audiences before Ad filtering process. The field will be
     * set as FIELD_UNSET if filter_process_type is FILTER_CONTEXTUAL_ADS
     */
    public abstract int getTotalNumOfCustomAudiencesBeforeFiltering();

    /**
     * Returns the number of Ads which are filtered during contextual ads. The field will be set as
     * FIELD_UNSET if filter_process_type is FILTER_CUSTOM_AUDIENCES
     */
    public abstract int getNumOfContextualAdsFiltered();

    /**
     * Returns the number of contextual Ads filtered out of bidding because of invalid signatures.
     * The field will be set as * FIELD_UNSET if filter_process_type is FILTER_CUSTOM_AUDIENCES
     */
    public abstract int getNumOfContextualAdsFilteredOutOfBiddingInvalidSignatures();

    /**
     * Returns the number of contextual Ads filtered out of bidding because of no Ads. The field
     * will be set as FIELD_UNSET if filter_process_type is FILTER_CUSTOM_AUDIENCES
     */
    public abstract int getNumOfContextualAdsFilteredOutOfBiddingNoAds();

    /**
     * Returns the total number of contextual Ads before filtering. The field will be set as
     * FIELD_UNSET if filter_process_type is FILTER_CUSTOM_AUDIENCES
     */
    public abstract int getTotalNumOfContextualAdsBeforeFiltering();

    /** Returns the number of ad counter keys in fcap filters. */
    public abstract int getNumOfAdCounterKeysInFcapFilters();

    /** Returns the number of app packages involve in app install filters. */
    public abstract int getNumOfPackageInAppInstallFilters();

    /** Returns the number of database operations during ad selection. */
    public abstract int getNumOfDbOperations();

    /**
     * @return generic builder
     */
    public static Builder builder() {
        return new AutoValue_AdFilteringProcessAdSelectionReportedStats.Builder()
                .setLatencyInMillisOfAllAdFiltering(0)
                .setLatencyInMillisOfAppInstallFiltering(0)
                .setLatencyInMillisOfFcapFilters(0)
                .setStatusCode(0)
                .setNumOfAdsFilteredOutOfBidding(0)
                .setNumOfCustomAudiencesFilteredOutOfBidding(0)
                .setTotalNumOfAdsBeforeFiltering(0)
                .setTotalNumOfCustomAudiencesBeforeFiltering(0)
                .setNumOfPackageInAppInstallFilters(0)
                .setNumOfDbOperations(0)
                .setFilterProcessType(AdsRelevanceStatusUtils.FILTER_PROCESS_TYPE_UNSET)
                .setNumOfContextualAdsFiltered(0)
                .setNumOfAdCounterKeysInFcapFilters(0)
                .setNumOfContextualAdsFilteredOutOfBiddingInvalidSignatures(0)
                .setNumOfContextualAdsFilteredOutOfBiddingNoAds(0)
                .setTotalNumOfContextualAdsBeforeFiltering(0);
    }

    /** Builder class for AdFilteringProcessAdSelectionReportedStats. */
    @AutoValue.Builder
    public abstract static class Builder {
        /** Sets the status response code in AdServices. */
        public abstract Builder setStatusCode(int value);

        /** Sets latency when running the whole Ad filtering process. */
        public abstract Builder setLatencyInMillisOfAllAdFiltering(int value);

        /** Sets latency when calling app install filters in ad filters. */
        public abstract Builder setLatencyInMillisOfAppInstallFiltering(int value);

        /** Sets latency when calling FCap filters in ad filters. */
        public abstract Builder setLatencyInMillisOfFcapFilters(int value);

        /** Sets the total number of Ads before Ad filtering process. */
        public abstract Builder setTotalNumOfAdsBeforeFiltering(int value);

        /** Sets the process type when calling Ad filtering. */
        public abstract Builder setFilterProcessType(
                @AdsRelevanceStatusUtils.FilterProcessType int value);

        /**
         * Sets the number of Ads which are filtered out of bidding. The field will be set as
         * FIELD_UNSET if filter_process_type is FILTER_CONTEXTUAL_ADS
         */
        public abstract Builder setNumOfAdsFilteredOutOfBidding(int value);

        /** Sets the number of custom audiences which are filtered out of bidding. */
        public abstract Builder setNumOfCustomAudiencesFilteredOutOfBidding(int value);

        /**
         * Sets the total number of custom audiences before Ad filtering process. The field will be
         * set as FIELD_UNSET if filter_process_type is FILTER_CONTEXTUAL_ADS
         */
        public abstract Builder setTotalNumOfCustomAudiencesBeforeFiltering(int value);

        /**
         * Sets the number of Ads which are filtered during contextual ads. The field will be set as
         * FIELD_UNSET if filter_process_type is FILTER_CUSTOM_AUDIENCES
         */
        public abstract Builder setNumOfContextualAdsFiltered(int value);

        /**
         * Sets the number of contextual Ads filtered out of bidding because of invalid signatures.
         */
        public abstract Builder setNumOfContextualAdsFilteredOutOfBiddingInvalidSignatures(
                int value);

        /** Sets the number of contextual Ads filtered out of bidding because of no Ads. */
        public abstract Builder setNumOfContextualAdsFilteredOutOfBiddingNoAds(int value);

        /** Sets the total number of contextual Ads before filtering. */
        public abstract Builder setTotalNumOfContextualAdsBeforeFiltering(int value);

        /** Sets the number of ad counter keys in fcap filters. */
        public abstract Builder setNumOfAdCounterKeysInFcapFilters(int value);

        /** Sets the number of app packages involve in app install filters. */
        public abstract Builder setNumOfPackageInAppInstallFilters(int value);

        /** Sets the number of database operations during ad selection. */
        public abstract Builder setNumOfDbOperations(int value);

        /** Returns an instance of {@link AdFilteringProcessAdSelectionReportedStats} */
        public abstract AdFilteringProcessAdSelectionReportedStats build();
    }
}

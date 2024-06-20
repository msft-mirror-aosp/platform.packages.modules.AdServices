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

/** Class for logging Ad filtering process during join CA. */
@AutoValue
public abstract class AdFilteringProcessJoinCAReportedStats {
    /** Returns the status response code in AdServices. */
    public abstract int getStatusCode();

    /**
     * Returns the total number of Ads using keys less than the maximum number of keys limitation.
     */
    public abstract int getCountOfAdsWithKeysMuchSmallerThanLimitation();

    /**
     * Returns the total number of Ads using keys are equal or greater than 50% maximum number of
     * keys limitation but smaller than maximum number of keys limitation.
     */
    public abstract int getCountOfAdsWithKeysSmallerThanLimitation();

    /**
     * Returns the total number of Ads using keys are equal to maximum number of keys limitation.
     */
    public abstract int getCountOfAdsWithKeysEqualToLimitation();

    /**
     * Returns the total number of Ads using keys greater than the maximum number of keys
     * limitation.
     */
    public abstract int getCountOfAdsWithKeysLargerThanLimitation();

    /** Returns the total number of Ads using empty keys. */
    public abstract int getCountOfAdsWithEmptyKeys();

    /**
     * Returns the total number of Ads using filters less than the maximum number of filters
     * limitation.
     */
    public abstract int getCountOfAdsWithFiltersMuchSmallerThanLimitation();

    /**
     * Returns the total number of Ads using filters are equal or greater than 50% maximum number of
     * filters limitation but smaller than maximum number of filters limitation.
     */
    public abstract int getCountOfAdsWithFiltersSmallerThanLimitation();

    /**
     * Returns the total number of Ads using filters are equal to maximum number of filters
     * limitation.
     */
    public abstract int getCountOfAdsWithFiltersEqualToLimitation();

    /**
     * Returns the total number of Ads using filters greater than the maximum number of filters
     * limitation.
     */
    public abstract int getCountOfAdsWithFiltersLargerThanLimitation();

    /** Returns the total number of Ads using empty filters. */
    public abstract int getCountOfAdsWithEmptyFilters();

    /** Returns the total number of used Ad filtering keys per custom audience. */
    public abstract int getTotalNumberOfUsedKeys();

    /** Returns the total number of used Ad filters per custom audience. */
    public abstract int getTotalNumberOfUsedFilters();

    /**
     * @return generic builder
     */
    public static Builder builder() {
        return new AutoValue_AdFilteringProcessJoinCAReportedStats.Builder();
    }

    /** Builder class for AdFilteringProcessJoinCAReportedStats. */
    @AutoValue.Builder
    public abstract static class Builder {
        /** Sets the status response code in AdServices. */
        public abstract Builder setStatusCode(int value);

        /**
         * Sets the total number of Ads using keys less than the maximum number of keys limitation.
         */
        public abstract Builder setCountOfAdsWithKeysMuchSmallerThanLimitation(int value);

        /**
         * Sets the total number of Ads using keys are equal or greater than 50% maximum number of
         * keys limitation but smaller than maximum number of keys limitation.
         */
        public abstract Builder setCountOfAdsWithKeysSmallerThanLimitation(int value);

        /**
         * Sets the total number of Ads using keys are equal to maximum number of keys limitation.
         */
        public abstract Builder setCountOfAdsWithKeysEqualToLimitation(int value);

        /**
         * Sets the total number of Ads using keys greater than the maximum number of keys
         * limitation.
         */
        public abstract Builder setCountOfAdsWithKeysLargerThanLimitation(int value);

        /** Sets the total number of Ads using empty keys. */
        public abstract Builder setCountOfAdsWithEmptyKeys(int value);

        /**
         * Sets the total number of Ads using filters less than the maximum number of filters
         * limitation.
         */
        public abstract Builder setCountOfAdsWithFiltersMuchSmallerThanLimitation(int value);

        /**
         * Sets the total number of Ads using filters are equal or greater than 50% maximum number
         * of filters limitation but smaller than maximum number of filters limitation.
         */
        public abstract Builder setCountOfAdsWithFiltersSmallerThanLimitation(int value);

        /**
         * Sets the total number of Ads using filters are equal to maximum number of filters
         * limitation.
         */
        public abstract Builder setCountOfAdsWithFiltersEqualToLimitation(int value);

        /**
         * Sets the total number of Ads using filters greater than the maximum number of filters
         * limitation.
         */
        public abstract Builder setCountOfAdsWithFiltersLargerThanLimitation(int value);

        /** Sets the total number of Ads using empty filters. */
        public abstract Builder setCountOfAdsWithEmptyFilters(int value);

        /** Returns the total number of used Ad filtering keys per custom audience. */
        public abstract Builder setTotalNumberOfUsedKeys(int value);

        /** Sets the total number of used Ad filters per custom audience. */
        public abstract Builder setTotalNumberOfUsedFilters(int value);

        /** Returns an instance of {@link AdFilteringProcessJoinCAReportedStats} */
        public abstract AdFilteringProcessJoinCAReportedStats build();
    }
}

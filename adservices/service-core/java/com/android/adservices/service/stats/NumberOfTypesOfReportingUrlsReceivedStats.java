/*
 * Copyright (C) 2025 The Android Open Source Project
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

@AutoValue
public abstract class NumberOfTypesOfReportingUrlsReceivedStats {

    /** Returns the number of top-level seller reporting URLs. */
    public abstract int getNumberOfTopLevelSellerReportingUrl();

    /** Returns the number of buyer reporting URLs. */
    public abstract int getNumberOfBuyerReportingUrl();

    /** Returns the number of component seller reporting URLs. */
    public abstract int getNumberOfComponentSellerReportingUrl();

    /** Returns the number of component seller event reporting URLs. */
    public abstract int getNumberOfComponentSellerEventReportingUrl();

    /** Returns the number of top-level seller event reporting URLs. */
    public abstract int getNumberOfTopLevelSellerEventReportingUrl();

    /** Returns the number of buyer event reporting URLs. */
    public abstract int getNumberOfBuyerEventReportingUrl();

    /** Creates a builder for {@link NumberOfTypesOfReportingUrlsReceivedStats}. */
    public static Builder builder() {
        return new AutoValue_NumberOfTypesOfReportingUrlsReceivedStats.Builder();
    }

    @AutoValue.Builder
    public abstract static class Builder {
        /**
         * Sets the number of top-level seller reporting URLs.
         *
         * @param numberOfTopLevelSellerUrl The number of top-level seller reporting URLs.
         */
        public abstract Builder setNumberOfTopLevelSellerReportingUrl(
                int numberOfTopLevelSellerUrl);

        /**
         * Sets the number of buyer reporting URLs.
         *
         * @param numberOfBuyerReportingUrl The number of buyer reporting URLs.
         */
        public abstract Builder setNumberOfBuyerReportingUrl(int numberOfBuyerReportingUrl);

        /**
         * Sets the number of component seller reporting URLs.
         *
         * @param numberOfComponentSellerReportingUrl The number of component seller reporting URLs.
         */
        public abstract Builder setNumberOfComponentSellerReportingUrl(
                int numberOfComponentSellerReportingUrl);

        /**
         * Sets the number of component seller event reporting URLs.
         *
         * @param numberOfComponentSellerEventReportingUrl The number of component seller event
         *     reporting URLs.
         */
        public abstract Builder setNumberOfComponentSellerEventReportingUrl(
                int numberOfComponentSellerEventReportingUrl);

        /**
         * Sets the number of top-level seller event reporting URLs.
         *
         * @param numberOfTopLevelSellerEventReportingUrl The number of top-level seller interaction
         *     reporting URLs.
         */
        public abstract Builder setNumberOfTopLevelSellerEventReportingUrl(
                int numberOfTopLevelSellerEventReportingUrl);

        /**
         * Sets the number of buyer event reporting URLs.
         *
         * @param numberOfBuyerEventReportingUrl The number of buyer interaction reporting URLs.
         */
        public abstract Builder setNumberOfBuyerEventReportingUrl(
                int numberOfBuyerEventReportingUrl);

        /** Builds the {@link NumberOfTypesOfReportingUrlsReceivedStats} object. */
        public abstract NumberOfTypesOfReportingUrlsReceivedStats build();
    }
}

/*
 * Copyright (C) 2022 The Android Open Source Project
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

package com.android.adservices.data.measurement;

import java.util.Objects;

/**
 * For test data parsing only - the object's
 * field types were only made to correspond
 * with the database column types.
 */
class AttributionRateLimit {
    private String mId;
    private String mSourceSite;
    private String mDestinationSite;
    private String mReportTo;
    private long mTriggerTime;
    private String mRegisterer;

    @Override
    public boolean equals(Object obj) {
        if (!(obj instanceof AttributionRateLimit)) {
            return false;
        }
        AttributionRateLimit attr = (AttributionRateLimit) obj;
        return mTriggerTime == attr.mTriggerTime
                && Objects.equals(mSourceSite, attr.mSourceSite)
                && Objects.equals(mDestinationSite, attr.mDestinationSite)
                && Objects.equals(mReportTo, attr.mReportTo)
                && Objects.equals(mRegisterer, attr.mRegisterer);
    }

    @Override
    public int hashCode() {
        return Objects.hash(
                mId, mSourceSite, mDestinationSite, mReportTo, mTriggerTime, mRegisterer);
    }

    public String getId() {
        return mId;
    }

    public String getSourceSite() {
        return mSourceSite;
    }

    public String getDestinationSite() {
        return mDestinationSite;
    }

    public String getReportTo() {
        return mReportTo;
    }

    public long getTriggerTime() {
        return mTriggerTime;
    }

    public String getRegisterer() {
        return mRegisterer;
    }

    /**
     * Builder for AttributionRateLimit
     */
    public static final class Builder {
        private final AttributionRateLimit mBuilding;

        Builder() {
            mBuilding = new AttributionRateLimit();
        }

        public Builder setId(String id) {
            mBuilding.mId = id;
            return this;
        }

        public Builder setSourceSite(String site) {
            mBuilding.mSourceSite = site;
            return this;
        }

        public Builder setDestinationSite(String dest) {
            mBuilding.mDestinationSite = dest;
            return this;
        }

        public Builder setReportTo(String reportTo) {
            mBuilding.mReportTo = reportTo;
            return this;
        }

        public Builder setTriggerTime(long time) {
            mBuilding.mTriggerTime = time;
            return this;
        }

        public Builder setRegisterer(String registerer) {
            mBuilding.mRegisterer = registerer;
            return this;
        }

        /**
         * Build the AttributionRateLimit
         */
        public AttributionRateLimit build() {
            return mBuilding;
        }
    }
}

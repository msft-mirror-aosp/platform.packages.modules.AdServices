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
class Attribution {
    private String mId;
    private String mSourceSite;
    private String mSourceOrigin;
    private String mDestinationSite;
    private String mDestinationOrigin;
    private String mAdTechDomain;
    private long mTriggerTime;
    private String mRegistrant;

    @Override
    public boolean equals(Object obj) {
        if (!(obj instanceof Attribution)) {
            return false;
        }
        Attribution attr = (Attribution) obj;
        return mTriggerTime == attr.mTriggerTime
                && Objects.equals(mSourceSite, attr.mSourceSite)
                && Objects.equals(mSourceOrigin, attr.mSourceOrigin)
                && Objects.equals(mDestinationSite, attr.mDestinationSite)
                && Objects.equals(mDestinationOrigin, attr.mDestinationOrigin)
                && Objects.equals(mAdTechDomain, attr.mAdTechDomain)
                && Objects.equals(mRegistrant, attr.mRegistrant);
    }

    @Override
    public int hashCode() {
        return Objects.hash(
                mId, mSourceSite, mSourceOrigin, mDestinationSite, mDestinationOrigin,
                mAdTechDomain, mTriggerTime, mRegistrant);
    }

    public String getId() {
        return mId;
    }

    public String getSourceSite() {
        return mSourceSite;
    }

    public String getSourceOrigin() {
        return mSourceOrigin;
    }

    public String getDestinationSite() {
        return mDestinationSite;
    }

    public String getDestinationOrigin() {
        return mDestinationOrigin;
    }

    public String getAdTechDomain() {
        return mAdTechDomain;
    }

    public long getTriggerTime() {
        return mTriggerTime;
    }

    public String getRegistrant() {
        return mRegistrant;
    }

    /**
     * Builder for Attribution
     */
    public static final class Builder {
        private final Attribution mBuilding;

        Builder() {
            mBuilding = new Attribution();
        }

        public Builder setId(String id) {
            mBuilding.mId = id;
            return this;
        }

        public Builder setSourceSite(String site) {
            mBuilding.mSourceSite = site;
            return this;
        }

        public Builder setSourceOrigin(String origin) {
            mBuilding.mSourceOrigin = origin;
            return this;
        }

        public Builder setDestinationSite(String dest) {
            mBuilding.mDestinationSite = dest;
            return this;
        }

        public Builder setDestinationOrigin(String origin) {
            mBuilding.mDestinationOrigin = origin;
            return this;
        }

        public Builder setAdTechDomain(String adTechDomain) {
            mBuilding.mAdTechDomain = adTechDomain;
            return this;
        }

        public Builder setTriggerTime(long time) {
            mBuilding.mTriggerTime = time;
            return this;
        }

        public Builder setRegistrant(String registrant) {
            mBuilding.mRegistrant = registrant;
            return this;
        }

        /**
         * Build the Attribution
         */
        public Attribution build() {
            return mBuilding;
        }
    }
}

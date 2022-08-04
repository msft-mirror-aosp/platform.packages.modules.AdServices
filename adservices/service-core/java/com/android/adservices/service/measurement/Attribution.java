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

package com.android.adservices.service.measurement;

import com.android.adservices.service.measurement.util.Validation;

import java.util.Objects;

/**
 * It is used to insert and retrieve an entry in the database that counts towards attribution rate
 * limits. It is typically built from an {@link EventReport} or {@link Source}-{@link Trigger}
 * combination.
 */
public class Attribution {
    private final String mId;
    private final String mSourceSite;
    private final String mSourceOrigin;
    private final String mDestinationSite;
    private final String mDestinationOrigin;
    private final String mAdTechDomain;
    private final long mTriggerTime;
    private final String mRegistrant;

    private Attribution(Builder builder) {
        this.mId = builder.mId;
        this.mSourceSite = builder.mSourceSite;
        this.mSourceOrigin = builder.mSourceOrigin;
        this.mDestinationSite = builder.mDestinationSite;
        this.mDestinationOrigin = builder.mDestinationOrigin;
        this.mAdTechDomain = builder.mAdTechDomain;
        this.mTriggerTime = builder.mTriggerTime;
        this.mRegistrant = builder.mRegistrant;
    }

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
                mSourceSite,
                mSourceOrigin,
                mDestinationSite,
                mDestinationOrigin,
                mAdTechDomain,
                mTriggerTime,
                mRegistrant);
    }

    /** @return unique identifier for {@link Attribution} */
    public String getId() {
        return mId;
    }

    /** @return top private domain of {@link Source} publisher */
    public String getSourceSite() {
        return mSourceSite;
    }

    /** @return complete {@link Source} publisher */
    public String getSourceOrigin() {
        return mSourceOrigin;
    }

    /** @return top private domain of {@link Trigger} destination */
    public String getDestinationSite() {
        return mDestinationSite;
    }

    /** @return complete {@link Trigger} destination */
    public String getDestinationOrigin() {
        return mDestinationOrigin;
    }

    /** @return {@link Source} or {@link Trigger} ad-tech domain */
    public String getAdTechDomain() {
        return mAdTechDomain;
    }

    /** @return {@link Trigger} event time */
    public long getTriggerTime() {
        return mTriggerTime;
    }

    /** @return {@link Trigger} registrant */
    public String getRegistrant() {
        return mRegistrant;
    }

    /** Builder for AttributionRateLimit */
    public static final class Builder {
        private String mId;
        private String mSourceSite;
        private String mSourceOrigin;
        private String mDestinationSite;
        private String mDestinationOrigin;
        private String mAdTechDomain;
        private long mTriggerTime;
        private String mRegistrant;

        /** See {@link Attribution#getId()}. */
        public Builder setId(String id) {
            mId = id;
            return this;
        }

        /** See {@link Attribution#getSourceSite()}. */
        public Builder setSourceSite(String sourceSite) {
            mSourceSite = sourceSite;
            return this;
        }

        /** See {@link Attribution#getSourceOrigin()}. */
        public Builder setSourceOrigin(String sourceOrigin) {
            mSourceOrigin = sourceOrigin;
            return this;
        }

        /** See {@link Attribution#getDestinationSite()}. */
        public Builder setDestinationSite(String destinationSite) {
            mDestinationSite = destinationSite;
            return this;
        }

        /** See {@link Attribution#getDestinationOrigin()}. */
        public Builder setDestinationOrigin(String destinationOrigin) {
            mDestinationOrigin = destinationOrigin;
            return this;
        }

        /** See {@link Attribution#getAdTechDomain()}. */
        public Builder setAdTechDomain(String adTechDomain) {
            mAdTechDomain = adTechDomain;
            return this;
        }

        /** See {@link Attribution#getTriggerTime()}. */
        public Builder setTriggerTime(long triggerTime) {
            mTriggerTime = triggerTime;
            return this;
        }

        /** See {@link Attribution#getRegistrant()}. */
        public Builder setRegistrant(String registrant) {
            mRegistrant = registrant;
            return this;
        }

        /** Validate and build the {@link Attribution}. */
        public Attribution build() {
            Validation.validateNonNull(
                    mSourceSite,
                    mSourceOrigin,
                    mDestinationSite,
                    mDestinationOrigin,
                    mAdTechDomain,
                    mRegistrant);
            return new Attribution(this);
        }
    }
}

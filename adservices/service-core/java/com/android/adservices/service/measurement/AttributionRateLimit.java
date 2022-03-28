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

import android.net.Uri;

import java.util.Objects;

/**
 * POJO for AttributionRateLimit.
 */
public class AttributionRateLimit {

    private String mId;
    private Uri mSourceSite;
    private Uri mDestinationSite;
    private Uri mReportTo;
    private long mTriggerTime;
    private Uri mRegisterer;

    private AttributionRateLimit() {
    }

    /**
     * Unique identifier for the {@link AttributionRateLimit}.
     */
    public String getId() {
        return mId;
    }

    /**
     * Site where {@link Source} occurred.
     */
    public Uri getSourceSite() {
        return mSourceSite;
    }

    /**
     * Site where {@link Trigger} occurred.
     */
    public Uri getDestinationSite() {
        return mDestinationSite;
    }

    /**
     * Reporting destination for the generated reports.
     */
    public Uri getReportTo() {
        return mReportTo;
    }

    /**
     * Time when the trigger occurred.
     */
    public long getTriggerTime() {
        return mTriggerTime;
    }

    /**
     * Registerer of the trigger.
     */
    public Uri getRegisterer() {
        return mRegisterer;
    }

    @Override
    public boolean equals(Object obj) {
        if (!(obj instanceof AttributionRateLimit)) {
            return false;
        }

        AttributionRateLimit attributionRateLimit = (AttributionRateLimit) obj;
        return mTriggerTime == attributionRateLimit.mTriggerTime
                && mId.equals(attributionRateLimit.mId)
                && Objects.equals(mSourceSite, attributionRateLimit.mSourceSite)
                && Objects.equals(mDestinationSite, attributionRateLimit.mDestinationSite)
                && Objects.equals(mReportTo, attributionRateLimit.mReportTo)
                && Objects.equals(mRegisterer, attributionRateLimit.mRegisterer);
    }

    @Override
    public int hashCode() {
        return Objects.hash(mId, mSourceSite, mDestinationSite, mReportTo, mTriggerTime,
                mRegisterer);
    }

    /**
     * Builder for {@link AttributionRateLimit}.
     */
    public static final class Builder {

        private final AttributionRateLimit mAttributionRateLimit;

        public Builder() {
            mAttributionRateLimit = new AttributionRateLimit();
        }

        /**
         * See {@link AttributionRateLimit#getId()}
         */
        public Builder setId(String id) {
            mAttributionRateLimit.mId = id;
            return this;
        }

        /**
         * See {@link AttributionRateLimit#getSourceSite()}
         */
        public Builder setSourceSite(Uri sourceSite) {
            mAttributionRateLimit.mSourceSite = sourceSite;
            return this;
        }

        /**
         * See {@link AttributionRateLimit#getDestinationSite()}
         */
        public Builder setDestinationSite(Uri destinationSite) {
            mAttributionRateLimit.mDestinationSite = destinationSite;
            return this;
        }

        /**
         * See {@link AttributionRateLimit#getReportTo()}
         */
        public Builder setReportTo(Uri reportTo) {
            mAttributionRateLimit.mReportTo = reportTo;
            return this;
        }

        /**
         * See {@link AttributionRateLimit#getTriggerTime()}
         */
        public Builder setTriggerTime(long triggerTime) {
            mAttributionRateLimit.mTriggerTime = triggerTime;
            return this;
        }

        /**
         * See {@link AttributionRateLimit#getRegisterer()}
         */
        public Builder setRegisterer(Uri registerer) {
            mAttributionRateLimit.mRegisterer = registerer;
            return this;
        }

        /**
         * Build the {@link AttributionRateLimit}.
         */
        public AttributionRateLimit build() {
            return mAttributionRateLimit;
        }
    }
}

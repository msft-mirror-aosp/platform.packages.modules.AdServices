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

import android.annotation.IntDef;
import android.net.Uri;

import com.android.adservices.service.measurement.attribution.RandomSelector;

import java.lang.annotation.Retention;
import java.lang.annotation.RetentionPolicy;
import java.util.Objects;
import java.util.stream.LongStream;

/**
 * POJO for Trigger.
 */

public class Trigger {

    private String mId;
    private Long mDedupKey;
    private Uri mAttributionDestination;
    private Uri mReportTo;
    private long mTriggerTime;
    private long mPriority;
    private long mTriggerData;
    private @Status int mStatus;
    private Uri mRegistrant;

    @IntDef(value = {
            Status.PENDING,
            Status.IGNORED,
            Status.ATTRIBUTED,
    })
    @Retention(RetentionPolicy.SOURCE)
    public @interface Status {
        int PENDING = 0;
        int IGNORED = 1;
        int ATTRIBUTED = 2;
    }

    private Trigger() {
        mDedupKey = null;
        mStatus = Status.PENDING;
    }

    @Override
    public boolean equals(Object obj) {
        if (!(obj instanceof Trigger)) {
            return false;
        }
        Trigger trigger = (Trigger) obj;
        return  Objects.equals(mId, trigger.getId())
                && Objects.equals(mAttributionDestination, trigger.mAttributionDestination)
                && Objects.equals(mReportTo, trigger.mReportTo)
                && mTriggerTime == trigger.mTriggerTime
                && mTriggerData == trigger.mTriggerData
                && mPriority == trigger.mPriority
                && mStatus == trigger.mStatus
                && Objects.equals(mDedupKey, trigger.mDedupKey)
                && Objects.equals(mRegistrant, trigger.mRegistrant);
    }

    @Override
    public int hashCode() {
        return Objects.hash(mId, mAttributionDestination, mReportTo, mTriggerTime, mTriggerData,
                mPriority, mStatus, mDedupKey);
    }

    /**
     * Unique identifier for the {@link Trigger}.
     */
    public String getId() {
        return mId;
    }

    /**
     * Deduplication key for distinguishing among different {@link Trigger} types.
     */
    public Long getDedupKey() {
        return mDedupKey;
    }

    /**
     * Destination where {@link Trigger} occurred.
     */
    public Uri getAttributionDestination() {
        return mAttributionDestination;
    }

    /**
     * Report destination for the generated reports.
     */
    public Uri getReportTo() {
        return mReportTo;
    }

    /**
     * Time when the event occurred.
     */
    public long getTriggerTime() {
        return mTriggerTime;
    }

    /**
     * Current state of the {@link Trigger}.
     */
    public @Status int getStatus() {
        return mStatus;
    }

    /**
     * Set the status.
     */
    public void setStatus(@Status int status) {
        mStatus = status;
    }

    /**
     * Priority used for selecting among {@link Trigger}.
     */
    public long getPriority() {
        return mPriority;
    }

    /**
     * Metadata for the {@link Trigger}.
     */
    public long getTriggerData() {
        return mTriggerData;
    }

    /**
     * Registrant of this trigger, primarily an App.
     */
    public Uri getRegistrant() {
        return mRegistrant;
    }

    /**
     * Function to get trigger data based on source type(Event/Navigation) with a pre-defined false
     * data randomness rate.
     * @param source {@link Source} for choosing source type
     * @return trigger data using false random rate based on source type
     */
    public long getRandomizedTriggerData(Source source) {
        Long[] possibleValues = LongStream.range(0, source.getTriggerDataCardinality())
                .boxed().toArray(Long[]::new);
        return RandomSelector.selectRandomDataWithProbability(
                source.getTriggerDataNoiseRate(), mTriggerData, possibleValues);
    }

    /**
     * Builder for {@link Trigger}.
     */
    public static final class Builder {

        private final Trigger mBuilding;

        public Builder() {
            mBuilding = new Trigger();
        }

        /**
         * See {@link Trigger#getId()}.
         */
        public Builder setId(String id) {
            mBuilding.mId = id;
            return this;
        }

        /**
         * See {@link Trigger#getPriority()}.
         */
        public Builder setPriority(long priority) {
            mBuilding.mPriority = priority;
            return this;
        }

        /**
         * See {@link Trigger#getAttributionDestination()}.
         */
        public Builder setAttributionDestination(Uri attributionDestination) {
            mBuilding.mAttributionDestination = attributionDestination;
            return this;
        }

        /**
         * See {@link Trigger#getReportTo()}.
         */
        public Builder setReportTo(Uri reportTo) {
            mBuilding.mReportTo = reportTo;
            return this;
        }

        /**
         * See {@link Trigger#getStatus()}.
         */
        public Builder setStatus(@Status int status) {
            mBuilding.mStatus = status;
            return this;
        }

        /**
         * See {@link Trigger#getTriggerData()}.
         */
        public Builder setTriggerData(long triggerData) {
            mBuilding.mTriggerData = triggerData;
            return this;
        }

        /**
         * See {@link Trigger#getDedupKey()}.
         */
        public Builder setDedupKey(Long dedupKey) {
            mBuilding.mDedupKey = dedupKey;
            return this;
        }

        /**
         * See {@link Trigger#getTriggerTime()}.
         */
        public Builder setTriggerTime(long triggerTime) {
            mBuilding.mTriggerTime = triggerTime;
            return this;
        }

        /**
         * See {@link Trigger#getRegistrant()}
         */
        public Builder setRegistrant(Uri registrant) {
            mBuilding.mRegistrant = registrant;
            return this;
        }

        /**
         * Build the {@link Trigger}.
         */
        public Trigger build() {
            return mBuilding;
        }
    }
}

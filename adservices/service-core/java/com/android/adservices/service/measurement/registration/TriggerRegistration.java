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
package com.android.adservices.service.measurement.registration;

import android.annotation.NonNull;
import android.annotation.Nullable;
import android.net.Uri;


/**
 * A registration for a trigger of attribution.
 */
public final class TriggerRegistration {
    private final Uri mTopOrigin;
    private final Uri mReportingOrigin;
    private final long mTriggerData;
    private final long mTriggerPriority;
    private final Long mDeduplicationKey;
    private final String mAggregateTriggerData;
    private final String mAggregateValues;
    private final String mFilters;

    /** Create a trigger registration. */
    private TriggerRegistration(
            @NonNull Uri topOrigin,
            @NonNull Uri reportingOrigin,
            long triggerData,
            long triggerPriority,
            Long deduplicationKey,
            String aggregateTriggerData,
            String aggregateValues,
            @Nullable String filters) {
        mTopOrigin = topOrigin;
        mReportingOrigin = reportingOrigin;
        mTriggerData = triggerData;
        mTriggerPriority = triggerPriority;
        mDeduplicationKey = deduplicationKey;
        mAggregateTriggerData = aggregateTriggerData;
        mAggregateValues = aggregateValues;
        mFilters = filters;
    }

    /**
     * Top level origin.
     */
    public @NonNull Uri getTopOrigin() {
        return mTopOrigin;
    }

    /**
     * Reporting origin.
     */
    public @NonNull Uri getReportingOrigin() {
        return mReportingOrigin;
    }

    /**
     * Trigger data.
     */
    public @NonNull long getTriggerData() {
        return mTriggerData;
    }

    /**
     * Trigger priority.
     */
    public @NonNull long getTriggerPriority() {
        return mTriggerPriority;
    }

    /**
     * De-dup key.
     */
    public @NonNull Long getDeduplicationKey() {
        return mDeduplicationKey;
    }

    /**
     * Aggregate trigger data is used to generate aggregate report.
     */
    public String getAggregateTriggerData() {
        return mAggregateTriggerData;
    }

    /**
     * Aggregate value is used to generate aggregate report.
     */
    public String getAggregateValues() {
        return mAggregateValues;
    }

    /** Top level filters. */
    public String getFilters() {
        return mFilters;
    }

    /**
     * A builder for {@link TriggerRegistration}.
     */
    public static final class Builder {
        private Uri mTopOrigin;
        private Uri mReportingOrigin;
        private long mTriggerData;
        private long mTriggerPriority;
        private Long mDeduplicationKey;
        private String mAggregateTriggerData;
        private String mAggregateValues;
        private String mFilters;

        public Builder() {
            mTopOrigin = Uri.EMPTY;
            mReportingOrigin = Uri.EMPTY;
            mDeduplicationKey = null;
        }

        /**
         * See {@link TriggerRegistration#getTopOrigin}.
         */
        public @NonNull Builder setTopOrigin(@NonNull Uri origin) {
            mTopOrigin = origin;
            return this;
        }

        /**
         * See {@link TriggerRegistration#getReportingOrigin}.
         */
        public @NonNull Builder setReportingOrigin(@NonNull Uri origin) {
            mReportingOrigin = origin;
            return this;
        }

        /**
         * See {@link TriggerRegistration#getTriggerData}.
         */
        public @NonNull Builder setTriggerData(long data) {
            mTriggerData = data;
            return this;
        }

        /**
         * See {@link TriggerRegistration#getTriggerPriority}.
         */
        public @NonNull Builder setTriggerPriority(long priority) {
            mTriggerPriority = priority;
            return this;
        }

        /**
         * See {@link TriggerRegistration#getDeduplicationKey}.
         */
        public @NonNull Builder setDeduplicationKey(long key) {
            mDeduplicationKey = key;
            return this;
        }

        /**
         * See {@link TriggerRegistration#getAggregateTriggerData()}.
         */
        public Builder setAggregateTriggerData(String aggregateTriggerData) {
            mAggregateTriggerData = aggregateTriggerData;
            return this;
        }

        /**
         * See {@link TriggerRegistration#getAggregateValues()}.
         */
        public Builder setAggregateValues(String aggregateValues) {
            mAggregateValues = aggregateValues;
            return this;
        }

        /** See {@link TriggerRegistration#getFilters()}. */
        public Builder setFilters(String filters) {
            mFilters = filters;
            return this;
        }

        /**
         * Build the TriggerRegistration.
         */
        public @NonNull TriggerRegistration build() {
            if (mTopOrigin == null
                    || mReportingOrigin == null) {
                throw new IllegalArgumentException("uninitialized field");
            }
            return new TriggerRegistration(
                    mTopOrigin,
                    mReportingOrigin,
                    mTriggerData,
                    mTriggerPriority,
                    mDeduplicationKey,
                    mAggregateTriggerData,
                    mAggregateValues,
                    mFilters);
        }
    }
}

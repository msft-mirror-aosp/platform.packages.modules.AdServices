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
    private final String mAggregateTriggerData;
    private final String mAggregateValues;
    private final String mFilters;
    private final String mEventTriggers;

    /** Create a trigger registration. */
    private TriggerRegistration(
            @NonNull Uri topOrigin,
            @NonNull Uri reportingOrigin,
            @NonNull String eventTriggers,
            String aggregateTriggerData,
            String aggregateValues,
            @Nullable String filters) {
        mTopOrigin = topOrigin;
        mReportingOrigin = reportingOrigin;
        mAggregateTriggerData = aggregateTriggerData;
        mAggregateValues = aggregateValues;
        mFilters = filters;
        mEventTriggers = eventTriggers;
    }

    /** Top level origin. */
    @NonNull
    public Uri getTopOrigin() {
        return mTopOrigin;
    }

    /** Reporting origin. */
    @NonNull
    public Uri getReportingOrigin() {
        return mReportingOrigin;
    }

    /** Event triggers - contains trigger data, priority, de-dup key and event-level filters. */
    @NonNull
    public String getEventTriggers() {
        return mEventTriggers;
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
        private String mEventTriggers;
        private String mAggregateTriggerData;
        private String mAggregateValues;
        private String mFilters;

        public Builder() {
            mTopOrigin = Uri.EMPTY;
            mReportingOrigin = Uri.EMPTY;
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

        /** See {@link TriggerRegistration#getEventTriggers()}. */
        public @NonNull Builder setEventTriggers(@NonNull String eventTriggers) {
            mEventTriggers = eventTriggers;
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
                    mEventTriggers,
                    mAggregateTriggerData,
                    mAggregateValues,
                    mFilters);
        }
    }
}

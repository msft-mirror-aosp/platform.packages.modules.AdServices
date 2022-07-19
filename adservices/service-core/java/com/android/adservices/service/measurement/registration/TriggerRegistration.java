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

import com.android.adservices.service.measurement.validation.Validation;

import java.util.Objects;

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
    @Nullable private final Long mDebugKey;

    /** Create a trigger registration. */
    private TriggerRegistration(
            @NonNull Uri topOrigin,
            @NonNull Uri reportingOrigin,
            @NonNull String eventTriggers,
            @Nullable String aggregateTriggerData,
            @Nullable String aggregateValues,
            @Nullable String filters,
            @Nullable Long debugKey) {
        mTopOrigin = topOrigin;
        mReportingOrigin = reportingOrigin;
        mAggregateTriggerData = aggregateTriggerData;
        mAggregateValues = aggregateValues;
        mFilters = filters;
        mEventTriggers = eventTriggers;
        mDebugKey = debugKey;
    }

    @Override
    public boolean equals(Object o) {
        if (this == o) return true;
        if (!(o instanceof TriggerRegistration)) return false;
        TriggerRegistration that = (TriggerRegistration) o;
        return Objects.equals(mTopOrigin, that.mTopOrigin)
                && Objects.equals(mReportingOrigin, that.mReportingOrigin)
                && Objects.equals(mAggregateTriggerData, that.mAggregateTriggerData)
                && Objects.equals(mAggregateValues, that.mAggregateValues)
                && Objects.equals(mFilters, that.mFilters)
                && Objects.equals(mEventTriggers, that.mEventTriggers)
                && Objects.equals(mDebugKey, that.mDebugKey);
    }

    @Override
    public int hashCode() {
        return Objects.hash(
                mTopOrigin,
                mReportingOrigin,
                mAggregateTriggerData,
                mAggregateValues,
                mFilters,
                mEventTriggers,
                mDebugKey);
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
    /** Trigger Debug Key. */
    public @Nullable Long getDebugKey() {
        return mDebugKey;
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
        private @Nullable Long mDebugKey;

        /** See {@link TriggerRegistration#getTopOrigin}. */
        @NonNull
        public Builder setTopOrigin(@NonNull Uri origin) {
            Validation.validateUri(origin);
            mTopOrigin = origin;
            return this;
        }

        /** See {@link TriggerRegistration#getReportingOrigin}. */
        @NonNull
        public Builder setReportingOrigin(@NonNull Uri origin) {
            Validation.validateUri(origin);
            mReportingOrigin = origin;
            return this;
        }

        /** See {@link TriggerRegistration#getEventTriggers()}. */
        @NonNull
        public Builder setEventTriggers(@NonNull String eventTriggers) {
            Validation.validateNonNull(eventTriggers);
            mEventTriggers = eventTriggers;
            return this;
        }

        /** See {@link TriggerRegistration#getAggregateTriggerData()}. */
        @NonNull
        public Builder setAggregateTriggerData(@Nullable String aggregateTriggerData) {
            mAggregateTriggerData = aggregateTriggerData;
            return this;
        }

        /** See {@link TriggerRegistration#getAggregateValues()}. */
        @NonNull
        public Builder setAggregateValues(@Nullable String aggregateValues) {
            mAggregateValues = aggregateValues;
            return this;
        }

        /** See {@link TriggerRegistration#getFilters()}. */
        @NonNull
        public Builder setFilters(@Nullable String filters) {
            mFilters = filters;
            return this;
        }

        /** See {@link TriggerRegistration#getDebugKey()}. */
        public Builder setDebugKey(@Nullable Long debugKey) {
            mDebugKey = debugKey;
            return this;
        }

        /** Build the TriggerRegistration. */
        @NonNull
        public TriggerRegistration build() {
            Validation.validateNonNull(mTopOrigin, mReportingOrigin);

            return new TriggerRegistration(
                    mTopOrigin,
                    mReportingOrigin,
                    mEventTriggers,
                    mAggregateTriggerData,
                    mAggregateValues,
                    mFilters,
                    mDebugKey);
        }
    }
}

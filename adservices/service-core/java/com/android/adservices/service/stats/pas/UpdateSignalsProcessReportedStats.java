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

package com.android.adservices.service.stats.pas;

import com.android.adservices.service.signals.evict.EvictionPriority;
import com.android.adservices.service.stats.AdsRelevanceStatusUtils.SignalEvictorType;
import com.android.adservices.service.stats.AdsRelevanceStatusUtils.Size;

import com.google.auto.value.AutoValue;
import com.google.common.collect.ImmutableSet;

import java.util.Set;

/** Class for updateSignals API process reported stats. */
@AutoValue
public abstract class UpdateSignalsProcessReportedStats {
    /** Returns the updated signals process latency in milliseconds for this API call. */
    public abstract int getUpdateSignalsProcessLatencyMillis();

    /** Returns the Adservices API status code for this API call. */
    public abstract int getAdservicesApiStatusCode();

    /** Returns the number of signals written for this API call. */
    public abstract int getSignalsWrittenCount();

    /** Returns the number of keys from downloaded JSON for this API call. */
    public abstract int getKeysStoredCount();

    /** Returns the number of values from downloaded JSON when calling PAS API. */
    public abstract int getValuesStoredCount();

    /** Returns the number of eviction rules for this API call. */
    public abstract int getEvictionRulesCount();

    /** Returns the bucketed size of the buyer who called the APIs signals. */
    @Size
    public abstract int getPerBuyerSignalSize();

    /**
     * Returns the average size, in bytes, of raw protected signals being updated for the buyer
     * performing the update.
     */
    public abstract float getMeanRawProtectedSignalsSizeBytes();

    /** Returns the size, in bytes, of the largest raw protected signal stored by the caller. */
    public abstract float getMaxRawProtectedSignalsSizeBytes();

    /** Returns the size, in bytes, of the smallest raw protected signal stored by the caller. */
    public abstract float getMinRawProtectedSignalsSizeBytes();

    /** Returns the unique evictors used in an eviction. */
    public abstract ImmutableSet<@SignalEvictorType Integer> getSignalEvictorsUsed();

    /** Returns the unique evicton priorities across updated signals in a single update call. */
    public abstract ImmutableSet<EvictionPriority> getUpdatedSignalEvictionPriorities();

    /** Returns the unique evicton priorities across evicted signals. */
    public abstract ImmutableSet<EvictionPriority> getEvictedSignalEvictionPriorities();

    /** Returns the bucketed size of the evicted signals in o single update call. */
    @Size
    public abstract int getPerBuyerEvictedSignalSize();

    /** Returns the count of updated signals with eviction priority. */
    public abstract int getUpdatedSignalsWithEvictionPriorityCount();

    /** Returns the value of X-UPDATE-SCHEMA-VERSION in the response header of update fetch call. */
    public abstract int getSignalUpdateSchemaVersion();

    /** Returns a generic builder. */
    public static Builder builder() {
        return new AutoValue_UpdateSignalsProcessReportedStats.Builder();
    }

    /** Builder class for UpdateSignalsProcessReported. */
    @AutoValue.Builder
    public abstract static class Builder {
        /** Sets the updated signals process latency in milliseconds for this API call. */
        public abstract Builder setUpdateSignalsProcessLatencyMillis(int value);

        /** Sets the Adservices API status code for this API call. */
        public abstract Builder setAdservicesApiStatusCode(int value);

        /** Sets the number of signals written for this API call. */
        public abstract Builder setSignalsWrittenCount(int value);

        /** Sets the number of keys from downloaded JSON for this API call. */
        public abstract Builder setKeysStoredCount(int value);

        /** Sets the number of values from downloaded JSON for this API call. */
        public abstract Builder setValuesStoredCount(int value);

        /** Sets the number of eviction rules for this API call. */
        public abstract Builder setEvictionRulesCount(int value);

        /** Sets the bucketed size of the buyer who called the APIs signals. */
        public abstract Builder setPerBuyerSignalSize(@Size int value);

        /**
         * Sets the average size, in bytes, of raw protected signals being updated for the buyer
         * performing the update.
         */
        public abstract Builder setMeanRawProtectedSignalsSizeBytes(float value);

        /** Sets the size, in bytes, of the largest raw protected signal stored by the caller. */
        public abstract Builder setMaxRawProtectedSignalsSizeBytes(float value);

        /** Sets the size, in bytes, of the smallest raw protected signal stored by the caller. */
        public abstract Builder setMinRawProtectedSignalsSizeBytes(float value);

        /** Sets the unique evictors used in an eviction. */
        public abstract Builder setSignalEvictorsUsed(Set<@SignalEvictorType Integer> value);

        /** Sets the unique evicton priorities across updated signals in a single update call. */
        public abstract Builder setUpdatedSignalEvictionPriorities(Set<EvictionPriority> value);

        /** Sets the unique evicton priorities across evicted signals. */
        public abstract Builder setEvictedSignalEvictionPriorities(Set<EvictionPriority> value);

        /** Sets the bucketed size of the evicted signals in o single update call. */
        public abstract Builder setPerBuyerEvictedSignalSize(@Size int value);

        /** Sets the count of updated signals with eviction priority. */
        public abstract Builder setUpdatedSignalsWithEvictionPriorityCount(int value);

        /**
         * Sets the value of X-UPDATE-SCHEMA-VERSION in the response header of update fetch call.
         */
        public abstract Builder setSignalUpdateSchemaVersion(int value);

        /** Build the {@link UpdateSignalsProcessReportedStats}. */
        public abstract UpdateSignalsProcessReportedStats build();
    }
}

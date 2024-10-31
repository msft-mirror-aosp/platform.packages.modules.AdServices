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
package com.android.adservices.service.measurement.aggregation;

import android.annotation.Nullable;

import com.android.adservices.service.measurement.util.UnsignedLong;

import java.util.Objects;

/** POJO for AggregatableKeyValue */
public class AggregatableKeyValue {
    private final int mValue;
    private final UnsignedLong mFilteringId;

    private AggregatableKeyValue(AggregatableKeyValue.Builder builder) {
        mValue = builder.mValue;
        mFilteringId = builder.mFilteringId;
    }

    /** Returns the int value of aggregatable_value's value. */
    public int getValue() {
        return mValue;
    }

    /** Returns the filtering id. */
    @Nullable
    public UnsignedLong getFilteringId() {
        return mFilteringId;
    }

    @Override
    public boolean equals(Object o) {
        if (this == o) return true;
        if (!(o instanceof AggregatableKeyValue)) return false;
        AggregatableKeyValue that = (AggregatableKeyValue) o;
        return mValue == that.mValue && Objects.equals(mFilteringId, that.mFilteringId);
    }

    @Override
    public int hashCode() {
        return Objects.hash(mValue, mFilteringId);
    }

    public static final class Builder {
        private int mValue;
        private UnsignedLong mFilteringId;

        // This will throw JSONException if a JSONObject was previously persisted, and flag
        // MEASUREMENT_ENABLE_FLEXIBLE_CONTRIBUTION_FILTERING gets set to false.
        public Builder(int value) {
            mValue = value;
        }

        /** {@link AggregatableKeyValue#getFilteringId()} */
        public AggregatableKeyValue.Builder setFilteringId(UnsignedLong filteringId) {
            mFilteringId = filteringId;
            return this;
        }

        /** Build the {@link AggregatableKeyValue}. */
        public AggregatableKeyValue build() {
            return new AggregatableKeyValue(this);
        }
    }

    public interface AggregatableKeyValueContract {
        String VALUE = "value";
        String FILTERING_ID = "filtering_id";
    }
}

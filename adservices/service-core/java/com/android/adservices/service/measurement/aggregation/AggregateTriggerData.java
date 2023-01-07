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

package com.android.adservices.service.measurement.aggregation;

import com.android.adservices.service.measurement.FilterMap;

import java.math.BigInteger;
import java.util.HashSet;
import java.util.List;
import java.util.Objects;
import java.util.Optional;
import java.util.Set;

/**
 * POJO for AggregateTriggerData.
 */
public class AggregateTriggerData {

    private BigInteger mKey;
    private Set<String> mSourceKeys;
    private Optional<List<FilterMap>> mFilterSet;
    private Optional<List<FilterMap>> mNotFilterSet;

    private AggregateTriggerData() {
        mKey = null;
        mSourceKeys = new HashSet<>();
        mFilterSet = Optional.empty();
        mNotFilterSet = Optional.empty();
    }

    @Override
    public boolean equals(Object obj) {
        if (!(obj instanceof AggregateTriggerData)) {
            return false;
        }
        AggregateTriggerData attributionTriggerData = (AggregateTriggerData) obj;
        return Objects.equals(mKey, attributionTriggerData.mKey)
                && Objects.equals(mSourceKeys, attributionTriggerData.mSourceKeys)
                && Objects.equals(mFilterSet, attributionTriggerData.mFilterSet)
                && Objects.equals(mNotFilterSet, attributionTriggerData.mNotFilterSet);
    }

    @Override
    public int hashCode() {
        return Objects.hash(mKey, mSourceKeys);
    }

    /**
     * Returns trigger_data's key which will be used to generate the aggregate key.
     */
    public BigInteger getKey() {
        return mKey;
    }

    /**
     * Returns the source_key set which represent which source this dimension applies to.
     */
    public Set<String> getSourceKeys() {
        return mSourceKeys;
    }

    /**
     * Returns the filter which controls when aggregate trigger data ise used based on impression
     * side information.
     */
    public Optional<List<FilterMap>> getFilterSet() {
        return mFilterSet;
    }

    /**
     * Returns the not_filter, reverse of filter.
     */
    public Optional<List<FilterMap>> getNotFilterSet() {
        return mNotFilterSet;
    }

    /**
     * Builder for {@link AggregateTriggerData}.
     */
    public static final class Builder {
        private final AggregateTriggerData mBuilding;

        public Builder() {
            mBuilding = new AggregateTriggerData();
        }

        /**
         * See {@link AggregateTriggerData#getKey()}.
         */
        public Builder setKey(BigInteger key) {
            mBuilding.mKey = key;
            return this;
        }

        /**
         * See {@link AggregateTriggerData#getSourceKeys()}.
         */
        public Builder setSourceKeys(Set<String> sourceKeys) {
            mBuilding.mSourceKeys = sourceKeys;
            return this;
        }

        /**
         * See {@link AggregateTriggerData#getFilter()}.
         */
        public Builder setFilterSet(List<FilterMap> filterSet) {
            mBuilding.mFilterSet = Optional.of(filterSet);
            return this;
        }

        /**
         * See {@link AggregateTriggerData#getNotFilter()}
         */
        public Builder setNotFilterSet(List<FilterMap> notFilterSet) {
            mBuilding.mNotFilterSet = Optional.of(notFilterSet);
            return this;
        }

        /**
         * Build the {@link AggregateTriggerData}
         */
        public AggregateTriggerData build() {
            return mBuilding;
        }
    }
}

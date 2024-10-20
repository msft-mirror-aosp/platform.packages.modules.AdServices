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

import android.annotation.Nullable;

import com.android.adservices.service.Flags;
import com.android.adservices.service.measurement.FilterMap;
import com.android.adservices.service.measurement.util.Filter;

import java.util.ArrayList;
import java.util.List;
import java.util.Objects;
import java.util.Optional;

/**
 * POJO for AggregatableAttributionTrigger.
 */
public class AggregatableAttributionTrigger {

    private List<AggregateTriggerData> mTriggerData;
    @Nullable private List<AggregatableNamedBudget> mNamedBudgets;
    @Nullable private List<AggregatableValuesConfig> mValueConfigs;
    private Optional<List<AggregateDeduplicationKey>> mAggregateDeduplicationKeys;

    private AggregatableAttributionTrigger() {
        mTriggerData = new ArrayList<>();
    }

    @Override
    public boolean equals(Object obj) {
        if (!(obj instanceof AggregatableAttributionTrigger)) {
            return false;
        }
        AggregatableAttributionTrigger attributionTrigger = (AggregatableAttributionTrigger) obj;
        return Objects.equals(mTriggerData, attributionTrigger.mTriggerData)
                && Objects.equals(mValueConfigs, attributionTrigger.mValueConfigs)
                && Objects.equals(mNamedBudgets, attributionTrigger.mNamedBudgets);
    }

    @Override
    public int hashCode() {
        return Objects.hash(mTriggerData, mNamedBudgets, mValueConfigs);
    }

    /**
     * Returns all trigger_data which define individual conversion data that we want to add to
     * the conversion side aggregation key.
     */
    public List<AggregateTriggerData> getTriggerData() {
        return mTriggerData;
    }

    /**
     * Returns a list of AggregatableValuesConfig that contains values, filters, and not_filters for
     * each aggregatable_source.
     */
    @Nullable
    public List<AggregatableValuesConfig> getValueConfigs() {
        return mValueConfigs;
    }

    /** Returns De-deuplication keys for Aggregate Report Creation. */
    public Optional<List<AggregateDeduplicationKey>> getAggregateDeduplicationKeys() {
        return mAggregateDeduplicationKeys;
    }

    /**
     * Returns a list of AggregatableNamedBudget that contains name, filters, and not_filters for
     * each budget.
     */
    @Nullable
    public List<AggregatableNamedBudget> getNamedBudgets() {
        return mNamedBudgets;
    }

    /**
     * Extract an {@link AggregateDeduplicationKey} from the aggregateDeduplicationKeys.
     *
     * @param sourceFilterMap the source filter map of the AggregatableAttributionSource.
     */
    public Optional<AggregateDeduplicationKey> maybeExtractDedupKey(
            FilterMap sourceFilterMap, Flags flags) {
        if (getAggregateDeduplicationKeys().isEmpty()) return Optional.empty();

        Filter filter = new Filter(flags);
        for (AggregateDeduplicationKey key : getAggregateDeduplicationKeys().get()) {
            if (sourceFilterMap.isEmpty(flags)) {
                return Optional.of(key);
            }
            if (key.getFilterSet().isPresent()
                    && !filter.isFilterMatch(sourceFilterMap, key.getFilterSet().get(), true)) {
                continue;
            }

            if (key.getNotFilterSet().isPresent()
                    && !filter.isFilterMatch(sourceFilterMap, key.getNotFilterSet().get(), false)) {
                continue;
            }
            if (key.getDeduplicationKey().isEmpty()) {
                return Optional.empty();
            }
            return Optional.of(key);
        }
        return Optional.empty();
    }

    /**
     * Extract the value for key "name" from the {@link AggregatableNamedBudget}
     * aggregatableNamedBudget.
     *
     * @param sourceFilterMap the source filter map of the AggregatableAttributionSource.
     */
    public Optional<String> maybeExtractNamedBudget(FilterMap sourceFilterMap, Flags flags) {
        if (getNamedBudgets() == null || getNamedBudgets().isEmpty()) {
            return Optional.empty();
        }

        if (sourceFilterMap.isEmpty(flags)) {
            return Optional.of(getNamedBudgets().get(0).getName());
        }
        Filter filter = new Filter(flags);
        for (AggregatableNamedBudget aggregatableNamedBudget : getNamedBudgets()) {
            if (aggregatableNamedBudget.getFilterSet() != null
                    && !filter.isFilterMatch(
                            sourceFilterMap,
                            aggregatableNamedBudget.getFilterSet(),
                            /* isFilter= */ true)) {
                continue;
            }

            if (aggregatableNamedBudget.getNotFilterSet() != null
                    && !filter.isFilterMatch(
                            sourceFilterMap,
                            aggregatableNamedBudget.getNotFilterSet(),
                            /* isFilter= */ false)) {
                continue;
            }
            return Optional.of(aggregatableNamedBudget.getName());
        }
        return Optional.empty();
    }

    /**
     * Builder for {@link AggregatableAttributionTrigger}.
     */
    public static final class Builder {
        private final AggregatableAttributionTrigger mBuilding;

        public Builder() {
            mBuilding = new AggregatableAttributionTrigger();
        }

        /**
         * See {@link AggregatableAttributionTrigger#getTriggerData()}.
         */
        public Builder setTriggerData(List<AggregateTriggerData> triggerData) {
            mBuilding.mTriggerData = triggerData;
            return this;
        }

        /** See {@link AggregatableAttributionTrigger#getValueConfigs()}. */
        public Builder setValueConfigs(@Nullable List<AggregatableValuesConfig> mValueConfigs) {
            mBuilding.mValueConfigs = mValueConfigs;
            return this;
        }

        /** See {@link AggregatableAttributionTrigger#getAggregateDeduplicationKeys()}. */
        public Builder setAggregateDeduplicationKeys(List<AggregateDeduplicationKey> keys) {
            mBuilding.mAggregateDeduplicationKeys = Optional.of(keys);
            return this;
        }

        /** See {@link AggregatableAttributionTrigger#getNamedBudgets()} ()} ()}. */
        public Builder setNamedBudgets(List<AggregatableNamedBudget> namedBudgets) {
            mBuilding.mNamedBudgets = namedBudgets;
            return this;
        }

        /**
         * Build the {@link AggregatableAttributionTrigger}.
         */
        public AggregatableAttributionTrigger build() {
            return mBuilding;
        }
    }
}

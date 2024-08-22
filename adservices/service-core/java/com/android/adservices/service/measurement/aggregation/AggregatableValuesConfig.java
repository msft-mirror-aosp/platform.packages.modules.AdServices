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

import com.android.adservices.service.Flags;
import com.android.adservices.service.measurement.FilterMap;
import com.android.adservices.service.measurement.util.Filter;
import com.android.adservices.service.measurement.util.Filter.FilterContract;

import org.json.JSONArray;
import org.json.JSONException;
import org.json.JSONObject;

import java.util.HashMap;
import java.util.Iterator;
import java.util.List;
import java.util.Map;
import java.util.Objects;

/** POJO for AggregatableValuesConfig */
public class AggregatableValuesConfig {
    Map<String, AggregatableKeyValue> mValues;
    @Nullable List<FilterMap> mFilterSet;
    @Nullable List<FilterMap> mNotFilterSet;

    private AggregatableValuesConfig(AggregatableValuesConfig.Builder builder) {
        mValues = builder.mValues;
        mFilterSet = builder.mFilterSet;
        mNotFilterSet = builder.mNotFilterSet;
    }

    /** Returns a map of key and aggregatable_value's values. Example: {"campaignCounts": 1664 } */
    public Map<String, AggregatableKeyValue> getValues() {
        return mValues;
    }

    /** Returns AggregateKeyValue filters. Example: [{"category": ["filter_1", "filter_2"]}] */
    @Nullable
    public List<FilterMap> getFilterSet() {
        return mFilterSet;
    }

    /** Returns AggregateKeyValue not_filters. Example: [{"category": ["filter_2", "filter_3"]}] */
    @Nullable
    public List<FilterMap> getNotFilterSet() {
        return mNotFilterSet;
    }

    public static final class Builder {
        private Map<String, AggregatableKeyValue> mValues;
        @Nullable private List<FilterMap> mFilterSet;
        @Nullable private List<FilterMap> mNotFilterSet;

        public Builder(Map<String, AggregatableKeyValue> values) {
            mValues = values;
        }

        public Builder(JSONObject aggregateKeyValuesJson, Flags flags) throws JSONException {
            mValues =
                    buildValues(
                            aggregateKeyValuesJson.getJSONObject(
                                    AggregatableValuesConfigContract.VALUES));
            Filter filter = new Filter(flags);
            if (!aggregateKeyValuesJson.isNull(FilterContract.FILTERS)) {
                JSONArray filterSet =
                        Filter.maybeWrapFilters(aggregateKeyValuesJson, FilterContract.FILTERS);
                mFilterSet = filter.deserializeFilterSet(filterSet);
            }
            if (!aggregateKeyValuesJson.isNull(FilterContract.NOT_FILTERS)) {
                JSONArray notFilterSet =
                        Filter.maybeWrapFilters(aggregateKeyValuesJson, FilterContract.NOT_FILTERS);
                mNotFilterSet = filter.deserializeFilterSet(notFilterSet);
            }
        }

        private Map<String, AggregatableKeyValue> buildValues(JSONObject aggregateKeyValuesJson)
                throws JSONException {
            Iterator<String> valuesKeySet = aggregateKeyValuesJson.keys();
            Map<String, AggregatableKeyValue> mValues = new HashMap<>();
            while (valuesKeySet.hasNext()) {
                String key = valuesKeySet.next();
                AggregatableKeyValue aggregatableKeyValue =
                        new AggregatableKeyValue.Builder(aggregateKeyValuesJson.getInt(key))
                                .build();
                mValues.put(key, aggregatableKeyValue);
            }
            return mValues;
        }

        /** See {@link AggregatableValuesConfig#getFilterSet()} */
        public Builder setFilterSet(@Nullable List<FilterMap> filterSet) {
            mFilterSet = filterSet;
            return this;
        }

        /** See {@link AggregatableValuesConfig#getNotFilterSet()} */
        public Builder setNotFilterSet(@Nullable List<FilterMap> notFilterSet) {
            mNotFilterSet = notFilterSet;
            return this;
        }

        /** Build the {@link AggregatableValuesConfig}. */
        public AggregatableValuesConfig build() {
            Objects.requireNonNull(mValues);
            return new AggregatableValuesConfig(this);
        }
    }

    public interface AggregatableValuesConfigContract {
        String VALUES = "values";
    }
}

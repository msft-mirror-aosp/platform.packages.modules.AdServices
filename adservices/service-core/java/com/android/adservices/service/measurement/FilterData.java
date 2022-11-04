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

import org.json.JSONArray;
import org.json.JSONException;
import org.json.JSONObject;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;


/**
 * POJO for AggregatableAttributionFilterData.
 */
public class FilterData {

    private Map<String, List<String>> mAttributionFilterMap;

    FilterData() {
        mAttributionFilterMap = new HashMap<>();
    }

    @Override
    public boolean equals(Object obj) {
        if (!(obj instanceof FilterData)) {
            return false;
        }
        FilterData attributionFilterData = (FilterData) obj;
        return Objects.equals(mAttributionFilterMap, attributionFilterData.mAttributionFilterMap);
    }

    @Override
    public int hashCode() {
        return Objects.hash(mAttributionFilterMap);
    }

    /**
     * Returns the attribution filter map.
     */
    public Map<String, List<String>> getAttributionFilterMap() {
        return mAttributionFilterMap;
    }

    /**
     * Builder for {@link FilterData}.
     */
    public static final class Builder {
        private final FilterData mBuilding;

        public Builder() {
            mBuilding = new FilterData();
        }

        /**
         * See {@link FilterData#getAttributionFilterMap()}.
         */
        public Builder setAttributionFilterMap(Map<String, List<String>> attributionFilterMap) {
            mBuilding.mAttributionFilterMap = attributionFilterMap;
            return this;
        }

        /**
         * Builds FilterData from JSONObject.
         */
        public Builder buildFilterData(JSONObject jsonObject)
                throws JSONException {
            Map<String, List<String>> filterData = new HashMap<>();
            for (String key : jsonObject.keySet()) {
                JSONArray jsonArray = jsonObject.getJSONArray(key);
                List<String> filterDataList = new ArrayList<>();
                for (int i = 0; i < jsonArray.length(); i++) {
                    filterDataList.add(jsonArray.getString(i));
                }
                filterData.put(key, filterDataList);
            }
            mBuilding.mAttributionFilterMap = filterData;
            return this;
        }

        /**
         * Build the {@link FilterData}.
         */
        public FilterData build() {
            return mBuilding;
        }
    }
}

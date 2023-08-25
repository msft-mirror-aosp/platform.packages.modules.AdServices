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

import android.annotation.Nullable;

import com.android.adservices.LogUtil;

import org.json.JSONArray;
import org.json.JSONException;
import org.json.JSONObject;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.Iterator;
import java.util.List;
import java.util.Map;
import java.util.Objects;

/** POJO for FilterMap. */
public class FilterMap {

    private Map<String, List<String>> mAttributionFilterMap;
    private Map<String, FilterValue> mAttributionFilterMapWithLongValue;

    public static final String LOOKBACK_WINDOW = "_lookback_window";

    FilterMap() {
        mAttributionFilterMap = new HashMap<>();
        mAttributionFilterMapWithLongValue = new HashMap<>();
    }

    @Override
    public boolean equals(Object obj) {
        if (!(obj instanceof FilterMap)) {
            return false;
        }
        FilterMap attributionFilterMap = (FilterMap) obj;
        return Objects.equals(mAttributionFilterMap, attributionFilterMap.mAttributionFilterMap)
                && Objects.equals(
                        mAttributionFilterMapWithLongValue,
                        attributionFilterMap.mAttributionFilterMapWithLongValue);
    }

    @Override
    public int hashCode() {
        return Objects.hash(mAttributionFilterMap, mAttributionFilterMapWithLongValue);
    }

    /**
     * Returns the attribution filter map.
     *
     * @deprecated use {@link #getAttributionFilterMapWithLongValue()} instead.
     */
    @Deprecated
    public Map<String, List<String>> getAttributionFilterMap() {
        return mAttributionFilterMap;
    }

    /** Returns the attribution filter map with lookback window included. */
    public Map<String, FilterValue> getAttributionFilterMapWithLongValue() {
        return mAttributionFilterMapWithLongValue;
    }

    /**
     * Serializes the object into a {@link JSONObject}.
     *
     * @return serialized {@link JSONObject}.
     * @deprecated use {@link #serializeAsJsonV2} instead.
     */
    @Deprecated
    @Nullable
    public JSONObject serializeAsJson() {
        if (mAttributionFilterMap == null) {
            return null;
        }

        try {
            JSONObject result = new JSONObject();
            for (String key : mAttributionFilterMap.keySet()) {
                result.put(key, new JSONArray(mAttributionFilterMap.get(key)));
            }

            return result;
        } catch (JSONException e) {
            LogUtil.d(e, "Failed to serialize filtermap.");
            return null;
        }
    }

    /**
     * Serializes the object into a {@link JSONObject}.
     *
     * @return serialized {@link JSONObject}.
     */
    @Nullable
    public JSONObject serializeAsJsonV2() {
        if (mAttributionFilterMapWithLongValue == null) {
            return null;
        }

        try {
            JSONObject result = new JSONObject();
            for (String key : mAttributionFilterMapWithLongValue.keySet()) {
                FilterValue value = mAttributionFilterMapWithLongValue.get(key);
                switch (value.kind()) {
                    case LONG_VALUE:
                        result.put(key, value.longValue());
                        break;
                    case STRING_LIST_VALUE:
                        result.put(key, new JSONArray(value.stringListValue()));
                        break;
                }
            }
            return result;
        } catch (JSONException e) {
            LogUtil.d(e, "Failed to serialize filtermap.");
            return null;
        }
    }

    /** Builder for {@link FilterMap}. */
    public static final class Builder {
        private final FilterMap mBuilding;

        public Builder() {
            mBuilding = new FilterMap();
        }

        /** See {@link FilterMap#getAttributionFilterMapWithLongValue()}. */
        public Builder setAttributionFilterMapWithLongValue(
                Map<String, FilterValue> attributionFilterMap) {
            mBuilding.mAttributionFilterMapWithLongValue = attributionFilterMap;
            return this;
        }

        /**
         * See {@link FilterMap#getAttributionFilterMap()}.
         *
         * @deprecated use {@link #setAttributionFilterMapWithLongValue} instead.
         */
        @Deprecated
        public Builder setAttributionFilterMap(Map<String, List<String>> attributionFilterMap) {
            mBuilding.mAttributionFilterMap = attributionFilterMap;
            return this;
        }

        /**
         * Builds FilterMap from JSONObject.
         *
         * @deprecated use {@link #buildFilterDataV2} instead.
         */
        @Deprecated
        public Builder buildFilterData(JSONObject jsonObject) throws JSONException {
            Map<String, List<String>> filterMap = new HashMap<>();
            Iterator<String> keys = jsonObject.keys();
            while (keys.hasNext()) {
                String key = keys.next();
                JSONArray jsonArray = jsonObject.getJSONArray(key);
                List<String> filterMapList = new ArrayList<>();
                for (int i = 0; i < jsonArray.length(); i++) {
                    filterMapList.add(jsonArray.getString(i));
                }
                filterMap.put(key, filterMapList);
            }
            mBuilding.mAttributionFilterMap = filterMap;
            return this;
        }

        /** Builds FilterMap from JSONObject with long filter values. */
        public Builder buildFilterDataV2(JSONObject jsonObject) throws JSONException {
            Map<String, FilterValue> filterMap = new HashMap<>();
            Iterator<String> keys = jsonObject.keys();
            while (keys.hasNext()) {
                String key = keys.next();
                if (LOOKBACK_WINDOW.equals(key)) {
                    String value = jsonObject.getString(key);
                    try {
                        filterMap.put(key, FilterValue.ofLong(Long.parseLong(value)));
                    } catch (NumberFormatException e) {
                        throw new JSONException(
                                String.format(
                                        "Failed to parse long value: %s for key: %s", value, key));
                    }
                } else {
                    JSONArray jsonArray = jsonObject.getJSONArray(key);
                    List<String> filterMapList = new ArrayList<>();
                    for (int i = 0; i < jsonArray.length(); i++) {
                        filterMapList.add(jsonArray.getString(i));
                    }
                    filterMap.put(key, FilterValue.ofStringList(filterMapList));
                }
            }
            mBuilding.mAttributionFilterMapWithLongValue = filterMap;
            return this;
        }

        /** Build the {@link FilterMap}. */
        public FilterMap build() {
            return mBuilding;
        }
    }
}

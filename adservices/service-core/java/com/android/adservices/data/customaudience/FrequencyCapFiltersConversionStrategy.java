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

package com.android.adservices.data.customaudience;

import android.adservices.common.AdData;
import android.adservices.common.AdFilters;
import android.adservices.common.FrequencyCapFilters;

import com.android.adservices.data.common.DBAdData;

import org.json.JSONException;
import org.json.JSONObject;

/** Strategy for doing optional feature-flagged parts of the frequency cap filters conversion */
public interface FrequencyCapFiltersConversionStrategy {
    /**
     * Fills a part of the given {@link JSONObject} with the data from {@link
     * DBAdData#getAdCounterKeys()}.
     */
    void toJsonAdCounterKeys(DBAdData adData, JSONObject toReturn) throws JSONException;

    /**
     * Fills a part of the given {@link JSONObject} with the data from {@link
     * AdFilters#getFrequencyCapFilters()}.
     */
    void toJsonFilters(FrequencyCapFilters frequencyCapFilters, JSONObject toReturn)
            throws JSONException;

    /** Deserialize {@link DBAdData#getAdCounterKeys()} from {@link JSONObject}. */
    void fromJsonAdCounterKeys(JSONObject json, DBAdData.Builder adDataBuilder)
            throws JSONException;

    /** Deserialize {@link FrequencyCapFilters} from {@link JSONObject}. */
    void fromJsonFilters(JSONObject json, AdFilters.Builder builder) throws JSONException;

    /**
     * Parse part of parcelable {@link AdData} to storage model {@link DBAdData} with the data from
     * {@link AdData#getAdCounterKeys()}
     */
    void fromServiceObjectAdCounterKeys(AdData parcelable, DBAdData.Builder adDataBuilder);

    /**
     * Parse part of parcelable {@link AdData} to storage model {@link DBAdData} with the data from
     * {@link AdFilters#getFrequencyCapFilters()}
     */
    void fromServiceObjectFilters(AdData parcelable, AdFilters.Builder builder);
}

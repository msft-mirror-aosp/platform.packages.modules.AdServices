/*
 * Copyright (C) 2023 The Android Open Source Project
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

package com.android.adservices.service.customaudience;

import static android.adservices.common.AdFilters.APP_INSTALL_FIELD_NAME;
import static android.adservices.common.AdFilters.FREQUENCY_CAP_FIELD_NAME;

import static com.android.adservices.service.customaudience.CustomAudienceUpdatableDataReader.AD_COUNTERS_KEY;
import static com.android.adservices.service.customaudience.CustomAudienceUpdatableDataReader.AD_FILTERS_KEY;

import android.adservices.common.AdFilters;
import android.adservices.common.AppInstallFilters;
import android.adservices.common.FrequencyCapFilters;

import com.android.adservices.LoggerFactory;
import com.android.adservices.data.common.DBAdData;

import org.json.JSONArray;
import org.json.JSONException;
import org.json.JSONObject;

import java.util.HashSet;
import java.util.Set;
/** Factory for ReadFiltersFromJsonStrategys */
public class ReadFiltersFromJsonStrategyFactory {
    private static final LoggerFactory.Logger sLogger = LoggerFactory.getFledgeLogger();

    private static class FilteringEnabledStrategy implements ReadFiltersFromJsonStrategy {
        private final ReadFrequencyCapFiltersFromJsonStrategy
                mReadFrequencyCapFiltersFromJsonStrategy;
        private final ReadAppInstallFiltersFromJsonStrategy mReadAppInstallFiltersFromJsonStrategy;

        FilteringEnabledStrategy(
                boolean frequencyCapFilteringEnabled, boolean appInstallFilteringEnabled) {
            if (frequencyCapFilteringEnabled) {
                mReadFrequencyCapFiltersFromJsonStrategy =
                        new FrequencyCapFilteringEnabledStrategy();
            } else {
                mReadFrequencyCapFiltersFromJsonStrategy =
                        new FrequencyCapFilteringDisabledStrategy();
            }

            if (appInstallFilteringEnabled) {
                mReadAppInstallFiltersFromJsonStrategy = new AppInstallFilteringEnabledStrategy();
            } else {
                mReadAppInstallFiltersFromJsonStrategy = new AppInstallFilteringDisabledStrategy();
            }
        }

        /**
         * Adds filtering fields to the provided AdData builder.
         *
         * @param adDataBuilder the AdData builder to modify.
         * @param adDataJsonObj the AdData JSON to extract from
         * @throws JSONException if the key is found but the schema is incorrect
         * @throws NullPointerException if the key found by the field is null
         */
        @Override
        public void readFilters(DBAdData.Builder adDataBuilder, JSONObject adDataJsonObj)
                throws JSONException, NullPointerException, IllegalArgumentException {
            mReadFrequencyCapFiltersFromJsonStrategy.readAdCounterKeys(
                    adDataJsonObj, adDataBuilder);
            AdFilters adFilters = null;
            if (adDataJsonObj.has(AD_FILTERS_KEY)) {
                AdFilters.Builder builder = new AdFilters.Builder();
                JSONObject adFiltersObject = adDataJsonObj.getJSONObject(AD_FILTERS_KEY);
                mReadFrequencyCapFiltersFromJsonStrategy.readFrequencyCapFilters(
                        adFiltersObject, builder);
                mReadAppInstallFiltersFromJsonStrategy.readAppInstallFilters(
                        adFiltersObject, builder);
                adFilters = builder.build();
            }
            adDataBuilder.setAdFilters(adFilters);
        }
    }

    private static class FilteringDisabledStrategy implements ReadFiltersFromJsonStrategy {
        /**
         * Does nothing.
         *
         * @param adDataBuilder unused
         * @param adDataJsonObj unused
         */
        @Override
        public void readFilters(DBAdData.Builder adDataBuilder, JSONObject adDataJsonObj) {}
    }

    private static class FrequencyCapFilteringEnabledStrategy
            implements ReadFrequencyCapFiltersFromJsonStrategy {

        @Override
        public void readAdCounterKeys(JSONObject json, DBAdData.Builder adDataBuilder)
                throws JSONException {
            Set<Integer> adCounterKeys = new HashSet<>();
            if (json.has(AD_COUNTERS_KEY)) {
                JSONArray counterKeys = json.getJSONArray(AD_COUNTERS_KEY);
                for (int i = 0; i < counterKeys.length(); i++) {
                    adCounterKeys.add(counterKeys.getInt(i));
                }
            }
            adDataBuilder.setAdCounterKeys(adCounterKeys);
        }

        @Override
        public void readFrequencyCapFilters(JSONObject json, AdFilters.Builder builder)
                throws JSONException {
            if (json.has(FREQUENCY_CAP_FIELD_NAME)) {
                builder.setFrequencyCapFilters(
                        FrequencyCapFilters.fromJson(json.getJSONObject(FREQUENCY_CAP_FIELD_NAME)));
            }
        }
    }

    private static class FrequencyCapFilteringDisabledStrategy
            implements ReadFrequencyCapFiltersFromJsonStrategy {

        @Override
        public void readAdCounterKeys(JSONObject json, DBAdData.Builder adDataBuilder)
                throws JSONException {
            sLogger.v("Frequency cap filtering is disabled, reading fcap filters is a no op");
        }

        @Override
        public void readFrequencyCapFilters(JSONObject json, AdFilters.Builder builder)
                throws JSONException {
            sLogger.v("Frequency cap filtering is disabled, reading fcap filters is a no op");
        }
    }

    private static class AppInstallFilteringEnabledStrategy
            implements ReadAppInstallFiltersFromJsonStrategy {

        @Override
        public void readAppInstallFilters(JSONObject json, AdFilters.Builder builder)
                throws JSONException {
            if (json.has(APP_INSTALL_FIELD_NAME)) {
                builder.setAppInstallFilters(
                        AppInstallFilters.fromJson(json.getJSONObject(APP_INSTALL_FIELD_NAME)));
            }
        }
    }

    private static class AppInstallFilteringDisabledStrategy
            implements ReadAppInstallFiltersFromJsonStrategy {

        @Override
        public void readAppInstallFilters(JSONObject json, AdFilters.Builder builder)
                throws JSONException {
            sLogger.v("App install filtering is disabled, reading app install filters is a no op");
        }
    }

    /**
     * Returns the appropriate ReadFiltersFromJsonStrategy based whether filtering is enabled
     *
     * @param frequencyCapFilteringEnabled Should be true if frequency cap filtering is enabled.
     * @param appInstallFilteringEnabled Should be true if app install filtering is enabled.
     * @return An implementation of ReadFiltersFromJsonStrategy
     */
    public static ReadFiltersFromJsonStrategy getStrategy(
            boolean frequencyCapFilteringEnabled, boolean appInstallFilteringEnabled) {
        if (frequencyCapFilteringEnabled || appInstallFilteringEnabled) {
            return new FilteringEnabledStrategy(
                    frequencyCapFilteringEnabled, appInstallFilteringEnabled);
        }
        return new FilteringDisabledStrategy();
    }
}

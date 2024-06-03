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

package com.android.adservices.data.customaudience;

import static android.adservices.common.AdFilters.APP_INSTALL_FIELD_NAME;
import static android.adservices.common.AdFilters.FREQUENCY_CAP_FIELD_NAME;

import android.adservices.common.AdData;
import android.adservices.common.AdFilters;
import android.adservices.common.AppInstallFilters;
import android.adservices.common.FrequencyCapFilters;
import android.net.Uri;

import androidx.annotation.NonNull;

import com.android.adservices.LoggerFactory;
import com.android.adservices.data.common.DBAdData;
import com.android.adservices.data.common.FledgeRoomConverters;

import org.json.JSONArray;
import org.json.JSONException;
import org.json.JSONObject;

import java.util.ArrayList;
import java.util.Collections;
import java.util.HashSet;
import java.util.List;
import java.util.Set;

/** Factory for AdDataConversionStrategys */
public class AdDataConversionStrategyFactory {
    private static final LoggerFactory.Logger sLogger = LoggerFactory.getFledgeLogger();
    private static final String RENDER_URI_FIELD_NAME = "renderUri";
    private static final String METADATA_FIELD_NAME = "metadata";
    private static final String AD_COUNTER_KEYS_FIELD_NAME = "adCounterKeys";
    private static final String AD_FILTERS_FIELD_NAME = "adFilters";
    private static final String AD_RENDER_ID_FIELD_NAME = "adRenderId";

    private static class FrequencyCapConversionImpl
            implements FrequencyCapFiltersConversionStrategy {
        @Override
        public void toJsonAdCounterKeys(DBAdData adData, JSONObject toReturn) throws JSONException {
            if (!adData.getAdCounterKeys().isEmpty()) {
                JSONArray jsonCounterKeys = new JSONArray(adData.getAdCounterKeys());
                toReturn.put(AD_COUNTER_KEYS_FIELD_NAME, jsonCounterKeys);
            }
        }

        @Override
        public void toJsonFilters(FrequencyCapFilters frequencyCapFilters, JSONObject toReturn)
                throws JSONException {
            if (frequencyCapFilters != null) {
                toReturn.put(FREQUENCY_CAP_FIELD_NAME, frequencyCapFilters.toJson());
            }
        }

        @Override
        public void fromJsonAdCounterKeys(JSONObject json, DBAdData.Builder adDataBuilder)
                throws JSONException {
            Set<Integer> adCounterKeys = new HashSet<>();
            if (json.has(AD_COUNTER_KEYS_FIELD_NAME)) {
                JSONArray counterKeys = json.getJSONArray(AD_COUNTER_KEYS_FIELD_NAME);
                for (int i = 0; i < counterKeys.length(); i++) {
                    adCounterKeys.add(counterKeys.getInt(i));
                }
            }
            adDataBuilder.setAdCounterKeys(adCounterKeys);
        }

        @Override
        public void fromJsonFilters(JSONObject json, AdFilters.Builder builder)
                throws JSONException {
            if (json.has(FREQUENCY_CAP_FIELD_NAME)) {
                builder.setFrequencyCapFilters(
                        FrequencyCapFilters.fromJson(json.getJSONObject(FREQUENCY_CAP_FIELD_NAME)));
            }
        }

        @Override
        public void fromServiceObjectAdCounterKeys(
                AdData parcelable, DBAdData.Builder adDataBuilder) {
            adDataBuilder.setAdCounterKeys(parcelable.getAdCounterKeys());
        }

        @Override
        public void fromServiceObjectFilters(AdData parcelable, AdFilters.Builder builder) {
            builder.setFrequencyCapFilters(parcelable.getAdFilters().getFrequencyCapFilters());
        }
    }

    private static class FrequencyCapConversionNoOp
            implements FrequencyCapFiltersConversionStrategy {

        @Override
        public void toJsonAdCounterKeys(DBAdData adData, JSONObject toReturn) throws JSONException {
            sLogger.v("Frequency cap filtering is disabled, so toJSON is a no op");
        }

        @Override
        public void toJsonFilters(FrequencyCapFilters frequencyCapFilters, JSONObject toReturn)
                throws JSONException {
            sLogger.v("Frequency cap filtering is disabled, so toJSON is a no op");
        }

        @Override
        public void fromJsonAdCounterKeys(JSONObject json, DBAdData.Builder adDataBuilder) {
            sLogger.v("Frequency cap filtering is disabled, so fromJSON is a no op");
        }

        @Override
        public void fromJsonFilters(JSONObject json, AdFilters.Builder builder)
                throws JSONException {
            sLogger.v("Frequency cap filtering is disabled, so fromJSON is a no op");
        }

        @Override
        public void fromServiceObjectAdCounterKeys(
                AdData parcelable, DBAdData.Builder adDataBuilder) {
            sLogger.v("Frequency cap filtering is disabled, so fromServiceObject is a no op");
        }

        @Override
        public void fromServiceObjectFilters(AdData parcelable, AdFilters.Builder builder) {
            sLogger.v("Frequency cap filtering is disabled, so fromServiceObject is a no op");
        }
    }

    private static class AppInstallConversionImpl implements AppInstallFiltersConversionStrategy {

        @Override
        public void toJson(AppInstallFilters appInstallFilters, JSONObject toReturn)
                throws JSONException {
            if (appInstallFilters != null) {
                toReturn.put(APP_INSTALL_FIELD_NAME, appInstallFilters.toJson());
            }
        }

        @Override
        public void fromJson(JSONObject json, AdFilters.Builder builder) throws JSONException {
            if (json.has(APP_INSTALL_FIELD_NAME)) {
                builder.setAppInstallFilters(
                        AppInstallFilters.fromJson(json.getJSONObject(APP_INSTALL_FIELD_NAME)));
            }
        }

        @Override
        public void fromServiceObject(AdData parcelable, AdFilters.Builder builder) {
            builder.setAppInstallFilters(parcelable.getAdFilters().getAppInstallFilters());
        }
    }

    private static class AppInstallConversionNoOp implements AppInstallFiltersConversionStrategy {
        @Override
        public void toJson(AppInstallFilters appInstallFilters, JSONObject toReturn)
                throws JSONException {
            sLogger.v("App install cap filtering is disabled, so toJSON is a no op");
        }

        @Override
        public void fromJson(JSONObject json, AdFilters.Builder builder) throws JSONException {
            sLogger.v("App install filtering is disabled, so fromJSON is a no op");
        }

        @Override
        public void fromServiceObject(AdData parcelable, AdFilters.Builder builder) {
            sLogger.v("App install filtering is disabled, so fromServiceObject is a no op");
        }
    }

    private static class FilteringEnabledConversionStrategy
            implements AdDataOptionalConversionStrategy {
        private final FrequencyCapFiltersConversionStrategy mFrequencyCapFiltersConversionStrategy;
        private final AppInstallFiltersConversionStrategy mAppInstallFiltersConversionStrategy;

        FilteringEnabledConversionStrategy(
                boolean frequencyCapFilteringEnabled, boolean appInstallFilteringEnabled) {
            if (frequencyCapFilteringEnabled) {
                mFrequencyCapFiltersConversionStrategy = new FrequencyCapConversionImpl();
            } else {
                mFrequencyCapFiltersConversionStrategy = new FrequencyCapConversionNoOp();
            }

            if (appInstallFilteringEnabled) {
                mAppInstallFiltersConversionStrategy = new AppInstallConversionImpl();
            } else {
                mAppInstallFiltersConversionStrategy = new AppInstallConversionNoOp();
            }
        }

        public void toJson(@NonNull DBAdData adData, @NonNull JSONObject toReturn)
                throws JSONException {
            mFrequencyCapFiltersConversionStrategy.toJsonAdCounterKeys(adData, toReturn);
            if (adData.getAdFilters() != null) {
                JSONObject adFilterWrapper = new JSONObject();
                mFrequencyCapFiltersConversionStrategy.toJsonFilters(
                        adData.getAdFilters().getFrequencyCapFilters(), adFilterWrapper);
                mAppInstallFiltersConversionStrategy.toJson(
                        adData.getAdFilters().getAppInstallFilters(), adFilterWrapper);
                toReturn.put(AD_FILTERS_FIELD_NAME, adFilterWrapper);
            }
        }

        public void fromJson(@NonNull JSONObject json, @NonNull DBAdData.Builder adDataBuilder)
                throws JSONException {
            mFrequencyCapFiltersConversionStrategy.fromJsonAdCounterKeys(json, adDataBuilder);
            AdFilters adFilters = null;
            if (json.has(AD_FILTERS_FIELD_NAME)) {
                AdFilters.Builder builder = new AdFilters.Builder();
                JSONObject adFiltersObject = json.getJSONObject(AD_FILTERS_FIELD_NAME);
                mFrequencyCapFiltersConversionStrategy.fromJsonFilters(adFiltersObject, builder);
                mAppInstallFiltersConversionStrategy.fromJson(adFiltersObject, builder);
                adFilters = builder.build();
            }
            adDataBuilder.setAdFilters(adFilters);
        }

        @Override
        public void fromServiceObject(
                @NonNull AdData parcelable, @NonNull DBAdData.Builder adDataBuilder) {
            mFrequencyCapFiltersConversionStrategy.fromServiceObjectAdCounterKeys(
                    parcelable, adDataBuilder);
            if (parcelable.getAdFilters() != null) {
                AdFilters adFilters;
                AdFilters.Builder adFiltersBuilder = new AdFilters.Builder();
                mAppInstallFiltersConversionStrategy.fromServiceObject(
                        parcelable, adFiltersBuilder);
                mFrequencyCapFiltersConversionStrategy.fromServiceObjectFilters(
                        parcelable, adFiltersBuilder);
                adFilters = adFiltersBuilder.build();
                sLogger.v("Final Ad Filters in fromServiceObject: %s", adFilters);
                adDataBuilder.setAdFilters(adFilters);
            }
        }
    }

    private static class AdRenderIdEnabledConversionStrategy
            implements AdDataOptionalConversionStrategy {
        public void toJson(@NonNull DBAdData adData, @NonNull JSONObject toReturn)
                throws JSONException {
            if (adData.getAdRenderId() != null) {
                toReturn.put(AD_RENDER_ID_FIELD_NAME, adData.getAdRenderId());
            }
        }

        public void fromJson(@NonNull JSONObject json, @NonNull DBAdData.Builder adDataBuilder)
                throws JSONException {
            if (json.has(AD_RENDER_ID_FIELD_NAME)) {
                adDataBuilder.setAdRenderId(json.getString(AD_RENDER_ID_FIELD_NAME));
            }
        }

        @Override
        public void fromServiceObject(
                @NonNull AdData parcelable, @NonNull DBAdData.Builder adDataBuilder) {
            sLogger.v("Setting ad render id " + parcelable.getAdRenderId());
            adDataBuilder.setAdRenderId(parcelable.getAdRenderId());
        }
    }

    /**
     * Conversion strategy with no optional feature enabled. This is the baseline of all
     * conversions.
     */
    private static class BaseConversionStrategy implements AdDataConversionStrategy {
        /**
         * Serialize {@link DBAdData} to {@link JSONObject}, but ignore filter fields.
         *
         * @param adData the {@link DBAdData} object to serialize
         * @return the json serialization of the AdData object
         */
        public JSONObject toJson(DBAdData adData) throws JSONException {
            return new org.json.JSONObject()
                    .put(
                            RENDER_URI_FIELD_NAME,
                            FledgeRoomConverters.serializeUri(adData.getRenderUri()))
                    .put(METADATA_FIELD_NAME, adData.getMetadata());
        }

        /**
         * Deserialize {@link DBAdData} to {@link JSONObject} but ignore filter fields.
         *
         * @param json the {@link JSONObject} object to deserialize
         * @return the {@link DBAdData} deserialized from the json
         */
        public DBAdData.Builder fromJson(JSONObject json) throws JSONException {
            String renderUriString = json.getString(RENDER_URI_FIELD_NAME);
            String metadata = json.getString(METADATA_FIELD_NAME);
            Uri renderUri = FledgeRoomConverters.deserializeUri(renderUriString);
            return new DBAdData.Builder().setRenderUri(renderUri).setMetadata(metadata);
        }

        /**
         * Parse parcelable {@link AdData} to storage model {@link DBAdData}.
         *
         * @param parcelable the service model.
         * @return storage model
         */
        @NonNull
        @Override
        public DBAdData.Builder fromServiceObject(@NonNull AdData parcelable) {
            return new DBAdData.Builder()
                    .setRenderUri(parcelable.getRenderUri())
                    .setMetadata(parcelable.getMetadata())
                    .setAdCounterKeys(Collections.emptySet());
        }
    }

    private static class CompositeConversionStrategy implements AdDataConversionStrategy {
        private final AdDataConversionStrategy mBaseStrategy;
        private final List<AdDataOptionalConversionStrategy> mOptionalStrategies;

        CompositeConversionStrategy(AdDataConversionStrategy baseStrategy) {
            this.mBaseStrategy = baseStrategy;
            this.mOptionalStrategies = new ArrayList<>();
        }

        @Override
        public JSONObject toJson(DBAdData adData) throws JSONException {
            JSONObject result = mBaseStrategy.toJson(adData);
            for (AdDataOptionalConversionStrategy optionalStrategy : mOptionalStrategies) {
                optionalStrategy.toJson(adData, result);
            }
            return result;
        }

        @Override
        public DBAdData.Builder fromJson(JSONObject json) throws JSONException {
            DBAdData.Builder result = mBaseStrategy.fromJson(json);
            for (AdDataOptionalConversionStrategy optionalStrategy : mOptionalStrategies) {
                optionalStrategy.fromJson(json, result);
            }
            return result;
        }

        @NonNull
        @Override
        public DBAdData.Builder fromServiceObject(@NonNull AdData parcelable) {
            DBAdData.Builder result = mBaseStrategy.fromServiceObject(parcelable);
            for (AdDataOptionalConversionStrategy optionalStrategy : mOptionalStrategies) {
                optionalStrategy.fromServiceObject(parcelable, result);
            }
            return result;
        }

        @NonNull
        public CompositeConversionStrategy composeWith(AdDataOptionalConversionStrategy strategy) {
            mOptionalStrategies.add(strategy);
            return this;
        }
    }

    /**
     * Returns the appropriate AdDataConversionStrategy based whether which kind of filtering is
     * enabled
     *
     * @param frequencyCapFilteringEnabled denotes if frequency cap filtering is enabled
     * @param appInstallFilteringEnabled denotes if app install filtering is enabled
     * @return An implementation of AdDataConversionStrategy
     */
    public static AdDataConversionStrategy getAdDataConversionStrategy(
            boolean frequencyCapFilteringEnabled,
            boolean appInstallFilteringEnabled,
            boolean adRenderIdEnabled) {

        CompositeConversionStrategy result =
                new CompositeConversionStrategy(new BaseConversionStrategy());

        if (frequencyCapFilteringEnabled || appInstallFilteringEnabled) {
            sLogger.v("Adding Filtering Conversion Strategy to Composite Conversion Strategy");
            result.composeWith(
                    new FilteringEnabledConversionStrategy(
                            frequencyCapFilteringEnabled, appInstallFilteringEnabled));
        }
        if (adRenderIdEnabled) {
            sLogger.v("Adding Ad Render Conversion Strategy to Composite Conversion Strategy");
            result.composeWith(new AdRenderIdEnabledConversionStrategy());
        }
        return result;
    }
}

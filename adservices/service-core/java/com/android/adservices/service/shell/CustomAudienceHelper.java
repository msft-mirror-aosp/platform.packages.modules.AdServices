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

package com.android.adservices.service.shell;

import android.adservices.common.AdFilters;
import android.adservices.common.AdSelectionSignals;
import android.adservices.common.AdTechIdentifier;
import android.net.Uri;

import androidx.annotation.NonNull;

import com.android.adservices.data.common.DBAdData;
import com.android.adservices.data.customaudience.DBCustomAudience;
import com.android.adservices.data.customaudience.DBTrustedBiddingData;

import com.google.common.collect.ImmutableList;
import com.google.common.collect.ImmutableSet;

import org.json.JSONArray;
import org.json.JSONException;
import org.json.JSONObject;

import java.time.Instant;
import java.util.List;
import java.util.Objects;
import java.util.Set;

final class CustomAudienceHelper {
    private static final String NAME = "name";
    private static final String OWNER = "owner";
    private static final String BUYER = "buyer";
    private static final String IS_DEBUGGABLE = "is_debuggable";
    private static final String ACTIVATION_TIME = "activation_time";
    private static final String CREATION_TIME = "creation_time";
    private static final String EXPIRATION_TIME = "expiration_time";
    private static final String UPDATED_TIME = "updated_time";
    private static final String BIDDING_LOGIC_URI = "bidding_logic_uri";
    private static final String USER_BIDDING_SIGNALS = "user_bidding_signals";
    private static final String TRUSTED_BIDDING_DATA = "trusted_bidding_data";
    private static final String ADS = "ads";
    private static final String ADS_URI = "uri";
    private static final String ADS_AD_COUNTER_KEYS = "ad_counter_keys";
    private static final String ADS_AD_FILTERS = "ad_filters";
    private static final String ADS_KEYS = "keys";
    private static final String AD_AD_RENDER_URI = "render_uri";
    private static final String AD_METADATA = "metadata";
    private static final String AD_AD_RENDER_ID = "ad_render_id";

    static JSONObject toJson(@NonNull DBCustomAudience customAudience) throws JSONException {
        return new JSONObject()
                .put(NAME, customAudience.getName())
                .put(OWNER, customAudience.getOwner())
                .put(BUYER, customAudience.getBuyer())
                .put(IS_DEBUGGABLE, customAudience.isDebuggable())
                .put(CREATION_TIME, customAudience.getCreationTime())
                .put(ACTIVATION_TIME, customAudience.getActivationTime())
                .put(EXPIRATION_TIME, customAudience.getExpirationTime())
                .put(UPDATED_TIME, customAudience.getLastAdsAndBiddingDataUpdatedTime())
                .put(BIDDING_LOGIC_URI, customAudience.getBiddingLogicUri().toString())
                .put(USER_BIDDING_SIGNALS, customAudience.getUserBiddingSignals())
                .put(
                        TRUSTED_BIDDING_DATA,
                        getJsonFromTrustedBiddingData(
                                Objects.requireNonNull(customAudience.getTrustedBiddingData())))
                .put(ADS, getJsonArrayFromAdsList(Objects.requireNonNull(customAudience.getAds())));
    }

    private static JSONObject getJsonFromTrustedBiddingData(DBTrustedBiddingData trustedBiddingData)
            throws JSONException {
        JSONObject jsonObject =
                new JSONObject().put(ADS_URI, trustedBiddingData.getUri().toString());
        JSONArray keys = new JSONArray();
        trustedBiddingData.getKeys().forEach(keys::put);
        return jsonObject.put(ADS_KEYS, keys);
    }

    private static JSONArray getJsonArrayFromAdsList(List<DBAdData> ads) throws JSONException {
        JSONArray array = new JSONArray();
        for (DBAdData ad : ads) {
            array.put(getJsonFromAd(ad));
        }
        return array;
    }

    private static JSONObject getJsonFromAd(DBAdData adData) throws JSONException {
        JSONObject jsonObject =
                new JSONObject()
                        .put(AD_AD_RENDER_URI, adData.getRenderUri())
                        .put(AD_AD_RENDER_ID, adData.getAdRenderId())
                        .put(AD_METADATA, adData.getMetadata())
                        .put(
                                ADS_AD_COUNTER_KEYS,
                                getJsonArrayFromIntegerSet(adData.getAdCounterKeys()));
        if (adData.getAdFilters() != null) {
            jsonObject.put(ADS_AD_FILTERS, adData.getAdFilters().toJson().toString());
        }
        return jsonObject;
    }

    static DBCustomAudience fromJson(@NonNull JSONObject jsonObject) throws JSONException {
        return new DBCustomAudience.Builder()
                .setName(jsonObject.getString(NAME))
                .setOwner(jsonObject.getString(OWNER))
                .setBuyer(AdTechIdentifier.fromString(jsonObject.getString(BUYER)))
                .setDebuggable(jsonObject.getBoolean(IS_DEBUGGABLE))
                .setCreationTime(Instant.parse(jsonObject.getString(CREATION_TIME)))
                .setActivationTime(Instant.parse(jsonObject.getString(ACTIVATION_TIME)))
                .setExpirationTime(Instant.parse(jsonObject.getString(EXPIRATION_TIME)))
                .setLastAdsAndBiddingDataUpdatedTime(
                        Instant.parse(jsonObject.getString(UPDATED_TIME)))
                .setBiddingLogicUri(Uri.parse(jsonObject.getString(BIDDING_LOGIC_URI)))
                .setUserBiddingSignals(
                        AdSelectionSignals.fromString(jsonObject.getString(USER_BIDDING_SIGNALS)))
                .setTrustedBiddingData(
                        getTrustedBiddingDataFromJson(
                                jsonObject.getJSONObject(TRUSTED_BIDDING_DATA)))
                .setAds(getAdsFromJsonArray(jsonObject.getJSONArray(ADS)))
                .build();
    }

    private static DBTrustedBiddingData getTrustedBiddingDataFromJson(JSONObject jsonObject)
            throws JSONException {
        return new DBTrustedBiddingData.Builder()
                .setUri(Uri.parse(jsonObject.getString(ADS_URI)))
                .setKeys(getStringsFromJsonArray(jsonObject.getJSONArray(ADS_KEYS)))
                .build();
    }

    private static ImmutableList<DBAdData> getAdsFromJsonArray(JSONArray jsonArray)
            throws JSONException {
        ImmutableList.Builder<DBAdData> builder = ImmutableList.builder();
        for (int i = 0; i < jsonArray.length(); i++) {
            builder.add(getAdFromJson(jsonArray.getJSONObject(i)));
        }
        return builder.build();
    }

    private static DBAdData getAdFromJson(JSONObject jsonObject) throws JSONException {
        DBAdData.Builder builder =
                new DBAdData.Builder()
                        .setRenderUri(Uri.parse(jsonObject.getString(AD_AD_RENDER_URI)))
                        .setMetadata(jsonObject.getString(AD_METADATA));
        if (jsonObject.has(AD_AD_RENDER_ID)) {
            builder.setAdRenderId(jsonObject.getString(AD_AD_RENDER_ID));
        }
        if (jsonObject.has(ADS_AD_COUNTER_KEYS)) {
            builder.setAdCounterKeys(
                    getIntegersFromJsonArray(jsonObject.getJSONArray(ADS_AD_COUNTER_KEYS)));
        }
        if (jsonObject.has(ADS_AD_FILTERS)) {
            builder.setAdFilters(
                    AdFilters.fromJson(new JSONObject(jsonObject.getString(ADS_AD_FILTERS))));
        }
        return builder.build();
    }

    private static ImmutableList<String> getStringsFromJsonArray(JSONArray jsonArray)
            throws JSONException {
        ImmutableList.Builder<String> builder = ImmutableList.builder();
        for (int i = 0; i < jsonArray.length(); i++) {
            builder.add(jsonArray.getString(i));
        }
        return builder.build();
    }

    private static JSONArray getJsonArrayFromIntegerSet(Set<Integer> integerSet) {
        JSONArray jsonArray = new JSONArray();
        for (Integer i : integerSet) {
            jsonArray.put(i);
        }
        return jsonArray;
    }

    private static ImmutableSet<Integer> getIntegersFromJsonArray(@NonNull JSONArray jsonArray)
            throws JSONException {
        ImmutableSet.Builder<Integer> builder = ImmutableSet.builder();
        for (int i = 0; i < jsonArray.length(); i++) {
            builder.add(jsonArray.getInt(i));
        }
        return builder.build();
    }
}

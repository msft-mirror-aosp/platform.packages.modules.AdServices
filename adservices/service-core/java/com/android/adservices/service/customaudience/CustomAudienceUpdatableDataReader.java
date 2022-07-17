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

package com.android.adservices.service.customaudience;

import android.net.Uri;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;

import com.android.adservices.LogUtil;
import com.android.adservices.data.common.DBAdData;
import com.android.adservices.data.customaudience.DBTrustedBiddingData;

import org.json.JSONArray;
import org.json.JSONException;
import org.json.JSONObject;

import java.util.ArrayList;
import java.util.List;
import java.util.Objects;

/**
 * A parser and validator for a JSON response that is fetched during the Custom Audience background
 * fetch process.
 */
public class CustomAudienceUpdatableDataReader {
    public static final String USER_BIDDING_SIGNALS_KEY = "user_bidding_signals";
    public static final String TRUSTED_BIDDING_DATA_KEY = "trusted_bidding_data";
    public static final String TRUSTED_BIDDING_URI_KEY = "trusted_bidding_uri";
    public static final String TRUSTED_BIDDING_KEYS_KEY = "trusted_bidding_keys";
    public static final String ADS_KEY = "ads";
    public static final String RENDER_URI_KEY = "render_uri";
    public static final String METADATA_KEY = "metadata";

    private static final String SUCCESS_VALID_LOG_FORMAT = "%s Found valid %s in JSON response";
    private static final String FIELD_NOT_FOUND_LOG_FORMAT = "%s %s not found in JSON response";
    private static final String SKIP_INVALID_JSON_TYPE_LOG_FORMAT =
            "%s Invalid JSON type while parsing a single item in the %s found in JSON response;"
                    + " ignoring and continuing";

    private final JSONObject mResponseObject;
    private final String mResponseHash;
    private final int mMaxUserBiddingSignalsSizeB;
    private final int mMaxTrustedBiddingDataSizeB;
    private final int mMaxAdsSizeB;
    private final int mMaxNumAds;

    /**
     * Creates a {@link CustomAudienceUpdatableDataReader} that will read updatable data from a
     * given {@link JSONObject} and log with the given identifying {@code responseHash}.
     *
     * @param responseObject a {@link JSONObject} that may contain user bidding signals, trusted
     *     bidding data, and/or a list of ads
     * @param responseHash a String that uniquely identifies the response which is used in logging
     * @param maxUserBiddingSignalsSizeB the configured maximum size in bytes allocated for user
     *     bidding signals
     * @param maxTrustedBiddingDataSizeB the configured maximum size in bytes allocated for trusted
     *     bidding data
     * @param maxAdsSizeB the configured maximum size in bytes allocated for ads
     * @param maxNumAds the configured maximum number of ads allowed per update
     */
    protected CustomAudienceUpdatableDataReader(
            @NonNull JSONObject responseObject,
            @NonNull String responseHash,
            int maxUserBiddingSignalsSizeB,
            int maxTrustedBiddingDataSizeB,
            int maxAdsSizeB,
            int maxNumAds) {
        Objects.requireNonNull(responseObject);
        Objects.requireNonNull(responseHash);

        mResponseObject = responseObject;
        mResponseHash = responseHash;
        mMaxUserBiddingSignalsSizeB = maxUserBiddingSignalsSizeB;
        mMaxTrustedBiddingDataSizeB = maxTrustedBiddingDataSizeB;
        mMaxAdsSizeB = maxAdsSizeB;
        mMaxNumAds = maxNumAds;
    }

    /**
     * Returns the user bidding signals extracted from the input object, if found.
     *
     * @throws JSONException if the key is found but the schema is incorrect
     * @throws NullPointerException if the key found by the field is null
     * @throws IllegalArgumentException if the extracted signals fail data validation
     */
    @Nullable
    public String getUserBiddingSignalsFromJsonObject()
            throws JSONException, NullPointerException, IllegalArgumentException {
        if (mResponseObject.has(USER_BIDDING_SIGNALS_KEY)) {
            JSONObject signalsJsonObj =
                    Objects.requireNonNull(mResponseObject.getJSONObject(USER_BIDDING_SIGNALS_KEY));
            String signalsString = signalsJsonObj.toString();

            if (signalsString.length() > mMaxUserBiddingSignalsSizeB) {
                throw new IllegalArgumentException();
            }

            LogUtil.v(SUCCESS_VALID_LOG_FORMAT, mResponseHash, USER_BIDDING_SIGNALS_KEY);
            return signalsString;
        } else {
            LogUtil.v(FIELD_NOT_FOUND_LOG_FORMAT, mResponseHash, USER_BIDDING_SIGNALS_KEY);
            return null;
        }
    }

    /**
     * Returns the trusted bidding data extracted from the input object, if found.
     *
     * @throws JSONException if the key is found but the schema is incorrect
     * @throws NullPointerException if the key found by the field is null
     * @throws IllegalArgumentException if the extracted data fails data validation
     */
    @Nullable
    public DBTrustedBiddingData getTrustedBiddingDataFromJsonObject()
            throws JSONException, NullPointerException, IllegalArgumentException {
        if (mResponseObject.has(TRUSTED_BIDDING_DATA_KEY)) {
            JSONObject dataJsonObj = mResponseObject.getJSONObject(TRUSTED_BIDDING_DATA_KEY);

            String uriString = dataJsonObj.getString(TRUSTED_BIDDING_URI_KEY);
            Uri parsedUri = Uri.parse(uriString);

            JSONArray keysJsonArray = dataJsonObj.getJSONArray(TRUSTED_BIDDING_KEYS_KEY);
            int keysListLength = keysJsonArray.length();
            List<String> keysList = new ArrayList<>(keysListLength);
            for (int i = 0; i < keysListLength; i++) {
                try {
                    // Note: getString() coerces values to be strings; use get() instead
                    Object key = keysJsonArray.get(i);
                    if (key instanceof String) {
                        keysList.add(Objects.requireNonNull((String) key));
                    } else {
                        LogUtil.v(
                                SKIP_INVALID_JSON_TYPE_LOG_FORMAT,
                                mResponseHash,
                                TRUSTED_BIDDING_KEYS_KEY);
                    }
                } catch (JSONException | NullPointerException ignoredException) {
                    // Skip any keys that are malformed and continue to the next in the list; note
                    // that if the entire given list of keys is junk, then any existing trusted
                    // bidding keys are cleared from the custom audience
                    LogUtil.v(
                            SKIP_INVALID_JSON_TYPE_LOG_FORMAT,
                            mResponseHash,
                            TRUSTED_BIDDING_KEYS_KEY);
                }
            }

            DBTrustedBiddingData trustedBiddingData =
                    new DBTrustedBiddingData.Builder().setUrl(parsedUri).setKeys(keysList).build();

            if (trustedBiddingData.size() > mMaxTrustedBiddingDataSizeB) {
                throw new IllegalArgumentException();
            }

            LogUtil.v(SUCCESS_VALID_LOG_FORMAT, mResponseHash, TRUSTED_BIDDING_DATA_KEY);
            return trustedBiddingData;
        } else {
            LogUtil.v(FIELD_NOT_FOUND_LOG_FORMAT, mResponseHash, TRUSTED_BIDDING_DATA_KEY);
            return null;
        }
    }

    /**
     * Returns the list of ads extracted from the input object, if found.
     *
     * @throws JSONException if the key is found but the schema is incorrect
     * @throws NullPointerException if the key found by the field is null
     * @throws IllegalArgumentException if the extracted ads fail data validation
     */
    @Nullable
    public List<DBAdData> getAdsFromJsonObject()
            throws JSONException, NullPointerException, IllegalArgumentException {
        if (mResponseObject.has(ADS_KEY)) {
            JSONArray adsJsonArray = mResponseObject.getJSONArray(ADS_KEY);
            int adsSize = 0;
            int adsListLength = adsJsonArray.length();
            List<DBAdData> adsList = new ArrayList<>();
            for (int i = 0; i < adsListLength; i++) {
                try {
                    JSONObject adDataJsonObj = adsJsonArray.getJSONObject(i);

                    String uriString = adDataJsonObj.getString(RENDER_URI_KEY);
                    Uri parsedUri = Uri.parse(uriString);

                    String metadata = Objects.requireNonNull(adDataJsonObj.getString(METADATA_KEY));

                    DBAdData adData =
                            new DBAdData.Builder()
                                    .setRenderUri(parsedUri)
                                    .setMetadata(metadata)
                                    .build();
                    adsList.add(adData);
                    adsSize += adData.size();
                } catch (JSONException | NullPointerException ignoredException) {
                    // Skip any ads that are malformed and continue to the next in the list; note
                    // that if the entire given list of ads is junk, then any existing ads are
                    // cleared from the custom audience
                    LogUtil.v(SKIP_INVALID_JSON_TYPE_LOG_FORMAT, mResponseHash, ADS_KEY);
                }
            }

            if (adsSize > mMaxAdsSizeB) {
                throw new IllegalArgumentException();
            }

            if (adsList.size() > mMaxNumAds) {
                throw new IllegalArgumentException();
            }

            LogUtil.v(SUCCESS_VALID_LOG_FORMAT, mResponseHash, ADS_KEY);
            return adsList;
        } else {
            LogUtil.v(FIELD_NOT_FOUND_LOG_FORMAT, mResponseHash, ADS_KEY);
            return null;
        }
    }
}

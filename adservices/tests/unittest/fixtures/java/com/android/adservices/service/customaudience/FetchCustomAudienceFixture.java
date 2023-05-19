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

import static com.android.adservices.service.customaudience.CustomAudienceUpdatableDataReader.ADS_KEY;
import static com.android.adservices.service.customaudience.CustomAudienceUpdatableDataReader.AD_COUNTERS_KEY;
import static com.android.adservices.service.customaudience.CustomAudienceUpdatableDataReader.AD_FILTERS_KEY;
import static com.android.adservices.service.customaudience.CustomAudienceUpdatableDataReader.METADATA_KEY;
import static com.android.adservices.service.customaudience.CustomAudienceUpdatableDataReader.RENDER_URI_KEY;
import static com.android.adservices.service.customaudience.CustomAudienceUpdatableDataReader.TRUSTED_BIDDING_DATA_KEY;
import static com.android.adservices.service.customaudience.CustomAudienceUpdatableDataReader.TRUSTED_BIDDING_KEYS_KEY;
import static com.android.adservices.service.customaudience.CustomAudienceUpdatableDataReader.TRUSTED_BIDDING_URI_KEY;
import static com.android.adservices.service.customaudience.CustomAudienceUpdatableDataReader.USER_BIDDING_SIGNALS_KEY;
import static com.android.adservices.service.customaudience.FetchCustomAudienceReader.ACTIVATION_TIME_KEY;
import static com.android.adservices.service.customaudience.FetchCustomAudienceReader.BIDDING_LOGIC_URI_KEY;
import static com.android.adservices.service.customaudience.FetchCustomAudienceReader.DAILY_UPDATE_URI_KEY;
import static com.android.adservices.service.customaudience.FetchCustomAudienceReader.EXPIRATION_TIME_KEY;
import static com.android.adservices.service.customaudience.FetchCustomAudienceReader.NAME_KEY;

import android.adservices.common.AdTechIdentifier;
import android.adservices.common.CommonFixture;
import android.adservices.customaudience.CustomAudienceFixture;
import android.net.Uri;

import com.android.adservices.LoggerFactory;
import com.android.adservices.common.DBAdDataFixture;
import com.android.adservices.common.JsonFixture;
import com.android.adservices.customaudience.DBTrustedBiddingDataFixture;
import com.android.adservices.data.common.DBAdData;
import com.android.adservices.data.customaudience.DBCustomAudience;
import com.android.adservices.data.customaudience.DBTrustedBiddingData;

import org.json.JSONArray;
import org.json.JSONException;
import org.json.JSONObject;

import java.time.Instant;
import java.util.List;

// TODO(b/283857101): Merge CustomAudienceUpdatableDataReaderFixture in
public class FetchCustomAudienceFixture {
    private static final LoggerFactory.Logger sLogger = LoggerFactory.getFledgeLogger();

    public static String getFullSuccessfulJsonResponseString() throws JSONException {
        return toJsonResponseString(
                CustomAudienceFixture.VALID_NAME,
                CustomAudienceFixture.VALID_ACTIVATION_TIME,
                CustomAudienceFixture.VALID_EXPIRATION_TIME,
                CustomAudienceFixture.getValidDailyUpdateUriByBuyer(
                        AdTechIdentifier.fromString("localhost")),
                CustomAudienceFixture.getValidBiddingLogicUriByBuyer(
                        AdTechIdentifier.fromString("localhost")),
                CustomAudienceFixture.VALID_USER_BIDDING_SIGNALS.toString(),
                DBTrustedBiddingDataFixture.getValidBuilderByBuyer(
                                AdTechIdentifier.fromString("localhost"))
                        .build(),
                DBAdDataFixture.getValidDbAdDataListByBuyer(
                        AdTechIdentifier.fromString("localhost")));
    }

    public static DBCustomAudience getFullSuccessfulDBCustomAudience() throws JSONException {
        return new DBCustomAudience.Builder()
                .setBuyer(AdTechIdentifier.fromString("localhost"))
                .setOwner(CustomAudienceFixture.VALID_OWNER)
                .setName(CustomAudienceFixture.VALID_NAME)
                .setCreationTime(CommonFixture.FIXED_NOW_TRUNCATED_TO_MILLI)
                .setLastAdsAndBiddingDataUpdatedTime(CommonFixture.FIXED_NOW_TRUNCATED_TO_MILLI)
                .setActivationTime(CustomAudienceFixture.VALID_ACTIVATION_TIME)
                .setExpirationTime(CustomAudienceFixture.VALID_EXPIRATION_TIME)
                .setUserBiddingSignals(CustomAudienceFixture.VALID_USER_BIDDING_SIGNALS)
                .setBiddingLogicUri(
                        CustomAudienceFixture.getValidBiddingLogicUriByBuyer(
                                AdTechIdentifier.fromString("localhost")))
                .setTrustedBiddingData(
                        DBTrustedBiddingDataFixture.getValidBuilderByBuyer(
                                        AdTechIdentifier.fromString("localhost"))
                                .build())
                .setAds(
                        DBAdDataFixture.getValidDbAdDataListByBuyer(
                                AdTechIdentifier.fromString("localhost")))
                .build();
    }

    /** Converts the input to a valid JSON object and returns it as a serialized string. */
    public static String toJsonResponseString(
            String name,
            Instant activationTime,
            Instant expirationTime,
            Uri dailyUpdateUri,
            Uri biddingLogicUri,
            String userBiddingSignals,
            DBTrustedBiddingData trustedBiddingData,
            List<DBAdData> ads)
            throws JSONException {
        return toJsonResponseString(
                name,
                activationTime,
                expirationTime,
                dailyUpdateUri,
                biddingLogicUri,
                userBiddingSignals,
                trustedBiddingData,
                ads,
                false);
    }

    /**
     * Converts the input user bidding signals, trusted bidding data, and list of ads to a valid
     * JSON object and returns it as a serialized string.
     *
     * <p>Optionally adds harmless junk to the response by adding unexpected fields.
     */
    private static String toJsonResponseString(
            String name,
            Instant activationTime,
            Instant expirationTime,
            Uri dailyUpdateUri,
            Uri biddingLogicUri,
            String userBiddingSignals,
            DBTrustedBiddingData trustedBiddingData,
            List<DBAdData> ads,
            boolean shouldAddHarmlessJunk)
            throws JSONException {
        JSONObject jsonResponse = new JSONObject();

        jsonResponse = addName(jsonResponse, name, shouldAddHarmlessJunk);
        jsonResponse = addActivationTime(jsonResponse, activationTime, shouldAddHarmlessJunk);
        jsonResponse = addExpirationTime(jsonResponse, expirationTime, shouldAddHarmlessJunk);
        jsonResponse = addDailyUpdateUri(jsonResponse, dailyUpdateUri, shouldAddHarmlessJunk);
        jsonResponse = addBiddingLogicUri(jsonResponse, biddingLogicUri, shouldAddHarmlessJunk);
        jsonResponse =
                addUserBiddingSignals(jsonResponse, userBiddingSignals, shouldAddHarmlessJunk);
        jsonResponse =
                addTrustedBiddingData(jsonResponse, trustedBiddingData, shouldAddHarmlessJunk);
        jsonResponse = addAds(jsonResponse, ads, shouldAddHarmlessJunk);

        return jsonResponse.toString();
    }

    /**
     * Converts a string representation of a JSON object into a JSONObject with a keyed field for
     * name.
     *
     * <p>Optionally adds harmless junk to the object by adding unexpected fields.
     */
    public static JSONObject addName(
            JSONObject jsonObject, String name, boolean shouldAddHarmlessJunk)
            throws JSONException {
        if (name != null) {
            return addToJSONObject(jsonObject, NAME_KEY, name, shouldAddHarmlessJunk);
        }
        return jsonObject;
    }

    /**
     * Converts a string representation of a JSON object into a JSONObject with a keyed field for
     * activation time.
     *
     * <p>Optionally adds harmless junk to the object by adding unexpected fields.
     */
    public static JSONObject addActivationTime(
            JSONObject jsonResponse, Instant activationTime, boolean shouldAddHarmlessJunk)
            throws JSONException {
        if (activationTime != null) {
            return addToJSONObject(
                    jsonResponse,
                    ACTIVATION_TIME_KEY,
                    activationTime.toEpochMilli(),
                    shouldAddHarmlessJunk);
        }
        return jsonResponse;
    }

    /**
     * Converts a string representation of a JSON object into a JSONObject with a keyed field for
     * expiration time.
     *
     * <p>Optionally adds harmless junk to the object by adding unexpected fields.
     */
    public static JSONObject addExpirationTime(
            JSONObject jsonResponse, Instant expirationTime, boolean shouldAddHarmlessJunk)
            throws JSONException {
        if (expirationTime != null) {
            return addToJSONObject(
                    jsonResponse,
                    EXPIRATION_TIME_KEY,
                    expirationTime.toEpochMilli(),
                    shouldAddHarmlessJunk);
        }
        return jsonResponse;
    }

    /**
     * Converts a string representation of a JSON object into a JSONObject with a keyed field for
     * daily update uri.
     *
     * <p>Optionally adds harmless junk to the object by adding unexpected fields.
     */
    public static JSONObject addDailyUpdateUri(
            JSONObject jsonResponse, Uri dailyUpdateUri, boolean shouldAddHarmlessJunk)
            throws JSONException {
        if (dailyUpdateUri != null) {
            return addToJSONObject(
                    jsonResponse,
                    DAILY_UPDATE_URI_KEY,
                    dailyUpdateUri.toString(),
                    shouldAddHarmlessJunk);
        }
        return jsonResponse;
    }

    /**
     * Converts a string representation of a JSON object into a JSONObject with a keyed field for
     * bidding logic uri.
     *
     * <p>Optionally adds harmless junk to the object by adding unexpected fields.
     */
    public static JSONObject addBiddingLogicUri(
            JSONObject jsonResponse, Uri biddingLogicUri, boolean shouldAddHarmlessJunk)
            throws JSONException {
        if (biddingLogicUri != null) {
            return addToJSONObject(
                    jsonResponse,
                    BIDDING_LOGIC_URI_KEY,
                    biddingLogicUri.toString(),
                    shouldAddHarmlessJunk);
        }
        return jsonResponse;
    }

    /**
     * Converts a string representation of a JSON object into a JSONObject with a keyed field for
     * user bidding signals.
     *
     * <p>Optionally adds harmless junk to the object by adding unexpected fields.
     */
    public static JSONObject addUserBiddingSignals(
            JSONObject jsonResponse, String userBiddingSignals, boolean shouldAddHarmlessJunk)
            throws JSONException {
        if (userBiddingSignals != null) {
            JSONObject userBiddingSignalsJson = new JSONObject(userBiddingSignals);
            return addToJSONObject(
                    jsonResponse,
                    USER_BIDDING_SIGNALS_KEY,
                    userBiddingSignalsJson,
                    shouldAddHarmlessJunk);
        }
        return jsonResponse;
    }

    /**
     * Converts {@link DBTrustedBiddingData} into a JSONObject with a keyed field for trusted
     * bidding data.
     *
     * <p>Optionally adds harmless junk to the object by adding unexpected fields.
     */
    public static JSONObject addTrustedBiddingData(
            JSONObject jsonResponse,
            DBTrustedBiddingData trustedBiddingData,
            boolean shouldAddHarmlessJunk)
            throws JSONException {
        if (trustedBiddingData != null) {
            JSONObject trustedBiddingDataJson = new JSONObject();

            if (shouldAddHarmlessJunk) {
                JsonFixture.addHarmlessJunkValues(trustedBiddingDataJson);
            }

            trustedBiddingDataJson.put(
                    TRUSTED_BIDDING_URI_KEY, trustedBiddingData.getUri().toString());
            JSONArray trustedBiddingKeysJson = new JSONArray(trustedBiddingData.getKeys());
            if (shouldAddHarmlessJunk) {
                JsonFixture.addHarmlessJunkValues(trustedBiddingKeysJson);
            }
            trustedBiddingDataJson.put(TRUSTED_BIDDING_KEYS_KEY, trustedBiddingKeysJson);

            return addToJSONObject(
                    jsonResponse, TRUSTED_BIDDING_DATA_KEY, trustedBiddingDataJson, false);
        }

        return jsonResponse;
    }

    /**
     * Converts a list of {@link DBAdData} into a JSONObject with a keyed field for ads.
     *
     * <p>Optionally adds harmless junk to the object by adding unexpected fields.
     */
    public static JSONObject addAds(
            JSONObject jsonResponse, List<DBAdData> ads, boolean shouldAddHarmlessJunk)
            throws JSONException {
        if (ads != null) {
            JSONArray adsJson = new JSONArray();

            if (shouldAddHarmlessJunk) {
                JsonFixture.addHarmlessJunkValues(adsJson);
            }

            for (DBAdData ad : ads) {
                JSONObject adJson = new JSONObject();
                if (shouldAddHarmlessJunk) {
                    JsonFixture.addHarmlessJunkValues(adJson);
                }

                adJson.put(RENDER_URI_KEY, ad.getRenderUri().toString());
                try {
                    adJson.put(METADATA_KEY, new JSONObject(ad.getMetadata()));
                } catch (JSONException exception) {
                    sLogger.v(
                            "Trying to add invalid JSON to test object (%s); inserting as String"
                                    + " instead",
                            exception.getMessage());
                    adJson.put(METADATA_KEY, ad.getMetadata());
                }
                if (!ad.getAdCounterKeys().isEmpty()) {
                    adJson.put(AD_COUNTERS_KEY, new JSONArray(ad.getAdCounterKeys()));
                }
                if (ad.getAdFilters() != null) {
                    adJson.put(AD_FILTERS_KEY, ad.getAdFilters().toJson());
                }
                adsJson.put(adJson);
            }

            return addToJSONObject(jsonResponse, ADS_KEY, adsJson, false);
        }

        return jsonResponse;
    }

    private static JSONObject addToJSONObject(
            JSONObject jsonResponse, String key, Object value, boolean shouldAddHarmlessJunk)
            throws JSONException {
        if (jsonResponse == null) {
            jsonResponse = new JSONObject();
        }

        if (shouldAddHarmlessJunk) {
            JsonFixture.addHarmlessJunkValues(jsonResponse);
        }

        jsonResponse.put(key, value);
        return jsonResponse;
    }
}

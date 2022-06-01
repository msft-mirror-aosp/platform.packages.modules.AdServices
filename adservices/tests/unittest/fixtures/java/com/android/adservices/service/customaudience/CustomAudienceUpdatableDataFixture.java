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

import static com.android.adservices.service.customaudience.CustomAudienceUpdatableData.ADS_KEY;
import static com.android.adservices.service.customaudience.CustomAudienceUpdatableData.METADATA_KEY;
import static com.android.adservices.service.customaudience.CustomAudienceUpdatableData.RENDER_URL_KEY;
import static com.android.adservices.service.customaudience.CustomAudienceUpdatableData.TRUSTED_BIDDING_DATA_KEY;
import static com.android.adservices.service.customaudience.CustomAudienceUpdatableData.TRUSTED_BIDDING_KEYS_KEY;
import static com.android.adservices.service.customaudience.CustomAudienceUpdatableData.TRUSTED_BIDDING_URL_KEY;
import static com.android.adservices.service.customaudience.CustomAudienceUpdatableData.USER_BIDDING_SIGNALS_KEY;

import android.adservices.common.CommonFixture;
import android.adservices.customaudience.CustomAudienceFixture;

import com.android.adservices.common.DBAdDataFixture;
import com.android.adservices.customaudience.DBTrustedBiddingDataFixture;
import com.android.adservices.data.common.DBAdData;
import com.android.adservices.data.customaudience.DBTrustedBiddingData;

import org.json.JSONArray;
import org.json.JSONException;
import org.json.JSONObject;

import java.util.List;

public class CustomAudienceUpdatableDataFixture {
    public static String getEmptyJsonResponseString() throws JSONException {
        return toJsonResponseString(null, null, null);
    }

    public static String getFullSuccessfulJsonResponseString() throws JSONException {
        return toJsonResponseString(
                CustomAudienceFixture.VALID_USER_BIDDING_SIGNALS,
                DBTrustedBiddingDataFixture.VALID_DB_TRUSTED_BIDDING_DATA,
                DBAdDataFixture.VALID_DB_AD_DATA_LIST);
    }

    /**
     * Converts the input user bidding signals, trusted bidding data, and list of ads to a valid
     * JSON object, adds harmless junk fields, and returns it as a serialized string.
     */
    public static String toJsonResponseStringWithHarmlessJunk(
            String userBiddingSignals, DBTrustedBiddingData trustedBiddingData, List<DBAdData> ads)
            throws JSONException {
        return toJsonResponseString(userBiddingSignals, trustedBiddingData, ads, true);
    }

    /**
     * Gets a valid JSON object with keys for user bidding signals, trusted bidding data, and a list
     * of ads, malforms the expected schema, and returns it as a serialized string.
     */
    public static String getMalformedJsonResponseString() throws JSONException {
        JSONObject jsonResponse = new JSONObject();

        jsonResponse.put(
                USER_BIDDING_SIGNALS_KEY,
                "user bidding signals but as a string and not a JSON object");
        jsonResponse.put(TRUSTED_BIDDING_DATA_KEY, 0);
        jsonResponse.put(ADS_KEY, "mismatched schema");

        return jsonResponse.toString();
    }

    /**
     * Converts the input user bidding signals, trusted bidding data, and list of ads to a valid
     * JSON object and returns it as a serialized string.
     */
    public static String toJsonResponseString(
            String userBiddingSignals, DBTrustedBiddingData trustedBiddingData, List<DBAdData> ads)
            throws JSONException {
        return toJsonResponseString(userBiddingSignals, trustedBiddingData, ads, false);
    }

    /**
     * Converts the input user bidding signals, trusted bidding data, and list of ads to a valid
     * JSON object and returns it as a serialized string.
     *
     * <p>Optionally adds harmless junk to the response by adding unexpected fields.
     */
    private static String toJsonResponseString(
            String userBiddingSignals,
            DBTrustedBiddingData trustedBiddingData,
            List<DBAdData> ads,
            boolean shouldAddHarmlessJunk)
            throws JSONException {
        JSONObject jsonResponse = new JSONObject();

        if (shouldAddHarmlessJunk) {
            addHarmlessJunkValues(jsonResponse);
        }

        if (userBiddingSignals != null) {
            JSONObject userBiddingSignalsJson = new JSONObject(userBiddingSignals);
            jsonResponse.put(USER_BIDDING_SIGNALS_KEY, userBiddingSignalsJson);
        }

        if (trustedBiddingData != null) {
            JSONObject trustedBiddingDataJson = new JSONObject();
            if (shouldAddHarmlessJunk) {
                addHarmlessJunkValues(trustedBiddingDataJson);
            }

            trustedBiddingDataJson.put(
                    TRUSTED_BIDDING_URL_KEY, trustedBiddingData.getUrl().toString());
            JSONArray trustedBiddingKeysJson = new JSONArray(trustedBiddingData.getKeys());
            if (shouldAddHarmlessJunk) {
                addHarmlessJunkValues(trustedBiddingKeysJson);
            }
            trustedBiddingDataJson.put(TRUSTED_BIDDING_KEYS_KEY, trustedBiddingKeysJson);

            jsonResponse.put(TRUSTED_BIDDING_DATA_KEY, trustedBiddingDataJson);
        }

        if (ads != null) {
            JSONArray adsJson = new JSONArray();
            if (shouldAddHarmlessJunk) {
                addHarmlessJunkValues(adsJson);
            }

            for (DBAdData ad : ads) {
                JSONObject adJson = new JSONObject();
                if (shouldAddHarmlessJunk) {
                    addHarmlessJunkValues(adJson);
                }

                adJson.put(RENDER_URL_KEY, ad.getRenderUrl().toString());
                adJson.put(METADATA_KEY, ad.getMetadata());

                adsJson.put(adJson);
            }

            jsonResponse.put(ADS_KEY, adsJson);
        }

        return jsonResponse.toString();
    }

    /** Modifies the target JSONObject in-place to add harmless junk values. */
    private static void addHarmlessJunkValues(JSONObject target) throws JSONException {
        target.put("junk_int", 1);
        target.put("junk_boolean", true);
        target.put("junk_string", "harmless junk");
        target.put("junk_null", null);
        target.put("junk_object", new JSONObject("{'harmless':true,'object':1}"));
    }

    /** Modifies the target JSONArray in-place to add harmless junk values. */
    private static void addHarmlessJunkValues(JSONArray target) throws JSONException {
        target.put(1);
        target.put(true);
        target.put("harmless junk");
        target.put(null);
        target.put(new JSONObject("{'harmless':true,'object':1}"));
    }

    /**
     * Returns an org.json.JSONObject representation of a valid JSON object serialized as a string.
     *
     * <p>This helps verify that when comparing strings before and after conversion to JSONObject,
     * equality from an org.json.JSONObject perspective is guaranteed.
     */
    public static String formatAsOrgJsonJSONObjectString(String inputJsonString)
            throws JSONException {
        return new JSONObject(inputJsonString).toString();
    }

    public static CustomAudienceUpdatableData.Builder getValidBuilderFullSuccessfulResponse() {
        return CustomAudienceUpdatableData.builder()
                .setUserBiddingSignals(CustomAudienceFixture.VALID_USER_BIDDING_SIGNALS)
                .setTrustedBiddingData(DBTrustedBiddingDataFixture.VALID_DB_TRUSTED_BIDDING_DATA)
                .setAds(DBAdDataFixture.VALID_DB_AD_DATA_LIST)
                .setAttemptedUpdateTime(CommonFixture.FIXED_NOW)
                .setInitialUpdateResult(BackgroundFetchRunner.UpdateResultType.SUCCESS)
                .setContainsSuccessfulUpdate(true);
    }

    public static CustomAudienceUpdatableData.Builder getValidBuilderEmptySuccessfulResponse() {
        return CustomAudienceUpdatableData.builder()
                .setUserBiddingSignals(null)
                .setTrustedBiddingData(null)
                .setAds(null)
                .setAttemptedUpdateTime(CommonFixture.FIXED_NOW)
                .setInitialUpdateResult(BackgroundFetchRunner.UpdateResultType.SUCCESS)
                .setContainsSuccessfulUpdate(true);
    }

    public static CustomAudienceUpdatableData.Builder getValidBuilderEmptyFailedResponse() {
        return CustomAudienceUpdatableData.builder()
                .setUserBiddingSignals(null)
                .setTrustedBiddingData(null)
                .setAds(null)
                .setAttemptedUpdateTime(CommonFixture.FIXED_NOW)
                .setInitialUpdateResult(BackgroundFetchRunner.UpdateResultType.SUCCESS)
                .setContainsSuccessfulUpdate(false);
    }
}

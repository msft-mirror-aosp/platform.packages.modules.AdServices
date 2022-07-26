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

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertNull;
import static org.junit.Assert.assertThrows;

import android.adservices.common.AdSelectionSignals;
import android.adservices.common.CommonFixture;
import android.adservices.customaudience.CustomAudienceFixture;

import com.android.adservices.common.DBAdDataFixture;
import com.android.adservices.customaudience.DBTrustedBiddingDataFixture;
import com.android.adservices.data.common.DBAdData;
import com.android.adservices.data.customaudience.DBTrustedBiddingData;
import com.android.adservices.service.Flags;
import com.android.adservices.service.FlagsFactory;

import org.json.JSONException;
import org.json.JSONObject;
import org.junit.Test;

import java.util.List;

public class CustomAudienceUpdatableDataReaderTest {
    private static final String RESPONSE_IDENTIFIER = "[1]";
    private static final DBTrustedBiddingData VALID_TRUSTED_BIDDING_DATA =
            DBTrustedBiddingDataFixture.getValidBuilderByBuyer(CommonFixture.VALID_BUYER).build();
    private static final List<DBAdData> VALID_DB_AD_DATA_LIST =
            DBAdDataFixture.getValidDbAdDataListByBuyer(CommonFixture.VALID_BUYER);

    private final Flags mFlags = FlagsFactory.getFlagsForTest();

    @Test
    public void testGetUserBiddingSignalsFromFullJsonObjectSuccess() throws JSONException {
        String validUserBiddingSignalsAsJsonObjectString =
                CustomAudienceUpdatableDataFixture.formatAsOrgJsonJSONObjectString(
                        CustomAudienceFixture.VALID_USER_BIDDING_SIGNALS.getStringForm());

        JSONObject responseObject =
                CustomAudienceUpdatableDataFixture.addToJsonObject(
                        null, validUserBiddingSignalsAsJsonObjectString, false);
        CustomAudienceUpdatableDataReader reader =
                new CustomAudienceUpdatableDataReader(
                        responseObject,
                        RESPONSE_IDENTIFIER,
                        CommonFixture.VALID_BUYER,
                        mFlags.getFledgeCustomAudienceMaxUserBiddingSignalsSizeB(),
                        mFlags.getFledgeCustomAudienceMaxTrustedBiddingDataSizeB(),
                        mFlags.getFledgeCustomAudienceMaxAdsSizeB(),
                        mFlags.getFledgeCustomAudienceMaxNumAds());

        assertEquals(
                AdSelectionSignals.fromString(validUserBiddingSignalsAsJsonObjectString),
                reader.getUserBiddingSignalsFromJsonObject());
    }

    @Test
    public void testGetUserBiddingSignalsFromFullJsonObjectWithHarmlessJunkSuccess()
            throws JSONException {
        String validUserBiddingSignalsAsJsonObjectString =
                CustomAudienceUpdatableDataFixture.formatAsOrgJsonJSONObjectString(
                        CustomAudienceFixture.VALID_USER_BIDDING_SIGNALS.getStringForm());

        JSONObject responseObject =
                CustomAudienceUpdatableDataFixture.addToJsonObject(
                        null, validUserBiddingSignalsAsJsonObjectString, true);
        CustomAudienceUpdatableDataReader reader =
                new CustomAudienceUpdatableDataReader(
                        responseObject,
                        RESPONSE_IDENTIFIER,
                        CommonFixture.VALID_BUYER,
                        mFlags.getFledgeCustomAudienceMaxUserBiddingSignalsSizeB(),
                        mFlags.getFledgeCustomAudienceMaxTrustedBiddingDataSizeB(),
                        mFlags.getFledgeCustomAudienceMaxAdsSizeB(),
                        mFlags.getFledgeCustomAudienceMaxNumAds());

        assertEquals(
                AdSelectionSignals.fromString(validUserBiddingSignalsAsJsonObjectString),
                reader.getUserBiddingSignalsFromJsonObject());
    }

    @Test
    public void testGetUserBiddingSignalsFromEmptyJsonObject() throws JSONException {
        String missingUserBiddingSignalsAsJsonObjectString = null;

        JSONObject responseObject =
                CustomAudienceUpdatableDataFixture.addToJsonObject(
                        null, missingUserBiddingSignalsAsJsonObjectString, false);
        CustomAudienceUpdatableDataReader reader =
                new CustomAudienceUpdatableDataReader(
                        responseObject,
                        RESPONSE_IDENTIFIER,
                        CommonFixture.VALID_BUYER,
                        mFlags.getFledgeCustomAudienceMaxUserBiddingSignalsSizeB(),
                        mFlags.getFledgeCustomAudienceMaxTrustedBiddingDataSizeB(),
                        mFlags.getFledgeCustomAudienceMaxAdsSizeB(),
                        mFlags.getFledgeCustomAudienceMaxNumAds());

        assertNull(reader.getUserBiddingSignalsFromJsonObject());
    }

    @Test
    public void testGetUserBiddingSignalsFromJsonObjectMismatchedSchema() throws JSONException {
        CustomAudienceUpdatableDataReader reader =
                new CustomAudienceUpdatableDataReader(
                        CustomAudienceUpdatableDataFixture.getMalformedJsonObject(),
                        RESPONSE_IDENTIFIER,
                        CommonFixture.VALID_BUYER,
                        mFlags.getFledgeCustomAudienceMaxUserBiddingSignalsSizeB(),
                        mFlags.getFledgeCustomAudienceMaxTrustedBiddingDataSizeB(),
                        mFlags.getFledgeCustomAudienceMaxAdsSizeB(),
                        mFlags.getFledgeCustomAudienceMaxNumAds());
        assertThrows(JSONException.class, reader::getUserBiddingSignalsFromJsonObject);
    }

    @Test
    public void testGetUserBiddingSignalsFromJsonObjectMismatchedNullSchema() throws JSONException {
        CustomAudienceUpdatableDataReader reader =
                new CustomAudienceUpdatableDataReader(
                        CustomAudienceUpdatableDataFixture.getMalformedNullJsonObject(),
                        RESPONSE_IDENTIFIER,
                        CommonFixture.VALID_BUYER,
                        mFlags.getFledgeCustomAudienceMaxUserBiddingSignalsSizeB(),
                        mFlags.getFledgeCustomAudienceMaxTrustedBiddingDataSizeB(),
                        mFlags.getFledgeCustomAudienceMaxAdsSizeB(),
                        mFlags.getFledgeCustomAudienceMaxNumAds());
        assertThrows(JSONException.class, reader::getUserBiddingSignalsFromJsonObject);
    }

    @Test
    public void testGetUserBiddingSignalsFromJsonObjectInvalidSize() throws JSONException {
        String validUserBiddingSignalsAsJsonObjectString =
                CustomAudienceUpdatableDataFixture.formatAsOrgJsonJSONObjectString(
                        CustomAudienceFixture.VALID_USER_BIDDING_SIGNALS.getStringForm());

        JSONObject responseObject =
                CustomAudienceUpdatableDataFixture.addToJsonObject(
                        null, validUserBiddingSignalsAsJsonObjectString, false);
        CustomAudienceUpdatableDataReader reader =
                new CustomAudienceUpdatableDataReader(
                        responseObject,
                        RESPONSE_IDENTIFIER,
                        CommonFixture.VALID_BUYER,
                        1,
                        mFlags.getFledgeCustomAudienceMaxTrustedBiddingDataSizeB(),
                        mFlags.getFledgeCustomAudienceMaxAdsSizeB(),
                        mFlags.getFledgeCustomAudienceMaxNumAds());

        assertThrows(IllegalArgumentException.class, reader::getUserBiddingSignalsFromJsonObject);
    }

    @Test
    public void testGetTrustedBiddingDataFromFullJsonObjectSuccess() throws JSONException {
        DBTrustedBiddingData expectedTrustedBiddingData = VALID_TRUSTED_BIDDING_DATA;

        JSONObject responseObject =
                CustomAudienceUpdatableDataFixture.addToJsonObject(
                        null, expectedTrustedBiddingData, false);
        CustomAudienceUpdatableDataReader reader =
                new CustomAudienceUpdatableDataReader(
                        responseObject,
                        RESPONSE_IDENTIFIER,
                        CommonFixture.VALID_BUYER,
                        mFlags.getFledgeCustomAudienceMaxUserBiddingSignalsSizeB(),
                        mFlags.getFledgeCustomAudienceMaxTrustedBiddingDataSizeB(),
                        mFlags.getFledgeCustomAudienceMaxAdsSizeB(),
                        mFlags.getFledgeCustomAudienceMaxNumAds());

        assertEquals(expectedTrustedBiddingData, reader.getTrustedBiddingDataFromJsonObject());
    }

    @Test
    public void testGetTrustedBiddingDataFromFullJsonObjectWithHarmlessJunkSuccess()
            throws JSONException {
        DBTrustedBiddingData expectedTrustedBiddingData = VALID_TRUSTED_BIDDING_DATA;

        JSONObject responseObject =
                CustomAudienceUpdatableDataFixture.addToJsonObject(
                        null, expectedTrustedBiddingData, true);
        CustomAudienceUpdatableDataReader reader =
                new CustomAudienceUpdatableDataReader(
                        responseObject,
                        RESPONSE_IDENTIFIER,
                        CommonFixture.VALID_BUYER,
                        mFlags.getFledgeCustomAudienceMaxUserBiddingSignalsSizeB(),
                        mFlags.getFledgeCustomAudienceMaxTrustedBiddingDataSizeB(),
                        mFlags.getFledgeCustomAudienceMaxAdsSizeB(),
                        mFlags.getFledgeCustomAudienceMaxNumAds());

        assertEquals(
                "responseObject = " + responseObject.toString(4),
                expectedTrustedBiddingData,
                reader.getTrustedBiddingDataFromJsonObject());
    }

    @Test
    public void testGetTrustedBiddingDataFromEmptyJsonObject() throws JSONException {
        String missingTrustedBiddingDataAsJsonObjectString = null;

        JSONObject responseObject =
                CustomAudienceUpdatableDataFixture.addToJsonObject(
                        null, missingTrustedBiddingDataAsJsonObjectString, false);
        CustomAudienceUpdatableDataReader reader =
                new CustomAudienceUpdatableDataReader(
                        responseObject,
                        RESPONSE_IDENTIFIER,
                        CommonFixture.VALID_BUYER,
                        mFlags.getFledgeCustomAudienceMaxUserBiddingSignalsSizeB(),
                        mFlags.getFledgeCustomAudienceMaxTrustedBiddingDataSizeB(),
                        mFlags.getFledgeCustomAudienceMaxAdsSizeB(),
                        mFlags.getFledgeCustomAudienceMaxNumAds());

        assertNull(reader.getTrustedBiddingDataFromJsonObject());
    }

    @Test
    public void testGetTrustedBiddingDataFromJsonObjectMismatchedSchema() throws JSONException {
        CustomAudienceUpdatableDataReader reader =
                new CustomAudienceUpdatableDataReader(
                        CustomAudienceUpdatableDataFixture.getMalformedJsonObject(),
                        RESPONSE_IDENTIFIER,
                        CommonFixture.VALID_BUYER,
                        mFlags.getFledgeCustomAudienceMaxUserBiddingSignalsSizeB(),
                        mFlags.getFledgeCustomAudienceMaxTrustedBiddingDataSizeB(),
                        mFlags.getFledgeCustomAudienceMaxAdsSizeB(),
                        mFlags.getFledgeCustomAudienceMaxNumAds());
        assertThrows(JSONException.class, reader::getTrustedBiddingDataFromJsonObject);
    }

    @Test
    public void testGetTrustedBiddingDataFromJsonObjectMismatchedNullSchema() throws JSONException {
        CustomAudienceUpdatableDataReader reader =
                new CustomAudienceUpdatableDataReader(
                        CustomAudienceUpdatableDataFixture.getMalformedNullJsonObject(),
                        RESPONSE_IDENTIFIER,
                        CommonFixture.VALID_BUYER,
                        mFlags.getFledgeCustomAudienceMaxUserBiddingSignalsSizeB(),
                        mFlags.getFledgeCustomAudienceMaxTrustedBiddingDataSizeB(),
                        mFlags.getFledgeCustomAudienceMaxAdsSizeB(),
                        mFlags.getFledgeCustomAudienceMaxNumAds());
        assertThrows(JSONException.class, reader::getTrustedBiddingDataFromJsonObject);
    }

    @Test
    public void testGetTrustedBiddingDataFromJsonObjectMismatchedUri() throws JSONException {
        DBTrustedBiddingData expectedTrustedBiddingData = VALID_TRUSTED_BIDDING_DATA;

        JSONObject responseObject =
                CustomAudienceUpdatableDataFixture.addToJsonObject(
                        null, expectedTrustedBiddingData, false);
        CustomAudienceUpdatableDataReader reader =
                new CustomAudienceUpdatableDataReader(
                        responseObject,
                        RESPONSE_IDENTIFIER,
                        "other.domain",
                        mFlags.getFledgeCustomAudienceMaxUserBiddingSignalsSizeB(),
                        mFlags.getFledgeCustomAudienceMaxTrustedBiddingDataSizeB(),
                        mFlags.getFledgeCustomAudienceMaxAdsSizeB(),
                        mFlags.getFledgeCustomAudienceMaxNumAds());

        assertThrows(IllegalArgumentException.class, reader::getTrustedBiddingDataFromJsonObject);
    }

    @Test
    public void testGetTrustedBiddingDataFromJsonObjectInvalidSize() throws JSONException {
        DBTrustedBiddingData expectedTrustedBiddingData = VALID_TRUSTED_BIDDING_DATA;

        JSONObject responseObject =
                CustomAudienceUpdatableDataFixture.addToJsonObject(
                        null, expectedTrustedBiddingData, false);
        CustomAudienceUpdatableDataReader reader =
                new CustomAudienceUpdatableDataReader(
                        responseObject,
                        RESPONSE_IDENTIFIER,
                        CommonFixture.VALID_BUYER,
                        mFlags.getFledgeCustomAudienceMaxUserBiddingSignalsSizeB(),
                        1,
                        mFlags.getFledgeCustomAudienceMaxAdsSizeB(),
                        mFlags.getFledgeCustomAudienceMaxNumAds());

        assertThrows(IllegalArgumentException.class, reader::getTrustedBiddingDataFromJsonObject);
    }

    @Test
    public void testGetAdsFromFullJsonObjectSuccess() throws JSONException {
        List<DBAdData> expectedAds = VALID_DB_AD_DATA_LIST;

        JSONObject responseObject =
                CustomAudienceUpdatableDataFixture.addToJsonObject(null, expectedAds, false);
        CustomAudienceUpdatableDataReader reader =
                new CustomAudienceUpdatableDataReader(
                        responseObject,
                        RESPONSE_IDENTIFIER,
                        CommonFixture.VALID_BUYER,
                        mFlags.getFledgeCustomAudienceMaxUserBiddingSignalsSizeB(),
                        mFlags.getFledgeCustomAudienceMaxTrustedBiddingDataSizeB(),
                        mFlags.getFledgeCustomAudienceMaxAdsSizeB(),
                        mFlags.getFledgeCustomAudienceMaxNumAds());

        assertEquals(expectedAds, reader.getAdsFromJsonObject());
    }

    @Test
    public void testGetAdsFromFullJsonObjectWithHarmlessJunkSuccess() throws JSONException {
        List<DBAdData> expectedAds = VALID_DB_AD_DATA_LIST;

        JSONObject responseObject =
                CustomAudienceUpdatableDataFixture.addToJsonObject(null, expectedAds, true);
        CustomAudienceUpdatableDataReader reader =
                new CustomAudienceUpdatableDataReader(
                        responseObject,
                        RESPONSE_IDENTIFIER,
                        CommonFixture.VALID_BUYER,
                        mFlags.getFledgeCustomAudienceMaxUserBiddingSignalsSizeB(),
                        mFlags.getFledgeCustomAudienceMaxTrustedBiddingDataSizeB(),
                        mFlags.getFledgeCustomAudienceMaxAdsSizeB(),
                        mFlags.getFledgeCustomAudienceMaxNumAds());

        assertEquals(
                "responseObject = " + responseObject.toString(4),
                expectedAds,
                reader.getAdsFromJsonObject());
    }

    @Test
    public void testGetAdsFromEmptyJsonObject() throws JSONException {
        String missingAdsAsJsonObjectString = null;

        JSONObject responseObject =
                CustomAudienceUpdatableDataFixture.addToJsonObject(
                        null, missingAdsAsJsonObjectString, false);
        CustomAudienceUpdatableDataReader reader =
                new CustomAudienceUpdatableDataReader(
                        responseObject,
                        RESPONSE_IDENTIFIER,
                        CommonFixture.VALID_BUYER,
                        mFlags.getFledgeCustomAudienceMaxUserBiddingSignalsSizeB(),
                        mFlags.getFledgeCustomAudienceMaxTrustedBiddingDataSizeB(),
                        mFlags.getFledgeCustomAudienceMaxAdsSizeB(),
                        mFlags.getFledgeCustomAudienceMaxNumAds());

        assertNull(reader.getAdsFromJsonObject());
    }

    @Test
    public void testGetAdsFromJsonObjectMismatchedSchema() throws JSONException {
        CustomAudienceUpdatableDataReader reader =
                new CustomAudienceUpdatableDataReader(
                        CustomAudienceUpdatableDataFixture.getMalformedJsonObject(),
                        RESPONSE_IDENTIFIER,
                        CommonFixture.VALID_BUYER,
                        mFlags.getFledgeCustomAudienceMaxUserBiddingSignalsSizeB(),
                        mFlags.getFledgeCustomAudienceMaxTrustedBiddingDataSizeB(),
                        mFlags.getFledgeCustomAudienceMaxAdsSizeB(),
                        mFlags.getFledgeCustomAudienceMaxNumAds());
        assertThrows(JSONException.class, reader::getAdsFromJsonObject);
    }

    @Test
    public void testGetAdsFromJsonObjectMismatchedNullSchema() throws JSONException {
        CustomAudienceUpdatableDataReader reader =
                new CustomAudienceUpdatableDataReader(
                        CustomAudienceUpdatableDataFixture.getMalformedNullJsonObject(),
                        RESPONSE_IDENTIFIER,
                        CommonFixture.VALID_BUYER,
                        mFlags.getFledgeCustomAudienceMaxUserBiddingSignalsSizeB(),
                        mFlags.getFledgeCustomAudienceMaxTrustedBiddingDataSizeB(),
                        mFlags.getFledgeCustomAudienceMaxAdsSizeB(),
                        mFlags.getFledgeCustomAudienceMaxNumAds());
        assertThrows(JSONException.class, reader::getAdsFromJsonObject);
    }

    @Test
    public void testGetAdsFromJsonObjectInvalidTotalSize() throws JSONException {
        List<DBAdData> expectedAds = VALID_DB_AD_DATA_LIST;

        JSONObject responseObject =
                CustomAudienceUpdatableDataFixture.addToJsonObject(null, expectedAds, false);
        CustomAudienceUpdatableDataReader reader =
                new CustomAudienceUpdatableDataReader(
                        responseObject,
                        RESPONSE_IDENTIFIER,
                        CommonFixture.VALID_BUYER,
                        mFlags.getFledgeCustomAudienceMaxUserBiddingSignalsSizeB(),
                        mFlags.getFledgeCustomAudienceMaxTrustedBiddingDataSizeB(),
                        1,
                        mFlags.getFledgeCustomAudienceMaxNumAds());

        assertThrows(IllegalArgumentException.class, reader::getAdsFromJsonObject);
    }

    @Test
    public void testGetAdsFromJsonObjectInvalidNumAds() throws JSONException {
        List<DBAdData> expectedAds = VALID_DB_AD_DATA_LIST;

        JSONObject responseObject =
                CustomAudienceUpdatableDataFixture.addToJsonObject(null, expectedAds, false);
        CustomAudienceUpdatableDataReader reader =
                new CustomAudienceUpdatableDataReader(
                        responseObject,
                        RESPONSE_IDENTIFIER,
                        CommonFixture.VALID_BUYER,
                        mFlags.getFledgeCustomAudienceMaxUserBiddingSignalsSizeB(),
                        mFlags.getFledgeCustomAudienceMaxTrustedBiddingDataSizeB(),
                        mFlags.getFledgeCustomAudienceMaxAdsSizeB(),
                        1);

        assertThrows(IllegalArgumentException.class, reader::getAdsFromJsonObject);
    }
}

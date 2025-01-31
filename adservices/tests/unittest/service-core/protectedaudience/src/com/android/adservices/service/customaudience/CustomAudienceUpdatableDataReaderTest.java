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

import static android.adservices.customaudience.CustomAudience.FLAG_AUCTION_SERVER_REQUEST_OMIT_ADS;
import static android.adservices.customaudience.CustomAudience.PRIORITY_DEFAULT;
import static android.adservices.customaudience.CustomAudienceFixture.VALID_PRIORITY_1;

import static com.android.adservices.service.FlagsConstants.KEY_FLEDGE_APP_INSTALL_FILTERING_ENABLED;
import static com.android.adservices.service.FlagsConstants.KEY_FLEDGE_FREQUENCY_CAP_FILTERING_ENABLED;
import static com.android.adservices.service.customaudience.CustomAudienceUpdatableDataReader.ADS_KEY;
import static com.android.adservices.service.customaudience.CustomAudienceUpdatableDataReader.AD_RENDER_ID_KEY;
import static com.android.adservices.service.customaudience.CustomAudienceUpdatableDataReader.AUCTION_SERVER_REQUEST_FLAGS_KEY;
import static com.android.adservices.service.customaudience.CustomAudienceUpdatableDataReader.COMPONENT_ADS_KEY;
import static com.android.adservices.service.customaudience.CustomAudienceUpdatableDataReader.COMPONENT_ADS_SIZE_EXCEEDS_MAX;
import static com.android.adservices.service.customaudience.CustomAudienceUpdatableDataReader.OMIT_ADS_VALUE;
import static com.android.adservices.service.customaudience.CustomAudienceUpdatableDataReader.RENDER_URI_KEY;

import static com.google.common.truth.Truth.assertThat;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertNotNull;
import static org.junit.Assert.assertNull;
import static org.junit.Assert.assertThrows;
import static org.junit.Assert.assertTrue;

import android.adservices.common.AdSelectionSignals;
import android.adservices.common.AdTechIdentifier;
import android.adservices.common.CommonFixture;
import android.adservices.common.ComponentAdData;
import android.adservices.common.ComponentAdDataFixture;
import android.adservices.customaudience.CustomAudienceFixture;

import com.android.adservices.common.AdServicesUnitTestCase;
import com.android.adservices.common.DBAdDataFixture;
import com.android.adservices.common.JsonFixture;
import com.android.adservices.customaudience.DBTrustedBiddingDataFixture;
import com.android.adservices.data.common.DBAdData;
import com.android.adservices.data.customaudience.DBTrustedBiddingData;
import com.android.adservices.shared.testing.annotations.SetFlagTrue;

import com.google.common.collect.ImmutableList;

import org.json.JSONException;
import org.json.JSONObject;
import org.junit.Test;

import java.util.ArrayList;
import java.util.List;
import java.util.stream.Collectors;

@SetFlagTrue(KEY_FLEDGE_FREQUENCY_CAP_FILTERING_ENABLED)
@SetFlagTrue(KEY_FLEDGE_APP_INSTALL_FILTERING_ENABLED)
public final class CustomAudienceUpdatableDataReaderTest extends AdServicesUnitTestCase {
    private static final String RESPONSE_IDENTIFIER = "[1]";
    private static final DBTrustedBiddingData VALID_TRUSTED_BIDDING_DATA =
            DBTrustedBiddingDataFixture.getValidBuilderByBuyer(CommonFixture.VALID_BUYER_1).build();
    private static final List<DBAdData> VALID_DB_AD_DATA_LIST =
            DBAdDataFixture.getValidDbAdDataListByBuyer(CommonFixture.VALID_BUYER_1);
    private static final List<ComponentAdData> VALID_COMPONENT_ADS_LIST =
            ComponentAdDataFixture.getValidComponentAdsByBuyer(CommonFixture.VALID_BUYER_1);
    private static final List<DBAdData> INVALID_DB_AD_DATA_LIST =
            DBAdDataFixture.getInvalidDbAdDataListByBuyer(CommonFixture.VALID_BUYER_1);

    @Test
    public void testGetUserBiddingSignalsFromFullJsonObjectSuccess() throws Exception {
        String validUserBiddingSignalsAsJsonObjectString =
                JsonFixture.formatAsOrgJsonJSONObjectString(
                        CustomAudienceFixture.VALID_USER_BIDDING_SIGNALS.toString());

        JSONObject responseObject =
                CustomAudienceUpdatableDataFixture.addToJsonObject(
                        null, validUserBiddingSignalsAsJsonObjectString, false);
        CustomAudienceUpdatableDataReader reader =
                new CustomAudienceUpdatableDataReader(
                        responseObject,
                        RESPONSE_IDENTIFIER,
                        CommonFixture.VALID_BUYER_1,
                        mFakeFlags.getFledgeCustomAudienceMaxUserBiddingSignalsSizeB(),
                        mFakeFlags.getFledgeCustomAudienceMaxTrustedBiddingDataSizeB(),
                        mFakeFlags.getFledgeCustomAudienceMaxAdsSizeB(),
                        mFakeFlags.getFledgeCustomAudienceMaxNumAds(),
                        mFakeFlags.getFledgeFrequencyCapFilteringEnabled(),
                        mFakeFlags.getFledgeAppInstallFilteringEnabled(),
                        mFakeFlags.getFledgeAuctionServerAdRenderIdEnabled(),
                        mFakeFlags.getFledgeAuctionServerAdRenderIdMaxLength(),
                        mFakeFlags.getComponentAdRenderIdMaxLengthBytes(),
                        mFakeFlags.getMaxComponentAdsPerCustomAudience());

        assertEquals(
                AdSelectionSignals.fromString(validUserBiddingSignalsAsJsonObjectString),
                reader.getUserBiddingSignalsFromJsonObject());
    }

    @Test
    public void testGetAuctionServerRequestFlagsSuccess() throws Exception {
        List<String> flagsList = ImmutableList.of(OMIT_ADS_VALUE);

        JSONObject responseObject =
                CustomAudienceUpdatableDataFixture.addAuctionServerRequestFlagsToJsonObject(
                        null, flagsList, true);
        CustomAudienceUpdatableDataReader reader =
                new CustomAudienceUpdatableDataReader(
                        responseObject,
                        RESPONSE_IDENTIFIER,
                        CommonFixture.VALID_BUYER_1,
                        mFakeFlags.getFledgeCustomAudienceMaxUserBiddingSignalsSizeB(),
                        mFakeFlags.getFledgeCustomAudienceMaxTrustedBiddingDataSizeB(),
                        mFakeFlags.getFledgeCustomAudienceMaxAdsSizeB(),
                        mFakeFlags.getFledgeCustomAudienceMaxNumAds(),
                        mFakeFlags.getFledgeFrequencyCapFilteringEnabled(),
                        mFakeFlags.getFledgeAppInstallFilteringEnabled(),
                        mFakeFlags.getFledgeAuctionServerAdRenderIdEnabled(),
                        mFakeFlags.getFledgeAuctionServerAdRenderIdMaxLength(),
                        mFakeFlags.getComponentAdRenderIdMaxLengthBytes(),
                        mFakeFlags.getMaxComponentAdsPerCustomAudience());

        assertEquals(FLAG_AUCTION_SERVER_REQUEST_OMIT_ADS, reader.getAuctionServerRequestFlags());
    }

    @Test
    public void testGetAuctionServerRequestFlagsNoMatchInFlags() throws Exception {
        List<String> flagsList = ImmutableList.of();

        JSONObject responseObject =
                CustomAudienceUpdatableDataFixture.addAuctionServerRequestFlagsToJsonObject(
                        null, flagsList, true);
        CustomAudienceUpdatableDataReader reader =
                new CustomAudienceUpdatableDataReader(
                        responseObject,
                        RESPONSE_IDENTIFIER,
                        CommonFixture.VALID_BUYER_1,
                        mFakeFlags.getFledgeCustomAudienceMaxUserBiddingSignalsSizeB(),
                        mFakeFlags.getFledgeCustomAudienceMaxTrustedBiddingDataSizeB(),
                        mFakeFlags.getFledgeCustomAudienceMaxAdsSizeB(),
                        mFakeFlags.getFledgeCustomAudienceMaxNumAds(),
                        mFakeFlags.getFledgeFrequencyCapFilteringEnabled(),
                        mFakeFlags.getFledgeAppInstallFilteringEnabled(),
                        mFakeFlags.getFledgeAuctionServerAdRenderIdEnabled(),
                        mFakeFlags.getFledgeAuctionServerAdRenderIdMaxLength(),
                        mFakeFlags.getComponentAdRenderIdMaxLengthBytes(),
                        mFakeFlags.getMaxComponentAdsPerCustomAudience());

        assertEquals(0, reader.getAuctionServerRequestFlags());
    }

    @Test
    public void testGetAuctionServerRequestFlagsNoFlagsField() throws Exception {
        JSONObject responseObject = new JSONObject();
        JsonFixture.addHarmlessJunkValues(responseObject);
        CustomAudienceUpdatableDataReader reader =
                new CustomAudienceUpdatableDataReader(
                        responseObject,
                        RESPONSE_IDENTIFIER,
                        CommonFixture.VALID_BUYER_1,
                        mFakeFlags.getFledgeCustomAudienceMaxUserBiddingSignalsSizeB(),
                        mFakeFlags.getFledgeCustomAudienceMaxTrustedBiddingDataSizeB(),
                        mFakeFlags.getFledgeCustomAudienceMaxAdsSizeB(),
                        mFakeFlags.getFledgeCustomAudienceMaxNumAds(),
                        mFakeFlags.getFledgeFrequencyCapFilteringEnabled(),
                        mFakeFlags.getFledgeAppInstallFilteringEnabled(),
                        mFakeFlags.getFledgeAuctionServerAdRenderIdEnabled(),
                        mFakeFlags.getFledgeAuctionServerAdRenderIdMaxLength(),
                        mFakeFlags.getComponentAdRenderIdMaxLengthBytes(),
                        mFakeFlags.getMaxComponentAdsPerCustomAudience());

        assertEquals(0, reader.getAuctionServerRequestFlags());
    }

    @Test
    public void testGetAuctionServerRequestFlagsFlagsNotArray() throws Exception {
        JSONObject responseObject =
                new JSONObject().put(AUCTION_SERVER_REQUEST_FLAGS_KEY, OMIT_ADS_VALUE);
        CustomAudienceUpdatableDataReader reader =
                new CustomAudienceUpdatableDataReader(
                        responseObject,
                        RESPONSE_IDENTIFIER,
                        CommonFixture.VALID_BUYER_1,
                        mFakeFlags.getFledgeCustomAudienceMaxUserBiddingSignalsSizeB(),
                        mFakeFlags.getFledgeCustomAudienceMaxTrustedBiddingDataSizeB(),
                        mFakeFlags.getFledgeCustomAudienceMaxAdsSizeB(),
                        mFakeFlags.getFledgeCustomAudienceMaxNumAds(),
                        mFakeFlags.getFledgeFrequencyCapFilteringEnabled(),
                        mFakeFlags.getFledgeAppInstallFilteringEnabled(),
                        mFakeFlags.getFledgeAuctionServerAdRenderIdEnabled(),
                        mFakeFlags.getFledgeAuctionServerAdRenderIdMaxLength(),
                        mFakeFlags.getComponentAdRenderIdMaxLengthBytes(),
                        mFakeFlags.getMaxComponentAdsPerCustomAudience());

        assertThrows(JSONException.class, reader::getAuctionServerRequestFlags);
    }

    @Test
    public void testGetPriorityValueSuccess() throws Exception {
        JSONObject responseObject =
                CustomAudienceUpdatableDataFixture.addPriorityValueToJsonObject(
                        /* jsonResponse= */ null,
                        VALID_PRIORITY_1,
                        /* shouldAddHarmlessJunk= */ true);
        CustomAudienceUpdatableDataReader reader =
                new CustomAudienceUpdatableDataReader(
                        responseObject,
                        RESPONSE_IDENTIFIER,
                        CommonFixture.VALID_BUYER_1,
                        mFakeFlags.getFledgeCustomAudienceMaxUserBiddingSignalsSizeB(),
                        mFakeFlags.getFledgeCustomAudienceMaxTrustedBiddingDataSizeB(),
                        mFakeFlags.getFledgeCustomAudienceMaxAdsSizeB(),
                        mFakeFlags.getFledgeCustomAudienceMaxNumAds(),
                        mFakeFlags.getFledgeFrequencyCapFilteringEnabled(),
                        mFakeFlags.getFledgeAppInstallFilteringEnabled(),
                        mFakeFlags.getFledgeAuctionServerAdRenderIdEnabled(),
                        mFakeFlags.getFledgeAuctionServerAdRenderIdMaxLength(),
                        mFakeFlags.getComponentAdRenderIdMaxLengthBytes(),
                        mFakeFlags.getMaxComponentAdsPerCustomAudience());

        assertEquals(0, Double.compare(VALID_PRIORITY_1, reader.getPriority()));
    }

    @Test
    public void testGetPriorityValueNoFieldSet() throws Exception {
        JSONObject responseObject = new JSONObject();
        JsonFixture.addHarmlessJunkValues(responseObject);
        CustomAudienceUpdatableDataReader reader =
                new CustomAudienceUpdatableDataReader(
                        responseObject,
                        RESPONSE_IDENTIFIER,
                        CommonFixture.VALID_BUYER_1,
                        mFakeFlags.getFledgeCustomAudienceMaxUserBiddingSignalsSizeB(),
                        mFakeFlags.getFledgeCustomAudienceMaxTrustedBiddingDataSizeB(),
                        mFakeFlags.getFledgeCustomAudienceMaxAdsSizeB(),
                        mFakeFlags.getFledgeCustomAudienceMaxNumAds(),
                        mFakeFlags.getFledgeFrequencyCapFilteringEnabled(),
                        mFakeFlags.getFledgeAppInstallFilteringEnabled(),
                        mFakeFlags.getFledgeAuctionServerAdRenderIdEnabled(),
                        mFakeFlags.getFledgeAuctionServerAdRenderIdMaxLength(),
                        mFakeFlags.getComponentAdRenderIdMaxLengthBytes(),
                        mFakeFlags.getMaxComponentAdsPerCustomAudience());

        assertEquals(0, Double.compare(PRIORITY_DEFAULT, reader.getPriority()));
    }

    @Test
    public void testGetUserBiddingSignalsFromFullJsonObjectWithHarmlessJunkSuccess()
            throws Exception {
        String validUserBiddingSignalsAsJsonObjectString =
                JsonFixture.formatAsOrgJsonJSONObjectString(
                        CustomAudienceFixture.VALID_USER_BIDDING_SIGNALS.toString());

        JSONObject responseObject =
                CustomAudienceUpdatableDataFixture.addToJsonObject(
                        null, validUserBiddingSignalsAsJsonObjectString, true);
        CustomAudienceUpdatableDataReader reader =
                new CustomAudienceUpdatableDataReader(
                        responseObject,
                        RESPONSE_IDENTIFIER,
                        CommonFixture.VALID_BUYER_1,
                        mFakeFlags.getFledgeCustomAudienceMaxUserBiddingSignalsSizeB(),
                        mFakeFlags.getFledgeCustomAudienceMaxTrustedBiddingDataSizeB(),
                        mFakeFlags.getFledgeCustomAudienceMaxAdsSizeB(),
                        mFakeFlags.getFledgeCustomAudienceMaxNumAds(),
                        mFakeFlags.getFledgeFrequencyCapFilteringEnabled(),
                        mFakeFlags.getFledgeAppInstallFilteringEnabled(),
                        mFakeFlags.getFledgeAuctionServerAdRenderIdEnabled(),
                        mFakeFlags.getFledgeAuctionServerAdRenderIdMaxLength(),
                        mFakeFlags.getComponentAdRenderIdMaxLengthBytes(),
                        mFakeFlags.getMaxComponentAdsPerCustomAudience());

        assertEquals(
                AdSelectionSignals.fromString(validUserBiddingSignalsAsJsonObjectString),
                reader.getUserBiddingSignalsFromJsonObject());
    }

    @Test
    public void testGetUserBiddingSignalsFromEmptyJsonObject() throws Exception {
        String missingUserBiddingSignalsAsJsonObjectString = null;

        JSONObject responseObject =
                CustomAudienceUpdatableDataFixture.addToJsonObject(
                        null, missingUserBiddingSignalsAsJsonObjectString, false);
        CustomAudienceUpdatableDataReader reader =
                new CustomAudienceUpdatableDataReader(
                        responseObject,
                        RESPONSE_IDENTIFIER,
                        CommonFixture.VALID_BUYER_1,
                        mFakeFlags.getFledgeCustomAudienceMaxUserBiddingSignalsSizeB(),
                        mFakeFlags.getFledgeCustomAudienceMaxTrustedBiddingDataSizeB(),
                        mFakeFlags.getFledgeCustomAudienceMaxAdsSizeB(),
                        mFakeFlags.getFledgeCustomAudienceMaxNumAds(),
                        mFakeFlags.getFledgeFrequencyCapFilteringEnabled(),
                        mFakeFlags.getFledgeAppInstallFilteringEnabled(),
                        mFakeFlags.getFledgeAuctionServerAdRenderIdEnabled(),
                        mFakeFlags.getFledgeAuctionServerAdRenderIdMaxLength(),
                        mFakeFlags.getComponentAdRenderIdMaxLengthBytes(),
                        mFakeFlags.getMaxComponentAdsPerCustomAudience());

        assertNull(reader.getUserBiddingSignalsFromJsonObject());
    }

    @Test
    public void testGetUserBiddingSignalsFromJsonObjectMismatchedSchema() throws Exception {
        CustomAudienceUpdatableDataReader reader =
                new CustomAudienceUpdatableDataReader(
                        CustomAudienceUpdatableDataFixture.getMalformedJsonObject(),
                        RESPONSE_IDENTIFIER,
                        CommonFixture.VALID_BUYER_1,
                        mFakeFlags.getFledgeCustomAudienceMaxUserBiddingSignalsSizeB(),
                        mFakeFlags.getFledgeCustomAudienceMaxTrustedBiddingDataSizeB(),
                        mFakeFlags.getFledgeCustomAudienceMaxAdsSizeB(),
                        mFakeFlags.getFledgeCustomAudienceMaxNumAds(),
                        mFakeFlags.getFledgeFrequencyCapFilteringEnabled(),
                        mFakeFlags.getFledgeAppInstallFilteringEnabled(),
                        mFakeFlags.getFledgeAuctionServerAdRenderIdEnabled(),
                        mFakeFlags.getFledgeAuctionServerAdRenderIdMaxLength(),
                        mFakeFlags.getComponentAdRenderIdMaxLengthBytes(),
                        mFakeFlags.getMaxComponentAdsPerCustomAudience());
        assertThrows(JSONException.class, reader::getUserBiddingSignalsFromJsonObject);
    }

    @Test
    public void testGetUserBiddingSignalsFromJsonObjectMismatchedNullSchema() throws Exception {
        CustomAudienceUpdatableDataReader reader =
                new CustomAudienceUpdatableDataReader(
                        CustomAudienceUpdatableDataFixture.getMalformedNullJsonObject(),
                        RESPONSE_IDENTIFIER,
                        CommonFixture.VALID_BUYER_1,
                        mFakeFlags.getFledgeCustomAudienceMaxUserBiddingSignalsSizeB(),
                        mFakeFlags.getFledgeCustomAudienceMaxTrustedBiddingDataSizeB(),
                        mFakeFlags.getFledgeCustomAudienceMaxAdsSizeB(),
                        mFakeFlags.getFledgeCustomAudienceMaxNumAds(),
                        mFakeFlags.getFledgeFrequencyCapFilteringEnabled(),
                        mFakeFlags.getFledgeAppInstallFilteringEnabled(),
                        mFakeFlags.getFledgeAuctionServerAdRenderIdEnabled(),
                        mFakeFlags.getFledgeAuctionServerAdRenderIdMaxLength(),
                        mFakeFlags.getComponentAdRenderIdMaxLengthBytes(),
                        mFakeFlags.getMaxComponentAdsPerCustomAudience());
        assertThrows(JSONException.class, reader::getUserBiddingSignalsFromJsonObject);
    }

    @Test
    public void testGetUserBiddingSignalsFromJsonObjectDeeperMismatchedSchema() throws Exception {
        CustomAudienceUpdatableDataReader reader =
                new CustomAudienceUpdatableDataReader(
                        CustomAudienceUpdatableDataFixture.getDeeperMalformedJsonObject(),
                        RESPONSE_IDENTIFIER,
                        CommonFixture.VALID_BUYER_1,
                        mFakeFlags.getFledgeCustomAudienceMaxUserBiddingSignalsSizeB(),
                        mFakeFlags.getFledgeCustomAudienceMaxTrustedBiddingDataSizeB(),
                        mFakeFlags.getFledgeCustomAudienceMaxAdsSizeB(),
                        mFakeFlags.getFledgeCustomAudienceMaxNumAds(),
                        mFakeFlags.getFledgeFrequencyCapFilteringEnabled(),
                        mFakeFlags.getFledgeAppInstallFilteringEnabled(),
                        mFakeFlags.getFledgeAuctionServerAdRenderIdEnabled(),
                        mFakeFlags.getFledgeAuctionServerAdRenderIdMaxLength(),
                        mFakeFlags.getComponentAdRenderIdMaxLengthBytes(),
                        mFakeFlags.getMaxComponentAdsPerCustomAudience());
        assertThrows(JSONException.class, reader::getUserBiddingSignalsFromJsonObject);
    }

    @Test
    public void testGetUserBiddingSignalsFromJsonObjectInvalidSize() throws Exception {
        String validUserBiddingSignalsAsJsonObjectString =
                JsonFixture.formatAsOrgJsonJSONObjectString(
                        CustomAudienceFixture.VALID_USER_BIDDING_SIGNALS.toString());

        JSONObject responseObject =
                CustomAudienceUpdatableDataFixture.addToJsonObject(
                        null, validUserBiddingSignalsAsJsonObjectString, false);
        CustomAudienceUpdatableDataReader reader =
                new CustomAudienceUpdatableDataReader(
                        responseObject,
                        RESPONSE_IDENTIFIER,
                        CommonFixture.VALID_BUYER_1,
                        1,
                        mFakeFlags.getFledgeCustomAudienceMaxTrustedBiddingDataSizeB(),
                        mFakeFlags.getFledgeCustomAudienceMaxAdsSizeB(),
                        mFakeFlags.getFledgeCustomAudienceMaxNumAds(),
                        mFakeFlags.getFledgeFrequencyCapFilteringEnabled(),
                        mFakeFlags.getFledgeAppInstallFilteringEnabled(),
                        mFakeFlags.getFledgeAuctionServerAdRenderIdEnabled(),
                        mFakeFlags.getFledgeAuctionServerAdRenderIdMaxLength(),
                        mFakeFlags.getComponentAdRenderIdMaxLengthBytes(),
                        mFakeFlags.getMaxComponentAdsPerCustomAudience());

        assertThrows(IllegalArgumentException.class, reader::getUserBiddingSignalsFromJsonObject);
    }

    @Test
    public void testGetTrustedBiddingDataFromFullJsonObjectSuccess() throws Exception {
        DBTrustedBiddingData expectedTrustedBiddingData = VALID_TRUSTED_BIDDING_DATA;

        JSONObject responseObject =
                CustomAudienceUpdatableDataFixture.addToJsonObject(
                        null, expectedTrustedBiddingData, false);
        CustomAudienceUpdatableDataReader reader =
                new CustomAudienceUpdatableDataReader(
                        responseObject,
                        RESPONSE_IDENTIFIER,
                        CommonFixture.VALID_BUYER_1,
                        mFakeFlags.getFledgeCustomAudienceMaxUserBiddingSignalsSizeB(),
                        mFakeFlags.getFledgeCustomAudienceMaxTrustedBiddingDataSizeB(),
                        mFakeFlags.getFledgeCustomAudienceMaxAdsSizeB(),
                        mFakeFlags.getFledgeCustomAudienceMaxNumAds(),
                        mFakeFlags.getFledgeFrequencyCapFilteringEnabled(),
                        mFakeFlags.getFledgeAppInstallFilteringEnabled(),
                        mFakeFlags.getFledgeAuctionServerAdRenderIdEnabled(),
                        mFakeFlags.getFledgeAuctionServerAdRenderIdMaxLength(),
                        mFakeFlags.getComponentAdRenderIdMaxLengthBytes(),
                        mFakeFlags.getMaxComponentAdsPerCustomAudience());

        assertEquals(expectedTrustedBiddingData, reader.getTrustedBiddingDataFromJsonObject());
    }

    @Test
    public void testGetTrustedBiddingDataFromFullJsonObjectWithHarmlessJunkSuccess()
            throws Exception {
        DBTrustedBiddingData expectedTrustedBiddingData = VALID_TRUSTED_BIDDING_DATA;

        JSONObject responseObject =
                CustomAudienceUpdatableDataFixture.addToJsonObject(
                        null, expectedTrustedBiddingData, true);
        CustomAudienceUpdatableDataReader reader =
                new CustomAudienceUpdatableDataReader(
                        responseObject,
                        RESPONSE_IDENTIFIER,
                        CommonFixture.VALID_BUYER_1,
                        mFakeFlags.getFledgeCustomAudienceMaxUserBiddingSignalsSizeB(),
                        mFakeFlags.getFledgeCustomAudienceMaxTrustedBiddingDataSizeB(),
                        mFakeFlags.getFledgeCustomAudienceMaxAdsSizeB(),
                        mFakeFlags.getFledgeCustomAudienceMaxNumAds(),
                        mFakeFlags.getFledgeFrequencyCapFilteringEnabled(),
                        mFakeFlags.getFledgeAppInstallFilteringEnabled(),
                        mFakeFlags.getFledgeAuctionServerAdRenderIdEnabled(),
                        mFakeFlags.getFledgeAuctionServerAdRenderIdMaxLength(),
                        mFakeFlags.getComponentAdRenderIdMaxLengthBytes(),
                        mFakeFlags.getMaxComponentAdsPerCustomAudience());

        assertEquals(
                "responseObject = " + responseObject.toString(4),
                expectedTrustedBiddingData,
                reader.getTrustedBiddingDataFromJsonObject());
    }

    @Test
    public void testGetTrustedBiddingDataFromEmptyJsonObject() throws Exception {
        String missingTrustedBiddingDataAsJsonObjectString = null;

        JSONObject responseObject =
                CustomAudienceUpdatableDataFixture.addToJsonObject(
                        null, missingTrustedBiddingDataAsJsonObjectString, false);
        CustomAudienceUpdatableDataReader reader =
                new CustomAudienceUpdatableDataReader(
                        responseObject,
                        RESPONSE_IDENTIFIER,
                        CommonFixture.VALID_BUYER_1,
                        mFakeFlags.getFledgeCustomAudienceMaxUserBiddingSignalsSizeB(),
                        mFakeFlags.getFledgeCustomAudienceMaxTrustedBiddingDataSizeB(),
                        mFakeFlags.getFledgeCustomAudienceMaxAdsSizeB(),
                        mFakeFlags.getFledgeCustomAudienceMaxNumAds(),
                        mFakeFlags.getFledgeFrequencyCapFilteringEnabled(),
                        mFakeFlags.getFledgeAppInstallFilteringEnabled(),
                        mFakeFlags.getFledgeAuctionServerAdRenderIdEnabled(),
                        mFakeFlags.getFledgeAuctionServerAdRenderIdMaxLength(),
                        mFakeFlags.getComponentAdRenderIdMaxLengthBytes(),
                        mFakeFlags.getMaxComponentAdsPerCustomAudience());

        assertNull(reader.getTrustedBiddingDataFromJsonObject());
    }

    @Test
    public void testGetTrustedBiddingDataFromJsonObjectMismatchedSchema() throws Exception {
        CustomAudienceUpdatableDataReader reader =
                new CustomAudienceUpdatableDataReader(
                        CustomAudienceUpdatableDataFixture.getMalformedJsonObject(),
                        RESPONSE_IDENTIFIER,
                        CommonFixture.VALID_BUYER_1,
                        mFakeFlags.getFledgeCustomAudienceMaxUserBiddingSignalsSizeB(),
                        mFakeFlags.getFledgeCustomAudienceMaxTrustedBiddingDataSizeB(),
                        mFakeFlags.getFledgeCustomAudienceMaxAdsSizeB(),
                        mFakeFlags.getFledgeCustomAudienceMaxNumAds(),
                        mFakeFlags.getFledgeFrequencyCapFilteringEnabled(),
                        mFakeFlags.getFledgeAppInstallFilteringEnabled(),
                        mFakeFlags.getFledgeAuctionServerAdRenderIdEnabled(),
                        mFakeFlags.getFledgeAuctionServerAdRenderIdMaxLength(),
                        mFakeFlags.getComponentAdRenderIdMaxLengthBytes(),
                        mFakeFlags.getMaxComponentAdsPerCustomAudience());
        assertThrows(JSONException.class, reader::getTrustedBiddingDataFromJsonObject);
    }

    @Test
    public void testGetTrustedBiddingDataFromJsonObjectMismatchedNullSchema() throws Exception {
        CustomAudienceUpdatableDataReader reader =
                new CustomAudienceUpdatableDataReader(
                        CustomAudienceUpdatableDataFixture.getMalformedNullJsonObject(),
                        RESPONSE_IDENTIFIER,
                        CommonFixture.VALID_BUYER_1,
                        mFakeFlags.getFledgeCustomAudienceMaxUserBiddingSignalsSizeB(),
                        mFakeFlags.getFledgeCustomAudienceMaxTrustedBiddingDataSizeB(),
                        mFakeFlags.getFledgeCustomAudienceMaxAdsSizeB(),
                        mFakeFlags.getFledgeCustomAudienceMaxNumAds(),
                        mFakeFlags.getFledgeFrequencyCapFilteringEnabled(),
                        mFakeFlags.getFledgeAppInstallFilteringEnabled(),
                        mFakeFlags.getFledgeAuctionServerAdRenderIdEnabled(),
                        mFakeFlags.getFledgeAuctionServerAdRenderIdMaxLength(),
                        mFakeFlags.getComponentAdRenderIdMaxLengthBytes(),
                        mFakeFlags.getMaxComponentAdsPerCustomAudience());
        assertThrows(JSONException.class, reader::getTrustedBiddingDataFromJsonObject);
    }

    @Test
    public void testGetTrustedBiddingDataFromJsonObjectDeeperMismatchedSchema() throws Exception {
        CustomAudienceUpdatableDataReader reader =
                new CustomAudienceUpdatableDataReader(
                        CustomAudienceUpdatableDataFixture.getDeeperMalformedJsonObject(),
                        RESPONSE_IDENTIFIER,
                        CommonFixture.VALID_BUYER_1,
                        mFakeFlags.getFledgeCustomAudienceMaxUserBiddingSignalsSizeB(),
                        mFakeFlags.getFledgeCustomAudienceMaxTrustedBiddingDataSizeB(),
                        mFakeFlags.getFledgeCustomAudienceMaxAdsSizeB(),
                        mFakeFlags.getFledgeCustomAudienceMaxNumAds(),
                        mFakeFlags.getFledgeFrequencyCapFilteringEnabled(),
                        mFakeFlags.getFledgeAppInstallFilteringEnabled(),
                        mFakeFlags.getFledgeAuctionServerAdRenderIdEnabled(),
                        mFakeFlags.getFledgeAuctionServerAdRenderIdMaxLength(),
                        mFakeFlags.getComponentAdRenderIdMaxLengthBytes(),
                        mFakeFlags.getMaxComponentAdsPerCustomAudience());
        assertThrows(JSONException.class, reader::getTrustedBiddingDataFromJsonObject);
    }

    @Test
    public void testGetTrustedBiddingDataFromJsonObjectMismatchedUri() throws Exception {
        DBTrustedBiddingData expectedTrustedBiddingData = VALID_TRUSTED_BIDDING_DATA;

        JSONObject responseObject =
                CustomAudienceUpdatableDataFixture.addToJsonObject(
                        null, expectedTrustedBiddingData, false);
        CustomAudienceUpdatableDataReader reader =
                new CustomAudienceUpdatableDataReader(
                        responseObject,
                        RESPONSE_IDENTIFIER,
                        AdTechIdentifier.fromString("other.domain"),
                        mFakeFlags.getFledgeCustomAudienceMaxUserBiddingSignalsSizeB(),
                        mFakeFlags.getFledgeCustomAudienceMaxTrustedBiddingDataSizeB(),
                        mFakeFlags.getFledgeCustomAudienceMaxAdsSizeB(),
                        mFakeFlags.getFledgeCustomAudienceMaxNumAds(),
                        mFakeFlags.getFledgeFrequencyCapFilteringEnabled(),
                        mFakeFlags.getFledgeAppInstallFilteringEnabled(),
                        mFakeFlags.getFledgeAuctionServerAdRenderIdEnabled(),
                        mFakeFlags.getFledgeAuctionServerAdRenderIdMaxLength(),
                        mFakeFlags.getComponentAdRenderIdMaxLengthBytes(),
                        mFakeFlags.getMaxComponentAdsPerCustomAudience());

        assertThrows(IllegalArgumentException.class, reader::getTrustedBiddingDataFromJsonObject);
    }

    @Test
    public void testGetTrustedBiddingDataFromJsonObjectInvalidSize() throws Exception {
        DBTrustedBiddingData expectedTrustedBiddingData = VALID_TRUSTED_BIDDING_DATA;

        JSONObject responseObject =
                CustomAudienceUpdatableDataFixture.addToJsonObject(
                        null, expectedTrustedBiddingData, false);
        CustomAudienceUpdatableDataReader reader =
                new CustomAudienceUpdatableDataReader(
                        responseObject,
                        RESPONSE_IDENTIFIER,
                        CommonFixture.VALID_BUYER_1,
                        mFakeFlags.getFledgeCustomAudienceMaxUserBiddingSignalsSizeB(),
                        1,
                        mFakeFlags.getFledgeCustomAudienceMaxAdsSizeB(),
                        mFakeFlags.getFledgeCustomAudienceMaxNumAds(),
                        mFakeFlags.getFledgeFrequencyCapFilteringEnabled(),
                        mFakeFlags.getFledgeAppInstallFilteringEnabled(),
                        mFakeFlags.getFledgeAuctionServerAdRenderIdEnabled(),
                        mFakeFlags.getFledgeAuctionServerAdRenderIdMaxLength(),
                        mFakeFlags.getComponentAdRenderIdMaxLengthBytes(),
                        mFakeFlags.getMaxComponentAdsPerCustomAudience());

        assertThrows(IllegalArgumentException.class, reader::getTrustedBiddingDataFromJsonObject);
    }

    @Test
    public void testGetAdsFromFullJsonObjectSuccess() throws Exception {
        List<DBAdData> expectedAds = VALID_DB_AD_DATA_LIST;

        JSONObject responseObject =
                CustomAudienceUpdatableDataFixture.addToJsonObject(null, expectedAds, false);
        CustomAudienceUpdatableDataReader reader =
                new CustomAudienceUpdatableDataReader(
                        responseObject,
                        RESPONSE_IDENTIFIER,
                        CommonFixture.VALID_BUYER_1,
                        mFakeFlags.getFledgeCustomAudienceMaxUserBiddingSignalsSizeB(),
                        mFakeFlags.getFledgeCustomAudienceMaxTrustedBiddingDataSizeB(),
                        mFakeFlags.getFledgeCustomAudienceMaxAdsSizeB(),
                        mFakeFlags.getFledgeCustomAudienceMaxNumAds(),
                        mFakeFlags.getFledgeFrequencyCapFilteringEnabled(),
                        mFakeFlags.getFledgeAppInstallFilteringEnabled(),
                        mFakeFlags.getFledgeAuctionServerAdRenderIdEnabled(),
                        mFakeFlags.getFledgeAuctionServerAdRenderIdMaxLength(),
                        mFakeFlags.getComponentAdRenderIdMaxLengthBytes(),
                        mFakeFlags.getMaxComponentAdsPerCustomAudience());

        assertEquals(expectedAds, reader.getAdsFromJsonObject());
    }

    @Test
    public void testGetAdsFromFullJsonObjectFilteringOffSuccess() throws Exception {
        List<DBAdData> inputAds =
                DBAdDataFixture.getValidDbAdDataListByBuyer(CommonFixture.VALID_BUYER_1);
        List<DBAdData> expectedAds =
                DBAdDataFixture.getValidDbAdDataListByBuyerNoFilters(CommonFixture.VALID_BUYER_1);
        JSONObject responseObject =
                CustomAudienceUpdatableDataFixture.addToJsonObject(null, inputAds, false);
        CustomAudienceUpdatableDataReader reader =
                new CustomAudienceUpdatableDataReader(
                        responseObject,
                        RESPONSE_IDENTIFIER,
                        CommonFixture.VALID_BUYER_1,
                        mFakeFlags.getFledgeCustomAudienceMaxUserBiddingSignalsSizeB(),
                        mFakeFlags.getFledgeCustomAudienceMaxTrustedBiddingDataSizeB(),
                        mFakeFlags.getFledgeCustomAudienceMaxAdsSizeB(),
                        mFakeFlags.getFledgeCustomAudienceMaxNumAds(),
                        false,
                        false,
                        mFakeFlags.getFledgeAuctionServerAdRenderIdEnabled(),
                        mFakeFlags.getFledgeAuctionServerAdRenderIdMaxLength(),
                        mFakeFlags.getComponentAdRenderIdMaxLengthBytes(),
                        mFakeFlags.getMaxComponentAdsPerCustomAudience());

        assertEquals(expectedAds, reader.getAdsFromJsonObject());
    }

    @Test
    public void testGetAdsFromFullJsonObjectAdRenderIdOnSuccess() throws Exception {
        List<DBAdData> inputAds =
                DBAdDataFixture.getValidDbAdDataListByBuyerWithAdRenderId(
                        CommonFixture.VALID_BUYER_1);
        List<DBAdData> expectedAds =
                DBAdDataFixture.getValidDbAdDataListByBuyerWithAdRenderId(
                        CommonFixture.VALID_BUYER_1);
        JSONObject responseObject =
                CustomAudienceUpdatableDataFixture.addToJsonObject(null, inputAds, false);
        CustomAudienceUpdatableDataReader reader =
                new CustomAudienceUpdatableDataReader(
                        responseObject,
                        RESPONSE_IDENTIFIER,
                        CommonFixture.VALID_BUYER_1,
                        mFakeFlags.getFledgeCustomAudienceMaxUserBiddingSignalsSizeB(),
                        mFakeFlags.getFledgeCustomAudienceMaxTrustedBiddingDataSizeB(),
                        mFakeFlags.getFledgeCustomAudienceMaxAdsSizeB(),
                        mFakeFlags.getFledgeCustomAudienceMaxNumAds(),
                        mFakeFlags.getFledgeFrequencyCapFilteringEnabled(),
                        mFakeFlags.getFledgeAppInstallFilteringEnabled(),
                        true,
                        mFakeFlags.getFledgeAuctionServerAdRenderIdMaxLength(),
                        mFakeFlags.getComponentAdRenderIdMaxLengthBytes(),
                        mFakeFlags.getMaxComponentAdsPerCustomAudience());

        assertEquals(expectedAds, reader.getAdsFromJsonObject());
    }

    @Test
    public void testGetAdsFromFullJsonObjectAdRenderIdOff_Success_dropAdRenderId()
            throws Exception {
        List<DBAdData> inputAds =
                DBAdDataFixture.getValidDbAdDataListByBuyerWithAdRenderId(
                        CommonFixture.VALID_BUYER_1);
        List<DBAdData> expectedAds =
                DBAdDataFixture.getValidDbAdDataListByBuyerWithAdRenderId(
                                CommonFixture.VALID_BUYER_1)
                        .stream()
                        .map(
                                adData ->
                                        new DBAdData(
                                                adData.getRenderUri(),
                                                adData.getMetadata(),
                                                adData.getAdCounterKeys(),
                                                adData.getAdFilters(),
                                                null))
                        .collect(Collectors.toList());
        JSONObject responseObject =
                CustomAudienceUpdatableDataFixture.addToJsonObject(null, inputAds, false);
        CustomAudienceUpdatableDataReader reader =
                new CustomAudienceUpdatableDataReader(
                        responseObject,
                        RESPONSE_IDENTIFIER,
                        CommonFixture.VALID_BUYER_1,
                        mFakeFlags.getFledgeCustomAudienceMaxUserBiddingSignalsSizeB(),
                        mFakeFlags.getFledgeCustomAudienceMaxTrustedBiddingDataSizeB(),
                        mFakeFlags.getFledgeCustomAudienceMaxAdsSizeB(),
                        mFakeFlags.getFledgeCustomAudienceMaxNumAds(),
                        mFakeFlags.getFledgeFrequencyCapFilteringEnabled(),
                        mFakeFlags.getFledgeAppInstallFilteringEnabled(),
                        false,
                        mFakeFlags.getFledgeAuctionServerAdRenderIdMaxLength(),
                        mFakeFlags.getComponentAdRenderIdMaxLengthBytes(),
                        mFakeFlags.getMaxComponentAdsPerCustomAudience());

        assertEquals(expectedAds, reader.getAdsFromJsonObject());
    }

    @Test
    public void testGetAdsFromJsonObjectWithMalformedAdRenderId() throws Exception {
        List<DBAdData> inputAds =
                ImmutableList.of(DBAdDataFixture.getValidDbAdDataBuilder().build());
        JSONObject responseObject =
                CustomAudienceUpdatableDataFixture.addToJsonObject(null, inputAds, false);
        JSONObject wrongAdRenderIdJsonObject = new JSONObject().put("ik", "v");
        responseObject
                .getJSONArray(ADS_KEY)
                .getJSONObject(0)
                .put(AD_RENDER_ID_KEY, wrongAdRenderIdJsonObject);
        CustomAudienceUpdatableDataReader reader =
                new CustomAudienceUpdatableDataReader(
                        responseObject,
                        RESPONSE_IDENTIFIER,
                        CommonFixture.VALID_BUYER_1,
                        mFakeFlags.getFledgeCustomAudienceMaxUserBiddingSignalsSizeB(),
                        mFakeFlags.getFledgeCustomAudienceMaxTrustedBiddingDataSizeB(),
                        mFakeFlags.getFledgeCustomAudienceMaxAdsSizeB(),
                        mFakeFlags.getFledgeCustomAudienceMaxNumAds(),
                        mFakeFlags.getFledgeFrequencyCapFilteringEnabled(),
                        mFakeFlags.getFledgeAppInstallFilteringEnabled(),
                        true,
                        mFakeFlags.getFledgeAuctionServerAdRenderIdMaxLength(),
                        mFakeFlags.getComponentAdRenderIdMaxLengthBytes(),
                        mFakeFlags.getMaxComponentAdsPerCustomAudience());
        assertTrue(reader.getAdsFromJsonObject().isEmpty());
    }

    @Test
    public void testGetAdsFromJsonObjectWithTooLongAdRenderId() throws Exception {
        List<DBAdData> inputAds =
                ImmutableList.of(
                        DBAdDataFixture.getValidDbAdDataBuilder()
                                .setAdRenderId("IamExtremelyLongLongLong")
                                .build());
        JSONObject responseObject =
                CustomAudienceUpdatableDataFixture.addToJsonObject(null, inputAds, false);
        CustomAudienceUpdatableDataReader reader =
                new CustomAudienceUpdatableDataReader(
                        responseObject,
                        RESPONSE_IDENTIFIER,
                        CommonFixture.VALID_BUYER_1,
                        mFakeFlags.getFledgeCustomAudienceMaxUserBiddingSignalsSizeB(),
                        mFakeFlags.getFledgeCustomAudienceMaxTrustedBiddingDataSizeB(),
                        mFakeFlags.getFledgeCustomAudienceMaxAdsSizeB(),
                        mFakeFlags.getFledgeCustomAudienceMaxNumAds(),
                        mFakeFlags.getFledgeFrequencyCapFilteringEnabled(),
                        mFakeFlags.getFledgeAppInstallFilteringEnabled(),
                        true,
                        mFakeFlags.getFledgeAuctionServerAdRenderIdMaxLength(),
                        mFakeFlags.getComponentAdRenderIdMaxLengthBytes(),
                        mFakeFlags.getMaxComponentAdsPerCustomAudience());
        assertTrue(reader.getAdsFromJsonObject().isEmpty());
    }

    @Test
    public void testGetAdsFromFullJsonObjectWithHarmlessJunkSuccess() throws Exception {
        List<DBAdData> expectedAds = VALID_DB_AD_DATA_LIST;

        JSONObject responseObject =
                CustomAudienceUpdatableDataFixture.addToJsonObject(null, expectedAds, true);
        CustomAudienceUpdatableDataReader reader =
                new CustomAudienceUpdatableDataReader(
                        responseObject,
                        RESPONSE_IDENTIFIER,
                        CommonFixture.VALID_BUYER_1,
                        mFakeFlags.getFledgeCustomAudienceMaxUserBiddingSignalsSizeB(),
                        mFakeFlags.getFledgeCustomAudienceMaxTrustedBiddingDataSizeB(),
                        mFakeFlags.getFledgeCustomAudienceMaxAdsSizeB(),
                        mFakeFlags.getFledgeCustomAudienceMaxNumAds(),
                        mFakeFlags.getFledgeFrequencyCapFilteringEnabled(),
                        mFakeFlags.getFledgeAppInstallFilteringEnabled(),
                        mFakeFlags.getFledgeAuctionServerAdRenderIdEnabled(),
                        mFakeFlags.getFledgeAuctionServerAdRenderIdMaxLength(),
                        mFakeFlags.getComponentAdRenderIdMaxLengthBytes(),
                        mFakeFlags.getMaxComponentAdsPerCustomAudience());

        assertEquals(
                "responseObject = " + responseObject.toString(4),
                expectedAds,
                reader.getAdsFromJsonObject());
    }

    @Test
    public void testGetAdsFromEmptyJsonObject() throws Exception {
        String missingAdsAsJsonObjectString = null;

        JSONObject responseObject =
                CustomAudienceUpdatableDataFixture.addToJsonObject(
                        null, missingAdsAsJsonObjectString, false);
        CustomAudienceUpdatableDataReader reader =
                new CustomAudienceUpdatableDataReader(
                        responseObject,
                        RESPONSE_IDENTIFIER,
                        CommonFixture.VALID_BUYER_1,
                        mFakeFlags.getFledgeCustomAudienceMaxUserBiddingSignalsSizeB(),
                        mFakeFlags.getFledgeCustomAudienceMaxTrustedBiddingDataSizeB(),
                        mFakeFlags.getFledgeCustomAudienceMaxAdsSizeB(),
                        mFakeFlags.getFledgeCustomAudienceMaxNumAds(),
                        mFakeFlags.getFledgeFrequencyCapFilteringEnabled(),
                        mFakeFlags.getFledgeAppInstallFilteringEnabled(),
                        mFakeFlags.getFledgeAuctionServerAdRenderIdEnabled(),
                        mFakeFlags.getFledgeAuctionServerAdRenderIdMaxLength(),
                        mFakeFlags.getComponentAdRenderIdMaxLengthBytes(),
                        mFakeFlags.getMaxComponentAdsPerCustomAudience());

        assertNull(reader.getAdsFromJsonObject());
    }

    @Test
    public void testGetAdsFromJsonObjectMismatchedSchema() throws Exception {
        CustomAudienceUpdatableDataReader reader =
                new CustomAudienceUpdatableDataReader(
                        CustomAudienceUpdatableDataFixture.getMalformedJsonObject(),
                        RESPONSE_IDENTIFIER,
                        CommonFixture.VALID_BUYER_1,
                        mFakeFlags.getFledgeCustomAudienceMaxUserBiddingSignalsSizeB(),
                        mFakeFlags.getFledgeCustomAudienceMaxTrustedBiddingDataSizeB(),
                        mFakeFlags.getFledgeCustomAudienceMaxAdsSizeB(),
                        mFakeFlags.getFledgeCustomAudienceMaxNumAds(),
                        mFakeFlags.getFledgeFrequencyCapFilteringEnabled(),
                        mFakeFlags.getFledgeAppInstallFilteringEnabled(),
                        mFakeFlags.getFledgeAuctionServerAdRenderIdEnabled(),
                        mFakeFlags.getFledgeAuctionServerAdRenderIdMaxLength(),
                        mFakeFlags.getComponentAdRenderIdMaxLengthBytes(),
                        mFakeFlags.getMaxComponentAdsPerCustomAudience());
        assertThrows(JSONException.class, reader::getAdsFromJsonObject);
    }

    @Test
    public void testGetAdsFromJsonObjectMismatchedNullSchema() throws Exception {
        CustomAudienceUpdatableDataReader reader =
                new CustomAudienceUpdatableDataReader(
                        CustomAudienceUpdatableDataFixture.getMalformedNullJsonObject(),
                        RESPONSE_IDENTIFIER,
                        CommonFixture.VALID_BUYER_1,
                        mFakeFlags.getFledgeCustomAudienceMaxUserBiddingSignalsSizeB(),
                        mFakeFlags.getFledgeCustomAudienceMaxTrustedBiddingDataSizeB(),
                        mFakeFlags.getFledgeCustomAudienceMaxAdsSizeB(),
                        mFakeFlags.getFledgeCustomAudienceMaxNumAds(),
                        mFakeFlags.getFledgeFrequencyCapFilteringEnabled(),
                        mFakeFlags.getFledgeAppInstallFilteringEnabled(),
                        mFakeFlags.getFledgeAuctionServerAdRenderIdEnabled(),
                        mFakeFlags.getFledgeAuctionServerAdRenderIdMaxLength(),
                        mFakeFlags.getComponentAdRenderIdMaxLengthBytes(),
                        mFakeFlags.getMaxComponentAdsPerCustomAudience());
        assertThrows(JSONException.class, reader::getAdsFromJsonObject);
    }

    @Test
    public void testGetAdsFromJsonObjectDeeperMismatchedSchema() throws Exception {
        CustomAudienceUpdatableDataReader reader =
                new CustomAudienceUpdatableDataReader(
                        CustomAudienceUpdatableDataFixture.getDeeperMalformedJsonObject(),
                        RESPONSE_IDENTIFIER,
                        CommonFixture.VALID_BUYER_1,
                        mFakeFlags.getFledgeCustomAudienceMaxUserBiddingSignalsSizeB(),
                        mFakeFlags.getFledgeCustomAudienceMaxTrustedBiddingDataSizeB(),
                        mFakeFlags.getFledgeCustomAudienceMaxAdsSizeB(),
                        mFakeFlags.getFledgeCustomAudienceMaxNumAds(),
                        mFakeFlags.getFledgeFrequencyCapFilteringEnabled(),
                        mFakeFlags.getFledgeAppInstallFilteringEnabled(),
                        mFakeFlags.getFledgeAuctionServerAdRenderIdEnabled(),
                        mFakeFlags.getFledgeAuctionServerAdRenderIdMaxLength(),
                        mFakeFlags.getComponentAdRenderIdMaxLengthBytes(),
                        mFakeFlags.getMaxComponentAdsPerCustomAudience());

        List<DBAdData> extractedAds = reader.getAdsFromJsonObject();
        assertNotNull(extractedAds);
        assertTrue(extractedAds.isEmpty());
    }

    @Test
    public void testGetAdsFromJsonObjectWithInvalidAdsMetadata() throws Exception {
        CustomAudienceUpdatableDataReader reader =
                new CustomAudienceUpdatableDataReader(
                        CustomAudienceUpdatableDataFixture.addToJsonObject(
                                null, INVALID_DB_AD_DATA_LIST, false),
                        RESPONSE_IDENTIFIER,
                        CommonFixture.VALID_BUYER_1,
                        mFakeFlags.getFledgeCustomAudienceMaxUserBiddingSignalsSizeB(),
                        mFakeFlags.getFledgeCustomAudienceMaxTrustedBiddingDataSizeB(),
                        mFakeFlags.getFledgeCustomAudienceMaxAdsSizeB(),
                        mFakeFlags.getFledgeCustomAudienceMaxNumAds(),
                        mFakeFlags.getFledgeFrequencyCapFilteringEnabled(),
                        mFakeFlags.getFledgeAppInstallFilteringEnabled(),
                        mFakeFlags.getFledgeAuctionServerAdRenderIdEnabled(),
                        mFakeFlags.getFledgeAuctionServerAdRenderIdMaxLength(),
                        mFakeFlags.getComponentAdRenderIdMaxLengthBytes(),
                        mFakeFlags.getMaxComponentAdsPerCustomAudience());

        List<DBAdData> extractedAds = reader.getAdsFromJsonObject();
        assertNotNull(extractedAds);
        assertTrue(extractedAds.isEmpty());
    }

    @Test
    public void testGetAdsFromJsonObjectInvalidTotalSize() throws Exception {
        List<DBAdData> expectedAds = VALID_DB_AD_DATA_LIST;

        JSONObject responseObject =
                CustomAudienceUpdatableDataFixture.addToJsonObject(null, expectedAds, false);
        CustomAudienceUpdatableDataReader reader =
                new CustomAudienceUpdatableDataReader(
                        responseObject,
                        RESPONSE_IDENTIFIER,
                        CommonFixture.VALID_BUYER_1,
                        mFakeFlags.getFledgeCustomAudienceMaxUserBiddingSignalsSizeB(),
                        mFakeFlags.getFledgeCustomAudienceMaxTrustedBiddingDataSizeB(),
                        1,
                        mFakeFlags.getFledgeCustomAudienceMaxNumAds(),
                        mFakeFlags.getFledgeFrequencyCapFilteringEnabled(),
                        mFakeFlags.getFledgeAppInstallFilteringEnabled(),
                        mFakeFlags.getFledgeAuctionServerAdRenderIdEnabled(),
                        mFakeFlags.getFledgeAuctionServerAdRenderIdMaxLength(),
                        mFakeFlags.getComponentAdRenderIdMaxLengthBytes(),
                        mFakeFlags.getMaxComponentAdsPerCustomAudience());

        assertThrows(IllegalArgumentException.class, reader::getAdsFromJsonObject);
    }

    @Test
    public void testGetAdsFromJsonObjectInvalidNumAds() throws Exception {
        List<DBAdData> expectedAds = VALID_DB_AD_DATA_LIST;

        JSONObject responseObject =
                CustomAudienceUpdatableDataFixture.addToJsonObject(null, expectedAds, false);
        CustomAudienceUpdatableDataReader reader =
                new CustomAudienceUpdatableDataReader(
                        responseObject,
                        RESPONSE_IDENTIFIER,
                        CommonFixture.VALID_BUYER_1,
                        mFakeFlags.getFledgeCustomAudienceMaxUserBiddingSignalsSizeB(),
                        mFakeFlags.getFledgeCustomAudienceMaxTrustedBiddingDataSizeB(),
                        mFakeFlags.getFledgeCustomAudienceMaxAdsSizeB(),
                        1,
                        mFakeFlags.getFledgeFrequencyCapFilteringEnabled(),
                        mFakeFlags.getFledgeAppInstallFilteringEnabled(),
                        mFakeFlags.getFledgeAuctionServerAdRenderIdEnabled(),
                        mFakeFlags.getFledgeAuctionServerAdRenderIdMaxLength(),
                        mFakeFlags.getComponentAdRenderIdMaxLengthBytes(),
                        mFakeFlags.getMaxComponentAdsPerCustomAudience());

        assertThrows(IllegalArgumentException.class, reader::getAdsFromJsonObject);
    }

    @Test
    public void testGetComponentAdsAdsFromFullJsonObjectSuccess() throws Exception {
        List<ComponentAdData> expectedAds = VALID_COMPONENT_ADS_LIST;

        JSONObject responseObject =
                CustomAudienceUpdatableDataFixture.addComponentAdsToJsonObject(
                        /* jsonResponse= */ null, expectedAds, /* shouldAddHarmlessJunk= */ false);
        CustomAudienceUpdatableDataReader reader =
                new CustomAudienceUpdatableDataReader(
                        responseObject,
                        RESPONSE_IDENTIFIER,
                        CommonFixture.VALID_BUYER_1,
                        mFakeFlags.getFledgeCustomAudienceMaxUserBiddingSignalsSizeB(),
                        mFakeFlags.getFledgeCustomAudienceMaxTrustedBiddingDataSizeB(),
                        mFakeFlags.getFledgeCustomAudienceMaxAdsSizeB(),
                        mFakeFlags.getFledgeCustomAudienceMaxNumAds(),
                        mFakeFlags.getFledgeFrequencyCapFilteringEnabled(),
                        mFakeFlags.getFledgeAppInstallFilteringEnabled(),
                        mFakeFlags.getFledgeAuctionServerAdRenderIdEnabled(),
                        mFakeFlags.getFledgeAuctionServerAdRenderIdMaxLength(),
                        mFakeFlags.getComponentAdRenderIdMaxLengthBytes(),
                        mFakeFlags.getMaxComponentAdsPerCustomAudience());

        expect.that(reader.getComponentAdsFromJsonObject()).isEqualTo(expectedAds);
    }

    @Test
    public void testGetComponentAdsAdsFromEmptyJsonObjectSuccess() throws Exception {
        JSONObject responseObject = new JSONObject();
        CustomAudienceUpdatableDataReader reader =
                new CustomAudienceUpdatableDataReader(
                        responseObject,
                        RESPONSE_IDENTIFIER,
                        CommonFixture.VALID_BUYER_1,
                        mFakeFlags.getFledgeCustomAudienceMaxUserBiddingSignalsSizeB(),
                        mFakeFlags.getFledgeCustomAudienceMaxTrustedBiddingDataSizeB(),
                        mFakeFlags.getFledgeCustomAudienceMaxAdsSizeB(),
                        mFakeFlags.getFledgeCustomAudienceMaxNumAds(),
                        mFakeFlags.getFledgeFrequencyCapFilteringEnabled(),
                        mFakeFlags.getFledgeAppInstallFilteringEnabled(),
                        mFakeFlags.getFledgeAuctionServerAdRenderIdEnabled(),
                        mFakeFlags.getFledgeAuctionServerAdRenderIdMaxLength(),
                        mFakeFlags.getComponentAdRenderIdMaxLengthBytes(),
                        mFakeFlags.getMaxComponentAdsPerCustomAudience());

        expect.that(reader.getComponentAdsFromJsonObject()).isNull();
    }

    @Test
    public void testGetComponentAdsFromFullJsonObjectWithJunkSkipsJunk() throws Exception {
        List<ComponentAdData> expectedAds = VALID_COMPONENT_ADS_LIST;

        JSONObject responseObject =
                CustomAudienceUpdatableDataFixture.addComponentAdsToJsonObject(
                        /* jsonResponse= */ null, expectedAds, /* shouldAddHarmlessJunk= */ true);
        CustomAudienceUpdatableDataReader reader =
                new CustomAudienceUpdatableDataReader(
                        responseObject,
                        RESPONSE_IDENTIFIER,
                        CommonFixture.VALID_BUYER_1,
                        mFakeFlags.getFledgeCustomAudienceMaxUserBiddingSignalsSizeB(),
                        mFakeFlags.getFledgeCustomAudienceMaxTrustedBiddingDataSizeB(),
                        mFakeFlags.getFledgeCustomAudienceMaxAdsSizeB(),
                        mFakeFlags.getFledgeCustomAudienceMaxNumAds(),
                        mFakeFlags.getFledgeFrequencyCapFilteringEnabled(),
                        mFakeFlags.getFledgeAppInstallFilteringEnabled(),
                        mFakeFlags.getFledgeAuctionServerAdRenderIdEnabled(),
                        mFakeFlags.getFledgeAuctionServerAdRenderIdMaxLength(),
                        mFakeFlags.getComponentAdRenderIdMaxLengthBytes(),
                        mFakeFlags.getMaxComponentAdsPerCustomAudience());

        expect.that(reader.getComponentAdsFromJsonObject()).isEqualTo(expectedAds);
    }

    @Test
    public void testGetComponentAdsThrowsExceptionExceedingMaxNumComponentAds() throws Exception {
        JSONObject responseObject =
                CustomAudienceUpdatableDataFixture.addComponentAdsToJsonObject(
                        /* jsonResponse= */ null,
                        VALID_COMPONENT_ADS_LIST,
                        /* shouldAddHarmlessJunk= */ false);
        CustomAudienceUpdatableDataReader reader =
                new CustomAudienceUpdatableDataReader(
                        responseObject,
                        RESPONSE_IDENTIFIER,
                        CommonFixture.VALID_BUYER_1,
                        mFakeFlags.getFledgeCustomAudienceMaxUserBiddingSignalsSizeB(),
                        mFakeFlags.getFledgeCustomAudienceMaxTrustedBiddingDataSizeB(),
                        mFakeFlags.getFledgeCustomAudienceMaxAdsSizeB(),
                        mFakeFlags.getFledgeCustomAudienceMaxNumAds(),
                        mFakeFlags.getFledgeFrequencyCapFilteringEnabled(),
                        mFakeFlags.getFledgeAppInstallFilteringEnabled(),
                        mFakeFlags.getFledgeAuctionServerAdRenderIdEnabled(),
                        mFakeFlags.getFledgeAuctionServerAdRenderIdMaxLength(),
                        mFakeFlags.getComponentAdRenderIdMaxLengthBytes(),
                        VALID_COMPONENT_ADS_LIST.size() - 1);

        Exception exception =
                assertThrows(IllegalArgumentException.class, reader::getComponentAdsFromJsonObject);
        assertThat(exception.getMessage()).isEqualTo(COMPONENT_ADS_SIZE_EXCEEDS_MAX);
    }

    @Test
    public void testGetComponentAdsSkipsComponentWithTooLongRenderId() throws Exception {
        List<ComponentAdData> expectedAds = new ArrayList<>();

        ComponentAdData componentAdData1 =
                ComponentAdDataFixture.getValidComponentAdDataByBuyer(
                        CommonFixture.VALID_BUYER_1, 0);

        expectedAds.add(componentAdData1);
        expectedAds.add(
                ComponentAdDataFixture.getValidComponentAdDataWithAdRenderId(
                        CommonFixture.VALID_BUYER_1, 1, "I am extremely loooooong"));

        JSONObject responseObject =
                CustomAudienceUpdatableDataFixture.addComponentAdsToJsonObject(
                        null, expectedAds, false);
        CustomAudienceUpdatableDataReader reader =
                new CustomAudienceUpdatableDataReader(
                        responseObject,
                        RESPONSE_IDENTIFIER,
                        CommonFixture.VALID_BUYER_1,
                        mFakeFlags.getFledgeCustomAudienceMaxUserBiddingSignalsSizeB(),
                        mFakeFlags.getFledgeCustomAudienceMaxTrustedBiddingDataSizeB(),
                        mFakeFlags.getFledgeCustomAudienceMaxAdsSizeB(),
                        mFakeFlags.getFledgeCustomAudienceMaxNumAds(),
                        mFakeFlags.getFledgeFrequencyCapFilteringEnabled(),
                        mFakeFlags.getFledgeAppInstallFilteringEnabled(),
                        mFakeFlags.getFledgeAuctionServerAdRenderIdEnabled(),
                        mFakeFlags.getFledgeAuctionServerAdRenderIdMaxLength(),
                        mFakeFlags.getComponentAdRenderIdMaxLengthBytes(),
                        mFakeFlags.getMaxComponentAdsPerCustomAudience());

        expect.that(reader.getComponentAdsFromJsonObject()).isEqualTo(List.of(componentAdData1));
    }

    @Test
    public void testGetComponentAdsSkipsComponentAdWithIncorrectBuyer() throws Exception {
        List<ComponentAdData> expectedAds = new ArrayList<>();

        ComponentAdData componentAdData1 =
                ComponentAdDataFixture.getValidComponentAdDataByBuyer(
                        CommonFixture.VALID_BUYER_1, 0);

        expectedAds.add(componentAdData1);
        expectedAds.add(
                ComponentAdDataFixture.getValidComponentAdDataByBuyer(
                        CommonFixture.VALID_BUYER_2, 1));

        JSONObject responseObject =
                CustomAudienceUpdatableDataFixture.addComponentAdsToJsonObject(
                        null, expectedAds, false);
        CustomAudienceUpdatableDataReader reader =
                new CustomAudienceUpdatableDataReader(
                        responseObject,
                        RESPONSE_IDENTIFIER,
                        CommonFixture.VALID_BUYER_1,
                        mFakeFlags.getFledgeCustomAudienceMaxUserBiddingSignalsSizeB(),
                        mFakeFlags.getFledgeCustomAudienceMaxTrustedBiddingDataSizeB(),
                        mFakeFlags.getFledgeCustomAudienceMaxAdsSizeB(),
                        mFakeFlags.getFledgeCustomAudienceMaxNumAds(),
                        mFakeFlags.getFledgeFrequencyCapFilteringEnabled(),
                        mFakeFlags.getFledgeAppInstallFilteringEnabled(),
                        mFakeFlags.getFledgeAuctionServerAdRenderIdEnabled(),
                        mFakeFlags.getFledgeAuctionServerAdRenderIdMaxLength(),
                        mFakeFlags.getComponentAdRenderIdMaxLengthBytes(),
                        mFakeFlags.getMaxComponentAdsPerCustomAudience());

        expect.that(reader.getComponentAdsFromJsonObject()).isEqualTo(List.of(componentAdData1));
    }

    @Test
    public void testGetComponentAdsSkipsComponentWithMalformedAdRenderId() throws Exception {
        List<ComponentAdData> expectedAds = new ArrayList<>();

        ComponentAdData componentAdData1 =
                ComponentAdDataFixture.getValidComponentAdDataByBuyer(
                        CommonFixture.VALID_BUYER_1, 0);

        expectedAds.add(componentAdData1);
        expectedAds.add(
                ComponentAdDataFixture.getValidComponentAdDataByBuyer(
                        CommonFixture.VALID_BUYER_2, 1));

        JSONObject responseObject =
                CustomAudienceUpdatableDataFixture.addComponentAdsToJsonObject(
                        null, expectedAds, false);

        JSONObject wrongAdRenderIdJsonObject = new JSONObject().put("ik", "v");
        responseObject
                .getJSONArray(COMPONENT_ADS_KEY)
                .getJSONObject(1)
                .put(AD_RENDER_ID_KEY, wrongAdRenderIdJsonObject);
        CustomAudienceUpdatableDataReader reader =
                new CustomAudienceUpdatableDataReader(
                        responseObject,
                        RESPONSE_IDENTIFIER,
                        CommonFixture.VALID_BUYER_1,
                        mFakeFlags.getFledgeCustomAudienceMaxUserBiddingSignalsSizeB(),
                        mFakeFlags.getFledgeCustomAudienceMaxTrustedBiddingDataSizeB(),
                        mFakeFlags.getFledgeCustomAudienceMaxAdsSizeB(),
                        mFakeFlags.getFledgeCustomAudienceMaxNumAds(),
                        mFakeFlags.getFledgeFrequencyCapFilteringEnabled(),
                        mFakeFlags.getFledgeAppInstallFilteringEnabled(),
                        mFakeFlags.getFledgeAuctionServerAdRenderIdEnabled(),
                        mFakeFlags.getFledgeAuctionServerAdRenderIdMaxLength(),
                        mFakeFlags.getComponentAdRenderIdMaxLengthBytes(),
                        mFakeFlags.getMaxComponentAdsPerCustomAudience());

        expect.that(reader.getComponentAdsFromJsonObject()).isEqualTo(List.of(componentAdData1));
    }

    @Test
    public void testGetComponentAdsSkipsComponentWithMalformedRenderUri() throws Exception {
        List<ComponentAdData> expectedAds = new ArrayList<>();

        ComponentAdData componentAdData1 =
                ComponentAdDataFixture.getValidComponentAdDataByBuyer(
                        CommonFixture.VALID_BUYER_1, 0);

        expectedAds.add(componentAdData1);

        expectedAds.add(
                ComponentAdDataFixture.getValidComponentAdDataByBuyer(
                        CommonFixture.VALID_BUYER_2, 1));

        JSONObject responseObject =
                CustomAudienceUpdatableDataFixture.addComponentAdsToJsonObject(
                        null, expectedAds, false);

        JSONObject wrongRenderUriJsonObject = new JSONObject().put("ik", "v");
        responseObject
                .getJSONArray(COMPONENT_ADS_KEY)
                .getJSONObject(1)
                .put(RENDER_URI_KEY, wrongRenderUriJsonObject);
        CustomAudienceUpdatableDataReader reader =
                new CustomAudienceUpdatableDataReader(
                        responseObject,
                        RESPONSE_IDENTIFIER,
                        CommonFixture.VALID_BUYER_1,
                        mFakeFlags.getFledgeCustomAudienceMaxUserBiddingSignalsSizeB(),
                        mFakeFlags.getFledgeCustomAudienceMaxTrustedBiddingDataSizeB(),
                        mFakeFlags.getFledgeCustomAudienceMaxAdsSizeB(),
                        mFakeFlags.getFledgeCustomAudienceMaxNumAds(),
                        mFakeFlags.getFledgeFrequencyCapFilteringEnabled(),
                        mFakeFlags.getFledgeAppInstallFilteringEnabled(),
                        mFakeFlags.getFledgeAuctionServerAdRenderIdEnabled(),
                        mFakeFlags.getFledgeAuctionServerAdRenderIdMaxLength(),
                        mFakeFlags.getComponentAdRenderIdMaxLengthBytes(),
                        mFakeFlags.getMaxComponentAdsPerCustomAudience());

        expect.that(reader.getComponentAdsFromJsonObject()).isEqualTo(List.of(componentAdData1));
    }

    @Test
    public void testGetComponentAdsSkipsComponentWithInvalidRenderUri() throws Exception {
        List<ComponentAdData> expectedAds = new ArrayList<>();

        ComponentAdData componentAdData1 =
                ComponentAdDataFixture.getValidComponentAdDataByBuyer(
                        CommonFixture.VALID_BUYER_1, 0);

        expectedAds.add(componentAdData1);
        expectedAds.add(
                ComponentAdDataFixture.getValidComponentAdDataByBuyer(
                        CommonFixture.VALID_BUYER_2, 1));

        JSONObject responseObject =
                CustomAudienceUpdatableDataFixture.addComponentAdsToJsonObject(
                        null, expectedAds, false);

        String invalidUri = "I'm an invalid Uri";
        responseObject
                .getJSONArray(COMPONENT_ADS_KEY)
                .getJSONObject(1)
                .put(RENDER_URI_KEY, invalidUri);
        CustomAudienceUpdatableDataReader reader =
                new CustomAudienceUpdatableDataReader(
                        responseObject,
                        RESPONSE_IDENTIFIER,
                        CommonFixture.VALID_BUYER_1,
                        mFakeFlags.getFledgeCustomAudienceMaxUserBiddingSignalsSizeB(),
                        mFakeFlags.getFledgeCustomAudienceMaxTrustedBiddingDataSizeB(),
                        mFakeFlags.getFledgeCustomAudienceMaxAdsSizeB(),
                        mFakeFlags.getFledgeCustomAudienceMaxNumAds(),
                        mFakeFlags.getFledgeFrequencyCapFilteringEnabled(),
                        mFakeFlags.getFledgeAppInstallFilteringEnabled(),
                        mFakeFlags.getFledgeAuctionServerAdRenderIdEnabled(),
                        mFakeFlags.getFledgeAuctionServerAdRenderIdMaxLength(),
                        mFakeFlags.getComponentAdRenderIdMaxLengthBytes(),
                        mFakeFlags.getMaxComponentAdsPerCustomAudience());

        expect.that(reader.getComponentAdsFromJsonObject()).isEqualTo(List.of(componentAdData1));
    }
}

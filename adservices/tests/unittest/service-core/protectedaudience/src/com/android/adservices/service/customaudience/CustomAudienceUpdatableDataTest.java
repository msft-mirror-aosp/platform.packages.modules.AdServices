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
import static com.android.adservices.service.FlagsConstants.KEY_FLEDGE_AUCTION_SERVER_REQUEST_FLAGS_ENABLED;
import static com.android.adservices.service.FlagsConstants.KEY_FLEDGE_CUSTOM_AUDIENCE_MAX_ADS_SIZE_B;
import static com.android.adservices.service.FlagsConstants.KEY_FLEDGE_CUSTOM_AUDIENCE_MAX_TRUSTED_BIDDING_DATA_SIZE_B;
import static com.android.adservices.service.FlagsConstants.KEY_FLEDGE_CUSTOM_AUDIENCE_MAX_USER_BIDDING_SIGNALS_SIZE_B;
import static com.android.adservices.service.FlagsConstants.KEY_FLEDGE_FREQUENCY_CAP_FILTERING_ENABLED;
import static com.android.adservices.service.FlagsConstants.KEY_FLEDGE_GET_AD_SELECTION_DATA_SELLER_CONFIGURATION_ENABLED;
import static com.android.adservices.service.customaudience.CustomAudienceUpdatableDataReader.OMIT_ADS_VALUE;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertNotEquals;
import static org.junit.Assert.assertNull;
import static org.junit.Assert.assertThrows;
import static org.junit.Assert.assertTrue;

import android.adservices.common.AdSelectionSignals;
import android.adservices.common.CommonFixture;
import android.adservices.customaudience.CustomAudienceFixture;

import com.android.adservices.common.AdServicesUnitTestCase;
import com.android.adservices.common.DBAdDataFixture;
import com.android.adservices.common.JsonFixture;
import com.android.adservices.customaudience.DBTrustedBiddingDataFixture;
import com.android.adservices.data.common.DBAdData;
import com.android.adservices.data.customaudience.DBTrustedBiddingData;
import com.android.adservices.service.Flags;
import com.android.adservices.shared.testing.annotations.SetFlagDisabled;
import com.android.adservices.shared.testing.annotations.SetFlagEnabled;
import com.android.adservices.shared.testing.annotations.SetFlagFalse;
import com.android.adservices.shared.testing.annotations.SetFlagTrue;
import com.android.adservices.shared.testing.annotations.SetIntegerFlag;

import com.google.common.collect.ImmutableList;

import org.junit.Test;

import java.util.List;

// NOTE: used Flags that extended FakeFlagsFactory.TestFlags (which in turn was replaced by
// @SetDefaultFledgeFlags), but apparently it doesn't need @SetDefaultFledgeFlags
@SetFlagTrue(KEY_FLEDGE_FREQUENCY_CAP_FILTERING_ENABLED)
@SetFlagTrue(KEY_FLEDGE_APP_INSTALL_FILTERING_ENABLED)
public final class CustomAudienceUpdatableDataTest extends AdServicesUnitTestCase {
    private static final DBTrustedBiddingData VALID_DB_TRUSTED_BIDDING_DATA =
            DBTrustedBiddingDataFixture.getValidBuilderByBuyer(CommonFixture.VALID_BUYER_1).build();
    private static final List<DBAdData> VALID_DB_AD_DATA_LIST =
            DBAdDataFixture.getValidDbAdDataListByBuyer(CommonFixture.VALID_BUYER_1);

    // TODO(b/384949821): move to superclass
    private final Flags mFakeFlags = flags.getFlags();

    @Test
    public void testBuildUpdatableDataSuccess() throws Exception {
        AdSelectionSignals validUserBiddingSignalsAsJsonObjectString =
                AdSelectionSignals.fromString(
                        JsonFixture.formatAsOrgJsonJSONObjectString(
                                CustomAudienceFixture.VALID_USER_BIDDING_SIGNALS.toString()));
        boolean expectedContainsSuccessfulUpdate = true;
        CustomAudienceUpdatableData updatableDataFromBuilder =
                CustomAudienceUpdatableData.builder()
                        .setUserBiddingSignals(validUserBiddingSignalsAsJsonObjectString)
                        .setTrustedBiddingData(VALID_DB_TRUSTED_BIDDING_DATA)
                        .setAds(VALID_DB_AD_DATA_LIST)
                        .setAttemptedUpdateTime(CommonFixture.FIXED_NOW)
                        .setInitialUpdateResult(BackgroundFetchRunner.UpdateResultType.SUCCESS)
                        .setContainsSuccessfulUpdate(expectedContainsSuccessfulUpdate)
                        .build();

        assertEquals(
                validUserBiddingSignalsAsJsonObjectString,
                updatableDataFromBuilder.getUserBiddingSignals());
        assertEquals(
                VALID_DB_TRUSTED_BIDDING_DATA, updatableDataFromBuilder.getTrustedBiddingData());
        assertEquals(VALID_DB_AD_DATA_LIST, updatableDataFromBuilder.getAds());
        assertEquals(CommonFixture.FIXED_NOW, updatableDataFromBuilder.getAttemptedUpdateTime());
        assertEquals(
                BackgroundFetchRunner.UpdateResultType.SUCCESS,
                updatableDataFromBuilder.getInitialUpdateResult());
        assertEquals(
                expectedContainsSuccessfulUpdate,
                updatableDataFromBuilder.getContainsSuccessfulUpdate());

        final String jsonResponse =
                CustomAudienceUpdatableDataFixture.toJsonResponseString(
                        validUserBiddingSignalsAsJsonObjectString.toString(),
                        VALID_DB_TRUSTED_BIDDING_DATA,
                        VALID_DB_AD_DATA_LIST);
        CustomAudienceUpdatableData updatableDataFromResponseString =
                CustomAudienceUpdatableData.createFromResponseString(
                        CommonFixture.FIXED_NOW,
                        CommonFixture.VALID_BUYER_1,
                        BackgroundFetchRunner.UpdateResultType.SUCCESS,
                        jsonResponse,
                        mFakeFlags);

        assertEquals(
                "Manually built updatable data does not match built from response string \""
                        + jsonResponse
                        + '"',
                updatableDataFromBuilder,
                updatableDataFromResponseString);
    }

    @Test
    @SetFlagTrue(KEY_FLEDGE_AUCTION_SERVER_REQUEST_FLAGS_ENABLED)
    public void testBuildUpdatableDataSuccessWithAuctionServerFlagsEnabled() throws Exception {
        AdSelectionSignals validUserBiddingSignalsAsJsonObjectString =
                AdSelectionSignals.fromString(
                        JsonFixture.formatAsOrgJsonJSONObjectString(
                                CustomAudienceFixture.VALID_USER_BIDDING_SIGNALS.toString()));
        boolean expectedContainsSuccessfulUpdate = true;
        CustomAudienceUpdatableData updatableDataFromBuilder =
                CustomAudienceUpdatableData.builder()
                        .setUserBiddingSignals(validUserBiddingSignalsAsJsonObjectString)
                        .setTrustedBiddingData(VALID_DB_TRUSTED_BIDDING_DATA)
                        .setAds(VALID_DB_AD_DATA_LIST)
                        .setAttemptedUpdateTime(CommonFixture.FIXED_NOW)
                        .setInitialUpdateResult(BackgroundFetchRunner.UpdateResultType.SUCCESS)
                        .setContainsSuccessfulUpdate(expectedContainsSuccessfulUpdate)
                        .setAuctionServerRequestFlags(FLAG_AUCTION_SERVER_REQUEST_OMIT_ADS)
                        .build();

        assertEquals(
                validUserBiddingSignalsAsJsonObjectString,
                updatableDataFromBuilder.getUserBiddingSignals());
        assertEquals(
                VALID_DB_TRUSTED_BIDDING_DATA, updatableDataFromBuilder.getTrustedBiddingData());
        assertEquals(VALID_DB_AD_DATA_LIST, updatableDataFromBuilder.getAds());
        assertEquals(CommonFixture.FIXED_NOW, updatableDataFromBuilder.getAttemptedUpdateTime());
        assertEquals(
                BackgroundFetchRunner.UpdateResultType.SUCCESS,
                updatableDataFromBuilder.getInitialUpdateResult());
        assertEquals(
                expectedContainsSuccessfulUpdate,
                updatableDataFromBuilder.getContainsSuccessfulUpdate());
        assertEquals(
                FLAG_AUCTION_SERVER_REQUEST_OMIT_ADS,
                updatableDataFromBuilder.getAuctionServerRequestFlags());

        final String jsonResponse =
                CustomAudienceUpdatableDataFixture.toJsonResponseString(
                        validUserBiddingSignalsAsJsonObjectString.toString(),
                        VALID_DB_TRUSTED_BIDDING_DATA,
                        VALID_DB_AD_DATA_LIST,
                        ImmutableList.of(OMIT_ADS_VALUE));
        CustomAudienceUpdatableData updatableDataFromResponseString =
                CustomAudienceUpdatableData.createFromResponseString(
                        CommonFixture.FIXED_NOW,
                        CommonFixture.VALID_BUYER_1,
                        BackgroundFetchRunner.UpdateResultType.SUCCESS,
                        jsonResponse,
                        mFakeFlags);

        assertEquals(
                "Manually built updatable data does not match built from response string \""
                        + jsonResponse
                        + '"',
                updatableDataFromBuilder,
                updatableDataFromResponseString);
    }

    @Test
    @SetFlagTrue(KEY_FLEDGE_AUCTION_SERVER_REQUEST_FLAGS_ENABLED)
    public void testBuildUpdatableDataSuccessWithJunkWithAuctionServerFlagsEnabled()
            throws Exception {
        AdSelectionSignals validUserBiddingSignalsAsJsonObjectString =
                AdSelectionSignals.fromString(
                        JsonFixture.formatAsOrgJsonJSONObjectString(
                                CustomAudienceFixture.VALID_USER_BIDDING_SIGNALS.toString()));
        boolean expectedContainsSuccessfulUpdate = true;
        CustomAudienceUpdatableData updatableDataFromBuilder =
                CustomAudienceUpdatableData.builder()
                        .setUserBiddingSignals(validUserBiddingSignalsAsJsonObjectString)
                        .setTrustedBiddingData(VALID_DB_TRUSTED_BIDDING_DATA)
                        .setAds(VALID_DB_AD_DATA_LIST)
                        .setAttemptedUpdateTime(CommonFixture.FIXED_NOW)
                        .setInitialUpdateResult(BackgroundFetchRunner.UpdateResultType.SUCCESS)
                        .setContainsSuccessfulUpdate(expectedContainsSuccessfulUpdate)
                        .setAuctionServerRequestFlags(FLAG_AUCTION_SERVER_REQUEST_OMIT_ADS)
                        .build();

        assertEquals(
                validUserBiddingSignalsAsJsonObjectString,
                updatableDataFromBuilder.getUserBiddingSignals());
        assertEquals(
                VALID_DB_TRUSTED_BIDDING_DATA, updatableDataFromBuilder.getTrustedBiddingData());
        assertEquals(VALID_DB_AD_DATA_LIST, updatableDataFromBuilder.getAds());
        assertEquals(CommonFixture.FIXED_NOW, updatableDataFromBuilder.getAttemptedUpdateTime());
        assertEquals(
                BackgroundFetchRunner.UpdateResultType.SUCCESS,
                updatableDataFromBuilder.getInitialUpdateResult());
        assertEquals(
                expectedContainsSuccessfulUpdate,
                updatableDataFromBuilder.getContainsSuccessfulUpdate());
        assertEquals(
                FLAG_AUCTION_SERVER_REQUEST_OMIT_ADS,
                updatableDataFromBuilder.getAuctionServerRequestFlags());

        final String jsonResponse =
                CustomAudienceUpdatableDataFixture.toJsonResponseString(
                        validUserBiddingSignalsAsJsonObjectString.toString(),
                        VALID_DB_TRUSTED_BIDDING_DATA,
                        VALID_DB_AD_DATA_LIST,
                        ImmutableList.of(OMIT_ADS_VALUE),
                        true);
        CustomAudienceUpdatableData updatableDataFromResponseString =
                CustomAudienceUpdatableData.createFromResponseString(
                        CommonFixture.FIXED_NOW,
                        CommonFixture.VALID_BUYER_1,
                        BackgroundFetchRunner.UpdateResultType.SUCCESS,
                        jsonResponse,
                        mFakeFlags);

        assertEquals(
                "Manually built updatable data does not match built from response string \""
                        + jsonResponse
                        + '"',
                updatableDataFromBuilder,
                updatableDataFromResponseString);
    }

    @Test
    @SetFlagFalse(KEY_FLEDGE_AUCTION_SERVER_REQUEST_FLAGS_ENABLED)
    public void testBuildUpdatableDataSuccessWithAuctionServerFlagsDisabled() throws Exception {
        AdSelectionSignals validUserBiddingSignalsAsJsonObjectString =
                AdSelectionSignals.fromString(
                        JsonFixture.formatAsOrgJsonJSONObjectString(
                                CustomAudienceFixture.VALID_USER_BIDDING_SIGNALS.toString()));
        boolean expectedContainsSuccessfulUpdate = true;
        CustomAudienceUpdatableData updatableDataFromBuilder =
                CustomAudienceUpdatableData.builder()
                        .setUserBiddingSignals(validUserBiddingSignalsAsJsonObjectString)
                        .setTrustedBiddingData(VALID_DB_TRUSTED_BIDDING_DATA)
                        .setAds(VALID_DB_AD_DATA_LIST)
                        .setAttemptedUpdateTime(CommonFixture.FIXED_NOW)
                        .setInitialUpdateResult(BackgroundFetchRunner.UpdateResultType.SUCCESS)
                        .setContainsSuccessfulUpdate(expectedContainsSuccessfulUpdate)
                        .build();

        assertEquals(
                validUserBiddingSignalsAsJsonObjectString,
                updatableDataFromBuilder.getUserBiddingSignals());
        assertEquals(
                VALID_DB_TRUSTED_BIDDING_DATA, updatableDataFromBuilder.getTrustedBiddingData());
        assertEquals(VALID_DB_AD_DATA_LIST, updatableDataFromBuilder.getAds());
        assertEquals(CommonFixture.FIXED_NOW, updatableDataFromBuilder.getAttemptedUpdateTime());
        assertEquals(
                BackgroundFetchRunner.UpdateResultType.SUCCESS,
                updatableDataFromBuilder.getInitialUpdateResult());
        assertEquals(
                expectedContainsSuccessfulUpdate,
                updatableDataFromBuilder.getContainsSuccessfulUpdate());

        final String jsonResponse =
                CustomAudienceUpdatableDataFixture.toJsonResponseString(
                        validUserBiddingSignalsAsJsonObjectString.toString(),
                        VALID_DB_TRUSTED_BIDDING_DATA,
                        VALID_DB_AD_DATA_LIST,
                        ImmutableList.of(OMIT_ADS_VALUE));
        CustomAudienceUpdatableData updatableDataFromResponseString =
                CustomAudienceUpdatableData.createFromResponseString(
                        CommonFixture.FIXED_NOW,
                        CommonFixture.VALID_BUYER_1,
                        BackgroundFetchRunner.UpdateResultType.SUCCESS,
                        jsonResponse,
                        mFakeFlags);

        assertEquals(
                "Manually built updatable data does not match built from response string \""
                        + jsonResponse
                        + '"',
                updatableDataFromBuilder,
                updatableDataFromResponseString);
        assertEquals(0, updatableDataFromResponseString.getAuctionServerRequestFlags());
    }

    @Test
    @SetFlagEnabled(KEY_FLEDGE_GET_AD_SELECTION_DATA_SELLER_CONFIGURATION_ENABLED)
    public void testUpdatableDataSuccessWithSellerConfigurationEnabled() throws Exception {
        AdSelectionSignals validUserBiddingSignalsAsJsonObjectString =
                AdSelectionSignals.fromString(
                        JsonFixture.formatAsOrgJsonJSONObjectString(
                                CustomAudienceFixture.VALID_USER_BIDDING_SIGNALS.toString()));
        boolean expectedContainsSuccessfulUpdate = true;
        CustomAudienceUpdatableData updatableDataFromBuilder =
                CustomAudienceUpdatableData.builder()
                        .setUserBiddingSignals(validUserBiddingSignalsAsJsonObjectString)
                        .setTrustedBiddingData(VALID_DB_TRUSTED_BIDDING_DATA)
                        .setAds(VALID_DB_AD_DATA_LIST)
                        .setAttemptedUpdateTime(CommonFixture.FIXED_NOW)
                        .setInitialUpdateResult(BackgroundFetchRunner.UpdateResultType.SUCCESS)
                        .setContainsSuccessfulUpdate(expectedContainsSuccessfulUpdate)
                        .setPriority(VALID_PRIORITY_1)
                        .build();

        assertEquals(
                validUserBiddingSignalsAsJsonObjectString,
                updatableDataFromBuilder.getUserBiddingSignals());
        assertEquals(
                VALID_DB_TRUSTED_BIDDING_DATA, updatableDataFromBuilder.getTrustedBiddingData());
        assertEquals(VALID_DB_AD_DATA_LIST, updatableDataFromBuilder.getAds());
        assertEquals(CommonFixture.FIXED_NOW, updatableDataFromBuilder.getAttemptedUpdateTime());
        assertEquals(
                BackgroundFetchRunner.UpdateResultType.SUCCESS,
                updatableDataFromBuilder.getInitialUpdateResult());
        assertEquals(
                expectedContainsSuccessfulUpdate,
                updatableDataFromBuilder.getContainsSuccessfulUpdate());
        assertEquals(0, Double.compare(VALID_PRIORITY_1, updatableDataFromBuilder.getPriority()));

        final String jsonResponse =
                CustomAudienceUpdatableDataFixture.toJsonResponseString(
                        validUserBiddingSignalsAsJsonObjectString.toString(),
                        VALID_DB_TRUSTED_BIDDING_DATA,
                        VALID_DB_AD_DATA_LIST,
                        VALID_PRIORITY_1,
                        false);
        CustomAudienceUpdatableData updatableDataFromResponseString =
                CustomAudienceUpdatableData.createFromResponseString(
                        CommonFixture.FIXED_NOW,
                        CommonFixture.VALID_BUYER_1,
                        BackgroundFetchRunner.UpdateResultType.SUCCESS,
                        jsonResponse,
                        mFakeFlags);

        assertEquals(
                "Manually built updatable data does not match built from response string \""
                        + jsonResponse
                        + '"',
                updatableDataFromBuilder,
                updatableDataFromResponseString);
        assertEquals(
                0,
                Double.compare(
                        updatableDataFromResponseString.getPriority(),
                        updatableDataFromBuilder.getPriority()));
    }

    @Test
    @SetFlagDisabled(KEY_FLEDGE_GET_AD_SELECTION_DATA_SELLER_CONFIGURATION_ENABLED)
    public void testUpdatableDataSuccessWithSellerConfigurationDisabled() throws Exception {
        AdSelectionSignals validUserBiddingSignalsAsJsonObjectString =
                AdSelectionSignals.fromString(
                        JsonFixture.formatAsOrgJsonJSONObjectString(
                                CustomAudienceFixture.VALID_USER_BIDDING_SIGNALS.toString()));
        boolean expectedContainsSuccessfulUpdate = true;
        CustomAudienceUpdatableData updatableDataFromBuilder =
                CustomAudienceUpdatableData.builder()
                        .setUserBiddingSignals(validUserBiddingSignalsAsJsonObjectString)
                        .setTrustedBiddingData(VALID_DB_TRUSTED_BIDDING_DATA)
                        .setAds(VALID_DB_AD_DATA_LIST)
                        .setAttemptedUpdateTime(CommonFixture.FIXED_NOW)
                        .setInitialUpdateResult(BackgroundFetchRunner.UpdateResultType.SUCCESS)
                        .setContainsSuccessfulUpdate(expectedContainsSuccessfulUpdate)
                        .build();

        assertEquals(
                validUserBiddingSignalsAsJsonObjectString,
                updatableDataFromBuilder.getUserBiddingSignals());
        assertEquals(
                VALID_DB_TRUSTED_BIDDING_DATA, updatableDataFromBuilder.getTrustedBiddingData());
        assertEquals(VALID_DB_AD_DATA_LIST, updatableDataFromBuilder.getAds());
        assertEquals(CommonFixture.FIXED_NOW, updatableDataFromBuilder.getAttemptedUpdateTime());
        assertEquals(
                BackgroundFetchRunner.UpdateResultType.SUCCESS,
                updatableDataFromBuilder.getInitialUpdateResult());
        assertEquals(
                expectedContainsSuccessfulUpdate,
                updatableDataFromBuilder.getContainsSuccessfulUpdate());
        assertEquals(0, Double.compare(PRIORITY_DEFAULT, updatableDataFromBuilder.getPriority()));

        final String jsonResponse =
                CustomAudienceUpdatableDataFixture.toJsonResponseString(
                        validUserBiddingSignalsAsJsonObjectString.toString(),
                        VALID_DB_TRUSTED_BIDDING_DATA,
                        VALID_DB_AD_DATA_LIST,
                        VALID_PRIORITY_1,
                        false);
        CustomAudienceUpdatableData updatableDataFromResponseString =
                CustomAudienceUpdatableData.createFromResponseString(
                        CommonFixture.FIXED_NOW,
                        CommonFixture.VALID_BUYER_1,
                        BackgroundFetchRunner.UpdateResultType.SUCCESS,
                        jsonResponse,
                        mFakeFlags);

        assertEquals(
                "Manually built updatable data does not match built from response string \""
                        + jsonResponse
                        + '"',
                updatableDataFromBuilder,
                updatableDataFromResponseString);
        assertEquals(
                0, Double.compare(PRIORITY_DEFAULT, updatableDataFromResponseString.getPriority()));
    }

    @Test
    @SetFlagEnabled(KEY_FLEDGE_GET_AD_SELECTION_DATA_SELLER_CONFIGURATION_ENABLED)
    public void testUpdatableDataSuccessWithNoPriorityValueInResponseString() throws Exception {
        AdSelectionSignals validUserBiddingSignalsAsJsonObjectString =
                AdSelectionSignals.fromString(
                        JsonFixture.formatAsOrgJsonJSONObjectString(
                                CustomAudienceFixture.VALID_USER_BIDDING_SIGNALS.toString()));
        boolean expectedContainsSuccessfulUpdate = true;
        CustomAudienceUpdatableData updatableDataFromBuilder =
                CustomAudienceUpdatableData.builder()
                        .setUserBiddingSignals(validUserBiddingSignalsAsJsonObjectString)
                        .setTrustedBiddingData(VALID_DB_TRUSTED_BIDDING_DATA)
                        .setAds(VALID_DB_AD_DATA_LIST)
                        .setAttemptedUpdateTime(CommonFixture.FIXED_NOW)
                        .setInitialUpdateResult(BackgroundFetchRunner.UpdateResultType.SUCCESS)
                        .setContainsSuccessfulUpdate(expectedContainsSuccessfulUpdate)
                        .build();

        assertEquals(
                validUserBiddingSignalsAsJsonObjectString,
                updatableDataFromBuilder.getUserBiddingSignals());
        assertEquals(
                VALID_DB_TRUSTED_BIDDING_DATA, updatableDataFromBuilder.getTrustedBiddingData());
        assertEquals(VALID_DB_AD_DATA_LIST, updatableDataFromBuilder.getAds());
        assertEquals(CommonFixture.FIXED_NOW, updatableDataFromBuilder.getAttemptedUpdateTime());
        assertEquals(
                BackgroundFetchRunner.UpdateResultType.SUCCESS,
                updatableDataFromBuilder.getInitialUpdateResult());
        assertEquals(
                expectedContainsSuccessfulUpdate,
                updatableDataFromBuilder.getContainsSuccessfulUpdate());
        assertEquals(0, Double.compare(PRIORITY_DEFAULT, updatableDataFromBuilder.getPriority()));

        // JSON response with no priority field
        final String jsonResponse =
                CustomAudienceUpdatableDataFixture.toJsonResponseString(
                        validUserBiddingSignalsAsJsonObjectString.toString(),
                        VALID_DB_TRUSTED_BIDDING_DATA,
                        VALID_DB_AD_DATA_LIST);

        CustomAudienceUpdatableData updatableDataFromResponseString =
                CustomAudienceUpdatableData.createFromResponseString(
                        CommonFixture.FIXED_NOW,
                        CommonFixture.VALID_BUYER_1,
                        BackgroundFetchRunner.UpdateResultType.SUCCESS,
                        jsonResponse,
                        mFakeFlags);

        assertEquals(
                "Manually built updatable data does not match built from response string \""
                        + jsonResponse
                        + '"',
                updatableDataFromBuilder,
                updatableDataFromResponseString);
        // Since default is 0.0, response string and builder should return 0.0
        assertEquals(
                0, Double.compare(PRIORITY_DEFAULT, updatableDataFromResponseString.getPriority()));
        assertEquals(
                0,
                Double.compare(
                        updatableDataFromResponseString.getPriority(),
                        updatableDataFromBuilder.getPriority()));
    }

    @Test
    public void testBuildEmptyUpdatableDataSuccess() throws Exception {
        boolean expectedContainsSuccessfulUpdate = true;
        CustomAudienceUpdatableData updatableDataFromBuilder =
                CustomAudienceUpdatableData.builder()
                        .setUserBiddingSignals(null)
                        .setTrustedBiddingData(null)
                        .setAds(null)
                        .setAttemptedUpdateTime(CommonFixture.FIXED_NOW)
                        .setInitialUpdateResult(BackgroundFetchRunner.UpdateResultType.SUCCESS)
                        .setContainsSuccessfulUpdate(expectedContainsSuccessfulUpdate)
                        .build();

        assertNull(updatableDataFromBuilder.getUserBiddingSignals());
        assertNull(updatableDataFromBuilder.getTrustedBiddingData());
        assertNull(updatableDataFromBuilder.getAds());
        assertEquals(CommonFixture.FIXED_NOW, updatableDataFromBuilder.getAttemptedUpdateTime());
        assertEquals(
                BackgroundFetchRunner.UpdateResultType.SUCCESS,
                updatableDataFromBuilder.getInitialUpdateResult());
        assertEquals(
                expectedContainsSuccessfulUpdate,
                updatableDataFromBuilder.getContainsSuccessfulUpdate());

        final String jsonResponse = CustomAudienceUpdatableDataFixture.getEmptyJsonResponseString();
        CustomAudienceUpdatableData updatableDataFromResponseString =
                CustomAudienceUpdatableData.createFromResponseString(
                        CommonFixture.FIXED_NOW,
                        CommonFixture.VALID_BUYER_1,
                        BackgroundFetchRunner.UpdateResultType.SUCCESS,
                        jsonResponse,
                        mFakeFlags);

        assertEquals(
                "Manually built updatable data does not match built from response string \""
                        + jsonResponse
                        + '"',
                updatableDataFromBuilder,
                updatableDataFromResponseString);

        CustomAudienceUpdatableData updatableDataFromEmptyString =
                CustomAudienceUpdatableData.createFromResponseString(
                        CommonFixture.FIXED_NOW,
                        CommonFixture.VALID_BUYER_1,
                        BackgroundFetchRunner.UpdateResultType.SUCCESS,
                        "",
                        mFakeFlags);

        assertEquals(
                "Updatable data created with empty string does not match built from response"
                        + " string \""
                        + jsonResponse
                        + '"',
                updatableDataFromEmptyString,
                updatableDataFromResponseString);
    }

    @Test
    public void testBuildEmptyUpdatableDataWithNonEmptyResponseSuccess() throws Exception {
        boolean expectedContainsSuccessfulUpdate = false;
        CustomAudienceUpdatableData updatableDataFromBuilder =
                CustomAudienceUpdatableData.builder()
                        .setUserBiddingSignals(null)
                        .setTrustedBiddingData(null)
                        .setAds(null)
                        .setAttemptedUpdateTime(CommonFixture.FIXED_NOW)
                        .setInitialUpdateResult(BackgroundFetchRunner.UpdateResultType.SUCCESS)
                        .setContainsSuccessfulUpdate(expectedContainsSuccessfulUpdate)
                        .build();

        assertNull(updatableDataFromBuilder.getUserBiddingSignals());
        assertNull(updatableDataFromBuilder.getTrustedBiddingData());
        assertNull(updatableDataFromBuilder.getAds());
        assertEquals(CommonFixture.FIXED_NOW, updatableDataFromBuilder.getAttemptedUpdateTime());
        assertEquals(
                BackgroundFetchRunner.UpdateResultType.SUCCESS,
                updatableDataFromBuilder.getInitialUpdateResult());
        assertEquals(
                expectedContainsSuccessfulUpdate,
                updatableDataFromBuilder.getContainsSuccessfulUpdate());

        // In this case, a non-empty response was parsed, but the units of data found were malformed
        // and not updatable
        final String jsonResponse =
                CustomAudienceUpdatableDataFixture.getMalformedJsonResponseString();
        CustomAudienceUpdatableData updatableDataFromResponseString =
                CustomAudienceUpdatableData.createFromResponseString(
                        CommonFixture.FIXED_NOW,
                        CommonFixture.VALID_BUYER_1,
                        BackgroundFetchRunner.UpdateResultType.SUCCESS,
                        jsonResponse,
                        mFakeFlags);

        assertEquals(
                "Manually built updatable data does not match built from response string \""
                        + jsonResponse
                        + '"',
                updatableDataFromBuilder,
                updatableDataFromResponseString);
    }

    @Test
    public void testHarmlessJunkIgnoredInUpdatableDataCreateFromResponse() throws Exception {
        // In this case, a regular full response was parsed without any extra fields
        final String jsonResponseWithoutHarmlessJunk =
                CustomAudienceUpdatableDataFixture.toJsonResponseString(
                        CustomAudienceFixture.VALID_USER_BIDDING_SIGNALS.toString(),
                        null,
                        VALID_DB_AD_DATA_LIST);
        CustomAudienceUpdatableData updatableDataWithoutHarmlessJunk =
                CustomAudienceUpdatableData.createFromResponseString(
                        CommonFixture.FIXED_NOW,
                        CommonFixture.VALID_BUYER_1,
                        BackgroundFetchRunner.UpdateResultType.SUCCESS,
                        jsonResponseWithoutHarmlessJunk,
                        mFakeFlags);

        // Harmless junk was added to the same response
        final String jsonResponseWithHarmlessJunk =
                CustomAudienceUpdatableDataFixture.toJsonResponseStringWithHarmlessJunk(
                        CustomAudienceFixture.VALID_USER_BIDDING_SIGNALS.toString(),
                        null,
                        VALID_DB_AD_DATA_LIST);
        CustomAudienceUpdatableData updatableDataWithHarmlessJunk =
                CustomAudienceUpdatableData.createFromResponseString(
                        CommonFixture.FIXED_NOW,
                        CommonFixture.VALID_BUYER_1,
                        BackgroundFetchRunner.UpdateResultType.SUCCESS,
                        jsonResponseWithHarmlessJunk,
                        mFakeFlags);

        assertNotEquals(
                "Harmless junk was not added to the response JSON",
                jsonResponseWithoutHarmlessJunk,
                jsonResponseWithHarmlessJunk);
        assertEquals(
                "Updatable data created without harmless junk \""
                        + jsonResponseWithoutHarmlessJunk
                        + "\" does not match created with harmless junk \""
                        + jsonResponseWithHarmlessJunk
                        + '"',
                updatableDataWithoutHarmlessJunk,
                updatableDataWithHarmlessJunk);
    }

    @Test
    public void testBuildNonEmptyUpdatableDataWithUnsuccessfulUpdateFailure() {
        assertThrows(
                IllegalArgumentException.class,
                () ->
                        CustomAudienceUpdatableData.builder()
                                .setUserBiddingSignals(
                                        CustomAudienceFixture.VALID_USER_BIDDING_SIGNALS)
                                .setTrustedBiddingData(VALID_DB_TRUSTED_BIDDING_DATA)
                                .setAds(VALID_DB_AD_DATA_LIST)
                                .setAttemptedUpdateTime(CommonFixture.FIXED_NOW)
                                .setInitialUpdateResult(
                                        BackgroundFetchRunner.UpdateResultType.SUCCESS)
                                .setContainsSuccessfulUpdate(false)
                                .build());
    }

    @Test
    public void testUnsuccessfulInitialUpdateResultCausesUnsuccessfulUpdate() throws Exception {
        // If the initial update result is anything except for SUCCESS, the resulting updatableData
        // should not contain a successful update
        for (BackgroundFetchRunner.UpdateResultType initialUpdateResult :
                BackgroundFetchRunner.UpdateResultType.values()) {
            CustomAudienceUpdatableData updatableData =
                    CustomAudienceUpdatableData.createFromResponseString(
                            CommonFixture.FIXED_NOW,
                            CommonFixture.VALID_BUYER_1,
                            initialUpdateResult,
                            CustomAudienceUpdatableDataFixture.getEmptyJsonResponseString(),
                            mFakeFlags);
            assertEquals(
                    "Incorrect update success when initial result is "
                            + initialUpdateResult.toString(),
                    initialUpdateResult == BackgroundFetchRunner.UpdateResultType.SUCCESS,
                    updatableData.getContainsSuccessfulUpdate());
        }
    }

    @Test
    public void testCreateFromNonJsonResponseStringCausesUnsuccessfulUpdate() {
        CustomAudienceUpdatableData updatableData =
                CustomAudienceUpdatableData.createFromResponseString(
                        CommonFixture.FIXED_NOW,
                        CommonFixture.VALID_BUYER_1,
                        BackgroundFetchRunner.UpdateResultType.SUCCESS,
                        "this (input ,string .is -not real json'",
                        mFakeFlags);

        assertNull(updatableData.getUserBiddingSignals());
        assertNull(updatableData.getTrustedBiddingData());
        assertNull(updatableData.getAds());
        assertEquals(0, Double.compare(PRIORITY_DEFAULT, updatableData.getPriority()));
        assertEquals(CommonFixture.FIXED_NOW, updatableData.getAttemptedUpdateTime());
        assertEquals(
                BackgroundFetchRunner.UpdateResultType.SUCCESS,
                updatableData.getInitialUpdateResult());
        assertFalse(updatableData.getContainsSuccessfulUpdate());
    }

    @SetIntegerFlag(name = KEY_FLEDGE_CUSTOM_AUDIENCE_MAX_USER_BIDDING_SIGNALS_SIZE_B, value = 1)
    @SetFlagEnabled(KEY_FLEDGE_FREQUENCY_CAP_FILTERING_ENABLED)
    @SetFlagEnabled(KEY_FLEDGE_APP_INSTALL_FILTERING_ENABLED)
    @Test
    public void testCreateFromFullJsonResponseStringWithSmallLimitStillSuccess() throws Exception {
        String validUserBiddingSignalsAsJsonObjectString =
                JsonFixture.formatAsOrgJsonJSONObjectString(
                        CustomAudienceFixture.VALID_USER_BIDDING_SIGNALS.toString());
        String jsonResponse =
                CustomAudienceUpdatableDataFixture.toJsonResponseString(
                        validUserBiddingSignalsAsJsonObjectString,
                        VALID_DB_TRUSTED_BIDDING_DATA,
                        VALID_DB_AD_DATA_LIST);
        CustomAudienceUpdatableData updatableDataFromResponseString =
                CustomAudienceUpdatableData.createFromResponseString(
                        CommonFixture.FIXED_NOW,
                        CommonFixture.VALID_BUYER_1,
                        BackgroundFetchRunner.UpdateResultType.SUCCESS,
                        jsonResponse,
                        mFakeFlags);

        // Because *something* was updated, even a failed unit of data still creates a successful
        // update, but the failed unit is not updated
        assertTrue(updatableDataFromResponseString.getContainsSuccessfulUpdate());
        assertNull(updatableDataFromResponseString.getUserBiddingSignals());
        assertEquals(
                VALID_DB_TRUSTED_BIDDING_DATA,
                updatableDataFromResponseString.getTrustedBiddingData());
        assertEquals(VALID_DB_AD_DATA_LIST, updatableDataFromResponseString.getAds());
    }

    @Test
    @SetIntegerFlag(name = KEY_FLEDGE_CUSTOM_AUDIENCE_MAX_USER_BIDDING_SIGNALS_SIZE_B, value = 1)
    @SetIntegerFlag(name = KEY_FLEDGE_CUSTOM_AUDIENCE_MAX_TRUSTED_BIDDING_DATA_SIZE_B, value = 1)
    @SetIntegerFlag(name = KEY_FLEDGE_CUSTOM_AUDIENCE_MAX_ADS_SIZE_B, value = 1)
    public void testCreateFromResponseStringWithLargeFieldsCausesUnsuccessfulUpdate()
            throws Exception {
        String validUserBiddingSignalsAsJsonObjectString =
                JsonFixture.formatAsOrgJsonJSONObjectString(
                        CustomAudienceFixture.VALID_USER_BIDDING_SIGNALS.toString());
        final String jsonResponse =
                CustomAudienceUpdatableDataFixture.toJsonResponseString(
                        validUserBiddingSignalsAsJsonObjectString,
                        VALID_DB_TRUSTED_BIDDING_DATA,
                        VALID_DB_AD_DATA_LIST);
        CustomAudienceUpdatableData updatableDataFromResponseString =
                CustomAudienceUpdatableData.createFromResponseString(
                        CommonFixture.FIXED_NOW,
                        CommonFixture.VALID_BUYER_1,
                        BackgroundFetchRunner.UpdateResultType.SUCCESS,
                        jsonResponse,
                        mFakeFlags);

        // All found fields in the response were too large, failing validation
        assertFalse(updatableDataFromResponseString.getContainsSuccessfulUpdate());
    }
}

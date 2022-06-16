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

package android.adservices.customaudience;

import static com.google.common.truth.Truth.assertThat;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertNull;
import static org.junit.Assert.assertThrows;

import android.adservices.common.AdData;
import android.adservices.common.AdDataFixture;
import android.os.Parcel;

import androidx.test.filters.SmallTest;

import org.junit.Test;

import java.util.ArrayList;
import java.util.Collections;

/** Unit tests for {@link android.adservices.customaudience.CustomAudience} */
@SmallTest
public final class CustomAudienceTest {

    private static final CustomAudience VALID_CUSTOM_AUDIENCE = new CustomAudience.Builder()
            .setOwner(CustomAudienceFixture.VALID_OWNER)
            .setBuyer(CustomAudienceFixture.VALID_BUYER)
            .setName(CustomAudienceFixture.VALID_NAME)
            .setActivationTime(CustomAudienceFixture.VALID_ACTIVATION_TIME)
            .setExpirationTime(CustomAudienceFixture.VALID_EXPIRATION_TIME)
            .setDailyUpdateUrl(CustomAudienceFixture.VALID_DAILY_UPDATE_URL)
            .setUserBiddingSignals(CustomAudienceFixture.VALID_USER_BIDDING_SIGNALS)
            .setTrustedBiddingData(TrustedBiddingDataFixture.VALID_TRUSTED_BIDDING_DATA)
            .setBiddingLogicUrl(CustomAudienceFixture.VALID_BIDDING_LOGIC_URL)
            .setAds(AdDataFixture.VALID_ADS)
            .build();

    private static final CustomAudience VALID_CUSTOM_AUDIENCE_WITH_NULL_VALUE =
            new CustomAudience.Builder()
                    .setOwner(CustomAudienceFixture.VALID_OWNER)
                    .setBuyer(CustomAudienceFixture.VALID_BUYER)
                    .setName(CustomAudienceFixture.VALID_NAME)
                    .setActivationTime(null)
                    .setExpirationTime(CustomAudienceFixture.VALID_EXPIRATION_TIME)
                    .setDailyUpdateUrl(CustomAudienceFixture.VALID_DAILY_UPDATE_URL)
                    .setUserBiddingSignals(CustomAudienceFixture.VALID_USER_BIDDING_SIGNALS)
                    .setTrustedBiddingData(null)
                    .setBiddingLogicUrl(CustomAudienceFixture.VALID_BIDDING_LOGIC_URL)
                    .setAds(AdDataFixture.VALID_ADS)
                    .build();

    public static final CustomAudience NULL_OWNER_CUSTOM_AUDIENCE = new CustomAudience.Builder()
            .setBuyer(CustomAudienceFixture.VALID_BUYER)
            .setName(CustomAudienceFixture.VALID_NAME)
            .setActivationTime(CustomAudienceFixture.VALID_ACTIVATION_TIME)
            .setExpirationTime(CustomAudienceFixture.VALID_EXPIRATION_TIME)
            .setDailyUpdateUrl(CustomAudienceFixture.VALID_DAILY_UPDATE_URL)
            .setUserBiddingSignals(CustomAudienceFixture.VALID_USER_BIDDING_SIGNALS)
            .setTrustedBiddingData(TrustedBiddingDataFixture.VALID_TRUSTED_BIDDING_DATA)
            .setBiddingLogicUrl(CustomAudienceFixture.VALID_BIDDING_LOGIC_URL)
            .setAds(AdDataFixture.VALID_ADS)
            .build();

    @Test
    public void testBuildValidCustomAudienceSuccess() {
        assertEquals(CustomAudienceFixture.VALID_OWNER, VALID_CUSTOM_AUDIENCE.getOwner());
        assertEquals(CustomAudienceFixture.VALID_BUYER, VALID_CUSTOM_AUDIENCE.getBuyer());
        assertEquals(CustomAudienceFixture.VALID_NAME, VALID_CUSTOM_AUDIENCE.getName());
        assertEquals(CustomAudienceFixture.VALID_ACTIVATION_TIME,
                VALID_CUSTOM_AUDIENCE.getActivationTime());
        assertEquals(CustomAudienceFixture.VALID_EXPIRATION_TIME,
                VALID_CUSTOM_AUDIENCE.getExpirationTime());
        assertEquals(CustomAudienceFixture.VALID_DAILY_UPDATE_URL,
                VALID_CUSTOM_AUDIENCE.getDailyUpdateUrl());
        assertEquals(CustomAudienceFixture.VALID_USER_BIDDING_SIGNALS,
                VALID_CUSTOM_AUDIENCE.getUserBiddingSignals());
        assertEquals(TrustedBiddingDataFixture.VALID_TRUSTED_BIDDING_DATA,
                VALID_CUSTOM_AUDIENCE.getTrustedBiddingData());
        assertEquals(CustomAudienceFixture.VALID_BIDDING_LOGIC_URL,
                VALID_CUSTOM_AUDIENCE.getBiddingLogicUrl());
        assertEquals(AdDataFixture.VALID_ADS, VALID_CUSTOM_AUDIENCE.getAds());
    }

    @Test
    public void testBuildNullOwnerCustomAudienceSuccess() {
        assertNull(NULL_OWNER_CUSTOM_AUDIENCE.getOwner());
        assertEquals(CustomAudienceFixture.VALID_BUYER, VALID_CUSTOM_AUDIENCE.getBuyer());
        assertEquals(CustomAudienceFixture.VALID_NAME, VALID_CUSTOM_AUDIENCE.getName());
        assertEquals(CustomAudienceFixture.VALID_ACTIVATION_TIME,
                VALID_CUSTOM_AUDIENCE.getActivationTime());
        assertEquals(CustomAudienceFixture.VALID_EXPIRATION_TIME,
                VALID_CUSTOM_AUDIENCE.getExpirationTime());
        assertEquals(CustomAudienceFixture.VALID_DAILY_UPDATE_URL,
                VALID_CUSTOM_AUDIENCE.getDailyUpdateUrl());
        assertEquals(CustomAudienceFixture.VALID_USER_BIDDING_SIGNALS,
                VALID_CUSTOM_AUDIENCE.getUserBiddingSignals());
        assertEquals(TrustedBiddingDataFixture.VALID_TRUSTED_BIDDING_DATA,
                VALID_CUSTOM_AUDIENCE.getTrustedBiddingData());
        assertEquals(CustomAudienceFixture.VALID_BIDDING_LOGIC_URL,
                VALID_CUSTOM_AUDIENCE.getBiddingLogicUrl());
        assertEquals(AdDataFixture.VALID_ADS, VALID_CUSTOM_AUDIENCE.getAds());
    }

    @Test
    public void testBuildValidDelayedActivationCustomAudienceSuccess() {
        CustomAudience validDelayedActivationCustomAudience = new CustomAudience.Builder()
                .setOwner(CustomAudienceFixture.VALID_OWNER)
                .setBuyer(CustomAudienceFixture.VALID_BUYER)
                .setName(CustomAudienceFixture.VALID_NAME)
                .setActivationTime(CustomAudienceFixture.VALID_DELAYED_ACTIVATION_TIME)
                .setExpirationTime(CustomAudienceFixture.VALID_DELAYED_EXPIRATION_TIME)
                .setDailyUpdateUrl(CustomAudienceFixture.VALID_DAILY_UPDATE_URL)
                .setUserBiddingSignals(CustomAudienceFixture.VALID_USER_BIDDING_SIGNALS)
                .setTrustedBiddingData(TrustedBiddingDataFixture.VALID_TRUSTED_BIDDING_DATA)
                .setBiddingLogicUrl(CustomAudienceFixture.VALID_BIDDING_LOGIC_URL)
                .setAds(AdDataFixture.VALID_ADS)
                .build();

        assertThat(validDelayedActivationCustomAudience.getOwner())
                .isEqualTo(CustomAudienceFixture.VALID_OWNER);
        assertThat(validDelayedActivationCustomAudience.getBuyer())
                .isEqualTo(CustomAudienceFixture.VALID_BUYER);
        assertThat(validDelayedActivationCustomAudience.getName())
                .isEqualTo(CustomAudienceFixture.VALID_NAME);
        assertThat(validDelayedActivationCustomAudience.getActivationTime())
                .isEqualTo(CustomAudienceFixture.VALID_DELAYED_ACTIVATION_TIME);
        assertThat(validDelayedActivationCustomAudience.getExpirationTime())
                .isEqualTo(CustomAudienceFixture.VALID_DELAYED_EXPIRATION_TIME);
        assertThat(validDelayedActivationCustomAudience.getDailyUpdateUrl())
                .isEqualTo(CustomAudienceFixture.VALID_DAILY_UPDATE_URL);
        assertThat(validDelayedActivationCustomAudience.getUserBiddingSignals())
                .isEqualTo(CustomAudienceFixture.VALID_USER_BIDDING_SIGNALS);
        assertThat(validDelayedActivationCustomAudience.getTrustedBiddingData())
                .isEqualTo(TrustedBiddingDataFixture.VALID_TRUSTED_BIDDING_DATA);
        assertThat(validDelayedActivationCustomAudience.getBiddingLogicUrl())
                .isEqualTo(CustomAudienceFixture.VALID_BIDDING_LOGIC_URL);
        assertThat(validDelayedActivationCustomAudience.getAds())
                .isEqualTo(AdDataFixture.VALID_ADS);
    }

    @Test
    public void testParcelValidCustomAudienceSuccess() {

        Parcel p = Parcel.obtain();
        VALID_CUSTOM_AUDIENCE.writeToParcel(p, 0);
        p.setDataPosition(0);
        CustomAudience fromParcel = CustomAudience.CREATOR.createFromParcel(p);

        assertEquals(VALID_CUSTOM_AUDIENCE, fromParcel);
    }

    @Test
    public void testParcelValidCustomAudienceWithNullValueSuccess() {
        Parcel p = Parcel.obtain();
        VALID_CUSTOM_AUDIENCE_WITH_NULL_VALUE.writeToParcel(p, 0);
        p.setDataPosition(0);
        CustomAudience fromParcel = CustomAudience.CREATOR.createFromParcel(p);

        assertEquals(VALID_CUSTOM_AUDIENCE_WITH_NULL_VALUE, fromParcel);
    }

    @Test
    public void testNonNullValueNotSetBuildFails() {
        assertThrows(NullPointerException.class, () -> {
            // No buyer were set
            new CustomAudience.Builder()
                    .setOwner(CustomAudienceFixture.VALID_OWNER)
                    .setName(CustomAudienceFixture.VALID_NAME)
                    .setActivationTime(CustomAudienceFixture.VALID_DELAYED_ACTIVATION_TIME)
                    .setExpirationTime(CustomAudienceFixture.VALID_EXPIRATION_TIME)
                    .setDailyUpdateUrl(CustomAudienceFixture.VALID_DAILY_UPDATE_URL)
                    .setUserBiddingSignals(CustomAudienceFixture.VALID_USER_BIDDING_SIGNALS)
                    .setTrustedBiddingData(TrustedBiddingDataFixture.VALID_TRUSTED_BIDDING_DATA)
                    .setBiddingLogicUrl(CustomAudienceFixture.VALID_BIDDING_LOGIC_URL)
                    .setAds(AdDataFixture.VALID_ADS)
                    .build();
        });
    }

    @Test
    public void testSetNullToNonNullValueFails() {
        assertThrows(NullPointerException.class, () -> {
            // No buyer were set
            new CustomAudience.Builder()
                    .setOwner(CustomAudienceFixture.VALID_OWNER)
                    .setBuyer(null)
                    .build();
        });
    }

    @Test
    public void testSetInvalidBeforeNowExpirationTimeCustomAudienceFails() {
        assertThrows(IllegalArgumentException.class, () -> {
            // The expiry is in the past
            new CustomAudience.Builder()
                    .setOwner(CustomAudienceFixture.VALID_OWNER)
                    .setBuyer(CustomAudienceFixture.VALID_BUYER)
                    .setName(CustomAudienceFixture.VALID_NAME)
                    .setActivationTime(CustomAudienceFixture.VALID_ACTIVATION_TIME)
                    .setExpirationTime(CustomAudienceFixture.INVALID_BEFORE_NOW_EXPIRATION_TIME)
                    .setDailyUpdateUrl(CustomAudienceFixture.VALID_DAILY_UPDATE_URL)
                    .setUserBiddingSignals(CustomAudienceFixture.VALID_USER_BIDDING_SIGNALS)
                    .setTrustedBiddingData(TrustedBiddingDataFixture.VALID_TRUSTED_BIDDING_DATA)
                    .setBiddingLogicUrl(CustomAudienceFixture.VALID_BIDDING_LOGIC_URL)
                    .setAds(AdDataFixture.VALID_ADS)
                    .build();
        });
    }

    @Test
    public void testSetInvalidBeforeDelayedExpirationTimeCustomAudienceFails() {
        assertThrows(IllegalArgumentException.class, () -> {
            // The activation time is delayed, but the CA expires before it activates
            new CustomAudience.Builder()
                    .setOwner(CustomAudienceFixture.VALID_OWNER)
                    .setBuyer(CustomAudienceFixture.VALID_BUYER)
                    .setName(CustomAudienceFixture.VALID_NAME)
                    .setActivationTime(CustomAudienceFixture.VALID_DELAYED_ACTIVATION_TIME)
                    .setExpirationTime(CustomAudienceFixture.INVALID_BEFORE_DELAYED_EXPIRATION_TIME)
                    .setDailyUpdateUrl(CustomAudienceFixture.VALID_DAILY_UPDATE_URL)
                    .setUserBiddingSignals(CustomAudienceFixture.VALID_USER_BIDDING_SIGNALS)
                    .setTrustedBiddingData(TrustedBiddingDataFixture.VALID_TRUSTED_BIDDING_DATA)
                    .setBiddingLogicUrl(CustomAudienceFixture.VALID_BIDDING_LOGIC_URL)
                    .setAds(AdDataFixture.VALID_ADS)
                    .build();
        });
    }

    @Test
    public void testBuildNullAdsCustomAudienceSuccess() {
        // Ads are not set, so the CustomAudience gets built with empty list.
        CustomAudience nullAdsCustomAudience = new CustomAudience.Builder()
                .setOwner(CustomAudienceFixture.VALID_OWNER)
                .setBuyer(CustomAudienceFixture.VALID_BUYER)
                .setName(CustomAudienceFixture.VALID_NAME)
                .setActivationTime(CustomAudienceFixture.VALID_ACTIVATION_TIME)
                .setExpirationTime(CustomAudienceFixture.VALID_EXPIRATION_TIME)
                .setDailyUpdateUrl(CustomAudienceFixture.VALID_DAILY_UPDATE_URL)
                .setUserBiddingSignals(CustomAudienceFixture.VALID_USER_BIDDING_SIGNALS)
                .setTrustedBiddingData(TrustedBiddingDataFixture.VALID_TRUSTED_BIDDING_DATA)
                .setBiddingLogicUrl(CustomAudienceFixture.VALID_BIDDING_LOGIC_URL)
                .build();

        assertThat(nullAdsCustomAudience.getOwner()).isEqualTo(CustomAudienceFixture.VALID_OWNER);
        assertThat(nullAdsCustomAudience.getBuyer()).isEqualTo(CustomAudienceFixture.VALID_BUYER);
        assertThat(nullAdsCustomAudience.getName()).isEqualTo(CustomAudienceFixture.VALID_NAME);
        assertThat(nullAdsCustomAudience.getActivationTime())
                .isEqualTo(CustomAudienceFixture.VALID_ACTIVATION_TIME);
        assertThat(nullAdsCustomAudience.getExpirationTime())
                .isEqualTo(CustomAudienceFixture.VALID_EXPIRATION_TIME);
        assertThat(nullAdsCustomAudience.getDailyUpdateUrl())
                .isEqualTo(CustomAudienceFixture.VALID_DAILY_UPDATE_URL);
        assertThat(nullAdsCustomAudience.getUserBiddingSignals())
                .isEqualTo(CustomAudienceFixture.VALID_USER_BIDDING_SIGNALS);
        assertThat(nullAdsCustomAudience.getTrustedBiddingData())
                .isEqualTo(TrustedBiddingDataFixture.VALID_TRUSTED_BIDDING_DATA);
        assertThat(nullAdsCustomAudience.getBiddingLogicUrl())
                .isEqualTo(CustomAudienceFixture.VALID_BIDDING_LOGIC_URL);
        assertThat(nullAdsCustomAudience.getAds()).isEqualTo(Collections.emptyList());
    }

    @Test
    public void testBuildEmptyAdsCustomAudienceSuccess() {
        // An empty list is allowed and should not throw any exceptions
        ArrayList<AdData> emptyAds = new ArrayList<>(Collections.emptyList());

        CustomAudience emptyAdsCustomAudience = new CustomAudience.Builder()
                .setOwner(CustomAudienceFixture.VALID_OWNER)
                .setBuyer(CustomAudienceFixture.VALID_BUYER)
                .setName(CustomAudienceFixture.VALID_NAME)
                .setActivationTime(CustomAudienceFixture.VALID_ACTIVATION_TIME)
                .setExpirationTime(CustomAudienceFixture.VALID_EXPIRATION_TIME)
                .setDailyUpdateUrl(CustomAudienceFixture.VALID_DAILY_UPDATE_URL)
                .setUserBiddingSignals(CustomAudienceFixture.VALID_USER_BIDDING_SIGNALS)
                .setTrustedBiddingData(TrustedBiddingDataFixture.VALID_TRUSTED_BIDDING_DATA)
                .setBiddingLogicUrl(CustomAudienceFixture.VALID_BIDDING_LOGIC_URL)
                .setAds(emptyAds)
                .build();

        assertThat(emptyAdsCustomAudience.getOwner()).isEqualTo(CustomAudienceFixture.VALID_OWNER);
        assertThat(emptyAdsCustomAudience.getBuyer()).isEqualTo(CustomAudienceFixture.VALID_BUYER);
        assertThat(emptyAdsCustomAudience.getName()).isEqualTo(CustomAudienceFixture.VALID_NAME);
        assertThat(emptyAdsCustomAudience.getActivationTime())
                .isEqualTo(CustomAudienceFixture.VALID_ACTIVATION_TIME);
        assertThat(emptyAdsCustomAudience.getExpirationTime())
                .isEqualTo(CustomAudienceFixture.VALID_EXPIRATION_TIME);
        assertThat(emptyAdsCustomAudience.getDailyUpdateUrl())
                .isEqualTo(CustomAudienceFixture.VALID_DAILY_UPDATE_URL);
        assertThat(emptyAdsCustomAudience.getUserBiddingSignals())
                .isEqualTo(CustomAudienceFixture.VALID_USER_BIDDING_SIGNALS);
        assertThat(emptyAdsCustomAudience.getTrustedBiddingData())
                .isEqualTo(TrustedBiddingDataFixture.VALID_TRUSTED_BIDDING_DATA);
        assertThat(emptyAdsCustomAudience.getBiddingLogicUrl())
                .isEqualTo(CustomAudienceFixture.VALID_BIDDING_LOGIC_URL);
        assertThat(emptyAdsCustomAudience.getAds()).isEqualTo(emptyAds);
    }
}

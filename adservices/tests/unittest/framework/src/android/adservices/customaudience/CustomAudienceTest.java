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

import static org.junit.Assert.assertThrows;

import android.adservices.common.AdData;
import android.os.Parcel;

import androidx.test.filters.SmallTest;

import org.junit.Test;

import java.util.ArrayList;
import java.util.Collections;

/** Unit tests for {@link android.adservices.customaudience.CustomAudience} */
@SmallTest
public final class CustomAudienceTest {
    @Test
    public void testBuildValidCustomAudienceSuccess() {
        CustomAudience validCustomAudience = new CustomAudience.Builder()
                .setOwner(CustomAudienceUtils.VALID_OWNER)
                .setBuyer(CustomAudienceUtils.VALID_BUYER)
                .setName(CustomAudienceUtils.VALID_NAME)
                .setActivationTime(CustomAudienceUtils.VALID_ACTIVATION_TIME)
                .setExpirationTime(CustomAudienceUtils.VALID_EXPIRATION_TIME)
                .setDailyUpdateUrl(CustomAudienceUtils.VALID_DAILY_UPDATE_URL)
                .setUserBiddingSignals(CustomAudienceUtils.VALID_USER_BIDDING_SIGNALS)
                .setTrustedBiddingData(CustomAudienceUtils.VALID_TRUSTED_BIDDING_DATA)
                .setBiddingLogicUrl(CustomAudienceUtils.VALID_BIDDING_LOGIC_URL)
                .setAds(CustomAudienceUtils.VALID_ADS)
                .build();

        assertThat(validCustomAudience.getOwner()).isEqualTo(CustomAudienceUtils.VALID_OWNER);
        assertThat(validCustomAudience.getBuyer()).isEqualTo(CustomAudienceUtils.VALID_BUYER);
        assertThat(validCustomAudience.getName()).isEqualTo(CustomAudienceUtils.VALID_NAME);
        assertThat(validCustomAudience.getActivationTime())
                .isEqualTo(CustomAudienceUtils.VALID_ACTIVATION_TIME);
        assertThat(validCustomAudience.getExpirationTime())
                .isEqualTo(CustomAudienceUtils.VALID_EXPIRATION_TIME);
        assertThat(validCustomAudience.getDailyUpdateUrl())
                .isEqualTo(CustomAudienceUtils.VALID_DAILY_UPDATE_URL);
        assertThat(validCustomAudience.getUserBiddingSignals())
                .isEqualTo(CustomAudienceUtils.VALID_USER_BIDDING_SIGNALS);
        assertThat(validCustomAudience.getTrustedBiddingData())
                .isEqualTo(CustomAudienceUtils.VALID_TRUSTED_BIDDING_DATA);
        assertThat(validCustomAudience.getBiddingLogicUrl())
                .isEqualTo(CustomAudienceUtils.VALID_BIDDING_LOGIC_URL);
        assertThat(validCustomAudience.getAds()).isEqualTo(CustomAudienceUtils.VALID_ADS);
    }

    @Test
    public void testBuildNullOwnerCustomAudienceSuccess() {
        // If owner is not provided, it is auto-populated
        CustomAudience emptyAdsCustomAudience = new CustomAudience.Builder()
                .setBuyer(CustomAudienceUtils.VALID_BUYER)
                .setName(CustomAudienceUtils.VALID_NAME)
                .setActivationTime(CustomAudienceUtils.VALID_ACTIVATION_TIME)
                .setExpirationTime(CustomAudienceUtils.VALID_EXPIRATION_TIME)
                .setDailyUpdateUrl(CustomAudienceUtils.VALID_DAILY_UPDATE_URL)
                .setUserBiddingSignals(CustomAudienceUtils.VALID_USER_BIDDING_SIGNALS)
                .setTrustedBiddingData(CustomAudienceUtils.VALID_TRUSTED_BIDDING_DATA)
                .setBiddingLogicUrl(CustomAudienceUtils.VALID_BIDDING_LOGIC_URL)
                .setAds(CustomAudienceUtils.VALID_ADS)
                .build();

        // TODO check owner auto-population
        //assertThat(emptyAdsCustomAudience.getOwner()).isEqualTo(CustomAudienceUtils.VALID_OWNER);
        assertThat(emptyAdsCustomAudience.getBuyer()).isEqualTo(CustomAudienceUtils.VALID_BUYER);
        assertThat(emptyAdsCustomAudience.getName()).isEqualTo(CustomAudienceUtils.VALID_NAME);
        assertThat(emptyAdsCustomAudience.getActivationTime())
                .isEqualTo(CustomAudienceUtils.VALID_ACTIVATION_TIME);
        assertThat(emptyAdsCustomAudience.getExpirationTime())
                .isEqualTo(CustomAudienceUtils.VALID_EXPIRATION_TIME);
        assertThat(emptyAdsCustomAudience.getDailyUpdateUrl())
                .isEqualTo(CustomAudienceUtils.VALID_DAILY_UPDATE_URL);
        assertThat(emptyAdsCustomAudience.getUserBiddingSignals())
                .isEqualTo(CustomAudienceUtils.VALID_USER_BIDDING_SIGNALS);
        assertThat(emptyAdsCustomAudience.getTrustedBiddingData())
                .isEqualTo(CustomAudienceUtils.VALID_TRUSTED_BIDDING_DATA);
        assertThat(emptyAdsCustomAudience.getBiddingLogicUrl())
                .isEqualTo(CustomAudienceUtils.VALID_BIDDING_LOGIC_URL);
        assertThat(emptyAdsCustomAudience.getAds()).isEqualTo(CustomAudienceUtils.VALID_ADS);
    }

    @Test
    public void testBuildValidDelayedActivationCustomAudienceSuccess() {
        CustomAudience validDelayedActivationCustomAudience = new CustomAudience.Builder()
                .setOwner(CustomAudienceUtils.VALID_OWNER)
                .setBuyer(CustomAudienceUtils.VALID_BUYER)
                .setName(CustomAudienceUtils.VALID_NAME)
                .setActivationTime(CustomAudienceUtils.VALID_DELAYED_ACTIVATION_TIME)
                .setExpirationTime(CustomAudienceUtils.VALID_DELAYED_EXPIRATION_TIME)
                .setDailyUpdateUrl(CustomAudienceUtils.VALID_DAILY_UPDATE_URL)
                .setUserBiddingSignals(CustomAudienceUtils.VALID_USER_BIDDING_SIGNALS)
                .setTrustedBiddingData(CustomAudienceUtils.VALID_TRUSTED_BIDDING_DATA)
                .setBiddingLogicUrl(CustomAudienceUtils.VALID_BIDDING_LOGIC_URL)
                .setAds(CustomAudienceUtils.VALID_ADS)
                .build();

        assertThat(validDelayedActivationCustomAudience.getOwner())
                .isEqualTo(CustomAudienceUtils.VALID_OWNER);
        assertThat(validDelayedActivationCustomAudience.getBuyer())
                .isEqualTo(CustomAudienceUtils.VALID_BUYER);
        assertThat(validDelayedActivationCustomAudience.getName())
                .isEqualTo(CustomAudienceUtils.VALID_NAME);
        assertThat(validDelayedActivationCustomAudience.getActivationTime())
                .isEqualTo(CustomAudienceUtils.VALID_DELAYED_ACTIVATION_TIME);
        assertThat(validDelayedActivationCustomAudience.getExpirationTime())
                .isEqualTo(CustomAudienceUtils.VALID_DELAYED_EXPIRATION_TIME);
        assertThat(validDelayedActivationCustomAudience.getDailyUpdateUrl())
                .isEqualTo(CustomAudienceUtils.VALID_DAILY_UPDATE_URL);
        assertThat(validDelayedActivationCustomAudience.getUserBiddingSignals())
                .isEqualTo(CustomAudienceUtils.VALID_USER_BIDDING_SIGNALS);
        assertThat(validDelayedActivationCustomAudience.getTrustedBiddingData())
                .isEqualTo(CustomAudienceUtils.VALID_TRUSTED_BIDDING_DATA);
        assertThat(validDelayedActivationCustomAudience.getBiddingLogicUrl())
                .isEqualTo(CustomAudienceUtils.VALID_BIDDING_LOGIC_URL);
        assertThat(validDelayedActivationCustomAudience.getAds())
                .isEqualTo(CustomAudienceUtils.VALID_ADS);
    }

    @Test
    public void testParcelValidCustomAudienceSuccess() {
        CustomAudience validCustomAudience = new CustomAudience.Builder()
                .setOwner(CustomAudienceUtils.VALID_OWNER)
                .setBuyer(CustomAudienceUtils.VALID_BUYER)
                .setName(CustomAudienceUtils.VALID_NAME)
                .setActivationTime(CustomAudienceUtils.VALID_ACTIVATION_TIME)
                .setExpirationTime(CustomAudienceUtils.VALID_EXPIRATION_TIME)
                .setDailyUpdateUrl(CustomAudienceUtils.VALID_DAILY_UPDATE_URL)
                .setUserBiddingSignals(CustomAudienceUtils.VALID_USER_BIDDING_SIGNALS)
                .setTrustedBiddingData(CustomAudienceUtils.VALID_TRUSTED_BIDDING_DATA)
                .setBiddingLogicUrl(CustomAudienceUtils.VALID_BIDDING_LOGIC_URL)
                .setAds(CustomAudienceUtils.VALID_ADS)
                .build();

        Parcel p = Parcel.obtain();
        validCustomAudience.writeToParcel(p, 0);
        p.setDataPosition(0);
        CustomAudience fromParcel = CustomAudience.CREATOR.createFromParcel(p);

        assertThat(fromParcel.getOwner()).isEqualTo(CustomAudienceUtils.VALID_OWNER);
        assertThat(fromParcel.getBuyer()).isEqualTo(CustomAudienceUtils.VALID_BUYER);
        assertThat(fromParcel.getName()).isEqualTo(CustomAudienceUtils.VALID_NAME);
        assertThat(fromParcel.getActivationTime())
                .isEqualTo(CustomAudienceUtils.VALID_ACTIVATION_TIME);
        assertThat(fromParcel.getExpirationTime())
                .isEqualTo(CustomAudienceUtils.VALID_EXPIRATION_TIME);
        assertThat(fromParcel.getDailyUpdateUrl())
                .isEqualTo(CustomAudienceUtils.VALID_DAILY_UPDATE_URL);
        assertThat(fromParcel.getUserBiddingSignals())
                .isEqualTo(CustomAudienceUtils.VALID_USER_BIDDING_SIGNALS);
        assertThat(fromParcel.getTrustedBiddingData())
                .isEqualTo(CustomAudienceUtils.VALID_TRUSTED_BIDDING_DATA);
        assertThat(fromParcel.getBiddingLogicUrl())
                .isEqualTo(CustomAudienceUtils.VALID_BIDDING_LOGIC_URL);
        assertThat(fromParcel.getAds()).isEqualTo(CustomAudienceUtils.VALID_ADS);
    }

    @Test
    public void testSetInvalidActivationTimeCustomAudienceFails() {
        assertThrows(IllegalArgumentException.class, () -> {
            // Activation time is delayed beyond the max allowed
            new CustomAudience.Builder()
                    .setOwner(CustomAudienceUtils.VALID_OWNER)
                    .setBuyer(CustomAudienceUtils.VALID_BUYER)
                    .setName(CustomAudienceUtils.VALID_NAME)
                    .setActivationTime(CustomAudienceUtils.INVALID_DELAYED_ACTIVATION_TIME)
                    .setExpirationTime(CustomAudienceUtils.VALID_EXPIRATION_TIME)
                    .setDailyUpdateUrl(CustomAudienceUtils.VALID_DAILY_UPDATE_URL)
                    .setUserBiddingSignals(CustomAudienceUtils.VALID_USER_BIDDING_SIGNALS)
                    .setTrustedBiddingData(CustomAudienceUtils.VALID_TRUSTED_BIDDING_DATA)
                    .setBiddingLogicUrl(CustomAudienceUtils.VALID_BIDDING_LOGIC_URL)
                    .setAds(CustomAudienceUtils.VALID_ADS)
                    .build();
        });
    }

    @Test
    public void testSetInvalidBeforeNowExpirationTimeCustomAudienceFails() {
        assertThrows(IllegalArgumentException.class, () -> {
            // The expiry is in the past
            new CustomAudience.Builder()
                    .setOwner(CustomAudienceUtils.VALID_OWNER)
                    .setBuyer(CustomAudienceUtils.VALID_BUYER)
                    .setName(CustomAudienceUtils.VALID_NAME)
                    .setActivationTime(CustomAudienceUtils.VALID_ACTIVATION_TIME)
                    .setExpirationTime(CustomAudienceUtils.INVALID_BEFORE_NOW_EXPIRATION_TIME)
                    .setDailyUpdateUrl(CustomAudienceUtils.VALID_DAILY_UPDATE_URL)
                    .setUserBiddingSignals(CustomAudienceUtils.VALID_USER_BIDDING_SIGNALS)
                    .setTrustedBiddingData(CustomAudienceUtils.VALID_TRUSTED_BIDDING_DATA)
                    .setBiddingLogicUrl(CustomAudienceUtils.VALID_BIDDING_LOGIC_URL)
                    .setAds(CustomAudienceUtils.VALID_ADS)
                    .build();
        });
    }

    @Test
    public void testSetInvalidBeyondMaxExpirationTimeCustomAudienceFails() {
        assertThrows(IllegalArgumentException.class, () -> {
            // The expiry is beyond max allowed
            new CustomAudience.Builder()
                    .setOwner(CustomAudienceUtils.VALID_OWNER)
                    .setBuyer(CustomAudienceUtils.VALID_BUYER)
                    .setName(CustomAudienceUtils.VALID_NAME)
                    .setActivationTime(CustomAudienceUtils.VALID_ACTIVATION_TIME)
                    .setExpirationTime(CustomAudienceUtils.INVALID_BEYOND_MAX_EXPIRATION_TIME)
                    .setDailyUpdateUrl(CustomAudienceUtils.VALID_DAILY_UPDATE_URL)
                    .setUserBiddingSignals(CustomAudienceUtils.VALID_USER_BIDDING_SIGNALS)
                    .setTrustedBiddingData(CustomAudienceUtils.VALID_TRUSTED_BIDDING_DATA)
                    .setBiddingLogicUrl(CustomAudienceUtils.VALID_BIDDING_LOGIC_URL)
                    .setAds(CustomAudienceUtils.VALID_ADS)
                    .build();
        });
    }

    @Test
    public void testSetInvalidBeforeDelayedExpirationTimeCustomAudienceFails() {
        assertThrows(IllegalArgumentException.class, () -> {
            // The activation time is delayed, but the CA expires before it activates
            new CustomAudience.Builder()
                    .setOwner(CustomAudienceUtils.VALID_OWNER)
                    .setBuyer(CustomAudienceUtils.VALID_BUYER)
                    .setName(CustomAudienceUtils.VALID_NAME)
                    .setActivationTime(CustomAudienceUtils.VALID_DELAYED_ACTIVATION_TIME)
                    .setExpirationTime(CustomAudienceUtils.INVALID_BEFORE_DELAYED_EXPIRATION_TIME)
                    .setDailyUpdateUrl(CustomAudienceUtils.VALID_DAILY_UPDATE_URL)
                    .setUserBiddingSignals(CustomAudienceUtils.VALID_USER_BIDDING_SIGNALS)
                    .setTrustedBiddingData(CustomAudienceUtils.VALID_TRUSTED_BIDDING_DATA)
                    .setBiddingLogicUrl(CustomAudienceUtils.VALID_BIDDING_LOGIC_URL)
                    .setAds(CustomAudienceUtils.VALID_ADS)
                    .build();
        });
    }

    @Test
    public void testBuildNullAdsCustomAudienceFails() {
        assertThrows(NullPointerException.class, () -> {
            // Ads are not set, so the CustomAudience gets built with null
            new CustomAudience.Builder()
                    .setOwner(CustomAudienceUtils.VALID_OWNER)
                    .setBuyer(CustomAudienceUtils.VALID_BUYER)
                    .setName(CustomAudienceUtils.VALID_NAME)
                    .setActivationTime(CustomAudienceUtils.VALID_ACTIVATION_TIME)
                    .setExpirationTime(CustomAudienceUtils.VALID_EXPIRATION_TIME)
                    .setDailyUpdateUrl(CustomAudienceUtils.VALID_DAILY_UPDATE_URL)
                    .setUserBiddingSignals(CustomAudienceUtils.VALID_USER_BIDDING_SIGNALS)
                    .setTrustedBiddingData(CustomAudienceUtils.VALID_TRUSTED_BIDDING_DATA)
                    .setBiddingLogicUrl(CustomAudienceUtils.VALID_BIDDING_LOGIC_URL)
                    .build();
        });
    }

    @Test
    public void testBuildEmptyAdsCustomAudienceSuccess() {
        // An empty list is allowed and should not throw any exceptions
        ArrayList<AdData> emptyAds = new ArrayList<>(Collections.emptyList());

        CustomAudience emptyAdsCustomAudience = new CustomAudience.Builder()
                .setOwner(CustomAudienceUtils.VALID_OWNER)
                .setBuyer(CustomAudienceUtils.VALID_BUYER)
                .setName(CustomAudienceUtils.VALID_NAME)
                .setActivationTime(CustomAudienceUtils.VALID_ACTIVATION_TIME)
                .setExpirationTime(CustomAudienceUtils.VALID_EXPIRATION_TIME)
                .setDailyUpdateUrl(CustomAudienceUtils.VALID_DAILY_UPDATE_URL)
                .setUserBiddingSignals(CustomAudienceUtils.VALID_USER_BIDDING_SIGNALS)
                .setTrustedBiddingData(CustomAudienceUtils.VALID_TRUSTED_BIDDING_DATA)
                .setBiddingLogicUrl(CustomAudienceUtils.VALID_BIDDING_LOGIC_URL)
                .setAds(emptyAds)
                .build();

        assertThat(emptyAdsCustomAudience.getOwner()).isEqualTo(CustomAudienceUtils.VALID_OWNER);
        assertThat(emptyAdsCustomAudience.getBuyer()).isEqualTo(CustomAudienceUtils.VALID_BUYER);
        assertThat(emptyAdsCustomAudience.getName()).isEqualTo(CustomAudienceUtils.VALID_NAME);
        assertThat(emptyAdsCustomAudience.getActivationTime())
                .isEqualTo(CustomAudienceUtils.VALID_ACTIVATION_TIME);
        assertThat(emptyAdsCustomAudience.getExpirationTime())
                .isEqualTo(CustomAudienceUtils.VALID_EXPIRATION_TIME);
        assertThat(emptyAdsCustomAudience.getDailyUpdateUrl())
                .isEqualTo(CustomAudienceUtils.VALID_DAILY_UPDATE_URL);
        assertThat(emptyAdsCustomAudience.getUserBiddingSignals())
                .isEqualTo(CustomAudienceUtils.VALID_USER_BIDDING_SIGNALS);
        assertThat(emptyAdsCustomAudience.getTrustedBiddingData())
                .isEqualTo(CustomAudienceUtils.VALID_TRUSTED_BIDDING_DATA);
        assertThat(emptyAdsCustomAudience.getBiddingLogicUrl())
                .isEqualTo(CustomAudienceUtils.VALID_BIDDING_LOGIC_URL);
        assertThat(emptyAdsCustomAudience.getAds()).isEqualTo(emptyAds);
    }
}

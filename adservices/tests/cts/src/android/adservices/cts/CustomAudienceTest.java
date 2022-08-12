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

package android.adservices.cts;

import static com.google.common.truth.Truth.assertThat;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertThrows;

import android.adservices.common.AdData;
import android.adservices.common.AdDataFixture;
import android.adservices.common.CommonFixture;
import android.adservices.customaudience.CustomAudience;
import android.adservices.customaudience.CustomAudienceFixture;
import android.adservices.customaudience.TrustedBiddingDataFixture;
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
        CustomAudience validCustomAudience =
                CustomAudienceFixture.getValidBuilderForBuyer(CommonFixture.VALID_BUYER).build();

        assertEquals(CustomAudienceFixture.VALID_OWNER, validCustomAudience.getOwnerPackageName());
        assertEquals(CommonFixture.VALID_BUYER, validCustomAudience.getBuyer());
        assertEquals(CustomAudienceFixture.VALID_NAME, validCustomAudience.getName());
        assertEquals(
                CustomAudienceFixture.VALID_ACTIVATION_TIME,
                validCustomAudience.getActivationTime());
        assertEquals(
                CustomAudienceFixture.VALID_EXPIRATION_TIME,
                validCustomAudience.getExpirationTime());
        assertEquals(
                CustomAudienceFixture.getValidDailyUpdateUriByBuyer(CommonFixture.VALID_BUYER),
                validCustomAudience.getDailyUpdateUrl());
        assertEquals(
                CustomAudienceFixture.VALID_USER_BIDDING_SIGNALS,
                validCustomAudience.getUserBiddingSignals());
        assertEquals(
                TrustedBiddingDataFixture.getValidTrustedBiddingDataByBuyer(
                        CommonFixture.VALID_BUYER),
                validCustomAudience.getTrustedBiddingData());
        assertEquals(
                CustomAudienceFixture.getValidBiddingLogicUrlByBuyer(CommonFixture.VALID_BUYER),
                validCustomAudience.getBiddingLogicUrl());
        assertEquals(
                AdDataFixture.getValidAdsByBuyer(CommonFixture.VALID_BUYER),
                validCustomAudience.getAds());
    }

    @Test
    public void testBuildValidDelayedActivationCustomAudienceSuccess() {
        CustomAudience validDelayedActivationCustomAudience =
                CustomAudienceFixture.getValidBuilderForBuyer(CommonFixture.VALID_BUYER)
                        .setActivationTime(CustomAudienceFixture.VALID_DELAYED_ACTIVATION_TIME)
                        .setExpirationTime(CustomAudienceFixture.VALID_DELAYED_EXPIRATION_TIME)
                        .build();

        assertThat(validDelayedActivationCustomAudience.getOwnerPackageName())
                .isEqualTo(CustomAudienceFixture.VALID_OWNER);
        assertThat(validDelayedActivationCustomAudience.getBuyer())
                .isEqualTo(CommonFixture.VALID_BUYER);
        assertThat(validDelayedActivationCustomAudience.getName())
                .isEqualTo(CustomAudienceFixture.VALID_NAME);
        assertThat(validDelayedActivationCustomAudience.getActivationTime())
                .isEqualTo(CustomAudienceFixture.VALID_DELAYED_ACTIVATION_TIME);
        assertThat(validDelayedActivationCustomAudience.getExpirationTime())
                .isEqualTo(CustomAudienceFixture.VALID_DELAYED_EXPIRATION_TIME);
        assertThat(validDelayedActivationCustomAudience.getDailyUpdateUrl())
                .isEqualTo(
                        CustomAudienceFixture.getValidDailyUpdateUriByBuyer(
                                CommonFixture.VALID_BUYER));
        assertThat(validDelayedActivationCustomAudience.getUserBiddingSignals())
                .isEqualTo(CustomAudienceFixture.VALID_USER_BIDDING_SIGNALS);
        assertThat(validDelayedActivationCustomAudience.getTrustedBiddingData())
                .isEqualTo(
                        TrustedBiddingDataFixture.getValidTrustedBiddingDataByBuyer(
                                CommonFixture.VALID_BUYER));
        assertThat(validDelayedActivationCustomAudience.getBiddingLogicUrl())
                .isEqualTo(
                        CustomAudienceFixture.getValidBiddingLogicUrlByBuyer(
                                CommonFixture.VALID_BUYER));
        assertThat(validDelayedActivationCustomAudience.getAds())
                .isEqualTo(AdDataFixture.getValidAdsByBuyer(CommonFixture.VALID_BUYER));
    }

    @Test
    public void testParcelValidCustomAudienceSuccess() {
        CustomAudience validCustomAudience =
                CustomAudienceFixture.getValidBuilderForBuyer(CommonFixture.VALID_BUYER).build();

        Parcel p = Parcel.obtain();
        validCustomAudience.writeToParcel(p, 0);
        p.setDataPosition(0);
        CustomAudience fromParcel = CustomAudience.CREATOR.createFromParcel(p);

        assertEquals(validCustomAudience, fromParcel);
    }

    @Test
    public void testParcelValidCustomAudienceWithNullValueSuccess() {
        CustomAudience validCustomAudienceWithNullValue =
                CustomAudienceFixture.getValidBuilderForBuyer(CommonFixture.VALID_BUYER)
                        .setActivationTime(null)
                        .setTrustedBiddingData(null)
                        .build();

        Parcel p = Parcel.obtain();
        validCustomAudienceWithNullValue.writeToParcel(p, 0);
        p.setDataPosition(0);
        CustomAudience fromParcel = CustomAudience.CREATOR.createFromParcel(p);

        assertEquals(validCustomAudienceWithNullValue, fromParcel);
    }

    @Test
    public void testNonNullValueNotSetBuildFails() {
        assertThrows(
                NullPointerException.class,
                () -> {
                    // No buyer were set
                    new CustomAudience.Builder()
                            .setOwnerPackageName(CustomAudienceFixture.VALID_OWNER)
                            .setName(CustomAudienceFixture.VALID_NAME)
                            .setActivationTime(CustomAudienceFixture.VALID_DELAYED_ACTIVATION_TIME)
                            .setExpirationTime(CustomAudienceFixture.VALID_EXPIRATION_TIME)
                            .setDailyUpdateUrl(
                                    CustomAudienceFixture.getValidDailyUpdateUriByBuyer(
                                            CommonFixture.VALID_BUYER))
                            .setUserBiddingSignals(CustomAudienceFixture.VALID_USER_BIDDING_SIGNALS)
                            .setTrustedBiddingData(
                                    TrustedBiddingDataFixture.getValidTrustedBiddingDataByBuyer(
                                            CommonFixture.VALID_BUYER))
                            .setBiddingLogicUrl(
                                    CustomAudienceFixture.getValidBiddingLogicUrlByBuyer(
                                            CommonFixture.VALID_BUYER))
                            .setAds(AdDataFixture.getValidAdsByBuyer(CommonFixture.VALID_BUYER))
                            .build();
                });
    }

    @Test
    public void testSetNullToNonNullValueFails() {
        assertThrows(
                NullPointerException.class,
                () -> {
                    // No buyer were set
                    new CustomAudience.Builder()
                            .setOwnerPackageName(CustomAudienceFixture.VALID_OWNER)
                            .setBuyer(null)
                            .build();
                });
    }

    @Test
    public void testBuildNullAdsCustomAudienceSuccess() {
        // Ads are not set, so the CustomAudience gets built with empty list.
        CustomAudience nullAdsCustomAudience =
                CustomAudienceFixture.getValidBuilderForBuyer(CommonFixture.VALID_BUYER)
                        .setAds(null)
                        .build();

        assertThat(nullAdsCustomAudience.getOwnerPackageName())
                .isEqualTo(CustomAudienceFixture.VALID_OWNER);
        assertThat(nullAdsCustomAudience.getBuyer()).isEqualTo(CommonFixture.VALID_BUYER);
        assertThat(nullAdsCustomAudience.getName()).isEqualTo(CustomAudienceFixture.VALID_NAME);
        assertThat(nullAdsCustomAudience.getActivationTime())
                .isEqualTo(CustomAudienceFixture.VALID_ACTIVATION_TIME);
        assertThat(nullAdsCustomAudience.getExpirationTime())
                .isEqualTo(CustomAudienceFixture.VALID_EXPIRATION_TIME);
        assertThat(nullAdsCustomAudience.getDailyUpdateUrl())
                .isEqualTo(
                        CustomAudienceFixture.getValidDailyUpdateUriByBuyer(
                                CommonFixture.VALID_BUYER));
        assertThat(nullAdsCustomAudience.getUserBiddingSignals())
                .isEqualTo(CustomAudienceFixture.VALID_USER_BIDDING_SIGNALS);
        assertThat(nullAdsCustomAudience.getTrustedBiddingData())
                .isEqualTo(
                        TrustedBiddingDataFixture.getValidTrustedBiddingDataByBuyer(
                                CommonFixture.VALID_BUYER));
        assertThat(nullAdsCustomAudience.getBiddingLogicUrl())
                .isEqualTo(
                        CustomAudienceFixture.getValidBiddingLogicUrlByBuyer(
                                CommonFixture.VALID_BUYER));
        assertThat(nullAdsCustomAudience.getAds()).isEqualTo(Collections.emptyList());
    }

    @Test
    public void testBuildEmptyAdsCustomAudienceSuccess() {
        // An empty list is allowed and should not throw any exceptions
        ArrayList<AdData> emptyAds = new ArrayList<>(Collections.emptyList());

        CustomAudience emptyAdsCustomAudience =
                CustomAudienceFixture.getValidBuilderForBuyer(CommonFixture.VALID_BUYER)
                        .setAds(emptyAds)
                        .build();

        assertThat(emptyAdsCustomAudience.getOwnerPackageName())
                .isEqualTo(CustomAudienceFixture.VALID_OWNER);
        assertThat(emptyAdsCustomAudience.getBuyer()).isEqualTo(CommonFixture.VALID_BUYER);
        assertThat(emptyAdsCustomAudience.getName()).isEqualTo(CustomAudienceFixture.VALID_NAME);
        assertThat(emptyAdsCustomAudience.getActivationTime())
                .isEqualTo(CustomAudienceFixture.VALID_ACTIVATION_TIME);
        assertThat(emptyAdsCustomAudience.getExpirationTime())
                .isEqualTo(CustomAudienceFixture.VALID_EXPIRATION_TIME);
        assertThat(emptyAdsCustomAudience.getDailyUpdateUrl())
                .isEqualTo(
                        CustomAudienceFixture.getValidDailyUpdateUriByBuyer(
                                CommonFixture.VALID_BUYER));
        assertThat(emptyAdsCustomAudience.getUserBiddingSignals())
                .isEqualTo(CustomAudienceFixture.VALID_USER_BIDDING_SIGNALS);
        assertThat(emptyAdsCustomAudience.getTrustedBiddingData())
                .isEqualTo(
                        TrustedBiddingDataFixture.getValidTrustedBiddingDataByBuyer(
                                CommonFixture.VALID_BUYER));
        assertThat(emptyAdsCustomAudience.getBiddingLogicUrl())
                .isEqualTo(
                        CustomAudienceFixture.getValidBiddingLogicUrlByBuyer(
                                CommonFixture.VALID_BUYER));
        assertThat(emptyAdsCustomAudience.getAds()).isEqualTo(emptyAds);
    }

    @Test
    public void testCustomAudienceDescribeContent() {
        CustomAudience validCustomAudience =
                CustomAudienceFixture.getValidBuilderForBuyer(CommonFixture.VALID_BUYER).build();

        assertThat(validCustomAudience.describeContents()).isEqualTo(0);
    }
}

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

import static android.adservices.customaudience.CustomAudience.FLAG_AUCTION_SERVER_REQUEST_OMIT_ADS;

import static com.google.common.truth.Truth.assertWithMessage;

import static org.junit.Assert.assertThrows;

import android.adservices.clients.customaudience.AdvertisingCustomAudienceClient;
import android.adservices.common.AdData;
import android.adservices.common.AdDataFixture;
import android.adservices.common.AdSelectionSignals;
import android.adservices.common.CommonFixture;
import android.adservices.common.ComponentAdData;
import android.adservices.common.ComponentAdDataFixture;
import android.adservices.customaudience.CustomAudience;
import android.adservices.customaudience.CustomAudienceFixture;
import android.adservices.customaudience.TrustedBiddingData;
import android.adservices.customaudience.TrustedBiddingDataFixture;
import android.net.Uri;
import android.os.Parcel;

import com.android.adservices.shared.testing.annotations.RequiresLowRamDevice;

import com.google.common.collect.ImmutableList;

import org.junit.Test;

import java.time.Instant;
import java.time.temporal.ChronoUnit;
import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.concurrent.ExecutionException;
import java.util.concurrent.Executor;
import java.util.concurrent.Executors;

/** Unit tests for {@link android.adservices.customaudience.CustomAudience} */
public final class CustomAudienceTest extends CtsAdServicesDeviceTestCase {
    // TODO(b/342332791): add to these tests with CA priority

    private static final Executor sCallbackExecutor = Executors.newCachedThreadPool();
    private static final double PRIORITY = 5.3;

    @Test
    public void testBuildValidCustomAudienceSuccess() {
        CustomAudience validCustomAudience =
                CustomAudienceFixture.getValidBuilderForBuyer(CommonFixture.VALID_BUYER_1).build();

        expect.that(validCustomAudience.getBuyer()).isEqualTo(CommonFixture.VALID_BUYER_1);
        expect.that(validCustomAudience.getName()).isEqualTo(CustomAudienceFixture.VALID_NAME);
        expect.that(validCustomAudience.getActivationTime())
                .isEqualTo(CustomAudienceFixture.VALID_ACTIVATION_TIME);
        expect.that(validCustomAudience.getExpirationTime())
                .isEqualTo(CustomAudienceFixture.VALID_EXPIRATION_TIME);
        expect.that(validCustomAudience.getDailyUpdateUri())
                .isEqualTo(
                        CustomAudienceFixture.getValidDailyUpdateUriByBuyer(
                                CommonFixture.VALID_BUYER_1));
        expect.that(validCustomAudience.getUserBiddingSignals())
                .isEqualTo(CustomAudienceFixture.VALID_USER_BIDDING_SIGNALS);
        expect.that(validCustomAudience.getTrustedBiddingData())
                .isEqualTo(
                        TrustedBiddingDataFixture.getValidTrustedBiddingDataByBuyer(
                                CommonFixture.VALID_BUYER_1));
        expect.that(validCustomAudience.getBiddingLogicUri())
                .isEqualTo(
                        CustomAudienceFixture.getValidBiddingLogicUriByBuyer(
                                CommonFixture.VALID_BUYER_1));
        expect.that(validCustomAudience.getAds())
                .isEqualTo(AdDataFixture.getValidAdsByBuyer(CommonFixture.VALID_BUYER_1));
    }

    @Test
    public void testBuildValidCustomAudienceSuccessWithComponentAds() {
        List<ComponentAdData> componentAdDataList =
                ComponentAdDataFixture.getValidComponentAdsByBuyer(CommonFixture.VALID_BUYER_1);

        CustomAudience validCustomAudience =
                CustomAudienceFixture.getValidBuilderForBuyer(CommonFixture.VALID_BUYER_1)
                        .setComponentAds(componentAdDataList)
                        .build();
        assertWithMessage("Valid custom audience").that(validCustomAudience).isNotNull();

        expect.that(validCustomAudience.getBuyer()).isEqualTo(CommonFixture.VALID_BUYER_1);
        expect.that(validCustomAudience.getName()).isEqualTo(CustomAudienceFixture.VALID_NAME);
        expect.that(validCustomAudience.getActivationTime())
                .isEqualTo(CustomAudienceFixture.VALID_ACTIVATION_TIME);
        expect.that(validCustomAudience.getExpirationTime())
                .isEqualTo(CustomAudienceFixture.VALID_EXPIRATION_TIME);
        expect.that(validCustomAudience.getDailyUpdateUri())
                .isEqualTo(
                        CustomAudienceFixture.getValidDailyUpdateUriByBuyer(
                                CommonFixture.VALID_BUYER_1));
        expect.that(validCustomAudience.getUserBiddingSignals())
                .isEqualTo(CustomAudienceFixture.VALID_USER_BIDDING_SIGNALS);
        expect.that(validCustomAudience.getTrustedBiddingData())
                .isEqualTo(
                        TrustedBiddingDataFixture.getValidTrustedBiddingDataByBuyer(
                                CommonFixture.VALID_BUYER_1));
        expect.that(validCustomAudience.getBiddingLogicUri())
                .isEqualTo(
                        CustomAudienceFixture.getValidBiddingLogicUriByBuyer(
                                CommonFixture.VALID_BUYER_1));
        expect.that(validCustomAudience.getAds())
                .isEqualTo(AdDataFixture.getValidAdsByBuyer(CommonFixture.VALID_BUYER_1));
        expect.that(validCustomAudience.getComponentAds()).isEqualTo(componentAdDataList);
    }

    @Test
    public void testBuildValidCustomAudienceSuccessWithPriority() {
        CustomAudience validCustomAudience =
                CustomAudienceFixture.getValidBuilderForBuyer(CommonFixture.VALID_BUYER_1)
                        .setPriority(PRIORITY)
                        .build();

        expect.that(validCustomAudience.getBuyer()).isEqualTo(CommonFixture.VALID_BUYER_1);
        expect.that(validCustomAudience.getName()).isEqualTo(CustomAudienceFixture.VALID_NAME);
        expect.that(validCustomAudience.getActivationTime())
                .isEqualTo(CustomAudienceFixture.VALID_ACTIVATION_TIME);
        expect.that(validCustomAudience.getExpirationTime())
                .isEqualTo(CustomAudienceFixture.VALID_EXPIRATION_TIME);
        expect.that(validCustomAudience.getDailyUpdateUri())
                .isEqualTo(
                        CustomAudienceFixture.getValidDailyUpdateUriByBuyer(
                                CommonFixture.VALID_BUYER_1));
        expect.that(validCustomAudience.getUserBiddingSignals())
                .isEqualTo(CustomAudienceFixture.VALID_USER_BIDDING_SIGNALS);
        expect.that(validCustomAudience.getTrustedBiddingData())
                .isEqualTo(
                        TrustedBiddingDataFixture.getValidTrustedBiddingDataByBuyer(
                                CommonFixture.VALID_BUYER_1));
        expect.that(validCustomAudience.getBiddingLogicUri())
                .isEqualTo(
                        CustomAudienceFixture.getValidBiddingLogicUriByBuyer(
                                CommonFixture.VALID_BUYER_1));
        expect.that(validCustomAudience.getAds())
                .isEqualTo(AdDataFixture.getValidAdsByBuyer(CommonFixture.VALID_BUYER_1));
        expect.that(validCustomAudience.getPriority()).isWithin(0).of(PRIORITY);
    }

    @Test
    public void testBuildValidCustomAudienceSuccessWithoutPriority() {
        CustomAudience validCustomAudience =
                CustomAudienceFixture.getValidBuilderForBuyer(CommonFixture.VALID_BUYER_1).build();

        expect.that(validCustomAudience.getBuyer()).isEqualTo(CommonFixture.VALID_BUYER_1);
        expect.that(validCustomAudience.getName()).isEqualTo(CustomAudienceFixture.VALID_NAME);
        expect.that(validCustomAudience.getActivationTime())
                .isEqualTo(CustomAudienceFixture.VALID_ACTIVATION_TIME);
        expect.that(validCustomAudience.getExpirationTime())
                .isEqualTo(CustomAudienceFixture.VALID_EXPIRATION_TIME);
        expect.that(validCustomAudience.getDailyUpdateUri())
                .isEqualTo(
                        CustomAudienceFixture.getValidDailyUpdateUriByBuyer(
                                CommonFixture.VALID_BUYER_1));
        expect.that(validCustomAudience.getUserBiddingSignals())
                .isEqualTo(CustomAudienceFixture.VALID_USER_BIDDING_SIGNALS);
        expect.that(validCustomAudience.getTrustedBiddingData())
                .isEqualTo(
                        TrustedBiddingDataFixture.getValidTrustedBiddingDataByBuyer(
                                CommonFixture.VALID_BUYER_1));
        expect.that(validCustomAudience.getBiddingLogicUri())
                .isEqualTo(
                        CustomAudienceFixture.getValidBiddingLogicUriByBuyer(
                                CommonFixture.VALID_BUYER_1));
        expect.that(validCustomAudience.getAds())
                .isEqualTo(AdDataFixture.getValidAdsByBuyer(CommonFixture.VALID_BUYER_1));
        expect.that(validCustomAudience.getPriority()).isWithin(0).of(0);
    }

    @Test
    public void testBuildValidCustomAudienceSuccessWithAuctionServerRequestFlags() {
        CustomAudience validCustomAudience =
                CustomAudienceFixture.getValidBuilderForBuyer(CommonFixture.VALID_BUYER_1)
                        .setAuctionServerRequestFlags(FLAG_AUCTION_SERVER_REQUEST_OMIT_ADS)
                        .build();

        expect.that(validCustomAudience.getBuyer()).isEqualTo(CommonFixture.VALID_BUYER_1);
        expect.that(validCustomAudience.getName()).isEqualTo(CustomAudienceFixture.VALID_NAME);
        expect.that(validCustomAudience.getActivationTime())
                .isEqualTo(CustomAudienceFixture.VALID_ACTIVATION_TIME);
        expect.that(validCustomAudience.getExpirationTime())
                .isEqualTo(CustomAudienceFixture.VALID_EXPIRATION_TIME);
        expect.that(validCustomAudience.getDailyUpdateUri())
                .isEqualTo(
                        CustomAudienceFixture.getValidDailyUpdateUriByBuyer(
                                CommonFixture.VALID_BUYER_1));
        expect.that(validCustomAudience.getUserBiddingSignals())
                .isEqualTo(CustomAudienceFixture.VALID_USER_BIDDING_SIGNALS);
        expect.that(validCustomAudience.getTrustedBiddingData())
                .isEqualTo(
                        TrustedBiddingDataFixture.getValidTrustedBiddingDataByBuyer(
                                CommonFixture.VALID_BUYER_1));
        expect.that(validCustomAudience.getBiddingLogicUri())
                .isEqualTo(
                        CustomAudienceFixture.getValidBiddingLogicUriByBuyer(
                                CommonFixture.VALID_BUYER_1));
        expect.that(validCustomAudience.getAds())
                .isEqualTo(AdDataFixture.getValidAdsByBuyer(CommonFixture.VALID_BUYER_1));
        expect.that(validCustomAudience.getAuctionServerRequestFlags())
                .isEqualTo(FLAG_AUCTION_SERVER_REQUEST_OMIT_ADS);
    }

    @Test
    public void testBuildValidDelayedActivationCustomAudienceSuccess() {
        CustomAudience validDelayedActivationCustomAudience =
                CustomAudienceFixture.getValidBuilderForBuyer(CommonFixture.VALID_BUYER_1)
                        .setActivationTime(CustomAudienceFixture.VALID_DELAYED_ACTIVATION_TIME)
                        .setExpirationTime(CustomAudienceFixture.VALID_DELAYED_EXPIRATION_TIME)
                        .build();

        expect.that(validDelayedActivationCustomAudience.getBuyer())
                .isEqualTo(CommonFixture.VALID_BUYER_1);
        expect.that(validDelayedActivationCustomAudience.getName())
                .isEqualTo(CustomAudienceFixture.VALID_NAME);
        expect.that(validDelayedActivationCustomAudience.getActivationTime())
                .isEqualTo(CustomAudienceFixture.VALID_DELAYED_ACTIVATION_TIME);
        expect.that(validDelayedActivationCustomAudience.getExpirationTime())
                .isEqualTo(CustomAudienceFixture.VALID_DELAYED_EXPIRATION_TIME);
        expect.that(validDelayedActivationCustomAudience.getDailyUpdateUri())
                .isEqualTo(
                        CustomAudienceFixture.getValidDailyUpdateUriByBuyer(
                                CommonFixture.VALID_BUYER_1));
        expect.that(validDelayedActivationCustomAudience.getUserBiddingSignals())
                .isEqualTo(CustomAudienceFixture.VALID_USER_BIDDING_SIGNALS);
        expect.that(validDelayedActivationCustomAudience.getTrustedBiddingData())
                .isEqualTo(
                        TrustedBiddingDataFixture.getValidTrustedBiddingDataByBuyer(
                                CommonFixture.VALID_BUYER_1));
        expect.that(validDelayedActivationCustomAudience.getBiddingLogicUri())
                .isEqualTo(
                        CustomAudienceFixture.getValidBiddingLogicUriByBuyer(
                                CommonFixture.VALID_BUYER_1));
        expect.that(validDelayedActivationCustomAudience.getAds())
                .isEqualTo(AdDataFixture.getValidAdsByBuyer(CommonFixture.VALID_BUYER_1));
    }

    @Test
    public void testParcelValidCustomAudienceSuccess() {
        CustomAudience validCustomAudience =
                CustomAudienceFixture.getValidBuilderForBuyer(CommonFixture.VALID_BUYER_1).build();

        Parcel p = Parcel.obtain();
        validCustomAudience.writeToParcel(p, 0);
        p.setDataPosition(0);
        CustomAudience fromParcel = CustomAudience.CREATOR.createFromParcel(p);

        expect.that(fromParcel).isEqualTo(validCustomAudience);
    }

    @Test
    public void testParcelValidCustomAudienceSuccessWithComponentAds() {
        CustomAudience validCustomAudience =
                CustomAudienceFixture.getValidBuilderForBuyer(CommonFixture.VALID_BUYER_1)
                        .setComponentAds(
                                ComponentAdDataFixture.getValidComponentAdsByBuyer(
                                        CommonFixture.VALID_BUYER_1))
                        .build();

        Parcel p = Parcel.obtain();
        try {
            validCustomAudience.writeToParcel(p, 0);
            p.setDataPosition(0);
            CustomAudience fromParcel = CustomAudience.CREATOR.createFromParcel(p);

            expect.that(fromParcel).isEqualTo(validCustomAudience);
        } finally {
            p.recycle();
        }
    }

    /** @hide */
    @Test
    public void testParcelValidCustomAudienceSuccessWithPriority() {
        CustomAudience validCustomAudience =
                CustomAudienceFixture.getValidBuilderForBuyer(CommonFixture.VALID_BUYER_1)
                        .setPriority(PRIORITY)
                        .build();

        Parcel p = Parcel.obtain();
        validCustomAudience.writeToParcel(p, 0);
        p.setDataPosition(0);
        CustomAudience fromParcel = CustomAudience.CREATOR.createFromParcel(p);

        expect.that(fromParcel).isEqualTo(validCustomAudience);
    }

    @Test
    public void testParcelValidCustomAudienceSuccessWithAuctionServerRequestFlags() {
        CustomAudience validCustomAudience =
                CustomAudienceFixture.getValidBuilderForBuyer(CommonFixture.VALID_BUYER_1)
                        .setAuctionServerRequestFlags(FLAG_AUCTION_SERVER_REQUEST_OMIT_ADS)
                        .build();

        Parcel p = Parcel.obtain();
        try {
            validCustomAudience.writeToParcel(p, 0);
            p.setDataPosition(0);
            CustomAudience fromParcel = CustomAudience.CREATOR.createFromParcel(p);

            expect.that(fromParcel).isEqualTo(validCustomAudience);
        } finally {
            p.recycle();
        }
    }

    @Test
    public void testParcelValidCustomAudienceWithNullValueSuccess() {
        CustomAudience validCustomAudienceWithNullValue =
                CustomAudienceFixture.getValidBuilderForBuyer(CommonFixture.VALID_BUYER_1)
                        .setActivationTime(null)
                        .setTrustedBiddingData(null)
                        .build();

        Parcel p = Parcel.obtain();
        validCustomAudienceWithNullValue.writeToParcel(p, 0);
        p.setDataPosition(0);
        CustomAudience fromParcel = CustomAudience.CREATOR.createFromParcel(p);

        expect.that(fromParcel).isEqualTo(validCustomAudienceWithNullValue);
    }

    @Test
    public void testNonNullValueNotSetBuildFails() {
        assertThrows(
                NullPointerException.class,
                () -> {
                    // No buyer were set
                    new CustomAudience.Builder()
                            .setName(CustomAudienceFixture.VALID_NAME)
                            .setActivationTime(CustomAudienceFixture.VALID_DELAYED_ACTIVATION_TIME)
                            .setExpirationTime(CustomAudienceFixture.VALID_EXPIRATION_TIME)
                            .setDailyUpdateUri(
                                    CustomAudienceFixture.getValidDailyUpdateUriByBuyer(
                                            CommonFixture.VALID_BUYER_1))
                            .setUserBiddingSignals(CustomAudienceFixture.VALID_USER_BIDDING_SIGNALS)
                            .setTrustedBiddingData(
                                    TrustedBiddingDataFixture.getValidTrustedBiddingDataByBuyer(
                                            CommonFixture.VALID_BUYER_1))
                            .setBiddingLogicUri(
                                    CustomAudienceFixture.getValidBiddingLogicUriByBuyer(
                                            CommonFixture.VALID_BUYER_1))
                            .setAds(AdDataFixture.getValidAdsByBuyer(CommonFixture.VALID_BUYER_1))
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
                            .setBuyer(null)
                            .build();
                });
    }

    @Test
    public void testBuildNullAdsCustomAudienceSuccess() {
        // Ads are not set, so the CustomAudience gets built with empty list.
        CustomAudience nullAdsCustomAudience =
                CustomAudienceFixture.getValidBuilderForBuyer(CommonFixture.VALID_BUYER_1)
                        .setAds(null)
                        .build();

        expect.that(nullAdsCustomAudience.getBuyer()).isEqualTo(CommonFixture.VALID_BUYER_1);
        expect.that(nullAdsCustomAudience.getName()).isEqualTo(CustomAudienceFixture.VALID_NAME);
        expect.that(nullAdsCustomAudience.getActivationTime())
                .isEqualTo(CustomAudienceFixture.VALID_ACTIVATION_TIME);
        expect.that(nullAdsCustomAudience.getExpirationTime())
                .isEqualTo(CustomAudienceFixture.VALID_EXPIRATION_TIME);
        expect.that(nullAdsCustomAudience.getDailyUpdateUri())
                .isEqualTo(
                        CustomAudienceFixture.getValidDailyUpdateUriByBuyer(
                                CommonFixture.VALID_BUYER_1));
        expect.that(nullAdsCustomAudience.getUserBiddingSignals())
                .isEqualTo(CustomAudienceFixture.VALID_USER_BIDDING_SIGNALS);
        expect.that(nullAdsCustomAudience.getTrustedBiddingData())
                .isEqualTo(
                        TrustedBiddingDataFixture.getValidTrustedBiddingDataByBuyer(
                                CommonFixture.VALID_BUYER_1));
        expect.that(nullAdsCustomAudience.getBiddingLogicUri())
                .isEqualTo(
                        CustomAudienceFixture.getValidBiddingLogicUriByBuyer(
                                CommonFixture.VALID_BUYER_1));
        expect.that(nullAdsCustomAudience.getAds()).isEqualTo(Collections.emptyList());
    }

    @Test
    public void testBuildEmptyAdsCustomAudienceSuccess() {
        // An empty list is allowed and should not throw any exceptions
        ArrayList<AdData> emptyAds = new ArrayList<>(Collections.emptyList());

        CustomAudience emptyAdsCustomAudience =
                CustomAudienceFixture.getValidBuilderForBuyer(CommonFixture.VALID_BUYER_1)
                        .setAds(emptyAds)
                        .build();

        expect.that(emptyAdsCustomAudience.getBuyer()).isEqualTo(CommonFixture.VALID_BUYER_1);
        expect.that(emptyAdsCustomAudience.getName()).isEqualTo(CustomAudienceFixture.VALID_NAME);
        expect.that(emptyAdsCustomAudience.getActivationTime())
                .isEqualTo(CustomAudienceFixture.VALID_ACTIVATION_TIME);
        expect.that(emptyAdsCustomAudience.getExpirationTime())
                .isEqualTo(CustomAudienceFixture.VALID_EXPIRATION_TIME);
        expect.that(emptyAdsCustomAudience.getDailyUpdateUri())
                .isEqualTo(
                        CustomAudienceFixture.getValidDailyUpdateUriByBuyer(
                                CommonFixture.VALID_BUYER_1));
        expect.that(emptyAdsCustomAudience.getUserBiddingSignals())
                .isEqualTo(CustomAudienceFixture.VALID_USER_BIDDING_SIGNALS);
        expect.that(emptyAdsCustomAudience.getTrustedBiddingData())
                .isEqualTo(
                        TrustedBiddingDataFixture.getValidTrustedBiddingDataByBuyer(
                                CommonFixture.VALID_BUYER_1));
        expect.that(emptyAdsCustomAudience.getBiddingLogicUri())
                .isEqualTo(
                        CustomAudienceFixture.getValidBiddingLogicUriByBuyer(
                                CommonFixture.VALID_BUYER_1));
        expect.that(emptyAdsCustomAudience.getAds()).isEqualTo(emptyAds);
    }

    @Test
    public void testCustomAudienceDescribeContent() {
        CustomAudience validCustomAudience =
                CustomAudienceFixture.getValidBuilderForBuyer(CommonFixture.VALID_BUYER_1).build();

        expect.that(validCustomAudience.describeContents()).isEqualTo(0);
    }

    @Test
    @RequiresLowRamDevice
    public void testGetCustomAudienceService_lowRamDevice_throwsIllegalStateException() {
        AdvertisingCustomAudienceClient client =
                new AdvertisingCustomAudienceClient.Builder()
                        .setContext(sContext)
                        .setExecutor(sCallbackExecutor)
                        .setUseGetMethodToCreateManagerInstance(true)
                        .build();

        CustomAudience customAudience =
                new CustomAudience.Builder()
                        .setName(CustomAudienceFixture.VALID_NAME)
                        .setDailyUpdateUri(Uri.parse("http://example.com"))
                        .setTrustedBiddingData(
                                new TrustedBiddingData.Builder()
                                        .setTrustedBiddingKeys(ImmutableList.of())
                                        .setTrustedBiddingUri(Uri.parse("http://example.com"))
                                        .build())
                        .setUserBiddingSignals(AdSelectionSignals.fromString("{}"))
                        .setAds(List.of())
                        .setBiddingLogicUri(Uri.parse("http://example.com"))
                        .setBuyer(CommonFixture.VALID_BUYER_1)
                        .setActivationTime(Instant.now())
                        .setExpirationTime(Instant.now().plus(5, ChronoUnit.DAYS))
                        .build();

        Exception exception =
                assertThrows(
                        ExecutionException.class,
                        () -> client.joinCustomAudience(customAudience).get());
        expect.that(exception).hasCauseThat().isInstanceOf(IllegalStateException.class);
        expect.that(exception).hasMessageThat().contains("service is not available");
    }
}

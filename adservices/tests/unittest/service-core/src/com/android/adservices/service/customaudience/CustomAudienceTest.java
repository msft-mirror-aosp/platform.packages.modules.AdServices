/*
 * Copyright (C) 2024 The Android Open Source Project
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

import static org.junit.Assert.assertEquals;

import android.adservices.common.AdDataFixture;
import android.adservices.common.CommonFixture;
import android.adservices.customaudience.CustomAudience;
import android.adservices.customaudience.CustomAudienceFixture;
import android.adservices.customaudience.TrustedBiddingDataFixture;
import android.content.Context;
import android.os.Parcel;

import androidx.test.core.app.ApplicationProvider;

import com.android.adservices.common.AdServicesDeviceSupportedRule;
import com.android.adservices.common.SdkLevelSupportRule;

import org.junit.Rule;
import org.junit.Test;

import java.util.concurrent.Executor;
import java.util.concurrent.Executors;

// TODO(b/320786372): Move these tests to CTS when methods are unhidden
public class CustomAudienceTest {

    private static final Context sContext = ApplicationProvider.getApplicationContext();
    private static final Executor sCallbackExecutor = Executors.newCachedThreadPool();

    // TODO(b/291488819) - Remove SDK Level check if Fledge is enabled on R.
    @Rule(order = 0)
    public final SdkLevelSupportRule sdkLevel = SdkLevelSupportRule.forAtLeastS();

    // Skip the test if it runs on unsupported platforms.
    @Rule(order = 1)
    public final AdServicesDeviceSupportedRule adServicesDeviceSupportedRule =
            new AdServicesDeviceSupportedRule();

    @Test
    public void testBuildValidCustomAudienceSuccessWithAuctionServerRequestFlags() {
        CustomAudience validCustomAudience =
                CustomAudienceFixture.getValidBuilderForBuyer(CommonFixture.VALID_BUYER_1)
                        .setAuctionServerRequestFlags(FLAG_AUCTION_SERVER_REQUEST_OMIT_ADS)
                        .build();

        assertEquals(CommonFixture.VALID_BUYER_1, validCustomAudience.getBuyer());
        assertEquals(CustomAudienceFixture.VALID_NAME, validCustomAudience.getName());
        assertEquals(
                CustomAudienceFixture.VALID_ACTIVATION_TIME,
                validCustomAudience.getActivationTime());
        assertEquals(
                CustomAudienceFixture.VALID_EXPIRATION_TIME,
                validCustomAudience.getExpirationTime());
        assertEquals(
                CustomAudienceFixture.getValidDailyUpdateUriByBuyer(CommonFixture.VALID_BUYER_1),
                validCustomAudience.getDailyUpdateUri());
        assertEquals(
                CustomAudienceFixture.VALID_USER_BIDDING_SIGNALS,
                validCustomAudience.getUserBiddingSignals());
        assertEquals(
                TrustedBiddingDataFixture.getValidTrustedBiddingDataByBuyer(
                        CommonFixture.VALID_BUYER_1),
                validCustomAudience.getTrustedBiddingData());
        assertEquals(
                CustomAudienceFixture.getValidBiddingLogicUriByBuyer(CommonFixture.VALID_BUYER_1),
                validCustomAudience.getBiddingLogicUri());
        assertEquals(
                AdDataFixture.getValidAdsByBuyer(CommonFixture.VALID_BUYER_1),
                validCustomAudience.getAds());
        assertEquals(
                FLAG_AUCTION_SERVER_REQUEST_OMIT_ADS,
                validCustomAudience.getAuctionServerRequestFlags());
    }

    @Test
    public void testParcelValidCustomAudienceSuccessWithAuctionServerRequestFlags() {
        CustomAudience validCustomAudience =
                CustomAudienceFixture.getValidBuilderForBuyer(CommonFixture.VALID_BUYER_1)
                        .setAuctionServerRequestFlags(FLAG_AUCTION_SERVER_REQUEST_OMIT_ADS)
                        .build();

        Parcel p = Parcel.obtain();
        validCustomAudience.writeToParcel(p, 0);
        p.setDataPosition(0);
        CustomAudience fromParcel = CustomAudience.CREATOR.createFromParcel(p);

        assertEquals(validCustomAudience, fromParcel);
    }
}

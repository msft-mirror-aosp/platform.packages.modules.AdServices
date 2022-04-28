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

import android.adservices.common.CommonFixture;
import android.net.Uri;

import com.android.adservices.data.customaudience.DBCustomAudience;
import com.android.adservices.data.customaudience.DBTrustedBiddingData;

import java.time.Instant;

/** Utility class supporting custom audience API unit tests */
public final class CustomAudienceFixture {

    public static final long DAY_IN_SECONDS = 60 * 60 * 24;
    public static final String VALID_OWNER = "testOwnerApplication";
    public static final String VALID_BUYER = "valid-buyer.example.com";
    public static final String VALID_NAME = "testCustomAudienceName";

    public static final Instant VALID_ACTIVATION_TIME =
            CommonFixture.FIXED_NOW_TRUNCATED_TO_MILLI;
    public static final Instant VALID_DELAYED_ACTIVATION_TIME =
            CommonFixture.FIXED_NOW_TRUNCATED_TO_MILLI
                    .plus(DBCustomAudience.getMaxActivateIn().dividedBy(2));
    public static final Instant INVALID_DELAYED_ACTIVATION_TIME =
            CommonFixture.FIXED_NOW_TRUNCATED_TO_MILLI
                    .plus(DBCustomAudience.getMaxActivateIn().multipliedBy(2));

    public static final Instant VALID_EXPIRATION_TIME =
            VALID_ACTIVATION_TIME.plus(DBCustomAudience.getDefaultExpireIn());
    public static final Instant VALID_DELAYED_EXPIRATION_TIME =
            VALID_DELAYED_ACTIVATION_TIME.plusSeconds(DAY_IN_SECONDS);
    public static final Instant INVALID_BEFORE_NOW_EXPIRATION_TIME =
            VALID_ACTIVATION_TIME.minusSeconds(DAY_IN_SECONDS);
    public static final Instant INVALID_BEFORE_DELAYED_EXPIRATION_TIME =
            VALID_DELAYED_ACTIVATION_TIME.minusSeconds(DAY_IN_SECONDS);
    public static final Instant INVALID_BEYOND_MAX_EXPIRATION_TIME =
            VALID_ACTIVATION_TIME.plus(DBCustomAudience.getMaxExpireIn().multipliedBy(2));

    public static final Uri VALID_DAILY_UPDATE_URL =
            new Uri.Builder().path("valid-update-url.example.com").build();
    public static final String VALID_USER_BIDDING_SIGNALS =
            "{'valid': 'yep', 'opaque': 'definitely'}";

    public static final Uri VALID_BIDDING_LOGIC_URL =
            new Uri.Builder().path("valid-buyer.example.com/bidding/logic/here/").build();

    public static DBCustomAudience.Builder getDBCustomAudienceBuilder() {
        return new DBCustomAudience.Builder()
                .setOwner(CustomAudienceFixture.VALID_OWNER)
                .setBuyer(CustomAudienceFixture.VALID_BUYER)
                .setName(CustomAudienceFixture.VALID_NAME)
                .setActivationTime(CustomAudienceFixture.VALID_ACTIVATION_TIME)
                .setExpirationTime(CustomAudienceFixture.VALID_EXPIRATION_TIME)
                .setCreationTime(CommonFixture.FIXED_NOW_TRUNCATED_TO_MILLI)
                .setLastAdsAndBiddingDataUpdatedTime(CommonFixture.FIXED_NOW_TRUNCATED_TO_MILLI)
                .setDailyUpdateUrl(CustomAudienceFixture.VALID_DAILY_UPDATE_URL)
                .setUserBiddingSignals(CustomAudienceFixture.VALID_USER_BIDDING_SIGNALS)
                .setTrustedBiddingData(
                        new DBTrustedBiddingData.Builder()
                                .setUrl(TrustedBiddingDataFixture.VALID_TRUSTED_BIDDING_URL)
                                .setKeys(TrustedBiddingDataFixture.VALID_TRUSTED_BIDDING_KEYS)
                                .build())
                .setBiddingLogicUrl(CustomAudienceFixture.VALID_BIDDING_LOGIC_URL);
    }
}

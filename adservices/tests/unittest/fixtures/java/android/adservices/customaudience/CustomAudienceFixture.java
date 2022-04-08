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

import android.net.Uri;

import java.time.Instant;
import java.time.temporal.ChronoUnit;

/** Utility class supporting custom audience API unit tests */
public final class CustomAudienceFixture {

    public static final long DAY_IN_SECONDS = 60 * 60 * 24;
    public static final String VALID_OWNER = "testOwnerApplication";
    public static final String VALID_BUYER = "valid-buyer.example.com";
    public static final String VALID_NAME = "testCustomAudienceName";

    public static final Instant VALID_ACTIVATION_TIME =
            Instant.now().truncatedTo(ChronoUnit.SECONDS);
    public static final Instant VALID_DELAYED_ACTIVATION_TIME =
            Instant.now().plusSeconds(CustomAudience.getMaxFutureActivationTimeSeconds() / 2)
                    .truncatedTo(ChronoUnit.SECONDS);
    public static final Instant INVALID_DELAYED_ACTIVATION_TIME =
            Instant.now().plusSeconds(CustomAudience.getMaxFutureActivationTimeSeconds() * 2)
                    .truncatedTo(ChronoUnit.SECONDS);

    public static final Instant VALID_EXPIRATION_TIME =
            VALID_ACTIVATION_TIME.plusSeconds(CustomAudience.getDefaultExpirationTimeSeconds());
    public static final Instant VALID_DELAYED_EXPIRATION_TIME =
            VALID_DELAYED_ACTIVATION_TIME.plusSeconds(DAY_IN_SECONDS);
    public static final Instant INVALID_BEFORE_NOW_EXPIRATION_TIME =
            VALID_ACTIVATION_TIME.minusSeconds(DAY_IN_SECONDS);
    public static final Instant INVALID_BEFORE_DELAYED_EXPIRATION_TIME =
            VALID_DELAYED_ACTIVATION_TIME.minusSeconds(DAY_IN_SECONDS);
    public static final Instant INVALID_BEYOND_MAX_EXPIRATION_TIME =
            VALID_ACTIVATION_TIME.plusSeconds(
                    CustomAudience.getMaxFutureExpirationTimeSeconds() * 2);

    public static final Uri VALID_DAILY_UPDATE_URL =
            new Uri.Builder().path("valid-update-url.example.com").build();
    public static final String VALID_USER_BIDDING_SIGNALS =
            "{'valid': 'yep', 'opaque': 'definitely'}";

    public static final Uri VALID_BIDDING_LOGIC_URL =
            new Uri.Builder().path("valid-buyer.example.com/bidding/logic/here/").build();
}

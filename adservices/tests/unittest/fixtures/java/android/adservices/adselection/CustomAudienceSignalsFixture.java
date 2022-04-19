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

package android.adservices.adselection;

import com.android.adservices.data.adselection.CustomAudienceSignals;

import java.time.Clock;
import java.time.Duration;
import java.time.Instant;
import java.time.ZoneOffset;
import java.time.temporal.ChronoUnit;

public class CustomAudienceSignalsFixture {
    public static final String OWNER = "owner";
    public static final String BUYER = "buyer";
    public static final String NAME = "name";
    public static final Clock CLOCK = Clock.fixed(Instant.now(), ZoneOffset.UTC);
    public static final Instant ACTIVATION_TIME = CLOCK.instant().truncatedTo(ChronoUnit.MILLIS);
    public static final Instant EXPIRATION_TIME =
            CLOCK.instant().plus(Duration.ofDays(1)).truncatedTo(ChronoUnit.MILLIS);
    public static final String USER_BIDDING_SIGNALS = "exampleUserBiddingSignals";

    public static CustomAudienceSignals aCustomAudienceSignals() {
        return new CustomAudienceSignals.Builder()
                .setOwner(OWNER)
                .setBuyer(BUYER)
                .setName(NAME)
                .setActivationTime(ACTIVATION_TIME)
                .setExpirationTime(EXPIRATION_TIME)
                .setUserBiddingSignals(USER_BIDDING_SIGNALS)
                .build();
    }
}

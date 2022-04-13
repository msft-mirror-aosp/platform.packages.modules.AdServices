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

package com.android.adservices.data.adselection;

import static org.junit.Assert.assertEquals;

import org.junit.Test;

import java.time.Clock;
import java.time.Duration;
import java.time.Instant;
import java.time.ZoneOffset;

public class CustomAudienceSignalsTest {
    private static final String OWNER = "owner";
    private static final String BUYER = "buyer";
    private static final String NAME = "name";
    private static final Clock CLOCK = Clock.fixed(Instant.now(), ZoneOffset.UTC);
    private static final Instant ACTIVATION_TIME = CLOCK.instant();
    private static final Instant EXPIRATION_TIME = CLOCK.instant().plus(Duration.ofDays(1));
    private static final String USER_BIDDING_SIGNALS = "exampleUserBiddingSignals";

    @Test
    public void testBuildCustomAudienceSignals() {
        CustomAudienceSignals customAudienceSignals =
                new CustomAudienceSignals.Builder()
                        .setOwner(OWNER)
                        .setBuyer(BUYER)
                        .setName(NAME)
                        .setActivationTime(ACTIVATION_TIME)
                        .setExpirationTime(EXPIRATION_TIME)
                        .setUserBiddingSignals(USER_BIDDING_SIGNALS)
                        .build();

        assertEquals(customAudienceSignals.getOwner(), OWNER);
        assertEquals(customAudienceSignals.getBuyer(), BUYER);
        assertEquals(customAudienceSignals.getName(), NAME);
        assertEquals(customAudienceSignals.getActivationTime(), ACTIVATION_TIME);
        assertEquals(customAudienceSignals.getExpirationTime(), EXPIRATION_TIME);
        assertEquals(customAudienceSignals.getUserBiddingSignals(), USER_BIDDING_SIGNALS);
    }
}

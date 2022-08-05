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

import android.adservices.adselection.CustomAudienceSignalsFixture;
import android.adservices.common.AdTechIdentifier;
import android.adservices.common.CommonFixture;
import android.adservices.customaudience.CustomAudienceFixture;

import com.android.adservices.customaudience.DBCustomAudienceFixture;

import org.junit.Test;

public class CustomAudienceSignalsTest {
    @Test
    public void testBuildCustomAudienceSignals() {
        CustomAudienceSignals customAudienceSignals =
                CustomAudienceSignalsFixture.aCustomAudienceSignalsBuilder()
                        .setBuyer(CommonFixture.VALID_BUYER.toString())
                        .build();

        assertEquals(customAudienceSignals.getOwner(), CustomAudienceFixture.VALID_OWNER);
        assertEquals(
                AdTechIdentifier.fromString(customAudienceSignals.getBuyer()),
                CommonFixture.VALID_BUYER);
        assertEquals(customAudienceSignals.getName(), CustomAudienceFixture.VALID_NAME);
        assertEquals(
                customAudienceSignals.getActivationTime(),
                CustomAudienceFixture.VALID_ACTIVATION_TIME);
        assertEquals(
                customAudienceSignals.getExpirationTime(),
                CustomAudienceFixture.VALID_EXPIRATION_TIME);
        assertEquals(
                customAudienceSignals.getUserBiddingSignals(),
                CustomAudienceFixture.VALID_USER_BIDDING_SIGNALS.toString());
    }

    @Test
    public void testBuildFromCustomAudience() {
        CustomAudienceSignals customAudienceSignals =
                CustomAudienceSignals.buildFromCustomAudience(
                        DBCustomAudienceFixture.getValidBuilderByBuyer(CommonFixture.VALID_BUYER)
                                .build());

        assertEquals(customAudienceSignals.getOwner(), CustomAudienceFixture.VALID_OWNER);
        assertEquals(
                AdTechIdentifier.fromString(customAudienceSignals.getBuyer()),
                CommonFixture.VALID_BUYER);
        assertEquals(customAudienceSignals.getName(), CustomAudienceFixture.VALID_NAME);
        assertEquals(
                customAudienceSignals.getActivationTime(),
                CustomAudienceFixture.VALID_ACTIVATION_TIME);
        assertEquals(
                customAudienceSignals.getExpirationTime(),
                CustomAudienceFixture.VALID_EXPIRATION_TIME);
        assertEquals(
                customAudienceSignals.getUserBiddingSignals(),
                CustomAudienceFixture.VALID_USER_BIDDING_SIGNALS.toString());
    }
}

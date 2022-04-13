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

package com.android.adservices.service.adselection;

import static com.android.adservices.service.js.JSScriptArgument.jsonArg;
import static com.android.adservices.service.js.JSScriptArgument.numericArg;
import static com.android.adservices.service.js.JSScriptArgument.recordArg;
import static com.android.adservices.service.js.JSScriptArgument.stringArg;

import static com.google.common.truth.Truth.assertThat;

import com.android.adservices.data.adselection.CustomAudienceSignals;

import org.json.JSONException;
import org.junit.Test;

import java.time.Instant;

public class CustomAudienceSignalsArgumentTest {
    private static final CustomAudienceSignals CUSTOM_AUDIENCE_SIGNALS =
            new CustomAudienceSignals.Builder()
                    .setOwner("test_owner")
                    .setBuyer("test_buyer")
                    .setName("test_name")
                    .setActivationTime(Instant.now())
                    .setExpirationTime(Instant.now())
                    .setUserBiddingSignals("{\"user_bidding_signals\":1}")
                    .build();

    @Test
    public void testConversionToScriptArgument() throws JSONException {
        assertThat(CustomAudienceSignalsArgument.asScriptArgument(CUSTOM_AUDIENCE_SIGNALS, "name"))
                .isEqualTo(
                        recordArg(
                                "name",
                                stringArg(
                                        CustomAudienceSignalsArgument.OWNER_FIELD_NAME,
                                        CUSTOM_AUDIENCE_SIGNALS.getOwner()),
                                stringArg(
                                        CustomAudienceSignalsArgument.BUYER_FIELD_NAME,
                                        CUSTOM_AUDIENCE_SIGNALS.getBuyer()),
                                stringArg(
                                        CustomAudienceSignalsArgument.NAME_FIELD_NAME,
                                        CUSTOM_AUDIENCE_SIGNALS.getName()),
                                numericArg(
                                        CustomAudienceSignalsArgument.ACTIVATION_TIME_FIELD_NAME,
                                        CustomAudienceSignalsArgument.instantToLong(
                                                CUSTOM_AUDIENCE_SIGNALS.getActivationTime())),
                                numericArg(
                                        CustomAudienceSignalsArgument.EXPIRATION_TIME_FIELD_NAME,
                                        CustomAudienceSignalsArgument.instantToLong(
                                                CUSTOM_AUDIENCE_SIGNALS.getExpirationTime())),
                                jsonArg(
                                        CustomAudienceSignalsArgument
                                                .USER_BIDDING_SIGNALS_FIELD_NAME,
                                        CUSTOM_AUDIENCE_SIGNALS.getUserBiddingSignals())));
    }

    @Test
    public void testInstantToLong() {
        long testLong = 123456789;
        Instant instant = Instant.ofEpochMilli(testLong);
        assertThat(CustomAudienceSignalsArgument.instantToLong(instant)).isEqualTo(testLong);
    }
}

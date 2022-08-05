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

import static com.android.adservices.service.js.JSScriptArgument.arrayArg;
import static com.android.adservices.service.js.JSScriptArgument.jsonArg;
import static com.android.adservices.service.js.JSScriptArgument.recordArg;
import static com.android.adservices.service.js.JSScriptArgument.stringArg;
import static com.android.adservices.service.js.JSScriptArgument.stringArrayArg;

import static com.google.common.truth.Truth.assertThat;

import android.adservices.adselection.AdSelectionConfig;
import android.adservices.adselection.AdWithBid;
import android.adservices.common.AdData;
import android.adservices.common.AdSelectionSignals;
import android.adservices.common.AdTechIdentifier;
import android.net.Uri;

import androidx.test.filters.SmallTest;

import com.google.common.collect.ImmutableList;
import com.google.common.collect.ImmutableMap;

import org.json.JSONException;
import org.junit.Test;

import java.util.stream.Collectors;

@SmallTest
public class AdSelectionConfigArgumentTest {
    public static final AdTechIdentifier BUYER_1 = AdTechIdentifier.fromString("buyer1");
    public static final AdTechIdentifier BUYER_2 = AdTechIdentifier.fromString("buyer2");
    public static final AdWithBid AD_WITH_BID =
            new AdWithBid(new AdData(Uri.parse("http://buyer.com/ads/1"), "{\"metadata\":1}"), 10);
    public static final AdSelectionConfig AD_SELECTION_CONFIG =
            new AdSelectionConfig.Builder()
                    .setSeller(AdTechIdentifier.fromString("seller"))
                    .setAdSelectionSignals(
                            AdSelectionSignals.fromString("{\"ad_selection_signals\":1}"))
                    .setDecisionLogicUri(Uri.parse("http://seller.com/decision_logic"))
                    .setContextualAds(ImmutableList.of(AD_WITH_BID))
                    .setCustomAudienceBuyers(ImmutableList.of(BUYER_1, BUYER_2))
                    .setSellerSignals(AdSelectionSignals.fromString("{\"seller_signals\":1}"))
                    .setTrustedScoringSignalsUri(Uri.parse("https://kvtrusted.com/scoring_signals"))
                    .setPerBuyerSignals(
                            ImmutableMap.of(
                                    BUYER_1,
                                    AdSelectionSignals.fromString("{\"buyer_signals\":1}"),
                                    BUYER_2,
                                    AdSelectionSignals.fromString("{\"buyer_signals\":2}")))
                    .build();

    @Test
    public void testConversionToScriptArgument() throws JSONException {
        assertThat(AdSelectionConfigArgument.asScriptArgument(AD_SELECTION_CONFIG, "name"))
                .isEqualTo(
                        recordArg(
                                "name",
                                stringArg(
                                        AdSelectionConfigArgument.SELLER_FIELD_NAME,
                                        AD_SELECTION_CONFIG.getSeller().toString()),
                                stringArg(
                                        AdSelectionConfigArgument.DECISION_LOGIC_URI_FIELD_NAME,
                                        AD_SELECTION_CONFIG.getDecisionLogicUri().toString()),
                                stringArrayArg(
                                        AdSelectionConfigArgument.CUSTOM_AUDIENCE_BUYERS_FIELD_NAME,
                                        AD_SELECTION_CONFIG.getCustomAudienceBuyers().stream()
                                                .map(AdTechIdentifier::toString)
                                                .collect(Collectors.toList())),
                                jsonArg(
                                        AdSelectionConfigArgument.AUCTION_SIGNALS_FIELD_NAME,
                                        AD_SELECTION_CONFIG.getAdSelectionSignals().toString()),
                                jsonArg(
                                        AdSelectionConfigArgument.SELLER_SIGNALS_FIELD_NAME,
                                        AD_SELECTION_CONFIG.getSellerSignals().toString()),
                                recordArg(
                                        AdSelectionConfigArgument.PER_BUYER_SIGNALS_FIELD_NAME,
                                        ImmutableList.of(
                                                jsonArg("buyer1", "{\"buyer_signals\":1}"),
                                                jsonArg("buyer2", "{\"buyer_signals\":2}"))),
                                arrayArg(
                                        AdSelectionConfigArgument.CONTEXTUAL_ADS_FIELD_NAME,
                                        AdWithBidArgument.asScriptArgument("ignored", AD_WITH_BID)),
                                stringArg(
                                        AdSelectionConfigArgument
                                                .TRUSTED_SCORING_SIGNAL_URI_FIELD_NAME,
                                        AD_SELECTION_CONFIG
                                                .getTrustedScoringSignalsUri()
                                                .toString())));
    }
}

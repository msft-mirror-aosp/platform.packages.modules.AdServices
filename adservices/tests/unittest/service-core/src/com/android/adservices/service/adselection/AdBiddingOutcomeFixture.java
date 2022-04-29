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

import android.adservices.adselection.AdWithBid;
import android.adservices.common.AdData;
import android.net.Uri;

public class AdBiddingOutcomeFixture {

    public static AdBiddingOutcome.Builder anAdBiddingOutcomeBuilder(String buyerName, Double bid) {

        final AdData adData = new AdData(
                new Uri.Builder().path("valid.example.com/testing/hello/" + buyerName).build(),
                "{'example': 'metadata', 'valid': true}");
        final double testBid = bid;

        return AdBiddingOutcome.builder()
                .setAdWithBid(new AdWithBid(adData, testBid))
                .setCustomAudienceBiddingInfo(CustomAudienceBiddingInfo.builder()
                        .setBiddingLogicUrl(CustomAudienceBiddingInfoFixture
                                .VALID_BIDDING_LOGIC_URL)
                        .setBuyerDecisionLogicJs(
                                CustomAudienceBiddingInfoFixture.BUYER_DECISION_LOGIC_JS)
                        .setCustomAudienceSignals(
                                CustomAudienceBiddingInfoFixture.CUSTOM_AUDIENCE_SIGNAL_BUILDER
                                        .setBuyer(buyerName).build())
                        .build());
    }

}

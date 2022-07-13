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

import android.adservices.adselection.AdSelectionConfig;
import android.adservices.adselection.AdWithBid;

import com.android.adservices.service.js.JSScriptArgument;

import com.google.common.collect.ImmutableList;

import org.json.JSONException;

import java.util.Map;

/**
 * A utility class to convert instances of {@link AdSelectionConfig} into {@link JSScriptArgument}
 */
public class AdSelectionConfigArgument {

    // TODO: (b/228094391): Put these common constants in a separate class
    public static final String SELLER_FIELD_NAME = "seller";
    public static final String DECISION_LOGIC_URL_FIELD_NAME = "decision_logic_url";
    public static final String CUSTOM_AUDIENCE_BUYERS_FIELD_NAME = "custom_audience_buyers";
    public static final String AUCTION_SIGNALS_FIELD_NAME = "auction_signals";
    public static final String SELLER_SIGNALS_FIELD_NAME = "seller_signals";
    public static final String PER_BUYER_SIGNALS_FIELD_NAME = "per_buyer_signals";
    public static final String CONTEXTUAL_ADS_FIELD_NAME = "contextual_ads";

    // No instance of this class is supposed to be created
    private AdSelectionConfigArgument() {}

    /**
     * @return A {@link JSScriptArgument} with the given {@code name} to represent this instance of
     *     {@link AdSelectionConfigArgument}
     * @throws JSONException if any of the signals in this class is not valid JSON.
     */
    public static JSScriptArgument asScriptArgument(
            AdSelectionConfig adSelectionConfig, String name) throws JSONException {
        ImmutableList.Builder<JSScriptArgument> perBuyerSignalsArg = ImmutableList.builder();
        for (Map.Entry<String, String> buyerSignal :
                adSelectionConfig.getPerBuyerSignals().entrySet()) {
            perBuyerSignalsArg.add(jsonArg(buyerSignal.getKey(), buyerSignal.getValue()));
        }

        final ImmutableList.Builder<JSScriptArgument> contextualAdsArgsBuilder =
                new ImmutableList.Builder<>();
        for (AdWithBid adWithBid : adSelectionConfig.getContextualAds()) {
            contextualAdsArgsBuilder.add(AdWithBidArgument.asScriptArgument("ignored", adWithBid));
        }
        return recordArg(
                name,
                stringArg(SELLER_FIELD_NAME, adSelectionConfig.getSeller()),
                stringArg(
                        DECISION_LOGIC_URL_FIELD_NAME,
                        adSelectionConfig.getDecisionLogicUri().toString()),
                stringArrayArg(
                        CUSTOM_AUDIENCE_BUYERS_FIELD_NAME,
                        adSelectionConfig.getCustomAudienceBuyers()),
                jsonArg(AUCTION_SIGNALS_FIELD_NAME, adSelectionConfig.getAdSelectionSignals()),
                jsonArg(SELLER_SIGNALS_FIELD_NAME, adSelectionConfig.getSellerSignals()),
                recordArg(PER_BUYER_SIGNALS_FIELD_NAME, perBuyerSignalsArg.build()),
                arrayArg(CONTEXTUAL_ADS_FIELD_NAME, contextualAdsArgsBuilder.build()));
    }
}

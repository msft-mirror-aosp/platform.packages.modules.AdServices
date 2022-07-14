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

import android.adservices.common.AdData;
import android.net.Uri;

import androidx.annotation.NonNull;

import java.util.Arrays;
import java.util.List;
import java.util.Map;

/** This is a static class meant to help with tests that involve creating an AdSelectionConfig. */
public class AdSelectionConfigFixture {

    public static final String SELLER = "testSeller";

    // Uri Constants
    public static final String SCHEME = "testScheme";
    public static final String SSP = "testSSP";
    public static final String FRAGMENT = "testFragment";

    public static final Uri DECISION_LOGIC_URI = Uri.fromParts(SCHEME, SSP, FRAGMENT);

    public static final String BUYER_1 = "buyer1";
    public static final String BUYER_2 = "buyer2";
    public static final String BUYER_3 = "buyer3";
    public static final List<String> CUSTOM_AUDIENCE_BUYERS =
            Arrays.asList("buyer1", "buyer2", "buyer3");

    public static final String EMPTY_SIGNALS = "{}";

    public static final String AD_SELECTION_SIGNALS = "{\"ad_selection_signals\":1}";

    public static final String SELLER_SIGNALS = "{\"test_seller_signals\":1}";

    public static final Map<String, String> PER_BUYER_SIGNALS =
            Map.of(
                    "buyer1",
                    "{\"buyer_signals\":1}",
                    "buyer2",
                    "{\"buyer_signals\":2}",
                    "buyer3",
                    "{\"buyer_signals\":3}",
                    "buyer",
                    "{\"buyer_signals\":0}");

    // Contextual Ads Components
    public static final AdWithBid ADS_WITH_BID_1 =
            createAdsWithBid(Uri.fromParts("adsScheme", "ssp1", null), "{\"metadata\":1}", 1.0);

    public static final AdWithBid ADS_WITH_BID_2 =
            createAdsWithBid(Uri.fromParts("adsScheme", "ssp2", null), "{\"metadata\":2}", 2.0);

    public static final AdWithBid ADS_WITH_BID_3 =
            createAdsWithBid(Uri.fromParts("adsScheme", "ssp3", null), "{\"metadata\":3}", 3.0);

    public static final List<AdWithBid> CONTEXTUAL_ADS =
            Arrays.asList(ADS_WITH_BID_1, ADS_WITH_BID_2, ADS_WITH_BID_3);

    public static final Uri TRUSTED_SCORING_SIGNALS_URI =
            Uri.parse("https://www.kv-server.example");

    private static AdWithBid createAdsWithBid(Uri renderUri, String metaData, double bid) {
        AdData asData = new AdData(renderUri, metaData);

        return new AdWithBid(asData, bid);
    }

    /** Creates an AdSelectionConfig object to be used in unit and integration tests */
    public static AdSelectionConfig anAdSelectionConfig() {
        return anAdSelectionConfigBuilder().build();
    }

    /**
     * @return returns a pre-loaded builder, where the internal members of the object can be changed
     * for the unit tests
     */
    public static AdSelectionConfig.Builder anAdSelectionConfigBuilder() {
        return new AdSelectionConfig.Builder()
                .setSeller(SELLER)
                .setDecisionLogicUri(DECISION_LOGIC_URI)
                .setCustomAudienceBuyers(CUSTOM_AUDIENCE_BUYERS)
                .setAdSelectionSignals(AD_SELECTION_SIGNALS)
                .setSellerSignals(SELLER_SIGNALS)
                .setPerBuyerSignals(PER_BUYER_SIGNALS)
                .setContextualAds(CONTEXTUAL_ADS)
                .setTrustedScoringSignalsUri(TRUSTED_SCORING_SIGNALS_URI);
    }

    /**
     * Creates an AdSelectionConfig object to be used in unit and integration tests Accepts a Uri
     * decisionLogicUri to be used instead of the default
     */
    public static AdSelectionConfig anAdSelectionConfig(@NonNull Uri decisionLogicUri) {
        return new AdSelectionConfig.Builder()
                .setSeller(SELLER)
                .setDecisionLogicUri(decisionLogicUri)
                .setCustomAudienceBuyers(CUSTOM_AUDIENCE_BUYERS)
                .setAdSelectionSignals(AD_SELECTION_SIGNALS)
                .setSellerSignals(SELLER_SIGNALS)
                .setPerBuyerSignals(PER_BUYER_SIGNALS)
                .setContextualAds(CONTEXTUAL_ADS)
                .setTrustedScoringSignalsUri(TRUSTED_SCORING_SIGNALS_URI)
                .build();
    }
}

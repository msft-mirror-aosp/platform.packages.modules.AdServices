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

import java.util.Arrays;
import java.util.List;
import java.util.Map;

/** This is a static class meant to help with tests that involve creating an AdSelectionConfig.
 *
 * @hide
 */
public class AdSelectionConfigFixture {

    public static final String SELLER = "testSeller";

    // Uri Constants
    public static final String SCHEME = "testScheme";
    public static final String SSP = "testSSP";
    public static final String FRAGMENT = "testFragment";

    public static final Uri DECISION_LOGIC_URL = Uri.fromParts(SCHEME, SSP, FRAGMENT);

    public static final List<String> CUSTOM_AUDIENCE_BUYERS =
            Arrays.asList("buyer1", "buyer2", "buyer3");

    public static final String AD_SELECTION_SIGNALS = "testAdSelectionSignals";

    public static final String SELLER_SIGNALS = "testSellerSignals";

    public static final Map<String, String> PER_BUYER_SIGNALS =
            Map.of("key1", "value1", "key2", "value2", "key3", "value3");

    // Contextual Ads Components
    public static final AdWithBid ADS_WITH_BID_1 =
            createAdsWithBid(Uri.fromParts("adsScheme", "ssp", null), "metaData1", 1.0);

    public static final AdWithBid ADS_WITH_BID_2 =
            createAdsWithBid(Uri.fromParts("adsScheme", "ssp2", null), "metaData2", 2.0);

    public static final AdWithBid ADS_WITH_BID_3 =
            createAdsWithBid(Uri.fromParts("adsScheme", "ssp3", null), "metaData2", 3.0);

    public static final List<AdWithBid> CONTEXTUAL_ADS =
            Arrays.asList(ADS_WITH_BID_1, ADS_WITH_BID_2, ADS_WITH_BID_3);

    private static AdWithBid createAdsWithBid(Uri renderUrl, String metaData, double bid) {
        AdData asData = new AdData(renderUrl, metaData);
        return new AdWithBid(asData, bid);
    }

    /** Creates an AdSelectionConfig object to be used in unit and integration tests */
    public static AdSelectionConfig anAdSelectionConfig() {
        return new AdSelectionConfig.Builder()
                .setSeller(SELLER)
                .setDecisionLogicUrl(DECISION_LOGIC_URL)
                .setCustomAudienceBuyers(CUSTOM_AUDIENCE_BUYERS)
                .setAdSelectionSignals(AD_SELECTION_SIGNALS)
                .setSellerSignals(SELLER_SIGNALS)
                .setPerBuyerSignals(PER_BUYER_SIGNALS)
                .setContextualAds(CONTEXTUAL_ADS)
                .build();
    }
}

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

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertThrows;
import static org.junit.Assert.assertTrue;

import android.adservices.common.AdTechIdentifier;
import android.os.Parcel;

import org.junit.Test;

import java.util.stream.Collectors;

public class AdSelectionConfigTest {
    @Test
    public void testBuildValidAdSelectionConfigSuccess() {
        AdSelectionConfig config =
                new AdSelectionConfig.Builder()
                        .setSeller(AdSelectionConfigFixture.SELLER.getStringForm())
                        .setDecisionLogicUri(AdSelectionConfigFixture.DECISION_LOGIC_URI)
                        .setCustomAudienceBuyers(
                                AdSelectionConfigFixture.CUSTOM_AUDIENCE_BUYERS.stream()
                                        .map(AdTechIdentifier::getStringForm)
                                        .collect(Collectors.toList()))
                        .setAdSelectionSignals(
                                AdSelectionConfigFixture.AD_SELECTION_SIGNALS.getStringForm())
                        .setSellerSignals(AdSelectionConfigFixture.SELLER_SIGNALS.getStringForm())
                        .setPerBuyerSignals(
                                AdSelectionConfigFixture.PER_BUYER_SIGNALS.entrySet().stream()
                                        .collect(
                                                Collectors.toMap(
                                                        e -> e.getKey().getStringForm(),
                                                        e -> e.getValue().getStringForm())))
                        .setContextualAds(AdSelectionConfigFixture.CONTEXTUAL_ADS)
                        .setTrustedScoringSignalsUri(
                                AdSelectionConfigFixture.TRUSTED_SCORING_SIGNALS_URI)
                        .build();

        assertEquals(config.getSeller(), AdSelectionConfigFixture.SELLER.getStringForm());
        assertEquals(config.getDecisionLogicUri(), AdSelectionConfigFixture.DECISION_LOGIC_URI);
        assertEquals(
                config.getCustomAudienceBuyers(),
                AdSelectionConfigFixture.CUSTOM_AUDIENCE_BUYERS.stream()
                        .map(AdTechIdentifier::getStringForm)
                        .collect(Collectors.toList()));
        assertEquals(
                config.getAdSelectionSignals(),
                AdSelectionConfigFixture.AD_SELECTION_SIGNALS.getStringForm());
        assertEquals(
                config.getSellerSignals(), AdSelectionConfigFixture.SELLER_SIGNALS.getStringForm());
        assertEquals(
                config.getPerBuyerSignals(),
                AdSelectionConfigFixture.PER_BUYER_SIGNALS.entrySet().stream()
                        .collect(
                                Collectors.toMap(
                                        e -> e.getKey().getStringForm(),
                                        e -> e.getValue().getStringForm())));
        assertEquals(config.getContextualAds(), AdSelectionConfigFixture.CONTEXTUAL_ADS);
        assertEquals(
                config.getTrustedScoringSignalsUri(),
                AdSelectionConfigFixture.TRUSTED_SCORING_SIGNALS_URI);
    }

    @Test
    public void testParcelValidAdDataSuccess() {
        AdSelectionConfig config = AdSelectionConfigFixture.anAdSelectionConfig();

        Parcel p = Parcel.obtain();
        config.writeToParcel(p, 0);
        p.setDataPosition(0);
        AdSelectionConfig fromParcel = AdSelectionConfig.CREATOR.createFromParcel(p);

        assertEquals(config.getSeller(), fromParcel.getSeller());
        assertEquals(config.getDecisionLogicUri(), fromParcel.getDecisionLogicUri());
        assertEquals(config.getCustomAudienceBuyers(), fromParcel.getCustomAudienceBuyers());
        assertEquals(config.getAdSelectionSignals(), fromParcel.getAdSelectionSignals());
        assertEquals(config.getSellerSignals(), fromParcel.getSellerSignals());
        assertEquals(config.getPerBuyerSignals(), fromParcel.getPerBuyerSignals());
        assertEquals(config.getContextualAds(), fromParcel.getContextualAds());
    }

    @Test
    public void testBuildMinimalAdSelectionConfigWithDefaultsSuccess() {
        AdSelectionConfig config =
                new AdSelectionConfig.Builder()
                        .setSeller(AdSelectionConfigFixture.SELLER.getStringForm())
                        .setDecisionLogicUri(AdSelectionConfigFixture.DECISION_LOGIC_URI)
                        .setCustomAudienceBuyers(
                                AdSelectionConfigFixture.CUSTOM_AUDIENCE_BUYERS.stream()
                                        .map(AdTechIdentifier::getStringForm)
                                        .collect(Collectors.toList()))
                        .setTrustedScoringSignalsUri(
                                AdSelectionConfigFixture.TRUSTED_SCORING_SIGNALS_URI)
                        .build();

        assertEquals(config.getSeller(), AdSelectionConfigFixture.SELLER.getStringForm());
        assertEquals(config.getDecisionLogicUri(), AdSelectionConfigFixture.DECISION_LOGIC_URI);
        assertEquals(
                config.getCustomAudienceBuyers(),
                AdSelectionConfigFixture.CUSTOM_AUDIENCE_BUYERS.stream()
                        .map(AdTechIdentifier::getStringForm)
                        .collect(Collectors.toList()));
        assertEquals(
                config.getTrustedScoringSignalsUri(),
                AdSelectionConfigFixture.TRUSTED_SCORING_SIGNALS_URI);

        // Populated by default with empty signals, map, and list
        assertEquals(
                config.getAdSelectionSignals(),
                AdSelectionConfigFixture.EMPTY_SIGNALS.getStringForm());
        assertEquals(
                config.getSellerSignals(), AdSelectionConfigFixture.EMPTY_SIGNALS.getStringForm());
        assertTrue(config.getPerBuyerSignals().isEmpty());
        assertTrue(config.getContextualAds().isEmpty());
    }

    @Test
    public void testBuildAdSelectionConfigUnsetSellerFailure() {
        assertThrows(
                NullPointerException.class,
                () -> {
                    new AdSelectionConfig.Builder()
                            .setDecisionLogicUri(AdSelectionConfigFixture.DECISION_LOGIC_URI)
                            .setCustomAudienceBuyers(
                                    AdSelectionConfigFixture.CUSTOM_AUDIENCE_BUYERS.stream()
                                            .map(AdTechIdentifier::getStringForm)
                                            .collect(Collectors.toList()))
                            .build();
                });
    }

    @Test
    public void testBuildAdSelectionConfigUnsetDecisionLogicUrlFailure() {
        assertThrows(
                NullPointerException.class,
                () -> {
                    new AdSelectionConfig.Builder()
                            .setSeller(AdSelectionConfigFixture.SELLER.getStringForm())
                            .setCustomAudienceBuyers(
                                    AdSelectionConfigFixture.CUSTOM_AUDIENCE_BUYERS.stream()
                                            .map(AdTechIdentifier::getStringForm)
                                            .collect(Collectors.toList()))
                            .build();
                });
    }

    @Test
    public void testBuildAdSelectionConfigUnsetBuyersFailure() {
        assertThrows(
                NullPointerException.class,
                () -> {
                    new AdSelectionConfig.Builder()
                            .setSeller(AdSelectionConfigFixture.SELLER.getStringForm())
                            .setDecisionLogicUri(AdSelectionConfigFixture.DECISION_LOGIC_URI)
                            .build();
                });
    }
}

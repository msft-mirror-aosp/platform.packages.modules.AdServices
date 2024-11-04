/*
 * Copyright (C) 2024 The Android Open Source Project
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

package android.adservices.cts;


import android.adservices.adselection.AdSelectionConfig;
import android.adservices.adselection.AdSelectionConfigFixture;
import android.adservices.adselection.AddAdSelectionOverrideRequest;
import android.adservices.adselection.DecisionLogic;
import android.adservices.adselection.PerBuyerDecisionLogic;
import android.adservices.common.AdSelectionSignals;
import android.adservices.common.CommonFixture;

import com.google.common.collect.ImmutableMap;

import org.junit.Test;

/** Adds tests for {@link AddAdSelectionOverrideRequest}. */
public final class AddAdSelectionOverrideRequestTest extends CtsAdServicesDeviceTestCase {
    private static final AdSelectionConfig AD_SELECTION_CONFIG =
            AdSelectionConfigFixture.anAdSelectionConfig();
    private static final String DECISION_LOGIC_JS = "function test() { return \"hello world\"; }";
    private static final AdSelectionSignals TRUSTED_SCORING_SIGNALS =
            AdSelectionSignals.fromString(
                    "{\n"
                            + "\t\"render_uri_1\": \"signals_for_1\",\n"
                            + "\t\"render_uri_2\": \"signals_for_2\"\n"
                            + "}");
    private static final DecisionLogic DECISION_LOGIC = new DecisionLogic(DECISION_LOGIC_JS);
    private static final PerBuyerDecisionLogic BUYERS_DECISION_LOGIC =
            new PerBuyerDecisionLogic(
                    ImmutableMap.of(
                            CommonFixture.VALID_BUYER_1,
                            DECISION_LOGIC,
                            CommonFixture.VALID_BUYER_2,
                            DECISION_LOGIC));

    @Test
    public void testBuildsAddAdSelectionOverrideRequest() {
        AddAdSelectionOverrideRequest request =
                new AddAdSelectionOverrideRequest(
                        AD_SELECTION_CONFIG,
                        DECISION_LOGIC_JS,
                        TRUSTED_SCORING_SIGNALS,
                        BUYERS_DECISION_LOGIC);

        expect.that(request.getDecisionLogicJs()).isEqualTo(DECISION_LOGIC_JS);
        expect.that(request.getAdSelectionConfig()).isEqualTo(AD_SELECTION_CONFIG);
        expect.that(request.getTrustedScoringSignals()).isEqualTo(TRUSTED_SCORING_SIGNALS);
        expect.that(request.getPerBuyerDecisionLogic()).isEqualTo(BUYERS_DECISION_LOGIC);
    }
}

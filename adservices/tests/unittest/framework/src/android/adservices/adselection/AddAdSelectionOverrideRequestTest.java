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

import static com.google.common.truth.Truth.assertThat;

import android.adservices.common.AdSelectionSignals;
import android.adservices.common.CommonFixture;

import org.junit.Test;

import java.util.List;

public class AddAdSelectionOverrideRequestTest {
    private static final AdSelectionConfig AD_SELECTION_CONFIG =
            AdSelectionConfigFixture.anAdSelectionConfig();
    private static final String DECISION_LOGIC_JS = "function test() { return \"hello world\"; }";
    private static final AdSelectionSignals TRUSTED_SCORING_SIGNALS =
            AdSelectionSignals.fromString(
                    "{\n"
                            + "\t\"render_uri_1\": \"signals_for_1\",\n"
                            + "\t\"render_uri_2\": \"signals_for_2\"\n"
                            + "}");
    private static final List<BuyerDecisionLogic> BUYERS_DECISION_LOGIC =
            List.of(
                    new BuyerDecisionLogic(CommonFixture.VALID_BUYER_1, "reportWin()"),
                    new BuyerDecisionLogic(CommonFixture.VALID_BUYER_2, "reportWin()"));

    @Test
    public void testBuildsAddAdSelectionOverrideRequest() throws Exception {
        AddAdSelectionOverrideRequest request =
                new AddAdSelectionOverrideRequest(
                        AD_SELECTION_CONFIG,
                        DECISION_LOGIC_JS,
                        TRUSTED_SCORING_SIGNALS,
                        BUYERS_DECISION_LOGIC);

        assertThat(request.getDecisionLogicJs()).isEqualTo(DECISION_LOGIC_JS);
        assertThat(request.getAdSelectionConfig()).isEqualTo(AD_SELECTION_CONFIG);
        assertThat(request.getTrustedScoringSignals()).isEqualTo(TRUSTED_SCORING_SIGNALS);
        assertThat(request.getBuyersDecisionLogic()).isEqualTo(BUYERS_DECISION_LOGIC);
    }
}

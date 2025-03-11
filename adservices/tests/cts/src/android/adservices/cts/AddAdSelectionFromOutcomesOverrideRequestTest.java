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


import android.adservices.adselection.AdSelectionFromOutcomesConfig;
import android.adservices.adselection.AdSelectionFromOutcomesConfigFixture;
import android.adservices.adselection.AddAdSelectionFromOutcomesOverrideRequest;
import android.adservices.common.AdSelectionSignals;

import org.junit.Test;

/** Adds tests for {@link AddAdSelectionFromOutcomesOverrideRequest}. */
public final class AddAdSelectionFromOutcomesOverrideRequestTest
        extends CtsAdServicesDeviceTestCase {
    private static final AdSelectionFromOutcomesConfig AD_SELECTION_FROM_OUTCOMES_CONFIG =
            AdSelectionFromOutcomesConfigFixture.anAdSelectionFromOutcomesConfig();
    private static final String SELECTION_LOGIC_JS = "function test() { return \"hello world\"; }";
    private static final AdSelectionSignals SELECTION_SIGNALS = AdSelectionSignals.EMPTY;

    @Test
    public void testBuildsAddAdSelectionOverrideRequest() {
        AddAdSelectionFromOutcomesOverrideRequest request =
                new AddAdSelectionFromOutcomesOverrideRequest(
                        AD_SELECTION_FROM_OUTCOMES_CONFIG, SELECTION_LOGIC_JS, SELECTION_SIGNALS);

        expect.that(request.getOutcomeSelectionLogicJs()).isEqualTo(SELECTION_LOGIC_JS);
        expect.that(request.getAdSelectionFromOutcomesConfig())
                .isEqualTo(AD_SELECTION_FROM_OUTCOMES_CONFIG);
        expect.that(request.getOutcomeSelectionTrustedSignals()).isEqualTo(SELECTION_SIGNALS);
    }
}

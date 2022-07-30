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

package android.adservices.customaudience;

import static org.junit.Assert.assertEquals;

import android.adservices.common.AdSelectionSignals;
import android.adservices.common.AdTechIdentifier;

import org.junit.Test;

public class AddCustomAudienceOverrideRequestTest {
    private static final String OWNER = "owner";
    private static final AdTechIdentifier BUYER = AdTechIdentifier.fromString("buyer");
    private static final String NAME = "name";
    private static final String BIDDING_LOGIC_JS = "function test() { return \"hello world\"; }";
    private static final AdSelectionSignals TRUSTED_BIDDING_DATA =
            AdSelectionSignals.fromString("{\"trusted_bidding_data\":1}");

    @Test
    public void testBuildAddCustomAudienceOverrideRequest() {
        AddCustomAudienceOverrideRequest request =
                new AddCustomAudienceOverrideRequest.Builder()
                        .setOwner(OWNER)
                        .setBuyer(BUYER.getStringForm())
                        .setName(NAME)
                        .setBiddingLogicJs(BIDDING_LOGIC_JS)
                        .setTrustedBiddingData(TRUSTED_BIDDING_DATA.getStringForm())
                        .build();

        assertEquals(request.getOwner(), OWNER);
        assertEquals(request.getBuyer(), BUYER.getStringForm());
        assertEquals(request.getName(), NAME);
        assertEquals(request.getBiddingLogicJs(), BIDDING_LOGIC_JS);
        assertEquals(request.getTrustedBiddingData(), TRUSTED_BIDDING_DATA.getStringForm());
    }
}

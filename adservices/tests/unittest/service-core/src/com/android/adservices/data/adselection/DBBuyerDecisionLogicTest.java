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

package com.android.adservices.data.adselection;

import static org.junit.Assert.assertEquals;

import android.net.Uri;

import org.junit.Test;

public class DBBuyerDecisionLogicTest {

    private static final String BUYER_DECISION_LOGIC_JS =
            "function test() { return \"hello world\"; }";
    private static final Uri BIDDING_LOGIC_URL = Uri.parse("http://www.domain.com/logic/1");

    @Test
    public void testBuildDBBuyerDecisionLogic() {
        DBBuyerDecisionLogic dbBuyerDecisionLogic =
                new DBBuyerDecisionLogic.Builder()
                        .setBiddingLogicUrl(BIDDING_LOGIC_URL)
                        .setBuyerDecisionLogicJs(BUYER_DECISION_LOGIC_JS)
                        .build();

        assertEquals(dbBuyerDecisionLogic.getBiddingLogicUrl(), BIDDING_LOGIC_URL);
        assertEquals(dbBuyerDecisionLogic.getBuyerDecisionLogicJs(), BUYER_DECISION_LOGIC_JS);
    }
}

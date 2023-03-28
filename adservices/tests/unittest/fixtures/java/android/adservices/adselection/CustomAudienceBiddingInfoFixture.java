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

import android.net.Uri;

public class CustomAudienceBiddingInfoFixture {

    public static final Uri VALID_BIDDING_LOGIC_URI =
            new Uri.Builder().path("https://valid-buyer.example.com/bidding/logic/here/").build();

    public static final String BUYER_DECISION_LOGIC_JS =
            "function runBidding(ad_selection_signals, per_buyer_signals, signals_for_buyer,"
                    + ") {;\n"
                    + "}";
}

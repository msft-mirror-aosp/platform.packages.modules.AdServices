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

import android.adservices.common.AdTechIdentifier;
import android.adservices.common.CommonFixture;
import android.net.Uri;

import java.util.ArrayList;
import java.util.Arrays;

/** Utility class supporting custom audience API unit tests */
public class TrustedBiddingDataFixture {
    public static final String VALID_TRUSTED_BIDDING_URI_PATH = "/trusted/bidding/";

    public static final ArrayList<String> VALID_TRUSTED_BIDDING_KEYS = new ArrayList<String>(
            Arrays.asList("example", "valid", "list", "of", "keys"));

    public static Uri getValidTrustedBiddingUriByBuyer(AdTechIdentifier buyer) {
        return CommonFixture.getUri(buyer, VALID_TRUSTED_BIDDING_URI_PATH);
    }

    public static TrustedBiddingData getValidTrustedBiddingDataByBuyer(AdTechIdentifier buyer) {
        return new TrustedBiddingData.Builder()
                .setTrustedBiddingKeys(VALID_TRUSTED_BIDDING_KEYS)
                .setTrustedBiddingUri(getValidTrustedBiddingUriByBuyer(buyer))
                .build();
    }
}

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

import android.net.Uri;

import java.util.ArrayList;
import java.util.Arrays;

/** Utility class supporting custom audience API unit tests */
public class TrustedBiddingDataFixture {

    public static final Uri VALID_TRUSTED_BIDDING_URL =
            new Uri.Builder().path("valid.example.com/testing/hello").build();
    public static final ArrayList<String> VALID_TRUSTED_BIDDING_KEYS = new ArrayList<String>(
            Arrays.asList("example", "valid", "list", "of", "keys"));

    public static final TrustedBiddingData VALID_TRUSTED_BIDDING_DATA =
            new TrustedBiddingData.Builder()
                    .setTrustedBiddingUrl(VALID_TRUSTED_BIDDING_URL)
                    .setTrustedBiddingKeys(VALID_TRUSTED_BIDDING_KEYS)
                    .build();
}

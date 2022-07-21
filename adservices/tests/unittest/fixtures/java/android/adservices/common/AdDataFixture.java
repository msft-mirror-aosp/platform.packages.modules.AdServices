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

package android.adservices.common;

import android.net.Uri;

import com.google.common.collect.ImmutableList;

import java.util.List;

/** Utility class supporting ad services API unit tests */
public class AdDataFixture {
    private static final String VALID_METADATA = "{'example': 'metadata', 'valid': true}";

    public static Uri getValidRenderUrlByBuyer(String buyer, int sequence) {
        return CommonFixture.getUri(buyer, "/testing/hello" + sequence);
    }

    public static List<AdData> getValidAdsByBuyer(String buyer) {
        return ImmutableList.of(
                new AdData.Builder()
                        .setRenderUri(getValidRenderUrlByBuyer(buyer, 1))
                        .setMetadata(VALID_METADATA)
                        .build(),
                new AdData.Builder()
                        .setRenderUri(getValidRenderUrlByBuyer(buyer, 2))
                        .setMetadata(VALID_METADATA)
                        .build(),
                new AdData.Builder()
                        .setRenderUri(getValidRenderUrlByBuyer(buyer, 3))
                        .setMetadata(VALID_METADATA)
                        .build(),
                new AdData.Builder()
                        .setRenderUri(getValidRenderUrlByBuyer(buyer, 4))
                        .setMetadata(VALID_METADATA)
                        .build());
    }
}

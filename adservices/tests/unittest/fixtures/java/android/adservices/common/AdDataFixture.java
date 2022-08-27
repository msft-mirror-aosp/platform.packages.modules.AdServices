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
    private static final String INVALID_METADATA = "not.{real!metadata} = 1";

    public static Uri getValidRenderUrlByBuyer(AdTechIdentifier buyer, int sequence) {
        return CommonFixture.getUri(buyer, "/testing/hello" + sequence);
    }

    public static List<AdData> getValidAdsByBuyer(AdTechIdentifier buyer) {
        return ImmutableList.of(
                getValidAdDataByBuyer(buyer, 1),
                getValidAdDataByBuyer(buyer, 2),
                getValidAdDataByBuyer(buyer, 3),
                getValidAdDataByBuyer(buyer, 4));
    }

    public static List<AdData> getInvalidAdsByBuyer(AdTechIdentifier buyer) {
        return ImmutableList.of(
                new AdData.Builder()
                        .setRenderUri(getValidRenderUrlByBuyer(buyer, 1))
                        .setMetadata(INVALID_METADATA)
                        .build(),
                new AdData.Builder()
                        .setRenderUri(getValidRenderUrlByBuyer(buyer, 2))
                        .setMetadata(INVALID_METADATA)
                        .build(),
                new AdData.Builder()
                        .setRenderUri(getValidRenderUrlByBuyer(buyer, 3))
                        .setMetadata(INVALID_METADATA)
                        .build(),
                new AdData.Builder()
                        .setRenderUri(getValidRenderUrlByBuyer(buyer, 4))
                        .setMetadata(INVALID_METADATA)
                        .build());
    }

    public static AdData getValidAdDataByBuyer(AdTechIdentifier buyer, int sequenceNumber) {
        return new AdData.Builder()
                .setRenderUri(getValidRenderUrlByBuyer(buyer, sequenceNumber))
                .setMetadata(VALID_METADATA)
                .build();
    }
}

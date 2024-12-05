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

package android.adservices.common;

import android.net.Uri;

import com.google.common.collect.ImmutableList;

import java.util.List;

/** Utility class supporting ad services API unit tests */
public final class ComponentAdDataFixture {
    private ComponentAdDataFixture() {}

    /**
     * @return a valid render uri for a specified buyer and sequence number
     */
    public static Uri getValidRenderUriByBuyer(AdTechIdentifier buyer, int sequence) {
        return CommonFixture.getUri(buyer, "/testing/hello" + sequence);
    }

    /**
     * @return a valid list of component ads for a specified buyer.
     */
    public static List<ComponentAdData> getValidComponentAdsByBuyer(AdTechIdentifier buyer) {
        return ImmutableList.of(
                getValidComponentAdDataByBuyer(buyer, 1),
                getValidComponentAdDataByBuyer(buyer, 2),
                getValidComponentAdDataByBuyer(buyer, 3),
                getValidComponentAdDataByBuyer(buyer, 4));
    }

    /**
     * @return a component ad for a specified buyer.
     */
    public static ComponentAdData getValidComponentAdDataByBuyer(
            AdTechIdentifier buyer, int sequenceNumber) {
        return new ComponentAdData(
                getValidRenderUriByBuyer(buyer, sequenceNumber), AdDataFixture.VALID_RENDER_ID);
    }
}

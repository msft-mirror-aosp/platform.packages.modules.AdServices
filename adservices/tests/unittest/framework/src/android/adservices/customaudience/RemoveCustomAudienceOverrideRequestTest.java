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

import com.android.adservices.common.AdServicesUnitTestCase;
import com.android.adservices.shared.testing.annotations.RequiresSdkLevelAtLeastS;

import org.junit.Test;

@RequiresSdkLevelAtLeastS
public final class RemoveCustomAudienceOverrideRequestTest extends AdServicesUnitTestCase {
    private static final AdTechIdentifier BUYER = AdTechIdentifier.fromString("buyer");
    private static final String NAME = "name";

    @Test
    public void testBuildAddCustomAudienceOverrideRequest() {
        RemoveCustomAudienceOverrideRequest request =
                new RemoveCustomAudienceOverrideRequest.Builder()
                        .setBuyer(BUYER)
                        .setName(NAME)
                        .build();

        expect.that(request.getBuyer()).isEqualTo(BUYER);
        expect.that(request.getName()).isEqualTo(NAME);
    }
}

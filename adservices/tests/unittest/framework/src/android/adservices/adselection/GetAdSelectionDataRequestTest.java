/*
 * Copyright (C) 2023 The Android Open Source Project
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

import android.adservices.common.AdTechIdentifier;
import android.net.Uri;

import com.android.adservices.common.AdServicesUnitTestCase;

import org.junit.Test;

public final class GetAdSelectionDataRequestTest extends AdServicesUnitTestCase {
    private static final AdTechIdentifier SELLER = AdSelectionConfigFixture.SELLER;

    @Test
    public void testGetAdSelectionDataRequest_validInputWithoutUri_success() {
        GetAdSelectionDataRequest request =
                new GetAdSelectionDataRequest.Builder().setSeller(SELLER).build();

        expect.that(request.getSeller()).isEqualTo(SELLER);
        expect.that(request.getCoordinatorOriginUri()).isNull();
    }

    @Test
    public void testGetAdSelectionDataRequest_validInputWithUri_success() {
        Uri expectedUri = Uri.parse("https://example.com");
        GetAdSelectionDataRequest request =
                new GetAdSelectionDataRequest.Builder()
                        .setSeller(SELLER)
                        .setCoordinatorOriginUri(expectedUri)
                        .build();

        expect.that(request.getSeller()).isEqualTo(SELLER);
        expect.that(request.getCoordinatorOriginUri()).isEqualTo(expectedUri);
    }
}

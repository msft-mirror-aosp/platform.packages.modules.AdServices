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

package android.adservices.cts;

import android.adservices.adselection.AdSelectionConfigFixture;
import android.adservices.adselection.GetAdSelectionDataRequest;
import android.adservices.adselection.SellerConfigurationFixture;
import android.adservices.common.AdTechIdentifier;
import android.net.Uri;

import org.junit.Test;

public final class GetAdSelectionDataRequestTest extends CtsAdServicesDeviceTestCase {

    private static final AdTechIdentifier SELLER = AdSelectionConfigFixture.SELLER;
    private static final Uri COORDINATOR_URI = Uri.parse("http://foo.bar/gcp");

    @Test
    public void getAdSelectionDataRequest_withNonNullInputs_success() {
        GetAdSelectionDataRequest request =
                new GetAdSelectionDataRequest.Builder()
                        .setSeller(SELLER)
                        .setCoordinatorOriginUri(COORDINATOR_URI)
                        .build();

        expect.that(request.getSeller()).isEqualTo(SELLER);
        expect.that(request.getCoordinatorOriginUri()).isEqualTo(COORDINATOR_URI);
    }

    @Test
    public void getAdSelectionDataRequest_nullCoordinator_success() {
        GetAdSelectionDataRequest request =
                new GetAdSelectionDataRequest.Builder().setSeller(SELLER).build();

        expect.that(request.getSeller()).isEqualTo(SELLER);
        expect.that(request.getCoordinatorOriginUri()).isNull();
    }

    @Test
    public void getAdSelectionDataRequest_nullSeller_success() {
        GetAdSelectionDataRequest request =
                new GetAdSelectionDataRequest.Builder()
                        .setCoordinatorOriginUri(COORDINATOR_URI)
                        .build();

        expect.that(request.getSeller()).isNull();
        expect.that(request.getCoordinatorOriginUri()).isEqualTo(COORDINATOR_URI);
    }

    @Test
    public void testGetAdSelectionDataRequest_sellerConfiguration_success() {
        GetAdSelectionDataRequest request =
                new GetAdSelectionDataRequest.Builder()
                        .setSeller(SELLER)
                        .setSellerConfiguration(SellerConfigurationFixture.SELLER_CONFIGURATION)
                        .build();

        expect.that(request.getSeller()).isEqualTo(SELLER);
        expect.that(request.getSellerConfiguration())
                .isEqualTo(SellerConfigurationFixture.SELLER_CONFIGURATION);
    }

    @Test
    public void testGetAdSelectionDataRequest_withoutSellerConfiguration_success() {
        GetAdSelectionDataRequest request =
                new GetAdSelectionDataRequest.Builder().setSeller(SELLER).build();

        expect.that(request.getSeller()).isEqualTo(SELLER);
        expect.that(request.getSellerConfiguration()).isNull();
    }
}

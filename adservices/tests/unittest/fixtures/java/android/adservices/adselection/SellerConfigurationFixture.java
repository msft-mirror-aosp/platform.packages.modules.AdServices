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

package android.adservices.adselection;

import android.adservices.common.AdTechIdentifier;
import android.adservices.common.CommonFixture;

import com.google.common.collect.ImmutableSet;

public class SellerConfigurationFixture {

    public static final AdTechIdentifier BUYER_1 = CommonFixture.VALID_BUYER_1;
    public static final AdTechIdentifier BUYER_2 = CommonFixture.VALID_BUYER_2;

    public static final Integer BUYER_1_TARGET_SIZE_B = 1000;
    public static final Integer BUYER_2_TARGET_SIZE_B = 500;

    public static final Integer SELLER_TARGET_SIZE_B = 2000;

    public static final PerBuyerConfiguration PER_BUYER_CONFIGURATION_1 =
            new PerBuyerConfiguration.Builder()
                    .setBuyer(BUYER_1)
                    .setTargetInputSizeBytes(BUYER_1_TARGET_SIZE_B)
                    .build();

    public static final PerBuyerConfiguration PER_BUYER_CONFIGURATION_2 =
            new PerBuyerConfiguration.Builder()
                    .setBuyer(BUYER_2)
                    .setTargetInputSizeBytes(BUYER_2_TARGET_SIZE_B)
                    .build();

    public static final SellerConfiguration SELLER_CONFIGURATION =
            new SellerConfiguration.Builder()
                    .setMaximumPayloadSizeBytes(SELLER_TARGET_SIZE_B)
                    .setPerBuyerConfigurations(
                            ImmutableSet.of(PER_BUYER_CONFIGURATION_1, PER_BUYER_CONFIGURATION_2))
                    .build();
}

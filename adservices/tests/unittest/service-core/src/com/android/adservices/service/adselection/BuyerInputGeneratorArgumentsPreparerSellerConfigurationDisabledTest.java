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

package com.android.adservices.service.adselection;

import static android.adservices.adselection.SellerConfigurationFixture.SELLER_CONFIGURATION;

import com.android.adservices.common.AdServicesMockitoTestCase;

import org.junit.Test;

public class BuyerInputGeneratorArgumentsPreparerSellerConfigurationDisabledTest
        extends AdServicesMockitoTestCase {

    @Test
    public void testPrepareSellerConfigurationReturnsDisabledContext() {
        BuyerInputGeneratorArgumentsPreparer argumentsPreparer =
                new BuyerInputGeneratorArgumentsPreparerSellerConfigurationDisabled();

        PayloadOptimizationContext context =
                argumentsPreparer.preparePayloadOptimizationContext(SELLER_CONFIGURATION, 10);

        expect.that(context.getOptimizationsEnabled()).isFalse();
        expect.that(context.getMaxBuyerInputSizeBytes()).isEqualTo(0);
        expect.that(context.getPerBuyerConfigurations()).isEmpty();
    }
}

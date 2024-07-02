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

import android.adservices.adselection.SellerConfiguration;

/**
 * Interface to prepare arguments for buyer input generator's {@code createCompressedBuyerInputs}
 * method.
 */
public interface BuyerInputGeneratorArgumentsPreparer {
    /**
     * Creates a {@link PayloadOptimizationContext} based on the seller's maximum size and the
     * current payload size.
     */
    PayloadOptimizationContext preparePayloadOptimizationContext(
            SellerConfiguration sellerConfiguration, int currentPayloadSizeBytes);
}

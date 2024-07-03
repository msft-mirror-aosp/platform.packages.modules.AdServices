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

import static com.android.adservices.service.adselection.AuctionServerPayloadFormattingUtil.DATA_SIZE_PADDING_LENGTH_BYTE;
import static com.android.adservices.service.adselection.AuctionServerPayloadFormattingUtil.META_INFO_LENGTH_BYTE;

import android.adservices.adselection.SellerConfiguration;
import android.annotation.Nullable;

import java.util.Objects;

/** Actual implementation of {@link BuyerInputGeneratorArgumentsPreparer} */
public class BuyerInputGeneratorArgumentsPreparerSellerConfigurationEnabled
        implements BuyerInputGeneratorArgumentsPreparer {
    @Override
    public PayloadOptimizationContext preparePayloadOptimizationContext(
            @Nullable SellerConfiguration sellerConfiguration, int currentPayloadSizeBytes) {
        if (Objects.isNull(sellerConfiguration)) {
            return PayloadOptimizationContext.builder().build();
        }
        int leftoverSize =
                getLeftoverMaxPayloadSizeBytes(
                        sellerConfiguration.getMaximumPayloadSizeBytes(), currentPayloadSizeBytes);
        if (leftoverSize <= 0) {
            leftoverSize = 0;
        }
        return PayloadOptimizationContext.builder()
                .setOptimizationsEnabled(true)
                .setMaxBuyerInputSizeBytes(leftoverSize)
                .setPerBuyerConfigurations(sellerConfiguration.getPerBuyerConfigurations())
                .build();
    }

    private int getLeftoverMaxPayloadSizeBytes(
            int sellerMaxSizeBytes, int currentPayloadSizeBytes) {
        currentPayloadSizeBytes += META_INFO_LENGTH_BYTE + DATA_SIZE_PADDING_LENGTH_BYTE;
        return sellerMaxSizeBytes - currentPayloadSizeBytes;
    }
}

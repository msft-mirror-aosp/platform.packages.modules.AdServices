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

import static android.adservices.adselection.SellerConfigurationFixture.PER_BUYER_CONFIGURATION_1;
import static android.adservices.adselection.SellerConfigurationFixture.PER_BUYER_CONFIGURATION_2;
import static android.adservices.adselection.SellerConfigurationFixture.SELLER_CONFIGURATION;

import static com.android.adservices.service.adselection.AuctionServerPayloadFormattingUtil.DATA_SIZE_PADDING_LENGTH_BYTE;
import static com.android.adservices.service.adselection.AuctionServerPayloadFormattingUtil.META_INFO_LENGTH_BYTE;

import android.adservices.adselection.SellerConfiguration;

import com.android.adservices.common.AdServicesUnitTestCase;

import org.junit.Before;
import org.junit.Test;

import java.util.Set;

public class BuyerInputGeneratorArgumentsPreparerSellerConfigurationEnabledTest
        extends AdServicesUnitTestCase {

    private static final int CURRENT_PAYLOAD_SIZE_BYTES = 10;

    BuyerInputGeneratorArgumentsPreparer mArgumentsPreparer;

    @Before
    public void setup() {
        mArgumentsPreparer = new BuyerInputGeneratorArgumentsPreparerSellerConfigurationEnabled();
    }

    @Test
    public void testPrepareSellerConfiguration_WithNullSellerConfiguration() {
        PayloadOptimizationContext context =
                mArgumentsPreparer.preparePayloadOptimizationContext(
                        null, CURRENT_PAYLOAD_SIZE_BYTES);

        expect.that(context.getOptimizationsEnabled()).isFalse();
        expect.that(context.getMaxBuyerInputSizeBytes()).isEqualTo(0);
        expect.that(context.getPerBuyerConfigurations()).isEmpty();
    }

    @Test
    public void testPrepareSellerConfiguration_SetsMaxToZeroWithZeroDifference() {
        int sellerConfigurationSize =
                META_INFO_LENGTH_BYTE + DATA_SIZE_PADDING_LENGTH_BYTE + CURRENT_PAYLOAD_SIZE_BYTES;
        SellerConfiguration sellerConfiguration =
                new SellerConfiguration.Builder()
                        .setMaximumPayloadSizeBytes(sellerConfigurationSize)
                        .build();
        expect.that(
                        mArgumentsPreparer
                                .preparePayloadOptimizationContext(
                                        sellerConfiguration, CURRENT_PAYLOAD_SIZE_BYTES)
                                .getMaxBuyerInputSizeBytes())
                .isEqualTo(0);
    }

    @Test
    public void testPrepareSellerConfiguration_SetsMaxToZeroWithNegativeDifference() {
        int sellerConfigurationSize =
                META_INFO_LENGTH_BYTE
                        + DATA_SIZE_PADDING_LENGTH_BYTE
                        + CURRENT_PAYLOAD_SIZE_BYTES
                        - 1; // subtract 1 to make answer negative
        SellerConfiguration sellerConfiguration =
                new SellerConfiguration.Builder()
                        .setMaximumPayloadSizeBytes(sellerConfigurationSize)
                        .build();
        expect.that(
                        mArgumentsPreparer
                                .preparePayloadOptimizationContext(
                                        sellerConfiguration, CURRENT_PAYLOAD_SIZE_BYTES)
                                .getMaxBuyerInputSizeBytes())
                .isEqualTo(0);
    }

    @Test
    public void testPrepareSellerConfiguration_ReturnsSellerConfigurationWithUpdatedSize() {
        int sellerConfigurationSize =
                META_INFO_LENGTH_BYTE
                        + DATA_SIZE_PADDING_LENGTH_BYTE
                        + CURRENT_PAYLOAD_SIZE_BYTES
                        + SELLER_CONFIGURATION.getMaximumPayloadSizeBytes();
        SellerConfiguration sellerConfiguration =
                new SellerConfiguration.Builder()
                        .setMaximumPayloadSizeBytes(sellerConfigurationSize)
                        .setPerBuyerConfigurations(
                                Set.of(PER_BUYER_CONFIGURATION_1, PER_BUYER_CONFIGURATION_2))
                        .build();
        PayloadOptimizationContext payloadOptimizationContext =
                mArgumentsPreparer.preparePayloadOptimizationContext(
                        sellerConfiguration, CURRENT_PAYLOAD_SIZE_BYTES);
        expect.that(payloadOptimizationContext.getOptimizationsEnabled()).isTrue();
        expect.that(payloadOptimizationContext.getMaxBuyerInputSizeBytes())
                .isEqualTo(SELLER_CONFIGURATION.getMaximumPayloadSizeBytes());
        expect.that(payloadOptimizationContext.getPerBuyerConfigurations())
                .isEqualTo(SELLER_CONFIGURATION.getPerBuyerConfigurations());
    }
}

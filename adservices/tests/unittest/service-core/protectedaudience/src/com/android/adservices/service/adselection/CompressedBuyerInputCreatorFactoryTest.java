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

import static android.adservices.common.CommonFixture.FIXED_CLOCK_TRUNCATED_TO_MILLI;

import static org.mockito.ArgumentMatchers.anyInt;

import android.adservices.adselection.PerBuyerConfiguration;
import android.adservices.common.AdTechIdentifier;

import com.android.adservices.common.AdServicesMockitoTestCase;
import com.android.adservices.data.customaudience.CustomAudienceDao;
import com.android.adservices.data.signals.EncodedPayloadDao;
import com.android.adservices.service.stats.GetAdSelectionDataApiCalledStats;

import com.google.common.collect.ImmutableSet;

import org.junit.Test;
import org.mockito.Mock;

public class CompressedBuyerInputCreatorFactoryTest extends AdServicesMockitoTestCase {

    @Mock private CompressedBuyerInputCreatorHelper mCompressedBuyerInputCreatorHelperMock;
    @Mock private AuctionServerDataCompressor mDataCompressorMock;
    @Mock private CustomAudienceDao mCustomAudienceDaoMock;
    @Mock private EncodedPayloadDao mEncodedPayloadDaoMock;
    private static final int MAX_NUM_RECOMPRESSIONS = 5;
    private static final int PAS_MAX_SIZE = 10;
    private static final int MAX_PAYLOAD_SIZE = 20;

    private static final boolean SELLER_CONFIGURATION_DISABLED = false;
    private static final boolean SELLER_CONFIGURATION_ENABLED = true;
    private static final PerBuyerConfiguration BUYER_CONFIGURATION =
            new PerBuyerConfiguration.Builder()
                    .setBuyer(AdTechIdentifier.fromString("buyer1"))
                    .setTargetInputSizeBytes(MAX_PAYLOAD_SIZE)
                    .build();

    private static final PayloadOptimizationContext PAYLOAD_OPTIMIZATION_CONTEXT_DISABLED =
            PayloadOptimizationContext.builder().build();
    private static final PayloadOptimizationContext PAYLOAD_OPTIMIZATION_CONTEXT_ENABLED =
            PayloadOptimizationContext.builder()
                    .setMaxBuyerInputSizeBytes(MAX_PAYLOAD_SIZE)
                    .setPerBuyerConfigurations(ImmutableSet.of(BUYER_CONFIGURATION))
                    .setOptimizationsEnabled(true)
                    .build();

    @Test
    public void testCreatePayloadFormatterReturnsNoOptimizationsSellerConfigurationDisabled() {
        CompressedBuyerInputCreatorFactory compressedBuyerInputCreatorFactory =
                new CompressedBuyerInputCreatorFactory(
                        mCompressedBuyerInputCreatorHelperMock,
                        mDataCompressorMock,
                        SELLER_CONFIGURATION_DISABLED,
                        mCustomAudienceDaoMock,
                        mEncodedPayloadDaoMock,
                        CompressedBuyerInputCreatorNoOptimizations.VERSION,
                        MAX_NUM_RECOMPRESSIONS,
                        PAS_MAX_SIZE,
                        FIXED_CLOCK_TRUNCATED_TO_MILLI);
        CompressedBuyerInputCreator compressedBuyerInputCreator =
                compressedBuyerInputCreatorFactory.createCompressedBuyerInputCreator(
                        PAYLOAD_OPTIMIZATION_CONTEXT_DISABLED,
                        GetAdSelectionDataApiCalledStats.builder());

        expect.that(compressedBuyerInputCreator)
                .isInstanceOf(CompressedBuyerInputCreatorNoOptimizations.class);
    }

    @Test
    public void testCreatePayloadFormatterReturnsNoOptimizationsVersionIsZero() {
        CompressedBuyerInputCreatorFactory compressedBuyerInputCreatorFactory =
                new CompressedBuyerInputCreatorFactory(
                        mCompressedBuyerInputCreatorHelperMock,
                        mDataCompressorMock,
                        SELLER_CONFIGURATION_ENABLED,
                        mCustomAudienceDaoMock,
                        mEncodedPayloadDaoMock,
                        CompressedBuyerInputCreatorNoOptimizations.VERSION,
                        MAX_NUM_RECOMPRESSIONS,
                        PAS_MAX_SIZE,
                        FIXED_CLOCK_TRUNCATED_TO_MILLI);
        CompressedBuyerInputCreator compressedBuyerInputCreator =
                compressedBuyerInputCreatorFactory.createCompressedBuyerInputCreator(
                        PAYLOAD_OPTIMIZATION_CONTEXT_ENABLED,
                        GetAdSelectionDataApiCalledStats.builder());

        expect.that(compressedBuyerInputCreator)
                .isInstanceOf(CompressedBuyerInputCreatorNoOptimizations.class);
    }

    @Test
    public void testCreatePayloadFormatterReturnsSellerMaxImpl() {
        CompressedBuyerInputCreatorFactory compressedBuyerInputCreatorFactory =
                new CompressedBuyerInputCreatorFactory(
                        mCompressedBuyerInputCreatorHelperMock,
                        mDataCompressorMock,
                        SELLER_CONFIGURATION_ENABLED,
                        mCustomAudienceDaoMock,
                        mEncodedPayloadDaoMock,
                        CompressedBuyerInputCreatorSellerPayloadMaxImpl.VERSION,
                        MAX_NUM_RECOMPRESSIONS,
                        PAS_MAX_SIZE,
                        FIXED_CLOCK_TRUNCATED_TO_MILLI);
        CompressedBuyerInputCreator compressedBuyerInputCreator =
                compressedBuyerInputCreatorFactory.createCompressedBuyerInputCreator(
                        PAYLOAD_OPTIMIZATION_CONTEXT_ENABLED,
                        GetAdSelectionDataApiCalledStats.builder());

        expect.that(compressedBuyerInputCreator)
                .isInstanceOf(CompressedBuyerInputCreatorSellerPayloadMaxImpl.class);
    }

    @Test
    public void testCreatePayloadFormatterReturnsPerBuyerLimitsGreedyImpl() {
        CompressedBuyerInputCreatorFactory compressedBuyerInputCreatorFactory =
                new CompressedBuyerInputCreatorFactory(
                        mCompressedBuyerInputCreatorHelperMock,
                        mDataCompressorMock,
                        SELLER_CONFIGURATION_ENABLED,
                        mCustomAudienceDaoMock,
                        mEncodedPayloadDaoMock,
                        CompressedBuyerInputCreatorPerBuyerLimitsGreedyImpl.VERSION,
                        anyInt(), // num recalculations is not used in per buyer limits impl
                        PAS_MAX_SIZE,
                        FIXED_CLOCK_TRUNCATED_TO_MILLI);
        CompressedBuyerInputCreator compressedBuyerInputCreator =
                compressedBuyerInputCreatorFactory.createCompressedBuyerInputCreator(
                        PAYLOAD_OPTIMIZATION_CONTEXT_ENABLED,
                        GetAdSelectionDataApiCalledStats.builder());

        expect.that(compressedBuyerInputCreator)
                .isInstanceOf(CompressedBuyerInputCreatorPerBuyerLimitsGreedyImpl.class);
    }

    @Test
    public void testGetBuyerInputDataFetcherSellerConfigurationEnabled() {
        CompressedBuyerInputCreatorFactory compressedBuyerInputCreatorFactory =
                new CompressedBuyerInputCreatorFactory(
                        mCompressedBuyerInputCreatorHelperMock,
                        mDataCompressorMock,
                        SELLER_CONFIGURATION_ENABLED,
                        mCustomAudienceDaoMock,
                        mEncodedPayloadDaoMock,
                        CompressedBuyerInputCreatorNoOptimizations.VERSION,
                        MAX_NUM_RECOMPRESSIONS,
                        PAS_MAX_SIZE,
                        FIXED_CLOCK_TRUNCATED_TO_MILLI);

        BuyerInputDataFetcher buyerInputDataFetcher =
                compressedBuyerInputCreatorFactory.getBuyerInputDataFetcher();

        expect.that(buyerInputDataFetcher)
                .isInstanceOf(BuyerInputDataFetcherBuyerAllowListImpl.class);
    }

    @Test
    public void testGetBuyerInputDataFetcherSellerConfigurationDisabled() {
        CompressedBuyerInputCreatorFactory compressedBuyerInputCreatorFactory =
                new CompressedBuyerInputCreatorFactory(
                        mCompressedBuyerInputCreatorHelperMock,
                        mDataCompressorMock,
                        SELLER_CONFIGURATION_DISABLED,
                        mCustomAudienceDaoMock,
                        mEncodedPayloadDaoMock,
                        CompressedBuyerInputCreatorNoOptimizations.VERSION,
                        MAX_NUM_RECOMPRESSIONS,
                        PAS_MAX_SIZE,
                        FIXED_CLOCK_TRUNCATED_TO_MILLI);

        BuyerInputDataFetcher buyerInputDataFetcher =
                compressedBuyerInputCreatorFactory.getBuyerInputDataFetcher();

        expect.that(buyerInputDataFetcher).isInstanceOf(BuyerInputDataFetcherAllBuyersImpl.class);
    }

    @Test
    public void testGetBuyerInputGeneratorArgumentsPreparerSellerConfigurationDisabled() {
        CompressedBuyerInputCreatorFactory compressedBuyerInputCreatorFactory =
                new CompressedBuyerInputCreatorFactory(
                        mCompressedBuyerInputCreatorHelperMock,
                        mDataCompressorMock,
                        SELLER_CONFIGURATION_DISABLED,
                        mCustomAudienceDaoMock,
                        mEncodedPayloadDaoMock,
                        CompressedBuyerInputCreatorNoOptimizations.VERSION,
                        MAX_NUM_RECOMPRESSIONS,
                        PAS_MAX_SIZE,
                        FIXED_CLOCK_TRUNCATED_TO_MILLI);

        BuyerInputGeneratorArgumentsPreparer argumentsPreparer =
                compressedBuyerInputCreatorFactory.getBuyerInputGeneratorArgumentsPreparer();

        expect.that(argumentsPreparer)
                .isInstanceOf(
                        BuyerInputGeneratorArgumentsPreparerSellerConfigurationDisabled.class);
    }

    @Test
    public void testGetBuyerInputGeneratorArgumentsPreparerSellerConfigurationEnabled() {
        CompressedBuyerInputCreatorFactory compressedBuyerInputCreatorFactory =
                new CompressedBuyerInputCreatorFactory(
                        mCompressedBuyerInputCreatorHelperMock,
                        mDataCompressorMock,
                        SELLER_CONFIGURATION_ENABLED,
                        mCustomAudienceDaoMock,
                        mEncodedPayloadDaoMock,
                        CompressedBuyerInputCreatorNoOptimizations.VERSION,
                        MAX_NUM_RECOMPRESSIONS,
                        PAS_MAX_SIZE,
                        FIXED_CLOCK_TRUNCATED_TO_MILLI);
        BuyerInputGeneratorArgumentsPreparer argumentsPreparer =
                compressedBuyerInputCreatorFactory.getBuyerInputGeneratorArgumentsPreparer();

        expect.that(argumentsPreparer)
                .isInstanceOf(BuyerInputGeneratorArgumentsPreparerSellerConfigurationEnabled.class);
    }
}

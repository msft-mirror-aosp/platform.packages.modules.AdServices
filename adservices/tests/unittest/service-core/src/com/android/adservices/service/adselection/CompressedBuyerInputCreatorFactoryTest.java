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

import com.android.adservices.common.AdServicesMockitoTestCase;
import com.android.adservices.data.customaudience.CustomAudienceDao;
import com.android.adservices.data.signals.EncodedPayloadDao;

import org.junit.Test;
import org.mockito.Mock;

public class CompressedBuyerInputCreatorFactoryTest extends AdServicesMockitoTestCase {

    @Mock private CompressedBuyerInputCreatorHelper mCompressedBuyerInputCreatorHelperMock;
    @Mock private AuctionServerDataCompressor mDataCompressorMock;
    @Mock private CustomAudienceDao mCustomAudienceDaoMock;
    @Mock private EncodedPayloadDao mEncodedPayloadDaoMock;

    private final boolean mSellerConfigurationDisabled = false;

    @Test
    public void createPayloadFormatterReturnsNoOptimizations() {
        CompressedBuyerInputCreatorFactory compressedBuyerInputCreatorFactory =
                new CompressedBuyerInputCreatorFactory(
                        mCompressedBuyerInputCreatorHelperMock,
                        mDataCompressorMock,
                        mSellerConfigurationDisabled,
                        mCustomAudienceDaoMock,
                        mEncodedPayloadDaoMock);
        CompressedBuyerInputCreator compressedBuyerInputCreator =
                compressedBuyerInputCreatorFactory.createCompressedBuyerInputCreator();

        expect.that(compressedBuyerInputCreator)
                .isInstanceOf(CompressedBuyerInputCreatorNoOptimizations.class);
    }

    @Test
    public void getBuyerInputDataFetcherSellerConfigurationDisabled() {
        boolean sellerConfigurationEnabled = true;
        CompressedBuyerInputCreatorFactory compressedBuyerInputCreatorFactory =
                new CompressedBuyerInputCreatorFactory(
                        mCompressedBuyerInputCreatorHelperMock,
                        mDataCompressorMock,
                        sellerConfigurationEnabled,
                        mCustomAudienceDaoMock,
                        mEncodedPayloadDaoMock);

        BuyerInputDataFetcher buyerInputDataFetcher =
                compressedBuyerInputCreatorFactory.getBuyerInputDataFetcher();

        expect.that(buyerInputDataFetcher)
                .isInstanceOf(BuyerInputDataFetcherBuyerAllowListImpl.class);
    }

    @Test
    public void getBuyerInputDataFetcherSellerConfigurationEnabled() {
        CompressedBuyerInputCreatorFactory compressedBuyerInputCreatorFactory =
                new CompressedBuyerInputCreatorFactory(
                        mCompressedBuyerInputCreatorHelperMock,
                        mDataCompressorMock,
                        mSellerConfigurationDisabled,
                        mCustomAudienceDaoMock,
                        mEncodedPayloadDaoMock);

        BuyerInputDataFetcher buyerInputDataFetcher =
                compressedBuyerInputCreatorFactory.getBuyerInputDataFetcher();

        expect.that(buyerInputDataFetcher).isInstanceOf(BuyerInputDataFetcherAllBuyersImpl.class);
    }
}

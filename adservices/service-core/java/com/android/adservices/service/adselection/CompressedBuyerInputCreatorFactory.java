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

import android.annotation.NonNull;

import com.android.adservices.LoggerFactory;
import com.android.adservices.data.customaudience.CustomAudienceDao;
import com.android.adservices.data.signals.EncodedPayloadDao;
import com.android.adservices.service.stats.GetAdSelectionDataApiCalledStats;

import java.time.Clock;

/** Factory for {@link CompressedBuyerInputCreator} */
public class CompressedBuyerInputCreatorFactory {
    private final CompressedBuyerInputCreatorHelper mCompressedBuyerInputCreatorHelper;
    private final AuctionServerDataCompressor mDataCompressor;
    private final boolean mSellerConfigurationEnabled;
    private final CustomAudienceDao mCustomAudienceDao;
    private final EncodedPayloadDao mEncodedPayloadDao;
    private final int mCompressedBuyerInputCreatorVersion;
    private static final LoggerFactory.Logger sLogger = LoggerFactory.getFledgeLogger();
    private final int mMaxNumRecompressions;
    private final int mPasMaxPerBuyerSizeBytes;
    private final Clock mClock;

    public CompressedBuyerInputCreatorFactory(
            CompressedBuyerInputCreatorHelper compressedBuyerInputCreatorHelper,
            AuctionServerDataCompressor dataCompressor,
            boolean sellerConfigurationEnabled,
            CustomAudienceDao customAudienceDao,
            EncodedPayloadDao encodedPayloadDao,
            int compressedBuyerInputCreatorVersion,
            int maxNumRecompressions,
            int pasMaxPerBuyerSizeBytes,
            Clock clock) {
        mCompressedBuyerInputCreatorHelper = compressedBuyerInputCreatorHelper;
        mDataCompressor = dataCompressor;
        mSellerConfigurationEnabled = sellerConfigurationEnabled;
        mCustomAudienceDao = customAudienceDao;
        mEncodedPayloadDao = encodedPayloadDao;
        mCompressedBuyerInputCreatorVersion = compressedBuyerInputCreatorVersion;
        mMaxNumRecompressions = maxNumRecompressions;
        mPasMaxPerBuyerSizeBytes = pasMaxPerBuyerSizeBytes;
        mClock = clock;
    }

    /** Returns an implementation for the {@link CompressedBuyerInputCreator} */
    @NonNull
    public CompressedBuyerInputCreator createCompressedBuyerInputCreator(
            PayloadOptimizationContext payloadOptimizationContext,
            GetAdSelectionDataApiCalledStats.Builder builder) {
        if (mSellerConfigurationEnabled) {
            if (mCompressedBuyerInputCreatorVersion
                    == CompressedBuyerInputCreatorSellerPayloadMaxImpl.VERSION) {
                sLogger.v("Returning CompressedBuyerInputCreatorSellerPayloadMaxImpl");
                int maxPayloadSizeBytes = payloadOptimizationContext.getMaxBuyerInputSizeBytes();
                return new CompressedBuyerInputCreatorSellerPayloadMaxImpl(
                        mCompressedBuyerInputCreatorHelper,
                        mDataCompressor,
                        mMaxNumRecompressions,
                        maxPayloadSizeBytes,
                        mPasMaxPerBuyerSizeBytes,
                        mClock,
                        builder);
            } else if (mCompressedBuyerInputCreatorVersion
                    == CompressedBuyerInputCreatorPerBuyerLimitsGreedyImpl.VERSION) {
                sLogger.v("Returning CompressedBuyerInputCreatorPerBuyerLimitsImpl");
                return new CompressedBuyerInputCreatorPerBuyerLimitsGreedyImpl(
                        mCompressedBuyerInputCreatorHelper,
                        mDataCompressor,
                        payloadOptimizationContext,
                        mPasMaxPerBuyerSizeBytes);
            }
        }
        // Update this whn more implementations are available
        sLogger.v("Returning CompressedBuyerInputCreatorNoOptimizations");
        return new CompressedBuyerInputCreatorNoOptimizations(
                mCompressedBuyerInputCreatorHelper, mDataCompressor, mClock, builder);
    }

    /**
     * Returns an implementation of {@link BuyerInputDataFetcher} depending on the seller
     * configuration flag.
     */
    public BuyerInputDataFetcher getBuyerInputDataFetcher() {
        return mSellerConfigurationEnabled
                ? new BuyerInputDataFetcherBuyerAllowListImpl(
                        mCustomAudienceDao, mEncodedPayloadDao)
                : new BuyerInputDataFetcherAllBuyersImpl(mCustomAudienceDao, mEncodedPayloadDao);
    }

    /**
     * Returns an implementation of {@link BuyerInputGeneratorArgumentsPreparer} depending on the
     * seller configuration flag.
     */
    public BuyerInputGeneratorArgumentsPreparer getBuyerInputGeneratorArgumentsPreparer() {
        return mSellerConfigurationEnabled
                ? new BuyerInputGeneratorArgumentsPreparerSellerConfigurationEnabled()
                : new BuyerInputGeneratorArgumentsPreparerSellerConfigurationDisabled();
    }
}
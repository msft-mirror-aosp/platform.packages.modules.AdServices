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

/** Factory for {@link CompressedBuyerInputCreator} */
public class CompressedBuyerInputCreatorFactory {
    private final CompressedBuyerInputCreatorHelper mCompressedBuyerInputCreatorHelper;
    private final AuctionServerDataCompressor mDataCompressor;
    private static final LoggerFactory.Logger sLogger = LoggerFactory.getFledgeLogger();

    public CompressedBuyerInputCreatorFactory(
            CompressedBuyerInputCreatorHelper compressedBuyerInputCreatorHelper,
            AuctionServerDataCompressor dataCompressor) {
        mCompressedBuyerInputCreatorHelper = compressedBuyerInputCreatorHelper;
        mDataCompressor = dataCompressor;
    }

    /** Returns an implementation for the {@link CompressedBuyerInputCreator} */
    @NonNull
    public CompressedBuyerInputCreator createCompressedBuyerInputCreator() {
        // Update this whn more implementations are available
        sLogger.v("Returning CompressedBuyerInputCreatorNoOptimizations");
        return new CompressedBuyerInputCreatorNoOptimizations(
                mCompressedBuyerInputCreatorHelper, mDataCompressor);
    }
}

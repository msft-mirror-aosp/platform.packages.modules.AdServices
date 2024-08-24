/*
 * Copyright (C) 2023 The Android Open Source Project
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
import android.annotation.NonNull;
import android.annotation.Nullable;

import com.android.adservices.LoggerFactory;
import com.android.adservices.service.stats.AdServicesLogger;
import com.android.internal.annotations.VisibleForTesting;
import com.android.internal.util.Preconditions;

import com.google.common.collect.ImmutableList;

import java.util.Objects;

/** Factory for {@link AuctionServerPayloadFormatter} and {@link AuctionServerPayloadExtractor} */
public class AuctionServerPayloadFormatterFactory {
    private static final LoggerFactory.Logger sLogger = LoggerFactory.getFledgeLogger();

    @VisibleForTesting
    static final String NO_IMPLEMENTATION_FOUND = "No %s implementation found for version %s";

    /** Returns an implementation for the {@link AuctionServerPayloadFormatter} */
    @NonNull
    public static AuctionServerPayloadFormatter createPayloadFormatter(
            int version,
            @NonNull ImmutableList<Integer> availableBucketSizes,
            @Nullable SellerConfiguration sellerConfiguration) {
        Preconditions.checkCollectionNotEmpty(availableBucketSizes, "available bucket sizes.");

        if (version == AuctionServerPayloadFormatterV0.VERSION) {
            sLogger.v("Using AuctionServerPayloadFormatterV0 formatter");
            return new AuctionServerPayloadFormatterV0(availableBucketSizes);
        } else if (version == AuctionServerPayloadFormatterExcessiveMaxSize.VERSION) {
            sLogger.v("Using AuctionServerPayloadFormatterExcessiveMaxSize formatter");
            return new AuctionServerPayloadFormatterExcessiveMaxSize();
        } else if (version == AuctionServerPayloadFormatterExactSize.VERSION) {
            if (Objects.nonNull(sellerConfiguration)) {
                sLogger.v("Using AuctionServerPayloadFormatterExactSize formatter");
                return new AuctionServerPayloadFormatterExactSize(
                        sellerConfiguration.getMaximumPayloadSizeBytes());
            } else {
                sLogger.v("Using AuctionServerPayloadFormatterExcessiveMaxSize formatter");
                return new AuctionServerPayloadFormatterExcessiveMaxSize();
            }
        }

        String errMsg =
                String.format(
                        NO_IMPLEMENTATION_FOUND,
                        AuctionServerPayloadFormatter.class.getName(),
                        version);
        sLogger.e(errMsg);
        throw new IllegalArgumentException(errMsg);
    }

    /** Returns an implementation for the {@link AuctionServerPayloadExtractor} */
    @NonNull
    public static AuctionServerPayloadExtractor createPayloadExtractor(
            int version, AdServicesLogger adServicesLogger) {
        if (version == AuctionServerPayloadFormatterV0.VERSION) {
            // Extract data does not need bucket size list nor payload metrics feature
            return new AuctionServerPayloadFormatterV0(ImmutableList.of());
        } else if (version == AuctionServerPayloadFormatterExcessiveMaxSize.VERSION) {
            return new AuctionServerPayloadFormatterExcessiveMaxSize();
        } else if (version == AuctionServerPayloadFormatterExactSize.VERSION) {
            // Extract data not need target size
            return new AuctionServerPayloadFormatterExactSize(/* targetPayloadSizeBytes= */ 0);
        }

        String errMsg =
                String.format(
                        NO_IMPLEMENTATION_FOUND,
                        AuctionServerPayloadExtractor.class.getName(),
                        version);
        sLogger.e(errMsg);
        throw new IllegalArgumentException(errMsg);
    }
}

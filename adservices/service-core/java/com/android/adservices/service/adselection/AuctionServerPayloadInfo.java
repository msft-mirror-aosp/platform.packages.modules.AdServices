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

import android.adservices.common.AdTechIdentifier;

import androidx.annotation.NonNull;

import com.android.adservices.service.adselection.debug.AuctionServerDebugConfiguration;

import com.google.auto.value.AutoValue;
import com.google.common.collect.ImmutableMap;

/** POJO to hold the payload to be send to bidding and auction servers. */
@AutoValue
abstract class AuctionServerPayloadInfo {
    @NonNull
    abstract ImmutableMap<AdTechIdentifier, AuctionServerDataCompressor.CompressedData>
            getCompressedBuyerInput();

    @NonNull
    abstract String getPackageName();

    abstract long getAdSelectionDataId();

    @NonNull
    abstract AuctionServerDebugConfiguration getAuctionServerDebugConfiguration();

    static Builder builder() {
        return new AutoValue_AuctionServerPayloadInfo.Builder();
    }

    @AutoValue.Builder
    abstract static class Builder {
        abstract Builder setCompressedBuyerInput(
                @NonNull
                        ImmutableMap<AdTechIdentifier, AuctionServerDataCompressor.CompressedData>
                                compressedBuyerInput);

        abstract Builder setPackageName(@NonNull String packageName);

        abstract Builder setAdSelectionDataId(long adSelectionDataId);

        abstract Builder setAuctionServerDebugConfiguration(
                @NonNull AuctionServerDebugConfiguration auctionServerDebugConfiguration);

        abstract AuctionServerPayloadInfo build();
    }
}

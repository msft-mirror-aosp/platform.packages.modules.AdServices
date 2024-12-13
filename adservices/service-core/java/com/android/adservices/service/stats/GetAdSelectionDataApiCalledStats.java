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

package com.android.adservices.service.stats;

import static com.android.adservices.service.stats.AdServicesLoggerUtil.FIELD_UNSET;
import static com.android.adservices.service.stats.AdsRelevanceStatusUtils.SERVER_AUCTION_COORDINATOR_SOURCE_UNSET;

import android.adservices.common.AdServicesStatusUtils;

import com.google.auto.value.AutoValue;

/** Class for GetAdSelectionData API called stats */
@AutoValue
public abstract class GetAdSelectionDataApiCalledStats {
    /** Returns the size of the payload in Kb after encryption and padding */
    public abstract int getPayloadSizeKb();

    /** Return number of buyers participating in this payload */
    public abstract int getNumBuyers();

    /** The status response code of the GetAdSelectionData API in AdServices */
    @AdServicesStatusUtils.StatusCode
    public abstract int getStatusCode();

    /** Return the coordinator source in this payload, i.e., DEFAULT or provided via API */
    @AdsRelevanceStatusUtils.ServerAuctionCoordinatorSource
    public abstract int getServerAuctionCoordinatorSource();

    /** Return the maximum size set by the seller. */
    public abstract int getSellerMaxSizeKb();

    /** Return the result of payload optimization. */
    public abstract PayloadOptimizationResult getPayloadOptimizationResult();

    /** Return the latency of buyer input generation. */
    public abstract int getInputGenerationLatencyMs();

    /** Version of compressed buyer input creator. */
    public abstract int getCompressedBuyerInputCreatorVersion();

    /** Number of times the payload was re compressed to update the current size estimation. */
    public abstract int getNumReEstimations();

    // The result of the getAdSelectionDataPayload optimization
    public enum PayloadOptimizationResult {
        PAYLOAD_OPTIMIZATION_RESULT_UNKNOWN(0),
        // there was still data available on the device but ran out of space.
        PAYLOAD_TRUNCATED_FOR_REQUESTED_MAX(1),
        // there was not enough data on the device so the max was not reached.
        PAYLOAD_WITHIN_REQUESTED_MAX(2);

        private final int mValue;

        PayloadOptimizationResult(int value) {
            mValue = value;
        }

        public int getValue() {
            return mValue;
        }
    }

    /** Returns a generic builder. */
    public static Builder builder() {
        return new AutoValue_GetAdSelectionDataApiCalledStats.Builder()
                .setServerAuctionCoordinatorSource(SERVER_AUCTION_COORDINATOR_SOURCE_UNSET)
                .setSellerMaxSizeKb(FIELD_UNSET)
                .setPayloadOptimizationResult(
                        PayloadOptimizationResult.PAYLOAD_OPTIMIZATION_RESULT_UNKNOWN)
                .setInputGenerationLatencyMs(FIELD_UNSET)
                .setCompressedBuyerInputCreatorVersion(FIELD_UNSET)
                .setNumReEstimations(FIELD_UNSET);
    }

    /** Builder class for GetAdSelectionDataApiCalledStats. */
    @AutoValue.Builder
    public abstract static class Builder {

        /** Sets the size of the payload in KB */
        public abstract Builder setPayloadSizeKb(int payloadSizeKb);

        /** Sets the number of buyers. */
        public abstract Builder setNumBuyers(int numBuyers);

        /** Sets the status code. */
        public abstract Builder setStatusCode(@AdServicesStatusUtils.StatusCode int statusCode);

        /** Sets the coordinator source in this payload, i.e., DEFAULT or provided via API. */
        public abstract Builder setServerAuctionCoordinatorSource(
                @AdsRelevanceStatusUtils.ServerAuctionCoordinatorSource int coordinatorSource);

        /** Sets the seller's maximum payload size in Kilobytes. */
        public abstract Builder setSellerMaxSizeKb(int sellerMaxSizeKb);

        /** Sets the result of payload optimization. */
        public abstract Builder setPayloadOptimizationResult(
                PayloadOptimizationResult payloadOptimizationResult);

        /** Sets the latency of buyer input generation. */
        public abstract Builder setInputGenerationLatencyMs(int inputGenerationLatencyMs);

        /** Sets the version of the compressed buyer input creator. */
        public abstract Builder setCompressedBuyerInputCreatorVersion(
                int compressedBuyerInputCreatorVersion);

        /**
         * Sets the number of times the payload was re compressed to update the current size
         * estimation.
         */
        public abstract Builder setNumReEstimations(int numReEstimations);

        /** Builds the {@link GetAdSelectionDataApiCalledStats} object. */
        public abstract GetAdSelectionDataApiCalledStats build();
    }
}

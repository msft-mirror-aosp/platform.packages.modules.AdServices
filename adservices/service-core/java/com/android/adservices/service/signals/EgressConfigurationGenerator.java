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

package com.android.adservices.service.signals;

import android.annotation.NonNull;

import com.android.adservices.LoggerFactory;
import com.android.adservices.service.adselection.AdIdFetcher;

import com.google.common.util.concurrent.FluentFuture;
import com.google.common.util.concurrent.Futures;
import com.google.common.util.concurrent.ListenableFuture;

import java.util.Objects;
import java.util.concurrent.ExecutorService;

/** Class to generate egress configuration for signals */
public interface EgressConfigurationGenerator {

    /**
     * @param packageName the package name of the caller.
     * @param callingUid the calling uid of the caller.
     * @return a Future of boolena value to determine if unlimited egress is enabled.
     */
    ListenableFuture<Boolean> isUnlimitedEgressEnabledForAuction(
            @NonNull String packageName, int callingUid);

    /**
     * @param enablePasUnlimitedEgress boolean to indicate if the feature is enabled.
     * @param adIdFetcher {@link com.android.adservices.service.adselection.AdIdFetcher}
     * @param auctionServerAdIdFetchTimeoutMs the timeout for AdId Fetcher call to
     *     isUnlimitedEgressDataEnabled
     * @param lightweightExecutorService Lightweight Executor Service.
     * @return a new Instance of {@link EgressConfigurationGenerator}
     */
    static EgressConfigurationGenerator createInstance(
            boolean enablePasUnlimitedEgress,
            @NonNull AdIdFetcher adIdFetcher,
            long auctionServerAdIdFetchTimeoutMs,
            @NonNull ExecutorService lightweightExecutorService) {
        Objects.requireNonNull(adIdFetcher, "AdIdFetcher cannot be null");
        Objects.requireNonNull(
                lightweightExecutorService, "Lightweight ExecutorService cannot be null");

        if (enablePasUnlimitedEgress) {
            return (packageName, callingUid) ->
                    FluentFuture.from(
                                    adIdFetcher.isLimitedAdTrackingEnabled(
                                            packageName,
                                            callingUid,
                                            auctionServerAdIdFetchTimeoutMs))
                            .transform(
                                    isLatEnabled -> {
                                        boolean isUnlimitedEgressDataEnabled = !isLatEnabled;
                                        LoggerFactory.getFledgeLogger()
                                                .v(
                                                        "Returning isUnlimitedEgressEnabled as: %b",
                                                        isUnlimitedEgressDataEnabled);
                                        return isUnlimitedEgressDataEnabled;
                                    },
                                    lightweightExecutorService);
        } else {
            return (packageName, callingUid) -> {
                LoggerFactory.getFledgeLogger()
                        .v(
                                "mEnablePasUnlimitedEgress is set to false. Returning"
                                        + " isUnlimitedEgressEnabledForAuction as false");
                return Futures.immediateFuture(false);
            };
        }
    }
}

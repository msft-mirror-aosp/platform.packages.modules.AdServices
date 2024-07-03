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

import android.adservices.adselection.PerBuyerConfiguration;
import android.adservices.common.AdTechIdentifier;
import android.annotation.NonNull;
import android.util.Pair;

import com.android.adservices.LoggerFactory;
import com.android.adservices.data.customaudience.DBCustomAudience;
import com.android.adservices.data.signals.DBEncodedPayload;
import com.android.adservices.service.profiling.Tracing;
import com.android.adservices.service.proto.bidding_auction_servers.BiddingAuctionServers.BuyerInput;

import com.google.common.util.concurrent.FluentFuture;
import com.google.common.util.concurrent.Futures;
import com.google.common.util.concurrent.ListenableFuture;
import com.google.common.util.concurrent.ListeningExecutorService;
import com.google.common.util.concurrent.MoreExecutors;

import java.time.Clock;
import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.concurrent.ExecutorService;
import java.util.function.Function;
import java.util.stream.Collectors;

/** Generates {@link BuyerInput} proto from device custom audience info. */
public class BuyerInputGenerator {

    private static final String EMPTY_USER_BIDDING_SIGNALS = "{}";
    private static final LoggerFactory.Logger sLogger = LoggerFactory.getFledgeLogger();
    @NonNull private final FrequencyCapAdFilterer mFrequencyCapAdFilterer;
    @NonNull private final AppInstallAdFilterer mAppInstallAdFilterer;
    @NonNull private final Clock mClock;
    @NonNull private final ListeningExecutorService mLightweightExecutorService;
    @NonNull private final ListeningExecutorService mBackgroundExecutorService;
    private final long mCustomAudienceActiveTimeWindowInMs;
    private final boolean mEnableAdFilter;
    private final boolean mEnableProtectedSignals;

    private final CompressedBuyerInputCreatorFactory mCompressedBuyerInputCreatorFactory;

    public BuyerInputGenerator(
            @NonNull final FrequencyCapAdFilterer frequencyCapAdFilterer,
            @NonNull final ExecutorService lightweightExecutorService,
            @NonNull final ExecutorService backgroundExecutorService,
            long customAudienceActiveTimeWindowInMs,
            boolean enableAdFilter,
            boolean enableProtectedSignals,
            @NonNull final AppInstallAdFilterer appInstallAdFilterer,
            CompressedBuyerInputCreatorFactory compressedBuyerInputCreatorFactory) {
        Objects.requireNonNull(frequencyCapAdFilterer);
        Objects.requireNonNull(lightweightExecutorService);
        Objects.requireNonNull(backgroundExecutorService);
        Objects.requireNonNull(appInstallAdFilterer);
        Objects.requireNonNull(compressedBuyerInputCreatorFactory);

        mFrequencyCapAdFilterer = frequencyCapAdFilterer;
        mClock = Clock.systemUTC();
        mLightweightExecutorService = MoreExecutors.listeningDecorator(lightweightExecutorService);
        mBackgroundExecutorService = MoreExecutors.listeningDecorator(backgroundExecutorService);

        mCustomAudienceActiveTimeWindowInMs = customAudienceActiveTimeWindowInMs;
        mEnableAdFilter = enableAdFilter;
        mEnableProtectedSignals = enableProtectedSignals;
        mAppInstallAdFilterer = appInstallAdFilterer;
        mCompressedBuyerInputCreatorFactory = compressedBuyerInputCreatorFactory;
    }

    /**
     * Creates {@link BuyerInput} class from {@link DBCustomAudience} entries to be sent to bidding
     * and auction servers.
     *
     * @return a map of buyer name and {@link BuyerInput}
     */
    public FluentFuture<Map<AdTechIdentifier, AuctionServerDataCompressor.CompressedData>>
            createCompressedBuyerInputs(
                    @NonNull PayloadOptimizationContext payloadOptimizationContext) {
        int traceCookie = Tracing.beginAsyncSection(Tracing.CREATE_BUYER_INPUTS);
        sLogger.v("Starting create buyer input");

        List<PerBuyerConfiguration> perBuyerConfigurations;

        if (payloadOptimizationContext.getOptimizationsEnabled()) {
            perBuyerConfigurations =
                    new ArrayList<>(payloadOptimizationContext.getPerBuyerConfigurations());
        } else {
            perBuyerConfigurations = List.of();
        }

        ListenableFuture<List<DBCustomAudience>> filteredCAs =
                FluentFuture.from(getBuyersCustomAudience(perBuyerConfigurations))
                        .transform(this::getFilteredCustomAudiences, mLightweightExecutorService);
        ListenableFuture<Map<AdTechIdentifier, DBEncodedPayload>> allSignals =
                getAllEncodedProtectedSignals(perBuyerConfigurations);
        ListenableFuture<Pair<List<DBCustomAudience>, Map<AdTechIdentifier, DBEncodedPayload>>>
                combinedCAsAndSignals =
                        Futures.whenAllSucceed(filteredCAs, allSignals)
                                .call(
                                        () -> {
                                            return new Pair<>(
                                                    Futures.getDone(filteredCAs),
                                                    Futures.getDone(allSignals));
                                        },
                                        mBackgroundExecutorService);

        return FluentFuture.from(combinedCAsAndSignals)
                .transform(
                        combined -> {
                            Map<AdTechIdentifier, AuctionServerDataCompressor.CompressedData>
                                    buyerInputFromCustomAudience =
                                            generateCompressedBuyerInputFromDBCAsAndEncodedSignals(
                                                    combined.first,
                                                    combined.second,
                                                    payloadOptimizationContext);
                            Tracing.endAsyncSection(Tracing.CREATE_BUYER_INPUTS, traceCookie);
                            return buyerInputFromCustomAudience;
                        },
                        mLightweightExecutorService);
    }

    private Map<AdTechIdentifier, AuctionServerDataCompressor.CompressedData>
            generateCompressedBuyerInputFromDBCAsAndEncodedSignals(
                    @NonNull final List<DBCustomAudience> dbCustomAudiences,
                    @NonNull final Map<AdTechIdentifier, DBEncodedPayload> encodedPayloadMap,
                    @NonNull PayloadOptimizationContext payloadOptimizationContext) {
        int traceCookie = Tracing.beginAsyncSection(Tracing.GET_COMPRESSED_BUYERS_INPUTS);

        int maxPayloadSizeBytes = 0;
        if (payloadOptimizationContext.getOptimizationsEnabled()) {
            maxPayloadSizeBytes = payloadOptimizationContext.getMaxBuyerInputSizeBytes();
        }

        CompressedBuyerInputCreator compressedBuyerInputCreator =
                mCompressedBuyerInputCreatorFactory.createCompressedBuyerInputCreator(
                        maxPayloadSizeBytes);

        Map<AdTechIdentifier, AuctionServerDataCompressor.CompressedData> compressedInputs =
                compressedBuyerInputCreator.generateCompressedBuyerInputFromDBCAsAndEncodedSignals(
                        dbCustomAudiences, encodedPayloadMap);
        Tracing.endAsyncSection(Tracing.GET_COMPRESSED_BUYERS_INPUTS, traceCookie);
        return compressedInputs;
    }

    private ListenableFuture<List<DBCustomAudience>> getBuyersCustomAudience(
            List<PerBuyerConfiguration> perBuyerConfigurations) {
        int traceCookie = Tracing.beginAsyncSection(Tracing.GET_BUYERS_CA);
        return mBackgroundExecutorService.submit(
                () -> {
                    List<DBCustomAudience> allActiveCAs =
                            mCompressedBuyerInputCreatorFactory
                                    .getBuyerInputDataFetcher()
                                    .getActiveCustomAudiences(
                                            perBuyerConfigurations,
                                            mClock.instant(),
                                            mCustomAudienceActiveTimeWindowInMs);
                    int numberOfCAsCollected =
                            (Objects.isNull(allActiveCAs) ? 0 : allActiveCAs.size());
                    sLogger.v(
                            String.format(
                                    "Collected %s active CAs from device", numberOfCAsCollected));
                    Tracing.endAsyncSection(Tracing.GET_BUYERS_CA, traceCookie);
                    return allActiveCAs;
                });
    }

    private List<DBCustomAudience> getFilteredCustomAudiences(
            @NonNull final List<DBCustomAudience> dbCustomAudiences) {
        int tracingCookie = Tracing.beginAsyncSection(Tracing.GET_FILTERED_BUYERS_CA);
        List<DBCustomAudience> filteredCustomAudiences =
                dbCustomAudiences.stream()
                        .filter(
                                AuctionServerCustomAudienceFilterer
                                        ::isValidCustomAudienceForServerSideAuction)
                        .collect(Collectors.toList());
        sLogger.v(
                String.format(
                        "After auction server filtering : %s active CAs from device",
                        filteredCustomAudiences.size()));
        if (mEnableAdFilter) {
            filteredCustomAudiences =
                    mAppInstallAdFilterer.filterCustomAudiences(filteredCustomAudiences);
            filteredCustomAudiences =
                    mFrequencyCapAdFilterer.filterCustomAudiences(filteredCustomAudiences);
            sLogger.v(
                    String.format(
                            "After ad filtering : %s active CAs from device",
                            filteredCustomAudiences.size()));
        }
        Tracing.endAsyncSection(Tracing.GET_FILTERED_BUYERS_CA, tracingCookie);
        return filteredCustomAudiences;
    }

    private ListenableFuture<Map<AdTechIdentifier, DBEncodedPayload>> getAllEncodedProtectedSignals(
            List<PerBuyerConfiguration> perBuyerConfigurations) {
        // If the feature flag is turned off we short circuit, and return empty signals
        if (!mEnableProtectedSignals) {
            return Futures.immediateFuture(Collections.emptyMap());
        }
        int traceCookie = Tracing.beginAsyncSection(Tracing.GET_BUYERS_PS);
        return mBackgroundExecutorService.submit(
                () -> {
                    List<DBEncodedPayload> allBuyerSignals =
                            mCompressedBuyerInputCreatorFactory
                                    .getBuyerInputDataFetcher()
                                    .getProtectedAudienceSignals(perBuyerConfigurations);
                    int numberOfSignalsCollected =
                            (Objects.isNull(allBuyerSignals) ? 0 : allBuyerSignals.size());
                    sLogger.v(
                            String.format(
                                    "Collected %s signals from device", numberOfSignalsCollected));
                    Tracing.endAsyncSection(Tracing.GET_BUYERS_PS, traceCookie);
                    return allBuyerSignals.stream()
                            .collect(
                                    Collectors.toMap(
                                            DBEncodedPayload::getBuyer, Function.identity()));
                });
    }
}

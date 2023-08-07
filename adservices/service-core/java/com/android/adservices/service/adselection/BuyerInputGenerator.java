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

import android.adservices.common.AdTechIdentifier;
import android.annotation.NonNull;

import com.android.adservices.LoggerFactory;
import com.android.adservices.data.customaudience.CustomAudienceDao;
import com.android.adservices.data.customaudience.DBCustomAudience;
import com.android.adservices.service.Flags;
import com.android.adservices.service.profiling.Tracing;
import com.android.adservices.service.proto.bidding_auction_servers.BiddingAuctionServers.BuyerInput;

import com.google.common.base.Strings;
import com.google.common.collect.ImmutableList;
import com.google.common.util.concurrent.FluentFuture;
import com.google.common.util.concurrent.ListenableFuture;
import com.google.common.util.concurrent.ListeningExecutorService;
import com.google.common.util.concurrent.MoreExecutors;

import java.time.Clock;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.concurrent.ExecutorService;
import java.util.stream.Collectors;

/** Generates {@link BuyerInput} proto from device custom audience info. */
public class BuyerInputGenerator {

    private static final String EMPTY_USER_BIDDING_SIGNALS = "{}";
    private static final LoggerFactory.Logger sLogger = LoggerFactory.getFledgeLogger();
    @NonNull private final CustomAudienceDao mCustomAudienceDao;
    @NonNull private final AdFilterer mAdFilterer;
    @NonNull private final Clock mClock;
    @NonNull private final Flags mFlags;
    @NonNull private final ListeningExecutorService mLightweightExecutorService;
    @NonNull private final ListeningExecutorService mBackgroundExecutorService;

    public BuyerInputGenerator(
            @NonNull final CustomAudienceDao customAudienceDao,
            @NonNull final AdFilterer adFilterer,
            @NonNull final Flags flags,
            @NonNull final ExecutorService lightweightExecutorService,
            @NonNull final ExecutorService backgroundExecutorService) {
        Objects.requireNonNull(customAudienceDao);
        Objects.requireNonNull(adFilterer);
        Objects.requireNonNull(flags);
        Objects.requireNonNull(lightweightExecutorService);
        Objects.requireNonNull(backgroundExecutorService);

        mCustomAudienceDao = customAudienceDao;
        mAdFilterer = adFilterer;
        mClock = Clock.systemUTC();
        mFlags = flags;
        mLightweightExecutorService = MoreExecutors.listeningDecorator(lightweightExecutorService);
        mBackgroundExecutorService = MoreExecutors.listeningDecorator(backgroundExecutorService);
    }

    /**
     * Creates {@link BuyerInput} class from {@link DBCustomAudience} entries to be sent to bidding
     * and auction servers.
     *
     * @return a map of buyer name and {@link BuyerInput}
     */
    public FluentFuture<Map<AdTechIdentifier, BuyerInput>> createBuyerInputs() {
        int traceCookie = Tracing.beginAsyncSection(Tracing.CREATE_BUYER_INPUTS);
        sLogger.v("Starting create buyer input");
        return FluentFuture.from(getBuyersCustomAudience())
                .transform(this::getFilteredCustomAudiences, mLightweightExecutorService)
                .transform(
                        dbCustomAudiences -> {
                            Map<AdTechIdentifier, BuyerInput> buyerInputFromCustomAudience =
                                    generateBuyerInputFromDBCustomAudience(dbCustomAudiences);
                            Tracing.endAsyncSection(Tracing.CREATE_BUYER_INPUTS, traceCookie);
                            return buyerInputFromCustomAudience;
                        },
                        mLightweightExecutorService);
    }

    private Map<AdTechIdentifier, BuyerInput> generateBuyerInputFromDBCustomAudience(
            @NonNull final List<DBCustomAudience> dbCustomAudiences) {
        final Map<AdTechIdentifier, BuyerInput.Builder> buyerInputs = new HashMap<>();
        for (DBCustomAudience customAudience : dbCustomAudiences) {
            final AdTechIdentifier buyerName = customAudience.getBuyer();
            if (!buyerInputs.containsKey(buyerName)) {
                buyerInputs.put(buyerName, BuyerInput.newBuilder());
            }

            buyerInputs
                    .get(buyerName)
                    .addCustomAudiences(buildCustomAudienceProtoFrom(customAudience));
        }

        sLogger.v(String.format("Created BuyerInput proto for %s buyers", buyerInputs.size()));
        return buyerInputs.entrySet().stream()
                .collect(Collectors.toMap(e -> e.getKey(), e -> e.getValue().build()));
    }

    private ListenableFuture<List<DBCustomAudience>> getBuyersCustomAudience() {
        int traceCookie = Tracing.beginAsyncSection(Tracing.GET_BUYERS_CA);
        return mBackgroundExecutorService.submit(
                () -> {
                    List<DBCustomAudience> allActiveCAs =
                            mCustomAudienceDao.getAllActiveCustomAudienceForServerSideAuction(
                                    mClock.instant(),
                                    mFlags.getFledgeCustomAudienceActiveTimeWindowInMs());
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
                mAdFilterer.filterCustomAudiences(dbCustomAudiences).stream()
                        .filter(
                                AuctionServerCustomAudienceFilterer
                                        ::isValidCustomAudienceForServerSideAuction)
                        .collect(Collectors.toList());
        Tracing.endAsyncSection(Tracing.GET_FILTERED_BUYERS_CA, tracingCookie);
        return filteredCustomAudiences;
    }

    private BuyerInput.CustomAudience buildCustomAudienceProtoFrom(
            DBCustomAudience customAudience) {
        BuyerInput.CustomAudience.Builder customAudienceBuilder =
                BuyerInput.CustomAudience.newBuilder();

        return customAudienceBuilder
                .setName(customAudience.getName())
                .setUserBiddingSignals(getUserBiddingSignals(customAudience))
                .addAllBiddingSignalsKeys(getTrustedBiddingSignalKeys(customAudience))
                .addAllAdRenderIds(getAdRenderIds(customAudience))
                .build();
    }

    private List<String> getTrustedBiddingSignalKeys(@NonNull DBCustomAudience customAudience) {
        List<String> biddingSignalKeys = customAudience.getTrustedBiddingData().getKeys();
        // If the bidding signal keys is just the CA name, we don't need to pass it to the server.
        if (biddingSignalKeys.size() == 1
                && customAudience.getName().equals(biddingSignalKeys.get(0))) {
            return ImmutableList.of();
        }

        // Remove the CA name from the bidding signal keys list to save space.
        biddingSignalKeys.remove(customAudience.getName());
        return biddingSignalKeys;
    }

    private String getUserBiddingSignals(DBCustomAudience customAudience) {
        return customAudience.getUserBiddingSignals() == null
                ? EMPTY_USER_BIDDING_SIGNALS
                : customAudience.getUserBiddingSignals().toString();
    }

    private List<String> getAdRenderIds(DBCustomAudience dbCustomAudience) {
        return dbCustomAudience.getAds().stream()
                .filter(ad -> !Strings.isNullOrEmpty(ad.getAdRenderId()))
                .map(ad -> ad.getAdRenderId())
                .collect(Collectors.toList());
    }
}

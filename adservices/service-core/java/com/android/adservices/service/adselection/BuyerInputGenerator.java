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
import com.android.adservices.service.proto.bidding_auction_servers.BiddingAuctionServers.BuyerInput;

import com.google.common.collect.ImmutableList;
import com.google.common.util.concurrent.FluentFuture;
import com.google.common.util.concurrent.ListenableFuture;
import com.google.common.util.concurrent.ListeningExecutorService;
import com.google.common.util.concurrent.MoreExecutors;
import com.google.protobuf.ListValue;
import com.google.protobuf.Value;

import java.time.Clock;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.concurrent.ExecutorService;
import java.util.stream.Collectors;

/** Generates {@link BuyerInput} proto from device custom audience info. */
public class BuyerInputGenerator {
    private static final LoggerFactory.Logger sLogger = LoggerFactory.getFledgeLogger();
    @NonNull private final CustomAudienceDao mCustomAudienceDao;
    @NonNull private final Clock mClock;
    @NonNull private final Flags mFlags;
    @NonNull private final ListeningExecutorService mLightweightExecutorService;
    @NonNull private final ListeningExecutorService mBackgroundExecutorService;

    public BuyerInputGenerator(
            @NonNull final CustomAudienceDao customAudienceDao,
            @NonNull final Flags flags,
            @NonNull final ExecutorService lightweightExecutorService,
            @NonNull final ExecutorService backgroundExecutorService) {
        Objects.requireNonNull(customAudienceDao);
        Objects.requireNonNull(flags);
        Objects.requireNonNull(lightweightExecutorService);
        Objects.requireNonNull(backgroundExecutorService);

        mCustomAudienceDao = customAudienceDao;
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
        sLogger.v("Starting create buyer input");
        return FluentFuture.from(getBuyersCustomAudience())
                .transform(
                        this::generateBuyerInputFromDBCustomAudience, mLightweightExecutorService);
    }

    private Map<AdTechIdentifier, BuyerInput> generateBuyerInputFromDBCustomAudience(
            @NonNull final List<DBCustomAudience> dbCustomAudiences) {
        Map<AdTechIdentifier, BuyerInput.Builder> buyerInputs = new HashMap<>();

        AdTechIdentifier buyerName;
        for (DBCustomAudience customAudience : dbCustomAudiences) {
            if (!buyerInputs.containsKey(buyerName = customAudience.getBuyer())) {
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
                    return allActiveCAs;
                });
    }

    private BuyerInput.CustomAudience buildCustomAudienceProtoFrom(
            DBCustomAudience customAudience) {
        // TODO(b/284185225): Add ad_render_ids and ad_component_render_ids when they are
        //  available
        BuyerInput.CustomAudience.Builder customAudienceBuilder =
                BuyerInput.CustomAudience.newBuilder()
                        .setName(customAudience.getName())
                        .addAllBiddingSignalsKeys(getTrustedBiddingSignalKeys(customAudience));

        if (customAudience.getUserBiddingSignals() != null) {
            customAudienceBuilder.setUserBiddingSignals(getUserBiddingSignals(customAudience));
        }

        return customAudienceBuilder.build();
    }

    private List<String> getTrustedBiddingSignalKeys(@NonNull DBCustomAudience customAudience) {
        Objects.requireNonNull(customAudience.getTrustedBiddingData());

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

    private ListValue getUserBiddingSignals(DBCustomAudience customAudience) {
        Objects.requireNonNull(customAudience.getUserBiddingSignals());

        return ListValue.newBuilder()
                .addValues(
                        Value.newBuilder()
                                .setStringValue(customAudience.getUserBiddingSignals().toString()))
                .build();
    }
}

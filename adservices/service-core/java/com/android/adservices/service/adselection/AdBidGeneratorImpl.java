/*
 * Copyright (C) 2022 The Android Open Source Project
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

import android.adservices.adselection.AdWithBid;
import android.adservices.common.AdData;
import android.annotation.NonNull;
import android.annotation.Nullable;
import android.content.Context;

import com.android.adservices.LogUtil;
import com.android.adservices.data.adselection.CustomAudienceSignals;
import com.android.adservices.data.customaudience.DBCustomAudience;
import com.android.adservices.data.customaudience.DBTrustedBiddingData;
import com.android.internal.annotations.VisibleForTesting;

import com.google.common.collect.ImmutableList;
import com.google.common.util.concurrent.FluentFuture;
import com.google.common.util.concurrent.Futures;
import com.google.common.util.concurrent.MoreExecutors;

import org.json.JSONException;

import java.util.Comparator;
import java.util.List;
import java.util.Objects;
import java.util.concurrent.Executor;
import java.util.concurrent.ExecutorService;
import java.util.stream.Collectors;

/** This class implements the ad bid generator. */
public class AdBidGeneratorImpl implements AdBidGenerator {
    @NonNull private final Context mContext;
    @NonNull private final Executor mExecutor;
    @NonNull private final AdSelectionScriptEngine mAdSelectionScriptEngine;

    public AdBidGeneratorImpl(@NonNull Context context, @NonNull ExecutorService executorService) {
        Objects.requireNonNull(context);
        Objects.requireNonNull(executorService);

        mContext = context;
        mExecutor = MoreExecutors.listeningDecorator(executorService);
        mAdSelectionScriptEngine = new AdSelectionScriptEngine(mContext);
    }

    @VisibleForTesting
    AdBidGeneratorImpl(
            @NonNull Context context,
            @NonNull Executor executor,
            @NonNull AdSelectionScriptEngine adSelectionScriptEngine) {
        Objects.requireNonNull(context);
        Objects.requireNonNull(executor);
        Objects.requireNonNull(adSelectionScriptEngine);

        mContext = context;
        mExecutor = executor;
        mAdSelectionScriptEngine = adSelectionScriptEngine;
    }

    @Override
    @NonNull
    public FluentFuture<AdBiddingOutcome> runAdBiddingPerCA(
            @NonNull DBCustomAudience customAudience,
            @NonNull String buyerDecisionLogicJs,
            @NonNull String adSelectionSignals,
            @NonNull String buyerSignals,
            @NonNull String contextualSignals) {
        Objects.requireNonNull(customAudience);
        Objects.requireNonNull(buyerDecisionLogicJs);
        Objects.requireNonNull(adSelectionSignals);
        Objects.requireNonNull(buyerSignals);
        Objects.requireNonNull(contextualSignals);

        if (Objects.isNull(customAudience.getAds()) || customAudience.getAds().isEmpty()) {
            return FluentFuture.from(Futures.immediateFuture(null));
        }

        String trustedBiddingSignals =
                getTrustedBiddingSignals(customAudience.getTrustedBiddingData());
        String userSignals = buildUserSignals(customAudience);
        CustomAudienceSignals customAudienceSignals =
                CustomAudienceSignals.buildFromCustomAudience(customAudience);
        // TODO(b/231265311): update AdSelectionScriptEngine AdData class objects with DBAdData
        //  classes and remove this conversion.
        List<AdData> ads =
                customAudience.getAds().stream()
                        .map(
                                adData -> {
                                    return new AdData(adData.getRenderUrl(), adData.getMetadata());
                                })
                        .collect(Collectors.toList());
        // TODO(b/221862406): implementation ads filtering logic.
        return runBidding(
                        buyerDecisionLogicJs,
                        ImmutableList.copyOf(ads),
                        buyerSignals,
                        trustedBiddingSignals,
                        contextualSignals,
                        customAudienceSignals,
                        userSignals,
                        adSelectionSignals)
                .transform(
                        candidate -> {
                            if (Objects.isNull(candidate) || candidate.getBid() <= 0.0) {
                                return null;
                            }
                            CustomAudienceBiddingInfo customAudienceInfo =
                                    CustomAudienceBiddingInfo.create(
                                            customAudience, buyerDecisionLogicJs);
                            AdBiddingOutcome result =
                                    AdBiddingOutcome.builder()
                                            .setAdWithBid(candidate)
                                            .setCustomAudienceBiddingInfo(customAudienceInfo)
                                            .build();
                            return result;
                        },
                        mExecutor)
                .catching(JSONException.class, this::handleBiddingError, mExecutor);
    }

    @Nullable
    private AdBiddingOutcome handleBiddingError(JSONException e) {
        // TODO(b/231326420): Define and implement the certain non-expected exceptions should be
        // re-throw from the AdBidGenerator.
        LogUtil.e("Fail to generate bids for the ads in this custom audience.", e);
        return null;
    }

    private String getTrustedBiddingSignals(@NonNull DBTrustedBiddingData trustedBiddingData) {
        Objects.requireNonNull(trustedBiddingData);
        // TODO(b/221862503): implementing fetching trusted_bidding_signals.
        return "{}";
    }

    /**
     * @return user information with respect to the custom audience will be available to
     *     generateBid(). This could include language, demographic information, information about
     *     custom audience such as time in list, number of impressions, last N winning impression
     *     timestamp etc.
     */
    @NonNull
    public String buildUserSignals(@Nullable DBCustomAudience customAudience) {
        // TODO: implement how to build user_signals with respect to customAudience.
        return "{}";
    }

    /**
     * @return the {@link AdWithBid} with the best bid per CustomAudience.
     */
    @NonNull
    @VisibleForTesting
    FluentFuture<AdWithBid> runBidding(
            @NonNull String buyerDecisionLogicJs,
            @NonNull ImmutableList<AdData> ads,
            @NonNull String buyerSignals,
            @NonNull String trustedBiddingSignals,
            @NonNull String contextualSignals,
            @NonNull CustomAudienceSignals customAudienceSignals,
            @NonNull String userSignals,
            @NonNull String adSelectionSignals) {
        try {
            return FluentFuture.from(
                            mAdSelectionScriptEngine.generateBids(
                                    buyerDecisionLogicJs,
                                    ads,
                                    adSelectionSignals,
                                    buyerSignals,
                                    trustedBiddingSignals,
                                    contextualSignals,
                                    userSignals,
                                    customAudienceSignals))
                    .transform(
                            adWithBids -> {
                                return getBestAdWithBidPerCA(adWithBids);
                            },
                            mExecutor);
        } catch (JSONException e) {
            return FluentFuture.from(Futures.immediateFailedFuture(e));
        }
    }

    @Nullable
    private AdWithBid getBestAdWithBidPerCA(@NonNull List<AdWithBid> adWithBids) {
        if (adWithBids.size() == 0) return null;
        AdWithBid maxBidCandidate =
                adWithBids.stream().max(Comparator.comparingDouble(AdWithBid::getBid)).get();
        if (maxBidCandidate.getBid() <= 0.0) {
            LogUtil.d("The max bid candidate should have positive bid.");
            return null;
        }
        return maxBidCandidate;
    }
}

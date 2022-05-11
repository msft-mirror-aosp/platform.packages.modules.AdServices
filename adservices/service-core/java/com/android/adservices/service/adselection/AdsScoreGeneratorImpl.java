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

import android.adservices.adselection.AdSelectionConfig;
import android.adservices.adselection.AdWithBid;
import android.adservices.exceptions.AdServicesException;
import android.annotation.NonNull;
import android.net.Uri;

import com.google.common.base.Function;
import com.google.common.util.concurrent.AsyncFunction;
import com.google.common.util.concurrent.FluentFuture;
import com.google.common.util.concurrent.Futures;
import com.google.common.util.concurrent.ListenableFuture;
import com.google.common.util.concurrent.ListeningExecutorService;
import com.google.common.util.concurrent.MoreExecutors;

import org.json.JSONException;

import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.ExecutorService;
import java.util.stream.Collectors;

/**
 * Generates score for Remarketing Ads based on Seller provided scoring logic
 */
public class AdsScoreGeneratorImpl implements AdsScoreGenerator {

    @NonNull
    private final AdSelectionScriptEngine mAdSelectionScriptEngine;
    @NonNull
    private final ListeningExecutorService mListeningExecutorService;
    @NonNull
    private final AdSelectionHttpClient mAdSelectionHttpClient;

    public AdsScoreGeneratorImpl(@NonNull AdSelectionScriptEngine adSelectionScriptEngine,
            @NonNull ExecutorService executor,
            @NonNull AdSelectionHttpClient adSelectionHttpClient) {
        mAdSelectionScriptEngine = adSelectionScriptEngine;
        mListeningExecutorService = MoreExecutors.listeningDecorator(executor);
        mAdSelectionHttpClient = adSelectionHttpClient;
    }

    /**
     * Scoring logic for finding most relevant Ad amongst Remarketing and contextual Ads
     *
     * @param adBiddingOutcomes Remarketing Ads that have been bid
     * @param adSelectionConfig Inputs with seller and buyer signals
     * @return {@link AdScoringOutcome} Ads with respective Score based on seller scoring logic
     */
    @Override
    public FluentFuture<List<AdScoringOutcome>> runAdScoring(
            @NonNull List<AdBiddingOutcome> adBiddingOutcomes,
            @NonNull final AdSelectionConfig adSelectionConfig) throws AdServicesException {

        ListenableFuture<String> scoreAdJs = getAdSelectionLogic(
                adSelectionConfig.getDecisionLogicUrl());

        AsyncFunction<String, List<Double>> getScoresFromLogic =
                adScoringLogic -> {
                    return getAdScores(adScoringLogic, adBiddingOutcomes, adSelectionConfig);
                };

        ListenableFuture<List<Double>> adScores = Futures.transformAsync(scoreAdJs,
                getScoresFromLogic,
                mListeningExecutorService);

        Function<List<Double>, List<AdScoringOutcome>> adsToScore =
                scores -> {
                    return mapAdsToScore(adBiddingOutcomes, scores);
                };

        return FluentFuture.from(adScores)
                .transform(adsToScore, mListeningExecutorService);
    }

    private ListenableFuture<String> getAdSelectionLogic(@NonNull final Uri decisionLogicUri) {
        return mAdSelectionHttpClient.fetchJavascript(decisionLogicUri);
    }

    private ListenableFuture<List<Double>> getAdScores(
            @NonNull String scoringLogic,
            @NonNull List<AdBiddingOutcome> adBiddingOutcomes,
            @NonNull final AdSelectionConfig adSelectionConfig) throws AdServicesException {
        final String sellerSignals = adSelectionConfig.getSellerSignals();
        final String trustedScoringSignals = adSelectionConfig.getAdSelectionSignals();
        final String contextualSignals = adSelectionConfig.getSeller();

        try {
            ListenableFuture<List<Double>> adScores = mAdSelectionScriptEngine.scoreAds(
                    scoringLogic,
                    adBiddingOutcomes.stream().map(a -> a.getAdWithBid())
                            .collect(Collectors.toList()),
                    adSelectionConfig,
                    sellerSignals,
                    getTrustedScoringSignals(adBiddingOutcomes, trustedScoringSignals),
                    contextualSignals,
                    // TODO(b/230432251): align JS logic to use multi CA signals
                    adBiddingOutcomes.get(0)
                            .getCustomAudienceBiddingInfo().getCustomAudienceSignals()
            );
            return adScores;
        } catch (JSONException e) {
            throw new AdServicesException("Invalid results obtained from Ad Scoring");
        }
    }

    private String getTrustedScoringSignals(
            @NonNull final List<AdBiddingOutcome> adBiddingOutcomes,
            @NonNull final String sellerSignals) {
        // TODO(b/230436736): Invoke the server to get trusted Scoring signals
        return "{}";
    }

    /**
     * @param adBiddingOutcomes Ads which have already been through auction process & contextual ads
     * @param adScores scores generated by executing the scoring logic for each Ad with Bid
     * @return {@link AdScoringOutcome} list where each of the input {@link AdWithBid}
     * is associated with its score
     */
    private List<AdScoringOutcome> mapAdsToScore(List<AdBiddingOutcome> adBiddingOutcomes,
            List<Double> adScores) {
        List<AdScoringOutcome> adScoringOutcomes = new ArrayList<>();

        for (int i = 0; i < adScores.size(); i++) {
            final Double score = adScores.get(i);
            final AdWithBid adWithBid = adBiddingOutcomes.get(i).getAdWithBid();
            final AdWithScore adWithScore = AdWithScore.builder()
                    .setScore(score)
                    .setAdWithBid(adWithBid)
                    .build();
            final CustomAudienceBiddingInfo customAudienceBiddingInfo =
                    adBiddingOutcomes.get(i).getCustomAudienceBiddingInfo();

            adScoringOutcomes.add(AdScoringOutcome.builder()
                    .setAdWithScore(adWithScore)
                    .setCustomAudienceBiddingInfo(customAudienceBiddingInfo)
                    .build());
        }
        return adScoringOutcomes;
    }
}

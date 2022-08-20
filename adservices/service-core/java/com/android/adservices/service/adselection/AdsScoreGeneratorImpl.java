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
import android.adservices.common.AdSelectionSignals;
import android.adservices.exceptions.AdServicesException;
import android.annotation.NonNull;
import android.annotation.Nullable;
import android.net.Uri;

import com.android.adservices.LogUtil;
import com.android.adservices.data.adselection.AdSelectionEntryDao;
import com.android.adservices.service.Flags;
import com.android.adservices.service.common.AdServicesHttpsClient;
import com.android.adservices.service.devapi.AdSelectionDevOverridesHelper;
import com.android.adservices.service.devapi.DevContext;
import com.android.internal.annotations.VisibleForTesting;

import com.google.common.base.Function;
import com.google.common.util.concurrent.AsyncFunction;
import com.google.common.util.concurrent.FluentFuture;
import com.google.common.util.concurrent.Futures;
import com.google.common.util.concurrent.ListenableFuture;
import com.google.common.util.concurrent.ListeningExecutorService;
import com.google.common.util.concurrent.UncheckedTimeoutException;

import java.util.ArrayList;
import java.util.List;
import java.util.Objects;
import java.util.concurrent.ScheduledThreadPoolExecutor;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.TimeoutException;
import java.util.stream.Collectors;

/**
 * Generates score for Remarketing Ads based on Seller provided scoring logic A new instance is
 * assumed to be created for every call
 */
public class AdsScoreGeneratorImpl implements AdsScoreGenerator {

    @VisibleForTesting static final String QUERY_PARAM_RENDER_URLS = "renderurls";

    @VisibleForTesting
    static final String MISSING_TRUSTED_SCORING_SIGNALS = "Error fetching trusted scoring signals";

    @VisibleForTesting
    static final String MISSING_SCORING_LOGIC = "Error fetching scoring decision logic";

    @VisibleForTesting
    static final String SCORING_TIMED_OUT = "Scoring exceeded allowed time limit";

    @NonNull private final AdSelectionScriptEngine mAdSelectionScriptEngine;
    @NonNull private final ListeningExecutorService mLightweightExecutorService;
    @NonNull private final ListeningExecutorService mBackgroundExecutorService;
    @NonNull private final AdServicesHttpsClient mAdServicesHttpsClient;
    @NonNull private final AdSelectionDevOverridesHelper mAdSelectionDevOverridesHelper;
    @NonNull private final Flags mFlags;

    public AdsScoreGeneratorImpl(
            @NonNull AdSelectionScriptEngine adSelectionScriptEngine,
            @NonNull ListeningExecutorService lightweightExecutor,
            @NonNull ListeningExecutorService backgroundExecutor,
            @NonNull AdServicesHttpsClient adServicesHttpsClient,
            @NonNull DevContext devContext,
            @NonNull AdSelectionEntryDao adSelectionEntryDao,
            @NonNull Flags flags) {
        Objects.requireNonNull(adSelectionScriptEngine);
        Objects.requireNonNull(lightweightExecutor);
        Objects.requireNonNull(backgroundExecutor);
        Objects.requireNonNull(adServicesHttpsClient);
        Objects.requireNonNull(devContext);
        Objects.requireNonNull(adSelectionEntryDao);
        Objects.requireNonNull(flags);

        mAdSelectionScriptEngine = adSelectionScriptEngine;
        mAdServicesHttpsClient = adServicesHttpsClient;
        mLightweightExecutorService = lightweightExecutor;
        mBackgroundExecutorService = backgroundExecutor;
        mAdSelectionDevOverridesHelper =
                new AdSelectionDevOverridesHelper(devContext, adSelectionEntryDao);
        mFlags = flags;
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
            @NonNull final AdSelectionConfig adSelectionConfig)
            throws AdServicesException {

        LogUtil.v("Starting Ad scoring for #%d bidding outcomes", adBiddingOutcomes.size());
        ListenableFuture<String> scoreAdJs =
                getAdSelectionLogic(adSelectionConfig.getDecisionLogicUri(), adSelectionConfig);

        AsyncFunction<String, List<Double>> getScoresFromLogic =
                adScoringLogic -> {
                    return getAdScores(adScoringLogic, adBiddingOutcomes, adSelectionConfig);
                };

        ListenableFuture<List<Double>> adScores =
                Futures.transformAsync(scoreAdJs, getScoresFromLogic, mLightweightExecutorService);

        Function<List<Double>, List<AdScoringOutcome>> adsToScore =
                scores -> {
                    return mapAdsToScore(adBiddingOutcomes, scores);
                };

        return FluentFuture.from(adScores)
                .transform(adsToScore, mLightweightExecutorService)
                .withTimeout(
                        mFlags.getAdSelectionScoringTimeoutMs(),
                        TimeUnit.MILLISECONDS,
                        // TODO(b/237103033): Comply with thread usage policy for AdServices;
                        //  use a global scheduled executor
                        new ScheduledThreadPoolExecutor(1))
                .catching(
                        TimeoutException.class,
                        this::handleTimeoutError,
                        mLightweightExecutorService);
    }

    @Nullable
    private List<AdScoringOutcome> handleTimeoutError(TimeoutException e) {
        LogUtil.e(e, "Scoring exceeded time limit");
        throw new UncheckedTimeoutException(SCORING_TIMED_OUT);
    }

    private ListenableFuture<String> getAdSelectionLogic(
            @NonNull final Uri decisionLogicUri, @NonNull AdSelectionConfig adSelectionConfig) {
        FluentFuture<String> jsOverrideFuture =
                FluentFuture.from(
                        mBackgroundExecutorService.submit(
                                () ->
                                        mAdSelectionDevOverridesHelper.getDecisionLogicOverride(
                                                adSelectionConfig)));
        return jsOverrideFuture
                .transformAsync(
                        jsOverride -> {
                            if (jsOverride == null) {
                                LogUtil.v("Fetching Ad Scoring Logic from the server");
                                return mAdServicesHttpsClient.fetchPayload(decisionLogicUri);
                            } else {
                                LogUtil.d(
                                        "Developer options enabled and an override JS is provided "
                                                + "for the current ad selection config. "
                                                + "Skipping call to server.");
                                return Futures.immediateFuture(jsOverride);
                            }
                        },
                        mLightweightExecutorService)
                .catching(
                        Exception.class,
                        e -> {
                            LogUtil.e(e, "Exception encountered when fetching scoring logic");
                            throw new IllegalStateException(MISSING_SCORING_LOGIC);
                        },
                        mLightweightExecutorService);
    }

    private ListenableFuture<List<Double>> getAdScores(
            @NonNull String scoringLogic,
            @NonNull List<AdBiddingOutcome> adBiddingOutcomes,
            @NonNull final AdSelectionConfig adSelectionConfig) {
        final AdSelectionSignals sellerSignals = adSelectionConfig.getSellerSignals();
        final FluentFuture<AdSelectionSignals> trustedScoringSignals =
                getTrustedScoringSignals(adSelectionConfig, adBiddingOutcomes);
        final AdSelectionSignals contextualSignals = getContextualSignals();
        ListenableFuture<List<Double>> adScores =
                trustedScoringSignals.transformAsync(
                        trustedSignals -> {
                            LogUtil.v("Invoking JS engine to generate Ad Scores");
                            return mAdSelectionScriptEngine.scoreAds(
                                    scoringLogic,
                                    adBiddingOutcomes.stream()
                                            .map(a -> a.getAdWithBid())
                                            .collect(Collectors.toList()),
                                    adSelectionConfig,
                                    sellerSignals,
                                    trustedSignals,
                                    contextualSignals,
                                    adBiddingOutcomes.stream()
                                            .map(
                                                    a ->
                                                            a.getCustomAudienceBiddingInfo()
                                                                    .getCustomAudienceSignals())
                                            .collect(Collectors.toList()));
                        },
                        mLightweightExecutorService);

        return adScores;
    }

    @VisibleForTesting
    AdSelectionSignals getContextualSignals() {
        // TODO(b/230569187): get the contextualSignal securely = "invoking app name"
        return AdSelectionSignals.EMPTY;
    }

    private FluentFuture<AdSelectionSignals> getTrustedScoringSignals(
            @NonNull final AdSelectionConfig adSelectionConfig,
            @NonNull final List<AdBiddingOutcome> adBiddingOutcomes) {
        final List<String> adRenderUrls =
                adBiddingOutcomes.stream()
                        .map(a -> a.getAdWithBid().getAdData().getRenderUri().toString())
                        .collect(Collectors.toList());
        final String queryParams = String.join(",", adRenderUrls);
        final Uri trustedScoringSignalUri = adSelectionConfig.getTrustedScoringSignalsUri();

        Uri trustedScoringSignalsUri =
                Uri.parse(trustedScoringSignalUri.toString())
                        .buildUpon()
                        .appendQueryParameter(QUERY_PARAM_RENDER_URLS, queryParams)
                        .build();

        FluentFuture<AdSelectionSignals> jsOverrideFuture =
                FluentFuture.from(
                        mBackgroundExecutorService.submit(
                                () ->
                                        mAdSelectionDevOverridesHelper
                                                .getTrustedScoringSignalsOverride(
                                                        adSelectionConfig)));
        return jsOverrideFuture
                .transformAsync(
                        jsOverride -> {
                            if (jsOverride == null) {
                                LogUtil.v("Fetching trusted scoring signals from server");
                                return Futures.transform(
                                        mAdServicesHttpsClient.fetchPayload(
                                                trustedScoringSignalsUri),
                                        s -> s == null ? null : AdSelectionSignals.fromString(s),
                                        mLightweightExecutorService);
                            } else {
                                LogUtil.d(
                                        "Developer options enabled and an override trusted scoring"
                                                + " signals are is provided for the current ad"
                                                + " selection config. Skipping call to server.");
                                return Futures.immediateFuture(jsOverride);
                            }
                        },
                        mLightweightExecutorService)
                .catching(
                        Exception.class,
                        e -> {
                            LogUtil.e(e, "Exception encountered when fetching trusted signals");
                            throw new IllegalStateException(MISSING_TRUSTED_SCORING_SIGNALS);
                        },
                        mLightweightExecutorService);
    }

    /**
     * @param adBiddingOutcomes Ads which have already been through auction process & contextual ads
     * @param adScores scores generated by executing the scoring logic for each Ad with Bid
     * @return {@link AdScoringOutcome} list where each of the input {@link AdWithBid} is associated
     *     with its score
     */
    private List<AdScoringOutcome> mapAdsToScore(
            List<AdBiddingOutcome> adBiddingOutcomes, List<Double> adScores) {
        List<AdScoringOutcome> adScoringOutcomes = new ArrayList<>();
        LogUtil.v(
                "Mapping #%d bidding outcomes to #%d ads with scores",
                adBiddingOutcomes.size(), adScores.size());
        for (int i = 0; i < adScores.size(); i++) {
            final Double score = adScores.get(i);
            final AdWithBid adWithBid = adBiddingOutcomes.get(i).getAdWithBid();
            final AdWithScore adWithScore =
                    AdWithScore.builder().setScore(score).setAdWithBid(adWithBid).build();
            final CustomAudienceBiddingInfo customAudienceBiddingInfo =
                    adBiddingOutcomes.get(i).getCustomAudienceBiddingInfo();

            adScoringOutcomes.add(
                    AdScoringOutcome.builder()
                            .setAdWithScore(adWithScore)
                            .setCustomAudienceBiddingInfo(customAudienceBiddingInfo)
                            .build());
        }
        LogUtil.v("Returning Ad Scoring Outcome");
        return adScoringOutcomes;
    }
}

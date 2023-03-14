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

import static android.adservices.common.AdServicesStatusUtils.STATUS_SUCCESS;

import static com.android.adservices.service.stats.AdServicesLoggerUtil.getResultCodeFromException;

import android.adservices.adselection.AdSelectionConfig;
import android.adservices.adselection.AdWithBid;
import android.adservices.common.AdSelectionSignals;
import android.adservices.exceptions.AdServicesException;
import android.annotation.NonNull;
import android.annotation.Nullable;
import android.net.Uri;

import com.android.adservices.LoggerFactory;
import com.android.adservices.data.adselection.AdSelectionEntryDao;
import com.android.adservices.service.Flags;
import com.android.adservices.service.common.httpclient.AdServicesHttpClientRequest;
import com.android.adservices.service.common.httpclient.AdServicesHttpsClient;
import com.android.adservices.service.devapi.AdSelectionDevOverridesHelper;
import com.android.adservices.service.devapi.DevContext;
import com.android.adservices.service.profiling.Tracing;
import com.android.adservices.service.stats.AdSelectionExecutionLogger;
import com.android.internal.annotations.VisibleForTesting;

import com.google.common.base.Function;
import com.google.common.util.concurrent.AsyncFunction;
import com.google.common.util.concurrent.FluentFuture;
import com.google.common.util.concurrent.Futures;
import com.google.common.util.concurrent.ListenableFuture;
import com.google.common.util.concurrent.ListeningExecutorService;
import com.google.common.util.concurrent.UncheckedTimeoutException;

import org.json.JSONException;

import java.util.ArrayList;
import java.util.List;
import java.util.Objects;
import java.util.concurrent.ScheduledThreadPoolExecutor;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.TimeoutException;
import java.util.stream.Collectors;

/**
 * Generates score for Remarketing Ads based on Seller provided scoring logic.
 *
 * <p>A new instance is assumed to be created for every call.
 */
public class AdsScoreGeneratorImpl implements AdsScoreGenerator {
    private static final LoggerFactory.Logger sLogger = LoggerFactory.getFledgeLogger();

    @VisibleForTesting static final String QUERY_PARAM_RENDER_URIS = "renderuris";

    @VisibleForTesting
    static final String MISSING_TRUSTED_SCORING_SIGNALS = "Error fetching trusted scoring signals";

    @VisibleForTesting
    static final String MISSING_SCORING_LOGIC = "Error fetching scoring decision logic";

    @VisibleForTesting
    static final String SCORING_TIMED_OUT = "Scoring exceeded allowed time limit";

    @NonNull private final AdSelectionScriptEngine mAdSelectionScriptEngine;
    @NonNull private final ListeningExecutorService mLightweightExecutorService;
    @NonNull private final ListeningExecutorService mBackgroundExecutorService;
    @NonNull private final ScheduledThreadPoolExecutor mScheduledExecutor;
    @NonNull private final AdServicesHttpsClient mAdServicesHttpsClient;
    @NonNull private final AdSelectionDevOverridesHelper mAdSelectionDevOverridesHelper;
    @NonNull private final Flags mFlags;
    @NonNull private final AdSelectionExecutionLogger mAdSelectionExecutionLogger;

    public AdsScoreGeneratorImpl(
            @NonNull AdSelectionScriptEngine adSelectionScriptEngine,
            @NonNull ListeningExecutorService lightweightExecutor,
            @NonNull ListeningExecutorService backgroundExecutor,
            @NonNull ScheduledThreadPoolExecutor scheduledExecutor,
            @NonNull AdServicesHttpsClient adServicesHttpsClient,
            @NonNull DevContext devContext,
            @NonNull AdSelectionEntryDao adSelectionEntryDao,
            @NonNull Flags flags,
            @NonNull AdSelectionExecutionLogger adSelectionExecutionLogger) {
        Objects.requireNonNull(adSelectionScriptEngine);
        Objects.requireNonNull(lightweightExecutor);
        Objects.requireNonNull(backgroundExecutor);
        Objects.requireNonNull(scheduledExecutor);
        Objects.requireNonNull(adServicesHttpsClient);
        Objects.requireNonNull(devContext);
        Objects.requireNonNull(adSelectionEntryDao);
        Objects.requireNonNull(flags);
        Objects.requireNonNull(adSelectionExecutionLogger);

        mAdSelectionScriptEngine = adSelectionScriptEngine;
        mAdServicesHttpsClient = adServicesHttpsClient;
        mLightweightExecutorService = lightweightExecutor;
        mBackgroundExecutorService = backgroundExecutor;
        mScheduledExecutor = scheduledExecutor;
        mAdSelectionDevOverridesHelper =
                new AdSelectionDevOverridesHelper(devContext, adSelectionEntryDao);
        mFlags = flags;
        mAdSelectionExecutionLogger = adSelectionExecutionLogger;
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
        sLogger.v("Starting Ad scoring for #%d bidding outcomes", adBiddingOutcomes.size());
        mAdSelectionExecutionLogger.startRunAdScoring(adBiddingOutcomes);
        int traceCookie = Tracing.beginAsyncSection(Tracing.RUN_AD_SCORING);

        ListenableFuture<String> scoreAdJs =
                getAdSelectionLogic(adSelectionConfig.getDecisionLogicUri(), adSelectionConfig);

        AsyncFunction<String, List<Double>> getScoresFromLogic =
                adScoringLogic -> getAdScores(adScoringLogic, adBiddingOutcomes, adSelectionConfig);

        ListenableFuture<List<Double>> adScores =
                Futures.transformAsync(scoreAdJs, getScoresFromLogic, mLightweightExecutorService);

        Function<List<Double>, List<AdScoringOutcome>> adsToScore =
                scores -> mapAdsToScore(adBiddingOutcomes, scores);

        return FluentFuture.from(adScores)
                .transform(adsToScore, mLightweightExecutorService)
                .withTimeout(
                        mFlags.getAdSelectionScoringTimeoutMs(),
                        TimeUnit.MILLISECONDS,
                        mScheduledExecutor)
                .catching(
                        TimeoutException.class,
                        e -> {
                            Tracing.endAsyncSection(Tracing.RUN_AD_SCORING, traceCookie);
                            return handleTimeoutError(e);
                        },
                        mLightweightExecutorService)
                .transform(
                        e -> {
                            Tracing.endAsyncSection(Tracing.RUN_AD_SCORING, traceCookie);
                            return endSuccessfulRunAdScoring(e);
                        },
                        mLightweightExecutorService)
                .catching(
                        RuntimeException.class,
                        e -> {
                            Tracing.endAsyncSection(Tracing.RUN_AD_SCORING, traceCookie);
                            return endFailedRunAdScoringWithRuntimeException(e);
                        },
                        mLightweightExecutorService)
                .catching(
                        AdServicesException.class,
                        e -> {
                            Tracing.endAsyncSection(Tracing.RUN_AD_SCORING, traceCookie);
                            return endFailedRunAdScoringWithAdServicesException(e);
                        },
                        mLightweightExecutorService);
    }

    @NonNull
    private List<AdScoringOutcome> endSuccessfulRunAdScoring(List<AdScoringOutcome> result) {
        mAdSelectionExecutionLogger.endRunAdScoring(STATUS_SUCCESS);
        return result;
    }

    @Nullable
    private List<AdScoringOutcome> endFailedRunAdScoringWithRuntimeException(RuntimeException e) {
        mAdSelectionExecutionLogger.endRunAdScoring(getResultCodeFromException(e));
        throw e;
    }

    @Nullable
    private List<AdScoringOutcome> endFailedRunAdScoringWithAdServicesException(
            AdServicesException e) {
        mAdSelectionExecutionLogger.endRunAdScoring(getResultCodeFromException(e));
        throw new RuntimeException(e.getMessage(), e.getCause());
    }

    @Nullable
    private List<AdScoringOutcome> handleTimeoutError(TimeoutException e) {
        sLogger.e(e, SCORING_TIMED_OUT);
        // DO NOT SUBMIT: Do we need to end tracing here as well?
        throw new UncheckedTimeoutException(SCORING_TIMED_OUT);
    }

    private ListenableFuture<String> getAdSelectionLogic(
            @NonNull final Uri decisionLogicUri, @NonNull AdSelectionConfig adSelectionConfig) {
        mAdSelectionExecutionLogger.startGetAdSelectionLogic();
        int traceCookie = Tracing.beginAsyncSection(Tracing.GET_AD_SELECTION_LOGIC);
        FluentFuture<String> jsOverrideFuture =
                FluentFuture.from(
                        mBackgroundExecutorService.submit(
                                () ->
                                        mAdSelectionDevOverridesHelper.getDecisionLogicOverride(
                                                adSelectionConfig)));
        AdServicesHttpClientRequest jsRequest =
                AdServicesHttpClientRequest.builder()
                        .setUri(decisionLogicUri)
                        .setUseCache(mFlags.getFledgeHttpJsCachingEnabled())
                        .build();
        return jsOverrideFuture
                .transformAsync(
                        jsOverride -> {
                            if (jsOverride == null) {
                                sLogger.v("Fetching Ad Scoring Logic from the server");
                                return FluentFuture.from(
                                                mAdServicesHttpsClient.fetchPayload(jsRequest))
                                        .transform(
                                                response -> response.getResponseBody(),
                                                mLightweightExecutorService);
                            } else {
                                sLogger.d(
                                        "Developer options enabled and an override JS is provided "
                                                + "for the current ad selection config. "
                                                + "Skipping call to server.");
                                return Futures.immediateFuture(jsOverride);
                            }
                        },
                        mLightweightExecutorService)
                .transform(
                        input -> {
                            Tracing.endAsyncSection(Tracing.GET_AD_SELECTION_LOGIC, traceCookie);
                            return input;
                        },
                        mLightweightExecutorService)
                .transform(this::endSuccessfulGetAdSelectionLogic, mLightweightExecutorService)
                .catching(
                        Exception.class,
                        e -> {
                            Tracing.endAsyncSection(Tracing.GET_AD_SELECTION_LOGIC, traceCookie);
                            sLogger.e(e, "Exception encountered when fetching scoring logic");
                            throw new IllegalStateException(MISSING_SCORING_LOGIC);
                        },
                        mLightweightExecutorService);
    }

    @NonNull
    private String endSuccessfulGetAdSelectionLogic(@NonNull String adSelectionLogic) {
        Objects.requireNonNull(adSelectionLogic);
        mAdSelectionExecutionLogger.endGetAdSelectionLogic(adSelectionLogic);
        return adSelectionLogic;
    }

    private ListenableFuture<List<Double>> getAdScores(
            @NonNull String scoringLogic,
            @NonNull List<AdBiddingOutcome> adBiddingOutcomes,
            @NonNull final AdSelectionConfig adSelectionConfig) {
        mAdSelectionExecutionLogger.startGetAdScores();
        final AdSelectionSignals sellerSignals = adSelectionConfig.getSellerSignals();
        final FluentFuture<AdSelectionSignals> trustedScoringSignals =
                getTrustedScoringSignals(adSelectionConfig, adBiddingOutcomes);
        final AdSelectionSignals contextualSignals = getContextualSignals();
        int traceCookie = Tracing.beginAsyncSection(Tracing.SCORE_AD);
        FluentFuture<List<Double>> adScores =
                trustedScoringSignals.transformAsync(
                        trustedSignals -> {
                            sLogger.v("Invoking JS engine to generate Ad Scores");
                            return mAdSelectionScriptEngine.scoreAds(
                                    scoringLogic,
                                    adBiddingOutcomes.stream()
                                            .map(AdBiddingOutcome::getAdWithBid)
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
                                            .collect(Collectors.toList()),
                                    mAdSelectionExecutionLogger);
                        },
                        mLightweightExecutorService);
        return adScores.transform(
                        result -> {
                            Tracing.endAsyncSection(Tracing.SCORE_AD, traceCookie);
                            return endSuccessfulGetAdScores(result);
                        },
                        mLightweightExecutorService)
                .catching(
                        JSONException.class,
                        e -> {
                            Tracing.endAsyncSection(Tracing.SCORE_AD, traceCookie);
                            return handleJSONException(e);
                        },
                        mLightweightExecutorService);
    }

    @Nullable
    private List<Double> handleJSONException(JSONException e) {
        IllegalArgumentException exception =
                new IllegalArgumentException(e.getMessage(), e.getCause());
        throw exception;
    }

    @NonNull
    private List<Double> endSuccessfulGetAdScores(List<Double> result) {
        mAdSelectionExecutionLogger.endGetAdScores();
        return result;
    }

    @VisibleForTesting
    AdSelectionSignals getContextualSignals() {
        // TODO(b/230569187): get the contextualSignal securely = "invoking app name"
        return AdSelectionSignals.EMPTY;
    }

    private FluentFuture<AdSelectionSignals> getTrustedScoringSignals(
            @NonNull final AdSelectionConfig adSelectionConfig,
            @NonNull final List<AdBiddingOutcome> adBiddingOutcomes) {
        mAdSelectionExecutionLogger.startGetTrustedScoringSignals();
        int traceCookie = Tracing.beginAsyncSection(Tracing.GET_TRUSTED_SCORING_SIGNALS);
        final List<String> adRenderUris =
                adBiddingOutcomes.stream()
                        .map(a -> a.getAdWithBid().getAdData().getRenderUri().toString())
                        .collect(Collectors.toList());
        final String queryParams = String.join(",", adRenderUris);
        final Uri trustedScoringSignalUri = adSelectionConfig.getTrustedScoringSignalsUri();

        Uri trustedScoringSignalsUri =
                Uri.parse(trustedScoringSignalUri.toString())
                        .buildUpon()
                        .appendQueryParameter(QUERY_PARAM_RENDER_URIS, queryParams)
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
                                sLogger.v("Fetching trusted scoring signals from server");
                                return Futures.transform(
                                        mAdServicesHttpsClient.fetchPayload(
                                                trustedScoringSignalsUri),
                                        s ->
                                                s == null
                                                        ? null
                                                        : AdSelectionSignals.fromString(
                                                                s.getResponseBody()),
                                        mLightweightExecutorService);
                            } else {
                                sLogger.d(
                                        "Developer options enabled and an override trusted scoring"
                                                + " signals are is provided for the current ad"
                                                + " selection config. Skipping call to server.");
                                return Futures.immediateFuture(jsOverride);
                            }
                        },
                        mLightweightExecutorService)
                .transform(
                        input -> {
                            Tracing.endAsyncSection(
                                    Tracing.GET_TRUSTED_SCORING_SIGNALS, traceCookie);
                            return endGetSuccessfulTrustedScoringSignals(input);
                        },
                        mLightweightExecutorService)
                .catching(
                        Exception.class,
                        e -> {
                            Tracing.endAsyncSection(
                                    Tracing.GET_TRUSTED_SCORING_SIGNALS, traceCookie);
                            sLogger.e(e, "Exception encountered when fetching trusted signals");
                            throw new IllegalStateException(MISSING_TRUSTED_SCORING_SIGNALS);
                        },
                        mLightweightExecutorService);
    }

    @NonNull
    private AdSelectionSignals endGetSuccessfulTrustedScoringSignals(
            AdSelectionSignals adSelectionSignals) {
        mAdSelectionExecutionLogger.endGetTrustedScoringSignals(adSelectionSignals);
        return adSelectionSignals;
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
        sLogger.v(
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
        sLogger.v("Returning Ad Scoring Outcome");
        return adScoringOutcomes;
    }
}

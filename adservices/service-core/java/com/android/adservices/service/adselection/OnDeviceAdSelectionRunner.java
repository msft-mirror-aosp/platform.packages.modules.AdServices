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
import android.adservices.common.AdSelectionSignals;
import android.adservices.exceptions.AdServicesException;
import android.annotation.NonNull;
import android.annotation.SuppressLint;
import android.content.Context;
import android.util.Pair;

import com.android.adservices.LogUtil;
import com.android.adservices.data.adselection.AdSelectionEntryDao;
import com.android.adservices.data.adselection.DBAdSelection;
import com.android.adservices.data.customaudience.CustomAudienceDao;
import com.android.adservices.data.customaudience.DBCustomAudience;
import com.android.adservices.service.Flags;
import com.android.adservices.service.common.AdServicesHttpsClient;
import com.android.adservices.service.common.AppImportanceFilter;
import com.android.adservices.service.common.FledgeAllowListsFilter;
import com.android.adservices.service.common.FledgeAuthorizationFilter;
import com.android.adservices.service.common.Throttler;
import com.android.adservices.service.consent.ConsentManager;
import com.android.adservices.service.devapi.DevContext;
import com.android.adservices.service.stats.AdServicesLogger;
import com.android.internal.annotations.VisibleForTesting;

import com.google.common.base.Function;
import com.google.common.util.concurrent.AsyncFunction;
import com.google.common.util.concurrent.Futures;
import com.google.common.util.concurrent.ListenableFuture;

import java.time.Clock;
import java.util.List;
import java.util.Objects;
import java.util.Optional;
import java.util.concurrent.ExecutionException;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.ForkJoinPool;
import java.util.concurrent.atomic.AtomicReference;
import java.util.function.Supplier;
import java.util.stream.Collectors;

/** Orchestrate on-device ad selection. */
public class OnDeviceAdSelectionRunner extends AdSelectionRunner {
    @NonNull protected final AdsScoreGenerator mAdsScoreGenerator;
    @NonNull protected final AdServicesHttpsClient mAdServicesHttpsClient;
    @NonNull protected final AdBidGenerator mAdBidGenerator;

    public OnDeviceAdSelectionRunner(
            @NonNull final Context context,
            @NonNull final CustomAudienceDao customAudienceDao,
            @NonNull final AdSelectionEntryDao adSelectionEntryDao,
            @NonNull final AdServicesHttpsClient adServicesHttpsClient,
            @NonNull final ExecutorService lightweightExecutorService,
            @NonNull final ExecutorService backgroundExecutorService,
            @NonNull final ConsentManager consentManager,
            @NonNull final AdServicesLogger adServicesLogger,
            @NonNull final DevContext devContext,
            @NonNull AppImportanceFilter appImportanceFilter,
            @NonNull final Flags flags,
            @NonNull final Supplier<Throttler> throttlerSupplier,
            int callerUid,
            @NonNull final FledgeAuthorizationFilter fledgeAuthorizationFilter,
            @NonNull final FledgeAllowListsFilter fledgeAllowListsFilter) {
        super(
                context,
                customAudienceDao,
                adSelectionEntryDao,
                lightweightExecutorService,
                backgroundExecutorService,
                consentManager,
                adServicesLogger,
                devContext,
                appImportanceFilter,
                flags,
                throttlerSupplier,
                callerUid,
                fledgeAuthorizationFilter,
                fledgeAllowListsFilter);

        Objects.requireNonNull(adServicesHttpsClient);

        mAdServicesHttpsClient = adServicesHttpsClient;
        mAdBidGenerator =
                new AdBidGeneratorImpl(
                        context,
                        mAdServicesHttpsClient,
                        mLightweightExecutorService,
                        mBackgroundExecutorService,
                        devContext,
                        mCustomAudienceDao,
                        flags);
        mAdsScoreGenerator =
                new AdsScoreGeneratorImpl(
                        new AdSelectionScriptEngine(
                                mContext,
                                () -> flags.getEnforceIsolateMaxHeapSize(),
                                () -> flags.getIsolateMaxHeapSizeBytes()),
                        mLightweightExecutorService,
                        mBackgroundExecutorService,
                        mAdServicesHttpsClient,
                        devContext,
                        mAdSelectionEntryDao,
                        flags);
    }

    @VisibleForTesting
    OnDeviceAdSelectionRunner(
            @NonNull final Context context,
            @NonNull final CustomAudienceDao customAudienceDao,
            @NonNull final AdSelectionEntryDao adSelectionEntryDao,
            @NonNull final AdServicesHttpsClient adServicesHttpsClient,
            @NonNull final ExecutorService lightweightExecutorService,
            @NonNull final ExecutorService backgroundExecutorService,
            @NonNull final ConsentManager consentManager,
            @NonNull final AdsScoreGenerator adsScoreGenerator,
            @NonNull final AdBidGenerator adBidGenerator,
            @NonNull final AdSelectionIdGenerator adSelectionIdGenerator,
            @NonNull Clock clock,
            @NonNull final AdServicesLogger adServicesLogger,
            @NonNull AppImportanceFilter appImportanceFilter,
            @NonNull final Flags flags,
            @NonNull final Supplier<Throttler> throttlerSupplier,
            int callerUid,
            @NonNull final FledgeAuthorizationFilter fledgeAuthorizationFilter,
            @NonNull final FledgeAllowListsFilter fledgeAllowListsFilter) {
        super(
                context,
                customAudienceDao,
                adSelectionEntryDao,
                lightweightExecutorService,
                backgroundExecutorService,
                consentManager,
                adSelectionIdGenerator,
                clock,
                adServicesLogger,
                appImportanceFilter,
                flags,
                throttlerSupplier,
                callerUid,
                fledgeAuthorizationFilter,
                fledgeAllowListsFilter);

        Objects.requireNonNull(adsScoreGenerator);
        Objects.requireNonNull(adServicesHttpsClient);
        Objects.requireNonNull(adBidGenerator);

        mAdsScoreGenerator = adsScoreGenerator;
        mAdServicesHttpsClient = adServicesHttpsClient;
        mAdBidGenerator = adBidGenerator;
    }

    /**
     * Orchestrate on device ad selection.
     *
     * @param adSelectionConfig Set of data from Sellers and Buyers needed for Ad Auction and
     *     Selection
     */
    public ListenableFuture<AdSelectionOrchestrationResult> orchestrateAdSelection(
            @NonNull final AdSelectionConfig adSelectionConfig,
            @NonNull final String callerPackageName,
            ListenableFuture<List<DBCustomAudience>> buyerCustomAudience) {
        AsyncFunction<List<DBCustomAudience>, List<AdBiddingOutcome>> bidAds =
                buyerCAs -> {
                    return runAdBidding(buyerCAs, adSelectionConfig);
                };

        ListenableFuture<List<AdBiddingOutcome>> biddingOutcome =
                Futures.transformAsync(buyerCustomAudience, bidAds, mLightweightExecutorService);

        AsyncFunction<List<AdBiddingOutcome>, List<AdScoringOutcome>> mapBidsToScores =
                bids -> {
                    return runAdScoring(bids, adSelectionConfig);
                };

        ListenableFuture<List<AdScoringOutcome>> scoredAds =
                Futures.transformAsync(
                        biddingOutcome, mapBidsToScores, mLightweightExecutorService);

        Function<List<AdScoringOutcome>, AdScoringOutcome> reduceScoresToWinner =
                scores -> {
                    return getWinningOutcome(scores);
                };

        ListenableFuture<AdScoringOutcome> winningOutcome =
                Futures.transform(scoredAds, reduceScoresToWinner, mLightweightExecutorService);

        Function<AdScoringOutcome, AdSelectionOrchestrationResult> mapWinnerToDBResult =
                scoringWinner -> {
                    return createAdSelectionResult(scoringWinner);
                };

        ListenableFuture<AdSelectionOrchestrationResult> dbAdSelectionBuilder =
                Futures.transform(winningOutcome, mapWinnerToDBResult, mLightweightExecutorService);

        return dbAdSelectionBuilder;
    }

    private ListenableFuture<List<AdBiddingOutcome>> runAdBidding(
            @NonNull final List<DBCustomAudience> customAudiences,
            @NonNull final AdSelectionConfig adSelectionConfig)
            throws InterruptedException, ExecutionException {
        if (customAudiences.isEmpty()) {
            LogUtil.w("Cannot invoke bidding on empty list of CAs");
            return Futures.immediateFailedFuture(new Throwable("No CAs found for selection"));
        }

        // TODO(b/237004875) : Use common thread pool for parallel execution if possible
        ForkJoinPool customThreadPool = new ForkJoinPool(getParallelBiddingCount());
        final AtomicReference<List<ListenableFuture<AdBiddingOutcome>>> bidWinningAds =
                new AtomicReference<>();

        try {
            LogUtil.d("Triggering bidding for all %d custom audiences", customAudiences.size());
            customThreadPool
                    .submit(
                            () -> {
                                LogUtil.v("Invoking bidding for #%d CAs", customAudiences.size());
                                bidWinningAds.set(
                                        customAudiences.parallelStream()
                                                .map(
                                                        customAudience -> {
                                                            return runAdBiddingPerCA(
                                                                    customAudience,
                                                                    adSelectionConfig);
                                                        })
                                                .collect(Collectors.toList()));
                            })
                    .get();
        } catch (InterruptedException e) {
            final String exceptionReason = "Bidding Interrupted Exception";
            LogUtil.e(e, exceptionReason);
            throw new InterruptedException(exceptionReason);
        } catch (ExecutionException e) {
            final String exceptionReason = "Bidding Execution Exception";
            LogUtil.e(e, exceptionReason);
            throw new ExecutionException(e.getCause());
        } finally {
            customThreadPool.shutdownNow();
        }
        return Futures.successfulAsList(bidWinningAds.get());
    }

    private ListenableFuture<AdBiddingOutcome> runAdBiddingPerCA(
            @NonNull final DBCustomAudience customAudience,
            @NonNull final AdSelectionConfig adSelectionConfig) {
        LogUtil.v(String.format("Invoking bidding for CA: %s", customAudience.getName()));

        // TODO(b/233239475) : Validate Buyer signals in Ad Selection Config
        AdSelectionSignals buyerSignal =
                Optional.ofNullable(
                                adSelectionConfig
                                        .getPerBuyerSignals()
                                        .get(customAudience.getBuyer()))
                        .orElse(AdSelectionSignals.EMPTY);
        return mAdBidGenerator.runAdBiddingPerCA(
                customAudience,
                adSelectionConfig.getAdSelectionSignals(),
                buyerSignal,
                AdSelectionSignals.EMPTY,
                adSelectionConfig);
        // TODO(b/230569187): get the contextualSignal securely = "invoking app name"
    }

    @SuppressLint("DefaultLocale")
    private ListenableFuture<List<AdScoringOutcome>> runAdScoring(
            @NonNull final List<AdBiddingOutcome> adBiddingOutcomes,
            @NonNull final AdSelectionConfig adSelectionConfig)
            throws AdServicesException {
        LogUtil.v("Got %d bidding outcomes", adBiddingOutcomes.size());
        List<AdBiddingOutcome> validBiddingOutcomes =
                adBiddingOutcomes.stream().filter(Objects::nonNull).collect(Collectors.toList());

        if (validBiddingOutcomes.isEmpty()) {
            LogUtil.w("Received empty list of Bidding outcomes");
            throw new IllegalStateException(ERROR_NO_VALID_BIDS_FOR_SCORING);
        }
        return mAdsScoreGenerator.runAdScoring(validBiddingOutcomes, adSelectionConfig);
    }

    private AdScoringOutcome getWinningOutcome(
            @NonNull List<AdScoringOutcome> overallAdScoringOutcome) {
        LogUtil.v("Scoring completed, generating winning outcome");
        return overallAdScoringOutcome.stream()
                .filter(a -> a.getAdWithScore().getScore() > 0)
                .max(
                        (a, b) ->
                                Double.compare(
                                        a.getAdWithScore().getScore(),
                                        b.getAdWithScore().getScore()))
                .orElseThrow(() -> new IllegalStateException(ERROR_NO_WINNING_AD_FOUND));
    }

    /**
     * This method populates an Ad Selection result ready to be persisted in DB, with all the fields
     * except adSelectionId and creation time, which should be created as close as possible to
     * persistence logic
     *
     * @param scoringWinner Winning Ad for overall Ad Selection
     * @return A {@link Pair} with a Builder for {@link DBAdSelection} populated with necessary data
     *     and a string containing the JS with the decision logic from this buyer.
     */
    @VisibleForTesting
    AdSelectionOrchestrationResult createAdSelectionResult(
            @NonNull AdScoringOutcome scoringWinner) {
        DBAdSelection.Builder dbAdSelectionBuilder = new DBAdSelection.Builder();
        LogUtil.v("Creating Ad Selection result from scoring winner");
        dbAdSelectionBuilder
                .setWinningAdBid(scoringWinner.getAdWithScore().getAdWithBid().getBid())
                .setCustomAudienceSignals(
                        scoringWinner.getCustomAudienceBiddingInfo().getCustomAudienceSignals())
                .setWinningAdRenderUri(
                        scoringWinner.getAdWithScore().getAdWithBid().getAdData().getRenderUri())
                .setBiddingLogicUri(
                        scoringWinner.getCustomAudienceBiddingInfo().getBiddingLogicUri())
                .setContextualSignals("{}");
        // TODO(b/230569187): get the contextualSignal securely = "invoking app name"
        return new AdSelectionOrchestrationResult(
                dbAdSelectionBuilder,
                scoringWinner.getCustomAudienceBiddingInfo().getBuyerDecisionLogicJs());
    }

    private int getParallelBiddingCount() {
        int parallelBiddingCountConfigValue = mFlags.getAdSelectionConcurrentBiddingCount();
        int numberOfAvailableProcessors = Runtime.getRuntime().availableProcessors();
        return Math.min(parallelBiddingCountConfigValue, numberOfAvailableProcessors);
    }
}

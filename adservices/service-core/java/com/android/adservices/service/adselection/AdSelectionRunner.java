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

import static com.android.adservices.service.stats.AdServicesStatsLog.AD_SERVICES_API_CALLED__API_NAME__RUN_AD_SELECTION;

import android.adservices.adselection.AdSelectionCallback;
import android.adservices.adselection.AdSelectionConfig;
import android.adservices.adselection.AdSelectionResponse;
import android.adservices.common.AdServicesStatusUtils;
import android.adservices.common.FledgeErrorResponse;
import android.adservices.exceptions.AdServicesException;
import android.annotation.NonNull;
import android.content.Context;
import android.os.RemoteException;
import android.util.Pair;

import com.android.adservices.LogUtil;
import com.android.adservices.data.adselection.AdSelectionEntryDao;
import com.android.adservices.data.adselection.DBAdSelection;
import com.android.adservices.data.adselection.DBBuyerDecisionLogic;
import com.android.adservices.data.customaudience.CustomAudienceDao;
import com.android.adservices.data.customaudience.DBCustomAudience;
import com.android.adservices.service.stats.AdServicesLogger;
import com.android.internal.annotations.VisibleForTesting;

import com.google.common.base.Function;
import com.google.common.base.Preconditions;
import com.google.common.util.concurrent.AsyncFunction;
import com.google.common.util.concurrent.FutureCallback;
import com.google.common.util.concurrent.Futures;
import com.google.common.util.concurrent.ListenableFuture;
import com.google.common.util.concurrent.ListeningExecutorService;
import com.google.common.util.concurrent.MoreExecutors;

import java.time.Clock;
import java.util.ArrayList;
import java.util.List;
import java.util.Objects;
import java.util.Optional;
import java.util.concurrent.ExecutorService;
import java.util.stream.Collectors;

/**
 * Orchestrator that runs the Ads Auction/Bidding and Scoring logic The class expects the caller to
 * create a concrete object instance of the class. The instances are mutually exclusive and do not
 * share any values across shared class instance.
 *
 * <p>Class takes in an executor on which it runs the AdSelection logic
 */
public final class AdSelectionRunner {
    private static final String TAG = AdSelectionRunner.class.getName();

    @NonNull private final Context mContext;
    @NonNull private final CustomAudienceDao mCustomAudienceDao;
    @NonNull private final AdSelectionEntryDao mAdSelectionEntryDao;
    @NonNull private final ExecutorService mExecutorService;
    @NonNull private final AdsScoreGenerator mAdsScoreGenerator;
    @NonNull private final AdBidGenerator mAdBidGenerator;
    @NonNull private final AdSelectionIdGenerator mAdSelectionIdGenerator;
    @NonNull private final Clock mClock;
    @NonNull private final AdServicesLogger mAdServicesLogger;

    public AdSelectionRunner(
            @NonNull final Context context,
            @NonNull final CustomAudienceDao customAudienceDao,
            @NonNull final AdSelectionEntryDao adSelectionEntryDao,
            @NonNull final ExecutorService executorService,
            @NonNull final AdServicesLogger adServicesLogger) {
        mContext = context;
        mCustomAudienceDao = customAudienceDao;
        mAdSelectionEntryDao = adSelectionEntryDao;
        mExecutorService = executorService;
        mAdServicesLogger = adServicesLogger;
        mAdsScoreGenerator =
                new AdsScoreGeneratorImpl(
                        new AdSelectionScriptEngine(mContext),
                        mExecutorService,
                        new AdSelectionHttpClient(mExecutorService));
        mAdBidGenerator = new AdBidGeneratorImpl(context, mExecutorService);
        mAdSelectionIdGenerator = new AdSelectionIdGenerator();
        mClock = Clock.systemUTC();
    }

    @VisibleForTesting
    AdSelectionRunner(
            @NonNull final Context context,
            @NonNull final CustomAudienceDao customAudienceDao,
            @NonNull final AdSelectionEntryDao adSelectionEntryDao,
            @NonNull final ExecutorService executorService,
            @NonNull final AdsScoreGenerator adsScoreGenerator,
            @NonNull final AdBidGenerator adBidGenerator,
            @NonNull final AdSelectionIdGenerator adSelectionIdGenerator,
            @NonNull Clock clock,
            @NonNull final AdServicesLogger adServicesLogger) {
        mContext = context;
        mCustomAudienceDao = customAudienceDao;
        mAdSelectionEntryDao = adSelectionEntryDao;
        mExecutorService = executorService;
        mAdsScoreGenerator = adsScoreGenerator;
        mAdBidGenerator = adBidGenerator;
        mAdSelectionIdGenerator = adSelectionIdGenerator;
        mClock = clock;
        mAdServicesLogger = adServicesLogger;
    }

    /**
     * Runs the ad selection for a given seller
     *
     * @param adSelectionConfig set of signals and other bidding and scoring information provided by
     *     the seller
     * @param callback used to notify the result back to the calling seller
     */
    public void runAdSelection(
            @NonNull AdSelectionConfig adSelectionConfig, @NonNull AdSelectionCallback callback) {
        Objects.requireNonNull(adSelectionConfig);
        Objects.requireNonNull(callback);
        try {
            ListenableFuture<DBAdSelection> dbAdSelectionFuture =
                    orchestrateAdSelection(adSelectionConfig);

            Futures.addCallback(
                    dbAdSelectionFuture,
                    new FutureCallback<DBAdSelection>() {
                        @Override
                        public void onSuccess(DBAdSelection result) {
                            notifySuccessToCaller(result, callback);
                        }

                        @Override
                        public void onFailure(Throwable t) {
                            notifyFailureToCaller(callback, t);
                        }
                    },
                    mExecutorService);
        } catch (Throwable t) {
            notifyFailureToCaller(callback, t);
        }
    }

    private void notifySuccessToCaller(
            @NonNull DBAdSelection result, @NonNull AdSelectionCallback callback) {
        int resultCode = AdServicesStatusUtils.STATUS_UNSET;
        try {
            callback.onSuccess(
                    new AdSelectionResponse.Builder()
                            .setAdSelectionId(result.getAdSelectionId())
                            .setRenderUrl(result.getWinningAdRenderUrl())
                            .build());
            resultCode = AdServicesStatusUtils.STATUS_SUCCESS;
        } catch (RemoteException e) {
            LogUtil.e("Encountered exception during " + "notifying AdSelection callback", e);
            resultCode = AdServicesStatusUtils.STATUS_UNKNOWN_ERROR;
        } finally {
            // TODO(b/233681870): Investigate implementation of actual failures in
            //  logs/metrics
            mAdServicesLogger.logFledgeApiCallStats(
                    AD_SERVICES_API_CALLED__API_NAME__RUN_AD_SELECTION, resultCode);
        }
    }

    private void notifyFailureToCaller(
            @NonNull AdSelectionCallback callback, @NonNull Throwable t) {
        int resultCode = AdServicesStatusUtils.STATUS_UNSET;
        try {
            resultCode = AdServicesStatusUtils.STATUS_INTERNAL_ERROR;
            FledgeErrorResponse selectionFailureResponse =
                    new FledgeErrorResponse.Builder()
                            .setErrorMessage("Encountered failure during Ad Selection")
                            .setStatusCode(resultCode)
                            .build();
            LogUtil.e(t, "Ad Selection failure: ");
            callback.onFailure(selectionFailureResponse);
        } catch (RemoteException e) {
            LogUtil.e("Encountered exception during " + "notifying AdSelection callback", e);
            resultCode = AdServicesStatusUtils.STATUS_UNKNOWN_ERROR;
        } finally {
            mAdServicesLogger.logFledgeApiCallStats(
                    AD_SERVICES_API_CALLED__API_NAME__RUN_AD_SELECTION, resultCode);
        }
    }

    /**
     * Overall moderator for running Ad Selection
     *
     * @param adSelectionConfig Set of data from Sellers and Buyers needed for Ad Auction and
     *     Selection
     * @return {@link AdSelectionResponse}
     */
    private ListenableFuture<DBAdSelection> orchestrateAdSelection(
            @NonNull final AdSelectionConfig adSelectionConfig) {

        List<String> buyers = adSelectionConfig.getCustomAudienceBuyers();
        Preconditions.checkArgument(
                !buyers.isEmpty(), "The list of the custom audience buyers should not be empty.");
        List<DBCustomAudience> buyerCustomAudience = getBuyerCustomAudience(buyers);
        if (buyerCustomAudience == null || buyerCustomAudience.isEmpty()) {
            return Futures.immediateFailedFuture(
                    new IllegalStateException(
                            "No Custom Audience available for the given list of buyers."));
        }
        ListenableFuture<List<AdBiddingOutcome>> biddingOutcome =
                runAdBidding(buyerCustomAudience, adSelectionConfig);

        AsyncFunction<List<AdBiddingOutcome>, List<AdScoringOutcome>> mapBidsToScores =
                bids -> {
                    return runAdScoring(bids, adSelectionConfig);
                };

        ListenableFuture<List<AdScoringOutcome>> scoredAds =
                Futures.transformAsync(biddingOutcome, mapBidsToScores, mExecutorService);

        Function<List<AdScoringOutcome>, AdScoringOutcome> reduceScoresToWinner =
                scores -> {
                    return getWinningOutcome(scores);
                };

        ListenableFuture<AdScoringOutcome> winningOutcome =
                Futures.transform(scoredAds, reduceScoresToWinner, mExecutorService);

        Function<AdScoringOutcome, Pair<DBAdSelection.Builder, String>> mapWinnerToDBResult =
                scoringWinner -> {
                    return createAdSelectionResult(scoringWinner);
                };

        ListenableFuture<Pair<DBAdSelection.Builder, String>> dbAdSelectionBuilder =
                Futures.transform(winningOutcome, mapWinnerToDBResult, mExecutorService);

        AsyncFunction<Pair<DBAdSelection.Builder, String>, DBAdSelection> saveResultToPersistence =
                adSelectionAndJs -> {
                    return persistAdSelection(adSelectionAndJs.first, adSelectionAndJs.second);
                };

        return Futures.transformAsync(
                dbAdSelectionBuilder, saveResultToPersistence, mExecutorService);
    }

    private List<DBCustomAudience> getBuyerCustomAudience(@NonNull final List<String> buyers) {
        return mCustomAudienceDao.getCustomAudienceByBuyers(buyers);
    }

    private ListenableFuture<List<AdBiddingOutcome>> runAdBidding(
            @NonNull final List<DBCustomAudience> customAudiences,
            @NonNull final AdSelectionConfig adSelectionConfig) {
        if (customAudiences.isEmpty()) {
            return Futures.immediateFailedFuture(new Throwable("No CAs found for selection"));
        }

        List<ListenableFuture<AdBiddingOutcome>> bidWinningAds = new ArrayList<>();
        for (DBCustomAudience customAudience : customAudiences) {
            bidWinningAds.add(runAdBiddingPerCA(customAudience, adSelectionConfig));
        }

        return Futures.successfulAsList(bidWinningAds);
    }

    private ListenableFuture<AdBiddingOutcome> runAdBiddingPerCA(
            @NonNull final DBCustomAudience customAudience,
            @NonNull final AdSelectionConfig adSelectionConfig) {

        // TODO(b/233239475) : Validate Buyer signals in Ad Selection Config
        String buyerSignal =
                Optional.ofNullable(
                                adSelectionConfig
                                        .getPerBuyerSignals()
                                        .get(customAudience.getBuyer()))
                        .orElse("{}");
        return mAdBidGenerator.runAdBiddingPerCA(
                customAudience, adSelectionConfig.getAdSelectionSignals(), buyerSignal, "{}");
        // TODO(b/230569187): get the contextualSignal securely = "invoking app name"
    }

    private ListenableFuture<List<AdScoringOutcome>> runAdScoring(
            @NonNull final List<AdBiddingOutcome> adBiddingOutcomes,
            @NonNull final AdSelectionConfig adSelectionConfig)
            throws AdServicesException {
        List<AdBiddingOutcome> validBiddingOutcomes =
                adBiddingOutcomes.stream().filter(Objects::nonNull).collect(Collectors.toList());

        if (validBiddingOutcomes.isEmpty()) {
            throw new IllegalStateException("No valid bids for scoring");
        }
        return mAdsScoreGenerator.runAdScoring(validBiddingOutcomes, adSelectionConfig);
    }

    private AdScoringOutcome getWinningOutcome(
            @NonNull List<AdScoringOutcome> overallAdScoringOutcome) {
        return overallAdScoringOutcome.stream()
                .filter(a -> a.getAdWithScore().getScore() > 0)
                .max(
                        (a, b) ->
                                Double.compare(
                                        a.getAdWithScore().getScore(),
                                        b.getAdWithScore().getScore()))
                .orElseThrow(() -> new IllegalStateException("No winning Ad found"));
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
    Pair<DBAdSelection.Builder, String> createAdSelectionResult(
            @NonNull AdScoringOutcome scoringWinner) {
        DBAdSelection.Builder dbAdSelectionBuilder = new DBAdSelection.Builder();

        dbAdSelectionBuilder
                .setWinningAdBid(scoringWinner.getAdWithScore().getAdWithBid().getBid())
                .setCustomAudienceSignals(
                        scoringWinner.getCustomAudienceBiddingInfo().getCustomAudienceSignals())
                .setWinningAdRenderUrl(
                        scoringWinner.getAdWithScore().getAdWithBid().getAdData().getRenderUrl())
                .setBiddingLogicUrl(
                        scoringWinner.getCustomAudienceBiddingInfo().getBiddingLogicUrl())
                .setContextualSignals("{}");
        // TODO(b/230569187): get the contextualSignal securely = "invoking app name"
        return Pair.create(
                dbAdSelectionBuilder,
                scoringWinner.getCustomAudienceBiddingInfo().getBuyerDecisionLogicJs());
    }

    private ListenableFuture<DBAdSelection> persistAdSelection(
            @NonNull DBAdSelection.Builder dbAdSelectionBuilder,
            @NonNull String buyerDecisionLogicJS) {
        ListeningExecutorService listeningExecutorService =
                MoreExecutors.listeningDecorator(mExecutorService);

        return listeningExecutorService.submit(
                () -> {
                    // TODO : b/230568647 retry ID generation in case of collision
                    DBAdSelection dbAdSelection;
                    dbAdSelectionBuilder
                            .setAdSelectionId(mAdSelectionIdGenerator.generateId())
                            .setCreationTimestamp(mClock.instant());
                    dbAdSelection = dbAdSelectionBuilder.build();
                    mAdSelectionEntryDao.persistAdSelection(dbAdSelection);
                    mAdSelectionEntryDao.persistBuyerDecisionLogic(
                            new DBBuyerDecisionLogic.Builder()
                                    .setBuyerDecisionLogicJs(buyerDecisionLogicJS)
                                    .setBiddingLogicUrl(dbAdSelection.getBiddingLogicUrl())
                                    .build());
                    return dbAdSelection;
                });
    }
}

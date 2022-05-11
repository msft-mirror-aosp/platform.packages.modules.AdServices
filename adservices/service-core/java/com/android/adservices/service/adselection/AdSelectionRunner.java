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

import android.adservices.adselection.AdSelectionCallback;
import android.adservices.adselection.AdSelectionConfig;
import android.adservices.adselection.AdSelectionResponse;
import android.adservices.common.AdServicesStatusUtils;
import android.adservices.common.FledgeErrorResponse;
import android.adservices.exceptions.AdServicesException;
import android.annotation.NonNull;
import android.content.Context;
import android.net.Uri;
import android.os.RemoteException;
import android.util.Log;

import com.android.adservices.LogUtil;
import com.android.adservices.data.AdServicesDatabase;
import com.android.adservices.data.adselection.AdSelectionDatabase;
import com.android.adservices.data.adselection.AdSelectionEntryDao;
import com.android.adservices.data.adselection.DBAdSelection;
import com.android.adservices.data.customaudience.CustomAudienceDao;
import com.android.adservices.data.customaudience.DBCustomAudience;
import com.android.adservices.service.AdServicesExecutors;
import com.android.internal.annotations.VisibleForTesting;

import com.google.common.base.Function;
import com.google.common.util.concurrent.AsyncFunction;
import com.google.common.util.concurrent.FutureCallback;
import com.google.common.util.concurrent.Futures;
import com.google.common.util.concurrent.ListenableFuture;
import com.google.common.util.concurrent.ListeningExecutorService;
import com.google.common.util.concurrent.MoreExecutors;

import java.util.ArrayList;
import java.util.Calendar;
import java.util.List;
import java.util.Objects;
import java.util.concurrent.ExecutorService;
import java.util.stream.Collectors;

/**
 * Orchestrator that runs the Ads Auction/Bidding and Scoring logic
 * The class expects the caller to create a concrete object instance of the class.
 * The instances are mutually exclusive and do not share any values across shared class instance.
 *
 * Class takes in an executor on which it runs the AdSelection logic
 */
public final class AdSelectionRunner {
    private static final String TAG = AdSelectionRunner.class.getName();

    @NonNull
    private final Context mContext;
    @NonNull
    private final CustomAudienceDao mCustomAudienceDao;
    @NonNull
    private final AdSelectionEntryDao mAdSelectionEntryDao;
    @NonNull
    private final ExecutorService mExecutorService;
    @NonNull
    private final AdsScoreGenerator mAdsScoreGenerator;
    @NonNull
    private final AdBidGenerator mAdBidGenerator;
    @NonNull
    private final AdSelectionIdGenerator mAdSelectionIdGenerator;


    public AdSelectionRunner(@NonNull final Context context) {
        mContext = context;
        mCustomAudienceDao = AdServicesDatabase.getInstance(context).customAudienceDao();
        mAdSelectionEntryDao = AdSelectionDatabase.getInstance(context).adSelectionEntryDao();
        mExecutorService = AdServicesExecutors.getBackgroundExecutor();
        mAdsScoreGenerator = new AdsScoreGeneratorImpl(new AdSelectionScriptEngine(mContext),
                mExecutorService);
        mAdBidGenerator = new AdBidGeneratorImpl(context, mExecutorService);
        mAdSelectionIdGenerator = new AdSelectionIdGenerator();
    }

    @VisibleForTesting
    AdSelectionRunner(@NonNull final Context context,
            @NonNull final CustomAudienceDao customAudienceDao,
            @NonNull final AdSelectionEntryDao adSelectionEntryDao,
            @NonNull final ExecutorService executorService,
            @NonNull final AdsScoreGenerator adsScoreGenerator,
            @NonNull final AdBidGenerator adBidGenerator,
            @NonNull final AdSelectionIdGenerator adSelectionIdGenerator) {
        mContext = context;
        mCustomAudienceDao = customAudienceDao;
        mAdSelectionEntryDao = adSelectionEntryDao;
        mExecutorService = executorService;
        mAdsScoreGenerator = adsScoreGenerator;
        mAdBidGenerator = adBidGenerator;
        mAdSelectionIdGenerator = adSelectionIdGenerator;
    }

    /**
     * Runs the ad selection for a given seller
     *
     * @param adSelectionConfig set of signals and other bidding and scoring information provided
     *                          by the seller
     * @param callback          used to notify the result back to the calling seller
     */
    public void runAdSelection(
            @NonNull AdSelectionConfig adSelectionConfig, @NonNull AdSelectionCallback callback) {
        Objects.requireNonNull(adSelectionConfig);
        Objects.requireNonNull(callback);

        ListenableFuture<DBAdSelection> dbAdSelectionFuture = orchestrateAdSelection(
                adSelectionConfig);

        Futures.addCallback(dbAdSelectionFuture,
                new FutureCallback<DBAdSelection>() {
                    @Override
                    public void onSuccess(DBAdSelection result) {
                        notifySuccessToCaller(result, callback);
                    }

                    @Override
                    public void onFailure(Throwable t) {
                        notifyFailureToCaller(callback, t);
                    }
                }, mExecutorService
        );
    }

    private void notifySuccessToCaller(@NonNull DBAdSelection result,
            @NonNull AdSelectionCallback callback) {
        try {
            callback.onSuccess(new AdSelectionResponse.Builder()
                    .setAdSelectionId(result.getAdSelectionId())
                    .setRenderUrl(result.getWinningAdRenderUrl())
                    .build()
            );
        } catch (RemoteException e) {
            LogUtil.e("Encountered exception during "
                    + "notifying AdSelection callback", e);
        }
    }

    private void notifyFailureToCaller(@NonNull AdSelectionCallback callback,
            @NonNull Throwable t) {
        try {
            FledgeErrorResponse selectionFailureResponse = new FledgeErrorResponse.Builder()
                    .setErrorMessage("Encountered failure during Ad Selection")
                    .setStatusCode(AdServicesStatusUtils.STATUS_INTERNAL_ERROR)
                    .build();
            Log.e(TAG, "Ad Selection failure: " + t.getMessage());
            callback.onFailure(selectionFailureResponse);
        } catch (RemoteException e) {
            LogUtil.e("Encountered exception during "
                    + "notifying AdSelection callback", e);
        }
    }

    /**
     * Overall moderator for running Ad Selection
     *
     * @param adSelectionConfig Set of data from Sellers and Buyers
     *                          needed for Ad Auction and Selection
     * @return {@link AdSelectionResponse}
     */
    private ListenableFuture<DBAdSelection> orchestrateAdSelection(@NonNull final AdSelectionConfig
            adSelectionConfig) {

        List<String> buyers = adSelectionConfig.getCustomAudienceBuyers();
        List<DBCustomAudience> buyerCustomAudience = getBuyerCustomAudience(buyers);

        ListenableFuture<List<AdBiddingOutcome>> biddingOutcome = runAdBidding(
                buyerCustomAudience,
                adSelectionConfig);

        AsyncFunction<List<AdBiddingOutcome>, List<AdScoringOutcome>> mapBidsToScores =
                bids -> {
                    return runAdScoring(bids, adSelectionConfig);
                };

        ListenableFuture<List<AdScoringOutcome>> scoredAds = Futures.transformAsync(biddingOutcome,
                mapBidsToScores,
                mExecutorService);

        Function<List<AdScoringOutcome>, AdScoringOutcome> reduceScoresToWinner =
                scores -> {
                    return getWinningOutcome(scores);
                };

        ListenableFuture<AdScoringOutcome> winningOutcome = Futures.transform(scoredAds,
                reduceScoresToWinner,
                mExecutorService);

        Function<AdScoringOutcome, DBAdSelection.Builder> mapWinnerToDBResult =
                scoringWinner -> {
                    return createAdSelectionResult(scoringWinner);
                };

        ListenableFuture<DBAdSelection.Builder> dbAdSelectionBuilder =
                Futures.transform(winningOutcome, mapWinnerToDBResult, mExecutorService);

        AsyncFunction<DBAdSelection.Builder, DBAdSelection> saveResultToPersistence =
                result -> {
                    return persistAdSelection(result);
                };

        return Futures.transformAsync(dbAdSelectionBuilder, saveResultToPersistence,
                mExecutorService);
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
            bidWinningAds.add(runAdBiddingPerCA(customAudience,
                    adSelectionConfig));
        }

        return Futures.successfulAsList(bidWinningAds);
    }

    private ListenableFuture<AdBiddingOutcome> runAdBiddingPerCA(
            @NonNull final DBCustomAudience customAudience,
            @NonNull final AdSelectionConfig adSelectionConfig) {
        return mAdBidGenerator.runAdBiddingPerCA(customAudience,
                getBuyerDecisionLogic(customAudience.getBiddingLogicUrl()),
                adSelectionConfig.getAdSelectionSignals(),
                adSelectionConfig.getPerBuyerSignals().toString(),
                "{}");
        // TODO(b/230569187): get the contextualSignal securely = "invoking app name"
    }

    private String getBuyerDecisionLogic(final Uri decisionLogicUri) {
        // TODO(b/230436736): Invoke the server to get decision Logic JS
        return "{}";
    }

    private ListenableFuture<List<AdScoringOutcome>> runAdScoring(
            @NonNull final List<AdBiddingOutcome> adBiddingOutcomes,
            @NonNull final AdSelectionConfig adSelectionConfig) throws AdServicesException {
        List<AdBiddingOutcome> validBiddingOutcomes = adBiddingOutcomes.stream()
                .filter(a -> a != null).collect(Collectors.toList());

        if (validBiddingOutcomes.isEmpty()) {
            throw new IllegalStateException("No valid bids for scoring");
        }
        return mAdsScoreGenerator.runAdScoring(validBiddingOutcomes,
                adSelectionConfig);
    }

    private AdScoringOutcome getWinningOutcome(
            @NonNull List<AdScoringOutcome> overallAdScoringOutcome) {
        return overallAdScoringOutcome.stream()
                .filter(a -> a.getAdWithScore().getScore() > 0)
                .max((a, b) -> Double.compare(a.getAdWithScore().getScore(),
                        b.getAdWithScore().getScore()))
                .orElseThrow(() -> new IllegalStateException("No winning Ad found"));
    }

    /**
     * This method populates an Ad Selection result ready to be persisted in DB, with all
     * the fields except adSelectionId and creation time, which should be created as close
     * as possible to persistence logic
     *
     * @param scoringWinner Winning Ad for overall Ad Selection
     * @return A Builder for {@link DBAdSelection} populated with necessary data
     */
    @VisibleForTesting
    DBAdSelection.Builder createAdSelectionResult(@NonNull AdScoringOutcome scoringWinner) {
        DBAdSelection.Builder dbAdSelectionBuilder = new DBAdSelection.Builder();

        dbAdSelectionBuilder
                .setWinningAdBid(scoringWinner.getAdWithScore()
                        .getAdWithBid().getBid())
                .setCustomAudienceSignals(scoringWinner.getCustomAudienceBiddingInfo()
                        .getCustomAudienceSignals())
                .setWinningAdRenderUrl(scoringWinner.getAdWithScore()
                        .getAdWithBid().getAdData().getRenderUrl())
                .setBiddingLogicUrl(scoringWinner.getCustomAudienceBiddingInfo()
                        .getBiddingLogicUrl())
                .setContextualSignals("{}");
        // TODO(b/230569187): get the contextualSignal securely = "invoking app name"
        return dbAdSelectionBuilder;
    }

    private ListenableFuture<DBAdSelection> persistAdSelection(
            @NonNull DBAdSelection.Builder dbAdSelectionBuilder) {
        ListeningExecutorService listeningExecutorService =
                MoreExecutors.listeningDecorator(mExecutorService);

        return listeningExecutorService.submit(
                () -> {
                    //TODO : b/230568647 retry ID generation in case of collision
                    DBAdSelection dbAdSelection;
                    dbAdSelectionBuilder.setAdSelectionId(
                                    mAdSelectionIdGenerator.generateId())
                            .setCreationTimestamp(Calendar.getInstance().toInstant());
                    dbAdSelection = dbAdSelectionBuilder.build();
                    mAdSelectionEntryDao.persistAdSelection(dbAdSelection);
                    return dbAdSelection;
                });
    }
}

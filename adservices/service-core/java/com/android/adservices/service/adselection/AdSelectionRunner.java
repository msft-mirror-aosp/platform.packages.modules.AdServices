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

import static com.android.adservices.service.stats.AdServicesStatsLog.AD_SERVICES_API_CALLED__API_NAME__SELECT_ADS;

import android.adservices.adselection.AdSelectionCallback;
import android.adservices.adselection.AdSelectionConfig;
import android.adservices.adselection.AdSelectionInput;
import android.adservices.adselection.AdSelectionResponse;
import android.adservices.common.AdSelectionSignals;
import android.adservices.common.AdServicesStatusUtils;
import android.adservices.common.FledgeErrorResponse;
import android.adservices.exceptions.AdServicesException;
import android.annotation.NonNull;
import android.annotation.Nullable;
import android.annotation.SuppressLint;
import android.content.Context;
import android.net.Uri;
import android.os.RemoteException;
import android.util.Pair;

import com.android.adservices.LogUtil;
import com.android.adservices.data.adselection.AdSelectionEntryDao;
import com.android.adservices.data.adselection.DBAdSelection;
import com.android.adservices.data.adselection.DBBuyerDecisionLogic;
import com.android.adservices.data.customaudience.CustomAudienceDao;
import com.android.adservices.data.customaudience.DBCustomAudience;
import com.android.adservices.service.Flags;
import com.android.adservices.service.common.AdServicesHttpsClient;
import com.android.adservices.service.common.AppImportanceFilter;
import com.android.adservices.service.common.AppImportanceFilter.WrongCallingApplicationStateException;
import com.android.adservices.service.consent.ConsentManager;
import com.android.adservices.service.devapi.DevContext;
import com.android.adservices.service.stats.AdServicesLogger;
import com.android.internal.annotations.VisibleForTesting;

import com.google.common.base.Function;
import com.google.common.base.Preconditions;
import com.google.common.util.concurrent.AsyncFunction;
import com.google.common.util.concurrent.FluentFuture;
import com.google.common.util.concurrent.FutureCallback;
import com.google.common.util.concurrent.Futures;
import com.google.common.util.concurrent.ListenableFuture;
import com.google.common.util.concurrent.ListeningExecutorService;
import com.google.common.util.concurrent.MoreExecutors;
import com.google.common.util.concurrent.UncheckedTimeoutException;

import java.time.Clock;
import java.util.List;
import java.util.Objects;
import java.util.Optional;
import java.util.concurrent.ExecutionException;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.ForkJoinPool;
import java.util.concurrent.ScheduledThreadPoolExecutor;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.TimeoutException;
import java.util.concurrent.atomic.AtomicReference;
import java.util.stream.Collectors;

/**
 * Orchestrator that runs the Ads Auction/Bidding and Scoring logic The class expects the caller to
 * create a concrete object instance of the class. The instances are mutually exclusive and do not
 * share any values across shared class instance.
 *
 * <p>Class takes in an executor on which it runs the AdSelection logic
 */
public final class AdSelectionRunner {

    @VisibleForTesting static final String AD_SELECTION_ERROR_PATTERN = "%s: %s";

    @VisibleForTesting
    static final String ERROR_AD_SELECTION_FAILURE = "Encountered failure during Ad Selection";

    @VisibleForTesting static final String ERROR_NO_WINNING_AD_FOUND = "No winning Ads found";

    @VisibleForTesting
    static final String ERROR_NO_VALID_BIDS_FOR_SCORING = "No valid bids for scoring";

    @VisibleForTesting static final String ERROR_NO_CA_AVAILABLE = "No Custom Audience available";

    @VisibleForTesting
    static final String ERROR_NO_BUYERS_AVAILABLE =
            "The list of the custom audience buyers should not be empty.";

    @VisibleForTesting
    static final String AD_SELECTION_TIMED_OUT = "Ad selection exceeded allowed time limit";

    @NonNull private final Context mContext;
    @NonNull private final CustomAudienceDao mCustomAudienceDao;
    @NonNull private final AdSelectionEntryDao mAdSelectionEntryDao;
    @NonNull private final ExecutorService mExecutorService;
    @NonNull private final AdsScoreGenerator mAdsScoreGenerator;
    @NonNull private final AdBidGenerator mAdBidGenerator;
    @NonNull private final AdSelectionIdGenerator mAdSelectionIdGenerator;
    @NonNull private final Clock mClock;
    @NonNull private final ConsentManager mConsentManager;
    @NonNull private final AdServicesLogger mAdServicesLogger;
    @NonNull private final Flags mFlags;
    @NonNull private final AppImportanceFilter mAppImportanceFilter;
    private final int mCallerUid;

    public AdSelectionRunner(
            @NonNull final Context context,
            @NonNull final CustomAudienceDao customAudienceDao,
            @NonNull final AdSelectionEntryDao adSelectionEntryDao,
            @NonNull final ExecutorService executorService,
            @NonNull final ConsentManager consentManager,
            @NonNull final AdServicesLogger adServicesLogger,
            @NonNull final DevContext devContext,
            @NonNull AppImportanceFilter appImportanceFilter,
            @NonNull final Flags flags,
            int callerUid) {
        Objects.requireNonNull(context);
        Objects.requireNonNull(customAudienceDao);
        Objects.requireNonNull(adSelectionEntryDao);
        Objects.requireNonNull(executorService);
        Objects.requireNonNull(adServicesLogger);
        Objects.requireNonNull(flags);

        mContext = context;
        mCustomAudienceDao = customAudienceDao;
        mAdSelectionEntryDao = adSelectionEntryDao;
        mExecutorService = executorService;
        mConsentManager = consentManager;
        mAdServicesLogger = adServicesLogger;
        mAdsScoreGenerator =
                new AdsScoreGeneratorImpl(
                        new AdSelectionScriptEngine(mContext),
                        mExecutorService,
                        new AdServicesHttpsClient(mExecutorService),
                        devContext,
                        mAdSelectionEntryDao,
                        flags);
        mAdBidGenerator =
                new AdBidGeneratorImpl(
                        context, executorService, devContext, mCustomAudienceDao, flags);
        mAdSelectionIdGenerator = new AdSelectionIdGenerator();
        mClock = Clock.systemUTC();
        mFlags = flags;
        mAppImportanceFilter = appImportanceFilter;
        mCallerUid = callerUid;
    }

    @VisibleForTesting
    AdSelectionRunner(
            @NonNull final Context context,
            @NonNull final CustomAudienceDao customAudienceDao,
            @NonNull final AdSelectionEntryDao adSelectionEntryDao,
            @NonNull final ExecutorService executorService,
            @NonNull final ConsentManager consentManager,
            @NonNull final AdsScoreGenerator adsScoreGenerator,
            @NonNull final AdBidGenerator adBidGenerator,
            @NonNull final AdSelectionIdGenerator adSelectionIdGenerator,
            @NonNull Clock clock,
            @NonNull final AdServicesLogger adServicesLogger,
            @NonNull AppImportanceFilter appImportanceFilter,
            @NonNull final Flags flags,
            int callerUid) {
        Objects.requireNonNull(context);
        Objects.requireNonNull(customAudienceDao);
        Objects.requireNonNull(adSelectionEntryDao);
        Objects.requireNonNull(executorService);
        Objects.requireNonNull(adsScoreGenerator);
        Objects.requireNonNull(adBidGenerator);
        Objects.requireNonNull(adSelectionIdGenerator);
        Objects.requireNonNull(clock);
        Objects.requireNonNull(adServicesLogger);
        Objects.requireNonNull(appImportanceFilter);
        Objects.requireNonNull(flags);

        mContext = context;
        mCustomAudienceDao = customAudienceDao;
        mAdSelectionEntryDao = adSelectionEntryDao;
        mExecutorService = executorService;
        mConsentManager = consentManager;
        mAdsScoreGenerator = adsScoreGenerator;
        mAdBidGenerator = adBidGenerator;
        mAdSelectionIdGenerator = adSelectionIdGenerator;
        mClock = clock;
        mAdServicesLogger = adServicesLogger;
        mFlags = flags;
        mAppImportanceFilter = appImportanceFilter;
        mCallerUid = callerUid;
    }

    /**
     * Runs the ad selection for a given seller
     *
     * @param inputParams containing {@link AdSelectionConfig} and {@code callerPackageName}
     * @param callback used to notify the result back to the calling seller
     */
    public void runAdSelection(
            @NonNull AdSelectionInput inputParams, @NonNull AdSelectionCallback callback) {
        Objects.requireNonNull(inputParams);
        Objects.requireNonNull(callback);

        try {
            ListenableFuture<Void> userConsentFuture =
                    Futures.submit(this::assertCallerHasUserConsent, mExecutorService);

            ListenableFuture<DBAdSelection> dbAdSelectionFuture =
                    FluentFuture.from(userConsentFuture)
                            .transform(
                                    ignoredVoid -> maybeAssertForegroundCaller(), mExecutorService)
                            .transformAsync(
                                    ignoredVoid ->
                                            orchestrateAdSelection(
                                                    inputParams.getAdSelectionConfig(),
                                                    inputParams.getCallerPackageName()),
                                    mExecutorService);

            Futures.addCallback(
                    dbAdSelectionFuture,
                    new FutureCallback<DBAdSelection>() {
                        @Override
                        public void onSuccess(DBAdSelection result) {
                            notifySuccessToCaller(result, callback);
                        }

                        @Override
                        public void onFailure(Throwable t) {
                            if (t instanceof ConsentManager.RevokedConsentException) {
                                notifyEmptySuccessToCaller(
                                        callback,
                                        AdServicesStatusUtils.STATUS_USER_CONSENT_REVOKED);
                            } else {
                                notifyFailureToCaller(callback, t);
                            }
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
                            .setRenderUri(result.getWinningAdRenderUri())
                            .build());
            resultCode = AdServicesStatusUtils.STATUS_SUCCESS;
        } catch (RemoteException e) {
            LogUtil.e(e, "Encountered exception during notifying AdSelection callback");
            resultCode = AdServicesStatusUtils.STATUS_UNKNOWN_ERROR;
        } finally {
            LogUtil.v(
                    "Ad Selection with Id:%d completed, attempted notifying success",
                    result.getAdSelectionId());
            mAdServicesLogger.logFledgeApiCallStats(
                    AD_SERVICES_API_CALLED__API_NAME__SELECT_ADS, resultCode);
        }
    }

    /** Sends a successful response to the caller that represents a silent failure. */
    private void notifyEmptySuccessToCaller(@NonNull AdSelectionCallback callback, int resultCode) {
        try {
            callback.onSuccess(
                    new AdSelectionResponse.Builder()
                            .setAdSelectionId(mAdSelectionIdGenerator.generateId())
                            .setRenderUri(Uri.EMPTY)
                            .build());
        } catch (RemoteException e) {
            LogUtil.e(e, "Encountered exception during notifying AdSelection callback");
            resultCode = AdServicesStatusUtils.STATUS_UNKNOWN_ERROR;
        } finally {
            mAdServicesLogger.logFledgeApiCallStats(
                    AD_SERVICES_API_CALLED__API_NAME__SELECT_ADS, resultCode);
        }
    }

    private void notifyFailureToCaller(
            @NonNull AdSelectionCallback callback, @NonNull Throwable t) {
        int resultCode = AdServicesStatusUtils.STATUS_UNSET;
        try {
            if (t instanceof WrongCallingApplicationStateException) {
                resultCode = AdServicesStatusUtils.STATUS_BACKGROUND_CALLER;
            } else if (t instanceof UncheckedTimeoutException) {
                resultCode = AdServicesStatusUtils.STATUS_TIMEOUT;
            } else {
                resultCode = AdServicesStatusUtils.STATUS_INTERNAL_ERROR;
            }
            FledgeErrorResponse selectionFailureResponse =
                    new FledgeErrorResponse.Builder()
                            .setErrorMessage(
                                    String.format(
                                            AD_SELECTION_ERROR_PATTERN,
                                            ERROR_AD_SELECTION_FAILURE,
                                            t.getMessage()))
                            .setStatusCode(resultCode)
                            .build();
            LogUtil.e(t, "Ad Selection failure: ");
            callback.onFailure(selectionFailureResponse);
        } catch (RemoteException e) {
            LogUtil.e(e, "Encountered exception during notifying AdSelection callback");
            resultCode = AdServicesStatusUtils.STATUS_UNKNOWN_ERROR;
        } finally {
            mAdServicesLogger.logFledgeApiCallStats(
                    AD_SERVICES_API_CALLED__API_NAME__SELECT_ADS, resultCode);
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
            @NonNull final AdSelectionConfig adSelectionConfig,
            @NonNull final String callerPackageName) {
        LogUtil.v("Beginning Ad Selection Orchestration");

        ListenableFuture<List<DBCustomAudience>> buyerCustomAudience =
                getBuyersCustomAudience(adSelectionConfig);

        AsyncFunction<List<DBCustomAudience>, List<AdBiddingOutcome>> bidAds =
                buyerCAs -> {
                    return runAdBidding(buyerCAs, adSelectionConfig);
                };

        ListenableFuture<List<AdBiddingOutcome>> biddingOutcome =
                Futures.transformAsync(buyerCustomAudience, bidAds, mExecutorService);

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
                    return persistAdSelection(
                            adSelectionAndJs.first, adSelectionAndJs.second, callerPackageName);
                };

        return FluentFuture.from(dbAdSelectionBuilder)
                .transformAsync(saveResultToPersistence, mExecutorService)
                .withTimeout(
                        mFlags.getAdSelectionOverallTimeoutMs(),
                        TimeUnit.MILLISECONDS,
                        // TODO(b/237103033): Comply with thread usage policy for AdServices;
                        //  use a global scheduled executor
                        new ScheduledThreadPoolExecutor(1))
                .catching(
                        TimeoutException.class,
                        this::handleTimeoutError,
                        MoreExecutors.listeningDecorator(mExecutorService));
    }

    @Nullable
    private DBAdSelection handleTimeoutError(TimeoutException e) {
        LogUtil.e(e, "Ad Selection exceeded time limit");
        throw new UncheckedTimeoutException(AD_SELECTION_TIMED_OUT);
    }

    private ListenableFuture<List<DBCustomAudience>> getBuyersCustomAudience(
            final AdSelectionConfig adSelectionConfig) {

        ListeningExecutorService listeningExecutorService =
                MoreExecutors.listeningDecorator(mExecutorService);

        return listeningExecutorService.submit(
                () -> {
                    Preconditions.checkArgument(
                            !adSelectionConfig.getCustomAudienceBuyers().isEmpty(),
                            ERROR_NO_BUYERS_AVAILABLE);
                    List<DBCustomAudience> buyerCustomAudience =
                            mCustomAudienceDao.getActiveCustomAudienceByBuyers(
                                    adSelectionConfig.getCustomAudienceBuyers(),
                                    mClock.instant(),
                                    mFlags.getFledgeCustomAudienceActiveTimeWindowInMs());
                    if (buyerCustomAudience == null || buyerCustomAudience.isEmpty()) {
                        // TODO(b/233296309) : Remove this exception after adding contextual ads
                        throw new IllegalStateException(ERROR_NO_CA_AVAILABLE);
                    }
                    return buyerCustomAudience;
                });
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

    private int getParallelBiddingCount() {
        int parallelBiddingCountConfigValue = mFlags.getAdSelectionConcurrentBiddingCount();
        int numberOfAvailableProcessors = Runtime.getRuntime().availableProcessors();
        return Math.min(parallelBiddingCountConfigValue, numberOfAvailableProcessors);
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
    Pair<DBAdSelection.Builder, String> createAdSelectionResult(
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
                        scoringWinner.getCustomAudienceBiddingInfo().getBiddingLogicUrl())
                .setContextualSignals("{}");
        // TODO(b/230569187): get the contextualSignal securely = "invoking app name"
        return Pair.create(
                dbAdSelectionBuilder,
                scoringWinner.getCustomAudienceBiddingInfo().getBuyerDecisionLogicJs());
    }

    private ListenableFuture<DBAdSelection> persistAdSelection(
            @NonNull DBAdSelection.Builder dbAdSelectionBuilder,
            @NonNull String buyerDecisionLogicJS,
            @NonNull String callerPackageName) {
        ListeningExecutorService listeningExecutorService =
                MoreExecutors.listeningDecorator(mExecutorService);

        final long adSelectionId = mAdSelectionIdGenerator.generateId();
        LogUtil.v("Persisting Ad Selection Result for Id:%d", adSelectionId);
        return listeningExecutorService.submit(
                () -> {
                    // TODO : b/230568647 retry ID generation in case of collision
                    DBAdSelection dbAdSelection;
                    dbAdSelectionBuilder
                            .setAdSelectionId(adSelectionId)
                            .setCreationTimestamp(mClock.instant())
                            .setCallerPackageName(callerPackageName);
                    dbAdSelection = dbAdSelectionBuilder.build();
                    mAdSelectionEntryDao.persistAdSelection(dbAdSelection);
                    mAdSelectionEntryDao.persistBuyerDecisionLogic(
                            new DBBuyerDecisionLogic.Builder()
                                    .setBuyerDecisionLogicJs(buyerDecisionLogicJS)
                                    .setBiddingLogicUri(dbAdSelection.getBiddingLogicUri())
                                    .build());
                    return dbAdSelection;
                });
    }

    /**
     * Asserts that FLEDGE APIs and the Privacy Sandbox as a whole have user consent.
     *
     * @return an ignorable {@code null}
     * @throws ConsentManager.RevokedConsentException if FLEDGE or the Privacy Sandbox do not have
     *     user consent
     */
    private Void assertCallerHasUserConsent() throws ConsentManager.RevokedConsentException {
        if (!mConsentManager.getConsent(mContext.getPackageManager()).isGiven()) {
            throw new ConsentManager.RevokedConsentException();
        }
        return null;
    }

    /**
     * Asserts that the caller has the appropriate foreground status, if enabled.
     *
     * @return an ignorable {@code null}
     * @throws WrongCallingApplicationStateException if the foreground check is enabled and fails
     */
    private Void maybeAssertForegroundCaller() throws WrongCallingApplicationStateException {
        if (mFlags.getEnforceForegroundStatusForFledgeRunAdSelection()) {
            mAppImportanceFilter.assertCallerIsInForeground(
                    mCallerUid, AD_SERVICES_API_CALLED__API_NAME__SELECT_ADS, null);
        }
        return null;
    }
}

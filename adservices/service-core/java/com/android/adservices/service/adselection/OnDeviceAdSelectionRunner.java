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

import android.adservices.adselection.AdSelectionConfig;
import android.adservices.common.AdTechIdentifier;
import android.adservices.exceptions.AdServicesException;
import android.annotation.NonNull;
import android.annotation.Nullable;
import android.annotation.SuppressLint;
import android.os.Build;
import android.util.Pair;

import androidx.annotation.RequiresApi;

import com.android.adservices.LoggerFactory;
import com.android.adservices.data.adselection.AdSelectionEntryDao;
import com.android.adservices.data.adselection.DBAdSelection;
import com.android.adservices.data.customaudience.CustomAudienceDao;
import com.android.adservices.data.customaudience.DBCustomAudience;
import com.android.adservices.data.encryptionkey.EncryptionKeyDao;
import com.android.adservices.data.enrollment.EnrollmentDao;
import com.android.adservices.service.DebugFlags;
import com.android.adservices.service.Flags;
import com.android.adservices.service.adselection.debug.DebugReport;
import com.android.adservices.service.adselection.debug.DebugReporting;
import com.android.adservices.service.common.AdSelectionServiceFilter;
import com.android.adservices.service.common.BinderFlagReader;
import com.android.adservices.service.common.FrequencyCapAdDataValidator;
import com.android.adservices.service.common.RetryStrategy;
import com.android.adservices.service.common.httpclient.AdServicesHttpsClient;
import com.android.adservices.service.devapi.CustomAudienceDevOverridesHelper;
import com.android.adservices.service.devapi.DevContext;
import com.android.adservices.service.kanon.KAnonSignJoinFactory;
import com.android.adservices.service.stats.AdSelectionExecutionLogger;
import com.android.adservices.service.stats.AdServicesLogger;
import com.android.adservices.service.stats.AdServicesLoggerUtil;
import com.android.internal.annotations.VisibleForTesting;

import com.google.common.base.Function;
import com.google.common.util.concurrent.AsyncFunction;
import com.google.common.util.concurrent.FluentFuture;
import com.google.common.util.concurrent.Futures;
import com.google.common.util.concurrent.ListenableFuture;

import java.time.Clock;
import java.util.ArrayList;
import java.util.Collections;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.ScheduledThreadPoolExecutor;
import java.util.stream.Collectors;

/** Orchestrate on-device ad selection. */
@RequiresApi(Build.VERSION_CODES.S)
public class OnDeviceAdSelectionRunner extends AdSelectionRunner {
    private static final LoggerFactory.Logger sLogger = LoggerFactory.getFledgeLogger();
    @NonNull protected final AdsScoreGenerator mAdsScoreGenerator;
    @NonNull protected final AdServicesHttpsClient mAdServicesHttpsClient;
    @NonNull protected final PerBuyerBiddingRunner mPerBuyerBiddingRunner;
    @NonNull protected final FrequencyCapAdFilterer mFrequencyCapAdFilterer;
    @NonNull private final AppInstallAdFilterer mAppInstallAdFilterer;
    @NonNull protected final AdCounterKeyCopier mAdCounterKeyCopier;

    public OnDeviceAdSelectionRunner(
            @NonNull final CustomAudienceDao customAudienceDao,
            @NonNull final AdSelectionEntryDao adSelectionEntryDao,
            @NonNull final EncryptionKeyDao encryptionKeyDao,
            @NonNull final EnrollmentDao enrollmentDao,
            @NonNull final AdServicesHttpsClient adServicesHttpsClient,
            @NonNull final ExecutorService lightweightExecutorService,
            @NonNull final ExecutorService backgroundExecutorService,
            @NonNull final ScheduledThreadPoolExecutor scheduledExecutor,
            @NonNull final AdServicesLogger adServicesLogger,
            @NonNull final DevContext devContext,
            @NonNull final Flags flags,
            @NonNull final DebugFlags debugFlags,
            @NonNull final AdSelectionExecutionLogger adSelectionExecutionLogger,
            @NonNull final AdSelectionServiceFilter adSelectionServiceFilter,
            @NonNull final FrequencyCapAdFilterer frequencyCapAdFilterer,
            @NonNull final AdCounterKeyCopier adCounterKeyCopier,
            @NonNull final AdCounterHistogramUpdater adCounterHistogramUpdater,
            @NonNull final FrequencyCapAdDataValidator frequencyCapAdDataValidator,
            @NonNull final DebugReporting debugReporting,
            final int callerUid,
            boolean shouldUseUnifiedTables,
            @NonNull final RetryStrategy retryStrategy,
            @NonNull final KAnonSignJoinFactory kAnonSignJoinFactory,
            @NonNull final AppInstallAdFilterer appInstallAdFilterer,
            boolean consoleMessageInLogsEnabled) {
        super(
                customAudienceDao,
                adSelectionEntryDao,
                encryptionKeyDao,
                enrollmentDao,
                lightweightExecutorService,
                backgroundExecutorService,
                scheduledExecutor,
                adServicesLogger,
                flags,
                debugFlags,
                adSelectionExecutionLogger,
                adSelectionServiceFilter,
                frequencyCapAdFilterer,
                frequencyCapAdDataValidator,
                adCounterHistogramUpdater,
                debugReporting,
                callerUid,
                shouldUseUnifiedTables,
                kAnonSignJoinFactory,
                appInstallAdFilterer);
        Objects.requireNonNull(adServicesHttpsClient);
        Objects.requireNonNull(frequencyCapAdFilterer);
        Objects.requireNonNull(adCounterKeyCopier);
        Objects.requireNonNull(retryStrategy);

        mAdServicesHttpsClient = adServicesHttpsClient;
        mFrequencyCapAdFilterer = frequencyCapAdFilterer;
        mAdCounterKeyCopier = adCounterKeyCopier;
        boolean cpcBillingEnabled = BinderFlagReader.readFlag(mFlags::getFledgeCpcBillingEnabled);
        boolean dataVersionHeaderEnabled =
                BinderFlagReader.readFlag(mFlags::getFledgeDataVersionHeaderEnabled);
        mAdsScoreGenerator =
                new AdsScoreGeneratorImpl(
                        new AdSelectionScriptEngine(
                                flags::getIsolateMaxHeapSizeBytes,
                                mAdCounterKeyCopier,
                                mDebugReporting.getScriptStrategy(),
                                cpcBillingEnabled,
                                retryStrategy,
                                () -> consoleMessageInLogsEnabled),
                        mLightweightExecutorService,
                        mBackgroundExecutorService,
                        mScheduledExecutor,
                        mAdServicesHttpsClient,
                        devContext,
                        mAdSelectionEntryDao,
                        flags,
                        adSelectionExecutionLogger,
                        mDebugReporting,
                        dataVersionHeaderEnabled);
        mPerBuyerBiddingRunner =
                new PerBuyerBiddingRunner(
                        new AdBidGeneratorImpl(
                                mAdServicesHttpsClient,
                                mLightweightExecutorService,
                                mBackgroundExecutorService,
                                mScheduledExecutor,
                                devContext,
                                mCustomAudienceDao,
                                mAdCounterKeyCopier,
                                flags,
                                mDebugReporting,
                                cpcBillingEnabled,
                                dataVersionHeaderEnabled,
                                retryStrategy,
                                consoleMessageInLogsEnabled),
                        new TrustedBiddingDataFetcher(
                                adServicesHttpsClient,
                                devContext,
                                new CustomAudienceDevOverridesHelper(devContext, customAudienceDao),
                                lightweightExecutorService,
                                dataVersionHeaderEnabled),
                        mScheduledExecutor,
                        mBackgroundExecutorService,
                        flags);
        mAppInstallAdFilterer = appInstallAdFilterer;
    }

    @VisibleForTesting
    OnDeviceAdSelectionRunner(
            @NonNull final CustomAudienceDao customAudienceDao,
            @NonNull final AdSelectionEntryDao adSelectionEntryDao,
            @NonNull final EncryptionKeyDao encryptionKeyDao,
            @NonNull final EnrollmentDao enrollmentDao,
            @NonNull final AdServicesHttpsClient adServicesHttpsClient,
            @NonNull final ExecutorService lightweightExecutorService,
            @NonNull final ExecutorService backgroundExecutorService,
            @NonNull final ScheduledThreadPoolExecutor scheduledExecutor,
            @NonNull final AdsScoreGenerator adsScoreGenerator,
            @NonNull final AdSelectionIdGenerator adSelectionIdGenerator,
            @NonNull Clock clock,
            @NonNull final AdServicesLogger adServicesLogger,
            @NonNull final Flags flags,
            @NonNull final DebugFlags debugFlags,
            int callerUid,
            @NonNull final AdSelectionServiceFilter adSelectionServiceFilter,
            @NonNull final AdSelectionExecutionLogger adSelectionExecutionLogger,
            @NonNull final PerBuyerBiddingRunner perBuyerBiddingRunner,
            @NonNull final FrequencyCapAdFilterer frequencyCapAdFilterer,
            @NonNull final AdCounterKeyCopier adCounterKeyCopier,
            @NonNull final AdCounterHistogramUpdater adCounterHistogramUpdater,
            @NonNull final FrequencyCapAdDataValidator frequencyCapAdDataValidator,
            @NonNull final DebugReporting debugReporting,
            boolean shouldUseUnifiedTables,
            @NonNull final KAnonSignJoinFactory kAnonSignJoinFactory,
            @NonNull final AppInstallAdFilterer appInstallAdFilterer) {
        super(
                customAudienceDao,
                adSelectionEntryDao,
                encryptionKeyDao,
                enrollmentDao,
                lightweightExecutorService,
                backgroundExecutorService,
                scheduledExecutor,
                adSelectionIdGenerator,
                clock,
                adServicesLogger,
                flags,
                debugFlags,
                callerUid,
                adSelectionServiceFilter,
                frequencyCapAdFilterer,
                frequencyCapAdDataValidator,
                adCounterHistogramUpdater,
                adSelectionExecutionLogger,
                debugReporting,
                shouldUseUnifiedTables,
                kAnonSignJoinFactory,
                appInstallAdFilterer);

        Objects.requireNonNull(adsScoreGenerator);
        Objects.requireNonNull(adServicesHttpsClient);
        Objects.requireNonNull(frequencyCapAdFilterer);
        Objects.requireNonNull(adCounterKeyCopier);
        Objects.requireNonNull(appInstallAdFilterer);

        mAdsScoreGenerator = adsScoreGenerator;
        mAdServicesHttpsClient = adServicesHttpsClient;
        mPerBuyerBiddingRunner = perBuyerBiddingRunner;
        mFrequencyCapAdFilterer = frequencyCapAdFilterer;
        mAdCounterKeyCopier = adCounterKeyCopier;
        mAppInstallAdFilterer = appInstallAdFilterer;
    }

    /**
     * Orchestrate on device ad selection.
     *
     * @param adSelectionConfig Set of data from Sellers and Buyers needed for Ad Auction and
     *                          Selection
     */
    public ListenableFuture<AdSelectionOrchestrationResult> orchestrateAdSelection(
            @NonNull final AdSelectionConfig adSelectionConfig,
            @NonNull final String callerPackageName,
            ListenableFuture<List<DBCustomAudience>> buyerCustomAudience) {

        ListenableFuture<List<DBCustomAudience>> filteredCas =
                FluentFuture.from(buyerCustomAudience)
                        .transform(this::filterCustomAudiences, mLightweightExecutorService);

        AsyncFunction<List<DBCustomAudience>, List<AdBiddingOutcome>> bidAds =
                buyerCAs -> runAdBidding(buyerCAs, adSelectionConfig);

        ListenableFuture<List<AdBiddingOutcome>> biddingOutcome =
                Futures.transformAsync(filteredCas, bidAds, mLightweightExecutorService);

        AsyncFunction<List<AdBiddingOutcome>, Pair<List<AdScoringOutcome>, List<DebugReport>>>
                mapBidsToScores = bids -> runAdScoring(bids, adSelectionConfig);

        ListenableFuture<Pair<List<AdScoringOutcome>, List<DebugReport>>>
                scoredAdsAndBuyerDebugReports =
                        Futures.transformAsync(
                                biddingOutcome, mapBidsToScores, mLightweightExecutorService);

        Function<
                        Pair<List<AdScoringOutcome>, List<DebugReport>>,
                        Pair<AdScoringOutcome, AdSelectionContext>>
                reduceScoresToWinner = this::getWinningOutcomeAndContext;

        ListenableFuture<Pair<AdScoringOutcome, AdSelectionContext>> winningOutcomeAndDebugReports =
                Futures.transform(
                        scoredAdsAndBuyerDebugReports,
                        reduceScoresToWinner,
                        mLightweightExecutorService);

        AsyncFunction<Pair<AdScoringOutcome, AdSelectionContext>, AdSelectionOrchestrationResult>
                mapWinnerToDBResult = this::createAdSelectionResult;

        ListenableFuture<AdSelectionOrchestrationResult> dbAdSelectionBuilder =
                Futures.transformAsync(
                        winningOutcomeAndDebugReports,
                        mapWinnerToDBResult,
                        mLightweightExecutorService);

        // Clean up after the future is complete, out of critical path
        dbAdSelectionBuilder.addListener(this::cleanUpCache, mLightweightExecutorService);

        return dbAdSelectionBuilder;
    }

    private List<DBCustomAudience> filterCustomAudiences(
            @NonNull final List<DBCustomAudience> customAudiences) {
        logStartCustomAudienceAdFiltering();

        logStartCustomAudienceAppInstallFiltering();
        List<DBCustomAudience> toReturn =
                mAppInstallAdFilterer.filterCustomAudiences(customAudiences);
        logEndCustomAudienceAppInstallFiltering();

        logStartCustomAudienceFcapFiltering();
        toReturn = mFrequencyCapAdFilterer.filterCustomAudiences(toReturn);
        logEndCustomAudienceFcapFiltering();

        logCustomAudienceAdFilteringDetails(customAudiences, toReturn);
        logEndCustomAudienceAdFiltering();
        return toReturn;
    }

    private void logStartCustomAudienceAdFiltering() {
        if (mFlags.getFledgeAppInstallFilteringMetricsEnabled()
                || mFlags.getFledgeFrequencyCapFilteringMetricsEnabled()) {
            mCustomAudienceFilteringLogger.setAdFilteringStartTimestamp();
        } else {
            sLogger.v(
                    "Both FledgeAppInstallFilteringMetricsEnabled and"
                            + " FledgeFrequencyCapFilteringMetricsEnabled flags are off. Skipped"
                            + " logStartCustomAudienceAdFiltering");
        }
    }

    private void logEndCustomAudienceAdFiltering() {
        if (mFlags.getFledgeAppInstallFilteringMetricsEnabled()
                || mFlags.getFledgeFrequencyCapFilteringMetricsEnabled()) {
            mCustomAudienceFilteringLogger.setAdFilteringEndTimestamp();
            mCustomAudienceFilteringLogger.close();
        } else {
            sLogger.v(
                    "Both FledgeAppInstallFilteringMetricsEnabled and"
                            + " FledgeFrequencyCapFilteringMetricsEnabled flags are off. Skipped"
                            + " logEndCustomAudienceAdFiltering");
        }
    }

    private void logStartCustomAudienceAppInstallFiltering() {
        if (mFlags.getFledgeAppInstallFilteringMetricsEnabled()) {
            mCustomAudienceFilteringLogger.setAppInstallStartTimestamp();
        } else {
            sLogger.v(
                    "FledgeAppInstallFilteringMetricsEnabled flag is off. Skipped"
                            + " logStartCustomAudienceAppInstallFiltering");
        }
    }

    private void logEndCustomAudienceAppInstallFiltering() {
        if (mFlags.getFledgeAppInstallFilteringMetricsEnabled()) {
            mCustomAudienceFilteringLogger.setAppInstallEndTimestamp();
        } else {
            sLogger.v(
                    "FledgeAppInstallFilteringMetricsEnabled flag is off. Skipped"
                            + " logEndCustomAudienceAppInstallFiltering");
        }
    }

    private void logStartCustomAudienceFcapFiltering() {
        if (mFlags.getFledgeFrequencyCapFilteringMetricsEnabled()) {
            mCustomAudienceFilteringLogger.setFrequencyCapStartTimestamp();
        } else {
            sLogger.v(
                    "FledgeFrequencyCapFilteringMetricsEnabled flag is off. Skipped"
                            + " logStartCustomAudienceFcapFiltering");
        }
    }

    private void logEndCustomAudienceFcapFiltering() {
        if (mFlags.getFledgeFrequencyCapFilteringMetricsEnabled()) {
            mCustomAudienceFilteringLogger.setFrequencyCapEndTimestamp();
        } else {
            sLogger.v(
                    "FledgeFrequencyCapFilteringMetricsEnabled flag is off. Skipped"
                            + " logEndCustomAudienceFcapFiltering");
        }
    }

    private void logCustomAudienceAdFilteringDetails(
            List<DBCustomAudience> beforeFiltering, List<DBCustomAudience> afterFiltering) {
        if (mFlags.getFledgeAppInstallFilteringMetricsEnabled()
                || mFlags.getFledgeFrequencyCapFilteringMetricsEnabled()) {
            Function<List<DBCustomAudience>, Integer> getNumOfAds =
                    (cas) -> (int) cas.stream().mapToLong(ca -> ca.getAds().size()).sum();
            int numOfAdsBeforeFiltering = getNumOfAds.apply(beforeFiltering);
            int numOfAdsAfterFiltering = getNumOfAds.apply(afterFiltering);
            mCustomAudienceFilteringLogger.setTotalNumOfAdsBeforeFiltering(numOfAdsBeforeFiltering);
            mCustomAudienceFilteringLogger.setTotalNumOfCustomAudiencesBeforeFiltering(
                    beforeFiltering.size());
            mCustomAudienceFilteringLogger.setNumOfAdsFilteredOutOfBidding(
                    numOfAdsBeforeFiltering - numOfAdsAfterFiltering);
            mCustomAudienceFilteringLogger.setNumOfCustomAudiencesFilteredOutOfBidding(
                    beforeFiltering.size() - afterFiltering.size());
        } else {
            sLogger.v(
                    "Both FledgeAppInstallFilteringMetricsEnabled and"
                            + " FledgeFrequencyCapFilteringMetricsEnabled flags are off. Skipped"
                            + " logCustomAudienceAdFilteringDetails");
        }
    }

    private ListenableFuture<List<AdBiddingOutcome>> runAdBidding(
            @NonNull final List<DBCustomAudience> customAudiences,
            @NonNull final AdSelectionConfig adSelectionConfig) {
        try {
            if (customAudiences.isEmpty()) {
                sLogger.w("Need not invoke bidding on empty list of CAs");
                // Return empty list of bids
                return Futures.immediateFuture(Collections.EMPTY_LIST);
            }
            mAdSelectionExecutionLogger.startRunAdBidding(customAudiences);
            Map<AdTechIdentifier, List<DBCustomAudience>> buyerToCustomAudienceMap =
                    mapBuyerToCustomAudience(customAudiences);

            sLogger.v("Invoking bidding for #%d buyers", buyerToCustomAudienceMap.size());
            long perBuyerBiddingTimeoutMs = mFlags.getAdSelectionBiddingTimeoutPerBuyerMs();
            return FluentFuture.from(
                            Futures.successfulAsList(
                                    buyerToCustomAudienceMap.entrySet().parallelStream()
                                            .map(
                                                    entry -> {
                                                        return mPerBuyerBiddingRunner.runBidding(
                                                                entry.getKey(),
                                                                entry.getValue(),
                                                                perBuyerBiddingTimeoutMs,
                                                                adSelectionConfig);
                                                    })
                                            .flatMap(List::stream)
                                            .collect(Collectors.toList())))
                    .transform(this::endSuccessfulBidding, mLightweightExecutorService)
                    .catching(
                            RuntimeException.class,
                            this::endFailedBiddingWithRuntimeException,
                            mLightweightExecutorService);
        } catch (Exception e) {
            mAdSelectionExecutionLogger.endBiddingProcess(
                    null, AdServicesLoggerUtil.getResultCodeFromException(e));
            throw e;
        }
    }

    @NonNull
    private List<AdBiddingOutcome> endSuccessfulBidding(@NonNull List<AdBiddingOutcome> result) {
        Objects.requireNonNull(result);
        mAdSelectionExecutionLogger.endBiddingProcess(result, STATUS_SUCCESS);
        return result;
    }

    private void endSilentFailedBidding(RuntimeException e) {
        mAdSelectionExecutionLogger.endBiddingProcess(
                null, AdServicesLoggerUtil.getResultCodeFromException(e));
    }

    @Nullable
    private List<AdBiddingOutcome> endFailedBiddingWithRuntimeException(RuntimeException e) {
        mAdSelectionExecutionLogger.endBiddingProcess(
                null, AdServicesLoggerUtil.getResultCodeFromException(e));
        throw e;
    }

    @SuppressLint("DefaultLocale")
    private ListenableFuture<Pair<List<AdScoringOutcome>, List<DebugReport>>> runAdScoring(
            @NonNull final List<AdBiddingOutcome> adBiddingOutcomes,
            @NonNull final AdSelectionConfig adSelectionConfig)
            throws AdServicesException {
        sLogger.v("Got %d total bidding outcomes", adBiddingOutcomes.size());
        List<AdBiddingOutcome> validBiddingOutcomes =
                adBiddingOutcomes.stream().filter(Objects::nonNull).collect(Collectors.toList());
        sLogger.v("Got %d valid bidding outcomes", validBiddingOutcomes.size());

        if (validBiddingOutcomes.isEmpty()
                && adSelectionConfig.getPerBuyerSignedContextualAds().isEmpty()) {
            sLogger.w("Received empty list of successful bidding outcomes and contextual ads");
            throw new IllegalStateException(ERROR_NO_VALID_BIDS_OR_CONTEXTUAL_ADS_FOR_SCORING);
        }
        List<DebugReport> buyerDebugReports = new ArrayList<>();
        if (mDebugReporting.isEnabled()) {
            buyerDebugReports.addAll(
                    validBiddingOutcomes.stream()
                            .map(AdBiddingOutcome::getDebugReport)
                            .filter(Objects::nonNull)
                            .collect(Collectors.toList()));
        }
        sLogger.v(
                "Invoking score generator with %s bids and %s contextual ads.",
                validBiddingOutcomes.size(),
                adSelectionConfig.getPerBuyerSignedContextualAds().size());
        return mAdsScoreGenerator
                .runAdScoring(validBiddingOutcomes, adSelectionConfig)
                .transform(
                        adScoringOutcomes -> Pair.create(adScoringOutcomes, buyerDebugReports),
                        mLightweightExecutorService);
    }

    private Pair<AdScoringOutcome, AdSelectionContext> getWinningOutcomeAndContext(
            @NonNull
                    Pair<List<AdScoringOutcome>, List<DebugReport>>
                            scoringOutcomesAndBuyerDebugReports) {
        sLogger.v("Scoring completed, generating winning outcome");
        // Iterate over the list to find the winner and second highest scored ad outcome.
        AdScoringOutcome winningAd = null;
        AdScoringOutcome secondHighestScoredAd = null;
        double winningAdScore = Double.MIN_VALUE;
        double secondHighestAdScore = Double.MIN_VALUE;
        List<DebugReport> debugReports =
                new ArrayList<>(scoringOutcomesAndBuyerDebugReports.second);
        for (AdScoringOutcome adScoringOutcome : scoringOutcomesAndBuyerDebugReports.first) {
            if (mDebugReporting.isEnabled() && Objects.nonNull(adScoringOutcome.getDebugReport())) {
                debugReports.add(adScoringOutcome.getDebugReport());
            }
            double score = adScoringOutcome.getAdWithScore().getScore();
            if (score <= 0L) {
                continue;
            }
            if (score > winningAdScore) {
                // winner becomes second highest scored ad.
                secondHighestScoredAd = winningAd;
                secondHighestAdScore = winningAdScore;
                winningAd = adScoringOutcome;
                winningAdScore = score;
            } else if (score > secondHighestAdScore) {
                secondHighestScoredAd = adScoringOutcome;
                secondHighestAdScore = score;
            }
        }
        if (Objects.isNull(winningAd)) {
            throw new IllegalStateException(ERROR_NO_WINNING_AD_FOUND);
        }

        AdSelectionContext adSelectionContext;
        if (mDebugReporting.isEnabled()) {
            adSelectionContext =
                    new AdSelectionContext(debugReports, winningAd, secondHighestScoredAd);
        } else {
            adSelectionContext =
                    new AdSelectionContext(
                            /*debugReports*/ Collections.emptyList(),
                            /*winningAdScoringOutcome*/ null,
                            /*secondHighestAdScoringOutcome*/ null);
        }
        return Pair.create(winningAd, adSelectionContext);
    }

    /**
     * This method populates an Ad Selection result ready to be persisted in DB, with all the fields
     * except adSelectionId and creation time, which should be created as close as possible to
     * persistence logic
     *
     * @param winningAdScoringOutcomeAndContext Winning Ad for overall Ad Selection with
     *     AdSelectionContext
     * @return A {@link Pair} with a Builder for {@link DBAdSelection} populated with necessary data
     *     and a string containing the JS with the decision logic from this buyer.
     */
    @VisibleForTesting
    ListenableFuture<AdSelectionOrchestrationResult> createAdSelectionResult(
            @NonNull Pair<AdScoringOutcome, AdSelectionContext> winningAdScoringOutcomeAndContext) {
        DBAdSelection.Builder dbAdSelectionBuilder = new DBAdSelection.Builder();
        sLogger.v("Creating Ad Selection result from scoring winner");
        String buyerContextualSignalsString;
        String sellerContextualSignalsString;
        // There should always be a winner.
        AdScoringOutcome scoringWinner = winningAdScoringOutcomeAndContext.first;
        sLogger.v("Scoring Winner: %s", scoringWinner);
        if (Objects.isNull(scoringWinner.getBuyerContextualSignals())) {
            buyerContextualSignalsString = "{}";
        } else {
            buyerContextualSignalsString = scoringWinner.getBuyerContextualSignals().toString();
        }

        if (Objects.isNull(scoringWinner.getSellerContextualSignals())) {
            sellerContextualSignalsString = "{}";
        } else {
            sellerContextualSignalsString = scoringWinner.getSellerContextualSignals().toString();
        }

        dbAdSelectionBuilder
                .setWinningAdBid(scoringWinner.getAdWithScore().getAdWithBid().getBid())
                .setCustomAudienceSignals(scoringWinner.getCustomAudienceSignals())
                .setWinningAdRenderUri(
                        scoringWinner.getAdWithScore().getAdWithBid().getAdData().getRenderUri())
                .setBiddingLogicUri(scoringWinner.getBiddingLogicUri())
                .setBuyerContextualSignals(buyerContextualSignalsString)
                .setSellerContextualSignals(sellerContextualSignalsString);
        // TODO(b/230569187): get the contextualSignal securely = "invoking app name"

        final DBAdSelection.Builder copiedDBAdSelectionBuilder =
                mAdCounterKeyCopier.copyAdCounterKeys(dbAdSelectionBuilder, scoringWinner);

        return getWinnerBiddingLogicJs(scoringWinner)
                .transform(
                        biddingLogicJs ->
                                new AdSelectionOrchestrationResult(
                                        copiedDBAdSelectionBuilder,
                                        biddingLogicJs,
                                        winningAdScoringOutcomeAndContext.second.mDebugReportList,
                                        winningAdScoringOutcomeAndContext
                                                .second
                                                .mWinnerAdScoringOutcome,
                                        winningAdScoringOutcomeAndContext
                                                .second
                                                .mSecondHighestAdScoringOutcome),
                        mLightweightExecutorService);
    }

    @VisibleForTesting
    FluentFuture<String> getWinnerBiddingLogicJs(AdScoringOutcome scoringOutcome) {
        String biddingLogicJs =
                (scoringOutcome.isBiddingLogicJsDownloaded())
                        ? scoringOutcome.getBiddingLogicJs()
                        : "";
        return FluentFuture.from(Futures.immediateFuture(biddingLogicJs));
    }

    private Map<AdTechIdentifier, List<DBCustomAudience>> mapBuyerToCustomAudience(
            final List<DBCustomAudience> customAudienceList) {
        Map<AdTechIdentifier, List<DBCustomAudience>> buyerToCustomAudienceMap = new HashMap<>();

        for (DBCustomAudience customAudience : customAudienceList) {
            buyerToCustomAudienceMap
                    .computeIfAbsent(customAudience.getBuyer(), k -> new ArrayList<>())
                    .add(customAudience);
        }
        sLogger.v("Created mapping for #%d buyers", buyerToCustomAudienceMap.size());
        return buyerToCustomAudienceMap;
    }

    /**
     * Given we no longer need to fetch data from web for this run of Ad Selection, we attempt to
     * clean up cache.
     */
    private void cleanUpCache() {
        ListenableFuture<?> unused =
                mBackgroundExecutorService.submit(
                        () -> mAdServicesHttpsClient.getAssociatedCache().cleanUp());
    }

    private static class AdSelectionContext {

        @NonNull final List<DebugReport> mDebugReportList;

        @Nullable final AdScoringOutcome mWinnerAdScoringOutcome;

        @Nullable final AdScoringOutcome mSecondHighestAdScoringOutcome;

        AdSelectionContext(
                @NonNull List<DebugReport> debugReports,
                @Nullable AdScoringOutcome winningAdScoringOutcome,
                @Nullable AdScoringOutcome secondHighestAdScoringOutcome) {
            this.mDebugReportList = debugReports;
            this.mWinnerAdScoringOutcome = winningAdScoringOutcome;
            this.mSecondHighestAdScoringOutcome = secondHighestAdScoringOutcome;
        }
    }
}

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

package com.android.adservices.service.stats;

import static com.android.adservices.service.stats.AdServicesStatsLog.AD_SERVICES_API_CALLED;
import static com.android.adservices.service.stats.AdServicesStatsLog.AD_SERVICES_API_CALLED__API_CLASS__UNKNOWN;
import static com.android.adservices.service.stats.AdServicesStatsLog.AD_SERVICES_EPOCH_COMPUTATION_CLASSIFIER_REPORTED;
import static com.android.adservices.service.stats.AdServicesStatsLog.AD_SERVICES_GET_TOPICS_REPORTED;
import static com.android.adservices.service.stats.AdServicesStatsLog.BACKGROUND_FETCH_PROCESS_REPORTED;
import static com.android.adservices.service.stats.AdServicesStatsLog.RUN_AD_BIDDING_PER_CA_PROCESS_REPORTED;
import static com.android.adservices.service.stats.AdServicesStatsLog.RUN_AD_BIDDING_PROCESS_REPORTED;
import static com.android.adservices.service.stats.AdServicesStatsLog.RUN_AD_SCORING_PROCESS_REPORTED;
import static com.android.adservices.service.stats.AdServicesStatsLog.RUN_AD_SELECTION_PROCESS_REPORTED;
import static com.android.adservices.service.stats.AdServicesStatsLog.UPDATE_CUSTOM_AUDIENCE_PROCESS_REPORTED;

import javax.annotation.concurrent.ThreadSafe;

/** AdServicesLogger that log stats to StatsD */
@ThreadSafe
public class StatsdAdServicesLogger implements AdServicesLogger {
    private static volatile StatsdAdServicesLogger sStatsdAdServicesLogger;

    private StatsdAdServicesLogger() {}

    /** Returns an instance of WestWorldAdServicesLogger. */
    public static StatsdAdServicesLogger getInstance() {
        if (sStatsdAdServicesLogger == null) {
            synchronized (StatsdAdServicesLogger.class) {
                if (sStatsdAdServicesLogger == null) {
                    sStatsdAdServicesLogger = new StatsdAdServicesLogger();
                }
            }
        }
        return sStatsdAdServicesLogger;
    }

    /** log method for measurement reporting. */
    public void logMeasurementReports(MeasurementReportsStats measurementReportsStats) {
        AdServicesStatsLog.write(
                measurementReportsStats.getCode(),
                measurementReportsStats.getType(),
                measurementReportsStats.getResultCode());
    }

    /** log method for API call stats. */
    public void logApiCallStats(ApiCallStats apiCallStats) {
        AdServicesStatsLog.write(
                apiCallStats.getCode(),
                apiCallStats.getApiClass(),
                apiCallStats.getApiName(),
                apiCallStats.getAppPackageName(),
                apiCallStats.getSdkPackageName(),
                apiCallStats.getLatencyMillisecond(),
                apiCallStats.getResultCode());
    }

    /** log method for UI stats. */
    public void logUIStats(UIStats uiStats) {
        AdServicesStatsLog.write(uiStats.getCode(), uiStats.getRegion(), uiStats.getAction());
    }

    @Override
    public void logFledgeApiCallStats(int apiName, int resultCode, int latencyMs) {
        AdServicesStatsLog.write(
                AD_SERVICES_API_CALLED,
                AD_SERVICES_API_CALLED__API_CLASS__UNKNOWN,
                apiName,
                "",
                "",
                latencyMs,
                resultCode);
    }

    @Override
    public void logMeasurementRegistrationsResponseSize(
            MeasurementRegistrationResponseStats stats) {
        AdServicesStatsLog.write(
                stats.getCode(),
                stats.getRegistrationType(),
                stats.getResponseSize(),
                stats.getAdTechDomain());
    }

    @Override
    public void logRunAdSelectionProcessReportedStats(RunAdSelectionProcessReportedStats stats) {
        AdServicesStatsLog.write(
                RUN_AD_SELECTION_PROCESS_REPORTED,
                stats.getIsRemarketingAdsWon(),
                stats.getAdSelectionEntrySizeInBytes(),
                stats.getPersistAdSelectionLatencyInMillis(),
                stats.getPersistAdSelectionResultCode(),
                stats.getRunAdSelectionLatencyInMillis(),
                stats.getRunAdSelectionResultCode());
    }

    @Override
    public void logRunAdBiddingProcessReportedStats(RunAdBiddingProcessReportedStats stats) {
        AdServicesStatsLog.write(
                RUN_AD_BIDDING_PROCESS_REPORTED,
                stats.getGetBuyersCustomAudienceLatencyInMills(),
                stats.getGetBuyersCustomAudienceResultCode(),
                stats.getNumBuyersRequested(),
                stats.getNumBuyersFetched(),
                stats.getNumOfAdsEnteringBidding(),
                stats.getNumOfCasEnteringBidding(),
                stats.getNumOfCasPostBidding(),
                stats.getRatioOfCasSelectingRmktAds(),
                stats.getRunAdBiddingLatencyInMillis(),
                stats.getRunAdBiddingResultCode(),
                stats.getTotalAdBiddingStageLatencyInMillis());
    }

    @Override
    public void logRunAdScoringProcessReportedStats(RunAdScoringProcessReportedStats stats) {
        AdServicesStatsLog.write(
                RUN_AD_SCORING_PROCESS_REPORTED,
                stats.getGetAdSelectionLogicLatencyInMillis(),
                stats.getGetAdSelectionLogicResultCode(),
                stats.getGetAdSelectionLogicScriptType(),
                stats.getFetchedAdSelectionLogicScriptSizeInBytes(),
                stats.getGetTrustedScoringSignalsLatencyInMillis(),
                stats.getGetTrustedScoringSignalsResultCode(),
                stats.getFetchedTrustedScoringSignalsDataSizeInBytes(),
                stats.getScoreAdsLatencyInMillis(),
                stats.getGetAdScoresLatencyInMillis(),
                stats.getGetAdScoresResultCode(),
                stats.getNumOfCasEnteringScoring(),
                stats.getNumOfRemarketingAdsEnteringScoring(),
                stats.getNumOfContextualAdsEnteringScoring(),
                stats.getRunAdScoringLatencyInMillis(),
                stats.getRunAdScoringResultCode());
    }

    @Override
    public void logRunAdBiddingPerCAProcessReportedStats(
            RunAdBiddingPerCAProcessReportedStats stats) {
        AdServicesStatsLog.write(
                RUN_AD_BIDDING_PER_CA_PROCESS_REPORTED,
                stats.getNumOfAdsForBidding(),
                stats.getRunAdBiddingPerCaLatencyInMillis(),
                stats.getRunAdBiddingPerCaResultCode(),
                stats.getGetBuyerDecisionLogicLatencyInMillis(),
                stats.getGetBuyerDecisionLogicResultCode(),
                stats.getBuyerDecisionLogicScriptType(),
                stats.getFetchedBuyerDecisionLogicScriptSizeInBytes(),
                stats.getNumOfKeysOfTrustedBiddingSignals(),
                stats.getFetchedTrustedBiddingSignalsDataSizeInBytes(),
                stats.getGetTrustedBiddingSignalsLatencyInMillis(),
                stats.getGetTrustedBiddingSignalsResultCode(),
                stats.getGenerateBidsLatencyInMillis(),
                stats.getRunBiddingLatencyInMillis(),
                stats.getRunBiddingResultCode());
    }

    @Override
    public void logBackgroundFetchProcessReportedStats(BackgroundFetchProcessReportedStats stats) {
        AdServicesStatsLog.write(
                BACKGROUND_FETCH_PROCESS_REPORTED,
                stats.getLatencyInMillis(),
                stats.getNumOfEligibleToUpdateCas(),
                stats.getResultCode());
    }

    @Override
    public void logUpdateCustomAudienceProcessReportedStats(
            UpdateCustomAudienceProcessReportedStats stats) {
        AdServicesStatsLog.write(
                UPDATE_CUSTOM_AUDIENCE_PROCESS_REPORTED,
                stats.getLatencyInMills(),
                stats.getResultCode(),
                stats.getDataSizeOfAdsInBytes(),
                stats.getNumOfAds());
    }

    @Override
    public void logGetTopicsReportedStats(GetTopicsReportedStats stats) {
        AdServicesStatsLog.write(
                AD_SERVICES_GET_TOPICS_REPORTED,
                new int[] {}, // TODO(b/256649873): Log empty topic ids until the long term
                // solution.
                stats.getDuplicateTopicCount(),
                stats.getFilteredBlockedTopicCount(),
                stats.getTopicIdsCount());
    }

    @Override
    public void logEpochComputationGetTopTopicsStats(EpochComputationGetTopTopicsStats stats) {
        AdServicesStatsLog.write(
                AD_SERVICES_EPOCH_COMPUTATION_CLASSIFIER_REPORTED,
                stats.getTopTopicCount(),
                stats.getPaddedRandomTopicsCount(),
                stats.getAppsConsideredCount(),
                stats.getSdksConsideredCount());
    }

    @Override
    public void logEpochComputationClassifierStats(EpochComputationClassifierStats stats) {
        AdServicesStatsLog.write(
                AD_SERVICES_EPOCH_COMPUTATION_CLASSIFIER_REPORTED,
                stats.getTopicIds().stream().mapToInt(Integer::intValue).toArray(),
                stats.getBuildId(),
                stats.getAssetVersion(),
                stats.getClassifierType(),
                stats.getOnDeviceClassifierStatus(),
                stats.getPrecomputedClassifierStatus());
    }
}

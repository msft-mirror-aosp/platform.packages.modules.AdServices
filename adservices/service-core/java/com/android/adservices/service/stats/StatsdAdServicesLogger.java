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

import static com.android.adservices.service.stats.AdServicesStatsLog.ADSERVICES_SHELL_COMMAND_CALLED;
import static com.android.adservices.service.stats.AdServicesStatsLog.AD_COUNTER_HISTOGRAM_UPDATER_REPORTED;
import static com.android.adservices.service.stats.AdServicesStatsLog.AD_FILTERING_PROCESS_AD_SELECTION_REPORTED;
import static com.android.adservices.service.stats.AdServicesStatsLog.AD_FILTERING_PROCESS_JOIN_CA_REPORTED;
import static com.android.adservices.service.stats.AdServicesStatsLog.AD_SERVICES_API_CALLED;
import static com.android.adservices.service.stats.AdServicesStatsLog.AD_SERVICES_API_CALLED__API_CLASS__UNKNOWN;
import static com.android.adservices.service.stats.AdServicesStatsLog.AD_SERVICES_BACK_COMPAT_EPOCH_COMPUTATION_CLASSIFIER_REPORTED;
import static com.android.adservices.service.stats.AdServicesStatsLog.AD_SERVICES_BACK_COMPAT_GET_TOPICS_REPORTED;
import static com.android.adservices.service.stats.AdServicesStatsLog.AD_SERVICES_CONSENT_MIGRATED;
import static com.android.adservices.service.stats.AdServicesStatsLog.AD_SERVICES_ENCRYPTION_KEY_DB_TRANSACTION_ENDED;
import static com.android.adservices.service.stats.AdServicesStatsLog.AD_SERVICES_ENCRYPTION_KEY_FETCHED;
import static com.android.adservices.service.stats.AdServicesStatsLog.AD_SERVICES_ENROLLMENT_DATA_STORED;
import static com.android.adservices.service.stats.AdServicesStatsLog.AD_SERVICES_ENROLLMENT_FAILED;
import static com.android.adservices.service.stats.AdServicesStatsLog.AD_SERVICES_ENROLLMENT_FILE_DOWNLOADED;
import static com.android.adservices.service.stats.AdServicesStatsLog.AD_SERVICES_ENROLLMENT_MATCHED;
import static com.android.adservices.service.stats.AdServicesStatsLog.AD_SERVICES_ENROLLMENT_TRANSACTION_STATS;
import static com.android.adservices.service.stats.AdServicesStatsLog.AD_SERVICES_EPOCH_COMPUTATION_CLASSIFIER_REPORTED;
import static com.android.adservices.service.stats.AdServicesStatsLog.AD_SERVICES_EPOCH_COMPUTATION_GET_TOP_TOPICS_REPORTED;
import static com.android.adservices.service.stats.AdServicesStatsLog.AD_SERVICES_GET_TOPICS_REPORTED;
import static com.android.adservices.service.stats.AdServicesStatsLog.AD_SERVICES_MEASUREMENT_AD_ID_MATCH_FOR_DEBUG_KEYS;
import static com.android.adservices.service.stats.AdServicesStatsLog.AD_SERVICES_MEASUREMENT_CLICK_VERIFICATION;
import static com.android.adservices.service.stats.AdServicesStatsLog.AD_SERVICES_MEASUREMENT_DEBUG_KEYS;
import static com.android.adservices.service.stats.AdServicesStatsLog.APP_MANIFEST_CONFIG_HELPER_CALLED;
import static com.android.adservices.service.stats.AdServicesStatsLog.BACKGROUND_FETCH_PROCESS_REPORTED;
import static com.android.adservices.service.stats.AdServicesStatsLog.DESTINATION_REGISTERED_BEACONS;
import static com.android.adservices.service.stats.AdServicesStatsLog.ENCODING_JOB_RUN;
import static com.android.adservices.service.stats.AdServicesStatsLog.ENCODING_JS_EXECUTION;
import static com.android.adservices.service.stats.AdServicesStatsLog.ENCODING_JS_FETCH;
import static com.android.adservices.service.stats.AdServicesStatsLog.GET_AD_SELECTION_DATA_API_CALLED;
import static com.android.adservices.service.stats.AdServicesStatsLog.GET_AD_SELECTION_DATA_BUYER_INPUT_GENERATED;
import static com.android.adservices.service.stats.AdServicesStatsLog.INTERACTION_REPORTING_TABLE_CLEARED;
import static com.android.adservices.service.stats.AdServicesStatsLog.K_ANON_BACKGROUND_JOB_STATUS_REPORTED;
import static com.android.adservices.service.stats.AdServicesStatsLog.K_ANON_IMMEDIATE_SIGN_JOIN_STATUS_REPORTED;
import static com.android.adservices.service.stats.AdServicesStatsLog.K_ANON_INITIALIZE_STATUS_REPORTED;
import static com.android.adservices.service.stats.AdServicesStatsLog.K_ANON_JOIN_STATUS_REPORTED;
import static com.android.adservices.service.stats.AdServicesStatsLog.K_ANON_KEY_ATTESTATION_STATUS_REPORTED;
import static com.android.adservices.service.stats.AdServicesStatsLog.K_ANON_SIGN_STATUS_REPORTED;
import static com.android.adservices.service.stats.AdServicesStatsLog.PERSIST_AD_SELECTION_RESULT_CALLED;
import static com.android.adservices.service.stats.AdServicesStatsLog.REPORT_IMPRESSION_API_CALLED;
import static com.android.adservices.service.stats.AdServicesStatsLog.REPORT_INTERACTION_API_CALLED;
import static com.android.adservices.service.stats.AdServicesStatsLog.RUN_AD_BIDDING_PER_CA_PROCESS_REPORTED;
import static com.android.adservices.service.stats.AdServicesStatsLog.RUN_AD_BIDDING_PROCESS_REPORTED;
import static com.android.adservices.service.stats.AdServicesStatsLog.RUN_AD_SCORING_PROCESS_REPORTED;
import static com.android.adservices.service.stats.AdServicesStatsLog.RUN_AD_SELECTION_PROCESS_REPORTED;
import static com.android.adservices.service.stats.AdServicesStatsLog.SCHEDULED_CUSTOM_AUDIENCE_UPDATE_BACKGROUND_JOB_RAN;
import static com.android.adservices.service.stats.AdServicesStatsLog.SCHEDULED_CUSTOM_AUDIENCE_UPDATE_PERFORMED;
import static com.android.adservices.service.stats.AdServicesStatsLog.SCHEDULED_CUSTOM_AUDIENCE_UPDATE_PERFORMED_ATTEMPTED_FAILURE_REPORTED;
import static com.android.adservices.service.stats.AdServicesStatsLog.SCHEDULED_CUSTOM_AUDIENCE_UPDATE_SCHEDULE_ATTEMPTED;
import static com.android.adservices.service.stats.AdServicesStatsLog.SELECT_ADS_FROM_OUTCOMES_API_CALLED;
import static com.android.adservices.service.stats.AdServicesStatsLog.SERVER_AUCTION_BACKGROUND_KEY_FETCH_ENABLED;
import static com.android.adservices.service.stats.AdServicesStatsLog.SERVER_AUCTION_KEY_FETCH_CALLED;
import static com.android.adservices.service.stats.AdServicesStatsLog.SIGNATURE_VERIFICATION;
import static com.android.adservices.service.stats.AdServicesStatsLog.TOPICS_ENCRYPTION_EPOCH_COMPUTATION_REPORTED;
import static com.android.adservices.service.stats.AdServicesStatsLog.TOPICS_ENCRYPTION_GET_TOPICS_REPORTED;
import static com.android.adservices.service.stats.AdServicesStatsLog.TOPICS_SCHEDULE_EPOCH_JOB_SETTING_REPORTED;
import static com.android.adservices.service.stats.AdServicesStatsLog.UPDATE_CUSTOM_AUDIENCE_PROCESS_REPORTED;
import static com.android.adservices.service.stats.AdServicesStatsLog.UPDATE_SIGNALS_API_CALLED;
import static com.android.adservices.service.stats.AdServicesStatsLog.UPDATE_SIGNALS_PROCESS_REPORTED;

import android.annotation.NonNull;
import android.annotation.Nullable;
import android.util.proto.ProtoOutputStream;

import com.android.adservices.service.Flags;
import com.android.adservices.service.FlagsFactory;
import com.android.adservices.service.common.AllowLists;
import com.android.adservices.service.common.AppManifestConfigCall;
import com.android.adservices.service.common.BinderFlagReader;
import com.android.adservices.service.stats.kanon.KAnonBackgroundJobStatusStats;
import com.android.adservices.service.stats.kanon.KAnonGetChallengeStatusStats;
import com.android.adservices.service.stats.kanon.KAnonImmediateSignJoinStatusStats;
import com.android.adservices.service.stats.kanon.KAnonInitializeStatusStats;
import com.android.adservices.service.stats.kanon.KAnonJoinStatusStats;
import com.android.adservices.service.stats.kanon.KAnonSignStatusStats;
import com.android.adservices.service.stats.pas.EncodingFetchStats;
import com.android.adservices.service.stats.pas.EncodingJobRunStats;
import com.android.adservices.service.stats.pas.EncodingJsExecutionStats;
import com.android.adservices.service.stats.pas.PersistAdSelectionResultCalledStats;
import com.android.adservices.service.stats.pas.UpdateSignalsApiCalledStats;
import com.android.adservices.service.stats.pas.UpdateSignalsProcessReportedStats;
import com.android.internal.annotations.GuardedBy;
import com.android.internal.annotations.VisibleForTesting;
import com.android.modules.utils.build.SdkLevel;

import java.util.Objects;

import javax.annotation.concurrent.ThreadSafe;

/** {@link AdServicesLogger} that log stats to StatsD. */
@ThreadSafe
public class StatsdAdServicesLogger implements AdServicesLogger {
    private static final int AD_SERVICES_TOPIC_IDS_FIELD_ID = 1;

    @GuardedBy("SINGLETON_LOCK")
    private static volatile StatsdAdServicesLogger sStatsdAdServicesLogger;

    private static final Object SINGLETON_LOCK = new Object();

    @NonNull private final Flags mFlags;

    @VisibleForTesting
    StatsdAdServicesLogger(@NonNull Flags flags) {
        this.mFlags = Objects.requireNonNull(flags);
    }

    /** Returns an instance of {@link StatsdAdServicesLogger}. */
    public static StatsdAdServicesLogger getInstance() {
        if (sStatsdAdServicesLogger == null) {
            synchronized (SINGLETON_LOCK) {
                if (sStatsdAdServicesLogger == null) {
                    sStatsdAdServicesLogger = new StatsdAdServicesLogger(FlagsFactory.getFlags());
                }
            }
        }
        return sStatsdAdServicesLogger;
    }

    private String getAllowlistedAppPackageName(String appPackageName) {
        if (!mFlags.getMeasurementEnableAppPackageNameLogging()
                || !AllowLists.isPackageAllowListed(
                        mFlags.getMeasurementAppPackageNameLoggingAllowlist(), appPackageName)) {
            return "";
        }
        return appPackageName;
    }

    /** log method for measurement reporting. */
    public void logMeasurementReports(
            MeasurementReportsStats measurementReportsStats, @Nullable String enrollmentId) {
        AdServicesStatsLog.write(
                measurementReportsStats.getCode(),
                measurementReportsStats.getType(),
                measurementReportsStats.getResultCode(),
                measurementReportsStats.getFailureType(),
                measurementReportsStats.getUploadMethod(),
                measurementReportsStats.getReportingDelay(),
                getAllowlistedAppPackageName(measurementReportsStats.getSourceRegistrant()),
                measurementReportsStats.getRetryCount(),
                /* httpResponseCode */ 0,
                /* isMarkedForDeletion */ false);
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
        AdServicesStatsLog.write(
                uiStats.getCode(),
                uiStats.getRegion(),
                uiStats.getAction(),
                uiStats.getDefaultConsent(),
                uiStats.getDefaultAdIdState(),
                /* @deprecated feature_type= */ 0,
                uiStats.getUx(),
                uiStats.getEnrollmentChannel());
    }

    @Override
    public void logFledgeApiCallStats(
            int apiName, String appPackageName, int resultCode, int latencyMs) {
        boolean enabled = BinderFlagReader.readFlag(mFlags::getFledgeAppPackageNameLoggingEnabled);
        if (enabled && (appPackageName != null)) {
            AdServicesStatsLog.write(
                    AD_SERVICES_API_CALLED,
                    AD_SERVICES_API_CALLED__API_CLASS__UNKNOWN,
                    apiName,
                    appPackageName,
                    "",
                    latencyMs,
                    resultCode);
        } else {
            logFledgeApiCallStats(apiName, resultCode, latencyMs);
        }
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
            MeasurementRegistrationResponseStats stats, @Nullable String enrollmentId) {
        AdServicesStatsLog.write(
                stats.getCode(),
                stats.getRegistrationType(),
                stats.getResponseSize(),
                stats.getAdTechDomain(),
                stats.getInteractionType(),
                stats.getSurfaceType(),
                stats.getRegistrationStatus(),
                stats.getFailureType(),
                stats.getRegistrationDelay(),
                getAllowlistedAppPackageName(stats.getSourceRegistrant()),
                stats.getRetryCount(),
                /* httpResponseCode */ 0,
                stats.isRedirectOnly(),
                stats.isPARequest(),
                stats.getNumDeletedEntities(),
                stats.isEventLevelEpsilonEnabled(),
                stats.isTriggerAggregatableValueFiltersConfigured(),
                stats.isTriggerFilteringIdConfigured());
    }

    @Override
    public void logRunAdSelectionProcessReportedStats(RunAdSelectionProcessReportedStats stats) {
        AdServicesStatsLog.write(
                RUN_AD_SELECTION_PROCESS_REPORTED,
                stats.getIsRemarketingAdsWon(),
                stats.getDBAdSelectionSizeInBytes(),
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
                stats.getRunAdScoringResultCode(),
                stats.getScoreAdSellerAdditionalSignalsContainedDataVersion(),
                stats.getScoreAdJsScriptResultCode());
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
                stats.getRunBiddingResultCode(),
                stats.getRunAdBiddingPerCaReturnedAdCost(),
                stats.getGenerateBidBuyerAdditionalSignalsContainedDataVersion(),
                stats.getGenerateBidJsScriptResultCode());
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
        int[] topicIds = new int[] {};
        if (stats.getTopicIds() != null) {
            topicIds = stats.getTopicIds().stream().mapToInt(Integer::intValue).toArray();
        }

        boolean isCompatLoggingEnabled = !mFlags.getCompatLoggingKillSwitch();
        if (isCompatLoggingEnabled) {
            long modeBytesFieldId =
                    ProtoOutputStream.FIELD_COUNT_REPEATED // topic_ids field is repeated.
                            // topic_id is represented by int32 type.
                            | ProtoOutputStream.FIELD_TYPE_INT32
                            // Field ID of topic_ids field in AdServicesTopicIds proto.
                            | AD_SERVICES_TOPIC_IDS_FIELD_ID;

            AdServicesStatsLog.write(
                    AD_SERVICES_BACK_COMPAT_GET_TOPICS_REPORTED,
                    // TODO(b/266626836) Add topic ids logging once long term solution is identified
                    stats.getDuplicateTopicCount(),
                    stats.getFilteredBlockedTopicCount(),
                    stats.getTopicIdsCount(),
                    toBytes(modeBytesFieldId, topicIds));
        }

        // This atom can only be logged on T+ due to usage of repeated fields. See go/rbc-ww-logging
        // for why we are temporarily double logging on T+.s
        if (SdkLevel.isAtLeastT()) {
            AdServicesStatsLog.write(
                    AD_SERVICES_GET_TOPICS_REPORTED,
                    topicIds,
                    stats.getDuplicateTopicCount(),
                    stats.getFilteredBlockedTopicCount(),
                    stats.getTopicIdsCount());
        }
    }

    @Override
    public void logEpochComputationGetTopTopicsStats(EpochComputationGetTopTopicsStats stats) {
        AdServicesStatsLog.write(
                AD_SERVICES_EPOCH_COMPUTATION_GET_TOP_TOPICS_REPORTED,
                stats.getTopTopicCount(),
                stats.getPaddedRandomTopicsCount(),
                stats.getAppsConsideredCount(),
                stats.getSdksConsideredCount());
    }

    @Override
    public void logEpochComputationClassifierStats(EpochComputationClassifierStats stats) {
        int[] topicIds = stats.getTopicIds().stream().mapToInt(Integer::intValue).toArray();

        boolean isCompatLoggingEnabled = !mFlags.getCompatLoggingKillSwitch();
        if (isCompatLoggingEnabled) {
            long modeBytesFieldId =
                    ProtoOutputStream.FIELD_COUNT_REPEATED // topic_ids field is repeated.
                            // topic_id is represented by int32 type.
                            | ProtoOutputStream.FIELD_TYPE_INT32
                            // Field ID of topic_ids field in AdServicesTopicIds proto.
                            | AD_SERVICES_TOPIC_IDS_FIELD_ID;

            AdServicesStatsLog.write(
                    AD_SERVICES_BACK_COMPAT_EPOCH_COMPUTATION_CLASSIFIER_REPORTED,
                    toBytes(modeBytesFieldId, topicIds),
                    stats.getBuildId(),
                    stats.getAssetVersion(),
                    stats.getClassifierType().getCompatLoggingValue(),
                    stats.getOnDeviceClassifierStatus().getCompatLoggingValue(),
                    stats.getPrecomputedClassifierStatus().getCompatLoggingValue());
        }

        // This atom can only be logged on T+ due to usage of repeated fields. See go/rbc-ww-logging
        // for why we are temporarily double logging on T+.
        if (SdkLevel.isAtLeastT()) {
            AdServicesStatsLog.write(
                    AD_SERVICES_EPOCH_COMPUTATION_CLASSIFIER_REPORTED,
                    topicIds,
                    stats.getBuildId(),
                    stats.getAssetVersion(),
                    stats.getClassifierType().getLoggingValue(),
                    stats.getOnDeviceClassifierStatus().getLoggingValue(),
                    stats.getPrecomputedClassifierStatus().getLoggingValue());
        }
    }

    @Override
    public void logMeasurementDebugKeysMatch(MsmtDebugKeysMatchStats stats) {
        AdServicesStatsLog.write(
                AD_SERVICES_MEASUREMENT_DEBUG_KEYS,
                stats.getAdTechEnrollmentId(),
                stats.getAttributionType(),
                stats.isMatched(),
                stats.getDebugJoinKeyHashedValue(),
                stats.getDebugJoinKeyHashLimit(),
                getAllowlistedAppPackageName(stats.getSourceRegistrant()));
    }

    @Override
    public void logMeasurementAdIdMatchForDebugKeysStats(MsmtAdIdMatchForDebugKeysStats stats) {
        AdServicesStatsLog.write(
                AD_SERVICES_MEASUREMENT_AD_ID_MATCH_FOR_DEBUG_KEYS,
                stats.getAdTechEnrollmentId(),
                stats.getAttributionType(),
                stats.isMatched(),
                stats.getNumUniqueAdIds(),
                stats.getNumUniqueAdIdsLimit(),
                getAllowlistedAppPackageName(stats.getSourceRegistrant()));
    }

    /** log method for measurement attribution. */
    public void logMeasurementAttributionStats(
            MeasurementAttributionStats measurementAttributionStats,
            @Nullable String enrollmentId) {
        AdServicesStatsLog.write(
                measurementAttributionStats.getCode(),
                measurementAttributionStats.getSourceType(),
                measurementAttributionStats.getSurfaceType(),
                measurementAttributionStats.getResult(),
                measurementAttributionStats.getFailureType(),
                measurementAttributionStats.isSourceDerived(),
                measurementAttributionStats.isInstallAttribution(),
                measurementAttributionStats.getAttributionDelay(),
                getAllowlistedAppPackageName(measurementAttributionStats.getSourceRegistrant()),
                measurementAttributionStats.getAggregateReportCount(),
                measurementAttributionStats.getAggregateDebugReportCount(),
                measurementAttributionStats.getEventReportCount(),
                measurementAttributionStats.getEventDebugReportCount(),
                /* retryCount */ 0,
                measurementAttributionStats.getNullAggregateReportCount());
    }

    /** log method for measurement wipeout. */
    public void logMeasurementWipeoutStats(MeasurementWipeoutStats measurementWipeoutStats) {
        AdServicesStatsLog.write(
                measurementWipeoutStats.getCode(),
                measurementWipeoutStats.getWipeoutType(),
                getAllowlistedAppPackageName(measurementWipeoutStats.getSourceRegistrant()));
    }

    /** log method for measurement attribution. */
    public void logMeasurementDelayedSourceRegistrationStats(
            MeasurementDelayedSourceRegistrationStats measurementDelayedSourceRegistrationStats) {
        AdServicesStatsLog.write(
                measurementDelayedSourceRegistrationStats.getCode(),
                measurementDelayedSourceRegistrationStats.getRegistrationStatus(),
                measurementDelayedSourceRegistrationStats.getRegistrationDelay(),
                getAllowlistedAppPackageName(
                        measurementDelayedSourceRegistrationStats.getRegistrant()));
    }

    /** Log method for measurement click verification. */
    public void logMeasurementClickVerificationStats(
            MeasurementClickVerificationStats measurementClickVerificationStats) {
        AdServicesStatsLog.write(
                AD_SERVICES_MEASUREMENT_CLICK_VERIFICATION,
                measurementClickVerificationStats.getSourceType(),
                measurementClickVerificationStats.isInputEventPresent(),
                measurementClickVerificationStats.isSystemClickVerificationSuccessful(),
                measurementClickVerificationStats.isSystemClickVerificationEnabled(),
                measurementClickVerificationStats.getInputEventDelayMillis(),
                measurementClickVerificationStats.getValidDelayWindowMillis(),
                getAllowlistedAppPackageName(
                        measurementClickVerificationStats.getSourceRegistrant()),
                measurementClickVerificationStats.isClickDeduplicationEnabled(),
                measurementClickVerificationStats.isClickDeduplicationEnforced(),
                measurementClickVerificationStats.getMaxSourcesPerClick(),
                measurementClickVerificationStats
                        .isCurrentRegistrationUnderClickDeduplicationLimit());
    }

    /** Logs measurement ODP registrations. */
    public void logMeasurementOdpRegistrations(MeasurementOdpRegistrationStats stats) {
        AdServicesStatsLog.write(
                stats.getCode(), stats.getRegistrationType(), stats.getRegistrationStatus());
    }

    /** Logs measurement ODP API calls. */
    public void logMeasurementOdpApiCall(MeasurementOdpApiCallStats stats) {
        AdServicesStatsLog.write(stats.getCode(), stats.getLatency(), stats.getApiCallStatus());
    }

    /** log method for consent migrations. */
    public void logConsentMigrationStats(ConsentMigrationStats stats) {
        if (mFlags.getAdservicesConsentMigrationLoggingEnabled()) {
            AdServicesStatsLog.write(
                    AD_SERVICES_CONSENT_MIGRATED,
                    stats.getMsmtConsent(),
                    stats.getTopicsConsent(),
                    stats.getFledgeConsent(),
                    true,
                    stats.getMigrationType().getMigrationTypeValue(),
                    stats.getRegion(),
                    stats.getMigrationStatus().getMigrationStatusValue());
        }
    }

    /** log method for read/write status of enrollment data. */
    public void logEnrollmentDataStats(int mType, boolean mIsSuccessful, int mBuildId) {
        AdServicesStatsLog.write(
                AD_SERVICES_ENROLLMENT_DATA_STORED, mType, mIsSuccessful, mBuildId);
    }

    /** log method for status of enrollment matching queries. */
    public void logEnrollmentMatchStats(boolean mIsSuccessful, int mBuildId) {
        AdServicesStatsLog.write(AD_SERVICES_ENROLLMENT_MATCHED, mIsSuccessful, mBuildId);
    }

    /** log method for status of enrollment downloads. */
    public void logEnrollmentFileDownloadStats(boolean mIsSuccessful, int mBuildId) {
        AdServicesStatsLog.write(AD_SERVICES_ENROLLMENT_FILE_DOWNLOADED, mIsSuccessful, mBuildId);
    }

    /** log method for enrollment-related status_caller_not_found errors. */
    public void logEnrollmentFailedStats(
            int mBuildId,
            int mDataFileGroupStatus,
            int mEnrollmentRecordCountInTable,
            String mQueryParameter,
            int mErrorCause) {
        AdServicesStatsLog.write(
                AD_SERVICES_ENROLLMENT_FAILED,
                mBuildId,
                mDataFileGroupStatus,
                mEnrollmentRecordCountInTable,
                mQueryParameter,
                mErrorCause);
    }

    @Override
    public void logEnrollmentTransactionStats(AdServicesEnrollmentTransactionStats stats) {
        AdServicesStatsLog.write(
                AD_SERVICES_ENROLLMENT_TRANSACTION_STATS,
                stats.transactionType().getValue(),
                stats.transactionStatus().getValue(),
                stats.transactionParameterCount(),
                stats.transactionResultCount(),
                stats.queryResultCount(),
                stats.dataSourceRecordCountPre(),
                stats.dataSourceRecordCountPost(),
                stats.enrollmentFileBuildId());
    }

    /** Logs encryption key fetch stats. */
    @Override
    public void logEncryptionKeyFetchedStats(AdServicesEncryptionKeyFetchedStats stats) {
        AdServicesStatsLog.write(
                AD_SERVICES_ENCRYPTION_KEY_FETCHED,
                stats.getFetchJobType().getValue(),
                stats.getFetchStatus().getValue(),
                stats.getIsFirstTimeFetch(),
                stats.getAdtechEnrollmentId(),
                "",
                stats.getEncryptionKeyUrl());
    }

    /** Logs encryption key datastore transaction ended stats. */
    @Override
    public void logEncryptionKeyDbTransactionEndedStats(
            AdServicesEncryptionKeyDbTransactionEndedStats stats) {
        AdServicesStatsLog.write(
                AD_SERVICES_ENCRYPTION_KEY_DB_TRANSACTION_ENDED,
                stats.getDbTransactionType().getValue(),
                stats.getDbTransactionStatus().getValue(),
                stats.getMethodName().getValue());
    }

    /** Logs destinationRegisteredBeacon reported stats. */
    @Override
    public void logDestinationRegisteredBeaconsReportedStats(
            DestinationRegisteredBeaconsReportedStats stats) {
        int[] attemptedKeySizesRangeType = new int[] {};
        if (stats.getAttemptedKeySizesRangeType() != null) {
            attemptedKeySizesRangeType =
                    stats.getAttemptedKeySizesRangeType().stream()
                            .mapToInt(
                                    DestinationRegisteredBeaconsReportedStats
                                                    .InteractionKeySizeRangeType
                                            ::getValue)
                            .toArray();
        }
        // TODO: b/325098723 - Add support for DestinationRegisteredBeacons for S- devices
        if (SdkLevel.isAtLeastT()) {
            AdServicesStatsLog.write(
                    DESTINATION_REGISTERED_BEACONS,
                    stats.getBeaconReportingDestinationType(),
                    stats.getAttemptedRegisteredBeacons(),
                    attemptedKeySizesRangeType,
                    stats.getTableNumRows(),
                    stats.getAdServicesStatusCode(),
                    // TODO: b/329720016 - implement and flag
                    0);
        }
    }

    /** Logs beacon level reporting for ReportInteraction API called stats. */
    @Override
    public void logReportInteractionApiCalledStats(ReportInteractionApiCalledStats stats) {
        AdServicesStatsLog.write(
                REPORT_INTERACTION_API_CALLED,
                stats.getBeaconReportingDestinationType(),
                stats.getNumMatchingUris());
    }

    /** Logs beacon level reporting for clearing interaction reporting table stats. */
    @Override
    public void logInteractionReportingTableClearedStats(
            InteractionReportingTableClearedStats stats) {
        AdServicesStatsLog.write(
                INTERACTION_REPORTING_TABLE_CLEARED,
                stats.getNumUrisCleared(),
                stats.getNumUnreportedUris());
    }

    @Override
    public void logAppManifestConfigCall(AppManifestConfigCall call) {
        AdServicesStatsLog.write(
                APP_MANIFEST_CONFIG_HELPER_CALLED, call.packageName, call.api, call.result);
    }

    @Override
    public void logKAnonSignJoinStatus() {
        // TODO(b/324564459): add logging for KAnon Sign Join
    }

    @Override
    public void logKAnonInitializeStats(KAnonInitializeStatusStats kAnonInitializeStatusStats) {
        AdServicesStatsLog.write(
                K_ANON_INITIALIZE_STATUS_REPORTED,
                kAnonInitializeStatusStats.getWasSuccessful(),
                kAnonInitializeStatusStats.getKAnonAction(),
                kAnonInitializeStatusStats.getKAnonActionFailureReason(),
                kAnonInitializeStatusStats.getLatencyInMs());
    }

    @Override
    public void logKAnonSignStats(KAnonSignStatusStats kAnonSignStatusStats) {
        AdServicesStatsLog.write(
                K_ANON_SIGN_STATUS_REPORTED,
                kAnonSignStatusStats.getWasSuccessful(),
                kAnonSignStatusStats.getKAnonAction(),
                kAnonSignStatusStats.getKAnonActionFailureReason(),
                kAnonSignStatusStats.getBatchSize(),
                kAnonSignStatusStats.getLatencyInMs());
    }

    @Override
    public void logKAnonJoinStats(KAnonJoinStatusStats kAnonJoinStatusStats) {
        AdServicesStatsLog.write(
                K_ANON_JOIN_STATUS_REPORTED,
                kAnonJoinStatusStats.getWasSuccessful(),
                kAnonJoinStatusStats.getTotalMessages(),
                kAnonJoinStatusStats.getNumberOfFailedMessages(),
                kAnonJoinStatusStats.getLatencyInMs());
    }

    @Override
    public void logKAnonBackgroundJobStats(
            KAnonBackgroundJobStatusStats kAnonBackgroundJobStatusStats) {
        AdServicesStatsLog.write(
                K_ANON_BACKGROUND_JOB_STATUS_REPORTED,
                kAnonBackgroundJobStatusStats.getKAnonJobResult(),
                kAnonBackgroundJobStatusStats.getTotalMessagesAttempted(),
                kAnonBackgroundJobStatusStats.getMessagesInDBLeft(),
                kAnonBackgroundJobStatusStats.getMessagesFailedToJoin(),
                kAnonBackgroundJobStatusStats.getMessagesFailedToSign(),
                kAnonBackgroundJobStatusStats.getLatencyInMs());
    }

    @Override
    public void logKAnonImmediateSignJoinStats(
            KAnonImmediateSignJoinStatusStats kAnonImmediateSignJoinStatusStats) {
        AdServicesStatsLog.write(
                K_ANON_IMMEDIATE_SIGN_JOIN_STATUS_REPORTED,
                kAnonImmediateSignJoinStatusStats.getKAnonJobResult(),
                kAnonImmediateSignJoinStatusStats.getTotalMessagesAttempted(),
                kAnonImmediateSignJoinStatusStats.getMessagesFailedToJoin(),
                kAnonImmediateSignJoinStatusStats.getMessagesFailedToSign(),
                kAnonImmediateSignJoinStatusStats.getLatencyInMs());
    }

    @Override
    public void logKAnonGetChallengeJobStats(
            KAnonGetChallengeStatusStats kAnonGetChallengeStatusStats) {
        AdServicesStatsLog.write(
                K_ANON_KEY_ATTESTATION_STATUS_REPORTED,
                kAnonGetChallengeStatusStats.getCertificateSizeInBytes(),
                kAnonGetChallengeStatusStats.getResultCode(),
                kAnonGetChallengeStatusStats.getLatencyInMs());
    }

    @Override
    public void logGetAdSelectionDataApiCalledStats(GetAdSelectionDataApiCalledStats stats) {
        AdServicesStatsLog.write(
                GET_AD_SELECTION_DATA_API_CALLED,
                stats.getPayloadSizeKb(),
                stats.getNumBuyers(),
                stats.getStatusCode(),
                stats.getServerAuctionCoordinatorSource(),
                stats.getSellerMaxSizeKb(),
                stats.getPayloadOptimizationResult().getValue(),
                stats.getInputGenerationLatencyMs(),
                stats.getCompressedBuyerInputCreatorVersion(),
                stats.getNumReEstimations());
    }

    @Override
    public void logGetAdSelectionDataBuyerInputGeneratedStats(
            GetAdSelectionDataBuyerInputGeneratedStats stats) {
        AdServicesStatsLog.write(
                GET_AD_SELECTION_DATA_BUYER_INPUT_GENERATED,
                stats.getNumCustomAudiences(),
                stats.getNumCustomAudiencesOmitAds(),
                stats.getCustomAudienceSizeMeanB(),
                stats.getCustomAudienceSizeVarianceB(),
                stats.getTrustedBiddingSignalsKeysSizeMeanB(),
                stats.getTrustedBiddingSignalsKeysSizeVarianceB(),
                stats.getUserBiddingSignalsSizeMeanB(),
                stats.getUserBiddingSignalsSizeVarianceB(),
                stats.getNumEncodedSignals(),
                stats.getEncodedSignalsSizeMean(),
                stats.getEncodedSignalsSizeMax(),
                stats.getEncodedSignalsSizeMin());
    }

    @Override
    public void logEncodingJsFetchStats(EncodingFetchStats stats) {
        AdServicesStatsLog.write(
                ENCODING_JS_FETCH,
                stats.getJsDownloadTime(),
                stats.getHttpResponseCode(),
                stats.getFetchStatus(),
                stats.getAdTechId());
    }

    @Override
    public void logAdFilteringProcessJoinCAReportedStats(
            AdFilteringProcessJoinCAReportedStats stats) {
        AdServicesStatsLog.write(
                AD_FILTERING_PROCESS_JOIN_CA_REPORTED,
                stats.getStatusCode(),
                stats.getCountOfAdsWithKeysMuchSmallerThanLimitation(),
                stats.getCountOfAdsWithKeysSmallerThanLimitation(),
                stats.getCountOfAdsWithKeysEqualToLimitation(),
                stats.getCountOfAdsWithKeysLargerThanLimitation(),
                stats.getCountOfAdsWithEmptyKeys(),
                stats.getCountOfAdsWithFiltersMuchSmallerThanLimitation(),
                stats.getCountOfAdsWithFiltersSmallerThanLimitation(),
                stats.getCountOfAdsWithFiltersEqualToLimitation(),
                stats.getCountOfAdsWithFiltersLargerThanLimitation(),
                stats.getCountOfAdsWithEmptyFilters(),
                stats.getTotalNumberOfUsedKeys(),
                stats.getTotalNumberOfUsedFilters());
    }

    @Override
    public void logAdFilteringProcessAdSelectionReportedStats(
            AdFilteringProcessAdSelectionReportedStats stats) {
        AdServicesStatsLog.write(
                AD_FILTERING_PROCESS_AD_SELECTION_REPORTED,
                stats.getLatencyInMillisOfAllAdFiltering(),
                stats.getLatencyInMillisOfAppInstallFiltering(),
                stats.getLatencyInMillisOfFcapFilters(),
                stats.getStatusCode(),
                stats.getNumOfAdsFilteredOutOfBidding(),
                stats.getNumOfCustomAudiencesFilteredOutOfBidding(),
                stats.getTotalNumOfAdsBeforeFiltering(),
                stats.getTotalNumOfCustomAudiencesBeforeFiltering(),
                stats.getNumOfPackageInAppInstallFilters(),
                stats.getNumOfDbOperations(),
                stats.getFilterProcessType(),
                stats.getNumOfContextualAdsFiltered(),
                stats.getNumOfAdCounterKeysInFcapFilters(),
                stats.getNumOfContextualAdsFilteredOutOfBiddingInvalidSignatures(),
                stats.getNumOfContextualAdsFilteredOutOfBiddingNoAds(),
                stats.getTotalNumOfContextualAdsBeforeFiltering());
    }

    @Override
    public void logAdCounterHistogramUpdaterReportedStats(
            AdCounterHistogramUpdaterReportedStats stats) {
        AdServicesStatsLog.write(
                AD_COUNTER_HISTOGRAM_UPDATER_REPORTED,
                stats.getLatencyInMillis(),
                stats.getStatusCode(),
                stats.getTotalNumberOfEventsInDatabaseAfterInsert(),
                stats.getNumberOfInsertedEvent(),
                stats.getNumberOfEvictedEvent());
    }

    @Override
    public void logTopicsEncryptionEpochComputationReportedStats(
            TopicsEncryptionEpochComputationReportedStats stats) {
        AdServicesStatsLog.write(
                TOPICS_ENCRYPTION_EPOCH_COMPUTATION_REPORTED,
                stats.getCountOfTopicsBeforeEncryption(),
                stats.getCountOfEmptyEncryptedTopics(),
                stats.getCountOfEncryptedTopics(),
                stats.getLatencyOfWholeEncryptionProcessMs(),
                stats.getLatencyOfEncryptionPerTopicMs(),
                stats.getLatencyOfPersistingEncryptedTopicsToDbMs());
    }

    @Override
    public void logTopicsEncryptionGetTopicsReportedStats(
            TopicsEncryptionGetTopicsReportedStats stats) {
        AdServicesStatsLog.write(
                TOPICS_ENCRYPTION_GET_TOPICS_REPORTED,
                stats.getCountOfEncryptedTopics(),
                stats.getLatencyOfReadingEncryptedTopicsFromDbMs());
    }

    @Override
    public void logShellCommandStats(ShellCommandStats stats) {
        AdServicesStatsLog.write(
                ADSERVICES_SHELL_COMMAND_CALLED,
                stats.getCommand(),
                stats.getResult(),
                stats.getLatencyMillis());
    }

    @Override
    public void logSignatureVerificationStats(SignatureVerificationStats stats) {
        AdServicesStatsLog.write(
                SIGNATURE_VERIFICATION,
                stats.getSerializationLatency(),
                stats.getKeyFetchLatency(),
                stats.getVerificationLatency(),
                stats.getNumOfKeysFetched(),
                stats.getSignatureVerificationStatus().getValue(),
                stats.getFailedSignatureBuyerEnrollmentId(),
                stats.getFailedSignatureSellerEnrollmentId(),
                stats.getFailedSignatureCallerPackageName(),
                stats.getFailureDetailUnknownError(),
                stats.getFailureDetailNoEnrollmentDataForBuyer(),
                stats.getFailureDetailNoKeysFetchedForBuyer(),
                stats.getFailureDetailWrongSignatureFormat(),
                stats.getFailureDetailCountOfKeysWithWrongFormat(),
                stats.getFailureDetailCountOfKeysFailedToVerifySignature());
    }

    @Override
    public void logUpdateSignalsApiCalledStats(UpdateSignalsApiCalledStats stats) {
        AdServicesStatsLog.write(
                UPDATE_SIGNALS_API_CALLED,
                stats.getHttpResponseCode(),
                stats.getJsonSize(),
                stats.getJsonProcessingStatus(),
                stats.getPackageUid(),
                stats.getAdTechId());
    }

    @Override
    public void logEncodingJsExecutionStats(EncodingJsExecutionStats stats) {
        AdServicesStatsLog.write(
                ENCODING_JS_EXECUTION,
                stats.getJsLatency(),
                stats.getEncodedSignalsSize(),
                stats.getRunStatus(),
                stats.getJsMemoryUsed(),
                stats.getAdTechId());
    }

    @Override
    public void logServerAuctionBackgroundKeyFetchScheduledStats(
            ServerAuctionBackgroundKeyFetchScheduledStats stats) {
        AdServicesStatsLog.write(
                SERVER_AUCTION_BACKGROUND_KEY_FETCH_ENABLED,
                stats.getStatus(),
                stats.getCountAuctionUrls(),
                stats.getCountJoinUrls());
    }

    @Override
    public void logServerAuctionKeyFetchCalledStats(ServerAuctionKeyFetchCalledStats stats) {
        AdServicesStatsLog.write(
                SERVER_AUCTION_KEY_FETCH_CALLED,
                stats.getSource(),
                stats.getEncryptionKeySource(),
                stats.getCoordinatorSource(),
                stats.getNetworkStatusCode(),
                stats.getNetworkLatencyMillis());
    }

    @Override
    public void logEncodingJobRunStats(EncodingJobRunStats stats) {
        AdServicesStatsLog.write(
                ENCODING_JOB_RUN,
                stats.getSignalEncodingSuccesses(),
                stats.getSignalEncodingFailures(),
                stats.getSignalEncodingSkips(),
                stats.getEncodingSourceType());
    }

    @Override
    public void logPersistAdSelectionResultCalledStats(PersistAdSelectionResultCalledStats stats) {
        AdServicesStatsLog.write(
                PERSIST_AD_SELECTION_RESULT_CALLED,
                stats.getWinnerType());
    }

    @Override
    public void logSelectAdsFromOutcomesApiCalledStats(SelectAdsFromOutcomesApiCalledStats stats) {
        AdServicesStatsLog.write(
                SELECT_ADS_FROM_OUTCOMES_API_CALLED,
                stats.getCountIds(),
                stats.getCountNonExistingIds(),
                stats.getUsedPrebuilt(),
                stats.getDownloadResultCode(),
                stats.getDownloadLatencyMillis(),
                stats.getExecutionResultCode(),
                stats.getExecutionLatencyMillis());
    }

    @Override
    public void logReportImpressionApiCalledStats(ReportImpressionApiCalledStats stats) {
        AdServicesStatsLog.write(
                REPORT_IMPRESSION_API_CALLED,
                stats.getReportWinBuyerAdditionalSignalsContainedAdCost(),
                stats.getReportWinBuyerAdditionalSignalsContainedDataVersion(),
                stats.getReportResultSellerAdditionalSignalsContainedDataVersion(),
                stats.getReportWinJsScriptResultCode(),
                stats.getReportResultJsScriptResultCode());
    }

    @Override
    public void logUpdateSignalsProcessReportedStats(UpdateSignalsProcessReportedStats stats) {
        AdServicesStatsLog.write(
                UPDATE_SIGNALS_PROCESS_REPORTED,
                stats.getUpdateSignalsProcessLatencyMillis(),
                stats.getAdservicesApiStatusCode(),
                stats.getSignalsWrittenCount(),
                stats.getKeysStoredCount(),
                stats.getValuesStoredCount(),
                stats.getEvictionRulesCount(),
                stats.getPerBuyerSignalSize(),
                stats.getMeanRawProtectedSignalsSizeBytes(),
                stats.getMaxRawProtectedSignalsSizeBytes(),
                stats.getMinRawProtectedSignalsSizeBytes());
    }

    @Override
    public void logTopicsScheduleEpochJobSettingReportedStats(
            TopicsScheduleEpochJobSettingReportedStats stats) {
        AdServicesStatsLog.write(
                TOPICS_SCHEDULE_EPOCH_JOB_SETTING_REPORTED,
                stats.getRescheduleEpochJobStatus(),
                stats.getPreviousEpochJobSetting(),
                stats.getCurrentEpochJobSetting(),
                stats.getScheduleIfNeededEpochJobStatus());
    }

    @Override
    public void logScheduledCustomAudienceUpdatePerformedStats(
            ScheduledCustomAudienceUpdatePerformedStats stats) {
        AdServicesStatsLog.write(
                SCHEDULED_CUSTOM_AUDIENCE_UPDATE_PERFORMED,
                stats.getNumberOfPartialCustomAudienceInRequest(),
                stats.getNumberOfJoinCustomAudienceInResponse(),
                stats.getNumberOfCustomAudienceJoined(),
                stats.getNumberOfLeaveCustomAudienceInRequest(),
                stats.getNumberOfLeaveCustomAudienceInResponse(),
                stats.getNumberOfCustomAudienceLeft(),
                stats.getWasInitialHop(),
                stats.getNumberOfScheduleUpdatesInResponse(),
                stats.getNumberOfUpdatesScheduled());
    }

    @Override
    public void logScheduledCustomAudienceUpdateBackgroundJobStats(
            ScheduledCustomAudienceUpdateBackgroundJobStats stats) {
        AdServicesStatsLog.write(
                SCHEDULED_CUSTOM_AUDIENCE_UPDATE_BACKGROUND_JOB_RAN,
                stats.getNumberOfUpdatesFound(),
                stats.getNumberOfSuccessfulUpdates());
    }

    @Override
    public void logScheduledCustomAudienceUpdateScheduleAttemptedStats(
            ScheduledCustomAudienceUpdateScheduleAttemptedStats stats) {
        AdServicesStatsLog.write(
                SCHEDULED_CUSTOM_AUDIENCE_UPDATE_SCHEDULE_ATTEMPTED,
                stats.getNumberOfPartialCustomAudiences(),
                stats.getMinimumDelayInMinutes(),
                stats.getExistingUpdateStatus(),
                stats.getNumberOfLeaveCustomAudiences(),
                stats.isInitialHop());
    }

    @Override
    public void logScheduledCustomAudienceUpdatePerformedFailureStats(
            ScheduledCustomAudienceUpdatePerformedFailureStats stats) {
        AdServicesStatsLog.write(
                SCHEDULED_CUSTOM_AUDIENCE_UPDATE_PERFORMED_ATTEMPTED_FAILURE_REPORTED,
                stats.getFailureType(),
                stats.getFailureAction());
    }

    @NonNull
    private byte[] toBytes(long fieldId, @NonNull int[] values) {
        ProtoOutputStream protoOutputStream = new ProtoOutputStream();
        for (int value : values) {
            protoOutputStream.write(fieldId, value);
        }
        return protoOutputStream.getBytes();
    }
}

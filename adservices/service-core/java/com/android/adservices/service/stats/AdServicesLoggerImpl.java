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

import android.annotation.Nullable;

import com.android.adservices.cobalt.ApiResponseCobaltLogger;
import com.android.adservices.cobalt.AppNameApiErrorLogger;
import com.android.adservices.cobalt.MeasurementCobaltLogger;
import com.android.adservices.concurrency.AdServicesExecutors;
import com.android.adservices.service.FlagsFactory;
import com.android.adservices.service.common.AppManifestConfigCall;
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
import com.android.internal.annotations.VisibleForTesting;

import java.util.Objects;
import java.util.concurrent.Executor;

import javax.annotation.concurrent.ThreadSafe;

/** AdServicesLogger that delegate to the appropriate Logger Implementations. */
@ThreadSafe
public final class AdServicesLoggerImpl implements AdServicesLogger {

    private static volatile AdServicesLoggerImpl sAdServicesLogger;
    private static final Executor sBlockingExecutor = AdServicesExecutors.getBlockingExecutor();
    private final StatsdAdServicesLogger mStatsdAdServicesLogger;

    private AdServicesLoggerImpl() {
        this(StatsdAdServicesLogger.getInstance());
    }

    @VisibleForTesting
    AdServicesLoggerImpl(StatsdAdServicesLogger statsdAdServicesLogger) {
        mStatsdAdServicesLogger = statsdAdServicesLogger;
    }

    /** Returns an instance of AdServicesLogger. */
    public static AdServicesLoggerImpl getInstance() {
        if (sAdServicesLogger == null) {
            synchronized (AdServicesLoggerImpl.class) {
                if (sAdServicesLogger == null) {
                    sAdServicesLogger = new AdServicesLoggerImpl();
                }
            }
        }
        return sAdServicesLogger;
    }

    @Override
    public void logMeasurementReports(
            MeasurementReportsStats measurementReportsStats, @Nullable String enrollmentId) {
        mStatsdAdServicesLogger.logMeasurementReports(measurementReportsStats, enrollmentId);
        cobaltLogMsmtReportingStats(measurementReportsStats, enrollmentId);
    }

    @Override
    public void logApiCallStats(ApiCallStats apiCallStats) {
        mStatsdAdServicesLogger.logApiCallStats(apiCallStats);

        // Package name should never be null in "real life" (as ApiCallStats builder would prevent
        // it), but it doesn't hurt to check - in particular, it could be null on unit tests if
        // mocked.
        String packageName = apiCallStats.getAppPackageName();
        com.android.internal.util.Preconditions.checkArgument(
                packageName != null, "ApiCallStats have null packageName: %s", apiCallStats);

        cobaltLogAppNameApiError(
                packageName, apiCallStats.getApiName(), apiCallStats.getResultCode());
        cobaltLogApiResponse(packageName, apiCallStats.getApiName(), apiCallStats.getResultCode());
    }

    @Override
    public void logUIStats(UIStats uiStats) {
        mStatsdAdServicesLogger.logUIStats(uiStats);
    }

    @Override
    public void logFledgeApiCallStats(int apiName, int resultCode, int latencyMs) {
        mStatsdAdServicesLogger.logFledgeApiCallStats(apiName, resultCode, latencyMs);
    }

    @Override
    public void logFledgeApiCallStats(
            int apiName, String appPackageName, int resultCode, int latencyMs) {
        Objects.requireNonNull(appPackageName, "appPackageName cannot be null");

        mStatsdAdServicesLogger.logFledgeApiCallStats(
                apiName, appPackageName, resultCode, latencyMs);

        cobaltLogAppNameApiError(appPackageName, apiName, resultCode);
        cobaltLogApiResponse(appPackageName, apiName, resultCode);
    }

    @Override
    public void logMeasurementRegistrationsResponseSize(
            MeasurementRegistrationResponseStats stats, @Nullable String enrollmentId) {
        mStatsdAdServicesLogger.logMeasurementRegistrationsResponseSize(stats, enrollmentId);

        // Log to Cobalt system in parallel with existing logging.
        cobaltLogMsmtRegistration(stats, enrollmentId);
    }

    @Override
    public void logRunAdSelectionProcessReportedStats(RunAdSelectionProcessReportedStats stats) {
        mStatsdAdServicesLogger.logRunAdSelectionProcessReportedStats(stats);
    }

    @Override
    public void logRunAdBiddingProcessReportedStats(RunAdBiddingProcessReportedStats stats) {
        mStatsdAdServicesLogger.logRunAdBiddingProcessReportedStats(stats);
    }

    @Override
    public void logRunAdScoringProcessReportedStats(RunAdScoringProcessReportedStats stats) {
        mStatsdAdServicesLogger.logRunAdScoringProcessReportedStats(stats);
    }

    @Override
    public void logRunAdBiddingPerCAProcessReportedStats(
            RunAdBiddingPerCAProcessReportedStats stats) {
        mStatsdAdServicesLogger.logRunAdBiddingPerCAProcessReportedStats(stats);
    }

    @Override
    public void logBackgroundFetchProcessReportedStats(BackgroundFetchProcessReportedStats stats) {
        mStatsdAdServicesLogger.logBackgroundFetchProcessReportedStats(stats);
    }

    @Override
    public void logUpdateCustomAudienceProcessReportedStats(
            UpdateCustomAudienceProcessReportedStats stats) {
        mStatsdAdServicesLogger.logUpdateCustomAudienceProcessReportedStats(stats);
    }

    @Override
    public void logGetTopicsReportedStats(GetTopicsReportedStats stats) {
        mStatsdAdServicesLogger.logGetTopicsReportedStats(stats);
    }

    @Override
    public void logEpochComputationGetTopTopicsStats(EpochComputationGetTopTopicsStats stats) {
        mStatsdAdServicesLogger.logEpochComputationGetTopTopicsStats(stats);
    }

    @Override
    public void logEpochComputationClassifierStats(EpochComputationClassifierStats stats) {
        mStatsdAdServicesLogger.logEpochComputationClassifierStats(stats);
    }

    @Override
    public void logMeasurementDebugKeysMatch(MsmtDebugKeysMatchStats stats) {
        mStatsdAdServicesLogger.logMeasurementDebugKeysMatch(stats);
    }

    @Override
    public void logMeasurementAdIdMatchForDebugKeysStats(MsmtAdIdMatchForDebugKeysStats stats) {
        mStatsdAdServicesLogger.logMeasurementAdIdMatchForDebugKeysStats(stats);
    }

    @Override
    public void logMeasurementAttributionStats(
            MeasurementAttributionStats measurementAttributionStats,
            @Nullable String enrollmentId) {
        mStatsdAdServicesLogger.logMeasurementAttributionStats(
                measurementAttributionStats, enrollmentId);
        cobaltLogMsmtAttribution(measurementAttributionStats, enrollmentId);
    }

    @Override
    public void logMeasurementWipeoutStats(MeasurementWipeoutStats measurementWipeoutStats) {
        mStatsdAdServicesLogger.logMeasurementWipeoutStats(measurementWipeoutStats);
    }

    @Override
    public void logMeasurementDelayedSourceRegistrationStats(
            MeasurementDelayedSourceRegistrationStats measurementDelayedSourceRegistrationStats) {
        mStatsdAdServicesLogger.logMeasurementDelayedSourceRegistrationStats(
                measurementDelayedSourceRegistrationStats);
    }

    @Override
    public void logMeasurementClickVerificationStats(
            MeasurementClickVerificationStats measurementClickVerificationStats) {
        mStatsdAdServicesLogger.logMeasurementClickVerificationStats(
                measurementClickVerificationStats);
    }

    @Override
    public void logMeasurementOdpRegistrations(MeasurementOdpRegistrationStats stats) {
        mStatsdAdServicesLogger.logMeasurementOdpRegistrations(stats);
    }

    @Override
    public void logMeasurementOdpApiCall(MeasurementOdpApiCallStats stats) {
        mStatsdAdServicesLogger.logMeasurementOdpApiCall(stats);
    }

    @Override
    public void logEnrollmentDataStats(int mType, boolean mIsSuccessful, int mBuildId) {
        mStatsdAdServicesLogger.logEnrollmentDataStats(mType, mIsSuccessful, mBuildId);
    }

    @Override
    public void logEnrollmentMatchStats(boolean mIsSuccessful, int mBuildId) {
        mStatsdAdServicesLogger.logEnrollmentMatchStats(mIsSuccessful, mBuildId);
    }

    @Override
    public void logEnrollmentFileDownloadStats(boolean mIsSuccessful, int mBuildId) {
        mStatsdAdServicesLogger.logEnrollmentFileDownloadStats(mIsSuccessful, mBuildId);
    }

    @Override
    public void logEnrollmentFailedStats(
            int mBuildId,
            int mDataFileGroupStatus,
            int mEnrollmentRecordCountInTable,
            String mQueryParameter,
            int mErrorCause) {
        mStatsdAdServicesLogger.logEnrollmentFailedStats(
                mBuildId,
                mDataFileGroupStatus,
                mEnrollmentRecordCountInTable,
                mQueryParameter,
                mErrorCause);
    }

    /** Logs enrollment transaction stats. */
    @Override
    public void logEnrollmentTransactionStats(AdServicesEnrollmentTransactionStats stats) {
        mStatsdAdServicesLogger.logEnrollmentTransactionStats(stats);
    }

    /** Logs encryption key fetch stats. */
    @Override
    public void logEncryptionKeyFetchedStats(AdServicesEncryptionKeyFetchedStats stats) {
        mStatsdAdServicesLogger.logEncryptionKeyFetchedStats(stats);
    }

    /** Logs encryption key datastore transaction ended stats. */
    @Override
    public void logEncryptionKeyDbTransactionEndedStats(
            AdServicesEncryptionKeyDbTransactionEndedStats stats) {
        mStatsdAdServicesLogger.logEncryptionKeyDbTransactionEndedStats(stats);
    }

    /** Logs destinationRegisteredBeacon reported stats. */
    @Override
    public void logDestinationRegisteredBeaconsReportedStats(
            DestinationRegisteredBeaconsReportedStats stats) {
        mStatsdAdServicesLogger.logDestinationRegisteredBeaconsReportedStats(stats);
    }

    /** Logs beacon level reporting for ReportInteraction API called stats. */
    @Override
    public void logReportInteractionApiCalledStats(ReportInteractionApiCalledStats stats) {
        mStatsdAdServicesLogger.logReportInteractionApiCalledStats(stats);
    }

    /** Logs beacon level reporting for clearing interaction reporting table stats. */
    @Override
    public void logInteractionReportingTableClearedStats(
            InteractionReportingTableClearedStats stats) {
        mStatsdAdServicesLogger.logInteractionReportingTableClearedStats(stats);
    }

    @Override
    public void logAppManifestConfigCall(AppManifestConfigCall call) {
        mStatsdAdServicesLogger.logAppManifestConfigCall(call);
    }

    @Override
    public void logKAnonSignJoinStatus() {
        // TODO(b/324564459): add logging for KAnon Sign Join
    }

    @Override
    public void logKAnonInitializeStats(KAnonInitializeStatusStats kAnonInitializeStatusStats) {
        mStatsdAdServicesLogger.logKAnonInitializeStats(kAnonInitializeStatusStats);
    }

    @Override
    public void logKAnonSignStats(KAnonSignStatusStats kAnonSignStatusStats) {
        mStatsdAdServicesLogger.logKAnonSignStats(kAnonSignStatusStats);
    }

    @Override
    public void logKAnonJoinStats(KAnonJoinStatusStats kAnonJoinStatusStats) {
        mStatsdAdServicesLogger.logKAnonJoinStats(kAnonJoinStatusStats);
    }

    @Override
    public void logKAnonBackgroundJobStats(
            KAnonBackgroundJobStatusStats kAnonBackgroundJobStatusStats) {
        mStatsdAdServicesLogger.logKAnonBackgroundJobStats(kAnonBackgroundJobStatusStats);
    }

    @Override
    public void logKAnonGetChallengeJobStats(
            KAnonGetChallengeStatusStats kAnonGetChallengeStatusStats) {
        mStatsdAdServicesLogger.logKAnonGetChallengeJobStats(kAnonGetChallengeStatusStats);
    }

    @Override
    public void logKAnonImmediateSignJoinStats(
            KAnonImmediateSignJoinStatusStats kAnonImmediateSignJoinStatusStats) {
        mStatsdAdServicesLogger.logKAnonImmediateSignJoinStats(kAnonImmediateSignJoinStatusStats);
    }

    @Override
    public void logGetAdSelectionDataApiCalledStats(GetAdSelectionDataApiCalledStats stats) {
        mStatsdAdServicesLogger.logGetAdSelectionDataApiCalledStats(stats);
    }

    @Override
    public void logServerAuctionBackgroundKeyFetchScheduledStats(
            ServerAuctionBackgroundKeyFetchScheduledStats stats) {
        mStatsdAdServicesLogger.logServerAuctionBackgroundKeyFetchScheduledStats(stats);
    }

    @Override
    public void logServerAuctionKeyFetchCalledStats(ServerAuctionKeyFetchCalledStats stats) {
        mStatsdAdServicesLogger.logServerAuctionKeyFetchCalledStats(stats);
    }

    @Override
    public void logGetAdSelectionDataBuyerInputGeneratedStats(
            GetAdSelectionDataBuyerInputGeneratedStats stats) {
        mStatsdAdServicesLogger.logGetAdSelectionDataBuyerInputGeneratedStats(stats);
    }

    @Override
    public void logAdFilteringProcessJoinCAReportedStats(
            AdFilteringProcessJoinCAReportedStats stats) {
        mStatsdAdServicesLogger.logAdFilteringProcessJoinCAReportedStats(stats);
    }

    @Override
    public void logAdFilteringProcessAdSelectionReportedStats(
            AdFilteringProcessAdSelectionReportedStats stats) {
        mStatsdAdServicesLogger.logAdFilteringProcessAdSelectionReportedStats(stats);
    }

    @Override
    public void logAdCounterHistogramUpdaterReportedStats(
            AdCounterHistogramUpdaterReportedStats stats) {
        mStatsdAdServicesLogger.logAdCounterHistogramUpdaterReportedStats(stats);
    }

    @Override
    public void logTopicsEncryptionEpochComputationReportedStats(
            TopicsEncryptionEpochComputationReportedStats stats) {
        mStatsdAdServicesLogger.logTopicsEncryptionEpochComputationReportedStats(stats);
    }

    @Override
    public void logTopicsEncryptionGetTopicsReportedStats(
            TopicsEncryptionGetTopicsReportedStats stats) {
        mStatsdAdServicesLogger.logTopicsEncryptionGetTopicsReportedStats(stats);
    }

    @Override
    public void logShellCommandStats(ShellCommandStats stats) {
        mStatsdAdServicesLogger.logShellCommandStats(stats);
    }

    @Override
    public void logSignatureVerificationStats(SignatureVerificationStats stats) {
        mStatsdAdServicesLogger.logSignatureVerificationStats(stats);
    }

    @Override
    public void logEncodingJsFetchStats(EncodingFetchStats stats) {
        mStatsdAdServicesLogger.logEncodingJsFetchStats(stats);
    }

    @Override
    public void logEncodingJsExecutionStats(EncodingJsExecutionStats stats) {
        mStatsdAdServicesLogger.logEncodingJsExecutionStats(stats);
    }

    @Override
    public void logEncodingJobRunStats(EncodingJobRunStats stats) {
        mStatsdAdServicesLogger.logEncodingJobRunStats(stats);
    }

    @Override
    public void logUpdateSignalsProcessReportedStats(UpdateSignalsProcessReportedStats stats) {
        mStatsdAdServicesLogger.logUpdateSignalsProcessReportedStats(stats);
    }

    @Override
    public void logPersistAdSelectionResultCalledStats(PersistAdSelectionResultCalledStats stats) {
        mStatsdAdServicesLogger.logPersistAdSelectionResultCalledStats(stats);
    }

    @Override
    public void logSelectAdsFromOutcomesApiCalledStats(SelectAdsFromOutcomesApiCalledStats stats) {
        mStatsdAdServicesLogger.logSelectAdsFromOutcomesApiCalledStats(stats);
    }

    @Override
    public void logReportImpressionApiCalledStats(ReportImpressionApiCalledStats stats) {
        mStatsdAdServicesLogger.logReportImpressionApiCalledStats(stats);
    }

    @Override
    public void logUpdateSignalsApiCalledStats(UpdateSignalsApiCalledStats stats) {
        mStatsdAdServicesLogger.logUpdateSignalsApiCalledStats(stats);
    }

    @Override
    public void logTopicsScheduleEpochJobSettingReportedStats(
            TopicsScheduleEpochJobSettingReportedStats stats) {
        mStatsdAdServicesLogger.logTopicsScheduleEpochJobSettingReportedStats(stats);
    }

    @Override
    public void logScheduledCustomAudienceUpdatePerformedStats(
            ScheduledCustomAudienceUpdatePerformedStats stats) {
        mStatsdAdServicesLogger.logScheduledCustomAudienceUpdatePerformedStats(stats);
    }

    @Override
    public void logScheduledCustomAudienceUpdateBackgroundJobStats(
            ScheduledCustomAudienceUpdateBackgroundJobStats stats) {
        mStatsdAdServicesLogger.logScheduledCustomAudienceUpdateBackgroundJobStats(stats);
    }

    @Override
    public void logScheduledCustomAudienceUpdateScheduleAttemptedStats(
            ScheduledCustomAudienceUpdateScheduleAttemptedStats stats) {
        mStatsdAdServicesLogger.logScheduledCustomAudienceUpdateScheduleAttemptedStats(stats);
    }

    @Override
    public void logScheduledCustomAudienceUpdatePerformedFailureStats(
            ScheduledCustomAudienceUpdatePerformedFailureStats stats) {
        mStatsdAdServicesLogger.logScheduledCustomAudienceUpdatePerformedFailureStats(stats);
    }

    /** Logs api call error status using {@code CobaltLogger}. */
    @VisibleForTesting
    // used by testCobaltLogAppNameApiError_nullPackageName only
    void cobaltLogAppNameApiError(String appPackageName, int apiName, int errorCode) {
        // Callers should have checked for appPackageName already, but it doesn't hurt to double
        // check (otherwise it would have been thrown on background
        Objects.requireNonNull(
                appPackageName, "INTERNAL ERROR: caller didn't check for null appPackageName");

        sBlockingExecutor.execute(
                () -> {
                    AppNameApiErrorLogger appNameApiErrorLogger =
                            AppNameApiErrorLogger.getInstance();

                    appNameApiErrorLogger.logErrorOccurrence(appPackageName, apiName, errorCode);
                });
    }

    /** Logs api call response using {@code CobaltLogger}. */
    @VisibleForTesting
    void cobaltLogApiResponse(String appPackageName, int apiName, int responseCode) {
        // Callers should have checked for appPackageName already, but it doesn't hurt to double
        // check (otherwise it would have been thrown on background
        Objects.requireNonNull(
                appPackageName, "INTERNAL ERROR: caller didn't check for null appPackageName");

        sBlockingExecutor.execute(
                () -> {
                    ApiResponseCobaltLogger.getInstance()
                            .logResponse(appPackageName, apiName, responseCode);
                });
    }

    /** Logs measurement registration status using {@code CobaltLogger}. */
    private void cobaltLogMsmtRegistration(
            MeasurementRegistrationResponseStats stats, @Nullable String enrollmentId) {
        sBlockingExecutor.execute(
                () -> {
                    MeasurementCobaltLogger measurementCobaltLogger =
                            MeasurementCobaltLogger.getInstance();
                    measurementCobaltLogger.logRegistrationStatus(
                            /* appPackageName= */ stats.getSourceRegistrant(),
                            /* surfaceType= */ stats.getSurfaceType(),
                            /* type= */ stats.getRegistrationType(),
                            /* sourceType= */ stats.getInteractionType(),
                            /* statusCode= */ stats.getRegistrationStatus(),
                            /* errorCode= */ stats.getFailureType(),
                            /* isEeaDevice= */ FlagsFactory.getFlags().isEeaDevice(),
                            enrollmentId);
                });
    }

    /** Logs measurement attribution status using {@code CobaltLogger}. */
    private void cobaltLogMsmtAttribution(
            MeasurementAttributionStats stats, @Nullable String enrollmentId) {
        sBlockingExecutor.execute(
                () -> {
                    MeasurementCobaltLogger measurementCobaltLogger =
                            MeasurementCobaltLogger.getInstance();
                    measurementCobaltLogger.logAttributionStatusWithAppName(
                            /* appPackageName= */ stats.getSourceRegistrant(),
                            /* attrSurfaceType= */ stats.getSurfaceType(),
                            /* sourceType= */ stats.getSourceType(),
                            /* statusCode= */ stats.getResult(),
                            /* errorCode= */ stats.getFailureType(),
                            enrollmentId);
                });
    }

    /** Logs measurement reporting status using {@code CobaltLogger}. */
    private void cobaltLogMsmtReportingStats(
            MeasurementReportsStats stats, @Nullable String enrollmentId) {
        sBlockingExecutor.execute(
                () -> {
                    MeasurementCobaltLogger measurementCobaltLogger =
                            MeasurementCobaltLogger.getInstance();
                    measurementCobaltLogger.logReportingStatusWithAppName(
                            /* appPackageName= */ stats.getSourceRegistrant(),
                            /* reportType= */ stats.getType(),
                            /* reportUploadMethod= */ stats.getUploadMethod(),
                            /* statusCode= */ stats.getResultCode(),
                            /* errorCode= */ stats.getFailureType(),
                            enrollmentId);
                });
    }
}

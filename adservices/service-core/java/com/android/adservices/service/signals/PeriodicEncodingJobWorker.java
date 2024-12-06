/*
 * Copyright (C) 2023 The Android Open Source Project
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

package com.android.adservices.service.signals;

import static com.android.adservices.service.stats.AdServicesStatsLog.AD_SERVICES_ERROR_REPORTED__ERROR_CODE__PAS_FAILED_PER_BUYER_ENCODING;
import static com.android.adservices.service.stats.AdServicesStatsLog.AD_SERVICES_ERROR_REPORTED__PPAPI_NAME__PAS;

import android.adservices.common.AdTechIdentifier;
import android.content.Context;

import com.android.adservices.LoggerFactory;
import com.android.adservices.concurrency.AdServicesExecutors;
import com.android.adservices.data.enrollment.EnrollmentDao;
import com.android.adservices.data.signals.DBEncoderLogicMetadata;
import com.android.adservices.data.signals.EncodedPayloadDao;
import com.android.adservices.data.signals.EncoderLogicHandler;
import com.android.adservices.data.signals.EncoderLogicMetadataDao;
import com.android.adservices.data.signals.ProtectedSignalsDao;
import com.android.adservices.data.signals.ProtectedSignalsDatabase;
import com.android.adservices.errorlogging.ErrorLogUtil;
import com.android.adservices.service.DebugFlags;
import com.android.adservices.service.Flags;
import com.android.adservices.service.FlagsFactory;
import com.android.adservices.service.common.RetryStrategy;
import com.android.adservices.service.common.RetryStrategyFactory;
import com.android.adservices.service.common.SingletonRunner;
import com.android.adservices.service.devapi.DevContext;
import com.android.adservices.service.profiling.Tracing;
import com.android.adservices.service.stats.AdServicesLogger;
import com.android.adservices.service.stats.AdServicesLoggerImpl;
import com.android.adservices.service.stats.AdsRelevanceStatusUtils;
import com.android.adservices.service.stats.pas.EncodingExecutionLogHelper;
import com.android.adservices.service.stats.pas.EncodingExecutionLogHelperImpl;
import com.android.adservices.service.stats.pas.EncodingExecutionLogHelperNoOpImpl;
import com.android.adservices.service.stats.pas.EncodingJobRunStats;
import com.android.adservices.service.stats.pas.EncodingJobRunStatsLogger;
import com.android.adservices.service.stats.pas.EncodingJobRunStatsLoggerImpl;
import com.android.adservices.service.stats.pas.EncodingJobRunStatsLoggerNoLoggingImpl;
import com.android.adservices.shared.common.ApplicationContextSingleton;
import com.android.adservices.shared.util.Clock;

import com.google.common.annotations.VisibleForTesting;
import com.google.common.util.concurrent.FluentFuture;
import com.google.common.util.concurrent.Futures;
import com.google.common.util.concurrent.ListenableFuture;
import com.google.common.util.concurrent.ListeningExecutorService;

import java.time.Instant;
import java.time.temporal.ChronoUnit;
import java.util.List;
import java.util.function.Supplier;
import java.util.stream.Collectors;

/**
 * Handles the periodic encoding responsibilities, such as fetching the raw signals and triggering
 * the JS engine for encoding.
 */
public final class PeriodicEncodingJobWorker {

    private static final LoggerFactory.Logger sLogger = LoggerFactory.getFledgeLogger();

    public static final String JOB_DESCRIPTION = "Protected-Signals Periodic Encoding";

    public static final String PAYLOAD_PERSISTENCE_ERROR_MSG = "Failed to persist encoded payload";

    private final int mPerBuyerEncodingTimeoutMs;
    private final int mEncodedPayLoadMaxSizeBytes;

    private final EncoderLogicHandler mEncoderLogicHandler;
    private final EncoderLogicMetadataDao mEncoderLogicMetadataDao;
    private final EncodedPayloadDao mEncodedPayloadDao;

    private final ListeningExecutorService mBackgroundExecutor;
    private final ListeningExecutorService mLightWeightExecutor;
    private final EnrollmentDao mEnrollmentDao;
    private final Clock mClock;
    private final AdServicesLogger mAdServicesLogger;
    private final SingletonRunner<Void> mSingletonRunner =
            new SingletonRunner<>(JOB_DESCRIPTION, this::doRun);

    private final Flags mFlags;
    private final PeriodicEncodingJobRunner mPeriodicEncodingJobRunner;

    private final EncodingJobRunStatsLogger mEncodingJobRunStatsLogger;

    @VisibleForTesting
    public PeriodicEncodingJobWorker(
            EncoderLogicHandler encoderLogicHandler,
            EncoderLogicMetadataDao encoderLogicMetadataDao,
            EncodedPayloadDao encodedPayloadDao,
            ProtectedSignalsDao protectedSignalsDao,
            SignalsScriptEngine scriptEngine,
            ListeningExecutorService backgroundExecutor,
            ListeningExecutorService lightWeightExecutor,
            Flags flags,
            EnrollmentDao enrollmentDao,
            Clock clock,
            AdServicesLogger adServicesLogger) {
        mEncoderLogicHandler = encoderLogicHandler;
        mEncoderLogicMetadataDao = encoderLogicMetadataDao;
        mEncodedPayloadDao = encodedPayloadDao;
        mBackgroundExecutor = backgroundExecutor;
        mLightWeightExecutor = lightWeightExecutor;
        mFlags = flags;
        mEncodedPayLoadMaxSizeBytes = mFlags.getProtectedSignalsEncodedPayloadMaxSizeBytes();
        mEnrollmentDao = enrollmentDao;
        mClock = clock;
        mAdServicesLogger = adServicesLogger;
        mPerBuyerEncodingTimeoutMs = mFlags.getPasScriptExecutionTimeoutMs();
        mPeriodicEncodingJobRunner =
                new PeriodicEncodingJobRunner(
                        new SignalsProviderAndArgumentFactory(
                                protectedSignalsDao, flags.getPasEncodingJobImprovementsEnabled()),
                        protectedSignalsDao,
                        scriptEngine,
                        mFlags.getProtectedSignalsMaxJsFailureExecutionOnCertainVersionBeforeStop(),
                        mEncodedPayLoadMaxSizeBytes,
                        mEncoderLogicHandler,
                        mEncodedPayloadDao,
                        mBackgroundExecutor,
                        mLightWeightExecutor);

        mEncodingJobRunStatsLogger =
                mFlags.getPasExtendedMetricsEnabled()
                        ? new EncodingJobRunStatsLoggerImpl(
                                mAdServicesLogger,
                                EncodingJobRunStats.builder(),
                                mFlags.getFledgeEnableForcedEncodingAfterSignalsUpdate())
                        : new EncodingJobRunStatsLoggerNoLoggingImpl();
    }

    /**
     * @return an instance of {@link PeriodicEncodingJobWorker}
     */
    public static PeriodicEncodingJobWorker getInstance() {
        return FieldHolder.sInstance;
    }

    // Lazy initialization holder class idiom for static fields as described in Effective Java Item
    // 83
    private static final class FieldHolder {
        private static final PeriodicEncodingJobWorker sInstance = computeFieldValue();

        private static PeriodicEncodingJobWorker computeFieldValue() {
            Context context = ApplicationContextSingleton.get();
            ProtectedSignalsDatabase signalsDatabase = ProtectedSignalsDatabase.getInstance();
            Flags flags = FlagsFactory.getFlags();
            RetryStrategy retryStrategy =
                    RetryStrategyFactory.createInstance(
                                    flags.getAdServicesRetryStrategyEnabled(),
                                    AdServicesExecutors.getLightWeightExecutor())
                            .createRetryStrategy(
                                    flags.getAdServicesJsScriptEngineMaxRetryAttempts());
            return new PeriodicEncodingJobWorker(
                    new EncoderLogicHandler(context),
                    signalsDatabase.getEncoderLogicMetadataDao(),
                    signalsDatabase.getEncodedPayloadDao(),
                    signalsDatabase.protectedSignalsDao(),
                    new SignalsScriptEngine(
                            flags::getIsolateMaxHeapSizeBytes,
                            retryStrategy,
                            () ->
                                    DebugFlags.getInstance()
                                            .getAdServicesJsIsolateConsoleMessagesInLogsEnabled()),
                    AdServicesExecutors.getBackgroundExecutor(),
                    AdServicesExecutors.getLightWeightExecutor(),
                    flags,
                    EnrollmentDao.getInstance(),
                    Clock.getInstance(),
                    AdServicesLoggerImpl.getInstance());
        }
    }

    /**
     * Initiates the encoding of raw signals with PAS encoding source type.
     *
     * @param encodingSourceType The PAS encoding source type.
     */
    public FluentFuture<Void> encodeProtectedSignals(
            @AdsRelevanceStatusUtils.PasEncodingSourceType int encodingSourceType) {
        sLogger.v("Starting %s", JOB_DESCRIPTION);
        // TODO(b/30495695): Verify the metric is logged async.
        mEncodingJobRunStatsLogger.resetStatsWithEncodingSourceType(encodingSourceType);
        return mSingletonRunner.runSingleInstance();
    }

    /** Requests that any ongoing work be stopped gracefully and waits for work to be stopped. */
    public void stopWork() {
        mSingletonRunner.stopWork();
    }

    /**
     * Runs encoding for the buyers that have registered their encoding logic. Also updates the
     * encoders for buyers that have the previous encoders downloaded outside the refresh window
     */
    private FluentFuture<Void> doRun(Supplier<Boolean> shouldStop) {
        int traceCookie = Tracing.beginAsyncSection(Tracing.RUN_WORKER);

        boolean pasExtendedMetricsEnabled = mFlags.getPasExtendedMetricsEnabled();
        FluentFuture<List<DBEncoderLogicMetadata>> buyersWithRegisteredEncoders =
                FluentFuture.from(
                        mBackgroundExecutor.submit(mEncoderLogicHandler::getAllRegisteredEncoders));

        FluentFuture<Void> encodeSignalsFuture =
                buyersWithRegisteredEncoders.transformAsync(
                        logicMetadata ->
                                doEncodingForRegisteredBuyers(
                                        logicMetadata,
                                        pasExtendedMetricsEnabled,
                                        mEncodingJobRunStatsLogger),
                        mBackgroundExecutor);

        // TODO(b/294900119) We should do the update of encoding logic in a separate job
        // Once the encodings are done, we update the encoder logic asynchronously
        final Instant timeForRefresh =
                Instant.now()
                        .minus(
                                mFlags.getProtectedSignalsEncoderRefreshWindowSeconds(),
                                ChronoUnit.SECONDS);
        return encodeSignalsFuture
                .transformAsync(
                        unused -> {
                            FluentFuture<List<AdTechIdentifier>> buyersWithEncodersReadyForRefresh =
                                    FluentFuture.from(
                                            mBackgroundExecutor.submit(
                                                    () ->
                                                            mEncoderLogicMetadataDao
                                                                    .getBuyersWithEncodersBeforeTime(
                                                                            timeForRefresh)));
                            mEncodingJobRunStatsLogger.logEncodingJobRunStats();

                            return buyersWithEncodersReadyForRefresh.transformAsync(
                                    this::doUpdateEncodersForBuyers, mBackgroundExecutor);
                        },
                        mLightWeightExecutor)
                .transform(
                        ignored -> {
                            Tracing.endAsyncSection(Tracing.RUN_WORKER, traceCookie);
                            return null;
                        },
                        mBackgroundExecutor);
    }

    private FluentFuture<Void> doEncodingForRegisteredBuyers(
            List<DBEncoderLogicMetadata> encoderLogicMetadataList,
            boolean extendedLoggingEnabled,
            EncodingJobRunStatsLogger encodingJobRunStatsLogger) {
        int traceCookie = Tracing.beginAsyncSection(Tracing.DO_ENCODING_FOR_REGISTERED_BUYERS);

        List<ListenableFuture<Void>> buyerEncodings =
                encoderLogicMetadataList.stream()
                        .map(
                                metadata ->
                                        pickLoggerAndRunEncodingPerBuyer(
                                                metadata,
                                                extendedLoggingEnabled,
                                                encodingJobRunStatsLogger))
                        .collect(Collectors.toList());
        encodingJobRunStatsLogger.setSizeOfFilteredBuyerEncodingList(buyerEncodings.size());
        return FluentFuture.from(Futures.successfulAsList(buyerEncodings))
                .transform(
                        ignored -> {
                            Tracing.endAsyncSection(
                                    Tracing.DO_ENCODING_FOR_REGISTERED_BUYERS, traceCookie);
                            return null;
                        },
                        mLightWeightExecutor);
    }

    private FluentFuture<Void> pickLoggerAndRunEncodingPerBuyer(
            DBEncoderLogicMetadata metadata,
            boolean extendedLoggingEnabled,
            EncodingJobRunStatsLogger encodingJobRunStatsLogger) {
        EncodingExecutionLogHelper logHelper;
        if (extendedLoggingEnabled) {
            logHelper =
                    new EncodingExecutionLogHelperImpl(mAdServicesLogger, mClock, mEnrollmentDao);
        } else {
            logHelper = new EncodingExecutionLogHelperNoOpImpl();
        }
        int timeoutSeconds = mPerBuyerEncodingTimeoutMs / 1000;
        return mPeriodicEncodingJobRunner
                .runEncodingPerBuyer(metadata, timeoutSeconds, logHelper, encodingJobRunStatsLogger)
                .catching(
                        Exception.class,
                        (e) -> {
                            handleFailedPerBuyerEncoding(metadata);
                            encodingJobRunStatsLogger.addOneSignalEncodingFailures();
                            ErrorLogUtil.e(
                                    e,
                                    AD_SERVICES_ERROR_REPORTED__ERROR_CODE__PAS_FAILED_PER_BUYER_ENCODING,
                                    AD_SERVICES_ERROR_REPORTED__PPAPI_NAME__PAS);
                            return null;
                        },
                        mLightWeightExecutor);
    }

    // TODO(b/294900119) We should do the update of encoding logic in a separate job, & remove this
    FluentFuture<Void> doUpdateEncodersForBuyers(List<AdTechIdentifier> buyers) {
        sLogger.d("Updating encoders for buyers");
        int traceCookie = Tracing.beginAsyncSection(Tracing.UPDATE_ENCODERS_FOR_BUYERS);

        List<ListenableFuture<Boolean>> encoderUpdates =
                buyers.stream()
                        .map(
                                buyer ->
                                        mEncoderLogicHandler.downloadAndUpdate(
                                                buyer, DevContext.createForDevOptionsDisabled()))
                        .collect(Collectors.toList());
        return FluentFuture.from(Futures.successfulAsList(encoderUpdates))
                .transform(
                        ignored -> {
                            Tracing.endAsyncSection(
                                    Tracing.UPDATE_ENCODERS_FOR_BUYERS, traceCookie);
                            return null;
                        },
                        mLightWeightExecutor);
    }

    private void handleFailedPerBuyerEncoding(DBEncoderLogicMetadata logic) {
        mEncoderLogicHandler.updateEncoderFailedCount(
                logic.getBuyer(), logic.getFailedEncodingCount() + 1);
    }
}

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

import static com.android.adservices.service.stats.AdsRelevanceStatusUtils.JS_RUN_STATUS_OTHER_FAILURE;
import static com.android.adservices.service.stats.AdsRelevanceStatusUtils.JS_RUN_STATUS_SUCCESS;

import android.adservices.common.AdTechIdentifier;
import android.content.Context;

import com.android.adservices.LoggerFactory;
import com.android.adservices.concurrency.AdServicesExecutors;
import com.android.adservices.data.enrollment.EnrollmentDao;
import com.android.adservices.data.signals.DBEncodedPayload;
import com.android.adservices.data.signals.DBEncoderLogicMetadata;
import com.android.adservices.data.signals.DBSignalsUpdateMetadata;
import com.android.adservices.data.signals.EncodedPayloadDao;
import com.android.adservices.data.signals.EncoderLogicHandler;
import com.android.adservices.data.signals.EncoderLogicMetadataDao;
import com.android.adservices.data.signals.ProtectedSignalsDao;
import com.android.adservices.data.signals.ProtectedSignalsDatabase;
import com.android.adservices.service.Flags;
import com.android.adservices.service.FlagsFactory;
import com.android.adservices.service.adselection.AdCounterKeyCopierNoOpImpl;
import com.android.adservices.service.adselection.AdSelectionScriptEngine;
import com.android.adservices.service.adselection.DebugReportingScriptDisabledStrategy;
import com.android.adservices.service.common.RetryStrategy;
import com.android.adservices.service.common.RetryStrategyFactory;
import com.android.adservices.service.common.SingletonRunner;
import com.android.adservices.service.devapi.DevContext;
import com.android.adservices.service.devapi.DevContextFilter;
import com.android.adservices.service.stats.AdServicesLogger;
import com.android.adservices.service.stats.AdServicesLoggerImpl;
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
import java.util.Map;
import java.util.concurrent.TimeUnit;
import java.util.function.Supplier;
import java.util.stream.Collectors;

/**
 * Handles the periodic encoding responsibilities, such as fetching the raw signals and triggering
 * the JS engine for encoding.
 */
public class PeriodicEncodingJobWorker {

    private static final LoggerFactory.Logger sLogger = LoggerFactory.getFledgeLogger();

    public static final String JOB_DESCRIPTION = "Protected-Signals Periodic Encoding";

    public static final String PAYLOAD_PERSISTENCE_ERROR_MSG = "Failed to persist encoded payload";

    private final int mPerBuyerEncodingTimeoutMs;

    private final int mEncodedPayLoadMaxSizeBytes;
    private final int mEncoderLogicMaximumFailure;

    private static final Object SINGLETON_LOCK = new Object();

    private static volatile PeriodicEncodingJobWorker sPeriodicEncodingJobWorker;

    private final EncoderLogicHandler mEncoderLogicHandler;
    private final EncoderLogicMetadataDao mEncoderLogicMetadataDao;
    private final EncodedPayloadDao mEncodedPayloadDao;
    private final SignalsProvider mSignalsProvider;
    private final ProtectedSignalsDao mProtectedSignalsDao;
    private final AdSelectionScriptEngine mScriptEngine;
    private final ListeningExecutorService mBackgroundExecutor;
    private final ListeningExecutorService mLightWeightExecutor;
    private final DevContextFilter mDevContextFilter;
    private final EnrollmentDao mEnrollmentDao;
    private final Clock mClock;
    private final AdServicesLogger mAdServicesLogger;
    private final SingletonRunner<Void> mSingletonRunner =
            new SingletonRunner<>(JOB_DESCRIPTION, this::doRun);

    private final Flags mFlags;

    @VisibleForTesting
    protected PeriodicEncodingJobWorker(
            EncoderLogicHandler encoderLogicHandler,
            EncoderLogicMetadataDao encoderLogicMetadataDao,
            EncodedPayloadDao encodedPayloadDao,
            SignalsProviderImpl signalStorageManager,
            ProtectedSignalsDao protectedSignalsDao,
            AdSelectionScriptEngine scriptEngine,
            ListeningExecutorService backgroundExecutor,
            ListeningExecutorService lightWeightExecutor,
            DevContextFilter devContextFilter,
            Flags flags,
            EnrollmentDao enrollmentDao,
            Clock clock,
            AdServicesLogger adServicesLogger) {
        mEncoderLogicHandler = encoderLogicHandler;
        mEncoderLogicMetadataDao = encoderLogicMetadataDao;
        mEncodedPayloadDao = encodedPayloadDao;
        mSignalsProvider = signalStorageManager;
        mProtectedSignalsDao = protectedSignalsDao;
        mScriptEngine = scriptEngine;
        mBackgroundExecutor = backgroundExecutor;
        mLightWeightExecutor = lightWeightExecutor;
        mDevContextFilter = devContextFilter;
        mFlags = flags;
        mEncodedPayLoadMaxSizeBytes = mFlags.getProtectedSignalsEncodedPayloadMaxSizeBytes();
        mEncoderLogicMaximumFailure =
                mFlags.getProtectedSignalsMaxJsFailureExecutionOnCertainVersionBeforeStop();
        mEnrollmentDao = enrollmentDao;
        mClock = clock;
        mAdServicesLogger = adServicesLogger;
        mPerBuyerEncodingTimeoutMs = mFlags.getPasScriptExecutionTimeoutMs();
    }

    /**
     * @return an instance of {@link PeriodicEncodingJobWorker}
     */
    public static PeriodicEncodingJobWorker getInstance() {
        Context context = ApplicationContextSingleton.get();

        PeriodicEncodingJobWorker singleReadResult = sPeriodicEncodingJobWorker;
        if (singleReadResult != null) {
            return singleReadResult;
        }

        synchronized (SINGLETON_LOCK) {
            if (sPeriodicEncodingJobWorker == null) {
                ProtectedSignalsDatabase signalsDatabase = ProtectedSignalsDatabase.getInstance();
                Flags flags = FlagsFactory.getFlags();
                RetryStrategy retryStrategy =
                        RetryStrategyFactory.createInstance(
                                        flags.getAdServicesRetryStrategyEnabled(),
                                        AdServicesExecutors.getLightWeightExecutor())
                                .createRetryStrategy(
                                        flags.getAdServicesJsScriptEngineMaxRetryAttempts());
                // since this is a background process, dev context is disabled
                DevContext devContext = DevContext.createForDevOptionsDisabled();
                sPeriodicEncodingJobWorker =
                        new PeriodicEncodingJobWorker(
                                new EncoderLogicHandler(context),
                                signalsDatabase.getEncoderLogicMetadataDao(),
                                signalsDatabase.getEncodedPayloadDao(),
                                new SignalsProviderImpl(signalsDatabase.protectedSignalsDao()),
                                signalsDatabase.protectedSignalsDao(),
                                new AdSelectionScriptEngine(
                                        context,
                                        flags::getEnforceIsolateMaxHeapSize,
                                        flags::getIsolateMaxHeapSizeBytes,
                                        new AdCounterKeyCopierNoOpImpl(),
                                        new DebugReportingScriptDisabledStrategy(),
                                        false, // not used in encoding
                                        retryStrategy,
                                        devContext),
                                AdServicesExecutors.getBackgroundExecutor(),
                                AdServicesExecutors.getLightWeightExecutor(),
                                DevContextFilter.create(context),
                                flags,
                                EnrollmentDao.getInstance(),
                                Clock.getInstance(),
                                AdServicesLoggerImpl.getInstance());
            }
        }
        return sPeriodicEncodingJobWorker;
    }

    /** Initiates the encoding of raw signals */
    public FluentFuture<Void> encodeProtectedSignals() {
        sLogger.v("Starting %s", JOB_DESCRIPTION);
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
        boolean pasExtendedMetricsEnabled = mFlags.getPasExtendedMetricsEnabled();
        EncodingJobRunStatsLogger encodingJobRunStatsLogger =
                pasExtendedMetricsEnabled
                        ? new EncodingJobRunStatsLoggerImpl(
                        mAdServicesLogger, EncodingJobRunStats.builder())
                        : new EncodingJobRunStatsLoggerNoLoggingImpl();

        FluentFuture<List<DBEncoderLogicMetadata>> buyersWithRegisteredEncoders =
                FluentFuture.from(
                        mBackgroundExecutor.submit(mEncoderLogicHandler::getAllRegisteredEncoders));

        FluentFuture<Void> encodeSignalsFuture =
                buyersWithRegisteredEncoders.transformAsync(
                        logicMetadata ->
                                doEncodingForRegisteredBuyers(
                                        logicMetadata,
                                        pasExtendedMetricsEnabled,
                                        encodingJobRunStatsLogger),
                        mBackgroundExecutor);

        // TODO(b/294900119) We should do the update of encoding logic in a separate job
        // Once the encodings are done, we update the encoder logic asynchronously
        final Instant timeForRefresh =
                Instant.now()
                        .minus(
                                mFlags.getProtectedSignalsEncoderRefreshWindowSeconds(),
                                ChronoUnit.SECONDS);
        return encodeSignalsFuture.transformAsync(
                unused -> {
                    FluentFuture<List<AdTechIdentifier>> buyersWithEncodersReadyForRefresh =
                            FluentFuture.from(
                                    mBackgroundExecutor.submit(
                                            () ->
                                                    mEncoderLogicMetadataDao
                                                            .getBuyersWithEncodersBeforeTime(
                                                                    timeForRefresh)));
                    encodingJobRunStatsLogger.logEncodingJobRunStats();

                    return buyersWithEncodersReadyForRefresh.transformAsync(
                            b -> doUpdateEncodersForBuyers(b), mBackgroundExecutor);
                },
                mLightWeightExecutor);
    }

    private FluentFuture<Void> doEncodingForRegisteredBuyers(
            List<DBEncoderLogicMetadata> encoderLogicMetadataList,
            boolean extendedLoggingEnabled,
            EncodingJobRunStatsLogger encodingJobRunStatsLogger) {

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
                .transform(ignored -> null, mLightWeightExecutor);
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
        return runEncodingPerBuyer(metadata, timeoutSeconds, logHelper, encodingJobRunStatsLogger)
                .catching(
                        Exception.class,
                        (e) -> {
                            handleFailedPerBuyerEncoding(metadata);
                            encodingJobRunStatsLogger.addOneSignalEncodingFailures();
                            return null;
                        },
                        mLightWeightExecutor);
    }

    // TODO(b/294900119) We should do the update of encoding logic in a separate job, & remove this
    private FluentFuture<Void> doUpdateEncodersForBuyers(List<AdTechIdentifier> buyers) {
        List<ListenableFuture<Boolean>> encoderUpdates =
                buyers.stream()
                        .map(
                                buyer ->
                                        mEncoderLogicHandler.downloadAndUpdate(
                                                buyer, mDevContextFilter.createDevContext()))
                        .collect(Collectors.toList());
        return FluentFuture.from(Futures.successfulAsList(encoderUpdates))
                .transform(ignored -> null, mLightWeightExecutor);
    }

    @VisibleForTesting
    FluentFuture<Void> runEncodingPerBuyer(
            DBEncoderLogicMetadata encoderLogicMetadata,
            int timeout,
            EncodingExecutionLogHelper logHelper,
            EncodingJobRunStatsLogger encodingJobRunStatsLogger) {
        AdTechIdentifier buyer = encoderLogicMetadata.getBuyer();
        Map<String, List<ProtectedSignal>> signals = mSignalsProvider.getSignals(buyer);
        if (signals.isEmpty()) {
            mEncoderLogicHandler.deleteEncoderForBuyer(buyer);
            return FluentFuture.from(Futures.immediateFuture(null));
        }

        DBSignalsUpdateMetadata signalsUpdateMetadata =
                mProtectedSignalsDao.getSignalsUpdateMetadata(buyer);
        DBEncodedPayload existingPayload = mEncodedPayloadDao.getEncodedPayload(buyer);
        if (signalsUpdateMetadata != null && existingPayload != null) {
            boolean isNoNewSignalUpdateAfterLastEncoding =
                    signalsUpdateMetadata
                            .getLastSignalsUpdatedTime()
                            .isBefore(existingPayload.getCreationTime());
            boolean isEncoderLogicNotUpdatedAfterLastEncoding =
                    encoderLogicMetadata
                            .getCreationTime()
                            .isBefore(existingPayload.getCreationTime());
            if (isNoNewSignalUpdateAfterLastEncoding && isEncoderLogicNotUpdatedAfterLastEncoding) {
                encodingJobRunStatsLogger.addOneSignalEncodingSkips();
                return FluentFuture.from(Futures.immediateFuture(null));
            }
        }

        int failedCount = encoderLogicMetadata.getFailedEncodingCount();
        if (failedCount >= mEncoderLogicMaximumFailure) {
            return FluentFuture.from(Futures.immediateFuture(null));
        }
        String encodingLogic = mEncoderLogicHandler.getEncoder(buyer);
        int version = encoderLogicMetadata.getVersion();

        logHelper.setAdtech(buyer);

        return FluentFuture.from(
                        mScriptEngine.encodeSignals(
                                encodingLogic, signals, mEncodedPayLoadMaxSizeBytes, logHelper))
                .transform(
                        encodedPayload -> {
                            validateAndPersistPayload(
                                    encoderLogicMetadata, encodedPayload, version);
                            logHelper.setStatus(JS_RUN_STATUS_SUCCESS);
                            logHelper.finish();
                            return (Void) null;
                        },
                        mBackgroundExecutor)
                .catching(
                        Exception.class,
                        e -> {
                            sLogger.e(
                                    e,
                                    "Exception trying to validate and persist encoded payload for"
                                            + " buyer: %s",
                                    buyer);
                            logHelper.setStatus(JS_RUN_STATUS_OTHER_FAILURE);
                            logHelper.finish();
                            throw new IllegalStateException(PAYLOAD_PERSISTENCE_ERROR_MSG, e);
                        },
                        mLightWeightExecutor)
                .withTimeout(timeout, TimeUnit.SECONDS, AdServicesExecutors.getScheduler());
    }

    private void handleFailedPerBuyerEncoding(DBEncoderLogicMetadata logic) {
        mEncoderLogicHandler.updateEncoderFailedCount(
                logic.getBuyer(), logic.getFailedEncodingCount() + 1);
    }

    @VisibleForTesting
    void validateAndPersistPayload(
            DBEncoderLogicMetadata encoderLogicMetadata, byte[] encodedBytes, int version) {
        AdTechIdentifier buyer = encoderLogicMetadata.getBuyer();
        if (encodedBytes.length > mEncodedPayLoadMaxSizeBytes) {
            // Do not persist encoded payload if the encoding logic violates the size constraints
            sLogger.e("Buyer:%s encoded payload exceeded max size limit", buyer);
            throw new IllegalArgumentException("Payload size exceeds limits.");
        }

        DBEncodedPayload dbEncodedPayload =
                DBEncodedPayload.builder()
                        .setBuyer(buyer)
                        .setCreationTime(Instant.now())
                        .setVersion(version)
                        .setEncodedPayload(encodedBytes)
                        .build();
        sLogger.v("Persisting encoded payload for buyer: %s", buyer);
        mEncodedPayloadDao.persistEncodedPayload(dbEncodedPayload);
        if (encoderLogicMetadata.getFailedEncodingCount() > 0) {
            mEncoderLogicHandler.updateEncoderFailedCount(buyer, 0);
        }
    }
}

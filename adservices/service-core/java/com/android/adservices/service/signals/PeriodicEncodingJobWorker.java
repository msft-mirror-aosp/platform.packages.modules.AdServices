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

import android.adservices.common.AdTechIdentifier;
import android.annotation.NonNull;
import android.content.Context;

import com.android.adservices.LoggerFactory;
import com.android.adservices.concurrency.AdServicesExecutors;
import com.android.adservices.data.signals.DBEncodedPayload;
import com.android.adservices.data.signals.EncodedPayloadDao;
import com.android.adservices.data.signals.EncoderLogicDao;
import com.android.adservices.data.signals.EncoderLogicHandler;
import com.android.adservices.data.signals.EncoderPersistenceDao;
import com.android.adservices.data.signals.ProtectedSignalsDatabase;
import com.android.adservices.service.Flags;
import com.android.adservices.service.FlagsFactory;
import com.android.adservices.service.adselection.AdCounterKeyCopierNoOpImpl;
import com.android.adservices.service.adselection.AdSelectionScriptEngine;
import com.android.adservices.service.adselection.DebugReportingScriptDisabledStrategy;
import com.android.adservices.service.common.SingletonRunner;
import com.android.adservices.service.devapi.DevContextFilter;

import com.google.common.annotations.VisibleForTesting;
import com.google.common.util.concurrent.FluentFuture;
import com.google.common.util.concurrent.Futures;
import com.google.common.util.concurrent.ListenableFuture;
import com.google.common.util.concurrent.ListeningExecutorService;

import java.time.Instant;
import java.time.temporal.ChronoUnit;
import java.util.List;
import java.util.Map;
import java.util.Objects;
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

    private static final int PER_BUYER_ENCODING_TIMEOUT_SECONDS = 5;

    private final int mEncodedPayLoadMaxSizeBytes;

    private static final Object SINGLETON_LOCK = new Object();

    private static volatile PeriodicEncodingJobWorker sPeriodicEncodingJobWorker;

    private final EncoderLogicHandler mEncoderLogicHandler;
    private final EncoderLogicDao mEncoderLogicMetadataDao;
    private final EncoderPersistenceDao mEncoderPersistenceDao;
    private final EncodedPayloadDao mEncodedPayloadDao;
    private final SignalsProvider mSignalsProvider;
    private final AdSelectionScriptEngine mScriptEngine;
    private final ListeningExecutorService mBackgroundExecutor;
    private final ListeningExecutorService mLightWeightExecutor;
    private final DevContextFilter mDevContextFilter;

    private final SingletonRunner<Void> mSingletonRunner =
            new SingletonRunner<>(JOB_DESCRIPTION, this::doRun);

    private final Flags mFlags;

    @VisibleForTesting
    protected PeriodicEncodingJobWorker(
            @NonNull EncoderLogicHandler encoderLogicHandler,
            @NonNull EncoderLogicDao encoderLogicMetadataDao,
            @NonNull EncoderPersistenceDao encoderPersistenceDao,
            @NonNull EncodedPayloadDao encodedPayloadDao,
            @NonNull SignalsProviderImpl signalStorageManager,
            @NonNull AdSelectionScriptEngine scriptEngine,
            @NonNull ListeningExecutorService backgroundExecutor,
            @NonNull ListeningExecutorService lightWeightExecutor,
            @NonNull DevContextFilter devContextFilter,
            @NonNull Flags flags) {
        mEncoderLogicHandler = encoderLogicHandler;
        mEncoderLogicMetadataDao = encoderLogicMetadataDao;
        mEncoderPersistenceDao = encoderPersistenceDao;
        mEncodedPayloadDao = encodedPayloadDao;
        mSignalsProvider = signalStorageManager;
        mScriptEngine = scriptEngine;
        mBackgroundExecutor = backgroundExecutor;
        mLightWeightExecutor = lightWeightExecutor;
        mDevContextFilter = devContextFilter;
        mFlags = flags;
        mEncodedPayLoadMaxSizeBytes = mFlags.getProtectedSignalsEncodedPayloadMaxSizeBytes();
    }

    /**
     * @return an instance of {@link PeriodicEncodingJobWorker}
     */
    @NonNull
    public static PeriodicEncodingJobWorker getInstance(@NonNull Context context) {
        Objects.requireNonNull(context);

        PeriodicEncodingJobWorker singleReadResult = sPeriodicEncodingJobWorker;
        if (singleReadResult != null) {
            return singleReadResult;
        }

        synchronized (SINGLETON_LOCK) {
            if (sPeriodicEncodingJobWorker == null) {
                ProtectedSignalsDatabase signalsDatabase =
                        ProtectedSignalsDatabase.getInstance(context);
                sPeriodicEncodingJobWorker =
                        new PeriodicEncodingJobWorker(
                                new EncoderLogicHandler(context),
                                signalsDatabase.getEncoderLogicDao(),
                                EncoderPersistenceDao.getInstance(context),
                                signalsDatabase.getEncodedPayloadDao(),
                                new SignalsProviderImpl(signalsDatabase.protectedSignalsDao()),
                                new AdSelectionScriptEngine(
                                        context,
                                        () ->
                                                FlagsFactory.getFlags()
                                                        .getEnforceIsolateMaxHeapSize(),
                                        () -> FlagsFactory.getFlags().getIsolateMaxHeapSizeBytes(),
                                        new AdCounterKeyCopierNoOpImpl(),
                                        new DebugReportingScriptDisabledStrategy(),
                                        false), // not used in encoding
                                AdServicesExecutors.getBackgroundExecutor(),
                                AdServicesExecutors.getLightWeightExecutor(),
                                DevContextFilter.create(context),
                                FlagsFactory.getFlags());
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
    private FluentFuture<Void> doRun(@NonNull Supplier<Boolean> shouldStop) {

        FluentFuture<List<AdTechIdentifier>> buyersWithRegisteredEncoders =
                FluentFuture.from(
                        mBackgroundExecutor.submit(
                                () -> mEncoderLogicMetadataDao
                                                .getAllBuyersWithRegisteredEncoders()));

        FluentFuture<Void> encodeSignalsFuture =
                buyersWithRegisteredEncoders.transformAsync(
                        b -> doEncodingForRegisteredBuyers(b), mBackgroundExecutor);

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

                    return buyersWithEncodersReadyForRefresh.transformAsync(
                            b -> doUpdateEncodersForBuyers(b), mBackgroundExecutor);
                },
                mLightWeightExecutor);
    }

    private FluentFuture<Void> doEncodingForRegisteredBuyers(List<AdTechIdentifier> buyers) {
        List<ListenableFuture<Boolean>> buyerEncodings =
                buyers.stream()
                        .map(
                                buyer ->
                                        runEncodingPerBuyer(
                                                buyer, PER_BUYER_ENCODING_TIMEOUT_SECONDS))
                        .collect(Collectors.toList());
        return FluentFuture.from(Futures.successfulAsList(buyerEncodings))
                .transform(ignored -> null, mLightWeightExecutor);
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
    FluentFuture<Boolean> runEncodingPerBuyer(AdTechIdentifier buyer, int timeout) {
        String encodingLogic = mEncoderPersistenceDao.getEncoder(buyer);
        int version = mEncoderLogicMetadataDao.getEncoder(buyer).getVersion();
        Map<String, List<ProtectedSignal>> signals = mSignalsProvider.getSignals(buyer);

        return FluentFuture.from(
                        mScriptEngine.encodeSignals(
                                encodingLogic, signals, mEncodedPayLoadMaxSizeBytes))
                .transform(
                        encodedPayload -> validateAndPersistPayload(buyer, encodedPayload, version),
                        mBackgroundExecutor)
                .catching(
                        Exception.class,
                        e -> {
                            sLogger.e(
                                    e,
                                    "Exception trying to validate and persist encoded payload for"
                                            + " buyer: %s",
                                    buyer);
                            throw new IllegalStateException(PAYLOAD_PERSISTENCE_ERROR_MSG, e);
                        },
                        mLightWeightExecutor)
                .withTimeout(timeout, TimeUnit.SECONDS, AdServicesExecutors.getScheduler());
    }

    @VisibleForTesting
    boolean validateAndPersistPayload(AdTechIdentifier buyer, byte[] encodedBytes, int version) {
        if (encodedBytes.length > mEncodedPayLoadMaxSizeBytes) {
            // Do not persist encoded payload if the encoding logic violates the size constraints
            sLogger.e("Buyer:%s encoded payload exceeded max size limit", buyer);
            return false;
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
        return true;
    }
}

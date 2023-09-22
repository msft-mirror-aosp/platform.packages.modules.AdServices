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
import com.android.adservices.data.signals.EncoderPersistenceDao;
import com.android.adservices.data.signals.ProtectedSignalsDatabase;
import com.android.adservices.service.Flags;
import com.android.adservices.service.FlagsFactory;
import com.android.adservices.service.adselection.AdCounterKeyCopierNoOpImpl;
import com.android.adservices.service.adselection.AdSelectionScriptEngine;
import com.android.adservices.service.adselection.DebugReportingScriptDisabledStrategy;
import com.android.adservices.service.common.SingletonRunner;

import com.google.common.annotations.VisibleForTesting;
import com.google.common.util.concurrent.FluentFuture;
import com.google.common.util.concurrent.Futures;
import com.google.common.util.concurrent.ListenableFuture;
import com.google.common.util.concurrent.ListeningExecutorService;

import java.time.Instant;
import java.util.Base64;
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

    private final EncoderLogicDao mEncoderLogicDao;
    private final EncoderPersistenceDao mEncoderPersistenceDao;
    private final EncodedPayloadDao mEncodedPayloadDao;
    private final SignalsProvider mSignalsProvider;
    private final AdSelectionScriptEngine mScriptEngine;
    private final ListeningExecutorService mBackgroundExecutor;
    private final ListeningExecutorService mLightWeightExecutor;

    private final SingletonRunner<Void> mSingletonRunner =
            new SingletonRunner<>(JOB_DESCRIPTION, this::doRun);

    private final Flags mFlags;

    @VisibleForTesting
    protected PeriodicEncodingJobWorker(
            @NonNull EncoderLogicDao encoderLogicDao,
            @NonNull EncoderPersistenceDao encoderPersistenceDao,
            @NonNull EncodedPayloadDao encodedPayloadDao,
            @NonNull SignalsProviderImpl signalStorageManager,
            @NonNull AdSelectionScriptEngine scriptEngine,
            @NonNull ListeningExecutorService backgroundExecutor,
            @NonNull ListeningExecutorService lightWeightExecutor,
            @NonNull Flags flags) {
        mEncoderLogicDao = encoderLogicDao;
        mEncoderPersistenceDao = encoderPersistenceDao;
        mEncodedPayloadDao = encodedPayloadDao;
        mSignalsProvider = signalStorageManager;
        mScriptEngine = scriptEngine;
        mBackgroundExecutor = backgroundExecutor;
        mLightWeightExecutor = lightWeightExecutor;
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

    private FluentFuture<Void> doRun(@NonNull Supplier<Boolean> shouldStop) {
        List<AdTechIdentifier> buyers = mEncoderLogicDao.getAllBuyersWithRegisteredEncoders();
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

    /** Requests that any ongoing work be stopped gracefully and waits for work to be stopped. */
    public void stopWork() {
        mSingletonRunner.stopWork();
    }

    @VisibleForTesting
    FluentFuture<Boolean> runEncodingPerBuyer(AdTechIdentifier buyer, int timeout) {
        String encodingLogic = mEncoderPersistenceDao.getEncoder(buyer);
        int version = mEncoderLogicDao.getEncoder(buyer).getVersion();
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
                                    "Exception trying to validate and persist encoded payload for"
                                            + " buyer: %s",
                                    buyer);
                            throw new IllegalStateException(PAYLOAD_PERSISTENCE_ERROR_MSG, e);
                        },
                        mLightWeightExecutor)
                .withTimeout(timeout, TimeUnit.SECONDS, AdServicesExecutors.getScheduler());
    }

    @VisibleForTesting
    boolean validateAndPersistPayload(AdTechIdentifier buyer, String encodedPayload, int version) {
        byte[] encodedBytes;
        try {
            encodedBytes = Base64.getDecoder().decode(encodedPayload);
        } catch (IllegalArgumentException e) {
            sLogger.e(
                    "Malformed encoded payload returned by buyer: %s, encoded payload: %s",
                    buyer, encodedPayload);
            return false;
        }
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
        mEncodedPayloadDao.persistEncodedPayload(dbEncodedPayload);
        return true;
    }
}

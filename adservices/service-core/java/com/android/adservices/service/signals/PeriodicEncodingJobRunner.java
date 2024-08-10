/*
 * Copyright (C) 2024 The Android Open Source Project
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
import android.os.Trace;

import com.android.adservices.LoggerFactory;
import com.android.adservices.concurrency.AdServicesExecutors;
import com.android.adservices.data.signals.DBEncodedPayload;
import com.android.adservices.data.signals.DBEncoderLogicMetadata;
import com.android.adservices.data.signals.DBSignalsUpdateMetadata;
import com.android.adservices.data.signals.EncodedPayloadDao;
import com.android.adservices.data.signals.EncoderLogicHandler;
import com.android.adservices.data.signals.ProtectedSignalsDao;
import com.android.adservices.service.profiling.Tracing;
import com.android.adservices.service.stats.AdsRelevanceStatusUtils;
import com.android.adservices.service.stats.pas.EncodingExecutionLogHelper;
import com.android.adservices.service.stats.pas.EncodingJobRunStatsLogger;

import com.google.common.annotations.VisibleForTesting;
import com.google.common.util.concurrent.FluentFuture;
import com.google.common.util.concurrent.Futures;
import com.google.common.util.concurrent.ListeningExecutorService;

import java.time.Instant;
import java.util.List;
import java.util.Map;
import java.util.concurrent.TimeUnit;

/** Encapsulate the business logic of executing an encoding update. */
public class PeriodicEncodingJobRunner {
    private static final LoggerFactory.Logger sLogger = LoggerFactory.getFledgeLogger();

    private final SignalsProvider mSignalsProvider;
    private final ProtectedSignalsDao mProtectedSignalsDao;
    private final SignalsScriptEngine mScriptEngine;
    private final int mEncoderLogicMaximumFailure;
    private final int mEncodedPayLoadMaxSizeBytes;
    private final EncoderLogicHandler mEncoderLogicHandler;
    private final EncodedPayloadDao mEncodedPayloadDao;
    private final ListeningExecutorService mBackgroundExecutor;
    private final ListeningExecutorService mLightWeightExecutor;

    PeriodicEncodingJobRunner(
            SignalsProvider signalsProvider,
            ProtectedSignalsDao protectedSignalsDao,
            SignalsScriptEngine scriptEngine,
            int encoderLogicMaximumFailure,
            int encodedPayLoadMaxSizeBytes,
            EncoderLogicHandler encoderLogicHandler,
            EncodedPayloadDao encodedPayloadDao,
            ListeningExecutorService backgroundExecutor,
            ListeningExecutorService lightWeightExecutor) {
        mSignalsProvider = signalsProvider;
        mProtectedSignalsDao = protectedSignalsDao;
        mScriptEngine = scriptEngine;
        mEncoderLogicMaximumFailure = encoderLogicMaximumFailure;
        mEncodedPayLoadMaxSizeBytes = encodedPayLoadMaxSizeBytes;
        mEncoderLogicHandler = encoderLogicHandler;
        mEncodedPayloadDao = encodedPayloadDao;
        mBackgroundExecutor = backgroundExecutor;
        mLightWeightExecutor = lightWeightExecutor;
    }

    /**
     * Run encoding for a given buyer.
     *
     * @param encoderLogicMetadata Metadata for the encoding logic. The buyer field is used to
     *     select the correct ad tech.
     * @param timeout Timeout in seconds.
     * @param logHelper Logger utility for execution.
     * @param encodingJobRunStatsLogger Stats utility for execution.
     * @return future for completion.
     */
    public FluentFuture<Void> runEncodingPerBuyer(
            DBEncoderLogicMetadata encoderLogicMetadata,
            int timeout,
            EncodingExecutionLogHelper logHelper,
            EncodingJobRunStatsLogger encodingJobRunStatsLogger) {
        int traceCookie = Tracing.beginAsyncSection(Tracing.RUN_ENCODING_PER_BUYER);
        AdTechIdentifier buyer = encoderLogicMetadata.getBuyer();

        Trace.beginSection(Tracing.GET_BUYER_SIGNALS);
        Map<String, List<ProtectedSignal>> signals = mSignalsProvider.getSignals(buyer);
        Trace.endSection();

        if (signals.isEmpty()) {
            mEncoderLogicHandler.deleteEncoderForBuyer(buyer);
            Tracing.endAsyncSection(Tracing.RUN_ENCODING_PER_BUYER, traceCookie);
            return FluentFuture.from(Futures.immediateFuture(null));
        }

        Trace.beginSection(Tracing.RUN_ENCODING_PER_BUYER + ":get signals update metadata");
        DBSignalsUpdateMetadata signalsUpdateMetadata =
                mProtectedSignalsDao.getSignalsUpdateMetadata(buyer);
        Trace.endSection();

        Trace.beginSection(Tracing.RUN_ENCODING_PER_BUYER + ":get encoded payload");
        DBEncodedPayload existingPayload = mEncodedPayloadDao.getEncodedPayload(buyer);
        Trace.endSection();

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
                Tracing.endAsyncSection(Tracing.RUN_ENCODING_PER_BUYER, traceCookie);
                return FluentFuture.from(Futures.immediateFuture(null));
            }
        }

        int failedCount = encoderLogicMetadata.getFailedEncodingCount();
        if (failedCount >= mEncoderLogicMaximumFailure) {
            Tracing.endAsyncSection(Tracing.RUN_ENCODING_PER_BUYER, traceCookie);
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
                            logHelper.setStatus(AdsRelevanceStatusUtils.JS_RUN_STATUS_SUCCESS);
                            logHelper.finish();
                            Tracing.endAsyncSection(Tracing.RUN_ENCODING_PER_BUYER, traceCookie);
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
                            logHelper.setStatus(
                                    AdsRelevanceStatusUtils.JS_RUN_STATUS_OTHER_FAILURE);
                            logHelper.finish();
                            Tracing.endAsyncSection(Tracing.RUN_ENCODING_PER_BUYER, traceCookie);
                            throw new IllegalStateException(
                                    PeriodicEncodingJobWorker.PAYLOAD_PERSISTENCE_ERROR_MSG, e);
                        },
                        mLightWeightExecutor)
                .withTimeout(timeout, TimeUnit.SECONDS, AdServicesExecutors.getScheduler());
    }

    @VisibleForTesting
    void validateAndPersistPayload(
            DBEncoderLogicMetadata encoderLogicMetadata, byte[] encodedBytes, int version) {
        Trace.beginSection(Tracing.VALIDATE_AND_PERSIST_PAYLOAD);
        try {
            AdTechIdentifier buyer = encoderLogicMetadata.getBuyer();
            if (encodedBytes.length > mEncodedPayLoadMaxSizeBytes) {
                // Do not persist encoded payload if the encoding logic violates the size
                // constraints
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
        } finally {
            Trace.endSection();
        }
    }
}

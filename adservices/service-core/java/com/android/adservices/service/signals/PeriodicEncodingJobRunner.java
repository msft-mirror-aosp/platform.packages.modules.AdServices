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

import com.android.adservices.LoggerFactory;
import com.android.adservices.concurrency.AdServicesExecutors;
import com.android.adservices.data.signals.DBEncodedPayload;
import com.android.adservices.data.signals.DBEncoderLogicMetadata;
import com.android.adservices.data.signals.DBSignalsUpdateMetadata;
import com.android.adservices.data.signals.EncodedPayloadDao;
import com.android.adservices.data.signals.EncoderLogicHandler;
import com.android.adservices.data.signals.ProtectedSignalsDao;
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

class PeriodicEncodingJobRunner {
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
                            logHelper.setStatus(AdsRelevanceStatusUtils.JS_RUN_STATUS_SUCCESS);
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
                            logHelper.setStatus(
                                    AdsRelevanceStatusUtils.JS_RUN_STATUS_OTHER_FAILURE);
                            logHelper.finish();
                            throw new IllegalStateException(
                                    PeriodicEncodingJobWorker.PAYLOAD_PERSISTENCE_ERROR_MSG, e);
                        },
                        mLightWeightExecutor)
                .withTimeout(timeout, TimeUnit.SECONDS, AdServicesExecutors.getScheduler());
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

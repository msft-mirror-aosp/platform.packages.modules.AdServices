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

import static com.android.adservices.service.stats.AdsRelevanceStatusUtils.PAS_ENCODING_SOURCE_TYPE_SERVICE_IMPL;

import android.adservices.common.AdTechIdentifier;
import android.content.Context;

import com.android.adservices.LoggerFactory;
import com.android.adservices.concurrency.AdServicesExecutors;
import com.android.adservices.data.signals.DBEncodedPayload;
import com.android.adservices.data.signals.EncodedPayloadDao;
import com.android.adservices.data.signals.EncoderLogicHandler;
import com.android.adservices.data.signals.ProtectedSignalsDao;
import com.android.adservices.data.signals.ProtectedSignalsDatabase;
import com.android.adservices.shared.util.Clock;

import com.google.common.annotations.VisibleForTesting;
import com.google.common.util.concurrent.FluentFuture;
import com.google.common.util.concurrent.Futures;
import com.google.common.util.concurrent.ListeningExecutorService;

import java.time.Instant;
import java.util.Objects;

public class ForcedEncoderImpl implements ForcedEncoder {
    private static final LoggerFactory.Logger sLogger = LoggerFactory.getFledgeLogger();
    private final long mFledgeForcedEncodingAfterSignalsUpdateCooldownSeconds;
    private final EncoderLogicHandler mEncoderLogicHandler;
    private final EncodedPayloadDao mEncodedPayloadDao;
    private final PeriodicEncodingJobWorker mEncodingJobWorker;
    private final ProtectedSignalsDao mProtectedSignalsDao;
    private final ListeningExecutorService mExecutor;
    private final Clock mClock;

    @VisibleForTesting
    ForcedEncoderImpl(
            long fledgeForcedEncodingAfterSignalsUpdateCooldownSeconds,
            EncoderLogicHandler encoderLogicHandler,
            EncodedPayloadDao encodedPayloadDao,
            ProtectedSignalsDao protectedSignalsDao,
            PeriodicEncodingJobWorker encodingJobWorker,
            ListeningExecutorService executor,
            Clock clock) {
        Objects.requireNonNull(
                encoderLogicHandler, "Non-null instance of EncoderLogicHandler required.");
        Objects.requireNonNull(
                encodedPayloadDao, "Non-null instance of EncodedPayloadDao required.");
        Objects.requireNonNull(
                protectedSignalsDao, "Non-null instance of ProtectedSignalsDao required.");
        Objects.requireNonNull(
                encoderLogicHandler, "Non-null instance of PeriodicEncodingJobWorker required.");
        Objects.requireNonNull(
                encoderLogicHandler, "Non-null instance of ListeningExecutorService required.");

        mFledgeForcedEncodingAfterSignalsUpdateCooldownSeconds =
                fledgeForcedEncodingAfterSignalsUpdateCooldownSeconds;
        mEncoderLogicHandler = encoderLogicHandler;
        mEncodedPayloadDao = encodedPayloadDao;
        mProtectedSignalsDao = protectedSignalsDao;
        mEncodingJobWorker = encodingJobWorker;
        mExecutor = executor;
        mClock = clock;
    }

    public ForcedEncoderImpl(
            long fledgeForcedEncodingAfterSignalsUpdateCooldownSeconds, Context context) {
        this(
                fledgeForcedEncodingAfterSignalsUpdateCooldownSeconds,
                new EncoderLogicHandler(context),
                ProtectedSignalsDatabase.getInstance().getEncodedPayloadDao(),
                ProtectedSignalsDatabase.getInstance().protectedSignalsDao(),
                PeriodicEncodingJobWorker.getInstance(),
                AdServicesExecutors.getBackgroundExecutor(),
                Clock.getInstance());
    }

    /**
     * Checks if forced encoding can be attempted for the given buyer.
     *
     * <p>Forced encoding can be attempted if either of the following conditions is met:
     *
     * <ul>
     *   <li>An encoded payload exists for the buyer and was created before the start of the
     *       cooldown window.
     *   <li>If an encoded payload does not exist, we check if the buyer has raw signals and an
     *       encoder is registered.
     * </ul>
     *
     * @param buyer the buyer for which forced encoding is being attempted.
     * @return {@code true} if forced encoding can be attempted, {@code false} otherwise.
     */
    private boolean shouldAttemptForcedEncodingForBuyer(AdTechIdentifier buyer) {
        // Fetch encoder registered with buyer.
        String encoderLogicMetadata = mEncoderLogicHandler.getEncoder(buyer);

        // Skip forced encoding if no encoder is registered.
        if (encoderLogicMetadata == null) {
            sLogger.v("Found no registered encoder for buyer %s", buyer.toString());
            return false;
        }
        sLogger.v("Found registered encoder for buyer %s", buyer.toString());

        // If an encoded payload exists, check if it was created before the start of the cooldown
        // window.
        DBEncodedPayload encodedPayload = mEncodedPayloadDao.getEncodedPayload(buyer);
        if (encodedPayload != null) {
            // Calculate the start of the cooldown window.
            Instant cooldownWindowStart =
                    Instant.ofEpochMilli(mClock.currentTimeMillis())
                            .minusSeconds(mFledgeForcedEncodingAfterSignalsUpdateCooldownSeconds);

            // Check if encoded payload was created before the start of the cooldown window.
            boolean hasEncodedPayloadBeforeCooldownStart =
                    encodedPayload.getCreationTime().isBefore(cooldownWindowStart);
            sLogger.v(
                    "Buyer %s hasEncodedPayloadBeforeCooldownStart: %s",
                    buyer, hasEncodedPayloadBeforeCooldownStart);

            return hasEncodedPayloadBeforeCooldownStart;
        }

        // With the absence of prior encoded signals, check if buyer has any raw signals.
        boolean hasRawSignals = mProtectedSignalsDao.hasSignalsFromBuyer(buyer);
        sLogger.v("Buyer %s hasRawSignals: %s", buyer, hasRawSignals);

        return hasRawSignals;
    }

    // TODO(b/380936203): Split into individual methods when we have individual implementations for
    //   raw signals encoding and encoders update.
    /**
     * Forces encoding and updates encoders.
     *
     * @return a {@link FluentFuture} that completes when the encoding and encoder updates are
     *     complete. The future's result is {@code null}.
     */
    public FluentFuture<Boolean> forceEncodingAndUpdateEncoderForBuyer(AdTechIdentifier buyer) {
        sLogger.v("Forced encoding enabled: running forced encoder for %s", buyer);

        FluentFuture<Boolean> forcedEncodingfuture =
                FluentFuture.from(Futures.immediateFuture(false)); // Default to false
        if (shouldAttemptForcedEncodingForBuyer(buyer)) {
            sLogger.v("Can attempt forced encoding for buyer %s", buyer);

            // TODO(b/381310604): Trigger the whole encoding and encoder update flow for only the
            //  calling buyer.
            forcedEncodingfuture =
                    forcedEncodingfuture.transformAsync(
                            ignored ->
                                    mEncodingJobWorker
                                            .encodeProtectedSignals(
                                                    PAS_ENCODING_SOURCE_TYPE_SERVICE_IMPL)
                                            .transform(unused -> true, mExecutor),
                            mExecutor);
        }

        return forcedEncodingfuture;
    }
}

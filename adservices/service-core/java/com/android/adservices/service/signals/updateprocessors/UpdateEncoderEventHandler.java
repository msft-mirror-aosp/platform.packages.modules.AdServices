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

package com.android.adservices.service.signals.updateprocessors;

import android.adservices.common.AdTechIdentifier;
import android.content.Context;
import android.content.Intent;
import android.net.Uri;

import androidx.annotation.NonNull;

import com.android.adservices.LoggerFactory;
import com.android.adservices.concurrency.AdServicesExecutors;
import com.android.adservices.data.signals.DBEncoderEndpoint;
import com.android.adservices.data.signals.EncoderEndpointsDao;
import com.android.adservices.data.signals.EncoderLogicHandler;
import com.android.adservices.data.signals.ProtectedSignalsDatabase;
import com.android.adservices.service.DebugFlags;
import com.android.adservices.service.devapi.DevContext;
import com.android.adservices.service.signals.ForcedEncoder;

import com.google.common.annotations.VisibleForTesting;
import com.google.common.util.concurrent.FluentFuture;
import com.google.common.util.concurrent.Futures;

import java.time.Instant;
import java.util.ArrayList;
import java.util.List;
import java.util.Objects;
import java.util.concurrent.Executor;

/** Takes appropriate action be it update or download encoder based on {@link UpdateEncoderEvent} */
public class UpdateEncoderEventHandler {
    private static final LoggerFactory.Logger sLogger = LoggerFactory.getFledgeLogger();
    @NonNull private final EncoderEndpointsDao mEncoderEndpointsDao;
    @NonNull private final EncoderLogicHandler mEncoderLogicHandler;
    private List<Observer> mUpdatesObserver;
    private final Executor mBackgroundExecutor;
    private final Context mContext;
    @NonNull private final ForcedEncoder mForcedEncoder;

    @VisibleForTesting
    public static final String ACTION_REGISTER_ENCODER_LOGIC_COMPLETE =
            "android.adservices.debug.REGISTER_ENCODER_LOGIC_COMPLETE";

    @VisibleForTesting
    public static final String FORCED_ENCODING_COMPLETED_ENCODING_ATTEMPTED =
            "FORCED_ENCODING_COMPLETED_ENCODING_ATTEMPTED";

    @VisibleForTesting
    public static final String FORCED_ENCODING_COMPLETED_ENCODING_NOT_ATTEMPTED =
            "FORCED_ENCODING_COMPLETED_ENCODING_NOT_ATTEMPTED";

    private final boolean mIsEncoderLogicRegisteredCompletionBroadcastEnabled; // for testing.
    private final boolean mIsEncoderForcedEncodingCompletionBroadcastEnabled; // for testing.

    @VisibleForTesting
    public UpdateEncoderEventHandler(
            EncoderEndpointsDao encoderEndpointsDao,
            EncoderLogicHandler encoderLogicHandler,
            Context context,
            Executor backgroundExecutor,
            boolean isEncoderLogicRegisteredCompletionBroadcastEnabled,
            ForcedEncoder forcedEncoder,
            boolean isForcedEncodingCompletionBroadcastEnabled) {
        Objects.requireNonNull(encoderEndpointsDao, "encoderEndpointsDao cannot be null");
        Objects.requireNonNull(encoderLogicHandler, "encoderLogicHandler cannot be null");
        Objects.requireNonNull(context, "context cannot be null");
        Objects.requireNonNull(backgroundExecutor, "backgroundExecutor cannot be null");
        Objects.requireNonNull(forcedEncoder, "forcedEncoder cannot be null");

        mEncoderEndpointsDao = encoderEndpointsDao;
        mEncoderLogicHandler = encoderLogicHandler;
        mUpdatesObserver = new ArrayList<>();
        mBackgroundExecutor = backgroundExecutor;
        mContext = context;
        mIsEncoderLogicRegisteredCompletionBroadcastEnabled =
                isEncoderLogicRegisteredCompletionBroadcastEnabled;
        mForcedEncoder = forcedEncoder;
        mIsEncoderForcedEncodingCompletionBroadcastEnabled =
                isForcedEncodingCompletionBroadcastEnabled;
    }

    public UpdateEncoderEventHandler(
            @NonNull Context context, @NonNull ForcedEncoder forcedEncoder) {
        this(
                ProtectedSignalsDatabase.getInstance().getEncoderEndpointsDao(),
                new EncoderLogicHandler(context),
                context,
                AdServicesExecutors.getBackgroundExecutor(),
                DebugFlags.getInstance()
                        .getProtectedAppSignalsEncoderLogicRegisteredBroadcastEnabled(),
                forcedEncoder,
                DebugFlags.getInstance().getForcedEncodingJobCompleteBroadcastEnabled());
    }

    /**
     * Handles different type of {@link UpdateEncoderEvent} based on the event type
     *
     * @param buyer Ad tech responsible for this update event
     * @param event an {@link UpdateEncoderEvent}
     * @param devContext development context used for testing network calls
     * @throws IllegalArgumentException if uri is null for registering encoder or the event type is
     *     not recognized
     */
    public void handle(
            @NonNull AdTechIdentifier buyer,
            @NonNull UpdateEncoderEvent event,
            @NonNull DevContext devContext)
            throws IllegalArgumentException {
        Objects.requireNonNull(buyer);
        Objects.requireNonNull(event);
        Objects.requireNonNull(devContext);
        sLogger.v("Entering UpdateEncoderEventHandler#handle");

        FluentFuture<Void> updateChain;
        switch (event.getUpdateType()) {
            case REGISTER:
                Uri uri = event.getEncoderEndpointUri();
                if (uri == null) {
                    throw new IllegalArgumentException(
                            "Uri cannot be null for event: " + event.getUpdateType());
                }
                DBEncoderEndpoint endpoint =
                        DBEncoderEndpoint.builder()
                                .setBuyer(buyer)
                                .setCreationTime(Instant.now())
                                .setDownloadUri(uri)
                                .build();

                DBEncoderEndpoint previousRegisteredEncoder =
                        mEncoderEndpointsDao.getEndpoint(buyer);
                mEncoderEndpointsDao.registerEndpoint(endpoint);
                sLogger.v(
                        "Registered new endpoint %s for buyer %s",
                        endpoint.getDownloadUri(), endpoint.getBuyer());

                if (previousRegisteredEncoder == null) {
                    // Immediately download and update if no previous encoder existed.
                    sLogger.v("Running download and update since previous encoder was null.");

                    // TODO(b/381305103): Refactor after updating observer semantics.
                    FluentFuture<Boolean> downloadAndUpdateBaseFuture =
                            mEncoderLogicHandler
                                    .downloadAndUpdate(buyer, devContext)
                                    .transform(
                                            result -> {
                                                sendEncoderLogicRegisteredCompletionBroadcastForTesting();
                                                return result;
                                            },
                                            mBackgroundExecutor);
                    notifyObservers(
                            buyer, event.getUpdateType().toString(), downloadAndUpdateBaseFuture);

                    // Continue the chain, if download and updated was successful.
                    updateChain =
                            downloadAndUpdateBaseFuture.transform(
                                    ignored -> null, mBackgroundExecutor);
                } else {
                    // Encoder already exists so return immediately.
                    sLogger.v("Did not update encoder as previous encoder exists");
                    updateChain = FluentFuture.from(Futures.immediateVoidFuture());
                    sendEncoderLogicRegisteredCompletionBroadcastForTesting();
                }
                break;
            default:
                throw new IllegalArgumentException(
                        "Unexpected value for update event type: " + event.getUpdateType());
        }

        updateChain
                .transformAsync(
                        ignored -> mForcedEncoder.forceEncodingAndUpdateEncoderForBuyer(buyer),
                        mBackgroundExecutor)
                .transformAsync(
                        result -> {
                            sendForcedEncodingCompletionBroadcastForTesting(result);
                            return Futures.immediateFuture(result);
                        },
                        mBackgroundExecutor)
                .addListener(
                        () -> {
                            sLogger.v("Update chain of futures has completed.");
                        },
                        mBackgroundExecutor);
    }

    /**
     * @param observer that will be notified of the update events
     */
    public void addObserver(Observer observer) {
        mUpdatesObserver.add(observer);
    }

    /**
     * @param buyer buyer for which the update happens
     * @param eventType the type of event that got completed
     * @param event the actual event result
     */
    public void notifyObservers(AdTechIdentifier buyer, String eventType, FluentFuture<?> event) {
        mUpdatesObserver.parallelStream().forEach(o -> o.update(buyer, eventType, event));
    }

    /**
     * An observer interface that helps subscribe to the update encoder events. Helps get notified
     * when an event gets completed rather than polling the DB state.
     */
    public interface Observer {
        /**
         * @param buyer buyer for which the update happens
         * @param eventType the type of event that got completed
         * @param event the actual event result
         */
        void update(AdTechIdentifier buyer, String eventType, FluentFuture<?> event);
    }

    private void sendEncoderLogicRegisteredCompletionBroadcastForTesting() {
        if (!mIsEncoderLogicRegisteredCompletionBroadcastEnabled) {
            sLogger.d("Sending completion broadcast is disabled");
            return;
        }
        sLogger.d("Sending REGISTER_ENCODER_LOGIC_COMPLETE broadcast for test to catch");
        Intent intent = new Intent(ACTION_REGISTER_ENCODER_LOGIC_COMPLETE);
        mContext.sendBroadcast(intent);
    }

    private void sendForcedEncodingCompletionBroadcastForTesting(Boolean result) {
        if (!mIsEncoderForcedEncodingCompletionBroadcastEnabled) {
            sLogger.d("Sending completion broadcast for forced encoding is disabled");
            return;
        }
        Intent intent;
        if (result) {
            sLogger.d(
                    "Sending FORCED_ENCODING_COMPLETED_ENCODING_ATTEMPTED broadcast for test to"
                            + " catch");
            intent = new Intent(FORCED_ENCODING_COMPLETED_ENCODING_ATTEMPTED);
        } else {
            sLogger.d(
                    "Sending FORCED_ENCODING_COMPLETED_ENCODING_NOT_ATTEMPTED broadcast for test to"
                            + " catch");
            intent = new Intent(FORCED_ENCODING_COMPLETED_ENCODING_NOT_ATTEMPTED);
        }
        mContext.sendBroadcast(intent);
    }
}

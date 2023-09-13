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
import android.net.Uri;

import androidx.annotation.NonNull;

import com.android.adservices.data.signals.DBEncoderEndpoint;
import com.android.adservices.data.signals.EncoderEndpointsDao;
import com.android.adservices.data.signals.EncoderLogicHandler;
import com.android.adservices.data.signals.ProtectedSignalsDatabase;

import com.google.common.annotations.VisibleForTesting;
import com.google.common.util.concurrent.FluentFuture;

import java.time.Instant;
import java.util.Objects;

/**
 * Takes appropriate action be it update, delete or download encoder based on {@link
 * UpdateEncoderEvent}
 */
public class UpdateEncoderEventHandler {
    @NonNull private final EncoderEndpointsDao mEncoderEndpointsDao;
    @NonNull private final EncoderLogicHandler mEncoderLogicHandler;

    @VisibleForTesting
    public UpdateEncoderEventHandler(
            @NonNull EncoderEndpointsDao encoderEndpointsDao,
            @NonNull EncoderLogicHandler encoderLogicHandler) {
        Objects.requireNonNull(encoderEndpointsDao);
        Objects.requireNonNull(encoderLogicHandler);
        mEncoderEndpointsDao = encoderEndpointsDao;
        mEncoderLogicHandler = encoderLogicHandler;
    }

    public UpdateEncoderEventHandler(@NonNull Context context) {
        this(
                ProtectedSignalsDatabase.getInstance(context).getEncoderEndpointsDao(),
                new EncoderLogicHandler(context));
    }

    /**
     * Handles different type of {@link UpdateEncoderEvent} based on the event type
     *
     * @param buyer Ad tech responsible for this update event
     * @param event an {@link UpdateEncoderEvent}
     * @throws IllegalArgumentException if uri is null for registering encoder or the event type is
     *     not recognized
     */
    public void handle(@NonNull AdTechIdentifier buyer, @NonNull UpdateEncoderEvent event)
            throws IllegalArgumentException {
        Objects.requireNonNull(buyer);
        Objects.requireNonNull(event);

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

                if (previousRegisteredEncoder == null) {
                    // We immediately download and update if no previous encoder existed
                    FluentFuture<Boolean> unused = mEncoderLogicHandler.downloadAndUpdate(buyer);
                }
                break;
            default:
                throw new IllegalArgumentException(
                        "Unexpected value for update event type: " + event.getUpdateType());
        }
    }
}

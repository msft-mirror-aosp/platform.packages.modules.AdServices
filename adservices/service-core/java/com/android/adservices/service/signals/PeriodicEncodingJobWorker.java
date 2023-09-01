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

import android.annotation.NonNull;
import android.content.Context;

import com.android.adservices.LoggerFactory;
import com.android.adservices.data.signals.EncodedPayloadDao;
import com.android.adservices.data.signals.EncoderLogicDao;
import com.android.adservices.data.signals.EncoderPersistenceManager;
import com.android.adservices.data.signals.ProtectedSignalsDatabase;
import com.android.adservices.service.Flags;
import com.android.adservices.service.FlagsFactory;

import com.google.common.annotations.VisibleForTesting;
import com.google.common.util.concurrent.FluentFuture;
import com.google.common.util.concurrent.Futures;

import java.util.Objects;

/**
 * Handles the periodic encoding responsibilities, such as fetching the raw signals and triggering
 * the JS engine for encoding.
 */
public class PeriodicEncodingJobWorker {

    @SuppressWarnings("unused")
    private static final LoggerFactory.Logger sLogger = LoggerFactory.getFledgeLogger();

    private static final Object SINGLETON_LOCK = new Object();
    private static volatile PeriodicEncodingJobWorker sPeriodicEncodingJobWorker;

    @SuppressWarnings("unused")
    private final EncoderLogicDao mEncoderLogicDao;

    @SuppressWarnings("unused")
    private final EncoderPersistenceManager mEncoderPersistenceManager;

    @SuppressWarnings("unused")
    private final EncodedPayloadDao mEncodedPayloadDao;

    @SuppressWarnings("unused")
    private final Flags mFlags;

    @VisibleForTesting
    protected PeriodicEncodingJobWorker(
            @NonNull EncoderLogicDao encoderLogicDao,
            @NonNull EncoderPersistenceManager encoderPersistenceManager,
            @NonNull EncodedPayloadDao encodedPayloadDao,
            @NonNull Flags flags) {
        mEncoderLogicDao = encoderLogicDao;
        mEncoderPersistenceManager = encoderPersistenceManager;
        mEncodedPayloadDao = encodedPayloadDao;
        mFlags = flags;
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
                                EncoderPersistenceManager.getInstance(context),
                                signalsDatabase.getEncodedPayloadDao(),
                                FlagsFactory.getFlags());
            }
        }
        return sPeriodicEncodingJobWorker;
    }

    /** Initiates the encoding of raw signals */
    public FluentFuture<Void> encodeProtectedSignals() {
        // TODO(b/294900378) JS changes to encode Protected Signals
        return FluentFuture.from(Futures.immediateFuture(null));
    }
}

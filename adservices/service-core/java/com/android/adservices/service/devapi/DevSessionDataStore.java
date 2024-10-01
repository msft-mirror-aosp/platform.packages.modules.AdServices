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

package com.android.adservices.service.devapi;

import android.content.Context;

import androidx.datastore.guava.GuavaDataStore;

import com.android.adservices.concurrency.AdServicesExecutors;
import com.android.adservices.service.proto.DevSession;
import com.android.adservices.shared.common.ApplicationContextSingleton;
import com.android.adservices.shared.datastore.ProtoSerializer;

import com.google.common.annotations.VisibleForTesting;
import com.google.common.util.concurrent.Futures;
import com.google.common.util.concurrent.ListenableFuture;
import com.google.protobuf.ExtensionRegistryLite;

import java.time.Clock;
import java.time.Instant;
import java.util.concurrent.Executor;

/** DataStore for {@link DevSession} state. */
public final class DevSessionDataStore {

    @VisibleForTesting static final String FILE_NAME = "dev_session.binarypb";

    private static final DevSessionDataStore sInstance =
            new DevSessionDataStore(
                    ApplicationContextSingleton.get(),
                    AdServicesExecutors.getBackgroundExecutor(),
                    AdServicesExecutors.getLightWeightExecutor(),
                    Clock.systemUTC(),
                    FILE_NAME);

    private final GuavaDataStore<DevSession> mDevSessionDataStore;
    private final Executor mLightWeightExecutor;
    private final Clock mClock;

    @VisibleForTesting
    DevSessionDataStore(
            Context context,
            Executor backgroundExecutor,
            Executor lightWeightExecutor,
            Clock clock,
            String fileName) {
        mClock = clock;
        mDevSessionDataStore =
                new GuavaDataStore.Builder(
                                context,
                                fileName,
                                new ProtoSerializer<DevSession>(
                                        DevSession.getDefaultInstance(),
                                        ExtensionRegistryLite.getEmptyRegistry()))
                        .setExecutor(backgroundExecutor)
                        .build();
        mLightWeightExecutor = lightWeightExecutor;
    }

    /**
     * @return The instance of {@link DevSessionDataStore}.
     */
    public static DevSessionDataStore getInstance() {
        return sInstance;
    }

    /**
     * @return {@code true} if dev session is active.
     */
    public ListenableFuture<Boolean> isDevSessionActive() {
        ListenableFuture<DevSession> devSessionFuture = mDevSessionDataStore.getDataAsync();
        return Futures.transform(
                devSessionFuture,
                data -> Instant.ofEpochSecond(data.getExpiryTimeSec()).isAfter(mClock.instant()),
                mLightWeightExecutor);
    }

    /**
     * Record the beginning of a dev session.
     *
     * @return Future for completion.
     */
    public ListenableFuture<Void> startDevSession(Instant expiry) {
        return setDevSessionExpiry(expiry);
    }

    /**
     * Record the ending of a dev session.
     *
     * @return Future for completion.
     */
    public ListenableFuture<Void> endDevSession() {
        return setDevSessionExpiry(Instant.EPOCH);
    }

    private ListenableFuture<Void> setDevSessionExpiry(Instant expires) {
        ListenableFuture<DevSession> updateFuture =
                mDevSessionDataStore.updateDataAsync(
                        data ->
                                data.toBuilder()
                                        .setExpiryTimeSec(expires.getEpochSecond())
                                        .build());
        return Futures.transform(updateFuture, input -> null, mLightWeightExecutor);
    }
}

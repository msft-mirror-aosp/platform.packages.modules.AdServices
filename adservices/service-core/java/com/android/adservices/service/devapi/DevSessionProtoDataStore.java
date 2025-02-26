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

import com.android.adservices.LoggerFactory;
import com.android.adservices.concurrency.AdServicesExecutors;
import com.android.adservices.service.proto.DevSessionStorage;
import com.android.adservices.shared.common.ApplicationContextSingleton;
import com.android.adservices.shared.datastore.ProtoSerializer;

import com.google.common.annotations.VisibleForTesting;
import com.google.common.util.concurrent.Futures;
import com.google.common.util.concurrent.ListenableFuture;
import com.google.protobuf.ExtensionRegistryLite;

import java.util.concurrent.Executor;

/** DataStore for {@link DevSession} state. */
public final class DevSessionProtoDataStore implements DevSessionDataStore {

    @VisibleForTesting static final String FILE_NAME = "dev_session.binarypb";

    private static final LoggerFactory.Logger sLogger = LoggerFactory.getLogger();
    private static final DevSessionDataStore sInstance =
            new DevSessionProtoDataStore(
                    ApplicationContextSingleton.get(),
                    AdServicesExecutors.getBackgroundExecutor(),
                    AdServicesExecutors.getLightWeightExecutor(),
                    FILE_NAME);

    private final Executor mLightWeightExecutor;
    private final GuavaDataStore<DevSessionStorage> mDevSessionDataStore;

    @VisibleForTesting
    DevSessionProtoDataStore(
            Context context,
            Executor backgroundExecutor,
            Executor lightWeightExecutor,
            String fileName) {
        mDevSessionDataStore =
                new GuavaDataStore.Builder(
                                context,
                                fileName,
                                new ProtoSerializer<DevSessionStorage>(
                                        DevSessionStorage.getDefaultInstance(),
                                        ExtensionRegistryLite.getEmptyRegistry()))
                        .setExecutor(backgroundExecutor)
                        .build();
        mLightWeightExecutor = lightWeightExecutor;
    }

    /**
     * @return The instance of {@link DevSessionProtoDataStore}.
     */
    static DevSessionDataStore getInstance() {
        return sInstance;
    }

    /**
     * Set the dev session state.
     *
     * @param devSession The desired state.
     * @return A future when the operation is complete.
     */
    @Override
    public ListenableFuture<DevSession> set(DevSession devSession) {
        sLogger.v("Beginning DevSessionDataStore#set(%s)", devSession);
        return Futures.transform(
                mDevSessionDataStore.updateDataAsync(
                        currentDevSession -> {
                            sLogger.v("DevSessionProtoDataStore: Completed updateDataAsync op");
                            return DevSession.toProto(devSession);
                        }),
                proto -> DevSession.fromProto(proto),
                mLightWeightExecutor);
    }

    /**
     * Get the dev session state.
     *
     * @return A future when the operation is complete, containing the current state.
     */
    @Override
    public ListenableFuture<DevSession> get() {
        sLogger.v("Beginning DevSessionDataStore#get");
        return Futures.transformAsync(
                mDevSessionDataStore.getDataAsync(),
                proto ->
                        proto.getIsStorageInitialized()
                                ? Futures.immediateFuture(DevSession.fromProto(proto))
                                : set(DevSession.createForNewlyInitializedState()),
                mLightWeightExecutor);
    }
}

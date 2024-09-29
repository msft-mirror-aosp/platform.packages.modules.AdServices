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
import com.android.adservices.service.proto.DevSessionStorage;
import com.android.adservices.shared.common.ApplicationContextSingleton;
import com.android.adservices.shared.datastore.ProtoSerializer;

import com.google.common.annotations.VisibleForTesting;
import com.google.common.util.concurrent.Futures;
import com.google.common.util.concurrent.ListenableFuture;
import com.google.protobuf.ExtensionRegistryLite;

import java.util.concurrent.Executor;

/** DataStore for {@link DevSession} state. */
public final class DevSessionDataStore {

    @VisibleForTesting static final String FILE_NAME = "dev_session.binarypb";

    private static final DevSessionDataStore sInstance =
            new DevSessionDataStore(
                    ApplicationContextSingleton.get(),
                    AdServicesExecutors.getBackgroundExecutor(),
                    AdServicesExecutors.getLightWeightExecutor(),
                    FILE_NAME);

    private final Executor mLightWeightExecutor;
    private final GuavaDataStore<DevSessionStorage> mDevSessionDataStore;

    @VisibleForTesting
    DevSessionDataStore(
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
     * @return The instance of {@link DevSessionDataStore}.
     */
    public static DevSessionDataStore getInstance() {
        return sInstance;
    }

    /**
     * Sets the dev session state.
     *
     * @param devSession The desired state.
     * @return A future when the operation is complete.
     */
    public ListenableFuture<DevSession> set(DevSession devSession) {
        return Futures.transform(
                mDevSessionDataStore.updateDataAsync(
                        currentDevSession -> DevSession.toProto(devSession)),
                proto -> DevSession.fromProto(proto),
                mLightWeightExecutor);
    }

    /**
     * Gets the dev session state.
     *
     * @return A future when the operation is complete, containing the current state.
     */
    public ListenableFuture<DevSession> get() {
        return Futures.transform(
                mDevSessionDataStore.getDataAsync(),
                proto -> DevSession.fromProto(proto),
                mLightWeightExecutor);
    }
}

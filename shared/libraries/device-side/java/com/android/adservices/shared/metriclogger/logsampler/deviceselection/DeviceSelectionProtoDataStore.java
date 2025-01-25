/*
 * Copyright (C) 2025 The Android Open Source Project
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

package com.android.adservices.shared.metriclogger.logsampler.deviceselection;

import android.content.Context;

import androidx.datastore.guava.GuavaDataStore;

import com.android.adservices.shared.datastore.ProtoSerializer;
import com.android.adservices.shared.proto.DeviceSelectionId;

import com.google.common.util.concurrent.ListenableFuture;
import com.google.protobuf.ExtensionRegistryLite;

import java.util.concurrent.Executor;

final class DeviceSelectionProtoDataStore implements DeviceSelectionDataStore {

    private static final Object LOCK = new Object();
    private static final String FILE_NAME = "adservices_udid";

    private static volatile DeviceSelectionDataStore sInstance = null;

    private final GuavaDataStore<DeviceSelectionId> mDataStore;

    /**
     * @return The instance of {@link DeviceSelectionDataStore}.
     */
    public static DeviceSelectionDataStore getInstance(
            Context context, Executor backgroundExecutor) {
        if (sInstance == null) {
            synchronized (LOCK) {
                if (sInstance == null) {
                    sInstance = new DeviceSelectionProtoDataStore(context, backgroundExecutor);
                }
            }
        }
        return sInstance;
    }

    private DeviceSelectionProtoDataStore(Context context, Executor backgroundExecutor) {
        mDataStore =
                new GuavaDataStore.Builder<>(
                                context,
                                FILE_NAME,
                                new ProtoSerializer<DeviceSelectionId>(
                                        DeviceSelectionId.getDefaultInstance(),
                                        ExtensionRegistryLite.getEmptyRegistry()))
                        .setExecutor(backgroundExecutor)
                        .build();
    }

    @Override
    public ListenableFuture<DeviceSelectionId> set(DeviceSelectionId deviceSelectionId) {
        return mDataStore.updateDataAsync(proto -> deviceSelectionId);
    }

    @Override
    public ListenableFuture<DeviceSelectionId> get() {
        return mDataStore.getDataAsync();
    }
}

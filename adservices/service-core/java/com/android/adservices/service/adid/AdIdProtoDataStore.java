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

package com.android.adservices.service.adid;

import androidx.datastore.guava.GuavaDataStore;

import com.android.adservices.service.proto.AdIdStorage;
import com.android.adservices.shared.common.ApplicationContextSingleton;
import com.android.adservices.shared.datastore.ProtoSerializer;

import com.google.common.annotations.VisibleForTesting;
import com.google.common.util.concurrent.ListenableFuture;
import com.google.protobuf.ExtensionRegistryLite;

import java.util.function.Function;

/** DataStore for {@link AdIdStorage} state. */
public final class AdIdProtoDataStore {
    @VisibleForTesting static final String FILE_NAME = "adid.binarypb";

    private static final AdIdProtoDataStore sInstance = new AdIdProtoDataStore(FILE_NAME);

    private final GuavaDataStore<AdIdStorage> mDataStore;

    @VisibleForTesting
    AdIdProtoDataStore(String fileName) {
        mDataStore =
                new GuavaDataStore.Builder(
                                ApplicationContextSingleton.get(),
                                fileName,
                                new ProtoSerializer<AdIdStorage>(
                                        AdIdStorage.getDefaultInstance(),
                                        ExtensionRegistryLite.getEmptyRegistry()))
                        .build();
    }

    /**
     * @return The instance of {@link AdIdProtoDataStore}.
     */
    public static AdIdProtoDataStore getInstance() {
        return sInstance;
    }

    /**
     * Get the AdId Storage state.
     *
     * @return A future when the operation is complete, containing the current state.
     */
    public ListenableFuture<AdIdStorage> getDataAsync() {
        return mDataStore.getDataAsync();
    }

    /**
     * Set the AdId Storage state.
     *
     * @param transform The function to save the states.
     * @return A future when the operation is complete.
     */
    public ListenableFuture<AdIdStorage> updateDataAsync(
            Function<AdIdStorage, AdIdStorage> transform) {
        return mDataStore.updateDataAsync(input -> transform.apply(input));
    }
}

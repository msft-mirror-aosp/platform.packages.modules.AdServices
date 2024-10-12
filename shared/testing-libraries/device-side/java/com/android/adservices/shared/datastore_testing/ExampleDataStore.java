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

package com.android.adservices.shared.datastore_testing;

import androidx.datastore.guava.GuavaDataStore;

import com.android.adservices.shared.common.ApplicationContextSingleton;
import com.android.adservices.shared.datastore.ProtoSerializer;
import com.android.adservices.shared.datastore_testing.proto.ExampleDatastoreProto;

import com.google.common.util.concurrent.ListenableFuture;
import com.google.protobuf.ExtensionRegistryLite;

import java.util.function.Function;

/**
 * Wrapper class for to read and update the {@link ExampleDatastoreProto} stored in the datastore.
 */
final class ExampleDataStore {
    private final GuavaDataStore<ExampleDatastoreProto> mDataStore;

    ExampleDataStore(String fileName) {
        mDataStore =
                new GuavaDataStore.Builder<ExampleDatastoreProto>(
                                ApplicationContextSingleton.get(),
                                fileName,
                                new ProtoSerializer<ExampleDatastoreProto>(
                                        ExampleDatastoreProto.getDefaultInstance(),
                                        ExtensionRegistryLite.getEmptyRegistry()))
                        .build();
    }

    ListenableFuture<ExampleDatastoreProto> getDataAsync() {
        return mDataStore.getDataAsync();
    }

    ListenableFuture<ExampleDatastoreProto> updateDataAsync(
            Function<ExampleDatastoreProto, ExampleDatastoreProto> transform) {
        return mDataStore.updateDataAsync(input -> transform.apply(input));
    }
}

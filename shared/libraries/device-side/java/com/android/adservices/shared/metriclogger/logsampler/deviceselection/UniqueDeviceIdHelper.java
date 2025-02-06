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

import com.android.adservices.shared.proto.DeviceSelectionId;
import com.android.internal.annotations.VisibleForTesting;

import com.google.common.hash.Hasher;
import com.google.common.hash.Hashing;
import com.google.common.util.concurrent.Futures;
import com.google.common.util.concurrent.ListenableFuture;

import java.time.Instant;
import java.util.UUID;
import java.util.concurrent.Executor;

/** Persists and retrieves the unique id for a device to use for logging. */
public final class UniqueDeviceIdHelper {
    private final DeviceSelectionDataStore mDataStore;
    private final Executor mLightweightExecutor;

    public UniqueDeviceIdHelper(
            Context context, Executor backgroundExecutor, Executor lightWeightExecutor) {
        this(
                DeviceSelectionProtoDataStore.getInstance(context, backgroundExecutor),
                lightWeightExecutor);
    }

    @VisibleForTesting
    UniqueDeviceIdHelper(DeviceSelectionDataStore dataStore, Executor lightweightExecutor) {
        mDataStore = dataStore;
        mLightweightExecutor = lightweightExecutor;
    }

    /**
     * Retrieves the device id from the datastore.
     *
     * <p>If the device id is not present in the datastore, we generate the unique id using hashing
     * and return it.
     */
    public ListenableFuture<Long> getDeviceId() {
        return Futures.transform(
                mDataStore.get(),
                proto -> proto.hasSelectionId() ? proto.getSelectionId() : generateAndPersist(),
                mLightweightExecutor);
    }

    private long generateUniqueDeviceId() {
        int uuid = UUID.randomUUID().hashCode();
        long currentTime = Instant.now().toEpochMilli();
        // Use hash(uuid, time) to generate a unique id. Even if 2 devices same uuid value,
        // including current time can reduce the probability of collisions.
        Hasher hasher = Hashing.murmur3_128().newHasher().putInt(uuid).putLong(currentTime);
        return hasher.hash().asLong();
    }

    private long generateAndPersist() {
        long deviceId = generateUniqueDeviceId();
        persist(deviceId);
        return deviceId;
    }

    private void persist(long deviceId) {
        var unused =
                mDataStore.set(DeviceSelectionId.newBuilder().setSelectionId(deviceId).build());
    }
}

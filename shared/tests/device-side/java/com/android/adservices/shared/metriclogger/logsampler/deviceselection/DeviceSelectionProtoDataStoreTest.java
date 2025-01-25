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

import com.android.adservices.shared.SharedUnitTestCase;
import com.android.adservices.shared.common.ApplicationContextSingleton;
import com.android.adservices.shared.proto.DeviceSelectionId;

import com.google.common.util.concurrent.ListenableFuture;
import com.google.common.util.concurrent.MoreExecutors;

import org.junit.After;
import org.junit.Before;
import org.junit.Test;

import java.util.concurrent.Executor;

public final class DeviceSelectionProtoDataStoreTest extends SharedUnitTestCase {
    private DeviceSelectionDataStore mDataStore;
    private static final Executor EXECUTOR = MoreExecutors.newDirectExecutorService();

    @Before
    public void setup() {
        mDataStore =
                DeviceSelectionProtoDataStore.getInstance(
                        ApplicationContextSingleton.get(), EXECUTOR);
    }

    @After
    public void teardown() {
        var unused = mDataStore.set(DeviceSelectionId.getDefaultInstance());
    }

    @Test
    public void testGet_defaultInstance() throws Exception {
        ListenableFuture<DeviceSelectionId> selectionId = mDataStore.get();

        expect.that(selectionId.get()).isEqualTo(DeviceSelectionId.getDefaultInstance());
    }

    @Test
    public void testGet_set() throws Exception {
        DeviceSelectionId selectionId = DeviceSelectionId.newBuilder().setSelectionId(10L).build();
        ListenableFuture<DeviceSelectionId> actual = mDataStore.set(selectionId);

        expect.that(actual.get()).isEqualTo(selectionId);

        // overwrite this value and check again
        selectionId = DeviceSelectionId.newBuilder().setSelectionId(11L).build();
        actual = mDataStore.set(selectionId);

        expect.that(actual.get()).isEqualTo(selectionId);
    }
}

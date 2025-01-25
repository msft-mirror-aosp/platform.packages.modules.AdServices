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

import com.android.adservices.shared.SharedMockitoTestCase;
import com.android.adservices.shared.common.ApplicationContextSingleton;
import com.android.adservices.shared.proto.DeviceSelectionId;

import com.google.common.util.concurrent.MoreExecutors;

import org.junit.After;
import org.junit.Before;
import org.junit.Test;

import java.util.concurrent.Executor;

public final class UniqueDeviceIdHelperTest extends SharedMockitoTestCase {
    private DeviceSelectionDataStore mDataStore;
    private static final Executor EXECUTOR = MoreExecutors.newDirectExecutorService();

    @Before
    public void setup() {
        mDataStore =
                DeviceSelectionProtoDataStore.getInstance(
                        ApplicationContextSingleton.get(), EXECUTOR);
    }

    @After
    public void tearDown() throws Exception {
        var unused = mDataStore.set(DeviceSelectionId.getDefaultInstance()).get();
    }

    @Test
    public void testGetDeviceId() throws Exception {
        DeviceSelectionId deviceSelectionId = mDataStore.get().get();
        expect.that(deviceSelectionId.hasSelectionId()).isEqualTo(false);

        UniqueDeviceIdHelper uniqueDeviceIdHelper = new UniqueDeviceIdHelper(mDataStore, EXECUTOR);
        long actualDeviceId = uniqueDeviceIdHelper.getDeviceId().get();
        long expectedDeviceId = mDataStore.get().get().getSelectionId();
        expect.that(actualDeviceId).isEqualTo(expectedDeviceId);
    }
}

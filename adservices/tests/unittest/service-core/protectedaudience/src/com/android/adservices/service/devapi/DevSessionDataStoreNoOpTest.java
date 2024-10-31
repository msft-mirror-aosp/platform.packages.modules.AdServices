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

import static org.junit.Assert.*;

import com.android.adservices.common.AdServicesUnitTestCase;
import com.android.adservices.devapi.DevSessionFixture;

import com.google.common.util.concurrent.Futures;
import com.google.common.util.concurrent.ListenableFuture;

import org.junit.Test;

public final class DevSessionDataStoreNoOpTest extends AdServicesUnitTestCase {

    private final DevSessionDataStoreNoOp mDataStore = new DevSessionDataStoreNoOp();

    @Test
    public void testSet_returnsFutureWithInProdSession() {
        ListenableFuture<DevSession> future = mDataStore.set(DevSessionFixture.IN_DEV);

        assertTrue(future.isDone());

        DevSession devSession = Futures.getUnchecked(future);
        assertEquals(DevSessionState.IN_PROD, devSession.getState());
    }

    @Test
    public void testGet_returnsFutureWithInProdSession() {
        ListenableFuture<DevSession> future = mDataStore.get();

        assertTrue(future.isDone());

        DevSession devSession = Futures.getUnchecked(future);
        assertEquals(DevSessionState.IN_PROD, devSession.getState());
    }
}

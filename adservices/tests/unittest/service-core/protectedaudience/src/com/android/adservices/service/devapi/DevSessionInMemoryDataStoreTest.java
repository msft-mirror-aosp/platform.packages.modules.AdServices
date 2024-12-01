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

import static com.google.common.util.concurrent.Futures.immediateFuture;

import com.android.adservices.common.AdServicesUnitTestCase;
import com.google.common.util.concurrent.ListenableFuture;
import com.google.common.util.concurrent.SettableFuture;
import java.util.concurrent.Future;
import java.util.concurrent.TimeUnit;

import org.junit.Test;

public class DevSessionInMemoryDataStoreTest extends AdServicesUnitTestCase {
    private static final int TIMEOUT_SEC = 5;
    private final DevSessionInMemoryDataStore mDataStore = new DevSessionInMemoryDataStore();

    @Test
    public void testGetDevSession_notSet() throws Exception {
        expect.that(wait(mDataStore.get())).isEqualTo(DevSession.createForNewlyInitializedState());
    }

    @Test
    public void testGetDevSession_set() throws Exception {
        DevSession devSession = DevSession.createForNewlyInitializedState();
        wait(mDataStore.set(devSession));
        expect.that(wait(mDataStore.get())).isEqualTo(devSession);
    }

    @Test
    public void testSetDevSession_overwritesExisting() throws Exception {
        DevSession devSession1 = DevSession.createForNewlyInitializedState();
        wait(mDataStore.set(devSession1));
        DevSession devSession2 =
                DevSession.builder().setState(DevSessionState.TRANSITIONING_PROD_TO_DEV).build();
        wait(mDataStore.set(devSession2));
        expect.that(wait(mDataStore.get())).isEqualTo(devSession2);
    }

    private static <T> T wait(Future<T> future) throws Exception {
        return future.get(TIMEOUT_SEC, TimeUnit.SECONDS);
    }
}

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

import com.android.adservices.common.AdServicesUnitTestCase;
import com.android.adservices.concurrency.AdServicesExecutors;
import com.android.adservices.devapi.DevSessionFixture;

import org.junit.After;
import org.junit.Before;
import org.junit.Test;

import java.util.concurrent.Future;
import java.util.concurrent.TimeUnit;

public final class DevSessionProtoDataStoreTest extends AdServicesUnitTestCase {

    private static final int TIMEOUT_SEC = 5;
    private DevSessionDataStore mDevSessionDataStore;

    @Before
    public void setUp() {
        mDevSessionDataStore =
                new DevSessionProtoDataStore(
                        mContext,
                        AdServicesExecutors.getBackgroundExecutor(),
                        AdServicesExecutors.getLightWeightExecutor(),
                        getTestInvocationId() + "_" + DevSessionProtoDataStore.FILE_NAME);
    }

    @After
    public void tearDown() throws Exception {
        wait(mDevSessionDataStore.set(DevSessionFixture.IN_PROD));
    }

    @Test
    public void testSetAndGetDevSession() throws Exception {
        wait(mDevSessionDataStore.set(DevSessionFixture.IN_DEV));

        expect.withMessage("DevSession future")
                .that(wait(mDevSessionDataStore.get()))
                .isEqualTo(DevSessionFixture.IN_DEV);
    }

    @Test
    public void testGetDevSessionWithoutSetAlwaysInitializes() throws Exception {
        DevSession devSession = wait(mDevSessionDataStore.get());

        expect.withMessage("DevSession future")
                .that(devSession)
                .isEqualTo(DevSession.createForNewlyInitializedState());
    }

    private static <T> T wait(Future<T> future) throws Exception {
        return future.get(TIMEOUT_SEC, TimeUnit.SECONDS);
    }
}

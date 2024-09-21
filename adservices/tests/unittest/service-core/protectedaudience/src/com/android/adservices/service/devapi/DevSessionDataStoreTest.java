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

import android.adservices.common.CommonFixture;

import com.android.adservices.common.AdServicesUnitTestCase;
import com.android.adservices.concurrency.AdServicesExecutors;

import org.junit.Before;
import org.junit.Test;

import java.time.Clock;
import java.time.ZoneId;
import java.util.concurrent.Future;
import java.util.concurrent.TimeUnit;

public final class DevSessionDataStoreTest extends AdServicesUnitTestCase {

    private static final int TIMEOUT_SEC = 2;
    private final Clock mClock = Clock.fixed(CommonFixture.FIXED_NOW, ZoneId.systemDefault());
    private DevSessionDataStore mDevSessionDataStore;

    @Before
    public void setUp() {
        mDevSessionDataStore =
                new DevSessionDataStore(
                        mContext,
                        AdServicesExecutors.getBackgroundExecutor(),
                        AdServicesExecutors.getLightWeightExecutor(),
                        mClock,
                        getTestInvocationId() + "_" + DevSessionDataStore.FILE_NAME);
    }

    @Test
    public void testStartDevSession() throws Exception {
        wait(mDevSessionDataStore.startDevSession(CommonFixture.FIXED_NEXT_ONE_DAY));

        expect.withMessage("Dev session should be active")
                .that(wait(mDevSessionDataStore.isDevSessionActive()))
                .isTrue();
    }

    @Test
    public void testDevSessionExpires() throws Exception {
        wait(mDevSessionDataStore.startDevSession(CommonFixture.FIXED_EARLIER_ONE_DAY));

        expect.withMessage("Dev session should be inactive")
                .that(wait(mDevSessionDataStore.isDevSessionActive()))
                .isFalse();
    }

    @Test
    public void testEndDevSession() throws Exception {
        wait(mDevSessionDataStore.endDevSession());

        expect.withMessage("Dev session should be inactive")
                .that(wait(mDevSessionDataStore.isDevSessionActive()))
                .isFalse();
    }

    private static <T> T wait(Future<T> future) throws Exception {
        return future.get(TIMEOUT_SEC, TimeUnit.SECONDS);
    }
}

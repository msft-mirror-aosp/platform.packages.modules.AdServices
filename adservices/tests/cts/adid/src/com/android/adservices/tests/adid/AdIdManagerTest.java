/*
 * Copyright (C) 2022 The Android Open Source Project
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
package com.android.adservices.tests.adid;

import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertTrue;

import android.adservices.adid.AdId;
import android.adservices.adid.AdIdManager;
import android.content.Context;
import android.os.LimitExceededException;
import android.os.OutcomeReceiver;
import android.util.Log;

import androidx.annotation.NonNull;
import androidx.test.core.app.ApplicationProvider;

import com.android.adservices.common.AdServicesDeviceSupportedRule;
import com.android.adservices.common.DeviceSideAdServicesFlagsSetterRule;
import com.android.adservices.common.SdkLevelSupportRule;

import org.junit.Assert;
import org.junit.Before;
import org.junit.Rule;
import org.junit.Test;

import java.util.concurrent.CompletableFuture;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.Executor;
import java.util.concurrent.Executors;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicBoolean;

public final class AdIdManagerTest {
    private static final String TAG = AdIdManagerTest.class.getSimpleName();
    private static final Executor CALLBACK_EXECUTOR = Executors.newCachedThreadPool();
    private static final Context sContext = ApplicationProvider.getApplicationContext();

    // Ignore tests when device is not at least S
    @Rule(order = 0)
    public final SdkLevelSupportRule sdkLevelRule = SdkLevelSupportRule.forAtLeastS();

    // Ignore tests when device is not supported
    @Rule(order = 1)
    public final AdServicesDeviceSupportedRule adServicesDeviceSupportedRule =
            new AdServicesDeviceSupportedRule();

    // Sets flags used in the test (and automatically reset them at the end)
    @Rule(order = 2)
    public final DeviceSideAdServicesFlagsSetterRule flags =
            DeviceSideAdServicesFlagsSetterRule.forAdidE2ETests(sContext.getPackageName());

    @Before
    public void setup() throws Exception {
        TimeUnit.SECONDS.sleep(1);
    }

    @Test
    public void testAdIdManager() throws Exception {
        AdIdManager adIdManager = AdIdManager.get(sContext);
        CompletableFuture<AdId> future = new CompletableFuture<>();
        OutcomeReceiver<AdId, Exception> callback =
                new OutcomeReceiver<>() {
                    @Override
                    public void onResult(AdId result) {
                        future.complete(result);
                    }

                    @Override
                    public void onError(Exception error) {
                        Log.e(TAG, "Failed to get Ad Id!", error);
                        Assert.fail();
                    }
                };
        adIdManager.getAdId(CALLBACK_EXECUTOR, callback);
        AdId resultAdId = future.get();
        Assert.assertNotNull(resultAdId.getAdId());
        Assert.assertNotNull(resultAdId.isLimitAdTrackingEnabled());
    }

    @Test
    public void testAdIdManager_verifyRateLimitReached() throws Exception {
        final AdIdManager adIdManager = AdIdManager.get(sContext);

        // Rate limit hasn't reached yet
        final long nowInMillis = System.currentTimeMillis();
        final float requestPerSecond = flags.getAdIdRequestPerSecond();
        for (int i = 0; i < requestPerSecond; i++) {
            assertFalse(getAdIdAndVerifyRateLimitReached(adIdManager));
        }

        // Due to bursting, we could reach the limit at the exact limit or limit + 1. Therefore,
        // triggering one more call without checking the outcome.
        getAdIdAndVerifyRateLimitReached(adIdManager);

        // Verify limit reached
        // If the test takes less than 1 second / permits per second, this test is reliable due to
        // the rate limiter limits queries per second. If duration is longer than a second, skip it.
        final boolean reachedLimit = getAdIdAndVerifyRateLimitReached(adIdManager);
        final boolean executedInLessThanOneSec =
                (System.currentTimeMillis() - nowInMillis) < (1_000 / requestPerSecond);
        if (executedInLessThanOneSec) {
            assertTrue(reachedLimit);
        }
    }

    private boolean getAdIdAndVerifyRateLimitReached(AdIdManager manager)
            throws InterruptedException {
        final AtomicBoolean reachedLimit = new AtomicBoolean(false);
        final CountDownLatch countDownLatch = new CountDownLatch(1);

        OutcomeReceiver<AdId, Exception> callback =
                new OutcomeReceiver<>() {
                    @Override
                    public void onResult(@NonNull AdId result) {
                        countDownLatch.countDown();
                    }

                    @Override
                    public void onError(@NonNull Exception error) {
                        if (error instanceof LimitExceededException) {
                            reachedLimit.set(true);
                        }
                        countDownLatch.countDown();
                    }
                };

        manager.getAdId(CALLBACK_EXECUTOR, callback);

        countDownLatch.await();
        return reachedLimit.get();
    }
}

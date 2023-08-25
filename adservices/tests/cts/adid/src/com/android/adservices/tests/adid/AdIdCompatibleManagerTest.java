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
import android.adservices.adid.AdIdCompatibleManager;
import android.adservices.common.AdServicesOutcomeReceiver;
import android.content.Context;
import android.os.LimitExceededException;

import androidx.annotation.NonNull;
import androidx.test.core.app.ApplicationProvider;

import com.android.adservices.common.AdServicesDeviceSupportedRule;
import com.android.adservices.common.AdServicesFlagsSetterRule;

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

public final class AdIdCompatibleManagerTest {
    private static final Executor CALLBACK_EXECUTOR = Executors.newCachedThreadPool();
    private static final float DEFAULT_ADID_REQUEST_PERMITS_PER_SECOND = 25f;
    private static final Context sContext = ApplicationProvider.getApplicationContext();

    @Rule(order = 0)
    public final AdServicesDeviceSupportedRule adServicesDeviceSupportedRule =
            new AdServicesDeviceSupportedRule();

    // Sets flags used in the test (and automatically reset them at the end)
    @Rule(order = 1)
    public final AdServicesFlagsSetterRule flags =
            AdServicesFlagsSetterRule.forAdidE2ETests(sContext.getPackageName());

    @Before
    public void setup() throws Exception {
        // Cool-off rate limiter in case it was initialized by another test
        TimeUnit.SECONDS.sleep(1);
    }

    @Test
    public void testAdIdCompatibleManager() throws Exception {
        AdIdCompatibleManager adIdCompatibleManager = new AdIdCompatibleManager(sContext);
        CompletableFuture<AdId> future = new CompletableFuture<>();
        AdServicesOutcomeReceiver<AdId, Exception> callback =
                new AdServicesOutcomeReceiver<>() {
                    @Override
                    public void onResult(AdId result) {
                        future.complete(result);
                    }

                    @Override
                    public void onError(Exception error) {
                        Assert.fail();
                    }
                };
        adIdCompatibleManager.getAdId(CALLBACK_EXECUTOR, callback);
        AdId resultAdId = future.get();
        Assert.assertNotNull(resultAdId.getAdId());
        Assert.assertNotNull(resultAdId.isLimitAdTrackingEnabled());
    }

    @Test
    public void testAdIdCompatibleManager_verifyRateLimitReached() throws Exception {
        final AdIdCompatibleManager adIdCompatibleManager = new AdIdCompatibleManager(sContext);

        // Rate limit hasn't reached yet
        final long nowInMillis = System.currentTimeMillis();
        final float requestPerSecond = flags.getAdIdRequestPerSecond();
        for (int i = 0; i < requestPerSecond; i++) {
            assertFalse(getAdIdAndVerifyRateLimitReached(adIdCompatibleManager));
        }

        // Due to bursting, we could reach the limit at the exact limit or limit + 1. Therefore,
        // triggering one more call without checking the outcome.
        getAdIdAndVerifyRateLimitReached(adIdCompatibleManager);

        // Verify limit reached
        // If the test takes less than 1 second / permits per second, this test is reliable due to
        // the rate limiter limits queries per second. If duration is longer than a second, skip it.
        final boolean reachedLimit = getAdIdAndVerifyRateLimitReached(adIdCompatibleManager);
        final boolean executedInLessThanOneSec =
                (System.currentTimeMillis() - nowInMillis) < (1_000 / requestPerSecond);
        if (executedInLessThanOneSec) {
            assertTrue(reachedLimit);
        }
    }

    private boolean getAdIdAndVerifyRateLimitReached(AdIdCompatibleManager manager)
            throws InterruptedException {
        final AtomicBoolean reachedLimit = new AtomicBoolean(false);
        final CountDownLatch countDownLatch = new CountDownLatch(1);

        manager.getAdId(
                CALLBACK_EXECUTOR,
                createCallbackWithCountdownOnLimitExceeded(countDownLatch, reachedLimit));

        countDownLatch.await();
        return reachedLimit.get();
    }

    private AdServicesOutcomeReceiver<AdId, Exception> createCallbackWithCountdownOnLimitExceeded(
            CountDownLatch countDownLatch, AtomicBoolean reachedLimit) {
        return new AdServicesOutcomeReceiver<>() {
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
    }
}

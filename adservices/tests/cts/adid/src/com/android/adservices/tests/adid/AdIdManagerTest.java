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

import androidx.annotation.NonNull;
import androidx.test.core.app.ApplicationProvider;
import androidx.test.runner.AndroidJUnit4;

import com.android.compatibility.common.util.ShellUtils;

import org.junit.After;
import org.junit.Assert;
import org.junit.Before;
import org.junit.Test;
import org.junit.runner.RunWith;

import java.util.concurrent.CompletableFuture;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.Executor;
import java.util.concurrent.Executors;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicBoolean;

@RunWith(AndroidJUnit4.class)
public class AdIdManagerTest {
    private static final Executor CALLBACK_EXECUTOR = Executors.newCachedThreadPool();
    private static final Context sContext = ApplicationProvider.getApplicationContext();

    private String mPreviousAppAllowList;

    @Before
    public void setup() {
        overrideAdIdKillSwitch(true);
        overridePpapiAppAllowList();
    }

    @After
    public void tearDown() throws Exception {
        overrideAdIdKillSwitch(false);
        setPpapiAppAllowList(mPreviousAppAllowList);
        // Cool-off rate limiter
        TimeUnit.SECONDS.sleep(1);
    }

    // Override adid related kill switch to ignore the effect of actual PH values.
    // If shouldOverride = true, override adid related kill switch to OFF to allow adservices
    // If shouldOverride = false, override adid related kill switch to meaningless value so that
    // PhFlags will use the default value.
    private void overrideAdIdKillSwitch(boolean shouldOverride) {
        String overrideString = shouldOverride ? "false" : "null";
        ShellUtils.runShellCommand("setprop debug.adservices.adid_kill_switch " + overrideString);
    }

    private void setPpapiAppAllowList(String allowList) {
        ShellUtils.runShellCommand(
                "device_config put adservices ppapi_app_allow_list " + allowList);
    }

    private void overridePpapiAppAllowList() {
        mPreviousAppAllowList =
                ShellUtils.runShellCommand("device_config get adservices ppapi_app_allow_list");
        setPpapiAppAllowList(mPreviousAppAllowList + "," + sContext.getPackageName());
    }

    @Test
    public void testAdIdManager() throws Exception {
        AdIdManager adIdManager = AdIdManager.get(sContext);
        CompletableFuture<AdId> future = new CompletableFuture<>();
        OutcomeReceiver<AdId, Exception> callback =
                new OutcomeReceiver<AdId, Exception>() {
                    @Override
                    public void onResult(AdId result) {
                        future.complete(result);
                    }

                    @Override
                    public void onError(Exception error) {
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
        assertFalse(getAdIdAndVerifyRateLimitReached(adIdManager));
        assertFalse(getAdIdAndVerifyRateLimitReached(adIdManager));
        assertFalse(getAdIdAndVerifyRateLimitReached(adIdManager));
        assertFalse(getAdIdAndVerifyRateLimitReached(adIdManager));
        assertFalse(getAdIdAndVerifyRateLimitReached(adIdManager));

        // Due to bursting, we could reach the limit at the exact limit or limit + 1. Therefore,
        // triggering one more call without checking the outcome.
        getAdIdAndVerifyRateLimitReached(adIdManager);

        // Verify limit reached
        assertTrue(getAdIdAndVerifyRateLimitReached(adIdManager));
    }

    private boolean getAdIdAndVerifyRateLimitReached(AdIdManager manager)
            throws InterruptedException {
        final AtomicBoolean reachedLimit = new AtomicBoolean(false);
        final CountDownLatch countDownLatch = new CountDownLatch(1);

        manager.getAdId(
                CALLBACK_EXECUTOR,
                createCallbackWithCountdownOnLimitExceeded(countDownLatch, reachedLimit));

        countDownLatch.await();
        return reachedLimit.get();
    }

    private OutcomeReceiver<AdId, Exception> createCallbackWithCountdownOnLimitExceeded(
            CountDownLatch countDownLatch, AtomicBoolean reachedLimit) {
        return new OutcomeReceiver<AdId, Exception>() {
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

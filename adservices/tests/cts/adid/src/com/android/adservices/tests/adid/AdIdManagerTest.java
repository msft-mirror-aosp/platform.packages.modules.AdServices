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

import static com.google.common.truth.Truth.assertWithMessage;

import static org.junit.Assert.fail;

import android.adservices.adid.AdId;
import android.adservices.adid.AdIdManager;
import android.content.Context;
import android.os.LimitExceededException;
import android.util.Log;

import androidx.test.core.app.ApplicationProvider;

import com.android.adservices.common.AdServicesDeviceSupportedRule;
import com.android.adservices.common.AdServicesFlagsSetterRule;
import com.android.adservices.common.OutcomeReceiverForTests;
import com.android.adservices.common.RequiresLowRamDevice;
import com.android.adservices.common.SdkLevelSupportRule;

import org.junit.Before;
import org.junit.Rule;
import org.junit.Test;

import java.util.concurrent.Executor;
import java.util.concurrent.Executors;
import java.util.concurrent.TimeUnit;

public final class AdIdManagerTest {

    private static final String TAG = AdIdManagerTest.class.getSimpleName();
    private static final long CALLBACK_TIMEOUT_MS = 500;

    private static final Executor sCallbackExecutor = Executors.newCachedThreadPool();
    private static final Context sContext = ApplicationProvider.getApplicationContext();

    // Ignore tests when device is not at least  (requires android.os.OutcomeReceiver)
    @Rule(order = 0)
    public final SdkLevelSupportRule sdkLevel = SdkLevelSupportRule.forAtLeastS();

    // Ignore tests when device is not supported
    @Rule(order = 1)
    public final AdServicesDeviceSupportedRule adServicesDeviceSupportedRule =
            new AdServicesDeviceSupportedRule();

    // Sets flags used in the test (and automatically reset them at the end)
    @Rule(order = 2)
    public final AdServicesFlagsSetterRule flags =
            AdServicesFlagsSetterRule.forAdidE2ETests(sContext.getPackageName());

    private AdIdManager mAdIdManager;

    @Before
    public void setup() throws Exception {
        Log.v(TAG, "setup(): sleeping 1s");
        TimeUnit.SECONDS.sleep(1);

        mAdIdManager = AdIdManager.get(sContext);
        assertWithMessage("AdIdManager on context %s", sContext).that(mAdIdManager).isNotNull();
    }

    @Test
    public void testAdIdManager() throws Exception {
        OutcomeReceiverForTests<AdId> callback = new OutcomeReceiverForTests<>();

        mAdIdManager.getAdId(sCallbackExecutor, callback);
        AdId resultAdId = callback.assertSuccess(CALLBACK_TIMEOUT_MS);
        Log.v(TAG, "AdId: " + toString(resultAdId));

        assertWithMessage("getAdId()").that(resultAdId.getAdId()).isNotNull();
        assertWithMessage("isLimitAdTrackingEnabled()")
                .that(resultAdId.isLimitAdTrackingEnabled())
                .isNotNull();
    }

    @Test
    public void testAdIdManager_verifyRateLimitReached() throws Exception {
        // Rate limit hasn't reached yet
        long nowInMillis = System.currentTimeMillis();
        float requestPerSecond = flags.getAdIdRequestPerSecond();
        for (int i = 0; i < requestPerSecond; i++) {
            Log.v(
                    TAG,
                    "calling getAdIdAndVerifyRateLimitReached() "
                            + (i + 1)
                            + "/"
                            + (int) requestPerSecond);
            assertWithMessage("getAdIdAndVerifyRateLimitReached() at step %s", i)
                    .that(getAdIdAndVerifyRateLimitReached())
                    .isFalse();
        }

        // Due to bursting, we could reach the limit at the exact limit or limit + 1. Therefore,
        // triggering one more call without checking the outcome.
        getAdIdAndVerifyRateLimitReached();

        // Verify limit reached
        // If the test takes less than 1 second / permits per second, this test is reliable due to
        // the rate limiter limits queries per second. If duration is longer than a second, skip it.
        boolean reachedLimit = getAdIdAndVerifyRateLimitReached();
        boolean executedInLessThanOneSec =
                (System.currentTimeMillis() - nowInMillis) < (1_000 / requestPerSecond);
        Log.d(
                TAG,
                "testAdIdManager_verifyRateLimitReached(): reachedLimit="
                        + reachedLimit
                        + ", executedInLessThanOneSec="
                        + executedInLessThanOneSec);
        if (executedInLessThanOneSec) {
            assertWithMessage("reachedLimit when executedInLessThanOneSec")
                    .that(reachedLimit)
                    .isTrue();
        }
    }

    private boolean getAdIdAndVerifyRateLimitReached() throws InterruptedException {
        OutcomeReceiverForTests<AdId> callback = new OutcomeReceiverForTests<>();
        mAdIdManager.getAdId(sCallbackExecutor, callback);
        callback.assertCalled(CALLBACK_TIMEOUT_MS);
        AdId result = callback.getResult();
        Exception error = callback.getError();
        Log.v(
                TAG,
                "getAdIdAndVerifyRateLimitReached(): result="
                        + toString(result)
                        + ", error="
                        + error);

        return error instanceof LimitExceededException;
    }

    @Test
    @RequiresLowRamDevice
    public void testAdIdManager_whenDeviceNotSupported() {
        AdIdManager adIdManager = AdIdManager.get(sContext);
        assertWithMessage("adIdManager").that(adIdManager).isNotNull();
        OutcomeReceiverForTests<AdId> receiver = new OutcomeReceiverForTests<>();

        // TODO(b/295235571): remove whole if block below once fixed
        if (true) {
            // NOTE: cannot use assertThrows() as it would cause a NoSuchClassException on R (as
            // JUnit somehow scans the whole class)
            try {
                adIdManager.getAdId(sCallbackExecutor, receiver);
                fail("getAdId() should have thrown IllegalStateException");
            } catch (IllegalStateException e) {
                // expected
            }
            return;
        }

        adIdManager.getAdId(sCallbackExecutor, receiver);
        receiver.assertFailure(IllegalStateException.class);
    }

    private static String toString(AdId adId) {
        return adId == null ? null : adId.getAdId();
    }
}

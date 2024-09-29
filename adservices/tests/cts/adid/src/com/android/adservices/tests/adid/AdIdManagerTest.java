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

import static com.android.adservices.AdServicesCommon.ACTION_ADID_PROVIDER_SERVICE;
import static com.android.adservices.shared.testing.AndroidSdk.RVC;

import static com.google.common.truth.Truth.assertWithMessage;

import android.adservices.adid.AdId;
import android.adservices.adid.AdIdManager;
import android.adservices.exceptions.AdServicesException;
import android.os.LimitExceededException;
import android.util.Log;

import androidx.test.filters.FlakyTest;

import com.android.adservices.common.AdServicesCtsTestCase;
import com.android.adservices.common.AdServicesOutcomeReceiverForTests;
import com.android.adservices.common.annotations.RequiresAndroidServiceAvailable;
import com.android.adservices.shared.testing.OutcomeReceiverForTests;
import com.android.adservices.shared.testing.annotations.RequiresLowRamDevice;
import com.android.adservices.shared.testing.annotations.RequiresSdkLevelAtLeastS;
import com.android.adservices.shared.testing.annotations.RequiresSdkRange;
import com.android.adservices.shared.testing.concurrency.FailableResultSyncCallback;
import com.android.modules.utils.build.SdkLevel;

import org.junit.Before;
import org.junit.Test;

import java.util.concurrent.Executor;
import java.util.concurrent.Executors;
import java.util.concurrent.TimeUnit;

@RequiresAndroidServiceAvailable(intentAction = ACTION_ADID_PROVIDER_SERVICE)
public final class AdIdManagerTest extends AdServicesCtsTestCase
        implements CtsAdIdEndToEndTestFlags {

    private static final Executor sCallbackExecutor = Executors.newCachedThreadPool();

    private AdIdManager mAdIdManager;

    @Before
    public void setup() throws Exception {
        Log.v(mTag, "setup(): sleeping 1s");
        TimeUnit.SECONDS.sleep(1);

        mAdIdManager = AdIdManager.get(sContext);
        assertWithMessage("AdIdManager on context %s", sContext).that(mAdIdManager).isNotNull();
    }

    @Test
    @RequiresSdkRange(atMost = RVC)
    public void testAdIdManager_getAdId_onR_invokesCallbackOnError() throws Exception {
        AdServicesOutcomeReceiverForTests<AdId> callback =
                new AdServicesOutcomeReceiverForTests<>();

        mAdIdManager.getAdId(sCallbackExecutor, callback);

        callback.assertFailure(AdServicesException.class);
    }

    @Test
    @RequiresSdkLevelAtLeastS()
    public void testAdIdManager_outcomeReceiver() throws Exception {
        OutcomeReceiverForTests<AdId> callback = new OutcomeReceiverForTests<>();
        mAdIdManager.getAdId(sCallbackExecutor, callback);
        validateAdIdManagerTestResults(callback);
    }

    @Test
    @RequiresSdkLevelAtLeastS()
    public void testAdIdManager_customReceiver() throws Exception {
        AdServicesOutcomeReceiverForTests<AdId> callback =
                new AdServicesOutcomeReceiverForTests<>();
        mAdIdManager.getAdId(sCallbackExecutor, callback);
        validateAdIdManagerTestResults(callback);
    }

    private void validateAdIdManagerTestResults(
            FailableResultSyncCallback<AdId, Exception> callback) throws Exception {
        AdId resultAdId = callback.assertResultReceived();
        Log.v(mTag, "AdId: " + toString(resultAdId));

        assertWithMessage("getAdId()").that(resultAdId.getAdId()).isNotNull();
        assertWithMessage("isLimitAdTrackingEnabled()")
                .that(resultAdId.isLimitAdTrackingEnabled())
                .isNotNull();
    }

    @Test
    @RequiresSdkLevelAtLeastS()
    @FlakyTest(bugId = 322812739)
    public void testAdIdManager_verifyRateLimitReached() throws Exception {
        // Rate limit hasn't reached yet
        long nowInMillis = System.currentTimeMillis();
        float requestPerSecond = flags.getAdIdRequestPerSecond();
        for (int i = 0; i < requestPerSecond; i++) {
            Log.v(
                    mTag,
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
                mTag,
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
        if (SdkLevel.isAtLeastS()) {
            OutcomeReceiverForTests<AdId> callback = new OutcomeReceiverForTests<>();
            mAdIdManager.getAdId(sCallbackExecutor, callback);
            return verifyAdIdRateLimitReached(callback);
        } else {
            AdServicesOutcomeReceiverForTests<AdId> callback =
                    new AdServicesOutcomeReceiverForTests<>();
            mAdIdManager.getAdId(sCallbackExecutor, callback);
            return verifyAdIdRateLimitReached(callback);
        }
    }

    private boolean verifyAdIdRateLimitReached(FailableResultSyncCallback<AdId, Exception> callback)
            throws InterruptedException {
        callback.assertCalled();
        AdId result = callback.getResult();
        Exception error = callback.getFailure();
        Log.v(
                mTag,
                "getAdIdAndVerifyRateLimitReached(): result="
                        + toString(result)
                        + ", error="
                        + error);

        return error instanceof LimitExceededException;
    }

    @Test
    @RequiresSdkLevelAtLeastS()
    @RequiresLowRamDevice
    public void testAdIdManager_whenDeviceNotSupported_outcomeReceiver() throws Exception {
        AdIdManager adIdManager = AdIdManager.get(sContext);
        assertWithMessage("adIdManager").that(adIdManager).isNotNull();
        OutcomeReceiverForTests<AdId> receiver = new OutcomeReceiverForTests<>();

        adIdManager.getAdId(sCallbackExecutor, receiver);
        receiver.assertFailure(IllegalStateException.class);
    }

    @Test
    @RequiresSdkLevelAtLeastS()
    @RequiresLowRamDevice
    public void testAdIdManager_whenDeviceNotSupported_customReceiver() throws Exception {
        AdIdManager adIdManager = AdIdManager.get(sContext);
        assertWithMessage("adIdManager").that(adIdManager).isNotNull();
        AdServicesOutcomeReceiverForTests<AdId> receiver =
                new AdServicesOutcomeReceiverForTests<>();

        adIdManager.getAdId(sCallbackExecutor, receiver);
        receiver.assertFailure(IllegalStateException.class);
    }

    private static String toString(AdId adId) {
        return adId == null ? null : adId.getAdId();
    }
}

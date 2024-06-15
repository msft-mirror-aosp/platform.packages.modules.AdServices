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
package com.android.adservices.tests.appsetid;

import static com.android.adservices.AdServicesCommon.ACTION_APPSETID_PROVIDER_SERVICE;

import static com.google.common.truth.Truth.assertThat;
import static com.google.common.truth.Truth.assertWithMessage;

import static org.junit.Assume.assumeTrue;

import android.adservices.appsetid.AppSetId;
import android.adservices.appsetid.AppSetIdManager;
import android.os.LimitExceededException;
import android.os.SystemProperties;
import android.text.TextUtils;

import androidx.annotation.Nullable;
import androidx.test.filters.FlakyTest;

import com.android.adservices.common.annotations.RequiresAndroidServiceAvailable;
import com.android.adservices.common.annotations.SetCompatModeFlags;
import com.android.adservices.common.annotations.SetPpapiAppAllowList;
import com.android.adservices.service.FlagsConstants;
import com.android.adservices.shared.common.exception.ServiceUnavailableException;
import com.android.adservices.shared.testing.OutcomeReceiverForTests;
import com.android.adservices.shared.testing.annotations.RequiresLowRamDevice;
import com.android.adservices.shared.testing.annotations.SetFlagDisabled;
import com.android.compatibility.common.util.ConnectivityUtils;
import com.android.compatibility.common.util.ShellUtils;

import org.junit.Before;
import org.junit.Test;

import java.util.concurrent.CompletableFuture;
import java.util.concurrent.Executor;
import java.util.concurrent.Executors;
import java.util.concurrent.TimeUnit;

@RequiresAndroidServiceAvailable(intentAction = ACTION_APPSETID_PROVIDER_SERVICE)
@SetCompatModeFlags
@SetFlagDisabled(FlagsConstants.KEY_APPSETID_KILL_SWITCH)
@SetPpapiAppAllowList
public final class AppSetIdManagerTest extends CtsAppSetIdEndToEndTestCase {

    private static final Executor CALLBACK_EXECUTOR = Executors.newCachedThreadPool();
    private static final float DEFAULT_APPSETID_REQUEST_PERMITS_PER_SECOND = 5f;

    @Before
    public void setup() throws Exception {
        // Cool-off rate limiter in case it was initialized by another test
        TimeUnit.SECONDS.sleep(1);
    }

    @Test
    @FlakyTest(bugId = 271656209)
    public void testAppSetIdManager() throws Exception {
        assumeOnline();
        AppSetIdManager appSetIdManager = AppSetIdManager.get(sContext);
        CompletableFuture<AppSetId> future = new CompletableFuture<>();
        OutcomeReceiverForTests<AppSetId> callback = new OutcomeReceiverForTests<AppSetId>();

        appSetIdManager.getAppSetId(CALLBACK_EXECUTOR, callback);

        AppSetId resultAppSetId = callback.assertResultReceived();
        assertWithMessage("id on result").that(resultAppSetId.getId()).isNotNull();
    }

    @Test
    @FlakyTest(bugId = 271656209)
    public void testAppSetIdManager_verifyRateLimitReached() throws Exception {
        assumeOnline();
        AppSetIdManager appSetIdManager = AppSetIdManager.get(sContext);

        // Rate limit hasn't reached yet
        long nowInMillis = System.currentTimeMillis();
        float requestPerSecond = getAppSetIdRequestPerSecond();
        for (int i = 0; i < requestPerSecond; i++) {
            assertThat(getAppSetIdAndVerifyRateLimitReached(appSetIdManager)).isFalse();
        }

        // Due to bursting, we could reach the limit at the exact limit or limit + 1. Therefore,
        // triggering one more call without checking the outcome.
        getAppSetIdAndVerifyRateLimitReached(appSetIdManager);

        // Verify limit reached
        // If the test takes less than 1 second / permits per second, this test is reliable due to
        // the rate limiter limits queries per second. If duration is longer than a second, skip it.
        boolean reachedLimit = getAppSetIdAndVerifyRateLimitReached(appSetIdManager);
        boolean executedInLessThanOneSec =
                (System.currentTimeMillis() - nowInMillis) < (1_000 / requestPerSecond);
        if (executedInLessThanOneSec) {
            assertThat(reachedLimit).isTrue();
        }
    }

    @Test
    @RequiresLowRamDevice
    public void testAppSetIdManager_whenDeviceNotSupported() throws Exception {
        AppSetIdManager appSetIdManager = AppSetIdManager.get(sContext);
        assertWithMessage("appSetIdManager").that(appSetIdManager).isNotNull();
        OutcomeReceiverForTests<AppSetId> receiver = new OutcomeReceiverForTests<>();

        appSetIdManager.getAppSetId(CALLBACK_EXECUTOR, receiver);

        // TODO(b/345835218): Create an Exception Checker for internal exceptions in tests.
        Exception e = receiver.assertFailure(IllegalStateException.class);

        assertThat(e).hasCauseThat().isInstanceOf(ServiceUnavailableException.class);
        assertThat(e.getClass().getSimpleName())
                .isEqualTo(ServiceUnavailableException.class.getSimpleName());
    }

    private boolean getAppSetIdAndVerifyRateLimitReached(AppSetIdManager manager)
            throws InterruptedException {
        OutcomeReceiverForTests<AppSetId> callback = new OutcomeReceiverForTests<AppSetId>();
        manager.getAppSetId(CALLBACK_EXECUTOR, callback);
        callback.assertCalled();

        Exception error = callback.getError();
        boolean reachedLimit = error instanceof LimitExceededException;
        mLog.d(
                "Callback returned: result=%s, error=%s, reachedLimit=%b",
                toString(callback.getResult()), error, reachedLimit);
        return reachedLimit;
    }

    private float getAppSetIdRequestPerSecond() {
        try {
            String permitString =
                    SystemProperties.get("debug.adservices.appsetid_request_permits_per_second");
            if (!TextUtils.isEmpty(permitString) && !"null".equalsIgnoreCase(permitString)) {
                return Float.parseFloat(permitString);
            }

            permitString =
                    ShellUtils.runShellCommand(
                            "device_config get adservices appsetid_request_permits_per_second");
            if (!TextUtils.isEmpty(permitString) && !"null".equalsIgnoreCase(permitString)) {
                return Float.parseFloat(permitString);
            }
            return DEFAULT_APPSETID_REQUEST_PERMITS_PER_SECOND;
        } catch (Exception e) {
            return DEFAULT_APPSETID_REQUEST_PERMITS_PER_SECOND;
        }
    }

    private static String toString(@Nullable AppSetId appSetId) {
        return appSetId == null ? "N/A" : appSetId.getId();
    }

    // TODO(b/346854625): remove this dependency
    private void assumeOnline() {
        assumeTrue("device must be online", ConnectivityUtils.isNetworkConnected(sContext));
    }
}

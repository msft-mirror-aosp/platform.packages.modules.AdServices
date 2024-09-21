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

package com.android.adservices.tests.cts.measurement;

import static com.android.adservices.service.FlagsConstants.KEY_MEASUREMENT_KILL_SWITCH;

import static com.google.common.truth.Truth.assertThat;
import static com.google.common.truth.Truth.assertWithMessage;

import static org.junit.Assert.fail;

import android.adservices.common.AdServicesOutcomeReceiver;
import android.adservices.measurement.DeletionRequest;
import android.adservices.measurement.MeasurementManager;
import android.adservices.measurement.SourceRegistrationRequest;
import android.adservices.measurement.WebSourceParams;
import android.adservices.measurement.WebSourceRegistrationRequest;
import android.adservices.measurement.WebTriggerParams;
import android.adservices.measurement.WebTriggerRegistrationRequest;
import android.net.Uri;
import android.os.LimitExceededException;
import android.os.OutcomeReceiver;
import android.os.SystemProperties;
import android.text.TextUtils;
import android.view.InputEvent;
import android.view.KeyEvent;

import androidx.annotation.NonNull;

import com.android.adservices.common.AdservicesTestHelper;
import com.android.adservices.common.WebUtil;
import com.android.adservices.service.FlagsConstants;
import com.android.adservices.shared.testing.annotations.RequiresLowRamDevice;
import com.android.adservices.shared.testing.annotations.RequiresSdkLevelAtLeastS;
import com.android.compatibility.common.util.ShellUtils;
import com.android.modules.utils.build.SdkLevel;

import org.junit.Before;
import org.junit.Test;

import java.time.Instant;
import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.Objects;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.Executor;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicBoolean;

public final class MeasurementManagerCtsTest extends CtsMeasurementEndToEndTestCase {

    private static final Executor CALLBACK_EXECUTOR = Executors.newCachedThreadPool();
    private static final long CALLBACK_TIMEOUT = 5_000L;

    /* Note: The source and trigger registration used here must match one of those in
       {@link PreEnrolledAdTechForTest}.
    */
    private static final Uri SOURCE_REGISTRATION_URI = WebUtil.validUri("https://test.test/source");
    private static final Uri TRIGGER_REGISTRATION_URI =
            WebUtil.validUri("https://test.test/trigger");
    private static final Uri LOCALHOST = Uri.parse("https://localhost");
    private static final Uri DESTINATION = WebUtil.validUri("http://trigger-origin.test");
    private static final Uri OS_DESTINATION = Uri.parse("android-app://com.os.destination");
    private static final Uri WEB_DESTINATION = WebUtil.validUri("http://web-destination.test");
    private static final Uri ORIGIN_URI = WebUtil.validUri("https://sample.example1.test");
    private static final Uri DOMAIN_URI = WebUtil.validUri("https://example2.test");
    private static final InputEvent INPUT_EVENT =
            new KeyEvent(KeyEvent.ACTION_DOWN, KeyEvent.KEYCODE_1);
    private static final float DEFAULT_REQUEST_PER_SECOND = 25f;
    private static final String FLAG_REGISTER_SOURCE =
            "measurement_register_source_request_permits_per_second";
    private static final String FLAG_REGISTER_SOURCES =
            "measurement_register_sources_request_permits_per_second";
    private static final String FLAG_REGISTER_WEB_SOURCE =
            "measurement_register_web_source_request_permits_per_second";
    private static final String FLAG_REGISTER_TRIGGER =
            "measurement_register_trigger_request_permits_per_second";
    private static final String FLAG_REGISTER_WEB_TRIGGER =
            "measurement_register_web_trigger_request_permits_per_second";

    private final ExecutorService mExecutorService = Executors.newCachedThreadPool();
    private MeasurementManager mMeasurementManager;

    @Before
    public void setup() throws Exception {
        // Kill adservices process to avoid interfering from other tests.
        AdservicesTestHelper.killAdservicesProcess(mContext);
        mMeasurementManager = MeasurementManager.get(mContext);
        assertWithMessage("MeasurementManager.get(%s)", mContext)
                .that(mMeasurementManager)
                .isNotNull();

        // Cool-off rate limiter in case it was initialized by another test
        TimeUnit.SECONDS.sleep(1);
    }

    @Test
    @RequiresLowRamDevice
    public void testMeasurementApiDisabled_lowRamDevice() throws Exception {
        MeasurementManager manager = MeasurementManager.get(mContext);
        assertWithMessage("manager").that(manager).isNotNull();

        boolean result = callMeasurementApiStatus(false);

        assertWithMessage("Msmt Api Enabled").that(result).isFalse();
    }

    @Test
    @RequiresSdkLevelAtLeastS
    public void testRegisterSource_withNoServerSetupWithCallbackOsReceiver_noErrors()
            throws Exception {
        CountDownLatch countDownLatch = new CountDownLatch(1);
        mMeasurementManager.registerSource(
                SOURCE_REGISTRATION_URI,
                /* inputEvent= */ null,
                CALLBACK_EXECUTOR,
                (OutcomeReceiver<Object, Exception>) result -> countDownLatch.countDown());
        assertThat(countDownLatch.await(CALLBACK_TIMEOUT, TimeUnit.MILLISECONDS)).isTrue();
    }

    @Test
    public void testRegisterSource_withNoServerSetupWithCallbackCustomReceiver_noErrors()
            throws Exception {
        CountDownLatch countDownLatch = new CountDownLatch(1);
        mMeasurementManager.registerSource(
                SOURCE_REGISTRATION_URI,
                /* inputEvent= */ null,
                CALLBACK_EXECUTOR,
                (AdServicesOutcomeReceiver<Object, Exception>)
                        result -> countDownLatch.countDown());
        assertThat(countDownLatch.await(CALLBACK_TIMEOUT, TimeUnit.MILLISECONDS)).isTrue();
    }

    @Test
    @RequiresSdkLevelAtLeastS
    public void testRegisterSource_withLocalhostUriNonDebuggableCallerWithOsReceiver_fails()
            throws Exception {
        CompletableFuture<Void> future = new CompletableFuture<>();
        CountDownLatch countDownLatch = new CountDownLatch(1);
        OutcomeReceiver<Object, Exception> osCallback =
                new OutcomeReceiver<>() {
                    @Override
                    public void onResult(@NonNull Object ignoredResult) {
                        fail();
                    }

                    @Override
                    public void onError(Exception error) {
                        countDownLatch.countDown();
                        future.complete(null);
                        assertThat(error).isInstanceOf(SecurityException.class);
                    }
                };
        mMeasurementManager.registerSource(
                LOCALHOST, /* inputEvent= */ null, CALLBACK_EXECUTOR, osCallback);
        assertThat(countDownLatch.await(CALLBACK_TIMEOUT, TimeUnit.MILLISECONDS)).isTrue();
        assertThat(future.get()).isNull();
    }

    @Test
    public void testRegisterSource_withLocalhostUriNonDebuggableCallerWithCustomReceiver_fails()
            throws Exception {
        CompletableFuture<Void> future = new CompletableFuture<>();
        CountDownLatch countDownLatch = new CountDownLatch(1);
        AdServicesOutcomeReceiver<Object, Exception> osCallback =
                new AdServicesOutcomeReceiver<>() {
                    @Override
                    public void onResult(@NonNull Object ignoredResult) {
                        fail();
                    }

                    @Override
                    public void onError(Exception error) {
                        countDownLatch.countDown();
                        future.complete(null);
                        assertThat(error).isInstanceOf(SecurityException.class);
                    }
                };
        mMeasurementManager.registerSource(
                LOCALHOST, /* inputEvent= */ null, CALLBACK_EXECUTOR, osCallback);
        assertThat(countDownLatch.await(CALLBACK_TIMEOUT, TimeUnit.MILLISECONDS)).isTrue();
        assertThat(future.get()).isNull();
    }

    @Test
    @RequiresSdkLevelAtLeastS
    public void testRegisterSource_withCallbackOsReceiver_verifyRateLimitReached()
            throws Exception {
        // Rate limit hasn't reached yet
        long nowInMillis = System.currentTimeMillis();
        float requestPerSecond = getRequestPerSecond(FLAG_REGISTER_SOURCE);
        for (int i = 0; i < requestPerSecond; i++) {
            assertThat(
                            registerSourceAndVerifyRateLimitReached(
                                    mMeasurementManager, /* useCustomReceiver= */ false))
                    .isFalse();
        }

        // Due to bursting, we could reach the limit at the exact limit or limit + 1. Therefore,
        // triggering one more call without checking the outcome.
        registerSourceAndVerifyRateLimitReached(
                mMeasurementManager, /* useCustomReceiver= */ false);

        // Verify limit reached
        // If the test takes less than 1 second / permits per second, this test is reliable due to
        // the rate limiter limits queries per second. If duration is longer than a second, skip it.
        boolean reachedLimit =
                registerSourceAndVerifyRateLimitReached(
                        mMeasurementManager, /* useCustomReceiver= */ false);
        boolean executedInLessThanOneSec =
                (System.currentTimeMillis() - nowInMillis) < (1_000 / requestPerSecond);
        if (executedInLessThanOneSec) {
            assertThat(reachedLimit).isTrue();
        }
    }

    @Test
    public void testRegisterSource_withCallbackCustomReceiver_verifyRateLimitReached()
            throws Exception {
        // Rate limit hasn't reached yet
        long nowInMillis = System.currentTimeMillis();
        float requestPerSecond = getRequestPerSecond(FLAG_REGISTER_SOURCE);
        for (int i = 0; i < requestPerSecond; i++) {
            assertThat(
                            registerSourceAndVerifyRateLimitReached(
                                    mMeasurementManager, /* useCustomReceiver= */ true))
                    .isFalse();
        }

        // Due to bursting, we could reach the limit at the exact limit or limit + 1. Therefore,
        // triggering one more call without checking the outcome.
        registerSourceAndVerifyRateLimitReached(mMeasurementManager, /* useCustomReceiver= */ true);

        // Verify limit reached
        // If the test takes less than 1 second / permits per second, this test is reliable due to
        // the rate limiter limits queries per second. If duration is longer than a second, skip it.
        boolean reachedLimit =
                registerSourceAndVerifyRateLimitReached(
                        mMeasurementManager, /* useCustomReceiver= */ true);
        boolean executedInLessThanOneSec =
                (System.currentTimeMillis() - nowInMillis) < (1_000 / requestPerSecond);
        if (executedInLessThanOneSec) {
            assertThat(reachedLimit).isTrue();
        }
    }

    @Test
    @RequiresSdkLevelAtLeastS
    public void testRegisterSourceMultiple_withNoServerSetupWithCallbackOsReceiver_noErrors()
            throws Exception {
        CountDownLatch countDownLatch = new CountDownLatch(1);
        OutcomeReceiver<Object, Exception> callback = result -> countDownLatch.countDown();
        mMeasurementManager.registerSource(
                createSourceRegistrationRequest(), CALLBACK_EXECUTOR, callback);
        assertThat(countDownLatch.await(CALLBACK_TIMEOUT, TimeUnit.MILLISECONDS)).isTrue();
    }

    @Test
    public void testRegisterSourceMultiple_withNoServerSetupWithCallbackCustomReceiver_noErrors()
            throws Exception {
        CountDownLatch countDownLatch = new CountDownLatch(1);
        mMeasurementManager.registerSource(
                createSourceRegistrationRequest(),
                CALLBACK_EXECUTOR,
                (AdServicesOutcomeReceiver<Object, Exception>)
                        result -> countDownLatch.countDown());
        assertThat(countDownLatch.await(CALLBACK_TIMEOUT, TimeUnit.MILLISECONDS)).isTrue();
    }

    @Test
    @RequiresSdkLevelAtLeastS
    public void testRegisterSourceMultiple_withCallbackOsReceiver_verifyRateLimitReached()
            throws Exception {
        // Rate limit hasn't reached yet
        long nowInMillis = System.currentTimeMillis();
        float requestPerSecond = getRequestPerSecond(FLAG_REGISTER_SOURCES);
        for (int i = 0; i < requestPerSecond; i++) {
            assertThat(
                            registerSourceMultipleAndVerifyRateLimitReached(
                                    mMeasurementManager, /* useCustomReceiver= */ false))
                    .isFalse();
        }

        // Due to bursting, we could reach the limit at the exact limit or limit + 1. Therefore,
        // triggering one more call without checking the outcome.
        registerSourceMultipleAndVerifyRateLimitReached(
                mMeasurementManager, /* useCustomReceiver= */ false);

        // Verify limit reached
        // If the test takes less than 1 second / permits per second, this test is reliable due to
        // the rate limiter limits queries per second. If duration is longer than a second, skip it.
        boolean reachedLimit =
                registerSourceMultipleAndVerifyRateLimitReached(
                        mMeasurementManager, /* useCustomReceiver= */ false);
        boolean executedInLessThanOneSec =
                (System.currentTimeMillis() - nowInMillis) < (1_000 / requestPerSecond);
        if (executedInLessThanOneSec) {
            assertThat(reachedLimit).isTrue();
        }
    }

    @Test
    public void testRegisterSourceMultiple_withCallbackCustomReceiver_verifyRateLimitReached()
            throws Exception {
        // Rate limit hasn't reached yet
        long nowInMillis = System.currentTimeMillis();
        float requestPerSecond = getRequestPerSecond(FLAG_REGISTER_SOURCES);
        for (int i = 0; i < requestPerSecond; i++) {
            assertWithMessage("%sth iteration; requestPerSecond %s", i, requestPerSecond)
                    .that(
                            registerSourceMultipleAndVerifyRateLimitReached(
                                    mMeasurementManager, /* useCustomReceiver= */ true))
                    .isFalse();
        }

        // Due to bursting, we could reach the limit at the exact limit or limit + 1. Therefore,
        // triggering one more call without checking the outcome.
        registerSourceMultipleAndVerifyRateLimitReached(
                mMeasurementManager, /* useCustomReceiver= */ true);

        // Verify limit reached
        // If the test takes less than 1 second / permits per second, this test is reliable due to
        // the rate limiter limits queries per second. If duration is longer than a second, skip it.
        boolean reachedLimit =
                registerSourceMultipleAndVerifyRateLimitReached(
                        mMeasurementManager, /* useCustomReceiver= */ true);
        boolean executedInLessThanOneSec =
                (System.currentTimeMillis() - nowInMillis) < (1_000 / requestPerSecond);
        if (executedInLessThanOneSec) {
            assertThat(reachedLimit).isTrue();
        }
    }

    @Test
    @RequiresSdkLevelAtLeastS
    public void testRegisterTrigger_withNoServerSetupWithCallbackOsReceiver_noErrors()
            throws Exception {
        CountDownLatch countDownLatch = new CountDownLatch(1);
        mMeasurementManager.registerTrigger(
                TRIGGER_REGISTRATION_URI,
                CALLBACK_EXECUTOR,
                (OutcomeReceiver<Object, Exception>) result -> countDownLatch.countDown());
        assertThat(countDownLatch.await(CALLBACK_TIMEOUT, TimeUnit.MILLISECONDS)).isTrue();
    }

    @Test
    public void testRegisterTrigger_withNoServerSetupWithCallbackCustomReceiver_noErrors()
            throws Exception {
        CountDownLatch countDownLatch = new CountDownLatch(1);
        mMeasurementManager.registerTrigger(
                TRIGGER_REGISTRATION_URI,
                CALLBACK_EXECUTOR,
                (AdServicesOutcomeReceiver<Object, Exception>)
                        result -> countDownLatch.countDown());
        assertThat(countDownLatch.await(CALLBACK_TIMEOUT, TimeUnit.MILLISECONDS)).isTrue();
    }

    @Test
    @RequiresSdkLevelAtLeastS
    public void testRegisterTrigger_withCallbackOsReceiver_verifyRateLimitReached()
            throws Exception {
        // Rate limit hasn't reached yet
        long nowInMillis = System.currentTimeMillis();
        float requestPerSecond = getRequestPerSecond(FLAG_REGISTER_TRIGGER);
        for (int i = 0; i < requestPerSecond; i++) {
            assertThat(
                            registerTriggerAndVerifyRateLimitReached(
                                    mMeasurementManager, /* useCustomReceiver= */ false))
                    .isFalse();
        }

        // Due to bursting, we could reach the limit at the exact limit or limit + 1. Therefore,
        // triggering one more call without checking the outcome.
        registerTriggerAndVerifyRateLimitReached(
                mMeasurementManager, /* useCustomReceiver= */ false);

        // Verify limit reached
        // If the test takes less than 1 second / permits per second, this test is reliable due to
        // the rate limiter limits queries per second. If duration is longer than a second, skip it.
        boolean reachedLimit =
                registerTriggerAndVerifyRateLimitReached(
                        mMeasurementManager, /* useCustomReceiver= */ false);
        boolean executedInLessThanOneSec =
                (System.currentTimeMillis() - nowInMillis) < (1_000 / requestPerSecond);
        if (executedInLessThanOneSec) {
            assertThat(reachedLimit).isTrue();
        }
    }

    @Test
    public void testRegisterTrigger_withCallbackCustomReceiver_verifyRateLimitReached()
            throws Exception {
        // Rate limit hasn't reached yet
        long nowInMillis = System.currentTimeMillis();
        float requestPerSecond = getRequestPerSecond(FLAG_REGISTER_TRIGGER);
        for (int i = 0; i < requestPerSecond; i++) {
            assertThat(
                            registerTriggerAndVerifyRateLimitReached(
                                    mMeasurementManager, /* useCustomReceiver= */ true))
                    .isFalse();
        }

        // Due to bursting, we could reach the limit at the exact limit or limit + 1. Therefore,
        // triggering one more call without checking the outcome.
        registerTriggerAndVerifyRateLimitReached(
                mMeasurementManager, /* useCustomReceiver= */ true);

        // Verify limit reached
        // If the test takes less than 1 second / permits per second, this test is reliable due to
        // the rate limiter limits queries per second. If duration is longer than a second, skip it.
        boolean reachedLimit =
                registerTriggerAndVerifyRateLimitReached(
                        mMeasurementManager, /* useCustomReceiver= */ true);
        boolean executedInLessThanOneSec =
                (System.currentTimeMillis() - nowInMillis) < (1_000 / requestPerSecond);
        if (executedInLessThanOneSec) {
            assertThat(reachedLimit).isTrue();
        }
    }

    @Test
    @RequiresSdkLevelAtLeastS
    public void testRegisterWebSource_withCallbackOsReceiver_noErrors() throws Exception {
        WebSourceParams webSourceParams =
                new WebSourceParams.Builder(SOURCE_REGISTRATION_URI)
                        .setDebugKeyAllowed(false)
                        .build();

        WebSourceRegistrationRequest webSourceRegistrationRequest =
                new WebSourceRegistrationRequest.Builder(
                                Collections.singletonList(webSourceParams), SOURCE_REGISTRATION_URI)
                        .setInputEvent(null)
                        .setAppDestination(OS_DESTINATION)
                        .setWebDestination(WEB_DESTINATION)
                        .setVerifiedDestination(null)
                        .build();

        CountDownLatch countDownLatch = new CountDownLatch(1);
        mMeasurementManager.registerWebSource(
                webSourceRegistrationRequest,
                CALLBACK_EXECUTOR,
                (OutcomeReceiver<Object, Exception>) result -> countDownLatch.countDown());
        assertThat(countDownLatch.await(CALLBACK_TIMEOUT, TimeUnit.MILLISECONDS)).isTrue();
    }

    @Test
    public void testRegisterWebSource_withCallbackCustomReceiver_noErrors() throws Exception {
        WebSourceParams webSourceParams =
                new WebSourceParams.Builder(SOURCE_REGISTRATION_URI)
                        .setDebugKeyAllowed(false)
                        .build();

        WebSourceRegistrationRequest webSourceRegistrationRequest =
                new WebSourceRegistrationRequest.Builder(
                                Collections.singletonList(webSourceParams), SOURCE_REGISTRATION_URI)
                        .setInputEvent(null)
                        .setAppDestination(OS_DESTINATION)
                        .setWebDestination(WEB_DESTINATION)
                        .setVerifiedDestination(null)
                        .build();

        CountDownLatch countDownLatch = new CountDownLatch(1);
        mMeasurementManager.registerWebSource(
                webSourceRegistrationRequest,
                CALLBACK_EXECUTOR,
                (AdServicesOutcomeReceiver<Object, Exception>)
                        result -> countDownLatch.countDown());
        assertThat(countDownLatch.await(CALLBACK_TIMEOUT, TimeUnit.MILLISECONDS)).isTrue();
    }

    @Test
    @RequiresSdkLevelAtLeastS
    public void testRegisterWebSource_withCallbackOsReceiver_verifyRateLimitReached()
            throws Exception {
        // Rate limit hasn't reached yet
        long nowInMillis = System.currentTimeMillis();
        float requestPerSecond = getRequestPerSecond(FLAG_REGISTER_WEB_SOURCE);
        for (int i = 0; i < requestPerSecond; i++) {
            assertThat(
                            registerWebSourceAndVerifyRateLimitReached(
                                    mMeasurementManager, /* useCustomReceiver= */ false))
                    .isFalse();
        }

        // Due to bursting, we could reach the limit at the exact limit or limit + 1. Therefore,
        // triggering one more call without checking the outcome.
        registerWebSourceAndVerifyRateLimitReached(
                mMeasurementManager, /* useCustomReceiver= */ false);

        // Verify limit reached
        // If the test takes less than 1 second / permits per second, this test is reliable due to
        // the rate limiter limits queries per second. If duration is longer than a second, skip it.
        boolean reachedLimit =
                registerWebSourceAndVerifyRateLimitReached(
                        mMeasurementManager, /* useCustomReceiver= */ false);
        boolean executedInLessThanOneSec =
                (System.currentTimeMillis() - nowInMillis) < (1_000 / requestPerSecond);
        if (executedInLessThanOneSec) {
            assertThat(reachedLimit).isTrue();
        }
    }

    @Test
    public void testRegisterWebSource_withCallbackCustomReceiver_verifyRateLimitReached()
            throws Exception {
        // Rate limit hasn't reached yet
        long nowInMillis = System.currentTimeMillis();
        float requestPerSecond = getRequestPerSecond(FLAG_REGISTER_WEB_SOURCE);
        for (int i = 0; i < requestPerSecond; i++) {
            assertThat(
                            registerWebSourceAndVerifyRateLimitReached(
                                    mMeasurementManager, /* useCustomReceiver= */ true))
                    .isFalse();
        }

        // Due to bursting, we could reach the limit at the exact limit or limit + 1. Therefore,
        // triggering one more call without checking the outcome.
        registerWebSourceAndVerifyRateLimitReached(
                mMeasurementManager, /* useCustomReceiver= */ true);

        // Verify limit reached
        // If the test takes less than 1 second / permits per second, this test is reliable due to
        // the rate limiter limits queries per second. If duration is longer than a second, skip it.
        boolean reachedLimit =
                registerWebSourceAndVerifyRateLimitReached(
                        mMeasurementManager, /* useCustomReceiver= */ true);
        boolean executedInLessThanOneSec =
                (System.currentTimeMillis() - nowInMillis) < (1_000 / requestPerSecond);
        if (executedInLessThanOneSec) {
            assertThat(reachedLimit).isTrue();
        }
    }

    @Test
    @RequiresSdkLevelAtLeastS
    public void testRegisterWebTrigger_withCallbackOsReceiver_noErrors() throws Exception {
        WebTriggerParams webTriggerParams =
                new WebTriggerParams.Builder(TRIGGER_REGISTRATION_URI).build();
        WebTriggerRegistrationRequest webTriggerRegistrationRequest =
                new WebTriggerRegistrationRequest.Builder(
                                Collections.singletonList(webTriggerParams), DESTINATION)
                        .build();

        CountDownLatch countDownLatch = new CountDownLatch(1);
        mMeasurementManager.registerWebTrigger(
                webTriggerRegistrationRequest,
                CALLBACK_EXECUTOR,
                (OutcomeReceiver<Object, Exception>) result -> countDownLatch.countDown());
        assertThat(countDownLatch.await(CALLBACK_TIMEOUT, TimeUnit.MILLISECONDS)).isTrue();
    }

    @Test
    public void testRegisterWebTrigger_withCallbackCustomReceiver_noErrors() throws Exception {
        WebTriggerParams webTriggerParams =
                new WebTriggerParams.Builder(TRIGGER_REGISTRATION_URI).build();
        WebTriggerRegistrationRequest webTriggerRegistrationRequest =
                new WebTriggerRegistrationRequest.Builder(
                                Collections.singletonList(webTriggerParams), DESTINATION)
                        .build();

        CountDownLatch countDownLatch = new CountDownLatch(1);
        mMeasurementManager.registerWebTrigger(
                webTriggerRegistrationRequest,
                CALLBACK_EXECUTOR,
                (AdServicesOutcomeReceiver<Object, Exception>)
                        result -> countDownLatch.countDown());
        assertThat(countDownLatch.await(CALLBACK_TIMEOUT, TimeUnit.MILLISECONDS)).isTrue();
    }

    @Test
    @RequiresSdkLevelAtLeastS
    public void testRegisterWebTrigger_withOsReceiver_verifyRateLimitReached() throws Exception {
        // Rate limit hasn't reached yet
        long nowInMillis = System.currentTimeMillis();
        float requestPerSecond = getRequestPerSecond(FLAG_REGISTER_WEB_TRIGGER);
        for (int i = 0; i < requestPerSecond; i++) {
            assertThat(
                            registerWebTriggerAndVerifyRateLimitReached(
                                    mMeasurementManager, /* useCustomReceiver= */ false))
                    .isFalse();
        }

        // Due to bursting, we could reach the limit at the exact limit or limit + 1. Therefore,
        // triggering one more call without checking the outcome.
        registerWebTriggerAndVerifyRateLimitReached(
                mMeasurementManager, /* useCustomReceiver= */ false);

        // Verify limit reached
        // If the test takes less than 1 second / permits per second, this test is reliable due to
        // the rate limiter limits queries per second. If duration is longer than a second, skip it.
        boolean reachedLimit =
                registerWebTriggerAndVerifyRateLimitReached(
                        mMeasurementManager, /* useCustomReceiver= */ false);
        boolean executedInLessThanOneSec =
                (System.currentTimeMillis() - nowInMillis) < (1_000 / requestPerSecond);
        if (executedInLessThanOneSec) {
            assertThat(reachedLimit).isTrue();
        }
    }

    @Test
    public void testRegisterWebTrigger_withCustomReceiver_verifyRateLimitReached()
            throws Exception {
        // Rate limit hasn't reached yet
        long nowInMillis = System.currentTimeMillis();
        float requestPerSecond = getRequestPerSecond(FLAG_REGISTER_WEB_TRIGGER);
        for (int i = 0; i < requestPerSecond; i++) {
            assertThat(
                            registerWebTriggerAndVerifyRateLimitReached(
                                    mMeasurementManager, /* useCustomReceiver= */ true))
                    .isFalse();
        }

        // Due to bursting, we could reach the limit at the exact limit or limit + 1. Therefore,
        // triggering one more call without checking the outcome.
        registerWebTriggerAndVerifyRateLimitReached(
                mMeasurementManager, /* useCustomReceiver= */ true);

        // Verify limit reached
        // If the test takes less than 1 second / permits per second, this test is reliable due to
        // the rate limiter limits queries per second. If duration is longer than a second, skip it.
        boolean reachedLimit =
                registerWebTriggerAndVerifyRateLimitReached(
                        mMeasurementManager, /* useCustomReceiver= */ true);
        boolean executedInLessThanOneSec =
                (System.currentTimeMillis() - nowInMillis) < (1_000 / requestPerSecond);
        if (executedInLessThanOneSec) {
            assertThat(reachedLimit).isTrue();
        }
    }

    @Test
    @RequiresSdkLevelAtLeastS
    public void testDeleteRegistrations_withNoOriginNoRangeWithCallbackOsReceiver_noErrors()
            throws Exception {
        DeletionRequest deletionRequest = new DeletionRequest.Builder().build();

        CountDownLatch countDownLatch = new CountDownLatch(1);
        mMeasurementManager.deleteRegistrations(
                deletionRequest,
                CALLBACK_EXECUTOR,
                (OutcomeReceiver<Object, Exception>) result -> countDownLatch.countDown());
        assertThat(countDownLatch.await(CALLBACK_TIMEOUT, TimeUnit.MILLISECONDS)).isTrue();
    }

    @Test
    public void testDeleteRegistrations_withNoOriginNoRangeWithCallbackCustomReceiver_noErrors()
            throws Exception {
        DeletionRequest deletionRequest = new DeletionRequest.Builder().build();

        CountDownLatch countDownLatch = new CountDownLatch(1);
        mMeasurementManager.deleteRegistrations(
                deletionRequest,
                CALLBACK_EXECUTOR,
                (AdServicesOutcomeReceiver<Object, Exception>)
                        result -> countDownLatch.countDown());
        assertThat(countDownLatch.await(CALLBACK_TIMEOUT, TimeUnit.MILLISECONDS)).isTrue();
    }

    @Test
    @RequiresSdkLevelAtLeastS
    public void testDeleteRegistrations_withMultipleNoOriginNoRangeWithCallbackOsReceiver_noErrors()
            throws Exception {
        DeletionRequest deletionRequest = new DeletionRequest.Builder().build();
        CountDownLatch firstCountDownLatch = new CountDownLatch(1);
        mMeasurementManager.deleteRegistrations(
                deletionRequest,
                CALLBACK_EXECUTOR,
                (OutcomeReceiver<Object, Exception>) result -> firstCountDownLatch.countDown());
        assertThat(firstCountDownLatch.await(CALLBACK_TIMEOUT, TimeUnit.MILLISECONDS)).isTrue();
        // Call it once more to ensure that there is no error when recording deletions back-to-back
        TimeUnit.SECONDS.sleep(1); // Sleep to ensure rate-limiter doesn't get tripped.
        CountDownLatch secondCountDownLatch = new CountDownLatch(1);
        mMeasurementManager.deleteRegistrations(
                deletionRequest,
                CALLBACK_EXECUTOR,
                (OutcomeReceiver<Object, Exception>) result -> secondCountDownLatch.countDown());
        assertThat(secondCountDownLatch.await(CALLBACK_TIMEOUT, TimeUnit.MILLISECONDS)).isTrue();
    }

    @Test
    public void
            testDeleteRegistrations_withMultipleNoOriginNoRangeWithCallbackCustomReceiver_noErrors()
                    throws Exception {
        DeletionRequest deletionRequest = new DeletionRequest.Builder().build();
        CountDownLatch firstCountDownLatch = new CountDownLatch(1);
        mMeasurementManager.deleteRegistrations(
                deletionRequest,
                CALLBACK_EXECUTOR,
                (AdServicesOutcomeReceiver<Object, Exception>)
                        result -> firstCountDownLatch.countDown());
        assertThat(firstCountDownLatch.await(CALLBACK_TIMEOUT, TimeUnit.MILLISECONDS)).isTrue();
        // Call it once more to ensure that there is no error when recording deletions back-to-back
        TimeUnit.SECONDS.sleep(1); // Sleep to ensure rate-limiter doesn't get tripped.
        CountDownLatch secondCountDownLatch = new CountDownLatch(1);
        mMeasurementManager.deleteRegistrations(
                deletionRequest,
                CALLBACK_EXECUTOR,
                (AdServicesOutcomeReceiver<Object, Exception>)
                        result -> secondCountDownLatch.countDown());
        assertThat(secondCountDownLatch.await(CALLBACK_TIMEOUT, TimeUnit.MILLISECONDS)).isTrue();
    }

    @Test
    @RequiresSdkLevelAtLeastS
    public void testDeleteRegistrations_WithNoRangeWithCallbackOsReceiver_noErrors()
            throws Exception {
        DeletionRequest deletionRequest =
                new DeletionRequest.Builder()
                        .setOriginUris(Collections.singletonList(ORIGIN_URI))
                        .setDomainUris(Collections.singletonList(DOMAIN_URI))
                        .build();
        CountDownLatch countDownLatch = new CountDownLatch(1);
        mMeasurementManager.deleteRegistrations(
                deletionRequest,
                CALLBACK_EXECUTOR,
                (OutcomeReceiver<Object, Exception>) result -> countDownLatch.countDown());
        assertThat(countDownLatch.await(CALLBACK_TIMEOUT, TimeUnit.MILLISECONDS)).isTrue();
    }

    @Test
    public void testDeleteRegistrations_withNoRangeWithCallbackCustomReceiver_noErrors()
            throws Exception {
        DeletionRequest deletionRequest =
                new DeletionRequest.Builder()
                        .setOriginUris(Collections.singletonList(ORIGIN_URI))
                        .setDomainUris(Collections.singletonList(DOMAIN_URI))
                        .build();
        CountDownLatch countDownLatch = new CountDownLatch(1);
        mMeasurementManager.deleteRegistrations(
                deletionRequest,
                CALLBACK_EXECUTOR,
                (AdServicesOutcomeReceiver<Object, Exception>)
                        result -> countDownLatch.countDown());
        assertThat(countDownLatch.await(CALLBACK_TIMEOUT, TimeUnit.MILLISECONDS)).isTrue();
    }

    @Test
    @RequiresSdkLevelAtLeastS
    public void testDeleteRegistrations_withEmptyListsWithRangeWithCallbackOsReceiver_noErrors()
            throws Exception {
        DeletionRequest deletionRequest =
                new DeletionRequest.Builder()
                        .setOriginUris(Collections.emptyList())
                        .setDomainUris(Collections.emptyList())
                        .setStart(Instant.ofEpochMilli(0))
                        .setEnd(Instant.now())
                        .build();
        CountDownLatch countDownLatch = new CountDownLatch(1);
        mMeasurementManager.deleteRegistrations(
                deletionRequest,
                CALLBACK_EXECUTOR,
                (OutcomeReceiver<Object, Exception>) result -> countDownLatch.countDown());
        assertThat(countDownLatch.await(CALLBACK_TIMEOUT, TimeUnit.MILLISECONDS)).isTrue();
    }

    @Test
    public void testDeleteRegistrations_withEmptyListsWithRangeWithCallbackCustomReceiver_noErrors()
            throws Exception {
        DeletionRequest deletionRequest =
                new DeletionRequest.Builder()
                        .setOriginUris(Collections.emptyList())
                        .setDomainUris(Collections.emptyList())
                        .setStart(Instant.ofEpochMilli(0))
                        .setEnd(Instant.now())
                        .build();
        CountDownLatch countDownLatch = new CountDownLatch(1);
        mMeasurementManager.deleteRegistrations(
                deletionRequest,
                CALLBACK_EXECUTOR,
                (AdServicesOutcomeReceiver<Object, Exception>)
                        result -> countDownLatch.countDown());
        assertThat(countDownLatch.await(CALLBACK_TIMEOUT, TimeUnit.MILLISECONDS)).isTrue();
    }

    @Test
    @RequiresSdkLevelAtLeastS
    public void testDeleteRegistrations_withUrisWithRangeWithCallbackOsReceiver_noErrors()
            throws Exception {
        DeletionRequest deletionRequest =
                new DeletionRequest.Builder()
                        .setOriginUris(Collections.singletonList(ORIGIN_URI))
                        .setDomainUris(Collections.singletonList(DOMAIN_URI))
                        .setStart(Instant.ofEpochMilli(0))
                        .setEnd(Instant.now())
                        .build();
        CountDownLatch countDownLatch = new CountDownLatch(1);
        mMeasurementManager.deleteRegistrations(
                deletionRequest,
                CALLBACK_EXECUTOR,
                (OutcomeReceiver<Object, Exception>) result -> countDownLatch.countDown());
        assertThat(countDownLatch.await(CALLBACK_TIMEOUT, TimeUnit.MILLISECONDS)).isTrue();
    }

    @Test
    public void testDeleteRegistrations_withUrisWithRangeWithCallbackCustomReceiver_noErrors()
            throws Exception {
        DeletionRequest deletionRequest =
                new DeletionRequest.Builder()
                        .setOriginUris(Collections.singletonList(ORIGIN_URI))
                        .setDomainUris(Collections.singletonList(DOMAIN_URI))
                        .setStart(Instant.ofEpochMilli(0))
                        .setEnd(Instant.now())
                        .build();
        CountDownLatch countDownLatch = new CountDownLatch(1);
        mMeasurementManager.deleteRegistrations(
                deletionRequest,
                CALLBACK_EXECUTOR,
                (AdServicesOutcomeReceiver<Object, Exception>)
                        result -> countDownLatch.countDown());
        assertThat(countDownLatch.await(CALLBACK_TIMEOUT, TimeUnit.MILLISECONDS)).isTrue();
    }

    @Test
    @RequiresSdkLevelAtLeastS
    public void testDeleteRegistrations_withInvalidArgumentsWithCallbackOsReceiver_hasError()
            throws Exception {
        MeasurementManager manager = MeasurementManager.get(mContext);
        Objects.requireNonNull(manager);

        CompletableFuture<Void> future = new CompletableFuture<>();
        OutcomeReceiver<Object, Exception> callback =
                new OutcomeReceiver<>() {
                    @Override
                    public void onResult(@NonNull Object ignoredResult) {
                        fail();
                    }

                    @Override
                    public void onError(Exception error) {
                        future.complete(null);
                        assertThat(error).isInstanceOf(IllegalArgumentException.class);
                    }
                };
        DeletionRequest request =
                new DeletionRequest.Builder()
                        .setOriginUris(Collections.singletonList(ORIGIN_URI))
                        .setDomainUris(Collections.singletonList(DOMAIN_URI))
                        .setStart(Instant.now().plusMillis(1000))
                        .setEnd(Instant.now())
                        .build();

        manager.deleteRegistrations(request, mExecutorService, callback);
        assertThat(future.get()).isNull();
    }

    @Test
    public void testDeleteRegistrations_withInvalidArgumentsWithCallbackCustomReceiver_hasError()
            throws Exception {
        MeasurementManager manager = MeasurementManager.get(mContext);
        Objects.requireNonNull(manager);

        CompletableFuture<Void> future = new CompletableFuture<>();
        AdServicesOutcomeReceiver<Object, Exception> callback =
                new AdServicesOutcomeReceiver<>() {
                    @Override
                    public void onResult(@NonNull Object ignoredResult) {
                        fail();
                    }

                    @Override
                    public void onError(Exception error) {
                        future.complete(null);
                        assertThat(error).isInstanceOf(IllegalArgumentException.class);
                    }
                };
        DeletionRequest request =
                new DeletionRequest.Builder()
                        .setOriginUris(Collections.singletonList(ORIGIN_URI))
                        .setDomainUris(Collections.singletonList(DOMAIN_URI))
                        .setStart(Instant.now().plusMillis(1000))
                        .setEnd(Instant.now())
                        .build();

        manager.deleteRegistrations(request, mExecutorService, callback);

        assertThat(future.get()).isNull();
    }

    @Test
    @RequiresSdkLevelAtLeastS
    public void testMeasurementApiStatus_killSwitchGlobalOffWithOsReceiver_returnEnabled()
            throws Exception {
        enableGlobalKillSwitch(/* enabled= */ false);
        enableMeasurementKillSwitch(/* enabled= */ false);
        allowAllPackageNamesAccessToMeasurementApis();
        boolean result = callMeasurementApiStatus(/* useCustomReceiver= */ false);
        assertThat(result).isTrue();
    }

    @Test
    public void testMeasurementApiStatus_killSwitchGlobalOffWithCustomReceiver_returnEnabled()
            throws Exception {
        enableGlobalKillSwitch(/* enabled= */ false);
        enableMeasurementKillSwitch(/* enabled= */ false);
        allowAllPackageNamesAccessToMeasurementApis();
        boolean result = callMeasurementApiStatus(/* useCustomReceiver= */ true);
        assertThat(result).isTrue();
    }

    @Test
    @RequiresSdkLevelAtLeastS
    public void testMeasurementApiStatus_killSwitchGlobalOnWithOsReceiver_returnDisabled()
            throws Exception {
        enableGlobalKillSwitch(/* enabled= */ true);
        boolean result = callMeasurementApiStatus(/* useCustomReceiver= */ false);
        assertThat(result).isFalse();
    }

    @Test
    public void testMeasurementApiStatus_killSwitchGlobalOnWithCustomReceiver_returnDisabled()
            throws Exception {
        enableGlobalKillSwitch(/* enabled= */ true);
        boolean result = callMeasurementApiStatus(/* useCustomReceiver= */ true);
        assertThat(result).isFalse();
    }

    @Test
    @RequiresSdkLevelAtLeastS
    public void testMeasurementApiStatus_killSwitchMeasurementOnWithOsReceiver_returnDisabled()
            throws Exception {
        enableMeasurementKillSwitch(/* enabled= */ true);
        boolean result = callMeasurementApiStatus(/* useCustomReceiver= */ false);
        assertThat(result).isFalse();
    }

    @Test
    public void testMeasurementApiStatus_killSwitchMeasurementOnWithCustomReceiver_returnDisabled()
            throws Exception {
        enableMeasurementKillSwitch(/* enabled= */ true);
        boolean result = callMeasurementApiStatus(/* useCustomReceiver= */ true);
        assertThat(result).isFalse();
    }

    @Test
    @RequiresSdkLevelAtLeastS
    public void testMeasurementApiStatus_notInAllowListWithOsReceiver_returnDisabled()
            throws Exception {
        enableGlobalKillSwitch(/* enabled= */ true);
        blockAllPackageNamesAccessToMeasurementApis();
        boolean result = callMeasurementApiStatus(/* useCustomReceiver= */ false);
        assertThat(result).isFalse();
    }

    @Test
    public void testMeasurementApiStatus_notInAllowListWithCustomReceiver_returnDisabled()
            throws Exception {
        enableGlobalKillSwitch(/* enabled= */ true);
        blockAllPackageNamesAccessToMeasurementApis();
        boolean result = callMeasurementApiStatus(/* useCustomReceiver= */ true);
        assertThat(result).isFalse();
    }

    /**
     * Performs calls to measurement status API and returns a boolean representing if the API was
     * enabled {@code true} or disabled {@code false}.
     *
     * @return api status
     */
    private boolean callMeasurementApiStatus(boolean useCustomReceiver) throws Exception {
        CountDownLatch countDownLatch = new CountDownLatch(1);
        MeasurementManager manager = MeasurementManager.get(mContext);
        List<Integer> resultCodes = new ArrayList<>();

        if (useCustomReceiver) {
            manager.getMeasurementApiStatus(
                    mExecutorService,
                    (AdServicesOutcomeReceiver<Integer, Exception>)
                            result -> {
                                resultCodes.add(result);
                                countDownLatch.countDown();
                            });
        } else {
            manager.getMeasurementApiStatus(
                    mExecutorService,
                    (OutcomeReceiver<Integer, Exception>)
                            result -> {
                                resultCodes.add(result);
                                countDownLatch.countDown();
                            });
        }

        assertThat(countDownLatch.await(500, TimeUnit.MILLISECONDS)).isTrue();
        assertThat(resultCodes).isNotNull();
        assertThat(resultCodes).hasSize(1);
        return resultCodes.get(0) == MeasurementManager.MEASUREMENT_API_STATE_ENABLED;
    }

    private void allowAllPackageNamesAccessToMeasurementApis() {
        String packageName = FlagsConstants.ALLOWLIST_ALL;
        flags.setMsmtApiAppAllowList(packageName).setMsmtWebContextClientAllowList(packageName);
    }

    private void blockAllPackageNamesAccessToMeasurementApis() {
        String packageName = FlagsConstants.ALLOWLIST_NONE;
        flags.setMsmtApiAppAllowList(packageName).setMsmtWebContextClientAllowList(packageName);
    }

    // TODO(b/346825347) Inline this method when the bug is fixed
    private void enableGlobalKillSwitch(boolean enabled) {
        if (SdkLevel.isAtLeastT()) {
            flags.setGlobalKillSwitch(enabled);
        } else {
            flags.setEnableBackCompat(!enabled);
        }
    }

    private void enableMeasurementKillSwitch(boolean enabled) {
        flags.setFlag(KEY_MEASUREMENT_KILL_SWITCH, enabled);
    }

    private boolean registerSourceAndVerifyRateLimitReached(
            MeasurementManager manager, boolean useCustomReceiver) throws InterruptedException {
        AtomicBoolean reachedLimit = new AtomicBoolean(false);
        CountDownLatch countDownLatch = new CountDownLatch(1);

        if (useCustomReceiver) {
            manager.registerSource(
                    SOURCE_REGISTRATION_URI,
                    null,
                    CALLBACK_EXECUTOR,
                    createCallbackWithCountdownOnLimitExceeded(countDownLatch, reachedLimit));
        } else {
            OutcomeReceiver<Object, Exception> osCallback =
                    new OutcomeReceiver<>() {
                        @Override
                        public void onResult(@NonNull Object result) {
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
            manager.registerSource(SOURCE_REGISTRATION_URI, null, CALLBACK_EXECUTOR, osCallback);
        }

        countDownLatch.await();
        return reachedLimit.get();
    }

    private boolean registerWebSourceAndVerifyRateLimitReached(
            MeasurementManager manager, boolean useCustomReceiver) throws InterruptedException {
        AtomicBoolean reachedLimit = new AtomicBoolean(false);
        CountDownLatch countDownLatch = new CountDownLatch(1);

        WebSourceParams webSourceParams =
                new WebSourceParams.Builder(SOURCE_REGISTRATION_URI)
                        .setDebugKeyAllowed(false)
                        .build();

        WebSourceRegistrationRequest webSourceRegistrationRequest =
                new WebSourceRegistrationRequest.Builder(
                                Collections.singletonList(webSourceParams), SOURCE_REGISTRATION_URI)
                        .setInputEvent(null)
                        .setAppDestination(OS_DESTINATION)
                        .setWebDestination(WEB_DESTINATION)
                        .setVerifiedDestination(null)
                        .build();

        if (useCustomReceiver) {
            manager.registerWebSource(
                    webSourceRegistrationRequest,
                    CALLBACK_EXECUTOR,
                    createCallbackWithCountdownOnLimitExceeded(countDownLatch, reachedLimit));
        } else {
            OutcomeReceiver<Object, Exception> osCallback =
                    new OutcomeReceiver<>() {
                        @Override
                        public void onResult(@NonNull Object result) {
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
            manager.registerWebSource(webSourceRegistrationRequest, CALLBACK_EXECUTOR, osCallback);
        }

        countDownLatch.await();
        return reachedLimit.get();
    }

    private boolean registerTriggerAndVerifyRateLimitReached(
            MeasurementManager manager, boolean useCustomReceiver) throws InterruptedException {
        AtomicBoolean reachedLimit = new AtomicBoolean(false);
        CountDownLatch countDownLatch = new CountDownLatch(1);

        if (useCustomReceiver) {
            manager.registerTrigger(
                    TRIGGER_REGISTRATION_URI,
                    CALLBACK_EXECUTOR,
                    createCallbackWithCountdownOnLimitExceeded(countDownLatch, reachedLimit));
        } else {
            OutcomeReceiver<Object, Exception> osCallback =
                    new OutcomeReceiver<>() {
                        @Override
                        public void onResult(@NonNull Object result) {
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
            manager.registerTrigger(TRIGGER_REGISTRATION_URI, CALLBACK_EXECUTOR, osCallback);
        }

        countDownLatch.await();
        return reachedLimit.get();
    }

    private boolean registerWebTriggerAndVerifyRateLimitReached(
            MeasurementManager manager, boolean useCustomReceiver) throws InterruptedException {
        AtomicBoolean reachedLimit = new AtomicBoolean(false);
        CountDownLatch countDownLatch = new CountDownLatch(1);

        WebTriggerParams webTriggerParams =
                new WebTriggerParams.Builder(TRIGGER_REGISTRATION_URI).build();
        WebTriggerRegistrationRequest webTriggerRegistrationRequest =
                new WebTriggerRegistrationRequest.Builder(
                                Collections.singletonList(webTriggerParams), DESTINATION)
                        .build();

        if (useCustomReceiver) {
            manager.registerWebTrigger(
                    webTriggerRegistrationRequest,
                    CALLBACK_EXECUTOR,
                    createCallbackWithCountdownOnLimitExceeded(countDownLatch, reachedLimit));
        } else {
            OutcomeReceiver<Object, Exception> osCallback =
                    new OutcomeReceiver<>() {
                        @Override
                        public void onResult(@NonNull Object result) {
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
            manager.registerWebTrigger(
                    webTriggerRegistrationRequest, CALLBACK_EXECUTOR, osCallback);
        }

        countDownLatch.await();
        return reachedLimit.get();
    }

    private boolean registerSourceMultipleAndVerifyRateLimitReached(
            MeasurementManager manager, boolean useCustomReceiver) throws InterruptedException {
        AtomicBoolean reachedLimit = new AtomicBoolean(false);
        CountDownLatch countDownLatch = new CountDownLatch(1);

        if (useCustomReceiver) {
            manager.registerSource(
                    createSourceRegistrationRequest(),
                    CALLBACK_EXECUTOR,
                    createCallbackWithCountdownOnLimitExceeded(countDownLatch, reachedLimit));
        } else {
            OutcomeReceiver<Object, Exception> osCallback =
                    new OutcomeReceiver<>() {
                        @Override
                        public void onResult(@NonNull Object result) {
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
            manager.registerSource(SOURCE_REGISTRATION_URI, null, CALLBACK_EXECUTOR, osCallback);
        }

        countDownLatch.await();
        return reachedLimit.get();
    }

    private SourceRegistrationRequest createSourceRegistrationRequest() {
        return new SourceRegistrationRequest.Builder(
                        Collections.singletonList(SOURCE_REGISTRATION_URI))
                .setInputEvent(INPUT_EVENT)
                .build();
    }

    private AdServicesOutcomeReceiver<Object, Exception> createCallbackWithCountdownOnLimitExceeded(
            CountDownLatch countDownLatch, AtomicBoolean reachedLimit) {
        return new AdServicesOutcomeReceiver<>() {
            @Override
            public void onResult(@NonNull Object result) {
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

    private float getRequestPerSecond(String flagName) {
        try {
            String permitString = SystemProperties.get("debug.adservices." + flagName);
            if (!TextUtils.isEmpty(permitString) && !"null".equalsIgnoreCase(permitString)) {
                return Float.parseFloat(permitString);
            }

            permitString = ShellUtils.runShellCommand("device_config get adservices " + flagName);
            if (!TextUtils.isEmpty(permitString) && !"null".equalsIgnoreCase(permitString)) {
                return Float.parseFloat(permitString);
            }
            return DEFAULT_REQUEST_PER_SECOND;
        } catch (Exception e) {
            return DEFAULT_REQUEST_PER_SECOND;
        }
    }
}

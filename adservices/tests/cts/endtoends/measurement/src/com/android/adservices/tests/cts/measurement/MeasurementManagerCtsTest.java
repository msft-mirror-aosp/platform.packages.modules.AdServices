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

import static com.google.common.truth.Truth.assertThat;

import static org.junit.Assert.assertNull;
import static org.junit.Assert.assertTrue;
import static org.junit.Assert.fail;

import android.adservices.clients.measurement.MeasurementClient;
import android.adservices.measurement.DeletionRequest;
import android.adservices.measurement.MeasurementManager;
import android.adservices.measurement.WebSourceParams;
import android.adservices.measurement.WebSourceRegistrationRequest;
import android.adservices.measurement.WebTriggerParams;
import android.adservices.measurement.WebTriggerRegistrationRequest;
import android.content.Context;
import android.net.Uri;
import android.os.OutcomeReceiver;
import android.test.suitebuilder.annotation.SmallTest;

import androidx.annotation.NonNull;
import androidx.test.core.app.ApplicationProvider;
import androidx.test.runner.AndroidJUnit4;

import com.android.compatibility.common.util.ShellUtils;

import com.google.common.util.concurrent.ListenableFuture;

import org.junit.After;
import org.junit.Assert;
import org.junit.Before;
import org.junit.Ignore;
import org.junit.Test;
import org.junit.runner.RunWith;

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

@SmallTest
@RunWith(AndroidJUnit4.class)
public class MeasurementManagerCtsTest {
    private MeasurementClient mMeasurementClient;
    private static final Executor CALLBACK_EXECUTOR = Executors.newCachedThreadPool();

    /* Note: The source and trigger registration used here must match one of those in
       {@link PreEnrolledAdTechForTest}.
    */
    private static final Uri SOURCE_REGISTRATION_URI = Uri.parse("https://test.com/source");
    private static final Uri TRIGGER_REGISTRATION_URI = Uri.parse("https://test.com/trigger");
    private static final Uri DESTINATION = Uri.parse("http://trigger-origin.com");
    private static final Uri OS_DESTINATION = Uri.parse("android-app://com.os.destination");
    private static final Uri WEB_DESTINATION = Uri.parse("http://web-destination.com");
    private static final Uri ORIGIN_URI = Uri.parse("https://sample.example1.com");
    private static final Uri DOMAIN_URI = Uri.parse("https://example2.com");
    private final ExecutorService mExecutorService = Executors.newCachedThreadPool();

    protected static final Context sContext = ApplicationProvider.getApplicationContext();

    @Before
    public void setup() {
        // To grant access to all web context
        ShellUtils.runShellCommand("device_config put adservices web_context_client_allow_list *");

        // To grant access to all pp api app
        ShellUtils.runShellCommand("device_config put adservices ppapi_app_allow_list *");

        mMeasurementClient =
                new MeasurementClient.Builder()
                        .setContext(sContext)
                        .setExecutor(CALLBACK_EXECUTOR)
                        .build();
    }

    @After
    public void tearDown() throws Exception {
        TimeUnit.SECONDS.sleep(1);
    }

    @Test
    public void testRegisterSource_withCallbackButNoServerSetup_NoErrors() throws Exception {
        overrideDisableMeasurementEnrollmentCheck("1");
        ListenableFuture<Void> result =
                mMeasurementClient.registerSource(SOURCE_REGISTRATION_URI, /* inputEvent = */ null);
        assertThat(result.get()).isNull();
        overrideDisableMeasurementEnrollmentCheck("0");
    }

    @Test
    public void testRegisterTrigger_withCallbackButNoServerSetup_NoErrors() throws Exception {
        overrideDisableMeasurementEnrollmentCheck("1");
        ListenableFuture<Void> result =
                mMeasurementClient.registerTrigger(TRIGGER_REGISTRATION_URI);
        assertThat(result.get()).isNull();
        overrideDisableMeasurementEnrollmentCheck("0");
    }

    @Test
    public void registerWebSource_withCallback_NoErrors() throws Exception {
        overrideDisableMeasurementEnrollmentCheck("1");
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

        ListenableFuture<Void> result =
                mMeasurementClient.registerWebSource(webSourceRegistrationRequest);
        assertThat(result.get()).isNull();
        overrideDisableMeasurementEnrollmentCheck("0");
    }

    @Test
    public void registerWebTrigger_withCallback_NoErrors() throws Exception {
        overrideDisableMeasurementEnrollmentCheck("1");
        WebTriggerParams webTriggerParams =
                new WebTriggerParams.Builder(TRIGGER_REGISTRATION_URI).build();
        WebTriggerRegistrationRequest webTriggerRegistrationRequest =
                new WebTriggerRegistrationRequest.Builder(
                                Collections.singletonList(webTriggerParams), DESTINATION)
                        .build();

        ListenableFuture<Void> result =
                mMeasurementClient.registerWebTrigger(webTriggerRegistrationRequest);
        assertThat(result.get()).isNull();
        overrideDisableMeasurementEnrollmentCheck("0");
    }

    @Ignore("b/243204209")
    @Test
    public void testDeleteRegistrations_withRequest_withNoOrigin_withNoRange_withCallback_NoErrors()
            throws Exception {
        overrideDisableMeasurementEnrollmentCheck("1");
        DeletionRequest deletionRequest = new DeletionRequest.Builder().build();
        ListenableFuture<Void> result = mMeasurementClient.deleteRegistrations(deletionRequest);
        assertNull(result.get());
        overrideDisableMeasurementEnrollmentCheck("0");
    }

    @Ignore("b/243204209")
    @Test
    public void testDeleteRegistrations_withRequest_withNoRange_withCallback_NoErrors()
            throws Exception {
        overrideDisableMeasurementEnrollmentCheck("1");
        DeletionRequest deletionRequest =
                new DeletionRequest.Builder()
                        .setOriginUris(Collections.singletonList(ORIGIN_URI))
                        .setDomainUris(Collections.singletonList(DOMAIN_URI))
                        .build();
        ListenableFuture<Void> result = mMeasurementClient.deleteRegistrations(deletionRequest);
        assertNull(result.get());
        overrideDisableMeasurementEnrollmentCheck("0");
    }

    @Test
    public void testDeleteRegistrations_withRequest_withEmptyLists_withRange_withCallback_NoErrors()
            throws Exception {
        overrideDisableMeasurementEnrollmentCheck("1");
        DeletionRequest deletionRequest =
                new DeletionRequest.Builder()
                        .setOriginUris(Collections.emptyList())
                        .setDomainUris(Collections.emptyList())
                        .setStart(Instant.ofEpochMilli(0))
                        .setEnd(Instant.now())
                        .build();
        ListenableFuture<Void> result = mMeasurementClient.deleteRegistrations(deletionRequest);
        assertNull(result.get());
        overrideDisableMeasurementEnrollmentCheck("0");
    }

    @Test
    public void testDeleteRegistrations_withRequest_withUris_withRange_withCallback_NoErrors()
            throws Exception {
        overrideDisableMeasurementEnrollmentCheck("1");
        DeletionRequest deletionRequest =
                new DeletionRequest.Builder()
                        .setOriginUris(Collections.singletonList(ORIGIN_URI))
                        .setDomainUris(Collections.singletonList(DOMAIN_URI))
                        .setStart(Instant.ofEpochMilli(0))
                        .setEnd(Instant.now())
                        .build();
        ListenableFuture<Void> result = mMeasurementClient.deleteRegistrations(deletionRequest);
        assertNull(result.get());
        overrideDisableMeasurementEnrollmentCheck("0");
    }

    @Ignore
    @Test
    public void testDeleteRegistrations_withRequest_withInvalidArguments_withCallback_hasError()
            throws Exception {
        overrideDisableMeasurementEnrollmentCheck("1");
        final MeasurementManager manager = sContext.getSystemService(MeasurementManager.class);
        Objects.requireNonNull(manager);

        CompletableFuture<Void> future = new CompletableFuture<>();
        OutcomeReceiver<Object, Exception> callback =
                new OutcomeReceiver<Object, Exception>() {
                    @Override
                    public void onResult(@NonNull Object ignoredResult) {
                        fail();
                    }

                    @Override
                    public void onError(Exception error) {
                        future.complete(null);
                        assertTrue(error instanceof IllegalArgumentException);
                    }
                };
        DeletionRequest request =
                new DeletionRequest.Builder()
                        .setOriginUris(Collections.singletonList(ORIGIN_URI))
                        .setDomainUris(Collections.singletonList(DOMAIN_URI))
                        .setEnd(Instant.now())
                        .build();

        manager.deleteRegistrations(request, mExecutorService, callback);

        Assert.assertNull(future.get());
        overrideDisableMeasurementEnrollmentCheck("0");
    }

    @Test
    public void testMeasurementApiStatus_returnResultStatus() throws Exception {
        overrideDisableMeasurementEnrollmentCheck("1");
        CountDownLatch countDownLatch = new CountDownLatch(1);
        final MeasurementManager manager = sContext.getSystemService(MeasurementManager.class);
        List<Integer> resultCodes = new ArrayList<>();

        manager.getMeasurementApiStatus(
                mExecutorService,
                result -> {
                    resultCodes.add(result);
                    countDownLatch.countDown();
                });

        assertThat(countDownLatch.await(500, TimeUnit.MILLISECONDS)).isTrue();
        Assert.assertNotNull(resultCodes);
        Assert.assertEquals(1, resultCodes.size());
        overrideDisableMeasurementEnrollmentCheck("0");
    }

    // Override the flag to disable Measurement enrollment check. Setting to 1 disables enforcement.
    private void overrideDisableMeasurementEnrollmentCheck(String val) {
        ShellUtils.runShellCommand(
                "setprop debug.adservices.disable_measurement_enrollment_check " + val);
    }
}

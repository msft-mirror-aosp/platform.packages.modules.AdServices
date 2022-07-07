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

package android.adservices.cts;

import static com.google.common.truth.Truth.assertThat;

import android.adservices.clients.measurement.MeasurementClient;
import android.adservices.exceptions.MeasurementException;
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
import androidx.test.platform.app.InstrumentationRegistry;
import androidx.test.runner.AndroidJUnit4;

import com.google.common.util.concurrent.ListenableFuture;

import org.junit.Assert;
import org.junit.Before;
import org.junit.Test;
import org.junit.runner.RunWith;

import java.time.Instant;
import java.util.Collections;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.Executor;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;

@SmallTest
@RunWith(AndroidJUnit4.class)
public class CtsMeasurementManagerTest {

    private MeasurementClient mMeasurementClient;
    private static final Executor CALLBACK_EXECUTOR = Executors.newCachedThreadPool();

    private static final String INVALID_SERVER_ADDRESS = "http://example.com";
    private static final Uri SOURCE_ORIGIN = Uri.parse("http://source-origin.com");
    private static final Uri DESTINATION = Uri.parse("http://trigger-origin.com");
    private static final Uri OS_DESTINATION = Uri.parse("android-app://com.os.destination");
    private static final Uri WEB_DESTINATION = Uri.parse("http://web-destination.com");
    private static final String ORIGIN_PACKAGE = "android-app://com.site.toBeDeleted";
    private final ExecutorService mExecutorService = Executors.newCachedThreadPool();

    protected static final Context sContext = ApplicationProvider.getApplicationContext();

    @Before
    public void setup() {
        mMeasurementClient =
                new MeasurementClient.Builder()
                        .setContext(sContext)
                        .setExecutor(CALLBACK_EXECUTOR)
                        .build();
    }

    @Test
    public void testRegisterSource_withCallbackButNoServerSetup_NoErrors() throws Exception {
        ListenableFuture<Void> result =
                mMeasurementClient.registerSource(
                        Uri.parse(INVALID_SERVER_ADDRESS), /* inputEvent = */ null);
        assertThat(result.get()).isNull();
    }

    @Test
    public void testRegisterTrigger_withCallbackButNoServerSetup_NoErrors() throws Exception {
        ListenableFuture<Void> result =
                mMeasurementClient.registerTrigger(Uri.parse(INVALID_SERVER_ADDRESS));
        assertThat(result.get()).isNull();
    }

    @Test
    public void registerWebSource_withCallback_NoErrors() throws Exception {
        WebSourceParams webSourceParams =
                new WebSourceParams.Builder()
                        .setRegistrationUri(Uri.parse(INVALID_SERVER_ADDRESS))
                        .setAllowDebugKey(false)
                        .build();

        WebSourceRegistrationRequest webSourceRegistrationRequest =
                new WebSourceRegistrationRequest.Builder()
                        .setSourceParams(Collections.singletonList(webSourceParams))
                        .setTopOriginUri(SOURCE_ORIGIN)
                        .setInputEvent(null)
                        .setOsDestination(OS_DESTINATION)
                        .setWebDestination(WEB_DESTINATION)
                        .setVerifiedDestination(null)
                        .build();

        ListenableFuture<Void> result =
                mMeasurementClient.registerWebSource(webSourceRegistrationRequest);
        assertThat(result.get()).isNull();
    }

    @Test
    public void registerWebTrigger_withCallback_NoErrors() throws Exception {
        WebTriggerParams webTriggerParams =
                new WebTriggerParams.Builder()
                        .setRegistrationUri(Uri.parse(INVALID_SERVER_ADDRESS))
                        .setAllowDebugKey(false)
                        .build();
        WebTriggerRegistrationRequest webTriggerRegistrationRequest =
                new WebTriggerRegistrationRequest.Builder()
                        .setTriggerParams(Collections.singletonList(webTriggerParams))
                        .setDestination(DESTINATION)
                        .build();

        ListenableFuture<Void> result =
                mMeasurementClient.registerWebTrigger(webTriggerRegistrationRequest);
        assertThat(result.get()).isNull();
    }

    @Test
    public void testDeleteRegistrations_withRequest_withNoOrigin_withNoRange_withCallback_NoErrors()
            throws Exception {
        DeletionRequest deletionRequest = new DeletionRequest.Builder().build();
        ListenableFuture<Void> result = mMeasurementClient.deleteRegistrations(deletionRequest);
        assertThat(result.get()).isNull();
    }

    @Test
    public void testDeleteRegistrations_withRequest_withOrigin_withNoRange_withCallback_NoErrors()
            throws Exception {
        DeletionRequest deletionRequest =
                new DeletionRequest.Builder().setOriginUri(Uri.parse(ORIGIN_PACKAGE)).build();
        ListenableFuture<Void> result = mMeasurementClient.deleteRegistrations(deletionRequest);
        assertThat(result.get()).isNull();
    }

    @Test
    public void testDeleteRegistrations_withRequest_withNoOrigin_withRange_withCallback_NoErrors()
            throws Exception {
        DeletionRequest deletionRequest =
                new DeletionRequest.Builder()
                        .setStart(Instant.ofEpochMilli(0))
                        .setEnd(Instant.now())
                        .build();
        ListenableFuture<Void> result = mMeasurementClient.deleteRegistrations(deletionRequest);
        assertThat(result.get()).isNull();
    }

    @Test
    public void testDeleteRegistrations_withRequest_withOrigin_withRange_withCallback_NoErrors()
            throws Exception {
        DeletionRequest deletionRequest =
                new DeletionRequest.Builder()
                        .setOriginUri(Uri.parse(ORIGIN_PACKAGE))
                        .setStart(Instant.ofEpochMilli(0))
                        .setEnd(Instant.now())
                        .build();
        ListenableFuture<Void> result = mMeasurementClient.deleteRegistrations(deletionRequest);
        assertThat(result.get()).isNull();
    }

    @Test
    public void testDeleteRegistrations_withRequest_withInvalidArguments_withCallback_hasError()
            throws Exception {
        final Context context = InstrumentationRegistry.getInstrumentation().getTargetContext();
        final MeasurementManager manager = context.getSystemService(MeasurementManager.class);

        CompletableFuture<Void> future = new CompletableFuture<>();
        OutcomeReceiver<Void, Exception> callback =
                new OutcomeReceiver<Void, Exception>() {
                    @Override
                    public void onResult(@NonNull Void result) {
                        Assert.fail();
                    }

                    @Override
                    public void onError(Exception error) {
                        future.complete(null);
                        Assert.assertEquals(
                                ((MeasurementException) error).getResultCode(),
                                MeasurementManager.RESULT_INVALID_ARGUMENT);
                    }
                };
        DeletionRequest request = new DeletionRequest.Builder().setEnd(Instant.now()).build();
        manager.deleteRegistrations(request, mExecutorService, callback);
        Assert.assertNull(future.get());
    }
}

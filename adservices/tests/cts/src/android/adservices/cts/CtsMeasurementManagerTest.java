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

import android.adservices.exceptions.AdServicesException;
import android.adservices.measurement.MeasurementApiUtil;
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
import androidx.test.platform.app.InstrumentationRegistry;
import androidx.test.runner.AndroidJUnit4;

import org.junit.Assert;
import org.junit.Test;
import org.junit.runner.RunWith;

import java.util.Collections;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;

@SmallTest
@RunWith(AndroidJUnit4.class)
public class CtsMeasurementManagerTest {

    private static final String INVALID_SERVER_ADDRESS = "http://example.com";
    private static final Uri SOURCE_ORIGIN = Uri.parse("http://source-origin.com");
    private static final Uri DESTINATION = Uri.parse("http://trigger-origin.com");
    private static final Uri OS_DESTINATION = Uri.parse("android-app://com.os.destination");
    private static final Uri WEB_DESTINATION = Uri.parse("http://web-destination.com");
    private final ExecutorService mExecutorService = Executors.newCachedThreadPool();

    @Test
    public void testRegisterSource_withCallbackButNoServerSetup_NoErrors() throws Exception {
        final Context context = InstrumentationRegistry.getInstrumentation().getTargetContext();
        final MeasurementManager manager = context.getSystemService(MeasurementManager.class);

        CompletableFuture<Void> future = new CompletableFuture<>();
        OutcomeReceiver<Void, AdServicesException> callback =
                new OutcomeReceiver<Void, AdServicesException>() {
            @Override
            public void onResult(@NonNull Void result) {
                future.complete(result);
            }

            @Override
            public void onError(AdServicesException error) {
                Assert.fail();
            }
        };
        manager.registerSource(
                /* attributionSource = */ Uri.parse(INVALID_SERVER_ADDRESS),
                /* inputEvent = */ null,
                /* executor = */ mExecutorService,
                /* callback = */ callback
        );

        Assert.assertNull(future.get());
    }

    @Test
    public void testRegisterTrigger_withCallbackButNoServerSetup_NoErrors() throws Exception {
        final Context context = InstrumentationRegistry.getInstrumentation().getTargetContext();
        final MeasurementManager manager = context.getSystemService(MeasurementManager.class);

        CompletableFuture<Void> future = new CompletableFuture<>();
        OutcomeReceiver<Void, AdServicesException> callback =
                new OutcomeReceiver<Void, AdServicesException>() {
                    @Override
                    public void onResult(@NonNull Void result) {
                        future.complete(result);
                    }

                    @Override
                    public void onError(AdServicesException error) {
                        Assert.fail();
                    }
                };
        manager.registerTrigger(
                /* trigger = */ Uri.parse(INVALID_SERVER_ADDRESS),
                /* executor = */ mExecutorService,
                /* callback = */ callback
        );

        Assert.assertNull(future.get());
    }

    @Test
    public void testGetMeasurementApiStatus_NoErrors() throws Exception {
        final Context context = InstrumentationRegistry.getInstrumentation().getTargetContext();
        final MeasurementManager manager = context.getSystemService(MeasurementManager.class);

        CompletableFuture<Integer> future = new CompletableFuture<>();
        OutcomeReceiver<Integer, AdServicesException> callback =
                new OutcomeReceiver<Integer, AdServicesException>() {
                    @Override
                    public void onResult(@NonNull Integer result) {
                        future.complete(result);
                    }

                    @Override
                    public void onError(AdServicesException error) {
                        Assert.fail();
                    }
                };
        manager.getMeasurementApiStatus(
                /* executor = */ mExecutorService,
                /* callback = */ callback
        );

        Assert.assertEquals(Integer.valueOf(
                MeasurementApiUtil.MEASUREMENT_API_STATE_ENABLED), future.get());
    }

    @Test
    public void registerWebSource_withCallback_NoErrors() throws Exception {
        final Context context = InstrumentationRegistry.getInstrumentation().getTargetContext();
        final MeasurementManager manager = context.getSystemService(MeasurementManager.class);

        WebSourceParams webSourceParams =
                new WebSourceParams.Builder()
                        .setRegistrationUri(Uri.parse(INVALID_SERVER_ADDRESS))
                        .setAllowDebugKey(false)
                        .build();
        CompletableFuture<Void> future = new CompletableFuture<>();
        OutcomeReceiver<Void, Exception> callback =
                new OutcomeReceiver<Void, Exception>() {
                    @Override
                    public void onResult(@NonNull Void result) {
                        future.complete(result);
                    }

                    @Override
                    public void onError(Exception error) {
                        Assert.fail();
                    }
                };

        manager.registerWebSource(
                new WebSourceRegistrationRequest.Builder()
                        .setSourceParams(Collections.singletonList(webSourceParams))
                        .setTopOriginUri(SOURCE_ORIGIN)
                        .setInputEvent(null)
                        .setOsDestination(OS_DESTINATION)
                        .setWebDestination(WEB_DESTINATION)
                        .setVerifiedDestination(null)
                        .build(),
                mExecutorService,
                callback);

        Assert.assertNull(future.get());
    }

    @Test
    public void registerWebSource_withSourceOsDestinationNoCallback_NoErrors() {
        final Context context = InstrumentationRegistry.getInstrumentation().getTargetContext();
        final MeasurementManager manager = context.getSystemService(MeasurementManager.class);
        WebSourceParams webSourceParams =
                new WebSourceParams.Builder()
                        .setRegistrationUri(Uri.parse(INVALID_SERVER_ADDRESS))
                        .setAllowDebugKey(false)
                        .build();
        manager.registerWebSource(
                new WebSourceRegistrationRequest.Builder()
                        .setSourceParams(Collections.singletonList(webSourceParams))
                        .setTopOriginUri(SOURCE_ORIGIN)
                        .setInputEvent(null)
                        .setOsDestination(OS_DESTINATION)
                        .setWebDestination(WEB_DESTINATION)
                        .setVerifiedDestination(null)
                        .build(),
                /* executor */ null,
                /* callback */ null);
    }

    @Test
    public void registerWebTrigger_withCallback_NoErrors() throws Exception {
        final Context context = InstrumentationRegistry.getInstrumentation().getTargetContext();
        final MeasurementManager manager = context.getSystemService(MeasurementManager.class);

        WebTriggerParams webTriggerParams =
                new WebTriggerParams.Builder()
                        .setRegistrationUri(Uri.parse(INVALID_SERVER_ADDRESS))
                        .setAllowDebugKey(false)
                        .build();
        CompletableFuture<Void> future = new CompletableFuture<>();
        OutcomeReceiver<Void, Exception> callback =
                new OutcomeReceiver<Void, Exception>() {
                    @Override
                    public void onResult(@NonNull Void result) {
                        future.complete(result);
                    }

                    @Override
                    public void onError(Exception error) {
                        Assert.fail();
                    }
                };
        manager.registerWebTrigger(
                new WebTriggerRegistrationRequest.Builder()
                        .setTriggerParams(Collections.singletonList(webTriggerParams))
                        .setDestination(DESTINATION)
                        .build(),
                mExecutorService,
                callback);

        Assert.assertNull(future.get());
    }

    @Test
    public void registerWebTrigger_withNoCallback_NoErrors() {
        final Context context = InstrumentationRegistry.getInstrumentation().getTargetContext();
        final MeasurementManager manager = context.getSystemService(MeasurementManager.class);

        WebTriggerParams webTriggerParams =
                new WebTriggerParams.Builder()
                        .setRegistrationUri(Uri.parse(INVALID_SERVER_ADDRESS))
                        .setAllowDebugKey(false)
                        .build();
        manager.registerWebTrigger(
                new WebTriggerRegistrationRequest.Builder()
                        .setTriggerParams(Collections.singletonList(webTriggerParams))
                        .setDestination(DESTINATION)
                        .build(),
                mExecutorService,
                /* callback */ null);
    }
}

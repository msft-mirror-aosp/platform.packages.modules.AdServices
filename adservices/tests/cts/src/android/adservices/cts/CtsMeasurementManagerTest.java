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
import android.adservices.measurement.MeasurementManager;
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

import java.util.concurrent.CompletableFuture;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;

@SmallTest
@RunWith(AndroidJUnit4.class)
public class CtsMeasurementManagerTest {

    private static final String INVALID_SERVER_ADDRESS = "http://example.com";
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
    public void testRegisterSource_withNoCallback_NoErrorsThrown() throws Exception {
        final Context context = InstrumentationRegistry.getInstrumentation().getTargetContext();
        final MeasurementManager manager = context.getSystemService(MeasurementManager.class);
        manager.registerSource(
                /* attributionSource = */ Uri.parse(INVALID_SERVER_ADDRESS),
                /* inputEvent = */ null
        );
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
    public void testRegisterTrigger_withNoCallback_NoErrorsThrown() throws Exception {
        final Context context = InstrumentationRegistry.getInstrumentation().getTargetContext();
        final MeasurementManager manager = context.getSystemService(MeasurementManager.class);
        manager.registerTrigger(
                /* trigger = */ Uri.parse(INVALID_SERVER_ADDRESS)
        );
    }
}

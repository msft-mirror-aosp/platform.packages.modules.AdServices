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
package com.android.adservices.measurement;

import static org.junit.Assert.assertNull;

import android.adservices.measurement.DeletionParam;
import android.adservices.measurement.DeletionRequest;
import android.adservices.measurement.IMeasurementService;
import android.adservices.measurement.MeasurementManager;
import android.adservices.measurement.RegistrationRequest;
import android.adservices.measurement.WebSourceParams;
import android.adservices.measurement.WebSourceRegistrationRequest;
import android.adservices.measurement.WebSourceRegistrationRequestInternal;
import android.adservices.measurement.WebTriggerParams;
import android.adservices.measurement.WebTriggerRegistrationRequest;
import android.adservices.measurement.WebTriggerRegistrationRequestInternal;
import android.app.sdksandbox.SandboxedSdkContext;
import android.content.Context;
import android.content.pm.ApplicationInfo;
import android.net.Uri;
import android.os.OutcomeReceiver;
import android.util.Log;

import androidx.test.core.app.ApplicationProvider;

import com.android.compatibility.common.util.ShellUtils;

import org.junit.Assert;
import org.junit.Test;
import org.mockito.ArgumentCaptor;
import org.mockito.ArgumentMatchers;
import org.mockito.Mockito;

import java.time.Instant;
import java.util.Collections;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.Executor;
import java.util.concurrent.Executors;

public class MeasurementManagerTest {
    private static final String TAG = "MeasurementManagerTest";
    private static final String SERVICE_APK_NAME = "com.android.adservices.api";
    private static final String CLIENT_PACKAGE_NAME = "com.android.adservices.endtoendtest";

    protected static final Context sContext = ApplicationProvider.getApplicationContext();
    private static final Executor CALLBACK_EXECUTOR = Executors.newCachedThreadPool();
    private static final SandboxedSdkContext sSandboxedSdkContext =
            new SandboxedSdkContext(
                    sContext,
                    CLIENT_PACKAGE_NAME,
                    new ApplicationInfo(),
                    "sdkName",
                    /* sdkCeDataDir = */ null,
                    /* sdkDeDataDir = */ null);

    private void measureRegisterAttributionSource(
            MeasurementManager mm, String label) throws Exception {
        Log.i(TAG, "Calling registerSource()");
        final long start = System.currentTimeMillis();

        CompletableFuture<Void> future = new CompletableFuture<>();
        mm.registerSource(
                Uri.parse("https://example.com"), null,
                CALLBACK_EXECUTOR, future::complete);
        assertNull(future.get());

        final long duration = System.currentTimeMillis() - start;
        Log.i(TAG, "registerSource() took "
                + duration + " ms: " + label);
    }

    private void measureTriggerAttribution(
            MeasurementManager mm, String label) throws Exception {
        Log.i(TAG, "Calling registerTrigger()");
        final long start = System.currentTimeMillis();

        CompletableFuture<Void> future = new CompletableFuture<>();
        mm.registerTrigger(Uri.parse("https://example.com"),
                CALLBACK_EXECUTOR, future::complete);
        assertNull(future.get());

        final long duration = System.currentTimeMillis() - start;
        Log.i(TAG, "registerTrigger() took " + duration + " ms: " + label);
    }

    private void measureDeleteRegistrations(
            MeasurementManager mm, String label) throws Exception {
        Log.i(TAG, "Calling deleteRegistrations()");
        DeletionRequest request =
                new DeletionRequest.Builder()
                        .setOriginUris(
                                Collections.singletonList(Uri.parse("https://a.example1.com")))
                        .setDomainUris(Collections.singletonList(Uri.parse("https://example2.com")))
                        .setStart(Instant.ofEpochMilli(123456789L))
                        .setEnd(Instant.now())
                        .build();

        final long start = System.currentTimeMillis();

        CompletableFuture<Void> future = new CompletableFuture<>();
        mm.deleteRegistrations(request, CALLBACK_EXECUTOR, future::complete);
        assertNull(future.get());

        final long duration = System.currentTimeMillis() - start;
        Log.i(TAG, "deleteRegistrations() took " + duration + " ms: " + label);
    }

    private void measureGetMeasurementApiStatus(
            MeasurementManager mm, String label) throws Exception {
        Log.i(TAG, "Calling getMeasurementApiStatus()");
        final long start = System.currentTimeMillis();

        CompletableFuture<Integer> future = new CompletableFuture<>();
        OutcomeReceiver<Integer, Exception> callback =
                new OutcomeReceiver<Integer, Exception>() {
                    @Override
                    public void onResult(Integer result) {
                        future.complete(result);
                    }

                    @Override
                    public void onError(Exception error) {
                        Assert.fail();
                    }
                };

        mm.getMeasurementApiStatus(CALLBACK_EXECUTOR, callback);
        Assert.assertEquals(Integer.valueOf(
                MeasurementManager.MEASUREMENT_API_STATE_ENABLED), future.get());

        final long duration = System.currentTimeMillis() - start;
        Log.i(TAG, "getMeasurementApiStatus() took " + duration + " ms: " + label);
    }

    @Test
    public void testMeasurementManager() throws Exception {
        MeasurementManager mm = sContext.getSystemService(MeasurementManager.class);

        mm.unbindFromService();
        Thread.sleep(1000);

        measureRegisterAttributionSource(mm, "no-kill, 1st call");
        measureRegisterAttributionSource(mm, "no-kill, 2nd call");
        measureTriggerAttribution(mm, "no-kill, 1st call");
        measureTriggerAttribution(mm, "no-kill, 2nd call");
        measureDeleteRegistrations(mm, "no-kill, 1st call");
        measureDeleteRegistrations(mm, "no-kill, 2nd call");
    }

    /**
     * Test to measure an "end-to-end" latency of registerSource()
     * and registerTrigger, * when the service process isn't running.
     *
     * To run this test alone, use the following command.
     *     atest com.android.privateads.MeasurementManagerTest#testGetMeasurementAfterKillingService
     *
     * Note the performance varies depending on various factors (examples below),
     * so getting the "real world number" is really hard.
     * - What other processes are running, what they're doing, and the temperature of the device,
     *   which affects the CPU clock, disk I/O performance, etc...
     *   The busy the CPU is, the higher the clock gets, but that causes the CPU to become hot,
     *   which then will lower the CPU clock.
     *   For micro-benchmarks, we fixate to a lower clock speed to avoid fluctuation, which works
     *   okay for comparing multiple algorithms, but not a good way to get the "actual" number.
     *
     * Omakoto@ got ~52 ms on bramble(pixel 5)-eng, but:
     * - It's on an eng build, which is slower than userdebug builds, which are basically the
     *   "real" builds. (or close to it)
     * - Service doesn't do any file access yet.
     *
     *   See also:
     *   https://docs.google.com/document/d/1vmDpaL6c_mwe7vWX7CSP0tJcyHlNzE7o7piqdZoiKlU/edit#
     */
    @Test
    public void testGetMeasurementAfterKillingService() throws Exception {
        // This call should be instant, we don't need to measure it.
        MeasurementManager mm = sContext.getSystemService(MeasurementManager.class);

        // First, make sure the service process is killed.
        //
        // If the test isn't executed alone (e.g. if executed with `atest MeasurementManagerTest`),
        // then this process may already have a binding to the service, so make sure
        // we're not bound before killing the process.
        // Otherwise, if there's a binding, the system will restart the service automatically
        // after it gets killed.
        // (This would also happen if another process binds to the service process, so make sure
        // there's no such processes when running this.)
        mm.unbindFromService();

        // Kill the service process, if it's already running.
        // Give the system time to calm down.
        // If we know process isn't running for sure, then we don't need it.
        Thread.sleep(1000);
        // Kill the service process.
        ShellUtils.runShellCommand("su 0 killall -9 " + SERVICE_APK_NAME);
        Thread.sleep(1000);

        measureRegisterAttributionSource(mm, "no-kill, 1st call");
        measureRegisterAttributionSource(mm, "no-kill, 2nd call");
        measureTriggerAttribution(mm, "no-kill, 1st call");
        measureTriggerAttribution(mm, "no-kill, 2nd call");
        measureDeleteRegistrations(mm, "no-kill, 1st call");
        measureDeleteRegistrations(mm, "no-kill, 2nd call");
        measureGetMeasurementApiStatus(mm, "no-kill, 1st call");
        measureGetMeasurementApiStatus(mm, "no-kill, 2nd call");
    }

    @Test
    public void testRegisterSource_callingApp_expectedAttributionSource() throws Exception {
        MeasurementManager mm = Mockito.spy(sContext.getSystemService(MeasurementManager.class));
        IMeasurementService mockService = Mockito.mock(IMeasurementService.class);
        Mockito.when(mm.getService()).thenReturn(mockService);
        ArgumentCaptor<RegistrationRequest> captor =
                ArgumentCaptor.forClass(RegistrationRequest.class);
        Mockito.doNothing().when(mockService).register(captor.capture(), ArgumentMatchers.any());

        mm.registerSource(
                Uri.parse("https://example.com"),
                /* inputEvent = */ null,
                /* executor = */ null,
                /* callback = */ null);

        Assert.assertNotNull(captor.getValue().getPackageName());
        Assert.assertEquals(CLIENT_PACKAGE_NAME, captor.getValue().getPackageName());
    }

    @Test
    public void testRegisterSource_callingSdk_expectedAttributionSource() throws Exception {
        MeasurementManager mm =
                Mockito.spy(sSandboxedSdkContext.getSystemService(MeasurementManager.class));
        IMeasurementService mockService = Mockito.mock(IMeasurementService.class);
        Mockito.when(mm.getService()).thenReturn(mockService);
        ArgumentCaptor<RegistrationRequest> captor =
                ArgumentCaptor.forClass(RegistrationRequest.class);
        Mockito.doNothing().when(mockService).register(captor.capture(), ArgumentMatchers.any());

        mm.registerSource(
                Uri.parse("https://example.com"),
                /* inputEvent = */ null,
                /* executor = */ null,
                /* callback = */ null);

        Assert.assertNotNull(captor.getValue().getPackageName());
        Assert.assertEquals(CLIENT_PACKAGE_NAME, captor.getValue().getPackageName());
    }

    @Test
    public void testRegisterWebSource_callingApp_expectedAttributionSource() throws Exception {
        MeasurementManager mm = Mockito.spy(sContext.getSystemService(MeasurementManager.class));
        IMeasurementService mockService = Mockito.mock(IMeasurementService.class);
        Mockito.when(mm.getService()).thenReturn(mockService);
        ArgumentCaptor<WebSourceRegistrationRequestInternal> captor =
                ArgumentCaptor.forClass(WebSourceRegistrationRequestInternal.class);
        Mockito.doNothing()
                .when(mockService)
                .registerWebSource(captor.capture(), ArgumentMatchers.any());

        WebSourceParams webSourceParams =
                new WebSourceParams.Builder()
                        .setRegistrationUri(Uri.parse("https://example.com"))
                        .setAllowDebugKey(false)
                        .build();

        WebSourceRegistrationRequest webSourceRegistrationRequest =
                new WebSourceRegistrationRequest.Builder()
                        .setSourceParams(Collections.singletonList(webSourceParams))
                        .setTopOriginUri(Uri.parse("https://example.com"))
                        .setInputEvent(null)
                        .setOsDestination(Uri.parse("https://example.com"))
                        .setWebDestination(Uri.parse("https://example.com"))
                        .setVerifiedDestination(null)
                        .build();
        mm.registerWebSource(
                webSourceRegistrationRequest, /* executor = */ null, /* callback = */ null);

        Assert.assertNotNull(captor.getValue().getPackageName());
        Assert.assertEquals(CLIENT_PACKAGE_NAME, captor.getValue().getPackageName());
    }

    @Test
    public void testRegisterWebSource_callingSdk_expectedAttributionSource() throws Exception {
        MeasurementManager mm =
                Mockito.spy(sSandboxedSdkContext.getSystemService(MeasurementManager.class));
        IMeasurementService mockService = Mockito.mock(IMeasurementService.class);
        Mockito.when(mm.getService()).thenReturn(mockService);
        ArgumentCaptor<WebSourceRegistrationRequestInternal> captor =
                ArgumentCaptor.forClass(WebSourceRegistrationRequestInternal.class);
        Mockito.doNothing()
                .when(mockService)
                .registerWebSource(captor.capture(), ArgumentMatchers.any());

        WebSourceParams webSourceParams =
                new WebSourceParams.Builder()
                        .setRegistrationUri(Uri.parse("https://example.com"))
                        .setAllowDebugKey(false)
                        .build();

        WebSourceRegistrationRequest webSourceRegistrationRequest =
                new WebSourceRegistrationRequest.Builder()
                        .setSourceParams(Collections.singletonList(webSourceParams))
                        .setTopOriginUri(Uri.parse("https://example.com"))
                        .setInputEvent(null)
                        .setOsDestination(Uri.parse("https://example.com"))
                        .setWebDestination(Uri.parse("https://example.com"))
                        .setVerifiedDestination(null)
                        .build();
        mm.registerWebSource(
                webSourceRegistrationRequest, /* executor = */ null, /* callback = */ null);

        Assert.assertNotNull(captor.getValue().getPackageName());
        Assert.assertEquals(CLIENT_PACKAGE_NAME, captor.getValue().getPackageName());
    }

    @Test
    public void testRegisterWebTrigger_callingApp_expectedAttributionSource() throws Exception {
        MeasurementManager mm = Mockito.spy(sContext.getSystemService(MeasurementManager.class));
        IMeasurementService mockService = Mockito.mock(IMeasurementService.class);
        Mockito.when(mm.getService()).thenReturn(mockService);
        ArgumentCaptor<WebTriggerRegistrationRequestInternal> captor =
                ArgumentCaptor.forClass(WebTriggerRegistrationRequestInternal.class);
        Mockito.doNothing()
                .when(mockService)
                .registerWebTrigger(captor.capture(), ArgumentMatchers.any());

        WebTriggerParams webTriggerParams =
                new WebTriggerParams.Builder()
                        .setRegistrationUri(Uri.parse("https://example.com"))
                        .setAllowDebugKey(false)
                        .build();
        WebTriggerRegistrationRequest webTriggerRegistrationRequest =
                new WebTriggerRegistrationRequest.Builder()
                        .setTriggerParams(Collections.singletonList(webTriggerParams))
                        .setDestination(Uri.parse("https://example.com"))
                        .build();
        mm.registerWebTrigger(
                webTriggerRegistrationRequest, /* executor = */ null, /* callback = */ null);

        Assert.assertNotNull(captor.getValue().getPackageName());
        Assert.assertEquals(CLIENT_PACKAGE_NAME, captor.getValue().getPackageName());
    }

    @Test
    public void testRegisterWebTrigger_callingSdk_expectedAttributionSource() throws Exception {
        MeasurementManager mm =
                Mockito.spy(sSandboxedSdkContext.getSystemService(MeasurementManager.class));
        IMeasurementService mockService = Mockito.mock(IMeasurementService.class);
        Mockito.when(mm.getService()).thenReturn(mockService);
        ArgumentCaptor<WebTriggerRegistrationRequestInternal> captor =
                ArgumentCaptor.forClass(WebTriggerRegistrationRequestInternal.class);
        Mockito.doNothing()
                .when(mockService)
                .registerWebTrigger(captor.capture(), ArgumentMatchers.any());

        WebTriggerParams webTriggerParams =
                new WebTriggerParams.Builder()
                        .setRegistrationUri(Uri.parse("https://example.com"))
                        .setAllowDebugKey(false)
                        .build();
        WebTriggerRegistrationRequest webTriggerRegistrationRequest =
                new WebTriggerRegistrationRequest.Builder()
                        .setTriggerParams(Collections.singletonList(webTriggerParams))
                        .setDestination(Uri.parse("https://example.com"))
                        .build();
        mm.registerWebTrigger(
                webTriggerRegistrationRequest, /* executor = */ null, /* callback = */ null);

        Assert.assertNotNull(captor.getValue().getPackageName());
        Assert.assertEquals(CLIENT_PACKAGE_NAME, captor.getValue().getPackageName());
    }

    @Test
    public void testRegisterTrigger_callingApp_expectedAttributionSource() throws Exception {
        MeasurementManager mm = Mockito.spy(sContext.getSystemService(MeasurementManager.class));
        IMeasurementService mockService = Mockito.mock(IMeasurementService.class);
        Mockito.when(mm.getService()).thenReturn(mockService);
        ArgumentCaptor<RegistrationRequest> captor =
                ArgumentCaptor.forClass(RegistrationRequest.class);
        Mockito.doNothing().when(mockService).register(captor.capture(), ArgumentMatchers.any());

        mm.registerTrigger(
                Uri.parse("https://example.com"), /* executor = */ null, /* callback = */ null);

        Assert.assertNotNull(captor.getValue().getPackageName());
        Assert.assertEquals(CLIENT_PACKAGE_NAME, captor.getValue().getPackageName());
    }

    @Test
    public void testRegisterTrigger_callingSdk_expectedAttributionSource() throws Exception {
        MeasurementManager mm =
                Mockito.spy(sSandboxedSdkContext.getSystemService(MeasurementManager.class));
        IMeasurementService mockService = Mockito.mock(IMeasurementService.class);
        Mockito.when(mm.getService()).thenReturn(mockService);
        ArgumentCaptor<RegistrationRequest> captor =
                ArgumentCaptor.forClass(RegistrationRequest.class);
        Mockito.doNothing().when(mockService).register(captor.capture(), ArgumentMatchers.any());

        mm.registerTrigger(
                Uri.parse("https://example.com"), /* executor = */ null, /* callback = */ null);

        Assert.assertNotNull(captor.getValue().getPackageName());
        Assert.assertEquals(CLIENT_PACKAGE_NAME, captor.getValue().getPackageName());
    }

    @Test
    public void testDeleteRegistrations_callingApp_expectedAttributionSource() throws Exception {
        MeasurementManager mm = Mockito.spy(sContext.getSystemService(MeasurementManager.class));
        IMeasurementService mockService = Mockito.mock(IMeasurementService.class);
        Mockito.when(mm.getService()).thenReturn(mockService);
        ArgumentCaptor<DeletionParam> captor = ArgumentCaptor.forClass(DeletionParam.class);
        Mockito.doNothing()
                .when(mockService)
                .deleteRegistrations(captor.capture(), ArgumentMatchers.any());

        mm.deleteRegistrations(
                new DeletionRequest.Builder().build(),
                CALLBACK_EXECUTOR,
                i -> new CompletableFuture<>().complete(i));

        Assert.assertNotNull(captor.getValue().getPackageName());
        Assert.assertEquals(CLIENT_PACKAGE_NAME, captor.getValue().getPackageName());
    }

    @Test
    public void testDeleteRegistrations_callingSdk_expectedAttributionSource() throws Exception {
        MeasurementManager mm =
                Mockito.spy(sSandboxedSdkContext.getSystemService(MeasurementManager.class));
        IMeasurementService mockService = Mockito.mock(IMeasurementService.class);
        Mockito.when(mm.getService()).thenReturn(mockService);
        ArgumentCaptor<DeletionParam> captor = ArgumentCaptor.forClass(DeletionParam.class);
        Mockito.doNothing()
                .when(mockService)
                .deleteRegistrations(captor.capture(), ArgumentMatchers.any());

        mm.deleteRegistrations(
                new DeletionRequest.Builder().build(),
                CALLBACK_EXECUTOR,
                i -> new CompletableFuture<>().complete(i));

        Assert.assertNotNull(captor.getValue().getPackageName());
        Assert.assertEquals(CLIENT_PACKAGE_NAME, captor.getValue().getPackageName());
    }
}

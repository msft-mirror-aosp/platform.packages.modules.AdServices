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

import static org.mockito.ArgumentMatchers.any;

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

import androidx.test.core.app.ApplicationProvider;

import org.junit.Assert;
import org.junit.Test;
import org.mockito.ArgumentCaptor;
import org.mockito.Mockito;

import java.util.Collections;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.Executor;
import java.util.concurrent.Executors;

public class MeasurementManagerTest {
    // TODO: Add register tests with non-null callback and executor
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

    @Test
    public void testRegisterSource_callingApp_expectedAttributionSource() throws Exception {
        MeasurementManager mm = Mockito.spy(sContext.getSystemService(MeasurementManager.class));
        IMeasurementService mockService = Mockito.mock(IMeasurementService.class);
        Mockito.when(mm.getService()).thenReturn(mockService);
        ArgumentCaptor<RegistrationRequest> captor =
                ArgumentCaptor.forClass(RegistrationRequest.class);
        Mockito.doNothing().when(mockService).register(captor.capture(), any(), any());

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
        Mockito.doNothing().when(mockService).register(captor.capture(), any(), any());

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
        Mockito.doNothing().when(mockService).registerWebSource(captor.capture(), any(), any());

        WebSourceParams webSourceParams =
                new WebSourceParams.Builder(Uri.parse("https://example.com"))
                        .setDebugKeyAllowed(false)
                        .build();

        WebSourceRegistrationRequest webSourceRegistrationRequest =
                new WebSourceRegistrationRequest.Builder(
                                Collections.singletonList(webSourceParams),
                                Uri.parse("https://example.com"))
                        .setInputEvent(null)
                        .setAppDestination(Uri.parse("android-app://com.example"))
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
        Mockito.doNothing().when(mockService).registerWebSource(captor.capture(), any(), any());

        WebSourceParams webSourceParams =
                new WebSourceParams.Builder(Uri.parse("https://example.com"))
                        .setDebugKeyAllowed(false)
                        .build();

        WebSourceRegistrationRequest webSourceRegistrationRequest =
                new WebSourceRegistrationRequest.Builder(
                                Collections.singletonList(webSourceParams),
                                Uri.parse("https://example.com"))
                        .setInputEvent(null)
                        .setAppDestination(Uri.parse("android-app://com.example"))
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
        Mockito.doNothing().when(mockService).registerWebTrigger(captor.capture(), any(), any());

        WebTriggerParams webTriggerParams =
                new WebTriggerParams.Builder(Uri.parse("https://example.com"))
                        .setDebugKeyAllowed(false)
                        .build();
        WebTriggerRegistrationRequest webTriggerRegistrationRequest =
                new WebTriggerRegistrationRequest.Builder(
                                Collections.singletonList(webTriggerParams),
                                Uri.parse("https://example.com"))
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
        Mockito.doNothing().when(mockService).registerWebTrigger(captor.capture(), any(), any());

        WebTriggerParams webTriggerParams =
                new WebTriggerParams.Builder(Uri.parse("https://example.com"))
                        .setDebugKeyAllowed(false)
                        .build();
        WebTriggerRegistrationRequest webTriggerRegistrationRequest =
                new WebTriggerRegistrationRequest.Builder(
                                Collections.singletonList(webTriggerParams),
                                Uri.parse("https://example.com"))
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
        Mockito.doNothing().when(mockService).register(captor.capture(), any(), any());

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
        Mockito.doNothing().when(mockService).register(captor.capture(), any(), any());

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
        Mockito.doNothing().when(mockService).deleteRegistrations(captor.capture(), any(), any());

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
        Mockito.doNothing().when(mockService).deleteRegistrations(captor.capture(), any(), any());

        mm.deleteRegistrations(
                new DeletionRequest.Builder().build(),
                CALLBACK_EXECUTOR,
                i -> new CompletableFuture<>().complete(i));

        Assert.assertNotNull(captor.getValue().getPackageName());
        Assert.assertEquals(CLIENT_PACKAGE_NAME, captor.getValue().getPackageName());
    }

    @Test
    public void testGetMeasurementApiStatus() throws Exception {
        MeasurementManager mm = Mockito.spy(sContext.getSystemService(MeasurementManager.class));

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

        Assert.assertEquals(
                Integer.valueOf(MeasurementManager.MEASUREMENT_API_STATE_ENABLED), future.get());
    }
}

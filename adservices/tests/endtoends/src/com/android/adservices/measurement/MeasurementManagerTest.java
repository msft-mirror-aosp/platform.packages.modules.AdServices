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

import static org.junit.Assert.assertThrows;
import static org.junit.Assert.assertTrue;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.after;
import static org.mockito.Mockito.doAnswer;
import static org.mockito.Mockito.doNothing;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.spy;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

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
import android.net.Uri;
import android.os.OutcomeReceiver;

import androidx.annotation.NonNull;
import androidx.test.core.app.ApplicationProvider;

import com.android.compatibility.common.util.ShellUtils;

import org.junit.After;
import org.junit.Assert;
import org.junit.Test;
import org.mockito.ArgumentCaptor;
import org.mockito.stubbing.Answer;

import java.util.Collections;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.Executor;
import java.util.concurrent.Executors;
import java.util.concurrent.TimeUnit;

public class MeasurementManagerTest {
    private static final String CLIENT_PACKAGE_NAME = "com.android.adservices.endtoendtest";
    private static final long TIMEOUT = 5000L;
    private static final long CALLBACK_TIMEOUT = 100L;

    protected static final Context sContext = ApplicationProvider.getApplicationContext();
    private static final Executor CALLBACK_EXECUTOR = Executors.newCachedThreadPool();
    private static final SandboxedSdkContext sSandboxedSdkContext =
            new SandboxedSdkContext(
                    sContext,
                    sContext.getClassLoader(),
                    CLIENT_PACKAGE_NAME,
                    sContext.getApplicationInfo(),
                    "sdkName",
                    /* sdkCeDataDir = */ null,
                    /* sdkDeDataDir = */ null);

    @After
    public void tearDown() {
        resetOverrideConsentManagerDebugMode();
    }

    @Test
    public void testRegisterSource_callingApp_expectedAttributionSource() throws Exception {
        MeasurementManager mm = spy(sContext.getSystemService(MeasurementManager.class));
        IMeasurementService mockService = mock(IMeasurementService.class);
        when(mm.getService()).thenReturn(mockService);
        ArgumentCaptor<RegistrationRequest> captor =
                ArgumentCaptor.forClass(RegistrationRequest.class);
        CountDownLatch countDownLatch = new CountDownLatch(1);
        Answer<Void> answer =
                (invocation) -> {
                    countDownLatch.countDown();
                    return null;
                };
        doAnswer(answer).when(mockService).register(captor.capture(), any(), any());

        mm.registerSource(
                Uri.parse("https://example.com"),
                /* inputEvent = */ null,
                /* executor = */ null,
                /* callback = */ null);

        assertTrue(countDownLatch.await(TIMEOUT, TimeUnit.MILLISECONDS));
        Assert.assertNotNull(captor.getValue().getAppPackageName());
        Assert.assertEquals(CLIENT_PACKAGE_NAME, captor.getValue().getAppPackageName());
    }

    @Test
    public void testRegisterSource_BindServiceFailure_propagateErrorCallback() {
        MeasurementManager measurementManager =
                spy(sContext.getSystemService(MeasurementManager.class));
        when(measurementManager.getService()).thenThrow(new IllegalStateException());
        OutcomeReceiver callback = mock(OutcomeReceiver.class);
        measurementManager.registerSource(
                Uri.parse("https://example.com"),
                /* inputEvent = */ null,
                /* executor = */ CALLBACK_EXECUTOR,
                /* callback = */ callback);

        verify(callback, after(CALLBACK_TIMEOUT)).onError(any());
    }

    @Test
    public void testRegisterTrigger_BindServiceFailure_propagateErrorCallback() {
        MeasurementManager measurementManager =
                spy(sContext.getSystemService(MeasurementManager.class));
        when(measurementManager.getService()).thenThrow(new IllegalStateException());
        OutcomeReceiver callback = mock(OutcomeReceiver.class);

        measurementManager.registerTrigger(
                Uri.parse("https://example.com"),
                /* executor = */ CALLBACK_EXECUTOR,
                /* callback = */ callback);

        verify(callback, after(CALLBACK_TIMEOUT)).onError(any());
    }

    @Test
    public void testRegisterWebSource_BindServiceFailure_propagateErrorCallback() {
        MeasurementManager measurementManager =
                spy(sContext.getSystemService(MeasurementManager.class));
        when(measurementManager.getService()).thenThrow(new IllegalStateException());
        OutcomeReceiver callback = mock(OutcomeReceiver.class);

        measurementManager.registerWebSource(
                buildDefaultWebSourceRegistrationRequest(),
                /* executor = */ CALLBACK_EXECUTOR,
                /* callback = */ callback);

        verify(callback, after(CALLBACK_TIMEOUT)).onError(any());
    }

    @Test
    public void testRegisterWebTrigger_BindServiceFailure_propagateErrorCallback() {
        MeasurementManager measurementManager =
                spy(sContext.getSystemService(MeasurementManager.class));
        when(measurementManager.getService()).thenThrow(new IllegalStateException());
        OutcomeReceiver callback = mock(OutcomeReceiver.class);

        measurementManager.registerWebTrigger(
                buildDefaultWebTriggerRegistrationRequest(),
                /* executor = */ CALLBACK_EXECUTOR,
                /* callback = */ callback);

        verify(callback, after(CALLBACK_TIMEOUT)).onError(any());
    }

    @Test
    public void testRegisterSource_callingSdk_expectedAttributionSource() throws Exception {
        MeasurementManager mm =
                spy(sSandboxedSdkContext.getSystemService(MeasurementManager.class));
        IMeasurementService mockService = mock(IMeasurementService.class);
        when(mm.getService()).thenReturn(mockService);
        ArgumentCaptor<RegistrationRequest> captor =
                ArgumentCaptor.forClass(RegistrationRequest.class);
        CountDownLatch countDownLatch = new CountDownLatch(1);
        Answer<Void> answer =
                (invocation) -> {
                    countDownLatch.countDown();
                    return null;
                };
        doAnswer(answer).when(mockService).register(captor.capture(), any(), any());

        mm.registerSource(
                Uri.parse("https://example.com"),
                /* inputEvent = */ null,
                /* executor = */ null,
                /* callback = */ null);

        assertTrue(countDownLatch.await(TIMEOUT, TimeUnit.MILLISECONDS));
        Assert.assertNotNull(captor.getValue().getAppPackageName());
        Assert.assertEquals(CLIENT_PACKAGE_NAME, captor.getValue().getAppPackageName());
    }

    @Test
    public void testRegisterSource_executorAndCallbackCalled() throws Exception {
        final MeasurementManager mm = spy(sContext.getSystemService(MeasurementManager.class));
        final CountDownLatch anyCountDownLatch = new CountDownLatch(1);

        mm.registerSource(
                Uri.parse("https://registration-source"),
                /* inputEvent = */ null,
                CALLBACK_EXECUTOR,
                new OutcomeReceiver<Object, Exception>() {
                    @Override
                    public void onResult(@NonNull Object result) {
                        anyCountDownLatch.countDown();
                    }

                    @Override
                    public void onError(@NonNull Exception error) {
                        anyCountDownLatch.countDown();
                    }
                });

        assertTrue(anyCountDownLatch.await(TIMEOUT, TimeUnit.MILLISECONDS));
    }

    private WebSourceRegistrationRequest buildDefaultWebSourceRegistrationRequest() {
        WebSourceParams webSourceParams =
                new WebSourceParams.Builder(Uri.parse("https://example.com"))
                        .setDebugKeyAllowed(false)
                        .build();

        return new WebSourceRegistrationRequest.Builder(
                        Collections.singletonList(webSourceParams),
                        Uri.parse("https://example.com"))
                .setInputEvent(null)
                .setAppDestination(Uri.parse("android-app://com.example"))
                .setWebDestination(Uri.parse("https://example.com"))
                .setVerifiedDestination(null)
                .build();
    }

    @Test
    public void testRegisterWebSource_callingApp_expectedAttributionSource() throws Exception {
        MeasurementManager mm = spy(sContext.getSystemService(MeasurementManager.class));
        IMeasurementService mockService = mock(IMeasurementService.class);
        when(mm.getService()).thenReturn(mockService);
        ArgumentCaptor<WebSourceRegistrationRequestInternal> captor =
                ArgumentCaptor.forClass(WebSourceRegistrationRequestInternal.class);
        CountDownLatch countDownLatch = new CountDownLatch(1);
        Answer<Void> answer =
                (invocation) -> {
                    countDownLatch.countDown();
                    return null;
                };
        doAnswer(answer).when(mockService).registerWebSource(captor.capture(), any(), any());

        mm.registerWebSource(
                buildDefaultWebSourceRegistrationRequest(),
                /* executor = */ null,
                /* callback = */ null);

        assertTrue(countDownLatch.await(TIMEOUT, TimeUnit.MILLISECONDS));
        Assert.assertNotNull(captor.getValue().getAppPackageName());
        Assert.assertEquals(CLIENT_PACKAGE_NAME, captor.getValue().getAppPackageName());
    }

    @Test
    public void testRegisterWebSource_callingSdk_expectedAttributionSource() throws Exception {
        MeasurementManager mm =
                spy(sSandboxedSdkContext.getSystemService(MeasurementManager.class));
        IMeasurementService mockService = mock(IMeasurementService.class);
        when(mm.getService()).thenReturn(mockService);
        ArgumentCaptor<WebSourceRegistrationRequestInternal> captor =
                ArgumentCaptor.forClass(WebSourceRegistrationRequestInternal.class);
        CountDownLatch countDownLatch = new CountDownLatch(1);
        Answer<Void> answer =
                (invocation) -> {
                    countDownLatch.countDown();
                    return null;
                };
        doAnswer(answer).when(mockService).registerWebSource(captor.capture(), any(), any());

        mm.registerWebSource(
                buildDefaultWebSourceRegistrationRequest(),
                /* executor = */ null,
                /* callback = */ null);

        assertTrue(countDownLatch.await(TIMEOUT, TimeUnit.MILLISECONDS));
        Assert.assertNotNull(captor.getValue().getAppPackageName());
        Assert.assertEquals(CLIENT_PACKAGE_NAME, captor.getValue().getAppPackageName());
    }

    @Test
    public void testRegisterWebSource_executorAndCallbackCalled() throws Exception {
        final MeasurementManager mm = spy(sContext.getSystemService(MeasurementManager.class));
        final CountDownLatch anyCountDownLatch = new CountDownLatch(1);

        mm.registerWebSource(
                buildDefaultWebSourceRegistrationRequest(),
                CALLBACK_EXECUTOR,
                new OutcomeReceiver<Object, Exception>() {
                    @Override
                    public void onResult(@NonNull Object result) {
                        anyCountDownLatch.countDown();
                    }

                    @Override
                    public void onError(@NonNull Exception error) {
                        anyCountDownLatch.countDown();
                    }
                });

        assertTrue(anyCountDownLatch.await(TIMEOUT, TimeUnit.MILLISECONDS));
    }

    private WebTriggerRegistrationRequest buildDefaultWebTriggerRegistrationRequest() {
        WebTriggerParams webTriggerParams =
                new WebTriggerParams.Builder(Uri.parse("https://example.com"))
                        .setDebugKeyAllowed(false)
                        .build();
        return new WebTriggerRegistrationRequest.Builder(
                        Collections.singletonList(webTriggerParams),
                        Uri.parse("https://example.com"))
                .build();
    }

    @Test
    public void testRegisterWebTrigger_callingApp_expectedAttributionSource() throws Exception {
        MeasurementManager mm = spy(sContext.getSystemService(MeasurementManager.class));
        IMeasurementService mockService = mock(IMeasurementService.class);
        when(mm.getService()).thenReturn(mockService);
        ArgumentCaptor<WebTriggerRegistrationRequestInternal> captor =
                ArgumentCaptor.forClass(WebTriggerRegistrationRequestInternal.class);
        CountDownLatch countDownLatch = new CountDownLatch(1);
        Answer<Void> answer =
                (invocation) -> {
                    countDownLatch.countDown();
                    return null;
                };
        doAnswer(answer).when(mockService).registerWebTrigger(captor.capture(), any(), any());

        mm.registerWebTrigger(
                buildDefaultWebTriggerRegistrationRequest(),
                /* executor = */ null,
                /* callback = */ null);

        assertTrue(countDownLatch.await(TIMEOUT, TimeUnit.MILLISECONDS));
        Assert.assertNotNull(captor.getValue().getAppPackageName());
        Assert.assertEquals(CLIENT_PACKAGE_NAME, captor.getValue().getAppPackageName());
    }

    @Test
    public void testRegisterWebTrigger_callingSdk_expectedAttributionSource() throws Exception {
        MeasurementManager mm =
                spy(sSandboxedSdkContext.getSystemService(MeasurementManager.class));
        IMeasurementService mockService = mock(IMeasurementService.class);
        when(mm.getService()).thenReturn(mockService);
        ArgumentCaptor<WebTriggerRegistrationRequestInternal> captor =
                ArgumentCaptor.forClass(WebTriggerRegistrationRequestInternal.class);
        CountDownLatch countDownLatch = new CountDownLatch(1);
        Answer<Void> answer =
                (invocation) -> {
                    countDownLatch.countDown();
                    return null;
                };
        doAnswer(answer).when(mockService).registerWebTrigger(captor.capture(), any(), any());

        mm.registerWebTrigger(
                buildDefaultWebTriggerRegistrationRequest(),
                /* executor = */ null,
                /* callback = */ null);

        assertTrue(countDownLatch.await(TIMEOUT, TimeUnit.MILLISECONDS));
        Assert.assertNotNull(captor.getValue().getAppPackageName());
        Assert.assertEquals(CLIENT_PACKAGE_NAME, captor.getValue().getAppPackageName());
    }

    @Test
    public void testRegisterWebTrigger_executorAndCallbackCalled() throws Exception {
        final MeasurementManager mm = spy(sContext.getSystemService(MeasurementManager.class));
        final CountDownLatch anyCountDownLatch = new CountDownLatch(1);

        mm.registerWebTrigger(
                buildDefaultWebTriggerRegistrationRequest(),
                CALLBACK_EXECUTOR,
                new OutcomeReceiver<Object, Exception>() {
                    @Override
                    public void onResult(@NonNull Object result) {
                        anyCountDownLatch.countDown();
                    }

                    @Override
                    public void onError(@NonNull Exception error) {
                        anyCountDownLatch.countDown();
                    }
                });

        assertTrue(anyCountDownLatch.await(TIMEOUT, TimeUnit.MILLISECONDS));
    }

    @Test
    public void testRegisterTrigger_callingApp_expectedAttributionSource() throws Exception {
        MeasurementManager mm = spy(sContext.getSystemService(MeasurementManager.class));
        IMeasurementService mockService = mock(IMeasurementService.class);
        when(mm.getService()).thenReturn(mockService);
        ArgumentCaptor<RegistrationRequest> captor =
                ArgumentCaptor.forClass(RegistrationRequest.class);
        CountDownLatch countDownLatch = new CountDownLatch(1);
        Answer<Void> answer =
                (invocation) -> {
                    countDownLatch.countDown();
                    return null;
                };
        doAnswer(answer).when(mockService).register(captor.capture(), any(), any());

        mm.registerTrigger(
                Uri.parse("https://example.com"), /* executor = */ null, /* callback = */ null);

        assertTrue(countDownLatch.await(TIMEOUT, TimeUnit.MILLISECONDS));
        Assert.assertNotNull(captor.getValue().getAppPackageName());
        Assert.assertEquals(CLIENT_PACKAGE_NAME, captor.getValue().getAppPackageName());
    }

    @Test
    public void testRegisterTrigger_callingSdk_expectedAttributionSource() throws Exception {
        MeasurementManager mm =
                spy(sSandboxedSdkContext.getSystemService(MeasurementManager.class));
        IMeasurementService mockService = mock(IMeasurementService.class);
        when(mm.getService()).thenReturn(mockService);
        ArgumentCaptor<RegistrationRequest> captor =
                ArgumentCaptor.forClass(RegistrationRequest.class);
        CountDownLatch countDownLatch = new CountDownLatch(1);
        Answer<Void> answer =
                (invocation) -> {
                    countDownLatch.countDown();
                    return null;
                };
        doAnswer(answer).when(mockService).register(captor.capture(), any(), any());

        mm.registerTrigger(
                Uri.parse("https://example.com"), /* executor = */ null, /* callback = */ null);

        assertTrue(countDownLatch.await(TIMEOUT, TimeUnit.MILLISECONDS));
        Assert.assertNotNull(captor.getValue().getAppPackageName());
        Assert.assertEquals(CLIENT_PACKAGE_NAME, captor.getValue().getAppPackageName());
    }

    @Test
    public void testRegisterTrigger_executorAndCallbackCalled() throws Exception {
        final MeasurementManager mm = spy(sContext.getSystemService(MeasurementManager.class));
        final CountDownLatch anyCountDownLatch = new CountDownLatch(1);

        mm.registerTrigger(
                Uri.parse("https://registration-trigger"),
                CALLBACK_EXECUTOR,
                new OutcomeReceiver<Object, Exception>() {
                    @Override
                    public void onResult(@NonNull Object result) {
                        anyCountDownLatch.countDown();
                    }

                    @Override
                    public void onError(@NonNull Exception error) {
                        anyCountDownLatch.countDown();
                    }
                });

        assertTrue(anyCountDownLatch.await(TIMEOUT, TimeUnit.MILLISECONDS));
    }

    @Test
    public void testDeleteRegistrations_callingApp_expectedAttributionSource() throws Exception {
        MeasurementManager mm = spy(sContext.getSystemService(MeasurementManager.class));
        IMeasurementService mockService = mock(IMeasurementService.class);
        when(mm.getService()).thenReturn(mockService);
        ArgumentCaptor<DeletionParam> captor = ArgumentCaptor.forClass(DeletionParam.class);
        doNothing().when(mockService).deleteRegistrations(captor.capture(), any(), any());

        mm.deleteRegistrations(
                new DeletionRequest.Builder().build(),
                CALLBACK_EXECUTOR,
                i -> new CompletableFuture<>().complete(i));

        Assert.assertNotNull(captor.getValue().getAppPackageName());
        Assert.assertEquals(CLIENT_PACKAGE_NAME, captor.getValue().getAppPackageName());
    }

    @Test
    public void testDeleteRegistrations_callingSdk_expectedAttributionSource() throws Exception {
        MeasurementManager mm =
                spy(sSandboxedSdkContext.getSystemService(MeasurementManager.class));
        IMeasurementService mockService = mock(IMeasurementService.class);
        when(mm.getService()).thenReturn(mockService);
        ArgumentCaptor<DeletionParam> captor = ArgumentCaptor.forClass(DeletionParam.class);
        doNothing().when(mockService).deleteRegistrations(captor.capture(), any(), any());

        mm.deleteRegistrations(
                new DeletionRequest.Builder().build(),
                CALLBACK_EXECUTOR,
                i -> new CompletableFuture<>().complete(i));

        Assert.assertNotNull(captor.getValue().getAppPackageName());
        Assert.assertEquals(CLIENT_PACKAGE_NAME, captor.getValue().getAppPackageName());
    }

    @Test
    public void testDeleteRegistrations_nullExecutor_throwNullPointerException() {
        MeasurementManager mm = spy(sContext.getSystemService(MeasurementManager.class));

        assertThrows(
                NullPointerException.class,
                () ->
                        mm.deleteRegistrations(
                                new DeletionRequest.Builder().build(),
                                /* executor */ null,
                                i -> new CompletableFuture<>().complete(i)));
    }

    @Test
    public void testDeleteRegistrations_nullCallback_throwNullPointerException() {
        MeasurementManager mm = spy(sContext.getSystemService(MeasurementManager.class));

        assertThrows(
                NullPointerException.class,
                () ->
                        mm.deleteRegistrations(
                                new DeletionRequest.Builder().build(),
                                CALLBACK_EXECUTOR,
                                /* callback */ null));
    }

    @Test
    public void testGetMeasurementApiStatus() throws Exception {
        MeasurementManager mm = spy(sContext.getSystemService(MeasurementManager.class));
        overrideConsentManagerDebugMode();

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

    @Test
    public void testGetMeasurementApiStatus_nullExecutor_throwNullPointerException() {
        MeasurementManager mm = spy(sContext.getSystemService(MeasurementManager.class));
        overrideConsentManagerDebugMode();

        assertThrows(
                NullPointerException.class,
                () -> mm.getMeasurementApiStatus(/* executor */ null, result -> {}));
    }

    @Test
    public void testGetMeasurementApiStatus_nullCallback_throwNullPointerException() {
        MeasurementManager mm = spy(sContext.getSystemService(MeasurementManager.class));
        overrideConsentManagerDebugMode();

        assertThrows(
                NullPointerException.class,
                () -> mm.getMeasurementApiStatus(CALLBACK_EXECUTOR, /* callback */ null));
    }

    // Override the Consent Manager behaviour - Consent Given
    private void overrideConsentManagerDebugMode() {
        ShellUtils.runShellCommand("setprop debug.adservices.consent_manager_debug_mode true");
    }

    private void resetOverrideConsentManagerDebugMode() {
        ShellUtils.runShellCommand("setprop debug.adservices.consent_manager_debug_mode null");
    }
}

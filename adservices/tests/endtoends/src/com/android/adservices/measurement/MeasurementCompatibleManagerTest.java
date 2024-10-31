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

import static com.android.adservices.measurement.MeasurementManagerUtil.buildDefaultAppSourcesRegistrationRequest;
import static com.android.adservices.measurement.MeasurementManagerUtil.buildDefaultWebSourceRegistrationRequest;
import static com.android.adservices.measurement.MeasurementManagerUtil.buildDefaultWebTriggerRegistrationRequest;
import static com.android.adservices.service.DebugFlagsConstants.KEY_CONSENT_MANAGER_DEBUG_MODE;

import static com.google.common.truth.Truth.assertThat;

import static org.junit.Assert.assertThrows;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.after;
import static org.mockito.Mockito.doAnswer;
import static org.mockito.Mockito.doNothing;
import static org.mockito.Mockito.doReturn;
import static org.mockito.Mockito.doThrow;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.spy;
import static org.mockito.Mockito.verify;

import android.adservices.adid.AdId;
import android.adservices.adid.AdIdManager;
import android.adservices.measurement.DeletionParam;
import android.adservices.measurement.DeletionRequest;
import android.adservices.measurement.IMeasurementService;
import android.adservices.measurement.MeasurementCompatibleManager;
import android.adservices.measurement.MeasurementManager;
import android.adservices.measurement.RegistrationRequest;
import android.adservices.measurement.SourceRegistrationRequestInternal;
import android.adservices.measurement.WebSourceRegistrationRequestInternal;
import android.adservices.measurement.WebTriggerRegistrationRequestInternal;
import android.app.sdksandbox.SandboxedSdkContext;
import android.net.Uri;
import android.os.OutcomeReceiver;
import android.os.RemoteException;

import androidx.annotation.NonNull;

import com.android.adservices.AdServicesEndToEndTestCase;
import com.android.adservices.LogUtil;
import com.android.adservices.common.annotations.SetMsmtApiAppAllowList;
import com.android.adservices.shared.testing.annotations.RequiresSdkLevelAtLeastT;
import com.android.modules.utils.build.SdkLevel;

import org.junit.Assert;
import org.junit.Test;
import org.mockito.ArgumentCaptor;
import org.mockito.stubbing.Answer;

import java.util.concurrent.CompletableFuture;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.Executor;
import java.util.concurrent.Executors;
import java.util.concurrent.TimeUnit;

@SetMsmtApiAppAllowList
@SuppressWarnings("NewApi")
public final class MeasurementCompatibleManagerTest extends AdServicesEndToEndTestCase {

    private static final String CLIENT_PACKAGE_NAME = "com.android.adservices.endtoendtest";
    private static final long TIMEOUT = 5000L;
    private static final long CALLBACK_TIMEOUT = 1000L;

    private static final long AD_ID_TIMEOUT = 500;

    public static final String AD_ID = "35a4ac90-e4dc-4fe7-bbc6-95e804aa7dbc";

    private static final Executor CALLBACK_EXECUTOR = Executors.newCachedThreadPool();
    private static final SandboxedSdkContext sSandboxedSdkContext =
            new SandboxedSdkContext(
                    sContext,
                    sContext.getClassLoader(),
                    CLIENT_PACKAGE_NAME,
                    sContext.getApplicationInfo(),
                    "sdkName",
                    /* sdkCeDataDir= */ null,
                    /* sdkDeDataDir= */ null);

    private static String getPackageName() {
        return SdkLevel.isAtLeastT()
                ? "com.android.adservices.endtoendtest"
                : "com.android.adextservices.endtoendtest";
    }

    private static MeasurementCompatibleManager getMeasurementCompatibleManager() {
        return spy(MeasurementCompatibleManager.get(sContext));
    }

    @Test
    public void testRegisterSource_callingApp_expectedAttributionSource() throws Exception {
        MeasurementCompatibleManager mm = getMeasurementCompatibleManager();
        IMeasurementService mockService = mock(IMeasurementService.class);
        doReturn(mockService).when(mm).getService();
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

        assertThat(countDownLatch.await(TIMEOUT, TimeUnit.MILLISECONDS)).isTrue();
        assertThat(captor.getValue().getAppPackageName()).isNotNull();
        assertThat(captor.getValue().getAppPackageName()).isEqualTo(getPackageName());
    }

    @Test
    public void testRegisterSource_BindServiceFailure_propagateErrorCallback() {
        MeasurementCompatibleManager measurementManager = getMeasurementCompatibleManager();
        doThrow(new IllegalStateException()).when(measurementManager).getService();
        OutcomeReceiver callback = mock(OutcomeReceiver.class);
        measurementManager.registerSource(
                Uri.parse("https://example.com"),
                /* inputEvent = */ null,
                /* executor = */ CALLBACK_EXECUTOR,
                /* callback = */ callback);

        verify(callback, after(CALLBACK_TIMEOUT)).onError(any());
    }

    @Test
    public void testRegisterSource_adIdEnabled_register() throws Exception {
        AdIdManager adIdManager = mock(AdIdManager.class);
        MeasurementCompatibleManager measurementManager =
                spy(MeasurementCompatibleManager.get(sContext, adIdManager));

        IMeasurementService mockService = mock(IMeasurementService.class);
        doReturn(mockService).when(measurementManager).getService();
        ArgumentCaptor<RegistrationRequest> captor =
                ArgumentCaptor.forClass(RegistrationRequest.class);
        CountDownLatch countDownLatch = new CountDownLatch(1);
        Answer<Void> answer =
                (invocation) -> {
                    countDownLatch.countDown();
                    return null;
                };
        doAnswer(answer).when(mockService).register(captor.capture(), any(), any());

        doAnswer(
                        (invocation) -> {
                            ((OutcomeReceiver) invocation.getArgument(1))
                                    .onResult(new AdId(AD_ID, true));
                            return null;
                        })
                .when(adIdManager)
                .getAdId(any(), any(OutcomeReceiver.class));

        measurementManager.registerSource(
                Uri.parse("https://example.com"),
                /* inputEvent = */ null,
                /* executor = */ null,
                /* callback = */ null);

        assertThat(countDownLatch.await(TIMEOUT, TimeUnit.MILLISECONDS)).isTrue();
        expect.that(captor.getValue().isAdIdPermissionGranted()).isTrue();
        expect.that(captor.getValue().getAppPackageName()).isEqualTo(getPackageName());
        verify(mock(LogUtil.class), never()).w(anyString());
    }

    @Test
    public void testRegisterSource_adIdZeroOut_register() throws Exception {
        AdIdManager adIdManager = mock(AdIdManager.class);
        MeasurementCompatibleManager measurementManager =
                spy(MeasurementCompatibleManager.get(sContext, adIdManager));

        IMeasurementService mockService = mock(IMeasurementService.class);
        doReturn(mockService).when(measurementManager).getService();
        ArgumentCaptor<RegistrationRequest> captor =
                ArgumentCaptor.forClass(RegistrationRequest.class);
        CountDownLatch countDownLatch = new CountDownLatch(1);
        Answer<Void> answer =
                (invocation) -> {
                    countDownLatch.countDown();
                    return null;
                };
        doAnswer(answer).when(mockService).register(captor.capture(), any(), any());

        doAnswer(
                        (invocation) -> {
                            ((OutcomeReceiver) invocation.getArgument(1))
                                    .onResult(new AdId(AdId.ZERO_OUT, true));
                            return null;
                        })
                .when(adIdManager)
                .getAdId(any(), any(OutcomeReceiver.class));

        measurementManager.registerSource(
                Uri.parse("https://example.com"),
                /* inputEvent = */ null,
                /* executor = */ null,
                /* callback = */ null);

        assertThat(countDownLatch.await(TIMEOUT, TimeUnit.MILLISECONDS)).isTrue();
        expect.that(captor.getValue().isAdIdPermissionGranted()).isFalse();
        expect.that(captor.getValue().getAppPackageName()).isEqualTo(getPackageName());
    }

    @Test
    public void testRegisterSource_adIdDisabled_register() throws Exception {
        AdIdManager adIdManager = mock(AdIdManager.class);
        MeasurementCompatibleManager measurementManager =
                spy(MeasurementCompatibleManager.get(sContext, adIdManager));

        IMeasurementService mockService = mock(IMeasurementService.class);
        doReturn(mockService).when(measurementManager).getService();
        ArgumentCaptor<RegistrationRequest> captor =
                ArgumentCaptor.forClass(RegistrationRequest.class);
        CountDownLatch countDownLatch = new CountDownLatch(1);
        Answer<Void> answer =
                (invocation) -> {
                    countDownLatch.countDown();
                    return null;
                };
        doAnswer(answer).when(mockService).register(captor.capture(), any(), any());

        doAnswer(
                        (invocation) -> {
                            ((OutcomeReceiver) invocation.getArgument(1))
                                    .onError(new SecurityException());
                            return null;
                        })
                .when(adIdManager)
                .getAdId(any(), any(OutcomeReceiver.class));

        measurementManager.registerSource(
                Uri.parse("https://example.com"),
                /* inputEvent = */ null,
                /* executor = */ null,
                /* callback = */ null);

        assertThat(countDownLatch.await(TIMEOUT, TimeUnit.MILLISECONDS)).isTrue();
        expect.that(captor.getValue().isAdIdPermissionGranted()).isFalse();
        expect.that(captor.getValue().getAppPackageName()).isEqualTo(getPackageName());
        verify(mock(LogUtil.class)).w(anyString());
    }

    @Test
    public void testRegisterSource_adIdTimeOut_register() throws Exception {
        AdIdManager adIdManager = mock(AdIdManager.class);
        MeasurementCompatibleManager measurementManager =
                spy(MeasurementCompatibleManager.get(sContext, adIdManager));

        IMeasurementService mockService = mock(IMeasurementService.class);
        doReturn(mockService).when(measurementManager).getService();
        ArgumentCaptor<RegistrationRequest> captor =
                ArgumentCaptor.forClass(RegistrationRequest.class);
        CountDownLatch countDownLatch = new CountDownLatch(1);
        Answer<Void> answer =
                (invocation) -> {
                    countDownLatch.countDown();
                    return null;
                };
        doAnswer(answer).when(mockService).register(captor.capture(), any(), any());

        doAnswer(
                        (invocation) -> {
                            Thread.sleep(AD_ID_TIMEOUT);
                            return null;
                        })
                .when(adIdManager)
                .getAdId(any(), any(OutcomeReceiver.class));

        measurementManager.registerSource(
                Uri.parse("https://example.com"),
                /* inputEvent = */ null,
                /* executor = */ null,
                /* callback = */ null);

        assertThat(countDownLatch.await(TIMEOUT, TimeUnit.MILLISECONDS)).isTrue();
        expect.that(captor.getValue().isAdIdPermissionGranted()).isFalse();
        expect.that(captor.getValue().getAppPackageName()).isEqualTo(getPackageName());
    }

    @Test
    public void testRegisterTrigger_BindServiceFailure_propagateErrorCallback() {
        MeasurementCompatibleManager measurementManager = getMeasurementCompatibleManager();
        doThrow(new IllegalStateException()).when(measurementManager).getService();
        OutcomeReceiver callback = mock(OutcomeReceiver.class);

        measurementManager.registerTrigger(
                Uri.parse("https://example.com"),
                /* executor = */ CALLBACK_EXECUTOR,
                /* callback = */ callback);

        verify(callback, after(CALLBACK_TIMEOUT)).onError(any());
    }

    @Test
    public void testRegisterWebSource_BindServiceFailure_propagateErrorCallback() {
        MeasurementCompatibleManager measurementManager = getMeasurementCompatibleManager();
        doThrow(new IllegalStateException()).when(measurementManager).getService();
        OutcomeReceiver callback = mock(OutcomeReceiver.class);

        measurementManager.registerWebSource(
                buildDefaultWebSourceRegistrationRequest(),
                /* executor = */ CALLBACK_EXECUTOR,
                /* callback = */ callback);

        verify(callback, after(CALLBACK_TIMEOUT)).onError(any());
    }

    @Test
    public void testRegisterWebTrigger_BindServiceFailure_propagateErrorCallback() {
        MeasurementCompatibleManager measurementManager = getMeasurementCompatibleManager();
        doThrow(new IllegalStateException()).when(measurementManager).getService();
        OutcomeReceiver callback = mock(OutcomeReceiver.class);

        measurementManager.registerWebTrigger(
                buildDefaultWebTriggerRegistrationRequest(),
                /* executor = */ CALLBACK_EXECUTOR,
                /* callback = */ callback);

        verify(callback, after(CALLBACK_TIMEOUT)).onError(any());
    }

    @Test
    @RequiresSdkLevelAtLeastT
    public void testRegisterSource_callingSdk_expectedAttributionSource() throws Exception {
        MeasurementCompatibleManager mm =
                spy(MeasurementCompatibleManager.get(sSandboxedSdkContext));
        IMeasurementService mockService = mock(IMeasurementService.class);
        doReturn(mockService).when(mm).getService();
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

        assertThat(countDownLatch.await(TIMEOUT, TimeUnit.MILLISECONDS)).isTrue();
        assertThat(captor.getValue().getAppPackageName()).isNotNull();
        expect.that(captor.getValue().getAppPackageName()).isEqualTo(getPackageName());
    }

    @Test
    public void testRegisterSource_executorAndCallbackCalled() throws Exception {
        MeasurementCompatibleManager mm = getMeasurementCompatibleManager();
        CountDownLatch anyCountDownLatch = new CountDownLatch(1);

        mm.registerSource(
                Uri.parse("https://registration-source"),
                /* inputEvent= */ null,
                CALLBACK_EXECUTOR,
                new OutcomeReceiver<>() {
                    @Override
                    public void onResult(@NonNull Object result) {
                        anyCountDownLatch.countDown();
                    }

                    @Override
                    public void onError(@NonNull Exception error) {
                        anyCountDownLatch.countDown();
                    }
                });

        assertThat(anyCountDownLatch.await(TIMEOUT, TimeUnit.MILLISECONDS)).isTrue();
    }

    @Test
    public void testRegisterWebSource_callingApp_expectedAttributionSource() throws Exception {
        MeasurementCompatibleManager mm = getMeasurementCompatibleManager();
        IMeasurementService mockService = mock(IMeasurementService.class);
        doReturn(mockService).when(mm).getService();
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

        assertThat(countDownLatch.await(TIMEOUT, TimeUnit.MILLISECONDS)).isTrue();
        assertThat(captor.getValue().getAppPackageName()).isNotNull();
        expect.that(captor.getValue().getAppPackageName()).isEqualTo(getPackageName());
    }

    @Test
    @RequiresSdkLevelAtLeastT
    public void testRegisterWebSource_callingSdk_expectedAttributionSource() throws Exception {
        MeasurementCompatibleManager mm =
                spy(MeasurementCompatibleManager.get(sSandboxedSdkContext));
        IMeasurementService mockService = mock(IMeasurementService.class);
        doReturn(mockService).when(mm).getService();
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

        assertThat(countDownLatch.await(TIMEOUT, TimeUnit.MILLISECONDS)).isTrue();
        assertThat(captor.getValue().getAppPackageName()).isNotNull();
        expect.that(captor.getValue().getAppPackageName()).isEqualTo(getPackageName());
    }

    @Test
    public void testRegisterWebSource_executorAndCallbackCalled() throws Exception {
        MeasurementCompatibleManager mm = getMeasurementCompatibleManager();
        CountDownLatch anyCountDownLatch = new CountDownLatch(1);

        mm.registerWebSource(
                buildDefaultWebSourceRegistrationRequest(),
                CALLBACK_EXECUTOR,
                new OutcomeReceiver<>() {
                    @Override
                    public void onResult(@NonNull Object result) {
                        anyCountDownLatch.countDown();
                    }

                    @Override
                    public void onError(@NonNull Exception error) {
                        anyCountDownLatch.countDown();
                    }
                });

        assertThat(anyCountDownLatch.await(TIMEOUT, TimeUnit.MILLISECONDS)).isTrue();
    }

    @Test
    public void testRegisterWebSource_adIdEnabled_register() throws Exception {
        AdIdManager adIdManager = mock(AdIdManager.class);
        MeasurementCompatibleManager measurementManager =
                spy(MeasurementCompatibleManager.get(sContext, adIdManager));

        IMeasurementService mockService = mock(IMeasurementService.class);
        doReturn(mockService).when(measurementManager).getService();
        ArgumentCaptor<WebSourceRegistrationRequestInternal> captor =
                ArgumentCaptor.forClass(WebSourceRegistrationRequestInternal.class);
        CountDownLatch countDownLatch = new CountDownLatch(1);
        Answer<Void> answer =
                (invocation) -> {
                    countDownLatch.countDown();
                    return null;
                };
        doAnswer(answer).when(mockService).registerWebSource(captor.capture(), any(), any());

        doAnswer(
                        (invocation) -> {
                            ((OutcomeReceiver) invocation.getArgument(1))
                                    .onResult(new AdId(AD_ID, true));
                            return null;
                        })
                .when(adIdManager)
                .getAdId(any(), any(OutcomeReceiver.class));

        measurementManager.registerWebSource(
                buildDefaultWebSourceRegistrationRequest(),
                /* executor = */ null,
                /* callback = */ null);

        assertThat(countDownLatch.await(TIMEOUT, TimeUnit.MILLISECONDS)).isTrue();
        assertThat(captor.getValue().isAdIdPermissionGranted()).isTrue();
        expect.that(captor.getValue().getAppPackageName()).isEqualTo(getPackageName());
        verify(mock(LogUtil.class), never()).w(anyString());
    }

    @Test
    public void testRegisterWebSource_adIdZeroOut_register() throws Exception {
        AdIdManager adIdManager = mock(AdIdManager.class);
        MeasurementCompatibleManager measurementManager =
                spy(MeasurementCompatibleManager.get(sContext, adIdManager));

        IMeasurementService mockService = mock(IMeasurementService.class);
        doReturn(mockService).when(measurementManager).getService();
        ArgumentCaptor<WebSourceRegistrationRequestInternal> captor =
                ArgumentCaptor.forClass(WebSourceRegistrationRequestInternal.class);
        CountDownLatch countDownLatch = new CountDownLatch(1);
        Answer<Void> answer =
                (invocation) -> {
                    countDownLatch.countDown();
                    return null;
                };
        doAnswer(answer).when(mockService).registerWebSource(captor.capture(), any(), any());

        doAnswer(
                        (invocation) -> {
                            ((OutcomeReceiver) invocation.getArgument(1))
                                    .onResult(new AdId(AdId.ZERO_OUT, true));
                            return null;
                        })
                .when(adIdManager)
                .getAdId(any(), any(OutcomeReceiver.class));

        measurementManager.registerWebSource(
                buildDefaultWebSourceRegistrationRequest(),
                /* executor = */ null,
                /* callback = */ null);

        assertThat(countDownLatch.await(TIMEOUT, TimeUnit.MILLISECONDS)).isTrue();
        expect.that(captor.getValue().isAdIdPermissionGranted()).isFalse();
        expect.that(captor.getValue().getAppPackageName()).isEqualTo(getPackageName());
    }

    @Test
    public void testRegisterWebSource_adIdDisabled_register() throws Exception {
        AdIdManager adIdManager = mock(AdIdManager.class);
        MeasurementCompatibleManager measurementManager =
                spy(MeasurementCompatibleManager.get(sContext, adIdManager));

        IMeasurementService mockService = mock(IMeasurementService.class);
        doReturn(mockService).when(measurementManager).getService();
        ArgumentCaptor<WebSourceRegistrationRequestInternal> captor =
                ArgumentCaptor.forClass(WebSourceRegistrationRequestInternal.class);
        CountDownLatch countDownLatch = new CountDownLatch(1);
        Answer<Void> answer =
                (invocation) -> {
                    countDownLatch.countDown();
                    return null;
                };
        doAnswer(answer).when(mockService).registerWebSource(captor.capture(), any(), any());

        doAnswer(
                        (invocation) -> {
                            ((OutcomeReceiver) invocation.getArgument(1))
                                    .onError(new SecurityException());
                            return null;
                        })
                .when(adIdManager)
                .getAdId(any(), any(OutcomeReceiver.class));

        measurementManager.registerWebSource(
                buildDefaultWebSourceRegistrationRequest(),
                /* executor = */ null,
                /* callback = */ null);

        assertThat(countDownLatch.await(TIMEOUT, TimeUnit.MILLISECONDS)).isTrue();
        expect.that(captor.getValue().isAdIdPermissionGranted()).isFalse();
        expect.that(captor.getValue().getAppPackageName()).isEqualTo(getPackageName());
        verify(mock(LogUtil.class)).w(anyString());
    }

    @Test
    public void testRegisterWebSource_adIdTimeOut_register() throws Exception {
        AdIdManager adIdManager = mock(AdIdManager.class);
        MeasurementCompatibleManager measurementManager =
                spy(MeasurementCompatibleManager.get(sContext, adIdManager));

        IMeasurementService mockService = mock(IMeasurementService.class);
        doReturn(mockService).when(measurementManager).getService();
        ArgumentCaptor<WebSourceRegistrationRequestInternal> captor =
                ArgumentCaptor.forClass(WebSourceRegistrationRequestInternal.class);
        CountDownLatch countDownLatch = new CountDownLatch(1);
        Answer<Void> answer =
                (invocation) -> {
                    countDownLatch.countDown();
                    return null;
                };
        doAnswer(answer).when(mockService).registerWebSource(captor.capture(), any(), any());

        doAnswer(
                        (invocation) -> {
                            Thread.sleep(AD_ID_TIMEOUT);
                            return null;
                        })
                .when(adIdManager)
                .getAdId(any(), any(OutcomeReceiver.class));

        measurementManager.registerWebSource(
                buildDefaultWebSourceRegistrationRequest(),
                /* executor = */ null,
                /* callback = */ null);

        assertThat(countDownLatch.await(TIMEOUT, TimeUnit.MILLISECONDS)).isTrue();
        expect.that(captor.getValue().isAdIdPermissionGranted()).isFalse();
        expect.that(captor.getValue().getAppPackageName()).isEqualTo(getPackageName());
    }

    @Test
    public void testRegisterWebTrigger_callingApp_expectedAttributionSource() throws Exception {
        MeasurementCompatibleManager mm = getMeasurementCompatibleManager();
        IMeasurementService mockService = mock(IMeasurementService.class);
        doReturn(mockService).when(mm).getService();
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

        assertThat(countDownLatch.await(TIMEOUT, TimeUnit.MILLISECONDS)).isTrue();
        assertThat(captor.getValue().getAppPackageName()).isNotNull();
        expect.that(captor.getValue().getAppPackageName()).isEqualTo(getPackageName());
    }

    @Test
    @RequiresSdkLevelAtLeastT
    public void testRegisterWebTrigger_callingSdk_expectedAttributionSource() throws Exception {
        MeasurementCompatibleManager mm =
                spy(MeasurementCompatibleManager.get(sSandboxedSdkContext));
        IMeasurementService mockService = mock(IMeasurementService.class);
        doReturn(mockService).when(mm).getService();
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

        assertThat(countDownLatch.await(TIMEOUT, TimeUnit.MILLISECONDS)).isTrue();
        assertThat(captor.getValue().getAppPackageName()).isNotNull();
        expect.that(captor.getValue().getAppPackageName()).isEqualTo(getPackageName());
    }

    @Test
    public void testRegisterWebTrigger_executorAndCallbackCalled() throws Exception {
        MeasurementCompatibleManager mm = getMeasurementCompatibleManager();
        CountDownLatch anyCountDownLatch = new CountDownLatch(1);

        mm.registerWebTrigger(
                buildDefaultWebTriggerRegistrationRequest(),
                CALLBACK_EXECUTOR,
                new OutcomeReceiver<>() {
                    @Override
                    public void onResult(@NonNull Object result) {
                        anyCountDownLatch.countDown();
                    }

                    @Override
                    public void onError(@NonNull Exception error) {
                        anyCountDownLatch.countDown();
                    }
                });

        assertThat(anyCountDownLatch.await(TIMEOUT, TimeUnit.MILLISECONDS)).isTrue();
    }

    @Test
    public void testRegisterWebTrigger_adIdEnabled_register() throws Exception {
        AdIdManager adIdManager = mock(AdIdManager.class);
        MeasurementCompatibleManager measurementManager =
                spy(MeasurementCompatibleManager.get(sContext, adIdManager));

        IMeasurementService mockService = mock(IMeasurementService.class);
        doReturn(mockService).when(measurementManager).getService();
        ArgumentCaptor<WebTriggerRegistrationRequestInternal> captor =
                ArgumentCaptor.forClass(WebTriggerRegistrationRequestInternal.class);
        CountDownLatch countDownLatch = new CountDownLatch(1);
        Answer<Void> answer =
                (invocation) -> {
                    countDownLatch.countDown();
                    return null;
                };
        doAnswer(answer).when(mockService).registerWebTrigger(captor.capture(), any(), any());

        doAnswer(
                        (invocation) -> {
                            ((OutcomeReceiver) invocation.getArgument(1))
                                    .onResult(new AdId(AD_ID, true));
                            return null;
                        })
                .when(adIdManager)
                .getAdId(any(), any(OutcomeReceiver.class));

        measurementManager.registerWebTrigger(
                buildDefaultWebTriggerRegistrationRequest(),
                /* executor = */ null,
                /* callback = */ null);

        assertThat(countDownLatch.await(TIMEOUT, TimeUnit.MILLISECONDS)).isTrue();
        assertThat(captor.getValue().isAdIdPermissionGranted()).isTrue();
        expect.that(captor.getValue().getAppPackageName()).isEqualTo(getPackageName());
        verify(mock(LogUtil.class), never()).w(anyString());
    }

    @Test
    public void testRegisterWebTrigger_adIdZeroOut_register() throws Exception {
        AdIdManager adIdManager = mock(AdIdManager.class);
        MeasurementCompatibleManager measurementManager =
                spy(MeasurementCompatibleManager.get(sContext, adIdManager));

        IMeasurementService mockService = mock(IMeasurementService.class);
        doReturn(mockService).when(measurementManager).getService();
        ArgumentCaptor<WebTriggerRegistrationRequestInternal> captor =
                ArgumentCaptor.forClass(WebTriggerRegistrationRequestInternal.class);
        CountDownLatch countDownLatch = new CountDownLatch(1);
        Answer<Void> answer =
                (invocation) -> {
                    countDownLatch.countDown();
                    return null;
                };
        doAnswer(answer).when(mockService).registerWebTrigger(captor.capture(), any(), any());

        doAnswer(
                        (invocation) -> {
                            ((OutcomeReceiver) invocation.getArgument(1))
                                    .onResult(new AdId(AdId.ZERO_OUT, true));
                            return null;
                        })
                .when(adIdManager)
                .getAdId(any(), any(OutcomeReceiver.class));

        measurementManager.registerWebTrigger(
                buildDefaultWebTriggerRegistrationRequest(),
                /* executor = */ null,
                /* callback = */ null);

        assertThat(countDownLatch.await(TIMEOUT, TimeUnit.MILLISECONDS)).isTrue();
        expect.that(captor.getValue().isAdIdPermissionGranted()).isFalse();
        expect.that(captor.getValue().getAppPackageName()).isEqualTo(getPackageName());
    }

    @Test
    public void testRegisterWebTrigger_adIdDisabled_register() throws Exception {
        AdIdManager adIdManager = mock(AdIdManager.class);
        MeasurementCompatibleManager measurementManager =
                spy(MeasurementCompatibleManager.get(sContext, adIdManager));

        IMeasurementService mockService = mock(IMeasurementService.class);
        doReturn(mockService).when(measurementManager).getService();
        ArgumentCaptor<WebTriggerRegistrationRequestInternal> captor =
                ArgumentCaptor.forClass(WebTriggerRegistrationRequestInternal.class);
        CountDownLatch countDownLatch = new CountDownLatch(1);
        Answer<Void> answer =
                (invocation) -> {
                    countDownLatch.countDown();
                    return null;
                };
        doAnswer(answer).when(mockService).registerWebTrigger(captor.capture(), any(), any());

        doAnswer(
                        (invocation) -> {
                            ((OutcomeReceiver) invocation.getArgument(1))
                                    .onError(new SecurityException());
                            return null;
                        })
                .when(adIdManager)
                .getAdId(any(), any(OutcomeReceiver.class));

        measurementManager.registerWebTrigger(
                buildDefaultWebTriggerRegistrationRequest(),
                /* executor = */ null,
                /* callback = */ null);

        assertThat(countDownLatch.await(TIMEOUT, TimeUnit.MILLISECONDS)).isTrue();
        expect.that(captor.getValue().isAdIdPermissionGranted()).isFalse();
        expect.that(captor.getValue().getAppPackageName()).isEqualTo(getPackageName());
        verify(mock(LogUtil.class)).w(anyString());
    }

    @Test
    public void testRegisterWebTrigger_adIdTimeOut_register() throws Exception {
        AdIdManager adIdManager = mock(AdIdManager.class);
        MeasurementCompatibleManager measurementManager =
                spy(MeasurementCompatibleManager.get(sContext, adIdManager));

        IMeasurementService mockService = mock(IMeasurementService.class);
        doReturn(mockService).when(measurementManager).getService();
        ArgumentCaptor<WebTriggerRegistrationRequestInternal> captor =
                ArgumentCaptor.forClass(WebTriggerRegistrationRequestInternal.class);
        CountDownLatch countDownLatch = new CountDownLatch(1);
        Answer<Void> answer =
                (invocation) -> {
                    countDownLatch.countDown();
                    return null;
                };
        doAnswer(answer).when(mockService).registerWebTrigger(captor.capture(), any(), any());

        doAnswer(
                        (invocation) -> {
                            Thread.sleep(AD_ID_TIMEOUT);
                            return null;
                        })
                .when(adIdManager)
                .getAdId(any(), any(OutcomeReceiver.class));

        measurementManager.registerWebTrigger(
                buildDefaultWebTriggerRegistrationRequest(),
                /* executor = */ null,
                /* callback = */ null);

        assertThat(countDownLatch.await(TIMEOUT, TimeUnit.MILLISECONDS)).isTrue();
        expect.that(captor.getValue().isAdIdPermissionGranted()).isFalse();
        expect.that(captor.getValue().getAppPackageName()).isEqualTo(getPackageName());
    }

    @Test
    public void testRegisterTrigger_callingApp_expectedAttributionSource() throws Exception {
        MeasurementCompatibleManager mm = getMeasurementCompatibleManager();
        IMeasurementService mockService = mock(IMeasurementService.class);
        doReturn(mockService).when(mm).getService();
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

        assertThat(countDownLatch.await(TIMEOUT, TimeUnit.MILLISECONDS)).isTrue();
        assertThat(captor.getValue().getAppPackageName()).isNotNull();
        expect.that(captor.getValue().getAppPackageName()).isEqualTo(getPackageName());
    }

    @Test
    @RequiresSdkLevelAtLeastT
    public void testRegisterTrigger_callingSdk_expectedAttributionSource() throws Exception {
        MeasurementCompatibleManager mm =
                spy(MeasurementCompatibleManager.get(sSandboxedSdkContext));
        IMeasurementService mockService = mock(IMeasurementService.class);
        doReturn(mockService).when(mm).getService();
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

        assertThat(countDownLatch.await(TIMEOUT, TimeUnit.MILLISECONDS)).isTrue();
        assertThat(captor.getValue().getAppPackageName()).isNotNull();
        expect.that(captor.getValue().getAppPackageName()).isEqualTo(getPackageName());
    }

    @Test
    public void testRegisterTrigger_executorAndCallbackCalled() throws Exception {
        MeasurementCompatibleManager mm = getMeasurementCompatibleManager();
        CountDownLatch anyCountDownLatch = new CountDownLatch(1);

        mm.registerTrigger(
                Uri.parse("https://registration-trigger"),
                CALLBACK_EXECUTOR,
                new OutcomeReceiver<>() {
                    @Override
                    public void onResult(@NonNull Object result) {
                        anyCountDownLatch.countDown();
                    }

                    @Override
                    public void onError(@NonNull Exception error) {
                        anyCountDownLatch.countDown();
                    }
                });

        assertThat(anyCountDownLatch.await(TIMEOUT, TimeUnit.MILLISECONDS)).isTrue();
    }

    @Test
    public void testRegisterTrigger_adIdEnabled_register() throws Exception {
        AdIdManager adIdManager = mock(AdIdManager.class);
        MeasurementCompatibleManager measurementManager =
                spy(MeasurementCompatibleManager.get(sContext, adIdManager));

        IMeasurementService mockService = mock(IMeasurementService.class);
        doReturn(mockService).when(measurementManager).getService();
        ArgumentCaptor<RegistrationRequest> captor =
                ArgumentCaptor.forClass(RegistrationRequest.class);
        CountDownLatch countDownLatch = new CountDownLatch(1);
        Answer<Void> answer =
                (invocation) -> {
                    countDownLatch.countDown();
                    return null;
                };
        doAnswer(answer).when(mockService).register(captor.capture(), any(), any());

        doAnswer(
                        (invocation) -> {
                            ((OutcomeReceiver) invocation.getArgument(1))
                                    .onResult(new AdId(AD_ID, true));
                            return null;
                        })
                .when(adIdManager)
                .getAdId(any(), any(OutcomeReceiver.class));

        measurementManager.registerTrigger(
                Uri.parse("https://example.com"), /* executor = */ null, /* callback = */ null);

        assertThat(countDownLatch.await(TIMEOUT, TimeUnit.MILLISECONDS)).isTrue();
        assertThat(captor.getValue().isAdIdPermissionGranted()).isTrue();
        expect.that(captor.getValue().getAppPackageName()).isEqualTo(getPackageName());
        verify(mock(LogUtil.class), never()).w(anyString());
    }

    @Test
    public void testRegisterTrigger_adIdZeroOut_register() throws Exception {
        AdIdManager adIdManager = mock(AdIdManager.class);
        MeasurementCompatibleManager measurementManager =
                spy(MeasurementCompatibleManager.get(sContext, adIdManager));

        IMeasurementService mockService = mock(IMeasurementService.class);
        doReturn(mockService).when(measurementManager).getService();
        ArgumentCaptor<RegistrationRequest> captor =
                ArgumentCaptor.forClass(RegistrationRequest.class);
        CountDownLatch countDownLatch = new CountDownLatch(1);
        Answer<Void> answer =
                (invocation) -> {
                    countDownLatch.countDown();
                    return null;
                };
        doAnswer(answer).when(mockService).register(captor.capture(), any(), any());

        doAnswer(
                        (invocation) -> {
                            ((OutcomeReceiver) invocation.getArgument(1))
                                    .onResult(new AdId(AdId.ZERO_OUT, true));
                            return null;
                        })
                .when(adIdManager)
                .getAdId(any(), any(OutcomeReceiver.class));

        measurementManager.registerTrigger(
                Uri.parse("https://example.com"), /* executor = */ null, /* callback = */ null);

        assertThat(countDownLatch.await(TIMEOUT, TimeUnit.MILLISECONDS)).isTrue();
        expect.that(captor.getValue().isAdIdPermissionGranted()).isFalse();
        expect.that(captor.getValue().getAppPackageName()).isEqualTo(getPackageName());
    }

    @Test
    public void testRegisterTrigger_adIdDisabled_register() throws Exception {
        AdIdManager adIdManager = mock(AdIdManager.class);
        MeasurementCompatibleManager measurementManager =
                spy(MeasurementCompatibleManager.get(sContext, adIdManager));

        IMeasurementService mockService = mock(IMeasurementService.class);
        doReturn(mockService).when(measurementManager).getService();
        ArgumentCaptor<RegistrationRequest> captor =
                ArgumentCaptor.forClass(RegistrationRequest.class);
        CountDownLatch countDownLatch = new CountDownLatch(1);
        Answer<Void> answer =
                (invocation) -> {
                    countDownLatch.countDown();
                    return null;
                };
        doAnswer(answer).when(mockService).register(captor.capture(), any(), any());

        doAnswer(
                        (invocation) -> {
                            ((OutcomeReceiver) invocation.getArgument(1))
                                    .onError(new SecurityException());
                            return null;
                        })
                .when(adIdManager)
                .getAdId(any(), any(OutcomeReceiver.class));

        measurementManager.registerTrigger(
                Uri.parse("https://example.com"), /* executor = */ null, /* callback = */ null);

        assertThat(countDownLatch.await(TIMEOUT, TimeUnit.MILLISECONDS)).isTrue();
        expect.that(captor.getValue().isAdIdPermissionGranted()).isFalse();
        expect.that(captor.getValue().getAppPackageName()).isEqualTo(getPackageName());
        verify(mock(LogUtil.class)).w(anyString());
    }

    @Test
    public void testRegisterTrigger_adIdTimeOut_register() throws Exception {
        AdIdManager adIdManager = mock(AdIdManager.class);
        MeasurementCompatibleManager measurementManager =
                spy(MeasurementCompatibleManager.get(sContext, adIdManager));

        IMeasurementService mockService = mock(IMeasurementService.class);
        doReturn(mockService).when(measurementManager).getService();
        ArgumentCaptor<RegistrationRequest> captor =
                ArgumentCaptor.forClass(RegistrationRequest.class);
        CountDownLatch countDownLatch = new CountDownLatch(1);
        Answer<Void> answer =
                (invocation) -> {
                    countDownLatch.countDown();
                    return null;
                };
        doAnswer(answer).when(mockService).register(captor.capture(), any(), any());

        doAnswer(
                        (invocation) -> {
                            Thread.sleep(AD_ID_TIMEOUT);
                            return null;
                        })
                .when(adIdManager)
                .getAdId(any(), any(OutcomeReceiver.class));

        measurementManager.registerTrigger(
                Uri.parse("https://example.com"), /* executor = */ null, /* callback = */ null);

        assertThat(countDownLatch.await(TIMEOUT, TimeUnit.MILLISECONDS)).isTrue();
        expect.that(captor.getValue().isAdIdPermissionGranted()).isFalse();
        expect.that(captor.getValue().getAppPackageName()).isEqualTo(getPackageName());
    }

    @Test
    public void testDeleteRegistrations_callingApp_expectedAttributionSource() throws Exception {
        MeasurementCompatibleManager mm = getMeasurementCompatibleManager();
        IMeasurementService mockService = mock(IMeasurementService.class);
        doReturn(mockService).when(mm).getService();
        ArgumentCaptor<DeletionParam> captor = ArgumentCaptor.forClass(DeletionParam.class);
        doNothing().when(mockService).deleteRegistrations(captor.capture(), any(), any());

        mm.deleteRegistrations(
                new DeletionRequest.Builder().build(),
                CALLBACK_EXECUTOR,
                i -> new CompletableFuture<>().complete(i));

        assertThat(captor.getValue().getAppPackageName()).isNotNull();
        expect.that(captor.getValue().getAppPackageName()).isEqualTo(getPackageName());
    }

    @Test
    @RequiresSdkLevelAtLeastT
    public void testDeleteRegistrations_callingSdk_expectedAttributionSource() throws Exception {
        MeasurementCompatibleManager mm =
                spy(MeasurementCompatibleManager.get(sSandboxedSdkContext));
        IMeasurementService mockService = mock(IMeasurementService.class);
        doReturn(mockService).when(mm).getService();
        ArgumentCaptor<DeletionParam> captor = ArgumentCaptor.forClass(DeletionParam.class);
        doNothing().when(mockService).deleteRegistrations(captor.capture(), any(), any());

        mm.deleteRegistrations(
                new DeletionRequest.Builder().build(),
                CALLBACK_EXECUTOR,
                i -> new CompletableFuture<>().complete(i));

        assertThat(captor.getValue().getAppPackageName()).isNotNull();
        expect.that(captor.getValue().getAppPackageName()).isEqualTo(getPackageName());
    }

    @Test
    public void testDeleteRegistrations_nullExecutor_throwNullPointerException() {
        MeasurementCompatibleManager mm = getMeasurementCompatibleManager();

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
        MeasurementCompatibleManager mm = getMeasurementCompatibleManager();

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
        MeasurementCompatibleManager mm = getMeasurementCompatibleManager();
        int response = callMeasurementApiStatus(mm);
        assertThat(response).isEqualTo(MeasurementManager.MEASUREMENT_API_STATE_ENABLED);
    }

    @Test
    public void testGetMeasurementApiStatus_nullExecutor_throwNullPointerException() {
        MeasurementCompatibleManager mm = getMeasurementCompatibleManager();
        overrideConsentManagerDebugMode();

        assertThrows(
                NullPointerException.class,
                () -> mm.getMeasurementApiStatus(/* executor */ null, result -> {}));
    }

    @Test
    public void testGetMeasurementApiStatus_nullCallback_throwNullPointerException() {
        MeasurementCompatibleManager mm = getMeasurementCompatibleManager();
        overrideConsentManagerDebugMode();

        assertThrows(
                NullPointerException.class,
                () -> mm.getMeasurementApiStatus(CALLBACK_EXECUTOR, /* callback */ null));
    }

    @Test
    public void testGetMeasurementApiStatus_getServiceThrowsIllegalState_returnDisabled()
            throws Exception {
        MeasurementCompatibleManager mm = getMeasurementCompatibleManager();
        doThrow(new IllegalStateException()).when(mm).getService();
        int response = callMeasurementApiStatus(mm);
        assertThat(response).isEqualTo(MeasurementManager.MEASUREMENT_API_STATE_DISABLED);
    }

    @Test
    public void testGetMeasurementApiStatus_getServiceThrowsRuntimeException_propagateOnError()
            throws Exception {
        MeasurementCompatibleManager mm = getMeasurementCompatibleManager();
        doThrow(new RuntimeException()).when(mm).getService();
        CompletableFuture<Exception> future = new CompletableFuture<>();

        mm.getMeasurementApiStatus(
                CALLBACK_EXECUTOR,
                new OutcomeReceiver<>() {
                    @Override
                    public void onResult(Integer result) {
                        Assert.fail();
                    }

                    @Override
                    public void onError(Exception error) {
                        future.complete(error);
                    }
                });

        Exception exception = future.get();
        assertThat(exception).isInstanceOf(RuntimeException.class);
    }

    @Test
    public void testGetMeasurementApiStatus_remoteException_returnDisabled() throws Exception {
        MeasurementCompatibleManager mm = getMeasurementCompatibleManager();
        IMeasurementService mockMeasurementService = mock(IMeasurementService.class);
        doReturn(mockMeasurementService).when(mm).getService();
        doThrow(new RemoteException())
                .when(mockMeasurementService)
                .getMeasurementApiStatus(any(), any(), any());
        int response = callMeasurementApiStatus(mm);
        assertThat(response).isEqualTo(MeasurementManager.MEASUREMENT_API_STATE_DISABLED);
    }

    @Test
    public void testGetMeasurementApiStatus_RuntimeException_propagateOnError() throws Exception {
        MeasurementCompatibleManager mm = getMeasurementCompatibleManager();
        IMeasurementService mockMeasurementService = mock(IMeasurementService.class);
        doReturn(mockMeasurementService).when(mm).getService();
        doThrow(new RuntimeException())
                .when(mockMeasurementService)
                .getMeasurementApiStatus(any(), any(), any());

        CompletableFuture<Exception> future = new CompletableFuture<>();
        mm.getMeasurementApiStatus(
                CALLBACK_EXECUTOR,
                new OutcomeReceiver<>() {
                    @Override
                    public void onResult(Integer result) {
                        Assert.fail();
                    }

                    @Override
                    public void onError(Exception error) {
                        future.complete(error);
                    }
                });

        Exception exception = future.get();
        assertThat(exception).isInstanceOf(RuntimeException.class);
    }

    @Test
    public void testRegisterSourceMulti_adIdDisabled_register() throws Exception {
        // Setup
        AdIdManager adIdManager = mock(AdIdManager.class);
        MeasurementCompatibleManager measurementManager =
                spy(MeasurementCompatibleManager.get(sContext, adIdManager));

        IMeasurementService mockService = mock(IMeasurementService.class);
        doReturn(mockService).when(measurementManager).getService();
        ArgumentCaptor<SourceRegistrationRequestInternal> captor =
                ArgumentCaptor.forClass(SourceRegistrationRequestInternal.class);
        CountDownLatch countDownLatch = new CountDownLatch(1);
        Answer<Void> serviceResponseAnswer =
                (invocation) -> {
                    countDownLatch.countDown();
                    return null;
                };
        doAnswer(serviceResponseAnswer)
                .when(mockService)
                .registerSource(captor.capture(), any(), any());
        Answer adIdAnswer =
                (invocation) -> {
                    ((OutcomeReceiver) invocation.getArgument(1)).onError(new SecurityException());
                    return null;
                };
        doAnswer(adIdAnswer).when(adIdManager).getAdId(any(), any(OutcomeReceiver.class));

        measurementManager.registerSource(
                buildDefaultAppSourcesRegistrationRequest(),
                /* executor = */ null,
                /* callback = */ null);

        assertThat(countDownLatch.await(TIMEOUT, TimeUnit.MILLISECONDS)).isTrue();
        expect.that(captor.getValue().getAdIdValue()).isNull();
        expect.that(captor.getValue().getAppPackageName()).isEqualTo(getPackageName());
    }

    @Test
    public void testRegisterSourceMulti_adIdTimeOut_register() throws Exception {
        AdIdManager adIdManager = mock(AdIdManager.class);
        MeasurementCompatibleManager measurementManager =
                spy(MeasurementCompatibleManager.get(sContext, adIdManager));

        IMeasurementService mockService = mock(IMeasurementService.class);
        doReturn(mockService).when(measurementManager).getService();
        ArgumentCaptor<SourceRegistrationRequestInternal> captor =
                ArgumentCaptor.forClass(SourceRegistrationRequestInternal.class);
        CountDownLatch countDownLatch = new CountDownLatch(1);
        Answer<Void> answer =
                (invocation) -> {
                    countDownLatch.countDown();
                    return null;
                };
        doAnswer(answer).when(mockService).registerSource(captor.capture(), any(), any());

        Answer adIdAnswer =
                (invocation) -> {
                    Thread.sleep(AD_ID_TIMEOUT);
                    return null;
                };
        doAnswer(adIdAnswer).when(adIdManager).getAdId(any(), any(OutcomeReceiver.class));

        measurementManager.registerSource(
                buildDefaultAppSourcesRegistrationRequest(),
                /* executor = */ null,
                /* callback = */ null);

        assertThat(countDownLatch.await(TIMEOUT, TimeUnit.MILLISECONDS)).isTrue();
        expect.that(captor.getValue().getAdIdValue()).isNull();
        expect.that(captor.getValue().getAppPackageName()).isEqualTo(getPackageName());
    }

    @Test
    public void testRegisterSourceMulti_adIdEnabled_register() throws Exception {
        AdIdManager adIdManager = mock(AdIdManager.class);
        MeasurementCompatibleManager measurementManager =
                spy(MeasurementCompatibleManager.get(sContext, adIdManager));

        IMeasurementService mockService = mock(IMeasurementService.class);
        doReturn(mockService).when(measurementManager).getService();
        ArgumentCaptor<SourceRegistrationRequestInternal> captor =
                ArgumentCaptor.forClass(SourceRegistrationRequestInternal.class);
        CountDownLatch countDownLatch = new CountDownLatch(1);
        Answer<Void> answer =
                (invocation) -> {
                    countDownLatch.countDown();
                    return null;
                };
        doAnswer(answer).when(mockService).registerSource(captor.capture(), any(), any());

        Answer adIdAnswer =
                (invocation) -> {
                    ((OutcomeReceiver) invocation.getArgument(1)).onResult(new AdId(AD_ID, true));
                    return null;
                };
        doAnswer(adIdAnswer).when(adIdManager).getAdId(any(), any(OutcomeReceiver.class));

        measurementManager.registerSource(
                buildDefaultAppSourcesRegistrationRequest(),
                /* executor = */ null,
                /* callback = */ null);

        assertThat(countDownLatch.await(TIMEOUT, TimeUnit.MILLISECONDS)).isTrue();
        expect.that(captor.getValue().getAdIdValue()).isEqualTo(AD_ID);
        expect.that(captor.getValue().getAppPackageName()).isEqualTo(getPackageName());
    }

    @Test
    public void testRegisterSourceMulti_adIdZeroOut_register() throws Exception {
        AdIdManager adIdManager = mock(AdIdManager.class);
        MeasurementCompatibleManager measurementManager =
                spy(MeasurementCompatibleManager.get(sContext, adIdManager));

        IMeasurementService mockService = mock(IMeasurementService.class);
        doReturn(mockService).when(measurementManager).getService();
        ArgumentCaptor<SourceRegistrationRequestInternal> captor =
                ArgumentCaptor.forClass(SourceRegistrationRequestInternal.class);
        CountDownLatch countDownLatch = new CountDownLatch(1);
        Answer<Void> answer =
                (invocation) -> {
                    countDownLatch.countDown();
                    return null;
                };
        doAnswer(answer).when(mockService).registerSource(captor.capture(), any(), any());

        Answer adIdAnswer =
                (invocation) -> {
                    ((OutcomeReceiver) invocation.getArgument(1))
                            .onResult(new AdId(AdId.ZERO_OUT, true));
                    return null;
                };
        doAnswer(adIdAnswer).when(adIdManager).getAdId(any(), any(OutcomeReceiver.class));

        measurementManager.registerSource(
                buildDefaultAppSourcesRegistrationRequest(),
                /* executor = */ null,
                /* callback = */ null);

        assertThat(countDownLatch.await(TIMEOUT, TimeUnit.MILLISECONDS)).isTrue();
        expect.that(captor.getValue().getAdIdValue()).isNull();
        expect.that(captor.getValue().getAppPackageName()).isEqualTo(getPackageName());
    }

    @Test
    public void testRegisterSourceMulti_BindServiceFailure_propagateErrorCallback() {
        MeasurementCompatibleManager measurementManager = getMeasurementCompatibleManager();
        doThrow(new IllegalStateException()).when(measurementManager).getService();
        OutcomeReceiver callback = mock(OutcomeReceiver.class);

        measurementManager.registerSource(
                buildDefaultAppSourcesRegistrationRequest(),
                /* executor = */ CALLBACK_EXECUTOR,
                /* callback = */ callback);

        verify(callback, after(CALLBACK_TIMEOUT)).onError(any());
    }

    @Test
    public void
            testRegisterSourceMulti_callbackProvidedWithoutExecutor_throwsIllegalArgException() {
        RvcGuardUberHackPlusPlus.assertRegisterSourceFromSourceRegistrationRequestThrows();
    }

    @Test
    public void testRegisterSourceMulti_callingApp_expectedAttributionSource() throws Exception {
        MeasurementCompatibleManager mm = getMeasurementCompatibleManager();
        IMeasurementService mockService = mock(IMeasurementService.class);
        doReturn(mockService).when(mm).getService();
        ArgumentCaptor<SourceRegistrationRequestInternal> captor =
                ArgumentCaptor.forClass(SourceRegistrationRequestInternal.class);
        CountDownLatch countDownLatch = new CountDownLatch(1);
        Answer<Void> answer =
                (invocation) -> {
                    countDownLatch.countDown();
                    return null;
                };
        doAnswer(answer).when(mockService).registerSource(captor.capture(), any(), any());

        mm.registerSource(
                buildDefaultAppSourcesRegistrationRequest(),
                /* executor = */ null,
                /* callback = */ null);

        assertThat(countDownLatch.await(TIMEOUT, TimeUnit.MILLISECONDS)).isTrue();
        expect.that(captor.getValue().getAppPackageName()).isEqualTo(getPackageName());
    }

    @Test
    public void testRegisterSource_callbackProvidedWithoutExecutor_throwsIllegalArgException() {
        RvcGuardUberHackPlusPlus.assertRegisterSourceFromUriAndInputEventThrows();
    }

    @Test
    public void testRegisterWebSource_callbackProvidedWithoutExecutor_throwsIllegalArgException() {
        RvcGuardUberHackPlusPlus.assertRegisterWebSourceThrows();
    }

    @Test
    public void testRegisterWebTrigger_callbackProvidedWithoutExecutor_throwsIllegalArgException() {
        RvcGuardUberHackPlusPlus.assertRegisterWebTriggerThrows();
    }

    private int callMeasurementApiStatus(MeasurementCompatibleManager mm) throws Exception {
        overrideConsentManagerDebugMode();
        CompletableFuture<Integer> future = new CompletableFuture<>();
        OutcomeReceiver<Integer, Exception> callback =
                new OutcomeReceiver<>() {
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
        return future.get();
    }

    // Override the Consent Manager behaviour - Consent Given
    private void overrideConsentManagerDebugMode() {
        flags.setDebugFlag(KEY_CONSENT_MANAGER_DEBUG_MODE, true);
    }

    @SuppressWarnings("VisibleForTests") // TODO(b/343741206): Remove suppress warning once fixed.
    /**
     * "Clever" workaround to avoid {@code java.lang.NoClassDefFoundError} failures when running on
     * Android RVC without using the {@code SafeAndroidJUnitRunner} "cleverer" runner.
     */
    private static final class RvcGuardUberHackPlusPlus {

        private static void assertRegisterSourceFromSourceRegistrationRequestThrows() {
            MeasurementCompatibleManager measurementManager = getMeasurementCompatibleManager();
            doThrow(new IllegalStateException()).when(measurementManager).getService();
            OutcomeReceiver callback = mock(OutcomeReceiver.class);

            assertThrows(
                    IllegalArgumentException.class,
                    () ->
                            measurementManager.registerSource(
                                    buildDefaultAppSourcesRegistrationRequest(),
                                    /* executor= */ null,
                                    /* callback= */ callback));
        }

        private static void assertRegisterSourceFromUriAndInputEventThrows() {
            MeasurementCompatibleManager measurementManager = getMeasurementCompatibleManager();
            doThrow(new IllegalStateException()).when(measurementManager).getService();
            OutcomeReceiver callback = mock(OutcomeReceiver.class);

            assertThrows(
                    IllegalArgumentException.class,
                    () ->
                            measurementManager.registerSource(
                                    Uri.parse("https://example.com"),
                                    /* inputEvent */ null,
                                    /* executor= */ null,
                                    /* callback= */ callback));
        }

        private static void assertRegisterWebSourceThrows() {
            MeasurementCompatibleManager measurementManager = getMeasurementCompatibleManager();
            doThrow(new IllegalStateException()).when(measurementManager).getService();
            OutcomeReceiver callback = mock(OutcomeReceiver.class);

            assertThrows(
                    IllegalArgumentException.class,
                    () ->
                            measurementManager.registerWebSource(
                                    buildDefaultWebSourceRegistrationRequest(),
                                    /* executor= */ null,
                                    /* callback= */ callback));
        }

        private static void assertRegisterWebTriggerThrows() {
            MeasurementCompatibleManager measurementManager = getMeasurementCompatibleManager();
            doThrow(new IllegalStateException()).when(measurementManager).getService();
            OutcomeReceiver callback = mock(OutcomeReceiver.class);

            assertThrows(
                    IllegalArgumentException.class,
                    () ->
                            measurementManager.registerWebTrigger(
                                    buildDefaultWebTriggerRegistrationRequest(),
                                    /* executor= */ null,
                                    /* callback= */ callback));
        }

        private RvcGuardUberHackPlusPlus() {
            throw new UnsupportedOperationException("Contains only static methods");
        }
    }
}

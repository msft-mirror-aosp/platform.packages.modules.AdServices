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

package android.app.sdksandbox;

import static android.app.sdksandbox.SdkSandboxManager.LOAD_SDK_NOT_FOUND;
import static android.app.sdksandbox.SdkSandboxManager.REQUEST_SURFACE_PACKAGE_INTERNAL_ERROR;
import static android.app.sdksandbox.SdkSandboxManager.SEND_DATA_INTERNAL_ERROR;

import static com.google.common.truth.Truth.assertThat;

import android.app.sdksandbox.testutils.FakeOutcomeReceiver;
import android.content.Context;
import android.content.pm.SharedLibraryInfo;
import android.os.Binder;
import android.os.Bundle;
import android.os.IBinder;
import android.os.OutcomeReceiver;

import androidx.test.platform.app.InstrumentationRegistry;

import org.junit.Before;
import org.junit.Test;
import org.junit.runner.RunWith;
import org.junit.runners.JUnit4;
import org.mockito.ArgumentCaptor;
import org.mockito.Mockito;

import java.util.List;

/** Tests {@link SdkSandboxManager} APIs. */
@RunWith(JUnit4.class)
public class SdkSandboxManagerUnitTest {

    private SdkSandboxManager mSdkSandboxManager;
    private ISdkSandboxManager mBinder;
    private Context mContext;
    private static final String SDK_NAME = "com.android.codeproviderresources";
    private static final String ERROR_MSG = "Error";

    @Before
    public void setup() {
        mContext = InstrumentationRegistry.getInstrumentation().getContext();

        mBinder = Mockito.mock(ISdkSandboxManager.class);
        mSdkSandboxManager = new SdkSandboxManager(mContext, mBinder);
    }

    @Test
    public void testGetSdkSandboxState() {
        assertThat(SdkSandboxManager.getSdkSandboxState())
                .isEqualTo(SdkSandboxManager.SDK_SANDBOX_STATE_ENABLED_PROCESS_ISOLATION);
    }

    @Test
    public void testLoadSdkSuccess() throws Exception {
        final Bundle params = new Bundle();

        OutcomeReceiver<LoadSdkResponse, LoadSdkException> outcomeReceiver =
                Mockito.spy(new FakeOutcomeReceiver<>());
        mSdkSandboxManager.loadSdk(SDK_NAME, params, Runnable::run, outcomeReceiver);

        ArgumentCaptor<ILoadSdkCallback> callbackArgumentCaptor =
                ArgumentCaptor.forClass(ILoadSdkCallback.class);
        Mockito.verify(mBinder)
                .loadSdk(
                        Mockito.eq(mContext.getPackageName()),
                        Mockito.eq(SDK_NAME),
                        Mockito.anyLong(),
                        Mockito.eq(params),
                        callbackArgumentCaptor.capture());

        // Simulate the success callback
        final Bundle extraInfo = new Bundle();
        callbackArgumentCaptor.getValue().onLoadSdkSuccess(extraInfo);
        ArgumentCaptor<LoadSdkResponse> responseCapture =
                ArgumentCaptor.forClass(LoadSdkResponse.class);
        Mockito.verify(outcomeReceiver).onResult(responseCapture.capture());

        assertThat(responseCapture.getValue().getExtraInformation()).isEqualTo(extraInfo);
    }

    @Test
    public void testLoadSdkFailed() throws Exception {
        final Bundle params = new Bundle();

        OutcomeReceiver<LoadSdkResponse, LoadSdkException> outcomeReceiver =
                Mockito.spy(new FakeOutcomeReceiver<>());
        mSdkSandboxManager.loadSdk(SDK_NAME, params, Runnable::run, outcomeReceiver);

        ArgumentCaptor<ILoadSdkCallback> callbackArgumentCaptor =
                ArgumentCaptor.forClass(ILoadSdkCallback.class);
        Mockito.verify(mBinder)
                .loadSdk(
                        Mockito.eq(mContext.getPackageName()),
                        Mockito.eq(SDK_NAME),
                        Mockito.anyLong(),
                        Mockito.eq(params),
                        callbackArgumentCaptor.capture());

        // Simulate the error callback
        callbackArgumentCaptor.getValue().onLoadSdkFailure(LOAD_SDK_NOT_FOUND, ERROR_MSG);
        ArgumentCaptor<LoadSdkException> exceptionCapture =
                ArgumentCaptor.forClass(LoadSdkException.class);
        Mockito.verify(outcomeReceiver).onError(exceptionCapture.capture());
        final LoadSdkException exception = exceptionCapture.getValue();

        assertThat(exception.getLoadSdkErrorCode()).isEqualTo(LOAD_SDK_NOT_FOUND);
        assertThat(exception.getMessage()).isEqualTo(ERROR_MSG);
    }

    @Test
    public void testGetLoadedSdkLibrariesInfo() throws Exception {
        List<SharedLibraryInfo> sharedLibraries = List.of();
        Mockito.when(mBinder.getLoadedSdkLibrariesInfo(Mockito.anyString(), Mockito.anyLong()))
                .thenReturn(sharedLibraries);

        assertThat(mSdkSandboxManager.getLoadedSdkLibrariesInfo()).isEqualTo(sharedLibraries);
        Mockito.verify(mBinder)
                .getLoadedSdkLibrariesInfo(
                        Mockito.eq(mContext.getPackageName()), Mockito.anyLong());
    }

    @Test
    public void testUnloadSdk() throws Exception {
        mSdkSandboxManager.unloadSdk(SDK_NAME);
        Mockito.verify(mBinder).unloadSdk(mContext.getPackageName(), SDK_NAME);
    }

    @Test
    public void testRequestSurfacePackageSuccess() throws Exception {
        final Bundle params = new Bundle();
        final int displayId = 1;
        final int width = 50;
        final int height = 50;

        final IBinder hostToken = new Binder();
        OutcomeReceiver<RequestSurfacePackageResponse, RequestSurfacePackageException>
                outcomeReceiver = Mockito.spy(new FakeOutcomeReceiver<>());
        mSdkSandboxManager.requestSurfacePackage(
                SDK_NAME,
                displayId,
                width,
                height,
                hostToken,
                params,
                Runnable::run,
                outcomeReceiver);

        ArgumentCaptor<IRequestSurfacePackageCallback> callbackArgumentCaptor =
                ArgumentCaptor.forClass(IRequestSurfacePackageCallback.class);
        Mockito.verify(mBinder)
                .requestSurfacePackage(
                        Mockito.eq(mContext.getPackageName()),
                        Mockito.eq(SDK_NAME),
                        Mockito.eq(hostToken),
                        Mockito.eq(displayId),
                        Mockito.eq(width),
                        Mockito.eq(height),
                        Mockito.anyLong(),
                        Mockito.eq(params),
                        callbackArgumentCaptor.capture());

        // Simulate the success callback
        final int surfacePackageId = 0;
        final Bundle extraInfo = new Bundle();
        callbackArgumentCaptor.getValue().onSurfacePackageReady(null, surfacePackageId, extraInfo);
        ArgumentCaptor<RequestSurfacePackageResponse> responseCapture =
                ArgumentCaptor.forClass(RequestSurfacePackageResponse.class);
        Mockito.verify(outcomeReceiver).onResult(responseCapture.capture());

        final RequestSurfacePackageResponse response = responseCapture.getValue();
        assertThat(response.getSurfacePackage()).isEqualTo(null);
        assertThat(response.getExtraInformation()).isEqualTo(extraInfo);
    }

    @Test
    public void testRequestSurfacePackageFailed() throws Exception {
        final Bundle params = new Bundle();
        final int displayId = 1;
        final int width = 50;
        final int height = 50;

        final IBinder hostToken = new Binder();
        OutcomeReceiver<RequestSurfacePackageResponse, RequestSurfacePackageException>
                outcomeReceiver = Mockito.spy(new FakeOutcomeReceiver<>());
        mSdkSandboxManager.requestSurfacePackage(
                SDK_NAME,
                displayId,
                width,
                height,
                hostToken,
                params,
                Runnable::run,
                outcomeReceiver);

        ArgumentCaptor<IRequestSurfacePackageCallback> callbackArgumentCaptor =
                ArgumentCaptor.forClass(IRequestSurfacePackageCallback.class);
        Mockito.verify(mBinder)
                .requestSurfacePackage(
                        Mockito.eq(mContext.getPackageName()),
                        Mockito.eq(SDK_NAME),
                        Mockito.eq(hostToken),
                        Mockito.eq(displayId),
                        Mockito.eq(width),
                        Mockito.eq(height),
                        Mockito.anyLong(),
                        Mockito.eq(params),
                        callbackArgumentCaptor.capture());

        // Simulate the error callback
        callbackArgumentCaptor
                .getValue()
                .onSurfacePackageError(REQUEST_SURFACE_PACKAGE_INTERNAL_ERROR, ERROR_MSG);
        ArgumentCaptor<RequestSurfacePackageException> responseCapture =
                ArgumentCaptor.forClass(RequestSurfacePackageException.class);
        Mockito.verify(outcomeReceiver).onError(responseCapture.capture());

        final RequestSurfacePackageException exception = responseCapture.getValue();
        assertThat(exception.getRequestSurfacePackageErrorCode())
                .isEqualTo(REQUEST_SURFACE_PACKAGE_INTERNAL_ERROR);
        assertThat(exception.getMessage()).isEqualTo(ERROR_MSG);
    }

    @Test
    public void testSendDataSuccess() throws Exception {
        final Bundle params = new Bundle();

        OutcomeReceiver<SendDataResponse, SendDataException> outcomeReceiver =
                Mockito.spy(new FakeOutcomeReceiver<>());
        mSdkSandboxManager.sendData(SDK_NAME, params, Runnable::run, outcomeReceiver);

        ArgumentCaptor<ISendDataCallback> callbackArgumentCaptor =
                ArgumentCaptor.forClass(ISendDataCallback.class);
        Mockito.verify(mBinder)
                .sendData(
                        Mockito.eq(mContext.getPackageName()),
                        Mockito.eq(SDK_NAME),
                        Mockito.eq(params),
                        callbackArgumentCaptor.capture());

        // Simulate the success callback
        final Bundle extraInfo = new Bundle();
        callbackArgumentCaptor.getValue().onSendDataSuccess(extraInfo);
        ArgumentCaptor<SendDataResponse> responseCapture =
                ArgumentCaptor.forClass(SendDataResponse.class);
        Mockito.verify(outcomeReceiver).onResult(responseCapture.capture());

        final SendDataResponse response = responseCapture.getValue();
        assertThat(response.getExtraInformation()).isEqualTo(extraInfo);
    }

    @Test
    public void testSendDataFailure() throws Exception {
        final Bundle params = new Bundle();

        OutcomeReceiver<SendDataResponse, SendDataException> outcomeReceiver =
                Mockito.spy(new FakeOutcomeReceiver<>());
        mSdkSandboxManager.sendData(SDK_NAME, params, Runnable::run, outcomeReceiver);

        ArgumentCaptor<ISendDataCallback> callbackArgumentCaptor =
                ArgumentCaptor.forClass(ISendDataCallback.class);
        Mockito.verify(mBinder)
                .sendData(
                        Mockito.eq(mContext.getPackageName()),
                        Mockito.eq(SDK_NAME),
                        Mockito.eq(params),
                        callbackArgumentCaptor.capture());

        // Simulate the error callback
        callbackArgumentCaptor.getValue().onSendDataError(SEND_DATA_INTERNAL_ERROR, ERROR_MSG);
        ArgumentCaptor<SendDataException> responseCapture =
                ArgumentCaptor.forClass(SendDataException.class);
        Mockito.verify(outcomeReceiver).onError(responseCapture.capture());

        SendDataException exception = responseCapture.getValue();
        assertThat(exception.getSendDataErrorCode()).isEqualTo(SEND_DATA_INTERNAL_ERROR);
        assertThat(exception.getMessage()).isEqualTo(ERROR_MSG);
    }
}

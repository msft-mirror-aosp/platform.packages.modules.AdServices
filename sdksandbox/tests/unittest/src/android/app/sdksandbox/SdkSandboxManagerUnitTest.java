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

import static android.app.sdksandbox.SdkSandboxManager.EXTRA_DISPLAY_ID;
import static android.app.sdksandbox.SdkSandboxManager.EXTRA_HEIGHT_IN_PIXELS;
import static android.app.sdksandbox.SdkSandboxManager.EXTRA_HOST_TOKEN;
import static android.app.sdksandbox.SdkSandboxManager.EXTRA_SURFACE_PACKAGE;
import static android.app.sdksandbox.SdkSandboxManager.EXTRA_WIDTH_IN_PIXELS;
import static android.app.sdksandbox.SdkSandboxManager.LOAD_SDK_NOT_FOUND;
import static android.app.sdksandbox.SdkSandboxManager.REQUEST_SURFACE_PACKAGE_INTERNAL_ERROR;
import static android.app.sdksandbox.SdkSandboxManager.SEND_DATA_INTERNAL_ERROR;

import static com.google.common.truth.Truth.assertThat;

import static org.junit.Assert.assertThrows;
import static org.junit.Assert.assertTrue;

import android.app.sdksandbox.testutils.FakeOutcomeReceiver;
import android.content.Context;
import android.content.pm.SharedLibraryInfo;
import android.os.Binder;
import android.os.Bundle;
import android.os.OutcomeReceiver;
import android.view.SurfaceControlViewHost.SurfacePackage;

import androidx.test.platform.app.InstrumentationRegistry;

import org.junit.Assert;
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
        long beforeCallingTimeStamp = System.currentTimeMillis();
        mSdkSandboxManager.loadSdk(SDK_NAME, params, Runnable::run, outcomeReceiver);
        long afterCallingTimeStamp = System.currentTimeMillis();

        ArgumentCaptor<ILoadSdkCallback> callbackArgumentCaptor =
                ArgumentCaptor.forClass(ILoadSdkCallback.class);
        ArgumentCaptor<Long> callingTimeArgumentCaptor = ArgumentCaptor.forClass(Long.class);
        Mockito.verify(mBinder)
                .loadSdk(
                        Mockito.eq(mContext.getPackageName()),
                        Mockito.eq(SDK_NAME),
                        callingTimeArgumentCaptor.capture(),
                        Mockito.eq(params),
                        callbackArgumentCaptor.capture());

        Assert.assertTrue(callingTimeArgumentCaptor.getValue() >= beforeCallingTimeStamp);
        Assert.assertTrue(callingTimeArgumentCaptor.getValue() <= afterCallingTimeStamp);

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
        Mockito.verify(mBinder)
                .unloadSdk(
                        Mockito.eq(mContext.getPackageName()),
                        Mockito.eq(SDK_NAME),
                        Mockito.anyLong());
    }

    @Test
    public void testRequestSurfacePackageSuccess() throws Exception {
        final Bundle params = new Bundle();
        params.putInt(EXTRA_WIDTH_IN_PIXELS, 400);
        params.putInt(EXTRA_HEIGHT_IN_PIXELS, 500);
        params.putInt(EXTRA_DISPLAY_ID, 0);
        params.putBinder(EXTRA_HOST_TOKEN, new Binder());

        OutcomeReceiver<Bundle, RequestSurfacePackageException> outcomeReceiver =
                Mockito.spy(new FakeOutcomeReceiver<>());
        mSdkSandboxManager.requestSurfacePackage(SDK_NAME, params, Runnable::run, outcomeReceiver);

        ArgumentCaptor<IRequestSurfacePackageCallback> callbackArgumentCaptor =
                ArgumentCaptor.forClass(IRequestSurfacePackageCallback.class);
        Mockito.verify(mBinder)
                .requestSurfacePackage(
                        Mockito.eq(mContext.getPackageName()),
                        Mockito.eq(SDK_NAME),
                        Mockito.eq(params.getBinder(EXTRA_HOST_TOKEN)),
                        Mockito.eq(params.getInt(EXTRA_DISPLAY_ID)),
                        Mockito.eq(params.getInt(EXTRA_WIDTH_IN_PIXELS)),
                        Mockito.eq(params.getInt(EXTRA_HEIGHT_IN_PIXELS)),
                        Mockito.anyLong(),
                        Mockito.eq(params),
                        callbackArgumentCaptor.capture());

        // Simulate the success callback
        final Bundle extraInfo = new Bundle();
        SurfacePackage surfacePackageMock = Mockito.mock(SurfacePackage.class);
        callbackArgumentCaptor.getValue().onSurfacePackageReady(surfacePackageMock, 0, extraInfo);
        ArgumentCaptor<Bundle> responseCapture = ArgumentCaptor.forClass(Bundle.class);
        Mockito.verify(outcomeReceiver).onResult(responseCapture.capture());

        final Bundle response = responseCapture.getValue();
        SurfacePackage surfacePackage =
                response.getParcelable(EXTRA_SURFACE_PACKAGE, SurfacePackage.class);
        assertThat(surfacePackage).isEqualTo(surfacePackageMock);
        assertThat(response).isEqualTo(extraInfo);
    }

    @Test
    public void testRequestSurfacePackageFailed() throws Exception {
        final Bundle params = new Bundle();
        params.putInt(EXTRA_WIDTH_IN_PIXELS, 400);
        params.putInt(EXTRA_HEIGHT_IN_PIXELS, 500);
        params.putInt(EXTRA_DISPLAY_ID, 0);
        params.putBinder(EXTRA_HOST_TOKEN, new Binder());

        OutcomeReceiver<Bundle, RequestSurfacePackageException> outcomeReceiver =
                Mockito.spy(new FakeOutcomeReceiver<>());
        mSdkSandboxManager.requestSurfacePackage(SDK_NAME, params, Runnable::run, outcomeReceiver);

        ArgumentCaptor<IRequestSurfacePackageCallback> callbackArgumentCaptor =
                ArgumentCaptor.forClass(IRequestSurfacePackageCallback.class);
        Mockito.verify(mBinder)
                .requestSurfacePackage(
                        Mockito.eq(mContext.getPackageName()),
                        Mockito.eq(SDK_NAME),
                        Mockito.eq(params.getBinder(EXTRA_HOST_TOKEN)),
                        Mockito.eq(params.getInt(EXTRA_DISPLAY_ID)),
                        Mockito.eq(params.getInt(EXTRA_WIDTH_IN_PIXELS)),
                        Mockito.eq(params.getInt(EXTRA_HEIGHT_IN_PIXELS)),
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
    public void requestSurfacePackageWithMissingWidthParam() {
        Bundle params = new Bundle();
        params.putInt(EXTRA_HEIGHT_IN_PIXELS, 500);
        params.putInt(EXTRA_DISPLAY_ID, 0);
        params.putBinder(EXTRA_HOST_TOKEN, new Binder());
        ensureIllegalArgumentExceptionOnRequestSurfacePackage(params, EXTRA_WIDTH_IN_PIXELS);
    }

    @Test
    public void requestSurfacePackageWithMissingHeightParam() {
        Bundle params = new Bundle();
        params.putInt(EXTRA_WIDTH_IN_PIXELS, 500);
        params.putInt(EXTRA_DISPLAY_ID, 0);
        params.putBinder(EXTRA_HOST_TOKEN, new Binder());
        ensureIllegalArgumentExceptionOnRequestSurfacePackage(params, EXTRA_HEIGHT_IN_PIXELS);
    }

    @Test
    public void requestSurfacePackageWithMissingDisplayIdParam() {
        Bundle params = new Bundle();
        params.putInt(EXTRA_WIDTH_IN_PIXELS, 500);
        params.putInt(EXTRA_HEIGHT_IN_PIXELS, 500);
        params.putBinder(EXTRA_HOST_TOKEN, new Binder());
        ensureIllegalArgumentExceptionOnRequestSurfacePackage(params, EXTRA_DISPLAY_ID);
    }

    @Test
    public void requestSurfacePackageWithMissingHostTokenParam() {
        Bundle params = new Bundle();
        params.putInt(EXTRA_WIDTH_IN_PIXELS, 500);
        params.putInt(EXTRA_HEIGHT_IN_PIXELS, 500);
        params.putInt(EXTRA_DISPLAY_ID, 0);
        ensureIllegalArgumentExceptionOnRequestSurfacePackage(params, EXTRA_HOST_TOKEN);
    }

    @Test
    public void requestSurfacePackageWithNegativeWidthParam() {
        Bundle params = new Bundle();
        params.putInt(EXTRA_WIDTH_IN_PIXELS, -1);
        params.putInt(EXTRA_HEIGHT_IN_PIXELS, 500);
        params.putInt(EXTRA_DISPLAY_ID, 0);
        params.putBinder(EXTRA_HOST_TOKEN, new Binder());
        ensureIllegalArgumentExceptionOnRequestSurfacePackage(params, EXTRA_WIDTH_IN_PIXELS);
    }

    @Test
    public void requestSurfacePackageWithNegativeHeightParam() {
        Bundle params = new Bundle();
        params.putInt(EXTRA_WIDTH_IN_PIXELS, 500);
        params.putInt(EXTRA_HEIGHT_IN_PIXELS, -1);
        params.putInt(EXTRA_DISPLAY_ID, 0);
        params.putBinder(EXTRA_HOST_TOKEN, new Binder());
        ensureIllegalArgumentExceptionOnRequestSurfacePackage(params, EXTRA_HEIGHT_IN_PIXELS);
    }

    @Test
    public void requestSurfacePackageWithNegativeDisplayIdParam() {
        Bundle params = new Bundle();
        params.putInt(EXTRA_WIDTH_IN_PIXELS, 500);
        params.putInt(EXTRA_HEIGHT_IN_PIXELS, 500);
        params.putInt(EXTRA_DISPLAY_ID, -1);
        params.putBinder(EXTRA_HOST_TOKEN, new Binder());
        ensureIllegalArgumentExceptionOnRequestSurfacePackage(params, EXTRA_DISPLAY_ID);
    }

    @Test
    public void requestSurfacePackageWithWrongTypeWidthParam() {
        Bundle params = new Bundle();
        params.putString(EXTRA_WIDTH_IN_PIXELS, "10");
        params.putInt(EXTRA_HEIGHT_IN_PIXELS, 500);
        params.putInt(EXTRA_DISPLAY_ID, 0);
        params.putBinder(EXTRA_HOST_TOKEN, new Binder());
        ensureIllegalArgumentExceptionOnRequestSurfacePackage(params, EXTRA_WIDTH_IN_PIXELS);
    }

    @Test
    public void requestSurfacePackageWithWrongTypeHeightParam() {
        Bundle params = new Bundle();
        params.putInt(EXTRA_WIDTH_IN_PIXELS, 500);
        params.putString(EXTRA_HEIGHT_IN_PIXELS, "10");
        params.putInt(EXTRA_DISPLAY_ID, 0);
        params.putBinder(EXTRA_HOST_TOKEN, new Binder());
        ensureIllegalArgumentExceptionOnRequestSurfacePackage(params, EXTRA_HEIGHT_IN_PIXELS);
    }

    @Test
    public void requestSurfacePackageWithWrongTypeDisplayIdParam() {
        Bundle params = new Bundle();
        params.putInt(EXTRA_WIDTH_IN_PIXELS, 500);
        params.putInt(EXTRA_HEIGHT_IN_PIXELS, 500);
        params.putString(EXTRA_DISPLAY_ID, "0");
        params.putBinder(EXTRA_HOST_TOKEN, new Binder());
        ensureIllegalArgumentExceptionOnRequestSurfacePackage(params, EXTRA_DISPLAY_ID);
    }

    @Test
    public void requestSurfacePackageWithNullHostTokenParam() {
        Bundle params = new Bundle();
        params.putInt(EXTRA_WIDTH_IN_PIXELS, 500);
        params.putInt(EXTRA_HEIGHT_IN_PIXELS, 500);
        params.putInt(EXTRA_DISPLAY_ID, 0);
        params.putBinder(EXTRA_HOST_TOKEN, null);
        ensureIllegalArgumentExceptionOnRequestSurfacePackage(params, EXTRA_HOST_TOKEN);
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

    private void ensureIllegalArgumentExceptionOnRequestSurfacePackage(
            Bundle params, String fieldKeyName) {
        OutcomeReceiver<Bundle, RequestSurfacePackageException> outcomeReceiver =
                Mockito.spy(new FakeOutcomeReceiver<>());
        IllegalArgumentException exception =
                assertThrows(
                        IllegalArgumentException.class,
                        () ->
                                mSdkSandboxManager.requestSurfacePackage(
                                        SDK_NAME, params, Runnable::run, outcomeReceiver));
        assertTrue(exception.getMessage().contains(fieldKeyName));
    }
}

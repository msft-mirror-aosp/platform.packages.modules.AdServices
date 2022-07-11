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

package com.android.tests.sdksandbox.endtoend;

import static com.google.common.truth.Truth.assertThat;

import android.app.sdksandbox.SdkSandboxManager;
import android.app.sdksandbox.testutils.FakeLoadSdkCallback;
import android.app.sdksandbox.testutils.FakeRequestSurfacePackageCallback;
import android.content.Context;
import android.os.Bundle;

import androidx.test.platform.app.InstrumentationRegistry;

import org.junit.Before;
import org.junit.Test;
import org.junit.runner.RunWith;
import org.junit.runners.JUnit4;

import java.util.concurrent.CountDownLatch;
import java.util.concurrent.TimeUnit;

/*
 * TODO(b/215372846): These providers
 * (RequestSurfacePackageSuccessfullySdkProvider, RetryLoadSameSdkShouldFailSdkProvider) could be
 *  deleted after solving this bug, as then tests can onload and load same SDK multiple times.
 */
@RunWith(JUnit4.class)
public class SdkSandboxManagerTest {

    private SdkSandboxManager mSdkSandboxManager;

    @Before
    public void setup() {
        final Context context = InstrumentationRegistry.getInstrumentation().getContext();
        mSdkSandboxManager = context.getSystemService(SdkSandboxManager.class);
    }

    @Test
    public void loadSdkSuccessfully() {
        final String sdkName = "com.android.loadSdkSuccessfullySdkProvider";
        final FakeLoadSdkCallback callback = new FakeLoadSdkCallback();

        mSdkSandboxManager.loadSdk(sdkName, new Bundle(), Runnable::run, callback);
        assertThat(callback.isLoadSdkSuccessful()).isTrue();
    }

    @Test
    public void retryLoadSameSdkShouldFail() {
        final String sdkName = "com.android.retryLoadSameSdkShouldFailSdkProvider";
        FakeLoadSdkCallback callback = new FakeLoadSdkCallback();

        mSdkSandboxManager.loadSdk(sdkName, new Bundle(), Runnable::run, callback);
        assertThat(callback.isLoadSdkSuccessful()).isTrue();

        callback = new FakeLoadSdkCallback();
        mSdkSandboxManager.loadSdk(sdkName, new Bundle(), Runnable::run, callback);
        assertThat(callback.isLoadSdkSuccessful()).isFalse();
        assertThat(callback.getLoadSdkErrorCode())
                .isEqualTo(SdkSandboxManager.LOAD_SDK_ALREADY_LOADED);
    }

    @Test
    public void loadNotExistSdkShouldFail() {
        final String sdkName = "com.android.not_exist";
        final FakeLoadSdkCallback callback = new FakeLoadSdkCallback();

        mSdkSandboxManager.loadSdk(sdkName, new Bundle(), Runnable::run, callback);
        assertThat(callback.isLoadSdkSuccessful()).isFalse();
        assertThat(callback.getLoadSdkErrorCode())
                .isEqualTo(SdkSandboxManager.LOAD_SDK_NOT_FOUND);
    }

    @Test
    public void loadSdkWithInternalErrorShouldFail() {
        final String sdkName = "com.android.loadSdkWithInternalErrorSdkProvider";
        final FakeLoadSdkCallback callback = new FakeLoadSdkCallback();
        mSdkSandboxManager.loadSdk(sdkName, new Bundle(), Runnable::run, callback);
        assertThat(callback.isLoadSdkSuccessful()).isFalse();
        assertThat(callback.getLoadSdkErrorCode())
                .isEqualTo(SdkSandboxManager.LOAD_SDK_INTERNAL_ERROR);
    }

    @Test
    public void reloadingSdkDoesNotInvalidateIt() {
        final String sdkName = "com.android.requestSurfacePackageSuccessfullySdkProvider";

        final FakeLoadSdkCallback callback = new FakeLoadSdkCallback();
        mSdkSandboxManager.loadSdk(sdkName, new Bundle(), Runnable::run, callback);
        // If the SDK provider has already been loaded from another test, ignore the error.
        assertThat(callback.isLoadSdkSuccessful(/*ignoreSdkAlreadyLoadedError=*/ true)).isTrue();

        // Attempt to load the SDK again and see that it fails.
        final FakeLoadSdkCallback reloadCallback = new FakeLoadSdkCallback();
        mSdkSandboxManager.loadSdk(sdkName, new Bundle(), Runnable::run, reloadCallback);
        assertThat(reloadCallback.isLoadSdkSuccessful()).isFalse();

        // Further calls to the SDK should still be valid.
        final FakeRequestSurfacePackageCallback surfacePackageCallback =
                new FakeRequestSurfacePackageCallback();
        mSdkSandboxManager.requestSurfacePackage(
                sdkName, 0, 500, 500, new Bundle(), Runnable::run, surfacePackageCallback);
        assertThat(surfacePackageCallback.isRequestSurfacePackageSuccessful()).isTrue();
    }

    @Test
    public void requestSurfacePackageSuccessfully() {
        final String sdkName = "com.android.requestSurfacePackageSuccessfullySdkProvider";
        final FakeLoadSdkCallback callback = new FakeLoadSdkCallback();

        mSdkSandboxManager.loadSdk(sdkName, new Bundle(), Runnable::run, callback);
        // If the SDK provider has already been loaded from another test, ignore the SDK already
        // loaded error.
        assertThat(callback.isLoadSdkSuccessful(/*ignoreSdkAlreadyLoadedError=*/ true)).isTrue();

        final FakeRequestSurfacePackageCallback surfacePackageCallback =
                new FakeRequestSurfacePackageCallback();
        mSdkSandboxManager.requestSurfacePackage(
                sdkName, 0, 500, 500, new Bundle(), Runnable::run, surfacePackageCallback);
        assertThat(surfacePackageCallback.isRequestSurfacePackageSuccessful()).isTrue();
    }

    @Test
    public void requestSurfacePackageWithInternalErrorShouldFail() {
        final String sdkName = "com.android.requestSurfacePackageWithInternalErrorSdkProvider";
        final FakeLoadSdkCallback callback = new FakeLoadSdkCallback();

        mSdkSandboxManager.loadSdk(sdkName, new Bundle(), Runnable::run, callback);
        assertThat(callback.isLoadSdkSuccessful()).isTrue();

        final FakeRequestSurfacePackageCallback surfacePackageCallback =
                new FakeRequestSurfacePackageCallback();
        mSdkSandboxManager.requestSurfacePackage(
                sdkName, 0, 500, 500, new Bundle(), Runnable::run, surfacePackageCallback);
        assertThat(surfacePackageCallback.isRequestSurfacePackageSuccessful()).isFalse();
        assertThat(surfacePackageCallback.getSurfacePackageErrorCode())
                .isEqualTo(SdkSandboxManager.REQUEST_SURFACE_PACKAGE_INTERNAL_ERROR);
    }

    @Test
    public void sendDataSuccessfully() {
        final String sdkName = "com.android.sendDataSdkProvider";
        final FakeLoadSdkCallback callback = new FakeLoadSdkCallback();
        mSdkSandboxManager.loadSdk(sdkName, new Bundle(), Runnable::run, callback);
        assertThat(callback.isLoadSdkSuccessful(/*ignoreSdkAlreadyLoadedError=*/ true)).isTrue();

        Bundle data = new Bundle();
        data.putChar("Success", 'S');
        final FakeSendDataCallback sendDataCallback = new FakeSendDataCallback();
        mSdkSandboxManager.sendData(sdkName, data, Runnable::run, sendDataCallback);
        assertThat(sendDataCallback.isSendDataSuccessful()).isTrue();
        Bundle returnData = sendDataCallback.getSendDataSuccessBundle();
        assertThat(returnData.getChar("Completed")).isEqualTo('C');
    }

    @Test
    public void sendIncorrectDataShouldFail() {
        final String sdkName = "com.android.sendDataSdkProvider";
        final FakeLoadSdkCallback callback = new FakeLoadSdkCallback();
        mSdkSandboxManager.loadSdk(sdkName, new Bundle(), Runnable::run, callback);
        assertThat(callback.isLoadSdkSuccessful(/*ignoreSdkAlreadyLoadedError=*/ true)).isTrue();

        final FakeSendDataCallback sendDataCallback = new FakeSendDataCallback();
        mSdkSandboxManager.sendData(sdkName, new Bundle(), Runnable::run, sendDataCallback);
        assertThat(sendDataCallback.isSendDataSuccessful()).isFalse();
        assertThat(sendDataCallback.getSendDataErrorCode())
                .isEqualTo(SdkSandboxManager.SEND_DATA_INTERNAL_ERROR);
        assertThat(sendDataCallback.getSendDataErrorMsg()).contains("Unable to process data");
    }

    @Test
    public void testResourcesAndAssets() {
        final String sdkName = "com.android.codeproviderresources";
        FakeLoadSdkCallback callback = new FakeLoadSdkCallback();
        mSdkSandboxManager.loadSdk(sdkName, new Bundle(), Runnable::run, callback);
        assertThat(callback.isLoadSdkSuccessful()).isTrue();
    }

    private static class FakeSendDataCallback implements SdkSandboxManager.SendDataCallback {
        private final CountDownLatch mSendDataLatch = new CountDownLatch(1);
        private boolean mSendDataSuccess;

        private Bundle mBundle;
        private int mErrorCode;
        private String mErrorMsg;

        @Override
        public void onSendDataSuccess(Bundle params) {
            mSendDataSuccess = true;
            mBundle = params;
            mSendDataLatch.countDown();
        }

        public void onSendDataError(int errorCode, String errorMsg) {
            mSendDataSuccess = false;
            mErrorCode = errorCode;
            mErrorMsg = errorMsg;
            mSendDataLatch.countDown();
        }

        public boolean isSendDataSuccessful() {
            waitForLatch(mSendDataLatch);
            return mSendDataSuccess;
        }

        public Bundle getSendDataSuccessBundle() {
            return mBundle;
        }

        public int getSendDataErrorCode() {
            waitForLatch(mSendDataLatch);
            assertThat(mSendDataSuccess).isFalse();
            return mErrorCode;
        }

        public String getSendDataErrorMsg() {
            waitForLatch(mSendDataLatch);
            assertThat(mSendDataSuccess).isFalse();
            return mErrorMsg;
        }

        private void waitForLatch(CountDownLatch latch) {
            try {
                // Wait for callback to be called
                final int waitTime = 5;
                if (!latch.await(waitTime, TimeUnit.SECONDS)) {
                    throw new IllegalStateException(
                            "Callback not called within " + waitTime + " seconds");
                }
            } catch (InterruptedException e) {
                throw new IllegalStateException(
                        "Interrupted while waiting on callback: " + e.getMessage());
            }
        }
    }
}


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

import static org.junit.Assert.assertThrows;

import android.app.sdksandbox.SdkSandboxManager;
import android.app.sdksandbox.testutils.FakeLoadSdkCallback;
import android.app.sdksandbox.testutils.FakeRequestSurfacePackageCallback;
import android.app.sdksandbox.testutils.FakeSdkSandboxLifecycleCallback;
import android.app.sdksandbox.testutils.FakeSendDataCallback;
import android.content.Context;
import android.content.pm.SharedLibraryInfo;
import android.os.Bundle;

import androidx.test.platform.app.InstrumentationRegistry;

import org.junit.Before;
import org.junit.Test;
import org.junit.runner.RunWith;
import org.junit.runners.JUnit4;

import java.util.List;

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
        mSdkSandboxManager.unloadSdk(sdkName);
    }

    @Test
    public void retryLoadSameSdkShouldFail() {
        final String sdkName = "com.android.loadSdkSuccessfullySdkProviderTwo";
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
    public void loadSdkWithInternalErrorShouldFail() throws Exception {
        final String sdkName = "com.android.loadSdkWithInternalErrorSdkProvider";
        final FakeLoadSdkCallback callback = new FakeLoadSdkCallback();
        mSdkSandboxManager.loadSdk(sdkName, new Bundle(), Runnable::run, callback);
        assertThat(callback.isLoadSdkSuccessful()).isFalse();
        assertThat(callback.getLoadSdkErrorCode())
                .isEqualTo(SdkSandboxManager.LOAD_SDK_INTERNAL_ERROR);
    }

    @Test
    public void unloadAndReloadSdk() {
        final String sdkName = "com.android.loadSdkSuccessfullySdkProvider";
        final FakeLoadSdkCallback callback = new FakeLoadSdkCallback();
        mSdkSandboxManager.loadSdk(sdkName, new Bundle(), Runnable::run, callback);
        assertThat(callback.isLoadSdkSuccessful()).isTrue();

        mSdkSandboxManager.unloadSdk(sdkName);

        // Calls to an unloaded SDK should throw an exception.
        final FakeSendDataCallback sendDataCallback = new FakeSendDataCallback();
        final FakeRequestSurfacePackageCallback requestSurfacePackageCallback =
                new FakeRequestSurfacePackageCallback();
        assertThrows(
                IllegalArgumentException.class,
                () ->
                        mSdkSandboxManager.sendData(
                                sdkName, new Bundle(), Runnable::run, sendDataCallback));
        assertThrows(
                IllegalArgumentException.class,
                () ->
                        mSdkSandboxManager.requestSurfacePackage(
                                sdkName,
                                0,
                                500,
                                500,
                                new Bundle(),
                                Runnable::run,
                                requestSurfacePackageCallback));

        // SDK can be reloaded after being unloaded.
        final FakeLoadSdkCallback callback2 = new FakeLoadSdkCallback();
        mSdkSandboxManager.loadSdk(sdkName, new Bundle(), Runnable::run, callback2);
        assertThat(callback2.isLoadSdkSuccessful()).isTrue();
    }

    @Test
    public void unloadingNonexistentSdkThrowsException() {
        final String sdkName = "com.android.nonexistent";
        assertThrows(IllegalArgumentException.class, () -> mSdkSandboxManager.unloadSdk(sdkName));
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
    public void testReloadingSdkAfterKillingSandboxIsSuccessful() throws Exception {
        // Kill the sandbox if it already exists from previous tests
        killSandboxIfExists();

        // Killing the sandbox and loading the same SDKs again multiple times should work
        for (int i = 0; i < 3; ++i) {
            FakeSdkSandboxLifecycleCallback callback = new FakeSdkSandboxLifecycleCallback();
            mSdkSandboxManager.addSdkSandboxLifecycleCallback(Runnable::run, callback);

            // The same SDKs should be able to be loaded again after sandbox death
            loadMultipleSdks();

            killSandbox();
            assertThat(callback.isSdkSandboxDeathDetected()).isTrue();
        }
    }

    @Test
    public void testAddSdkSandboxLifecycleCallback_BeforeStartingSandbox() throws Exception {
        // Kill the sandbox if it already exists from previous tests
        killSandboxIfExists();

        // Add a sandbox lifecycle callback before starting the sandbox
        FakeSdkSandboxLifecycleCallback lifecycleCallback = new FakeSdkSandboxLifecycleCallback();
        mSdkSandboxManager.addSdkSandboxLifecycleCallback(Runnable::run, lifecycleCallback);

        // Bring up the sandbox
        final String sdkName = "com.android.loadSdkSuccessfullySdkProvider";
        final FakeLoadSdkCallback callback = new FakeLoadSdkCallback();
        mSdkSandboxManager.loadSdk(sdkName, new Bundle(), Runnable::run, callback);
        assertThat(callback.isLoadSdkSuccessful()).isTrue();

        killSandbox();
        assertThat(lifecycleCallback.isSdkSandboxDeathDetected()).isTrue();
    }

    @Test
    public void testAddSdkSandboxLifecycleCallback_AfterStartingSandbox() throws Exception {
        // Bring up the sandbox
        final String sdkName = "com.android.loadSdkSuccessfullySdkProvider";
        final FakeLoadSdkCallback callback = new FakeLoadSdkCallback();
        mSdkSandboxManager.loadSdk(sdkName, new Bundle(), Runnable::run, callback);
        assertThat(callback.isLoadSdkSuccessful(/*ignoreSdkAlreadyLoadedError=*/ true)).isTrue();

        // Add a sandbox lifecycle callback before starting the sandbox
        FakeSdkSandboxLifecycleCallback lifecycleCallback = new FakeSdkSandboxLifecycleCallback();
        mSdkSandboxManager.addSdkSandboxLifecycleCallback(Runnable::run, lifecycleCallback);

        killSandbox();
        assertThat(lifecycleCallback.isSdkSandboxDeathDetected()).isTrue();
    }

    @Test
    public void testRegisterMultipleSdkSandboxLifecycleCallbacks() throws Exception {
        // Kill the sandbox if it already exists from previous tests
        killSandboxIfExists();

        // Add a sandbox lifecycle callback before starting the sandbox
        FakeSdkSandboxLifecycleCallback lifecycleCallback1 = new FakeSdkSandboxLifecycleCallback();
        mSdkSandboxManager.addSdkSandboxLifecycleCallback(Runnable::run, lifecycleCallback1);

        // Bring up the sandbox
        final String sdkName = "com.android.loadSdkSuccessfullySdkProvider";
        final FakeLoadSdkCallback callback = new FakeLoadSdkCallback();
        mSdkSandboxManager.loadSdk(sdkName, new Bundle(), Runnable::run, callback);
        assertThat(callback.isLoadSdkSuccessful()).isTrue();

        // Add another sandbox lifecycle callback after starting it
        FakeSdkSandboxLifecycleCallback lifecycleCallback2 = new FakeSdkSandboxLifecycleCallback();
        mSdkSandboxManager.addSdkSandboxLifecycleCallback(Runnable::run, lifecycleCallback2);

        killSandbox();
        assertThat(lifecycleCallback1.isSdkSandboxDeathDetected()).isTrue();
        assertThat(lifecycleCallback2.isSdkSandboxDeathDetected()).isTrue();
    }

    @Test
    public void testRemoveSdkSandboxLifecycleCallback() throws Exception {
        // Bring up the sandbox
        final String sdkName = "com.android.loadSdkSuccessfullySdkProvider";
        final FakeLoadSdkCallback callback = new FakeLoadSdkCallback();
        mSdkSandboxManager.loadSdk(sdkName, new Bundle(), Runnable::run, callback);
        assertThat(callback.isLoadSdkSuccessful(/*ignoreSdkAlreadyLoadedError=*/ true)).isTrue();

        // Add and remove a sandbox lifecycle callback
        FakeSdkSandboxLifecycleCallback lifecycleCallback1 = new FakeSdkSandboxLifecycleCallback();
        mSdkSandboxManager.addSdkSandboxLifecycleCallback(Runnable::run, lifecycleCallback1);
        mSdkSandboxManager.removeSdkSandboxLifecycleCallback(lifecycleCallback1);

        // Add a lifecycle callback but don't remove it
        FakeSdkSandboxLifecycleCallback lifecycleCallback2 = new FakeSdkSandboxLifecycleCallback();
        mSdkSandboxManager.addSdkSandboxLifecycleCallback(Runnable::run, lifecycleCallback2);

        killSandbox();
        assertThat(lifecycleCallback1.isSdkSandboxDeathDetected()).isFalse();
        assertThat(lifecycleCallback2.isSdkSandboxDeathDetected()).isTrue();
    }

    @Test
    public void getLoadedSdkLibrariesInfoSuccessfully() {
        final String sdkName = "com.android.getLoadedSdkLibInfoSuccessfully";
        final FakeLoadSdkCallback callback = new FakeLoadSdkCallback();

        mSdkSandboxManager.loadSdk(sdkName, new Bundle(), Runnable::run, callback);
        List<SharedLibraryInfo> sdkLibrariesInfo = mSdkSandboxManager.getLoadedSdkLibrariesInfo();

        assertThat(callback.isLoadSdkSuccessful()).isTrue();
        // TODO(b/239025435): assert size 1 after unload is implemented
        assertThat(sdkLibrariesInfo.stream().filter(lib -> lib.getName().equals(sdkName)).count())
                .isEqualTo(1);
    }

    @Test
    public void getLoadedSdkLibrariesInfoMissesSdkWhenLoadFailed() {
        final String sdkName = "com.android.loadSdkWithInternalErrorSdkProvider";
        final FakeLoadSdkCallback callback = new FakeLoadSdkCallback();

        mSdkSandboxManager.loadSdk(sdkName, new Bundle(), Runnable::run, callback);
        assertThat(callback.isLoadSdkSuccessful()).isFalse();

        List<SharedLibraryInfo> sdkLibrariesInfo = mSdkSandboxManager.getLoadedSdkLibrariesInfo();
        // TODO(b/239025435): assert empty after unload is implemented
        assertThat(sdkLibrariesInfo.stream().filter(lib -> lib.getName().equals(sdkName)).count())
                .isEqualTo(0);
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

    private void loadMultipleSdks() {
        FakeLoadSdkCallback callback = new FakeLoadSdkCallback();
        final String sdk1 = "com.android.loadSdkSuccessfullySdkProvider";
        mSdkSandboxManager.loadSdk(sdk1, new Bundle(), Runnable::run, callback);
        assertThat(callback.isLoadSdkSuccessful()).isTrue();

        FakeLoadSdkCallback callback2 = new FakeLoadSdkCallback();
        final String sdk2 = "com.android.loadSdkSuccessfullySdkProviderTwo";
        mSdkSandboxManager.loadSdk(sdk2, new Bundle(), Runnable::run, callback2);
        assertThat(callback2.isLoadSdkSuccessful()).isTrue();
    }

    // Returns true if the sandbox was already likely existing, false otherwise.
    private boolean killSandboxIfExists() throws Exception {
        FakeSdkSandboxLifecycleCallback callback = new FakeSdkSandboxLifecycleCallback();
        mSdkSandboxManager.addSdkSandboxLifecycleCallback(Runnable::run, callback);
        killSandbox();

        return callback.isSdkSandboxDeathDetected();
    }

    private void killSandbox() throws Exception {
        // TODO(b/241542162): Avoid using reflection as a workaround once test apis can be run
        //  without issue.
        mSdkSandboxManager.getClass().getMethod("stopSdkSandbox").invoke(mSdkSandboxManager);
    }
}


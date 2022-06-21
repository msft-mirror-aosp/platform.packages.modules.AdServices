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

import org.junit.BeforeClass;
import org.junit.Test;
import org.junit.runner.RunWith;
import org.junit.runners.JUnit4;
/*
 * TODO(b/215372846): These providers
 * (RequestSurfacePackageSuccessfullySdkProvider, RetryLoadSameSdkShouldFailSdkProvider) could be
 *  deleted after solving this bug, as then tests can onload and load same SDK multiple times.
 */
@RunWith(JUnit4.class)
public class SdkSandboxManagerTest {

    private static Context sContext;

    @BeforeClass
    public static void setup() {
        sContext = InstrumentationRegistry.getInstrumentation().getContext();
    }

    @Test
    public void loadSdkSuccessfully() {
        final SdkSandboxManager sdkSandboxManager =
                sContext.getSystemService(SdkSandboxManager.class);
        final String sdkName = "com.android.loadSdkSuccessfullySdkProvider";
        final FakeLoadSdkCallback callback = new FakeLoadSdkCallback();

        sdkSandboxManager.loadSdk(sdkName, new Bundle(), Runnable::run, callback);
        assertThat(callback.isLoadSdkSuccessful()).isTrue();
    }

    @Test
    public void retryLoadSameSdkShouldFail() {
        final SdkSandboxManager sdkSandboxManager =
                sContext.getSystemService(SdkSandboxManager.class);
        final String sdkName = "com.android.retryLoadSameSdkShouldFailSdkProvider";
        FakeLoadSdkCallback callback = new FakeLoadSdkCallback();

        sdkSandboxManager.loadSdk(
                sdkName, new Bundle(), Runnable::run, callback);
        assertThat(callback.isLoadSdkSuccessful()).isTrue();

        callback = new FakeLoadSdkCallback();
        sdkSandboxManager.loadSdk(sdkName, new Bundle(), Runnable::run, callback);
        assertThat(callback.isLoadSdkSuccessful()).isFalse();
        assertThat(callback.getLoadSdkErrorCode())
                .isEqualTo(SdkSandboxManager.LOAD_SDK_ALREADY_LOADED);
    }

    @Test
    public void loadNotExistSdkShouldFail() {
        final SdkSandboxManager sdkSandboxManager =
                sContext.getSystemService(SdkSandboxManager.class);
        final String sdkName = "com.android.not_exist";
        final FakeLoadSdkCallback callback = new FakeLoadSdkCallback();

        sdkSandboxManager.loadSdk(sdkName, new Bundle(),  Runnable::run, callback);
        assertThat(callback.isLoadSdkSuccessful()).isFalse();
        assertThat(callback.getLoadSdkErrorCode())
                .isEqualTo(SdkSandboxManager.LOAD_SDK_NOT_FOUND);
    }

    @Test
    public void loadSdkWithInternalErrorShouldFail() {
        final SdkSandboxManager sdkSandboxManager =
                sContext.getSystemService(SdkSandboxManager.class);
        final String sdkName = "com.android.loadSdkWithInternalErrorSdkProvider";
        final FakeLoadSdkCallback callback = new FakeLoadSdkCallback();
        sdkSandboxManager.loadSdk(sdkName, new Bundle(), Runnable::run, callback);
        assertThat(callback.isLoadSdkSuccessful()).isFalse();
        assertThat(callback.getLoadSdkErrorCode())
                .isEqualTo(SdkSandboxManager.LOAD_SDK_INTERNAL_ERROR);
    }

    @Test
    public void reloadingSdkDoesNotInvalidateIt() {
        final SdkSandboxManager sdkSandboxManager =
                sContext.getSystemService(SdkSandboxManager.class);
        final String sdkName = "com.android.requestSurfacePackageSuccessfullySdkProvider";

        final FakeLoadSdkCallback callback = new FakeLoadSdkCallback();
        sdkSandboxManager.loadSdk(sdkName, new Bundle(), Runnable::run, callback);
        // If the SDK provider has already been loaded from another test, ignore the error.
        assertThat(callback.isLoadSdkSuccessful(/*ignoreSdkAlreadyLoadedError=*/ true)).isTrue();

        // Attempt to load the SDK again and see that it fails.
        final FakeLoadSdkCallback reloadCallback = new FakeLoadSdkCallback();
        sdkSandboxManager.loadSdk(sdkName, new Bundle(), Runnable::run, reloadCallback);
        assertThat(reloadCallback.isLoadSdkSuccessful()).isFalse();

        // Further calls to the SDK should still be valid.
        final FakeRequestSurfacePackageCallback surfacePackageCallback =
                new FakeRequestSurfacePackageCallback();
        sdkSandboxManager.requestSurfacePackage(
                sdkName, 0, 500, 500, new Bundle(), Runnable::run, surfacePackageCallback);
        assertThat(surfacePackageCallback.isRequestSurfacePackageSuccessful()).isTrue();
    }

    @Test
    public void requestSurfacePackageSuccessfully() {
        final SdkSandboxManager sdkSandboxManager =
                sContext.getSystemService(SdkSandboxManager.class);
        final String sdkName = "com.android.requestSurfacePackageSuccessfullySdkProvider";

        final FakeLoadSdkCallback callback = new FakeLoadSdkCallback();
        sdkSandboxManager.loadSdk(sdkName, new Bundle(), Runnable::run, callback);
        // If the SDK provider has already been loaded from another test, ignore the SDK already
        // loaded error.
        assertThat(callback.isLoadSdkSuccessful(/*ignoreSdkAlreadyLoadedError=*/ true)).isTrue();

        final FakeRequestSurfacePackageCallback surfacePackageCallback =
                new FakeRequestSurfacePackageCallback();
        sdkSandboxManager.requestSurfacePackage(
                sdkName, 0, 500, 500, new Bundle(), Runnable::run, surfacePackageCallback);
        assertThat(surfacePackageCallback.isRequestSurfacePackageSuccessful()).isTrue();
    }

    @Test
    public void requestSurfacePackageWithInternalErrorShouldFail() {
        final SdkSandboxManager sdkSandboxManager =
                sContext.getSystemService(SdkSandboxManager.class);
        final String sdkName = "com.android.requestSurfacePackageWithInternalErrorSdkProvider";
        final FakeLoadSdkCallback callback = new FakeLoadSdkCallback();

        sdkSandboxManager.loadSdk(sdkName, new Bundle(), Runnable::run, callback);
        assertThat(callback.isLoadSdkSuccessful()).isTrue();

        final FakeRequestSurfacePackageCallback surfacePackageCallback =
                new FakeRequestSurfacePackageCallback();
        sdkSandboxManager.requestSurfacePackage(
                sdkName, 0, 500, 500, new Bundle(), Runnable::run, surfacePackageCallback);
        assertThat(surfacePackageCallback.isRequestSurfacePackageSuccessful()).isFalse();
        assertThat(surfacePackageCallback.getSurfacePackageErrorCode())
                .isEqualTo(SdkSandboxManager.REQUEST_SURFACE_PACKAGE_INTERNAL_ERROR);
    }

    @Test
    public void testResourcesAndAssets() {
        final SdkSandboxManager sdkSandboxManager =
                sContext.getSystemService(SdkSandboxManager.class);
        final String sdkName = "com.android.codeproviderresources";
        FakeLoadSdkCallback callback = new FakeLoadSdkCallback();
        sdkSandboxManager.loadSdk(sdkName, new Bundle(), Runnable::run, callback);
        assertThat(callback.isLoadSdkSuccessful()).isTrue();
    }

}


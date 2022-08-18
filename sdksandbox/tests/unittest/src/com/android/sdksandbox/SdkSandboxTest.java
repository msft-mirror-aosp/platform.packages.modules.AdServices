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

package com.android.sdksandbox;

import static com.google.common.truth.Truth.assertThat;

import android.app.sdksandbox.LoadSdkException;
import android.app.sdksandbox.LoadSdkResponse;
import android.content.Context;
import android.content.SharedPreferences;
import android.content.pm.ApplicationInfo;
import android.os.Binder;
import android.os.Bundle;
import android.os.IBinder;
import android.os.Looper;
import android.os.Process;
import android.preference.PreferenceManager;
import android.view.SurfaceControlViewHost;

import androidx.test.InstrumentationRegistry;

import org.junit.After;
import org.junit.Before;
import org.junit.BeforeClass;
import org.junit.Test;
import org.junit.runner.RunWith;
import org.junit.runners.JUnit4;
import org.mockito.Mockito;

import java.io.FileDescriptor;
import java.io.PrintWriter;
import java.io.StringWriter;
import java.util.Map;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.TimeUnit;

@RunWith(JUnit4.class)
public class SdkSandboxTest {

    private SdkSandboxServiceImpl mService;
    private ApplicationInfo mApplicationInfo;
    private static final String CLIENT_PACKAGE_NAME = "com.android.client";
    private static final String SDK_NAME = "com.android.testprovider";
    private static final String SDK_PACKAGE = "com.android.testprovider";
    private static final String SDK_PROVIDER_CLASS = "com.android.testprovider.TestProvider";
    private static final long TIME_SYSTEM_SERVER_CALLED_SANDBOX = 3;
    private static final long TIME_SANDBOX_RECEIVED_CALL_FROM_SYSTEM_SERVER = 5;
    private static final long TIME_SANDBOX_CALLED_SDK = 7;
    private static final long TIME_SDK_CALL_COMPLETED = 9;
    private static final long TIME_SANDBOX_CALLED_SYSTEM_SERVER = 11;

    private static final Map<String, String> TEST_DATA =
            Map.of("hello1", "world1", "hello2", "world2", "empty", "");

    private Context mContext;
    private InjectorForTest mInjector;

    static class InjectorForTest extends SdkSandboxServiceImpl.Injector {

        InjectorForTest(Context context) {
            super(context);
        }

        @Override
        int getCallingUid() {
            return Process.SYSTEM_UID;
        }
    }

    @BeforeClass
    public static void setupClass() {
        // Required to create a SurfaceControlViewHost
        Looper.prepare();
    }

    @Before
    public void setup() throws Exception {
        mContext = InstrumentationRegistry.getContext();
        mInjector = Mockito.spy(new InjectorForTest(mContext));
        mService = new SdkSandboxServiceImpl(mInjector);
        mApplicationInfo = mContext.getPackageManager().getApplicationInfo(SDK_PACKAGE, 0);
    }

    @After
    public void teardown() throws Exception {
        getClientSharedPreference().edit().clear().commit();
    }

    @Test
    public void testLoadingSuccess() throws Exception {
        CountDownLatch latch = new CountDownLatch(1);
        RemoteCode mRemoteCode = new RemoteCode(latch);
        mService.loadSdk(
                CLIENT_PACKAGE_NAME,
                new Binder(),
                mApplicationInfo,
                SDK_NAME,
                SDK_PROVIDER_CLASS,
                null,
                null,
                new Bundle(),
                mRemoteCode);
        assertThat(latch.await(1, TimeUnit.MINUTES)).isTrue();
        assertThat(mRemoteCode.mSuccessful).isTrue();
    }

    @Test
    public void testDuplicateLoadingFails() throws Exception {
        CountDownLatch latch1 = new CountDownLatch(1);
        RemoteCode mRemoteCode1 = new RemoteCode(latch1);
        CountDownLatch latch2 = new CountDownLatch(1);
        RemoteCode mRemoteCode2 = new RemoteCode(latch2);
        IBinder duplicateToken = new Binder();
        mService.loadSdk(
                CLIENT_PACKAGE_NAME,
                duplicateToken,
                mApplicationInfo,
                SDK_NAME,
                SDK_PROVIDER_CLASS,
                null,
                null,
                new Bundle(),
                mRemoteCode1);
        assertThat(latch1.await(1, TimeUnit.MINUTES)).isTrue();
        mService.loadSdk(
                CLIENT_PACKAGE_NAME,
                duplicateToken,
                mApplicationInfo,
                SDK_NAME,
                SDK_PROVIDER_CLASS,
                null,
                null,
                new Bundle(),
                mRemoteCode2);
        assertThat(latch2.await(1, TimeUnit.MINUTES)).isTrue();
        assertThat(mRemoteCode2.mSuccessful).isFalse();
        assertThat(mRemoteCode2.mErrorCode)
                .isEqualTo(ILoadSdkInSandboxCallback.LOAD_SDK_ALREADY_LOADED);
    }

    @Test
    public void testLoadingMultiple() throws Exception {
        CountDownLatch latch1 = new CountDownLatch(1);
        RemoteCode mRemoteCode1 = new RemoteCode(latch1);
        CountDownLatch latch2 = new CountDownLatch(1);
        RemoteCode mRemoteCode2 = new RemoteCode(latch2);
        mService.loadSdk(
                CLIENT_PACKAGE_NAME,
                new Binder(),
                mApplicationInfo,
                SDK_NAME,
                SDK_PROVIDER_CLASS,
                null,
                null,
                new Bundle(),
                mRemoteCode1);
        mService.loadSdk(
                CLIENT_PACKAGE_NAME,
                new Binder(),
                mApplicationInfo,
                SDK_NAME,
                SDK_PROVIDER_CLASS,
                null,
                null,
                new Bundle(),
                mRemoteCode2);
        assertThat(latch1.await(1, TimeUnit.MINUTES)).isTrue();
        assertThat(mRemoteCode1.mSuccessful).isTrue();
        assertThat(latch2.await(1, TimeUnit.MINUTES)).isTrue();
        assertThat(mRemoteCode2.mSuccessful).isTrue();
    }

    @Test
    public void testRequestSurfacePackage() throws Exception {
        CountDownLatch latch = new CountDownLatch(1);
        RemoteCode mRemoteCode = new RemoteCode(latch);
        mService.loadSdk(
                CLIENT_PACKAGE_NAME,
                new Binder(),
                mApplicationInfo,
                SDK_NAME,
                SDK_PROVIDER_CLASS,
                null,
                null,
                new Bundle(),
                mRemoteCode);
        assertThat(latch.await(1, TimeUnit.MINUTES)).isTrue();

        CountDownLatch surfaceLatch = new CountDownLatch(1);
        RequestSurfacePackageCallbackImpl callback =
                new RequestSurfacePackageCallbackImpl(surfaceLatch);
        mRemoteCode
                .getCallback()
                .onSurfacePackageRequested(
                        new Binder(),
                        mContext.getDisplayId(),
                        500,
                        500,
                        System.currentTimeMillis(),
                        new Bundle(),
                        callback);
        assertThat(surfaceLatch.await(1, TimeUnit.MINUTES)).isTrue();
        assertThat(callback.mSurfacePackage).isNotNull();
    }

    @Test
    public void testSurfacePackageError() throws Exception {
        CountDownLatch latch = new CountDownLatch(1);
        RemoteCode mRemoteCode = new RemoteCode(latch);
        mService.loadSdk(
                CLIENT_PACKAGE_NAME,
                new Binder(),
                mApplicationInfo,
                SDK_NAME,
                SDK_PROVIDER_CLASS,
                null,
                null,
                new Bundle(),
                mRemoteCode);
        assertThat(latch.await(1, TimeUnit.MINUTES)).isTrue();

        CountDownLatch surfaceLatch = new CountDownLatch(1);
        RequestSurfacePackageCallbackImpl callback =
                new RequestSurfacePackageCallbackImpl(surfaceLatch);
        mRemoteCode
                .getCallback()
                .onSurfacePackageRequested(
                        new Binder(),
                        111111 /* invalid displayId */,
                        500,
                        500,
                        System.currentTimeMillis(),
                        null,
                        callback);
        assertThat(surfaceLatch.await(1, TimeUnit.MINUTES)).isTrue();
        assertThat(callback.mSurfacePackage).isNull();
        assertThat(callback.mSuccessful).isFalse();
        assertThat(callback.mErrorCode)
                .isEqualTo(IRequestSurfacePackageFromSdkCallback.SURFACE_PACKAGE_INTERNAL_ERROR);
    }

    @Test(expected = SecurityException.class)
    public void testDumpWithoutPermission() {
        mService.dump(new FileDescriptor(), new PrintWriter(new StringWriter()), new String[0]);
    }

    @Test
    public void testSyncDataFromClient_StoresInClientSharedPreference() throws Exception {
        mService.syncDataFromClient(getBundleFromMap(TEST_DATA));

        // Verify that ClientSharedPreference contains the synced data
        SharedPreferences pref = getClientSharedPreference();
        assertThat(pref.getAll().keySet()).containsExactlyElementsIn(TEST_DATA.keySet());
        assertThat(pref.getAll().values()).containsExactlyElementsIn(TEST_DATA.values());
    }

    private Bundle getBundleFromMap(Map<String, String> data) {
        Bundle bundle = new Bundle();
        for (String key : data.keySet()) {
            // TODO(b/239403323): add support for non-string values
            bundle.putString(key, data.get(key));
        }
        return bundle;
    }

    private SharedPreferences getClientSharedPreference() {
        return PreferenceManager.getDefaultSharedPreferences(mContext);
    }

    private static class RemoteCode extends ILoadSdkInSandboxCallback.Stub {

        private CountDownLatch mLatch;
        boolean mSuccessful = false;
        int mErrorCode = -1;

        private ISdkSandboxManagerToSdkSandboxCallback mCallback;

        private ISdkSandboxManagerToSdkSandboxCallback getCallback() {
            return mCallback;
        }

        RemoteCode(CountDownLatch latch) {
            mLatch = latch;
        }

        @Override
        public void onLoadSdkSuccess(
                LoadSdkResponse loadSdkResponse, ISdkSandboxManagerToSdkSandboxCallback callback) {
            mLatch.countDown();
            mCallback = callback;
            mSuccessful = true;
        }

        @Override
        public void onLoadSdkError(LoadSdkException exception) {
            mLatch.countDown();
            mErrorCode = exception.getLoadSdkErrorCode();
            mSuccessful = false;
        }

    }

    @Test
    public void testLatencyMetrics_requestSurfacePackage_success() throws Exception {
        final CountDownLatch latch = new CountDownLatch(1);
        final RemoteCode mRemoteCode = new RemoteCode(latch);

        Mockito.when(mInjector.getCurrentTime())
                .thenReturn(
                        TIME_SANDBOX_RECEIVED_CALL_FROM_SYSTEM_SERVER,
                        TIME_SANDBOX_CALLED_SDK,
                        TIME_SDK_CALL_COMPLETED,
                        TIME_SANDBOX_CALLED_SYSTEM_SERVER);

        mService.loadSdk(
                CLIENT_PACKAGE_NAME,
                new Binder(),
                mApplicationInfo,
                SDK_NAME,
                SDK_PROVIDER_CLASS,
                null,
                null,
                new Bundle(),
                mRemoteCode);
        assertThat(latch.await(1, TimeUnit.MINUTES)).isTrue();

        CountDownLatch surfaceLatch = new CountDownLatch(1);
        RequestSurfacePackageCallbackImpl callback =
                new RequestSurfacePackageCallbackImpl(surfaceLatch);
        mRemoteCode
                .getCallback()
                .onSurfacePackageRequested(
                        new Binder(),
                        mContext.getDisplayId(),
                        500,
                        500,
                        TIME_SYSTEM_SERVER_CALLED_SANDBOX,
                        new Bundle(),
                        callback);
        assertThat(surfaceLatch.await(1, TimeUnit.MINUTES)).isTrue();
        assertThat(callback.mSurfacePackage).isNotNull();
        assertThat(callback.mLatencySystemServerToSandbox)
                .isEqualTo(
                        (int)
                                (TIME_SANDBOX_RECEIVED_CALL_FROM_SYSTEM_SERVER
                                        - TIME_SYSTEM_SERVER_CALLED_SANDBOX));
        assertThat(callback.mLatencySdk)
                .isEqualTo((int) (TIME_SDK_CALL_COMPLETED - TIME_SANDBOX_CALLED_SDK));
        assertThat(callback.mLatencySandbox)
                .isEqualTo(
                        (int)
                                (TIME_SANDBOX_CALLED_SYSTEM_SERVER
                                        - TIME_SANDBOX_RECEIVED_CALL_FROM_SYSTEM_SERVER
                                        - (TIME_SDK_CALL_COMPLETED - TIME_SANDBOX_CALLED_SDK)));
        assertThat(callback.mTimeSandboxCalledSystemServer)
                .isEqualTo(TIME_SANDBOX_CALLED_SYSTEM_SERVER);
    }

    private static class RequestSurfacePackageCallbackImpl
            extends IRequestSurfacePackageFromSdkCallback.Stub {
        private CountDownLatch mLatch;
        private SurfaceControlViewHost.SurfacePackage mSurfacePackage;
        boolean mSuccessful = false;
        int mErrorCode = -1;
        private int mLatencySystemServerToSandbox;
        private int mLatencySandbox;
        private int mLatencySdk;
        private long mTimeSandboxCalledSystemServer;

        RequestSurfacePackageCallbackImpl(CountDownLatch latch) {
            mLatch = latch;
        }

        @Override
        public void onSurfacePackageReady(
                SurfaceControlViewHost.SurfacePackage surfacePackage,
                int displayId,
                long timeSandboxCalledSystemServer,
                Bundle params,
                Bundle latencies) {
            mLatch.countDown();
            mSurfacePackage = surfacePackage;
            mLatencySystemServerToSandbox =
                    latencies.getInt(
                            IRequestSurfacePackageFromSdkCallback.LATENCY_SYSTEM_SERVER_TO_SANDBOX);
            mLatencySandbox =
                    latencies.getInt(IRequestSurfacePackageFromSdkCallback.LATENCY_SANDBOX);
            mLatencySdk = latencies.getInt(IRequestSurfacePackageFromSdkCallback.LATENCY_SDK);
            mTimeSandboxCalledSystemServer = timeSandboxCalledSystemServer;
        }

        @Override
        public void onSurfacePackageError(
                int errorCode,
                String message,
                long timeSandboxCalledSystemServer,
                boolean failedAtSdk,
                Bundle sandboxLatencies) {
            mLatch.countDown();
            mErrorCode = errorCode;
            mSuccessful = false;
        }
    }
}

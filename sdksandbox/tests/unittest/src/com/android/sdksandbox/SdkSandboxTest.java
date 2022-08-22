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

import android.app.sdksandbox.KeyWithType;
import android.app.sdksandbox.LoadSdkException;
import android.app.sdksandbox.SandboxedSdk;
import android.app.sdksandbox.SharedPreferencesUpdate;
import android.app.sdksandbox.testutils.FakeSharedPreferencesSyncCallback;
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

import androidx.test.platform.app.InstrumentationRegistry;

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
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collections;
import java.util.List;
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

    private static final String KEY_TO_UPDATE = "hello1";
    private static final KeyWithType KEY_WITH_TYPE_TO_UPDATE =
            new KeyWithType(KEY_TO_UPDATE, KeyWithType.KEY_TYPE_STRING);
    private static final Map<String, String> TEST_DATA =
            Map.of(KEY_TO_UPDATE, "world1", "hello2", "world2", "empty", "");
    private static final List<KeyWithType> KEYS_TO_SYNC =
            List.of(
                    KEY_WITH_TYPE_TO_UPDATE,
                    new KeyWithType("hello2", KeyWithType.KEY_TYPE_STRING),
                    new KeyWithType("empty", KeyWithType.KEY_TYPE_STRING));
    private static final SharedPreferencesUpdate TEST_UPDATE =
            new SharedPreferencesUpdate(KEYS_TO_SYNC, getBundleFromMap(TEST_DATA));

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
        Context context = InstrumentationRegistry.getInstrumentation().getContext();
        mContext = Mockito.spy(context);
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
        assertThat(mRemoteCode1.mSuccessful).isTrue();
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

    @Test
    public void testDump_NoSdk() {
        Mockito.doNothing()
                .when(mContext)
                .enforceCallingPermission(
                        Mockito.eq("android.permission.DUMP"), Mockito.anyString());
        final StringWriter stringWriter = new StringWriter();
        mService.dump(new FileDescriptor(), new PrintWriter(stringWriter), new String[0]);
        assertThat(stringWriter.toString()).contains("mHeldSdk is empty");
    }

    @Test
    public void testDump_WithSdk() {
        Mockito.doNothing()
                .when(mContext)
                .enforceCallingPermission(
                        Mockito.eq("android.permission.DUMP"), Mockito.anyString());

        mService.loadSdk(
                CLIENT_PACKAGE_NAME,
                new Binder(),
                mApplicationInfo,
                SDK_NAME,
                SDK_PROVIDER_CLASS,
                null,
                null,
                new Bundle(),
                new RemoteCode(new CountDownLatch(1)));

        final StringWriter stringWriter = new StringWriter();
        mService.dump(new FileDescriptor(), new PrintWriter(stringWriter), new String[0]);
        assertThat(stringWriter.toString()).contains("mHeldSdk size:");
    }

    @Test(expected = SecurityException.class)
    public void testDump_WithoutPermission() {
        mService.dump(new FileDescriptor(), new PrintWriter(new StringWriter()), new String[0]);
    }

    @Test
    public void testSyncDataFromClient_StoresInClientSharedPreference() throws Exception {
        mService.syncDataFromClient(TEST_UPDATE, new FakeSharedPreferencesSyncCallback());

        // Verify that ClientSharedPreference contains the synced data
        SharedPreferences pref = getClientSharedPreference();
        assertThat(pref.getAll().keySet()).containsExactlyElementsIn(TEST_DATA.keySet());
        assertThat(pref.getAll().values()).containsExactlyElementsIn(TEST_DATA.values());
    }

    @Test
    public void testSyncDataFromClient_SupportsAllValidTypes() throws Exception {
        // Create a bundle with all supported values
        Bundle bundle = new Bundle();
        bundle.putString("string", "value");
        bundle.putBoolean("boolean", true);
        bundle.putInt("integer", 1);
        bundle.putFloat("float", 1.0f);
        bundle.putLong("long", 1L);
        bundle.putStringArrayList("arrayList", new ArrayList<>(Arrays.asList("list1", "list2")));

        final List<KeyWithType> keysToSync =
                List.of(
                        new KeyWithType("string", KeyWithType.KEY_TYPE_STRING),
                        new KeyWithType("boolean", KeyWithType.KEY_TYPE_BOOLEAN),
                        new KeyWithType("integer", KeyWithType.KEY_TYPE_INTEGER),
                        new KeyWithType("float", KeyWithType.KEY_TYPE_FLOAT),
                        new KeyWithType("long", KeyWithType.KEY_TYPE_LONG),
                        new KeyWithType("arrayList", KeyWithType.KEY_TYPE_STRING_SET));
        final SharedPreferencesUpdate update = new SharedPreferencesUpdate(keysToSync, bundle);
        mService.syncDataFromClient(update, new FakeSharedPreferencesSyncCallback());

        // Verify that ClientSharedPreference contains the synced data
        SharedPreferences pref = getClientSharedPreference();
        assertThat(pref.getAll().keySet()).containsExactlyElementsIn(bundle.keySet());
        assertThat(pref.getString("string", "")).isEqualTo("value");
        assertThat(pref.getBoolean("boolean", false)).isEqualTo(true);
        assertThat(pref.getInt("integer", 0)).isEqualTo(1);
        assertThat(pref.getFloat("float", 0.0f)).isEqualTo(1.0f);
        assertThat(pref.getLong("long", 0L)).isEqualTo(1L);
        assertThat(pref.getStringSet("arrayList", Collections.emptySet()))
                .containsExactly("list1", "list2");
    }

    @Test
    public void testSyncDataFromClient_KeyCanBeUpdated() throws Exception {
        // Preload some data
        mService.syncDataFromClient(TEST_UPDATE, new FakeSharedPreferencesSyncCallback());

        // Now send in a new update
        final Bundle newData = getBundleFromMap(Map.of(KEY_TO_UPDATE, "update"));
        final SharedPreferencesUpdate newUpdate =
                new SharedPreferencesUpdate(List.of(KEY_WITH_TYPE_TO_UPDATE), newData);
        mService.syncDataFromClient(newUpdate, new FakeSharedPreferencesSyncCallback());

        // Verify that ClientSharedPreference contains the synced data
        SharedPreferences pref = getClientSharedPreference();
        assertThat(pref.getAll().keySet()).containsExactlyElementsIn(TEST_DATA.keySet());
        assertThat(pref.getString(KEY_TO_UPDATE, "")).isEqualTo("update");
    }

    @Test
    public void testSyncDataFromClient_KeyCanBeRemoved() throws Exception {
        // Preload some data
        mService.syncDataFromClient(TEST_UPDATE, new FakeSharedPreferencesSyncCallback());

        // Now send in a new update
        final SharedPreferencesUpdate newUpdate =
                new SharedPreferencesUpdate(TEST_UPDATE.getKeysInUpdate(), new Bundle());
        mService.syncDataFromClient(newUpdate, new FakeSharedPreferencesSyncCallback());

        // Verify that ClientSharedPreference contains the synced data
        SharedPreferences pref = getClientSharedPreference();
        assertThat(pref.getAll().keySet()).doesNotContain(KEY_TO_UPDATE);
    }

    @Test
    public void testSyncDataFromClient_CallbackIsCalled() throws Exception {
        // Preload some data
        final FakeSharedPreferencesSyncCallback callback = new FakeSharedPreferencesSyncCallback();
        mService.syncDataFromClient(TEST_UPDATE, callback);

        // Verify that ClientSharedPreference contains the synced data
        assertThat(callback.isSuccessful()).isTrue();
    }

    private static Bundle getBundleFromMap(Map<String, String> data) {
        Bundle bundle = new Bundle();
        for (String key : data.keySet()) {
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
                SandboxedSdk sandboxedSdk, ISdkSandboxManagerToSdkSandboxCallback callback) {
            mCallback = callback;
            mSuccessful = true;
            mLatch.countDown();
        }

        @Override
        public void onLoadSdkError(LoadSdkException exception) {
            mErrorCode = exception.getLoadSdkErrorCode();
            mSuccessful = false;
            mLatch.countDown();
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
            mSurfacePackage = surfacePackage;
            mLatencySystemServerToSandbox =
                    latencies.getInt(
                            IRequestSurfacePackageFromSdkCallback.LATENCY_SYSTEM_SERVER_TO_SANDBOX);
            mLatencySandbox =
                    latencies.getInt(IRequestSurfacePackageFromSdkCallback.LATENCY_SANDBOX);
            mLatencySdk = latencies.getInt(IRequestSurfacePackageFromSdkCallback.LATENCY_SDK);
            mTimeSandboxCalledSystemServer = timeSandboxCalledSystemServer;
            mLatch.countDown();
        }

        @Override
        public void onSurfacePackageError(
                int errorCode,
                String message,
                long timeSandboxCalledSystemServer,
                boolean failedAtSdk,
                Bundle sandboxLatencies) {
            mErrorCode = errorCode;
            mSuccessful = false;
            mLatch.countDown();
        }
    }
}

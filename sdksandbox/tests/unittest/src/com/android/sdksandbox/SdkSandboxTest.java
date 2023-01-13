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

import static org.junit.Assert.assertThrows;

import android.app.sdksandbox.LoadSdkException;
import android.app.sdksandbox.SandboxedSdk;
import android.app.sdksandbox.SdkSandboxLocalSingleton;
import android.app.sdksandbox.SharedPreferencesKey;
import android.app.sdksandbox.SharedPreferencesUpdate;
import android.app.sdksandbox.testutils.StubSdkToServiceLink;
import android.content.Context;
import android.content.SharedPreferences;
import android.content.pm.ApplicationInfo;
import android.content.pm.PackageInfo;
import android.content.pm.PackageManager;
import android.os.Binder;
import android.os.Bundle;
import android.os.Looper;
import android.os.Process;
import android.view.SurfaceControlViewHost;

import androidx.test.platform.app.InstrumentationRegistry;

import com.android.dx.mockito.inline.extended.ExtendedMockito;

import org.junit.After;
import org.junit.Before;
import org.junit.BeforeClass;
import org.junit.Test;
import org.junit.runner.RunWith;
import org.junit.runners.JUnit4;
import org.mockito.Mockito;
import org.mockito.MockitoSession;
import org.mockito.quality.Strictness;

import java.io.File;
import java.io.FileDescriptor;
import java.io.IOException;
import java.io.PrintWriter;
import java.io.StringWriter;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
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
    private static final SharedPreferencesKey KEY_WITH_TYPE_TO_UPDATE =
            new SharedPreferencesKey(KEY_TO_UPDATE, SharedPreferencesKey.KEY_TYPE_STRING);
    private static final Map<String, String> TEST_DATA =
            Map.of(KEY_TO_UPDATE, "world1", "hello2", "world2", "empty", "");
    private static final List<SharedPreferencesKey> KEYS_TO_SYNC =
            List.of(
                    KEY_WITH_TYPE_TO_UPDATE,
                    new SharedPreferencesKey("hello2", SharedPreferencesKey.KEY_TYPE_STRING),
                    new SharedPreferencesKey("empty", SharedPreferencesKey.KEY_TYPE_STRING));
    private static final SharedPreferencesUpdate TEST_UPDATE =
            new SharedPreferencesUpdate(KEYS_TO_SYNC, getBundleFromMap(TEST_DATA));
    private static final SandboxLatencyInfo SANDBOX_LATENCY_INFO =
            new SandboxLatencyInfo(TIME_SYSTEM_SERVER_CALLED_SANDBOX);

    private Context mContext;
    private InjectorForTest mInjector;

    private PackageManager mSpyPackageManager;
    private MockitoSession mStaticMockSession;

    static class InjectorForTest extends SdkSandboxServiceImpl.Injector {

        private Context mContext;

        InjectorForTest(Context context) {
            super(context);
            mContext = context;
        }

        @Override
        int getCallingUid() {
            return Process.SYSTEM_UID;
        }

        @Override
        Context getContext() {
            return mContext;
        }
    }

    @BeforeClass
    public static void setupClass() {
        // Required to create a SurfaceControlViewHost
        Looper.prepare();
    }

    @Before
    public void setup() throws Exception {
        mStaticMockSession =
                ExtendedMockito.mockitoSession()
                        .strictness(Strictness.LENIENT)
                        .mockStatic(Process.class)
                        .startMocking();
        ExtendedMockito.doReturn(true).when(() -> Process.isSdkSandbox());

        Context context = InstrumentationRegistry.getInstrumentation().getContext();
        mContext = Mockito.spy(context);
        mSpyPackageManager = Mockito.spy(mContext.getPackageManager());
        mInjector = Mockito.spy(new InjectorForTest(mContext));
        Mockito.doReturn(mSpyPackageManager).when(mContext).getPackageManager();
        mService = new SdkSandboxServiceImpl(mInjector);
        mApplicationInfo = mContext.getPackageManager().getApplicationInfo(SDK_PACKAGE, 0);
    }

    @After
    public void teardown() throws Exception {
        mService.getClientSharedPreferences().edit().clear().commit();
        mStaticMockSession.finishMocking();
    }

    @Test
    public void testSandboxInitialization_initializesSdkSandboxLocalSingleTon() throws Exception {
        assertThrows(
                IllegalStateException.class, () -> SdkSandboxLocalSingleton.getExistingInstance());

        mService.initialize(new StubSdkToServiceLink());

        assertThat(SdkSandboxLocalSingleton.getExistingInstance()).isNotNull();
    }

    @Test
    public void testSandboxInitialization_clearsSyncedData() throws Exception {
        // First write some data
        mService.syncDataFromClient(TEST_UPDATE);

        mService.initialize(new StubSdkToServiceLink());

        assertThat(mService.getClientSharedPreferences().getAll()).isEmpty();
    }

    @Test
    public void testLoadingSuccess() throws Exception {
        CountDownLatch latch = new CountDownLatch(1);
        RemoteCode mRemoteCode = new RemoteCode(latch);
        mService.loadSdk(
                CLIENT_PACKAGE_NAME,
                mApplicationInfo,
                SDK_NAME,
                SDK_PROVIDER_CLASS,
                null,
                null,
                new Bundle(),
                mRemoteCode,
                SANDBOX_LATENCY_INFO);
        assertThat(latch.await(1, TimeUnit.MINUTES)).isTrue();
        assertThat(mRemoteCode.mSuccessful).isTrue();
    }

    private void createFileInPaths(List<String> paths) throws IOException {
        final String fileName = "file.txt";

        for (int i = 0; i < 2; i++) {
            Files.createDirectories(
                    Paths.get(mContext.getDataDir().getPath() + "/" + paths.get(i)));
            final Path path =
                    Paths.get(mContext.getDataDir().getPath(), "/" + paths.get(i) + "/" + fileName);
            Files.deleteIfExists(path);

            Files.createFile(path);
            final byte[] buffer = new byte[1 * 1024 * 1024];
            Files.write(path, buffer);

            final File file = new File(String.valueOf(path));
            assertThat(file.exists()).isTrue();
        }
    }

    @Test
    public void testComputeSdkStorage() throws Exception {
        final String pathName1 = "path1";
        final String pathName2 = "path2";

        createFileInPaths(Arrays.asList(pathName1, pathName2));

        final List<String> sharedPaths =
                Arrays.asList(mContext.getDataDir().getPath() + "/" + pathName1);
        final List<String> sdkPaths =
                Arrays.asList(mContext.getDataDir().getPath() + "/" + pathName2);

        SdkSandboxStorageCallback sdkSandboxStorageCallback = new SdkSandboxStorageCallback();
        mService.computeSdkStorage(sharedPaths, sdkPaths, sdkSandboxStorageCallback);

        Thread.sleep(5000);

        assertThat(sdkSandboxStorageCallback.getSdkStorage()).isEqualTo(1024F);
        assertThat(sdkSandboxStorageCallback.getSharedStorage()).isEqualTo(1024F);
    }

    @Test
    public void testDuplicateLoadingFails() throws Exception {
        CountDownLatch latch1 = new CountDownLatch(1);
        RemoteCode mRemoteCode1 = new RemoteCode(latch1);
        CountDownLatch latch2 = new CountDownLatch(1);
        RemoteCode mRemoteCode2 = new RemoteCode(latch2);
        mService.loadSdk(
                CLIENT_PACKAGE_NAME,
                mApplicationInfo,
                SDK_NAME,
                SDK_PROVIDER_CLASS,
                null,
                null,
                new Bundle(),
                mRemoteCode1,
                SANDBOX_LATENCY_INFO);
        assertThat(latch1.await(1, TimeUnit.MINUTES)).isTrue();
        assertThat(mRemoteCode1.mSuccessful).isTrue();
        mService.loadSdk(
                CLIENT_PACKAGE_NAME,
                mApplicationInfo,
                SDK_NAME,
                SDK_PROVIDER_CLASS,
                null,
                null,
                new Bundle(),
                mRemoteCode2,
                SANDBOX_LATENCY_INFO);
        assertThat(latch2.await(1, TimeUnit.MINUTES)).isTrue();
        assertThat(mRemoteCode2.mSuccessful).isFalse();
        assertThat(mRemoteCode2.mErrorCode)
                .isEqualTo(ILoadSdkInSandboxCallback.LOAD_SDK_ALREADY_LOADED);
    }

    @Test
    public void testRequestSurfacePackage() throws Exception {
        CountDownLatch latch = new CountDownLatch(1);
        RemoteCode mRemoteCode = new RemoteCode(latch);
        mService.loadSdk(
                CLIENT_PACKAGE_NAME,
                mApplicationInfo,
                SDK_NAME,
                SDK_PROVIDER_CLASS,
                null,
                null,
                new Bundle(),
                mRemoteCode,
                SANDBOX_LATENCY_INFO);
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
                        new Bundle(),
                        SANDBOX_LATENCY_INFO,
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
                mApplicationInfo,
                SDK_NAME,
                SDK_PROVIDER_CLASS,
                null,
                null,
                new Bundle(),
                mRemoteCode,
                SANDBOX_LATENCY_INFO);
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
                        null,
                        SANDBOX_LATENCY_INFO,
                        callback);
        assertThat(surfaceLatch.await(1, TimeUnit.MINUTES)).isTrue();
        assertThat(callback.mSurfacePackage).isNull();
        assertThat(callback.mSuccessful).isFalse();
        assertThat(callback.mErrorCode)
                .isEqualTo(IRequestSurfacePackageFromSdkCallback.SURFACE_PACKAGE_INTERNAL_ERROR);
    }

    @Test
    public void testDump_NoSdk() {
        final StringWriter stringWriter = new StringWriter();
        mService.dump(new FileDescriptor(), new PrintWriter(stringWriter), new String[0]);
        assertThat(stringWriter.toString()).contains("mHeldSdk is empty");
    }

    @Test
    public void testDump_WithSdk() {
        mService.loadSdk(
                CLIENT_PACKAGE_NAME,
                mApplicationInfo,
                SDK_NAME,
                SDK_PROVIDER_CLASS,
                null,
                null,
                new Bundle(),
                new RemoteCode(new CountDownLatch(1)),
                SANDBOX_LATENCY_INFO);

        final StringWriter stringWriter = new StringWriter();
        mService.dump(new FileDescriptor(), new PrintWriter(stringWriter), new String[0]);
        assertThat(stringWriter.toString()).contains("mHeldSdk size:");
    }

    @Test
    public void testDisabledWhenWebviewNotResolvable() throws Exception {
        // WebView provider cannot be resolved, therefore sandbox should be disabled.
        Mockito.doReturn(null)
                .when(mSpyPackageManager)
                .getPackageInfo(
                        Mockito.anyString(), Mockito.any(PackageManager.PackageInfoFlags.class));
        SdkSandboxDisabledCallback callback = new SdkSandboxDisabledCallback();
        mService.isDisabled(callback);
        assertThat(callback.mIsDisabled).isTrue();
    }

    @Test
    public void testNotDisabledWhenWebviewResolvable() throws Exception {
        // WebView provider can be resolved, therefore sandbox should not be disabled.
        Mockito.doReturn(new PackageInfo())
                .when(mSpyPackageManager)
                .getPackageInfo(
                        Mockito.anyString(), Mockito.any(PackageManager.PackageInfoFlags.class));
        SdkSandboxDisabledCallback callback = new SdkSandboxDisabledCallback();
        mService.isDisabled(callback);
        assertThat(callback.isDisabled()).isFalse();
    }

    @Test
    public void testSyncDataFromClient_StoresInClientSharedPreference() throws Exception {
        mService.syncDataFromClient(TEST_UPDATE);

        // Verify that ClientSharedPreference contains the synced data
        SharedPreferences pref = mService.getClientSharedPreferences();
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

        final List<SharedPreferencesKey> keysToSync =
                List.of(
                        new SharedPreferencesKey("string", SharedPreferencesKey.KEY_TYPE_STRING),
                        new SharedPreferencesKey("boolean", SharedPreferencesKey.KEY_TYPE_BOOLEAN),
                        new SharedPreferencesKey("integer", SharedPreferencesKey.KEY_TYPE_INTEGER),
                        new SharedPreferencesKey("float", SharedPreferencesKey.KEY_TYPE_FLOAT),
                        new SharedPreferencesKey("long", SharedPreferencesKey.KEY_TYPE_LONG),
                        new SharedPreferencesKey(
                                "arrayList", SharedPreferencesKey.KEY_TYPE_STRING_SET));
        final SharedPreferencesUpdate update = new SharedPreferencesUpdate(keysToSync, bundle);
        mService.syncDataFromClient(update);

        // Verify that ClientSharedPreference contains the synced data
        SharedPreferences pref = mService.getClientSharedPreferences();
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
        mService.syncDataFromClient(TEST_UPDATE);

        // Now send in a new update
        final Bundle newData = getBundleFromMap(Map.of(KEY_TO_UPDATE, "update"));
        final SharedPreferencesUpdate newUpdate =
                new SharedPreferencesUpdate(List.of(KEY_WITH_TYPE_TO_UPDATE), newData);
        mService.syncDataFromClient(newUpdate);

        // Verify that ClientSharedPreference contains the synced data
        SharedPreferences pref = mService.getClientSharedPreferences();
        assertThat(pref.getAll().keySet()).containsExactlyElementsIn(TEST_DATA.keySet());
        assertThat(pref.getString(KEY_TO_UPDATE, "")).isEqualTo("update");
    }

    @Test
    public void testSyncDataFromClient_KeyCanBeRemoved() throws Exception {
        // Preload some data
        mService.syncDataFromClient(TEST_UPDATE);

        // Now send in a new update
        final SharedPreferencesUpdate newUpdate =
                new SharedPreferencesUpdate(TEST_UPDATE.getKeysInUpdate(), new Bundle());
        mService.syncDataFromClient(newUpdate);

        // Verify that ClientSharedPreference contains the synced data
        SharedPreferences pref = mService.getClientSharedPreferences();
        assertThat(pref.getAll().keySet()).doesNotContain(KEY_TO_UPDATE);
    }

    @Test
    public void testLatencyMetrics_loadSdk_success() throws Exception {
        final CountDownLatch latch = new CountDownLatch(1);
        final RemoteCode mRemoteCode = new RemoteCode(latch);
        SANDBOX_LATENCY_INFO.setTimeSandboxReceivedCallFromSystemServer(
                TIME_SANDBOX_RECEIVED_CALL_FROM_SYSTEM_SERVER);

        Mockito.when(mInjector.getCurrentTime())
                .thenReturn(
                        TIME_SANDBOX_CALLED_SDK,
                        TIME_SDK_CALL_COMPLETED,
                        TIME_SANDBOX_CALLED_SYSTEM_SERVER);

        mService.loadSdk(
                CLIENT_PACKAGE_NAME,
                mApplicationInfo,
                SDK_NAME,
                SDK_PROVIDER_CLASS,
                null,
                null,
                new Bundle(),
                mRemoteCode,
                SANDBOX_LATENCY_INFO);
        assertThat(latch.await(1, TimeUnit.MINUTES)).isTrue();
        assertThat(mRemoteCode.mSandboxLatencyInfo.getLatencySystemServerToSandbox())
                .isEqualTo(
                        (int)
                                (TIME_SANDBOX_RECEIVED_CALL_FROM_SYSTEM_SERVER
                                        - TIME_SYSTEM_SERVER_CALLED_SANDBOX));
        assertThat(mRemoteCode.mSandboxLatencyInfo.getSdkLatency())
                .isEqualTo((int) (TIME_SDK_CALL_COMPLETED - TIME_SANDBOX_CALLED_SDK));

        assertThat(mRemoteCode.mSandboxLatencyInfo.getSandboxLatency())
                .isEqualTo(
                        (int)
                                (TIME_SANDBOX_CALLED_SYSTEM_SERVER
                                        - TIME_SANDBOX_RECEIVED_CALL_FROM_SYSTEM_SERVER
                                        - (TIME_SDK_CALL_COMPLETED - TIME_SANDBOX_CALLED_SDK)));
        assertThat(mRemoteCode.mSandboxLatencyInfo.getTimeSandboxCalledSystemServer())
                .isEqualTo(TIME_SANDBOX_CALLED_SYSTEM_SERVER);
    }

    @Test
    public void testLatencyMetrics_unloadSdk_success() throws Exception {
        SANDBOX_LATENCY_INFO.setTimeSandboxReceivedCallFromSystemServer(
                TIME_SANDBOX_RECEIVED_CALL_FROM_SYSTEM_SERVER);

        Mockito.when(mInjector.getCurrentTime())
                .thenReturn(
                        // loadSdk mocks
                        TIME_SANDBOX_CALLED_SDK,
                        TIME_SDK_CALL_COMPLETED,
                        TIME_SANDBOX_CALLED_SYSTEM_SERVER,
                        // unloadSdk mocks
                        TIME_SANDBOX_CALLED_SDK,
                        TIME_SDK_CALL_COMPLETED,
                        TIME_SANDBOX_CALLED_SYSTEM_SERVER);

        final CountDownLatch latch = new CountDownLatch(1);
        mService.loadSdk(
                CLIENT_PACKAGE_NAME,
                mApplicationInfo,
                SDK_NAME,
                SDK_PROVIDER_CLASS,
                null,
                null,
                new Bundle(),
                new RemoteCode(latch),
                SANDBOX_LATENCY_INFO);
        assertThat(latch.await(1, TimeUnit.MINUTES)).isTrue();

        final UnloadSdkCallbackImpl unloadSdkCallback = new UnloadSdkCallbackImpl();
        mService.unloadSdk(SDK_NAME, unloadSdkCallback, SANDBOX_LATENCY_INFO);

        final SandboxLatencyInfo sandboxLatencyInfo = unloadSdkCallback.getSandboxLatencyInfo();

        assertThat(sandboxLatencyInfo.getSdkLatency())
                .isEqualTo((int) (TIME_SDK_CALL_COMPLETED - TIME_SANDBOX_CALLED_SDK));

        assertThat(sandboxLatencyInfo.getSandboxLatency())
                .isEqualTo(
                        (int)
                                (TIME_SANDBOX_CALLED_SYSTEM_SERVER
                                        - TIME_SANDBOX_RECEIVED_CALL_FROM_SYSTEM_SERVER
                                        - (TIME_SDK_CALL_COMPLETED - TIME_SANDBOX_CALLED_SDK)));
        assertThat(sandboxLatencyInfo.getTimeSandboxCalledSystemServer())
                .isEqualTo(TIME_SANDBOX_CALLED_SYSTEM_SERVER);
        assertThat(sandboxLatencyInfo.getTimeSandboxCalledSystemServer())
                .isEqualTo(TIME_SANDBOX_CALLED_SYSTEM_SERVER);
    }

    @Test
    public void testLatencyMetrics_requestSurfacePackage_success() throws Exception {
        final CountDownLatch latch = new CountDownLatch(1);
        final RemoteCode mRemoteCode = new RemoteCode(latch);

        Mockito.when(mInjector.getCurrentTime())
                .thenReturn(
                        // loadSdk mocks
                        TIME_SANDBOX_CALLED_SDK,
                        TIME_SDK_CALL_COMPLETED,
                        TIME_SANDBOX_CALLED_SYSTEM_SERVER,
                        // requestSurfacePackage mocks
                        TIME_SANDBOX_RECEIVED_CALL_FROM_SYSTEM_SERVER,
                        TIME_SANDBOX_CALLED_SDK,
                        TIME_SDK_CALL_COMPLETED,
                        TIME_SANDBOX_CALLED_SYSTEM_SERVER);

        mService.loadSdk(
                CLIENT_PACKAGE_NAME,
                mApplicationInfo,
                SDK_NAME,
                SDK_PROVIDER_CLASS,
                null,
                null,
                new Bundle(),
                mRemoteCode,
                SANDBOX_LATENCY_INFO);
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
                        new Bundle(),
                        SANDBOX_LATENCY_INFO,
                        callback);
        assertThat(surfaceLatch.await(1, TimeUnit.MINUTES)).isTrue();
        assertThat(callback.mSurfacePackage).isNotNull();
        assertThat(callback.mSandboxLatencyInfo.getLatencySystemServerToSandbox())
                .isEqualTo(
                        (int)
                                (TIME_SANDBOX_RECEIVED_CALL_FROM_SYSTEM_SERVER
                                        - TIME_SYSTEM_SERVER_CALLED_SANDBOX));
        assertThat(callback.mSandboxLatencyInfo.getSdkLatency())
                .isEqualTo((int) (TIME_SDK_CALL_COMPLETED - TIME_SANDBOX_CALLED_SDK));
        assertThat(callback.mSandboxLatencyInfo.getSandboxLatency())
                .isEqualTo(
                        (int)
                                (TIME_SANDBOX_CALLED_SYSTEM_SERVER
                                        - TIME_SANDBOX_RECEIVED_CALL_FROM_SYSTEM_SERVER
                                        - (TIME_SDK_CALL_COMPLETED - TIME_SANDBOX_CALLED_SDK)));
        assertThat(callback.mSandboxLatencyInfo.getTimeSandboxCalledSystemServer())
                .isEqualTo(TIME_SANDBOX_CALLED_SYSTEM_SERVER);
    }

    private static class RemoteCode extends ILoadSdkInSandboxCallback.Stub {

        private CountDownLatch mLatch;
        private SandboxLatencyInfo mSandboxLatencyInfo;
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
                SandboxedSdk sandboxedSdk,
                ISdkSandboxManagerToSdkSandboxCallback callback,
                SandboxLatencyInfo sandboxLatencyInfo) {
            mCallback = callback;
            mSuccessful = true;
            mSandboxLatencyInfo = sandboxLatencyInfo;
            mLatch.countDown();
        }

        @Override
        public void onLoadSdkError(
                LoadSdkException exception, SandboxLatencyInfo sandboxLatencyInfo) {
            mErrorCode = exception.getLoadSdkErrorCode();
            mSuccessful = false;
            mLatch.countDown();
        }
    }

    private static class UnloadSdkCallbackImpl extends IUnloadSdkCallback.Stub {
        private SandboxLatencyInfo mSandboxLatencyInfo;

        @Override
        public void onUnloadSdk(SandboxLatencyInfo sandboxLatencyInfo) {
            mSandboxLatencyInfo = sandboxLatencyInfo;
        }

        public SandboxLatencyInfo getSandboxLatencyInfo() {
            return mSandboxLatencyInfo;
        }
    }

    private static Bundle getBundleFromMap(Map<String, String> data) {
        Bundle bundle = new Bundle();
        for (String key : data.keySet()) {
            bundle.putString(key, data.get(key));
        }
        return bundle;
    }

    private static class RequestSurfacePackageCallbackImpl
            extends IRequestSurfacePackageFromSdkCallback.Stub {
        private CountDownLatch mLatch;
        private SurfaceControlViewHost.SurfacePackage mSurfacePackage;
        boolean mSuccessful = false;
        int mErrorCode = -1;
        private SandboxLatencyInfo mSandboxLatencyInfo;
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
                Bundle params,
                SandboxLatencyInfo sandboxLatencyInfo) {
            mSurfacePackage = surfacePackage;
            mSandboxLatencyInfo = sandboxLatencyInfo;
            mLatch.countDown();
        }

        @Override
        public void onSurfacePackageError(
                int errorCode, String message, SandboxLatencyInfo sandboxLatencyInfo) {
            mErrorCode = errorCode;
            mSuccessful = false;
            mLatch.countDown();
        }
    }

    private static class SdkSandboxDisabledCallback extends ISdkSandboxDisabledCallback.Stub {
        private final CountDownLatch mLatch;
        private boolean mIsDisabled;

        SdkSandboxDisabledCallback() {
            mLatch = new CountDownLatch(1);
        }

        @Override
        public void onResult(boolean isDisabled) {
            mIsDisabled = isDisabled;
            mLatch.countDown();
        }

        boolean isDisabled() throws Exception {
            assertThat(mLatch.await(1, TimeUnit.SECONDS)).isTrue();
            return mIsDisabled;
        }
    }

    private static class SdkSandboxStorageCallback extends IComputeSdkStorageCallback.Stub {
        private float mSharedStorage;
        private float mSdkStorage;

        @Override
        public void onStorageInfoComputed(int sharedStorage, int sdkStorage) {
            mSharedStorage = sharedStorage;
            mSdkStorage = sdkStorage;
        }

        public float getSharedStorage() {
            return mSharedStorage;
        }

        public float getSdkStorage() {
            return mSdkStorage;
        }
    }
}

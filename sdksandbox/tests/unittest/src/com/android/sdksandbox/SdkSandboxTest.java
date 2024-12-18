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
import static org.junit.Assert.fail;

import android.app.sdksandbox.ISdkToServiceCallback;
import android.app.sdksandbox.LoadSdkException;
import android.app.sdksandbox.SandboxLatencyInfo;
import android.app.sdksandbox.SandboxedSdk;
import android.app.sdksandbox.SandboxedSdkContext;
import android.app.sdksandbox.SdkSandboxLocalSingleton;
import android.app.sdksandbox.SharedPreferencesKey;
import android.app.sdksandbox.SharedPreferencesUpdate;
import android.app.sdksandbox.sdkprovider.SdkSandboxActivityRegistry;
import android.app.sdksandbox.testutils.FakeSdkSandboxActivityRegistryInjector;
import android.app.sdksandbox.testutils.StubSdkToServiceLink;
import android.content.Context;
import android.content.SharedPreferences;
import android.content.pm.ApplicationInfo;
import android.content.pm.PackageManager;
import android.os.Binder;
import android.os.Build;
import android.os.Bundle;
import android.os.Looper;
import android.os.Process;
import android.view.SurfaceControlViewHost;

import androidx.annotation.RequiresApi;
import androidx.test.platform.app.InstrumentationRegistry;

import com.android.dx.mockito.inline.extended.ExtendedMockito;
import com.android.modules.utils.build.SdkLevel;
import com.android.server.sdksandbox.DeviceSupportedBaseTest;

import com.google.common.truth.Expect;

import dalvik.system.PathClassLoader;

import org.junit.After;
import org.junit.Before;
import org.junit.BeforeClass;
import org.junit.Rule;
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
public class SdkSandboxTest extends DeviceSupportedBaseTest {

    private SdkSandboxServiceImpl mService;
    private ApplicationInfo mApplicationInfo;
    private ClassLoader mLoader;
    private static final String CLIENT_PACKAGE_NAME = "com.android.client";
    private static final String SDK_NAME = "com.android.testprovider";
    private static final String SDK_PACKAGE = "com.android.testprovider";
    private static final String SDK_PROVIDER_CLASS = "com.android.testprovider.TestProvider";
    // Key passed to TestProvider to trigger a load error.
    private static final String THROW_EXCEPTION_KEY = "throw-exception";
    private static final long TIME_SYSTEM_SERVER_CALL_FINISHED = 3;
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
    private SandboxLatencyInfo mSandboxLatencyInfo;

    private SdkSandboxActivityRegistry mRegistry;

    private Context mContext;
    private InjectorForTest mInjector;

    private PackageManager mSpyPackageManager;
    private MockitoSession mStaticMockSession;

    static class InjectorForTest extends SdkSandboxServiceImpl.Injector {

        private Context mContext;
        private SdkSandboxActivityRegistry mRegistry;

        InjectorForTest(Context context, SdkSandboxActivityRegistry registry) {
            super(context);
            mContext = context;
            mRegistry = registry;
        }

        @Override
        int getCallingUid() {
            return Process.SYSTEM_UID;
        }

        @Override
        Context getContext() {
            return mContext;
        }

        @Override
        @RequiresApi(Build.VERSION_CODES.UPSIDE_DOWN_CAKE)
        SdkSandboxActivityRegistry getSdkSandboxActivityRegistry() {
            return mRegistry;
        }
    }

    @Rule(order = 0)
    public final Expect mExpect = Expect.create();

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
                        .spyStatic(Process.class)
                        .startMocking();
        ExtendedMockito.doReturn(true).when(() -> Process.isSdkSandbox());

        Context context = InstrumentationRegistry.getInstrumentation().getContext();
        mContext = Mockito.spy(context);
        mApplicationInfo = mContext.getPackageManager().getApplicationInfo(SDK_PACKAGE, 0);
        mLoader = getClassLoader(mApplicationInfo);
        // Starting from Android U, we customize the base context of SandboxedSdkContext directly.
        if (SdkLevel.isAtLeastU()) {
            Mockito.doReturn(mContext)
                    .when(mContext)
                    .createContextForSdkInSandbox(Mockito.any(), Mockito.anyInt());
            Mockito.doReturn(mLoader).when(mContext).getClassLoader();
        }

        mSpyPackageManager = Mockito.spy(mContext.getPackageManager());
        Mockito.doReturn(mSpyPackageManager).when(mContext).getPackageManager();

        SdkSandboxLocalSingleton sdkSandboxLocalSingleton =
                Mockito.mock(SdkSandboxLocalSingleton.class);
        ISdkToServiceCallback serviceCallback = Mockito.mock(ISdkToServiceCallback.class);
        Mockito.when(sdkSandboxLocalSingleton.getSdkToServiceCallback())
                .thenReturn(serviceCallback);
        FakeSdkSandboxActivityRegistryInjector activityRegistryInjector =
                new FakeSdkSandboxActivityRegistryInjector(sdkSandboxLocalSingleton);
        mRegistry = Mockito.spy(SdkSandboxActivityRegistry.getInstance(activityRegistryInjector));

        mInjector = Mockito.spy(new InjectorForTest(mContext, mRegistry));
        mService = new SdkSandboxServiceImpl(mInjector);

        mSandboxLatencyInfo = new SandboxLatencyInfo();
    }

    @After
    public void teardown() throws Exception {
        if (mService != null) mService.getClientSharedPreferences().edit().clear().commit();
        if (mStaticMockSession != null) mStaticMockSession.finishMocking();
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
        LoadSdkCallback loadSdkCallback = new LoadSdkCallback();
        mService.initialize(new StubSdkToServiceLink());
        mService.loadSdk(
                CLIENT_PACKAGE_NAME,
                mApplicationInfo,
                SDK_NAME,
                SDK_PROVIDER_CLASS,
                new ApplicationInfo(),
                new Bundle(),
                loadSdkCallback,
                mSandboxLatencyInfo);
        loadSdkCallback.assertLoadSdkIsSuccessful();
    }

    @Test
    public void testLoadingWithoutInitializingFails() throws Exception {
        LoadSdkCallback loadSdkCallback = new LoadSdkCallback();
        mService.loadSdk(
                CLIENT_PACKAGE_NAME,
                mApplicationInfo,
                SDK_NAME,
                SDK_PROVIDER_CLASS,
                new ApplicationInfo(),
                new Bundle(),
                loadSdkCallback,
                mSandboxLatencyInfo);
        loadSdkCallback.assertLoadSdkIsUnsuccessful();
        assertThat(loadSdkCallback.mErrorCode)
                .isEqualTo(ILoadSdkInSandboxCallback.LOAD_SDK_INSTANTIATION_ERROR);
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

        mExpect.that(sdkSandboxStorageCallback.getSdkStorage()).isEqualTo(1024F);
        mExpect.that(sdkSandboxStorageCallback.getSharedStorage()).isEqualTo(1024F);
    }

    @Test
    public void testDuplicateLoadingFails() throws Exception {
        LoadSdkCallback loadSdkCallback1 = new LoadSdkCallback();
        mService.initialize(new StubSdkToServiceLink());
        mService.loadSdk(
                CLIENT_PACKAGE_NAME,
                mApplicationInfo,
                SDK_NAME,
                SDK_PROVIDER_CLASS,
                new ApplicationInfo(),
                new Bundle(),
                loadSdkCallback1,
                mSandboxLatencyInfo);
        loadSdkCallback1.assertLoadSdkIsSuccessful();

        LoadSdkCallback loadSdkCallback2 = new LoadSdkCallback();
        mService.loadSdk(
                CLIENT_PACKAGE_NAME,
                mApplicationInfo,
                SDK_NAME,
                SDK_PROVIDER_CLASS,
                new ApplicationInfo(),
                new Bundle(),
                loadSdkCallback2,
                mSandboxLatencyInfo);

        assertThat(loadSdkCallback2.mLatch.await(1, TimeUnit.MINUTES)).isTrue();
        assertThat(loadSdkCallback2.mSuccessful).isFalse();
        assertThat(loadSdkCallback2.mErrorCode)
                .isEqualTo(ILoadSdkInSandboxCallback.LOAD_SDK_ALREADY_LOADED);
    }

    @Test
    public void testRequestSurfacePackage() throws Exception {
        LoadSdkCallback loadSdkCallback = new LoadSdkCallback();
        mService.initialize(new StubSdkToServiceLink());
        mService.loadSdk(
                CLIENT_PACKAGE_NAME,
                mApplicationInfo,
                SDK_NAME,
                SDK_PROVIDER_CLASS,
                new ApplicationInfo(),
                new Bundle(),
                loadSdkCallback,
                mSandboxLatencyInfo);
        loadSdkCallback.assertLoadSdkIsSuccessful();

        CountDownLatch surfaceLatch = new CountDownLatch(1);
        RequestSurfacePackageCallbackImpl callback =
                new RequestSurfacePackageCallbackImpl(surfaceLatch);
        loadSdkCallback
                .getCallback()
                .onSurfacePackageRequested(
                        new Binder(),
                        mContext.getDisplayId(),
                        500,
                        500,
                        new Bundle(),
                        mSandboxLatencyInfo,
                        callback);
        assertThat(surfaceLatch.await(1, TimeUnit.MINUTES)).isTrue();
        assertThat(callback.mSurfacePackage).isNotNull();
    }

    @Test
    public void testSurfacePackageError() throws Exception {
        LoadSdkCallback loadSdkCallback = new LoadSdkCallback();
        mService.initialize(new StubSdkToServiceLink());
        mService.loadSdk(
                CLIENT_PACKAGE_NAME,
                mApplicationInfo,
                SDK_NAME,
                SDK_PROVIDER_CLASS,
                new ApplicationInfo(),
                new Bundle(),
                loadSdkCallback,
                mSandboxLatencyInfo);
        loadSdkCallback.assertLoadSdkIsSuccessful();

        CountDownLatch surfaceLatch = new CountDownLatch(1);
        RequestSurfacePackageCallbackImpl callback =
                new RequestSurfacePackageCallbackImpl(surfaceLatch);
        loadSdkCallback
                .getCallback()
                .onSurfacePackageRequested(
                        new Binder(),
                        111111 /* invalid displayId */,
                        500,
                        500,
                        null,
                        mSandboxLatencyInfo,
                        callback);
        assertThat(surfaceLatch.await(1, TimeUnit.MINUTES)).isTrue();
        mExpect.that(callback.mSurfacePackage).isNull();
        mExpect.that(callback.mSuccessful).isFalse();
        mExpect.that(callback.mErrorCode)
                .isEqualTo(IRequestSurfacePackageFromSdkCallback.SURFACE_PACKAGE_INTERNAL_ERROR);
    }

    @Test
    public void testDump_NoSdk() {
        final StringWriter stringWriter = new StringWriter();
        mService.dump(new FileDescriptor(), new PrintWriter(stringWriter), new String[0]);
        assertThat(stringWriter.toString()).contains("mHeldSdk is empty");
    }

    @Test
    public void testDump_WithSdk() throws Exception {
        LoadSdkCallback callback = new LoadSdkCallback();
        mService.initialize(new StubSdkToServiceLink());
        mService.loadSdk(
                CLIENT_PACKAGE_NAME,
                mApplicationInfo,
                SDK_NAME,
                SDK_PROVIDER_CLASS,
                new ApplicationInfo(),
                new Bundle(),
                callback,
                mSandboxLatencyInfo);
        callback.assertLoadSdkIsSuccessful();

        StringWriter stringWriter = new StringWriter();
        mService.dump(new FileDescriptor(), new PrintWriter(stringWriter), new String[0]);
        assertThat(stringWriter.toString()).contains("mHeldSdk size:");
    }

    @Test
    public void testSyncDataFromClient_StoresInClientSharedPreference() throws Exception {
        mService.syncDataFromClient(TEST_UPDATE);

        // Verify that ClientSharedPreference contains the synced data
        SharedPreferences pref = mService.getClientSharedPreferences();
        mExpect.that(pref.getAll().keySet()).containsExactlyElementsIn(TEST_DATA.keySet());
        mExpect.that(pref.getAll().values()).containsExactlyElementsIn(TEST_DATA.values());
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
        mExpect.that(pref.getAll().keySet()).containsExactlyElementsIn(bundle.keySet());
        mExpect.that(pref.getString("string", "")).isEqualTo("value");
        mExpect.that(pref.getBoolean("boolean", false)).isEqualTo(true);
        mExpect.that(pref.getInt("integer", 0)).isEqualTo(1);
        mExpect.that(pref.getFloat("float", 0.0f)).isEqualTo(1.0f);
        mExpect.that(pref.getLong("long", 0L)).isEqualTo(1L);
        mExpect.that(pref.getStringSet("arrayList", Collections.emptySet()))
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
        mExpect.that(pref.getAll().keySet()).containsExactlyElementsIn(TEST_DATA.keySet());
        mExpect.that(pref.getString(KEY_TO_UPDATE, "")).isEqualTo("update");
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
        mSandboxLatencyInfo.setTimeSandboxReceivedCallFromSystemServer(
                TIME_SANDBOX_RECEIVED_CALL_FROM_SYSTEM_SERVER);

        Mockito.when(mInjector.elapsedRealtime())
                .thenReturn(
                        TIME_SANDBOX_CALLED_SDK,
                        TIME_SDK_CALL_COMPLETED,
                        TIME_SANDBOX_CALLED_SYSTEM_SERVER);

        LoadSdkCallback loadSdkCallback = new LoadSdkCallback();
        mService.initialize(new StubSdkToServiceLink());
        mService.loadSdk(
                CLIENT_PACKAGE_NAME,
                mApplicationInfo,
                SDK_NAME,
                SDK_PROVIDER_CLASS,
                new ApplicationInfo(),
                new Bundle(),
                loadSdkCallback,
                mSandboxLatencyInfo);
        loadSdkCallback.assertLoadSdkIsSuccessful();

        mExpect.that(loadSdkCallback.mSandboxLatencyInfo.getSdkLatency())
                .isEqualTo((int) (TIME_SDK_CALL_COMPLETED - TIME_SANDBOX_CALLED_SDK));

        mExpect.that(loadSdkCallback.mSandboxLatencyInfo.getSandboxLatency())
                .isEqualTo(
                        (int)
                                (TIME_SANDBOX_CALLED_SYSTEM_SERVER
                                        - TIME_SANDBOX_RECEIVED_CALL_FROM_SYSTEM_SERVER
                                        - (TIME_SDK_CALL_COMPLETED - TIME_SANDBOX_CALLED_SDK)));
        mExpect.that(loadSdkCallback.mSandboxLatencyInfo.getTimeSandboxCalledSystemServer())
                .isEqualTo(TIME_SANDBOX_CALLED_SYSTEM_SERVER);
    }

    @Test
    public void testLatencyMetrics_unloadSdk_success() throws Exception {
        mSandboxLatencyInfo.setTimeSandboxReceivedCallFromSystemServer(
                TIME_SANDBOX_RECEIVED_CALL_FROM_SYSTEM_SERVER);

        LoadSdkCallback loadSdkCallback = new LoadSdkCallback();
        mService.initialize(new StubSdkToServiceLink());
        mService.loadSdk(
                CLIENT_PACKAGE_NAME,
                mApplicationInfo,
                SDK_NAME,
                SDK_PROVIDER_CLASS,
                new ApplicationInfo(),
                new Bundle(),
                loadSdkCallback,
                mSandboxLatencyInfo);
        loadSdkCallback.assertLoadSdkIsSuccessful();

        Mockito.when(mInjector.elapsedRealtime())
                .thenReturn(
                        // unloadSdk mocks
                        TIME_SANDBOX_CALLED_SDK,
                        TIME_SDK_CALL_COMPLETED,
                        TIME_SANDBOX_CALLED_SYSTEM_SERVER);

        UnloadSdkInSandboxCallbackImpl unloadSdkInSandboxCallback =
                new UnloadSdkInSandboxCallbackImpl();
        mService.unloadSdk(SDK_NAME, unloadSdkInSandboxCallback, mSandboxLatencyInfo);

        mSandboxLatencyInfo = unloadSdkInSandboxCallback.getSandboxLatencyInfo();

        mExpect.that(mSandboxLatencyInfo.getSdkLatency())
                .isEqualTo((int) (TIME_SDK_CALL_COMPLETED - TIME_SANDBOX_CALLED_SDK));

        mExpect.that(mSandboxLatencyInfo.getSandboxLatency())
                .isEqualTo(
                        (int)
                                (TIME_SANDBOX_CALLED_SYSTEM_SERVER
                                        - TIME_SANDBOX_RECEIVED_CALL_FROM_SYSTEM_SERVER
                                        - (TIME_SDK_CALL_COMPLETED - TIME_SANDBOX_CALLED_SDK)));
        mExpect.that(mSandboxLatencyInfo.getTimeSandboxCalledSystemServer())
                .isEqualTo(TIME_SANDBOX_CALLED_SYSTEM_SERVER);
        mExpect.that(mSandboxLatencyInfo.getTimeSandboxCalledSystemServer())
                .isEqualTo(TIME_SANDBOX_CALLED_SYSTEM_SERVER);
        if (SdkLevel.isAtLeastU()) {
            Mockito.verify(mRegistry).unregisterAllActivityHandlersForSdk(SDK_NAME);
        }
    }

    @Test
    public void testLatencyMetrics_requestSurfacePackage_success() throws Exception {
        LoadSdkCallback loadSdkCallback = new LoadSdkCallback();
        mService.initialize(new StubSdkToServiceLink());
        mService.loadSdk(
                CLIENT_PACKAGE_NAME,
                mApplicationInfo,
                SDK_NAME,
                SDK_PROVIDER_CLASS,
                new ApplicationInfo(),
                new Bundle(),
                loadSdkCallback,
                mSandboxLatencyInfo);
        loadSdkCallback.assertLoadSdkIsSuccessful();

        Mockito.when(mInjector.elapsedRealtime())
                .thenReturn(
                        // requestSurfacePackage mocks
                        TIME_SANDBOX_RECEIVED_CALL_FROM_SYSTEM_SERVER,
                        TIME_SANDBOX_CALLED_SDK,
                        TIME_SDK_CALL_COMPLETED,
                        TIME_SANDBOX_CALLED_SYSTEM_SERVER);

        CountDownLatch surfaceLatch = new CountDownLatch(1);
        RequestSurfacePackageCallbackImpl callback =
                new RequestSurfacePackageCallbackImpl(surfaceLatch);
        loadSdkCallback
                .getCallback()
                .onSurfacePackageRequested(
                        new Binder(),
                        mContext.getDisplayId(),
                        500,
                        500,
                        new Bundle(),
                        mSandboxLatencyInfo,
                        callback);
        assertThat(surfaceLatch.await(1, TimeUnit.MINUTES)).isTrue();
        mExpect.that(callback.mSurfacePackage).isNotNull();
        mExpect.that(callback.mSandboxLatencyInfo.getSdkLatency())
                .isEqualTo((int) (TIME_SDK_CALL_COMPLETED - TIME_SANDBOX_CALLED_SDK));
        mExpect.that(callback.mSandboxLatencyInfo.getSandboxLatency())
                .isEqualTo(
                        (int)
                                (TIME_SANDBOX_CALLED_SYSTEM_SERVER
                                        - TIME_SANDBOX_RECEIVED_CALL_FROM_SYSTEM_SERVER
                                        - (TIME_SDK_CALL_COMPLETED - TIME_SANDBOX_CALLED_SDK)));
        mExpect.that(callback.mSandboxLatencyInfo.getTimeSandboxCalledSystemServer())
                .isEqualTo(TIME_SANDBOX_CALLED_SYSTEM_SERVER);
    }

    @Test
    public void testSandboxedSdkHolderSuccessCallbacks() throws Exception {
        SandboxedSdkHolder holder = new SandboxedSdkHolder();
        LoadSdkCallback mCallback = new LoadSdkCallback();
        SdkHolderToSdkSandboxServiceCallbackImpl sdkHolderCallback =
                new SdkHolderToSdkSandboxServiceCallbackImpl();
        holder.init(
                new Bundle(),
                mCallback,
                SDK_PROVIDER_CLASS,
                mLoader,
                new SandboxedSdkContext(
                        mContext,
                        mLoader,
                        CLIENT_PACKAGE_NAME,
                        mApplicationInfo,
                        SDK_NAME,
                        null,
                        null),
                new SdkSandboxServiceImpl.Injector(mContext),
                mSandboxLatencyInfo,
                sdkHolderCallback);
        mCallback.assertLoadSdkIsSuccessful();
        assertThat(sdkHolderCallback.isSuccessful()).isTrue();
    }

    @Test
    public void testReloadingSdkThatInitiallyFailed() throws Exception {
        LoadSdkCallback mCallback = new LoadSdkCallback();
        Bundle params = new Bundle();
        params.putString(THROW_EXCEPTION_KEY, "random-value");
        mService.initialize(new StubSdkToServiceLink());
        mService.loadSdk(
                CLIENT_PACKAGE_NAME,
                mApplicationInfo,
                SDK_NAME,
                SDK_PROVIDER_CLASS,
                new ApplicationInfo(),
                params,
                mCallback,
                mSandboxLatencyInfo);
        mCallback.assertLoadSdkIsUnsuccessful();

        mCallback = new LoadSdkCallback();
        mService.loadSdk(
                CLIENT_PACKAGE_NAME,
                mApplicationInfo,
                SDK_NAME,
                SDK_PROVIDER_CLASS,
                new ApplicationInfo(),
                new Bundle(),
                mCallback,
                mSandboxLatencyInfo);
        mCallback.assertLoadSdkIsSuccessful();
    }

    private ClassLoader getClassLoader(ApplicationInfo appInfo) {
        final ClassLoader current = getClass().getClassLoader();
        final ClassLoader parent = current != null ? current.getParent() : null;
        return new PathClassLoader(appInfo.sourceDir, parent);
    }

    private static class LoadSdkCallback extends ILoadSdkInSandboxCallback.Stub {

        private CountDownLatch mLatch;
        private SandboxLatencyInfo mSandboxLatencyInfo;
        LoadSdkException mLoadSdkException;
        boolean mSuccessful = false;
        int mErrorCode = -1;

        private ISdkSandboxManagerToSdkSandboxCallback mCallback;

        private ISdkSandboxManagerToSdkSandboxCallback getCallback() {
            return mCallback;
        }

        LoadSdkCallback() {
            mLatch = new CountDownLatch(1);
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
            mLoadSdkException = exception;
            mErrorCode = exception.getLoadSdkErrorCode();
            mSuccessful = false;
            mLatch.countDown();
        }

        public void assertLoadSdkIsSuccessful() throws Exception {
            assertThat(mLatch.await(1, TimeUnit.MINUTES)).isTrue();
            if (!mSuccessful) {
                fail(
                        "Load SDK was not successful. errorCode: "
                                + mLoadSdkException.getLoadSdkErrorCode()
                                + ", errorMsg: "
                                + mLoadSdkException.getMessage());
            }
        }

        public void assertLoadSdkIsUnsuccessful() throws Exception {
            assertThat(mLatch.await(1, TimeUnit.MINUTES)).isTrue();
            if (mSuccessful) {
                fail("Load SDK was unexpectedly successful.");
            }
        }
    }

    private static class UnloadSdkInSandboxCallbackImpl extends IUnloadSdkInSandboxCallback.Stub {
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

    private static class SdkHolderToSdkSandboxServiceCallbackImpl
            implements SdkSandboxServiceImpl.SdkHolderToSdkSandboxServiceCallback {
        private final CountDownLatch mLatch;
        private boolean mSuccess = false;

        SdkHolderToSdkSandboxServiceCallbackImpl() {
            mLatch = new CountDownLatch(1);
        }

        @Override
        public void onSuccess() {
            mSuccess = true;
            mLatch.countDown();
        }

        boolean isSuccessful() throws Exception {
            assertThat(mLatch.await(5, TimeUnit.SECONDS)).isTrue();
            return mSuccess;
        }
    }
}

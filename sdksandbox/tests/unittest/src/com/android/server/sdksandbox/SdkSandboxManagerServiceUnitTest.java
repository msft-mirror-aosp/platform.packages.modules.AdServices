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

package com.android.server.sdksandbox;

import static android.app.ActivityManager.RunningAppProcessInfo.IMPORTANCE_FOREGROUND;

import static com.google.common.truth.Truth.assertThat;

import static org.junit.Assert.assertThrows;

import android.Manifest;
import android.app.ActivityManager;
import android.app.sdksandbox.ILoadSdkCallback;
import android.app.sdksandbox.ISendDataCallback;
import android.app.sdksandbox.SandboxedSdkContext;
import android.app.sdksandbox.SdkSandboxManager;
import android.app.sdksandbox.testutils.FakeLoadSdkCallbackBinder;
import android.app.sdksandbox.testutils.FakeRequestSurfacePackageCallbackBinder;
import android.app.sdksandbox.testutils.FakeSdkSandboxLifecycleCallbackBinder;
import android.content.ComponentName;
import android.content.Context;
import android.content.Intent;
import android.content.ServiceConnection;
import android.content.pm.ApplicationInfo;
import android.content.pm.PackageManager;
import android.content.res.AssetManager;
import android.content.res.Resources;
import android.os.Binder;
import android.os.Bundle;
import android.os.IBinder;
import android.os.Process;
import android.os.RemoteException;
import android.util.ArrayMap;
import android.view.SurfaceControlViewHost;

import androidx.annotation.Nullable;
import androidx.test.platform.app.InstrumentationRegistry;

import com.android.dx.mockito.inline.extended.ExtendedMockito;
import com.android.sdksandbox.IDataReceivedCallback;
import com.android.sdksandbox.ILoadSdkInSandboxCallback;
import com.android.sdksandbox.IRequestSurfacePackageFromSdkCallback;
import com.android.sdksandbox.ISdkSandboxManagerToSdkSandboxCallback;
import com.android.sdksandbox.ISdkSandboxService;
import com.android.sdksandbox.service.stats.SdkSandboxStatsLog;
import com.android.server.LocalManagerRegistry;
import com.android.server.pm.PackageManagerLocal;

import org.junit.After;
import org.junit.Before;
import org.junit.Test;
import org.mockito.ArgumentCaptor;
import org.mockito.ArgumentMatchers;
import org.mockito.Mockito;
import org.mockito.MockitoSession;

import java.io.BufferedReader;
import java.io.FileDescriptor;
import java.io.InputStreamReader;
import java.io.PrintWriter;
import java.io.StringWriter;
import java.util.Objects;

/**
 * Unit tests for {@link SdkSandboxManagerService}.
 */
public class SdkSandboxManagerServiceUnitTest {

    private SdkSandboxManagerService mService;
    private ActivityManager mAmSpy;
    private FakeSdkSandboxService mSdkSandboxService;
    private FakeSdkSandboxProvider mProvider;
    private MockitoSession mStaticMockSession = null;
    private Context mSpyContext;

    private static final String CLIENT_PACKAGE_NAME = "com.android.client";
    private static final String SDK_NAME = "com.android.codeprovider";
    private static final String SDK_PROVIDER_PACKAGE = "com.android.codeprovider_1";
    private static final String SDK_PROVIDER_RESOURCES_SDK_NAME =
            "com.android.codeproviderresources";
    private static final String SDK_PROVIDER_RESOURCES_PACKAGE =
            "com.android.codeproviderresources_1";
    private static final String TEST_PACKAGE = "com.android.server.sdksandbox.tests";
    private static final long TIME_APP_CALLED_SYSTEM_SERVER = 1;
    private static final long TIME_SYSTEM_SERVER_RECEIVED_CALL_FROM_APP = 3;
    private static final long START_TIME_TO_LOAD_SANDBOX = 5;
    private static final long END_TIME_TO_LOAD_SANDBOX = 7;
    private static final long TIME_BEFORE_SYSTEM_SERVER_CALLS_SANDBOX = 9;
    private static final long TIME_FAILURE_HANDLED = 11;
    private static final long END_TIME_IN_SYSTEM_SERVER = 15;

    @Before
    public void setup() {
        mStaticMockSession =
                ExtendedMockito.mockitoSession()
                        .mockStatic(LocalManagerRegistry.class)
                        .mockStatic(SdkSandboxStatsLog.class)
                        .startMocking();

        Context context = InstrumentationRegistry.getInstrumentation().getContext();
        mSpyContext = Mockito.spy(context);

        ActivityManager am = context.getSystemService(ActivityManager.class);
        mAmSpy = Mockito.spy(Objects.requireNonNull(am));

        Mockito.when(mSpyContext.getSystemService(ActivityManager.class)).thenReturn(mAmSpy);

        // Required to access <sdk-library> information.
        InstrumentationRegistry.getInstrumentation().getUiAutomation().adoptShellPermissionIdentity(
                Manifest.permission.ACCESS_SHARED_LIBRARIES, Manifest.permission.INSTALL_PACKAGES);
        mSdkSandboxService = Mockito.spy(FakeSdkSandboxService.class);
        mProvider = new FakeSdkSandboxProvider(mSdkSandboxService);

        // Populate LocalManagerRegistry
        ExtendedMockito.doReturn(Mockito.mock(PackageManagerLocal.class))
                .when(() -> LocalManagerRegistry.getManager(PackageManagerLocal.class));

        InjectorForTest injector = new InjectorForTest();

        mService = new SdkSandboxManagerService(mSpyContext, mProvider, injector);
    }

    @After
    public void tearDown() {
        mStaticMockSession.finishMocking();
    }

    /** Mock the ActivityManager::killUid to avoid SecurityException thrown in test. **/
    private void disableKillUid() {
        Mockito.doNothing().when(mAmSpy).killUid(Mockito.anyInt(), Mockito.anyString());
    }

    private void disableForegroundCheck() {
        Mockito.doReturn(IMPORTANCE_FOREGROUND).when(mAmSpy).getUidImportance(Mockito.anyInt());
    }

    /* Ignores network permission checks. */
    private void disableNetworkPermissionChecks() {
        Mockito.doNothing().when(mSpyContext).enforceCallingPermission(
                Mockito.eq("android.permission.INTERNET"), Mockito.anyString());
        Mockito.doNothing().when(mSpyContext).enforceCallingPermission(
                Mockito.eq("android.permission.ACCESS_NETWORK_STATE"), Mockito.anyString());
    }

    @Test
    public void testLoadSdkIsSuccessful() throws Exception {
        disableNetworkPermissionChecks();
        disableForegroundCheck();

        FakeLoadSdkCallbackBinder callback = new FakeLoadSdkCallbackBinder();
        mService.loadSdk(
                TEST_PACKAGE, SDK_NAME, TIME_APP_CALLED_SYSTEM_SERVER, new Bundle(), callback);
        // Assume sdk sandbox loads successfully
        mSdkSandboxService.sendLoadCodeSuccessful();
        assertThat(callback.isLoadSdkSuccessful()).isTrue();
    }

    @Test
    public void testLoadSdkNonExistentCallingPackage() {
        FakeLoadSdkCallbackBinder callback = new FakeLoadSdkCallbackBinder();

        SecurityException thrown =
                assertThrows(
                        SecurityException.class,
                        () ->
                                mService.loadSdk(
                                        "does.not.exist",
                                        SDK_NAME,
                                        TIME_APP_CALLED_SYSTEM_SERVER,
                                        new Bundle(),
                                        callback));
        assertThat(thrown).hasMessageThat().contains("does.not.exist not found");
    }

    @Test
    public void testLoadSdkIncorrectCallingPackage() {
        FakeLoadSdkCallbackBinder callback = new FakeLoadSdkCallbackBinder();
        SecurityException thrown =
                assertThrows(
                        SecurityException.class,
                        () ->
                                mService.loadSdk(
                                        SDK_PROVIDER_PACKAGE,
                                        SDK_NAME,
                                        TIME_APP_CALLED_SYSTEM_SERVER,
                                        new Bundle(),
                                        callback));
        assertThat(thrown).hasMessageThat().contains("does not belong to uid");
    }

    @Test
    public void testLoadSdkPackageDoesNotExist() {
        disableNetworkPermissionChecks();
        disableForegroundCheck();

        FakeLoadSdkCallbackBinder callback = new FakeLoadSdkCallbackBinder();
        mService.loadSdk(
                TEST_PACKAGE,
                "does.not.exist",
                TIME_APP_CALLED_SYSTEM_SERVER,
                new Bundle(),
                callback);

        // Verify loading failed
        assertThat(callback.isLoadSdkSuccessful()).isFalse();
        assertThat(callback.getLoadSdkErrorCode())
                .isEqualTo(SdkSandboxManager.LOAD_SDK_NOT_FOUND);
        assertThat(callback.getLoadSdkErrorMsg()).contains("not found for loading");
    }

    @Test
    public void testLoadSdk_errorFromSdkSandbox() throws Exception {
        disableNetworkPermissionChecks();
        disableForegroundCheck();

        FakeLoadSdkCallbackBinder callback = new FakeLoadSdkCallbackBinder();

        mService.loadSdk(
                TEST_PACKAGE, SDK_NAME, TIME_APP_CALLED_SYSTEM_SERVER, new Bundle(), callback);
        mSdkSandboxService.sendLoadCodeError();

        // Verify loading failed
        assertThat(callback.isLoadSdkSuccessful()).isFalse();
        assertThat(callback.getLoadSdkErrorCode()).isEqualTo(
                SdkSandboxManager.LOAD_SDK_INTERNAL_ERROR);
    }

    @Test
    public void testLoadSdk_errorNoInternet() throws Exception {
        FakeLoadSdkCallbackBinder callback = new FakeLoadSdkCallbackBinder();
        SecurityException thrown =
                assertThrows(
                        SecurityException.class,
                        () ->
                                mService.loadSdk(
                                        TEST_PACKAGE,
                                        SDK_NAME,
                                        TIME_APP_CALLED_SYSTEM_SERVER,
                                        new Bundle(),
                                        callback));

        assertThat(thrown).hasMessageThat().contains(android.Manifest.permission.INTERNET);
    }

    @Test
    public void testLoadSdk_errorNoAccessNetworkState() throws Exception {
        // Stub out internet permission check
        Mockito.doNothing().when(mSpyContext).enforceCallingPermission(
                Mockito.eq("android.permission.INTERNET"), Mockito.anyString());

        FakeLoadSdkCallbackBinder callback = new FakeLoadSdkCallbackBinder();
        SecurityException thrown =
                assertThrows(
                        SecurityException.class,
                        () ->
                                mService.loadSdk(
                                        TEST_PACKAGE,
                                        SDK_NAME,
                                        TIME_APP_CALLED_SYSTEM_SERVER,
                                        new Bundle(),
                                        callback));

        assertThat(thrown).hasMessageThat().contains(
                android.Manifest.permission.ACCESS_NETWORK_STATE);
    }


    @Test
    public void testLoadSdk_successOnFirstLoad_errorOnLoadAgain() throws Exception {
        disableNetworkPermissionChecks();
        disableForegroundCheck();

        // Load it once
        {
            FakeLoadSdkCallbackBinder callback = new FakeLoadSdkCallbackBinder();
            mService.loadSdk(
                    TEST_PACKAGE, SDK_NAME, TIME_APP_CALLED_SYSTEM_SERVER, new Bundle(), callback);
            // Assume SupplementalProcess loads successfully
            mSdkSandboxService.sendLoadCodeSuccessful();
            assertThat(callback.isLoadSdkSuccessful()).isTrue();
        }

        // Load it again
        {
            FakeLoadSdkCallbackBinder callback = new FakeLoadSdkCallbackBinder();
            mService.loadSdk(
                    TEST_PACKAGE, SDK_NAME, TIME_APP_CALLED_SYSTEM_SERVER, new Bundle(), callback);
            // Verify loading failed
            assertThat(callback.isLoadSdkSuccessful()).isFalse();
            assertThat(callback.getLoadSdkErrorCode()).isEqualTo(
                    SdkSandboxManager.LOAD_SDK_ALREADY_LOADED);
            assertThat(callback.getLoadSdkErrorMsg()).contains("has been loaded already");
        }
    }

    @Test
    public void testLoadSdk_errorOnFirstLoad_canBeLoadedAgain() throws Exception {
        disableNetworkPermissionChecks();
        disableForegroundCheck();

        // Load code, but make it fail
        {
            FakeLoadSdkCallbackBinder callback = new FakeLoadSdkCallbackBinder();
            mService.loadSdk(
                    TEST_PACKAGE, SDK_NAME, TIME_APP_CALLED_SYSTEM_SERVER, new Bundle(), callback);
            // Assume SupplementalProcess load fails
            mSdkSandboxService.sendLoadCodeError();
            assertThat(callback.isLoadSdkSuccessful()).isFalse();
        }

        // Caller should be able to retry loading the code
        {
            FakeLoadSdkCallbackBinder callback = new FakeLoadSdkCallbackBinder();
            mService.loadSdk(
                    TEST_PACKAGE, SDK_NAME, TIME_APP_CALLED_SYSTEM_SERVER, new Bundle(), callback);
            // Assume SupplementalProcess loads successfully
            mSdkSandboxService.sendLoadCodeSuccessful();
            assertThat(callback.isLoadSdkSuccessful()).isTrue();
        }
    }

    @Test
    public void testRequestSurfacePackageSdkNotLoaded() {
        disableForegroundCheck();
        // Trying to request package with not exist SDK packageName
        String sdkName = "invalid";
        IllegalArgumentException thrown =
                assertThrows(
                        IllegalArgumentException.class,
                        () ->
                                mService.requestSurfacePackage(
                                        TEST_PACKAGE,
                                        sdkName,
                                        new Binder(),
                                        0,
                                        500,
                                        500,
                                        TIME_APP_CALLED_SYSTEM_SERVER,
                                        new Bundle(),
                                        new FakeRequestSurfacePackageCallbackBinder()));
        assertThat(thrown).hasMessageThat().contains("Sdk " + sdkName + " is not loaded");
    }

    @Test
    public void testRequestSurfacePackage() throws Exception {
        // 1. We first need to collect a proper sdkToken by calling loadCode
        loadSdk();

        // 2. Call request package with the retrieved sdkToken
        FakeRequestSurfacePackageCallbackBinder surfacePackageCallback =
                new FakeRequestSurfacePackageCallbackBinder();
        mService.requestSurfacePackage(
                TEST_PACKAGE,
                SDK_NAME,
                new Binder(),
                0,
                500,
                500,
                TIME_APP_CALLED_SYSTEM_SERVER,
                new Bundle(),
                surfacePackageCallback);
        mSdkSandboxService.sendSurfacePackageReady(surfacePackageCallback);
        assertThat(surfacePackageCallback.isRequestSurfacePackageSuccessful()).isTrue();
    }

    @Test
    public void testRequestSurfacePackageFailedAfterAppDied() throws Exception {
        disableKillUid();
        disableNetworkPermissionChecks();
        disableForegroundCheck();

        FakeLoadSdkCallbackBinder callback = Mockito.spy(new FakeLoadSdkCallbackBinder());
        Mockito.doReturn(Mockito.mock(Binder.class)).when(callback).asBinder();

        ArgumentCaptor<IBinder.DeathRecipient> deathRecipient =
                ArgumentCaptor.forClass(IBinder.DeathRecipient.class);

        mService.loadSdk(
                TEST_PACKAGE, SDK_NAME, TIME_APP_CALLED_SYSTEM_SERVER, new Bundle(), callback);
        mSdkSandboxService.sendLoadCodeSuccessful();
        assertThat(callback.isLoadSdkSuccessful()).isTrue();

        Mockito.verify(callback.asBinder())
                .linkToDeath(deathRecipient.capture(), ArgumentMatchers.eq(0));

        // App Died
        deathRecipient.getValue().binderDied();

        // After App Died
        IllegalArgumentException thrown =
                assertThrows(
                        IllegalArgumentException.class,
                        () ->
                                mService.requestSurfacePackage(
                                        TEST_PACKAGE,
                                        SDK_NAME,
                                        new Binder(),
                                        0,
                                        500,
                                        500,
                                        TIME_APP_CALLED_SYSTEM_SERVER,
                                        new Bundle(),
                                        new FakeRequestSurfacePackageCallbackBinder()));
        assertThat(thrown).hasMessageThat()
                .contains("Sdk " + SDK_NAME + " is not loaded");
    }

    @Test
    public void testSurfacePackageError() throws Exception {
        loadSdk();

        // Assume SurfacePackage encounters an error.
        FakeRequestSurfacePackageCallbackBinder surfacePackageCallback =
                new FakeRequestSurfacePackageCallbackBinder();
        mSdkSandboxService.sendSurfacePackageError(
                SdkSandboxManager.REQUEST_SURFACE_PACKAGE_INTERNAL_ERROR,
                "bad surface",
                surfacePackageCallback);
        assertThat(surfacePackageCallback.getSurfacePackageErrorMsg()).contains("bad surface");
        assertThat(surfacePackageCallback.getSurfacePackageErrorCode())
                .isEqualTo(SdkSandboxManager.REQUEST_SURFACE_PACKAGE_INTERNAL_ERROR);
    }

    @Test
    public void testSendData_SdkNotLoaded() throws Exception {
        disableForegroundCheck();
        IllegalArgumentException thrown =
                assertThrows(
                        IllegalArgumentException.class,
                        () ->
                                mService.sendData(
                                        TEST_PACKAGE,
                                        SDK_NAME,
                                        new Bundle(),
                                        new ISendDataCallback.Stub() {
                                            @Override
                                            public void onSendDataSuccess(Bundle params)
                                                    throws RemoteException {}

                                            @Override
                                            public void onSendDataError(int i, String s)
                                                    throws RemoteException {}
                                        }));
        assertThat(thrown).hasMessageThat().contains("Sdk " + SDK_NAME + " is not loaded");
    }

    @Test
    public void testAddSdkSandboxLifecycleCallback_BeforeStartingSandbox() throws Exception {
        // Register for sandbox death event
        FakeSdkSandboxLifecycleCallbackBinder lifecycleCallback =
                new FakeSdkSandboxLifecycleCallbackBinder();
        mService.addSdkSandboxLifecycleCallback(TEST_PACKAGE, lifecycleCallback);

        // Load SDK and start the sandbox
        loadSdk();

        // Kill the sandbox
        ArgumentCaptor<IBinder.DeathRecipient> deathRecipientCaptor =
                ArgumentCaptor.forClass(IBinder.DeathRecipient.class);
        Mockito.verify(mSdkSandboxService.asBinder(), Mockito.atLeastOnce())
                .linkToDeath(deathRecipientCaptor.capture(), ArgumentMatchers.eq(0));
        IBinder.DeathRecipient deathRecipient = deathRecipientCaptor.getValue();
        deathRecipient.binderDied();

        // Check that death is recorded correctly
        assertThat(lifecycleCallback.isSdkSandboxDeathDetected()).isTrue();
    }

    @Test
    public void testAddSdkSandboxLifecycleCallback_AfterStartingSandbox() throws Exception {
        // Load SDK and start the sandbox
        loadSdk();

        // Register for sandbox death event
        FakeSdkSandboxLifecycleCallbackBinder lifecycleCallback =
                new FakeSdkSandboxLifecycleCallbackBinder();
        mService.addSdkSandboxLifecycleCallback(TEST_PACKAGE, lifecycleCallback);

        // Kill the sandbox
        ArgumentCaptor<IBinder.DeathRecipient> deathRecipientCaptor =
                ArgumentCaptor.forClass(IBinder.DeathRecipient.class);
        Mockito.verify(mSdkSandboxService.asBinder(), Mockito.atLeastOnce())
                .linkToDeath(deathRecipientCaptor.capture(), ArgumentMatchers.eq(0));
        IBinder.DeathRecipient deathRecipient = deathRecipientCaptor.getValue();
        deathRecipient.binderDied();

        // Check that death is recorded correctly
        assertThat(lifecycleCallback.isSdkSandboxDeathDetected()).isTrue();
    }

    @Test
    public void testMultipleAddSdkSandboxLifecycleCallbacks() throws Exception {
        // Load SDK and start the sandbox
        loadSdk();

        // Register for sandbox death event
        FakeSdkSandboxLifecycleCallbackBinder lifecycleCallback1 =
                new FakeSdkSandboxLifecycleCallbackBinder();
        mService.addSdkSandboxLifecycleCallback(TEST_PACKAGE, lifecycleCallback1);

        // Register for sandbox death event again
        FakeSdkSandboxLifecycleCallbackBinder lifecycleCallback2 =
                new FakeSdkSandboxLifecycleCallbackBinder();
        mService.addSdkSandboxLifecycleCallback(TEST_PACKAGE, lifecycleCallback2);

        // Kill the sandbox
        ArgumentCaptor<IBinder.DeathRecipient> deathRecipientCaptor =
                ArgumentCaptor.forClass(IBinder.DeathRecipient.class);
        Mockito.verify(mSdkSandboxService.asBinder(), Mockito.atLeastOnce())
                .linkToDeath(deathRecipientCaptor.capture(), ArgumentMatchers.eq(0));
        IBinder.DeathRecipient deathRecipient = deathRecipientCaptor.getValue();
        deathRecipient.binderDied();

        // Check that death is recorded correctly
        assertThat(lifecycleCallback1.isSdkSandboxDeathDetected()).isTrue();
        assertThat(lifecycleCallback2.isSdkSandboxDeathDetected()).isTrue();
    }

    @Test
    public void testRemoveSdkSandboxLifecycleCallback() throws Exception {
        // Load SDK and start the sandbox
        loadSdk();

        // Register for sandbox death event
        FakeSdkSandboxLifecycleCallbackBinder lifecycleCallback1 =
                new FakeSdkSandboxLifecycleCallbackBinder();
        mService.addSdkSandboxLifecycleCallback(TEST_PACKAGE, lifecycleCallback1);

        // Register for sandbox death event again
        FakeSdkSandboxLifecycleCallbackBinder lifecycleCallback2 =
                new FakeSdkSandboxLifecycleCallbackBinder();
        mService.addSdkSandboxLifecycleCallback(TEST_PACKAGE, lifecycleCallback2);

        // Unregister one of the lifecycle callbacks
        mService.removeSdkSandboxLifecycleCallback(TEST_PACKAGE, lifecycleCallback1);

        // Kill the sandbox
        ArgumentCaptor<IBinder.DeathRecipient> deathRecipientCaptor =
                ArgumentCaptor.forClass(IBinder.DeathRecipient.class);
        Mockito.verify(mSdkSandboxService.asBinder(), Mockito.atLeastOnce())
                .linkToDeath(deathRecipientCaptor.capture(), ArgumentMatchers.eq(0));
        IBinder.DeathRecipient deathRecipient = deathRecipientCaptor.getValue();
        deathRecipient.binderDied();

        // Check that death is recorded correctly
        assertThat(lifecycleCallback1.isSdkSandboxDeathDetected()).isFalse();
        assertThat(lifecycleCallback2.isSdkSandboxDeathDetected()).isTrue();
    }

    @Test(expected = SecurityException.class)
    public void testDumpWithoutPermission() {
        mService.dump(new FileDescriptor(), new PrintWriter(new StringWriter()), new String[0]);
    }

    @Test
    public void testSdkSandboxServiceUnbindingWhenAppDied() throws Exception {
        disableKillUid();
        disableNetworkPermissionChecks();
        disableForegroundCheck();

        ILoadSdkCallback.Stub callback = Mockito.spy(ILoadSdkCallback.Stub.class);
        int callingUid = Binder.getCallingUid();
        final CallingInfo callingInfo = new CallingInfo(callingUid, TEST_PACKAGE);
        assertThat(mProvider.getBoundServiceForApp(callingInfo)).isNull();

        mService.loadSdk(
                TEST_PACKAGE, SDK_NAME, TIME_APP_CALLED_SYSTEM_SERVER, new Bundle(), callback);

        ArgumentCaptor<IBinder.DeathRecipient> deathRecipient = ArgumentCaptor
                .forClass(IBinder.DeathRecipient.class);
        Mockito.verify(callback.asBinder(), Mockito.times(1))
                .linkToDeath(deathRecipient.capture(), Mockito.eq(0));

        assertThat(mProvider.getBoundServiceForApp(callingInfo)).isNotNull();
        deathRecipient.getValue().binderDied();
        assertThat(mProvider.getBoundServiceForApp(callingInfo)).isNull();
    }

    /* Tests resources defined in CodeProviderWithResources may be read. */
    @Test
    public void testCodeContextResourcesAndAssets() throws Exception {
        Context context = InstrumentationRegistry.getInstrumentation().getContext();
        PackageManager pm = context.getPackageManager();
        ApplicationInfo info = pm.getApplicationInfo(SDK_PROVIDER_RESOURCES_PACKAGE,
                PackageManager.MATCH_STATIC_SHARED_AND_SDK_LIBRARIES);
        assertThat(info).isNotNull();
        SandboxedSdkContext sandboxedSdkContext =
                new SandboxedSdkContext(context, CLIENT_PACKAGE_NAME, info, SDK_NAME, null, null);
        Resources resources = sandboxedSdkContext.getResources();

        int integerId = resources.getIdentifier("test_integer", "integer",
                SDK_PROVIDER_RESOURCES_PACKAGE);
        assertThat(integerId).isNotEqualTo(0);
        assertThat(resources.getInteger(integerId)).isEqualTo(1234);

        int stringId = resources.getIdentifier("test_string", "string",
                SDK_PROVIDER_RESOURCES_PACKAGE);
        assertThat(stringId).isNotEqualTo(0);
        assertThat(resources.getString(stringId)).isEqualTo("Test String");

        AssetManager assetManager = resources.getAssets();
        BufferedReader reader = new BufferedReader(
                new InputStreamReader(assetManager.open("test-asset.txt")));
        assertThat(reader.readLine()).isEqualTo("This is a test asset");
    }

    /** Tests that only allowed intents may be sent from the sdk sandbox. */
    @Test
    public void testEnforceAllowedToSendBroadcast() {
        SdkSandboxManagerLocal mSdkSandboxManagerLocal = mService.getLocalManager();

        Intent disallowedIntent = new Intent(Intent.ACTION_SCREEN_ON);
        assertThrows(SecurityException.class,
                () -> mSdkSandboxManagerLocal.enforceAllowedToSendBroadcast(disallowedIntent));
    }

    /** Tests that only allowed activities may be started from the sdk sandbox. */
    @Test
    public void testEnforceAllowedToStartActivity() {
        SdkSandboxManagerLocal mSdkSandboxManagerLocal = mService.getLocalManager();
        Intent allowedIntent = new Intent(Intent.ACTION_VIEW);
        mSdkSandboxManagerLocal.enforceAllowedToStartActivity(allowedIntent);

        Intent disallowedIntent = new Intent(Intent.ACTION_SCREEN_OFF);
        assertThrows(SecurityException.class,
                () -> mSdkSandboxManagerLocal.enforceAllowedToStartActivity(disallowedIntent));
    }

    @Test
    public void testGetSdkSandboxProcessNameForInstrumentation() throws Exception {
        final SdkSandboxManagerLocal localManager = mService.getLocalManager();
        final PackageManager pm =
                InstrumentationRegistry.getInstrumentation().getContext().getPackageManager();
        final ApplicationInfo info = pm.getApplicationInfo(TEST_PACKAGE, 0);
        final String processName = localManager.getSdkSandboxProcessNameForInstrumentation(info);
        assertThat(processName).isEqualTo(TEST_PACKAGE + "_sdk_sandbox_instr");
    }

    @Test
    public void testNotifyInstrumentationStarted_killsSandboxProcess() throws Exception {
        disableKillUid();

        // First load SDK.
        loadSdk();

        final CallingInfo callingInfo = new CallingInfo(Process.myUid(), TEST_PACKAGE);

        // Check that sdk sandbox for TEST_PACKAGE is bound
        assertThat(mProvider.getBoundServiceForApp(callingInfo)).isNotNull();

        final SdkSandboxManagerLocal localManager = mService.getLocalManager();
        localManager.notifyInstrumentationStarted(TEST_PACKAGE, Process.myUid());

        // Verify that sdk sandbox was killed
        Mockito.verify(mAmSpy)
                .killUid(Mockito.eq(Process.toSdkSandboxUid(Process.myUid())), Mockito.anyString());
        assertThat(mProvider.getBoundServiceForApp(callingInfo)).isNull();
    }

    @Test
    public void testNotifyInstrumentationStarted_doesNotAllowLoadSdk() throws Exception {
        disableKillUid();

        // First load SDK.
        loadSdk();

        final CallingInfo callingInfo = new CallingInfo(Process.myUid(), TEST_PACKAGE);

        // Check that sdk sandbox for TEST_PACKAGE is bound
        assertThat(mProvider.getBoundServiceForApp(callingInfo)).isNotNull();

        final SdkSandboxManagerLocal localManager = mService.getLocalManager();
        localManager.notifyInstrumentationStarted(TEST_PACKAGE, Process.myUid());
        assertThat(mProvider.getBoundServiceForApp(callingInfo)).isNull();

        // Try load again, it should throw SecurityException
        FakeLoadSdkCallbackBinder callback2 = new FakeLoadSdkCallbackBinder();
        SecurityException e =
                assertThrows(
                        SecurityException.class,
                        () ->
                                mService.loadSdk(
                                        TEST_PACKAGE,
                                        SDK_NAME,
                                        TIME_APP_CALLED_SYSTEM_SERVER,
                                        new Bundle(),
                                        callback2));
        assertThat(e)
                .hasMessageThat()
                .contains("Currently running instrumentation of this sdk sandbox process");
    }

    @Test
    public void testNotifyInstrumentationFinished_canLoadSdk() throws Exception {
        disableKillUid();
        disableNetworkPermissionChecks();
        disableForegroundCheck();

        final SdkSandboxManagerLocal localManager = mService.getLocalManager();
        localManager.notifyInstrumentationStarted(TEST_PACKAGE, Process.myUid());

        final CallingInfo callingInfo = new CallingInfo(Process.myUid(), TEST_PACKAGE);
        assertThat(mProvider.getBoundServiceForApp(callingInfo)).isNull();

        FakeLoadSdkCallbackBinder callback = new FakeLoadSdkCallbackBinder();
        // Try loading, it should throw SecurityException
        SecurityException e =
                assertThrows(
                        SecurityException.class,
                        () ->
                                mService.loadSdk(
                                        TEST_PACKAGE,
                                        SDK_NAME,
                                        TIME_APP_CALLED_SYSTEM_SERVER,
                                        new Bundle(),
                                        callback));
        assertThat(e)
                .hasMessageThat()
                .contains("Currently running instrumentation of this sdk sandbox process");

        localManager.notifyInstrumentationFinished(TEST_PACKAGE, Process.myUid());

        FakeLoadSdkCallbackBinder callback2 = new FakeLoadSdkCallbackBinder();
        // Now loading should work
        mService.loadSdk(
                TEST_PACKAGE, SDK_NAME, TIME_APP_CALLED_SYSTEM_SERVER, new Bundle(), callback2);
        mSdkSandboxService.sendLoadCodeSuccessful();
        assertThat(callback2.isLoadSdkSuccessful()).isTrue();
        assertThat(mProvider.getBoundServiceForApp(callingInfo)).isNotNull();
    }

    @Test
    public void testGetLoadedSdkLibrariesInfo_afterLoadSdkSuccess() throws Exception {
        loadSdk();
        assertThat(mService.getLoadedSdkLibrariesInfo(TEST_PACKAGE, TIME_APP_CALLED_SYSTEM_SERVER))
                .hasSize(1);
        assertThat(
                        mService.getLoadedSdkLibrariesInfo(
                                        TEST_PACKAGE, TIME_APP_CALLED_SYSTEM_SERVER)
                                .get(0)
                                .getName())
                .isEqualTo(SDK_NAME);
    }

    @Test
    public void testGetLoadedSdkLibrariesInfo_errorLoadingSdk() throws Exception {
        disableNetworkPermissionChecks();
        disableForegroundCheck();

        FakeLoadSdkCallbackBinder callback = new FakeLoadSdkCallbackBinder();

        mService.loadSdk(
                TEST_PACKAGE, SDK_NAME, TIME_APP_CALLED_SYSTEM_SERVER, new Bundle(), callback);
        mSdkSandboxService.sendLoadCodeError();

        // Verify sdkInfo is missing when loading failed
        assertThat(callback.isLoadSdkSuccessful()).isFalse();
        assertThat(callback.getLoadSdkErrorCode())
                .isEqualTo(SdkSandboxManager.LOAD_SDK_INTERNAL_ERROR);
        assertThat(mService.getLoadedSdkLibrariesInfo(TEST_PACKAGE, TIME_APP_CALLED_SYSTEM_SERVER))
                .isEmpty();
    }

    @Test
    public void testEnforceAllowedToStartOrBindService() {
        SdkSandboxManagerLocal mSdkSandboxManagerLocal = mService.getLocalManager();

        Intent disallowedIntent = new Intent();
        disallowedIntent.setComponent(new ComponentName("nonexistent.package", "test"));
        assertThrows(SecurityException.class,
                () -> mSdkSandboxManagerLocal.enforceAllowedToStartOrBindService(disallowedIntent));
    }

    @Test
    public void testAdServicesPackageIsResolved() {
        assertThat(mService.getAdServicesPackageName()).contains("adservices");
    }

    @Test
    public void testUnloadSdkThatIsNotLoaded() {
        disableForegroundCheck();
        assertThrows(
                IllegalArgumentException.class,
                () -> mService.unloadSdk(TEST_PACKAGE, SDK_NAME, TIME_APP_CALLED_SYSTEM_SERVER));
    }

    @Test
    public void testUnloadSdkThatIsLoaded() throws Exception {
        disableKillUid();
        loadSdk();

        FakeLoadSdkCallbackBinder callback2 = new FakeLoadSdkCallbackBinder();
        mService.loadSdk(
                TEST_PACKAGE,
                SDK_PROVIDER_RESOURCES_SDK_NAME,
                TIME_APP_CALLED_SYSTEM_SERVER,
                new Bundle(),
                callback2);
        mSdkSandboxService.sendLoadCodeSuccessful();
        assertThat(callback2.isLoadSdkSuccessful()).isTrue();

        final CallingInfo callingInfo = new CallingInfo(Process.myUid(), TEST_PACKAGE);
        mService.unloadSdk(TEST_PACKAGE, SDK_NAME, TIME_APP_CALLED_SYSTEM_SERVER);

        // One SDK should still be loaded, therefore the sandbox should still be alive.
        assertThat(mProvider.getBoundServiceForApp(callingInfo)).isNotNull();

        mService.unloadSdk(
                TEST_PACKAGE, SDK_PROVIDER_RESOURCES_SDK_NAME, TIME_APP_CALLED_SYSTEM_SERVER);

        // No more SDKs should be loaded at this point. Verify that the sandbox has been killed.
        Mockito.verify(mAmSpy)
                .killUid(Mockito.eq(Process.toSdkSandboxUid(Process.myUid())), Mockito.anyString());
        assertThat(mProvider.getBoundServiceForApp(callingInfo)).isNull();
    }

    @Test
    public void test_syncDataFromClient_verifiesCallingPackageName() {
        SecurityException thrown =
                assertThrows(
                        SecurityException.class,
                        () ->
                                mService.loadSdk(
                                        "does.not.exist",
                                        SDK_NAME,
                                        TIME_APP_CALLED_SYSTEM_SERVER,
                                        new Bundle(),
                                        new FakeLoadSdkCallbackBinder()));
        assertThat(thrown).hasMessageThat().contains("does.not.exist not found");
    }

    @Test
    public void test_syncDataFromClient_sandboxServiceIsNotBound() {
        // Sync data from client
        mService.syncDataFromClient(
                TEST_PACKAGE,
                /*timeAppCalledSystemServer=*/ System.currentTimeMillis(),
                new Bundle());

        // Verify when sandbox is not bound, manager service does not try to sync
        assertThat(mSdkSandboxService.getLastUpdate()).isNull();
    }

    @Test
    public void test_syncDataFromClient_sandboxServiceIsAlreadyBound() {
        // Ensure a sandbox service is already bound for the client
        final CallingInfo callingInfo = new CallingInfo(Process.myUid(), TEST_PACKAGE);
        mProvider.bindService(callingInfo, Mockito.mock(ServiceConnection.class));

        // Sync data from client
        final Bundle data = new Bundle();
        mService.syncDataFromClient(
                TEST_PACKAGE, /*timeAppCalledSystemServer=*/ System.currentTimeMillis(), data);

        // Verify that manager service calls sandbox to sync data
        assertThat(mSdkSandboxService.getLastUpdate()).isSameInstanceAs(data);
    }

    @Test
    public void testStopSdkSandbox() throws Exception {
        disableKillUid();
        loadSdk();

        Mockito.doNothing()
                .when(mSpyContext)
                .enforceCallingPermission(
                        Mockito.eq("com.android.app.sdksandbox.permission.STOP_SDK_SANDBOX"),
                        Mockito.anyString());
        mService.stopSdkSandbox(TEST_PACKAGE);
        int callingUid = Binder.getCallingUid();
        final CallingInfo callingInfo = new CallingInfo(callingUid, TEST_PACKAGE);
        assertThat(mProvider.getBoundServiceForApp(callingInfo)).isEqualTo(null);
    }

    @Test(expected = SecurityException.class)
    public void testStopSdkSandbox_WithoutPermission() {
        mService.stopSdkSandbox(TEST_PACKAGE);
    }

    @Test
    public void testLatencyMetrics_IpcFromAppToSystemServer_LoadSdk() throws Exception {
        loadSdk();

        ExtendedMockito.verify(
                () ->
                        SdkSandboxStatsLog.write(
                                SdkSandboxStatsLog.SANDBOX_API_CALLED,
                                SdkSandboxStatsLog.SANDBOX_API_CALLED__METHOD__LOAD_SDK,
                                (int)
                                        (TIME_SYSTEM_SERVER_RECEIVED_CALL_FROM_APP
                                                - TIME_APP_CALLED_SYSTEM_SERVER),
                                /*success=*/ true,
                                SdkSandboxStatsLog
                                        .SANDBOX_API_CALLED__STAGE__APP_TO_SYSTEM_SERVER));
    }

    @Test
    public void testLatencyMetrics_IpcFromAppToSystemServer_RequestSurfacePackage()
            throws Exception {
        loadSdk();

        // 2. Call request package
        FakeRequestSurfacePackageCallbackBinder surfacePackageCallback =
                new FakeRequestSurfacePackageCallbackBinder();
        mService.requestSurfacePackage(
                TEST_PACKAGE,
                SDK_NAME,
                new Binder(),
                0,
                500,
                500,
                TIME_APP_CALLED_SYSTEM_SERVER,
                new Bundle(),
                surfacePackageCallback);
        ExtendedMockito.verify(
                () ->
                        SdkSandboxStatsLog.write(
                                SdkSandboxStatsLog.SANDBOX_API_CALLED,
                                SdkSandboxStatsLog
                                        .SANDBOX_API_CALLED__METHOD__REQUEST_SURFACE_PACKAGE,
                                (int)
                                        (TIME_SYSTEM_SERVER_RECEIVED_CALL_FROM_APP
                                                - TIME_APP_CALLED_SYSTEM_SERVER),
                                /*success=*/ true,
                                SdkSandboxStatsLog
                                        .SANDBOX_API_CALLED__STAGE__APP_TO_SYSTEM_SERVER));
    }

    @Test
    public void testLatencyMetrics_IpcFromAppToSystemServer_GetLoadedSdkLibrariesInfo() {
        mService.getLoadedSdkLibrariesInfo(TEST_PACKAGE, TIME_APP_CALLED_SYSTEM_SERVER);
        ExtendedMockito.verify(
                () ->
                        SdkSandboxStatsLog.write(
                                SdkSandboxStatsLog.SANDBOX_API_CALLED,
                                SdkSandboxStatsLog
                                        .SANDBOX_API_CALLED__METHOD__GET_LOADED_SDK_LIBRARIES_INFO,
                                (int)
                                        (TIME_SYSTEM_SERVER_RECEIVED_CALL_FROM_APP
                                                - TIME_APP_CALLED_SYSTEM_SERVER),
                                /*success=*/ true,
                                SdkSandboxStatsLog
                                        .SANDBOX_API_CALLED__STAGE__APP_TO_SYSTEM_SERVER));
    }

    @Test
    public void testLatencyMetrics_SystemServerAppToSandbox_GetLoadedSdkLibrariesInfo() {
        SdkSandboxManagerService.Injector injector =
                Mockito.mock(SdkSandboxManagerService.Injector.class);

        SdkSandboxManagerService service =
                new SdkSandboxManagerService(mSpyContext, mProvider, injector);
        // TODO(b/242149555): Update tests to use fake for getting time series.
        Mockito.when(injector.getCurrentTime())
                .thenReturn(TIME_SYSTEM_SERVER_RECEIVED_CALL_FROM_APP, END_TIME_IN_SYSTEM_SERVER);

        service.getLoadedSdkLibrariesInfo(TEST_PACKAGE, TIME_APP_CALLED_SYSTEM_SERVER);

        ExtendedMockito.verify(
                () ->
                        SdkSandboxStatsLog.write(
                                SdkSandboxStatsLog.SANDBOX_API_CALLED,
                                SdkSandboxStatsLog
                                        .SANDBOX_API_CALLED__METHOD__GET_LOADED_SDK_LIBRARIES_INFO,
                                (int)
                                        (END_TIME_IN_SYSTEM_SERVER
                                                - TIME_SYSTEM_SERVER_RECEIVED_CALL_FROM_APP),
                                /*success=*/ true,
                                SdkSandboxStatsLog
                                        .SANDBOX_API_CALLED__STAGE__SYSTEM_SERVER_APP_TO_SANDBOX));
    }

    @Test
    public void testLatencyMetrics_IpcFromAppToSystemServer_UnloadSdk() throws Exception {
        disableKillUid();
        loadSdk();
        mService.unloadSdk(TEST_PACKAGE, SDK_NAME, TIME_APP_CALLED_SYSTEM_SERVER);
        ExtendedMockito.verify(
                () ->
                        SdkSandboxStatsLog.write(
                                SdkSandboxStatsLog.SANDBOX_API_CALLED,
                                SdkSandboxStatsLog.SANDBOX_API_CALLED__METHOD__UNLOAD_SDK,
                                (int)
                                        (TIME_SYSTEM_SERVER_RECEIVED_CALL_FROM_APP
                                                - TIME_APP_CALLED_SYSTEM_SERVER),
                                /*success=*/ true,
                                SdkSandboxStatsLog
                                        .SANDBOX_API_CALLED__STAGE__APP_TO_SYSTEM_SERVER));
    }

    @Test
    public void testLatencyMetrics_IpcFromAppToSystemServer_SyncDataFromClient() {
        // Sync data from client
        final Bundle data = new Bundle();
        mService.syncDataFromClient(TEST_PACKAGE, TIME_APP_CALLED_SYSTEM_SERVER, data);
        ExtendedMockito.verify(
                () ->
                        SdkSandboxStatsLog.write(
                                SdkSandboxStatsLog.SANDBOX_API_CALLED,
                                SdkSandboxStatsLog
                                        .SANDBOX_API_CALLED__METHOD__SYNC_DATA_FROM_CLIENT,
                                (int)
                                        (TIME_SYSTEM_SERVER_RECEIVED_CALL_FROM_APP
                                                - TIME_APP_CALLED_SYSTEM_SERVER),
                                /*success=*/ true,
                                SdkSandboxStatsLog
                                        .SANDBOX_API_CALLED__STAGE__APP_TO_SYSTEM_SERVER));
    }

    // TODO(b/242149555): Update tests to use fake for getting time series.
    @Test
    public void testLatencyMetrics_SystemServerAppToSandbox_LoadSdk() throws Exception {
        disableNetworkPermissionChecks();
        disableForegroundCheck();
        SdkSandboxManagerService.Injector injector =
                Mockito.mock(SdkSandboxManagerService.Injector.class);

        SdkSandboxManagerService service =
                new SdkSandboxManagerService(mSpyContext, mProvider, injector);
        Mockito.when(injector.getCurrentTime())
                .thenReturn(
                        TIME_SYSTEM_SERVER_RECEIVED_CALL_FROM_APP,
                        START_TIME_TO_LOAD_SANDBOX,
                        END_TIME_TO_LOAD_SANDBOX,
                        TIME_BEFORE_SYSTEM_SERVER_CALLS_SANDBOX);
        FakeLoadSdkCallbackBinder callback = new FakeLoadSdkCallbackBinder();
        service.loadSdk(
                TEST_PACKAGE, SDK_NAME, TIME_APP_CALLED_SYSTEM_SERVER, new Bundle(), callback);
        // Assume sdk sandbox loads successfully
        mSdkSandboxService.sendLoadCodeSuccessful();

        final int timeToLoadSdk = (int) (END_TIME_TO_LOAD_SANDBOX - START_TIME_TO_LOAD_SANDBOX);

        ExtendedMockito.verify(
                () ->
                        SdkSandboxStatsLog.write(
                                SdkSandboxStatsLog.SANDBOX_API_CALLED,
                                SdkSandboxStatsLog.SANDBOX_API_CALLED__METHOD__LOAD_SDK,
                                timeToLoadSdk,
                                /*success=*/ true,
                                SdkSandboxStatsLog.SANDBOX_API_CALLED__STAGE__LOAD_SANDBOX));

        int timeSystemServerAppToSandbox =
                (int)
                        (TIME_BEFORE_SYSTEM_SERVER_CALLS_SANDBOX
                                - TIME_SYSTEM_SERVER_RECEIVED_CALL_FROM_APP
                                - timeToLoadSdk);

        ExtendedMockito.verify(
                () ->
                        SdkSandboxStatsLog.write(
                                SdkSandboxStatsLog.SANDBOX_API_CALLED,
                                SdkSandboxStatsLog.SANDBOX_API_CALLED__METHOD__LOAD_SDK,
                                timeSystemServerAppToSandbox,
                                /*success=*/ true,
                                SdkSandboxStatsLog
                                        .SANDBOX_API_CALLED__STAGE__SYSTEM_SERVER_APP_TO_SANDBOX));
    }

    @Test
    public void testLatencyMetrics_SystemServerAppToSandbox_LoadSdk_FailureOnMultiLoad()
            throws Exception {
        disableNetworkPermissionChecks();
        disableForegroundCheck();

        SdkSandboxManagerService.Injector injector =
                Mockito.mock(SdkSandboxManagerService.Injector.class);

        SdkSandboxManagerService service =
                new SdkSandboxManagerService(mSpyContext, mProvider, injector);
        Mockito.when(injector.getCurrentTime())
                .thenReturn(
                        TIME_SYSTEM_SERVER_RECEIVED_CALL_FROM_APP,
                        START_TIME_TO_LOAD_SANDBOX,
                        END_TIME_TO_LOAD_SANDBOX,
                        TIME_BEFORE_SYSTEM_SERVER_CALLS_SANDBOX,
                        TIME_SYSTEM_SERVER_RECEIVED_CALL_FROM_APP,
                        TIME_FAILURE_HANDLED);

        // Load it once
        {
            FakeLoadSdkCallbackBinder callback = new FakeLoadSdkCallbackBinder();
            service.loadSdk(
                    TEST_PACKAGE, SDK_NAME, TIME_APP_CALLED_SYSTEM_SERVER, new Bundle(), callback);
            mSdkSandboxService.sendLoadCodeSuccessful();
        }

        // Load it again
        {
            FakeLoadSdkCallbackBinder callback = new FakeLoadSdkCallbackBinder();
            service.loadSdk(
                    TEST_PACKAGE, SDK_NAME, TIME_APP_CALLED_SYSTEM_SERVER, new Bundle(), callback);
        }

        int timeSystemServerAppToSandbox =
                (int) (TIME_FAILURE_HANDLED - TIME_SYSTEM_SERVER_RECEIVED_CALL_FROM_APP);

        ExtendedMockito.verify(
                () ->
                        SdkSandboxStatsLog.write(
                                SdkSandboxStatsLog.SANDBOX_API_CALLED,
                                SdkSandboxStatsLog.SANDBOX_API_CALLED__METHOD__LOAD_SDK,
                                timeSystemServerAppToSandbox,
                                /*success=*/ false,
                                SdkSandboxStatsLog
                                        .SANDBOX_API_CALLED__STAGE__SYSTEM_SERVER_APP_TO_SANDBOX));
    }

    @Test
    public void testLatencyMetrics_SystemServerAppToSandbox_LoadSdk_InvalidSdkName() {
        disableNetworkPermissionChecks();
        disableForegroundCheck();

        SdkSandboxManagerService.Injector injector =
                Mockito.mock(SdkSandboxManagerService.Injector.class);

        SdkSandboxManagerService service =
                new SdkSandboxManagerService(mSpyContext, mProvider, injector);
        Mockito.when(injector.getCurrentTime())
                .thenReturn(TIME_SYSTEM_SERVER_RECEIVED_CALL_FROM_APP, TIME_FAILURE_HANDLED);

        FakeLoadSdkCallbackBinder callback = new FakeLoadSdkCallbackBinder();
        service.loadSdk(
                TEST_PACKAGE, "RANDOM", TIME_APP_CALLED_SYSTEM_SERVER, new Bundle(), callback);

        ExtendedMockito.verify(
                () ->
                        SdkSandboxStatsLog.write(
                                SdkSandboxStatsLog.SANDBOX_API_CALLED,
                                SdkSandboxStatsLog.SANDBOX_API_CALLED__METHOD__LOAD_SDK,
                                (int)
                                        (TIME_FAILURE_HANDLED
                                                - TIME_SYSTEM_SERVER_RECEIVED_CALL_FROM_APP),
                                /*success=*/ false,
                                SdkSandboxStatsLog
                                        .SANDBOX_API_CALLED__STAGE__SYSTEM_SERVER_APP_TO_SANDBOX));

        ExtendedMockito.verify(
                () ->
                        SdkSandboxStatsLog.write(
                                Mockito.anyInt(),
                                Mockito.anyInt(),
                                Mockito.anyInt(),
                                Mockito.anyBoolean(),
                                Mockito.anyInt()),
                Mockito.times(2));
    }

    @Test
    public void testLatencyMetrics_SystemServerAppToSandbox_LoadSdk_FailureOnAppDeath()
            throws RemoteException {
        disableNetworkPermissionChecks();
        disableForegroundCheck();

        FakeLoadSdkCallbackBinder callback = Mockito.mock(FakeLoadSdkCallbackBinder.class);
        IBinder binder = Mockito.mock(IBinder.class);
        Mockito.when(callback.asBinder()).thenReturn(binder);

        Mockito.doThrow(new RemoteException())
                .when(binder)
                .linkToDeath(Mockito.any(), Mockito.anyInt());

        SdkSandboxManagerService.Injector injector =
                Mockito.mock(SdkSandboxManagerService.Injector.class);

        SdkSandboxManagerService service =
                new SdkSandboxManagerService(mSpyContext, mProvider, injector);
        Mockito.when(injector.getCurrentTime())
                .thenReturn(TIME_SYSTEM_SERVER_RECEIVED_CALL_FROM_APP, TIME_FAILURE_HANDLED);

        service.loadSdk(
                TEST_PACKAGE, SDK_NAME, TIME_APP_CALLED_SYSTEM_SERVER, new Bundle(), callback);

        ExtendedMockito.verify(
                () ->
                        SdkSandboxStatsLog.write(
                                SdkSandboxStatsLog.SANDBOX_API_CALLED,
                                SdkSandboxStatsLog.SANDBOX_API_CALLED__METHOD__LOAD_SDK,
                                (int)
                                        (TIME_FAILURE_HANDLED
                                                - TIME_SYSTEM_SERVER_RECEIVED_CALL_FROM_APP),
                                /*success=*/ false,
                                SdkSandboxStatsLog
                                        .SANDBOX_API_CALLED__STAGE__SYSTEM_SERVER_APP_TO_SANDBOX));

        ExtendedMockito.verify(
                () ->
                        SdkSandboxStatsLog.write(
                                Mockito.anyInt(),
                                Mockito.anyInt(),
                                Mockito.anyInt(),
                                Mockito.anyBoolean(),
                                Mockito.anyInt()),
                Mockito.times(2));
    }

    @Test
    public void testLatencyMetrics_SystemServerAppToSandbox_LoadSdk_LoadSandboxFailure() {
        disableNetworkPermissionChecks();
        disableForegroundCheck();

        SdkSandboxManagerService.Injector injector =
                Mockito.mock(SdkSandboxManagerService.Injector.class);

        SdkSandboxManagerService service =
                new SdkSandboxManagerService(mSpyContext, mProvider, injector);

        mProvider.disableBinding();

        Mockito.when(injector.getCurrentTime())
                .thenReturn(
                        TIME_SYSTEM_SERVER_RECEIVED_CALL_FROM_APP,
                        START_TIME_TO_LOAD_SANDBOX,
                        TIME_FAILURE_HANDLED);

        FakeLoadSdkCallbackBinder callback = new FakeLoadSdkCallbackBinder();
        service.loadSdk(
                TEST_PACKAGE, SDK_NAME, TIME_APP_CALLED_SYSTEM_SERVER, new Bundle(), callback);

        ExtendedMockito.verify(
                () ->
                        SdkSandboxStatsLog.write(
                                SdkSandboxStatsLog.SANDBOX_API_CALLED,
                                SdkSandboxStatsLog.SANDBOX_API_CALLED__METHOD__LOAD_SDK,
                                (int) (TIME_FAILURE_HANDLED - START_TIME_TO_LOAD_SANDBOX),
                                /*success=*/ false,
                                SdkSandboxStatsLog.SANDBOX_API_CALLED__STAGE__LOAD_SANDBOX));
    }

    private void loadSdk() throws RemoteException {
        disableNetworkPermissionChecks();
        disableForegroundCheck();
        FakeLoadSdkCallbackBinder callback = new FakeLoadSdkCallbackBinder();
        mService.loadSdk(
                TEST_PACKAGE, SDK_NAME, TIME_APP_CALLED_SYSTEM_SERVER, new Bundle(), callback);
        mSdkSandboxService.sendLoadCodeSuccessful();
        assertThat(callback.isLoadSdkSuccessful()).isTrue();
    }

    /** Fake service provider that returns local instance of {@link SdkSandboxServiceProvider} */
    private static class FakeSdkSandboxProvider implements SdkSandboxServiceProvider {
        private final ISdkSandboxService mSdkSandboxService;
        private final ArrayMap<CallingInfo, ISdkSandboxService> mService = new ArrayMap<>();

        // When set to true, this will fail the bindService call
        private boolean mFailBinding = false;

        FakeSdkSandboxProvider(ISdkSandboxService service) {
            mSdkSandboxService = service;
        }

        public void disableBinding() {
            mFailBinding = true;
        }

        @Override
        public void bindService(CallingInfo callingInfo, ServiceConnection serviceConnection) {
            if (mFailBinding) {
                serviceConnection.onNullBinding(new ComponentName("random", "component"));
                return;
            }

            if (mService.containsKey(callingInfo)) {
                return;
            }
            mService.put(callingInfo, mSdkSandboxService);
            serviceConnection.onServiceConnected(null, mSdkSandboxService.asBinder());
        }

        @Override
        public void unbindService(CallingInfo callingInfo) {
            mService.remove(callingInfo);
        }

        @Nullable
        @Override
        public ISdkSandboxService getBoundServiceForApp(CallingInfo callingInfo) {
            return mService.get(callingInfo);
        }

        @Override
        public void setBoundServiceForApp(CallingInfo callingInfo,
                @Nullable ISdkSandboxService service) {
            mService.put(callingInfo, service);
        }
    }

    public static class FakeSdkSandboxService extends ISdkSandboxService.Stub {
        private ILoadSdkInSandboxCallback mLoadSdkInSandboxCallback;
        private final ISdkSandboxManagerToSdkSandboxCallback mManagerToSdkCallback;

        private boolean mSurfacePackageRequested = false;
        private Bundle mLastSyncUpdate = null;

        FakeSdkSandboxService() {
            mManagerToSdkCallback = new FakeManagerToSdkCallback();
        }

        @Override
        public void loadSdk(
                String callingPackageName,
                IBinder codeToken,
                ApplicationInfo info,
                String sdkName,
                String sdkProviderClass,
                String ceDataDir,
                String deDataDir,
                Bundle params,
                ILoadSdkInSandboxCallback callback) {
            mLoadSdkInSandboxCallback = callback;
        }

        @Override
        public void unloadSdk(IBinder sdkToken) {}

        @Override
        public void syncDataFromClient(Bundle data) {
            mLastSyncUpdate = data;
        }

        @Nullable
        public Bundle getLastUpdate() {
            return mLastSyncUpdate;
        }

        void sendLoadCodeSuccessful() throws RemoteException {
            mLoadSdkInSandboxCallback.onLoadSdkSuccess(new Bundle(), mManagerToSdkCallback);
        }

        void sendLoadCodeError() throws RemoteException {
            mLoadSdkInSandboxCallback.onLoadSdkError(
                    SdkSandboxManager.LOAD_SDK_INTERNAL_ERROR, "Internal error");
        }

        void sendSurfacePackageReady(FakeRequestSurfacePackageCallbackBinder callback)
                throws RemoteException {
            if (mSurfacePackageRequested) {
                callback.onSurfacePackageReady(
                        Mockito.mock(SurfaceControlViewHost.SurfacePackage.class), 0, new Bundle());
            }
        }

        void sendSurfacePackageError(
                int errorCode, String errorMsg, FakeRequestSurfacePackageCallbackBinder callback)
                throws RemoteException {
            callback.onSurfacePackageError(errorCode, errorMsg);
        }

        private class FakeManagerToSdkCallback extends ISdkSandboxManagerToSdkSandboxCallback.Stub {
            @Override
            public void onSurfacePackageRequested(
                    IBinder hostToken,
                    int displayId,
                    int width,
                    int height,
                    Bundle extraParams,
                    IRequestSurfacePackageFromSdkCallback iRequestSurfacePackageFromSdkCallback) {
                mSurfacePackageRequested = true;
            }

            @Override
            public void onDataReceived(Bundle data, IDataReceivedCallback callback) {}
        }
    }

    public static class InjectorForTest extends SdkSandboxManagerService.Injector {

        @Override
        public long getCurrentTime() {
            return TIME_SYSTEM_SERVER_RECEIVED_CALL_FROM_APP;
        }
    }
}

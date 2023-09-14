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

import static android.app.sdksandbox.SdkSandboxManager.EXTRA_DISPLAY_ID;
import static android.app.sdksandbox.SdkSandboxManager.EXTRA_HEIGHT_IN_PIXELS;
import static android.app.sdksandbox.SdkSandboxManager.EXTRA_HOST_TOKEN;
import static android.app.sdksandbox.SdkSandboxManager.EXTRA_WIDTH_IN_PIXELS;
import static android.app.sdksandbox.SdkSandboxManager.LOAD_SDK_INTERNAL_ERROR;
import static androidx.lifecycle.Lifecycle.State;
import static androidx.test.platform.app.InstrumentationRegistry.getInstrumentation;
import static com.android.sdksandbox.flags.Flags.FLAG_SANDBOX_ACTIVITY_SDK_BASED_CONTEXT;
import static com.google.common.truth.Truth.assertThat;
import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertNotNull;
import static org.junit.Assert.assertThrows;
import static org.junit.Assert.fail;
import static org.junit.Assume.assumeTrue;

import android.app.Activity;
import android.app.ActivityManager;
import android.app.sdksandbox.AppOwnedSdkSandboxInterface;
import android.app.sdksandbox.LoadSdkException;
import android.app.sdksandbox.SandboxedSdk;
import android.app.sdksandbox.SdkSandboxManager;
import android.app.sdksandbox.testutils.FakeLoadSdkCallback;
import android.app.sdksandbox.testutils.FakeRequestSurfacePackageCallback;
import android.app.sdksandbox.testutils.FakeSdkSandboxProcessDeathCallback;
import android.app.sdksandbox.testutils.SdkSandboxDeviceSupportedRule;
import android.content.Context;
import android.content.Intent;
import android.content.pm.PackageInfo;
import android.content.pm.PackageManager;
import android.content.pm.PermissionInfo;
import android.os.Binder;
import android.os.Bundle;
import android.os.IBinder;
import android.os.Parcel;
import android.os.RemoteException;
import android.platform.test.annotations.RequiresFlagsDisabled;
import android.platform.test.annotations.RequiresFlagsEnabled;
import android.platform.test.flag.junit.CheckFlagsRule;
import android.platform.test.flag.junit.DeviceFlagsValueProvider;
import android.provider.DeviceConfig;
import android.view.Surface;

import androidx.lifecycle.Lifecycle;
import androidx.test.core.app.ActivityScenario;
import androidx.test.ext.junit.rules.ActivityScenarioRule;
import androidx.test.platform.app.InstrumentationRegistry;
import androidx.test.uiautomator.UiDevice;

import com.android.compatibility.common.util.DeviceConfigStateHelper;
import com.android.ctssdkprovider.IActivityActionExecutor;
import com.android.ctssdkprovider.IActivityStarter;
import com.android.ctssdkprovider.ICtsSdkProviderApi;
import com.android.modules.utils.build.SdkLevel;

import com.google.common.truth.Expect;

import org.junit.Before;
import org.junit.Ignore;
import org.junit.Rule;
import org.junit.Test;
import org.junit.runner.RunWith;
import org.junit.runners.JUnit4;

import java.util.Arrays;
import java.util.List;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.TimeUnit;

/** End-to-end tests of {@link SdkSandboxManager} APIs. */
@RunWith(JUnit4.class)
public final class SdkSandboxManagerTest {

    private static final String TAG = SdkSandboxManagerTest.class.getSimpleName();
    private static final String NON_EXISTENT_SDK = "com.android.not_exist";

    private static final String APP_OWNED_SDK_SANDBOX_INTERFACE_NAME =
            "com.android.ctsappownedsdksandboxinterface";
    private static final String SDK_NAME_1 = "com.android.ctssdkprovider";
    private static final String SDK_NAME_2 = "com.android.emptysdkprovider";

    private static final String TEST_OPTION = "test-option";
    private static final String OPTION_THROW_INTERNAL_ERROR = "internal-error";
    private static final String OPTION_THROW_REQUEST_SURFACE_PACKAGE_ERROR = "rsp-error";

    private static final String NAMESPACE_WINDOW_MANAGER = "window_manager";
    private static final String ASM_RESTRICTIONS_ENABLED =
            "ActivitySecurity__asm_restrictions_enabled";
    private static final String CUSTOMIZED_SDK_CONTEXT_ENABLED =
            "sdksandbox_customized_sdk_context_enabled";
    private static final String UNREGISTER_BEFORE_STARTING_KEY = "UNREGISTER_BEFORE_STARTING_KEY";
    private static final String ACTIVITY_STARTER_KEY = "ACTIVITY_STARTER_KEY";
    private static final UiDevice sUiDevice = UiDevice.getInstance(getInstrumentation());

    @Rule(order = 0)
    public final SdkSandboxDeviceSupportedRule supportedRule = new SdkSandboxDeviceSupportedRule();

    @Rule(order = 1)
    public final ActivityScenarioRule<TestActivity> activityScenarioRule =
            new ActivityScenarioRule<>(TestActivity.class);

    @Rule(order = 2)
    public final Expect expect = Expect.create();

    @Rule
    public final CheckFlagsRule mCheckFlagsRule = DeviceFlagsValueProvider.createCheckFlagsRule();

    private ActivityScenario<TestActivity> mScenario;

    private SdkSandboxManager mSdkSandboxManager;

    private final DeviceConfigStateHelper mDeviceConfig =
            new DeviceConfigStateHelper(NAMESPACE_WINDOW_MANAGER);

    @Before
    public void setup() throws Exception {
        Context context = InstrumentationRegistry.getInstrumentation().getContext();
        mSdkSandboxManager = context.getSystemService(SdkSandboxManager.class);
        killSandboxIfExists();
        mScenario = activityScenarioRule.getScenario();
        mDeviceConfig.set(ASM_RESTRICTIONS_ENABLED, "1");
        sUiDevice.setOrientationNatural();
    }

    @Test
    public void testGetSdkSandboxState() {
        int state = SdkSandboxManager.getSdkSandboxState();
        assertThat(state).isEqualTo(SdkSandboxManager.SDK_SANDBOX_STATE_ENABLED_PROCESS_ISOLATION);
    }

    @Test
    public void testLoadSdkSuccessfully() {
        final FakeLoadSdkCallback callback = new FakeLoadSdkCallback();
        mSdkSandboxManager.loadSdk(SDK_NAME_1, new Bundle(), Runnable::run, callback);
        callback.assertLoadSdkIsSuccessful();
        assertNotNull(callback.getSandboxedSdk());
        assertNotNull(callback.getSandboxedSdk().getInterface());
    }

    @Test
    public void testRegisterAndGetAppOwnedSdkSandboxInterface() throws Exception {
        try {
            IBinder iBinder = new Binder();
            mSdkSandboxManager.registerAppOwnedSdkSandboxInterface(
                    new AppOwnedSdkSandboxInterface(
                            APP_OWNED_SDK_SANDBOX_INTERFACE_NAME,
                            /*version=*/ 0,
                            /*interfaceIBinder=*/ iBinder));
            final List<AppOwnedSdkSandboxInterface> appOwnedSdkSandboxInterfaceList =
                    mSdkSandboxManager.getAppOwnedSdkSandboxInterfaces();
            assertThat(appOwnedSdkSandboxInterfaceList).hasSize(1);
            assertThat(appOwnedSdkSandboxInterfaceList.get(0).getName())
                    .isEqualTo(APP_OWNED_SDK_SANDBOX_INTERFACE_NAME);
            assertThat(appOwnedSdkSandboxInterfaceList.get(0).getVersion()).isEqualTo(0);
            assertThat(appOwnedSdkSandboxInterfaceList.get(0).getInterface()).isEqualTo(iBinder);
        } finally {
            mSdkSandboxManager.unregisterAppOwnedSdkSandboxInterface(
                    APP_OWNED_SDK_SANDBOX_INTERFACE_NAME);
        }
    }

    @Test
    public void testUnregisterAppOwnedSdkSandboxInterface() throws Exception {
        mSdkSandboxManager.registerAppOwnedSdkSandboxInterface(
                new AppOwnedSdkSandboxInterface(
                        APP_OWNED_SDK_SANDBOX_INTERFACE_NAME,
                        /*version=*/ 0,
                        /*interfaceIBinder=*/ new Binder()));
        mSdkSandboxManager.unregisterAppOwnedSdkSandboxInterface(
                APP_OWNED_SDK_SANDBOX_INTERFACE_NAME);
        assertThat(mSdkSandboxManager.getAppOwnedSdkSandboxInterfaces()).hasSize(0);
    }

    @Test
    public void testRegisterAppOwnedSdkSandboxInterfaceAlreadyRegistered() throws Exception {
        try {
            mSdkSandboxManager.registerAppOwnedSdkSandboxInterface(
                    new AppOwnedSdkSandboxInterface(
                            APP_OWNED_SDK_SANDBOX_INTERFACE_NAME,
                            /*version=*/ 0,
                            /*interfaceIBinder=*/ new Binder()));
            assertThrows(
                    RuntimeException.class,
                    () ->
                            mSdkSandboxManager.registerAppOwnedSdkSandboxInterface(
                                    new AppOwnedSdkSandboxInterface(
                                            APP_OWNED_SDK_SANDBOX_INTERFACE_NAME,
                                            /*version=*/ 0,
                                            /*interfaceIBinder=*/ new Binder())));
        } finally {
            mSdkSandboxManager.unregisterAppOwnedSdkSandboxInterface(
                    APP_OWNED_SDK_SANDBOX_INTERFACE_NAME);
        }
    }

    @Test
    public void testGetSandboxedSdkSuccessfully() {
        loadSdk();

        List<SandboxedSdk> sandboxedSdks = mSdkSandboxManager.getSandboxedSdks();

        assertThat(sandboxedSdks.size()).isEqualTo(1);
        assertThat(sandboxedSdks.get(0).getSharedLibraryInfo().getName()).isEqualTo(SDK_NAME_1);

        mSdkSandboxManager.unloadSdk(SDK_NAME_1);
        List<SandboxedSdk> sandboxedSdksAfterUnload = mSdkSandboxManager.getSandboxedSdks();
        assertThat(sandboxedSdksAfterUnload.size()).isEqualTo(0);
    }

    @Test
    public void testLoadSdkAndCheckClassloader() throws Exception {
        ICtsSdkProviderApi sdk = loadSdk();
        sdk.checkClassloaders();
    }

    @Test
    public void testGetOpPackageName() throws Exception {
        ICtsSdkProviderApi sdk = loadSdk();
        final PackageManager pm =
                InstrumentationRegistry.getInstrumentation().getContext().getPackageManager();
        assertThat(sdk.getOpPackageName()).isEqualTo(pm.getSdkSandboxPackageName());
    }

    @Test
    public void testRetryLoadSameSdkShouldFail() {
        FakeLoadSdkCallback callback = new FakeLoadSdkCallback();

        mSdkSandboxManager.loadSdk(SDK_NAME_1, new Bundle(), Runnable::run, callback);
        callback.assertLoadSdkIsSuccessful();

        callback = new FakeLoadSdkCallback();
        mSdkSandboxManager.loadSdk(SDK_NAME_1, new Bundle(), Runnable::run, callback);
        callback.assertLoadSdkIsUnsuccessful();
        assertThat(callback.getLoadSdkErrorCode())
                .isEqualTo(SdkSandboxManager.LOAD_SDK_ALREADY_LOADED);
    }

    @Test
    public void testLoadNonExistentSdk() {
        final FakeLoadSdkCallback callback = new FakeLoadSdkCallback();

        mSdkSandboxManager.loadSdk(NON_EXISTENT_SDK, new Bundle(), Runnable::run, callback);
        callback.assertLoadSdkIsUnsuccessful();
        assertThat(callback.getLoadSdkErrorCode())
                .isEqualTo(SdkSandboxManager.LOAD_SDK_NOT_FOUND);
        LoadSdkException loadSdkException = callback.getLoadSdkException();
        assertThat(loadSdkException.getExtraInformation()).isNotNull();
        assertThat(loadSdkException.getExtraInformation().isEmpty()).isTrue();
    }

    @Test
    public void testLoadSdkWithInternalErrorShouldFail() throws Exception {
        final FakeLoadSdkCallback callback = new FakeLoadSdkCallback();
        Bundle params = new Bundle();
        params.putString(TEST_OPTION, OPTION_THROW_INTERNAL_ERROR);
        mSdkSandboxManager.loadSdk(SDK_NAME_1, params, Runnable::run, callback);
        callback.assertLoadSdkIsUnsuccessful();
        assertThat(callback.getLoadSdkErrorCode())
                .isEqualTo(SdkSandboxManager.LOAD_SDK_SDK_DEFINED_ERROR);
    }

    @Test
    public void testUnloadAndReloadSdk() throws Exception {
        final FakeLoadSdkCallback callback = new FakeLoadSdkCallback();
        mSdkSandboxManager.loadSdk(SDK_NAME_1, new Bundle(), Runnable::run, callback);
        callback.assertLoadSdkIsSuccessful();

        mSdkSandboxManager.unloadSdk(SDK_NAME_1);
        // Wait till SDK is unloaded.
        Thread.sleep(2000);

        // Calls to an unloaded SDK should fail.
        final FakeRequestSurfacePackageCallback requestSurfacePackageCallback =
                new FakeRequestSurfacePackageCallback();
        mSdkSandboxManager.requestSurfacePackage(
                SDK_NAME_1,
                getRequestSurfacePackageParams(),
                Runnable::run,
                requestSurfacePackageCallback);

        assertThat(requestSurfacePackageCallback.isRequestSurfacePackageSuccessful()).isFalse();
        assertThat(requestSurfacePackageCallback.getSurfacePackageErrorCode())
                .isEqualTo(SdkSandboxManager.REQUEST_SURFACE_PACKAGE_SDK_NOT_LOADED);

        // SDK can be reloaded after being unloaded.
        final FakeLoadSdkCallback callback2 = new FakeLoadSdkCallback();
        mSdkSandboxManager.loadSdk(SDK_NAME_1, new Bundle(), Runnable::run, callback2);
        callback2.assertLoadSdkIsSuccessful();
    }

    @Test
    public void testUnloadNonexistentSdk() {
        final FakeLoadSdkCallback callback = new FakeLoadSdkCallback();
        mSdkSandboxManager.loadSdk(SDK_NAME_1, new Bundle(), Runnable::run, callback);
        callback.assertLoadSdkIsSuccessful();

        final String nonexistentSdk = "com.android.nonexistent";
        // Unloading does nothing - call should go through without error.
        mSdkSandboxManager.unloadSdk(nonexistentSdk);
    }

    @Test
    public void testReloadingSdkDoesNotInvalidateIt() {

        final FakeLoadSdkCallback callback = new FakeLoadSdkCallback();
        mSdkSandboxManager.loadSdk(SDK_NAME_1, new Bundle(), Runnable::run, callback);
        callback.assertLoadSdkIsSuccessful();
        SandboxedSdk sandboxedSdk = callback.getSandboxedSdk();
        assertNotNull(sandboxedSdk.getInterface());

        // Attempt to load the SDK again and see that it fails.
        final FakeLoadSdkCallback reloadCallback = new FakeLoadSdkCallback();
        mSdkSandboxManager.loadSdk(SDK_NAME_1, new Bundle(), Runnable::run, reloadCallback);
        reloadCallback.assertLoadSdkIsUnsuccessful();

        // SDK's interface should still be obtainable.
        assertNotNull(sandboxedSdk.getInterface());

        // Further calls to the SDK should still be valid.
        final FakeRequestSurfacePackageCallback surfacePackageCallback =
                new FakeRequestSurfacePackageCallback();
        mSdkSandboxManager.requestSurfacePackage(
                SDK_NAME_1,
                getRequestSurfacePackageParams(),
                Runnable::run,
                surfacePackageCallback);
        assertThat(surfacePackageCallback.isRequestSurfacePackageSuccessful()).isTrue();
    }

    @Test
    public void testReloadingSdkAfterKillingSandboxIsSuccessful() throws Exception {
        // Kill the sandbox if it already exists from previous tests
        killSandboxIfExists();

        FakeSdkSandboxProcessDeathCallback callback = new FakeSdkSandboxProcessDeathCallback();
        mSdkSandboxManager.addSdkSandboxProcessDeathCallback(Runnable::run, callback);
        assertThat(callback.waitForSandboxDeath()).isFalse();

        // Killing the sandbox and loading the same SDKs again multiple times should work
        for (int i = 1; i <= 3; ++i) {
            // The same SDKs should be able to be loaded again after sandbox death
            loadMultipleSdks();
            callback.resetLatch();
            killSandbox();
            assertThat(callback.waitForSandboxDeath()).isTrue();
        }
    }

    @Test
    public void testAddSdkSandboxProcessDeathCallback_BeforeStartingSandbox() throws Exception {
        // Kill the sandbox if it already exists from previous tests
        killSandboxIfExists();

        // Add a sandbox lifecycle callback before starting the sandbox
        FakeSdkSandboxProcessDeathCallback lifecycleCallback =
                new FakeSdkSandboxProcessDeathCallback();
        mSdkSandboxManager.addSdkSandboxProcessDeathCallback(Runnable::run, lifecycleCallback);

        // Bring up the sandbox
        loadSdk();

        killSandbox();
        assertThat(lifecycleCallback.waitForSandboxDeath()).isTrue();
    }

    @Test
    public void testAddSdkSandboxProcessDeathCallback_AfterStartingSandbox() throws Exception {
        // Bring up the sandbox
        loadSdk();

        // Add a sandbox lifecycle callback before starting the sandbox
        FakeSdkSandboxProcessDeathCallback lifecycleCallback =
                new FakeSdkSandboxProcessDeathCallback();
        mSdkSandboxManager.addSdkSandboxProcessDeathCallback(Runnable::run, lifecycleCallback);

        killSandbox();
        assertThat(lifecycleCallback.waitForSandboxDeath()).isTrue();
    }

    @Test
    public void testRegisterMultipleSdkSandboxProcessDeathCallbacks() throws Exception {
        // Kill the sandbox if it already exists from previous tests
        killSandboxIfExists();

        // Add a sandbox lifecycle callback before starting the sandbox
        FakeSdkSandboxProcessDeathCallback lifecycleCallback1 =
                new FakeSdkSandboxProcessDeathCallback();
        mSdkSandboxManager.addSdkSandboxProcessDeathCallback(Runnable::run, lifecycleCallback1);

        // Bring up the sandbox
        loadSdk();

        // Add another sandbox lifecycle callback after starting it
        FakeSdkSandboxProcessDeathCallback lifecycleCallback2 =
                new FakeSdkSandboxProcessDeathCallback();
        mSdkSandboxManager.addSdkSandboxProcessDeathCallback(Runnable::run, lifecycleCallback2);

        killSandbox();
        assertThat(lifecycleCallback1.waitForSandboxDeath()).isTrue();
        assertThat(lifecycleCallback2.waitForSandboxDeath()).isTrue();
    }

    @Test
    public void testRemoveSdkSandboxProcessDeathCallback() throws Exception {
        // Bring up the sandbox
        loadSdk();

        // Add and remove a sandbox lifecycle callback
        FakeSdkSandboxProcessDeathCallback lifecycleCallback1 =
                new FakeSdkSandboxProcessDeathCallback();
        mSdkSandboxManager.addSdkSandboxProcessDeathCallback(Runnable::run, lifecycleCallback1);
        mSdkSandboxManager.removeSdkSandboxProcessDeathCallback(lifecycleCallback1);

        // Add a lifecycle callback but don't remove it
        FakeSdkSandboxProcessDeathCallback lifecycleCallback2 =
                new FakeSdkSandboxProcessDeathCallback();
        mSdkSandboxManager.addSdkSandboxProcessDeathCallback(Runnable::run, lifecycleCallback2);

        killSandbox();
        assertThat(lifecycleCallback1.waitForSandboxDeath()).isFalse();
        assertThat(lifecycleCallback2.waitForSandboxDeath()).isTrue();
    }

    @Test
    public void testRequestSurfacePackageSuccessfully() {
        loadSdk();

        final FakeRequestSurfacePackageCallback surfacePackageCallback =
                new FakeRequestSurfacePackageCallback();
        mSdkSandboxManager.requestSurfacePackage(
                SDK_NAME_1,
                getRequestSurfacePackageParams(),
                Runnable::run,
                surfacePackageCallback);
        assertThat(surfacePackageCallback.isRequestSurfacePackageSuccessful()).isTrue();
    }

    @Test
    public void testRequestSurfacePackageWithInternalErrorShouldFail() {
        loadSdk();

        final FakeRequestSurfacePackageCallback surfacePackageCallback =
                new FakeRequestSurfacePackageCallback();
        Bundle params = getRequestSurfacePackageParams();
        params.putString(TEST_OPTION, OPTION_THROW_REQUEST_SURFACE_PACKAGE_ERROR);
        mSdkSandboxManager.requestSurfacePackage(
                SDK_NAME_1, params, Runnable::run, surfacePackageCallback);
        assertThat(surfacePackageCallback.isRequestSurfacePackageSuccessful()).isFalse();
        assertThat(surfacePackageCallback.getSurfacePackageErrorCode())
                .isEqualTo(SdkSandboxManager.REQUEST_SURFACE_PACKAGE_INTERNAL_ERROR);
        assertThat(surfacePackageCallback.getExtraErrorInformation()).isNotNull();
        assertThat(surfacePackageCallback.getExtraErrorInformation().isEmpty()).isTrue();
    }

    @Test
    public void testRequestSurfacePackage_SandboxDiesAfterLoadingSdk() throws Exception {
        loadSdk();

        assertThat(killSandboxIfExists()).isTrue();

        final FakeRequestSurfacePackageCallback surfacePackageCallback =
                new FakeRequestSurfacePackageCallback();
        mSdkSandboxManager.requestSurfacePackage(
                SDK_NAME_1,
                getRequestSurfacePackageParams(),
                Runnable::run,
                surfacePackageCallback);
        assertThat(surfacePackageCallback.isRequestSurfacePackageSuccessful()).isFalse();
        assertThat(surfacePackageCallback.getSurfacePackageErrorCode())
                .isEqualTo(SdkSandboxManager.REQUEST_SURFACE_PACKAGE_SDK_NOT_LOADED);
    }

    @Test
    public void testResourcesAndAssets() throws Exception {
        ICtsSdkProviderApi sdk = loadSdk();
        sdk.checkResourcesAndAssets();
    }

    @Test
    public void testLoadSdkInBackgroundFails() throws Exception {
        mScenario.moveToState(Lifecycle.State.DESTROYED);

        // Wait for the activity to be destroyed
        Thread.sleep(1000);

        final FakeLoadSdkCallback callback = new FakeLoadSdkCallback();
        mSdkSandboxManager.loadSdk(SDK_NAME_1, new Bundle(), Runnable::run, callback);

        LoadSdkException thrown = callback.getLoadSdkException();

        assertEquals(LOAD_SDK_INTERNAL_ERROR, thrown.getLoadSdkErrorCode());
        assertThat(thrown).hasMessageThat().contains("does not run in the foreground");
    }

    @Test
    public void testSandboxApisAreUsableAfterUnbindingSandbox() throws Exception {
        FakeLoadSdkCallback callback1 = new FakeLoadSdkCallback();
        mSdkSandboxManager.loadSdk(SDK_NAME_1, new Bundle(), Runnable::run, callback1);
        callback1.assertLoadSdkIsSuccessful();

        // Move the app to the background and bring it back to the foreground again.
        mScenario.recreate();

        // Loading another sdk should work without issue
        FakeLoadSdkCallback callback2 = new FakeLoadSdkCallback();
        mSdkSandboxManager.loadSdk(SDK_NAME_2, new Bundle(), Runnable::run, callback2);
        callback2.assertLoadSdkIsSuccessful();

        // Requesting surface package from the first loaded sdk should work.
        final FakeRequestSurfacePackageCallback surfacePackageCallback =
                new FakeRequestSurfacePackageCallback();
        mSdkSandboxManager.requestSurfacePackage(
                SDK_NAME_1,
                getRequestSurfacePackageParams(),
                Runnable::run,
                surfacePackageCallback);
        assertThat(surfacePackageCallback.isRequestSurfacePackageSuccessful()).isTrue();
    }

    /** Checks that {@code SdkSandbox.apk} only requests normal permissions in its manifest. */
    // TODO: This should probably be a separate test module
    @Test
    public void testSdkSandboxPermissions() throws Exception {
        final PackageManager pm =
                InstrumentationRegistry.getInstrumentation().getContext().getPackageManager();
        final PackageInfo sdkSandboxPackage =
                pm.getPackageInfo(
                        pm.getSdkSandboxPackageName(),
                        PackageManager.PackageInfoFlags.of(PackageManager.GET_PERMISSIONS));
        for (int i = 0; i < sdkSandboxPackage.requestedPermissions.length; i++) {
            final String permissionName = sdkSandboxPackage.requestedPermissions[i];
            final PermissionInfo permissionInfo = pm.getPermissionInfo(permissionName, 0);
            expect.withMessage("SdkSandbox.apk requests non-normal permission %s", permissionName)
                    .that(permissionInfo.getProtection())
                    .isEqualTo(PermissionInfo.PROTECTION_NORMAL);
        }
    }

    // TODO(b/244730098): The test below needs to be moved from e2e.
    // It is not and e2e test.
    @Test
    public void testLoadSdkExceptionWriteToParcel() {
        final Bundle bundle = new Bundle();
        bundle.putChar("testKey", /*testValue=*/ 'C');
        final String errorMessage = "Error Message";
        final Exception cause = new Exception(errorMessage);

        final LoadSdkException exception = new LoadSdkException(cause, bundle);

        final Parcel parcel = Parcel.obtain();
        exception.writeToParcel(parcel, /*flags=*/ 0);

        // Create LoadSdkException with the same parcel
        parcel.setDataPosition(0); // rewind
        final LoadSdkException exceptionCheck = LoadSdkException.CREATOR.createFromParcel(parcel);

        assertThat(exceptionCheck.getLoadSdkErrorCode()).isEqualTo(exception.getLoadSdkErrorCode());
        assertThat(exceptionCheck.getMessage()).isEqualTo(exception.getMessage());
        assertThat(exceptionCheck.getExtraInformation().getChar("testKey"))
                .isEqualTo(exception.getExtraInformation().getChar("testKey"));
        assertThat(exceptionCheck.getExtraInformation().keySet()).containsExactly("testKey");
    }

    // TODO(b/244730098): The test below needs to be moved from e2e.
    // It is not and e2e test.
    @Test
    public void testLoadSdkExceptionDescribeContents() throws Exception {
        final LoadSdkException exception = new LoadSdkException(new Exception(), new Bundle());
        assertThat(exception.describeContents()).isEqualTo(0);
    }

    // TODO(b/244730098): The test below needs to be moved from e2e.
    // It is not and e2e test.
    @Test
    public void testSandboxedSdkDescribeContents() throws Exception {
        final SandboxedSdk sandboxedSdk = new SandboxedSdk(new Binder());
        assertThat(sandboxedSdk.describeContents()).isEqualTo(0);
    }

    @Test
    public void testSdkAndAppProcessImportanceIsAligned_AppIsBackgrounded() throws Exception {
        // Sandbox and app priority is aligned only in U+.
        assumeTrue(SdkLevel.isAtLeastU());

        ICtsSdkProviderApi sdk = loadSdk();
        assertThat(sdk.getProcessImportance()).isEqualTo(getAppProcessImportance());

        // Move the app to the background.
        mScenario.moveToState(Lifecycle.State.DESTROYED);
        Thread.sleep(1000);

        assertThat(sdk.getProcessImportance()).isEqualTo(getAppProcessImportance());
    }

    @Test
    public void testSdkAndAppProcessImportanceIsAligned_AppIsBackgroundedAndForegrounded()
            throws Exception {
        // Sandbox and app priority is aligned only in U+.
        assumeTrue(SdkLevel.isAtLeastU());

        ICtsSdkProviderApi sdk = loadSdk();
        assertThat(sdk.getProcessImportance()).isEqualTo(getAppProcessImportance());

        // Move the app to the background and bring it back to the foreground again.
        mScenario.recreate();

        // The sandbox should have foreground importance again.
        assertThat(sdk.getProcessImportance()).isEqualTo(getAppProcessImportance());
    }

    @Test
    public void testSDKCanNotStartSandboxActivityDirectlyByAction() {
        assumeTrue(SdkLevel.isAtLeastU());

        final ICtsSdkProviderApi sdk = loadSdk();

        assertThat(mScenario.getState()).isEqualTo(State.RESUMED);
        SecurityException exception =
                assertThrows(
                        SecurityException.class, () -> sdk.startSandboxActivityDirectlyByAction());
        assertThat(exception.getMessage())
                .isEqualTo("Sandbox process is not allowed to start sandbox activities.");
        assertThat(mScenario.getState()).isEqualTo(State.RESUMED);
    }

    @Test
    public void testSDKCanNotStartSandboxActivityDirectlyByComponent() {
        assumeTrue(SdkLevel.isAtLeastU());

        final ICtsSdkProviderApi sdk = loadSdk();

        assertThat(mScenario.getState()).isEqualTo(State.RESUMED);
        SecurityException exception =
                assertThrows(
                        SecurityException.class,
                        () -> sdk.startSandboxActivityDirectlyByComponent());
        assertThat(exception.getMessage())
                .isEqualTo("Sandbox process is not allowed to start sandbox activities.");
        assertThat(mScenario.getState()).isEqualTo(State.RESUMED);
    }

    @Test
    public void testSandboxProcessShouldBeRunningToHostTheSandboxActivity() {
        assumeTrue(SdkLevel.isAtLeastU());

        assertThat(mScenario.getState()).isEqualTo(State.RESUMED);
        mScenario.onActivity(
                clientActivity -> {
                    SecurityException exception =
                            assertThrows(
                                    SecurityException.class,
                                    () ->
                                            mSdkSandboxManager.startSdkSandboxActivity(
                                                    clientActivity, new Binder()));
                    assertThat(exception.getMessage())
                            .contains("There is no sandbox process running");
                });
        assertThat(mScenario.getState()).isEqualTo(State.RESUMED);
    }

    @Test
    public void testStartSdkSandboxActivity() {
        assumeTrue(SdkLevel.isAtLeastU());

        ICtsSdkProviderApi sdk = loadSdk();

        assertThat(mScenario.getState()).isEqualTo(State.RESUMED);
        ActivityStarter activityStarter = new ActivityStarter();
        assertThat(activityStarter.isActivityResumed()).isFalse();

        startSandboxActivity(sdk, activityStarter);

        assertThat(mScenario.getState()).isIn(Arrays.asList(State.CREATED, State.STARTED));
        assertThat(activityStarter.isActivityResumed()).isTrue();
    }

    @Test
    public void testStartSdkSandboxActivityOnTopOfASandboxActivity() {
        assumeTrue(SdkLevel.isAtLeastU());

        ICtsSdkProviderApi sdk = loadSdk();

        ActivityStarter sandboxActivity1Starter = new ActivityStarter();
        ActivityStarter sandboxActivity2Starter = new ActivityStarter();
        assertThat(sandboxActivity1Starter.isActivityResumed()).isFalse();
        assertThat(sandboxActivity2Starter.isActivityResumed()).isFalse();
        assertThat(mScenario.getState()).isEqualTo(State.RESUMED);

        startSandboxActivity(sdk, sandboxActivity1Starter);

        assertThat(mScenario.getState()).isIn(Arrays.asList(State.CREATED, State.STARTED));
        assertThat(sandboxActivity1Starter.isActivityResumed()).isTrue();
        assertThat(sandboxActivity2Starter.isActivityResumed()).isFalse();

        startSandboxActivity(sdk, sandboxActivity2Starter);

        assertThat(mScenario.getState()).isIn(Arrays.asList(State.CREATED, State.STARTED));
        assertThat(sandboxActivity1Starter.isActivityResumed()).isFalse();
        assertThat(sandboxActivity2Starter.isActivityResumed()).isTrue();
    }

    @Test
    public void testStartLocalActivityOnTopOfASandboxActivity() {
        assumeTrue(SdkLevel.isAtLeastU());

        ICtsSdkProviderApi sdk = loadSdk();

        ActivityStarter sandboxActivityStarter = new ActivityStarter();
        ActivityStarter otherClientActivityStarter = new ActivityStarter();
        assertThat(sandboxActivityStarter.isActivityResumed()).isFalse();
        assertThat(otherClientActivityStarter.isActivityResumed()).isFalse();
        assertThat(mScenario.getState()).isEqualTo(State.RESUMED);

        startSandboxActivity(sdk, sandboxActivityStarter);

        assertThat(mScenario.getState()).isIn(Arrays.asList(State.CREATED, State.STARTED));
        assertThat(otherClientActivityStarter.isActivityResumed()).isFalse();
        assertThat(sandboxActivityStarter.isActivityResumed()).isTrue();

        mScenario.onActivity(
                clientActivity -> {
                    otherClientActivityStarter.setFromActivity(clientActivity);
                });
        otherClientActivityStarter.startLocalActivity();

        assertThat(mScenario.getState()).isIn(Arrays.asList(State.CREATED, State.STARTED));
        assertThat(sandboxActivityStarter.isActivityResumed()).isFalse();
        assertThat(otherClientActivityStarter.isActivityResumed()).isTrue();
    }

    @Test
    public void testClientAppCanClearTopWhileOtherActivitiesOnTopIncludingSandboxActivities() {
        assumeTrue(SdkLevel.isAtLeastU());

        ICtsSdkProviderApi sdk = loadSdk();

        // Start 2 sandbox activities.
        ActivityStarter sandboxActivity1Starter = new ActivityStarter();
        ActivityStarter sandboxActivity2Starter = new ActivityStarter();
        startSandboxActivity(sdk, sandboxActivity1Starter);
        startSandboxActivity(sdk, sandboxActivity2Starter);

        assertThat(mScenario.getState()).isIn(Arrays.asList(State.CREATED, State.STARTED));
        assertThat(sandboxActivity1Starter.isActivityResumed()).isFalse();
        assertThat(sandboxActivity2Starter.isActivityResumed()).isTrue();

        // Clear top (include the sandbox activities on top).
        ActivityStarter clearTopActivityStarter = new ActivityStarter();
        mScenario.onActivity(
                clientActivity -> {
                    clearTopActivityStarter.setFromActivity(clientActivity);
                });
        clearTopActivityStarter.startLocalActivity(Intent.FLAG_ACTIVITY_CLEAR_TOP);
        assertThat(sandboxActivity1Starter.isActivityResumed()).isFalse();
        assertThat(sandboxActivity2Starter.isActivityResumed()).isFalse();
        assertThat(clearTopActivityStarter.isActivityResumed()).isTrue();
    }

    /**
     * Test that in case {@link FLAG_SANDBOX_ACTIVITY_SDK_BASED_CONTEXT} is enabled and the {@link
     * CUSTOMIZED_SDK_CONTEXT_ENABLED} flag is enabled, the sandbox activity context is created
     * using the SDK ApplicationInfo.
     *
     * @throws RemoteException
     */
    @Test
    @RequiresFlagsEnabled(FLAG_SANDBOX_ACTIVITY_SDK_BASED_CONTEXT)
    public void testSandboxActivityUseSdkBasedContextIfRequiredFlagAreEnabled()
            throws RemoteException {
        assumeTrue(SdkLevel.isAtLeastU());

        DeviceConfigStateHelper mDeviceConfig =
                new DeviceConfigStateHelper(DeviceConfig.NAMESPACE_ADSERVICES);
        mDeviceConfig.set(CUSTOMIZED_SDK_CONTEXT_ENABLED, "true");

        ICtsSdkProviderApi sdk = loadSdk();

        ActivityStarter sandboxActivityStarter = new ActivityStarter();
        IActivityActionExecutor actionExecutor = startSandboxActivity(sdk, sandboxActivityStarter);
        assertThat(mScenario.getState()).isIn(Arrays.asList(State.CREATED, State.STARTED));
        assertThat(sandboxActivityStarter.isActivityResumed()).isTrue();

        String dataDir = actionExecutor.getDataDir();
        assertThat(dataDir).contains(SDK_NAME_1);
        assertThat(dataDir).doesNotContain(getSdkSandboxPackageName());
    }

    /**
     * Test that in case {@link FLAG_SANDBOX_ACTIVITY_SDK_BASED_CONTEXT} is enabled but the {@link
     * CUSTOMIZED_SDK_CONTEXT_ENABLED} flag is disabled, the sandbox activity context is created
     * using the sandbox App ApplicationInfo.
     *
     * @throws RemoteException
     */
    @Test
    @RequiresFlagsEnabled(FLAG_SANDBOX_ACTIVITY_SDK_BASED_CONTEXT)
    public void testSandboxActivityUseAppBasedContextIfCustomizedSdkFlagIsDisabled()
            throws RemoteException {
        assumeTrue(SdkLevel.isAtLeastU());

        DeviceConfigStateHelper mDeviceConfig =
                new DeviceConfigStateHelper(DeviceConfig.NAMESPACE_ADSERVICES);
        mDeviceConfig.set(CUSTOMIZED_SDK_CONTEXT_ENABLED, "false");

        ICtsSdkProviderApi sdk = loadSdk();

        ActivityStarter sandboxActivityStarter = new ActivityStarter();
        IActivityActionExecutor actionExecutor = startSandboxActivity(sdk, sandboxActivityStarter);
        assertThat(mScenario.getState()).isIn(Arrays.asList(State.CREATED, State.STARTED));
        assertThat(sandboxActivityStarter.isActivityResumed()).isTrue();

        String dataDir = actionExecutor.getDataDir();
        assertThat(dataDir).doesNotContain(SDK_NAME_1);
        assertThat(dataDir).contains(getSdkSandboxPackageName());
    }

    /**
     * Test that in case {@link FLAG_SANDBOX_ACTIVITY_SDK_BASED_CONTEXT} is disabled but the {@link
     * CUSTOMIZED_SDK_CONTEXT_ENABLED} flag is enabled, the sandbox activity context is created
     * using the sandbox App ApplicationInfo.
     *
     * @throws RemoteException
     */
    @Test
    @RequiresFlagsDisabled(FLAG_SANDBOX_ACTIVITY_SDK_BASED_CONTEXT)
    public void testSandboxActivityUseAppBasedContextIfSdkBasedFlagIDisabled()
            throws RemoteException {
        assumeTrue(SdkLevel.isAtLeastU());

        DeviceConfigStateHelper mDeviceConfig =
                new DeviceConfigStateHelper(DeviceConfig.NAMESPACE_ADSERVICES);
        mDeviceConfig.set(CUSTOMIZED_SDK_CONTEXT_ENABLED, "true");

        ICtsSdkProviderApi sdk = loadSdk();

        ActivityStarter sandboxActivityStarter = new ActivityStarter();
        IActivityActionExecutor actionExecutor = startSandboxActivity(sdk, sandboxActivityStarter);
        assertThat(mScenario.getState()).isIn(Arrays.asList(State.CREATED, State.STARTED));
        assertThat(sandboxActivityStarter.isActivityResumed()).isTrue();

        String dataDir = actionExecutor.getDataDir();
        assertThat(dataDir).doesNotContain(SDK_NAME_1);
        assertThat(dataDir).contains(getSdkSandboxPackageName());
    }

    @Test
    @Ignore("b/298171583")
    public void testBackNavigation() throws Exception {
        assumeTrue(SdkLevel.isAtLeastU());

        ICtsSdkProviderApi sdk = loadSdk();

        ActivityStarter sandboxActivityStarter = new ActivityStarter();
        IActivityActionExecutor actionExecutor = startSandboxActivity(sdk, sandboxActivityStarter);

        assertThat(mScenario.getState()).isIn(Arrays.asList(State.CREATED, State.STARTED));
        assertThat(sandboxActivityStarter.isActivityResumed()).isTrue();

        try {
            actionExecutor.disableBackButton();
        } catch (RemoteException e) {
            fail("Error while disabling back button: " + e.getMessage());
        }
        sUiDevice.pressBack();
        assertThat(mScenario.getState()).isIn(Arrays.asList(State.CREATED, State.STARTED));
        assertThat(sandboxActivityStarter.isActivityResumed()).isTrue();

        try {
            actionExecutor.enableBackButton();
        } catch (RemoteException e) {
            fail("Error while enabling back button: " + e.getMessage());
        }
        sUiDevice.pressBack();
        assertThat(mScenario.getState()).isEqualTo(State.RESUMED);
        assertThat(sandboxActivityStarter.isActivityResumed()).isFalse();
    }

    @Test
    @Ignore("b/298171583")
    public void testSandboxActivityShouldRotateIfNotLocked() throws Exception {
        assumeTrue(SdkLevel.isAtLeastU());

        ICtsSdkProviderApi sdk = loadSdk();

        ActivityStarter sandboxActivityStarter = new ActivityStarter();
        startSandboxActivity(sdk, sandboxActivityStarter);
        assertThat(mScenario.getState()).isIn(Arrays.asList(State.CREATED, State.STARTED));
        assertThat(sandboxActivityStarter.isActivityResumed()).isTrue();

        // Assert Natural Rotation.
        assertThat(sUiDevice.getDisplayRotation()).isEqualTo(Surface.ROTATION_0);
        // Rotate device to landscape
        sUiDevice.setOrientationLandscape();
        // Without SDK locking the orientation, display should rotate.
        assertThat(sUiDevice.getDisplayRotation()).isEqualTo(Surface.ROTATION_90);
        assertThat(mScenario.getState()).isIn(Arrays.asList(State.CREATED, State.STARTED));
        assertThat(sandboxActivityStarter.isActivityResumed()).isTrue();
    }

    @Test
    @Ignore("b/298171583")
    public void testSandboxActivityOrientationLocking() throws Exception {
        assumeTrue(SdkLevel.isAtLeastU());

        ICtsSdkProviderApi sdk = loadSdk();

        ActivityStarter sandboxActivityStarter = new ActivityStarter();
        IActivityActionExecutor actionExecutor = startSandboxActivity(sdk, sandboxActivityStarter);
        assertThat(mScenario.getState()).isIn(Arrays.asList(State.CREATED, State.STARTED));
        assertThat(sandboxActivityStarter.isActivityResumed()).isTrue();

        // Assert Natural Rotation.
        assertThat(sUiDevice.getDisplayRotation()).isEqualTo(Surface.ROTATION_0);

        // Locking orientation to landscape should override device current orientation
        // (portrait) and restart the sandbox activity in the landscape orientation.
        actionExecutor.setOrientationToLandscape();

        assertThat(sUiDevice.getDisplayRotation()).isEqualTo(Surface.ROTATION_90);
        // Rotation the device should not affect the locked display orientation.
        sUiDevice.setOrientationPortrait();
        assertThat(sUiDevice.getDisplayRotation()).isEqualTo(Surface.ROTATION_90);
        assertThat(mScenario.getState()).isIn(Arrays.asList(State.CREATED, State.STARTED));
        assertThat(sandboxActivityStarter.isActivityResumed()).isTrue();

        // Locking orientation to portrait should override device current orientation
        // (landscape) and restart the sandbox activity in the portrait orientation.
        actionExecutor.setOrientationToPortrait();

        assertThat(sUiDevice.getDisplayRotation()).isEqualTo(Surface.ROTATION_0);
        // Rotation the device should not affect the locked display orientation.
        sUiDevice.setOrientationLandscape();
        assertThat(sUiDevice.getDisplayRotation()).isEqualTo(Surface.ROTATION_0);
        assertThat(mScenario.getState()).isIn(Arrays.asList(State.CREATED, State.STARTED));
        assertThat(sandboxActivityStarter.isActivityResumed()).isTrue();
    }

    @Test
    public void testStartSdkSandboxedActivityFailIfTheHandlerUnregistered() {
        assumeTrue(SdkLevel.isAtLeastU());

        // Load SDK in sandbox
        ICtsSdkProviderApi sdk = loadSdk();

        ActivityStarter activityStarter = new ActivityStarter();
        assertThat(mScenario.getState()).isEqualTo(State.RESUMED);

        Bundle extras = new Bundle();
        extras.putBoolean(UNREGISTER_BEFORE_STARTING_KEY, true);
        startSandboxActivity(sdk, activityStarter, extras);

        assertThat(mScenario.getState()).isEqualTo(State.RESUMED);
        assertThat(activityStarter.isActivityResumed()).isFalse();
    }

    @Test
    public void testSandboxActivityStartIntentViewWithNoSecurityExceptions() throws Exception {
        assumeTrue(SdkLevel.isAtLeastU());

        ICtsSdkProviderApi sdk = loadSdk();

        ActivityStarter sandboxActivityStarter = new ActivityStarter();
        IActivityActionExecutor actionExecutor = startSandboxActivity(sdk, sandboxActivityStarter);
        assertThat(mScenario.getState()).isIn(Arrays.asList(State.CREATED, State.STARTED));
        assertThat(sandboxActivityStarter.isActivityResumed()).isTrue();

        actionExecutor.openLandingPage();
    }

    // Helper method to load SDK_NAME_1
    private ICtsSdkProviderApi loadSdk() {
        final FakeLoadSdkCallback callback = new FakeLoadSdkCallback();
        mSdkSandboxManager.loadSdk(SDK_NAME_1, new Bundle(), Runnable::run, callback);
        callback.assertLoadSdkIsSuccessful();

        final SandboxedSdk sandboxedSdk = callback.getSandboxedSdk();
        assertNotNull(sandboxedSdk);
        return ICtsSdkProviderApi.Stub.asInterface(callback.getSandboxedSdk().getInterface());
    }

    private int getAppProcessImportance() {
        ActivityManager.RunningAppProcessInfo processInfo =
                new ActivityManager.RunningAppProcessInfo();
        ActivityManager.getMyMemoryState(processInfo);
        return processInfo.importance;
    }

    private IActivityActionExecutor startSandboxActivity(
            ICtsSdkProviderApi sdk, ActivityStarter activityStarter) {
        return startSandboxActivity(sdk, activityStarter, new Bundle());
    }

    private IActivityActionExecutor startSandboxActivity(
            ICtsSdkProviderApi sdk, ActivityStarter activityStarter, Bundle extras) {
        ActivityExecutorContainer activityExecutorContainer = new ActivityExecutorContainer();
        mScenario.onActivity(
                clientActivity -> {
                    activityStarter.setFromActivity(clientActivity);
                    IActivityActionExecutor actionExecutor = null;
                    try {
                        actionExecutor =
                                (IActivityActionExecutor)
                                        sdk.startActivity(activityStarter, extras);
                    } catch (RemoteException e) {
                        fail("Got exception while starting activity: " + e.getMessage());
                    }
                    activityExecutorContainer.setExecutor(actionExecutor);
                });
        IActivityActionExecutor actionExecutor = activityExecutorContainer.getExecutor();
        assertThat(actionExecutor).isNotNull();
        return actionExecutor;
    }

    // Separate class to store IActivityActionExecutor which is returned in a lambda expression.
    private static class ActivityExecutorContainer {
        private IActivityActionExecutor mExecutor;

        public void setExecutor(IActivityActionExecutor executor) {
            mExecutor = executor;
        }

        public IActivityActionExecutor getExecutor() {
            return mExecutor;
        }
    }

    private class ActivityStarter extends IActivityStarter.Stub {
        private Activity mFromActivity;
        private boolean mActivityResumed = false;
        private final CountDownLatch mWaitingForActivityToStartLatch = new CountDownLatch(1);

        ActivityStarter() {}

        // To be called by SDKs to start sandbox activities.
        @Override
        public void startSdkSandboxActivity(IBinder token) throws RemoteException {
            assertThat(mFromActivity).isNotNull();

            mSdkSandboxManager.startSdkSandboxActivity(mFromActivity, token);
            waitForActivityToBeResumed();
        }

        // It is called to notify that onResume() is called against the new started Activity.
        @Override
        public void onActivityResumed() {
            mActivityResumed = true;
            mWaitingForActivityToStartLatch.countDown();
        }

        // It is called to notify the new started Activity is no longer in the Resumed state.
        @Override
        public void onLeftActivityResumed() {
            mActivityResumed = false;
        }

        // To start local test activities (can not be called between processes).
        public void startLocalActivity() {
            assertThat(mFromActivity).isNotNull();
            startLocalActivity(0);
        }

        // To start local test activities (can not be called between processes).
        public void startLocalActivity(int flags) {
            assertThat(mFromActivity).isNotNull();

            Intent intent = new Intent(mFromActivity, TestActivity.class);
            Bundle params = new Bundle();
            params.putBinder(ACTIVITY_STARTER_KEY, this);
            intent.putExtras(params);
            intent.addFlags(flags);
            mFromActivity.startActivity(intent);
            waitForActivityToBeResumed();
        }

        public void setFromActivity(Activity activity) {
            mFromActivity = activity;
        }

        public boolean isActivityResumed() {
            return mActivityResumed;
        }

        private void waitForActivityToBeResumed() {
            try {
                mWaitingForActivityToStartLatch.await(1, TimeUnit.SECONDS);
            } catch (InterruptedException e) {
                fail("Exception while waiting for the sandbox activity: " + e.getMessage());
            }
        }
    }

    private Bundle getRequestSurfacePackageParams() {
        Bundle params = new Bundle();
        params.putInt(EXTRA_WIDTH_IN_PIXELS, 500);
        params.putInt(EXTRA_HEIGHT_IN_PIXELS, 500);
        params.putInt(EXTRA_DISPLAY_ID, 0);
        params.putBinder(EXTRA_HOST_TOKEN, new Binder());

        return params;
    }

    private void loadMultipleSdks() {
        FakeLoadSdkCallback callback = new FakeLoadSdkCallback();
        mSdkSandboxManager.loadSdk(SDK_NAME_1, new Bundle(), Runnable::run, callback);
        callback.assertLoadSdkIsSuccessful();

        FakeLoadSdkCallback callback2 = new FakeLoadSdkCallback();
        mSdkSandboxManager.loadSdk(SDK_NAME_2, new Bundle(), Runnable::run, callback2);
        callback2.assertLoadSdkIsSuccessful();
    }

    // Returns true if the sandbox was already likely existing, false otherwise.
    private boolean killSandboxIfExists() throws Exception {
        FakeSdkSandboxProcessDeathCallback callback = new FakeSdkSandboxProcessDeathCallback();
        mSdkSandboxManager.addSdkSandboxProcessDeathCallback(Runnable::run, callback);
        killSandbox();

        return callback.waitForSandboxDeath();
    }

    private String getSdkSandboxPackageName() {
        return InstrumentationRegistry.getInstrumentation()
                .getContext()
                .getPackageManager()
                .getSdkSandboxPackageName();
    }

    private void killSandbox() throws Exception {
        // TODO(b/241542162): Avoid using reflection as a workaround once test apis can be run
        //  without issue.
        mSdkSandboxManager.getClass().getMethod("stopSdkSandbox").invoke(mSdkSandboxManager);
    }
}

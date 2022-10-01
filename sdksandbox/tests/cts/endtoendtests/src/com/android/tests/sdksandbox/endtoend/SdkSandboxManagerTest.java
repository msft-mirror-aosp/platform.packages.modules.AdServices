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

import static com.google.common.truth.Truth.assertThat;

import static org.junit.Assert.assertNotNull;
import static org.junit.Assert.assertThrows;

import android.app.sdksandbox.LoadSdkException;
import android.app.sdksandbox.SandboxedSdk;
import android.app.sdksandbox.SdkSandboxManager;
import android.app.sdksandbox.testutils.FakeLoadSdkCallback;
import android.app.sdksandbox.testutils.FakeRequestSurfacePackageCallback;
import android.app.sdksandbox.testutils.FakeSdkSandboxProcessDeathCallback;
import android.content.Context;
import android.content.pm.PackageInfo;
import android.content.pm.PackageManager;
import android.content.pm.PermissionInfo;
import android.os.Binder;
import android.os.Bundle;
import android.os.Parcel;

import androidx.lifecycle.Lifecycle;
import androidx.test.core.app.ActivityScenario;
import androidx.test.ext.junit.rules.ActivityScenarioRule;
import androidx.test.platform.app.InstrumentationRegistry;

import com.google.common.truth.Expect;

import org.junit.Before;
import org.junit.Rule;
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

    private static final String NON_EXISTENT_SDK = "com.android.not_exist";

    @Rule
    public final ActivityScenarioRule<TestActivity> mRule =
            new ActivityScenarioRule<>(TestActivity.class);

    @Rule public final Expect mExpect = Expect.create();

    private ActivityScenario<TestActivity> mScenario;

    private SdkSandboxManager mSdkSandboxManager;

    @Before
    public void setup() {
        final Context context = InstrumentationRegistry.getInstrumentation().getContext();
        mSdkSandboxManager = context.getSystemService(SdkSandboxManager.class);
        mScenario = mRule.getScenario();
    }

    @Test
    public void testGetSdkSandboxState() throws Exception {
        int state = mSdkSandboxManager.getSdkSandboxState();
        assertThat(state).isEqualTo(SdkSandboxManager.SDK_SANDBOX_STATE_ENABLED_PROCESS_ISOLATION);
    }

    @Test
    public void loadSdkSuccessfully() {
        final String sdkName = "com.android.loadSdkSuccessfullySdkProvider";
        final FakeLoadSdkCallback callback = new FakeLoadSdkCallback();
        mSdkSandboxManager.loadSdk(sdkName, new Bundle(), Runnable::run, callback);
        assertThat(callback.isLoadSdkSuccessful()).isTrue();
        assertNotNull(callback.getSandboxedSdk());
        assertNotNull(callback.getSandboxedSdk().getInterface());
        mSdkSandboxManager.unloadSdk(sdkName);
    }

    @Test
    public void getSandboxedSdkSuccessfully() {
        final String sdkName = "com.android.loadSdkSuccessfullySdkProvider";
        final FakeLoadSdkCallback callback = new FakeLoadSdkCallback();
        mSdkSandboxManager.loadSdk(sdkName, new Bundle(), Runnable::run, callback);
        assertThat(callback.isLoadSdkSuccessful(/*ignoreSdkAlreadyLoadedError=*/ true)).isTrue();

        List<SandboxedSdk> sandboxedSdks = mSdkSandboxManager.getSandboxedSdks();

        int nLoadedSdks = sandboxedSdks.size();
        assertThat(nLoadedSdks).isGreaterThan(0);
        assertThat(
                        sandboxedSdks.stream()
                                .filter(s -> s.getSharedLibraryInfo().getName().equals(sdkName))
                                .count())
                .isEqualTo(1);

        mSdkSandboxManager.unloadSdk(sdkName);
        List<SandboxedSdk> sandboxedSdksAfterUnload = mSdkSandboxManager.getSandboxedSdks();
        assertThat(sandboxedSdksAfterUnload.size()).isEqualTo(nLoadedSdks - 1);
    }

    @Test
    public void loadSdkAndCheckClassloader() {
        final String sdkName = "com.android.loadSdkAndCheckClassloader";
        final FakeLoadSdkCallback callback = new FakeLoadSdkCallback();
        mSdkSandboxManager.loadSdk(sdkName, new Bundle(), Runnable::run, callback);
        assertThat(callback.isLoadSdkSuccessful()).isTrue();
        assertNotNull(callback.getSandboxedSdk());
        assertNotNull(callback.getSandboxedSdk().getInterface());
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
        final FakeLoadSdkCallback callback = new FakeLoadSdkCallback();

        mSdkSandboxManager.loadSdk(NON_EXISTENT_SDK, new Bundle(), Runnable::run, callback);
        assertThat(callback.isLoadSdkSuccessful()).isFalse();
        assertThat(callback.getLoadSdkErrorCode())
                .isEqualTo(SdkSandboxManager.LOAD_SDK_NOT_FOUND);
    }

    @Test
    public void loadNotExistSdkShouldFail_checkLoadSdkException() {
        final FakeLoadSdkCallback callback = new FakeLoadSdkCallback();

        mSdkSandboxManager.loadSdk(NON_EXISTENT_SDK, new Bundle(), Runnable::run, callback);
        LoadSdkException loadSdkException = callback.getLoadSdkException();
        assertThat(loadSdkException.getExtraInformation()).isNotNull();
        assertThat(loadSdkException.getExtraInformation().isEmpty()).isTrue();
    }

    @Test
    public void loadSdkWithInternalErrorShouldFail() throws Exception {
        final String sdkName = "com.android.loadSdkWithInternalErrorSdkProvider";
        final FakeLoadSdkCallback callback = new FakeLoadSdkCallback();
        mSdkSandboxManager.loadSdk(sdkName, new Bundle(), Runnable::run, callback);
        assertThat(callback.isLoadSdkSuccessful()).isFalse();
        assertThat(callback.getLoadSdkErrorCode())
                .isEqualTo(SdkSandboxManager.LOAD_SDK_SDK_DEFINED_ERROR);
    }

    @Test
    public void unloadAndReloadSdk() {
        final String sdkName = "com.android.loadSdkSuccessfullySdkProvider";
        final FakeLoadSdkCallback callback = new FakeLoadSdkCallback();
        mSdkSandboxManager.loadSdk(sdkName, new Bundle(), Runnable::run, callback);
        assertThat(callback.isLoadSdkSuccessful()).isTrue();

        mSdkSandboxManager.unloadSdk(sdkName);

        // Calls to an unloaded SDK should fail.
        // TODO(249482251): Only check one error case.
        final FakeRequestSurfacePackageCallback requestSurfacePackageCallback =
                new FakeRequestSurfacePackageCallback();
        boolean threwException = false;
        try {
            mSdkSandboxManager.requestSurfacePackage(
                    sdkName,
                    getRequestSurfacePackageParams(),
                    Runnable::run,
                    requestSurfacePackageCallback);
        } catch (IllegalArgumentException e) {
            threwException = true;
        }

        if (!threwException) {
            assertThat(requestSurfacePackageCallback.isRequestSurfacePackageSuccessful()).isFalse();
            assertThat(requestSurfacePackageCallback.getSurfacePackageErrorCode())
                    .isEqualTo(SdkSandboxManager.SDK_SANDBOX_PROCESS_NOT_AVAILABLE);
        }

        // SDK can be reloaded after being unloaded.
        final FakeLoadSdkCallback callback2 = new FakeLoadSdkCallback();
        mSdkSandboxManager.loadSdk(sdkName, new Bundle(), Runnable::run, callback2);
        assertThat(callback2.isLoadSdkSuccessful()).isTrue();
    }

    @Test
    public void unloadingNonexistentSdkThrowsException() {
        final String sdkName1 = "com.android.loadSdkSuccessfullySdkProvider";
        final FakeLoadSdkCallback callback = new FakeLoadSdkCallback();
        mSdkSandboxManager.loadSdk(sdkName1, new Bundle(), Runnable::run, callback);
        // If the SDK provider has already been loaded from another test, ignore the error.
        assertThat(callback.isLoadSdkSuccessful(/*ignoreSdkAlreadyLoadedError=*/ true)).isTrue();

        final String sdkName2 = "com.android.nonexistent";
        assertThrows(IllegalArgumentException.class, () -> mSdkSandboxManager.unloadSdk(sdkName2));
    }

    @Test
    public void reloadingSdkDoesNotInvalidateIt() {
        final String sdkName = "com.android.requestSurfacePackageSuccessfullySdkProvider";

        final FakeLoadSdkCallback callback = new FakeLoadSdkCallback();
        mSdkSandboxManager.loadSdk(sdkName, new Bundle(), Runnable::run, callback);
        // If the SDK provider has already been loaded from another test, ignore the error.
        assertThat(callback.isLoadSdkSuccessful(/*ignoreSdkAlreadyLoadedError=*/ true)).isTrue();
        SandboxedSdk sandboxedSdk = callback.getSandboxedSdk();
        assertNotNull(sandboxedSdk.getInterface());

        // Attempt to load the SDK again and see that it fails.
        final FakeLoadSdkCallback reloadCallback = new FakeLoadSdkCallback();
        mSdkSandboxManager.loadSdk(sdkName, new Bundle(), Runnable::run, reloadCallback);
        assertThat(reloadCallback.isLoadSdkSuccessful()).isFalse();

        // SDK's interface should still be obtainable.
        assertNotNull(sandboxedSdk.getInterface());

        // Further calls to the SDK should still be valid.
        final FakeRequestSurfacePackageCallback surfacePackageCallback =
                new FakeRequestSurfacePackageCallback();
        mSdkSandboxManager.requestSurfacePackage(
                sdkName, getRequestSurfacePackageParams(), Runnable::run, surfacePackageCallback);
        assertThat(surfacePackageCallback.isRequestSurfacePackageSuccessful()).isTrue();
    }

    @Test
    public void testReloadingSdkAfterKillingSandboxIsSuccessful() throws Exception {
        // Kill the sandbox if it already exists from previous tests
        killSandboxIfExists();

        // Killing the sandbox and loading the same SDKs again multiple times should work
        for (int i = 0; i < 3; ++i) {
            FakeSdkSandboxProcessDeathCallback callback = new FakeSdkSandboxProcessDeathCallback();
            mSdkSandboxManager.addSdkSandboxProcessDeathCallback(Runnable::run, callback);

            // The same SDKs should be able to be loaded again after sandbox death
            loadMultipleSdks();

            killSandbox();
            assertThat(callback.isSdkSandboxDeathDetected()).isTrue();
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
        final String sdkName = "com.android.loadSdkSuccessfullySdkProvider";
        final FakeLoadSdkCallback callback = new FakeLoadSdkCallback();
        mSdkSandboxManager.loadSdk(sdkName, new Bundle(), Runnable::run, callback);
        assertThat(callback.isLoadSdkSuccessful()).isTrue();

        killSandbox();
        assertThat(lifecycleCallback.isSdkSandboxDeathDetected()).isTrue();
    }

    @Test
    public void testAddSdkSandboxProcessDeathCallback_AfterStartingSandbox() throws Exception {
        // Bring up the sandbox
        final String sdkName = "com.android.loadSdkSuccessfullySdkProvider";
        final FakeLoadSdkCallback callback = new FakeLoadSdkCallback();
        mSdkSandboxManager.loadSdk(sdkName, new Bundle(), Runnable::run, callback);
        assertThat(callback.isLoadSdkSuccessful(/*ignoreSdkAlreadyLoadedError=*/ true)).isTrue();

        // Add a sandbox lifecycle callback before starting the sandbox
        FakeSdkSandboxProcessDeathCallback lifecycleCallback =
                new FakeSdkSandboxProcessDeathCallback();
        mSdkSandboxManager.addSdkSandboxProcessDeathCallback(Runnable::run, lifecycleCallback);

        killSandbox();
        assertThat(lifecycleCallback.isSdkSandboxDeathDetected()).isTrue();
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
        final String sdkName = "com.android.loadSdkSuccessfullySdkProvider";
        final FakeLoadSdkCallback callback = new FakeLoadSdkCallback();
        mSdkSandboxManager.loadSdk(sdkName, new Bundle(), Runnable::run, callback);
        assertThat(callback.isLoadSdkSuccessful()).isTrue();

        // Add another sandbox lifecycle callback after starting it
        FakeSdkSandboxProcessDeathCallback lifecycleCallback2 =
                new FakeSdkSandboxProcessDeathCallback();
        mSdkSandboxManager.addSdkSandboxProcessDeathCallback(Runnable::run, lifecycleCallback2);

        killSandbox();
        assertThat(lifecycleCallback1.isSdkSandboxDeathDetected()).isTrue();
        assertThat(lifecycleCallback2.isSdkSandboxDeathDetected()).isTrue();
    }

    @Test
    public void testRemoveSdkSandboxProcessDeathCallback() throws Exception {
        // Bring up the sandbox
        final String sdkName = "com.android.loadSdkSuccessfullySdkProvider";
        final FakeLoadSdkCallback callback = new FakeLoadSdkCallback();
        mSdkSandboxManager.loadSdk(sdkName, new Bundle(), Runnable::run, callback);
        assertThat(callback.isLoadSdkSuccessful(/*ignoreSdkAlreadyLoadedError=*/ true)).isTrue();

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
        assertThat(lifecycleCallback1.isSdkSandboxDeathDetected()).isFalse();
        assertThat(lifecycleCallback2.isSdkSandboxDeathDetected()).isTrue();
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
                sdkName, getRequestSurfacePackageParams(), Runnable::run, surfacePackageCallback);
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
                sdkName, getRequestSurfacePackageParams(), Runnable::run, surfacePackageCallback);
        assertThat(surfacePackageCallback.isRequestSurfacePackageSuccessful()).isFalse();
        assertThat(surfacePackageCallback.getSurfacePackageErrorCode())
                .isEqualTo(SdkSandboxManager.REQUEST_SURFACE_PACKAGE_INTERNAL_ERROR);
        assertThat(surfacePackageCallback.getExtraErrorInformation()).isNotNull();
        assertThat(surfacePackageCallback.getExtraErrorInformation().isEmpty()).isTrue();
    }

    @Test
    public void testRequestSurfacePackage_SandboxDiesAfterLoadingSdk() throws Exception {
        final String sdkName = "com.android.requestSurfacePackageSuccessfullySdkProvider";
        final FakeLoadSdkCallback callback = new FakeLoadSdkCallback();
        mSdkSandboxManager.loadSdk(sdkName, new Bundle(), Runnable::run, callback);
        assertThat(callback.isLoadSdkSuccessful(/*ignoreSdkAlreadyLoadedError=*/ true)).isTrue();

        assertThat(killSandboxIfExists()).isTrue();

        final FakeRequestSurfacePackageCallback surfacePackageCallback =
                new FakeRequestSurfacePackageCallback();
        mSdkSandboxManager.requestSurfacePackage(
                sdkName, getRequestSurfacePackageParams(), Runnable::run, surfacePackageCallback);
        assertThat(surfacePackageCallback.isRequestSurfacePackageSuccessful()).isFalse();
        assertThat(surfacePackageCallback.getSurfacePackageErrorCode())
                .isEqualTo(SdkSandboxManager.SDK_SANDBOX_PROCESS_NOT_AVAILABLE);
    }

    @Test
    public void testResourcesAndAssets() {
        final String sdkName = "com.android.codeproviderresources";
        FakeLoadSdkCallback callback = new FakeLoadSdkCallback();
        mSdkSandboxManager.loadSdk(sdkName, new Bundle(), Runnable::run, callback);
        assertThat(callback.isLoadSdkSuccessful()).isTrue();
    }

    @Test
    public void testLoadSdkInBackgroundFails() throws Exception {
        mScenario.moveToState(Lifecycle.State.DESTROYED);

        // Wait for the activity to be destroyed
        Thread.sleep(1000);

        final String sdkName = "com.android.loadSdkSuccessfullySdkProvider";
        final FakeLoadSdkCallback callback = new FakeLoadSdkCallback();
        SecurityException thrown =
                assertThrows(
                        SecurityException.class,
                        () ->
                                mSdkSandboxManager.loadSdk(
                                        sdkName, new Bundle(), Runnable::run, callback));
        assertThat(thrown).hasMessageThat().contains("does not run in the foreground");
    }

    @Test
    public void testSandboxApisAreUsableAfterUnbindingSandbox() throws Exception {
        final String sdkName1 = "com.android.requestSurfacePackageSuccessfullySdkProvider";
        FakeLoadSdkCallback callback1 = new FakeLoadSdkCallback();
        mSdkSandboxManager.loadSdk(sdkName1, new Bundle(), Runnable::run, callback1);
        assertThat(callback1.isLoadSdkSuccessful(/*ignoreSdkAlreadyLoadedError=*/ true)).isTrue();

        // Move the app to the background and bring it back to the foreground again.
        mScenario.recreate();

        // Loading another sdk should work without issue
        final String sdkName2 = "com.android.loadSdkSuccessfullySdkProvider";
        FakeLoadSdkCallback callback2 = new FakeLoadSdkCallback();
        mSdkSandboxManager.loadSdk(sdkName2, new Bundle(), Runnable::run, callback2);
        assertThat(callback2.isLoadSdkSuccessful()).isTrue();

        // Requesting surface package from the first loaded sdk should work.
        final FakeRequestSurfacePackageCallback surfacePackageCallback =
                new FakeRequestSurfacePackageCallback();
        mSdkSandboxManager.requestSurfacePackage(
                sdkName1, getRequestSurfacePackageParams(), Runnable::run, surfacePackageCallback);
        assertThat(surfacePackageCallback.isRequestSurfacePackageSuccessful()).isTrue();

        mSdkSandboxManager.unloadSdk(sdkName1);
        mSdkSandboxManager.unloadSdk(sdkName2);
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
            mExpect.withMessage("SdkSandbox.apk requests non-normal permission " + permissionName)
                    .that(permissionInfo.getProtection())
                    .isEqualTo(PermissionInfo.PROTECTION_NORMAL);
        }
    }

    // TODO(b/244730098): The test below needs to be moved from e2e.
    // It is not and e2e test.
    @Test
    public void testLoadSdkExceptionWriteToParcel() throws Exception {
        final Bundle bundle = new Bundle();
        bundle.putChar("testKey", /*testValue=*/ 'C');

        final LoadSdkException exception = new LoadSdkException(/*Throwable=*/ null, bundle);

        final Parcel parcel = Parcel.obtain();
        exception.writeToParcel(parcel, /*flags=*/ 0);

        // Create LoadSdkException with the same parcel
        parcel.setDataPosition(0); // rewind
        final LoadSdkException exceptionCheck = LoadSdkException.CREATOR.createFromParcel(parcel);

        assertThat(exceptionCheck.getLoadSdkErrorCode()).isEqualTo(exception.getLoadSdkErrorCode());
        assertThat(exceptionCheck.getExtraInformation().getChar("testKey"))
                .isEqualTo(exception.getExtraInformation().getChar("testKey"));
        assertThat(exceptionCheck.getExtraInformation().keySet()).containsExactly("testKey");
    }

    // TODO(b/244730098): The test below needs to be moved from e2e.
    // It is not and e2e test.
    @Test
    public void testLoadSdkExceptionDescribeContents() throws Exception {
        final LoadSdkException exception = new LoadSdkException(/*Throwable=*/ null, new Bundle());
        assertThat(exception.describeContents()).isEqualTo(0);
    }

    // TODO(b/244730098): The test below needs to be moved from e2e.
    // It is not and e2e test.
    @Test
    public void testSandboxedSdkDescribeContents() throws Exception {
        final SandboxedSdk sandboxedSdk = new SandboxedSdk(new Binder());
        assertThat(sandboxedSdk.describeContents()).isEqualTo(0);
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
        FakeSdkSandboxProcessDeathCallback callback = new FakeSdkSandboxProcessDeathCallback();
        mSdkSandboxManager.addSdkSandboxProcessDeathCallback(Runnable::run, callback);
        killSandbox();

        return callback.isSdkSandboxDeathDetected();
    }

    private void killSandbox() throws Exception {
        // TODO(b/241542162): Avoid using reflection as a workaround once test apis can be run
        //  without issue.
        mSdkSandboxManager.getClass().getMethod("stopSdkSandbox").invoke(mSdkSandboxManager);
    }
}

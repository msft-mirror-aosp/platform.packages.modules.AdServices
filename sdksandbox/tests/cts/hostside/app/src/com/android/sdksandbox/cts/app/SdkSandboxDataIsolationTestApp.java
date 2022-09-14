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

package com.android.sdksandbox.cts.app;

import static android.app.sdksandbox.SdkSandboxManager.EXTRA_DISPLAY_ID;
import static android.app.sdksandbox.SdkSandboxManager.EXTRA_HEIGHT_IN_PIXELS;
import static android.app.sdksandbox.SdkSandboxManager.EXTRA_HOST_TOKEN;
import static android.app.sdksandbox.SdkSandboxManager.EXTRA_WIDTH_IN_PIXELS;

import static com.google.common.truth.Truth.assertThat;

import static org.junit.Assert.assertThrows;

import android.app.sdksandbox.SdkSandboxManager;
import android.app.sdksandbox.testutils.FakeLoadSdkCallback;
import android.app.sdksandbox.testutils.FakeRequestSurfacePackageCallback;
import android.os.Binder;
import android.os.Bundle;

import androidx.test.core.app.ApplicationProvider;
import androidx.test.ext.junit.rules.ActivityScenarioRule;
import androidx.test.platform.app.InstrumentationRegistry;

import org.junit.Before;
import org.junit.Rule;
import org.junit.Test;
import org.junit.runner.RunWith;
import org.junit.runners.JUnit4;

import java.io.File;
import java.io.FileInputStream;
import java.io.FileNotFoundException;

@RunWith(JUnit4.class)
public class SdkSandboxDataIsolationTestApp {

    private static final String APP_PKG = "com.android.sdksandbox.cts.app";
    private static final String APP_2_PKG = "com.android.sdksandbox.cts.app2";

    private static final String SDK_NAME = "com.android.sdksandbox.cts.provider";

    private static final String BUNDLE_KEY_PHASE_NAME = "phase-name";

    private static final String JAVA_FILE_PERMISSION_DENIED_MSG =
            "open failed: EACCES (Permission denied)";
    private static final String JAVA_FILE_NOT_FOUND_MSG =
            "open failed: ENOENT (No such file or directory)";

    private SdkSandboxManager mSdkSandboxManager;

    @Rule public final ActivityScenarioRule mRule = new ActivityScenarioRule<>(TestActivity.class);

    @Before
    public void setup() {
        mRule.getScenario();
        mSdkSandboxManager =
                ApplicationProvider.getApplicationContext()
                        .getSystemService(SdkSandboxManager.class);
        assertThat(mSdkSandboxManager).isNotNull();
    }

    // Run a phase of the test inside the SDK loaded for this app
    private void runPhaseInsideSdk(String phaseName) {
        runPhaseInsideSdk(phaseName, new Bundle());
    }

    // Run a phase of the test inside the SDK loaded for this app
    // TODO(b/242678799): We want to use interface provided by loadSdk to perform the communication
    // i.e. use the correct approach
    private void runPhaseInsideSdk(String phaseName, Bundle params) {
        params.putString(BUNDLE_KEY_PHASE_NAME, phaseName);
        params.putInt(EXTRA_WIDTH_IN_PIXELS, 500);
        params.putInt(EXTRA_HEIGHT_IN_PIXELS, 500);
        params.putInt(EXTRA_DISPLAY_ID, 0);
        params.putBinder(EXTRA_HOST_TOKEN, new Binder());
        final FakeRequestSurfacePackageCallback callback = new FakeRequestSurfacePackageCallback();

        mSdkSandboxManager.requestSurfacePackage(SDK_NAME, params, Runnable::run, callback);
        assertThat(callback.isRequestSurfacePackageSuccessful()).isTrue();
    }

    @Test
    public void testAppCannotAccessAnySandboxDirectories() throws Exception {
        assertFileAccessIsDenied("/data/misc_ce/0/sdksandbox/" + APP_PKG);
        assertFileAccessIsDenied("/data/misc_ce/0/sdksandbox/" + APP_2_PKG);
        assertFileAccessIsDenied("/data/misc_ce/0/sdksandbox/does.not.exist");

        assertFileAccessIsDenied("/data/misc_de/0/sdksandbox/" + APP_PKG);
        assertFileAccessIsDenied("/data/misc_de/0/sdksandbox/" + APP_2_PKG);
        assertFileAccessIsDenied("/data/misc_de/0/sdksandbox/does.not.exist");
    }

    @Test
    public void testSdkSandboxDataIsolation_SandboxCanAccessItsDirectory() throws Exception {
        loadSdk();
        runPhaseInsideSdk("testSdkSandboxDataIsolation_SandboxCanAccessItsDirectory");
    }

    @Test
    public void testSdkSandboxDataIsolation_CannotVerifyAppExistence() throws Exception {
        loadSdk();
        runPhaseInsideSdk("testSdkSandboxDataIsolation_CannotVerifyAppExistence");
    }

    @Test
    public void testSdkSandboxDataIsolation_CannotVerifyOtherUserAppExistence() throws Exception {
        loadSdk();
        final Bundle arguments = InstrumentationRegistry.getArguments();
        runPhaseInsideSdk(
                "testSdkSandboxDataIsolation_CannotVerifyOtherUserAppExistence", arguments);
    }

    @Test
    public void testSdkSandboxDataIsolation_CannotVerifyAcrossVolumes() throws Exception {
        loadSdk();
        final Bundle arguments = InstrumentationRegistry.getArguments();
        runPhaseInsideSdk("testSdkSandboxDataIsolation_CannotVerifyAcrossVolumes", arguments);
    }

    private void loadSdk() {
        FakeLoadSdkCallback callback = new FakeLoadSdkCallback();
        mSdkSandboxManager.loadSdk(SDK_NAME, new Bundle(), Runnable::run, callback);
        assertThat(callback.isLoadSdkSuccessful()).isTrue();
    }

    private static void assertFileAccessIsDenied(String path) {
        File file = new File(path);

        // Trying to access a file that does not exist in that directory, it should return
        // permission denied file not found.
        Exception exception =
                assertThrows(
                        FileNotFoundException.class,
                        () -> {
                            new FileInputStream(file);
                        });
        assertThat(exception.getMessage()).contains(JAVA_FILE_PERMISSION_DENIED_MSG);
        assertThat(exception.getMessage()).doesNotContain(JAVA_FILE_NOT_FOUND_MSG);

        assertThat(file.canExecute()).isFalse();
    }
}

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

package com.android.tests.sdksandbox;

import static android.os.storage.StorageManager.UUID_DEFAULT;

import static com.google.common.truth.Truth.assertThat;

import static org.junit.Assert.assertThrows;

import android.app.sdksandbox.SdkSandboxManager;
import android.app.sdksandbox.testutils.FakeLoadSdkCallback;
import android.app.sdksandbox.testutils.FakeRequestSurfacePackageCallback;
import android.app.usage.StorageStats;
import android.app.usage.StorageStatsManager;
import android.content.Context;
import android.os.Binder;
import android.os.Bundle;
import android.os.Process;
import android.os.UserHandle;

import androidx.test.core.app.ApplicationProvider;
import androidx.test.ext.junit.rules.ActivityScenarioRule;
import androidx.test.platform.app.InstrumentationRegistry;

import junit.framework.AssertionFailedError;

import org.junit.Before;
import org.junit.Rule;
import org.junit.Test;
import org.junit.runner.RunWith;
import org.junit.runners.JUnit4;

import java.io.File;
import java.io.FileInputStream;
import java.io.FileNotFoundException;

@RunWith(JUnit4.class)
public class SdkSandboxStorageTestApp {

    private static final String SDK_NAME = "com.android.tests.codeprovider.storagetest";

    private static final String BUNDLE_KEY_PHASE_NAME = "phase-name";

    private static final String JAVA_FILE_PERMISSION_DENIED_MSG =
            "open failed: EACCES (Permission denied)";
    private static final String JAVA_FILE_NOT_FOUND_MSG =
            "open failed: ENOENT (No such file or directory)";

    @Rule public final ActivityScenarioRule mRule = new ActivityScenarioRule<>(TestActivity.class);

    private Context mContext;
    private SdkSandboxManager mSdkSandboxManager;

    @Before
    public void setup() {
        mContext = ApplicationProvider.getApplicationContext();
        mSdkSandboxManager = mContext.getSystemService(SdkSandboxManager.class);
        assertThat(mSdkSandboxManager).isNotNull();
        mRule.getScenario();
    }

    // Run a phase of the test inside the code loaded for this app
    // TODO(b/242678799): We want to use interface provided by loadSdk to perform the communication
    // i.e. use the correct approach
    private void runPhaseInsideCode(String phaseName) {
        FakeRequestSurfacePackageCallback callback = new FakeRequestSurfacePackageCallback();
        Bundle params = new Bundle();
        params.putInt(mSdkSandboxManager.EXTRA_WIDTH_IN_PIXELS, 500);
        params.putInt(mSdkSandboxManager.EXTRA_HEIGHT_IN_PIXELS, 500);
        params.putInt(mSdkSandboxManager.EXTRA_DISPLAY_ID, 0);
        params.putBinder(mSdkSandboxManager.EXTRA_HOST_TOKEN, new Binder());
        params.putString(BUNDLE_KEY_PHASE_NAME, phaseName);
        mSdkSandboxManager.requestSurfacePackage(SDK_NAME, params, Runnable::run, callback);
        // Wait for SDK to finish handling the request
        assertThat(callback.isRequestSurfacePackageSuccessful()).isFalse();
    }

    @Test
    public void loadSdk() throws Exception {
        FakeLoadSdkCallback callback = new FakeLoadSdkCallback();
        mSdkSandboxManager.loadSdk(SDK_NAME, new Bundle(), Runnable::run, callback);
        assertThat(callback.isLoadSdkSuccessful()).isTrue();
    }

    @Test
    public void testSdkSandboxDataRootDirectory_IsNotAccessibleByApps() throws Exception {
        assertDirIsNotAccessible("/data/misc_ce/0/sdksandbox");
        assertDirIsNotAccessible("/data/misc_de/0/sdksandbox");
    }

    @Test
    public void testSdkDataPackageDirectory_SharedStorageIsUsable() throws Exception {
        loadSdk();

        // Run phase inside the SDK
        runPhaseInsideCode("testSdkDataPackageDirectory_SharedStorageIsUsable");
    }

    @Test
    public void testSdkDataSubDirectory_PerSdkStorageIsUsable() throws Exception {
        loadSdk();

        runPhaseInsideCode("testSdkDataSubDirectory_PerSdkStorageIsUsable");
    }

    @Test
    public void testSdkDataIsAttributedToApp() throws Exception {
        loadSdk();

        final StorageStatsManager stats = InstrumentationRegistry.getInstrumentation().getContext()
                                                .getSystemService(StorageStatsManager.class);
        int uid = Process.myUid();
        UserHandle user = Process.myUserHandle();

        // Have the sdk use up space
        final StorageStats initialAppStats = stats.queryStatsForUid(UUID_DEFAULT, uid);
        final StorageStats initialUserStats = stats.queryStatsForUser(UUID_DEFAULT, user);

        runPhaseInsideCode("testSdkDataIsAttributedToApp");

        final StorageStats finalAppStats = stats.queryStatsForUid(UUID_DEFAULT, uid);
        final StorageStats finalUserStats = stats.queryStatsForUser(UUID_DEFAULT, user);

        // Verify the space used with a few hundred kilobytes error margin
        long deltaAppSize = 2000000;
        long deltaCacheSize = 1000000;
        long errorMarginSize = 100000;
        assertMostlyEquals(deltaAppSize,
                    finalAppStats.getDataBytes() - initialAppStats.getDataBytes(),
                           errorMarginSize);
        assertMostlyEquals(deltaAppSize,
                    finalUserStats.getDataBytes() - initialUserStats.getDataBytes(),
                           errorMarginSize);
        assertMostlyEquals(deltaCacheSize,
                    finalAppStats.getCacheBytes() - initialAppStats.getCacheBytes(),
                           errorMarginSize);
        assertMostlyEquals(deltaCacheSize,
                    finalUserStats.getCacheBytes() - initialUserStats.getCacheBytes(),
                           errorMarginSize);
    }

    private static void assertDirIsNotAccessible(String path) {
        // Trying to access a file that does not exist in that directory, it should return
        // permission denied not file not found.
        Exception exception = assertThrows(FileNotFoundException.class, () -> {
            new FileInputStream(new File(path, "FILE_DOES_NOT_EXIST"));
        });
        assertThat(exception.getMessage()).contains(JAVA_FILE_PERMISSION_DENIED_MSG);
        assertThat(exception.getMessage()).doesNotContain(JAVA_FILE_NOT_FOUND_MSG);

        assertThat(new File(path).canExecute()).isFalse();
    }

    private static void assertMostlyEquals(long expected, long actual, long delta) {
        if (Math.abs(expected - actual) > delta) {
            throw new AssertionFailedError("Expected roughly " + expected + " but was " + actual);
        }
    }
}

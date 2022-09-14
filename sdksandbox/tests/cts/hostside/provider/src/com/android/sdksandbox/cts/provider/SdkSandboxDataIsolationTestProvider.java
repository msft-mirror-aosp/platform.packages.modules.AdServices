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

package com.android.sdksandbox.cts.provider;

import static com.google.common.truth.Truth.assertThat;

import android.app.sdksandbox.SandboxedSdk;
import android.app.sdksandbox.SandboxedSdkProvider;
import android.content.Context;
import android.os.Binder;
import android.os.Bundle;
import android.util.Log;
import android.view.View;

import java.io.File;
import java.io.FileInputStream;
import java.io.FileNotFoundException;

public class SdkSandboxDataIsolationTestProvider extends SandboxedSdkProvider {

    private static final String TAG = "SdkSandboxDataIsolationTestProvider";
    private static final String BUNDLE_KEY_PHASE_NAME = "phase-name";

    private static final String JAVA_FILE_PERMISSION_DENIED_MSG =
            "open failed: EACCES (Permission denied)";
    private static final String JAVA_FILE_NOT_FOUND_MSG =
            "open failed: ENOENT (No such file or directory)";
    private static final String JAVA_IS_A_DIRECTORY_ERROR_MSG =
            "open failed: EISDIR (Is a directory)";

    private static final String APP_PKG = "com.android.sdksandbox.cts.app";
    private static final String APP_2_PKG = "com.android.sdksandbox.cts.app2";

    @Override
    public SandboxedSdk onLoadSdk(Bundle params) {
        return new SandboxedSdk(new Binder());
    }

    @Override
    public View getView(Context windowContext, Bundle params, int width, int height) {
        handlePhase(params);
        return new View(windowContext);
    }

    private void handlePhase(Bundle params) {
        String phaseName = params.getString(BUNDLE_KEY_PHASE_NAME, "");
        Log.i(TAG, "Handling phase: " + phaseName);
        switch (phaseName) {
            case "testSdkSandboxDataIsolation_SandboxCanAccessItsDirectory":
                testSdkSandboxDataIsolation_SandboxCanAccessItsDirectory();
                break;
            case "testSdkSandboxDataIsolation_CannotVerifyAppExistence":
                testSdkSandboxDataIsolation_CannotVerifyAppExistence();
                break;
            case "testSdkSandboxDataIsolation_CannotVerifyOtherUserAppExistence":
                testSdkSandboxDataIsolation_CannotVerifyOtherUserAppExistence(params);
                break;
            case "testSdkSandboxDataIsolation_CannotVerifyAcrossVolumes":
                testSdkSandboxDataIsolation_CannotVerifyAcrossVolumes(params);
                break;
            default:
        }
    }

    private void testSdkSandboxDataIsolation_SandboxCanAccessItsDirectory() {
        verifyDirectoryAccess(getContext().getDataDir().toString(), true);
    }

    private void testSdkSandboxDataIsolation_CannotVerifyAppExistence() {
        // Check if the sandbox can check existence of any app through their data directories,
        // profiles or associated sandbox data directories.
        verifyDirectoryAccess("/data/user/0/" + APP_PKG, false);
        verifyDirectoryAccess("/data/user/0/" + APP_2_PKG, false);
        verifyDirectoryAccess("/data/user/0/does.not.exist", false);

        verifyDirectoryAccess("/data/misc/profiles/cur/0/" + APP_PKG, false);
        verifyDirectoryAccess("/data/misc/profiles/cur/0/" + APP_2_PKG, false);
        verifyDirectoryAccess("/data/misc/profiles/cur/0/does.not.exist", false);

        verifyDirectoryAccess("/data/misc_ce/0/sdksandbox/" + APP_2_PKG, false);
        verifyDirectoryAccess("/data/misc_ce/0/sdksandbox/does.not.exist", false);
        verifyDirectoryAccess("/data/misc_de/0/sdksandbox/" + APP_2_PKG, false);
        verifyDirectoryAccess("/data/misc_de/0/sdksandbox/does.not.exist", false);
    }

    private void testSdkSandboxDataIsolation_CannotVerifyOtherUserAppExistence(Bundle params) {
        final String otherUserId = params.getString("sandbox_isolation_user_id");

        String sandboxPackageDir1 = "/data/misc_ce/" + otherUserId + "/sdksandbox/" + APP_PKG;
        String sandboxPackageDir2 = "/data/misc_ce/" + otherUserId + "/sdksandbox/" + APP_2_PKG;

        // Check error message obtained when trying to access each of these packages.
        verifyDirectoryAccess(sandboxPackageDir1, false);
        verifyDirectoryAccess(sandboxPackageDir2, false);
    }

    private void testSdkSandboxDataIsolation_CannotVerifyAcrossVolumes(Bundle params) {
        verifyDirectoryAccess(getContext().getApplicationContext().getDataDir().toString(), true);

        String uuid = params.getString("sandbox_isolation_uuid");
        String volumePath = "/mnt/expand/" + uuid;

        verifyDirectoryAccess(volumePath + "/user/0/" + APP_2_PKG, false);
        verifyDirectoryAccess(volumePath + "/user/0/does.not.exist", false);
        verifyDirectoryAccess(volumePath + "/misc_ce/0/sdksandbox/" + APP_2_PKG, false);
        verifyDirectoryAccess(volumePath + "/misc_ce/0/sdksandbox/does.not.exist", false);
        verifyDirectoryAccess(volumePath + "/misc_de/0/sdksandbox/" + APP_2_PKG, false);
        verifyDirectoryAccess(volumePath + "/misc_de/0/sdksandbox/does.not.exist", false);
    }

    private void verifyDirectoryAccess(String path, boolean shouldBeAccessible) {
        File file = new File(path);
        try {
            new FileInputStream(file);
        } catch (FileNotFoundException exception) {
            if (shouldBeAccessible) {
                assertThat(exception.getMessage()).contains(JAVA_IS_A_DIRECTORY_ERROR_MSG);
            } else {
                assertThat(exception.getMessage()).contains(JAVA_FILE_NOT_FOUND_MSG);
                assertThat(exception.getMessage()).doesNotContain(JAVA_FILE_PERMISSION_DENIED_MSG);
                assertThat(exception.getMessage()).doesNotContain(JAVA_IS_A_DIRECTORY_ERROR_MSG);
            }
        }
    }
}

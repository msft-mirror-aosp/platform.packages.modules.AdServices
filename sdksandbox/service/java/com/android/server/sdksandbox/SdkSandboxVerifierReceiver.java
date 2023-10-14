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

import android.content.BroadcastReceiver;
import android.content.Context;
import android.content.Intent;
import android.content.pm.PackageInfo;
import android.content.pm.PackageManager;
import android.os.Build;
import android.os.Handler;
import android.os.Looper;
import android.util.Log;

import com.android.server.sdksandbox.verifier.SdkDexVerifier;

/**
 * Broadcast Receiver for receiving new Sdk install requests and verifying Sdk code before running
 * it in Sandbox.
 *
 * @hide
 */
public class SdkSandboxVerifierReceiver extends BroadcastReceiver {

    private static final String TAG = "SdkSandboxVerifier";
    private static final Handler MAIN_HANDLER = new Handler(Looper.getMainLooper());

    @Override
    public void onReceive(Context context, Intent intent) {
        if (!Intent.ACTION_PACKAGE_NEEDS_VERIFICATION.equals(intent.getAction())) {
            return;
        }

        Log.d(TAG, "Received sdk sandbox verification intent " + intent.toString());
        Log.d(TAG, "Extras " + intent.getExtras());

        verifySdkHandler(context, intent);
    }

    private void verifySdkHandler(Context context, Intent intent) {
        int verificationId = intent.getIntExtra(PackageManager.EXTRA_VERIFICATION_ID, -1);
        String apkPath = intent.getData() != null ? intent.getData().getPath() : null;

        PackageInfo packageInfo = null;
        if (apkPath != null) {
            packageInfo = context.getPackageManager().getPackageArchiveInfo(apkPath, /* flags */ 0);
        }

        if (packageInfo == null) {
            Log.e(TAG, "Package data to verify was absent or invalid.");
            context.getPackageManager()
                    .verifyPendingInstall(verificationId, PackageManager.VERIFICATION_REJECT);
            return;
        }

        int targetSdkVersion =
                packageInfo.applicationInfo != null
                        ? packageInfo.applicationInfo.targetSdkVersion
                        : Build.VERSION.SDK_INT;
        MAIN_HANDLER.post(
                () -> SdkDexVerifier.getInstance().startDexVerification(apkPath, targetSdkVersion));

        // Verification will continue to run on background, return VERIFICATION_ALLOW to
        // unblock install
        context.getPackageManager()
                .verifyPendingInstall(verificationId, PackageManager.VERIFICATION_ALLOW);
        Log.d(TAG, "Sent VERIFICATION_ALLOW");
    }
}

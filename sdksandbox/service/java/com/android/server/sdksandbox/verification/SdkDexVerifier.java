/*
 * Copyright (C) 2023 The Android Open Source Project
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

package com.android.server.sdksandbox.verifier;

import android.annotation.NonNull;
import android.os.SystemClock;
import android.util.Log;

import com.android.internal.annotations.GuardedBy;
import com.android.server.sdksandbox.proto.Verifier.AllowedApisList;
import com.android.server.sdksandbox.proto.Verifier.AllowedApisPerTargetSdk;

import java.io.File;
import java.util.Map;

/**
 * Verifies the SDK being installed against the APIs allowlist. Verification runs on DEX files of
 * the SDK package.
 *
 * @hide
 */
public class SdkDexVerifier {

    private final Object mPlatformApiAllowlistsLock = new Object();

    private static final String TAG = "SdkSandboxVerifier";

    private static final String ALLOW_LIST_PROTO_CURRENT_RES =
            "platform_api_allowlist_per_target_sdk_version_current.binarypb";

    private static SdkDexVerifier sSdkDexVerifier;

    // Maps targetSdkVersion to its allowlist
    @GuardedBy("mPlatformApiAllowlistsLock")
    private Map<Long, AllowedApisList> mPlatformApiAllowlists;

    private SdkDexVerifier() {
        // TODO(b/279165123): initialize dex parser
    }

    private boolean loadPlatformApiAllowlist() {
        // TODO: Read a DeviceConfig to decide between current or next
        try {
            byte[] allowlistBytes =
                    this.getClass()
                            .getClassLoader()
                            .getResource(ALLOW_LIST_PROTO_CURRENT_RES)
                            .openStream()
                            .readAllBytes();

            AllowedApisPerTargetSdk allowedApisPerTargetSdk =
                    AllowedApisPerTargetSdk.parseFrom(allowlistBytes);

            synchronized (mPlatformApiAllowlistsLock) {
                mPlatformApiAllowlists = allowedApisPerTargetSdk.getAllowlistPerTargetSdk();
            }
            return true;

        } catch (Exception e) {
            Log.e(TAG, "Could not parse allowlists", e);
            return false;
        }
    }

    /** Returns a singleton instance of {@link SdkDexVerifier} */
    @NonNull
    public static SdkDexVerifier getInstance() {
        synchronized (SdkDexVerifier.class) {
            if (sSdkDexVerifier == null) {
                sSdkDexVerifier = new SdkDexVerifier();
            }
        }
        return sSdkDexVerifier;
    }

    /** Initializes the allowlist for a given target sandbox sdk version */
    private void initAllowlist() throws Exception {
        synchronized (mPlatformApiAllowlistsLock) {
            if (mPlatformApiAllowlists == null) {
                loadPlatformApiAllowlist();
            }
        }
    }

    /**
     * Starts verification of the requested sdk
     *
     * @param sdkPath path to the sdk package to be verified
     */
    public void startDexVerification(String sdkPath) {
        long startTime = SystemClock.elapsedRealtime();

        try {
            initAllowlist();
        } catch (Exception e) {
            Log.e(TAG, "Could not initialize allowlists", e);
            return;
        }

        File sdkPathFile = new File(sdkPath);

        if (!sdkPathFile.exists()) {
            Log.e(TAG, "Apk to verify not found: " + sdkPath);
        }

        // TODO(b/231441674): load apk dex data and, verify against allowtrie.

        long verificationTime = SystemClock.elapsedRealtime() - startTime;
        Log.d(TAG, "Verification time: " + verificationTime);

        // TODO(b/231441674): cache and log verification result
    }
}

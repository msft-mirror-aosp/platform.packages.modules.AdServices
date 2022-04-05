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

import android.content.Context;
import android.content.pm.ApplicationInfo;
import android.content.pm.PackageManager;
import android.content.pm.SharedLibraryInfo;
import android.os.UserHandle;
import android.os.UserManager;
import android.util.Base64;
import android.util.Log;

import com.android.server.LocalManagerRegistry;
import com.android.server.pm.PackageManagerLocal;

import java.security.SecureRandom;
import java.util.ArrayList;
import java.util.Collections;
import java.util.List;

/**
 * Helper class to handle all logics related to sdk data
 */
class SdkSandboxStorageManager {
    private static final String TAG = "SdkSandboxManager";

    private final Context mContext;
    private final PackageManagerLocal mPackageManagerLocal;

    SdkSandboxStorageManager(Context context) {
        mContext = context;
        mPackageManagerLocal = LocalManagerRegistry.getManager(PackageManagerLocal.class);
    }

    /**
     * Handle package added or updated event.
     *
     * On package added or updated, we need to reconcile sdk subdirectories for the new/updated
     * package.
     */
    void onPackageAddedOrUpdated(String packageName, int uid) {
        reconcileSdkData(packageName, uid, /*forInstrumentation=*/false);
    }

    void notifyInstrumentationStarted(String packageName, int uid) {
        reconcileSdkData(packageName, uid, /*forInstrumentation=*/true);
    }

    private void reconcileSdkData(String packageName, int uid, boolean forInstrumentation) {
        final List<SharedLibraryInfo> sdksUsed = getSdksUsed(packageName);
        if (sdksUsed.isEmpty()) {
            if (forInstrumentation) {
                Log.w(TAG,
                        "Running instrumentation for the sdk-sandbox process belonging to client "
                                + "app "
                                + packageName + " (uid = " + uid
                                + "). However client app doesn't depend on any SDKs. Only "
                                + "creating \"shared\" sdk sandbox data sub directory");
            } else {
                return;
            }
        }
        final List<String> subDirNames = new ArrayList<>();
        subDirNames.add("shared");
        for (int i = 0; i < sdksUsed.size(); i++) {
            final SharedLibraryInfo sdk = sdksUsed.get(i);
            //TODO(b/223386213): We need to scan the sdk package directory so that we don't create
            //multiple subdirectories for the same sdk, due to passing different random suffix.
            subDirNames.add(sdk.getName() + "@" + getRandomString());
        }
        final UserHandle userHandle = UserHandle.getUserHandleForUid(uid);
        final int userId = userHandle.getIdentifier();
        final int appId = UserHandle.getAppId(uid);
        final int flags = mContext.getSystemService(UserManager.class).isUserUnlocked(userHandle)
                ? PackageManagerLocal.FLAG_STORAGE_CE | PackageManagerLocal.FLAG_STORAGE_DE
                : PackageManagerLocal.FLAG_STORAGE_DE;

        try {
            //TODO(b/224719352): Pass actual seinfo from here
            mPackageManagerLocal.reconcileSdkData(/*volumeUuid=*/null, packageName, subDirNames,
                    userId, appId, /*previousAppId=*/-1, /*seInfo=*/"default", flags);
        } catch (Exception e) {
            // We will retry when sdk gets loaded
            Log.w(TAG, "Failed to reconcileSdkData for " + packageName + " subDirNames: "
                    + String.join(", ", subDirNames) + " error: " + e.getMessage());
        }
    }

    // Returns a random string.
    private static String getRandomString() {
        SecureRandom random = new SecureRandom();
        byte[] bytes = new byte[16];
        random.nextBytes(bytes);
        return Base64.encodeToString(bytes, Base64.URL_SAFE | Base64.NO_WRAP);
    }

    /**
     * Returns list of sdks {@code packageName} uses
     */
    @SuppressWarnings("MixedMutabilityReturnType")
    private List<SharedLibraryInfo> getSdksUsed(String packageName) {
        List<SharedLibraryInfo> result = new ArrayList<>();
        PackageManager pm = mContext.getPackageManager();
        try {
            ApplicationInfo info = pm.getApplicationInfo(
                    packageName, PackageManager.GET_SHARED_LIBRARY_FILES);
            List<SharedLibraryInfo> sharedLibraries = info.getSharedLibraryInfos();
            for (int i = 0; i < sharedLibraries.size(); i++) {
                final SharedLibraryInfo sharedLib = sharedLibraries.get(i);
                if (sharedLib.getType() != SharedLibraryInfo.TYPE_SDK_PACKAGE) {
                    continue;
                }
                result.add(sharedLib);
            }
            return result;
        } catch (PackageManager.NameNotFoundException ignored) {
            return Collections.emptyList();
        }
    }

}

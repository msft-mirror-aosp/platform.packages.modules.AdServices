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

import android.annotation.Nullable;
import android.content.Context;
import android.content.pm.ApplicationInfo;
import android.content.pm.PackageInfo;
import android.content.pm.PackageManager;
import android.content.pm.SharedLibraryInfo;
import android.os.UserHandle;
import android.os.UserManager;
import android.text.TextUtils;
import android.util.ArraySet;
import android.util.Base64;
import android.util.Log;

import com.android.internal.annotations.GuardedBy;
import com.android.server.LocalManagerRegistry;
import com.android.server.pm.PackageManagerLocal;

import java.io.File;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.security.SecureRandom;
import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.Set;

/**
 * Helper class to handle all logics related to sdk data
 */
class SdkSandboxStorageManager {
    private static final String TAG = "SdkSandboxManager";

    private final Context mContext;
    private final PackageManagerLocal mPackageManagerLocal;
    private final Object mLock = new Object();

    SdkSandboxStorageManager(Context context) {
        mContext = context;
        mPackageManagerLocal = LocalManagerRegistry.getManager(PackageManagerLocal.class);
    }

    public void notifyInstrumentationStarted(String packageName, int uid) {
        synchronized (mLock) {
            reconcileSdkDataSubDirs(packageName, uid, /*forInstrumentation=*/true);
        }
    }

    /**
     * Handle package added or updated event.
     *
     * On package added or updated, we need to reconcile sdk subdirectories for the new/updated
     * package.
     */
    void onPackageAddedOrUpdated(String packageName, int uid) {
        synchronized (mLock) {
            reconcileSdkDataSubDirs(packageName, uid, /*forInstrumentation=*/false);
        }
    }

    /**
     * Handle user unlock event.
     *
     * When user unlocks their device, the credential encrypted storage becomes available for
     * reconcilation.
     */
    public void onUserUnlocking(int userId) {
        synchronized (mLock) {
            reconcileSdkDataInvalidPackageDirs(userId);
        }
    }

    @GuardedBy("mLock")
    private void reconcileSdkDataSubDirs(String packageName, int uid, boolean forInstrumentation) {
        final UserHandle userHandle = UserHandle.getUserHandleForUid(uid);
        final int userId = userHandle.getIdentifier();
        final List<SharedLibraryInfo> sdksUsed = getSdksUsed(userId, packageName);
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
    private List<SharedLibraryInfo> getSdksUsed(int userId, String packageName) {
        List<SharedLibraryInfo> result = new ArrayList<>();
        PackageManager pm = getPackageManager(userId);
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

    /**
     * For the given {@code userId}, ensure that sdk data package directories are still valid.
     *
     * The primary concern of this method is to remove invalid data directories. Missing valid
     * directories will get created when the app loads sdk for the first time.
     */
    @GuardedBy("mLock")
    private void reconcileSdkDataInvalidPackageDirs(int userId) {
        Log.i(TAG, "Reconciling sdk data package directories for " + userId);
        reconcileSdkDataInvalidPackageDirs(userId, /*isCeData=*/true);
        reconcileSdkDataInvalidPackageDirs(userId, /*isCeData=*/false);
    }

    @GuardedBy("mLock")
    private void reconcileSdkDataInvalidPackageDirs(int userId, boolean isCeData) {
        Set<String> packageNamesWithDataDirs = getPackageNamesWithDataDirectories(userId);

        // Collect package names from root directory
        //TODO(b/226095967): We should sync data on all volumes
        final String rootDir = getSdkDataRootDirectory(null, userId, isCeData);
        final String[] sdkPackages = new File(rootDir).list();

        // Now loop over package directories and remove the ones that are invalid
        for (int i = 0; i < sdkPackages.length; i++) {
            final String packageName = sdkPackages[i];
            if (packageNamesWithDataDirs.contains(packageName)) continue;
            destroySdkDataPackageDirectory(null, userId, packageName, isCeData);
        }
    }

    private Set<String> getPackageNamesWithDataDirectories(int userId) {
        PackageManager pm = getPackageManager(userId);
        List<PackageInfo> packageInfoList = pm.getInstalledPackages(
                PackageManager.MATCH_UNINSTALLED_PACKAGES);
        ArraySet<String> result = new ArraySet<>();
        for (int i = 0; i < packageInfoList.size(); i++) {
            result.add(packageInfoList.get(i).packageName);
        }
        return result;
    }

    private PackageManager getPackageManager(int userId) {
        return mContext.createContextAsUser(UserHandle.of(userId), 0).getPackageManager();
    }

    @GuardedBy("mLock")
    private void destroySdkDataPackageDirectory(@Nullable String volumeUuid, int userId,
            String packageName, boolean isCeData) {
        final Path packageDir = Paths.get(getSdkDataPackageDirectory(volumeUuid, userId,
                    packageName, isCeData));
        if (!Files.exists(packageDir)) {
            return;
        }

        Log.i(TAG, "Destroying sdk data package directory " + packageDir);

        // Even though system owns the package directory, the sub-directories are owned by sandbox.
        // We first need to get rid of sub-directories.
        try {
            final int flag = isCeData
                    ? PackageManagerLocal.FLAG_STORAGE_CE
                    : PackageManagerLocal.FLAG_STORAGE_DE;
            mPackageManagerLocal.reconcileSdkData(volumeUuid, packageName,
                    Collections.emptyList(), userId, /*appId=*/-1, /*previousAppId=*/-1,
                    /*seInfo=*/"default", flag);
        } catch (Exception e) {
            Log.e(TAG, "Failed to destroy sdk data on user unlock for userId: " + userId
                    + " packageName: " + packageName +  " error: " + e.getMessage());
        }

        // Now that the package directory is empty, we can delete it
        try {
            Files.delete(packageDir);
        } catch (Exception e) {
            Log.e(TAG, "Failed to destroy sdk data on user unlock for userId: " + userId
                    + " packageName: " + packageName +  " error: " + e.getMessage());
        }
    }

    private static String getDataDirectory(@Nullable String volumeUuid) {
        if (TextUtils.isEmpty(volumeUuid)) {
            return "/data";
        } else {
            return "/mnt/expand/" + volumeUuid;
        }
    }

    private static String getSdkDataRootDirectory(@Nullable String volumeUuid, int userId,
            boolean isCeData) {
        return getDataDirectory(volumeUuid) + (isCeData ? "/misc_ce/" : "/misc_de/") + userId
            + "/sdksandbox";
    }

    private static String getSdkDataPackageDirectory(@Nullable String volumeUuid, int userId,
            String packageName, boolean isCeData) {
        return getSdkDataRootDirectory(volumeUuid, userId, isCeData) + "/" + packageName;
    }
}

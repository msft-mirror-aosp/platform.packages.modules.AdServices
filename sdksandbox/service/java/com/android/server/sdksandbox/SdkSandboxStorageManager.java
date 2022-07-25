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

import android.annotation.NonNull;
import android.annotation.Nullable;
import android.content.Context;
import android.content.pm.ApplicationInfo;
import android.content.pm.PackageInfo;
import android.content.pm.PackageManager;
import android.content.pm.SharedLibraryInfo;
import android.os.UserHandle;
import android.os.UserManager;
import android.text.TextUtils;
import android.util.ArrayMap;
import android.util.ArraySet;
import android.util.Base64;
import android.util.Log;

import com.android.internal.annotations.GuardedBy;
import com.android.internal.annotations.VisibleForTesting;
import com.android.server.pm.PackageManagerLocal;

import java.io.File;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.security.SecureRandom;
import java.util.ArrayList;
import java.util.Collections;
import java.util.Iterator;
import java.util.List;
import java.util.Locale;
import java.util.Set;
import java.util.UUID;

/**
 * Helper class to handle all logics related to sdk data
 */
class SdkSandboxStorageManager {
    private static final String TAG = "SdkSandboxManager";

    private final Context mContext;
    private final Object mLock = new Object();

    // Prefix to prepend with all sdk storage paths.
    private final String mRootDir;

    private final SdkSandboxManagerLocal mSdkSandboxManagerLocal;
    private final PackageManagerLocal mPackageManagerLocal;

    SdkSandboxStorageManager(
            Context context,
            SdkSandboxManagerLocal sdkSandboxManagerLocal,
            PackageManagerLocal packageManagerLocal) {
        this(context, sdkSandboxManagerLocal, packageManagerLocal, /*rootDir=*/ "");
    }

    @VisibleForTesting(visibility = VisibleForTesting.Visibility.PRIVATE)
    SdkSandboxStorageManager(
            Context context,
            SdkSandboxManagerLocal sdkSandboxManagerLocal,
            PackageManagerLocal packageManagerLocal,
            String rootDir) {
        mContext = context;
        mSdkSandboxManagerLocal = sdkSandboxManagerLocal;
        mPackageManagerLocal = packageManagerLocal;
        mRootDir = rootDir;
    }

    public void notifyInstrumentationStarted(CallingInfo callingInfo) {
        synchronized (mLock) {
            reconcileSdkDataSubDirs(callingInfo, /*forInstrumentation=*/true);
        }
    }

    /**
     * Handle package added or updated event.
     *
     * On package added or updated, we need to reconcile sdk subdirectories for the new/updated
     * package.
     */
    void onPackageAddedOrUpdated(CallingInfo callingInfo) {
        synchronized (mLock) {
            reconcileSdkDataSubDirs(callingInfo, /*forInstrumentation=*/false);
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
            reconcileSdkDataPackageDirs(userId);
        }
    }

    void prepareSdkDataOnLoad(CallingInfo callingInfo) {
        synchronized (mLock) {
            reconcileSdkDataSubDirs(callingInfo, /*forInstrumentation=*/false);
        }
    }

    SdkDataDirInfo getSdkDataDirInfo(CallingInfo callingInfo, String sdkName) {
        final int uid = callingInfo.getUid();
        final String packageName = callingInfo.getPackageName();
        final String cePackagePath = getSdkDataPackageDirectory(/*volumeUuid=*/null,
                getUserId(uid), packageName, /*isCeData=*/true);
        final String dePackagePath = getSdkDataPackageDirectory(/*volumeUuid=*/null,
                getUserId(uid), packageName, /*isCeData=*/false);
        // TODO(b/232924025): We should have these information cached, instead of rescanning dirs.
        synchronized (mLock) {
            final String sdkCeSubDirName = getSubDirs(cePackagePath).getOrDefault(sdkName, null);
            final String sdkCeSubDirPath = (sdkCeSubDirName == null) ? null
                    : Paths.get(cePackagePath, sdkCeSubDirName).toString();
            final String sdkDeSubDirName = getSubDirs(dePackagePath).getOrDefault(sdkName, null);
            final String sdkDeSubDirPath = (sdkDeSubDirName == null) ? null
                    : Paths.get(dePackagePath, sdkDeSubDirName).toString();
            return new SdkDataDirInfo(sdkCeSubDirPath, sdkDeSubDirPath);
        }
    }

    private int getUserId(int uid) {
        final UserHandle userHandle = UserHandle.getUserHandleForUid(uid);
        return userHandle.getIdentifier();
    }

    @GuardedBy("mLock")
    private void reconcileSdkDataSubDirs(CallingInfo callingInfo, boolean forInstrumentation) {
        final int uid = callingInfo.getUid();
        final int userId = getUserId(uid);
        final String packageName = callingInfo.getPackageName();
        final List<String> sdksUsed = getSdksUsed(userId, packageName);
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
        String volumeUuid = null;
        try {
            volumeUuid = getVolumeUuidForPackage(userId, packageName);
        } catch (Exception e) {
            Log.w(TAG, "Failed to find package " + packageName + " error: " + e.getMessage());
            return;
        }
        final String deSdkDataPackagePath =
                getSdkDataPackageDirectory(volumeUuid, userId, packageName, /*isCeData=*/ false);
        final ArrayMap<String, String> existingDeSubdirMap = getSubDirs(deSdkDataPackagePath);
        final List<String> subDirNames = new ArrayList<>();
        subDirNames.add("shared");
        for (int i = 0; i < sdksUsed.size(); i++) {
            final String sdk = sdksUsed.get(i);
            if (!existingDeSubdirMap.containsKey(sdk)) {
                subDirNames.add(sdk + "@" + getRandomString());
            } else {
                subDirNames.add(existingDeSubdirMap.get(sdk));
            }
        }
        final int appId = UserHandle.getAppId(uid);
        final UserManager um = mContext.getSystemService(UserManager.class);
        int flags = 0;
        boolean doesCeNeedReconcile = false;
        boolean doesDeNeedReconcile = false;
        final Set<String> expectedSubDirNames = new ArraySet<>(sdksUsed);
        expectedSubDirNames.add("shared");
        final UserHandle userHandle = UserHandle.getUserHandleForUid(uid);
        if (um.isUserUnlockingOrUnlocked(userHandle)) {
            final String ceSdkDataPackagePath =
                    getSdkDataPackageDirectory(volumeUuid, userId, packageName, /*isCeData=*/ true);
            final Set<String> ceSdkDirsBeforeReconcilePrefix =
                    getSubDirs(ceSdkDataPackagePath).keySet();
            final Set<String> deSdkDirsBeforeReconcilePrefix = existingDeSubdirMap.keySet();
            flags = PackageManagerLocal.FLAG_STORAGE_CE | PackageManagerLocal.FLAG_STORAGE_DE;
            doesCeNeedReconcile = !ceSdkDirsBeforeReconcilePrefix.equals(expectedSubDirNames);
            doesDeNeedReconcile = !deSdkDirsBeforeReconcilePrefix.equals(expectedSubDirNames);
        } else {
            final Set<String> deSdkDirsBeforeReconcilePrefix = existingDeSubdirMap.keySet();
            flags = PackageManagerLocal.FLAG_STORAGE_DE;
            doesDeNeedReconcile = !deSdkDirsBeforeReconcilePrefix.equals(expectedSubDirNames);
        }
        if (doesCeNeedReconcile || doesDeNeedReconcile) {
            try {
                // TODO(b/224719352): Pass actual seinfo from here
                mPackageManagerLocal.reconcileSdkData(
                        volumeUuid,
                        packageName,
                        subDirNames,
                        userId,
                        appId,
                        /*previousAppId=*/ -1,
                        /*seInfo=*/ "default",
                        flags);
            } catch (Exception e) {
                // We will retry when sdk gets loaded
                Log.w(TAG, "Failed to reconcileSdkData for " + packageName + " subDirNames: "
                        + String.join(", ", subDirNames) + " error: " + e.getMessage());
            }
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
    private List<String> getSdksUsed(int userId, String packageName) {
        PackageManager pm = getPackageManager(userId);
        try {
            ApplicationInfo info = pm.getApplicationInfo(
                    packageName, PackageManager.GET_SHARED_LIBRARY_FILES);
            return getSdksUsed(info);
        } catch (PackageManager.NameNotFoundException ignored) {
            return Collections.emptyList();
        }
    }

    private static List<String> getSdksUsed(ApplicationInfo info) {
        List<String> result = new ArrayList<>();
        List<SharedLibraryInfo> sharedLibraries = info.getSharedLibraryInfos();
        for (int i = 0; i < sharedLibraries.size(); i++) {
            final SharedLibraryInfo sharedLib = sharedLibraries.get(i);
            if (sharedLib.getType() != SharedLibraryInfo.TYPE_SDK_PACKAGE) {
                continue;
            }
            result.add(sharedLib.getName());
        }
        return result;
    }

    /**
     * For the given {@code userId}, ensure that sdk data package directories are still valid.
     *
     * The primary concern of this method is to remove invalid data directories. Missing valid
     * directories will get created when the app loads sdk for the first time.
     */
    @GuardedBy("mLock")
    private void reconcileSdkDataPackageDirs(int userId) {
        Log.i(TAG, "Reconciling sdk data package directories for " + userId);
        PackageInfoHolder pmInfoHolder = new PackageInfoHolder(mContext, userId);
        reconcileSdkDataPackageDirs(userId, /*isCeData=*/true, pmInfoHolder);
        reconcileSdkDataPackageDirs(userId, /*isCeData=*/false, pmInfoHolder);
    }

    @GuardedBy("mLock")
    private void reconcileSdkDataPackageDirs(
            int userId, boolean isCeData, PackageInfoHolder pmInfoHolder) {

        final List<String> volumeUuids = getMountedVolumes();
        for (int i = 0; i < volumeUuids.size(); i++) {
            final String volumeUuid = volumeUuids.get(i);
            final String rootDir = getSdkDataRootDirectory(volumeUuid, userId, isCeData);
            final String[] sdkPackages = new File(rootDir).list();
            if (sdkPackages == null) {
                continue;
            }
            // Now loop over package directories and remove the ones that are invalid
            for (int j = 0; j < sdkPackages.length; j++) {
                final String packageName = sdkPackages[j];
                // Only consider installed packages which are not instrumented and either
                // not using sdk or on incorrect volume for destroying
                final int uid = pmInfoHolder.getUid(packageName);
                final boolean isInstrumented =
                        mSdkSandboxManagerLocal.isInstrumentationRunning(packageName, uid);
                final boolean hasCorrectVolume =
                        TextUtils.equals(volumeUuid, pmInfoHolder.getVolumeUuid(packageName));
                final boolean isInstalled = !pmInfoHolder.isUninstalled(packageName);
                final boolean usesSdk = pmInfoHolder.usesSdk(packageName);
                if (!isInstrumented && isInstalled && (!hasCorrectVolume || !usesSdk)) {
                    destroySdkDataPackageDirectory(volumeUuid, userId, packageName, isCeData);
                }
            }
        }

        // Now loop over all installed packages and ensure all packages have sdk data directories
        final Iterator<String> it = pmInfoHolder.getInstalledPackagesUsingSdks().iterator();
        while (it.hasNext()) {
            final String packageName = it.next();
            final String volumeUuid = pmInfoHolder.getVolumeUuid(packageName);
            // Verify if package dir contains a subdir for each sdk and a shared directory
            final String packageDir = getSdkDataPackageDirectory(volumeUuid, userId, packageName,
                    isCeData);
            final Set<String> subDirs = getSubDirs(packageDir).keySet();
            final Set<String> expectedSubDirNames = pmInfoHolder.getSdksUsed(packageName);
            // Add the shared directory name to expectedSubDirNames
            expectedSubDirNames.add("shared");
            if (subDirs.equals(expectedSubDirNames)) {
                continue;
            }

            Log.i(TAG, "Reconciling missing package directory for: " + packageDir);
            final int uid = pmInfoHolder.getUid(packageName);
            if (uid == -1) {
                Log.w(TAG, "Failed to get uid for reconcilation of " + packageDir);
                // Safe to continue since we will retry during loading sdk
                continue;
            }
            final CallingInfo callingInfo = new CallingInfo(uid, packageName);
            reconcileSdkDataSubDirs(callingInfo, /*forInstrumentation=*/false);
        }
    }

    // Returns a map of: sdk_name->sdk_name_with_random_suffix
    private ArrayMap<String, String> getSubDirs(String path) {
        final File parent = new File(path);
        final String[] children = parent.list();
        if (children == null) {
            return new ArrayMap<>();
        }
        final ArrayMap<String, String> result = new ArrayMap<>();
        for (int i = 0; i < children.length; i++) {
            final String[] tokens = children[i].split("@");
            result.put(tokens[0], children[i]);
        }
        return result;
    }

    private PackageManager getPackageManager(int userId) {
        return mContext.createContextAsUser(UserHandle.of(userId), 0).getPackageManager();
    }

    @GuardedBy("mLock")
    private void destroySdkDataPackageDirectory(
            @Nullable String volumeUuid, int userId, String packageName, boolean isCeData) {
        final Path packageDir =
                Paths.get(getSdkDataPackageDirectory(volumeUuid, userId, packageName, isCeData));
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
            Log.e(
                    TAG,
                    "Failed to destroy sdk data on user unlock for userId: "
                            + userId
                            + " packageName: "
                            + packageName
                            + " error: "
                            + e.getMessage());
        }
    }

    private String getDataDirectory(@Nullable String volumeUuid) {
        if (TextUtils.isEmpty(volumeUuid)) {
            return mRootDir + "/data";
        } else {
            return mRootDir + "/mnt/expand/" + volumeUuid;
        }
    }

    private String getSdkDataRootDirectory(
            @Nullable String volumeUuid, int userId, boolean isCeData) {
        return getDataDirectory(volumeUuid) + (isCeData ? "/misc_ce/" : "/misc_de/") + userId
            + "/sdksandbox";
    }

    @VisibleForTesting(visibility = VisibleForTesting.Visibility.PRIVATE)
    String getSdkDataPackageDirectory(
            @Nullable String volumeUuid, int userId, String packageName, boolean isCeData) {
        return getSdkDataRootDirectory(volumeUuid, userId, isCeData) + "/" + packageName;
    }

    private static class PackageInfoHolder {
        private final Context mContext;
        final ArrayMap<String, Set<String>> mPackagesWithSdks = new ArrayMap<>();
        final ArrayMap<String, Integer> mPackageNameToUid = new ArrayMap<>();
        final ArrayMap<String, String> mPackageNameToVolumeUuid = new ArrayMap<>();
        final Set<String> mUninstalledPackages = new ArraySet<>();

        PackageInfoHolder(Context context, int userId) {
            mContext = context.createContextAsUser(UserHandle.of(userId), 0);

            PackageManager pm = mContext.getPackageManager();
            final List<PackageInfo> packageInfoList = pm.getInstalledPackages(
                    PackageManager.GET_SHARED_LIBRARY_FILES);
            final ArraySet<String> installedPackages = new ArraySet<>();

            for (int i = 0; i < packageInfoList.size(); i++) {
                final PackageInfo info = packageInfoList.get(i);
                installedPackages.add(info.packageName);
                final String volumeUuid =
                        StorageUuuidConverter.convertToVolumeUuid(info.applicationInfo.storageUuid);
                mPackageNameToVolumeUuid.put(info.packageName, volumeUuid);
                mPackageNameToUid.put(info.packageName, info.applicationInfo.uid);

                final List<String> sdksUsedNames =
                        SdkSandboxStorageManager.getSdksUsed(info.applicationInfo);
                if (sdksUsedNames.isEmpty()) {
                    continue;
                }
                mPackagesWithSdks.put(info.packageName, new ArraySet<>(sdksUsedNames));
            }

            // If an app is uninstalled with DELETE_KEEP_DATA flag, we need to preserve its sdk
            // data. For that, we need names of uninstalled packages.
            final List<PackageInfo> allPackages =
                    pm.getInstalledPackages(PackageManager.MATCH_UNINSTALLED_PACKAGES);
            for (int i = 0; i < allPackages.size(); i++) {
                final String packageName = allPackages.get(i).packageName;
                if (!installedPackages.contains(packageName)) {
                    mUninstalledPackages.add(packageName);
                }
            }
        }

        public boolean isUninstalled(String packageName) {
            return mUninstalledPackages.contains(packageName);
        }

        public int getUid(String packageName) {
            return mPackageNameToUid.getOrDefault(packageName, -1);
        }

        public Set<String> getInstalledPackagesUsingSdks() {
            return mPackagesWithSdks.keySet();
        }

        public Set<String> getSdksUsed(String packageName) {
            return mPackagesWithSdks.get(packageName);
        }

        public boolean usesSdk(String packageName) {
            return mPackagesWithSdks.containsKey(packageName);
        }

        public String getVolumeUuid(String packageName) {
            return mPackageNameToVolumeUuid.get(packageName);
        }
    }

    // TODO(b/234023859): We will remove this class once the required APIs get unhidden
    // The class below has been copied from StorageManager's convert logic
    private static class StorageUuuidConverter {
        private static final String FAT_UUID_PREFIX = "fafafafa-fafa-5afa-8afa-fafa";
        private static final UUID UUID_DEFAULT =
                UUID.fromString("41217664-9172-527a-b3d5-edabb50a7d69");
        private static final String UUID_SYSTEM = "system";
        private static final UUID UUID_SYSTEM_ =
                UUID.fromString("5d258386-e60d-59e3-826d-0089cdd42cc0");
        private static final String UUID_PRIVATE_INTERNAL = null;
        private static final String UUID_PRIMARY_PHYSICAL = "primary_physical";
        private static final UUID UUID_PRIMARY_PHYSICAL_ =
                UUID.fromString("0f95a519-dae7-5abf-9519-fbd6209e05fd");

        private static @Nullable String convertToVolumeUuid(@NonNull UUID storageUuid) {
            if (UUID_DEFAULT.equals(storageUuid)) {
                return UUID_PRIVATE_INTERNAL;
            } else if (UUID_PRIMARY_PHYSICAL_.equals(storageUuid)) {
                return UUID_PRIMARY_PHYSICAL;
            } else if (UUID_SYSTEM_.equals(storageUuid)) {
                return UUID_SYSTEM;
            } else {
                String uuidString = storageUuid.toString();
                // This prefix match will exclude fsUuids from private volumes because
                // (a) linux fsUuids are generally Version 4 (random) UUIDs so the prefix
                // will contain 4xxx instead of 5xxx and (b) we've already matched against
                // known namespace (Version 5) UUIDs above.
                if (uuidString.startsWith(FAT_UUID_PREFIX)) {
                    String fatStr =
                            uuidString.substring(FAT_UUID_PREFIX.length()).toUpperCase(Locale.US);
                    return fatStr.substring(0, 4) + "-" + fatStr.substring(4);
                }

                return storageUuid.toString();
            }
        }
    }

    // We loop over "/mnt/expand" directory's children and find the volumeUuids
    // TODO(b/234023859): We want to use storage manager api in future for this task
    @VisibleForTesting(visibility = VisibleForTesting.Visibility.PRIVATE)
    List<String> getMountedVolumes() {
        // Collect package names from root directory
        final List<String> volumeUuids = new ArrayList<>();
        volumeUuids.add(null);

        final String[] mountedVolumes = new File(mRootDir + "/mnt/expand").list();
        if (mountedVolumes == null) {
            return volumeUuids;
        }

        for (int i = 0; i < mountedVolumes.length; i++) {
            final String volumeUuid = mountedVolumes[i];
            volumeUuids.add(volumeUuid);
        }
        return volumeUuids;
    }

    private @Nullable String getVolumeUuidForPackage(int userId, String packageName)
            throws PackageManager.NameNotFoundException {
        PackageManager pm = getPackageManager(userId);
        ApplicationInfo info = pm.getApplicationInfo(packageName, /*flags=*/ 0);
        return StorageUuuidConverter.convertToVolumeUuid(info.storageUuid);
    }

    /**
     * Sdk data directories for a particular sdk.
     *
     * Every sdk has two data directories. One is credentially encrypted storage and another is
     * device encrypted.
     */
    static class SdkDataDirInfo {
        @Nullable final String mCeData;
        @Nullable final String mDeData;

        SdkDataDirInfo(@Nullable String ceDataPath, @Nullable String deDataPath) {
            mCeData = ceDataPath;
            mDeData = deDataPath;
        }

        @Nullable String getCeDataDir() {
            return mCeData;
        }

        @Nullable String getDeDataDir() {
            return mDeData;
        }
    }
}

/*
 * Copyright (C) 2024 The Android Open Source Project
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
package com.android.adservices.service.common;

import static com.android.adservices.service.stats.AdServicesStatsLog.AD_SERVICES_ERROR_REPORTED__ERROR_CODE__PACKAGE_DENY_PROCESS_ERROR_DISABLED;
import static com.android.adservices.service.stats.AdServicesStatsLog.AD_SERVICES_ERROR_REPORTED__ERROR_CODE__PACKAGE_DENY_PROCESS_ERROR_FAILED_FILTERING_INSTALLED_PACKAGES;
import static com.android.adservices.service.stats.AdServicesStatsLog.AD_SERVICES_ERROR_REPORTED__ERROR_CODE__PACKAGE_DENY_PROCESS_ERROR_FAILED_READING_CACHE;
import static com.android.adservices.service.stats.AdServicesStatsLog.AD_SERVICES_ERROR_REPORTED__ERROR_CODE__PACKAGE_DENY_PROCESS_ERROR_FAILED_READING_FILE;
import static com.android.adservices.service.stats.AdServicesStatsLog.AD_SERVICES_ERROR_REPORTED__ERROR_CODE__PACKAGE_DENY_PROCESS_ERROR_FAILED_UPDATING_CACHE;
import static com.android.adservices.service.stats.AdServicesStatsLog.AD_SERVICES_ERROR_REPORTED__ERROR_CODE__PACKAGE_DENY_PROCESS_ERROR_FAILURE_UNKNOWN;
import static com.android.adservices.service.stats.AdServicesStatsLog.AD_SERVICES_ERROR_REPORTED__ERROR_CODE__PACKAGE_DENY_PROCESS_ERROR_NO_FILE_FOUND;
import static com.android.adservices.service.stats.AdServicesStatsLog.AD_SERVICES_ERROR_REPORTED__PPAPI_NAME__COMMON;

import android.annotation.Nullable;
import android.content.pm.ApplicationInfo;
import android.content.pm.PackageManager;
import android.net.Uri;
import android.os.Build;

import androidx.annotation.NonNull;
import androidx.annotation.RequiresApi;
import androidx.datastore.guava.GuavaDataStore;

import com.android.adservices.LogUtil;
import com.android.adservices.concurrency.AdServicesExecutors;
import com.android.adservices.download.MobileDataDownloadFactory;
import com.android.adservices.errorlogging.ErrorLogUtil;
import com.android.adservices.service.Flags;
import com.android.adservices.service.FlagsFactory;
import com.android.adservices.service.common.compat.FileCompatUtils;
import com.android.adservices.service.proto.ApiDenyGroupsForPackage;
import com.android.adservices.service.proto.ApiGroupsCache;
import com.android.adservices.service.proto.PackageToApiDenyGroupsCacheMap;
import com.android.adservices.service.proto.PackageToApiDenyGroupsMap;
import com.android.adservices.service.proto.PackageType;
import com.android.adservices.shared.common.ApplicationContextSingleton;
import com.android.adservices.shared.datastore.ProtoSerializer;
import com.android.internal.annotations.VisibleForTesting;

import com.google.android.libraries.mobiledatadownload.GetFileGroupRequest;
import com.google.android.libraries.mobiledatadownload.MobileDataDownload;
import com.google.android.libraries.mobiledatadownload.file.SynchronousFileStorage;
import com.google.android.libraries.mobiledatadownload.file.openers.ReadStreamOpener;
import com.google.common.util.concurrent.FluentFuture;
import com.google.common.util.concurrent.Futures;
import com.google.common.util.concurrent.ListenableFuture;
import com.google.mobiledatadownload.ClientConfigProto;
import com.google.protobuf.ExtensionRegistryLite;

import java.io.IOException;
import java.util.Collections;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.stream.Collectors;

@RequiresApi(Build.VERSION_CODES.S)
public final class AdPackageDenyResolver {
    private static final String GROUP_NAME = "package-deny";
    private static final String PACKAGE_DENY_DATA_STORE =
            FileCompatUtils.getAdservicesFilename("package_deny_data_store");
    private final boolean mEnablePackageDeny;
    @Nullable private final MobileDataDownload mMobileDataDownload;
    @Nullable private final SynchronousFileStorage mFileStorage;

    @Nullable
    private final GuavaDataStore<PackageToApiDenyGroupsCacheMap> mPackageDenyCacheDataStore;

    private static final AdPackageDenyResolver sAdPackageDenyResolver = newInstance();

    private AdPackageDenyResolver(
            @Nullable MobileDataDownload mobileDataDownload,
            @Nullable SynchronousFileStorage synchronousFileStorage,
            @Nullable GuavaDataStore<PackageToApiDenyGroupsCacheMap> packageDenyCacheDataStore,
            boolean enablePackageDeny) {
        LogUtil.d("initializing AdPackageDenyResolver instance");
        this.mMobileDataDownload = mobileDataDownload;
        this.mFileStorage = synchronousFileStorage;
        this.mPackageDenyCacheDataStore = packageDenyCacheDataStore;
        mEnablePackageDeny = enablePackageDeny;
    }

    /**
     * Creates a new instance of {@link AdPackageDenyResolver}.
     *
     * <p>This method uses the `enablePackageDenyMdd` flag from {@link FlagsFactory} to determine
     * how the `AdPackageDenyResolver` should be initialized.
     *
     * <p>If the flag is enabled: - Obtains an MDD instance from {@link MobileDataDownloadFactory}.
     * - Retrieves a file storage instance from {@link MobileDataDownloadFactory}. - Uses a
     * `PackageToApiDenyGroupsCacheMapDataStore` (obtained via
     * `getPackageToApiDenyGroupsCacheMapDataStore()`). - Sets enablement flag to `true`.
     *
     * <p>If the flag is disabled: - It creates an instance with all dependencies set to `null` and
     * the enablement flag set to `false`.
     *
     * @return A new instance of `AdPackageDenyResolver` configured based on the
     *     `enablePackageDenyMdd` flag.
     */
    private static AdPackageDenyResolver newInstance() {
        try {
            Flags flags = FlagsFactory.getFlags();
            if (flags.getEnablePackageDenyService()) {
                return new AdPackageDenyResolver(
                        MobileDataDownloadFactory.getMdd(flags),
                        MobileDataDownloadFactory.getFileStorage(),
                        getPackageToApiDenyGroupsCacheMapDataStore(),
                        true);
            }
        } catch (Exception e) {
            LogUtil.e("Error initializing AdPackageDenyResolver %s", e.getMessage());
        }
        return new AdPackageDenyResolver(null, null, null, false);
    }

    @VisibleForTesting
    static AdPackageDenyResolver newInstanceForTests(
            @NonNull MobileDataDownload mobileDataDownload,
            @NonNull SynchronousFileStorage synchronousFileStorage,
            @NonNull GuavaDataStore<PackageToApiDenyGroupsCacheMap> packageDataStore,
            boolean enablePackageDeny) {
        return new AdPackageDenyResolver(
                mobileDataDownload, synchronousFileStorage, packageDataStore, enablePackageDeny);
    }

    private static GuavaDataStore<PackageToApiDenyGroupsCacheMap>
            getPackageToApiDenyGroupsCacheMapDataStore() {
        return new GuavaDataStore.Builder(
                        ApplicationContextSingleton.get(),
                        PACKAGE_DENY_DATA_STORE,
                        new ProtoSerializer<PackageToApiDenyGroupsCacheMap>(
                                PackageToApiDenyGroupsCacheMap.getDefaultInstance(),
                                ExtensionRegistryLite.getEmptyRegistry()))
                .setExecutor(AdServicesExecutors.getBackgroundExecutor())
                .build();
    }

    /**
     * Returns the singleton instance of {@link AdPackageDenyResolver}.
     *
     * <p>This method provides access to the single instance of the `AdPackageDenyResolver` class,
     * ensuring that all parts of the application use the same resolver object.
     *
     * @return The singleton instance of `AdPackageDenyResolver`.
     */
    public static AdPackageDenyResolver getInstance() {
        return sAdPackageDenyResolver;
    }

    /**
     * Determines whether an app or SDK should be denied access based on the provided API groups.
     *
     * <p>This method checks if access should be denied for a given caller app or SDK based on their
     * usage of specific API groups. It uses a package deny list loaded from MDD and cached in
     * `mPackageDenyCacheDataStore`.
     *
     * <p>The logic works as follows: 1. **Feature Check:** If the package deny feature
     * (`mEnablePackageDeny`) is disabled, it logs a message and immediately returns `false`. 2.
     * **Input Validation:** If `apiGroups` is null or empty, or both `callerAppName` and
     * `callerSdkName` are null, it returns `false`. 3. **Deny List Check:** It retrieves the
     * package deny list data from the cache (`mPackageDenyCacheDataStore`). 4. **App and SDK
     * Check:** It checks if the `callerAppName` or `callerSdkName` exists in the deny list and if
     * there's any overlap between the provided `apiGroups` and the API groups associated with the
     * caller app or SDK in the deny list. If an overlap is found, it means the package should be
     * denied access.
     *
     * @param callerAppName The name of the calling app. Can be null or empty.
     * @param callerSdkName The name of the calling SDK. Can be null or empty.
     * @param apiGroups The set of API groups being accessed.
     * @return a {@link ListenableFuture} representing the result of the deny list check. The future
     *     yields `true` if the package should be denied access, `false` otherwise.
     */
    public ListenableFuture<Boolean> shouldDenyPackage(
            String callerAppName, String callerSdkName, Set<String> apiGroups) {
        if (!mEnablePackageDeny) {
            LogUtil.e(
                    "shouldDenyPackage() is called on package-based deny-list that is disabled"
                            + " with calling app %s, calling sdk %s and api groups %s",
                    callerAppName, callerSdkName, apiGroups);
            PackageDenyMddProcessStatus.logError(PackageDenyMddProcessStatus.DISABLED);
            return Futures.immediateFuture(false);
        }
        if (apiGroups == null
                || apiGroups.isEmpty()
                || (callerAppName == null && callerSdkName == null)) {
            return Futures.immediateFuture(false);
        }
        return Futures.transform(
                mPackageDenyCacheDataStore.getDataAsync(),
                packageToApiDenyGroupsCacheMap ->
                        (callerAppName != null
                                        && !callerAppName.isBlank()
                                        && !Collections.disjoint(
                                                apiGroups,
                                                packageToApiDenyGroupsCacheMap
                                                        .getAppToApiDenyGroupsCacheMap()
                                                        .getOrDefault(
                                                                callerAppName,
                                                                ApiGroupsCache.getDefaultInstance())
                                                        .getApiGroupList()))
                                || (callerSdkName != null
                                        && !callerSdkName.isBlank()
                                        && !Collections.disjoint(
                                                apiGroups,
                                                packageToApiDenyGroupsCacheMap
                                                        .getSdkToApiDenyGroupsCacheMap()
                                                        .getOrDefault(
                                                                callerSdkName,
                                                                ApiGroupsCache.getDefaultInstance())
                                                        .getApiGroupList())),
                AdServicesExecutors.getBackgroundExecutor());
    }

    /**
     * Loads and processes package deny data from Mobile Data Download (MDD).
     *
     * <p>This method orchestrates the retrieval and processing of package deny data from MDD. It
     * performs the following steps asynchronously: 1. **Checks Feature Flag:** Verifies if the
     * package deny feature is enabled (`mEnablePackageDeny`). If disabled, it returns a {@link
     * PackageDenyMddProcessStatus#DISABLED} status. 2. **Fetches MDD File Group:** Initiates a
     * request to MDD to fetch the file group associated with the package deny data (using
     * `mMobileDataDownload::getFileGroup`). 3. **Extracts MDD File:** Obtains the actual file from
     * the fetched file group (using `this::getMddFile`). 4. **Parses the File:** Parses the content
     * of the MDD file, converting it into a usable data structure (using `this::parseFile`). 5.
     * **Filters Data:** Filters the parsed data to retain only information relevant to installed
     * packages (using `this::filterMddDataToInstalledPackages`). 6. **Updates Cache:**
     * Asynchronously updates the package deny cache data store (`mPackageDenyCacheDataStore`) with
     * the processed data. 7. **Handles Errors:** Includes error handling for `PackageDenyException`
     * and general `Exception` to log issues and return appropriate {@link
     * PackageDenyMddProcessStatus} values.
     *
     * @return A {@link ListenableFuture} representing the eventual result of the MDD data loading
     *     and processing operation. The future yields a {@link PackageDenyMddProcessStatus}
     *     indicating the outcome of the process (success, failure, or disabled).
     */
    public ListenableFuture<PackageDenyMddProcessStatus> loadDenyDataFromMdd() {
        LogUtil.d("Executing load deny data from mdd download");
        if (!mEnablePackageDeny) {
            LogUtil.d("Package deny service flag is false, returning...");
            PackageDenyMddProcessStatus.logError(PackageDenyMddProcessStatus.DISABLED);
            return Futures.immediateFuture(PackageDenyMddProcessStatus.DISABLED);
        }
        return FluentFuture.from(
                        Futures.immediateFuture(
                                GetFileGroupRequest.newBuilder().setGroupName(GROUP_NAME).build()))
                .transformAsync(
                        mMobileDataDownload::getFileGroup,
                        AdServicesExecutors.getLightWeightExecutor())
                .transform(this::getMddFile, AdServicesExecutors.getLightWeightExecutor())
                .transform(this::parseFile, AdServicesExecutors.getBackgroundExecutor())
                .transform(this::convertToCacheMap, AdServicesExecutors.getLightWeightExecutor())
                .transformAsync(
                        map -> mPackageDenyCacheDataStore.updateDataAsync(data -> map),
                        AdServicesExecutors.getBackgroundExecutor())
                .transform(
                        // TODO (b/365605754) add metrics for success
                        x -> PackageDenyMddProcessStatus.SUCCESS,
                        AdServicesExecutors.getLightWeightExecutor())
                .catching(
                        PackageDenyException.class,
                        e -> {
                            LogUtil.e(
                                    "PackageDenyException: %s with cause: %s",
                                    e.getMessage(), e.getCause());
                            PackageDenyMddProcessStatus packageDenyMddProcessStatus =
                                    PackageDenyMddProcessStatus.valueOf(e.getMessage());
                            PackageDenyMddProcessStatus.logError(packageDenyMddProcessStatus);
                            return packageDenyMddProcessStatus;
                        },
                        AdServicesExecutors.getLightWeightExecutor())
                .catching(
                        Exception.class,
                        e -> {
                            LogUtil.e("Failure in deny package mdd process %s", e.getMessage());
                            PackageDenyMddProcessStatus.logError(
                                    PackageDenyMddProcessStatus.FAILED_UPDATING_CACHE);
                            return PackageDenyMddProcessStatus.FAILED_UPDATING_CACHE;
                        },
                        AdServicesExecutors.getLightWeightExecutor());
    }

    private ClientConfigProto.ClientFile getMddFile(
            ClientConfigProto.ClientFileGroup clientFileGroup) {
        if (clientFileGroup == null) {
            LogUtil.d("MDD has not downloaded the package deny file yet for group %s", GROUP_NAME);
            throw new PackageDenyException(PackageDenyMddProcessStatus.NO_FILE_FOUND);
        }
        return clientFileGroup.getFileList().stream()
                .filter(file -> file.getFileId().endsWith(".pb"))
                .findFirst()
                .orElseThrow(
                        () -> new PackageDenyException(PackageDenyMddProcessStatus.NO_FILE_FOUND));
    }

    private PackageToApiDenyGroupsMap parseFile(ClientConfigProto.ClientFile clientFile) {
        try {
            return PackageToApiDenyGroupsMap.parseFrom(
                    mFileStorage.open(
                            Uri.parse(clientFile.getFileUri()), ReadStreamOpener.create()));
        } catch (IOException e) {
            throw new PackageDenyException(PackageDenyMddProcessStatus.FAILED_READING_FILE, e);
        }
    }

    /**
     * Converts the PackageToApiDenyGroupsMap to PackageToApiDenyGroupsCacheMap based on whether the
     * package is installed.
     *
     * <p>If the enableInstalledPackageFilter flag is enabled, this method retrieves a map of
     * installed packages and their version codes, and then filters the input
     * packageToApiDenyGroupsMap to only include entries for installed packages. If the flag is
     * disabled, the input map is returned without filtering and does not deny based on package
     * version.
     *
     * @param packageToApiDenyGroupsMap The input PackageToApiDenyGroupsMap to filter.
     * @return The converted PackageToApiDenyGroupsCacheMap.
     * @throws PackageDenyException if an error occurs during the filtering process, specifically
     *     with PackageDenyMddProcessStatus#FAILED_FILTERING_INSTALLED_PACKAGES status.
     */
    @VisibleForTesting
    PackageToApiDenyGroupsCacheMap convertToCacheMap(
            PackageToApiDenyGroupsMap packageToApiDenyGroupsMap) {
        try {
            if (FlagsFactory.getFlags().getPackageDenyEnableInstalledPackageFilter()) {
                Map<String, Long> installedPackages =
                        getInstalledPackageNameToVersionCodeMap(
                                ApplicationContextSingleton.get().getPackageManager());
                return getCacheMapFilteredByInstalledPackages(
                        packageToApiDenyGroupsMap, installedPackages);
            } else {
                LogUtil.d("Package installed filter is disabled");
                return getCacheMapWithoutFilter(packageToApiDenyGroupsMap);
            }

        } catch (Exception e) {
            throw new PackageDenyException(
                    PackageDenyMddProcessStatus.FAILED_FILTERING_INSTALLED_PACKAGES, e);
        }
    }

    /**
     * Filters a PackageToApiDenyGroupsMap to include only installed packages and transforms it into
     * a PackageToApiDenyGroupsCacheMap with optimized API group representations.
     *
     * <p>This method takes a PackageToApiDenyGroupsMap and a map of installed packages with their
     * version codes. It performs the following steps: 1. **Filters the input map:** Keeps only the
     * entries corresponding to installed packages. 2. **Groups by package type:** Groups the
     * entries into two categories: APP and SDK. 3. **Transforms into nested maps:** Creates a
     * nested map structure where the outer map keys are package types (`PackageType`), and the
     * inner map keys are package names with values as lists of applicable API groups. This is done
     * by calling `getApiGroupsForInstalledPackages` to determine the relevant API groups for each
     * package based on its installed version. 4. Converts the inner maps from "package name to list
     * of API groups to "package name to `ApiGroupsCache`" using the `mapToApiGroupsCache` method.
     * 5. **Constructs the result:** Builds a `PackageToApiDenyGroupsCacheMap` object from the
     * optimized map, separating APP and SDK entries into their respective fields.
     *
     * @param packageToApiDenyGroupsMap The input PackageToApiDenyGroupsMap to be filtered and
     *     transformed.
     * @param installedPackages A map of installed package names to their corresponding version
     *     codes.
     * @return A PackageToApiDenyGroupsCacheMap containing filtered and optimized API deny group
     *     information for installed packages.
     */
    private static PackageToApiDenyGroupsCacheMap getCacheMapFilteredByInstalledPackages(
            PackageToApiDenyGroupsMap packageToApiDenyGroupsMap,
            Map<String, Long> installedPackages) {
        Map<PackageType, Map<String, List<String>>> map =
                packageToApiDenyGroupsMap.getMapMap().entrySet().stream()
                        // filter installed packages from the file
                        .filter(
                                packageDenyApiGroupsEntry ->
                                        installedPackages.containsKey(
                                                packageDenyApiGroupsEntry.getKey()))
                        .collect(
                                Collectors.groupingBy(
                                        entry -> entry.getValue().getPackageType(),
                                        Collectors.mapping(
                                                entry -> entry,
                                                Collectors.toMap(
                                                        Map.Entry::getKey,
                                                        e ->
                                                                getApiGroupsForInstalledPackages(
                                                                        e.getValue(),
                                                                        installedPackages.get(
                                                                                e.getKey()))))));

        // TODO (b/365605754) add error log for unknown package type
        return getPackageToApiDenyGroupsCacheMap(map);
    }

    private static PackageToApiDenyGroupsCacheMap getPackageToApiDenyGroupsCacheMap(
            Map<PackageType, Map<String, List<String>>> map) {
        LogUtil.d("package deny map for cache is %s", map);
        Map<PackageType, Map<String, ApiGroupsCache>> apiGroupsCacheMap =
                map.entrySet().stream()
                        .map((e -> Map.entry(e.getKey(), mapToApiGroupsCache(e.getValue()))))
                        .filter(e -> e.getValue().size() > 0)
                        .collect(Collectors.toMap(Map.Entry::getKey, Map.Entry::getValue));
        return PackageToApiDenyGroupsCacheMap.newBuilder()
                .putAllAppToApiDenyGroupsCacheMap(
                        apiGroupsCacheMap.getOrDefault(PackageType.APP, Collections.emptyMap()))
                .putAllSdkToApiDenyGroupsCacheMap(
                        apiGroupsCacheMap.getOrDefault(PackageType.SDK, Collections.emptyMap()))
                .build();
    }

    private static PackageToApiDenyGroupsCacheMap getCacheMapWithoutFilter(
            PackageToApiDenyGroupsMap packageToApiDenyGroupsMap) {
        Map<PackageType, Map<String, List<String>>> map =
                packageToApiDenyGroupsMap.getMapMap().entrySet().stream()
                        .collect(
                                Collectors.groupingBy(
                                        entry -> entry.getValue().getPackageType(),
                                        Collectors.mapping(
                                                entry -> entry,
                                                Collectors.toMap(
                                                        Map.Entry::getKey,
                                                        e -> collectApiGroups(e.getValue())))));
        return getPackageToApiDenyGroupsCacheMap(map);
    }

    private static List<String> collectApiGroups(ApiDenyGroupsForPackage apiDenyGroupsForPackage) {
        return apiDenyGroupsForPackage.getApiDenyGroupsForPackageVersionsList().stream()
                .flatMap(
                        appApiDenyGroup ->
                                appApiDenyGroup.getApiGroups().getApiGroupList().stream())
                .distinct()
                .collect(Collectors.toList());
    }

    /**
     * Converts a map of package names to lists of API groups into a map of package names to
     * ApiGroupsCache objects.
     *
     * <p>This method iterates through the input map, filters out entries with empty API group
     * lists, and then transforms each remaining entry into a new entry where the value is an
     * ApiGroupsCache object built from the original list of API groups. The resulting map provides
     * an optimized representation of API group information for each package.
     *
     * @param map The input map where keys are package names and values are API group names.
     * @return A map where keys are package names and values are corresponding ApiGroupsCache
     *     objects.
     */
    private static Map<String, ApiGroupsCache> mapToApiGroupsCache(Map<String, List<String>> map) {
        return map.entrySet().stream()
                .filter(e1 -> e1.getValue().size() > 0)
                .map(
                        e1 ->
                                Map.entry(
                                        e1.getKey(),
                                        ApiGroupsCache.newBuilder()
                                                .addAllApiGroup(e1.getValue())
                                                .build()))
                .collect(Collectors.toMap(Map.Entry::getKey, Map.Entry::getValue));
    }

    /**
     * Gets a map of installed package names to their corresponding version codes.
     *
     * @param pm The {@link PackageManager} to use for retrieving installed applications.
     * @return A map where the keys are package names (String) and the values are version codes
     *     (Long).
     */
    private Map<String, Long> getInstalledPackageNameToVersionCodeMap(PackageManager pm) {
        Map<String, Long> installedPackages =
                pm.getInstalledApplications(PackageManager.GET_META_DATA).stream()
                        .filter(applicationInfo -> applicationInfo.packageName != null)
                        .collect(
                                Collectors.groupingBy(
                                        applicationInfo -> applicationInfo.packageName,
                                        Collectors.reducing(
                                                0L, // Initial value
                                                applicationInfo ->
                                                        getPackageVersion(pm, applicationInfo),
                                                // Map each application to their versioncode
                                                (v1, v2) -> v1 == 0L ? v2 : v1
                                                // Keep the first version code encountered
                                                )));
        LogUtil.d("installed packages name to versioncode map is %s", installedPackages);
        return installedPackages;
    }

    /**
     * Retrieves a list of API groups that apply to an installed package based on its version.
     *
     * <p>This method iterates through the `apiDenyGroupsForPackageVersionsList` in the provided
     * ApiDenyGroupsForPackage object. It filters the list to include only those version ranges that
     * encompass the given `installedVersion`. For each matching version range, it extracts the
     * associated API groups, flattens them into a single stream, removes duplicates, and finally
     * collects them into a list.
     *
     * @param apiDenyGroupsForPackage The ApiDenyGroupsForPackage object containing the API deny
     *     groups and their corresponding package version ranges.
     * @param installedVersion The installed version code of the package.
     * @return A list of distinct API group names (String) that apply to the installed package
     *     version.
     */
    private static List<String> getApiGroupsForInstalledPackages(
            ApiDenyGroupsForPackage apiDenyGroupsForPackage, long installedVersion) {
        return apiDenyGroupsForPackage.getApiDenyGroupsForPackageVersionsList().stream()
                .filter(
                        apiDenyGroupsForAppVersions ->
                                installedVersion
                                                >= apiDenyGroupsForAppVersions
                                                        .getPackageMinVersion()
                                        && installedVersion
                                                <= (apiDenyGroupsForAppVersions
                                                                        .getPackageMaxVersion()
                                                                > 0
                                                        ? apiDenyGroupsForAppVersions
                                                                .getPackageMaxVersion()
                                                        : Long.MAX_VALUE))
                .flatMap(
                        appApiDenyGroup ->
                                appApiDenyGroup.getApiGroups().getApiGroupList().stream())
                .distinct()
                .collect(Collectors.toList());
    }

    /**
     * Retrieves the version code of a package.
     *
     * <p>This method attempts to retrieve the long version code of a package using the provided
     * {@link PackageManager} and {@link ApplicationInfo}. If the package is not found, it logs an
     * error and returns 0.
     *
     * @param pm The {@link PackageManager} to use for retrieving package information.
     * @param applicationInfo The {@link ApplicationInfo} containing the package name.
     * @return The long version code of the package, or 0 if the package is not found.
     */
    private static long getPackageVersion(PackageManager pm, ApplicationInfo applicationInfo) {
        try {
            return pm.getPackageInfo(applicationInfo.packageName, 0).getLongVersionCode();
        } catch (PackageManager.NameNotFoundException e) {
            LogUtil.e(
                    "Deny package service cannot find package version for %s",
                    applicationInfo.packageName);
            return 0L;
        }
    }

    public enum PackageDenyMddProcessStatus {
        SUCCESS(-1),
        DISABLED(AD_SERVICES_ERROR_REPORTED__ERROR_CODE__PACKAGE_DENY_PROCESS_ERROR_DISABLED),
        FAILURE(AD_SERVICES_ERROR_REPORTED__ERROR_CODE__PACKAGE_DENY_PROCESS_ERROR_FAILURE_UNKNOWN),
        NO_FILE_FOUND(
                AD_SERVICES_ERROR_REPORTED__ERROR_CODE__PACKAGE_DENY_PROCESS_ERROR_NO_FILE_FOUND),
        FAILED_READING_FILE(
                AD_SERVICES_ERROR_REPORTED__ERROR_CODE__PACKAGE_DENY_PROCESS_ERROR_FAILED_READING_FILE),
        FAILED_FILTERING_INSTALLED_PACKAGES(
                AD_SERVICES_ERROR_REPORTED__ERROR_CODE__PACKAGE_DENY_PROCESS_ERROR_FAILED_FILTERING_INSTALLED_PACKAGES),
        FAILED_UPDATING_CACHE(
                AD_SERVICES_ERROR_REPORTED__ERROR_CODE__PACKAGE_DENY_PROCESS_ERROR_FAILED_UPDATING_CACHE),
        FAILED_READING_CACHE(
                AD_SERVICES_ERROR_REPORTED__ERROR_CODE__PACKAGE_DENY_PROCESS_ERROR_FAILED_READING_CACHE),
        ;

        private final int mAdServicesErrorReportedErrorCodePackageDenyProcessError;

        PackageDenyMddProcessStatus(int adServicesErrorReportedErrorCodePackageDenyProcessError) {
            this.mAdServicesErrorReportedErrorCodePackageDenyProcessError =
                    adServicesErrorReportedErrorCodePackageDenyProcessError;
        }

        static void logError(PackageDenyMddProcessStatus packageDenyMddProcessStatus) {
            ErrorLogUtil.e(
                    packageDenyMddProcessStatus
                            .mAdServicesErrorReportedErrorCodePackageDenyProcessError,
                    AD_SERVICES_ERROR_REPORTED__PPAPI_NAME__COMMON);
        }
    }

    private static final class PackageDenyException extends RuntimeException {
        PackageDenyException(PackageDenyMddProcessStatus packageDenyMddProcessStatus) {
            super(packageDenyMddProcessStatus.name());
        }

        PackageDenyException(PackageDenyMddProcessStatus packageDenyMddProcessStatus, Throwable t) {
            super(packageDenyMddProcessStatus.name(), t);
        }
    }
}

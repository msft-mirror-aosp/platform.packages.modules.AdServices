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
import static com.android.adservices.service.stats.AdServicesStatsLog.AD_SERVICES_ERROR_REPORTED__ERROR_CODE__PACKAGE_DENY_PROCESS_ERROR_FAILED_READING_FILE;
import static com.android.adservices.service.stats.AdServicesStatsLog.AD_SERVICES_ERROR_REPORTED__ERROR_CODE__PACKAGE_DENY_PROCESS_ERROR_FAILED_UPDATING_CACHE;
import static com.android.adservices.service.stats.AdServicesStatsLog.AD_SERVICES_ERROR_REPORTED__ERROR_CODE__PACKAGE_DENY_PROCESS_ERROR_NO_FILE_FOUND;
import static com.android.adservices.service.stats.AdServicesStatsLog.AD_SERVICES_ERROR_REPORTED__PPAPI_NAME__COMMON;
import static com.android.dx.mockito.inline.extended.ExtendedMockito.doReturn;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyInt;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

import android.content.pm.ApplicationInfo;
import android.content.pm.PackageInfo;
import android.content.pm.PackageManager;

import androidx.datastore.guava.GuavaDataStore;

import com.android.adservices.common.AdServicesExtendedMockitoTestCase;
import com.android.adservices.common.logging.annotations.ExpectErrorLogUtilCall;
import com.android.adservices.download.MobileDataDownloadFactory;
import com.android.adservices.service.FlagsFactory;
import com.android.adservices.service.proto.ApiDenyGroupsForPackage;
import com.android.adservices.service.proto.ApiDenyGroupsForPackageVersions;
import com.android.adservices.service.proto.ApiGroups;
import com.android.adservices.service.proto.ApiGroupsCache;
import com.android.adservices.service.proto.PackageToApiDenyGroupsCacheMap;
import com.android.adservices.service.proto.PackageToApiDenyGroupsMap;
import com.android.adservices.service.proto.PackageType;
import com.android.modules.utils.testing.ExtendedMockitoRule.MockStatic;
import com.android.modules.utils.testing.ExtendedMockitoRule.SpyStatic;

import com.google.android.libraries.mobiledatadownload.MobileDataDownload;
import com.google.android.libraries.mobiledatadownload.file.SynchronousFileStorage;
import com.google.common.util.concurrent.Futures;
import com.google.mobiledatadownload.ClientConfigProto;

import org.junit.Assert;
import org.junit.Before;
import org.junit.Test;
import org.mockito.Mock;

import java.io.IOException;
import java.util.List;
import java.util.Set;
import java.util.concurrent.TimeUnit;

@SpyStatic(FlagsFactory.class)
@MockStatic(MobileDataDownloadFactory.class)
public final class AdPackageDenyResolverTest extends AdServicesExtendedMockitoTestCase {
    public static final int TIMEOUT = 5000;
    private static final String TEST_DENY_PACKAGE_DATA_FILE_DIR = "deny_package";
    private static final String TEST_FILE = "test.pb";
    private static final String TEST_FILE_FAIL = "test_incorrect.pb";
    private AdPackageDenyResolver mAdDenyPackageResolver;
    @Mock private SynchronousFileStorage mMockFileStorage;
    @Mock private MobileDataDownload mMockMdd;
    @Mock private GuavaDataStore<PackageToApiDenyGroupsCacheMap> mMockPackageDenyCacheDataStore;
    @Mock private ClientConfigProto.ClientFile mMockFile;
    private ClientConfigProto.ClientFileGroup mMockFileGroup;

    @Before
    public void setUp() throws Exception {
        appContext.set(mSpyContext);
        mocker.mockGetFlags(mMockFlags);
        when(mMockFlags.getEnablePackageDenyService()).thenReturn(true);
        doReturn(mMockFileStorage).when(MobileDataDownloadFactory::getFileStorage);
        doReturn(mMockMdd).when(() -> (MobileDataDownloadFactory.getMdd(any())));
        createDataStoreForTesting();
        createInstalledPackagesMapForTesting();
        mAdDenyPackageResolver =
                AdPackageDenyResolver.newInstanceForTests(
                        mMockMdd, mMockFileStorage, mMockPackageDenyCacheDataStore, true);
    }

    @Test
    public void testGetInstance() {
        mocker.mockGetFlags(mMockFlags);
        AdPackageDenyResolver firstInstance = AdPackageDenyResolver.getInstance();
        AdPackageDenyResolver secondInstance = AdPackageDenyResolver.getInstance();

        expect.withMessage("first AdPackageDenyResolver Instance").that(firstInstance).isNotNull();
        expect.withMessage("first and second AdPackageDenyResolver should be same")
                .that(firstInstance)
                .isSameInstanceAs(secondInstance);
    }

    @Test
    public void testShouldDeny() throws Exception {
        assertDataStoreDeny();
    }

    @Test
    public void testLoadDenyDataFromMdd_withoutFilter_success() throws Exception {
        when(mMockFlags.getPackageDenyEnableInstalledPackageFilter()).thenReturn(false);
        mockTestMddFile(TEST_FILE);
        when(mMockPackageDenyCacheDataStore.updateDataAsync(any()))
                .thenReturn(
                        Futures.immediateFuture(
                                PackageToApiDenyGroupsCacheMap.newBuilder().build()));
        expect.withMessage("test_loadDenyDataFromMdd_success")
                .that(
                        mAdDenyPackageResolver
                                .loadDenyDataFromMdd()
                                .get(TIMEOUT, TimeUnit.MILLISECONDS))
                .isEqualTo(AdPackageDenyResolver.PackageDenyMddProcessStatus.SUCCESS);
    }

    @Test
    public void testLoadDenyDataFromMdd_withFilter_success() throws Exception {
        mockTestMddFile(TEST_FILE);
        when(mMockFlags.getPackageDenyEnableInstalledPackageFilter()).thenReturn(true);
        when(mMockPackageDenyCacheDataStore.updateDataAsync(any()))
                .thenReturn(
                        Futures.immediateFuture(
                                PackageToApiDenyGroupsCacheMap.newBuilder().build()));

        expect.withMessage("test_loadDenyDataFromMdd_success")
                .that(
                        mAdDenyPackageResolver
                                .loadDenyDataFromMdd()
                                .get(TIMEOUT, TimeUnit.MILLISECONDS))
                .isEqualTo(AdPackageDenyResolver.PackageDenyMddProcessStatus.SUCCESS);
    }

    @Test
    @ExpectErrorLogUtilCall(
            errorCode =
                    AD_SERVICES_ERROR_REPORTED__ERROR_CODE__PACKAGE_DENY_PROCESS_ERROR_FAILED_UPDATING_CACHE,
            ppapiName = AD_SERVICES_ERROR_REPORTED__PPAPI_NAME__COMMON)
    public void testUpdateCache_FutureInterruptedException() throws Exception {
        mockTestMddFile(TEST_FILE);
        when(mMockPackageDenyCacheDataStore.updateDataAsync(any()))
                .thenReturn(Futures.immediateFailedFuture(new InterruptedException()));

        expect.withMessage("test_updateCache_FutureInterruptedException")
                .that(
                        mAdDenyPackageResolver
                                .loadDenyDataFromMdd()
                                .get(TIMEOUT, TimeUnit.MILLISECONDS))
                .isEqualTo(AdPackageDenyResolver.PackageDenyMddProcessStatus.FAILED_UPDATING_CACHE);
        // Test previous data still exits
        assertDataStoreDeny();
    }

    @Test
    @ExpectErrorLogUtilCall(
            errorCode =
                    AD_SERVICES_ERROR_REPORTED__ERROR_CODE__PACKAGE_DENY_PROCESS_ERROR_FAILED_FILTERING_INSTALLED_PACKAGES,
            ppapiName = AD_SERVICES_ERROR_REPORTED__PPAPI_NAME__COMMON)
    public void testInstalledPackages_Exception() throws Exception {
        when(mMockFlags.getPackageDenyEnableInstalledPackageFilter()).thenReturn(true);
        mockTestMddFile(TEST_FILE);
        PackageManager mockPackageManager = mock(PackageManager.class);
        when(mSpyContext.getPackageManager()).thenReturn(mockPackageManager);
        when(mockPackageManager.getInstalledApplications(anyInt()))
                .thenThrow(new RuntimeException());

        expect.withMessage("test_installedPackages_Exception")
                .that(
                        mAdDenyPackageResolver
                                .loadDenyDataFromMdd()
                                .get(TIMEOUT, TimeUnit.MILLISECONDS))
                .isEqualTo(
                        AdPackageDenyResolver.PackageDenyMddProcessStatus
                                .FAILED_FILTERING_INSTALLED_PACKAGES);
        // Test previous data still exits
        assertDataStoreDeny();
    }

    @Test
    @ExpectErrorLogUtilCall(
            errorCode =
                    AD_SERVICES_ERROR_REPORTED__ERROR_CODE__PACKAGE_DENY_PROCESS_ERROR_NO_FILE_FOUND,
            ppapiName = AD_SERVICES_ERROR_REPORTED__PPAPI_NAME__COMMON)
    public void testNoFileGroupFoundException() throws Exception {
        when(mMockMdd.getFileGroup(any())).thenReturn(Futures.immediateFuture(null));
        when(mMockFile.getFileId()).thenReturn(TEST_FILE);
        when(mMockFileStorage.open(any(), any())).thenThrow(IOException.class);

        expect.withMessage("test_noFileGroupFoundException")
                .that(
                        mAdDenyPackageResolver
                                .loadDenyDataFromMdd()
                                .get(TIMEOUT, TimeUnit.MILLISECONDS))
                .isEqualTo(AdPackageDenyResolver.PackageDenyMddProcessStatus.NO_FILE_FOUND);
        // Test previous data still exits
        assertDataStoreDeny();
    }

    @Test
    @ExpectErrorLogUtilCall(
            errorCode =
                    AD_SERVICES_ERROR_REPORTED__ERROR_CODE__PACKAGE_DENY_PROCESS_ERROR_NO_FILE_FOUND,
            ppapiName = AD_SERVICES_ERROR_REPORTED__PPAPI_NAME__COMMON)
    public void testNoFileFoundException() throws Exception {
        when(mMockMdd.getFileGroup(any())).thenReturn(Futures.immediateFuture(mMockFileGroup));
        when(mMockFileStorage.open(any(), any())).thenThrow(IOException.class);

        expect.withMessage("test_noFileFoundException")
                .that(
                        mAdDenyPackageResolver
                                .loadDenyDataFromMdd()
                                .get(TIMEOUT, TimeUnit.MILLISECONDS))
                .isEqualTo(AdPackageDenyResolver.PackageDenyMddProcessStatus.NO_FILE_FOUND);
        // Test previous data still exits
        assertDataStoreDeny();
    }

    @Test
    @ExpectErrorLogUtilCall(
            errorCode =
                    AD_SERVICES_ERROR_REPORTED__ERROR_CODE__PACKAGE_DENY_PROCESS_ERROR_FAILED_READING_FILE,
            ppapiName = AD_SERVICES_ERROR_REPORTED__PPAPI_NAME__COMMON)
    public void testReadFile_fileStorageIOException() throws Exception {
        mockTestMddFile(TEST_FILE_FAIL);
        when(mMockFileStorage.open(any(), any())).thenThrow(IOException.class);

        expect.withMessage("test_ReadFile_fileStorageIOException")
                .that(
                        mAdDenyPackageResolver
                                .loadDenyDataFromMdd()
                                .get(TIMEOUT, TimeUnit.MILLISECONDS))
                .isEqualTo(AdPackageDenyResolver.PackageDenyMddProcessStatus.FAILED_READING_FILE);
        // Test previous data still exits
        assertDataStoreDeny();
    }

    @Test
    @ExpectErrorLogUtilCall(
            errorCode =
                    AD_SERVICES_ERROR_REPORTED__ERROR_CODE__PACKAGE_DENY_PROCESS_ERROR_FAILED_READING_FILE,
            ppapiName = AD_SERVICES_ERROR_REPORTED__PPAPI_NAME__COMMON)
    public void testReadFile_incorrect_format() throws Exception {
        createDataStoreForTesting();
        mockTestMddFile(TEST_FILE_FAIL);

        expect.withMessage("test_readFile_incorrect_format")
                .that(
                        mAdDenyPackageResolver
                                .loadDenyDataFromMdd()
                                .get(TIMEOUT, TimeUnit.MILLISECONDS))
                .isEqualTo(AdPackageDenyResolver.PackageDenyMddProcessStatus.FAILED_READING_FILE);
        // Test previous data still exits
        assertDataStoreDeny();
    }

    @Test
    @ExpectErrorLogUtilCall(
            errorCode = AD_SERVICES_ERROR_REPORTED__ERROR_CODE__PACKAGE_DENY_PROCESS_ERROR_DISABLED,
            ppapiName = AD_SERVICES_ERROR_REPORTED__PPAPI_NAME__COMMON)
    public void testLoadDenyDataFromMdd_disabled() throws Exception {
        when(mMockFlags.getEnablePackageDenyService()).thenReturn(false);
        mAdDenyPackageResolver =
                AdPackageDenyResolver.newInstanceForTests(
                        mMockMdd, mMockFileStorage, mMockPackageDenyCacheDataStore, false);

        expect.withMessage("test_loadDenyDataFromMdd_disabled")
                .that(
                        mAdDenyPackageResolver
                                .loadDenyDataFromMdd()
                                .get(TIMEOUT, TimeUnit.MILLISECONDS))
                .isEqualTo(AdPackageDenyResolver.PackageDenyMddProcessStatus.DISABLED);
    }

    @Test
    public void test_filter_package_install() {
        when(mMockFlags.getPackageDenyEnableInstalledPackageFilter()).thenReturn(true);
        PackageToApiDenyGroupsCacheMap actual =
                mAdDenyPackageResolver.convertToCacheMap(
                        createPackageToApiDenyGroupsMapForTesting());
        Assert.assertTrue(
                "app1 should be present",
                actual.getAppToApiDenyGroupsCacheMap().containsKey("app1"));
        Assert.assertFalse(
                "app_not_installed should not be present",
                actual.getAppToApiDenyGroupsCacheMap().containsKey("app_not_installed"));
    }

    @Test
    public void test_no_filter_package_install() {
        when(mMockFlags.getPackageDenyEnableInstalledPackageFilter()).thenReturn(false);
        PackageToApiDenyGroupsCacheMap actual =
                mAdDenyPackageResolver.convertToCacheMap(
                        createPackageToApiDenyGroupsMapForTesting());
        Assert.assertTrue(
                "app1 should be present",
                actual.getAppToApiDenyGroupsCacheMap().containsKey("app1"));
        Assert.assertTrue(
                "app_not_installed should be present",
                actual.getAppToApiDenyGroupsCacheMap().containsKey("app_not_installed"));
    }

    private void createDataStoreForTesting() {
        when(mMockPackageDenyCacheDataStore.getDataAsync())
                .thenReturn(
                        Futures.immediateFuture(
                                PackageToApiDenyGroupsCacheMap.newBuilder()
                                        .putAppToApiDenyGroupsCacheMap(
                                                "a",
                                                ApiGroupsCache.newBuilder()
                                                        .addAllApiGroup(List.of("a1", "a2"))
                                                        .build())
                                        .putAppToApiDenyGroupsCacheMap(
                                                "b",
                                                ApiGroupsCache.newBuilder()
                                                        .addAllApiGroup(List.of("b1"))
                                                        .build())
                                        .putSdkToApiDenyGroupsCacheMap(
                                                "c",
                                                ApiGroupsCache.newBuilder()
                                                        .addAllApiGroup(List.of("c1"))
                                                        .build())
                                        .putSdkToApiDenyGroupsCacheMap(
                                                "d",
                                                ApiGroupsCache.newBuilder()
                                                        .addAllApiGroup(List.of("d1"))
                                                        .build())
                                        .build()));
    }

    private PackageToApiDenyGroupsMap createPackageToApiDenyGroupsMapForTesting() {
        return PackageToApiDenyGroupsMap.newBuilder()
                .putMap(
                        "app1",
                        ApiDenyGroupsForPackage.newBuilder()
                                .setPackageType(PackageType.APP)
                                .addApiDenyGroupsForPackageVersions(
                                        ApiDenyGroupsForPackageVersions.newBuilder()
                                                .setApiGroups(
                                                        ApiGroups.newBuilder()
                                                                .addApiGroup("a1")
                                                                .addApiGroup("a2")
                                                                .build())
                                                .build())
                                .build())
                .putMap(
                        "app_not_installed",
                        ApiDenyGroupsForPackage.newBuilder()
                                .setPackageType(PackageType.APP)
                                .addApiDenyGroupsForPackageVersions(
                                        ApiDenyGroupsForPackageVersions.newBuilder()
                                                .setApiGroups(
                                                        ApiGroups.newBuilder()
                                                                .addApiGroup("a1")
                                                                .addApiGroup("a2")
                                                                .build())
                                                .build())
                                .build())
                .build();
    }

    private void createInstalledPackagesMapForTesting()
            throws PackageManager.NameNotFoundException {
        PackageManager mockPackageManager = mock(PackageManager.class);
        when(mSpyContext.getPackageManager()).thenReturn(mockPackageManager);
        ApplicationInfo app1 = mockInstalledApplicationInfo(mockPackageManager, "app1", 20);
        ApplicationInfo app2 = mockInstalledApplicationInfo(mockPackageManager, "app2", 310);
        ApplicationInfo app3 = mockInstalledApplicationInfo(mockPackageManager, "app3", 1);
        ApplicationInfo app4 = mockInstalledApplicationInfo(mockPackageManager, "app4", 2);
        ApplicationInfo app5 = mockInstalledApplicationInfo(mockPackageManager, "app5", 3);
        ApplicationInfo app21 = mockInstalledApplicationInfo(mockPackageManager, "app2", 310);
        ApplicationInfo topics1 =
                mockInstalledApplicationInfo(
                        mockPackageManager, "com.example.adservices.samples.topics.sampleapp1", 40);
        ApplicationInfo topics2 =
                mockInstalledApplicationInfo(
                        mockPackageManager, "com.example.adservices.samples.topics.sampleapp2", 40);
        when(mockPackageManager.getInstalledApplications(anyInt()))
                .thenReturn(List.of(app1, app2, app3, app4, app5, app21, topics1, topics2));
    }

    private static ApplicationInfo mockInstalledApplicationInfo(
            PackageManager mockPackageManager, String packageName, long versionCode)
            throws PackageManager.NameNotFoundException {
        ApplicationInfo ai = new ApplicationInfo();
        ai.packageName = packageName;
        PackageInfo mockPackageInfo = new PackageInfo();
        mockPackageInfo.setLongVersionCode(versionCode);
        when(mockPackageManager.getPackageInfo(eq(packageName), anyInt()))
                .thenReturn(mockPackageInfo);
        return ai;
    }

    private void assertDataStoreDeny() throws Exception {
        expect.withMessage("should deny result for calling packages a should be true")
                .that(
                        mAdDenyPackageResolver
                                .shouldDenyPackage("a", "x", Set.of("x", "a2"))
                                .get(TIMEOUT, TimeUnit.MILLISECONDS))
                .isTrue();
        expect.withMessage("should deny result for calling packages a and x should be false")
                .that(
                        mAdDenyPackageResolver
                                .shouldDenyPackage("a", "x", Set.of("x", "y"))
                                .get(TIMEOUT, TimeUnit.MILLISECONDS))
                .isFalse();
        expect.withMessage("should deny result for calling packages a and c should be true")
                .that(
                        mAdDenyPackageResolver
                                .shouldDenyPackage("a", "c", Set.of("x", "c1"))
                                .get(TIMEOUT, TimeUnit.MILLISECONDS))
                .isTrue();
        expect.withMessage("should deny result for calling packages a and d should be false")
                .that(
                        mAdDenyPackageResolver
                                .shouldDenyPackage("a", "d", Set.of("x", "y"))
                                .get(TIMEOUT, TimeUnit.MILLISECONDS))
                .isFalse();
        expect.withMessage("should deny result for calling packages a and b should be false")
                .that(
                        mAdDenyPackageResolver
                                .shouldDenyPackage("a", "b", null)
                                .get(TIMEOUT, TimeUnit.MILLISECONDS))
                .isFalse();
        expect.withMessage("should deny result for calling packages a and b should be false")
                .that(
                        mAdDenyPackageResolver
                                .shouldDenyPackage(null, "b", null)
                                .get(TIMEOUT, TimeUnit.MILLISECONDS))
                .isFalse();
        expect.withMessage("should deny result for calling packages c should be true")
                .that(
                        mAdDenyPackageResolver
                                .shouldDenyPackage(null, "c", Set.of("b1", "c1"))
                                .get(TIMEOUT, TimeUnit.MILLISECONDS))
                .isTrue();
        expect.withMessage("should deny result for null calling packages should be false")
                .that(
                        mAdDenyPackageResolver
                                .shouldDenyPackage(null, null, null)
                                .get(TIMEOUT, TimeUnit.MILLISECONDS))
                .isFalse();
    }

    private void mockTestMddFile(String testFile) throws IOException {
        mMockFileGroup = ClientConfigProto.ClientFileGroup.newBuilder().addFile(mMockFile).build();
        when(mMockFileStorage.open(any(), any()))
                .thenReturn(
                        mContext.getAssets()
                                .open(TEST_DENY_PACKAGE_DATA_FILE_DIR + "/" + testFile));
        when(mMockMdd.getFileGroup(any())).thenReturn(Futures.immediateFuture(mMockFileGroup));

        when(mMockFile.getFileId()).thenReturn(TEST_FILE);
        when(mMockFile.getFileUri()).thenReturn(TEST_FILE);
    }
}

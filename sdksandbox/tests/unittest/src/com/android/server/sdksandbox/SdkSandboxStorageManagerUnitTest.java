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

import static com.google.common.truth.Truth.assertThat;

import android.content.Context;
import android.content.Intent;
import android.content.pm.ApplicationInfo;
import android.content.pm.PackageManager;
import android.os.FileUtils;
import android.os.UserHandle;

import androidx.annotation.NonNull;
import androidx.test.platform.app.InstrumentationRegistry;

import com.android.server.pm.PackageManagerLocal;
import com.android.server.sdksandbox.SdkSandboxStorageManager.SdkDataDirInfo;
import com.android.server.sdksandbox.SdkSandboxStorageManager.SubDirectories;

import org.junit.After;
import org.junit.Before;
import org.junit.Test;
import org.mockito.Mockito;

import java.io.File;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.util.Arrays;
import java.util.Collections;
import java.util.List;
import java.util.Set;
import java.util.UUID;

/** Unit tests for {@link SdkSandboxStorageManager}. */
public class SdkSandboxStorageManagerUnitTest {

    /** Test directory on Android where all storage will be created */
    private static final int CLIENT_UID = 11000;

    private static final String CLIENT_PKG_NAME = "client";
    private static final String SDK_NAME = "sdk";
    private static final String SDK2_NAME = "sdk2";

    // Use the test app's private storage as mount point for sdk storage testing
    private SdkSandboxStorageManager mSdkSandboxStorageManager;
    // Separate location where all of the sdk storage directories will be created for testing
    private String mTestDir;
    private FakeSdkSandboxManagerLocal mSdkSandboxManagerLocal;
    private PackageManager mPmMock;
    private Context mSpyContext;

    @Before
    public void setup() throws Exception {
        Context context = InstrumentationRegistry.getInstrumentation().getContext();
        mTestDir = context.getDataDir().getPath();
        mSpyContext = Mockito.spy(context);

        mPmMock = Mockito.mock(PackageManager.class);
        Mockito.doReturn(mPmMock).when(mSpyContext).getPackageManager();
        Mockito.doReturn(mSpyContext)
                .when(mSpyContext)
                .createContextAsUser(Mockito.any(UserHandle.class), Mockito.anyInt());

        PackageManagerLocal packageManagerLocal =
                (volumeUuid,
                        packageName,
                        subDirNames,
                        userId,
                        appId,
                        previousAppId,
                        seInfo,
                        flags) -> {};

        mSdkSandboxManagerLocal = new FakeSdkSandboxManagerLocal();
        mSdkSandboxStorageManager =
                new SdkSandboxStorageManager(
                        mSpyContext, mSdkSandboxManagerLocal, packageManagerLocal, mTestDir);
    }

    @After
    public void teardown() throws Exception {
        FileUtils.deleteContents(new File(mTestDir));
    }

    @After
    public void tearDown() throws Exception {
        FileUtils.deleteContents(new File(mTestDir));
    }

    @Test
    public void test_GetSdkDataPackageDirectory() throws Exception {
        assertThat(mSdkSandboxStorageManager.getSdkDataPackageDirectory(null, 0, "foo", true))
                .isEqualTo(mTestDir + "/data/misc_ce/0/sdksandbox/foo");
        // Build DE path
        assertThat(mSdkSandboxStorageManager.getSdkDataPackageDirectory(null, 0, "foo", false))
                .isEqualTo(mTestDir + "/data/misc_de/0/sdksandbox/foo");
        // Build with different package name
        assertThat(mSdkSandboxStorageManager.getSdkDataPackageDirectory(null, 0, "bar", true))
                .isEqualTo(mTestDir + "/data/misc_ce/0/sdksandbox/bar");
        // Build with different user
        assertThat(mSdkSandboxStorageManager.getSdkDataPackageDirectory(null, 10, "foo", true))
                .isEqualTo(mTestDir + "/data/misc_ce/10/sdksandbox/foo");
        // Build with different volume
        assertThat(mSdkSandboxStorageManager.getSdkDataPackageDirectory("hello", 0, "foo", true))
                .isEqualTo(mTestDir + "/mnt/expand/hello/misc_ce/0/sdksandbox/foo");
    }

    @Test
    public void test_SdkDataDirInfo_GetterApis() throws Exception {
        final SdkDataDirInfo sdkInfo = new SdkDataDirInfo("foo", "bar");
        assertThat(sdkInfo.getCeDataDir()).isEqualTo("foo");
        assertThat(sdkInfo.getDeDataDir()).isEqualTo("bar");
    }

    @Test
    public void test_GetSdkDataDirInfo_NonExistingStorage() throws Exception {
        // Call getSdkDataDirInfo on SdkStorageManager
        final CallingInfo callingInfo = new CallingInfo(CLIENT_UID, CLIENT_PKG_NAME);
        final SdkDataDirInfo sdkInfo =
                mSdkSandboxStorageManager.getSdkDataDirInfo(callingInfo, SDK_NAME);

        assertThat(sdkInfo.getCeDataDir()).isNull();
        assertThat(sdkInfo.getDeDataDir()).isNull();
    }

    @Test
    public void test_GetSdkDataDirInfo_StorageExists() throws Exception {
        createSdkStorageForTest(
                /*volumeUuid=*/ null,
                /*userId=*/ 0,
                CLIENT_PKG_NAME,
                Arrays.asList(SDK_NAME),
                Collections.emptyList());

        final ApplicationInfo info = new ApplicationInfo();
        info.storageUuid = UUID.fromString("41217664-9172-527a-b3d5-edabb50a7d69");
        Mockito.doReturn(info)
                .when(mPmMock)
                .getApplicationInfo(Mockito.any(String.class), Mockito.anyInt());

        // Call getSdkDataDirInfo on SdkStorageManager
        final CallingInfo callingInfo = new CallingInfo(CLIENT_UID, CLIENT_PKG_NAME);
        final SdkDataDirInfo sdkInfo =
                mSdkSandboxStorageManager.getSdkDataDirInfo(callingInfo, SDK_NAME);

        assertThat(sdkInfo.getCeDataDir())
                .isEqualTo(mTestDir + "/data/misc_ce/0/sdksandbox/client/sdk@sdk");
        assertThat(sdkInfo.getDeDataDir())
                .isEqualTo(mTestDir + "/data/misc_de/0/sdksandbox/client/sdk@sdk");
    }

    @Test
    public void test_GetMountedVolumes_NewVolumeExists() throws Exception {
        createSdkStorageForTest(
                /*volumeUuid=*/ null,
                /*userId=*/ 0,
                CLIENT_PKG_NAME,
                Arrays.asList(SDK_NAME),
                Collections.emptyList());
        createSdkStorageForTest(
                "newVolume",
                /*userId=*/ 0,
                CLIENT_PKG_NAME,
                Arrays.asList(SDK_NAME),
                Collections.emptyList());

        final List<String> mountedVolumes = mSdkSandboxStorageManager.getMountedVolumes();

        assertThat(mountedVolumes).containsExactly(null, "newVolume");
    }

    @Test
    public void test_GetMountedVolumes_NewVolumeDoesNotExist() throws Exception {
        createSdkStorageForTest(
                /*volumeUuid=*/ null,
                /*userId=*/ 0,
                CLIENT_PKG_NAME,
                Arrays.asList(SDK_NAME),
                Collections.emptyList());

        final List<String> mountedVolumes = mSdkSandboxStorageManager.getMountedVolumes();

        assertThat(mountedVolumes).containsExactly((String) null);
    }

    @Test
    public void test_onUserUnlocking_Instrumentation_NoSdk_PackageDirNotRemoved() throws Exception {
        createSdkStorageForTest(
                /*volumeUuid=*/ null,
                /*userId=*/ 0,
                CLIENT_PKG_NAME,
                Collections.emptyList(),
                Collections.emptyList());

        // Set instrumentation started, so that isInstrumentationRunning will return true
        mSdkSandboxManagerLocal.notifyInstrumentationStarted(CLIENT_PKG_NAME, CLIENT_UID);

        mSdkSandboxStorageManager.onUserUnlocking(0);

        final Path ceDataPackageDirectory =
                Paths.get(
                        mSdkSandboxStorageManager.getSdkDataPackageDirectory(
                                null, 0, CLIENT_PKG_NAME, true));

        final Path deDataPackageDirectory =
                Paths.get(
                        mSdkSandboxStorageManager.getSdkDataPackageDirectory(
                                null, 0, CLIENT_PKG_NAME, false));

        assertThat(Files.exists(ceDataPackageDirectory)).isTrue();
        assertThat(Files.exists(deDataPackageDirectory)).isTrue();
    }

    @Test
    public void test_onUserUnlocking_NoInstrumentation_NoSdk_PackageDirRemoved() throws Exception {
        createSdkStorageForTest(
                /*volumeUuid=*/ null,
                /*userId=*/ 0,
                CLIENT_PKG_NAME,
                Collections.emptyList(),
                Collections.emptyList());

        mSdkSandboxStorageManager.onUserUnlocking(0);

        final Path ceDataPackageDirectory =
                Paths.get(
                        mSdkSandboxStorageManager.getSdkDataPackageDirectory(
                                null, 0, CLIENT_PKG_NAME, true));

        final Path deDataPackageDirectory =
                Paths.get(
                        mSdkSandboxStorageManager.getSdkDataPackageDirectory(
                                null, 0, CLIENT_PKG_NAME, false));

        assertThat(Files.exists(ceDataPackageDirectory)).isFalse();
        assertThat(Files.exists(deDataPackageDirectory)).isFalse();
    }

    @Test
    public void test_SdkSubDirectories_NonExistingParentPath() throws Exception {
        createSdkStorageForTest(
                /*volumeUuid=*/ null,
                /*userId=*/ 0,
                CLIENT_PKG_NAME,
                Arrays.asList(SDK_NAME),
                Collections.emptyList());

        final SubDirectories subDirs = new SubDirectories("/does/not/exist");

        assertThat(subDirs.getSdkSubDir("does.not.exist")).isNull();
        assertThat(subDirs.getSdkSubDir(SDK_NAME)).isNull();
    }

    @Test
    public void test_SdkSubDirectories_GetSdkSubDir() throws Exception {
        createSdkStorageForTest(
                /*volumeUuid=*/ null,
                /*userId=*/ 0,
                CLIENT_PKG_NAME,
                Arrays.asList(SDK_NAME),
                Collections.emptyList());

        final String packageDir =
                mSdkSandboxStorageManager.getSdkDataPackageDirectory(
                        /*volumeUuid=*/ null, /*userId=*/ 0, CLIENT_PKG_NAME, /*isCeData=*/ true);

        final SubDirectories subDirs = new SubDirectories(packageDir);

        assertThat(subDirs.getSdkSubDir("does.not.exist")).isNull();
        assertThat(subDirs.getSdkSubDir("does.not.exist", /*fullPath=*/ true)).isNull();

        final String expectedSubDirName = SDK_NAME + "@" + SDK_NAME;
        assertThat(subDirs.getSdkSubDir(SDK_NAME)).isEqualTo(expectedSubDirName);
        final String expectedFullPath = Paths.get(packageDir, expectedSubDirName).toString();
        assertThat(subDirs.getSdkSubDir(SDK_NAME, /*fullPath=*/ true)).isEqualTo(expectedFullPath);
    }

    @Test
    public void test_SdkSubDirectories_IsValid() throws Exception {
        createSdkStorageForTest(
                /*volumeUuid=*/ null,
                /*userId=*/ 0,
                CLIENT_PKG_NAME,
                Arrays.asList(SDK_NAME, SDK2_NAME),
                Arrays.asList("shared"));

        final String packageDir =
                mSdkSandboxStorageManager.getSdkDataPackageDirectory(
                        /*volumeUuid=*/ null, /*userId=*/ 0, CLIENT_PKG_NAME, /*isCeData=*/ true);

        final SubDirectories subDirs = new SubDirectories(packageDir);

        assertThat(subDirs.isValid(Collections.emptySet())).isFalse();
        assertThat(subDirs.isValid(Set.of(SDK_NAME))).isFalse();
        assertThat(subDirs.isValid(Set.of(SDK_NAME, SDK2_NAME))).isTrue();
    }

    @Test
    public void test_SdkSubDirectories_IsValid_MissingNonSdkStorage() throws Exception {
        // Avoid creating "shared" storage
        createSdkStorageForTest(
                /*volumeUuid=*/ null,
                /*userId=*/ 0,
                CLIENT_PKG_NAME,
                Arrays.asList(SDK_NAME),
                Collections.emptyList());

        final String packageDir =
                mSdkSandboxStorageManager.getSdkDataPackageDirectory(
                        /*volumeUuid=*/ null, /*userId=*/ 0, CLIENT_PKG_NAME, /*isCeData=*/ true);

        final SubDirectories subDirs = new SubDirectories(packageDir);

        assertThat(subDirs.isValid(Set.of(SDK_NAME))).isFalse();
    }

    @Test
    public void test_SdkSubDirectories_IsValid_HasUnknownSubDir() throws Exception {
        createSdkStorageForTest(
                /*volumeUuid=*/ null,
                /*userId=*/ 0,
                CLIENT_PKG_NAME,
                Arrays.asList(SDK_NAME),
                Arrays.asList("shared"));

        // Create a random subdir not following our format
        final String packageDir =
                mSdkSandboxStorageManager.getSdkDataPackageDirectory(
                        /*volumeUuid=*/ null, /*userId=*/ 0, CLIENT_PKG_NAME, /*isCeData=*/ true);
        Path invalidSubDir = Paths.get(packageDir, "invalid");
        Files.createDirectories(invalidSubDir);

        final SubDirectories subDirs = new SubDirectories(packageDir);

        assertThat(subDirs.isValid(Set.of(SDK_NAME))).isFalse();
    }

    /**
     * A helper method for create sdk storage for test purpose.
     *
     * <p>It creates <volume>/misc_ce/<userId>/sdksandbox/<packageName>/<name>[@,#]<name>
     *
     * <p>We are reusing the name of directory as random suffix for simplicity.
     */
    private void createSdkStorageForTest(
            String volumeUuid,
            int userId,
            String packageName,
            List<String> sdkNames,
            List<String> nonSdkDirectories)
            throws Exception {
        for (int i = 0; i < 2; i++) {
            final boolean isCeData = (i == 0);
            final String packageDir =
                    mSdkSandboxStorageManager.getSdkDataPackageDirectory(
                            volumeUuid, userId, packageName, isCeData);
            final Path packagePath = Paths.get(packageDir);
            Files.createDirectories(packagePath);
            for (String sdkName : sdkNames) {
                final Path perSdkPath = Paths.get(packageDir, sdkName + "@" + sdkName);
                Files.createDirectories(perSdkPath);
            }

            for (String dir : nonSdkDirectories) {
                final Path subDir =
                        Paths.get(packageDir, dir.equals("shared") ? dir : dir + "#" + dir);
                Files.createDirectories(subDir);
            }
        }
    }

    private static class FakeSdkSandboxManagerLocal implements SdkSandboxManagerLocal {

        private boolean mInstrumentationRunning = false;

        @Override
        public void enforceAllowedToSendBroadcast(@NonNull Intent intent) {}

        @Override
        public void enforceAllowedToStartActivity(@NonNull Intent intent) {}

        @Override
        public void enforceAllowedToStartOrBindService(@NonNull Intent intent) {}

        @NonNull
        @Override
        public String getSdkSandboxProcessNameForInstrumentation(
                @NonNull ApplicationInfo clientAppInfo) {
            return clientAppInfo.processName + "_sdk_sandbox_instr";
        }

        @Override
        public void notifyInstrumentationStarted(
                @NonNull String clientAppPackageName, int clientAppUid) {
            mInstrumentationRunning = true;
        }

        @Override
        public void notifyInstrumentationFinished(
                @NonNull String clientAppPackageName, int clientAppUid) {
            mInstrumentationRunning = false;
        }

        @Override
        public boolean isInstrumentationRunning(
                @NonNull String clientAppPackageName, int clientAppUid) {
            return mInstrumentationRunning;
        }
    }
}

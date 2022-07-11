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

import androidx.test.platform.app.InstrumentationRegistry;

import com.android.server.sdksandbox.SdkSandboxStorageManager.SdkDataDirInfo;

import org.junit.Before;
import org.junit.Test;

import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.util.Arrays;
import java.util.List;

/** Unit tests for {@link SdkSandboxStorageManager}. */
public class SdkSandboxStorageManagerUnitTest {

    /** Test directory on Android where all storage will be created */
    private static final int CLIENT_UID = 11000;

    private static final String CLIENT_PKG_NAME = "client";
    private static final String SDK_NAME = "sdk";

    // Use the test app's private storage as mount point for sdk storage testing
    private SdkSandboxStorageManager mSdkSandboxStorageManager;
    // Separate location where all of the sdk storage directories will be created for testing
    private String mTestDir;

    @Before
    public void setup() throws Exception {
        Context context = InstrumentationRegistry.getInstrumentation().getContext();
        mTestDir = context.getDataDir().getPath();
        mSdkSandboxStorageManager = new SdkSandboxStorageManager(context, mTestDir);
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
                /*volumeUuid=*/ null, /*userId=*/ 0, CLIENT_PKG_NAME, Arrays.asList(SDK_NAME));

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
    public void test_getMountedVolumes_NewVolumeExists() throws Exception {
        createSdkStorageForTest(
                /*volumeUuid=*/ null, /*userId=*/ 0, CLIENT_PKG_NAME, Arrays.asList(SDK_NAME));
        createSdkStorageForTest(
                "newVolume", /*userId=*/ 0, CLIENT_PKG_NAME, Arrays.asList(SDK_NAME));

        final List<String> mountedVolumes = mSdkSandboxStorageManager.getMountedVolumes();

        assertThat(mountedVolumes).containsExactly(null, "newVolume");
    }

    @Test
    public void test_getMountedVolumes_NewVolumeDoesNotExist() throws Exception {
        createSdkStorageForTest(
                /*volumeUuid=*/ null, /*userId=*/ 0, CLIENT_PKG_NAME, Arrays.asList(SDK_NAME));

        final List<String> mountedVolumes = mSdkSandboxStorageManager.getMountedVolumes();

        assertThat(mountedVolumes).containsExactly((String) null);
    }

    /**
     * A helper method for create sdk storage for test purpose.
     *
     * <p>It creates <volume>/misc_ce/<userId>/sdksandbox/<packageName>/<sdkName>@<sdkName>
     *
     * <p>We are using the sdkName as random suffix for simplicity.
     */
    private void createSdkStorageForTest(
            String volumeUuid, int userId, String packageName, List<String> sdkNames)
            throws Exception {
        for (int i = 0; i < 2; i++) {
            final boolean isCeData = (i == 0);
            final String packageDir =
                    mSdkSandboxStorageManager.getSdkDataPackageDirectory(
                            volumeUuid, userId, packageName, isCeData);
            for (String sdkName : sdkNames) {
                final Path perSdkPath = Paths.get(packageDir, sdkName + "@" + sdkName);
                Files.createDirectories(perSdkPath);
            }
        }
    }
}

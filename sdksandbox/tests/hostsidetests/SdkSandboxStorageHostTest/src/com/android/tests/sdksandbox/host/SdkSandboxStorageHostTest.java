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

package com.android.tests.sdksandbox.host;

import static android.appsecurity.cts.Utils.waitForBootCompleted;

import static com.google.common.truth.Truth.assertThat;
import static com.google.common.truth.Truth.assertWithMessage;

import static org.hamcrest.CoreMatchers.equalTo;
import static org.junit.Assert.fail;
import static org.junit.Assume.assumeThat;
import static org.junit.Assume.assumeTrue;

import android.cts.install.lib.host.InstallUtilsHost;
import android.platform.test.annotations.LargeTest;

import com.android.tradefed.log.LogUtil.CLog;
import com.android.tradefed.testtype.DeviceJUnit4ClassRunner;
import com.android.tradefed.testtype.junit4.BaseHostJUnit4Test;

import org.junit.After;
import org.junit.AssumptionViolatedException;
import org.junit.Before;
import org.junit.Test;
import org.junit.runner.RunWith;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collections;
import java.util.List;

import javax.annotation.Nullable;

@RunWith(DeviceJUnit4ClassRunner.class)
public final class SdkSandboxStorageHostTest extends BaseHostJUnit4Test {

    private int mOriginalUserId;
    private int mSecondaryUserId = -1;
    private boolean mWasRoot;

    private static final String CODE_PROVIDER_APK = "StorageTestCodeProvider.apk";
    private static final String TEST_APP_STORAGE_PACKAGE = "com.android.tests.sdksandbox";
    private static final String TEST_APP_STORAGE_APK = "SdkSandboxStorageTestApp.apk";
    private static final String TEST_APP_STORAGE_V2_NO_SDK =
            "SdkSandboxStorageTestAppV2_DoesNotConsumeSdk.apk";
    private static final String SDK_NAME = "com.android.tests.codeprovider.storagetest";

    private static final long SWITCH_USER_COMPLETED_NUMBER_OF_POLLS = 60;
    private static final long SWITCH_USER_COMPLETED_POLL_INTERVAL_IN_MILLIS = 1000;
    private static final long WAIT_FOR_RECONCILE_MS = 5000;

    private final InstallUtilsHost mHostUtils = new InstallUtilsHost(this);
    private final AdoptableStorageUtils mAdoptableUtils = new AdoptableStorageUtils(this);
    private final DeviceLockUtils mDeviceLockUtils = new DeviceLockUtils(this);

    /**
     * Runs the given phase of a test by calling into the device.
     * Throws an exception if the test phase fails.
     * <p>
     * For example, <code>runPhase("testExample");</code>
     */
    private void runPhase(String phase) throws Exception {
        assertThat(runDeviceTests(TEST_APP_STORAGE_PACKAGE,
                "com.android.tests.sdksandbox.SdkSandboxStorageTestApp",
                phase)).isTrue();
    }

    @Before
    public void setUp() throws Exception {
        // TODO(b/209061624): See if we can remove root privilege when instrumentation support for
        // sdk sandbox is added.
        mWasRoot = getDevice().isAdbRoot();
        getDevice().enableAdbRoot();
        uninstallPackage(TEST_APP_STORAGE_PACKAGE);
        mOriginalUserId = getDevice().getCurrentUser();
    }

    @After
    public void tearDown() throws Exception {
        removeSecondaryUserIfNecessary();
        uninstallPackage(TEST_APP_STORAGE_PACKAGE);
        if (!mWasRoot) {
            getDevice().disableAdbRoot();
        }
    }

    @Test
    public void testSelinuxLabel() throws Exception {
        installPackage(TEST_APP_STORAGE_APK);

        assertSelinuxLabel("/data/misc_ce/0/sdksandbox", "system_data_file");
        assertSelinuxLabel("/data/misc_de/0/sdksandbox", "system_data_file");
        // Check label of /data/misc_{ce,de}/0/sdksandbox/<app-name>/shared
        assertSelinuxLabel(getSdkDataSharedPath(0, TEST_APP_STORAGE_PACKAGE, true),
                "sdk_sandbox_data_file");
        assertSelinuxLabel(getSdkDataSharedPath(0, TEST_APP_STORAGE_PACKAGE, false),
                "sdk_sandbox_data_file");
        // Check label of /data/misc_{ce,de}/0/sdksandbox/<app-name>/<sdk-package>
        assertSelinuxLabel(getSdkDataPerSdkPath(0, TEST_APP_STORAGE_PACKAGE, SDK_NAME, true),
                "sdk_sandbox_data_file");
        assertSelinuxLabel(getSdkDataPerSdkPath(0, TEST_APP_STORAGE_PACKAGE, SDK_NAME, false),
                "sdk_sandbox_data_file");
    }

    /**
     * Verify that {@code /data/misc_{ce,de}/<user-id>/sdksandbox} is created when
     * {@code <user-id>} is created.
     */
    @Test
    public void testSdkDataRootDirectory_IsCreatedOnUserCreate() throws Exception {
        {
            // Verify root directory exists for primary user
            final String cePath = getSdkDataRootPath(0, true);
            final String dePath = getSdkDataRootPath(0, false);
            assertThat(getDevice().isDirectory(dePath)).isTrue();
            assertThat(getDevice().isDirectory(cePath)).isTrue();
        }

        {
            // Verify root directory is created for new user
            mSecondaryUserId = createAndStartSecondaryUser();
            final String cePath = getSdkDataRootPath(mSecondaryUserId, true);
            final String dePath = getSdkDataRootPath(mSecondaryUserId, false);
            assertThat(getDevice().isDirectory(dePath)).isTrue();
            assertThat(getDevice().isDirectory(cePath)).isTrue();
        }
    }

    @Test
    public void testSdkDataPackageDirectory_IsCreatedOnInstall() throws Exception {
        // Directory should not exist before install
        final String cePath = getSdkDataPackagePath(0, TEST_APP_STORAGE_PACKAGE, true);
        final String dePath = getSdkDataPackagePath(0, TEST_APP_STORAGE_PACKAGE, false);
        assertThat(getDevice().isDirectory(cePath)).isFalse();
        assertThat(getDevice().isDirectory(dePath)).isFalse();

        // Install the app
        installPackage(TEST_APP_STORAGE_APK);

        // Verify directory is created
        assertThat(getDevice().isDirectory(cePath)).isTrue();
        assertThat(getDevice().isDirectory(dePath)).isTrue();
    }

    @Test
    public void testSdkDataPackageDirectory_IsNotCreatedWithoutSdkConsumption()
            throws Exception {
        // Install the an app that does not consume sdk
        installPackage(TEST_APP_STORAGE_V2_NO_SDK);

        // Verify directories are not created
        final String cePath = getSdkDataPackagePath(0, TEST_APP_STORAGE_PACKAGE, true);
        final String dePath = getSdkDataPackagePath(0, TEST_APP_STORAGE_PACKAGE, false);
        assertThat(getDevice().isDirectory(cePath)).isFalse();
        assertThat(getDevice().isDirectory(dePath)).isFalse();
    }

    @Test
    public void testSdkDataPackageDirectory_IsDestroyedOnUninstall() throws Exception {
        // Install the app
        installPackage(TEST_APP_STORAGE_APK);

        //Uninstall the app
        uninstallPackage(TEST_APP_STORAGE_PACKAGE);

        // Directory should not exist after uninstall
        final String cePath = getSdkDataPackagePath(0, TEST_APP_STORAGE_PACKAGE, true);
        final String dePath = getSdkDataPackagePath(0, TEST_APP_STORAGE_PACKAGE, false);
        // Verify directory is destoyed
        assertThat(getDevice().isDirectory(cePath)).isFalse();
        assertThat(getDevice().isDirectory(dePath)).isFalse();
    }

    @Test
    @LargeTest
    public void testSdkDataPackageDirectory_IsDestroyedOnUninstall_DeviceLocked()
            throws Exception {
        assumeThat("Device is NOT encrypted with file-based encryption.",
                getDevice().getProperty("ro.crypto.type"), equalTo("file"));
        assumeTrue("Screen lock is not supported so skip direct boot test",
                hasDeviceFeature("android.software.secure_lock_screen"));

        installPackage(TEST_APP_STORAGE_APK);

        // Verify sdk ce directory contains TEST_APP_STORAGE_PACKAGE
        final String ceSandboxPath = getSdkDataRootPath(0, /*isCeData=*/true);
        String[] children = getDevice().getChildren(ceSandboxPath);
        assertThat(children).isNotEmpty();
        final int numberOfChildren = children.length;
        assertThat(children).asList().contains(TEST_APP_STORAGE_PACKAGE);

        try {
            mDeviceLockUtils.rebootToLockedDevice();

            // Verify sdk ce package directory is encrypted, so longer contains the test package
            children = getDevice().getChildren(ceSandboxPath);
            assertThat(children).hasLength(numberOfChildren);
            assertThat(children).asList().doesNotContain(TEST_APP_STORAGE_PACKAGE);

            // Uninstall while device is locked
            uninstallPackage(TEST_APP_STORAGE_PACKAGE);

            // Verify ce sdk data did not change while device is locked
            children = getDevice().getChildren(ceSandboxPath);
            assertThat(children).hasLength(numberOfChildren);

            // Meanwhile, de storage area should already be deleted
            final String dePath = getSdkDataPackagePath(0, TEST_APP_STORAGE_PACKAGE, false);
            assertThat(getDevice().isDirectory(dePath)).isFalse();
        } finally {
            mDeviceLockUtils.clearScreenLock();
        }

        // Once device is unlocked, the uninstallation during locked state should take effect.
        // Allow some time for background task to run.
        Thread.sleep(10000);

        final String cePath = getSdkDataPackagePath(0, TEST_APP_STORAGE_PACKAGE, true);
        assertDirectoryDoesNotExist(cePath);
        // Verify number of children under root directory is one less than before
        children = getDevice().getChildren(ceSandboxPath);
        assertThat(children).hasLength(numberOfChildren - 1);
        assertThat(children).asList().doesNotContain(TEST_APP_STORAGE_PACKAGE);
    }

    @Test
    @LargeTest
    public void testSdkDataPackageDirectory_IsReconciled_InvalidAndMissingPackage()
            throws Exception {

        installPackage(TEST_APP_STORAGE_APK);

        // Rename the sdk data directory to some non-existing package name
        final String cePackageDir = getSdkDataPackagePath(0, TEST_APP_STORAGE_PACKAGE, true);
        final String ceInvalidDir = getSdkDataPackagePath(0, "com.invalid.foo", true);
        getDevice().executeShellCommand(String.format("mv %s %s", cePackageDir, ceInvalidDir));
        assertDirectoryExists(ceInvalidDir);

        final String dePackageDir = getSdkDataPackagePath(0, TEST_APP_STORAGE_PACKAGE, false);
        final String deInvalidDir = getSdkDataPackagePath(0, "com.invalid.foo", false);
        getDevice().executeShellCommand(String.format("mv %s %s", dePackageDir, deInvalidDir));
        assertDirectoryExists(deInvalidDir);

        // Reboot since reconcilation happens on user unlock only
        getDevice().reboot();
        Thread.sleep(WAIT_FOR_RECONCILE_MS);

        // Verify invalid directory doesn't exist
        assertDirectoryDoesNotExist(ceInvalidDir);
        assertDirectoryDoesNotExist(deInvalidDir);
        assertDirectoryExists(cePackageDir);
        assertDirectoryExists(dePackageDir);
    }

    @Test
    @LargeTest
    public void testSdkDataPackageDirectory_IsReconciled_MissingSubDirs() throws Exception {

        installPackage(TEST_APP_STORAGE_APK);

        // Rename the sdk data directory to some non-existing package name
        final String cePackageDir = getSdkDataPackagePath(0, TEST_APP_STORAGE_PACKAGE, true);
        // Delete the shared directory
        final String sharedDir = cePackageDir + "/shared";
        getDevice().deleteFile(sharedDir);
        assertDirectoryDoesNotExist(sharedDir);

        // Reboot since reconcilation happens on user unlock only
        getDevice().reboot();
        Thread.sleep(WAIT_FOR_RECONCILE_MS);

        // Verify shared dir exists
        assertDirectoryExists(sharedDir);
    }

    @Test
    @LargeTest
    public void testSdkDataPackageDirectory_IsReconciled_DeleteKeepData() throws Exception {

        installPackage(TEST_APP_STORAGE_APK);

        // Uninstall while keeping the data
        getDevice().executeShellCommand("pm uninstall -k --user 0 " + TEST_APP_STORAGE_PACKAGE);

        // Rename the sdk data directory to some non-existing package name
        final String cePackageDir = getSdkDataPackagePath(0, TEST_APP_STORAGE_PACKAGE, true);
        final String dePackageDir = getSdkDataPackagePath(0, TEST_APP_STORAGE_PACKAGE, false);
        assertDirectoryExists(cePackageDir);
        assertDirectoryExists(dePackageDir);

        // Reboot since reconcilation happens on user unlock only
        getDevice().reboot();
        Thread.sleep(WAIT_FOR_RECONCILE_MS);

        // Verify sdk data are not cleaned up during reconcilation
        assertDirectoryExists(cePackageDir);
        assertDirectoryExists(dePackageDir);
    }

    @Test
    public void testSdkDataPackageDirectory_IsClearedOnClearAppData() throws Exception {
        // Install the app
        installPackage(TEST_APP_STORAGE_APK);
        {
            // Verify directory is not clear
            final String ceDataSharedPath =
                    getSdkDataSharedPath(0, TEST_APP_STORAGE_PACKAGE, true);
            final String[] ceChildren = getDevice().getChildren(ceDataSharedPath);
            {
                final String fileToDelete = ceDataSharedPath + "/deleteme.txt";
                getDevice().executeShellCommand("echo something to delete > " + fileToDelete);
                assertThat(getDevice().doesFileExist(fileToDelete)).isTrue();
            }
            assertThat(ceChildren.length).isNotEqualTo(0);
            final String deDataSharedPath =
                    getSdkDataSharedPath(0, TEST_APP_STORAGE_PACKAGE, false);
            final String[] deChildren = getDevice().getChildren(deDataSharedPath);
            {
                final String fileToDelete = deDataSharedPath + "/deleteme.txt";
                getDevice().executeShellCommand("echo something to delete > " + fileToDelete);
                assertThat(getDevice().doesFileExist(fileToDelete)).isTrue();
            }
            assertThat(deChildren.length).isNotEqualTo(0);
        }

        // Clear the app data
        getDevice().executeShellCommand("pm clear " + TEST_APP_STORAGE_PACKAGE);
        {
            // Verify directory is cleared
            final String ceDataSharedPath =
                    getSdkDataSharedPath(0, TEST_APP_STORAGE_PACKAGE, true);
            final String[] ceChildren = getDevice().getChildren(ceDataSharedPath);
            assertThat(ceChildren.length).isEqualTo(0);
            final String deDataSharedPath =
                    getSdkDataSharedPath(0, TEST_APP_STORAGE_PACKAGE, false);
            final String[] deChildren = getDevice().getChildren(deDataSharedPath);
            assertThat(deChildren.length).isEqualTo(0);
        }
    }

    // TODO(b/221946754): Need to write tests for clearing cache and clearing code cache
    @Test
    public void testSdkDataPackageDirectory_IsDestroyedOnUserDeletion() throws Exception {
        // Create new user
        mSecondaryUserId = createAndStartSecondaryUser();

        // Install the app
        installPackage(TEST_APP_STORAGE_APK);

        // delete the new user
        removeSecondaryUserIfNecessary();

        // Sdk Sandbox root directories should not exist as the user was removed
        final String ceSdkSandboxDataRootPath = getSdkDataRootPath(mSecondaryUserId, true);
        final String deSdkSandboxDataRootPath = getSdkDataRootPath(mSecondaryUserId, false);
        assertThat(getDevice().isDirectory(ceSdkSandboxDataRootPath)).isFalse();
        assertThat(getDevice().isDirectory(deSdkSandboxDataRootPath)).isFalse();
    }

    @Test
    public void testSdkDataPackageDirectory_IsUserSpecific() throws Exception {
        // Install first before creating the user
        installPackage(TEST_APP_STORAGE_APK, "--user all");

        mSecondaryUserId = createAndStartSecondaryUser();

        // Data directories should not exist as the package is not installed on new user
        final String ceAppPath = getAppDataPath(mSecondaryUserId, TEST_APP_STORAGE_PACKAGE, true);
        final String deAppPath = getAppDataPath(mSecondaryUserId, TEST_APP_STORAGE_PACKAGE, false);
        final String cePath = getSdkDataPackagePath(mSecondaryUserId,
                TEST_APP_STORAGE_PACKAGE, true);
        final String dePath = getSdkDataPackagePath(mSecondaryUserId,
                TEST_APP_STORAGE_PACKAGE, false);

        assertThat(getDevice().isDirectory(ceAppPath)).isFalse();
        assertThat(getDevice().isDirectory(deAppPath)).isFalse();
        assertThat(getDevice().isDirectory(cePath)).isFalse();
        assertThat(getDevice().isDirectory(dePath)).isFalse();

        // Install the app on new user
        installPackage(TEST_APP_STORAGE_APK);

        assertThat(getDevice().isDirectory(ceAppPath)).isTrue();
        assertThat(getDevice().isDirectory(deAppPath)).isTrue();
        assertThat(getDevice().isDirectory(cePath)).isTrue();
        assertThat(getDevice().isDirectory(dePath)).isTrue();
    }

    @Test
    public void testSdkDataPackageDirectory_SharedStorageIsUsable() throws Exception {
        installPackage(TEST_APP_STORAGE_APK);

        // Verify that shared storage exist
        final String sharedCePath = getSdkDataSharedPath(0, TEST_APP_STORAGE_PACKAGE, true);
        assertThat(getDevice().isDirectory(sharedCePath)).isTrue();

        // Write a file in the shared storage that code needs to read and write it back
        // in another file
        String fileToRead = sharedCePath + "/readme.txt";
        getDevice().executeShellCommand("echo something to read > " + fileToRead);
        assertThat(getDevice().doesFileExist(fileToRead)).isTrue();

        runPhase("testSdkDataPackageDirectory_SharedStorageIsUsable");

        // Assert that code was able to create file and directories
        assertThat(getDevice().isDirectory(sharedCePath + "/dir")).isTrue();
        assertThat(getDevice().doesFileExist(sharedCePath + "/dir/file")).isTrue();
        String content = getDevice().executeShellCommand("cat " + sharedCePath + "/dir/file");
        assertThat(content).isEqualTo("something to read");
    }

    @Test
    public void testSdkDataPackageDirectory_CreateMissingSdkSubDirsWhenPackageDirEmpty()
            throws Exception {
        installPackage(TEST_APP_STORAGE_APK);
        final String cePackagePath =
                getSdkDataPackagePath(0, TEST_APP_STORAGE_PACKAGE, true);
        final String dePackagePath =
                getSdkDataPackagePath(0, TEST_APP_STORAGE_PACKAGE, false);
        final List<String> ceSdkDirsBeforeLoadingSdksList = getSubDirs(cePackagePath,
                /*includeRandomSuffix=*/true);
        final List<String> deSdkDirsBeforeLoadingSdksList = getSubDirs(dePackagePath,
                /*includeRandomSuffix=*/true);
        // Delete the sdk sub directories
        for (String child : ceSdkDirsBeforeLoadingSdksList) {
            getDevice().deleteFile(cePackagePath + "/" + child);
        }
        for (String child : deSdkDirsBeforeLoadingSdksList) {
            getDevice().deleteFile(dePackagePath + "/" + child);
        }
        assertThat(getDevice().getChildren(cePackagePath)).asList().isEmpty();
        runPhase("testSdkDataPackageDirectory_CreateMissingSdkDirs");

        final List<String> ceSdkDirsAfterLoadingSdksList = getSubDirs(cePackagePath,
                /*includeRandomSuffix=*/false);
        final List<String> deSdkDirsAfterLoadingSdksList = getSubDirs(dePackagePath,
                /*includeRandomSuffix=*/false);
        assertThat(ceSdkDirsAfterLoadingSdksList).containsExactly("shared", SDK_NAME);
        assertThat(deSdkDirsAfterLoadingSdksList).containsExactly("shared", SDK_NAME);
    }

    @Test
    public void testSdkDataPackageDirectory_CreateMissingSdkSubDirsWhenPackageDirMissing()
            throws Exception {
        installPackage(TEST_APP_STORAGE_APK);
        final String cePackagePath =
                getSdkDataPackagePath(0, TEST_APP_STORAGE_PACKAGE, true);
        final String dePackagePath =
                getSdkDataPackagePath(0, TEST_APP_STORAGE_PACKAGE, false);
        // Delete the package paths
        getDevice().deleteFile(cePackagePath);
        getDevice().deleteFile(dePackagePath);
        runPhase("testSdkDataPackageDirectory_CreateMissingSdkDirs");

        final List<String> ceSdkDirsAfterLoadingSdksList = getSubDirs(cePackagePath,
                /*includeRandomSuffix=*/false);
        final List<String> deSdkDirsAfterLoadingSdksList = getSubDirs(dePackagePath,
                /*includeRandomSuffix=*/false);
        assertThat(ceSdkDirsAfterLoadingSdksList).containsExactly("shared", SDK_NAME);
        assertThat(deSdkDirsAfterLoadingSdksList).containsExactly("shared", SDK_NAME);
    }

    @Test
    public void testSdkDataPackageDirectory_CreateMissingSdkSubDirsWhenPackageDirIsNotEmpty()
            throws Exception {
        installPackage(TEST_APP_STORAGE_APK);
        final String cePackagePath =
                getSdkDataPackagePath(0, TEST_APP_STORAGE_PACKAGE, true);
        final String dePackagePath =
                getSdkDataPackagePath(0, TEST_APP_STORAGE_PACKAGE, false);
        final List<String> ceSdkDirsBeforeLoadingSdksList = getSubDirs(cePackagePath,
                /*includeRandomSuffix=*/true);
        final List<String> deSdkDirsBeforeLoadingSdksList = getSubDirs(dePackagePath,
                /*includeRandomSuffix=*/true);
        // Delete the sdk sub directories
        getDevice().deleteFile(cePackagePath + "/" + ceSdkDirsBeforeLoadingSdksList.get(0));
        getDevice().deleteFile(dePackagePath + "/" + deSdkDirsBeforeLoadingSdksList.get(0));
        runPhase("testSdkDataPackageDirectory_CreateMissingSdkDirs");

        final List<String> ceSdkDirsAfterLoadingSdksList = getSubDirs(cePackagePath,
                /*includeRandomSuffix=*/false);
        final List<String> deSdkDirsAfterLoadingSdksList = getSubDirs(dePackagePath,
                /*includeRandomSuffix=*/false);
        assertThat(ceSdkDirsAfterLoadingSdksList).containsExactly("shared", SDK_NAME);
        assertThat(deSdkDirsAfterLoadingSdksList).containsExactly("shared", SDK_NAME);
    }

    @Test
    public void testSdkDataPackageDirectory_ReuseExistingRandomSuffixInReconcile()
            throws Exception {
        installPackage(TEST_APP_STORAGE_APK);
        final String cePackagePath =
                getSdkDataPackagePath(0, TEST_APP_STORAGE_PACKAGE, true);
        final String dePackagePath =
                getSdkDataPackagePath(0, TEST_APP_STORAGE_PACKAGE, false);
        final List<String> ceSdkDirsBeforeLoadingSdksList = getSubDirs(cePackagePath,
                /*includeRandomSuffix=*/true);
        final List<String> deSdkDirsBeforeLoadingSdksList = getSubDirs(dePackagePath,
                /*includeRandomSuffix=*/true);
        // Delete the sdk sub directories
        getDevice().deleteFile(cePackagePath + "/" + ceSdkDirsBeforeLoadingSdksList.get(1));
        getDevice().deleteFile(dePackagePath + "/" + deSdkDirsBeforeLoadingSdksList.get(1));
        runPhase("testSdkDataPackageDirectory_CreateMissingSdkDirs");

        final List<String> ceSdkDirsAfterLoadingSdksList = getSubDirs(cePackagePath,
                /*includeRandomSuffix=*/true);
        final List<String> deSdkDirsAfterLoadingSdksList = getSubDirs(dePackagePath,
                /*includeRandomSuffix=*/true);
        assertThat(ceSdkDirsAfterLoadingSdksList)
                .containsExactlyElementsIn(ceSdkDirsBeforeLoadingSdksList);
        assertThat(deSdkDirsAfterLoadingSdksList)
                .containsExactlyElementsIn(deSdkDirsBeforeLoadingSdksList);
    }

    @Test
    public void testSdkDataPackageDirectory_OnUpdateDoesNotConsumeSdk() throws Exception {
        installPackage(TEST_APP_STORAGE_APK);

        final String cePath = getSdkDataPackagePath(0, TEST_APP_STORAGE_PACKAGE, true);
        final String dePath = getSdkDataPackagePath(0, TEST_APP_STORAGE_PACKAGE, false);
        assertThat(getDevice().isDirectory(cePath)).isTrue();
        assertThat(getDevice().isDirectory(dePath)).isTrue();

        // Update app so that it no longer consumes any sdk
        installPackage(TEST_APP_STORAGE_V2_NO_SDK);
        assertThat(getDevice().isDirectory(cePath)).isFalse();
        assertThat(getDevice().isDirectory(dePath)).isFalse();
    }

    @Test
    public void testSdkDataSubDirectory_IsCreatedOnInstall() throws Exception {
        // Directory should not exist before install
        assertThat(getSdkDataPerSdkPath(
                    0, TEST_APP_STORAGE_PACKAGE, SDK_NAME, true)).isNull();
        assertThat(getSdkDataPerSdkPath(
                    0, TEST_APP_STORAGE_PACKAGE, SDK_NAME, false)).isNull();

        // Install the app
        installPackage(TEST_APP_STORAGE_APK);

        // Verify directory is created
        assertThat(getSdkDataPerSdkPath(
                    0, TEST_APP_STORAGE_PACKAGE, SDK_NAME, true)).isNotNull();
        assertThat(getSdkDataPerSdkPath(
                    0, TEST_APP_STORAGE_PACKAGE, SDK_NAME, false)).isNotNull();
    }

    @Test
    @LargeTest
    public void testSdkDataSubDirectory_IsCreatedOnInstall_DeviceLocked()
            throws Exception {
        assumeThat("Device is NOT encrypted with file-based encryption.",
                getDevice().getProperty("ro.crypto.type"), equalTo("file"));
        assumeTrue("Screen lock is not supported so skip direct boot test",
                hasDeviceFeature("android.software.secure_lock_screen"));

        // Store number of package directories under root path for comparison later
        final String ceSandboxPath = getSdkDataRootPath(0, /*isCeData=*/true);
        String[] children = getDevice().getChildren(ceSandboxPath);
        final int numberOfChildren = children.length;

        try {
            mDeviceLockUtils.rebootToLockedDevice();
            // Install app after installation
            installPackage(TEST_APP_STORAGE_APK);
            // De storage area should already have per-sdk directories
            assertThat(getSdkDataPerSdkPath(
                        0, TEST_APP_STORAGE_PACKAGE, SDK_NAME, /*isCeData=*/false)).isNotNull();

            mDeviceLockUtils.unlockDevice();

            // Allow some time for reconciliation task to finish
            Thread.sleep(WAIT_FOR_RECONCILE_MS);

            assertThat(getSdkDataPerSdkPath(
                        0, TEST_APP_STORAGE_PACKAGE, SDK_NAME, /*isCeData=*/false)).isNotNull();
            // Once device is unlocked, the per-sdk ce directories should be created
            assertThat(getSdkDataPerSdkPath(
                        0, TEST_APP_STORAGE_PACKAGE, SDK_NAME, /*isCeData=*/true)).isNotNull();
        } finally {
            mDeviceLockUtils.clearScreenLock();
        }
    }

    @Test
    public void testSdkData_CanBeMovedToDifferentVolume() throws Exception {
        assumeTrue(mAdoptableUtils.isAdoptableStorageSupported());

        installPackage(TEST_APP_STORAGE_APK);

        // Create a new adoptable storage where we will be moving our installed package
        try {
            final String newVolumeUuid = mAdoptableUtils.createNewVolume();

            assertSuccess(getDevice().executeShellCommand(
                    "pm move-package " + TEST_APP_STORAGE_PACKAGE + " " + newVolumeUuid));

            // Verify that sdk data is moved
            for (int i = 0; i < 2; i++) {
                boolean isCeData = (i == 0) ? true : false;
                final String sdkDataRootPath = "/mnt/expand/" + newVolumeUuid
                        + (isCeData ? "/misc_ce" : "/misc_de") +  "/0/sdksandbox";
                final String sdkDataPackagePath = sdkDataRootPath + "/" + TEST_APP_STORAGE_PACKAGE;
                final String sdkDataSharedPath = sdkDataPackagePath + "/" + "shared";

                assertThat(getDevice().isDirectory(sdkDataRootPath)).isTrue();
                assertThat(getDevice().isDirectory(sdkDataPackagePath)).isTrue();
                assertThat(getDevice().isDirectory(sdkDataSharedPath)).isTrue();

                assertSelinuxLabel(sdkDataRootPath, "system_data_file");
                assertSelinuxLabel(sdkDataPackagePath, "system_data_file");
                assertSelinuxLabel(sdkDataSharedPath, "sdk_sandbox_data_file");
            }
        } finally {
            mAdoptableUtils.cleanUpVolume();
        }
    }

    @Test
    public void testSdkSharedStorage_DifferentVolumeIsUsable() throws Exception {
        assumeTrue(mAdoptableUtils.isAdoptableStorageSupported());

        installPackage(TEST_APP_STORAGE_APK);

        // Move the app to another volume and check if the sdk can read and write to it.
        try {
            final String newVolumeUuid = mAdoptableUtils.createNewVolume();
            assertSuccess(getDevice().executeShellCommand(
                    "pm move-package " + TEST_APP_STORAGE_PACKAGE + " " + newVolumeUuid));

            final String sharedCePath = "/mnt/expand/" + newVolumeUuid + "/misc_ce/0/sdksandbox/"
                    + TEST_APP_STORAGE_PACKAGE + "/shared";
            assertThat(getDevice().isDirectory(sharedCePath)).isTrue();

            String fileToRead = sharedCePath + "/readme.txt";
            getDevice().executeShellCommand("echo something to read > " + fileToRead);
            assertThat(getDevice().doesFileExist(fileToRead)).isTrue();

            runPhase("testSdkDataPackageDirectory_SharedStorageIsUsable");

            // Assert that the sdk was able to create file and directories
            assertThat(getDevice().isDirectory(sharedCePath + "/dir")).isTrue();
            assertThat(getDevice().doesFileExist(sharedCePath + "/dir/file")).isTrue();
            String content = getDevice().executeShellCommand("cat " + sharedCePath + "/dir/file");
            assertThat(content).isEqualTo("something to read");

        } finally {
            mAdoptableUtils.cleanUpVolume();
        }
    }

    @Test
    public void testSdkData_IsAttributedToApp() throws Exception {
        installPackage(TEST_APP_STORAGE_APK);
        runPhase("testSdkDataIsAttributedToApp");
    }

    private String getAppDataPath(int userId, String packageName, boolean isCeData) {
        if (isCeData) {
            return String.format("/data/user/%d/%s", userId, packageName);
        } else {
            return String.format("/data/user_de/%d/%s", userId, packageName);
        }
    }

    private String getSdkDataRootPath(int userId, boolean isCeData) {
        if (isCeData) {
            return String.format("/data/misc_ce/%d/sdksandbox", userId);
        } else {
            return String.format("/data/misc_de/%d/sdksandbox", userId);
        }
    }

    private String getSdkDataPackagePath(int userId, String packageName, boolean isCeData) {
        return String.format(
            "%s/%s", getSdkDataRootPath(userId, isCeData), packageName);
    }

    private String getSdkDataSharedPath(int userId, String packageName,
            boolean isCeData) {
        return String.format(
            "%s/shared", getSdkDataPackagePath(userId, packageName, isCeData));
    }

    // Per-Sdk directory has random suffix. So we need to iterate over the app-level directory
    // to find it.
    @Nullable
    private String getSdkDataPerSdkPath(int userId, String packageName, String sdkName,
            boolean isCeData) throws Exception {
        final String appLevelPath = getSdkDataPackagePath(userId, packageName, isCeData);
        final String[] children = getDevice().getChildren(appLevelPath);
        String result = null;
        for (String child : children) {
            String[] tokens = child.split("@");
            if (tokens.length != 2) {
                continue;
            }
            String sdkNameFound = tokens[0];
            if (sdkName.equals(sdkNameFound)) {
                if (result == null) {
                    result = appLevelPath + "/" + child;
                } else {
                    throw new IllegalStateException("Found two per-sdk directory for " + sdkName);
                }
            }
        }
        return result;
    }

    private List<String> getSubDirs(String path, boolean includeRandomSuffix)
            throws Exception {
        final String[] children = getDevice().getChildren(path);
        if (children == null) {
            return Collections.emptyList();
        }
        if (includeRandomSuffix) {
            return new ArrayList<>(Arrays.asList(children));
        }
        final List<String> result = new ArrayList();
        for (int i = 0; i < children.length; i++) {
            final String[] tokens = children[i].split("@");
            result.add(tokens[0]);
        }
        return result;
    }

    private void assertSelinuxLabel(@Nullable String path, String label) throws Exception {
        assertThat(path).isNotNull();
        final String output = getDevice().executeShellCommand("ls -ldZ " + path);
        assertThat(output).contains("u:object_r:" + label);
    }

    private int createAndStartSecondaryUser() throws Exception {
        String name = "SdkSandboxStorageHostTest_User" + System.currentTimeMillis();
        int newId = getDevice().createUser(name);
        getDevice().startUser(newId);
        // Note we can't install apps on a locked user
        awaitUserUnlocked(newId);
        return newId;
    }

    private void awaitUserUnlocked(int userId) throws Exception {
        for (int i = 0; i < SWITCH_USER_COMPLETED_NUMBER_OF_POLLS; ++i) {
            String userState = getDevice().executeShellCommand("am get-started-user-state "
                    + userId);
            if (userState.contains("RUNNING_UNLOCKED")) {
                return;
            }
            Thread.sleep(SWITCH_USER_COMPLETED_POLL_INTERVAL_IN_MILLIS);
        }
        fail("Timed out in unlocking user: " + userId);
    }

    private void removeSecondaryUserIfNecessary() throws Exception {
        if (mSecondaryUserId != -1) {
            // Can't remove the 2nd user without switching out of it
            assertThat(getDevice().switchUser(mOriginalUserId)).isTrue();
            getDevice().removeUser(mSecondaryUserId);
            mSecondaryUserId = -1;
        }
    }

    private static void assertSuccess(String str) {
        if (str == null || !str.startsWith("Success")) {
            throw new AssertionError("Expected success string but found " + str);
        }
    }

    private void assertDirectoryExists(String path) throws Exception {
        assertWithMessage(path + " is not a directory or does not exist")
            .that(getDevice().isDirectory(path)).isTrue();
    }

    private void assertDirectoryDoesNotExist(String path) throws Exception {
        assertWithMessage(path + " exists when expected not to")
            .that(getDevice().doesFileExist(path)).isFalse();
    }

    private static class AdoptableStorageUtils {

        private final BaseHostJUnit4Test mTest;

        private String mDiskId;

        AdoptableStorageUtils(BaseHostJUnit4Test test) {
            mTest = test;
        }

        public boolean isAdoptableStorageSupported() throws Exception {
            boolean hasFeature = mTest.getDevice().hasFeature(
                    "feature:android.software.adoptable_storage");
            boolean hasFstab = Boolean.parseBoolean(mTest.getDevice().executeShellCommand(
                        "sm has-adoptable").trim());
            return hasFeature && hasFstab;
        }

        // Creates a new volume in adoptable storage and returns its uuid
        public String createNewVolume() throws Exception {
            mDiskId = getAdoptionDisk();
            assertEmpty(mTest.getDevice().executeShellCommand(
                        "sm partition " + mDiskId + " private"));
            final LocalVolumeInfo vol = getAdoptionVolume();
            return vol.uuid;
        }

        // Destroy the volume created before
        public void cleanUpVolume() throws Exception {
            mTest.getDevice().executeShellCommand("sm partition " + mDiskId + " public");
            mTest.getDevice().executeShellCommand("sm forget all");
        }

        private String getAdoptionDisk() throws Exception {
            // In the case where we run multiple test we cleanup the state of the device. This
            // results in the execution of sm forget all which causes the MountService to "reset"
            // all its knowledge about available drives. This can cause the adoptable drive to
            // become temporarily unavailable.
            int attempt = 0;
            String disks = mTest.getDevice().executeShellCommand("sm list-disks adoptable");
            while ((disks == null || disks.isEmpty()) && attempt++ < 15) {
                Thread.sleep(1000);
                disks = mTest.getDevice().executeShellCommand("sm list-disks adoptable");
            }

            if (disks == null || disks.isEmpty()) {
                throw new AssertionError(
                        "Devices that claim to support adoptable storage must have "
                        + "adoptable media inserted during CTS to verify correct behavior");
            }
            return disks.split("\n")[0].trim();
        }

        private static void assertEmpty(String str) {
            if (str != null && str.trim().length() > 0) {
                throw new AssertionError("Expected empty string but found " + str);
            }
        }

        private static class LocalVolumeInfo {
            public String volId;
            public String state;
            public String uuid;

            LocalVolumeInfo(String line) {
                final String[] split = line.split(" ");
                volId = split[0];
                state = split[1];
                uuid = split[2];
            }
        }

        private LocalVolumeInfo getAdoptionVolume() throws Exception {
            String[] lines = null;
            int attempt = 0;
            int mounted_count = 0;
            while (attempt++ < 15) {
                lines = mTest.getDevice().executeShellCommand(
                        "sm list-volumes private").split("\n");
                CLog.w("getAdoptionVolume(): " + Arrays.toString(lines));
                for (String line : lines) {
                    final LocalVolumeInfo info = new LocalVolumeInfo(line.trim());
                    if (!"private".equals(info.volId)) {
                        if ("mounted".equals(info.state)) {
                            // make sure the storage is mounted and stable for a while
                            mounted_count++;
                            attempt--;
                            if (mounted_count >= 3) {
                                return waitForVolumeReady(info);
                            }
                        } else {
                            mounted_count = 0;
                        }
                    }
                }
                Thread.sleep(1000);
            }
            throw new AssertionError("Expected private volume; found " + Arrays.toString(lines));
        }

        private LocalVolumeInfo waitForVolumeReady(LocalVolumeInfo vol)
                throws Exception {
            int attempt = 0;
            while (attempt++ < 15) {
                if (mTest.getDevice().executeShellCommand(
                            "dumpsys package volumes").contains(vol.volId)) {
                    return vol;
                }
                Thread.sleep(1000);
            }
            throw new AssertionError("Volume not ready " + vol.volId);
        }
    }

    private static class DeviceLockUtils {

        private static final String FBE_MODE_EMULATED = "emulated";
        private static final String FBE_MODE_NATIVE = "native";

        private final BaseHostJUnit4Test mTest;

        private boolean mIsDeviceLocked = false;

        DeviceLockUtils(BaseHostJUnit4Test test) {
            mTest = test;
        }

        public void rebootToLockedDevice() throws Exception {
            // Setup screenlock
            mTest.getDevice().executeShellCommand(
                    "settings put global require_password_to_decrypt 0");
            mTest.getDevice().executeShellCommand("locksettings set-disabled false");
            String response = mTest.getDevice().executeShellCommand("locksettings set-pin 1234");
            if (!response.contains("1234")) {
                // This seems to fail occasionally. Try again once, then give up.
                Thread.sleep(500);
                response = mTest.getDevice().executeShellCommand("locksettings set-pin 1234");
                assertWithMessage("Test requires setting a pin, which failed: " + response)
                    .that(response).contains("1234");
            }

            // Give enough time for vold to update keys
            Thread.sleep(15000);

            // Follow DirectBootHostTest, reboot system into known state with keys ejected
            if (isFbeModeEmulated()) {
                final String res = mTest.getDevice().executeShellCommand("sm set-emulate-fbe true");
                if (res != null && res.contains("Emulation not supported")) {
                    throw new AssumptionViolatedException("FBE emulation is not supported");
                }
                mTest.getDevice().waitForDeviceNotAvailable(30000);
                mTest.getDevice().waitForDeviceOnline(120000);
            } else {
                mTest.getDevice().rebootUntilOnline();
            }
            waitForBootCompleted(mTest.getDevice());

            mIsDeviceLocked = true;
        }

        public void clearScreenLock() throws Exception {
            Thread.sleep(5000);
            try {
                unlockDevice();
                mTest.getDevice().executeShellCommand("locksettings clear --old 1234");
                mTest.getDevice().executeShellCommand("locksettings set-disabled true");
                mTest.getDevice().executeShellCommand(
                        "settings delete global require_password_to_decrypt");
            } finally {
                // Get ourselves back into a known-good state
                if (isFbeModeEmulated()) {
                    mTest.getDevice().executeShellCommand("sm set-emulate-fbe false");
                    mTest.getDevice().waitForDeviceNotAvailable(30000);
                    mTest.getDevice().waitForDeviceOnline();
                } else {
                    mTest.getDevice().rebootUntilOnline();
                }
                mTest.getDevice().waitForDeviceAvailable();
            }
        }

        public void unlockDevice() throws Exception {
            if (!mIsDeviceLocked) return;
            assertThat(mTest.runDeviceTests("com.android.cts.appdataisolation.appa",
                        "com.android.cts.appdataisolation.appa.AppATests",
                        "testUnlockDevice")).isTrue();
            mIsDeviceLocked = false;
        }

        private boolean isFbeModeEmulated() throws Exception {
            String mode = "unknown";
            for (int i = 0; i < 2; i++) {
                mode = mTest.getDevice().executeShellCommand("sm get-fbe-mode").trim();
                if (mode.equals(FBE_MODE_EMULATED)) {
                    return true;
                } else if (mode.equals(FBE_MODE_NATIVE)) {
                    return false;
                }
                // Sometimes mount service takes time to get ready
                Thread.sleep(5000);
            }
            fail("Unknown FBE mode: " + mode);
            return false;
        }

    }

}

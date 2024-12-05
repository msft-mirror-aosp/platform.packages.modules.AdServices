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

package com.android.adservices.data.consent;

import static com.android.adservices.data.consent.AppConsentDao.DATASTORE_KEY_SEPARATOR;
import static com.android.adservices.shared.testing.common.FileHelper.deleteFile;
import static com.android.dx.mockito.inline.extended.ExtendedMockito.any;
import static com.android.dx.mockito.inline.extended.ExtendedMockito.anyInt;
import static com.android.dx.mockito.inline.extended.ExtendedMockito.doReturn;
import static com.android.dx.mockito.inline.extended.ExtendedMockito.doThrow;
import static com.android.dx.mockito.inline.extended.ExtendedMockito.eq;
import static com.android.dx.mockito.inline.extended.ExtendedMockito.never;
import static com.android.dx.mockito.inline.extended.ExtendedMockito.verify;

import static com.google.common.truth.Truth.assertThat;

import static org.junit.Assert.assertThrows;
import static org.mockito.Mockito.spy;

import android.content.pm.ApplicationInfo;
import android.content.pm.PackageManager;

import androidx.annotation.NonNull;
import androidx.test.core.content.pm.ApplicationInfoBuilder;

import com.android.adservices.common.AdServicesExtendedMockitoTestCase;
import com.android.adservices.service.common.compat.PackageManagerCompatUtils;
import com.android.adservices.shared.errorlogging.AdServicesErrorLogger;
import com.android.adservices.shared.storage.AtomicFileDatastore;
import com.android.modules.utils.testing.ExtendedMockitoRule.SpyStatic;

import org.junit.Before;
import org.junit.Test;
import org.mockito.Mock;

import java.io.File;
import java.io.IOException;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;
import java.util.Set;

@SpyStatic(PackageManagerCompatUtils.class)
public final class AppConsentDaoTest extends AdServicesExtendedMockitoTestCase {
    private AppConsentDao mAppConsentDao;

    @Mock private AdServicesErrorLogger mMockAdServicesErrorLogger;

    private AtomicFileDatastore mDatastoreSpy;

    @Before
    public void setup() throws IOException {
        File datastoreFile =
                new File(mContext.getDataDir(), AppConsentDaoFixture.TEST_DATASTORE_NAME);
        deleteFile(datastoreFile);
        mDatastoreSpy =
                spy(
                        new AtomicFileDatastore(
                                datastoreFile,
                                /* datastoreVersion= */ 1,
                                /* versionKey= */ "Songs in the Key of Version",
                                mMockAdServicesErrorLogger));
        mAppConsentDao = new AppConsentDao(mDatastoreSpy, mContext.getPackageManager());
    }

    @Test
    public void testInitializeOnlyOnce() throws IOException {
        verify(mDatastoreSpy, never()).initialize();

        mAppConsentDao.initializeDatastoreIfNeeded();
        mAppConsentDao.initializeDatastoreIfNeeded();
        mAppConsentDao.initializeDatastoreIfNeeded();

        verify(mDatastoreSpy).initialize();
    }

    @Test
    public void testGetUidForInstalledPackageNameWithRealTestNameSuccess() {
        int expectedUid = mSpyContext.getApplicationInfo().uid;
        int testUid = mAppConsentDao.getUidForInstalledPackageName(mSpyContext.getPackageName());
        assertThat(testUid).isEqualTo(expectedUid);
    }

    @Test
    public void testGetUidForInstalledPackageNameWithFakePackageNameThrows() {
        Exception exception =
                assertThrows(
                        IllegalArgumentException.class,
                        () ->
                                mAppConsentDao.getUidForInstalledPackageName(
                                        AppConsentDaoFixture.APP_NOT_FOUND_PACKAGE_NAME));
        assertThat(exception)
                .hasCauseThat()
                .isInstanceOf(PackageManager.NameNotFoundException.class);
    }

    @Test
    public void testPackageNameToDatastoreKeySuccess() {
        mockPackageUid(AppConsentDaoFixture.APP10_PACKAGE_NAME, AppConsentDaoFixture.APP10_UID);
        assertThat(mAppConsentDao.toDatastoreKey(AppConsentDaoFixture.APP10_PACKAGE_NAME))
                .isEqualTo(AppConsentDaoFixture.APP10_DATASTORE_KEY);
    }

    @Test
    public void testNotFoundPackageNameToDatastoreKeyThrows() {
        mockThrowExceptionOnGetPackageUid(AppConsentDaoFixture.APP_NOT_FOUND_PACKAGE_NAME);
        assertThrows(
                IllegalArgumentException.class,
                () ->
                        mAppConsentDao.toDatastoreKey(
                                AppConsentDaoFixture.APP_NOT_FOUND_PACKAGE_NAME));
    }

    @Test
    public void testPackageNameAndUidToDatastoreKeySuccess() {
        assertThat(
                        mAppConsentDao.toDatastoreKey(
                                AppConsentDaoFixture.APP10_PACKAGE_NAME,
                                AppConsentDaoFixture.APP10_UID))
                .isEqualTo(AppConsentDaoFixture.APP10_DATASTORE_KEY);
    }

    @Test
    public void testPackageNameAndInvalidUidToDatastoreKeyThrows() {
        assertThrows(
                IllegalArgumentException.class,
                () -> mAppConsentDao.toDatastoreKey(AppConsentDaoFixture.APP10_PACKAGE_NAME, -10));
        assertThrows(
                IllegalArgumentException.class,
                () -> mAppConsentDao.toDatastoreKey(AppConsentDaoFixture.APP10_PACKAGE_NAME, 0));
    }

    @Test
    public void testDatastoreKeyToPackageNameSuccess() {
        String testPackageName =
                mAppConsentDao.datastoreKeyToPackageName(AppConsentDaoFixture.APP10_DATASTORE_KEY);
        assertThat(testPackageName).isEqualTo(AppConsentDaoFixture.APP10_PACKAGE_NAME);
    }

    @Test
    public void testEmptyDatastoreKeyToPackageNameThrows() {
        assertThrows(
                IllegalArgumentException.class, () -> mAppConsentDao.datastoreKeyToPackageName(""));
    }

    @Test
    public void testInvalidDatastoreKeyToPackageNameThrows() {
        assertThrows(
                "Missing UID should throw",
                IllegalArgumentException.class,
                () -> mAppConsentDao.datastoreKeyToPackageName("invalid.missing.uid"));
        assertThrows(
                "Missing package name should throw",
                IllegalArgumentException.class,
                () -> mAppConsentDao.datastoreKeyToPackageName("98"));
        assertThrows(
                "Missing separator should throw",
                IllegalArgumentException.class,
                () -> mAppConsentDao.datastoreKeyToPackageName("invalid.missing.separator22"));
    }

    @Test
    public void testDatastoreKeyConversion() {
        mockPackageUid(AppConsentDaoFixture.APP10_PACKAGE_NAME, AppConsentDaoFixture.APP10_UID);
        mockPackageUid(AppConsentDaoFixture.APP20_PACKAGE_NAME, AppConsentDaoFixture.APP20_UID);

        // Package name to datastore key and back to package name
        String convertedDatastoreKey =
                mAppConsentDao.toDatastoreKey(AppConsentDaoFixture.APP10_PACKAGE_NAME);
        assertThat(convertedDatastoreKey).isEqualTo(AppConsentDaoFixture.APP10_DATASTORE_KEY);
        String convertedPackageName =
                mAppConsentDao.datastoreKeyToPackageName(convertedDatastoreKey);
        assertThat(convertedPackageName).isEqualTo(AppConsentDaoFixture.APP10_PACKAGE_NAME);

        // Datastore key to package name and back
        convertedPackageName =
                mAppConsentDao.datastoreKeyToPackageName(AppConsentDaoFixture.APP20_DATASTORE_KEY);
        assertThat(convertedPackageName).isEqualTo(AppConsentDaoFixture.APP20_PACKAGE_NAME);
        convertedDatastoreKey = mAppConsentDao.toDatastoreKey(convertedPackageName);
        assertThat(convertedDatastoreKey).isEqualTo(AppConsentDaoFixture.APP20_DATASTORE_KEY);
    }

    @Test
    public void testSetConsentForAppSuccess() throws IOException {
        mockPackageUid(AppConsentDaoFixture.APP10_PACKAGE_NAME, AppConsentDaoFixture.APP10_UID);
        mockPackageUid(AppConsentDaoFixture.APP20_PACKAGE_NAME, AppConsentDaoFixture.APP20_UID);

        assertThat(mDatastoreSpy.getBoolean(AppConsentDaoFixture.APP10_DATASTORE_KEY)).isNull();
        assertThat(mDatastoreSpy.getBoolean(AppConsentDaoFixture.APP20_DATASTORE_KEY)).isNull();

        mAppConsentDao.setConsentForApp(AppConsentDaoFixture.APP10_PACKAGE_NAME, true);

        assertThat(mDatastoreSpy.getBoolean(AppConsentDaoFixture.APP10_DATASTORE_KEY)).isTrue();
        assertThat(mDatastoreSpy.getBoolean(AppConsentDaoFixture.APP20_DATASTORE_KEY)).isNull();

        mAppConsentDao.setConsentForApp(AppConsentDaoFixture.APP20_PACKAGE_NAME, false);

        assertThat(mDatastoreSpy.getBoolean(AppConsentDaoFixture.APP10_DATASTORE_KEY)).isTrue();
        assertThat(mDatastoreSpy.getBoolean(AppConsentDaoFixture.APP20_DATASTORE_KEY)).isFalse();

        verify(mDatastoreSpy).initialize();
    }

    @Test
    public void testSetConsentForNotFoundAppThrows() throws IOException {
        mockThrowExceptionOnGetPackageUid(AppConsentDaoFixture.APP_NOT_FOUND_PACKAGE_NAME);
        assertThrows(
                IllegalArgumentException.class,
                () ->
                        mAppConsentDao.setConsentForApp(
                                AppConsentDaoFixture.APP_NOT_FOUND_PACKAGE_NAME, true));

        verify(mDatastoreSpy).initialize();
    }

    @Test
    public void testSetConsentForAppIfNewWithNewKeysSuccess() throws IOException {
        mockPackageUid(AppConsentDaoFixture.APP10_PACKAGE_NAME, AppConsentDaoFixture.APP10_UID);
        mockPackageUid(AppConsentDaoFixture.APP20_PACKAGE_NAME, AppConsentDaoFixture.APP20_UID);

        assertThat(mDatastoreSpy.getBoolean(AppConsentDaoFixture.APP10_DATASTORE_KEY)).isNull();
        assertThat(mDatastoreSpy.getBoolean(AppConsentDaoFixture.APP20_DATASTORE_KEY)).isNull();

        assertThat(
                        mAppConsentDao.setConsentForAppIfNew(
                                AppConsentDaoFixture.APP10_PACKAGE_NAME, true))
                .isTrue();

        assertThat(mDatastoreSpy.getBoolean(AppConsentDaoFixture.APP10_DATASTORE_KEY)).isTrue();
        assertThat(mDatastoreSpy.getBoolean(AppConsentDaoFixture.APP20_DATASTORE_KEY)).isNull();

        assertThat(
                        mAppConsentDao.setConsentForAppIfNew(
                                AppConsentDaoFixture.APP20_PACKAGE_NAME, false))
                .isFalse();

        assertThat(mDatastoreSpy.getBoolean(AppConsentDaoFixture.APP10_DATASTORE_KEY)).isTrue();
        assertThat(mDatastoreSpy.getBoolean(AppConsentDaoFixture.APP20_DATASTORE_KEY)).isFalse();

        verify(mDatastoreSpy).initialize();
    }

    @Test
    public void testSetConsentForAppIfNewWithExistingKeysUsesOldValues() throws IOException {
        mockPackageUid(AppConsentDaoFixture.APP10_PACKAGE_NAME, AppConsentDaoFixture.APP10_UID);
        mockPackageUid(AppConsentDaoFixture.APP20_PACKAGE_NAME, AppConsentDaoFixture.APP20_UID);

        mDatastoreSpy.putBoolean(AppConsentDaoFixture.APP10_DATASTORE_KEY, false);
        mDatastoreSpy.putBoolean(AppConsentDaoFixture.APP20_DATASTORE_KEY, true);

        assertThat(mDatastoreSpy.getBoolean(AppConsentDaoFixture.APP10_DATASTORE_KEY)).isFalse();
        assertThat(mDatastoreSpy.getBoolean(AppConsentDaoFixture.APP20_DATASTORE_KEY)).isTrue();

        assertThat(
                        mAppConsentDao.setConsentForAppIfNew(
                                AppConsentDaoFixture.APP10_PACKAGE_NAME, true))
                .isFalse();
        assertThat(
                        mAppConsentDao.setConsentForAppIfNew(
                                AppConsentDaoFixture.APP20_PACKAGE_NAME, false))
                .isTrue();

        assertThat(mDatastoreSpy.getBoolean(AppConsentDaoFixture.APP10_DATASTORE_KEY)).isFalse();
        assertThat(mDatastoreSpy.getBoolean(AppConsentDaoFixture.APP20_DATASTORE_KEY)).isTrue();

        verify(mDatastoreSpy).initialize();
    }

    @Test
    public void testSetConsentForNotFoundAppIfNewThrows() throws IOException {
        mockThrowExceptionOnGetPackageUid(AppConsentDaoFixture.APP_NOT_FOUND_PACKAGE_NAME);
        assertThrows(
                IllegalArgumentException.class,
                () ->
                        mAppConsentDao.setConsentForApp(
                                AppConsentDaoFixture.APP_NOT_FOUND_PACKAGE_NAME, true));

        verify(mDatastoreSpy).initialize();
    }

    @Test
    public void testIsConsentRevokedForAppSuccess() throws IOException {
        mockPackageUid(AppConsentDaoFixture.APP10_PACKAGE_NAME, AppConsentDaoFixture.APP10_UID);
        mockPackageUid(AppConsentDaoFixture.APP20_PACKAGE_NAME, AppConsentDaoFixture.APP20_UID);

        assertThat(mDatastoreSpy.getBoolean(AppConsentDaoFixture.APP10_DATASTORE_KEY)).isNull();
        assertThat(mAppConsentDao.isConsentRevokedForApp(AppConsentDaoFixture.APP10_PACKAGE_NAME))
                .isFalse();
        assertThat(mDatastoreSpy.getBoolean(AppConsentDaoFixture.APP20_DATASTORE_KEY)).isNull();
        assertThat(mAppConsentDao.isConsentRevokedForApp(AppConsentDaoFixture.APP20_PACKAGE_NAME))
                .isFalse();

        mDatastoreSpy.putBoolean(AppConsentDaoFixture.APP10_DATASTORE_KEY, true);

        assertThat(mDatastoreSpy.getBoolean(AppConsentDaoFixture.APP10_DATASTORE_KEY)).isTrue();
        assertThat(mAppConsentDao.isConsentRevokedForApp(AppConsentDaoFixture.APP10_PACKAGE_NAME))
                .isTrue();
        assertThat(mDatastoreSpy.getBoolean(AppConsentDaoFixture.APP20_DATASTORE_KEY)).isNull();
        assertThat(mAppConsentDao.isConsentRevokedForApp(AppConsentDaoFixture.APP20_PACKAGE_NAME))
                .isFalse();

        mDatastoreSpy.putBoolean(AppConsentDaoFixture.APP20_DATASTORE_KEY, false);

        assertThat(mDatastoreSpy.getBoolean(AppConsentDaoFixture.APP10_DATASTORE_KEY)).isTrue();
        assertThat(mAppConsentDao.isConsentRevokedForApp(AppConsentDaoFixture.APP10_PACKAGE_NAME))
                .isTrue();
        assertThat(mDatastoreSpy.getBoolean(AppConsentDaoFixture.APP20_DATASTORE_KEY)).isFalse();
        assertThat(mAppConsentDao.isConsentRevokedForApp(AppConsentDaoFixture.APP20_PACKAGE_NAME))
                .isFalse();

        verify(mDatastoreSpy).initialize();
    }

    @Test
    public void testIsConsentRevokedForNotFoundAppThrows() throws IOException {
        mockThrowExceptionOnGetPackageUid(AppConsentDaoFixture.APP_NOT_FOUND_PACKAGE_NAME);
        assertThrows(
                IllegalArgumentException.class,
                () ->
                        mAppConsentDao.isConsentRevokedForApp(
                                AppConsentDaoFixture.APP_NOT_FOUND_PACKAGE_NAME));

        verify(mDatastoreSpy).initialize();
    }

    @Test
    public void testGetKnownAppsWithConsent() throws IOException {
        mDatastoreSpy.putBoolean(AppConsentDaoFixture.APP10_DATASTORE_KEY, false);
        mDatastoreSpy.putBoolean(AppConsentDaoFixture.APP20_DATASTORE_KEY, false);
        mDatastoreSpy.putBoolean(AppConsentDaoFixture.APP30_DATASTORE_KEY, true);
        List<ApplicationInfo> applicationsInstalled =
                Arrays.asList(
                        ApplicationInfoBuilder.newBuilder()
                                .setPackageName(AppConsentDaoFixture.APP10_PACKAGE_NAME)
                                .build(),
                        ApplicationInfoBuilder.newBuilder()
                                .setPackageName(AppConsentDaoFixture.APP20_PACKAGE_NAME)
                                .build(),
                        ApplicationInfoBuilder.newBuilder()
                                .setPackageName(AppConsentDaoFixture.APP30_PACKAGE_NAME)
                                .build());
        doReturn(applicationsInstalled)
                .when(() -> PackageManagerCompatUtils.getInstalledApplications(any(), anyInt()));

        Set<String> knownAppsWithConsent = mAppConsentDao.getKnownAppsWithConsent();

        assertThat(knownAppsWithConsent).hasSize(2);
        assertThat(
                        knownAppsWithConsent.containsAll(
                                Arrays.asList(
                                        AppConsentDaoFixture.APP10_PACKAGE_NAME,
                                        AppConsentDaoFixture.APP20_PACKAGE_NAME)))
                .isTrue();
        assertThat(knownAppsWithConsent.contains(AppConsentDaoFixture.APP30_PACKAGE_NAME))
                .isFalse();

        verify(mDatastoreSpy).initialize();
    }

    @Test
    public void testGetKnownAppsWithConsentNotExistentApp() throws IOException {
        mDatastoreSpy.putBoolean(AppConsentDaoFixture.APP30_DATASTORE_KEY, true);
        List<ApplicationInfo> applicationsInstalled = new ArrayList<>();
        doReturn(applicationsInstalled)
                .when(() -> PackageManagerCompatUtils.getInstalledApplications(any(), anyInt()));

        Set<String> knownAppsWithConsent = mAppConsentDao.getKnownAppsWithConsent();

        assertThat(knownAppsWithConsent).hasSize(0);

        verify(mDatastoreSpy).initialize();
    }

    @Test
    public void testGetAppsWithRevokedConsent() throws IOException {
        mDatastoreSpy.putBoolean(AppConsentDaoFixture.APP10_DATASTORE_KEY, true);
        mDatastoreSpy.putBoolean(AppConsentDaoFixture.APP20_DATASTORE_KEY, true);
        mDatastoreSpy.putBoolean(AppConsentDaoFixture.APP30_DATASTORE_KEY, false);
        List<ApplicationInfo> applicationsInstalled =
                Arrays.asList(
                        ApplicationInfoBuilder.newBuilder()
                                .setPackageName(AppConsentDaoFixture.APP10_PACKAGE_NAME)
                                .build(),
                        ApplicationInfoBuilder.newBuilder()
                                .setPackageName(AppConsentDaoFixture.APP20_PACKAGE_NAME)
                                .build(),
                        ApplicationInfoBuilder.newBuilder()
                                .setPackageName(AppConsentDaoFixture.APP30_PACKAGE_NAME)
                                .build());
        doReturn(applicationsInstalled)
                .when(() -> PackageManagerCompatUtils.getInstalledApplications(any(), anyInt()));

        Set<String> appsWithRevokedConsent = mAppConsentDao.getAppsWithRevokedConsent();

        assertThat(appsWithRevokedConsent).hasSize(2);
        assertThat(
                        appsWithRevokedConsent.containsAll(
                                Arrays.asList(
                                        AppConsentDaoFixture.APP10_PACKAGE_NAME,
                                        AppConsentDaoFixture.APP20_PACKAGE_NAME)))
                .isTrue();
        assertThat(appsWithRevokedConsent.contains(AppConsentDaoFixture.APP30_PACKAGE_NAME))
                .isFalse();

        verify(mDatastoreSpy).initialize();
    }

    @Test
    public void testGetAppsWithRevokedConsentNonExistentApp() throws IOException {
        mDatastoreSpy.putBoolean(AppConsentDaoFixture.APP10_DATASTORE_KEY, true);
        List<ApplicationInfo> applicationsInstalled = new ArrayList<>();
        doReturn(applicationsInstalled)
                .when(() -> PackageManagerCompatUtils.getInstalledApplications(any(), anyInt()));

        Set<String> appsWithRevokedConsent = mAppConsentDao.getAppsWithRevokedConsent();

        assertThat(appsWithRevokedConsent).hasSize(0);

        verify(mDatastoreSpy).initialize();
    }

    @Test
    public void testClearAllConsentData() throws IOException {
        mDatastoreSpy.putBoolean(AppConsentDaoFixture.APP10_DATASTORE_KEY, true);
        mDatastoreSpy.putBoolean(AppConsentDaoFixture.APP20_DATASTORE_KEY, true);
        mDatastoreSpy.putBoolean(AppConsentDaoFixture.APP30_DATASTORE_KEY, false);

        mAppConsentDao.clearAllConsentData();

        assertThat(mDatastoreSpy.getBoolean(AppConsentDaoFixture.APP10_DATASTORE_KEY)).isNull();
        assertThat(mDatastoreSpy.getBoolean(AppConsentDaoFixture.APP20_DATASTORE_KEY)).isNull();
        assertThat(mDatastoreSpy.getBoolean(AppConsentDaoFixture.APP30_DATASTORE_KEY)).isNull();

        verify(mDatastoreSpy).initialize();
    }

    @Test
    public void testClearKnownAppsWithConsent() throws IOException {
        mDatastoreSpy.putBoolean(AppConsentDaoFixture.APP10_DATASTORE_KEY, true);
        mDatastoreSpy.putBoolean(AppConsentDaoFixture.APP20_DATASTORE_KEY, true);
        mDatastoreSpy.putBoolean(AppConsentDaoFixture.APP30_DATASTORE_KEY, false);
        List<ApplicationInfo> applicationsInstalled =
                Arrays.asList(
                        ApplicationInfoBuilder.newBuilder()
                                .setPackageName(AppConsentDaoFixture.APP10_PACKAGE_NAME)
                                .build(),
                        ApplicationInfoBuilder.newBuilder()
                                .setPackageName(AppConsentDaoFixture.APP20_PACKAGE_NAME)
                                .build(),
                        ApplicationInfoBuilder.newBuilder()
                                .setPackageName(AppConsentDaoFixture.APP30_PACKAGE_NAME)
                                .build());
        doReturn(applicationsInstalled)
                .when(() -> PackageManagerCompatUtils.getInstalledApplications(any(), anyInt()));

        mAppConsentDao.clearKnownAppsWithConsent();

        assertThat(mDatastoreSpy.getBoolean(AppConsentDaoFixture.APP10_DATASTORE_KEY)).isNotNull();
        assertThat(mDatastoreSpy.getBoolean(AppConsentDaoFixture.APP20_DATASTORE_KEY)).isNotNull();
        assertThat(mDatastoreSpy.getBoolean(AppConsentDaoFixture.APP30_DATASTORE_KEY)).isNull();

        assertThat(mAppConsentDao.getKnownAppsWithConsent()).isEmpty();
        assertThat(mAppConsentDao.getAppsWithRevokedConsent()).isNotEmpty();

        verify(mDatastoreSpy).initialize();
    }

    @Test
    public void testClearConsentForUninstalledApp() throws IOException {
        mDatastoreSpy.putBoolean(AppConsentDaoFixture.APP10_DATASTORE_KEY, true);
        mDatastoreSpy.putBoolean(AppConsentDaoFixture.APP20_DATASTORE_KEY, true);
        mDatastoreSpy.putBoolean(AppConsentDaoFixture.APP30_DATASTORE_KEY, false);

        mAppConsentDao.clearConsentForUninstalledApp(
                AppConsentDaoFixture.APP20_PACKAGE_NAME, AppConsentDaoFixture.APP20_UID);

        assertThat(mDatastoreSpy.getBoolean(AppConsentDaoFixture.APP10_DATASTORE_KEY)).isNotNull();
        assertThat(mDatastoreSpy.getBoolean(AppConsentDaoFixture.APP20_DATASTORE_KEY)).isNull();
        assertThat(mDatastoreSpy.getBoolean(AppConsentDaoFixture.APP30_DATASTORE_KEY)).isNotNull();

        verify(mDatastoreSpy).initialize();
    }

    @Test
    public void testClearConsentForUninstalledAppWithInvalidArgsThrows() throws IOException {
        assertThrows(
                IllegalArgumentException.class,
                () ->
                        mAppConsentDao.clearConsentForUninstalledApp(
                                AppConsentDaoFixture.APP10_PACKAGE_NAME, 0));
        assertThrows(
                IllegalArgumentException.class,
                () ->
                        mAppConsentDao.clearConsentForUninstalledApp(
                                AppConsentDaoFixture.APP10_PACKAGE_NAME, -10));
        assertThrows(
                NullPointerException.class,
                () ->
                        mAppConsentDao.clearConsentForUninstalledApp(
                                null, AppConsentDaoFixture.APP10_UID));

        verify(mDatastoreSpy).initialize();
    }

    @Test
    public void testClearAllConsentForUninstalledApp() throws IOException {
        String app20User10PackageName =
                AppConsentDaoFixture.APP20_PACKAGE_NAME
                        + DATASTORE_KEY_SEPARATOR
                        + AppConsentDaoFixture.APP10_UID;

        // Ensure that a different package name that begins with the one being uninstalled isn't
        // removed from the store.
        String app20PackageNameAsPrefix =
                AppConsentDaoFixture.APP20_PACKAGE_NAME
                        + "test"
                        + DATASTORE_KEY_SEPARATOR
                        + AppConsentDaoFixture.APP10_UID;

        mDatastoreSpy.putBoolean(AppConsentDaoFixture.APP10_DATASTORE_KEY, true);
        mDatastoreSpy.putBoolean(AppConsentDaoFixture.APP20_DATASTORE_KEY, true);
        mDatastoreSpy.putBoolean(AppConsentDaoFixture.APP30_DATASTORE_KEY, false);
        mDatastoreSpy.putBoolean(app20User10PackageName, false);
        mDatastoreSpy.putBoolean(app20PackageNameAsPrefix, true);

        mAppConsentDao.clearConsentForUninstalledApp(AppConsentDaoFixture.APP20_PACKAGE_NAME);

        assertThat(mDatastoreSpy.getBoolean(AppConsentDaoFixture.APP10_DATASTORE_KEY)).isNotNull();
        assertThat(mDatastoreSpy.getBoolean(AppConsentDaoFixture.APP20_DATASTORE_KEY)).isNull();
        assertThat(mDatastoreSpy.getBoolean(AppConsentDaoFixture.APP30_DATASTORE_KEY)).isNotNull();
        assertThat(mDatastoreSpy.getBoolean(app20User10PackageName)).isNull();
        assertThat(mDatastoreSpy.getBoolean(app20PackageNameAsPrefix)).isNotNull();

        verify(mDatastoreSpy).initialize();
        verify(mDatastoreSpy).removeByPrefix(any());
    }

    @Test
    public void testClearAllConsentForUninstalledAppWithInvalidArgsThrows() throws IOException {
        assertThrows(
                NullPointerException.class,
                () -> mAppConsentDao.clearConsentForUninstalledApp(null));
        assertThrows(
                IllegalArgumentException.class,
                () -> mAppConsentDao.clearConsentForUninstalledApp(""));
        verify(mDatastoreSpy, never()).initialize();
    }

    private void mockPackageUid(@NonNull String packageName, int packageUid) {
        doReturn(packageUid)
                .when(
                        () ->
                                PackageManagerCompatUtils.getPackageUid(
                                        any(), eq(packageName), anyInt()));
    }

    private void mockThrowExceptionOnGetPackageUid(@NonNull String packageName) {
        doThrow(PackageManager.NameNotFoundException.class)
                .when(
                        () ->
                                PackageManagerCompatUtils.getPackageUid(
                                        any(), eq(packageName), anyInt()));
    }
}

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

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertNotNull;
import static org.junit.Assert.assertNull;
import static org.junit.Assert.assertThrows;
import static org.junit.Assert.assertTrue;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.doReturn;
import static org.mockito.Mockito.doThrow;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;

import android.content.Context;
import android.content.pm.ApplicationInfo;
import android.content.pm.PackageManager;

import androidx.test.core.app.ApplicationProvider;
import androidx.test.core.content.pm.ApplicationInfoBuilder;

import com.android.adservices.data.common.BooleanFileDatastore;

import org.junit.After;
import org.junit.Before;
import org.junit.Rule;
import org.junit.Test;
import org.mockito.Mock;
import org.mockito.Spy;
import org.mockito.junit.MockitoJUnit;
import org.mockito.junit.MockitoRule;

import java.io.IOException;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;
import java.util.Set;

public class AppConsentDaoTest {
    @Rule public final MockitoRule rule = MockitoJUnit.rule();
    private final Context mContext = ApplicationProvider.getApplicationContext();
    private AppConsentDao mAppConsentDao;

    @Spy
    private BooleanFileDatastore mDatastoreSpy =
            new BooleanFileDatastore(mContext, AppConsentDaoFixture.TEST_DATASTORE_NAME, 1);

    @Mock private PackageManager mPackageManagerMock;

    @Before
    public void setup() throws IOException {
        mAppConsentDao = new AppConsentDao(mDatastoreSpy, mPackageManagerMock);
    }

    @After
    public void teardown() throws IOException {
        mDatastoreSpy.clear();
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
    public void testGetUidForInstalledPackageNameWithRealTestNameSuccess()
            throws PackageManager.NameNotFoundException {
        mAppConsentDao = new AppConsentDao(mDatastoreSpy, mContext.getPackageManager());
        int expectedUid = mContext.getApplicationInfo().uid;
        int testUid = mAppConsentDao.getUidForInstalledPackageName(mContext.getPackageName());
        assertEquals(expectedUid, testUid);
    }

    @Test
    public void testGetUidForInstalledPackageNameWithFakePackageNameThrows() {
        mAppConsentDao = new AppConsentDao(mDatastoreSpy, mContext.getPackageManager());
        Exception exception =
                assertThrows(
                        IllegalArgumentException.class,
                        () ->
                                mAppConsentDao.getUidForInstalledPackageName(
                                        AppConsentDaoFixture.APP_NOT_FOUND_PACKAGE_NAME));
        assertTrue(exception.getCause() instanceof PackageManager.NameNotFoundException);
    }

    @Test
    public void testPackageNameToDatastoreKeySuccess() throws PackageManager.NameNotFoundException {
        doReturn(AppConsentDaoFixture.APP10_UID)
                .when(mPackageManagerMock)
                .getPackageUid(eq(AppConsentDaoFixture.APP10_PACKAGE_NAME), any());

        assertEquals(
                AppConsentDaoFixture.APP10_DATASTORE_KEY,
                mAppConsentDao.toDatastoreKey(AppConsentDaoFixture.APP10_PACKAGE_NAME));
    }

    @Test
    public void testNotFoundPackageNameToDatastoreKeyThrows()
            throws PackageManager.NameNotFoundException {
        doThrow(PackageManager.NameNotFoundException.class)
                .when(mPackageManagerMock)
                .getPackageUid(eq(AppConsentDaoFixture.APP_NOT_FOUND_PACKAGE_NAME), any());

        assertThrows(
                IllegalArgumentException.class,
                () ->
                        mAppConsentDao.toDatastoreKey(
                                AppConsentDaoFixture.APP_NOT_FOUND_PACKAGE_NAME));
    }

    @Test
    public void testPackageNameAndUidToDatastoreKeySuccess() {
        assertEquals(
                AppConsentDaoFixture.APP10_DATASTORE_KEY,
                mAppConsentDao.toDatastoreKey(
                        AppConsentDaoFixture.APP10_PACKAGE_NAME, AppConsentDaoFixture.APP10_UID));
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
        assertEquals(AppConsentDaoFixture.APP10_PACKAGE_NAME, testPackageName);
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
    public void testDatastoreKeyConversion() throws PackageManager.NameNotFoundException {
        doReturn(AppConsentDaoFixture.APP10_UID)
                .when(mPackageManagerMock)
                .getPackageUid(eq(AppConsentDaoFixture.APP10_PACKAGE_NAME), any());
        doReturn(AppConsentDaoFixture.APP20_UID)
                .when(mPackageManagerMock)
                .getPackageUid(eq(AppConsentDaoFixture.APP20_PACKAGE_NAME), any());

        // Package name to datastore key and back to package name
        String convertedDatastoreKey =
                mAppConsentDao.toDatastoreKey(AppConsentDaoFixture.APP10_PACKAGE_NAME);
        assertEquals(AppConsentDaoFixture.APP10_DATASTORE_KEY, convertedDatastoreKey);
        String convertedPackageName =
                mAppConsentDao.datastoreKeyToPackageName(convertedDatastoreKey);
        assertEquals(AppConsentDaoFixture.APP10_PACKAGE_NAME, convertedPackageName);

        // Datastore key to package name and back
        convertedPackageName =
                mAppConsentDao.datastoreKeyToPackageName(AppConsentDaoFixture.APP20_DATASTORE_KEY);
        assertEquals(AppConsentDaoFixture.APP20_PACKAGE_NAME, convertedPackageName);
        convertedDatastoreKey = mAppConsentDao.toDatastoreKey(convertedPackageName);
        assertEquals(AppConsentDaoFixture.APP20_DATASTORE_KEY, convertedDatastoreKey);
    }

    @Test
    public void testSetConsentForAppSuccess()
            throws IOException, PackageManager.NameNotFoundException {
        doReturn(AppConsentDaoFixture.APP10_UID)
                .when(mPackageManagerMock)
                .getPackageUid(eq(AppConsentDaoFixture.APP10_PACKAGE_NAME), any());
        doReturn(AppConsentDaoFixture.APP20_UID)
                .when(mPackageManagerMock)
                .getPackageUid(eq(AppConsentDaoFixture.APP20_PACKAGE_NAME), any());

        assertNull(mDatastoreSpy.get(AppConsentDaoFixture.APP10_DATASTORE_KEY));
        assertNull(mDatastoreSpy.get(AppConsentDaoFixture.APP20_DATASTORE_KEY));

        mAppConsentDao.setConsentForApp(AppConsentDaoFixture.APP10_PACKAGE_NAME, true);

        assertEquals(true, mDatastoreSpy.get(AppConsentDaoFixture.APP10_DATASTORE_KEY));
        assertNull(mDatastoreSpy.get(AppConsentDaoFixture.APP20_DATASTORE_KEY));

        mAppConsentDao.setConsentForApp(AppConsentDaoFixture.APP20_PACKAGE_NAME, false);

        assertEquals(true, mDatastoreSpy.get(AppConsentDaoFixture.APP10_DATASTORE_KEY));
        assertEquals(false, mDatastoreSpy.get(AppConsentDaoFixture.APP20_DATASTORE_KEY));

        verify(mDatastoreSpy).initialize();
    }

    @Test
    public void testSetConsentForNotFoundAppThrows()
            throws PackageManager.NameNotFoundException, IOException {
        doThrow(PackageManager.NameNotFoundException.class)
                .when(mPackageManagerMock)
                .getPackageUid(eq(AppConsentDaoFixture.APP_NOT_FOUND_PACKAGE_NAME), any());

        assertThrows(
                IllegalArgumentException.class,
                () ->
                        mAppConsentDao.setConsentForApp(
                                AppConsentDaoFixture.APP_NOT_FOUND_PACKAGE_NAME, true));

        verify(mDatastoreSpy).initialize();
    }

    @Test
    public void testSetConsentForAppIfNewWithNewKeysSuccess()
            throws IOException, PackageManager.NameNotFoundException {
        doReturn(AppConsentDaoFixture.APP10_UID)
                .when(mPackageManagerMock)
                .getPackageUid(eq(AppConsentDaoFixture.APP10_PACKAGE_NAME), any());
        doReturn(AppConsentDaoFixture.APP20_UID)
                .when(mPackageManagerMock)
                .getPackageUid(eq(AppConsentDaoFixture.APP20_PACKAGE_NAME), any());

        assertNull(mDatastoreSpy.get(AppConsentDaoFixture.APP10_DATASTORE_KEY));
        assertNull(mDatastoreSpy.get(AppConsentDaoFixture.APP20_DATASTORE_KEY));

        assertTrue(
                mAppConsentDao.setConsentForAppIfNew(
                        AppConsentDaoFixture.APP10_PACKAGE_NAME, true));

        assertEquals(true, mDatastoreSpy.get(AppConsentDaoFixture.APP10_DATASTORE_KEY));
        assertNull(mDatastoreSpy.get(AppConsentDaoFixture.APP20_DATASTORE_KEY));

        assertFalse(
                mAppConsentDao.setConsentForAppIfNew(
                        AppConsentDaoFixture.APP20_PACKAGE_NAME, false));

        assertEquals(true, mDatastoreSpy.get(AppConsentDaoFixture.APP10_DATASTORE_KEY));
        assertEquals(false, mDatastoreSpy.get(AppConsentDaoFixture.APP20_DATASTORE_KEY));

        verify(mDatastoreSpy).initialize();
    }

    @Test
    public void testSetConsentForAppIfNewWithExistingKeysUsesOldValues()
            throws IOException, PackageManager.NameNotFoundException {
        doReturn(AppConsentDaoFixture.APP10_UID)
                .when(mPackageManagerMock)
                .getPackageUid(eq(AppConsentDaoFixture.APP10_PACKAGE_NAME), any());
        doReturn(AppConsentDaoFixture.APP20_UID)
                .when(mPackageManagerMock)
                .getPackageUid(eq(AppConsentDaoFixture.APP20_PACKAGE_NAME), any());

        mDatastoreSpy.put(AppConsentDaoFixture.APP10_DATASTORE_KEY, false);
        mDatastoreSpy.put(AppConsentDaoFixture.APP20_DATASTORE_KEY, true);

        assertEquals(false, mDatastoreSpy.get(AppConsentDaoFixture.APP10_DATASTORE_KEY));
        assertEquals(true, mDatastoreSpy.get(AppConsentDaoFixture.APP20_DATASTORE_KEY));

        assertFalse(
                mAppConsentDao.setConsentForAppIfNew(
                        AppConsentDaoFixture.APP10_PACKAGE_NAME, true));
        assertTrue(
                mAppConsentDao.setConsentForAppIfNew(
                        AppConsentDaoFixture.APP20_PACKAGE_NAME, false));

        assertEquals(false, mDatastoreSpy.get(AppConsentDaoFixture.APP10_DATASTORE_KEY));
        assertEquals(true, mDatastoreSpy.get(AppConsentDaoFixture.APP20_DATASTORE_KEY));

        verify(mDatastoreSpy).initialize();
    }

    @Test
    public void testSetConsentForNotFoundAppIfNewThrows()
            throws PackageManager.NameNotFoundException, IOException {
        doThrow(PackageManager.NameNotFoundException.class)
                .when(mPackageManagerMock)
                .getPackageUid(eq(AppConsentDaoFixture.APP_NOT_FOUND_PACKAGE_NAME), any());

        assertThrows(
                IllegalArgumentException.class,
                () ->
                        mAppConsentDao.setConsentForApp(
                                AppConsentDaoFixture.APP_NOT_FOUND_PACKAGE_NAME, true));

        verify(mDatastoreSpy).initialize();
    }

    @Test
    public void testIsConsentRevokedForAppSuccess()
            throws IOException, PackageManager.NameNotFoundException {
        doReturn(AppConsentDaoFixture.APP10_UID)
                .when(mPackageManagerMock)
                .getPackageUid(eq(AppConsentDaoFixture.APP10_PACKAGE_NAME), any());
        doReturn(AppConsentDaoFixture.APP20_UID)
                .when(mPackageManagerMock)
                .getPackageUid(eq(AppConsentDaoFixture.APP20_PACKAGE_NAME), any());

        assertNull(mDatastoreSpy.get(AppConsentDaoFixture.APP10_DATASTORE_KEY));
        assertFalse(mAppConsentDao.isConsentRevokedForApp(AppConsentDaoFixture.APP10_PACKAGE_NAME));
        assertNull(mDatastoreSpy.get(AppConsentDaoFixture.APP20_DATASTORE_KEY));
        assertFalse(mAppConsentDao.isConsentRevokedForApp(AppConsentDaoFixture.APP20_PACKAGE_NAME));

        mDatastoreSpy.put(AppConsentDaoFixture.APP10_DATASTORE_KEY, true);

        assertEquals(true, mDatastoreSpy.get(AppConsentDaoFixture.APP10_DATASTORE_KEY));
        assertTrue(mAppConsentDao.isConsentRevokedForApp(AppConsentDaoFixture.APP10_PACKAGE_NAME));
        assertNull(mDatastoreSpy.get(AppConsentDaoFixture.APP20_DATASTORE_KEY));
        assertFalse(mAppConsentDao.isConsentRevokedForApp(AppConsentDaoFixture.APP20_PACKAGE_NAME));

        mDatastoreSpy.put(AppConsentDaoFixture.APP20_DATASTORE_KEY, false);

        assertEquals(true, mDatastoreSpy.get(AppConsentDaoFixture.APP10_DATASTORE_KEY));
        assertTrue(mAppConsentDao.isConsentRevokedForApp(AppConsentDaoFixture.APP10_PACKAGE_NAME));
        assertEquals(false, mDatastoreSpy.get(AppConsentDaoFixture.APP20_DATASTORE_KEY));
        assertFalse(mAppConsentDao.isConsentRevokedForApp(AppConsentDaoFixture.APP20_PACKAGE_NAME));

        verify(mDatastoreSpy).initialize();
    }

    @Test
    public void testIsConsentRevokedForNotFoundAppThrows()
            throws PackageManager.NameNotFoundException, IOException {
        doThrow(PackageManager.NameNotFoundException.class)
                .when(mPackageManagerMock)
                .getPackageUid(eq(AppConsentDaoFixture.APP_NOT_FOUND_PACKAGE_NAME), any());

        assertThrows(
                IllegalArgumentException.class,
                () ->
                        mAppConsentDao.isConsentRevokedForApp(
                                AppConsentDaoFixture.APP_NOT_FOUND_PACKAGE_NAME));

        verify(mDatastoreSpy).initialize();
    }

    @Test
    public void testGetKnownAppsWithConsent() throws IOException {
        mDatastoreSpy.put(AppConsentDaoFixture.APP10_DATASTORE_KEY, false);
        mDatastoreSpy.put(AppConsentDaoFixture.APP20_DATASTORE_KEY, false);
        mDatastoreSpy.put(AppConsentDaoFixture.APP30_DATASTORE_KEY, true);
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
                .when(mPackageManagerMock)
                .getInstalledApplications(any(PackageManager.ApplicationInfoFlags.class));

        final Set<String> knownAppsWithConsent = mAppConsentDao.getKnownAppsWithConsent();

        assertEquals(2, knownAppsWithConsent.size());
        assertTrue(
                knownAppsWithConsent.containsAll(
                        Arrays.asList(
                                AppConsentDaoFixture.APP10_PACKAGE_NAME,
                                AppConsentDaoFixture.APP20_PACKAGE_NAME)));
        assertFalse(knownAppsWithConsent.contains(AppConsentDaoFixture.APP30_PACKAGE_NAME));

        verify(mDatastoreSpy).initialize();
    }

    @Test
    public void testGetKnownAppsWithConsentNotExistentApp() throws IOException {
        mDatastoreSpy.put(AppConsentDaoFixture.APP30_DATASTORE_KEY, true);
        List<ApplicationInfo> applicationsInstalled = new ArrayList<>();
        doReturn(applicationsInstalled)
                .when(mPackageManagerMock)
                .getInstalledApplications(any(PackageManager.ApplicationInfoFlags.class));

        final Set<String> knownAppsWithConsent = mAppConsentDao.getKnownAppsWithConsent();

        assertEquals(0, knownAppsWithConsent.size());

        verify(mDatastoreSpy).initialize();
    }

    @Test
    public void testGetAppsWithRevokedConsent() throws IOException {
        mDatastoreSpy.put(AppConsentDaoFixture.APP10_DATASTORE_KEY, true);
        mDatastoreSpy.put(AppConsentDaoFixture.APP20_DATASTORE_KEY, true);
        mDatastoreSpy.put(AppConsentDaoFixture.APP30_DATASTORE_KEY, false);
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
                .when(mPackageManagerMock)
                .getInstalledApplications(any(PackageManager.ApplicationInfoFlags.class));

        final Set<String> appsWithRevokedConsent = mAppConsentDao.getAppsWithRevokedConsent();

        assertEquals(2, appsWithRevokedConsent.size());
        assertTrue(
                appsWithRevokedConsent.containsAll(
                        Arrays.asList(
                                AppConsentDaoFixture.APP10_PACKAGE_NAME,
                                AppConsentDaoFixture.APP20_PACKAGE_NAME)));
        assertFalse(appsWithRevokedConsent.contains(AppConsentDaoFixture.APP30_PACKAGE_NAME));

        verify(mDatastoreSpy).initialize();
    }

    @Test
    public void testGetAppsWithRevokedConsentNonExistentApp() throws IOException {
        mDatastoreSpy.put(AppConsentDaoFixture.APP10_DATASTORE_KEY, true);
        List<ApplicationInfo> applicationsInstalled = new ArrayList<>();
        doReturn(applicationsInstalled)
                .when(mPackageManagerMock)
                .getInstalledApplications(any(PackageManager.ApplicationInfoFlags.class));

        final Set<String> appsWithRevokedConsent = mAppConsentDao.getAppsWithRevokedConsent();

        assertEquals(0, appsWithRevokedConsent.size());

        verify(mDatastoreSpy).initialize();
    }

    @Test
    public void testClearAllConsentData() throws IOException {
        mDatastoreSpy.put(AppConsentDaoFixture.APP10_DATASTORE_KEY, true);
        mDatastoreSpy.put(AppConsentDaoFixture.APP20_DATASTORE_KEY, true);
        mDatastoreSpy.put(AppConsentDaoFixture.APP30_DATASTORE_KEY, false);

        mAppConsentDao.clearAllConsentData();

        assertNull(mDatastoreSpy.get(AppConsentDaoFixture.APP10_DATASTORE_KEY));
        assertNull(mDatastoreSpy.get(AppConsentDaoFixture.APP20_DATASTORE_KEY));
        assertNull(mDatastoreSpy.get(AppConsentDaoFixture.APP30_DATASTORE_KEY));

        verify(mDatastoreSpy).initialize();
    }

    @Test
    public void testClearKnownAppsWithConsent() throws IOException {
        mDatastoreSpy.put(AppConsentDaoFixture.APP10_DATASTORE_KEY, true);
        mDatastoreSpy.put(AppConsentDaoFixture.APP20_DATASTORE_KEY, true);
        mDatastoreSpy.put(AppConsentDaoFixture.APP30_DATASTORE_KEY, false);
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
                .when(mPackageManagerMock)
                .getInstalledApplications(any(PackageManager.ApplicationInfoFlags.class));

        mAppConsentDao.clearKnownAppsWithConsent();

        assertNotNull(mDatastoreSpy.get(AppConsentDaoFixture.APP10_DATASTORE_KEY));
        assertNotNull(mDatastoreSpy.get(AppConsentDaoFixture.APP20_DATASTORE_KEY));
        assertNull(mDatastoreSpy.get(AppConsentDaoFixture.APP30_DATASTORE_KEY));

        assertTrue(mAppConsentDao.getKnownAppsWithConsent().isEmpty());
        assertFalse(mAppConsentDao.getAppsWithRevokedConsent().isEmpty());

        verify(mDatastoreSpy).initialize();
    }

    @Test
    public void testClearConsentForUninstalledApp() throws IOException {
        mDatastoreSpy.put(AppConsentDaoFixture.APP10_DATASTORE_KEY, true);
        mDatastoreSpy.put(AppConsentDaoFixture.APP20_DATASTORE_KEY, true);
        mDatastoreSpy.put(AppConsentDaoFixture.APP30_DATASTORE_KEY, false);

        mAppConsentDao.clearConsentForUninstalledApp(
                AppConsentDaoFixture.APP20_PACKAGE_NAME, AppConsentDaoFixture.APP20_UID);

        assertNotNull(mDatastoreSpy.get(AppConsentDaoFixture.APP10_DATASTORE_KEY));
        assertNull(mDatastoreSpy.get(AppConsentDaoFixture.APP20_DATASTORE_KEY));
        assertNotNull(mDatastoreSpy.get(AppConsentDaoFixture.APP30_DATASTORE_KEY));

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
        final String app20User10PackageName =
                AppConsentDaoFixture.APP20_PACKAGE_NAME
                        + DATASTORE_KEY_SEPARATOR
                        + AppConsentDaoFixture.APP10_UID;

        // Ensure that a different package name that begins with the one being uninstalled isn't
        // removed from the store.
        final String app20PackageNameAsPrefix =
                AppConsentDaoFixture.APP20_PACKAGE_NAME
                        + "test"
                        + DATASTORE_KEY_SEPARATOR
                        + AppConsentDaoFixture.APP10_UID;

        mDatastoreSpy.put(AppConsentDaoFixture.APP10_DATASTORE_KEY, true);
        mDatastoreSpy.put(AppConsentDaoFixture.APP20_DATASTORE_KEY, true);
        mDatastoreSpy.put(AppConsentDaoFixture.APP30_DATASTORE_KEY, false);
        mDatastoreSpy.put(app20User10PackageName, false);
        mDatastoreSpy.put(app20PackageNameAsPrefix, true);

        mAppConsentDao.clearConsentForUninstalledApp(AppConsentDaoFixture.APP20_PACKAGE_NAME);

        assertNotNull(mDatastoreSpy.get(AppConsentDaoFixture.APP10_DATASTORE_KEY));
        assertNull(mDatastoreSpy.get(AppConsentDaoFixture.APP20_DATASTORE_KEY));
        assertNotNull(mDatastoreSpy.get(AppConsentDaoFixture.APP30_DATASTORE_KEY));
        assertNull(mDatastoreSpy.get(app20User10PackageName));
        assertNotNull(mDatastoreSpy.get(app20PackageNameAsPrefix));

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
}

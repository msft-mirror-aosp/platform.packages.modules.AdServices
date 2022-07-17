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

package com.android.adservices.service.consent;

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

import android.content.Context;
import android.content.pm.PackageManager;

import androidx.test.core.app.ApplicationProvider;

import com.android.adservices.data.common.BooleanFileDatastore;
import com.android.adservices.data.consent.AppConsentDao;
import com.android.adservices.data.consent.AppConsentDaoFixture;
import com.android.adservices.data.customaudience.CustomAudienceDao;

import org.junit.After;
import org.junit.Before;
import org.junit.Rule;
import org.junit.Test;
import org.mockito.Mock;
import org.mockito.junit.MockitoJUnit;
import org.mockito.junit.MockitoRule;

import java.io.IOException;
import java.util.Arrays;
import java.util.Set;

public class AppConsentManagerTest {
    private final Context mContext = ApplicationProvider.getApplicationContext();

    private AppConsentDao mAppConsentDao;
    private BooleanFileDatastore mDatastore;
    private AppConsentManager mAppConsentManager;

    @Rule public final MockitoRule rule = MockitoJUnit.rule();

    @Mock private PackageManager mPackageManagerMock;
    @Mock private CustomAudienceDao mCustomAudienceDaoMock;

    @Before
    public void setup() throws IOException {
        mDatastore =
                new BooleanFileDatastore(mContext, AppConsentDaoFixture.TEST_DATASTORE_NAME, 1);
        mAppConsentDao = new AppConsentDao(mDatastore, mPackageManagerMock);
        mAppConsentManager = new AppConsentManager(mAppConsentDao, mCustomAudienceDaoMock);
    }

    @After
    public void teardown() throws IOException {
        mDatastore.clear();
    }

    @Test
    public void testRevokeConsentForAppSuccess()
            throws PackageManager.NameNotFoundException, IOException {
        doReturn(AppConsentDaoFixture.APP10_UID)
                .when(mPackageManagerMock)
                .getPackageUid(eq(AppConsentDaoFixture.APP10_PACKAGE_NAME), any());

        mAppConsentManager.revokeConsentForApp(AppConsentDaoFixture.APP10_PACKAGE_NAME);

        assertEquals(true, mDatastore.get(AppConsentDaoFixture.APP10_DATASTORE_KEY));
        // TODO(b/234642471): Verify that the FLEDGE CA datastore was cleared
    }

    @Test
    public void testRevokeConsentForExistingAppOverwriteSuccess()
            throws PackageManager.NameNotFoundException, IOException {
        doReturn(AppConsentDaoFixture.APP10_UID)
                .when(mPackageManagerMock)
                .getPackageUid(eq(AppConsentDaoFixture.APP10_PACKAGE_NAME), any());

        mDatastore.put(AppConsentDaoFixture.APP10_DATASTORE_KEY, false);

        mAppConsentManager.revokeConsentForApp(AppConsentDaoFixture.APP10_PACKAGE_NAME);

        assertEquals(true, mDatastore.get(AppConsentDaoFixture.APP10_DATASTORE_KEY));
        // TODO(b/234642471): Verify that the FLEDGE CA datastore was cleared
    }

    @Test
    public void testRevokeConsentForNotFoundAppThrows()
            throws PackageManager.NameNotFoundException {
        doThrow(PackageManager.NameNotFoundException.class)
                .when(mPackageManagerMock)
                .getPackageUid(eq(AppConsentDaoFixture.APP_NOT_FOUND_PACKAGE_NAME), any());

        assertThrows(
                IllegalArgumentException.class,
                () ->
                        mAppConsentManager.revokeConsentForApp(
                                AppConsentDaoFixture.APP_NOT_FOUND_PACKAGE_NAME));
    }

    @Test
    public void testRestoreConsentForAppSuccess()
            throws PackageManager.NameNotFoundException, IOException {
        doReturn(AppConsentDaoFixture.APP10_UID)
                .when(mPackageManagerMock)
                .getPackageUid(eq(AppConsentDaoFixture.APP10_PACKAGE_NAME), any());

        mAppConsentManager.restoreConsentForApp(AppConsentDaoFixture.APP10_PACKAGE_NAME);

        assertEquals(false, mDatastore.get(AppConsentDaoFixture.APP10_DATASTORE_KEY));
    }

    @Test
    public void testRestoreConsentForExistingAppOverwriteSuccess()
            throws PackageManager.NameNotFoundException, IOException {
        doReturn(AppConsentDaoFixture.APP10_UID)
                .when(mPackageManagerMock)
                .getPackageUid(eq(AppConsentDaoFixture.APP10_PACKAGE_NAME), any());

        mDatastore.put(AppConsentDaoFixture.APP10_DATASTORE_KEY, true);

        mAppConsentManager.restoreConsentForApp(AppConsentDaoFixture.APP10_PACKAGE_NAME);

        assertEquals(false, mDatastore.get(AppConsentDaoFixture.APP10_DATASTORE_KEY));
    }

    @Test
    public void testRestoreConsentForNotFoundAppThrows()
            throws PackageManager.NameNotFoundException {
        doThrow(PackageManager.NameNotFoundException.class)
                .when(mPackageManagerMock)
                .getPackageUid(eq(AppConsentDaoFixture.APP_NOT_FOUND_PACKAGE_NAME), any());

        assertThrows(
                IllegalArgumentException.class,
                () ->
                        mAppConsentManager.restoreConsentForApp(
                                AppConsentDaoFixture.APP_NOT_FOUND_PACKAGE_NAME));
    }

    @Test
    public void testGetKnownAppsWithConsent() throws IOException {
        mDatastore.put(AppConsentDaoFixture.APP10_DATASTORE_KEY, false);
        mDatastore.put(AppConsentDaoFixture.APP20_DATASTORE_KEY, false);
        mDatastore.put(AppConsentDaoFixture.APP30_DATASTORE_KEY, true);

        final Set<String> knownAppsWithConsent = mAppConsentManager.getKnownAppsWithConsent();

        assertEquals(2, knownAppsWithConsent.size());
        assertTrue(
                knownAppsWithConsent.containsAll(
                        Arrays.asList(
                                AppConsentDaoFixture.APP10_PACKAGE_NAME,
                                AppConsentDaoFixture.APP20_PACKAGE_NAME)));
        assertFalse(knownAppsWithConsent.contains(AppConsentDaoFixture.APP30_PACKAGE_NAME));
    }

    @Test
    public void testGetAppsWithRevokedConsent() throws IOException {
        mDatastore.put(AppConsentDaoFixture.APP10_DATASTORE_KEY, true);
        mDatastore.put(AppConsentDaoFixture.APP20_DATASTORE_KEY, true);
        mDatastore.put(AppConsentDaoFixture.APP30_DATASTORE_KEY, false);

        final Set<String> appsWithRevokedConsent = mAppConsentManager.getAppsWithRevokedConsent();

        assertEquals(2, appsWithRevokedConsent.size());
        assertTrue(
                appsWithRevokedConsent.containsAll(
                        Arrays.asList(
                                AppConsentDaoFixture.APP10_PACKAGE_NAME,
                                AppConsentDaoFixture.APP20_PACKAGE_NAME)));
        assertFalse(appsWithRevokedConsent.contains(AppConsentDaoFixture.APP30_PACKAGE_NAME));
    }

    @Test
    public void testClearAllConsentData() throws IOException {
        mDatastore.put(AppConsentDaoFixture.APP10_DATASTORE_KEY, true);
        mDatastore.put(AppConsentDaoFixture.APP20_DATASTORE_KEY, true);
        mDatastore.put(AppConsentDaoFixture.APP30_DATASTORE_KEY, false);

        mAppConsentManager.clearAllConsentData();

        assertNull(mDatastore.get(AppConsentDaoFixture.APP10_DATASTORE_KEY));
        assertNull(mDatastore.get(AppConsentDaoFixture.APP20_DATASTORE_KEY));
        assertNull(mDatastore.get(AppConsentDaoFixture.APP30_DATASTORE_KEY));
        // TODO(b/234642471): Verify that the FLEDGE CA datastore was cleared
    }

    @Test
    public void testClearConsentForUninstalledApp() throws IOException {
        mDatastore.put(AppConsentDaoFixture.APP10_DATASTORE_KEY, true);
        mDatastore.put(AppConsentDaoFixture.APP20_DATASTORE_KEY, true);
        mDatastore.put(AppConsentDaoFixture.APP30_DATASTORE_KEY, false);

        mAppConsentManager.clearConsentForUninstalledApp(
                AppConsentDaoFixture.APP20_PACKAGE_NAME, AppConsentDaoFixture.APP20_UID);

        assertNotNull(mDatastore.get(AppConsentDaoFixture.APP10_DATASTORE_KEY));
        assertNull(mDatastore.get(AppConsentDaoFixture.APP20_DATASTORE_KEY));
        assertNotNull(mDatastore.get(AppConsentDaoFixture.APP30_DATASTORE_KEY));
        // TODO(b/234642471): Verify that the FLEDGE CA datastore was cleared
    }

    @Test
    public void testClearConsentForUninstalledAppWithInvalidArgsThrows() {
        assertThrows(
                IllegalArgumentException.class,
                () ->
                        mAppConsentManager.clearConsentForUninstalledApp(
                                AppConsentDaoFixture.APP10_PACKAGE_NAME, 0));
        assertThrows(
                IllegalArgumentException.class,
                () ->
                        mAppConsentManager.clearConsentForUninstalledApp(
                                AppConsentDaoFixture.APP10_PACKAGE_NAME, -10));
        assertThrows(
                NullPointerException.class,
                () ->
                        mAppConsentManager.clearConsentForUninstalledApp(
                                null, AppConsentDaoFixture.APP10_UID));
    }
}

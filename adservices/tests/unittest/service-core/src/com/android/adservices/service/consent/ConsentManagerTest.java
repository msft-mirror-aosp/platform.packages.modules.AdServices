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

import static com.android.adservices.service.consent.ConsentManager.EEA_DEVICE;

import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertThrows;
import static org.junit.Assert.assertTrue;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.doReturn;
import static org.mockito.Mockito.doThrow;
import static org.mockito.Mockito.when;

import android.content.Context;
import android.content.pm.PackageManager;

import androidx.test.core.app.ApplicationProvider;
import androidx.test.filters.SmallTest;

import com.android.adservices.data.common.BooleanFileDatastore;
import com.android.adservices.data.consent.AppConsentDao;
import com.android.adservices.data.consent.AppConsentDaoFixture;
import com.android.adservices.service.topics.TopicsWorker;

import org.junit.After;
import org.junit.Before;
import org.junit.Test;
import org.mockito.Mock;
import org.mockito.MockitoAnnotations;

import java.io.IOException;

@SmallTest
public class ConsentManagerTest {
    private final Context mContext = ApplicationProvider.getApplicationContext();

    private BooleanFileDatastore mDatastore;
    private AppConsentDao mAppConsentDao;

    private ConsentManager mConsentManager;
    @Mock private PackageManager mPackageManager;

    @Before
    public void setup() throws IOException {
        MockitoAnnotations.initMocks(this);

        mDatastore =
                new BooleanFileDatastore(mContext, AppConsentDaoFixture.TEST_DATASTORE_NAME, 1);
        mAppConsentDao = new AppConsentDao(mDatastore, mPackageManager);

        mConsentManager =
                new ConsentManager(mContext, TopicsWorker.getInstance(mContext), mAppConsentDao);
    }

    @After
    public void teardown() throws IOException {
        mDatastore.clear();
    }

    @Test
    public void testConsentIsGivenAfterEnabling() {
        when(mPackageManager.hasSystemFeature(EEA_DEVICE)).thenReturn(true);
        mConsentManager.enable(mPackageManager);

        assertTrue(mConsentManager.getConsent(mPackageManager).isGiven());
    }

    @Test
    public void testConsentIsRevokedAfterDisabling() {
        when(mPackageManager.hasSystemFeature(EEA_DEVICE)).thenReturn(true);
        mConsentManager.disable(mPackageManager);

        assertFalse(mConsentManager.getConsent(mPackageManager).isGiven());
    }

    @Test
    public void testConsentIsEnabledForEuConfig() {
        when(mPackageManager.hasSystemFeature(EEA_DEVICE)).thenReturn(true);

        assertFalse(mConsentManager.getInitialConsent(mPackageManager));
    }

    @Test
    public void testConsentIsEnabledForNonEuConfig() {
        when(mPackageManager.hasSystemFeature(EEA_DEVICE)).thenReturn(false);

        assertTrue(mConsentManager.getInitialConsent(mPackageManager));
    }

    @Test
    public void testIsFledgeConsentRevokedForAppWithFullApiConsent()
            throws IOException, PackageManager.NameNotFoundException {
        doReturn(false).when(mPackageManager).hasSystemFeature(eq(EEA_DEVICE));
        mConsentManager.enable(mPackageManager);
        assertTrue(mConsentManager.getConsent(mPackageManager).isGiven());

        doReturn(AppConsentDaoFixture.APP10_UID)
                .when(mPackageManager)
                .getPackageUid(eq(AppConsentDaoFixture.APP10_PACKAGE_NAME), any());
        doReturn(AppConsentDaoFixture.APP20_UID)
                .when(mPackageManager)
                .getPackageUid(eq(AppConsentDaoFixture.APP20_PACKAGE_NAME), any());
        doReturn(AppConsentDaoFixture.APP30_UID)
                .when(mPackageManager)
                .getPackageUid(eq(AppConsentDaoFixture.APP30_PACKAGE_NAME), any());

        mDatastore.put(AppConsentDaoFixture.APP10_DATASTORE_KEY, false);
        mDatastore.put(AppConsentDaoFixture.APP20_DATASTORE_KEY, true);

        assertFalse(
                mConsentManager.isFledgeConsentRevokedForApp(
                        mPackageManager, AppConsentDaoFixture.APP10_PACKAGE_NAME));
        assertTrue(
                mConsentManager.isFledgeConsentRevokedForApp(
                        mPackageManager, AppConsentDaoFixture.APP20_PACKAGE_NAME));
        assertFalse(
                mConsentManager.isFledgeConsentRevokedForApp(
                        mPackageManager, AppConsentDaoFixture.APP30_PACKAGE_NAME));
    }

    @Test
    public void testIsFledgeConsentRevokedForAppWithoutPrivacySandboxConsent()
            throws PackageManager.NameNotFoundException, IOException {
        doReturn(true).when(mPackageManager).hasSystemFeature(eq(EEA_DEVICE));
        mConsentManager.disable(mPackageManager);
        assertFalse(mConsentManager.getConsent(mPackageManager).isGiven());

        doReturn(AppConsentDaoFixture.APP10_UID)
                .when(mPackageManager)
                .getPackageUid(eq(AppConsentDaoFixture.APP10_PACKAGE_NAME), any());
        doReturn(AppConsentDaoFixture.APP20_UID)
                .when(mPackageManager)
                .getPackageUid(eq(AppConsentDaoFixture.APP20_PACKAGE_NAME), any());

        assertTrue(
                mConsentManager.isFledgeConsentRevokedForApp(
                        mPackageManager, AppConsentDaoFixture.APP10_PACKAGE_NAME));
        assertTrue(
                mConsentManager.isFledgeConsentRevokedForApp(
                        mPackageManager, AppConsentDaoFixture.APP20_PACKAGE_NAME));
    }

    @Test
    public void testIsFledgeConsentRevokedForNotFoundAppThrows()
            throws PackageManager.NameNotFoundException {
        doReturn(false).when(mPackageManager).hasSystemFeature(eq(EEA_DEVICE));
        mConsentManager.enable(mPackageManager);
        assertTrue(mConsentManager.getConsent(mPackageManager).isGiven());

        doThrow(PackageManager.NameNotFoundException.class)
                .when(mPackageManager)
                .getPackageUid(eq(AppConsentDaoFixture.APP_NOT_FOUND_PACKAGE_NAME), any());

        assertThrows(
                IllegalArgumentException.class,
                () ->
                        mConsentManager.isFledgeConsentRevokedForApp(
                                mPackageManager, AppConsentDaoFixture.APP_NOT_FOUND_PACKAGE_NAME));
    }

    @Test
    public void testIsFledgeConsentRevokedForAppAfterSettingFledgeUseWithFullApiConsent()
            throws IOException, PackageManager.NameNotFoundException {
        doReturn(false).when(mPackageManager).hasSystemFeature(eq(EEA_DEVICE));
        mConsentManager.enable(mPackageManager);
        assertTrue(mConsentManager.getConsent(mPackageManager).isGiven());

        doReturn(AppConsentDaoFixture.APP10_UID)
                .when(mPackageManager)
                .getPackageUid(eq(AppConsentDaoFixture.APP10_PACKAGE_NAME), any());
        doReturn(AppConsentDaoFixture.APP20_UID)
                .when(mPackageManager)
                .getPackageUid(eq(AppConsentDaoFixture.APP20_PACKAGE_NAME), any());
        doReturn(AppConsentDaoFixture.APP30_UID)
                .when(mPackageManager)
                .getPackageUid(eq(AppConsentDaoFixture.APP30_PACKAGE_NAME), any());

        mDatastore.put(AppConsentDaoFixture.APP10_DATASTORE_KEY, false);
        mDatastore.put(AppConsentDaoFixture.APP20_DATASTORE_KEY, true);

        assertFalse(
                mConsentManager.isFledgeConsentRevokedForAppAfterSettingFledgeUse(
                        mPackageManager, AppConsentDaoFixture.APP10_PACKAGE_NAME));
        assertTrue(
                mConsentManager.isFledgeConsentRevokedForAppAfterSettingFledgeUse(
                        mPackageManager, AppConsentDaoFixture.APP20_PACKAGE_NAME));
        assertFalse(
                mConsentManager.isFledgeConsentRevokedForAppAfterSettingFledgeUse(
                        mPackageManager, AppConsentDaoFixture.APP30_PACKAGE_NAME));
    }

    @Test
    public void testIsFledgeConsentRevokedForAppAfterSettingFledgeUseWithoutPrivacySandboxConsent()
            throws PackageManager.NameNotFoundException, IOException {
        doReturn(true).when(mPackageManager).hasSystemFeature(eq(EEA_DEVICE));
        mConsentManager.disable(mPackageManager);
        assertFalse(mConsentManager.getConsent(mPackageManager).isGiven());

        doReturn(AppConsentDaoFixture.APP10_UID)
                .when(mPackageManager)
                .getPackageUid(eq(AppConsentDaoFixture.APP10_PACKAGE_NAME), any());
        doReturn(AppConsentDaoFixture.APP20_UID)
                .when(mPackageManager)
                .getPackageUid(eq(AppConsentDaoFixture.APP20_PACKAGE_NAME), any());

        assertTrue(
                mConsentManager.isFledgeConsentRevokedForAppAfterSettingFledgeUse(
                        mPackageManager, AppConsentDaoFixture.APP10_PACKAGE_NAME));
        assertTrue(
                mConsentManager.isFledgeConsentRevokedForAppAfterSettingFledgeUse(
                        mPackageManager, AppConsentDaoFixture.APP20_PACKAGE_NAME));
    }

    @Test
    public void testIsFledgeConsentRevokedForAppAfterSettingFledgeUseThrows()
            throws PackageManager.NameNotFoundException {
        doReturn(false).when(mPackageManager).hasSystemFeature(eq(EEA_DEVICE));
        mConsentManager.enable(mPackageManager);
        assertTrue(mConsentManager.getConsent(mPackageManager).isGiven());

        doThrow(PackageManager.NameNotFoundException.class)
                .when(mPackageManager)
                .getPackageUid(eq(AppConsentDaoFixture.APP_NOT_FOUND_PACKAGE_NAME), any());

        assertThrows(
                IllegalArgumentException.class,
                () ->
                        mConsentManager.isFledgeConsentRevokedForAppAfterSettingFledgeUse(
                                mPackageManager, AppConsentDaoFixture.APP_NOT_FOUND_PACKAGE_NAME));
    }
}

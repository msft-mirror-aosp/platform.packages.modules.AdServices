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

package com.android.adservices.data.adselection;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertTrue;

import android.adservices.common.AdTechIdentifier;
import android.adservices.common.CommonFixture;
import android.content.Context;

import androidx.room.Room;
import androidx.test.core.app.ApplicationProvider;

import org.junit.After;
import org.junit.Before;
import org.junit.Test;

import java.util.Arrays;

public class AppInstallDaoTest {
    public static AdTechIdentifier BUYER_1 = CommonFixture.VALID_BUYER_1;
    public static AdTechIdentifier BUYER_2 = CommonFixture.VALID_BUYER_2;
    public static String PACKAGE_1 = CommonFixture.TEST_PACKAGE_NAME_1;
    public static String PACKAGE_2 = CommonFixture.TEST_PACKAGE_NAME_2;

    private static final Context CONTEXT = ApplicationProvider.getApplicationContext();

    private AppInstallDao mAppInstallDao;

    @Before
    public void setup() {
        mAppInstallDao =
                Room.inMemoryDatabaseBuilder(CONTEXT, SharedStorageDatabase.class)
                        .build()
                        .appInstallDao();
    }

    @After
    public void cleanup() {
        mAppInstallDao.deleteByPackageName(PACKAGE_1);
        mAppInstallDao.deleteByPackageName(PACKAGE_2);
    }

    @Test
    public void testSetThenRead() {
        mAppInstallDao.setAdTechsForPackage(
                PACKAGE_1, Arrays.asList(new DBAppInstallPermissions(BUYER_1, PACKAGE_1)));

        assertTrue(mAppInstallDao.canBuyerFilterPackage(BUYER_1, PACKAGE_1));
    }

    @Test
    public void testSetThenDelete() {
        mAppInstallDao.setAdTechsForPackage(
                PACKAGE_1, Arrays.asList(new DBAppInstallPermissions(BUYER_1, PACKAGE_1)));
        assertEquals(1, mAppInstallDao.deleteByPackageName(PACKAGE_1));
    }

    @Test
    public void testSetThenDeleteThenRead() {
        mAppInstallDao.setAdTechsForPackage(
                PACKAGE_1, Arrays.asList(new DBAppInstallPermissions(BUYER_1, PACKAGE_1)));
        mAppInstallDao.deleteByPackageName(PACKAGE_1);

        assertFalse(mAppInstallDao.canBuyerFilterPackage(BUYER_1, PACKAGE_1));
    }

    @Test
    public void testSetThenReadMultiple() {
        mAppInstallDao.setAdTechsForPackage(
                PACKAGE_1,
                Arrays.asList(
                        new DBAppInstallPermissions(BUYER_1, PACKAGE_1),
                        new DBAppInstallPermissions(BUYER_2, PACKAGE_2)));

        assertTrue(mAppInstallDao.canBuyerFilterPackage(BUYER_1, PACKAGE_1));
        assertTrue(mAppInstallDao.canBuyerFilterPackage(BUYER_2, PACKAGE_2));
    }

    @Test
    public void testSetThenReadMultipleSeparateCalls() {
        mAppInstallDao.setAdTechsForPackage(
                PACKAGE_1, Arrays.asList(new DBAppInstallPermissions(BUYER_1, PACKAGE_1)));
        mAppInstallDao.setAdTechsForPackage(
                PACKAGE_2, Arrays.asList(new DBAppInstallPermissions(BUYER_2, PACKAGE_2)));

        assertTrue(mAppInstallDao.canBuyerFilterPackage(BUYER_1, PACKAGE_1));
        assertTrue(mAppInstallDao.canBuyerFilterPackage(BUYER_2, PACKAGE_2));
    }

    @Test
    public void testSetThenReadMultipleBuyers() {
        mAppInstallDao.setAdTechsForPackage(
                PACKAGE_1,
                Arrays.asList(
                        new DBAppInstallPermissions(BUYER_1, PACKAGE_1),
                        new DBAppInstallPermissions(BUYER_2, PACKAGE_1)));
        assertTrue(mAppInstallDao.canBuyerFilterPackage(BUYER_1, PACKAGE_1));
        assertTrue(mAppInstallDao.canBuyerFilterPackage(BUYER_2, PACKAGE_1));
    }

    @Test
    public void testSetThenDeleteMultipleBuyers() {
        mAppInstallDao.setAdTechsForPackage(
                PACKAGE_1,
                Arrays.asList(
                        new DBAppInstallPermissions(BUYER_1, PACKAGE_1),
                        new DBAppInstallPermissions(BUYER_2, PACKAGE_1)));
        assertEquals(2, mAppInstallDao.deleteByPackageName(PACKAGE_1));
    }

    @Test
    public void testSetThenDeleteThenReadMultipleBuyers() {
        mAppInstallDao.setAdTechsForPackage(
                PACKAGE_1,
                Arrays.asList(
                        new DBAppInstallPermissions(BUYER_1, PACKAGE_1),
                        new DBAppInstallPermissions(BUYER_2, PACKAGE_1)));
        mAppInstallDao.deleteByPackageName(PACKAGE_1);
        assertFalse(mAppInstallDao.canBuyerFilterPackage(BUYER_1, PACKAGE_1));
        assertFalse(mAppInstallDao.canBuyerFilterPackage(BUYER_2, PACKAGE_1));
    }

    @Test
    public void testSetThenReadMultiplePackages() {
        mAppInstallDao.setAdTechsForPackage(
                PACKAGE_1,
                Arrays.asList(
                        new DBAppInstallPermissions(BUYER_1, PACKAGE_1),
                        new DBAppInstallPermissions(BUYER_1, PACKAGE_2)));
        assertTrue(mAppInstallDao.canBuyerFilterPackage(BUYER_1, PACKAGE_1));
        assertTrue(mAppInstallDao.canBuyerFilterPackage(BUYER_1, PACKAGE_2));
    }

    @Test
    public void testSetThenDeleteThenReadMultiplePackages() {
        mAppInstallDao.setAdTechsForPackage(
                PACKAGE_1,
                Arrays.asList(
                        new DBAppInstallPermissions(BUYER_1, PACKAGE_1),
                        new DBAppInstallPermissions(BUYER_1, PACKAGE_2)));
        mAppInstallDao.deleteByPackageName(PACKAGE_1);
        mAppInstallDao.deleteByPackageName(PACKAGE_2);
        assertFalse(mAppInstallDao.canBuyerFilterPackage(BUYER_1, PACKAGE_1));
        assertFalse(mAppInstallDao.canBuyerFilterPackage(BUYER_1, PACKAGE_2));
    }

    @Test
    public void testSetAdTechsForPackageDeletesExisting() {
        mAppInstallDao.setAdTechsForPackage(
                PACKAGE_1,
                Arrays.asList(
                        new DBAppInstallPermissions(BUYER_1, PACKAGE_1),
                        new DBAppInstallPermissions(BUYER_2, PACKAGE_2)));
        mAppInstallDao.setAdTechsForPackage(
                PACKAGE_1, Arrays.asList(new DBAppInstallPermissions(BUYER_2, PACKAGE_1)));
        assertFalse(mAppInstallDao.canBuyerFilterPackage(BUYER_1, PACKAGE_1));
        assertTrue(mAppInstallDao.canBuyerFilterPackage(BUYER_2, PACKAGE_2));
        assertTrue(mAppInstallDao.canBuyerFilterPackage(BUYER_2, PACKAGE_1));
    }
}

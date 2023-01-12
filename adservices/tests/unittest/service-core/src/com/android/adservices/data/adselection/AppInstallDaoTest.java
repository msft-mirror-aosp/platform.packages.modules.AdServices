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
import android.content.Context;

import androidx.room.Room;
import androidx.test.core.app.ApplicationProvider;

import org.junit.After;
import org.junit.Before;
import org.junit.Test;

import java.util.Arrays;

public class AppInstallDaoTest {
    public static AdTechIdentifier BUYER_1 = AdTechIdentifier.fromString("example1.com");
    public static AdTechIdentifier BUYER_2 = AdTechIdentifier.fromString("example2.com");
    public static String PACKAGE_1 = "package1.app";
    public static String PACKAGE_2 = "package2.app";

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
    public void testInsertThenRead() {
        mAppInstallDao.insertAllAppInstallPermissions(
                Arrays.asList(new DBAppInstallPermissions(BUYER_1, PACKAGE_1)));

        assertTrue(mAppInstallDao.canBuyerFilterPackage(BUYER_1, PACKAGE_1));
    }

    @Test
    public void testInsertThenDelete() {
        mAppInstallDao.insertAllAppInstallPermissions(
                Arrays.asList(new DBAppInstallPermissions(BUYER_1, PACKAGE_1)));
        assertEquals(1, mAppInstallDao.deleteByPackageName(PACKAGE_1));
    }

    @Test
    public void testInsertThenDeleteThenRead() {
        mAppInstallDao.insertAllAppInstallPermissions(
                Arrays.asList(new DBAppInstallPermissions(BUYER_1, PACKAGE_1)));
        mAppInstallDao.deleteByPackageName(PACKAGE_1);

        assertFalse(mAppInstallDao.canBuyerFilterPackage(BUYER_1, PACKAGE_1));
    }

    @Test
    public void testInsertThenReadMultiple() {
        mAppInstallDao.insertAllAppInstallPermissions(
                Arrays.asList(
                        new DBAppInstallPermissions(BUYER_1, PACKAGE_1),
                        new DBAppInstallPermissions(BUYER_2, PACKAGE_2)));

        assertTrue(mAppInstallDao.canBuyerFilterPackage(BUYER_1, PACKAGE_1));
        assertTrue(mAppInstallDao.canBuyerFilterPackage(BUYER_2, PACKAGE_2));
    }

    @Test
    public void testInsertThenReadMultipleSeparateCalls() {
        mAppInstallDao.insertAllAppInstallPermissions(
                Arrays.asList(new DBAppInstallPermissions(BUYER_1, PACKAGE_1)));
        mAppInstallDao.insertAllAppInstallPermissions(
                Arrays.asList(new DBAppInstallPermissions(BUYER_2, PACKAGE_2)));

        assertTrue(mAppInstallDao.canBuyerFilterPackage(BUYER_1, PACKAGE_1));
        assertTrue(mAppInstallDao.canBuyerFilterPackage(BUYER_2, PACKAGE_2));
    }

    @Test
    public void testInsertThenReadMultipleBuyers() {
        mAppInstallDao.insertAllAppInstallPermissions(
                Arrays.asList(
                        new DBAppInstallPermissions(BUYER_1, PACKAGE_1),
                        new DBAppInstallPermissions(BUYER_2, PACKAGE_1)));
        assertTrue(mAppInstallDao.canBuyerFilterPackage(BUYER_1, PACKAGE_1));
        assertTrue(mAppInstallDao.canBuyerFilterPackage(BUYER_2, PACKAGE_1));
    }

    @Test
    public void testInsertThenDeleteMultipleBuyers() {
        mAppInstallDao.insertAllAppInstallPermissions(
                Arrays.asList(
                        new DBAppInstallPermissions(BUYER_1, PACKAGE_1),
                        new DBAppInstallPermissions(BUYER_2, PACKAGE_1)));
        assertEquals(2, mAppInstallDao.deleteByPackageName(PACKAGE_1));
    }

    @Test
    public void testInsertThenDeleteThenReadMultipleBuyers() {
        mAppInstallDao.insertAllAppInstallPermissions(
                Arrays.asList(
                        new DBAppInstallPermissions(BUYER_1, PACKAGE_1),
                        new DBAppInstallPermissions(BUYER_2, PACKAGE_1)));
        mAppInstallDao.deleteByPackageName(PACKAGE_1);
        assertFalse(mAppInstallDao.canBuyerFilterPackage(BUYER_1, PACKAGE_1));
        assertFalse(mAppInstallDao.canBuyerFilterPackage(BUYER_2, PACKAGE_1));
    }

    @Test
    public void testInsertThenReadMultiplePackages() {
        mAppInstallDao.insertAllAppInstallPermissions(
                Arrays.asList(
                        new DBAppInstallPermissions(BUYER_1, PACKAGE_1),
                        new DBAppInstallPermissions(BUYER_1, PACKAGE_2)));
        assertTrue(mAppInstallDao.canBuyerFilterPackage(BUYER_1, PACKAGE_1));
        assertTrue(mAppInstallDao.canBuyerFilterPackage(BUYER_1, PACKAGE_2));
    }

    @Test
    public void testInsertThenDeleteThenReadMultiplePackages() {
        mAppInstallDao.insertAllAppInstallPermissions(
                Arrays.asList(
                        new DBAppInstallPermissions(BUYER_1, PACKAGE_1),
                        new DBAppInstallPermissions(BUYER_1, PACKAGE_2)));
        mAppInstallDao.deleteByPackageName(PACKAGE_1);
        mAppInstallDao.deleteByPackageName(PACKAGE_2);
        assertFalse(mAppInstallDao.canBuyerFilterPackage(BUYER_1, PACKAGE_1));
        assertFalse(mAppInstallDao.canBuyerFilterPackage(BUYER_1, PACKAGE_2));
    }
}

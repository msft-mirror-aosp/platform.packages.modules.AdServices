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

import static com.android.dx.mockito.inline.extended.ExtendedMockito.any;
import static com.android.dx.mockito.inline.extended.ExtendedMockito.anyInt;
import static com.android.dx.mockito.inline.extended.ExtendedMockito.doReturn;

import static com.google.common.truth.Truth.assertWithMessage;

import android.adservices.common.AdTechIdentifier;
import android.adservices.common.CommonFixture;
import android.content.pm.ApplicationInfo;

import androidx.room.Room;

import com.android.adservices.common.AdServicesExtendedMockitoTestCase;
import com.android.adservices.service.Flags;
import com.android.adservices.service.FlagsFactory;
import com.android.adservices.service.common.compat.PackageManagerCompatUtils;
import com.android.modules.utils.testing.ExtendedMockitoRule.MockStatic;
import com.android.modules.utils.testing.ExtendedMockitoRule.SpyStatic;

import org.junit.After;
import org.junit.Before;
import org.junit.Test;

import java.util.Arrays;
import java.util.List;

@SpyStatic(FlagsFactory.class)
@MockStatic(PackageManagerCompatUtils.class)
public final class AppInstallDaoTest extends AdServicesExtendedMockitoTestCase {
    public static AdTechIdentifier BUYER_1 = CommonFixture.VALID_BUYER_1;
    public static AdTechIdentifier BUYER_2 = CommonFixture.VALID_BUYER_2;
    public static String PACKAGE_1 = CommonFixture.TEST_PACKAGE_NAME_1;
    public static String PACKAGE_2 = CommonFixture.TEST_PACKAGE_NAME_2;

    private AppInstallDao mAppInstallDao;

    @Before
    public void setup() {
        mAppInstallDao =
                Room.inMemoryDatabaseBuilder(mContext, SharedStorageDatabase.class)
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

        assertWithMessage("canBuyerFilterPackage(%s, %s)", BUYER_1, PACKAGE_1)
                .that(mAppInstallDao.canBuyerFilterPackage(BUYER_1, PACKAGE_1))
                .isTrue();
    }

    @Test
    public void testSetThenDelete() {
        mAppInstallDao.setAdTechsForPackage(
                PACKAGE_1, Arrays.asList(new DBAppInstallPermissions(BUYER_1, PACKAGE_1)));
        assertWithMessage("deleteByPackageName(%s)", PACKAGE_1)
                .that(1)
                .isEqualTo(mAppInstallDao.deleteByPackageName(PACKAGE_1));
    }

    @Test
    public void testSetThenDeleteThenRead() {
        mAppInstallDao.setAdTechsForPackage(
                PACKAGE_1, Arrays.asList(new DBAppInstallPermissions(BUYER_1, PACKAGE_1)));
        mAppInstallDao.deleteByPackageName(PACKAGE_1);

        assertWithMessage("canBuyerFilterPackage(%s, %s)", BUYER_1, PACKAGE_1)
                .that(mAppInstallDao.canBuyerFilterPackage(BUYER_1, PACKAGE_1))
                .isFalse();
    }

    @Test
    public void testSetThenReadMultiple() {
        mAppInstallDao.setAdTechsForPackage(
                PACKAGE_1,
                Arrays.asList(
                        new DBAppInstallPermissions(BUYER_1, PACKAGE_1),
                        new DBAppInstallPermissions(BUYER_2, PACKAGE_2)));

        assertWithMessage("canBuyerFilterPackage(%s, %s)", BUYER_1, PACKAGE_1)
                .that(mAppInstallDao.canBuyerFilterPackage(BUYER_1, PACKAGE_1))
                .isTrue();
        assertWithMessage("canBuyerFilterPackage(%s, %s)", BUYER_2, PACKAGE_2)
                .that(mAppInstallDao.canBuyerFilterPackage(BUYER_2, PACKAGE_2))
                .isTrue();
    }

    @Test
    public void testSetThenReadMultipleSeparateCalls() {
        mAppInstallDao.setAdTechsForPackage(
                PACKAGE_1, Arrays.asList(new DBAppInstallPermissions(BUYER_1, PACKAGE_1)));
        mAppInstallDao.setAdTechsForPackage(
                PACKAGE_2, Arrays.asList(new DBAppInstallPermissions(BUYER_2, PACKAGE_2)));

        assertWithMessage("canBuyerFilterPackage(%s, %s)", BUYER_1, PACKAGE_1)
                .that(mAppInstallDao.canBuyerFilterPackage(BUYER_1, PACKAGE_1))
                .isTrue();
        assertWithMessage("canBuyerFilterPackage(%s, %s)", BUYER_2, PACKAGE_2)
                .that(mAppInstallDao.canBuyerFilterPackage(BUYER_2, PACKAGE_2))
                .isTrue();
    }

    @Test
    public void testSetThenReadMultipleBuyers() {
        mAppInstallDao.setAdTechsForPackage(
                PACKAGE_1,
                Arrays.asList(
                        new DBAppInstallPermissions(BUYER_1, PACKAGE_1),
                        new DBAppInstallPermissions(BUYER_2, PACKAGE_1)));
        assertWithMessage("canBuyerFilterPackage(%s, %s)", BUYER_1, PACKAGE_1)
                .that(mAppInstallDao.canBuyerFilterPackage(BUYER_1, PACKAGE_1))
                .isTrue();
        assertWithMessage("canBuyerFilterPackage(%s, %s))", BUYER_2, PACKAGE_1)
                .that(mAppInstallDao.canBuyerFilterPackage(BUYER_2, PACKAGE_1))
                .isTrue();
    }

    @Test
    public void testSetThenDeleteMultipleBuyers() {
        mAppInstallDao.setAdTechsForPackage(
                PACKAGE_1,
                Arrays.asList(
                        new DBAppInstallPermissions(BUYER_1, PACKAGE_1),
                        new DBAppInstallPermissions(BUYER_2, PACKAGE_1)));
        assertWithMessage("deleteByPackageName(%s)", PACKAGE_1)
                .that(2)
                .isEqualTo(mAppInstallDao.deleteByPackageName(PACKAGE_1));
    }

    @Test
    public void testSetThenDeleteThenReadMultipleBuyers() {
        mAppInstallDao.setAdTechsForPackage(
                PACKAGE_1,
                Arrays.asList(
                        new DBAppInstallPermissions(BUYER_1, PACKAGE_1),
                        new DBAppInstallPermissions(BUYER_2, PACKAGE_1)));
        mAppInstallDao.deleteByPackageName(PACKAGE_1);
        assertWithMessage("canBuyerFilterPackage(%s, %s)", BUYER_1, PACKAGE_1)
                .that(mAppInstallDao.canBuyerFilterPackage(BUYER_1, PACKAGE_1))
                .isFalse();
        assertWithMessage("canBuyerFilterPackage(%s, %s)", BUYER_2, PACKAGE_1)
                .that(mAppInstallDao.canBuyerFilterPackage(BUYER_2, PACKAGE_1))
                .isFalse();
    }

    @Test
    public void testSetThenReadMultiplePackages() {
        mAppInstallDao.setAdTechsForPackage(
                PACKAGE_1,
                Arrays.asList(
                        new DBAppInstallPermissions(BUYER_1, PACKAGE_1),
                        new DBAppInstallPermissions(BUYER_1, PACKAGE_2)));
        assertWithMessage("canBuyerFilterPackage(%s, %s)", BUYER_1, PACKAGE_1)
                .that(mAppInstallDao.canBuyerFilterPackage(BUYER_1, PACKAGE_1))
                .isTrue();
        assertWithMessage("canBuyerFilterPackage(%s, %s)", BUYER_1, PACKAGE_2)
                .that(mAppInstallDao.canBuyerFilterPackage(BUYER_1, PACKAGE_2))
                .isTrue();
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
        assertWithMessage("canBuyerFilterPackage(%s, %s)", BUYER_1, PACKAGE_1)
                .that(mAppInstallDao.canBuyerFilterPackage(BUYER_1, PACKAGE_1))
                .isFalse();
        assertWithMessage("canBuyerFilterPackage(%s, %s)", BUYER_1, PACKAGE_2)
                .that(mAppInstallDao.canBuyerFilterPackage(BUYER_1, PACKAGE_2))
                .isFalse();
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
        assertWithMessage("canBuyerFilterPackage(%s, %s)", BUYER_1, PACKAGE_1)
                .that(mAppInstallDao.canBuyerFilterPackage(BUYER_1, PACKAGE_1))
                .isFalse();
        assertWithMessage("canBuyerFilterPackage(%s, %s)", BUYER_2, PACKAGE_2)
                .that(mAppInstallDao.canBuyerFilterPackage(BUYER_2, PACKAGE_2))
                .isTrue();
        assertWithMessage("canBuyerFilterPackage(%s, %s)", BUYER_2, PACKAGE_1)
                .that(mAppInstallDao.canBuyerFilterPackage(BUYER_2, PACKAGE_1))
                .isTrue();
    }

    @Test
    public void testDeleteAll() {
        mAppInstallDao.setAdTechsForPackage(
                PACKAGE_1,
                Arrays.asList(
                        new DBAppInstallPermissions(BUYER_1, PACKAGE_1),
                        new DBAppInstallPermissions(BUYER_2, PACKAGE_1)));
        mAppInstallDao.deleteAllAppInstallData();
        assertWithMessage("canBuyerFilterPackage(%s, %s)", BUYER_1, PACKAGE_1)
                .that(mAppInstallDao.canBuyerFilterPackage(BUYER_1, PACKAGE_1))
                .isFalse();
        assertWithMessage("canBuyerFilterPackage(%s, %s)", BUYER_2, PACKAGE_1)
                .that(mAppInstallDao.canBuyerFilterPackage(BUYER_2, PACKAGE_1))
                .isFalse();
    }

    @Test
    public void testGetAllPackageNames() {
        mAppInstallDao.setAdTechsForPackage(
                PACKAGE_1, Arrays.asList(new DBAppInstallPermissions(BUYER_1, PACKAGE_1)));
        mAppInstallDao.setAdTechsForPackage(
                PACKAGE_2, Arrays.asList(new DBAppInstallPermissions(BUYER_1, PACKAGE_2)));
        List<String> unorderedExpected = Arrays.asList(PACKAGE_1, PACKAGE_2);
        assertWithMessage("getAllPackageNames")
                .that(mAppInstallDao.getAllPackageNames())
                .containsExactlyElementsIn(unorderedExpected);
    }

    @Test
    public void testDeleteAllDisallowedPackageEntries() {
        class FlagsThatAllowOneApp implements Flags {
            @Override
            public String getPpapiAppAllowList() {
                return PACKAGE_1;
            }
        }
        ApplicationInfo installedPackage1 = new ApplicationInfo();
        installedPackage1.packageName = PACKAGE_1;
        ApplicationInfo installedPackage2 = new ApplicationInfo();
        installedPackage2.packageName = PACKAGE_2;
        doReturn(Arrays.asList(installedPackage1, installedPackage2))
                .when(() -> PackageManagerCompatUtils.getInstalledApplications(any(), anyInt()));
        mAppInstallDao.setAdTechsForPackage(
                PACKAGE_1, Arrays.asList(new DBAppInstallPermissions(BUYER_1, PACKAGE_1)));
        mAppInstallDao.setAdTechsForPackage(
                PACKAGE_2, Arrays.asList(new DBAppInstallPermissions(BUYER_1, PACKAGE_2)));
        assertWithMessage("deleteAllDisallowedPackageEntries")
                .that(1)
                .isEqualTo(
                        mAppInstallDao.deleteAllDisallowedPackageEntries(
                                mContext.getPackageManager(), new FlagsThatAllowOneApp()));
        assertWithMessage("getAllPackageNames")
                .that(Arrays.asList(PACKAGE_1))
                .isEqualTo(mAppInstallDao.getAllPackageNames());
    }
}

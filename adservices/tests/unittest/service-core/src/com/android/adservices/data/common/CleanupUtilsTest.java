/*
 * Copyright (C) 2023 The Android Open Source Project
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

package com.android.adservices.data.common;

import static com.android.adservices.service.common.AllowLists.ALLOW_ALL;
import static com.android.dx.mockito.inline.extended.ExtendedMockito.any;
import static com.android.dx.mockito.inline.extended.ExtendedMockito.anyInt;

import static com.google.common.truth.Truth.assertWithMessage;

import static org.mockito.Mockito.when;

import android.adservices.common.CommonFixture;
import android.content.pm.ApplicationInfo;

import com.android.adservices.common.AdServicesExtendedMockitoTestCase;
import com.android.adservices.service.FlagsFactory;
import com.android.adservices.service.common.compat.PackageManagerCompatUtils;
import com.android.modules.utils.testing.ExtendedMockitoRule.MockStatic;
import com.android.modules.utils.testing.ExtendedMockitoRule.SpyStatic;

import org.junit.Test;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collections;
import java.util.List;

@SpyStatic(FlagsFactory.class)
@MockStatic(PackageManagerCompatUtils.class)
public final class CleanupUtilsTest extends AdServicesExtendedMockitoTestCase {
    @Test
    public void testEmpty() {
        List<String> packageList = new ArrayList<>();

        CleanupUtils.removeAllowedPackages(
                packageList,
                mContext.getPackageManager(),
                List.of(CommonFixture.TEST_PACKAGE_NAME_1));

        assertWithMessage("packageList").that(packageList).isEmpty();
    }

    @Test
    public void testCleanupNotUninstalled() {
        ApplicationInfo installedPackage1 = new ApplicationInfo();
        installedPackage1.packageName = CommonFixture.TEST_PACKAGE_NAME_1;
        ApplicationInfo installedPackage2 = new ApplicationInfo();
        installedPackage2.packageName = CommonFixture.TEST_PACKAGE_NAME_2;
        when(PackageManagerCompatUtils.getInstalledApplications(any(), anyInt()))
                .thenReturn(Arrays.asList(installedPackage1, installedPackage2));
        List<String> packageList =
                new ArrayList<>(
                        Arrays.asList(
                                CommonFixture.TEST_PACKAGE_NAME_1,
                                CommonFixture.TEST_PACKAGE_NAME_2));
        List<String> expected = List.of(CommonFixture.TEST_PACKAGE_NAME_2);

        CleanupUtils.removeAllowedPackages(
                packageList,
                mContext.getPackageManager(),
                List.of(CommonFixture.TEST_PACKAGE_NAME_1));

        assertWithMessage("packageList").that(packageList).containsExactlyElementsIn(expected);
    }

    @Test
    public void testCleanupNotAllowed() {
        ApplicationInfo installedPackage2 = new ApplicationInfo();
        installedPackage2.packageName = CommonFixture.TEST_PACKAGE_NAME_2;
        when(PackageManagerCompatUtils.getInstalledApplications(any(), anyInt()))
                .thenReturn(List.of(installedPackage2));
        List<String> packageList =
                new ArrayList<>(
                        Arrays.asList(
                                CommonFixture.TEST_PACKAGE_NAME_1,
                                CommonFixture.TEST_PACKAGE_NAME_2));
        List<String> expected = List.of(CommonFixture.TEST_PACKAGE_NAME_1);

        CleanupUtils.removeAllowedPackages(
                packageList, mContext.getPackageManager(), List.of(ALLOW_ALL));

        assertWithMessage("packageList").that(packageList).containsExactlyElementsIn(expected);
    }

    @Test
    public void testCleanupMultipleAllowLists() {
        ApplicationInfo installedPackage1 = new ApplicationInfo();
        installedPackage1.packageName = CommonFixture.TEST_PACKAGE_NAME_1;
        ApplicationInfo installedPackage2 = new ApplicationInfo();
        installedPackage2.packageName = CommonFixture.TEST_PACKAGE_NAME_2;
        when(PackageManagerCompatUtils.getInstalledApplications(any(), anyInt()))
                .thenReturn(Arrays.asList(installedPackage1, installedPackage2));
        List<String> packageList =
                new ArrayList<>(
                        Arrays.asList(
                                CommonFixture.TEST_PACKAGE_NAME_1,
                                CommonFixture.TEST_PACKAGE_NAME_2));
        List<String> expected = Collections.emptyList();

        CleanupUtils.removeAllowedPackages(
                packageList,
                mContext.getPackageManager(),
                Arrays.asList(
                        CommonFixture.TEST_PACKAGE_NAME_1, CommonFixture.TEST_PACKAGE_NAME_2));

        assertWithMessage("packageList").that(packageList).containsExactlyElementsIn(expected);
    }

    @Test
    public void testCleanupMultipleAllowListsWildCard() {
        ApplicationInfo installedPackage1 = new ApplicationInfo();
        installedPackage1.packageName = CommonFixture.TEST_PACKAGE_NAME_1;
        ApplicationInfo installedPackage2 = new ApplicationInfo();
        installedPackage2.packageName = CommonFixture.TEST_PACKAGE_NAME_2;
        when(PackageManagerCompatUtils.getInstalledApplications(any(), anyInt()))
                .thenReturn(Arrays.asList(installedPackage1, installedPackage2));
        List<String> packageList =
                new ArrayList<>(
                        Arrays.asList(
                                CommonFixture.TEST_PACKAGE_NAME_1,
                                CommonFixture.TEST_PACKAGE_NAME_2));
        List<String> expected = Collections.emptyList();

        CleanupUtils.removeAllowedPackages(
                packageList,
                mContext.getPackageManager(),
                Arrays.asList(CommonFixture.TEST_PACKAGE_NAME_1, ALLOW_ALL));

        assertWithMessage("packageList").that(packageList).containsExactlyElementsIn(expected);
    }

    @Test
    public void testCleanupMultipleAllowListsRedundant() {
        ApplicationInfo installedPackage1 = new ApplicationInfo();
        installedPackage1.packageName = CommonFixture.TEST_PACKAGE_NAME_1;
        ApplicationInfo installedPackage2 = new ApplicationInfo();
        installedPackage2.packageName = CommonFixture.TEST_PACKAGE_NAME_2;
        when(PackageManagerCompatUtils.getInstalledApplications(any(), anyInt()))
                .thenReturn(Arrays.asList(installedPackage1, installedPackage2));
        List<String> packageList =
                new ArrayList<>(
                        Arrays.asList(
                                CommonFixture.TEST_PACKAGE_NAME_1,
                                CommonFixture.TEST_PACKAGE_NAME_2));
        List<String> expected = List.of(CommonFixture.TEST_PACKAGE_NAME_2);

        CleanupUtils.removeAllowedPackages(
                packageList,
                mContext.getPackageManager(),
                Arrays.asList(
                        CommonFixture.TEST_PACKAGE_NAME_1, CommonFixture.TEST_PACKAGE_NAME_1));

        assertWithMessage("packageList").that(packageList).containsExactlyElementsIn(expected);
    }
}

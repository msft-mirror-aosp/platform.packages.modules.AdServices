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

package com.android.adservices.service.common;

import static com.android.adservices.service.common.AllowLists.ALLOW_ALL;

import static com.google.common.truth.Truth.assertThat;

import androidx.test.filters.SmallTest;

import org.junit.Test;

/** Unit tests for {@link com.android.adservices.service.common.AllowLists} */
@SmallTest
public class AllowListsTest {
    @Test
    public void testAppCanUsePpapi_allowAll() {
        assertThat(AllowLists.isPackageAllowListed(ALLOW_ALL, "SomePackageName")).isTrue();
    }

    @Test
    public void testAppCanUsePpapi_emptyAllowList() {
        assertThat(AllowLists.isPackageAllowListed("", "SomePackageName")).isFalse();
    }

    @Test
    public void testAppCanUsePpapi_notEmptyAllowList() {
        String allowList = "SomePackageName,AnotherPackageName";
        assertThat(AllowLists.isPackageAllowListed(allowList, "notAllowedPackageName")).isFalse();
        assertThat(AllowLists.isPackageAllowListed(allowList, "SomePackageName")).isTrue();
        assertThat(AllowLists.isPackageAllowListed(allowList, "AnotherPackageName")).isTrue();
    }

    @Test
    public void testAppCanUsePpapi_havingSpaceFail() {
        String allowList = "SomePackageName, AnotherPackageName";
        assertThat(AllowLists.isPackageAllowListed(allowList, "notAllowedPackageName")).isFalse();
        assertThat(AllowLists.isPackageAllowListed(allowList, "SomePackageName")).isTrue();
        // There is a space in the package name list.
        assertThat(AllowLists.isPackageAllowListed(allowList, "AnotherPackageName")).isFalse();
    }
}

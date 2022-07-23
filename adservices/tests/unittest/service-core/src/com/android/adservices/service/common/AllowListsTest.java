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

import static org.mockito.Mockito.when;

import androidx.test.filters.SmallTest;

import com.android.adservices.service.Flags;

import org.junit.Before;
import org.junit.Test;
import org.mockito.Mock;
import org.mockito.MockitoAnnotations;

/** Unit tests for {@link com.android.adservices.service.common.AllowLists} */
@SmallTest
public class AllowListsTest {
    @Mock Flags mMockFlags;

    @Before
    public void setup() {
        MockitoAnnotations.initMocks(this);
    }

    @Test
    public void testAppCanUsePpapi_allowAll() {
        when(mMockFlags.getPpapiAppAllowList()).thenReturn(ALLOW_ALL);
        assertThat(AllowLists.appCanUsePpapi(mMockFlags, "SomePackageName")).isTrue();
    }

    @Test
    public void testAppCanUsePpapi_emptyAllowList() {
        when(mMockFlags.getPpapiAppAllowList()).thenReturn("");
        assertThat(AllowLists.appCanUsePpapi(mMockFlags, "SomePackageName")).isFalse();
    }

    @Test
    public void testAppCanUsePpapi_notEmptyAllowList() {
        when(mMockFlags.getPpapiAppAllowList()).thenReturn("SomePackageName,AnotherPackageName");
        assertThat(AllowLists.appCanUsePpapi(mMockFlags, "notAllowedPackageName")).isFalse();
        assertThat(AllowLists.appCanUsePpapi(mMockFlags, "SomePackageName")).isTrue();
        assertThat(AllowLists.appCanUsePpapi(mMockFlags, "AnotherPackageName")).isTrue();
    }

    @Test
    public void testAppCanUsePpapi_havingSpaceFail() {
        when(mMockFlags.getPpapiAppAllowList()).thenReturn("SomePackageName, AnotherPackageName");
        assertThat(AllowLists.appCanUsePpapi(mMockFlags, "notAllowedPackageName")).isFalse();
        assertThat(AllowLists.appCanUsePpapi(mMockFlags, "SomePackageName")).isTrue();
        // There is a space in the package name list.
        assertThat(AllowLists.appCanUsePpapi(mMockFlags, "AnotherPackageName")).isFalse();
    }
}

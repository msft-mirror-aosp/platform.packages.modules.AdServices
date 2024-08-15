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

package com.android.adservices.service.signals;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertThrows;

import android.adservices.common.CommonFixture;

import com.android.adservices.shared.testing.SdkLevelSupportRule;

import org.junit.Rule;
import org.junit.Test;

public class ProtectedSignalTest {

    @Rule(order = 0)
    public final SdkLevelSupportRule sdkLevel = SdkLevelSupportRule.forAtLeastT();

    private static final String VALID_BASE64 = "dGVzdGluZw==";
    private static final String VALID_HEX = "74657374696E67";

    @Test
    public void test_protectedSignalBuilder_hexValueSetsBase64_returnsProperBase64() {
        ProtectedSignal signal =
                ProtectedSignal.builder()
                        .setHexEncodedValue(VALID_HEX)
                        .setCreationTime(CommonFixture.FIXED_NOW)
                        .setPackageName(CommonFixture.TEST_PACKAGE_NAME)
                        .build();

        assertEquals(VALID_BASE64, signal.getBase64EncodedValue());
        assertEquals(VALID_HEX, signal.getHexEncodedValue());
        assertEquals(CommonFixture.FIXED_NOW, signal.getCreationTime());
        assertEquals(CommonFixture.TEST_PACKAGE_NAME, signal.getPackageName());
    }

    @Test
    public void test_protectedSignalBuilder_base64ValueSetsHex_returnsProperHex() {
        ProtectedSignal signal =
                ProtectedSignal.builder()
                        .setBase64EncodedValue(VALID_BASE64)
                        .setCreationTime(CommonFixture.FIXED_NOW)
                        .setPackageName(CommonFixture.TEST_PACKAGE_NAME)
                        .build();

        assertEquals(VALID_BASE64, signal.getBase64EncodedValue());
        assertEquals(VALID_HEX, signal.getHexEncodedValue());
        assertEquals(CommonFixture.FIXED_NOW, signal.getCreationTime());
        assertEquals(CommonFixture.TEST_PACKAGE_NAME, signal.getPackageName());
    }

    @Test
    public void test_protectedSignalBuilder_invalidBase64_throwsException() {
        assertThrows(
                IllegalArgumentException.class,
                () -> {
                    ProtectedSignal.builder()
                            .setBase64EncodedValue("not base 64")
                            .setCreationTime(CommonFixture.FIXED_NOW)
                            .setPackageName(CommonFixture.TEST_PACKAGE_NAME_1)
                            .build();
                });
    }
}

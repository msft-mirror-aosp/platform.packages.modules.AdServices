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

package com.android.adservices.data.signals;

import static android.adservices.common.CommonFixture.FIXED_NOW_TRUNCATED_TO_MILLI;
import static android.adservices.common.CommonFixture.TEST_PACKAGE_NAME_1;
import static android.adservices.common.CommonFixture.VALID_BUYER_1;

import static com.android.adservices.data.signals.DBProtectedSignalFixture.KEY;
import static com.android.adservices.data.signals.DBProtectedSignalFixture.VALUE;

import android.adservices.common.CommonFixture;

import com.android.adservices.common.AdServicesUnitTestCase;
import com.android.adservices.service.signals.evict.EvictionPriority;
import com.android.adservices.shared.testing.EqualsTester;
import com.android.adservices.shared.testing.annotations.RequiresSdkLevelAtLeastT;

import org.junit.Test;

@RequiresSdkLevelAtLeastT
public class DBProtectedSignalTest extends AdServicesUnitTestCase {

    private final EqualsTester mEqualsTester = new EqualsTester(expect);

    @Test
    public void testCreateSignal() {
        DBProtectedSignal signal =
                DBProtectedSignal.create(
                        null,
                        VALID_BUYER_1,
                        KEY,
                        DBProtectedSignalFixture.VALUE,
                        FIXED_NOW_TRUNCATED_TO_MILLI,
                        TEST_PACKAGE_NAME_1,
                        EvictionPriority.DEFAULT);

        expect.withMessage("id").that(signal.getId()).isNull();
        expect.withMessage("buyer").that(signal.getBuyer()).isEqualTo(VALID_BUYER_1);
        expect.withMessage("key").that(signal.getKey()).isEqualTo(KEY);
        expect.withMessage("value").that(signal.getValue()).isEqualTo(VALUE);
        expect.withMessage("creationTime")
                .that(signal.getCreationTime())
                .isEqualTo(FIXED_NOW_TRUNCATED_TO_MILLI);
        expect.withMessage("packageName")
                .that(signal.getPackageName())
                .isEqualTo(TEST_PACKAGE_NAME_1);
        expect.withMessage("evictionPriority")
                .that(signal.getEvictionPriority())
                .isEqualTo(EvictionPriority.DEFAULT);
    }

    @Test
    public void testBuildSignal() {
        DBProtectedSignal signal =
                DBProtectedSignal.builder()
                        .setId(null)
                        .setBuyer(VALID_BUYER_1)
                        .setKey(KEY)
                        .setValue(DBProtectedSignalFixture.VALUE)
                        .setCreationTime(FIXED_NOW_TRUNCATED_TO_MILLI)
                        .setPackageName(TEST_PACKAGE_NAME_1)
                        .setEvictionPriority(EvictionPriority.EVICT_SOONER)
                        .build();

        expect.withMessage("id").that(signal.getId()).isNull();
        expect.withMessage("buyer").that(signal.getBuyer()).isEqualTo(VALID_BUYER_1);
        expect.withMessage("key").that(signal.getKey()).isEqualTo(KEY);
        expect.withMessage("value").that(signal.getValue()).isEqualTo(VALUE);
        expect.withMessage("creationTime")
                .that(signal.getCreationTime())
                .isEqualTo(FIXED_NOW_TRUNCATED_TO_MILLI);
        expect.withMessage("packageName")
                .that(signal.getPackageName())
                .isEqualTo(TEST_PACKAGE_NAME_1);
        expect.withMessage("evictionPriority")
                .that(signal.getEvictionPriority())
                .isEqualTo(EvictionPriority.EVICT_SOONER);
    }

    @Test
    public void testEqual_sameId() {
        DBProtectedSignal signal1 =
                DBProtectedSignal.builder()
                        .setId(1L)
                        .setBuyer(VALID_BUYER_1)
                        .setKey(KEY)
                        .setValue(DBProtectedSignalFixture.VALUE)
                        .setCreationTime(FIXED_NOW_TRUNCATED_TO_MILLI)
                        .setPackageName(TEST_PACKAGE_NAME_1)
                        .setEvictionPriority(EvictionPriority.DEFAULT)
                        .build();
        DBProtectedSignal signal2 =
                DBProtectedSignal.builder()
                        .setId(1L)
                        .setBuyer(VALID_BUYER_1)
                        .setKey(KEY)
                        .setValue(DBProtectedSignalFixture.VALUE)
                        .setCreationTime(FIXED_NOW_TRUNCATED_TO_MILLI)
                        .setPackageName(TEST_PACKAGE_NAME_1)
                        .setEvictionPriority(EvictionPriority.DEFAULT)
                        .build();

        mEqualsTester.expectObjectsAreEqual(signal1, signal2);
    }

    @Test
    public void testEqual_differentIds() {
        DBProtectedSignal signal1 =
                DBProtectedSignal.builder()
                        .setId(1L)
                        .setBuyer(VALID_BUYER_1)
                        .setKey(KEY)
                        .setValue(DBProtectedSignalFixture.VALUE)
                        .setCreationTime(FIXED_NOW_TRUNCATED_TO_MILLI)
                        .setPackageName(TEST_PACKAGE_NAME_1)
                        .setEvictionPriority(EvictionPriority.DEFAULT)
                        .build();
        DBProtectedSignal signal2 =
                DBProtectedSignal.builder()
                        .setId(2L)
                        .setBuyer(VALID_BUYER_1)
                        .setKey(KEY)
                        .setValue(DBProtectedSignalFixture.VALUE)
                        .setCreationTime(FIXED_NOW_TRUNCATED_TO_MILLI)
                        .setPackageName(TEST_PACKAGE_NAME_1)
                        .setEvictionPriority(EvictionPriority.DEFAULT)
                        .build();

        mEqualsTester.expectObjectsAreEqual(signal1, signal2);
    }

    @Test
    public void testNotEqual() {
        DBProtectedSignal signal1 =
                DBProtectedSignal.builder()
                        .setBuyer(VALID_BUYER_1)
                        .setKey(KEY)
                        .setValue(DBProtectedSignalFixture.VALUE)
                        .setCreationTime(FIXED_NOW_TRUNCATED_TO_MILLI)
                        .setPackageName(TEST_PACKAGE_NAME_1)
                        .setEvictionPriority(EvictionPriority.DEFAULT)
                        .build();
        DBProtectedSignal signal2 =
                DBProtectedSignal.builder()
                        .setBuyer(CommonFixture.VALID_BUYER_2)
                        .setKey(KEY)
                        .setValue(DBProtectedSignalFixture.VALUE)
                        .setCreationTime(FIXED_NOW_TRUNCATED_TO_MILLI)
                        .setPackageName(TEST_PACKAGE_NAME_1)
                        .setEvictionPriority(EvictionPriority.DEFAULT)
                        .build();

        mEqualsTester.expectObjectsAreNotEqual(signal1, signal2);
    }
}

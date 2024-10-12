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

import static com.google.common.truth.Truth.assertWithMessage;

import android.adservices.common.CommonFixture;

import java.time.Duration;

public class DBProtectedSignalFixture {

    public static final byte[] KEY = {(byte) 1, (byte) 2, (byte) 3, (byte) 4};
    public static final byte[] VALUE = {(byte) 42};

    public static DBProtectedSignal.Builder getBuilder() {
        return DBProtectedSignal.builder()
                .setId(null)
                .setBuyer(CommonFixture.VALID_BUYER_1)
                .setKey(DBProtectedSignalFixture.KEY)
                .setValue(DBProtectedSignalFixture.VALUE)
                .setCreationTime(CommonFixture.FIXED_NOW_TRUNCATED_TO_MILLI)
                .setPackageName(CommonFixture.TEST_PACKAGE_NAME_1);
    }

    public static final DBProtectedSignal SIGNAL = getBuilder().build();
    public static final DBProtectedSignal SIGNAL_OTHER_BUYER =
            getBuilder().setBuyer(CommonFixture.VALID_BUYER_2).build();
    public static final DBProtectedSignal SIGNAL_OTHER_BUYER_OTHER_PACKAGE =
            getBuilder()
                    .setBuyer(CommonFixture.VALID_BUYER_2)
                    .setPackageName(CommonFixture.TEST_PACKAGE_NAME_2)
                    .build();
    public static final DBProtectedSignal LATER_TIME_SIGNAL =
            getBuilder()
                    .setCreationTime(
                            CommonFixture.FIXED_NOW_TRUNCATED_TO_MILLI.plus(Duration.ofDays(1)))
                    .build();
    public static final DBProtectedSignal LATER_TIME_SIGNAL_OTHER_BUYER =
            getBuilder()
                    .setCreationTime(
                            CommonFixture.FIXED_NOW_TRUNCATED_TO_MILLI.plus(Duration.ofDays(1)))
                    .setBuyer(CommonFixture.VALID_BUYER_2)
                    .build();

    /**
     * Asserts that the two non-null {@link DBProtectedSignal} objects are equal, as persisted in
     * the database.
     */
    public static void assertEqualsExceptId(DBProtectedSignal expected, DBProtectedSignal actual) {
        assertWithMessage("Expected DBProtectedSignal").that(expected).isNotNull();
        assertWithMessage("Actual DBProtectedSignal").that(actual).isNotNull();
        assertWithMessage("Buyer").that(actual.getBuyer()).isEqualTo(expected.getBuyer());
        assertWithMessage("Key").that(actual.getKey()).isEqualTo(expected.getKey());
        assertWithMessage("Value").that(actual.getValue()).isEqualTo(expected.getValue());
        assertWithMessage("Creation time")
                .that(actual.getCreationTime())
                .isEqualTo(expected.getCreationTime());
        assertWithMessage("Package name")
                .that(actual.getPackageName())
                .isEqualTo(expected.getPackageName());
    }
}

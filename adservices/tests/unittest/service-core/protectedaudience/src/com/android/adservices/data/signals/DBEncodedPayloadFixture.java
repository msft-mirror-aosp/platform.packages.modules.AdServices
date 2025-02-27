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

import android.adservices.common.AdTechIdentifier;
import android.adservices.common.CommonFixture;

public class DBEncodedPayloadFixture {

    public static final byte[] SAMPLE_PAYLOAD = {(byte) 10, (byte) 20, (byte) 30, (byte) 40};

    public static DBEncodedPayload anEncodedPayload() {
        return anEncodedPayloadBuilder().build();
    }

    public static DBEncodedPayload.Builder anEncodedPayloadBuilder() {
        return anEncodedPayloadBuilder(CommonFixture.VALID_BUYER_1);
    }

    public static DBEncodedPayload.Builder anEncodedPayloadBuilder(AdTechIdentifier buyer) {
        return DBEncodedPayload.builder()
                .setBuyer(buyer)
                .setVersion(1)
                .setCreationTime(CommonFixture.FIXED_NOW)
                .setEncodedPayload(SAMPLE_PAYLOAD);
    }

    /**
     * Asserts that the two non-null {@link DBEncodedPayload} objects are equal, as persisted in the
     * database.
     */
    public static void assertDBEncodedPayloadsAreEqual(
            DBEncodedPayload expected, DBEncodedPayload actual) {
        assertWithMessage("Expected DBEncodedPayload").that(expected).isNotNull();
        assertWithMessage("Actual DBEncodedPayload").that(actual).isNotNull();
        assertWithMessage("Buyer").that(actual.getBuyer()).isEqualTo(expected.getBuyer());
        assertWithMessage("Version").that(actual.getVersion()).isEqualTo(expected.getVersion());
        assertWithMessage("Creation time (in milliseconds from epoch)")
                .that(actual.getCreationTime().toEpochMilli())
                .isEqualTo(expected.getCreationTime().toEpochMilli());
        assertWithMessage("Encoded payload")
                .that(actual.getEncodedPayload())
                .isEqualTo(expected.getEncodedPayload());
    }
}

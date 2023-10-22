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

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertNotEquals;
import static org.junit.Assert.assertThrows;

import android.adservices.common.AdTechIdentifier;
import android.adservices.common.CommonFixture;

import org.junit.Test;

import java.time.Instant;

public class DBEncoderLogicMetadataTest {

    @Test
    public void testCreateEncodingLogic() {
        AdTechIdentifier buyer = CommonFixture.VALID_BUYER_1;
        int version = 1;
        Instant time = CommonFixture.FIXED_NOW;

        DBEncoderLogicMetadata logicEntry = DBEncoderLogicMetadata.create(buyer, version, time);
        assertEquals(buyer, logicEntry.getBuyer());
        assertEquals(version, (int) logicEntry.getVersion());
        assertEquals(time, logicEntry.getCreationTime());
    }

    @Test
    public void testBuildEncodingLogic() {
        AdTechIdentifier buyer = CommonFixture.VALID_BUYER_1;
        int version = 1;
        Instant time = CommonFixture.FIXED_NOW;

        DBEncoderLogicMetadata logicEntry =
                DBEncoderLogicMetadata.builder()
                        .setBuyer(buyer)
                        .setVersion(version)
                        .setCreationTime(time)
                        .build();
        assertEquals(buyer, logicEntry.getBuyer());
        assertEquals(version, (int) logicEntry.getVersion());
        assertEquals(time, logicEntry.getCreationTime());
    }

    @Test
    public void testNullFails() {
        assertThrows(
                IllegalStateException.class,
                () -> {
                    DBEncoderLogicMetadata.builder().build();
                });
    }

    @Test
    public void testEquals() {
        AdTechIdentifier buyer = CommonFixture.VALID_BUYER_1;
        int version = 1;
        Instant time = CommonFixture.FIXED_NOW;

        DBEncoderLogicMetadata logicEntry1 =
                DBEncoderLogicMetadata.builder()
                        .setBuyer(buyer)
                        .setVersion(version)
                        .setCreationTime(time)
                        .build();
        DBEncoderLogicMetadata logicEntry2 =
                DBEncoderLogicMetadata.builder()
                        .setBuyer(buyer)
                        .setVersion(version)
                        .setCreationTime(time)
                        .build();

        assertEquals(logicEntry1, logicEntry2);
    }

    @Test
    public void testNotEquals() {
        AdTechIdentifier buyer = CommonFixture.VALID_BUYER_1;
        int version = 1;
        Instant time = CommonFixture.FIXED_NOW;

        DBEncoderLogicMetadata logicEntry1 =
                DBEncoderLogicMetadata.builder()
                        .setBuyer(buyer)
                        .setVersion(version)
                        .setCreationTime(time)
                        .build();
        DBEncoderLogicMetadata logicEntry2 =
                DBEncoderLogicMetadata.builder()
                        .setBuyer(buyer)
                        .setVersion(version + 1)
                        .setCreationTime(time)
                        .build();

        assertNotEquals(logicEntry1, logicEntry2);
    }

    @Test
    public void testHashCode() {
        AdTechIdentifier buyer = CommonFixture.VALID_BUYER_1;
        int version = 1;
        Instant time = CommonFixture.FIXED_NOW;

        DBEncoderLogicMetadata logicEntry1 =
                DBEncoderLogicMetadata.builder()
                        .setBuyer(buyer)
                        .setVersion(version)
                        .setCreationTime(time)
                        .build();
        DBEncoderLogicMetadata logicEntry2 =
                DBEncoderLogicMetadata.builder()
                        .setBuyer(buyer)
                        .setVersion(version)
                        .setCreationTime(time)
                        .build();

        assertEquals(logicEntry1.hashCode(), logicEntry2.hashCode());
    }
}

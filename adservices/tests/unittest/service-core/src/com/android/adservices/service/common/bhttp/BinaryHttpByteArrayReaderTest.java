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

package com.android.adservices.service.common.bhttp;

import static com.android.adservices.service.common.bhttp.BinaryHttpTestUtil.combineSections;

import static com.google.common.truth.Truth.assertWithMessage;

import static org.junit.Assert.assertArrayEquals;
import static org.junit.Assert.assertThrows;

import com.android.adservices.common.AdServicesUnitTestCase;

import org.junit.Test;

public final class BinaryHttpByteArrayReaderTest extends AdServicesUnitTestCase {

    @Test
    public void testReadNextKnownLengthData_canReadAllComponents() {
        byte[] expectedData = new byte[] {'c'};
        BinaryHttpMessageDeserializer.BinaryHttpByteArrayReader reader =
                new BinaryHttpMessageDeserializer.BinaryHttpByteArrayReader(
                        combineSections(
                                new byte[] {0}, // Framing Indicator
                                new byte[] {0x02}, // data length
                                new byte[] {0x01}, // sub data length
                                expectedData // data
                                ));

        assertWithMessage("getFramingIndicatorByte")
                .that(reader.getFramingIndicatorByte())
                .isEqualTo(0);
        assertWithMessage("hasRemainingBytes").that(reader.hasRemainingBytes()).isTrue();
        BinaryHttpMessageDeserializer.BinaryHttpByteArrayReader subReader =
                reader.readNextKnownLengthData();
        assertWithMessage("hasRemainingBytes").that(reader.hasRemainingBytes()).isFalse();
        assertArrayEquals(combineSections(new byte[] {0x01}, expectedData), subReader.getData());
        assertWithMessage("subReader.hasRemainingBytes")
                .that(subReader.hasRemainingBytes())
                .isTrue();
        assertArrayEquals(expectedData, subReader.readNextKnownLengthData().getData());
        assertWithMessage("subReader.hasRemainingBytes")
                .that(subReader.hasRemainingBytes())
                .isFalse();
        assertThrows(IllegalArgumentException.class, subReader::readNextKnownLengthData);
    }

    @Test
    public void testReadNextKnownLengthData_notEnoughDataToRead_throwException() {
        BinaryHttpMessageDeserializer.BinaryHttpByteArrayReader reader =
                new BinaryHttpMessageDeserializer.BinaryHttpByteArrayReader(
                        combineSections(
                                new byte[] {0}, // Framing Indicator
                                new byte[] {0x01} // data length
                                ));

        assertWithMessage("getFramingIndicatorByte")
                .that(reader.getFramingIndicatorByte())
                .isEqualTo(0);
        assertWithMessage("hasRemainingBytes").that(reader.hasRemainingBytes()).isTrue();
        assertThrows(IllegalArgumentException.class, reader::readNextKnownLengthData);
    }
}

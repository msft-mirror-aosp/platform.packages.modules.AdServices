/*
 * Copyright (C) 2024 The Android Open Source Project
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

import static com.android.adservices.service.signals.HexEncodingUtil.binaryToHex;
import static com.android.adservices.service.signals.HexEncodingUtil.hexToBinary;

import static org.junit.Assert.assertEquals;

import org.junit.Test;

public class HexEncodingUtilTest {

    @Test
    public void test_binaryToHex_emptyArray_returnsEmptyString() {
        byte[] emptyArray = {};
        String hex = binaryToHex(emptyArray);

        assertEquals(0, hex.length());
        assertEquals("", hex);
    }

    @Test
    public void test_binaryToHex_oneByte_returnsProperEncoding() {
        byte[] oneByte = {(byte) 0b11111111};
        String hex = binaryToHex(oneByte);

        assertEquals(2, hex.length());
        assertEquals("FF", hex);
    }

    @Test
    public void test_binaryToHex_variousBytes_returnsProperEncoding() {
        byte[] binary = {
            (byte) 0b11111111, // FF
            (byte) 0b00000000, // 00
            (byte) 0b00001010, // 0A
            (byte) 0b00011111, // 1F
            (byte) 0b01111110, // 7E
            (byte) 0b10100101, // A5
        };
        String hex = binaryToHex(binary);

        assertEquals(12, hex.length());
        assertEquals("FF000A1F7EA5", hex);
    }

    @Test
    public void test_hexToBinary_emptyHex_returnsEmptyBinary() {
        byte[] binary = hexToBinary("");

        assertEquals(0, binary.length);
    }

    @Test
    public void test_hexToBinary_oneHexByte_returnsProperBinary() {
        byte[] expectedBinary = {(byte) 0b11111111};
        byte[] actualBinary = hexToBinary("FF");

        assertEquals(1, actualBinary.length);
        assertEquals(expectedBinary[0], actualBinary[0]);
    }

    @Test
    public void test_hexToBinary_variousBytes_returnsProperBinary() {
        byte[] expectedBinary = {
            (byte) 0b11111111, // FF
            (byte) 0b00000000, // 00
            (byte) 0b00001010, // 0A
            (byte) 0b00011111, // 1F
            (byte) 0b01111110, // 7E
            (byte) 0b10100101, // A5
        };
        byte[] actualBinary = hexToBinary("FF000A1F7EA5");

        assertEquals(6, actualBinary.length);
        for (int i = 0; i < 6; i++) {
            assertEquals(expectedBinary[i], actualBinary[i]);
        }
    }
}

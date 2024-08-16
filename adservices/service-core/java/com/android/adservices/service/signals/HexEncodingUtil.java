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

/** Utilities for binary encoding */
public class HexEncodingUtil {

    private static final char[] sHexCharacters = {
        '0', '1', '2', '3', '4', '5', '6', '7',
        '8', '9', 'A', 'B', 'C', 'D', 'E', 'F'
    };

    /** Encodes the {@code binary} into a Hex string */
    public static String binaryToHex(byte[] binary) {
        char[] result = new char[binary.length * 2];

        int i = 0;
        for (byte b : binary) {
            int leftBits = (b & 0b11110000) >> 4;
            int rightBits = b & 0b00001111;

            result[i++] = sHexCharacters[leftBits];
            result[i++] = sHexCharacters[rightBits];
        }

        return new String(result);
    }

    /** Decodes a Hex string into binary. The characters are expected to be in uppercase. */
    public static byte[] hexToBinary(String hex) {
        byte[] result = new byte[hex.length() / 2];

        for (int i = 0; i < hex.length(); i += 2) {
            char leftBitsChar = hex.charAt(i);
            char rightBitsChar = hex.charAt(i + 1);

            int leftBits = indexOf(leftBitsChar);
            int rightBits = indexOf(rightBitsChar);

            result[i / 2] = (byte) ((leftBits << 4) | rightBits);
        }

        return result;
    }

    private static int indexOf(char item) {
        for (int i = 0; i < sHexCharacters.length; i++) {
            if (sHexCharacters[i] == item) {
                return i;
            }
        }
        return -1;
    }
}

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

package com.android.adservices.service.topics;

import androidx.annotation.NonNull;

import com.android.adservices.HpkeJni;
import com.android.adservices.LoggerFactory;

import java.util.Arrays;
import java.util.Objects;

/**
 * One shot implementation of HPKE(Hybrid Public Key Encryption). This particular implementation is
 * referred to as “curve25519”, but “X25519” is a more precise name.
 */
public class HpkeEncrypter implements Encrypter {
    private static final LoggerFactory.Logger sLogger = LoggerFactory.getTopicsLogger();

    private static final byte[] EMPTY_BYTE_ARRAY = new byte[] {};

    private static final int X25519_PUBLIC_VALUE_LEN = 32;

    /**
     * Encrypt a byte string using HPKE one shot algorithm.
     *
     * @param publicKey the public key used for encryption
     * @param plainText the plain text string to encrypt
     * @param contextInfo HPKE context info used for encryption
     * @return the encrypted ciphertext
     */
    @Override
    public byte[] encrypt(
            @NonNull byte[] publicKey, @NonNull byte[] plainText, @NonNull byte[] contextInfo) {
        Objects.requireNonNull(publicKey);
        Objects.requireNonNull(plainText);
        Objects.requireNonNull(contextInfo);

        if (!isValidKey(publicKey)) {
            return EMPTY_BYTE_ARRAY;
        }

        return HpkeJni.encrypt(publicKey, plainText, contextInfo);
    }

    // Return true if key is compatible with the supported HPKE implementation.
    private boolean isValidKey(byte[] publicKey) {
        // Check if the public key length matches the X25519 public key requirement.
        if (publicKey.length != X25519_PUBLIC_VALUE_LEN) {
            sLogger.d(
                    "Invalid HPKE public key = %s. Expected public key length of %d, got %d.",
                    Arrays.toString(publicKey), X25519_PUBLIC_VALUE_LEN, publicKey.length);
            // TODO(b/310699530): Add CEL here to Topics encryption
            return false;
        }
        return true;
    }
}

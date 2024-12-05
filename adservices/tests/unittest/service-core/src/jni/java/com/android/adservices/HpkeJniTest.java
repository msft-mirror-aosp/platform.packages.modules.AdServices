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

package com.android.adservices;

import static com.google.common.truth.Truth.assertThat;

import com.android.adservices.common.AdServicesUnitTestCase;
import com.android.adservices.service.measurement.aggregation.AggregateCryptoFixture;

import org.junit.Test;

import java.util.Base64;

public final class HpkeJniTest extends AdServicesUnitTestCase {

    private static final byte[] sAssociatedData = "associated_data".getBytes();
    private static final byte[] sPlaintext = "plaintext".getBytes();
    private static final byte[] sCiphertext =
            decode("0Ie+jDZ/Hznx1IrIkS06V+kAHuD5RsybXWwrKRIbGEL5TJT4/HYny2SHfWbeXxMydwvS0FEZqvzs");
    private static final byte[] sPublicKey = AggregateCryptoFixture.getPublicKey();
    private static final byte[] sPrivateKey = AggregateCryptoFixture.getPrivateKey();

    @Test
    public void testHpkeEncrypt_Success() {
        byte[] result = HpkeJni.encrypt(sPublicKey, sPlaintext, sAssociatedData);
        assertThat(result).isNotNull();
        assertThat(result.length).isGreaterThan(0);
    }

    @Test
    public void testHpkeDecrypt_Success() {
        byte[] result = HpkeJni.decrypt(sPrivateKey, sCiphertext, sAssociatedData);
        assertThat(result).isNotNull();
        assertThat(result.length).isGreaterThan(0);
        assertThat(new String(result)).isEqualTo(new String(sPlaintext));
    }

    @Test
    public void testHpkeEncryptDecrypt_Success() {
        byte[] ciphertext = HpkeJni.encrypt(sPublicKey, sPlaintext, sAssociatedData);
        assertThat(ciphertext).isNotNull();
        assertThat(ciphertext.length).isGreaterThan(0);
        assertThat(new String(ciphertext)).isNotEqualTo(new String(sPlaintext));

        byte[] plaintext = HpkeJni.decrypt(sPrivateKey, ciphertext, sAssociatedData);
        assertThat(plaintext).isNotNull();
        assertThat(plaintext.length).isGreaterThan(0);
        assertThat(new String(plaintext)).isEqualTo(new String(sPlaintext));
    }

    @Test
    public void testHpkeEncrypt_publicKeyNull_fail() {
        byte[] result = HpkeJni.encrypt(/* publicKey= */ null, sPlaintext, sAssociatedData);
        assertThat(result).isNull();
    }

    @Test
    public void testHpkeEncrypt_publicKeyShorterThan32_fail() {
        byte[] shortPublicKey = new byte[31];
        byte[] result = HpkeJni.encrypt(shortPublicKey, sPlaintext, sAssociatedData);
        assertThat(result).isNull();
    }

    @Test
    public void testHpkeEncrypt_publicKeyLongerThan32_fail() {
        byte[] longPublicKey = new byte[33];
        byte[] result = HpkeJni.encrypt(longPublicKey, sPlaintext, sAssociatedData);
        assertThat(result).isNull();
    }

    @Test
    public void testHpkeEncrypt_plainTextNull_fail() {
        byte[] result = HpkeJni.encrypt(sPublicKey, /* plainText= */ null, sAssociatedData);
        assertThat(result).isNull();
    }

    @Test
    public void testHpkeEncrypt_plainTextEmpty_success() {
        byte[] emptyPlainText = new byte[] {};
        byte[] result = HpkeJni.encrypt(sPublicKey, emptyPlainText, sAssociatedData);
        assertThat(result).isNotNull();
        assertThat(result.length).isGreaterThan(0);
    }

    @Test
    public void testHpkeEncrypt_associatedDataNull_fail() {
        byte[] result = HpkeJni.encrypt(sPublicKey, sPlaintext, /* associatedData= */ null);
        assertThat(result).isNull();
    }

    @Test
    public void testHpkeEncrypt_associatedDataEmpty_success() {
        byte[] emptyAssociatedData = new byte[] {};
        byte[] result = HpkeJni.encrypt(sPublicKey, sPlaintext, emptyAssociatedData);
        assertThat(result).isNotNull();
        assertThat(result.length).isGreaterThan(0);
    }

    @Test
    public void testHpkeDecrypt_privateKeyNull_fail() {
        byte[] result = HpkeJni.decrypt(/* privateKey= */ null, sCiphertext, sAssociatedData);
        assertThat(result).isNull();
    }

    @Test
    public void testHpkDecrypt_privateKeyShorterThan32_fail() {
        byte[] shortPrivateKey = new byte[31];
        byte[] result = HpkeJni.decrypt(shortPrivateKey, sCiphertext, sAssociatedData);
        assertThat(result).isNull();
    }

    @Test
    public void testHpkeDecrypt_privateKeyLargerThan32_fail() {
        byte[] longPrivateKey = new byte[33];
        byte[] result = HpkeJni.decrypt(longPrivateKey, sCiphertext, sAssociatedData);
        assertThat(result).isNull();
    }

    @Test
    public void testHpkeDecrypt_privateKeyInvalid_fail() {
        byte[] privateKey = new byte[32];
        byte[] result = HpkeJni.decrypt(privateKey, sCiphertext, sAssociatedData);
        assertThat(result).isNull();
    }

    @Test
    public void testHpkeDecrypt_ciphertextNull_fail() {
        byte[] result = HpkeJni.encrypt(sPrivateKey, /* ciphertext= */ null, sAssociatedData);
        assertThat(result).isNull();
    }

    @Test
    public void testHpkeDecrypt_ciphertextInvalid_fail() {
        byte[] emptyCiphertext = new byte[] {};
        byte[] result = HpkeJni.decrypt(sPrivateKey, emptyCiphertext, sAssociatedData);
        assertThat(result).isNull();
    }

    @Test
    public void testHpkeDecrypt_associatedDataNull_fail() {
        byte[] result = HpkeJni.decrypt(sPrivateKey, sCiphertext, /* associatedData= */ null);
        assertThat(result).isNull();
    }

    private static byte[] decode(String value) {
        return Base64.getDecoder().decode(value.getBytes());
    }
}

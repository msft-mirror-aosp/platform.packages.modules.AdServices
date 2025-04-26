/*
 * Copyright (C) 2025 The Android Open Source Project
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

package com.android.adservices.service.common.crypto;

import static com.android.adservices.service.common.crypto.AdServicesHpke.isHpkeAvailable;
import static com.android.adservices.service.stats.AdServicesStatsLog.AD_SERVICES_ERROR_REPORTED__ERROR_CODE__HPKE_DECRYPTION_EXCEPTION;
import static com.android.adservices.service.stats.AdServicesStatsLog.AD_SERVICES_ERROR_REPORTED__ERROR_CODE__HPKE_INVALID_KEY;
import static com.android.adservices.service.stats.AdServicesStatsLog.AD_SERVICES_ERROR_REPORTED__PPAPI_NAME__COMMON;
import static com.android.dx.mockito.inline.extended.ExtendedMockito.doReturn;

import static com.google.common.truth.Truth.assertThat;

import com.android.adservices.common.AdServicesExtendedMockitoTestCase;
import com.android.adservices.common.logging.annotations.ExpectErrorLogUtilWithExceptionCall;
import com.android.adservices.service.FlagsFactory;
import com.android.adservices.service.measurement.aggregation.AggregateCryptoFixture;
import com.android.modules.utils.testing.ExtendedMockitoRule.SpyStatic;

import org.junit.Assume;
import org.junit.Before;
import org.junit.Test;

import java.security.spec.InvalidKeySpecException;
import java.util.Base64;

@SpyStatic(FlagsFactory.class)
@SpyStatic(AdServicesHpke.class)
public final class AdServicesHpkeTest extends AdServicesExtendedMockitoTestCase {
    private static final byte[] sInfo = "associated_data".getBytes();
    private static final byte[] sPlaintext = "plaintext".getBytes();
    private static final byte[] sCiphertext =
            Base64.getDecoder()
                    .decode(
                            ("0Ie+jDZ/Hznx1IrIkS06V+kAHuD5RsybXWwrKRIbGEL5TJT"
                                            + "4/HYny2SHfWbeXxMydwvS0FEZqvzs")
                                    .getBytes());
    private static final byte[] sPublicKey = AggregateCryptoFixture.getPublicKey();
    private static final byte[] sPrivateKey = AggregateCryptoFixture.getPrivateKey();

    @Before
    public void setUp() {
        // Skip the test if it runs on unsupported platforms.
        Assume.assumeTrue(isHpkeAvailable());

        mocker.mockGetFlags(mMockFlags);
        featureFlagEncryptWithPlatformApis(true);
    }

    @Test
    public void testHpkeEncryptWithPlatform_success() {
        assertHpkeEncrypt();
    }

    @Test
    public void testHpkeEncryptWithJni_success() {
        featureFlagEncryptWithPlatformApis(false);
        assertHpkeEncrypt();
    }

    private void assertHpkeEncrypt() {
        byte[] result = AdServicesHpke.encrypt(sPublicKey, sPlaintext, sInfo);
        assertThat(result).isNotNull();
        assertThat(result.length).isGreaterThan(0);
    }

    @Test
    public void testHpkeDecryptWithPlatform_success() {
        assertHpkeDecrypt();
    }

    @Test
    public void testHpkeDecryptWithJni_success() {
        featureFlagEncryptWithPlatformApis(false);
        assertHpkeDecrypt();
    }

    private void assertHpkeDecrypt() {
        byte[] result = AdServicesHpke.decrypt(sPrivateKey, sCiphertext, sInfo);
        assertThat(result).isNotNull();
        assertThat(result.length).isGreaterThan(0);
        assertThat(new String(result)).isEqualTo(new String(sPlaintext));
    }

    @Test
    public void testHpkeEncryptDecryptWithPlatform_success() {
        assertHpkeEncryptDecrypt();
    }

    @Test
    public void testHpkeEncryptDecryptWithJni_success() {
        featureFlagEncryptWithPlatformApis(false);
        assertHpkeEncryptDecrypt();
    }

    private void assertHpkeEncryptDecrypt() {
        byte[] ciphertext = AdServicesHpke.encrypt(sPublicKey, sPlaintext, sInfo);
        assertThat(ciphertext).isNotNull();
        assertThat(ciphertext.length).isGreaterThan(0);
        assertThat(new String(ciphertext)).isNotEqualTo(new String(sPlaintext));

        byte[] plaintext = AdServicesHpke.decrypt(sPrivateKey, ciphertext, sInfo);
        assertThat(plaintext).isNotNull();
        assertThat(plaintext.length).isGreaterThan(0);
        assertThat(new String(plaintext)).isEqualTo(new String(sPlaintext));
    }

    @Test
    public void testHpkeEncryptDecryptWithPlatform_emptyBytes_success() {
        assertHpkeEncryptDecryptEmptyBytes();
    }

    @Test
    public void testHpkeEncryptDecryptWithJni_emptyBytes_success() {
        featureFlagEncryptWithPlatformApis(false);
        assertHpkeEncryptDecryptEmptyBytes();
    }

    private void assertHpkeEncryptDecryptEmptyBytes() {
        byte[] emptyBytes = new byte[0];
        byte[] ciphertext = AdServicesHpke.encrypt(sPublicKey, emptyBytes, sInfo);
        assertThat(ciphertext).isNotNull();
        assertThat(ciphertext.length).isGreaterThan(0);
        assertThat(ciphertext).isNotEqualTo(emptyBytes);

        byte[] plaintext = AdServicesHpke.decrypt(sPrivateKey, ciphertext, sInfo);
        assertThat(plaintext).isNotNull();
        assertThat(plaintext.length).isEqualTo(0);
        assertThat(plaintext).isEqualTo(emptyBytes);
    }

    @Test
    @ExpectErrorLogUtilWithExceptionCall(
            throwable = InvalidKeySpecException.class,
            errorCode = AD_SERVICES_ERROR_REPORTED__ERROR_CODE__HPKE_INVALID_KEY,
            ppapiName = AD_SERVICES_ERROR_REPORTED__PPAPI_NAME__COMMON)
    public void testHpkeEncryptWithPlatformApis_publicKeyNull_fail() {
        assertEncryptionFailureWithoutPublicKey();
    }

    @Test
    public void testHpkeEncryptWithJni_publicKeyNull_fail() {
        featureFlagEncryptWithPlatformApis(false);
        assertEncryptionFailureWithoutPublicKey();
    }

    private void assertEncryptionFailureWithoutPublicKey() {
        byte[] result = AdServicesHpke.encrypt(/* publicKey= */ null, sPlaintext, sInfo);
        assertThat(result).isNull();
    }

    @Test
    @ExpectErrorLogUtilWithExceptionCall(
            throwable = InvalidKeySpecException.class,
            errorCode = AD_SERVICES_ERROR_REPORTED__ERROR_CODE__HPKE_INVALID_KEY,
            ppapiName = AD_SERVICES_ERROR_REPORTED__PPAPI_NAME__COMMON)
    public void testHpkeEncryptWithPlatformApis_publicKeyShorterThan32_fail() {
        assertEncryptionFailureWithPublicKeyShorterThan32();
    }

    @Test
    public void testHpkeEncryptWithJni_publicKeyShorterThan32_fail() {
        featureFlagEncryptWithPlatformApis(false);
        assertEncryptionFailureWithPublicKeyShorterThan32();
    }

    private void assertEncryptionFailureWithPublicKeyShorterThan32() {
        byte[] shortPublicKey = new byte[31];
        byte[] result = AdServicesHpke.encrypt(shortPublicKey, sPlaintext, sInfo);
        assertThat(result).isNull();
    }

    @Test
    @ExpectErrorLogUtilWithExceptionCall(
            throwable = InvalidKeySpecException.class,
            errorCode = AD_SERVICES_ERROR_REPORTED__ERROR_CODE__HPKE_INVALID_KEY,
            ppapiName = AD_SERVICES_ERROR_REPORTED__PPAPI_NAME__COMMON)
    public void testHpkeEncryptWithPlatformApis_publicKeyLongerThan32_fail() {
        assertEncryptionFailureWithPublicKeyLongerThan32();
    }

    @Test
    public void testHpkeEncryptWithJni_publicKeyLongerThan32_fail() {
        featureFlagEncryptWithPlatformApis(false);
        assertEncryptionFailureWithPublicKeyLongerThan32();
    }

    private void assertEncryptionFailureWithPublicKeyLongerThan32() {
        byte[] longPublicKey = new byte[33];
        byte[] result = AdServicesHpke.encrypt(longPublicKey, sPlaintext, sInfo);
        assertThat(result).isNull();
    }

    @Test
    public void testHpkeEncryptWithPlatformApis_plaintextNull_returnNull() {
        assertNullCiphertextOnNullPlaintext();
    }

    @Test
    public void testHpkeEncryptWithJni_plaintextNull_returnNull() {
        featureFlagEncryptWithPlatformApis(false);
        assertNullCiphertextOnNullPlaintext();
    }

    private void assertNullCiphertextOnNullPlaintext() {
        byte[] result = AdServicesHpke.encrypt(sPublicKey, /* plaintext= */ null, sInfo);
        assertThat(result).isNull();
    }

    @Test
    public void testHpkeEncryptWithPlatformApis_plaintextEmpty_success() {
        assertEncryptionOnEmptyPlaintext();
    }

    @Test
    public void testHpkeEncryptWithJni_plaintextEmpty_success() {
        featureFlagEncryptWithPlatformApis(false);
        assertEncryptionOnEmptyPlaintext();
    }

    private void assertEncryptionOnEmptyPlaintext() {
        byte[] emptyPlainText = new byte[] {};
        byte[] result = AdServicesHpke.encrypt(sPublicKey, emptyPlainText, sInfo);
        assertThat(result).isNotNull();
        assertThat(result.length).isGreaterThan(0);
    }

    @Test
    public void testHpkeEncryptWithPlatformApis_infoEmpty_success() {
        assertEncryptionWithEmptyInfo();
    }

    @Test
    public void testHpkeEncryptWithJni_infoEmpty_success() {
        featureFlagEncryptWithPlatformApis(false);
        assertEncryptionWithEmptyInfo();
    }

    private void assertEncryptionWithEmptyInfo() {
        byte[] emptyInfo = new byte[] {};
        byte[] result = AdServicesHpke.encrypt(sPublicKey, sPlaintext, emptyInfo);
        assertThat(result).isNotNull();
        assertThat(result.length).isGreaterThan(0);
    }

    @Test
    @ExpectErrorLogUtilWithExceptionCall(
            throwable = InvalidKeySpecException.class,
            errorCode = AD_SERVICES_ERROR_REPORTED__ERROR_CODE__HPKE_INVALID_KEY,
            ppapiName = AD_SERVICES_ERROR_REPORTED__PPAPI_NAME__COMMON)
    public void testHpkeDecryptWithPlatformApis_privateKeyNull_fail() {
        assertDecryptionWithoutPrivateKeys();
    }

    @Test
    public void testHpkeDecryptWithJni_privateKeyNull_fail() {
        featureFlagEncryptWithPlatformApis(false);
        assertDecryptionWithoutPrivateKeys();
    }

    private void assertDecryptionWithoutPrivateKeys() {
        byte[] result = AdServicesHpke.decrypt(/* privateKey= */ null, sCiphertext, sInfo);
        assertThat(result).isNull();
    }

    @Test
    @ExpectErrorLogUtilWithExceptionCall(
            throwable = InvalidKeySpecException.class,
            errorCode = AD_SERVICES_ERROR_REPORTED__ERROR_CODE__HPKE_INVALID_KEY,
            ppapiName = AD_SERVICES_ERROR_REPORTED__PPAPI_NAME__COMMON)
    public void testHpkDecryptWithPlatformApis_privateKeyShorterThan32_fail() {
        assertDecryptionWithPrivateKeysShorterThan32Bytes();
    }

    @Test
    public void testHpkDecryptWithJni_privateKeyShorterThan32_fail() {
        featureFlagEncryptWithPlatformApis(false);
        assertDecryptionWithPrivateKeysShorterThan32Bytes();
    }

    private void assertDecryptionWithPrivateKeysShorterThan32Bytes() {
        byte[] shortPrivateKey = new byte[31];
        byte[] result = AdServicesHpke.decrypt(shortPrivateKey, sCiphertext, sInfo);
        assertThat(result).isNull();
    }

    @Test
    @ExpectErrorLogUtilWithExceptionCall(
            throwable = InvalidKeySpecException.class,
            errorCode = AD_SERVICES_ERROR_REPORTED__ERROR_CODE__HPKE_INVALID_KEY,
            ppapiName = AD_SERVICES_ERROR_REPORTED__PPAPI_NAME__COMMON)
    public void testHpkeDecryptWithPlatformApis_privateKeyLongerThan32_fail() {
        assertDecryptionWithPrivateKeysLongerThan32Bytes();
    }

    @Test
    public void testHpkeDecryptWithJni_privateKeyLongerThan32_fail() {
        featureFlagEncryptWithPlatformApis(false);
        assertDecryptionWithPrivateKeysLongerThan32Bytes();
    }

    private void assertDecryptionWithPrivateKeysLongerThan32Bytes() {
        byte[] longPrivateKey = new byte[33];
        byte[] result = AdServicesHpke.decrypt(longPrivateKey, sCiphertext, sInfo);
        assertThat(result).isNull();
    }

    @Test
    @ExpectErrorLogUtilWithExceptionCall(
            throwable = ExpectErrorLogUtilWithExceptionCall.Any.class,
            errorCode = AD_SERVICES_ERROR_REPORTED__ERROR_CODE__HPKE_DECRYPTION_EXCEPTION,
            ppapiName = AD_SERVICES_ERROR_REPORTED__PPAPI_NAME__COMMON)
    public void testHpkeDecryptWithPlatformApis_privateKeyValidButWrongKey_fail() {
        assertDecryptionWithWrongPrivateKey();
    }

    @Test
    public void testHpkeDecryptWithJni_privateKeyValidButWrongKey_fail() {
        featureFlagEncryptWithPlatformApis(false);
        assertDecryptionWithWrongPrivateKey();
    }

    private void assertDecryptionWithWrongPrivateKey() {
        byte[] privateKey = new byte[32];
        byte[] result = AdServicesHpke.decrypt(privateKey, sCiphertext, sInfo);
        assertThat(result).isNull();
    }

    @Test
    public void testHpkeDecryptWithPlatformApis_ciphertextNull_returnNull() {
        assertDecryptionWithNullCiphertext();
    }

    @Test
    public void testHpkeDecryptWithJni_ciphertextNull_returnNull() {
        featureFlagEncryptWithPlatformApis(false);
        assertDecryptionWithNullCiphertext();
    }

    private void assertDecryptionWithNullCiphertext() {
        byte[] result = AdServicesHpke.encrypt(sPrivateKey, /* plaintext= */ null, sInfo);
        assertThat(result).isNull();
    }

    @Test
    public void testHpkeDecryptWithPlatformApis_ciphertextInvalid_fail() {
        assertDecryptionWithInvalidCiphertext();
    }

    @Test
    public void testHpkeDecryptWithJni_ciphertextInvalid_fail() {
        featureFlagEncryptWithPlatformApis(false);
        assertDecryptionWithInvalidCiphertext();
    }

    private void assertDecryptionWithInvalidCiphertext() {
        byte[] emptyCiphertext = new byte[] {};
        byte[] result = AdServicesHpke.decrypt(sPrivateKey, emptyCiphertext, sInfo);
        assertThat(result).isNull();
    }

    @Test
    @ExpectErrorLogUtilWithExceptionCall(
            throwable = ExpectErrorLogUtilWithExceptionCall.Any.class,
            errorCode = AD_SERVICES_ERROR_REPORTED__ERROR_CODE__HPKE_DECRYPTION_EXCEPTION,
            ppapiName = AD_SERVICES_ERROR_REPORTED__PPAPI_NAME__COMMON)
    public void testHpkeDecryptWithPlatformApis_infoNotMatchingEncryptedInfo_fail() {
        assertDecryptionWithInvalidInfo();
    }

    @Test
    public void testHpkeDecryptWithJni_infoNotMatchingEncryptedInfo_fail() {
        featureFlagEncryptWithPlatformApis(false);
        assertDecryptionWithInvalidInfo();
    }

    private void assertDecryptionWithInvalidInfo() {
        byte[] result = AdServicesHpke.decrypt(sPrivateKey, sCiphertext, /* info= */ sCiphertext);
        assertThat(result).isNull();
    }

    private void featureFlagEncryptWithPlatformApis(boolean value) {
        doReturn(value).when(mMockFlags).getEnableHpkeWithPlatformApis();
    }
}

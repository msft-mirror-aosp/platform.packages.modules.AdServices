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

package com.android.adservices.download;

import static com.android.adservices.common.logging.annotations.ExpectErrorLogUtilWithExceptionCall.Any;
import static com.android.adservices.download.EncryptionKeyConverterUtil.createEncryptionKeyFromJson;
import static com.android.adservices.service.stats.AdServicesStatsLog.AD_SERVICES_ERROR_REPORTED__ERROR_CODE__ENCRYPTION_KEYS_INCORRECT_JSON_VERSION;
import static com.android.adservices.service.stats.AdServicesStatsLog.AD_SERVICES_ERROR_REPORTED__ERROR_CODE__ENCRYPTION_KEYS_JSON_PARSING_ERROR;
import static com.android.adservices.service.stats.AdServicesStatsLog.AD_SERVICES_ERROR_REPORTED__PPAPI_NAME__COMMON;

import static com.google.common.truth.Truth.assertThat;

import static java.util.Map.entry;

import android.net.Uri;

import com.android.adservices.common.AdServicesExtendedMockitoTestCase;
import com.android.adservices.common.logging.annotations.ExpectErrorLogUtilCall;
import com.android.adservices.common.logging.annotations.ExpectErrorLogUtilWithExceptionCall;
import com.android.adservices.common.logging.annotations.SetErrorLogUtilDefaultParams;
import com.android.adservices.service.encryptionkey.EncryptionKey;

import org.json.JSONObject;
import org.junit.Test;

import java.util.Map;
import java.util.Optional;

/** Tests for {@link EncryptionKeyConverterUtil}. */
@SetErrorLogUtilDefaultParams(
        throwable = Any.class,
        ppapiName = AD_SERVICES_ERROR_REPORTED__PPAPI_NAME__COMMON)
public final class EncryptionKeyConverterUtilTest extends AdServicesExtendedMockitoTestCase {
    private static final Map SIGNING_V3_DEFAULT_VALUES =
            Map.ofEntries(
                    entry("keyType", "Signing"),
                    entry("enrollmentId", "TEST0"),
                    entry("reportingOrigin", "https://adtech.example.com"),
                    entry(
                            "encryptionKeyUrl",
                            "https://adtech.example.com/.well-knonwn/encryption-keys"),
                    entry("protocolType", "ECDSA"),
                    entry("keyId", 98765),
                    entry("body", "MIIBCgKCAQEAwVG1qA=="),
                    entry("expiration", 1682516522000L),
                    entry("version", 3));

    @Test
    public void createEncryptionKeyFromJson_signing_successVersion3() {
        Optional<EncryptionKey> keyOptional =
                createEncryptionKeyFromJson(new JSONObject(SIGNING_V3_DEFAULT_VALUES));

        assertThat(keyOptional.isPresent()).isTrue();
        EncryptionKey encryptionKey = keyOptional.get();
        expect.that(encryptionKey.getKeyType()).isEqualTo(EncryptionKey.KeyType.SIGNING);
        expect.that(encryptionKey.getEnrollmentId()).isEqualTo("TEST0");
        expect.that(encryptionKey.getReportingOrigin())
                .isEqualTo(Uri.parse("https://adtech.example.com"));
        expect.that(encryptionKey.getEncryptionKeyUrl())
                .isEqualTo("https://adtech.example.com/.well-knonwn/encryption-keys");
        expect.that(encryptionKey.getProtocolType()).isEqualTo(EncryptionKey.ProtocolType.ECDSA);
        expect.that(encryptionKey.getKeyCommitmentId()).isEqualTo(98765);
        expect.that(encryptionKey.getBody()).isEqualTo("MIIBCgKCAQEAwVG1qA==");
        expect.that(encryptionKey.getExpiration()).isEqualTo(1682516522000L);
    }

    @Test
    public void createEncryptionKeyFromJson_encryption_successVersion3() throws Exception {
        JSONObject jsonObject = new JSONObject(SIGNING_V3_DEFAULT_VALUES);
        jsonObject.put("keyType", "Encryption");
        jsonObject.put("protocolType", "HPKE");

        Optional<EncryptionKey> keyOptional = createEncryptionKeyFromJson(jsonObject);

        assertThat(keyOptional.isPresent()).isTrue();
        EncryptionKey encryptionKey = keyOptional.get();
        expect.that(encryptionKey.getKeyType()).isEqualTo(EncryptionKey.KeyType.ENCRYPTION);
        expect.that(encryptionKey.getEnrollmentId()).isEqualTo("TEST0");
        expect.that(encryptionKey.getReportingOrigin())
                .isEqualTo(Uri.parse("https://adtech.example.com"));
        expect.that(encryptionKey.getEncryptionKeyUrl())
                .isEqualTo("https://adtech.example.com/.well-knonwn/encryption-keys");
        expect.that(encryptionKey.getProtocolType()).isEqualTo(EncryptionKey.ProtocolType.HPKE);
        expect.that(encryptionKey.getKeyCommitmentId()).isEqualTo(98765);
        expect.that(encryptionKey.getBody()).isEqualTo("MIIBCgKCAQEAwVG1qA==");
        expect.that(encryptionKey.getExpiration()).isEqualTo(1682516522000L);
        expect.that(encryptionKey.getLastFetchTime()).isGreaterThan(0);
    }

    @Test
    @ExpectErrorLogUtilCall(
            errorCode =
                    AD_SERVICES_ERROR_REPORTED__ERROR_CODE__ENCRYPTION_KEYS_INCORRECT_JSON_VERSION)
    public void createEncryptionKeyFromJson_incorrectVersion() throws Exception {
        JSONObject jsonObject = new JSONObject(SIGNING_V3_DEFAULT_VALUES);
        jsonObject.put("version", 2);

        Optional<EncryptionKey> keyOptional = createEncryptionKeyFromJson(jsonObject);

        expect.that(keyOptional.isPresent()).isFalse();
    }

    @Test
    @ExpectErrorLogUtilWithExceptionCall(
            errorCode = AD_SERVICES_ERROR_REPORTED__ERROR_CODE__ENCRYPTION_KEYS_JSON_PARSING_ERROR)
    public void createEncryptionKeyFromJson_incorrectKeyType() throws Exception {
        JSONObject jsonObject = new JSONObject(SIGNING_V3_DEFAULT_VALUES);
        jsonObject.put("keyType", "InvalidKeyType");

        Optional<EncryptionKey> keyOptional = createEncryptionKeyFromJson(jsonObject);

        expect.that(keyOptional.isPresent()).isFalse();
    }

    @Test
    @ExpectErrorLogUtilWithExceptionCall(
            errorCode = AD_SERVICES_ERROR_REPORTED__ERROR_CODE__ENCRYPTION_KEYS_JSON_PARSING_ERROR)
    public void createEncryptionKeyFromJson_incorrectProtocolType() throws Exception {
        JSONObject jsonObject = new JSONObject(SIGNING_V3_DEFAULT_VALUES);
        jsonObject.put("protocolType", "HASH128");

        Optional<EncryptionKey> keyOptional = createEncryptionKeyFromJson(jsonObject);

        expect.that(keyOptional.isPresent()).isFalse();
    }

    @Test
    @ExpectErrorLogUtilWithExceptionCall(
            errorCode = AD_SERVICES_ERROR_REPORTED__ERROR_CODE__ENCRYPTION_KEYS_JSON_PARSING_ERROR)
    public void createEncryptionKeyFromJson_incorrectKeyIdFormat() throws Exception {
        JSONObject jsonObject = new JSONObject(SIGNING_V3_DEFAULT_VALUES);
        jsonObject.put("keyId", "abc");

        Optional<EncryptionKey> keyOptional = createEncryptionKeyFromJson(jsonObject);

        expect.that(keyOptional.isPresent()).isFalse();
    }

    @Test
    @ExpectErrorLogUtilWithExceptionCall(
            errorCode = AD_SERVICES_ERROR_REPORTED__ERROR_CODE__ENCRYPTION_KEYS_JSON_PARSING_ERROR)
    public void createEncryptionKeyFromJson_incorrectExpirationFormat() throws Exception {
        JSONObject jsonObject = new JSONObject(SIGNING_V3_DEFAULT_VALUES);
        jsonObject.put("expiration", " ");

        Optional<EncryptionKey> keyOptional = createEncryptionKeyFromJson(jsonObject);

        expect.that(keyOptional.isPresent()).isFalse();
    }

    @Test
    @ExpectErrorLogUtilWithExceptionCall(
            errorCode = AD_SERVICES_ERROR_REPORTED__ERROR_CODE__ENCRYPTION_KEYS_JSON_PARSING_ERROR)
    public void createEncryptionKeyFromJson_missingFields() {
        JSONObject jsonObject = new JSONObject(SIGNING_V3_DEFAULT_VALUES);
        jsonObject.remove("expiration");

        Optional<EncryptionKey> keyOptional = createEncryptionKeyFromJson(jsonObject);

        expect.that(keyOptional.isPresent()).isFalse();
    }
}

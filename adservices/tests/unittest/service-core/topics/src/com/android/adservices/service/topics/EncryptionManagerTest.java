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

import static com.android.adservices.common.logging.annotations.ExpectErrorLogUtilWithExceptionCall.Any;
import static com.android.adservices.service.stats.AdServicesStatsLog.AD_SERVICES_ERROR_REPORTED__ERROR_CODE__TOPICS_ENCRYPTION_INVALID_KEY_LENGTH;
import static com.android.adservices.service.stats.AdServicesStatsLog.AD_SERVICES_ERROR_REPORTED__ERROR_CODE__TOPICS_ENCRYPTION_INVALID_RESPONSE_LENGTH;
import static com.android.adservices.service.stats.AdServicesStatsLog.AD_SERVICES_ERROR_REPORTED__ERROR_CODE__TOPICS_ENCRYPTION_KEY_DECODE_FAILURE;
import static com.android.adservices.service.stats.AdServicesStatsLog.AD_SERVICES_ERROR_REPORTED__ERROR_CODE__TOPICS_ENCRYPTION_KEY_MISSING;
import static com.android.adservices.service.stats.AdServicesStatsLog.AD_SERVICES_ERROR_REPORTED__ERROR_CODE__TOPICS_ENCRYPTION_NULL_RESPONSE;
import static com.android.adservices.service.stats.AdServicesStatsLog.AD_SERVICES_ERROR_REPORTED__PPAPI_NAME__TOPICS;
import static com.android.adservices.service.topics.TopicsJsonMapper.KEY_MODEL_VERSION;
import static com.android.adservices.service.topics.TopicsJsonMapper.KEY_TAXONOMY_VERSION;
import static com.android.adservices.service.topics.TopicsJsonMapper.KEY_TOPIC_ID;

import static com.google.common.truth.Truth.assertThat;

import static org.junit.Assert.assertThrows;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.when;

import android.net.Uri;

import com.android.adservices.HpkeJni;
import com.android.adservices.common.AdServicesExtendedMockitoTestCase;
import com.android.adservices.common.logging.annotations.ExpectErrorLogUtilCall;
import com.android.adservices.common.logging.annotations.ExpectErrorLogUtilWithExceptionCall;
import com.android.adservices.common.logging.annotations.SetErrorLogUtilDefaultParams;
import com.android.adservices.data.encryptionkey.EncryptionKeyDao;
import com.android.adservices.data.enrollment.EnrollmentDao;
import com.android.adservices.data.topics.EncryptedTopic;
import com.android.adservices.data.topics.Topic;
import com.android.adservices.service.encryptionkey.EncryptionKey;
import com.android.adservices.service.enrollment.EnrollmentData;

import com.google.common.primitives.Bytes;

import org.json.JSONException;
import org.json.JSONObject;
import org.junit.Before;
import org.junit.Test;
import org.mockito.Mock;

import java.util.Base64;
import java.util.List;
import java.util.Optional;

/** Unit tests for {@link EncryptionManager}. */
@SetErrorLogUtilDefaultParams(
        throwable = Any.class,
        ppapiName = AD_SERVICES_ERROR_REPORTED__PPAPI_NAME__TOPICS)
public final class EncryptionManagerTest extends AdServicesExtendedMockitoTestCase {
    static final String PUBLIC_KEY_BASE64 = "YqYc6zOFQFFu3eRg4nkjqN9nSbw44nsQAc1bi5EC5Ew=";
    static final String PRIVATE_KEY_BASE64 = "2ZEyJDoJwkp0l/PahgjwuoCMIaV10zZ59LJGA+ltJ60=";
    static final byte[] DECODED_PUBLIC_KEY = Base64.getDecoder().decode(PUBLIC_KEY_BASE64);
    static final byte[] DECODED_PRIVATE_KEY = Base64.getDecoder().decode(PRIVATE_KEY_BASE64);
    static final byte[] EMPTY_CONTEXT_INFO = new byte[] {};
    private static final String SDK_NAME = "sdk";
    private static final String ENROLLMENT_ID = "enrollmentId";
    private static final EnrollmentData ENROLLMENT_DATA =
            new EnrollmentData.Builder()
                    .setEnrollmentId(ENROLLMENT_ID)
                    .setSdkNames(SDK_NAME)
                    .build();
    private static final EncryptionKey LATEST_HPKE_ENCRYPTION_KEY =
            new EncryptionKey.Builder()
                    .setId("1")
                    .setKeyType(EncryptionKey.KeyType.ENCRYPTION)
                    .setEnrollmentId("100")
                    .setReportingOrigin(Uri.parse("https://test1.com"))
                    .setEncryptionKeyUrl("https://test1.com/.well-known/encryption-keys")
                    .setProtocolType(EncryptionKey.ProtocolType.HPKE)
                    .setKeyCommitmentId(11)
                    .setBody(PUBLIC_KEY_BASE64)
                    .setExpiration(100004L)
                    .setLastFetchTime(100004L)
                    .build();
    private static final EncryptionKey OLDER_INCOMPATIBLE_ENCRYPTION_KEY =
            new EncryptionKey.Builder()
                    .setId("4")
                    .setKeyType(EncryptionKey.KeyType.ENCRYPTION)
                    .setEnrollmentId("101")
                    .setReportingOrigin(Uri.parse("https://test2.com"))
                    .setEncryptionKeyUrl("https://test2.com/.well-known/encryption-keys")
                    .setProtocolType(EncryptionKey.ProtocolType.HPKE)
                    .setKeyCommitmentId(14)
                    .setBody("IncompatibleKey")
                    .setExpiration(100001L)
                    .setLastFetchTime(100001L)
                    .build();

    private EncryptionManager mEncryptionManager;
    @Mock private EnrollmentDao mEnrollmentDao;
    @Mock private EncryptionKeyDao mEncryptionKeyDao;
    @Mock private Encrypter mEncrypter;

    @Before
    public void setup() {
        mEncryptionManager =
                new EncryptionManager(
                        new HpkeEncrypter(), mEnrollmentDao, mEncryptionKeyDao, mMockFlags);

        when(mMockFlags.getEnableDatabaseSchemaVersion9()).thenReturn(true);
        when(mMockFlags.getTopicsEncryptionEnabled()).thenReturn(true);
        when(mMockFlags.getTopicsTestEncryptionPublicKey()).thenReturn("");
    }

    @Test
    public void testEncryption_success() throws JSONException {
        when(mEnrollmentDao.getEnrollmentDataFromSdkName(SDK_NAME)).thenReturn(ENROLLMENT_DATA);
        when(mEncryptionKeyDao.getEncryptionKeyFromEnrollmentIdAndKeyType(
                        ENROLLMENT_ID, EncryptionKey.KeyType.ENCRYPTION))
                .thenReturn(List.of(LATEST_HPKE_ENCRYPTION_KEY));
        Topic topic = Topic.create(/* topic */ 5, /* taxonomyVersion */ 6L, /* modelVersion */ 7L);

        Optional<EncryptedTopic> optionalEncryptedTopic =
                mEncryptionManager.encryptTopic(topic, SDK_NAME);

        // Verify EncryptedTopic is not empty.
        assertThat(optionalEncryptedTopic.isPresent()).isTrue();
        assertThat(optionalEncryptedTopic.get().getEncryptedTopic()).isNotEmpty();
        assertThat(optionalEncryptedTopic.get().getKeyIdentifier()).isEqualTo(PUBLIC_KEY_BASE64);
        assertThat(optionalEncryptedTopic.get().getEncapsulatedKey()).isNotEmpty();

        // Decrypt and deserialize to verify correct information.
        byte[] cipherText =
                Bytes.concat(
                        optionalEncryptedTopic.get().getEncapsulatedKey(),
                        optionalEncryptedTopic.get().getEncryptedTopic());
        byte[] decryptedText = HpkeJni.decrypt(DECODED_PRIVATE_KEY, cipherText, EMPTY_CONTEXT_INFO);
        assertThat(new String(decryptedText))
                .isEqualTo("{\"topic_id\":5,\"model_version\":7," + "\"taxonomy_version\":6}");
        JSONObject returnedJSON = new JSONObject(new String(decryptedText));
        Topic returnedTopic =
                Topic.create(
                        returnedJSON.getInt(KEY_TOPIC_ID),
                        returnedJSON.getLong(KEY_TAXONOMY_VERSION),
                        returnedJSON.getLong(KEY_MODEL_VERSION));
        // Verify decrypted and deserialized object creates the expected Topic.
        assertThat(returnedTopic).isEqualTo(topic);
    }

    @Test
    public void testEncryption_verifyLatestKeys() {
        when(mEnrollmentDao.getEnrollmentDataFromSdkName(SDK_NAME)).thenReturn(ENROLLMENT_DATA);
        when(mEncryptionKeyDao.getEncryptionKeyFromEnrollmentIdAndKeyType(
                        ENROLLMENT_ID, EncryptionKey.KeyType.ENCRYPTION))
                .thenReturn(List.of(LATEST_HPKE_ENCRYPTION_KEY, OLDER_INCOMPATIBLE_ENCRYPTION_KEY));
        Topic topic = Topic.create(/* topic */ 5, /* taxonomyVersion */ 6L, /* modelVersion */ 7L);

        Optional<EncryptedTopic> optionalEncryptedTopic =
                mEncryptionManager.encryptTopic(topic, SDK_NAME);

        // Verify EncryptedTopic is not empty.
        assertThat(optionalEncryptedTopic.isPresent()).isTrue();
        assertThat(optionalEncryptedTopic.get().getEncryptedTopic()).isNotEmpty();
        assertThat(optionalEncryptedTopic.get().getKeyIdentifier())
                .isEqualTo(LATEST_HPKE_ENCRYPTION_KEY.getBody());
        assertThat(optionalEncryptedTopic.get().getEncapsulatedKey()).isNotEmpty();
    }

    @Test
    public void testEncryption_useTestingKeys() {
        String overrideTestKey = "YVfr8K7rpuv45LtaCv9L1eIGxBv/UK22WugJBjg53fo";
        when(mMockFlags.getTopicsTestEncryptionPublicKey()).thenReturn(overrideTestKey);
        Topic topic = Topic.create(/* topic */ 5, /* taxonomyVersion */ 6L, /* modelVersion */ 7L);

        Optional<EncryptedTopic> optionalEncryptedTopic =
                mEncryptionManager.encryptTopic(topic, SDK_NAME);

        // Verify EncryptedTopic is not empty.
        assertThat(optionalEncryptedTopic.isPresent()).isTrue();
        expect.that(optionalEncryptedTopic.get().getEncryptedTopic()).isNotEmpty();
        // Verify test key used to override has been used.
        expect.that(optionalEncryptedTopic.get().getKeyIdentifier()).isEqualTo(overrideTestKey);
        expect.that(optionalEncryptedTopic.get().getEncapsulatedKey()).isNotEmpty();
    }

    @Test
    @ExpectErrorLogUtilCall(
            errorCode = AD_SERVICES_ERROR_REPORTED__ERROR_CODE__TOPICS_ENCRYPTION_KEY_MISSING)
    public void testEncryption_missingKeys() {
        when(mEnrollmentDao.getEnrollmentDataFromSdkName(SDK_NAME)).thenReturn(ENROLLMENT_DATA);
        when(mEncryptionKeyDao.getEncryptionKeyFromEnrollmentIdAndKeyType(
                        ENROLLMENT_ID, EncryptionKey.KeyType.ENCRYPTION))
                .thenReturn(List.of());
        Topic topic = Topic.create(/* topic */ 5, /* taxonomyVersion */ 6L, /* modelVersion */ 7L);

        Optional<EncryptedTopic> optionalEncryptedTopic =
                mEncryptionManager.encryptTopic(topic, SDK_NAME);

        // Verify EncryptedTopic is empty.
        assertThat(optionalEncryptedTopic.isPresent()).isFalse();
    }

    @Test
    @ExpectErrorLogUtilCall(
            errorCode =
                    AD_SERVICES_ERROR_REPORTED__ERROR_CODE__TOPICS_ENCRYPTION_INVALID_KEY_LENGTH)
    @ExpectErrorLogUtilCall(
            errorCode = AD_SERVICES_ERROR_REPORTED__ERROR_CODE__TOPICS_ENCRYPTION_NULL_RESPONSE)
    public void testEncryption_incompatibleKeys() {
        when(mEnrollmentDao.getEnrollmentDataFromSdkName(SDK_NAME)).thenReturn(ENROLLMENT_DATA);
        when(mEncryptionKeyDao.getEncryptionKeyFromEnrollmentIdAndKeyType(
                        ENROLLMENT_ID, EncryptionKey.KeyType.ENCRYPTION))
                .thenReturn(List.of(OLDER_INCOMPATIBLE_ENCRYPTION_KEY));
        Topic topic = Topic.create(/* topic */ 5, /* taxonomyVersion */ 6L, /* modelVersion */ 7L);

        Optional<EncryptedTopic> optionalEncryptedTopic =
                mEncryptionManager.encryptTopic(topic, SDK_NAME);

        // Verify EncryptedTopic is empty.
        assertThat(optionalEncryptedTopic.isPresent()).isFalse();
    }

    @Test
    @ExpectErrorLogUtilCall(
            errorCode = AD_SERVICES_ERROR_REPORTED__ERROR_CODE__TOPICS_ENCRYPTION_KEY_MISSING)
    public void testEncryption_missingEnrollmentData() {
        when(mEnrollmentDao.getEnrollmentDataFromSdkName(SDK_NAME)).thenReturn(null);
        Topic topic = Topic.create(/* topic */ 5, /* taxonomyVersion */ 6L, /* modelVersion */ 7L);

        Optional<EncryptedTopic> optionalEncryptedTopic =
                mEncryptionManager.encryptTopic(topic, SDK_NAME);

        // Verify EncryptedTopic is empty.
        assertThat(optionalEncryptedTopic.isPresent()).isFalse();
    }

    @Test
    @ExpectErrorLogUtilCall(
            errorCode = AD_SERVICES_ERROR_REPORTED__ERROR_CODE__TOPICS_ENCRYPTION_KEY_MISSING)
    public void testEncryption_missingEnrollmentId() {
        when(mEnrollmentDao.getEnrollmentDataFromSdkName(SDK_NAME))
                .thenReturn(new EnrollmentData.Builder().build());
        Topic topic = Topic.create(/* topic */ 5, /* taxonomyVersion */ 6L, /* modelVersion */ 7L);

        Optional<EncryptedTopic> optionalEncryptedTopic =
                mEncryptionManager.encryptTopic(topic, SDK_NAME);

        // Verify EncryptedTopic is empty.
        assertThat(optionalEncryptedTopic.isPresent()).isFalse();
    }

    @Test
    @ExpectErrorLogUtilCall(
            errorCode = AD_SERVICES_ERROR_REPORTED__ERROR_CODE__TOPICS_ENCRYPTION_KEY_MISSING)
    public void testEncryption_missingSdkName() {
        Topic topic = Topic.create(/* topic */ 5, /* taxonomyVersion */ 6L, /* modelVersion */ 7L);

        Optional<EncryptedTopic> optionalEncryptedTopic =
                mEncryptionManager.encryptTopic(topic, "");

        // Verify EncryptedTopic is empty.
        assertThat(optionalEncryptedTopic.isPresent()).isFalse();
    }

    @Test
    @ExpectErrorLogUtilCall(
            errorCode = AD_SERVICES_ERROR_REPORTED__ERROR_CODE__TOPICS_ENCRYPTION_KEY_MISSING)
    public void testEncryption_nullSdkName() {
        Topic topic = Topic.create(/* topic */ 5, /* taxonomyVersion */ 6L, /* modelVersion */ 7L);

        Optional<EncryptedTopic> optionalEncryptedTopic =
                mEncryptionManager.encryptTopic(topic, null);

        // Verify EncryptedTopic is empty.
        assertThat(optionalEncryptedTopic.isPresent()).isFalse();
    }

    @Test
    @ExpectErrorLogUtilCall(
            errorCode = AD_SERVICES_ERROR_REPORTED__ERROR_CODE__TOPICS_ENCRYPTION_KEY_MISSING)
    public void testEncryption_nullTopic_throwsNullPointerException() {
        assertThrows(
                NullPointerException.class,
                () -> mEncryptionManager.encryptTopic(/* topic */ null, SDK_NAME));
    }

    @Test
    @ExpectErrorLogUtilWithExceptionCall(
            throwable = IllegalArgumentException.class,
            errorCode =
                    AD_SERVICES_ERROR_REPORTED__ERROR_CODE__TOPICS_ENCRYPTION_KEY_DECODE_FAILURE)
    public void testEncryption_keyDecodingFailure() {
        EncryptionKey encryptionKeyWithIncompatibleBase64Key =
                new EncryptionKey.Builder()
                        .setId("1")
                        .setKeyType(EncryptionKey.KeyType.ENCRYPTION)
                        .setEnrollmentId("100")
                        .setReportingOrigin(Uri.parse("https://test1.com"))
                        .setEncryptionKeyUrl("https://test1.com/.well-known/encryption-keys")
                        .setProtocolType(EncryptionKey.ProtocolType.HPKE)
                        .setKeyCommitmentId(11)
                        .setBody(
                                /* incompatibleKeyWithBase64Encoding */
                                "!?#$%^&*()=+<>[]{};:.,/\\|~@-_")
                        .setExpiration(100004L)
                        .setLastFetchTime(100004L)
                        .build();
        when(mEnrollmentDao.getEnrollmentDataFromSdkName(SDK_NAME)).thenReturn(ENROLLMENT_DATA);
        when(mEncryptionKeyDao.getEncryptionKeyFromEnrollmentIdAndKeyType(
                        ENROLLMENT_ID, EncryptionKey.KeyType.ENCRYPTION))
                .thenReturn(List.of(encryptionKeyWithIncompatibleBase64Key));
        Topic topic = Topic.create(/* topic */ 5, /* taxonomyVersion */ 6L, /* modelVersion */ 7L);

        Optional<EncryptedTopic> optionalEncryptedTopic =
                mEncryptionManager.encryptTopic(topic, SDK_NAME);

        // Verify EncryptedTopic is empty.
        assertThat(optionalEncryptedTopic.isPresent()).isFalse();
    }

    @Test
    @ExpectErrorLogUtilCall(
            errorCode =
                    AD_SERVICES_ERROR_REPORTED__ERROR_CODE__TOPICS_ENCRYPTION_INVALID_KEY_LENGTH)
    @ExpectErrorLogUtilCall(
            errorCode = AD_SERVICES_ERROR_REPORTED__ERROR_CODE__TOPICS_ENCRYPTION_NULL_RESPONSE)
    public void testEncryption_keyWithSmallerLength() {
        EncryptionKey encryptionKeyWithSmallerLength =
                new EncryptionKey.Builder()
                        .setId("1")
                        .setKeyType(EncryptionKey.KeyType.ENCRYPTION)
                        .setEnrollmentId("100")
                        .setReportingOrigin(Uri.parse("https://test1.com"))
                        .setEncryptionKeyUrl("https://test1.com/.well-known/encryption-keys")
                        .setProtocolType(EncryptionKey.ProtocolType.HPKE)
                        .setKeyCommitmentId(11)
                        .setBody(
                                /* keyWithSmallerLength */
                                "rSJBSUYG0ebvfW1AXCWO0CMGMJhDzp")
                        .setExpiration(100004L)
                        .setLastFetchTime(100004L)
                        .build();
        when(mEnrollmentDao.getEnrollmentDataFromSdkName(SDK_NAME)).thenReturn(ENROLLMENT_DATA);
        when(mEncryptionKeyDao.getEncryptionKeyFromEnrollmentIdAndKeyType(
                        ENROLLMENT_ID, EncryptionKey.KeyType.ENCRYPTION))
                .thenReturn(List.of(encryptionKeyWithSmallerLength));
        Topic topic = Topic.create(/* topic */ 5, /* taxonomyVersion */ 6L, /* modelVersion */ 7L);

        Optional<EncryptedTopic> optionalEncryptedTopic =
                mEncryptionManager.encryptTopic(topic, SDK_NAME);

        // Verify EncryptedTopic is empty.
        assertThat(optionalEncryptedTopic.isPresent()).isFalse();
    }

    @Test
    @ExpectErrorLogUtilCall(
            errorCode = AD_SERVICES_ERROR_REPORTED__ERROR_CODE__TOPICS_ENCRYPTION_NULL_RESPONSE)
    public void testEncryption_nullResponseFromEncrypter() {
        mEncryptionManager =
                new EncryptionManager(mEncrypter, mEnrollmentDao, mEncryptionKeyDao, mMockFlags);
        when(mEnrollmentDao.getEnrollmentDataFromSdkName(SDK_NAME)).thenReturn(ENROLLMENT_DATA);
        when(mEncryptionKeyDao.getEncryptionKeyFromEnrollmentIdAndKeyType(
                        ENROLLMENT_ID, EncryptionKey.KeyType.ENCRYPTION))
                .thenReturn(List.of(LATEST_HPKE_ENCRYPTION_KEY));
        when(mEncrypter.encrypt(any(), any(), any())).thenReturn(null);
        Topic topic = Topic.create(/* topic */ 5, /* taxonomyVersion */ 6L, /* modelVersion */ 7L);

        Optional<EncryptedTopic> optionalEncryptedTopic =
                mEncryptionManager.encryptTopic(topic, SDK_NAME);

        // Verify EncryptedTopic is empty.
        assertThat(optionalEncryptedTopic.isPresent()).isFalse();
    }

    @Test
    @ExpectErrorLogUtilCall(
            errorCode =
                    AD_SERVICES_ERROR_REPORTED__ERROR_CODE__TOPICS_ENCRYPTION_INVALID_RESPONSE_LENGTH)
    public void testEncryption_smallResponseFromEncrypter() {
        mEncryptionManager =
                new EncryptionManager(mEncrypter, mEnrollmentDao, mEncryptionKeyDao, mMockFlags);
        when(mEnrollmentDao.getEnrollmentDataFromSdkName(SDK_NAME)).thenReturn(ENROLLMENT_DATA);
        when(mEncryptionKeyDao.getEncryptionKeyFromEnrollmentIdAndKeyType(
                        ENROLLMENT_ID, EncryptionKey.KeyType.ENCRYPTION))
                .thenReturn(List.of(LATEST_HPKE_ENCRYPTION_KEY));
        when(mEncrypter.encrypt(any(), any(), any()))
                .thenReturn(
                        /* smallResponse */
                        new byte[] {1, 2});
        Topic topic = Topic.create(/* topic */ 5, /* taxonomyVersion */ 6L, /* modelVersion */ 7L);

        Optional<EncryptedTopic> optionalEncryptedTopic =
                mEncryptionManager.encryptTopic(topic, SDK_NAME);

        // Verify EncryptedTopic is empty.
        assertThat(optionalEncryptedTopic.isPresent()).isFalse();
    }
}

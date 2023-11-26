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

import static com.android.adservices.service.topics.TopicsJsonMapper.KEY_MODEL_VERSION;
import static com.android.adservices.service.topics.TopicsJsonMapper.KEY_TAXONOMY_VERSION;
import static com.android.adservices.service.topics.TopicsJsonMapper.KEY_TOPIC_ID;

import static com.google.common.truth.Truth.assertThat;

import static org.junit.Assert.assertThrows;
import static org.mockito.Mockito.when;

import android.content.Context;
import android.net.Uri;

import androidx.test.core.app.ApplicationProvider;

import com.android.adservices.HpkeJni;
import com.android.adservices.data.encryptionkey.EncryptionKeyDao;
import com.android.adservices.data.enrollment.EnrollmentDao;
import com.android.adservices.data.topics.EncryptedTopic;
import com.android.adservices.data.topics.Topic;
import com.android.adservices.service.Flags;
import com.android.adservices.service.encryptionkey.EncryptionKey;
import com.android.adservices.service.enrollment.EnrollmentData;

import com.google.common.primitives.Bytes;

import org.json.JSONException;
import org.json.JSONObject;
import org.junit.Before;
import org.junit.Test;
import org.mockito.Mock;
import org.mockito.MockitoAnnotations;

import java.util.Base64;
import java.util.List;
import java.util.Optional;

/** Unit tests for {@link EncryptionManager}. */
public class EncryptionManagerTest {
    static final String PUBLIC_KEY_BASE64 = "rSJBSUYG0ebvfW1AXCWO0CMGMJhDzpfQm3eLyw1uxX8=";
    static final String PRIVATE_KEY_BASE64 = "f86EzLmGaVmc+PwjJk5ADPE4ijQvliWf0CQyY/Zyy7I=";
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

    private final Context mContext = ApplicationProvider.getApplicationContext();
    private EncryptionManager mEncryptionManager;
    @Mock private EnrollmentDao mEnrollmentDao;
    @Mock private EncryptionKeyDao mEncryptionKeyDao;
    @Mock private Flags mFlags;

    @Before
    public void setup() {
        MockitoAnnotations.initMocks(this);

        mEncryptionManager =
                new EncryptionManager(
                        new HpkeEncrypter(), mEnrollmentDao, mEncryptionKeyDao, mFlags);

        when(mFlags.getEnableDatabaseSchemaVersion9()).thenReturn(true);
        when(mFlags.getTopicsEnableEncryption()).thenReturn(true);
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
    public void testEncryption_missingEnrollmentData() {
        when(mEnrollmentDao.getEnrollmentDataFromSdkName(SDK_NAME)).thenReturn(null);
        Topic topic = Topic.create(/* topic */ 5, /* taxonomyVersion */ 6L, /* modelVersion */ 7L);

        Optional<EncryptedTopic> optionalEncryptedTopic =
                mEncryptionManager.encryptTopic(topic, SDK_NAME);

        // Verify EncryptedTopic is empty.
        assertThat(optionalEncryptedTopic.isPresent()).isFalse();
    }

    @Test
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
    public void testEncryption_missingSdkName() {
        Topic topic = Topic.create(/* topic */ 5, /* taxonomyVersion */ 6L, /* modelVersion */ 7L);

        Optional<EncryptedTopic> optionalEncryptedTopic =
                mEncryptionManager.encryptTopic(topic, "");

        // Verify EncryptedTopic is empty.
        assertThat(optionalEncryptedTopic.isPresent()).isFalse();
    }

    @Test
    public void testEncryption_nullSdkName() {
        Topic topic = Topic.create(/* topic */ 5, /* taxonomyVersion */ 6L, /* modelVersion */ 7L);

        Optional<EncryptedTopic> optionalEncryptedTopic =
                mEncryptionManager.encryptTopic(topic, null);

        // Verify EncryptedTopic is empty.
        assertThat(optionalEncryptedTopic.isPresent()).isFalse();
    }

    @Test
    public void testEncryption_nullTopic_throwsNullPointerException() {
        assertThrows(
                NullPointerException.class,
                () -> mEncryptionManager.encryptTopic(/* topic */ null, SDK_NAME));
    }
}

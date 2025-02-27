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
import static com.android.adservices.service.stats.AdServicesStatsLog.AD_SERVICES_ERROR_REPORTED__PPAPI_NAME__TOPICS;
import static com.android.adservices.service.topics.EncryptionManagerTest.DECODED_PRIVATE_KEY;
import static com.android.adservices.service.topics.EncryptionManagerTest.DECODED_PUBLIC_KEY;
import static com.android.adservices.service.topics.EncryptionManagerTest.EMPTY_CONTEXT_INFO;
import static com.android.adservices.service.topics.TopicsJsonMapper.KEY_MODEL_VERSION;
import static com.android.adservices.service.topics.TopicsJsonMapper.KEY_TAXONOMY_VERSION;
import static com.android.adservices.service.topics.TopicsJsonMapper.KEY_TOPIC_ID;

import static com.google.common.truth.Truth.assertThat;

import static org.junit.Assert.assertThrows;

import com.android.adservices.HpkeJni;
import com.android.adservices.common.AdServicesExtendedMockitoTestCase;
import com.android.adservices.common.logging.annotations.ExpectErrorLogUtilCall;
import com.android.adservices.common.logging.annotations.SetErrorLogUtilDefaultParams;
import com.android.adservices.data.topics.Topic;

import org.json.JSONObject;
import org.junit.Test;

import java.nio.charset.StandardCharsets;

/** Unit tests for {@link HpkeEncrypter}. */
@SetErrorLogUtilDefaultParams(
        throwable = Any.class,
        ppapiName = AD_SERVICES_ERROR_REPORTED__PPAPI_NAME__TOPICS)
public final class HpkeEncrypterTest extends AdServicesExtendedMockitoTestCase {
    private final HpkeEncrypter mHpkeEncrypter = new HpkeEncrypter();

    @Test
    public void testEncryption_success() throws Exception {
        Topic topic = Topic.create(/* topic */ 5, /* taxonomyVersion */ 6L, /* modelVersion */ 7L);
        byte[] plainText = generateTopicsPlainText(topic);

        // Encrypt plain text to cipher text
        byte[] cipherText =
                mHpkeEncrypter.encrypt(DECODED_PUBLIC_KEY, plainText, EMPTY_CONTEXT_INFO);

        // Verify cipher text
        assertThat(cipherText).isNotEmpty();

        // Decrypt and deserialize to verify correct information.
        byte[] decryptedText = HpkeJni.decrypt(DECODED_PRIVATE_KEY, cipherText, EMPTY_CONTEXT_INFO);
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
    @ExpectErrorLogUtilCall(
            errorCode =
                    AD_SERVICES_ERROR_REPORTED__ERROR_CODE__TOPICS_ENCRYPTION_INVALID_KEY_LENGTH)
    public void testEncryption_invalidKeyLength_returnsEmpty() {
        Topic topic = Topic.create(/* topic */ 5, /* taxonomyVersion */ 6L, /* modelVersion */ 7L);
        byte[] plainText = generateTopicsPlainText(topic);

        // Encrypt plain text to cipher text
        byte[] cipherText =
                mHpkeEncrypter.encrypt(
                        "invalidKey".getBytes(StandardCharsets.UTF_8),
                        plainText,
                        EMPTY_CONTEXT_INFO);

        // Verify cipher text is null.
        assertThat(cipherText).isNull();
    }

    @Test
    public void testEncryption_nullPlainText_throwsException() {
        assertThrows(
                NullPointerException.class,
                () ->
                        mHpkeEncrypter.encrypt(
                                DECODED_PUBLIC_KEY, /* plainText */ null, EMPTY_CONTEXT_INFO));
    }

    @Test
    public void testEncryption_nullPublicKey_throwsException() {
        Topic topic = Topic.create(/* topic */ 5, /* taxonomyVersion */ 6L, /* modelVersion */ 7L);
        byte[] plainText = generateTopicsPlainText(topic);

        assertThrows(
                NullPointerException.class,
                () -> mHpkeEncrypter.encrypt(/* publicKey */ null, plainText, EMPTY_CONTEXT_INFO));
    }

    @Test
    public void testEncryption_nullContextInfo_throwsException() {
        Topic topic = Topic.create(/* topic */ 5, /* taxonomyVersion */ 6L, /* modelVersion */ 7L);
        byte[] plainText = generateTopicsPlainText(topic);

        assertThrows(
                NullPointerException.class,
                () ->
                        mHpkeEncrypter.encrypt(
                                DECODED_PUBLIC_KEY, plainText, /* contextInfo */ null));
    }

    private byte[] generateTopicsPlainText(Topic topic) {
        return TopicsJsonMapper.toJson(topic).get().toString().getBytes(StandardCharsets.UTF_8);
    }
}

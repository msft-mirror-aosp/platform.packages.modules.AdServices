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

import android.content.Context;

import androidx.test.core.app.ApplicationProvider;

import com.android.adservices.HpkeJni;
import com.android.adservices.data.topics.EncryptedTopic;
import com.android.adservices.data.topics.Topic;

import com.google.common.primitives.Bytes;

import org.json.JSONException;
import org.json.JSONObject;
import org.junit.Test;

import java.util.Base64;
import java.util.Optional;

/** Unit tests for {@link EncryptionManager}. */
public class EncryptionManagerTest {
    static final String PUBLIC_KEY_BASE64 = "rSJBSUYG0ebvfW1AXCWO0CMGMJhDzpfQm3eLyw1uxX8=";
    static final String PRIVATE_KEY_BASE64 = "f86EzLmGaVmc+PwjJk5ADPE4ijQvliWf0CQyY/Zyy7I=";
    static final byte[] DECODED_PUBLIC_KEY = Base64.getDecoder().decode(PUBLIC_KEY_BASE64);
    static final byte[] DECODED_PRIVATE_KEY = Base64.getDecoder().decode(PRIVATE_KEY_BASE64);
    static final byte[] EMPTY_CONTEXT_INFO = new byte[] {};

    private final Context mContext = ApplicationProvider.getApplicationContext();
    EncryptionManager mEncryptionManager = EncryptionManager.getInstance(mContext);

    @Test
    public void testEncryption_success() throws JSONException {
        Topic topic = Topic.create(/* topic */ 5, /* taxonomyVersion */ 6L, /* modelVersion */ 7L);
        String sdkName = "sdk";

        Optional<EncryptedTopic> optionalEncryptedTopic =
                mEncryptionManager.encryptTopic(topic, sdkName);

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
    public void testEncryption_nullTopic_throwsNullPointerException() {
        assertThrows(
                NullPointerException.class,
                () -> mEncryptionManager.encryptTopic(/* topic */ null, "sdkName"));
    }
}

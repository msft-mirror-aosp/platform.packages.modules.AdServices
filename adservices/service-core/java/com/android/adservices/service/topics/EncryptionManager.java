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

import android.annotation.NonNull;
import android.content.Context;

import com.android.adservices.LoggerFactory;
import com.android.adservices.data.topics.EncryptedTopic;
import com.android.adservices.data.topics.Topic;

import org.json.JSONObject;

import java.nio.charset.StandardCharsets;
import java.util.Base64;
import java.util.Objects;
import java.util.Optional;

/**
 * Class to handle encryption for {@link Topic} objects.
 *
 * <p>Identify the algorithm supported for Encryption.
 *
 * <p>Fetch public key corresponding to sdk(adtech) caller.
 *
 * <p>Generate {@link EncryptedTopic} object from the encrypted cipher text.
 */
public class EncryptionManager {
    private static final LoggerFactory.Logger sLogger = LoggerFactory.getTopicsLogger();
    private static final byte[] EMPTY_BYTE_ARRAY = new byte[] {};
    private static final int ENCAPSULATED_KEY_LENGTH = 32;
    private static final String PUBLIC_KEY_BASE64 = "rSJBSUYG0ebvfW1AXCWO0CMGMJhDzpfQm3eLyw1uxX8=";

    private static EncryptionManager sSingleton;

    private Encrypter mEncrypter;

    EncryptionManager(Encrypter encrypter) {
        mEncrypter = encrypter;
    }

    /** Returns the singleton instance of the {@link EncryptionManager} given a context. */
    @NonNull
    public static EncryptionManager getInstance(@NonNull Context context) {
        synchronized (EncryptionManager.class) {
            if (sSingleton == null) {
                sSingleton = new EncryptionManager(new HpkeEncrypter());
            }
        }
        return sSingleton;
    }

    /**
     * Converts plain text {@link Topic} object to {@link EncryptedTopic}.
     *
     * <p>Returns {@link Optional#empty()} if encryption fails.
     *
     * @param topic object to be encrypted
     * @return corresponding encrypted object
     */
    public Optional<EncryptedTopic> encryptTopic(Topic topic, String sdkName) {
        return encryptTopicWithKey(topic, fetchPublicKeyFor(sdkName));
    }

    private String fetchPublicKeyFor(String sdkName) {
        sLogger.v("Fetching public key for %s", sdkName);
        // TODO(b/310753075): Update logic to fetch public keys for sdkName.
        return PUBLIC_KEY_BASE64;
    }

    /**
     * Serialise {@link Topic} to JSON string with UTF-8 encoding. Encrypt serialised Topic with the
     * given public key.
     */
    private Optional<EncryptedTopic> encryptTopicWithKey(Topic topic, String publicKey) {
        Objects.requireNonNull(topic);

        Optional<JSONObject> optionalTopicJSON = TopicsJsonMapper.toJson(topic);
        if (optionalTopicJSON.isPresent()) {
            // UTF-8 is the default encoding for JSON data.
            byte[] unencryptedSerializedTopic =
                    optionalTopicJSON.get().toString().getBytes(StandardCharsets.UTF_8);
            byte[] base64DecodedPublicKey = Base64.getDecoder().decode(publicKey);
            byte[] response =
                    mEncrypter.encrypt(
                            /* publicKey */
                            base64DecodedPublicKey, /* plainText */
                            unencryptedSerializedTopic, /* contextInfo */
                            EMPTY_BYTE_ARRAY);

            return buildEncryptedTopic(response, publicKey);
        }
        return Optional.empty();
    }

    private static Optional<EncryptedTopic> buildEncryptedTopic(byte[] response, String publicKey) {
        if (response.length < ENCAPSULATED_KEY_LENGTH) {
            sLogger.d(
                    "Encrypted response size is smaller than minimum expected size "
                            + ENCAPSULATED_KEY_LENGTH);
            return Optional.empty();
        }

        // First 32 bytes are the encapsulated key and the remaining array in the cipher text.
        int cipherTextLength = response.length - ENCAPSULATED_KEY_LENGTH;
        byte[] encapsulatedKey = new byte[ENCAPSULATED_KEY_LENGTH];
        byte[] cipherText = new byte[cipherTextLength];
        System.arraycopy(
                response,
                /* srcPos */ 0,
                encapsulatedKey,
                /* destPos */ 0,
                /* length */ ENCAPSULATED_KEY_LENGTH);
        System.arraycopy(
                response,
                /* srcPos */ ENCAPSULATED_KEY_LENGTH,
                cipherText,
                /* destPos */ 0,
                /* length */ cipherTextLength);

        return Optional.of(EncryptedTopic.create(cipherText, publicKey, encapsulatedKey));
    }
}

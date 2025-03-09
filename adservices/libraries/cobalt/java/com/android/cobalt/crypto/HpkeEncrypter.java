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

package com.android.cobalt.crypto;

import com.android.cobalt.CobaltPipelineType;
import com.android.internal.annotations.VisibleForTesting;

import com.google.cobalt.EncryptedMessage;
import com.google.cobalt.Envelope;
import com.google.cobalt.Observation;
import com.google.cobalt.ObservationToEncrypt;
import com.google.protobuf.ByteString;
import com.google.protobuf.MessageLite;

import java.util.Objects;
import java.util.Optional;

/** Handler for encryption of {@link Envelope} and {@link Observation} via {@link HpkeEncrypt}. */
public final class HpkeEncrypter implements Encrypter {
    private final HpkeEncrypt mEncrypter;
    private final PublicEncryptionKeys mPublicEncryptionKeys;

    @VisibleForTesting final int mShufflerKeyIndex;
    @VisibleForTesting final int mAnalyzerKeyIndex;

    private final byte[] mShufflerKey;
    private final byte[] mAnalyzerKey;

    /** Creates a HpkeEncrypter compatible with the specified Cobalt environment */
    public static HpkeEncrypter createForEnvironment(
            HpkeEncrypt encrypter,
            CobaltPipelineType type,
            PublicEncryptionKeys publicEncryptionKeys) {
        Objects.requireNonNull(encrypter, "HpkeEncrypt cannot be null");
        Objects.requireNonNull(type, "CobaltPipelineType cannot be null");
        Objects.requireNonNull(publicEncryptionKeys, "PublicEncryptionKeys cannot be null");

        switch (type) {
            case PROD:
                return new HpkeEncrypter(
                        encrypter,
                        publicEncryptionKeys,
                        publicEncryptionKeys.getShufflerKeyProd(),
                        publicEncryptionKeys.getShufflerKeyIndexProd(),
                        publicEncryptionKeys.getAnalyzerKeyProd(),
                        publicEncryptionKeys.getAnalyzerKeyIndexProd());
            case DEV:
                return new HpkeEncrypter(
                        encrypter,
                        publicEncryptionKeys,
                        publicEncryptionKeys.getShufflerKeyDev(),
                        publicEncryptionKeys.getShufflerKeyIndexDev(),
                        publicEncryptionKeys.getAnalyzerKeyDev(),
                        publicEncryptionKeys.getAnalyzerKeyIndexDev());
        }

        throw new IllegalArgumentException("Unknown Cobalt environment" + type);
    }

    @VisibleForTesting
    HpkeEncrypter(
            HpkeEncrypt encrypter,
            PublicEncryptionKeys publicEncryptionKeys,
            byte[] shufflerKey,
            int shufflerKeyIndex,
            byte[] analyzerKey,
            int analyzerKeyIndex) {
        this.mEncrypter = Objects.requireNonNull(encrypter, "HpkeEncrypt cannot be null");
        this.mPublicEncryptionKeys =
                Objects.requireNonNull(publicEncryptionKeys, "PublicEncryptionKeys cannot be null");
        this.mShufflerKey = Objects.requireNonNull(shufflerKey, "Shuffler key cannot be null");
        this.mShufflerKeyIndex = shufflerKeyIndex;
        this.mAnalyzerKey = Objects.requireNonNull(analyzerKey, "Analyzer key cannot be null");
        this.mAnalyzerKeyIndex = analyzerKeyIndex;
    }

    /**
     * Encrypts the provided {@link Envelope} with the key for the shuffler and wraps it into an
     * {@link EncryptedMessage}.
     *
     * @return {@link EncryptedMessage} wrapped in an Optional if the {@link Envelope} is
     *     successfully encrypted. Optional will be empty if the {@link Envelope} is empty
     * @throws EncryptionFailedException if encryption fails
     */
    @Override
    public Optional<EncryptedMessage> encryptEnvelope(Envelope envelope)
            throws EncryptionFailedException {
        Objects.requireNonNull(envelope, "Envelope cannot be null");

        return encrypt(
                envelope,
                mShufflerKey,
                mShufflerKeyIndex,
                mPublicEncryptionKeys.getShufflerContextInfoBytes(),
                ByteString.EMPTY);
    }

    /**
     * Extracts and encrypts {@link Observation} from the provided {@link ObservationToEncrypt} with
     * the key for the analyzer and wraps it into an {@link EncryptedMessage}.
     *
     * @return {@link EncryptedMessage} wrapped in an Optional if the {@link Observation} is
     *     successfully encrypted. Optional will be empty if the {@link Observation} is empty
     * @throws EncryptionFailedException if encryption fails
     */
    @Override
    public Optional<EncryptedMessage> encryptObservation(ObservationToEncrypt observationToEncrypt)
            throws EncryptionFailedException {
        Objects.requireNonNull(observationToEncrypt, "ObservationToEncrypt cannot be null");

        return encrypt(
                observationToEncrypt.getObservation(),
                mAnalyzerKey,
                mAnalyzerKeyIndex,
                mPublicEncryptionKeys.getAnalyzerContextInfoBytes(),
                observationToEncrypt.getContributionId());
    }

    /**
     * Encrypts the given message and wraps it into an {@link EncryptedMessage.Builder}
     *
     * @param publicKey used by the encryption algorithm, must satisfies the encryption scheme
     *     required key length
     * @param contextInfoBytes used by the encryption algorithm, intended to provide additional data
     *     keeping the message integrity. Cannot be empty
     * @param contributionId passed by the Message to encrypt, used to set contributionId in {@link
     *     EncryptedMessage}. This field should only be set when encrypting an {@link Observation}
     *     that should be counted towards the shuffler threshold. All other Messages should pass a
     *     ByteString.EMPTY
     * @return {@link EncryptedMessage} wrapped in an Optional if the {@link MessageLite} is
     *     successfully encrypted. Optional will be empty if the {@link MessageLite} is empty
     * @throws EncryptionFailedException if encryption fails
     */
    private Optional<EncryptedMessage> encrypt(
            MessageLite message,
            byte[] publicKey,
            int keyIndex,
            byte[] contextInfoBytes,
            ByteString contributionId)
            throws EncryptionFailedException {
        // Assert the public key length matches the X25519 public key requirement, and
        // contextInfoBytes.
        int x25519PublicValueLen = mPublicEncryptionKeys.getX25519PublicValueLen();
        if (publicKey.length != x25519PublicValueLen || contextInfoBytes.length == 0) {
            throw new AssertionError(
                    String.format(
                            "Invalid HPKE parameters. Expected public key length of %d, got %d. "
                                    + "Expected non-zero context info length, got %d",
                            x25519PublicValueLen, publicKey.length, contextInfoBytes.length));
        }

        byte[] plainText = message.toByteArray();
        if (plainText.length == 0) {
            return Optional.empty();
        }

        byte[] encryptedMessageBytes =
                mEncrypter.encrypt(publicKey, message.toByteArray(), contextInfoBytes);
        if (encryptedMessageBytes.length == 0) {
            throw new EncryptionFailedException("Message couldn't be encrypted.");
        }

        return Optional.of(
                EncryptedMessage.newBuilder()
                        .setCiphertext(ByteString.copyFrom(encryptedMessageBytes))
                        .setKeyIndex(keyIndex)
                        .setContributionId(contributionId)
                        .build());
    }
}

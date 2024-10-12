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

package com.android.cobalt.testing.crypto;

import androidx.annotation.VisibleForTesting;

import com.android.cobalt.crypto.Encrypter;
import com.android.cobalt.crypto.EncryptionFailedException;

import com.google.cobalt.EncryptedMessage;
import com.google.cobalt.Envelope;
import com.google.cobalt.ObservationToEncrypt;

import java.util.Objects;
import java.util.Optional;

/** An encrypter that doesn't encrypt, just serializes the proto into the EncryptedMessage. */
@VisibleForTesting
public final class FakeEncrypter implements Encrypter {
    private boolean mThrowOnEncryptEnvelope;
    private boolean mThrowOnEncryptObservation;

    public FakeEncrypter() {
        mThrowOnEncryptEnvelope = false;
        mThrowOnEncryptObservation = false;
    }

    /** Enables throw on encrypt envelope */
    public void setThrowOnEncryptEnvelope() {
        mThrowOnEncryptEnvelope = true;
    }

    /** Enables throw on encrypt observation */
    public void setThrowOnEncryptObservation() {
        mThrowOnEncryptObservation = true;
    }

    /**
     * Encrypts an envelope by serializing its bytes. Throws EncryptionFailedException when throw on
     * encrypt envelope is set
     */
    public Optional<EncryptedMessage> encryptEnvelope(Envelope envelope)
            throws EncryptionFailedException {
        if (mThrowOnEncryptEnvelope) {
            throw new EncryptionFailedException("Envelope couldn't be encrypted.");
        }

        Objects.requireNonNull(envelope);
        if (envelope.toByteArray().length == 0) {
            return Optional.empty();
        }
        return Optional.of(
                EncryptedMessage.newBuilder().setCiphertext(envelope.toByteString()).build());
    }

    /** Encrypts an observation by serializing its bytes. */
    public Optional<EncryptedMessage> encryptObservation(ObservationToEncrypt observation)
            throws EncryptionFailedException {
        if (mThrowOnEncryptObservation) {
            throw new EncryptionFailedException("Observation couldn't be encrypted.");
        }

        Objects.requireNonNull(observation);
        if (observation.getObservation().toByteArray().length == 0) {
            return Optional.empty();
        }
        return Optional.of(
                EncryptedMessage.newBuilder()
                        .setContributionId(observation.getContributionId())
                        .setCiphertext(observation.getObservation().toByteString())
                        .build());
    }
}

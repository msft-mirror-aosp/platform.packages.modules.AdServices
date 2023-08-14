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

package com.android.cobalt.upload.testing;

import static com.android.cobalt.collect.ImmutableHelpers.toImmutableList;

import android.annotation.NonNull;

import com.android.cobalt.upload.Uploader;

import com.google.cobalt.EncryptedMessage;
import com.google.cobalt.Envelope;
import com.google.common.collect.ImmutableList;
import com.google.protobuf.InvalidProtocolBufferException;

/** An uploader that doesn't upload, just tracks EncryptedMessages it receives. */
public final class NoOpUploader implements Uploader {
    private final ImmutableList.Builder<EncryptedMessage> mEncryptedMessages;

    public NoOpUploader() {
        mEncryptedMessages = ImmutableList.builder();
    }

    /** Store the provided encrypted message. */
    public void upload(@NonNull EncryptedMessage encryptedMessage) {
        mEncryptedMessages.add(encryptedMessage);
    }

    /**
     * Get the EncryptedMessages sent through the logger as Envelopes.
     *
     * <p>This assumes encryption was a no-op, though this class could be amended to take a
     * decryption implementation to avoid making this assumption.
     */
    public ImmutableList<Envelope> getSentEnvelopes() {
        return mEncryptedMessages.build().stream()
                .map(
                        e -> {
                            try {
                                return Envelope.parseFrom(e.getCiphertext());
                            } catch (InvalidProtocolBufferException x) {
                                return Envelope.getDefaultInstance();
                            }
                        })
                .collect(toImmutableList());
    }
}

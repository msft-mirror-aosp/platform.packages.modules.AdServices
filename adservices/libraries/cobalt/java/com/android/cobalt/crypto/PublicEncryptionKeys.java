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

package com.android.cobalt.crypto;

/** Interface for {@link HpkeEncrypter} public encryption keys */
public interface PublicEncryptionKeys {
    /** Gets {@code X25519_PUBLIC_VALUE_LEN}. */
    int getX25519PublicValueLen();

    /** Gets {@code SHUFFLER_KEY_PROD}. */
    byte[] getShufflerKeyProd();

    /** Gets {@code SHUFFLER_KEY_INDEX_PROD}. */
    int getShufflerKeyIndexProd();

    /** Gets {@code SHUFFLER_KEY_DEV}. */
    byte[] getShufflerKeyDev();

    /** Gets {@code SHUFFLER_KEY_INDEX_DEV}. */
    int getShufflerKeyIndexDev();

    /** Gets {@code SHUFFLER_CONTEXT_INFO_BYTES}. */
    byte[] getShufflerContextInfoBytes();

    /** Gets {@code ANALYZER_KEY_PROD}. */
    byte[] getAnalyzerKeyProd();

    /** Gets {@code ANALYZER_KEY_INDEX_PROD}. */
    int getAnalyzerKeyIndexProd();

    /** Gets {@code ANALYZER_KEY_DEV}. */
    byte[] getAnalyzerKeyDev();

    /** Gets {@code ANALYZER_KEY_INDEX_DEV}. */
    int getAnalyzerKeyIndexDev();

    /** Gets {@code ANALYZER_CONTEXT_INFO_BYTES}. */
    byte[] getAnalyzerContextInfoBytes();
}

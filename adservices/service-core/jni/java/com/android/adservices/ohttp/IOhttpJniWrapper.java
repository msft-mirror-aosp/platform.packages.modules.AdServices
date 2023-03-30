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

package com.android.adservices.ohttp;

/** Interface for {@link OhttpJniWrapper} to allow mocking of native methods. */
interface IOhttpJniWrapper {

    void hpkeCtxFree(long ctx);

    /** Returns the reference to the KEM algorithm DHKEM(X25519, HKDF-SHA256) */
    long hpkeKemDhkemX25519HkdfSha256();

    /** Returns a reference to the KDF algorithm HKDF-SHA256 */
    long hpkeKdfHkdfSha256();

    /** Returns a reference to the AEAD algorithm AES-256-GCM */
    long hpkeAeadAes256Gcm();

    /**
     * Returns reference to a newly-allocated EVP_HPKE_CTX BoringSSL object. Object thus created
     * must be freed by calling {@link #hpkeCtxFree(long)}
     */
    long hpkeCtxNew();

    /**
     * Calls the boringSSL EVP_HPKE_CTX_setup_sender_with_seed method which implements the
     * SetupBaseS HPKE operation.
     *
     * <p>It encapsulates and returns the sharedSecret for publicKey and sets up ctx as sender
     * context.
     *
     * @param ctx The EVP_HPKE_CTX context containing reference of the heap allocated HPKE context
     *     created using hpkeCtxNew()
     * @param kemNativeRef The reference to the KEM algorithm
     * @param kdfNativeRef The reference to the KDF algorithm
     * @param aeadNativeRef The reference to the AEAD algorithm
     * @param publicKey The server's public key
     * @param info Info for AEAD encryption
     * @param seed A randomly generated seed used for KEM shared secret
     * @return The encapsulated shared secret
     */
    byte[] hpkeCtxSetupSenderWithSeed(
            HpkeContextNativeRef ctx,
            KemNativeRef kemNativeRef,
            KdfNativeRef kdfNativeRef,
            AeadNativeRef aeadNativeRef,
            byte[] publicKey,
            byte[] info,
            byte[] seed);
}

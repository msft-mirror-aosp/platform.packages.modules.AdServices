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

import java.util.Objects;

/**
 * Contains JNI wrappers over BoringSSL methods required to implement Oblivious HTTP.
 *
 * <p>The wrappers follow the following design philosophies:
 *
 * <ul>
 *   <li>None of the JNI wrappers marshal or unmarshal complex Java objects across the JNI boundary.
 *   <li>If the class provides a method to allocate memory on the heap (usually with _new suffix),
 *       it will also provide a method to free that memory (usually with _free suffix)
 * </ul>
 *
 * Mockito can not mock native methods. Hence, this class implements an interface {@link
 * IOhttpJniWrapper} that can be mocked.
 */
class OhttpJniWrapper implements IOhttpJniWrapper {
    private static OhttpJniWrapper sWrapper;

    private OhttpJniWrapper() {
        // We included ohttp_jni as part of hpki_jni shared lib to save on space.
        // Creating a new shared library was around 50 KB more expensive.
        System.loadLibrary("hpke_jni");
    }

    public static OhttpJniWrapper getInstance() {
        if (sWrapper == null) {
            sWrapper = new OhttpJniWrapper();
        }

        return sWrapper;
    }

    /**
     * Releases memory associated with the HPKE context, which must have been created with {@link
     * #hpkeCtxNew()}
     */
    public native void hpkeCtxFree(long ctx);

    /** Returns the reference to the KEM algorithm DHKEM(X25519, HKDF-SHA256) */
    public native long hpkeKemDhkemX25519HkdfSha256();

    /** Returns a reference to the KDF algorithm HKDF-SHA256 */
    public native long hpkeKdfHkdfSha256();

    /** Returns a reference to the AEAD algorithm AES-256-GCM */
    public native long hpkeAeadAes256Gcm();

    /**
     * Returns reference to a newly-allocated EVP_HPKE_CTX BoringSSL object. Object thus created
     * must be freed by calling {@link #hpkeCtxFree(long)}
     */
    public native long hpkeCtxNew();

    /**
     * Calls the boringSSL EVP_HPKE_CTX_setup_sender_with_seed method which implements the
     * SetupBaseS HPKE operation.
     *
     * <p>It encapsulates and returns the sharedSecret for publicKey and sets up ctx as sender
     * context.
     *
     * @param hpkeContextNativeRef The EVP_HPKE_CTX context containing reference of the heap
     *     allocated HPKE context created using hpkeCtxNew()
     * @param kemNativeRef The reference to the KEM algorithm
     * @param kdfNativeRef The reference to the KDF algorithm
     * @param aeadNativeRef The reference to the AEAD algorithm
     * @param publicKey The server's public key
     * @param info Info for AEAD encryption
     * @param seed A randomly generated seed used for KEM shared secret
     * @return The encapsulated shared secret
     */
    public byte[] hpkeCtxSetupSenderWithSeed(
            HpkeContextNativeRef hpkeContextNativeRef,
            KemNativeRef kemNativeRef,
            KdfNativeRef kdfNativeRef,
            AeadNativeRef aeadNativeRef,
            byte[] publicKey,
            byte[] info,
            byte[] seed) {
        Objects.requireNonNull(hpkeContextNativeRef);
        Objects.requireNonNull(kemNativeRef);
        Objects.requireNonNull(kdfNativeRef);
        Objects.requireNonNull(aeadNativeRef);
        return hpkeCtxSetupSenderWithSeed(
                hpkeContextNativeRef.getAddress(),
                kemNativeRef.getAddress(),
                kdfNativeRef.getAddress(),
                aeadNativeRef.getAddress(),
                publicKey,
                info,
                seed);
    }

    private native byte[] hpkeCtxSetupSenderWithSeed(
            long ctx,
            long kemNativeRef,
            long kdfNativeRef,
            long aeadNativeRef,
            byte[] publicKey,
            byte[] info,
            byte[] seed);
}

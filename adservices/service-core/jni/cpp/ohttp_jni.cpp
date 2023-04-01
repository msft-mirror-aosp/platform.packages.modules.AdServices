/*
 * Copyright (C) 2022 The Android Open Source Project
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
#include "ohttp_jni.h"

#include <android/log.h>
#include <openssl/hpke.h>

#include <iostream>
#include <string_view>
#include <vector>

constexpr char const *LOG_TAG = "OhttpJniWrapper";

// TODO(b/274425716) : Use macros similar to Conscrypt's JNI_TRACE for cleaner logging
// TODO(b/274598556) : Add error throwing convenience methods

JNIEXPORT jlong JNICALL
Java_com_android_adservices_ohttp_OhttpJniWrapper_hpkeKemDhkemX25519HkdfSha256(JNIEnv *env,
                                                                  jclass) {
  __android_log_write(ANDROID_LOG_INFO, LOG_TAG, "hpkeKemDhkemX25519HkdfSha256");

  const EVP_HPKE_KEM *ctx = EVP_hpke_x25519_hkdf_sha256();
  return reinterpret_cast<jlong>(ctx);
}

JNIEXPORT jlong JNICALL
Java_com_android_adservices_ohttp_OhttpJniWrapper_hpkeKdfHkdfSha256(JNIEnv *env, jclass) {
  __android_log_write(ANDROID_LOG_INFO, LOG_TAG, "hpkeKdfHkdfSha256");

  const EVP_HPKE_KDF *ctx = EVP_hpke_hkdf_sha256();
  return reinterpret_cast<jlong>(ctx);
}

JNIEXPORT jlong JNICALL
Java_com_android_adservices_ohttp_OhttpJniWrapper_hpkeAeadAes256Gcm(JNIEnv *env, jclass) {
  __android_log_write(ANDROID_LOG_INFO, LOG_TAG, "hpkeAeadAes256Gcm");

  const EVP_HPKE_AEAD *ctx = EVP_hpke_aes_256_gcm();
  return reinterpret_cast<jlong>(ctx);
}

JNIEXPORT void JNICALL Java_com_android_adservices_ohttp_OhttpJniWrapper_hpkeCtxFree(
    JNIEnv *env, jclass, jlong hpkeCtxRef) {
  __android_log_write(ANDROID_LOG_INFO, LOG_TAG, "hpkeCtxFree");

  EVP_HPKE_CTX *ctx = reinterpret_cast<EVP_HPKE_CTX *>(hpkeCtxRef);
  if (ctx != nullptr) {
    EVP_HPKE_CTX_free(ctx);
  }
}

JNIEXPORT jlong JNICALL
Java_com_android_adservices_ohttp_OhttpJniWrapper_hpkeCtxNew(JNIEnv *env, jclass) {
  __android_log_write(ANDROID_LOG_INFO, LOG_TAG, "hpkeCtxNew");

  const EVP_HPKE_CTX *ctx = EVP_HPKE_CTX_new();
  return reinterpret_cast<jlong>(ctx);
}

// Defining EVP_HPKE_KEM struct with only the field needed to call the
// function "EVP_HPKE_CTX_setup_sender_with_seed_for_testing" using
// "kem->seed_len"
struct evp_hpke_kem_st {
  size_t seed_len;
};

JNIEXPORT jbyteArray JNICALL
Java_com_android_adservices_ohttp_OhttpJniWrapper_hpkeCtxSetupSenderWithSeed(
    JNIEnv *env, jclass, jlong senderHpkeCtxRef, jlong evpKemRef,
    jlong evpKdfRef, jlong evpAeadRef, jbyteArray publicKeyArray,
    jbyteArray infoArray, jbyteArray seedArray) {
    __android_log_write(ANDROID_LOG_INFO, LOG_TAG, "hpkeCtxSetupSenderWithSeed");

  EVP_HPKE_CTX *ctx = reinterpret_cast<EVP_HPKE_CTX *>(senderHpkeCtxRef);
  if (ctx == nullptr) {
    // TODO(b/274598556) : throw NullPointerException
    __android_log_print(ANDROID_LOG_ERROR, LOG_TAG,
                          "hpke context is null");
    return {};
  }

  const EVP_HPKE_KEM *kem = reinterpret_cast<const EVP_HPKE_KEM *>(evpKemRef);
  const EVP_HPKE_KDF *kdf = reinterpret_cast<const EVP_HPKE_KDF *>(evpKdfRef);
  const EVP_HPKE_AEAD *aead =
      reinterpret_cast<const EVP_HPKE_AEAD *>(evpAeadRef);

  __android_log_print(
      ANDROID_LOG_INFO, LOG_TAG,
      "EVP_HPKE_CTX_setup_sender_with_seed(%p, %ld, %ld, %ld, %p, %p, %p)", ctx,
      (long)evpKemRef, (long)evpKdfRef, (long)evpAeadRef, publicKeyArray,
      infoArray, seedArray);


  if (kem == nullptr || kdf == nullptr || aead == nullptr) {
    __android_log_print(ANDROID_LOG_ERROR, LOG_TAG,
                       "kem or kdf or aead is null");
    return {};
  }

  if (publicKeyArray == nullptr || seedArray == nullptr) {
    __android_log_print(ANDROID_LOG_ERROR, LOG_TAG,
                       "public key array or seed array is null");
    return {};
  }

  jbyte *peer_public_key = env->GetByteArrayElements(publicKeyArray, 0);
  jbyte *seed = env->GetByteArrayElements(seedArray, 0);

  jbyte *infoArrayBytes = nullptr;
  const uint8_t *info = nullptr;
  size_t infoLen = 0;
  if (infoArray != nullptr) {
    infoArrayBytes = env->GetByteArrayElements(infoArray, 0);
    info = reinterpret_cast<const uint8_t *>(infoArrayBytes);
    infoLen = env->GetArrayLength(infoArray);
  }

  size_t encapsulatedSharedSecretLen;
  std::vector<uint8_t> encapsulatedSharedSecret(EVP_HPKE_MAX_ENC_LENGTH);
  if (!EVP_HPKE_CTX_setup_sender_with_seed_for_testing(
          /* ctx= */ ctx,
          /* out_enc= */ encapsulatedSharedSecret.data(),
          /* out_enc_len= */ &encapsulatedSharedSecretLen,
          /* max_enc= */ encapsulatedSharedSecret.size(),
          /* kem= */ kem,
          /* kdf= */ kdf,
          /* aead= */ aead,
          /* peer_public_key= */
          reinterpret_cast<const uint8_t *>(peer_public_key),
          /* peer_public_key_len= */ env->GetArrayLength(publicKeyArray),
          /* info= */ info,
          /* info_len= */ infoLen,
          /* seed= */ reinterpret_cast<const uint8_t *>(seed),
          /* seed_len= */ kem->seed_len)) {
    env->ReleaseByteArrayElements(publicKeyArray, peer_public_key, JNI_ABORT);
    env->ReleaseByteArrayElements(seedArray, seed, JNI_ABORT);

    if (infoArrayBytes != nullptr) {
      env->ReleaseByteArrayElements(infoArray, infoArrayBytes, JNI_ABORT);
    }

    __android_log_print(ANDROID_LOG_ERROR, LOG_TAG,
                        "setup sender returned 0");
    return {};
  }

  env->ReleaseByteArrayElements(publicKeyArray, peer_public_key, JNI_ABORT);
  env->ReleaseByteArrayElements(seedArray, seed, JNI_ABORT);

  if (infoArrayBytes!= nullptr) {
    env->ReleaseByteArrayElements(infoArray, infoArrayBytes, JNI_ABORT);
  }

  jbyteArray encArray = env->NewByteArray(encapsulatedSharedSecretLen);
  env->SetByteArrayRegion(
      encArray, 0, encapsulatedSharedSecretLen,
      reinterpret_cast<const jbyte *>(encapsulatedSharedSecret.data()));

  return encArray;
}

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

package com.android.adservices.service.common.crypto;

import static com.android.adservices.service.stats.AdServicesStatsLog.AD_SERVICES_ERROR_REPORTED__ERROR_CODE__HPKE_ALGORITHM_NOT_FOUND;
import static com.android.adservices.service.stats.AdServicesStatsLog.AD_SERVICES_ERROR_REPORTED__ERROR_CODE__HPKE_CLASS_NOT_FOUND;
import static com.android.adservices.service.stats.AdServicesStatsLog.AD_SERVICES_ERROR_REPORTED__ERROR_CODE__HPKE_DECRYPTION_EXCEPTION;
import static com.android.adservices.service.stats.AdServicesStatsLog.AD_SERVICES_ERROR_REPORTED__ERROR_CODE__HPKE_ENCRYPTION_EXCEPTION;
import static com.android.adservices.service.stats.AdServicesStatsLog.AD_SERVICES_ERROR_REPORTED__ERROR_CODE__HPKE_INVALID_KEY;
import static com.android.adservices.service.stats.AdServicesStatsLog.AD_SERVICES_ERROR_REPORTED__PPAPI_NAME__COMMON;

import android.annotation.Nullable;
import android.crypto.hpke.AeadParameterSpec;
import android.crypto.hpke.Hpke;
import android.crypto.hpke.KdfParameterSpec;
import android.crypto.hpke.KemParameterSpec;
import android.crypto.hpke.Message;
import android.crypto.hpke.XdhKeySpec;
import android.os.Build;
import android.os.ext.SdkExtensions;
import android.util.Log;

import com.android.adservices.HpkeJni;
import com.android.adservices.errorlogging.ErrorLogUtil;
import com.android.adservices.service.FlagsFactory;
import com.android.internal.annotations.VisibleForTesting;

import java.security.GeneralSecurityException;
import java.security.InvalidKeyException;
import java.security.KeyFactory;
import java.security.NoSuchAlgorithmException;
import java.security.PrivateKey;
import java.security.PublicKey;
import java.security.spec.InvalidKeySpecException;
import java.security.spec.KeySpec;

/**
 * Hybrid Public Key Encryption (HPKE) operations.
 *
 * <p>RFC: <a href="https://datatracker.ietf.org/doc/rfc9180">RFC-9180</a>
 */
public final class AdServicesHpke {
    // TODO(b/413122664): Fix cobalt library test dependency to use LoggerFactory
    private static final String TAG = "adservices";
    private static final int DHKEM_X25519_HKDF_SHA256_ENC_SIZE = 32;
    private static final int SDK_EXTENSION_VERSION_HPKE_RELEASED = 17;

    /**
     * Hpke encryption using the default algorithm chosen by AdServices. The default algorithm is: -
     * KEM: DHKEM_X25519_HKDF_SHA256 - KDF: HKDF_SHA256 - AEAD: CHACHA20POLY1305
     *
     * <p>Note: This encryption function is a wrapper that refactors the existing encryption
     * implementation and allows switching from {@link HpkeJni#encrypt(byte[], byte[], byte[])}.
     *
     * @param publicKey used by the encryption algorithm, must be exactly 32 bytes long defined by
     *     the KEM (DHKEM_X25519_HKDF_SHA256). The public key is typically retrieved from a trusted
     *     server.
     * @param plaintext the message to be encrypted. If the plaintext is null, the output will be
     *     null as well.
     * @param info used by the encryption algorithm intended to provide info keeping message
     *     integrity.
     * @return ciphertext encrypted result, ciphertext would be null if encryption fails.
     */
    public static byte[] encrypt(byte[] publicKey, byte[] plaintext, @Nullable byte[] info) {
        if (FlagsFactory.getFlags().getEnableHpkeWithPlatformApis() && isHpkeAvailable()) {
            Log.d(TAG, "Encrypting message using platform Hpke API");
            if (plaintext == null) {
                Log.d(TAG, "Plaintext is missing");
                return null;
            }
            try {
                return transform(
                        encrypt(
                                KemParameterSpec.DHKEM_X25519_HKDF_SHA256,
                                KdfParameterSpec.HKDF_SHA256,
                                AeadParameterSpec.CHACHA20POLY1305,
                                createPublicKey(publicKey),
                                plaintext,
                                info,
                                /* aad */ null));
            } catch (NoClassDefFoundError e) {
                Log.e(TAG, "Platform Hpke encryption API not found", e);
                ErrorLogUtil.e(
                        e,
                        AD_SERVICES_ERROR_REPORTED__ERROR_CODE__HPKE_CLASS_NOT_FOUND,
                        AD_SERVICES_ERROR_REPORTED__PPAPI_NAME__COMMON);
            } catch (NoSuchAlgorithmException e) {
                Log.e(TAG, "The algorithm chosen is not found by the encryption provider", e);
                ErrorLogUtil.e(
                        e,
                        AD_SERVICES_ERROR_REPORTED__ERROR_CODE__HPKE_ALGORITHM_NOT_FOUND,
                        AD_SERVICES_ERROR_REPORTED__PPAPI_NAME__COMMON);
            } catch (InvalidKeyException | InvalidKeySpecException e) {
                Log.e(TAG, "Public key is not valid", e);
                ErrorLogUtil.e(
                        e,
                        AD_SERVICES_ERROR_REPORTED__ERROR_CODE__HPKE_INVALID_KEY,
                        AD_SERVICES_ERROR_REPORTED__PPAPI_NAME__COMMON);
            } catch (Exception e) {
                Log.e(TAG, "Error while encrypting message", e);
                ErrorLogUtil.e(
                        e,
                        AD_SERVICES_ERROR_REPORTED__ERROR_CODE__HPKE_ENCRYPTION_EXCEPTION,
                        AD_SERVICES_ERROR_REPORTED__PPAPI_NAME__COMMON);
            }
            return null;
        } else {
            Log.d(TAG, "Encrypting message using internal mechanism");
            return HpkeJni.encrypt(publicKey, plaintext, info);
        }
    }

    /**
     * Hpke decryption using the default algorithm chosen by AdServices. The default algorithm is: -
     * KEM: DHKEM_X25519_HKDF_SHA256 - KDF: HKDF_SHA256 - AEAD: CHACHA20POLY1305
     *
     * <p>Note: This decryption function is a wrapper that refactors the existing decryption
     * implementation and allows switching from {@link HpkeJni#decrypt(byte[], byte[], byte[])}.
     *
     * @param privateKey used to decrypt the ciphertext.
     * @param message the encrypted message to be decrypted. It consists of the encapsulated key
     *     followed by the encrypted message. The encapsulated key size depends on the KEM used,
     *     which is 32 bytes for DHKEM_X25519_HKDF_SHA256. If the message is null, the output will
     *     be null as well.
     * @param info used on decryption verifying message integrity.
     * @return plaintext decrypted result, plaintext would be null if decryption fails.
     */
    public static byte[] decrypt(byte[] privateKey, byte[] message, @Nullable byte[] info) {
        if (FlagsFactory.getFlags().getEnableHpkeWithPlatformApis() && isHpkeAvailable()) {
            Log.d(TAG, "Decrypting message using platform Hpke API");
            if (message == null) {
                Log.d(TAG, "Message is missing");
                return null;
            }
            if (message.length < DHKEM_X25519_HKDF_SHA256_ENC_SIZE) {
                Log.d(TAG, "Encapsulated key size is not valid");
                return null;
            }
            try {
                byte[] enc = new byte[DHKEM_X25519_HKDF_SHA256_ENC_SIZE];
                byte[] ciphertext = new byte[message.length - DHKEM_X25519_HKDF_SHA256_ENC_SIZE];
                System.arraycopy(
                        /* src */ message,
                        /* srcPos */ 0,
                        /* dest */ enc,
                        /* destPos */ 0,
                        enc.length);
                System.arraycopy(
                        /* src */ message,
                        /* srcPos */ enc.length,
                        /* dest */ ciphertext,
                        /* destPos */ 0,
                        ciphertext.length);

                return decrypt(
                        KemParameterSpec.DHKEM_X25519_HKDF_SHA256,
                        KdfParameterSpec.HKDF_SHA256,
                        AeadParameterSpec.CHACHA20POLY1305,
                        createPrivateKey(privateKey),
                        enc,
                        ciphertext,
                        info,
                        /* aad */ null);
            } catch (NoClassDefFoundError e) {
                Log.e(TAG, "Platform Hpke decryption API not found", e);
                ErrorLogUtil.e(
                        e,
                        AD_SERVICES_ERROR_REPORTED__ERROR_CODE__HPKE_CLASS_NOT_FOUND,
                        AD_SERVICES_ERROR_REPORTED__PPAPI_NAME__COMMON);
            } catch (NoSuchAlgorithmException e) {
                Log.e(TAG, "The algorithm chosen is not found by the decryption provider", e);
                ErrorLogUtil.e(
                        e,
                        AD_SERVICES_ERROR_REPORTED__ERROR_CODE__HPKE_ALGORITHM_NOT_FOUND,
                        AD_SERVICES_ERROR_REPORTED__PPAPI_NAME__COMMON);
            } catch (InvalidKeyException | InvalidKeySpecException e) {
                Log.e(TAG, "Private key is not valid", e);
                ErrorLogUtil.e(
                        e,
                        AD_SERVICES_ERROR_REPORTED__ERROR_CODE__HPKE_INVALID_KEY,
                        AD_SERVICES_ERROR_REPORTED__PPAPI_NAME__COMMON);
            } catch (Exception e) {
                Log.e(TAG, "Error while decrypting message", e);
                ErrorLogUtil.e(
                        e,
                        AD_SERVICES_ERROR_REPORTED__ERROR_CODE__HPKE_DECRYPTION_EXCEPTION,
                        AD_SERVICES_ERROR_REPORTED__PPAPI_NAME__COMMON);
            }
            return null;
        } else {
            Log.d(TAG, "Decrypting message using internal mechanism");
            return HpkeJni.decrypt(privateKey, message, info);
        }
    }

    private static Message encrypt(
            KemParameterSpec kem,
            KdfParameterSpec kdf,
            AeadParameterSpec aead,
            PublicKey publicKey,
            byte[] plaintext,
            @Nullable byte[] info,
            @Nullable byte[] aad)
            throws NoSuchAlgorithmException, InvalidKeyException {
        Hpke hpke = Hpke.getInstance(Hpke.getSuiteName(kem, kdf, aead));
        return hpke.seal(publicKey, info, plaintext, aad);
    }

    private static byte[] decrypt(
            KemParameterSpec kem,
            KdfParameterSpec kdf,
            AeadParameterSpec aead,
            PrivateKey privateKey,
            byte[] enc,
            byte[] ciphertext,
            @Nullable byte[] info,
            @Nullable byte[] aad)
            throws GeneralSecurityException {
        Hpke hpke = Hpke.getInstance(Hpke.getSuiteName(kem, kdf, aead));
        Message message = new Message(enc, ciphertext);
        return hpke.open(privateKey, info, message, aad);
    }

    private static PublicKey createPublicKey(byte[] publicKey)
            throws NoSuchAlgorithmException, InvalidKeySpecException {
        if (publicKey == null) {
            throw new InvalidKeySpecException("publicKey is required");
        }
        final KeyFactory factory = KeyFactory.getInstance("XDH");
        final KeySpec spec = new XdhKeySpec(publicKey);
        return factory.generatePublic(spec);
    }

    private static PrivateKey createPrivateKey(byte[] privateKey)
            throws NoSuchAlgorithmException, InvalidKeySpecException {
        if (privateKey == null) {
            throw new InvalidKeySpecException("privateKey is required");
        }
        final KeyFactory factory = KeyFactory.getInstance("XDH");
        final KeySpec spec = new XdhKeySpec(privateKey);
        return factory.generatePrivate(spec);
    }

    /**
     * Converts a {@link Message} to a byte array concatenating the encapsulated key with the
     * ciphertext (enc + ct). The encapsulated key size depends on the KEM used.
     */
    private static byte[] transform(Message message) {
        byte[] result = new byte[message.getEncapsulated().length + message.getCiphertext().length];
        System.arraycopy(
                /* src */ message.getEncapsulated(),
                /* srcPos */ 0,
                /* dest */ result,
                /* destPos */ 0,
                message.getEncapsulated().length);
        System.arraycopy(
                /* src */ message.getCiphertext(),
                /* srcPos */ 0,
                /* dest */ result,
                /* destPos */ message.getEncapsulated().length,
                message.getCiphertext().length);
        return result;
    }

    @VisibleForTesting
    static boolean isHpkeAvailable() {
        return SdkExtensions.getExtensionVersion(Build.VERSION_CODES.S)
                >= SDK_EXTENSION_VERSION_HPKE_RELEASED;
    }
}

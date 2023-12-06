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

import android.annotation.NonNull;

import com.android.adservices.ohttp.algorithms.AeadAlgorithmSpec;
import com.android.adservices.ohttp.algorithms.KdfAlgorithmSpec;
import com.android.adservices.ohttp.algorithms.KemAlgorithmSpec;
import com.android.adservices.ohttp.algorithms.UnsupportedHpkeAlgorithmException;

import com.google.common.base.Preconditions;

import java.io.ByteArrayOutputStream;
import java.io.DataOutputStream;
import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.security.SecureRandom;

/** Provides methods for OHTTP server/gateway side encryption and decryption */
// TODO(b/309955907): Refactor ObliviousHttpGateway and ObliviousHttpClient to reduce duplication
public class ObliviousHttpGateway {
    private static final String OHTTP_REQUEST_LABEL = "message/bhttp request";
    private static final String OHTTP_RESPONSE_LABEL = "message/bhttp response";

    // HPKE export methods require context strings to export aead key and nonce as defined in
    // https://www.ietf.org/archive/id/draft-ietf-ohai-ohttp-03.html#section-4.2-3
    private static final String AEAD_KEY_CONTEXT = "key";
    private static final String AEAD_NONCE_CONTEXT = "nonce";

    /**
     * According to https://www.ietf.org/archive/id/draft-ietf-ohai-ohttp-03.html#section-4.1-6
     *
     * <p>hdr = concat(encode(1, keyID), encode(2, kemID), encode(2, kdfID), encode(2, aeadID))
     *
     * <p>total length = 7 (1+2+2+2)
     */
    private static final int MESSAGE_HEADER_LENGTH_IN_BYTES = 7;

    /**
     * Decrypts the given encapsulated request using the private key provided
     *
     * <p>From https://www.ietf.org/archive/id/draft-ietf-ohai-ohttp-03.html#section-4-4
     *
     * <p>The encapsulated request is a combination of :
     *
     * <pre>     Encapsulated Request {
     *            Key Identifier (8),
     *            KEM Identifier (16),
     *            KDF Identifier (16),
     *            AEAD Identifier (16),
     *            Encapsulated KEM Shared Secret (8*Nenc),
     *            AEAD-Protected Request (..)}
     * </pre>
     *
     * <p>This method provides a way to decrypt payloads generated by {@link
     * ObliviousHttpClient#createObliviousHttpRequest(byte[])}
     *
     * @param privateKey The private key with which to decrypt the payload
     * @param encapsulatedRequest The payload to decrypt.
     * @return the decrypted bytes
     */
    public static byte[] decrypt(
            OhttpGatewayPrivateKey privateKey, @NonNull byte[] encapsulatedRequest)
            throws UnsupportedHpkeAlgorithmException, IOException {

        // Parse the encapsulated request into its components
        int keyId = getKeyId(encapsulatedRequest);
        KemAlgorithmSpec kemAlgorithmSpec = getKem(encapsulatedRequest);
        KdfAlgorithmSpec kdfAlgorithmSpec = getKdf(encapsulatedRequest);
        AeadAlgorithmSpec aeadAlgorithmSpec = getAead(encapsulatedRequest);

        EncapsulatedSharedSecret encapsulatedSharedSecret =
                getEncapsulatedSharedSecret(
                        kemAlgorithmSpec.encapsulatedKeyLength(), encapsulatedRequest);

        // Compute the recipient info required to decrypt the payload
        // As per https://www.ietf.org/archive/id/draft-ietf-ohai-ohttp-03.html#section-4.1-10
        RecipientKeyInfo info =
                createRecipientKeyInfo(
                        keyId,
                        kemAlgorithmSpec.identifier(),
                        kdfAlgorithmSpec.identifier(),
                        aeadAlgorithmSpec.identifier());

        byte[] cipherText =
                getCipherText(kemAlgorithmSpec.encapsulatedKeyLength(), encapsulatedRequest);

        OhttpJniWrapper jniWrapper = OhttpJniWrapper.getInstance();
        HpkeContextNativeRef hpkeContextNativeRef =
                HpkeContextNativeRef.createHpkeContextReference();

        if (!jniWrapper.hpkeSetupRecipient(
                hpkeContextNativeRef,
                kemAlgorithmSpec.kemNativeRefSupplier().get(),
                kdfAlgorithmSpec.kdfNativeRefSupplier().get(),
                aeadAlgorithmSpec.aeadNativeRefSupplier().get(),
                privateKey,
                encapsulatedSharedSecret,
                info)) {
            return new byte[0];
        }

        GatewayDecryptResponse decrypted =
                jniWrapper.gatewayDecrypt(
                        hpkeContextNativeRef,
                        kemAlgorithmSpec.kemNativeRefSupplier().get(),
                        kdfAlgorithmSpec.kdfNativeRefSupplier().get(),
                        aeadAlgorithmSpec.aeadNativeRefSupplier().get(),
                        cipherText);

        return decrypted.getBytes();
    }

    /**
     * Encrypts the given plaintext
     *
     * <p>The gateway/server derives the necessary materials for encryption including the key from
     * an encrypted payload that was generated by the client. Hence, we need to provide a seed
     * encrypted request.
     *
     * <p>This method will derive the necessary information from encryptedSeedRequest and encrypt
     * the plainText
     *
     * <p>Encrypts payload that can be decrypted by {@link
     * ObliviousHttpClient#decryptObliviousHttpResponse(byte[], ObliviousHttpRequestContext)}
     *
     * @param privateKey The private key of the server
     * @param encryptedSeedRequest The encrypted payload from which to derive keying materials
     * @param plainText The payload to encrypt.
     * @return the encrypted bytes
     */
    public static byte[] encrypt(
            OhttpGatewayPrivateKey privateKey,
            @NonNull byte[] encryptedSeedRequest,
            @NonNull byte[] plainText)
            throws UnsupportedHpkeAlgorithmException, IOException {
        // Parse the encapsulated request into its components
        int keyId = getKeyId(encryptedSeedRequest);
        KemAlgorithmSpec kemAlgorithmSpec = getKem(encryptedSeedRequest);
        KdfAlgorithmSpec kdfAlgorithmSpec = getKdf(encryptedSeedRequest);
        AeadAlgorithmSpec aeadAlgorithmSpec = getAead(encryptedSeedRequest);

        EncapsulatedSharedSecret encapsulatedSharedSecret =
                getEncapsulatedSharedSecret(
                        kemAlgorithmSpec.encapsulatedKeyLength(), encryptedSeedRequest);

        // Compute the recipient info
        // As per https://www.ietf.org/archive/id/draft-ietf-ohai-ohttp-03.html#section-4.1-10
        RecipientKeyInfo info =
                createRecipientKeyInfo(
                        keyId,
                        kemAlgorithmSpec.identifier(),
                        kdfAlgorithmSpec.identifier(),
                        aeadAlgorithmSpec.identifier());

        /**
         * The encryption algorithm is as follows
         * https://www.ietf.org/archive/id/draft-ietf-ohai-ohttp-03.html#section-4.2-3
         *
         * <pre> secret = context.Export("message/bhttp response", Nk)
         *       response_nonce = random(max(Nn, Nk))
         *       salt = concat(enc, response_nonce)
         *       prk = Extract(salt, secret)
         *       aead_key = Expand(prk, "key", Nk)
         *       aead_nonce = Expand(prk, "nonce", Nn)
         *       ct = Seal(aead_key, aead_nonce, "", response)
         *       enc_response = concat(response_nonce, ct)
         *       </pre>
         */

        // Create and set up the recipient context
        OhttpJniWrapper jniWrapper = OhttpJniWrapper.getInstance();
        HpkeContextNativeRef hpkeContextNativeRef =
                HpkeContextNativeRef.createHpkeContextReference();

        if (!jniWrapper.hpkeSetupRecipient(
                hpkeContextNativeRef,
                kemAlgorithmSpec.kemNativeRefSupplier().get(),
                kdfAlgorithmSpec.kdfNativeRefSupplier().get(),
                aeadAlgorithmSpec.aeadNativeRefSupplier().get(),
                privateKey,
                encapsulatedSharedSecret,
                info)) {
            return new byte[0];
        }

        // secret = context.Export("message/bhttp response", Nk)
        byte[] labelBytes = OHTTP_RESPONSE_LABEL.getBytes(StandardCharsets.US_ASCII);
        HpkeExportResponse secret =
                jniWrapper.hpkeExport(
                        hpkeContextNativeRef, labelBytes, aeadAlgorithmSpec.keyLength());

        // response_nonce = random(max(Nn, Nk))
        int lengthNonce = Math.max(aeadAlgorithmSpec.nonceLength(), aeadAlgorithmSpec.keyLength());
        byte[] responseNonce = getSecureRandomBytes(lengthNonce);

        // salt = concat(enc, response_nonce)
        byte[] salt = concatByteArrays(encapsulatedSharedSecret.getBytes(), responseNonce);

        HkdfMessageDigestNativeRef messageDigest = kdfAlgorithmSpec.messageDigestSupplier().get();
        // prk = Extract(salt, secret)
        byte[] prk = extract(jniWrapper, messageDigest, secret.getBytes(), salt);

        //  aead_key = Expand(prk, "key", Nk)
        byte[] keyContext = AEAD_KEY_CONTEXT.getBytes(StandardCharsets.US_ASCII);
        HkdfExpandResponse hkdfKeyResponse =
                jniWrapper.hkdfExpand(
                        messageDigest, prk, keyContext, aeadAlgorithmSpec.keyLength());

        // aead_nonce = Expand(prk, "nonce", Nn)
        byte[] nonceContext = AEAD_NONCE_CONTEXT.getBytes(StandardCharsets.US_ASCII);
        HkdfExpandResponse hkdfNonceResponse =
                jniWrapper.hkdfExpand(
                        messageDigest, prk, nonceContext, aeadAlgorithmSpec.nonceLength());

        // ct = Seal(aead_key, aead_nonce, "", response)
        byte[] cipherText =
                jniWrapper.aeadSeal(
                        aeadAlgorithmSpec.aeadNativeRefSupplier().get(),
                        hkdfKeyResponse.getBytes(),
                        hkdfNonceResponse.getBytes(),
                        plainText);

        // enc_response = concat(response_nonce, ct)
        return concatByteArrays(responseNonce, cipherText);
    }

    private static byte[] extract(
            OhttpJniWrapper ohttpJniWrapper,
            HkdfMessageDigestNativeRef messageDigest,
            byte[] secret,
            byte[] salt) {
        HkdfExtractResponse extractResponse =
                ohttpJniWrapper.hkdfExtract(messageDigest, secret, salt);
        return extractResponse.getBytes();
    }

    private static byte[] concatByteArrays(byte[] array1, byte[] array2) throws IOException {
        ByteArrayOutputStream outputStream = new ByteArrayOutputStream();
        outputStream.write(array1);
        outputStream.write(array2);
        return outputStream.toByteArray();
    }

    private static int getKeyId(byte[] cipherText) {
        return cipherText[0];
    }

    private static KemAlgorithmSpec getKem(byte[] cipherText)
            throws UnsupportedHpkeAlgorithmException {
        int kemId = ((cipherText[1] & 0xff) << 8) | (cipherText[2] & 0xff);
        return KemAlgorithmSpec.get(kemId);
    }

    private static KdfAlgorithmSpec getKdf(byte[] cipherText)
            throws UnsupportedHpkeAlgorithmException {
        int kdfId = ((cipherText[3] & 0xff) << 8) | (cipherText[4] & 0xff);
        return KdfAlgorithmSpec.get(kdfId);
    }

    private static AeadAlgorithmSpec getAead(byte[] cipherText)
            throws UnsupportedHpkeAlgorithmException {
        int aeadId = ((cipherText[5] & 0xff) << 8) | (cipherText[6] & 0xff);
        return AeadAlgorithmSpec.get(aeadId);
    }

    private static EncapsulatedSharedSecret getEncapsulatedSharedSecret(
            int encLength, byte[] cipherText) {
        Preconditions.checkArgument(
                encLength + MESSAGE_HEADER_LENGTH_IN_BYTES <= cipherText.length);
        byte[] destinationArray = new byte[encLength];
        System.arraycopy(
                cipherText,
                MESSAGE_HEADER_LENGTH_IN_BYTES,
                destinationArray,
                /* destPos= */ 0,
                encLength);
        return EncapsulatedSharedSecret.create(destinationArray);
    }

    /**
     * Generates the 'info' field as required by HPKE setupBaseR operation according to OHTTP spec
     *
     * <p>https://www.ietf.org/archive/id/draft-ietf-ohai-ohttp-03.html#section-4.1-10
     *
     * <pre>info = concat(encode_str("message/bhttp request"),
     *               encode(1, 0),
     *               encode(1, keyID),
     *               encode(2, kemID),
     *               encode(2, kdfID),
     *               encode(2, aeadID)) </pre>
     */
    private static RecipientKeyInfo createRecipientKeyInfo(
            int keyId, int kemId, int kdfId, int aeadId) throws IOException {
        try (ByteArrayOutputStream byteArrayOutputStream = new ByteArrayOutputStream();
                DataOutputStream dataOutputStream = new DataOutputStream(byteArrayOutputStream)) {
            byte[] ohttpReqLabelBytes = OHTTP_REQUEST_LABEL.getBytes(StandardCharsets.US_ASCII);
            dataOutputStream.write(ohttpReqLabelBytes);
            dataOutputStream.writeByte(0);

            // TODO(b/309095970) : Extract OhttpMessageHeader into its own class
            dataOutputStream.writeByte(keyId);
            dataOutputStream.writeShort(kemId);
            dataOutputStream.writeShort(kdfId);
            dataOutputStream.writeShort(aeadId);
            dataOutputStream.flush();

            return RecipientKeyInfo.create(byteArrayOutputStream.toByteArray());
        }
    }

    private static byte[] getCipherText(int encLength, byte[] encapsulatedRequest) {
        Preconditions.checkArgument(
                encLength + MESSAGE_HEADER_LENGTH_IN_BYTES <= encapsulatedRequest.length);
        int sizeOfCipherText =
                encapsulatedRequest.length - (MESSAGE_HEADER_LENGTH_IN_BYTES + encLength);
        byte[] destinationArray = new byte[sizeOfCipherText];
        System.arraycopy(
                encapsulatedRequest,
                /* srcPos= */ encLength + MESSAGE_HEADER_LENGTH_IN_BYTES,
                destinationArray,
                /* destPos= */ 0,
                sizeOfCipherText);
        return destinationArray;
    }

    private static byte[] getSecureRandomBytes(int length) {
        SecureRandom random = new SecureRandom();
        byte[] token = new byte[length];
        random.nextBytes(token);

        return token;
    }
}

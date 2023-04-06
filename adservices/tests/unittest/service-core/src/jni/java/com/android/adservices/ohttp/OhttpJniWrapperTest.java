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

import com.google.common.io.BaseEncoding;

import org.junit.Assert;
import org.junit.Test;

import java.io.ByteArrayOutputStream;
import java.nio.charset.StandardCharsets;

public class OhttpJniWrapperTest {

    private static final String SERVER_PUBLIC_KEY =
            "6d21cfe09fbea5122f9ebc2eb2a69fcc4f06408cd54aac934f012e76fcdcef62";
    private static final String KEM_SEED = "wwwwwwwwwwwwwwwwwwwwwwwwwwwwwwww";

    private final OhttpJniWrapper mOhttpJniWrapper = OhttpJniWrapper.getInstance();

    @Test
    public void hpkeKemDhkemX25519HkdfSha256_success_nonZeroReference() {
        Assert.assertNotEquals(mOhttpJniWrapper.hpkeKemDhkemX25519HkdfSha256(), 0);
    }

    @Test
    public void hpkeKdfHkdfSha256_success_nonZeroReference() {
        Assert.assertNotEquals(mOhttpJniWrapper.hpkeKdfHkdfSha256(), 0);
    }

    @Test
    public void hpkeAeadAes256Gcm_success_nonZeroReference() {
        Assert.assertNotEquals(mOhttpJniWrapper.hpkeAeadAes256Gcm(), 0);
    }

    @Test
    public void hpkeCtxNew_success_nonZeroReference() {
        Assert.assertNotEquals(mOhttpJniWrapper.hpkeCtxNew(), 0);
    }

    @Test
    public void freeHpkeContext_success_noError() {
        mOhttpJniWrapper.hpkeCtxFree(mOhttpJniWrapper.hpkeCtxNew());
    }

    @Test
    public void hpkeCtxSetupSenderWithSeed_returnsCorrectSharedSecret() throws Exception {
        KemNativeRef kem = KemNativeRef.getHpkeKemDhkemX25519HkdfSha256Reference();
        KdfNativeRef kdf = KdfNativeRef.getHpkeKdfHkdfSha256Reference();
        AeadNativeRef aead = AeadNativeRef.getHpkeAeadAes256GcmReference();
        HpkeContextNativeRef evpCtxSender = HpkeContextNativeRef.createHpkeContextReference();
        byte[] seedBytes = KEM_SEED.getBytes(StandardCharsets.US_ASCII);
        byte[] recipientKeyInfoBytes = createRecipientKeyInfoBytes();
        byte[] keyBytes = BaseEncoding.base16().lowerCase().decode(SERVER_PUBLIC_KEY);

        byte[] enc =
                mOhttpJniWrapper.hpkeCtxSetupSenderWithSeed(
                        evpCtxSender, kem, kdf, aead, keyBytes, recipientKeyInfoBytes, seedBytes);

        Assert.assertEquals(
                BaseEncoding.base16().lowerCase().encode(enc),
                "1cf579aba45a10ba1d1ef06d91fca2aa9ed0a1150515653155405d0b18cb9a67");
    }

    @Test
    public void hpkeEncrypt_returnsCorrectResponse() throws Exception {
        KemNativeRef kem = KemNativeRef.getHpkeKemDhkemX25519HkdfSha256Reference();
        KdfNativeRef kdf = KdfNativeRef.getHpkeKdfHkdfSha256Reference();
        AeadNativeRef aead = AeadNativeRef.getHpkeAeadAes256GcmReference();
        HpkeContextNativeRef evpCtxSender = HpkeContextNativeRef.createHpkeContextReference();
        byte[] seedBytes = KEM_SEED.getBytes(StandardCharsets.US_ASCII);
        byte[] recipientKeyInfoBytes = createRecipientKeyInfoBytes();
        byte[] keyBytes = BaseEncoding.base16().lowerCase().decode(SERVER_PUBLIC_KEY);
        String plainText = "test request 1";
        byte[] plainTextBytes = plainText.getBytes(StandardCharsets.US_ASCII);

        mOhttpJniWrapper.hpkeCtxSetupSenderWithSeed(
                evpCtxSender, kem, kdf, aead, keyBytes, recipientKeyInfoBytes, seedBytes);
        HpkeEncryptResponse response =
                mOhttpJniWrapper.hpkeEncrypt(
                        evpCtxSender,
                        kem,
                        kdf,
                        aead,
                        keyBytes,
                        recipientKeyInfoBytes,
                        seedBytes,
                        plainTextBytes,
                        null);

        Assert.assertEquals(
                BaseEncoding.base16().lowerCase().encode(response.encapsulatedSharedSecret()),
                "1cf579aba45a10ba1d1ef06d91fca2aa9ed0a1150515653155405d0b18cb9a67");
        Assert.assertEquals(
                BaseEncoding.base16().lowerCase().encode(response.cipherText()),
                "2ef2da3b97acee493624b9959f0fc6df008a6f0701c923c5a60ed0ed2c34");
    }

    @Test
    public void hpkeEncrypt_emptyPlainText_returnsNullCipherText() throws Exception {
        KemNativeRef kem = KemNativeRef.getHpkeKemDhkemX25519HkdfSha256Reference();
        KdfNativeRef kdf = KdfNativeRef.getHpkeKdfHkdfSha256Reference();
        AeadNativeRef aead = AeadNativeRef.getHpkeAeadAes256GcmReference();
        HpkeContextNativeRef evpCtxSender = HpkeContextNativeRef.createHpkeContextReference();
        byte[] seedBytes = KEM_SEED.getBytes(StandardCharsets.US_ASCII);
        byte[] recipientKeyInfoBytes = createRecipientKeyInfoBytes();
        byte[] keyBytes = BaseEncoding.base16().lowerCase().decode(SERVER_PUBLIC_KEY);

        mOhttpJniWrapper.hpkeCtxSetupSenderWithSeed(
                evpCtxSender, kem, kdf, aead, keyBytes, recipientKeyInfoBytes, seedBytes);
        HpkeEncryptResponse response =
                mOhttpJniWrapper.hpkeEncrypt(
                        evpCtxSender,
                        kem,
                        kdf,
                        aead,
                        keyBytes,
                        recipientKeyInfoBytes,
                        seedBytes,
                        null,
                        null);

        Assert.assertEquals(
                BaseEncoding.base16().lowerCase().encode(response.encapsulatedSharedSecret()),
                "1cf579aba45a10ba1d1ef06d91fca2aa9ed0a1150515653155405d0b18cb9a67");
        Assert.assertNull(response.cipherText());
    }

    @Test
    public void hpkeCtxSetupSenderWithSeed_nullContext_throwsException() throws Exception {
        KemNativeRef kem = KemNativeRef.getHpkeKemDhkemX25519HkdfSha256Reference();
        KdfNativeRef kdf = KdfNativeRef.getHpkeKdfHkdfSha256Reference();
        AeadNativeRef aead = AeadNativeRef.getHpkeAeadAes256GcmReference();
        HpkeContextNativeRef evpCtxSender = null;
        byte[] seedBytes = KEM_SEED.getBytes(StandardCharsets.US_ASCII);
        byte[] recipientKeyInfoBytes = createRecipientKeyInfoBytes();
        byte[] keyBytes = BaseEncoding.base16().lowerCase().decode(SERVER_PUBLIC_KEY);

        Assert.assertThrows(
                NullPointerException.class,
                () ->
                        mOhttpJniWrapper.hpkeCtxSetupSenderWithSeed(
                                evpCtxSender,
                                kem,
                                kdf,
                                aead,
                                keyBytes,
                                recipientKeyInfoBytes,
                                seedBytes));
    }

    @Test
    public void hpkeCtxSetupSenderWithSeed_emptyPublicKey_throwsException() throws Exception {
        KemNativeRef kem = KemNativeRef.getHpkeKemDhkemX25519HkdfSha256Reference();
        KdfNativeRef kdf = KdfNativeRef.getHpkeKdfHkdfSha256Reference();
        AeadNativeRef aead = AeadNativeRef.getHpkeAeadAes256GcmReference();
        HpkeContextNativeRef evpCtxSender = HpkeContextNativeRef.createHpkeContextReference();
        byte[] seedBytes = KEM_SEED.getBytes(StandardCharsets.US_ASCII);
        byte[] recipientKeyInfoBytes = createRecipientKeyInfoBytes();
        byte[] keyBytes = null;

        Assert.assertThrows(
                NullPointerException.class,
                () ->
                        mOhttpJniWrapper.hpkeCtxSetupSenderWithSeed(
                                evpCtxSender,
                                kem,
                                kdf,
                                aead,
                                keyBytes,
                                recipientKeyInfoBytes,
                                seedBytes));
    }

    @Test
    public void hpkeCtxSetupSenderWithSeed_invalidPublicKey_returnsNullArray() throws Exception {
        KemNativeRef kem = KemNativeRef.getHpkeKemDhkemX25519HkdfSha256Reference();
        KdfNativeRef kdf = KdfNativeRef.getHpkeKdfHkdfSha256Reference();
        AeadNativeRef aead = AeadNativeRef.getHpkeAeadAes256GcmReference();
        HpkeContextNativeRef evpCtxSender = HpkeContextNativeRef.createHpkeContextReference();
        String serverPublicKey = "abcd";
        byte[] seedBytes = KEM_SEED.getBytes(StandardCharsets.US_ASCII);
        byte[] recipientKeyInfoBytes = createRecipientKeyInfoBytes();
        byte[] keyBytes = BaseEncoding.base16().lowerCase().decode(serverPublicKey);

        byte[] enc =
                mOhttpJniWrapper.hpkeCtxSetupSenderWithSeed(
                        evpCtxSender, kem, kdf, aead, keyBytes, recipientKeyInfoBytes, seedBytes);

        Assert.assertNull(enc);
    }

    // This method will eventually be part of ObliviousHttpKeyConfig
    private static byte[] createRecipientKeyInfoBytes() throws Exception {
        /*
          hdr = concat(encode(1, keyID),
               encode(2, kemID),
               encode(2, kdfID),
               encode(2, aeadID))
          info = concat(encode_str("message/bhttp request"),
                encode(1, 0),
                hdr)
        */
        int keyId = 4;
        int kemId = 0x0020;
        int kdfId = 0x0001;
        int aeadId = 0x0002;
        String httpLabel = "message/bhttp request";
        byte[] b = httpLabel.getBytes(StandardCharsets.US_ASCII);
        byte[] header = new byte[7];
        header[0] = (byte) keyId;
        header[1] = header[3] = header[5] = (byte) 0;
        header[2] = (byte) kemId;
        header[4] = (byte) kdfId;
        header[6] = (byte) aeadId;

        ByteArrayOutputStream outputStream = new ByteArrayOutputStream();
        outputStream.write(b);
        outputStream.write((byte) 0);
        outputStream.write(header);

        return outputStream.toByteArray();
    }
}

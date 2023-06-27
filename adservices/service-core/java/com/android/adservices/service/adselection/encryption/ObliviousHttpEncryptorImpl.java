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

package com.android.adservices.service.adselection.encryption;

import static com.android.adservices.service.adselection.encryption.AdSelectionEncryptionKey.AdSelectionEncryptionKeyType.AUCTION;

import com.android.adservices.LoggerFactory;
import com.android.adservices.data.adselection.EncryptionContextDao;
import com.android.adservices.ohttp.ObliviousHttpClient;
import com.android.adservices.ohttp.ObliviousHttpKeyConfig;
import com.android.adservices.ohttp.ObliviousHttpRequest;
import com.android.adservices.ohttp.ObliviousHttpRequestContext;
import com.android.adservices.ohttp.algorithms.UnsupportedHpkeAlgorithmException;

import java.io.IOException;
import java.util.Objects;

/** Class to encrypt and decrypt bytes using OHTTP. */
public class ObliviousHttpEncryptorImpl implements ObliviousHttpEncryptor {
    private static final LoggerFactory.Logger sLogger = LoggerFactory.getFledgeLogger();
    private AdSelectionEncryptionKeyManager mEncryptionKeyManager;
    private ObliviousHttpRequestContextMarshaller mObliviousHttpRequestContextMarshaller;

    public ObliviousHttpEncryptorImpl(
            AdSelectionEncryptionKeyManager encryptionKeyManager,
            EncryptionContextDao encryptionContextDao) {
        Objects.requireNonNull(encryptionKeyManager);
        Objects.requireNonNull(encryptionContextDao);

        mEncryptionKeyManager = encryptionKeyManager;
        mObliviousHttpRequestContextMarshaller =
                new ObliviousHttpRequestContextMarshaller(encryptionContextDao);
    }

    /** Encrypts the given byte and stores the encryption context data keyed by given contextId */
    @Override
    public byte[] encryptBytes(byte[] plainText, long contextId, long keyFetchTimeoutMs) {

        try {
            ObliviousHttpKeyConfig config =
                    mEncryptionKeyManager.getLatestOhttpKeyConfigOfType(AUCTION, keyFetchTimeoutMs);

            Objects.requireNonNull(config);
            ObliviousHttpClient client = ObliviousHttpClient.create(config);

            Objects.requireNonNull(client);
            ObliviousHttpRequest request = client.createObliviousHttpRequest(plainText);

            Objects.requireNonNull(request);
            mObliviousHttpRequestContextMarshaller.insertAuctionEncryptionContext(
                    contextId, request.requestContext());

            return request.serialize();
        } catch (UnsupportedHpkeAlgorithmException e) {
            sLogger.e("Unexpected error during Oblivious Http Client creation");
            throw new RuntimeException(e);
        } catch (IOException e) {
            sLogger.e("Unexpected error during Oblivious HTTP Request creation");
            throw new RuntimeException(e);
        }
    }
    /**
     * Decrypts the given bytes using context stored in the DB keyed by the given storedContextId.
     */
    @Override
    public byte[] decryptBytes(byte[] encryptedBytes, long storedContextId) {
        Objects.requireNonNull(encryptedBytes);
        try {
            ObliviousHttpRequestContext context =
                    mObliviousHttpRequestContextMarshaller.getAuctionOblivioushttpRequestContext(
                            storedContextId);
            ObliviousHttpClient client = ObliviousHttpClient.create(context.keyConfig());

            return client.decryptObliviousHttpResponse(encryptedBytes, context);
        } catch (Exception e) {
            sLogger.e("Unexpected error during decryption");
            throw new RuntimeException(e);
        }
    }
}

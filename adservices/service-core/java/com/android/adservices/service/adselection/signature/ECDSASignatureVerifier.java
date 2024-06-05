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

package com.android.adservices.service.adselection.signature;

import android.annotation.NonNull;

import com.android.adservices.LoggerFactory;
import com.android.adservices.service.stats.SignatureVerificationLogger;

import com.google.common.annotations.VisibleForTesting;

import java.security.InvalidKeyException;
import java.security.KeyFactory;
import java.security.PublicKey;
import java.security.Signature;
import java.security.SignatureException;
import java.security.spec.InvalidKeySpecException;
import java.security.spec.X509EncodedKeySpec;
import java.util.Objects;

/** Verifies signatures using ECDSA algorithm (aka. ECDSAwithSHA256). */
public class ECDSASignatureVerifier implements SignatureVerifier {
    private static final LoggerFactory.Logger sLogger = LoggerFactory.getFledgeLogger();

    @VisibleForTesting
    protected static final String UNKNOWN_ERROR =
            "An unknown error occurred during signature verification";

    @VisibleForTesting
    protected static final String INVALID_KEY_ERROR =
            "Given key is either null or not suitable with the specified algorithm: ECDSA";

    @VisibleForTesting
    protected static final String SIGNATURE_VALIDATION_ERROR =
            "Encounter error during signature verification";

    @VisibleForTesting protected static final String EC_KEY_ALGORITHM = "EC";

    @VisibleForTesting
    protected static final String ECDSA_WITH_SHA256_SIGNING_ALGORITHM = "SHA256withECDSA";

    @NonNull private final SignatureVerificationLogger mSignatureVerificationLogger;

    public ECDSASignatureVerifier(
            @NonNull SignatureVerificationLogger signatureVerificationLogger) {
        Objects.requireNonNull(signatureVerificationLogger);

        mSignatureVerificationLogger = signatureVerificationLogger;
    }

    /**
     * Verifies a given signature against the given public key and the serialized data.
     *
     * <p>Verifies the signature created by ECDSAwithSHA256 algorithm
     *
     * @param publicKey public key paired with the private key used to sign the data
     * @param data serialized representation of the data
     * @param signature signature to verify
     */
    @Override
    public boolean verify(byte[] publicKey, byte[] data, byte[] signature) {
        try {
            KeyFactory keyFactory = KeyFactory.getInstance(EC_KEY_ALGORITHM);
            X509EncodedKeySpec keySpec = new X509EncodedKeySpec(publicKey);
            PublicKey aPublicKey = keyFactory.generatePublic(keySpec);
            // TODO(b/311368820): Make this singleton to avoid initializing it every time
            Signature ecdsa = Signature.getInstance(ECDSA_WITH_SHA256_SIGNING_ALGORITHM);
            ecdsa.initVerify(aPublicKey);
            ecdsa.update(data);
            return ecdsa.verify(signature);
        } catch (InvalidKeySpecException | InvalidKeyException | NullPointerException error) {
            sLogger.e(error, INVALID_KEY_ERROR);
            logWrongKeyFormatError();
            return false;
        } catch (SignatureException error) {
            sLogger.e(error, SIGNATURE_VALIDATION_ERROR);
            logSignatureFormatError();
            return false;
        } catch (Exception error) {
            sLogger.e(error, UNKNOWN_ERROR);
            logUnknownError();
            return false;
        }
    }

    private void logWrongKeyFormatError() {
        mSignatureVerificationLogger.addFailureDetailCountOfKeysWithWrongFormat();
    }

    private void logSignatureFormatError() {
        mSignatureVerificationLogger.setFailureDetailWrongSignatureFormat();
    }

    private void logUnknownError() {
        mSignatureVerificationLogger.setFailureDetailUnknownError();
    }
}

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

import static com.android.adservices.service.adselection.signature.ECDSASignatureVerifier.ECDSA_WITH_SHA256_SIGNING_ALGORITHM;
import static com.android.adservices.service.adselection.signature.ECDSASignatureVerifier.EC_KEY_ALGORITHM;

import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertTrue;
import static org.mockito.Mockito.verify;

import com.android.adservices.service.stats.SignatureVerificationLogger;
import com.android.adservices.shared.testing.SdkLevelSupportRule;

import org.junit.Before;
import org.junit.Rule;
import org.junit.Test;
import org.mockito.Mock;
import org.mockito.MockitoAnnotations;

import java.nio.charset.StandardCharsets;
import java.security.KeyPair;
import java.security.KeyPairGenerator;
import java.security.PublicKey;
import java.security.Signature;

public class ECDSASignatureVerifierTest {
    @Mock SignatureVerificationLogger mSignatureVerificationLoggerMock;
    private static final String MESSAGE = "Hello, world!";
    private static final int KEY_SIZE = 256;
    private SignatureVerifier mSignatureVerifier;
    private byte[] mPublicKeyBytes;
    private byte[] mDataBytes;
    private byte[] mSignatureBytes;

    @Rule(order = 0)
    public final SdkLevelSupportRule sdkLevel = SdkLevelSupportRule.forAtLeastS();

    @Before
    public void setup() throws Exception {
        MockitoAnnotations.initMocks(this);
        mSignatureVerifier = new ECDSASignatureVerifier(mSignatureVerificationLoggerMock);
        mDataBytes = MESSAGE.getBytes(StandardCharsets.UTF_8);

        // Generate a key pair for testing
        KeyPairGenerator keyPairGenerator = KeyPairGenerator.getInstance(EC_KEY_ALGORITHM);
        keyPairGenerator.initialize(KEY_SIZE);
        KeyPair pair = keyPairGenerator.generateKeyPair();
        PublicKey publicKey = pair.getPublic();
        mPublicKeyBytes = publicKey.getEncoded();

        // Sign mDataBytes
        Signature signature = Signature.getInstance(ECDSA_WITH_SHA256_SIGNING_ALGORITHM);
        signature.initSign(pair.getPrivate());
        signature.update(mDataBytes);
        mSignatureBytes = signature.sign();
    }

    @Test
    public void testVerifySignature_validKeyAndSignature_success() {
        assertTrue(mSignatureVerifier.verify(mPublicKeyBytes, mDataBytes, mSignatureBytes));
    }

    @Test
    public void testVerifySignature_validKeyWrongSignature_failure() {
        byte[] wrongSignature = new byte[] {1, 2, 3};
        assertFalse(mSignatureVerifier.verify(mPublicKeyBytes, mDataBytes, wrongSignature));
    }

    @Test
    public void testVerifySignature_malformedKey_failureLogged() {
        byte[] malformedKey = new byte[] {1, 2, 3};
        boolean isVerified = mSignatureVerifier.verify(malformedKey, mDataBytes, mSignatureBytes);

        assertFalse(isVerified);
        verify(mSignatureVerificationLoggerMock).addFailureDetailCountOfKeysWithWrongFormat();
    }
}

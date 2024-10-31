/*
 * Copyright (C) 2024 The Android Open Source Project
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

package com.android.adservices.service.kanon;

import static com.android.dx.mockito.inline.extended.ExtendedMockito.any;
import static com.android.dx.mockito.inline.extended.ExtendedMockito.doReturn;
import static com.android.dx.mockito.inline.extended.ExtendedMockito.doThrow;

import static com.google.common.truth.Truth.assertThat;

import static org.junit.Assert.assertThrows;
import static org.mockito.Mockito.doNothing;

import com.android.adservices.common.AdServicesDeviceSupportedRule;

import org.junit.Before;
import org.junit.Rule;
import org.junit.Test;
import org.mockito.Mock;
import org.mockito.junit.MockitoJUnit;
import org.mockito.junit.MockitoRule;

import java.security.InvalidAlgorithmParameterException;
import java.security.KeyPair;
import java.security.KeyPairGenerator;
import java.security.KeyStore;
import java.security.KeyStoreException;
import java.security.cert.Certificate;
import java.security.cert.CertificateException;

public final class KeyAttestationTest {
    private static final byte[] CHALLENGE =
            ("AHXUDhoSEFikqOefmo8xE7kGp/xjVMRDYBecBiHGxCN8rTv9W0Z4L/14d0OLB"
                            + "vC1VVzXBAnjgHoKLZzuJifTOaBJwGNIQ2ejnx3n6ayoRchDNCgpK29T+EAhBWzH")
                    .getBytes();

    private static final String ANDROID_KEY_STORE = "AndroidKeyStore";

    private static final String KEY_ALIAS = "PaKanonKeyAttestation";

    private KeyAttestation mKeyAttestation;

    @Mock private KeyStore mMockKeyStore;

    @Mock private KeyPair mMockKeyPair;

    @Mock private Certificate mMockCertificate;

    @Mock private KeyPairGenerator mMockKeyPairGenerator;

    @Rule(order = 1)
    public final AdServicesDeviceSupportedRule deviceSupportRule =
            new AdServicesDeviceSupportedRule();

    @Rule public final MockitoRule mockito = MockitoJUnit.rule();

    @Before
    public void setUp() throws Exception {
        mKeyAttestation =
                new KeyAttestation(/* useStrongBox= */ false, mMockKeyStore, mMockKeyPairGenerator);

        doReturn(new byte[] {1, 1}).when(mMockCertificate).getEncoded();
        doNothing().when(mMockKeyPairGenerator).initialize(any());
        doReturn(mMockKeyPair).when(mMockKeyPairGenerator).generateKeyPair();

        doNothing().when(mMockKeyStore).load(any());
        doReturn(new Certificate[] {mMockCertificate})
                .when(mMockKeyStore)
                .getCertificateChain(any());
    }

    @Test
    public void testGenerateAttestationRecord_success() throws Exception {
        KeyAttestationCertificateChainRecord record =
                mKeyAttestation.generateAttestationRecord(CHALLENGE);

        assertThat(record.encode().length).isGreaterThan(4);
    }

    @Test
    public void testGenerateAttestationRecord_nullKey_throwsException() {
        doReturn(null).when(mMockKeyPairGenerator).generateKeyPair();

        assertThrows(
                IllegalStateException.class,
                () -> mKeyAttestation.generateAttestationRecord(CHALLENGE));
    }

    @Test
    public void testGenerateHybridKey_initFailure() throws Exception {
        doThrow(new InvalidAlgorithmParameterException("Invalid Parameters"))
                .when(mMockKeyPairGenerator)
                .initialize(any());

        assertThrows(
                IllegalStateException.class,
                () -> mKeyAttestation.generateHybridKey(CHALLENGE, KEY_ALIAS));
    }

    @Test
    public void testGetAttestationRecordFromKeyAlias_success() throws Exception {
        KeyPair unused = mKeyAttestation.generateHybridKey(CHALLENGE, KEY_ALIAS);

        KeyAttestationCertificateChainRecord record =
                mKeyAttestation.getAttestationRecordFromKeyAlias(KEY_ALIAS);

        assertThat(record.encode().length).isGreaterThan(4);
    }

    @Test
    public void testGetAttestationRecordFromKeyAlias_certFailure() throws Exception {
        doThrow(new CertificateException("Cert Exception")).when(mMockKeyStore).load(any());

        KeyAttestationCertificateChainRecord record =
                mKeyAttestation.getAttestationRecordFromKeyAlias(KEY_ALIAS);

        assertThat(record.encode().length).isEqualTo(0);
    }

    @Test
    public void testGetAttestationRecordFromKeyAlias_keyStoreFailure() throws Exception {
        doThrow(new KeyStoreException("Key Store Exception"))
                .when(mMockKeyStore)
                .getCertificateChain(any());

        KeyAttestationCertificateChainRecord record =
                mKeyAttestation.getAttestationRecordFromKeyAlias(KEY_ALIAS);

        assertThat(record.encode().length).isEqualTo(0);
    }

    @Test
    public void testGetAttestationRecordFromKeyAlias_keyStoreReturnsNullChain_Failure()
            throws Exception {
        doReturn(null).when(mMockKeyStore).getCertificateChain(any());

        assertThrows(
                IllegalStateException.class,
                () -> mKeyAttestation.getAttestationRecordFromKeyAlias(KEY_ALIAS));
    }
}

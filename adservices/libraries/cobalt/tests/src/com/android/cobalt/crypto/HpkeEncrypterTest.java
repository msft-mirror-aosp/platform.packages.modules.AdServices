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

package com.android.cobalt.crypto;

import static com.google.common.truth.Truth.assertThat;

import static org.junit.Assert.assertThrows;
import static org.junit.Assert.assertTrue;
import static org.mockito.Mockito.when;

import static java.nio.charset.StandardCharsets.UTF_8;

import com.android.adservices.cobalt.HpkeEncryptImpl;
import com.android.cobalt.CobaltPipelineType;
import com.android.cobalt.testing.crypto.HpkeEncryptFactory;

import com.google.cobalt.EncryptedMessage;
import com.google.cobalt.EncryptedMessage.EncryptionScheme;
import com.google.cobalt.Envelope;
import com.google.cobalt.Observation;
import com.google.cobalt.ObservationBatch;
import com.google.cobalt.ObservationMetadata;
import com.google.cobalt.ObservationToEncrypt;
import com.google.protobuf.ByteString;

import org.junit.Before;
import org.junit.Rule;
import org.junit.Test;
import org.mockito.Mock;
import org.mockito.junit.MockitoJUnit;
import org.mockito.junit.MockitoRule;

import java.nio.charset.StandardCharsets;
import java.util.Optional;

public class HpkeEncrypterTest {
    @Rule public final MockitoRule mockito = MockitoJUnit.rule();
    // KeyIndex values for testing.
    private static final int SHUFFLER_KEY_INDEX_VALUE = 1;
    private static final int ANALYZER_KEY_INDEX_VALUE = 1337;
    static final byte[] SHUFFLER_KEY_PROD =
            new byte[] {
                -111, -111, 86, 123, -6, -114, -109, 121, -84, -113, -92, -5, 50, 103, 22, -53, 103,
                -57, 97, 11, 80, -5, 105, -26, -122, 106, 65, 107, -32, -74, -23, 10
            };
    private static final byte[] SHUFFLER_KEY_DEV =
            new byte[] {
                -90, -73, 32, -62, 119, -72, 48, -40, -127, -103, -7, -58, 35, -88, -4, 45, 33, 21,
                32, 48, 42, 43, 89, 33, -43, -81, -64, 111, 118, 76, 77, 32
            };
    static final byte[] ANALYZER_KEY_PROD =
            new byte[] {
                75, -121, 55, -37, -24, -80, -119, -113, -64, 25, -91, 114, -56, -23, 108, 5, 90,
                -3, 24, -62, 1, 109, 51, 123, -88, 36, 36, 0, 51, 104, -37, 1
            };
    private static final byte[] ANALYZER_KEY_DEV =
            new byte[] {
                -5, -81, 123, 9, -16, -83, -75, -106, 122, -13, 111, -106, 123, -65, -7, -78, 125,
                107, -23, 69, 120, -59, 40, 19, 6, 92, -119, 6, -58, 126, 125, 41
            };
    private static final byte[] SHUFFLER_CONTEXT_INFO_BYTES =
            "cobalt-1.0-shuffler".getBytes(StandardCharsets.UTF_8);
    private static final byte[] ANALYZER_CONTEXT_INFO_BYTES =
            "cobalt-1.0-analyzer".getBytes(StandardCharsets.UTF_8);
    private static final int SHUFFLER_KEY_INDEX_PROD = 11;
    private static final int SHUFFLER_KEY_INDEX_DEV = 9;
    private static final int ANALYZER_KEY_INDEX_PROD = 12;
    private static final int ANALYZER_KEY_INDEX_DEV = 10;
    private static final int X25519_PUBLIC_VALUE_LEN = 32;

    private static final int TEST_METRIC_ID = 25;
    private static final ByteString TEST_CONTRIBUTION_ID = ByteString.copyFromUtf8("testContId");

    private HpkeEncrypter mHpkeEncrypter;
    @Mock private PublicEncryptionKeys mPublicEncryptionKeys;

    @Before
    public void setUp() {
        when(mPublicEncryptionKeys.getAnalyzerKeyIndexDev()).thenReturn(ANALYZER_KEY_INDEX_DEV);
        when(mPublicEncryptionKeys.getAnalyzerKeyIndexProd()).thenReturn(ANALYZER_KEY_INDEX_PROD);
        when(mPublicEncryptionKeys.getShufflerKeyIndexDev()).thenReturn(SHUFFLER_KEY_INDEX_DEV);
        when(mPublicEncryptionKeys.getShufflerKeyIndexProd()).thenReturn(SHUFFLER_KEY_INDEX_PROD);
        when(mPublicEncryptionKeys.getX25519PublicValueLen()).thenReturn(X25519_PUBLIC_VALUE_LEN);
        when(mPublicEncryptionKeys.getShufflerContextInfoBytes())
                .thenReturn(SHUFFLER_CONTEXT_INFO_BYTES);
        when(mPublicEncryptionKeys.getAnalyzerContextInfoBytes())
                .thenReturn(ANALYZER_CONTEXT_INFO_BYTES);

        mHpkeEncrypter =
                new HpkeEncrypter(
                        HpkeEncryptFactory.noOpHpkeEncrypt(),
                        mPublicEncryptionKeys,
                        SHUFFLER_KEY_DEV,
                        SHUFFLER_KEY_INDEX_VALUE,
                        ANALYZER_KEY_DEV,
                        ANALYZER_KEY_INDEX_VALUE);
    }

    /** Round-trip an envelope through the {@link HpkeEncrypter} with a no-op encryption. */
    @Test
    public void encryptEnvelope_noOpRoundTripSuccess() throws Exception {
        Envelope envelope =
                Envelope.newBuilder()
                        .addBatch(
                                ObservationBatch.newBuilder()
                                        .setMetaData(
                                                ObservationMetadata.newBuilder()
                                                        .setMetricId(TEST_METRIC_ID)
                                                        .build()))
                        .build();

        // NoOp-encrypt the envelope.
        Optional<EncryptedMessage> encryptionResult = mHpkeEncrypter.encryptEnvelope(envelope);
        assertTrue(encryptionResult.isPresent());

        // Check that returned EncryptedMessage fields are correctly set.
        EncryptedMessage encryptedMessage = encryptionResult.get();
        assertThat(encryptedMessage.getKeyIndex()).isEqualTo(SHUFFLER_KEY_INDEX_VALUE);
        assertThat(encryptedMessage.getPublicKeyFingerprint()).isEmpty();
        assertThat(encryptedMessage.getScheme()).isEqualTo(EncryptionScheme.NONE);
        assertThat(encryptedMessage.getContributionId()).isEmpty();

        // NoOp-decrypt the envelope.
        byte[] envelopeDecryptedBytes = encryptedMessage.getCiphertext().toByteArray();
        Envelope decryptedEnvelope = Envelope.parseFrom(envelopeDecryptedBytes);

        // Check that the observation was correctly round-tripped.
        assertThat(decryptedEnvelope.getBatchCount()).isEqualTo(1);
        assertThat(decryptedEnvelope.getBatch(0).getMetaData().getMetricId())
                .isEqualTo(TEST_METRIC_ID);
    }

    /** Round-trip an observation through the {@link HpkeEncrypter} with a no-op encryption. */
    @Test
    public void encryptObservation_noOpRoundTripSuccess() throws Exception {
        String testObservationIdString = "testObsId";
        Observation observation =
                Observation.newBuilder()
                        .setRandomId(ByteString.copyFromUtf8(testObservationIdString))
                        .build();
        ObservationToEncrypt observationToEncrypt =
                ObservationToEncrypt.newBuilder()
                        .setContributionId(TEST_CONTRIBUTION_ID)
                        .setObservation(observation)
                        .build();

        // NoOp-Encrypt the observation.
        Optional<EncryptedMessage> encryptionResult =
                mHpkeEncrypter.encryptObservation(observationToEncrypt);
        assertTrue(encryptionResult.isPresent());

        // Check that returned EncryptedMessage fields are correctly set.
        EncryptedMessage encryptedMessage = encryptionResult.get();
        assertThat(encryptedMessage.getPublicKeyFingerprint()).isEmpty();
        assertThat(encryptedMessage.getScheme()).isEqualTo(EncryptionScheme.NONE);
        assertThat(encryptedMessage.getKeyIndex()).isEqualTo(ANALYZER_KEY_INDEX_VALUE);
        assertThat(encryptedMessage.getContributionId()).isEqualTo(TEST_CONTRIBUTION_ID);

        // NoOp-decrypt the envelope.
        byte[] observationDecryptedBytes = encryptedMessage.getCiphertext().toByteArray();
        Observation decryptedObservation = Observation.parseFrom(observationDecryptedBytes);

        // Check that the observation was correctly round-tripped.
        assertThat(decryptedObservation.getRandomId().toString(UTF_8))
                .isEqualTo(testObservationIdString);
    }

    @Test
    public void encryptEnvelope_hpkeSingleTripSuccess() throws Exception {
        Envelope envelope =
                Envelope.newBuilder()
                        .addBatch(
                                ObservationBatch.newBuilder()
                                        .setMetaData(
                                                ObservationMetadata.newBuilder()
                                                        .setMetricId(TEST_METRIC_ID)
                                                        .build()))
                        .build();

        mHpkeEncrypter =
                new HpkeEncrypter(
                        new HpkeEncryptImpl(),
                        mPublicEncryptionKeys,
                        SHUFFLER_KEY_DEV,
                        SHUFFLER_KEY_INDEX_VALUE,
                        ANALYZER_KEY_DEV,
                        ANALYZER_KEY_INDEX_VALUE);

        assertTrue(mHpkeEncrypter.encryptEnvelope(envelope).isPresent());
    }

    @Test
    public void encryptObservation_hpkeSingleTripSuccess() throws Exception {
        String testObservationIdString = "testObsId";
        Observation observation =
                Observation.newBuilder()
                        .setRandomId(ByteString.copyFromUtf8(testObservationIdString))
                        .build();
        ObservationToEncrypt observationToEncrypt =
                ObservationToEncrypt.newBuilder()
                        .setContributionId(TEST_CONTRIBUTION_ID)
                        .setObservation(observation)
                        .build();

        mHpkeEncrypter =
                new HpkeEncrypter(
                        new HpkeEncryptImpl(),
                        mPublicEncryptionKeys,
                        SHUFFLER_KEY_DEV,
                        SHUFFLER_KEY_INDEX_VALUE,
                        ANALYZER_KEY_DEV,
                        ANALYZER_KEY_INDEX_VALUE);

        assertTrue(mHpkeEncrypter.encryptObservation(observationToEncrypt).isPresent());
    }

    @Test
    public void encryptEmptyMessage_returnsEmptyOptional() throws Exception {
        assertThat(mHpkeEncrypter.encryptEnvelope(Envelope.newBuilder().build()))
                .isEqualTo(Optional.empty());
        assertThat(
                        mHpkeEncrypter.encryptObservation(
                                ObservationToEncrypt.newBuilder()
                                        .setContributionId(TEST_CONTRIBUTION_ID)
                                        .build()))
                .isEqualTo(Optional.empty());
    }

    @Test
    public void wrongSizedKey_throwsAssertionError() throws Exception {
        Envelope envelope =
                Envelope.newBuilder()
                        .addBatch(
                                ObservationBatch.newBuilder()
                                        .setMetaData(
                                                ObservationMetadata.newBuilder()
                                                        .setMetricId(TEST_METRIC_ID)
                                                        .build()))
                        .build();

        byte[] non32BytePublicKey = new byte[] {};
        mHpkeEncrypter =
                new HpkeEncrypter(
                        HpkeEncryptFactory.noOpHpkeEncrypt(),
                        mPublicEncryptionKeys,
                        non32BytePublicKey,
                        SHUFFLER_KEY_INDEX_VALUE,
                        non32BytePublicKey,
                        ANALYZER_KEY_INDEX_VALUE);
        assertThrows(AssertionError.class, () -> mHpkeEncrypter.encryptEnvelope(envelope));
    }

    @Test
    public void encryptionFailure_throwsEncryptionFailedException() throws Exception {
        Envelope envelope =
                Envelope.newBuilder()
                        .addBatch(
                                ObservationBatch.newBuilder()
                                        .setMetaData(
                                                ObservationMetadata.newBuilder()
                                                        .setMetricId(TEST_METRIC_ID)
                                                        .build()))
                        .build();

        mHpkeEncrypter =
                new HpkeEncrypter(
                        HpkeEncryptFactory.emptyHpkeEncrypt(),
                        mPublicEncryptionKeys,
                        SHUFFLER_KEY_DEV,
                        SHUFFLER_KEY_INDEX_VALUE,
                        ANALYZER_KEY_DEV,
                        ANALYZER_KEY_INDEX_VALUE);
        assertThrows(
                EncryptionFailedException.class, () -> mHpkeEncrypter.encryptEnvelope(envelope));
    }

    @Test
    public void checkKeyIndices_prodEnvironment() {
        when(mPublicEncryptionKeys.getShufflerKeyProd()).thenReturn(SHUFFLER_KEY_PROD);
        when(mPublicEncryptionKeys.getAnalyzerKeyProd()).thenReturn(ANALYZER_KEY_PROD);

        HpkeEncrypter hpkeEncrypter =
                HpkeEncrypter.createForEnvironment(
                        HpkeEncryptFactory.noOpHpkeEncrypt(),
                        CobaltPipelineType.PROD,
                        mPublicEncryptionKeys);
        assertThat(hpkeEncrypter.mShufflerKeyIndex).isEqualTo(SHUFFLER_KEY_INDEX_PROD);
        assertThat(hpkeEncrypter.mAnalyzerKeyIndex).isEqualTo(ANALYZER_KEY_INDEX_PROD);
    }

    @Test
    public void checkKeyIndices_devEnvironment() {
        when(mPublicEncryptionKeys.getShufflerKeyDev()).thenReturn(SHUFFLER_KEY_DEV);
        when(mPublicEncryptionKeys.getAnalyzerKeyDev()).thenReturn(ANALYZER_KEY_DEV);

        HpkeEncrypter hpkeEncrypter =
                HpkeEncrypter.createForEnvironment(
                        HpkeEncryptFactory.noOpHpkeEncrypt(),
                        CobaltPipelineType.DEV,
                        mPublicEncryptionKeys);
        assertThat(hpkeEncrypter.mShufflerKeyIndex).isEqualTo(SHUFFLER_KEY_INDEX_DEV);
        assertThat(hpkeEncrypter.mAnalyzerKeyIndex).isEqualTo(ANALYZER_KEY_INDEX_DEV);
    }
}

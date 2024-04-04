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

package com.android.adservices.service.stats;

import static com.android.adservices.service.stats.AdSelectionExecutionLoggerTest.START_ELAPSED_TIMESTAMP;
import static com.android.adservices.service.stats.AdsRelevanceExecutionLoggerImplTest.BINDER_ELAPSED_TIMESTAMP;
import static com.android.adservices.service.stats.SignatureVerificationStats.EMPTY_STRING;

import static com.google.common.truth.Truth.assertThat;

import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import android.adservices.common.CallerMetadata;

import com.android.adservices.common.SdkLevelSupportRule;
import com.android.adservices.shared.util.Clock;

import org.junit.Before;
import org.junit.Rule;
import org.junit.Test;
import org.mockito.ArgumentCaptor;
import org.mockito.Captor;
import org.mockito.Mock;
import org.mockito.MockitoAnnotations;

public class SignatureVerificationLoggerTest {
    public static final CallerMetadata sCallerMetadata =
            new CallerMetadata.Builder()
                    .setBinderElapsedTimestamp(BINDER_ELAPSED_TIMESTAMP)
                    .build();
    public static final int SIGNATURE_VERIFICATION_KEY_FETCH_LATENCY_MS = 3;
    public static final int SIGNATURE_VERIFICATION_SERIALIZATION_LATENCY_MS = 5;
    public static final int SIGNATURE_VERIFICATION_VERIFICATION_LATENCY_MS = 7;

    public static final long SIGNATURE_VERIFICATION_START_KEY_FETCH = START_ELAPSED_TIMESTAMP + 1L;
    public static final long SIGNATURE_VERIFICATION_END_KEY_FETCH =
            SIGNATURE_VERIFICATION_START_KEY_FETCH + SIGNATURE_VERIFICATION_KEY_FETCH_LATENCY_MS;
    public static final long SIGNATURE_VERIFICATION_START_SERIALIZATION =
            SIGNATURE_VERIFICATION_END_KEY_FETCH + 1L;
    public static final long SIGNATURE_VERIFICATION_END_SERIALIZATION =
            SIGNATURE_VERIFICATION_START_SERIALIZATION
                    + SIGNATURE_VERIFICATION_SERIALIZATION_LATENCY_MS;
    public static final long SIGNATURE_VERIFICATION_START_VERIFICATION =
            SIGNATURE_VERIFICATION_END_SERIALIZATION + 1L;
    public static final long SIGNATURE_VERIFICATION_END_VERIFICATION =
            SIGNATURE_VERIFICATION_START_VERIFICATION
                    + SIGNATURE_VERIFICATION_VERIFICATION_LATENCY_MS;
    @Captor ArgumentCaptor<SignatureVerificationStats> mSignatureVerificationStatsArgumentCaptor;
    @Mock private Clock mMockClock;
    @Mock private AdServicesLogger mAdServicesLoggerMock;

    @Rule(order = 0)
    public final SdkLevelSupportRule sdkLevel = SdkLevelSupportRule.forAtLeastS();

    @Before
    public void setUp() {
        MockitoAnnotations.initMocks(this);
    }

    @Test
    public void testSignatureVerification_successfulVerificationLogging() {
        SignatureVerificationStats.VerificationStatus signingVerificationStatus =
                SignatureVerificationStats.VerificationStatus.VERIFIED;
        long startTime = 1L;
        long keyFetchLatency = 2L;
        long serializationLatency = 3L;
        long verificationLatency = 4L;
        when(mMockClock.elapsedRealtime())
                .thenReturn(
                        startTime,
                        startTime,
                        startTime + keyFetchLatency,
                        startTime + keyFetchLatency,
                        startTime + keyFetchLatency + serializationLatency,
                        startTime + keyFetchLatency + serializationLatency,
                        startTime + keyFetchLatency + serializationLatency + verificationLatency);

        SignatureVerificationLogger logger = getSignatureVerificationLogger();
        logger.startKeyFetchForSignatureVerification();
        logger.endKeyFetchForSignatureVerification();
        logger.startSerializationForSignatureVerification();
        logger.endSerializationForSignatureVerification();
        logger.startSignatureVerification();
        logger.endSignatureVerification();
        logger.close(signingVerificationStatus.getValue());

        verify(mAdServicesLoggerMock)
                .logSignatureVerificationStats(mSignatureVerificationStatsArgumentCaptor.capture());
        SignatureVerificationStats stats = mSignatureVerificationStatsArgumentCaptor.getValue();

        assertThat(stats.getKeyFetchLatency()).isEqualTo(keyFetchLatency);
        assertThat(stats.getSerializationLatency()).isEqualTo(serializationLatency);
        assertThat(stats.getVerificationLatency()).isEqualTo(verificationLatency);
        assertThat(stats.getSignatureVerificationStatus()).isEqualTo(signingVerificationStatus);
    }

    @Test
    public void testSignatureVerification_failedVerificationLogging() {
        SignatureVerificationStats.VerificationStatus signingVerificationStatus =
                SignatureVerificationStats.VerificationStatus.VERIFICATION_FAILED;
        String failedSellerEnrollmentId = "failed-seller";
        String failedBuyerEnrollmentId = "failed-buyer";
        String failedCallerPackageName = "failed-caller-package-name";

        when(mMockClock.elapsedRealtime()).thenReturn(1L);

        SignatureVerificationLogger logger = getSignatureVerificationLogger();
        logger.startKeyFetchForSignatureVerification();
        logger.endKeyFetchForSignatureVerification();
        logger.startSerializationForSignatureVerification();
        logger.endSerializationForSignatureVerification();
        logger.startSignatureVerification();
        logger.endSignatureVerification();
        logger.setNumOfKeysFetched(1);
        logger.setFailedSignatureSellerEnrollmentId(failedSellerEnrollmentId);
        logger.setFailedSignatureBuyerEnrollmentId(failedBuyerEnrollmentId);
        logger.setFailedSignatureCallerPackageName(failedCallerPackageName);
        logger.setFailureDetailUnknownError();
        logger.setFailureDetailNoEnrollmentDataForBuyer();
        logger.setFailureDetailNoKeysFetchedForBuyer();
        logger.setFailureDetailWrongSignatureFormat();
        logger.addFailureDetailCountOfKeysWithWrongFormat();
        logger.addFailureDetailCountOfKeysWithWrongFormat();
        logger.addFailureDetailCountOfKeysFailedToVerifySignature();
        logger.addFailureDetailCountOfKeysFailedToVerifySignature();
        logger.addFailureDetailCountOfKeysFailedToVerifySignature();
        logger.close(signingVerificationStatus.getValue());

        verify(mAdServicesLoggerMock)
                .logSignatureVerificationStats(mSignatureVerificationStatsArgumentCaptor.capture());
        SignatureVerificationStats stats = mSignatureVerificationStatsArgumentCaptor.getValue();

        assertThat(stats.getSignatureVerificationStatus()).isEqualTo(signingVerificationStatus);
        assertThat(stats.getFailedSignatureSellerEnrollmentId())
                .isEqualTo(failedSellerEnrollmentId);
        assertThat(stats.getFailedSignatureBuyerEnrollmentId()).isEqualTo(failedBuyerEnrollmentId);
        assertThat(stats.getFailedSignatureCallerPackageName()).isEqualTo(failedCallerPackageName);
        assertThat(stats.getNumOfKeysFetched()).isEqualTo(1);
        assertThat(stats.getFailureDetailUnknownError()).isEqualTo(1);
        assertThat(stats.getFailureDetailNoEnrollmentDataForBuyer()).isEqualTo(1);
        assertThat(stats.getFailureDetailNoKeysFetchedForBuyer()).isEqualTo(1);
        assertThat(stats.getFailureDetailWrongSignatureFormat()).isEqualTo(1);
        assertThat(stats.getFailureDetailCountOfKeysWithWrongFormat()).isEqualTo(2);
        assertThat(stats.getFailureDetailCountOfKeysFailedToVerifySignature()).isEqualTo(3);
    }

    @Test
    public void testSignatureVerification_errorDetailLoggedOnlyInCaseOfError() {
        when(mMockClock.elapsedRealtime()).thenReturn(1L);

        SignatureVerificationStats.VerificationStatus signingVerificationStatus =
                SignatureVerificationStats.VerificationStatus.VERIFIED;
        SignatureVerificationLogger logger = getSignatureVerificationLogger();
        logger.startKeyFetchForSignatureVerification();
        logger.endKeyFetchForSignatureVerification();
        logger.startSerializationForSignatureVerification();
        logger.endSerializationForSignatureVerification();
        logger.startSignatureVerification();
        logger.endSignatureVerification();
        logger.setFailedSignatureSellerEnrollmentId("should-be-removed");
        logger.setFailedSignatureBuyerEnrollmentId("should-be-removed");
        logger.setFailedSignatureCallerPackageName("should-be-removed");
        logger.close(signingVerificationStatus.getValue());

        verify(mAdServicesLoggerMock)
                .logSignatureVerificationStats(mSignatureVerificationStatsArgumentCaptor.capture());
        SignatureVerificationStats stats = mSignatureVerificationStatsArgumentCaptor.getValue();

        assertThat(stats.getFailedSignatureSellerEnrollmentId()).isEqualTo(EMPTY_STRING);
        assertThat(stats.getFailedSignatureBuyerEnrollmentId()).isEqualTo(EMPTY_STRING);
        assertThat(stats.getFailedSignatureCallerPackageName()).isEqualTo(EMPTY_STRING);
    }

    private SignatureVerificationLogger getSignatureVerificationLogger() {
        return new SignatureVerificationLogger(mMockClock, mAdServicesLoggerMock);
    }
}

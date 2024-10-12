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

import static com.android.adservices.service.stats.SignatureVerificationLoggerImplTestFixture.SIGNATURE_VERIFICATION_END_KEY_FETCH;
import static com.android.adservices.service.stats.SignatureVerificationLoggerImplTestFixture.SIGNATURE_VERIFICATION_END_SERIALIZATION;
import static com.android.adservices.service.stats.SignatureVerificationLoggerImplTestFixture.SIGNATURE_VERIFICATION_END_VERIFICATION;
import static com.android.adservices.service.stats.SignatureVerificationLoggerImplTestFixture.SIGNATURE_VERIFICATION_KEY_FETCH_LATENCY_MS;
import static com.android.adservices.service.stats.SignatureVerificationLoggerImplTestFixture.SIGNATURE_VERIFICATION_SERIALIZATION_LATENCY_MS;
import static com.android.adservices.service.stats.SignatureVerificationLoggerImplTestFixture.SIGNATURE_VERIFICATION_START_KEY_FETCH;
import static com.android.adservices.service.stats.SignatureVerificationLoggerImplTestFixture.SIGNATURE_VERIFICATION_START_SERIALIZATION;
import static com.android.adservices.service.stats.SignatureVerificationLoggerImplTestFixture.SIGNATURE_VERIFICATION_START_VERIFICATION;
import static com.android.adservices.service.stats.SignatureVerificationLoggerImplTestFixture.SIGNATURE_VERIFICATION_VERIFICATION_LATENCY_MS;
import static com.android.adservices.service.stats.SignatureVerificationStats.EMPTY_STRING;

import static com.google.common.truth.Truth.assertThat;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import com.android.adservices.shared.util.Clock;

import org.junit.Before;
import org.junit.Test;
import org.mockito.ArgumentCaptor;
import org.mockito.Captor;
import org.mockito.Mock;
import org.mockito.MockitoAnnotations;

public class SignatureVerificationLoggerImplTest {
    @Captor ArgumentCaptor<SignatureVerificationStats> mSignatureVerificationStatsArgumentCaptor;
    @Mock private Clock mMockClock;
    @Mock private AdServicesLogger mAdServicesLoggerMock;

    @Before
    public void setUp() {
        MockitoAnnotations.initMocks(this);
    }

    @Test
    public void testSignatureVerification_successfulVerificationLogging() {
        SignatureVerificationStats.VerificationStatus signingVerificationStatus =
                SignatureVerificationStats.VerificationStatus.VERIFIED;
        when(mMockClock.elapsedRealtime())
                .thenReturn(
                        SIGNATURE_VERIFICATION_START_KEY_FETCH,
                        SIGNATURE_VERIFICATION_END_KEY_FETCH,
                        SIGNATURE_VERIFICATION_START_SERIALIZATION,
                        SIGNATURE_VERIFICATION_END_SERIALIZATION,
                        SIGNATURE_VERIFICATION_START_VERIFICATION,
                        SIGNATURE_VERIFICATION_END_VERIFICATION);

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

        assertThat(stats.getKeyFetchLatency())
                .isEqualTo(SIGNATURE_VERIFICATION_KEY_FETCH_LATENCY_MS);
        assertThat(stats.getSerializationLatency())
                .isEqualTo(SIGNATURE_VERIFICATION_SERIALIZATION_LATENCY_MS);
        assertThat(stats.getVerificationLatency())
                .isEqualTo(SIGNATURE_VERIFICATION_VERIFICATION_LATENCY_MS);
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

    @Test
    public void testSignatureVerification_missingEndKeyFetchTimestamp() {
        SignatureVerificationStats.VerificationStatus signingVerificationStatus =
                SignatureVerificationStats.VerificationStatus.VERIFIED;
        when(mMockClock.elapsedRealtime()).thenReturn(1L);

        SignatureVerificationLogger logger = getSignatureVerificationLogger();
        logger.startKeyFetchForSignatureVerification();
        // Skip logging end of key fetch
        logger.startSerializationForSignatureVerification();
        logger.endSerializationForSignatureVerification();
        logger.startSignatureVerification();
        logger.endSignatureVerification();
        logger.close(signingVerificationStatus.getValue());

        verify(mAdServicesLoggerMock, times(0)).logSignatureVerificationStats(any());
    }

    @Test
    public void testSignatureVerification_missingEndSerializationTimestamp() {
        SignatureVerificationStats.VerificationStatus signingVerificationStatus =
                SignatureVerificationStats.VerificationStatus.VERIFIED;
        when(mMockClock.elapsedRealtime()).thenReturn(1L);

        SignatureVerificationLogger logger = getSignatureVerificationLogger();
        logger.startKeyFetchForSignatureVerification();
        logger.endKeyFetchForSignatureVerification();
        logger.startSerializationForSignatureVerification();
        // Skip logging end of serialization
        logger.startSignatureVerification();
        logger.endSignatureVerification();
        logger.close(signingVerificationStatus.getValue());

        verify(mAdServicesLoggerMock, times(0)).logSignatureVerificationStats(any());
    }

    @Test
    public void testSignatureVerification_missingEndSignatureVerificationTimestamp() {
        SignatureVerificationStats.VerificationStatus signingVerificationStatus =
                SignatureVerificationStats.VerificationStatus.VERIFIED;
        when(mMockClock.elapsedRealtime()).thenReturn(1L);

        SignatureVerificationLogger logger = getSignatureVerificationLogger();
        logger.startKeyFetchForSignatureVerification();
        logger.endKeyFetchForSignatureVerification();
        logger.startSerializationForSignatureVerification();
        logger.endSerializationForSignatureVerification();
        // Skip logging end of signature verification
        logger.endSignatureVerification();
        logger.close(signingVerificationStatus.getValue());

        verify(mAdServicesLoggerMock, times(0)).logSignatureVerificationStats(any());
    }

    private SignatureVerificationLogger getSignatureVerificationLogger() {
        return new SignatureVerificationLoggerImpl(mAdServicesLoggerMock, mMockClock);
    }
}

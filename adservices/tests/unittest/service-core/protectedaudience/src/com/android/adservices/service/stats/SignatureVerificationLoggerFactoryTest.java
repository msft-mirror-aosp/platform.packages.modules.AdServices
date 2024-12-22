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

import static com.google.common.truth.Truth.assertThat;

import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyZeroInteractions;

import com.android.adservices.service.Flags;

import org.junit.Before;
import org.junit.Test;
import org.mockito.ArgumentCaptor;
import org.mockito.Captor;
import org.mockito.Mock;
import org.mockito.MockitoAnnotations;

public class SignatureVerificationLoggerFactoryTest {
    @Mock private AdServicesLogger mAdServicesLoggerMock;

    @Captor private ArgumentCaptor<SignatureVerificationStats> mStatsArgumentCaptor;

    @Before
    public void setup() {
        MockitoAnnotations.initMocks(this);
    }

    @Test
    public void testBothMetricsFlagsOn_returnsSignatureVerificationLoggerImpl() {
        Flags flags =
                new Flags() {
                    @Override
                    public boolean getFledgeAdSelectionContextualAdsMetricsEnabled() {
                        return true;
                    }
                };
        SignatureVerificationLoggerFactory loggerFactory =
                new SignatureVerificationLoggerFactory(mAdServicesLoggerMock, flags);
        int verificationStatus = 1;
        SignatureVerificationLogger logger = loggerFactory.getInstance();
        logger.startKeyFetchForSignatureVerification();
        logger.endKeyFetchForSignatureVerification();
        logger.startSerializationForSignatureVerification();
        logger.endSerializationForSignatureVerification();
        logger.startSignatureVerification();
        logger.endSignatureVerification();
        logger.close(verificationStatus);

        assertThat(logger).isInstanceOf(SignatureVerificationLoggerImpl.class);

        verify(mAdServicesLoggerMock, times(1))
                .logSignatureVerificationStats(mStatsArgumentCaptor.capture());
        SignatureVerificationStats stats = mStatsArgumentCaptor.getValue();
        assertThat(stats.getSignatureVerificationStatus().getValue()).isEqualTo(verificationStatus);
    }

    @Test
    public void testBothMetricsFlagsOff_returnsANoOpLoggerImpl() {
        Flags flags =
                new Flags() {
                    @Override
                    public boolean getFledgeAdSelectionContextualAdsMetricsEnabled() {
                        return false;
                    }
                };
        SignatureVerificationLoggerFactory loggerFactory =
                new SignatureVerificationLoggerFactory(mAdServicesLoggerMock, flags);
        int verificationStatus = 1;
        SignatureVerificationLogger logger = loggerFactory.getInstance();
        logger.close(verificationStatus);

        assertThat(logger).isInstanceOf(SignatureVerificationLoggerNoOp.class);
        verifyZeroInteractions(mAdServicesLoggerMock);
    }
}

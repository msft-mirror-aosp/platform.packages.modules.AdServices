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

package com.android.adservices.service.signals;


import static com.android.adservices.service.Flags.FLEDGE_FORCED_ENCODING_AFTER_SIGNALS_UPDATE_COOLDOWN_SECONDS;
import static com.android.adservices.service.stats.AdServicesStatsLog.AD_SERVICES_ERROR_REPORTED__PPAPI_NAME__PAS;
import static com.android.adservices.service.stats.AdsRelevanceStatusUtils.PAS_ENCODING_SOURCE_TYPE_SERVICE_IMPL;
import static com.android.dx.mockito.inline.extended.ExtendedMockito.verify;
import static com.android.dx.mockito.inline.extended.ExtendedMockito.verifyZeroInteractions;
import static com.android.dx.mockito.inline.extended.ExtendedMockito.when;

import android.adservices.common.AdTechIdentifier;
import android.adservices.common.CommonFixture;

import com.android.adservices.common.AdServicesExtendedMockitoTestCase;
import com.android.adservices.common.logging.annotations.ExpectErrorLogUtilWithExceptionCall;
import com.android.adservices.common.logging.annotations.SetErrorLogUtilDefaultParams;
import com.android.adservices.data.signals.DBEncodedPayload;
import com.android.adservices.data.signals.EncodedPayloadDao;
import com.android.adservices.data.signals.EncoderLogicHandler;
import com.android.adservices.data.signals.ProtectedSignalsDao;
import com.android.adservices.shared.testing.annotations.RequiresSdkLevelAtLeastT;
import com.android.adservices.shared.util.Clock;

import com.google.common.util.concurrent.ListeningExecutorService;
import com.google.common.util.concurrent.MoreExecutors;

import org.junit.Before;
import org.junit.Test;
import org.mockito.Mock;

import java.time.Duration;
import java.time.Instant;

@SetErrorLogUtilDefaultParams(
        throwable = ExpectErrorLogUtilWithExceptionCall.Any.class,
        ppapiName = AD_SERVICES_ERROR_REPORTED__PPAPI_NAME__PAS)
@RequiresSdkLevelAtLeastT(reason = "PAS is only supported on T+")
public class ForcedEncoderImplTest extends AdServicesExtendedMockitoTestCase {
    private static final Duration COOLDOWN_WINDOW_SECONDS =
            Duration.ofSeconds(FLEDGE_FORCED_ENCODING_AFTER_SIGNALS_UPDATE_COOLDOWN_SECONDS);
    private static final long FIXED_TIME = 100L;
    private static final AdTechIdentifier BUYER = CommonFixture.VALID_BUYER_1;
    private static final String ENCODER_LOGIC = "function buyer_EncodeJs() {\" correct result \"}";
    private final ListeningExecutorService mDirectExecutor =
            MoreExecutors.newDirectExecutorService();
    @Mock EncoderLogicHandler mEncoderLogicHandlerMock;
    @Mock EncodedPayloadDao mEncodedPayloadDaoMock;
    @Mock ProtectedSignalsDao mProtectedSignalsDaoMock;
    @Mock PeriodicEncodingJobWorker mEncodingJobWorkerMock;
    @Mock private Clock mClockMock;
    private ForcedEncoder mSyncForcedEncoder;

    @Before
    public void setup() {
        when(mClockMock.currentTimeMillis()).thenReturn(FIXED_TIME);

        mSyncForcedEncoder =
                new ForcedEncoderImpl(
                        COOLDOWN_WINDOW_SECONDS.getSeconds(),
                        mEncoderLogicHandlerMock,
                        mEncodedPayloadDaoMock,
                        mProtectedSignalsDaoMock,
                        mEncodingJobWorkerMock,
                        mDirectExecutor,
                        mClockMock);
    }

    private DBEncodedPayload getDbEncodedPayloadBeforeNowBy(Duration duration) {
        return DBEncodedPayload.create(
                BUYER,
                1,
                Instant.ofEpochMilli(FIXED_TIME).minus(duration),
                new byte[] {0x22, 0x33});
    }

    @Test
    public void test_canAttemptForcedEncodingForBuyer_absentRegisteredEncoder_false()
            throws Exception {
        when(mEncoderLogicHandlerMock.getEncoder(BUYER)).thenReturn(null);

        mSyncForcedEncoder.forceEncodingAndUpdateEncoderForBuyer(BUYER);
        verifyZeroInteractions(
                mEncodedPayloadDaoMock, mProtectedSignalsDaoMock, mEncodingJobWorkerMock);
    }

    @Test
    public void test_canAttemptForcedEncodingForBuyer_hasEncodedPayloadBeforeCooldownStart_true()
            throws Exception {
        when(mEncoderLogicHandlerMock.getEncoder(BUYER)).thenReturn(ENCODER_LOGIC);
        when(mEncodedPayloadDaoMock.getEncodedPayload(BUYER))
                .thenReturn(getDbEncodedPayloadBeforeNowBy(COOLDOWN_WINDOW_SECONDS.plusSeconds(5)));

        mSyncForcedEncoder.forceEncodingAndUpdateEncoderForBuyer(BUYER);

        verifyZeroInteractions(mProtectedSignalsDaoMock);
        verify(mEncodingJobWorkerMock)
                .encodeProtectedSignals(PAS_ENCODING_SOURCE_TYPE_SERVICE_IMPL);
    }

    @Test
    public void test_canAttemptForcedEncodingForBuyer_hasEncodedPayloadAfterCooldownStart_false()
            throws Exception {
        when(mEncoderLogicHandlerMock.getEncoder(BUYER)).thenReturn(ENCODER_LOGIC);
        when(mEncodedPayloadDaoMock.getEncodedPayload(BUYER))
                .thenReturn(
                        getDbEncodedPayloadBeforeNowBy(COOLDOWN_WINDOW_SECONDS.minusSeconds(5)));

        // Don't need to wait for the thread completion since `mForcedEncoder`
        // is using a `directExecutor`
        mSyncForcedEncoder.forceEncodingAndUpdateEncoderForBuyer(BUYER);
        verifyZeroInteractions(mProtectedSignalsDaoMock, mEncodingJobWorkerMock);
    }

    @Test
    public void test_canAttemptForcedEncodingForBuyer_hasRawSignals_true() throws Exception {
        when(mEncoderLogicHandlerMock.getEncoder(BUYER)).thenReturn(ENCODER_LOGIC);
        when(mEncodedPayloadDaoMock.getEncodedPayload(BUYER)).thenReturn(null);
        when(mProtectedSignalsDaoMock.hasSignalsFromBuyer(BUYER)).thenReturn(true);

        mSyncForcedEncoder.forceEncodingAndUpdateEncoderForBuyer(BUYER);
        verify(mEncodingJobWorkerMock)
                .encodeProtectedSignals(PAS_ENCODING_SOURCE_TYPE_SERVICE_IMPL);
    }

    @Test
    public void test_canAttemptForcedEncodingForBuyer_absentRawSignals_true() throws Exception {
        when(mEncoderLogicHandlerMock.getEncoder(BUYER)).thenReturn(ENCODER_LOGIC);
        when(mEncodedPayloadDaoMock.getEncodedPayload(BUYER)).thenReturn(null);
        when(mProtectedSignalsDaoMock.hasSignalsFromBuyer(BUYER)).thenReturn(false);

        mSyncForcedEncoder.forceEncodingAndUpdateEncoderForBuyer(BUYER);
        verifyZeroInteractions(mEncodingJobWorkerMock);
    }
}

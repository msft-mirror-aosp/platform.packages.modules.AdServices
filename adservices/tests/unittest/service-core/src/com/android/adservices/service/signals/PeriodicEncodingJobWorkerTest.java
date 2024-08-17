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

package com.android.adservices.service.signals;

import static com.google.common.truth.Truth.assertThat;

import static org.junit.Assert.assertEquals;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyInt;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyZeroInteractions;
import static org.mockito.Mockito.when;

import android.adservices.common.AdTechIdentifier;
import android.adservices.common.CommonFixture;

import com.android.adservices.concurrency.AdServicesExecutors;
import com.android.adservices.data.enrollment.EnrollmentDao;
import com.android.adservices.data.signals.DBEncodedPayload;
import com.android.adservices.data.signals.DBEncoderLogicMetadata;
import com.android.adservices.data.signals.DBProtectedSignal;
import com.android.adservices.data.signals.EncodedPayloadDao;
import com.android.adservices.data.signals.EncoderLogicHandler;
import com.android.adservices.data.signals.EncoderLogicMetadataDao;
import com.android.adservices.data.signals.EncoderPersistenceDao;
import com.android.adservices.data.signals.ProtectedSignalsDao;
import com.android.adservices.service.Flags;
import com.android.adservices.service.stats.AdServicesLogger;
import com.android.adservices.service.stats.pas.EncodingJobRunStats;
import com.android.adservices.shared.testing.SdkLevelSupportRule;
import com.android.adservices.shared.util.Clock;

import com.google.common.collect.ImmutableList;
import com.google.common.util.concurrent.FluentFuture;
import com.google.common.util.concurrent.Futures;
import com.google.common.util.concurrent.ListenableFuture;
import com.google.common.util.concurrent.ListeningExecutorService;

import org.junit.Before;
import org.junit.Rule;
import org.junit.Test;
import org.mockito.ArgumentCaptor;
import org.mockito.Captor;
import org.mockito.Mock;
import org.mockito.junit.MockitoJUnit;
import org.mockito.junit.MockitoRule;

import java.time.Instant;
import java.util.Collections;
import java.util.HashSet;
import java.util.List;
import java.util.Set;
import java.util.concurrent.ExecutionException;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.TimeoutException;

public class PeriodicEncodingJobWorkerTest {

    private static final AdTechIdentifier BUYER = CommonFixture.VALID_BUYER_1;
    private static final AdTechIdentifier BUYER_2 = CommonFixture.VALID_BUYER_2;
    private static final int VERSION_1 = 1;
    private static final int VERSION_2 = 2;
    private static final DBEncoderLogicMetadata DB_ENCODER_LOGIC_BUYER_1 =
            DBEncoderLogicMetadata.builder()
                    .setBuyer(BUYER)
                    .setVersion(VERSION_1)
                    .setCreationTime(Instant.now())
                    .build();
    private static final DBEncoderLogicMetadata DB_ENCODER_LOGIC_BUYER_2 =
            DBEncoderLogicMetadata.builder()
                    .setBuyer(BUYER_2)
                    .setVersion(VERSION_2)
                    .setCreationTime(Instant.now())
                    .build();

    private static final List<DBProtectedSignal> FAKE_SIGNALS =
            ImmutableList.of(
                    DBProtectedSignal.create(
                            0L,
                            BUYER,
                            "v1".getBytes(),
                            "valid value".getBytes(),
                            Instant.now(),
                            "package name"));

    private static final int MAX_SIZE_BYTES = 100;

    @Rule public MockitoRule rule = MockitoJUnit.rule();

    @Mock private EncoderLogicHandler mEncoderLogicHandler;
    @Mock private EncoderLogicMetadataDao mEncoderLogicMetadataDao;
    @Mock private EncoderPersistenceDao mEncoderPersistenceDao;
    @Mock private EncodedPayloadDao mEncodedPayloadDao;
    @Mock private ProtectedSignalsDao mProtectedSignalsDao;
    @Mock private SignalsScriptEngine mScriptEngine;
    @Mock Flags mFlags;
    @Mock private EnrollmentDao mEnrollmentDao;
    @Mock private Clock mClock;
    @Mock private AdServicesLogger mAdServicesLogger;

    @Captor private ArgumentCaptor<DBEncodedPayload> mEncodedPayloadCaptor;

    private ListeningExecutorService mBackgroundExecutor =
            AdServicesExecutors.getBackgroundExecutor();
    private ListeningExecutorService mLightWeightExecutor =
            AdServicesExecutors.getLightWeightExecutor();

    private PeriodicEncodingJobWorker mJobWorker;
    private ArgumentCaptor<EncodingJobRunStats> mEncodingJobRunStatsArgumentCaptor;

    @Rule(order = 0)
    public final SdkLevelSupportRule sdkLevel = SdkLevelSupportRule.forAtLeastT();

    @Before
    public void setup() {
        int maxFailedRun =
                Flags.PROTECTED_SIGNALS_MAX_JS_FAILURE_EXECUTION_ON_CERTAIN_VERSION_BEFORE_STOP;
        when(mFlags.getProtectedSignalsEncodedPayloadMaxSizeBytes()).thenReturn(MAX_SIZE_BYTES);
        when(mFlags.getProtectedSignalsMaxJsFailureExecutionOnCertainVersionBeforeStop())
                .thenReturn(maxFailedRun);
        when(mFlags.getPasExtendedMetricsEnabled()).thenReturn(true);
        when(mFlags.getPasScriptExecutionTimeoutMs()).thenReturn(1000);
        when(mFlags.getPasEncodingJobImprovementsEnabled()).thenReturn(true);
        mJobWorker =
                new PeriodicEncodingJobWorker(
                        mEncoderLogicHandler,
                        mEncoderLogicMetadataDao,
                        mEncodedPayloadDao,
                        mProtectedSignalsDao,
                        mScriptEngine,
                        mBackgroundExecutor,
                        mLightWeightExecutor,
                        mFlags,
                        mEnrollmentDao,
                        mClock,
                        mAdServicesLogger);
    }

    @Test
    public void testEncodeProtectedSignalsGracefullyHandleFailures()
            throws ExecutionException, InterruptedException, TimeoutException {
        setupEncodingJobRunStatsLogging();

        // Buyer 1 encoding would succeed
        String encoderLogic1 = "function buyer1_EncodeJs() {\" correct result \"}";
        when(mEncoderLogicHandler.getEncoder(BUYER)).thenReturn(encoderLogic1);
        when(mProtectedSignalsDao.getSignalsByBuyer(BUYER)).thenReturn(FAKE_SIGNALS);
        byte[] validResponse = new byte[] {0x01, 0x02, 0x03, 0x04, 0x05, 0x0A};
        ListenableFuture<byte[]> successResponse = Futures.immediateFuture(validResponse);
        when(mScriptEngine.encodeSignals(eq(encoderLogic1), any(), anyInt(), any(), any()))
                .thenReturn(successResponse);

        // Buyer 2 encoding would fail
        String encoderLogic2 = "function buyer2_EncodeJs() {\" throws exception \"}";
        when(mEncoderLogicHandler.getEncoder(BUYER_2)).thenReturn(encoderLogic2);
        when(mProtectedSignalsDao.getSignalsByBuyer(BUYER_2)).thenReturn(FAKE_SIGNALS);
        ListenableFuture<byte[]> failureResponse =
                Futures.immediateFailedFuture(new RuntimeException("Random exception"));
        when(mScriptEngine.encodeSignals(eq(encoderLogic2), any(), anyInt(), any(), any()))
                .thenReturn(failureResponse);
        when(mEncoderLogicHandler.getAllRegisteredEncoders())
                .thenReturn(List.of(DB_ENCODER_LOGIC_BUYER_1, DB_ENCODER_LOGIC_BUYER_2));

        // This should gracefully handle Buyer_2 failure and not impact Buyer_1's encoding
        Void unused = mJobWorker.encodeProtectedSignals().get(5, TimeUnit.SECONDS);

        verify(mEncoderLogicHandler).getAllRegisteredEncoders();
        verify(mEncoderLogicHandler).getEncoder(BUYER);
        //        verify(mSignalStorageManager).getSignals(BUYER);
        verify(mEncodedPayloadDao, times(1)).getEncodedPayload(BUYER);
        verify(mEncodedPayloadDao, times(1)).getEncodedPayload(BUYER_2);
        verify(mEncodedPayloadDao, times(1)).persistEncodedPayload(mEncodedPayloadCaptor.capture());
        verify(mEncoderLogicHandler).updateEncoderFailedCount(BUYER_2, 1);
        assertEquals(BUYER, mEncodedPayloadCaptor.getValue().getBuyer());
        assertEquals(VERSION_1, mEncodedPayloadCaptor.getValue().getVersion());
        assertEquals(
                getSetFromBytes(validResponse),
                getSetFromBytes(mEncodedPayloadCaptor.getValue().getEncodedPayload()));

        verifyEncodingJobRunStatsLogging(
                /* countOfSignalEncodingSuccesses */ 1,
                /* countOfSignalEncodingFailures */ 1,
                /* countOfSignalEncodingSkips */ 0);
    }


    @Test
    public void testUpdatesEncodersAllUpdatedEncodersDoNotDownloadAgain() {
        when(mEncoderLogicMetadataDao.getBuyersWithEncodersBeforeTime(any()))
                .thenReturn(Collections.emptyList());
        verifyZeroInteractions(mEncoderLogicHandler);
    }

    @Test
    public void testEncodeProtectedSignalsAlsoUpdatesEncoders()
            throws ExecutionException, InterruptedException, TimeoutException {
        setupEncodingJobRunStatsLogging();

        when(mEncoderLogicMetadataDao.getBuyersWithEncodersBeforeTime(any()))
                .thenReturn(List.of(BUYER, BUYER_2));
        when(mEncoderLogicHandler.downloadAndUpdate(any(), any()))
                .thenReturn(FluentFuture.from(Futures.immediateFuture(true)));

        Void unused = mJobWorker.encodeProtectedSignals().get(5, TimeUnit.SECONDS);
        verify(mEncoderLogicHandler).downloadAndUpdate(eq(BUYER), any());
        verify(mEncoderLogicHandler).downloadAndUpdate(eq(BUYER_2), any());

        verifyEncodingJobRunStatsLogging(
                /* countOfSignalEncodingSuccesses */ 0,
                /* countOfSignalEncodingFailures */ 0,
                /* countOfSignalEncodingSkips */ 0);
    }

    @Test
    public void testEncodeProtectedSignalsAlsoUpdatesEncodersIsNotAffectedByEncodingFailures()
            throws ExecutionException, InterruptedException, TimeoutException {
        setupEncodingJobRunStatsLogging();

        when(mEncoderLogicMetadataDao.getAllBuyersWithRegisteredEncoders())
                .thenReturn(List.of(BUYER, BUYER_2));

        String encoderLogic = "function buyer1_EncodeJs() {\" correct result \"}";
        int version1 = 1;
        DBEncoderLogicMetadata fakeEncoderLogicEntry =
                DBEncoderLogicMetadata.builder()
                        .setBuyer(BUYER)
                        .setVersion(version1)
                        .setCreationTime(Instant.now())
                        .build();
        when(mEncoderLogicMetadataDao.getMetadata(any())).thenReturn(fakeEncoderLogicEntry);
        when(mEncoderPersistenceDao.getEncoder(any())).thenReturn(encoderLogic);
        when(mProtectedSignalsDao.getSignalsByBuyer(BUYER)).thenReturn(ImmutableList.of());

        // All the encodings are wired to fail with exceptions
        ListenableFuture<byte[]> failureResponse =
                Futures.immediateFailedFuture(new RuntimeException("Random exception"));
        when(mScriptEngine.encodeSignals(any(), any(), anyInt(), any(), any()))
                .thenReturn(failureResponse);

        when(mEncoderLogicMetadataDao.getBuyersWithEncodersBeforeTime(any()))
                .thenReturn(List.of(BUYER, BUYER_2));
        when(mEncoderLogicHandler.downloadAndUpdate(any(), any()))
                .thenReturn(FluentFuture.from(Futures.immediateFuture(true)));

        Void unused = mJobWorker.encodeProtectedSignals().get(5, TimeUnit.SECONDS);
        verify(mEncoderLogicHandler).downloadAndUpdate(eq(BUYER), any());
        verify(mEncoderLogicHandler).downloadAndUpdate(eq(BUYER_2), any());

        verifyEncodingJobRunStatsLogging(
                /* countOfSignalEncodingSuccesses */ 0,
                /* countOfSignalEncodingFailures */ 0,
                /* countOfSignalEncodingSkips */ 0);
    }

    private Set<Byte> getSetFromBytes(byte[] bytes) {
        Set<Byte> byteSet = new HashSet<>();

        for (byte b : bytes) {
            byteSet.add(b);
        }
        return byteSet;
    }

    private void setupEncodingJobRunStatsLogging() {
        mEncodingJobRunStatsArgumentCaptor = ArgumentCaptor.forClass(EncodingJobRunStats.class);
    }

    private void verifyEncodingJobRunStatsLogging(
            int countOfSignalEncodingSuccesses,
            int countOfSignalEncodingFailures,
            int countOfSignalEncodingSkips) {
        verify(mAdServicesLogger)
                .logEncodingJobRunStats(mEncodingJobRunStatsArgumentCaptor.capture());

        EncodingJobRunStats stats = mEncodingJobRunStatsArgumentCaptor.getValue();
        assertThat(stats.getSignalEncodingSuccesses()).isEqualTo(countOfSignalEncodingSuccesses);
        assertThat(stats.getSignalEncodingFailures()).isEqualTo(countOfSignalEncodingFailures);
        assertThat(stats.getSignalEncodingSkips()).isEqualTo(countOfSignalEncodingSkips);
    }
}

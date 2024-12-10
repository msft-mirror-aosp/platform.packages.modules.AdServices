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

import static com.android.adservices.service.stats.AdServicesStatsLog.AD_SERVICES_ERROR_REPORTED__ERROR_CODE__PAS_FAILED_PER_BUYER_ENCODING;
import static com.android.adservices.service.stats.AdServicesStatsLog.AD_SERVICES_ERROR_REPORTED__ERROR_CODE__PAS_VALIDATE_AND_PERSIST_ENCODED_PAYLOAD_FAILURE;
import static com.android.adservices.service.stats.AdServicesStatsLog.AD_SERVICES_ERROR_REPORTED__PPAPI_NAME__PAS;

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

import com.android.adservices.common.AdServicesExtendedMockitoTestCase;
import com.android.adservices.common.logging.annotations.ExpectErrorLogUtilWithExceptionCall;
import com.android.adservices.common.logging.annotations.SetErrorLogUtilDefaultParams;
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
import com.android.adservices.service.stats.AdsRelevanceStatusUtils;
import com.android.adservices.service.stats.pas.EncodingJobRunStats;
import com.android.adservices.shared.testing.annotations.RequiresSdkLevelAtLeastT;
import com.android.adservices.shared.util.Clock;

import com.google.common.collect.ImmutableList;
import com.google.common.util.concurrent.FluentFuture;
import com.google.common.util.concurrent.Futures;
import com.google.common.util.concurrent.ListenableFuture;
import com.google.common.util.concurrent.ListeningExecutorService;

import org.junit.Before;
import org.junit.Test;
import org.mockito.ArgumentCaptor;
import org.mockito.Captor;
import org.mockito.Mock;

import java.time.Instant;
import java.util.Collections;
import java.util.HashSet;
import java.util.List;
import java.util.Set;
import java.util.concurrent.ExecutionException;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.TimeoutException;

@SetErrorLogUtilDefaultParams(
        throwable = ExpectErrorLogUtilWithExceptionCall.Any.class,
        ppapiName = AD_SERVICES_ERROR_REPORTED__PPAPI_NAME__PAS)
@RequiresSdkLevelAtLeastT(reason = "PAS is only supported on T+")
public class PeriodicEncodingJobWorkerTest extends AdServicesExtendedMockitoTestCase {

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
    private static final int PAS_ENCODING_SOURCE_TYPE =
            AdsRelevanceStatusUtils.PAS_ENCODING_SOURCE_TYPE_ENCODING_JOB_SERVICE;

    @Mock private EncoderLogicHandler mEncoderLogicHandler;
    @Mock private EncoderLogicMetadataDao mEncoderLogicMetadataDao;
    @Mock private EncoderPersistenceDao mEncoderPersistenceDao;
    @Mock private EncodedPayloadDao mEncodedPayloadDao;
    @Mock private ProtectedSignalsDao mProtectedSignalsDao;
    @Mock private SignalsScriptEngine mScriptEngine;
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

    @Before
    public void setup() {
        int maxFailedRun =
                Flags.PROTECTED_SIGNALS_MAX_JS_FAILURE_EXECUTION_ON_CERTAIN_VERSION_BEFORE_STOP;
        when(mMockFlags.getProtectedSignalsEncodedPayloadMaxSizeBytes()).thenReturn(MAX_SIZE_BYTES);
        when(mMockFlags.getProtectedSignalsMaxJsFailureExecutionOnCertainVersionBeforeStop())
                .thenReturn(maxFailedRun);
        when(mMockFlags.getPasExtendedMetricsEnabled()).thenReturn(true);
        when(mMockFlags.getPasScriptExecutionTimeoutMs()).thenReturn(1000);
        when(mMockFlags.getPasEncodingJobImprovementsEnabled()).thenReturn(true);
        when(mMockFlags.getFledgeEnableForcedEncodingAfterSignalsUpdate()).thenReturn(true);
        mJobWorker =
                new PeriodicEncodingJobWorker(
                        mEncoderLogicHandler,
                        mEncoderLogicMetadataDao,
                        mEncodedPayloadDao,
                        mProtectedSignalsDao,
                        mScriptEngine,
                        mBackgroundExecutor,
                        mLightWeightExecutor,
                        mMockFlags,
                        mEnrollmentDao,
                        mClock,
                        mAdServicesLogger);
    }

    @Test
    @ExpectErrorLogUtilWithExceptionCall(
            errorCode = AD_SERVICES_ERROR_REPORTED__ERROR_CODE__PAS_FAILED_PER_BUYER_ENCODING)
    @ExpectErrorLogUtilWithExceptionCall(
            errorCode = AD_SERVICES_ERROR_REPORTED__ERROR_CODE__PAS_VALIDATE_AND_PERSIST_ENCODED_PAYLOAD_FAILURE)
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
        Void unused =
                mJobWorker
                        .encodeProtectedSignals(PAS_ENCODING_SOURCE_TYPE)
                        .get(5, TimeUnit.SECONDS);

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
                /* countOfSignalEncodingSkips */ 0,
                AdsRelevanceStatusUtils.PAS_ENCODING_SOURCE_TYPE_ENCODING_JOB_SERVICE);
    }

    @Test
    @ExpectErrorLogUtilWithExceptionCall(
            errorCode = AD_SERVICES_ERROR_REPORTED__ERROR_CODE__PAS_FAILED_PER_BUYER_ENCODING,
            times = 3)
    @ExpectErrorLogUtilWithExceptionCall(
            errorCode =
                    AD_SERVICES_ERROR_REPORTED__ERROR_CODE__PAS_VALIDATE_AND_PERSIST_ENCODED_PAYLOAD_FAILURE,
            times = 3)
    public void testEncodeProtectedSignalsGracefullyHandleFailures_encodeTwoTimes()
            throws Exception {
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
        Void unused1 =
                mJobWorker
                        .encodeProtectedSignals(PAS_ENCODING_SOURCE_TYPE)
                        .get(5, TimeUnit.SECONDS);

        verify(mEncoderLogicHandler).getAllRegisteredEncoders();
        verify(mEncoderLogicHandler).getEncoder(BUYER);
        verify(mEncodedPayloadDao).getEncodedPayload(BUYER);
        verify(mEncodedPayloadDao).getEncodedPayload(BUYER_2);
        verify(mEncodedPayloadDao).persistEncodedPayload(mEncodedPayloadCaptor.capture());
        verify(mEncoderLogicHandler).updateEncoderFailedCount(BUYER_2, 1);

        DBEncodedPayload dbEncodedPayload = mEncodedPayloadCaptor.getValue();
        expect.withMessage("DBEncodedPayload")
                .that(dbEncodedPayload)
                .isNotNull();
        expect.withMessage("DBEncodedPayload.getBuyer()")
                .that(dbEncodedPayload.getBuyer())
                .isEqualTo(BUYER);
        expect.withMessage("DBEncodedPayload.getVersion()")
                .that(dbEncodedPayload.getVersion())
                .isEqualTo(VERSION_1);
        expect.withMessage("DBEncodedPayload.getEncodedPayload()")
                .that(getSetFromBytes(dbEncodedPayload.getEncodedPayload()))
                .isEqualTo(getSetFromBytes(validResponse));

        // Reset buyer's settings
        // Buyer 1 encoding would fail
        String encoderLogic3 = "function buyer1_EncodeJs() {\" throws exception \"}";
        when(mEncoderLogicHandler.getEncoder(BUYER)).thenReturn(encoderLogic3);
        when(mProtectedSignalsDao.getSignalsByBuyer(BUYER)).thenReturn(FAKE_SIGNALS);
        when(mScriptEngine.encodeSignals(eq(encoderLogic3), any(), anyInt(), any(), any()))
                .thenReturn(failureResponse);

        // Buyer 2 encoding would fail
        String encoderLogic4 = "function buyer2_EncodeJs() {\" throws exception \"}";
        when(mEncoderLogicHandler.getEncoder(BUYER_2)).thenReturn(encoderLogic4);
        when(mProtectedSignalsDao.getSignalsByBuyer(BUYER_2)).thenReturn(FAKE_SIGNALS);
        when(mScriptEngine.encodeSignals(eq(encoderLogic4), any(), anyInt(), any(), any()))
                .thenReturn(failureResponse);
        when(mEncoderLogicHandler.getAllRegisteredEncoders())
                .thenReturn(List.of(DB_ENCODER_LOGIC_BUYER_1, DB_ENCODER_LOGIC_BUYER_2));

        // This should gracefully handle Buyer_1 and Buyer_2 failure encoding again.
        Void unused2 =
                mJobWorker
                        .encodeProtectedSignals(
                                AdsRelevanceStatusUtils.PAS_ENCODING_SOURCE_TYPE_SERVICE_IMPL)
                        .get(5, TimeUnit.SECONDS);

        // Verify EncodingJobRunStats is logged 2 times.
        // In the first time, the log has 1 SignalEncodingSuccesses and 1 SignalEncodingFailures.
        // In the second time, the log has 2 SignalEncodingFailures.
        verify(mAdServicesLogger, times(2))
                .logEncodingJobRunStats(mEncodingJobRunStatsArgumentCaptor.capture());

        List<EncodingJobRunStats> stats = mEncodingJobRunStatsArgumentCaptor.getAllValues();
        expect.withMessage("List of EncodingJobRunStats captured")
                .that(stats)
                .hasSize(2);

        EncodingJobRunStats stats1 = stats.get(0);
        expect.withMessage("EncodingJobRunStats1")
                .that(stats1)
                .isNotNull();
        expect.withMessage("EncodingJobRunStats1.getSignalEncodingSuccesses()")
                .that(stats1.getSignalEncodingSuccesses())
                .isEqualTo(1);
        expect.withMessage("EncodingJobRunStats1.getSignalEncodingFailures()")
                .that(stats1.getSignalEncodingFailures())
                .isEqualTo(1);
        expect.withMessage("EncodingJobRunStats1.getEncodingSourceType()")
                .that(stats1.getEncodingSourceType())
                .isEqualTo(AdsRelevanceStatusUtils.PAS_ENCODING_SOURCE_TYPE_ENCODING_JOB_SERVICE);

        EncodingJobRunStats stats2 = stats.get(1);
        expect.withMessage("EncodingJobRunStats2")
                .that(stats2)
                .isNotNull();
        expect.withMessage("EncodingJobRunStats2.getSignalEncodingSuccesses()")
                .that(stats2.getSignalEncodingSuccesses())
                .isEqualTo(0);
        expect.withMessage("EncodingJobRunStats2.getSignalEncodingFailures()")
                .that(stats2.getSignalEncodingFailures())
                .isEqualTo(2);
        expect.withMessage("EncodingJobRunStats2.getEncodingSourceType()")
                .that(stats2.getEncodingSourceType())
                .isEqualTo(AdsRelevanceStatusUtils.PAS_ENCODING_SOURCE_TYPE_SERVICE_IMPL);
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

        Void unused =
                mJobWorker
                        .encodeProtectedSignals(PAS_ENCODING_SOURCE_TYPE)
                        .get(5, TimeUnit.SECONDS);
        verify(mEncoderLogicHandler).downloadAndUpdate(eq(BUYER), any());
        verify(mEncoderLogicHandler).downloadAndUpdate(eq(BUYER_2), any());

        verifyEncodingJobRunStatsLogging(
                /* countOfSignalEncodingSuccesses */ 0,
                /* countOfSignalEncodingFailures */ 0,
                /* countOfSignalEncodingSkips */ 0,
                AdsRelevanceStatusUtils.PAS_ENCODING_SOURCE_TYPE_ENCODING_JOB_SERVICE);
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

        Void unused =
                mJobWorker
                        .encodeProtectedSignals(PAS_ENCODING_SOURCE_TYPE)
                        .get(5, TimeUnit.SECONDS);
        verify(mEncoderLogicHandler).downloadAndUpdate(eq(BUYER), any());
        verify(mEncoderLogicHandler).downloadAndUpdate(eq(BUYER_2), any());

        verifyEncodingJobRunStatsLogging(
                /* countOfSignalEncodingSuccesses */ 0,
                /* countOfSignalEncodingFailures */ 0,
                /* countOfSignalEncodingSkips */ 0,
                AdsRelevanceStatusUtils.PAS_ENCODING_SOURCE_TYPE_ENCODING_JOB_SERVICE);
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
            int countOfSignalEncodingSkips,
            @AdsRelevanceStatusUtils.PasEncodingSourceType int pasEncodingSourceType) {
        verify(mAdServicesLogger)
                .logEncodingJobRunStats(mEncodingJobRunStatsArgumentCaptor.capture());

        EncodingJobRunStats stats = mEncodingJobRunStatsArgumentCaptor.getValue();
        expect.withMessage("EncodingJobRunStats")
                .that(stats)
                .isNotNull();
        expect.withMessage("EncodingJobRunStats.getSignalEncodingSuccesses()")
                .that(stats.getSignalEncodingSuccesses())
                .isEqualTo(countOfSignalEncodingSuccesses);
        expect.withMessage("EncodingJobRunStats.getSignalEncodingFailures()")
                .that(stats.getSignalEncodingFailures())
                .isEqualTo(countOfSignalEncodingFailures);
        expect.withMessage("EncodingJobRunStats.getSignalEncodingSkips()")
                .that(stats.getSignalEncodingSkips())
                .isEqualTo(countOfSignalEncodingSkips);
        expect.withMessage("EncodingJobRunStats.getEncodingSourceType()")
                .that(stats.getEncodingSourceType())
                .isEqualTo(pasEncodingSourceType);
    }
}

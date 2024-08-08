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

import static com.android.adservices.service.signals.PeriodicEncodingJobWorker.PAYLOAD_PERSISTENCE_ERROR_MSG;
import static com.android.adservices.service.stats.AdsRelevanceStatusUtils.JS_RUN_STATUS_OTHER_FAILURE;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertThrows;
import static org.junit.Assert.assertTrue;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyInt;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.doAnswer;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoMoreInteractions;
import static org.mockito.Mockito.verifyZeroInteractions;
import static org.mockito.Mockito.when;

import android.adservices.common.AdTechIdentifier;
import android.adservices.common.CommonFixture;

import com.android.adservices.concurrency.AdServicesExecutors;
import com.android.adservices.data.signals.DBEncodedPayload;
import com.android.adservices.data.signals.DBEncoderLogicMetadata;
import com.android.adservices.data.signals.DBSignalsUpdateMetadata;
import com.android.adservices.data.signals.EncodedPayloadDao;
import com.android.adservices.data.signals.EncoderLogicHandler;
import com.android.adservices.data.signals.ProtectedSignalsDao;
import com.android.adservices.service.Flags;
import com.android.adservices.service.stats.pas.EncodingExecutionLogHelper;
import com.android.adservices.service.stats.pas.EncodingJobRunStatsLogger;
import com.android.adservices.service.stats.pas.EncodingJobRunStatsLoggerNoLoggingImpl;
import com.android.adservices.shared.testing.SdkLevelSupportRule;

import com.google.common.collect.ImmutableList;
import com.google.common.collect.ImmutableMap;
import com.google.common.util.concurrent.Futures;
import com.google.common.util.concurrent.ListenableFuture;
import com.google.common.util.concurrent.ListeningExecutorService;

import org.junit.Before;
import org.junit.Rule;
import org.junit.Test;
import org.mockito.ArgumentCaptor;
import org.mockito.Captor;
import org.mockito.Mock;
import org.mockito.Mockito;
import org.mockito.junit.MockitoJUnit;
import org.mockito.junit.MockitoRule;

import java.time.Instant;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.ExecutionException;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.TimeoutException;

public class PeriodicEncodingJobRunnerTest {

    private static final AdTechIdentifier BUYER = CommonFixture.VALID_BUYER_1;
    private static final int VERSION_1 = 1;
    private static final DBEncoderLogicMetadata DB_ENCODER_LOGIC_BUYER_1 =
            DBEncoderLogicMetadata.builder()
                    .setBuyer(BUYER)
                    .setVersion(VERSION_1)
                    .setCreationTime(Instant.now())
                    .build();
    private static final Map<String, List<ProtectedSignal>> FAKE_SIGNALS =
            ImmutableMap.of(
                    "v1",
                    ImmutableList.of(
                            ProtectedSignal.builder()
                                    .setBase64EncodedValue("valid value")
                                    .setCreationTime(Instant.now())
                                    .setPackageName("package name")
                                    .build()));
    private static final int TIMEOUT_SECONDS = 5;

    @Rule public MockitoRule rule = MockitoJUnit.rule();

    @Mock private EncoderLogicHandler mEncoderLogicHandler;
    @Mock private EncodedPayloadDao mEncodedPayloadDao;
    @Mock private ProtectedSignalsDao mProtectedSignalsDao;
    @Mock private SignalsProviderImpl mSignalStorageManager;
    @Mock private SignalsScriptEngine mScriptEngine;
    @Mock private EncodingExecutionLogHelper mEncodingExecutionLoggerMock;
    @Mock private EncodingJobRunStatsLogger mEncodingJobRunStatsLoggerMock;

    @Captor private ArgumentCaptor<DBEncodedPayload> mEncodedPayloadCaptor;

    private ListeningExecutorService mBackgroundExecutor =
            AdServicesExecutors.getBackgroundExecutor();
    private ListeningExecutorService mLightWeightExecutor =
            AdServicesExecutors.getLightWeightExecutor();

    private PeriodicEncodingJobRunner mRunner;
    private static final int ENCODER_LOGIC_MAXIMUM_FAILURE = 3;
    private static final int ENCODED_PAY_LOAD_MAX_SIZE_BYTES = 100;
    private static final int MAX_SIZE_BYTES = 100;

    @Rule(order = 0)
    public final SdkLevelSupportRule sdkLevel = SdkLevelSupportRule.forAtLeastT();

    @Before
    public void setUp() {
        mRunner =
                new PeriodicEncodingJobRunner(
                        mSignalStorageManager,
                        mProtectedSignalsDao,
                        mScriptEngine,
                        ENCODER_LOGIC_MAXIMUM_FAILURE,
                        ENCODED_PAY_LOAD_MAX_SIZE_BYTES,
                        mEncoderLogicHandler,
                        mEncodedPayloadDao,
                        mBackgroundExecutor,
                        mLightWeightExecutor);
    }

    @Test
    public void testValidateAndPersistPayloadSuccess() {
        byte[] payload = new byte[] {0x0A, 0x01};
        int version = 1;
        mRunner.validateAndPersistPayload(DB_ENCODER_LOGIC_BUYER_1, payload, version);

        verify(mEncodedPayloadDao).persistEncodedPayload(mEncodedPayloadCaptor.capture());
        assertEquals(BUYER, mEncodedPayloadCaptor.getValue().getBuyer());
        assertEquals(version, mEncodedPayloadCaptor.getValue().getVersion());

        assertEquals(
                getSetFromBytes(payload),
                getSetFromBytes(mEncodedPayloadCaptor.getValue().getEncodedPayload()));
    }

    @Test
    public void testValidateAndPersistLargePayloadSkips() {
        int reallySmallMaxSizeLimit = 5;
        mRunner =
                new PeriodicEncodingJobRunner(
                        mSignalStorageManager,
                        mProtectedSignalsDao,
                        mScriptEngine,
                        ENCODER_LOGIC_MAXIMUM_FAILURE,
                        reallySmallMaxSizeLimit,
                        mEncoderLogicHandler,
                        mEncodedPayloadDao,
                        mBackgroundExecutor,
                        mLightWeightExecutor);
        int version = 1;
        assertThrows(
                IllegalArgumentException.class,
                () ->
                        mRunner.validateAndPersistPayload(
                                DB_ENCODER_LOGIC_BUYER_1,
                                new byte[] {0x01, 0x02, 0x03, 0x04, 0x05, 0x06},
                                version));

        Mockito.verifyZeroInteractions(mEncodedPayloadDao);
    }

    @Test
    public void testEncodingPerBuyerSuccess()
            throws ExecutionException, InterruptedException, TimeoutException {
        String encoderLogic = "function fakeEncodeJs() {}";

        when(mEncoderLogicHandler.getEncoder(BUYER)).thenReturn(encoderLogic);
        when(mSignalStorageManager.getSignals(BUYER)).thenReturn(FAKE_SIGNALS);

        byte[] validResponse = new byte[] {0x01, 0x02, 0x03, 0x04, 0x05, 0x0A};
        ListenableFuture<byte[]> jsScriptResponse = Futures.immediateFuture(validResponse);
        when(mScriptEngine.encodeSignals(any(), any(), anyInt(), any()))
                .thenReturn(jsScriptResponse);
        when((mEncodedPayloadDao.persistEncodedPayload(any()))).thenReturn(10L);

        // Run encoding for the buyer
        mRunner.runEncodingPerBuyer(
                        DB_ENCODER_LOGIC_BUYER_1,
                        TIMEOUT_SECONDS,
                        mEncodingExecutionLoggerMock,
                        new EncodingJobRunStatsLoggerNoLoggingImpl())
                .get(5, TimeUnit.SECONDS);

        verify(mScriptEngine)
                .encodeSignals(
                        encoderLogic, FAKE_SIGNALS, MAX_SIZE_BYTES, mEncodingExecutionLoggerMock);
        verify(mEncodedPayloadDao).persistEncodedPayload(mEncodedPayloadCaptor.capture());
        assertEquals(BUYER, mEncodedPayloadCaptor.getValue().getBuyer());
        assertEquals(VERSION_1, mEncodedPayloadCaptor.getValue().getVersion());
        assertEquals(
                getSetFromBytes(validResponse),
                getSetFromBytes(mEncodedPayloadCaptor.getValue().getEncodedPayload()));
        verify(mEncodingExecutionLoggerMock).finish();
    }

    @Test
    public void testEncodingPerBuyerScriptFailureCausesIllegalStateException() {
        String encoderLogic = "function fakeEncodeJs() {}";

        when(mEncoderLogicHandler.getEncoder(BUYER)).thenReturn(encoderLogic);
        when(mSignalStorageManager.getSignals(BUYER)).thenReturn(FAKE_SIGNALS);

        when(mScriptEngine.encodeSignals(any(), any(), anyInt(), any()))
                .thenReturn(
                        Futures.immediateFailedFuture(
                                new IllegalStateException("Simulating illegal response from JS")));

        // Run encoding for the buyer where jsEngine returns invalid payload
        Exception e =
                assertThrows(
                        ExecutionException.class,
                        () ->
                                mRunner.runEncodingPerBuyer(
                                                DB_ENCODER_LOGIC_BUYER_1,
                                                TIMEOUT_SECONDS,
                                                mEncodingExecutionLoggerMock,
                                                new EncodingJobRunStatsLoggerNoLoggingImpl())
                                        .get(5, TimeUnit.SECONDS));
        assertEquals(IllegalStateException.class, e.getCause().getClass());
        assertEquals(PAYLOAD_PERSISTENCE_ERROR_MSG, e.getCause().getMessage());
        verify(mEncodedPayloadDao).getEncodedPayload(BUYER);
        verifyNoMoreInteractions(mEncodedPayloadDao);
        verify(mEncodingExecutionLoggerMock).setStatus(eq(JS_RUN_STATUS_OTHER_FAILURE));
        verify(mEncodingExecutionLoggerMock).finish();
    }

    @Test
    public void testEncodingPerBuyerFailedFuture() {
        String encoderLogic = "function fakeEncodeJs() {}";
        when(mEncoderLogicHandler.getEncoder(BUYER)).thenReturn(encoderLogic);
        when(mSignalStorageManager.getSignals(BUYER)).thenReturn(FAKE_SIGNALS);

        when(mScriptEngine.encodeSignals(any(), any(), anyInt(), any()))
                .thenReturn(
                        Futures.immediateFailedFuture(new RuntimeException("Random exception")));

        // Run encoding for the buyer where jsEngine encounters Runtime Exception
        Exception e =
                assertThrows(
                        ExecutionException.class,
                        () -> {
                            mRunner.runEncodingPerBuyer(
                                            DB_ENCODER_LOGIC_BUYER_1,
                                            TIMEOUT_SECONDS,
                                            mEncodingExecutionLoggerMock,
                                            new EncodingJobRunStatsLoggerNoLoggingImpl())
                                    .get(5, TimeUnit.SECONDS);
                        });
        assertEquals(IllegalStateException.class, e.getCause().getClass());
        assertEquals(PAYLOAD_PERSISTENCE_ERROR_MSG, e.getCause().getMessage());
        verify(mEncodedPayloadDao).getEncodedPayload(BUYER);
        verifyNoMoreInteractions(mEncodedPayloadDao);
    }

    @Test
    public void testEncodingPerBuyerNoSignalAvailable()
            throws ExecutionException, InterruptedException, TimeoutException {
        when(mSignalStorageManager.getSignals(BUYER)).thenReturn(ImmutableMap.of());
        mRunner.runEncodingPerBuyer(
                        DB_ENCODER_LOGIC_BUYER_1,
                        TIMEOUT_SECONDS,
                        mEncodingExecutionLoggerMock,
                        new EncodingJobRunStatsLoggerNoLoggingImpl())
                .get(TIMEOUT_SECONDS, TimeUnit.SECONDS);
        verify(mEncoderLogicHandler).deleteEncoderForBuyer(BUYER);
        verifyNoMoreInteractions(mEncoderLogicHandler);
        verifyZeroInteractions(mScriptEngine);
    }

    @Test
    public void testEncodingPerBuyerFailedTimeout() throws InterruptedException {
        String encoderLogic = "function fakeEncodeJs() {}";
        when(mEncoderLogicHandler.getEncoder(BUYER)).thenReturn(encoderLogic);

        when(mSignalStorageManager.getSignals(BUYER)).thenReturn(FAKE_SIGNALS);

        CountDownLatch stallEncodingLatch = new CountDownLatch(1);
        CountDownLatch startEncodingLatch = new CountDownLatch(1);
        doAnswer(
                        invocation -> {
                            startEncodingLatch.countDown();
                            return mBackgroundExecutor.submit(
                                    () -> {
                                        // Await and stall encoding, indefinitely until timed out
                                        try {
                                            stallEncodingLatch.await();
                                        } catch (InterruptedException e) {
                                            // Cleanup stalled thread
                                            Thread.currentThread().interrupt();
                                        }
                                    });
                        })
                .when(mScriptEngine)
                .encodeSignals(any(), any(), anyInt(), any());

        // Run encoding for the buyer with a really short timeout
        int shortTimeoutSecond = 1;
        Exception e =
                assertThrows(
                        ExecutionException.class,
                        () -> {
                            mRunner.runEncodingPerBuyer(
                                            DB_ENCODER_LOGIC_BUYER_1,
                                            shortTimeoutSecond,
                                            mEncodingExecutionLoggerMock,
                                            new EncodingJobRunStatsLoggerNoLoggingImpl())
                                    .get(shortTimeoutSecond + 1, TimeUnit.SECONDS);
                        });

        // Encoding should have been started
        startEncodingLatch.await(5, TimeUnit.SECONDS);
        assertEquals(
                "Stalling latch should have never been counted down, but interrupted by timeout",
                1,
                stallEncodingLatch.getCount());
        // e is TimeoutFuture$TimeoutFutureException which extends TimeoutException
        assertTrue(TimeoutException.class.isAssignableFrom(e.getCause().getClass()));
        verify(mEncodedPayloadDao).getEncodedPayload(BUYER);
        verifyNoMoreInteractions(mEncodedPayloadDao);
    }

    @Test
    public void testEncodeSignals_tooManyFailure_noJsExecution()
            throws ExecutionException, InterruptedException, TimeoutException {
        when(mSignalStorageManager.getSignals(BUYER)).thenReturn(FAKE_SIGNALS);
        int maxFailure =
                Flags.PROTECTED_SIGNALS_MAX_JS_FAILURE_EXECUTION_ON_CERTAIN_VERSION_BEFORE_STOP;
        DBEncoderLogicMetadata metadata =
                DBEncoderLogicMetadata.builder()
                        .setBuyer(BUYER)
                        .setCreationTime(Instant.now())
                        .setVersion(1)
                        .setFailedEncodingCount(maxFailure)
                        .build();

        mRunner.runEncodingPerBuyer(
                        metadata,
                        5,
                        mEncodingExecutionLoggerMock,
                        new EncodingJobRunStatsLoggerNoLoggingImpl())
                .get(TIMEOUT_SECONDS, TimeUnit.SECONDS);

        verify(mSignalStorageManager).getSignals(BUYER);
        verifyNoMoreInteractions(mSignalStorageManager);
        verifyZeroInteractions(mScriptEngine);
    }

    @Test
    public void testEncodeSignals_previousFailureAndThenSuccess_resetFailedCount()
            throws ExecutionException, InterruptedException, TimeoutException {
        DBEncoderLogicMetadata metadata =
                DBEncoderLogicMetadata.builder()
                        .setBuyer(BUYER)
                        .setCreationTime(Instant.now())
                        .setVersion(1)
                        .setFailedEncodingCount(1)
                        .build();
        when(mSignalStorageManager.getSignals(BUYER)).thenReturn(FAKE_SIGNALS);

        String encoderLogic = "function buyer1_EncodeJs() {\" correct result \"}";
        when(mEncoderLogicHandler.getEncoder(BUYER)).thenReturn(encoderLogic);
        byte[] validResponse = new byte[] {0x01, 0x02, 0x03, 0x04, 0x05, 0x0A};
        ListenableFuture<byte[]> successResponse = Futures.immediateFuture(validResponse);
        when(mScriptEngine.encodeSignals(eq(encoderLogic), any(), anyInt(), any()))
                .thenReturn(successResponse);

        mRunner.runEncodingPerBuyer(
                        metadata,
                        5,
                        mEncodingExecutionLoggerMock,
                        new EncodingJobRunStatsLoggerNoLoggingImpl())
                .get(TIMEOUT_SECONDS, TimeUnit.SECONDS);

        verify(mEncoderLogicHandler).updateEncoderFailedCount(BUYER, 0);
    }

    @Test
    public void testEncodeSignals_noUpdateToBuyer_skipEncoding()
            throws ExecutionException, InterruptedException, TimeoutException {
        when(mSignalStorageManager.getSignals(BUYER)).thenReturn(FAKE_SIGNALS);
        when(mProtectedSignalsDao.getSignalsUpdateMetadata(BUYER))
                .thenReturn(
                        DBSignalsUpdateMetadata.builder()
                                .setBuyer(BUYER)
                                .setLastSignalsUpdatedTime(CommonFixture.FIXED_EARLIER_ONE_DAY)
                                .build());
        when(mEncodedPayloadDao.getEncodedPayload(BUYER))
                .thenReturn(
                        DBEncodedPayload.create(
                                BUYER, 1, CommonFixture.FIXED_NOW, new byte[] {0x22, 0x33}));

        mRunner.runEncodingPerBuyer(
                        DBEncoderLogicMetadata.builder()
                                .setBuyer(BUYER)
                                .setCreationTime(CommonFixture.FIXED_EARLIER_ONE_DAY)
                                .setVersion(1)
                                .setFailedEncodingCount(1)
                                .build(),
                        TIMEOUT_SECONDS,
                        mEncodingExecutionLoggerMock,
                        mEncodingJobRunStatsLoggerMock)
                .get(TIMEOUT_SECONDS, TimeUnit.SECONDS);

        verify(mSignalStorageManager).getSignals(BUYER);
        verify(mProtectedSignalsDao).getSignalsUpdateMetadata(BUYER);
        verify(mEncodedPayloadDao).getEncodedPayload(BUYER);
        verifyNoMoreInteractions(mSignalStorageManager, mProtectedSignalsDao, mEncodedPayloadDao);
        verifyZeroInteractions(mEncoderLogicHandler, mScriptEngine);

        verify(mEncodingJobRunStatsLoggerMock).addOneSignalEncodingSkips();
    }

    private Set<Byte> getSetFromBytes(byte[] bytes) {
        Set<Byte> byteSet = new HashSet<>();

        for (byte b : bytes) {
            byteSet.add(b);
        }
        return byteSet;
    }
}
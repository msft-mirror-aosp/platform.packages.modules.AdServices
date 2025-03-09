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
import static com.android.adservices.service.stats.AdServicesStatsLog.AD_SERVICES_ERROR_REPORTED__ERROR_CODE__PAS_ENCODED_PAYLOAD_SIZE_EXCEEDS_LIMITS;
import static com.android.adservices.service.stats.AdServicesStatsLog.AD_SERVICES_ERROR_REPORTED__ERROR_CODE__PAS_VALIDATE_AND_PERSIST_ENCODED_PAYLOAD_FAILURE;
import static com.android.adservices.service.stats.AdServicesStatsLog.AD_SERVICES_ERROR_REPORTED__PPAPI_NAME__PAS;
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

import com.android.adservices.common.AdServicesExtendedMockitoTestCase;
import com.android.adservices.common.logging.annotations.ExpectErrorLogUtilCall;
import com.android.adservices.common.logging.annotations.ExpectErrorLogUtilWithExceptionCall;
import com.android.adservices.common.logging.annotations.SetErrorLogUtilDefaultParams;
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
import com.android.adservices.shared.testing.annotations.RequiresSdkLevelAtLeastT;

import com.google.common.collect.ImmutableList;
import com.google.common.collect.ImmutableMap;
import com.google.common.util.concurrent.Futures;
import com.google.common.util.concurrent.ListenableFuture;
import com.google.common.util.concurrent.ListeningExecutorService;

import org.junit.Before;
import org.junit.Test;
import org.mockito.ArgumentCaptor;
import org.mockito.Captor;
import org.mockito.Mock;
import org.mockito.Mockito;

import java.time.Instant;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.ExecutionException;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.TimeoutException;

@SetErrorLogUtilDefaultParams(
        throwable = ExpectErrorLogUtilWithExceptionCall.Any.class,
        ppapiName = AD_SERVICES_ERROR_REPORTED__PPAPI_NAME__PAS)
@RequiresSdkLevelAtLeastT(reason = "PAS is only supported on T+")
public class PeriodicEncodingJobRunnerTest extends AdServicesExtendedMockitoTestCase {

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
                                    .setHexEncodedValue("valid value")
                                    .setCreationTime(Instant.now())
                                    .setPackageName("package name")
                                    .build()));
    private static final int TIMEOUT_SECONDS = 5;

    @Mock private EncoderLogicHandler mMockEncoderLogicHandler;
    @Mock private EncodedPayloadDao mMockEncodedPayloadDao;
    @Mock private ProtectedSignalsDao mMockProtectedSignalsDao;
    @Mock private SignalsProviderAndArgumentFactory mMockSignalsProviderAndArgumentFactory;
    @Mock private SignalsProviderImpl mMockSignalStorageManager;
    @Mock private SignalsScriptEngine mMockScriptEngine;
    @Mock private EncodingExecutionLogHelper mMockEncodingExecutionLogger;
    @Mock private EncodingJobRunStatsLogger mMockEncodingJobRunStatsLogger;
    @Mock private ProtectedSignalsArgument mMockProtectedSignalsArgument;

    @Captor private ArgumentCaptor<DBEncodedPayload> mEncodedPayloadCaptor;

    private ListeningExecutorService mBackgroundExecutor =
            AdServicesExecutors.getBackgroundExecutor();
    private ListeningExecutorService mLightWeightExecutor =
            AdServicesExecutors.getLightWeightExecutor();

    private PeriodicEncodingJobRunner mRunner;
    private static final int ENCODER_LOGIC_MAXIMUM_FAILURE = 3;
    private static final int ENCODED_PAY_LOAD_MAX_SIZE_BYTES = 100;
    private static final int MAX_SIZE_BYTES = 100;

    @Before
    public void setUp() {
        when(mMockSignalsProviderAndArgumentFactory.getSignalsProvider())
                .thenReturn(mMockSignalStorageManager);
        when(mMockSignalsProviderAndArgumentFactory.getProtectedSignalsArgument())
                .thenReturn(mMockProtectedSignalsArgument);
        mRunner =
                new PeriodicEncodingJobRunner(
                        mMockSignalsProviderAndArgumentFactory,
                        mMockProtectedSignalsDao,
                        mMockScriptEngine,
                        ENCODER_LOGIC_MAXIMUM_FAILURE,
                        ENCODED_PAY_LOAD_MAX_SIZE_BYTES,
                        mMockEncoderLogicHandler,
                        mMockEncodedPayloadDao,
                        mBackgroundExecutor,
                        mLightWeightExecutor);
    }

    @Test
    public void testValidateAndPersistPayloadSuccess() {
        byte[] payload = new byte[] {0x0A, 0x01};
        int version = 1;
        mRunner.validateAndPersistPayload(DB_ENCODER_LOGIC_BUYER_1, payload, version);

        verify(mMockEncodedPayloadDao).persistEncodedPayload(mEncodedPayloadCaptor.capture());
        assertEquals(BUYER, mEncodedPayloadCaptor.getValue().getBuyer());
        assertEquals(version, mEncodedPayloadCaptor.getValue().getVersion());

        assertEquals(
                getSetFromBytes(payload),
                getSetFromBytes(mEncodedPayloadCaptor.getValue().getEncodedPayload()));
    }

    @Test
    @ExpectErrorLogUtilCall(
            errorCode = AD_SERVICES_ERROR_REPORTED__ERROR_CODE__PAS_ENCODED_PAYLOAD_SIZE_EXCEEDS_LIMITS)
    public void testValidateAndPersistLargePayloadSkips() {
        int reallySmallMaxSizeLimit = 5;
        mRunner =
                new PeriodicEncodingJobRunner(
                        mMockSignalsProviderAndArgumentFactory,
                        mMockProtectedSignalsDao,
                        mMockScriptEngine,
                        ENCODER_LOGIC_MAXIMUM_FAILURE,
                        reallySmallMaxSizeLimit,
                        mMockEncoderLogicHandler,
                        mMockEncodedPayloadDao,
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

        Mockito.verifyZeroInteractions(mMockEncodedPayloadDao);
    }

    @Test
    public void testEncodingPerBuyerSuccess()
            throws ExecutionException, InterruptedException, TimeoutException {
        String encoderLogic = "function fakeEncodeJs() {}";

        when(mMockEncoderLogicHandler.getEncoder(BUYER)).thenReturn(encoderLogic);
        when(mMockSignalStorageManager.getSignals(BUYER)).thenReturn(FAKE_SIGNALS);

        byte[] validResponse = new byte[] {0x01, 0x02, 0x03, 0x04, 0x05, 0x0A};
        ListenableFuture<byte[]> jsScriptResponse = Futures.immediateFuture(validResponse);
        when(mMockScriptEngine.encodeSignals(
                        any(), any(), anyInt(), any(), eq(mMockProtectedSignalsArgument)))
                .thenReturn(jsScriptResponse);
        when(mMockEncodedPayloadDao.persistEncodedPayload(any())).thenReturn(10L);

        // Run encoding for the buyer
        mRunner.runEncodingPerBuyer(
                        DB_ENCODER_LOGIC_BUYER_1,
                        TIMEOUT_SECONDS,
                        mMockEncodingExecutionLogger,
                        new EncodingJobRunStatsLoggerNoLoggingImpl())
                .get(5, TimeUnit.SECONDS);

        verify(mMockScriptEngine)
                .encodeSignals(
                        encoderLogic,
                        FAKE_SIGNALS,
                        MAX_SIZE_BYTES,
                        mMockEncodingExecutionLogger,
                        mMockProtectedSignalsArgument);
        verify(mMockScriptEngine)
                .encodeSignals(
                        encoderLogic,
                        FAKE_SIGNALS,
                        ENCODED_PAY_LOAD_MAX_SIZE_BYTES,
                        mMockEncodingExecutionLogger,
                        mMockProtectedSignalsArgument);
        verify(mMockEncodedPayloadDao).persistEncodedPayload(mEncodedPayloadCaptor.capture());
        assertEquals(BUYER, mEncodedPayloadCaptor.getValue().getBuyer());
        assertEquals(VERSION_1, mEncodedPayloadCaptor.getValue().getVersion());
        assertEquals(
                getSetFromBytes(validResponse),
                getSetFromBytes(mEncodedPayloadCaptor.getValue().getEncodedPayload()));
        verify(mMockEncodingExecutionLogger).finish();
    }

    @Test
    @ExpectErrorLogUtilWithExceptionCall(
            errorCode = AD_SERVICES_ERROR_REPORTED__ERROR_CODE__PAS_VALIDATE_AND_PERSIST_ENCODED_PAYLOAD_FAILURE)
    public void testEncodingPerBuyerScriptFailureCausesIllegalStateException() {
        String encoderLogic = "function fakeEncodeJs() {}";

        when(mMockEncoderLogicHandler.getEncoder(BUYER)).thenReturn(encoderLogic);
        when(mMockSignalStorageManager.getSignals(BUYER)).thenReturn(FAKE_SIGNALS);

        when(mMockScriptEngine.encodeSignals(
                        any(), any(), anyInt(), any(), eq(mMockProtectedSignalsArgument)))
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
                                                mMockEncodingExecutionLogger,
                                                new EncodingJobRunStatsLoggerNoLoggingImpl())
                                        .get(5, TimeUnit.SECONDS));
        assertEquals(IllegalStateException.class, e.getCause().getClass());
        assertEquals(PAYLOAD_PERSISTENCE_ERROR_MSG, e.getCause().getMessage());
        verify(mMockEncodedPayloadDao).getEncodedPayload(BUYER);
        verifyNoMoreInteractions(mMockEncodedPayloadDao);
        verify(mMockEncodingExecutionLogger).setStatus(eq(JS_RUN_STATUS_OTHER_FAILURE));
        verify(mMockEncodingExecutionLogger).finish();
    }

    @Test
    @ExpectErrorLogUtilWithExceptionCall(
            errorCode = AD_SERVICES_ERROR_REPORTED__ERROR_CODE__PAS_VALIDATE_AND_PERSIST_ENCODED_PAYLOAD_FAILURE)
    public void testEncodingPerBuyerFailedFuture() {
        String encoderLogic = "function fakeEncodeJs() {}";

        when(mMockEncoderLogicHandler.getEncoder(BUYER)).thenReturn(encoderLogic);
        when(mMockSignalStorageManager.getSignals(BUYER)).thenReturn(FAKE_SIGNALS);

        when(mMockScriptEngine.encodeSignals(
                        any(), any(), anyInt(), any(), eq(mMockProtectedSignalsArgument)))
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
                                            mMockEncodingExecutionLogger,
                                            new EncodingJobRunStatsLoggerNoLoggingImpl())
                                    .get(5, TimeUnit.SECONDS);
                        });
        assertEquals(IllegalStateException.class, e.getCause().getClass());
        assertEquals(PAYLOAD_PERSISTENCE_ERROR_MSG, e.getCause().getMessage());
        verify(mMockEncodedPayloadDao).getEncodedPayload(BUYER);
        verifyNoMoreInteractions(mMockEncodedPayloadDao);
    }

    @Test
    public void testEncodingPerBuyerNoSignalAvailable()
            throws ExecutionException, InterruptedException, TimeoutException {
        when(mMockSignalStorageManager.getSignals(BUYER)).thenReturn(ImmutableMap.of());
        mRunner.runEncodingPerBuyer(
                        DB_ENCODER_LOGIC_BUYER_1,
                        TIMEOUT_SECONDS,
                        mMockEncodingExecutionLogger,
                        new EncodingJobRunStatsLoggerNoLoggingImpl())
                .get(TIMEOUT_SECONDS, TimeUnit.SECONDS);
        verify(mMockEncoderLogicHandler).deleteEncoderForBuyer(BUYER);
        verifyNoMoreInteractions(mMockEncoderLogicHandler);
        verifyZeroInteractions(mMockScriptEngine);
    }

    @Test
    public void testEncodingPerBuyerFailedTimeout() throws InterruptedException {
        String encoderLogic = "function fakeEncodeJs() {}";

        when(mMockEncoderLogicHandler.getEncoder(BUYER)).thenReturn(encoderLogic);
        when(mMockSignalStorageManager.getSignals(BUYER)).thenReturn(FAKE_SIGNALS);

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
                .when(mMockScriptEngine)
                .encodeSignals(any(), any(), anyInt(), any(), eq(mMockProtectedSignalsArgument));

        // Run encoding for the buyer with a really short timeout
        int shortTimeoutSecond = 1;
        Exception e =
                assertThrows(
                        ExecutionException.class,
                        () -> {
                            mRunner.runEncodingPerBuyer(
                                            DB_ENCODER_LOGIC_BUYER_1,
                                            shortTimeoutSecond,
                                            mMockEncodingExecutionLogger,
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
        verify(mMockEncodedPayloadDao).getEncodedPayload(BUYER);
        verifyNoMoreInteractions(mMockEncodedPayloadDao);
    }

    @Test
    public void testEncodeSignals_tooManyFailure_noJsExecution()
            throws ExecutionException, InterruptedException, TimeoutException {
        when(mMockSignalStorageManager.getSignals(BUYER)).thenReturn(FAKE_SIGNALS);
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
                        mMockEncodingExecutionLogger,
                        new EncodingJobRunStatsLoggerNoLoggingImpl())
                .get(TIMEOUT_SECONDS, TimeUnit.SECONDS);

        verify(mMockSignalStorageManager).getSignals(BUYER);
        verifyNoMoreInteractions(mMockSignalStorageManager);
        verifyZeroInteractions(mMockScriptEngine);
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
        when(mMockSignalStorageManager.getSignals(BUYER)).thenReturn(FAKE_SIGNALS);

        String encoderLogic = "function buyer1_EncodeJs() {\" correct result \"}";
        when(mMockEncoderLogicHandler.getEncoder(BUYER)).thenReturn(encoderLogic);
        byte[] validResponse = new byte[] {0x01, 0x02, 0x03, 0x04, 0x05, 0x0A};
        ListenableFuture<byte[]> successResponse = Futures.immediateFuture(validResponse);
        when(mMockScriptEngine.encodeSignals(
                        eq(encoderLogic),
                        any(),
                        anyInt(),
                        any(),
                        eq(mMockProtectedSignalsArgument)))
                .thenReturn(successResponse);

        mRunner.runEncodingPerBuyer(
                        metadata,
                        5,
                        mMockEncodingExecutionLogger,
                        new EncodingJobRunStatsLoggerNoLoggingImpl())
                .get(TIMEOUT_SECONDS, TimeUnit.SECONDS);

        verify(mMockEncoderLogicHandler).updateEncoderFailedCount(BUYER, 0);
    }

    @Test
    public void testEncodeSignals_noUpdateToBuyer_skipEncoding()
            throws ExecutionException, InterruptedException, TimeoutException {
        when(mMockSignalStorageManager.getSignals(BUYER)).thenReturn(FAKE_SIGNALS);
        when(mMockProtectedSignalsDao.getSignalsUpdateMetadata(BUYER))
                .thenReturn(
                        DBSignalsUpdateMetadata.builder()
                                .setBuyer(BUYER)
                                .setLastSignalsUpdatedTime(CommonFixture.FIXED_EARLIER_ONE_DAY)
                                .build());
        when(mMockEncodedPayloadDao.getEncodedPayload(BUYER))
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
                        mMockEncodingExecutionLogger,
                        mMockEncodingJobRunStatsLogger)
                .get(TIMEOUT_SECONDS, TimeUnit.SECONDS);

        verify(mMockSignalStorageManager).getSignals(BUYER);
        verify(mMockProtectedSignalsDao).getSignalsUpdateMetadata(BUYER);
        verify(mMockEncodedPayloadDao).getEncodedPayload(BUYER);
        verifyNoMoreInteractions(
                mMockSignalStorageManager, mMockProtectedSignalsDao, mMockEncodedPayloadDao);
        verifyZeroInteractions(mMockEncoderLogicHandler, mMockScriptEngine);

        verify(mMockEncodingJobRunStatsLogger).addOneSignalEncodingSkips();
    }

    private Set<Byte> getSetFromBytes(byte[] bytes) {
        Set<Byte> byteSet = new HashSet<>();

        for (byte b : bytes) {
            byteSet.add(b);
        }
        return byteSet;
    }
}

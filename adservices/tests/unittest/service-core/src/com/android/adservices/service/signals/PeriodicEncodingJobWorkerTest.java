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

import static com.android.adservices.service.signals.PeriodicEncodingJobWorker.PAYLOAD_PERSISTENCE_ERROR_MSG;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertThrows;
import static org.junit.Assert.assertTrue;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyInt;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.doAnswer;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyZeroInteractions;
import static org.mockito.Mockito.when;

import android.adservices.common.AdTechIdentifier;
import android.adservices.common.CommonFixture;

import com.android.adservices.concurrency.AdServicesExecutors;
import com.android.adservices.data.signals.DBEncodedPayload;
import com.android.adservices.data.signals.DBEncoderLogic;
import com.android.adservices.data.signals.EncodedPayloadDao;
import com.android.adservices.data.signals.EncoderLogicDao;
import com.android.adservices.data.signals.EncoderLogicHandler;
import com.android.adservices.data.signals.EncoderPersistenceDao;
import com.android.adservices.service.Flags;
import com.android.adservices.service.adselection.AdSelectionScriptEngine;
import com.android.adservices.service.devapi.DevContextFilter;

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
import org.mockito.Mockito;
import org.mockito.junit.MockitoJUnit;
import org.mockito.junit.MockitoRule;

import java.time.Instant;
import java.util.Base64;
import java.util.Collections;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.ExecutionException;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.TimeoutException;

public class PeriodicEncodingJobWorkerTest {

    private static final AdTechIdentifier BUYER = CommonFixture.VALID_BUYER_1;
    private static final AdTechIdentifier BUYER_2 = CommonFixture.VALID_BUYER_2;

    private static final int TIMEOUT_SECONDS = 5;
    private static final int MAX_SIZE_BYTES = 100;

    @Rule public MockitoRule rule = MockitoJUnit.rule();

    @Mock private EncoderLogicHandler mEncoderLogicHandler;
    @Mock private EncoderLogicDao mEncoderLogicMetadataDao;
    @Mock private EncoderPersistenceDao mEncoderPersistenceDao;
    @Mock private EncodedPayloadDao mEncodedPayloadDao;
    @Mock private SignalsProviderImpl mSignalStorageManager;
    @Mock private AdSelectionScriptEngine mScriptEngine;
    @Mock private DevContextFilter mDevContextFilter;
    @Mock Flags mFlags;

    @Captor private ArgumentCaptor<DBEncodedPayload> mEncodedPayloadCaptor;

    private ListeningExecutorService mBackgroundExecutor =
            AdServicesExecutors.getBackgroundExecutor();
    private ListeningExecutorService mLightWeightExecutor =
            AdServicesExecutors.getLightWeightExecutor();

    private PeriodicEncodingJobWorker mJobWorker;

    @Before
    public void setup() {
        when(mFlags.getProtectedSignalsEncodedPayloadMaxSizeBytes()).thenReturn(MAX_SIZE_BYTES);
        mJobWorker =
                new PeriodicEncodingJobWorker(
                        mEncoderLogicHandler,
                        mEncoderLogicMetadataDao,
                        mEncoderPersistenceDao,
                        mEncodedPayloadDao,
                        mSignalStorageManager,
                        mScriptEngine,
                        mBackgroundExecutor,
                        mLightWeightExecutor,
                        mDevContextFilter,
                        mFlags);
    }

    @Test
    public void testValidateAndPersistPayloadSuccess() {
        byte[] payload = new byte[] {0x0A, 0x01};
        int version = 1;
        assertTrue(mJobWorker.validateAndPersistPayload(BUYER, payload, version));

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
        when(mFlags.getProtectedSignalsEncodedPayloadMaxSizeBytes())
                .thenReturn(reallySmallMaxSizeLimit);
        mJobWorker =
                new PeriodicEncodingJobWorker(
                        mEncoderLogicHandler,
                        mEncoderLogicMetadataDao,
                        mEncoderPersistenceDao,
                        mEncodedPayloadDao,
                        mSignalStorageManager,
                        mScriptEngine,
                        mBackgroundExecutor,
                        mLightWeightExecutor,
                        mDevContextFilter,
                        mFlags);
        byte[] encodedPayload =  new byte[] {0x01, 0x02, 0x03, 0x04, 0x05, 0x06};
        int version = 1;
        assertFalse(mJobWorker.validateAndPersistPayload(BUYER, encodedPayload, version));

        verifyZeroInteractions(mEncodedPayloadDao);
    }

    @Test
    public void testEncodingPerBuyerSuccess()
            throws ExecutionException, InterruptedException, TimeoutException {
        String encoderLogic = "function fakeEncodeJs() {}";
        int version = 2;
        DBEncoderLogic fakeEncoderLogicEntry =
                DBEncoderLogic.builder()
                        .setBuyer(BUYER)
                        .setVersion(version)
                        .setCreationTime(Instant.now())
                        .build();
        Map<String, List<ProtectedSignal>> fakeSignals = new HashMap<>();

        when(mEncoderLogicMetadataDao.getEncoder(BUYER)).thenReturn(fakeEncoderLogicEntry);
        when(mEncoderPersistenceDao.getEncoder(BUYER)).thenReturn(encoderLogic);
        when(mSignalStorageManager.getSignals(BUYER)).thenReturn(fakeSignals);

        byte[] validResponse = new byte[] {0x01, 0x02, 0x03, 0x04, 0x05, 0x0A};
        ListenableFuture<byte[]> jsScriptResponse = Futures.immediateFuture(validResponse);
        when(mScriptEngine.encodeSignals(any(), any(), anyInt())).thenReturn(jsScriptResponse);
        when((mEncodedPayloadDao.persistEncodedPayload(any()))).thenReturn(10L);

        // Run encoding for the buyer
        assertTrue(mJobWorker.runEncodingPerBuyer(BUYER, TIMEOUT_SECONDS).get(5, TimeUnit.SECONDS));

        verify(mScriptEngine).encodeSignals(encoderLogic, fakeSignals, MAX_SIZE_BYTES);
        verify(mEncodedPayloadDao).persistEncodedPayload(mEncodedPayloadCaptor.capture());
        assertEquals(BUYER, mEncodedPayloadCaptor.getValue().getBuyer());
        assertEquals(version, mEncodedPayloadCaptor.getValue().getVersion());
        assertEquals(
                getSetFromBytes(validResponse),
                getSetFromBytes(mEncodedPayloadCaptor.getValue().getEncodedPayload()));
    }

    @Test
    public void testEncodingPerBuyerScriptFailureCausesIllegalStateException()
            throws ExecutionException, InterruptedException, TimeoutException {
        String encoderLogic = "function fakeEncodeJs() {}";
        int version = 2;
        DBEncoderLogic fakeEncoderLogicEntry =
                DBEncoderLogic.builder()
                        .setBuyer(BUYER)
                        .setVersion(version)
                        .setCreationTime(Instant.now())
                        .build();
        when(mEncoderLogicMetadataDao.getEncoder(BUYER)).thenReturn(fakeEncoderLogicEntry);
        when(mEncoderPersistenceDao.getEncoder(BUYER)).thenReturn(encoderLogic);

        Map<String, List<ProtectedSignal>> fakeSignals = new HashMap<>();
        when(mSignalStorageManager.getSignals(BUYER)).thenReturn(fakeSignals);

        when(mScriptEngine.encodeSignals(any(), any(), anyInt()))
                .thenReturn(
                        Futures.immediateFailedFuture(
                                new IllegalStateException("Simulating illegal response from JS")));

        // Run encoding for the buyer where jsEngine returns invalid payload
        Exception e =
                assertThrows(
                        ExecutionException.class,
                        () ->
                                mJobWorker
                                        .runEncodingPerBuyer(
                                                BUYER, TIMEOUT_SECONDS)
                                        .get(5, TimeUnit.SECONDS));
        assertEquals(IllegalStateException.class, e.getCause().getClass());
        assertEquals(PAYLOAD_PERSISTENCE_ERROR_MSG, e.getCause().getMessage());
        Mockito.verifyZeroInteractions(mEncodedPayloadDao);
    }

    @Test
    public void testEncodingPerBuyerFailedFuture() {
        String encoderLogic = "function fakeEncodeJs() {}";
        int version = 2;
        DBEncoderLogic fakeEncoderLogicEntry =
                DBEncoderLogic.builder()
                        .setBuyer(BUYER)
                        .setVersion(version)
                        .setCreationTime(Instant.now())
                        .build();
        when(mEncoderLogicMetadataDao.getEncoder(BUYER)).thenReturn(fakeEncoderLogicEntry);
        when(mEncoderPersistenceDao.getEncoder(BUYER)).thenReturn(encoderLogic);

        Map<String, List<ProtectedSignal>> fakeSignals = new HashMap<>();
        when(mSignalStorageManager.getSignals(BUYER)).thenReturn(fakeSignals);

        ListenableFuture<byte[]> jsScriptResponse =
                Futures.immediateFailedFuture(new RuntimeException("Random exception"));
        when(mScriptEngine.encodeSignals(any(), any(), anyInt())).thenReturn(jsScriptResponse);

        // Run encoding for the buyer where jsEngine encounters Runtime Exception
        Exception e =
                assertThrows(
                        ExecutionException.class,
                        () -> {
                            mJobWorker
                                    .runEncodingPerBuyer(BUYER, TIMEOUT_SECONDS)
                                    .get(5, TimeUnit.SECONDS);
                        });
        assertEquals(IllegalStateException.class, e.getCause().getClass());
        assertEquals(PAYLOAD_PERSISTENCE_ERROR_MSG, e.getCause().getMessage());
        verifyZeroInteractions(mEncodedPayloadDao);
    }

    @Test
    public void testEncodingPerBuyerFailedTimeout() throws InterruptedException {
        String encoderLogic = "function fakeEncodeJs() {}";
        int version = 1;
        DBEncoderLogic fakeEncoderLogicEntry =
                DBEncoderLogic.builder()
                        .setBuyer(BUYER)
                        .setVersion(version)
                        .setCreationTime(Instant.now())
                        .build();
        when(mEncoderLogicMetadataDao.getEncoder(BUYER)).thenReturn(fakeEncoderLogicEntry);
        when(mEncoderPersistenceDao.getEncoder(BUYER)).thenReturn(encoderLogic);

        Map<String, List<ProtectedSignal>> fakeSignals = new HashMap<>();
        when(mSignalStorageManager.getSignals(BUYER)).thenReturn(fakeSignals);

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
                .encodeSignals(any(), any(), anyInt());

        // Run encoding for the buyer with a really short timeout
        int shortTimeoutSecond = 1;
        Exception e =
                assertThrows(
                        ExecutionException.class,
                        () -> {
                            mJobWorker
                                    .runEncodingPerBuyer(BUYER, shortTimeoutSecond)
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
        verifyZeroInteractions(mEncodedPayloadDao);
    }

    @Test
    public void testEncodeProtectedSignalsGracefullyHandleFailures()
            throws ExecutionException, InterruptedException, TimeoutException {

        // Buyer 1 encoding would succeed
        String encoderLogic1 = "function buyer1_EncodeJs() {\" correct result \"}";
        int version1 = 1;
        DBEncoderLogic fakeEncoderLogicEntry =
                DBEncoderLogic.builder()
                        .setBuyer(BUYER)
                        .setVersion(version1)
                        .setCreationTime(Instant.now())
                        .build();
        Map<String, List<ProtectedSignal>> fakeSignals = new HashMap<>();
        when(mEncoderLogicMetadataDao.getEncoder(BUYER)).thenReturn(fakeEncoderLogicEntry);
        when(mEncoderPersistenceDao.getEncoder(BUYER)).thenReturn(encoderLogic1);
        when(mSignalStorageManager.getSignals(BUYER)).thenReturn(fakeSignals);
        byte[] validResponse = new byte[] {0x01, 0x02, 0x03, 0x04, 0x05, 0x0A};
        ListenableFuture<byte[]> successResponse = Futures.immediateFuture(validResponse);
        when(mScriptEngine.encodeSignals(eq(encoderLogic1), any(), anyInt()))
                .thenReturn(successResponse);

        // Buyer 2 encoding would fail
        String encoderLogic2 = "function buyer2_EncodeJs() {\" throws exception \"}";
        int version2 = 2;
        DBEncoderLogic fakeEncoderLogicEntry2 =
                DBEncoderLogic.builder()
                        .setBuyer(BUYER_2)
                        .setVersion(version2)
                        .setCreationTime(Instant.now())
                        .build();
        when(mEncoderLogicMetadataDao.getEncoder(BUYER_2)).thenReturn(fakeEncoderLogicEntry2);
        when(mEncoderPersistenceDao.getEncoder(BUYER_2)).thenReturn(encoderLogic2);
        when(mSignalStorageManager.getSignals(BUYER_2)).thenReturn(fakeSignals);
        ListenableFuture<byte[]> failureResponse =
                Futures.immediateFailedFuture(new RuntimeException("Random exception"));
        when(mScriptEngine.encodeSignals(eq(encoderLogic2), any(), anyInt()))
                .thenReturn(failureResponse);

        when(mEncoderLogicMetadataDao.getAllBuyersWithRegisteredEncoders())
                .thenReturn(List.of(BUYER, BUYER_2));

        // This should gracefully handle Buyer_2 failure and not impact Buyer_1's encoding
        Void unused = mJobWorker.encodeProtectedSignals().get(5, TimeUnit.SECONDS);

        verify(mEncodedPayloadDao, times(1)).persistEncodedPayload(mEncodedPayloadCaptor.capture());
        assertEquals(BUYER, mEncodedPayloadCaptor.getValue().getBuyer());
        assertEquals(version1, mEncodedPayloadCaptor.getValue().getVersion());
        assertEquals(
                getSetFromBytes(validResponse),
                getSetFromBytes(mEncodedPayloadCaptor.getValue().getEncodedPayload()));
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

        when(mEncoderLogicMetadataDao.getBuyersWithEncodersBeforeTime(any()))
                .thenReturn(List.of(BUYER, BUYER_2));
        when(mEncoderLogicHandler.downloadAndUpdate(any(), any()))
                .thenReturn(FluentFuture.from(Futures.immediateFuture(true)));

        Void unused = mJobWorker.encodeProtectedSignals().get(5, TimeUnit.SECONDS);
        verify(mEncoderLogicHandler).downloadAndUpdate(eq(BUYER), any());
        verify(mEncoderLogicHandler).downloadAndUpdate(eq(BUYER_2), any());
    }

    @Test
    public void testEncodeProtectedSignalsAlsoUpdatesEncodersIsNotAffectedByEncodingFailures()
            throws ExecutionException, InterruptedException, TimeoutException {

        when(mEncoderLogicMetadataDao.getAllBuyersWithRegisteredEncoders())
                .thenReturn(List.of(BUYER, BUYER_2));

        String encoderLogic = "function buyer1_EncodeJs() {\" correct result \"}";
        int version1 = 1;
        DBEncoderLogic fakeEncoderLogicEntry =
                DBEncoderLogic.builder()
                        .setBuyer(BUYER)
                        .setVersion(version1)
                        .setCreationTime(Instant.now())
                        .build();
        Map<String, List<ProtectedSignal>> fakeSignals = new HashMap<>();
        when(mEncoderLogicMetadataDao.getEncoder(any())).thenReturn(fakeEncoderLogicEntry);
        when(mEncoderPersistenceDao.getEncoder(any())).thenReturn(encoderLogic);
        when(mSignalStorageManager.getSignals(any())).thenReturn(fakeSignals);

        // All the encodings are wired to fail with exceptions
        ListenableFuture<byte[]> failureResponse =
                Futures.immediateFailedFuture(new RuntimeException("Random exception"));
        when(mScriptEngine.encodeSignals(any(), any(), anyInt())).thenReturn(failureResponse);

        when(mEncoderLogicMetadataDao.getBuyersWithEncodersBeforeTime(any()))
                .thenReturn(List.of(BUYER, BUYER_2));
        when(mEncoderLogicHandler.downloadAndUpdate(any(), any()))
                .thenReturn(FluentFuture.from(Futures.immediateFuture(true)));

        Void unused = mJobWorker.encodeProtectedSignals().get(5, TimeUnit.SECONDS);
        verify(mEncoderLogicHandler).downloadAndUpdate(eq(BUYER), any());
        verify(mEncoderLogicHandler).downloadAndUpdate(eq(BUYER_2), any());
    }

    private String getBase64String(String str) {
        return Base64.getEncoder().encodeToString(str.getBytes());
    }

    private byte[] getBytesFromBase64(String base64String) {
        return Base64.getDecoder().decode(base64String);
    }

    private Set<Byte> getSetFromBytes(byte[] bytes) {
        Set<Byte> byteSet = new HashSet<>();

        for (byte b : bytes) {
            byteSet.add(b);
        }
        return byteSet;
    }
}

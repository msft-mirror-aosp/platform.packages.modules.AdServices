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

import static com.android.adservices.service.signals.SignalsFixture.DEV_CONTEXT;
import static com.android.adservices.service.stats.AdServicesStatsLog.AD_SERVICES_ERROR_REPORTED__ERROR_CODE__PAS_COLLISION_ERROR;
import static com.android.adservices.service.stats.AdServicesStatsLog.AD_SERVICES_ERROR_REPORTED__ERROR_CODE__PAS_JSON_PROCESSING_STATUS_SEMANTIC_ERROR;
import static com.android.adservices.service.stats.AdServicesStatsLog.AD_SERVICES_ERROR_REPORTED__ERROR_CODE__PAS_UNPACK_SIGNAL_UPDATES_JSON_FAILURE;
import static com.android.adservices.service.stats.AdServicesStatsLog.AD_SERVICES_ERROR_REPORTED__PPAPI_NAME__PAS;
import static com.android.adservices.service.stats.AdsRelevanceStatusUtils.JSON_PROCESSING_STATUS_SEMANTIC_ERROR;
import static com.android.adservices.service.stats.AdsRelevanceStatusUtils.JSON_PROCESSING_STATUS_SYNTACTIC_ERROR;

import static com.google.common.truth.Truth.assertThat;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertThrows;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
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
import com.android.adservices.data.signals.DBProtectedSignal;
import com.android.adservices.data.signals.ProtectedSignalsDao;
import com.android.adservices.service.signals.evict.FifoSignalEvictor;
import com.android.adservices.service.signals.evict.SignalEvictionController;
import com.android.adservices.service.signals.updateprocessors.UpdateEncoderEvent;
import com.android.adservices.service.signals.updateprocessors.UpdateEncoderEventHandler;
import com.android.adservices.service.signals.updateprocessors.UpdateOutput;
import com.android.adservices.service.signals.updateprocessors.UpdateProcessor;
import com.android.adservices.service.signals.updateprocessors.UpdateProcessorSelector;
import com.android.adservices.service.stats.pas.UpdateSignalsApiCalledStats;
import com.android.adservices.service.stats.pas.UpdateSignalsProcessReportedLogger;
import com.android.adservices.shared.testing.annotations.RequiresSdkLevelAtLeastT;

import org.json.JSONArray;
import org.json.JSONException;
import org.json.JSONObject;
import org.junit.Before;
import org.junit.Test;
import org.junit.runner.RunWith;
import org.mockito.ArgumentCaptor;
import org.mockito.Captor;
import org.mockito.Mock;
import org.mockito.junit.MockitoJUnitRunner;

import java.nio.ByteBuffer;
import java.time.Instant;
import java.util.Arrays;
import java.util.Collections;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.stream.Collectors;

@RunWith(MockitoJUnitRunner.class)
@SetErrorLogUtilDefaultParams(
        throwable = ExpectErrorLogUtilWithExceptionCall.Any.class,
        ppapiName = AD_SERVICES_ERROR_REPORTED__PPAPI_NAME__PAS)
@RequiresSdkLevelAtLeastT(reason = "PAS is only supported on T+")
public class UpdateProcessingOrchestratorTest extends AdServicesExtendedMockitoTestCase {

    public static final byte[] KEY_1 = {(byte) 1, (byte) 2, (byte) 3, (byte) 4};
    public static final byte[] KEY_2 = {(byte) 5, (byte) 6, (byte) 7, (byte) 8};
    public static final byte[] VALUE = {(byte) 42};
    private static final AdTechIdentifier ADTECH = CommonFixture.VALID_BUYER_1;
    private static final String PACKAGE = CommonFixture.TEST_PACKAGE_NAME_1;
    private static final Instant NOW = CommonFixture.FIXED_NOW;
    private static final String TEST_PROCESSOR = "test_processor";
    @Mock private ProtectedSignalsDao mProtectedSignalsDaoMock;
    @Mock private UpdateProcessorSelector mUpdateProcessorSelectorMock;
    @Mock private UpdateEncoderEventHandler mUpdateEncoderEventHandlerMock;
    @Mock private SignalEvictionController mSignalEvictionControllerMock;
    @Mock private UpdateSignalsProcessReportedLogger mUpdateSignalsProcessReportedLoggerMock;
    @Mock private ForcedEncoder mForcedEncoderMock;
    private UpdateSignalsApiCalledStats.Builder mUpdateSignalsApiCalledStats;
    @Captor ArgumentCaptor<List<DBProtectedSignal>> mInsertCaptor;
    @Captor ArgumentCaptor<List<DBProtectedSignal>> mRemoveCaptor;
    @Captor ArgumentCaptor<UpdateOutput> mUpdateOutputArgumentCaptor;

    private UpdateProcessingOrchestrator mUpdateProcessingOrchestrator;

    @Before
    public void setup() {
        mUpdateProcessingOrchestrator =
                new UpdateProcessingOrchestrator(
                        mProtectedSignalsDaoMock,
                        mUpdateProcessorSelectorMock,
                        mUpdateEncoderEventHandlerMock,
                        mSignalEvictionControllerMock,
                        mForcedEncoderMock);
        mUpdateSignalsApiCalledStats = UpdateSignalsApiCalledStats.builder();
    }

    @Test
    public void testUpdatesProcessorEmptyJson() {
        when(mProtectedSignalsDaoMock.getSignalsByBuyer(ADTECH)).thenReturn(List.of());
        mUpdateProcessingOrchestrator.processUpdates(
                ADTECH,
                PACKAGE,
                NOW,
                new JSONObject(),
                DEV_CONTEXT,
                mUpdateSignalsApiCalledStats,
                mUpdateSignalsProcessReportedLoggerMock);
        verify(mProtectedSignalsDaoMock).getSignalsByBuyer(eq(ADTECH));
        verify(mSignalEvictionControllerMock)
                .evict(
                        eq(ADTECH),
                        eq(Collections.emptyList()),
                        mUpdateOutputArgumentCaptor.capture(),
                        any(UpdateSignalsProcessReportedLogger.class));
        assertUpdateOutputEquals(new UpdateOutput(), mUpdateOutputArgumentCaptor.getValue());
        verifyZeroInteractions(mUpdateProcessorSelectorMock, mUpdateEncoderEventHandlerMock);
        verify(mProtectedSignalsDaoMock)
                .insertAndDelete(ADTECH, NOW, Collections.emptyList(), Collections.emptyList());
        verify(mForcedEncoderMock).forceEncodingAndUpdateEncoderForBuyer(ADTECH);
    }

    @Test
    @ExpectErrorLogUtilWithExceptionCall(
            errorCode = AD_SERVICES_ERROR_REPORTED__ERROR_CODE__PAS_UNPACK_SIGNAL_UPDATES_JSON_FAILURE)
    public void testUpdatesProcessorBadJson() throws Exception {
        final JSONException exception = new JSONException("JSONException for testing");
        when(mUpdateProcessorSelectorMock.getUpdateProcessor(TEST_PROCESSOR))
                .thenReturn(
                        new UpdateProcessor() {
                            @Override
                            public String getName() {
                                return null;
                            }

                            @Override
                            public UpdateOutput processUpdates(
                                    Object updates, Map<ByteBuffer, Set<DBProtectedSignal>> current)
                                    throws JSONException {
                                throw exception;
                            }
                        });

        JSONObject commandToNumber = new JSONObject();
        commandToNumber.put(TEST_PROCESSOR, 1);
        Throwable t =
                assertThrows(
                        "Couldn't unpack signal updates JSON",
                        IllegalArgumentException.class,
                        () ->
                                mUpdateProcessingOrchestrator.processUpdates(
                                        ADTECH,
                                        PACKAGE,
                                        NOW,
                                        commandToNumber,
                                        DEV_CONTEXT,
                                        mUpdateSignalsApiCalledStats,
                                        mUpdateSignalsProcessReportedLoggerMock));
        assertEquals(exception, t.getCause());
        verify(mProtectedSignalsDaoMock).getSignalsByBuyer(ADTECH);
        verifyZeroInteractions(mSignalEvictionControllerMock, mForcedEncoderMock);
        verifyNoMoreInteractions(mProtectedSignalsDaoMock);
        assertEquals(
                JSON_PROCESSING_STATUS_SYNTACTIC_ERROR,
                mUpdateSignalsApiCalledStats.build().getJsonProcessingStatus());
    }

    @Test
    public void testUpdatesProcessorSingleInsert() throws Exception {
        JSONObject json = new JSONObject();
        json.put(TEST_PROCESSOR, new JSONObject());

        when(mProtectedSignalsDaoMock.getSignalsByBuyer(any())).thenReturn(Collections.emptyList());

        UpdateOutput toReturn = new UpdateOutput();
        toReturn.getKeysTouched().add(ByteBuffer.wrap(KEY_1));
        DBProtectedSignal.Builder addedSignal =
                DBProtectedSignal.builder().setKey(KEY_1).setValue(VALUE);
        toReturn.getToAdd().add(addedSignal);
        when(mUpdateProcessorSelectorMock.getUpdateProcessor(TEST_PROCESSOR))
                .thenReturn(createProcessor(TEST_PROCESSOR, toReturn));

        mUpdateProcessingOrchestrator.processUpdates(
                ADTECH,
                PACKAGE,
                NOW,
                json,
                DEV_CONTEXT,
                mUpdateSignalsApiCalledStats,
                mUpdateSignalsProcessReportedLoggerMock);

        verify(mUpdateProcessorSelectorMock).getUpdateProcessor(eq(TEST_PROCESSOR));
        verify(mSignalEvictionControllerMock)
                .evict(
                        eq(ADTECH),
                        eq(List.of(addedSignal.build())),
                        mUpdateOutputArgumentCaptor.capture(),
                        any(UpdateSignalsProcessReportedLogger.class));
        assertUpdateOutputEquals(toReturn, mUpdateOutputArgumentCaptor.getValue());
        List<DBProtectedSignal> expected = List.of(createSignal(KEY_1, VALUE));
        verify(mProtectedSignalsDaoMock)
                .insertAndDelete(ADTECH, NOW, expected, Collections.emptyList());
        verify(mForcedEncoderMock).forceEncodingAndUpdateEncoderForBuyer(ADTECH);
    }

    @Test
    public void testUpdatesProcessorSingleInsertJsonArray() throws Exception {
        JSONObject json = new JSONObject();
        json.put(TEST_PROCESSOR, new JSONArray());

        when(mProtectedSignalsDaoMock.getSignalsByBuyer(any())).thenReturn(Collections.emptyList());

        UpdateOutput toReturn = new UpdateOutput();
        toReturn.getKeysTouched().add(ByteBuffer.wrap(KEY_1));
        DBProtectedSignal.Builder addedSignal =
                DBProtectedSignal.builder().setKey(KEY_1).setValue(VALUE);
        toReturn.getToAdd().add(addedSignal);
        when(mUpdateProcessorSelectorMock.getUpdateProcessor(TEST_PROCESSOR))
                .thenReturn(createProcessor(TEST_PROCESSOR, toReturn));

        mUpdateProcessingOrchestrator.processUpdates(
                ADTECH,
                PACKAGE,
                NOW,
                json,
                DEV_CONTEXT,
                mUpdateSignalsApiCalledStats,
                mUpdateSignalsProcessReportedLoggerMock);

        verify(mUpdateProcessorSelectorMock).getUpdateProcessor(eq(TEST_PROCESSOR));
        verify(mSignalEvictionControllerMock)
                .evict(
                        eq(ADTECH),
                        eq(List.of(addedSignal.build())),
                        mUpdateOutputArgumentCaptor.capture(),
                        any(UpdateSignalsProcessReportedLogger.class));
        assertUpdateOutputEquals(toReturn, mUpdateOutputArgumentCaptor.getValue());
        List<DBProtectedSignal> expected = Arrays.asList(createSignal(KEY_1, VALUE));
        verify(mProtectedSignalsDaoMock)
                .insertAndDelete(ADTECH, NOW, expected, Collections.emptyList());
        verify(mForcedEncoderMock).forceEncodingAndUpdateEncoderForBuyer(ADTECH);
    }

    @Test
    public void testUpdatesProcessorSingleRemove() throws Exception {
        DBProtectedSignal toRemove = createSignal(KEY_1, VALUE, 123L);
        DBProtectedSignal toKeep = createSignal(KEY_2, VALUE, 456L);
        JSONObject json = new JSONObject();
        json.put(TEST_PROCESSOR, new JSONObject());

        when(mProtectedSignalsDaoMock.getSignalsByBuyer(any()))
                .thenReturn(Arrays.asList(toRemove, toKeep));
        UpdateOutput toReturn = new UpdateOutput();
        toReturn.getKeysTouched().add(ByteBuffer.wrap(KEY_1));
        toReturn.getToRemove().add(toRemove);
        when(mUpdateProcessorSelectorMock.getUpdateProcessor(TEST_PROCESSOR))
                .thenReturn(createProcessor(TEST_PROCESSOR, toReturn));

        mUpdateProcessingOrchestrator.processUpdates(
                ADTECH,
                PACKAGE,
                NOW,
                json,
                DEV_CONTEXT,
                mUpdateSignalsApiCalledStats,
                mUpdateSignalsProcessReportedLoggerMock);

        verify(mProtectedSignalsDaoMock).getSignalsByBuyer(eq(ADTECH));
        verify(mUpdateProcessorSelectorMock).getUpdateProcessor(eq(TEST_PROCESSOR));
        verify(mSignalEvictionControllerMock)
                .evict(
                        eq(ADTECH),
                        eq(List.of(toKeep)),
                        mUpdateOutputArgumentCaptor.capture(),
                        any(UpdateSignalsProcessReportedLogger.class));
        assertUpdateOutputEquals(toReturn, mUpdateOutputArgumentCaptor.getValue());
        verify(mProtectedSignalsDaoMock)
                .insertAndDelete(ADTECH, NOW, Collections.emptyList(), Arrays.asList(toRemove));
        verify(mForcedEncoderMock).forceEncodingAndUpdateEncoderForBuyer(ADTECH);
    }

    @Test
    public void testUpdatesProcessorTwoInserts() throws Exception {
        JSONObject json = new JSONObject();
        json.put(TEST_PROCESSOR + 1, new JSONObject());
        json.put(TEST_PROCESSOR + 2, new JSONObject());

        when(mProtectedSignalsDaoMock.getSignalsByBuyer(any())).thenReturn(Collections.emptyList());

        UpdateOutput toReturnFirst = new UpdateOutput();
        toReturnFirst.getKeysTouched().add(ByteBuffer.wrap(KEY_1));
        DBProtectedSignal.Builder toAdd1 =
                DBProtectedSignal.builder().setKey(KEY_1).setValue(VALUE);
        toReturnFirst.getToAdd().add(toAdd1);
        when(mUpdateProcessorSelectorMock.getUpdateProcessor(TEST_PROCESSOR + 1))
                .thenReturn(createProcessor(TEST_PROCESSOR + 1, toReturnFirst));

        UpdateOutput toReturnSecond = new UpdateOutput();
        toReturnSecond.getKeysTouched().add(ByteBuffer.wrap(KEY_2));
        DBProtectedSignal.Builder toAdd2 =
                DBProtectedSignal.builder().setKey(KEY_2).setValue(VALUE);
        toReturnSecond.getToAdd().add(toAdd2);
        when(mUpdateProcessorSelectorMock.getUpdateProcessor(TEST_PROCESSOR + 2))
                .thenReturn(createProcessor(TEST_PROCESSOR, toReturnSecond));

        mUpdateProcessingOrchestrator.processUpdates(
                ADTECH,
                PACKAGE,
                NOW,
                json,
                DEV_CONTEXT,
                mUpdateSignalsApiCalledStats,
                mUpdateSignalsProcessReportedLoggerMock);

        verify(mUpdateProcessorSelectorMock).getUpdateProcessor(eq(TEST_PROCESSOR + 1));
        verify(mUpdateProcessorSelectorMock).getUpdateProcessor(eq(TEST_PROCESSOR + 2));
        UpdateOutput toEvictOutput = new UpdateOutput();
        toEvictOutput.getKeysTouched().add(ByteBuffer.wrap(KEY_1));
        toEvictOutput.getKeysTouched().add(ByteBuffer.wrap(KEY_2));
        toEvictOutput.getToAdd().add(toAdd1);
        toEvictOutput.getToAdd().add(toAdd2);
        verify(mSignalEvictionControllerMock)
                .evict(
                        eq(ADTECH),
                        eq(List.of(toAdd1.build(), toAdd2.build())),
                        mUpdateOutputArgumentCaptor.capture(),
                        any(UpdateSignalsProcessReportedLogger.class));
        assertUpdateOutputEquals(toEvictOutput, mUpdateOutputArgumentCaptor.getValue());
        DBProtectedSignal expected1 = createSignal(KEY_1, VALUE);
        DBProtectedSignal expected2 = createSignal(KEY_2, VALUE);
        verify(mProtectedSignalsDaoMock)
                .insertAndDelete(
                        eq(ADTECH), eq(NOW), mInsertCaptor.capture(), eq(Collections.emptyList()));
        assertThat(mInsertCaptor.getValue())
                .containsExactlyElementsIn(Arrays.asList(expected1, expected2));
        verify(mForcedEncoderMock).forceEncodingAndUpdateEncoderForBuyer(ADTECH);
    }

    @Test
    @ExpectErrorLogUtilCall(
            errorCode = AD_SERVICES_ERROR_REPORTED__ERROR_CODE__PAS_COLLISION_ERROR)
    @ExpectErrorLogUtilWithExceptionCall(
            errorCode = AD_SERVICES_ERROR_REPORTED__ERROR_CODE__PAS_JSON_PROCESSING_STATUS_SEMANTIC_ERROR)
    public void testUpdatesProcessorTwoInsertsSameKey() throws Exception {
        JSONObject json = new JSONObject();
        json.put(TEST_PROCESSOR + 1, new JSONObject());
        json.put(TEST_PROCESSOR + 2, new JSONObject());

        when(mProtectedSignalsDaoMock.getSignalsByBuyer(any())).thenReturn(Collections.emptyList());

        UpdateOutput toReturnFirst = new UpdateOutput();
        toReturnFirst.getKeysTouched().add(ByteBuffer.wrap(KEY_1));
        toReturnFirst.getToAdd().add(DBProtectedSignal.builder().setKey(KEY_1).setValue(VALUE));
        when(mUpdateProcessorSelectorMock.getUpdateProcessor(TEST_PROCESSOR + 1))
                .thenReturn(createProcessor(TEST_PROCESSOR + 1, toReturnFirst));

        UpdateOutput toReturnSecond = new UpdateOutput();
        toReturnSecond.getKeysTouched().add(ByteBuffer.wrap(KEY_1));
        toReturnSecond.getToAdd().add(DBProtectedSignal.builder().setKey(KEY_1).setValue(VALUE));
        when(mUpdateProcessorSelectorMock.getUpdateProcessor(TEST_PROCESSOR + 2))
                .thenReturn(createProcessor(TEST_PROCESSOR, toReturnSecond));

        assertThrows(
                IllegalArgumentException.class,
                () ->
                        mUpdateProcessingOrchestrator.processUpdates(
                                ADTECH,
                                PACKAGE,
                                NOW,
                                json,
                                DEV_CONTEXT,
                                mUpdateSignalsApiCalledStats,
                                mUpdateSignalsProcessReportedLoggerMock));
        verifyZeroInteractions(mSignalEvictionControllerMock, mForcedEncoderMock);
        assertEquals(
                JSON_PROCESSING_STATUS_SEMANTIC_ERROR,
                mUpdateSignalsApiCalledStats.build().getJsonProcessingStatus());
    }

    @Test
    public void testUpdatesProcessorTwoDeletesSameKey() throws Exception {
        DBProtectedSignal toRemove1 = createSignal(KEY_1, VALUE, 123L);
        DBProtectedSignal toRemove2 = createSignal(KEY_1, VALUE, 456L);
        JSONObject json = new JSONObject();
        json.put(TEST_PROCESSOR, new JSONObject());

        when(mProtectedSignalsDaoMock.getSignalsByBuyer(any()))
                .thenReturn(Arrays.asList(toRemove1, toRemove2));
        UpdateOutput toReturn = new UpdateOutput();
        toReturn.getKeysTouched().add(ByteBuffer.wrap(KEY_1));
        toReturn.getToRemove().add(toRemove1);
        toReturn.getToRemove().add(toRemove2);
        when(mUpdateProcessorSelectorMock.getUpdateProcessor(TEST_PROCESSOR))
                .thenReturn(createProcessor(TEST_PROCESSOR, toReturn));

        mUpdateProcessingOrchestrator.processUpdates(
                ADTECH,
                PACKAGE,
                NOW,
                json,
                DEV_CONTEXT,
                mUpdateSignalsApiCalledStats,
                mUpdateSignalsProcessReportedLoggerMock);

        verify(mProtectedSignalsDaoMock).getSignalsByBuyer(eq(ADTECH));
        verify(mUpdateProcessorSelectorMock).getUpdateProcessor(eq(TEST_PROCESSOR));
        verify(mSignalEvictionControllerMock)
                .evict(
                        eq(ADTECH),
                        eq(List.of()),
                        mUpdateOutputArgumentCaptor.capture(),
                        any(UpdateSignalsProcessReportedLogger.class));
        assertUpdateOutputEquals(toReturn, mUpdateOutputArgumentCaptor.getValue());
        verify(mProtectedSignalsDaoMock)
                .insertAndDelete(
                        ADTECH, NOW, Collections.emptyList(), Arrays.asList(toRemove1, toRemove2));
        verify(mForcedEncoderMock).forceEncodingAndUpdateEncoderForBuyer(ADTECH);
    }

    @Test
    public void testUpdatesProcessorNoEncoderUpdates() throws JSONException {
        JSONObject json = new JSONObject();
        json.put(TEST_PROCESSOR, new JSONObject());

        UpdateOutput toReturn = new UpdateOutput();
        when(mUpdateProcessorSelectorMock.getUpdateProcessor(TEST_PROCESSOR))
                .thenReturn(createProcessor(TEST_PROCESSOR, toReturn));
        mUpdateProcessingOrchestrator.processUpdates(
                ADTECH,
                PACKAGE,
                NOW,
                json,
                DEV_CONTEXT,
                mUpdateSignalsApiCalledStats,
                mUpdateSignalsProcessReportedLoggerMock);
        verifyZeroInteractions(mUpdateEncoderEventHandlerMock);
        verify(mForcedEncoderMock).forceEncodingAndUpdateEncoderForBuyer(ADTECH);
    }

    @Test
    public void testUpdatesProcessorSingleEncoderUpdate() throws JSONException {
        JSONObject json = new JSONObject();
        json.put(TEST_PROCESSOR, new JSONObject());

        UpdateOutput toReturn = new UpdateOutput();
        UpdateEncoderEvent event =
                UpdateEncoderEvent.builder()
                        .setUpdateType(UpdateEncoderEvent.UpdateType.REGISTER)
                        .setEncoderEndpointUri(
                                CommonFixture.getUri(CommonFixture.VALID_BUYER_1, "/"))
                        .build();
        toReturn.setUpdateEncoderEvent(event);
        when(mUpdateProcessorSelectorMock.getUpdateProcessor(TEST_PROCESSOR))
                .thenReturn(createProcessor(TEST_PROCESSOR, toReturn));
        mUpdateProcessingOrchestrator.processUpdates(
                ADTECH,
                PACKAGE,
                NOW,
                json,
                DEV_CONTEXT,
                mUpdateSignalsApiCalledStats,
                mUpdateSignalsProcessReportedLoggerMock);
        verify(mUpdateEncoderEventHandlerMock)
                .handle(CommonFixture.VALID_BUYER_1, event, DEV_CONTEXT);
        verifyZeroInteractions(mForcedEncoderMock);
    }

    @Test
    public void testUpdatesProcessorSingleInsert_evictNewlyAddedSignal() throws Exception {
        JSONObject json = new JSONObject();
        json.put(TEST_PROCESSOR, new JSONObject());

        when(mProtectedSignalsDaoMock.getSignalsByBuyer(any())).thenReturn(Collections.emptyList());

        UpdateOutput toReturn = new UpdateOutput();
        toReturn.getKeysTouched().add(ByteBuffer.wrap(KEY_1));
        DBProtectedSignal.Builder addedSignal =
                DBProtectedSignal.builder().setKey(KEY_1).setValue(VALUE);
        toReturn.getToAdd().add(addedSignal);
        when(mUpdateProcessorSelectorMock.getUpdateProcessor(TEST_PROCESSOR))
                .thenReturn(createProcessor(TEST_PROCESSOR, toReturn));

        SignalEvictionController signalEvictionController =
                new SignalEvictionController(
                        List.of(new FifoSignalEvictor()),
                        mMockFlags.getProtectedSignalsMaxSignalSizePerBuyerBytes(),
                        mMockFlags
                                .getProtectedSignalsMaxSignalSizePerBuyerWithOversubsciptionBytes()) {
                    @Override
                    public void evict(
                            AdTechIdentifier adTech,
                            List<DBProtectedSignal> updatedSignals,
                            UpdateOutput combinedUpdates,
                            UpdateSignalsProcessReportedLogger updateSignalsProcessReportedLogger) {
                        combinedUpdates.getToRemove().add(updatedSignals.remove(0));
                    }
                };
        mUpdateProcessingOrchestrator =
                new UpdateProcessingOrchestrator(
                        mProtectedSignalsDaoMock,
                        mUpdateProcessorSelectorMock,
                        mUpdateEncoderEventHandlerMock,
                        signalEvictionController,
                        mForcedEncoderMock);

        mUpdateProcessingOrchestrator.processUpdates(
                ADTECH,
                PACKAGE,
                NOW,
                json,
                DEV_CONTEXT,
                mUpdateSignalsApiCalledStats,
                mUpdateSignalsProcessReportedLoggerMock);

        verify(mUpdateProcessorSelectorMock).getUpdateProcessor(eq(TEST_PROCESSOR));

        List<DBProtectedSignal> expected = Arrays.asList(createSignal(KEY_1, VALUE));
        verify(mProtectedSignalsDaoMock).insertAndDelete(ADTECH, NOW, expected, expected);
        verify(mForcedEncoderMock).forceEncodingAndUpdateEncoderForBuyer(ADTECH);
    }

    private DBProtectedSignal createSignal(byte[] key, byte[] value) {
        return DBProtectedSignal.builder()
                .setBuyer(ADTECH)
                .setPackageName(PACKAGE)
                .setCreationTime(CommonFixture.FIXED_NOW)
                .setKey(key)
                .setValue(value)
                .build();
    }

    private DBProtectedSignal createSignal(byte[] key, byte[] value, long id) {
        return DBProtectedSignal.builder()
                .setId(id)
                .setBuyer(ADTECH)
                .setPackageName(PACKAGE)
                .setCreationTime(CommonFixture.FIXED_NOW)
                .setKey(key)
                .setValue(value)
                .build();
    }

    private UpdateProcessor createProcessor(String name, UpdateOutput toReturn) {
        return new UpdateProcessor() {
            @Override
            public String getName() {
                return name;
            }

            @Override
            public UpdateOutput processUpdates(
                    Object updates, Map<ByteBuffer, Set<DBProtectedSignal>> current)
                    throws JSONException {
                return toReturn;
            }
        };
    }

    private void assertUpdateOutputEquals(UpdateOutput expect, UpdateOutput actual) {
        assertEquals(
                expect.getToAdd().stream()
                        .map(DBProtectedSignal.Builder::build)
                        .collect(Collectors.toList()),
                actual.getToAdd().stream()
                        .map(DBProtectedSignal.Builder::build)
                        .collect(Collectors.toList()));
        assertEquals(expect.getUpdateEncoderEvent(), actual.getUpdateEncoderEvent());
        assertEquals(expect.getToRemove(), actual.getToRemove());
        assertEquals(expect.getKeysTouched(), actual.getKeysTouched());
    }
}

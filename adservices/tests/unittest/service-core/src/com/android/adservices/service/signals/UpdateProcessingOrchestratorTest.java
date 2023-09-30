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
import static org.junit.Assert.assertThrows;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyZeroInteractions;
import static org.mockito.Mockito.when;

import android.adservices.common.AdTechIdentifier;
import android.adservices.common.CommonFixture;

import com.android.adservices.data.signals.DBProtectedSignal;
import com.android.adservices.data.signals.ProtectedSignalsDao;
import com.android.adservices.service.signals.updateprocessors.UpdateEncoderEvent;
import com.android.adservices.service.signals.updateprocessors.UpdateEncoderEventHandler;
import com.android.adservices.service.signals.updateprocessors.UpdateOutput;
import com.android.adservices.service.signals.updateprocessors.UpdateProcessor;
import com.android.adservices.service.signals.updateprocessors.UpdateProcessorSelector;

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

@RunWith(MockitoJUnitRunner.class)
public class UpdateProcessingOrchestratorTest {

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
    @Captor ArgumentCaptor<List<DBProtectedSignal>> mInsertCaptor;
    @Captor ArgumentCaptor<List<DBProtectedSignal>> mRemoveCaptor;

    private UpdateProcessingOrchestrator mUpdateProcessingOrchestrator;

    @Before
    public void setup() {
        mUpdateProcessingOrchestrator =
                new UpdateProcessingOrchestrator(
                        mProtectedSignalsDaoMock,
                        mUpdateProcessorSelectorMock,
                        mUpdateEncoderEventHandlerMock);
    }

    @Test
    public void testUpdatesProcessorEmptyJson() {
        mUpdateProcessingOrchestrator.processUpdates(ADTECH, PACKAGE, NOW, new JSONObject());
        verify(mProtectedSignalsDaoMock).getSignalsByBuyer(eq(ADTECH));
        verify(mProtectedSignalsDaoMock)
                .insertAndDelete(Collections.emptyList(), Collections.emptyList());
        verifyZeroInteractions(mUpdateProcessorSelectorMock);
    }

    @Test
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
                                        ADTECH, PACKAGE, NOW, commandToNumber));
        assertEquals(exception, t.getCause());
    }

    @Test
    public void testUpdatesProcessorSingleInsert() throws Exception {
        JSONObject json = new JSONObject();
        json.put(TEST_PROCESSOR, new JSONObject());

        when(mProtectedSignalsDaoMock.getSignalsByBuyer(any())).thenReturn(Collections.emptyList());

        UpdateOutput toReturn = new UpdateOutput();
        toReturn.getKeysTouched().add(ByteBuffer.wrap(KEY_1));
        toReturn.getToAdd().add(DBProtectedSignal.builder().setKey(KEY_1).setValue(VALUE));
        when(mUpdateProcessorSelectorMock.getUpdateProcessor(TEST_PROCESSOR))
                .thenReturn(createProcessor(TEST_PROCESSOR, toReturn));

        mUpdateProcessingOrchestrator.processUpdates(ADTECH, PACKAGE, NOW, json);

        List<DBProtectedSignal> expected = Arrays.asList(createSignal(KEY_1, VALUE));
        verify(mProtectedSignalsDaoMock).insertAndDelete(eq(expected), eq(Collections.emptyList()));
        verify(mUpdateProcessorSelectorMock).getUpdateProcessor(eq(TEST_PROCESSOR));
    }

    @Test
    public void testUpdatesProcessorSingleInsertJsonArray() throws Exception {
        JSONObject json = new JSONObject();
        json.put(TEST_PROCESSOR, new JSONArray());

        when(mProtectedSignalsDaoMock.getSignalsByBuyer(any())).thenReturn(Collections.emptyList());

        UpdateOutput toReturn = new UpdateOutput();
        toReturn.getKeysTouched().add(ByteBuffer.wrap(KEY_1));
        toReturn.getToAdd().add(DBProtectedSignal.builder().setKey(KEY_1).setValue(VALUE));
        when(mUpdateProcessorSelectorMock.getUpdateProcessor(TEST_PROCESSOR))
                .thenReturn(createProcessor(TEST_PROCESSOR, toReturn));

        mUpdateProcessingOrchestrator.processUpdates(ADTECH, PACKAGE, NOW, json);

        List<DBProtectedSignal> expected = Arrays.asList(createSignal(KEY_1, VALUE));
        verify(mProtectedSignalsDaoMock).insertAndDelete(eq(expected), eq(Collections.emptyList()));
        verify(mUpdateProcessorSelectorMock).getUpdateProcessor(eq(TEST_PROCESSOR));
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

        mUpdateProcessingOrchestrator.processUpdates(ADTECH, PACKAGE, NOW, json);

        verify(mProtectedSignalsDaoMock).getSignalsByBuyer(eq(ADTECH));
        verify(mProtectedSignalsDaoMock)
                .insertAndDelete(eq(Collections.emptyList()), eq(Arrays.asList(toRemove)));
        verify(mUpdateProcessorSelectorMock).getUpdateProcessor(eq(TEST_PROCESSOR));
    }

    @Test
    public void testUpdatesProcessorTwoInserts() throws Exception {
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
        toReturnSecond.getKeysTouched().add(ByteBuffer.wrap(KEY_2));
        toReturnSecond.getToAdd().add(DBProtectedSignal.builder().setKey(KEY_2).setValue(VALUE));
        when(mUpdateProcessorSelectorMock.getUpdateProcessor(TEST_PROCESSOR + 2))
                .thenReturn(createProcessor(TEST_PROCESSOR, toReturnSecond));

        mUpdateProcessingOrchestrator.processUpdates(ADTECH, PACKAGE, NOW, json);

        DBProtectedSignal expected1 = createSignal(KEY_1, VALUE);
        DBProtectedSignal expected2 = createSignal(KEY_2, VALUE);
        verify(mProtectedSignalsDaoMock)
                .insertAndDelete(mInsertCaptor.capture(), eq(Collections.emptyList()));
        assertThat(mInsertCaptor.getValue())
                .containsExactlyElementsIn(Arrays.asList(expected1, expected2));
        verify(mUpdateProcessorSelectorMock).getUpdateProcessor(eq(TEST_PROCESSOR + 1));
        verify(mUpdateProcessorSelectorMock).getUpdateProcessor(eq(TEST_PROCESSOR + 2));
    }

    @Test
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
                () -> mUpdateProcessingOrchestrator.processUpdates(ADTECH, PACKAGE, NOW, json));
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

        mUpdateProcessingOrchestrator.processUpdates(ADTECH, PACKAGE, NOW, json);

        verify(mProtectedSignalsDaoMock).getSignalsByBuyer(eq(ADTECH));
        verify(mProtectedSignalsDaoMock)
                .insertAndDelete(
                        eq(Collections.emptyList()), eq(Arrays.asList(toRemove1, toRemove2)));
        verify(mUpdateProcessorSelectorMock).getUpdateProcessor(eq(TEST_PROCESSOR));
    }

    @Test
    public void testUpdatesProcessorNoEncoderUpdates() throws JSONException {
        JSONObject json = new JSONObject();
        json.put(TEST_PROCESSOR, new JSONObject());

        UpdateOutput toReturn = new UpdateOutput();
        when(mUpdateProcessorSelectorMock.getUpdateProcessor(TEST_PROCESSOR))
                .thenReturn(createProcessor(TEST_PROCESSOR, toReturn));
        mUpdateProcessingOrchestrator.processUpdates(ADTECH, PACKAGE, NOW, json);
        verifyZeroInteractions(mUpdateEncoderEventHandlerMock);
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
        mUpdateProcessingOrchestrator.processUpdates(ADTECH, PACKAGE, NOW, json);
        verify(mUpdateEncoderEventHandlerMock).handle(CommonFixture.VALID_BUYER_1, event);
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
}

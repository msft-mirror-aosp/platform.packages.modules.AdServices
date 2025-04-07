/*
 * Copyright (C) 2025 The Android Open Source Project
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

package com.android.adservices.service.signals.updateprocessors.append;

import static com.android.adservices.service.signals.SignalsFixture.BASE64_KEY_1;
import static com.android.adservices.service.signals.SignalsFixture.BASE64_KEY_2;
import static com.android.adservices.service.signals.SignalsFixture.BASE64_VALUE_1;
import static com.android.adservices.service.signals.SignalsFixture.BASE64_VALUE_2;
import static com.android.adservices.service.signals.SignalsFixture.BB_KEY_1;
import static com.android.adservices.service.signals.SignalsFixture.BB_KEY_2;
import static com.android.adservices.service.signals.SignalsFixture.ID_1;
import static com.android.adservices.service.signals.SignalsFixture.ID_2;
import static com.android.adservices.service.signals.SignalsFixture.ID_3;
import static com.android.adservices.service.signals.SignalsFixture.KEY_1;
import static com.android.adservices.service.signals.SignalsFixture.KEY_2;
import static com.android.adservices.service.signals.SignalsFixture.NOW;
import static com.android.adservices.service.signals.SignalsFixture.VALUE_1;
import static com.android.adservices.service.signals.SignalsFixture.VALUE_2;
import static com.android.adservices.service.signals.SignalsFixture.createSignal;
import static com.android.adservices.service.signals.SignalsFixture.expectThatSignalBuilderListsAreEqual;

import static org.junit.Assert.assertThrows;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import com.android.adservices.common.AdServicesMockitoTestCase;
import com.android.adservices.data.signals.DBProtectedSignal;
import com.android.adservices.service.signals.evict.EvictionPriority;
import com.android.adservices.service.signals.updateprocessors.UpdateOutput;
import com.android.adservices.service.signals.updateprocessors.evictionpriority.EvictionPriorityHandler;
import com.android.adservices.service.stats.pas.UpdateSignalsProcessReportedLogger;
import com.android.adservices.shared.testing.annotations.RequiresSdkLevelAtLeastT;

import com.google.common.collect.ImmutableList;
import com.google.common.collect.ImmutableMap;
import com.google.common.collect.ImmutableSet;

import org.json.JSONArray;
import org.json.JSONObject;
import org.junit.Before;
import org.junit.Test;
import org.mockito.Mock;

import java.nio.ByteBuffer;
import java.time.Duration;
import java.util.List;
import java.util.Map;
import java.util.Set;

@RequiresSdkLevelAtLeastT(reason = "PAS is only supported on T+")
public class AppendV1Test extends AdServicesMockitoTestCase {

    /*
     * Hardcoding names here since JSON keys are an external
     * contract and changing them should require test changes.
     */
    private static final String MAX_SIGNALS = "max_signals";
    private static final String VALUES = "values";
    private static final String EVICTION_PRIORITY = "eviction_priority";

    @Mock private EvictionPriorityHandler mEvictionPriorityHandlerMock;
    @Mock private UpdateSignalsProcessReportedLogger mUpdateSignalsProcessReportedLoggerMock;
    private AppendV1 mAppendV1;

    @Before
    public void setup() {
        mAppendV1 = new AppendV1(mEvictionPriorityHandlerMock);
    }

    @Test
    public void testAppendSingle() throws Exception {
        JSONArray valuesJson = new JSONArray();
        valuesJson.put(BASE64_VALUE_1);

        JSONObject appendJson = new JSONObject();
        appendJson.put(VALUES, valuesJson);
        appendJson.put(MAX_SIGNALS, 1);
        appendJson.put(EVICTION_PRIORITY, EvictionPriority.EVICT_SOONER);

        when(mEvictionPriorityHandlerMock.getEvictionPriority(
                        BB_KEY_1, appendJson, mUpdateSignalsProcessReportedLoggerMock))
                .thenReturn(EvictionPriority.EVICT_SOONER);

        JSONObject updatesJson = new JSONObject();
        updatesJson.put(BASE64_KEY_1, appendJson);

        UpdateOutput output =
                mAppendV1.processUpdates(
                        updatesJson, ImmutableMap.of(), mUpdateSignalsProcessReportedLoggerMock);

        verify(mEvictionPriorityHandlerMock)
                .getEvictionPriority(BB_KEY_1, appendJson, mUpdateSignalsProcessReportedLoggerMock);
        expect.withMessage("keysTouched").that(output.getKeysTouched()).containsExactly(BB_KEY_1);
        expect.withMessage("toRemove").that(output.getToRemove()).isEmpty();
        List<DBProtectedSignal.Builder> expectedToAdd =
                ImmutableList.of(
                        DBProtectedSignal.builder()
                                .setKey(KEY_1)
                                .setValue(VALUE_1)
                                .setEvictionPriority(EvictionPriority.EVICT_SOONER));
        expectThatSignalBuilderListsAreEqual(expect, "toAdd", output.getToAdd(), expectedToAdd);
    }

    @Test
    public void testAppendMultipleValues() throws Exception {
        JSONArray valuesJson = new JSONArray();
        valuesJson.put(BASE64_VALUE_1);
        valuesJson.put(BASE64_VALUE_2);

        JSONObject appendJson = new JSONObject();
        appendJson.put(VALUES, valuesJson);
        appendJson.put(MAX_SIGNALS, 2);
        appendJson.put(EVICTION_PRIORITY, EvictionPriority.EVICT_SOONER);

        when(mEvictionPriorityHandlerMock.getEvictionPriority(
                        BB_KEY_1, appendJson, mUpdateSignalsProcessReportedLoggerMock))
                .thenReturn(EvictionPriority.EVICT_SOONER);

        JSONObject updatesJson = new JSONObject();
        updatesJson.put(BASE64_KEY_1, appendJson);

        UpdateOutput output =
                mAppendV1.processUpdates(
                        updatesJson, ImmutableMap.of(), mUpdateSignalsProcessReportedLoggerMock);

        verify(mEvictionPriorityHandlerMock)
                .getEvictionPriority(BB_KEY_1, appendJson, mUpdateSignalsProcessReportedLoggerMock);
        expect.withMessage("keysTouched").that(output.getKeysTouched()).containsExactly(BB_KEY_1);
        expect.withMessage("toRemove").that(output.getToRemove()).isEmpty();
        List<DBProtectedSignal.Builder> expectedToAdd =
                ImmutableList.of(
                        DBProtectedSignal.builder()
                                .setKey(KEY_1)
                                .setValue(VALUE_1)
                                .setEvictionPriority(EvictionPriority.EVICT_SOONER),
                        DBProtectedSignal.builder()
                                .setKey(KEY_1)
                                .setValue(VALUE_2)
                                .setEvictionPriority(EvictionPriority.EVICT_SOONER));
        expectThatSignalBuilderListsAreEqual(expect, "toAdd", output.getToAdd(), expectedToAdd);
    }

    @Test
    public void testAppendMultipleKeys() throws Exception {
        JSONArray valuesJson1 = new JSONArray();
        valuesJson1.put(BASE64_VALUE_1);
        valuesJson1.put(BASE64_VALUE_2);

        JSONArray valuesJson2 = new JSONArray();
        valuesJson2.put(BASE64_VALUE_2);

        JSONObject appendJson1 = new JSONObject();
        appendJson1.put(VALUES, valuesJson1);
        appendJson1.put(MAX_SIGNALS, 2);
        appendJson1.put(EVICTION_PRIORITY, EvictionPriority.EVICT_SOONER);

        JSONObject appendJson2 = new JSONObject();
        appendJson2.put(VALUES, valuesJson2);
        appendJson2.put(MAX_SIGNALS, 1);
        appendJson2.put(EVICTION_PRIORITY, EvictionPriority.EVICT_LATER);

        when(mEvictionPriorityHandlerMock.getEvictionPriority(
                        BB_KEY_1, appendJson1, mUpdateSignalsProcessReportedLoggerMock))
                .thenReturn(EvictionPriority.EVICT_SOONER);
        when(mEvictionPriorityHandlerMock.getEvictionPriority(
                        BB_KEY_2, appendJson2, mUpdateSignalsProcessReportedLoggerMock))
                .thenReturn(EvictionPriority.EVICT_LATER);

        JSONObject updatesJson = new JSONObject();
        updatesJson.put(BASE64_KEY_1, appendJson1);
        updatesJson.put(BASE64_KEY_2, appendJson2);

        UpdateOutput output =
                mAppendV1.processUpdates(
                        updatesJson, ImmutableMap.of(), mUpdateSignalsProcessReportedLoggerMock);

        verify(mEvictionPriorityHandlerMock)
                .getEvictionPriority(
                        BB_KEY_1, appendJson1, mUpdateSignalsProcessReportedLoggerMock);
        verify(mEvictionPriorityHandlerMock)
                .getEvictionPriority(
                        BB_KEY_2, appendJson2, mUpdateSignalsProcessReportedLoggerMock);
        expect.withMessage("keysTouched")
                .that(output.getKeysTouched())
                .containsExactly(BB_KEY_1, BB_KEY_2);
        expect.withMessage("toRemove").that(output.getToRemove()).isEmpty();
        List<DBProtectedSignal.Builder> expectedToAdd =
                ImmutableList.of(
                        DBProtectedSignal.builder()
                                .setKey(KEY_1)
                                .setValue(VALUE_1)
                                .setEvictionPriority(EvictionPriority.EVICT_SOONER),
                        DBProtectedSignal.builder()
                                .setKey(KEY_1)
                                .setValue(VALUE_2)
                                .setEvictionPriority(EvictionPriority.EVICT_SOONER),
                        DBProtectedSignal.builder()
                                .setKey(KEY_2)
                                .setValue(VALUE_2)
                                .setEvictionPriority(EvictionPriority.EVICT_LATER));
        expectThatSignalBuilderListsAreEqual(expect, "toAdd", output.getToAdd(), expectedToAdd);
    }

    @Test
    public void testOverwriteExisting() throws Exception {
        JSONArray valuesJson = new JSONArray();
        valuesJson.put(BASE64_VALUE_1);

        JSONObject appendJson = new JSONObject();
        appendJson.put(VALUES, valuesJson);
        appendJson.put(MAX_SIGNALS, 2);
        appendJson.put(EVICTION_PRIORITY, EvictionPriority.EVICT_SOONER);

        when(mEvictionPriorityHandlerMock.getEvictionPriority(
                        BB_KEY_1, appendJson, mUpdateSignalsProcessReportedLoggerMock))
                .thenReturn(EvictionPriority.EVICT_SOONER);

        JSONObject updatesJson = new JSONObject();
        updatesJson.put(BASE64_KEY_1, appendJson);

        DBProtectedSignal toOverwrite =
                createSignal(
                        KEY_1,
                        VALUE_1,
                        ID_1,
                        NOW.minus(Duration.ofDays(1)),
                        EvictionPriority.EVICT_SOONER);
        DBProtectedSignal toKeep =
                createSignal(KEY_1, VALUE_2, ID_2, NOW, EvictionPriority.EVICT_SOONER);
        Map<ByteBuffer, Set<DBProtectedSignal>> existingSignals =
                ImmutableMap.of(BB_KEY_1, ImmutableSet.of(toOverwrite, toKeep));

        UpdateOutput output =
                mAppendV1.processUpdates(
                        updatesJson, existingSignals, mUpdateSignalsProcessReportedLoggerMock);

        verify(mEvictionPriorityHandlerMock)
                .getEvictionPriority(BB_KEY_1, appendJson, mUpdateSignalsProcessReportedLoggerMock);
        expect.withMessage("keysTouched").that(output.getKeysTouched()).containsExactly(BB_KEY_1);
        expect.withMessage("toRemove").that(output.getToRemove()).containsExactly(toOverwrite);
        List<DBProtectedSignal.Builder> expectedToAdd =
                ImmutableList.of(
                        DBProtectedSignal.builder()
                                .setKey(KEY_1)
                                .setValue(VALUE_1)
                                .setEvictionPriority(EvictionPriority.EVICT_SOONER));
        expectThatSignalBuilderListsAreEqual(expect, "toAdd", output.getToAdd(), expectedToAdd);
    }

    @Test
    public void testOverwriteMultipleExisting() throws Exception {
        JSONArray valuesJson = new JSONArray();
        valuesJson.put(BASE64_VALUE_1);

        JSONObject appendJson = new JSONObject();
        appendJson.put(VALUES, valuesJson);
        appendJson.put(MAX_SIGNALS, 2);
        appendJson.put(EVICTION_PRIORITY, EvictionPriority.EVICT_SOONER);

        when(mEvictionPriorityHandlerMock.getEvictionPriority(
                        BB_KEY_1, appendJson, mUpdateSignalsProcessReportedLoggerMock))
                .thenReturn(EvictionPriority.EVICT_SOONER);

        JSONObject updatesJson = new JSONObject();
        updatesJson.put(BASE64_KEY_1, appendJson);

        DBProtectedSignal toOverwrite1 =
                createSignal(
                        KEY_1,
                        VALUE_1,
                        ID_1,
                        NOW.minus(Duration.ofDays(1)),
                        EvictionPriority.EVICT_SOONER);
        DBProtectedSignal toOverwrite2 =
                createSignal(
                        KEY_1,
                        VALUE_1,
                        ID_2,
                        NOW.minus(Duration.ofDays(2)),
                        EvictionPriority.EVICT_SOONER);
        DBProtectedSignal toKeep =
                createSignal(KEY_1, VALUE_2, ID_3, NOW, EvictionPriority.EVICT_SOONER);
        Map<ByteBuffer, Set<DBProtectedSignal>> existingSignals =
                ImmutableMap.of(BB_KEY_1, ImmutableSet.of(toOverwrite1, toOverwrite2, toKeep));

        UpdateOutput output =
                mAppendV1.processUpdates(
                        updatesJson, existingSignals, mUpdateSignalsProcessReportedLoggerMock);

        verify(mEvictionPriorityHandlerMock)
                .getEvictionPriority(BB_KEY_1, appendJson, mUpdateSignalsProcessReportedLoggerMock);
        expect.withMessage("keysTouched").that(output.getKeysTouched()).containsExactly(BB_KEY_1);
        expect.withMessage("toRemove")
                .that(output.getToRemove())
                .containsExactly(toOverwrite1, toOverwrite2);
        List<DBProtectedSignal.Builder> expectedToAdd =
                ImmutableList.of(
                        DBProtectedSignal.builder()
                                .setKey(KEY_1)
                                .setValue(VALUE_1)
                                .setEvictionPriority(EvictionPriority.EVICT_SOONER));
        expectThatSignalBuilderListsAreEqual(expect, "toAdd", output.getToAdd(), expectedToAdd);
    }

    @Test
    public void testAddToExistingWithNewProperties() throws Exception {
        JSONArray valuesJson = new JSONArray();
        valuesJson.put(BASE64_VALUE_1);

        JSONObject appendJson = new JSONObject();
        appendJson.put(VALUES, valuesJson);
        appendJson.put(MAX_SIGNALS, 3);
        appendJson.put(EVICTION_PRIORITY, EvictionPriority.EVICT_SOONER);

        when(mEvictionPriorityHandlerMock.getEvictionPriority(
                        BB_KEY_1, appendJson, mUpdateSignalsProcessReportedLoggerMock))
                .thenReturn(EvictionPriority.EVICT_SOONER);

        JSONObject updatesJson = new JSONObject();
        updatesJson.put(BASE64_KEY_1, appendJson);

        DBProtectedSignal existing1 =
                createSignal(
                        KEY_1,
                        VALUE_1,
                        ID_1,
                        NOW.minus(Duration.ofDays(1)),
                        EvictionPriority.EVICT_LATER);
        DBProtectedSignal existing2 =
                createSignal(KEY_1, VALUE_2, ID_1, NOW, EvictionPriority.EVICT_LATER);
        Map<ByteBuffer, Set<DBProtectedSignal>> existingSignals =
                ImmutableMap.of(BB_KEY_1, ImmutableSet.of(existing1, existing2));

        UpdateOutput output =
                mAppendV1.processUpdates(
                        updatesJson, existingSignals, mUpdateSignalsProcessReportedLoggerMock);

        verify(mEvictionPriorityHandlerMock)
                .getEvictionPriority(BB_KEY_1, appendJson, mUpdateSignalsProcessReportedLoggerMock);
        expect.withMessage("keysTouched").that(output.getKeysTouched()).containsExactly(BB_KEY_1);
        expect.withMessage("toRemove")
                .that(output.getToRemove())
                .containsExactly(existing1, existing2);
        List<DBProtectedSignal.Builder> expectedToAdd =
                ImmutableList.of(
                        DBProtectedSignal.builder()
                                .setKey(KEY_1)
                                .setValue(VALUE_1)
                                .setEvictionPriority(EvictionPriority.EVICT_SOONER),
                        existing1.toBuilder().setEvictionPriority(EvictionPriority.EVICT_SOONER),
                        existing2.toBuilder().setEvictionPriority(EvictionPriority.EVICT_SOONER));
        expectThatSignalBuilderListsAreEqual(expect, "toAdd", output.getToAdd(), expectedToAdd);
    }

    @Test
    public void testBadMaxValueThrowsException() throws Exception {
        JSONArray valuesJson = new JSONArray();
        valuesJson.put(BASE64_VALUE_1);
        valuesJson.put(BASE64_VALUE_2);

        JSONObject appendJson = new JSONObject();
        appendJson.put(VALUES, valuesJson);
        appendJson.put(MAX_SIGNALS, 1);
        appendJson.put(EVICTION_PRIORITY, EvictionPriority.EVICT_SOONER);

        JSONObject updatesJson = new JSONObject();
        updatesJson.put(BASE64_KEY_1, appendJson);

        assertThrows(
                IllegalArgumentException.class,
                () ->
                        mAppendV1.processUpdates(
                                updatesJson,
                                ImmutableMap.of(),
                                mUpdateSignalsProcessReportedLoggerMock));
    }
}

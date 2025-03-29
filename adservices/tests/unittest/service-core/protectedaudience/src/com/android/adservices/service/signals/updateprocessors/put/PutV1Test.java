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

package com.android.adservices.service.signals.updateprocessors.put;

import static com.android.adservices.service.signals.SignalsFixture.BASE64_KEY_1;
import static com.android.adservices.service.signals.SignalsFixture.BASE64_KEY_2;
import static com.android.adservices.service.signals.SignalsFixture.BASE64_VALUE_1;
import static com.android.adservices.service.signals.SignalsFixture.BASE64_VALUE_2;
import static com.android.adservices.service.signals.SignalsFixture.BB_KEY_1;
import static com.android.adservices.service.signals.SignalsFixture.BB_KEY_2;
import static com.android.adservices.service.signals.SignalsFixture.ID_1;
import static com.android.adservices.service.signals.SignalsFixture.ID_2;
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
import com.android.adservices.shared.testing.annotations.RequiresSdkLevelAtLeastT;

import com.google.common.collect.ImmutableList;
import com.google.common.collect.ImmutableMap;
import com.google.common.collect.ImmutableSet;

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
public class PutV1Test extends AdServicesMockitoTestCase {
    /*
     * Hardcoding names here since JSON keys are an external
     * contract and changing them should require test changes.
     */
    private static final String VALUE = "value";
    private static final String EVICTION_PRIORITY = "eviction_priority";
    @Mock private EvictionPriorityHandler mEvictionPriorityHandlerMock;

    private PutV1 mPutV1;

    @Before
    public void setup() {
        mPutV1 = new PutV1(mEvictionPriorityHandlerMock);
    }

    @Test
    public void testPutSingle() throws Exception {
        JSONObject putJson = new JSONObject();
        putJson.put(VALUE, BASE64_VALUE_1);
        putJson.put(EVICTION_PRIORITY, EvictionPriority.EVICT_SOONER);

        when(mEvictionPriorityHandlerMock.getEvictionPriority(putJson))
                .thenReturn(EvictionPriority.EVICT_SOONER);

        JSONObject updatesJson = new JSONObject();
        updatesJson.put(BASE64_KEY_1, putJson);

        UpdateOutput output = mPutV1.processUpdates(updatesJson, ImmutableMap.of());

        verify(mEvictionPriorityHandlerMock).getEvictionPriority(putJson);
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
    public void testPutMultipleKeys() throws Exception {
        JSONObject putJson1 = new JSONObject();
        putJson1.put(VALUE, BASE64_VALUE_1);
        putJson1.put(EVICTION_PRIORITY, EvictionPriority.EVICT_SOONER);

        when(mEvictionPriorityHandlerMock.getEvictionPriority(putJson1))
                .thenReturn(EvictionPriority.EVICT_SOONER);

        JSONObject putJson2 = new JSONObject();
        putJson2.put(VALUE, BASE64_VALUE_2);
        putJson2.put(EVICTION_PRIORITY, EvictionPriority.EVICT_LATER);

        when(mEvictionPriorityHandlerMock.getEvictionPriority(putJson2))
                .thenReturn(EvictionPriority.EVICT_LATER);

        JSONObject updatesJson = new JSONObject();
        updatesJson.put(BASE64_KEY_1, putJson1);
        updatesJson.put(BASE64_KEY_2, putJson2);

        UpdateOutput output = mPutV1.processUpdates(updatesJson, ImmutableMap.of());

        verify(mEvictionPriorityHandlerMock).getEvictionPriority(putJson1);
        verify(mEvictionPriorityHandlerMock).getEvictionPriority(putJson2);
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
                                .setKey(KEY_2)
                                .setValue(VALUE_2)
                                .setEvictionPriority(EvictionPriority.EVICT_LATER));
        expectThatSignalBuilderListsAreEqual(expect, "toAdd", output.getToAdd(), expectedToAdd);
    }

    @Test
    public void testOverwriteExisting() throws Exception {
        JSONObject putJson = new JSONObject();
        putJson.put(VALUE, BASE64_VALUE_1);
        putJson.put(EVICTION_PRIORITY, EvictionPriority.EVICT_SOONER);

        when(mEvictionPriorityHandlerMock.getEvictionPriority(putJson))
                .thenReturn(EvictionPriority.EVICT_SOONER);

        JSONObject updatesJson = new JSONObject();
        updatesJson.put(BASE64_KEY_1, putJson);

        DBProtectedSignal toOverwrite =
                createSignal(KEY_1, VALUE_1, ID_1, NOW.minus(Duration.ofDays(1)));
        Map<ByteBuffer, Set<DBProtectedSignal>> existingSignals =
                ImmutableMap.of(BB_KEY_1, ImmutableSet.of(toOverwrite));

        UpdateOutput output = mPutV1.processUpdates(updatesJson, existingSignals);

        verify(mEvictionPriorityHandlerMock).getEvictionPriority(putJson);
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
        JSONObject putJson = new JSONObject();
        putJson.put(VALUE, BASE64_VALUE_1);
        putJson.put(EVICTION_PRIORITY, EvictionPriority.EVICT_SOONER);

        when(mEvictionPriorityHandlerMock.getEvictionPriority(putJson))
                .thenReturn(EvictionPriority.EVICT_SOONER);

        JSONObject updatesJson = new JSONObject();
        updatesJson.put(BASE64_KEY_1, putJson);

        DBProtectedSignal toOverwrite1 =
                createSignal(KEY_1, VALUE_1, ID_1, NOW.minus(Duration.ofDays(1)));
        DBProtectedSignal toOverwrite2 =
                createSignal(KEY_1, VALUE_2, ID_2, NOW.minus(Duration.ofDays(1)));
        Map<ByteBuffer, Set<DBProtectedSignal>> existingSignals =
                ImmutableMap.of(BB_KEY_1, ImmutableSet.of(toOverwrite1, toOverwrite2));

        UpdateOutput output = mPutV1.processUpdates(updatesJson, existingSignals);

        verify(mEvictionPriorityHandlerMock).getEvictionPriority(putJson);
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
    public void testProcessUpdates_invalidUpdateType() throws Exception {
        JSONObject updatesJson = new JSONObject();
        updatesJson.put(BASE64_KEY_1, "Not a JSON object");

        assertThrows(
                "Expected exception",
                IllegalArgumentException.class,
                () -> mPutV1.processUpdates(updatesJson, ImmutableMap.of()));
    }
}

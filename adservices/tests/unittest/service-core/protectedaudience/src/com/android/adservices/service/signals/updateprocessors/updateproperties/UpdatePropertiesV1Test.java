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

package com.android.adservices.service.signals.updateprocessors.updateproperties;

import static com.android.adservices.service.signals.SignalsFixture.BASE64_KEY_1;
import static com.android.adservices.service.signals.SignalsFixture.BB_KEY_1;
import static com.android.adservices.service.signals.SignalsFixture.ID_1;
import static com.android.adservices.service.signals.SignalsFixture.KEY_1;
import static com.android.adservices.service.signals.SignalsFixture.NOW;
import static com.android.adservices.service.signals.SignalsFixture.VALUE_1;
import static com.android.adservices.service.signals.SignalsFixture.createSignal;
import static com.android.adservices.service.signals.SignalsFixture.expectThatSignalBuilderListsAreEqual;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.never;
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
public class UpdatePropertiesV1Test extends AdServicesMockitoTestCase {
    /*
     * Hardcoding names here since JSON keys are an external
     * contract and changing them should require test changes.
     */
    private static final String EVICTION_PRIORITY = "eviction_priority";

    @Mock private EvictionPriorityHandler mEvictionPriorityHandlerMock;
    @Mock private UpdateSignalsProcessReportedLogger mUpdateSignalsProcessReportedLoggerMock;

    private UpdatePropertiesV1 mUpdatePropertiesV1;

    @Before
    public void setup() {
        mUpdatePropertiesV1 = new UpdatePropertiesV1(mEvictionPriorityHandlerMock);
    }

    @Test
    public void testUpdateProperties_signalExists() throws Exception {
        JSONObject updatePropertiesJson = new JSONObject();
        updatePropertiesJson.put(EVICTION_PRIORITY, EvictionPriority.EVICT_SOONER);

        JSONObject updatesJson = new JSONObject();
        updatesJson.put(BASE64_KEY_1, updatePropertiesJson);

        when(mEvictionPriorityHandlerMock.getEvictionPriority(
                        BB_KEY_1, updatePropertiesJson, mUpdateSignalsProcessReportedLoggerMock))
                .thenReturn(EvictionPriority.EVICT_SOONER);

        DBProtectedSignal existingSignal =
                createSignal(KEY_1, VALUE_1, ID_1, NOW.minus(Duration.ofDays(1)));
        Map<ByteBuffer, Set<DBProtectedSignal>> existingSignals =
                ImmutableMap.of(BB_KEY_1, ImmutableSet.of(existingSignal));

        UpdateOutput output =
                mUpdatePropertiesV1.processUpdates(
                        updatesJson, existingSignals, mUpdateSignalsProcessReportedLoggerMock);

        verify(mEvictionPriorityHandlerMock)
                .getEvictionPriority(
                        BB_KEY_1, updatePropertiesJson, mUpdateSignalsProcessReportedLoggerMock);
        expect.withMessage("keysTouched").that(output.getKeysTouched()).containsExactly(BB_KEY_1);
        expect.withMessage("toRemove").that(output.getToRemove()).containsExactly(existingSignal);
        List<DBProtectedSignal.Builder> expectedToAdd =
                ImmutableList.of(
                        existingSignal.toBuilder()
                                .setEvictionPriority(EvictionPriority.EVICT_SOONER));
        expectThatSignalBuilderListsAreEqual(expect, "toAdd", output.getToAdd(), expectedToAdd);
    }

    @Test
    public void testUpdateProperties_signalDoesNotExist() throws Exception {
        JSONObject updatePropertiesJson = new JSONObject();
        updatePropertiesJson.put(EVICTION_PRIORITY, EvictionPriority.EVICT_SOONER);

        JSONObject updatesJson = new JSONObject();
        updatesJson.put(BASE64_KEY_1, updatePropertiesJson);

        UpdateOutput output =
                mUpdatePropertiesV1.processUpdates(
                        updatesJson, ImmutableMap.of(), mUpdateSignalsProcessReportedLoggerMock);

        verify(mEvictionPriorityHandlerMock, never()).getEvictionPriority(any(), any(), any());
        expect.withMessage("keysTouched").that(output.getKeysTouched()).containsExactly(BB_KEY_1);
        expect.withMessage("toRemove").that(output.getToRemove()).isEmpty();
        expect.withMessage("toAdd").that(output.getToAdd()).isEmpty();
    }
}

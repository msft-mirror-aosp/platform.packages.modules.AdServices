/*
 * Copyright (C) 2022 The Android Open Source Project
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

package com.android.adservices.service.measurement.util;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertNull;

import android.util.Pair;

import androidx.test.filters.SmallTest;

import com.android.adservices.service.measurement.EventSurfaceType;
import com.android.adservices.service.measurement.Source;
import com.android.adservices.service.measurement.SourceFixture;
import com.android.adservices.service.measurement.Trigger;
import com.android.adservices.service.measurement.TriggerFixture;

import org.junit.Test;

/** Unit tests for {@link DebugKey} */
@SmallTest
public class DebugKeyTest {

    private static final UnsignedLong SOURCE_DEBUG_KEY = new UnsignedLong(111111L);
    private static final UnsignedLong TRIGGER_DEBUG_KEY = new UnsignedLong(222222L);

    @Test
    public void testDebugKeys_adIdPermission_debugKeys() {
        Trigger trigger =
                TriggerFixture.getValidTriggerBuilder()
                        .setId("triggerId1")
                        .setStatus(Trigger.Status.PENDING)
                        .setTriggerTime(234324L)
                        .setAdIdPermission(true)
                        .setArDebugPermission(false)
                        .setDestinationType(EventSurfaceType.APP)
                        .setDebugKey(TRIGGER_DEBUG_KEY)
                        .build();
        Source source =
                SourceFixture.getValidSourceBuilder()
                        .setAdIdPermission(true)
                        .setArDebugPermission(false)
                        .setDebugKey(SOURCE_DEBUG_KEY)
                        .build();
        Pair<UnsignedLong, UnsignedLong> debugKeyPair = DebugKey.getDebugKeys(source, trigger);
        assertEquals(SOURCE_DEBUG_KEY, debugKeyPair.first);
        assertEquals(TRIGGER_DEBUG_KEY, debugKeyPair.second);
    }

    @Test
    public void testDebugKeys_no_adIdPermission_no_debugKeys() {
        Trigger trigger =
                TriggerFixture.getValidTriggerBuilder()
                        .setId("triggerId1")
                        .setStatus(Trigger.Status.PENDING)
                        .setTriggerTime(234324L)
                        .setAdIdPermission(false)
                        .setArDebugPermission(false)
                        .setDestinationType(EventSurfaceType.APP)
                        .setDebugKey(TRIGGER_DEBUG_KEY)
                        .build();
        Source source =
                SourceFixture.getValidSourceBuilder()
                        .setAdIdPermission(false)
                        .setArDebugPermission(false)
                        .setDebugKey(SOURCE_DEBUG_KEY)
                        .build();
        Pair<UnsignedLong, UnsignedLong> debugKeyPair = DebugKey.getDebugKeys(source, trigger);
        assertNull(debugKeyPair.first);
        assertNull(debugKeyPair.second);
    }

    @Test
    public void testWebDebugKeys_adIdPermission_arDebugPermission_debugKeys() {
        Trigger trigger =
                TriggerFixture.getValidTriggerBuilder()
                        .setId("triggerId1")
                        .setStatus(Trigger.Status.PENDING)
                        .setTriggerTime(234324L)
                        .setAdIdPermission(true)
                        .setArDebugPermission(true)
                        .setDestinationType(EventSurfaceType.WEB)
                        .setDebugKey(TRIGGER_DEBUG_KEY)
                        .build();
        Source source =
                SourceFixture.getValidSourceBuilder()
                        .setAdIdPermission(true)
                        .setArDebugPermission(true)
                        .setDebugKey(SOURCE_DEBUG_KEY)
                        .build();
        Pair<UnsignedLong, UnsignedLong> debugKeyPair = DebugKey.getDebugKeys(source, trigger);
        assertEquals(SOURCE_DEBUG_KEY, debugKeyPair.first);
        assertEquals(TRIGGER_DEBUG_KEY, debugKeyPair.second);
    }

    @Test
    public void testWebDebugKeys_arDebugPermission_sameRegistrant_debugKeys() {
        Trigger trigger =
                TriggerFixture.getValidTriggerBuilder()
                        .setId("triggerId1")
                        .setStatus(Trigger.Status.PENDING)
                        .setTriggerTime(234324L)
                        .setAdIdPermission(false)
                        .setArDebugPermission(true)
                        .setDestinationType(EventSurfaceType.WEB)
                        .setDebugKey(TRIGGER_DEBUG_KEY)
                        .build();
        Source source =
                SourceFixture.getValidSourceBuilder()
                        .setAdIdPermission(false)
                        .setArDebugPermission(true)
                        .setDebugKey(SOURCE_DEBUG_KEY)
                        .build();
        Pair<UnsignedLong, UnsignedLong> debugKeyPair = DebugKey.getDebugKeys(source, trigger);
        assertEquals(SOURCE_DEBUG_KEY, debugKeyPair.first);
        assertEquals(TRIGGER_DEBUG_KEY, debugKeyPair.second);
    }
}

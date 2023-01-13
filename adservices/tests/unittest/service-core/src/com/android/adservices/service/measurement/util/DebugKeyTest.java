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

import android.net.Uri;
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
    public static final String TRIGGER_ID = "triggerId1";
    public static final long TRIGGER_TIME = 234324L;
    public static final Uri REGISTRANT = Uri.parse("android-app://com.registrant.different");

    @Test
    public void testSourceAppTriggerApp_adIdPermission_debugKeysPresent() {
        Trigger trigger =
                TriggerFixture.getValidTriggerBuilder()
                        .setId(TRIGGER_ID)
                        .setStatus(Trigger.Status.PENDING)
                        .setTriggerTime(TRIGGER_TIME)
                        .setAdIdPermission(true)
                        .setDestinationType(EventSurfaceType.APP)
                        .setDebugKey(TRIGGER_DEBUG_KEY)
                        .build();
        Source source =
                SourceFixture.getValidSourceBuilder()
                        .setAdIdPermission(true)
                        .setDebugKey(SOURCE_DEBUG_KEY)
                        .setPublisherType(EventSurfaceType.APP)
                        .build();
        Pair<UnsignedLong, UnsignedLong> debugKeyPair = DebugKey.getDebugKeys(source, trigger);
        assertEquals(SOURCE_DEBUG_KEY, debugKeyPair.first);
        assertEquals(TRIGGER_DEBUG_KEY, debugKeyPair.second);
    }

    @Test
    public void testSourceAppTriggerApp_noAdIdPermission_debugKeysAbsent() {
        Trigger trigger =
                TriggerFixture.getValidTriggerBuilder()
                        .setId(TRIGGER_ID)
                        .setStatus(Trigger.Status.PENDING)
                        .setTriggerTime(TRIGGER_TIME)
                        .setAdIdPermission(false)
                        .setDestinationType(EventSurfaceType.APP)
                        .setDebugKey(TRIGGER_DEBUG_KEY)
                        .build();
        Source source =
                SourceFixture.getValidSourceBuilder()
                        .setAdIdPermission(false)
                        .setDebugKey(SOURCE_DEBUG_KEY)
                        .setPublisherType(EventSurfaceType.APP)
                        .build();
        Pair<UnsignedLong, UnsignedLong> debugKeyPair = DebugKey.getDebugKeys(source, trigger);
        assertNull(debugKeyPair.first);
        assertNull(debugKeyPair.second);
    }

    @Test
    public void testSourceAppTriggerApp_sourceAdId_sourceDebugKeyPresent() {
        Trigger trigger =
                TriggerFixture.getValidTriggerBuilder()
                        .setId(TRIGGER_ID)
                        .setStatus(Trigger.Status.PENDING)
                        .setTriggerTime(TRIGGER_TIME)
                        .setDestinationType(EventSurfaceType.APP)
                        .setDebugKey(TRIGGER_DEBUG_KEY)
                        .build();
        Source source =
                SourceFixture.getValidSourceBuilder()
                        .setAdIdPermission(true)
                        .setDebugKey(SOURCE_DEBUG_KEY)
                        .setPublisherType(EventSurfaceType.APP)
                        .build();
        Pair<UnsignedLong, UnsignedLong> debugKeyPair = DebugKey.getDebugKeys(source, trigger);
        assertEquals(SOURCE_DEBUG_KEY, debugKeyPair.first);
        assertNull(debugKeyPair.second);
    }

    @Test
    public void testSourceAppTriggerApp_triggerAdId_triggerDebugKeyPresent() {
        Trigger trigger =
                TriggerFixture.getValidTriggerBuilder()
                        .setId(TRIGGER_ID)
                        .setStatus(Trigger.Status.PENDING)
                        .setTriggerTime(TRIGGER_TIME)
                        .setAdIdPermission(true)
                        .setDestinationType(EventSurfaceType.APP)
                        .setDebugKey(TRIGGER_DEBUG_KEY)
                        .build();
        Source source =
                SourceFixture.getValidSourceBuilder()
                        .setDebugKey(SOURCE_DEBUG_KEY)
                        .setPublisherType(EventSurfaceType.APP)
                        .build();
        Pair<UnsignedLong, UnsignedLong> debugKeyPair = DebugKey.getDebugKeys(source, trigger);
        assertNull(debugKeyPair.first);
        assertEquals(TRIGGER_DEBUG_KEY, debugKeyPair.second);
    }

    @Test
    public void testSourceWebTriggerWeb_arDebugPermission_sameRegistrant_debugKeysPresent() {
        Trigger trigger =
                TriggerFixture.getValidTriggerBuilder()
                        .setId(TRIGGER_ID)
                        .setStatus(Trigger.Status.PENDING)
                        .setTriggerTime(TRIGGER_TIME)
                        .setArDebugPermission(true)
                        .setDestinationType(EventSurfaceType.WEB)
                        .setDebugKey(TRIGGER_DEBUG_KEY)
                        .build();
        Source source =
                SourceFixture.getValidSourceBuilder()
                        .setArDebugPermission(true)
                        .setPublisherType(EventSurfaceType.WEB)
                        .setDebugKey(SOURCE_DEBUG_KEY)
                        .build();
        Pair<UnsignedLong, UnsignedLong> debugKeyPair = DebugKey.getDebugKeys(source, trigger);
        assertEquals(SOURCE_DEBUG_KEY, debugKeyPair.first);
        assertEquals(TRIGGER_DEBUG_KEY, debugKeyPair.second);
    }

    @Test
    public void testSourceWebTriggerWeb_noArDebugPermission_debugKeysAbsent() {
        Trigger trigger =
                TriggerFixture.getValidTriggerBuilder()
                        .setId(TRIGGER_ID)
                        .setStatus(Trigger.Status.PENDING)
                        .setTriggerTime(TRIGGER_TIME)
                        .setArDebugPermission(false)
                        .setDestinationType(EventSurfaceType.WEB)
                        .setDebugKey(TRIGGER_DEBUG_KEY)
                        .build();
        Source source =
                SourceFixture.getValidSourceBuilder()
                        .setArDebugPermission(false)
                        .setPublisherType(EventSurfaceType.WEB)
                        .setDebugKey(SOURCE_DEBUG_KEY)
                        .build();
        Pair<UnsignedLong, UnsignedLong> debugKeyPair = DebugKey.getDebugKeys(source, trigger);
        assertNull(debugKeyPair.first);
        assertNull(debugKeyPair.second);
    }

    @Test
    public void testSourceWebTriggerWeb_arDebugPermission_differentRegistrant_debugKeysAbsent() {
        Trigger trigger =
                TriggerFixture.getValidTriggerBuilder()
                        .setId(TRIGGER_ID)
                        .setStatus(Trigger.Status.PENDING)
                        .setTriggerTime(TRIGGER_TIME)
                        .setAdIdPermission(true)
                        .setArDebugPermission(true)
                        .setDestinationType(EventSurfaceType.WEB)
                        .setDebugKey(TRIGGER_DEBUG_KEY)
                        .build();
        Source source =
                SourceFixture.getValidSourceBuilder()
                        .setRegistrant(REGISTRANT)
                        .setArDebugPermission(true)
                        .setPublisherType(EventSurfaceType.WEB)
                        .setDebugKey(SOURCE_DEBUG_KEY)
                        .build();
        Pair<UnsignedLong, UnsignedLong> debugKeyPair = DebugKey.getDebugKeys(source, trigger);
        assertNull(debugKeyPair.first);
        assertNull(debugKeyPair.second);
    }

    @Test
    public void testSourceWebTriggerWeb_sourceArDebugSameRegistrant_sourceDebugKeysPresent() {
        Trigger trigger =
                TriggerFixture.getValidTriggerBuilder()
                        .setId(TRIGGER_ID)
                        .setStatus(Trigger.Status.PENDING)
                        .setTriggerTime(TRIGGER_TIME)
                        .setDestinationType(EventSurfaceType.WEB)
                        .setDebugKey(TRIGGER_DEBUG_KEY)
                        .build();
        Source source =
                SourceFixture.getValidSourceBuilder()
                        .setArDebugPermission(true)
                        .setPublisherType(EventSurfaceType.WEB)
                        .setDebugKey(SOURCE_DEBUG_KEY)
                        .build();
        Pair<UnsignedLong, UnsignedLong> debugKeyPair = DebugKey.getDebugKeys(source, trigger);
        assertEquals(SOURCE_DEBUG_KEY, debugKeyPair.first);
        assertNull(debugKeyPair.second);
    }

    @Test
    public void testSourceWebTriggerWeb_triggerArDebugSameRegistrant_triggerDebugKeysPresent() {
        Trigger trigger =
                TriggerFixture.getValidTriggerBuilder()
                        .setId(TRIGGER_ID)
                        .setStatus(Trigger.Status.PENDING)
                        .setTriggerTime(TRIGGER_TIME)
                        .setArDebugPermission(true)
                        .setDestinationType(EventSurfaceType.WEB)
                        .setDebugKey(TRIGGER_DEBUG_KEY)
                        .build();
        Source source =
                SourceFixture.getValidSourceBuilder()
                        .setPublisherType(EventSurfaceType.WEB)
                        .setDebugKey(SOURCE_DEBUG_KEY)
                        .build();
        Pair<UnsignedLong, UnsignedLong> debugKeyPair = DebugKey.getDebugKeys(source, trigger);
        assertNull(debugKeyPair.first);
        assertEquals(TRIGGER_DEBUG_KEY, debugKeyPair.second);
    }

    @Test
    public void testSourceAppTriggerWeb_debugKeysAbsent() {
        Trigger trigger =
                TriggerFixture.getValidTriggerBuilder()
                        .setId(TRIGGER_ID)
                        .setStatus(Trigger.Status.PENDING)
                        .setTriggerTime(TRIGGER_TIME)
                        .setArDebugPermission(true)
                        .setDestinationType(EventSurfaceType.WEB)
                        .setDebugKey(TRIGGER_DEBUG_KEY)
                        .build();
        Source source =
                SourceFixture.getValidSourceBuilder()
                        .setAdIdPermission(true)
                        .setDebugKey(SOURCE_DEBUG_KEY)
                        .setPublisherType(EventSurfaceType.APP)
                        .build();
        Pair<UnsignedLong, UnsignedLong> debugKeyPair = DebugKey.getDebugKeys(source, trigger);
        assertNull(debugKeyPair.first);
        assertNull(debugKeyPair.second);
    }

    @Test
    public void testSourceWebTriggerApp_debugKeysAbsent() {
        Trigger trigger =
                TriggerFixture.getValidTriggerBuilder()
                        .setId(TRIGGER_ID)
                        .setStatus(Trigger.Status.PENDING)
                        .setTriggerTime(TRIGGER_TIME)
                        .setAdIdPermission(true)
                        .setDestinationType(EventSurfaceType.APP)
                        .setDebugKey(TRIGGER_DEBUG_KEY)
                        .build();
        Source source =
                SourceFixture.getValidSourceBuilder()
                        .setArDebugPermission(true)
                        .setDebugKey(SOURCE_DEBUG_KEY)
                        .setPublisherType(EventSurfaceType.WEB)
                        .build();
        Pair<UnsignedLong, UnsignedLong> debugKeyPair = DebugKey.getDebugKeys(source, trigger);
        assertNull(debugKeyPair.first);
        assertNull(debugKeyPair.second);
    }
}


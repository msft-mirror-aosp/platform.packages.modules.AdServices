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
package com.android.adservices.service.measurement;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertNull;

import android.net.Uri;

import androidx.test.filters.SmallTest;

import org.junit.Test;

/** Unit tests for {@link EventReport} */
@SmallTest
public final class EventReportTest {

    private EventReport createExample() {
        return new EventReport.Builder()
                .setId("1")
                .setSourceId(21)
                .setReportTo(Uri.parse("http://foo.com"))
                .setAttributionDestination(Uri.parse("http://bar.com"))
                .setTriggerTime(1000L)
                .setTriggerData(8L)
                .setTriggerPriority(2L)
                .setTriggerDedupKey(3L)
                .setReportTime(2000L)
                .setStatus(EventReport.Status.PENDING)
                .build();
    }

    @Test
    public void testCreation() throws Exception {
        EventReport eventReport = createExample();
        assertEquals("1", eventReport.getId());
        assertEquals(21, eventReport.getSourceId());
        assertEquals("http://foo.com", eventReport.getReportTo().toString());
        assertEquals("http://bar.com", eventReport.getAttributionDestination().toString());
        assertEquals(1000L, eventReport.getTriggerTime());
        assertEquals(8L, eventReport.getTriggerData());
        assertEquals(2L, eventReport.getTriggerPriority());
        assertEquals(Long.valueOf(3), eventReport.getTriggerDedupKey());
        assertEquals(2000L, eventReport.getReportTime());
        assertEquals(EventReport.Status.PENDING, eventReport.getStatus());
    }

    @Test
    public void testDefaults() throws Exception {
        EventReport eventReport = new EventReport.Builder().build();
        assertNull(eventReport.getId());
        assertEquals(0L, eventReport.getSourceId());
        assertNull(eventReport.getReportTo());
        assertNull(eventReport.getAttributionDestination());
        assertEquals(0L, eventReport.getTriggerTime());
        assertEquals(0L, eventReport.getTriggerData());
        assertEquals(0L, eventReport.getTriggerPriority());
        assertNull(eventReport.getTriggerDedupKey());
        assertEquals(0L, eventReport.getReportTime());
        assertEquals(EventReport.Status.PENDING, eventReport.getStatus());
    }
}

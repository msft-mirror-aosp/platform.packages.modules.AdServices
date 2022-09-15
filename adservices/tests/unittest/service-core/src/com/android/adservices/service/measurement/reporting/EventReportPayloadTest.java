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

package com.android.adservices.service.measurement.reporting;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertNull;

import org.json.JSONException;
import org.json.JSONObject;
import org.junit.Test;

public class EventReportPayloadTest {

    private static final String ATTRIBUTION_DESTINATION = "https://toasters.example";
    private static final Long SOURCE_EVENT_ID = 12345L;
    private static final Long TRIGGER_DATA = 2L;
    private static final String REPORT_ID = "678";
    private static final String SOURCE_TYPE = "event";
    private static final double RANDOMIZED_TRIGGER_RATE = 0.0024;
    private static final Long SOURCE_DEBUG_KEY = 3894783L;
    private static final Long TRIGGER_DEBUG_KEY = 2387222L;

    private static EventReportPayload createEventReportPayload(Long sourceDebugKey,
            Long triggerDebugKey) {
        return new EventReportPayload.Builder()
                .setAttributionDestination(ATTRIBUTION_DESTINATION)
                .setSourceEventId(SOURCE_EVENT_ID)
                .setTriggerData(TRIGGER_DATA)
                .setReportId(REPORT_ID)
                .setSourceType(SOURCE_TYPE)
                .setRandomizedTriggerRate(RANDOMIZED_TRIGGER_RATE)
                .setSourceDebugKey(sourceDebugKey)
                .setTriggerDebugKey(triggerDebugKey)
                .build();
    }

    @Test
    public void testEventPayloadJsonSerialization() throws JSONException {
        EventReportPayload eventReport =
                createEventReportPayload(SOURCE_DEBUG_KEY, TRIGGER_DEBUG_KEY);
        JSONObject eventPayloadReportJson = eventReport.toJson();

        assertEquals(ATTRIBUTION_DESTINATION,
                eventPayloadReportJson.get("attribution_destination"));
        assertEquals(Long.toUnsignedString(SOURCE_EVENT_ID),
                eventPayloadReportJson.get("source_event_id"));
        assertEquals(Long.toUnsignedString(TRIGGER_DATA),
                eventPayloadReportJson.get("trigger_data"));
        assertEquals(REPORT_ID, eventPayloadReportJson.get("report_id"));
        assertEquals(SOURCE_TYPE, eventPayloadReportJson.get("source_type"));
        assertEquals(RANDOMIZED_TRIGGER_RATE,
                eventPayloadReportJson.get("randomized_trigger_rate"));
        assertEquals(Long.toUnsignedString(SOURCE_DEBUG_KEY),
                eventPayloadReportJson.get("source_debug_key"));
        assertEquals(Long.toUnsignedString(TRIGGER_DEBUG_KEY),
                eventPayloadReportJson.get("trigger_debug_key"));
    }

    @Test
    public void testEventPayloadJsonSerializationWithNullDebugKeys() throws JSONException {
        EventReportPayload eventReport = createEventReportPayload(null, null);
        JSONObject eventPayloadReportJson = eventReport.toJson();

        assertEquals(
                ATTRIBUTION_DESTINATION, eventPayloadReportJson.get("attribution_destination"));
        assertEquals(Long.toUnsignedString(SOURCE_EVENT_ID),
                eventPayloadReportJson.get("source_event_id"));
        assertEquals(Long.toUnsignedString(TRIGGER_DATA),
                eventPayloadReportJson.get("trigger_data"));
        assertEquals(REPORT_ID, eventPayloadReportJson.get("report_id"));
        assertEquals(SOURCE_TYPE, eventPayloadReportJson.get("source_type"));
        assertEquals(
                RANDOMIZED_TRIGGER_RATE, eventPayloadReportJson.get("randomized_trigger_rate"));
        assertNull(eventPayloadReportJson.opt("source_debug_key"));
        assertNull(eventPayloadReportJson.opt("trigger_debug_key"));
    }

    @Test
    public void testEventPayloadJsonSerializationWithSingleTriggerDebugKeys() throws JSONException {
        EventReportPayload eventReport = createEventReportPayload(null, TRIGGER_DEBUG_KEY);
        JSONObject eventPayloadReportJson = eventReport.toJson();

        assertEquals(
                ATTRIBUTION_DESTINATION, eventPayloadReportJson.get("attribution_destination"));
        assertEquals(Long.toUnsignedString(SOURCE_EVENT_ID),
                eventPayloadReportJson.get("source_event_id"));
        assertEquals(Long.toUnsignedString(TRIGGER_DATA),
                eventPayloadReportJson.get("trigger_data"));
        assertEquals(REPORT_ID, eventPayloadReportJson.get("report_id"));
        assertEquals(SOURCE_TYPE, eventPayloadReportJson.get("source_type"));
        assertEquals(
                RANDOMIZED_TRIGGER_RATE, eventPayloadReportJson.get("randomized_trigger_rate"));
        assertNull(eventPayloadReportJson.opt("source_debug_key"));
        assertEquals(Long.toUnsignedString(TRIGGER_DEBUG_KEY),
                eventPayloadReportJson.get("trigger_debug_key"));
    }

    @Test
    public void testEventPayloadJsonSerialization_debugKeysSourceEventIdAndTriggerDataUse64thBit()
            throws JSONException {
        String unsigned64BitIntString = "18446744073709551615";
        Long signed64BitInt = -1L;
        EventReportPayload eventReport = new EventReportPayload.Builder()
                .setAttributionDestination(ATTRIBUTION_DESTINATION)
                .setSourceEventId(signed64BitInt)
                .setTriggerData(signed64BitInt)
                .setReportId(REPORT_ID)
                .setSourceType(SOURCE_TYPE)
                .setRandomizedTriggerRate(RANDOMIZED_TRIGGER_RATE)
                .setSourceDebugKey(signed64BitInt)
                .setTriggerDebugKey(signed64BitInt)
                .build();
        JSONObject eventPayloadReportJson = eventReport.toJson();

        assertEquals(
                ATTRIBUTION_DESTINATION, eventPayloadReportJson.get("attribution_destination"));
        assertEquals(unsigned64BitIntString, eventPayloadReportJson.get("source_event_id"));
        assertEquals(unsigned64BitIntString, eventPayloadReportJson.get("trigger_data"));
        assertEquals(REPORT_ID, eventPayloadReportJson.get("report_id"));
        assertEquals(SOURCE_TYPE, eventPayloadReportJson.get("source_type"));
        assertEquals(
                RANDOMIZED_TRIGGER_RATE, eventPayloadReportJson.get("randomized_trigger_rate"));
        assertEquals(unsigned64BitIntString, eventPayloadReportJson.opt("source_debug_key"));
        assertEquals(unsigned64BitIntString, eventPayloadReportJson.get("trigger_debug_key"));
    }

    @Test
    public void testEventPayloadJsonSerializationWithSingleSourceDebugKeys() throws JSONException {
        EventReportPayload eventReport = createEventReportPayload(SOURCE_DEBUG_KEY, null);
        JSONObject eventPayloadReportJson = eventReport.toJson();

        assertEquals(
                ATTRIBUTION_DESTINATION, eventPayloadReportJson.get("attribution_destination"));
        assertEquals(Long.toUnsignedString(SOURCE_EVENT_ID),
                eventPayloadReportJson.get("source_event_id"));
        assertEquals(Long.toUnsignedString(TRIGGER_DATA),
                eventPayloadReportJson.get("trigger_data"));
        assertEquals(REPORT_ID, eventPayloadReportJson.get("report_id"));
        assertEquals(SOURCE_TYPE, eventPayloadReportJson.get("source_type"));
        assertEquals(
                RANDOMIZED_TRIGGER_RATE, eventPayloadReportJson.get("randomized_trigger_rate"));
        assertNull(eventPayloadReportJson.opt("trigger_debug_key"));
        assertEquals(Long.toUnsignedString(SOURCE_DEBUG_KEY),
                eventPayloadReportJson.get("source_debug_key"));
    }
}

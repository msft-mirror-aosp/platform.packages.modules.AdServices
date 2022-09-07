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
    private static final String SOURCE_EVENT_ID = "12345";
    private static final String TRIGGER_DATA = "2";
    private static final String REPORT_ID = "678";
    private static final String SOURCE_TYPE = "event";
    private static final double RANDOMIZED_TRIGGER_RATE = 0.0024;
    private static final Long SOURCE_DEBUG_KEY = 3894783L;
    private static final Long TRIGGER_DEBUG_KEY = 2387222L;

    private EventReportPayload createEventReportPayloadExample1() {
        return new EventReportPayload.Builder()
                .setAttributionDestination(ATTRIBUTION_DESTINATION)
                .setSourceEventId(SOURCE_EVENT_ID)
                .setTriggerData(TRIGGER_DATA)
                .setReportId(REPORT_ID)
                .setSourceType(SOURCE_TYPE)
                .setRandomizedTriggerRate(RANDOMIZED_TRIGGER_RATE)
                .setSourceDebugKey(SOURCE_DEBUG_KEY)
                .setTriggerDebugKey(TRIGGER_DEBUG_KEY)
                .build();
    }

    private EventReportPayload createEventReportPayloadWithNullDebugKeys() {
        return new EventReportPayload.Builder()
                .setAttributionDestination(ATTRIBUTION_DESTINATION)
                .setSourceEventId(SOURCE_EVENT_ID)
                .setTriggerData(TRIGGER_DATA)
                .setReportId(REPORT_ID)
                .setSourceType(SOURCE_TYPE)
                .setRandomizedTriggerRate(RANDOMIZED_TRIGGER_RATE)
                .setSourceDebugKey(null)
                .setTriggerDebugKey(null)
                .build();
    }

    private EventReportPayload createEventReportPayloadWithSingleTriggerDebugKeys() {
        return new EventReportPayload.Builder()
                .setAttributionDestination(ATTRIBUTION_DESTINATION)
                .setSourceEventId(SOURCE_EVENT_ID)
                .setTriggerData(TRIGGER_DATA)
                .setReportId(REPORT_ID)
                .setSourceType(SOURCE_TYPE)
                .setRandomizedTriggerRate(RANDOMIZED_TRIGGER_RATE)
                .setTriggerDebugKey(TRIGGER_DEBUG_KEY)
                .build();
    }

    private EventReportPayload createEventReportPayloadWithSingleSourceDebugKeys() {
        return new EventReportPayload.Builder()
                .setAttributionDestination(ATTRIBUTION_DESTINATION)
                .setSourceEventId(SOURCE_EVENT_ID)
                .setTriggerData(TRIGGER_DATA)
                .setReportId(REPORT_ID)
                .setSourceType(SOURCE_TYPE)
                .setRandomizedTriggerRate(RANDOMIZED_TRIGGER_RATE)
                .setSourceDebugKey(SOURCE_DEBUG_KEY)
                .build();
    }

    @Test
    public void testEventPayloadJsonSerialization() throws JSONException {
        EventReportPayload eventReport = createEventReportPayloadExample1();
        JSONObject eventPayloadReportJson = eventReport.toJson();

        assertEquals(ATTRIBUTION_DESTINATION,
                eventPayloadReportJson.get("attribution_destination"));
        assertEquals(SOURCE_EVENT_ID, eventPayloadReportJson.get("source_event_id"));
        assertEquals(TRIGGER_DATA, eventPayloadReportJson.get("trigger_data"));
        assertEquals(REPORT_ID, eventPayloadReportJson.get("report_id"));
        assertEquals(SOURCE_TYPE, eventPayloadReportJson.get("source_type"));
        assertEquals(RANDOMIZED_TRIGGER_RATE,
                eventPayloadReportJson.get("randomized_trigger_rate"));
        assertEquals(SOURCE_DEBUG_KEY, eventPayloadReportJson.get("source_debug_key"));
        assertEquals(TRIGGER_DEBUG_KEY, eventPayloadReportJson.get("trigger_debug_key"));
    }

    @Test
    public void testEventPayloadJsonSerializationWithNullDebugKeys() throws JSONException {
        EventReportPayload eventReport = createEventReportPayloadWithNullDebugKeys();
        JSONObject eventPayloadReportJson = eventReport.toJson();

        assertEquals(
                ATTRIBUTION_DESTINATION, eventPayloadReportJson.get("attribution_destination"));
        assertEquals(SOURCE_EVENT_ID, eventPayloadReportJson.get("source_event_id"));
        assertEquals(TRIGGER_DATA, eventPayloadReportJson.get("trigger_data"));
        assertEquals(REPORT_ID, eventPayloadReportJson.get("report_id"));
        assertEquals(SOURCE_TYPE, eventPayloadReportJson.get("source_type"));
        assertEquals(
                RANDOMIZED_TRIGGER_RATE, eventPayloadReportJson.get("randomized_trigger_rate"));
        assertNull(eventPayloadReportJson.opt("source_debug_key"));
        assertNull(eventPayloadReportJson.opt("trigger_debug_key"));
    }

    @Test
    public void testEventPayloadJsonSerializationWithSingleTriggerDebugKeys() throws JSONException {
        EventReportPayload eventReport = createEventReportPayloadWithSingleTriggerDebugKeys();
        JSONObject eventPayloadReportJson = eventReport.toJson();

        assertEquals(
                ATTRIBUTION_DESTINATION, eventPayloadReportJson.get("attribution_destination"));
        assertEquals(SOURCE_EVENT_ID, eventPayloadReportJson.get("source_event_id"));
        assertEquals(TRIGGER_DATA, eventPayloadReportJson.get("trigger_data"));
        assertEquals(REPORT_ID, eventPayloadReportJson.get("report_id"));
        assertEquals(SOURCE_TYPE, eventPayloadReportJson.get("source_type"));
        assertEquals(
                RANDOMIZED_TRIGGER_RATE, eventPayloadReportJson.get("randomized_trigger_rate"));
        assertNull(eventPayloadReportJson.opt("source_debug_key"));
        assertEquals(TRIGGER_DEBUG_KEY, eventPayloadReportJson.get("trigger_debug_key"));
    }

    @Test
    public void testEventPayloadJsonSerializationWithSingleSourceDebugKeys() throws JSONException {
        EventReportPayload eventReport = createEventReportPayloadWithSingleSourceDebugKeys();
        JSONObject eventPayloadReportJson = eventReport.toJson();

        assertEquals(
                ATTRIBUTION_DESTINATION, eventPayloadReportJson.get("attribution_destination"));
        assertEquals(SOURCE_EVENT_ID, eventPayloadReportJson.get("source_event_id"));
        assertEquals(TRIGGER_DATA, eventPayloadReportJson.get("trigger_data"));
        assertEquals(REPORT_ID, eventPayloadReportJson.get("report_id"));
        assertEquals(SOURCE_TYPE, eventPayloadReportJson.get("source_type"));
        assertEquals(
                RANDOMIZED_TRIGGER_RATE, eventPayloadReportJson.get("randomized_trigger_rate"));
        assertNull(eventPayloadReportJson.opt("trigger_debug_key"));
        assertEquals(SOURCE_DEBUG_KEY, eventPayloadReportJson.get("source_debug_key"));
    }
}

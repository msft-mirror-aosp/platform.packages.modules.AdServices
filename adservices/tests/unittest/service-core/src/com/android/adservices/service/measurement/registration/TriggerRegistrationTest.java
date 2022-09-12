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
package com.android.adservices.service.measurement.registration;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertNull;

import android.net.Uri;

import androidx.test.filters.SmallTest;

import org.junit.Test;


/**
 * Unit tests for {@link TriggerRegistration}
 */
@SmallTest
public final class TriggerRegistrationTest {
    private static final Uri TOP_ORIGIN = Uri.parse("https://foo.com");
    private static final String ENROLLMENT_ID = "enrollment-id";
    private static final String TOP_LEVEL_FILTERS_JSON_STRING =
            "{\n"
                    + "  \"key_1\": [\"value_1\", \"value_2\"],\n"
                    + "  \"key_2\": [\"value_1\", \"value_2\"]\n"
                    + "}\n";
    private static final String EVENT_TRIGGERS =
            "[\n"
                    + "{\n"
                    + "  \"trigger_data\": \"1\",\n"
                    + "  \"priority\": \"345678\",\n"
                    + "  \"deduplication_key\": \"2345678\",\n"
                    + "  \"filters\": {\n"
                    + "    \"source_type\": [\"navigation\"],\n"
                    + "    \"key_1\": [\"value_1\"] \n"
                    + "   }\n"
                    + "}"
                    + "]\n";

    private static final Long DEBUG_KEY = 23478951L;

    private static final String AGGREGATE_TRIGGER_DATA =
            "[{\"key_piece\":\"0x400\",\"source_keys\":[\"campaignCounts\"],"
                    + "\"not_filters\":{\"product\":[\"1\"]}},"
                    + "{\"key_piece\":\"0xA80\",\"source_keys\":[\"geoValue\"]}]";

    private TriggerRegistration createExampleResponse() {
        return new TriggerRegistration.Builder()
                .setTopOrigin(TOP_ORIGIN)
                .setEnrollmentId(ENROLLMENT_ID)
                .setEventTriggers(EVENT_TRIGGERS)
                .setAggregateTriggerData(AGGREGATE_TRIGGER_DATA)
                .setAggregateValues("{\"campaignCounts\":32768,\"geoValue\":1644}")
                .setFilters(TOP_LEVEL_FILTERS_JSON_STRING)
                .setDebugKey(DEBUG_KEY)
                .build();
    }

    void verifyExampleResponse(TriggerRegistration triggerRegistration) {
        assertEquals("https://foo.com", triggerRegistration.getTopOrigin().toString());
        assertEquals(ENROLLMENT_ID, triggerRegistration.getEnrollmentId());
        assertEquals(EVENT_TRIGGERS, triggerRegistration.getEventTriggers());
        assertEquals(AGGREGATE_TRIGGER_DATA, triggerRegistration.getAggregateTriggerData());
        assertEquals(
                "{\"campaignCounts\":32768,\"geoValue\":1644}",
                triggerRegistration.getAggregateValues());
        assertEquals(TOP_LEVEL_FILTERS_JSON_STRING, triggerRegistration.getFilters());
        assertEquals(DEBUG_KEY, triggerRegistration.getDebugKey());
    }

    @Test
    public void testCreation() throws Exception {
        verifyExampleResponse(createExampleResponse());
    }

    @Test
    public void testDefaults() throws Exception {
        TriggerRegistration response =
                new TriggerRegistration.Builder()
                        .setTopOrigin(TOP_ORIGIN)
                        .setEnrollmentId(ENROLLMENT_ID)
                        .build();
        assertEquals(TOP_ORIGIN, response.getTopOrigin());
        assertEquals(ENROLLMENT_ID, response.getEnrollmentId());
        assertNull(response.getEventTriggers());
        assertNull(response.getAggregateTriggerData());
        assertNull(response.getAggregateValues());
        assertNull(response.getFilters());
    }

    @Test
    public void equals_success() {
        assertEquals(createExampleResponse(), createExampleResponse());
    }
}

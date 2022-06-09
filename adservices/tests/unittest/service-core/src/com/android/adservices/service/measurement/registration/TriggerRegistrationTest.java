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
    private static final String TOP_LEVEL_FILTERS_JSON_STRING =
            "{\n"
                    + "  \"key_1\": [\"value_1\", \"value_2\"],\n"
                    + "  \"key_2\": [\"value_1\", \"value_2\"]\n"
                    + "}\n";

    private TriggerRegistration createExampleResponse() {
        return new TriggerRegistration.Builder()
                .setTopOrigin(Uri.parse("https://foo.com"))
                .setReportingOrigin(Uri.parse("https://bar.com"))
                .setTriggerData(1)
                .setTriggerPriority(345678)
                .setDeduplicationKey(2345678)
                .setAggregateTriggerData(
                        "[{\"key_piece\":\"0x400\",\"source_keys\":[\"campaignCounts\"],"
                                + "\"not_filters\":{\"product\":[\"1\"]}},"
                                + "{\"key_piece\":\"0xA80\",\"source_keys\":[\"geoValue\"]}]")
                .setAggregateValues("{\"campaignCounts\":32768,\"geoValue\":1644}")
                .setFilters(TOP_LEVEL_FILTERS_JSON_STRING)
                .build();
    }

    void verifyExampleResponse(TriggerRegistration response) {
        assertEquals("https://foo.com", response.getTopOrigin().toString());
        assertEquals("https://bar.com", response.getReportingOrigin().toString());
        assertEquals(1, response.getTriggerData());
        assertEquals(345678, response.getTriggerPriority());
        assertEquals(2345678, response.getDeduplicationKey().longValue());
        assertEquals("[{\"key_piece\":\"0x400\",\"source_keys\":[\"campaignCounts\"],"
                + "\"not_filters\":{\"product\":[\"1\"]}},"
                + "{\"key_piece\":\"0xA80\",\"source_keys\":[\"geoValue\"]}]",
                response.getAggregateTriggerData());
        assertEquals("{\"campaignCounts\":32768,\"geoValue\":1644}",
                response.getAggregateValues());
        assertEquals(TOP_LEVEL_FILTERS_JSON_STRING, response.getFilters());
    }

    @Test
    public void testCreation() throws Exception {
        verifyExampleResponse(createExampleResponse());
    }

    @Test
    public void testDefaults() throws Exception {
        TriggerRegistration response = new TriggerRegistration.Builder().build();
        assertEquals("", response.getTopOrigin().toString());
        assertEquals("", response.getReportingOrigin().toString());
        assertEquals(0, response.getTriggerData());
        assertEquals(0, response.getTriggerPriority());
        assertNull(response.getDeduplicationKey());
        assertNull(response.getAggregateTriggerData());
        assertNull(response.getAggregateValues());
        assertNull(response.getFilters());
    }
}

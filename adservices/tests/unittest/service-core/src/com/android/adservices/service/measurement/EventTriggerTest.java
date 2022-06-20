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

import static org.junit.Assert.*;

import com.android.adservices.service.measurement.aggregation.AggregateFilterData;

import org.json.JSONException;
import org.json.JSONObject;
import org.junit.Test;

public class EventTriggerTest {
    private static JSONObject sFilterData1;
    private static JSONObject sFilterData2;
    private static JSONObject sNotFilterData1;
    private static JSONObject sNotFilterData2;

    static {
        try {
            sFilterData1 =
                    new JSONObject(
                            "{\n"
                                    + "    \"source_type\": [\"navigation\"],\n"
                                    + "    \"key_1\": [\"value_1\"] \n"
                                    + "   }\n");
            sFilterData2 =
                    new JSONObject(
                            "{\n"
                                    + "    \"source_type\": [\"EVENT\"],\n"
                                    + "    \"key_1\": [\"value_1\"] \n"
                                    + "   }\n");
            sNotFilterData1 =
                    new JSONObject(
                            "{\n"
                                    + "    \"not_source_type\": [\"EVENT\"],\n"
                                    + "    \"not_key_1\": [\"value_1\"] \n"
                                    + "   }\n");
            sNotFilterData2 =
                    new JSONObject(
                            "{\n"
                                    + "    \"not_source_type\": [\"navigation\"],\n"
                                    + "    \"not_key_1\": [\"value_1\"] \n"
                                    + "   }\n");
        } catch (JSONException e) {
            fail();
        }
    }

    @Test
    public void testDefaults() throws Exception {
        EventTrigger eventTrigger = new EventTrigger.Builder().build();
        assertEquals(0L, eventTrigger.getTriggerPriority());
        assertNull(eventTrigger.getTriggerData());
        assertNull(eventTrigger.getDedupKey());
        assertFalse(eventTrigger.getFilterData().isPresent());
        assertFalse(eventTrigger.getNotFilterData().isPresent());
    }

    @Test
    public void test_equals_pass() throws Exception {
        EventTrigger eventTrigger1 =
                new EventTrigger.Builder()
                        .setTriggerPriority(1L)
                        .setTriggerData(101L)
                        .setDedupKey(1001L)
                        .setFilter(
                                new AggregateFilterData.Builder()
                                        .buildAggregateFilterData(sFilterData1)
                                        .build())
                        .setNotFilter(
                                new AggregateFilterData.Builder()
                                        .buildAggregateFilterData(sNotFilterData1)
                                        .build())
                        .build();
        EventTrigger eventTrigger2 =
                new EventTrigger.Builder()
                        .setTriggerPriority(1L)
                        .setTriggerData(101L)
                        .setDedupKey(1001L)
                        .setFilter(
                                new AggregateFilterData.Builder()
                                        .buildAggregateFilterData(sFilterData1)
                                        .build())
                        .setNotFilter(
                                new AggregateFilterData.Builder()
                                        .buildAggregateFilterData(sNotFilterData1)
                                        .build())
                        .build();

        assertEquals(eventTrigger1, eventTrigger2);
    }

    @Test
    public void test_equals_fail() throws Exception {
        assertNotEquals(
                new EventTrigger.Builder().setTriggerPriority(1L).build(),
                new EventTrigger.Builder().setTriggerPriority(2L).build());
        assertNotEquals(
                new EventTrigger.Builder().setTriggerData(1L).build(),
                new EventTrigger.Builder().setTriggerData(2L).build());
        assertNotEquals(
                new EventTrigger.Builder().setDedupKey(1L).build(),
                new EventTrigger.Builder().setDedupKey(2L).build());
        assertNotEquals(
                new EventTrigger.Builder()
                        .setFilter(
                                new AggregateFilterData.Builder()
                                        .buildAggregateFilterData(sFilterData1)
                                        .build())
                        .build(),
                new EventTrigger.Builder()
                        .setFilter(
                                new AggregateFilterData.Builder()
                                        .buildAggregateFilterData(sFilterData2)
                                        .build())
                        .build());
        assertNotEquals(
                new EventTrigger.Builder()
                        .setNotFilter(
                                new AggregateFilterData.Builder()
                                        .buildAggregateFilterData(sNotFilterData1)
                                        .build())
                        .build(),
                new EventTrigger.Builder()
                        .setNotFilter(
                                new AggregateFilterData.Builder()
                                        .buildAggregateFilterData(sNotFilterData2)
                                        .build())
                        .build());
    }
}

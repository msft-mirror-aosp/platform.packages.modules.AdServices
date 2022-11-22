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
import static org.junit.Assert.assertNotEquals;
import static org.junit.Assert.assertNotNull;
import static org.junit.Assert.assertNull;
import static org.junit.Assert.assertThrows;
import static org.junit.Assert.assertTrue;

import android.net.Uri;

import com.android.adservices.service.measurement.aggregation.AggregatableAttributionTrigger;
import com.android.adservices.service.measurement.aggregation.AggregateTriggerData;
import com.android.adservices.service.measurement.util.UnsignedLong;

import org.json.JSONArray;
import org.json.JSONException;
import org.json.JSONObject;
import org.junit.Test;

import java.util.Arrays;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.Set;

public class TriggerTest {
    private static final String TOP_LEVEL_FILTERS_JSON_STRING =
            "{\n"
                    + "  \"key_1\": [\"value_1\", \"value_2\"],\n"
                    + "  \"key_2\": [\"value_1\", \"value_2\"]\n"
                    + "}\n";

    private static final String TOP_LEVEL_FILTERS_JSON_STRING_X =
            "{\n"
                    + "  \"key_1x\": [\"value_1\", \"value_2\"],\n"
                    + "  \"key_2x\": [\"value_1\", \"value_2\"]\n"
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

    private static final UnsignedLong DEBUG_KEY = new UnsignedLong(2367372L);
    private static final Uri APP_DESTINATION = Uri.parse("android-app://com.android.app");
    private static final Uri APP_DESTINATION_WITH_PATH =
            Uri.parse("android-app://com.android.app/with/path");
    private static final Uri WEB_DESTINATION = WebUtil.validUri("https://example.test");
    private static final Uri WEB_DESTINATION_WITH_PATH =
            WebUtil.validUri("https://example.test/with/path");
    private static final Uri WEB_DESTINATION_WITH_SUBDOMAIN =
            WebUtil.validUri("https://subdomain.example.test");
    private static final Uri WEB_DESTINATION_WITH_SUBDOMAIN_PATH_QUERY_FRAGMENT =
            WebUtil.validUri("https://subdomain.example.test/with/path?query=0#fragment");
    private static final Uri WEB_DESTINATION_INVALID = Uri.parse("https://example.notatld");

    @Test
    public void testEqualsPass() throws JSONException {
        assertEquals(TriggerFixture.getValidTriggerBuilder().build(),
                TriggerFixture.getValidTriggerBuilder().build());
        JSONArray aggregateTriggerDatas = new JSONArray();
        JSONObject aggregateTriggerData1  = new JSONObject();
        aggregateTriggerData1.put("key_piece", "0x400");
        aggregateTriggerData1.put("source_keys", Arrays.asList("campaignCounts"));
        JSONObject aggregateTriggerData2  = new JSONObject();
        aggregateTriggerData2.put("key_piece", "0xA80");
        aggregateTriggerData2.put("source_keys", Arrays.asList("geoValue", "nonMatchingKey"));
        aggregateTriggerDatas.put(aggregateTriggerData1);
        aggregateTriggerDatas.put(aggregateTriggerData2);
        JSONObject values  = new JSONObject();
        values.put("campaignCounts", 32768);
        values.put("geoValue", 1664);
        assertEquals(
                TriggerFixture.getValidTriggerBuilder()
                        .setEnrollmentId("enrollment-id")
                        .setAttributionDestination(Uri.parse("https://example.test/aD"))
                        .setDestinationType(EventSurfaceType.WEB)
                        .setId("1")
                        .setEventTriggers(EVENT_TRIGGERS)
                        .setTriggerTime(5L)
                        .setIsDebugReporting(true)
                        .setStatus(Trigger.Status.PENDING)
                        .setRegistrant(Uri.parse("android-app://com.example.abc"))
                        .setAggregateTriggerData(aggregateTriggerDatas.toString())
                        .setAggregateValues(values.toString())
                        .setFilters(TOP_LEVEL_FILTERS_JSON_STRING)
                        .setNotFilters(TOP_LEVEL_FILTERS_JSON_STRING)
                        .setDebugKey(DEBUG_KEY)
                        .setAggregatableAttributionTrigger(
                                TriggerFixture.getValidTrigger()
                                        .getAggregatableAttributionTrigger())
                        .build(),
                TriggerFixture.getValidTriggerBuilder()
                        .setEnrollmentId("enrollment-id")
                        .setAttributionDestination(Uri.parse("https://example.test/aD"))
                        .setDestinationType(EventSurfaceType.WEB)
                        .setId("1")
                        .setEventTriggers(EVENT_TRIGGERS)
                        .setTriggerTime(5L)
                        .setIsDebugReporting(true)
                        .setStatus(Trigger.Status.PENDING)
                        .setRegistrant(Uri.parse("android-app://com.example.abc"))
                        .setAggregateTriggerData(aggregateTriggerDatas.toString())
                        .setAggregateValues(values.toString())
                        .setFilters(TOP_LEVEL_FILTERS_JSON_STRING)
                        .setNotFilters(TOP_LEVEL_FILTERS_JSON_STRING)
                        .setDebugKey(DEBUG_KEY)
                        .setAggregatableAttributionTrigger(
                                TriggerFixture.getValidTrigger()
                                        .getAggregatableAttributionTrigger())
                        .build());
    }

    @Test
    public void testEqualsFail() throws JSONException {
        assertNotEquals(
                TriggerFixture.getValidTriggerBuilder().setId("1").build(),
                TriggerFixture.getValidTriggerBuilder().setId("2").build());
        assertNotEquals(
                TriggerFixture.getValidTriggerBuilder()
                        .setAttributionDestination(Uri.parse("https://1.test")).build(),
                TriggerFixture.getValidTriggerBuilder()
                        .setAttributionDestination(Uri.parse("https://2.test")).build());
        assertNotEquals(
                TriggerFixture.getValidTriggerBuilder()
                        .setDestinationType(EventSurfaceType.APP).build(),
                TriggerFixture.getValidTriggerBuilder()
                        .setDestinationType(EventSurfaceType.WEB).build());
        assertNotEquals(
                TriggerFixture.getValidTriggerBuilder()
                        .setEnrollmentId("enrollment-id-1").build(),
                TriggerFixture.getValidTriggerBuilder()
                        .setEnrollmentId("enrollment-id-2").build());
        assertNotEquals(
                TriggerFixture.getValidTriggerBuilder().setEventTriggers("a").build(),
                TriggerFixture.getValidTriggerBuilder().setEventTriggers("b").build());
        assertNotEquals(
                TriggerFixture.getValidTriggerBuilder().setTriggerTime(1L).build(),
                TriggerFixture.getValidTriggerBuilder().setTriggerTime(2L).build());
        assertNotEquals(
                TriggerFixture.getValidTriggerBuilder()
                        .setStatus(Trigger.Status.PENDING).build(),
                TriggerFixture.getValidTriggerBuilder()
                        .setStatus(Trigger.Status.IGNORED).build());
        assertNotEquals(
                TriggerFixture.getValidTriggerBuilder()
                        .setRegistrant(Uri.parse("android-app://com.example.abc"))
                        .build(),
                TriggerFixture.getValidTriggerBuilder()
                        .setRegistrant(Uri.parse("android-app://com.example.xyz"))
                        .build());
        JSONArray aggregateTriggerDataList1 = new JSONArray();
        JSONObject aggregateTriggerData1  = new JSONObject();
        aggregateTriggerData1.put("key_piece", "0x400");
        aggregateTriggerData1.put("source_keys", Arrays.asList("campaignCounts"));
        aggregateTriggerDataList1.put(aggregateTriggerData1);
        JSONArray aggregateTriggerDataList2 = new JSONArray();
        JSONObject aggregateTriggerData2  = new JSONObject();
        aggregateTriggerData2.put("key_piece", "0xA80");
        aggregateTriggerData2.put("source_keys", Arrays.asList("geoValue", "nonMatchingKey"));
        aggregateTriggerDataList2.put(aggregateTriggerData2);
        assertNotEquals(
                TriggerFixture.getValidTriggerBuilder()
                        .setAggregateTriggerData(aggregateTriggerDataList1.toString()).build(),
                TriggerFixture.getValidTriggerBuilder()
                        .setAggregateTriggerData(aggregateTriggerDataList2.toString()).build());
        JSONObject values1  = new JSONObject();
        values1.put("campaignCounts", 32768);
        JSONObject values2  = new JSONObject();
        values2.put("geoValue", 1664);
        assertNotEquals(
                TriggerFixture.getValidTriggerBuilder()
                        .setAggregateValues(values1.toString()).build(),
                TriggerFixture.getValidTriggerBuilder()
                        .setAggregateValues(values2.toString()).build());
        assertNotEquals(
                TriggerFixture.getValidTriggerBuilder()
                        .setFilters(TOP_LEVEL_FILTERS_JSON_STRING).build(),
                TriggerFixture.getValidTriggerBuilder()
                        .setFilters(TOP_LEVEL_FILTERS_JSON_STRING_X).build());
        assertNotEquals(
                TriggerFixture.getValidTriggerBuilder()
                        .setNotFilters(TOP_LEVEL_FILTERS_JSON_STRING).build(),
                TriggerFixture.getValidTriggerBuilder()
                        .setNotFilters(TOP_LEVEL_FILTERS_JSON_STRING_X).build());
    }

    @Test
    public void testHashCode_equals() {
        final Trigger trigger1 = TriggerFixture.getValidTriggerBuilder().build();
        final Trigger trigger2 = TriggerFixture.getValidTriggerBuilder().build();
        final Set<Trigger> triggerSet1 = Set.of(trigger1);
        final Set<Trigger> triggerSet2 = Set.of(trigger2);
        assertEquals(trigger1.hashCode(), trigger2.hashCode());
        assertEquals(trigger1, trigger2);
        assertEquals(triggerSet1, triggerSet2);
    }

    @Test
    public void testHashCode_notEquals() {
        final Trigger trigger1 = TriggerFixture.getValidTriggerBuilder().build();
        final Trigger trigger2 = TriggerFixture.getValidTriggerBuilder().setId("100").build();
        final Set<Trigger> triggerSet1 = Set.of(trigger1);
        final Set<Trigger> triggerSet2 = Set.of(trigger2);
        assertNotEquals(trigger1.hashCode(), trigger2.hashCode());
        assertNotEquals(trigger1, trigger2);
        assertNotEquals(triggerSet1, triggerSet2);
    }

    @Test
    public void testTriggerBuilder_validateArgumentAttributionDestination() {
        assertInvalidTriggerArguments(
                null,
                TriggerFixture.ValidTriggerParams.ENROLLMENT_ID,
                TriggerFixture.ValidTriggerParams.REGISTRANT,
                TriggerFixture.ValidTriggerParams.TRIGGER_TIME,
                TriggerFixture.ValidTriggerParams.EVENT_TRIGGERS,
                TriggerFixture.ValidTriggerParams.AGGREGATE_TRIGGER_DATA,
                TriggerFixture.ValidTriggerParams.AGGREGATE_VALUES,
                TriggerFixture.ValidTriggerParams.TOP_LEVEL_FILTERS_JSON_STRING,
                TriggerFixture.ValidTriggerParams.TOP_LEVEL_NOT_FILTERS_JSON_STRING,
                TriggerFixture.ValidTriggerParams.DEBUG_KEY);
        assertInvalidTriggerArguments(
                Uri.parse("com.destination"),
                TriggerFixture.ValidTriggerParams.ENROLLMENT_ID,
                TriggerFixture.ValidTriggerParams.REGISTRANT,
                TriggerFixture.ValidTriggerParams.TRIGGER_TIME,
                TriggerFixture.ValidTriggerParams.EVENT_TRIGGERS,
                TriggerFixture.ValidTriggerParams.AGGREGATE_TRIGGER_DATA,
                TriggerFixture.ValidTriggerParams.AGGREGATE_VALUES,
                TriggerFixture.ValidTriggerParams.TOP_LEVEL_FILTERS_JSON_STRING,
                TriggerFixture.ValidTriggerParams.TOP_LEVEL_NOT_FILTERS_JSON_STRING,
                TriggerFixture.ValidTriggerParams.DEBUG_KEY);
    }

    @Test
    public void testTriggerBuilder_validateArgumentEnrollmentId() {
        assertInvalidTriggerArguments(
                TriggerFixture.ValidTriggerParams.ATTRIBUTION_DESTINATION,
                null,
                TriggerFixture.ValidTriggerParams.REGISTRANT,
                TriggerFixture.ValidTriggerParams.TRIGGER_TIME,
                TriggerFixture.ValidTriggerParams.EVENT_TRIGGERS,
                TriggerFixture.ValidTriggerParams.AGGREGATE_TRIGGER_DATA,
                TriggerFixture.ValidTriggerParams.AGGREGATE_VALUES,
                TriggerFixture.ValidTriggerParams.TOP_LEVEL_FILTERS_JSON_STRING,
                TriggerFixture.ValidTriggerParams.TOP_LEVEL_NOT_FILTERS_JSON_STRING,
                TriggerFixture.ValidTriggerParams.DEBUG_KEY);
    }

    @Test
    public void testTriggerBuilder_validateArgumentRegistrant() {
        assertInvalidTriggerArguments(
                TriggerFixture.ValidTriggerParams.ATTRIBUTION_DESTINATION,
                TriggerFixture.ValidTriggerParams.ENROLLMENT_ID,
                null,
                TriggerFixture.ValidTriggerParams.TRIGGER_TIME,
                TriggerFixture.ValidTriggerParams.EVENT_TRIGGERS,
                TriggerFixture.ValidTriggerParams.AGGREGATE_TRIGGER_DATA,
                TriggerFixture.ValidTriggerParams.AGGREGATE_VALUES,
                TriggerFixture.ValidTriggerParams.TOP_LEVEL_FILTERS_JSON_STRING,
                TriggerFixture.ValidTriggerParams.TOP_LEVEL_NOT_FILTERS_JSON_STRING,
                TriggerFixture.ValidTriggerParams.DEBUG_KEY);
        assertInvalidTriggerArguments(
                TriggerFixture.ValidTriggerParams.ATTRIBUTION_DESTINATION,
                TriggerFixture.ValidTriggerParams.ENROLLMENT_ID,
                Uri.parse("com.registrant"),
                TriggerFixture.ValidTriggerParams.TRIGGER_TIME,
                TriggerFixture.ValidTriggerParams.EVENT_TRIGGERS,
                TriggerFixture.ValidTriggerParams.AGGREGATE_TRIGGER_DATA,
                TriggerFixture.ValidTriggerParams.AGGREGATE_VALUES,
                TriggerFixture.ValidTriggerParams.TOP_LEVEL_FILTERS_JSON_STRING,
                TriggerFixture.ValidTriggerParams.TOP_LEVEL_NOT_FILTERS_JSON_STRING,
                TriggerFixture.ValidTriggerParams.DEBUG_KEY);
    }

    @Test
    public void testAggregatableAttributionTrigger() throws Exception {
        final Map<String, Integer> values = Map.of("foo", 93);
        final List<AggregateTriggerData> triggerData =
                List.of(new AggregateTriggerData.Builder().build());
        final AggregatableAttributionTrigger attributionTrigger =
                new AggregatableAttributionTrigger.Builder()
                        .setValues(values)
                        .setTriggerData(triggerData)
                        .build();

        final Trigger trigger =
                TriggerFixture.getValidTriggerBuilder()
                        .setAggregatableAttributionTrigger(attributionTrigger)
                        .build();

        assertNotNull(trigger.getAggregatableAttributionTrigger());
        assertNotNull(trigger.getAggregatableAttributionTrigger().getTriggerData());
        assertEquals(values, trigger.getAggregatableAttributionTrigger().getValues());
        assertEquals(triggerData, trigger.getAggregatableAttributionTrigger().getTriggerData());
    }

    @Test
    public void testParseAggregateTrigger() throws JSONException {
        JSONArray triggerDatas = new JSONArray();
        JSONObject jsonObject1 = new JSONObject();
        jsonObject1.put("key_piece", "0x400");
        jsonObject1.put("source_keys", new JSONArray(Arrays.asList("campaignCounts")));
        jsonObject1.put("filters", createFilterJSONObject());
        jsonObject1.put("not_filters", createFilterJSONObject());
        JSONObject jsonObject2 = new JSONObject();
        jsonObject2.put("key_piece", "0xA80");
        jsonObject2.put("source_keys", new JSONArray(Arrays.asList("geoValue", "noMatch")));
        triggerDatas.put(jsonObject1);
        triggerDatas.put(jsonObject2);

        JSONObject values = new JSONObject();
        values.put("campaignCounts", 32768);
        values.put("geoValue", 1664);

        Trigger trigger = TriggerFixture.getValidTriggerBuilder()
                .setAggregateTriggerData(triggerDatas.toString())
                .setAggregateValues(values.toString()).build();
        Optional<AggregatableAttributionTrigger> aggregatableAttributionTrigger =
                trigger.parseAggregateTrigger();

        assertTrue(aggregatableAttributionTrigger.isPresent());
        AggregatableAttributionTrigger aggregateTrigger = aggregatableAttributionTrigger.get();
        assertEquals(aggregateTrigger.getTriggerData().size(), 2);
        assertEquals(aggregateTrigger.getTriggerData().get(0).getSourceKeys().size(), 1);
        assertEquals(aggregateTrigger.getTriggerData().get(0).getKey().intValue(), 1024);
        assertTrue(aggregateTrigger.getTriggerData().get(0)
                .getSourceKeys().contains("campaignCounts"));
        assertTrue(aggregateTrigger.getTriggerData().get(0).getFilter().isPresent());
        assertEquals(aggregateTrigger.getTriggerData().get(0).getFilter()
                .get().getAttributionFilterMap().size(), 2);
        assertTrue(aggregateTrigger.getTriggerData().get(0).getNotFilter().isPresent());
        assertEquals(aggregateTrigger.getTriggerData().get(0).getNotFilter()
                .get().getAttributionFilterMap().size(), 2);

        assertEquals(aggregateTrigger.getTriggerData().get(1).getKey().intValue(), 2688);
        assertEquals(aggregateTrigger.getTriggerData().get(1).getSourceKeys().size(), 2);
        assertTrue(aggregateTrigger.getTriggerData().get(1).getSourceKeys().contains("geoValue"));
        assertTrue(aggregateTrigger.getTriggerData().get(1).getSourceKeys().contains("noMatch"));
        assertEquals(aggregateTrigger.getValues().size(), 2);
        assertEquals(aggregateTrigger.getValues().get("campaignCounts").intValue(), 32768);
        assertEquals(aggregateTrigger.getValues().get("geoValue").intValue(), 1664);
    }

    @Test
    public void testGetAttributionDestinationBaseUri_appDestination() throws JSONException {
        Trigger trigger = TriggerFixture.getValidTriggerBuilder()
                .setAttributionDestination(APP_DESTINATION)
                .setDestinationType(EventSurfaceType.APP)
                .build();
        assertEquals(APP_DESTINATION, trigger.getAttributionDestinationBaseUri());
    }

    @Test
    public void testGetAttributionDestinationBaseUri_trimsAppDestination() throws JSONException {
        Trigger trigger = TriggerFixture.getValidTriggerBuilder()
                .setAttributionDestination(APP_DESTINATION_WITH_PATH)
                .setDestinationType(EventSurfaceType.APP)
                .build();
        assertEquals(APP_DESTINATION, trigger.getAttributionDestinationBaseUri());
    }

    @Test
    public void testGetAttributionDestinationBaseUri_webDestination() throws JSONException {
        Trigger trigger = TriggerFixture.getValidTriggerBuilder()
                .setAttributionDestination(WEB_DESTINATION)
                .setDestinationType(EventSurfaceType.WEB)
                .build();
        assertEquals(WEB_DESTINATION, trigger.getAttributionDestinationBaseUri());
    }

    @Test
    public void testGetAttributionDestinationBaseUri_trimsWebDestinationWithSubdomain()
            throws JSONException {
        Trigger trigger = TriggerFixture.getValidTriggerBuilder()
                .setAttributionDestination(WEB_DESTINATION_WITH_SUBDOMAIN)
                .setDestinationType(EventSurfaceType.WEB)
                .build();
        assertEquals(WEB_DESTINATION, trigger.getAttributionDestinationBaseUri());
    }

    @Test
    public void testGetAttributionDestinationBaseUri_trimsWebDestinationWithPath()
            throws JSONException {
        Trigger trigger = TriggerFixture.getValidTriggerBuilder()
                .setAttributionDestination(WEB_DESTINATION_WITH_PATH)
                .setDestinationType(EventSurfaceType.WEB)
                .build();
        assertEquals(WEB_DESTINATION, trigger.getAttributionDestinationBaseUri());
    }

    @Test
    public void testGetAttributionDestinationBaseUri_trimsWebDestinationWithSubdomainPathQueryFrag()
            throws JSONException {
        Trigger trigger = TriggerFixture.getValidTriggerBuilder()
                .setAttributionDestination(WEB_DESTINATION_WITH_SUBDOMAIN_PATH_QUERY_FRAGMENT)
                .setDestinationType(EventSurfaceType.WEB)
                .build();
        assertEquals(WEB_DESTINATION, trigger.getAttributionDestinationBaseUri());
    }

    @Test
    public void testGetAttributionDestinationBaseUri_invalidWebDestination()
            throws JSONException {
        Trigger trigger = TriggerFixture.getValidTriggerBuilder()
                .setAttributionDestination(WEB_DESTINATION_INVALID)
                .setDestinationType(EventSurfaceType.WEB)
                .build();
        assertNull(trigger.getAttributionDestinationBaseUri());
    }

    @Test
    public void parseEventTriggers() throws JSONException {
        // setup
        JSONObject filters1 =
                new JSONObject(
                        "{\n"
                                + "    \"filter_key_1\": [\"filter_value_1\"], \n"
                                + "    \"filter_key_2\": [\"filter_value_2\"] \n"
                                + "   }");
        JSONObject notFilters1 =
                new JSONObject(
                        "{\n"
                                + "    \"not_filter_key_1\": [\"not_filter_value_1\", "
                                + "\"not_filter_value_2\"]"
                                + "   }");
        JSONObject notFilters2 =
                new JSONObject("{\n" + "    \"key_1\": [\"value_1_x\"] \n" + "   }");
        Trigger trigger =
                TriggerFixture.getValidTriggerBuilder()
                        .setId("triggerId1")
                        .setStatus(Trigger.Status.PENDING)
                        .setEventTriggers(
                                "[\n"
                                        + "{\n"
                                        + "  \"trigger_data\": \"2\",\n"
                                        + "  \"priority\": \"2\",\n"
                                        + "  \"deduplication_key\": \"2\",\n"
                                        + "  \"filters\": {\n"
                                        + "    \"filter_key_1\": [\"filter_value_1\"], \n"
                                        + "    \"filter_key_2\": [\"filter_value_2\"] \n"
                                        + "   },\n"
                                        + "  \"not_filters\": {\n"
                                        + "    \"not_filter_key_1\": [\"not_filter_value_1\", "
                                        + "\"not_filter_value_2\"]"
                                        + "   }\n"
                                        + "},"
                                        + "{\n"
                                        + "  \"trigger_data\": \"3\",\n"
                                        + "  \"priority\": \"3\",\n"
                                        + "  \"deduplication_key\": \"3\",\n"
                                        + "  \"not_filters\": {\n"
                                        + "    \"key_1\": [\"value_1_x\"] \n"
                                        + "   }\n"
                                        + "}"
                                        + "]\n")
                        .setTriggerTime(234324L)
                        .build();
        EventTrigger eventTrigger1 =
                new EventTrigger.Builder()
                        .setTriggerPriority(2L)
                        .setTriggerData(new UnsignedLong(2L))
                        .setDedupKey(new UnsignedLong(2L))
                        .setFilter(
                                new FilterData.Builder()
                                        .buildFilterData(filters1)
                                        .build())
                        .setNotFilter(
                                new FilterData.Builder()
                                        .buildFilterData(notFilters1)
                                        .build())
                        .build();
        EventTrigger eventTrigger2 =
                new EventTrigger.Builder()
                        .setTriggerPriority(3L)
                        .setTriggerData(new UnsignedLong(3L))
                        .setDedupKey(new UnsignedLong(3L))
                        .setNotFilter(
                                new FilterData.Builder()
                                        .buildFilterData(notFilters2)
                                        .build())
                        .build();

        // Action
        List<EventTrigger> actualEventTriggers = trigger.parseEventTriggers();

        // Assertion
        assertEquals(Arrays.asList(eventTrigger1, eventTrigger2), actualEventTriggers);
    }

    private void assertInvalidTriggerArguments(
            Uri attributionDestination,
            String enrollmentId,
            Uri registrant,
            Long triggerTime,
            String eventTriggers,
            String aggregateTriggerData,
            String aggregateValues,
            String filters,
            String notFilters,
            UnsignedLong debugKey) {
        assertThrows(
                IllegalArgumentException.class,
                () ->
                        new Trigger.Builder()
                                .setAttributionDestination(attributionDestination)
                                .setEnrollmentId(enrollmentId)
                                .setRegistrant(registrant)
                                .setTriggerTime(triggerTime)
                                .setEventTriggers(eventTriggers)
                                .setAggregateTriggerData(aggregateTriggerData)
                                .setAggregateValues(aggregateValues)
                                .setFilters(filters)
                                .setNotFilters(notFilters)
                                .setDebugKey(debugKey)
                                .build());
    }

    private JSONObject createFilterJSONObject() throws JSONException {
        JSONObject filterData = new JSONObject();
        filterData.put("conversion_subdomain",
                new JSONArray(Arrays.asList("electronics.megastore")));
        filterData.put("product", new JSONArray(Arrays.asList("1234", "2345")));
        return filterData;
    }
}

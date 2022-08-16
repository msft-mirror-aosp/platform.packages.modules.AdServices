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

package com.android.adservices.service.measurement.aggregation;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertNotEquals;
import static org.junit.Assert.assertNull;
import static org.junit.Assert.assertTrue;

import androidx.test.filters.SmallTest;

import org.junit.Test;

import java.math.BigInteger;
import java.util.Arrays;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;

/** Unit tests for {@link AggregateTriggerData} */
@SmallTest
public final class AggregateTriggerDataTest {

    @Test
    public void testCreation() throws Exception {
        AggregateTriggerData attributionTriggerData = createExample();

        assertEquals(attributionTriggerData.getKey().longValue(), 5L);
        assertEquals(attributionTriggerData.getSourceKeys().size(), 3);
        assertTrue(attributionTriggerData.getFilter().isPresent());
        AggregateFilterData filterData = attributionTriggerData.getFilter().get();
        AggregateFilterData nonFilteredData = attributionTriggerData.getNotFilter().get();
        assertEquals(2, filterData.getAttributionFilterMap().get("ctid").size());
        assertEquals(1, nonFilteredData.getAttributionFilterMap().get("nctid").size());
    }

    @Test
    public void testDefaults() throws Exception {
        AggregateTriggerData attributionTriggerData =
                new AggregateTriggerData.Builder().build();
        assertNull(attributionTriggerData.getKey());
        assertEquals(attributionTriggerData.getSourceKeys().size(), 0);
        assertFalse(attributionTriggerData.getFilter().isPresent());
        assertFalse(attributionTriggerData.getNotFilter().isPresent());
    }

    @Test
    public void testHashCode_equals() throws Exception {
        final AggregateTriggerData data1 = createExample();
        final AggregateTriggerData data2 = createExample();
        final Set<AggregateTriggerData> dataSet1 = Set.of(data1);
        final Set<AggregateTriggerData> dataSet2 = Set.of(data2);
        assertEquals(data1.hashCode(), data2.hashCode());
        assertEquals(data1, data2);
        assertEquals(dataSet1, dataSet2);
    }

    @Test
    public void testHashCode_notEquals() throws Exception {
        final AggregateTriggerData data1 = createExample();

        Map<String, List<String>> attributionFilterMap = new HashMap<>();
        attributionFilterMap.put("ctid", Arrays.asList("1"));
        AggregateFilterData filterData =
                new AggregateFilterData.Builder()
                        .setAttributionFilterMap(attributionFilterMap)
                        .build();

        Map<String, List<String>> attributionNonFilterMap = new HashMap<>();
        attributionNonFilterMap.put("other", Arrays.asList("1"));
        AggregateFilterData nonFilterData =
                new AggregateFilterData.Builder()
                        .setAttributionFilterMap(attributionNonFilterMap)
                        .build();

        final AggregateTriggerData data2 =
                new AggregateTriggerData.Builder()
                        .setKey(BigInteger.valueOf(1L))
                        .setSourceKeys(
                                new HashSet<>(
                                        Arrays.asList(
                                                "campCounts", "campGeoCounts", "campGeoValue")))
                        .setFilter(filterData)
                        .setNotFilter(nonFilterData)
                        .build();
        final Set<AggregateTriggerData> dataSet1 = Set.of(data1);
        final Set<AggregateTriggerData> dataSet2 = Set.of(data2);
        assertNotEquals(data1.hashCode(), data2.hashCode());
        assertNotEquals(data1, data2);
        assertNotEquals(dataSet1, dataSet2);
    }

    private AggregateTriggerData createExample() {
        Map<String, List<String>> attributionFilterMap = new HashMap<>();
        attributionFilterMap.put("ctid", Arrays.asList("1", "2"));
        AggregateFilterData filterData =
                new AggregateFilterData.Builder()
                        .setAttributionFilterMap(attributionFilterMap)
                        .build();

        Map<String, List<String>> attributionNonFilterMap = new HashMap<>();
        attributionNonFilterMap.put("nctid", Arrays.asList("3"));
        AggregateFilterData nonFilterData =
                new AggregateFilterData.Builder()
                        .setAttributionFilterMap(attributionNonFilterMap)
                        .build();

        return new AggregateTriggerData.Builder()
                .setKey(BigInteger.valueOf(5L))
                .setSourceKeys(
                        new HashSet<>(Arrays.asList("campCounts", "campGeoCounts", "campGeoValue")))
                .setFilter(filterData)
                .setNotFilter(nonFilterData)
                .build();
    }
}

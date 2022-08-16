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
import static org.junit.Assert.assertNotEquals;

import androidx.test.filters.SmallTest;

import org.junit.Test;

import java.math.BigInteger;
import java.util.Arrays;
import java.util.HashMap;
import java.util.HashSet;
import java.util.Map;
import java.util.Set;

/** Unit tests for {@link AggregatableAttributionTrigger} */
@SmallTest
public final class AggregatableAttributionTriggerTest {

    private AggregatableAttributionTrigger createExample() {
        AggregateTriggerData attributionTriggerData1 =
                new AggregateTriggerData.Builder()
                        .setKey(BigInteger.valueOf(159L))
                        .setSourceKeys(new HashSet<>(
                                Arrays.asList("campCounts", "campGeoCounts"))).build();
        AggregateTriggerData attributionTriggerData2 =
                new AggregateTriggerData.Builder()
                        .setKey(BigInteger.valueOf(5L))
                        .setSourceKeys(new HashSet<>(
                                Arrays.asList("campCounts", "campGeoCounts", "campGeoValue")))
                        .build();

        Map<String, Integer> values = new HashMap<>();
        values.put("campCounts", 1);
        values.put("campGeoCounts", 100);

        return new AggregatableAttributionTrigger.Builder()
                .setTriggerData(Arrays.asList(attributionTriggerData1, attributionTriggerData2))
                .setValues(values)
                .build();
    }

    @Test
    public void testCreation() throws Exception {
        AggregatableAttributionTrigger attributionTrigger = createExample();

        assertEquals(attributionTrigger.getTriggerData().size(), 2);
        assertEquals(attributionTrigger.getTriggerData().get(0).getKey().longValue(), 159L);
        assertEquals(attributionTrigger.getTriggerData().get(0).getSourceKeys().size(), 2);
        assertEquals(attributionTrigger.getTriggerData().get(1).getKey().longValue(), 5L);
        assertEquals(attributionTrigger.getTriggerData().get(1).getSourceKeys().size(), 3);
        assertEquals(attributionTrigger.getValues().get("campCounts").intValue(), 1);
        assertEquals(attributionTrigger.getValues().get("campGeoCounts").intValue(), 100);
    }

    @Test
    public void testDefaults() throws Exception {
        AggregatableAttributionTrigger attributionTrigger =
                new AggregatableAttributionTrigger.Builder().build();
        assertEquals(attributionTrigger.getTriggerData().size(), 0);
        assertEquals(attributionTrigger.getValues().size(), 0);
    }

    @Test
    public void testHashCode_equals() throws Exception {
        final AggregatableAttributionTrigger attributionTrigger1 = createExample();
        final AggregatableAttributionTrigger attributionTrigger2 = createExample();
        final Set<AggregatableAttributionTrigger> attributionTriggerSet1 =
                Set.of(attributionTrigger1);
        final Set<AggregatableAttributionTrigger> attributionTriggerSet2 =
                Set.of(attributionTrigger2);
        assertEquals(attributionTrigger1.hashCode(), attributionTrigger2.hashCode());
        assertEquals(attributionTrigger1, attributionTrigger2);
        assertEquals(attributionTriggerSet1, attributionTriggerSet2);
    }

    @Test
    public void testHashCode_notEquals() throws Exception {
        final AggregatableAttributionTrigger attributionTrigger1 = createExample();

        AggregateTriggerData attributionTriggerData1 =
                new AggregateTriggerData.Builder()
                        .setKey(BigInteger.valueOf(159L))
                        .setSourceKeys(new HashSet<>(Arrays.asList("campCounts", "campGeoCounts")))
                        .build();
        Map<String, Integer> values = new HashMap<>();
        values.put("campCounts", 1);
        values.put("campGeoCounts", 100);

        final AggregatableAttributionTrigger attributionTrigger2 =
                new AggregatableAttributionTrigger.Builder()
                        .setTriggerData(Arrays.asList(attributionTriggerData1))
                        .setValues(values)
                        .build();
        final Set<AggregatableAttributionTrigger> attributionTriggerSet1 =
                Set.of(attributionTrigger1);
        final Set<AggregatableAttributionTrigger> attributionTriggerSet2 =
                Set.of(attributionTrigger2);
        assertNotEquals(attributionTrigger1.hashCode(), attributionTrigger2.hashCode());
        assertNotEquals(attributionTrigger1, attributionTrigger2);
        assertNotEquals(attributionTriggerSet1, attributionTriggerSet2);
    }
}

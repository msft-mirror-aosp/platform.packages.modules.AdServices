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

import java.util.Arrays;
import java.util.Collections;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;

/** Unit tests for {@link AggregateFilterData} */
@SmallTest
public final class AggregateFilterDataTest {

    @Test
    public void testCreation() throws Exception {
        AggregateFilterData attributionFilterData = createExample();

        assertEquals(attributionFilterData.getAttributionFilterMap().size(), 2);
        assertEquals(attributionFilterData.getAttributionFilterMap().get("type").size(), 4);
        assertEquals(attributionFilterData.getAttributionFilterMap().get("ctid").size(), 1);
    }

    @Test
    public void testDefaults() throws Exception {
        AggregateFilterData data = new AggregateFilterData.Builder().build();
        assertEquals(data.getAttributionFilterMap().size(), 0);
    }

    @Test
    public void testHashCode_equals() throws Exception {
        final AggregateFilterData data1 = createExample();
        final AggregateFilterData data2 = createExample();
        final Set<AggregateFilterData> dataSet1 = Set.of(data1);
        final Set<AggregateFilterData> dataSet2 = Set.of(data2);
        assertEquals(data1.hashCode(), data2.hashCode());
        assertEquals(data1, data2);
        assertEquals(dataSet1, dataSet2);
    }

    @Test
    public void testHashCode_notEquals() throws Exception {
        final AggregateFilterData data1 = createExample();

        Map<String, List<String>> attributionFilterMap = new HashMap<>();
        attributionFilterMap.put("type", Arrays.asList("2", "3", "4"));
        attributionFilterMap.put("ctid", Collections.singletonList("id"));

        final AggregateFilterData data2 =
                new AggregateFilterData.Builder()
                        .setAttributionFilterMap(attributionFilterMap)
                        .build();
        final Set<AggregateFilterData> dataSet1 = Set.of(data1);
        final Set<AggregateFilterData> dataSet2 = Set.of(data2);
        assertNotEquals(data1.hashCode(), data2.hashCode());
        assertNotEquals(data1, data2);
        assertNotEquals(dataSet1, dataSet2);
    }

    private AggregateFilterData createExample() {
        Map<String, List<String>> attributionFilterMap = new HashMap<>();
        attributionFilterMap.put("type", Arrays.asList("1", "2", "3", "4"));
        attributionFilterMap.put("ctid", Collections.singletonList("id"));

        return new AggregateFilterData.Builder()
                .setAttributionFilterMap(attributionFilterMap)
                .build();
    }
}

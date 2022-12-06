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

package com.android.adservices.service.measurement.util;

import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertTrue;

import com.android.adservices.service.measurement.FilterMap;

import org.junit.Test;

import java.util.Arrays;
import java.util.Collections;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

public class FilterTest {
    @Test
    public void testIsFilterMatch_filterSet_nonEmptyValues_returnTrue() {
        Map<String, List<String>> sourceFilterMap = new HashMap<>();
        sourceFilterMap.put(
                "conversion_subdomain", Collections.singletonList("electronics.megastore"));
        sourceFilterMap.put("product", Arrays.asList("1234", "234"));
        sourceFilterMap.put("ctid", Collections.singletonList("id"));
        FilterMap sourceFilter =
                new FilterMap.Builder().setAttributionFilterMap(sourceFilterMap).build();

        Map<String, List<String>> triggerFilterMap1 = new HashMap<>();
        triggerFilterMap1.put(
                "conversion_subdomain", Collections.singletonList("electronics.megastore.one"));
        triggerFilterMap1.put("product", Arrays.asList("12345", "2345"));
        triggerFilterMap1.put("id", Arrays.asList("1", "2"));
        FilterMap triggerFilter1 =
                new FilterMap.Builder().setAttributionFilterMap(triggerFilterMap1).build();

        Map<String, List<String>> triggerFilterMap2 = new HashMap<>();
        triggerFilterMap2.put(
                "conversion_subdomain", Collections.singletonList("electronics.megastore"));
        triggerFilterMap2.put("product", Arrays.asList("1234", "2345"));
        triggerFilterMap2.put("id", Arrays.asList("1", "2"));
        FilterMap triggerFilter2 =
                new FilterMap.Builder().setAttributionFilterMap(triggerFilterMap2).build();

        assertTrue(Filter.isFilterMatch(
                sourceFilter, List.of(triggerFilter1, triggerFilter2), true));
    }

    @Test
    public void testIsFilterMatch_nonEmptyValues_returnTrue() {
        Map<String, List<String>> sourceFilterMap = new HashMap<>();
        sourceFilterMap.put(
                "conversion_subdomain", Collections.singletonList("electronics.megastore"));
        sourceFilterMap.put("product", Arrays.asList("1234", "234"));
        sourceFilterMap.put("ctid", Collections.singletonList("id"));
        FilterMap sourceFilter =
                new FilterMap.Builder().setAttributionFilterMap(sourceFilterMap).build();

        Map<String, List<String>> triggerFilterMap = new HashMap<>();
        triggerFilterMap.put(
                "conversion_subdomain", Collections.singletonList("electronics.megastore"));
        triggerFilterMap.put("product", Arrays.asList("1234", "2345"));
        triggerFilterMap.put("id", Arrays.asList("1", "2"));
        FilterMap triggerFilter =
                new FilterMap.Builder().setAttributionFilterMap(triggerFilterMap).build();

        assertTrue(Filter.isFilterMatch(sourceFilter, List.of(triggerFilter), true));
    }

    @Test
    public void testIsFilterMatch_filterSet_nonEmptyValues_returnFalse() {
        Map<String, List<String>> sourceFilterMap = new HashMap<>();
        sourceFilterMap.put(
                "conversion_subdomain", Collections.singletonList("electronics.megastore"));
        sourceFilterMap.put("product", Arrays.asList("1234", "234"));
        sourceFilterMap.put("ctid", Collections.singletonList("id"));
        FilterMap sourceFilter =
                new FilterMap.Builder().setAttributionFilterMap(sourceFilterMap).build();

        Map<String, List<String>> triggerFilterMap1 = new HashMap<>();
        triggerFilterMap1.put(
                "conversion_subdomain", Collections.singletonList("electronics.megastore"));
        // Doesn't match
        triggerFilterMap1.put("product", Arrays.asList("2", "3"));
        triggerFilterMap1.put("id", Arrays.asList("11", "22"));
        FilterMap triggerFilter1 =
                new FilterMap.Builder().setAttributionFilterMap(triggerFilterMap1).build();

        Map<String, List<String>> triggerFilterMap2 = new HashMap<>();
        triggerFilterMap2.put(
                "conversion_subdomain", Collections.singletonList("electronics.megastore"));
        // Doesn't match
        triggerFilterMap2.put("product", Arrays.asList("1", "2"));
        triggerFilterMap2.put("id", Arrays.asList("1", "2"));
        FilterMap triggerFilter2 =
                new FilterMap.Builder().setAttributionFilterMap(triggerFilterMap2).build();

        assertFalse(Filter.isFilterMatch(
                sourceFilter, List.of(triggerFilter1, triggerFilter2), true));
    }

    @Test
    public void testIsFilterMatch_nonEmptyValues_returnFalse() {
        Map<String, List<String>> sourceFilterMap = new HashMap<>();
        sourceFilterMap.put(
                "conversion_subdomain", Collections.singletonList("electronics.megastore"));
        sourceFilterMap.put("product", Arrays.asList("1234", "234"));
        sourceFilterMap.put("ctid", Collections.singletonList("id"));
        FilterMap sourceFilter =
                new FilterMap.Builder().setAttributionFilterMap(sourceFilterMap).build();

        Map<String, List<String>> triggerFilterMap = new HashMap<>();
        triggerFilterMap.put(
                "conversion_subdomain", Collections.singletonList("electronics.megastore"));
        // Doesn't match
        triggerFilterMap.put("product", Arrays.asList("1", "2"));
        triggerFilterMap.put("id", Arrays.asList("1", "2"));
        FilterMap triggerFilter =
                new FilterMap.Builder().setAttributionFilterMap(triggerFilterMap).build();

        assertFalse(Filter.isFilterMatch(sourceFilter, List.of(triggerFilter), true));
    }

    @Test
    public void testIsFilterMatch_filterSet_withEmptyValues_returnTrue() {
        Map<String, List<String>> sourceFilterMap = new HashMap<>();
        sourceFilterMap.put(
                "conversion_subdomain", Collections.singletonList("electronics.megastore"));
        sourceFilterMap.put("product", Collections.emptyList());
        sourceFilterMap.put("ctid", Collections.singletonList("id"));
        FilterMap sourceFilter =
                new FilterMap.Builder().setAttributionFilterMap(sourceFilterMap).build();

        Map<String, List<String>> triggerFilterMap1 = new HashMap<>();
        triggerFilterMap1.put(
                "conversion_subdomain", Collections.singletonList("electronics.megastore"));
        // Doesn't match
        triggerFilterMap1.put("product", Arrays.asList("2", "3"));
        triggerFilterMap1.put("id", Arrays.asList("11", "22"));
        FilterMap triggerFilter1 =
                new FilterMap.Builder().setAttributionFilterMap(triggerFilterMap1).build();

        Map<String, List<String>> triggerFilterMap2 = new HashMap<>();
        triggerFilterMap2.put(
                "conversion_subdomain", Collections.singletonList("electronics.megastore"));
        triggerFilterMap2.put("product", Collections.emptyList());
        triggerFilterMap2.put("id", Arrays.asList("1", "2"));
        FilterMap triggerFilter2 =
                new FilterMap.Builder().setAttributionFilterMap(triggerFilterMap2).build();

        assertTrue(Filter.isFilterMatch(
                sourceFilter, List.of(triggerFilter1, triggerFilter2), true));
    }

    @Test
    public void testIsFilterMatch_withEmptyValues_returnTrue() {
        Map<String, List<String>> sourceFilterMap = new HashMap<>();
        sourceFilterMap.put(
                "conversion_subdomain", Collections.singletonList("electronics.megastore"));
        sourceFilterMap.put("product", Collections.emptyList());
        sourceFilterMap.put("ctid", Collections.singletonList("id"));
        FilterMap sourceFilter =
                new FilterMap.Builder().setAttributionFilterMap(sourceFilterMap).build();

        Map<String, List<String>> triggerFilterMap = new HashMap<>();
        triggerFilterMap.put(
                "conversion_subdomain", Collections.singletonList("electronics.megastore"));
        triggerFilterMap.put("product", Collections.emptyList());
        triggerFilterMap.put("id", Arrays.asList("1", "2"));
        FilterMap triggerFilter =
                new FilterMap.Builder().setAttributionFilterMap(triggerFilterMap).build();

        assertTrue(Filter.isFilterMatch(sourceFilter, List.of(triggerFilter), true));
    }

    @Test
    public void testIsFilterMatch_filterSet_withEmptyValues_returnFalse() {
        Map<String, List<String>> sourceFilterMap = new HashMap<>();
        sourceFilterMap.put(
                "conversion_subdomain", Collections.singletonList("electronics.megastore"));
        sourceFilterMap.put("product", Arrays.asList("1234", "234"));
        sourceFilterMap.put("ctid", Collections.singletonList("id"));
        FilterMap sourceFilter =
                new FilterMap.Builder().setAttributionFilterMap(sourceFilterMap).build();

        Map<String, List<String>> triggerFilterMap1 = new HashMap<>();
        triggerFilterMap1.put(
                "conversion_subdomain", Collections.singletonList("electronics.megastore"));
        // Doesn't match
        triggerFilterMap1.put("product", Collections.emptyList());
        triggerFilterMap1.put("id", Arrays.asList("11", "22"));
        FilterMap triggerFilter1 =
                new FilterMap.Builder().setAttributionFilterMap(triggerFilterMap1).build();

        Map<String, List<String>> triggerFilterMap2 = new HashMap<>();
        triggerFilterMap2.put(
                "conversion_subdomain", Collections.singletonList("electronics.megastore"));
        // Doesn't match
        triggerFilterMap2.put("product", Collections.emptyList());
        triggerFilterMap2.put("id", Arrays.asList("1", "2"));
        FilterMap triggerFilter2 =
                new FilterMap.Builder().setAttributionFilterMap(triggerFilterMap2).build();

        assertFalse(Filter.isFilterMatch(
                sourceFilter, List.of(triggerFilter1, triggerFilter2), true));
    }

    @Test
    public void testIsFilterMatch_withEmptyValues_returnFalse() {
        Map<String, List<String>> sourceFilterMap = new HashMap<>();
        sourceFilterMap.put(
                "conversion_subdomain", Collections.singletonList("electronics.megastore"));
        sourceFilterMap.put("product", Arrays.asList("1234", "234"));
        sourceFilterMap.put("ctid", Collections.singletonList("id"));
        FilterMap sourceFilter =
                new FilterMap.Builder().setAttributionFilterMap(sourceFilterMap).build();

        Map<String, List<String>> triggerFilterMap = new HashMap<>();
        triggerFilterMap.put(
                "conversion_subdomain", Collections.singletonList("electronics.megastore"));
        // Doesn't match
        triggerFilterMap.put("product", Collections.emptyList());
        triggerFilterMap.put("id", Arrays.asList("1", "2"));
        FilterMap triggerFilter =
                new FilterMap.Builder().setAttributionFilterMap(triggerFilterMap).build();

        assertFalse(Filter.isFilterMatch(sourceFilter, List.of(triggerFilter), true));
    }

    @Test
    public void testIsFilterMatch_filterSet_withNegation_nonEmptyValues_returnTrue() {
        Map<String, List<String>> sourceFilterMap = new HashMap<>();
        sourceFilterMap.put(
                "conversion_subdomain", Collections.singletonList("electronics.megastore"));
        sourceFilterMap.put("product", Arrays.asList("1234", "234"));
        sourceFilterMap.put("ctid", Collections.singletonList("id"));
        FilterMap sourceFilter =
                new FilterMap.Builder().setAttributionFilterMap(sourceFilterMap).build();

        Map<String, List<String>> triggerFilterMap1 = new HashMap<>();
        // Intersecting values
        triggerFilterMap1.put("conversion_subdomain",
                Collections.singletonList("electronics.megastore"));
        triggerFilterMap1.put("product", Arrays.asList("1234", "2"));
        triggerFilterMap1.put("id", Arrays.asList("1", "2"));
        FilterMap triggerFilter1 =
                new FilterMap.Builder().setAttributionFilterMap(triggerFilterMap1).build();

        // Non-intersecting values
        Map<String, List<String>> triggerFilterMap2 = new HashMap<>();
        triggerFilterMap2.put("conversion_subdomain", Collections.singletonList("electronics"));
        triggerFilterMap2.put("product", Arrays.asList("1", "2"));
        triggerFilterMap2.put("id", Arrays.asList("1", "2"));
        FilterMap triggerFilter2 =
                new FilterMap.Builder().setAttributionFilterMap(triggerFilterMap2).build();

        assertTrue(Filter.isFilterMatch(
                sourceFilter, List.of(triggerFilter1, triggerFilter2), false));
    }

    @Test
    public void testIsFilterMatch_withNegation_nonEmptyValues_returnTrue() {
        Map<String, List<String>> sourceFilterMap = new HashMap<>();
        sourceFilterMap.put(
                "conversion_subdomain", Collections.singletonList("electronics.megastore"));
        sourceFilterMap.put("product", Arrays.asList("1234", "234"));
        sourceFilterMap.put("ctid", Collections.singletonList("id"));
        FilterMap sourceFilter =
                new FilterMap.Builder().setAttributionFilterMap(sourceFilterMap).build();

        Map<String, List<String>> triggerFilterMap = new HashMap<>();
        triggerFilterMap.put("conversion_subdomain", Collections.singletonList("electronics"));
        triggerFilterMap.put("product", Arrays.asList("1", "2"));
        triggerFilterMap.put("id", Arrays.asList("1", "2"));
        FilterMap triggerFilter =
                new FilterMap.Builder().setAttributionFilterMap(triggerFilterMap).build();

        assertTrue(Filter.isFilterMatch(sourceFilter, List.of(triggerFilter), false));
    }

    @Test
    public void testIsFilterMatch_filterSet_withNegation_nonEmptyValues_returnFalse() {
        Map<String, List<String>> sourceFilterMap = new HashMap<>();
        sourceFilterMap.put(
                "conversion_subdomain", Collections.singletonList("electronics.megastore"));
        sourceFilterMap.put("product", Arrays.asList("1234", "234"));
        sourceFilterMap.put("ctid", Collections.singletonList("id"));
        FilterMap sourceFilter =
                new FilterMap.Builder().setAttributionFilterMap(sourceFilterMap).build();

        Map<String, List<String>> triggerFilterMap1 = new HashMap<>();
        triggerFilterMap1.put("product", Arrays.asList("abcd", "234"));
        FilterMap triggerFilter1 =
                new FilterMap.Builder().setAttributionFilterMap(triggerFilterMap1).build();

        Map<String, List<String>> triggerFilterMap2 = new HashMap<>();
        triggerFilterMap2.put(
                "conversion_subdomain", Collections.singletonList("electronics.megastore"));
        triggerFilterMap2.put("product", Arrays.asList("1234", "2345"));
        triggerFilterMap2.put("id", Arrays.asList("1", "2"));
        FilterMap triggerFilter2 =
                new FilterMap.Builder().setAttributionFilterMap(triggerFilterMap2).build();

        assertFalse(Filter.isFilterMatch(
                sourceFilter, List.of(triggerFilter1, triggerFilter2), false));
    }

    @Test
    public void testIsFilterMatch_withNegation_nonEmptyValues_returnFalse() {
        Map<String, List<String>> sourceFilterMap = new HashMap<>();
        sourceFilterMap.put(
                "conversion_subdomain", Collections.singletonList("electronics.megastore"));
        sourceFilterMap.put("product", Arrays.asList("1234", "234"));
        sourceFilterMap.put("ctid", Collections.singletonList("id"));
        FilterMap sourceFilter =
                new FilterMap.Builder().setAttributionFilterMap(sourceFilterMap).build();

        Map<String, List<String>> triggerFilterMap = new HashMap<>();
        triggerFilterMap.put(
                "conversion_subdomain", Collections.singletonList("electronics.megastore"));
        triggerFilterMap.put("product", Arrays.asList("1234", "2345"));
        triggerFilterMap.put("id", Arrays.asList("1", "2"));
        FilterMap triggerFilter =
                new FilterMap.Builder().setAttributionFilterMap(triggerFilterMap).build();

        assertFalse(Filter.isFilterMatch(sourceFilter, List.of(triggerFilter), false));
    }

    @Test
    public void testIsFilterMatch_filterSet_withNegation_withEmptyValues_returnTrue() {
        Map<String, List<String>> sourceFilterMap = new HashMap<>();
        sourceFilterMap.put(
                "conversion_subdomain", Collections.singletonList("electronics.megastore"));
        sourceFilterMap.put("product", Arrays.asList("1234", "234"));
        sourceFilterMap.put("ctid", Collections.singletonList("id"));
        FilterMap sourceFilter =
                new FilterMap.Builder().setAttributionFilterMap(sourceFilterMap).build();

        Map<String, List<String>> triggerFilterMap1 = new HashMap<>();
        triggerFilterMap1.put("conversion_subdomain", Collections.emptyList());
        triggerFilterMap1.put("product", Collections.emptyList());
        FilterMap triggerFilter1 =
                new FilterMap.Builder().setAttributionFilterMap(triggerFilterMap1).build();

        Map<String, List<String>> triggerFilterMap2 = new HashMap<>();
        triggerFilterMap2.put("conversion_subdomain", Collections.singletonList("electronics"));
        triggerFilterMap2.put("product", Collections.emptyList());
        triggerFilterMap2.put("id", Arrays.asList("1", "2"));
        FilterMap triggerFilter2 =
                new FilterMap.Builder().setAttributionFilterMap(triggerFilterMap2).build();

        assertTrue(Filter.isFilterMatch(
                sourceFilter, List.of(triggerFilter1, triggerFilter2), false));
    }

    @Test
    public void testIsFilterMatch_withNegation_withEmptyValues_returnTrue() {
        Map<String, List<String>> sourceFilterMap = new HashMap<>();
        sourceFilterMap.put(
                "conversion_subdomain", Collections.singletonList("electronics.megastore"));
        sourceFilterMap.put("product", Arrays.asList("1234", "234"));
        sourceFilterMap.put("ctid", Collections.singletonList("id"));
        FilterMap sourceFilter =
                new FilterMap.Builder().setAttributionFilterMap(sourceFilterMap).build();

        Map<String, List<String>> triggerFilterMap = new HashMap<>();
        triggerFilterMap.put("conversion_subdomain", Collections.singletonList("electronics"));
        // Matches when negated
        triggerFilterMap.put("product", Collections.emptyList());
        triggerFilterMap.put("id", Arrays.asList("1", "2"));
        FilterMap triggerFilter =
                new FilterMap.Builder().setAttributionFilterMap(triggerFilterMap).build();

        assertTrue(Filter.isFilterMatch(sourceFilter, List.of(triggerFilter), false));
    }

    @Test
    public void testIsFilterMatch_filterSet_withNegation_withEmptyValues_returnFalse() {
        Map<String, List<String>> sourceFilterMap = new HashMap<>();
        sourceFilterMap.put("conversion_subdomain", Collections.emptyList());
        sourceFilterMap.put("product", Collections.emptyList());
        sourceFilterMap.put("ctid", Collections.singletonList("id"));
        FilterMap sourceFilter =
                new FilterMap.Builder().setAttributionFilterMap(sourceFilterMap).build();

        Map<String, List<String>> triggerFilterMap1 = new HashMap<>();
        // Doesn't match when negated
        triggerFilterMap1.put("conversion_subdomain", Collections.emptyList());
        triggerFilterMap1.put("product", Arrays.asList("3", "4"));
        triggerFilterMap1.put("id", Arrays.asList("1", "2"));
        FilterMap triggerFilter1 =
                new FilterMap.Builder().setAttributionFilterMap(triggerFilterMap1).build();

        Map<String, List<String>> triggerFilterMap2 = new HashMap<>();
        triggerFilterMap2.put(
                "conversion_subdomain", Collections.singletonList("electronics.megastore"));
        // Doesn't match when negated
        triggerFilterMap2.put("product", Collections.emptyList());
        triggerFilterMap2.put("id", Arrays.asList("1", "2"));
        FilterMap triggerFilter2 =
                new FilterMap.Builder().setAttributionFilterMap(triggerFilterMap2).build();

        assertFalse(Filter.isFilterMatch(
                sourceFilter, List.of(triggerFilter1, triggerFilter2), false));
    }

    @Test
    public void testIsFilterMatch_withNegation_withEmptyValues_returnFalse() {
        Map<String, List<String>> sourceFilterMap = new HashMap<>();
        sourceFilterMap.put(
                "conversion_subdomain", Collections.singletonList("electronics.megastore"));
        sourceFilterMap.put("product", Collections.emptyList());
        sourceFilterMap.put("ctid", Collections.singletonList("id"));
        FilterMap sourceFilter =
                new FilterMap.Builder().setAttributionFilterMap(sourceFilterMap).build();

        Map<String, List<String>> triggerFilterMap = new HashMap<>();
        triggerFilterMap.put(
                "conversion_subdomain", Collections.singletonList("electronics.megastore"));
        // Doesn't match when negated
        triggerFilterMap.put("product", Collections.emptyList());
        triggerFilterMap.put("id", Arrays.asList("1", "2"));
        FilterMap triggerFilter =
                new FilterMap.Builder().setAttributionFilterMap(triggerFilterMap).build();

        assertFalse(Filter.isFilterMatch(sourceFilter, List.of(triggerFilter), false));
    }
}

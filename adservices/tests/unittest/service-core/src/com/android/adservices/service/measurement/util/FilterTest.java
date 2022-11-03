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

import com.android.adservices.service.measurement.FilterData;

import org.junit.Test;

import java.util.Arrays;
import java.util.Collections;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

public class FilterTest {
    @Test
    public void testIsFilterMatch_nonEmptyValues_returnTrue() {
        Map<String, List<String>> sourceFilterMap = new HashMap<>();
        sourceFilterMap.put(
                "conversion_subdomain", Collections.singletonList("electronics.megastore"));
        sourceFilterMap.put("product", Arrays.asList("1234", "234"));
        sourceFilterMap.put("ctid", Collections.singletonList("id"));
        FilterData sourceFilter =
                new FilterData.Builder().setAttributionFilterMap(sourceFilterMap).build();

        Map<String, List<String>> triggerFilterMap = new HashMap<>();
        triggerFilterMap.put(
                "conversion_subdomain", Collections.singletonList("electronics.megastore"));
        triggerFilterMap.put("product", Arrays.asList("1234", "2345"));
        triggerFilterMap.put("id", Arrays.asList("1", "2"));
        FilterData triggerFilter =
                new FilterData.Builder().setAttributionFilterMap(triggerFilterMap).build();

        assertTrue(Filter.isFilterMatch(sourceFilter, triggerFilter, true));
    }

    @Test
    public void testIsFilterMatch_nonEmptyValues_returnFalse() {
        Map<String, List<String>> sourceFilterMap = new HashMap<>();
        sourceFilterMap.put(
                "conversion_subdomain", Collections.singletonList("electronics.megastore"));
        sourceFilterMap.put("product", Arrays.asList("1234", "234"));
        sourceFilterMap.put("ctid", Collections.singletonList("id"));
        FilterData sourceFilter =
                new FilterData.Builder().setAttributionFilterMap(sourceFilterMap).build();

        Map<String, List<String>> triggerFilterMap = new HashMap<>();
        triggerFilterMap.put(
                "conversion_subdomain", Collections.singletonList("electronics.megastore"));
        // Doesn't match
        triggerFilterMap.put("product", Arrays.asList("1", "2"));
        triggerFilterMap.put("id", Arrays.asList("1", "2"));
        FilterData triggerFilter =
                new FilterData.Builder().setAttributionFilterMap(triggerFilterMap).build();

        assertFalse(Filter.isFilterMatch(sourceFilter, triggerFilter, true));
    }

    @Test
    public void testIsFilterMatch_withEmptyValues_returnTrue() {
        Map<String, List<String>> sourceFilterMap = new HashMap<>();
        sourceFilterMap.put(
                "conversion_subdomain", Collections.singletonList("electronics.megastore"));
        sourceFilterMap.put("product", Collections.emptyList());
        sourceFilterMap.put("ctid", Collections.singletonList("id"));
        FilterData sourceFilter =
                new FilterData.Builder().setAttributionFilterMap(sourceFilterMap).build();

        Map<String, List<String>> triggerFilterMap = new HashMap<>();
        triggerFilterMap.put(
                "conversion_subdomain", Collections.singletonList("electronics.megastore"));
        triggerFilterMap.put("product", Collections.emptyList());
        triggerFilterMap.put("id", Arrays.asList("1", "2"));
        FilterData triggerFilter =
                new FilterData.Builder().setAttributionFilterMap(triggerFilterMap).build();

        assertTrue(Filter.isFilterMatch(sourceFilter, triggerFilter, true));
    }

    @Test
    public void testIsFilterMatch_withEmptyValues_returnFalse() {
        Map<String, List<String>> sourceFilterMap = new HashMap<>();
        sourceFilterMap.put(
                "conversion_subdomain", Collections.singletonList("electronics.megastore"));
        sourceFilterMap.put("product", Arrays.asList("1234", "234"));
        sourceFilterMap.put("ctid", Collections.singletonList("id"));
        FilterData sourceFilter =
                new FilterData.Builder().setAttributionFilterMap(sourceFilterMap).build();

        Map<String, List<String>> triggerFilterMap = new HashMap<>();
        triggerFilterMap.put(
                "conversion_subdomain", Collections.singletonList("electronics.megastore"));
        // Doesn't match
        triggerFilterMap.put("product", Collections.emptyList());
        triggerFilterMap.put("id", Arrays.asList("1", "2"));
        FilterData triggerFilter =
                new FilterData.Builder().setAttributionFilterMap(triggerFilterMap).build();

        assertFalse(Filter.isFilterMatch(sourceFilter, triggerFilter, true));
    }

    @Test
    public void testIsFilterMatch_withNegation_nonEmptyValues_returnTrue() {
        Map<String, List<String>> sourceFilterMap = new HashMap<>();
        sourceFilterMap.put(
                "conversion_subdomain", Collections.singletonList("electronics.megastore"));
        sourceFilterMap.put("product", Arrays.asList("1234", "234"));
        sourceFilterMap.put("ctid", Collections.singletonList("id"));
        FilterData sourceFilter =
                new FilterData.Builder().setAttributionFilterMap(sourceFilterMap).build();

        Map<String, List<String>> triggerFilterMap = new HashMap<>();
        triggerFilterMap.put("conversion_subdomain", Collections.singletonList("electronics"));
        // Doesn't match
        triggerFilterMap.put("product", Arrays.asList("1", "2"));
        triggerFilterMap.put("id", Arrays.asList("1", "2"));
        FilterData triggerFilter =
                new FilterData.Builder().setAttributionFilterMap(triggerFilterMap).build();
        assertTrue(Filter.isFilterMatch(sourceFilter, triggerFilter, false));
    }

    @Test
    public void testIsFilterMatch_withNegation_nonEmptyValues_returnFalse() {
        Map<String, List<String>> sourceFilterMap = new HashMap<>();
        sourceFilterMap.put(
                "conversion_subdomain", Collections.singletonList("electronics.megastore"));
        sourceFilterMap.put("product", Arrays.asList("1234", "234"));
        sourceFilterMap.put("ctid", Collections.singletonList("id"));
        FilterData sourceFilter =
                new FilterData.Builder().setAttributionFilterMap(sourceFilterMap).build();

        Map<String, List<String>> triggerFilterMap = new HashMap<>();
        triggerFilterMap.put(
                "conversion_subdomain", Collections.singletonList("electronics.megastore"));
        triggerFilterMap.put("product", Arrays.asList("1234", "2345"));
        triggerFilterMap.put("id", Arrays.asList("1", "2"));
        FilterData triggerFilter =
                new FilterData.Builder().setAttributionFilterMap(triggerFilterMap).build();

        assertFalse(Filter.isFilterMatch(sourceFilter, triggerFilter, false));
    }

    @Test
    public void testIsFilterMatch_withNegation_withEmptyValues_returnTrue() {
        Map<String, List<String>> sourceFilterMap = new HashMap<>();
        sourceFilterMap.put(
                "conversion_subdomain", Collections.singletonList("electronics.megastore"));
        sourceFilterMap.put("product", Arrays.asList("1234", "234"));
        sourceFilterMap.put("ctid", Collections.singletonList("id"));
        FilterData sourceFilter =
                new FilterData.Builder().setAttributionFilterMap(sourceFilterMap).build();

        Map<String, List<String>> triggerFilterMap = new HashMap<>();
        triggerFilterMap.put("conversion_subdomain", Collections.singletonList("electronics"));
        // Matches when negated
        triggerFilterMap.put("product", Collections.emptyList());
        triggerFilterMap.put("id", Arrays.asList("1", "2"));
        FilterData triggerFilter =
                new FilterData.Builder().setAttributionFilterMap(triggerFilterMap).build();
        assertTrue(Filter.isFilterMatch(sourceFilter, triggerFilter, false));
    }

    @Test
    public void testIsFilterMatch_withNegation_withEmptyValues_returnFalse() {
        Map<String, List<String>> sourceFilterMap = new HashMap<>();
        sourceFilterMap.put(
                "conversion_subdomain", Collections.singletonList("electronics.megastore"));
        sourceFilterMap.put("product", Collections.emptyList());
        sourceFilterMap.put("ctid", Collections.singletonList("id"));
        FilterData sourceFilter =
                new FilterData.Builder().setAttributionFilterMap(sourceFilterMap).build();

        Map<String, List<String>> triggerFilterMap = new HashMap<>();
        triggerFilterMap.put(
                "conversion_subdomain", Collections.singletonList("electronics.megastore"));
        // Doesn't match when negated
        triggerFilterMap.put("product", Collections.emptyList());
        triggerFilterMap.put("id", Arrays.asList("1", "2"));
        FilterData triggerFilter =
                new FilterData.Builder().setAttributionFilterMap(triggerFilterMap).build();

        assertFalse(Filter.isFilterMatch(sourceFilter, triggerFilter, false));
    }
}

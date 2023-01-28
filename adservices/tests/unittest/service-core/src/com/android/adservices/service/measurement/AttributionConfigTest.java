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

import android.util.Pair;

import androidx.test.filters.SmallTest;

import org.junit.Test;

import java.util.Arrays;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;

/** Unit tests for {@link AttributionConfig} */
@SmallTest
public final class AttributionConfigTest {

    @Test
    public void testCreation() throws Exception {
        AttributionConfig attributionConfig = createExample();

        assertEquals("AdTech1-Ads", attributionConfig.getSourceAdtech());
        assertEquals(100L, attributionConfig.getSourcePriorityRange().first.longValue());
        assertEquals(1000L, attributionConfig.getSourcePriorityRange().second.longValue());
        List<FilterMap> sourceFilter = attributionConfig.getSourceFilters();
        assertEquals(1, sourceFilter.get(0).getAttributionFilterMap().get("campaign_type").size());
        assertEquals(1, sourceFilter.get(0).getAttributionFilterMap().get("source_type").size());
        List<FilterMap> sourceNotFilter = attributionConfig.getSourceNotFilters();
        assertEquals(
                1, sourceNotFilter.get(0).getAttributionFilterMap().get("campaign_type").size());
        assertEquals(600000L, attributionConfig.getSourceExpiryOverride().longValue());
        assertEquals(99L, attributionConfig.getPriority().longValue());
        assertEquals(604800L, attributionConfig.getExpiry().longValue());
        List<FilterMap> filterData = attributionConfig.getFilterData();
        assertEquals(1, filterData.get(0).getAttributionFilterMap().get("campaign_type").size());
        assertEquals(100000L, attributionConfig.getPostInstallExclusivityWindow().longValue());
    }

    @Test
    public void testDefaults() throws Exception {
        AttributionConfig attributionConfig =
                new AttributionConfig.Builder().setSourceAdtech("AdTech1-Ads").build();
        assertNotNull(attributionConfig.getSourceAdtech());
        assertNull(attributionConfig.getSourcePriorityRange());
        assertNull(attributionConfig.getSourceFilters());
        assertNull(attributionConfig.getSourceNotFilters());
        assertNull(attributionConfig.getSourceExpiryOverride());
        assertNull(attributionConfig.getPriority());
        assertNull(attributionConfig.getExpiry());
        assertNull(attributionConfig.getFilterData());
        assertNull(attributionConfig.getPostInstallExclusivityWindow());
    }

    @Test
    public void testHashCode_equals() throws Exception {
        final AttributionConfig config1 = createExample();
        final AttributionConfig config2 = createExample();
        final Set<AttributionConfig> configSet1 = Set.of(config1);
        final Set<AttributionConfig> configSet2 = Set.of(config2);
        assertEquals(config1.hashCode(), config2.hashCode());
        assertEquals(config1, config2);
        assertEquals(configSet1, configSet2);
    }

    @Test
    public void testHashCode_notEquals() throws Exception {
        final AttributionConfig config1 = createExample();

        Pair<Long, Long> sourcePriorityRange = new Pair<>(100L, 1000L);

        Map<String, List<String>> sourceFiltersMap = new HashMap<>();
        sourceFiltersMap.put("campaign_type", Arrays.asList("install"));
        sourceFiltersMap.put("source_type", Arrays.asList("navigation"));
        FilterMap sourceFilters =
                new FilterMap.Builder().setAttributionFilterMap(sourceFiltersMap).build();

        Map<String, List<String>> sourceNotFiltersMap = new HashMap<>();
        sourceNotFiltersMap.put("campaign_type", Arrays.asList("product"));
        FilterMap sourceNotFilters =
                new FilterMap.Builder().setAttributionFilterMap(sourceNotFiltersMap).build();

        Map<String, List<String>> filterDataMap = new HashMap<>();
        filterDataMap.put("campaign_type", Arrays.asList("install"));
        FilterMap filterData =
                new FilterMap.Builder().setAttributionFilterMap(filterDataMap).build();

        AttributionConfig config2 =
                new AttributionConfig.Builder()
                        .setSourceAdtech("AdTech2-Ads")
                        .setSourcePriorityRange(sourcePriorityRange)
                        .setSourceFilters(List.of(sourceFilters))
                        .setSourceNotFilters(List.of(sourceNotFilters))
                        .setSourceExpiryOverride(600000L)
                        .setPriority(99L)
                        .setExpiry(604800L)
                        .setFilterData(List.of(filterData))
                        .setPostInstallExclusivityWindow(100000L)
                        .build();

        final Set<AttributionConfig> configSet1 = Set.of(config1);
        final Set<AttributionConfig> configSet2 = Set.of(config2);
        assertNotEquals(config1.hashCode(), config2.hashCode());
        assertNotEquals(config1, config2);
        assertNotEquals(configSet1, configSet2);
    }

    private AttributionConfig createExample() {
        Pair<Long, Long> sourcePriorityRange = new Pair<>(100L, 1000L);

        Map<String, List<String>> sourceFiltersMap = new HashMap<>();
        sourceFiltersMap.put("campaign_type", Arrays.asList("install"));
        sourceFiltersMap.put("source_type", Arrays.asList("navigation"));
        FilterMap sourceFilters =
                new FilterMap.Builder().setAttributionFilterMap(sourceFiltersMap).build();

        Map<String, List<String>> sourceNotFiltersMap = new HashMap<>();
        sourceNotFiltersMap.put("campaign_type", Arrays.asList("product"));
        FilterMap sourceNotFilters =
                new FilterMap.Builder().setAttributionFilterMap(sourceNotFiltersMap).build();

        Map<String, List<String>> filterDataMap = new HashMap<>();
        filterDataMap.put("campaign_type", Arrays.asList("install"));
        FilterMap filterData =
                new FilterMap.Builder().setAttributionFilterMap(filterDataMap).build();

        return new AttributionConfig.Builder()
                .setSourceAdtech("AdTech1-Ads")
                .setSourcePriorityRange(sourcePriorityRange)
                .setSourceFilters(List.of(sourceFilters))
                .setSourceNotFilters(List.of(sourceNotFilters))
                .setSourceExpiryOverride(600000L)
                .setPriority(99L)
                .setExpiry(604800L)
                .setFilterData(List.of(filterData))
                .setPostInstallExclusivityWindow(100000L)
                .build();
    }
}

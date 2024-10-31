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

import static com.google.common.truth.Truth.assertThat;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertNotEquals;
import static org.junit.Assert.assertTrue;
import static org.mockito.Mockito.when;

import androidx.test.filters.SmallTest;

import com.android.adservices.service.Flags;
import com.android.adservices.service.measurement.FilterMap;
import com.android.adservices.service.measurement.util.Filter;
import com.android.adservices.service.measurement.util.UnsignedLong;

import org.json.JSONArray;
import org.json.JSONObject;
import org.junit.Test;
import org.junit.runner.RunWith;
import org.mockito.Mock;
import org.mockito.junit.MockitoJUnitRunner;

import java.math.BigInteger;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collections;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.Set;

/** Unit tests for {@link AggregatableAttributionTrigger} */
@SmallTest
@RunWith(MockitoJUnitRunner.class)
public final class AggregatableAttributionTriggerTest {
    @Mock Flags mFlags;

    private static final String BUDGET_NAME1 = "BUDGET1";
    private static final String BUDGET_NAME2 = "BUDGET2";

    private List<AggregateTriggerData> createAggregateTriggerData() {
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
        return List.of(attributionTriggerData1, attributionTriggerData2);
    }

    private AggregatableAttributionTrigger createExampleWithValues(
            List<AggregateDeduplicationKey> aggregateDeduplicationKeys) throws Exception {
        List<AggregateTriggerData> aggregateTriggerDataList = createAggregateTriggerData();
        AggregateTriggerData attributionTriggerData1 = aggregateTriggerDataList.get(0);
        AggregateTriggerData attributionTriggerData2 = aggregateTriggerDataList.get(1);
        Map<String, AggregatableKeyValue> values = new HashMap<>();
        values.put("campCounts", new AggregatableKeyValue.Builder(1).build());
        values.put("campGeoCounts", new AggregatableKeyValue.Builder(100).build());
        List<AggregatableValuesConfig> configList = new ArrayList<>();
        configList.add(new AggregatableValuesConfig.Builder(values).build());
        if (aggregateDeduplicationKeys != null) {
            return new AggregatableAttributionTrigger.Builder()
                    .setTriggerData(Arrays.asList(attributionTriggerData1, attributionTriggerData2))
                    .setValueConfigs(configList)
                    .setAggregateDeduplicationKeys(aggregateDeduplicationKeys)
                    .build();
        }
        return new AggregatableAttributionTrigger.Builder()
                .setTriggerData(Arrays.asList(attributionTriggerData1, attributionTriggerData2))
                .setValueConfigs(configList)
                .build();
    }

    private AggregatableAttributionTrigger createExampleWithValues(
            List<AggregateDeduplicationKey> aggregateDeduplicationKeys,
            List<AggregatableNamedBudget> aggregatableNamedBudgets)
            throws Exception {
        List<AggregateTriggerData> aggregateTriggerDataList = createAggregateTriggerData();
        AggregateTriggerData attributionTriggerData1 = aggregateTriggerDataList.get(0);
        AggregateTriggerData attributionTriggerData2 = aggregateTriggerDataList.get(1);
        Map<String, AggregatableKeyValue> values = new HashMap<>();
        values.put("campCounts", new AggregatableKeyValue.Builder(1).build());
        values.put("campGeoCounts", new AggregatableKeyValue.Builder(100).build());
        List<AggregatableValuesConfig> configList = new ArrayList<>();
        configList.add(new AggregatableValuesConfig.Builder(values).build());
        AggregatableAttributionTrigger.Builder builder =
                new AggregatableAttributionTrigger.Builder()
                        .setTriggerData(
                                Arrays.asList(attributionTriggerData1, attributionTriggerData2))
                        .setValueConfigs(configList);
        if (aggregateDeduplicationKeys != null) {
            builder.setAggregateDeduplicationKeys(aggregateDeduplicationKeys);
        }
        if (aggregatableNamedBudgets != null) {
            builder.setNamedBudgets(aggregatableNamedBudgets);
        }
        return builder.build();
    }

    private AggregatableAttributionTrigger createExampleWithValueConfigs() throws Exception {
        when(mFlags.getMeasurementEnableAggregateValueFilters()).thenReturn(true);
        List<AggregateTriggerData> aggregateTriggerDataList = createAggregateTriggerData();
        AggregateTriggerData attributionTriggerData1 = aggregateTriggerDataList.get(0);
        AggregateTriggerData attributionTriggerData2 = aggregateTriggerDataList.get(1);
        // Build AggregatableValuesConfig
        JSONObject jsonObj1Values = new JSONObject();
        jsonObj1Values.put("campCounts", 1);
        jsonObj1Values.put("campGeoCounts", 100);
        // Build filter_set and not_filter_set
        JSONObject filterMap = new JSONObject();
        filterMap.put(
                "conversion_subdomain", new JSONArray(Arrays.asList("electronics.megastore")));
        filterMap.put("product", new JSONArray(Arrays.asList("1234", "2345")));
        JSONArray filterSet = new JSONArray();
        filterSet.put(filterMap);
        // Put into json object
        JSONObject jsonObj = new JSONObject();
        jsonObj.put("values", jsonObj1Values);
        jsonObj.put("filter_set", filterMap);
        jsonObj.put("not_filter_set", filterMap);
        AggregatableValuesConfig aggregatableValuesConfig =
                new AggregatableValuesConfig.Builder(jsonObj, mFlags).build();
        List<AggregatableValuesConfig> aggregatableValuesConfigList = new ArrayList<>();
        aggregatableValuesConfigList.add(aggregatableValuesConfig);
        return new AggregatableAttributionTrigger.Builder()
                .setTriggerData(Arrays.asList(attributionTriggerData1, attributionTriggerData2))
                .setValueConfigs(aggregatableValuesConfigList)
                .build();
    }

    @Test
    public void testCreationWithValues() throws Exception {
        AggregatableAttributionTrigger attributionTrigger = createExampleWithValues(null);

        assertThat(attributionTrigger.getTriggerData().size()).isEqualTo(2);
        assertThat(attributionTrigger.getTriggerData().get(0).getKey().longValue()).isEqualTo(159L);
        assertThat(attributionTrigger.getTriggerData().get(0).getSourceKeys().size()).isEqualTo(2);
        assertThat(attributionTrigger.getTriggerData().get(1).getKey().longValue()).isEqualTo(5L);
        assertThat(attributionTrigger.getTriggerData().get(1).getSourceKeys().size()).isEqualTo(3);
        assertThat(
                        attributionTrigger
                                .getValueConfigs()
                                .get(0)
                                .getValues()
                                .get("campCounts")
                                .getValue())
                .isEqualTo(1);
        assertThat(
                        attributionTrigger
                                .getValueConfigs()
                                .get(0)
                                .getValues()
                                .get("campGeoCounts")
                                .getValue())
                .isEqualTo(100);
    }

    @Test
    public void testCreationWithValueConfigs() throws Exception {
        AggregatableAttributionTrigger attributionTrigger = createExampleWithValueConfigs();
        assertThat(attributionTrigger.getTriggerData().size()).isEqualTo(2);
        assertThat(attributionTrigger.getTriggerData().get(0).getKey().longValue()).isEqualTo(159L);
        assertThat(attributionTrigger.getTriggerData().get(0).getSourceKeys().size()).isEqualTo(2);
        assertThat(attributionTrigger.getTriggerData().get(1).getKey().longValue()).isEqualTo(5L);
        assertThat(attributionTrigger.getTriggerData().get(1).getSourceKeys().size()).isEqualTo(3);
        assertThat(
                        attributionTrigger
                                .getValueConfigs()
                                .get(0)
                                .getValues()
                                .get("campCounts")
                                .getValue())
                .isEqualTo(1);
        assertThat(
                        attributionTrigger
                                .getValueConfigs()
                                .get(0)
                                .getValues()
                                .get("campGeoCounts")
                                .getValue())
                .isEqualTo(100);
    }

    @Test
    public void testDefaults() throws Exception {
        AggregatableAttributionTrigger attributionTrigger =
                new AggregatableAttributionTrigger.Builder().build();
        assertEquals(attributionTrigger.getTriggerData().size(), 0);
    }

    @Test
    public void testGetNamedBudgets() throws Exception {
        JSONObject filterMap1 = new JSONObject();
        filterMap1.put("2", new JSONArray(Arrays.asList("1234", "234")));
        JSONArray filterSet1 = new JSONArray();
        filterSet1.put(filterMap1);
        JSONObject budgetObj1 = new JSONObject();
        budgetObj1.put(AggregatableNamedBudget.NamedBudgetContract.NAME, "biddable");
        budgetObj1.put(Filter.FilterContract.FILTERS, filterSet1);
        AggregatableNamedBudget aggregatableNamedBudget1 =
                new AggregatableNamedBudget(budgetObj1, mFlags);

        JSONObject filterMap2 = new JSONObject();
        filterMap2.put("2", new JSONArray(Arrays.asList("5678", "678")));
        JSONArray filterSet2 = new JSONArray();
        filterSet2.put(filterMap2);
        JSONObject budgetObj2 = new JSONObject();
        budgetObj2.put(AggregatableNamedBudget.NamedBudgetContract.NAME, "nonbiddable");
        budgetObj2.put(Filter.FilterContract.FILTERS, filterSet2);
        AggregatableNamedBudget aggregatableNamedBudget2 =
                new AggregatableNamedBudget(budgetObj2, mFlags);

        List<AggregatableNamedBudget> aggregatableNamedBudgets =
                createExampleWithValues(
                                /* aggregateDeduplicationKeys= */ null,
                                Arrays.asList(aggregatableNamedBudget1, aggregatableNamedBudget2))
                        .getNamedBudgets();
        assertThat(aggregatableNamedBudgets).isNotNull();
        assertThat(aggregatableNamedBudgets)
                .isEqualTo(List.of(aggregatableNamedBudget1, aggregatableNamedBudget2));
    }

    @Test
    public void testGetNamedBudgets_nullNamedBudget() throws Exception {
        AggregatableAttributionTrigger attributionTrigger =
                createExampleWithValues(
                        /* aggregateDeduplicationKeys= */ null,
                        /* aggregatableNamedBudgets= */ null);

        assertThat(attributionTrigger.getNamedBudgets()).isNull();
    }

    @Test
    public void testHashCode_equals() throws Exception {
        final AggregatableAttributionTrigger attributionTrigger1 = createExampleWithValues(null);
        final AggregatableAttributionTrigger attributionTrigger2 = createExampleWithValues(null);
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
        final AggregatableAttributionTrigger attributionTrigger1 = createExampleWithValues(null);

        AggregateTriggerData attributionTriggerData1 =
                new AggregateTriggerData.Builder()
                        .setKey(BigInteger.valueOf(159L))
                        .setSourceKeys(new HashSet<>(Arrays.asList("campCounts", "campGeoCounts")))
                        .build();
        Map<String, AggregatableKeyValue> values = new HashMap<>();
        values.put("campCounts", new AggregatableKeyValue.Builder(1).build());
        values.put("campGeoCounts", new AggregatableKeyValue.Builder(100).build());

        final AggregatableAttributionTrigger attributionTrigger2 =
                new AggregatableAttributionTrigger.Builder()
                        .setTriggerData(Arrays.asList(attributionTriggerData1))
                        .setValueConfigs(
                                List.of(new AggregatableValuesConfig.Builder(values).build()))
                        .build();
        final Set<AggregatableAttributionTrigger> attributionTriggerSet1 =
                Set.of(attributionTrigger1);
        final Set<AggregatableAttributionTrigger> attributionTriggerSet2 =
                Set.of(attributionTrigger2);
        assertNotEquals(attributionTrigger1.hashCode(), attributionTrigger2.hashCode());
        assertNotEquals(attributionTrigger1, attributionTrigger2);
        assertNotEquals(attributionTriggerSet1, attributionTriggerSet2);
    }

    @Test
    public void testExtractDedupKey_bothKeysHaveMatchingFilters() throws Exception {
        Map<String, List<String>> triggerFilterMap1 = new HashMap<>();
        triggerFilterMap1.put(
                "conversion_subdomain", Collections.singletonList("electronics.megastore"));
        triggerFilterMap1.put("product", Arrays.asList("1234", "234"));
        AggregateDeduplicationKey aggregateDeduplicationKey1 =
                new AggregateDeduplicationKey.Builder()
                        .setDeduplicationKey(new UnsignedLong(10L))
                        .setFilterSet(
                                Collections.singletonList(
                                        new FilterMap.Builder()
                                                .setAttributionFilterMap(triggerFilterMap1)
                                                .build()))
                        .build();
        Map<String, List<String>> triggerFilterMap2 = new HashMap<>();
        triggerFilterMap2.put(
                "conversion_subdomain", Collections.singletonList("electronics.megastore"));
        triggerFilterMap2.put("product", Arrays.asList("1234", "234"));
        AggregateDeduplicationKey aggregateDeduplicationKey2 =
                new AggregateDeduplicationKey.Builder()
                        .setDeduplicationKey(new UnsignedLong(11L))
                        .setFilterSet(
                                Collections.singletonList(
                                        new FilterMap.Builder()
                                                .setAttributionFilterMap(triggerFilterMap2)
                                                .build()))
                        .build();

        Map<String, List<String>> sourceFilterMap = new HashMap<>();
        sourceFilterMap.put(
                "conversion_subdomain", Collections.singletonList("electronics.megastore"));
        sourceFilterMap.put("product", Arrays.asList("1234", "234"));
        FilterMap sourceFilter =
                new FilterMap.Builder().setAttributionFilterMap(sourceFilterMap).build();

        Optional<AggregateDeduplicationKey> aggregateDeduplicationKey =
                createExampleWithValues(
                                Arrays.asList(
                                        aggregateDeduplicationKey1, aggregateDeduplicationKey2))
                        .maybeExtractDedupKey(sourceFilter, mFlags);
        assertTrue(aggregateDeduplicationKey.isPresent());
        assertEquals(aggregateDeduplicationKey1, aggregateDeduplicationKey.get());
    }

    @Test
    public void testExtractDedupKey_secondKeyMatches_firstKeyHasInvalidFilters() throws Exception {
        Map<String, List<String>> triggerFilterMap1 = new HashMap<>();
        triggerFilterMap1.put(
                "conversion_subdomain", Collections.singletonList("electronics.ministore"));
        triggerFilterMap1.put("product", Arrays.asList("1234", "234"));
        Map<String, List<String>> notTriggerFilterMap1 = new HashMap<>();
        notTriggerFilterMap1.put(
                "conversion_subdomain", Collections.singletonList("electronics.megastore"));
        notTriggerFilterMap1.put("product", Arrays.asList("856", "23"));

        AggregateDeduplicationKey aggregateDeduplicationKey1 =
                new AggregateDeduplicationKey.Builder()
                        .setDeduplicationKey(new UnsignedLong(10L))
                        .setFilterSet(
                                Collections.singletonList(
                                        new FilterMap.Builder()
                                                .setAttributionFilterMap(triggerFilterMap1)
                                                .build()))
                        .setFilterSet(
                                Collections.singletonList(
                                        new FilterMap.Builder()
                                                .setAttributionFilterMap(notTriggerFilterMap1)
                                                .build()))
                        .build();

        Map<String, List<String>> triggerFilterMap2 = new HashMap<>();
        triggerFilterMap2.put(
                "conversion_subdomain", Collections.singletonList("electronics.megastore"));
        triggerFilterMap2.put("product", Arrays.asList("1234", "234"));
        Map<String, List<String>> notTriggerFilterMap2 = new HashMap<>();
        notTriggerFilterMap2.put(
                "conversion_subdomain", Collections.singletonList("electronics.ministore"));
        notTriggerFilterMap2.put("product", Arrays.asList("856", "23"));
        AggregateDeduplicationKey aggregateDeduplicationKey2 =
                new AggregateDeduplicationKey.Builder()
                        .setDeduplicationKey(new UnsignedLong(11L))
                        .setFilterSet(
                                Collections.singletonList(
                                        new FilterMap.Builder()
                                                .setAttributionFilterMap(triggerFilterMap2)
                                                .build()))
                        .setNotFilterSet(
                                Collections.singletonList(
                                        new FilterMap.Builder()
                                                .setAttributionFilterMap(notTriggerFilterMap2)
                                                .build()))
                        .build();

        Map<String, List<String>> sourceFilterMap = new HashMap<>();
        sourceFilterMap.put(
                "conversion_subdomain", Collections.singletonList("electronics.megastore"));
        sourceFilterMap.put("product", Arrays.asList("1234", "234"));
        FilterMap sourceFilter =
                new FilterMap.Builder().setAttributionFilterMap(sourceFilterMap).build();

        Optional<AggregateDeduplicationKey> aggregateDeduplicationKey =
                createExampleWithValues(
                                Arrays.asList(
                                        aggregateDeduplicationKey1, aggregateDeduplicationKey2))
                        .maybeExtractDedupKey(sourceFilter, mFlags);
        assertTrue(aggregateDeduplicationKey.isPresent());
        assertEquals(aggregateDeduplicationKey2, aggregateDeduplicationKey.get());
    }

    @Test
    public void testExtractDedupKey_secondKeyMatches_firstKeyHasInvalidNotFilters()
            throws Exception {
        Map<String, List<String>> triggerFilterMap1 = new HashMap<>();
        triggerFilterMap1.put("product", Arrays.asList("1234", "234"));
        Map<String, List<String>> notTriggerFilterMap1 = new HashMap<>();
        notTriggerFilterMap1.put(
                "conversion_subdomain", Collections.singletonList("electronics.megastore"));
        AggregateDeduplicationKey aggregateDeduplicationKey1 =
                new AggregateDeduplicationKey.Builder()
                        .setDeduplicationKey(new UnsignedLong(10L))
                        .setFilterSet(
                                Collections.singletonList(
                                        new FilterMap.Builder()
                                                .setAttributionFilterMap(triggerFilterMap1)
                                                .build()))
                        .setNotFilterSet(
                                Collections.singletonList(
                                        new FilterMap.Builder()
                                                .setAttributionFilterMap(notTriggerFilterMap1)
                                                .build()))
                        .build();

        Map<String, List<String>> triggerFilterMap2 = new HashMap<>();
        triggerFilterMap2.put(
                "conversion_subdomain", Collections.singletonList("electronics.megastore"));
        triggerFilterMap2.put("product", Arrays.asList("1234", "234"));
        Map<String, List<String>> notTriggerFilterMap2 = new HashMap<>();
        notTriggerFilterMap2.put(
                "conversion_subdomain", Collections.singletonList("electronics.ministore"));
        notTriggerFilterMap2.put("product", Arrays.asList("856", "23"));
        AggregateDeduplicationKey aggregateDeduplicationKey2 =
                new AggregateDeduplicationKey.Builder()
                        .setDeduplicationKey(new UnsignedLong(11L))
                        .setFilterSet(
                                Collections.singletonList(
                                        new FilterMap.Builder()
                                                .setAttributionFilterMap(triggerFilterMap2)
                                                .build()))
                        .setNotFilterSet(
                                Collections.singletonList(
                                        new FilterMap.Builder()
                                                .setAttributionFilterMap(notTriggerFilterMap2)
                                                .build()))
                        .build();

        Map<String, List<String>> sourceFilterMap = new HashMap<>();
        sourceFilterMap.put(
                "conversion_subdomain", Collections.singletonList("electronics.megastore"));
        sourceFilterMap.put("product", Arrays.asList("1234", "234"));
        FilterMap sourceFilter =
                new FilterMap.Builder().setAttributionFilterMap(sourceFilterMap).build();

        Optional<AggregateDeduplicationKey> aggregateDeduplicationKey =
                createExampleWithValues(
                                Arrays.asList(
                                        aggregateDeduplicationKey1, aggregateDeduplicationKey2))
                        .maybeExtractDedupKey(sourceFilter, mFlags);
        assertTrue(aggregateDeduplicationKey.isPresent());
        assertEquals(aggregateDeduplicationKey2, aggregateDeduplicationKey.get());
    }

    @Test
    public void testExtractDedupKey_noFiltersInFirstKey() throws Exception {
        AggregateDeduplicationKey aggregateDeduplicationKey1 =
                new AggregateDeduplicationKey.Builder()
                        .setDeduplicationKey(new UnsignedLong(10L))
                        .build();
        Map<String, List<String>> triggerFilterMap2 = new HashMap<>();
        triggerFilterMap2.put(
                "conversion_subdomain", Collections.singletonList("electronics.megastore"));
        triggerFilterMap2.put("product", Arrays.asList("1234", "234"));
        AggregateDeduplicationKey aggregateDeduplicationKey2 =
                new AggregateDeduplicationKey.Builder()
                        .setDeduplicationKey(new UnsignedLong(11L))
                        .setFilterSet(
                                Collections.singletonList(
                                        new FilterMap.Builder()
                                                .setAttributionFilterMap(triggerFilterMap2)
                                                .build()))
                        .build();

        Map<String, List<String>> sourceFilterMap = new HashMap<>();
        sourceFilterMap.put(
                "conversion_subdomain", Collections.singletonList("electronics.megastore"));
        sourceFilterMap.put("product", Arrays.asList("1234", "234"));
        FilterMap sourceFilter =
                new FilterMap.Builder().setAttributionFilterMap(sourceFilterMap).build();

        Optional<AggregateDeduplicationKey> aggregateDeduplicationKey =
                createExampleWithValues(
                                Arrays.asList(
                                        aggregateDeduplicationKey1, aggregateDeduplicationKey2))
                        .maybeExtractDedupKey(sourceFilter, mFlags);
        assertTrue(aggregateDeduplicationKey.isPresent());
        assertEquals(aggregateDeduplicationKey1, aggregateDeduplicationKey.get());
    }

    @Test
    public void testExtractDedupKey_noKeysMatch() throws Exception {
        Map<String, List<String>> triggerFilterMap1 = new HashMap<>();
        triggerFilterMap1.put(
                "conversion_subdomain", Collections.singletonList("electronics.ministore"));
        triggerFilterMap1.put("product", Arrays.asList("4321", "432"));
        AggregateDeduplicationKey aggregateDeduplicationKey1 =
                new AggregateDeduplicationKey.Builder()
                        .setDeduplicationKey(new UnsignedLong(10L))
                        .setFilterSet(
                                Collections.singletonList(
                                        new FilterMap.Builder()
                                                .setAttributionFilterMap(triggerFilterMap1)
                                                .build()))
                        .build();
        Map<String, List<String>> triggerFilterMap2 = new HashMap<>();
        triggerFilterMap2.put(
                "conversion_subdomain", Collections.singletonList("electronics.store"));
        triggerFilterMap2.put("product", Arrays.asList("9876", "654"));
        AggregateDeduplicationKey aggregateDeduplicationKey2 =
                new AggregateDeduplicationKey.Builder()
                        .setDeduplicationKey(new UnsignedLong(11L))
                        .setFilterSet(
                                Collections.singletonList(
                                        new FilterMap.Builder()
                                                .setAttributionFilterMap(triggerFilterMap2)
                                                .build()))
                        .build();

        Map<String, List<String>> sourceFilterMap = new HashMap<>();
        sourceFilterMap.put(
                "conversion_subdomain", Collections.singletonList("electronics.megastore"));
        sourceFilterMap.put("product", Arrays.asList("1234", "234"));
        FilterMap sourceFilter =
                new FilterMap.Builder().setAttributionFilterMap(sourceFilterMap).build();

        Optional<AggregateDeduplicationKey> aggregateDeduplicationKey =
                createExampleWithValues(
                                Arrays.asList(
                                        aggregateDeduplicationKey1, aggregateDeduplicationKey2))
                        .maybeExtractDedupKey(sourceFilter, mFlags);
        assertTrue(aggregateDeduplicationKey.isEmpty());
    }

    @Test
    public void testExtractDedupKey_secondKeyMatches_nullDedupKey() throws Exception {
        Map<String, List<String>> triggerFilterMap1 = new HashMap<>();
        triggerFilterMap1.put("product", Arrays.asList("1234", "234"));
        Map<String, List<String>> notTriggerFilterMap1 = new HashMap<>();
        notTriggerFilterMap1.put(
                "conversion_subdomain", Collections.singletonList("electronics.megastore"));
        AggregateDeduplicationKey aggregateDeduplicationKey1 =
                new AggregateDeduplicationKey.Builder()
                        .setDeduplicationKey(new UnsignedLong(10L))
                        .setFilterSet(
                                Collections.singletonList(
                                        new FilterMap.Builder()
                                                .setAttributionFilterMap(triggerFilterMap1)
                                                .build()))
                        .setNotFilterSet(
                                Collections.singletonList(
                                        new FilterMap.Builder()
                                                .setAttributionFilterMap(notTriggerFilterMap1)
                                                .build()))
                        .build();

        Map<String, List<String>> triggerFilterMap2 = new HashMap<>();
        triggerFilterMap2.put(
                "conversion_subdomain", Collections.singletonList("electronics.megastore"));
        triggerFilterMap2.put("product", Arrays.asList("1234", "234"));
        Map<String, List<String>> notTriggerFilterMap2 = new HashMap<>();
        notTriggerFilterMap2.put(
                "conversion_subdomain", Collections.singletonList("electronics.ministore"));
        notTriggerFilterMap2.put("product", Arrays.asList("856", "23"));
        AggregateDeduplicationKey aggregateDeduplicationKey2 =
                new AggregateDeduplicationKey.Builder()
                        .setFilterSet(
                                Collections.singletonList(
                                        new FilterMap.Builder()
                                                .setAttributionFilterMap(triggerFilterMap2)
                                                .build()))
                        .setNotFilterSet(
                                Collections.singletonList(
                                        new FilterMap.Builder()
                                                .setAttributionFilterMap(notTriggerFilterMap2)
                                                .build()))
                        .build();

        Map<String, List<String>> sourceFilterMap = new HashMap<>();
        sourceFilterMap.put(
                "conversion_subdomain", Collections.singletonList("electronics.megastore"));
        sourceFilterMap.put("product", Arrays.asList("1234", "234"));
        FilterMap sourceFilter =
                new FilterMap.Builder().setAttributionFilterMap(sourceFilterMap).build();

        Optional<AggregateDeduplicationKey> aggregateDeduplicationKey =
                createExampleWithValues(
                                Arrays.asList(
                                        aggregateDeduplicationKey1, aggregateDeduplicationKey2))
                        .maybeExtractDedupKey(sourceFilter, mFlags);
        assertTrue(aggregateDeduplicationKey.isEmpty());
    }

    @Test
    public void testExtractDedupKey_lookbackWindowEnabledAndEmptyDedupKeys_returnsEmpty()
            throws Exception {
        when(mFlags.getMeasurementEnableLookbackWindowFilter()).thenReturn(true);
        AggregateDeduplicationKey aggregateDeduplicationKey1 =
                new AggregateDeduplicationKey.Builder()
                        .setFilterSet(
                                Collections.singletonList(
                                        new FilterMap.Builder()
                                                .addStringListValue(
                                                        "conversion_subdomain",
                                                        List.of("electronics.megastore"))
                                                .addStringListValue(
                                                        "product", List.of("1234", "234"))
                                                .build()))
                        .build();
        AggregateDeduplicationKey aggregateDeduplicationKey2 =
                new AggregateDeduplicationKey.Builder()
                        .setFilterSet(
                                Collections.singletonList(
                                        new FilterMap.Builder()
                                                .addStringListValue(
                                                        "conversion_subdomain",
                                                        List.of("electronics.store"))
                                                .addStringListValue(
                                                        "product", List.of("9876", "654"))
                                                .build()))
                        .build();

        FilterMap sourceFilter =
                new FilterMap.Builder()
                        .addStringListValue(
                                "conversion_subdomain", List.of("electronics.megastore"))
                        .addStringListValue("product", List.of("1234", "234"))
                        .build();

        Optional<AggregateDeduplicationKey> aggregateDeduplicationKey =
                createExampleWithValues(
                                Arrays.asList(
                                        aggregateDeduplicationKey1, aggregateDeduplicationKey2))
                        .maybeExtractDedupKey(sourceFilter, mFlags);
        assertTrue(aggregateDeduplicationKey.isEmpty());
    }

    @Test
    public void testExtractNamedBudget_bothNamedBudgetsHaveMatchingFilters() throws Exception {
        // Set up
        JSONObject budgetObj1 = new JSONObject();
        budgetObj1.put(AggregatableNamedBudget.NamedBudgetContract.NAME, BUDGET_NAME1);
        JSONObject filterMap1 = new JSONObject();
        filterMap1.put("1", new JSONArray(Arrays.asList("1234", "234")));
        JSONArray filterSet1 = new JSONArray();
        filterSet1.put(filterMap1);
        budgetObj1.put(Filter.FilterContract.FILTERS, filterSet1);
        AggregatableNamedBudget aggregatableNamedBudget1 =
                new AggregatableNamedBudget(budgetObj1, mFlags);

        JSONObject budgetObj2 = new JSONObject();
        budgetObj2.put(AggregatableNamedBudget.NamedBudgetContract.NAME, BUDGET_NAME2);
        JSONObject filterMap2 = new JSONObject();
        filterMap2.put("1", new JSONArray(Arrays.asList("1234", "234")));
        JSONArray filterSet2 = new JSONArray();
        filterSet2.put(filterMap2);
        budgetObj2.put(Filter.FilterContract.FILTERS, filterSet2);
        AggregatableNamedBudget aggregatableNamedBudget2 =
                new AggregatableNamedBudget(budgetObj2, mFlags);

        Map<String, List<String>> sourceFilterMap = new HashMap<>();
        sourceFilterMap.put("1", Arrays.asList("1234", "234"));
        FilterMap sourceFilter =
                new FilterMap.Builder().setAttributionFilterMap(sourceFilterMap).build();

        // Execution
        Optional<String> matchedNamedBudget =
                createExampleWithValues(
                                /* aggregateDeduplicationKeys= */ null,
                                Arrays.asList(aggregatableNamedBudget1, aggregatableNamedBudget2))
                        .maybeExtractNamedBudget(sourceFilter, mFlags);

        // Assertion
        assertThat(matchedNamedBudget).isPresent();
        assertThat(matchedNamedBudget.get()).isEqualTo(BUDGET_NAME1);
    }

    @Test
    public void testExtractNamedBudget_firstNamedBudgetHasUnmatchedFilters() throws Exception {
        // Set up
        JSONObject budgetObj1 = new JSONObject();
        budgetObj1.put(AggregatableNamedBudget.NamedBudgetContract.NAME, BUDGET_NAME1);
        JSONObject filterMap1 = new JSONObject();
        filterMap1.put("1", new JSONArray(Arrays.asList("78")));
        filterMap1.put("2", new JSONArray(Arrays.asList("1234", "234")));
        JSONArray filterSet1 = new JSONArray();
        filterSet1.put(filterMap1);
        budgetObj1.put(Filter.FilterContract.FILTERS, filterSet1);
        JSONObject notFilterMap1 = new JSONObject();
        notFilterMap1.put("1", new JSONArray(Arrays.asList("91")));
        notFilterMap1.put("2", new JSONArray(Arrays.asList("856", "23")));
        JSONArray notFilterSet1 = new JSONArray();
        notFilterSet1.put(notFilterMap1);
        budgetObj1.put(Filter.FilterContract.NOT_FILTERS, notFilterSet1);
        AggregatableNamedBudget aggregatableNamedBudget1 =
                new AggregatableNamedBudget(budgetObj1, mFlags);

        JSONObject budgetObj2 = new JSONObject();
        budgetObj2.put(AggregatableNamedBudget.NamedBudgetContract.NAME, BUDGET_NAME2);
        JSONObject filterMap2 = new JSONObject();
        filterMap2.put("1", new JSONArray(Arrays.asList("91")));
        filterMap2.put("2", new JSONArray(Arrays.asList("1234", "234")));
        JSONArray filterSet2 = new JSONArray();
        filterSet2.put(filterMap2);
        budgetObj2.put(Filter.FilterContract.FILTERS, filterSet2);
        JSONObject notFilterMap2 = new JSONObject();
        notFilterMap2.put("1", new JSONArray(Arrays.asList("78")));
        notFilterMap2.put("2", new JSONArray(Arrays.asList("856", "23")));
        JSONArray notFilterSet2 = new JSONArray();
        notFilterSet2.put(notFilterMap2);
        budgetObj2.put(Filter.FilterContract.NOT_FILTERS, notFilterSet2);
        AggregatableNamedBudget aggregatableNamedBudget2 =
                new AggregatableNamedBudget(budgetObj2, mFlags);

        Map<String, List<String>> sourceFilterMap = new HashMap<>();
        sourceFilterMap.put("1", Collections.singletonList("91"));
        sourceFilterMap.put("2", Arrays.asList("1234", "234"));
        FilterMap sourceFilter =
                new FilterMap.Builder().setAttributionFilterMap(sourceFilterMap).build();

        // Execution
        Optional<String> matchedNamedBudget =
                createExampleWithValues(
                                /* aggregateDeduplicationKeys= */ null,
                                Arrays.asList(aggregatableNamedBudget1, aggregatableNamedBudget2))
                        .maybeExtractNamedBudget(sourceFilter, mFlags);

        // Assertion
        assertThat(matchedNamedBudget).isPresent();
        assertThat(matchedNamedBudget.get()).isEqualTo(BUDGET_NAME2);
    }

    @Test
    public void testExtractNamedBudget_firstNamedBudgetHasUnmatchedNotFilters() throws Exception {
        // Set up
        JSONObject budgetObj1 = new JSONObject();
        budgetObj1.put(AggregatableNamedBudget.NamedBudgetContract.NAME, BUDGET_NAME1);
        JSONObject filterMap1 = new JSONObject();
        filterMap1.put("1", new JSONArray(Arrays.asList("1234", "234")));
        JSONArray filterSet1 = new JSONArray();
        filterSet1.put(filterMap1);
        budgetObj1.put(Filter.FilterContract.FILTERS, filterSet1);
        JSONObject notFilterMap1 = new JSONObject();
        notFilterMap1.put("2", new JSONArray(Arrays.asList("56")));
        JSONArray notFilterSet1 = new JSONArray();
        notFilterSet1.put(notFilterMap1);
        budgetObj1.put(Filter.FilterContract.NOT_FILTERS, notFilterSet1);
        AggregatableNamedBudget aggregatableNamedBudget1 =
                new AggregatableNamedBudget(budgetObj1, mFlags);

        JSONObject budgetObj2 = new JSONObject();
        budgetObj2.put(AggregatableNamedBudget.NamedBudgetContract.NAME, BUDGET_NAME2);
        JSONObject filterMap2 = new JSONObject();
        filterMap2.put("2", new JSONArray(Arrays.asList("56")));
        filterMap2.put("1", new JSONArray(Arrays.asList("1234", "234")));
        JSONArray filterSet2 = new JSONArray();
        filterSet2.put(filterMap2);
        budgetObj2.put(Filter.FilterContract.FILTERS, filterSet2);
        JSONObject notFilterMap2 = new JSONObject();
        notFilterMap2.put("2", new JSONArray(Arrays.asList("78")));
        notFilterMap2.put("1", new JSONArray(Arrays.asList("856", "23")));
        JSONArray notFilterSet2 = new JSONArray();
        notFilterSet2.put(notFilterMap2);
        budgetObj2.put(Filter.FilterContract.NOT_FILTERS, notFilterSet2);
        AggregatableNamedBudget aggregatableNamedBudget2 =
                new AggregatableNamedBudget(budgetObj2, mFlags);

        Map<String, List<String>> sourceFilterMap = new HashMap<>();
        sourceFilterMap.put("2", Collections.singletonList("56"));
        sourceFilterMap.put("1", Arrays.asList("1234", "234"));
        FilterMap sourceFilter =
                new FilterMap.Builder().setAttributionFilterMap(sourceFilterMap).build();

        // Execution
        Optional<String> matchedNamedBudget =
                createExampleWithValues(
                                /* aggregateDeduplicationKeys= */ null,
                                Arrays.asList(aggregatableNamedBudget1, aggregatableNamedBudget2))
                        .maybeExtractNamedBudget(sourceFilter, mFlags);

        // Assertion
        assertThat(matchedNamedBudget).isPresent();
        assertThat(matchedNamedBudget.get()).isEqualTo(BUDGET_NAME2);
    }

    @Test
    public void testExtractNamedBudget_noFiltersInFirstNamedBudget() throws Exception {
        JSONObject budgetObj1 = new JSONObject();
        budgetObj1.put(AggregatableNamedBudget.NamedBudgetContract.NAME, BUDGET_NAME1);
        AggregatableNamedBudget aggregatableNamedBudget1 =
                new AggregatableNamedBudget(budgetObj1, mFlags);

        JSONObject budgetObj2 = new JSONObject();
        budgetObj2.put(AggregatableNamedBudget.NamedBudgetContract.NAME, BUDGET_NAME2);
        JSONObject filterMap2 = new JSONObject();
        filterMap2.put("1", new JSONArray(Arrays.asList("789")));
        filterMap2.put("2", new JSONArray(Arrays.asList("1234", "234")));
        JSONArray filterSet2 = new JSONArray();
        filterSet2.put(filterMap2);
        budgetObj2.put(Filter.FilterContract.FILTERS, filterSet2);
        AggregatableNamedBudget aggregatableNamedBudget2 =
                new AggregatableNamedBudget(budgetObj2, mFlags);

        Map<String, List<String>> sourceFilterMap = new HashMap<>();
        sourceFilterMap.put("1", Collections.singletonList("789"));
        sourceFilterMap.put("2", Arrays.asList("1234", "234"));
        FilterMap sourceFilter =
                new FilterMap.Builder().setAttributionFilterMap(sourceFilterMap).build();

        Optional<String> matchedNamedBudget =
                createExampleWithValues(
                                /* aggregateDeduplicationKeys= */ null,
                                Arrays.asList(aggregatableNamedBudget1, aggregatableNamedBudget2))
                        .maybeExtractNamedBudget(sourceFilter, mFlags);
        assertThat(matchedNamedBudget).isPresent();
        assertThat(matchedNamedBudget.get()).isEqualTo(BUDGET_NAME1);
    }

    @Test
    public void testExtractNamedBudget_noNamedBudgetsMatch() throws Exception {
        JSONObject budgetObj1 = new JSONObject();
        budgetObj1.put(AggregatableNamedBudget.NamedBudgetContract.NAME, BUDGET_NAME1);
        JSONObject filterMap1 = new JSONObject();
        filterMap1.put("1", new JSONArray(Arrays.asList("78")));
        filterMap1.put("2", new JSONArray(Arrays.asList("4321", "432")));
        JSONArray filterSet1 = new JSONArray();
        filterSet1.put(filterMap1);
        budgetObj1.put(Filter.FilterContract.FILTERS, filterSet1);
        AggregatableNamedBudget aggregatableNamedBudget1 =
                new AggregatableNamedBudget(budgetObj1, mFlags);

        JSONObject budgetObj2 = new JSONObject();
        budgetObj2.put(AggregatableNamedBudget.NamedBudgetContract.NAME, BUDGET_NAME2);
        JSONObject filterMap2 = new JSONObject();
        filterMap2.put("1", new JSONArray(Arrays.asList("26")));
        filterMap2.put("2", new JSONArray(Arrays.asList("9876", "654")));
        JSONArray filterSet2 = new JSONArray();
        filterSet2.put(filterMap2);
        budgetObj2.put(Filter.FilterContract.FILTERS, filterSet2);
        AggregatableNamedBudget aggregatableNamedBudget2 =
                new AggregatableNamedBudget(budgetObj2, mFlags);

        Map<String, List<String>> sourceFilterMap = new HashMap<>();
        sourceFilterMap.put("1", Collections.singletonList("509"));
        sourceFilterMap.put("2", Arrays.asList("1234", "234"));
        FilterMap sourceFilter =
                new FilterMap.Builder().setAttributionFilterMap(sourceFilterMap).build();

        Optional<String> matchedNamedBudget =
                createExampleWithValues(
                                /* aggregateDeduplicationKeys= */ null,
                                Arrays.asList(aggregatableNamedBudget1, aggregatableNamedBudget2))
                        .maybeExtractNamedBudget(sourceFilter, mFlags);
        assertThat(matchedNamedBudget).isEmpty();
    }

    @Test
    public void testExtractNamedBudget_nullNamedBudgets() throws Exception {
        Map<String, List<String>> sourceFilterMap = new HashMap<>();
        sourceFilterMap.put("1", Collections.singletonList("509"));
        sourceFilterMap.put("2", Arrays.asList("1234", "234"));
        FilterMap sourceFilter =
                new FilterMap.Builder().setAttributionFilterMap(sourceFilterMap).build();

        Optional<String> matchedNamedBudget =
                createExampleWithValues(
                                /* aggregateDeduplicationKeys= */ null,
                                /* aggregatableBuckets= */ null)
                        .maybeExtractNamedBudget(sourceFilter, mFlags);
        assertThat(matchedNamedBudget).isEmpty();
    }

    @Test
    public void testExtractNamedBudget_emptyNamedBudgets() throws Exception {
        Map<String, List<String>> sourceFilterMap = new HashMap<>();
        sourceFilterMap.put("1", Collections.singletonList("509"));
        sourceFilterMap.put("2", Arrays.asList("1234", "234"));
        FilterMap sourceFilter =
                new FilterMap.Builder().setAttributionFilterMap(sourceFilterMap).build();

        Optional<String> matchedNamedBudget =
                createExampleWithValues(/* aggregateDeduplicationKeys= */ null, Arrays.asList())
                        .maybeExtractNamedBudget(sourceFilter, mFlags);
        assertThat(matchedNamedBudget).isEmpty();
    }
}

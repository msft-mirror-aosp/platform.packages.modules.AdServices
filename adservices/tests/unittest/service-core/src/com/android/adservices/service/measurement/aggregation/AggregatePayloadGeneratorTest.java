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
import static org.junit.Assert.assertTrue;

import androidx.test.filters.SmallTest;

import org.junit.Test;

import java.math.BigInteger;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collections;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Optional;

/** Unit tests for {@link AggregatePayloadGenerator} */
@SmallTest
public final class AggregatePayloadGeneratorTest {

    @Test
    public void testIsFilterMatchReturnTrue() {
        Map<String, List<String>> sourceFilterMap = new HashMap<>();
        sourceFilterMap.put("conversion_subdomain",
                Collections.singletonList("electronics.megastore"));
        sourceFilterMap.put("product", Arrays.asList("1234", "234"));
        sourceFilterMap.put("ctid", Collections.singletonList("id"));
        AggregateFilterData sourceFilter =  new AggregateFilterData.Builder()
                .setAttributionFilterMap(sourceFilterMap).build();

        Map<String, List<String>> triggerFilterMap = new HashMap<>();
        triggerFilterMap.put("conversion_subdomain",
                Collections.singletonList("electronics.megastore"));
        triggerFilterMap.put("product", Arrays.asList("1234", "2345"));
        triggerFilterMap.put("id", Arrays.asList("1", "2"));
        AggregateFilterData triggerFilter =  new AggregateFilterData.Builder()
                .setAttributionFilterMap(triggerFilterMap).build();

        assertTrue(
                AggregatePayloadGenerator.isFilterMatch(sourceFilter, triggerFilter, true));
    }

    @Test
    public void testIsFilterMatchReturnFalse() {
        Map<String, List<String>> sourceFilterMap = new HashMap<>();
        sourceFilterMap.put("conversion_subdomain",
                Collections.singletonList("electronics.megastore"));
        sourceFilterMap.put("product", Arrays.asList("1234", "234"));
        sourceFilterMap.put("ctid", Collections.singletonList("id"));
        AggregateFilterData sourceFilter =  new AggregateFilterData.Builder()
                .setAttributionFilterMap(sourceFilterMap).build();

        Map<String, List<String>> triggerFilterMap = new HashMap<>();
        triggerFilterMap.put("conversion_subdomain",
                Collections.singletonList("electronics.megastore"));
        triggerFilterMap.put("product", Arrays.asList("1", "2"));  // doesn't match.
        triggerFilterMap.put("id", Arrays.asList("1", "2"));
        AggregateFilterData triggerFilter =  new AggregateFilterData.Builder()
                .setAttributionFilterMap(triggerFilterMap).build();

        assertFalse(
                AggregatePayloadGenerator.isFilterMatch(sourceFilter, triggerFilter, true));
    }

    @Test
    public void testIsNotFilterMatchReturnTrue() {
        Map<String, List<String>> sourceFilterMap = new HashMap<>();
        sourceFilterMap.put("conversion_subdomain",
                Collections.singletonList("electronics.megastore"));
        sourceFilterMap.put("product", Arrays.asList("1234", "234"));
        sourceFilterMap.put("ctid", Collections.singletonList("id"));
        AggregateFilterData sourceFilter =  new AggregateFilterData.Builder()
                .setAttributionFilterMap(sourceFilterMap).build();

        Map<String, List<String>> triggerFilterMap = new HashMap<>();
        triggerFilterMap.put("conversion_subdomain", Collections.singletonList("electronics"));
        triggerFilterMap.put("product", Arrays.asList("1", "2"));  // doesn't match.
        triggerFilterMap.put("id", Arrays.asList("1", "2"));
        AggregateFilterData triggerFilter =  new AggregateFilterData.Builder()
                .setAttributionFilterMap(triggerFilterMap).build();
        assertTrue(AggregatePayloadGenerator.isFilterMatch(
                sourceFilter, triggerFilter, false));
    }

    @Test
    public void testIsNotFilterMatchReturnFalse() {
        Map<String, List<String>> sourceFilterMap = new HashMap<>();
        sourceFilterMap.put("conversion_subdomain",
                Collections.singletonList("electronics.megastore"));
        sourceFilterMap.put("product", Arrays.asList("1234", "234"));
        sourceFilterMap.put("ctid", Collections.singletonList("id"));
        AggregateFilterData sourceFilter =  new AggregateFilterData.Builder()
                .setAttributionFilterMap(sourceFilterMap).build();

        Map<String, List<String>> triggerFilterMap = new HashMap<>();
        triggerFilterMap.put("conversion_subdomain",
                Collections.singletonList("electronics.megastore"));
        triggerFilterMap.put("product", Arrays.asList("1234", "2345"));
        triggerFilterMap.put("id", Arrays.asList("1", "2"));
        AggregateFilterData triggerFilter =  new AggregateFilterData.Builder()
                .setAttributionFilterMap(triggerFilterMap).build();

        assertFalse(
                AggregatePayloadGenerator.isFilterMatch(sourceFilter, triggerFilter, false));
    }

    @Test
    public void testGenerateAttributionReportTwoContributionsSuccessfully() {
        // Build AggregatableAttributionSource.
        Map<String, AttributionAggregatableKey> aggregatableSource = new HashMap<>();
        aggregatableSource.put("campaignCounts",
                new AttributionAggregatableKey.Builder().setHighBits(0L).setLowBits(345L).build());
        aggregatableSource.put("geoValue",
                new AttributionAggregatableKey.Builder().setHighBits(0L).setLowBits(5L).build());
        Map<String, List<String>> sourceFilterMap = new HashMap<>();
        sourceFilterMap.put("conversion_subdomain",
                Collections.singletonList("electronics.megastore"));
        sourceFilterMap.put("product", Arrays.asList("1234", "234"));
        sourceFilterMap.put("ctid", Collections.singletonList("id"));
        AggregateFilterData sourceFilter =  new AggregateFilterData.Builder()
                .setAttributionFilterMap(sourceFilterMap).build();
        AggregatableAttributionSource attributionSource =
                new AggregatableAttributionSource.Builder()
                        .setAggregatableSource(aggregatableSource)
                        .setAggregateFilterData(sourceFilter).build();

        // Build AggregatableAttributionTrigger.
        List<AggregateTriggerData> triggerDataList = new ArrayList<>();
        // Apply this key_piece to "campaignCounts".
        Map<String, List<String>> triggerDataFilter1 = new HashMap<>();
        triggerDataFilter1.put("product", Collections.singletonList("1234"));
        triggerDataFilter1.put("ctid", Collections.singletonList("id"));
        Map<String, List<String>> triggerDataNotFilter1 = new HashMap<>();
        triggerDataNotFilter1.put("product", Collections.singletonList("100"));
        triggerDataList.add(
                new AggregateTriggerData.Builder()
                        .setKey(new AttributionAggregatableKey.Builder()
                                .setHighBits(0L).setLowBits(1024L).build())
                        .setSourceKeys(new HashSet<>(Collections.singletonList("campaignCounts")))
                        .setFilter(new AggregateFilterData.Builder()
                                .setAttributionFilterMap(triggerDataFilter1).build())
                        .setNotFilter(new AggregateFilterData.Builder()
                                .setAttributionFilterMap(triggerDataNotFilter1).build()).build());
        // Apply this key_piece to "geoValue".
        triggerDataList.add(
                new AggregateTriggerData.Builder()
                        .setKey(new AttributionAggregatableKey.Builder()
                                .setHighBits(0L).setLowBits(2688L).build())
                        .setSourceKeys(new HashSet<>(Arrays.asList("geoValue", "nonMatch")))
                        .build());

        Map<String, Integer> values = new HashMap<>();
        values.put("campaignCounts", 32768);
        values.put("geoValue", 1664);
        AggregatableAttributionTrigger attributionTrigger =
                new AggregatableAttributionTrigger.Builder()
                        .setTriggerData(triggerDataList)
                        .setValues(values).build();

        Optional<CleartextAggregatePayload> attributionReport =
                AggregatePayloadGenerator.generateAttributionReport(
                        attributionSource, attributionTrigger);
        assertTrue(attributionReport.isPresent());
        List<AggregateHistogramContribution> contributions =
                attributionReport.get().getAggregateAttributionData().getContributions();

        assertEquals(contributions.size(), 2);
        assertTrue(contributions.contains(
                new AggregateHistogramContribution.Builder()
                        .setKey(BigInteger.valueOf(1369L)).setValue(32768).build()));
        assertTrue(contributions.contains(
                new AggregateHistogramContribution.Builder()
                        .setKey(BigInteger.valueOf(2693L)).setValue(1664).build()));
    }

    @Test
    public void testGenerateAttributionReportOnlyTwoContributionsSuccessfully() {
        // Build AggregatableAttributionSource.
        Map<String, AttributionAggregatableKey> aggregatableSource = new HashMap<>();
        aggregatableSource.put("campaignCounts",
                new AttributionAggregatableKey.Builder().setHighBits(0L).setLowBits(345L).build());
        aggregatableSource.put("geoValue",
                new AttributionAggregatableKey.Builder().setHighBits(0L).setLowBits(5L).build());
        aggregatableSource.put("thirdSource",
                new AttributionAggregatableKey.Builder().setHighBits(0L).setLowBits(100L).build());
        Map<String, List<String>> sourceFilterMap = new HashMap<>();
        sourceFilterMap.put("conversion_subdomain",
                Collections.singletonList("electronics.megastore"));
        sourceFilterMap.put("product", Arrays.asList("1234", "234"));
        sourceFilterMap.put("ctid", Collections.singletonList("id"));
        AggregateFilterData sourceFilter =  new AggregateFilterData.Builder()
                .setAttributionFilterMap(sourceFilterMap).build();
        AggregatableAttributionSource attributionSource =
                new AggregatableAttributionSource.Builder()
                        .setAggregatableSource(aggregatableSource)
                        .setAggregateFilterData(sourceFilter).build();
        // Build AggregatableAttributionTrigger.
        List<AggregateTriggerData> triggerDataList = new ArrayList<>();
        // Apply this key_piece to "campaignCounts".
        Map<String, List<String>> triggerDataFilter1 = new HashMap<>();
        triggerDataFilter1.put("product", Collections.singletonList("1234"));
        triggerDataFilter1.put("ctid", Collections.singletonList("id"));
        Map<String, List<String>> triggerDataNotFilter1 = new HashMap<>();
        triggerDataNotFilter1.put("product", Collections.singletonList("100"));
        triggerDataList.add(
                new AggregateTriggerData.Builder()
                        .setKey(new AttributionAggregatableKey.Builder()
                                .setHighBits(0L).setLowBits(1024L).build())
                        .setSourceKeys(new HashSet<>(Collections.singletonList("campaignCounts")))
                        .setFilter(new AggregateFilterData.Builder()
                                .setAttributionFilterMap(triggerDataFilter1).build())
                        .setNotFilter(new AggregateFilterData.Builder()
                                .setAttributionFilterMap(triggerDataNotFilter1).build())
                        .build());
        // Apply this key_piece to "geoValue".
        triggerDataList.add(
                new AggregateTriggerData.Builder()
                        .setKey(new AttributionAggregatableKey.Builder()
                                .setHighBits(0L).setLowBits(2688L).build())
                        .setSourceKeys(new HashSet<>(Arrays.asList("geoValue", "nonMatch")))
                        .build());

        Map<String, Integer> values = new HashMap<>();
        values.put("campaignCounts", 32768);
        values.put("geoValue", 1664);
        values.put("thirdSource", 100);
        AggregatableAttributionTrigger attributionTrigger =
                new AggregatableAttributionTrigger.Builder()
                        .setTriggerData(triggerDataList)
                        .setValues(values).build();

        Optional<CleartextAggregatePayload> attributionReport =
                AggregatePayloadGenerator.generateAttributionReport(
                        attributionSource, attributionTrigger);
        assertTrue(attributionReport.isPresent());
        List<AggregateHistogramContribution> contributions =
                attributionReport.get().getAggregateAttributionData().getContributions();

        assertEquals(contributions.size(), 2);
        assertTrue(contributions.contains(
                new AggregateHistogramContribution.Builder()
                        .setKey(BigInteger.valueOf(1369L)).setValue(32768).build()));
        assertTrue(contributions.contains(
                new AggregateHistogramContribution.Builder()
                        .setKey(BigInteger.valueOf(2693L)).setValue(1664).build()));
    }

    @Test
    public void testGenerateAttributionReportMoreTriggerDataSuccessfully() {
        // Build AggregatableAttributionSource.
        Map<String, AttributionAggregatableKey> aggregatableSource = new HashMap<>();
        aggregatableSource.put("campaignCounts",
                new AttributionAggregatableKey.Builder().setHighBits(0L).setLowBits(345L).build());
        aggregatableSource.put("geoValue",
                new AttributionAggregatableKey.Builder().setHighBits(0L).setLowBits(5L).build());
        Map<String, List<String>> sourceFilterMap = new HashMap<>();
        sourceFilterMap.put("conversion_subdomain",
                Collections.singletonList("electronics.megastore"));
        sourceFilterMap.put("product", Arrays.asList("1234", "234"));
        sourceFilterMap.put("ctid", Collections.singletonList("id"));
        AggregateFilterData sourceFilter =  new AggregateFilterData.Builder()
                .setAttributionFilterMap(sourceFilterMap).build();
        AggregatableAttributionSource attributionSource =
                new AggregatableAttributionSource.Builder()
                        .setAggregatableSource(aggregatableSource)
                        .setAggregateFilterData(sourceFilter).build();
        // Build AggregatableAttributionTrigger.
        List<AggregateTriggerData> triggerDataList = new ArrayList<>();
        // Apply this key_piece to "campaignCounts".
        Map<String, List<String>> triggerDataFilter1 = new HashMap<>();
        triggerDataFilter1.put("product", Collections.singletonList("1234"));
        triggerDataFilter1.put("ctid", Collections.singletonList("id"));
        Map<String, List<String>> triggerDataNotFilter1 = new HashMap<>();
        triggerDataNotFilter1.put("product", Collections.singletonList("100"));
        triggerDataList.add(
                new AggregateTriggerData.Builder()
                        .setKey(new AttributionAggregatableKey.Builder()
                                .setHighBits(0L).setLowBits(1024L).build())
                        .setSourceKeys(new HashSet<>(Collections.singletonList("campaignCounts")))
                        .setFilter(new AggregateFilterData.Builder()
                                .setAttributionFilterMap(triggerDataFilter1).build())
                        .setNotFilter(new AggregateFilterData.Builder()
                                .setAttributionFilterMap(triggerDataNotFilter1).build())
                        .build());
        // Apply this key_piece to "geoValue".
        triggerDataList.add(
                new AggregateTriggerData.Builder()
                        .setKey(new AttributionAggregatableKey.Builder()
                                .setHighBits(0L).setLowBits(2688L).build())
                        .setSourceKeys(new HashSet<>(Arrays.asList("geoValue", "nonMatch")))
                        .build());
        // Apply this key_piece to "geoValue".
        triggerDataList.add(
                new AggregateTriggerData.Builder()
                        .setKey(new AttributionAggregatableKey.Builder()
                                .setHighBits(0L).setLowBits(768L).build())
                        .setSourceKeys(new HashSet<>(Collections.singletonList("geoValue")))
                        .build());
        // Don't apply this key_piece.
        Map<String, List<String>> triggerDataFilter2 = new HashMap<>();
        triggerDataFilter2.put("product", Collections.singletonList("0"));
        triggerDataList.add(
                new AggregateTriggerData.Builder()
                        .setKey(new AttributionAggregatableKey.Builder()
                                .setHighBits(0L).setLowBits(200L).build())
                        .setSourceKeys(new HashSet<>(Arrays.asList("campaignCounts", "geoValue")))
                        .setFilter(new AggregateFilterData.Builder()
                                .setAttributionFilterMap(triggerDataFilter2).build())
                        .build());

        Map<String, Integer> values = new HashMap<>();
        values.put("campaignCounts", 32768);
        values.put("geoValue", 1664);
        AggregatableAttributionTrigger attributionTrigger =
                new AggregatableAttributionTrigger.Builder()
                        .setTriggerData(triggerDataList)
                        .setValues(values).build();

        Optional<CleartextAggregatePayload> attributionReport =
                AggregatePayloadGenerator.generateAttributionReport(
                        attributionSource, attributionTrigger);
        assertTrue(attributionReport.isPresent());
        List<AggregateHistogramContribution> contributions =
                attributionReport.get().getAggregateAttributionData().getContributions();

        assertEquals(contributions.size(), 2);
        assertTrue(contributions.contains(
                new AggregateHistogramContribution.Builder()
                        .setKey(BigInteger.valueOf(1369L)).setValue(32768).build()));
        assertTrue(contributions.contains(
                new AggregateHistogramContribution.Builder()
                        .setKey(BigInteger.valueOf(3461L)).setValue(1664).build()));
    }
}

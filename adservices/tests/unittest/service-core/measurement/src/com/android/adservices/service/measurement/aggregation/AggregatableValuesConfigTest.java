/*
 * Copyright (C) 2024 The Android Open Source Project
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

import static org.mockito.Mockito.when;

import androidx.test.filters.SmallTest;

import com.android.adservices.service.Flags;

import org.json.JSONArray;
import org.json.JSONObject;
import org.junit.Test;
import org.junit.runner.RunWith;
import org.mockito.Mock;
import org.mockito.junit.MockitoJUnitRunner;

import java.util.Collections;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

/** Unit tests for {@link AggregatableValuesConfig} */
@RunWith(MockitoJUnitRunner.class)
@SmallTest
public final class AggregatableValuesConfigTest {
    @Mock Flags mFlags;

    @Test
    public void testBuilderWithJsonObject_success() throws Exception {
        // Build values
        JSONObject jsonObject = new JSONObject();
        jsonObject.put("campaignCounts", 32768);
        jsonObject.put("geoValue", 1664);
        AggregatableValuesConfig aggregatableValuesConfig =
                new AggregatableValuesConfig.Builder(jsonObject).build();
        assertThat(aggregatableValuesConfig.getValues()).isNotNull();
        assertThat(aggregatableValuesConfig.getValues().size()).isEqualTo(2);
        assertThat(aggregatableValuesConfig.getValues().get("campaignCounts").getValue())
                .isEqualTo(32768);
        assertThat(aggregatableValuesConfig.getValues().get("geoValue").getValue()).isEqualTo(1664);
    }

    @Test
    public void testBuilderWithJsonObjectFromArray_success() throws Exception {
        when(mFlags.getMeasurementEnableLookbackWindowFilter()).thenReturn(true);
        AggregatableValuesConfig aggregatableValuesConfig = createSimpleAggregatableValuesConfig();
        assertThat(aggregatableValuesConfig.getValues()).isNotNull();
        assertThat(aggregatableValuesConfig.getValues().size()).isEqualTo(2);
        assertThat(aggregatableValuesConfig.getValues().get("campaignCounts").getValue())
                .isEqualTo(32768);
        assertThat(aggregatableValuesConfig.getValues().get("geoValue").getValue()).isEqualTo(1664);
        assertThat(aggregatableValuesConfig.getFilterSet().size()).isEqualTo(1);
        assertThat(aggregatableValuesConfig.getFilterSet().get(0).getStringListValue("conversion"))
                .isEqualTo(Collections.singletonList("electronics"));
        assertThat(aggregatableValuesConfig.getNotFilterSet().get(0).getStringListValue("product"))
                .isEqualTo(List.of("1234", "2345"));
    }

    @Test
    public void testHashCode_equals() throws Exception {
        final AggregatableValuesConfig aggregatableValuesConfig1 =
                createSimpleAggregatableValuesConfig();
        final AggregatableValuesConfig aggregatableValuesConfig2 =
                createSimpleAggregatableValuesConfig();
        assertThat(aggregatableValuesConfig1.hashCode())
                .isEqualTo(aggregatableValuesConfig2.hashCode());
        assertThat(aggregatableValuesConfig1).isEqualTo(aggregatableValuesConfig2);
    }

    @Test
    public void testHashCode_notEquals() throws Exception {
        final AggregatableValuesConfig aggregatableValuesConfig1 =
                createSimpleAggregatableValuesConfig();
        JSONObject jsonObj1Values = new JSONObject();
        jsonObj1Values.put("campaignCounts", 32768);
        jsonObj1Values.put("geoValue", 1664);
        JSONObject jsonObject = new JSONObject().put("values", jsonObj1Values);
        final AggregatableValuesConfig aggregatableValuesConfig2 =
                new AggregatableValuesConfig.Builder(jsonObject, mFlags).build();
        assertThat(aggregatableValuesConfig1.hashCode())
                .isNotEqualTo(aggregatableValuesConfig2.hashCode());
        assertThat(aggregatableValuesConfig1).isNotEqualTo(aggregatableValuesConfig2);
    }

    @Test
    public void testGetConfigValuesMap_success() throws Exception {
        Map<String, Integer> expectedConfigValuesMap = new HashMap<>();
        expectedConfigValuesMap.put("campaignCounts", 32768);
        expectedConfigValuesMap.put("geoValue", 1664);
        AggregatableValuesConfig aggregatableValuesConfig = createSimpleAggregatableValuesConfig();
        assertThat(aggregatableValuesConfig.getConfigValuesMap())
                .isEqualTo(expectedConfigValuesMap);
    }

    private AggregatableValuesConfig createSimpleAggregatableValuesConfig() throws Exception {
        // Build values
        JSONObject jsonObj1Values = new JSONObject();
        jsonObj1Values.put("campaignCounts", 32768);
        jsonObj1Values.put("geoValue", 1664);
        // Build filter_set and not_filter_set
        JSONObject filterMapJson = new JSONObject();
        filterMapJson.put("conversion", new JSONArray(List.of("electronics")));
        filterMapJson.put("product", new JSONArray(List.of("1234", "2345")));
        JSONObject notFilterMapJson = new JSONObject();
        notFilterMapJson.put("product", new JSONArray(List.of("1234", "2345")));
        // Put into json object
        JSONObject jsonObj = new JSONObject();
        jsonObj.put("values", jsonObj1Values);
        jsonObj.put("filters", filterMapJson);
        jsonObj.put("not_filters", notFilterMapJson);
        return new AggregatableValuesConfig.Builder(jsonObj, mFlags).build();
    }
}

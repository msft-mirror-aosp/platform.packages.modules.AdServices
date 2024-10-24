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
import com.android.adservices.service.measurement.FilterMap;
import com.android.adservices.service.measurement.util.Filter;

import org.json.JSONArray;
import org.json.JSONException;
import org.json.JSONObject;
import org.junit.Test;
import org.junit.runner.RunWith;
import org.mockito.Mock;
import org.mockito.junit.MockitoJUnitRunner;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

/** Unit tests for {@link AggregatableNamedBudget} */
@SmallTest
@RunWith(MockitoJUnitRunner.class)
public class AggregatableNamedBudgetTest {
    private static final String NAME = "budget1";
    @Mock Flags mFlags;

    private AggregatableNamedBudget createExample() throws JSONException {
        JSONObject budgetObj = new JSONObject();
        budgetObj.put(AggregatableNamedBudget.NamedBudgetContract.NAME, NAME);

        JSONObject filterMap = new JSONObject();
        filterMap.put("filter1", new JSONArray(Arrays.asList("abc", "xyz")));
        JSONArray filterSet = new JSONArray();
        filterSet.put(filterMap);

        budgetObj.put(Filter.FilterContract.FILTERS, filterSet);
        return new AggregatableNamedBudget(budgetObj, mFlags);
    }

    void verifyExample(AggregatableNamedBudget aggregatableNamedBudget) {
        Map<String, List<String>> filter = new HashMap<>();
        List<String> filterValues = new ArrayList<>();
        filterValues.add("abc");
        filterValues.add("xyz");
        filter.put("filter1", filterValues);
        List<FilterMap> filterSet = new ArrayList<>();
        filterSet.add(new FilterMap.Builder().setAttributionFilterMap(filter).build());

        assertThat(aggregatableNamedBudget.getName()).isEqualTo(NAME);
        assertThat(aggregatableNamedBudget.getFilterSet()).isEqualTo(filterSet);
    }

    @Test
    public void testCreation() throws JSONException {
        when(mFlags.getMeasurementEnableLookbackWindowFilter()).thenReturn(false);
        verifyExample(createExample());
    }

    @Test
    public void testHashCode_equals() throws Exception {
        when(mFlags.getMeasurementEnableLookbackWindowFilter()).thenReturn(false);
        final AggregatableNamedBudget aggregatableNamedBudget1 = createExample();
        final AggregatableNamedBudget aggregatableNamedBudget2 = createExample();
        final List<AggregatableNamedBudget> aggregatableNamedBudgetList1 =
                List.of(aggregatableNamedBudget1);
        final List<AggregatableNamedBudget> aggregatableNamedBudgetList2 =
                List.of(aggregatableNamedBudget2);
        assertThat(aggregatableNamedBudget1.hashCode())
                .isEqualTo(aggregatableNamedBudget2.hashCode());
        assertThat(aggregatableNamedBudget1).isEqualTo(aggregatableNamedBudget2);
        assertThat(aggregatableNamedBudgetList1).isEqualTo(aggregatableNamedBudgetList2);
    }

    @Test
    public void testHashCode_notEquals() throws Exception {
        when(mFlags.getMeasurementEnableLookbackWindowFilter()).thenReturn(false);
        final AggregatableNamedBudget aggregatableNamedBudget1 = createExample();
        JSONObject budgetObj = new JSONObject();
        budgetObj.put(AggregatableNamedBudget.NamedBudgetContract.NAME, NAME);
        AggregatableNamedBudget aggregatableNamedBudget2 =
                new AggregatableNamedBudget(budgetObj, mFlags);
        final List<AggregatableNamedBudget> aggregatableNamedBudgetList1 =
                List.of(aggregatableNamedBudget1);
        final List<AggregatableNamedBudget> aggregatableNamedBudgetList2 =
                List.of(aggregatableNamedBudget2);
        assertThat(aggregatableNamedBudget1.hashCode())
                .isNotEqualTo(aggregatableNamedBudget2.hashCode());
        assertThat(aggregatableNamedBudget1).isNotEqualTo(aggregatableNamedBudget2);
        assertThat(aggregatableNamedBudgetList1).isNotEqualTo(aggregatableNamedBudgetList2);
    }
}

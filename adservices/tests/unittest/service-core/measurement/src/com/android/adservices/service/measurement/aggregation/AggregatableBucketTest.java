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

/** Unit tests for {@link AggregatableBucket} */
@SmallTest
@RunWith(MockitoJUnitRunner.class)
public class AggregatableBucketTest {
    private static final String BUCKET_NAME = "bucket1";
    @Mock Flags mFlags;

    private AggregatableBucket createExample() throws JSONException {
        JSONObject bucketObj = new JSONObject();
        bucketObj.put(AggregatableBucket.AggregatableBucketContract.BUCKET, BUCKET_NAME);

        JSONObject filterMap = new JSONObject();
        filterMap.put("filter1", new JSONArray(Arrays.asList("abc", "xyz")));
        JSONArray filterSet = new JSONArray();
        filterSet.put(filterMap);

        bucketObj.put(Filter.FilterContract.FILTERS, filterSet);
        return new AggregatableBucket(bucketObj, mFlags);
    }

    void verifyExample(AggregatableBucket aggregatableBucket) {
        Map<String, List<String>> filter = new HashMap<>();
        List<String> filterValues = new ArrayList<>();
        filterValues.add("abc");
        filterValues.add("xyz");
        filter.put("filter1", filterValues);
        List<FilterMap> filterSet = new ArrayList<>();
        filterSet.add(new FilterMap.Builder().setAttributionFilterMap(filter).build());

        assertThat(aggregatableBucket.getBucket()).isEqualTo(BUCKET_NAME);
        assertThat(aggregatableBucket.getFilterSet()).isEqualTo(filterSet);
    }

    @Test
    public void testCreation() throws JSONException {
        when(mFlags.getMeasurementEnableLookbackWindowFilter()).thenReturn(false);
        verifyExample(createExample());
    }

    @Test
    public void testHashCode_equals() throws Exception {
        when(mFlags.getMeasurementEnableLookbackWindowFilter()).thenReturn(false);
        final AggregatableBucket aggregatableBucket1 = createExample();
        final AggregatableBucket aggregatableBucket2 = createExample();
        final List<AggregatableBucket> aggregatableBucketList1 = List.of(aggregatableBucket1);
        final List<AggregatableBucket> aggregatableBucketList2 = List.of(aggregatableBucket2);
        assertThat(aggregatableBucket1.hashCode()).isEqualTo(aggregatableBucket2.hashCode());
        assertThat(aggregatableBucket1).isEqualTo(aggregatableBucket2);
        assertThat(aggregatableBucketList1).isEqualTo(aggregatableBucketList2);
    }

    @Test
    public void testHashCode_notEquals() throws Exception {
        when(mFlags.getMeasurementEnableLookbackWindowFilter()).thenReturn(false);
        final AggregatableBucket aggregatableBucket1 = createExample();
        JSONObject bucketObj = new JSONObject();
        bucketObj.put(AggregatableBucket.AggregatableBucketContract.BUCKET, BUCKET_NAME);
        AggregatableBucket aggregatableBucket2 = new AggregatableBucket(bucketObj, mFlags);
        final List<AggregatableBucket> aggregatableBucketList1 = List.of(aggregatableBucket1);
        final List<AggregatableBucket> aggregatableBucketList2 = List.of(aggregatableBucket2);
        assertThat(aggregatableBucket1.hashCode()).isNotEqualTo(aggregatableBucket2.hashCode());
        assertThat(aggregatableBucket1).isNotEqualTo(aggregatableBucket2);
        assertThat(aggregatableBucketList1).isNotEqualTo(aggregatableBucketList2);
    }
}

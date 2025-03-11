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

import androidx.test.filters.SmallTest;

import com.android.adservices.service.measurement.util.UnsignedLong;

import org.junit.Test;

/** Unit tests for {@link AggregatableKeyValue} */
@SmallTest
public final class AggregatableKeyValueTest {
    @Test
    public void testBuilderWithInt() {
        AggregatableKeyValue aggregatableKeyValue = createSimpleAggregatableKeyValue();
        assertThat(aggregatableKeyValue.getValue()).isEqualTo(1664);
    }

    @Test
    public void testHashCode_equals() {
        final AggregatableKeyValue aggregatableKeyValue1 = createSimpleAggregatableKeyValue();
        final AggregatableKeyValue aggregatableKeyValue2 = createSimpleAggregatableKeyValue();
        assertThat(aggregatableKeyValue1.hashCode()).isEqualTo(aggregatableKeyValue2.hashCode());
        assertThat(aggregatableKeyValue1).isEqualTo(aggregatableKeyValue2);

        final AggregatableKeyValue aggKeyValueWithFilteringId1 = createAggregatableKeyValue();
        final AggregatableKeyValue aggKeyValueWithFilteringId2 = createAggregatableKeyValue();
        assertThat(aggKeyValueWithFilteringId1.hashCode())
                .isEqualTo(aggKeyValueWithFilteringId2.hashCode());
        assertThat(aggKeyValueWithFilteringId1).isEqualTo(aggKeyValueWithFilteringId2);
    }

    @Test
    public void testHashCode_notEquals() {
        final AggregatableKeyValue aggregatableKeyValue1 = createSimpleAggregatableKeyValue();
        final AggregatableKeyValue aggregatableKeyValue2 =
                new AggregatableKeyValue.Builder(1663).build();
        assertThat(aggregatableKeyValue1.hashCode()).isNotEqualTo(aggregatableKeyValue2.hashCode());
        assertThat(aggregatableKeyValue1).isNotEqualTo(aggregatableKeyValue2);

        final AggregatableKeyValue aggKeyValueWithFilteringId1 = createAggregatableKeyValue();
        final AggregatableKeyValue aggKeyValueWithFilteringId2 =
                new AggregatableKeyValue.Builder(1663)
                        .setFilteringId(new UnsignedLong("123"))
                        .build();
        assertThat(aggKeyValueWithFilteringId1.hashCode())
                .isNotEqualTo(aggKeyValueWithFilteringId2.hashCode());
        assertThat(aggKeyValueWithFilteringId1).isNotEqualTo(aggKeyValueWithFilteringId2);
    }

    private AggregatableKeyValue createSimpleAggregatableKeyValue() {
        int value = 1664;
        AggregatableKeyValue aggregatableKeyValue = new AggregatableKeyValue.Builder(value).build();
        return aggregatableKeyValue;
    }

    private AggregatableKeyValue createAggregatableKeyValue() {
        int value = 1664;
        UnsignedLong filteringId = new UnsignedLong("123");
        AggregatableKeyValue aggregatableKeyValue =
                new AggregatableKeyValue.Builder(value).setFilteringId(filteringId).build();
        return aggregatableKeyValue;
    }
}

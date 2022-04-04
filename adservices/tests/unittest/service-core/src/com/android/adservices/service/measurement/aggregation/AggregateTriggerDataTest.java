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
import static org.junit.Assert.assertNull;

import androidx.test.filters.SmallTest;

import org.junit.Test;

import java.util.Arrays;
import java.util.HashSet;

/** Unit tests for {@link AggregateTriggerData} */
@SmallTest
public final class AggregateTriggerDataTest {

    @Test
    public void testCreation() throws Exception {
        AggregateTriggerData attributionTriggerData =
                new AggregateTriggerData.Builder()
                        .setKey(
                                new AttributionAggregatableKey.Builder()
                                        .setHighBits(0L).setLowBits(5L).build())
                        .setSourceKeys(new HashSet<>(
                                Arrays.asList("campCounts", "campGeoCounts", "campGeoValue")))
                        .build();

        assertEquals(attributionTriggerData.getKey().getHighBits().longValue(), 0L);
        assertEquals(attributionTriggerData.getKey().getLowBits().longValue(), 5L);
        assertEquals(attributionTriggerData.getSourceKeys().size(), 3);
    }

    @Test
    public void testDefaults() throws Exception {
        AggregateTriggerData attributionTriggerData =
                new AggregateTriggerData.Builder().build();
        assertNull(attributionTriggerData.getKey());
        assertEquals(attributionTriggerData.getSourceKeys().size(), 0);
    }
}

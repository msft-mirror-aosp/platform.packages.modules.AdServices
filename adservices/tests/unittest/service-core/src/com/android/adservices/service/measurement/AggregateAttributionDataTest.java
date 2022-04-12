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
import static org.junit.Assert.assertNotNull;
import static org.junit.Assert.assertNull;

import androidx.test.filters.SmallTest;

import org.junit.Test;

import java.util.ArrayList;

/** Unit tests for {@link AggregateAttributionData} */
@SmallTest
public final class AggregateAttributionDataTest {

    private AggregateAttributionData createExample() {
        return new AggregateAttributionData.Builder()
                .setContributions(new ArrayList<>())
                .setId(1L)
                .setAssembledReport(new AggregateReport.Builder().build()).build();
    }

    @Test
    public void testCreation() throws Exception {
        AggregateAttributionData data = createExample();
        assertNotNull(data.getContributions());
        assertEquals(1L, data.getId().longValue());
        assertNotNull(data.getAssembledReport());
    }

    @Test
    public void testDefaults() throws Exception {
        AggregateAttributionData data = new AggregateAttributionData.Builder().build();
        assertEquals(0, data.getContributions().size());
        assertNull(data.getId());
        assertNull(data.getAssembledReport());
    }
}

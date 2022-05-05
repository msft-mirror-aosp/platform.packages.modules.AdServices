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
import static org.junit.Assert.assertNotNull;
import static org.junit.Assert.assertNull;

import androidx.test.filters.SmallTest;

import org.junit.Test;

/** Unit tests for {@link CleartextAggregatePayload} */
@SmallTest
public final class CleartextAggregatePayloadTest {

    private CleartextAggregatePayload.AttributionInfo createAttributionInfo() {
        return new CleartextAggregatePayload.AttributionInfo.Builder().setTime(1000L).build();
    }

    private CleartextAggregatePayload createAttributionReport() {
        return new CleartextAggregatePayload.Builder()
                .setAttributionInfo(createAttributionInfo())
                .setReportTime(1L)
                .setExternalReportId(2L)
                .setAggregateAttributionData(
                        new AggregateAttributionData.Builder().build())
                .setStatus(CleartextAggregatePayload.Status.PENDING)
                .build();
    }

    @Test
    public void testCreation() throws Exception {
        CleartextAggregatePayload attributionReport = createAttributionReport();
        assertEquals(1L, attributionReport.getReportTime());
        assertEquals(2L, attributionReport.getExternalReportId());
        assertNotNull(attributionReport.getAggregateAttributionData());
        CleartextAggregatePayload.AttributionInfo attributionInfo =
                attributionReport.getAttributionInfo();
        assertEquals(1000L, attributionInfo.getTime());
        assertEquals(CleartextAggregatePayload.Status.PENDING, attributionReport.getStatus());
    }

    @Test
    public void testDefaults() throws Exception {
        CleartextAggregatePayload attributionReport =
                new CleartextAggregatePayload.Builder().build();
        assertNull(attributionReport.getAttributionInfo());
        assertEquals(0L, attributionReport.getReportTime());
        assertEquals(0L, attributionReport.getExternalReportId());
        assertNull(attributionReport.getAggregateAttributionData());
        assertEquals(CleartextAggregatePayload.Status.PENDING, attributionReport.getStatus());
    }
}

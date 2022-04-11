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

/** Unit tests for {@link AttributionReport} */
@SmallTest
public final class AttributionReportTest {

    private AttributionReport.AttributionInfo createAttributionInfo() {
        return new AttributionReport.AttributionInfo.Builder()
                .setTime(1000L).setDebugkey(null).build();
    }
    private AttributionReport createAttributionReport() {
        return new AttributionReport.Builder()
                .setAttributionInfo(createAttributionInfo())
                .setReportTime(1L)
                .setExternalReportId(2L)
                .setAggregateAttributionData(
                        new AggregateAttributionData.Builder().build())
                .build();
    }

    @Test
    public void testCreation() throws Exception {
        AttributionReport attributionReport = createAttributionReport();
        assertEquals(1L, attributionReport.getReportTime());
        assertEquals(2L, attributionReport.getExternalReportId());
        assertNotNull(attributionReport.getAggregateAttributionData());
        AttributionReport.AttributionInfo attributionInfo = attributionReport.getAttributionInfo();
        assertEquals(1000L, attributionInfo.getTime());
        assertNull(attributionInfo.getDebugkey());
    }

    @Test
    public void testDefaults() throws Exception {
        AttributionReport attributionReport = new AttributionReport.Builder().build();
        assertNull(attributionReport.getAttributionInfo());
        assertEquals(0L, attributionReport.getReportTime());
        assertEquals(0L, attributionReport.getExternalReportId());
        assertNull(attributionReport.getAggregateAttributionData());
    }
}

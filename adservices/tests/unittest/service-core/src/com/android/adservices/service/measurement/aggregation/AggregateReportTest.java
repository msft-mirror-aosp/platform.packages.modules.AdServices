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
import java.util.List;

/** Unit tests for {@link AggregateReport} */
@SmallTest
public final class AggregateReportTest {

    private AggregateReport.AggregationServicePayload createPayload(
            List<Integer> payload, String keyId) {
        return new AggregateReport.AggregationServicePayload.Builder()
                    .setPayload(payload)
                    .setKeyId(keyId).build();
    }

    private AggregateReport createAggregateReport() {
        return new AggregateReport.Builder()
                .setAggregationServicePayload(
                        Arrays.asList(
                                createPayload(Arrays.asList(1, 2), "1"),
                                createPayload(Arrays.asList(3, 4), "2"))
                )
                .setSharedInfo("share_info").build();
    }

    @Test
    public void testCreation() throws Exception {
        AggregateReport aggregateReport = createAggregateReport();
        assertEquals("share_info", aggregateReport.getSharedInfo());
        List<AggregateReport.AggregationServicePayload> payloads = aggregateReport.getPayloads();
        assertEquals(payloads.size(), 2);
        assertEquals("1", payloads.get(0).getKeyId());
        assertEquals("2", payloads.get(1).getKeyId());
    }

    @Test
    public void testDefaults() throws Exception {
        AggregateReport aggregateReport = new AggregateReport.Builder().build();
        assertEquals(0, aggregateReport.getPayloads().size());
        assertNull(aggregateReport.getSharedInfo());
    }
}

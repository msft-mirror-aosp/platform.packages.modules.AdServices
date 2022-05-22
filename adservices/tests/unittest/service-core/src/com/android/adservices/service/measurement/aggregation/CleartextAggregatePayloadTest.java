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

import android.net.Uri;

import androidx.test.filters.SmallTest;

import org.junit.Test;

/** Unit tests for {@link CleartextAggregatePayload} */
@SmallTest
public final class CleartextAggregatePayloadTest {

    private CleartextAggregatePayload createAttributionReport() {
        return new CleartextAggregatePayload.Builder()
                .setId("1")
                .setPublisher(Uri.parse("android-app://com.example.abc"))
                .setAttributionDestination(Uri.parse("https://example.com/aS"))
                .setSourceRegistrationTime(5L)
                .setScheduledReportTime(1L)
                .setPrivacyBudgetKey("key")
                .setReportingOrigin(Uri.parse("https://example.com/rT"))
                .setDebugCleartextPayload(" key: 1369, value: 32768; key: 3461, value: 1664;")
                .setAggregateAttributionData(
                        new AggregateAttributionData.Builder().build())
                .setStatus(CleartextAggregatePayload.Status.PENDING)
                .build();
    }

    @Test
    public void testCreation() throws Exception {
        CleartextAggregatePayload attributionReport = createAttributionReport();
        assertEquals("1", attributionReport.getId());
        assertEquals(Uri.parse("android-app://com.example.abc"), attributionReport.getPublisher());
        assertEquals(Uri.parse("https://example.com/aS"),
                attributionReport.getAttributionDestination());
        assertEquals(5L, attributionReport.getSourceRegistrationTime());
        assertEquals(1L, attributionReport.getScheduledReportTime());
        assertEquals("key", attributionReport.getPrivacyBudgetKey());
        assertEquals(Uri.parse("https://example.com/rT"), attributionReport.getReportingOrigin());
        assertEquals(" key: 1369, value: 32768; key: 3461, value: 1664;",
                attributionReport.getDebugCleartextPayload());
        assertNotNull(attributionReport.getAggregateAttributionData());
        assertEquals(CleartextAggregatePayload.Status.PENDING, attributionReport.getStatus());
    }

    @Test
    public void testDefaults() throws Exception {
        CleartextAggregatePayload attributionReport =
                new CleartextAggregatePayload.Builder().build();
        assertNull(attributionReport.getId());
        assertNull(attributionReport.getPublisher());
        assertNull(attributionReport.getAttributionDestination());
        assertEquals(0L, attributionReport.getSourceRegistrationTime());
        assertEquals(0L, attributionReport.getScheduledReportTime());
        assertNull(attributionReport.getPrivacyBudgetKey());
        assertNull(attributionReport.getReportingOrigin());
        assertNull(attributionReport.getDebugCleartextPayload());
        assertNull(attributionReport.getAggregateAttributionData());
        assertEquals(CleartextAggregatePayload.Status.PENDING, attributionReport.getStatus());
    }
}

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
import static org.junit.Assert.assertNotEquals;
import static org.junit.Assert.assertTrue;
import static org.mockito.Mockito.doReturn;
import static org.mockito.Mockito.spy;

import android.net.Uri;

import com.android.adservices.service.measurement.aggregation.AggregatableAttributionSource;
import com.android.adservices.service.measurement.noising.ImpressionNoiseParams;

import org.json.JSONArray;
import org.json.JSONException;
import org.json.JSONObject;
import org.junit.Test;

import java.util.Arrays;
import java.util.Collections;
import java.util.List;
import java.util.Optional;
import java.util.concurrent.TimeUnit;
import java.util.stream.Collectors;
import java.util.stream.LongStream;

public class SourceTest {

    private static final double DOUBLE_MAX_DELTA = 0.000000001D;

    @Test
    public void testDefaults() {
        Source source = new Source.Builder().build();
        assertEquals(0, source.getDedupKeys().size());
        assertEquals(Source.Status.ACTIVE, source.getStatus());
        assertEquals(Source.SourceType.EVENT, source.getSourceType());
        assertEquals(Source.AttributionMode.UNASSIGNED, source.getAttributionMode());
    }

    @Test
    public void testEqualsPass() throws JSONException {
        assertEquals(new Source.Builder().build(), new Source.Builder().build());
        JSONArray aggregateSource = new JSONArray();
        JSONObject jsonObject1 = new JSONObject();
        jsonObject1.put("id", "campaignCounts");
        jsonObject1.put("key_piece", "0x159");
        JSONObject jsonObject2 = new JSONObject();
        jsonObject2.put("id", "geoValue");
        jsonObject2.put("key_piece", "0x5");
        aggregateSource.put(jsonObject1);
        aggregateSource.put(jsonObject2);

        JSONObject aggregateFilterData = new JSONObject();
        aggregateFilterData.put(
                "conversion_subdomain", Arrays.asList("electronics.megastore"));
        aggregateFilterData.put("product", Arrays.asList("1234", "2345"));
        assertEquals(
                new Source.Builder()
                        .setAdTechDomain(Uri.parse("https://example.com"))
                        .setAttributionDestination(Uri.parse("https://example.com/aD"))
                        .setPublisher(Uri.parse("https://example.com/aS"))
                        .setId("1")
                        .setEventId(2L)
                        .setPriority(3L)
                        .setEventTime(5L)
                        .setExpiryTime(5L)
                        .setDedupKeys(LongStream.range(0, 2).boxed().collect(Collectors.toList()))
                        .setStatus(Source.Status.ACTIVE)
                        .setSourceType(Source.SourceType.EVENT)
                        .setRegistrant(Uri.parse("android-app://com.example.abc"))
                        .setAggregateFilterData(aggregateFilterData.toString())
                        .setAggregateSource(aggregateSource.toString())
                        .setAggregateContributions(50001)
                        .build(),
                new Source.Builder()
                        .setAdTechDomain(Uri.parse("https://example.com"))
                        .setAttributionDestination(Uri.parse("https://example.com/aD"))
                        .setPublisher(Uri.parse("https://example.com/aS"))
                        .setId("1")
                        .setEventId(2L)
                        .setPriority(3L)
                        .setEventTime(5L)
                        .setExpiryTime(5L)
                        .setDedupKeys(LongStream.range(0, 2).boxed().collect(Collectors.toList()))
                        .setStatus(Source.Status.ACTIVE)
                        .setSourceType(Source.SourceType.EVENT)
                        .setRegistrant(Uri.parse("android-app://com.example.abc"))
                        .setAggregateFilterData(aggregateFilterData.toString())
                        .setAggregateSource(aggregateSource.toString())
                        .setAggregateContributions(50001)
                        .build());
    }

    @Test
    public void testEqualsFail() throws JSONException {
        assertNotEquals(
                new Source.Builder().setId("1").build(),
                new Source.Builder().setId("2").build());
        assertNotEquals(
                new Source.Builder().setEventId(1).build(),
                new Source.Builder().setEventId(2).build());
        assertNotEquals(
                new Source.Builder().setAttributionDestination(Uri.parse("1")).build(),
                new Source.Builder().setAttributionDestination(Uri.parse("2")).build());
        assertNotEquals(
                new Source.Builder().setAdTechDomain(Uri.parse("1")).build(),
                new Source.Builder().setAdTechDomain(Uri.parse("2")).build());
        assertNotEquals(
                new Source.Builder().setPublisher(Uri.parse("1")).build(),
                new Source.Builder().setPublisher(Uri.parse("2")).build());
        assertNotEquals(
                new Source.Builder().setPriority(1L).build(),
                new Source.Builder().setPriority(2L).build());
        assertNotEquals(
                new Source.Builder().setEventTime(1L).build(),
                new Source.Builder().setEventTime(2L).build());
        assertNotEquals(
                new Source.Builder().setExpiryTime(1L).build(),
                new Source.Builder().setExpiryTime(2L).build());
        assertNotEquals(
                new Source.Builder().setSourceType(Source.SourceType.EVENT).build(),
                new Source.Builder().setSourceType(Source.SourceType.NAVIGATION).build());
        assertNotEquals(
                new Source.Builder().setStatus(Source.Status.ACTIVE).build(),
                new Source.Builder().setStatus(Source.Status.IGNORED).build());
        assertNotEquals(
                new Source.Builder()
                        .setDedupKeys(LongStream.range(0, 2).boxed()
                                .collect(Collectors.toList()))
                        .build(),
                new Source.Builder()
                        .setDedupKeys(LongStream.range(1, 3).boxed()
                                .collect(Collectors.toList()))
                        .build());
        assertNotEquals(
                new Source.Builder()
                        .setRegistrant(Uri.parse("android-app://com.example.abc"))
                        .build(),
                new Source.Builder()
                        .setRegistrant(Uri.parse("android-app://com.example.xyz"))
                        .build());
        JSONArray aggregateSource1 = new JSONArray();
        JSONObject jsonObject1 = new JSONObject();
        jsonObject1.put("id", "campaignCounts");
        jsonObject1.put("key_piece", "0x159");
        aggregateSource1.put(jsonObject1);

        JSONArray aggregateSource2 = new JSONArray();
        JSONObject jsonObject2 = new JSONObject();
        jsonObject2.put("id", "geoValue");
        jsonObject2.put("key_piece", "0x5");
        aggregateSource2.put(jsonObject2);
        assertNotEquals(
                new Source.Builder().setAggregateSource(aggregateSource1.toString()).build(),
                new Source.Builder().setAggregateSource(aggregateSource2.toString()).build());
        assertNotEquals(
                new Source.Builder().setAggregateContributions(4000).build(),
                new Source.Builder().setAggregateContributions(4055).build());
    }

    @Test
    public void testGetReportingTimeEvent() {
        long triggerTime = System.currentTimeMillis();
        long expiryTime = triggerTime + TimeUnit.DAYS.toMillis(30);
        long sourceEventTime = triggerTime - TimeUnit.DAYS.toMillis(1);
        Source source = new Source.Builder()
                .setSourceType(Source.SourceType.EVENT)
                .setExpiryTime(expiryTime)
                .setEventTime(sourceEventTime)
                .build();
        assertEquals(expiryTime + TimeUnit.HOURS.toMillis(1),
                source.getReportingTime(triggerTime));
    }

    @Test
    public void testGetReportingTimeNavigationFirst() {
        long triggerTime = System.currentTimeMillis();
        long sourceExpiryTime = triggerTime + TimeUnit.DAYS.toMillis(25);
        long sourceEventTime = triggerTime - TimeUnit.DAYS.toMillis(1);
        Source source = new Source.Builder()
                .setSourceType(Source.SourceType.NAVIGATION)
                .setExpiryTime(sourceExpiryTime)
                .setEventTime(sourceEventTime)
                .build();
        assertEquals(
                sourceEventTime
                        + PrivacyParams.NAVIGATION_EARLY_REPORTING_WINDOW_MILLISECONDS[0]
                        + TimeUnit.HOURS.toMillis(1),
                source.getReportingTime(triggerTime));
    }

    @Test
    public void testGetReportingTimeNavigationSecond() {
        long triggerTime = System.currentTimeMillis();
        long sourceExpiryTime = triggerTime + TimeUnit.DAYS.toMillis(25);
        long sourceEventTime = triggerTime - TimeUnit.DAYS.toMillis(3);
        Source source = new Source.Builder()
                .setSourceType(Source.SourceType.NAVIGATION)
                .setExpiryTime(sourceExpiryTime)
                .setEventTime(sourceEventTime)
                .build();
        assertEquals(
                sourceEventTime
                        + PrivacyParams.NAVIGATION_EARLY_REPORTING_WINDOW_MILLISECONDS[1]
                        + TimeUnit.HOURS.toMillis(1),
                source.getReportingTime(triggerTime));
    }

    @Test
    public void testGetReportingTimeNavigationSecondExpiry() {
        long triggerTime = System.currentTimeMillis();
        long sourceExpiryTime = triggerTime + TimeUnit.DAYS.toMillis(2);
        long sourceEventTime = triggerTime - TimeUnit.DAYS.toMillis(3);
        Source source = new Source.Builder()
                .setSourceType(Source.SourceType.NAVIGATION)
                .setExpiryTime(sourceExpiryTime)
                .setEventTime(sourceEventTime)
                .build();
        assertEquals(
                sourceExpiryTime + TimeUnit.HOURS.toMillis(1),
                source.getReportingTime(triggerTime));
    }

    @Test
    public void testGetReportingTimeNavigationLast() {
        long triggerTime = System.currentTimeMillis();
        long sourceExpiryTime = triggerTime + TimeUnit.DAYS.toMillis(1);
        long sourceEventTime = triggerTime - TimeUnit.DAYS.toMillis(20);
        Source source = new Source.Builder()
                .setSourceType(Source.SourceType.NAVIGATION)
                .setExpiryTime(sourceExpiryTime)
                .setEventTime(sourceEventTime)
                .build();
        assertEquals(
                sourceExpiryTime + TimeUnit.HOURS.toMillis(1),
                source.getReportingTime(triggerTime));
    }

    @Test
    public void testTriggerDataCardinality() {
        Source eventSource = new Source.Builder()
                .setSourceType(Source.SourceType.EVENT)
                .build();
        assertEquals(PrivacyParams.EVENT_TRIGGER_DATA_CARDINALITY,
                eventSource.getTriggerDataCardinality());
        Source navigationSource = new Source.Builder()
                .setSourceType(Source.SourceType.NAVIGATION)
                .build();
        assertEquals(PrivacyParams.NAVIGATION_TRIGGER_DATA_CARDINALITY,
                navigationSource.getTriggerDataCardinality());
    }

    @Test
    public void testMaxReportCount() {
        Source eventSource = new Source.Builder()
                .setSourceType(Source.SourceType.EVENT)
                .build();
        assertEquals(PrivacyParams.EVENT_SOURCE_MAX_REPORTS,
                eventSource.getMaxReportCount());
        Source navigationSource = new Source.Builder()
                .setSourceType(Source.SourceType.NAVIGATION)
                .build();
        assertEquals(PrivacyParams.NAVIGATION_SOURCE_MAX_REPORTS,
                navigationSource.getMaxReportCount());
    }

    @Test
    public void testRandomAttributionProbability() {
        Source eventSource = new Source.Builder()
                .setSourceType(Source.SourceType.EVENT)
                .build();
        assertEquals(PrivacyParams.EVENT_NOISE_PROBABILITY,
                eventSource.getRandomAttributionProbability(), DOUBLE_MAX_DELTA);
        Source navigationSource = new Source.Builder()
                .setSourceType(Source.SourceType.NAVIGATION)
                .build();
        assertEquals(PrivacyParams.NAVIGATION_NOISE_PROBABILITY,
                navigationSource.getRandomAttributionProbability(), DOUBLE_MAX_DELTA);

        Source eventSourceWithInstallAttribution = new Source.Builder()
                .setSourceType(Source.SourceType.EVENT)
                .setInstallCooldownWindow(1)
                .build();
        assertEquals(PrivacyParams.INSTALL_ATTR_EVENT_NOISE_PROBABILITY,
                eventSourceWithInstallAttribution.getRandomAttributionProbability(),
                DOUBLE_MAX_DELTA);

        Source navigationSourceWithInstallAttribution = new Source.Builder()
                .setSourceType(Source.SourceType.NAVIGATION)
                .setInstallCooldownWindow(1)
                .build();
        assertEquals(PrivacyParams.INSTALL_ATTR_NAVIGATION_NOISE_PROBABILITY,
                navigationSourceWithInstallAttribution.getRandomAttributionProbability(),
                DOUBLE_MAX_DELTA);
    }

    @Test
    public void testFakeReportGeneration() {
        long expiry = System.currentTimeMillis();
        Source source = spy(new Source.Builder()
                .setSourceType(Source.SourceType.EVENT)
                .setExpiryTime(expiry)
                .build());
        // Increase the probability of random attribution.
        doReturn(0.50D).when(source).getRandomAttributionProbability();
        int falseCount = 0;
        int neverCount = 0;
        int truthCount = 0;
        for (int i = 0; i < 500; i++) {
            List<Source.FakeReport> fakeReports = source
                    .assignAttributionModeAndGenerateFakeReport();
            if (source.getAttributionMode() == Source.AttributionMode.FALSELY) {
                falseCount++;
                assertNotEquals(0, fakeReports.size());
                for (Source.FakeReport report : fakeReports) {
                    assertTrue(expiry + TimeUnit.HOURS.toMillis(1)
                            >= report.getReportingTime());
                    assertTrue(report.getTriggerData() < source.getTriggerDataCardinality());
                }
            } else if (source.getAttributionMode() == Source.AttributionMode.NEVER) {
                neverCount++;
                assertEquals(0, fakeReports.size());
            } else {
                truthCount++;
            }
        }
        assertNotEquals(0, falseCount);
        assertNotEquals(0, neverCount);
        assertNotEquals(0, truthCount);
    }

    @Test
    public void impressionNoiseParamGeneration() {
        long eventTime = System.currentTimeMillis();
        Source eventSource30dExpiry = new Source.Builder()
                .setSourceType(Source.SourceType.EVENT)
                .setEventTime(eventTime)
                .setExpiryTime(eventTime + TimeUnit.DAYS.toMillis(30))
                .build();
        assertEquals(
                new ImpressionNoiseParams(
                        /* reportCount= */ 1,
                        /* triggerDataCardinality= */ 2,
                        /* reportingWindowCount= */ 1),
                eventSource30dExpiry.getImpressionNoiseParams());

        Source eventSource7dExpiry = new Source.Builder()
                .setSourceType(Source.SourceType.EVENT)
                .setEventTime(eventTime)
                .setExpiryTime(eventTime + TimeUnit.DAYS.toMillis(30))
                .build();
        assertEquals(
                new ImpressionNoiseParams(
                        /* reportCount= */ 1,
                        /* triggerDataCardinality= */ 2,
                        /* reportingWindowCount= */ 1),
                eventSource7dExpiry.getImpressionNoiseParams());

        Source eventSource2dExpiry = new Source.Builder()
                .setSourceType(Source.SourceType.EVENT)
                .setEventTime(eventTime)
                .setExpiryTime(eventTime + TimeUnit.DAYS.toMillis(30))
                .build();
        assertEquals(
                new ImpressionNoiseParams(
                        /* reportCount= */ 1,
                        /* triggerDataCardinality= */ 2,
                        /* reportingWindowCount= */ 1),
                eventSource2dExpiry.getImpressionNoiseParams());

        Source navigationSource30dExpiry = new Source.Builder()
                .setSourceType(Source.SourceType.NAVIGATION)
                .setEventTime(eventTime)
                .setExpiryTime(eventTime + TimeUnit.DAYS.toMillis(30))
                .build();
        assertEquals(
                new ImpressionNoiseParams(
                        /* reportCount= */ 3,
                        /* triggerDataCardinality= */ 8,
                        /* reportingWindowCount= */ 3),
                navigationSource30dExpiry.getImpressionNoiseParams());

        Source navigationSource7dExpiry = new Source.Builder()
                .setSourceType(Source.SourceType.NAVIGATION)
                .setEventTime(eventTime)
                .setExpiryTime(eventTime + TimeUnit.DAYS.toMillis(7))
                .build();
        assertEquals(
                new ImpressionNoiseParams(
                        /* reportCount= */ 3,
                        /* triggerDataCardinality= */ 8,
                        /* reportingWindowCount= */ 2),
                navigationSource7dExpiry.getImpressionNoiseParams());

        Source navigationSource2dExpiry = new Source.Builder()
                .setSourceType(Source.SourceType.NAVIGATION)
                .setEventTime(eventTime)
                .setExpiryTime(eventTime + TimeUnit.DAYS.toMillis(2))
                .build();
        assertEquals(
                new ImpressionNoiseParams(
                        /* reportCount= */ 3,
                        /* triggerDataCardinality= */ 8,
                        /* reportingWindowCount= */ 1),
                navigationSource2dExpiry.getImpressionNoiseParams());
    }

    @Test
    public void impressionNoiseParamGeneration_withInstallAttribution() {
        long eventTime = System.currentTimeMillis();

        Source eventSource30dExpiry = new Source.Builder()
                .setSourceType(Source.SourceType.EVENT)
                .setInstallCooldownWindow(TimeUnit.DAYS.toMillis(2))
                .setInstallAttributionWindow(TimeUnit.DAYS.toMillis(10))
                .setEventTime(eventTime)
                .setExpiryTime(eventTime + TimeUnit.DAYS.toMillis(30))
                .build();
        assertEquals(
                new ImpressionNoiseParams(
                        /* reportCount= */ 2,
                        /* triggerDataCardinality= */2,
                        /* reportingWindowCount= */ 2),
                eventSource30dExpiry.getImpressionNoiseParams());

        Source eventSource7dExpiry = new Source.Builder()
                .setSourceType(Source.SourceType.EVENT)
                .setInstallCooldownWindow(TimeUnit.DAYS.toMillis(2))
                .setInstallAttributionWindow(TimeUnit.DAYS.toMillis(10))
                .setEventTime(eventTime)
                .setExpiryTime(eventTime + TimeUnit.DAYS.toMillis(7))
                .build();
        assertEquals(
                new ImpressionNoiseParams(
                        /* reportCount= */ 2,
                        /* triggerDataCardinality= */2,
                        /* reportingWindowCount= */ 2),
                eventSource7dExpiry.getImpressionNoiseParams());

        Source eventSource2dExpiry = new Source.Builder()
                .setSourceType(Source.SourceType.EVENT)
                .setInstallCooldownWindow(TimeUnit.DAYS.toMillis(2))
                .setInstallAttributionWindow(TimeUnit.DAYS.toMillis(10))
                .setEventTime(eventTime)
                .setExpiryTime(eventTime + TimeUnit.DAYS.toMillis(2))
                .build();
        assertEquals(
                new ImpressionNoiseParams(
                        /* reportCount= */ 2,
                        /* triggerDataCardinality= */2,
                        /* reportingWindowCount= */ 1),
                eventSource2dExpiry.getImpressionNoiseParams());

        Source navigationSource30dExpiry = new Source.Builder()
                .setSourceType(Source.SourceType.NAVIGATION)
                .setInstallCooldownWindow(TimeUnit.DAYS.toMillis(2))
                .setInstallAttributionWindow(TimeUnit.DAYS.toMillis(10))
                .setEventTime(eventTime)
                .setExpiryTime(eventTime + TimeUnit.DAYS.toMillis(30))
                .build();
        assertEquals(
                new ImpressionNoiseParams(
                        /* reportCount= */ 3,
                        /* triggerDataCardinality= */ 8,
                        /* reportingWindowCount= */ 3),
                navigationSource30dExpiry.getImpressionNoiseParams());

        Source navigationSource7dExpiry = new Source.Builder()
                .setSourceType(Source.SourceType.NAVIGATION)
                .setInstallCooldownWindow(TimeUnit.DAYS.toMillis(2))
                .setInstallAttributionWindow(TimeUnit.DAYS.toMillis(10))
                .setEventTime(eventTime)
                .setExpiryTime(eventTime + TimeUnit.DAYS.toMillis(7))
                .build();
        assertEquals(
                new ImpressionNoiseParams(
                        /* reportCount= */ 3,
                        /* triggerDataCardinality= */ 8,
                        /* reportingWindowCount= */ 2),
                navigationSource7dExpiry.getImpressionNoiseParams());

        Source navigationSource2dExpiry = new Source.Builder()
                .setSourceType(Source.SourceType.NAVIGATION)
                .setInstallCooldownWindow(TimeUnit.DAYS.toMillis(2))
                .setInstallAttributionWindow(TimeUnit.DAYS.toMillis(10))
                .setEventTime(eventTime)
                .setExpiryTime(eventTime + TimeUnit.DAYS.toMillis(2))
                .build();
        assertEquals(
                new ImpressionNoiseParams(
                        /* reportCount= */ 3,
                        /* triggerDataCardinality= */ 8,
                        /* reportingWindowCount= */ 1),
                navigationSource2dExpiry.getImpressionNoiseParams());
    }

    @Test
    public void reportingTimeByIndex_event() {
        long eventTime = System.currentTimeMillis();
        long oneHourInMillis = TimeUnit.HOURS.toMillis(1);

        // Expected: 1 window at expiry
        Source eventSource10d = new Source.Builder()
                .setSourceType(Source.SourceType.EVENT)
                .setEventTime(eventTime)
                .setExpiryTime(eventTime + TimeUnit.DAYS.toMillis(10))
                .build();
        assertEquals(
                eventTime + TimeUnit.DAYS.toMillis(10) + oneHourInMillis,
                eventSource10d.getReportingTimeForNoising(/* windowIndex= */ 0));
        assertEquals(eventTime + TimeUnit.DAYS.toMillis(10) + oneHourInMillis,
                eventSource10d.getReportingTimeForNoising(/* windowIndex= */ 1));

        // Expected: 1 window at expiry
        Source eventSource7d = new Source.Builder()
                .setSourceType(Source.SourceType.EVENT)
                .setEventTime(eventTime)
                .setExpiryTime(eventTime + TimeUnit.DAYS.toMillis(7))
                .build();
        assertEquals(
                eventTime + TimeUnit.DAYS.toMillis(7) + oneHourInMillis,
                eventSource7d.getReportingTimeForNoising(/* windowIndex= */ 0));
        assertEquals(eventTime + TimeUnit.DAYS.toMillis(7) + oneHourInMillis,
                eventSource7d.getReportingTimeForNoising(/* windowIndex= */ 1));

        // Expected: 1 window at expiry
        Source eventSource2d = new Source.Builder()
                .setSourceType(Source.SourceType.EVENT)
                .setEventTime(eventTime)
                .setExpiryTime(eventTime + TimeUnit.DAYS.toMillis(2))
                .build();
        assertEquals(
                eventTime + TimeUnit.DAYS.toMillis(2) + oneHourInMillis,
                eventSource2d.getReportingTimeForNoising(/* windowIndex= */ 0));
        assertEquals(eventTime + TimeUnit.DAYS.toMillis(2) + oneHourInMillis,
                eventSource2d.getReportingTimeForNoising(/* windowIndex= */ 1));

    }

    @Test
    public void reportingTimeByIndex_eventWithInstallAttribution() {
        long eventTime = System.currentTimeMillis();
        long oneHourInMillis = TimeUnit.HOURS.toMillis(1);

        // Expected: 2 windows at 2d, expiry(10d)
        Source eventSource10d = new Source.Builder()
                .setSourceType(Source.SourceType.EVENT)
                .setInstallCooldownWindow(TimeUnit.DAYS.toMillis(1))
                .setEventTime(eventTime)
                .setExpiryTime(eventTime + TimeUnit.DAYS.toMillis(10))
                .build();
        assertEquals(
                eventTime + TimeUnit.DAYS.toMillis(2) + oneHourInMillis,
                eventSource10d.getReportingTimeForNoising(/* windowIndex= */ 0));
        assertEquals(eventTime + TimeUnit.DAYS.toMillis(10) + oneHourInMillis,
                eventSource10d.getReportingTimeForNoising(/* windowIndex= */ 1));
        assertEquals(eventTime + TimeUnit.DAYS.toMillis(10) + oneHourInMillis,
                eventSource10d.getReportingTimeForNoising(/* windowIndex= */ 2));

        // Expected: 1 window at 2d(expiry)
        Source eventSource2d = new Source.Builder()
                .setSourceType(Source.SourceType.EVENT)
                .setEventTime(eventTime)
                .setExpiryTime(eventTime + TimeUnit.DAYS.toMillis(2))
                .build();
        assertEquals(
                eventTime + TimeUnit.DAYS.toMillis(2) + oneHourInMillis,
                eventSource2d.getReportingTimeForNoising(/* windowIndex= */ 0));
        assertEquals(eventTime + TimeUnit.DAYS.toMillis(2) + oneHourInMillis,
                eventSource2d.getReportingTimeForNoising(/* windowIndex= */ 1));
    }

    @Test
    public void reportingTimeByIndex_navigation() {
        long eventTime = System.currentTimeMillis();
        long oneHourInMillis = TimeUnit.HOURS.toMillis(1);

        // Expected: 3 windows at 2d, 7d & expiry(20d)
        Source navigationSource20d = new Source.Builder()
                .setSourceType(Source.SourceType.NAVIGATION)
                .setEventTime(eventTime)
                .setExpiryTime(eventTime + TimeUnit.DAYS.toMillis(20))
                .build();
        assertEquals(
                eventTime + TimeUnit.DAYS.toMillis(2) + oneHourInMillis,
                navigationSource20d.getReportingTimeForNoising(/* windowIndex= */ 0));
        assertEquals(eventTime + TimeUnit.DAYS.toMillis(7) + oneHourInMillis,
                navigationSource20d.getReportingTimeForNoising(/* windowIndex= */ 1));
        assertEquals(eventTime + TimeUnit.DAYS.toMillis(20) + oneHourInMillis,
                navigationSource20d.getReportingTimeForNoising(/* windowIndex= */ 2));

        // Expected: 2 windows at 2d & expiry(7d)
        Source navigationSource7d = new Source.Builder()
                .setSourceType(Source.SourceType.NAVIGATION)
                .setEventTime(eventTime)
                .setExpiryTime(eventTime + TimeUnit.DAYS.toMillis(7))
                .build();
        assertEquals(
                eventTime + TimeUnit.DAYS.toMillis(2) + oneHourInMillis,
                navigationSource7d.getReportingTimeForNoising(/* windowIndex= */ 0));
        assertEquals(eventTime + TimeUnit.DAYS.toMillis(7) + oneHourInMillis,
                navigationSource7d.getReportingTimeForNoising(/* windowIndex= */ 1));
        assertEquals(eventTime + TimeUnit.DAYS.toMillis(7) + oneHourInMillis,
                navigationSource7d.getReportingTimeForNoising(/* windowIndex= */ 2));

        // Expected: 1 window at 2d(expiry)
        Source navigationSource2d = new Source.Builder()
                .setSourceType(Source.SourceType.NAVIGATION)
                .setEventTime(eventTime)
                .setExpiryTime(eventTime + TimeUnit.DAYS.toMillis(2))
                .build();
        assertEquals(
                eventTime + TimeUnit.DAYS.toMillis(2) + oneHourInMillis,
                navigationSource2d.getReportingTimeForNoising(/* windowIndex= */ 0));
        assertEquals(eventTime + TimeUnit.DAYS.toMillis(2) + oneHourInMillis,
                navigationSource2d.getReportingTimeForNoising(/* windowIndex= */ 1));
    }

    @Test
    public void reportingTimeByIndex_navigationWithInstallAttribution() {
        long eventTime = System.currentTimeMillis();
        long oneHourInMillis = TimeUnit.HOURS.toMillis(1);

        // Expected: 3 windows at 2d, 7d & expiry(20d)
        Source navigationSource20d = new Source.Builder()
                .setSourceType(Source.SourceType.NAVIGATION)
                .setInstallCooldownWindow(TimeUnit.DAYS.toMillis(1))
                .setEventTime(eventTime)
                .setExpiryTime(eventTime + TimeUnit.DAYS.toMillis(20))
                .build();
        assertEquals(
                eventTime + TimeUnit.DAYS.toMillis(2) + oneHourInMillis,
                navigationSource20d.getReportingTimeForNoising(/* windowIndex= */ 0));
        assertEquals(eventTime + TimeUnit.DAYS.toMillis(7) + oneHourInMillis,
                navigationSource20d.getReportingTimeForNoising(/* windowIndex= */ 1));
        assertEquals(eventTime + TimeUnit.DAYS.toMillis(20) + oneHourInMillis,
                navigationSource20d.getReportingTimeForNoising(/* windowIndex= */ 2));

        // Expected: 2 windows at 2d & expiry(7d)
        Source navigationSource7d = new Source.Builder()
                .setSourceType(Source.SourceType.NAVIGATION)
                .setInstallCooldownWindow(TimeUnit.DAYS.toMillis(1))
                .setEventTime(eventTime)
                .setExpiryTime(eventTime + TimeUnit.DAYS.toMillis(7))
                .build();
        assertEquals(
                eventTime + TimeUnit.DAYS.toMillis(2) + oneHourInMillis,
                navigationSource7d.getReportingTimeForNoising(/* windowIndex= */ 0));
        assertEquals(eventTime + TimeUnit.DAYS.toMillis(7) + oneHourInMillis,
                navigationSource7d.getReportingTimeForNoising(/* windowIndex= */ 1));
        assertEquals(eventTime + TimeUnit.DAYS.toMillis(7) + oneHourInMillis,
                navigationSource7d.getReportingTimeForNoising(/* windowIndex= */ 2));

        // Expected: 1 window at 2d(expiry)
        Source navigationSource2d = new Source.Builder()
                .setSourceType(Source.SourceType.NAVIGATION)
                .setInstallCooldownWindow(TimeUnit.DAYS.toMillis(1))
                .setEventTime(eventTime)
                .setExpiryTime(eventTime + TimeUnit.DAYS.toMillis(2))
                .build();
        assertEquals(
                eventTime + TimeUnit.DAYS.toMillis(2) + oneHourInMillis,
                navigationSource2d.getReportingTimeForNoising(/* windowIndex= */ 0));
        assertEquals(eventTime + TimeUnit.DAYS.toMillis(2) + oneHourInMillis,
                navigationSource2d.getReportingTimeForNoising(/* windowIndex= */ 1));
    }


    @Test
    public void testParseAggregateSource() throws JSONException {
        JSONArray aggregatableSource = new JSONArray();
        JSONObject jsonObject1 = new JSONObject();
        jsonObject1.put("id", "campaignCounts");
        jsonObject1.put("key_piece", "0x159");
        JSONObject jsonObject2 = new JSONObject();
        jsonObject2.put("id", "geoValue");
        jsonObject2.put("key_piece", "0x5");
        aggregatableSource.put(jsonObject1);
        aggregatableSource.put(jsonObject2);

        JSONObject filterData = new JSONObject();
        filterData.put("conversion_subdomain",
                new JSONArray(Collections.singletonList("electronics.megastore")));
        filterData.put("product", new JSONArray(Arrays.asList("1234", "2345")));

        Source source = new Source.Builder().setAggregateSource(aggregatableSource.toString())
                .setAggregateFilterData(filterData.toString()).build();
        Optional<AggregatableAttributionSource> aggregatableAttributionSource =
                source.parseAggregateSource();
        assertTrue(aggregatableAttributionSource.isPresent());
        AggregatableAttributionSource aggregateSource = aggregatableAttributionSource.get();
        assertEquals(aggregateSource.getAggregatableSource().size(), 2);
        assertEquals(aggregateSource.getAggregatableSource()
                .get("campaignCounts").longValue(), 345L);
        assertEquals(aggregateSource.getAggregatableSource().get("geoValue").longValue(), 5L);
        assertEquals(aggregateSource.getAggregateFilterData().getAttributionFilterMap().size(), 2);
    }
}

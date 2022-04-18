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

    @FunctionalInterface
    public interface ThreeArgumentFunc<T1, T2, T3> {
        void apply(T1 t1, T2 t2, T3 t3);
    }

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
                        .setReportTo(Uri.parse("https://example.com/rT"))
                        .setAttributionDestination(Uri.parse("https://example.com/aD"))
                        .setAttributionSource(Uri.parse("https://example.com/aS"))
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
                        .build(),
                new Source.Builder()
                        .setReportTo(Uri.parse("https://example.com/rT"))
                        .setAttributionDestination(Uri.parse("https://example.com/aD"))
                        .setAttributionSource(Uri.parse("https://example.com/aS"))
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
                new Source.Builder().setReportTo(Uri.parse("1")).build(),
                new Source.Builder().setReportTo(Uri.parse("2")).build());
        assertNotEquals(
                new Source.Builder().setAttributionSource(Uri.parse("1")).build(),
                new Source.Builder().setAttributionSource(Uri.parse("2")).build());
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
    public void testTriggerDataNoiseRate() {
        Source eventSource = new Source.Builder()
                .setSourceType(Source.SourceType.EVENT)
                .build();
        assertEquals(PrivacyParams.EVENT_RANDOM_TRIGGER_DATA_NOISE,
                eventSource.getTriggerDataNoiseRate(), DOUBLE_MAX_DELTA);
        Source navigationSource = new Source.Builder()
                .setSourceType(Source.SourceType.NAVIGATION)
                .build();
        assertEquals(PrivacyParams.NAVIGATION_RANDOM_TRIGGER_DATA_NOISE,
                navigationSource.getTriggerDataNoiseRate(), DOUBLE_MAX_DELTA);
    }

    @Test
    public void testRandomAttributionProbability() {
        Source eventSource = new Source.Builder()
                .setSourceType(Source.SourceType.EVENT)
                .build();
        assertEquals(PrivacyParams.EVENT_RANDOM_ATTRIBUTION_STATE_PROBABILITY,
                eventSource.getRandomAttributionProbability(), DOUBLE_MAX_DELTA);
        Source navigationSource = new Source.Builder()
                .setSourceType(Source.SourceType.NAVIGATION)
                .build();
        assertEquals(PrivacyParams.NAVIGATION_RANDOM_ATTRIBUTION_STATE_PROBABILITY,
                navigationSource.getRandomAttributionProbability(), DOUBLE_MAX_DELTA);
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
    public void testFakeReportGenerationForSequenceIndex() {
        ThreeArgumentFunc<Source, List<long[]>, Integer> tester =
                (source, expectedReports, sequenceIndex) -> {
                    List<Source.FakeReport> actualReports = source.generateFakeReports(
                            sequenceIndex);
                    assertEquals(expectedReports.size(), actualReports.size());
                    for (int i = 0; i < actualReports.size(); i++) {
                        Source.FakeReport actual = actualReports.get(i);
                        long[] expected = expectedReports.get(i);
                        assertEquals(expected[0], actual.getTriggerData());
                        assertEquals(expected[1], actual.getReportingTime());
                    }
                };
        long eventTime = System.currentTimeMillis();
        long eventSourceExpiry = eventTime + TimeUnit.DAYS.toMillis(20);
        long eventSourceReportingTime = eventSourceExpiry + TimeUnit.HOURS.toMillis(1);
        Source eventSource = new Source.Builder()
                .setSourceType(Source.SourceType.EVENT)
                .setEventTime(eventTime)
                .setExpiryTime(eventSourceExpiry)
                .build();
        tester.apply(
                /*source=*/eventSource,
                /*expectedReports=*/Collections.emptyList(),
                /*sequenceIndex=*/0);
        tester.apply(
                /*source=*/eventSource,
                /*expectedReports=*/Collections.singletonList(
                        new long[]{0, eventSourceReportingTime}),
                /*sequenceIndex=*/1);
        tester.apply(
                /*source=*/eventSource,
                /*expectedReports=*/Collections.singletonList(
                        new long[]{1, eventSourceReportingTime}),
                /*sequenceIndex=*/2);
        long navigationSourceExpiry = eventTime + TimeUnit.DAYS.toMillis(28);
        long navigationReport1Time = eventTime + TimeUnit.DAYS.toMillis(2)
                + TimeUnit.HOURS.toMillis(1);
        long navigationReport2Time = eventTime + TimeUnit.DAYS.toMillis(7)
                + TimeUnit.HOURS.toMillis(1);
        long navigationReport3Time = navigationSourceExpiry + TimeUnit.HOURS.toMillis(1);
        Source navigationSource = new Source.Builder()
                .setSourceType(Source.SourceType.NAVIGATION)
                .setEventTime(eventTime)
                .setExpiryTime(navigationSourceExpiry)
                .build();
        tester.apply(
                /*source=*/navigationSource,
                /*expectedReports=*/Collections.emptyList(),
                /*sequenceIndex=*/0);
        tester.apply(
                /*source=*/navigationSource,
                /*expectedReports=*/Collections.singletonList(
                        new long[]{3, navigationReport1Time}),
                /*sequenceIndex=*/20);
        tester.apply(
                /*source=*/navigationSource,
                /*expectedReports=*/Arrays.asList(
                        new long[]{4, navigationReport1Time},
                        new long[]{2, navigationReport1Time}),
                /*sequenceIndex=*/41);
        tester.apply(
                /*source=*/navigationSource,
                /*expectedReports=*/Arrays.asList(
                        new long[]{4, navigationReport1Time},
                        new long[]{4, navigationReport1Time}),
                /*sequenceIndex=*/50);
        tester.apply(
                /*source=*/navigationSource,
                /*expectedReports=*/Arrays.asList(
                        new long[]{1, navigationReport3Time},
                        new long[]{6, navigationReport2Time},
                        new long[]{7, navigationReport1Time}),
                /*sequenceIndex=*/1268);
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
                .get("campaignCounts").getHighBits().longValue(), 0L);
        assertEquals(aggregateSource.getAggregatableSource()
                .get("campaignCounts").getLowBits().longValue(), 345L);
        assertEquals(aggregateSource.getAggregatableSource()
                .get("geoValue").getHighBits().longValue(), 0L);
        assertEquals(aggregateSource.getAggregatableSource()
                .get("geoValue").getLowBits().longValue(), 5L);
        assertEquals(aggregateSource.getAggregateFilterData().getAttributionFilterMap().size(),
                2);
    }
}

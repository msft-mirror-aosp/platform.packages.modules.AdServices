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
import static org.junit.Assert.assertNotNull;
import static org.junit.Assert.assertThrows;
import static org.junit.Assert.assertTrue;
import static org.mockito.Mockito.doReturn;
import static org.mockito.Mockito.spy;

import android.net.Uri;

import androidx.annotation.Nullable;

import com.android.adservices.service.measurement.aggregation.AggregatableAttributionSource;
import com.android.adservices.service.measurement.noising.ImpressionNoiseParams;
import com.android.adservices.service.measurement.noising.ImpressionNoiseUtil;
import com.android.adservices.service.measurement.util.UnsignedLong;

import org.json.JSONArray;
import org.json.JSONException;
import org.json.JSONObject;
import org.junit.Test;

import java.math.BigInteger;
import java.util.Arrays;
import java.util.Collections;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.concurrent.TimeUnit;
import java.util.stream.Collectors;
import java.util.stream.LongStream;

public class SourceTest {

    private static final double ZERO_DELTA = 0D;
    private static final UnsignedLong DEBUG_KEY_1 = new UnsignedLong(81786463L);
    private static final UnsignedLong DEBUG_KEY_2 = new UnsignedLong(23487834L);

    @Test
    public void testDefaults() {
        Source source = SourceFixture.getValidSourceBuilder().build();
        assertEquals(0, source.getDedupKeys().size());
        assertEquals(Source.Status.ACTIVE, source.getStatus());
        assertEquals(Source.SourceType.EVENT, source.getSourceType());
        assertEquals(Source.AttributionMode.UNASSIGNED, source.getAttributionMode());
    }

    @Test
    public void testEqualsPass() throws JSONException {
        assertEquals(SourceFixture.getValidSourceBuilder().build(),
                SourceFixture.getValidSourceBuilder().build());
        JSONArray aggregateSource = new JSONArray();
        JSONObject jsonObject1 = new JSONObject();
        jsonObject1.put("id", "campaignCounts");
        jsonObject1.put("key_piece", "0x159");
        JSONObject jsonObject2 = new JSONObject();
        jsonObject2.put("id", "geoValue");
        jsonObject2.put("key_piece", "0x5");
        aggregateSource.put(jsonObject1);
        aggregateSource.put(jsonObject2);

        JSONObject filterMap = new JSONObject();
        filterMap.put(
                "conversion_subdomain", Collections.singletonList("electronics.megastore"));
        filterMap.put("product", Arrays.asList("1234", "2345"));
        assertEquals(
                new Source.Builder()
                        .setEnrollmentId("enrollment-id")
                        .setAppDestination(Uri.parse("android-app://example.test/aD1"))
                        .setWebDestination(Uri.parse("https://example.test/aD2"))
                        .setPublisher(Uri.parse("https://example.test/aS"))
                        .setPublisherType(EventSurfaceType.WEB)
                        .setId("1")
                        .setEventId(new UnsignedLong(2L))
                        .setPriority(3L)
                        .setEventTime(5L)
                        .setExpiryTime(5L)
                        .setIsDebugReporting(true)
                        .setDedupKeys(
                                LongStream.range(0, 2)
                                        .boxed()
                                        .map(UnsignedLong::new)
                                        .collect(Collectors.toList()))
                        .setStatus(Source.Status.ACTIVE)
                        .setSourceType(Source.SourceType.EVENT)
                        .setRegistrant(Uri.parse("android-app://com.example.abc"))
                        .setFilterData(filterMap.toString())
                        .setAggregateSource(aggregateSource.toString())
                        .setAggregateContributions(50001)
                        .setDebugKey(DEBUG_KEY_1)
                        .setAggregatableAttributionSource(
                                SourceFixture.getValidSource().getAggregatableAttributionSource())
                        .build(),
                new Source.Builder()
                        .setEnrollmentId("enrollment-id")
                        .setAppDestination(Uri.parse("android-app://example.test/aD1"))
                        .setWebDestination(Uri.parse("https://example.test/aD2"))
                        .setPublisher(Uri.parse("https://example.test/aS"))
                        .setPublisherType(EventSurfaceType.WEB)
                        .setId("1")
                        .setEventId(new UnsignedLong(2L))
                        .setPriority(3L)
                        .setEventTime(5L)
                        .setExpiryTime(5L)
                        .setIsDebugReporting(true)
                        .setDedupKeys(
                                LongStream.range(0, 2)
                                        .boxed()
                                        .map(UnsignedLong::new)
                                        .collect(Collectors.toList()))
                        .setStatus(Source.Status.ACTIVE)
                        .setSourceType(Source.SourceType.EVENT)
                        .setRegistrant(Uri.parse("android-app://com.example.abc"))
                        .setFilterData(filterMap.toString())
                        .setAggregateSource(aggregateSource.toString())
                        .setAggregateContributions(50001)
                        .setDebugKey(DEBUG_KEY_1)
                        .setAggregatableAttributionSource(
                                SourceFixture.getValidSource().getAggregatableAttributionSource())
                        .build());
    }

    @Test
    public void testEqualsFail() throws JSONException {
        assertNotEquals(
                SourceFixture.getValidSourceBuilder().setId("1").build(),
                SourceFixture.getValidSourceBuilder().setId("2").build());
        assertNotEquals(
                SourceFixture.getValidSourceBuilder().setEventId(new UnsignedLong(1L)).build(),
                SourceFixture.getValidSourceBuilder().setEventId(new UnsignedLong(2L)).build());
        assertNotEquals(
                SourceFixture.getValidSourceBuilder()
                        .setAppDestination(Uri.parse("android-app://1.test"))
                        .build(),
                SourceFixture.getValidSourceBuilder()
                        .setAppDestination(Uri.parse("android-app://2.test"))
                        .build());
        assertNotEquals(
                SourceFixture.getValidSourceBuilder()
                        .setWebDestination(Uri.parse("https://1.test"))
                        .build(),
                SourceFixture.getValidSourceBuilder()
                        .setWebDestination(Uri.parse("https://2.test"))
                        .build());
        assertNotEquals(
                SourceFixture.getValidSourceBuilder()
                        .setEnrollmentId("enrollment-id-1")
                        .build(),
                SourceFixture.getValidSourceBuilder()
                        .setEnrollmentId("enrollment-id-2")
                        .build());
        assertNotEquals(
                SourceFixture.getValidSourceBuilder()
                        .setPublisher(Uri.parse("https://1.test")).build(),
                SourceFixture.getValidSourceBuilder()
                        .setPublisher(Uri.parse("https://2.test")).build());
        assertNotEquals(
                SourceFixture.getValidSourceBuilder()
                        .setPublisherType(EventSurfaceType.APP).build(),
                SourceFixture.getValidSourceBuilder()
                        .setPublisherType(EventSurfaceType.WEB).build());
        assertNotEquals(
                SourceFixture.getValidSourceBuilder().setPriority(1L).build(),
                SourceFixture.getValidSourceBuilder().setPriority(2L).build());
        assertNotEquals(
                SourceFixture.getValidSourceBuilder().setEventTime(1L).build(),
                SourceFixture.getValidSourceBuilder().setEventTime(2L).build());
        assertNotEquals(
                SourceFixture.getValidSourceBuilder().setExpiryTime(1L).build(),
                SourceFixture.getValidSourceBuilder().setExpiryTime(2L).build());
        assertNotEquals(
                SourceFixture.getValidSourceBuilder()
                        .setSourceType(Source.SourceType.EVENT).build(),
                SourceFixture.getValidSourceBuilder()
                        .setSourceType(Source.SourceType.NAVIGATION).build());
        assertNotEquals(
                SourceFixture.getValidSourceBuilder()
                        .setStatus(Source.Status.ACTIVE).build(),
                SourceFixture.getValidSourceBuilder()
                        .setStatus(Source.Status.IGNORED).build());
        assertNotEquals(
                SourceFixture.getValidSourceBuilder()
                        .setDedupKeys(LongStream.range(0, 2).boxed().map(UnsignedLong::new)
                                .collect(Collectors.toList()))
                        .build(),
                SourceFixture.getValidSourceBuilder()
                        .setDedupKeys(LongStream.range(1, 3).boxed().map(UnsignedLong::new)
                                .collect(Collectors.toList()))
                        .build());
        assertNotEquals(
                SourceFixture.getValidSourceBuilder()
                        .setRegistrant(Uri.parse("android-app://com.example.abc"))
                        .build(),
                SourceFixture.getValidSourceBuilder()
                        .setRegistrant(Uri.parse("android-app://com.example.xyz"))
                        .build());
        assertNotEquals(
                SourceFixture.ValidSourceParams.buildAggregatableAttributionSource(),
                SourceFixture.getValidSourceBuilder()
                        .setAggregatableAttributionSource(
                                new AggregatableAttributionSource.Builder().build())
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
                SourceFixture.getValidSourceBuilder()
                        .setAggregateSource(aggregateSource1.toString()).build(),
                SourceFixture.getValidSourceBuilder()
                        .setAggregateSource(aggregateSource2.toString()).build());

        assertNotEquals(
                SourceFixture.getValidSourceBuilder()
                        .setAggregateContributions(4000).build(),
                SourceFixture.getValidSourceBuilder()
                        .setAggregateContributions(4055).build());

        assertNotEquals(
                SourceFixture.getValidSourceBuilder().setDebugKey(DEBUG_KEY_1).build(),
                SourceFixture.getValidSourceBuilder().setDebugKey(DEBUG_KEY_2).build());
    }

    @Test
    public void testSourceBuilder_validateArgumentPublisher() {
        assertInvalidSourceArguments(
                SourceFixture.ValidSourceParams.SOURCE_EVENT_ID,
                null,
                SourceFixture.ValidSourceParams.ATTRIBUTION_DESTINATION,
                SourceFixture.ValidSourceParams.WEB_DESTINATION,
                SourceFixture.ValidSourceParams.ENROLLMENT_ID,
                SourceFixture.ValidSourceParams.REGISTRANT,
                SourceFixture.ValidSourceParams.SOURCE_EVENT_TIME,
                SourceFixture.ValidSourceParams.EXPIRY_TIME,
                SourceFixture.ValidSourceParams.PRIORITY,
                SourceFixture.ValidSourceParams.SOURCE_TYPE,
                SourceFixture.ValidSourceParams.INSTALL_ATTRIBUTION_WINDOW,
                SourceFixture.ValidSourceParams.INSTALL_COOLDOWN_WINDOW,
                SourceFixture.ValidSourceParams.DEBUG_KEY,
                SourceFixture.ValidSourceParams.ATTRIBUTION_MODE,
                SourceFixture.ValidSourceParams.buildAggregateSource(),
                SourceFixture.ValidSourceParams.buildFilterData());

        assertInvalidSourceArguments(
                SourceFixture.ValidSourceParams.SOURCE_EVENT_ID,
                Uri.parse("com.source"),
                SourceFixture.ValidSourceParams.ATTRIBUTION_DESTINATION,
                SourceFixture.ValidSourceParams.WEB_DESTINATION,
                SourceFixture.ValidSourceParams.ENROLLMENT_ID,
                SourceFixture.ValidSourceParams.REGISTRANT,
                SourceFixture.ValidSourceParams.SOURCE_EVENT_TIME,
                SourceFixture.ValidSourceParams.EXPIRY_TIME,
                SourceFixture.ValidSourceParams.PRIORITY,
                SourceFixture.ValidSourceParams.SOURCE_TYPE,
                SourceFixture.ValidSourceParams.INSTALL_ATTRIBUTION_WINDOW,
                SourceFixture.ValidSourceParams.INSTALL_COOLDOWN_WINDOW,
                SourceFixture.ValidSourceParams.DEBUG_KEY,
                SourceFixture.ValidSourceParams.ATTRIBUTION_MODE,
                SourceFixture.ValidSourceParams.buildAggregateSource(),
                SourceFixture.ValidSourceParams.buildFilterData());
    }

    @Test
    public void testSourceBuilder_validateArgumentAttributionDestination() {
        assertInvalidSourceArguments(
                SourceFixture.ValidSourceParams.SOURCE_EVENT_ID,
                SourceFixture.ValidSourceParams.PUBLISHER,
                null,
                null,
                SourceFixture.ValidSourceParams.ENROLLMENT_ID,
                SourceFixture.ValidSourceParams.REGISTRANT,
                SourceFixture.ValidSourceParams.SOURCE_EVENT_TIME,
                SourceFixture.ValidSourceParams.EXPIRY_TIME,
                SourceFixture.ValidSourceParams.PRIORITY,
                SourceFixture.ValidSourceParams.SOURCE_TYPE,
                SourceFixture.ValidSourceParams.INSTALL_ATTRIBUTION_WINDOW,
                SourceFixture.ValidSourceParams.INSTALL_COOLDOWN_WINDOW,
                SourceFixture.ValidSourceParams.DEBUG_KEY,
                SourceFixture.ValidSourceParams.ATTRIBUTION_MODE,
                SourceFixture.ValidSourceParams.buildAggregateSource(),
                SourceFixture.ValidSourceParams.buildFilterData());

        assertInvalidSourceArguments(
                SourceFixture.ValidSourceParams.SOURCE_EVENT_ID,
                SourceFixture.ValidSourceParams.PUBLISHER,
                Uri.parse("com.destination"),
                SourceFixture.ValidSourceParams.WEB_DESTINATION,
                SourceFixture.ValidSourceParams.ENROLLMENT_ID,
                SourceFixture.ValidSourceParams.REGISTRANT,
                SourceFixture.ValidSourceParams.SOURCE_EVENT_TIME,
                SourceFixture.ValidSourceParams.EXPIRY_TIME,
                SourceFixture.ValidSourceParams.PRIORITY,
                SourceFixture.ValidSourceParams.SOURCE_TYPE,
                SourceFixture.ValidSourceParams.INSTALL_ATTRIBUTION_WINDOW,
                SourceFixture.ValidSourceParams.INSTALL_COOLDOWN_WINDOW,
                SourceFixture.ValidSourceParams.DEBUG_KEY,
                SourceFixture.ValidSourceParams.ATTRIBUTION_MODE,
                SourceFixture.ValidSourceParams.buildAggregateSource(),
                SourceFixture.ValidSourceParams.buildFilterData());

        assertInvalidSourceArguments(
                SourceFixture.ValidSourceParams.SOURCE_EVENT_ID,
                SourceFixture.ValidSourceParams.PUBLISHER,
                SourceFixture.ValidSourceParams.ATTRIBUTION_DESTINATION,
                Uri.parse("com.destination"),
                SourceFixture.ValidSourceParams.ENROLLMENT_ID,
                SourceFixture.ValidSourceParams.REGISTRANT,
                SourceFixture.ValidSourceParams.SOURCE_EVENT_TIME,
                SourceFixture.ValidSourceParams.EXPIRY_TIME,
                SourceFixture.ValidSourceParams.PRIORITY,
                SourceFixture.ValidSourceParams.SOURCE_TYPE,
                SourceFixture.ValidSourceParams.INSTALL_ATTRIBUTION_WINDOW,
                SourceFixture.ValidSourceParams.INSTALL_COOLDOWN_WINDOW,
                SourceFixture.ValidSourceParams.DEBUG_KEY,
                SourceFixture.ValidSourceParams.ATTRIBUTION_MODE,
                SourceFixture.ValidSourceParams.buildAggregateSource(),
                SourceFixture.ValidSourceParams.buildFilterData());
    }

    @Test
    public void testSourceBuilder_validateArgumentEnrollmentId() {
        assertInvalidSourceArguments(
                SourceFixture.ValidSourceParams.SOURCE_EVENT_ID,
                SourceFixture.ValidSourceParams.PUBLISHER,
                SourceFixture.ValidSourceParams.ATTRIBUTION_DESTINATION,
                SourceFixture.ValidSourceParams.WEB_DESTINATION,
                null,
                SourceFixture.ValidSourceParams.REGISTRANT,
                SourceFixture.ValidSourceParams.SOURCE_EVENT_TIME,
                SourceFixture.ValidSourceParams.EXPIRY_TIME,
                SourceFixture.ValidSourceParams.PRIORITY,
                SourceFixture.ValidSourceParams.SOURCE_TYPE,
                SourceFixture.ValidSourceParams.INSTALL_ATTRIBUTION_WINDOW,
                SourceFixture.ValidSourceParams.INSTALL_COOLDOWN_WINDOW,
                SourceFixture.ValidSourceParams.DEBUG_KEY,
                SourceFixture.ValidSourceParams.ATTRIBUTION_MODE,
                SourceFixture.ValidSourceParams.buildAggregateSource(),
                SourceFixture.ValidSourceParams.buildFilterData());
    }

    @Test
    public void testSourceBuilder_validateArgumentRegistrant() {
        assertInvalidSourceArguments(
                SourceFixture.ValidSourceParams.SOURCE_EVENT_ID,
                SourceFixture.ValidSourceParams.PUBLISHER,
                SourceFixture.ValidSourceParams.ATTRIBUTION_DESTINATION,
                SourceFixture.ValidSourceParams.WEB_DESTINATION,
                SourceFixture.ValidSourceParams.ENROLLMENT_ID,
                null,
                SourceFixture.ValidSourceParams.SOURCE_EVENT_TIME,
                SourceFixture.ValidSourceParams.EXPIRY_TIME,
                SourceFixture.ValidSourceParams.PRIORITY,
                SourceFixture.ValidSourceParams.SOURCE_TYPE,
                SourceFixture.ValidSourceParams.INSTALL_ATTRIBUTION_WINDOW,
                SourceFixture.ValidSourceParams.INSTALL_COOLDOWN_WINDOW,
                SourceFixture.ValidSourceParams.DEBUG_KEY,
                SourceFixture.ValidSourceParams.ATTRIBUTION_MODE,
                SourceFixture.ValidSourceParams.buildAggregateSource(),
                SourceFixture.ValidSourceParams.buildFilterData());

        assertInvalidSourceArguments(
                SourceFixture.ValidSourceParams.SOURCE_EVENT_ID,
                SourceFixture.ValidSourceParams.PUBLISHER,
                SourceFixture.ValidSourceParams.ATTRIBUTION_DESTINATION,
                SourceFixture.ValidSourceParams.WEB_DESTINATION,
                SourceFixture.ValidSourceParams.ENROLLMENT_ID,
                Uri.parse("com.registrant"),
                SourceFixture.ValidSourceParams.SOURCE_EVENT_TIME,
                SourceFixture.ValidSourceParams.EXPIRY_TIME,
                SourceFixture.ValidSourceParams.PRIORITY,
                SourceFixture.ValidSourceParams.SOURCE_TYPE,
                SourceFixture.ValidSourceParams.INSTALL_ATTRIBUTION_WINDOW,
                SourceFixture.ValidSourceParams.INSTALL_COOLDOWN_WINDOW,
                SourceFixture.ValidSourceParams.DEBUG_KEY,
                SourceFixture.ValidSourceParams.ATTRIBUTION_MODE,
                SourceFixture.ValidSourceParams.buildAggregateSource(),
                SourceFixture.ValidSourceParams.buildFilterData());
    }

    @Test
    public void testSourceBuilder_validateArgumentSourceType() {
        assertInvalidSourceArguments(
                SourceFixture.ValidSourceParams.SOURCE_EVENT_ID,
                SourceFixture.ValidSourceParams.PUBLISHER,
                SourceFixture.ValidSourceParams.ATTRIBUTION_DESTINATION,
                SourceFixture.ValidSourceParams.WEB_DESTINATION,
                SourceFixture.ValidSourceParams.ENROLLMENT_ID,
                SourceFixture.ValidSourceParams.REGISTRANT,
                SourceFixture.ValidSourceParams.SOURCE_EVENT_TIME,
                SourceFixture.ValidSourceParams.EXPIRY_TIME,
                SourceFixture.ValidSourceParams.PRIORITY,
                null,
                SourceFixture.ValidSourceParams.INSTALL_ATTRIBUTION_WINDOW,
                SourceFixture.ValidSourceParams.INSTALL_COOLDOWN_WINDOW,
                SourceFixture.ValidSourceParams.DEBUG_KEY,
                SourceFixture.ValidSourceParams.ATTRIBUTION_MODE,
                SourceFixture.ValidSourceParams.buildAggregateSource(),
                SourceFixture.ValidSourceParams.buildFilterData());
    }

    @Test
    public void getReportingTime_eventSourceAppDestination() {
        long triggerTime = System.currentTimeMillis();
        long expiryTime = triggerTime + TimeUnit.DAYS.toMillis(30);
        long sourceEventTime = triggerTime - TimeUnit.DAYS.toMillis(1);
        Source source =
                SourceFixture.getValidSourceBuilder()
                        .setSourceType(Source.SourceType.EVENT)
                        .setExpiryTime(expiryTime)
                        .setEventTime(sourceEventTime)
                        .build();
        assertEquals(
                expiryTime + TimeUnit.HOURS.toMillis(1),
                source.getReportingTime(triggerTime, EventSurfaceType.APP));
    }

    @Test
    public void getReportingTime_eventSrcInstallAttributedAppDestinationTrigger1stWindow() {
        long triggerTime = System.currentTimeMillis();
        long expiryTime = triggerTime + TimeUnit.DAYS.toMillis(30);
        long sourceEventTime = triggerTime - TimeUnit.DAYS.toMillis(1);
        Source source =
                SourceFixture.getValidSourceBuilder()
                        .setSourceType(Source.SourceType.EVENT)
                        .setExpiryTime(expiryTime)
                        .setEventTime(sourceEventTime)
                        .setInstallAttributed(true)
                        .build();
        assertEquals(
                sourceEventTime
                        + PrivacyParams.INSTALL_ATTR_EVENT_EARLY_REPORTING_WINDOW_MILLISECONDS[0]
                        + TimeUnit.HOURS.toMillis(1),
                source.getReportingTime(triggerTime, EventSurfaceType.APP));
    }

    @Test
    public void getReportingTime_eventSrcInstallAttributedAppDestinationTrigger2ndWindow() {
        long triggerTime = System.currentTimeMillis();
        long expiryTime = triggerTime + TimeUnit.DAYS.toMillis(30);
        long sourceEventTime = triggerTime - TimeUnit.DAYS.toMillis(3);
        Source source =
                SourceFixture.getValidSourceBuilder()
                        .setSourceType(Source.SourceType.EVENT)
                        .setExpiryTime(expiryTime)
                        .setEventTime(sourceEventTime)
                        .setInstallAttributed(true)
                        .build();
        assertEquals(
                expiryTime + TimeUnit.HOURS.toMillis(1),
                source.getReportingTime(triggerTime, EventSurfaceType.APP));
    }

    @Test
    public void getReportingTime_eventSrcInstallAttributedWebDestinationTrigger1stWindow() {
        long triggerTime = System.currentTimeMillis();
        long expiryTime = triggerTime + TimeUnit.DAYS.toMillis(30);
        long sourceEventTime = triggerTime - TimeUnit.DAYS.toMillis(1);
        Source source =
                SourceFixture.getValidSourceBuilder()
                        .setSourceType(Source.SourceType.EVENT)
                        .setExpiryTime(expiryTime)
                        .setEventTime(sourceEventTime)
                        .setInstallAttributed(true)
                        .build();
        assertEquals(
                expiryTime + TimeUnit.HOURS.toMillis(1),
                source.getReportingTime(triggerTime, EventSurfaceType.WEB));
    }

    @Test
    public void getReportingTime_eventSrcInstallAttributedWebDestinationTrigger2ndWindow() {
        long triggerTime = System.currentTimeMillis();
        long expiryTime = triggerTime + TimeUnit.DAYS.toMillis(30);
        long sourceEventTime = triggerTime - TimeUnit.DAYS.toMillis(3);
        Source source =
                SourceFixture.getValidSourceBuilder()
                        .setSourceType(Source.SourceType.EVENT)
                        .setExpiryTime(expiryTime)
                        .setEventTime(sourceEventTime)
                        .setInstallAttributed(true)
                        .build();
        assertEquals(
                expiryTime + TimeUnit.HOURS.toMillis(1),
                source.getReportingTime(triggerTime, EventSurfaceType.WEB));
    }

    @Test
    public void getReportingTime_eventSourceWebDestination() {
        long triggerTime = System.currentTimeMillis();
        long expiryTime = triggerTime + TimeUnit.DAYS.toMillis(30);
        long sourceEventTime = triggerTime - TimeUnit.DAYS.toMillis(1);
        Source source =
                SourceFixture.getValidSourceBuilder()
                        .setSourceType(Source.SourceType.EVENT)
                        .setExpiryTime(expiryTime)
                        .setEventTime(sourceEventTime)
                        .build();
        assertEquals(
                expiryTime + TimeUnit.HOURS.toMillis(1),
                source.getReportingTime(triggerTime, EventSurfaceType.WEB));
    }

    @Test
    public void getReportingTime_navigationSourceTriggerInFirstWindow() {
        long triggerTime = System.currentTimeMillis();
        long sourceExpiryTime = triggerTime + TimeUnit.DAYS.toMillis(25);
        long sourceEventTime = triggerTime - TimeUnit.DAYS.toMillis(1);
        Source source =
                SourceFixture.getValidSourceBuilder()
                        .setSourceType(Source.SourceType.NAVIGATION)
                        .setExpiryTime(sourceExpiryTime)
                        .setEventTime(sourceEventTime)
                        .build();
        assertEquals(
                sourceEventTime
                        + PrivacyParams.NAVIGATION_EARLY_REPORTING_WINDOW_MILLISECONDS[0]
                        + TimeUnit.HOURS.toMillis(1),
                source.getReportingTime(triggerTime, EventSurfaceType.APP));
    }

    @Test
    public void getReportingTime_navigationSourceTriggerInSecondWindow() {
        long triggerTime = System.currentTimeMillis();
        long sourceExpiryTime = triggerTime + TimeUnit.DAYS.toMillis(25);
        long sourceEventTime = triggerTime - TimeUnit.DAYS.toMillis(3);
        Source source =
                SourceFixture.getValidSourceBuilder()
                        .setSourceType(Source.SourceType.NAVIGATION)
                        .setExpiryTime(sourceExpiryTime)
                        .setEventTime(sourceEventTime)
                        .build();
        assertEquals(
                sourceEventTime
                        + PrivacyParams.NAVIGATION_EARLY_REPORTING_WINDOW_MILLISECONDS[1]
                        + TimeUnit.HOURS.toMillis(1),
                source.getReportingTime(triggerTime, EventSurfaceType.APP));
    }

    @Test
    public void getReportingTime_navigationSecondExpiry() {
        long triggerTime = System.currentTimeMillis();
        long sourceExpiryTime = triggerTime + TimeUnit.DAYS.toMillis(2);
        long sourceEventTime = triggerTime - TimeUnit.DAYS.toMillis(3);
        Source source =
                SourceFixture.getValidSourceBuilder()
                        .setSourceType(Source.SourceType.NAVIGATION)
                        .setExpiryTime(sourceExpiryTime)
                        .setEventTime(sourceEventTime)
                        .build();
        assertEquals(
                sourceExpiryTime + TimeUnit.HOURS.toMillis(1),
                source.getReportingTime(triggerTime, EventSurfaceType.APP));
    }

    @Test
    public void getReportingTime_navigationLast() {
        long triggerTime = System.currentTimeMillis();
        long sourceExpiryTime = triggerTime + TimeUnit.DAYS.toMillis(1);
        long sourceEventTime = triggerTime - TimeUnit.DAYS.toMillis(20);
        Source source =
                SourceFixture.getValidSourceBuilder()
                        .setSourceType(Source.SourceType.NAVIGATION)
                        .setExpiryTime(sourceExpiryTime)
                        .setEventTime(sourceEventTime)
                        .build();
        assertEquals(
                sourceExpiryTime + TimeUnit.HOURS.toMillis(1),
                source.getReportingTime(triggerTime, EventSurfaceType.APP));
    }

    @Test
    public void testAggregatableAttributionSource() throws Exception {
        final Map<String, BigInteger> aggregatableSource = Map.of("2", new BigInteger("71"));
        final Map<String, List<String>> filterMap = Map.of("x", List.of("1"));
        final AggregatableAttributionSource attributionSource =
                new AggregatableAttributionSource.Builder()
                        .setAggregatableSource(aggregatableSource)
                        .setFilterMap(
                                new FilterMap.Builder()
                                        .setAttributionFilterMap(filterMap)
                                        .build())
                        .build();

        final Source source =
                SourceFixture.getValidSourceBuilder()
                        .setAggregatableAttributionSource(attributionSource)
                        .build();

        assertNotNull(source.getAggregatableAttributionSource());
        assertNotNull(source.getAggregatableAttributionSource().getAggregatableSource());
        assertNotNull(source.getAggregatableAttributionSource().getFilterMap());
        assertEquals(
                aggregatableSource,
                source.getAggregatableAttributionSource().getAggregatableSource());
        assertEquals(
                filterMap,
                source.getAggregatableAttributionSource()
                        .getFilterMap()
                        .getAttributionFilterMap());
    }

    @Test
    public void testTriggerDataCardinality() {
        Source eventSource = SourceFixture.getValidSourceBuilder()
                .setSourceType(Source.SourceType.EVENT)
                .build();
        assertEquals(
                PrivacyParams.EVENT_TRIGGER_DATA_CARDINALITY,
                eventSource.getTriggerDataCardinality());
        Source navigationSource = SourceFixture.getValidSourceBuilder()
                .setSourceType(Source.SourceType.NAVIGATION)
                .build();
        assertEquals(
                PrivacyParams.getNavigationTriggerDataCardinality(),
                navigationSource.getTriggerDataCardinality());
    }

    @Test
    public void testMaxReportCount() {
        Source eventSourceInstallNotAttributed =
                SourceFixture.getValidSourceBuilder()
                        .setSourceType(Source.SourceType.EVENT)
                        .setInstallAttributed(false)
                        .build();
        assertEquals(
                PrivacyParams.EVENT_SOURCE_MAX_REPORTS,
                eventSourceInstallNotAttributed.getMaxReportCount(EventSurfaceType.APP));
        assertEquals(
                PrivacyParams.EVENT_SOURCE_MAX_REPORTS,
                eventSourceInstallNotAttributed.getMaxReportCount(EventSurfaceType.WEB));

        Source navigationSourceInstallNotAttributed =
                SourceFixture.getValidSourceBuilder()
                        .setSourceType(Source.SourceType.NAVIGATION)
                        .setInstallAttributed(false)
                        .build();
        assertEquals(
                PrivacyParams.NAVIGATION_SOURCE_MAX_REPORTS,
                navigationSourceInstallNotAttributed.getMaxReportCount(
                        EventSurfaceType.APP));
        assertEquals(
                PrivacyParams.NAVIGATION_SOURCE_MAX_REPORTS,
                navigationSourceInstallNotAttributed.getMaxReportCount(
                        EventSurfaceType.WEB));

        Source eventSourceInstallAttributed =
                SourceFixture.getValidSourceBuilder()
                        .setSourceType(Source.SourceType.EVENT)
                        .setInstallAttributed(true)
                        .build();
        assertEquals(
                PrivacyParams.INSTALL_ATTR_EVENT_SOURCE_MAX_REPORTS,
                eventSourceInstallAttributed.getMaxReportCount(EventSurfaceType.APP));
        // Install attribution state does not matter for web destination
        assertEquals(
                PrivacyParams.EVENT_SOURCE_MAX_REPORTS,
                eventSourceInstallAttributed.getMaxReportCount(EventSurfaceType.WEB));

        Source navigationSourceInstallAttributed =
                SourceFixture.getValidSourceBuilder()
                        .setSourceType(Source.SourceType.NAVIGATION)
                        .setInstallAttributed(true)
                        .build();
        assertEquals(
                PrivacyParams.NAVIGATION_SOURCE_MAX_REPORTS,
                navigationSourceInstallAttributed.getMaxReportCount(EventSurfaceType.APP));
        assertEquals(
                PrivacyParams.NAVIGATION_SOURCE_MAX_REPORTS,
                navigationSourceInstallAttributed.getMaxReportCount(EventSurfaceType.WEB));
    }

    @Test
    public void testRandomAttributionProbability() {
        assertEquals(
                PrivacyParams.EVENT_NOISE_PROBABILITY,
                SourceFixture.getValidSourceBuilder()
                        .setSourceType(Source.SourceType.EVENT)
                        .setAppDestination(SourceFixture.ValidSourceParams.ATTRIBUTION_DESTINATION)
                        .build()
                        .getRandomAttributionProbability(),
                ZERO_DELTA);
        assertEquals(
                PrivacyParams.NAVIGATION_NOISE_PROBABILITY,
                SourceFixture.getValidSourceBuilder()
                        .setSourceType(Source.SourceType.NAVIGATION)
                        .setAppDestination(SourceFixture.ValidSourceParams.ATTRIBUTION_DESTINATION)
                        .build()
                        .getRandomAttributionProbability(),
                ZERO_DELTA);

        assertEquals(
                PrivacyParams.INSTALL_ATTR_EVENT_NOISE_PROBABILITY,
                SourceFixture.getValidSourceBuilder()
                        .setSourceType(Source.SourceType.EVENT)
                        .setAppDestination(SourceFixture.ValidSourceParams.ATTRIBUTION_DESTINATION)
                        .setInstallCooldownWindow(1)
                        .build()
                        .getRandomAttributionProbability(),
                ZERO_DELTA);

        assertEquals(
                PrivacyParams.INSTALL_ATTR_NAVIGATION_NOISE_PROBABILITY,
                SourceFixture.getValidSourceBuilder()
                        .setSourceType(Source.SourceType.NAVIGATION)
                        .setAppDestination(SourceFixture.ValidSourceParams.ATTRIBUTION_DESTINATION)
                        .setInstallCooldownWindow(1)
                        .build()
                        .getRandomAttributionProbability(),
                ZERO_DELTA);

        assertEquals(
                PrivacyParams.INSTALL_ATTR_DUAL_DESTINATION_EVENT_NOISE_PROBABILITY,
                SourceFixture.getValidSourceBuilder()
                        .setSourceType(Source.SourceType.EVENT)
                        .setAppDestination(SourceFixture.ValidSourceParams.ATTRIBUTION_DESTINATION)
                        .setWebDestination(SourceFixture.ValidSourceParams.WEB_DESTINATION)
                        .setInstallCooldownWindow(1)
                        .build()
                        .getRandomAttributionProbability(),
                ZERO_DELTA);

        assertEquals(
                PrivacyParams.INSTALL_ATTR_DUAL_DESTINATION_NAVIGATION_NOISE_PROBABILITY,
                SourceFixture.getValidSourceBuilder()
                        .setSourceType(Source.SourceType.NAVIGATION)
                        .setAppDestination(SourceFixture.ValidSourceParams.ATTRIBUTION_DESTINATION)
                        .setWebDestination(SourceFixture.ValidSourceParams.WEB_DESTINATION)
                        .setInstallCooldownWindow(1)
                        .build()
                        .getRandomAttributionProbability(),
                ZERO_DELTA);

        assertEquals(
                PrivacyParams.DUAL_DESTINATION_EVENT_NOISE_PROBABILITY,
                SourceFixture.getValidSourceBuilder()
                        .setSourceType(Source.SourceType.EVENT)
                        .setAppDestination(SourceFixture.ValidSourceParams.ATTRIBUTION_DESTINATION)
                        .setWebDestination(SourceFixture.ValidSourceParams.WEB_DESTINATION)
                        .build()
                        .getRandomAttributionProbability(),
                ZERO_DELTA);

        assertEquals(
                PrivacyParams.DUAL_DESTINATION_NAVIGATION_NOISE_PROBABILITY,
                SourceFixture.getValidSourceBuilder()
                        .setSourceType(Source.SourceType.NAVIGATION)
                        .setAppDestination(SourceFixture.ValidSourceParams.ATTRIBUTION_DESTINATION)
                        .setWebDestination(SourceFixture.ValidSourceParams.WEB_DESTINATION)
                        .build()
                        .getRandomAttributionProbability(),
                ZERO_DELTA);
    }

    @Test
    public void testFakeReportGeneration() {
        long expiry = System.currentTimeMillis();
        // Single (App) destination, EVENT type
        verifyAlgorithmicFakeReportGeneration(
                spy(
                        SourceFixture.getValidSourceBuilder()
                                .setSourceType(Source.SourceType.EVENT)
                                .setAppDestination(
                                        SourceFixture.ValidSourceParams.ATTRIBUTION_DESTINATION)
                                .setWebDestination(null)
                                .setExpiryTime(expiry)
                                .build()),
                PrivacyParams.EVENT_TRIGGER_DATA_CARDINALITY);

        // Single (App) destination, NAVIGATION type
        verifyAlgorithmicFakeReportGeneration(
                spy(
                        SourceFixture.getValidSourceBuilder()
                                .setSourceType(Source.SourceType.EVENT)
                                .setAppDestination(
                                        SourceFixture.ValidSourceParams.ATTRIBUTION_DESTINATION)
                                .setWebDestination(null)
                                .setExpiryTime(expiry)
                                .build()),
                PrivacyParams.getNavigationTriggerDataCardinality());

        // Single (Web) destination, EVENT type
        verifyAlgorithmicFakeReportGeneration(
                spy(
                        SourceFixture.getValidSourceBuilder()
                                .setSourceType(Source.SourceType.EVENT)
                                .setExpiryTime(expiry)
                                .setAppDestination(null)
                                .setWebDestination(SourceFixture.ValidSourceParams.WEB_DESTINATION)
                                .build()),
                PrivacyParams.EVENT_TRIGGER_DATA_CARDINALITY);

        // Single (Web) destination, NAVIGATION type
        verifyAlgorithmicFakeReportGeneration(
                spy(
                        SourceFixture.getValidSourceBuilder()
                                .setSourceType(Source.SourceType.EVENT)
                                .setExpiryTime(expiry)
                                .setAppDestination(null)
                                .setWebDestination(SourceFixture.ValidSourceParams.WEB_DESTINATION)
                                .build()),
                PrivacyParams.getNavigationTriggerDataCardinality());

        // Both destinations set, EVENT type
        verifyAlgorithmicFakeReportGeneration(
                spy(
                        SourceFixture.getValidSourceBuilder()
                                .setSourceType(Source.SourceType.EVENT)
                                .setExpiryTime(expiry)
                                .setAppDestination(
                                        SourceFixture.ValidSourceParams.ATTRIBUTION_DESTINATION)
                                .setWebDestination(SourceFixture.ValidSourceParams.WEB_DESTINATION)
                                .build()),
                PrivacyParams.EVENT_TRIGGER_DATA_CARDINALITY);

        // Both destinations set, NAVIGATION type
        verifyAlgorithmicFakeReportGeneration(
                spy(
                        SourceFixture.getValidSourceBuilder()
                                .setSourceType(Source.SourceType.EVENT)
                                .setExpiryTime(expiry)
                                .setAppDestination(
                                        SourceFixture.ValidSourceParams.ATTRIBUTION_DESTINATION)
                                .setWebDestination(SourceFixture.ValidSourceParams.WEB_DESTINATION)
                                .build()),
                PrivacyParams.getNavigationTriggerDataCardinality());

        // App destination with cooldown window
        verifyAlgorithmicFakeReportGeneration(
                spy(
                        SourceFixture.getValidSourceBuilder()
                                .setSourceType(Source.SourceType.EVENT)
                                .setExpiryTime(expiry)
                                .setAppDestination(
                                        SourceFixture.ValidSourceParams.ATTRIBUTION_DESTINATION)
                                .setWebDestination(null)
                                .setInstallCooldownWindow(
                                        SourceFixture.ValidSourceParams.INSTALL_COOLDOWN_WINDOW)
                                .build()),
                PrivacyParams.EVENT_TRIGGER_DATA_CARDINALITY);
    }

    @Test
    public void fakeReports_eventSourceDualDestPostInstallMode_generatesFromStaticReportStates() {
        long expiry = System.currentTimeMillis();
        Source source =
                spy(
                        SourceFixture.getValidSourceBuilder()
                                .setSourceType(Source.SourceType.EVENT)
                                .setWebDestination(SourceFixture.ValidSourceParams.WEB_DESTINATION)
                                .setExpiryTime(expiry)
                                .setInstallCooldownWindow(
                                        SourceFixture.ValidSourceParams.INSTALL_COOLDOWN_WINDOW)
                                .build());
        // Increase the probability of random attribution.
        doReturn(0.50D).when(source).getRandomAttributionProbability();
        int falseCount = 0;
        int neverCount = 0;
        int truthCount = 0;
        for (int i = 0; i < 500; i++) {
            List<Source.FakeReport> fakeReports =
                    source.assignAttributionModeAndGenerateFakeReports();
            if (source.getAttributionMode() == Source.AttributionMode.FALSELY) {
                falseCount++;
                assertNotEquals(0, fakeReports.size());
                assertTrue(
                        isValidEventSourceDualDestPostInstallModeFakeReportState(
                                source, fakeReports));
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
        Source eventSource30dExpiry = SourceFixture.getValidSourceBuilder()
                .setSourceType(Source.SourceType.EVENT)
                .setEventTime(eventTime)
                .setExpiryTime(eventTime + TimeUnit.DAYS.toMillis(30))
                .build();
        assertEquals(
                new ImpressionNoiseParams(
                        /* reportCount= */ 1,
                        /* triggerDataCardinality= */ 2,
                        /* reportingWindowCount= */ 1,
                        /* destinationMultiplier */ 1),
                eventSource30dExpiry.getImpressionNoiseParams());

        Source eventSource7dExpiry = SourceFixture.getValidSourceBuilder()
                .setSourceType(Source.SourceType.EVENT)
                .setEventTime(eventTime)
                .setExpiryTime(eventTime + TimeUnit.DAYS.toMillis(30))
                .build();
        assertEquals(
                new ImpressionNoiseParams(
                        /* reportCount= */ 1,
                        /* triggerDataCardinality= */ 2,
                        /* reportingWindowCount= */ 1,
                        /* destinationMultiplier */ 1),
                eventSource7dExpiry.getImpressionNoiseParams());

        Source eventSource2dExpiry = SourceFixture.getValidSourceBuilder()
                .setSourceType(Source.SourceType.EVENT)
                .setEventTime(eventTime)
                .setExpiryTime(eventTime + TimeUnit.DAYS.toMillis(30))
                .build();
        assertEquals(
                new ImpressionNoiseParams(
                        /* reportCount= */ 1,
                        /* triggerDataCardinality= */ 2,
                        /* reportingWindowCount= */ 1,
                        /* destinationMultiplier */ 1),
                eventSource2dExpiry.getImpressionNoiseParams());

        Source navigationSource30dExpiry = SourceFixture.getValidSourceBuilder()
                .setSourceType(Source.SourceType.NAVIGATION)
                .setEventTime(eventTime)
                .setExpiryTime(eventTime + TimeUnit.DAYS.toMillis(30))
                .build();
        assertEquals(
                new ImpressionNoiseParams(
                        /* reportCount= */ 3,
                        /* triggerDataCardinality= */ 8,
                        /* reportingWindowCount= */ 3,
                        /* destinationMultiplier */ 1),
                navigationSource30dExpiry.getImpressionNoiseParams());

        Source navigationSource7dExpiry = SourceFixture.getValidSourceBuilder()
                .setSourceType(Source.SourceType.NAVIGATION)
                .setEventTime(eventTime)
                .setExpiryTime(eventTime + TimeUnit.DAYS.toMillis(7))
                .build();
        assertEquals(
                new ImpressionNoiseParams(
                        /* reportCount= */ 3,
                        /* triggerDataCardinality= */ 8,
                        /* reportingWindowCount= */ 2,
                        /* destinationMultiplier */ 1),
                navigationSource7dExpiry.getImpressionNoiseParams());

        Source navigationSource2dExpiry = SourceFixture.getValidSourceBuilder()
                .setSourceType(Source.SourceType.NAVIGATION)
                .setEventTime(eventTime)
                .setExpiryTime(eventTime + TimeUnit.DAYS.toMillis(2))
                .build();
        assertEquals(
                new ImpressionNoiseParams(
                        /* reportCount= */ 3,
                        /* triggerDataCardinality= */ 8,
                        /* reportingWindowCount= */ 1,
                        /* destinationMultiplier */ 1),
                navigationSource2dExpiry.getImpressionNoiseParams());
    }

    @Test
    public void impressionNoiseParamGeneration_withInstallAttribution() {
        long eventTime = System.currentTimeMillis();

        Source eventSource30dExpiry = SourceFixture.getValidSourceBuilder()
                .setSourceType(Source.SourceType.EVENT)
                .setInstallCooldownWindow(TimeUnit.DAYS.toMillis(2))
                .setInstallAttributionWindow(TimeUnit.DAYS.toMillis(10))
                .setEventTime(eventTime)
                .setExpiryTime(eventTime + TimeUnit.DAYS.toMillis(30))
                .build();
        assertEquals(
                new ImpressionNoiseParams(
                        /* reportCount= */ 2,
                        /* triggerDataCardinality= */ 2,
                        /* reportingWindowCount= */ 2,
                        /* destinationMultiplier */ 1),
                eventSource30dExpiry.getImpressionNoiseParams());

        Source eventSource7dExpiry = SourceFixture.getValidSourceBuilder()
                .setSourceType(Source.SourceType.EVENT)
                .setInstallCooldownWindow(TimeUnit.DAYS.toMillis(2))
                .setInstallAttributionWindow(TimeUnit.DAYS.toMillis(10))
                .setEventTime(eventTime)
                .setExpiryTime(eventTime + TimeUnit.DAYS.toMillis(7))
                .build();
        assertEquals(
                new ImpressionNoiseParams(
                        /* reportCount= */ 2,
                        /* triggerDataCardinality= */ 2,
                        /* reportingWindowCount= */ 2,
                        /* destinationMultiplier */ 1),
                eventSource7dExpiry.getImpressionNoiseParams());

        Source eventSource2dExpiry = SourceFixture.getValidSourceBuilder()
                .setSourceType(Source.SourceType.EVENT)
                .setInstallCooldownWindow(TimeUnit.DAYS.toMillis(2))
                .setInstallAttributionWindow(TimeUnit.DAYS.toMillis(10))
                .setEventTime(eventTime)
                .setExpiryTime(eventTime + TimeUnit.DAYS.toMillis(2))
                .build();
        assertEquals(
                new ImpressionNoiseParams(
                        /* reportCount= */ 2,
                        /* triggerDataCardinality= */ 2,
                        /* reportingWindowCount= */ 1,
                        /* destinationMultiplier */ 1),
                eventSource2dExpiry.getImpressionNoiseParams());

        Source navigationSource30dExpiry = SourceFixture.getValidSourceBuilder()
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
                        /* reportingWindowCount= */ 3,
                        /* destinationMultiplier */ 1),
                navigationSource30dExpiry.getImpressionNoiseParams());

        Source navigationSource7dExpiry = SourceFixture.getValidSourceBuilder()
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
                        /* reportingWindowCount= */ 2,
                        /* destinationMultiplier */ 1),
                navigationSource7dExpiry.getImpressionNoiseParams());

        Source navigationSource2dExpiry = SourceFixture.getValidSourceBuilder()
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
                        /* reportingWindowCount= */ 1,
                        /* destinationMultiplier */ 1),
                navigationSource2dExpiry.getImpressionNoiseParams());
        Source eventSourceWith2Destinations30dExpiry =
                SourceFixture.getValidSourceBuilder()
                        .setWebDestination(SourceFixture.ValidSourceParams.WEB_DESTINATION)
                        .setSourceType(Source.SourceType.EVENT)
                        .setInstallCooldownWindow(TimeUnit.DAYS.toMillis(2))
                        .setInstallAttributionWindow(TimeUnit.DAYS.toMillis(10))
                        .setEventTime(eventTime)
                        .setExpiryTime(eventTime + TimeUnit.DAYS.toMillis(30))
                        .build();
        assertEquals(
                new ImpressionNoiseParams(
                        /* reportCount= */ 2,
                        /* triggerDataCardinality= */ 2,
                        /* reportingWindowCount= */ 2,
                        /* destinationMultiplier */ 2),
                eventSourceWith2Destinations30dExpiry.getImpressionNoiseParams());
    }

    @Test
    public void reportingTimeByIndex_event() {
        long eventTime = System.currentTimeMillis();
        long oneHourInMillis = TimeUnit.HOURS.toMillis(1);

        // Expected: 1 window at expiry
        Source eventSource10d = SourceFixture.getValidSourceBuilder()
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
        Source eventSource7d = SourceFixture.getValidSourceBuilder()
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
        Source eventSource2d = SourceFixture.getValidSourceBuilder()
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
        Source eventSource10d = SourceFixture.getValidSourceBuilder()
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
        Source eventSource2d = SourceFixture.getValidSourceBuilder()
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
        Source navigationSource20d = SourceFixture.getValidSourceBuilder()
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
        Source navigationSource7d = SourceFixture.getValidSourceBuilder()
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
        Source navigationSource2d = SourceFixture.getValidSourceBuilder()
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
        Source navigationSource20d = SourceFixture.getValidSourceBuilder()
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
        Source navigationSource7d = SourceFixture.getValidSourceBuilder()
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
        Source navigationSource2d = SourceFixture.getValidSourceBuilder()
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
    public void testParseFilterData_nonEmpty() throws JSONException {
        JSONObject filterMapJson = new JSONObject();
        filterMapJson.put("conversion", new JSONArray(Collections.singletonList("electronics")));
        filterMapJson.put("product", new JSONArray(Arrays.asList("1234", "2345")));
        Source source = SourceFixture.getValidSourceBuilder()
                .setSourceType(Source.SourceType.NAVIGATION)
                .setFilterData(filterMapJson.toString())
                .build();
        FilterMap filterMap = source.parseFilterData();
        assertEquals(filterMap.getAttributionFilterMap().size(), 3);
        assertEquals(Collections.singletonList("electronics"),
                filterMap.getAttributionFilterMap().get("conversion"));
        assertEquals(Arrays.asList("1234", "2345"),
                filterMap.getAttributionFilterMap().get("product"));
        assertEquals(Collections.singletonList("navigation"),
                filterMap.getAttributionFilterMap().get("source_type"));
    }

    @Test
    public void testParseFilterData_nullFilterData() throws JSONException {
        Source source = SourceFixture.getValidSourceBuilder()
                .setSourceType(Source.SourceType.EVENT)
                .build();
        FilterMap filterMap = source.parseFilterData();
        assertEquals(filterMap.getAttributionFilterMap().size(), 1);
        assertEquals(Collections.singletonList("event"),
                filterMap.getAttributionFilterMap().get("source_type"));
    }

    @Test
    public void testParseFilterData_emptyFilterData() throws JSONException {
        Source source = SourceFixture.getValidSourceBuilder()
                .setSourceType(Source.SourceType.EVENT)
                .setFilterData("")
                .build();
        FilterMap filterMap = source.parseFilterData();
        assertEquals(filterMap.getAttributionFilterMap().size(), 1);
        assertEquals(Collections.singletonList("event"),
                filterMap.getAttributionFilterMap().get("source_type"));
    }

    @Test
    public void testParseAggregateSource() throws JSONException {
        JSONObject aggregatableSource = new JSONObject();
        aggregatableSource.put("campaignCounts", "0x159");
        aggregatableSource.put("geoValue", "0x5");

        JSONObject filterMap = new JSONObject();
        filterMap.put("conversion_subdomain",
                new JSONArray(Collections.singletonList("electronics.megastore")));
        filterMap.put("product", new JSONArray(Arrays.asList("1234", "2345")));

        Source source = SourceFixture.getValidSourceBuilder()
                .setSourceType(Source.SourceType.NAVIGATION)
                .setAggregateSource(aggregatableSource.toString())
                .setFilterData(filterMap.toString()).build();
        Optional<AggregatableAttributionSource> aggregatableAttributionSource =
                source.parseAggregateSource();
        assertTrue(aggregatableAttributionSource.isPresent());
        AggregatableAttributionSource aggregateSource = aggregatableAttributionSource.get();
        assertEquals(aggregateSource.getAggregatableSource().size(), 2);
        assertEquals(
                aggregateSource.getAggregatableSource().get("campaignCounts").longValue(), 345L);
        assertEquals(aggregateSource.getAggregatableSource().get("geoValue").longValue(), 5L);
        assertEquals(aggregateSource.getFilterMap().getAttributionFilterMap().size(), 3);
    }

    private void verifyAlgorithmicFakeReportGeneration(Source source, int expectedCardinality) {
        // Increase the probability of random attribution.
        doReturn(0.50D).when(source).getRandomAttributionProbability();
        int falseCount = 0;
        int neverCount = 0;
        int truthCount = 0;
        for (int i = 0; i < 500; i++) {
            List<Source.FakeReport> fakeReports =
                    source.assignAttributionModeAndGenerateFakeReports();
            if (source.getAttributionMode() == Source.AttributionMode.FALSELY) {
                falseCount++;
                assertNotEquals(0, fakeReports.size());
                for (Source.FakeReport report : fakeReports) {
                    assertTrue(
                            source.getExpiryTime() + TimeUnit.HOURS.toMillis(1)
                                    >= report.getReportingTime());
                    Long triggerData = report.getTriggerData().getValue();
                    assertTrue(0 <= triggerData && triggerData < expectedCardinality);
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

    private boolean isValidEventSourceDualDestPostInstallModeFakeReportState(
            Source source, List<Source.FakeReport> fakeReportsState) {
        // Generated fake reports state matches one of the states
        return Arrays.stream(ImpressionNoiseUtil.DUAL_DESTINATION_POST_INSTALL_FAKE_REPORT_CONFIG)
                .map(reportsState -> convertToReportsState(reportsState, source))
                .anyMatch(fakeReportsState::equals);
    }

    private List<Source.FakeReport> convertToReportsState(int[][] reportsState, Source source) {
        return Arrays.stream(reportsState)
                .map(
                        reportState ->
                                new Source.FakeReport(
                                        new UnsignedLong(Long.valueOf(reportState[0])),
                                        source.getReportingTimeForNoising(reportState[1]),
                                        reportState[2] == 0
                                                ? source.getAppDestination()
                                                : source.getWebDestination()))
                .collect(Collectors.toList());
    }

    private void assertInvalidSourceArguments(
            UnsignedLong sourceEventId,
            Uri publisher,
            Uri appDestination,
            Uri webDestination,
            String enrollmentId,
            Uri registrant,
            Long sourceEventTime,
            Long expiryTime,
            Long priority,
            Source.SourceType sourceType,
            Long installAttributionWindow,
            Long installCooldownWindow,
            @Nullable UnsignedLong debugKey,
            @Source.AttributionMode int attributionMode,
            @Nullable String aggregateSource,
            @Nullable String filterData) {
        assertThrows(
                IllegalArgumentException.class,
                () ->
                        new Source.Builder()
                                .setEventId(sourceEventId)
                                .setPublisher(publisher)
                                .setAppDestination(appDestination)
                                .setWebDestination(webDestination)
                                .setEnrollmentId(enrollmentId)
                                .setRegistrant(registrant)
                                .setEventTime(sourceEventTime)
                                .setExpiryTime(expiryTime)
                                .setPriority(priority)
                                .setSourceType(sourceType)
                                .setInstallAttributionWindow(installAttributionWindow)
                                .setInstallCooldownWindow(installCooldownWindow)
                                .setAttributionMode(attributionMode)
                                .setAggregateSource(aggregateSource)
                                .setFilterData(filterData)
                                .setDebugKey(debugKey)
                                .build());
    }
}

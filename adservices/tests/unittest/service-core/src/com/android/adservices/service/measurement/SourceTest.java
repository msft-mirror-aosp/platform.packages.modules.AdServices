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

import static org.junit.Assert.assertArrayEquals;
import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertNotEquals;
import static org.junit.Assert.assertNotNull;
import static org.junit.Assert.assertThrows;
import static org.junit.Assert.assertTrue;

import android.net.Uri;

import androidx.annotation.Nullable;

import com.android.adservices.service.measurement.aggregation.AggregatableAttributionSource;
import com.android.adservices.service.measurement.util.UnsignedLong;

import org.json.JSONArray;
import org.json.JSONException;
import org.json.JSONObject;
import org.junit.Test;

import java.math.BigInteger;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collections;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.Random;
import java.util.TreeMap;
import java.util.concurrent.TimeUnit;
import java.util.stream.Collectors;
import java.util.stream.LongStream;

public class SourceTest {

    private static final UnsignedLong DEBUG_KEY_1 = new UnsignedLong(81786463L);
    private static final UnsignedLong DEBUG_KEY_2 = new UnsignedLong(23487834L);

    @Test
    public void testDefaults() {
        Source source = SourceFixture.getMinimalValidSourceBuilder().build();
        assertEquals(0, source.getEventReportDedupKeys().size());
        assertEquals(0, source.getAggregateReportDedupKeys().size());
        assertEquals(Source.Status.ACTIVE, source.getStatus());
        assertEquals(Source.SourceType.EVENT, source.getSourceType());
        assertEquals(Source.AttributionMode.UNASSIGNED, source.getAttributionMode());
    }

    @Test
    public void testEqualsPass() throws JSONException {
        assertEquals(
                SourceFixture.getMinimalValidSourceBuilder().build(),
                SourceFixture.getMinimalValidSourceBuilder().build());
        JSONObject aggregateSource = new JSONObject();
        aggregateSource.put("campaignCounts", "0x159");
        aggregateSource.put("geoValue", "0x5");

        JSONObject filterMap = new JSONObject();
        filterMap.put(
                "conversion_subdomain", Collections.singletonList("electronics.megastore"));
        filterMap.put("product", Arrays.asList("1234", "2345"));

        String sharedAggregateKeys = "[\"campaignCounts\"]";
        String parentId = "parent-id";
        String debugJoinKey = "SAMPLE_DEBUG_JOIN_KEY";
        String debugAppAdId = "SAMPLE_DEBUG_APP_ADID";
        String debugWebAdId = "SAMPLE_DEBUG_WEB_ADID";
        assertEquals(
                new Source.Builder()
                        .setEnrollmentId("enrollment-id")
                        .setAppDestinations(List.of(Uri.parse("android-app://example.test/aD1")))
                        .setWebDestinations(List.of(Uri.parse("https://example.test/aD2")))
                        .setPublisher(Uri.parse("https://example.test/aS"))
                        .setPublisherType(EventSurfaceType.WEB)
                        .setId("1")
                        .setEventId(new UnsignedLong(2L))
                        .setPriority(3L)
                        .setEventTime(5L)
                        .setExpiryTime(5L)
                        .setEventReportWindow(55L)
                        .setAggregatableReportWindow(555L)
                        .setIsDebugReporting(true)
                        .setEventReportDedupKeys(
                                LongStream.range(0, 2)
                                        .boxed()
                                        .map(UnsignedLong::new)
                                        .collect(Collectors.toList()))
                        .setAggregateReportDedupKeys(
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
                        .setRegistrationId("R1")
                        .setSharedAggregationKeys(sharedAggregateKeys)
                        .setInstallTime(100L)
                        .setParentId(parentId)
                        .setDebugJoinKey(debugJoinKey)
                        .setPlatformAdId(debugAppAdId)
                        .setDebugAdId(debugWebAdId)
                        .setRegistrationOrigin(WebUtil.validUri("https://subdomain.example.test"))
                        .setCoarseEventReportDestinations(true)
                        .build(),
                new Source.Builder()
                        .setEnrollmentId("enrollment-id")
                        .setAppDestinations(List.of(Uri.parse("android-app://example.test/aD1")))
                        .setWebDestinations(List.of(Uri.parse("https://example.test/aD2")))
                        .setPublisher(Uri.parse("https://example.test/aS"))
                        .setPublisherType(EventSurfaceType.WEB)
                        .setId("1")
                        .setEventId(new UnsignedLong(2L))
                        .setPriority(3L)
                        .setEventTime(5L)
                        .setExpiryTime(5L)
                        .setEventReportWindow(55L)
                        .setAggregatableReportWindow(555L)
                        .setIsDebugReporting(true)
                        .setEventReportDedupKeys(
                                LongStream.range(0, 2)
                                        .boxed()
                                        .map(UnsignedLong::new)
                                        .collect(Collectors.toList()))
                        .setAggregateReportDedupKeys(
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
                        .setRegistrationId("R1")
                        .setSharedAggregationKeys(sharedAggregateKeys)
                        .setInstallTime(100L)
                        .setParentId(parentId)
                        .setDebugJoinKey(debugJoinKey)
                        .setPlatformAdId(debugAppAdId)
                        .setDebugAdId(debugWebAdId)
                        .setRegistrationOrigin(WebUtil.validUri("https://subdomain.example.test"))
                        .setCoarseEventReportDestinations(true)
                        .build());
    }

    @Test
    public void testEqualsFail() throws JSONException {
        assertNotEquals(
                SourceFixture.getMinimalValidSourceBuilder()
                        .setEventId(new UnsignedLong(1L))
                        .build(),
                SourceFixture.getMinimalValidSourceBuilder()
                        .setEventId(new UnsignedLong(2L))
                        .build());
        assertNotEquals(
                SourceFixture.getMinimalValidSourceBuilder()
                        .setAppDestinations(List.of(Uri.parse("android-app://1.test")))
                        .build(),
                SourceFixture.getMinimalValidSourceBuilder()
                        .setAppDestinations(List.of(Uri.parse("android-app://2.test")))
                        .build());
        assertNotEquals(
                SourceFixture.getMinimalValidSourceBuilder()
                        .setWebDestinations(List.of(Uri.parse("https://1.test")))
                        .build(),
                SourceFixture.getMinimalValidSourceBuilder()
                        .setWebDestinations(List.of(Uri.parse("https://2.test")))
                        .build());
        assertNotEquals(
                SourceFixture.getMinimalValidSourceBuilder()
                        .setEnrollmentId("enrollment-id-1")
                        .build(),
                SourceFixture.getMinimalValidSourceBuilder()
                        .setEnrollmentId("enrollment-id-2")
                        .build());
        assertNotEquals(
                SourceFixture.getMinimalValidSourceBuilder()
                        .setPublisher(Uri.parse("https://1.test"))
                        .build(),
                SourceFixture.getMinimalValidSourceBuilder()
                        .setPublisher(Uri.parse("https://2.test"))
                        .build());
        assertNotEquals(
                SourceFixture.getMinimalValidSourceBuilder().setParentId("parent-id-1").build(),
                SourceFixture.getMinimalValidSourceBuilder().setParentId("parent-id-2").build());
        assertNotEquals(
                SourceFixture.getMinimalValidSourceBuilder()
                        .setPublisherType(EventSurfaceType.APP)
                        .build(),
                SourceFixture.getMinimalValidSourceBuilder()
                        .setPublisherType(EventSurfaceType.WEB)
                        .build());
        assertNotEquals(
                SourceFixture.getMinimalValidSourceBuilder().setPriority(1L).build(),
                SourceFixture.getMinimalValidSourceBuilder().setPriority(2L).build());
        assertNotEquals(
                SourceFixture.getMinimalValidSourceBuilder().setEventTime(1L).build(),
                SourceFixture.getMinimalValidSourceBuilder().setEventTime(2L).build());
        assertNotEquals(
                SourceFixture.getMinimalValidSourceBuilder().setExpiryTime(1L).build(),
                SourceFixture.getMinimalValidSourceBuilder().setExpiryTime(2L).build());
        assertNotEquals(
                SourceFixture.getMinimalValidSourceBuilder().setEventReportWindow(1L).build(),
                SourceFixture.getMinimalValidSourceBuilder().setEventReportWindow(2L).build());
        assertNotEquals(
                SourceFixture.getMinimalValidSourceBuilder()
                        .setAggregatableReportWindow(1L)
                        .build(),
                SourceFixture.getMinimalValidSourceBuilder()
                        .setAggregatableReportWindow(2L)
                        .build());
        assertNotEquals(
                SourceFixture.getMinimalValidSourceBuilder()
                        .setSourceType(Source.SourceType.EVENT)
                        .build(),
                SourceFixture.getMinimalValidSourceBuilder()
                        .setSourceType(Source.SourceType.NAVIGATION)
                        .build());
        assertNotEquals(
                SourceFixture.getMinimalValidSourceBuilder()
                        .setStatus(Source.Status.ACTIVE)
                        .build(),
                SourceFixture.getMinimalValidSourceBuilder()
                        .setStatus(Source.Status.IGNORED)
                        .build());
        assertNotEquals(
                SourceFixture.getMinimalValidSourceBuilder()
                        .setEventReportDedupKeys(
                                LongStream.range(0, 2)
                                        .boxed()
                                        .map(UnsignedLong::new)
                                        .collect(Collectors.toList()))
                        .build(),
                SourceFixture.getMinimalValidSourceBuilder()
                        .setEventReportDedupKeys(
                                LongStream.range(1, 3)
                                        .boxed()
                                        .map(UnsignedLong::new)
                                        .collect(Collectors.toList()))
                        .build());
        assertNotEquals(
                SourceFixture.getMinimalValidSourceBuilder()
                        .setAggregateReportDedupKeys(
                                LongStream.range(0, 2)
                                        .boxed()
                                        .map(UnsignedLong::new)
                                        .collect(Collectors.toList()))
                        .build(),
                SourceFixture.getMinimalValidSourceBuilder()
                        .setAggregateReportDedupKeys(
                                LongStream.range(1, 3)
                                        .boxed()
                                        .map(UnsignedLong::new)
                                        .collect(Collectors.toList()))
                        .build());
        assertNotEquals(
                SourceFixture.getMinimalValidSourceBuilder()
                        .setRegistrant(Uri.parse("android-app://com.example.abc"))
                        .build(),
                SourceFixture.getMinimalValidSourceBuilder()
                        .setRegistrant(Uri.parse("android-app://com.example.xyz"))
                        .build());
        assertNotEquals(
                SourceFixture.ValidSourceParams.buildAggregatableAttributionSource(),
                SourceFixture.getMinimalValidSourceBuilder()
                        .setAggregatableAttributionSource(
                                new AggregatableAttributionSource.Builder().build())
                        .build());
        JSONObject aggregateSource1 = new JSONObject();
        aggregateSource1.put("campaignCounts", "0x159");

        JSONObject aggregateSource2 = new JSONObject();
        aggregateSource2.put("geoValue", "0x5");

        assertNotEquals(
                SourceFixture.getMinimalValidSourceBuilder()
                        .setAggregateSource(aggregateSource1.toString())
                        .build(),
                SourceFixture.getMinimalValidSourceBuilder()
                        .setAggregateSource(aggregateSource2.toString())
                        .build());

        assertNotEquals(
                SourceFixture.getMinimalValidSourceBuilder()
                        .setAggregateContributions(4000)
                        .build(),
                SourceFixture.getMinimalValidSourceBuilder()
                        .setAggregateContributions(4055)
                        .build());

        assertNotEquals(
                SourceFixture.getMinimalValidSourceBuilder().setDebugKey(DEBUG_KEY_1).build(),
                SourceFixture.getMinimalValidSourceBuilder().setDebugKey(DEBUG_KEY_2).build());

        assertNotEquals(
                SourceFixture.getMinimalValidSourceBuilder().setRegistrationId("R1").build(),
                SourceFixture.getMinimalValidSourceBuilder().setRegistrationId("R2").build());

        String sharedAggregationKeys1 = "[\"key1\"]";
        String sharedAggregationKeys2 = "[\"key2\"]";

        assertNotEquals(
                SourceFixture.getMinimalValidSourceBuilder()
                        .setSharedAggregationKeys(sharedAggregationKeys1)
                        .build(),
                SourceFixture.getMinimalValidSourceBuilder()
                        .setSharedAggregationKeys(sharedAggregationKeys2)
                        .build());

        assertNotEquals(
                SourceFixture.getMinimalValidSourceBuilder().setInstallTime(100L).build(),
                SourceFixture.getMinimalValidSourceBuilder().setInstallTime(101L).build());
        assertNotEquals(
                SourceFixture.getMinimalValidSourceBuilder()
                        .setDebugJoinKey("debugJoinKey1")
                        .build(),
                SourceFixture.getMinimalValidSourceBuilder()
                        .setDebugJoinKey("debugJoinKey2")
                        .build());
        assertNotEquals(
                SourceFixture.getMinimalValidSourceBuilder()
                        .setPlatformAdId("debugAppAdId1")
                        .build(),
                SourceFixture.getMinimalValidSourceBuilder()
                        .setPlatformAdId("debugAppAdId2")
                        .build());
        assertNotEquals(
                SourceFixture.getMinimalValidSourceBuilder().setDebugAdId("debugWebAdId1").build(),
                SourceFixture.getMinimalValidSourceBuilder().setDebugAdId("debugWebAdId2").build());
        assertNotEquals(
                SourceFixture.getMinimalValidSourceBuilder()
                        .setRegistrationOrigin(WebUtil.validUri("https://subdomain1.example.test"))
                        .build(),
                SourceFixture.getMinimalValidSourceBuilder()
                        .setRegistrationOrigin(WebUtil.validUri("https://subdomain2.example.test"))
                        .build());
        assertNotEquals(
                SourceFixture.getMinimalValidSourceBuilder()
                        .setCoarseEventReportDestinations(false)
                        .build(),
                SourceFixture.getMinimalValidSourceBuilder()
                        .setCoarseEventReportDestinations(true)
                        .build());
    }

    @Test
    public void testSourceBuilder_validateArgumentPublisher() {
        assertInvalidSourceArguments(
                SourceFixture.ValidSourceParams.SOURCE_EVENT_ID,
                null,
                SourceFixture.ValidSourceParams.ATTRIBUTION_DESTINATIONS,
                SourceFixture.ValidSourceParams.WEB_DESTINATIONS,
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
                SourceFixture.ValidSourceParams.buildFilterData(),
                SourceFixture.ValidSourceParams.REGISTRATION_ID,
                SourceFixture.ValidSourceParams.SHARED_AGGREGATE_KEYS,
                SourceFixture.ValidSourceParams.INSTALL_TIME,
                SourceFixture.ValidSourceParams.REGISTRATION_ORIGIN);

        assertInvalidSourceArguments(
                SourceFixture.ValidSourceParams.SOURCE_EVENT_ID,
                Uri.parse("com.source"),
                SourceFixture.ValidSourceParams.ATTRIBUTION_DESTINATIONS,
                SourceFixture.ValidSourceParams.WEB_DESTINATIONS,
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
                SourceFixture.ValidSourceParams.buildFilterData(),
                SourceFixture.ValidSourceParams.REGISTRATION_ID,
                SourceFixture.ValidSourceParams.SHARED_AGGREGATE_KEYS,
                SourceFixture.ValidSourceParams.INSTALL_TIME,
                SourceFixture.ValidSourceParams.REGISTRATION_ORIGIN);
    }

    @Test
    public void testSourceBuilder_validateArgumentAttributionDestination() {
        // Invalid app Uri
        assertInvalidSourceArguments(
                SourceFixture.ValidSourceParams.SOURCE_EVENT_ID,
                SourceFixture.ValidSourceParams.PUBLISHER,
                List.of(Uri.parse("com.destination")),
                SourceFixture.ValidSourceParams.WEB_DESTINATIONS,
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
                SourceFixture.ValidSourceParams.buildFilterData(),
                SourceFixture.ValidSourceParams.REGISTRATION_ID,
                SourceFixture.ValidSourceParams.SHARED_AGGREGATE_KEYS,
                SourceFixture.ValidSourceParams.INSTALL_TIME,
                SourceFixture.ValidSourceParams.REGISTRATION_ORIGIN);

        // Invalid web Uri
        assertInvalidSourceArguments(
                SourceFixture.ValidSourceParams.SOURCE_EVENT_ID,
                SourceFixture.ValidSourceParams.PUBLISHER,
                SourceFixture.ValidSourceParams.ATTRIBUTION_DESTINATIONS,
                List.of(Uri.parse("com.destination")),
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
                SourceFixture.ValidSourceParams.buildFilterData(),
                SourceFixture.ValidSourceParams.REGISTRATION_ID,
                SourceFixture.ValidSourceParams.SHARED_AGGREGATE_KEYS,
                SourceFixture.ValidSourceParams.INSTALL_TIME,
                SourceFixture.ValidSourceParams.REGISTRATION_ORIGIN);

        // Empty app destinations list
        assertInvalidSourceArguments(
                SourceFixture.ValidSourceParams.SOURCE_EVENT_ID,
                SourceFixture.ValidSourceParams.PUBLISHER,
                new ArrayList<>(),
                SourceFixture.ValidSourceParams.WEB_DESTINATIONS,
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
                SourceFixture.ValidSourceParams.buildFilterData(),
                SourceFixture.ValidSourceParams.REGISTRATION_ID,
                SourceFixture.ValidSourceParams.SHARED_AGGREGATE_KEYS,
                SourceFixture.ValidSourceParams.INSTALL_TIME,
                SourceFixture.ValidSourceParams.REGISTRATION_ORIGIN);

        // Empty web destinations list
        assertInvalidSourceArguments(
                SourceFixture.ValidSourceParams.SOURCE_EVENT_ID,
                SourceFixture.ValidSourceParams.PUBLISHER,
                SourceFixture.ValidSourceParams.ATTRIBUTION_DESTINATIONS,
                new ArrayList<>(),
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
                SourceFixture.ValidSourceParams.buildFilterData(),
                SourceFixture.ValidSourceParams.REGISTRATION_ID,
                SourceFixture.ValidSourceParams.SHARED_AGGREGATE_KEYS,
                SourceFixture.ValidSourceParams.INSTALL_TIME,
                SourceFixture.ValidSourceParams.REGISTRATION_ORIGIN);

        // Too many app destinations
        assertInvalidSourceArguments(
                SourceFixture.ValidSourceParams.SOURCE_EVENT_ID,
                SourceFixture.ValidSourceParams.PUBLISHER,
                List.of(
                        Uri.parse("android-app://com.destination"),
                        Uri.parse("android-app://com.destination2")),
                SourceFixture.ValidSourceParams.WEB_DESTINATIONS,
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
                SourceFixture.ValidSourceParams.buildFilterData(),
                SourceFixture.ValidSourceParams.REGISTRATION_ID,
                SourceFixture.ValidSourceParams.SHARED_AGGREGATE_KEYS,
                SourceFixture.ValidSourceParams.INSTALL_TIME,
                SourceFixture.ValidSourceParams.REGISTRATION_ORIGIN);
    }

    @Test
    public void testSourceBuilder_validateArgumentEnrollmentId() {
        assertInvalidSourceArguments(
                SourceFixture.ValidSourceParams.SOURCE_EVENT_ID,
                SourceFixture.ValidSourceParams.PUBLISHER,
                SourceFixture.ValidSourceParams.ATTRIBUTION_DESTINATIONS,
                SourceFixture.ValidSourceParams.WEB_DESTINATIONS,
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
                SourceFixture.ValidSourceParams.buildFilterData(),
                SourceFixture.ValidSourceParams.REGISTRATION_ID,
                SourceFixture.ValidSourceParams.SHARED_AGGREGATE_KEYS,
                SourceFixture.ValidSourceParams.INSTALL_TIME,
                SourceFixture.ValidSourceParams.REGISTRATION_ORIGIN);
    }

    @Test
    public void testSourceBuilder_validateArgumentRegistrant() {
        assertInvalidSourceArguments(
                SourceFixture.ValidSourceParams.SOURCE_EVENT_ID,
                SourceFixture.ValidSourceParams.PUBLISHER,
                SourceFixture.ValidSourceParams.ATTRIBUTION_DESTINATIONS,
                SourceFixture.ValidSourceParams.WEB_DESTINATIONS,
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
                SourceFixture.ValidSourceParams.buildFilterData(),
                SourceFixture.ValidSourceParams.REGISTRATION_ID,
                SourceFixture.ValidSourceParams.SHARED_AGGREGATE_KEYS,
                SourceFixture.ValidSourceParams.INSTALL_TIME,
                SourceFixture.ValidSourceParams.REGISTRATION_ORIGIN);

        assertInvalidSourceArguments(
                SourceFixture.ValidSourceParams.SOURCE_EVENT_ID,
                SourceFixture.ValidSourceParams.PUBLISHER,
                SourceFixture.ValidSourceParams.ATTRIBUTION_DESTINATIONS,
                SourceFixture.ValidSourceParams.WEB_DESTINATIONS,
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
                SourceFixture.ValidSourceParams.buildFilterData(),
                SourceFixture.ValidSourceParams.REGISTRATION_ID,
                SourceFixture.ValidSourceParams.SHARED_AGGREGATE_KEYS,
                SourceFixture.ValidSourceParams.INSTALL_TIME,
                SourceFixture.ValidSourceParams.REGISTRATION_ORIGIN);
    }

    @Test
    public void testSourceBuilder_validateArgumentSourceType() {
        assertInvalidSourceArguments(
                SourceFixture.ValidSourceParams.SOURCE_EVENT_ID,
                SourceFixture.ValidSourceParams.PUBLISHER,
                SourceFixture.ValidSourceParams.ATTRIBUTION_DESTINATIONS,
                SourceFixture.ValidSourceParams.WEB_DESTINATIONS,
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
                SourceFixture.ValidSourceParams.buildFilterData(),
                SourceFixture.ValidSourceParams.REGISTRATION_ID,
                SourceFixture.ValidSourceParams.SHARED_AGGREGATE_KEYS,
                SourceFixture.ValidSourceParams.INSTALL_TIME,
                SourceFixture.ValidSourceParams.REGISTRATION_ORIGIN);
    }

    @Test
    public void testAggregatableAttributionSource() throws Exception {
        final TreeMap<String, BigInteger> aggregatableSource = new TreeMap<>();
        aggregatableSource.put("2", new BigInteger("71"));
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
                SourceFixture.getMinimalValidSourceBuilder()
                        .setAggregatableAttributionSource(attributionSource)
                        .build();

        assertNotNull(source.getAggregatableAttributionSource().orElse(null));
        assertNotNull(
                source.getAggregatableAttributionSource().orElse(null).getAggregatableSource());
        assertNotNull(source.getAggregatableAttributionSource().orElse(null).getFilterMap());
        assertEquals(
                aggregatableSource,
                source.getAggregatableAttributionSource().orElse(null).getAggregatableSource());
        assertEquals(
                filterMap,
                source.getAggregatableAttributionSource()
                        .orElse(null)
                        .getFilterMap()
                        .getAttributionFilterMap());
    }

    @Test
    public void testTriggerDataCardinality() {
        Source eventSource =
                SourceFixture.getMinimalValidSourceBuilder()
                        .setSourceType(Source.SourceType.EVENT)
                        .build();
        assertEquals(
                PrivacyParams.EVENT_TRIGGER_DATA_CARDINALITY,
                eventSource.getTriggerDataCardinality());
        Source navigationSource =
                SourceFixture.getMinimalValidSourceBuilder()
                        .setSourceType(Source.SourceType.NAVIGATION)
                        .build();
        assertEquals(
                PrivacyParams.getNavigationTriggerDataCardinality(),
                navigationSource.getTriggerDataCardinality());
    }

    @Test
    public void testGetAttributionDestinations() {
        Source source = SourceFixture.getValidSource();
        assertEquals(
                source.getAttributionDestinations(EventSurfaceType.APP),
                source.getAppDestinations());
        assertEquals(
                source.getAttributionDestinations(EventSurfaceType.WEB),
                source.getWebDestinations());
    }

    @Test
    public void testParseFilterData_nonEmpty() throws JSONException {
        JSONObject filterMapJson = new JSONObject();
        filterMapJson.put("conversion", new JSONArray(Collections.singletonList("electronics")));
        filterMapJson.put("product", new JSONArray(Arrays.asList("1234", "2345")));
        Source source =
                SourceFixture.getMinimalValidSourceBuilder()
                        .setSourceType(Source.SourceType.NAVIGATION)
                        .setFilterData(filterMapJson.toString())
                        .build();
        FilterMap filterMap = source.getFilterData();
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
        Source source =
                SourceFixture.getMinimalValidSourceBuilder()
                        .setSourceType(Source.SourceType.EVENT)
                        .build();
        FilterMap filterMap = source.getFilterData();
        assertEquals(filterMap.getAttributionFilterMap().size(), 1);
        assertEquals(Collections.singletonList("event"),
                filterMap.getAttributionFilterMap().get("source_type"));
    }

    @Test
    public void testParseFilterData_emptyFilterData() throws JSONException {
        Source source =
                SourceFixture.getMinimalValidSourceBuilder()
                        .setSourceType(Source.SourceType.EVENT)
                        .setFilterData("")
                        .build();
        FilterMap filterMap = source.getFilterData();
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

        Source source =
                SourceFixture.getMinimalValidSourceBuilder()
                        .setSourceType(Source.SourceType.NAVIGATION)
                        .setAggregateSource(aggregatableSource.toString())
                        .setFilterData(filterMap.toString())
                        .build();
        Optional<AggregatableAttributionSource> aggregatableAttributionSource =
                source.getAggregatableAttributionSource();
        assertTrue(aggregatableAttributionSource.isPresent());
        AggregatableAttributionSource aggregateSource = aggregatableAttributionSource.get();
        assertEquals(aggregateSource.getAggregatableSource().size(), 2);
        assertEquals(
                aggregateSource.getAggregatableSource().get("campaignCounts").longValue(), 345L);
        assertEquals(aggregateSource.getAggregatableSource().get("geoValue").longValue(), 5L);
        assertEquals(aggregateSource.getFilterMap().getAttributionFilterMap().size(), 3);
    }

    @Test
    public void fromBuilder_equalsComparison_success() {
        // Setup
        Source fromSource = SourceFixture.getValidSource();

        // Assertion
        assertEquals(fromSource, Source.Builder.from(fromSource).build());
    }

    @Test
    public void reportSpecs_encodingDecoding_equal() throws JSONException {
        // Setup
        Source validSource = SourceFixture.getValidSourceWithFlexEventReport();

        ReportSpec originalReportSpec = validSource.getFlexEventReportSpec();
        String encodedTriggerSpecs = originalReportSpec.encodeTriggerSpecsToJSON();
        String encodeddMaxReports = Integer.toString(originalReportSpec.getMaxReports());
        String encodedExistingTriggerStatus =
                originalReportSpec.encodeTriggerStatusToJSON().toString();
        String encodedPrivacyParameters = originalReportSpec.encodePrivacyParametersToJSONString();

        ReportSpec reportSpec =
                new ReportSpec(
                        encodedTriggerSpecs,
                        encodeddMaxReports,
                        encodedExistingTriggerStatus,
                        encodedPrivacyParameters);
        // Assertion
        assertEquals(originalReportSpec.getMaxReports(), reportSpec.getMaxReports());
        assertArrayEquals(originalReportSpec.getTriggerSpecs(), reportSpec.getTriggerSpecs());
        assertEquals(originalReportSpec, reportSpec);
    }

    private void assertInvalidSourceArguments(
            UnsignedLong sourceEventId,
            Uri publisher,
            List<Uri> appDestinations,
            List<Uri> webDestinations,
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
            @Nullable String filterData,
            @Nullable String registrationId,
            @Nullable String sharedAggregationKeys,
            @Nullable Long installTime,
            Uri registrationOrigin) {
        assertThrows(
                IllegalArgumentException.class,
                () ->
                        new Source.Builder()
                                .setEventId(sourceEventId)
                                .setPublisher(publisher)
                                .setAppDestinations(appDestinations)
                                .setWebDestinations(webDestinations)
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
                                .setRegistrationId(registrationId)
                                .setSharedAggregationKeys(sharedAggregationKeys)
                                .setInstallTime(installTime)
                                .setRegistrationOrigin(registrationOrigin)
                                .build());
    }

    @Test
    public void getTriggerDataCardinality_flexEventApi_equals() {
        Source eventSource =
                SourceFixture.getMinimalValidSourceBuilder()
                        .setSourceType(Source.SourceType.EVENT)
                        .build();
        assertEquals(
                PrivacyParams.EVENT_TRIGGER_DATA_CARDINALITY,
                eventSource.getTriggerDataCardinality());
        Source navigationSource =
                SourceFixture.getMinimalValidSourceBuilder()
                        .setSourceType(Source.SourceType.NAVIGATION)
                        .build();
        assertEquals(
                PrivacyParams.getNavigationTriggerDataCardinality(),
                navigationSource.getTriggerDataCardinality());
    }

    @Test
    public void isFlexEventApiValueValid_eventSource_true() throws JSONException {
        // setup
        String triggerSpecsString =
                "[{\"trigger_data\": [1, 2],"
                        + "\"event_report_windows\": { "
                        + "\"start_time\": \"0\", "
                        + String.format("\"end_times\": [%s]}, ", TimeUnit.DAYS.toMillis(7))
                        + "\"summary_window_operator\": \"count\", "
                        + "\"summary_buckets\": [1]}]\n";
        Source testSource =
                SourceFixture.getMinimalValidSourceBuilder()
                        .setEventId(new UnsignedLong(1L))
                        .setAppDestinations(List.of(Uri.parse("android-app://com.destination1")))
                        .setWebDestinations(
                                List.of(WebUtil.validUri("https://web-destination1.test")))
                        .setRegistrant(Uri.parse("android-app://com.example"))
                        .setEventTime(new Random().nextLong())
                        .setExpiryTime(8640000010L)
                        .setPriority(100L)
                        .setSourceType(Source.SourceType.EVENT)
                        .setAttributionMode(Source.AttributionMode.TRUTHFULLY)
                        .setDebugKey(new UnsignedLong(47823478789L))
                        .setTriggerSpecs(triggerSpecsString)
                        .setMaxBucketIncrements("1")
                        .buildInitialFlexEventReportSpec()
                        .build();
        assertTrue(testSource.isFlexEventApiValueValid());
    }

    @Test
    public void isFlexEventApiValueValid_eventSource_false() throws JSONException {
        // setup
        String triggerSpecsString =
                "[{\"trigger_data\": [1, 2, 3, 4, 5, 6, 7, 8],"
                        + "\"event_report_windows\": { "
                        + "\"start_time\": \"0\", "
                        + String.format(
                                "\"end_times\": [%s, %s, %s]}, ",
                                TimeUnit.DAYS.toMillis(2),
                                TimeUnit.DAYS.toMillis(7),
                                TimeUnit.DAYS.toMillis(30))
                        + "\"summary_window_operator\": \"count\", "
                        + "\"summary_buckets\": [1, 2, 3]}]\n";
        Source testSource =
                SourceFixture.getMinimalValidSourceBuilder()
                        .setEventId(new UnsignedLong(1L))
                        .setAppDestinations(List.of(Uri.parse("android-app://com.destination1")))
                        .setWebDestinations(
                                List.of(WebUtil.validUri("https://web-destination1.test")))
                        .setRegistrant(Uri.parse("android-app://com.example"))
                        .setEventTime(new Random().nextLong())
                        .setExpiryTime(8640000010L)
                        .setPriority(100L)
                        .setSourceType(Source.SourceType.EVENT)
                        .setAttributionMode(Source.AttributionMode.TRUTHFULLY)
                        .setDebugKey(new UnsignedLong(47823478789L))
                        .setTriggerSpecs(triggerSpecsString)
                        .setMaxBucketIncrements("1")
                        .buildInitialFlexEventReportSpec()
                        .build();
        assertFalse(testSource.isFlexEventApiValueValid());
    }

    @Test
    public void isFlexEventApiValueValid_navigationSource_true() throws JSONException {
        // setup
        String triggerSpecsString =
                "[{\"trigger_data\": [1, 2, 3, 4, 5, 6, 7, 8],"
                        + "\"event_report_windows\": { "
                        + "\"start_time\": \"0\", "
                        + String.format(
                                "\"end_times\": [%s, %s, %s]}, ",
                                TimeUnit.DAYS.toMillis(2),
                                TimeUnit.DAYS.toMillis(7),
                                TimeUnit.DAYS.toMillis(30))
                        + "\"summary_window_operator\": \"count\", "
                        + "\"summary_buckets\": [1, 2, 3]}]\n";
        Source testSource =
                SourceFixture.getMinimalValidSourceBuilder()
                        .setEventId(new UnsignedLong(1L))
                        .setAppDestinations(List.of(Uri.parse("android-app://com.destination1")))
                        .setWebDestinations(
                                List.of(WebUtil.validUri("https://web-destination1.test")))
                        .setRegistrant(Uri.parse("android-app://com.example"))
                        .setEventTime(new Random().nextLong())
                        .setExpiryTime(8640000010L)
                        .setPriority(100L)
                        .setSourceType(Source.SourceType.NAVIGATION)
                        .setAttributionMode(Source.AttributionMode.TRUTHFULLY)
                        .setDebugKey(new UnsignedLong(47823478789L))
                        .setTriggerSpecs(triggerSpecsString)
                        .setMaxBucketIncrements("1")
                        .buildInitialFlexEventReportSpec()
                        .build();
        assertTrue(testSource.isFlexEventApiValueValid());
    }

    @Test
    public void buildFlexibleEventReportApi_validParams_pass() throws JSONException {
        String triggerSpecsString =
                "[{\"trigger_data\": [1, 2, 3],"
                        + "\"event_report_windows\": { "
                        + "\"start_time\": \"0\", "
                        + String.format(
                                "\"end_times\": [%s, %s, %s]}, ",
                                TimeUnit.DAYS.toMillis(2),
                                TimeUnit.DAYS.toMillis(7),
                                TimeUnit.DAYS.toMillis(30))
                        + "\"summary_window_operator\": \"count\", "
                        + "\"summary_buckets\": [1, 2, 3, 4]}]";
        Source testSource =
                SourceFixture.getMinimalValidSourceBuilder()
                        .setTriggerSpecs(triggerSpecsString)
                        .setMaxBucketIncrements("3")
                        .setPrivacyParameters("{\"flip_probability\" :0.0024}")
                        .build();
        testSource.buildFlexibleEventReportApi();
        // Assertion
        assertEquals(
                testSource.getFlexEventReportSpec(),
                new ReportSpec(triggerSpecsString, "3", "", "{\"flip_probability\" :0.0024}"));
    }

    @Test
    public void buildFlexibleEventReportApi_invalidParamsSyntaxError_throws() throws JSONException {
        String triggerSpecsString =
                "[{\"trigger_data\": [1, 2, 3,"
                        + "\"event_report_windows\": { "
                        + "\"start_time\": \"0\", "
                        + String.format(
                                "\"end_times\": [%s, %s, %s]}, ",
                                TimeUnit.DAYS.toMillis(2),
                                TimeUnit.DAYS.toMillis(7),
                                TimeUnit.DAYS.toMillis(30))
                        + "\"summary_window_operator\": \"count\", "
                        + "\"summary_buckets\": [1, 2, 3, 4]}]";
        Source testSource =
                SourceFixture.getMinimalValidSourceBuilder()
                        .setTriggerSpecs(triggerSpecsString)
                        .setMaxBucketIncrements("3")
                        .setPrivacyParameters("{\"flip_probability\" :0.0024}")
                        .build();

        // Assertion
        assertThrows(JSONException.class, () -> testSource.buildFlexibleEventReportApi());
    }

    @Test
    public void buildInitialFlexEventReportSpec_validParams_pass() throws JSONException {
        String triggerSpecsString =
                "[{\"trigger_data\": [1, 2, 3],"
                        + "\"event_report_windows\": { "
                        + "\"start_time\": \"0\", "
                        + String.format(
                                "\"end_times\": [%s, %s, %s]}, ",
                                TimeUnit.DAYS.toMillis(2),
                                TimeUnit.DAYS.toMillis(7),
                                TimeUnit.DAYS.toMillis(30))
                        + "\"summary_window_operator\": \"count\", "
                        + "\"summary_buckets\": [1, 2, 3, 4]}]";
        Source testSource =
                SourceFixture.getMinimalValidSourceBuilder()
                        .setTriggerSpecs(triggerSpecsString)
                        .setMaxBucketIncrements("3")
                        .buildInitialFlexEventReportSpec()
                        .build();
        // Assertion
        assertEquals(testSource.getFlexEventReportSpec(), new ReportSpec(triggerSpecsString, "3"));
    }

    @Test
    public void buildInitialFlexEventReportSpec_invalidParamsSyntaxError_throws() {
        String triggerSpecsString =
                "[{\"trigger_data\": [1, 2, 3,"
                        + "\"event_report_windows\": { "
                        + "\"start_time\": \"0\", "
                        + String.format(
                                "\"end_times\": [%s, %s, %s]}, ",
                                TimeUnit.DAYS.toMillis(2),
                                TimeUnit.DAYS.toMillis(7),
                                TimeUnit.DAYS.toMillis(30))
                        + "\"summary_window_operator\": \"count\", "
                        + "\"summary_buckets\": [1, 2, 3, 4]}]";
        Source.Builder testSourceBuilder =
                SourceFixture.getMinimalValidSourceBuilder()
                        .setTriggerSpecs(triggerSpecsString)
                        .setMaxBucketIncrements("3")
                        .setPrivacyParameters("{\"flip_probability\" :0.0024}");

        // Assertion
        assertThrows(
                JSONException.class, () -> testSourceBuilder.buildInitialFlexEventReportSpec());
    }
}

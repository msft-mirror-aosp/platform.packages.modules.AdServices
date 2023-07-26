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
import static org.junit.Assert.assertNull;
import static org.junit.Assert.assertThrows;
import static org.junit.Assert.assertTrue;
import static org.mockito.Mockito.doReturn;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.spy;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import android.net.Uri;
import android.util.Pair;

import androidx.annotation.Nullable;

import com.android.adservices.service.Flags;
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
import java.util.HashSet;
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
    private static final UnsignedLong SHARED_DEBUG_KEY_1 = new UnsignedLong(1786463L);
    private static final UnsignedLong SHARED_DEBUG_KEY_2 = new UnsignedLong(3487834L);
    private static final List<AttributedTrigger> ATTRIBUTED_TRIGGERS =
            List.of(
                    new AttributedTrigger(
                            "triggerId",
                            /* long priority */ 5L,
                            /* UnsignedLong triggerData */ new UnsignedLong("89"),
                            /* long value */ 15L,
                            /* long triggerTime */ 1934567890L,
                            /* UnsignedLong dedupKey */ null));

    @Test
    public void testDefaults() {
        Source source = SourceFixture.getMinimalValidSourceBuilder().build();
        assertEquals(0, source.getEventReportDedupKeys().size());
        assertEquals(0, source.getAggregateReportDedupKeys().size());
        assertEquals(Source.Status.ACTIVE, source.getStatus());
        assertEquals(Source.SourceType.EVENT, source.getSourceType());
        assertEquals(Source.AttributionMode.UNASSIGNED, source.getAttributionMode());
        assertNull(source.getAttributedTriggers());
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
        String sharedFilterDataKeys = "[\"product\"]";
        String parentId = "parent-id";
        String debugJoinKey = "SAMPLE_DEBUG_JOIN_KEY";
        String debugAppAdId = "SAMPLE_DEBUG_APP_ADID";
        String debugWebAdId = "SAMPLE_DEBUG_WEB_ADID";
        ReportSpec reportSpec = SourceFixture.getValidReportSpecValueSum();
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
                        .setSharedFilterDataKeys(sharedFilterDataKeys)
                        .setInstallTime(100L)
                        .setParentId(parentId)
                        .setDebugJoinKey(debugJoinKey)
                        .setPlatformAdId(debugAppAdId)
                        .setDebugAdId(debugWebAdId)
                        .setRegistrationOrigin(WebUtil.validUri("https://subdomain.example.test"))
                        .setCoarseEventReportDestinations(true)
                        .setSharedDebugKey(SHARED_DEBUG_KEY_1)
                        .setAttributedTriggers(ATTRIBUTED_TRIGGERS)
                        .setFlexEventReportSpec(reportSpec)
                        .setTriggerSpecs(reportSpec.encodeTriggerSpecsToJSON())
                        .setMaxEventLevelReports(reportSpec.getMaxReports())
                        .setEventAttributionStatus(null)
                        .setPrivacyParameters(reportSpec.encodePrivacyParametersToJSONString())
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
                        .setSharedFilterDataKeys(sharedFilterDataKeys)
                        .setInstallTime(100L)
                        .setParentId(parentId)
                        .setDebugJoinKey(debugJoinKey)
                        .setPlatformAdId(debugAppAdId)
                        .setDebugAdId(debugWebAdId)
                        .setRegistrationOrigin(WebUtil.validUri("https://subdomain.example.test"))
                        .setCoarseEventReportDestinations(true)
                        .setSharedDebugKey(SHARED_DEBUG_KEY_1)
                        .setAttributedTriggers(ATTRIBUTED_TRIGGERS)
                        .setFlexEventReportSpec(reportSpec)
                        .setTriggerSpecs(reportSpec.encodeTriggerSpecsToJSON())
                        .setMaxEventLevelReports(reportSpec.getMaxReports())
                        .setEventAttributionStatus(null)
                        .setPrivacyParameters(reportSpec.encodePrivacyParametersToJSONString())
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

        String sharedFilterDataKeys1 = "[\"key1\"]";
        String sharedFilterDataKeys2 = "[\"key2\"]";

        assertNotEquals(
                SourceFixture.getMinimalValidSourceBuilder()
                        .setSharedFilterDataKeys(sharedFilterDataKeys1)
                        .build(),
                SourceFixture.getMinimalValidSourceBuilder()
                        .setSharedFilterDataKeys(sharedFilterDataKeys2)
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
        assertNotEquals(
                SourceFixture.getMinimalValidSourceBuilder()
                        .setSharedDebugKey(SHARED_DEBUG_KEY_1)
                        .build(),
                SourceFixture.getMinimalValidSourceBuilder()
                        .setSharedDebugKey(SHARED_DEBUG_KEY_2)
                        .build());
        assertNotEquals(
                SourceFixture.getMinimalValidSourceBuilder()
                        .setAttributedTriggers(ATTRIBUTED_TRIGGERS)
                        .build(),
                SourceFixture.getMinimalValidSourceBuilder()
                        .setAttributedTriggers(new ArrayList<>())
                        .build());

        ReportSpec reportSpecValueSumBased =
                new ReportSpec(
                        SourceFixture.getTriggerSpecValueSumEncodedJSONValidBaseline(),
                        "5",
                        null);
        ReportSpec reportSpecCountBased = SourceFixture.getValidReportSpecCountBased();
        assertNotEquals(
                SourceFixture.getMinimalValidSourceBuilder()
                        .setTriggerSpecs(reportSpecValueSumBased.encodeTriggerSpecsToJSON())
                        .build(),
                SourceFixture.getMinimalValidSourceBuilder()
                        .setTriggerSpecs(reportSpecCountBased.encodeTriggerSpecsToJSON())
                        .build());
        assertNotEquals(
                SourceFixture.getMinimalValidSourceBuilder()
                        .setMaxEventLevelReports(3)
                        .build(),
                SourceFixture.getMinimalValidSourceBuilder()
                        .setMaxEventLevelReports(4)
                        .build());
        assertNotEquals(
                SourceFixture.getMinimalValidSourceBuilder()
                        .setEventAttributionStatus(null)
                        .build(),
                SourceFixture.getMinimalValidSourceBuilder()
                        .setEventAttributionStatus(new JSONArray().toString())
                        .build());
        assertNotEquals(
                SourceFixture.getMinimalValidSourceBuilder()
                        .setPrivacyParameters(
                                reportSpecValueSumBased.encodePrivacyParametersToJSONString())
                        .build(),
                SourceFixture.getMinimalValidSourceBuilder()
                        .setPrivacyParameters(
                                reportSpecCountBased.encodePrivacyParametersToJSONString())
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
                SourceFixture.ValidSourceParams.SHARED_FILTER_DATA_KEYS,
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
                SourceFixture.ValidSourceParams.SHARED_FILTER_DATA_KEYS,
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
                SourceFixture.ValidSourceParams.SHARED_FILTER_DATA_KEYS,
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
                SourceFixture.ValidSourceParams.SHARED_FILTER_DATA_KEYS,
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
                SourceFixture.ValidSourceParams.SHARED_FILTER_DATA_KEYS,
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
                SourceFixture.ValidSourceParams.SHARED_FILTER_DATA_KEYS,
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
                SourceFixture.ValidSourceParams.SHARED_FILTER_DATA_KEYS,
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
                SourceFixture.ValidSourceParams.SHARED_FILTER_DATA_KEYS,
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
                SourceFixture.ValidSourceParams.SHARED_FILTER_DATA_KEYS,
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
                SourceFixture.ValidSourceParams.SHARED_FILTER_DATA_KEYS,
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
                SourceFixture.ValidSourceParams.SHARED_FILTER_DATA_KEYS,
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
    public void setSharedDebugKey_success() throws JSONException {
        Source source =
                SourceFixture.getMinimalValidSourceBuilder()
                        .setSharedDebugKey(SHARED_DEBUG_KEY_1)
                        .build();
        assertEquals(SHARED_DEBUG_KEY_1, source.getSharedDebugKey());
    }

    @Test
    public void encodeAttributedTriggersToJson_buildAttributedTriggers_encodesAndDecodesCorrectly()
            throws JSONException {
        JSONArray existingAttributes = new JSONArray();
        JSONObject triggerRecord1 = new JSONObject();
        triggerRecord1.put("trigger_id", "100");
        triggerRecord1.put("value", 2L);
        triggerRecord1.put("priority", 1L);
        triggerRecord1.put("trigger_time", 1689564817000L);
        triggerRecord1.put("trigger_data", new UnsignedLong(1L).toString());
        /* dedup_key not provided */

        JSONObject triggerRecord2 = new JSONObject();
        triggerRecord2.put("trigger_id", "200");
        triggerRecord2.put("value", 3L);
        triggerRecord2.put("priority", 4L);
        triggerRecord2.put("trigger_time", 1689564817010L);
        triggerRecord2.put("trigger_data", new UnsignedLong(1L).toString());
        triggerRecord2.put("dedup_key", new UnsignedLong(45678L).toString());
        existingAttributes.put(triggerRecord1);
        existingAttributes.put(triggerRecord2);

        Source sourceWithEventAttributionStatus =
                SourceFixture.getValidSourceBuilder()
                        .setEventAttributionStatus(existingAttributes.toString())
                        .setAttributedTriggers(null)
                        .build();
        sourceWithEventAttributionStatus.buildAttributedTriggers();

        String encodedTriggerStatusJson =
                sourceWithEventAttributionStatus.encodeAttributedTriggersToJson();
        JSONArray encodedTriggerStatus = new JSONArray(encodedTriggerStatusJson);

        Source sourceWithEventAttributionStatusAfterDecoding =
                SourceFixture.getValidSourceBuilder()
                        .setEventAttributionStatus(encodedTriggerStatusJson)
                        .setAttributedTriggers(null)
                        .build();
        sourceWithEventAttributionStatusAfterDecoding.buildAttributedTriggers();

        // Assertion
        HashSet<String> keys1 = new HashSet(triggerRecord1.keySet());
        keys1.addAll(encodedTriggerStatus.getJSONObject(0).keySet());
        for (String key : keys1) {
            assertEquals(
                    triggerRecord1.get(key).toString(),
                    encodedTriggerStatus.getJSONObject(0).get(key).toString());
        }

        HashSet<String> keys2 = new HashSet(triggerRecord2.keySet());
        keys2.addAll(encodedTriggerStatus.getJSONObject(1).keySet());
        for (String key : keys2) {
            assertEquals(
                    triggerRecord2.get(key).toString(),
                    encodedTriggerStatus.getJSONObject(1).get(key).toString());
        }

        assertEquals(
                sourceWithEventAttributionStatus.getAttributedTriggers().size(),
                sourceWithEventAttributionStatusAfterDecoding.getAttributedTriggers().size());

        for (int i = 0; i < sourceWithEventAttributionStatus.getAttributedTriggers().size(); i++) {
            assertEquals(
                    sourceWithEventAttributionStatus.getAttributedTriggers().get(i),
                    sourceWithEventAttributionStatusAfterDecoding.getAttributedTriggers().get(i));
        }
    }

    @Test
    public void buildAttributedTriggers_multipleCalls_doesNotParseAttributionStatus()
            throws JSONException {
        JSONArray existingAttributes = new JSONArray();
        JSONObject triggerRecord = new JSONObject();
        triggerRecord.put("trigger_id", "100");
        triggerRecord.put("value", 2L);
        triggerRecord.put("priority", 1L);
        triggerRecord.put("trigger_time", 1689564817000L);
        triggerRecord.put("trigger_data", new UnsignedLong(1L).toString());
        triggerRecord.put("dedup_key", new UnsignedLong(34567L).toString());

        existingAttributes.put(triggerRecord);

        Source sourceWithEventAttributionStatus =
                SourceFixture.getValidSourceBuilder()
                        .setEventAttributionStatus(existingAttributes.toString())
                        .setAttributedTriggers(null)
                        .build();
        Source spySource = spy(sourceWithEventAttributionStatus);
        // Multiple calls to `Source::buildAttributedTriggers`
        spySource.buildAttributedTriggers();
        spySource.buildAttributedTriggers();
        spySource.buildAttributedTriggers();
        verify(spySource, times(1)).parseEventAttributionStatus();
    }

    @Test
    public void reportSpecs_encodingDecoding_equal() throws JSONException {
        // Setup
        Source validSource = SourceFixture.getValidSourceWithFlexEventReport();
        ReportSpec originalReportSpec = validSource.getFlexEventReportSpec();
        String encodedTriggerSpecs = originalReportSpec.encodeTriggerSpecsToJSON();
        String encodeddMaxReports = Integer.toString(originalReportSpec.getMaxReports());
        String encodedPrivacyParameters = originalReportSpec.encodePrivacyParametersToJSONString();
        ReportSpec reportSpec =
                new ReportSpec(
                        encodedTriggerSpecs,
                        encodeddMaxReports,
                        validSource,
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
            @Nullable String sharedFilterDataKeys,
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
                                .setSharedFilterDataKeys(sharedFilterDataKeys)
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
        Flags flags = mock(Flags.class);
        doReturn(2).when(flags).getMeasurementVtcConfigurableMaxEventReportsCount();
        doReturn(true).when(flags).getMeasurementFlexibleEventReportingApiEnabled();
        doReturn(Flags.MEASUREMENT_FLEX_API_MAX_INFO_GAIN_EVENT)
                .when(flags)
                .getMeasurementFlexAPIMaxInformationGainEvent();
        doReturn(Flags.MEASUREMENT_FLEX_API_MAX_INFO_GAIN_NAVIGATION)
                .when(flags)
                .getMeasurementFlexAPIMaxInformationGainNavigation();
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
                        .setSourceType(Source.SourceType.NAVIGATION)
                        .setAttributionMode(Source.AttributionMode.TRUTHFULLY)
                        .setDebugKey(new UnsignedLong(47823478789L))
                        .setTriggerSpecs(triggerSpecsString)
                        .setMaxEventLevelReports(3)
                        .buildInitialFlexEventReportSpec(flags)
                        .build();
        assertTrue(testSource.isFlexEventApiValueValid(flags));
    }

    @Test
    public void isFlexEventApiValueValid_eventSource_false() throws JSONException {
        Flags flags = mock(Flags.class);
        doReturn(2).when(flags).getMeasurementVtcConfigurableMaxEventReportsCount();
        doReturn(true).when(flags).getMeasurementFlexibleEventReportingApiEnabled();
        doReturn(Flags.MEASUREMENT_FLEX_API_MAX_INFO_GAIN_EVENT)
                .when(flags)
                .getMeasurementFlexAPIMaxInformationGainEvent();
        doReturn(Flags.MEASUREMENT_FLEX_API_MAX_INFO_GAIN_NAVIGATION)
                .when(flags)
                .getMeasurementFlexAPIMaxInformationGainNavigation();
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
                        .setMaxEventLevelReports(3)
                        .buildInitialFlexEventReportSpec(flags)
                        .build();
        assertFalse(testSource.isFlexEventApiValueValid(flags));
    }

    @Test
    public void isFlexEventApiValueValid_navigationSource_true() throws JSONException {
        Flags flags = mock(Flags.class);
        doReturn(2).when(flags).getMeasurementVtcConfigurableMaxEventReportsCount();
        doReturn(true).when(flags).getMeasurementFlexibleEventReportingApiEnabled();
        doReturn(Flags.MEASUREMENT_FLEX_API_MAX_INFO_GAIN_EVENT)
                .when(flags)
                .getMeasurementFlexAPIMaxInformationGainEvent();
        doReturn(Flags.MEASUREMENT_FLEX_API_MAX_INFO_GAIN_NAVIGATION)
                .when(flags)
                .getMeasurementFlexAPIMaxInformationGainNavigation();
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
                        .setMaxEventLevelReports(3)
                        .buildInitialFlexEventReportSpec(flags)
                        .build();
        assertTrue(testSource.isFlexEventApiValueValid(flags));
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
        Source source =
                SourceFixture.getMinimalValidSourceBuilder()
                        .setTriggerSpecs(triggerSpecsString)
                        .setMaxEventLevelReports(3)
                        .setPrivacyParameters("{\"flip_probability\" :0.0024}")
                        .build();
        source.buildFlexibleEventReportApi();
        // Assertion
        assertEquals(
                source.getFlexEventReportSpec(),
                new ReportSpec(triggerSpecsString, "3", source, "{\"flip_probability\":0.0024}"));
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
                        .setMaxEventLevelReports(3)
                        .setPrivacyParameters("{\"flip_probability\" :0.0024}")
                        .build();

        // Assertion
        assertThrows(JSONException.class, () -> testSource.buildFlexibleEventReportApi());
    }

    @Test
    public void buildInitialFlexEventReportSpec_validParams_pass() throws JSONException {
        Flags flags = mock(Flags.class);
        doReturn(2).when(flags).getMeasurementVtcConfigurableMaxEventReportsCount();
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
                        .setMaxEventLevelReports(3)
                        .buildInitialFlexEventReportSpec(flags)
                        .build();
        // Assertion
        assertEquals(
                testSource.getFlexEventReportSpec(),
                new ReportSpec(triggerSpecsString, "3", null));
    }

    @Test
    public void buildInitialFlexEventReportSpec_invalidParamsSyntaxError_throws() {
        Flags flags = mock(Flags.class);
        doReturn(2).when(flags).getMeasurementVtcConfigurableMaxEventReportsCount();
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
                        .setMaxEventLevelReports(3)
                        .setPrivacyParameters("{\"flip_probability\" :0.0024}");

        // Assertion
        assertThrows(
                JSONException.class,
                () -> testSourceBuilder.buildInitialFlexEventReportSpec(flags));
    }

    @Test
    public void parseEventReportWindows() {
        // Invalid JSON
        assertNull(Source.parseEventReportWindows("{'}"));

        // No Start Time
        List<Pair<Long, Long>> eventReportWindows =
                Source.parseEventReportWindows("{'end_times': [3600, 86400]}");
        assertNotNull(eventReportWindows);
        assertEquals(2, eventReportWindows.size());
        assertEquals(new Pair<>(0L, 3600L), eventReportWindows.get(0));
        assertEquals(new Pair<>(3600L, 86400L), eventReportWindows.get(1));

        // With Start Time
        eventReportWindows =
                Source.parseEventReportWindows(
                        "{'start_time': '2000', 'end_times': [3600, 86400, 172000]}");
        assertNotNull(eventReportWindows);
        assertEquals(eventReportWindows.size(), 3);
        assertEquals(new Pair<>(2000L, 3600L), eventReportWindows.get(0));
        assertEquals(new Pair<>(3600L, 86400L), eventReportWindows.get(1));
        assertEquals(new Pair<>(86400L, 172000L), eventReportWindows.get(2));
    }

    @Test
    public void getOrDefaultEventReportWindows() {
        Flags flags = mock(Flags.class);
        // AdTech Windows
        List<Pair<Long, Long>> eventReportWindows =
                Source.getOrDefaultEventReportWindows(
                        "{'start_time': '2000000', 'end_times': [3600000, 86400000, 172000000]}",
                        Source.SourceType.EVENT,
                        8640000,
                        flags);
        assertNotNull(eventReportWindows);
        assertEquals(eventReportWindows.size(), 3);
        assertEquals(new Pair<>(2000000L, 3600000L), eventReportWindows.get(0));
        assertEquals(new Pair<>(3600000L, 86400000L), eventReportWindows.get(1));
        assertEquals(new Pair<>(86400000L, 172000000L), eventReportWindows.get(2));

        // Default Windows - Event
        when(flags.getMeasurementEventReportsVtcEarlyReportingWindows()).thenReturn("86400");
        when(flags.getMeasurementEventReportsCtcEarlyReportingWindows())
                .thenReturn("172800,604800");
        eventReportWindows =
                Source.getOrDefaultEventReportWindows(
                        null, Source.SourceType.EVENT, TimeUnit.DAYS.toMillis(15), flags);
        assertNotNull(eventReportWindows);
        assertEquals(2, eventReportWindows.size());
        assertEquals(new Pair<>(0L, 86400000L), eventReportWindows.get(0));
        assertEquals(new Pair<>(86400000L, 1296000000L), eventReportWindows.get(1));

        // Default Windows - Navigation
        eventReportWindows =
                Source.getOrDefaultEventReportWindows(
                        null, Source.SourceType.NAVIGATION, TimeUnit.DAYS.toMillis(15), flags);
        assertNotNull(eventReportWindows);
        assertEquals(3, eventReportWindows.size());
        assertEquals(new Pair<>(0L, 172800000L), eventReportWindows.get(0));
        assertEquals(new Pair<>(172800000L, 604800000L), eventReportWindows.get(1));
        assertEquals(new Pair<>(604800000L, 1296000000L), eventReportWindows.get(2));
    }

    @Test
    public void parsedProcessedEventReportWindows() {
        // Null event report window
        Source source = SourceFixture.getValidSource();
        assertNull(source.parsedProcessedEventReportWindows());

        // Invalid event report windows string
        Source sourceInvalidWindows =
                SourceFixture.getMinimalValidSourceBuilder()
                        .setEventTime(10L)
                        .setEventReportWindows("{''}")
                        .build();
        assertNull(sourceInvalidWindows.parsedProcessedEventReportWindows());

        // Valid event report windows string
        Source sourceValidWindows =
                SourceFixture.getMinimalValidSourceBuilder()
                        .setEventTime(10L)
                        .setEventReportWindows(
                                "{'start_time': 1800000, 'end_times': [3600000, 864000000]}")
                        .build();
        List<Pair<Long, Long>> windows = sourceValidWindows.parsedProcessedEventReportWindows();
        assertNotNull(windows);
        assertEquals(2, windows.size());
        assertEquals(new Pair<>(1800010L, 3600010L), windows.get(0));
        assertEquals(new Pair<>(3600010L, 864000010L), windows.get(1));
    }

    @Test
    public void getOrDefaultMaxEventLevelReports() {
        Flags flags = mock(Flags.class);
        when(flags.getMeasurementVtcConfigurableMaxEventReportsCount()).thenReturn(2);
        // null, Default for EVENT
        assertEquals(
                Integer.valueOf(2),
                Source.getOrDefaultMaxEventLevelReports(Source.SourceType.EVENT, null, flags));
        // null, Default for NAVIGATION
        assertEquals(
                Integer.valueOf(3),
                Source.getOrDefaultMaxEventLevelReports(Source.SourceType.NAVIGATION, null, flags));
        // Valid value provided
        assertEquals(
                Integer.valueOf(7),
                Source.getOrDefaultMaxEventLevelReports(Source.SourceType.NAVIGATION, 7, flags));
    }

    @Test
    public void getProcessedEventReportWindow() {
        // null eventReportWindow
        Source sourceNullEventReportWindow =
                SourceFixture.getMinimalValidSourceBuilder()
                        .setEventTime(10)
                        .setEventReportWindow(null)
                        .build();
        assertNull(sourceNullEventReportWindow.getProcessedEventReportWindow());

        // eventReportWindow Value < eventTime
        Source sourceNewEventReportWindow =
                SourceFixture.getMinimalValidSourceBuilder()
                        .setEventTime(10)
                        .setEventReportWindow(4L)
                        .build();
        assertEquals(Long.valueOf(14L), sourceNewEventReportWindow.getProcessedEventReportWindow());

        // eventReportWindow Value > eventTime
        Source sourceOldEventReportWindow =
                SourceFixture.getMinimalValidSourceBuilder()
                        .setEventTime(10)
                        .setEventReportWindow(15L)
                        .build();
        assertEquals(Long.valueOf(15L), sourceOldEventReportWindow.getProcessedEventReportWindow());
    }
}

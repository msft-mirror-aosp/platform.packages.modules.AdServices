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

import static com.android.adservices.service.measurement.Source.DEFAULT_MAX_EVENT_STATES;

import static com.google.common.truth.Truth.assertThat;
import static com.google.common.truth.Truth.assertWithMessage;

import static org.junit.Assert.assertArrayEquals;
import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertNotEquals;
import static org.junit.Assert.assertNotNull;
import static org.junit.Assert.assertNull;
import static org.junit.Assert.assertThrows;
import static org.junit.Assert.assertTrue;
import static org.mockito.Mockito.doReturn;
import static org.mockito.Mockito.spy;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import android.net.Uri;
import android.util.Pair;

import androidx.annotation.Nullable;

import com.android.adservices.common.AdServicesMockitoTestCase;
import com.android.adservices.common.WebUtil;
import com.android.adservices.service.Flags;
import com.android.adservices.service.measurement.aggregation.AggregatableAttributionSource;
import com.android.adservices.service.measurement.aggregation.AggregateDebugReportData;
import com.android.adservices.service.measurement.aggregation.AggregateDebugReporting;
import com.android.adservices.service.measurement.reporting.DebugReportApi;
import com.android.adservices.service.measurement.util.UnsignedLong;
import com.android.dx.mockito.inline.extended.ExtendedMockito;

import org.json.JSONArray;
import org.json.JSONException;
import org.json.JSONObject;
import org.junit.Before;
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
import java.util.Set;
import java.util.TreeMap;
import java.util.concurrent.TimeUnit;
import java.util.stream.Collectors;
import java.util.stream.LongStream;

public final class SourceTest extends AdServicesMockitoTestCase {

    private static final UnsignedLong DEBUG_KEY_1 = new UnsignedLong(81786463L);
    private static final UnsignedLong DEBUG_KEY_2 = new UnsignedLong(23487834L);
    private static final UnsignedLong SHARED_DEBUG_KEY_1 = new UnsignedLong(1786463L);
    private static final UnsignedLong SHARED_DEBUG_KEY_2 = new UnsignedLong(3487834L);
    private static final Set<UnsignedLong> SOURCE_TRIGGER_DATA =
            Set.of(new UnsignedLong(23L), new UnsignedLong(1L));
    private static final List<AttributedTrigger> ATTRIBUTED_TRIGGERS =
            List.of(
                    new AttributedTrigger(
                            "triggerId",
                            /* long priority */ 5L,
                            /* UnsignedLong triggerData */ new UnsignedLong("89"),
                            /* long value */ 15L,
                            /* long triggerTime */ 1934567890L,
                            /* UnsignedLong dedupKey */ null,
                            /* UnsignedLong debugKey */ null,
                            /* boolean hasSourceDebugKey */ false));

    @Before
    public void setup() {
        ExtendedMockito.doReturn(false).when(mMockFlags).getMeasurementEnableLookbackWindowFilter();
    }

    @Test
    public void testDefaults() {
        Source source = SourceFixture.getMinimalValidSourceBuilder().build();
        assertEquals(0, source.getEventReportDedupKeys().size());
        assertEquals(0, source.getAggregateReportDedupKeys().size());
        assertEquals(Source.Status.ACTIVE, source.getStatus());
        assertEquals(Source.SourceType.EVENT, source.getSourceType());
        assertEquals(Source.AttributionMode.UNASSIGNED, source.getAttributionMode());
        assertEquals(Source.TriggerDataMatching.MODULUS, source.getTriggerDataMatching());
        assertNull(source.getTriggerData());
        assertNull(source.getAttributedTriggers());
        assertNull(source.getAggregateDebugReportingString());
        assertEquals(0, source.getAggregateDebugReportContributions());
    }

    @Test
    public void testEqualsPass() throws Exception {
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
        TriggerSpecs triggerSpecs = SourceFixture.getValidTriggerSpecsValueSum();
        Double event_level_epsilon = 10d;
        String aggregateDebugReportingString =
                SourceFixture.ValidSourceParams.AGGREGATE_DEBUG_REPORT;
        int aggregateDebugReportContributions = 1024;
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
                        .setFilterDataString(filterMap.toString())
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
                        .setTriggerData(SOURCE_TRIGGER_DATA)
                        .setTriggerSpecs(triggerSpecs)
                        .setTriggerSpecsString(triggerSpecs.encodeToJson())
                        .setMaxEventLevelReports(triggerSpecs.getMaxReports())
                        .setEventAttributionStatus(null)
                        .setPrivacyParameters(triggerSpecs.encodePrivacyParametersToJsonString())
                        .setTriggerDataMatching(Source.TriggerDataMatching.EXACT)
                        .setDropSourceIfInstalled(true)
                        .setAttributionScopes(List.of("1", "2", "3"))
                        .setAttributionScopeLimit(4L)
                        .setMaxEventStates(10L)
                        .setDestinationLimitPriority(100L)
                        .setDestinationLimitAlgorithm(Source.DestinationLimitAlgorithm.FIFO)
                        .setEventLevelEpsilon(event_level_epsilon)
                        .setAggregateDebugReportingString(aggregateDebugReportingString)
                        .setAggregateDebugReportContributions(aggregateDebugReportContributions)
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
                        .setFilterDataString(filterMap.toString())
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
                        .setTriggerData(SOURCE_TRIGGER_DATA)
                        .setTriggerSpecs(triggerSpecs)
                        .setTriggerSpecsString(triggerSpecs.encodeToJson())
                        .setTriggerDataMatching(Source.TriggerDataMatching.EXACT)
                        .setMaxEventLevelReports(triggerSpecs.getMaxReports())
                        .setEventAttributionStatus(null)
                        .setPrivacyParameters(triggerSpecs.encodePrivacyParametersToJsonString())
                        .setDropSourceIfInstalled(true)
                        .setAttributionScopes(List.of("1", "2", "3"))
                        .setAttributionScopeLimit(4L)
                        .setMaxEventStates(10L)
                        .setDestinationLimitPriority(100L)
                        .setDestinationLimitAlgorithm(Source.DestinationLimitAlgorithm.FIFO)
                        .setEventLevelEpsilon(event_level_epsilon)
                        .setAggregateDebugReportingString(aggregateDebugReportingString)
                        .setAggregateDebugReportContributions(aggregateDebugReportContributions)
                        .build());
    }

    @Test
    public void testEqualsFail() throws Exception {
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
        assertNotEquals(
                SourceFixture.getMinimalValidSourceBuilder()
                        .setTriggerData(SOURCE_TRIGGER_DATA)
                        .build(),
                SourceFixture.getMinimalValidSourceBuilder()
                        .setTriggerData(Set.of(new UnsignedLong(24L), new UnsignedLong(1L)))
                        .build());

        TriggerSpecs triggerSpecsValueSumBased = SourceFixture.getValidTriggerSpecsValueSum(5);
        TriggerSpecs triggerSpecsCountBased = SourceFixture.getValidTriggerSpecsCountBased();

        assertNotEquals(
                SourceFixture.getMinimalValidSourceBuilder()
                        .setTriggerSpecsString(triggerSpecsValueSumBased.encodeToJson())
                        .build(),
                SourceFixture.getMinimalValidSourceBuilder()
                        .setTriggerSpecsString(triggerSpecsCountBased.encodeToJson())
                        .build());
        assertNotEquals(
                SourceFixture.getMinimalValidSourceBuilder()
                        .setTriggerDataMatching(Source.TriggerDataMatching.MODULUS)
                        .build(),
                SourceFixture.getMinimalValidSourceBuilder()
                        .setTriggerDataMatching(Source.TriggerDataMatching.EXACT)
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
                                triggerSpecsValueSumBased.encodePrivacyParametersToJsonString())
                        .build(),
                SourceFixture.getMinimalValidSourceBuilder()
                        .setPrivacyParameters(
                                triggerSpecsCountBased.encodePrivacyParametersToJsonString())
                        .build());
        assertNotEquals(
                SourceFixture.getMinimalValidSourceBuilder().setDropSourceIfInstalled(true).build(),
                SourceFixture.getMinimalValidSourceBuilder()
                        .setDropSourceIfInstalled(false)
                        .build());
        assertNotEquals(
                SourceFixture.getMinimalValidSourceWithAttributionScope()
                        .setAttributionScopes(List.of("1", "2", "3"))
                        .build(),
                SourceFixture.getMinimalValidSourceWithAttributionScope()
                        .setAttributionScopes(List.of("4", "5", "6"))
                        .build());
        assertNotEquals(
                SourceFixture.getMinimalValidSourceWithAttributionScope()
                        .setAttributionScopeLimit(10L)
                        .build(),
                SourceFixture.getMinimalValidSourceWithAttributionScope()
                        .setAttributionScopeLimit(5L)
                        .build());
        assertNotEquals(
                SourceFixture.getMinimalValidSourceWithAttributionScope()
                        .setMaxEventStates(1L)
                        .build(),
                SourceFixture.getMinimalValidSourceWithAttributionScope()
                        .setMaxEventStates(2L)
                        .build());
        assertNotEquals(
                SourceFixture.getMinimalValidSourceWithAttributionScope()
                        .setDestinationLimitPriority(10L)
                        .build(),
                SourceFixture.getMinimalValidSourceWithAttributionScope()
                        .setDestinationLimitPriority(20L)
                        .build());
        assertNotEquals(
                SourceFixture.getMinimalValidSourceWithAttributionScope()
                        .setDestinationLimitAlgorithm(Source.DestinationLimitAlgorithm.FIFO)
                        .build(),
                SourceFixture.getMinimalValidSourceWithAttributionScope()
                        .setDestinationLimitAlgorithm(Source.DestinationLimitAlgorithm.LIFO)
                        .build());
        assertNotEquals(
                SourceFixture.getMinimalValidSourceBuilder().setEventLevelEpsilon(10D).build(),
                SourceFixture.getMinimalValidSourceBuilder().setEventLevelEpsilon(11D).build());
        assertThat(
                        SourceFixture.getMinimalValidSourceBuilder()
                                .setAggregateDebugReportingString(
                                        "{\"budget\":1024,\"key_piece\":\"0x1\"}")
                                .build())
                .isNotEqualTo(
                        SourceFixture.getMinimalValidSourceBuilder()
                                .setAggregateDebugReportingString(
                                        "{\"budget\":1024,\"key_piece\":\"0x2\"}")
                                .build());
        assertNotEquals(
                SourceFixture.getMinimalValidSourceBuilder()
                        .setAggregateDebugReportContributions(1024)
                        .build(),
                SourceFixture.getMinimalValidSourceBuilder()
                        .setAggregateDebugReportContributions(1025)
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
                SourceFixture.ValidSourceParams.buildFilterDataString(),
                SourceFixture.ValidSourceParams.REGISTRATION_ID,
                SourceFixture.ValidSourceParams.SHARED_AGGREGATE_KEYS,
                SourceFixture.ValidSourceParams.SHARED_FILTER_DATA_KEYS,
                SourceFixture.ValidSourceParams.INSTALL_TIME,
                SourceFixture.ValidSourceParams.REGISTRATION_ORIGIN,
                SourceFixture.ValidSourceParams.EVENT_LEVEL_EPSILON,
                SourceFixture.ValidSourceParams.AGGREGATE_DEBUG_REPORT,
                SourceFixture.ValidSourceParams.AGGREGATE_DEBUG_REPORT_CONTRIBUTIONS);

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
                SourceFixture.ValidSourceParams.buildFilterDataString(),
                SourceFixture.ValidSourceParams.REGISTRATION_ID,
                SourceFixture.ValidSourceParams.SHARED_AGGREGATE_KEYS,
                SourceFixture.ValidSourceParams.SHARED_FILTER_DATA_KEYS,
                SourceFixture.ValidSourceParams.INSTALL_TIME,
                SourceFixture.ValidSourceParams.REGISTRATION_ORIGIN,
                SourceFixture.ValidSourceParams.EVENT_LEVEL_EPSILON,
                SourceFixture.ValidSourceParams.AGGREGATE_DEBUG_REPORT,
                SourceFixture.ValidSourceParams.AGGREGATE_DEBUG_REPORT_CONTRIBUTIONS);
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
                SourceFixture.ValidSourceParams.buildFilterDataString(),
                SourceFixture.ValidSourceParams.REGISTRATION_ID,
                SourceFixture.ValidSourceParams.SHARED_AGGREGATE_KEYS,
                SourceFixture.ValidSourceParams.SHARED_FILTER_DATA_KEYS,
                SourceFixture.ValidSourceParams.INSTALL_TIME,
                SourceFixture.ValidSourceParams.REGISTRATION_ORIGIN,
                SourceFixture.ValidSourceParams.EVENT_LEVEL_EPSILON,
                SourceFixture.ValidSourceParams.AGGREGATE_DEBUG_REPORT,
                SourceFixture.ValidSourceParams.AGGREGATE_DEBUG_REPORT_CONTRIBUTIONS);

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
                SourceFixture.ValidSourceParams.buildFilterDataString(),
                SourceFixture.ValidSourceParams.REGISTRATION_ID,
                SourceFixture.ValidSourceParams.SHARED_AGGREGATE_KEYS,
                SourceFixture.ValidSourceParams.SHARED_FILTER_DATA_KEYS,
                SourceFixture.ValidSourceParams.INSTALL_TIME,
                SourceFixture.ValidSourceParams.REGISTRATION_ORIGIN,
                SourceFixture.ValidSourceParams.EVENT_LEVEL_EPSILON,
                SourceFixture.ValidSourceParams.AGGREGATE_DEBUG_REPORT,
                SourceFixture.ValidSourceParams.AGGREGATE_DEBUG_REPORT_CONTRIBUTIONS);

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
                SourceFixture.ValidSourceParams.buildFilterDataString(),
                SourceFixture.ValidSourceParams.REGISTRATION_ID,
                SourceFixture.ValidSourceParams.SHARED_AGGREGATE_KEYS,
                SourceFixture.ValidSourceParams.SHARED_FILTER_DATA_KEYS,
                SourceFixture.ValidSourceParams.INSTALL_TIME,
                SourceFixture.ValidSourceParams.REGISTRATION_ORIGIN,
                SourceFixture.ValidSourceParams.EVENT_LEVEL_EPSILON,
                SourceFixture.ValidSourceParams.AGGREGATE_DEBUG_REPORT,
                SourceFixture.ValidSourceParams.AGGREGATE_DEBUG_REPORT_CONTRIBUTIONS);

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
                SourceFixture.ValidSourceParams.buildFilterDataString(),
                SourceFixture.ValidSourceParams.REGISTRATION_ID,
                SourceFixture.ValidSourceParams.SHARED_AGGREGATE_KEYS,
                SourceFixture.ValidSourceParams.SHARED_FILTER_DATA_KEYS,
                SourceFixture.ValidSourceParams.INSTALL_TIME,
                SourceFixture.ValidSourceParams.REGISTRATION_ORIGIN,
                SourceFixture.ValidSourceParams.EVENT_LEVEL_EPSILON,
                SourceFixture.ValidSourceParams.AGGREGATE_DEBUG_REPORT,
                SourceFixture.ValidSourceParams.AGGREGATE_DEBUG_REPORT_CONTRIBUTIONS);

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
                SourceFixture.ValidSourceParams.buildFilterDataString(),
                SourceFixture.ValidSourceParams.REGISTRATION_ID,
                SourceFixture.ValidSourceParams.SHARED_AGGREGATE_KEYS,
                SourceFixture.ValidSourceParams.SHARED_FILTER_DATA_KEYS,
                SourceFixture.ValidSourceParams.INSTALL_TIME,
                SourceFixture.ValidSourceParams.REGISTRATION_ORIGIN,
                SourceFixture.ValidSourceParams.EVENT_LEVEL_EPSILON,
                SourceFixture.ValidSourceParams.AGGREGATE_DEBUG_REPORT,
                SourceFixture.ValidSourceParams.AGGREGATE_DEBUG_REPORT_CONTRIBUTIONS);
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
                SourceFixture.ValidSourceParams.buildFilterDataString(),
                SourceFixture.ValidSourceParams.REGISTRATION_ID,
                SourceFixture.ValidSourceParams.SHARED_AGGREGATE_KEYS,
                SourceFixture.ValidSourceParams.SHARED_FILTER_DATA_KEYS,
                SourceFixture.ValidSourceParams.INSTALL_TIME,
                SourceFixture.ValidSourceParams.REGISTRATION_ORIGIN,
                SourceFixture.ValidSourceParams.EVENT_LEVEL_EPSILON,
                SourceFixture.ValidSourceParams.AGGREGATE_DEBUG_REPORT,
                SourceFixture.ValidSourceParams.AGGREGATE_DEBUG_REPORT_CONTRIBUTIONS);
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
                SourceFixture.ValidSourceParams.buildFilterDataString(),
                SourceFixture.ValidSourceParams.REGISTRATION_ID,
                SourceFixture.ValidSourceParams.SHARED_AGGREGATE_KEYS,
                SourceFixture.ValidSourceParams.SHARED_FILTER_DATA_KEYS,
                SourceFixture.ValidSourceParams.INSTALL_TIME,
                SourceFixture.ValidSourceParams.REGISTRATION_ORIGIN,
                SourceFixture.ValidSourceParams.EVENT_LEVEL_EPSILON,
                SourceFixture.ValidSourceParams.AGGREGATE_DEBUG_REPORT,
                SourceFixture.ValidSourceParams.AGGREGATE_DEBUG_REPORT_CONTRIBUTIONS);

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
                SourceFixture.ValidSourceParams.buildFilterDataString(),
                SourceFixture.ValidSourceParams.REGISTRATION_ID,
                SourceFixture.ValidSourceParams.SHARED_AGGREGATE_KEYS,
                SourceFixture.ValidSourceParams.SHARED_FILTER_DATA_KEYS,
                SourceFixture.ValidSourceParams.INSTALL_TIME,
                SourceFixture.ValidSourceParams.REGISTRATION_ORIGIN,
                SourceFixture.ValidSourceParams.EVENT_LEVEL_EPSILON,
                SourceFixture.ValidSourceParams.AGGREGATE_DEBUG_REPORT,
                SourceFixture.ValidSourceParams.AGGREGATE_DEBUG_REPORT_CONTRIBUTIONS);
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
                SourceFixture.ValidSourceParams.buildFilterDataString(),
                SourceFixture.ValidSourceParams.REGISTRATION_ID,
                SourceFixture.ValidSourceParams.SHARED_AGGREGATE_KEYS,
                SourceFixture.ValidSourceParams.SHARED_FILTER_DATA_KEYS,
                SourceFixture.ValidSourceParams.INSTALL_TIME,
                SourceFixture.ValidSourceParams.REGISTRATION_ORIGIN,
                SourceFixture.ValidSourceParams.EVENT_LEVEL_EPSILON,
                SourceFixture.ValidSourceParams.AGGREGATE_DEBUG_REPORT,
                SourceFixture.ValidSourceParams.AGGREGATE_DEBUG_REPORT_CONTRIBUTIONS);
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
        Trigger trigger = TriggerFixture.getValidTrigger();

        assertNotNull(source.getAggregatableAttributionSource(trigger, mMockFlags).orElse(null));
        assertNotNull(
                source.getAggregatableAttributionSource(trigger, mMockFlags)
                        .get()
                        .getAggregatableSource());
        assertNotNull(
                source.getAggregatableAttributionSource(trigger, mMockFlags).get().getFilterMap());
        assertEquals(
                aggregatableSource,
                source.getAggregatableAttributionSource(trigger, mMockFlags)
                        .get()
                        .getAggregatableSource());
        assertEquals(
                filterMap,
                source.getAggregatableAttributionSource(trigger, mMockFlags)
                        .get()
                        .getFilterMap()
                        .getAttributionFilterMap());
    }

    @Test
    public void aggregateDebugReport_parsing_asExpected() throws JSONException {
        // Setup
        String aggregateDebugReport =
                "{\"budget\":1024,"
                        + "\"key_piece\":\"0x100\","
                        + "\"debug_data\":["
                        + "{"
                        + "\"types\": [\"source-destination-limit\"],"
                        + "\"key_piece\": \"0x111\","
                        + "\"value\": 111"
                        + "},"
                        + "{"
                        + "\"types\": [\"unspecified\"],"
                        + "\"key_piece\": \"0x222\","
                        + "\"value\": 222"
                        + "}"
                        + "]}";
        Source source =
                SourceFixture.getValidSourceBuilder()
                        .setAggregateDebugReportingString(aggregateDebugReport)
                        .build();

        // Execution
        AggregateDebugReportData debugData1 =
                new AggregateDebugReportData.Builder(
                                Collections.singleton(
                                        DebugReportApi.Type.SOURCE_DESTINATION_LIMIT.getValue()),
                                new BigInteger("111", 16),
                                111)
                        .build();
        AggregateDebugReportData debugData2 =
                new AggregateDebugReportData.Builder(
                                Collections.singleton(DebugReportApi.Type.UNSPECIFIED.getValue()),
                                new BigInteger("222", 16),
                                222)
                        .build();

        // Assertion
        assertThat(source.getAggregateDebugReportingObject())
                .isEqualTo(
                        new AggregateDebugReporting.Builder(
                                        1024,
                                        new BigInteger("100", 16),
                                        Arrays.asList(debugData1, debugData2),
                                        null)
                                .build());
    }

    @Test
    public void testReinstallReattributionWindow() throws Exception {
        final Source source =
                SourceFixture.getMinimalValidSourceBuilder()
                        .setReinstallReattributionWindow(50L)
                        .build();

        assertEquals(50L, source.getReinstallReattributionWindow());
    }

    @Test
    public void testAggregatableAttributionSourceWithTrigger_addsLookbackWindow() throws Exception {
        when(mMockFlags.getMeasurementEnableLookbackWindowFilter()).thenReturn(true);
        JSONObject aggregatableSource = new JSONObject();
        aggregatableSource.put("campaignCounts", "0x159");
        aggregatableSource.put("geoValue", "0x5");

        JSONObject filterMapJson = new JSONObject();
        filterMapJson.put("conversion", new JSONArray(Collections.singletonList("electronics")));

        final Source source =
                SourceFixture.getMinimalValidSourceBuilder()
                        .setAggregateSource(aggregatableSource.toString())
                        .setFilterDataString(filterMapJson.toString())
                        .build();

        Trigger trigger = TriggerFixture.getValidTrigger();
        Optional<AggregatableAttributionSource> aggregatableAttributionSource =
                source.getAggregatableAttributionSource(trigger, mMockFlags);
        assertThat(aggregatableAttributionSource.isPresent()).isTrue();
        assertThat(aggregatableAttributionSource.get().getAggregatableSource())
                .containsExactly(
                        "campaignCounts",
                        new BigInteger("159", 16),
                        "geoValue",
                        new BigInteger("5", 16));
        assertThat(
                        aggregatableAttributionSource
                                .get()
                                .getFilterMap()
                                .getAttributionFilterMapWithLongValue())
                .containsExactly(
                        "conversion",
                        FilterValue.ofStringList(Collections.singletonList("electronics")),
                        "source_type",
                        FilterValue.ofStringList(Collections.singletonList("event")),
                        FilterMap.LOOKBACK_WINDOW,
                        FilterValue.ofLong(8640000L));
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
    public void testGetAllAttributionDestinations() {
        Source source = SourceFixture.getValidSource();
        assertThat(source.getAllAttributionDestinations())
                .containsExactly(
                        Pair.create(
                                EventSurfaceType.APP,
                                Uri.parse("android-app://com.destination").toString()),
                        Pair.create(
                                EventSurfaceType.WEB,
                                Uri.parse("https://destination.com").toString()));
    }

    @Test
    public void testGetFilterData_nonEmpty() throws Exception {
        JSONObject filterMapJson = new JSONObject();
        filterMapJson.put("conversion", new JSONArray(Collections.singletonList("electronics")));
        filterMapJson.put("product", new JSONArray(Arrays.asList("1234", "2345")));
        Source source =
                SourceFixture.getMinimalValidSourceBuilder()
                        .setSourceType(Source.SourceType.NAVIGATION)
                        .setFilterDataString(filterMapJson.toString())
                        .build();
        Trigger trigger = TriggerFixture.getValidTrigger();
        FilterMap filterMap = source.getFilterData(trigger, mMockFlags);
        assertEquals(filterMap.getAttributionFilterMap().size(), 3);
        assertEquals(Collections.singletonList("electronics"),
                filterMap.getAttributionFilterMap().get("conversion"));
        assertEquals(Arrays.asList("1234", "2345"),
                filterMap.getAttributionFilterMap().get("product"));
        assertEquals(
                Collections.singletonList("navigation"),
                filterMap.getAttributionFilterMap().get("source_type"));
    }

    @Test
    public void testGetFilterData_withTrigger_addsLookbackWindow() throws Exception {
        when(mMockFlags.getMeasurementEnableLookbackWindowFilter()).thenReturn(true);
        JSONObject filterMapJson = new JSONObject();
        filterMapJson.put("conversion", new JSONArray(List.of("electronics")));
        filterMapJson.put("product", new JSONArray(List.of("1234", "2345")));
        Source source =
                SourceFixture.getMinimalValidSourceBuilder()
                        .setSourceType(Source.SourceType.NAVIGATION)
                        .setFilterDataString(filterMapJson.toString())
                        .build();
        Trigger trigger = TriggerFixture.getValidTrigger();
        FilterMap filterMap = source.getFilterData(trigger, mMockFlags);
        assertThat(filterMap.getAttributionFilterMapWithLongValue())
                .containsExactly(
                        "conversion",
                        FilterValue.ofStringList(List.of("electronics")),
                        "product",
                        FilterValue.ofStringList(List.of("1234", "2345")),
                        "source_type",
                        FilterValue.ofStringList(List.of("navigation")),
                        FilterMap.LOOKBACK_WINDOW,
                        FilterValue.ofLong(8640000L));
    }

    @Test
    public void testGetFilterData_withTriggerAndEmptyData_addsLookbackWindow() throws Exception {
        when(mMockFlags.getMeasurementEnableLookbackWindowFilter()).thenReturn(true);
        Source source =
                SourceFixture.getMinimalValidSourceBuilder()
                        .setSourceType(Source.SourceType.NAVIGATION)
                        .build();
        Trigger trigger = TriggerFixture.getValidTrigger();
        FilterMap filterMap = source.getFilterData(trigger, mMockFlags);
        assertThat(filterMap.getAttributionFilterMapWithLongValue())
                .containsExactly(
                        "source_type",
                        FilterValue.ofStringList(List.of("navigation")),
                        FilterMap.LOOKBACK_WINDOW,
                        FilterValue.ofLong(8640000L));
    }

    @Test
    public void testGetSharedFilterData_withLongValue_success() throws Exception {
        when(mMockFlags.getMeasurementEnableLookbackWindowFilter()).thenReturn(true);
        JSONObject filterMapJson = new JSONObject();
        filterMapJson.put("conversion", new JSONArray(List.of("electronics")));
        filterMapJson.put("product", new JSONArray(List.of("1234", "2345")));
        String sharedFilterDataKeys = "[\"product\"]";
        Source source =
                SourceFixture.getMinimalValidSourceBuilder()
                        .setSourceType(Source.SourceType.NAVIGATION)
                        .setFilterDataString(filterMapJson.toString())
                        .setSharedFilterDataKeys(sharedFilterDataKeys)
                        .build();
        Trigger trigger = TriggerFixture.getValidTrigger();
        FilterMap filterMap = source.getSharedFilterData(trigger, mMockFlags);
        assertThat(filterMap.getAttributionFilterMapWithLongValue())
                .containsExactly("product", FilterValue.ofStringList(List.of("1234", "2345")));
    }

    @Test
    public void testGetSharedFilterData_success() throws Exception {
        when(mMockFlags.getMeasurementEnableLookbackWindowFilter()).thenReturn(false);
        JSONObject filterMapJson = new JSONObject();
        filterMapJson.put("conversion", new JSONArray(List.of("electronics")));
        filterMapJson.put("product", new JSONArray(List.of("1234", "2345")));
        String sharedFilterDataKeys = "[\"product\"]";
        Source source =
                SourceFixture.getMinimalValidSourceBuilder()
                        .setSourceType(Source.SourceType.NAVIGATION)
                        .setFilterDataString(filterMapJson.toString())
                        .setSharedFilterDataKeys(sharedFilterDataKeys)
                        .build();
        Trigger trigger = TriggerFixture.getValidTrigger();
        FilterMap filterMap = source.getSharedFilterData(trigger, mMockFlags);
        assertThat(filterMap.getAttributionFilterMap())
                .containsExactly("product", List.of("1234", "2345"));
    }

    @Test
    public void testGetFilterData_nullFilterData() throws Exception {
        Source source =
                SourceFixture.getMinimalValidSourceBuilder()
                        .setSourceType(Source.SourceType.EVENT)
                        .build();
        Trigger trigger = TriggerFixture.getValidTrigger();
        FilterMap filterMap = source.getFilterData(trigger, mMockFlags);
        assertEquals(filterMap.getAttributionFilterMap().size(), 1);
        assertEquals(Collections.singletonList("event"),
                filterMap.getAttributionFilterMap().get("source_type"));
    }

    @Test
    public void testGetFilterData_emptyFilterData() throws Exception {
        Source source =
                SourceFixture.getMinimalValidSourceBuilder()
                        .setSourceType(Source.SourceType.EVENT)
                        .setFilterDataString("")
                        .build();
        Trigger trigger = TriggerFixture.getValidTrigger();
        FilterMap filterMap = source.getFilterData(trigger, mMockFlags);
        assertEquals(filterMap.getAttributionFilterMap().size(), 1);
        assertEquals(Collections.singletonList("event"),
                filterMap.getAttributionFilterMap().get("source_type"));
    }

    @Test
    public void testParseAggregateSource() throws Exception {
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
                        .setFilterDataString(filterMap.toString())
                        .build();
        Trigger trigger = TriggerFixture.getValidTrigger();
        Optional<AggregatableAttributionSource> aggregatableAttributionSource =
                source.getAggregatableAttributionSource(trigger, mMockFlags);
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
    public void setSharedDebugKey_success() throws Exception {
        Source source =
                SourceFixture.getMinimalValidSourceBuilder()
                        .setSharedDebugKey(SHARED_DEBUG_KEY_1)
                        .build();
        assertEquals(SHARED_DEBUG_KEY_1, source.getSharedDebugKey());
    }

    @Test
    public void attributedTriggersToJson_buildAttributedTriggers_encodesAndDecodes()
            throws Exception {
        JSONArray existingAttributes = new JSONArray();
        JSONObject triggerRecord1 = new JSONObject();
        triggerRecord1.put("trigger_id", "100");
        triggerRecord1.put("trigger_data", new UnsignedLong(123L).toString());
        triggerRecord1.put("dedup_key", new UnsignedLong(456L).toString());

        JSONObject triggerRecord2 = new JSONObject();
        triggerRecord2.put("trigger_id", "200");
        triggerRecord2.put("trigger_data", new UnsignedLong(12L).toString());
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
                sourceWithEventAttributionStatus.attributedTriggersToJson();
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
    public void attributedTriggersToJsonFlexApi_buildAttributedTriggers_encodesAndDecodes()
            throws Exception {
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
                sourceWithEventAttributionStatus.attributedTriggersToJsonFlexApi();
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
            throws Exception {
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
    public void triggerSpecs_encodingDecoding_equal() throws Exception {
        // Setup
        Source validSource = SourceFixture.getValidSourceWithFlexEventReport();
        TriggerSpecs originalTriggerSpecs = validSource.getTriggerSpecs();
        String encodedTriggerSpecs = originalTriggerSpecs.encodeToJson();
        String encodeddMaxReports = Integer.toString(originalTriggerSpecs.getMaxReports());
        String encodedPrivacyParameters =
                originalTriggerSpecs.encodePrivacyParametersToJsonString();
        TriggerSpecs triggerSpecs =
                new TriggerSpecs(
                        encodedTriggerSpecs,
                        encodeddMaxReports,
                        validSource,
                        encodedPrivacyParameters);

        // Assertion
        assertEquals(originalTriggerSpecs.getMaxReports(), triggerSpecs.getMaxReports());
        assertArrayEquals(originalTriggerSpecs.getTriggerSpecs(), triggerSpecs.getTriggerSpecs());
        assertEquals(originalTriggerSpecs, triggerSpecs);
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
            @Nullable String filterDataString,
            @Nullable String registrationId,
            @Nullable String sharedAggregationKeys,
            @Nullable String sharedFilterDataKeys,
            @Nullable Long installTime,
            Uri registrationOrigin,
            @Nullable Double eventLevelEpsilon,
            @Nullable String aggregateDebugReportingString,
            @Nullable Integer aggregateDebugReportContributions) {
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
                                .setFilterDataString(filterDataString)
                                .setDebugKey(debugKey)
                                .setRegistrationId(registrationId)
                                .setSharedAggregationKeys(sharedAggregationKeys)
                                .setInstallTime(installTime)
                                .setRegistrationOrigin(registrationOrigin)
                                .setSharedFilterDataKeys(sharedFilterDataKeys)
                                .setEventLevelEpsilon(eventLevelEpsilon)
                                .setAggregateDebugReportingString(aggregateDebugReportingString)
                                .setAggregateDebugReportContributions(
                                        aggregateDebugReportContributions)
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
    public void validateAndSetNumReportStates_flexLiteValid_returnsTrue() {
        doReturn(Flags.MEASUREMENT_MAX_REPORT_STATES_PER_SOURCE_REGISTRATION)
                .when(mMockFlags)
                .getMeasurementMaxReportStatesPerSourceRegistration();
        doReturn(Flags.DEFAULT_MEASUREMENT_VTC_CONFIGURABLE_MAX_EVENT_REPORTS_COUNT)
                .when(mMockFlags)
                .getMeasurementVtcConfigurableMaxEventReportsCount();
        long baseTime = System.currentTimeMillis();
        Source source =
                SourceFixture.getMinimalValidSourceBuilder()
                        .setSourceType(Source.SourceType.NAVIGATION)
                        .setEventTime(baseTime)
                        .setEventReportWindows(
                                "{"
                                        + "\"start_time\": \"0\","
                                        + String.format(
                                                "\"end_times\": [%s, %s]}",
                                                baseTime + TimeUnit.DAYS.toMillis(7),
                                                baseTime + TimeUnit.DAYS.toMillis(30)))
                        .setMaxEventLevelReports(2)
                        .build();
        assertTrue(source.validateAndSetNumReportStates(mMockFlags));
    }

    @Test
    public void validateAttributionScopeValues_attributionScopeEnabledValid_returnsTrue() {
        doReturn(true).when(mMockFlags).getMeasurementEnableAttributionScope();
        doReturn(Flags.MEASUREMENT_MAX_REPORT_STATES_PER_SOURCE_REGISTRATION)
                .when(mMockFlags)
                .getMeasurementMaxReportStatesPerSourceRegistration();
        doReturn(Flags.DEFAULT_MEASUREMENT_VTC_CONFIGURABLE_MAX_EVENT_REPORTS_COUNT)
                .when(mMockFlags)
                .getMeasurementVtcConfigurableMaxEventReportsCount();
        doReturn(Flags.MEASUREMENT_ATTRIBUTION_SCOPE_MAX_INFO_GAIN_NAVIGATION)
                .when(mMockFlags)
                .getMeasurementAttributionScopeMaxInfoGainNavigation();
        doReturn(Flags.MEASUREMENT_ATTRIBUTION_SCOPE_MAX_INFO_GAIN_EVENT)
                .when(mMockFlags)
                .getMeasurementAttributionScopeMaxInfoGainEvent();
        long baseTime = System.currentTimeMillis();
        Source source =
                SourceFixture.getMinimalValidSourceBuilder()
                        .setSourceType(Source.SourceType.EVENT)
                        .setEventTime(baseTime)
                        .setEventReportWindows(
                                "{"
                                        + "\"start_time\": \"0\","
                                        + String.format(
                                                "\"end_times\": [%s, %s]}",
                                                baseTime + TimeUnit.DAYS.toMillis(7),
                                                baseTime + TimeUnit.DAYS.toMillis(30)))
                        .setMaxEventLevelReports(2)
                        .setAttributionScopes(List.of("1", "2"))
                        // Attribution scope information gain:
                        .setAttributionScopeLimit(2L)
                        .setMaxEventStates(15L)
                        .build();
        assertThat(source.validateAttributionScopeValues(mMockFlags))
                .isEqualTo(Source.AttributionScopeValidationResult.VALID);
    }

    @Test
    public void validateAttributionScopeValues_infoGainTooHigh_returnsFalse() {
        doReturn(true).when(mMockFlags).getMeasurementEnableAttributionScope();
        doReturn(Flags.MEASUREMENT_MAX_REPORT_STATES_PER_SOURCE_REGISTRATION)
                .when(mMockFlags)
                .getMeasurementMaxReportStatesPerSourceRegistration();
        doReturn(Flags.DEFAULT_MEASUREMENT_VTC_CONFIGURABLE_MAX_EVENT_REPORTS_COUNT)
                .when(mMockFlags)
                .getMeasurementVtcConfigurableMaxEventReportsCount();
        doReturn(Flags.MEASUREMENT_ATTRIBUTION_SCOPE_MAX_INFO_GAIN_NAVIGATION)
                .when(mMockFlags)
                .getMeasurementAttributionScopeMaxInfoGainNavigation();
        doReturn(Flags.MEASUREMENT_ATTRIBUTION_SCOPE_MAX_INFO_GAIN_EVENT)
                .when(mMockFlags)
                .getMeasurementAttributionScopeMaxInfoGainEvent();
        long baseTime = System.currentTimeMillis();
        Source source =
                SourceFixture.getMinimalValidSourceBuilder()
                        .setSourceType(Source.SourceType.NAVIGATION)
                        .setEventTime(baseTime)
                        .setEventReportWindows(
                                "{"
                                        + "\"start_time\": \"0\","
                                        + String.format(
                                                "\"end_times\": [%s, %s]}",
                                                baseTime + TimeUnit.DAYS.toMillis(7),
                                                baseTime + TimeUnit.DAYS.toMillis(30)))
                        .setMaxEventLevelReports(2)
                        .setAttributionScopes(List.of("1", "2"))
                        // Attribution scope information gain: 11.622509454683335 > 11.55.
                        .setAttributionScopeLimit(4L)
                        .setMaxEventStates(1000L)
                        .build();
        assertThat(source.validateAttributionScopeValues(mMockFlags))
                .isEqualTo(Source.AttributionScopeValidationResult.INVALID_INFORMATION_GAIN_LIMIT);
    }

    @Test
    public void validateAttributionScopeValues_infoGainValid_returnsTrue() {
        doReturn(true).when(mMockFlags).getMeasurementEnableAttributionScope();
        doReturn(Flags.MEASUREMENT_MAX_REPORT_STATES_PER_SOURCE_REGISTRATION)
                .when(mMockFlags)
                .getMeasurementMaxReportStatesPerSourceRegistration();
        doReturn(Flags.DEFAULT_MEASUREMENT_VTC_CONFIGURABLE_MAX_EVENT_REPORTS_COUNT)
                .when(mMockFlags)
                .getMeasurementVtcConfigurableMaxEventReportsCount();
        doReturn(Flags.MEASUREMENT_ATTRIBUTION_SCOPE_MAX_INFO_GAIN_NAVIGATION)
                .when(mMockFlags)
                .getMeasurementAttributionScopeMaxInfoGainNavigation();
        doReturn(Flags.MEASUREMENT_ATTRIBUTION_SCOPE_MAX_INFO_GAIN_EVENT)
                .when(mMockFlags)
                .getMeasurementAttributionScopeMaxInfoGainEvent();
        long baseTime = System.currentTimeMillis();
        Source source =
                SourceFixture.getMinimalValidSourceBuilder()
                        .setSourceType(Source.SourceType.NAVIGATION)
                        .setEventTime(baseTime)
                        .setEventReportWindows(
                                "{"
                                        + "\"start_time\": \"0\","
                                        + String.format(
                                                "\"end_times\": [%s, %s]}",
                                                baseTime + TimeUnit.DAYS.toMillis(7),
                                                baseTime + TimeUnit.DAYS.toMillis(30)))
                        .setMaxEventLevelReports(2)
                        .setAttributionScopes(List.of("1", "2"))
                        // Attribution scope information gain: 11.072132604167233 < 11.55.
                        .setAttributionScopeLimit(3L)
                        .setMaxEventStates(1000L)
                        .build();
        assertThat(source.validateAttributionScopeValues(mMockFlags))
                .isEqualTo(Source.AttributionScopeValidationResult.VALID);
    }

    @Test
    public void validateAttributionScopeValues_maxEventStatesNullNonDefaultVtc_returnsFalse() {
        doReturn(true).when(mMockFlags).getMeasurementEnableAttributionScope();
        doReturn(Flags.MEASUREMENT_MAX_REPORT_STATES_PER_SOURCE_REGISTRATION)
                .when(mMockFlags)
                .getMeasurementMaxReportStatesPerSourceRegistration();
        doReturn(Flags.DEFAULT_MEASUREMENT_VTC_CONFIGURABLE_MAX_EVENT_REPORTS_COUNT)
                .when(mMockFlags)
                .getMeasurementVtcConfigurableMaxEventReportsCount();
        long baseTime = System.currentTimeMillis();
        // Source with numStates = 15.
        Source source =
                SourceFixture.getMinimalValidSourceBuilder()
                        .setSourceType(Source.SourceType.EVENT)
                        .setEventTime(baseTime)
                        .setEventReportWindows(
                                "{"
                                        + "\"start_time\": \"0\","
                                        + String.format(
                                                "\"end_times\": [%s, %s]}",
                                                baseTime + TimeUnit.DAYS.toMillis(7),
                                                baseTime + TimeUnit.DAYS.toMillis(30)))
                        .setMaxEventLevelReports(2)
                        .setAttributionScopes(List.of("1", "2"))
                        .setAttributionScopeLimit(3L)
                        .setMaxEventStates(3L)
                        .build();
        assertThat(source.validateAttributionScopeValues(mMockFlags))
                .isEqualTo(Source.AttributionScopeValidationResult.INVALID_MAX_EVENT_STATES_LIMIT);
    }

    @Test
    public void validateAttributionScopeValues_maxEventStatesNullNavigation_returnsFalse() {
        doReturn(true).when(mMockFlags).getMeasurementEnableAttributionScope();
        doReturn(Flags.MEASUREMENT_MAX_REPORT_STATES_PER_SOURCE_REGISTRATION)
                .when(mMockFlags)
                .getMeasurementMaxReportStatesPerSourceRegistration();
        doReturn(Flags.DEFAULT_MEASUREMENT_VTC_CONFIGURABLE_MAX_EVENT_REPORTS_COUNT)
                .when(mMockFlags)
                .getMeasurementVtcConfigurableMaxEventReportsCount();
        doReturn(Flags.MEASUREMENT_ATTRIBUTION_SCOPE_MAX_INFO_GAIN_NAVIGATION)
                .when(mMockFlags)
                .getMeasurementAttributionScopeMaxInfoGainNavigation();
        doReturn(Flags.MEASUREMENT_ATTRIBUTION_SCOPE_MAX_INFO_GAIN_EVENT)
                .when(mMockFlags)
                .getMeasurementAttributionScopeMaxInfoGainEvent();
        long baseTime = System.currentTimeMillis();
        // Source with numStates = 15.
        Source source =
                SourceFixture.getMinimalValidSourceBuilder()
                        .setSourceType(Source.SourceType.NAVIGATION)
                        .setEventTime(baseTime)
                        .setEventReportWindows(
                                "{"
                                        + "\"start_time\": \"0\","
                                        + String.format(
                                                "\"end_times\": [%s, %s]}",
                                                baseTime + TimeUnit.DAYS.toMillis(7),
                                                baseTime + TimeUnit.DAYS.toMillis(30)))
                        .setMaxEventLevelReports(2)
                        .setAttributionScopes(List.of("1", "2"))
                        .setAttributionScopeLimit(3L)
                        .setMaxEventStates(3L)
                        .build();
        assertThat(source.validateAttributionScopeValues(mMockFlags))
                .isEqualTo(Source.AttributionScopeValidationResult.VALID);
        assertThat(source.getMaxEventStates()).isEqualTo(DEFAULT_MAX_EVENT_STATES);
    }

    @Test
    public void validateAttributionScopeValues_maxEventStatesTooLow_maxEventStatesLimit() {
        doReturn(true).when(mMockFlags).getMeasurementEnableAttributionScope();
        doReturn(Flags.MEASUREMENT_MAX_REPORT_STATES_PER_SOURCE_REGISTRATION)
                .when(mMockFlags)
                .getMeasurementMaxReportStatesPerSourceRegistration();
        doReturn(Flags.DEFAULT_MEASUREMENT_VTC_CONFIGURABLE_MAX_EVENT_REPORTS_COUNT)
                .when(mMockFlags)
                .getMeasurementVtcConfigurableMaxEventReportsCount();
        long baseTime = System.currentTimeMillis();
        Source source =
                SourceFixture.getMinimalValidSourceBuilder()
                        .setSourceType(Source.SourceType.EVENT)
                        .setEventTime(baseTime)
                        .setEventReportWindows(
                                "{"
                                        + "\"start_time\": \"0\","
                                        + String.format(
                                                "\"end_times\": [%s, %s]}",
                                                baseTime + TimeUnit.DAYS.toMillis(7),
                                                baseTime + TimeUnit.DAYS.toMillis(30)))
                        .setMaxEventLevelReports(2)
                        .setAttributionScopes(List.of("1", "2"))
                        .setAttributionScopeLimit(5L)
                        .setMaxEventStates(3L)
                        .build();
        assertThat(source.validateAttributionScopeValues(mMockFlags))
                .isEqualTo(Source.AttributionScopeValidationResult.INVALID_MAX_EVENT_STATES_LIMIT);
    }

    @Test
    public void validateAndSetNumReportStates_flexLiteInvalid_returnsFalse() {
        doReturn(Flags.MEASUREMENT_MAX_REPORT_STATES_PER_SOURCE_REGISTRATION)
                .when(mMockFlags)
                .getMeasurementMaxReportStatesPerSourceRegistration();
        doReturn(Flags.DEFAULT_MEASUREMENT_VTC_CONFIGURABLE_MAX_EVENT_REPORTS_COUNT)
                .when(mMockFlags)
                .getMeasurementVtcConfigurableMaxEventReportsCount();
        long baseTime = System.currentTimeMillis();
        Source source =
                SourceFixture.getMinimalValidSourceBuilder()
                        .setSourceType(Source.SourceType.NAVIGATION)
                        .setEventTime(baseTime)
                        .setEventReportWindows("{"
                                + "\"start_time\": \"0\","
                                + String.format(
                                        "\"end_times\": [%s, %s, %s, %s, %s]}",
                                        baseTime + TimeUnit.DAYS.toMillis(2),
                                        baseTime + TimeUnit.DAYS.toMillis(5),
                                        baseTime + TimeUnit.DAYS.toMillis(7),
                                        baseTime + TimeUnit.DAYS.toMillis(11),
                                        baseTime + TimeUnit.DAYS.toMillis(30)))
                        .setMaxEventLevelReports(20)
                        .build();
        assertFalse(source.validateAndSetNumReportStates(mMockFlags));
    }

    @Test
    public void validateAndSetNumReportStates_fullFlexValid_returnsTrue() throws Exception {
        doReturn(Flags.DEFAULT_MEASUREMENT_VTC_CONFIGURABLE_MAX_EVENT_REPORTS_COUNT)
                .when(mMockFlags)
                .getMeasurementVtcConfigurableMaxEventReportsCount();
        doReturn(true).when(mMockFlags).getMeasurementFlexibleEventReportingApiEnabled();
        doReturn(Flags.MEASUREMENT_MAX_REPORT_STATES_PER_SOURCE_REGISTRATION)
                .when(mMockFlags)
                .getMeasurementMaxReportStatesPerSourceRegistration();
        // setup
        String triggerSpecsString =
                "[{\"trigger_data\": [0, 1],"
                        + "\"event_report_windows\": { "
                        + "\"start_time\": \"0\", "
                        + String.format("\"end_times\": [%s]},",
                                TimeUnit.DAYS.toMillis(7))
                        + "\"summary_operator\": \"count\", "
                        + "\"summary_buckets\": [1]}]";
        TriggerSpec[] triggerSpecsArray = TriggerSpecsUtil.triggerSpecArrayFrom(triggerSpecsString);
        int maxEventLevelReports = 3;
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
                        .setMaxEventLevelReports(maxEventLevelReports)
                        .setTriggerSpecs(new TriggerSpecs(
                                triggerSpecsArray, maxEventLevelReports, null))
                        .build();
        assertTrue(testSource.validateAndSetNumReportStates(mMockFlags));
    }

    @Test
    public void validateAndSetNumReportStates_fullFlexArithmeticInvalid_returnsFalse()
            throws Exception {
        doReturn(Flags.DEFAULT_MEASUREMENT_VTC_CONFIGURABLE_MAX_EVENT_REPORTS_COUNT)
                .when(mMockFlags)
                .getMeasurementVtcConfigurableMaxEventReportsCount();
        doReturn(true).when(mMockFlags).getMeasurementFlexibleEventReportingApiEnabled();
        doReturn(Flags.MEASUREMENT_MAX_REPORT_STATES_PER_SOURCE_REGISTRATION)
                .when(mMockFlags)
                .getMeasurementMaxReportStatesPerSourceRegistration();
        // setup
        String triggerSpecsString =
                "[{\"trigger_data\": [0, 1, 2, 3, 4, 5, 6, 7],"
                        + "\"event_report_windows\":{"
                        + "\"start_time\": \"0\","
                        + String.format("\"end_times\": [%s, %s, %s, %s, %s]}, ",
                                TimeUnit.DAYS.toMillis(2),
                                TimeUnit.DAYS.toMillis(5),
                                TimeUnit.DAYS.toMillis(7),
                                TimeUnit.DAYS.toMillis(11),
                                TimeUnit.DAYS.toMillis(30))
                        + "\"summary_operator\": \"count\", "
                        + "\"summary_buckets\": [1, 2, 3, 4, 5, 6, 7, 8, 9, 10, "
                                + "11, 12, 13, 14, 15, 16, 17, 18, 19, 20]}]";
        TriggerSpec[] triggerSpecsArray = TriggerSpecsUtil.triggerSpecArrayFrom(triggerSpecsString);
        int maxEventLevelReports = 20;
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
                        .setMaxEventLevelReports(maxEventLevelReports)
                        .setTriggerSpecs(new TriggerSpecs(
                                triggerSpecsArray, maxEventLevelReports, null))
                        .build();
        assertFalse(testSource.validateAndSetNumReportStates(mMockFlags));
    }

    @Test
    public void validateAndSetNumReportStates_fullFlexIterativeInvalid_returnsFalse()
            throws Exception {
        doReturn(Flags.DEFAULT_MEASUREMENT_VTC_CONFIGURABLE_MAX_EVENT_REPORTS_COUNT)
                .when(mMockFlags)
                .getMeasurementVtcConfigurableMaxEventReportsCount();
        doReturn(true).when(mMockFlags).getMeasurementFlexibleEventReportingApiEnabled();
        doReturn(Flags.MEASUREMENT_MAX_REPORT_STATES_PER_SOURCE_REGISTRATION)
                .when(mMockFlags)
                .getMeasurementMaxReportStatesPerSourceRegistration();
        // setup
        String triggerSpecsString =
                "["
                        + "{\"trigger_data\": [0, 1, 2, 3],"
                                + "\"event_report_windows\":{"
                                + "\"start_time\": \"0\","
                                + String.format("\"end_times\": [%s, %s, %s, %s, %s]}, ",
                                        TimeUnit.DAYS.toMillis(2),
                                        TimeUnit.DAYS.toMillis(5),
                                        TimeUnit.DAYS.toMillis(7),
                                        TimeUnit.DAYS.toMillis(11),
                                        TimeUnit.DAYS.toMillis(30))
                                + "\"summary_operator\": \"count\", "
                                + "\"summary_buckets\": [1, 2, 3, 4, 5, 6, 7, 8, 9, 10, "
                                        + "11, 12, 13, 14, 15, 16, 17, 18, 19, 20]}]"
                        + "{\"trigger_data\": [4, 5, 6, 7],"
                                + "\"event_report_windows\":{"
                                + "\"start_time\": \"0\","
                                + String.format("\"end_times\": [%s, %s, %s, %s]}, ",
                                        TimeUnit.DAYS.toMillis(5),
                                        TimeUnit.DAYS.toMillis(7),
                                        TimeUnit.DAYS.toMillis(11),
                                        TimeUnit.DAYS.toMillis(30))
                                + "\"summary_operator\": \"count\", "
                                + "\"summary_buckets\": [1, 2, 3, 4, 5, 6, 7, 8, 9, 10, "
                                        + "11, 12, 13, 14, 15, 16, 17, 18, 19]}]";
        TriggerSpec[] triggerSpecsArray = TriggerSpecsUtil.triggerSpecArrayFrom(triggerSpecsString);
        int maxEventLevelReports = 20;
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
                        .setMaxEventLevelReports(maxEventLevelReports)
                        .setTriggerSpecs(new TriggerSpecs(
                                triggerSpecsArray, maxEventLevelReports, null))
                        .build();
        assertFalse(testSource.validateAndSetNumReportStates(mMockFlags));
    }

    @Test
    public void
            validateAttributionScopeValues_fullFlexAttributionScopeMaxEventStatesNull_returnsFalse()
                    throws Exception {
        doReturn(true).when(mMockFlags).getMeasurementEnableAttributionScope();
        doReturn(true).when(mMockFlags).getMeasurementFlexibleEventReportingApiEnabled();
        doReturn(Flags.MEASUREMENT_MAX_REPORT_STATES_PER_SOURCE_REGISTRATION)
                .when(mMockFlags)
                .getMeasurementMaxReportStatesPerSourceRegistration();
        // setup
        String triggerSpecsString =
                "[{\"trigger_data\": [0, 1],"
                        + "\"event_report_windows\": { "
                        + "\"start_time\": \"0\", "
                        + String.format("\"end_times\": [%s]},", TimeUnit.DAYS.toMillis(7))
                        + "\"summary_operator\": \"count\", "
                        + "\"summary_buckets\": [1]}]";
        TriggerSpec[] triggerSpecsArray = TriggerSpecsUtil.triggerSpecArrayFrom(triggerSpecsString);
        int maxEventLevelReports = 3;
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
                        .setMaxEventLevelReports(maxEventLevelReports)
                        .setSourceType(Source.SourceType.EVENT)
                        .setTriggerSpecs(
                                new TriggerSpecs(triggerSpecsArray, maxEventLevelReports, null))
                        .setAttributionScopeLimit(3L)
                        .setMaxEventStates(3L)
                        .build();
        assertThat(testSource.validateAttributionScopeValues(mMockFlags))
                .isEqualTo(Source.AttributionScopeValidationResult.INVALID_MAX_EVENT_STATES_LIMIT);
    }

    @Test
    public void hasValidInformationGain_fullFlex_enableEventLevelEpsilon_success()
            throws Exception {
        when(mMockFlags.getMeasurementEnableEventLevelEpsilonInSource()).thenReturn(true);
        when(mMockFlags.getMeasurementMaxReportStatesPerSourceRegistration())
                .thenReturn(Flags.MEASUREMENT_MAX_REPORT_STATES_PER_SOURCE_REGISTRATION);
        when(mMockFlags.getMeasurementFlexApiMaxInformationGainEvent())
                .thenReturn(Flags.MEASUREMENT_FLEX_API_MAX_INFORMATION_GAIN_EVENT);
        String triggerSpecsString =
                "[{\"trigger_data\": [1, 2],"
                        + "\"event_report_windows\": { "
                        + "\"start_time\": \"0\", "
                        + String.format("\"end_times\": [%s]}, ", TimeUnit.DAYS.toMillis(7))
                        + "\"summary_operator\": \"count\", "
                        + "\"summary_buckets\": [1]}]";
        TriggerSpec[] triggerSpecsArray = TriggerSpecsUtil.triggerSpecArrayFrom(triggerSpecsString);
        int maxEventLevelReports = 3;
        Source testSource =
                SourceFixture.getMinimalValidSourceBuilder()
                        .setSourceType(Source.SourceType.EVENT)
                        .setEventLevelEpsilon(10D)
                        .setTriggerSpecs(
                                new TriggerSpecs(triggerSpecsArray, maxEventLevelReports, null))
                        .build();
        assertTrue(testSource.hasValidInformationGain(mMockFlags));
    }

    @Test
    public void hasValidInformationGain_flexLite_withEventLevelEpsilonAndAttributionScope_success()
            throws Exception {
        when(mMockFlags.getMeasurementEnableEventLevelEpsilonInSource()).thenReturn(true);
        when(mMockFlags.getMeasurementEnableAttributionScope()).thenReturn(true);
        when(mMockFlags.getMeasurementMaxReportStatesPerSourceRegistration())
                .thenReturn(Flags.MEASUREMENT_MAX_REPORT_STATES_PER_SOURCE_REGISTRATION);
        when(mMockFlags.getMeasurementFlexApiMaxInformationGainEvent())
                .thenReturn(Flags.MEASUREMENT_FLEX_API_MAX_INFORMATION_GAIN_EVENT);
        when(mMockFlags.getMeasurementVtcConfigurableMaxEventReportsCount())
                .thenReturn(Flags.DEFAULT_MEASUREMENT_VTC_CONFIGURABLE_MAX_EVENT_REPORTS_COUNT);
        long baseTime = System.currentTimeMillis();
        Source testSource =
                SourceFixture.getMinimalValidSourceBuilder()
                        .setSourceType(Source.SourceType.EVENT)
                        .setEventLevelEpsilon(10D)
                        .setEventTime(baseTime)
                        .setEventReportWindows(
                                "{"
                                        + "\"start_time\": \"0\","
                                        + String.format(
                                                "\"end_times\": [%s, %s]}",
                                                baseTime + TimeUnit.DAYS.toMillis(7),
                                                baseTime + TimeUnit.DAYS.toMillis(30)))
                        .setMaxEventLevelReports(2)
                        .setAttributionScopeLimit(3L)
                        .setMaxEventStates(10L)
                        .setAttributionScopes(List.of("1", "2"))
                        .build();
        assertTrue(testSource.hasValidInformationGain(mMockFlags));
    }

    @Test
    public void testHasValidInformationGain_flexLite_enableEventLevelEpsilon_success()
            throws Exception {
        when(mMockFlags.getMeasurementEnableEventLevelEpsilonInSource()).thenReturn(true);
        when(mMockFlags.getMeasurementMaxReportStatesPerSourceRegistration())
                .thenReturn(Flags.MEASUREMENT_MAX_REPORT_STATES_PER_SOURCE_REGISTRATION);
        when(mMockFlags.getMeasurementFlexApiMaxInformationGainEvent())
                .thenReturn(Flags.MEASUREMENT_FLEX_API_MAX_INFORMATION_GAIN_EVENT);
        long baseTime = System.currentTimeMillis();
        Source testSource =
                SourceFixture.getMinimalValidSourceBuilder()
                        .setSourceType(Source.SourceType.EVENT)
                        .setEventLevelEpsilon(10D)
                        .setEventReportWindows(
                                "{"
                                        + "\"start_time\": \"0\","
                                        + String.format(
                                                "\"end_times\": [%s, %s]}",
                                                baseTime + TimeUnit.DAYS.toMillis(7),
                                                baseTime + TimeUnit.DAYS.toMillis(30)))
                        .build();
        assertTrue(testSource.hasValidInformationGain(mMockFlags));
    }

    @Test
    public void isFlexEventApiValueValid_eventSource_true() throws Exception {
        doReturn(Flags.DEFAULT_MEASUREMENT_VTC_CONFIGURABLE_MAX_EVENT_REPORTS_COUNT)
                .when(mMockFlags)
                .getMeasurementVtcConfigurableMaxEventReportsCount();
        doReturn(true).when(mMockFlags).getMeasurementFlexibleEventReportingApiEnabled();
        doReturn(Flags.MEASUREMENT_FLEX_API_MAX_INFORMATION_GAIN_EVENT)
                .when(mMockFlags)
                .getMeasurementFlexApiMaxInformationGainEvent();
        doReturn(Flags.MEASUREMENT_FLEX_API_MAX_INFORMATION_GAIN_NAVIGATION)
                .when(mMockFlags)
                .getMeasurementFlexApiMaxInformationGainNavigation();
        doReturn(Flags.MEASUREMENT_FLEX_API_MAX_INFORMATION_GAIN_DUAL_DESTINATION_EVENT)
                .when(mMockFlags)
                .getMeasurementFlexApiMaxInformationGainDualDestinationEvent();
        doReturn(Flags.MEASUREMENT_FLEX_API_MAX_INFORMATION_GAIN_DUAL_DESTINATION_NAVIGATION)
                .when(mMockFlags)
                .getMeasurementFlexApiMaxInformationGainDualDestinationNavigation();
        // setup
        String triggerSpecsString =
                "[{\"trigger_data\": [1, 2],"
                        + "\"event_report_windows\": { "
                        + "\"start_time\": \"0\", "
                        + String.format("\"end_times\": [%s]}, ", TimeUnit.DAYS.toMillis(7))
                        + "\"summary_operator\": \"count\", "
                        + "\"summary_buckets\": [1]}]";
        TriggerSpec[] triggerSpecsArray = TriggerSpecsUtil.triggerSpecArrayFrom(triggerSpecsString);
        int maxEventLevelReports = 3;
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
                        .setMaxEventLevelReports(maxEventLevelReports)
                        .setTriggerSpecs(new TriggerSpecs(
                                triggerSpecsArray, maxEventLevelReports, null))
                        .build();
        assertTrue(testSource.isFlexEventApiValueValid(mMockFlags));
    }

    @Test
    public void isFlexEventApiValueValid_eventSource_false() throws Exception {
        doReturn(Flags.DEFAULT_MEASUREMENT_VTC_CONFIGURABLE_MAX_EVENT_REPORTS_COUNT)
                .when(mMockFlags)
                .getMeasurementVtcConfigurableMaxEventReportsCount();
        doReturn(true).when(mMockFlags).getMeasurementFlexibleEventReportingApiEnabled();
        doReturn(Flags.MEASUREMENT_MAX_REPORT_STATES_PER_SOURCE_REGISTRATION)
                .when(mMockFlags)
                .getMeasurementMaxReportStatesPerSourceRegistration();
        doReturn(Flags.MEASUREMENT_FLEX_API_MAX_INFORMATION_GAIN_EVENT)
                .when(mMockFlags)
                .getMeasurementFlexApiMaxInformationGainEvent();
        doReturn(Flags.MEASUREMENT_FLEX_API_MAX_INFORMATION_GAIN_NAVIGATION)
                .when(mMockFlags)
                .getMeasurementFlexApiMaxInformationGainNavigation();
        doReturn(Flags.DEFAULT_MEASUREMENT_PRIVACY_EPSILON)
                .when(mMockFlags)
                .getMeasurementPrivacyEpsilon();
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
                        + "\"summary_operator\": \"count\", "
                        + "\"summary_buckets\": [1, 2, 3]}]";
        TriggerSpec[] triggerSpecsArray = TriggerSpecsUtil.triggerSpecArrayFrom(triggerSpecsString);
        int maxEventLevelReports = 3;
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
                        .setMaxEventLevelReports(3)
                        .setTriggerSpecs(new TriggerSpecs(
                                triggerSpecsArray, maxEventLevelReports, null))
                        .build();
        assertFalse(testSource.isFlexEventApiValueValid(mMockFlags));
    }

    @Test
    public void isFlexEventApiValueValid_navigationSource_true() throws Exception {
        doReturn(Flags.DEFAULT_MEASUREMENT_VTC_CONFIGURABLE_MAX_EVENT_REPORTS_COUNT)
                .when(mMockFlags)
                .getMeasurementVtcConfigurableMaxEventReportsCount();
        doReturn(true).when(mMockFlags).getMeasurementFlexibleEventReportingApiEnabled();
        doReturn(Flags.MEASUREMENT_FLEX_API_MAX_INFORMATION_GAIN_EVENT)
                .when(mMockFlags)
                .getMeasurementFlexApiMaxInformationGainEvent();
        doReturn(Flags.MEASUREMENT_FLEX_API_MAX_INFORMATION_GAIN_NAVIGATION)
                .when(mMockFlags)
                .getMeasurementFlexApiMaxInformationGainNavigation();
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
                        + "\"summary_operator\": \"count\", "
                        + "\"summary_buckets\": [1, 2, 3]}]";
        TriggerSpec[] triggerSpecsArray = TriggerSpecsUtil.triggerSpecArrayFrom(triggerSpecsString);
        int maxEventLevelReports = 3;
        Source testSource =
                SourceFixture.getMinimalValidSourceBuilder()
                        .setEventId(new UnsignedLong(1L))
                        .setAppDestinations(List.of(Uri.parse("android-app://com.destination1")))
                        .setRegistrant(Uri.parse("android-app://com.example"))
                        .setEventTime(new Random().nextLong())
                        .setExpiryTime(8640000010L)
                        .setPriority(100L)
                        .setSourceType(Source.SourceType.NAVIGATION)
                        .setAttributionMode(Source.AttributionMode.TRUTHFULLY)
                        .setDebugKey(new UnsignedLong(47823478789L))
                        .setMaxEventLevelReports(maxEventLevelReports)
                        .setTriggerSpecs(new TriggerSpecs(
                                triggerSpecsArray, maxEventLevelReports, null))
                        .build();
        assertTrue(testSource.isFlexEventApiValueValid(mMockFlags));
    }

    @Test
    public void buildTriggerSpecs_validParams_pass() throws Exception {
        String triggerSpecsString =
                "[{\"trigger_data\": [1, 2, 3],"
                        + "\"event_report_windows\": { "
                        + "\"start_time\": \"0\", "
                        + String.format(
                                "\"end_times\": [%s, %s, %s]}, ",
                                TimeUnit.DAYS.toMillis(2),
                                TimeUnit.DAYS.toMillis(7),
                                TimeUnit.DAYS.toMillis(30))
                        + "\"summary_operator\": \"count\", "
                        + "\"summary_buckets\": [1, 2, 3, 4]}]";
        Source source =
                SourceFixture.getMinimalValidSourceBuilder()
                        .setTriggerSpecsString(triggerSpecsString)
                        .setMaxEventLevelReports(3)
                        .setPrivacyParameters("{\"flip_probability\" :0.0024}")
                        .build();
        source.buildTriggerSpecs();
        // Assertion
        assertEquals(
                source.getTriggerSpecs(),
                new TriggerSpecs(triggerSpecsString, "3", source, "{\"flip_probability\":0.0024}"));
    }

    @Test
    public void buildTriggerSpecs_invalidParamsSyntaxError_throws() throws Exception {
        String triggerSpecsString =
                "[{\"trigger_data\": [1, 2, 3,"
                        + "\"event_report_windows\": { "
                        + "\"start_time\": \"0\", "
                        + String.format(
                                "\"end_times\": [%s, %s, %s]}, ",
                                TimeUnit.DAYS.toMillis(2),
                                TimeUnit.DAYS.toMillis(7),
                                TimeUnit.DAYS.toMillis(30))
                        + "\"summary_operator\": \"count\", "
                        + "\"summary_buckets\": [1, 2, 3, 4]}]";
        Source testSource =
                SourceFixture.getMinimalValidSourceBuilder()
                        .setTriggerSpecsString(triggerSpecsString)
                        .setMaxEventLevelReports(3)
                        .setPrivacyParameters("{\"flip_probability\" :0.0024}")
                        .build();

        // Assertion
        assertThrows(JSONException.class, () -> testSource.buildTriggerSpecs());
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
    public void getOrDefaultEventReportWindowsForFlex() throws Exception {
        JSONObject windowsObj = new JSONObject("{'start_time': '2000000', 'end_times': "
                + "[3600000, 86400000, 172000000]}");
        // Provided Windows
        List<Pair<Long, Long>> eventReportWindows =
                Source.getOrDefaultEventReportWindowsForFlex(
                        windowsObj, Source.SourceType.EVENT, 8640000, mMockFlags);
        assertNotNull(eventReportWindows);
        assertEquals(eventReportWindows.size(), 3);
        assertEquals(new Pair<>(2000000L, 3600000L), eventReportWindows.get(0));
        assertEquals(new Pair<>(3600000L, 86400000L), eventReportWindows.get(1));
        assertEquals(new Pair<>(86400000L, 172000000L), eventReportWindows.get(2));

        // Default Windows - Event
        when(mMockFlags.getMeasurementEventReportsVtcEarlyReportingWindows()).thenReturn("86400");
        when(mMockFlags.getMeasurementEventReportsCtcEarlyReportingWindows())
                .thenReturn("172800,604800");
        eventReportWindows =
                Source.getOrDefaultEventReportWindowsForFlex(
                        null, Source.SourceType.EVENT, TimeUnit.DAYS.toMillis(15), mMockFlags);
        assertNotNull(eventReportWindows);
        assertEquals(2, eventReportWindows.size());
        assertEquals(new Pair<>(0L, 86400000L), eventReportWindows.get(0));
        assertEquals(new Pair<>(86400000L, 1296000000L), eventReportWindows.get(1));

        // Default Windows - Navigation
        eventReportWindows =
                Source.getOrDefaultEventReportWindowsForFlex(
                        null, Source.SourceType.NAVIGATION, TimeUnit.DAYS.toMillis(15), mMockFlags);
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
        when(mMockFlags.getMeasurementVtcConfigurableMaxEventReportsCount()).thenReturn(2);
        // null, Default for EVENT
        assertEquals(
                Integer.valueOf(2),
                Source.getOrDefaultMaxEventLevelReports(Source.SourceType.EVENT, null, mMockFlags));
        // null, Default for NAVIGATION
        assertEquals(
                Integer.valueOf(3),
                Source.getOrDefaultMaxEventLevelReports(
                        Source.SourceType.NAVIGATION, null, mMockFlags));
        // Valid value provided
        assertEquals(
                Integer.valueOf(7),
                Source.getOrDefaultMaxEventLevelReports(
                        Source.SourceType.NAVIGATION, 7, mMockFlags));
    }

    @Test
    public void getEffectiveEventReportWindow() {
        long expiryTime = 7654321L;
        // null eventReportWindow
        Source sourceNullEventReportWindow =
                SourceFixture.getMinimalValidSourceBuilder()
                        .setEventTime(10)
                        .setExpiryTime(expiryTime)
                        .setEventReportWindow(null)
                        .build();
        assertEquals(expiryTime, sourceNullEventReportWindow.getEffectiveEventReportWindow());

        // eventReportWindow Value < eventTime
        Source sourceNewEventReportWindow =
                SourceFixture.getMinimalValidSourceBuilder()
                        .setEventTime(10L)
                        .setEventReportWindow(4L)
                        .build();
        assertEquals(14L, sourceNewEventReportWindow.getEffectiveEventReportWindow());

        // eventReportWindow Value > eventTime
        Source sourceOldEventReportWindow =
                SourceFixture.getMinimalValidSourceBuilder()
                        .setEventTime(10L)
                        .setEventReportWindow(15L)
                        .build();
        assertEquals(15L, sourceOldEventReportWindow.getEffectiveEventReportWindow());
    }

    @Test
    public void testGetConditionalEventLevelEpsilon() {
        when(mMockFlags.getMeasurementEnableEventLevelEpsilonInSource()).thenReturn(false, true);
        when(mMockFlags.getMeasurementPrivacyEpsilon())
                .thenReturn(Flags.DEFAULT_MEASUREMENT_PRIVACY_EPSILON);

        Source withoutEpsilonConfigured = SourceFixture.getValidSource();
        Double epsilonRetrieved =
                withoutEpsilonConfigured.getConditionalEventLevelEpsilon(mMockFlags);
        assertEquals(Flags.DEFAULT_MEASUREMENT_PRIVACY_EPSILON, epsilonRetrieved, 0.0);

        Source withEpsilonConfigured =
                SourceFixture.getValidSourceBuilder().setEventLevelEpsilon(10D).build();
        Double epsilonRetrievedConfigured =
                withEpsilonConfigured.getConditionalEventLevelEpsilon(mMockFlags);
        assertEquals(10D, epsilonRetrievedConfigured, 0.0);
    }

    @Test
    public void testGetInformationGainThreshold_dualDestination_event() {
        Source source =
                SourceFixture.getMinimalValidSourceBuilder()
                        .setAppDestinations(List.of(Uri.parse("android-app://com.destination1")))
                        .setWebDestinations(
                                List.of(WebUtil.validUri("https://web-destination1.test")))
                        .setSourceType(Source.SourceType.EVENT)
                        .build();
        when(mMockFlags.getMeasurementEnableCoarseEventReportDestinations()).thenReturn(false);
        when(mMockFlags.getMeasurementFlexApiMaxInformationGainDualDestinationEvent())
                .thenReturn(Flags.MEASUREMENT_FLEX_API_MAX_INFORMATION_GAIN_DUAL_DESTINATION_EVENT);

        float thresholdRetrieved = source.getInformationGainThreshold(mMockFlags);
        verify(mMockFlags).getMeasurementFlexApiMaxInformationGainDualDestinationEvent();
        assertWithMessage("getInformationGainThreshold() dual destination event")
                .that(thresholdRetrieved)
                .isEqualTo(Flags.MEASUREMENT_FLEX_API_MAX_INFORMATION_GAIN_DUAL_DESTINATION_EVENT);
    }

    @Test
    public void testGetInformationGainThreshold_dualDestination_navigation() {
        Source source =
                SourceFixture.getMinimalValidSourceBuilder()
                        .setAppDestinations(List.of(Uri.parse("android-app://com.destination1")))
                        .setWebDestinations(
                                List.of(WebUtil.validUri("https://web-destination1.test")))
                        .setSourceType(Source.SourceType.NAVIGATION)
                        .build();
        when(mMockFlags.getMeasurementEnableCoarseEventReportDestinations()).thenReturn(false);
        float infoGn = Flags.MEASUREMENT_FLEX_API_MAX_INFORMATION_GAIN_DUAL_DESTINATION_NAVIGATION;
        when(mMockFlags.getMeasurementFlexApiMaxInformationGainDualDestinationNavigation())
                .thenReturn(infoGn);

        float thresholdRetrieved = source.getInformationGainThreshold(mMockFlags);
        verify(mMockFlags).getMeasurementFlexApiMaxInformationGainDualDestinationNavigation();
        assertWithMessage("getInformationGainThreshold() dual destination nav")
                .that(thresholdRetrieved)
                .isEqualTo(infoGn);
    }

    @Test
    public void testGetInformationGainThreshold_singleDestination_event() {
        Source source =
                SourceFixture.getMinimalValidSourceBuilder()
                        .setSourceType(Source.SourceType.EVENT)
                        .build();
        when(mMockFlags.getMeasurementEnableCoarseEventReportDestinations()).thenReturn(true);
        when(mMockFlags.getMeasurementFlexApiMaxInformationGainEvent())
                .thenReturn(Flags.MEASUREMENT_FLEX_API_MAX_INFORMATION_GAIN_EVENT);

        float thresholdRetrieved = source.getInformationGainThreshold(mMockFlags);
        verify(mMockFlags).getMeasurementFlexApiMaxInformationGainEvent();
        assertWithMessage("getInformationGainThreshold() single destination event")
                .that(thresholdRetrieved)
                .isEqualTo(Flags.MEASUREMENT_FLEX_API_MAX_INFORMATION_GAIN_EVENT);
    }

    @Test
    public void testGetInformationGainThreshold_singleDestination_navigation() {
        Source source =
                SourceFixture.getMinimalValidSourceBuilder()
                        .setSourceType(Source.SourceType.NAVIGATION)
                        .build();
        when(mMockFlags.getMeasurementEnableCoarseEventReportDestinations()).thenReturn(true);
        when(mMockFlags.getMeasurementFlexApiMaxInformationGainNavigation())
                .thenReturn(Flags.MEASUREMENT_FLEX_API_MAX_INFORMATION_GAIN_NAVIGATION);

        float thresholdRetrieved = source.getInformationGainThreshold(mMockFlags);
        verify(mMockFlags).getMeasurementFlexApiMaxInformationGainNavigation();
        assertWithMessage("getInformationGainThreshold() single destination nav")
                .that(thresholdRetrieved)
                .isEqualTo(Flags.MEASUREMENT_FLEX_API_MAX_INFORMATION_GAIN_NAVIGATION);
    }
}
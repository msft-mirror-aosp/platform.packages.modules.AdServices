/*
 * Copyright (C) 2023 The Android Open Source Project
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

package com.android.adservices.service.measurement.reporting;

import static com.android.adservices.service.measurement.SourceFixture.ValidSourceParams;
import static com.android.adservices.service.measurement.SourceFixture.getValidSourceBuilder;
import static com.android.adservices.service.measurement.TriggerFixture.ValidTriggerParams;
import static com.android.adservices.service.measurement.TriggerFixture.getValidTriggerBuilder;
import static com.android.adservices.service.stats.AdServicesStatsLog.AD_SERVICES_MEASUREMENT_DEBUG_KEYS__ATTRIBUTION_TYPE__APP_WEB;
import static com.android.adservices.service.stats.AdServicesStatsLog.AD_SERVICES_MEASUREMENT_DEBUG_KEYS__ATTRIBUTION_TYPE__WEB_APP;
import static com.android.adservices.service.stats.AdServicesStatsLog.AD_SERVICES_MEASUREMENT_DEBUG_KEYS__ATTRIBUTION_TYPE__WEB_WEB;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertNull;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.eq;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import android.net.Uri;
import android.util.Pair;

import com.android.adservices.service.Flags;
import com.android.adservices.service.measurement.EventSurfaceType;
import com.android.adservices.service.measurement.Source;
import com.android.adservices.service.measurement.Trigger;
import com.android.adservices.service.measurement.util.UnsignedLong;
import com.android.adservices.service.stats.AdServicesLogger;
import com.android.adservices.service.stats.MsmtDebugKeysMatchStats;

import org.junit.Before;
import org.junit.Test;
import org.junit.runner.RunWith;
import org.mockito.Mock;
import org.mockito.junit.MockitoJUnitRunner;

/** Unit tests for {@link DebugKeyAccessor} */
@RunWith(MockitoJUnitRunner.class)
public class DebugKeyAccessorTest {

    public static final String TRIGGER_ID = "triggerId1";
    public static final long TRIGGER_TIME = 234324L;
    private static final UnsignedLong SOURCE_DEBUG_KEY = new UnsignedLong(111111L);
    private static final UnsignedLong TRIGGER_DEBUG_KEY = new UnsignedLong(222222L);
    private static final long DEFAULT_JOIN_KEY_HASH_LIMIT = 100;

    @Mock private Flags mFlags;
    @Mock private AdServicesLogger mAdServicesLogger;

    private DebugKeyAccessor mDebugKeyAccessor;

    @Before
    public void setup() {
        mDebugKeyAccessor = new DebugKeyAccessor(mFlags, mAdServicesLogger);
        when(mFlags.getMeasurementDebugJoinKeyHashLimit()).thenReturn(DEFAULT_JOIN_KEY_HASH_LIMIT);
        when(mFlags.getMeasurementDebugJoinKeyEnrollmentAllowlist())
                .thenReturn(
                        ValidSourceParams.ENROLLMENT_ID + "," + ValidTriggerParams.ENROLLMENT_ID);
    }

    @Test
    public void getDebugKeys_appAppWithAdIdPermission_debugKeysPresent() {
        Trigger trigger =
                createTrigger(
                        EventSurfaceType.APP, true, false, ValidTriggerParams.REGISTRANT, null);
        Source source =
                createSource(EventSurfaceType.APP, true, false, ValidSourceParams.REGISTRANT, null);
        Pair<UnsignedLong, UnsignedLong> debugKeyPair =
                mDebugKeyAccessor.getDebugKeys(source, trigger);
        assertEquals(SOURCE_DEBUG_KEY, debugKeyPair.first);
        assertEquals(TRIGGER_DEBUG_KEY, debugKeyPair.second);
        verify(mAdServicesLogger, never()).logMeasurementDebugKeysMatch(any());
    }

    @Test
    public void getDebugKeys_appAppNoAdIdPermission_debugKeysAbsent() {
        Trigger trigger =
                createTrigger(
                        EventSurfaceType.APP, false, false, ValidTriggerParams.REGISTRANT, null);
        Source source =
                createSource(
                        EventSurfaceType.APP, false, false, ValidSourceParams.REGISTRANT, null);
        Pair<UnsignedLong, UnsignedLong> debugKeyPair =
                mDebugKeyAccessor.getDebugKeys(source, trigger);
        assertNull(debugKeyPair.first);
        assertNull(debugKeyPair.second);
        verify(mAdServicesLogger, never()).logMeasurementDebugKeysMatch(any());
    }

    @Test
    public void getDebugKeys_appAppNoAdIdPermissionWithJoinKeys_debugKeysAbsent() {
        Trigger trigger =
                createTrigger(
                        EventSurfaceType.APP,
                        false,
                        true,
                        ValidTriggerParams.REGISTRANT,
                        "debug-join-key");
        Source source =
                createSource(
                        EventSurfaceType.APP,
                        false,
                        true,
                        ValidSourceParams.REGISTRANT,
                        "debug-join-key");
        Pair<UnsignedLong, UnsignedLong> debugKeyPair =
                mDebugKeyAccessor.getDebugKeys(source, trigger);
        assertNull(debugKeyPair.first);
        assertNull(debugKeyPair.second);
        verify(mAdServicesLogger, never()).logMeasurementDebugKeysMatch(any());
    }

    @Test
    public void getDebugKeys_appAppWithSourceAdId_sourceDebugKeyPresent() {
        Trigger trigger =
                createTrigger(
                        EventSurfaceType.APP, false, false, ValidTriggerParams.REGISTRANT, null);
        Source source =
                createSource(EventSurfaceType.APP, true, false, ValidSourceParams.REGISTRANT, null);
        Pair<UnsignedLong, UnsignedLong> debugKeyPair =
                mDebugKeyAccessor.getDebugKeys(source, trigger);
        assertEquals(SOURCE_DEBUG_KEY, debugKeyPair.first);
        assertNull(debugKeyPair.second);
        verify(mAdServicesLogger, never()).logMeasurementDebugKeysMatch(any());
    }

    @Test
    public void getDebugKeys_appAppWithTriggerAdId_triggerDebugKeyPresent() {
        Trigger trigger =
                createTrigger(
                        EventSurfaceType.APP, true, false, ValidTriggerParams.REGISTRANT, null);
        Source source =
                createSource(
                        EventSurfaceType.APP, false, false, ValidSourceParams.REGISTRANT, null);
        Pair<UnsignedLong, UnsignedLong> debugKeyPair =
                mDebugKeyAccessor.getDebugKeys(source, trigger);
        assertNull(debugKeyPair.first);
        assertEquals(TRIGGER_DEBUG_KEY, debugKeyPair.second);
        verify(mAdServicesLogger, never()).logMeasurementDebugKeysMatch(any());
    }

    @Test
    public void getDebugKeys_webWebWithSameRegistrant_debugKeysPresent() {
        Trigger trigger =
                createTrigger(
                        EventSurfaceType.WEB, false, true, ValidTriggerParams.REGISTRANT, null);
        Source source =
                createSource(EventSurfaceType.WEB, false, true, ValidSourceParams.REGISTRANT, null);
        Pair<UnsignedLong, UnsignedLong> debugKeyPair =
                mDebugKeyAccessor.getDebugKeys(source, trigger);
        assertEquals(SOURCE_DEBUG_KEY, debugKeyPair.first);
        assertEquals(TRIGGER_DEBUG_KEY, debugKeyPair.second);
        verify(mAdServicesLogger, never()).logMeasurementDebugKeysMatch(any());
    }

    @Test
    public void getDebugKeys_webWebNoJoinKeysAndDifferentRegistrants_debugKeysAbsent() {
        Trigger trigger =
                createTrigger(
                        EventSurfaceType.WEB,
                        false,
                        false,
                        Uri.parse("https://com.registrant1"),
                        null);
        Source source =
                createSource(
                        EventSurfaceType.WEB,
                        false,
                        false,
                        Uri.parse("https://com.registrant2"),
                        null);
        Pair<UnsignedLong, UnsignedLong> debugKeyPair =
                mDebugKeyAccessor.getDebugKeys(source, trigger);
        assertNull(debugKeyPair.first);
        assertNull(debugKeyPair.second);
        verify(mAdServicesLogger, never()).logMeasurementDebugKeysMatch(any());
    }

    @Test
    public void getDebugKeys_webWebDiffJoinKeysSameRegFalseArDebug_debugKeysAbsent() {
        Trigger trigger =
                createTrigger(
                        EventSurfaceType.WEB,
                        false,
                        false,
                        ValidTriggerParams.REGISTRANT,
                        "debug-join-key1");
        Source source =
                createSource(
                        EventSurfaceType.WEB,
                        false,
                        false,
                        ValidSourceParams.REGISTRANT,
                        "debug-join-key2");
        Pair<UnsignedLong, UnsignedLong> debugKeyPair =
                mDebugKeyAccessor.getDebugKeys(source, trigger);
        assertNull(debugKeyPair.first);
        assertNull(debugKeyPair.second);
        verify(mAdServicesLogger, never()).logMeasurementDebugKeysMatch(any());
    }

    @Test
    public void getDebugKeys_webWebSameJoinKeysAndDifferentRegistrants_debugKeysPresent() {
        Trigger trigger =
                createTrigger(
                        EventSurfaceType.WEB,
                        false,
                        false,
                        Uri.parse("https://com.registrant1"),
                        "debug-join-key");
        Source source =
                createSource(
                        EventSurfaceType.WEB,
                        false,
                        false,
                        Uri.parse("https://com.registrant2"),
                        "debug-join-key");
        Pair<UnsignedLong, UnsignedLong> debugKeyPair =
                mDebugKeyAccessor.getDebugKeys(source, trigger);
        assertEquals(SOURCE_DEBUG_KEY, debugKeyPair.first);
        assertEquals(TRIGGER_DEBUG_KEY, debugKeyPair.second);
        MsmtDebugKeysMatchStats stats =
                MsmtDebugKeysMatchStats.builder()
                        .setAdTechEnrollmentId(ValidTriggerParams.ENROLLMENT_ID)
                        .setAttributionType(
                                AD_SERVICES_MEASUREMENT_DEBUG_KEYS__ATTRIBUTION_TYPE__WEB_WEB)
                        .setMatched(true)
                        .setDebugJoinKeyHashedValue(54L)
                        .setDebugJoinKeyHashLimit(DEFAULT_JOIN_KEY_HASH_LIMIT)
                        .build();
        verify(mAdServicesLogger).logMeasurementDebugKeysMatch(eq(stats));
    }

    @Test
    public void getDebugKeys_webWebOnlySourceJoinKeyAndDifferentRegistrants_debugKeysPresent() {
        Trigger trigger =
                createTrigger(
                        EventSurfaceType.WEB,
                        false,
                        false,
                        Uri.parse("https://com.registrant1"),
                        null);
        Source source =
                createSource(
                        EventSurfaceType.WEB,
                        false,
                        false,
                        Uri.parse("https://com.registrant2"),
                        "debug-join-key");
        Pair<UnsignedLong, UnsignedLong> debugKeyPair =
                mDebugKeyAccessor.getDebugKeys(source, trigger);
        assertNull(debugKeyPair.first);
        assertNull(debugKeyPair.second);
        verify(mAdServicesLogger, never()).logMeasurementDebugKeysMatch(any());
    }

    @Test
    public void getDebugKeys_webWebDiffJoinKeysAndDifferentRegistrants_debugKeysAbsent() {
        Trigger trigger =
                createTrigger(
                        EventSurfaceType.WEB,
                        true,
                        true,
                        Uri.parse("https://com.registrant1"),
                        "debug-join-key1");
        Source source =
                createSource(
                        EventSurfaceType.WEB,
                        true,
                        true,
                        Uri.parse("https://com.registrant2"),
                        "debug-join-key2");
        Pair<UnsignedLong, UnsignedLong> debugKeyPair =
                mDebugKeyAccessor.getDebugKeys(source, trigger);
        assertNull(debugKeyPair.first);
        assertNull(debugKeyPair.second);
        MsmtDebugKeysMatchStats stats =
                MsmtDebugKeysMatchStats.builder()
                        .setAdTechEnrollmentId(ValidTriggerParams.ENROLLMENT_ID)
                        .setAttributionType(
                                AD_SERVICES_MEASUREMENT_DEBUG_KEYS__ATTRIBUTION_TYPE__WEB_WEB)
                        .setMatched(false)
                        .setDebugJoinKeyHashedValue(0L)
                        .setDebugJoinKeyHashLimit(DEFAULT_JOIN_KEY_HASH_LIMIT)
                        .build();
        verify(mAdServicesLogger).logMeasurementDebugKeysMatch(eq(stats));
    }

    @Test
    public void getDebugKeys_webWebSameRegistrantWithArDebugOnSource_sourceDebugKeysPresent() {
        Trigger trigger =
                createTrigger(
                        EventSurfaceType.WEB,
                        false,
                        false,
                        ValidTriggerParams.REGISTRANT,
                        "debug-join-key");
        Source source =
                createSource(
                        EventSurfaceType.WEB,
                        false,
                        true,
                        ValidSourceParams.REGISTRANT,
                        "debug-join-key");
        Pair<UnsignedLong, UnsignedLong> debugKeyPair =
                mDebugKeyAccessor.getDebugKeys(source, trigger);
        assertEquals(SOURCE_DEBUG_KEY, debugKeyPair.first);
        assertNull(debugKeyPair.second);
        verify(mAdServicesLogger, never()).logMeasurementDebugKeysMatch(any());
    }

    @Test
    public void getDebugKeys_appWebNoJoinKeys_debugKeysAbsent() {
        Source source =
                createSource(EventSurfaceType.APP, true, true, ValidSourceParams.REGISTRANT, null);
        Trigger trigger =
                createTrigger(
                        EventSurfaceType.WEB, true, true, ValidTriggerParams.REGISTRANT, null);
        Pair<UnsignedLong, UnsignedLong> debugKeyPair =
                mDebugKeyAccessor.getDebugKeys(source, trigger);
        assertNull(debugKeyPair.first);
        assertNull(debugKeyPair.second);
        verify(mAdServicesLogger, never()).logMeasurementDebugKeysMatch(any());
    }

    @Test
    public void getDebugKeys_appWebMatchingJoinKeys_debugKeysPresent() {
        Source source =
                createSource(
                        EventSurfaceType.APP,
                        false,
                        true,
                        ValidSourceParams.REGISTRANT,
                        "debug-join-key");
        Trigger trigger =
                createTrigger(
                        EventSurfaceType.WEB,
                        false,
                        true,
                        ValidTriggerParams.REGISTRANT,
                        "debug-join-key");
        Pair<UnsignedLong, UnsignedLong> debugKeyPair =
                mDebugKeyAccessor.getDebugKeys(source, trigger);
        assertEquals(SOURCE_DEBUG_KEY, debugKeyPair.first);
        assertEquals(TRIGGER_DEBUG_KEY, debugKeyPair.second);
        MsmtDebugKeysMatchStats stats =
                MsmtDebugKeysMatchStats.builder()
                        .setAdTechEnrollmentId(ValidTriggerParams.ENROLLMENT_ID)
                        .setAttributionType(
                                AD_SERVICES_MEASUREMENT_DEBUG_KEYS__ATTRIBUTION_TYPE__APP_WEB)
                        .setMatched(true)
                        .setDebugJoinKeyHashedValue(54L)
                        .setDebugJoinKeyHashLimit(DEFAULT_JOIN_KEY_HASH_LIMIT)
                        .build();
        verify(mAdServicesLogger).logMeasurementDebugKeysMatch(eq(stats));
    }

    @Test
    public void getDebugKeys_appWebOnlyTriggerJoinKeyProvided_debugKeysAbsent() {
        Source source =
                createSource(EventSurfaceType.APP, false, true, ValidSourceParams.REGISTRANT, null);
        Trigger trigger =
                createTrigger(
                        EventSurfaceType.WEB,
                        false,
                        true,
                        ValidTriggerParams.REGISTRANT,
                        "debug-join-key");
        Pair<UnsignedLong, UnsignedLong> debugKeyPair =
                mDebugKeyAccessor.getDebugKeys(source, trigger);
        assertNull(debugKeyPair.first);
        assertNull(debugKeyPair.second);
        verify(mAdServicesLogger, never()).logMeasurementDebugKeysMatch(any());
    }

    @Test
    public void getDebugKeys_appWebNotMatchingJoinKeys_debugKeysAbsent() {
        Source source =
                createSource(
                        EventSurfaceType.APP,
                        true,
                        true,
                        ValidSourceParams.REGISTRANT,
                        "debug-join-key1");
        Trigger trigger =
                createTrigger(
                        EventSurfaceType.WEB,
                        true,
                        true,
                        ValidTriggerParams.REGISTRANT,
                        "debug-join-key2");
        Pair<UnsignedLong, UnsignedLong> debugKeyPair =
                mDebugKeyAccessor.getDebugKeys(source, trigger);
        assertNull(debugKeyPair.first);
        assertNull(debugKeyPair.second);
        MsmtDebugKeysMatchStats stats =
                MsmtDebugKeysMatchStats.builder()
                        .setAdTechEnrollmentId(ValidTriggerParams.ENROLLMENT_ID)
                        .setAttributionType(
                                AD_SERVICES_MEASUREMENT_DEBUG_KEYS__ATTRIBUTION_TYPE__APP_WEB)
                        .setMatched(false)
                        .setDebugJoinKeyHashedValue(0L)
                        .setDebugJoinKeyHashLimit(DEFAULT_JOIN_KEY_HASH_LIMIT)
                        .build();
        verify(mAdServicesLogger).logMeasurementDebugKeysMatch(eq(stats));
    }

    @Test
    public void getDebugKeys_webAppNoJoinKeys_debugKeysAbsent() {
        Source source =
                createSource(EventSurfaceType.WEB, true, true, ValidSourceParams.REGISTRANT, null);
        Trigger trigger =
                createTrigger(
                        EventSurfaceType.APP, true, true, ValidTriggerParams.REGISTRANT, null);
        Pair<UnsignedLong, UnsignedLong> debugKeyPair =
                mDebugKeyAccessor.getDebugKeys(source, trigger);
        assertNull(debugKeyPair.first);
        assertNull(debugKeyPair.second);
        verify(mAdServicesLogger, never()).logMeasurementDebugKeysMatch(any());
    }

    @Test
    public void getDebugKeys_webAppMatchingJoinKeys_debugKeysPresent() {
        Source source =
                createSource(
                        EventSurfaceType.WEB,
                        false,
                        false,
                        ValidSourceParams.REGISTRANT,
                        "debug-join-key");
        Trigger trigger =
                createTrigger(
                        EventSurfaceType.APP,
                        false,
                        false,
                        ValidTriggerParams.REGISTRANT,
                        "debug-join-key");
        Pair<UnsignedLong, UnsignedLong> debugKeyPair =
                mDebugKeyAccessor.getDebugKeys(source, trigger);
        assertEquals(SOURCE_DEBUG_KEY, debugKeyPair.first);
        assertEquals(TRIGGER_DEBUG_KEY, debugKeyPair.second);
        MsmtDebugKeysMatchStats stats =
                MsmtDebugKeysMatchStats.builder()
                        .setAdTechEnrollmentId(ValidTriggerParams.ENROLLMENT_ID)
                        .setAttributionType(
                                AD_SERVICES_MEASUREMENT_DEBUG_KEYS__ATTRIBUTION_TYPE__WEB_APP)
                        .setMatched(true)
                        .setDebugJoinKeyHashedValue(54L)
                        .setDebugJoinKeyHashLimit(DEFAULT_JOIN_KEY_HASH_LIMIT)
                        .build();
        verify(mAdServicesLogger).logMeasurementDebugKeysMatch(eq(stats));
    }

    @Test
    public void getDebugKeys_webAppOnlySourceJoinKeyProvided_debugKeysAbsent() {
        Source source =
                createSource(
                        EventSurfaceType.WEB,
                        false,
                        false,
                        ValidSourceParams.REGISTRANT,
                        "debug-join-key");
        Trigger trigger =
                createTrigger(
                        EventSurfaceType.APP, false, false, ValidTriggerParams.REGISTRANT, null);
        Pair<UnsignedLong, UnsignedLong> debugKeyPair =
                mDebugKeyAccessor.getDebugKeys(source, trigger);
        assertNull(debugKeyPair.first);
        assertNull(debugKeyPair.second);
        verify(mAdServicesLogger, never()).logMeasurementDebugKeysMatch(any());
    }

    @Test
    public void getDebugKeys_webAppMatchingJoinKeysNotAllowListed_debugKeysAbsent() {
        when(mFlags.getMeasurementDebugJoinKeyEnrollmentAllowlist()).thenReturn("");
        Source source =
                createSource(
                        EventSurfaceType.WEB,
                        true,
                        true,
                        ValidSourceParams.REGISTRANT,
                        "debug-join-key");
        Trigger trigger =
                createTrigger(
                        EventSurfaceType.APP,
                        true,
                        true,
                        ValidTriggerParams.REGISTRANT,
                        "debug-join-key");
        Pair<UnsignedLong, UnsignedLong> debugKeyPair =
                mDebugKeyAccessor.getDebugKeys(source, trigger);
        assertNull(debugKeyPair.first);
        assertNull(debugKeyPair.second);
        verify(mAdServicesLogger, never()).logMeasurementDebugKeysMatch(any());
    }

    @Test
    public void getDebugKeys_webAppNotMatchingJoinKeys_debugKeysAbsent() {
        Source source =
                createSource(
                        EventSurfaceType.WEB,
                        true,
                        true,
                        ValidSourceParams.REGISTRANT,
                        "debug-join-key1");
        Trigger trigger =
                createTrigger(
                        EventSurfaceType.APP,
                        true,
                        true,
                        ValidTriggerParams.REGISTRANT,
                        "debug-join-key2");
        Pair<UnsignedLong, UnsignedLong> debugKeyPair =
                mDebugKeyAccessor.getDebugKeys(source, trigger);
        assertNull(debugKeyPair.first);
        assertNull(debugKeyPair.second);
        MsmtDebugKeysMatchStats stats =
                MsmtDebugKeysMatchStats.builder()
                        .setAdTechEnrollmentId(ValidTriggerParams.ENROLLMENT_ID)
                        .setAttributionType(
                                AD_SERVICES_MEASUREMENT_DEBUG_KEYS__ATTRIBUTION_TYPE__WEB_APP)
                        .setMatched(false)
                        .setDebugJoinKeyHashedValue(0L)
                        .setDebugJoinKeyHashLimit(DEFAULT_JOIN_KEY_HASH_LIMIT)
                        .build();
        verify(mAdServicesLogger).logMeasurementDebugKeysMatch(eq(stats));
    }

    @Test
    public void getDebugKeys_webWebNotAllowListedDiffRegMatchingJoinKeys_debugKeysAbsent() {
        when(mFlags.getMeasurementDebugJoinKeyEnrollmentAllowlist())
                .thenReturn("some_random_enrollment1,some_random_enrollment2");
        Source source =
                createSource(
                        EventSurfaceType.WEB,
                        true,
                        true,
                        Uri.parse("https://com.registrant1"),
                        "debug-join-key");
        Trigger trigger =
                createTrigger(
                        EventSurfaceType.WEB,
                        true,
                        true,
                        Uri.parse("https://com.registrant2"),
                        "debug-join-key");
        Pair<UnsignedLong, UnsignedLong> debugKeyPair =
                mDebugKeyAccessor.getDebugKeys(source, trigger);
        assertNull(debugKeyPair.first);
        assertNull(debugKeyPair.second);
        verify(mAdServicesLogger, never()).logMeasurementDebugKeysMatch(any());
    }

    @Test
    public void getDebugKeys_appWebMatchingJoinKeysNotAllowListed_debugKeysAbsent() {
        when(mFlags.getMeasurementDebugJoinKeyEnrollmentAllowlist())
                .thenReturn("some_random_enrollment1,some_random_enrollment2");
        Source source =
                createSource(
                        EventSurfaceType.APP,
                        true,
                        true,
                        ValidSourceParams.REGISTRANT,
                        "debug-join-key");
        Trigger trigger =
                createTrigger(
                        EventSurfaceType.WEB,
                        true,
                        true,
                        ValidTriggerParams.REGISTRANT,
                        "debug-join-key");
        Pair<UnsignedLong, UnsignedLong> debugKeyPair =
                mDebugKeyAccessor.getDebugKeys(source, trigger);
        assertNull(debugKeyPair.first);
        assertNull(debugKeyPair.second);
        verify(mAdServicesLogger, never()).logMeasurementDebugKeysMatch(any());
    }

    private static Trigger createTrigger(
            int destinationType,
            boolean adIdPermission,
            boolean arDebugPermission,
            Uri registrant,
            String debugJoinKey) {
        return getValidTriggerBuilder()
                .setId(TRIGGER_ID)
                .setArDebugPermission(arDebugPermission)
                .setAdIdPermission(adIdPermission)
                .setRegistrant(registrant)
                .setDestinationType(destinationType)
                .setDebugKey(TRIGGER_DEBUG_KEY)
                .setDebugJoinKey(debugJoinKey)
                .build();
    }

    private static Source createSource(
            int publisherType,
            boolean adIdPermission,
            boolean arDebugPermission,
            Uri registrant,
            String debugJoinKey) {
        return getValidSourceBuilder()
                .setArDebugPermission(arDebugPermission)
                .setAdIdPermission(adIdPermission)
                .setDebugKey(SOURCE_DEBUG_KEY)
                .setPublisherType(publisherType)
                .setRegistrant(registrant)
                .setDebugJoinKey(debugJoinKey)
                .build();
    }
}

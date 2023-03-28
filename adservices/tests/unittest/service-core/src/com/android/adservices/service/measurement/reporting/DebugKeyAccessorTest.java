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
    public void getDebugKeys_appToAppWithAdIdPermission_debugKeysPresent() {
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
    public void getDebugKeys_appToAppNoAdIdPermission_debugKeysAbsent() {
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
    public void getDebugKeys_appToAppNoAdIdPermissionWithJoinKeys_debugKeysAbsent() {
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
    public void getDebugKeys_appToAppWithSourceAdId_sourceDebugKeyPresent() {
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
    public void getDebugKeys_appToAppWithTriggerAdId_triggerDebugKeyPresent() {
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
    public void getDebugKeys_webToWebWithSameRegistrant_debugKeysPresent() {
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
    public void getDebugKeys_webToWebNoJoinKeysAndDifferentRegistrants_debugKeysAbsent() {
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
    public void getDebugKeys_webToWebDiffJoinKeysSameRegFalseArDebug_debugKeysAbsent() {
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
    public void getDebugKeys_webToWebSameJoinKeysAndDifferentRegistrants_debugKeysPresent() {
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
    public void getDebugKeys_webToWebOnlySourceJoinKeyAndDifferentRegistrants_debugKeysAbsent() {
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
    public void getDebugKeys_webToWebDiffJoinKeysAndDifferentRegistrants_debugKeysAbsent() {
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
    public void getDebugKeys_webToWebSameRegistrantWithArDebugOnSource_sourceDebugKeysPresent() {
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
    public void getDebugKeys_appToWebNoJoinKeys_debugKeysAbsent() {
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
    public void getDebugKeys_appToWebJoinKeysMatch_debugKeysPresent() {
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
    public void getDebugKeys_appToWebOnlyTriggerJoinKeyProvided_debugKeysAbsent() {
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
    public void getDebugKeys_appToWebJoinKeysMismatch_debugKeysAbsent() {
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
    public void getDebugKeys_webToAppNoJoinKeys_debugKeysAbsent() {
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
    public void getDebugKeys_webToAppJoinKeysMatch_debugKeysPresent() {
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
    public void getDebugKeys_webToAppOnlySourceJoinKeyProvided_debugKeysAbsent() {
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
    public void getDebugKeys_webToAppJoinKeysMatchNotAllowListed_debugKeysAbsent() {
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
    public void getDebugKeys_webToAppJoinKeysMismatch_debugKeysAbsent() {
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
    public void getDebugKeys_webToWebNotAllowListedDiffRegJoinKeysMatch_debugKeysAbsent() {
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
    public void getDebugKeys_appToWebJoinKeysMatchNotAllowListed_debugKeysAbsent() {
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

    @Test
    public void getDebugKeysForVerbose_noSourceTriggerAdIdPermission_triggerDebugKeyPresent() {
        Trigger trigger =
                createTrigger(
                        EventSurfaceType.APP, true, false, ValidTriggerParams.REGISTRANT, null);
        Pair<UnsignedLong, UnsignedLong> debugKeyPair =
                mDebugKeyAccessor.getDebugKeysForVerboseTriggerDebugReport(null, trigger);
        assertNull(debugKeyPair.first);
        assertEquals(TRIGGER_DEBUG_KEY, debugKeyPair.second);
        verify(mAdServicesLogger, never()).logMeasurementDebugKeysMatch(any());
    }

    @Test
    public void getDebugKeysForVerbose_noSourceTriggerNoAdIdPermission_triggerDebugKeyAbsent() {
        Trigger trigger =
                createTrigger(
                        EventSurfaceType.APP, false, false, ValidTriggerParams.REGISTRANT, null);
        Pair<UnsignedLong, UnsignedLong> debugKeyPair =
                mDebugKeyAccessor.getDebugKeysForVerboseTriggerDebugReport(null, trigger);
        assertNull(debugKeyPair.first);
        assertNull(debugKeyPair.second);
        verify(mAdServicesLogger, never()).logMeasurementDebugKeysMatch(any());
    }

    @Test
    public void getDebugKeysForVerbose_noSourceTriggerArdebugPermission_triggerDebugKeyPresent() {
        Trigger trigger =
                createTrigger(
                        EventSurfaceType.WEB, false, true, ValidTriggerParams.REGISTRANT, null);
        Pair<UnsignedLong, UnsignedLong> debugKeyPair =
                mDebugKeyAccessor.getDebugKeysForVerboseTriggerDebugReport(null, trigger);
        assertNull(debugKeyPair.first);
        assertEquals(TRIGGER_DEBUG_KEY, debugKeyPair.second);
        verify(mAdServicesLogger, never()).logMeasurementDebugKeysMatch(any());
    }

    @Test
    public void getDebugKeysForVerbose_noSourceTriggerNoArdebugPermission_triggerDebugKeyAbsent() {
        Trigger trigger =
                createTrigger(
                        EventSurfaceType.WEB, false, false, ValidTriggerParams.REGISTRANT, null);
        Pair<UnsignedLong, UnsignedLong> debugKeyPair =
                mDebugKeyAccessor.getDebugKeysForVerboseTriggerDebugReport(null, trigger);
        assertNull(debugKeyPair.first);
        assertNull(debugKeyPair.second);
        verify(mAdServicesLogger, never()).logMeasurementDebugKeysMatch(any());
    }

    @Test
    public void getDebugKeysForVerbose_appToAppWithAdIdPermission_debugKeysPresent() {
        Trigger trigger =
                createTrigger(
                        EventSurfaceType.APP, true, false, ValidTriggerParams.REGISTRANT, null);
        Source source =
                createSource(EventSurfaceType.APP, true, false, ValidSourceParams.REGISTRANT, null);
        Pair<UnsignedLong, UnsignedLong> debugKeyPair =
                mDebugKeyAccessor.getDebugKeysForVerboseTriggerDebugReport(source, trigger);
        assertEquals(SOURCE_DEBUG_KEY, debugKeyPair.first);
        assertEquals(TRIGGER_DEBUG_KEY, debugKeyPair.second);
        verify(mAdServicesLogger, never()).logMeasurementDebugKeysMatch(any());
    }

    @Test
    public void getDebugKeysForVerbose_appToAppBothNoAdIdPermission_debugKeysAbsent() {
        Trigger trigger =
                createTrigger(
                        EventSurfaceType.APP, false, false, ValidTriggerParams.REGISTRANT, null);
        Source source =
                createSource(
                        EventSurfaceType.APP, false, false, ValidSourceParams.REGISTRANT, null);
        Pair<UnsignedLong, UnsignedLong> debugKeyPair =
                mDebugKeyAccessor.getDebugKeysForVerboseTriggerDebugReport(source, trigger);
        assertNull(debugKeyPair.first);
        assertNull(debugKeyPair.second);
        verify(mAdServicesLogger, never()).logMeasurementDebugKeysMatch(any());
    }

    @Test
    public void getDebugKeysForVerbose_appToAppNoTriggerAdId_debugKeysAbsent() {
        Trigger trigger =
                createTrigger(
                        EventSurfaceType.APP, false, false, ValidTriggerParams.REGISTRANT, null);
        Source source =
                createSource(EventSurfaceType.APP, true, false, ValidSourceParams.REGISTRANT, null);
        Pair<UnsignedLong, UnsignedLong> debugKeyPair =
                mDebugKeyAccessor.getDebugKeysForVerboseTriggerDebugReport(source, trigger);
        assertNull(debugKeyPair.first);
        assertNull(debugKeyPair.second);
        verify(mAdServicesLogger, never()).logMeasurementDebugKeysMatch(any());
    }

    @Test
    public void getDebugKeysForVerbose_appToAppNoSourceAdId_sourceDebugKeyAbsent() {
        Trigger trigger =
                createTrigger(
                        EventSurfaceType.APP, true, false, ValidTriggerParams.REGISTRANT, null);
        Source source =
                createSource(
                        EventSurfaceType.APP, false, false, ValidSourceParams.REGISTRANT, null);
        Pair<UnsignedLong, UnsignedLong> debugKeyPair =
                mDebugKeyAccessor.getDebugKeysForVerboseTriggerDebugReport(source, trigger);
        assertNull(debugKeyPair.first);
        assertEquals(TRIGGER_DEBUG_KEY, debugKeyPair.second);
        verify(mAdServicesLogger, never()).logMeasurementDebugKeysMatch(any());
    }

    @Test
    public void getDebugKeysForVerbose_appToAppBothNoAdIdWithJoinKeys_debugKeysAbsent() {
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
                mDebugKeyAccessor.getDebugKeysForVerboseTriggerDebugReport(source, trigger);
        assertNull(debugKeyPair.first);
        assertNull(debugKeyPair.second);
        verify(mAdServicesLogger, never()).logMeasurementDebugKeysMatch(any());
    }

    @Test
    public void getDebugKeysForVerbose_webToWebWithSameRegistrant_debugKeysPresent() {
        Trigger trigger =
                createTrigger(
                        EventSurfaceType.WEB, false, true, ValidTriggerParams.REGISTRANT, null);
        Source source =
                createSource(EventSurfaceType.WEB, false, true, ValidSourceParams.REGISTRANT, null);
        Pair<UnsignedLong, UnsignedLong> debugKeyPair =
                mDebugKeyAccessor.getDebugKeysForVerboseTriggerDebugReport(source, trigger);
        assertEquals(SOURCE_DEBUG_KEY, debugKeyPair.first);
        assertEquals(TRIGGER_DEBUG_KEY, debugKeyPair.second);
        verify(mAdServicesLogger, never()).logMeasurementDebugKeysMatch(any());
    }

    @Test
    public void getDebugKeysForVerbose_webToWebTriggerNoArDebugPermission_debugKeysAbsent() {
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
                        false,
                        ValidSourceParams.REGISTRANT,
                        "debug-join-key");
        Pair<UnsignedLong, UnsignedLong> debugKeyPair =
                mDebugKeyAccessor.getDebugKeysForVerboseTriggerDebugReport(source, trigger);
        assertNull(debugKeyPair.first);
        assertNull(debugKeyPair.second);
        verify(mAdServicesLogger, never()).logMeasurementDebugKeysMatch(any());
    }

    @Test
    public void
            getDebugKeysForVerbose_webToWebSameJoinKeysAndDifferentRegistrants_debugKeysPresent() {
        Trigger trigger =
                createTrigger(
                        EventSurfaceType.WEB,
                        false,
                        true,
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
                mDebugKeyAccessor.getDebugKeysForVerboseTriggerDebugReport(source, trigger);
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
    public void getDebugKeysForVerbose_webToWebNoJoinKeyDiffRegistrants_sourceDebugKeyAbsent() {
        Trigger trigger =
                createTrigger(
                        EventSurfaceType.WEB,
                        false,
                        true,
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
                mDebugKeyAccessor.getDebugKeysForVerboseTriggerDebugReport(source, trigger);
        assertNull(debugKeyPair.first);
        assertEquals(TRIGGER_DEBUG_KEY, debugKeyPair.second);
        verify(mAdServicesLogger, never()).logMeasurementDebugKeysMatch(any());
    }

    @Test
    public void
            getDebugKeysForVerbose_webToWebDiffJoinKeysDifferentRegistrants_sourceDebugKeyAbsent() {
        Trigger trigger =
                createTrigger(
                        EventSurfaceType.WEB,
                        false,
                        true,
                        Uri.parse("https://com.registrant1"),
                        "debug-join-key1");
        Source source =
                createSource(
                        EventSurfaceType.WEB,
                        false,
                        true,
                        Uri.parse("https://com.registrant2"),
                        "debug-join-key2");
        Pair<UnsignedLong, UnsignedLong> debugKeyPair =
                mDebugKeyAccessor.getDebugKeysForVerboseTriggerDebugReport(source, trigger);
        assertNull(debugKeyPair.first);
        assertEquals(TRIGGER_DEBUG_KEY, debugKeyPair.second);
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
    public void getDebugKeysForVerbose_webToWebSameRegistrantWithArDebug_debugKeysPresent() {
        Trigger trigger =
                createTrigger(
                        EventSurfaceType.WEB,
                        false,
                        true,
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
                mDebugKeyAccessor.getDebugKeysForVerboseTriggerDebugReport(source, trigger);
        assertEquals(SOURCE_DEBUG_KEY, debugKeyPair.first);
        assertEquals(TRIGGER_DEBUG_KEY, debugKeyPair.second);
        verify(mAdServicesLogger, never()).logMeasurementDebugKeysMatch(any());
    }

    @Test
    public void
            getDebugKeysForVerbose_webToWebNotAllowListDiffRegJoinKeysMatch_sourceDebugKeyAbsent() {
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
                mDebugKeyAccessor.getDebugKeysForVerboseTriggerDebugReport(source, trigger);
        assertNull(debugKeyPair.first);
        assertEquals(TRIGGER_DEBUG_KEY, debugKeyPair.second);
        verify(mAdServicesLogger, never()).logMeasurementDebugKeysMatch(any());
    }

    @Test
    public void getDebugKeysForVerbose_appToWebTriggerNoArDebugPermission_debugKeysAbsent() {
        Source source =
                createSource(EventSurfaceType.APP, true, true, ValidSourceParams.REGISTRANT, null);
        Trigger trigger =
                createTrigger(
                        EventSurfaceType.WEB, true, false, ValidTriggerParams.REGISTRANT, null);
        Pair<UnsignedLong, UnsignedLong> debugKeyPair =
                mDebugKeyAccessor.getDebugKeysForVerboseTriggerDebugReport(source, trigger);
        assertNull(debugKeyPair.first);
        assertNull(debugKeyPair.second);
        verify(mAdServicesLogger, never()).logMeasurementDebugKeysMatch(any());
    }

    @Test
    public void getDebugKeysForVerbose_appToWebNoJoinKeys_sourceDebugKeysAbsent() {
        Source source =
                createSource(EventSurfaceType.APP, true, true, ValidSourceParams.REGISTRANT, null);
        Trigger trigger =
                createTrigger(
                        EventSurfaceType.WEB, true, true, ValidTriggerParams.REGISTRANT, null);
        Pair<UnsignedLong, UnsignedLong> debugKeyPair =
                mDebugKeyAccessor.getDebugKeysForVerboseTriggerDebugReport(source, trigger);
        assertNull(debugKeyPair.first);
        assertEquals(TRIGGER_DEBUG_KEY, debugKeyPair.second);
        verify(mAdServicesLogger, never()).logMeasurementDebugKeysMatch(any());
    }

    @Test
    public void getDebugKeysForVerbose_appToWebJoinKeysMatch_debugKeysPresent() {
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
                mDebugKeyAccessor.getDebugKeysForVerboseTriggerDebugReport(source, trigger);
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
    public void getDebugKeysForVerbose_appToWebNoSourceJoinKey_sourceDebugKeyAbsent() {
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
                mDebugKeyAccessor.getDebugKeysForVerboseTriggerDebugReport(source, trigger);
        assertNull(debugKeyPair.first);
        assertEquals(TRIGGER_DEBUG_KEY, debugKeyPair.second);
        verify(mAdServicesLogger, never()).logMeasurementDebugKeysMatch(any());
    }

    @Test
    public void getDebugKeysForVerbose_appToWebJoinKeysMismatch_sourceDebugKeyAbsent() {
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
                mDebugKeyAccessor.getDebugKeysForVerboseTriggerDebugReport(source, trigger);
        assertNull(debugKeyPair.first);
        assertEquals(TRIGGER_DEBUG_KEY, debugKeyPair.second);
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
    public void getDebugKeysForVerbose_appToWebJoinKeysMatchNotAllowListed_sourceDebugKeyAbsent() {
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
                mDebugKeyAccessor.getDebugKeysForVerboseTriggerDebugReport(source, trigger);
        assertNull(debugKeyPair.first);
        assertEquals(TRIGGER_DEBUG_KEY, debugKeyPair.second);
        verify(mAdServicesLogger, never()).logMeasurementDebugKeysMatch(any());
    }

    @Test
    public void getDebugKeysForVerbose_webToAppTriggerNoAdid_debugKeysAbsent() {
        Source source =
                createSource(EventSurfaceType.WEB, true, true, ValidSourceParams.REGISTRANT, null);
        Trigger trigger =
                createTrigger(
                        EventSurfaceType.APP, false, true, ValidTriggerParams.REGISTRANT, null);
        Pair<UnsignedLong, UnsignedLong> debugKeyPair =
                mDebugKeyAccessor.getDebugKeysForVerboseTriggerDebugReport(source, trigger);
        assertNull(debugKeyPair.first);
        assertNull(debugKeyPair.second);
        verify(mAdServicesLogger, never()).logMeasurementDebugKeysMatch(any());
    }

    @Test
    public void getDebugKeysForVerbose_webToAppNoJoinKeys_sourceDebugKeyAbsent() {
        Source source =
                createSource(EventSurfaceType.WEB, true, true, ValidSourceParams.REGISTRANT, null);
        Trigger trigger =
                createTrigger(
                        EventSurfaceType.APP, true, true, ValidTriggerParams.REGISTRANT, null);
        Pair<UnsignedLong, UnsignedLong> debugKeyPair =
                mDebugKeyAccessor.getDebugKeysForVerboseTriggerDebugReport(source, trigger);
        assertNull(debugKeyPair.first);
        assertEquals(TRIGGER_DEBUG_KEY, debugKeyPair.second);
        verify(mAdServicesLogger, never()).logMeasurementDebugKeysMatch(any());
    }

    @Test
    public void getDebugKeysForVerbose_webToAppJoinKeysMatch_debugKeysPresent() {
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
                        true,
                        true,
                        ValidTriggerParams.REGISTRANT,
                        "debug-join-key");
        Pair<UnsignedLong, UnsignedLong> debugKeyPair =
                mDebugKeyAccessor.getDebugKeysForVerboseTriggerDebugReport(source, trigger);
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
    public void getDebugKeysForVerbose_webToAppJoinKeysMatchNotAllowListed_sourceDebugKeyAbsent() {
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
                mDebugKeyAccessor.getDebugKeysForVerboseTriggerDebugReport(source, trigger);
        assertNull(debugKeyPair.first);
        assertEquals(TRIGGER_DEBUG_KEY, debugKeyPair.second);
        verify(mAdServicesLogger, never()).logMeasurementDebugKeysMatch(any());
    }

    @Test
    public void getDebugKeysForVerbose_webToAppJoinKeysMismatch_sourceDebugKeyAbsent() {
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
                mDebugKeyAccessor.getDebugKeysForVerboseTriggerDebugReport(source, trigger);
        assertNull(debugKeyPair.first);
        assertEquals(TRIGGER_DEBUG_KEY, debugKeyPair.second);
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

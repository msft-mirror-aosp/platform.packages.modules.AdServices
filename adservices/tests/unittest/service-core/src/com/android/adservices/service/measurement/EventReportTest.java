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

import static com.android.adservices.service.measurement.PrivacyParams.EVENT_NOISE_PROBABILITY;
import static com.android.adservices.service.measurement.PrivacyParams.INSTALL_ATTR_EVENT_NOISE_PROBABILITY;
import static com.android.adservices.service.measurement.PrivacyParams.INSTALL_ATTR_NAVIGATION_EARLY_REPORTING_WINDOW_MILLISECONDS;
import static com.android.adservices.service.measurement.PrivacyParams.INSTALL_ATTR_NAVIGATION_NOISE_PROBABILITY;
import static com.android.adservices.service.measurement.PrivacyParams.NAVIGATION_EARLY_REPORTING_WINDOW_MILLISECONDS;
import static com.android.adservices.service.measurement.PrivacyParams.NAVIGATION_NOISE_PROBABILITY;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertNotEquals;
import static org.junit.Assert.assertNull;
import static org.mockito.Mockito.spy;
import static org.mockito.Mockito.when;

import android.net.Uri;

import androidx.test.filters.SmallTest;

import com.android.adservices.service.measurement.util.UnsignedLong;

import org.json.JSONException;
import org.junit.Test;

import java.util.List;
import java.util.Set;
import java.util.UUID;
import java.util.concurrent.TimeUnit;

/** Unit tests for {@link EventReport} */
@SmallTest
public final class EventReportTest {

    private static final long ONE_HOUR_IN_MILLIS = TimeUnit.HOURS.toMillis(1);
    private static final double DOUBLE_MAX_DELTA = 0.0000001D;
    private static final long TRIGGER_PRIORITY = 345678L;
    private static final UnsignedLong TRIGGER_DEDUP_KEY = new UnsignedLong(2345678L);
    private static final UnsignedLong TRIGGER_DATA = new UnsignedLong(4L);
    private static final UnsignedLong SOURCE_DEBUG_KEY = new UnsignedLong(237865L);
    private static final UnsignedLong TRIGGER_DEBUG_KEY = new UnsignedLong(928762L);
    private static final String SOURCE_ID = UUID.randomUUID().toString();
    private static final String TRIGGER_ID = UUID.randomUUID().toString();
    private static final Uri APP_DESTINATION = Uri.parse("android-app://example1.app");
    private static final Uri WEB_DESTINATION = Uri.parse("https://example1.test");
    private static final String EVENT_TRIGGERS =
            "[\n"
                    + "{\n"
                    + "  \"trigger_data\": \""
                    + TRIGGER_DATA
                    + "\",\n"
                    + "  \"priority\": \""
                    + TRIGGER_PRIORITY
                    + "\",\n"
                    + "  \"deduplication_key\": \""
                    + TRIGGER_DEDUP_KEY
                    + "\",\n"
                    + "  \"filters\": [{\n"
                    + "    \"source_type\": [\"navigation\"],\n"
                    + "    \"key_1\": [\"value_1\"] \n"
                    + "   }]\n"
                    + "}"
                    + "]\n";

    @Test
    public void creation_success() {
        EventReport eventReport = createExample();
        assertEquals("1", eventReport.getId());
        assertEquals(new UnsignedLong(21L), eventReport.getSourceEventId());
        assertEquals("enrollment-id", eventReport.getEnrollmentId());
        assertEquals("https://bar.test",
                eventReport.getAttributionDestinations().get(0).toString());
        assertEquals(1000L, eventReport.getTriggerTime());
        assertEquals(new UnsignedLong(8L), eventReport.getTriggerData());
        assertEquals(2L, eventReport.getTriggerPriority());
        assertEquals(new UnsignedLong(3L), eventReport.getTriggerDedupKey());
        assertEquals(2000L, eventReport.getReportTime());
        assertEquals(EventReport.Status.PENDING, eventReport.getStatus());
        assertEquals(EventReport.DebugReportStatus.PENDING, eventReport.getDebugReportStatus());
        assertEquals(Source.SourceType.NAVIGATION, eventReport.getSourceType());
        assertEquals(SOURCE_DEBUG_KEY, eventReport.getSourceDebugKey());
        assertEquals(TRIGGER_DEBUG_KEY, eventReport.getTriggerDebugKey());
        assertEquals(SOURCE_ID, eventReport.getSourceId());
        assertEquals(TRIGGER_ID, eventReport.getTriggerId());
    }

    @Test
    public void creationSuccessSingleSourceDebugKey() {
        EventReport eventReport = createExampleSingleSourceDebugKey();
        assertEquals("1", eventReport.getId());
        assertEquals(new UnsignedLong(21L), eventReport.getSourceEventId());
        assertEquals("enrollment-id", eventReport.getEnrollmentId());
        assertEquals("https://bar.test",
                eventReport.getAttributionDestinations().get(0).toString());
        assertEquals(1000L, eventReport.getTriggerTime());
        assertEquals(new UnsignedLong(8L), eventReport.getTriggerData());
        assertEquals(2L, eventReport.getTriggerPriority());
        assertEquals(new UnsignedLong(3L), eventReport.getTriggerDedupKey());
        assertEquals(2000L, eventReport.getReportTime());
        assertEquals(EventReport.Status.PENDING, eventReport.getStatus());
        assertEquals(EventReport.DebugReportStatus.PENDING, eventReport.getDebugReportStatus());
        assertEquals(Source.SourceType.NAVIGATION, eventReport.getSourceType());
        assertEquals(SOURCE_DEBUG_KEY, eventReport.getSourceDebugKey());
        assertNull(eventReport.getTriggerDebugKey());
        assertEquals(SOURCE_ID, eventReport.getSourceId());
        assertEquals(TRIGGER_ID, eventReport.getTriggerId());
    }

    @Test
    public void creationSuccessSingleTriggerDebugKey() {
        EventReport eventReport = createExampleSingleTriggerDebugKey();
        assertEquals("1", eventReport.getId());
        assertEquals(new UnsignedLong(21L), eventReport.getSourceEventId());
        assertEquals("enrollment-id", eventReport.getEnrollmentId());
        assertEquals("https://bar.test",
                eventReport.getAttributionDestinations().get(0).toString());
        assertEquals(1000L, eventReport.getTriggerTime());
        assertEquals(new UnsignedLong(8L), eventReport.getTriggerData());
        assertEquals(2L, eventReport.getTriggerPriority());
        assertEquals(new UnsignedLong(3L), eventReport.getTriggerDedupKey());
        assertEquals(2000L, eventReport.getReportTime());
        assertEquals(EventReport.Status.PENDING, eventReport.getStatus());
        assertEquals(EventReport.DebugReportStatus.PENDING, eventReport.getDebugReportStatus());
        assertEquals(Source.SourceType.NAVIGATION, eventReport.getSourceType());
        assertNull(eventReport.getSourceDebugKey());
        assertEquals(TRIGGER_DEBUG_KEY, eventReport.getTriggerDebugKey());
        assertEquals(SOURCE_ID, eventReport.getSourceId());
        assertEquals(TRIGGER_ID, eventReport.getTriggerId());
    }

    @Test
    public void defaults_success() {
        EventReport eventReport = new EventReport.Builder().build();
        assertNull(eventReport.getId());
        assertNull(eventReport.getSourceEventId());
        assertNull(eventReport.getEnrollmentId());
        assertNull(eventReport.getAttributionDestinations());
        assertEquals(0L, eventReport.getTriggerTime());
        assertNull(eventReport.getTriggerData());
        assertEquals(0L, eventReport.getTriggerPriority());
        assertNull(eventReport.getTriggerDedupKey());
        assertEquals(0L, eventReport.getReportTime());
        assertEquals(EventReport.Status.PENDING, eventReport.getStatus());
        assertEquals(EventReport.DebugReportStatus.NONE, eventReport.getDebugReportStatus());
        assertNull(eventReport.getSourceType());
        assertNull(eventReport.getSourceDebugKey());
        assertNull(eventReport.getTriggerDebugKey());
        assertNull(eventReport.getSourceId());
        assertNull(eventReport.getTriggerId());
    }

    @Test
    public void populateFromSourceAndTrigger_eventSourceAppDestWithoutInstallAttribution()
            throws JSONException {
        long baseTime = System.currentTimeMillis();
        Source source =
                createSourceForTest(
                        baseTime, Source.SourceType.EVENT, false, APP_DESTINATION, null);
        Trigger trigger = createTriggerForTest(
                baseTime + TimeUnit.SECONDS.toMillis(10), APP_DESTINATION, EventSurfaceType.APP);

        List<EventTrigger> eventTriggers = trigger.parseEventTriggers();
        EventReport report =
                new EventReport.Builder()
                        .populateFromSourceAndTrigger(source, trigger, eventTriggers.get(0))
                        .build();

        assertEquals(TRIGGER_PRIORITY, report.getTriggerPriority());
        assertEquals(TRIGGER_DEDUP_KEY, report.getTriggerDedupKey());
        // Truncated data 4 % 2 = 0
        assertEquals(new UnsignedLong(0L), report.getTriggerData());
        assertEquals(trigger.getTriggerTime(), report.getTriggerTime());
        assertEquals(source.getEventId(), report.getSourceEventId());
        assertEquals(source.getEnrollmentId(), report.getEnrollmentId());
        assertEquals(trigger.getAttributionDestination(),
                report.getAttributionDestinations().get(0));
        assertEquals(source.getEventReportWindow() + ONE_HOUR_IN_MILLIS, report.getReportTime());
        assertEquals(source.getSourceType(), report.getSourceType());
        assertEquals(EVENT_NOISE_PROBABILITY, report.getRandomizedTriggerRate(), DOUBLE_MAX_DELTA);
        assertEquals(SOURCE_ID, report.getSourceId());
        assertEquals(TRIGGER_ID, report.getTriggerId());
    }

    @Test
    public void testPopulateFromSourceAndTrigger_shouldTruncateTriggerDataWith64thBit()
            throws JSONException {
        long baseTime = System.currentTimeMillis();
        Source source =
                createSourceForTest(
                        baseTime, Source.SourceType.NAVIGATION, false, APP_DESTINATION, null);
        Trigger trigger = createTriggerForTest(
                baseTime + TimeUnit.SECONDS.toMillis(10), APP_DESTINATION, EventSurfaceType.APP);

        List<EventTrigger> eventTriggers = trigger.parseEventTriggers();
        EventTrigger eventTrigger = spy(eventTriggers.get(0));
        when(eventTrigger.getTriggerData()).thenReturn(new UnsignedLong(-50003L));
        EventReport report =
                new EventReport.Builder()
                        .populateFromSourceAndTrigger(source, trigger, eventTrigger)
                        .build();

        assertEquals(TRIGGER_PRIORITY, report.getTriggerPriority());
        assertEquals(TRIGGER_DEDUP_KEY, report.getTriggerDedupKey());
        assertEquals(trigger.getTriggerTime(), report.getTriggerTime());
        // uint64 18446744073709501613 = long -50003
        // Truncated data 18446744073709501613 % 8 = 5
        assertEquals(new UnsignedLong(5L), report.getTriggerData());
        assertEquals(source.getEventId(), report.getSourceEventId());
        assertEquals(source.getEnrollmentId(), report.getEnrollmentId());
        assertEquals(APP_DESTINATION, report.getAttributionDestinations().get(0));
        assertEquals(
                source.getEventTime()
                        + NAVIGATION_EARLY_REPORTING_WINDOW_MILLISECONDS[0]
                        + ONE_HOUR_IN_MILLIS,
                report.getReportTime());
        assertEquals(Source.SourceType.NAVIGATION, report.getSourceType());
        assertEquals(
                NAVIGATION_NOISE_PROBABILITY, report.getRandomizedTriggerRate(), DOUBLE_MAX_DELTA);
        assertEquals(SOURCE_ID, report.getSourceId());
        assertEquals(TRIGGER_ID, report.getTriggerId());
    }

    @Test
    public void populateFromSourceAndTrigger_eventSourceWebDestWithoutInstallAttribution()
            throws JSONException {
        long baseTime = System.currentTimeMillis();
        Source source =
                createSourceForTest(
                        baseTime, Source.SourceType.EVENT, false, null, WEB_DESTINATION);
        Trigger trigger = createTriggerForTest(
                baseTime + TimeUnit.SECONDS.toMillis(10), WEB_DESTINATION, EventSurfaceType.WEB);

        List<EventTrigger> eventTriggers = trigger.parseEventTriggers();
        EventReport report =
                new EventReport.Builder()
                        .populateFromSourceAndTrigger(source, trigger, eventTriggers.get(0))
                        .build();

        assertEquals(TRIGGER_PRIORITY, report.getTriggerPriority());
        assertEquals(TRIGGER_DEDUP_KEY, report.getTriggerDedupKey());
        // Truncated data 4 % 2 = 0
        assertEquals(new UnsignedLong(0L), report.getTriggerData());
        assertEquals(trigger.getTriggerTime(), report.getTriggerTime());
        assertEquals(source.getEventId(), report.getSourceEventId());
        assertEquals(source.getEnrollmentId(), report.getEnrollmentId());
        assertEquals(trigger.getAttributionDestination(),
                report.getAttributionDestinations().get(0));
        assertEquals(source.getEventReportWindow() + ONE_HOUR_IN_MILLIS, report.getReportTime());
        assertEquals(Source.SourceType.EVENT, report.getSourceType());
        assertEquals(EVENT_NOISE_PROBABILITY, report.getRandomizedTriggerRate(), DOUBLE_MAX_DELTA);
        assertEquals(SOURCE_ID, report.getSourceId());
        assertEquals(TRIGGER_ID, report.getTriggerId());
    }

    @Test
    public void populateFromSourceAndTrigger_eventSourceAppDestWithInstallAttribution()
            throws JSONException {
        long baseTime = System.currentTimeMillis();
        Source source =
                createSourceForTest(baseTime, Source.SourceType.EVENT, true, APP_DESTINATION, null);
        Trigger trigger = createTriggerForTest(
                baseTime + TimeUnit.SECONDS.toMillis(10), APP_DESTINATION, EventSurfaceType.APP);

        List<EventTrigger> eventTriggers = trigger.parseEventTriggers();
        EventReport report =
                new EventReport.Builder()
                        .populateFromSourceAndTrigger(source, trigger, eventTriggers.get(0))
                        .build();

        assertEquals(TRIGGER_PRIORITY, report.getTriggerPriority());
        assertEquals(TRIGGER_DEDUP_KEY, report.getTriggerDedupKey());
        assertEquals(trigger.getTriggerTime(), report.getTriggerTime());
        assertEquals(source.getEventId(), report.getSourceEventId());
        assertEquals(source.getEnrollmentId(), report.getEnrollmentId());
        assertEquals(trigger.getAttributionDestination(),
                report.getAttributionDestinations().get(0));
        assertEquals(source.getEventReportWindow() + ONE_HOUR_IN_MILLIS, report.getReportTime());
        assertEquals(Source.SourceType.EVENT, report.getSourceType());
        assertEquals(
                INSTALL_ATTR_EVENT_NOISE_PROBABILITY,
                report.getRandomizedTriggerRate(),
                DOUBLE_MAX_DELTA);
        assertEquals(SOURCE_ID, report.getSourceId());
        assertEquals(TRIGGER_ID, report.getTriggerId());
    }

    @Test
    public void populateFromSourceAndTrigger_eventSourceWebDestWithInstallAttribution()
            throws JSONException {
        long baseTime = System.currentTimeMillis();
        Source source =
                createSourceForTest(baseTime, Source.SourceType.EVENT, true, null, WEB_DESTINATION);
        Trigger trigger = createTriggerForTest(
                baseTime + TimeUnit.SECONDS.toMillis(10), WEB_DESTINATION, EventSurfaceType.WEB);

        List<EventTrigger> eventTriggers = trigger.parseEventTriggers();
        EventReport report =
                new EventReport.Builder()
                        .populateFromSourceAndTrigger(source, trigger, eventTriggers.get(0))
                        .build();

        assertEquals(TRIGGER_PRIORITY, report.getTriggerPriority());
        assertEquals(TRIGGER_DEDUP_KEY, report.getTriggerDedupKey());
        assertEquals(trigger.getTriggerTime(), report.getTriggerTime());
        assertEquals(source.getEventId(), report.getSourceEventId());
        assertEquals(source.getEnrollmentId(), report.getEnrollmentId());
        assertEquals(trigger.getAttributionDestination(),
                report.getAttributionDestinations().get(0));
        assertEquals(source.getEventReportWindow() + ONE_HOUR_IN_MILLIS, report.getReportTime());
        assertEquals(Source.SourceType.EVENT, report.getSourceType());
        assertEquals(EVENT_NOISE_PROBABILITY, report.getRandomizedTriggerRate(), DOUBLE_MAX_DELTA);
        assertEquals(SOURCE_ID, report.getSourceId());
        assertEquals(TRIGGER_ID, report.getTriggerId());
    }

    @Test
    public void populateFromSourceAndTrigger_navigationSourceAppDestWithoutInstall()
            throws JSONException {
        long baseTime = System.currentTimeMillis();
        Source source =
                createSourceForTest(
                        baseTime, Source.SourceType.NAVIGATION, false, APP_DESTINATION, null);
        Trigger trigger = createTriggerForTest(
                baseTime + TimeUnit.SECONDS.toMillis(10), APP_DESTINATION, EventSurfaceType.APP);

        List<EventTrigger> eventTriggers = trigger.parseEventTriggers();
        EventReport report =
                new EventReport.Builder()
                        .populateFromSourceAndTrigger(source, trigger, eventTriggers.get(0))
                        .build();

        assertEquals(TRIGGER_PRIORITY, report.getTriggerPriority());
        assertEquals(TRIGGER_DEDUP_KEY, report.getTriggerDedupKey());
        assertEquals(trigger.getTriggerTime(), report.getTriggerTime());
        assertEquals(source.getEventId(), report.getSourceEventId());
        assertEquals(source.getEnrollmentId(), report.getEnrollmentId());
        assertEquals(APP_DESTINATION, report.getAttributionDestinations().get(0));
        assertEquals(
                source.getEventTime()
                        + NAVIGATION_EARLY_REPORTING_WINDOW_MILLISECONDS[0]
                        + ONE_HOUR_IN_MILLIS,
                report.getReportTime());
        assertEquals(Source.SourceType.NAVIGATION, report.getSourceType());
        assertEquals(
                NAVIGATION_NOISE_PROBABILITY, report.getRandomizedTriggerRate(), DOUBLE_MAX_DELTA);
        assertEquals(SOURCE_ID, report.getSourceId());
        assertEquals(TRIGGER_ID, report.getTriggerId());
    }

    @Test
    public void populateFromSourceAndTrigger_navigationSourceWebDestWithoutInstall()
            throws JSONException {
        long baseTime = System.currentTimeMillis();
        Source source =
                createSourceForTest(
                        baseTime, Source.SourceType.NAVIGATION, false, null, WEB_DESTINATION);
        Trigger trigger = createTriggerForTest(
                baseTime + TimeUnit.SECONDS.toMillis(10), WEB_DESTINATION, EventSurfaceType.WEB);

        List<EventTrigger> eventTriggers = trigger.parseEventTriggers();
        EventReport report =
                new EventReport.Builder()
                        .populateFromSourceAndTrigger(source, trigger, eventTriggers.get(0))
                        .build();

        assertEquals(TRIGGER_PRIORITY, report.getTriggerPriority());
        assertEquals(TRIGGER_DEDUP_KEY, report.getTriggerDedupKey());
        assertEquals(trigger.getTriggerTime(), report.getTriggerTime());
        assertEquals(source.getEventId(), report.getSourceEventId());
        assertEquals(source.getEnrollmentId(), report.getEnrollmentId());
        assertEquals(trigger.getAttributionDestination(),
                report.getAttributionDestinations().get(0));
        assertEquals(
                source.getReportingTime(trigger.getTriggerTime(), EventSurfaceType.WEB),
                report.getReportTime());
        assertEquals(source.getSourceType(), report.getSourceType());
        assertEquals(
                NAVIGATION_NOISE_PROBABILITY, report.getRandomizedTriggerRate(), DOUBLE_MAX_DELTA);
        assertEquals(SOURCE_ID, report.getSourceId());
        assertEquals(TRIGGER_ID, report.getTriggerId());
    }

    @Test
    public void testPopulateFromSourceAndTrigger_navigationSourceAppDestWithInstallAttribution()
            throws JSONException {
        long baseTime = System.currentTimeMillis();
        Source source =
                createSourceForTest(
                        baseTime, Source.SourceType.NAVIGATION, true, APP_DESTINATION, null);
        Trigger trigger = createTriggerForTest(
                baseTime + TimeUnit.SECONDS.toMillis(10), APP_DESTINATION, EventSurfaceType.APP);

        List<EventTrigger> eventTriggers = trigger.parseEventTriggers();
        EventReport report =
                new EventReport.Builder()
                        .populateFromSourceAndTrigger(source, trigger, eventTriggers.get(0))
                        .build();

        assertEquals(TRIGGER_PRIORITY, report.getTriggerPriority());
        assertEquals(TRIGGER_DEDUP_KEY, report.getTriggerDedupKey());
        assertEquals(new UnsignedLong(4L), report.getTriggerData());
        assertEquals(trigger.getTriggerTime(), report.getTriggerTime());
        assertEquals(source.getEventId(), report.getSourceEventId());
        assertEquals(source.getEnrollmentId(), report.getEnrollmentId());
        assertEquals(trigger.getAttributionDestination(),
                report.getAttributionDestinations().get(0));
        // One hour after install attributed navigation type window
        assertEquals(
                source.getEventTime()
                        + INSTALL_ATTR_NAVIGATION_EARLY_REPORTING_WINDOW_MILLISECONDS[0]
                        + ONE_HOUR_IN_MILLIS,
                report.getReportTime());
        assertEquals(Source.SourceType.NAVIGATION, report.getSourceType());
        assertEquals(
                INSTALL_ATTR_NAVIGATION_NOISE_PROBABILITY,
                report.getRandomizedTriggerRate(),
                DOUBLE_MAX_DELTA);
        assertEquals(SOURCE_ID, report.getSourceId());
        assertEquals(TRIGGER_ID, report.getTriggerId());
    }

    @Test
    public void testPopulateFromSourceAndTrigger_navigationSourceWebDestWithInstallAttribution()
            throws JSONException {
        long baseTime = System.currentTimeMillis();
        Source source =
                createSourceForTest(
                        baseTime, Source.SourceType.NAVIGATION, true, null, WEB_DESTINATION);
        Trigger trigger = createTriggerForTest(
                baseTime + TimeUnit.SECONDS.toMillis(10), WEB_DESTINATION, EventSurfaceType.WEB);

        List<EventTrigger> eventTriggers = trigger.parseEventTriggers();
        EventReport report =
                new EventReport.Builder()
                        .populateFromSourceAndTrigger(source, trigger, eventTriggers.get(0))
                        .build();

        assertEquals(TRIGGER_PRIORITY, report.getTriggerPriority());
        assertEquals(TRIGGER_DEDUP_KEY, report.getTriggerDedupKey());
        assertEquals(new UnsignedLong(4L), report.getTriggerData());
        assertEquals(trigger.getTriggerTime(), report.getTriggerTime());
        assertEquals(source.getEventId(), report.getSourceEventId());
        assertEquals(source.getEnrollmentId(), report.getEnrollmentId());
        assertEquals(trigger.getAttributionDestination(),
                report.getAttributionDestinations().get(0));
        // One hour after regular navigation type window (without install attribution consideration)
        assertEquals(
                source.getEventTime()
                        + NAVIGATION_EARLY_REPORTING_WINDOW_MILLISECONDS[0]
                        + ONE_HOUR_IN_MILLIS,
                report.getReportTime());
        assertEquals(source.getSourceType(), report.getSourceType());
        assertEquals(
                NAVIGATION_NOISE_PROBABILITY, report.getRandomizedTriggerRate(), DOUBLE_MAX_DELTA);
        assertEquals(SOURCE_ID, report.getSourceId());
        assertEquals(TRIGGER_ID, report.getTriggerId());
    }

    @Test
    public void testHashCode_equals() {
        final EventReport eventReport1 = createExample();
        final EventReport eventReport2 = createExample();
        final Set<EventReport> eventReportSet1 = Set.of(eventReport1);
        final Set<EventReport> eventReportSet2 = Set.of(eventReport2);
        assertEquals(eventReport1.hashCode(), eventReport2.hashCode());
        assertEquals(eventReport1, eventReport2);
        assertEquals(eventReportSet1, eventReportSet2);
    }

    @Test
    public void testHashCode_notEquals() {
        final EventReport eventReport1 = createExample();
        final EventReport eventReport2 =
                new EventReport.Builder()
                        .setId("1")
                        .setSourceEventId(new UnsignedLong(22L))
                        .setEnrollmentId("another-enrollment-id")
                        .setAttributionDestinations(List.of(Uri.parse("https://bar.test")))
                        .setTriggerTime(1000L)
                        .setTriggerData(new UnsignedLong(8L))
                        .setTriggerPriority(2L)
                        .setTriggerDedupKey(new UnsignedLong(3L))
                        .setReportTime(2000L)
                        .setStatus(EventReport.Status.PENDING)
                        .setStatus(EventReport.DebugReportStatus.PENDING)
                        .setSourceType(Source.SourceType.NAVIGATION)
                        .build();
        final Set<EventReport> eventReportSet1 = Set.of(eventReport1);
        final Set<EventReport> eventReportSet2 = Set.of(eventReport2);
        assertNotEquals(eventReport1.hashCode(), eventReport2.hashCode());
        assertNotEquals(eventReport1, eventReport2);
        assertNotEquals(eventReportSet1, eventReportSet2);
    }

    private Source createSourceForTest(
            long eventTime,
            Source.SourceType sourceType,
            boolean isInstallAttributable,
            Uri appDestination,
            Uri webDestination) {
        return SourceFixture.getValidSourceBuilder()
                .setId(SOURCE_ID)
                .setEventId(new UnsignedLong(10L))
                .setSourceType(sourceType)
                .setInstallCooldownWindow(isInstallAttributable ? 100 : 0)
                .setEventTime(eventTime)
                .setEnrollmentId("enrollment-id")
                .setAppDestinations(getNullableUriList(appDestination))
                .setWebDestinations(getNullableUriList(webDestination))
                .setEventReportWindow(eventTime + TimeUnit.DAYS.toMillis(10))
                .build();
    }

    private Trigger createTriggerForTest(
            long eventTime, Uri destination, @EventSurfaceType int destinationType) {
        return TriggerFixture.getValidTriggerBuilder()
                .setId(TRIGGER_ID)
                .setTriggerTime(eventTime)
                .setEventTriggers(EVENT_TRIGGERS)
                .setEnrollmentId("enrollment-id")
                .setAttributionDestination(destination)
                .setDestinationType(destinationType)
                .build();
    }

    private EventReport createExample() {
        return new EventReport.Builder()
                .setId("1")
                .setSourceEventId(new UnsignedLong(21L))
                .setEnrollmentId("enrollment-id")
                .setAttributionDestinations(List.of(Uri.parse("https://bar.test")))
                .setTriggerTime(1000L)
                .setTriggerData(new UnsignedLong(8L))
                .setTriggerPriority(2L)
                .setTriggerDedupKey(new UnsignedLong(3L))
                .setReportTime(2000L)
                .setStatus(EventReport.Status.PENDING)
                .setDebugReportStatus(EventReport.DebugReportStatus.PENDING)
                .setSourceType(Source.SourceType.NAVIGATION)
                .setSourceDebugKey(SOURCE_DEBUG_KEY)
                .setTriggerDebugKey(TRIGGER_DEBUG_KEY)
                .setSourceId(SOURCE_ID)
                .setTriggerId(TRIGGER_ID)
                .build();
    }

    private EventReport createExampleSingleTriggerDebugKey() {
        return new EventReport.Builder()
                .setId("1")
                .setSourceEventId(new UnsignedLong(21L))
                .setEnrollmentId("enrollment-id")
                .setAttributionDestinations(List.of(Uri.parse("https://bar.test")))
                .setTriggerTime(1000L)
                .setTriggerData(new UnsignedLong(8L))
                .setTriggerPriority(2L)
                .setTriggerDedupKey(new UnsignedLong(3L))
                .setReportTime(2000L)
                .setStatus(EventReport.Status.PENDING)
                .setDebugReportStatus(EventReport.DebugReportStatus.PENDING)
                .setSourceType(Source.SourceType.NAVIGATION)
                .setTriggerDebugKey(TRIGGER_DEBUG_KEY)
                .setSourceId(SOURCE_ID)
                .setTriggerId(TRIGGER_ID)
                .build();
    }

    private EventReport createExampleSingleSourceDebugKey() {
        return new EventReport.Builder()
                .setId("1")
                .setSourceEventId(new UnsignedLong(21L))
                .setEnrollmentId("enrollment-id")
                .setAttributionDestinations(List.of(Uri.parse("https://bar.test")))
                .setTriggerTime(1000L)
                .setTriggerData(new UnsignedLong(8L))
                .setTriggerPriority(2L)
                .setTriggerDedupKey(new UnsignedLong(3L))
                .setReportTime(2000L)
                .setStatus(EventReport.Status.PENDING)
                .setDebugReportStatus(EventReport.DebugReportStatus.PENDING)
                .setSourceType(Source.SourceType.NAVIGATION)
                .setSourceDebugKey(SOURCE_DEBUG_KEY)
                .setSourceId(SOURCE_ID)
                .setTriggerId(TRIGGER_ID)
                .build();
    }

    private static List<Uri> getNullableUriList(Uri uri) {
        return uri == null ? null : List.of(uri);
    }
}

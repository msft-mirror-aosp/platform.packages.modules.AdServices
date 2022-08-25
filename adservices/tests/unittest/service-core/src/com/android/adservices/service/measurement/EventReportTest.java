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

import android.net.Uri;

import androidx.test.filters.SmallTest;

import org.json.JSONException;
import org.junit.Test;

import java.util.List;
import java.util.Set;
import java.util.concurrent.TimeUnit;

/** Unit tests for {@link EventReport} */
@SmallTest
public final class EventReportTest {

    private static final long ONE_HOUR_IN_MILLIS = TimeUnit.HOURS.toMillis(1);
    private static final double DOUBLE_MAX_DELTA = 0.0000001D;
    private static final long TRIGGER_PRIORITY = 345678L;
    private static final Long TRIGGER_DEDUP_KEY = 2345678L;
    private static final Long TRIGGER_DATA = 4L;
    private static final Uri APP_DESTINATION = Uri.parse("android-app://example1.app");
    private static final Uri WEB_DESTINATION = Uri.parse("https://example1.com");
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
                    + "  \"filters\": {\n"
                    + "    \"source_type\": [\"navigation\"],\n"
                    + "    \"key_1\": [\"value_1\"] \n"
                    + "   }\n"
                    + "}"
                    + "]\n";

    @Test
    public void creation_success() {
        EventReport eventReport = createExample();
        assertEquals("1", eventReport.getId());
        assertEquals(21, eventReport.getSourceId());
        assertEquals("https://foo.com", eventReport.getAdTechDomain().toString());
        assertEquals("enrollment-id", eventReport.getEnrollmentId());
        assertEquals("https://bar.com", eventReport.getAttributionDestination().toString());
        assertEquals(1000L, eventReport.getTriggerTime());
        assertEquals(8L, eventReport.getTriggerData());
        assertEquals(2L, eventReport.getTriggerPriority());
        assertEquals(Long.valueOf(3), eventReport.getTriggerDedupKey());
        assertEquals(2000L, eventReport.getReportTime());
        assertEquals(EventReport.Status.PENDING, eventReport.getStatus());
        assertEquals(Source.SourceType.NAVIGATION, eventReport.getSourceType());
    }

    @Test
    public void defaults_success() {
        EventReport eventReport = new EventReport.Builder().build();
        assertNull(eventReport.getId());
        assertEquals(0L, eventReport.getSourceId());
        assertNull(eventReport.getAdTechDomain());
        assertNull(eventReport.getEnrollmentId());
        assertNull(eventReport.getAttributionDestination());
        assertEquals(0L, eventReport.getTriggerTime());
        assertEquals(0L, eventReport.getTriggerData());
        assertEquals(0L, eventReport.getTriggerPriority());
        assertNull(eventReport.getTriggerDedupKey());
        assertEquals(0L, eventReport.getReportTime());
        assertEquals(EventReport.Status.PENDING, eventReport.getStatus());
        assertNull(eventReport.getSourceType());
    }

    @Test
    public void populateFromSourceAndTrigger_eventSourceAppDestWithoutInstallAttribution()
            throws JSONException {
        long baseTime = System.currentTimeMillis();
        Source source =
                createSourceForTest(
                        baseTime, Source.SourceType.EVENT, false, APP_DESTINATION, null);
        Trigger trigger =
                createTriggerForTest(baseTime + TimeUnit.SECONDS.toMillis(10), APP_DESTINATION);

        List<EventTrigger> eventTriggers = trigger.parseEventTriggers();
        EventReport report =
                new EventReport.Builder()
                        .populateFromSourceAndTrigger(source, trigger, eventTriggers.get(0))
                        .build();

        assertEquals(TRIGGER_PRIORITY, report.getTriggerPriority());
        assertEquals(TRIGGER_DEDUP_KEY, report.getTriggerDedupKey());
        // Truncated data 4 % 2 = 0
        assertEquals(0, report.getTriggerData());
        assertEquals(trigger.getTriggerTime(), report.getTriggerTime());
        assertEquals(source.getEventId(), report.getSourceId());
        assertEquals(source.getAdTechDomain(), report.getAdTechDomain());
        assertEquals(source.getEnrollmentId(), report.getEnrollmentId());
        assertEquals(trigger.getAttributionDestination(), report.getAttributionDestination());
        assertEquals(source.getExpiryTime() + ONE_HOUR_IN_MILLIS, report.getReportTime());
        assertEquals(source.getSourceType(), report.getSourceType());
        assertEquals(EVENT_NOISE_PROBABILITY, report.getRandomizedTriggerRate(), DOUBLE_MAX_DELTA);
    }

    @Test
    public void populateFromSourceAndTrigger_eventSourceWebDestWithoutInstallAttribution()
            throws JSONException {
        long baseTime = System.currentTimeMillis();
        Source source =
                createSourceForTest(
                        baseTime, Source.SourceType.EVENT, false, null, WEB_DESTINATION);
        Trigger trigger =
                createTriggerForTest(baseTime + TimeUnit.SECONDS.toMillis(10), WEB_DESTINATION);

        List<EventTrigger> eventTriggers = trigger.parseEventTriggers();
        EventReport report =
                new EventReport.Builder()
                        .populateFromSourceAndTrigger(source, trigger, eventTriggers.get(0))
                        .build();

        assertEquals(TRIGGER_PRIORITY, report.getTriggerPriority());
        assertEquals(TRIGGER_DEDUP_KEY, report.getTriggerDedupKey());
        // Truncated data 4 % 2 = 0
        assertEquals(0, report.getTriggerData());
        assertEquals(trigger.getTriggerTime(), report.getTriggerTime());
        assertEquals(source.getEventId(), report.getSourceId());
        assertEquals(source.getAdTechDomain(), report.getAdTechDomain());
        assertEquals(source.getEnrollmentId(), report.getEnrollmentId());
        assertEquals(trigger.getAttributionDestination(), report.getAttributionDestination());
        assertEquals(source.getExpiryTime() + ONE_HOUR_IN_MILLIS, report.getReportTime());
        assertEquals(Source.SourceType.EVENT, report.getSourceType());
        assertEquals(EVENT_NOISE_PROBABILITY, report.getRandomizedTriggerRate(), DOUBLE_MAX_DELTA);
    }

    @Test
    public void populateFromSourceAndTrigger_eventSourceAppDestWithInstallAttribution()
            throws JSONException {
        long baseTime = System.currentTimeMillis();
        Source source =
                createSourceForTest(baseTime, Source.SourceType.EVENT, true, APP_DESTINATION, null);
        Trigger trigger =
                createTriggerForTest(baseTime + TimeUnit.SECONDS.toMillis(10), APP_DESTINATION);

        List<EventTrigger> eventTriggers = trigger.parseEventTriggers();
        EventReport report =
                new EventReport.Builder()
                        .populateFromSourceAndTrigger(source, trigger, eventTriggers.get(0))
                        .build();

        assertEquals(TRIGGER_PRIORITY, report.getTriggerPriority());
        assertEquals(TRIGGER_DEDUP_KEY, report.getTriggerDedupKey());
        assertEquals(trigger.getTriggerTime(), report.getTriggerTime());
        assertEquals(source.getEventId(), report.getSourceId());
        assertEquals(source.getAdTechDomain(), report.getAdTechDomain());
        assertEquals(source.getEnrollmentId(), report.getEnrollmentId());
        assertEquals(trigger.getAttributionDestination(), report.getAttributionDestination());
        assertEquals(source.getExpiryTime() + ONE_HOUR_IN_MILLIS, report.getReportTime());
        assertEquals(Source.SourceType.EVENT, report.getSourceType());
        assertEquals(
                INSTALL_ATTR_EVENT_NOISE_PROBABILITY,
                report.getRandomizedTriggerRate(),
                DOUBLE_MAX_DELTA);
    }

    @Test
    public void populateFromSourceAndTrigger_eventSourceWebDestWithInstallAttribution()
            throws JSONException {
        long baseTime = System.currentTimeMillis();
        Source source =
                createSourceForTest(baseTime, Source.SourceType.EVENT, true, null, WEB_DESTINATION);
        Trigger trigger =
                createTriggerForTest(baseTime + TimeUnit.SECONDS.toMillis(10), WEB_DESTINATION);

        List<EventTrigger> eventTriggers = trigger.parseEventTriggers();
        EventReport report =
                new EventReport.Builder()
                        .populateFromSourceAndTrigger(source, trigger, eventTriggers.get(0))
                        .build();

        assertEquals(TRIGGER_PRIORITY, report.getTriggerPriority());
        assertEquals(TRIGGER_DEDUP_KEY, report.getTriggerDedupKey());
        assertEquals(trigger.getTriggerTime(), report.getTriggerTime());
        assertEquals(source.getEventId(), report.getSourceId());
        assertEquals(source.getAdTechDomain(), report.getAdTechDomain());
        assertEquals(source.getEnrollmentId(), report.getEnrollmentId());
        assertEquals(trigger.getAttributionDestination(), report.getAttributionDestination());
        assertEquals(source.getExpiryTime() + ONE_HOUR_IN_MILLIS, report.getReportTime());
        assertEquals(Source.SourceType.EVENT, report.getSourceType());
        assertEquals(EVENT_NOISE_PROBABILITY, report.getRandomizedTriggerRate(), DOUBLE_MAX_DELTA);
    }

    @Test
    public void populateFromSourceAndTrigger_navigationSourceAppDestWithoutInstall()
            throws JSONException {
        long baseTime = System.currentTimeMillis();
        Source source =
                createSourceForTest(
                        baseTime, Source.SourceType.NAVIGATION, false, APP_DESTINATION, null);
        Trigger trigger =
                createTriggerForTest(baseTime + TimeUnit.SECONDS.toMillis(10), APP_DESTINATION);

        List<EventTrigger> eventTriggers = trigger.parseEventTriggers();
        EventReport report =
                new EventReport.Builder()
                        .populateFromSourceAndTrigger(source, trigger, eventTriggers.get(0))
                        .build();

        assertEquals(TRIGGER_PRIORITY, report.getTriggerPriority());
        assertEquals(TRIGGER_DEDUP_KEY, report.getTriggerDedupKey());
        assertEquals(trigger.getTriggerTime(), report.getTriggerTime());
        assertEquals(source.getEventId(), report.getSourceId());
        assertEquals(source.getAdTechDomain(), report.getAdTechDomain());
        assertEquals(source.getEnrollmentId(), report.getEnrollmentId());
        assertEquals(APP_DESTINATION, report.getAttributionDestination());
        assertEquals(
                source.getEventTime()
                        + NAVIGATION_EARLY_REPORTING_WINDOW_MILLISECONDS[0]
                        + ONE_HOUR_IN_MILLIS,
                report.getReportTime());
        assertEquals(Source.SourceType.NAVIGATION, report.getSourceType());
        assertEquals(
                NAVIGATION_NOISE_PROBABILITY, report.getRandomizedTriggerRate(), DOUBLE_MAX_DELTA);
    }

    @Test
    public void populateFromSourceAndTrigger_navigationSourceWebDestWithoutInstall()
            throws JSONException {
        long baseTime = System.currentTimeMillis();
        Source source =
                createSourceForTest(
                        baseTime, Source.SourceType.NAVIGATION, false, null, WEB_DESTINATION);
        Trigger trigger =
                createTriggerForTest(baseTime + TimeUnit.SECONDS.toMillis(10), WEB_DESTINATION);

        List<EventTrigger> eventTriggers = trigger.parseEventTriggers();
        EventReport report =
                new EventReport.Builder()
                        .populateFromSourceAndTrigger(source, trigger, eventTriggers.get(0))
                        .build();

        assertEquals(TRIGGER_PRIORITY, report.getTriggerPriority());
        assertEquals(TRIGGER_DEDUP_KEY, report.getTriggerDedupKey());
        assertEquals(trigger.getTriggerTime(), report.getTriggerTime());
        assertEquals(source.getEventId(), report.getSourceId());
        assertEquals(source.getAdTechDomain(), report.getAdTechDomain());
        assertEquals(source.getEnrollmentId(), report.getEnrollmentId());
        assertEquals(trigger.getAttributionDestination(), report.getAttributionDestination());
        assertEquals(
                source.getReportingTime(trigger.getTriggerTime(), EventSurfaceType.WEB),
                report.getReportTime());
        assertEquals(source.getSourceType(), report.getSourceType());
        assertEquals(
                NAVIGATION_NOISE_PROBABILITY, report.getRandomizedTriggerRate(), DOUBLE_MAX_DELTA);
    }

    @Test
    public void testPopulateFromSourceAndTrigger_navigationSourceAppDestWithInstallAttribution()
            throws JSONException {
        long baseTime = System.currentTimeMillis();
        Source source =
                createSourceForTest(
                        baseTime, Source.SourceType.NAVIGATION, true, APP_DESTINATION, null);
        Trigger trigger =
                createTriggerForTest(baseTime + TimeUnit.SECONDS.toMillis(10), APP_DESTINATION);

        List<EventTrigger> eventTriggers = trigger.parseEventTriggers();
        EventReport report =
                new EventReport.Builder()
                        .populateFromSourceAndTrigger(source, trigger, eventTriggers.get(0))
                        .build();

        assertEquals(TRIGGER_PRIORITY, report.getTriggerPriority());
        assertEquals(TRIGGER_DEDUP_KEY, report.getTriggerDedupKey());
        assertEquals(4, report.getTriggerData());
        assertEquals(trigger.getTriggerTime(), report.getTriggerTime());
        assertEquals(source.getEventId(), report.getSourceId());
        assertEquals(source.getAdTechDomain(), report.getAdTechDomain());
        assertEquals(source.getEnrollmentId(), report.getEnrollmentId());
        assertEquals(trigger.getAttributionDestination(), report.getAttributionDestination());
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
    }

    @Test
    public void testPopulateFromSourceAndTrigger_navigationSourceWebDestWithInstallAttribution()
            throws JSONException {
        long baseTime = System.currentTimeMillis();
        Source source =
                createSourceForTest(
                        baseTime, Source.SourceType.NAVIGATION, true, null, WEB_DESTINATION);
        Trigger trigger =
                createTriggerForTest(baseTime + TimeUnit.SECONDS.toMillis(10), WEB_DESTINATION);

        List<EventTrigger> eventTriggers = trigger.parseEventTriggers();
        EventReport report =
                new EventReport.Builder()
                        .populateFromSourceAndTrigger(source, trigger, eventTriggers.get(0))
                        .build();

        assertEquals(TRIGGER_PRIORITY, report.getTriggerPriority());
        assertEquals(TRIGGER_DEDUP_KEY, report.getTriggerDedupKey());
        assertEquals(4, report.getTriggerData());
        assertEquals(trigger.getTriggerTime(), report.getTriggerTime());
        assertEquals(source.getEventId(), report.getSourceId());
        assertEquals(source.getAdTechDomain(), report.getAdTechDomain());
        assertEquals(source.getEnrollmentId(), report.getEnrollmentId());
        assertEquals(trigger.getAttributionDestination(), report.getAttributionDestination());
        // One hour after regular navigation type window (without install attribution consideration)
        assertEquals(
                source.getEventTime()
                        + NAVIGATION_EARLY_REPORTING_WINDOW_MILLISECONDS[0]
                        + ONE_HOUR_IN_MILLIS,
                report.getReportTime());
        assertEquals(source.getSourceType(), report.getSourceType());
        assertEquals(
                NAVIGATION_NOISE_PROBABILITY, report.getRandomizedTriggerRate(), DOUBLE_MAX_DELTA);
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
                        .setSourceId(22)
                        .setAdTechDomain(Uri.parse("https://foo.com"))
                        .setEnrollmentId("another-enrollment-id")
                        .setAttributionDestination(Uri.parse("https://bar.com"))
                        .setTriggerTime(1000L)
                        .setTriggerData(8L)
                        .setTriggerPriority(2L)
                        .setTriggerDedupKey(3L)
                        .setReportTime(2000L)
                        .setStatus(EventReport.Status.PENDING)
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
                .setEventId(10)
                .setSourceType(sourceType)
                .setInstallCooldownWindow(isInstallAttributable ? 100 : 0)
                .setEventTime(eventTime)
                .setAdTechDomain(Uri.parse("https://example-adtech1.com"))
                .setEnrollmentId("enrollment-id")
                .setAppDestination(appDestination)
                .setWebDestination(webDestination)
                .setExpiryTime(eventTime + TimeUnit.DAYS.toMillis(10))
                .build();
    }

    private Trigger createTriggerForTest(long eventTime, Uri destination) {
        return TriggerFixture.getValidTriggerBuilder()
                .setTriggerTime(eventTime)
                .setEventTriggers(EVENT_TRIGGERS)
                .setAdTechDomain(Uri.parse("https://example-adtech2.com"))
                .setEnrollmentId("enrollment-id")
                .setAttributionDestination(destination)
                .build();
    }

    private EventReport createExample() {
        return new EventReport.Builder()
                .setId("1")
                .setSourceId(21)
                .setAdTechDomain(Uri.parse("https://foo.com"))
                .setEnrollmentId("enrollment-id")
                .setAttributionDestination(Uri.parse("https://bar.com"))
                .setTriggerTime(1000L)
                .setTriggerData(8L)
                .setTriggerPriority(2L)
                .setTriggerDedupKey(3L)
                .setReportTime(2000L)
                .setStatus(EventReport.Status.PENDING)
                .setSourceType(Source.SourceType.NAVIGATION)
                .build();
    }
}

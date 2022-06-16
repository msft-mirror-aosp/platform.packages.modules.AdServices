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
import static org.junit.Assert.assertNull;

import android.net.Uri;

import androidx.test.filters.SmallTest;

import org.json.JSONException;
import org.junit.Test;

import java.util.List;
import java.util.concurrent.TimeUnit;

/** Unit tests for {@link EventReport} */
@SmallTest
public final class EventReportTest {

    private static final double DOUBLE_MAX_DELTA = 0.0000001D;
    private static final long TRIGGER_PRIORITY = 345678L;
    private static final Long TRIGGER_DEDUP_KEY = 2345678L;
    private static final Long TRIGGER_DATA = 4L;
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

    private EventReport createExample() {
        return new EventReport.Builder()
                .setId("1")
                .setSourceId(21)
                .setAdTechDomain(Uri.parse("http://foo.com"))
                .setAttributionDestination(Uri.parse("http://bar.com"))
                .setTriggerTime(1000L)
                .setTriggerData(8L)
                .setTriggerPriority(2L)
                .setTriggerDedupKey(3L)
                .setReportTime(2000L)
                .setStatus(EventReport.Status.PENDING)
                .setSourceType(Source.SourceType.NAVIGATION)
                .build();
    }

    @Test
    public void testCreation() throws Exception {
        EventReport eventReport = createExample();
        assertEquals("1", eventReport.getId());
        assertEquals(21, eventReport.getSourceId());
        assertEquals("http://foo.com", eventReport.getAdTechDomain().toString());
        assertEquals("http://bar.com", eventReport.getAttributionDestination().toString());
        assertEquals(1000L, eventReport.getTriggerTime());
        assertEquals(8L, eventReport.getTriggerData());
        assertEquals(2L, eventReport.getTriggerPriority());
        assertEquals(Long.valueOf(3), eventReport.getTriggerDedupKey());
        assertEquals(2000L, eventReport.getReportTime());
        assertEquals(EventReport.Status.PENDING, eventReport.getStatus());
        assertEquals(Source.SourceType.NAVIGATION, eventReport.getSourceType());
    }

    @Test
    public void testDefaults() throws Exception {
        EventReport eventReport = new EventReport.Builder().build();
        assertNull(eventReport.getId());
        assertEquals(0L, eventReport.getSourceId());
        assertNull(eventReport.getAdTechDomain());
        assertNull(eventReport.getAttributionDestination());
        assertEquals(0L, eventReport.getTriggerTime());
        assertEquals(0L, eventReport.getTriggerData());
        assertEquals(0L, eventReport.getTriggerPriority());
        assertNull(eventReport.getTriggerDedupKey());
        assertEquals(0L, eventReport.getReportTime());
        assertEquals(EventReport.Status.PENDING, eventReport.getStatus());
        assertNull(eventReport.getSourceType());
    }

    private Source createSourceForTest(long eventTime, Source.SourceType sourceType,
            boolean isInstallAttributable) {
        return new Source.Builder()
                .setEventId(10)
                .setSourceType(sourceType)
                .setInstallCooldownWindow(isInstallAttributable ? 100 : 0)
                .setEventTime(eventTime)
                .setAdTechDomain(Uri.parse("https://example-adtech1.com"))
                .setAttributionDestination(Uri.parse("android-app://example1.app"))
                .setExpiryTime(eventTime + TimeUnit.DAYS.toMillis(10))
                .build();
    }

    private Trigger createTriggerForTest(long eventTime) {
        return new Trigger.Builder()
                .setTriggerTime(eventTime)
                .setEventTriggers(EVENT_TRIGGERS)
                .setAdTechDomain(Uri.parse("https://example-adtech2.com"))
                .setAttributionDestination(Uri.parse("android-app://example2.app"))
                .build();
    }

    @Test
    public void testPopulateFromSourceAndTrigger_event() throws JSONException {
        long baseTime = System.currentTimeMillis();
        Source source = createSourceForTest(baseTime, Source.SourceType.EVENT, false);
        Trigger trigger = createTriggerForTest(baseTime + TimeUnit.SECONDS.toMillis(10));

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
        assertEquals(source.getAttributionDestination(), report.getAttributionDestination());
        assertEquals(source.getReportingTime(trigger.getTriggerTime()), report.getReportTime());
        assertEquals(source.getSourceType(), report.getSourceType());
        assertEquals(PrivacyParams.EVENT_NOISE_PROBABILITY, report.getRandomizedTriggerRate(),
                DOUBLE_MAX_DELTA);
    }

    @Test
    public void testPopulateFromSourceAndTrigger_eventWithInstallAttribution()
            throws JSONException {
        long baseTime = System.currentTimeMillis();
        Source source = createSourceForTest(baseTime, Source.SourceType.EVENT, true);
        Trigger trigger = createTriggerForTest(baseTime + TimeUnit.SECONDS.toMillis(10));

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
        assertEquals(source.getAttributionDestination(), report.getAttributionDestination());
        assertEquals(source.getReportingTime(trigger.getTriggerTime()), report.getReportTime());
        assertEquals(source.getSourceType(), report.getSourceType());
        assertEquals(PrivacyParams.INSTALL_ATTR_EVENT_NOISE_PROBABILITY,
                report.getRandomizedTriggerRate(),
                DOUBLE_MAX_DELTA);
    }

    @Test
    public void testPopulateFromSourceAndTrigger_navigation() throws JSONException {
        long baseTime = System.currentTimeMillis();
        Source source = createSourceForTest(baseTime, Source.SourceType.NAVIGATION, false);
        Trigger trigger = createTriggerForTest(baseTime + TimeUnit.SECONDS.toMillis(10));

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
        assertEquals(source.getAttributionDestination(), report.getAttributionDestination());
        assertEquals(source.getReportingTime(trigger.getTriggerTime()), report.getReportTime());
        assertEquals(source.getSourceType(), report.getSourceType());
        assertEquals(PrivacyParams.NAVIGATION_NOISE_PROBABILITY, report.getRandomizedTriggerRate(),
                DOUBLE_MAX_DELTA);
    }

    @Test
    public void testPopulateFromSourceAndTrigger_navigationWithInstallAttribution()
            throws JSONException {
        long baseTime = System.currentTimeMillis();
        Source source = createSourceForTest(baseTime, Source.SourceType.NAVIGATION, true);
        Trigger trigger = createTriggerForTest(baseTime + TimeUnit.SECONDS.toMillis(10));

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
        assertEquals(source.getAttributionDestination(), report.getAttributionDestination());
        assertEquals(source.getReportingTime(trigger.getTriggerTime()), report.getReportTime());
        assertEquals(source.getSourceType(), report.getSourceType());
        assertEquals(PrivacyParams.INSTALL_ATTR_NAVIGATION_NOISE_PROBABILITY,
                report.getRandomizedTriggerRate(),
                DOUBLE_MAX_DELTA);
    }

}

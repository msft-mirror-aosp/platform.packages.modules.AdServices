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

import static org.junit.Assert.assertEquals;

import com.android.adservices.service.Flags;
import com.android.adservices.service.measurement.EventSurfaceType;
import com.android.adservices.service.measurement.PrivacyParams;
import com.android.adservices.service.measurement.Source;
import com.android.adservices.service.measurement.SourceFixture;

import org.junit.Before;
import org.junit.Test;
import org.junit.runner.RunWith;
import org.mockito.Mock;
import org.mockito.junit.MockitoJUnitRunner;

import java.util.concurrent.TimeUnit;

@RunWith(MockitoJUnitRunner.class)
public class EventReportWindowCalcDelegateTest {
    @Mock private Flags mFlags;

    EventReportWindowCalcDelegate mEventReportWindowCalcDelegate;

    @Before
    public void setup() {
        mEventReportWindowCalcDelegate = new EventReportWindowCalcDelegate(mFlags);
    }

    @Test
    public void getReportingTime_eventSourceAppDestination() {
        long triggerTime = System.currentTimeMillis();
        long expiryTime = triggerTime + TimeUnit.DAYS.toMillis(30);
        long sourceEventTime = triggerTime - TimeUnit.DAYS.toMillis(1);
        Source source =
                SourceFixture.getValidSourceBuilder()
                        .setSourceType(Source.SourceType.EVENT)
                        .setEventReportWindow(expiryTime)
                        .setEventTime(sourceEventTime)
                        .build();
        assertEquals(
                expiryTime + TimeUnit.HOURS.toMillis(1),
                mEventReportWindowCalcDelegate.getReportingTime(
                        source, triggerTime, EventSurfaceType.APP));
    }

    @Test
    public void getReportingTime_eventSrcInstallAttributedAppDestinationTrigger1stWindow() {
        long triggerTime = System.currentTimeMillis();
        long expiryTime = triggerTime + TimeUnit.DAYS.toMillis(30);
        long sourceEventTime = triggerTime - TimeUnit.DAYS.toMillis(1);
        Source source =
                SourceFixture.getValidSourceBuilder()
                        .setSourceType(Source.SourceType.EVENT)
                        .setEventReportWindow(expiryTime)
                        .setEventTime(sourceEventTime)
                        .setInstallAttributed(true)
                        .build();
        assertEquals(
                sourceEventTime
                        + PrivacyParams.INSTALL_ATTR_EVENT_EARLY_REPORTING_WINDOW_MILLISECONDS[0]
                        + TimeUnit.HOURS.toMillis(1),
                mEventReportWindowCalcDelegate.getReportingTime(
                        source, triggerTime, EventSurfaceType.APP));
    }

    @Test
    public void getReportingTime_eventSrcInstallAttributedAppDestinationTrigger2ndWindow() {
        long triggerTime = System.currentTimeMillis();
        long expiryTime = triggerTime + TimeUnit.DAYS.toMillis(30);
        long sourceEventTime = triggerTime - TimeUnit.DAYS.toMillis(3);
        Source source =
                SourceFixture.getValidSourceBuilder()
                        .setSourceType(Source.SourceType.EVENT)
                        .setEventReportWindow(expiryTime)
                        .setEventTime(sourceEventTime)
                        .setInstallAttributed(true)
                        .build();
        assertEquals(
                expiryTime + TimeUnit.HOURS.toMillis(1),
                mEventReportWindowCalcDelegate.getReportingTime(
                        source, triggerTime, EventSurfaceType.APP));
    }

    @Test
    public void getReportingTime_eventSrcInstallAttributedWebDestinationTrigger1stWindow() {
        long triggerTime = System.currentTimeMillis();
        long expiryTime = triggerTime + TimeUnit.DAYS.toMillis(30);
        long sourceEventTime = triggerTime - TimeUnit.DAYS.toMillis(1);
        Source source =
                SourceFixture.getValidSourceBuilder()
                        .setSourceType(Source.SourceType.EVENT)
                        .setEventReportWindow(expiryTime)
                        .setEventTime(sourceEventTime)
                        .setInstallAttributed(true)
                        .build();
        assertEquals(
                expiryTime + TimeUnit.HOURS.toMillis(1),
                mEventReportWindowCalcDelegate.getReportingTime(
                        source, triggerTime, EventSurfaceType.WEB));
    }

    @Test
    public void getReportingTime_eventSrcInstallAttributedWebDestinationTrigger2ndWindow() {
        long triggerTime = System.currentTimeMillis();
        long expiryTime = triggerTime + TimeUnit.DAYS.toMillis(30);
        long sourceEventTime = triggerTime - TimeUnit.DAYS.toMillis(3);
        Source source =
                SourceFixture.getValidSourceBuilder()
                        .setSourceType(Source.SourceType.EVENT)
                        .setEventReportWindow(expiryTime)
                        .setEventTime(sourceEventTime)
                        .setInstallAttributed(true)
                        .build();
        assertEquals(
                expiryTime + TimeUnit.HOURS.toMillis(1),
                mEventReportWindowCalcDelegate.getReportingTime(
                        source, triggerTime, EventSurfaceType.WEB));
    }

    @Test
    public void getReportingTime_eventSourceWebDestination() {
        long triggerTime = System.currentTimeMillis();
        long expiryTime = triggerTime + TimeUnit.DAYS.toMillis(30);
        long sourceEventTime = triggerTime - TimeUnit.DAYS.toMillis(1);
        Source source =
                SourceFixture.getValidSourceBuilder()
                        .setSourceType(Source.SourceType.EVENT)
                        .setEventReportWindow(expiryTime)
                        .setEventTime(sourceEventTime)
                        .build();
        assertEquals(
                expiryTime + TimeUnit.HOURS.toMillis(1),
                mEventReportWindowCalcDelegate.getReportingTime(
                        source, triggerTime, EventSurfaceType.WEB));
    }

    @Test
    public void getReportingTime_navigationSourceTriggerInFirstWindow() {
        long triggerTime = System.currentTimeMillis();
        long sourceExpiryTime = triggerTime + TimeUnit.DAYS.toMillis(25);
        long sourceEventTime = triggerTime - TimeUnit.DAYS.toMillis(1);
        Source source =
                SourceFixture.getValidSourceBuilder()
                        .setSourceType(Source.SourceType.NAVIGATION)
                        .setEventReportWindow(sourceExpiryTime)
                        .setEventTime(sourceEventTime)
                        .build();
        assertEquals(
                sourceEventTime
                        + PrivacyParams.NAVIGATION_EARLY_REPORTING_WINDOW_MILLISECONDS[0]
                        + TimeUnit.HOURS.toMillis(1),
                mEventReportWindowCalcDelegate.getReportingTime(
                        source, triggerTime, EventSurfaceType.APP));
    }

    @Test
    public void getReportingTime_navigationSourceTriggerInSecondWindow() {
        long triggerTime = System.currentTimeMillis();
        long sourceExpiryTime = triggerTime + TimeUnit.DAYS.toMillis(25);
        long sourceEventTime = triggerTime - TimeUnit.DAYS.toMillis(3);
        Source source =
                SourceFixture.getValidSourceBuilder()
                        .setSourceType(Source.SourceType.NAVIGATION)
                        .setEventReportWindow(sourceExpiryTime)
                        .setEventTime(sourceEventTime)
                        .build();
        assertEquals(
                sourceEventTime
                        + PrivacyParams.NAVIGATION_EARLY_REPORTING_WINDOW_MILLISECONDS[1]
                        + TimeUnit.HOURS.toMillis(1),
                mEventReportWindowCalcDelegate.getReportingTime(
                        source, triggerTime, EventSurfaceType.APP));
    }

    @Test
    public void getReportingTime_navigationSecondExpiry() {
        long triggerTime = System.currentTimeMillis();
        long sourceExpiryTime = triggerTime + TimeUnit.DAYS.toMillis(2);
        long sourceEventTime = triggerTime - TimeUnit.DAYS.toMillis(3);
        Source source =
                SourceFixture.getValidSourceBuilder()
                        .setSourceType(Source.SourceType.NAVIGATION)
                        .setEventReportWindow(sourceExpiryTime)
                        .setEventTime(sourceEventTime)
                        .build();
        assertEquals(
                sourceExpiryTime + TimeUnit.HOURS.toMillis(1),
                mEventReportWindowCalcDelegate.getReportingTime(
                        source, triggerTime, EventSurfaceType.APP));
    }

    @Test
    public void getReportingTime_navigationLast() {
        long triggerTime = System.currentTimeMillis();
        long sourceExpiryTime = triggerTime + TimeUnit.DAYS.toMillis(1);
        long sourceEventTime = triggerTime - TimeUnit.DAYS.toMillis(20);
        Source source =
                SourceFixture.getValidSourceBuilder()
                        .setSourceType(Source.SourceType.NAVIGATION)
                        .setEventReportWindow(sourceExpiryTime)
                        .setEventTime(sourceEventTime)
                        .build();
        assertEquals(
                sourceExpiryTime + TimeUnit.HOURS.toMillis(1),
                mEventReportWindowCalcDelegate.getReportingTime(
                        source, triggerTime, EventSurfaceType.APP));
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
                mEventReportWindowCalcDelegate.getMaxReportCount(
                        eventSourceInstallNotAttributed, false));
        assertEquals(
                PrivacyParams.EVENT_SOURCE_MAX_REPORTS,
                mEventReportWindowCalcDelegate.getMaxReportCount(
                        eventSourceInstallNotAttributed, false));

        Source navigationSourceInstallNotAttributed =
                SourceFixture.getValidSourceBuilder()
                        .setSourceType(Source.SourceType.NAVIGATION)
                        .setInstallAttributed(false)
                        .build();
        assertEquals(
                PrivacyParams.NAVIGATION_SOURCE_MAX_REPORTS,
                mEventReportWindowCalcDelegate.getMaxReportCount(
                        navigationSourceInstallNotAttributed, false));
        assertEquals(
                PrivacyParams.NAVIGATION_SOURCE_MAX_REPORTS,
                mEventReportWindowCalcDelegate.getMaxReportCount(
                        navigationSourceInstallNotAttributed, false));

        Source eventSourceInstallAttributed =
                SourceFixture.getValidSourceBuilder()
                        .setSourceType(Source.SourceType.EVENT)
                        .setInstallAttributed(true)
                        .build();
        assertEquals(
                PrivacyParams.INSTALL_ATTR_EVENT_SOURCE_MAX_REPORTS,
                mEventReportWindowCalcDelegate.getMaxReportCount(
                        eventSourceInstallAttributed, true));
        // Install attribution state does not matter for web destination
        assertEquals(
                PrivacyParams.EVENT_SOURCE_MAX_REPORTS,
                mEventReportWindowCalcDelegate.getMaxReportCount(
                        eventSourceInstallAttributed, false));

        Source navigationSourceInstallAttributed =
                SourceFixture.getValidSourceBuilder()
                        .setSourceType(Source.SourceType.NAVIGATION)
                        .setInstallAttributed(true)
                        .build();
        assertEquals(
                PrivacyParams.NAVIGATION_SOURCE_MAX_REPORTS,
                mEventReportWindowCalcDelegate.getMaxReportCount(
                        navigationSourceInstallAttributed, true));
        assertEquals(
                PrivacyParams.NAVIGATION_SOURCE_MAX_REPORTS,
                mEventReportWindowCalcDelegate.getMaxReportCount(
                        navigationSourceInstallAttributed, true));
    }

    @Test
    public void reportingTimeByIndex_event() {
        long eventTime = System.currentTimeMillis();
        long oneHourInMillis = TimeUnit.HOURS.toMillis(1);

        // Expected: 1 window at expiry
        Source eventSource10d =
                SourceFixture.getValidSourceBuilder()
                        .setSourceType(Source.SourceType.EVENT)
                        .setEventTime(eventTime)
                        .setEventReportWindow(eventTime + TimeUnit.DAYS.toMillis(10))
                        .build();
        assertEquals(
                eventTime + TimeUnit.DAYS.toMillis(10) + oneHourInMillis,
                mEventReportWindowCalcDelegate.getReportingTimeForNoising(
                        eventSource10d, /* windowIndex= */ 0, /* isInstallCase */ false));
        assertEquals(
                eventTime + TimeUnit.DAYS.toMillis(10) + oneHourInMillis,
                mEventReportWindowCalcDelegate.getReportingTimeForNoising(
                        eventSource10d, /* windowIndex= */ 1, /* isInstallCase */ false));

        // Expected: 1 window at expiry
        Source eventSource7d =
                SourceFixture.getValidSourceBuilder()
                        .setSourceType(Source.SourceType.EVENT)
                        .setEventTime(eventTime)
                        .setEventReportWindow(eventTime + TimeUnit.DAYS.toMillis(7))
                        .build();
        assertEquals(
                eventTime + TimeUnit.DAYS.toMillis(7) + oneHourInMillis,
                mEventReportWindowCalcDelegate.getReportingTimeForNoising(
                        eventSource7d, /* windowIndex= */ 0, /* isInstallCase */ false));
        assertEquals(
                eventTime + TimeUnit.DAYS.toMillis(7) + oneHourInMillis,
                mEventReportWindowCalcDelegate.getReportingTimeForNoising(
                        eventSource7d, /* windowIndex= */ 1, /* isInstallCase */ false));

        // Expected: 1 window at expiry
        Source eventSource2d =
                SourceFixture.getValidSourceBuilder()
                        .setSourceType(Source.SourceType.EVENT)
                        .setEventTime(eventTime)
                        .setEventReportWindow(eventTime + TimeUnit.DAYS.toMillis(2))
                        .build();
        assertEquals(
                eventTime + TimeUnit.DAYS.toMillis(2) + oneHourInMillis,
                mEventReportWindowCalcDelegate.getReportingTimeForNoising(
                        eventSource2d, /* windowIndex= */ 0, /* isInstallCase */ false));
        assertEquals(
                eventTime + TimeUnit.DAYS.toMillis(2) + oneHourInMillis,
                mEventReportWindowCalcDelegate.getReportingTimeForNoising(
                        eventSource2d, /* windowIndex= */ 1, /* isInstallCase */ false));
    }

    @Test
    public void reportingTimeByIndex_eventWithInstallAttribution() {
        long eventTime = System.currentTimeMillis();
        long oneHourInMillis = TimeUnit.HOURS.toMillis(1);

        // Expected: 2 windows at 2d, expiry(10d)
        Source eventSource10d =
                SourceFixture.getValidSourceBuilder()
                        .setSourceType(Source.SourceType.EVENT)
                        .setInstallCooldownWindow(TimeUnit.DAYS.toMillis(1))
                        .setEventTime(eventTime)
                        .setEventReportWindow(eventTime + TimeUnit.DAYS.toMillis(10))
                        .build();
        assertEquals(
                eventTime + TimeUnit.DAYS.toMillis(2) + oneHourInMillis,
                mEventReportWindowCalcDelegate.getReportingTimeForNoising(
                        eventSource10d, /* windowIndex= */ 0, /* isInstallCase */ true));
        assertEquals(
                eventTime + TimeUnit.DAYS.toMillis(10) + oneHourInMillis,
                mEventReportWindowCalcDelegate.getReportingTimeForNoising(
                        eventSource10d, /* windowIndex= */ 1, /* isInstallCase */ true));
        assertEquals(
                eventTime + TimeUnit.DAYS.toMillis(10) + oneHourInMillis,
                mEventReportWindowCalcDelegate.getReportingTimeForNoising(
                        eventSource10d, /* windowIndex= */ 2, /* isInstallCase */ true));

        // Expected: 1 window at 2d(expiry)
        Source eventSource2d =
                SourceFixture.getValidSourceBuilder()
                        .setSourceType(Source.SourceType.EVENT)
                        .setEventTime(eventTime)
                        .setEventReportWindow(eventTime + TimeUnit.DAYS.toMillis(2))
                        .build();
        assertEquals(
                eventTime + TimeUnit.DAYS.toMillis(2) + oneHourInMillis,
                mEventReportWindowCalcDelegate.getReportingTimeForNoising(
                        eventSource2d, /* windowIndex= */ 0, /* isInstallCase */ true));
        assertEquals(
                eventTime + TimeUnit.DAYS.toMillis(2) + oneHourInMillis,
                mEventReportWindowCalcDelegate.getReportingTimeForNoising(
                        eventSource2d, /* windowIndex= */ 1, /* isInstallCase */ true));
    }

    @Test
    public void reportingTimeByIndex_navigation() {
        long eventTime = System.currentTimeMillis();
        long oneHourInMillis = TimeUnit.HOURS.toMillis(1);

        // Expected: 3 windows at 2d, 7d & expiry(20d)
        Source navigationSource20d =
                SourceFixture.getValidSourceBuilder()
                        .setSourceType(Source.SourceType.NAVIGATION)
                        .setEventTime(eventTime)
                        .setEventReportWindow(eventTime + TimeUnit.DAYS.toMillis(20))
                        .build();
        assertEquals(
                eventTime + TimeUnit.DAYS.toMillis(2) + oneHourInMillis,
                mEventReportWindowCalcDelegate.getReportingTimeForNoising(
                        navigationSource20d, /* windowIndex= */ 0, /* isInstallCase */ false));
        assertEquals(
                eventTime + TimeUnit.DAYS.toMillis(7) + oneHourInMillis,
                mEventReportWindowCalcDelegate.getReportingTimeForNoising(
                        navigationSource20d, /* windowIndex= */ 1, /* isInstallCase */ false));
        assertEquals(
                eventTime + TimeUnit.DAYS.toMillis(20) + oneHourInMillis,
                mEventReportWindowCalcDelegate.getReportingTimeForNoising(
                        navigationSource20d, /* windowIndex= */ 2, /* isInstallCase */ false));

        // Expected: 2 windows at 2d & expiry(7d)
        Source navigationSource7d =
                SourceFixture.getValidSourceBuilder()
                        .setSourceType(Source.SourceType.NAVIGATION)
                        .setEventTime(eventTime)
                        .setEventReportWindow(eventTime + TimeUnit.DAYS.toMillis(7))
                        .build();
        assertEquals(
                eventTime + TimeUnit.DAYS.toMillis(2) + oneHourInMillis,
                mEventReportWindowCalcDelegate.getReportingTimeForNoising(
                        navigationSource7d, /* windowIndex= */ 0, /* isInstallCase */ false));
        assertEquals(
                eventTime + TimeUnit.DAYS.toMillis(7) + oneHourInMillis,
                mEventReportWindowCalcDelegate.getReportingTimeForNoising(
                        navigationSource7d, /* windowIndex= */ 1, /* isInstallCase */ false));
        assertEquals(
                eventTime + TimeUnit.DAYS.toMillis(7) + oneHourInMillis,
                mEventReportWindowCalcDelegate.getReportingTimeForNoising(
                        navigationSource7d, /* windowIndex= */ 2, /* isInstallCase */ false));

        // Expected: 1 window at 2d(expiry)
        Source navigationSource2d =
                SourceFixture.getValidSourceBuilder()
                        .setSourceType(Source.SourceType.NAVIGATION)
                        .setEventTime(eventTime)
                        .setEventReportWindow(eventTime + TimeUnit.DAYS.toMillis(2))
                        .build();
        assertEquals(
                eventTime + TimeUnit.DAYS.toMillis(2) + oneHourInMillis,
                mEventReportWindowCalcDelegate.getReportingTimeForNoising(
                        navigationSource2d, /* windowIndex= */ 0, /* isInstallCase */ false));
        assertEquals(
                eventTime + TimeUnit.DAYS.toMillis(2) + oneHourInMillis,
                mEventReportWindowCalcDelegate.getReportingTimeForNoising(
                        navigationSource2d, /* windowIndex= */ 1, /* isInstallCase */ false));
    }

    @Test
    public void reportingTimeByIndex_navigationWithInstallAttribution() {
        long eventTime = System.currentTimeMillis();
        long oneHourInMillis = TimeUnit.HOURS.toMillis(1);

        // Expected: 3 windows at 2d, 7d & expiry(20d)
        Source navigationSource20d =
                SourceFixture.getValidSourceBuilder()
                        .setSourceType(Source.SourceType.NAVIGATION)
                        .setInstallCooldownWindow(TimeUnit.DAYS.toMillis(1))
                        .setEventTime(eventTime)
                        .setEventReportWindow(eventTime + TimeUnit.DAYS.toMillis(20))
                        .build();
        assertEquals(
                eventTime + TimeUnit.DAYS.toMillis(2) + oneHourInMillis,
                mEventReportWindowCalcDelegate.getReportingTimeForNoising(
                        navigationSource20d, /* windowIndex= */ 0, /* isInstallCase */ false));
        assertEquals(
                eventTime + TimeUnit.DAYS.toMillis(7) + oneHourInMillis,
                mEventReportWindowCalcDelegate.getReportingTimeForNoising(
                        navigationSource20d, /* windowIndex= */ 1, /* isInstallCase */ false));
        assertEquals(
                eventTime + TimeUnit.DAYS.toMillis(20) + oneHourInMillis,
                mEventReportWindowCalcDelegate.getReportingTimeForNoising(
                        navigationSource20d, /* windowIndex= */ 2, /* isInstallCase */ false));

        // Expected: 2 windows at 2d & expiry(7d)
        Source navigationSource7d =
                SourceFixture.getValidSourceBuilder()
                        .setSourceType(Source.SourceType.NAVIGATION)
                        .setInstallCooldownWindow(TimeUnit.DAYS.toMillis(1))
                        .setEventTime(eventTime)
                        .setEventReportWindow(eventTime + TimeUnit.DAYS.toMillis(7))
                        .build();
        assertEquals(
                eventTime + TimeUnit.DAYS.toMillis(2) + oneHourInMillis,
                mEventReportWindowCalcDelegate.getReportingTimeForNoising(
                        navigationSource7d, /* windowIndex= */ 0, /* isInstallCase */ false));
        assertEquals(
                eventTime + TimeUnit.DAYS.toMillis(7) + oneHourInMillis,
                mEventReportWindowCalcDelegate.getReportingTimeForNoising(
                        navigationSource7d, /* windowIndex= */ 1, /* isInstallCase */ false));
        assertEquals(
                eventTime + TimeUnit.DAYS.toMillis(7) + oneHourInMillis,
                mEventReportWindowCalcDelegate.getReportingTimeForNoising(
                        navigationSource7d, /* windowIndex= */ 2, /* isInstallCase */ false));

        // Expected: 1 window at 2d(expiry)
        Source navigationSource2d =
                SourceFixture.getValidSourceBuilder()
                        .setSourceType(Source.SourceType.NAVIGATION)
                        .setInstallCooldownWindow(TimeUnit.DAYS.toMillis(1))
                        .setEventTime(eventTime)
                        .setEventReportWindow(eventTime + TimeUnit.DAYS.toMillis(2))
                        .build();
        assertEquals(
                eventTime + TimeUnit.DAYS.toMillis(2) + oneHourInMillis,
                mEventReportWindowCalcDelegate.getReportingTimeForNoising(
                        navigationSource2d, /* windowIndex= */ 0, /* isInstallCase */ false));
        assertEquals(
                eventTime + TimeUnit.DAYS.toMillis(2) + oneHourInMillis,
                mEventReportWindowCalcDelegate.getReportingTimeForNoising(
                        navigationSource2d, /* windowIndex= */ 1, /* isInstallCase */ false));
    }
}

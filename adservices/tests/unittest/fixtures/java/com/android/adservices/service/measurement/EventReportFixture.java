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

import static com.android.adservices.service.measurement.EventReportFixture.ValidEventReportParams;

import android.net.Uri;

import com.android.adservices.common.WebUtil;
import com.android.adservices.service.measurement.util.UnsignedLong;

import java.util.List;
import java.util.UUID;

public final class EventReportFixture {
    private EventReportFixture() {}

    public static EventReport.Builder getBaseEventReportBuild() {
        return new EventReport.Builder()
                .setId(UUID.randomUUID().toString())
                .setSourceEventId(ValidEventReportParams.SOURCE_EVENT_ID)
                .setEnrollmentId(ValidEventReportParams.ENROLLMENT_ID)
                .setAttributionDestinations(ValidEventReportParams.ATTRIBUTION_DESTINATIONS)
                .setTriggerTime(1000L)
                .setTriggerDedupKey(ValidEventReportParams.TRIGGER_DEDUP_KEY)
                .setReportTime(ValidEventReportParams.REPORT_TIME)
                .setStatus(ValidEventReportParams.STATUS)
                .setDebugReportStatus(ValidEventReportParams.DEBUG_REPORT_STATUS)
                .setSourceType(ValidEventReportParams.SOURCE_TYPE)
                .setSourceDebugKey(ValidEventReportParams.SOURCE_DEBUG_KEY)
                .setTriggerDebugKey(ValidEventReportParams.TRIGGER_DEBUG_KEY)
                .setSourceId(UUID.randomUUID().toString())
                .setTriggerId(UUID.randomUUID().toString())
                .setRegistrationOrigin(ValidEventReportParams.REGISTRATION_ORIGIN)
                .setTriggerSummaryBucket(ValidEventReportParams.TRIGGER_SUMMARY_BUCKET);
    }

    public static class ValidEventReportParams {
        public static final UnsignedLong SOURCE_EVENT_ID = new UnsignedLong(21L);
        public static final String ENROLLMENT_ID = "enrollment-id";
        public static final List<Uri> ATTRIBUTION_DESTINATIONS =
                List.of(Uri.parse("https://bar.test"));
        public static final long TRIGGER_TIME = 8640000000L;
        public static final String SOURCE_ID = "source_id";
        public static final String TRIGGER_ID = "trigger_id";
        public static final UnsignedLong TRIGGER_DEDUP_KEY = new UnsignedLong(3L);
        public static final long REPORT_TIME = 2000L;
        public static final int STATUS = EventReport.Status.PENDING;
        public static final int DEBUG_REPORT_STATUS = EventReport.DebugReportStatus.PENDING;
        public static final Source.SourceType SOURCE_TYPE = Source.SourceType.NAVIGATION;
        public static final UnsignedLong SOURCE_DEBUG_KEY = new UnsignedLong(237865L);
        public static final UnsignedLong TRIGGER_DEBUG_KEY = new UnsignedLong(928762L);
        public static final Uri REGISTRATION_ORIGIN =
                WebUtil.validUri("https://subdomain.example.test");
        public static final String TRIGGER_SUMMARY_BUCKET = "2,3";
    }
}

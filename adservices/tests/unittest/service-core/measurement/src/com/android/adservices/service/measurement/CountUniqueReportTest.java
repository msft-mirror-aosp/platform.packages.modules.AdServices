/*
 * Copyright (C) 2025 The Android Open Source Project
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

import static com.google.common.truth.Truth.assertWithMessage;

import android.net.Uri;

import org.junit.Test;

import java.util.Set;

public class CountUniqueReportTest {

    @Test
    public void testCreation() throws Exception {
        CountUniqueReport countUniqueReport = createCountUniqueReport();
        assertWithMessage("countUniqueReport.getReportId()")
                .that(countUniqueReport.getReportId())
                .isEqualTo("1");
        assertWithMessage("countUniqueReport.getReportingOrigin()")
                .that(countUniqueReport.getReportingOrigin())
                .isEqualTo(Uri.parse("https://example.test/cu"));
        assertWithMessage("countUniqueReport.getApiVersion()")
                .that(countUniqueReport.getApiVersion())
                .isEqualTo("0.1");
        assertWithMessage("countUniqueReport.getDebugKey()")
                .that(countUniqueReport.getDebugKey())
                .isEqualTo("123");
        assertWithMessage("countUniqueReport.getScheduledReportTime()")
                .that(countUniqueReport.getScheduledReportTime())
                .isEqualTo(234L);
        assertWithMessage("countUniqueReport.getStatus()")
                .that(countUniqueReport.getStatus())
                .isEqualTo(CountUniqueReport.ReportDeliveryStatus.PENDING);
        assertWithMessage("countUniqueReport.getDebugReportStatus()")
                .that(countUniqueReport.getDebugReportStatus())
                .isEqualTo(CountUniqueReport.ReportDeliveryStatus.PENDING);
        assertWithMessage("countUniqueReport.getPayload()")
                .that(countUniqueReport.getPayload())
                .isEqualTo("{bucket: 5678n, value: 16, filteringId: 33n}");
        assertWithMessage("countUniqueReport.getContextId()")
                .that(countUniqueReport.getContextId())
                .isEqualTo("test-context-id");
        assertWithMessage("countUniqueReport.getEnrollmentId()")
                .that(countUniqueReport.getEnrollmentId())
                .isEqualTo("test-enrollment-id");
        assertWithMessage("countUniqueReport.getContributionValue()")
                .that(countUniqueReport.getContributionValue())
                .isEqualTo(5);
        assertWithMessage("countUniqueReport.getContributionTime()")
                .that(countUniqueReport.getContributionTime())
                .isEqualTo(555L);
        assertWithMessage("countUniqueReport.getRegistrant()")
                .that(countUniqueReport.getRegistrant())
                .isEqualTo(Uri.parse("android-app://com.example"));
    }

    @Test
    public void testDefaults() throws Exception {
        CountUniqueReport countUniqueReport = new CountUniqueReport.Builder().build();
        assertWithMessage("countUniqueReport.getReportId()")
                .that(countUniqueReport.getReportId())
                .isNull();
        assertWithMessage("countUniqueReport.getReportingOrigin()")
                .that(countUniqueReport.getReportingOrigin())
                .isNull();
        assertWithMessage("countUniqueReport.getApiVersion()")
                .that(countUniqueReport.getApiVersion())
                .isNull();
        assertWithMessage("countUniqueReport.getDebugKey()")
                .that(countUniqueReport.getDebugKey())
                .isNull();
        assertWithMessage("countUniqueReport.getScheduledReportTime()")
                .that(countUniqueReport.getScheduledReportTime())
                .isNull();

        assertWithMessage("countUniqueReport.getStatus()")
                .that(countUniqueReport.getStatus())
                .isEqualTo(CountUniqueReport.ReportDeliveryStatus.PENDING);

        assertWithMessage("countUniqueReport.getDebugReportStatus()")
                .that(countUniqueReport.getDebugReportStatus())
                .isEqualTo(CountUniqueReport.ReportDeliveryStatus.NONE);

        assertWithMessage("countUniqueReport.getPayload()")
                .that(countUniqueReport.getPayload())
                .isNull();

        assertWithMessage("countUniqueReport.getContextId()")
                .that(countUniqueReport.getContextId())
                .isNull();
        assertWithMessage("countUniqueReport.getEnrollmentId()")
                .that(countUniqueReport.getEnrollmentId())
                .isNull();
        assertWithMessage("countUniqueReport.getContributionValue()")
                .that(countUniqueReport.getContributionValue())
                .isNull();
        assertWithMessage("countUniqueReport.getContributionTime()")
                .that(countUniqueReport.getContributionTime())
                .isNull();
        assertWithMessage("countUniqueReport.getRegistrant()")
                .that(countUniqueReport.getRegistrant())
                .isNull();
    }

    @Test
    public void testHashCode_equals() throws Exception {
        CountUniqueReport countUniqueReport1 = createCountUniqueReport();
        CountUniqueReport countUniqueReport2 = createCountUniqueReport();
        Set<CountUniqueReport> countUniqueReportSet1 = Set.of(countUniqueReport1);
        Set<CountUniqueReport> countUniqueReportSet2 = Set.of(countUniqueReport2);
        assertWithMessage("countUniqueReport.hashCode()")
                .that(countUniqueReport1.hashCode())
                .isEqualTo(countUniqueReport2.hashCode());
        assertWithMessage("CountUniqueReport")
                .that(countUniqueReport1)
                .isEqualTo(countUniqueReport2);
        assertWithMessage("Set<CountUniqueReport>")
                .that(countUniqueReportSet1)
                .isEqualTo(countUniqueReportSet2);
    }

    @Test
    public void testHashCode_notEquals() throws Exception {
        CountUniqueReport countUniqueReport1 = createCountUniqueReport();
        CountUniqueReport countUniqueReport2 =
                new CountUniqueReport.Builder()
                        .setReportId("1")
                        .setReportingOrigin(Uri.parse("https://example.test/cu"))
                        .setApiVersion("0.1")
                        .setDebugKey("123")
                        .setScheduledReportTime(211L)
                        .setStatus(CountUniqueReport.ReportDeliveryStatus.PENDING)
                        .setDebugReportStatus(CountUniqueReport.ReportDeliveryStatus.PENDING)
                        .setPayload("{bucket: 1238n, value: 22, filteringId: 44n}")
                        .setContextId("test-context-id")
                        .setEnrollmentId("different-enrollment")
                        .setContributionValue(4)
                        .setContributionTime(444L)
                        .setRegistrant(Uri.parse("android-app://com.example2"))
                        .build();
        Set<CountUniqueReport> countUniqueReportSet1 = Set.of(countUniqueReport1);
        Set<CountUniqueReport> countUniqueReportSet2 = Set.of(countUniqueReport2);

        assertWithMessage("countUniqueReport.hashCode()")
                .that(countUniqueReport1.hashCode())
                .isNotEqualTo(countUniqueReport2.hashCode());
        assertWithMessage("CountUniqueReport")
                .that(countUniqueReport1)
                .isNotEqualTo(countUniqueReport2);
        assertWithMessage("Set<CountUniqueReport>")
                .that(countUniqueReportSet1)
                .isNotEqualTo(countUniqueReportSet2);
    }

    private CountUniqueReport createCountUniqueReport() {
        return new CountUniqueReport.Builder()
                .setReportId("1")
                .setReportingOrigin(Uri.parse("https://example.test/cu"))
                .setApiVersion("0.1")
                .setDebugKey("123")
                .setScheduledReportTime(234L)
                .setStatus(CountUniqueReport.ReportDeliveryStatus.PENDING)
                .setDebugReportStatus(CountUniqueReport.ReportDeliveryStatus.PENDING)
                .setPayload("{bucket: 5678n, value: 16, filteringId: 33n}")
                .setContextId("test-context-id")
                .setEnrollmentId("test-enrollment-id")
                .setContributionValue(5)
                .setContributionTime(555L)
                .setRegistrant(Uri.parse("android-app://com.example"))
                .build();
    }
}

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

package com.android.adservices.service.stats;

import static com.android.adservices.service.stats.AdsRelevanceStatusUtils.REPORTING_API_REPORT_EVENT;
import static com.android.adservices.service.stats.AdsRelevanceStatusUtils.REPORTING_CALL_DESTINATION_COMPONENT_SELLER;
import static com.android.adservices.service.stats.AdsRelevanceStatusUtils.REPORTING_CALL_STATUS_FAILURE_HTTP_REDIRECTION;

import static com.google.common.truth.Truth.assertWithMessage;

import org.junit.Test;

public class ReportEventRequestWithDestinationPerformedTest {

    @Test
    public void testBuilderAndGetters() {
        ReportingWithDestinationPerformedStats stats =
                ReportingWithDestinationPerformedStats.builder()
                        .setDestination(REPORTING_CALL_DESTINATION_COMPONENT_SELLER)
                        .setStatus(REPORTING_CALL_STATUS_FAILURE_HTTP_REDIRECTION)
                        .setReportingType(REPORTING_API_REPORT_EVENT)
                        .build();

        assertWithMessage("Destination")
                .that(stats.getDestination())
                .isEqualTo(REPORTING_CALL_DESTINATION_COMPONENT_SELLER);
        assertWithMessage("Status")
                .that(stats.getStatus())
                .isEqualTo(REPORTING_CALL_STATUS_FAILURE_HTTP_REDIRECTION);

        assertWithMessage("Reporting type")
                .that(stats.getReportingType())
                .isEqualTo(REPORTING_API_REPORT_EVENT);
    }
}

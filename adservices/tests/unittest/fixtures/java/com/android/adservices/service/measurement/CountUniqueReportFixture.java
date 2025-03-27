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

import android.net.Uri;

import com.android.adservices.LogUtil;
import com.android.adservices.common.WebUtil;
import com.android.adservices.service.measurement.aggregation.AggregateHistogramContribution;
import com.android.adservices.service.measurement.aggregation.AggregateReport;
import com.android.adservices.service.measurement.util.UnsignedLong;

import org.json.JSONException;

import java.math.BigInteger;
import java.util.ArrayList;
import java.util.List;
import java.util.UUID;

public final class CountUniqueReportFixture {
    private CountUniqueReportFixture() {}

    public static CountUniqueReport.Builder getValidCountUniqueReportBuilder() {
        return new CountUniqueReport.Builder()
                .setReportId(UUID.randomUUID().toString())
                .setStatus(CountUniqueReport.ReportDeliveryStatus.PENDING)
                .setDebugReportStatus(CountUniqueReport.ReportDeliveryStatus.PENDING)
                .setScheduledReportTime(ValidCountUniqueParams.SCHEDULED_REPORT_TIME)
                .setReportingOrigin(ValidCountUniqueParams.REPORTING_ORIGIN)
                .setDebugKey(ValidCountUniqueParams.DEBUG_KEY.toString())
                .setContextId(ValidCountUniqueParams.CONTEXT_ID)
                .setPayload(ValidCountUniqueParams.getDebugPayload())
                .setApiVersion(ValidCountUniqueParams.API_VERSION);
    }

    public static class ValidCountUniqueParams {
        public static final long SCHEDULED_REPORT_TIME = 8640000000L;
        public static final Uri REPORTING_ORIGIN =
                WebUtil.validUri("https://subdomain.example.test");
        public static final UnsignedLong DEBUG_KEY = new UnsignedLong(67878545L);
        public static final String CONTEXT_ID = "context_id";
        public static final String API = "shared-storage";
        public static final String API_VERSION = "0.1";

        /** Get sample debug cleartext payload. */
        public static String getDebugPayload() {
            AggregateHistogramContribution contribution =
                    new AggregateHistogramContribution.Builder()
                            .setKey(BigInteger.valueOf(1369L))
                            .setValue(32768)
                            .build();

            List<AggregateHistogramContribution> contributions = new ArrayList<>();
            contributions.add(contribution);
            String debugPayload = null;
            try {
                debugPayload = AggregateReport.generateDebugPayload(contributions);
            } catch (JSONException e) {
                LogUtil.e("JSONException when generating debug payload.");
            }
            return debugPayload;
        }
    }
}

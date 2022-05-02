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

import org.json.JSONArray;
import org.json.JSONException;
import org.json.JSONObject;
import org.junit.Test;

public class AggregateReportBodyTest {

    private static final String SOURCE_SITE = "https://source.example";
    private static final String ATTRIBUTION_DESTINATION = "https://attribution.destination";
    private static final String SOURCE_REGISTRATION_TIME = "1246174152155";
    private static final String SCHEDULED_REPORT_TIME = "1246174158155";
    private static final String PRIVACY_BUDGET_KEY = "example-key";
    private static final String VERSION = "1";
    private static final String REPORT_ID = "A1";
    private static final String REPORTING_ORIGIN = "https://adtech.domain";
    private static final String DEBUG_CLEARTEXT_PAYLOAD = "{\"operation\":\"histogram\","
            + "\"data\":[{\"bucket\":1369,\"value\":32768},{\"bucket\":3461,"
            + "\"value\":1664}]}";

    private AggregateReportBody createAggregateReportBodyExample1() {
        return new AggregateReportBody.Builder()
                .setSourceSite(SOURCE_SITE)
                .setAttributionDestination(ATTRIBUTION_DESTINATION)
                .setSourceRegistrationTime(SOURCE_REGISTRATION_TIME)
                .setScheduledReportTime(SCHEDULED_REPORT_TIME)
                .setPrivacyBudgetKey(PRIVACY_BUDGET_KEY)
                .setVersion(VERSION)
                .setReportId(REPORT_ID)
                .setReportingOrigin(REPORTING_ORIGIN)
                .setDebugCleartextPayload(DEBUG_CLEARTEXT_PAYLOAD)
                .build();
    }

    @Test
    public void testAggregateBodyJsonSerialization() throws JSONException {
        AggregateReportBody aggregateReport = createAggregateReportBodyExample1();
        JSONObject aggregateReportJson = aggregateReport.toJson();

        assertEquals(SOURCE_SITE, aggregateReportJson.get("source_site"));
        assertEquals(ATTRIBUTION_DESTINATION, aggregateReportJson.get("attribution_destination"));
        assertEquals(SOURCE_REGISTRATION_TIME, aggregateReportJson.get("source_registration_time"));
    }

    @Test
    public void testSharedInfoJsonSerialization() throws JSONException {
        AggregateReportBody aggregateReport = createAggregateReportBodyExample1();
        JSONObject sharedInfoJson = aggregateReport.sharedInfoToJson();

        assertEquals(SCHEDULED_REPORT_TIME, sharedInfoJson.get("scheduled_report_time"));
        assertEquals(PRIVACY_BUDGET_KEY, sharedInfoJson.get("privacy_budget_key"));
        assertEquals(VERSION, sharedInfoJson.get("version"));
        assertEquals(REPORT_ID, sharedInfoJson.get("report_id"));
        assertEquals(REPORTING_ORIGIN, sharedInfoJson.get("reporting_origin"));
    }

    @Test
    public void testAggregationServicePayloadsJsonSerialization() throws JSONException {
        AggregateReportBody aggregateReport = createAggregateReportBodyExample1();
        JSONArray aggregationServicePayloadsJson =
                aggregateReport.aggregationServicePayloadsToJson();

        JSONObject debugCleartextPayloadJson =
                aggregationServicePayloadsJson.getJSONObject(0);

        assertEquals(DEBUG_CLEARTEXT_PAYLOAD,
                debugCleartextPayloadJson.get("debug_cleartext_payload"));
    }
}

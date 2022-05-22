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

import android.net.Uri;

import org.json.JSONException;
import org.json.JSONObject;
import org.junit.Test;
import org.mockito.Mockito;

import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.io.OutputStream;
import java.net.HttpURLConnection;

public class AggregateReportSenderTest {

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

    /**
     *
     * Tests posting a report with a mock HttpUrlConnection.
     */
    @Test
    public void testSendAggregateReport() throws JSONException, IOException {
        HttpURLConnection httpUrlConnection = Mockito.mock(HttpURLConnection.class);

        OutputStream outputStream = new ByteArrayOutputStream();
        Mockito.when(httpUrlConnection.getOutputStream()).thenReturn(outputStream);
        Mockito.when(httpUrlConnection.getResponseCode()).thenReturn(200);

        JSONObject aggregateReportJson = createAggregateReportBodyExample1().toJson();
        Uri reportingOrigin = Uri.parse(REPORTING_ORIGIN);

        AggregateReportSender aggregateReportSender = new AggregateReportSender();
        AggregateReportSender spyAggregateReportSender = Mockito.spy(aggregateReportSender);

        Mockito.doReturn(httpUrlConnection).when(spyAggregateReportSender)
                .createHttpUrlConnection(Mockito.any());

        int responseCode = spyAggregateReportSender.sendReport(reportingOrigin,
                aggregateReportJson);

        assertEquals(outputStream.toString(), aggregateReportJson.toString());
        assertEquals(responseCode, 200);
    }
}

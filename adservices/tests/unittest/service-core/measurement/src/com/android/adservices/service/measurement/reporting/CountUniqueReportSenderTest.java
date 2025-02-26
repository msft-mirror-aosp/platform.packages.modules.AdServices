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

package com.android.adservices.service.measurement.reporting;

import static com.google.common.truth.Truth.assertThat;

import android.content.Context;
import android.net.Uri;

import androidx.test.core.app.ApplicationProvider;

import com.android.adservices.service.FlagsFactory;
import com.android.adservices.service.measurement.aggregation.AggregateCryptoFixture;
import com.android.modules.utils.testing.TestableDeviceConfig;

import org.json.JSONException;
import org.json.JSONObject;
import org.junit.Rule;
import org.junit.Test;
import org.mockito.Mockito;

import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.io.OutputStream;
import java.net.HttpURLConnection;
import java.net.URL;

import javax.net.ssl.HttpsURLConnection;

public class CountUniqueReportSenderTest {

    @Rule
    public final TestableDeviceConfig.TestableDeviceConfigRule mDeviceConfigRule =
            new TestableDeviceConfig.TestableDeviceConfigRule();

    private static final long SCHEDULED_REPORT_TIME = 1246174158155L;
    private static final String VERSION = "1234";
    private static final String REPORT_ID = "A1";
    private static final Uri REPORTING_ORIGIN = Uri.parse("https://adtech.domain");
    private static final String COORDINATOR_ORIGIN = "https://coordinator.origin";
    private static final String CONTEXT_ID = "context_id";
    private static final String API = "count-unique";
    private static final String DEBUG_CLEARTEXT_PAYLOAD =
            "{\"operation\":\"histogram\"," + "\"data\":[{\"bucket\":\"1369\",\"value\":32768}]}";

    protected static final Context sContext = ApplicationProvider.getApplicationContext();

    private CountUniqueReportBody createCountUniqueReportBodyExample1() {
        return new CountUniqueReportBody.Builder()
                .setReportId(REPORT_ID)
                .setReportingOrigin(REPORTING_ORIGIN)
                .setContextId(CONTEXT_ID)
                .setApi(API)
                .setApiVersion(VERSION)
                .setAggregationCoordinatorOrigin(Uri.parse(COORDINATOR_ORIGIN))
                .setDebugCleartextPayload(DEBUG_CLEARTEXT_PAYLOAD)
                .setScheduledReportTime(SCHEDULED_REPORT_TIME)
                .build();
    }

    /** Tests posting a report with a mock HttpUrlConnection. */
    @Test
    public void testSendCountUniqueReport() throws JSONException, IOException {
        HttpURLConnection httpUrlConnection = Mockito.mock(HttpURLConnection.class);

        OutputStream outputStream = new ByteArrayOutputStream();
        Mockito.when(httpUrlConnection.getOutputStream()).thenReturn(outputStream);
        Mockito.when(httpUrlConnection.getResponseCode()).thenReturn(200);

        JSONObject countUniqueReportJson =
                createCountUniqueReportBodyExample1()
                        .toJson(AggregateCryptoFixture.getKey(), FlagsFactory.getFlags());

        CountUniqueReportSender countUniqueReportSender =
                new CountUniqueReportSender(false, sContext);
        CountUniqueReportSender spyCountUniqueReportSender = Mockito.spy(countUniqueReportSender);

        Mockito.doReturn(httpUrlConnection)
                .when(spyCountUniqueReportSender)
                .createHttpUrlConnection(Mockito.any());

        int responseCode =
                spyCountUniqueReportSender.sendReportWithHeaders(
                        REPORTING_ORIGIN, countUniqueReportJson, /* headers= */ null);

        assertThat(outputStream.toString()).isEqualTo(countUniqueReportJson.toString());
        assertThat(responseCode).isEqualTo(HttpsURLConnection.HTTP_OK);
    }

    @Test
    public void testCreateHttpUrlConnection() throws Exception {
        HttpsURLConnection mockConnection = Mockito.mock(HttpsURLConnection.class);
        URL spyUrl = Mockito.spy(new URL("https://foo"));
        Mockito.doReturn(mockConnection).when(spyUrl).openConnection();

        CountUniqueReportSender countUniqueReportSender =
                new CountUniqueReportSender(false, sContext);
        HttpsURLConnection connection =
                (HttpsURLConnection) countUniqueReportSender.createHttpUrlConnection(spyUrl);
        assertThat(connection).isEqualTo(mockConnection);
    }
}

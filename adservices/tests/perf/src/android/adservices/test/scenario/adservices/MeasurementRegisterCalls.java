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

package android.adservices.test.scenario.adservices;

import android.Manifest;
import android.adservices.clients.measurement.MeasurementClient;
import android.adservices.test.scenario.adservices.utils.MockWebServerRule;
import android.content.Context;
import android.net.Uri;
import android.os.SystemClock;
import android.platform.test.scenario.annotation.Scenario;
import android.provider.DeviceConfig;
import android.support.test.uiautomator.UiDevice;

import androidx.test.core.app.ApplicationProvider;
import androidx.test.platform.app.InstrumentationRegistry;

import com.google.mockwebserver.MockResponse;
import com.google.mockwebserver.MockWebServer;

import org.json.JSONArray;
import org.json.JSONException;
import org.json.JSONObject;
import org.junit.AfterClass;
import org.junit.BeforeClass;
import org.junit.Test;
import org.junit.runner.RunWith;
import org.junit.runners.JUnit4;

import java.io.IOException;
import java.net.URL;
import java.util.List;
import java.util.Map;
import java.util.concurrent.Executor;
import java.util.concurrent.Executors;

/** Crystal Ball tests for Measurement API. */
@Scenario
@RunWith(JUnit4.class)
public class MeasurementRegisterCalls {
    protected static final Context sContext = ApplicationProvider.getApplicationContext();
    private static final Executor CALLBACK_EXECUTOR = Executors.newCachedThreadPool();
    private static MeasurementClient sMeasurementClient;
    private static UiDevice sDevice;

    @BeforeClass
    public static void setupDevicePropertiesAndInitializeClient() throws Exception {
        sMeasurementClient =
                new MeasurementClient.Builder()
                        .setContext(sContext)
                        .setExecutor(CALLBACK_EXECUTOR)
                        .build();
        InstrumentationRegistry.getInstrumentation()
                .getUiAutomation()
                .adoptShellPermissionIdentity(Manifest.permission.WRITE_DEVICE_CONFIG);

        // Override consent manager behavior to give user consent.
        getUiDevice()
                .executeShellCommand("setprop debug.adservices.consent_manager_debug_mode true");

        // Override the flag to allow current package to call APIs.
        DeviceConfig.setProperty(
                DeviceConfig.NAMESPACE_ADSERVICES,
                "ppapi_app_allow_list",
                "*",
                /* makeDefault */ false);
    }

    @AfterClass
    public static void resetDeviceProperties() throws Exception {
        // Reset consent
        getUiDevice()
                .executeShellCommand("setprop debug.adservices.consent_manager_debug_mode false");

        // Reset allowed packages.
        DeviceConfig.setProperty(
                DeviceConfig.NAMESPACE_ADSERVICES,
                "ppapi_app_allow_list",
                "null",
                /* makeDefault */ false);
    }

    @Test
    public void testRegisterSourceAndTriggerAndRunAttributionAndReporting() throws Exception {
        // Create source registration response.
        MockResponse sourceResponse = new MockResponse();
        final JSONObject headerRegisterSource =
                buildJson(
                        Map.of(
                                "source_event_id", 1,
                                "destination", "android-app://android.platform.test.scenario",
                                "priority", 1));
        sourceResponse.addHeader("Attribution-Reporting-Register-Source", headerRegisterSource);
        sourceResponse.setResponseCode(200);

        // Create trigger registration response.
        MockResponse triggerResponse = new MockResponse();
        final JSONObject eventTriggerData =
                buildJson(
                        Map.of(
                                "trigger_data", "1",
                                "priority", "101"));
        final JSONArray eventTriggerDataList = buildJsonArray(List.of(eventTriggerData));
        final JSONObject headerRegisterTrigger =
                buildJson(Map.of("event_trigger_data", eventTriggerDataList));
        triggerResponse.addHeader("Attribution-Reporting-Register-Trigger", headerRegisterTrigger);
        triggerResponse.setResponseCode(200);

        // Create report response.
        MockResponse reportResponse = new MockResponse();
        reportResponse.setResponseCode(200);

        // Start mock web server.
        List<MockResponse> responses = List.of(sourceResponse, triggerResponse, reportResponse);
        MockWebServer mockWebServer =
                MockWebServerRule.forHttps(
                                sContext, "adservices_test_server.p12", "adservices_test")
                        .startMockWebServer(responses);

        URL url = mockWebServer.getUrl("/mockServer");

        // Set the initial time to register the source and trigger.
        getUiDevice().executeShellCommand("date -s 2022-08-01");

        sMeasurementClient.registerSource(Uri.parse(url.toString()), null).get();
        sMeasurementClient.registerTrigger(Uri.parse(url.toString())).get();
        runAttributionJob();

        // Advance the time so that generated report is within the reporting window.
        getUiDevice().executeShellCommand("date -s 2022-09-01");
        runReportingJob();
    }

    private void runAttributionJob() throws InterruptedException, IOException {
        getUiDevice()
                .executeShellCommand("cmd jobscheduler run -f com.google.android.adservices.api 5");
        // Wait for attribution to complete.
        SystemClock.sleep(2000);
    }

    private void runReportingJob() throws InterruptedException, IOException {
        getUiDevice()
                .executeShellCommand("cmd jobscheduler run -f com.google.android.adservices.api 3");
        // Wait for reporting to complete.
        SystemClock.sleep(2000);
    }

    private static JSONObject buildJson(Map<String, Object> fields) throws JSONException {
        JSONObject json = new JSONObject();
        for (Map.Entry<String, Object> entry : fields.entrySet()) {
            json.put(entry.getKey(), entry.getValue());
        }
        return json;
    }

    private static JSONArray buildJsonArray(List<JSONObject> list) throws JSONException {
        JSONArray json = new JSONArray();
        for (int i = 0; i < list.size(); i++) {
            json.put(i, list.get(i));
        }
        return json;
    }

    private static UiDevice getUiDevice() {
        if (sDevice == null) {
            sDevice = UiDevice.getInstance(InstrumentationRegistry.getInstrumentation());
        }
        return sDevice;
    }
}

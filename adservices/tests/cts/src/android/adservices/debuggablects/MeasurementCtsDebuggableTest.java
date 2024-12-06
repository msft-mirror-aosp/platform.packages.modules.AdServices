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

package android.adservices.debuggablects;

import static com.android.adservices.service.FlagsConstants.KEY_MEASUREMENT_AGGREGATE_REPORT_DELAY_CONFIG;
import static com.android.adservices.service.FlagsConstants.KEY_MEASUREMENT_AGGREGATION_COORDINATOR_ORIGIN_LIST;
import static com.android.adservices.service.FlagsConstants.KEY_MEASUREMENT_AGGREGATION_COORDINATOR_PATH;
import static com.android.adservices.service.FlagsConstants.KEY_MEASUREMENT_DEFAULT_AGGREGATION_COORDINATOR_ORIGIN;
import static com.android.adservices.service.FlagsConstants.KEY_MEASUREMENT_ENFORCE_FOREGROUND_STATUS_REGISTER_SOURCE;
import static com.android.adservices.service.FlagsConstants.KEY_MEASUREMENT_ENFORCE_FOREGROUND_STATUS_REGISTER_TRIGGER;
import static com.android.adservices.service.FlagsConstants.KEY_MEASUREMENT_EVENT_REPORTS_VTC_EARLY_REPORTING_WINDOWS;
import static com.android.adservices.service.FlagsConstants.KEY_MEASUREMENT_KILL_SWITCH;
import static com.android.adservices.service.FlagsConstants.KEY_MEASUREMENT_NULL_AGG_REPORT_RATE_EXCL_SOURCE_REGISTRATION_TIME;
import static com.android.adservices.service.FlagsConstants.KEY_MEASUREMENT_NULL_AGG_REPORT_RATE_INCL_SOURCE_REGISTRATION_TIME;
import static com.android.adservices.service.FlagsConstants.KEY_MEASUREMENT_REGISTRATION_JOB_QUEUE_KILL_SWITCH;
import static com.android.adservices.service.FlagsConstants.KEY_WEB_CONTEXT_CLIENT_ALLOW_LIST;

import static com.google.common.truth.Truth.assertThat;

import android.adservices.common.AdServicesOutcomeReceiver;
import android.adservices.measurement.DeletionRequest;
import android.adservices.measurement.MeasurementManager;
import android.adservices.measurement.WebSourceParams;
import android.adservices.measurement.WebSourceRegistrationRequest;
import android.adservices.measurement.WebTriggerParams;
import android.adservices.measurement.WebTriggerRegistrationRequest;
import android.adservices.utils.DevContextUtils;
import android.adservices.utils.MockWebServerRule;
import android.net.Uri;

import androidx.test.platform.app.InstrumentationRegistry;
import androidx.test.uiautomator.UiDevice;

import com.android.adservices.common.AdServicesSupportHelper;
import com.android.adservices.shared.testing.SupportedByConditionRule;

import com.google.mockwebserver.MockResponse;
import com.google.mockwebserver.MockWebServer;
import com.google.mockwebserver.RecordedRequest;

import org.junit.After;
import org.junit.Before;
import org.junit.Rule;
import org.junit.Test;

import java.io.IOException;
import java.util.Collections;
import java.util.List;
import java.util.Objects;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.ExecutionException;
import java.util.concurrent.Executor;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.Future;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.TimeoutException;

/** CTS debuggable test for Measurement API. */
public final class MeasurementCtsDebuggableTest extends AdServicesDebuggableTestCase {
    private static final Executor CALLBACK_EXECUTOR = Executors.newCachedThreadPool();
    private static UiDevice sDevice;

    private static final String SERVER_BASE_URI = replaceTestDomain("https://localhost");
    private static final String WEB_ORIGIN = replaceTestDomain("https://rb-example-origin.test");
    private static final String WEB_DESTINATION =
            replaceTestDomain("https://rb-example-destination.test");

    private static final int DEFAULT_PORT = 38383;
    private static final int KEYS_PORT = 38384;

    private static final long TIMEOUT_IN_MS = 5_000;

    private static final int EVENT_REPORTING_JOB_ID = 3;
    private static final int ATTRIBUTION_REPORTING_JOB_ID = 5;
    private static final int ASYNC_REGISTRATION_QUEUE_JOB_ID = 20;
    private static final int AGGREGATE_REPORTING_JOB_ID = 7;

    private static final String AGGREGATE_ENCRYPTION_KEY_COORDINATOR_ORIGIN =
            SERVER_BASE_URI + ":" + KEYS_PORT;
    private static final String AGGREGATE_ENCRYPTION_KEY_COORDINATOR_PATH = "keys";
    private static final String REGISTRATION_RESPONSE_SOURCE_HEADER =
            "Attribution-Reporting-Register-Source";
    private static final String REGISTRATION_RESPONSE_TRIGGER_HEADER =
            "Attribution-Reporting-Register-Trigger";
    private static final String SOURCE_PATH = "/source";
    private static final String TRIGGER_PATH = "/trigger";
    private static final String AGGREGATE_ATTRIBUTION_REPORT_URI_PATH =
            "/.well-known/attribution-reporting/report-aggregate-attribution";
    private static final String EVENT_ATTRIBUTION_REPORT_URI_PATH =
            "/.well-known/attribution-reporting/report-event-attribution";

    @Rule(order = 11)
    public final SupportedByConditionRule devOptionsEnabled =
            DevContextUtils.createDevOptionsAvailableRule(mContext, LOGCAT_TAG_MEASUREMENT);

    private MeasurementManager mMeasurementManager;

    @Before
    public void setup() throws Exception {
        setFlagsForMeasurement();

        mMeasurementManager = MeasurementManager.get(sContext);
        Objects.requireNonNull(mMeasurementManager);
        executeDeleteRegistrations();
    }

    @After
    public void tearDown() {
        executeDeleteRegistrations();
    }

    @Test
    public void registerSourceAndTriggerAndRunAttributionAndEventReporting() {
        executeRegisterSource();
        executeRegisterTrigger();
        executeAttribution();
        executeEventReporting();
    }

    @Test
    public void registerSourceAndTriggerAndRunAttributionAndAggregateReporting() {
        executeRegisterSource();
        executeRegisterTrigger();
        executeAttribution();
        executeAggregateReporting();
    }

    @Test
    public void registerWebSourceAndWebTriggerAndRunAttributionAndEventReporting() {
        executeRegisterWebSource();
        executeRegisterWebTrigger();
        executeAttribution();
        executeEventReporting();
    }

    @Test
    public void registerWebSourceAndWebTriggerAndRunAttributionAndAggregateReporting() {
        executeRegisterWebSource();
        executeRegisterWebTrigger();
        executeAttribution();
        executeAggregateReporting();
    }

    private static UiDevice getUiDevice() {
        if (sDevice == null) {
            sDevice = UiDevice.getInstance(InstrumentationRegistry.getInstrumentation());
        }
        return sDevice;
    }

    private static String replaceTestDomain(String value) {
        return value.replaceAll("test", "com");
    }

    private MockWebServerRule createForHttps() {
        return MockWebServerRule.forHttps(
                sContext, "adservices_untrusted_test_server.p12", "adservices_test");
    }

    private MockWebServer startServer(int port, MockResponse... mockResponses) {
        try {
            MockWebServerRule serverRule = createForHttps();
            return serverRule.startMockWebServer(List.of(mockResponses), port);
        } catch (Exception e) {
            throw new IllegalStateException(e);
        }
    }

    private void shutdownServer(MockWebServer mockWebServer) {
        try {
            if (mockWebServer != null) mockWebServer.shutdown();
        } catch (IOException e) {
            throw new IllegalStateException(e);
        } finally {
            sleep();
        }
    }

    private static void sleep() {
        sleep(1L);
    }

    private static void sleep(long seconds) {
        try {
            TimeUnit.MILLISECONDS.sleep(seconds * 1000L);
        } catch (InterruptedException e) {
            throw new IllegalStateException(e);
        }
    }

    private MockResponse createRegisterSourceResponse() {
        MockResponse mockRegisterSourceResponse = new MockResponse();
        String payload =
                "{"
                        + "\"destination\": \"android-app://"
                        + mPackageName
                        + "\","
                        + "\"priority\": \"10\","
                        + "\"expiry\": \"1728000\","
                        + "\"source_event_id\": \"11111111111\","
                        + "\"aggregation_keys\": "
                        + "              {"
                        + "                \"campaignCounts\": \"0x159\","
                        + "                \"geoValue\": \"0x5\""
                        + "              }"
                        + "}";

        mockRegisterSourceResponse.setHeader(REGISTRATION_RESPONSE_SOURCE_HEADER, payload);
        mockRegisterSourceResponse.setResponseCode(200);
        return mockRegisterSourceResponse;
    }

    private MockResponse createRegisterTriggerResponse() {
        MockResponse mockRegisterTriggerResponse = new MockResponse();
        String payload =
                "{\"event_trigger_data\":"
                        + "[{"
                        + "  \"trigger_data\": \"1\","
                        + "  \"priority\": \"1\","
                        + "  \"deduplication_key\": \"111\""
                        + "}],"
                        + "\"aggregatable_trigger_data\": ["
                        + "              {"
                        + "                \"key_piece\": \"0x200\","
                        + "                \"source_keys\": ["
                        + "                  \"campaignCounts\","
                        + "                  \"geoValue\""
                        + "                ]"
                        + "              }"
                        + "            ],"
                        + "            \"aggregatable_values\": {"
                        + "              \"campaignCounts\": 32768,"
                        + "              \"geoValue\": 1664"
                        + "            }"
                        + "}";

        mockRegisterTriggerResponse.setHeader(REGISTRATION_RESPONSE_TRIGGER_HEADER, payload);
        mockRegisterTriggerResponse.setResponseCode(200);
        return mockRegisterTriggerResponse;
    }

    private MockResponse createRegisterWebSourceResponse() {
        MockResponse mockRegisterWebSourceResponse = new MockResponse();
        String payload =
                "{"
                        + "\"web_destination\": \""
                        + WEB_DESTINATION
                        + "\","
                        + "\"priority\": \"10\","
                        + "\"expiry\": \"1728000\","
                        + "\"source_event_id\": \"99999999999\","
                        + "\"aggregation_keys\": "
                        + "              {"
                        + "                \"campaignCounts\": \"0x159\","
                        + "                \"geoValue\": \"0x5\""
                        + "              }"
                        + "}";

        mockRegisterWebSourceResponse.setHeader(REGISTRATION_RESPONSE_SOURCE_HEADER, payload);
        mockRegisterWebSourceResponse.setResponseCode(200);
        return mockRegisterWebSourceResponse;
    }

    private MockResponse createRegisterWebTriggerResponse() {
        MockResponse mockRegisterWebTriggerResponse = new MockResponse();
        String payload =
                "{\"event_trigger_data\":"
                        + "[{"
                        + "  \"trigger_data\": \"9\","
                        + "  \"priority\": \"9\","
                        + "  \"deduplication_key\": \"999\""
                        + "}],"
                        + "\"aggregatable_trigger_data\": ["
                        + "              {"
                        + "                \"key_piece\": \"0x200\","
                        + "                \"source_keys\": ["
                        + "                  \"campaignCounts\","
                        + "                  \"geoValue\""
                        + "                ]"
                        + "              }"
                        + "            ],"
                        + "            \"aggregatable_values\": {"
                        + "              \"campaignCounts\": 32768,"
                        + "              \"geoValue\": 1664"
                        + "            }"
                        + "}]}";

        mockRegisterWebTriggerResponse.setHeader(REGISTRATION_RESPONSE_TRIGGER_HEADER, payload);
        mockRegisterWebTriggerResponse.setResponseCode(200);
        return mockRegisterWebTriggerResponse;
    }

    private MockResponse createEventReportUploadResponse() {
        MockResponse reportResponse = new MockResponse();
        reportResponse.setResponseCode(200);
        return reportResponse;
    }

    private MockResponse createAggregateReportUploadResponse() {
        MockResponse reportResponse = new MockResponse();
        reportResponse.setResponseCode(200);
        return reportResponse;
    }

    private MockResponse createGetAggregationKeyResponse() {
        MockResponse mockGetAggregationKeyResponse = new MockResponse();
        String body =
                "{\"keys\":[{"
                        + "\"id\":\"0fa73e34-c6f3-4839-a4ed-d1681f185a76\","
                        + "\"key\":\"bcy3EsCsm/7rhO1VSl9W+h4MM0dv20xjcFbbLPE16Vg\\u003d\"}]}";

        mockGetAggregationKeyResponse.setBody(body);
        mockGetAggregationKeyResponse.setHeader("age", "14774");
        mockGetAggregationKeyResponse.setHeader("cache-control", "max-age=72795");
        mockGetAggregationKeyResponse.setResponseCode(200);

        return mockGetAggregationKeyResponse;
    }

    private void executeAsyncRegistrationJob() {
        executeJob(ASYNC_REGISTRATION_QUEUE_JOB_ID);
    }

    private void executeJob(int jobId) {
        String packageName = AdServicesSupportHelper.getInstance().getAdServicesPackageName();
        String cmd = "cmd jobscheduler run -f " + packageName + " " + jobId;
        try {
            getUiDevice().executeShellCommand(cmd);
        } catch (IOException e) {
            throw new IllegalStateException(
                    String.format("Error while executing job %d", jobId), e);
        }
    }

    private void executeAttributionJob() {
        executeJob(ATTRIBUTION_REPORTING_JOB_ID);
    }

    private void executeEventReportingJob() {
        executeJob(EVENT_REPORTING_JOB_ID);
    }

    private void executeAggregateReportingJob() {
        executeJob(AGGREGATE_REPORTING_JOB_ID);
    }

    private void executeRegisterSource() {
        MockResponse mockResponse = createRegisterSourceResponse();
        MockWebServer mockWebServer = startServer(DEFAULT_PORT, mockResponse);

        try {
            String path = SERVER_BASE_URI + ":" + mockWebServer.getPort() + SOURCE_PATH;

            CountDownLatch countDownLatch = new CountDownLatch(1);
            mMeasurementManager.registerSource(
                    Uri.parse(path),
                    /* inputEvent= */ null,
                    CALLBACK_EXECUTOR,
                    (AdServicesOutcomeReceiver<Object, Exception>)
                            result -> countDownLatch.countDown());
            assertThat(countDownLatch.await(TIMEOUT_IN_MS, TimeUnit.MILLISECONDS)).isTrue();

            sleep();
            executeAsyncRegistrationJob();
            sleep();

            RecordedRequest recordedRequest = takeRequestTimeoutWrapper(mockWebServer);
            assertThat(recordedRequest.getPath()).isEqualTo(SOURCE_PATH);
            assertThat(mockWebServer.getRequestCount()).isEqualTo(1);
        } catch (InterruptedException e) {
            throw new IllegalStateException("Error while registering source", e);
        } finally {
            shutdownServer(mockWebServer);
        }
    }

    private void executeRegisterTrigger() {
        MockResponse mockResponse = createRegisterTriggerResponse();
        MockWebServer mockWebServer = startServer(DEFAULT_PORT, mockResponse);

        try {
            String path = SERVER_BASE_URI + ":" + mockWebServer.getPort() + TRIGGER_PATH;

            CountDownLatch countDownLatch = new CountDownLatch(1);
            mMeasurementManager.registerTrigger(
                    Uri.parse(path),
                    CALLBACK_EXECUTOR,
                    (AdServicesOutcomeReceiver<Object, Exception>)
                            result -> countDownLatch.countDown());
            assertThat(countDownLatch.await(TIMEOUT_IN_MS, TimeUnit.MILLISECONDS)).isTrue();

            sleep();
            executeAsyncRegistrationJob();
            sleep();

            RecordedRequest recordedRequest = takeRequestTimeoutWrapper(mockWebServer);
            assertThat(recordedRequest.getPath()).isEqualTo(TRIGGER_PATH);
            assertThat(mockWebServer.getRequestCount()).isEqualTo(1);
        } catch (InterruptedException e) {
            throw new IllegalStateException("Error while registering trigger", e);
        } finally {
            shutdownServer(mockWebServer);
        }
    }

    private void executeRegisterWebSource() {
        MockResponse mockResponse = createRegisterWebSourceResponse();
        MockWebServer mockWebServer = startServer(DEFAULT_PORT, mockResponse);

        try {
            String path = SERVER_BASE_URI + ":" + mockWebServer.getPort() + SOURCE_PATH;
            WebSourceParams params = new WebSourceParams.Builder(Uri.parse(path)).build();
            WebSourceRegistrationRequest request =
                    new WebSourceRegistrationRequest.Builder(
                                    Collections.singletonList(params), Uri.parse(WEB_ORIGIN))
                            .setWebDestination(Uri.parse(WEB_DESTINATION))
                            .build();

            CountDownLatch countDownLatch = new CountDownLatch(1);
            mMeasurementManager.registerWebSource(
                    request,
                    CALLBACK_EXECUTOR,
                    (AdServicesOutcomeReceiver<Object, Exception>)
                            result -> countDownLatch.countDown());
            assertThat(countDownLatch.await(TIMEOUT_IN_MS, TimeUnit.MILLISECONDS)).isTrue();

            sleep();
            executeAsyncRegistrationJob();
            sleep();

            RecordedRequest recordedRequest = takeRequestTimeoutWrapper(mockWebServer);
            assertThat(recordedRequest.getPath()).isEqualTo(SOURCE_PATH);
            assertThat(mockWebServer.getRequestCount()).isEqualTo(1);
        } catch (InterruptedException e) {
            throw new IllegalStateException("Error while registering web source", e);
        } finally {
            shutdownServer(mockWebServer);
        }
    }

    private void executeRegisterWebTrigger() {
        MockResponse mockResponse = createRegisterWebTriggerResponse();
        MockWebServer mockWebServer = startServer(DEFAULT_PORT, mockResponse);

        try {
            String path = SERVER_BASE_URI + ":" + mockWebServer.getPort() + TRIGGER_PATH;
            WebTriggerParams params = new WebTriggerParams.Builder(Uri.parse(path)).build();
            WebTriggerRegistrationRequest request =
                    new WebTriggerRegistrationRequest.Builder(
                                    Collections.singletonList(params), Uri.parse(WEB_DESTINATION))
                            .build();

            CountDownLatch countDownLatch = new CountDownLatch(1);
            mMeasurementManager.registerWebTrigger(
                    request,
                    CALLBACK_EXECUTOR,
                    (AdServicesOutcomeReceiver<Object, Exception>)
                            result -> countDownLatch.countDown());
            assertThat(countDownLatch.await(TIMEOUT_IN_MS, TimeUnit.MILLISECONDS)).isTrue();

            sleep();
            executeAsyncRegistrationJob();
            sleep();

            RecordedRequest recordedRequest = takeRequestTimeoutWrapper(mockWebServer);
            assertThat(recordedRequest.getPath()).isEqualTo(TRIGGER_PATH);
            assertThat(mockWebServer.getRequestCount()).isEqualTo(1);
        } catch (InterruptedException e) {
            throw new IllegalStateException("Error while registering web trigger", e);
        } finally {
            shutdownServer(mockWebServer);
        }
    }

    private void executeAttribution() {
        MockResponse mockResponse = createGetAggregationKeyResponse();
        MockWebServer mockWebServer = startServer(KEYS_PORT, mockResponse);

        try {
            sleep();
            executeAttributionJob();
            sleep();
        } finally {
            shutdownServer(mockWebServer);
        }
    }

    private void executeEventReporting() {
        MockResponse mockResponse = createEventReportUploadResponse();
        MockWebServer mockWebServer = startServer(DEFAULT_PORT, mockResponse, mockResponse);
        try {
            sleep();
            executeEventReportingJob();
            sleep();

            RecordedRequest recordedRequest = takeRequestTimeoutWrapper(mockWebServer);
            assertThat(recordedRequest.getPath()).isEqualTo(EVENT_ATTRIBUTION_REPORT_URI_PATH);
            assertThat(mockWebServer.getRequestCount()).isEqualTo(1);
        } finally {
            shutdownServer(mockWebServer);
        }
    }

    private void executeAggregateReporting() {
        MockResponse aggregateReportMockResponse = createAggregateReportUploadResponse();
        MockWebServer aggregateReportWebServer =
                startServer(DEFAULT_PORT, aggregateReportMockResponse, aggregateReportMockResponse);

        MockResponse keysMockResponse = createGetAggregationKeyResponse();
        MockWebServer keysReportWebServer =
                startServer(KEYS_PORT, keysMockResponse, keysMockResponse);

        try {
            sleep();
            executeAggregateReportingJob();
            sleep();

            RecordedRequest recordedRequest = takeRequestTimeoutWrapper(aggregateReportWebServer);
            assertThat(recordedRequest.getPath()).isEqualTo(AGGREGATE_ATTRIBUTION_REPORT_URI_PATH);
            assertThat(aggregateReportWebServer.getRequestCount()).isEqualTo(1);
        } finally {
            shutdownServer(aggregateReportWebServer);
            shutdownServer(keysReportWebServer);
        }
    }

    private void executeDeleteRegistrations() {
        try {
            DeletionRequest deletionRequest =
                    new DeletionRequest.Builder()
                            // Preserve none since empty origin and site lists are provided.
                            .setMatchBehavior(DeletionRequest.MATCH_BEHAVIOR_PRESERVE)
                            .build();
            CountDownLatch countDownLatch = new CountDownLatch(1);
            mMeasurementManager.deleteRegistrations(
                    deletionRequest,
                    CALLBACK_EXECUTOR,
                    (AdServicesOutcomeReceiver<Object, Exception>)
                            result -> countDownLatch.countDown());
            assertThat(countDownLatch.await(TIMEOUT_IN_MS, TimeUnit.MILLISECONDS)).isTrue();
        } catch (InterruptedException e) {
            throw new IllegalStateException("Error while deleting registrations", e);
        }
    }

    private RecordedRequest takeRequestTimeoutWrapper(MockWebServer mockWebServer) {
        ExecutorService executor = Executors.newSingleThreadExecutor();
        Future<RecordedRequest> future = executor.submit(mockWebServer::takeRequest);
        try {
            return future.get(60, TimeUnit.SECONDS);
        } catch (TimeoutException | InterruptedException | ExecutionException e) {
            throw new IllegalStateException("Error while running mockWebServer.takeRequest()", e);
        } finally {
            future.cancel(true);
        }
    }

    private void setFlagsForMeasurement() {
        flags.setFlag(KEY_MEASUREMENT_KILL_SWITCH, false)
                .setMsmtApiAppAllowList(mPackageName)
                .setFlag(KEY_MEASUREMENT_REGISTRATION_JOB_QUEUE_KILL_SWITCH, false)
                .setFlag(KEY_MEASUREMENT_ENFORCE_FOREGROUND_STATUS_REGISTER_SOURCE, false)
                .setFlag(KEY_MEASUREMENT_ENFORCE_FOREGROUND_STATUS_REGISTER_TRIGGER, false)
                .setFlag(
                        KEY_MEASUREMENT_AGGREGATION_COORDINATOR_ORIGIN_LIST,
                        AGGREGATE_ENCRYPTION_KEY_COORDINATOR_ORIGIN)
                .setFlag(KEY_WEB_CONTEXT_CLIENT_ALLOW_LIST, mPackageName)
                .setFlag(
                        KEY_MEASUREMENT_AGGREGATION_COORDINATOR_ORIGIN_LIST,
                        AGGREGATE_ENCRYPTION_KEY_COORDINATOR_ORIGIN)
                .setFlag(
                        KEY_MEASUREMENT_DEFAULT_AGGREGATION_COORDINATOR_ORIGIN,
                        AGGREGATE_ENCRYPTION_KEY_COORDINATOR_ORIGIN)
                .setFlag(
                        KEY_MEASUREMENT_AGGREGATION_COORDINATOR_PATH,
                        AGGREGATE_ENCRYPTION_KEY_COORDINATOR_PATH)
                .setFlag(KEY_MEASUREMENT_EVENT_REPORTS_VTC_EARLY_REPORTING_WINDOWS, "8,15")
                .setFlag(KEY_MEASUREMENT_AGGREGATE_REPORT_DELAY_CONFIG, "0,0")
                .setFlag(KEY_MEASUREMENT_NULL_AGG_REPORT_RATE_EXCL_SOURCE_REGISTRATION_TIME, "0.0")
                .setFlag(KEY_MEASUREMENT_NULL_AGG_REPORT_RATE_INCL_SOURCE_REGISTRATION_TIME, "0.0");

        sleep();
    }
}

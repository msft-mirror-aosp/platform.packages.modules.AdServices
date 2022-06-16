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
package com.android.adservices.service.measurement.registration;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertTrue;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.doReturn;
import static org.mockito.Mockito.doThrow;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import android.adservices.measurement.RegistrationRequest;
import android.content.Context;
import android.net.Uri;

import androidx.test.InstrumentationRegistry;
import androidx.test.filters.SmallTest;

import org.junit.Before;
import org.junit.Test;
import org.mockito.Mock;
import org.mockito.MockitoAnnotations;
import org.mockito.Spy;

import java.io.IOException;
import java.net.URL;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

import javax.net.ssl.HttpsURLConnection;


/**
 * Unit tests for {@link TriggerFetcher}
 */
@SmallTest
public final class TriggerFetcherTest {
    private static final String TRIGGER_URI = "https://foo.com";
    private static final String TOP_ORIGIN = "https://baz.com";
    private static final long TRIGGER_DATA = 7;
    private static final long PRIORITY = 1;
    private static final long DEDUP_KEY = 100;

    private static final String DEFAULT_REDIRECT = "https://bar.com";

    private static final String EVENT_TRIGGERS =
            "[\n"
                    + "{\n"
                    + "  \"trigger_data\": \""
                    + TRIGGER_DATA
                    + "\",\n"
                    + "  \"priority\": \""
                    + PRIORITY
                    + "\",\n"
                    + "  \"deduplication_key\": \""
                    + DEDUP_KEY
                    + "\",\n"
                    + "  \"filters\": {\n"
                    + "    \"source_type\": [\"navigation\"],\n"
                    + "    \"key_1\": [\"value_1\"] \n"
                    + "   }\n"
                    + "}"
                    + "]\n";

    private static final Context sContext = InstrumentationRegistry.getTargetContext();

    @Spy TriggerFetcher mFetcher;
    @Mock HttpsURLConnection mUrlConnection;

    @Before
    public void setUp() throws Exception {
        MockitoAnnotations.initMocks(this);
    }

    @Test
    public void testBasicTriggerRequest() throws Exception {
        RegistrationRequest request = buildRequest(TRIGGER_URI, TOP_ORIGIN);
        doReturn(mUrlConnection).when(mFetcher).openUrl(new URL(TRIGGER_URI));
        when(mUrlConnection.getResponseCode()).thenReturn(200);
        when(mUrlConnection.getHeaderFields())
                .thenReturn(
                        Map.of(
                                "Attribution-Reporting-Register-Event-Trigger",
                                List.of(EVENT_TRIGGERS)));
        ArrayList<TriggerRegistration> result = new ArrayList();
        assertTrue(mFetcher.fetchTrigger(request, result));
        assertEquals(1, result.size());
        assertEquals(TOP_ORIGIN, result.get(0).getTopOrigin().toString());
        assertEquals(TRIGGER_URI, result.get(0).getReportingOrigin().toString());
        assertEquals(EVENT_TRIGGERS, result.get(0).getEventTriggers());
        verify(mUrlConnection).setRequestMethod("POST");
    }

    @Test
    public void testBadTriggerUrl() throws Exception {
        RegistrationRequest request =
                buildRequest("bad-schema://foo.com", TOP_ORIGIN);
        ArrayList<TriggerRegistration> result = new ArrayList();
        assertFalse(mFetcher.fetchTrigger(request, result));
        assertEquals(0, result.size());
    }

    @Test
    public void testBadTriggerConnection() throws Exception {
        RegistrationRequest request = buildRequest(TRIGGER_URI, TOP_ORIGIN);
        doThrow(new IOException("Bad internet things"))
                .when(mFetcher).openUrl(new URL(TRIGGER_URI));
        ArrayList<TriggerRegistration> result = new ArrayList();
        assertFalse(mFetcher.fetchTrigger(request, result));
        assertEquals(0, result.size());
        verify(mUrlConnection, never()).setRequestMethod("POST");
    }

    @Test
    public void testBadRequestReturnFailure() throws Exception {
        RegistrationRequest request = buildRequest(TRIGGER_URI, TOP_ORIGIN);
        doReturn(mUrlConnection).when(mFetcher).openUrl(new URL(TRIGGER_URI));
        when(mUrlConnection.getResponseCode()).thenReturn(400);
        when(mUrlConnection.getHeaderFields())
                .thenReturn(Map.of("Attribution-Reporting-Register-Event-Trigger",
                        List.of("[{\n"
                                + "  \"trigger_data\": \"" + TRIGGER_DATA + "\",\n"
                                + "  \"priority\": \"" + PRIORITY + "\",\n"
                                + "  \"deduplication_key\": \"" + DEDUP_KEY + "\"\n"
                                + "}]\n")));
        ArrayList<TriggerRegistration> result = new ArrayList();
        assertFalse(mFetcher.fetchTrigger(request, result));
        assertEquals(0, result.size());
        verify(mUrlConnection).setRequestMethod("POST");
    }

    @Test
    public void testBasicTriggerRequestMinimumFields() throws Exception {
        RegistrationRequest request = buildRequest(TRIGGER_URI, TOP_ORIGIN);
        doReturn(mUrlConnection).when(mFetcher).openUrl(new URL(TRIGGER_URI));
        when(mUrlConnection.getResponseCode()).thenReturn(200);
        when(mUrlConnection.getHeaderFields())
                .thenReturn(Map.of("Attribution-Reporting-Register-Event-Trigger",
                        List.of("[{}]\n")));
        ArrayList<TriggerRegistration> result = new ArrayList<>();
        assertTrue(mFetcher.fetchTrigger(request, result));
        assertEquals(1, result.size());
        assertEquals(TOP_ORIGIN, result.get(0).getTopOrigin().toString());
        assertEquals(TRIGGER_URI, result.get(0).getReportingOrigin().toString());
        assertEquals("[{}]\n", result.get(0).getEventTriggers());
        verify(mUrlConnection).setRequestMethod("POST");
    }

    @Test
    public void testNotOverHttps() throws Exception {
        RegistrationRequest request = buildRequest("http://foo.com", TOP_ORIGIN);
        // Non-https should fail.
        ArrayList<TriggerRegistration> result = new ArrayList();
        assertFalse(mFetcher.fetchTrigger(request, result));
        assertEquals(0, result.size());
    }

    @Test
    public void testFirst200Next500_ignoreFailureReturnSuccess() throws Exception {
        RegistrationRequest request = buildRequest(TRIGGER_URI, TOP_ORIGIN);
        doReturn(mUrlConnection).when(mFetcher).openUrl(any(URL.class));
        when(mUrlConnection.getResponseCode()).thenReturn(200).thenReturn(500);

        Map<String, List<String>> headersFirstRequest = new HashMap<>();
        headersFirstRequest.put(
                "Attribution-Reporting-Register-Event-Trigger", List.of(EVENT_TRIGGERS));
        headersFirstRequest.put("Attribution-Reporting-Redirect", List.of(DEFAULT_REDIRECT));

        when(mUrlConnection.getHeaderFields()).thenReturn(headersFirstRequest);

        ArrayList<TriggerRegistration> result = new ArrayList<>();
        assertTrue(mFetcher.fetchTrigger(request, result));
        assertEquals(1, result.size());
        assertEquals(TOP_ORIGIN, result.get(0).getTopOrigin().toString());
        assertEquals(TRIGGER_URI, result.get(0).getReportingOrigin().toString());
        assertEquals(EVENT_TRIGGERS, result.get(0).getEventTriggers());
        verify(mUrlConnection, times(2)).setRequestMethod("POST");
    }

    @Test
    public void testMissingHeaderButWithRedirect() throws Exception {
        RegistrationRequest request = buildRequest(TRIGGER_URI, TOP_ORIGIN);
        doReturn(mUrlConnection).when(mFetcher).openUrl(any(URL.class));
        when(mUrlConnection.getResponseCode()).thenReturn(200);
        when(mUrlConnection.getHeaderFields())
                .thenReturn(Map.of("Attribution-Reporting-Redirect", List.of(DEFAULT_REDIRECT)))
                .thenReturn(
                        Map.of(
                                "Attribution-Reporting-Register-Event-Trigger",
                                List.of(EVENT_TRIGGERS)));
        ArrayList<TriggerRegistration> result = new ArrayList();
        assertFalse(mFetcher.fetchTrigger(request, result));
        assertEquals(0, result.size());
        verify(mUrlConnection, times(1)).setRequestMethod("POST");
    }

    @Test
    public void testBasicTriggerRequestWithAggregateTriggerData() throws Exception {
        RegistrationRequest request = buildRequest(TRIGGER_URI, TOP_ORIGIN);
        doReturn(mUrlConnection).when(mFetcher).openUrl(new URL(TRIGGER_URI));
        when(mUrlConnection.getResponseCode()).thenReturn(200);
        when(mUrlConnection.getHeaderFields())
                .thenReturn(Map.of("Attribution-Reporting-Register-Aggregatable-Trigger-Data",
                        List.of("[{\"key_piece\":\"0x400\",\"source_keys\":[\"campaignCounts\"],"
                                + "\"filters\":"
                                + "{\"conversion_subdomain\":[\"electronics.megastore\"]},"
                                + "\"not_filters\":{\"product\":[\"1\"]}},"
                                + "{\"key_piece\":\"0xA80\",\"source_keys\":[\"geoValue\"]}]")));
        ArrayList<TriggerRegistration> result = new ArrayList<>();
        assertTrue(mFetcher.fetchTrigger(request, result));
        assertEquals(1, result.size());
        assertEquals("https://baz.com", result.get(0).getTopOrigin().toString());
        assertEquals("https://foo.com", result.get(0).getReportingOrigin().toString());
        assertEquals(
                "[{\"key_piece\":\"0x400\",\"source_keys\":[\"campaignCounts\"],"
                        + "\"filters\":{\"conversion_subdomain\":[\"electronics.megastore\"]},"
                        + "\"not_filters\":{\"product\":[\"1\"]}},"
                        + "{\"key_piece\":\"0xA80\",\"source_keys\":[\"geoValue\"]}]",
                result.get(0).getAggregateTriggerData());
        verify(mUrlConnection).setRequestMethod("POST");
    }

    @Test
    public void testBasicTriggerRequestWithAggregateValues() throws Exception {
        RegistrationRequest request = buildRequest(TRIGGER_URI, TOP_ORIGIN);
        doReturn(mUrlConnection).when(mFetcher).openUrl(new URL(TRIGGER_URI));
        when(mUrlConnection.getResponseCode()).thenReturn(200);
        when(mUrlConnection.getHeaderFields())
                .thenReturn(Map.of("Attribution-Reporting-Register-Aggregatable-Values",
                        List.of("{\"campaignCounts\":32768,\"geoValue\":1644}")));
        ArrayList<TriggerRegistration> result = new ArrayList<>();
        assertTrue(mFetcher.fetchTrigger(request, result));
        assertEquals(1, result.size());
        assertEquals("https://baz.com", result.get(0).getTopOrigin().toString());
        assertEquals("https://foo.com", result.get(0).getReportingOrigin().toString());
        assertEquals("{\"campaignCounts\":32768,\"geoValue\":1644}",
                result.get(0).getAggregateValues());
        verify(mUrlConnection).setRequestMethod("POST");
    }

    @Test
    public void testReportingFilters() throws IOException {
        // Setup
        RegistrationRequest request = buildRequest(TRIGGER_URI, TOP_ORIGIN);
        doReturn(mUrlConnection).when(mFetcher).openUrl(new URL(TRIGGER_URI));
        when(mUrlConnection.getResponseCode()).thenReturn(200);
        when(mUrlConnection.getHeaderFields())
                .thenReturn(
                        Map.of(
                                "Attribution-Reporting-Filters",
                                List.of(
                                        "{\n"
                                                + "  \"key_1\": [\"value_1\", \"value_2\"],\n"
                                                + "  \"key_2\": [\"value_1\", \"value_2\"]\n"
                                                + "}")));

        // Execution
        ArrayList<TriggerRegistration> result = new ArrayList<>();
        boolean success = mFetcher.fetchTrigger(request, result);

        // Assertion
        assertTrue(success);
        assertEquals(1, result.size());
        assertEquals("https://baz.com", result.get(0).getTopOrigin().toString());
        assertEquals("https://foo.com", result.get(0).getReportingOrigin().toString());
        assertEquals(
                "{\n"
                        + "  \"key_1\": [\"value_1\", \"value_2\"],\n"
                        + "  \"key_2\": [\"value_1\", \"value_2\"]\n"
                        + "}",
                result.get(0).getFilters());
        verify(mUrlConnection).setRequestMethod("POST");
    }

    private RegistrationRequest buildRequest(String triggerUri, String topOriginUri) {
        return new RegistrationRequest.Builder()
                .setRegistrationType(RegistrationRequest.REGISTER_TRIGGER)
                .setRegistrationUri(Uri.parse(triggerUri))
                .setTopOriginUri(Uri.parse(topOriginUri))
                .setAttributionSource(sContext.getAttributionSource())
                .build();
    }
}

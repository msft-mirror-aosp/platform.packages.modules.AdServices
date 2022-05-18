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

import static com.android.adservices.service.measurement.PrivacyParams.MAX_REPORTING_REGISTER_SOURCE_EXPIRATION_IN_SECONDS;
import static com.android.adservices.service.measurement.PrivacyParams.MIN_REPORTING_REGISTER_SOURCE_EXPIRATION_IN_SECONDS;

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
import java.util.Collections;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

import javax.net.ssl.HttpsURLConnection;


/**
 * Unit tests for {@link SourceFetcher}
 */
@SmallTest
public final class SourceFetcherTest {
    private static final String DEFAULT_REGISTRATION = "https://foo.com";
    private static final String DEFAULT_TOP_ORIGIN = "https://baz.com";
    private static final String DEFAULT_DESTINATION = "android-app://com.myapps";
    private static final long DEFAULT_PRIORITY = 123;
    private static final long DEFAULT_EXPIRY = 456789;
    private static final long DEFAULT_EVENT_ID = 987654321;
    private static final String ALT_REGISTRATION = "https://bar.com";
    private static final String ALT_DESTINATION = "android-app://com.yourapps";
    private static final long ALT_PRIORITY = 321;
    private static final long ALT_EVENT_ID = 123456789;
    private static final long ALT_EXPIRY = 456790;

    private static final Context sContext = InstrumentationRegistry.getTargetContext();

    @Spy SourceFetcher mFetcher;
    @Mock HttpsURLConnection mUrlConnection;

    @Before
    public void setUp() throws Exception {
        MockitoAnnotations.initMocks(this);
    }

    @Test
    public void testBasicSourceRequest() throws Exception {
        RegistrationRequest request = buildRequest(DEFAULT_REGISTRATION, DEFAULT_TOP_ORIGIN);
        doReturn(mUrlConnection).when(mFetcher).openUrl(new URL(DEFAULT_REGISTRATION));
        when(mUrlConnection.getResponseCode()).thenReturn(200);
        when(mUrlConnection.getHeaderFields()).thenReturn(Map.of(
                "Attribution-Reporting-Register-Source",
                List.of("{\n"
                        + "  \"destination\": \"" + DEFAULT_DESTINATION + "\",\n"
                        + "  \"priority\": \"" + DEFAULT_PRIORITY + "\",\n"
                        + "  \"expiry\": \"" + DEFAULT_EXPIRY + "\",\n"
                        + "  \"source_event_id\": \"" + DEFAULT_EVENT_ID + "\"\n"
                        + "}\n")));

        ArrayList<SourceRegistration> result = new ArrayList();
        assertTrue(mFetcher.fetchSource(request, result));
        assertEquals(1, result.size());
        assertEquals(DEFAULT_TOP_ORIGIN, result.get(0).getTopOrigin().toString());
        assertEquals(DEFAULT_REGISTRATION, result.get(0).getReportingOrigin().toString());
        assertEquals(DEFAULT_DESTINATION, result.get(0).getDestination().toString());
        assertEquals(DEFAULT_PRIORITY, result.get(0).getSourcePriority());
        assertEquals(DEFAULT_EXPIRY, result.get(0).getExpiry());
        assertEquals(DEFAULT_EVENT_ID, result.get(0).getSourceEventId());
        verify(mUrlConnection).setRequestMethod("POST");
    }

    @Test
    public void testSourceRequestWithPostInstallAttributes() throws Exception {
        RegistrationRequest request = new RegistrationRequest.Builder()
                .setRegistrationType(RegistrationRequest.REGISTER_SOURCE)
                .setRegistrationUri(Uri.parse("https://foo.com"))
                .setTopOriginUri(Uri.parse("https://baz.com"))
                .setAttributionSource(sContext.getAttributionSource())
                .build();
        doReturn(mUrlConnection).when(mFetcher).openUrl(new URL("https://foo.com"));
        when(mUrlConnection.getResponseCode()).thenReturn(200);
        when(mUrlConnection.getHeaderFields())
                .thenReturn(Map.of("Attribution-Reporting-Register-Source",
                        List.of("{\n"
                                + "  \"destination\": \"android-app://com.myapps\",\n"
                                + "  \"priority\": \"123\",\n"
                                + "  \"expiry\": \"456789\",\n"
                                + "  \"source_event_id\": \"987654321\",\n"
                                + "  \"install_attribution_window\": \"272800\",\n"
                                + "  \"install_cooldown_window\": \"987654\"\n"
                                + "}\n")));
        ArrayList<SourceRegistration> result = new ArrayList();
        assertTrue(mFetcher.fetchSource(request, result));
        assertEquals(1, result.size());
        assertEquals("https://baz.com", result.get(0).getTopOrigin().toString());
        assertEquals("https://foo.com", result.get(0).getReportingOrigin().toString());
        assertEquals("android-app://com.myapps", result.get(0).getDestination().toString());
        assertEquals(123, result.get(0).getSourcePriority());
        assertEquals(456789, result.get(0).getExpiry());
        assertEquals(987654321, result.get(0).getSourceEventId());
        assertEquals(272800, result.get(0).getInstallAttributionWindow());
        assertEquals(987654L, result.get(0).getInstallCooldownWindow());
        verify(mUrlConnection).setRequestMethod("POST");
    }

    @Test
    public void testSourceRequestWithPostInstallAttributesReceivedAsNull() throws Exception {
        RegistrationRequest request = new RegistrationRequest.Builder()
                .setRegistrationType(RegistrationRequest.REGISTER_SOURCE)
                .setRegistrationUri(Uri.parse("https://foo.com"))
                .setTopOriginUri(Uri.parse("https://baz.com"))
                .setAttributionSource(sContext.getAttributionSource())
                .build();
        doReturn(mUrlConnection).when(mFetcher).openUrl(new URL("https://foo.com"));
        when(mUrlConnection.getResponseCode()).thenReturn(200);
        when(mUrlConnection.getHeaderFields())
                .thenReturn(Map.of("Attribution-Reporting-Register-Source",
                        List.of("{\n"
                                + "  \"destination\": \"android-app://com.myapps\",\n"
                                + "  \"priority\": \"123\",\n"
                                + "  \"expiry\": \"456789\",\n"
                                + "  \"source_event_id\": \"987654321\",\n"
                                + "  \"install_attribution_window\": null,\n"
                                + "  \"install_cooldown_window\": null\n"
                                + "}\n")));
        ArrayList<SourceRegistration> result = new ArrayList();
        assertTrue(mFetcher.fetchSource(request, result));
        assertEquals(1, result.size());
        assertEquals("https://baz.com", result.get(0).getTopOrigin().toString());
        assertEquals("https://foo.com", result.get(0).getReportingOrigin().toString());
        assertEquals("android-app://com.myapps", result.get(0).getDestination().toString());
        assertEquals(123, result.get(0).getSourcePriority());
        assertEquals(456789, result.get(0).getExpiry());
        assertEquals(987654321, result.get(0).getSourceEventId());
        // fallback to default value - 30 days
        assertEquals(2592000L, result.get(0).getInstallAttributionWindow());
        // fallback to default value - 0 days
        assertEquals(0L, result.get(0).getInstallCooldownWindow());
        verify(mUrlConnection).setRequestMethod("POST");
    }

    @Test
    public void testSourceRequestWithInstallAttributesOutofBounds() throws IOException {
        RegistrationRequest request = new RegistrationRequest.Builder()
                .setRegistrationType(RegistrationRequest.REGISTER_SOURCE)
                .setRegistrationUri(Uri.parse("https://foo.com"))
                .setTopOriginUri(Uri.parse("https://baz.com"))
                .setAttributionSource(sContext.getAttributionSource())
                .build();
        doReturn(mUrlConnection).when(mFetcher).openUrl(new URL("https://foo.com"));
        when(mUrlConnection.getResponseCode()).thenReturn(200);
        when(mUrlConnection.getHeaderFields())
                .thenReturn(Map.of("Attribution-Reporting-Register-Source",
                        List.of("{\n"
                                + "  \"destination\": \"android-app://com.myapps\",\n"
                                + "  \"priority\": \"123\",\n"
                                + "  \"expiry\": \"456789\",\n"
                                + "  \"source_event_id\": \"987654321\",\n"
                                // Min value of attribution is 2 days or 172800 seconds
                                + "  \"install_attribution_window\": \"172700\",\n"
                                // Max value of cooldown is 30 days or 2592000 seconds
                                + "  \"install_cooldown_window\": \"9876543210\"\n"
                                + "}\n")));
        ArrayList<SourceRegistration> result = new ArrayList();
        assertTrue(mFetcher.fetchSource(request, result));
        assertEquals(1, result.size());
        assertEquals("https://baz.com", result.get(0).getTopOrigin().toString());
        assertEquals("https://foo.com", result.get(0).getReportingOrigin().toString());
        assertEquals("android-app://com.myapps", result.get(0).getDestination().toString());
        assertEquals(123, result.get(0).getSourcePriority());
        assertEquals(456789, result.get(0).getExpiry());
        assertEquals(987654321, result.get(0).getSourceEventId());
        // Adjusted to minimum allowed value
        assertEquals(172800, result.get(0).getInstallAttributionWindow());
        // Adjusted to maximum allowed value
        assertEquals(2592000L, result.get(0).getInstallCooldownWindow());
        verify(mUrlConnection).setRequestMethod("POST");
    }

    @Test
    public void testBadSourceUrl() throws Exception {
        RegistrationRequest request = buildRequest(
                /* registrationUri = */ "bad-schema://foo.com", DEFAULT_TOP_ORIGIN);
        ArrayList<SourceRegistration> result = new ArrayList();
        assertFalse(mFetcher.fetchSource(request, result));
        assertEquals(0, result.size());
    }

    @Test
    public void testBadSourceConnection() throws Exception {
        RegistrationRequest request = buildRequest(DEFAULT_REGISTRATION, DEFAULT_TOP_ORIGIN);
        doThrow(new IOException("Bad internet things")).when(mFetcher).openUrl(
                new URL(DEFAULT_REGISTRATION)
        );
        ArrayList<SourceRegistration> result = new ArrayList();
        assertFalse(mFetcher.fetchSource(request, result));
        assertEquals(0, result.size());
    }

    @Test
    public void testBadSourceJson_missingSourceEventId() throws Exception {
        RegistrationRequest request = buildRequest(DEFAULT_REGISTRATION, DEFAULT_TOP_ORIGIN);
        doReturn(mUrlConnection).when(mFetcher).openUrl(new URL(DEFAULT_REGISTRATION));
        when(mUrlConnection.getResponseCode()).thenReturn(200);
        when(mUrlConnection.getHeaderFields())
                .thenReturn(Map.of("Attribution-Reporting-Register-Source",
                        List.of("{\n"
                                + "\"source_event_id\": \"" + DEFAULT_EVENT_ID + "\"")));
        ArrayList<SourceRegistration> result = new ArrayList();
        assertFalse(mFetcher.fetchSource(request, result));
        assertEquals(0, result.size());
        verify(mUrlConnection).setRequestMethod("POST");
    }

    @Test
    public void testBadSourceJson_missingHeader() throws Exception {
        RegistrationRequest request = buildRequest(DEFAULT_REGISTRATION, DEFAULT_TOP_ORIGIN);
        doReturn(mUrlConnection).when(mFetcher).openUrl(new URL(DEFAULT_REGISTRATION));
        when(mUrlConnection.getResponseCode()).thenReturn(200);
        when(mUrlConnection.getHeaderFields()).thenReturn(Collections.emptyMap());
        ArrayList<SourceRegistration> result = new ArrayList();
        assertFalse(mFetcher.fetchSource(request, result));
        assertEquals(0, result.size());
        verify(mUrlConnection).setRequestMethod("POST");
    }

    @Test
    public void testBadSourceJson_missingDestination() throws Exception {
        RegistrationRequest request = buildRequest(DEFAULT_REGISTRATION, DEFAULT_TOP_ORIGIN);
        doReturn(mUrlConnection).when(mFetcher).openUrl(new URL(DEFAULT_REGISTRATION));
        when(mUrlConnection.getResponseCode()).thenReturn(200);
        when(mUrlConnection.getHeaderFields())
                .thenReturn(Map.of("Attribution-Reporting-Register-Source",
                        List.of("{\n"
                                + "\"destination\": \"" + DEFAULT_DESTINATION + "\"")));
        ArrayList<SourceRegistration> result = new ArrayList();
        assertFalse(mFetcher.fetchSource(request, result));
        assertEquals(0, result.size());
        verify(mUrlConnection).setRequestMethod("POST");
    }

    @Test
    public void testBasicSourceRequestMinimumFields() throws Exception {
        RegistrationRequest request = buildRequest(DEFAULT_REGISTRATION, DEFAULT_TOP_ORIGIN);
        doReturn(mUrlConnection).when(mFetcher).openUrl(new URL(DEFAULT_REGISTRATION));
        when(mUrlConnection.getResponseCode()).thenReturn(200);
        when(mUrlConnection.getHeaderFields())
                .thenReturn(Map.of("Attribution-Reporting-Register-Source",
                        List.of("{\n"
                                + "\"destination\": \"" + DEFAULT_DESTINATION + "\",\n"
                                + "\"source_event_id\": \"" + DEFAULT_EVENT_ID + "\"\n"
                                + "}\n")));
        ArrayList<SourceRegistration> result = new ArrayList();
        assertTrue(mFetcher.fetchSource(request, result));
        assertEquals(1, result.size());
        assertEquals(DEFAULT_TOP_ORIGIN, result.get(0).getTopOrigin().toString());
        assertEquals(DEFAULT_REGISTRATION, result.get(0).getReportingOrigin().toString());
        assertEquals(DEFAULT_DESTINATION, result.get(0).getDestination().toString());
        assertEquals(DEFAULT_EVENT_ID, result.get(0).getSourceEventId());
        assertEquals(0, result.get(0).getSourcePriority());
        assertEquals(
                MAX_REPORTING_REGISTER_SOURCE_EXPIRATION_IN_SECONDS, result.get(0).getExpiry());
        verify(mUrlConnection).setRequestMethod("POST");
    }

    @Test
    public void testBasicSourceRequestMinimumFieldsAndRestNull() throws Exception {
        RegistrationRequest request = new RegistrationRequest.Builder()
                .setRegistrationType(RegistrationRequest.REGISTER_SOURCE)
                .setRegistrationUri(Uri.parse("https://foo.com"))
                .setTopOriginUri(Uri.parse("https://baz.com"))
                .setAttributionSource(sContext.getAttributionSource())
                .build();
        doReturn(mUrlConnection).when(mFetcher).openUrl(new URL("https://foo.com"));
        when(mUrlConnection.getResponseCode()).thenReturn(200);
        when(mUrlConnection.getHeaderFields())
                .thenReturn(Map.of("Attribution-Reporting-Register-Source",
                        List.of("{\n"
                                + "\"destination\": \"android-app://com.myapps\",\n"
                                + "\"source_event_id\": \"123\",\n"
                                + "\"priority\": null,\n"
                                + "\"expiry\": null\n"
                                + "}\n")));
        ArrayList<SourceRegistration> result = new ArrayList();
        assertTrue(mFetcher.fetchSource(request, result));
        assertEquals(1, result.size());
        assertEquals("https://baz.com", result.get(0).getTopOrigin().toString());
        assertEquals("https://foo.com", result.get(0).getReportingOrigin().toString());
        assertEquals("android-app://com.myapps", result.get(0).getDestination().toString());
        assertEquals(123, result.get(0).getSourceEventId());
        assertEquals(0, result.get(0).getSourcePriority());
        assertEquals(MAX_REPORTING_REGISTER_SOURCE_EXPIRATION_IN_SECONDS,
                result.get(0).getExpiry());
        verify(mUrlConnection).setRequestMethod("POST");
    }

    @Test
    public void testBasicSourceRequestWithExpiryLessThan2Days() throws Exception {
        RegistrationRequest request = buildRequest(DEFAULT_REGISTRATION, DEFAULT_TOP_ORIGIN);
        doReturn(mUrlConnection).when(mFetcher).openUrl(new URL(DEFAULT_REGISTRATION));
        when(mUrlConnection.getResponseCode()).thenReturn(200);
        when(mUrlConnection.getHeaderFields())
                .thenReturn(Map.of("Attribution-Reporting-Register-Source",
                        List.of("{\n"
                                + "\"destination\": \"" + DEFAULT_DESTINATION + "\",\n"
                                + "\"source_event_id\": \"" + DEFAULT_EVENT_ID + "\",\n"
                                + "\"expiry\": 1"
                                + "}\n")));
        ArrayList<SourceRegistration> result = new ArrayList();
        assertTrue(mFetcher.fetchSource(request, result));
        assertEquals(1, result.size());
        assertEquals(DEFAULT_TOP_ORIGIN, result.get(0).getTopOrigin().toString());
        assertEquals(DEFAULT_REGISTRATION, result.get(0).getReportingOrigin().toString());
        assertEquals(DEFAULT_DESTINATION, result.get(0).getDestination().toString());
        assertEquals(DEFAULT_EVENT_ID, result.get(0).getSourceEventId());
        assertEquals(0, result.get(0).getSourcePriority());
        assertEquals(
                MIN_REPORTING_REGISTER_SOURCE_EXPIRATION_IN_SECONDS, result.get(0).getExpiry());
        verify(mUrlConnection).setRequestMethod("POST");
    }

    @Test
    public void testBasicSourceRequestWithExpiryMoreThan30Days() throws Exception {
        RegistrationRequest request = buildRequest(DEFAULT_REGISTRATION, DEFAULT_TOP_ORIGIN);
        doReturn(mUrlConnection).when(mFetcher).openUrl(new URL(DEFAULT_REGISTRATION));
        when(mUrlConnection.getResponseCode()).thenReturn(200);
        when(mUrlConnection.getHeaderFields())
                .thenReturn(Map.of("Attribution-Reporting-Register-Source",
                        List.of("{\n"
                                + "\"destination\": \"" + DEFAULT_DESTINATION + "\",\n"
                                + "\"source_event_id\": \"" + DEFAULT_EVENT_ID + "\",\n"
                                + "\"expiry\": 2678400"
                                + "}\n")));
        ArrayList<SourceRegistration> result = new ArrayList();
        assertTrue(mFetcher.fetchSource(request, result));
        assertEquals(1, result.size());
        assertEquals(DEFAULT_TOP_ORIGIN, result.get(0).getTopOrigin().toString());
        assertEquals(DEFAULT_REGISTRATION, result.get(0).getReportingOrigin().toString());
        assertEquals(DEFAULT_DESTINATION, result.get(0).getDestination().toString());
        assertEquals(DEFAULT_EVENT_ID, result.get(0).getSourceEventId());
        assertEquals(0, result.get(0).getSourcePriority());
        assertEquals(
                MAX_REPORTING_REGISTER_SOURCE_EXPIRATION_IN_SECONDS, result.get(0).getExpiry());
        verify(mUrlConnection).setRequestMethod("POST");
    }

    @Test
    public void testNotOverHttps() throws Exception {
        RegistrationRequest request = buildRequest("http://foo.com", DEFAULT_TOP_ORIGIN);

        ArrayList<SourceRegistration> result = new ArrayList();
        assertFalse(mFetcher.fetchSource(request, result));
        assertEquals(0, result.size());
        verify(mFetcher, never()).openUrl(any());
    }

    @Test
    public void testFirst200Next500_ignoreFailureReturnSuccess() throws Exception {
        RegistrationRequest request = buildRequest(DEFAULT_REGISTRATION, DEFAULT_TOP_ORIGIN);
        doReturn(mUrlConnection).when(mFetcher).openUrl(any(URL.class));
        when(mUrlConnection.getResponseCode()).thenReturn(200).thenReturn(500);

        Map<String, List<String>> headersFirstRequest = new HashMap<>();
        headersFirstRequest.put("Attribution-Reporting-Register-Source", List.of("{\n"
                + "\"destination\": \"" + DEFAULT_DESTINATION + "\",\n"
                + "\"source_event_id\": \"" + DEFAULT_EVENT_ID + "\",\n"
                + "\"expiry\": " + DEFAULT_EXPIRY + ""
                + "}\n"));
        headersFirstRequest.put("Attribution-Reporting-Redirect", List.of("https://bar.com"));

        Map<String, List<String>> headersSecondRequest = new HashMap<>();
        headersSecondRequest.put("Attribution-Reporting-Register-Source", List.of("{\n"
                + "\"destination\": \"" + DEFAULT_DESTINATION + "\",\n"
                + "\"source_event_id\": \"" + ALT_EVENT_ID + "\",\n"
                + "\"expiry\": " + ALT_EXPIRY + ""
                + "}\n"));

        when(mUrlConnection.getHeaderFields()).thenReturn(headersFirstRequest).thenReturn(
                headersSecondRequest);

        ArrayList<SourceRegistration> result = new ArrayList();
        assertTrue(mFetcher.fetchSource(request, result));
        assertEquals(1, result.size());
        assertEquals(DEFAULT_TOP_ORIGIN, result.get(0).getTopOrigin().toString());
        assertEquals(DEFAULT_REGISTRATION, result.get(0).getReportingOrigin().toString());
        assertEquals(DEFAULT_DESTINATION, result.get(0).getDestination().toString());
        assertEquals(DEFAULT_EVENT_ID, result.get(0).getSourceEventId());
        assertEquals(0, result.get(0).getSourcePriority());
        assertEquals(DEFAULT_EXPIRY, result.get(0).getExpiry());
        verify(mUrlConnection, times(2)).setRequestMethod("POST");
    }

    @Test
    public void testFailedParsingButValidRedirect_returnFailure() throws Exception {
        RegistrationRequest request = buildRequest(DEFAULT_REGISTRATION, DEFAULT_TOP_ORIGIN);
        doReturn(mUrlConnection).when(mFetcher).openUrl(any(URL.class));
        when(mUrlConnection.getResponseCode()).thenReturn(200);

        Map<String, List<String>> headersFirstRequest = new HashMap<>();
        headersFirstRequest.put("Attribution-Reporting-Register-Source", List.of("{}"));
        headersFirstRequest.put("Attribution-Reporting-Redirect", List.of("https://bar.com"));

        Map<String, List<String>> headersSecondRequest = new HashMap<>();
        headersSecondRequest.put("Attribution-Reporting-Register-Source", List.of("{\n"
                + "\"destination\": \"" + DEFAULT_DESTINATION + "\",\n"
                + "\"source_event_id\": \"" + ALT_EVENT_ID + "\",\n"
                + "\"expiry\": " + ALT_EXPIRY + ""
                + "}\n"));

        when(mUrlConnection.getHeaderFields()).thenReturn(headersFirstRequest).thenReturn(
                headersSecondRequest);

        ArrayList<SourceRegistration> result = new ArrayList();
        assertFalse(mFetcher.fetchSource(request, result));
        assertEquals(0, result.size());
        verify(mUrlConnection, times(1)).setRequestMethod("POST");
    }

    @Test
    public void testRedirectDifferentDestination_keepAllReturnSuccess() throws Exception {
        RegistrationRequest request = buildRequest(DEFAULT_REGISTRATION, DEFAULT_TOP_ORIGIN);
        doReturn(mUrlConnection).when(mFetcher).openUrl(any(URL.class));
        when(mUrlConnection.getResponseCode()).thenReturn(200);

        Map<String, List<String>> headersFirstRequest = new HashMap<>();
        headersFirstRequest.put("Attribution-Reporting-Register-Source", List.of("{\n"
                + "\"destination\": \"" + DEFAULT_DESTINATION + "\",\n"
                + "\"source_event_id\": \"" + DEFAULT_EVENT_ID + "\",\n"
                + "\"priority\": \"" + DEFAULT_PRIORITY + "\",\n"
                + "\"expiry\": " + DEFAULT_EXPIRY + ""
                + "}\n"));
        headersFirstRequest.put("Attribution-Reporting-Redirect", List.of(ALT_REGISTRATION));

        Map<String, List<String>> headersSecondRequest = new HashMap<>();
        headersSecondRequest.put("Attribution-Reporting-Register-Source", List.of("{\n"
                + "\"destination\": \"" + ALT_DESTINATION + "\",\n"
                + "\"source_event_id\": \"" + ALT_EVENT_ID + "\",\n"
                + "\"priority\": \"" + ALT_PRIORITY + "\",\n"
                + "\"expiry\": " + ALT_EXPIRY + ""
                + "}\n"));

        when(mUrlConnection.getHeaderFields()).thenReturn(headersFirstRequest).thenReturn(
                headersSecondRequest);

        ArrayList<SourceRegistration> result = new ArrayList();
        assertTrue(mFetcher.fetchSource(request, result));
        assertEquals(2, result.size());
        assertEquals(DEFAULT_TOP_ORIGIN, result.get(0).getTopOrigin().toString());
        assertEquals(DEFAULT_REGISTRATION, result.get(0).getReportingOrigin().toString());
        assertEquals(DEFAULT_DESTINATION, result.get(0).getDestination().toString());
        assertEquals(DEFAULT_EVENT_ID, result.get(0).getSourceEventId());
        assertEquals(DEFAULT_PRIORITY, result.get(0).getSourcePriority());
        assertEquals(DEFAULT_EXPIRY, result.get(0).getExpiry());
        assertEquals(DEFAULT_TOP_ORIGIN, result.get(1).getTopOrigin().toString());
        assertEquals(ALT_REGISTRATION, result.get(1).getReportingOrigin().toString());
        assertEquals(ALT_DESTINATION, result.get(1).getDestination().toString());
        assertEquals(ALT_EVENT_ID, result.get(1).getSourceEventId());
        assertEquals(ALT_PRIORITY, result.get(1).getSourcePriority());
        assertEquals(ALT_EXPIRY, result.get(1).getExpiry());
        verify(mUrlConnection, times(2)).setRequestMethod("POST");
    }

    @Test
    public void testRedirectSameDestination_returnBothSuccessfully() throws Exception {
        RegistrationRequest request = buildRequest(DEFAULT_REGISTRATION, DEFAULT_TOP_ORIGIN);
        doReturn(mUrlConnection).when(mFetcher).openUrl(any(URL.class));
        when(mUrlConnection.getResponseCode()).thenReturn(200);

        Map<String, List<String>> headersFirstRequest = new HashMap<>();
        headersFirstRequest.put("Attribution-Reporting-Register-Source", List.of("{\n"
                + "\"destination\": \"" + DEFAULT_DESTINATION + "\",\n"
                + "\"source_event_id\": \"" + DEFAULT_EVENT_ID + "\",\n"
                + "\"priority\": \"" + DEFAULT_PRIORITY + "\",\n"
                + "\"expiry\": " + DEFAULT_EXPIRY + ""
                + "}\n"));
        headersFirstRequest.put("Attribution-Reporting-Redirect", List.of(ALT_REGISTRATION));

        Map<String, List<String>> headersSecondRequest = new HashMap<>();
        headersSecondRequest.put("Attribution-Reporting-Register-Source", List.of("{\n"
                + "\"destination\": \"" + DEFAULT_DESTINATION + "\",\n"
                + "\"source_event_id\": \"" + ALT_EVENT_ID + "\",\n"
                + "\"priority\": \"" + ALT_PRIORITY + "\",\n"
                + "\"expiry\": " + ALT_EXPIRY + ""
                + "}\n"));

        when(mUrlConnection.getHeaderFields()).thenReturn(headersFirstRequest).thenReturn(
                headersSecondRequest);

        ArrayList<SourceRegistration> result = new ArrayList();
        assertTrue(mFetcher.fetchSource(request, result));
        assertEquals(2, result.size());
        assertEquals(DEFAULT_TOP_ORIGIN, result.get(0).getTopOrigin().toString());
        assertEquals(DEFAULT_REGISTRATION, result.get(0).getReportingOrigin().toString());
        assertEquals(DEFAULT_DESTINATION, result.get(0).getDestination().toString());
        assertEquals(DEFAULT_EVENT_ID, result.get(0).getSourceEventId());
        assertEquals(DEFAULT_PRIORITY, result.get(0).getSourcePriority());
        assertEquals(DEFAULT_EXPIRY, result.get(0).getExpiry());
        assertEquals(DEFAULT_TOP_ORIGIN, result.get(1).getTopOrigin().toString());
        assertEquals(ALT_REGISTRATION, result.get(1).getReportingOrigin().toString());
        assertEquals(DEFAULT_DESTINATION, result.get(1).getDestination().toString());
        assertEquals(ALT_EVENT_ID, result.get(1).getSourceEventId());
        assertEquals(ALT_PRIORITY, result.get(1).getSourcePriority());
        assertEquals(ALT_EXPIRY, result.get(1).getExpiry());
        verify(mUrlConnection, times(2)).setRequestMethod("POST");
    }

    @Test
    public void testMissingHeaderButWithRedirect() throws Exception {
        RegistrationRequest request = buildRequest(DEFAULT_REGISTRATION, DEFAULT_TOP_ORIGIN);
        doReturn(mUrlConnection).when(mFetcher).openUrl(any(URL.class));
        when(mUrlConnection.getResponseCode()).thenReturn(200);
        when(mUrlConnection.getHeaderFields())
                .thenReturn(Map.of("Attribution-Reporting-Redirect", List.of(ALT_REGISTRATION)))
                .thenReturn(Map.of("Attribution-Reporting-Register-Source",
                        List.of("{\n"
                                + "  \"destination\": \"" + DEFAULT_DESTINATION + "\",\n"
                                + "  \"priority\": \"" + DEFAULT_PRIORITY + "\",\n"
                                + "  \"expiry\": \"" + DEFAULT_EXPIRY + "\",\n"
                                + "  \"source_event_id\": \"" + DEFAULT_EVENT_ID + "\"\n"
                                + "}\n")));

        ArrayList<SourceRegistration> result = new ArrayList();
        assertFalse(mFetcher.fetchSource(request, result));
        assertEquals(0, result.size());
        verify(mUrlConnection, times(1)).setRequestMethod("POST");
    }

    @Test
    public void testBasicSourceRequestWithAggregateFilterData() throws Exception {
        RegistrationRequest request = buildRequest(DEFAULT_REGISTRATION, DEFAULT_TOP_ORIGIN);
        String filterData =
                "  \"filter_data\": {\"product\":[\"1234\",\"2345\"], \"ctid\":[\"id\"]} \n";
        doReturn(mUrlConnection).when(mFetcher).openUrl(any(URL.class));
        when(mUrlConnection.getResponseCode()).thenReturn(200);
        when(mUrlConnection.getHeaderFields())
                .thenReturn(Map.of("Attribution-Reporting-Register-Source",
                        List.of("{\n"
                                + "  \"destination\": \"android-app://com.myapps\",\n"
                                + "  \"priority\": \"123\",\n"
                                + "  \"expiry\": \"456789\",\n"
                                + "  \"source_event_id\": \"987654321\",\n"
                                + filterData
                                + "}\n")));
        ArrayList<SourceRegistration> result = new ArrayList<>();
        assertTrue(mFetcher.fetchSource(request, result));
        assertEquals(1, result.size());
        assertEquals("https://baz.com", result.get(0).getTopOrigin().toString());
        assertEquals("https://foo.com", result.get(0).getReportingOrigin().toString());
        assertEquals("android-app://com.myapps", result.get(0).getDestination().toString());
        assertEquals(123, result.get(0).getSourcePriority());
        assertEquals(456789, result.get(0).getExpiry());
        assertEquals(987654321, result.get(0).getSourceEventId());
        assertEquals("{\"product\":[\"1234\",\"2345\"],\"ctid\":[\"id\"]}",
                result.get(0).getAggregateFilterData());
        verify(mUrlConnection).setRequestMethod("POST");
    }

    @Test
    public void testBasicSourceRequestWithAggregateSource() throws Exception {
        RegistrationRequest request = buildRequest(DEFAULT_REGISTRATION, DEFAULT_TOP_ORIGIN);
        doReturn(mUrlConnection).when(mFetcher).openUrl(any(URL.class));
        when(mUrlConnection.getResponseCode()).thenReturn(200);
        when(mUrlConnection.getHeaderFields())
                .thenReturn(Map.of("Attribution-Reporting-Register-Aggregatable-Source",
                        List.of("[{\"id\" : \"campaignCounts\", \"key_piece\" : \"0x159\"},"
                                + "{\"id\" : \"geoValue\", \"key_piece\" : \"0x5\"}]")));
        ArrayList<SourceRegistration> result = new ArrayList<>();
        assertTrue(mFetcher.fetchSource(request, result));
        assertEquals(1, result.size());
        assertEquals("https://baz.com", result.get(0).getTopOrigin().toString());
        assertEquals("https://foo.com", result.get(0).getReportingOrigin().toString());
        assertEquals(
                "[{\"id\" : \"campaignCounts\", \"key_piece\" : \"0x159\"},{\"id\" : "
                        + "\"geoValue\", \"key_piece\" : \"0x5\"}]",
                result.get(0).getAggregateSource());
        verify(mUrlConnection).setRequestMethod("POST");
    }

    private RegistrationRequest buildRequest(String registrationUri, String topOrigin) {
        return new RegistrationRequest.Builder()
                .setRegistrationType(RegistrationRequest.REGISTER_SOURCE)
                .setRegistrationUri(Uri.parse(registrationUri))
                .setTopOriginUri(Uri.parse(topOrigin))
                .setAttributionSource(sContext.getAttributionSource())
                .build();
    }
}
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
import static org.mockito.Mockito.doReturn;
import static org.mockito.Mockito.doThrow;
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
import java.util.List;
import java.util.Map;

import javax.net.ssl.HttpsURLConnection;


/**
 * Unit tests for {@link SourceFetcher}
 */
@SmallTest
public final class SourceFetcherTest {
    private static final String TAG = "SourceFetcherTest";

    private static final Context sContext = InstrumentationRegistry.getTargetContext();

    @Spy SourceFetcher mFetcher;
    @Mock HttpsURLConnection mUrlConnection;

    @Before
    public void setUp() throws Exception {
        MockitoAnnotations.initMocks(this);
    }

    @Test
    public void testBasicSourceRequest() throws Exception {
        RegistrationRequest request = new RegistrationRequest.Builder()
                .setRegistrationType(RegistrationRequest.REGISTER_SOURCE)
                .setRegistrationUri(Uri.parse("https://foo.com"))
                .setReferrerUri(Uri.parse("https://bar.com"))
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
                                         + "  \"source_event_id\": \"987654321\"\n"
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
        verify(mUrlConnection).setRequestMethod("POST");
    }

    @Test
    public void testBadSourceUrl() throws Exception {
        RegistrationRequest request = new RegistrationRequest.Builder()
                .setRegistrationType(RegistrationRequest.REGISTER_SOURCE)
                .setRegistrationUri(Uri.parse("bad-schema://foo.com"))
                .setReferrerUri(Uri.parse("https://bar.com"))
                .setTopOriginUri(Uri.parse("https://baz.com"))
                .setAttributionSource(sContext.getAttributionSource())
                .build();
        ArrayList<SourceRegistration> result = new ArrayList();
        assertFalse(mFetcher.fetchSource(request, result));
        assertEquals(0, result.size());
    }

    @Test
    public void testBadSourceConnection() throws Exception {
        RegistrationRequest request = new RegistrationRequest.Builder()
                .setRegistrationType(RegistrationRequest.REGISTER_SOURCE)
                .setRegistrationUri(Uri.parse("bad-schema://foo.com"))
                .setReferrerUri(Uri.parse("https://bar.com"))
                .setTopOriginUri(Uri.parse("https://baz.com"))
                .setAttributionSource(sContext.getAttributionSource())
                .build();
        doThrow(new IOException("Bad internet things"))
                .when(mFetcher).openUrl(new URL("https://foo.com"));
        ArrayList<SourceRegistration> result = new ArrayList();
        assertFalse(mFetcher.fetchSource(request, result));
        assertEquals(0, result.size());
    }

    @Test
    public void testBadSourceJson() throws Exception {
        RegistrationRequest request = new RegistrationRequest.Builder()
                .setRegistrationType(RegistrationRequest.REGISTER_SOURCE)
                .setRegistrationUri(Uri.parse("https://foo.com"))
                .setReferrerUri(Uri.parse("https://bar.com"))
                .setTopOriginUri(Uri.parse("https://baz.com"))
                .setAttributionSource(sContext.getAttributionSource())
                .build();
        doReturn(mUrlConnection).when(mFetcher).openUrl(new URL("https://foo.com"));
        when(mUrlConnection.getResponseCode()).thenReturn(200);
        when(mUrlConnection.getHeaderFields())
                .thenReturn(Map.of("Attribution-Reporting-Register-Source",
                                   List.of("{\n"
                                         + "\"destination\": \"android-app://com.myapps\"")));
        ArrayList<SourceRegistration> result = new ArrayList();
        assertFalse(mFetcher.fetchSource(request, result));
        assertEquals(0, result.size());
        verify(mUrlConnection).setRequestMethod("POST");
    }

    @Test
    public void testBasicSourceRequestMinimumFields() throws Exception {
        RegistrationRequest request = new RegistrationRequest.Builder()
                .setRegistrationType(RegistrationRequest.REGISTER_SOURCE)
                .setRegistrationUri(Uri.parse("https://foo.com"))
                .setReferrerUri(Uri.parse("https://bar.com"))
                .setTopOriginUri(Uri.parse("https://baz.com"))
                .setAttributionSource(sContext.getAttributionSource())
                .build();
        doReturn(mUrlConnection).when(mFetcher).openUrl(new URL("https://foo.com"));
        when(mUrlConnection.getResponseCode()).thenReturn(200);
        when(mUrlConnection.getHeaderFields())
                .thenReturn(Map.of("Attribution-Reporting-Register-Source",
                                   List.of("{\n"
                                         + "\"destination\": \"android-app://com.myapps\",\n"
                                         + "\"source_event_id\": \"123\"\n"
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
        RegistrationRequest request = new RegistrationRequest.Builder()
                .setRegistrationType(RegistrationRequest.REGISTER_SOURCE)
                .setRegistrationUri(Uri.parse("https://foo.com"))
                .setReferrerUri(Uri.parse("https://bar.com"))
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
                                + "\"expiry\": 1"
                                + "}\n")));
        ArrayList<SourceRegistration> result = new ArrayList();
        assertTrue(mFetcher.fetchSource(request, result));
        assertEquals(1, result.size());
        assertEquals("https://baz.com", result.get(0).getTopOrigin().toString());
        assertEquals("https://foo.com", result.get(0).getReportingOrigin().toString());
        assertEquals("android-app://com.myapps", result.get(0).getDestination().toString());
        assertEquals(123, result.get(0).getSourceEventId());
        assertEquals(0, result.get(0).getSourcePriority());
        assertEquals(MIN_REPORTING_REGISTER_SOURCE_EXPIRATION_IN_SECONDS,
                result.get(0).getExpiry());
        verify(mUrlConnection).setRequestMethod("POST");
    }

    @Test
    public void testBasicSourceRequestWithExpiryMoreThan30Days() throws Exception {
        RegistrationRequest request = new RegistrationRequest.Builder()
                .setRegistrationType(RegistrationRequest.REGISTER_SOURCE)
                .setRegistrationUri(Uri.parse("https://foo.com"))
                .setReferrerUri(Uri.parse("https://bar.com"))
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
                                + "\"expiry\": 2678400"
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
    public void testNotOverHttps() throws Exception {
        RegistrationRequest request = new RegistrationRequest.Builder()
                .setRegistrationType(RegistrationRequest.REGISTER_SOURCE)
                .setRegistrationUri(Uri.parse("http://foo.com"))
                .setReferrerUri(Uri.parse("https://bar.com"))
                .setTopOriginUri(Uri.parse("https://baz.com"))
                .setAttributionSource(sContext.getAttributionSource())
                .build();
        ArrayList<SourceRegistration> result = new ArrayList();
        // Require https.
        assertFalse(mFetcher.fetchSource(request, result));
        assertEquals(0, result.size());
    }

    // TODO: Add testing of redirection.
    // TODO: Add testing of response codes.
    // TODO: Add testing of conflicting destinations in redirects.
}

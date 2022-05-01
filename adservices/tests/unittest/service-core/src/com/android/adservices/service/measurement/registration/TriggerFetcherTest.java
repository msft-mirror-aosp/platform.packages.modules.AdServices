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
import static org.junit.Assert.assertNull;
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
 * Unit tests for {@link TriggerFetcher}
 */
@SmallTest
public final class TriggerFetcherTest {
    private static final String TAG = "TriggerFetcherTest";

    private static final Context sContext = InstrumentationRegistry.getTargetContext();

    @Spy TriggerFetcher mFetcher;
    @Mock HttpsURLConnection mUrlConnection;

    @Before
    public void setUp() throws Exception {
        MockitoAnnotations.initMocks(this);
    }

    @Test
    public void testBasicTriggerRequest() throws Exception {
        RegistrationRequest request = new RegistrationRequest.Builder()
                .setRegistrationType(RegistrationRequest.REGISTER_TRIGGER)
                .setRegistrationUri(Uri.parse("https://foo.com"))
                .setTopOriginUri(Uri.parse("https://baz.com"))
                .setAttributionSource(sContext.getAttributionSource())
                .build();
        doReturn(mUrlConnection).when(mFetcher).openUrl(new URL("https://foo.com"));
        when(mUrlConnection.getResponseCode()).thenReturn(200);
        when(mUrlConnection.getHeaderFields())
                .thenReturn(Map.of("Attribution-Reporting-Register-Event-Trigger",
                                   List.of("[{\n"
                                         + "  \"trigger_data\": \"3\",\n"
                                         + "  \"priority\": \"11111\",\n"
                                         + "  \"deduplication_key\": \"22222\"\n"
                                         + "}]\n")));
        ArrayList<TriggerRegistration> result = new ArrayList();
        assertTrue(mFetcher.fetchTrigger(request, result));
        assertEquals(1, result.size());
        assertEquals("https://baz.com", result.get(0).getTopOrigin().toString());
        assertEquals("https://foo.com", result.get(0).getReportingOrigin().toString());
        assertEquals(3, result.get(0).getTriggerData());
        assertEquals(11111, result.get(0).getTriggerPriority());
        assertEquals(22222, result.get(0).getDeduplicationKey().longValue());
        verify(mUrlConnection).setRequestMethod("POST");
    }

    @Test
    public void testBadTriggerUrl() throws Exception {
        RegistrationRequest request = new RegistrationRequest.Builder()
                .setRegistrationType(RegistrationRequest.REGISTER_TRIGGER)
                .setRegistrationUri(Uri.parse("bad-schema://foo.com"))
                .setTopOriginUri(Uri.parse("https://baz.com"))
                .setAttributionSource(sContext.getAttributionSource())
                .build();
        ArrayList<TriggerRegistration> result = new ArrayList();
        assertFalse(mFetcher.fetchTrigger(request, result));
        assertEquals(0, result.size());
    }

    @Test
    public void testBadTriggerConnection() throws Exception {
        RegistrationRequest request = new RegistrationRequest.Builder()
                .setRegistrationType(RegistrationRequest.REGISTER_TRIGGER)
                .setRegistrationUri(Uri.parse("bad-schema://foo.com"))
                .setTopOriginUri(Uri.parse("https://baz.com"))
                .setAttributionSource(sContext.getAttributionSource())
                .build();
        doThrow(new IOException("Bad internet things"))
                .when(mFetcher).openUrl(new URL("https://foo.com"));
        ArrayList<TriggerRegistration> result = new ArrayList();
        assertFalse(mFetcher.fetchTrigger(request, result));
        assertEquals(0, result.size());
    }

    @Test
    public void testBadTriggerJson() throws Exception {
        RegistrationRequest request = new RegistrationRequest.Builder()
                .setRegistrationType(RegistrationRequest.REGISTER_TRIGGER)
                .setRegistrationUri(Uri.parse("https://foo.com"))
                .setTopOriginUri(Uri.parse("https://baz.com"))
                .setAttributionSource(sContext.getAttributionSource())
                .build();
        doReturn(mUrlConnection).when(mFetcher).openUrl(new URL("https://foo.com"));
        when(mUrlConnection.getResponseCode()).thenReturn(200);
        when(mUrlConnection.getHeaderFields())
                .thenReturn(Map.of("Attribution-Reporting-Register-Event-Trigger",
                                   List.of("{\n"
                                         + "\"foo\": 123")));
        ArrayList<TriggerRegistration> result = new ArrayList();
        assertFalse(mFetcher.fetchTrigger(request, result));
        assertEquals(0, result.size());
        verify(mUrlConnection).setRequestMethod("POST");
    }

    @Test
    public void testBasicTriggerRequestMinimumFields() throws Exception {
        RegistrationRequest request = new RegistrationRequest.Builder()
                .setRegistrationType(RegistrationRequest.REGISTER_TRIGGER)
                .setRegistrationUri(Uri.parse("https://foo.com"))
                .setTopOriginUri(Uri.parse("https://baz.com"))
                .setAttributionSource(sContext.getAttributionSource())
                .build();
        doReturn(mUrlConnection).when(mFetcher).openUrl(new URL("https://foo.com"));
        when(mUrlConnection.getResponseCode()).thenReturn(200);
        when(mUrlConnection.getHeaderFields())
                .thenReturn(Map.of("Attribution-Reporting-Register-Event-Trigger",
                                   List.of("[{}]")));
        ArrayList<TriggerRegistration> result = new ArrayList();
        assertTrue(mFetcher.fetchTrigger(request, result));
        assertEquals(1, result.size());
        assertEquals("https://baz.com", result.get(0).getTopOrigin().toString());
        assertEquals("https://foo.com", result.get(0).getReportingOrigin().toString());
        assertEquals(0, result.get(0).getTriggerData());
        assertEquals(0, result.get(0).getTriggerPriority());
        assertNull(result.get(0).getDeduplicationKey());
        verify(mUrlConnection).setRequestMethod("POST");
    }

    @Test
    public void testNotOverHttps() throws Exception {
        RegistrationRequest request = new RegistrationRequest.Builder()
                .setRegistrationType(RegistrationRequest.REGISTER_TRIGGER)
                .setRegistrationUri(Uri.parse("http://foo.com"))
                .setTopOriginUri(Uri.parse("https://baz.com"))
                .setAttributionSource(sContext.getAttributionSource())
                .build();
        // Non-https should fail.
        ArrayList<TriggerRegistration> result = new ArrayList();
        assertFalse(mFetcher.fetchTrigger(request, result));
        assertEquals(0, result.size());
    }

    // TODO: Add testing of redirection.
    // TODO: Add testing of response codes.
    // TODO: Add testing of conflicting destinations in redirects.
}

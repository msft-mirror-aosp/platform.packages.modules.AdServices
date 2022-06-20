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
package com.android.adservices.service.measurement.aggregation;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.doReturn;
import static org.mockito.Mockito.doThrow;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import android.net.Uri;

import androidx.test.filters.SmallTest;

import org.junit.Before;
import org.junit.Test;
import org.mockito.Mock;
import org.mockito.MockitoAnnotations;
import org.mockito.Spy;

import java.io.ByteArrayInputStream;
import java.io.IOException;
import java.io.InputStream;
import java.net.URL;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;

import javax.net.ssl.HttpsURLConnection;


/**
 * Unit tests for {@link AggregateEncryptionKeyFetcher}
 */
@SmallTest
public final class AggregateEncryptionKeyFetcherTest {
    private static final Uri DEFAULT_TARGET = Uri.parse("https://foo.com");
    private static final long DEFAULT_EVENT_TIME = 1653681612892L;
    private static final String DEFAULT_MAX_AGE = "max-age=604800";
    private static final long DEFAULT_EXPIRY = 1654286412892L; // 1653681612892L + 604800000L
    private interface DEFAULT_KEY_1 {
        String KEY_ID = "38b1d571-f924-4dc0-abe1-e2bac9b6a6be";
        String PUBLIC_KEY = "/amqBgfDOvHAIuatDyoHxhfHaMoYA4BDxZxwtWBRQhc=";
    }
    private interface DEFAULT_KEY_2 {
        String KEY_ID = "e52dbbda-4e3a-4380-a7c8-14db3e08ef33";
        String PUBLIC_KEY = "dU3hTbFy1RgCddQIQIZjoVNPJ3KScryj8BSREFr9yW8=";
    }

    @Spy AggregateEncryptionKeyFetcher mFetcher;
    @Mock HttpsURLConnection mUrlConnection;

    private static String getDefaultResponseBody() {
        return String.format("{\"keys\":[{\"id\":\"%s\",\"key\":\"%s\"},"
                + "{\"id\":\"%s\",\"key\":\"%s\"}]}",
                DEFAULT_KEY_1.KEY_ID, DEFAULT_KEY_1.PUBLIC_KEY,
                DEFAULT_KEY_2.KEY_ID, DEFAULT_KEY_2.PUBLIC_KEY);
    }

    @Before
    public void setUp() throws Exception {
        MockitoAnnotations.initMocks(this);
    }

    @Test
    public void testBasicAggregateEncryptionKeyRequest() throws Exception {
        InputStream inputStream = new ByteArrayInputStream(getDefaultResponseBody().getBytes());
        doReturn(mUrlConnection).when(mFetcher).openUrl(new URL(DEFAULT_TARGET.toString()));
        when(mUrlConnection.getResponseCode()).thenReturn(200);
        when(mUrlConnection.getHeaderFields()).thenReturn(Map.of(
                "cache-control", List.of(DEFAULT_MAX_AGE)));
        when(mUrlConnection.getInputStream()).thenReturn(inputStream);

        Optional<List<AggregateEncryptionKey>> resultOptional =
                mFetcher.fetch(DEFAULT_TARGET, DEFAULT_EVENT_TIME);
        List<AggregateEncryptionKey> result = resultOptional.get();
        assertEquals(2, result.size());
        assertEquals(DEFAULT_KEY_1.KEY_ID, result.get(0).getKeyId());
        assertEquals(DEFAULT_KEY_1.PUBLIC_KEY, result.get(0).getPublicKey());
        assertEquals(DEFAULT_EXPIRY, result.get(0).getExpiry());
        assertEquals(DEFAULT_KEY_2.KEY_ID, result.get(1).getKeyId());
        assertEquals(DEFAULT_KEY_2.PUBLIC_KEY, result.get(1).getPublicKey());
        assertEquals(DEFAULT_EXPIRY, result.get(1).getExpiry());
        verify(mUrlConnection).setRequestMethod("GET");
    }

    @Test
    public void testBadSourceUrl() throws Exception {
        Uri badTarget = Uri.parse("bad-schema://foo.com");
        Optional<List<AggregateEncryptionKey>> resultOptional =
                mFetcher.fetch(badTarget, DEFAULT_EVENT_TIME);
        assertFalse(resultOptional.isPresent());
    }

    @Test
    public void testBadConnection() throws Exception {
        doThrow(new IOException("Bad internet things")).when(mFetcher).openUrl(
                new URL(DEFAULT_TARGET.toString()));
        Optional<List<AggregateEncryptionKey>> resultOptional =
                mFetcher.fetch(DEFAULT_TARGET, DEFAULT_EVENT_TIME);
        assertFalse(resultOptional.isPresent());
    }

    @Test
    public void testInvalidResponseBodyJson() throws Exception {
        InputStream inputStream = new ByteArrayInputStream(
                ("{" + getDefaultResponseBody()).getBytes());
        doReturn(mUrlConnection).when(mFetcher).openUrl(new URL(DEFAULT_TARGET.toString()));
        when(mUrlConnection.getResponseCode()).thenReturn(200);
        when(mUrlConnection.getHeaderFields()).thenReturn(Map.of(
                "cache-control", List.of(DEFAULT_MAX_AGE)));
        when(mUrlConnection.getInputStream()).thenReturn(inputStream);
        Optional<List<AggregateEncryptionKey>> resultOptional =
                mFetcher.fetch(DEFAULT_TARGET, DEFAULT_EVENT_TIME);
        assertFalse(resultOptional.isPresent());
    }

    @Test
    public void testMissingCacheControlHeader() throws Exception {
        InputStream inputStream = new ByteArrayInputStream(getDefaultResponseBody().getBytes());
        doReturn(mUrlConnection).when(mFetcher).openUrl(new URL(DEFAULT_TARGET.toString()));
        when(mUrlConnection.getResponseCode()).thenReturn(200);
        when(mUrlConnection.getInputStream()).thenReturn(inputStream);
        when(mUrlConnection.getHeaderFields()).thenReturn(new HashMap());
        Optional<List<AggregateEncryptionKey>> resultOptional =
                mFetcher.fetch(DEFAULT_TARGET, DEFAULT_EVENT_TIME);
        assertFalse(resultOptional.isPresent());
    }

    @Test
    public void testNotOverHttps() throws Exception {
        Optional<List<AggregateEncryptionKey>> resultOptional =
                mFetcher.fetch(Uri.parse("http://foo.com"), DEFAULT_EVENT_TIME);
        assertFalse(resultOptional.isPresent());
        verify(mFetcher, never()).openUrl(any());
    }
}

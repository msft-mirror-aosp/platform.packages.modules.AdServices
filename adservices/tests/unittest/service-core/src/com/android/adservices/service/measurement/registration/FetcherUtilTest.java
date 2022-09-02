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

import static com.android.adservices.service.measurement.SystemHealthParams.MAX_ATTRIBUTION_FILTERS;
import static com.android.adservices.service.measurement.SystemHealthParams.MAX_BYTES_PER_ATTRIBUTION_AGGREGATE_KEY_ID;
import static com.android.adservices.service.measurement.SystemHealthParams.MAX_VALUES_PER_ATTRIBUTION_FILTER;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertTrue;

import android.net.Uri;

import androidx.test.filters.SmallTest;

import org.json.JSONException;
import org.json.JSONObject;
import org.junit.Test;

import java.util.List;
import java.util.Map;
import java.util.stream.Collectors;
import java.util.stream.IntStream;

/**
 * Unit tests for {@link FetcherUtil}
 */
@SmallTest
public final class FetcherUtilTest {
    private static final String LONG_FILTER_STRING = "12345678901234567890123456";

    @Test
    public void testIsSuccess() {
        assertTrue(FetcherUtil.isSuccess(200));
        assertFalse(FetcherUtil.isSuccess(404));
        assertFalse(FetcherUtil.isSuccess(500));
        assertFalse(FetcherUtil.isSuccess(0));
    }

    @Test
    public void testIsRedirect() {
        assertTrue(FetcherUtil.isRedirect(301));
        assertTrue(FetcherUtil.isRedirect(302));
        assertTrue(FetcherUtil.isRedirect(303));
        assertTrue(FetcherUtil.isRedirect(307));
        assertTrue(FetcherUtil.isRedirect(308));
        assertFalse(FetcherUtil.isRedirect(200));
        assertFalse(FetcherUtil.isRedirect(404));
        assertFalse(FetcherUtil.isRedirect(500));
        assertFalse(FetcherUtil.isRedirect(0));
    }

    @Test
    public void testParseRedirectsNothingInitial() {
        List<Uri> redirs = FetcherUtil.parseRedirects(Map.of());
        assertEquals(0, redirs.size());
    }

    @Test
    public void testParseRedirectsARR() {
        List<Uri> redirs =
                FetcherUtil.parseRedirects(
                        Map.of("Attribution-Reporting-Redirect", List.of("foo.com", "bar.com")));
        assertEquals(2, redirs.size());
        assertEquals(Uri.parse("foo.com"), redirs.get(0));
        assertEquals(Uri.parse("bar.com"), redirs.get(1));
    }

    @Test
    public void testParseRedirectsSingleElementARR() {
        List<Uri> redirs =
                FetcherUtil.parseRedirects(
                        Map.of("Attribution-Reporting-Redirect", List.of("foo.com")));
        assertEquals(1, redirs.size());
    }

    @Test
    public void testIsValidAggregateKeyId_valid() {
        assertTrue(FetcherUtil.isValidAggregateKeyId("abcd"));
    }

    @Test
    public void testIsValidAggregateKeyId_null() {
        assertFalse(FetcherUtil.isValidAggregateKeyId(null));
    }

    @Test
    public void testIsValidAggregateKeyId_tooLong() {
        StringBuilder keyId = new StringBuilder("");
        for (int i = 0; i < MAX_BYTES_PER_ATTRIBUTION_AGGREGATE_KEY_ID + 1; i++) {
            keyId.append("a");
        }
        assertFalse(FetcherUtil.isValidAggregateKeyId(keyId.toString()));
    }

    @Test
    public void testIsValidAggregateKeyPiece_valid() {
        assertTrue(FetcherUtil.isValidAggregateKeyPiece("0x15A"));
    }

    @Test
    public void testIsValidAggregateKeyPiece_validWithUpperCasePrefix() {
        assertTrue(FetcherUtil.isValidAggregateKeyPiece("0X15A"));
    }

    @Test
    public void testIsValidAggregateKeyPiece_null() {
        assertFalse(FetcherUtil.isValidAggregateKeyPiece(null));
    }

    @Test
    public void testIsValidAggregateKeyPiece_missingPrefix() {
        assertFalse(FetcherUtil.isValidAggregateKeyPiece("1234"));
    }

    @Test
    public void testIsValidAggregateKeyPiece_tooShort() {
        assertFalse(FetcherUtil.isValidAggregateKeyPiece("0x"));
    }

    @Test
    public void testIsValidAggregateKeyPiece_tooLong() {
        StringBuilder keyPiece = new StringBuilder("0x");
        for (int i = 0; i < 33; i++) {
            keyPiece.append("1");
        }
        assertFalse(FetcherUtil.isValidAggregateKeyPiece(keyPiece.toString()));
    }

    @Test
    public void testAreValidAttributionFilters_valid() throws JSONException {
        String json = "{"
                + "\"filter-string-1\": [\"filter-value-1\"],"
                + "\"filter-string-2\": [\"filter-value-2\", \"filter-value-3\"]"
                + "}";
        JSONObject filters = new JSONObject(json);
        assertTrue(FetcherUtil.areValidAttributionFilters(filters));
    }

    @Test
    public void testAreValidAttributionFilters_null() {
        assertFalse(FetcherUtil.areValidAttributionFilters(null));
    }

    @Test
    public void testAreValidAttributionFilters_tooManyFilters() throws JSONException {
        StringBuilder json = new StringBuilder("{");
        json.append(IntStream.range(0, MAX_ATTRIBUTION_FILTERS + 1)
                .mapToObj(i -> "\"filter-string-" + i + "\": [\"filter-value\"]")
                .collect(Collectors.joining(",")));
        json.append("}");
        JSONObject filters = new JSONObject(json.toString());
        assertFalse(FetcherUtil.areValidAttributionFilters(filters));
    }

    @Test
    public void testAreValidAttributionFilters_keyTooLong() throws JSONException {
        String json = "{"
                + "\"" + LONG_FILTER_STRING + "\": [\"filter-value-1\"],"
                + "\"filter-string-2\": [\"filter-value-2\", \"filter-value-3\"]"
                + "}";
        JSONObject filters = new JSONObject(json);
        assertFalse(FetcherUtil.areValidAttributionFilters(filters));
    }

    @Test
    public void testAreValidAttributionFilters_tooManyValues() throws JSONException {
        StringBuilder json = new StringBuilder("{"
                + "\"filter-string-1\": [\"filter-value-1\"],"
                + "\"filter-string-2\": [");
        json.append(IntStream.range(0, MAX_VALUES_PER_ATTRIBUTION_FILTER + 1)
                .mapToObj(i -> "\"filter-value-" + i + "\"")
                .collect(Collectors.joining(",")));
        json.append("]}");
        JSONObject filters = new JSONObject(json.toString());
        assertFalse(FetcherUtil.areValidAttributionFilters(filters));
    }

    @Test
    public void testAreValidAttributionFilters_valueTooLong() throws JSONException {
        String json = "{"
                + "\"filter-string-1\": [\"filter-value-1\"],"
                + "\"filter-string-2\": [\"filter-value-2\", \"" + LONG_FILTER_STRING + "\"]"
                + "}";
        JSONObject filters = new JSONObject(json);
        assertFalse(FetcherUtil.areValidAttributionFilters(filters));
    }
}

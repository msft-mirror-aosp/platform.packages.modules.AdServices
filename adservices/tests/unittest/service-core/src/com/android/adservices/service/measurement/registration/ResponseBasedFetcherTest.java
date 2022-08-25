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

import android.net.Uri;

import androidx.test.filters.SmallTest;

import org.junit.Test;

import java.util.List;
import java.util.Map;


/**
 * Unit tests for {@link ResponseBasedFetcher}
 */
@SmallTest
public final class ResponseBasedFetcherTest {

    @Test
    public void testIsSuccess() {
        assertTrue(ResponseBasedFetcher.isSuccess(200));
        assertFalse(ResponseBasedFetcher.isSuccess(404));
        assertFalse(ResponseBasedFetcher.isSuccess(500));
        assertFalse(ResponseBasedFetcher.isSuccess(0));
    }

    @Test
    public void testIsRedirect() {
        assertTrue(ResponseBasedFetcher.isRedirect(301));
        assertTrue(ResponseBasedFetcher.isRedirect(302));
        assertTrue(ResponseBasedFetcher.isRedirect(303));
        assertTrue(ResponseBasedFetcher.isRedirect(307));
        assertTrue(ResponseBasedFetcher.isRedirect(308));
        assertFalse(ResponseBasedFetcher.isRedirect(200));
        assertFalse(ResponseBasedFetcher.isRedirect(404));
        assertFalse(ResponseBasedFetcher.isRedirect(500));
        assertFalse(ResponseBasedFetcher.isRedirect(0));
    }

    @Test
    public void testParseRedirectsNothingInitial() {
        List<Uri> redirs = ResponseBasedFetcher.parseRedirects(Map.of());
        assertEquals(0, redirs.size());
    }

    @Test
    public void testParseRedirectsARR() {
        List<Uri> redirs =
                ResponseBasedFetcher.parseRedirects(
                        Map.of("Attribution-Reporting-Redirect", List.of("foo.com", "bar.com")));
        assertEquals(2, redirs.size());
        assertEquals(Uri.parse("foo.com"), redirs.get(0));
        assertEquals(Uri.parse("bar.com"), redirs.get(1));
    }

    @Test
    public void testParseRedirectsSingleElementARR() {
        List<Uri> redirs =
                ResponseBasedFetcher.parseRedirects(
                        Map.of("Attribution-Reporting-Redirect", List.of("foo.com")));
        assertEquals(1, redirs.size());
    }
}

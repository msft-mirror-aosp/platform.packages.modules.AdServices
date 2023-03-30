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

package com.android.adservices.service.measurement.util;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertTrue;

import android.net.Uri;

import org.junit.Test;

import java.util.Optional;

public class WebTest {

    private static final String VALID_PUBLIC_DOMAIN = "com";
    private static final String VALID_PRIVATE_DOMAIN = "blogspot.com";
    private static final String INVALID_TLD = "invalid_tld";
    private static final String TOP_PRIVATE_DOMAIN = "private-domain";
    private static final String SUBDOMAIN = "subdomain";
    private static final String HTTPS_SCHEME = "https";
    private static final String HTTP_SCHEME = "http";
    private static final String INVALID_URL = "invalid url";
    private static final String PATH = "path";
    private static final String SECOND_PATH = "second_path";

    @Test
    public void testTopPrivateDomainAndScheme_ValidPublicDomainAndHttpsScheme() {
        String inputUrl = String.format("%s://%s.%s",
                HTTPS_SCHEME, TOP_PRIVATE_DOMAIN, VALID_PUBLIC_DOMAIN);
        Uri expectedUri = Uri.parse(inputUrl);
        Optional<Uri> output = Web.topPrivateDomainAndScheme(inputUrl);
        assertTrue(output.isPresent());
        assertEquals(expectedUri, output.get());
    }

    @Test
    public void testTopPrivateDomainAndScheme_ValidPrivateDomainAndHttpsScheme() {
        String inputUrl = String.format("%s://%s.%s",
                HTTPS_SCHEME, TOP_PRIVATE_DOMAIN, VALID_PRIVATE_DOMAIN);
        Uri expectedUri = Uri.parse(inputUrl);
        Optional<Uri> output = Web.topPrivateDomainAndScheme(inputUrl);
        assertTrue(output.isPresent());
        assertEquals(expectedUri, output.get());
    }

    @Test
    public void testTopPrivateDomainAndScheme_ValidPublicDomainAndHttpsScheme_extraSubdomain() {
        String inputUrl = String.format("%s://%s.%s.%s",
                HTTPS_SCHEME, SUBDOMAIN, TOP_PRIVATE_DOMAIN, VALID_PUBLIC_DOMAIN);
        Uri expectedUri = Uri.parse(String.format("%s://%s.%s",
                  HTTPS_SCHEME, TOP_PRIVATE_DOMAIN, VALID_PUBLIC_DOMAIN));
        Optional<Uri> output = Web.topPrivateDomainAndScheme(inputUrl);
        assertTrue(output.isPresent());
        assertEquals(expectedUri, output.get());
    }

    @Test
    public void testTopPrivateDomainAndScheme_ValidPrivateDomainAndHttpsScheme_extraSubdomain() {
        String inputUrl = String.format("%s://%s.%s.%s",
                HTTPS_SCHEME, SUBDOMAIN, TOP_PRIVATE_DOMAIN, VALID_PRIVATE_DOMAIN);
        Uri expectedUri = Uri.parse(String.format("%s://%s.%s",
                  HTTPS_SCHEME, TOP_PRIVATE_DOMAIN, VALID_PRIVATE_DOMAIN));
        Optional<Uri> output = Web.topPrivateDomainAndScheme(inputUrl);
        assertTrue(output.isPresent());
        assertEquals(expectedUri, output.get());
    }

    @Test
    public void testTopPrivateDomainAndScheme_ValidPublicDomainAndHttpScheme() {
        String inputUrl = String.format("%s://%s.%s",
                HTTP_SCHEME, TOP_PRIVATE_DOMAIN, VALID_PUBLIC_DOMAIN);
        Uri expectedUri = Uri.parse(inputUrl);
        Optional<Uri> output = Web.topPrivateDomainAndScheme(inputUrl);
        assertTrue(output.isPresent());
        assertEquals(expectedUri, output.get());
    }

    @Test
    public void testTopPrivateDomainAndScheme_InvalidTldAndHttpsScheme() {
        String inputUrl = String.format("%s://%s.%s", HTTP_SCHEME, TOP_PRIVATE_DOMAIN, INVALID_TLD);
        Optional<Uri> output = Web.topPrivateDomainAndScheme(inputUrl);
        assertFalse(output.isPresent());
    }

    @Test
    public void testTopPrivateDomainAndScheme_InvalidUrl() {
        Optional<Uri> output = Web.topPrivateDomainAndScheme(INVALID_URL);
        assertFalse(output.isPresent());
    }

    @Test
    public void topPrivateDomainAndPath_ForInvalidUri_ReturnsEmptyOptional() {
        Optional<Uri> output = Web.topPrivateDomainAndScheme(INVALID_URL);
        assertFalse(output.isPresent());
    }
}

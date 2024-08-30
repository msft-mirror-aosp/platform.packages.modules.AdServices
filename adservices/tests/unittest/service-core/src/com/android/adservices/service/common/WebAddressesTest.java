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

package com.android.adservices.service.common;

import static com.google.common.truth.Truth.assertThat;
import static com.google.common.truth.Truth.assertWithMessage;

import android.net.Uri;

import com.android.adservices.common.AdServicesUnitTestCase;
import com.android.adservices.common.WebUtil;

import org.junit.Test;

import java.util.List;
import java.util.Optional;

public final class WebAddressesTest extends AdServicesUnitTestCase {

    private static final String COM_PUBLIC_SUFFIX = "com";
    private static final String BLOGSPOT_COM_PUBLIC_SUFFIX = "blogspot.com";
    private static final String TOP_PRIVATE_DOMAIN = "private-domain";
    private static final String SUBDOMAIN = "subdomain";
    private static final String HTTPS_SCHEME = "https";
    private static final String HTTP_SCHEME = "http";
    private static final String INVALID_TLD = "invalid_tld";
    private static final String INVALID_URL = "invalid url";
    private static final String PORT = "443";
    private static final String LOCALHOST = "localhost";
    private static final String LOCALHOST_IP = "127.0.0.1";

    private static final Uri HTTPS_PRIVATE_DOMAIN_COM_PUBLIC_SUFFIX =
            Uri.parse(
                    String.format(
                            "%s://%s.%s", HTTPS_SCHEME, TOP_PRIVATE_DOMAIN, COM_PUBLIC_SUFFIX));

    private static final Uri HTTPS_PRIVATE_DOMAIN_BLOGSPOT_COM_PUBLIC_SUFFIX =
            Uri.parse(
                    String.format(
                            "%s://%s.%s",
                            HTTPS_SCHEME, TOP_PRIVATE_DOMAIN, BLOGSPOT_COM_PUBLIC_SUFFIX));

    private static final Uri HTTPS_SUBDOMAIN_PRIVATE_DOMAIN_COM_PUBLIC_SUFFIX =
            Uri.parse(
                    String.format(
                            "%s://%s.%s.%s",
                            HTTPS_SCHEME, SUBDOMAIN, TOP_PRIVATE_DOMAIN, COM_PUBLIC_SUFFIX));

    private static final Uri HTTPS_SUBDOMAIN_PRIVATE_DOMAIN_BLOGSPOT_COM_PUBLIC_SUFFIX =
            Uri.parse(
                    String.format(
                            "%s://%s.%s.%s",
                            HTTPS_SCHEME,
                            SUBDOMAIN,
                            TOP_PRIVATE_DOMAIN,
                            BLOGSPOT_COM_PUBLIC_SUFFIX));

    private static final Uri HTTPS_PRIVATE_DOMAIN_COM_PUBLIC_SUFFIX_PORT =
            Uri.parse(
                    String.format(
                            "%s://%s.%s:%s",
                            HTTPS_SCHEME, TOP_PRIVATE_DOMAIN, COM_PUBLIC_SUFFIX, PORT));

    private static final Uri HTTP_PRIVATE_DOMAIN_COM_PUBLIC_SUFFIX =
            Uri.parse(
                    String.format(
                            "%s://%s.%s", HTTP_SCHEME, TOP_PRIVATE_DOMAIN, COM_PUBLIC_SUFFIX));

    private static final Uri HTTPS_LOCALHOST =
            Uri.parse(String.format("%s://%s", HTTPS_SCHEME, LOCALHOST));

    private static final Uri HTTPS_LOCALHOST_IP =
            Uri.parse(String.format("%s://%s", HTTPS_SCHEME, LOCALHOST_IP));

    @Test
    public void testTopPrivateDomainAndScheme_validPublicDomainAndHttpsScheme() {
        Optional<Uri> output =
                WebAddresses.topPrivateDomainAndScheme(HTTPS_PRIVATE_DOMAIN_COM_PUBLIC_SUFFIX);
        assertThat(output.isPresent()).isTrue();
        assertThat(output.get()).isEqualTo(HTTPS_PRIVATE_DOMAIN_COM_PUBLIC_SUFFIX);
    }

    @Test
    public void testTopPrivateDomainAndScheme_validLocalhost() {
        // Localhost
        Optional<Uri> localhost = WebAddresses.topPrivateDomainAndScheme(HTTPS_LOCALHOST);
        assertThat(localhost.isPresent()).isTrue();
        expect.withMessage("localhost").that(localhost.get()).isEqualTo(HTTPS_LOCALHOST);

        Uri localhostWithPath =
                Uri.parse(String.format("%s://%s/%s", HTTPS_SCHEME, LOCALHOST, "path"));
        Optional<Uri> localhostWithPathUri =
                WebAddresses.topPrivateDomainAndScheme(localhostWithPath);
        assertThat(localhostWithPathUri.isPresent()).isTrue();
        expect.withMessage("localhostWithPath")
                .that(localhostWithPathUri.get())
                .isEqualTo(HTTPS_LOCALHOST);

        Uri localhostWithPort =
                Uri.parse(String.format("%s://%s:%s", HTTPS_SCHEME, LOCALHOST, "4000"));
        Optional<Uri> localhostWithPortUri =
                WebAddresses.topPrivateDomainAndScheme(localhostWithPort);
        assertThat(localhostWithPortUri.isPresent()).isTrue();
        expect.withMessage("localhostWithPort")
                .that(localhostWithPortUri.get())
                .isEqualTo(HTTPS_LOCALHOST);

        // localhost ip
        Optional<Uri> localhost_ip = WebAddresses.topPrivateDomainAndScheme(HTTPS_LOCALHOST_IP);
        assertThat(localhost_ip.isPresent()).isTrue();
        expect.withMessage("localhost_ip").that(localhost_ip.get()).isEqualTo(HTTPS_LOCALHOST_IP);

        Uri localhostIpWithPath =
                Uri.parse(String.format("%s://%s/%s", HTTPS_SCHEME, LOCALHOST_IP, "path"));
        Optional<Uri> localhostIpWithPathUri =
                WebAddresses.topPrivateDomainAndScheme(localhostIpWithPath);
        assertThat(localhostIpWithPathUri.isPresent()).isTrue();
        expect.withMessage("localhostIpWithPath")
                .that(localhostIpWithPathUri.get())
                .isEqualTo(HTTPS_LOCALHOST_IP);

        Uri localhostIpWithPort =
                Uri.parse(String.format("%s://%s:%s", HTTPS_SCHEME, LOCALHOST_IP, "4000"));
        Optional<Uri> localhostIpWithPortUri =
                WebAddresses.topPrivateDomainAndScheme(localhostIpWithPort);
        assertThat(localhostIpWithPortUri.isPresent()).isTrue();
        expect.withMessage("localhostIpWithPort")
                .that(localhostIpWithPortUri.get())
                .isEqualTo(HTTPS_LOCALHOST_IP);
    }

    @Test
    public void testOriginAndScheme_validPublicDomainAndHttpsScheme() {
        Optional<Uri> output = WebAddresses.originAndScheme(HTTPS_PRIVATE_DOMAIN_COM_PUBLIC_SUFFIX);
        assertThat(output.isPresent()).isTrue();
        expect.that(output.get()).isEqualTo(HTTPS_PRIVATE_DOMAIN_COM_PUBLIC_SUFFIX);
    }

    @Test
    public void testOriginAndScheme_validLocalhost() {
        // Localhost
        Optional<Uri> localhost = WebAddresses.originAndScheme(HTTPS_LOCALHOST);
        assertWithMessage("localhost").that(localhost.isPresent()).isTrue();
        expect.withMessage("localhost").that(localhost.get()).isEqualTo(HTTPS_LOCALHOST);

        Uri localhostWithPath =
                Uri.parse(String.format("%s://%s/%s", HTTPS_SCHEME, LOCALHOST, "path"));
        Optional<Uri> localhostWithPathUri = WebAddresses.originAndScheme(localhostWithPath);
        assertWithMessage("localhostWithPath").that(localhostWithPathUri.isPresent()).isTrue();
        expect.withMessage("localhostWithPath")
                .that(localhostWithPathUri.get())
                .isEqualTo(HTTPS_LOCALHOST);

        Uri localhostWithPort =
                Uri.parse(String.format("%s://%s:%s", HTTPS_SCHEME, LOCALHOST, "4000"));
        Optional<Uri> localhostWithPortUri = WebAddresses.originAndScheme(localhostWithPort);
        assertWithMessage("localhostWithPort").that(localhostWithPortUri.isPresent()).isTrue();
        expect.withMessage("localhostWithPort")
                .that(localhostWithPortUri.get())
                .isEqualTo(localhostWithPort);

        // localhost ip
        Optional<Uri> localhost_ip = WebAddresses.originAndScheme(HTTPS_LOCALHOST_IP);
        assertWithMessage("localhost_ip").that(localhost_ip.isPresent()).isTrue();
        expect.withMessage("localhost_ip").that(localhost_ip.get()).isEqualTo(HTTPS_LOCALHOST_IP);

        Uri localhostIpWithPath =
                Uri.parse(String.format("%s://%s/%s", HTTPS_SCHEME, LOCALHOST_IP, "path"));
        Optional<Uri> localhostIpWithPathUri = WebAddresses.originAndScheme(localhostIpWithPath);
        assertWithMessage("localhostIpWithPath").that(localhostIpWithPathUri.isPresent()).isTrue();
        expect.withMessage("localhostIpWithPath")
                .that(localhostIpWithPathUri.get())
                .isEqualTo(HTTPS_LOCALHOST_IP);

        Uri localhostIpWithPort =
                Uri.parse(String.format("%s://%s:%s", HTTPS_SCHEME, LOCALHOST_IP, "4000"));
        Optional<Uri> localhostIpWithPortUri = WebAddresses.originAndScheme(localhostIpWithPort);
        assertWithMessage("localhostIpWithPort").that(localhostIpWithPortUri.isPresent()).isTrue();
        expect.withMessage("localhostIpWithPort")
                .that(localhostIpWithPortUri.get())
                .isEqualTo(localhostIpWithPort);
    }

    @Test
    public void testTopPrivateDomainAndScheme_validPrivateDomainAndHttpsScheme() {
        Optional<Uri> output =
                WebAddresses.topPrivateDomainAndScheme(
                        HTTPS_PRIVATE_DOMAIN_BLOGSPOT_COM_PUBLIC_SUFFIX);
        assertThat(output.isPresent()).isTrue();
        expect.that(output.get()).isEqualTo(HTTPS_PRIVATE_DOMAIN_BLOGSPOT_COM_PUBLIC_SUFFIX);
    }

    @Test
    public void testOriginAndScheme_validPrivateDomainAndHttpsScheme() {
        Optional<Uri> output = WebAddresses.originAndScheme(
                HTTPS_PRIVATE_DOMAIN_BLOGSPOT_COM_PUBLIC_SUFFIX);
        assertThat(output.isPresent()).isTrue();
        expect.that(output.get()).isEqualTo(HTTPS_PRIVATE_DOMAIN_BLOGSPOT_COM_PUBLIC_SUFFIX);
    }

    @Test
    public void testTopPrivateDomainAndScheme_validPublicDomainAndHttpsScheme_extraSubdomain() {
        Optional<Uri> output =
                WebAddresses.topPrivateDomainAndScheme(
                        HTTPS_SUBDOMAIN_PRIVATE_DOMAIN_COM_PUBLIC_SUFFIX);
        assertThat(output.isPresent()).isTrue();
        expect.that(output.get()).isEqualTo(HTTPS_PRIVATE_DOMAIN_COM_PUBLIC_SUFFIX);
    }

    @Test
    public void testOriginAndScheme_validPublicDomainAndHttpsScheme_extraSubdomain() {
        Optional<Uri> output =
                WebAddresses.originAndScheme(HTTPS_SUBDOMAIN_PRIVATE_DOMAIN_COM_PUBLIC_SUFFIX);
        assertThat(output.isPresent()).isTrue();
        expect.that(output.get()).isEqualTo(HTTPS_SUBDOMAIN_PRIVATE_DOMAIN_COM_PUBLIC_SUFFIX);
    }

    @Test
    public void testTopPrivateDomainAndScheme_validPrivateDomainAndHttpsScheme_extraSubdomain() {
        Optional<Uri> output =
                WebAddresses.topPrivateDomainAndScheme(
                        HTTPS_SUBDOMAIN_PRIVATE_DOMAIN_BLOGSPOT_COM_PUBLIC_SUFFIX);
        assertThat(output.isPresent()).isTrue();
        expect.that(output.get()).isEqualTo(HTTPS_PRIVATE_DOMAIN_BLOGSPOT_COM_PUBLIC_SUFFIX);
    }

    @Test
    public void testOriginAndScheme_validPrivateDomainAndHttpsScheme_extraSubdomain() {
        Optional<Uri> output =
                WebAddresses.originAndScheme(
                        HTTPS_SUBDOMAIN_PRIVATE_DOMAIN_BLOGSPOT_COM_PUBLIC_SUFFIX);
        assertThat(output.isPresent()).isTrue();
        expect.that(output.get())
                .isEqualTo(HTTPS_SUBDOMAIN_PRIVATE_DOMAIN_BLOGSPOT_COM_PUBLIC_SUFFIX);
    }

    @Test
    public void testTopPrivateDomainAndScheme_validPublicDomainAndHttpScheme() {
        Optional<Uri> output = WebAddresses.topPrivateDomainAndScheme(
                HTTP_PRIVATE_DOMAIN_COM_PUBLIC_SUFFIX);
        assertThat(output.isPresent()).isTrue();
        expect.that(output.get()).isEqualTo(HTTP_PRIVATE_DOMAIN_COM_PUBLIC_SUFFIX);
    }

    @Test
    public void testOriginAndScheme_validPublicDomainAndHttpScheme() {
        Optional<Uri> output = WebAddresses.originAndScheme(HTTP_PRIVATE_DOMAIN_COM_PUBLIC_SUFFIX);
        assertThat(output.isPresent()).isTrue();
        expect.that(output.get()).isEqualTo(HTTP_PRIVATE_DOMAIN_COM_PUBLIC_SUFFIX);
    }

    @Test
    public void testTopPrivateDomainAndScheme_validPublicDomainAndPortAndHttpsScheme() {
        Optional<Uri> output =
                WebAddresses.topPrivateDomainAndScheme(HTTPS_PRIVATE_DOMAIN_COM_PUBLIC_SUFFIX_PORT);
        assertThat(output.isPresent()).isTrue();
        expect.that(output.get()).isEqualTo(HTTPS_PRIVATE_DOMAIN_COM_PUBLIC_SUFFIX);
    }

    @Test
    public void testOriginAndScheme_validPublicDomainAndPortAndHttpsScheme() {
        Optional<Uri> output = WebAddresses.originAndScheme(
                HTTPS_PRIVATE_DOMAIN_COM_PUBLIC_SUFFIX_PORT);
        assertThat(output.isPresent()).isTrue();
        expect.that(output.get()).isEqualTo(HTTPS_PRIVATE_DOMAIN_COM_PUBLIC_SUFFIX_PORT);
    }

    @Test
    public void testTopPrivateDomainAndPath_forInvalidUri_returnsEmptyOptional() {
        Optional<Uri> output = WebAddresses.topPrivateDomainAndScheme(Uri.parse(INVALID_URL));
        assertThat(output.isPresent()).isFalse();
    }

    @Test
    public void testOriginAndScheme_forInvalidUri_returnsEmptyOptional() {
        Optional<Uri> output = WebAddresses.originAndScheme(Uri.parse(INVALID_URL));
        assertThat(output.isPresent()).isFalse();
    }

    @Test
    public void testTopPrivateDomainAndScheme_invalidTldAndHttpScheme_returnsEmptyOptional() {
        String inputUrl = String.format("%s://%s.%s", HTTP_SCHEME, TOP_PRIVATE_DOMAIN, INVALID_TLD);
        Optional<Uri> output = WebAddresses.topPrivateDomainAndScheme(Uri.parse(inputUrl));
        assertThat(output.isPresent()).isFalse();
    }

    @Test
    public void testTopPrivateDomainAndScheme_invalidLocalhostScheme_returnsEmptyOptional() {
        Uri inputUrl = Uri.parse(String.format("%s://%s", HTTP_SCHEME, LOCALHOST));
        Optional<Uri> output = WebAddresses.topPrivateDomainAndScheme(inputUrl);
        assertThat(output.isPresent()).isFalse();
    }

    @Test
    public void testTopPrivateDomainAndScheme_invalidLocalhost_returnsEmptyOptional() {
        List<String> invalidUris =
                List.of(
                        String.format("%s://%s", HTTPS_SCHEME, "localyhost"),
                        String.format("%s://%s", HTTPS_SCHEME, "localhosts/path"),
                        String.format("%s://%s", HTTPS_SCHEME, "localhosts:8000"),
                        String.format("%s://%s", HTTPS_SCHEME, "128.0.0.1"),
                        String.format("%s://%s", HTTPS_SCHEME, "127.1.0.1/path"),
                        String.format("%s://%s", HTTPS_SCHEME, "127.0.0.2:7654"));

        for (String invalidUri : invalidUris) {
            expect.withMessage(invalidUri)
                    .that(WebAddresses.topPrivateDomainAndScheme(Uri.parse(invalidUri)).isPresent())
                    .isFalse();
        }
    }

    @Test
    public void testOriginAndScheme_invalidTldAndHttpScheme_returnsEmptyOptional() {
        String inputUrl = String.format("%s://%s.%s", HTTP_SCHEME, TOP_PRIVATE_DOMAIN, INVALID_TLD);
        Optional<Uri> output = WebAddresses.originAndScheme(Uri.parse(inputUrl));
        assertThat(output.isPresent()).isFalse();
    }

    @Test
    public void testOriginAndScheme_invalidTldAndHttpsScheme_returnsEmptyOptional() {
        String inputUrl =
                String.format("%s://%s.%s", HTTPS_SCHEME, TOP_PRIVATE_DOMAIN, INVALID_TLD);
        Optional<Uri> output = WebAddresses.originAndScheme(Uri.parse(inputUrl));
        assertThat(output.isPresent()).isFalse();
    }

    @Test
    public void testOriginAndScheme_invalidTldAndHttpSchemeAndSubdomain_returnsEmptyOptional() {
        String inputUrl =
                String.format(
                        "%s://%s.%s.%s", HTTP_SCHEME, SUBDOMAIN, TOP_PRIVATE_DOMAIN, INVALID_TLD);
        Optional<Uri> output = WebAddresses.originAndScheme(Uri.parse(inputUrl));
        assertThat(output.isPresent()).isFalse();
    }

    @Test
    public void testOriginAndScheme_invalidTldAndHttpsSchemeAndSubdomain_returnsEmptyOptional() {
        String inputUrl =
                String.format(
                        "%s://%s.%s.%s", HTTPS_SCHEME, SUBDOMAIN, TOP_PRIVATE_DOMAIN, INVALID_TLD);
        Optional<Uri> output = WebAddresses.originAndScheme(Uri.parse(inputUrl));
        assertThat(output.isPresent()).isFalse();
    }

    @Test
    public void testOriginAndScheme_invalidLocalhostScheme() {
        Uri inputUrl = Uri.parse(String.format("%s://%s", HTTP_SCHEME, LOCALHOST));
        Optional<Uri> output = WebAddresses.originAndScheme(inputUrl);
        assertThat(output.isPresent()).isFalse();
    }

    @Test
    public void testOriginAndScheme_invalidLocalhost() {
        List<String> invalidUris =
                List.of(
                        String.format("%s://%s", HTTPS_SCHEME, "localyhost"),
                        String.format("%s://%s", HTTPS_SCHEME, "localhosts/path"),
                        String.format("%s://%s", HTTPS_SCHEME, "localhosts:8000"),
                        String.format("%s://%s", HTTPS_SCHEME, "128.0.0.1"),
                        String.format("%s://%s", HTTPS_SCHEME, "127.1.0.1/path"),
                        String.format("%s://%s", HTTPS_SCHEME, "127.0.0.2:7654"));

        for (String invalidUri : invalidUris) {
            expect.withMessage(invalidUri)
                    .that(WebAddresses.originAndScheme(Uri.parse(invalidUri)).isPresent())
                    .isFalse();
        }
    }

    @Test
    public void testIsLocalHost_success() {
        List<String> uris =
                List.of(
                        "https://127.0.0.1",
                        "https://127.0.0.1:5000/path",
                        "https://127.0.0.1/path",
                        "https://localhost",
                        "https://localhost:5000/path",
                        "https://localhost/path");

        for (String uri : uris) {
            expect.withMessage(uri).that(WebAddresses.isLocalhost(Uri.parse(uri))).isTrue();
        }
    }

    @Test
    public void testIsLocalHost_wrongScheme() {
        List<Uri> uris =
                List.of(
                        Uri.parse("android-app://com.example"),
                        WebUtil.validUri("http://example.test:8000"),
                        Uri.parse("http://127.0.0.1:5000/path"),
                        Uri.parse("http://127.0.0.1/path"),
                        Uri.parse("127.0.0.1:5000/path"),
                        Uri.parse("127.0.0.1/path"),
                        Uri.parse("http://localhost:5000/path"),
                        Uri.parse("http://localhost/path"),
                        Uri.parse("localhost:5000/path"),
                        Uri.parse("localhost/path"));

        for (Uri uri : uris) {
            expect.withMessage(uri.toSafeString()).that(WebAddresses.isLocalhost(uri)).isFalse();
        }
    }

    @Test
    public void testIsLocalHost_wrongHost() {
        List<String> uris =
                List.of(
                        "https://128.0.0.1",
                        "https://127.56.0.1:5000/path",
                        "https://127.0.1.1/path",
                        "https://localhosts",
                        "https://not-localhost:5000/path",
                        "https://localyhost/path");

        for (String uri : uris) {
            expect.withMessage(uri).that(WebAddresses.isLocalhost(Uri.parse(uri))).isFalse();
        }
    }

    @Test
    public void testIsLocalHostIp_success() {
        List<String> uris =
                List.of(
                        "https://127.0.0.1",
                        "https://127.0.0.1:5000/path",
                        "https://127.0.0.1/path");

        for (String uri : uris) {
            expect.withMessage(uri).that(WebAddresses.isLocalhostIp(Uri.parse(uri))).isTrue();
        }
    }

    @Test
    public void testIsLocalHostIp_wrongScheme() {
        List<Uri> uris =
                List.of(
                        Uri.parse("android-app://com.example"),
                        WebUtil.validUri("http://example.test:8000"),
                        Uri.parse("http://127.0.0.1:5000/path"),
                        Uri.parse("http://127.0.0.1/path"),
                        Uri.parse("127.0.0.1:5000/path"),
                        Uri.parse("127.0.0.1/path"));

        for (Uri uri : uris) {
            expect.withMessage(uri.toSafeString()).that(WebAddresses.isLocalhostIp(uri)).isFalse();
        }
    }

    @Test
    public void testIsLocalHostIp_wrongHost() {
        List<String> uris =
                List.of(
                        "https://localhost",
                        "https://localhost:5000/path",
                        "https://localhost/path",
                        "https://128.0.0.1",
                        "https://127.56.0.1:5000/path",
                        "https://127.0.1.1/path",
                        "https://localhosts",
                        "https://not-localhost:5000/path",
                        "https://localyhost/path");

        for (String uri : uris) {
            expect.withMessage(uri).that(WebAddresses.isLocalhostIp(Uri.parse(uri))).isFalse();
        }
    }
}

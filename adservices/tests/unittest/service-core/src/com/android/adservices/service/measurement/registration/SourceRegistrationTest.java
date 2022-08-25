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

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertNull;
import static org.junit.Assert.assertThrows;

import android.net.Uri;

import androidx.test.filters.SmallTest;

import org.junit.Test;


/**
 * Unit tests for {@link SourceRegistration}
 */
@SmallTest
public final class SourceRegistrationTest {
    private static final Uri TOP_ORIGIN = Uri.parse("https://foo.com");
    private static final Uri REGISTRATION_URI = Uri.parse("https://bar.com");
    private static final Long DEBUG_KEY = 2376843L;

    private SourceRegistration createExampleResponse() {

        return new SourceRegistration.Builder()
                .setTopOrigin(TOP_ORIGIN)
                .setRegistrationUri(REGISTRATION_URI)
                .setAppDestination(Uri.parse("android-app://baz.com"))
                .setSourceEventId(1234567)
                .setExpiry(2345678)
                .setSourcePriority(345678)
                .setAggregateSource(
                        "[{\"id\" : \"campaignCounts\", \"key_piece\" : \"0x159\"},"
                                + "{\"id\" : \"geoValue\", \"key_piece\" : \"0x5\"}]")
                .setAggregateFilterData("{\"product\":[\"1234\",\"2345\"],\"ctid\":[\"id\"]}")
                .setDebugKey(DEBUG_KEY)
                .build();
    }

    void verifyExampleResponse(SourceRegistration response) {
        assertEquals("https://foo.com", response.getTopOrigin().toString());
        assertEquals("https://bar.com", response.getRegistrationUri().toString());
        assertEquals("android-app://baz.com", response.getAppDestination().toString());
        assertEquals(1234567, response.getSourceEventId());
        assertEquals(2345678, response.getExpiry());
        assertEquals(345678, response.getSourcePriority());
        assertEquals(DEBUG_KEY, response.getDebugKey());
        assertEquals(
                "[{\"id\" : \"campaignCounts\", \"key_piece\" : \"0x159\"},"
                        + "{\"id\" : \"geoValue\", \"key_piece\" : \"0x5\"}]",
                response.getAggregateSource());
        assertEquals("{\"product\":[\"1234\",\"2345\"],\"ctid\":[\"id\"]}",
                response.getAggregateFilterData());
    }

    @Test
    public void testCreation() throws Exception {
        verifyExampleResponse(createExampleResponse());
    }

    @Test
    public void sourceRegistration_onlyAppDestination_success() {
        Uri destination = Uri.parse("android-app://baz.com");
        SourceRegistration response =
                new SourceRegistration.Builder()
                        .setTopOrigin(TOP_ORIGIN)
                        .setRegistrationUri(REGISTRATION_URI)
                        .setAppDestination(destination)
                        .build();
        assertEquals(TOP_ORIGIN, response.getTopOrigin());
        assertEquals(REGISTRATION_URI, response.getRegistrationUri());
        assertEquals(destination, response.getAppDestination());
        assertNull(response.getWebDestination());
        assertEquals(0, response.getSourceEventId());
        assertEquals(MAX_REPORTING_REGISTER_SOURCE_EXPIRATION_IN_SECONDS, response.getExpiry());
        assertEquals(0, response.getSourcePriority());
        assertNull(response.getAggregateSource());
        assertNull(response.getAggregateFilterData());
    }

    @Test
    public void sourceRegistration_onlyWebDestination_success() {
        Uri destination = Uri.parse("https://baz.com");
        SourceRegistration response =
                new SourceRegistration.Builder()
                        .setTopOrigin(TOP_ORIGIN)
                        .setRegistrationUri(REGISTRATION_URI)
                        .setWebDestination(destination)
                        .build();
        assertEquals(TOP_ORIGIN, response.getTopOrigin());
        assertEquals(REGISTRATION_URI, response.getRegistrationUri());
        assertEquals(destination, response.getWebDestination());
        assertNull(response.getAppDestination());
        assertEquals(0, response.getSourceEventId());
        assertEquals(MAX_REPORTING_REGISTER_SOURCE_EXPIRATION_IN_SECONDS, response.getExpiry());
        assertEquals(0, response.getSourcePriority());
        assertNull(response.getAggregateSource());
        assertNull(response.getAggregateFilterData());
    }

    @Test
    public void sourceRegistration_bothOsAndWebDestination_success() {
        Uri webDestination = Uri.parse("https://baz.com");
        Uri destination = Uri.parse("android-app://baz.com");
        SourceRegistration response =
                new SourceRegistration.Builder()
                        .setTopOrigin(TOP_ORIGIN)
                        .setRegistrationUri(REGISTRATION_URI)
                        .setAppDestination(destination)
                        .setWebDestination(webDestination)
                        .build();
        assertEquals(TOP_ORIGIN, response.getTopOrigin());
        assertEquals(REGISTRATION_URI, response.getRegistrationUri());
        assertEquals(webDestination, response.getWebDestination());
        assertEquals(destination, response.getAppDestination());
        assertEquals(0, response.getSourceEventId());
        assertEquals(MAX_REPORTING_REGISTER_SOURCE_EXPIRATION_IN_SECONDS, response.getExpiry());
        assertEquals(0, response.getSourcePriority());
        assertNull(response.getAggregateSource());
        assertNull(response.getAggregateFilterData());
    }

    @Test
    public void sourceRegistration_bothDestinationsNull_throwsException() {
        assertThrows(
                IllegalArgumentException.class,
                () ->
                        new SourceRegistration.Builder()
                                .setAppDestination(null)
                                .setWebDestination(null)
                                .build());
    }

    @Test
    public void equals_success() {
        assertEquals(createExampleResponse(), createExampleResponse());
    }
}

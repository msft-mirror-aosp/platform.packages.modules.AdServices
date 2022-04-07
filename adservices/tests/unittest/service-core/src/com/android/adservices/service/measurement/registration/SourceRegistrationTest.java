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

import android.content.Context;
import android.net.Uri;

import androidx.test.InstrumentationRegistry;
import androidx.test.filters.SmallTest;

import org.junit.Test;


/**
 * Unit tests for {@link SourceRegistration}
 */
@SmallTest
public final class SourceRegistrationTest {
    private static final String TAG = "SourceRegistrationTest";

    private static final Context sContext = InstrumentationRegistry.getTargetContext();

    private SourceRegistration createExampleResponse() {
        return new SourceRegistration.Builder()
            .setTopOrigin(Uri.parse("https://foo.com"))
            .setReportingOrigin(Uri.parse("https://bar.com"))
            .setDestination(Uri.parse("android-app://baz.com"))
            .setSourceEventId(1234567)
            .setExpiry(2345678)
            .setSourcePriority(345678)
            .build();
    }

    void verifyExampleResponse(SourceRegistration response) {
        assertEquals("https://foo.com", response.getTopOrigin().toString());
        assertEquals("https://bar.com", response.getReportingOrigin().toString());
        assertEquals("android-app://baz.com", response.getDestination().toString());
        assertEquals(1234567, response.getSourceEventId());
        assertEquals(2345678, response.getExpiry());
        assertEquals(345678, response.getSourcePriority());
    }

    @Test
    public void testCreation() throws Exception {
        verifyExampleResponse(createExampleResponse());
    }

    @Test
    public void testDefaults() throws Exception {
        SourceRegistration response = new SourceRegistration.Builder().build();
        assertEquals("", response.getTopOrigin().toString());
        assertEquals("", response.getReportingOrigin().toString());
        assertEquals("", response.getDestination().toString());
        assertEquals(0, response.getSourceEventId());
        assertEquals(0, response.getExpiry());
        assertEquals(0, response.getSourcePriority());
    }
}

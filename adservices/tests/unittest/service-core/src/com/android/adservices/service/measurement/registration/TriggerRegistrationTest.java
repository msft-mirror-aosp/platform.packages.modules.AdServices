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
 * Unit tests for {@link TriggerRegistration}
 */
@SmallTest
public final class TriggerRegistrationTest {
    private static final String TAG = "TriggerRegistrationTest";

    private static final Context sContext = InstrumentationRegistry.getTargetContext();

    private TriggerRegistration createExampleResponse() {
        return new TriggerRegistration.Builder()
            .setTopOrigin(Uri.parse("https://foo.com"))
            .setReportingOrigin(Uri.parse("https://bar.com"))
            .setTriggerData(1)
            .setTriggerPriority(345678)
            .setDeduplicationKey(2345678)
            .build();
    }

    void verifyExampleResponse(TriggerRegistration response) {
        assertEquals("https://foo.com", response.getTopOrigin().toString());
        assertEquals("https://bar.com", response.getReportingOrigin().toString());
        assertEquals(1, response.getTriggerData());
        assertEquals(345678, response.getTriggerPriority());
        assertEquals(2345678, response.getDeduplicationKey());
    }

    @Test
    public void testCreation() throws Exception {
        verifyExampleResponse(createExampleResponse());
    }

    @Test
    public void testDefaults() throws Exception {
        TriggerRegistration response = new TriggerRegistration.Builder().build();
        assertEquals("", response.getTopOrigin().toString());
        assertEquals("", response.getReportingOrigin().toString());
        assertEquals(0, response.getTriggerData());
        assertEquals(0, response.getTriggerPriority());
        assertEquals(0, response.getDeduplicationKey());
    }
}

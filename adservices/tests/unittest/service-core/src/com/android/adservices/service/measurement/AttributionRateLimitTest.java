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
package com.android.adservices.service.measurement;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertNull;

import android.net.Uri;

import androidx.test.filters.SmallTest;

import org.junit.Test;

/** Unit tests for {@link AttributionRateLimit} */
@SmallTest
public final class AttributionRateLimitTest {

    public static final Uri URI_SRC = Uri.parse("android-app://com.example1.src");
    public static final Uri URI_DEST = Uri.parse("android-app://com.example2.dest");
    public static final Uri URI_REPORT = Uri.parse("https://example3.com/report");
    public static final Uri URI_REGISTERER = Uri.parse("android-app://com.example4.registerer");

    private AttributionRateLimit createExample() {
        return new AttributionRateLimit.Builder()
                .setId("id")
                .setSourceSite(URI_SRC)
                .setDestinationSite(URI_DEST)
                .setReportTo(URI_REPORT)
                .setTriggerTime(1L)
                .setRegisterer(URI_REGISTERER)
                .build();
    }

    @Test
    public void testCreation() {
        AttributionRateLimit attributionRateLimit = createExample();
        assertEquals("id", attributionRateLimit.getId());
        assertEquals(URI_SRC, attributionRateLimit.getSourceSite());
        assertEquals(URI_DEST, attributionRateLimit.getDestinationSite());
        assertEquals(URI_REPORT, attributionRateLimit.getReportTo());
        assertEquals(1L, attributionRateLimit.getTriggerTime());
        assertEquals(URI_REGISTERER, attributionRateLimit.getRegisterer());
    }

    @Test
    public void testDefaults() {
        AttributionRateLimit attributionRateLimit = new AttributionRateLimit.Builder().build();
        assertNull(attributionRateLimit.getId());
        assertNull(attributionRateLimit.getSourceSite());
        assertNull(attributionRateLimit.getDestinationSite());
        assertNull(attributionRateLimit.getReportTo());
        assertEquals(0L, attributionRateLimit.getTriggerTime());
        assertNull(attributionRateLimit.getRegisterer());
    }
}

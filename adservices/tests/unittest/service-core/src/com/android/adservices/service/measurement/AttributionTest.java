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
import static org.junit.Assert.assertNotEquals;
import static org.junit.Assert.assertThrows;

import org.junit.Test;

public class AttributionTest {
    private static final String ID = "AR1";
    private static final String DESTINATION_ORIGIN = "https://destination.com/origin";
    private static final String DESTINATION_SITE = "https://destination.com";
    private static final String PUBLISHER_ORIGIN = "android-app://com.publisher/abc";
    private static final String PUBLISHER_SITE = "android-app://com.publisher";
    private static final String REGISTRANT = "android-app://com.registrant";
    private static final String AD_TECH_DOMAIN = "https://com.example";
    private static final long TRIGGER_TIME = 10000L;
    private static final String SOME_OTHER_STRING = "some_other";
    private static final long SOME_OTHER_LONG = 1L;

    @Test
    public void equals_pass() {
        assertEquals(
                createExampleAttributionRateLimitBuilder().build(),
                createExampleAttributionRateLimitBuilder().build());
        assertEquals(
                createExampleAttributionRateLimitBuilder().setId(SOME_OTHER_STRING).build(),
                createExampleAttributionRateLimitBuilder().build());
    }

    @Test
    public void equals_fail() {
        assertNotEquals(
                createExampleAttributionRateLimitBuilder().setRegistrant(SOME_OTHER_STRING).build(),
                createExampleAttributionRateLimitBuilder().build());
        assertNotEquals(
                createExampleAttributionRateLimitBuilder()
                        .setAdTechDomain(SOME_OTHER_STRING)
                        .build(),
                createExampleAttributionRateLimitBuilder().build());
        assertNotEquals(
                createExampleAttributionRateLimitBuilder()
                        .setDestinationSite(SOME_OTHER_STRING)
                        .build(),
                createExampleAttributionRateLimitBuilder().build());
        assertNotEquals(
                createExampleAttributionRateLimitBuilder()
                        .setDestinationOrigin(SOME_OTHER_STRING)
                        .build(),
                createExampleAttributionRateLimitBuilder().build());
        assertNotEquals(
                createExampleAttributionRateLimitBuilder().setSourceSite(SOME_OTHER_STRING).build(),
                createExampleAttributionRateLimitBuilder().build());
        assertNotEquals(
                createExampleAttributionRateLimitBuilder()
                        .setSourceOrigin(SOME_OTHER_STRING)
                        .build(),
                createExampleAttributionRateLimitBuilder().build());
        assertNotEquals(
                createExampleAttributionRateLimitBuilder().setTriggerTime(SOME_OTHER_LONG).build(),
                createExampleAttributionRateLimitBuilder().build());
    }

    @Test
    public void hashCode_pass() {
        assertEquals(
                createExampleAttributionRateLimitBuilder().build().hashCode(),
                createExampleAttributionRateLimitBuilder().build().hashCode());
    }

    @Test
    public void hashCode_fail() {
        assertNotEquals(
                createExampleAttributionRateLimitBuilder()
                        .setRegistrant(SOME_OTHER_STRING)
                        .build()
                        .hashCode(),
                createExampleAttributionRateLimitBuilder().build().hashCode());
        assertNotEquals(
                createExampleAttributionRateLimitBuilder()
                        .setAdTechDomain(SOME_OTHER_STRING)
                        .build()
                        .hashCode(),
                createExampleAttributionRateLimitBuilder().build().hashCode());
        assertNotEquals(
                createExampleAttributionRateLimitBuilder()
                        .setDestinationSite(SOME_OTHER_STRING)
                        .build()
                        .hashCode(),
                createExampleAttributionRateLimitBuilder().build().hashCode());
        assertNotEquals(
                createExampleAttributionRateLimitBuilder()
                        .setDestinationOrigin(SOME_OTHER_STRING)
                        .build()
                        .hashCode(),
                createExampleAttributionRateLimitBuilder().build().hashCode());
        assertNotEquals(
                createExampleAttributionRateLimitBuilder()
                        .setSourceSite(SOME_OTHER_STRING)
                        .build()
                        .hashCode(),
                createExampleAttributionRateLimitBuilder().build().hashCode());
        assertNotEquals(
                createExampleAttributionRateLimitBuilder()
                        .setSourceOrigin(SOME_OTHER_STRING)
                        .build()
                        .hashCode(),
                createExampleAttributionRateLimitBuilder().build().hashCode());
        assertNotEquals(
                createExampleAttributionRateLimitBuilder()
                        .setTriggerTime(SOME_OTHER_LONG)
                        .build()
                        .hashCode(),
                createExampleAttributionRateLimitBuilder().build().hashCode());
    }

    @Test
    public void getters() {
        assertEquals(
                REGISTRANT, createExampleAttributionRateLimitBuilder().build().getRegistrant());
        assertEquals(
                DESTINATION_SITE,
                createExampleAttributionRateLimitBuilder().build().getDestinationSite());
        assertEquals(
                DESTINATION_ORIGIN,
                createExampleAttributionRateLimitBuilder().build().getDestinationOrigin());
        assertEquals(
                PUBLISHER_ORIGIN,
                createExampleAttributionRateLimitBuilder().build().getSourceOrigin());
        assertEquals(
                PUBLISHER_SITE, createExampleAttributionRateLimitBuilder().build().getSourceSite());
        assertEquals(
                TRIGGER_TIME, createExampleAttributionRateLimitBuilder().build().getTriggerTime());
        assertEquals(
                AD_TECH_DOMAIN,
                createExampleAttributionRateLimitBuilder().build().getAdTechDomain());
    }

    @Test
    public void validate() {
        assertThrows(
                IllegalArgumentException.class,
                () -> createExampleAttributionRateLimitBuilder().setSourceSite(null).build());
        assertThrows(
                IllegalArgumentException.class,
                () -> createExampleAttributionRateLimitBuilder().setRegistrant(null).build());
        assertThrows(
                IllegalArgumentException.class,
                () -> createExampleAttributionRateLimitBuilder().setAdTechDomain(null).build());
        assertThrows(
                IllegalArgumentException.class,
                () -> createExampleAttributionRateLimitBuilder().setDestinationSite(null).build());
        assertThrows(
                IllegalArgumentException.class,
                () ->
                        createExampleAttributionRateLimitBuilder()
                                .setDestinationOrigin(null)
                                .build());
        assertThrows(
                IllegalArgumentException.class,
                () -> createExampleAttributionRateLimitBuilder().setSourceOrigin(null).build());
    }

    private static Attribution.Builder createExampleAttributionRateLimitBuilder() {
        return new Attribution.Builder()
                .setId(ID)
                .setRegistrant(REGISTRANT)
                .setTriggerTime(TRIGGER_TIME)
                .setAdTechDomain(AD_TECH_DOMAIN)
                .setDestinationOrigin(DESTINATION_ORIGIN)
                .setDestinationSite(DESTINATION_SITE)
                .setSourceOrigin(PUBLISHER_ORIGIN)
                .setSourceSite(PUBLISHER_SITE);
    }
}

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

import android.net.Uri;

import org.junit.Test;

public class TriggerTest {

    @Test
    public void testEqualsPass() {
        assertEquals(new Trigger.Builder().build(), new Trigger.Builder().build());
        assertEquals(
                new Trigger.Builder()
                        .setReportTo(Uri.parse("https://example.com/rT"))
                        .setAttributionDestination(Uri.parse("https://example.com/aD"))
                        .setId("1")
                        .setTriggerData(1L)
                        .setPriority(3L)
                        .setTriggerTime(5L)
                        .setDedupKey(6L)
                        .setStatus(Trigger.Status.PENDING)
                        .setRegistrant(Uri.parse("android-app://com.example.abc"))
                        .build(),
                new Trigger.Builder()
                        .setReportTo(Uri.parse("https://example.com/rT"))
                        .setAttributionDestination(Uri.parse("https://example.com/aD"))
                        .setId("1")
                        .setTriggerData(1L)
                        .setPriority(3L)
                        .setTriggerTime(5L)
                        .setDedupKey(6L)
                        .setStatus(Trigger.Status.PENDING)
                        .setRegistrant(Uri.parse("android-app://com.example.abc"))
                        .build());
    }

    @Test
    public void testEqualsFail() {
        assertNotEquals(
                new Trigger.Builder().setId("1").build(),
                new Trigger.Builder().setId("2").build());
        assertNotEquals(
                new Trigger.Builder().setAttributionDestination(Uri.parse("1")).build(),
                new Trigger.Builder().setAttributionDestination(Uri.parse("2")).build());
        assertNotEquals(
                new Trigger.Builder().setReportTo(Uri.parse("1")).build(),
                new Trigger.Builder().setReportTo(Uri.parse("2")).build());
        assertNotEquals(
                new Trigger.Builder().setPriority(1L).build(),
                new Trigger.Builder().setPriority(2L).build());
        assertNotEquals(
                new Trigger.Builder().setTriggerTime(1L).build(),
                new Trigger.Builder().setTriggerTime(2L).build());
        assertNotEquals(
                new Trigger.Builder().setTriggerData(1L).build(),
                new Trigger.Builder().setTriggerData(2L).build());
        assertNotEquals(
                new Trigger.Builder().setStatus(Trigger.Status.PENDING).build(),
                new Trigger.Builder().setStatus(Trigger.Status.IGNORED).build());
        assertNotEquals(
                new Trigger.Builder().setDedupKey(1L).build(),
                new Trigger.Builder().setDedupKey(2L).build());
        assertNotEquals(
                new Trigger.Builder().setDedupKey(1L).build(),
                new Trigger.Builder().setDedupKey(null).build());
        assertNotEquals(
                new Trigger.Builder()
                        .setRegistrant(Uri.parse("android-app://com.example.abc"))
                        .build(),
                new Trigger.Builder()
                        .setRegistrant(Uri.parse("android-app://com.example.xyz"))
                        .build());
    }

    @Test
    public void testGetRandomizedTriggerData() {
        Source source = new Source.Builder()
                .setSourceType(Source.SourceType.NAVIGATION).build();
        Trigger trigger = new Trigger.Builder().setTriggerData(2L).build();
        int randomCount = 0;
        for (int i = 0; i < 5000; i++) {
            if (trigger.getTriggerData() != trigger.getRandomizedTriggerData(source)) {
                randomCount++;
            }
        }
        assertNotEquals(0, randomCount);
        assertNotEquals(5000, randomCount);
    }

    @Test
    public void getTruncatedTriggerDataNavigation() {
        Source source = new Source.Builder()
                .setSourceType(Source.SourceType.NAVIGATION).build();

        assertEquals(6, (new Trigger.Builder().setTriggerData(6).build())
                .getTruncatedTriggerData(source));
        assertEquals(7, (new Trigger.Builder().setTriggerData(7).build())
                .getTruncatedTriggerData(source));
        assertEquals(3, (new Trigger.Builder().setTriggerData(11).build())
                .getTruncatedTriggerData(source));
        assertEquals(4, (new Trigger.Builder().setTriggerData(12).build())
                .getTruncatedTriggerData(source));
        assertEquals(2, (new Trigger.Builder().setTriggerData(10).build())
                .getTruncatedTriggerData(source));
        assertEquals(7, (new Trigger.Builder().setTriggerData(127).build())
                .getTruncatedTriggerData(source));
    }

    @Test
    public void getTruncatedTriggerDataEvent() {
        Source source = new Source.Builder()
                .setSourceType(Source.SourceType.EVENT).build();

        assertEquals(0, (new Trigger.Builder().setTriggerData(0).build())
                .getTruncatedTriggerData(source));
        assertEquals(1, (new Trigger.Builder().setTriggerData(1).build())
                .getTruncatedTriggerData(source));
        assertEquals(0, (new Trigger.Builder().setTriggerData(2).build())
                .getTruncatedTriggerData(source));
        assertEquals(1, (new Trigger.Builder().setTriggerData(3).build())
                .getTruncatedTriggerData(source));
        assertEquals(1, (new Trigger.Builder().setTriggerData(101).build())
                .getTruncatedTriggerData(source));

    }
}

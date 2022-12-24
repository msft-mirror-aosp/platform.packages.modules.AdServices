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

package com.android.adservices.service.adselection;

import static com.android.adservices.service.adselection.EventUriRegistrationInfo.EXPECTED_STRUCTURE_MISMATCH;
import static com.android.adservices.service.adselection.ReportImpressionScriptEngine.EVENT_TYPE_ARG_NAME;
import static com.android.adservices.service.adselection.ReportImpressionScriptEngine.EVENT_URI_ARG_NAME;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertNotEquals;
import static org.junit.Assert.assertThrows;

import android.net.Uri;

import org.json.JSONArray;
import org.json.JSONObject;
import org.junit.Test;

public class EventUriRegistrationInfoTest {
    private static final String CLICK = "click";
    private static final String HOVER = "hover";
    private static final String EVENT_URI_STRING = "https://domain.com/click";
    private static final Uri EVENT_URI = Uri.parse(EVENT_URI_STRING);
    private static final Uri DIFFERENT_EVENT_URI = Uri.parse("https://different.com/click");

    @Test
    public void testFromJsonSucceeds() throws Exception {
        JSONObject obj = new JSONObject();
        obj.put(EVENT_TYPE_ARG_NAME, CLICK);
        obj.put(EVENT_URI_ARG_NAME, EVENT_URI_STRING);

        EventUriRegistrationInfo eventUriRegistrationInfo = EventUriRegistrationInfo.fromJson(obj);
        assertEquals(eventUriRegistrationInfo.getEventType(), CLICK);
        assertEquals(eventUriRegistrationInfo.getEventUri(), EVENT_URI);
    }

    @Test
    public void testFromJsonFailsWithWrongEventTypeKeyName() throws Exception {
        JSONObject obj = new JSONObject();
        obj.put(EVENT_TYPE_ARG_NAME + "wrong", CLICK);
        obj.put(EVENT_URI_ARG_NAME, EVENT_URI_STRING);

        assertThrows(
                EXPECTED_STRUCTURE_MISMATCH,
                IllegalArgumentException.class,
                () -> {
                    EventUriRegistrationInfo.fromJson(obj);
                });
    }

    @Test
    public void testFromJsonFailsWithWrongEventUriKeyName() throws Exception {
        JSONObject obj = new JSONObject();
        obj.put(EVENT_TYPE_ARG_NAME, CLICK);
        obj.put(EVENT_URI_ARG_NAME + "wrong", EVENT_URI_STRING);

        assertThrows(
                EXPECTED_STRUCTURE_MISMATCH,
                IllegalArgumentException.class,
                () -> {
                    EventUriRegistrationInfo.fromJson(obj);
                });
    }

    @Test
    public void testFromJsonFailsWhenEventTypeNotAString() throws Exception {
        JSONObject obj = new JSONObject();

        JSONObject innerObj = new JSONObject().put(EVENT_TYPE_ARG_NAME, CLICK);
        obj.put(EVENT_TYPE_ARG_NAME, innerObj);

        obj.put(EVENT_URI_ARG_NAME, EVENT_URI_STRING);

        assertThrows(
                EXPECTED_STRUCTURE_MISMATCH,
                IllegalArgumentException.class,
                () -> {
                    EventUriRegistrationInfo.fromJson(obj);
                });
    }

    @Test
    public void testFromJsonFailsWhenEventUriNotAString() throws Exception {
        JSONObject obj = new JSONObject();
        obj.put(EVENT_TYPE_ARG_NAME, CLICK);

        JSONObject innerObj = new JSONObject().put(EVENT_URI_ARG_NAME, EVENT_URI_STRING);
        obj.put(EVENT_URI_ARG_NAME, innerObj);

        assertThrows(
                EXPECTED_STRUCTURE_MISMATCH,
                IllegalArgumentException.class,
                () -> {
                    EventUriRegistrationInfo.fromJson(obj);
                });
    }

    @Test
    public void testFromJsonWithEmptyListValueFails() throws Exception {
        JSONObject obj = new JSONObject();
        obj.put(EVENT_TYPE_ARG_NAME, CLICK);
        obj.put(EVENT_URI_ARG_NAME, new JSONArray());

        assertThrows(
                EXPECTED_STRUCTURE_MISMATCH,
                IllegalArgumentException.class,
                () -> {
                    EventUriRegistrationInfo.fromJson(obj);
                });
    }

    @Test
    public void testFromJsonWithPopulatedListValueFails() throws Exception {
        JSONObject obj = new JSONObject();
        obj.put(EVENT_TYPE_ARG_NAME, CLICK);
        obj.put(EVENT_URI_ARG_NAME, new JSONArray().put(EVENT_URI_STRING));

        assertThrows(
                EXPECTED_STRUCTURE_MISMATCH,
                IllegalArgumentException.class,
                () -> {
                    EventUriRegistrationInfo.fromJson(obj);
                });
    }

    @Test
    public void testEqualsSuccessfulCase() throws Exception {
        EventUriRegistrationInfo eventUriRegistrationInfo1 =
                EventUriRegistrationInfo.builder()
                        .setEventType(CLICK)
                        .setEventUri(EVENT_URI)
                        .build();
        EventUriRegistrationInfo eventUriRegistrationInfo2 =
                EventUriRegistrationInfo.builder()
                        .setEventType(CLICK)
                        .setEventUri(EVENT_URI)
                        .build();

        assertEquals(eventUriRegistrationInfo1, eventUriRegistrationInfo2);
    }

    @Test
    public void testEqualsFailedCaseEventType() throws Exception {
        EventUriRegistrationInfo eventUriRegistrationInfo1 =
                EventUriRegistrationInfo.builder()
                        .setEventType(CLICK)
                        .setEventUri(EVENT_URI)
                        .build();
        EventUriRegistrationInfo eventUriRegistrationInfo2 =
                EventUriRegistrationInfo.builder()
                        .setEventType(HOVER)
                        .setEventUri(EVENT_URI)
                        .build();

        assertNotEquals(eventUriRegistrationInfo1, eventUriRegistrationInfo2);
    }

    @Test
    public void testEqualsFailedCaseEventUri() throws Exception {
        EventUriRegistrationInfo eventUriRegistrationInfo1 =
                EventUriRegistrationInfo.builder()
                        .setEventType(CLICK)
                        .setEventUri(EVENT_URI)
                        .build();
        EventUriRegistrationInfo eventUriRegistrationInfo2 =
                EventUriRegistrationInfo.builder()
                        .setEventType(CLICK)
                        .setEventUri(DIFFERENT_EVENT_URI)
                        .build();

        assertNotEquals(eventUriRegistrationInfo1, eventUriRegistrationInfo2);
    }

    @Test
    public void testHashCodeSuccessfulCase() throws Exception {
        EventUriRegistrationInfo eventUriRegistrationInfo1 =
                EventUriRegistrationInfo.builder()
                        .setEventType(CLICK)
                        .setEventUri(EVENT_URI)
                        .build();
        EventUriRegistrationInfo eventUriRegistrationInfo2 =
                EventUriRegistrationInfo.builder()
                        .setEventType(CLICK)
                        .setEventUri(EVENT_URI)
                        .build();

        assertEquals(eventUriRegistrationInfo1.hashCode(), eventUriRegistrationInfo2.hashCode());
    }

    @Test
    public void testHashCodeFailedCaseEventType() throws Exception {
        EventUriRegistrationInfo eventUriRegistrationInfo1 =
                EventUriRegistrationInfo.builder()
                        .setEventType(CLICK)
                        .setEventUri(EVENT_URI)
                        .build();
        EventUriRegistrationInfo eventUriRegistrationInfo2 =
                EventUriRegistrationInfo.builder()
                        .setEventType(HOVER)
                        .setEventUri(EVENT_URI)
                        .build();

        assertNotEquals(eventUriRegistrationInfo1.hashCode(), eventUriRegistrationInfo2.hashCode());
    }

    @Test
    public void testHashCodeFailedCaseEventUri() throws Exception {
        EventUriRegistrationInfo eventUriRegistrationInfo1 =
                EventUriRegistrationInfo.builder()
                        .setEventType(CLICK)
                        .setEventUri(EVENT_URI)
                        .build();
        EventUriRegistrationInfo eventUriRegistrationInfo2 =
                EventUriRegistrationInfo.builder()
                        .setEventType(CLICK)
                        .setEventUri(DIFFERENT_EVENT_URI)
                        .build();

        assertNotEquals(eventUriRegistrationInfo1.hashCode(), eventUriRegistrationInfo2.hashCode());
    }
}

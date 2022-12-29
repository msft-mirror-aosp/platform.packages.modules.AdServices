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

package com.android.adservices.data.adselection;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertThrows;

import android.net.Uri;

import org.junit.Test;

public class DBRegisteredAdEventTest {
    public static final int AD_SELECTION_ID = 1;
    public static final String EVENT_TYPE_CLICK = "CLICK";

    @DBRegisteredAdEvent.Destination
    public static final int DESTINATION_SELLER = DBRegisteredAdEvent.DESTINATION_SELLER;

    private static final String BASE_URI = "https://www.seller.com/";
    public static final Uri EVENT_REPORTING_URI = Uri.parse(BASE_URI + EVENT_TYPE_CLICK);

    @Test
    public void testBuildDBRegisteredEvent() {
        DBRegisteredAdEvent dbRegisteredAdEvent =
                DBRegisteredAdEvent.builder()
                        .setAdSelectionId(AD_SELECTION_ID)
                        .setEventType(EVENT_TYPE_CLICK)
                        .setDestination(DESTINATION_SELLER)
                        .setEventUri(EVENT_REPORTING_URI)
                        .build();

        assertEquals(AD_SELECTION_ID, dbRegisteredAdEvent.getAdSelectionId());
        assertEquals(EVENT_TYPE_CLICK, dbRegisteredAdEvent.getEventType());
        assertEquals(DESTINATION_SELLER, dbRegisteredAdEvent.getDestination());
        assertEquals(EVENT_REPORTING_URI, dbRegisteredAdEvent.getEventUri());
    }

    @Test
    public void testThrowsExceptionWithNoAdSelectionId() {
        assertThrows(
                IllegalStateException.class,
                () -> {
                    DBRegisteredAdEvent.builder()
                            .setEventType(EVENT_TYPE_CLICK)
                            .setDestination(DESTINATION_SELLER)
                            .setEventUri(EVENT_REPORTING_URI)
                            .build();
                });
    }

    @Test
    public void testThrowsExceptionWithNoEventType() {
        assertThrows(
                IllegalStateException.class,
                () -> {
                    DBRegisteredAdEvent.builder()
                            .setAdSelectionId(AD_SELECTION_ID)
                            .setDestination(DESTINATION_SELLER)
                            .setEventUri(EVENT_REPORTING_URI)
                            .build();
                });
    }

    @Test
    public void testThrowsExceptionWithNoDestination() {
        assertThrows(
                IllegalStateException.class,
                () -> {
                    DBRegisteredAdEvent.builder()
                            .setAdSelectionId(AD_SELECTION_ID)
                            .setEventType(EVENT_TYPE_CLICK)
                            .setEventUri(EVENT_REPORTING_URI)
                            .build();
                });
    }

    @Test
    public void testThrowsExceptionWithNoEventUri() {
        assertThrows(
                IllegalStateException.class,
                () -> {
                    DBRegisteredAdEvent.builder()
                            .setAdSelectionId(AD_SELECTION_ID)
                            .setEventType(EVENT_TYPE_CLICK)
                            .setDestination(DESTINATION_SELLER)
                            .build();
                });
    }
}

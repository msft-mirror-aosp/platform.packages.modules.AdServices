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

import static android.adservices.adselection.ReportEventRequest.FLAG_REPORTING_DESTINATION_SELLER;

import static com.android.adservices.data.adselection.DBRegisteredAdInteractionFixture.AD_SELECTION_ID;
import static com.android.adservices.data.adselection.DBRegisteredAdInteractionFixture.EVENT_REPORTING_URI;
import static com.android.adservices.data.adselection.DBRegisteredAdInteractionFixture.INTERACTION_KEY_CLICK;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertThrows;

import org.junit.Test;

public class DBRegisteredAdInteractionTest {

    @Test
    public void testBuildDBRegisteredAdInteraction() {
        DBRegisteredAdInteraction dbRegisteredAdInteraction =
                DBRegisteredAdInteractionFixture.getValidDbRegisteredAdInteractionBuilder().build();

        assertEquals(AD_SELECTION_ID, dbRegisteredAdInteraction.getAdSelectionId());
        assertEquals(INTERACTION_KEY_CLICK, dbRegisteredAdInteraction.getInteractionKey());
        assertEquals(FLAG_REPORTING_DESTINATION_SELLER, dbRegisteredAdInteraction.getDestination());
        assertEquals(EVENT_REPORTING_URI, dbRegisteredAdInteraction.getInteractionReportingUri());
    }

    @Test
    public void testThrowsExceptionWithNoAdSelectionId() {
        assertThrows(
                IllegalStateException.class,
                () -> {
                    DBRegisteredAdInteraction.builder()
                            .setInteractionKey(INTERACTION_KEY_CLICK)
                            .setDestination(FLAG_REPORTING_DESTINATION_SELLER)
                            .setInteractionReportingUri(EVENT_REPORTING_URI)
                            .build();
                });
    }

    @Test
    public void testThrowsExceptionWithNoInteractionKey() {
        assertThrows(
                IllegalStateException.class,
                () -> {
                    DBRegisteredAdInteraction.builder()
                            .setAdSelectionId(AD_SELECTION_ID)
                            .setDestination(FLAG_REPORTING_DESTINATION_SELLER)
                            .setInteractionReportingUri(EVENT_REPORTING_URI)
                            .build();
                });
    }

    @Test
    public void testThrowsExceptionWithNoDestination() {
        assertThrows(
                IllegalStateException.class,
                () -> {
                    DBRegisteredAdInteraction.builder()
                            .setAdSelectionId(AD_SELECTION_ID)
                            .setInteractionKey(INTERACTION_KEY_CLICK)
                            .setInteractionReportingUri(EVENT_REPORTING_URI)
                            .build();
                });
    }

    @Test
    public void testThrowsExceptionWithNoInteractionReportingUri() {
        assertThrows(
                IllegalStateException.class,
                () -> {
                    DBRegisteredAdInteraction.builder()
                            .setAdSelectionId(AD_SELECTION_ID)
                            .setInteractionKey(INTERACTION_KEY_CLICK)
                            .setDestination(FLAG_REPORTING_DESTINATION_SELLER)
                            .build();
                });
    }
}

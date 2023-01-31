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

package android.adservices.adselection;

import static android.adservices.adselection.ReportInteractionInput.DESTINATION_BUYER;
import static android.adservices.adselection.ReportInteractionInput.DESTINATION_SELLER;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertThrows;

import android.os.Parcel;

import org.json.JSONObject;
import org.junit.Before;
import org.junit.Test;

public class ReportInteractionInputTest {
    private static final long AD_SELECTION_ID = 1234L;
    private static final String INTERACTION_KEY = "click";
    private InteractionData mInteractionData;
    private static final int DESTINATIONS = DESTINATION_SELLER | DESTINATION_BUYER;

    @Before
    public void setup() throws Exception {
        JSONObject obj = new JSONObject().put("key", "value");
        mInteractionData = InteractionData.fromString(obj.toString());
    }

    @Test
    public void testWriteToParcel() throws Exception {
        ReportInteractionInput input =
                new ReportInteractionInput.Builder()
                        .setAdSelectionId(AD_SELECTION_ID)
                        .setInteractionKey(INTERACTION_KEY)
                        .setInteractionData(mInteractionData)
                        .setDestinations(DESTINATIONS)
                        .build();

        Parcel p = Parcel.obtain();
        input.writeToParcel(p, 0);
        p.setDataPosition(0);

        ReportInteractionInput fromParcel = ReportInteractionInput.CREATOR.createFromParcel(p);

        assertEquals(AD_SELECTION_ID, fromParcel.getAdSelectionId());
        assertEquals(INTERACTION_KEY, fromParcel.getInteractionKey());
        assertEquals(mInteractionData.toString(), fromParcel.getInteractionData().toString());
        assertEquals(DESTINATIONS, fromParcel.getDestinations());
    }

    @Test
    public void testFailsToBuildWithUnsetAdSelectionId() {
        assertThrows(
                IllegalArgumentException.class,
                () -> {
                    new ReportInteractionInput.Builder()
                            .setInteractionKey(INTERACTION_KEY)
                            .setInteractionData(mInteractionData)
                            .setDestinations(DESTINATIONS)
                            .build();
                });
    }

    @Test
    public void testFailsToBuildWithNullInteractionKey() {
        assertThrows(
                NullPointerException.class,
                () -> {
                    new ReportInteractionInput.Builder()
                            .setAdSelectionId(AD_SELECTION_ID)
                            .setInteractionData(mInteractionData)
                            .setDestinations(DESTINATIONS)
                            .build();
                });
    }

    @Test
    public void testFailsToBuildWithNullInteractionData() {
        assertThrows(
                NullPointerException.class,
                () -> {
                    new ReportInteractionInput.Builder()
                            .setAdSelectionId(AD_SELECTION_ID)
                            .setInteractionKey(INTERACTION_KEY)
                            .setDestinations(DESTINATIONS)
                            .build();
                });
    }

    @Test
    public void testFailsToBuildWithUnsetDestinations() {
        assertThrows(
                IllegalArgumentException.class,
                () -> {
                    new ReportInteractionInput.Builder()
                            .setAdSelectionId(AD_SELECTION_ID)
                            .setInteractionKey(INTERACTION_KEY)
                            .setInteractionData(mInteractionData)
                            .build();
                });
    }

    @Test
    public void testReportInteractionInputDescribeContents() {
        ReportInteractionInput input =
                new ReportInteractionInput.Builder()
                        .setAdSelectionId(AD_SELECTION_ID)
                        .setInteractionKey(INTERACTION_KEY)
                        .setInteractionData(mInteractionData)
                        .setDestinations(DESTINATIONS)
                        .build();

        assertEquals(input.describeContents(), 0);
    }
}

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

package android.adservices.cts;

import static android.adservices.adselection.ReportEventRequest.FLAG_REPORTING_DESTINATION_BUYER;
import static android.adservices.adselection.ReportEventRequest.FLAG_REPORTING_DESTINATION_SELLER;
import static android.view.KeyEvent.ACTION_DOWN;

import static org.junit.Assert.assertThrows;

import android.adservices.adselection.ReportEventRequest;
import android.view.InputEvent;
import android.view.KeyEvent;

import com.android.adservices.shared.testing.annotations.RequiresSdkLevelAtLeastS;

import org.json.JSONObject;
import org.junit.Before;
import org.junit.Test;

@RequiresSdkLevelAtLeastS
public final class ReportEventRequestTest extends CtsAdServicesDeviceTestCase {
    private static final long AD_SELECTION_ID = 1234L;
    private static final String INTERACTION_KEY = "click";
    private static final InputEvent INPUT_EVENT = new KeyEvent(ACTION_DOWN, 0);
    private String mInteractionData;
    private static final int DESTINATIONS =
            FLAG_REPORTING_DESTINATION_SELLER | FLAG_REPORTING_DESTINATION_BUYER;

    @Before
    public void setup() throws Exception {
        JSONObject obj = new JSONObject().put("key", "value");
        mInteractionData = obj.toString();
    }

    @Test
    public void testBuildReportEventRequestSuccess_viewInputEvent() {
        ReportEventRequest request =
                new ReportEventRequest.Builder(
                                AD_SELECTION_ID, INTERACTION_KEY, mInteractionData, DESTINATIONS)
                        .build();

        expect.that(request.getAdSelectionId()).isEqualTo(AD_SELECTION_ID);
        expect.that(request.getKey()).isEqualTo(INTERACTION_KEY);
        expect.that(request.getInputEvent()).isNull();
        expect.that(request.getData()).isEqualTo(mInteractionData);
        expect.that(request.getReportingDestinations()).isEqualTo(DESTINATIONS);
    }

    @Test
    public void testBuildReportEventRequestSuccess_clickInputEvent() {
        ReportEventRequest request =
                new ReportEventRequest.Builder(
                                AD_SELECTION_ID, INTERACTION_KEY, mInteractionData, DESTINATIONS)
                        .setInputEvent(INPUT_EVENT)
                        .build();

        expect.that(request.getAdSelectionId()).isEqualTo(AD_SELECTION_ID);
        expect.that(request.getKey()).isEqualTo(INTERACTION_KEY);
        expect.that(request.getInputEvent()).isEqualTo(INPUT_EVENT);
        expect.that(request.getData()).isEqualTo(mInteractionData);
        expect.that(request.getReportingDestinations()).isEqualTo(DESTINATIONS);
    }

    @Test
    public void testFailsToBuildWithUnsetAdSelectionId() {
        assertThrows(
                IllegalArgumentException.class,
                () ->
                        new ReportEventRequest.Builder(
                                        0, INTERACTION_KEY, mInteractionData, DESTINATIONS)
                                .build());
    }

    @Test
    public void testBuildReportEventRequestSuccess_callAllSetters() {
        long otherAdSelectionId = AD_SELECTION_ID + 1;
        String hoverKey = "hover";
        String otherInteractionData = "otherInteractionData";

        ReportEventRequest request =
                new ReportEventRequest.Builder(
                                AD_SELECTION_ID, INTERACTION_KEY, mInteractionData, DESTINATIONS)
                        .setAdSelectionId(otherAdSelectionId)
                        .setKey(hoverKey)
                        .setData(otherInteractionData)
                        .setReportingDestinations(FLAG_REPORTING_DESTINATION_SELLER)
                        .setInputEvent(INPUT_EVENT)
                        .build();

        expect.that(request.getAdSelectionId()).isEqualTo(otherAdSelectionId);
        expect.that(request.getKey()).isEqualTo(hoverKey);
        expect.that(request.getInputEvent()).isEqualTo(INPUT_EVENT);
        expect.that(request.getData()).isEqualTo(otherInteractionData);
        expect.that(request.getReportingDestinations())
                .isEqualTo(FLAG_REPORTING_DESTINATION_SELLER);
    }

    @Test
    public void testFailsToBuildWithUnsetInteractionKey() {
        assertThrows(
                NullPointerException.class,
                () ->
                        new ReportEventRequest.Builder(
                                        AD_SELECTION_ID, null, mInteractionData, DESTINATIONS)
                                .build());
    }

    @Test
    public void testFailsToBuildWithUnsetInteractionData() {
        assertThrows(
                NullPointerException.class,
                () ->
                        new ReportEventRequest.Builder(
                                        AD_SELECTION_ID, INTERACTION_KEY, null, DESTINATIONS)
                                .build());
    }

    @Test
    public void testFailsToBuildWithUnsetDestinations() {
        assertThrows(
                IllegalArgumentException.class,
                () ->
                        new ReportEventRequest.Builder(
                                        AD_SELECTION_ID, INTERACTION_KEY, mInteractionData, 0)
                                .build());
    }

    @Test
    public void testFailsToBuildWithInvalidDestinations() {
        assertThrows(
                IllegalArgumentException.class,
                () ->
                        new ReportEventRequest.Builder(
                                        AD_SELECTION_ID, INTERACTION_KEY, mInteractionData, 5)
                                .build());
    }

    @Test
    public void testFailsToBuildWithEventDataExceedsMaxSize() {
        char[] largePayload = new char[65 * 1024]; // 65KB
        assertThrows(
                IllegalArgumentException.class,
                () ->
                        new ReportEventRequest.Builder(
                                AD_SELECTION_ID,
                                INTERACTION_KEY,
                                new String(largePayload),
                                DESTINATIONS));
    }
}

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

package android.adservices.cts;

import static android.adservices.adselection.AdSelectionConfigFixture.anAdSelectionConfig;

import static org.junit.Assert.assertThrows;

import android.adservices.adselection.AdSelectionConfig;
import android.adservices.adselection.ReportImpressionRequest;

import org.junit.Test;

public final class ReportImpressionRequestTest extends CtsAdServicesDeviceTestCase {
    private static final long AUCTION_ID = 123;

    @Test
    public void testBuildsReportImpressionInput() {
        AdSelectionConfig testAdSelectionConfig = anAdSelectionConfig();

        ReportImpressionRequest request =
                new ReportImpressionRequest(AUCTION_ID, testAdSelectionConfig);

        expect.that(request.getAdSelectionId()).isEqualTo(AUCTION_ID);
        expect.that(request.getAdSelectionConfig()).isEqualTo(testAdSelectionConfig);
    }

    @Test
    public void testBuildsReportImpressionInputWithOnlyAdSelectionId() {
        ReportImpressionRequest request = new ReportImpressionRequest(AUCTION_ID);

        expect.that(request.getAdSelectionId()).isEqualTo(AUCTION_ID);
        expect.that(request.getAdSelectionConfig()).isEqualTo(AdSelectionConfig.EMPTY);
    }

    @Test
    public void testFailsToBuildWithUnsetAdSelectionId() {
        AdSelectionConfig testAdSelectionConfig = anAdSelectionConfig();
        assertThrows(
                IllegalArgumentException.class,
                () -> new ReportImpressionRequest(0, testAdSelectionConfig));
    }
}

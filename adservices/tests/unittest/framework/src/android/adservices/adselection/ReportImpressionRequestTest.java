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

package android.adservices.adselection;

import static android.adservices.adselection.AdSelectionConfigFixture.anAdSelectionConfig;

import static com.google.common.truth.Truth.assertThat;

import static org.junit.Assert.assertThrows;

import android.os.Parcel;

import androidx.test.filters.SmallTest;

import org.junit.Test;

/** Unit tests for {@link android.adservices.adselection.ReportImpressionRequest} */
@SmallTest
public final class ReportImpressionRequestTest {
    private static final int AUCTION_ID = 123;

    @Test
    public void testWriteToParcel() throws Exception {

        AdSelectionConfig testAdSelectionConfig = anAdSelectionConfig();

        ReportImpressionRequest request =
                new ReportImpressionRequest.Builder()
                        .setAdSelectionId(AUCTION_ID)
                        .setAdSelectionConfig(testAdSelectionConfig)
                        .build();
        Parcel p = Parcel.obtain();
        request.writeToParcel(p, 0);
        p.setDataPosition(0);

        ReportImpressionRequest fromParcel = ReportImpressionRequest.CREATOR.createFromParcel(p);

        assertThat(fromParcel.getAdSelectionId()).isEqualTo(AUCTION_ID);
        assertThat(fromParcel.getAdSelectionConfig()).isEqualTo(testAdSelectionConfig);
    }

    @Test
    public void testFailsToBuildWithUnsetAdSelectionId() {

        AdSelectionConfig testAdSelectionConfig = anAdSelectionConfig();
        assertThrows(
                IllegalArgumentException.class,
                () -> {
                        new ReportImpressionRequest.Builder()
                                // Not setting AdSelectionId making it null.
                                .setAdSelectionConfig(testAdSelectionConfig)
                                .build();
                });
    }

    @Test
    public void testFailsToBuildWithNullAdSelectionConfig() {

        assertThrows(
                IllegalArgumentException.class,
                () -> {
                        new ReportImpressionRequest.Builder()
                                .setAdSelectionId(AUCTION_ID)
                                // Not setting AdSelectionConfig making it null.
                                .build();
                });
    }
}

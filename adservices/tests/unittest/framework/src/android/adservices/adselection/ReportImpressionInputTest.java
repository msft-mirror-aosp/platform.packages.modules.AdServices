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

import androidx.test.filters.SmallTest;

import org.junit.Test;

@SmallTest
public class ReportImpressionInputTest {
    private static final long AUCTION_ID = 123;

    @Test
    public void testBuildsReportImpressionInput() throws Exception {
        AdSelectionConfig testAdSelectionConfig = anAdSelectionConfig();

        ReportImpressionInput input =
                new ReportImpressionInput.Builder()
                        .setAdSelectionId(AUCTION_ID)
                        .setAdSelectionConfig(testAdSelectionConfig)
                        .build();

        assertThat(input.getAdSelectionId()).isEqualTo(AUCTION_ID);
        assertThat(input.getAdSelectionConfig()).isEqualTo(testAdSelectionConfig);
    }

    @Test
    public void testFailsToBuildWithUnsetAdSelectionId() {

        AdSelectionConfig testAdSelectionConfig = anAdSelectionConfig();
        assertThrows(
                IllegalArgumentException.class,
                () -> {
                    new ReportImpressionInput.Builder()
                            // Not setting AdSelectionId making it null.
                            .setAdSelectionConfig(testAdSelectionConfig)
                            .build();
                });
    }

    @Test
    public void testFailsToBuildWithNullAdSelectionConfig() {

        assertThrows(
                NullPointerException.class,
                () -> {
                    new ReportImpressionInput.Builder()
                            .setAdSelectionId(AUCTION_ID)
                            // Not setting AdSelectionConfig making it null.
                            .build();
                });
    }
}

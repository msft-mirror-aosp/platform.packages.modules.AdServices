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

import static com.google.common.truth.Truth.assertThat;

import static org.junit.Assert.assertThrows;

import android.net.Uri;
import android.os.Parcel;

import androidx.test.filters.SmallTest;

import org.junit.Test;

@SmallTest
public class AdSelectionResponseTest {
    private static final Uri VALID_RENDER_URL =
            new Uri.Builder().path("valid.example.com/testing/hello").build();
    private static final int TEST_AD_SELECTION_ID = 12345;

    @Test
    public void testBuildAdSelectionResponse() {
        AdSelectionResponse adSelectionResponse =
                new AdSelectionResponse.Builder()
                        .setAdSelectionId(TEST_AD_SELECTION_ID)
                        .setRenderUrl(VALID_RENDER_URL)
                        .build();

        assertThat(adSelectionResponse.getAdSelectionId()).isEqualTo(TEST_AD_SELECTION_ID);
        assertThat(adSelectionResponse.getRenderUrl()).isEqualTo(VALID_RENDER_URL);
    }

    @Test
    public void testParcelAdSelectionResponse() {
        AdSelectionResponse adSelectionResponse =
                new AdSelectionResponse.Builder()
                        .setAdSelectionId(TEST_AD_SELECTION_ID)
                        .setRenderUrl(VALID_RENDER_URL)
                        .build();

        Parcel p = Parcel.obtain();
        adSelectionResponse.writeToParcel(p, 0);
        p.setDataPosition(0);
        AdSelectionResponse fromParcel = AdSelectionResponse.CREATOR.createFromParcel(p);

        assertThat(fromParcel.getAdSelectionId()).isEqualTo(TEST_AD_SELECTION_ID);
        assertThat(fromParcel.getRenderUrl()).isEqualTo(VALID_RENDER_URL);
    }

    @Test
    public void testFailsToBuildWithUnsetAdSelectionId() {
        assertThrows(
                IllegalArgumentException.class,
                () -> {
                    new AdSelectionResponse.Builder()
                            // Not setting AdSelectionId making it null.
                            .setRenderUrl(VALID_RENDER_URL)
                            .build();
                });
    }

    @Test
    public void testFailsToBuildWithNullAdData() {
        assertThrows(
                NullPointerException.class,
                () -> {
                    new AdSelectionResponse.Builder()
                            .setAdSelectionId(TEST_AD_SELECTION_ID)
                            // Not setting AdData making it null.
                            .build();
                });
    }
}

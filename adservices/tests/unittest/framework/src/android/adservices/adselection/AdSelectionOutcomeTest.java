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

import android.net.Uri;

import androidx.test.filters.SmallTest;

import org.junit.Test;

@SmallTest
public class AdSelectionOutcomeTest {
    private static final Uri VALID_RENDER_URL =
            new Uri.Builder().path("valid.example.com/testing/hello").build();
    private static final int TEST_AD_SELECTION_ID = 12345;

    @Test
    public void testBuildAdSelectionOutcome() {
        AdSelectionOutcome adSelectionOutcome =
                new AdSelectionOutcome.Builder()
                        .setAdSelectionId(TEST_AD_SELECTION_ID)
                        .setRenderUrl(VALID_RENDER_URL)
                        .build();

        assertThat(adSelectionOutcome.getAdSelectionId()).isEqualTo(TEST_AD_SELECTION_ID);
        assertThat(adSelectionOutcome.getRenderUrl()).isEqualTo(VALID_RENDER_URL);
    }
}

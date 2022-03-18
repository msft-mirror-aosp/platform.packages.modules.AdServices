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

import android.adservices.common.AdData;
import android.net.Uri;

import androidx.test.filters.SmallTest;

import org.junit.Test;

@SmallTest
public class AdSelectionResponseTest {
    @Test
    public void testAdSelectionResponseSuccessfulResponse() {
        AdData adData = new AdData(new Uri.Builder().build(), "");
        int adSelectionId = 5;
        AdSelectionResponse.Builder builder =
                new AdSelectionResponse.Builder()
                        .setAdSelectionId(adSelectionId)
                        .setAdData(adData)
                        .setResultCode(AdSelectionResponse.RESULT_OK);
        AdSelectionResponse adSelectionResponse = builder
                .build();
        assertThat(adSelectionResponse.getAdSelectionId()).isEqualTo(adSelectionId);
        assertThat(adSelectionResponse.getAdData()).isEqualTo(adData);
        assertThat(adSelectionResponse.getResultCode()).isEqualTo(AdSelectionResponse.RESULT_OK);
        assertThat(adSelectionResponse.getErrorMessage()).isNull();
    }

    @Test
    public void testAdSelectionResponseNoAdDataSuccessfulResponse() {
        int adSelectionId = 5;
        IllegalArgumentException thrown = assertThrows(
                IllegalArgumentException.class,
                () ->  new AdSelectionResponse.Builder()
                            // Leave AdsData null.
                            .setAdSelectionId(adSelectionId)
                            .setResultCode(AdSelectionResponse.RESULT_OK)
                            .build());
        assertThat(thrown).hasMessageThat()
                .contains("AdData is required for a successful response.");
    }

    @Test
    public void testAdSelectionResponseNoAdSelectionIdSuccessfulResponse() {
        AdData adData = new AdData(new Uri.Builder().build(), "");
        IllegalArgumentException thrown = assertThrows(
                IllegalArgumentException.class,
                () -> new AdSelectionResponse.Builder()
                            // leave adSelectionId unset.
                            .setAdData(adData)
                            .setResultCode(AdSelectionResponse.RESULT_OK)
                            .build());
        assertThat(thrown).hasMessageThat()
                .contains("AdSelectionID should be non-zero for a successful response.");
    }

    @Test
    public void testAdSelectionResponseNonNullErrorMessageSuccessfulResponse() {
        AdData adData = new AdData(new Uri.Builder().build(), "");
        int adSelectionId = 5;
        IllegalArgumentException thrown = assertThrows(
                IllegalArgumentException.class,
                () -> new AdSelectionResponse.Builder()
                            .setAdData(adData)
                            .setAdSelectionId(adSelectionId)
                            .setResultCode(AdSelectionResponse.RESULT_OK)
                            .setErrorMessage("Non null")
                            .build());
        assertThat(thrown).hasMessageThat()
                .contains("The ErrorMessage should be null for a successful response.");
    }

    @Test
    public void testAdSelectionResponseNullErrorMessageUnsuccessfulResponse() {
        IllegalArgumentException thrownInternalError = assertThrows(
                IllegalArgumentException.class,
                () -> new AdSelectionResponse.Builder()
                            .setResultCode(AdSelectionResponse.RESULT_INTERNAL_ERROR)
                            .build());
        assertThat(thrownInternalError).hasMessageThat()
                .contains("The ErrorMessage is required for non successful responses.");

        IllegalArgumentException thrownInvalidArgument = assertThrows(
                IllegalArgumentException.class,
                () -> new AdSelectionResponse.Builder()
                            .setResultCode(AdSelectionResponse.RESULT_INVALID_ARGUMENT)
                            .build());
        assertThat(thrownInvalidArgument).hasMessageThat()
                .contains("The ErrorMessage is required for non successful responses.");
    }
}

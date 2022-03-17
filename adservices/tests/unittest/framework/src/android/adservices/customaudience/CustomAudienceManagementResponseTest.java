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

package android.adservices.customaudience;

import static com.google.common.truth.Truth.assertThat;

import static org.junit.Assert.assertThrows;

import android.os.Parcel;

import androidx.test.filters.SmallTest;

import org.junit.Test;

/** Unit tests for {@link android.adservices.customaudience.CustomAudienceManagementResponse} */
@SmallTest
public final class CustomAudienceManagementResponseTest {
    @Test
    public void testBuildValidCustomAudienceManagementResponseSuccess() {
        int expectedStatus = CustomAudienceManagementResponse.STATUS_SUCCESS;
        CustomAudienceManagementResponse validCustomAudienceManagementResponse =
                new CustomAudienceManagementResponse.Builder()
                        .setStatusCode(expectedStatus)
                        .setErrorMessage(CustomAudienceUtils.VALID_ERROR_MESSAGE)
                        .build();

        assertThat(validCustomAudienceManagementResponse.isSuccess()).isTrue();
        assertThat(validCustomAudienceManagementResponse.getStatusCode()).isEqualTo(expectedStatus);
        assertThat(validCustomAudienceManagementResponse.getErrorMessage())
                .isEqualTo(CustomAudienceUtils.VALID_ERROR_MESSAGE);
    }

    @Test
    public void testParcelValidCustomAudienceManagementResponseSuccess() {
        int expectedStatus = CustomAudienceManagementResponse.STATUS_SUCCESS;
        CustomAudienceManagementResponse validCustomAudienceManagementResponse =
                new CustomAudienceManagementResponse.Builder()
                        .setStatusCode(expectedStatus)
                        .setErrorMessage(CustomAudienceUtils.VALID_ERROR_MESSAGE)
                        .build();

        Parcel p = Parcel.obtain();
        validCustomAudienceManagementResponse.writeToParcel(p, 0);
        p.setDataPosition(0);
        CustomAudienceManagementResponse fromParcel =
                CustomAudienceManagementResponse.CREATOR.createFromParcel(p);

        assertThat(fromParcel.isSuccess()).isTrue();
        assertThat(fromParcel.getStatusCode()).isEqualTo(expectedStatus);
        assertThat(fromParcel.getErrorMessage()).isEqualTo(CustomAudienceUtils.VALID_ERROR_MESSAGE);
    }

    @Test
    public void testBuildNullStatusCodeCustomAudienceManagementResponseFails() {
        assertThrows(IllegalArgumentException.class, () -> {
            // StatusCode is not set, so the response gets built with null
            new CustomAudienceManagementResponse.Builder()
                    .setErrorMessage(CustomAudienceUtils.VALID_ERROR_MESSAGE).build();
        });
    }

    @Test
    public void testBuildNullErrorMessageCustomAudienceManagementResponseSuccess() {
        int expectedStatus = CustomAudienceManagementResponse.STATUS_INTERNAL_ERROR;
        CustomAudienceManagementResponse nullErrorMessageCustomAudienceManagementResponse =
                new CustomAudienceManagementResponse.Builder()
                        .setStatusCode(expectedStatus)
                        .build();

        assertThat(nullErrorMessageCustomAudienceManagementResponse.isSuccess()).isFalse();
        assertThat(nullErrorMessageCustomAudienceManagementResponse.getStatusCode())
                .isEqualTo(expectedStatus);
        assertThat(nullErrorMessageCustomAudienceManagementResponse.getErrorMessage()).isNull();
    }
}

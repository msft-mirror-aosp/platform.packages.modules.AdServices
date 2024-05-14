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

package android.adservices.measurement;

import static android.adservices.common.AdServicesStatusUtils.STATUS_INTERNAL_ERROR;
import static android.adservices.common.AdServicesStatusUtils.STATUS_INVALID_ARGUMENT;
import static android.adservices.common.AdServicesStatusUtils.STATUS_IO_ERROR;
import static android.adservices.common.AdServicesStatusUtils.STATUS_PERMISSION_NOT_REQUESTED;

import static com.google.common.truth.Truth.assertThat;

import android.adservices.common.AdServicesStatusUtils;
import android.os.Parcel;

import com.android.adservices.common.AdServicesUnitTestCase;

import org.junit.Test;

import java.io.IOException;

/** Unit tests for {@link MeasurementErrorResponse} */
public final class MeasurementErrorResponseTest extends AdServicesUnitTestCase {
    private static final String ERROR_MESSAGE = "Error message";
    private static final int RESULT_CODE = 500;

    @Test
    public void testDefaults() throws Exception {
        MeasurementErrorResponse response = new MeasurementErrorResponse.Builder().build();

        expect.that(response.getStatusCode()).isEqualTo(0);
        expect.that(response.getErrorMessage()).isNull();
    }

    @Test
    public void testCreationAttribution() {
        verifyExample(createExample());
    }

    @Test
    public void testParceling() {
        Parcel p = Parcel.obtain();
        createExample().writeToParcel(p, 0);
        p.setDataPosition(0);
        verifyExample(MeasurementErrorResponse.CREATOR.createFromParcel(p));
        p.recycle();
    }

    @Test
    public void testAdServicesException_invalidArgument_expectIllegalArgumentException() {
        MeasurementErrorResponse response =
                new MeasurementErrorResponse.Builder()
                        .setStatusCode(STATUS_INVALID_ARGUMENT)
                        .build();
        assertThat(AdServicesStatusUtils.asException(response))
                .isInstanceOf(IllegalArgumentException.class);
    }

    @Test
    public void testAdServicesException_internalError_expectIllegalStateException() {
        MeasurementErrorResponse response =
                new MeasurementErrorResponse.Builder().setStatusCode(STATUS_INTERNAL_ERROR).build();
        assertThat(AdServicesStatusUtils.asException(response))
                .isInstanceOf(IllegalStateException.class);
    }

    @Test
    public void testAdServicesException_ioError_expectIOException() {
        MeasurementErrorResponse response =
                new MeasurementErrorResponse.Builder().setStatusCode(STATUS_IO_ERROR).build();
        assertThat(AdServicesStatusUtils.asException(response)).isInstanceOf(IOException.class);
    }

    @Test
    public void testAdServicesException_unauthorized_expectSecurityException() {
        MeasurementErrorResponse response =
                new MeasurementErrorResponse.Builder()
                        .setStatusCode(STATUS_PERMISSION_NOT_REQUESTED)
                        .build();
        assertThat(AdServicesStatusUtils.asException(response))
                .isInstanceOf(SecurityException.class);
    }

    @Test
    public void testAdServicesException_unrecognized_expectIllegalStateException() {
        MeasurementErrorResponse response =
                new MeasurementErrorResponse.Builder().setStatusCode(Integer.MAX_VALUE).build();
        assertThat(AdServicesStatusUtils.asException(response))
                .isInstanceOf(IllegalStateException.class);
    }

    @Test
    public void testDescribeContents() {
        assertThat(createExample().describeContents()).isEqualTo(0);
    }

    private MeasurementErrorResponse createExample() {
        return new MeasurementErrorResponse.Builder()
                .setErrorMessage(ERROR_MESSAGE)
                .setStatusCode(RESULT_CODE)
                .build();
    }

    private void verifyExample(MeasurementErrorResponse response) {
        expect.that(response.getErrorMessage()).isEqualTo(ERROR_MESSAGE);
        expect.that(response.getStatusCode()).isEqualTo(RESULT_CODE);
    }
}

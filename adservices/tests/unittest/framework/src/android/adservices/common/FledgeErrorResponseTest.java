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

package android.adservices.common;

import static org.junit.Assert.assertThrows;

import android.os.Parcel;

import com.android.adservices.common.AdServicesUnitTestCase;

import org.junit.Test;

/** Unit tests for {@link FledgeErrorResponse} */
public final class FledgeErrorResponseTest extends AdServicesUnitTestCase {
    @Test
    public void testBuildFledgeErrorResponse() {
        String notImplementedMessage = "Not Implemented!";
        FledgeErrorResponse response =
                new FledgeErrorResponse.Builder()
                        .setStatusCode(AdServicesStatusUtils.STATUS_INTERNAL_ERROR)
                        .setErrorMessage(notImplementedMessage)
                        .build();

        expect.that(response.getStatusCode())
                .isEqualTo(AdServicesStatusUtils.STATUS_INTERNAL_ERROR);
        expect.that(response.getErrorMessage()).isEqualTo(notImplementedMessage);
    }

    @Test
    public void testWriteToParcel() {
        String notImplementedMessage = "Not Implemented!";
        FledgeErrorResponse response =
                new FledgeErrorResponse.Builder()
                        .setStatusCode(AdServicesStatusUtils.STATUS_INTERNAL_ERROR)
                        .setErrorMessage(notImplementedMessage)
                        .build();
        Parcel p = Parcel.obtain();
        response.writeToParcel(p, 0);
        p.setDataPosition(0);

        FledgeErrorResponse fromParcel = FledgeErrorResponse.CREATOR.createFromParcel(p);

        expect.that(fromParcel.getStatusCode())
                .isEqualTo(AdServicesStatusUtils.STATUS_INTERNAL_ERROR);
        expect.that(fromParcel.getErrorMessage()).isEqualTo(notImplementedMessage);
    }

    @Test
    public void testWriteToParcelEmptyMessage() {
        FledgeErrorResponse response =
                new FledgeErrorResponse.Builder()
                        .setStatusCode(AdServicesStatusUtils.STATUS_SUCCESS)
                        .build();
        Parcel p = Parcel.obtain();
        response.writeToParcel(p, 0);
        p.setDataPosition(0);

        FledgeErrorResponse fromParcel = FledgeErrorResponse.CREATOR.createFromParcel(p);

        expect.that(AdServicesStatusUtils.isSuccess(fromParcel.getStatusCode())).isTrue();
        expect.that(fromParcel.getErrorMessage()).isNull();
    }

    @Test
    public void testFailsForNotSetStatus() {
        assertThrows(
                IllegalArgumentException.class,
                () ->
                        new FledgeErrorResponse.Builder()
                                .setErrorMessage("Status not set!")
                                // Not setting status code making it -1.
                                .build());
    }

    @Test
    public void testFledgeErrorResponseDescribeContents() {
        String notImplementedMessage = "Not Implemented!";
        FledgeErrorResponse response =
                new FledgeErrorResponse.Builder()
                        .setStatusCode(AdServicesStatusUtils.STATUS_INTERNAL_ERROR)
                        .setErrorMessage(notImplementedMessage)
                        .build();

        expect.that(response.describeContents()).isEqualTo(0);
    }

    @Test
    public void testToString() {
        String notImplementedMessage = "Not Implemented!";
        FledgeErrorResponse response =
                new FledgeErrorResponse.Builder()
                        .setStatusCode(AdServicesStatusUtils.STATUS_INTERNAL_ERROR)
                        .setErrorMessage(notImplementedMessage)
                        .build();

        expect.that(response.toString())
                .isEqualTo(
                        String.format(
                                "FledgeErrorResponse{mStatusCode=%s, mErrorMessage='%s'}",
                                AdServicesStatusUtils.STATUS_INTERNAL_ERROR,
                                notImplementedMessage));
    }
}

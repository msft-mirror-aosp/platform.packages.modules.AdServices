/*
 * Copyright (C) 2024 The Android Open Source Project
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

import android.os.Parcel;

import com.android.adservices.common.AdServicesUnitTestCase;

import org.junit.Test;

public final class EnableAdServicesResponseTest extends AdServicesUnitTestCase {
    @Test
    public void testEnableAdservicesResponseCoverages() {
        EnableAdServicesResponse response =
                new EnableAdServicesResponse.Builder()
                        .setApiEnabled(true)
                        .setErrorMessage("No Error")
                        .setStatusCode(200)
                        .setSuccess(true)
                        .build();

        Parcel parcel = Parcel.obtain();

        try {
            response.writeToParcel(parcel, 0);
            parcel.setDataPosition(0);

            EnableAdServicesResponse createdParams =
                    EnableAdServicesResponse.CREATOR.createFromParcel(parcel);
            expect.that(createdParams.describeContents()).isEqualTo(0);
            expect.that(createdParams).isNotSameInstanceAs(response);
            expect.that(createdParams.isApiEnabled()).isTrue();
            expect.that(createdParams.isSuccess()).isTrue();
            expect.that(createdParams.toString()).isEqualTo(response.toString());
        } finally {
            parcel.recycle();
        }
    }
}

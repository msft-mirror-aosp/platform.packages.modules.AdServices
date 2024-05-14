/*
 * Copyright (C) 2023 The Android Open Source Project
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

package android.adservices.extdata;

import android.os.Parcel;

import com.android.adservices.common.AdServicesUnitTestCase;

import org.junit.Test;

public final class GetAdServicesExtDataResultTest extends AdServicesUnitTestCase {
    @Test
    public void testWriteToParcel() throws Exception {
        AdServicesExtDataParams params =
                new AdServicesExtDataParams.Builder()
                        .setNotificationDisplayed(1)
                        .setMsmtConsent(-1)
                        .setIsU18Account(0)
                        .setIsAdultAccount(1)
                        .setManualInteractionWithConsentStatus(-1)
                        .setMsmtRollbackApexVersion(200L)
                        .build();

        GetAdServicesExtDataResult response =
                new GetAdServicesExtDataResult.Builder().setAdServicesExtDataParams(params).build();

        Parcel parcel = Parcel.obtain();
        response.writeToParcel(parcel, 0);
        parcel.setDataPosition(0);

        GetAdServicesExtDataResult fromParcel =
                GetAdServicesExtDataResult.CREATOR.createFromParcel(parcel);
        expect.that(GetAdServicesExtDataResult.CREATOR.newArray(1)).hasLength(1);

        expect.that(fromParcel.getAdServicesExtDataParams().getIsNotificationDisplayed())
                .isEqualTo(1);
        expect.that(fromParcel.getAdServicesExtDataParams().getIsMeasurementConsented())
                .isEqualTo(-1);
        expect.that(fromParcel.getAdServicesExtDataParams().getIsU18Account()).isEqualTo(0);
        expect.that(fromParcel.getAdServicesExtDataParams().getIsAdultAccount()).isEqualTo(1);
        expect.that(fromParcel.getAdServicesExtDataParams().getManualInteractionWithConsentStatus())
                .isEqualTo(-1);
        expect.that(fromParcel.getAdServicesExtDataParams().getMeasurementRollbackApexVersion())
                .isEqualTo(200L);
        expect.that(fromParcel.getErrorMessage()).isNull();
        expect.that(fromParcel.describeContents()).isEqualTo(0);
        expect.that(fromParcel.toString()).isNotNull();
    }
}

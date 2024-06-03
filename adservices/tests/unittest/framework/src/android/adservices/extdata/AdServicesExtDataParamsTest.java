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

public final class AdServicesExtDataParamsTest extends AdServicesUnitTestCase {
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

        Parcel parcel = Parcel.obtain();
        params.writeToParcel(parcel, 0);
        parcel.setDataPosition(0);

        AdServicesExtDataParams fromParcel =
                AdServicesExtDataParams.CREATOR.createFromParcel(parcel);
        expect.that(AdServicesExtDataParams.CREATOR.newArray(1)).hasLength(1);

        expect.that(fromParcel.getIsNotificationDisplayed()).isEqualTo(1);
        expect.that(fromParcel.getIsMeasurementConsented()).isEqualTo(-1);
        expect.that(fromParcel.getIsU18Account()).isEqualTo(0);
        expect.that(fromParcel.getIsAdultAccount()).isEqualTo(1);
        expect.that(fromParcel.getManualInteractionWithConsentStatus()).isEqualTo(-1);
        expect.that(fromParcel.getMeasurementRollbackApexVersion()).isEqualTo(200L);

        expect.that(fromParcel.describeContents()).isEqualTo(0);
        expect.that(fromParcel.toString()).isNotNull();
    }

    @Test
    public void testToString() {
        AdServicesExtDataParams params =
                new AdServicesExtDataParams.Builder()
                        .setNotificationDisplayed(1)
                        .setMsmtConsent(-1)
                        .setIsU18Account(0)
                        .setIsAdultAccount(1)
                        .setManualInteractionWithConsentStatus(-1)
                        .setMsmtRollbackApexVersion(200L)
                        .build();

        String expectedResult =
                "AdServicesExtDataParams{"
                        + "mIsNotificationDisplayed=1, "
                        + "mIsMsmtConsented=-1, "
                        + "mIsU18Account=0, "
                        + "mIsAdultAccount=1, "
                        + "mManualInteractionWithConsentStatus=-1, "
                        + "mMsmtRollbackApexVersion=200}";

        expect.that(params.toString()).isEqualTo(expectedResult);
    }
}

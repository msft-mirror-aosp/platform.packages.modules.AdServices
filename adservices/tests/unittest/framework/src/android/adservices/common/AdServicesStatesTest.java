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

public final class AdServicesStatesTest extends AdServicesUnitTestCase {
    @Test
    public void testAdServicesStatesCoverages() {
        AdServicesStates adServicesStates =
                new AdServicesStates.Builder()
                        .setAdIdEnabled(false)
                        .setAdultAccount(true)
                        .setU18Account(false)
                        .setPrivacySandboxUiRequest(true)
                        .setPrivacySandboxUiEnabled(true)
                        .build();

        Parcel parcel = Parcel.obtain();
        try {
            adServicesStates.writeToParcel(parcel, 0);
            parcel.setDataPosition(0);

            AdServicesStates createdParams = AdServicesStates.CREATOR.createFromParcel(parcel);
            expect.that(createdParams.describeContents()).isEqualTo(0);
            expect.that(createdParams).isNotSameInstanceAs(adServicesStates);
            expect.that(createdParams.isAdIdEnabled()).isFalse();
            expect.that(createdParams.isAdultAccount()).isTrue();
            expect.that(createdParams.isU18Account()).isFalse();
            expect.that(createdParams.isPrivacySandboxUiEnabled()).isTrue();
            expect.that(createdParams.isPrivacySandboxUiRequest()).isTrue();
        } finally {
            parcel.recycle();
        }
    }
}

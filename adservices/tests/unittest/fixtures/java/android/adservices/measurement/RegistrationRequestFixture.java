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

package android.adservices.measurement;

import android.annotation.NonNull;
import android.net.Uri;
import android.os.Parcel;

import java.util.Objects;

public class RegistrationRequestFixture {
    public static RegistrationRequest getInvalidRegistrationRequest(
            int registrationType,
            @NonNull Uri registrationUri,
            @NonNull String appPackageName,
            @NonNull String sdkPackageName) {
        Objects.requireNonNull(registrationUri);
        Objects.requireNonNull(appPackageName);
        Objects.requireNonNull(sdkPackageName);

        Parcel parcel = Parcel.obtain();
        parcel.writeInt(registrationType);
        registrationUri.writeToParcel(parcel, 0);
        parcel.writeString(appPackageName);
        parcel.writeString(sdkPackageName);
        /* mInputEvent */ parcel.writeBoolean(false);
        /* mRequestTime */ parcel.writeLong(0L);
        /* mIsAdIdPermissionGranted */ parcel.writeBoolean(false);
        /* mAdIdValue */ parcel.writeBoolean(false);
        parcel.setDataPosition(0);

        return RegistrationRequest.CREATOR.createFromParcel(parcel);
    }
}

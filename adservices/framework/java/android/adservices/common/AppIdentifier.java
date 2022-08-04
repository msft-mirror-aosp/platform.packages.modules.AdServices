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

import android.annotation.NonNull;
import android.os.Parcel;
import android.os.Parcelable;

import java.util.Objects;

/**
 * Represents an application's unique identifier.
 *
 * <p>Hidden for future API review.
 *
 * @hide
 */
public final class AppIdentifier implements Parcelable {
    @NonNull private final String mPackageName;

    @NonNull
    public static final Creator<AppIdentifier> CREATOR =
            new Creator<AppIdentifier>() {
                @Override
                public AppIdentifier createFromParcel(Parcel in) {
                    return new AppIdentifier(in);
                }

                @Override
                public AppIdentifier[] newArray(int size) {
                    return new AppIdentifier[size];
                }
            };

    /**
     * Represents an application's unique identifier.
     *
     * @param packageName the String package name that is used to uniquely identify the application
     */
    public AppIdentifier(@NonNull String packageName) {
        Objects.requireNonNull(packageName);
        mPackageName = packageName;
    }

    private AppIdentifier(@NonNull Parcel in) {
        Objects.requireNonNull(in);
        mPackageName = in.readString();
    }

    /** @return the String package name that uniquely identifies the application */
    @NonNull
    public String getPackageName() {
        return mPackageName;
    }

    /** @hide */
    @Override
    public int describeContents() {
        return 0;
    }

    @Override
    public void writeToParcel(@NonNull Parcel dest, int flags) {
        Objects.requireNonNull(dest);
        dest.writeString(mPackageName);
    }

    /** Checks whether two {@link AppIdentifier} objects represent the same application. */
    @Override
    public boolean equals(Object o) {
        if (this == o) return true;
        if (!(o instanceof AppIdentifier)) return false;
        AppIdentifier that = (AppIdentifier) o;
        return mPackageName.equals(that.mPackageName);
    }

    /** @return the hash of the {@link AppIdentifier} object. */
    @Override
    public int hashCode() {
        return Objects.hash(mPackageName);
    }

    @Override
    @NonNull
    public String toString() {
        return "AppIdentifier{" + "mPackageName='" + mPackageName + '\'' + '}';
    }
}

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
import android.annotation.Nullable;
import android.os.Parcel;
import android.os.Parcelable;

import java.util.Objects;

/**
 * An Identifier representing an ad buyer or seller.
 *
 * @hide
 */
public final class AdTechIdentifier implements Parcelable {

    @NonNull private final String mAdTechIdentifier;

    private AdTechIdentifier(@NonNull Parcel in) {
        this(in.readString());
    }

    private AdTechIdentifier(@NonNull String adTechIdentifier) {
        Objects.requireNonNull(adTechIdentifier);
        validate(adTechIdentifier);
        mAdTechIdentifier = adTechIdentifier;
    }

    @NonNull
    public static final Creator<AdTechIdentifier> CREATOR =
            new Creator<AdTechIdentifier>() {
                @Override
                public AdTechIdentifier createFromParcel(Parcel in) {
                    return new AdTechIdentifier(in);
                }

                @Override
                public AdTechIdentifier[] newArray(int size) {
                    return new AdTechIdentifier[size];
                }
            };

    @Override
    public int describeContents() {
        return 0;
    }

    @Override
    public void writeToParcel(@NonNull Parcel dest, int flags) {
        dest.writeString(mAdTechIdentifier);
    }

    @Override
    public boolean equals(Object o) {
        return o instanceof AdTechIdentifier
                && mAdTechIdentifier.equals(((AdTechIdentifier) o).getStringForm());
    }

    @Override
    public int hashCode() {
        return mAdTechIdentifier.hashCode();
    }

    @Override
    public String toString() {
        return getStringForm();
    }

    /**
     * Construct an instance of this class from a String.
     *
     * @param source A valid eTLD+1 domain of an ad buyer or seller or null.
     * @return An {@link AdTechIdentifier} class wrapping the given domain or null if the input was
     *     null.
     */
    @NonNull
    public static AdTechIdentifier fromString(@Nullable String source) {
        if (source == null) {
            return null;
        }
        return new AdTechIdentifier(source);
    }

    /** @return The identifier in String form. */
    @NonNull
    public String getStringForm() {
        return mAdTechIdentifier;
    }

    private void validate(String inputString) {
        // TODO(b/238849930) Bring existing validation function here
    }
}

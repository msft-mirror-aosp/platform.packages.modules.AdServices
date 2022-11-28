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

package android.app.adservices;

import android.annotation.NonNull;
import android.os.Parcel;
import android.os.Parcelable;

/**
 * Represent a User Consent.
 *
 * @hide
 */
public final class ConsentParcel implements Parcelable {
    public static final ConsentParcel GIVEN = new Builder().setIsGiven(true).build();
    public static final ConsentParcel REVOKED = new Builder().setIsGiven(false).build();

    private final boolean mIsGiven;

    private ConsentParcel(boolean isGiven) {
        mIsGiven = isGiven;
    }

    private ConsentParcel(@NonNull Parcel in) {
        mIsGiven = in.readBoolean();
    }

    public static final @NonNull Creator<ConsentParcel> CREATOR =
            new Parcelable.Creator<ConsentParcel>() {
                @Override
                public ConsentParcel createFromParcel(Parcel in) {
                    return new ConsentParcel(in);
                }

                @Override
                public ConsentParcel[] newArray(int size) {
                    return new ConsentParcel[size];
                }
            };

    @Override
    public int describeContents() {
        return 0;
    }

    @Override
    public void writeToParcel(@NonNull Parcel out, int flags) {
        out.writeBoolean(mIsGiven);
    }

    /** Get the IsGiven. */
    public boolean isIsGiven() {
        return mIsGiven;
    }

    /** Builder for {@link ConsentParcel} objects. */
    public static final class Builder {
        private boolean mIsGiven = false;

        public Builder() {}

        /** Set the IsGiven */
        public @NonNull Builder setIsGiven(Boolean isGiven) {
            // null input means isGiven = false
            mIsGiven = isGiven != null ? isGiven : false;
            return this;
        }

        /** Builds a {@link ConsentParcel} instance. */
        public @NonNull ConsentParcel build() {
            return new ConsentParcel(mIsGiven);
        }
    }
}

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

package android.adservices.adselection;

import android.annotation.NonNull;
import android.annotation.Nullable;
import android.os.Parcel;
import android.os.Parcelable;

import java.util.Objects;

/**
 * Represent input params to the GetAdSelectionData API.
 *
 * @hide
 */
public final class GetAdSelectionDataInput implements Parcelable {
    @Nullable private final GetAdSelectionDataRequest mGetAdSelectionDataRequest;
    @Nullable private final String mCallerPackageName;

    @NonNull
    public static final Creator<GetAdSelectionDataInput> CREATOR =
            new Creator<>() {
                public GetAdSelectionDataInput createFromParcel(Parcel in) {
                    return new GetAdSelectionDataInput(in);
                }

                public GetAdSelectionDataInput[] newArray(int size) {
                    return new GetAdSelectionDataInput[size];
                }
            };

    private GetAdSelectionDataInput(
            @NonNull GetAdSelectionDataRequest getAdSelectionDataRequest,
            @NonNull String callerPackageName) {
        Objects.requireNonNull(getAdSelectionDataRequest);

        this.mGetAdSelectionDataRequest = getAdSelectionDataRequest;
        this.mCallerPackageName = callerPackageName;
    }

    private GetAdSelectionDataInput(@NonNull Parcel in) {
        Objects.requireNonNull(in);

        this.mGetAdSelectionDataRequest = GetAdSelectionDataRequest.CREATOR.createFromParcel(in);
        this.mCallerPackageName = in.readString();
    }

    @Override
    public int describeContents() {
        return 0;
    }

    @Override
    public void writeToParcel(@NonNull Parcel dest, int flags) {
        Objects.requireNonNull(dest);

        mGetAdSelectionDataRequest.writeToParcel(dest, flags);
        dest.writeString(mCallerPackageName);
    }

    /**
     * Returns the {@code AdSelectionDataRequest}, one of the inputs to {@link
     * GetAdSelectionDataInput} as noted in {@code AdSelectionService}.
     */
    @NonNull
    public GetAdSelectionDataRequest getAdSelectionDataRequest() {
        return mGetAdSelectionDataRequest;
    }

    /**
     * @return the caller package name
     */
    @NonNull
    public String getCallerPackageName() {
        return mCallerPackageName;
    }

    /**
     * Builder for {@link GetAdSelectionDataInput} objects.
     *
     * @hide
     */
    public static final class Builder {
        @Nullable private GetAdSelectionDataRequest mGetAdSelectionDataRequest;
        @Nullable private String mCallerPackageName;

        public Builder() {}

        /** Set the adSelectionDataRequest. */
        @NonNull
        public GetAdSelectionDataInput.Builder setAdSelectionDataRequest(
                @NonNull GetAdSelectionDataRequest getAdSelectionDataRequest) {
            Objects.requireNonNull(getAdSelectionDataRequest);

            this.mGetAdSelectionDataRequest = getAdSelectionDataRequest;
            return this;
        }

        /** Sets the caller's package name. */
        @NonNull
        public GetAdSelectionDataInput.Builder setCallerPackageName(
                @NonNull String callerPackageName) {
            Objects.requireNonNull(callerPackageName);

            this.mCallerPackageName = callerPackageName;
            return this;
        }

        /** Builds a {@link GetAdSelectionDataInput} instance. */
        @NonNull
        public GetAdSelectionDataInput build() {
            Objects.requireNonNull(mGetAdSelectionDataRequest);
            Objects.requireNonNull(mCallerPackageName);

            return new GetAdSelectionDataInput(mGetAdSelectionDataRequest, mCallerPackageName);
        }
    }
}

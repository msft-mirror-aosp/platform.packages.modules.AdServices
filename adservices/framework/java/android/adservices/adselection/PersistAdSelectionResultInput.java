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
 * Represents input params to the PersistAdSelectionResult API.
 *
 * @hide
 */
public final class PersistAdSelectionResultInput implements Parcelable {
    @Nullable private final PersistAdSelectionResultRequest mPersistAdSelectionResultRequest;
    @Nullable private final String mCallerPackageName;

    @NonNull
    public static final Creator<PersistAdSelectionResultInput> CREATOR =
            new Creator<>() {
                public PersistAdSelectionResultInput createFromParcel(Parcel in) {
                    return new PersistAdSelectionResultInput(in);
                }

                public PersistAdSelectionResultInput[] newArray(int size) {
                    return new PersistAdSelectionResultInput[size];
                }
            };

    private PersistAdSelectionResultInput(
            @NonNull PersistAdSelectionResultRequest persistAdSelectionResultRequest,
            @NonNull String callerPackageName) {
        Objects.requireNonNull(persistAdSelectionResultRequest);

        this.mPersistAdSelectionResultRequest = persistAdSelectionResultRequest;
        this.mCallerPackageName = callerPackageName;
    }

    private PersistAdSelectionResultInput(@NonNull Parcel in) {
        Objects.requireNonNull(in);

        this.mPersistAdSelectionResultRequest =
                PersistAdSelectionResultRequest.CREATOR.createFromParcel(in);
        this.mCallerPackageName = in.readString();
    }

    @Override
    public int describeContents() {
        return 0;
    }

    @Override
    public void writeToParcel(@NonNull Parcel dest, int flags) {
        Objects.requireNonNull(dest);

        mPersistAdSelectionResultRequest.writeToParcel(dest, flags);
        dest.writeString(mCallerPackageName);
    }

    /**
     * Returns the {@link PersistAdSelectionResultRequest}, one of the inputs to {@link
     * PersistAdSelectionResultInput} as noted in {@link AdSelectionService}.
     */
    @Nullable
    public PersistAdSelectionResultRequest getPersistAdSelectionResultRequest() {
        return mPersistAdSelectionResultRequest;
    }

    /**
     * @return the caller package name
     */
    @Nullable
    public String getCallerPackageName() {
        return mCallerPackageName;
    }

    /**
     * Builder for {@link PersistAdSelectionResultInput} objects.
     *
     * @hide
     */
    public static final class Builder {
        @Nullable private PersistAdSelectionResultRequest mPersistAdSelectionResultRequest;
        @Nullable private String mCallerPackageName;

        public Builder() {}

        /** Set the PersistAdSelectionResultRequest. */
        @NonNull
        public PersistAdSelectionResultInput.Builder setPersistAdSelectionResultRequest(
                @NonNull PersistAdSelectionResultRequest persistAdSelectionResultRequest) {
            Objects.requireNonNull(persistAdSelectionResultRequest);

            this.mPersistAdSelectionResultRequest = persistAdSelectionResultRequest;
            return this;
        }

        /** Sets the caller's package name. */
        @NonNull
        public PersistAdSelectionResultInput.Builder setCallerPackageName(
                @NonNull String callerPackageName) {
            Objects.requireNonNull(callerPackageName);

            this.mCallerPackageName = callerPackageName;
            return this;
        }

        /** Builds a {@link PersistAdSelectionResultInput} instance. */
        @NonNull
        public PersistAdSelectionResultInput build() {
            Objects.requireNonNull(mPersistAdSelectionResultRequest);
            Objects.requireNonNull(mCallerPackageName);

            return new PersistAdSelectionResultInput(
                    mPersistAdSelectionResultRequest, mCallerPackageName);
        }
    }
}

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
 * Represent input params to the ProcessAdSelectionResult API.
 *
 * @hide
 */
public final class ProcessAdSelectionResultInput implements Parcelable {
    @Nullable private final ProcessAdSelectionResultRequest mProcessAdSelectionResultRequest;
    @Nullable private final String mCallerPackageName;

    @NonNull
    public static final Creator<ProcessAdSelectionResultInput> CREATOR =
            new Creator<>() {
                public ProcessAdSelectionResultInput createFromParcel(Parcel in) {
                    return new ProcessAdSelectionResultInput(in);
                }

                public ProcessAdSelectionResultInput[] newArray(int size) {
                    return new ProcessAdSelectionResultInput[size];
                }
            };

    private ProcessAdSelectionResultInput(
            @NonNull ProcessAdSelectionResultRequest processAdSelectionResultRequest,
            @NonNull String callerPackageName) {
        Objects.requireNonNull(processAdSelectionResultRequest);

        this.mProcessAdSelectionResultRequest = processAdSelectionResultRequest;
        this.mCallerPackageName = callerPackageName;
    }

    private ProcessAdSelectionResultInput(@NonNull Parcel in) {
        Objects.requireNonNull(in);

        this.mProcessAdSelectionResultRequest =
                ProcessAdSelectionResultRequest.CREATOR.createFromParcel(in);
        this.mCallerPackageName = in.readString();
    }

    @Override
    public int describeContents() {
        return 0;
    }

    @Override
    public void writeToParcel(@NonNull Parcel dest, int flags) {
        Objects.requireNonNull(dest);

        mProcessAdSelectionResultRequest.writeToParcel(dest, flags);
        dest.writeString(mCallerPackageName);
    }

    /**
     * Returns the {@code ProcessAdSelectionResultRequest}, one of the inputs to {@link
     * ProcessAdSelectionResultInput} as noted in {@code AdSelectionService}.
     */
    @NonNull
    public ProcessAdSelectionResultRequest getProcessAdSelectionResultRequest() {
        return mProcessAdSelectionResultRequest;
    }

    /**
     * @return the caller package name
     */
    @NonNull
    public String getCallerPackageName() {
        return mCallerPackageName;
    }

    /**
     * Builder for {@link ProcessAdSelectionResultInput} objects.
     *
     * @hide
     */
    public static final class Builder {
        @Nullable private ProcessAdSelectionResultRequest mProcessAdSelectionResultRequest;
        @Nullable private String mCallerPackageName;

        public Builder() {}

        /** Set the ProcessAdSelectionResultRequest. */
        @NonNull
        public ProcessAdSelectionResultInput.Builder setProcessAdSelectionResultRequest(
                @NonNull ProcessAdSelectionResultRequest processAdSelectionResultRequest) {
            Objects.requireNonNull(processAdSelectionResultRequest);

            this.mProcessAdSelectionResultRequest = processAdSelectionResultRequest;
            return this;
        }

        /** Sets the caller's package name. */
        @NonNull
        public ProcessAdSelectionResultInput.Builder setCallerPackageName(
                @NonNull String callerPackageName) {
            Objects.requireNonNull(callerPackageName);

            this.mCallerPackageName = callerPackageName;
            return this;
        }

        /** Builds a {@link ProcessAdSelectionResultInput} instance. */
        @NonNull
        public ProcessAdSelectionResultInput build() {
            Objects.requireNonNull(mProcessAdSelectionResultRequest);
            Objects.requireNonNull(mCallerPackageName);

            return new ProcessAdSelectionResultInput(
                    mProcessAdSelectionResultRequest, mCallerPackageName);
        }
    }
}

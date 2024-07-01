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

import android.adservices.common.AdServicesStatusUtils.StatusCode;
import android.annotation.FlaggedApi;
import android.annotation.NonNull;
import android.annotation.Nullable;
import android.annotation.SystemApi;
import android.os.Parcel;
import android.os.Parcelable;

import com.android.adservices.flags.Flags;
import com.android.internal.util.Preconditions;

import java.util.Objects;

/**
 * Represents a generic response for SetNotificationResponse API's.
 *
 * @hide
 */
@SystemApi
@FlaggedApi(Flags.FLAG_ADSERVICES_ENABLE_PER_MODULE_OVERRIDES_API)
public final class AdServicesCommonResponse implements Parcelable {

    @StatusCode private int mStatusCode;
    @Nullable private String mErrorMessage;

    private AdServicesCommonResponse(@StatusCode int statusCode, @Nullable String errorMessage) {
        this.mStatusCode = statusCode;
        this.mErrorMessage = errorMessage;
    }

    private AdServicesCommonResponse(@NonNull Parcel in) {
        Objects.requireNonNull(in);

        mStatusCode = in.readInt();
        mErrorMessage = in.readString();
    }

    @NonNull
    public static final Creator<AdServicesCommonResponse> CREATOR =
            new Parcelable.Creator<>() {
                @Override
                public AdServicesCommonResponse createFromParcel(@NonNull Parcel in) {
                    Objects.requireNonNull(in);
                    return new AdServicesCommonResponse(in);
                }

                @Override
                public AdServicesCommonResponse[] newArray(int size) {
                    return new AdServicesCommonResponse[size];
                }
            };

    @Override
    public int describeContents() {
        return 0;
    }

    @Override
    public void writeToParcel(@NonNull Parcel dest, int flags) {
        Objects.requireNonNull(dest);

        dest.writeInt(mStatusCode);
        dest.writeString(mErrorMessage);
    }

    @Override
    public String toString() {
        return "AdServicesCommonResponse{"
                + "mStatusCode="
                + mStatusCode
                + ", mErrorMessage='"
                + mErrorMessage
                + "'}";
    }

    public int getStatusCode() {
        return mStatusCode;
    }

    @Nullable
    public String getErrorMessage() {
        return mErrorMessage;
    }

    /**
     * Builder for {@link AdServicesCommonResponse} objects.
     *
     * @hide
     */
    public static final class Builder {
        @StatusCode private int mStatusCode = AdServicesStatusUtils.STATUS_UNSET;
        @Nullable private String mErrorMessage;

        public Builder() {}

        /** Set the Status Code. */
        @NonNull
        public AdServicesCommonResponse.Builder setStatusCode(@StatusCode int statusCode) {
            mStatusCode = statusCode;
            return this;
        }

        /** Set the Error Message. */
        @NonNull
        public AdServicesCommonResponse.Builder setErrorMessage(@Nullable String errorMessage) {
            mErrorMessage = errorMessage;
            return this;
        }

        /**
         * Builds a {@link AdServicesCommonResponse} instance.
         *
         * <p>throws IllegalArgumentException if any of the status code is null or error message is
         * not set for an unsuccessful status
         */
        @NonNull
        public AdServicesCommonResponse build() {
            Preconditions.checkArgument(
                    mStatusCode != AdServicesStatusUtils.STATUS_UNSET,
                    "Status code has not been set!");

            return new AdServicesCommonResponse(mStatusCode, mErrorMessage);
        }
    }
}

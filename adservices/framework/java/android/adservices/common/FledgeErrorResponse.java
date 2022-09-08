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

import android.adservices.common.AdServicesStatusUtils.StatusCode;
import android.adservices.exceptions.AdServicesException;
import android.adservices.exceptions.ApiNotAuthorizedException;
import android.annotation.NonNull;
import android.annotation.Nullable;
import android.os.Parcel;
import android.os.Parcelable;

import com.android.internal.util.Preconditions;

import java.util.Objects;

/**
 * Represent a generic response for FLEDGE API's.
 *
 * @hide
 */
public final class FledgeErrorResponse implements Parcelable {
    @StatusCode private final int mStatusCode;
    @Nullable private final String mErrorMessage;

    private FledgeErrorResponse(@StatusCode int statusCode, @Nullable String errorMessage) {
        mStatusCode = statusCode;
        mErrorMessage = errorMessage;
    }

    private FledgeErrorResponse(@NonNull Parcel in) {
        Objects.requireNonNull(in);

        mStatusCode = in.readInt();
        mErrorMessage = in.readString();
    }

    @NonNull
    public static final Creator<FledgeErrorResponse> CREATOR =
            new Parcelable.Creator<FledgeErrorResponse>() {
                @Override
                public FledgeErrorResponse createFromParcel(@NonNull Parcel in) {
                    Objects.requireNonNull(in);
                    return new FledgeErrorResponse(in);
                }

                @Override
                public FledgeErrorResponse[] newArray(int size) {
                    return new FledgeErrorResponse[size];
                }
            };

    @Override
    public int describeContents() {
        return 0;
    }

    /** @hide */
    @Override
    public void writeToParcel(@NonNull Parcel dest, int flags) {
        Objects.requireNonNull(dest);

        dest.writeInt(mStatusCode);
        dest.writeString(mErrorMessage);
    }

    /** Returns one of the {@code STATUS} constants defined in {@link AdServicesStatusUtils}. */
    public int getStatusCode() {
        return mStatusCode;
    }

    /**
     * Returns the error message associated with this response.
     *
     * <p>If {@link AdServicesStatusUtils#isSuccess(int)} is {@code true}, the error message is
     * always {@code null}. The error message may not be {@code null} even if {@link
     * AdServicesStatusUtils#isSuccess(int)} is {@code false}.
     */
    @Nullable
    public String getErrorMessage() {
        return mErrorMessage;
    }

    /** Converts the response to an exception to be used in the callback. */
    @NonNull
    public AdServicesException asException() {

        Exception innerException;
        switch (mStatusCode) {
            case AdServicesStatusUtils.STATUS_INTERNAL_ERROR:
                innerException = new IllegalStateException();
                break;
            case AdServicesStatusUtils.STATUS_INVALID_ARGUMENT:
                innerException = new IllegalArgumentException();
                break;
            case AdServicesStatusUtils.STATUS_UNAUTHORIZED:
                innerException = new ApiNotAuthorizedException();
                break;
            case AdServicesStatusUtils.STATUS_SUCCESS: // Intentional fallthrough
            case AdServicesStatusUtils.STATUS_UNKNOWN_ERROR: // Intentional fallthrough
            case AdServicesStatusUtils.STATUS_UNSET: // Intentional fallthrough
            default:
                innerException = new Exception();
                break;
        }
        return new AdServicesException(mErrorMessage, innerException);
    }

    /**
     * Builder for {@link FledgeErrorResponse} objects.
     *
     * @hide
     */
    public static final class Builder {
        @StatusCode private int mStatusCode = AdServicesStatusUtils.STATUS_UNSET;
        @Nullable private String mErrorMessage;

        public Builder() {}

        /** Set the Status Code. */
        @NonNull
        public FledgeErrorResponse.Builder setStatusCode(@StatusCode int statusCode) {
            mStatusCode = statusCode;
            return this;
        }

        /** Set the Error Message. */
        @NonNull
        public FledgeErrorResponse.Builder setErrorMessage(@Nullable String errorMessage) {
            mErrorMessage = errorMessage;
            return this;
        }

        /**
         * Builds a {@link FledgeErrorResponse} instance.
         *
         * <p>throws IllegalArgumentException if any of the status code is null or error message is
         * not set for an unsuccessful status
         */
        @NonNull
        public FledgeErrorResponse build() {
            Preconditions.checkArgument(
                    mStatusCode != AdServicesStatusUtils.STATUS_UNSET,
                    "Status code has not been set!");

            return new FledgeErrorResponse(mStatusCode, mErrorMessage);
        }
    }
}

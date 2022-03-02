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
package android.adservices.adselection;

import android.annotation.IntDef;
import android.annotation.NonNull;
import android.annotation.Nullable;
import android.os.Parcel;
import android.os.Parcelable;

import com.android.internal.util.Preconditions;

/**
 * Represent the result from the reportImpression API.
 *
 * <p>Hiding for future implementation and review for public exposure.
 *
 * @hide
 */
public final class ReportImpressionResponse implements Parcelable {
    /**
     * Result codes from reportImpression API.
     *
     * @hide
     */
    @IntDef(
            value = {
                STATUS_UNSET,
                STATUS_OK,
                STATUS_INTERNAL_ERROR,
                STATUS_INVALID_ARGUMENT,
                STATUS_UNKNOWN_ERROR
            })
    public @interface ResultCode {}

    /** The status code has not been set */
    public static final int STATUS_UNSET = -1;

    /** The call was successful. */
    public static final int STATUS_OK = 0;

    /**
     * An internal error occurred within Impression Reporting API, which the caller cannot address.
     * This error may be considered similar to {@link IllegalStateException}
     */
    public static final int STATUS_INTERNAL_ERROR = 1;

    /**
     * The caller supplied invalid arguments to the call. This error may be considered similar to
     * {@link IllegalArgumentException}.
     */
    public static final int STATUS_INVALID_ARGUMENT = 2;

    /** There was an unknown error. Keep unknown error the largest status code. */
    public static final int STATUS_UNKNOWN_ERROR = 3;

    @ResultCode private final int mResultCode;
    @Nullable private final String mErrorMessage;

    private ReportImpressionResponse(@ResultCode int resultCode, @Nullable String errorMessage) {
        mResultCode = resultCode;
        mErrorMessage = errorMessage;
    }

    private ReportImpressionResponse(@NonNull Parcel in) {
        mResultCode = in.readInt();
        mErrorMessage = in.readString();
    }

    @NonNull
    public static final Creator<android.adservices.adselection.ReportImpressionResponse> CREATOR =
            new Parcelable.Creator<android.adservices.adselection.ReportImpressionResponse>() {
                @Override
                public android.adservices.adselection.ReportImpressionResponse createFromParcel(
                        Parcel in) {
                    return new android.adservices.adselection.ReportImpressionResponse(in);
                }

                @Override
                public android.adservices.adselection.ReportImpressionResponse[] newArray(
                        int size) {
                    return new android.adservices.adselection.ReportImpressionResponse[size];
                }
            };

    /** @hide */
    @Override
    public int describeContents() {
        return 0;
    }

    /** @hide */
    @Override
    public void writeToParcel(@NonNull Parcel out, int flags) {
        out.writeInt(mResultCode);
        out.writeString(mErrorMessage);
    }

    /**
     * Returns {@code true} if {@link #getResultCode} equals {@link
     * android.adservices.adselection.ReportImpressionResponse#STATUS_OK}.
     */
    public boolean isSuccess() {
        return getResultCode() == STATUS_OK;
    }

    /**
     * Returns one of the {@code RESULT} constants defined in {@link
     * android.adservices.adselection.ReportImpressionResponse}.
     */
    public int getResultCode() {
        return mResultCode;
    }

    /**
     * Returns the error message associated with this result.
     *
     * <p>If {@link #isSuccess} is {@code true}, the error message is always {@code null}. The error
     * message may be {@code null} even if {@link #isSuccess} is {@code false}.
     */
    @Nullable
    public String getErrorMessage() {
        return mErrorMessage;
    }

    /**
     * Builder for {@link ReportImpressionResponse} objects.
     *
     * @hide
     */
    public static final class Builder {
        @ResultCode private int mResultCode = STATUS_UNSET;
        @Nullable private String mErrorMessage;

        public Builder() {}

        /** Set the Result Code. */
        @NonNull
        public ReportImpressionResponse.Builder setResultCode(
                @ReportImpressionResponse.ResultCode int resultCode) {
            mResultCode = resultCode;
            return this;
        }

        /** Set the Error Message. */
        @NonNull
        public ReportImpressionResponse.Builder setErrorMessage(@Nullable String errorMessage) {
            mErrorMessage = errorMessage;
            return this;
        }

        /**
         * Builds a {@link ReportImpressionResponse} instance.
         *
         * <p>throws IllegalArgumentException if any of the status code is null or error message is
         * not set for an unsuccessful status
         */
        @NonNull
        public ReportImpressionResponse build() {
            Preconditions.checkArgument(
                    mResultCode == STATUS_OK || mErrorMessage != null,
                    "Empty Error message with non-successful status code");

            Preconditions.checkArgument(
                    mResultCode != STATUS_UNSET, "Status code has not been set!");

            return new ReportImpressionResponse(mResultCode, mErrorMessage);
        }
    }
}

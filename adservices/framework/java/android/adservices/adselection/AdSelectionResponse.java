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

import android.adservices.common.AdData;
import android.annotation.IntDef;
import android.annotation.NonNull;
import android.annotation.Nullable;
import android.os.Parcel;
import android.os.Parcelable;

import com.android.internal.util.Preconditions;

import java.lang.annotation.Retention;
import java.lang.annotation.RetentionPolicy;

/**
 * The AdSelectionResponse class represents the result from the runAdSelection API.
 *
 * @hide
 */
public final class AdSelectionResponse implements Parcelable {
    /**
     * Result codes from {@link AdSelectionResponse} methods.
     *
     * @hide
     */

    @Nullable private final AdData mAdData;
    private final int mAdSelectionId;
    private final @ResultCode int mResultCode;
    @Nullable private final String mErrorMessage;

    @IntDef(
            value = {
                RESULT_OK,
                RESULT_INTERNAL_ERROR,
                RESULT_INVALID_ARGUMENT,
            })
    @Retention(RetentionPolicy.SOURCE)
    public @interface ResultCode {}

    /** The call is successful and the response will return a winning Ad and an adSelectionId */
    public static final int RESULT_OK = 0;

    /**
     * An internal error occurred within AdSelection API, which the caller cannot address.
     *
     * <p>This error may be considered similar to {@link IllegalStateException}
     */
    public static final int RESULT_INTERNAL_ERROR = 1;

    /**
     * The caller supplied invalid arguments to the call, i.e. the decisonLogicUrl is invalid to
     * fetch any java scripts required for the ad selection process.
     *
     * <p>This error may be considered similar to {@link IllegalArgumentException}.
     */
    public static final int RESULT_INVALID_ARGUMENT = 2;

    /** An ID used to identify this ad selection. It is used by the {@see ReportingAPI} */
    public int getAdSelectionId() {
        return mAdSelectionId;
    }

    /** The winning adData. */
    public @Nullable AdData getAdData() {
        return mAdData;
    }

    /**
     * Returns one of {@link ResultCode} constants defined in {@link AdSelectionResponse}.
     * The valid return values are
     *  0 for a successful run.
     *  1 for failure due to internal error.
     *  2 for failure due to invalid argument.
     * Any other values are invalid.
     */
    @ResultCode
    public int getResultCode() {
        return mResultCode;
    }

    /** Returns the error message associated with this result. */
    public String getErrorMessage() {
        return mErrorMessage;
    }

    private AdSelectionResponse(
            @Nullable AdData adData,
            int adSelectionId,
            @ResultCode int resultCode,
            @Nullable String errorMessage) {
        mAdData = adData;
        mAdSelectionId = adSelectionId;
        mResultCode = resultCode;
        mErrorMessage = errorMessage;
    }

    private AdSelectionResponse(@NonNull Parcel in) {
        mAdData = AdData.CREATOR.createFromParcel(in);
        mAdSelectionId = in.readInt();
        mResultCode = in.readInt();
        mErrorMessage = in.readString();
    }

    @NonNull
    public static final Creator<AdSelectionResponse> CREATOR =
            new Creator<AdSelectionResponse>() {
                @Override
                public AdSelectionResponse createFromParcel(Parcel in) {
                    return new AdSelectionResponse(in);
                }

                @Override
                public AdSelectionResponse[] newArray(int size) {
                    return new AdSelectionResponse[size];
                }
            };

    @Override
    public int describeContents() {
        return 0;
    }

    @Override
    public void writeToParcel(@NonNull Parcel dest, int flags) {
        dest.writeInt(mAdSelectionId);
        mAdData.writeToParcel(dest, flags);
        dest.writeInt(mResultCode);
        dest.writeString(mErrorMessage);
    }

    /**
     * Builder for {@link AdSelectionResponse} objects.
     *
     * @hide
     */
    public static final class Builder {
        private @Nullable AdData mAdData;

        private int mAdSelectionId;

        private @ResultCode int mResultCode;
        @Nullable private String mErrorMessage;

        public Builder() {}
        /** the setter method of adSelectionId. */
        public Builder setAdSelectionId(int adSelectionId) {
            this.mAdSelectionId = adSelectionId;
            return this;
        }

        /** the setter method of Advert. */
        public Builder setAdData(AdData adData) {
            this.mAdData = adData;
            return this;
        }

        /** the setter method of the Result Code. */
        public @NonNull Builder setResultCode(@ResultCode int resultCode) {
            mResultCode = resultCode;
            return this;
        }

        /** the setter method of the Error Message. */
        public @NonNull Builder setErrorMessage(@Nullable String errorMessage) {
            mErrorMessage = errorMessage;
            return this;
        }

        /**
         * Builds a {@link AdSelectionResponse} instance.
         * Throws IllegalArgumentException
         * 1. when the result code is {@link ResultCode#RESULT_OK} but
         * {@link AdData} is null or the AdSelectionId is invalid or the error message is not null.
         * 2. when the result code is not {ResultCode#RESULT_OK} but the error message is null.
         */
        public @NonNull AdSelectionResponse build() {
            if (mResultCode == RESULT_OK) {
                Preconditions.checkArgument(
                        mAdData != null, "AdData is required for a successful response.");
                Preconditions.checkArgument(
                        mAdSelectionId != 0,
                        "AdSelectionID should be non-zero for a successful response.");
                Preconditions.checkArgument(
                        mErrorMessage == null,
                        "The ErrorMessage should be null for a successful response.");
            } else {
                Preconditions.checkArgument(
                        mErrorMessage != null,
                        "The ErrorMessage is required for non successful responses.");
            }
            return new AdSelectionResponse(mAdData, mAdSelectionId, mResultCode, mErrorMessage);
        }
    }
}

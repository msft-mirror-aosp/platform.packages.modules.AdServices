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

package android.adservices.measurement;

import static com.android.adservices.ResultCode.RESULT_INTERNAL_ERROR;
import static com.android.adservices.ResultCode.RESULT_INVALID_ARGUMENT;
import static com.android.adservices.ResultCode.RESULT_IO_ERROR;
import static com.android.adservices.ResultCode.RESULT_OK;
import static com.android.adservices.ResultCode.RESULT_UNAUTHORIZED_CALL;

import android.adservices.exceptions.AdServicesException;
import android.adservices.exceptions.MeasurementException;
import android.adservices.measurement.MeasurementManager.ResultCode;
import android.annotation.NonNull;
import android.annotation.Nullable;
import android.os.Parcel;
import android.os.Parcelable;

import java.io.IOException;
import java.util.Objects;

/**
 * Represents a generic response for Measurement APIs.
 *
 * @hide
 */
public final class MeasurementErrorResponse implements Parcelable {
    @ResultCode private final int mResultCode;
    @Nullable private final String mErrorMessage;

    private MeasurementErrorResponse(@ResultCode int resultCode, @Nullable String errorMessage) {
        mResultCode = resultCode;
        mErrorMessage = errorMessage;
    }

    private MeasurementErrorResponse(@NonNull Parcel in) {
        Objects.requireNonNull(in);

        mResultCode = in.readInt();
        mErrorMessage = in.readString();
    }

    @NonNull
    public static final Creator<MeasurementErrorResponse> CREATOR =
            new Parcelable.Creator<MeasurementErrorResponse>() {
                @Override
                public MeasurementErrorResponse createFromParcel(@NonNull Parcel in) {
                    Objects.requireNonNull(in);
                    return new MeasurementErrorResponse(in);
                }

                @Override
                public MeasurementErrorResponse[] newArray(int size) {
                    return new MeasurementErrorResponse[size];
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

        dest.writeInt(mResultCode);
        dest.writeString(mErrorMessage);
    }

    /**
     * Returns one of the {@code STATUS} constants defined in {@link MeasurementManager.ResultCode}.
     */
    public int getStatusCode() {
        return mResultCode;
    }

    /** Returns the error message associated with this response. */
    @Nullable
    public String getErrorMessage() {
        return mErrorMessage;
    }

    /** Converts the response to an exception to be used in the callback. */
    @NonNull
    public AdServicesException asException() {

        Exception innerException;
        switch (mResultCode) {
            case RESULT_INVALID_ARGUMENT:
                innerException = new IllegalArgumentException();
                break;
            case RESULT_INTERNAL_ERROR:
                innerException = new IllegalStateException();
                break;
            case RESULT_IO_ERROR:
                innerException = new IOException();
                break;
            case RESULT_UNAUTHORIZED_CALL:
                innerException = new SecurityException(mErrorMessage);
                break;
            case RESULT_OK: // Intentional fallthrough
            default:
                innerException = new Exception();
                break;
        }
        return new MeasurementException(mErrorMessage, innerException);
    }

    /**
     * Builder for {@link MeasurementErrorResponse} objects.
     *
     * @hide
     */
    public static final class Builder {
        @ResultCode private int mResultCode = RESULT_OK;
        @Nullable private String mErrorMessage;

        public Builder() {}

        /** Set the Status Code. */
        @NonNull
        public MeasurementErrorResponse.Builder setResultCode(@ResultCode int resultCode) {
            mResultCode = resultCode;
            return this;
        }

        /** Set the Error Message. */
        @NonNull
        public MeasurementErrorResponse.Builder setErrorMessage(@Nullable String errorMessage) {
            mErrorMessage = errorMessage;
            return this;
        }

        /** Builds a {@link MeasurementErrorResponse} instance. */
        @NonNull
        public MeasurementErrorResponse build() {
            return new MeasurementErrorResponse(mResultCode, mErrorMessage);
        }
    }
}

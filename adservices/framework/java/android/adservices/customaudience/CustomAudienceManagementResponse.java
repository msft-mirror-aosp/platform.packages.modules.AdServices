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

package android.adservices.customaudience;

import android.annotation.IntDef;
import android.annotation.NonNull;
import android.annotation.Nullable;
import android.os.Parcel;
import android.os.Parcelable;

import com.android.internal.util.Preconditions;

import java.lang.annotation.Retention;
import java.lang.annotation.RetentionPolicy;
import java.util.Objects;

/**
 * Represents a return status from
 * {@code CustomAudienceManagementServiceManager#joinCustomAudience(CustomAudience)} and
 * {@code CustomAudienceManagementServiceManager#leaveCustomAudience(String, Uri, String)}.
 * TODO(b/211030351): Correct this javadoc with links when exposed
 *
 * Hiding for future implementation and review for public exposure.
 * @hide
 */
public final class CustomAudienceManagementResponse implements Parcelable {
    @StatusCode
    private final int mStatusCode;
    @Nullable
    private final String mErrorMessage;

    /**
     * Status return codes for
     * {@code CustomAudienceManagementServiceManager#joinCustomAudience(CustomAudience)} and
     * {@code CustomAudienceManagementServiceManager#leaveCustomAudience(String, Uri, String)}
     * methods.
     * TODO(b/211030351): Correct this javadoc with links when exposed
     *
     * @hide
     */
    @IntDef(prefix = {"STATUS_"}, value = {
            STATUS_UNSET,
            STATUS_SUCCESS,
            STATUS_INTERNAL_ERROR,
            STATUS_INVALID_ARGUMENT,
            STATUS_UNKNOWN_ERROR
    })
    @Retention(RetentionPolicy.SOURCE)
    public @interface StatusCode { }

    /**
     * The status code has not been set.
     * Keep unset status code the lowest value of the status codes.
     */
    public static final int STATUS_UNSET = -1;

    /**
     * The call was successful.
     */
    public static final int STATUS_SUCCESS = 0;

    /**
     * An internal error occurred within the Custom Audience API, which the caller cannot address.
     *
     * <p>This error may be considered similar to {@link IllegalStateException}.
     */
    public static final int STATUS_INTERNAL_ERROR = 1;

    /**
     * The caller supplied invalid arguments to the call.
     *
     * <p>This error may be considered similar to {@link IllegalArgumentException}.
     */
    public static final int STATUS_INVALID_ARGUMENT = 2;

    /**
     * There was an unknown error.
     * Keep unknown error the largest status code.
     */
    public static final int STATUS_UNKNOWN_ERROR = 3;

    @NonNull
    public static final Creator<CustomAudienceManagementResponse> CREATOR =
            new Creator<CustomAudienceManagementResponse>() {
                @Override
                public CustomAudienceManagementResponse createFromParcel(Parcel in) {
                    return new CustomAudienceManagementResponse(in);
                }

                @Override
                public CustomAudienceManagementResponse[] newArray(int size) {
                    return new CustomAudienceManagementResponse[size];
                }
            };

    private CustomAudienceManagementResponse(@StatusCode int statusCode,
            @Nullable String errorMessage) {
        mStatusCode = statusCode;
        mErrorMessage = errorMessage;
    }

    private CustomAudienceManagementResponse(@NonNull Parcel in) {
        mStatusCode = in.readInt();
        mErrorMessage = in.readString();
    }

    @Override
    public void writeToParcel(@NonNull Parcel dest, int flags) {
        dest.writeInt(mStatusCode);
        dest.writeString(mErrorMessage);
    }

    @Override
    public int describeContents() {
        return 0;
    }

    /**
     * Checks whether two {@link CustomAudienceManagementResponse} objects contain the same
     * information.
     */
    @Override
    public boolean equals(Object o) {
        if (this == o) return true;
        if (!(o instanceof CustomAudienceManagementResponse)) return false;
        CustomAudienceManagementResponse that = (CustomAudienceManagementResponse) o;
        return mStatusCode == that.mStatusCode
                && ((mErrorMessage == null && that.mErrorMessage == null)
                        || (mErrorMessage != null && mErrorMessage.equals(that.mErrorMessage)));
    }

    /**
     * @return the hash of the {@link CustomAudienceManagementResponse} object's data
     */
    @Override
    public int hashCode() {
        return Objects.hash(mStatusCode, mErrorMessage);
    }

    /**
     * @return {@code true} if the status code matches {@link #STATUS_SUCCESS}
     */
    public boolean isSuccess() {
        return mStatusCode == STATUS_SUCCESS;
    }

    /**
     * @return the status code set in the response
     */
    @StatusCode
    public int getStatusCode() {
        return mStatusCode;
    }

    /**
     * @return the String error message, which may be {@code null}
     */
    @Nullable
    public String getErrorMessage() {
        return mErrorMessage;
    }

    /** Builder for {@link CustomAudienceManagementResponse} objects. */
    public static final class Builder {
        @StatusCode
        private int mStatusCode = STATUS_UNSET;

        @Nullable
        private String mErrorMessage;

        public Builder() { }

        /**
         * Sets the status code to be returned.
         *
         * @throws IllegalArgumentException if statusCode is invalid
         */
        @NonNull
        public CustomAudienceManagementResponse.Builder setStatusCode(@StatusCode int statusCode) {
            Preconditions.checkArgument(statusCode > STATUS_UNSET
                    && statusCode <= STATUS_UNKNOWN_ERROR, "Invalid StatusCode");

            mStatusCode = statusCode;
            return this;
        }

        /**
         * Sets the response's error message.
         *
         * This method does not permit a null message to be set; simply omit building a message to
         * leave the error message {@code null}.
         */
        @NonNull
        public CustomAudienceManagementResponse.Builder setErrorMessage(
                @NonNull String errorMessage) {
            mErrorMessage = errorMessage;
            return this;
        }

        /**
         * Builds the {@link CustomAudienceManagementResponse} object.
         *
         * @throws IllegalArgumentException if status code is unset when the response is built
         */
        @NonNull
        public CustomAudienceManagementResponse build() {
            Preconditions.checkArgument(mStatusCode != STATUS_UNSET, "StatusCode unset");

            return new CustomAudienceManagementResponse(mStatusCode, mErrorMessage);
        }
    }
}

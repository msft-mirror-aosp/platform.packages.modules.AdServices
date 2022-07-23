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

package android.adservices.appsetid;

import static android.adservices.appsetid.AppsetIdManager.RESULT_OK;

import android.adservices.appsetid.AppsetIdManager.ResultCode;
import android.annotation.IntDef;
import android.annotation.NonNull;
import android.annotation.Nullable;
import android.os.Parcel;
import android.os.Parcelable;

import java.lang.annotation.Retention;
import java.lang.annotation.RetentionPolicy;
import java.util.Objects;

/**
 * Represent the result from the getAppsetId API.
 *
 * @hide
 */
public final class GetAppsetIdResult implements Parcelable {
    private final @ResultCode int mResultCode;
    @Nullable private final String mErrorMessage;
    @NonNull private final String mAppsetId;

    /** @hide */
    @Retention(RetentionPolicy.SOURCE)
    @IntDef({
        SCOPE_APP,
        SCOPE_DEVELOPER,
    })
    public @interface AppsetIdScope {}
    /** The appsetId is scoped to an app. All apps on a device will have a different appsetId. */
    public static final int SCOPE_APP = 1;

    /**
     * The appsetId is scoped to a developer account on an app store. All apps from the same
     * developer on a device will have the same developer scoped appsetId.
     */
    public static final int SCOPE_DEVELOPER = 2;

    private final @AppsetIdScope int mAppsetIdScope;

    private GetAppsetIdResult(
            @ResultCode int resultCode,
            @Nullable String errorMessage,
            @NonNull String appsetId,
            @AppsetIdScope int appsetIdScope) {
        mResultCode = resultCode;
        mErrorMessage = errorMessage;
        mAppsetId = appsetId;
        mAppsetIdScope = appsetIdScope;
    }

    private GetAppsetIdResult(@NonNull Parcel in) {
        Objects.requireNonNull(in);

        mResultCode = in.readInt();
        mErrorMessage = in.readString();
        mAppsetId = in.readString();
        mAppsetIdScope = in.readInt();
    }

    public static final @NonNull Creator<GetAppsetIdResult> CREATOR =
            new Parcelable.Creator<GetAppsetIdResult>() {
                @Override
                public GetAppsetIdResult createFromParcel(@NonNull Parcel in) {
                    Objects.requireNonNull(in);
                    return new GetAppsetIdResult(in);
                }

                @Override
                public GetAppsetIdResult[] newArray(int size) {
                    return new GetAppsetIdResult[size];
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
        out.writeString(mAppsetId);
        out.writeInt(mAppsetIdScope);
    }

    /** Returns {@code true} if {@link #getResultCode} is {@link GetAppsetIdResult#RESULT_OK}. */
    public boolean isSuccess() {
        return getResultCode() == RESULT_OK;
    }

    /** Returns one of the {@code RESULT} constants defined in {@link GetAppsetIdResult}. */
    public @ResultCode int getResultCode() {
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

    /** Returns the AppsetId associated with this result. */
    @NonNull
    public String getAppsetId() {
        return mAppsetId;
    }

    /** Returns the appsetId scope associated with this result. */
    public @AppsetIdScope int getAppsetIdScope() {
        return mAppsetIdScope;
    }

    @Override
    public String toString() {
        return "GetAppsetIdResult{"
                + "mResultCode="
                + mResultCode
                + ", mErrorMessage='"
                + mErrorMessage
                + '\''
                + ", mAppsetId="
                + mAppsetId
                + ", mAppsetIdScope="
                + mAppsetIdScope
                + '}';
    }

    @Override
    public boolean equals(Object o) {
        if (this == o) {
            return true;
        }

        if (!(o instanceof GetAppsetIdResult)) {
            return false;
        }

        GetAppsetIdResult that = (GetAppsetIdResult) o;

        return mResultCode == that.mResultCode
                && Objects.equals(mErrorMessage, that.mErrorMessage)
                && Objects.equals(mAppsetId, that.mAppsetId)
                && (mAppsetIdScope == that.mAppsetIdScope);
    }

    @Override
    public int hashCode() {
        return Objects.hash(mResultCode, mErrorMessage, mAppsetId, mAppsetIdScope);
    }

    /**
     * Builder for {@link GetAppsetIdResult} objects.
     *
     * @hide
     */
    public static final class Builder {
        private @ResultCode int mResultCode;
        @Nullable private String mErrorMessage;
        @NonNull private String mAppsetId;
        private @AppsetIdScope int mAppsetIdScope;

        public Builder() {}

        /** Set the Result Code. */
        public @NonNull Builder setResultCode(@ResultCode int resultCode) {
            mResultCode = resultCode;
            return this;
        }

        /** Set the Error Message. */
        public @NonNull Builder setErrorMessage(@Nullable String errorMessage) {
            mErrorMessage = errorMessage;
            return this;
        }

        /** Set the appsetId. */
        public @NonNull Builder setAppsetId(@NonNull String appsetId) {
            mAppsetId = appsetId;
            return this;
        }

        /** Set the appsetId scope field. */
        public @NonNull Builder setAppsetIdScope(@AppsetIdScope int scope) {
            mAppsetIdScope = scope;
            return this;
        }

        /** Builds a {@link GetAppsetIdResult} instance. */
        public @NonNull GetAppsetIdResult build() {

            return new GetAppsetIdResult(mResultCode, mErrorMessage, mAppsetId, mAppsetIdScope);
        }
    }
}

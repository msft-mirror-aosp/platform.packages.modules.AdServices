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

import android.annotation.NonNull;
import android.os.Parcel;
import android.os.Parcelable;

import java.util.Objects;

/**
 * Internal source registration request object to communicate from {@link MeasurementManager} to
 * {@link IMeasurementService}.
 *
 * @hide
 */
public class WebSourceRegistrationRequestInternal implements Parcelable {
    /** Creator for Paracelable (via reflection). */
    public static final Parcelable.Creator<WebSourceRegistrationRequestInternal> CREATOR =
            new Parcelable.Creator<WebSourceRegistrationRequestInternal>() {
                @Override
                public WebSourceRegistrationRequestInternal createFromParcel(Parcel in) {
                    return new WebSourceRegistrationRequestInternal(in);
                }

                @Override
                public WebSourceRegistrationRequestInternal[] newArray(int size) {
                    return new WebSourceRegistrationRequestInternal[size];
                }
            };
    /** Holds input to measurement source registration calls from web context. */
    @NonNull private final WebSourceRegistrationRequest mSourceRegistrationRequest;
    /** Holds package info of where the request is coming from. */
    @NonNull private final String mPackageName;
    /** Time the request was created, as millis since boot excluding time in deep sleep. */
    private final long mRequestTime;

    private WebSourceRegistrationRequestInternal(@NonNull Builder builder) {
        mSourceRegistrationRequest = builder.mSourceRegistrationRequest;
        mPackageName = builder.mPackageName;
        mRequestTime = builder.mRequestTime;
    }

    private WebSourceRegistrationRequestInternal(Parcel in) {
        Objects.requireNonNull(in);
        mSourceRegistrationRequest = WebSourceRegistrationRequest.CREATOR.createFromParcel(in);
        mPackageName = in.readString();
        mRequestTime = in.readLong();
    }

    /** Getter for {@link #mSourceRegistrationRequest}. */
    public WebSourceRegistrationRequest getSourceRegistrationRequest() {
        return mSourceRegistrationRequest;
    }

    /** Getter for {@link #mPackageName}. */
    public String getPackageName() {
        return mPackageName;
    }

    /** Getter for {@link #mRequestTime}. */
    public long getRequestTime() {
        return mRequestTime;
    }

    @Override
    public boolean equals(Object o) {
        if (this == o) return true;
        if (!(o instanceof WebSourceRegistrationRequestInternal)) return false;
        WebSourceRegistrationRequestInternal that = (WebSourceRegistrationRequestInternal) o;
        return Objects.equals(mSourceRegistrationRequest, that.mSourceRegistrationRequest)
                && Objects.equals(mPackageName, that.mPackageName)
                && mRequestTime == that.mRequestTime;
    }

    @Override
    public int hashCode() {
        return Objects.hash(mSourceRegistrationRequest, mPackageName);
    }

    @Override
    public int describeContents() {
        return 0;
    }

    @Override
    public void writeToParcel(@NonNull Parcel out, int flags) {
        Objects.requireNonNull(out);
        mSourceRegistrationRequest.writeToParcel(out, flags);
        out.writeString(mPackageName);
        out.writeLong(mRequestTime);
    }

    /** Builder for {@link WebSourceRegistrationRequestInternal}. */
    public static final class Builder {
        /** External source registration request from client app SDK. */
        @NonNull private final WebSourceRegistrationRequest mSourceRegistrationRequest;
        /** Client's package name used for the registration. Used to determine the registrant. */
        @NonNull private final String mPackageName;
        /** Time the request was created, as millis since boot excluding time in deep sleep. */
        private final long mRequestTime;

        /**
         * Builder constructor for {@link WebSourceRegistrationRequestInternal}.
         *
         * @param sourceRegistrationRequest external source registration request
         * @param packageName that is calling PP API
         * @param requestTime time that the request was created
         */
        public Builder(
                @NonNull WebSourceRegistrationRequest sourceRegistrationRequest,
                @NonNull String packageName,
                long requestTime) {
            Objects.requireNonNull(sourceRegistrationRequest);
            Objects.requireNonNull(packageName);
            mSourceRegistrationRequest = sourceRegistrationRequest;
            mPackageName = packageName;
            mRequestTime = requestTime;
        }

        /** Pre-validates parameters and builds {@link WebSourceRegistrationRequestInternal}. */
        @NonNull
        public WebSourceRegistrationRequestInternal build() {
            return new WebSourceRegistrationRequestInternal(this);
        }
    }
}

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
 * Internal trigger registration request object to communicate from {@link MeasurementManager} to
 * {@link IMeasurementService}.
 *
 * @hide
 */
public class WebTriggerRegistrationRequestInternal implements Parcelable {
    /** Creator for Paracelable (via reflection). */
    @NonNull
    public static final Creator<WebTriggerRegistrationRequestInternal> CREATOR =
            new Creator<WebTriggerRegistrationRequestInternal>() {
                @Override
                public WebTriggerRegistrationRequestInternal createFromParcel(Parcel in) {
                    return new WebTriggerRegistrationRequestInternal(in);
                }

                @Override
                public WebTriggerRegistrationRequestInternal[] newArray(int size) {
                    return new WebTriggerRegistrationRequestInternal[size];
                }
            };
    /** Holds input to measurement trigger registration calls from web context. */
    @NonNull private final WebTriggerRegistrationRequest mTriggerRegistrationRequest;
    /** Holds package info of where the request is coming from. */
    @NonNull private final String mPackageName;

    private WebTriggerRegistrationRequestInternal(@NonNull Builder builder) {
        mTriggerRegistrationRequest = builder.mTriggerRegistrationRequest;
        mPackageName = builder.mPackageName;
    }

    private WebTriggerRegistrationRequestInternal(Parcel in) {
        Objects.requireNonNull(in);
        mTriggerRegistrationRequest = WebTriggerRegistrationRequest.CREATOR.createFromParcel(in);
        mPackageName = in.readString();
    }

    /** Getter for {@link #mTriggerRegistrationRequest}. */
    public WebTriggerRegistrationRequest getTriggerRegistrationRequest() {
        return mTriggerRegistrationRequest;
    }

    /** Getter for {@link #mPackageName}. */
    public String getPackageName() {
        return mPackageName;
    }

    @Override
    public boolean equals(Object o) {
        if (this == o) return true;
        if (!(o instanceof WebTriggerRegistrationRequestInternal)) return false;
        WebTriggerRegistrationRequestInternal that = (WebTriggerRegistrationRequestInternal) o;
        return Objects.equals(mTriggerRegistrationRequest, that.mTriggerRegistrationRequest)
                && Objects.equals(mPackageName, that.mPackageName);
    }

    @Override
    public int hashCode() {
        return Objects.hash(mTriggerRegistrationRequest, mPackageName);
    }

    @Override
    public int describeContents() {
        return 0;
    }

    @Override
    public void writeToParcel(@NonNull Parcel out, int flags) {
        Objects.requireNonNull(out);
        mTriggerRegistrationRequest.writeToParcel(out, flags);
        out.writeString(mPackageName);
    }

    /** Builder for {@link WebTriggerRegistrationRequestInternal}. */
    public static final class Builder {
        /** External trigger registration request from client app SDK. */
        @NonNull private final WebTriggerRegistrationRequest mTriggerRegistrationRequest;
        /** Package name used for the registration. Used to determine the registrant. */
        @NonNull private final String mPackageName;

        /**
         * Builder constructor for {@link WebTriggerRegistrationRequestInternal}.
         *
         * @param triggerRegistrationRequest external trigger registration request
         * @param packageName that is calling PP API
         */
        public Builder(
                @NonNull WebTriggerRegistrationRequest triggerRegistrationRequest,
                @NonNull String packageName) {
            Objects.requireNonNull(triggerRegistrationRequest);
            Objects.requireNonNull(packageName);
            mTriggerRegistrationRequest = triggerRegistrationRequest;
            mPackageName = packageName;
        }

        /** Pre-validates parameters and builds {@link WebTriggerRegistrationRequestInternal}. */
        @NonNull
        public WebTriggerRegistrationRequestInternal build() {
            return new WebTriggerRegistrationRequestInternal(this);
        }
    }
}

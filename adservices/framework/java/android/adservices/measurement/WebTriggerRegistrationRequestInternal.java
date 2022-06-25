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
import android.content.AttributionSource;
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
    /** Holds input to measurement trigger registration calls from embedded web context. */
    @NonNull private final WebTriggerRegistrationRequest mTriggerRegistrationRequest;
    /** Holds package info of where the request is coming from. */
    @NonNull private final AttributionSource mAttributionSource;

    private WebTriggerRegistrationRequestInternal(
            WebTriggerRegistrationRequest triggerRegistrationRequest,
            AttributionSource attributionSource) {
        mTriggerRegistrationRequest = triggerRegistrationRequest;
        mAttributionSource = attributionSource;
    }

    private WebTriggerRegistrationRequestInternal(Parcel in) {
        Objects.requireNonNull(in);
        mTriggerRegistrationRequest = WebTriggerRegistrationRequest.CREATOR.createFromParcel(in);
        mAttributionSource = AttributionSource.CREATOR.createFromParcel(in);
    }

    /** Getter for {@link #mTriggerRegistrationRequest}. */
    public WebTriggerRegistrationRequest getTriggerRegistrationRequest() {
        return mTriggerRegistrationRequest;
    }

    /** Getter for {@link #mAttributionSource}. */
    public AttributionSource getAttributionSource() {
        return mAttributionSource;
    }

    @Override
    public boolean equals(Object o) {
        if (this == o) return true;
        if (!(o instanceof WebTriggerRegistrationRequestInternal)) return false;
        WebTriggerRegistrationRequestInternal that = (WebTriggerRegistrationRequestInternal) o;
        return Objects.equals(mTriggerRegistrationRequest, that.mTriggerRegistrationRequest)
                && Objects.equals(mAttributionSource, that.mAttributionSource);
    }

    @Override
    public int hashCode() {
        return Objects.hash(mTriggerRegistrationRequest, mAttributionSource);
    }

    @Override
    public int describeContents() {
        return 0;
    }

    @Override
    public void writeToParcel(@NonNull Parcel out, int flags) {
        Objects.requireNonNull(out);
        mTriggerRegistrationRequest.writeToParcel(out, flags);
        mAttributionSource.writeToParcel(out, flags);
    }

    /** Builder for {@link WebTriggerRegistrationRequestInternal}. */
    public static final class Builder {
        /** External trigger registration request from client app SDK. */
        @NonNull private WebTriggerRegistrationRequest mTriggerRegistrationRequest;
        /** AttributionSource of the registration. Used to determine the registrant. */
        @NonNull private AttributionSource mAttributionSource;

        /**
         * Setter for {@link #mTriggerRegistrationRequest}.
         *
         * @param triggerRegistrationRequest trigger registration request
         * @return builder
         */
        @NonNull
        public Builder setTriggerRegistrationRequest(
                @NonNull WebTriggerRegistrationRequest triggerRegistrationRequest) {
            mTriggerRegistrationRequest = triggerRegistrationRequest;
            return this;
        }

        /**
         * Setter for {@link #mAttributionSource}.
         *
         * @param attributionSource app that is calling PP API
         * @return builder
         */
        @NonNull
        public Builder setAttributionSource(@NonNull AttributionSource attributionSource) {
            mAttributionSource = attributionSource;
            return this;
        }

        /** Pre-validates paramerters and builds {@link WebTriggerRegistrationRequestInternal}. */
        @NonNull
        public WebTriggerRegistrationRequestInternal build() {
            Objects.requireNonNull(mTriggerRegistrationRequest);
            Objects.requireNonNull(mAttributionSource);

            return new WebTriggerRegistrationRequestInternal(
                    mTriggerRegistrationRequest, mAttributionSource);
        }
    }
}

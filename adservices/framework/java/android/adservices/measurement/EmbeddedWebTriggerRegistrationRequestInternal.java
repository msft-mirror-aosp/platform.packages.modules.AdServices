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
public class EmbeddedWebTriggerRegistrationRequestInternal implements Parcelable {
    /** Creator for Paracelable (via reflection). */
    @NonNull
    public static final Creator<EmbeddedWebTriggerRegistrationRequestInternal> CREATOR =
            new Creator<EmbeddedWebTriggerRegistrationRequestInternal>() {
                @Override
                public EmbeddedWebTriggerRegistrationRequestInternal createFromParcel(Parcel in) {
                    return new EmbeddedWebTriggerRegistrationRequestInternal(in);
                }

                @Override
                public EmbeddedWebTriggerRegistrationRequestInternal[] newArray(int size) {
                    return new EmbeddedWebTriggerRegistrationRequestInternal[size];
                }
            };
    /** Holds input to measurement trigger registration calls from embedded web context. */
    @NonNull private final EmbeddedWebTriggerRegistrationRequest mTriggerRegistrationRequest;
    /** Holds package info of where the request is coming from. */
    @NonNull private final AttributionSource mAttributionSource;

    private EmbeddedWebTriggerRegistrationRequestInternal(
            EmbeddedWebTriggerRegistrationRequest triggerRegistrationRequest,
            AttributionSource attributionSource) {
        mTriggerRegistrationRequest = triggerRegistrationRequest;
        mAttributionSource = attributionSource;
    }

    private EmbeddedWebTriggerRegistrationRequestInternal(Parcel in) {
        Objects.requireNonNull(in);
        mTriggerRegistrationRequest =
                EmbeddedWebTriggerRegistrationRequest.CREATOR.createFromParcel(in);
        mAttributionSource = AttributionSource.CREATOR.createFromParcel(in);
    }

    /** Getter for {@link #mTriggerRegistrationRequest}. */
    public EmbeddedWebTriggerRegistrationRequest getTriggerRegistrationRequest() {
        return mTriggerRegistrationRequest;
    }

    /** Getter for {@link #mAttributionSource}. */
    public AttributionSource getAttributionSource() {
        return mAttributionSource;
    }

    @Override
    public boolean equals(Object o) {
        if (this == o) return true;
        if (!(o instanceof EmbeddedWebTriggerRegistrationRequestInternal)) return false;
        EmbeddedWebTriggerRegistrationRequestInternal that =
                (EmbeddedWebTriggerRegistrationRequestInternal) o;
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

    /** Builder for {@link EmbeddedWebTriggerRegistrationRequestInternal}. */
    public static final class Builder {
        /** External trigger registration request from client app SDK. */
        @NonNull private EmbeddedWebTriggerRegistrationRequest mTriggerRegistrationRequest;
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
                @NonNull EmbeddedWebTriggerRegistrationRequest triggerRegistrationRequest) {
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

        /**
         * Pre-validates paramerters and builds {@link
         * EmbeddedWebTriggerRegistrationRequestInternal}.
         */
        @NonNull
        public EmbeddedWebTriggerRegistrationRequestInternal build() {
            Objects.requireNonNull(mTriggerRegistrationRequest);
            Objects.requireNonNull(mAttributionSource);

            return new EmbeddedWebTriggerRegistrationRequestInternal(
                    mTriggerRegistrationRequest, mAttributionSource);
        }
    }
}

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
 * Internal source registration request object to communicate from {@link MeasurementManager} to
 * {@link IMeasurementService}.
 *
 * @hide
 */
public class EmbeddedWebSourceRegistrationRequestInternal implements Parcelable {
    /** Creator for Paracelable (via reflection). */
    @NonNull
    public static final Parcelable.Creator<EmbeddedWebSourceRegistrationRequestInternal> CREATOR =
            new Parcelable.Creator<EmbeddedWebSourceRegistrationRequestInternal>() {
                @Override
                public EmbeddedWebSourceRegistrationRequestInternal createFromParcel(Parcel in) {
                    return new EmbeddedWebSourceRegistrationRequestInternal(in);
                }

                @Override
                public EmbeddedWebSourceRegistrationRequestInternal[] newArray(int size) {
                    return new EmbeddedWebSourceRegistrationRequestInternal[size];
                }
            };
    /** Holds input to measurement source registration calls from embedded web context. */
    @NonNull private final EmbeddedWebSourceRegistrationRequest mSourceRegistrationRequest;
    /** Holds package info of where the request is coming from. */
    @NonNull private final AttributionSource mAttributionSource;

    private EmbeddedWebSourceRegistrationRequestInternal(
            EmbeddedWebSourceRegistrationRequest sourceRegistrationRequest,
            AttributionSource attributionSource) {
        mSourceRegistrationRequest = sourceRegistrationRequest;
        mAttributionSource = attributionSource;
    }

    private EmbeddedWebSourceRegistrationRequestInternal(Parcel in) {
        Objects.requireNonNull(in);
        mSourceRegistrationRequest =
                EmbeddedWebSourceRegistrationRequest.CREATOR.createFromParcel(in);
        mAttributionSource = AttributionSource.CREATOR.createFromParcel(in);
    }

    /** Getter for {@link #mSourceRegistrationRequest}. */
    public EmbeddedWebSourceRegistrationRequest getSourceRegistrationRequest() {
        return mSourceRegistrationRequest;
    }

    /** Getter for {@link #mAttributionSource}. */
    public AttributionSource getAttributionSource() {
        return mAttributionSource;
    }

    @Override
    public boolean equals(Object o) {
        if (this == o) return true;
        if (!(o instanceof EmbeddedWebSourceRegistrationRequestInternal)) return false;
        EmbeddedWebSourceRegistrationRequestInternal that =
                (EmbeddedWebSourceRegistrationRequestInternal) o;
        return Objects.equals(mSourceRegistrationRequest, that.mSourceRegistrationRequest)
                && Objects.equals(mAttributionSource, that.mAttributionSource);
    }

    @Override
    public int hashCode() {
        return Objects.hash(mSourceRegistrationRequest, mAttributionSource);
    }

    @Override
    public int describeContents() {
        return 0;
    }

    @Override
    public void writeToParcel(@NonNull Parcel out, int flags) {
        Objects.requireNonNull(out);
        mSourceRegistrationRequest.writeToParcel(out, flags);
        mAttributionSource.writeToParcel(out, flags);
    }

    /** Builder for {@link EmbeddedWebSourceRegistrationRequestInternal}. */
    public static final class Builder {
        /** External source registration request from client app SDK. */
        @NonNull private EmbeddedWebSourceRegistrationRequest mSourceRegistrationRequest;
        /** AttributionSource of the registration. Used to determine the registrant. */
        @NonNull private AttributionSource mAttributionSource;

        /**
         * Setter for {@link #mSourceRegistrationRequest}.
         *
         * @param sourceRegistrationRequest source registration request
         * @return builder
         */
        @NonNull
        public Builder setSourceRegistrationRequest(
                @NonNull EmbeddedWebSourceRegistrationRequest sourceRegistrationRequest) {
            mSourceRegistrationRequest = sourceRegistrationRequest;
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
         * EmbeddedWebSourceRegistrationRequestInternal}.
         */
        @NonNull
        public EmbeddedWebSourceRegistrationRequestInternal build() {
            Objects.requireNonNull(mSourceRegistrationRequest);
            Objects.requireNonNull(mAttributionSource);

            return new EmbeddedWebSourceRegistrationRequestInternal(
                    mSourceRegistrationRequest, mAttributionSource);
        }
    }
}

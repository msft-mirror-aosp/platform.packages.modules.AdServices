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
import android.net.Uri;
import android.os.Parcel;
import android.os.Parcelable;

import java.util.ArrayList;
import java.util.List;
import java.util.Objects;

/**
 * Class to hold input to measurement trigger registration calls from embedded web context.
 *
 * @hide
 */
public final class WebTriggerRegistrationRequest implements Parcelable {
    /** Creator for Paracelable (via reflection). */
    @NonNull
    public static final Parcelable.Creator<WebTriggerRegistrationRequest> CREATOR =
            new Parcelable.Creator<WebTriggerRegistrationRequest>() {
                @Override
                public WebTriggerRegistrationRequest createFromParcel(Parcel in) {
                    return new WebTriggerRegistrationRequest(in);
                }

                @Override
                public WebTriggerRegistrationRequest[] newArray(int size) {
                    return new WebTriggerRegistrationRequest[size];
                }
            };
    /** Registration info to fetch sources. */
    @NonNull private final List<TriggerParams> mTriggerParams;

    /** Destination {@link Uri}. */
    @NonNull private final Uri mDestination;

    /** Constructor for {@link WebTriggerRegistrationRequest}. */
    private WebTriggerRegistrationRequest(
            @NonNull List<TriggerParams> triggerParams, @NonNull Uri destination) {
        mTriggerParams = triggerParams;
        mDestination = destination;
    }

    /** Unpack parcel of OSAttributionTriggerRegistrationRequest. */
    private WebTriggerRegistrationRequest(Parcel in) {
        Objects.requireNonNull(in);
        ArrayList<TriggerParams> triggerParams = new ArrayList<>();
        in.readList(triggerParams, TriggerParams.class.getClassLoader(), TriggerParams.class);
        mTriggerParams = triggerParams;
        mDestination = Uri.CREATOR.createFromParcel(in);
    }

    @Override
    public boolean equals(Object o) {
        if (this == o) return true;
        if (!(o instanceof WebTriggerRegistrationRequest)) return false;
        WebTriggerRegistrationRequest that = (WebTriggerRegistrationRequest) o;
        return Objects.equals(mTriggerParams, that.mTriggerParams)
                && Objects.equals(mDestination, that.mDestination);
    }

    @Override
    public int hashCode() {
        return Objects.hash(mTriggerParams, mDestination);
    }

    /** Getter for trigger params. */
    @NonNull
    public List<TriggerParams> getTriggerParams() {
        return mTriggerParams;
    }

    /** Getter for destination. */
    @NonNull
    public Uri getDestination() {
        return mDestination;
    }

    @Override
    public int describeContents() {
        return 0;
    }

    @Override
    public void writeToParcel(@NonNull Parcel out, int flags) {
        Objects.requireNonNull(out);
        out.writeList(mTriggerParams);
        mDestination.writeToParcel(out, flags);
    }

    /** Builder for {@link WebTriggerRegistrationRequest}. */
    public static final class Builder {
        /**
         * Registration info to fetch triggers. Maximum 20 registrations allowed at once, to be in
         * sync with Chrome platform.
         */
        @NonNull private List<TriggerParams> mTriggerParams;
        /** Top level origin of publisher app. */
        @NonNull private Uri mDestination;

        /**
         * Setter for trigger params.
         *
         * @param triggerParams source registrations
         * @return builder
         */
        @NonNull
        public Builder setTriggerParams(@NonNull List<TriggerParams> triggerParams) {
            mTriggerParams = triggerParams;
            return this;
        }

        /**
         * Setter for destination.
         *
         * @param destination destination top origin {@link Uri}
         * @return builder
         */
        @NonNull
        public Builder setDestination(@NonNull Uri destination) {
            mDestination = destination;
            return this;
        }

        /** Pre-validates parameters and builds {@link WebTriggerRegistrationRequest}. */
        @NonNull
        public WebTriggerRegistrationRequest build() {
            if (mTriggerParams == null || mTriggerParams.isEmpty()) {
                throw new IllegalArgumentException("registration URI unset");
            }

            Objects.requireNonNull(mDestination);

            return new WebTriggerRegistrationRequest(mTriggerParams, mDestination);
        }
    }
}

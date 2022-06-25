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

import java.util.Objects;

/**
 * Class holding trigger registration parameters.
 *
 * @hide
 */
public final class TriggerParams implements Parcelable {
    /** Creator for Paracelable (via reflection). */
    @NonNull
    public static final Creator<TriggerParams> CREATOR =
            new Creator<TriggerParams>() {
                @Override
                public TriggerParams createFromParcel(Parcel in) {
                    return new TriggerParams(in);
                }

                @Override
                public TriggerParams[] newArray(int size) {
                    return new TriggerParams[size];
                }
            };
    /** Used to fetch registration metadata. */
    @NonNull private final Uri mRegistrationUri;
    /** True, if debugKey should be allowed in reports. */
    private final boolean mDebugEnabled;

    /**
     * Constructor for {@link TriggerParams}.
     *
     * @param registrationUri registration URI
     * @param debugEnabled flag for enabling or disabling debug keys
     */
    private TriggerParams(@NonNull Uri registrationUri, boolean debugEnabled) {
        mRegistrationUri = registrationUri;
        mDebugEnabled = debugEnabled;
    }

    /** Unpack a TriggerRegistration from a Parcel. */
    private TriggerParams(@NonNull Parcel in) {
        mRegistrationUri = Uri.CREATOR.createFromParcel(in);
        mDebugEnabled = in.readBoolean();
    }

    @Override
    public boolean equals(Object o) {
        if (this == o) return true;
        if (!(o instanceof TriggerParams)) return false;
        TriggerParams that = (TriggerParams) o;
        return mDebugEnabled == that.mDebugEnabled
                && Objects.equals(mRegistrationUri, that.mRegistrationUri);
    }

    @Override
    public int hashCode() {
        return Objects.hash(mRegistrationUri, mDebugEnabled);
    }

    /** Getter for registration Uri. */
    @NonNull
    public Uri getRegistrationUri() {
        return mRegistrationUri;
    }

    /** Getter for debug enablement flag. */
    public boolean isDebugEnabled() {
        return mDebugEnabled;
    }

    @Override
    public int describeContents() {
        return 0;
    }

    @Override
    public void writeToParcel(@NonNull Parcel out, int flags) {
        Objects.requireNonNull(out);
        mRegistrationUri.writeToParcel(out, flags);
        out.writeBoolean(mDebugEnabled);
    }

    /** A builder for {@link TriggerParams}. */
    public static final class Builder {
        /** Used to fetch registration metadata. */
        private Uri mRegistrationUri;
        /** True, if debugKey should be allowed in reports. */
        private boolean mDebugEnabled;

        /** Builder for {@link TriggerParams}. */
        public Builder() {
            mDebugEnabled = false;
        }

        /**
         * Setter for registration Uri.
         *
         * @param registrationUri registration URI
         * @return builder
         */
        @NonNull
        public Builder setRegistrationUri(@NonNull Uri registrationUri) {
            mRegistrationUri = registrationUri;
            return this;
        }

        /**
         * Setter for debug enablement flag.
         *
         * @param debugEnabled debug enablement flag
         * @return builder
         */
        @NonNull
        public Builder setDebugEnabled(boolean debugEnabled) {
            mDebugEnabled = debugEnabled;
            return this;
        }

        /**
         * Built immutable {@link TriggerParams}.
         *
         * @return immutable {@link TriggerParams}
         */
        @NonNull
        public TriggerParams build() {
            if (mRegistrationUri == null) {
                throw new IllegalArgumentException("registration URI unset");
            }

            return new TriggerParams(mRegistrationUri, mDebugEnabled);
        }
    }
}

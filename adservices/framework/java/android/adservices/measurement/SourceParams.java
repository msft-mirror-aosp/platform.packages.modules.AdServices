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
 * Class holding source registration parameters.
 *
 * @hide
 */
public final class SourceParams implements Parcelable {
    /** Creator for Paracelable (via reflection). */
    @NonNull
    public static final Parcelable.Creator<SourceParams> CREATOR =
            new Parcelable.Creator<SourceParams>() {
                @Override
                public SourceParams createFromParcel(Parcel in) {
                    return new SourceParams(in);
                }

                @Override
                public SourceParams[] newArray(int size) {
                    return new SourceParams[size];
                }
            };
    /** Used to fetch registration metadata. */
    @NonNull private final Uri mRegistrationUri;
    /** True, if debugKey should be allowed in reports. */
    private final boolean mDebugEnabled;

    /**
     * Constructor for {@link SourceParams}.
     *
     * @param registrationUri registration URI
     * @param debugEnabled flag for enabling or disabling debug keys
     */
    private SourceParams(@NonNull Uri registrationUri, boolean debugEnabled) {
        mRegistrationUri = registrationUri;
        mDebugEnabled = debugEnabled;
    }

    /** Unpack a SourceRegistration from a Parcel. */
    private SourceParams(@NonNull Parcel in) {
        mRegistrationUri = Uri.CREATOR.createFromParcel(in);
        mDebugEnabled = in.readBoolean();
    }

    @Override
    public boolean equals(Object o) {
        if (this == o) return true;
        if (!(o instanceof SourceParams)) return false;
        SourceParams that = (SourceParams) o;
        return mDebugEnabled == that.mDebugEnabled
                && Objects.equals(mRegistrationUri, that.mRegistrationUri);
    }

    @Override
    public int hashCode() {
        return Objects.hash(mRegistrationUri, mDebugEnabled);
    }

    @NonNull
    public Uri getRegistrationUri() {
        return mRegistrationUri;
    }

    /** Getter for {@link #mDebugEnabled}. */
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

    /** A builder for {@link SourceParams}. */
    public static final class Builder {
        /** Used to fetch registration metadata. */
        private Uri mRegistrationUri;
        /** True, if debugKey should be allowed in reports. */
        private boolean mDebugEnabled;

        /** Builder for {@link SourceParams}. */
        public Builder() {
            mDebugEnabled = false;
        }

        /**
         * Setter for {@link SourceParams#mRegistrationUri}.
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
         * Setter for {@link SourceParams#mDebugEnabled}.
         *
         * @param debugEnabled debug enabler flag
         * @return builder
         */
        @NonNull
        public Builder setDebugEnabled(boolean debugEnabled) {
            mDebugEnabled = debugEnabled;
            return this;
        }

        /**
         * Built immutable {@link SourceParams}.
         *
         * @return immutable {@link SourceParams}
         */
        @NonNull
        public SourceParams build() {
            if (mRegistrationUri == null) {
                throw new IllegalArgumentException("registration URI unset");
            }

            return new SourceParams(mRegistrationUri, mDebugEnabled);
        }
    }
}

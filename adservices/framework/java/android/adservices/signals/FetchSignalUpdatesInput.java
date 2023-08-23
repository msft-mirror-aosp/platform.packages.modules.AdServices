/*
 * Copyright (C) 2023 The Android Open Source Project
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

package android.adservices.signals;

import android.annotation.NonNull;
import android.net.Uri;
import android.os.Parcel;
import android.os.Parcelable;

import java.util.Objects;

/**
 * The input object wrapping the parameters for the fetchSignalUpdates API.
 *
 * <p>Refer to {@link FetchSignalUpdatesRequest} for more information about the parameters.
 *
 * @hide
 */
public final class FetchSignalUpdatesInput implements Parcelable {
    @NonNull private final Uri mFetchUri;
    @NonNull private final String mCallerPackageName;

    @NonNull
    public static final Creator<FetchSignalUpdatesInput> CREATOR =
            new Creator<>() {
                @NonNull
                @Override
                public FetchSignalUpdatesInput createFromParcel(@NonNull Parcel in) {
                    return new FetchSignalUpdatesInput(in);
                }

                @NonNull
                @Override
                public FetchSignalUpdatesInput[] newArray(int size) {
                    return new FetchSignalUpdatesInput[size];
                }
            };

    private FetchSignalUpdatesInput(@NonNull Uri fetchUri, @NonNull String callerPackageName) {
        Objects.requireNonNull(fetchUri);
        Objects.requireNonNull(callerPackageName);

        mFetchUri = fetchUri;
        mCallerPackageName = callerPackageName;
    }

    private FetchSignalUpdatesInput(@NonNull Parcel in) {
        Objects.requireNonNull(in);

        Uri fetchUri = Uri.CREATOR.createFromParcel(in);
        Objects.requireNonNull(fetchUri);
        mFetchUri = fetchUri;
        String callerPackageName = in.readString();
        Objects.requireNonNull(callerPackageName);
        mCallerPackageName = callerPackageName;
    }

    /**
     * @return the {@link Uri} from which the signals will be fetched.
     */
    @NonNull
    public Uri getFetchUri() {
        return mFetchUri;
    }

    /**
     * @return the caller app's package name.
     */
    @NonNull
    public String getCallerPackageName() {
        return mCallerPackageName;
    }

    @Override
    public int describeContents() {
        return 0;
    }

    @Override
    public void writeToParcel(@NonNull Parcel dest, int flags) {
        Objects.requireNonNull(dest);

        mFetchUri.writeToParcel(dest, flags);
        dest.writeString(mCallerPackageName);
    }

    /**
     * @return {@code true} if and only if the other object is {@link FetchSignalUpdatesRequest}
     *     with the same fetch URI and package name
     */
    @Override
    public boolean equals(Object o) {
        if (!(o instanceof FetchSignalUpdatesInput)) return false;
        FetchSignalUpdatesInput that = (FetchSignalUpdatesInput) o;
        return mFetchUri.equals(that.mFetchUri)
                && mCallerPackageName.equals(that.mCallerPackageName);
    }

    /**
     * @return the hash of the {@link FetchSignalUpdatesInput} object's data.
     */
    @Override
    public int hashCode() {
        return Objects.hash(mFetchUri, mCallerPackageName);
    }

    @Override
    public String toString() {
        return "FetchSignalUpdatesInput{"
                + "mFetchUri="
                + mFetchUri
                + ", mCallerPackageName='"
                + mCallerPackageName
                + '\''
                + '}';
    }

    /**
     * Builder for {@link FetchSignalUpdatesInput} objects.
     *
     * @hide
     */
    public static final class Builder {
        @NonNull private Uri mFetchUri;
        @NonNull private String mCallerPackageName;

        /**
         * Instantiates a {@link FetchSignalUpdatesInput.Builder} with the {@link Uri} from which
         * the JSON is to be fetched and the caller app's package name.
         *
         * @throws NullPointerException if any non-null parameter is null.
         */
        public Builder(@NonNull Uri fetchUri, @NonNull String callerPackageName) {
            Objects.requireNonNull(fetchUri);
            Objects.requireNonNull(callerPackageName);

            this.mFetchUri = fetchUri;
            this.mCallerPackageName = callerPackageName;
        }

        /**
         * Sets the {@link Uri} from which the signal updates will be fetched.
         *
         * <p>See {@link #getFetchUri()} ()} for details.
         */
        @NonNull
        public Builder setFetchUri(@NonNull Uri fetchUri) {
            Objects.requireNonNull(fetchUri);
            this.mFetchUri = fetchUri;
            return this;
        }

        /**
         * Sets the caller app's package name.
         *
         * <p>See {@link #getCallerPackageName()} for details.
         */
        @NonNull
        public Builder setCallerPackageName(@NonNull String callerPackageName) {
            Objects.requireNonNull(callerPackageName);
            this.mCallerPackageName = callerPackageName;
            return this;
        }

        /**
         * Builds an instance of a {@link FetchSignalUpdatesInput}.
         *
         * @throws NullPointerException if any non-null parameter is null.
         */
        @NonNull
        public FetchSignalUpdatesInput build() {
            return new FetchSignalUpdatesInput(mFetchUri, mCallerPackageName);
        }
    }
}

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

import java.util.Objects;

/**
 * The request object for fetchSignalsUpdates.
 *
 * <p>{@code fetchUri} is the only parameter. It represents the URI that the service will reach out
 * to retrieve the signals updates.
 *
 * @hide
 */
public final class FetchSignalUpdatesRequest {
    @NonNull private final Uri mFetchUri;

    private FetchSignalUpdatesRequest(@NonNull Uri fetchUri) {
        Objects.requireNonNull(fetchUri, "fetchUri must not be null in FetchSignalUpdatesRequest");

        mFetchUri = fetchUri;
    }

    /**
     * @return the {@link Uri} from which the signal updates will be fetched.
     */
    @NonNull
    public Uri getFetchUri() {
        return mFetchUri;
    }

    /**
     * @return {@code true} if and only if the other object is {@link FetchSignalUpdatesRequest}
     *     with the same fetch URI.
     */
    @Override
    public boolean equals(Object o) {
        if (!(o instanceof FetchSignalUpdatesRequest)) return false;
        FetchSignalUpdatesRequest other = (FetchSignalUpdatesRequest) o;
        return mFetchUri.equals(other.mFetchUri);
    }

    /**
     * @return the hash of the {@link FetchSignalUpdatesRequest} object's data.
     */
    @Override
    public int hashCode() {
        return Objects.hash(mFetchUri);
    }

    /**
     * @return a human-readable representation of {@link FetchSignalUpdatesRequest}.
     */
    @Override
    public String toString() {
        return "FetchSignalUpdatesRequest{" + "fetchUri=" + mFetchUri + '}';
    }

    /** Builder for {@link FetchSignalUpdatesRequest} objects. */
    public static final class Builder {
        @NonNull private Uri mFetchUri;

        /**
         * Instantiates a {@link Builder} with the {@link Uri} from which the signal updates will be
         * fetched.
         */
        public Builder(@NonNull Uri fetchUri) {
            Objects.requireNonNull(fetchUri);
            this.mFetchUri = fetchUri;
        }

        /**
         * Sets the {@link Uri} from which the JSON is to be fetched.
         *
         * <p>See {@link #getFetchUri()} ()} for details.
         */
        @NonNull
        public Builder setFetchUri(@NonNull Uri fetchUri) {
            Objects.requireNonNull(
                    fetchUri, "fetchUri must not be null in FetchSignalUpdatesRequest");
            this.mFetchUri = fetchUri;
            return this;
        }

        /**
         * Builds an instance of a {@link FetchSignalUpdatesRequest}.
         *
         * @throws NullPointerException if any non-null parameter is null.
         */
        @NonNull
        public FetchSignalUpdatesRequest build() {
            return new FetchSignalUpdatesRequest(mFetchUri);
        }
    }
}

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

package android.adservices.customaudience;

import android.annotation.NonNull;
import android.net.Uri;
import android.os.Parcel;
import android.os.Parcelable;

import java.util.List;
import java.util.Objects;

/**
 * Represents semi-opaque data used during the ad selection process to fetch bidding signals.
 *
 * Hiding for future implementation and review for public exposure.
 * @hide
 */
public final class TrustedBiddingData implements Parcelable {
    @NonNull
    private final Uri mTrustedBiddingUrl;
    @NonNull
    private final List<String> mTrustedBiddingKeys;

    @NonNull
    public static final Creator<TrustedBiddingData> CREATOR = new Creator<TrustedBiddingData>() {
        @Override
        public TrustedBiddingData createFromParcel(@NonNull Parcel in) {
            Objects.requireNonNull(in);
            return new TrustedBiddingData(in);
        }

        @Override
        public TrustedBiddingData[] newArray(int size) {
            return new TrustedBiddingData[size];
        }
    };

    private TrustedBiddingData(@NonNull Uri trustedBiddingUrl,
            @NonNull List<String> trustedBiddingKeys) {
        Objects.requireNonNull(trustedBiddingUrl);
        Objects.requireNonNull(trustedBiddingKeys);
        mTrustedBiddingUrl = trustedBiddingUrl;
        mTrustedBiddingKeys = trustedBiddingKeys;
    }

    private TrustedBiddingData(@NonNull Parcel in) {
        Objects.requireNonNull(in);
        mTrustedBiddingUrl = Uri.CREATOR.createFromParcel(in);
        mTrustedBiddingKeys = in.createStringArrayList();
    }

    @Override
    public void writeToParcel(@NonNull Parcel dest, int flags) {
        Objects.requireNonNull(dest);
        mTrustedBiddingUrl.writeToParcel(dest, flags);
        dest.writeStringList(mTrustedBiddingKeys);
    }

    /** @hide */
    @Override
    public int describeContents() {
        return 0;
    }

    /**
     * @return the URL pointing to the trusted key-value server holding bidding signals
     */
    @NonNull
    public Uri getTrustedBiddingUrl() {
        return mTrustedBiddingUrl;
    }

    /**
     * @return the list of keys to query from the trusted key-value server holding bidding signals
     */
    @NonNull
    public List<String> getTrustedBiddingKeys() {
        return mTrustedBiddingKeys;
    }

    /**
     * @return {@code true} if two {@link TrustedBiddingData} objects contain the same information
     */
    @Override
    public boolean equals(Object o) {
        if (this == o) return true;
        if (!(o instanceof TrustedBiddingData)) return false;
        TrustedBiddingData that = (TrustedBiddingData) o;
        return mTrustedBiddingUrl.equals(that.mTrustedBiddingUrl) && mTrustedBiddingKeys.equals(
                that.mTrustedBiddingKeys);
    }

    /**
     * @return the hash of the {@link TrustedBiddingData} object's data
     */
    @Override
    public int hashCode() {
        return Objects.hash(mTrustedBiddingUrl, mTrustedBiddingKeys);
    }

    /** Builder for {@link TrustedBiddingData} objects. */
    public static final class Builder {
        @NonNull
        private Uri mTrustedBiddingUrl;
        @NonNull
        private List<String> mTrustedBiddingKeys;

        public Builder() { }

        /**
         * Sets the URL pointing to a trusted key-value server used to fetch bidding signals during
         * the ad selection process.
         */
        @NonNull
        public Builder setTrustedBiddingUrl(@NonNull Uri trustedBiddingUrl) {
            Objects.requireNonNull(trustedBiddingUrl);
            mTrustedBiddingUrl = trustedBiddingUrl;
            return this;
        }

        /**
         * Sets the list of keys to query the trusted key-value server with.
         *
         * This list is permitted to be empty, but it must not be null.
         */
        @NonNull
        public Builder setTrustedBiddingKeys(@NonNull List<String> trustedBiddingKeys) {
            Objects.requireNonNull(trustedBiddingKeys);
            mTrustedBiddingKeys = trustedBiddingKeys;
            return this;
        }

        /**
         * Builds the {@link TrustedBiddingData} object.
         *
         * @throws NullPointerException if any parameters are null when built
         */
        @NonNull
        public TrustedBiddingData build() {
            Objects.requireNonNull(mTrustedBiddingUrl);
            // Note that the list of keys is allowed to be empty, but not null
            Objects.requireNonNull(mTrustedBiddingKeys);

            return new TrustedBiddingData(mTrustedBiddingUrl, mTrustedBiddingKeys);
        }
    }
}

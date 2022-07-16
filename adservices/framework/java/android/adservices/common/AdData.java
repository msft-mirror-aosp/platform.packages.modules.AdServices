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

package android.adservices.common;

import android.annotation.NonNull;
import android.net.Uri;
import android.os.Parcel;
import android.os.Parcelable;

import java.util.Objects;

/**
 * Represents data specific to an ad that is necessary for ad selection and rendering.
 */
public final class AdData implements Parcelable {
    @NonNull private final Uri mRenderUri;
    @NonNull
    private final String mMetadata;

    @NonNull
    public static final Creator<AdData> CREATOR =
            new Creator<AdData>() {
                @Override
                public AdData createFromParcel(@NonNull Parcel in) {
                    Objects.requireNonNull(in);

                    return new AdData(in);
                }

                @Override
                public AdData[] newArray(int size) {
                    return new AdData[size];
                }
            };

    /**
     * Represents data specific to a single ad that is necessary for ad selection and rendering.
     *
     * @param renderUri a URL pointing to the ad's rendering assets
     * @param metadata buyer ad metadata represented as a JSON string
     * @hide
     * @deprecated use Builder to build the obj instead of this constructor.
     */
    // TODO(b/230782527): Remove this constructor.
    @Deprecated
    public AdData(@NonNull Uri renderUri, @NonNull String metadata) {
        Objects.requireNonNull(renderUri);
        Objects.requireNonNull(metadata);
        mRenderUri = renderUri;
        mMetadata = metadata;
    }

    private AdData(@NonNull AdData.Builder builder) {
        mRenderUri = builder.mRenderUri;
        mMetadata = builder.mMetadata;
    }

    private AdData(@NonNull Parcel in) {
        Objects.requireNonNull(in);

        mRenderUri = Uri.CREATOR.createFromParcel(in);
        mMetadata = in.readString();
    }

    @Override
    public void writeToParcel(@NonNull Parcel dest, int flags) {
        Objects.requireNonNull(dest);
        mRenderUri.writeToParcel(dest, flags);
        dest.writeString(mMetadata);
    }

    /** @hide */
    @Override
    public int describeContents() {
        return 0;
    }

    /** Gets the URL that points to the ad's rendering assets. The URL must use HTTPS. */
    @NonNull
    public Uri getRenderUri() {
        return mRenderUri;
    }

    /**
     * Gets the buyer ad metadata used during the ad selection process.
     * <p>
     * The metadata should be a valid JSON object serialized as a string. Metadata represents
     * ad-specific bidding information that will be used during ad selection as part of bid
     * generation and used in buyer JavaScript logic, which is executed in an isolated execution
     * environment.
     * <p>
     * If the metadata is not a valid JSON object that can be consumed by the buyer's JS, the ad
     * will not be eligible for ad selection.
     */
    @NonNull
    public String getMetadata() {
        return mMetadata;
    }

    /** Checks whether two {@link AdData} objects contain the same information. */
    @Override
    public boolean equals(Object o) {
        if (this == o) return true;
        if (!(o instanceof AdData)) return false;
        AdData adData = (AdData) o;
        return Objects.equals(mRenderUri, adData.mRenderUri)
                && Objects.equals(mMetadata, adData.mMetadata);
    }

    /** Returns the hash of the {@link AdData} object's data. */
    @Override
    public int hashCode() {
        return Objects.hash(mRenderUri, mMetadata);
    }

    @Override
    public String toString() {
        return "AdData{" + "mRenderUri=" + mRenderUri + ", mMetadata='" + mMetadata + '\'' + '}';
    }

    /** Builder for {@link AdData} objects. */
    public static final class Builder {
        @NonNull private Uri mRenderUri;
        @NonNull
        private String mMetadata;

        // TODO(b/232883403): We may need to add @NonNUll members as args.
        public Builder() {
        }

        /**
         * Sets the URL that points to the ad's rendering assets. The URL must use HTTPS.
         *
         * <p>See {@link #getRenderUri()} for detail.
         */
        @NonNull
        public AdData.Builder setRenderUri(@NonNull Uri renderUri) {
            Objects.requireNonNull(renderUri);
            mRenderUri = renderUri;
            return this;
        }

        /**
         * Sets the buyer ad metadata used during the ad selection process.
         * <p>
         * The metadata should be a valid JSON object serialized as a string. Metadata represents
         * ad-specific bidding information that will be used during ad selection as part of bid
         * generation and used in buyer JavaScript logic, which is executed in an isolated execution
         * environment.
         * <p>
         * If the metadata is not a valid JSON object that can be consumed by the buyer's JS, the ad
         * will not be eligible for ad selection.
         * <p>
         * See {@link #getMetadata()} for detail.
         */
        @NonNull
        public AdData.Builder setMetadata(@NonNull String metadata) {
            Objects.requireNonNull(metadata);
            mMetadata = metadata;
            return this;
        }

        /**
         * Builds the {@link AdData} object.
         *
         * @throws NullPointerException if any parameters are null when built
         */
        @NonNull
        public AdData build() {
            Objects.requireNonNull(mRenderUri);
            // TODO(b/231997523): Add JSON field validation.
            Objects.requireNonNull(mMetadata);

            return new AdData(this);
        }
    }
}

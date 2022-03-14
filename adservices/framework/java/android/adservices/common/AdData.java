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
 *
 * Hiding for future implementation and review for public exposure.
 * @hide
 */
public final class AdData implements Parcelable {
    @NonNull
    private final Uri mRenderUrl;
    @NonNull
    private final String mMetadata;

    @NonNull
    public static final Creator<AdData> CREATOR = new Creator<AdData>() {
        @Override
        public AdData createFromParcel(@NonNull Parcel in) {
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
     * @param renderUrl - a URL pointing to the ad's rendering assets
     * @param metadata - buyer ad metadata represented as a JSON string that is opaque to the
     *                 custom audience management and ad selection services
     */
    public AdData(@NonNull Uri renderUrl, @NonNull String metadata) {
        Objects.requireNonNull(renderUrl);
        Objects.requireNonNull(metadata);
        mRenderUrl = renderUrl;
        mMetadata = metadata;
    }

    private AdData(@NonNull Parcel in) {
        mRenderUrl = Uri.CREATOR.createFromParcel(in);
        mMetadata = in.readString();
    }

    @Override
    public void writeToParcel(@NonNull Parcel dest, int flags) {
        mRenderUrl.writeToParcel(dest, flags);
        dest.writeString(mMetadata);
    }

    /** @hide */
    @Override
    public int describeContents() {
        return 0;
    }

    /**
     * Gets the URL that points to the ad's rendering assets.
     */
    @NonNull
    public Uri getRenderUrl() {
        return mRenderUrl;
    }

    /**
     * Gets the buyer ad metadata used during the ad selection process.
     *
     * The metadata is opaque to the Custom Audience and Ad Selection APIs and is represented as a
     * JSON object string.
     */
    @NonNull
    public String getMetadata() {
        return mMetadata;
    }

    /**
     * Checks whether two {@link AdData} objects contain the same information.
     */
    @Override
    public boolean equals(Object o) {
        if (this == o) return true;
        if (!(o instanceof AdData)) return false;
        AdData adData = (AdData) o;
        return Objects.equals(mRenderUrl, adData.mRenderUrl)
                && Objects.equals(mMetadata, adData.mMetadata);
    }

    /**
     * Returns the hash of the {@link AdData} object's data.
     */
    @Override
    public int hashCode() {
        return Objects.hash(mRenderUrl, mMetadata);
    }
}

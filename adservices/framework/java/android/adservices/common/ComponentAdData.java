/*
 * Copyright (C) 2024 The Android Open Source Project
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
 * Represents data specific to a component ad that is necessary for ad selection and rendering. This
 * is to support use case for ads composed of multiple pieces, such as an ad displaying multiple
 * products at once.
 *
 * @hide TODO(b/370108832): Replace with flaggedApi
 */
public final class ComponentAdData implements Parcelable {
    private final Uri mRenderUri;
    private final String mAdRenderId;

    @NonNull
    public static final Creator<ComponentAdData> CREATOR =
            new Creator<>() {
                @Override
                public ComponentAdData createFromParcel(@NonNull Parcel in) {
                    return new ComponentAdData(in);
                }

                @Override
                public ComponentAdData[] newArray(int size) {
                    return new ComponentAdData[size];
                }
            };

    private ComponentAdData(@NonNull Parcel in) {
        mRenderUri = Uri.CREATOR.createFromParcel(in);
        mAdRenderId = in.readString();
    }

    /**
     * Constructs a {@link ComponentAdData} object.
     *
     * @param renderUri the URI that points to the component ad's rendering assets.
     * @param adRenderId the component ad render id used for server auctions.
     * @hide
     */
    public ComponentAdData(@NonNull Uri renderUri, @NonNull String adRenderId) {
        if (adRenderId.isEmpty()) {
            throw new IllegalArgumentException("Ad render id cannot be empty");
        }
        mRenderUri = Objects.requireNonNull(renderUri, "Provided render uri is null");
        mAdRenderId = Objects.requireNonNull(adRenderId, "Provided ad render id is null");
    }

    @Override
    public void writeToParcel(@NonNull Parcel dest, int flags) {
        Objects.requireNonNull(dest);

        mRenderUri.writeToParcel(dest, flags);
        dest.writeString(mAdRenderId);
    }

    @Override
    public int describeContents() {
        return 0;
    }

    /** Gets the URI that points to the component ad's rendering assets. */
    @NonNull
    public Uri getRenderUri() {
        return mRenderUri;
    }

    /**
     * Gets the component ad render id for server auctions.
     *
     * <p>Ad render id is collected for each {@link ComponentAdData} when server auction request is
     * received.
     *
     * <p>The overall size of the Custom Audience is limited. The size of this field is considered
     * using {@link String#getBytes()} in {@code UTF-8} encoding.
     */
    @NonNull
    public String getAdRenderId() {
        return mAdRenderId;
    }

    @Override
    public boolean equals(Object o) {
        if (this == o) return true;
        if (!(o instanceof ComponentAdData adData)) return false;
        return mRenderUri.equals(adData.mRenderUri)
                && Objects.equals(mAdRenderId, adData.mAdRenderId);
    }

    @Override
    public int hashCode() {
        return Objects.hash(mRenderUri, mAdRenderId);
    }

    @Override
    public String toString() {
        return "ComponentAdData{"
                + "mRenderUri="
                + mRenderUri
                + ", mAdRenderId='"
                + mAdRenderId
                + '\''
                + '}';
    }
}

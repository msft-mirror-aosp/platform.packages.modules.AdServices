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

package com.android.adservices.data.common;

import android.adservices.common.AdData;
import android.net.Uri;

import androidx.annotation.NonNull;

import java.util.Objects;

/**
 * Represents data specific to an ad that is necessary for ad selection and rendering.
 *
 * <p>Hiding for future implementation and review for public exposure.
 *
 * @hide
 */
public class DBAdData {
    @NonNull private final Uri mRenderUri;
    @NonNull
    private final String mMetadata;

    public DBAdData(Uri renderUri, String metadata) {
        mRenderUri = renderUri;
        mMetadata = metadata;
    }

    /**
     * Parse parcelable {@link AdData} to storage model {@link DBAdData}.
     *
     * @param parcelable the service model.
     * @return storage model
     */
    @NonNull
    public static DBAdData fromServiceObject(@NonNull AdData parcelable) {
        return new DBAdData(parcelable.getRenderUri(), parcelable.getMetadata());
    }

    /** Gets the URI that points to the ad's rendering assets. */
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

    @Override
    public boolean equals(Object o) {
        if (this == o) return true;
        if (!(o instanceof DBAdData)) return false;
        DBAdData adData = (DBAdData) o;
        return mRenderUri.equals(adData.mRenderUri) && mMetadata.equals(adData.mMetadata);
    }

    @Override
    public int hashCode() {
        return Objects.hash(mRenderUri, mMetadata);
    }

    @Override
    public String toString() {
        return "DBAdData{" + "mRenderUri=" + mRenderUri + ", mMetadata='" + mMetadata + '\'' + '}';
    }


    /**
     * Builder to construct a {@link DBAdData}.
     */
    public static class Builder {
        private Uri mRenderUri;
        private String mMetadata;

        public Builder() {
        }

        /** See {@link #getRenderUri()} for detail. */
        public Builder setRenderUri(@NonNull Uri renderUri) {
            this.mRenderUri = renderUri;
            return this;
        }

        /**
         * See {@link #getMetadata()} for detail.
         */
        public Builder setMetadata(@NonNull String metadata) {
            this.mMetadata = metadata;
            return this;
        }

        /**
         * Build the {@link DBAdData}.
         *
         * @return the built {@link DBAdData}.
         */
        public DBAdData build() {
            return new DBAdData(mRenderUri, mMetadata);
        }
    }
}

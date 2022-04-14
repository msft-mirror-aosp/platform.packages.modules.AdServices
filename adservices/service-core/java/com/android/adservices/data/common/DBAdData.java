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
    @NonNull
    private final Uri mRenderUrl;
    @NonNull
    private final String mMetadata;

    public DBAdData(Uri renderUrl, String metadata) {
        mRenderUrl = renderUrl;
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
        return new DBAdData(parcelable.getRenderUrl(), parcelable.getMetadata());
    }

    /** Gets the URL that points to the ad's rendering assets. */
    @NonNull
    public Uri getRenderUrl() {
        return mRenderUrl;
    }

    /**
     * Gets the buyer ad metadata used during the ad selection process.
     *
     * <p>The metadata is opaque to the Custom Audience and Ad Selection APIs and is represented as
     * a JSON object string.
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
        return mRenderUrl.equals(adData.mRenderUrl) && mMetadata.equals(adData.mMetadata);
    }

    @Override
    public int hashCode() {
        return Objects.hash(mRenderUrl, mMetadata);
    }

    @Override
    public String toString() {
        return "DBAdData{"
                + "mRenderUrl=" + mRenderUrl
                + ", mMetadata='" + mMetadata + '\''
                + '}';
    }


    /**
     * Builder to construct a {@link DBAdData}.
     */
    public static class Builder {
        private Uri mRenderUrl;
        private String mMetadata;

        public Builder() {
        }

        /**
         * See {@link #getRenderUrl()} for detail.
         */
        public Builder setRenderUrl(@NonNull Uri renderUrl) {
            this.mRenderUrl = renderUrl;
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
            return new DBAdData(mRenderUrl, mMetadata);
        }
    }
}

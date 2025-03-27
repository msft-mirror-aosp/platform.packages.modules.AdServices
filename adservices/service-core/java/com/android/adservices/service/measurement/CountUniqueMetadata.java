/*
 * Copyright (C) 2025 The Android Open Source Project
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

package com.android.adservices.service.measurement;

import android.annotation.NonNull;
import android.net.Uri;

import java.util.Objects;

public class CountUniqueMetadata {

    private Uri mReportingOrigin;
    private String mKey;
    private Integer mValue;
    private Long mExpirationTime; // async registration time + 30 days
    private Uri mRegistrant;

    public CountUniqueMetadata() {
        mReportingOrigin = null;
        mKey = null;
        mValue = null;
        mExpirationTime = null;
        mRegistrant = null;
    }

    @Override
    public boolean equals(Object obj) {
        if (!(obj instanceof CountUniqueMetadata countUniqueMetadata)) {
            return false;
        }
        return Objects.equals(mReportingOrigin, countUniqueMetadata.mReportingOrigin)
                && Objects.equals(mKey, countUniqueMetadata.mKey)
                && Objects.equals(mValue, countUniqueMetadata.mValue)
                && mExpirationTime.equals(countUniqueMetadata.mExpirationTime)
                && mRegistrant.equals(countUniqueMetadata.mRegistrant);
    }

    @Override
    public int hashCode() {
        return Objects.hash(mReportingOrigin, mKey, mValue, mExpirationTime, mRegistrant);
    }

    /** Reporting origin used to store the metadata */
    public Uri getReportingOrigin() {
        return mReportingOrigin;
    }

    /** Key for the metadata */
    public String getKey() {
        return mKey;
    }

    /** Value for metadata */
    public Integer getValue() {
        return mValue;
    }

    /** Expiration time for the metadata */
    public Long getExpirationTime() {
        return mExpirationTime;
    }

    /** Registrant that stored the metadata */
    public Uri getRegistrant() {
        return mRegistrant;
    }

    public static class Builder {
        private CountUniqueMetadata mMetadata;

        public Builder() {
            mMetadata = new CountUniqueMetadata();
        }

        /** See {@link CountUniqueMetadata#getReportingOrigin()} ()} */
        public CountUniqueMetadata.Builder setReportingOrigin(@NonNull Uri reportingOrigin) {
            mMetadata.mReportingOrigin = reportingOrigin;
            return this;
        }

        /** See {@link CountUniqueMetadata#getKey()} ()} */
        public CountUniqueMetadata.Builder setKey(@NonNull String key) {
            mMetadata.mKey = key;
            return this;
        }

        /** See {@link CountUniqueMetadata#getValue()} ()} */
        public CountUniqueMetadata.Builder setValue(@NonNull Integer value) {
            mMetadata.mValue = value;
            return this;
        }

        /** See {@link CountUniqueMetadata#getExpirationTime()} ()} */
        public CountUniqueMetadata.Builder setExpirationTime(@NonNull Long expirationTime) {
            mMetadata.mExpirationTime = expirationTime;
            return this;
        }

        /** See {@link CountUniqueMetadata#getRegistrant()} */
        public CountUniqueMetadata.Builder setRegistrant(@NonNull Uri registrant) {
            mMetadata.mRegistrant = registrant;
            return this;
        }

        /** Builds CountUniqueMetadata */
        public CountUniqueMetadata build() {
            return mMetadata;
        }
    }
}

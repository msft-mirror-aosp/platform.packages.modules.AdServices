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

package com.android.adservices.service.measurement;

import android.annotation.NonNull;
import android.net.Uri;

import androidx.annotation.Nullable;

import com.android.adservices.service.measurement.util.Validation;

/** POJO for AsyncRegistration. */
public class AsyncRegistration {

    public enum RegistrationType {
        APP_SOURCE,
        APP_TRIGGER,
        WEB_SOURCE,
        WEB_TRIGGER
    }

    private final String mId;
    private final String mEnrollmentId;
    private final Uri mOsDestination;
    private final Uri mWebDestination;
    private final Uri mRegistrationUri;
    private final Uri mVerifiedDestination;
    private final Uri mTopOrigin;
    private final boolean mRedirect;
    private final Uri mRegistrant;
    private final Source.SourceType mSourceType;
    private long mRequestTime;
    private long mRetryCount;
    private long mLastProcessingTime;
    private final RegistrationType mType;
    private final boolean mDebugKeyAllowed;

    public AsyncRegistration(@NonNull AsyncRegistration.Builder builder) {
        mId = builder.mId;
        mEnrollmentId = builder.mEnrollmentId;
        mOsDestination = builder.mOsDestination;
        mWebDestination = builder.mWebDestination;
        mRegistrationUri = builder.mRegistrationUri;
        mVerifiedDestination = builder.mVerifiedDestination;
        mTopOrigin = builder.mTopOrigin;
        mRedirect = builder.mRedirect;
        mRegistrant = builder.mRegistrant;
        mSourceType = builder.mSourceType;
        mRequestTime = builder.mRequestTime;
        mRetryCount = builder.mRetryCount;
        mLastProcessingTime = builder.mLastProcessingTime;
        mType = builder.mType;
        mDebugKeyAllowed = builder.mDebugKeyAllowed;
    }

    /** Unique identifier for the {@link AsyncRegistration}. */
    public String getId() {
        return mId;
    }

    /** App destination of the {@link Source}. */
    @Nullable
    public Uri getOsDestination() {
        return mOsDestination;
    }

    /** Web destination of the {@link Source}. */
    @Nullable
    public Uri getWebDestination() {
        return mWebDestination;
    }

    /** Represents the location of registration payload. */
    @NonNull
    public Uri getRegistrationUri() {
        return mRegistrationUri;
    }

    /** Uri used to identify and locate a {@link Source} originating from the web. */
    @Nullable
    public Uri getVerifiedDestination() {
        return mVerifiedDestination;
    }

    /** Package name of caller app. */
    @NonNull
    public Uri getTopOrigin() {
        return mTopOrigin;
    }

    /** Package name of caller app, name comes from context. */
    @NonNull
    public Uri getRegistrant() {
        return mRegistrant;
    }

    /** Derived from the Ad tech domain. */
    @NonNull
    public String getEnrollmentId() {
        return mEnrollmentId;
    }

    /** Determines whether redirects of a {@link Source} or {@link Trigger} will be serviced. */
    public boolean getRedirect() {
        return mRedirect;
    }

    /** Determines whether the input event was a click or view. */
    public Source.SourceType getSourceType() {
        return mSourceType;
    }

    /** Time in ms that record arrived at Registration Queue. */
    public long getRequestTime() {
        return mRequestTime;
    }

    /** Retry attempt counter. */
    public long getRetryCount() {
        return mRetryCount;
    }

    /** Processing time in ms. */
    public long getLastProcessingTime() {
        return mLastProcessingTime;
    }

    /** Indicates how the record will be processed . */
    public RegistrationType getType() {
        return mType;
    }

    /** Indicates whether the debug key provided by Ad-Tech is allowed to be used or not. */
    public boolean getDebugKeyAllowed() {
        return mDebugKeyAllowed;
    }

    /** Increments the retry count of the current record. */
    public void incrementRetryCount() {
        ++mRetryCount;
    }

    /** Builder for {@link AsyncRegistration}. */
    public static class Builder {
        private String mId;
        private String mEnrollmentId;
        private Uri mOsDestination;
        private Uri mWebDestination;
        private Uri mRegistrationUri;
        private Uri mVerifiedDestination;
        private Uri mTopOrigin;
        private boolean mRedirect = true;
        private Uri mRegistrant;
        private Source.SourceType mSourceType;
        private long mRequestTime;
        private long mRetryCount = 0;
        private long mLastProcessingTime;
        private AsyncRegistration.RegistrationType mType;
        private boolean mDebugKeyAllowed;

        /** See {@link AsyncRegistration#getId()}. */
        @NonNull
        public Builder setId(@NonNull String id) {
            Validation.validateNonNull(id);
            mId = id;
            return this;
        }

        /** See {@link AsyncRegistration#getEnrollmentId()}. */
        @NonNull
        public Builder setEnrollmentId(@NonNull String enrollmentId) {
            Validation.validateNonNull(enrollmentId);
            mEnrollmentId = enrollmentId;
            return this;
        }

        /** See {@link AsyncRegistration#getOsDestination()}. */
        @NonNull
        public Builder setOsDestination(@Nullable Uri osDestination) {
            mOsDestination = osDestination;
            return this;
        }

        /** See {@link AsyncRegistration#getRegistrationUri()}. */
        @NonNull
        public Builder setRegistrationUri(@NonNull Uri registrationUri) {
            Validation.validateNonNull(registrationUri);
            mRegistrationUri = registrationUri;
            return this;
        }

        /** See {@link AsyncRegistration#getVerifiedDestination()}. */
        @NonNull
        public Builder setVerifiedDestination(@Nullable Uri verifiedDestination) {
            mVerifiedDestination = verifiedDestination;
            return this;
        }

        /** See {@link AsyncRegistration#getWebDestination()}. */
        @NonNull
        public Builder setWebDestination(@Nullable Uri webDestination) {
            mWebDestination = webDestination;
            return this;
        }

        /** See {@link AsyncRegistration#getTopOrigin()}. */
        @NonNull
        public Builder setTopOrigin(@NonNull Uri topOrigin) {
            Validation.validateNonNull(topOrigin);
            mTopOrigin = topOrigin;
            return this;
        }

        /** See {@link AsyncRegistration#getRedirect()}. */
        @NonNull
        public Builder setRedirect(boolean redirect) {
            mRedirect = redirect;
            return this;
        }

        /** See {@link AsyncRegistration#getRegistrant()}. */
        @NonNull
        public Builder setRegistrant(@NonNull Uri registrant) {
            Validation.validateNonNull(registrant);
            mRegistrant = registrant;
            return this;
        }

        /**
         * See {@link AsyncRegistration#getSourceType()}. Valid inputs are ordinals of {@link
         * Source.SourceType} enum values.
         */
        @NonNull
        public Builder setSourceType(Source.SourceType sourceType) {
            mSourceType = sourceType;
            return this;
        }

        /** See {@link AsyncRegistration#getRequestTime()}. */
        @NonNull
        public Builder setRequestTime(long requestTime) {
            mRequestTime = requestTime;
            return this;
        }

        /** See {@link AsyncRegistration#getRetryCount()}. */
        @NonNull
        public Builder setRetryCount(long retryCount) {
            mRetryCount = retryCount;
            return this;
        }

        /** See {@link AsyncRegistration#getLastProcessingTime()}. */
        @NonNull
        public Builder setLastProcessingTime(long lastProcessingTime) {
            mLastProcessingTime = lastProcessingTime;
            return this;
        }

        /**
         * See {@link AsyncRegistration#getType()}. Valid inputs are ordinals of {@link
         * AsyncRegistration.RegistrationType} enum values.
         */
        @NonNull
        public Builder setType(int type) {
            mType = RegistrationType.values()[type];
            return this;
        }

        /** See {@link AsyncRegistration#getDebugKeyAllowed()}. */
        @NonNull
        public Builder setDebugKeyAllowed(boolean debugKeyAllowed) {
            mDebugKeyAllowed = debugKeyAllowed;
            return this;
        }

        /** Build the {@link AsyncRegistration}. */
        public AsyncRegistration build() {
            return new AsyncRegistration(this);
        }
    }
}

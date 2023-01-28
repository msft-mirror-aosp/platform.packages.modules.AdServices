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

import static com.android.adservices.service.measurement.SystemHealthParams.MAX_REDIRECTS_PER_REGISTRATION;

import android.annotation.IntDef;
import android.annotation.NonNull;
import android.net.Uri;

import androidx.annotation.Nullable;

import com.android.adservices.service.measurement.util.Validation;

import java.lang.annotation.Retention;
import java.lang.annotation.RetentionPolicy;

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
    @RedirectType private int mRedirectType;
    private final int mRedirectCount;
    private final Uri mRegistrant;
    private final Source.SourceType mSourceType;
    private long mRequestTime;
    private long mRetryCount;
    private long mLastProcessingTime;
    private final RegistrationType mType;
    private final boolean mDebugKeyAllowed;
    private final boolean mAdIdPermission;
    @Nullable private String mRegistrationId;

    @IntDef(value = {
            RedirectType.NONE,
            RedirectType.ANY,
            RedirectType.DAISY_CHAIN,
    })
    @Retention(RetentionPolicy.SOURCE)
    public @interface RedirectType {
        int NONE = 0;
        int ANY = 1;
        int DAISY_CHAIN = 2;
    }

    public AsyncRegistration(@NonNull AsyncRegistration.Builder builder) {
        mId = builder.mId;
        mEnrollmentId = builder.mEnrollmentId;
        mOsDestination = builder.mOsDestination;
        mWebDestination = builder.mWebDestination;
        mRegistrationUri = builder.mRegistrationUri;
        mVerifiedDestination = builder.mVerifiedDestination;
        mTopOrigin = builder.mTopOrigin;
        mRedirectType = builder.mRedirectType;
        mRedirectCount = builder.mRedirectCount;
        mRegistrant = builder.mRegistrant;
        mSourceType = builder.mSourceType;
        mRequestTime = builder.mRequestTime;
        mRetryCount = builder.mRetryCount;
        mLastProcessingTime = builder.mLastProcessingTime;
        mType = builder.mType;
        mDebugKeyAllowed = builder.mDebugKeyAllowed;
        mAdIdPermission = builder.mAdIdPermission;
        mRegistrationId = builder.mRegistrationId;
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

    /** Determines the type of redirects of a {@link Source} or {@link Trigger}, as well as the
     * states, None and Completed. */
    @RedirectType
    public int getRedirectType() {
        return mRedirectType;
    }

    /** Determines the count of remaining redirects to observe when fetching a {@link Source} or
     * {@link Trigger}, provided the {@link RedirectType} is not None or Compeleted */
    public int getRedirectCount() {
        return mRedirectCount;
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

    /** Indicates whether Ad Id permission is enabled. */
    public boolean hasAdIdPermission() {
        return mAdIdPermission;
    }

    /** Returns the registration id. */
    @Nullable
    public String getRegistrationId() {
        return mRegistrationId;
    }

    /** Increments the retry count of the current record. */
    public void incrementRetryCount() {
        ++mRetryCount;
    }

    /** Indicates whether the registration runner should process redirects for this registration. */
    public boolean shouldProcessRedirects() {
        if (mRedirectType == RedirectType.NONE) {
            return false;
        }
        return mRedirectType == RedirectType.ANY || mRedirectCount < MAX_REDIRECTS_PER_REGISTRATION;
    }

    /** Gets the next expected redirect count for this registration. */
    public int getNextRedirectCount() {
        if (mRedirectType == AsyncRegistration.RedirectType.NONE) {
            return 0;
        // Redirect type is being set for the first time for this registration-sequence.
        } else if (mRedirectType == AsyncRegistration.RedirectType.ANY) {
            return 1;
        // This registration-sequence already has an assigned redirect type.
        } else {
            return mRedirectCount + 1;
        }
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
        private @RedirectType int mRedirectType = RedirectType.ANY;
        private int mRedirectCount = 0;
        private Uri mRegistrant;
        private Source.SourceType mSourceType;
        private long mRequestTime;
        private long mRetryCount = 0;
        private long mLastProcessingTime;
        private AsyncRegistration.RegistrationType mType;
        private boolean mDebugKeyAllowed;
        private boolean mAdIdPermission;
        @Nullable private String mRegistrationId;

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
        public Builder setTopOrigin(@Nullable Uri topOrigin) {
            mTopOrigin = topOrigin;
            return this;
        }

        /** See {@link AsyncRegistration#getRedirectType()}. */
        @NonNull
        public Builder setRedirectType(@RedirectType int redirectType) {
            mRedirectType = redirectType;
            return this;
        }

        /** See {@link AsyncRegistration#getRedirectCount()}. */
        @NonNull
        public Builder setRedirectCount(int redirectCount) {
            mRedirectCount = redirectCount;
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

        /** See {@link AsyncRegistration#hasAdIdPermission()}. */
        @NonNull
        public Builder setAdIdPermission(boolean adIdPermission) {
            mAdIdPermission = adIdPermission;
            return this;
        }

        /** See {@link AsyncRegistration#getRegistrationId()} */
        public Builder setRegistrationId(@Nullable String registrationId) {
            mRegistrationId = registrationId;
            return this;
        }

        /** Build the {@link AsyncRegistration}. */
        public AsyncRegistration build() {
            return new AsyncRegistration(this);
        }
    }
}
